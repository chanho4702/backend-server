package com.platform.boardservice.org;

import com.platform.common.error.ServiceUnavailableException;
import com.platform.common.grpc.InternalTokenClientInterceptor;
import com.platform.proto.org.v1.Action;
import com.platform.proto.org.v1.CheckPermissionRequest;
import com.platform.proto.org.v1.CheckPermissionResponse;
import com.platform.proto.org.v1.GetMembersRequest;
import com.platform.proto.org.v1.GetMembersResponse;
import com.platform.proto.org.v1.MemberInfo;
import com.platform.proto.org.v1.PermissionServiceGrpc;
import com.platform.proto.org.v1.ResourceType;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** gRPC 구현의 코드별 분기 — UNAVAILABLE/DEADLINE만 가용성 장애, 나머지는 fail-closed. 토큰 메타데이터도 본다. */
class GrpcOrgClientTest {

    private static final Metadata.Key<String> TOKEN = Metadata.Key.of("x-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    private final AtomicReference<Status> failWith = new AtomicReference<>();
    private final AtomicReference<String> seenToken = new AtomicReference<>();
    private final AtomicReference<CheckPermissionRequest> seenCheck = new AtomicReference<>();
    private final AtomicInteger checkCalls = new AtomicInteger();
    private volatile boolean adminAllowed;
    private volatile MemberInfo member;

    private Server server;
    private ManagedChannel channel;
    private GrpcOrgClient client;

    @BeforeEach
    void start() throws Exception {
        String name = InProcessServerBuilder.generateName();
        PermissionServiceGrpc.PermissionServiceImplBase service = new PermissionServiceGrpc.PermissionServiceImplBase() {
            @Override
            public void getMembers(GetMembersRequest request, StreamObserver<GetMembersResponse> out) {
                if (failWith.get() != null) {
                    out.onError(failWith.get().asRuntimeException());
                    return;
                }
                GetMembersResponse.Builder b = GetMembersResponse.newBuilder();
                if (member != null) b.addMembers(member);
                out.onNext(b.build());
                out.onCompleted();
            }

            @Override
            public void checkPermission(CheckPermissionRequest request, StreamObserver<CheckPermissionResponse> out) {
                checkCalls.incrementAndGet();
                seenCheck.set(request);
                if (failWith.get() != null) {
                    out.onError(failWith.get().asRuntimeException());
                    return;
                }
                out.onNext(CheckPermissionResponse.newBuilder().setAllowed(adminAllowed).build());
                out.onCompleted();
            }
        };
        ServerInterceptor capture = new ServerInterceptor() {
            @Override
            public <Q, R> ServerCall.Listener<Q> interceptCall(ServerCall<Q, R> call, Metadata headers,
                                                              ServerCallHandler<Q, R> next) {
                seenToken.set(headers.get(TOKEN));
                return next.startCall(call, headers);
            }
        };
        server = InProcessServerBuilder.forName(name).directExecutor()
                .addService(ServerInterceptors.intercept(service, capture)).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor()
                .intercept(new InternalTokenClientInterceptor("s3cret")).build();
        client = new GrpcOrgClient(PermissionServiceGrpc.newBlockingStub(channel), 2);
    }

    @AfterEach
    void stop() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void 상태를_읽고_서비스_간_토큰을_싣는다() {
        member = MemberInfo.newBuilder().setId(7).setStatus("ACTIVE").setKind("HUMAN").build();
        OrgClient.StatusLookup result = client.memberStatus(7);
        assertThat(result.outcome()).isEqualTo(OrgClient.Outcome.OK);
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(seenToken.get()).isEqualTo("s3cret");
    }

    @Test
    void 행이_없으면_OK에_null() {
        OrgClient.StatusLookup result = client.memberStatus(8);
        assertThat(result.outcome()).isEqualTo(OrgClient.Outcome.OK);
        assertThat(result.status()).isNull();
    }

    @Test
    void 상태_조회_UNAVAILABLE과_DEADLINE은_가용성_장애() {
        failWith.set(Status.UNAVAILABLE);
        assertThat(client.memberStatus(7).outcome()).isEqualTo(OrgClient.Outcome.UNAVAILABLE);
        failWith.set(Status.DEADLINE_EXCEEDED);
        assertThat(client.memberStatus(7).outcome()).isEqualTo(OrgClient.Outcome.UNAVAILABLE);
    }

    @Test
    void 상태_조회_그_밖의_오류는_FAILED() {
        failWith.set(Status.UNAUTHENTICATED); // org enforce에서 토큰이 틀리면 — 장애가 아니라 거부로 닫힌다
        assertThat(client.memberStatus(7).outcome()).isEqualTo(OrgClient.Outcome.FAILED);
        failWith.set(Status.INTERNAL);
        assertThat(client.memberStatus(7).outcome()).isEqualTo(OrgClient.Outcome.FAILED);
    }

    @Test
    void 전역_관리자는_GLOBAL_ADMIN으로_묻는다() {
        adminAllowed = true;
        assertThat(client.isGlobalAdmin(3)).isTrue();
        CheckPermissionRequest req = seenCheck.get();
        assertThat(req.getResourceType()).isEqualTo(ResourceType.GLOBAL);
        assertThat(req.getResourceId()).isEmpty();
        assertThat(req.getAction()).isEqualTo(Action.ADMIN);
        assertThat(req.getUserId()).isEqualTo(3);
    }

    @Test
    void 관리자_판정은_캐시된다() {
        adminAllowed = true;
        client.isGlobalAdmin(4);
        client.isGlobalAdmin(4);
        assertThat(checkCalls.get()).isEqualTo(1);
    }

    @Test
    void 관리자_판정_불능은_503() {
        failWith.set(Status.UNAVAILABLE);
        assertThatThrownBy(() -> client.isGlobalAdmin(5)).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void 관리자_판정_그_밖의_오류는_fail_closed이고_캐시하지_않는다() {
        adminAllowed = true;
        failWith.set(Status.PERMISSION_DENIED);
        assertThat(client.isGlobalAdmin(6)).isFalse();
        failWith.set(null);
        assertThat(client.isGlobalAdmin(6)).isTrue();
    }
}

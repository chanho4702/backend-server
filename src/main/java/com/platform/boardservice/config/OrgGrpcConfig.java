package com.platform.boardservice.config;

import com.platform.boardservice.org.GrpcOrgClient;
import com.platform.boardservice.org.OrgClient;
import com.platform.common.grpc.InternalTokenClientInterceptor;
import com.platform.proto.org.v1.PermissionServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * org PermissionService gRPC 채널 — 위키·ALM·agent와 같은 env(ORG_GRPC_HOST·ORG_GRPC_PORT·ORG_GRPC_DEADLINE_SECONDS).
 * 서비스 간 토큰(P-01): 모든 호출 메타데이터에 {@code PLATFORM_GRPC_TOKEN}을 싣는다 — 인터셉터는 common-starter가 준다
 * (org가 enforce면 토큰 없는 호출은 거부되고, 그건 가용성 장애가 아니라 fail-closed 거부로 떨어진다).
 */
@Configuration
public class OrgGrpcConfig {

    @Bean(destroyMethod = "shutdown")
    ManagedChannel orgChannel(@Value("${platform.org-grpc.host}") String host,
                              @Value("${platform.org-grpc.port}") int port,
                              InternalTokenClientInterceptor platformGrpcClientInterceptor) {
        // alm 실측(2026-09-05): 첫 요청이 연결 수립에 데드라인을 넘기지 않게 시작 시 연결을 선점하고 keepalive로 유휴 끊김을 막는다.
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .intercept(platformGrpcClientInterceptor)
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(true)
                .build();
        channel.getState(true);
        return channel;
    }

    @Bean
    OrgClient orgClient(ManagedChannel orgChannel,
                        @Value("${platform.org-grpc.deadline-seconds:2}") long deadlineSeconds) {
        return new GrpcOrgClient(PermissionServiceGrpc.newBlockingStub(orgChannel), deadlineSeconds);
    }
}

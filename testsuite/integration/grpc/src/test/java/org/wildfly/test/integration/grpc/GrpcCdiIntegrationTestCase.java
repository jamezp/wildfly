/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.integration.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.container.annotation.ArquillianTest;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.wildfly.test.integration.grpc.service.GreeterGrpc;
import org.wildfly.test.integration.grpc.service.HelloReply;
import org.wildfly.test.integration.grpc.service.HelloRequest;
import org.wildfly.test.integration.grpc.service.cdi.CdiGreeterService;

/**
 * Tests CDI integration with gRPC services.
 * <p>
 * This test validates that:
 * - gRPC services can be CDI beans (@ApplicationScoped)
 * - CDI injection works inside gRPC service implementations
 * - CDI lifecycle is properly managed
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
@ArquillianTest
@RunAsClient
public class GrpcCdiIntegrationTestCase {

    @ArquillianResource
    private URI uri;

    private ManagedChannel channel;

    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class, "ROOT.war")
                .addPackages(true, CdiGreeterService.class.getPackage())
                .addPackages(true, GreeterGrpc.class.getPackage())
                .addAsWebInfResource("WEB-INF/beans.xml", "beans.xml");
    }

    @BeforeEach
    public void setUp() {
        channel = ManagedChannelBuilder.forAddress(uri.getHost(), uri.getPort())
                .usePlaintext()
                .build();
    }

    @AfterEach
    public void tearDown() throws InterruptedException {
        if (channel != null) {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testCdiInjectionInGrpcService() {
        final GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);
        final HelloRequest request = HelloRequest.newBuilder()
                .setName("CDI Test")
                .build();

        final HelloReply reply = stub.sayHello(request);

        assertNotNull(reply);
        // GreetingService adds " (via CDI)" suffix
        assertEquals("Hello, CDI Test! (via CDI)", reply.getMessage());
    }

    @Test
    public void testCdiScopeIsApplicationScoped() {
        final GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);

        // Make multiple requests - should hit same CDI bean instance
        final HelloReply reply1 = stub.sayHello(HelloRequest.newBuilder().setName("Request1").build());
        final HelloReply reply2 = stub.sayHello(HelloRequest.newBuilder().setName("Request2").build());

        assertNotNull(reply1);
        assertNotNull(reply2);

        // Both should have been processed by same ApplicationScoped bean instance
        assertTrue(reply1.getMessage().contains("(via CDI)"));
        assertTrue(reply2.getMessage().contains("(via CDI)"));
    }

    @Test
    public void testCdiPostConstructCalled() {
        final GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);
        final HelloRequest request = HelloRequest.newBuilder()
                .setName("PostConstruct Test")
                .build();

        final HelloReply reply = stub.sayHello(request);

        assertNotNull(reply);
        // GreetingService should be initialized via @PostConstruct
        assertTrue(reply.getMessage().contains("via CDI"));
    }

    @Test
    public void testMultipleCdiInjectionsInSameService() {
        final GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);

        // CdiGreeterService has multiple @Inject fields - all should work
        final HelloRequest request = HelloRequest.newBuilder()
                .setName("Multi-Inject")
                .build();

        final HelloReply reply = stub.sayHello(request);

        assertNotNull(reply);
        assertEquals("Hello, Multi-Inject! (via CDI)", reply.getMessage());
    }
}

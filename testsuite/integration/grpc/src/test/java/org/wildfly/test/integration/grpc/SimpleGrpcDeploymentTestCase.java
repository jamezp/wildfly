/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.integration.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
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
import org.wildfly.test.integration.grpc.service.GreeterService;
import org.wildfly.test.integration.grpc.service.HelloReply;
import org.wildfly.test.integration.grpc.service.HelloRequest;

/**
 * Tests basic gRPC service deployment and invocation.
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
@ArquillianTest
@RunAsClient
public class SimpleGrpcDeploymentTestCase {

    @ArquillianResource
    private URI uri;

    private ManagedChannel channel;

    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class, "ROOT.war")
                .addPackages(true, GreeterService.class.getPackage())
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
    public void testSimpleGreeting() {
        final GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);
        final HelloRequest request = HelloRequest.newBuilder()
                .setName("WildFly")
                .build();

        final HelloReply reply = stub.sayHello(request);

        assertNotNull(reply);
        assertEquals("Hello, WildFly!", reply.getMessage());
    }

    @Test
    public void testEmptyName() {
        final GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);
        final HelloRequest request = HelloRequest.newBuilder()
                .setName("")
                .build();

        final HelloReply reply = stub.sayHello(request);

        assertNotNull(reply);
        assertEquals("Hello, World!", reply.getMessage());
    }

    @Test
    public void testMultipleInvocations() {
        final GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);

        for (int i = 0; i < 10; i++) {
            final HelloRequest request = HelloRequest.newBuilder()
                    .setName("Test" + i)
                    .build();

            final HelloReply reply = stub.sayHello(request);

            assertNotNull(reply);
            assertEquals("Hello, Test" + i + "!", reply.getMessage());
        }
    }

    @Test
    public void testInvalidMethod() {
        final GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);

        // This should fail - method doesn't exist
        assertThrows(StatusRuntimeException.class, () -> {
            stub.sayGoodbye(HelloRequest.newBuilder().setName("Test").build());
        });
    }
}

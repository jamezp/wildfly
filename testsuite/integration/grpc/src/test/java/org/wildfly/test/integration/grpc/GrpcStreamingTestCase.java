/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.integration.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.container.annotation.ArquillianTest;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.wildfly.test.integration.grpc.service.HelloReply;
import org.wildfly.test.integration.grpc.service.HelloRequest;
import org.wildfly.test.integration.grpc.service.StreamingGreeterGrpc;
import org.wildfly.test.integration.grpc.service.streaming.StreamingGreeterService;

/**
 * Tests gRPC streaming (server-side, client-side, and bidirectional).
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
@ArquillianTest
@RunAsClient
public class GrpcStreamingTestCase {

    @ArquillianResource
    private URI uri;

    private ManagedChannel channel;

    @Deployment
    public static WebArchive createDeployment() {
        return ShrinkWrap.create(WebArchive.class, "ROOT.war")
                .addPackages(true, StreamingGreeterService.class.getPackage())
                .addPackages(true, StreamingGreeterGrpc.class.getPackage())
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
    public void testServerSideStreaming() throws InterruptedException {
        final StreamingGreeterGrpc.StreamingGreeterStub stub = StreamingGreeterGrpc.newStub(channel);
        final HelloRequest request = HelloRequest.newBuilder()
                .setName("Streaming Test")
                .build();

        final List<String> responses = new ArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);

        stub.sayHelloServerStream(request, new StreamObserver<HelloReply>() {
            @Override
            public void onNext(final HelloReply reply) {
                responses.add(reply.getMessage());
            }

            @Override
            public void onError(final Throwable t) {
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        // Server should stream multiple responses
        assertTrue(responses.size() > 1);
        assertTrue(responses.stream().allMatch(msg -> msg.contains("Streaming Test")));
    }

    @Test
    public void testClientSideStreaming() throws InterruptedException {
        final StreamingGreeterGrpc.StreamingGreeterStub stub = StreamingGreeterGrpc.newStub(channel);

        final List<String> responses = new ArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);

        final StreamObserver<HelloRequest> requestObserver = stub.sayHelloClientStream(new StreamObserver<HelloReply>() {
            @Override
            public void onNext(final HelloReply reply) {
                responses.add(reply.getMessage());
            }

            @Override
            public void onError(final Throwable t) {
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        });

        // Send multiple requests
        for (int i = 0; i < 5; i++) {
            requestObserver.onNext(HelloRequest.newBuilder()
                    .setName("Client" + i)
                    .build());
        }
        requestObserver.onCompleted();

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        // Should get one response summarizing all client messages
        assertEquals(1, responses.size());
    }

    @Test
    public void testBidirectionalStreaming() throws InterruptedException {
        final StreamingGreeterGrpc.StreamingGreeterStub stub = StreamingGreeterGrpc.newStub(channel);

        final List<String> responses = new ArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);

        final StreamObserver<HelloRequest> requestObserver = stub.sayHelloBidirectionalStream(new StreamObserver<HelloReply>() {
            @Override
            public void onNext(final HelloReply reply) {
                responses.add(reply.getMessage());
            }

            @Override
            public void onError(final Throwable t) {
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        });

        // Send multiple requests, should get response for each
        for (int i = 0; i < 5; i++) {
            requestObserver.onNext(HelloRequest.newBuilder()
                    .setName("Bidi" + i)
                    .build());
        }
        requestObserver.onCompleted();

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        // Should get a response for each request
        assertEquals(5, responses.size());
    }

    @Test
    public void testLargeStreamVolume() throws InterruptedException {
        final StreamingGreeterGrpc.StreamingGreeterStub stub = StreamingGreeterGrpc.newStub(channel);

        final List<String> responses = new ArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);

        final StreamObserver<HelloRequest> requestObserver = stub.sayHelloBidirectionalStream(new StreamObserver<HelloReply>() {
            @Override
            public void onNext(final HelloReply reply) {
                responses.add(reply.getMessage());
            }

            @Override
            public void onError(final Throwable t) {
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        });

        // Send many requests to test backpressure and thread pool
        for (int i = 0; i < 100; i++) {
            requestObserver.onNext(HelloRequest.newBuilder()
                    .setName("Load" + i)
                    .build());
        }
        requestObserver.onCompleted();

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        assertEquals(100, responses.size());
    }
}

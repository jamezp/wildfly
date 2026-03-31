/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.integration.grpc.service.streaming;

import io.grpc.stub.StreamObserver;
import org.wildfly.test.integration.grpc.service.HelloReply;
import org.wildfly.test.integration.grpc.service.HelloRequest;
import org.wildfly.test.integration.grpc.service.StreamingGreeterGrpc;

import java.util.ArrayList;
import java.util.List;

/**
 * gRPC service with streaming method implementations.
 * Demonstrates server-side, client-side, and bidirectional streaming.
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
public class StreamingGreeterService extends StreamingGreeterGrpc.StreamingGreeterImplBase {

    public void sayHelloServerStream(final HelloRequest request, final StreamObserver<HelloReply> responseObserver) {
        final String name = request.getName();

        // Send multiple responses
        for (int i = 0; i < 5; i++) {
            final HelloReply reply = HelloReply.newBuilder()
                    .setMessage("Hello #" + i + ", " + name + "!")
                    .build();
            responseObserver.onNext(reply);
        }

        responseObserver.onCompleted();
    }

    public StreamObserver<HelloRequest> sayHelloClientStream(final StreamObserver<HelloReply> responseObserver) {
        return new StreamObserver<HelloRequest>() {
            private final List<String> names = new ArrayList<>();

            @Override
            public void onNext(final HelloRequest request) {
                names.add(request.getName());
            }

            @Override
            public void onError(final Throwable t) {
                responseObserver.onError(t);
            }

            @Override
            public void onCompleted() {
                // Send single response summarizing all received names
                final String summary = "Received " + names.size() + " greetings: " + String.join(", ", names);
                final HelloReply reply = HelloReply.newBuilder()
                        .setMessage(summary)
                        .build();
                responseObserver.onNext(reply);
                responseObserver.onCompleted();
            }
        };
    }

    public StreamObserver<HelloRequest> sayHelloBidirectionalStream(final StreamObserver<HelloReply> responseObserver) {
        return new StreamObserver<HelloRequest>() {

            @Override
            public void onNext(final HelloRequest request) {
                // Respond immediately to each request
                final HelloReply reply = HelloReply.newBuilder()
                        .setMessage("Echo: " + request.getName())
                        .build();
                responseObserver.onNext(reply);
            }

            @Override
            public void onError(final Throwable t) {
                responseObserver.onError(t);
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }
}

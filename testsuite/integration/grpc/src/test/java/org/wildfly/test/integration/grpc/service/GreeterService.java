/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.integration.grpc.service;

import io.grpc.stub.StreamObserver;

/**
 * Simple gRPC greeter service implementation.
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
public class GreeterService extends GreeterGrpc.GreeterImplBase {

    @Override
    public void sayHello(final HelloRequest request, final StreamObserver<HelloReply> responseObserver) {
        String name = request.getName();
        if (name == null || name.isEmpty()) {
            name = "World";
        }

        final HelloReply reply = HelloReply.newBuilder()
                .setMessage("Hello, " + name + "!")
                .build();

        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }
}

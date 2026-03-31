/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.integration.grpc.service.cdi;

import io.grpc.stub.StreamObserver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.wildfly.test.integration.grpc.service.GreeterGrpc;
import org.wildfly.test.integration.grpc.service.HelloReply;
import org.wildfly.test.integration.grpc.service.HelloRequest;

/**
 * gRPC service with CDI integration.
 * Demonstrates @ApplicationScoped and @Inject usage.
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
@ApplicationScoped
public class CdiGreeterService extends GreeterGrpc.GreeterImplBase {

    @Inject
    private GreetingService greetingService;

    @Override
    public void sayHello(final HelloRequest request, final StreamObserver<HelloReply> responseObserver) {
        final String greeting = greetingService.formatGreeting(request.getName());

        final HelloReply reply = HelloReply.newBuilder()
                .setMessage(greeting)
                .build();

        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }
}

/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.grpc._private;

import static org.jboss.logging.Logger.Level.ERROR;
import static org.jboss.logging.Logger.Level.INFO;

import java.lang.invoke.MethodHandles;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.annotations.Cause;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

/**
 * Log messages for WildFly gRPC Extension.
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
@MessageLogger(projectCode = "WFLYGRPC", length = 4)
public interface GrpcLogger extends BasicLogger {

    /**
     * A logger with the category {@code org.wildfly.extension.grpc}.
     */
    GrpcLogger LOGGER = Logger.getMessageLogger(MethodHandles.lookup(), GrpcLogger.class, "org.wildfly.extension.grpc");

    // TODO (jrp) for all the current logs we need to decide if these are too verbose, IoW they might need to be debug logs

    @LogMessage(level = INFO)
    @Message(id = 1, value = "Activating gRPC Subsystem")
    void activatingSubsystem();

    @LogMessage(level = INFO)
    @Message(id = 2, value = "Starting gRPC server")
    void startingGrpcServer();

    @LogMessage(level = INFO)
    @Message(id = 3, value = "Stopping gRPC server")
    void stoppingGrpcServer();

    @LogMessage(level = ERROR)
    @Message(id = 10, value = "Failed to stop gRPC server")
    void failedToStopServer(@Cause Throwable cause);

    @LogMessage(level = ERROR)
    @Message(id = 11, value = "Failed to stop gRPC server for deployment %s")
    void failedToStopServer(@Cause Throwable cause, String deploymentName);

    @Message(id = 100, value = "Class does not implement BindableService: %s")
    IllegalArgumentException notBindableService(String className);

    @Message(id = 101, value = "gRPC requires HTTP/2")
    IllegalStateException notHttp2Request();

    @Message(id = 102, value = "Failed to start gRPC server")
    RuntimeException failedToStartServer(@Cause Throwable cause);

    @Message(id = 110, value = "Method not found")
    String methodNotFound();

    @Message(id = 111, value = "Failed to process gRPC request")
    String failedToProcessRequest();

    @Message(id = 112, value = "Failed to send response data")
    String failedToSendResponseData();
}

/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.test.integration.observability.micrometer;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListener;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wildfly.plugin.tools.server.ServerManager;

@RunWith(Arquillian.class)
@RunAsClient
public class MicrometerRemovalTestCase {
    private static final String ERROR_MESSAGE = "Failed to publish metrics to OTLP receiver";
    private static final Logger log = LoggerFactory.getLogger(MicrometerRemovalTestCase.class);
    private static final ModelNode micrometerExtension = Operations.createAddress("extension", "org.wildfly.extension.micrometer");
    private static final ModelNode micrometerSubsystem = Operations.createAddress("subsystem", "micrometer");

    @ArquillianResource
    private ServerManager serverManager;

    private String logFilePath;

    @Before
    public void getLogLocation() throws IOException {
        // /path=jboss.server.log.dir:read-attribute(name="path")
        ModelNode address = Operations.createAddress("path", "jboss.server.log.dir");
        ModelNode op = Operations.createReadAttributeOperation(address, "path");
        final ModelNode result = serverManager.executeOperation(op);
        logFilePath = result.get("result").asString();
    }

    @Test
    public void testRemoval() throws Exception {
        ServerLogTailerListener listener = new ServerLogTailerListener();
        try (
                Tailer ignored = Tailer.builder()
                        .setFile(new File(logFilePath, "server.log"))
                        .setTailerListener(listener)
                        .setDelayDuration(Duration.ofMillis(500))
                        .get()
        ) {
            try {
                setupSubsystem();
                Thread.sleep(1000);
            } finally {
                removeSubsystem();
            }
            Thread.sleep(1000);
            // Micrometer will push one last time while the registry is shutting down. Sleep long enough to allow that to
            // happen, then clear the log, wait, then check again. The server is configured to push every millisecond, so
            // 500 should be sufficient to give that time without slowing down the test suite more than necessary.
            Thread.sleep(1000);
            listener.logs.clear();
            Thread.sleep(1000);
            System.out.println(listener.logs);
            Assert.assertTrue("Micrometer has been removed, but errors are still being logged.",
                    listener.logs.stream().noneMatch(l -> l.contains(ERROR_MESSAGE)));
        }
    }

    private void setupSubsystem() throws IOException {
        try {
            if (!Operations.isSuccessfulOutcome(serverManager.client()
                    .execute(Operations.createReadResourceOperation(micrometerExtension)))) {
                serverManager.executeOperation(Operations.createAddOperation(micrometerExtension));
            }

            if (!Operations.isSuccessfulOutcome(serverManager.client()
                    .execute(Operations.createReadResourceOperation(micrometerSubsystem)))) {
                ModelNode addOp = Operations.createAddOperation(micrometerSubsystem);
                addOp.get("endpoint").set("http://localhost:4318/v1/metrics/v1/metrics");
                serverManager.executeOperation(addOp);
            }

            serverManager.executeOperation(Operations.createWriteAttributeOperation(micrometerSubsystem, "endpoint", "http://localhost:4318/v1/metrics/v1/metrics"));
            serverManager.executeOperation(Operations.createWriteAttributeOperation(micrometerSubsystem, "step", "1"));
        } finally {
            serverManager.reloadIfRequired();
        }
    }

    private void removeSubsystem() throws IOException {
        try {
            serverManager.executeOperation(Operations.createRemoveOperation(micrometerSubsystem));
            serverManager.executeOperation(Operations.createRemoveOperation(micrometerExtension));
        } finally {
            serverManager.reloadIfRequired();
        }
    }

    private static class ServerLogTailerListener implements TailerListener {
        List<String> logs = new ArrayList<>();

        @Override
        public void fileNotFound() {
            throw new RuntimeException("Log file not found.");
        }

        @Override
        public void fileRotated() {
            logs.clear();
        }

        @Override
        public void handle(Exception ex) {
            throw new RuntimeException(ex);
        }

        @Override
        public void handle(String line) {
            logs.add(line);
        }

        @Override
        public void init(Tailer tailer) {

        }
    }
}

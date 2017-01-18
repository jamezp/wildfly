/*
 * Copyright 2017 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.servlet.dist;

import static org.jboss.as.controller.client.helpers.ClientConstants.CONTROLLER_PROCESS_STATE_STARTING;
import static org.jboss.as.controller.client.helpers.ClientConstants.CONTROLLER_PROCESS_STATE_STOPPING;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.as.controller.client.helpers.domain.ServerIdentity;
import org.jboss.as.controller.client.helpers.domain.ServerStatus;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.core.launcher.DomainCommandBuilder;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.core.launcher.StandaloneCommandBuilder;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("MagicNumber")
public class ServerStartupTest {
    private static final String JBOSS_HOME = System.getProperty("jboss.home");
    private static final ModelNode EMPTY_ADDRESS = new ModelNode().setEmptyList();
    private static final Logger LOGGER = Logger.getLogger(ServerStartupTest.class);

    private static final long STARTUP_TIMEOUT = TimeoutUtil.adjust(60);
    private static final int SHUTDOWN_TIMEOUT = TimeoutUtil.adjust(30);

    private Process process;

    @After
    public void killProcess() {
        // Ensure the process is dead
        if (process != null) {
            process.destroyForcibly();
        }
        process = null;
    }

    @Test
    public void startStandalone() throws Exception {
        // Find all standalone configuration files
        final Path configDir = Paths.get(JBOSS_HOME, "standalone", "configuration");
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(configDir, "standalone*.xml")) {
            for (Path configFile : stream) {
                startStandalone(configFile.getFileName().toString());
            }
        }
    }

    @Test
    public void startDomain() throws Exception {
        // Find all domain configuration files
        final Path configDir = Paths.get(JBOSS_HOME, "domain", "configuration");
        try (final DirectoryStream<Path> domainStream = Files.newDirectoryStream(configDir, "domain*.xml")) {
            for (Path domainConfig : domainStream) {
                startDomain(domainConfig.getFileName().toString(), "host.xml");
            }
        }
    }

    private void startStandalone(final String configFile) throws Exception {
        final StandaloneCommandBuilder commandBuilder = StandaloneCommandBuilder.of(JBOSS_HOME)
                .setServerConfiguration(configFile);
        process = Launcher.of(commandBuilder)
                //.redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .setRedirectErrorStream(true)
                .launch();
        try (final ModelControllerClient client = TestSuiteEnvironment.getModelControllerClient()) {
            waitForStandalone(client, STARTUP_TIMEOUT);
            Assert.assertTrue("Standalone server does not appear to be running.", isStandaloneRunning(client));
            shutdownStandalone(client);
        }
    }

    private void startDomain(final String domainConfig, final String hostConfig) throws Exception {
        final DomainCommandBuilder commandBuilder = DomainCommandBuilder.of(JBOSS_HOME)
                .setDomainConfiguration(domainConfig)
                .setHostConfiguration(hostConfig);
        process = Launcher.of(commandBuilder)
                //.redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .setRedirectErrorStream(true)
                .launch();
        try (final DomainClient client = DomainClient.Factory.create(TestSuiteEnvironment.getModelControllerClient())) {
            waitForDomain(client, STARTUP_TIMEOUT);
            Assert.assertTrue("Domain server does not appear to be running.", isDomainRunning(client));
            shutdownDomain(client);
        }
    }

    /**
     * Waits the given amount of time in seconds for a managed domain to start. A domain is considered started when each
     * of the servers in the domain are started unless the server is disabled.
     * <p>
     * If the {@code process} is not {@code null} and a timeout occurs the process will be
     * {@linkplain Process#destroy() destroyed}.
     * </p>
     *
     * @param client         the client used to communicate with the server
     * @param startupTimeout the time, in seconds, to wait for the server start
     *
     * @throws InterruptedException if interrupted while waiting for the server to start
     */
    private void waitForDomain(final ModelControllerClient client, final long startupTimeout) throws InterruptedException {
        long timeout = startupTimeout * 1000;
        final long sleep = 100;
        while (timeout > 0) {
            long before = System.currentTimeMillis();
            if (isDomainRunning(client)) {
                break;
            }
            timeout -= (System.currentTimeMillis() - before);
            if (process == null) {
                Assert.fail("Process does not appear to have been started.");
            } else if (!process.isAlive()) {
                Assert.fail(String.format("The process has unexpectedly exited with code %d", process.exitValue()));
            }
            TimeUnit.MILLISECONDS.sleep(sleep);
            timeout -= sleep;
        }
        if (timeout <= 0) {
            if (process != null) {
                process.destroy();
            }
            Assert.fail(String.format("The server did not start within %s seconds.", startupTimeout));
        }
    }

    /**
     * Checks to see if the domain is running. If the server is not in admin only mode each servers running state is
     * checked. If any server is not in a started state the domain is not considered to be running.
     *
     * @param client the client used to communicate with the server
     *
     * @return {@code true} if the server is in a running state, otherwise {@code false}
     */
    private boolean isDomainRunning(final ModelControllerClient client) {
        return process != null && isDomainRunning(client, false);
    }

    /**
     * Shuts down a managed domain container. The servers are first stopped, then the host controller is shutdown.
     *
     * @param client the client used to communicate with the server
     *
     * @throws IOException if an error occurs communicating with the server
     */
    private void shutdownDomain(final ModelControllerClient client) throws IOException, InterruptedException {
        // Note the following two operations used to shutdown a domain don't seem to work well in a composite operation.
        // The operation occasionally sees a java.util.concurrent.CancellationException because the operation client
        // is likely closed before the AsyncFuture.get() is complete. Using a non-composite operation doesn't seem to
        // have this issue.

        // First shutdown the servers
        final ModelNode stopServersOp = Operations.createOperation("stop-servers");
        stopServersOp.get("blocking").set(true);
        ModelNode response = client.execute(stopServersOp);
        if (!Operations.isSuccessfulOutcome(response)) {
            Assert.fail(Operations.getFailureDescription(response).asString());
        }

        // Now shutdown the host
        final ModelNode address = determineHostAddress(client);
        final ModelNode shutdownOp = Operations.createOperation("shutdown", address);
        response = client.execute(shutdownOp);
        if (Operations.isSuccessfulOutcome(response)) {
            // Wait until the process has died
            while (true) {
                if (isDomainRunning(client, true)) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(20L);
                    } catch (InterruptedException e) {
                        LOGGER.debug("Interrupted during sleep", e);
                    }
                } else {
                    break;
                }
            }
        } else {
            Assert.fail(Operations.getFailureDescription(response).asString());
        }
        if (process.waitFor(SHUTDOWN_TIMEOUT, TimeUnit.SECONDS)) {
            process = null;
        } else {
            Assert.fail(String.format("Standalone process failed to terminate within %d seconds", SHUTDOWN_TIMEOUT));
        }
    }

    /**
     * Waits the given amount of time in seconds for a standalone server to start.
     * <p>
     * If the {@code process} is not {@code null} and a timeout occurs the process will be
     * {@linkplain Process#destroy() destroyed}.
     * </p>
     *
     * @param client         the client used to communicate with the server
     * @param startupTimeout the time, in seconds, to wait for the server start
     *
     * @throws InterruptedException if interrupted while waiting for the server to start
     */
    private void waitForStandalone(final ModelControllerClient client, final long startupTimeout) throws InterruptedException {
        long timeout = startupTimeout * 1000;
        final long sleep = 100L;
        while (timeout > 0) {
            long before = System.currentTimeMillis();
            if (isStandaloneRunning(client))
                break;
            timeout -= (System.currentTimeMillis() - before);
            if (process == null) {
                Assert.fail("Process does not appear to have been started.");
            } else if (!process.isAlive()) {
                Assert.fail(String.format("The process has unexpectedly exited with code %d", process.exitValue()));
            }
            TimeUnit.MILLISECONDS.sleep(sleep);
            timeout -= sleep;
        }
        if (timeout <= 0) {
            if (process != null) {
                process.destroy();
            }
            Assert.fail(String.format("The server did not start within %s seconds.", startupTimeout));
        }
    }

    /**
     * Checks to see if a standalone server is running.
     *
     * @param client the client used to communicate with the server
     *
     * @return {@code true} if the server is running, otherwise {@code false}
     */
    private boolean isStandaloneRunning(final ModelControllerClient client) {
        if (process == null) return false;
        try {
            final ModelNode response = client.execute(Operations.createReadAttributeOperation(EMPTY_ADDRESS, "server-state"));
            if (Operations.isSuccessfulOutcome(response)) {
                final String state = Operations.readResult(response).asString();
                return !CONTROLLER_PROCESS_STATE_STARTING.equals(state)
                        && !CONTROLLER_PROCESS_STATE_STOPPING.equals(state);
            }
        } catch (RuntimeException | IOException e) {
            LOGGER.debug("Interrupted determining if standalone is running", e);
        }
        return false;
    }

    /**
     * Shuts down a standalone server.
     *
     * @param client the client used to communicate with the server
     *
     * @throws IOException if an error occurs communicating with the server
     */
    private void shutdownStandalone(final ModelControllerClient client) throws IOException, InterruptedException {
        final ModelNode response = client.execute(Operations.createOperation("shutdown"));
        if (!Operations.isSuccessfulOutcome(response)) {
            Assert.fail(String.format("Shutdown of standalone server failed: %s", Operations.getFailureDescription(response)));
        }
        if (process.waitFor(SHUTDOWN_TIMEOUT, TimeUnit.SECONDS)) {
            process = null;
        } else {
            Assert.fail(String.format("Standalone process failed to terminate within %d seconds", SHUTDOWN_TIMEOUT));
        }
    }

    private static boolean isDomainRunning(final ModelControllerClient client, boolean shutdown) {
        final DomainClient domainClient = (client instanceof DomainClient ? (DomainClient) client : DomainClient.Factory.create(client));
        try {
            // Check for admin-only
            final ModelNode hostAddress = determineHostAddress(client);
            final CompositeOperationBuilder builder = CompositeOperationBuilder.create()
                    .addStep(Operations.createReadAttributeOperation(hostAddress, "running-mode"))
                    .addStep(Operations.createReadAttributeOperation(hostAddress, "host-state"));
            ModelNode response = domainClient.execute(builder.build());
            if (Operations.isSuccessfulOutcome(response)) {
                response = Operations.readResult(response);
                if ("ADMIN_ONLY".equals(Operations.readResult(response.get("step-1")).asString())) {
                    if (Operations.isSuccessfulOutcome(response.get("step-2"))) {
                        final String state = Operations.readResult(response).asString();
                        return !CONTROLLER_PROCESS_STATE_STARTING.equals(state)
                                && !CONTROLLER_PROCESS_STATE_STOPPING.equals(state);
                    }
                }
            }
            final Map<ServerIdentity, ServerStatus> servers = new HashMap<>();
            final Map<ServerIdentity, ServerStatus> statuses = domainClient.getServerStatuses();
            for (ServerIdentity id : statuses.keySet()) {
                final ServerStatus status = statuses.get(id);
                switch (status) {
                    case DISABLED:
                    case STARTED: {
                        servers.put(id, status);
                        break;
                    }
                }
            }
            if (shutdown) {
                return statuses.isEmpty();
            }
            return statuses.size() == servers.size();
        } catch (Exception e) {
            LOGGER.debug("Interrupted determining if domain is running", e);
        }
        return false;
    }

    /**
     * Determines the address for the host being used.
     *
     * @param client the client used to communicate with the server
     *
     * @return the address of the host
     *
     * @throws IOException if an error occurs communicating with the server
     */
    private static ModelNode determineHostAddress(final ModelControllerClient client) throws IOException, InterruptedException {
        long timeout = STARTUP_TIMEOUT * 1000;
        ModelNode hostAddress = null;
        // The server may not be ready to accept operations, we'll wait for a maximum of 60 seconds for the server to
        // become available for management operations
        while (hostAddress == null) {
            hostAddress = getHostAddress(client);
            // The server may not be up yet, wait briefly before trying again
            if (hostAddress == null) {
                TimeUnit.MILLISECONDS.sleep(50L);
                timeout -= 50L;
            }
            if (timeout <= 0) {
                Assert.fail(String.format("Failed to determine if a domain server was running with %d seconds", STARTUP_TIMEOUT));
                // Should never be hit
                break;
            }
        }
        return hostAddress;
    }

    private static ModelNode getHostAddress(final ModelControllerClient client) throws IOException, InterruptedException {
        final ModelNode op = Operations.createReadAttributeOperation(EMPTY_ADDRESS, "local-host-name");
        ModelNode response = client.execute(op);
        if (Operations.isSuccessfulOutcome(response)) {
            return Operations.createAddress("host", Operations.readResult(response).asString());
        }
        LOGGER.debugf("Could not determine host name: %s", Operations.getFailureDescription(response).asString());
        return null;
    }
}

/*
 * Copyright 2020 Red Hat, Inc.
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

package org.jboss.as.test.manualmode.logging;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.jboss.arquillian.container.test.api.ContainerController;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
import org.jboss.as.test.integration.common.HttpRequest;
import org.jboss.as.test.integration.management.util.ServerReload;
import org.jboss.as.test.module.util.ModuleBuilder;
import org.jboss.as.test.shared.ServerSnapshot;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.Asset;
import org.jboss.shrinkwrap.api.asset.ByteArrayAsset;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
abstract class AbstractLogRouterTest {
    static final String JSON_FORMATTER_NAME = "json";
    static final String LOG_FILE = "json.log";
    static final String DEFAULT_CONTAINER = "jbossas-non-clustered";

    private final String[] deploymentNames;
    private final String[] logFileNames;

    private static Runnable moduleCleaner;
    private static ModelControllerClient client;
    private static AutoCloseable snapshotRestore;

    @ArquillianResource
    ContainerController container;

    @ArquillianResource
    Deployer deployer;

    protected AbstractLogRouterTest(final String[] deploymentNames, final String[] logFileNames) {
        this.deploymentNames = deploymentNames;
        this.logFileNames = logFileNames;
    }

    @BeforeClass
    public static void setup() throws Exception {
        moduleCleaner = createModule();
        client = TestSuiteEnvironment.getModelControllerClient();

    }

    @AfterClass
    public static void cleanup() throws Exception {
        client.close();

        if (moduleCleaner != null) {
            moduleCleaner.run();
        }
    }

    @Test
    @InSequence(-1)
    public void startAndConfigureContainer() throws Exception {
        container.start(DEFAULT_CONTAINER);
        snapshotRestore = ServerSnapshot.takeSnapshot(client);
        final CompositeOperationBuilder builder = initConfig();
        configureContainer(builder);
        executeOperation(builder.build());
        for (String deploymentName : deploymentNames) {
            deployer.deploy(deploymentName);
        }
    }

    @Test
    @InSequence(Integer.MAX_VALUE)
    public void shutdown() throws Exception {
        for (String deploymentName : deploymentNames) {
            deployer.undeploy(deploymentName);
        }

        snapshotRestore.close();

        // Get the log directory before the container is shutdown
        final Path logDir = resolveLogDirectory();
        container.stop(DEFAULT_CONTAINER);

        Files.deleteIfExists(logDir.resolve(LOG_FILE));
        for (String fileName : logFileNames) {
            Files.deleteIfExists(logDir.resolve(fileName));
        }
    }

    @SuppressWarnings("SameParameterValue")
    void configureContainer(final CompositeOperationBuilder builder) {
    }

    List<JsonObject> readJsonLogFileFromModel(final String logProfileName, final String logFileName) throws IOException {
        return readJsonLogFile(logProfileName, logFileName, true);
    }

    List<JsonObject> readJsonLogFile(final String logFileName) throws IOException {
        return readJsonLogFile(null, logFileName, false);
    }

    void addProfile(final Operations.CompositeOperationBuilder builder, final String profileName, final String fileName) {
        final ModelNode address = Operations.createAddress("subsystem", "logging", "logging-profile", profileName);
        builder.addStep(Operations.createAddOperation(address));

        // Add a JSON formatter
        builder.addStep(Operations.createAddOperation(Operations.createAddress("subsystem", "logging", "logging-profile", profileName, "json-formatter", JSON_FORMATTER_NAME)));

        // add file handler
        builder.addStep(createFileAddOp(profileName, fileName, fileName));

        // add root logger
        final ModelNode op = Operations.createAddOperation(Operations.createAddress("subsystem", "logging", "logging-profile", profileName, "root-logger", "ROOT"));
        op.get("level").set("INFO");
        op.get("handlers").setEmptyList().add(fileName);
        builder.addStep(op);
    }

    private List<JsonObject> readJsonLogFile(final String logProfileName, final String logFileName, final boolean fromModel) throws IOException {
        final List<JsonObject> lines = new ArrayList<>();
        try (
                BufferedReader reader = fromModel ? readLogFileFromModel(client, logProfileName, logFileName) : readLogFile(logFileName)
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                try (JsonReader jsonReader = Json.createReader(new StringReader(line))) {
                    lines.add(jsonReader.readObject());
                }
            }
        }
        return lines;
    }

    private BufferedReader readLogFileFromModel(final ModelControllerClient client, final String logProfileName, final String logFileName) throws IOException {
        final ModelNode address;
        if (logProfileName == null) {
            address = Operations.createAddress("subsystem", "logging", "log-file", logFileName);
        } else {
            address = Operations.createAddress("subsystem", "logging", "logging-profile", logProfileName, "log-file", logFileName);
        }
        final ModelNode op = Operations.createReadAttributeOperation(address, "stream");
        final OperationResponse response = client.executeOperation(Operation.Factory.create(op), OperationMessageHandler.logging);
        final ModelNode result = response.getResponseNode();
        if (Operations.isSuccessfulOutcome(result)) {
            final OperationResponse.StreamEntry entry = response.getInputStream(Operations.readResult(result)
                    .asString());
            if (entry == null) {
                throw new RuntimeException(String.format("Failed to find entry with UUID %s for log file %s",
                        Operations.readResult(result).asString(), logFileName));
            }
            return new BufferedReader(new InputStreamReader(entry.getStream(), StandardCharsets.UTF_8));
        }
        throw new RuntimeException(String.format("Failed to read log file %s: %s", logFileName, Operations.getFailureDescription(result)
                .asString()));
    }

    private BufferedReader readLogFile(final String logFileName) throws IOException {
        final Path path = resolveLogDirectory().resolve(logFileName);
        Assert.assertTrue("Path " + path + " does not exist.", Files.exists(path));
        return Files.newBufferedReader(path, StandardCharsets.UTF_8);
    }

    private Path resolveLogDirectory() throws IOException {
        final ModelNode address = Operations.createAddress("path", "jboss.server.log.dir");
        final ModelNode op = Operations.createOperation("path-info", address);
        final ModelNode result = executeOperation(op);
        final Path dir = Paths.get(result.get("path", "resolved-path").asString());
        Assert.assertTrue("Log directory " + dir + " does not exist", Files.exists(dir));
        return dir;
    }

    private CompositeOperationBuilder initConfig() {

        final CompositeOperationBuilder builder = CompositeOperationBuilder.create();

        // Create a JSON formatter on the default log context
        final ModelNode address = Operations.createAddress("subsystem", "logging", "json-formatter", JSON_FORMATTER_NAME);
        builder.addStep(Operations.createAddOperation(address));

        // Add a new file handler to write JSON logs to
        ModelNode op = createFileAddOp(null, "json-file", LOG_FILE);
        builder.addStep(op);

        // Add the new handler to the root logger
        op = Operations.createOperation("add-handler", Operations.createAddress("subsystem", "logging", "root-logger", "ROOT"));
        op.get("name").set("json-file");
        builder.addStep(op);
        op = Operations.createOperation("remove-handler", Operations.createAddress("subsystem", "logging", "root-logger", "ROOT"));
        op.get("name").set("json-file");
        return builder;
    }

    static WebArchive createDeployment(final String deploymentName) {
        return createDeployment(deploymentName, null);
    }

    static WebArchive createDeployment(final String deploymentName, final String profile) {
        final WebArchive war = ShrinkWrap.create(WebArchive.class, deploymentName)
                .addClass(LoggingServlet.class);
        if (profile != null) {
            war.addAsManifestResource(new StringAsset("Dependencies: org.jboss.as.logging.test\nLogging-Profile: " + profile + "\n"), "MANIFEST.MF");
        } else {
            war.addAsManifestResource(new StringAsset("Dependencies: org.jboss.as.logging.test\n"), "MANIFEST.MF");
        }
        return war;
    }

    static void performCall(final String url) throws Exception {
        HttpRequest.get(url, TimeoutUtil.adjust(10), TimeUnit.SECONDS);
    }

    static void assertUnexpectedLogs(final Collection<JsonObject> unexpectedLogs, final String logFile) {
        if (!unexpectedLogs.isEmpty()) {
            final StringBuilder msg = new StringBuilder("Found unexpected log messages in file")
                    .append(logFile)
                    .append(':');
            for (JsonObject logMsg : unexpectedLogs) {
                msg.append(System.lineSeparator())
                        .append('\t')
                        .append('[').append(logMsg.getString("level")).append("] ")
                        .append(logMsg.getString("loggerClassName")).append(' ')
                        .append('(').append(logMsg.getString("loggerName")).append(") ")
                        .append(logMsg.getString("message"));
            }
            Assert.fail(msg.toString());
        }
    }

    ModelNode executeOperation(final ModelNode op) throws IOException {
        return executeOperation(Operation.Factory.create(op));
    }

    ModelNode executeOperation(final Operation op) throws IOException {
        return executeOperation(op, true);
    }

    ModelNode executeOperation(final Operation op, boolean allowReload) throws IOException {
        final ModelNode result = client.execute(op);
        if (!Operations.isSuccessfulOutcome(result)) {
            //Assert.fail(Operations.getFailureDescription(result).toString());
        }
        // Reload if required
        if (allowReload && result.hasDefined(ClientConstants.RESPONSE_HEADERS)) {
            final ModelNode responseHeaders = result.get(ClientConstants.RESPONSE_HEADERS);
            if (responseHeaders.hasDefined("process-state")) {
                if (ClientConstants.CONTROLLER_PROCESS_STATE_RELOAD_REQUIRED.equals(responseHeaders.get("process-state")
                        .asString())) {
                    ServerReload.executeReloadAndWaitForCompletion(client);
                } else if (ClientConstants.CONTROLLER_PROCESS_STATE_RESTART_REQUIRED.equalsIgnoreCase(responseHeaders.get("process-state")
                        .asString())) {
                    // We need to fully restart the server
                    container.stop(DEFAULT_CONTAINER);
                    container.start(DEFAULT_CONTAINER);
                    Assert.assertTrue("Failed to restart the server", container.isStarted(DEFAULT_CONTAINER));
                }
            }
        }
        return Operations.readResult(result);
    }

    static Asset createLoggingConfig(final String fileName) {
        final Properties properties = new Properties();

        // Configure the root logger
        properties.setProperty("logger.level", "INFO");
        properties.setProperty("logger.handlers", fileName);

        // Configure the handler
        properties.setProperty("handler." + fileName, "org.jboss.logmanager.handlers.FileHandler");
        properties.setProperty("handler." + fileName + ".level", "ALL");
        properties.setProperty("handler." + fileName + ".formatter", "json");
        properties.setProperty("handler." + fileName + ".properties", "append,autoFlush,fileName");
        properties.setProperty("handler." + fileName + ".append", "true");
        properties.setProperty("handler." + fileName + ".autoFlush", "true");
        properties.setProperty("handler." + fileName + ".fileName", "${jboss.server.log.dir}" + File.separatorChar + fileName);

        // Add the JSON formatter
        properties.setProperty("formatter.json", "org.jboss.logmanager.formatters.JsonFormatter");

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            properties.store(out, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ByteArrayAsset(out.toByteArray());
    }

    static Asset createLog4jLoggingConfig(final String fileName) {
        final Properties properties = new Properties();

        // Configure the root logger
        properties.setProperty("log4j.rootLogger", "INFO, " + fileName);

        // Configure the handler
        properties.setProperty("log4j.appender." + fileName, "org.apache.log4j.FileAppender");
        properties.setProperty("log4j.appender." + fileName + ".Threashold", "ALL");
        properties.setProperty("log4j.appender." + fileName + ".layout", "org.jboss.as.test.manualmode.logging.JsonLayout");
        //properties.setProperty("log4j.appender." + fileName + ".append", "true");
        //properties.setProperty("log4j.appender." + fileName + ".autoFlush", "true");
        properties.setProperty("log4j.appender." + fileName + ".File", "${jboss.server.log.dir}" + File.separatorChar + fileName);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            properties.store(out, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ByteArrayAsset(out.toByteArray());
    }

    static ModelNode createFileAddOp(final String profileName, final String handlerName, final String fileName) {
        // add file handler
        final ModelNode op;
        if (profileName == null) {
            op = Operations.createAddOperation(Operations.createAddress("subsystem", "logging", "file-handler", handlerName));
        } else {
            op = Operations.createAddOperation(Operations.createAddress("subsystem", "logging", "logging-profile", profileName, "file-handler", handlerName));
        }
        op.get("level").set("INFO");
        op.get("append").set(true);
        op.get("autoflush").set(true);
        final ModelNode file = new ModelNode();
        file.get("relative-to").set("jboss.server.log.dir");
        file.get("path").set(fileName);
        op.get("file").set(file);
        op.get("named-formatter").set(JSON_FORMATTER_NAME);
        return op;
    }

    private static Runnable createModule() {
        return ModuleBuilder.of("org.jboss.as.logging.test", "logging-test.jar")
                .addClasses(StaticModuleLogger.class, JsonLayout.class)
                .addDependencies(
                        "javax.json.api",
                        "java.logging",
                        "org.apache.commons.logging",
                        "org.apache.log4j",
                        "org.jboss.logging",
                        "org.slf4j")
                .build();
    }

    @SuppressWarnings({"UnusedReturnValue", "SameParameterValue"})
    static class UrlBuilder {
        private final URL url;
        private final String[] paths;
        private final Map<String, String> params;

        private UrlBuilder(final URL url, final String... paths) {
            this.url = url;
            this.paths = paths;
            params = new HashMap<>();
        }

        static UrlBuilder of(final URL url, final String... paths) {
            return new UrlBuilder(url, paths);
        }

        UrlBuilder addParameter(final String key, final String value) {
            params.put(key, value);
            return this;
        }

        String build() throws UnsupportedEncodingException {
            final StringBuilder result = new StringBuilder(url.toExternalForm());
            if (paths != null) {
                for (String path : paths) {
                    result.append('/').append(path);
                }
            }
            boolean isFirst = true;
            for (String key : params.keySet()) {
                if (isFirst) {
                    result.append('?');
                } else {
                    result.append('&');
                }
                final String value = params.get(key);
                result.append(URLEncoder.encode(key, "UTF-8")).append('=').append(URLEncoder.encode(value, "UTF-8"));
                isFirst = false;
            }
            return result.toString();
        }
    }
}

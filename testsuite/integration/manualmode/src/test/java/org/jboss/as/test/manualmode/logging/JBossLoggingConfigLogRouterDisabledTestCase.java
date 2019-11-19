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

import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javax.json.JsonObject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.junit.InSequence;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("MagicNumber")
@RunWith(Arquillian.class)
@RunAsClient
public class JBossLoggingConfigLogRouterDisabledTestCase extends AbstractLogRouterTest {
    private static final String DEPLOYMENT_1 = "test-jbl-config--disabled-logging-1.war";
    private static final String DEPLOYMENT_2 = "test-jbl-config-disabled-logging-2.war";
    private static final String DEP1_FILE_NAME = "jb-test1-disabled-json.log";
    private static final String DEP2_FILE_NAME = "jb-test2-disabled-json.log";

    public JBossLoggingConfigLogRouterDisabledTestCase() {
        super(new String[] {DEPLOYMENT_1, DEPLOYMENT_2}, new String[] {DEP1_FILE_NAME, DEP2_FILE_NAME});
    }

    @Deployment(name = DEPLOYMENT_1, managed = false)
    @TargetsContainer(DEFAULT_CONTAINER)
    public static WebArchive createDeployment1() {
        return createDeployment(DEPLOYMENT_1)
                .addAsManifestResource(createLoggingConfig(DEP1_FILE_NAME), "logging.properties");
    }

    @Deployment(name = DEPLOYMENT_2, managed = false)
    @TargetsContainer(DEFAULT_CONTAINER)
    public static WebArchive createDeployment2() {
        return createDeployment(DEPLOYMENT_2)
                .addAsManifestResource(createLoggingConfig(DEP2_FILE_NAME), "logging.properties");
    }

    @Test
    @OperateOnDeployment(DEPLOYMENT_1)
    @InSequence(1)
    public void testDeployment1(@ArquillianResource URL url) throws Exception {
        final String suffix = "jb-test-dep-1";
        final UrlBuilder builder = UrlBuilder.of(url, "log");
        builder.addParameter("suffix", suffix);
        performCall(builder.build());

        final List<JsonObject> depLogs = readJsonLogFile(DEP1_FILE_NAME);

        Assert.assertFalse(String.format("Expected log file %s not to be empty.", DEP1_FILE_NAME), depLogs.isEmpty());

        // Check the expected number of log files
        Assert.assertEquals(12L, depLogs.stream()
                .filter(jsonObject -> jsonObject.getString("message").contains(suffix))
                .count());

        // Check the expected dep log file
        Collection<JsonObject> unexpectedLogs = depLogs.stream()
                .filter(logMessage -> {
                    final String loggerName = logMessage.getString("loggerName");
                    return (LoggingServlet.LOGGER_NAME.equals(loggerName) || StaticModuleLogger.LOGGER_NAME.equals(loggerName)) &&
                            !logMessage.getString("message").contains(suffix);
                })
                .collect(Collectors.toList());
        assertUnexpectedLogs(unexpectedLogs, DEP1_FILE_NAME);

        // Now we want to make sure that none of the logs made it into the default log context
        final List<JsonObject> defaultLogs = readJsonLogFileFromModel(null, LOG_FILE);
        unexpectedLogs = defaultLogs.stream()
                .filter(logMessage -> {
                    final String loggerName = logMessage.getString("loggerName");
                    return LoggingServlet.LOGGER_NAME.equals(loggerName) || StaticModuleLogger.LOGGER_NAME.equals(loggerName);
                })
                .collect(Collectors.toList());
        assertUnexpectedLogs(unexpectedLogs, DEP1_FILE_NAME);
    }

    @Test
    @OperateOnDeployment(DEPLOYMENT_2)
    @InSequence(2)
    public void testDeployment2(@ArquillianResource URL url) throws Exception {
        final String suffix = "jb-test-dep-2";
        final UrlBuilder builder = UrlBuilder.of(url, "log");
        builder.addParameter("suffix", suffix);
        performCall(builder.build());
        final List<JsonObject> dep1Logs = readJsonLogFile(DEP1_FILE_NAME);
        final List<JsonObject> dep2Logs = readJsonLogFile(DEP2_FILE_NAME);

        // There should be 4 logs with the deployment name in the DEP1's log as the static loggers were created on that
        // context. These 4 loggers should only be from the static logger.
        Assert.assertEquals(4L, dep1Logs.stream()
                .filter(jsonObject ->
                        jsonObject.getString("message")
                                .contains(suffix) && StaticModuleLogger.LOGGER_NAME.equals(jsonObject.getString("loggerName")))
                .count());
        // Given the above this means 8 should be in DEP2's file
        Assert.assertEquals(8L, dep2Logs.stream()
                .filter(jsonObject -> jsonObject.getString("message").contains(suffix))
                .count());

        // Validate the DEP2 log file only contains messages from that log context
        Collection<JsonObject> unexpectedLogs = dep2Logs.stream()
                .filter(logMessage -> {
                    final String loggerName = logMessage.getString("loggerName");
                    return (LoggingServlet.LOGGER_NAME.equals(loggerName) || StaticModuleLogger.LOGGER_NAME.equals(loggerName)) &&
                            !logMessage.getString("message").contains(suffix);
                })
                .collect(Collectors.toList());
        assertUnexpectedLogs(unexpectedLogs, DEP2_FILE_NAME);

        // Now we want to make sure that none of the logs made it into the default log context
        final List<JsonObject> defaultLogs = readJsonLogFileFromModel(null, LOG_FILE);
        unexpectedLogs = defaultLogs.stream()
                .filter(logMessage -> {
                    final String loggerName = logMessage.getString("loggerName");
                    return LoggingServlet.LOGGER_NAME.equals(loggerName) || StaticModuleLogger.LOGGER_NAME.equals(loggerName);
                })
                .collect(Collectors.toList());
        assertUnexpectedLogs(unexpectedLogs, LOG_FILE);
    }

    @Override
    void configureContainer(final Operations.CompositeOperationBuilder builder) {
        builder.addStep(Operations.createWriteAttributeOperation(Operations.createAddress("subsystem", "logging"), "allow-log-routing", false));
    }
}

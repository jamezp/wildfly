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

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javax.json.JsonObject;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
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
public class Log4jConfigLogRouterTestCase extends AbstractLogRouterTest {
    private static final String DEPLOYMENT_1 = "test-log4j-config-logging-1.war";
    private static final String DEPLOYMENT_2 = "test-log4j-config-logging-2.war";
    private static final String DEP1_SUFFIX = "log4j-test-dep-1";
    private static final String DEP2_SUFFIX = "log4j-test-dep-2";
    private static final String DEP1_FILE_NAME = "log4j-test1-json.log";
    private static final String DEP2_FILE_NAME = "log4j-test2-json.log";

    public Log4jConfigLogRouterTestCase() {
        super(new String[] {DEPLOYMENT_1, DEPLOYMENT_2}, new String[] {DEP1_FILE_NAME, DEP2_FILE_NAME});
    }

    @Deployment(name = DEPLOYMENT_1, managed = false)
    @TargetsContainer(DEFAULT_CONTAINER)
    public static WebArchive createDeployment1() {
        return createDeployment(DEPLOYMENT_1)
                .addAsManifestResource(createLog4jLoggingConfig(DEP1_FILE_NAME), "log4j.properties");
    }

    @Deployment(name = DEPLOYMENT_2, managed = false)
    @TargetsContainer(DEFAULT_CONTAINER)
    public static WebArchive createDeployment2() {
        return createDeployment(DEPLOYMENT_2)
                .addAsManifestResource(createLog4jLoggingConfig(DEP2_FILE_NAME), "log4j.properties");
    }

    @Test
    @OperateOnDeployment(DEPLOYMENT_1)
    public void testDeployment1(@ArquillianResource URL url) throws Exception {
        final UrlBuilder builder = UrlBuilder.of(url, "log");
        builder.addParameter("suffix", DEP1_SUFFIX);
        performCall(builder.build());
        testLogFile(DEP1_SUFFIX);
    }

    @Test
    @OperateOnDeployment(DEPLOYMENT_2)
    public void testDeployment2(@ArquillianResource URL url) throws Exception {
        final UrlBuilder builder = UrlBuilder.of(url, "log");
        builder.addParameter("suffix", DEP2_SUFFIX);
        performCall(builder.build());
        testLogFile(DEP2_SUFFIX);
    }

    private void testLogFile(final String suffix) throws IOException {
        final List<JsonObject> dep1Logs = readJsonLogFile(DEP1_FILE_NAME);
        final List<JsonObject> dep2Logs = readJsonLogFile(DEP2_FILE_NAME);

        // Determine which file has logs we should be expecting the logs in
        final List<JsonObject> depLogs = new ArrayList<>();
        final List<JsonObject> otherDepLogs = new ArrayList<>();
        final String logFileName;
        switch (suffix) {
            case DEP1_SUFFIX:
                depLogs.addAll(dep1Logs);
                otherDepLogs.addAll(dep2Logs);
                logFileName = DEP1_FILE_NAME;
                break;
            case DEP2_SUFFIX:
                depLogs.addAll(dep2Logs);
                otherDepLogs.addAll(dep1Logs);
                logFileName = DEP2_FILE_NAME;
                break;
            default:
                Assert.fail("Deployment suffix " + suffix + " is invalid.");
                // Should never be reached
                logFileName = null;
        }

        Assert.assertFalse(String.format("Expected log file %s not to be empty.", logFileName), depLogs.isEmpty());

        // There should be 12 logs with the suffix
        Assert.assertEquals(12L, depLogs.stream().filter(jsonObject -> jsonObject.getString("message").contains(suffix)).count());

        // Check the expected deployment log file
        Collection<JsonObject> unexpectedLogs = depLogs.stream()
                .filter(logMessage -> {
                    final String loggerName = logMessage.getString("loggerName");
                    return (LoggingServlet.LOGGER_NAME.equals(loggerName) || StaticModuleLogger.LOGGER_NAME.equals(loggerName)) &&
                            !logMessage.getString("message").contains(suffix);
                })
                .collect(Collectors.toList());
        assertUnexpectedLogs(unexpectedLogs, logFileName);

        // Now we want to check that the other deployment does NOT contain any log messages from the expected deployment
        unexpectedLogs = otherDepLogs.stream()
                .filter(logMessage -> {
                    final String loggerName = logMessage.getString("loggerName");
                    return (LoggingServlet.LOGGER_NAME.equals(loggerName) || StaticModuleLogger.LOGGER_NAME.equals(loggerName)) &&
                            logMessage.getString("message").contains(suffix);
                })
                .collect(Collectors.toList());
        assertUnexpectedLogs(unexpectedLogs, logFileName);

        // Now we want to make sure that none of the logs made it into the default log context
        final List<JsonObject> defaultLogs = readJsonLogFileFromModel(null, LOG_FILE);
        unexpectedLogs = defaultLogs.stream()
                .filter(logMessage -> {
                    final String loggerName = logMessage.getString("loggerName");
                    return LoggingServlet.LOGGER_NAME.equals(loggerName) || StaticModuleLogger.LOGGER_NAME.equals(loggerName);
                })
                .collect(Collectors.toList());
        assertUnexpectedLogs(unexpectedLogs, logFileName);
    }
}

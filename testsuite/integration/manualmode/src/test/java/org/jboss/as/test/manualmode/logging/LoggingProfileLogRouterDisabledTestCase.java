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
import java.util.Deque;
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
import org.jboss.dmr.ModelNode;
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
public class LoggingProfileLogRouterDisabledTestCase extends AbstractLogRouterTest {

    private static final String PROFILE1 = "test-profile1";
    private static final String PROFILE2 = "test-profile2";
    private static final String DEPLOYMENT_1 = "test-profile-disabled-logging-1.war";
    private static final String DEPLOYMENT_2 = "test-profile-disabled-logging-2.war";
    private static final String PROFILE1_LOG_FILE = "test-profile1-disabled.log";
    private static final String PROFILE2_LOG_FILE = "test-profile2-disabled.log";

    public LoggingProfileLogRouterDisabledTestCase() {
        super(new String[] {DEPLOYMENT_1, DEPLOYMENT_2}, new String[] {PROFILE1_LOG_FILE, PROFILE2_LOG_FILE});
    }

    @Deployment(name = DEPLOYMENT_1, managed = false)
    @TargetsContainer(DEFAULT_CONTAINER)
    public static WebArchive createDeployment1() {
        return createDeployment(DEPLOYMENT_1, PROFILE1);
    }

    @Deployment(name = DEPLOYMENT_2, managed = false)
    @TargetsContainer(DEFAULT_CONTAINER)
    public static WebArchive createDeployment2() {
        return createDeployment(DEPLOYMENT_2, PROFILE2);
    }

    @Test
    @OperateOnDeployment(DEPLOYMENT_1)
    @InSequence(1)
    public void testProfile1(@ArquillianResource URL url) throws Exception {
        final String suffix = "profile-disabled-1";
        final UrlBuilder builder = UrlBuilder.of(url, "log");
        builder.addParameter("suffix", suffix);
        performCall(builder.build());

        final List<JsonObject> profileLogs = readJsonLogFileFromModel(PROFILE1, PROFILE1_LOG_FILE);

        Assert.assertFalse(String.format("Expected log file %s not to be empty.", PROFILE1_LOG_FILE), profileLogs.isEmpty());

        // Check the expected number of log files
        Assert.assertEquals(12L, profileLogs.stream().filter(jsonObject -> jsonObject.getString("message").contains(suffix)).count());

        // Check the expected profile log file
        Collection<JsonObject> unexpectedLogs = profileLogs.stream()
                .filter(logMessage -> {
                    final String loggerName = logMessage.getString("loggerName");
                    return (LoggingServlet.LOGGER_NAME.equals(loggerName) || StaticModuleLogger.LOGGER_NAME.equals(loggerName)) &&
                            !logMessage.getString("message").contains(suffix);
                })
                .collect(Collectors.toList());
        assertUnexpectedLogs(unexpectedLogs, PROFILE1_LOG_FILE);

        // Now we want to make sure that none of the logs made it into the default log context
        final List<JsonObject> defaultLogs = readJsonLogFileFromModel(null, LOG_FILE);
        unexpectedLogs = defaultLogs.stream()
                .filter(logMessage -> {
                    final String loggerName = logMessage.getString("loggerName");
                    return LoggingServlet.LOGGER_NAME.equals(loggerName) || StaticModuleLogger.LOGGER_NAME.equals(loggerName);
                })
                .collect(Collectors.toList());
        assertUnexpectedLogs(unexpectedLogs, PROFILE1_LOG_FILE);
    }

    @Test
    @OperateOnDeployment(DEPLOYMENT_2)
    @InSequence(2)
    public void testProfile2(@ArquillianResource URL url) throws Exception {
        final String suffix = "profile-disabled-2";
        final UrlBuilder builder = UrlBuilder.of(url, "log");
        builder.addParameter("suffix", suffix);
        performCall(builder.build());
        final List<JsonObject> profile1Logs = readJsonLogFileFromModel(PROFILE1, PROFILE1_LOG_FILE);
        final List<JsonObject> profile2Logs = readJsonLogFileFromModel(PROFILE2, PROFILE2_LOG_FILE);

        // There should be 4 logs with the profile name in the PROFILE1's log as the static loggers were created on that
        // context. These 4 loggers should only be from the static logger.
        Assert.assertEquals(4L, profile1Logs.stream().filter(jsonObject ->
                jsonObject.getString("message").contains(suffix) && StaticModuleLogger.LOGGER_NAME.equals(jsonObject.getString("loggerName"))).count());
        // Given the above this means 8 should be in PROFILE2's file
        Assert.assertEquals(8L, profile2Logs.stream().filter(jsonObject -> jsonObject.getString("message").contains(suffix)).count());

        // Validate the PROFILE2 log file only contains messages from that profile
        Collection<JsonObject> unexpectedLogs = profile2Logs.stream()
                .filter(logMessage -> {
                    final String loggerName = logMessage.getString("loggerName");
                    return (LoggingServlet.LOGGER_NAME.equals(loggerName) || StaticModuleLogger.LOGGER_NAME.equals(loggerName)) &&
                            !logMessage.getString("message").contains(suffix);
                })
                .collect(Collectors.toList());
        assertUnexpectedLogs(unexpectedLogs, PROFILE2_LOG_FILE);

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
    void configureContainer(final Operations.CompositeOperationBuilder builder, final Deque<ModelNode> tearDownOps) {
        builder.addStep(Operations.createWriteAttributeOperation(Operations.createAddress("subsystem", "logging"), "allow-log-routing", false));
        addProfile(builder, PROFILE1, PROFILE1_LOG_FILE);
        addProfile(builder, PROFILE2, PROFILE2_LOG_FILE);
        tearDownOps.add(Operations.createUndefineAttributeOperation(Operations.createAddress("subsystem", "logging"), "allow-log-routing"));
    }
}

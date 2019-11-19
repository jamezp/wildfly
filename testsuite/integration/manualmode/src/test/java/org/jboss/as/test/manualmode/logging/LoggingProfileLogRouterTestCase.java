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
import org.jboss.as.controller.client.helpers.Operations.CompositeOperationBuilder;
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
public class LoggingProfileLogRouterTestCase extends AbstractLogRouterTest {

    private static final String PROFILE1 = "test-profile1";
    private static final String PROFILE2 = "test-profile2";
    private static final String DEPLOYMENT_1 = "test-profile-logging-1.war";
    private static final String DEPLOYMENT_2 = "test-profile-logging-2.war";
    private static final String PROFILE1_LOG_FILE = "test-profile1.log";
    private static final String PROFILE2_LOG_FILE = "test-profile2.log";

    public LoggingProfileLogRouterTestCase() {
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
    public void testProfile1(@ArquillianResource URL url) throws Exception {
        final UrlBuilder builder = UrlBuilder.of(url, "log");
        builder.addParameter("suffix", PROFILE1);
        performCall(builder.build());
        testLogFile(PROFILE1);
    }

    @Test
    @OperateOnDeployment(DEPLOYMENT_2)
    public void testProfile2(@ArquillianResource URL url) throws Exception {
        final UrlBuilder builder = UrlBuilder.of(url, "log");
        builder.addParameter("suffix", PROFILE2);
        performCall(builder.build());
        testLogFile(PROFILE2);
    }

    @Override
    void configureContainer(final CompositeOperationBuilder builder) {
        addProfile(builder, PROFILE1, PROFILE1_LOG_FILE);
        addProfile(builder, PROFILE2, PROFILE2_LOG_FILE);
    }

    private void testLogFile(final String profileName) throws IOException {
        final List<JsonObject> profile1Logs = readJsonLogFileFromModel(PROFILE1, PROFILE1_LOG_FILE);
        final List<JsonObject> profile2Logs = readJsonLogFileFromModel(PROFILE2, PROFILE2_LOG_FILE);

        // Determine which file has logs we should be expecting the logs in
        final List<JsonObject> profileLogs = new ArrayList<>();
        final List<JsonObject> otherProfileLogs = new ArrayList<>();
        final String logFileName;
        switch (profileName) {
            case PROFILE1:
                profileLogs.addAll(profile1Logs);
                otherProfileLogs.addAll(profile2Logs);
                logFileName = PROFILE1_LOG_FILE;
                break;
            case PROFILE2:
                profileLogs.addAll(profile2Logs);
                otherProfileLogs.addAll(profile1Logs);
                logFileName = PROFILE2_LOG_FILE;
                break;
            default:
                Assert.fail("Profile " + profileName + " is invalid.");
                // Should never be reached
                logFileName = null;
        }

        Assert.assertFalse(String.format("Expected log file %s not to be empty.", logFileName), profileLogs.isEmpty());

        // There should be 12 logs with the profile name
        Assert.assertEquals(12L, profileLogs.stream()
                .filter(jsonObject -> jsonObject.getString("message").contains(profileName))
                .count());

        // Check the expected profile log file
        Collection<JsonObject> unexpectedLogs = profileLogs.stream()
                .filter(logMessage -> {
                    final String loggerName = logMessage.getString("loggerName");
                    return (LoggingServlet.LOGGER_NAME.equals(loggerName) || StaticModuleLogger.LOGGER_NAME.equals(loggerName)) &&
                            !logMessage.getString("message").contains(profileName);
                })
                .collect(Collectors.toList());
        assertUnexpectedLogs(unexpectedLogs, logFileName);

        // Now we want to check that the other profile does NOT contain any log messages from the expected profile
        unexpectedLogs = otherProfileLogs.stream()
                .filter(logMessage -> {
                    final String loggerName = logMessage.getString("loggerName");
                    return (LoggingServlet.LOGGER_NAME.equals(loggerName) || StaticModuleLogger.LOGGER_NAME.equals(loggerName)) &&
                            logMessage.getString("message").contains(profileName);
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

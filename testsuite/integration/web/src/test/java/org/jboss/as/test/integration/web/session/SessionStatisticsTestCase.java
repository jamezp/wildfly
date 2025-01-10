/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.web.session;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;

import java.net.URI;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.test.integration.management.ManagementOperations;
import org.jboss.as.test.shared.ServerReload;
import org.jboss.as.test.shared.SnapshotRestoreSetupTask;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.xnio.IoUtils;

import io.undertow.util.StatusCodes;

/**
 */
@ExtendWith(ArquillianExtension.class)
@RunAsClient
@ServerSetup(SessionStatisticsTestCase.SessionStatisticsServerSetup.class)
public class SessionStatisticsTestCase {
    private static final String ACTIVE_SESSIONS = "active-sessions";
    private static final String EXPIRED_SESSIONS = "expired-sessions";
    private static final String HIGHEST_SESSION_COUNT = "highest-session-count";
    private static final String MAX_ACTIVE_SESSIONS = "max-active-sessions";
    private static final String REJECTED_SESSIONS = "rejected-sessions";
    private static final String SESSIONS_CREATED = "sessions-created";

    private static final int CLIENT_COUNT = 7;
    private static final int MAX_SESSIONS = 4;
    @ArquillianResource
    public ManagementClient managementClient;

    static class SessionStatisticsServerSetup extends SnapshotRestoreSetupTask {

        @Override
        public void doSetup(ManagementClient managementClient, String containerId) throws Exception {
            ModelNode op = new ModelNode();
            op.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
            op.get(OP_ADDR).add(SUBSYSTEM, "undertow");
            op.get(NAME).set("statistics-enabled");
            op.get(VALUE).set(true);
            ManagementOperations.executeOperation(managementClient.getControllerClient(), op);
            ServerReload.executeReloadAndWaitForCompletion(managementClient);
        }
    }

    @ArquillianResource
    private URI uri;

    @Deployment
    public static Archive<?> dependent() {
        return ShrinkWrap.create(WebArchive.class, "stats.war")
                .addClasses(SessionTestServlet.class)
                .addAsWebInfResource(new StringAsset("<jboss-web><max-active-sessions>" + MAX_SESSIONS + "</max-active-sessions></jboss-web>"), "jboss-web.xml");
    }

    @Test
    public void testSessionManagementOperations() throws Exception {
        CloseableHttpClient[] clients = new CloseableHttpClient[CLIENT_COUNT];
        try {
            for (int i = 0; i < CLIENT_COUNT; ++i) {
                clients[i] = HttpClients.createDefault();
            }
            ModelNode operation = new ModelNode();
            operation.get(ModelDescriptionConstants.OP).set(ModelDescriptionConstants.READ_RESOURCE_OPERATION);
            operation.get(ModelDescriptionConstants.INCLUDE_RUNTIME).set(true);
            operation.get(ModelDescriptionConstants.OP_ADDR).set(PathAddress.parseCLIStyleAddress("/deployment=stats.war/subsystem=undertow").toModelNode());
            ModelNode opRes = managementClient.getControllerClient().execute(operation);
            Assertions.assertEquals("success", opRes.get(ModelDescriptionConstants.OUTCOME).asString(), opRes.toString());
            ModelNode result = opRes.get(ModelDescriptionConstants.RESULT);
            //check everything is zero
            Assertions.assertEquals(0, result.get(ACTIVE_SESSIONS).asInt());
            Assertions.assertEquals(0, result.get(EXPIRED_SESSIONS).asInt());
            Assertions.assertEquals(0, result.get(HIGHEST_SESSION_COUNT).asInt());
            Assertions.assertEquals(4, result.get(MAX_ACTIVE_SESSIONS).asInt());
            Assertions.assertEquals(0, result.get(REJECTED_SESSIONS).asInt());
            Assertions.assertEquals(0, result.get("session-avg-alive-time").asInt());
            Assertions.assertEquals(0, result.get("session-max-alive-time").asInt());
            Assertions.assertEquals(0, result.get(SESSIONS_CREATED).asInt());

            final HttpGet get = new HttpGet(uri.toString() + "/SessionPersistenceServlet");
            final HttpGet invalidate = new HttpGet(get.getURI().toString() + "?invalidate=true");
            HttpResponse res = clients[0].execute(get);
            Assertions.assertEquals(StatusCodes.OK, res.getStatusLine().getStatusCode());
            EntityUtils.consume(res.getEntity());
            opRes = managementClient.getControllerClient().execute(operation);
            Assertions.assertEquals("success", opRes.get(ModelDescriptionConstants.OUTCOME).asString(), opRes.toString());
            result = opRes.get(ModelDescriptionConstants.RESULT);

            //create a session and check that it worked
            Assertions.assertEquals(1, result.get(ACTIVE_SESSIONS).asInt());
            Assertions.assertEquals(0, result.get(EXPIRED_SESSIONS).asInt());
            Assertions.assertEquals(1, result.get(HIGHEST_SESSION_COUNT).asInt());
            Assertions.assertEquals(MAX_SESSIONS, result.get(MAX_ACTIVE_SESSIONS).asInt());
            Assertions.assertEquals(0, result.get(REJECTED_SESSIONS).asInt());
            Assertions.assertEquals(1, result.get(SESSIONS_CREATED).asInt());

            //this should use the same session
            res = clients[0].execute(get);
            Assertions.assertEquals(StatusCodes.OK, res.getStatusLine().getStatusCode());
            EntityUtils.consume(res.getEntity());
            opRes = managementClient.getControllerClient().execute(operation);
            Assertions.assertEquals("success", opRes.get(ModelDescriptionConstants.OUTCOME).asString(), opRes.toString());
            result = opRes.get(ModelDescriptionConstants.RESULT);

            Assertions.assertEquals(1, result.get(ACTIVE_SESSIONS).asInt());
            Assertions.assertEquals(0, result.get(EXPIRED_SESSIONS).asInt());
            Assertions.assertEquals(1, result.get(HIGHEST_SESSION_COUNT).asInt());
            Assertions.assertEquals(MAX_SESSIONS, result.get(MAX_ACTIVE_SESSIONS).asInt());
            Assertions.assertEquals(0, result.get(REJECTED_SESSIONS).asInt());
            Assertions.assertEquals(1, result.get(SESSIONS_CREATED).asInt());


            res = clients[0].execute(invalidate);
            Assertions.assertEquals(StatusCodes.OK, res.getStatusLine().getStatusCode());
            EntityUtils.consume(res.getEntity());

            opRes = managementClient.getControllerClient().execute(operation);
            Assertions.assertEquals("success", opRes.get(ModelDescriptionConstants.OUTCOME).asString(), opRes.toString());
            result = opRes.get(ModelDescriptionConstants.RESULT);

            Assertions.assertEquals(0, result.get(ACTIVE_SESSIONS).asInt());
            Assertions.assertEquals(1, result.get(EXPIRED_SESSIONS).asInt());
            Assertions.assertEquals(1, result.get(HIGHEST_SESSION_COUNT).asInt());
            Assertions.assertEquals(MAX_SESSIONS, result.get(MAX_ACTIVE_SESSIONS).asInt());
            Assertions.assertEquals(0, result.get(REJECTED_SESSIONS).asInt());
            Assertions.assertEquals(1, result.get(SESSIONS_CREATED).asInt());

            for (int i = 0; i < CLIENT_COUNT; ++i) {
                res = clients[i].execute(get);
                Assertions.assertEquals(i >= MAX_SESSIONS ? StatusCodes.INTERNAL_SERVER_ERROR : StatusCodes.OK, res.getStatusLine().getStatusCode());
                EntityUtils.consume(res.getEntity());
            }

            opRes = managementClient.getControllerClient().execute(operation);
            Assertions.assertEquals("success", opRes.get(ModelDescriptionConstants.OUTCOME).asString(), opRes.toString());
            result = opRes.get(ModelDescriptionConstants.RESULT);

            Assertions.assertEquals(MAX_SESSIONS, result.get(ACTIVE_SESSIONS).asInt());
            Assertions.assertEquals(1, result.get(EXPIRED_SESSIONS).asInt());
            Assertions.assertEquals(MAX_SESSIONS, result.get(HIGHEST_SESSION_COUNT).asInt());
            Assertions.assertEquals(MAX_SESSIONS, result.get(MAX_ACTIVE_SESSIONS).asInt());
            Assertions.assertEquals(CLIENT_COUNT - MAX_SESSIONS, result.get(REJECTED_SESSIONS).asInt());
            Assertions.assertEquals(MAX_SESSIONS + 1, result.get(SESSIONS_CREATED).asInt());

            for (int i = 0; i < MAX_SESSIONS; ++i) {
                res = clients[i].execute(invalidate);
                Assertions.assertEquals(StatusCodes.OK, res.getStatusLine().getStatusCode());
                EntityUtils.consume(res.getEntity());
            }

            opRes = managementClient.getControllerClient().execute(operation);
            Assertions.assertEquals("success", opRes.get(ModelDescriptionConstants.OUTCOME).asString(), opRes.toString());
            result = opRes.get(ModelDescriptionConstants.RESULT);

            Assertions.assertEquals(0, result.get(ACTIVE_SESSIONS).asInt());
            Assertions.assertEquals(MAX_SESSIONS + 1, result.get(EXPIRED_SESSIONS).asInt());
            Assertions.assertEquals(MAX_SESSIONS, result.get(HIGHEST_SESSION_COUNT).asInt());
            Assertions.assertEquals(MAX_SESSIONS, result.get(MAX_ACTIVE_SESSIONS).asInt());
            Assertions.assertEquals(CLIENT_COUNT - MAX_SESSIONS, result.get(REJECTED_SESSIONS).asInt());
            Assertions.assertEquals(MAX_SESSIONS + 1, result.get(SESSIONS_CREATED).asInt());


        } finally {
            for (CloseableHttpClient i : clients) {
                IoUtils.safeClose(i);
            }
        }
    }
}

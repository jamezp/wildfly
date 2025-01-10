/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.web.rootcontext;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.arquillian.api.ServerSetupTask;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * This class tests a root context deployed as an EAR or a WAR.
 *
 * @author Stan.Silvert@jboss.org
 * @author lbarreiro@redhat.com
 */
@ExtendWith(ArquillianExtension.class)
@RunAsClient
@ServerSetup(RootContextWarUnitTestCase.RootContextWarSetup.class)
public class RootContextWarUnitTestCase {

    private static String HOST = "context-host";

    static class RootContextWarSetup implements ServerSetupTask {

        @Override
        public void setup(final ManagementClient managementClient, final String containerId) throws Exception {
            RootContextUtil.createVirutalHost(managementClient.getControllerClient(), HOST);
        }

        @Override
        public void tearDown(final ManagementClient managementClient, final String containerId) throws Exception {
            RootContextUtil.removeVirtualHost(managementClient.getControllerClient(), HOST);
        }
    }

    @Deployment(name = "root-context.war")
    public static WebArchive warDeployment() {

        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        String resourcesLocation = "org/jboss/as/test/integration/web/rootcontext/resources/";

        WebArchive war = ShrinkWrap.create(WebArchive.class, "root-context.war");

        war.setWebXML(tccl.getResource(resourcesLocation + "root-web.xml"));
        war.addAsWebInfResource(tccl.getResource(resourcesLocation + "jboss-web.xml"), "jboss-web.xml");
        war.addAsWebResource(tccl.getResource(resourcesLocation + "index.html"), "index.html");

        return war;
    }

    @Test
    public void testRootContextWAR(@ArquillianResource URL url) throws Exception {
        String response = RootContextUtil.hitRootContext(url, HOST);
        assertTrue(response.contains("A Root Context Page"));
    }

}

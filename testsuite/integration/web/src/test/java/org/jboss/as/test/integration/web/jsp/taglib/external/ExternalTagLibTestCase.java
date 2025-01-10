/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.web.jsp.taglib.external;

import java.net.URL;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.test.module.util.TestModule;
import org.jboss.as.test.shared.ModuleUtils;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;


@ExtendWith(ArquillianExtension.class)
@RunAsClient
public class ExternalTagLibTestCase {

    private static final String BOTH_DEPENDENCIES_WAR = "both-dependencies.war";
    private static final String EXTERNAL_DEPENDENCY_ONLY_WAR = "external-dependency-only.war";
    private static final String MODULE_NAME = "external-tag-lib";
    private static final String TEST_JSP = "test.jsp";
    private static TestModule testModule;

    private static final boolean isRunningWithBootableJar = Boolean.getBoolean("ts.bootable");

    @AfterAll
    public static void tearDown() throws Exception {
        if (!isRunningWithBootableJar) {
            testModule.remove();
        }
    }

    @Deployment(name = EXTERNAL_DEPENDENCY_ONLY_WAR, order = 1)
    public static WebArchive deployment() throws Exception {
        //when running with bootable JAR, the module is packed into JAR before tests are run
        if (!isRunningWithBootableJar) {
            createExternalTaglibModule();
        }
        return ShrinkWrap.create(WebArchive.class, EXTERNAL_DEPENDENCY_ONLY_WAR)
                .addAsManifestResource(new StringAsset("Dependencies: test." + MODULE_NAME + " meta-inf\n"), "MANIFEST.MF")
                .addAsWebResource(getJspAsset(false), TEST_JSP);
    }

    @Deployment(name = BOTH_DEPENDENCIES_WAR, order = 2)
    public static WebArchive deployWithBothDependencies() throws Exception {
        return ShrinkWrap.create(WebArchive.class, BOTH_DEPENDENCIES_WAR)
                .addClass(InternalTag.class)
                .addAsWebInfResource(ExternalTagLibTestCase.class.getPackage(), "internal.tld", "internal.tld")
                .addAsManifestResource(new StringAsset("Dependencies: test." + MODULE_NAME + " meta-inf\n"), "MANIFEST.MF")
                .addAsWebResource(getJspAsset(true), TEST_JSP);
    }

    @ArquillianResource
    @OperateOnDeployment(BOTH_DEPENDENCIES_WAR)
    private URL both_dependencies_url;

    @ArquillianResource
    @OperateOnDeployment(EXTERNAL_DEPENDENCY_ONLY_WAR)
    private URL external_dependency_only_url;

    @Test
    public void testExternalTagLibOnly() throws Exception {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpget = new HttpGet(external_dependency_only_url.toExternalForm() + TEST_JSP);
            HttpResponse response = httpClient.execute(httpget);
            HttpEntity entity = response.getEntity();
            String result = EntityUtils.toString(entity);
            Assertions.assertTrue(result.contains("External Tag!"), result);
        }
    }

    @Test
    public void testExternalAndInternalTagLib() throws Exception {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpget = new HttpGet(both_dependencies_url.toExternalForm() + TEST_JSP);
            HttpResponse response = httpClient.execute(httpget);
            HttpEntity entity = response.getEntity();
            String result = EntityUtils.toString(entity);
            Assertions.assertTrue(result.contains("External Tag!"), result);
            Assertions.assertTrue(result.contains("Internal Tag!"), result);
        }
    }

    private static void createExternalTaglibModule() throws Exception {
        testModule = ModuleUtils.createTestModuleWithEEDependencies(MODULE_NAME);
        JavaArchive jar = testModule.addResource("module.jar");
        jar.addClass(ExternalTag.class);
        jar.addAsManifestResource(ExternalTagLibTestCase.class.getPackage(), "external.tld", "external.tld");
        testModule.create(true);
    }

    private static StringAsset getJspAsset(boolean withInternalLib) {
        String optionalInternalTagLib = withInternalLib ? "<%@ taglib prefix=\"i\" uri=\"http://internal.taglib\" %>\n" : "";
        String optionalInternalTag = withInternalLib ? "    <i:test/>\n" : "";

        return new StringAsset(
                "<%@ taglib prefix=\"e\" uri=\"http://external.taglib\" %>\n" +
                        optionalInternalTagLib +
                        "<html>\n" +
                        "  <head>\n" +
                        "    <title>test</title>\n" +
                        "  </head>\n" +
                        "  <body>\n" +
                        "    <e:test/>\n" +
                        optionalInternalTag +
                        "  </body>\n" +
                        "</html>");
    }

}
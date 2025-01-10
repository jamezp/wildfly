/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.web.handlers;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.FilePermission;
import java.net.URL;

import static org.jboss.as.test.shared.PermissionUtils.createPermissionsXmlAsset;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the use of undertow-handlers.conf
 *
 * @author Stuart Douglas
 */
@ExtendWith(ArquillianExtension.class)
@RunAsClient
public class UndertowHandlersConfigTestCase {

    @Deployment
    public static WebArchive deploy() {
        return ShrinkWrap.create(WebArchive.class, "non-blocking-handler.war")
                .addPackage(UndertowHandlersConfigTestCase.class.getPackage())
                .addAsWebInfResource(UndertowHandlersConfigTestCase.class.getPackage(), "jboss-web.xml", "jboss-web.xml")
                .addAsWebResource(new StringAsset("A file"), "file.txt")
                .addAsWebInfResource(new StringAsset("regex['/rewrite.*'] -> rewrite['/file.txt']"), "undertow-handlers.conf")
                .addAsWebResource(createPermissionsXmlAsset(new FilePermission("<<ALL FILES>>", "read,write")), "META-INF/permissions.xml");

    }

    @ArquillianResource
    protected URL url;

    @Test
    public void testRewrite() throws Exception {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet httpget = new HttpGet(url.toExternalForm() + "rewritea");

            HttpResponse response = httpClient.execute(httpget);
            HttpEntity entity = response.getEntity();

            StatusLine statusLine = response.getStatusLine();
            assertEquals(200, statusLine.getStatusCode());

            String result = EntityUtils.toString(entity);
            Assertions.assertEquals("A file", result);

            Header[] headers = response.getHeaders("MyHeader");
            Assertions.assertEquals(1, headers.length);
            Assertions.assertEquals("MyValue", headers[0].getValue());
        }
    }
}

/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.web.cookie;

import static org.junit.jupiter.api.Assertions.*;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.logging.Logger;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test case for cookie
 *
 * @author prabhat.jha@jboss.com
 * @author lbarreiro@redhat.com
 */
@ExtendWith(ArquillianExtension.class)
@RunAsClient
public class CookieUnitTestCase {

    protected static Logger log = Logger.getLogger(CookieUnitTestCase.class);

    protected static String[] cookieNames = {"simpleCookie", "withSpace", "commented", "expired"};

    protected static final long fiveSeconds = 5000;

    @ArquillianResource(CookieServlet.class)
    protected URL cookieURL;

    @ArquillianResource(CookieReadServlet.class)
    protected URL cookieReadURL;

    @Deployment(testable = false)
    public static WebArchive deployment() {

        WebArchive war = ShrinkWrap.create(WebArchive.class, "jbosstest-cookie.war");
        war.addClass(CookieReadServlet.class);
        war.addClass(CookieServlet.class);

        return war;
    }

    @Test
    public void testCookieSetCorrectly() throws Exception {
        log.debug("testCookieSetCorrectly()");
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpResponse response = httpclient.execute(new HttpGet(cookieReadURL.toURI() + "CookieReadServlet"));
            if (response.getEntity() != null) { response.getEntity().getContent().close(); }

            log.debug("Sending request with cookie");
            response = httpclient.execute(new HttpPost(cookieReadURL.toURI() + "CookieReadServlet"));
        }
    }

    @Test
    public void testCookieRetrievedCorrectly() throws Exception {
        log.trace("testCookieRetrievedCorrectly()");
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpClientContext context = HttpClientContext.create();
            HttpResponse response = httpclient.execute(new HttpGet(cookieURL.toURI() + "CookieServlet"), context);

            // assert that we are able to hit servlet successfully
            int postStatusCode = response.getStatusLine().getStatusCode();
            Header[] postErrorHeaders = response.getHeaders("X-Exception");
            assertEquals(HttpURLConnection.HTTP_OK, postStatusCode, "Wrong response code: " + postStatusCode);
            assertEquals(0, postErrorHeaders.length, "X-Exception(" + Arrays.toString(postErrorHeaders) + ") is null");

            List<Cookie> cookies = context.getCookieStore().getCookies();
            assertTrue(checkNoExpiredCookie(cookies), "Sever did not set expired cookie on client");

            for (Cookie cookie : cookies) {
                log.trace("Cookie : " + cookie);
                String cookieName = cookie.getName();
                String cookieValue = cookie.getValue();

                if (cookieName.equals("simpleCookie")) {
                    assertEquals("jboss", cookieValue, "cookie value should be jboss");
                    assertEquals("/jbosstest-cookie", cookie.getPath(), "cookie path");
                    assertFalse(cookie.isPersistent(), "cookie persistence");
                } else if (cookieName.equals("withSpace")) {
                    assertEquals(-1, cookieValue.indexOf("\""), "should be no quote in cookie with space");
                } else if (cookieName.equals("comment")) {
                    log.trace("comment in cookie: " + cookie.getComment());
                    // RFC2109:Note that there is no Comment attribute in the Cookie request header
                    // corresponding to the one in the Set-Cookie response header. The user
                    // agent does not return the comment information to the origin server.

                    assertNull(cookie.getComment());
                } else if (cookieName.equals("withComma")) {
                    assertTrue(cookieValue.indexOf(",") != -1, "should contain a comma");
                } else if (cookieName.equals("expireIn10Sec")) {
                    Date now = new Date();
                    log.trace("will sleep for 5 seconds to see if cookie expires");
                    assertFalse(cookie.isExpired(new Date(now.getTime() + fiveSeconds)), "cookies should not be expired by now");
                    log.trace("will sleep for 5 more secs and it should expire");
                    assertTrue(cookie.isExpired(new Date(now.getTime() + 2 * fiveSeconds)), "cookies should be expired by now");
                }
            }
        }
    }

    protected boolean checkNoExpiredCookie(List<Cookie> cookies) {
        for (Cookie cookie : cookies) { if (cookie.getName().equals("expired")) { return false; } }
        return true;
    }
}

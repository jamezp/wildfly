/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.web.security.form;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.test.integration.web.security.SecuredServlet;
import org.jboss.as.test.integration.web.security.WebTestsSecurityDomainSetup;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Unit Test web security
 *
 * @author <a href="mailto:mmoyses@redhat.com">Marcus Moyses</a>
 */
@ExtendWith(ArquillianExtension.class)
@RunAsClient
@ServerSetup(WebTestsSecurityDomainSetup.class)
@Tag("CommonCriteria")
@Disabled("AS7-6813 - Re-Evaluate or Remove WebSecurityJBossWebXmlSecurityRolesTestCase")
public class WebSecurityJBossWebXmlSecurityRolesTestCase extends AbstractWebSecurityFORMTestCase {

    @Deployment(testable = false)
    public static WebArchive deployment() throws Exception {

        WebArchive war = ShrinkWrap.create(WebArchive.class, "web-secure.war");
        war.addClasses(SecuredServlet.class);

        war.addAsWebResource(WebSecurityJBossWebXmlSecurityRolesTestCase.class.getPackage(), "login.jsp", "login.jsp");
        war.addAsWebResource(WebSecurityJBossWebXmlSecurityRolesTestCase.class.getPackage(), "error.jsp", "error.jsp");

        war.addAsWebInfResource(WebSecurityJBossWebXmlSecurityRolesTestCase.class.getPackage(), "jboss-web-role-mapping.xml",
                "jboss-web.xml");
        war.addAsWebInfResource(WebSecurityJBossWebXmlSecurityRolesTestCase.class.getPackage(), "web.xml", "web.xml");

        war.addAsResource(WebSecurityJBossWebXmlSecurityRolesTestCase.class.getPackage(), "users.properties",
                "users.properties");
        war.addAsResource(WebSecurityJBossWebXmlSecurityRolesTestCase.class.getPackage(), "roles.properties",
                "roles.properties");
        return war;
    }

    /**
     * Negative test as marcus doesn't have proper role in module mapping created.
     */
    @Override
    @Test
    public void testPasswordBasedUnsuccessfulAuth() throws Exception {
        makeCall("marcus", "marcus", 200);
    }

    /**
     * Negative test to see if mapping is not performed on username instead of role.
     *
     * @throws Exception
     */
    @Test
    public void testPrincipalMappingOnRole() throws Exception {
        makeCall("peter", "peter", 403);
    }
}

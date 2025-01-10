/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.web.security.form;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.test.integration.web.security.WebTestsSecurityDomainSetup;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Unit Test web security
 *
 * @author <a href="mailto:pskopek@redhat.com">Peter Skopek</a>
 */
@ExtendWith(ArquillianExtension.class)
@RunAsClient
@ServerSetup(WebTestsSecurityDomainSetup.class)
@Tag("CommonCriteria")
public class WebSecurityJBossWebSimpleRoleMappingTestCase extends AbstractWebSecurityFORMTestCase {

    @Deployment(testable = false)
    public static WebArchive deployment() throws Exception {
        return prepareDeployment("jboss-web-role-mapping.xml");
    }

    /**
     * At this time peter can go through because he has role mapped in the map-module option.
     *
     * @throws Exception
     */
    @Test
    public void testPrincipalMappingOnRole() throws Exception {
        makeCall("peter", "peter", 200);
    }
}
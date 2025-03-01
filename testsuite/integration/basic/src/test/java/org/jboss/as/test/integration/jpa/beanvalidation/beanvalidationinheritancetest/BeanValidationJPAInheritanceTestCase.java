/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.jpa.beanvalidation.beanvalidationinheritancetest;

import static org.jboss.as.test.shared.PermissionUtils.createPermissionsXmlAsset;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.Locale;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.hibernate.validator.HibernateValidatorPermission;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.test.integration.jpa.beanvalidation.Player;
import org.jboss.as.test.integration.jpa.beanvalidation.SLSBInheritance;
import org.jboss.as.test.integration.jpa.beanvalidation.SoccerPlayer;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test Jakarta Bean Validation is propagated on inherited attributes
 *
 * @author Madhumita Sadhukhan
 */
@RunWith(Arquillian.class)
public class BeanValidationJPAInheritanceTestCase {

    private static final String ARCHIVE_NAME = "jpa_TestBeanValidationJPAInheritance";

    @ArquillianResource
    private static InitialContext iniCtx;

    @BeforeClass
    public static void beforeClass() throws NamingException {
        iniCtx = new InitialContext();
    }

    @Deployment
    public static Archive<?> deploy() {
        JavaArchive jar = ShrinkWrap.create(JavaArchive.class, ARCHIVE_NAME + ".jar");
        jar.addClasses(Player.class, SoccerPlayer.class, SLSBInheritance.class);
        jar.addAsManifestResource(BeanValidationJPAInheritanceTestCase.class.getPackage(), "persistence.xml", "persistence.xml");
        jar.addAsManifestResource(createPermissionsXmlAsset(
                HibernateValidatorPermission.ACCESS_PRIVATE_MEMBERS
        ), "permissions.xml");
        return jar;
    }

    protected static <T> T lookup(String beanName, Class<T> interfaceType) throws NamingException {
        return interfaceType
                .cast(iniCtx.lookup("java:global/" + ARCHIVE_NAME + "/" + beanName + "!" + interfaceType.getName()));
    }

    /* Ensure that Jakarta Bean Validation works for inheritance across persistent objects */

    @Test
    public void testConstraintValidationforJPA() throws NamingException, SQLException {

        SLSBInheritance slsb = lookup("SLSBInheritance", SLSBInheritance.class);

        try {
            SoccerPlayer socplayer = slsb.createSoccerPlayer("LEONARDO", "", "SOCCER", "REAL MADRID");

            socplayer.setFirstName("Christiano");
            socplayer.setLastName("");
            socplayer.setGame("FOOTBALL");
            socplayer = slsb.updateSoccerPlayer(socplayer);
        } catch (Exception e) {

            StringWriter w = new StringWriter();
            e.printStackTrace(new PrintWriter(w));
            String stacktrace = w.toString();

            if (Locale.getDefault().getLanguage().equals("en")) {
                Assert.assertTrue(stacktrace.contains("interpolatedMessage='must not be empty', propertyPath=lastName, rootBeanClass=class org.jboss.as.test.integration.jpa.beanvalidation.SoccerPlayer"));
            } else {
                Assert.assertTrue(stacktrace.contains("propertyPath=lastName, rootBeanClass=class org.jboss.as.test.integration.jpa.beanvalidation.SoccerPlayer"));
            }
        }

    }

}

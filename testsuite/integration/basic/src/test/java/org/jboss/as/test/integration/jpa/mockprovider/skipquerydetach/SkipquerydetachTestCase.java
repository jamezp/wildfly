/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.jpa.mockprovider.skipquerydetach;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.EnterpriseArchive;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Hibernate "hibernate.ejb.use_class_enhancer" test that causes hibernate to add a
 * jakarta.persistence.spi.ClassTransformer to the pu.
 *
 * @author Scott Marlow
 */
@RunWith(Arquillian.class)
public class SkipquerydetachTestCase {

    private static final String ARCHIVE_NAME = "jpa_skipquerydetachTestWithMockProvider";

    @Deployment
    public static Archive<?> deploy() {
        JavaArchive persistenceProvider = ShrinkWrap.create(JavaArchive.class, ARCHIVE_NAME + ".jar");
        persistenceProvider.addClasses(
                AbstractTestPersistenceProvider.class,
                TestClassTransformer.class,
                TestEntityManagerFactory.class,
                TestEntityManager.class,
                TestQuery.class,
                TestPersistenceProvider.class,
                TestAdapter.class
        );

        // META-INF/services/jakarta.persistence.spi.PersistenceProvider
        persistenceProvider.addAsResource(new StringAsset("org.jboss.as.test.integration.jpa.mockprovider.skipquerydetach.TestPersistenceProvider"),
                "META-INF/services/jakarta.persistence.spi.PersistenceProvider");

        EnterpriseArchive ear = ShrinkWrap.create(EnterpriseArchive.class, ARCHIVE_NAME + ".ear");

        JavaArchive ejbjar = ShrinkWrap.create(JavaArchive.class, "ejbjar.jar");
        ejbjar.addAsManifestResource(emptyEjbJar(), "ejb-jar.xml");
        ejbjar.addClasses(SkipquerydetachTestCase.class,
                SFSB1.class
        );
        ejbjar.addAsManifestResource(SkipquerydetachTestCase.class.getPackage(), "persistence.xml", "persistence.xml");

        ear.addAsModule(ejbjar);        // add ejbjar to root of ear

        ear.addAsLibraries(persistenceProvider);
        return ear;

    }

    private static StringAsset emptyEjbJar() {
        return new StringAsset(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<ejb-jar xmlns=\"http://java.sun.com/xml/ns/javaee\" \n" +
                        "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n" +
                        "         xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/ejb-jar_3_0.xsd\"\n" +
                        "         version=\"3.0\">\n" +
                        "   \n" +
                        "</ejb-jar>");
    }

    @ArquillianResource
    private InitialContext iniCtx;

    @Test
    public void test_withSkipQueryDetachEnabled() throws NamingException {
        SFSB1 sfsb1 = (SFSB1)iniCtx.lookup("java:module/" + SFSB1.class.getSimpleName());
        try {
            assertNotNull("EJB injection of SFSB1 failed",sfsb1);
            assertNull(sfsb1.queryWithSkipQueryDetachEnabled());
        } finally {
            TestAdapter.clearInitialized();
            TestEntityManager.clear();
            TestEntityManagerFactory.clear();
        }
    }


    @Test
    public void test_withSkipQueryDetachDisabled() throws NamingException {
        SFSB1 sfsb1 = (SFSB1)iniCtx.lookup("java:module/" + SFSB1.class.getSimpleName());

        try {
            assertNotNull("EJB injection of SFSB1 failed",sfsb1);
            assertNull(sfsb1.queryWithSkipQueryDetachDisabled());
        } finally {
            TestAdapter.clearInitialized();
            TestEntityManager.clear();
            TestEntityManagerFactory.clear();
        }
    }
}

/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.web.handlestypes;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import jakarta.servlet.ServletContainerInitializer;
import java.util.Arrays;
import java.util.HashSet;

/**
 * @author Stuart Douglas
 */
@ExtendWith(ArquillianExtension.class)
public class HandlesTypesWarTestCase {

    @Deployment
    public static Archive<?> deploy() {
        WebArchive war = ShrinkWrap.create(WebArchive.class)
                .addClasses(SomeAnnotation.class)
                .addClasses(AnnotatedParent.class, NonAnnotatedChild.class, AnnotatedChild.class)
                .addClasses(HandlesTypesParent.class, HandlesTypesChild.class, HandlesTypesGandchild.class)
                .addClasses(HandlesTypesInterface.class, HandlesTypesImplementor.class, HandlesTypesImplementorChild.class)
                .addClasses(ParentServletContainerInitializer.class, ChildServletContainerInitializer.class, AnnotationServletContainerInitializer.class)
                .addAsServiceProvider(ServletContainerInitializer.class, ParentServletContainerInitializer.class, ChildServletContainerInitializer.class, AnnotationServletContainerInitializer.class);
        return war;
    }

    @Test
    public void testParentClass() {
        Class<?>[] expeccted = {HandlesTypesChild.class, HandlesTypesImplementor.class, HandlesTypesGandchild.class, HandlesTypesImplementorChild.class};
        Assertions.assertEquals(ParentServletContainerInitializer.HANDLES_TYPES, new HashSet<>(Arrays.asList(expeccted)));
    }

    @Test
    public void testChildClass() {
        Class<?>[] expeccted = {HandlesTypesGandchild.class, HandlesTypesImplementorChild.class};
        Assertions.assertEquals(ChildServletContainerInitializer.HANDLES_TYPES, new HashSet<>(Arrays.asList(expeccted)));
    }
    @Test
    public void testAnnotatedClass() {
        Class<?>[] expeccted = {AnnotatedParent.class, AnnotatedChild.class};
        Assertions.assertEquals(AnnotationServletContainerInitializer.HANDLES_TYPES, new HashSet<>(Arrays.asList(expeccted)));
    }
}

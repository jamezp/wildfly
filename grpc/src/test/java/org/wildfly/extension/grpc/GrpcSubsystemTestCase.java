/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.grpc;

import java.util.EnumSet;
import java.util.Properties;

import org.jboss.as.subsystem.test.AbstractSubsystemSchemaTest;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Test case for the gRPC subsystem.
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
@RunWith(Parameterized.class)
public class GrpcSubsystemTestCase extends AbstractSubsystemSchemaTest<GrpcSubsystemSchema> {
    @Parameters
    public static Iterable<GrpcSubsystemSchema> parameters() {
        return EnumSet.allOf(GrpcSubsystemSchema.class);
    }

    public GrpcSubsystemTestCase(GrpcSubsystemSchema schema) {
        super(GrpcConfigurationConstants.SUBSYSTEM_NAME, new GrpcExtension(), schema, GrpcSubsystemSchema.CURRENT);
    }

    @Override
    protected String getSubsystemXmlPathPattern() {
        // Exclude subsystem name from pattern
        return "subsystem_%2$d_%3$d.xml";
    }

    @Override
    protected Properties getResolvedProperties() {
        return System.getProperties();
    }
}

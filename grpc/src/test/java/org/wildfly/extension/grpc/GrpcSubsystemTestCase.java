/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.grpc;

import java.io.IOException;

import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;

/**
 * Test case for the gRPC subsystem.
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
public class GrpcSubsystemTestCase extends AbstractSubsystemBaseTest {

    public GrpcSubsystemTestCase() {
        super(GrpcConfigurationConstants.SUBSYSTEM_NAME, new GrpcExtension());
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("/org/wildfly/extension/grpc/subsystem_1_0.xml");
    }

    @Override
    protected String getSubsystemXsdPath() {
        return "schema/wildfly-grpc_1_0.xsd";
    }
}

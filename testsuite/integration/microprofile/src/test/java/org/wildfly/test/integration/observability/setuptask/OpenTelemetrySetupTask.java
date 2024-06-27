/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.test.integration.observability.setuptask;

import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.arquillian.container.ManagementClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.test.shared.ServerReload;
import org.jboss.as.test.shared.util.AssumeTestGroupUtil;
import org.jboss.dmr.ModelNode;
import org.wildfly.test.integration.observability.container.OpenTelemetryCollectorContainer;

public class OpenTelemetrySetupTask extends AbstractSetupTask {
    protected static final String SUBSYSTEM_NAME = "opentelemetry";
    protected final ModelNode extensionAddress = Operations.createAddress("extension", "org.wildfly.extension.opentelemetry");
    protected final ModelNode subsystemAddress = Operations.createAddress("subsystem", SUBSYSTEM_NAME);
    private boolean extensionAdded = false;
    private boolean subsystemAdded = false;

    @ArquillianResource
    protected OpenTelemetryCollectorContainer otelContainer;

    @Override
    public void setup(final ManagementClient managementClient, final String containerId) throws Exception {
        AssumeTestGroupUtil.assumeDockerAvailable();

        if (!Operations.isSuccessfulOutcome(executeRead(managementClient, extensionAddress))) {
            executeOp(managementClient, Operations.createAddOperation(extensionAddress));
            extensionAdded = true;
        }

        if (!Operations.isSuccessfulOutcome(executeRead(managementClient, subsystemAddress))) {
            ModelNode addOp = Operations.createAddOperation(subsystemAddress);
            executeOp(managementClient, addOp);
            subsystemAdded = true;
        }

        executeOp(managementClient, writeAttribute(SUBSYSTEM_NAME, "batch-delay", "1"));
        executeOp(managementClient, writeAttribute(SUBSYSTEM_NAME, "sampler-type", "on"));
        executeOp(managementClient, writeAttribute(SUBSYSTEM_NAME, "endpoint", otelContainer.getOtlpGrpcEndpoint()));

        ServerReload.reloadIfRequired(managementClient);
    }

    @Override
    public void tearDown(final ManagementClient managementClient, final String containerId) throws Exception {
        if (subsystemAdded) {
            executeOp(managementClient, Operations.createRemoveOperation(subsystemAddress));
        }
        if (extensionAdded) {
            executeOp(managementClient, Operations.createRemoveOperation(extensionAddress));
        }

        ServerReload.reloadIfRequired(managementClient);
    }
}

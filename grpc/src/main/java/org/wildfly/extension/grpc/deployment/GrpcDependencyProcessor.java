package org.wildfly.extension.grpc.deployment;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ModuleDependency;
import org.jboss.as.server.deployment.module.ModuleSpecification;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoader;

/**
 * A deployment unit process for adding the required dependencies to deployments.
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
public class GrpcDependencyProcessor implements DeploymentUnitProcessor {
    @Override
    public void deploy(final DeploymentPhaseContext phaseContext) {
        // TODO (jrp) we should probably only add these to WAR's, but would a CDI JAR be valid if it's a dependency
        // TODO (jrp) to a WAR?
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final ModuleSpecification moduleSpecification = deploymentUnit.getAttachment(Attachments.MODULE_SPECIFICATION);

        // TODO (jrp) is this right?
        final ModuleLoader moduleLoader = Module.getBootModuleLoader();
        addDependency(moduleSpecification, moduleLoader, "io.grpc");
        addDependency(moduleSpecification, moduleLoader, "io.grpc.core");
        addDependency(moduleSpecification, moduleLoader, "com.google.protobuf");

    }

    private void addDependency(final ModuleSpecification moduleSpecification, final ModuleLoader moduleLoader,
                               final String moduleIdentifier) {
        ModuleDependency dependency = ModuleDependency.Builder.of(moduleLoader, moduleIdentifier)
                .setOptional(false)
                .setImportServices(true)
                .build();
        moduleSpecification.addSystemDependency(dependency);
    }
}

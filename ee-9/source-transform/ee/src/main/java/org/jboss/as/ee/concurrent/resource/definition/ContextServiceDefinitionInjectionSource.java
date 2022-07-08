/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2022 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.ee.concurrent.resource.definition;

import org.glassfish.enterprise.concurrent.spi.TransactionSetupProvider;
import org.jboss.as.ee.concurrent.ContextServiceImpl;
import org.jboss.as.ee.concurrent.DefaultContextSetupProviderImpl;
import org.jboss.as.ee.concurrent.service.ConcurrentServiceNames;
import org.jboss.as.ee.concurrent.service.ContextServiceService;
import org.jboss.as.ee.resource.definition.ResourceDefinitionInjectionSource;
import org.jboss.as.ee.subsystem.ContextServiceResourceDefinition;
import org.jboss.as.naming.ManagedReference;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.msc.inject.InjectionException;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;

/**
 * The {@link ResourceDefinitionInjectionSource} for {@link jakarta.enterprise.concurrent.ContextServiceDefinition}
 *
 * @author emmartins
 */
public class ContextServiceDefinitionInjectionSource extends ResourceDefinitionInjectionSource {

    public static final String CLEARED_PROP = "cleared";
    public static final String PROPAGATED_PROP = "propagated";
    public static final String UNCHANGED_PROP = "unchanged";

    private String[] cleared;
    private String[] propagated;
    private String[] unchanged;

    public ContextServiceDefinitionInjectionSource(final String jndiName) {
        super(jndiName);
    }

    public void getResourceValue(final ResolutionContext context, final ServiceBuilder<?> serviceBuilder, final DeploymentPhaseContext phaseContext, final Injector<ManagedReferenceFactory> injector) throws DeploymentUnitProcessingException {
        final String resourceName = uniqueName(context);
        final String resourceJndiName = "java:jboss/ee/concurrency/definition/context/"+resourceName;
        try {
            // install the resource service
            final ContextServiceService resourceService = new ContextServiceService(resourceName, resourceJndiName, new DefaultContextSetupProviderImpl());
            final ServiceName resourceServiceName = ContextServiceResourceDefinition.CAPABILITY.getCapabilityServiceName(resourceName);
            phaseContext.getServiceTarget().addService(resourceServiceName)
                    .addDependency(ConcurrentServiceNames.TRANSACTION_SETUP_PROVIDER_SERVICE_NAME, TransactionSetupProvider.class, resourceService.getTransactionSetupProvider())
                    .setInstance(resourceService)
                    .install();
            // use a dependency to the resource service installed to inject the resource
            serviceBuilder.addDependency(resourceServiceName, ContextServiceImpl.class, new Injector<>() {
                @Override
                public void inject(final ContextServiceImpl resource) throws InjectionException {
                    injector.inject(() -> new ManagedReference() {
                        @Override
                        public void release() {
                        }
                        @Override
                        public Object getInstance() {
                            return resource;
                        }
                    });
                }
                @Override
                public void uninject() {
                    injector.uninject();
                }
            });
        } catch (Exception e) {
            throw new DeploymentUnitProcessingException(e);
        }
    }

    public String[] getCleared() {
        return cleared;
    }

    public void setCleared(String[] cleared) {
        this.cleared = cleared;
    }

    public String[] getPropagated() {
        return propagated;
    }

    public void setPropagated(String[] propagated) {
        this.propagated = propagated;
    }

    public String[] getUnchanged() {
        return unchanged;
    }

    public void setUnchanged(String[] unchanged) {
        this.unchanged = unchanged;
    }
}

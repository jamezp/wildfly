    /*
     * JBoss, Home of Professional Open Source.
     * Copyright 2023, Red Hat, Inc., and individual contributors
     * as indicated by the @author tags. See the copyright.txt file in the
     * distribution for a full listing of individual contributors.
     *
     * This is free software; you can redistribute it and/or modify it
     * under the terms of the GNU Lesser General Public License as
     * published by the Free Software Foundation; either version 2.1 of
     * the License, or (at your option) any later version.
     *
     * This software is distributed in the hope that it will be useful,
     * but WITHOUT ANY WARRANTY; without even the implied warranty of
     * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
     * Lesser General Public License for more details.
     *
     * You should have received a copy of the GNU Lesser General Public
     * License along with this software; if not, write to the Free
     * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
     * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
     */

    package org.wildfly.extension.microprofile.lra.participant.service;

    import static org.wildfly.extension.microprofile.lra.participant.MicroProfileLRAParticipantSubsystemDefinition.COORDINATOR_URL_PROP;

    import java.util.function.Supplier;

    import jakarta.servlet.ServletException;

    import io.undertow.servlet.api.DeploymentInfo;
    import io.undertow.servlet.api.DeploymentManager;
    import io.undertow.servlet.api.ServletContainer;
    import io.undertow.servlet.api.ServletInfo;
    import org.jboss.msc.Service;
    import org.jboss.msc.service.StartContext;
    import org.jboss.msc.service.StartException;
    import org.jboss.msc.service.StopContext;
    import org.jboss.resteasy.plugins.server.servlet.HttpServlet30Dispatcher;
    import org.wildfly.extension.microprofile.lra.participant._private.MicroProfileLRAParticipantLogger;
    import org.wildfly.extension.microprofile.lra.participant.jaxrs.LRAParticipantApplication;
    import org.wildfly.extension.undertow.Host;

    public final class LRAParticipantService implements Service {

        public static final String CONTEXT_PATH = "/lra-participant-narayana-proxy";
        private static final String DEPLOYMENT_NAME = "LRA Participant Proxy";

        private final Supplier<Host> undertow;

        // Guarded by this
        private DeploymentManager deploymentManager = null;
        // Guarded by this
        private DeploymentInfo deploymentInfo = null;

        public LRAParticipantService(Supplier<Host> undertow) {
            this.undertow = undertow;
        }

        @Override
        public synchronized void start(final StartContext context) throws StartException {
            deployParticipantProxy();
        }

        @Override
        public synchronized void stop(final StopContext context) {
            try {
                undeployServlet();
            } finally {
                // If we are stopping the server is either shutting down or reloading.
                // In case it's a reload and this subsystem will not be installed after the reload,
                // clear the lra.coordinator.url prop so it doesn't affect the reloaded server.
                // If the subsystem is still in the config, the add op handler will set it again.
                // TODO perhaps set the property in this service's start and have LRAParticipantDeploymentDependencyProcessor
                // add a dep on this service to the next DeploymentUnitPhaseService (thus ensuring the prop
                // is set before any deployment begins creating services).
                System.clearProperty(COORDINATOR_URL_PROP);
            }
        }

        private void deployParticipantProxy() {
            undeployServlet();

            MicroProfileLRAParticipantLogger.LOGGER.startingParticipantProxy(CONTEXT_PATH);
            deploymentInfo = getDeploymentInfo();
            deployServlet();
        }

        private DeploymentInfo getDeploymentInfo() {
            final DeploymentInfo deploymentInfo = new DeploymentInfo()
                    .addInitParameter("jakarta.ws.rs.Application", LRAParticipantApplication.class.getName())
                    .setClassLoader(LRAParticipantApplication.class.getClassLoader())
                    .setContextPath(CONTEXT_PATH)
                    .setDeploymentName(DEPLOYMENT_NAME);
            // REST setup
            ServletInfo restEasyServlet = new ServletInfo("RESTEasy-LRA-Participant", HttpServlet30Dispatcher.class).addMapping("/*");
            deploymentInfo.addServlets(restEasyServlet);

            return deploymentInfo;
        }

        private void deployServlet() {
            // Get the servlet container from the host
            final ServletContainer container = undertow.get().getServer().getServletContainer().getServletContainer();
            // Add the deployment to get the deployment manager
            deploymentManager = container.addDeployment(deploymentInfo);
            deploymentManager.deploy();

            try {
                undertow.get()
                        .registerDeployment(deploymentManager.getDeployment(), deploymentManager.start());
            } catch (ServletException e) {
                MicroProfileLRAParticipantLogger.LOGGER.tracef(e, "Failed to register LRA participant deployment: %s", deploymentInfo.getDeploymentName());
            }
        }

        private void undeployServlet() {
            // Safeguard against the undeploy in the start
            if (deploymentManager != null) {
                // Unregister the current deployment from the host
                undertow.get()
                        .unregisterDeployment(deploymentManager.getDeployment());
                try {
                    deploymentManager.stop();
                } catch (ServletException e) {
                    MicroProfileLRAParticipantLogger.LOGGER.failedStoppingParticipant(CONTEXT_PATH, e);
                } finally {
                    deploymentManager.undeploy();
                }
                // Remove the deployment from the servlet container
                ServletContainer container = undertow.get().getServer().getServletContainer().getServletContainer();
                container.removeDeployment(deploymentInfo);
                deploymentManager = null;
            }
        }
    }
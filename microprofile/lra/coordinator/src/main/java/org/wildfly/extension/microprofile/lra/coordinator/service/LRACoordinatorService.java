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

package org.wildfly.extension.microprofile.lra.coordinator.service;

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
import org.wildfly.extension.microprofile.lra.coordinator._private.MicroProfileLRACoordinatorLogger;
import org.wildfly.extension.microprofile.lra.coordinator.jaxrs.LRACoordinatorApp;
import org.wildfly.extension.undertow.Host;

public final class LRACoordinatorService implements Service {

    public static final String CONTEXT_PATH = "/lra-coordinator";
    private static final String DEPLOYMENT_NAME = "LRA Coordinator";

    private final Supplier<Host> undertow;

    // Guarded by this
    private DeploymentManager deploymentManager = null;
    // Guarded by this
    private DeploymentInfo deploymentInfo = null;

    public LRACoordinatorService(Supplier<Host> undertow) {
        this.undertow = undertow;
    }

    @Override
    public synchronized void start(final StartContext context) throws StartException {
        deployCoordinator();
    }

    @Override
    public synchronized void stop(final StopContext context) {
        undeployServlet();
    }

    private void deployCoordinator() {
        undeployServlet();

        MicroProfileLRACoordinatorLogger.LOGGER.startingCoordinator(CONTEXT_PATH);
        deploymentInfo = createDeploymentInfo();
        deployServlet();
    }

    private DeploymentInfo createDeploymentInfo() {
        final DeploymentInfo deploymentInfo = new DeploymentInfo()
                .addInitParameter("jakarta.ws.rs.Application", LRACoordinatorApp.class.getName())
                .setClassLoader(LRACoordinatorApp.class.getClassLoader())
                .setContextPath(CONTEXT_PATH)
                .setDeploymentName(DEPLOYMENT_NAME);
        // REST setup
        final ServletInfo restEasyServlet = new ServletInfo("RESTEasy-LRA-Coordinator", HttpServlet30Dispatcher.class).addMapping("/*");
        return deploymentInfo.addServlets(restEasyServlet);
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
            MicroProfileLRACoordinatorLogger.LOGGER.tracef(e, "Failed to register LRA coordinator deployment: %s", deploymentInfo.getDeploymentName());
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
                MicroProfileLRACoordinatorLogger.LOGGER.failedStoppingCoordinator(CONTEXT_PATH, e);
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
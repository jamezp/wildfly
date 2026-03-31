/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.integration.grpc.service.cdi;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * CDI bean for greeting logic.
 * Used to demonstrate CDI injection in gRPC services.
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
@ApplicationScoped
public class GreetingService {

    private boolean initialized;

    @PostConstruct
    public void init() {
        initialized = true;
    }

    public String formatGreeting(final String name) {
        if (!initialized) {
            throw new IllegalStateException("Service not initialized");
        }

        final String effectiveName = (name == null || name.isEmpty()) ? "World" : name;
        return "Hello, " + effectiveName + "! (via CDI)";
    }
}

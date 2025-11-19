/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.microprofile.telemetry.api;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import io.smallrye.opentelemetry.api.OpenTelemetryConfig;
import io.smallrye.opentelemetry.api.OpenTelemetryHandler;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.Config;
import org.jboss.logmanager.ExtLogRecord;

public class MicroProfileTelemetryCdiExtension implements Extension {
    private final Map<String, String> serverConfig;

    public MicroProfileTelemetryCdiExtension(Map<String, String> serverConfig) {
        this.serverConfig = serverConfig;
    }

    public void registerOpenTelemetryConfigBean(@Observes AfterBeanDiscovery abd, BeanManager beanManager) {
        abd.addBean()
                .scope(Singleton.class)
                .addQualifier(Default.Literal.INSTANCE)
                .types(OpenTelemetryConfig.class)
                .createWith(c -> {
                            Config appConfig = beanManager.createInstance().select(Config.class).get();
                            Map<String, String> properties = new HashMap<>(serverConfig);
                            // MicroProfile Telemetry is disabled by default
                            properties.put("otel.sdk.disabled", "true");
                            for (String propertyName : appConfig.getPropertyNames()) {
                                if (propertyName.startsWith("otel.") || propertyName.startsWith("OTEL_")) {
                                    appConfig.getOptionalValue(propertyName, String.class).ifPresent(
                                            value -> properties.put(propertyName, value));
                                }
                            }

                            return (OpenTelemetryConfig) () -> properties;
                        }
                );
        // This is a hack to add a filter to the OpenTelemetryHandler. SmallRye adds this to the root logger in the
        // CDI producer for the OpenTelemetry instance.
        final Logger rootLogger = Logger.getLogger("");
        // Get the handlers and look for the OpenTelemetryHandler
        for (Handler handler : rootLogger.getHandlers()) {
            if (handler instanceof OpenTelemetryHandler) {
                handler.setFilter(ExtLogRecordFilter.INSTANCE);
            }
        }
    }

    private static class ExtLogRecordFilter implements Filter {
        private static final ExtLogRecordFilter INSTANCE = new ExtLogRecordFilter();

        @Override
        public boolean isLoggable(final LogRecord record) {
            if (record instanceof ExtLogRecord extLogRecord) {
                // Copies MDC, NDC and formats the message
                extLogRecord.copyAll();
            }
            return true;
        }
    }
}

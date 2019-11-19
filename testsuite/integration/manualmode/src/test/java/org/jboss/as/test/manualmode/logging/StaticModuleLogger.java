/*
 * Copyright 2020 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.manualmode.logging;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("WeakerAccess")
public class StaticModuleLogger {
    public static final String LOGGER_NAME = StaticModuleLogger.class.getName();
    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final org.apache.commons.logging.Log JCL_LOGGER = org.apache.commons.logging.LogFactory.getLog(LOGGER_NAME);
    private static final org.apache.log4j.Logger LOG4J_LOGGER = org.apache.log4j.Logger.getLogger(LOGGER_NAME);
    private static final org.jboss.logging.Logger JBL_LOGGER = org.jboss.logging.Logger.getLogger(LOGGER_NAME);
    private static final org.slf4j.Logger SLF4J_LOGGER = org.slf4j.LoggerFactory.getLogger(LOGGER_NAME);

    public static void staticLog(final String suffix) {
        final int currentCount = COUNTER.incrementAndGet();
        JCL_LOGGER.info(String.format("Apache Commons Logging log from module static logger for suffix %s count: %d", suffix, currentCount));
        LOG4J_LOGGER.info(String.format("log4j log from module static logger for suffix %s count: %d", suffix, currentCount));
        JBL_LOGGER.infof("JBoss Logging log from module static logger for suffix %s count: %d", suffix, currentCount);
        SLF4J_LOGGER.info(String.format("slf4j log from module static logger for suffix %s count: %d", suffix, currentCount));
    }

    public static void log(final String suffix) {
        final int currentCount = COUNTER.incrementAndGet();
        org.apache.commons.logging.LogFactory.getLog(LOGGER_NAME).info(String.format("Apache Commons Logging log from module for suffix %s count: %d", suffix, currentCount));
        org.apache.log4j.Logger.getLogger(LOGGER_NAME).info(String.format("log4j log from module for suffix %s count: %d", suffix, currentCount));
        org.jboss.logging.Logger.getLogger(LOGGER_NAME).infof("JBoss Logging log from module for suffix %s count: %d", suffix, currentCount);
        org.slf4j.LoggerFactory.getLogger(LOGGER_NAME).info(String.format("slf4j log from module for suffix %s count: %d", suffix, currentCount));
    }
}

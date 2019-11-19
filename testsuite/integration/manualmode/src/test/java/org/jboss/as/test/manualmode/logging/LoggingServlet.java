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

import java.io.IOException;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@WebServlet("/log")
public class LoggingServlet extends HttpServlet {
    static final String LOGGER_NAME = LoggingServlet.class.getName();

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        final String suffix = getFirstParameter(req.getParameterMap(), "suffix");
        StaticModuleLogger.staticLog(suffix);
        StaticModuleLogger.log(suffix);
        org.apache.commons.logging.LogFactory.getLog(LOGGER_NAME).info("Apache Commons Logging log from LoggingServlet " + suffix);
        org.apache.log4j.Logger.getLogger(LOGGER_NAME).info("log4j log from module from LoggingServlet " + suffix);
        org.jboss.logging.Logger.getLogger(LOGGER_NAME).infof("JBoss Logging log from module from LoggingServlet %s", suffix);
        org.slf4j.LoggerFactory.getLogger(LOGGER_NAME).info("slf4j log from module from LoggingServlet " + suffix);
    }

    private static String getFirstParameter(final Map<String, String[]> params, final String key) {
        final String[] values = params.get(key);
        if (values != null && values.length > 0) {
            return values[0];
        }
        return "";
    }
}

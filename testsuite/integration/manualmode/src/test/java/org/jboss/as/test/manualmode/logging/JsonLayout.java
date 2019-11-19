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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.json.stream.JsonGeneratorFactory;

import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class JsonLayout extends Layout {

    private final JsonGeneratorFactory factory;

    public JsonLayout() {
        factory = Json.createGeneratorFactory(Collections.emptyMap());
    }

    @Override
    public String format(final LoggingEvent event) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JsonGenerator generator = factory.createGenerator(out, StandardCharsets.UTF_8)) {
            generator.writeStartObject();

            final DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());
            generator.write("timestamp", formatter.format(Instant.ofEpochMilli(event.getTimeStamp())));
            generator.write("sequence", -1);
            generator.write("loggerClassName", event.getFQNOfLoggerClass());
            generator.write("loggerName", event.getLoggerName());
            generator.write("level", event.getLevel().toString());
            generator.write("message", event.getRenderedMessage());
            generator.write("threadName", event.getThreadName());
            generator.writeNull("threadId");
            generator.writeStartObject("mdc");
            generator.writeEnd();
            generator.write("ndc", event.getNDC());

            final ThrowableInformation throwableInformation = event.getThrowableInformation();
            if (throwableInformation != null) {
                final Throwable cause = throwableInformation.getThrowable();
                if (cause != null) {
                    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try (PrintStream ps = new PrintStream(baos)) {
                        cause.printStackTrace(ps);
                        generator.write("stackTrace", baos.toString());
                    }
                }
            }

            generator.writeEnd();
            generator.flush();
        }
        out.write('\n');
        return out.toString();
    }

    @Override
    public boolean ignoresThrowable() {
        return false;
    }

    @Override
    public void activateOptions() {

    }
}

/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.grpc;

import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.as.threads.ThreadsParser;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * XML writer for the gRPC subsystem.
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
class GrpcSubsystemWriter implements XMLElementWriter<SubsystemMarshallingContext> {

    @Override
    public void writeContent(final XMLExtendedStreamWriter writer, final SubsystemMarshallingContext context) throws XMLStreamException {
        final ThreadsParser threadsParser = new ThreadsParser();
        context.startSubsystemElement(GrpcSubsystemSchema.CURRENT.getUri(), false);

        final ModelNode model = context.getModelNode();

        // Write subsystem attributes
        GrpcSubsystemRegistrar.DEFAULT_THREAD_POOL.marshallAsAttribute(model, writer);

        // Write thread-pool children using ThreadsParser
        if (model.hasDefined(GrpcThreadPoolResourceDefinition.NAME)) {
            final List<Property> threadPools = model.get(GrpcThreadPoolResourceDefinition.NAME).asPropertyList();
            for (Property threadPool : threadPools) {
                threadsParser.writeUnboundedQueueThreadPool(writer, threadPool, GrpcThreadPoolResourceDefinition.NAME, true);
            }
        }

        writer.writeEndElement();
    }
}

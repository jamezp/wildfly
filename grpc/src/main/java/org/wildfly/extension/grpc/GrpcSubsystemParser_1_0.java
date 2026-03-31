/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.grpc;

import static org.jboss.as.threads.Namespace.THREADS_1_1;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.AttributeParser;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.threads.ThreadsParser;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * Parser for the gRPC subsystem.
 *
 * @author <a href="mailto:jperkins@ibm.com">James R. Perkins</a>
 */
class GrpcSubsystemParser_1_0 implements XMLStreamConstants, XMLElementReader<List<ModelNode>> {

    @Override
    public void readElement(final XMLExtendedStreamReader reader, final List<ModelNode> ops) throws XMLStreamException {
        final ThreadsParser threadsParser = new ThreadsParser();
        final PathAddress subsystemAddress = PathAddress.pathAddress(GrpcSubsystemRegistrar.REGISTRATION.getPathElement());

        // Add the subsystem
        final ModelNode subsystemAddOp = Util.createAddOperation(subsystemAddress);
        ops.add(subsystemAddOp);

        // Parse subsystem attributes
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            final String attrName = reader.getAttributeLocalName(i);
            final String attrValue = reader.getAttributeValue(i);

            if (attrName.equals(GrpcSubsystemRegistrar.DEFAULT_THREAD_POOL.getName())) {
                final AttributeParser parser = GrpcSubsystemRegistrar.DEFAULT_THREAD_POOL.getParser();
                parser.parseAndSetParameter(GrpcSubsystemRegistrar.DEFAULT_THREAD_POOL, attrValue, subsystemAddOp, reader);
            } else {
                throw ParseUtils.unexpectedAttribute(reader, i);
            }
        }

        // Find required elements
        final Set<Element> requiredElements = EnumSet.of(Element.THREAD_POOL);

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final String localName = reader.getLocalName();
            final Element element = Element.forName(localName);

            if (element == Element.THREAD_POOL) {
                threadsParser.parseUnboundedQueueThreadPool(reader, reader.getNamespaceURI(),
                        THREADS_1_1, subsystemAddress.toModelNode(), ops,
                        GrpcThreadPoolResourceDefinition.NAME, null);
                requiredElements.remove(Element.THREAD_POOL);
            } else {
                throw ParseUtils.unexpectedElement(reader);
            }
        }

        if (!requiredElements.isEmpty()) {
            throw ParseUtils.missingRequired(reader, requiredElements);
        }
    }

    enum Element {
        THREAD_POOL("thread-pool"),
        UNKNOWN(null);

        private final String name;

        Element(final String name) {
            this.name = name;
        }

        static Element forName(String localName) {
            for (Element element : values()) {
                if (element.name != null && element.name.equals(localName)) {
                    return element;
                }
            }
            return UNKNOWN;
        }
    }
}

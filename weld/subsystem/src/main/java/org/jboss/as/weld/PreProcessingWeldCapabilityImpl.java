/*
 * Copyright 2022 Red Hat, Inc.
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

package org.jboss.as.weld;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class PreProcessingWeldCapabilityImpl implements PreProcessingWeldCapability {
    static final PreProcessingWeldCapability INSTANCE = new PreProcessingWeldCapabilityImpl();

    private final Map<String, Boolean> annotations;

    private PreProcessingWeldCapabilityImpl() {
        annotations = new ConcurrentHashMap<>();
    }

    @Override
    public PreProcessingWeldCapability addBeanDefiningAnnotation(final Class<? extends Annotation> annotation, final boolean inherited) {
        annotations.put(annotation.getName(), inherited);
        return this;
    }

    @Override
    public Map<String, Boolean> getBeanDefiningAnnotations() {
        return Map.copyOf(annotations);
    }
}

/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.messaging.broker.observe.trace;

import io.ballerina.messaging.broker.observe.trace.config.OpenTracingConfiguration;
import io.opentracing.References;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * This class wraps the functions of the opentracing APIs
 */
public class OpenTracerManager {

    private static final String ROOT_CONTEXT = "root_context";
    private TracersStore tracerStore;
    private SpanStore spanStore;
    private boolean enabled = false;

    private OpenTracerManager() {}

    public OpenTracerManager(OpenTracingConfiguration openTracingConfig) {
        if (openTracingConfig != null) {
            tracerStore = new TracersStore(openTracingConfig);
            enabled = true;
        }
        spanStore = new SpanStore();
    }

    /**
     * Method to start a span.
     *
     * @param serviceName   name of the service the span should belong to
     * @param spanName      name of the span
     * @param tags          key value paired tags to attach to the span
     * @param referenceType type of reference to any parent span
     * @param parentSpanId  id of the parent span
     * @return unique id of the created span
     */
    public String startSpan(String serviceName, String spanName, Map<String, String> tags, ReferenceType referenceType,
                            String parentSpanId, boolean activate) {
        if (enabled) {
            Map<String, Span> spanMap = new HashMap<>();
            Map<String, Tracer> tracers = tracerStore.getTracers(serviceName);

            final Map parentSpanContext;
            if (ROOT_CONTEXT.equals(parentSpanId) || parentSpanId == null) {
                parentSpanContext = new HashMap<>();
            } else {
                parentSpanContext = spanStore.getParent(parentSpanId);
            }

            tracers.forEach((tracerName, tracer) -> {
                Tracer.SpanBuilder spanBuilder = tracer.buildSpan(spanName);
                if (tags != null) {
                    for (Map.Entry<String, String> tag : tags.entrySet()) {
                        spanBuilder = spanBuilder.withTag(tag.getKey(), tag.getValue());
                    }
                }

                if (parentSpanContext != null && !parentSpanContext.isEmpty()) {
                    spanBuilder = setParent(referenceType, parentSpanContext, spanBuilder, tracerName);
                }

                Span span = spanBuilder.start();
                if (activate) {
                    tracer.scopeManager().activate(span, false);
                }
                spanMap.put(tracerName, span);
            });

            String spanId = UUID.randomUUID().toString();
            spanStore.addSpan(spanId, spanMap);
            return spanId;
        } else {
            return null;
        }
    }

    private Tracer.SpanBuilder setParent(ReferenceType referenceType, Map parentSpanContext,
                                         Tracer.SpanBuilder spanBuilder, String tracerName) {

        Object parentSpan = parentSpanContext.get(tracerName);
        if (parentSpan != null) {
            if (parentSpan instanceof SpanContext) {
                if (ReferenceType.CHILDOF == referenceType) {
                    spanBuilder = spanBuilder.asChildOf((SpanContext) parentSpan);
                } else if (ReferenceType.FOLLOWSFROM == referenceType) {
                    spanBuilder.addReference(References.FOLLOWS_FROM, (SpanContext) parentSpan);
                }
            } else if (parentSpan instanceof Span) {
                if (ReferenceType.CHILDOF == referenceType) {
                    spanBuilder = spanBuilder.asChildOf((((Span) parentSpan).context()));
                } else if (ReferenceType.FOLLOWSFROM == referenceType) {
                    spanBuilder.addReference(References.FOLLOWS_FROM, (((Span) parentSpan).context()));
                }
            }
        }
        return spanBuilder;
    }

    /**
     * Method to activate given span.
     *
     * @param serviceName the service name
     * @param spanId the id of the span to finish
     */
    public void activateSpan(String serviceName, String spanId) {
        if (enabled) {
            Map<String, Tracer> tracers = tracerStore.getTracers(serviceName);
            Map<String, Span> spanMap = spanStore.getSpan(spanId);

            if (spanMap != null) {
                spanMap.forEach((tracerName, span) -> {
                    Tracer tracer = tracers.get(tracerName);
                    tracer.scopeManager().activate(span, false);
                });
            }
        }
    }

    /**
     * Method to mark a span as finished.
     *
     * @param spanId the id of the span to finish
     */
    public void finishSpan(String spanId) {
        if (enabled) {
            Map<String, Span> spanMap = spanStore.closeSpan(spanId);
            if (spanMap != null) {
                spanMap.forEach((tracerName, span) -> span.finish());
            }
        }
    }

    /**
     * Method to add tags to an existing span.
     *
     * @param spanId   the id of the span
     * @param tagKey   the key of the tag
     * @param tagValue the value of the tag
     */
    public void addTags(String spanId, String tagKey, String tagValue) {
        if (enabled) {
            Map<String, Span> spanList = spanStore.getSpan(spanId);
            if (spanList != null) {
                spanList.forEach((tracerName, span) -> span.setTag(tagKey, tagValue));
            }
        }
    }

    /**
     * Method to add tags to an existing span.
     *
     * @param spanId   the id of the span
     * @param tagKey   the key of the tag
     * @param tagValue the int value of the tag
     */
    public void addTags(String spanId, String tagKey, Number tagValue) {
        if (enabled) {
            Map<String, Span> spanList = spanStore.getSpan(spanId);
            if (spanList != null) {
                spanList.forEach((tracerName, span) -> span.setTag(tagKey, tagValue));
            }
        }
    }

    /**
     * Method to add tags to an existing span.
     *
     * @param spanId   the id of the span
     * @param tagKey   the key of the tag
     * @param tagValue the boolean value of the tag
     */
    public void addTags(String spanId, String tagKey, boolean tagValue) {
        if (enabled) {
            Map<String, Span> spanList = spanStore.getSpan(spanId);
            if (spanList != null) {
                spanList.forEach((tracerName, span) -> span.setTag(tagKey, tagValue));
            }
        }
    }

    /**
     * Method to add logs to an existing span.
     *
     * @param spanId the id of the span
     * @param logs   the map (event, message) to be logged
     */
    public void log(String spanId, Map<String, String> logs) {
        if (enabled) {
            Map<String, Span> spanList = spanStore.getSpan(spanId);
            spanList.forEach((tracerName, span) -> span.log(logs));
        }
    }

    /**
     * Method to add baggage item to an existing span.
     *
     * @param spanId       the id of the span
     * @param baggageKey   the key of the baggage item
     * @param baggageValue the value of the baggage item
     */
    public void setBaggageItem(String spanId, String baggageKey, String baggageValue) {
        if (enabled) {
            Map<String, Span> spanList = spanStore.getSpan(spanId);
            spanList.forEach((tracerName, span) -> span.setBaggageItem(baggageKey, baggageValue));
        }
    }

    /**
     * Method to get a baggage value from an existing span.
     *
     * @param spanId     the id of the span
     * @param baggageKey the key of the baggage item
     */
    public String getBaggageItem(String spanId, String baggageKey) {
        String baggageValue = null;
        if (enabled) {
            Map<String, Span> spanMap = spanStore.getSpan(spanId);
            for (Map.Entry<String, Span> spanEntry : spanMap.entrySet()) {
                baggageValue = spanEntry.getValue().getBaggageItem(baggageKey);
                if (baggageValue != null) {
                    break;
                }
            }
        }
        return baggageValue;
    }
}

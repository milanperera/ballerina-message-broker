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

package io.ballerina.messaging.broker.core.trace;

import io.ballerina.messaging.broker.observe.trace.OpenTracerManager;
import io.ballerina.messaging.broker.observe.trace.config.OpenTracingConfiguration;

import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of {@link BrokerTracingManager}
 */
public class DefaultBrokerTracingManager implements BrokerTracingManager {

    private final OpenTracerManager manager;

    private String parentSpan;

    public String getParentSpan() {
        return parentSpan;
    }

    public void setParentSpan(String parentSpan) {
        this.parentSpan = parentSpan;
    }

    public DefaultBrokerTracingManager(OpenTracingConfiguration configuration) {
        manager = new OpenTracerManager(configuration);
    }

    @Override
    public String startSpan(Tracer tracer) {
        return manager.startSpan(tracer.getServiceName(), tracer.getSpanName(), null,
                tracer.getReferenceType(), tracer.getParentSpanId(), tracer.isSpanActivated());
    }

    @Override
    public void stopSpan(String spanId) {
        manager.finishSpan(spanId);
    }

    @Override
    public void addLog(String spanId, String key, String value) {
        Map<String, String> logs = new HashMap<>();
        logs.put(key, value);
        manager.log(spanId, logs);
    }

    @Override
    public void addTag(String spanId, String key, String value) {
        manager.addTags(spanId, key, value);
    }

    @Override
    public void addTag(String spanId, String key, Number value) {
        manager.addTags(spanId, key, value);
    }

    @Override
    public void addTag(String spanId, String key, boolean value) {
        manager.addTags(spanId, key, value);
    }
}

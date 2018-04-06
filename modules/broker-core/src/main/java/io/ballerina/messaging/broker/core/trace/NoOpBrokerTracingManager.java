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

/**
 * Null object implementation for {@link BrokerTracingManager}
 */
public class NoOpBrokerTracingManager implements BrokerTracingManager {

    @Override
    public String startSpan(Tracer tracer) {
        return null;
    }

    @Override
    public void stopSpan(String spanId) {

    }

    @Override
    public void addLog(String spanId, String key, String value) {

    }

    @Override
    public void addTag(String spanId, String key, String value) {

    }

    @Override
    public void addTag(String spanId, String key, Number value) {

    }

    @Override
    public void addTag(String spanId, String key, boolean value) {

    }

    public String getParentSpan() {
        return null;
    }

    public void setParentSpan(String parentSpan) {

    }
}

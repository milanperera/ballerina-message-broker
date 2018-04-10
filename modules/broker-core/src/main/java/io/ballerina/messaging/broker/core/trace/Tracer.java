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


import io.ballerina.messaging.broker.observe.trace.ReferenceType;

/**
 * This class represents the values related to tracer
 */
public class Tracer {

    private String serviceName;
    private String spanName;
    private ReferenceType referenceType;
    private String parentSpanId;
    // by default it is active unless specified false
    private boolean activateSpan = true;

    private void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    private void setSpanName(String spanName) {
        this.spanName = spanName;
    }

    private void setReferenceType(ReferenceType referenceType) {
        this.referenceType = referenceType;
    }

    public void setParentSpanId(String parentSpanId) {
        this.parentSpanId = parentSpanId;
    }

    private void setActivateSpan(boolean activateSpan) {
        this.activateSpan = activateSpan;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getSpanName() {
        return spanName;
    }

    public ReferenceType getReferenceType() {
        return referenceType;
    }

    public String getParentSpanId() {
        return parentSpanId;
    }

    public boolean isSpanActivated() {
        return activateSpan;
    }

    /**
     * This class is used to build the trace object
     */
    public static class TracerBuilder {

        private final Tracer tracer = new Tracer();

        public TracerBuilder serviceName(String serviceName) {
            tracer.setServiceName(serviceName);
            return this;
        }

        public TracerBuilder spanName(String spanName) {
            tracer.setSpanName(spanName);
            return this;
        }

        public TracerBuilder referenceType(ReferenceType referenceType) {
            tracer.setReferenceType(referenceType);
            return this;
        }

        public TracerBuilder parentSpanId(String spanId) {
            tracer.setParentSpanId(spanId);
            return this;
        }

        public TracerBuilder activate(boolean active) {
            tracer.setActivateSpan(active);
            return this;
        }

        public Tracer build() {
            return tracer;
        }
    }
}

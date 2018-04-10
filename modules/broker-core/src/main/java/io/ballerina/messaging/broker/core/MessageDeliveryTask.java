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
 *
 */

package io.ballerina.messaging.broker.core;

import io.ballerina.messaging.broker.core.task.Task;
import io.ballerina.messaging.broker.core.trace.BrokerTracingManager;
import io.ballerina.messaging.broker.core.trace.Tracer;
import io.ballerina.messaging.broker.core.util.MessageTracer;
import io.ballerina.messaging.broker.observe.trace.ReferenceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.ballerina.messaging.broker.core.trace.Constants.Log.MESSAGE;
import static io.ballerina.messaging.broker.core.trace.Constants.Tag.CONSUMER;
import static io.ballerina.messaging.broker.core.trace.Constants.Tag.CONSUMER_ID;
import static io.ballerina.messaging.broker.core.trace.Constants.Tag.MESSAGE_EXCHANGE;
import static io.ballerina.messaging.broker.core.trace.Constants.Tag.MESSAGE_QUEUE;

/**
 * Delivers messages to consumers for a given queueHandler.
 */
final class MessageDeliveryTask extends Task {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageDeliveryTask.class);

    private final QueueHandler queueHandler;

    private final BrokerTracingManager tracingManager;

    MessageDeliveryTask(QueueHandler queueHandler) {
        this.queueHandler = queueHandler;
        this.tracingManager = queueHandler.getTracingManager();
    }

    @Override
    public void onAdd() {

    }

    @Override
    public void onRemove() {

    }

    @Override
    public String getId() {
        return queueHandler.getQueue().getName();
    }

    @Override
    public TaskHint call() throws Exception {
        CyclicConsumerIterator consumerIterator = queueHandler.getCyclicConsumerIterator();
        if (!consumerIterator.hasNext()) {
            return TaskHint.IDLE;
        }

        int deliveredCount = 0;
        Consumer previousConsumer = null;
        while (true) {
            Consumer consumer = consumerIterator.next();

            if (!consumer.isReady()) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Consumer {} is not ready for consuming messages from {}",
                                 consumer,
                                 queueHandler.getQueue().getName());
                }

                if (consumer.equals(previousConsumer)) {
                    return TaskHint.IDLE;
                } else {
                    previousConsumer = consumer;
                    continue;
                }
            } else {
                // TODO: handle send errors
                Message message = queueHandler.takeForDelivery();
                if (message != null) {
                    Tracer tracerSend = new Tracer.TracerBuilder().
                            serviceName("Consumer").
                            spanName("send").
                            referenceType(ReferenceType.ROOT).
                            build();

                    String parentSpan = tracingManager.startSpan(tracerSend);
                    LOGGER.debug("Sending message {} to {}", message, consumer);
                    MessageTracer.trace(message, queueHandler, MessageTracer.DELIVER);
                    tracingManager.addLog(parentSpan, MESSAGE, MessageTracer.DELIVER);
                    tracingManager.addTag(parentSpan, MESSAGE_QUEUE, message.getMetadata().getRoutingKey());
                    tracingManager.addTag(parentSpan, MESSAGE_EXCHANGE, message.getMetadata().getExchangeName());
                    tracingManager.addTag(parentSpan, CONSUMER_ID, consumer.getId());
                    tracingManager.addTag(parentSpan, CONSUMER, consumer.toString());
                    message.setParentSpan(parentSpan);
                    consumer.send(message);
                    deliveredCount++;
                    // TODO: make the value configurable
                    if (deliveredCount == 1000) {
                        break;
                    }
                } else {
                    // We need to break the while loop if there are no messages in the queue
                    break;
                }
            }
            previousConsumer = consumer;
        }
        if (deliveredCount > 0) {
            return TaskHint.ACTIVE;
        } else {
            return TaskHint.IDLE;
        }
    }
}

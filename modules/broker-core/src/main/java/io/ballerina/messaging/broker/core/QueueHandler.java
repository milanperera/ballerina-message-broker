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

import com.google.common.collect.Iterables;
import io.ballerina.messaging.broker.common.ValidationException;
import io.ballerina.messaging.broker.common.util.function.ThrowingConsumer;
import io.ballerina.messaging.broker.core.metrics.BrokerMetricManager;
import io.ballerina.messaging.broker.core.queue.MemQueueImpl;
import io.ballerina.messaging.broker.core.queue.UnmodifiableQueueWrapper;
import io.ballerina.messaging.broker.core.trace.BrokerTracingManager;
import io.ballerina.messaging.broker.core.trace.Tracer;
import io.ballerina.messaging.broker.core.util.MessageTracer;

import io.ballerina.messaging.broker.observe.trace.ReferenceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.transaction.xa.Xid;

/**
 * Represents the queue of the broker. Contains a bounded queue to store messages. Subscriptions for the queue are
 * maintained as an in-memory set.
 */
public final class QueueHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueueHandler.class);

    private Queue queue;

    private final CyclicConsumerIterator consumerIterator;

    private final Queue redeliveryQueue;

    /**
     * Used to send metric signals related to queue handler.
     */
    private final BrokerMetricManager metricManager;

    private final BrokerTracingManager tracingManager;

    private final Set<Consumer> consumers;

    private final Queue unmodifiableQueueView;

    private final Map<Binding, ThrowingConsumer<Binding, BrokerException>> bindingChangeListenersMap;

    QueueHandler(Queue queue, BrokerMetricManager metricManager, BrokerTracingManager tracingManager) {
        this.queue = queue;
        queue.setQueueHandler(this);
        unmodifiableQueueView = new UnmodifiableQueueWrapper(queue);
        // TODO: take message count from queue configuration
        // We create an unbounded redelivery queue since we keep the messages which are already in memory which does
        // not increase memory usage. When loading data to memory we should consider messages in both queue and
        // redelivery queue data structures.
        this.redeliveryQueue = new MemQueueImpl(queue.getName(), false);
        this.metricManager = metricManager;
        this.consumers = ConcurrentHashMap.newKeySet();
        consumerIterator = new CyclicConsumerIterator();
        bindingChangeListenersMap = new ConcurrentHashMap<>();
        this.tracingManager = tracingManager;
    }

    public Queue getQueue() {
        return unmodifiableQueueView;
    }

    /**
     * Retrieve all the current consumers for the queue.
     *
     * @return Set of unmodifiable subscription objects
     */
    public Collection<Consumer> getConsumers() {
        return Collections.unmodifiableCollection(consumers);
    }

    /**
     * Add a new consumer to the queue.
     *
     * @param consumer {@link Consumer} implementation.
     *
     * @return true if {@link Consumer} was successfully added.
     */
    boolean addConsumer(Consumer consumer) {
        return consumers.add(consumer);
    }

    /**
     * Remove consumer from the queue. NOTE: This method is synchronized with getting next subscriber for the queue to
     * avoid concurrent issues
     *
     * @param consumer {@link Consumer} to be removed.
     *
     * @return True if the {@link Consumer} is removed.
     */
    boolean removeConsumer(Consumer consumer) {
        return consumers.remove(consumer);
    }

    /**
     * Put the message to the tail of the queue. If the queue is full message will get dropped
     *
     * @param message {@link Message}
     */
    void enqueue(Message message) throws BrokerException {
        Tracer tracer = new Tracer.TracerBuilder().
                serviceName("QueueHandler").
                spanName("enqueue").
                referenceType(ReferenceType.CHILDOF).
                parentSpanId(tracingManager.getParentSpan()).
                build();

        String enqueueSpan = tracingManager.startSpan(tracer);
        try {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Enqueuing message {} to queue {}", message, queue.getName());
            }

            tracingManager.addTag(enqueueSpan, "message.id", String.valueOf(message.getInternalId()));
            tracingManager.addTag(enqueueSpan, "message.queue", getQueue().getName());
            tracingManager.addTag(enqueueSpan, "message.exchange", message.getMetadata().getExchangeName());
            tracingManager.addTag(enqueueSpan, "message.redelivery.count", message.getRedeliveryCount());
            tracingManager.addTag(enqueueSpan, "message.is.delivered", message.isRedelivered());
            boolean success = queue.enqueue(message);
            if (success) {
                metricManager.addInMemoryMessage();
                MessageTracer.trace(message, this, MessageTracer.PUBLISH_SUCCESSFUL);
                tracingManager.addLog(enqueueSpan, "message", MessageTracer.PUBLISH_SUCCESSFUL);
            } else {
                message.release();
                MessageTracer.trace(message, this, MessageTracer.PUBLISH_FAILURE);
                tracingManager.addLog(enqueueSpan, "message", MessageTracer.PUBLISH_FAILURE);
                LOGGER.info("Failed to publish message {} to the queue {}", message, queue.getName());
            }
        } finally {
            tracingManager.stopSpan(enqueueSpan);
            //stopping parent span here
            tracingManager.stopSpan(tracingManager.getParentSpan());
        }
    }

    void prepareForEnqueue(Xid xid, Message message) throws BrokerException {
        MessageTracer.trace(message, xid, this, MessageTracer.PREPARE_ENQUEUE);
        queue.prepareEnqueue(xid, message);
    }

    void prepareForDetach(Xid xid, Message message) throws BrokerException {
        MessageTracer.trace(message, xid, this, MessageTracer.PREPARE_DEQUEUE);
        queue.prepareDetach(xid, message);
    }

    public void commit(Xid xid) {
        queue.commit(xid);
        MessageTracer.trace(xid, this, MessageTracer.QUEUE_COMMIT);
    }

    public void rollback(Xid xid) {
        queue.rollback(xid);
        MessageTracer.trace(xid, this, MessageTracer.QUEUE_ROLLBACK);
    }

    /**
     * Retrieves next available message for delivery. If the queue is empty, null is returned.
     *
     * @return Message
     */
    Message takeForDelivery() {
        Message message = redeliveryQueue.dequeue();
        if (message == null) {
            message = queue.dequeue();
            MessageTracer.trace(message, this, MessageTracer.RETRIEVE_FOR_DELIVERY);
        } else {
            MessageTracer.trace(message, this, MessageTracer.RETRIEVE_FOR_REDELIVERY);
        }

        return message;
    }

    /**
     * Removes the message from the queue.
     *
     * @param message message to be removed.
     *
     * @throws BrokerException throws on failure to dequeue the message.
     */
    void dequeue(Message message) throws BrokerException {
        queue.detach(message);
        metricManager.removeInMemoryMessage();
        MessageTracer.trace(message, this, MessageTracer.ACKNOWLEDGE);
    }

    public void requeue(Message message) throws BrokerException {
        boolean success = redeliveryQueue.enqueue(message);
        if (!success) {
            LOGGER.warn("Enqueuing message since redelivery queue for {} is full. message:{}",
                        queue.getName(),
                        message);
            enqueue(message);
        }
        MessageTracer.trace(message, this, MessageTracer.REQUEUE);
    }

    /**
     * Get the current consumer list iterator for the queue. This is a snapshot of the consumers at the time when the
     * when this method is invoked.
     *
     * @return CyclicConsumerIterator
     */
    CyclicConsumerIterator getCyclicConsumerIterator() {
        consumerIterator.setIterator(Iterables.cycle(consumers).iterator());
        return consumerIterator;
    }

    /**
     * True if there are no {@link Message} objects in the queue and false otherwise.
     *
     * @return True if the queue doesn't contain any {@link Message} objects
     */
    boolean isEmpty() {
        return size() == 0;
    }

    /**
     * Returns the number of {@link Message} objects in this queue.
     *
     * @return Number of {@link Message} objects in the queue.
     */
    public int size() {
        return queue.size() + redeliveryQueue.size();
    }

    /**
     * True if there are no consumers and false otherwise.
     *
     * @return True if there are no {@link Consumer} for the queue.
     */
    boolean isUnused() {
        return consumers.isEmpty();
    }

    private void closeAllConsumers() {
        Iterator<Consumer> iterator = consumers.iterator();
        while (iterator.hasNext()) {

            Consumer consumer = iterator.next();
            try {
                consumer.close();
            } catch (BrokerException e) {
                LOGGER.error("Error occurred while closing the consumer [ " + consumer + " ] " +
                                     "for queue [ " + queue.toString() + " ]", e);
            } finally {
                iterator.remove();
            }
        }
    }

    public int consumerCount() {
        return consumers.size();
    }

    public void addBinding(Binding binding, ThrowingConsumer<Binding, BrokerException> bindingChangeListener) {
        bindingChangeListenersMap.put(binding, bindingChangeListener);
    }

    public void releaseResources() throws BrokerException {
        closeAllConsumers();
        for (Map.Entry<Binding, ThrowingConsumer<Binding, BrokerException>> entry
                : bindingChangeListenersMap.entrySet()) {
            entry.getValue().accept(entry.getKey());
        }
    }

    public void removeBinding(Binding binding) {
        bindingChangeListenersMap.remove(binding);
    }

    public int purgeQueue() throws ValidationException {
        if (consumerCount() == 0) {
            int queueMessages = queue.clear();
            int totalMessages = queueMessages + redeliveryQueue.size();
            redeliveryQueue.clear();

            return totalMessages;
        } else {
            throw new ValidationException("Cannot purge queue " + queue.getName() + " since there " + consumerCount()
                                                  + " active consumer(s)");
        }
    }

}

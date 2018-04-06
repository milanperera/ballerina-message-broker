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

import io.ballerina.messaging.broker.core.configuration.BrokerCoreConfiguration;
import io.ballerina.messaging.broker.core.metrics.BrokerMetricManager;
import io.ballerina.messaging.broker.core.queue.DbBackedQueueImpl;
import io.ballerina.messaging.broker.core.queue.MemQueueImpl;
import io.ballerina.messaging.broker.core.queue.QueueBufferFactory;
import io.ballerina.messaging.broker.core.store.DbMessageStore;
import io.ballerina.messaging.broker.core.trace.BrokerTracingManager;

/**
 * DB backed factory for creating queue handler objects.
 */
public class DbBackedQueueHandlerFactory implements QueueHandlerFactory {
    private final DbMessageStore dbMessageStore;
    private final BrokerMetricManager metricManager;
    private final int nonDurableQueueMaxDepth;
    private QueueBufferFactory queueBufferFactory;
    private final BrokerTracingManager tracingManager;

    public DbBackedQueueHandlerFactory(DbMessageStore dbMessageStore, BrokerMetricManager metricManager,
                                       BrokerCoreConfiguration configuration, BrokerTracingManager tracingManager) {
        this.dbMessageStore = dbMessageStore;
        this.metricManager = metricManager;
        nonDurableQueueMaxDepth = Integer.parseInt(configuration.getNonDurableQueueMaxDepth());
        queueBufferFactory = new QueueBufferFactory(configuration);
        this.tracingManager = tracingManager;
    }

    /**
     * Create a durable queue handler with the give arguments.
     *
     * @param queueName  name of the queue
     * @param autoDelete true if auto deletable
     * @return QueueHandler object
     * @throws BrokerException if cannot create queue handler
     */
    public QueueHandler createDurableQueueHandler(String queueName, boolean autoDelete) throws BrokerException {
        Queue queue = new DbBackedQueueImpl(queueName, autoDelete, dbMessageStore, queueBufferFactory);
        return new QueueHandler(queue, metricManager, tracingManager);
    }

    /**
     * Create a non durable queue handler with the give arguments.
     *
     * @param queueName  name of the queue
     * @param autoDelete true if auto deletable
     * @return QueueHandler object
     */
    public QueueHandler createNonDurableQueueHandler(String queueName, boolean autoDelete) {
        Queue queue = new MemQueueImpl(queueName, nonDurableQueueMaxDepth, autoDelete);
        return new QueueHandler(queue, metricManager, tracingManager);
    }

}

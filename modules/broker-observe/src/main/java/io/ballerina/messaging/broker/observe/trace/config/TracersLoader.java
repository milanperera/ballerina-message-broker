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
package io.ballerina.messaging.broker.observe.trace.config;

import org.wso2.carbon.config.ConfigProviderFactory;
import org.wso2.carbon.config.ConfigurationException;
import org.wso2.carbon.config.provider.ConfigProvider;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * This is the config file loader for the global property provided by the ballerina global config.
 */
public class TracersLoader {

    // System property to specify the path of the trace config file.
    public static final String SYSTEM_PARAM_TRACE_CONFIG = "broker.trace.config";
    public static final String TRACERS_CONFIG_NAMESPACE = "broker.tracers";

    private TracersLoader() {
    }

    public static OpenTracingConfiguration load() throws ConfigurationException {
        Path traceYamlFile;
        String usersFilePath = System.getProperty(SYSTEM_PARAM_TRACE_CONFIG);
        if (usersFilePath == null || usersFilePath.trim().isEmpty()) {
            // use current path.
            return null;
        } else {
            traceYamlFile = Paths.get(usersFilePath).toAbsolutePath();
            ConfigProvider configProvider = ConfigProviderFactory.getConfigProvider(traceYamlFile, null);
            OpenTracingConfiguration openTracingConfiguration = configProvider
                    .getConfigurationObject(TRACERS_CONFIG_NAMESPACE, OpenTracingConfiguration.class);
            return openTracingConfiguration;
        }
    }
}

/*
 * Copyright 2017-2026 B2i Healthcare, https://b2ihealthcare.com
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.b2international.index.es;

import java.util.Map;

import javax.net.ssl.SSLContext;

import com.b2international.index.IndexClient;
import com.b2international.index.IndexClientFactory;
import com.b2international.index.es.admin.EsIndexAdmin;
import com.b2international.index.es.client.EsClient;
import com.b2international.index.es8.Es8Client;
import com.b2international.index.mapping.Mappings;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @since 5.10
 */
public final class EsIndexClientFactory implements IndexClientFactory {

	@Override
	public IndexClient createClient(String name, ObjectMapper mapper, Mappings mappings, Map<String, Object> settings) {
		// generic ES cluster settings
		final String clusterUrl = (String) settings.getOrDefault(CLUSTER_URL, DEFAULT_CLUSTER_URL);
		final String clusterName = (String) settings.getOrDefault(CLUSTER_NAME, DEFAULT_CLUSTER_NAME);
		final Object connectTimeoutSetting = settings.getOrDefault(CONNECT_TIMEOUT, DEFAULT_CONNECT_TIMEOUT);
		final Object socketTimeoutSetting = settings.getOrDefault(SOCKET_TIMEOUT, DEFAULT_SOCKET_TIMEOUT);
		final int connectTimeout = connectTimeoutSetting instanceof Integer ? (int) connectTimeoutSetting : Integer.parseInt((String) connectTimeoutSetting);
		final int socketTimeout = socketTimeoutSetting instanceof Integer ? (int) socketTimeoutSetting : Integer.parseInt((String) socketTimeoutSetting);
		final String username = (String) settings.getOrDefault(CLUSTER_USERNAME, "");
		final String password = (String) settings.getOrDefault(CLUSTER_PASSWORD, "");
		final SSLContext sslContext = (SSLContext) settings.get(CLUSTER_SSL_CONTEXT);
		final EsClientConfiguration configuration = new EsClientConfiguration(clusterName, clusterUrl, username, password, connectTimeout, socketTimeout, sslContext, mapper);

		// Old High-level REST client running in compatibility mode
		final EsClient client = EsClient.create(configuration);
		// Elasticsearch Java client for version 8.x and up
		final Es8Client es8Client = Es8Client.create(configuration);
		
		return new EsIndexClient(new EsIndexAdmin(client, es8Client, mapper, name, mappings, settings), mapper);
	}
}

/*
 * Copyright 2018-2026 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.index.es.client;

import java.io.IOException;
import java.util.Set;

import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.index.reindex.RemoteInfo;
import org.elasticsearch.script.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.b2international.index.Activator;
import com.b2international.index.IndexException;
import com.b2international.index.es.EsClientConfiguration;
import com.b2international.index.es.client.http.EsHttpClient;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalNotification;
import com.google.common.util.concurrent.UncheckedExecutionException;

/**
 * @since 6.11
 */
public interface EsClient extends AutoCloseable {

	Logger LOG = LoggerFactory.getLogger("elastic-snowowl");
	
	/**
	 * We currently only support Elasticsearch 8.x clusters as 9.x requires moving completely to the new Java client.
	 */
	Set<String> SUPPORTED_MAJOR_VERSIONS = Set.of("8.");
	
	/**
	 * Gets the Elasticsearch version from the currently configured host using the Info Endpoint. 
	 * @return a version number in the form of "major.minor.patch", never <code>null</code>
	 * @throws IOException 
	 */
	String version() throws IOException;
	
	EsClusterStatus status(String...indices);
	
	IndicesClient indices();
	
	ClusterClient cluster();
	
	GetResponse get(GetRequest req) throws IOException;
	
	SearchResponse search(SearchRequest req) throws IOException;
	
	UpdateResponse update(UpdateRequest req) throws IOException;
	
	BulkProcessor.Builder bulk(BulkProcessor.Listener listener);
	
	BulkByScrollResponse updateByQuery(String index, int batchSize, Script script, QueryBuilder query) throws IOException;
	
	BulkByScrollResponse deleteByQuery(String index, int batchSize, QueryBuilder query) throws IOException;
	
	BulkByScrollResponse reindex(String sourceIndex, String destinationIndex, RemoteInfo remoteInfo, boolean refresh, int batchSize) throws IOException;
	
	static EsClient create(final EsClientConfiguration configuration) {
		return ClientPool.create(configuration);
	}
	
	static void closeAll() {
		ClientPool.closeAll();
	}
	
	/**
	 * @since 6.11
	 */
	final class ClientPool {
		
		private static final LoadingCache<EsClientConfiguration, EsClient> CLIENTS_BY_HOST = CacheBuilder.newBuilder()
				.removalListener(ClientPool::onRemove)
				.build(CacheLoader.from(ClientPool::onAdd));
		
		private ClientPool() {}
		
		static EsClient create(EsClientConfiguration configuration) {
			try {
				return CLIENTS_BY_HOST.getUnchecked(configuration);
			} catch (UncheckedExecutionException e) {
				if (e.getCause() instanceof RuntimeException) {
					throw (RuntimeException) e.getCause();
				} else {
					throw new RuntimeException(e.getCause());
				}
			}
		}

		static void closeAll() {
			CLIENTS_BY_HOST.invalidateAll();
			CLIENTS_BY_HOST.cleanUp();
		}
		
		static EsClient onAdd(final EsClientConfiguration configuration) {
			LOG.info("Connecting to Elasticsearch cluster with ES7 client at '{}'{}, connect timeout: {} ms, socket timeout: {} ms.", 
					configuration.getClusterUrl(),
					configuration.isProtected() ? " using basic authentication" : "",
					configuration.getConnectTimeout(),
					configuration.getSocketTimeout());
		
			EsClient client = new EsHttpClient(configuration);
			
			// check version and report if Elasticsearch version is not supported
			String elasticsearchVersion;
			try {
				elasticsearchVersion = client.version();
			} catch (Exception e) {
				throw new IndexException("Failed to determine version of underlying Elasticsearch cluster.", e);
			}
			
			if (SUPPORTED_MAJOR_VERSIONS.stream().noneMatch(supportedMajorVersion -> elasticsearchVersion.startsWith(supportedMajorVersion))) {
				throw new IndexException(String.format(
						"The connected Elasticsearch cluster is running a non-supported major version, '%s'. The currently supported major versions are: %s.",
						elasticsearchVersion, SUPPORTED_MAJOR_VERSIONS));
			}
			
			return client;
		}
		
		static void onRemove(final RemovalNotification<EsClientConfiguration, EsClient> notification) {
			Activator.withTccl(() -> {
				closeClient(notification.getKey(), notification.getValue());
			});
		}
		
		static void closeClient(final EsClientConfiguration configuration, EsClient client) {
			try {
				client.close();
				LOG.info("Closed ES client connected to '{}'", configuration.getClusterUrl());
			} catch (final Exception e) {
				LOG.error("Unable to close ES client connected to '{}'", configuration.getClusterUrl(), e);
			}
		}
		
	}

}

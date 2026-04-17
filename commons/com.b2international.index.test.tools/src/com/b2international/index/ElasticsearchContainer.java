/*
 * Copyright 2026 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.index;

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;

import org.apache.commons.lang3.SystemProperties;
import org.apache.commons.lang3.SystemUtils;
import org.eclipse.core.runtime.FileLocator;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitStrategy;
import org.testcontainers.dockerclient.DockerClientProviderStrategy;
import org.testcontainers.dockerclient.UnixSocketClientProviderStrategy;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.MountableFile;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

/**
 * Customized Elasticsearch container that can be used to run Snow Owl tests against. Each instance will boot up a separate Elasticsearch container so
 * memoize it in a ClassRule or singleton rule in order to use the same instance unless you need a specific Elasticsearch version and separate
 * container for a given test.
 * 
 * @since 10.0.0
 */
public final class ElasticsearchContainer {

	private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchContainer.class);
	
	/**
	 * Constant that represents the default bridge network name in Docker environments.
	 */
	private static final String DEFAULT_DOCKER_NETWORK_NAME = "bridge";
	
	/**
	 * Java system property to configure which Elasticsearch version to use when initializing its docker container. By default it uses the {@value #ES_DOCKER_VERSION} version.
	 */
	public static final String ES_DOCKER_VERSION_VARIABLE = "so.index.es.docker.version";
	
	/**
	 * The default Elasticsearch image version to run tests against. 
	 */
	public static final String ES_DOCKER_VERSION = "8.19.10";
	
	// override wait strategies for specific Elasticsearch versions as the log output may differ and relying on solely on started message arrival might not be enough
	private static final Map<Predicate<String>, WaitStrategy> WAIT_STRATEGIES = Map.of(
		version -> version.startsWith("8."), new LogMessageWaitStrategy().withRegEx(".*(\"message\":\\s?\"Security is enabled[\\s?|\"].*\n$)")
		// not specifying custom wait strategy for ES 7.x as the default one waits for the started message which is sufficient
		// version -> version.startsWith("7."), null
	);

	private final String elasticsearchDockerImageVersion;

	private org.testcontainers.elasticsearch.ElasticsearchContainer container;

	// computed values after the container has started
	private SSLContext sslContext;
	private String clusterUrl;
	
	public ElasticsearchContainer() throws Exception {
		this(System.getProperty(ES_DOCKER_VERSION_VARIABLE, ES_DOCKER_VERSION));
	}
	
	public ElasticsearchContainer(String elasticsearchDockerImageVersion) throws Exception {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(elasticsearchDockerImageVersion), "'elasticsearchDockerImageVersion' may not be null or empty.");
		this.elasticsearchDockerImageVersion = elasticsearchDockerImageVersion;
		var elasticsearchDockerImage = String.format("docker.elastic.co/elasticsearch/elasticsearch:%s", elasticsearchDockerImageVersion);

		// before creating the first Elasticsearchcontainer, make sure we trigger one client provider strategy init so that it won't fail when doing the first actual init
		// required only in CI Linux environments where the DOCKER_HOST env var is not set and we rely on the unix socket to init the docker client
		if (SystemUtils.IS_OS_LINUX || SystemUtils.IS_OS_MAC) {
			DockerClientProviderStrategy.getFirstValidStrategy(List.of(new UnixSocketClientProviderStrategy()));
		}
		
		this.container = new org.testcontainers.elasticsearch.ElasticsearchContainer(elasticsearchDockerImage);
		// XXX elasticsearch-default-memory-vm.options is a classpath resource in the testcontainers-elasticsearch jar since 7.17.4
		// loading it from the classpath won't work because testcontainers is not ready to handle bundleresource URLs specific to Eclipse OSGi 
		// remove the entry and replace it with ours
		this.container.getCopyToFileContainerPathMap().keySet().removeIf(file -> file.getFilesystemPath().startsWith("bundleresource://") && file.getFilesystemPath().contains("elasticsearch-default-memory-vm.options"));
		
		this.container
			.withCopyFileToContainer(MountableFile.forHostPath(toAbsolutePathBundleEntry(ElasticsearchContainer.class, "elasticsearch-default-memory-vm.options")), "/usr/share/elasticsearch/config/jvm.options.d/elasticsearch-default-memory-vm.options")
			.withEnv("rest.action.multi.allow_explicit_index", "false");
	
		// override the default wait strategy to wait for not just the started message but also for the security module to be initialized
		// fixes authentication issues when trying to connect to an Elasticsearch cluster that already accepts connections but is not yet ready for authentication
		WAIT_STRATEGIES.entrySet().stream()
			.filter(entry -> entry.getKey().test(this.elasticsearchDockerImageVersion))
			.findFirst()
			.map(Map.Entry::getValue)
			.ifPresent(this.container::setWaitStrategy);

		// all configuration done, start the container
		start();

		// use the default synonym file at startup to initialize the container with it
		overrideSearchSynonyms(List.of());
	}
	
	public Map<String, Object> getIndexClientConfiguration() {
		Preconditions.checkState(this.container != null, "Elasticsearch container is already stopped and closed.");
		Preconditions.checkState(this.container.isRunning(), "Elasticsearch container is not running.");
		
		final ImmutableMap.Builder<String, Object> indexClientConfiguration = ImmutableMap.builder();

		indexClientConfiguration.put(IndexClientFactory.CLUSTER_URL, this.clusterUrl);
		indexClientConfiguration.put(IndexClientFactory.CLUSTER_USERNAME, "elastic");
		indexClientConfiguration.put(IndexClientFactory.CLUSTER_PASSWORD, org.testcontainers.elasticsearch.ElasticsearchContainer.ELASTICSEARCH_DEFAULT_PASSWORD);
		
		if (sslContext != null) {
			indexClientConfiguration.put(IndexClientFactory.CLUSTER_SSL_CONTEXT, this.sslContext);
		}
		
		return indexClientConfiguration.build();
	}

	public void overrideSearchSynonyms(List<String> synonyms) throws Exception {
		Preconditions.checkArgument(synonyms != null, "'synonyms' may not be null.");
		Preconditions.checkState(this.container != null, "Elasticsearch container is already stopped and closed.");
		Preconditions.checkState(this.container.isRunning(), "Elasticsearch container is not running.");
		// make sure we transfer the new synonym text content into the synonym.txt file
		this.container.copyFileToContainer(Transferable.of(synonyms.stream().collect(Collectors.joining("\n"))), "/usr/share/elasticsearch/config/analysis/synonym.txt");
	}
	
	public void start() {
		Preconditions.checkState(this.container != null, "Elasticsearch container is already stopped and closed.");
		this.container.start();
		
		// XXX temporal coupling here between SSL Context generation and computation of the clusterUrl
		try {
			this.sslContext = this.container.createSslContextFromCa();
		} catch (Exception e) {
			if (e.getMessage().contains("CA cert under") && e.getMessage().contains("not found")) {
				// in certain older Elasticsearch images (7.x) certificate generation is not present, throwing an error that the file/folder/data is not found
				// ignore these errors and consider connecting via HTTP instead of HTTPS
			} else {
				// if not the known error to ignore, report it as usual
				throw e;
			}
 		}
		this.clusterUrl = computeClusterUrl(this.container);
		
		LOG.info("Started Elasticsearch test container at {}", this.clusterUrl);
	}
	
	public void stop() {
		Preconditions.checkState(this.container != null, "Elasticsearch container is already stopped and closed.");
		this.container.stop();
	}

	public void destroy() {
		if (this.container != null) {
			this.container.stop();
			this.container.close();
			this.container = null;
			LOG.info("Stopped Elasticsearch test container at {}", this.clusterUrl);
			this.sslContext = null;
			this.clusterUrl = null;
		}
	}
	
	static Path toAbsolutePathBundleEntry(Class<?> contextClass, String path) throws Exception {
		var bundle = checkNotNull(FrameworkUtil.getBundle(contextClass), "Bundle not found for %s", contextClass);
		var fileURL = new URL(FileLocator.toFileURL(bundle.getEntry(path)).toString().replaceAll(" ", "%20"));
		return Paths.get(fileURL.toURI());
	}
	
	static String computeClusterUrl(org.testcontainers.elasticsearch.ElasticsearchContainer container) {
		Objects.requireNonNull(container, "'container' may not be null");
		
		String protocol = container.caCertAsBytes().isPresent() ? "https://" : "http://";
		
		//
		// org.testcontainers.elasticsearch.ElasticsearchContainer.getHttpHostAddress() is not reliable in certain cases,
		// so we need additional steps to get the actual HTTP host address. The getHttpHostAddress() method returns the following:
		// 		getHost() + ":" + getMappedPort(ELASTICSEARCH_DEFAULT_PORT)
		//
		// org.testcontainers.containers.ContainerState.getHost() returns the following:
		// 		DockerClientFactory.instance().dockerHostIpAddress()
		// 
		// Depending on the runtime environment the IP address of the docker host could be different.
		// See a related thread here: https://github.com/testcontainers/testcontainers-java/issues/452
		//
		
		// Simple setup, OS + docker -> testcontainers running "one level above" the host. e.g. a dev-env
    	if (container.getHost().contains("localhost")) {
    		
    		// the returned http host address already includes the random mapped port created by testcontainers
    		return protocol + container.getHttpHostAddress(); 
    	
    	// Complex setup, OS + docker + docker -> testcontainers running "two or more level above" the host. e.g. a CI/CD env
    	} else if (container.getContainerInfo().getNetworkSettings().getNetworks().containsKey(DEFAULT_DOCKER_NETWORK_NAME)) {
    		
    		// The build agent and the Elasticsearch container must be on the same docker network (bridge is the default).
    		// We need the internal IP address of Elasticsearch within the 'bridge' docker network and the default ES HTTP port.
    		//
			//    	  Docker default bridge network
			//            (gateway: 172.17.0.1)
			//                       |
			//       ---------------------------------
			//       |                               |
			//  +---------------+         +-----------------------------+
			//  | build-agent   |         | testcontainers-elasticsearch|
			//  | 172.17.0.2    | ----->  | 172.17.0.3                  |
			//  |               | HTTP(S) |                             |
			//  +---------------+  :9200  +-----------------------------+
    		//
    			
			return String.format("%s%s:%s",
				protocol,
				// use the IP address of Elasticsearch from within the bridge network
				container.getContainerInfo().getNetworkSettings().getNetworks().get(DEFAULT_DOCKER_NETWORK_NAME).getIpAddress(),
				IndexClientFactory.DEFAULT_ES_HTTP_PORT
			);
			
    	} else {
    		throw new IllegalStateException("Unable to determine the correct HTTP address for Elasticsearch container. Container's getHttpHostAddress() returns: " + container.getHttpHostAddress());
    	}
    	
	}
	
}

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.eclipse.core.runtime.FileLocator;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.MountableFile;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

/**
 * Customized Elasticsearch container that can be used to run Snow Owl tests against. Each instance will boot up a separate Elasticsearch container so
 * memoize it in a ClassRule or singleton rule in order to use the same instance unless you need a specific Elasticsearch version and separate
 * container for a given test.
 * 
 * @since 10.0
 */
public final class ElasticsearchContainer {

	private static final Logger LOG = LoggerFactory.getLogger(ElasticsearchContainer.class);
	
	/**
	 * Java system property to configure the use of a testcontainer Elasticsearch Docker container and optionally configure the actual image as well. By default it uses the {@value #ES_DOCKER_IMAGE_VERSION} version.
	 */
	public static final String ES_USE_TEST_CONTAINER_VARIABLE = "so.index.elasticsearch.image";
	
	/**
	 * The default Elasticsearch image version to run tests against. 
	 */
	public static final String ES_DOCKER_IMAGE_VERSION = "8.19.10";
	
	/**
	 * The default Elasticsearch image to use when running tests.
	 */
	public static final String DEFAULT_ES_DOCKER_IMAGE = String.format("docker.elastic.co/elasticsearch/elasticsearch:%s", ES_DOCKER_IMAGE_VERSION);
	
	private org.testcontainers.elasticsearch.ElasticsearchContainer container;
	
	public ElasticsearchContainer() throws Exception {
		this(System.getProperty(ES_USE_TEST_CONTAINER_VARIABLE, DEFAULT_ES_DOCKER_IMAGE));
	}
	
	public ElasticsearchContainer(String elasticsearchDockerImage) throws Exception {
		Preconditions.checkArgument(!Strings.isNullOrEmpty(elasticsearchDockerImage), "'elasticsearchDockerImage' may not be null or empty.");
		this.container = new org.testcontainers.elasticsearch.ElasticsearchContainer(elasticsearchDockerImage);
		// XXX elasticsearch-default-memory-vm.options is a classpath resource in the testcontainers-elasticsearch jar since 7.17.4
		// loading it from the classpath won't work because testcontainers is not ready to handle bundleresource URLs specific to Eclipse OSGi 
		// remove the entry and replace it with ours
		this.container.getCopyToFileContainerPathMap().keySet().removeIf(file -> file.getFilesystemPath().startsWith("bundleresource://") && file.getFilesystemPath().contains("elasticsearch-default-memory-vm.options"));
		this.container
			.withCopyFileToContainer(MountableFile.forHostPath(toAbsolutePathBundleEntry(ElasticsearchContainer.class, "elasticsearch-default-memory-vm.options")), "/usr/share/elasticsearch/config/jvm.options.d/elasticsearch-default-memory-vm.options")
			.withEnv("rest.action.multi.allow_explicit_index", "false")
			// override the default wait strategy to wait for not just the started message but also for the security module to be initialized
			// fixes authentication issues when trying to connect to an Elasticsearch cluster that already accepts connections but is not yet ready for authentication
			.waitingFor(new LogMessageWaitStrategy().withRegEx(".*(\"message\":\\s?\"Security is enabled[\\s?|\"].*\n$)"));

		start();
		
		LOG.info("Started Elasticsearch test container at {}", container.getHttpHostAddress());

		// use the default synonym file at startup to initialize the container with it
		overrideSynonymFile(null);
	}
	
	public Map<String, Object> getIndexClientConfiguration() {
		Preconditions.checkState(this.container != null, "Elasticsearch container is already stopped and closed.");
		Preconditions.checkState(this.container.isRunning(), "Elasticsearch container is not running.");
		return Map.of(
			IndexClientFactory.CLUSTER_URL, "https://" + container.getHttpHostAddress(),
			IndexClientFactory.CLUSTER_USERNAME, "elastic",
			IndexClientFactory.CLUSTER_PASSWORD, org.testcontainers.elasticsearch.ElasticsearchContainer.ELASTICSEARCH_DEFAULT_PASSWORD,
			IndexClientFactory.CLUSTER_SSL_CONTEXT, container.createSslContextFromCa()
		);
	}

	public void overrideSynonymFile(Path synonymsFile) throws Exception {
		Preconditions.checkState(this.container != null, "Elasticsearch container is already stopped and closed.");
		Preconditions.checkState(this.container.isRunning(), "Elasticsearch container is not running.");
		// make sure we update the synonyms.txt inside the test container
		if (synonymsFile == null || !Files.exists(synonymsFile)) {
			synonymsFile = toAbsolutePathBundleEntry(ElasticsearchContainer.class, "synonym.txt");
		}
		this.container.copyFileToContainer(MountableFile.forHostPath(synonymsFile), "/usr/share/elasticsearch/config/analysis/synonym.txt");
	}
	
	public void start() {
		Preconditions.checkState(this.container != null, "Elasticsearch container is already stopped and closed.");
		this.container.start();
	}
	
	public void stop() {
		Preconditions.checkState(this.container != null, "Elasticsearch container is already stopped and closed.");
		this.container.stop();
	}

	public void destroy() {
		if (this.container != null) {
			final String address = container.getHttpHostAddress();
			this.container.stop();
			this.container.close();
			this.container = null;
			LOG.info("Stopped Elasticsearch test container at {}", address);
		}
	}
	
	static Path toAbsolutePathBundleEntry(Class<?> contextClass, String path) throws Exception {
		var bundle = checkNotNull(FrameworkUtil.getBundle(contextClass), "Bundle not found for %s", contextClass);
		var fileURL = new URL(FileLocator.toFileURL(bundle.getEntry(path)).toString().replaceAll(" ", "%20"));
		return Paths.get(fileURL.toURI());
	}

	
}

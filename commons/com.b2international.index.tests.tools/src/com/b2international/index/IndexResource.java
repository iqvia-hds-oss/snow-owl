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
package com.b2international.index;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.junit.Assume.assumeTrue;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.eclipse.core.runtime.FileLocator;
import org.junit.rules.ExternalResource;
import org.osgi.framework.FrameworkUtil;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.MountableFile;

import com.b2international.index.mapping.Mappings;
import com.b2international.index.revision.Commit;
import com.b2international.index.revision.DefaultRevisionIndex;
import com.b2international.index.revision.RevisionBranch;
import com.b2international.index.revision.TimestampProvider;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

/**
 * @since 7.1
 */
public final class IndexResource extends ExternalResource {

	/**
	 * Java system property to configure the use of a testcontainer Elasticsearch Docker container and optionally configure the actual image as well. By default it uses the 8.1.3 image.
	 */
	public static final String ES_USE_TEST_CONTAINER_VARIABLE = "so.index.es.useDocker";

	/**
	 * The default Elasticsearch image version to run tests against. 
	 */
	public static final String ES_DOCKER_IMAGE_VERSION = "8.19.10";
	
	/**
	 * The default Elasticsearch image to use when running tests.
	 */
	public static final String DEFAULT_ES_DOCKER_IMAGE = String.format("docker.elastic.co/elasticsearch/elasticsearch:%s", ES_DOCKER_IMAGE_VERSION);

	private static final AtomicBoolean INIT = new AtomicBoolean(false);
	
	private static final String DEFAULT_DOCKER_NETWORK = "bridge";
	private static final int DEFAULT_ES_HTTP_PORT = 9200;
	
	private static ObjectMapper mapper;
	private static Index index;
	private static IndexClient client;
	private static DefaultRevisionIndex revisionIndex;
	private static ElasticsearchContainer container;

	private final Collection<Class<?>> types;
	private final Consumer<ObjectMapper> objectMapperConfigurator;
	private final Supplier<Map<String, Object>> indexSettings;
	private final Supplier<String> supportedVersion;
	private final Supplier<Path> synonymsFile;
	
	private IndexResource(Collection<Class<?>> types, Consumer<ObjectMapper> objectMapperConfigurator, Supplier<Map<String, Object>> indexSettings, Supplier<String> supportedVersion, Supplier<Path> synonymsFile) {
		this.types = types;
		this.objectMapperConfigurator = objectMapperConfigurator;
		this.indexSettings = indexSettings;
		this.supportedVersion = supportedVersion;
		this.synonymsFile = synonymsFile;
	}
	
	@Override
	protected void before() throws Throwable {
		if (INIT.compareAndSet(false, true)) {
			final Map<String, Object> settings;
			
			// fire up an Elasticsearch test container if requested via useDocker system prop
			String testElasticsearchContainer = System.getProperty(ES_USE_TEST_CONTAINER_VARIABLE);
			if (testElasticsearchContainer != null) {
				if (testElasticsearchContainer.isEmpty()) {
					testElasticsearchContainer = DEFAULT_ES_DOCKER_IMAGE;
				}
				container = new ElasticsearchContainer(testElasticsearchContainer);
				// XXX elasticsearch-default-memory-vm.options is a classpath resource in the testcontainers:elasticsearch jar since 7.17.4
				// loading it from the classpath won't work because testcontainers is not ready to handle bundleresource URLs specific to Eclipse OSGi 
				// remove the entry and replace it with ours
				container.getCopyToFileContainerPathMap().keySet().removeIf(file -> file.getFilesystemPath().startsWith("bundleresource://") && file.getFilesystemPath().contains("elasticsearch-default-memory-vm.options"));
				container
					.withCopyFileToContainer(MountableFile.forHostPath(toAbsolutePathBundleEntry(IndexResource.class, "elasticsearch-default-memory-vm.options")), "/usr/share/elasticsearch/config/jvm.options.d/elasticsearch-default-memory-vm.options")
					.withEnv("rest.action.multi.allow_explicit_index", "false")
					.start();
				
				settings = Maps.newHashMap(this.indexSettings.get());
				settings.putIfAbsent(IndexClientFactory.CLUSTER_SSL_CONTEXT, container.createSslContextFromCa());
				settings.putIfAbsent(IndexClientFactory.CLUSTER_USERNAME, "elastic");
				settings.putIfAbsent(IndexClientFactory.CLUSTER_PASSWORD, ElasticsearchContainer.ELASTICSEARCH_DEFAULT_PASSWORD);
				settings.putIfAbsent(IndexClientFactory.CLUSTER_URL, getClusterUrl(container));
			} else {
				settings = this.indexSettings.get();
			}
			
			mapper = new ObjectMapper();
			mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
			client = Indexes.createIndexClient(UUID.randomUUID().toString(), mapper, new Mappings(), settings);
			index = new DefaultIndex(client);
			revisionIndex = new DefaultRevisionIndex(index, new TimestampProvider.Default(), mapper);
		}
		
		// when init is ready check version and ignore test if connected cluster is not supported
		assumeTrue(supportedVersion.get().equals("*") || index.admin().client().version().startsWith(supportedVersion.get()));
		
		if (container != null) {
			// make sure we update the synonyms.txt inside the test container
			Path synonymsFile = this.synonymsFile.get();
			if (synonymsFile == null) {
				synonymsFile = toAbsolutePathBundleEntry(IndexResource.class, "synonym.txt");
			}
			final MountableFile localSynonymFilePath = MountableFile.forHostPath(synonymsFile);
			final String containerSynonymFilePath = "/usr/share/elasticsearch/config/analysis/synonym.txt";
			container.copyFileToContainer(localSynonymFilePath, containerSynonymFilePath);
		}
		
		// apply mapper changes first
		objectMapperConfigurator.accept(mapper);
		
		// then mapping changes
		revisionIndex.admin().updateMappings(new Mappings(types));
		
		// then update settings changes for existing indices (TODO move this into create? or updateMappings?)
		revisionIndex.admin().updateSettings(indexSettings.get());
		
		// then make sure we have all indexes ready for tests
		revisionIndex.admin().create();
	}

	private static Path toAbsolutePathBundleEntry(Class<?> contextClass, String path) throws Exception {
		var bundle = checkNotNull(FrameworkUtil.getBundle(contextClass), "Bundle not found for %s", contextClass);
		var fileURL = new URL(FileLocator.toFileURL(bundle.getEntry(path)).toString().replaceAll(" ", "%20"));
		return Paths.get(fileURL.toURI());
	}
	
	@Override
	protected void after() {
		// make sure we clear each index after we've used them
		revisionIndex.admin().clear(ImmutableSet.<Class<?>>builder()
				.addAll(types)
				.add(RevisionBranch.class)
				.add(Commit.class)
				.build());
	}
	
	public ElasticsearchContainer getContainer() {
		return container;
	}
	
	public IndexClient getClient() {
		return client;
	}
	
	public Index getIndex() {
		return index;
	}
	
	public DefaultRevisionIndex getRevisionIndex() {
		return revisionIndex;
	}
	
	public ObjectMapper getMapper() {
		return mapper;
	}
	
	public static IndexResource create(Collection<Class<?>> types, Consumer<ObjectMapper> objectMapperConfigurator, Supplier<Map<String, Object>> indexSettings, Supplier<String> supportedVersion, Supplier<Path> synonymsFile) {
		return new IndexResource(types, objectMapperConfigurator, indexSettings, supportedVersion, synonymsFile);
	}
	
	public static String getClusterUrl(ElasticsearchContainer esContainer) {
		
		String protocol = esContainer.caCertAsBytes().isPresent() ? "https://" : "http://";
    	
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
		
		// Simple setup, OS + docker -> testcontainers running "one level above" the host. E.g. a dev-env
    	if (esContainer.getHost().contains("localhost")) {
    		
    		return protocol + esContainer.getHttpHostAddress() /* already includes the random mapped port created by testcontainers */; 
    	
    	// Complex setup, OS + docker + docker -> testcontainers running "two or more level above" the host. E.g. a CI/CD env
    	} else if (esContainer.getContainerInfo().getNetworkSettings().getNetworks().containsKey(DEFAULT_DOCKER_NETWORK)) {
    		
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
    			
			return String.format("%s%s:%d",
				protocol,
				// use the IP address of Elasticsearch from within the bridge network
				esContainer.getContainerInfo().getNetworkSettings().getNetworks().get(DEFAULT_DOCKER_NETWORK).getIpAddress(),
				DEFAULT_ES_HTTP_PORT
			);
			
    	}
    	
		throw new IllegalStateException("Unable to determine the correct HTTP address for Elasticsearch container. Container's getHttpHostAddress() returned: " + esContainer.getHttpHostAddress());
		
	}

}

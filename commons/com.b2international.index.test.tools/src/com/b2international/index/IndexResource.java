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

import static org.junit.Assume.assumeTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.junit.rules.ExternalResource;

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
 * Configures an index to be used for a set of tests. By default it boots up a testcontainer managed containerized Elasticsearch cluster. The cluster
 * version can be configured via -Dso.index.es.docker.version=8.19.10 sysprop. If the latter is not specified the hardcoded last tried version will be
 * used, which is {@value ElasticsearchContainer#ES_DOCKER_VERSION}.
 * 
 * @since 7.1
 */
public final class IndexResource extends ExternalResource {

	private static final AtomicBoolean INIT = new AtomicBoolean(false);
	
	private static ObjectMapper mapper;
	private static Index index;
	private static IndexClient client;
	private static DefaultRevisionIndex revisionIndex;
	private static ElasticsearchContainer container;

	private final Collection<Class<?>> types;
	private final Consumer<ObjectMapper> objectMapperConfigurator;
	private final Supplier<Map<String, Object>> indexSettings;
	private final Supplier<String> supportedVersion;
	private final Supplier<List<String>> synonymsToUse;
	
	private IndexResource(Collection<Class<?>> types, Consumer<ObjectMapper> objectMapperConfigurator, Supplier<Map<String, Object>> indexSettings, Supplier<String> supportedVersion, Supplier<List<String>> synonymsToUse) {
		this.types = types;
		this.objectMapperConfigurator = objectMapperConfigurator;
		this.indexSettings = indexSettings;
		this.supportedVersion = supportedVersion;
		this.synonymsToUse = synonymsToUse;
	}
	
	@Override
	protected void before() throws Throwable {
		if (INIT.compareAndSet(false, true)) {
			// boot up a shared global Elasticsearch test container
			container = new ElasticsearchContainer();
			
			final Map<String, Object> settings = Maps.newHashMap(this.indexSettings.get());
			container.getIndexClientConfiguration().forEach(settings::putIfAbsent);
			
			mapper = new ObjectMapper();
			mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
			client = Indexes.createIndexClient(UUID.randomUUID().toString(), mapper, new Mappings(), settings);
			index = new DefaultIndex(client);
			revisionIndex = new DefaultRevisionIndex(index, new TimestampProvider.Default(), mapper);
		}
		
		// when init is ready check version and ignore test if connected cluster is not supported
		assumeTrue(supportedVersion.get().equals("*") || index.admin().client().version().startsWith(supportedVersion.get()));
		
		if (container != null) {
			container.overrideSearchSynonyms(this.synonymsToUse.get());
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

	@Override
	protected void after() {
		// make sure we clear each index after we've used them
		revisionIndex.admin().clear(ImmutableSet.<Class<?>>builder()
				.addAll(types)
				.add(RevisionBranch.class)
				.add(Commit.class)
				.build());
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
	
	public static IndexResource create(Collection<Class<?>> types, Consumer<ObjectMapper> objectMapperConfigurator, Supplier<Map<String, Object>> indexSettings, Supplier<String> supportedVersion, Supplier<List<String>> synonymsToUse) {
		return new IndexResource(types, objectMapperConfigurator, indexSettings, supportedVersion, synonymsToUse);
	}

}

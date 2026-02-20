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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.Rule;
import org.junit.Test;

import com.b2international.index.mapping.Mappings;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @since 10.0.0.rc1
 */
public class Elasticsearch7ClusterConnectionTest {

	private static final String UNSUPPORTED_ELASTIC_VERSION = "7.17.29";
	
	@Rule
	public ElasticsearchContainerResource elasticsearch = new ElasticsearchContainerResource(UNSUPPORTED_ELASTIC_VERSION);
	
	@Test
	public void connectToES7() throws Exception {
		var mapper = new ObjectMapper();
		mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
		// try to initialize the index client, which should initialize an HTTP connection to retrieve the version info and fail if the cluster is an unsupported version
		assertThatThrownBy(() -> Indexes.createIndexClient(UUID.randomUUID().toString(), mapper, new Mappings(), elasticsearch.getContainer().getIndexClientConfiguration()))
			.isInstanceOf(IndexException.class)
			.hasMessageContaining("The connected Elasticsearch cluster is running a non-supported major version, '%s'.", UNSUPPORTED_ELASTIC_VERSION);
	}
	
}

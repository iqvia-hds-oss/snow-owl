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

import org.junit.rules.ExternalResource;

import com.google.common.base.Strings;

/**
 * Simple JUnit rule that starts/stops an Elasticsearch container for the duration of a single test.
 * Good for tests that require a specific version of Elasticsearch to be booted up.
 * 
 * @since 10.0
 */
public final class ElasticsearchContainerResource extends ExternalResource {

	private final String elasticsearchDockerImageVersion;
	
	private ElasticsearchContainer container;

	public ElasticsearchContainerResource() {
		this(null);
	}
	
	public ElasticsearchContainerResource(String elasticsearchDockerImageVersion) {
		this.elasticsearchDockerImageVersion = Strings.isNullOrEmpty(elasticsearchDockerImageVersion) ? ElasticsearchContainer.ES_DOCKER_VERSION : elasticsearchDockerImageVersion;
	}
	
	@Override
	protected void before() throws Throwable {
		this.container = new ElasticsearchContainer(elasticsearchDockerImageVersion);
	}

	@Override
	protected void after() {
		if (this.container != null) {
			this.container.destroy();
			this.container = null;
		}
	}

	public ElasticsearchContainer getContainer() {
		return container;
	}
	
}

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
package com.b2international.snowowl.fhir.core.request;

import java.util.List;

import org.hl7.fhir.r5.model.MetadataResource;

import com.b2international.snowowl.core.context.ResourceRepositoryRequestBuilder;
import com.b2international.snowowl.core.domain.RepositoryContext;
import com.b2international.snowowl.core.events.BaseRequestBuilder;

/**
 * @since 10.3
 */
public abstract class FhirResourceHistoryVersionGetRequestBuilder<R extends FhirResourceHistoryVersionGetRequest<T, ?, ?>, T extends MetadataResource>
	extends BaseRequestBuilder<FhirResourceHistoryVersionGetRequestBuilder<R, T>, RepositoryContext, T>
	implements ResourceRepositoryRequestBuilder<T> {

	private final String id;
	private final String version;
	
	private String summary;
	private List<String> elements;
	
	public FhirResourceHistoryVersionGetRequestBuilder(String id, String version) {
		this.id = id;
		this.version = version;
	}
	
	protected abstract R createRequest(String id, String version);
	
	@Override
	protected R doBuild() {
		R request = createRequest(id, version);
		request.setSummary(summary);
		request.setElements(elements);
		return request;
	}
	
	public final FhirResourceHistoryVersionGetRequestBuilder<R, T> setSummary(String summary) {
		this.summary = summary;
		return getSelf();
	}

	public final FhirResourceHistoryVersionGetRequestBuilder<R, T> setElements(List<String> elements) {
		this.elements = elements;
		return getSelf();
	}
}

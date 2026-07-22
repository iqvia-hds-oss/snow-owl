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

import org.hl7.fhir.r5.model.Bundle;
import org.hl7.fhir.r5.model.MetadataResource;

import com.b2international.commons.StringUtils;
import com.b2international.commons.exceptions.NotFoundException;
import com.b2international.snowowl.core.ResourceURI;
import com.b2international.snowowl.core.domain.RepositoryContext;
import com.b2international.snowowl.core.events.Request;
import com.b2international.snowowl.fhir.core.HistorySort;

/**
 * @since 10.3
 */
public abstract class FhirResourceHistoryVersionGetRequest<R extends MetadataResource, GB extends FhirResourceGetRequestBuilder<GB, ?, R>, HB extends FhirResourceHistoryGetRequestBuilder<HB>>
	implements Request<RepositoryContext, R> {

	private static final long serialVersionUID = 1L;
	
	private final String id;
	private final String version;
	
	private String summary;
	private List<String> elements;

	public FhirResourceHistoryVersionGetRequest(String id, String version) {
		this.id = id;
		this.version = version;
	}
	
	final void setSummary(String summary) {
		this.summary = summary;
	}
	
	final void setElements(List<String> elements) {
		this.elements = elements;
	}

	protected abstract GB prepareGet(String id);
	
	protected abstract HB prepareHistoryGet();
	
	@Override
	public R execute(final RepositoryContext context) {
		if (ResourceURI.HEAD.equals(version)) {
			// Fallback to regular GET to get HEAD version
			return prepareGet(id)
				.setSummary(summary)
				.setElements(elements)
				.buildAsync()
				.execute(context);
		} else {
			HB builder = prepareHistoryGet()
					.filterById(id);
	
			if (ResourceURI.LATEST.equals(version)) {
				builder = builder.sortHistoryBy(HistorySort.LAST_UPDATED_DESCENDING);
			} else {
				builder = builder.filterByVersion(version);
			}
			
			return builder
				.setSummary(summary)
				.setElements(elements)
				.one()
				.buildAsync()
				.execute(context)
				.getEntry()
				.stream()
				.findFirst()
				.map(Bundle.BundleEntryComponent::getResource)
				.map(resource -> (R) resource)
				.orElseThrow(() -> new NotFoundException(StringUtils.splitCamelCaseAndCapitalize(getReturnType().getSimpleName()), String.format("%s|%s", id, version)));
		}
	}
}

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
package com.b2international.snowowl.fhir.core.request.valueset;

import java.util.List;

import org.hl7.fhir.r5.model.ValueSet;

import com.b2international.snowowl.core.RepositoryManager;
import com.b2international.snowowl.core.ResourceFragment;
import com.b2international.snowowl.core.ResourceURI;
import com.b2international.snowowl.core.domain.RepositoryContext;
import com.b2international.snowowl.fhir.core.R5ObjectFields;
import com.b2international.snowowl.fhir.core.request.FhirResourceHistoryGetRequest;

/**
 * @since 10.3
 */
final class FhirValueSetHistoryGetRequest extends FhirResourceHistoryGetRequest<ValueSet> {

	private static final long serialVersionUID = 1L;
	
	public FhirValueSetHistoryGetRequest(String id) {
		super(ResourceURI.of("valuesets", id));
	}

	@Override
	protected ValueSet createResource() {
		return new ValueSet();
	}
	
	@Override
	protected void configureFieldsToLoad(List<String> fields) {
		// make sure we are not trying to load unindexed fields when requested
		fields.remove(R5ObjectFields.ValueSet.COMPOSE);
		fields.remove(R5ObjectFields.ValueSet.EXPANSION);
		fields.remove(R5ObjectFields.ValueSet.IMMUTABLE);
	}
	
	@Override
	protected void expandResourceSpecificFields(RepositoryContext context, ValueSet entry, ResourceFragment resource) {
		FhirValueSetResourceConverter converter = context.service(RepositoryManager.class)
				.get(resource.getToolingId())
				.optionalService(FhirValueSetResourceConverter.class)
				.orElse(FhirValueSetResourceConverter.DEFAULT);
		
		includeIfFieldSelected(R5ObjectFields.ValueSet.COMPOSE, () -> converter.expandCompose(context, resource.getResourceURI()), entry::setCompose);
	}
	
}

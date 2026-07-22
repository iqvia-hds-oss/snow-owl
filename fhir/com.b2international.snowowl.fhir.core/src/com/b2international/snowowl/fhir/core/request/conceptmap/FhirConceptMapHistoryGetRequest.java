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
package com.b2international.snowowl.fhir.core.request.conceptmap;

import java.util.List;

import org.hl7.fhir.r5.model.ConceptMap;

import com.b2international.snowowl.core.RepositoryManager;
import com.b2international.snowowl.core.ResourceFragment;
import com.b2international.snowowl.core.ResourceURI;
import com.b2international.snowowl.core.domain.RepositoryContext;
import com.b2international.snowowl.fhir.core.R5ObjectFields;
import com.b2international.snowowl.fhir.core.request.FhirResourceHistoryGetRequest;

/**
 * @since 10.3
 */
final class FhirConceptMapHistoryGetRequest extends FhirResourceHistoryGetRequest<ConceptMap> {

	private static final long serialVersionUID = 1L;
	
	FhirConceptMapHistoryGetRequest(String id) {
		super(ResourceURI.of("conceptmaps", id));
	}
	
	@Override
	protected ConceptMap createResource() {
		return new ConceptMap();
	}
	
	@Override
	protected void configureFieldsToLoad(List<String> fields) {
		fields.remove(R5ObjectFields.ConceptMap.GROUP);
	}
	
	@Override
	protected void expandResourceSpecificFields(RepositoryContext context, ConceptMap entry, ResourceFragment resource) {
		FhirConceptMapResourceConverter converter = context.service(RepositoryManager.class)
				.get(resource.getToolingId())
				.optionalService(FhirConceptMapResourceConverter.class)
				.orElse(FhirConceptMapResourceConverter.DEFAULT);
		
		/*
		 * TODO: Implement the following logic to expand "sourceScope" and "targetScope" fields if selected:
		 * 
		 * - Collect native resource URIs (with query) from the native resource's dependencies list with "source" and "target" scope
		 * - Convert native resource URIs into their corresponding FHIR URL representation using the rules below
		 *   - Code systems should be converted to an "all concepts" implicit VS URL
		 *   - ECL-constrained code systems should be converted into a constraint-based implicit VS URL
		 *   - Value set-based domains do not require modification  
		 */
		// includeIfFieldSelected(R5ObjectFields.ConceptMap.SOURCE_SCOPE, () -> expandScope(context, Dependency.find(resource.getDependencies, "source")), entry::setSourceScope);
		// includeIfFieldSelected(R5ObjectFields.ConceptMap.TARGET_SCOPE, () -> expandScope(context, Dependency.find(resource.getDependencies, "target")), entry::setTargetScope);
		includeIfFieldSelected(R5ObjectFields.ConceptMap.GROUP, () -> converter.expandMembers(context, resource.getResourceURI()), entry::setGroup);
	}
	
}

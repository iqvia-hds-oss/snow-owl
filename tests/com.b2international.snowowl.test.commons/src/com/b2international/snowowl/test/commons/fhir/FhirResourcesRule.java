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
package com.b2international.snowowl.test.commons.fhir;

import java.time.Instant;
import java.util.List;

import org.junit.rules.ExternalResource;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;

/**
 * @since 10.5.0
 */
public final class FhirResourcesRule extends ExternalResource {

	private boolean initialized = false;
	
	private final Multimap<String, String> fhirCodeSystemsToManage = ArrayListMultimap.create();
	private final Multimap<String, String> fhirValueSetsToManage = ArrayListMultimap.create();
	private final Multimap<String, String> fhirConceptMapsToManage = ArrayListMultimap.create();

	private final ListMultimap<String, String> insertionTimesByResource = ArrayListMultimap.create(); 
	
	public FhirResourcesRule() {
	}
	
	public FhirResourcesRule ensureCodeSystemPresent(String codeSystemId, String body) {
		Preconditions.checkArgument(!this.fhirCodeSystemsToManage.containsEntry(codeSystemId, body), "FHIR CodeSystem '%s' with the supplied body is already managed by this rule", codeSystemId);
		this.fhirCodeSystemsToManage.put(codeSystemId, body);
		if (initialized) {
			createCodeSystem(codeSystemId, body);
		}
		return this;
	}
	
	public FhirResourcesRule ensureValueSetPresent(String valueSetId, String body) {
		Preconditions.checkArgument(!this.fhirValueSetsToManage.containsEntry(valueSetId, body), "FHIR ValueSet '%s' with the supplied body is already managed by this rule", valueSetId);
		this.fhirValueSetsToManage.put(valueSetId, body);
		if (initialized) {
			createValueSet(valueSetId, body);
		}
		return this;
	}
	
	public FhirResourcesRule ensureConceptMapPresent(String conceptMapId, String body) {
		Preconditions.checkArgument(!this.fhirConceptMapsToManage.containsEntry(conceptMapId, body), "FHIR ConceptMap '%s' with the supplied body is already managed by this rule", conceptMapId);
		this.fhirConceptMapsToManage.put(conceptMapId, body);
		if (initialized) {
			createConceptMap(conceptMapId, body);
		}
		return this;
	}
	
	public List<String> getInsertionTimes(String resourceId) {
		return List.copyOf(this.insertionTimesByResource.get(resourceId));
	}
	
	@Override
	protected void before() throws Throwable {
		this.fhirCodeSystemsToManage.forEach(this::createCodeSystem);
		this.fhirValueSetsToManage.forEach(this::createValueSet);
		this.fhirConceptMapsToManage.forEach(this::createConceptMap);
		this.initialized = true;
	}
	
	@Override
	protected void after() {
		this.fhirConceptMapsToManage.keySet().forEach(FhirApiHelpers::deleteConceptMap);
		this.fhirValueSetsToManage.keySet().forEach(FhirApiHelpers::deleteValueSet);
		this.fhirCodeSystemsToManage.keySet().forEach(FhirApiHelpers::deleteCodeSystem);
		this.insertionTimesByResource.clear();
		this.initialized = false;
	}
	
	private void createCodeSystem(String codeSystemId, String body) {
		FhirApiHelpers.createCodeSystem(codeSystemId, body);
		this.insertionTimesByResource.put(codeSystemId, Instant.now().toString());
	}
	
	private void createValueSet(String valueSetId, String body) {
		FhirApiHelpers.createValueSet(valueSetId, body);
		this.insertionTimesByResource.put(valueSetId, Instant.now().toString());
	}

	private void createConceptMap(String conceptMapId, String body) {
		FhirApiHelpers.createConceptMap(conceptMapId, body);
		this.insertionTimesByResource.put(conceptMapId, Instant.now().toString());
	}
	
}

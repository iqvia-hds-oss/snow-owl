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
package com.b2international.snowowl.fhir.rest.tests.conceptmap;

import static com.b2international.snowowl.snomed.common.SnomedConstants.Concepts.FULLY_SPECIFIED_NAME;
import static com.b2international.snowowl.snomed.common.SnomedConstants.Concepts.IS_A;
import static com.b2international.snowowl.snomed.common.SnomedConstants.Concepts.SYNONYM;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.core.date.DateFormats;
import com.b2international.snowowl.core.date.EffectiveTimes;
import com.b2international.snowowl.fhir.rest.tests.FhirTestConcepts;
import com.b2international.snowowl.snomed.common.SnomedConstants;
import com.b2international.snowowl.snomed.common.SnomedConstants.Concepts;
import com.b2international.snowowl.snomed.common.SnomedRf2Headers;
import com.b2international.snowowl.snomed.core.domain.Acceptability;
import com.b2international.snowowl.snomed.core.domain.SnomedConcept;
import com.b2international.snowowl.snomed.core.domain.SnomedDescription;
import com.b2international.snowowl.snomed.core.domain.refset.SnomedRefSetType;
import com.b2international.snowowl.snomed.datastore.SnomedRefSetUtil;
import com.b2international.snowowl.snomed.datastore.request.SnomedDescriptionCreateRequestBuilder;
import com.b2international.snowowl.snomed.datastore.request.SnomedRelationshipCreateRequestBuilder;
import com.b2international.snowowl.snomed.datastore.request.SnomedRequests;
import com.b2international.snowowl.test.commons.Services;
import com.b2international.snowowl.test.commons.SnomedContentRule;
import com.b2international.snowowl.test.commons.codesystem.CodeSystemVersionRestRequests;
import com.b2international.snowowl.test.commons.rest.RestExtensions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

/**
 * @since 10.3
 */
public class FhirSnomedConceptMapGenerator {
	
	public static final String SIMPLE_MAP_TEST_REF_SET = "SIMPLE_MAP_TEST_REF_SET";
	public static final String SIMPLE_MAP_TO_TEST_REF_SET = "SIMPLE_MAP_TO_TEST_REF_SET";
	public static final String COMPLEX_MAP_TEST_REF_SET = "COMPLEX_MAP_TEST_REF_SET";
	public static final String VERSIONED_SIMPLE_MAP_TEST_REF_SET = "VERSIONED_SIMPLE_MAP_TEST_REF_SET";
	
	public static final String VERSION_2026_01_01 = "20260101";
	public static final String VERSION_2027_01_01 = "20270101";
	
	public static Map<String, String> createReferenceSets() {
		// Look for existing refset
		final String branchPath = IBranchPath.MAIN_BRANCH;
		
		Optional<SnomedDescription> refSetDescription = getRefSetConcept(branchPath, SIMPLE_MAP_TEST_REF_SET);
		
		if (!refSetDescription.isPresent()) {
			final String simpleRefSetId = createRefsetConcept(branchPath, SIMPLE_MAP_TEST_REF_SET, SnomedRefSetType.SIMPLE_MAP);
			createSimpleMapping(branchPath, simpleRefSetId, FhirTestConcepts.MICROORGANISM, "MO");
			
			// Create missing Simple map from SNOMED CT type reference set
			createSimpleToRefsetConcept(branchPath);
				
			final String simpleToRefSetId = createRefsetConcept(branchPath, SIMPLE_MAP_TO_TEST_REF_SET, SnomedRefSetType.SIMPLE_MAP_TO);
			createSimpleToMapping(branchPath, simpleToRefSetId, "MO", FhirTestConcepts.MICROORGANISM);
			
			final String complexRefSetId = createRefsetConcept(branchPath, COMPLEX_MAP_TEST_REF_SET, SnomedRefSetType.COMPLEX_MAP);
			createComplexMapping(branchPath, complexRefSetId, FhirTestConcepts.MICROORGANISM, "MO");
			
			final String versionedRefSetId = createRefsetConcept(branchPath, VERSIONED_SIMPLE_MAP_TEST_REF_SET, SnomedRefSetType.SIMPLE_MAP);
			createSimpleMapping(branchPath, versionedRefSetId, FhirTestConcepts.MICROORGANISM, "MO");
			
			
			CodeSystemVersionRestRequests.createVersion(SnomedContentRule.SNOMEDCT_ID, EffectiveTimes.parse(VERSION_2026_01_01, DateFormats.SHORT));
			
			// Create new version  
			createSimpleMapping(branchPath, versionedRefSetId, FhirTestConcepts.MICROORGANISM, "MO2");
			CodeSystemVersionRestRequests.createVersion(SnomedContentRule.SNOMEDCT_ID, EffectiveTimes.parse(VERSION_2027_01_01, DateFormats.SHORT));
			return Map.of(
					SIMPLE_MAP_TEST_REF_SET, simpleRefSetId,
					SIMPLE_MAP_TO_TEST_REF_SET, simpleToRefSetId,
					COMPLEX_MAP_TEST_REF_SET, complexRefSetId,
					VERSIONED_SIMPLE_MAP_TEST_REF_SET, versionedRefSetId
			);
		} else {			
			return Map.of(
					SIMPLE_MAP_TEST_REF_SET, getRefSetConcept(branchPath, SIMPLE_MAP_TEST_REF_SET).get().getConceptId(),
					SIMPLE_MAP_TO_TEST_REF_SET, getRefSetConcept(branchPath, SIMPLE_MAP_TO_TEST_REF_SET).get().getConceptId(),
					COMPLEX_MAP_TEST_REF_SET, getRefSetConcept(branchPath, COMPLEX_MAP_TEST_REF_SET).get().getConceptId(),
					VERSIONED_SIMPLE_MAP_TEST_REF_SET, getRefSetConcept(branchPath, VERSIONED_SIMPLE_MAP_TEST_REF_SET).get().getConceptId()
			);
		}
	}
	
	private static void createSimpleMapping(String branchPath, String refSetId, String source, String target) {
		Map<String, Object> properties = Maps.newHashMap();
		properties.put(SnomedRf2Headers.FIELD_MAP_TARGET, target);
		
		SnomedRequests.prepareNewMember()
			.setId(UUID.randomUUID().toString())
			.setModuleId(Concepts.MODULE_SCT_CORE)
			.setActive(true)
			.setRefsetId(refSetId)
			.setProperties(properties)
			.setReferencedComponentId(source)
			.build(branchPath, RestExtensions.USER, "FHIR Automated Test Refset Member")
			.execute(Services.bus())
			.getSync();
	}
	
	private static void createSimpleToMapping(String branchPath, String refSetId, String source, String target) {
		Map<String, Object> properties = Maps.newHashMap();
		properties.put(SnomedRf2Headers.FIELD_MAP_SOURCE, source);
		
		SnomedRequests.prepareNewMember()
			.setId(UUID.randomUUID().toString())
			.setModuleId(Concepts.MODULE_SCT_CORE)
			.setActive(true)
			.setRefsetId(refSetId)
			.setProperties(properties)
			.setReferencedComponentId(target)
			.build(branchPath, RestExtensions.USER, "FHIR Automated Test Refset Member")
			.execute(Services.bus())
			.getSync();
		
	}
	
	private static void createComplexMapping(String branchPath, String refSetId, String source, String target) {
		Map<String, Object> properties = Maps.newHashMap();
		properties.put(SnomedRf2Headers.FIELD_MAP_TARGET, target);
		properties.put(SnomedRf2Headers.FIELD_MAP_ADVICE, "If microorganism then use something else");
		properties.put(SnomedRf2Headers.FIELD_MAP_GROUP, 1);
		properties.put(SnomedRf2Headers.FIELD_MAP_PRIORITY, 1);
		properties.put(SnomedRf2Headers.FIELD_MAP_RULE, "OTHERWISE TRUE");
		properties.put(SnomedRf2Headers.FIELD_CORRELATION_ID, "447557004"); // exact
		
		SnomedRequests.prepareNewMember()
			.setId(UUID.randomUUID().toString())
			.setModuleId(Concepts.MODULE_SCT_CORE)
			.setActive(true)
			.setRefsetId(refSetId)
			.setProperties(properties)
			.setReferencedComponentId(source)
			.build(branchPath, RestExtensions.USER, "FHIR Automated Test Complex Map Type Refset Member")
			.execute(Services.bus())
			.getSync();
		
	}
	
	private static Optional<SnomedDescription> getRefSetConcept(String branchPath, String refSetName) {
		return SnomedRequests.prepareSearchDescription()
			.one()
			.filterByExactTerm(refSetName + " (foundation metadata concept)")
			.build(branchPath)
			.execute(Services.bus())
			.getSync()
			.first();
	}
	
	private static String createSimpleToRefsetConcept(String branchPath) {
		return SnomedRequests.prepareNewConcept()
			.setId(SnomedConstants.Concepts.REFSET_SIMPLE_MAP_TO_TYPE)
			.setActive(true)
			.setModuleId(Concepts.MODULE_SCT_CORE)
			.addDescription(createDescription("Simple map to SNOMED CT type reference set (foundation metadata concept)", FULLY_SPECIFIED_NAME))
			.addDescription(createDescription("Simple map to SNOMED CT type reference set", SYNONYM))
			.addRelationship(createIsaRelationship(Concepts.STATED_RELATIONSHIP, SnomedConstants.Concepts.REFSET_ROOT_CONCEPT))
			.addRelationship(createIsaRelationship(Concepts.INFERRED_RELATIONSHIP, SnomedConstants.Concepts.REFSET_ROOT_CONCEPT))
			.build(branchPath, RestExtensions.USER, "FHIR Automated Test Reference Set")
			.execute(Services.bus())
			.getSync()
			.getResultAs(String.class);
	}
	
	private static String createRefsetConcept(String branchPath, String refSetName, SnomedRefSetType refsetType) {
		return SnomedRequests.prepareNewConcept()
			.setIdFromNamespace(SnomedConstants.B2I_NAMESPACE)
			.setActive(true)
			.setModuleId(Concepts.MODULE_SCT_CORE)
			.addDescription(createDescription(refSetName + " (foundation metadata concept)", FULLY_SPECIFIED_NAME))
			.addDescription(createDescription(refSetName, SYNONYM))
			.addRelationship(createIsaRelationship(Concepts.STATED_RELATIONSHIP, SnomedRefSetUtil.getParentConceptId(refsetType)))
			.addRelationship(createIsaRelationship(Concepts.INFERRED_RELATIONSHIP, SnomedRefSetUtil.getParentConceptId(refsetType)))
			.setRefSet(SnomedRequests.prepareNewRefSet()
					.setReferencedComponentType(SnomedConcept.TYPE)
					.setMapTargetComponentType(SnomedConcept.TYPE)
					.setType(refsetType))
			.build(branchPath, RestExtensions.USER, "FHIR Automated Test Reference Set")
			.execute(Services.bus())
			.getSync()
			.getResultAs(String.class);
	}

	private static SnomedDescriptionCreateRequestBuilder createDescription(final String term, final String type) {
		return SnomedRequests.prepareNewDescription()
			.setIdFromNamespace(SnomedConstants.B2I_NAMESPACE)
			.setActive(true)
			.setModuleId(Concepts.MODULE_SCT_CORE)
			.setLanguageCode("en")
			.setTypeId(type)
			.setTerm(term)
			.setCaseSignificanceId(Concepts.ENTIRE_TERM_CASE_INSENSITIVE)
			.setAcceptability(ImmutableMap.of(SnomedConstants.Concepts.REFSET_LANGUAGE_TYPE_US, Acceptability.PREFERRED));
	}

	private static SnomedRelationshipCreateRequestBuilder createIsaRelationship(final String characteristicTypeId, String destinationId) {
		return SnomedRequests.prepareNewRelationship() 
			.setIdFromNamespace(SnomedConstants.B2I_NAMESPACE)
			.setActive(true)
			.setModuleId(Concepts.MODULE_SCT_CORE)
			.setDestinationId(destinationId)
			.setTypeId(IS_A)
			.setCharacteristicTypeId(characteristicTypeId)
			.setModifierId(Concepts.EXISTENTIAL_RESTRICTION_MODIFIER);
	}
	
}

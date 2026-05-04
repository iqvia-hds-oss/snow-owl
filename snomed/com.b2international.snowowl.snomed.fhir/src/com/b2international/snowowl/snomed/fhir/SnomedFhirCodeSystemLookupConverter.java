/*
 * Copyright 2021-2026 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.snomed.fhir;

import static com.google.common.collect.Lists.newArrayListWithCapacity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.hl7.fhir.r5.model.*;

import com.b2international.commons.StringUtils;
import com.b2international.fhir.r5.operations.CodeSystemLookupParameters;
import com.b2international.fhir.r5.operations.CodeSystemLookupResultParameters;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.date.DateFormats;
import com.b2international.snowowl.core.date.EffectiveTimes;
import com.b2international.snowowl.core.domain.Concept;
import com.b2international.snowowl.fhir.core.request.codesystem.FhirCodeSystemLookupConverter;
import com.b2international.snowowl.snomed.cis.SnomedIdentifiers;
import com.b2international.snowowl.snomed.common.SnomedConstants.Concepts;
import com.b2international.snowowl.snomed.common.SnomedTerminologyComponentConstants;
import com.b2international.snowowl.snomed.core.SnomedDisplayTermType;
import com.b2international.snowowl.snomed.core.domain.*;
import com.b2international.snowowl.snomed.datastore.request.SnomedRequests;

/**
 * @since 8.0
 */
public final class SnomedFhirCodeSystemLookupConverter implements FhirCodeSystemLookupConverter {
	
	private static final String SNOMED_SYSTEM_URL = "http://snomed.info/sct";

	@Override
	public String configureConceptExpand(CodeSystemLookupParameters parameters) {
		String expandDescriptions = parameters.isPropertyRequested(CodeSystemLookupParameters.PROPERTY_DESIGNATION) ? "descriptions(expand(type(expand(pt()))),sort:\"typeId,term\")" : null;
		String expandDescendants = parameters.isPropertyRequested(CodeSystemLookupParameters.PROPERTY_CHILD) ? "descendants(direct:true,expand(pt()))" : null;
		String expandAncestors = parameters.isPropertyRequested(CodeSystemLookupParameters.PROPERTY_PARENT) ? "ancestors(direct:true,expand(pt()))" : null;
		return StringUtils.COMMA_JOINER.join("pt()", expandDescriptions, expandDescendants, expandAncestors);
	}
	
	@Override
	public List<CodeSystemLookupResultParameters.Designation> expandDesignations(ServiceProvider context, CodeSystem codeSystem, Concept concept, CodeSystemLookupParameters parameters) {
		if (!parameters.isPropertyRequested(CodeSystemLookupParameters.PROPERTY_DESIGNATION)) {
			return null;
		}

		/*
		 * According to an example response, we no longer need to create separate "virtual descriptions" for each 
		 * language code <--> language reference set ID pair; we will consult the native descriptions directly instead.
		 */
		final SnomedConcept snomedConcept = concept.getInternalConceptAs();
		final SnomedDescriptions snomedDescriptions = snomedConcept.getDescriptions();
		final List<CodeSystemLookupResultParameters.Designation> designations = newArrayListWithCapacity(snomedDescriptions.getItems().size());

		for (final SnomedDescription snomedDescription : snomedDescriptions) {

			/* 
			 * Convert language reference set ID, acceptability and description type triples into "designation-use-context" extensions
			 * https://confluence.ihtsdotools.org/display/FHIR/Designation+extension
			 */
			final Map<String, Acceptability> acceptabilityMap = snomedDescription.getAcceptabilityMap();
			final List<Extension> designationExtensions = newArrayListWithCapacity(acceptabilityMap.size());
			final List<String> languageRefsetIds = acceptabilityMap.keySet()
				.stream()
				.sorted()
				.toList();

			// Extract information about the description type here because it is used both in "designation-use-context" and the converted designation
			final Coding typeCoding = new Coding()
				.setSystem(SNOMED_SYSTEM_URL)
				.setCode(snomedDescription.getTypeId())
				.setDisplay(SnomedDisplayTermType.PT.getLabel(snomedDescription.getType()));

			for (String languageRefsetId : languageRefsetIds) {

				// Extension "context" encodes the language reference set ID
				final Coding contextCoding = new Coding()
					// FIXME: "system" may need to be set to the code system's URL we are calling from
					.setSystem(SNOMED_SYSTEM_URL)
					.setCode(languageRefsetId);
				
				// Extension "role" encodes the acceptability ID for the language reference set
				final Acceptability acceptability = acceptabilityMap.get(languageRefsetId);
				
				final Coding roleCoding = new Coding()
					// FIXME: "system" may need to be set to the code system's URL we are calling from
					.setSystem(SNOMED_SYSTEM_URL)
					.setCode(acceptability.getConceptId())
					.setDisplay(acceptability.getLabel());
				
				final Extension useContextExtension = new Extension("http://snomed.info/fhir/StructureDefinition/designation-use-context");
				
				useContextExtension.addExtension()
					.setUrl("context")
					.setValue(contextCoding);

				useContextExtension.addExtension()
					.setUrl("role")
					.setValue(roleCoding);

				useContextExtension.addExtension()
					.setUrl("type")
					.setValue(typeCoding);
				
				designationExtensions.add(useContextExtension);
			}
			
			// Now convert the native SNOMED CT description into a FHIR designation
			var designation = new CodeSystemLookupResultParameters.Designation();
			
			designation
				.setValue(snomedDescription.getTerm())
				.setLanguage(snomedDescription.getLanguageCode())
				.setUse(typeCoding)
				.setExtension(designationExtensions);
			
			designations.add(designation);
		}
		
		return designations;
	}
	
	@Override
	public List<CodeSystemLookupResultParameters.Property> expandProperties(ServiceProvider context, CodeSystem codeSystem, Concept concept, CodeSystemLookupParameters parameters) {
		SnomedConcept snomedConcept = concept.getInternalConceptAs();
		
		List<CodeSystemLookupResultParameters.Property> properties = new ArrayList<>();
		
		// add basic SNOMED properties
		if (parameters.isPropertyRequested(SnomedFhirConstants.SNOMED_PROPERTY_INACTIVE.getCode())) {
			properties.add(new CodeSystemLookupResultParameters.Property()
					.setCode(SnomedFhirConstants.SNOMED_PROPERTY_INACTIVE.getCode())
					.setValue(new BooleanType(!snomedConcept.isActive())));
		}
		
		if (parameters.isPropertyRequested(SnomedFhirConstants.SNOMED_PROPERTY_MODULE_ID.getCode())) {
			properties.add(new CodeSystemLookupResultParameters.Property()
					.setCode(SnomedFhirConstants.SNOMED_PROPERTY_MODULE_ID.getCode())
					.setValue(new CodeType(snomedConcept.getModuleId())));
		}
		
		if (parameters.isPropertyRequested(SnomedFhirConstants.SNOMED_PROPERTY_SUFFICIENTLY_DEFINED.getCode())) {
			properties.add(new CodeSystemLookupResultParameters.Property()
					.setCode(SnomedFhirConstants.SNOMED_PROPERTY_SUFFICIENTLY_DEFINED.getCode())
					.setValue(new BooleanType(!snomedConcept.isPrimitive())));
		}
		
		if (parameters.isPropertyRequested(SnomedFhirConstants.SNOMED_PROPERTY_EFFECTIVE_TIME.getCode())) {
			properties.add(new CodeSystemLookupResultParameters.Property()
					.setCode(SnomedFhirConstants.SNOMED_PROPERTY_EFFECTIVE_TIME.getCode())
					// FHIR DateTimeType expects YYYY-mm-dd format (with a dash separator in positions 4 and 7) 
					.setValue(new DateTimeType(EffectiveTimes.format(snomedConcept.getEffectiveTime(), DateFormats.DEFAULT))));
		}
		
		// Optionally requested properties
		boolean requestedChild = parameters.isPropertyRequested(CodeSystemLookupParameters.PROPERTY_CHILD);
		boolean requestedParent = parameters.isPropertyRequested(CodeSystemLookupParameters.PROPERTY_PARENT);
		
			
		if (requestedChild && snomedConcept.getDescendants() != null) {
			for (SnomedConcept child : snomedConcept.getDescendants()) {
				properties.add(new CodeSystemLookupResultParameters.Property()
						.setCode(CodeSystemLookupParameters.PROPERTY_CHILD)
						.setValue(new CodeType(child.getId()))
						.setDescription(SnomedDisplayTermType.PT.getLabel(child))
				);
			}
		}
		
		if (requestedParent && snomedConcept.getAncestors() != null) {
			for (SnomedConcept parent : snomedConcept.getAncestors()) {
				properties.add(new CodeSystemLookupResultParameters.Property()
						.setCode(CodeSystemLookupParameters.PROPERTY_PARENT)
						.setValue(new CodeType(parent.getId()))
						.setDescription(SnomedDisplayTermType.PT.getLabel(parent))
				);
			}
		}
		
		// Relationship Properties
		Set<String> relationshipTypeIds = parameters.getPropertyValues().stream()
			.map(p -> {
				if (p.startsWith(SnomedTerminologyComponentConstants.SNOMED_URI_BASE)) {
					return p.substring(p.lastIndexOf('/') + 1, p.length()); // URI prefixed properties
				} else {
					return p; // use as is, so users won't need to define full URI for each SNOMED CT property, ID is enough
				}
			})
			.filter(SnomedIdentifiers::isConceptIdentifier) // only SNOMED CT Concept IDs
			.collect(Collectors.toSet());
		
		if (!relationshipTypeIds.isEmpty()) {
			// TODO use expand logic instead of custom fetch?
			SnomedRequests.prepareSearchRelationship()
				.all()
				.filterByActive(true)
				.filterByCharacteristicType(Concepts.INFERRED_RELATIONSHIP)
				.filterBySource(concept.getId())
				.filterByTypes(relationshipTypeIds)
				.build(codeSystem.getUrl())
				.getRequest()
				.execute(context)
				.forEach(r -> {
					CodeSystemLookupResultParameters.Property property = new CodeSystemLookupResultParameters.Property()
						.setCode(r.getTypeId());
					
					if (r.hasValue()) {
						RelationshipValue value = r.getValueAsObject();
						value.map(
							i -> property.setValue(new IntegerType(i)),
							d -> property.setValue(new DecimalType(d.doubleValue())),
							s -> property.setValue(new StringType(s)));
					} else {
						property.setValue(new CodeType(r.getDestinationId()));
						// TODO set description to destination concept PT?
					}
					
					
					properties.add(property);
				});
		}
		
		if (properties.isEmpty()) {
			return null;
		} else {
			return properties;
		}
	}
	
}

/*******************************************************************************
 * Copyright (c) 2026 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.snomed.fhir;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.elasticsearch.common.Strings;
import org.hl7.fhir.r5.model.Coding;
import org.hl7.fhir.r5.model.ConceptMap;
import org.hl7.fhir.r5.model.Enumerations.ConceptMapRelationship;

import com.b2international.commons.exceptions.BadRequestException;
import com.b2international.commons.http.ExtendedLocale;
import com.b2international.fhir.r5.operations.ConceptMapTranslateParameters;
import com.b2international.fhir.r5.operations.ConceptMapTranslateResultParameters;
import com.b2international.snowowl.core.ResourceFragment;
import com.b2international.snowowl.core.ResourceURI;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.request.SearchResourceRequest.Sort;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.b2international.snowowl.fhir.core.R5ObjectFields;
import com.b2international.snowowl.fhir.core.request.conceptmap.FhirConceptMapTranslator;
import com.b2international.snowowl.snomed.common.SnomedConstants.Concepts;
import com.b2international.snowowl.snomed.common.SnomedRf2Headers;
import com.b2international.snowowl.snomed.core.domain.SnomedConcept;
import com.b2international.snowowl.snomed.core.domain.refset.SnomedReferenceSetMember;
import com.b2international.snowowl.snomed.core.domain.refset.SnomedReferenceSetMembers;
import com.b2international.snowowl.snomed.core.domain.refset.SnomedReferenceSets;
import com.b2international.snowowl.snomed.datastore.index.entry.SnomedRefSetMemberIndexEntry;
import com.b2international.snowowl.snomed.datastore.request.SnomedRequests;

/**
 * @since 10.3
 */
public class SnomedFhirConceptMapTranslator implements FhirConceptMapTranslator {

	private static final Set<String> ASSOCIATION_REFSETS = Set.of(
		Concepts.REFSET_ALTERNATIVE_ASSOCIATION,
		Concepts.REFSET_MOVED_TO_ASSOCIATION,
		Concepts.REFSET_POSSIBLY_EQUIVALENT_TO_ASSOCIATION,
		Concepts.REFSET_REFERS_TO_ASSOCIATION,
		Concepts.REFSET_REPLACED_BY_ASSOCIATION,
		Concepts.REFSET_SAME_AS_ASSOCIATION,
		Concepts.REFSET_SIMILAR_TO_ASSOCIATION,
		Concepts.REFSET_WAS_A_ASSOCIATION
	);
	
	private static final Set<String> TARGET_HEADER_PROPERTIES = Set.of(SnomedRf2Headers.FIELD_TARGET_COMPONENT_ID, SnomedRf2Headers.FIELD_MAP_TARGET);
	
	@Override
	public ConceptMapTranslateResultParameters translate(final ServiceProvider context, final ConceptMap conceptMap, final ConceptMapTranslateParameters parameters) {
		final ConceptMapTranslateResultParameters translateResult = new ConceptMapTranslateResultParameters();

		// XXX since this is an implicit CM, and resource stored in the CM here is a CodeSystem referring to the proper SNOMED CT Edition
		final ResourceFragment resource = FhirModelHelpers.getResourceFragment(conceptMap);
		final ResourceURI implicitCodeSystemUri = resource.getResourceURI();
		final List<ExtendedLocale> locales = getLocale(conceptMap);
		
		// Extract reference set id
		final String refSetId = conceptMap.getUrl().substring(conceptMap.getUrl().indexOf("?fhir_cm=") + 9);

		final Coding sourceCoding = getSourceCoding(parameters);
		final Coding targetCoding = getTargetCoding(parameters);
		
		final boolean reverse;
		final String componentId;
		if (sourceCoding.getCode() != null) {
			reverse = false;
			componentId = sourceCoding.getCode();
		} else if (targetCoding.getCode() != null) {
			reverse = true;
			componentId = targetCoding.getCode();
		} else {
			throw new BadRequestException("Either 'sourceCode' or 'targetCode' must be provided");
		}
		
		// Source and Target should be the same
		final String implicitSystem = conceptMap.getGroupFirstRep().getSourceElement().baseUrl();
		final String implicitVersion = conceptMap.getGroupFirstRep().getSourceElement().version();
		
		final SnomedReferenceSets referenceSets = SnomedRequests.prepareSearchRefSet()
				.filterByActive(true)
				.filterById(refSetId)
				.setLimit(0)
				.build(implicitCodeSystemUri)
				.execute(context);
			
		if (referenceSets.getTotal() < 1) {
			throw new BadRequestException(String.format("Reference set could not be found: %s", conceptMap.getUrl()));
		}
		
		final List<ConceptMapTranslateResultParameters.Match> matches = SnomedRequests.prepareSearchMember()
			.filterByActive(true)
			.filterByRefSet(refSetId)
			.filterByComponentId(componentId)
			.setExpand("referencedComponent(expand(pt())),targetComponent(expand(pt()))")
			.setLocales(locales)
			.sortBy(Sort.fieldAsc(SnomedRefSetMemberIndexEntry.Fields.ID))
			.setLimit(context.getPageSize())
			.streamAsync(context, req -> req.build(implicitCodeSystemUri))
			.flatMap(SnomedReferenceSetMembers::stream)
			.filter(member -> keepMember(member, reverse, componentId))
			.map(member -> createMatch(member, reverse, conceptMap.getUrl(), implicitSystem, implicitVersion))
			.collect(Collectors.toList());
		
		if (!matches.isEmpty()) {
			return translateResult
				.setResult(true)
				.setMessage(String.format("%d member(s) from concept map: %s", matches.size(), conceptMap.getUrl()))
				.setMatch(matches);
		} else {
			return translateResult
				.setResult(false)
				.setMessage("No matches");
		}
	}
	
	@SuppressWarnings("unchecked")
	private static List<ExtendedLocale> getLocale(ConceptMap conceptMap) {
		return (List<ExtendedLocale>) conceptMap.getUserData(R5ObjectFields.ConceptMap.UserData.LOCALE);
	}
	
	private static boolean keepMember(SnomedReferenceSetMember member, boolean reverse, String componentId) {
		if (hasCorrelationId(member)) {
			if (Concepts.MAP_CORRELATION_NOT_MAPPABLE.equals(member.getPropertyValue(SnomedRf2Headers.FIELD_CORRELATION_ID))) {
				// NOT MAPPABLE should not be translatable
				return false;
			}
		}
		
		// Validate proper direction
		if (reverse) {
			return Objects.equals(getTargetCode(member), componentId);
		} else {
			return Objects.equals(getSourceCode(member), componentId);		
		}
	}
	
	private static ConceptMapTranslateResultParameters.Match createMatch(SnomedReferenceSetMember member, boolean reverse, String url, String system, String version) {
		final Coding conceptCoding = new Coding()
			.setCode(getCode(member, reverse))
			.setDisplay(getDisplay(member, reverse))
			.setSystem(system)
			.setVersion(version);

		return new ConceptMapTranslateResultParameters.Match()
			.setOriginMap(url)
			.setRelationship(getRelationship(member))
			.setConcept(conceptCoding);
	}
	
	private static boolean isAssociationMap(SnomedReferenceSetMember member) {
		return ASSOCIATION_REFSETS.contains(member.getRefsetId());
	}
	
	private static boolean isReverseMap(SnomedReferenceSetMember member) {
		return member.getProperties() != null && member.getProperties().containsKey(SnomedRf2Headers.FIELD_MAP_SOURCE);
	}
	
	private static boolean hasCorrelationId(SnomedReferenceSetMember member) {
		// Both Simple or Complex maps can have correlationId
		return member.getProperties() != null && member.getProperties().containsKey(SnomedRf2Headers.FIELD_CORRELATION_ID);
	}
	
	private static String getCode(SnomedReferenceSetMember member, boolean reverse) {
		if (reverse) {
			return getSourceCode(member);
		} else {
			return getTargetCode(member);
		}
	}
	
	private static String getSourceCode(SnomedReferenceSetMember member) {
		if (isReverseMap(member)) {
			return member.getPropertyValue(SnomedRf2Headers.FIELD_MAP_SOURCE);
		} else {
			return member.getReferencedComponentId();
		}
	}
	
	private static String getTargetCode(SnomedReferenceSetMember member) {
		if (isReverseMap(member)) {
			return member.getReferencedComponentId();
		} else {
			for (String field: TARGET_HEADER_PROPERTIES) {
				String target = member.getPropertyValue(field);
				if (!Strings.isNullOrEmpty(target)) {
					return target;
				}
			}
			throw new BadRequestException("Failed to find target code!");
		}
	}
	
	private static String getDisplay(SnomedReferenceSetMember member, boolean reverse) {
		if (reverse) {
			return getSourceDisplay(member);
		} else {
			return getTargetDisplay(member);
		}
	}
	
	private static String getSourceDisplay(SnomedReferenceSetMember member) {
		if (isReverseMap(member)) {
			// Cannot resolve source term
			return null;
		} else {
			return getTerm(((SnomedConcept) member.getReferencedComponent()));
		}
	}
	
	private static String getTargetDisplay(SnomedReferenceSetMember member) {
		if (isReverseMap(member)) {
			return getTerm(((SnomedConcept) member.getReferencedComponent()));
		} else if (isAssociationMap(member)) {
			return getTerm(((SnomedConcept) member.getPropertyValue(SnomedReferenceSetMember.Expand.TARGET_COMPONENT)));
		} else {
			// Cannot resolve target term
			return null;
		}
	}
	
	private static String getTerm(SnomedConcept concept) {
		if (concept.getPt() != null) {
			return concept.getPt().getTerm();
		} else {
			return null;
		}
	}
	
	private static String getRelationship(SnomedReferenceSetMember member) {
		// Check if it is an association refset
		if (isAssociationMap(member)) {
			switch (member.getRefsetId()) {
				case Concepts.REFSET_POSSIBLY_EQUIVALENT_TO_ASSOCIATION: return ConceptMapRelationship.RELATEDTO.toCode();
				case Concepts.REFSET_ALTERNATIVE_ASSOCIATION:            return ConceptMapRelationship.RELATEDTO.toCode();
				case Concepts.REFSET_REPLACED_BY_ASSOCIATION:            return ConceptMapRelationship.EQUIVALENT.toCode();
				case Concepts.REFSET_SAME_AS_ASSOCIATION:                return ConceptMapRelationship.EQUIVALENT.toCode();
				//XXX: what about other association types
				default:                                                 return ConceptMapRelationship.RELATEDTO.toCode();
			}
		} else if (hasCorrelationId(member)) {
			// It is a complex map try using the correlationId field
			final String correlationId = member.getPropertyValue(SnomedRf2Headers.FIELD_CORRELATION_ID);
			switch (correlationId) {
				case Concepts.MAP_CORRELATION_EXACT_MATCH:     return ConceptMapRelationship.EQUIVALENT.toCode();
				case Concepts.MAP_CORRELATION_BROAD_TO_NARROW: return ConceptMapRelationship.SOURCEISBROADERTHANTARGET.toCode();
				case Concepts.MAP_CORRELATION_NARROW_TO_BROAD: return ConceptMapRelationship.SOURCEISNARROWERTHANTARGET.toCode();
				case Concepts.MAP_CORRELATION_PARTIAL_OVERLAP: return ConceptMapRelationship.RELATEDTO.toCode();
				case Concepts.MAP_CORRELATION_NOT_MAPPABLE:    throw new IllegalArgumentException("Invalid value cannot be mapped: " + correlationId);  //This should have been filtered
				case Concepts.MAP_CORRELATION_NOT_SPECIFIED:   return ConceptMapRelationship.RELATEDTO.toCode();
				default:                                       return ConceptMapRelationship.RELATEDTO.toCode();
			}
		} else {
			return ConceptMapRelationship.RELATEDTO.toCode();
		}
	}
	
}

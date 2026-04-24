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
package com.b2international.snowowl.fhir.core.request.codesystem;

import static com.google.common.collect.Sets.newHashSet;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.Enumerations.CodeSystemContentMode;
import org.hl7.fhir.r5.model.Identifier;
import org.hl7.fhir.r5.model.Identifier.IdentifierUse;

import com.b2international.commons.StringUtils;
import com.b2international.index.query.Expression;
import com.b2international.index.query.Expressions;
import com.b2international.index.query.Expressions.ExpressionBuilder;
import com.b2international.snowowl.core.RepositoryManager;
import com.b2international.snowowl.core.ResourceURI;
import com.b2international.snowowl.core.domain.RepositoryContext;
import com.b2international.snowowl.core.internal.ResourceDocument;
import com.b2international.snowowl.core.version.VersionDocument;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.b2international.snowowl.fhir.core.R5ObjectFields;
import com.b2international.snowowl.fhir.core.request.FhirResourceSearchRequest;

/**
 * @since 8.0
 */
final class FhirCodeSystemSearchRequest extends FhirResourceSearchRequest<CodeSystem> {

	private static final long serialVersionUID = 1L;
	
	private static final Set<String> EXTERNAL_FHIR_CODESYSTEM_FIELDS = Set.of(
		R5ObjectFields.CodeSystem.COUNT,
		R5ObjectFields.CodeSystem.CASE_SENSITIVE,
		R5ObjectFields.CodeSystem.CONTENT,
		R5ObjectFields.CodeSystem.CONCEPT,
		R5ObjectFields.CodeSystem.FILTER,
		R5ObjectFields.CodeSystem.PROPERTY,
		R5ObjectFields.CodeSystem.IDENTIFIER
	);
	
	// Identifier system URI that indicates that the identifier value represents a URI
	private static final String SYSTEM_GLOBALLY_UNIQUE_URI = "urn:ietf:rfc:3986";
	
	// URI (URN) prefix for OIDs
	private static final String OID_PREFIX = "urn:oid:";
	
	@Override
	protected String getResourceType() {
		return com.b2international.snowowl.core.codesystem.CodeSystem.RESOURCE_TYPE;
	}
	
	@Override
	protected void addUrlFilter(final ExpressionBuilder query) {
		if (!containsKey(OptionKey.URL)) {
			return;
		}
		
		// Remove all user-supplied SNOMED CT URIs from the URL filter, we don't want the FHIR API to find them this way
		final Set<String> urls = newHashSet(getCollection(OptionKey.URL, String.class));
		final boolean hadBaseSnomedUri = urls.contains(FhirModelHelpers.SNOMED_BASE_URI_STRING);
		urls.removeIf(FhirModelHelpers::isSnomedUri);

		final Set<String> snomedUrls = getCollection(OptionKey.VERSION, String.class)
			.stream()
			.filter(FhirModelHelpers::isSnomedUri)
			.collect(Collectors.toSet());
		
		urls.addAll(snomedUrls);
		
		if (!urls.isEmpty()) {
			query.filter(ResourceDocument.Expressions.urls(urls));
		} else if (hadBaseSnomedUri) {
			/*
			 * TODO: When encountering the SNOMED CT base URL without a specific version filter, 
			 * add a filter that replaces it with the "definitive" SNOMED CT resource URL instead. 
			 * 
			 * This is currently the HEAD of the International Edition to match the behavior of
			 * FhirValueSetExpandRequest#expandImplicitValueSet(ServiceProvider, String) but is 
			 * subject to change in the future, see SO-6575.
			 * 
			 * The flag should be stored on the resource document but it is OK to have it 
			 * snapshotted when a version is created. Querying the "definitive" version should 
			 * be based on the creation date (not the effective date) of version documents, 
			 * this way we can ensure that the "definitive" version is always the version 
			 * that had the flag set the last time.
			 * 
			 * Some things for consideration:
			 * 
			 * - If the flag is not set on any version, we should return the most recent version
			 *   for the "SNOMEDCT" resource as fallback if it exists, with any version from the
			 *   "snomedct" tooling as a secondary fallback
			 *   
			 * - If the flag is set on multiple resources, the most recent "definitive" version 
			 *   will flip-flop between the resources each time a new version is created
			 *   
			 * - Users can't set a past version as "definitive", only LATEST versions
			 */			
			query.filter(ResourceDocument.Expressions.url(FhirModelHelpers.SNOMED_BASE_URI_STRING + "/900000000000207008"));
		} else {
			// An explicit URL filter was provided but all values got eliminated
			query.filter(Expressions.matchNone());
		}
	}
	
	@Override
	protected void addVersionFilter(final ExpressionBuilder query) {
		if (!containsKey(OptionKey.VERSION)) {
			return;
		}

		/*
		 * When we have both SNOMED CT URLs (moved from the version filter) and
		 * non-SNOMED version IDs, we must OR the two conditions instead of AND-ing
		 * them. Otherwise resources matching non-SNOMED versions would be incorrectly
		 * restricted to only those that also match the SNOMED CT URL filter.
		 * 
		 * Ideally this would be pushed down to a tooling-specific handler but at this
		 * point we don't know which tooling is being queried.
		 */
		final Map<Boolean, List<String>> versionsByKind = getCollection(OptionKey.VERSION, String.class)
			.stream()
			.collect(Collectors.partitioningBy(FhirModelHelpers::isSnomedUri));
		
		final List<String> snomedVersions = versionsByKind.get(true);
		final List<String> nonSnomedVersions = versionsByKind.get(false);

		final Expression urlExpression;
		if (!snomedVersions.isEmpty()) {
			urlExpression = ResourceDocument.Expressions.urls(snomedVersions);
		} else {
			urlExpression = null;
		}
		
		final Expression versionExpression;
		if (!nonSnomedVersions.isEmpty()) {
			versionExpression = VersionDocument.Expressions.versions(nonSnomedVersions);
		} else {
			versionExpression = null;
		}
		
		if (urlExpression != null && versionExpression != null) {
			query.filter(Expressions.bool()
				.should(urlExpression)
				.should(versionExpression)
				.build());
		} else if (urlExpression != null) {
			query.filter(urlExpression);
		} else if (versionExpression != null) {
			query.filter(versionExpression);
		} else {
			// An explicit version filter was provided but all values got eliminated
			query.filter(Expressions.matchNone());
		}
	}
	
	@Override
	protected Set<String> getExternalFhirResourceFields() {
		return EXTERNAL_FHIR_CODESYSTEM_FIELDS;
	}

	@Override
	protected CodeSystem createResource() {
		return new CodeSystem();
	}

	@Override
	protected void configureFieldsToLoad(List<String> fields) {
		super.configureFieldsToLoad(fields);

		// replace caseSensitive with internal settings field (stored within resource metadata)
		if (fields.contains(R5ObjectFields.CodeSystem.CASE_SENSITIVE)) {
			fields.remove(R5ObjectFields.CodeSystem.CASE_SENSITIVE);
			fields.add(ResourceDocument.Fields.SETTINGS);
		}
	}
  
	private Identifier getIdentifier(final String oid) {
		if (StringUtils.isEmpty(oid)) {
			return null;
		}
		
		return new Identifier()
			.setUse(IdentifierUse.OFFICIAL)
			.setSystem(SYSTEM_GLOBALLY_UNIQUE_URI)
			.setValue(OID_PREFIX + oid);
	}

	@Override
	protected void expandResourceSpecificFields(final RepositoryContext context, final CodeSystem entry, final ResourceFragment resource) {
		final ResourceURI resourceURI = resource.getResourceURI();
		
		// addIdentifier() is a no-op if the input is null so we can safely call it here
		includeIfFieldSelected(R5ObjectFields.CodeSystem.IDENTIFIER, () -> getIdentifier(resource.getOid()), entry::addIdentifier);
		includeIfFieldSelected(R5ObjectFields.CodeSystem.CASE_SENSITIVE, () -> (Boolean) resource.getSettings().getOrDefault(R5ObjectFields.CodeSystem.CASE_SENSITIVE, true), entry::setCaseSensitive);
    
		// The rest of the field inclusions is specific to code system tooling, we need to obtain the appropriate converter for this purpose
		final FhirCodeSystemResourceConverter converter = context.service(RepositoryManager.class)
			.get(resource.getToolingId())
			.optionalService(FhirCodeSystemResourceConverter.class)
			.orElse(FhirCodeSystemResourceConverter.DEFAULT);
		
		if (fields().isEmpty() || fields().contains(R5ObjectFields.CodeSystem.CONTENT)) {

			/*
			 * XXX: When "content" is requested "count" will also be populated as we need
			 * this information to determine content mode in the first place. Servers are allowed
			 * to return more information than requested according to the specification.
			 */
			final int count = converter.count(context, resourceURI);
			entry.setCount(count);

			/*
			 * TODO: if concept expansion becomes limited in length, set "example" as the
			 * content mode if the total concept count exceeds the theoretical maximum.
			 * Currently for LCS code systems all concepts are returned, while other
			 * toolings do not return concepts at all.
			 */
			if (count == 0) {
				entry.setContent(CodeSystemContentMode.NOTPRESENT);
			} else {
				entry.setContent(CodeSystemContentMode.COMPLETE);
			}
			
		} else {
			includeIfFieldSelected(R5ObjectFields.CodeSystem.COUNT, () -> converter.count(context, resourceURI), entry::setCount);
		}
		
		includeIfFieldSelected(R5ObjectFields.CodeSystem.CONCEPT, () -> converter.expandConcepts(context, resourceURI, locales()), entry::setConcept);
		includeIfFieldSelected(R5ObjectFields.CodeSystem.FILTER, () -> converter.expandFilters(context, resourceURI, locales()), entry::setFilter);
		includeIfFieldSelected(R5ObjectFields.CodeSystem.PROPERTY, () -> converter.expandProperties(context, resourceURI, locales()), entry::setProperty);
		includeIfFieldSelected(R5ObjectFields.CodeSystem.VALUE_SET, () -> converter.computeValueSet(entry), entry::setValueSet);
		
		/*
		 * XXX: Override URL and version logic for SNOMED CT code systems: FHIR URL is a
		 * fixed value and FHIR version is the URL we are storing in the native resource
		 * representation.
		 * 
		 * This could be pushed to the converter implementation but it is better to keep
		 * manipulation of the fields together with the filtering logic above.
		 */
		if (FhirModelHelpers.isSnomedUri(resource.getUrl())) {
			includeIfFieldSelected(R5ObjectFields.CodeSystem.URL, () -> FhirModelHelpers.SNOMED_BASE_URI_STRING, entry::setUrl);
			includeIfFieldSelected(R5ObjectFields.CodeSystem.VERSION, resource::getUrl, entry::setVersion);
		}
	}
}

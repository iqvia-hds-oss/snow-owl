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
package com.b2international.snowowl.fhir.core.request.codesystem;

import java.util.List;
import java.util.Set;

import org.hl7.fhir.r5.model.CodeSystem;
import org.hl7.fhir.r5.model.Enumerations.CodeSystemContentMode;
import org.hl7.fhir.r5.model.Identifier;
import org.hl7.fhir.r5.model.Identifier.IdentifierUse;

import com.b2international.commons.StringUtils;
import com.b2international.snowowl.core.RepositoryManager;
import com.b2international.snowowl.core.ResourceFragment;
import com.b2international.snowowl.core.ResourceURI;
import com.b2international.snowowl.core.domain.RepositoryContext;
import com.b2international.snowowl.core.version.VersionDocument;
import com.b2international.snowowl.fhir.core.R5ObjectFields;
import com.b2international.snowowl.fhir.core.request.FhirResourceHistoryGetRequest;

/**
 * @since 10.3
 */
final class FhirCodeSystemHistoryGetRequest extends FhirResourceHistoryGetRequest<CodeSystem> {

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
	
	FhirCodeSystemHistoryGetRequest(String id) {
		super(com.b2international.snowowl.core.codesystem.CodeSystem.uri(id));
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
			fields.add(VersionDocument.Fields.SETTINGS);
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
			final int count = converter.count(context, resource.getToolingId(), resourceURI);
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
			includeIfFieldSelected(R5ObjectFields.CodeSystem.COUNT, () -> converter.count(context, resource.getToolingId(), resourceURI), entry::setCount);
		}
		
		includeIfFieldSelected(R5ObjectFields.CodeSystem.CONCEPT, () -> converter.expandConcepts(context, resourceURI, locales()), entry::setConcept);
		includeIfFieldSelected(R5ObjectFields.CodeSystem.FILTER, () -> converter.expandFilters(context, resourceURI, locales()), entry::setFilter);
		includeIfFieldSelected(R5ObjectFields.CodeSystem.PROPERTY, () -> converter.expandProperties(context, resourceURI, locales()), entry::setProperty);
		includeIfFieldSelected(R5ObjectFields.CodeSystem.VALUE_SET, () -> converter.computeValueSet(entry), entry::setValueSet);
	}
}

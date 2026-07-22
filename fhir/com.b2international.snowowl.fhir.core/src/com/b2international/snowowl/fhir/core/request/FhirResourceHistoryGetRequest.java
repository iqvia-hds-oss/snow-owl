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

import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.hl7.fhir.r5.model.*;
import org.hl7.fhir.r5.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r5.model.Bundle.BundleType;
import org.hl7.fhir.r5.model.ContactPoint.ContactPointSystem;
import org.hl7.fhir.r5.model.Enumerations.PublicationStatus;
import org.hl7.fhir.r5.model.Narrative.NarrativeStatus;
import org.hl7.fhir.utilities.xhtml.NodeType;
import org.hl7.fhir.utilities.xhtml.XhtmlNode;

import com.b2international.commons.CompareUtils;
import com.b2international.commons.StringUtils;
import com.b2international.commons.exceptions.NotFoundException;
import com.b2international.fhir.FhirCodeSystems;
import com.b2international.index.Hits;
import com.b2international.index.query.Expressions;
import com.b2international.index.query.Expressions.ExpressionBuilder;
import com.b2international.index.query.Query;
import com.b2international.index.revision.RevisionSearcher;
import com.b2international.snowowl.core.ResourceFragment;
import com.b2international.snowowl.core.ResourceURI;
import com.b2international.snowowl.core.TerminologyResource;
import com.b2international.snowowl.core.domain.RepositoryContext;
import com.b2international.snowowl.core.id.IDs;
import com.b2international.snowowl.core.internal.ResourceDocument;
import com.b2international.snowowl.core.request.SearchResourceRequest;
import com.b2international.snowowl.core.version.VersionDocument;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.b2international.snowowl.fhir.core.R5ObjectFields;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.Iterables;

import net.jodah.typetools.TypeResolver;

/**
 * @since 10.3
 */
public abstract class FhirResourceHistoryGetRequest<T extends MetadataResource> extends SearchResourceRequest<RepositoryContext, Bundle> {

	private static final long serialVersionUID = 1L;

	private static final Set<String> EXTERNAL_FHIR_RESOURCE_FIELDS = Set.of(
		R5ObjectFields.MetadataResource.NAME,
		R5ObjectFields.MetadataResource.META,
		R5ObjectFields.MetadataResource.TEXT,
		R5ObjectFields.MetadataResource.EFFECTIVE_PERIOD
	);
	
	/**
	 * @since 10.3
	 */
	public enum OptionKey {
		VERSION,
		SINCE,
		AT,
	}
	
	protected abstract String getResourceType();
	
	private Bundle prepareBundle() {
		final Meta meta = new Meta();
		meta.setLastUpdatedElement(FhirModelHelpers.toInstantElement(new Date()));

		if (!CompareUtils.isEmpty(fields())) {
			meta.addTag(FhirCodeSystems.CODING_SUBSETTED);
		}

		final Bundle bundle = new Bundle(BundleType.HISTORY);
		bundle.setId(IDs.base62UUID());
		bundle.setMeta(meta);
		
		return bundle;
	}

	@Override
	protected final Bundle createEmptyResult(final int limit) {
		return prepareBundle().setTotal(0);
	}
	
	private void addVersionFilter(final ExpressionBuilder query) {
		if (!containsKey(OptionKey.VERSION)) {
			return;
		}
		
		final Set<String> uniqueVersions = new HashSet<>(getCollection(OptionKey.VERSION, String.class));
		if (uniqueVersions.isEmpty()) {
			// An explicit version filter was provided but no values were given, impossible to match anything
			throw new NoResultException();
		}
		
		query.filter(VersionDocument.Expressions.versions(uniqueVersions));
	}
	
	private void addResourceFilter(final ExpressionBuilder query) {
		if (componentIds() == null) {
			return;
		}
		Set<String> resources = componentIds().stream()
			.map(componentId -> ResourceURI.of(getResourceType(), componentId).toString())
			.collect(Collectors.toSet());
		query.filter(VersionDocument.Expressions.resources(resources));
	}
	
	private void addSinceFilter(final ExpressionBuilder query) {
		if (!containsKey(OptionKey.SINCE)) {
			return;
		}					
		query.filter(VersionDocument.Expressions.createdAt(get(OptionKey.SINCE, Long.class), Long.MAX_VALUE));
	}
	
	private void addAtFilter(final ExpressionBuilder query) {
		if (!containsKey(OptionKey.AT)) {
			return;
		}
		// XXX: this is rather a BEFORE, as it will return all revisions before AT not just that was "valid" at that time.
		query.filter(VersionDocument.Expressions.createdAt(0L, get(OptionKey.AT, Long.class)));
	}

	/**
	 * Subclasses may override this method to specify additional FHIR resource
	 * fields that should be removed from the field selection and not loaded from
	 * the underlying index. The default implementation returns an empty set.
	 * 
	 * @return
	 */
	protected Set<String> getExternalFhirResourceFields() {
		// Known shared fields are listed in the EXTERNAL_FHIR_RESOURCE_FIELDS constant
		return Set.of();
	}

	/**
	 * Subclasses may override this method to handle additional cases of field
	 * mapping between the external FHIR and the internal Snow Owl model. The
	 * default implementation does nothing.
	 * 
	 * @param fields
	 */
	protected void configureFieldsToLoad(final List<String> fields) {
		// Empty by default, the fields shared across all resource types are handled above
	}

	private List<String> replaceFieldsToLoad(final List<String> fields) {
		final List<String> internalFields = new ArrayList<>(fields);
		
		// If any fields were listed for field selection, make sure the ones we usually need are still included
		if (!internalFields.isEmpty()) {
			
			if (!internalFields.contains(VersionDocument.Fields.ID)) {
				internalFields.add(VersionDocument.Fields.ID);
			}
			
			if (!internalFields.contains(VersionDocument.Fields.RESOURCE_TYPE)) {
				internalFields.add(VersionDocument.Fields.RESOURCE_TYPE);
			}
			
			if (!internalFields.contains(VersionDocument.Fields.TOOLING_ID)) {
				internalFields.add(VersionDocument.Fields.TOOLING_ID);
			}
			
			if (!internalFields.contains(VersionDocument.Fields.BRANCH_PATH)) {
				internalFields.add(VersionDocument.Fields.BRANCH_PATH);
			}
			
			if (!internalFields.contains(VersionDocument.Fields.CREATED_AT)) {
				internalFields.add(VersionDocument.Fields.CREATED_AT);
			}
			
			if (!internalFields.contains(VersionDocument.Fields.UPDATED_AT)) {
				internalFields.add(VersionDocument.Fields.UPDATED_AT);
			}
			
			if (!internalFields.contains(VersionDocument.Fields.DEPENDENCIES)) {
				internalFields.add(VersionDocument.Fields.DEPENDENCIES);
			}
			
			// version doc only fields go here, anything that is shared go above using resource field constants
			if (!internalFields.contains(VersionDocument.Fields.VERSION)) {
				internalFields.add(VersionDocument.Fields.VERSION);
			}
		}
		
		// Remove all fields that are not part of the current resource model
		internalFields.removeAll(EXTERNAL_FHIR_RESOURCE_FIELDS);
		internalFields.removeAll(getExternalFhirResourceFields());
		
		// Replace publisher with internal settings field (publisher is stored within resource metadata)
		if (internalFields.remove(R5ObjectFields.MetadataResource.PUBLISHER)) {
			internalFields.add(VersionDocument.Fields.SETTINGS);
		}

		// Replace identifier with internal OID field
		if (internalFields.remove(R5ObjectFields.CodeSystem.IDENTIFIER)) {
			internalFields.add(VersionDocument.Fields.OID);
		}
		
		// Replace date with internal effectiveTime field
		if (internalFields.remove(R5ObjectFields.MetadataResource.DATE)) {
			internalFields.add(VersionDocument.Fields.EFFECTIVE_TIME);
			// TODO
			internalFields.add(VersionDocument.Fields.CREATED);
			internalFields.add(VersionDocument.Fields.REVISED);
		}
		
		// If the URL field is requested, retrieve settings as well
		if (internalFields.remove(R5ObjectFields.CodeSystem.URL)) {
			internalFields.add(VersionDocument.Fields.URL);
			// It would be better to add the FHIR URL override field specifically but this requires a schema change
			internalFields.add(VersionDocument.Fields.SETTINGS);
		}
		
		// If the version field is requested, retrieve settings and URL as well
		if (internalFields.remove(R5ObjectFields.CodeSystem.VERSION)) {
			internalFields.add(VersionDocument.Fields.URL);
			// It would be better to add the FHIR version property field specifically, as in the above case
			internalFields.add(VersionDocument.Fields.SETTINGS);
			internalFields.add(VersionDocument.Fields.VERSION);
		}
		
		// Consult search request subclasses for any additional field mapping
		configureFieldsToLoad(internalFields);
		
		return internalFields;
	}

	@JsonIgnore
	@SuppressWarnings("unchecked")
	private Class<T> getResourceTypeClass() {
		final Class<?>[] types = TypeResolver.resolveRawArguments(FhirResourceHistoryGetRequest.class, getClass());
		checkState(TypeResolver.Unknown.class != types[0], "Couldn't resolve return type parameter for class %s", getClass().getSimpleName());
		return (Class<T>) types[0];
	}
	
	protected abstract T createResource();

	private PublicationStatus toPublicationStatus(final String status) {
		try {
			return PublicationStatus.fromCode(status);
		} catch (final Exception e) {
			// ignore any errors coming from status detection and treat it as unknown status
			return PublicationStatus.UNKNOWN;
		}
	}

	private Meta toMeta(final Long updatedAt, final Long createdAt) {
		final Meta meta = new Meta();

		// updatedAt returns version creation time (createdAt and updatedAt is the same) or latest updateAt value from the resource :gold:
		if (updatedAt != null) {
			meta.setLastUpdatedElement(FhirModelHelpers.toInstantElement(updatedAt));
		} else if (createdAt != null) {
			meta.setLastUpdatedElement(FhirModelHelpers.toInstantElement(createdAt));
		}
		
		return meta;
	}

	protected final <C> void includeIfFieldSelected(final String field, final Supplier<C> getter, final Consumer<C> setter) {
		if (CompareUtils.isEmpty(fields()) || fields().contains(field)) {
			setter.accept(getter.get());
		}
	}

	private Narrative getBlankNarrative() {
		// We are not generating any narrative text for the resources so return an empty <div> element
		final XhtmlNode div = new XhtmlNode(NodeType.Element, "div");
		return new Narrative(NarrativeStatus.EMPTY, div);
	}

	private String getPublisher(final Map<String, Object> settings) {
		if (settings == null) {
			return "";
		} else {
			return (String) settings.getOrDefault(R5ObjectFields.CodeSystem.PUBLISHER, "");
		}
	}
	
	private DateTimeType toDateTimeType(final Long effectiveTime) {
		return FhirModelHelpers.toDateTimeElement(effectiveTime);
	}

	private ContactDetail toContactDetail(final String contact) {
		if (StringUtils.isEmpty(contact)) {
			return null;
		}
		
		final ContactDetail contactDetail = new ContactDetail();
		
		contactDetail.addTelecom()
			.setSystem(ContactPointSystem.URL)
			.setValue(contact);
			
		return contactDetail;
	}
	
	private Period toEffectivePeriod(final Long start) {
		if (start == null) {
			return null;
		}

		final Period period = new Period();
		final DateTimeType startElement = FhirModelHelpers.toDateTimeElement(start);
		
		period.setStartElement(startElement);
		
		return period;
	}

	private String getUrl(final ResourceFragment fragment) {
		if (fragment.getSettings() == null) {
			return fragment.getUrl();
		}
		
		final String fhirUrlOverride = (String) fragment.getSettings().get(TerminologyResource.Settings.FHIR_URL);
		if (StringUtils.isEmpty(fhirUrlOverride)) {
			return fragment.getUrl();
		}
		
		// If a FHIR URL override is set in the resource metadata, use that as the URL
		return fhirUrlOverride;
	}
	
	private String getVersion(final ResourceFragment fragment) {
		if (fragment.getSettings() == null) {
			return fragment.getVersion();
		}
		
		final String fhirVersionProperty = (String) fragment.getSettings().get(TerminologyResource.Settings.FHIR_VERSION_PROPERTY);
		if (!VersionDocument.Fields.URL.equals(fhirVersionProperty)) {
			return fragment.getVersion();
		}
		
		// If a FHIR version property override indicates that the URL should be used as the version, do so
		return fragment.getUrl();
	}

	/**
	 * Subclasses may override this method to set additional properties on the
	 * resulting FHIR resource based on the provided ResourceFragment and field
	 * selection settings.
	 * <p>
	 * The default implementation does nothing, as the base class already handles
	 * the most common fields across all resource types.
	 * 
	 * @param context
	 * @param entry
	 * @param resource
	 */
	protected void expandResourceSpecificFields(final RepositoryContext context, final T entry, final ResourceFragment resource) {
		// Empty method body
	}

	private T toFhirResource(final RepositoryContext context, final ResourceFragment resource) {
		final T entry = createResource();
		
		// XXX: We still allow retrieving a resource by versioned identifier, but the ID used in the FHIR resource will always be the resource ID
		entry.setId(resource.extractResourceId());
		entry.setStatus(toPublicationStatus(resource.getStatus()));
		entry.setMeta(toMeta(resource.getUpdatedAt(), resource.getCreatedAt()));
		
		// store the entire resource fragment to reuse any loaded data in subsequent requests when needed
		// see FhirModelHelpers for easy accessors for specific fields, such as tooling ID and "native" resource URI
		entry.setUserData(R5ObjectFields.MetadataResource.UserData.INTERNAL_RESOURCE, resource);
		
		// We are using the raw ID of the resource as machine readable name
		includeIfFieldSelected(R5ObjectFields.MetadataResource.NAME, resource::getId, entry::setName);
		// We have a description field available both for the resource and the version, here the one for the resource is needed
		includeIfFieldSelected(R5ObjectFields.MetadataResource.DESCRIPTION, resource::getResourceDescription, entry::setDescription);
		includeIfFieldSelected(R5ObjectFields.MetadataResource.TITLE, resource::getTitle, entry::setTitle);
		includeIfFieldSelected(R5ObjectFields.Resource.LANGUAGE, resource::getLanguage, entry::setLanguage);
		includeIfFieldSelected(R5ObjectFields.MetadataResource.PURPOSE, resource::getPurpose, entry::setPurpose);
		includeIfFieldSelected(R5ObjectFields.MetadataResource.COPYRIGHT, resource::getCopyright, entry::setCopyright);
		
		// Currently support setting start with the effectiveTime of the resource
		includeIfFieldSelected(R5ObjectFields.MetadataResource.EFFECTIVE_PERIOD, () -> toEffectivePeriod(resource.getEffectiveTime()), entry::setEffectivePeriod);
		
		includeIfFieldSelected(R5ObjectFields.DomainResource.TEXT, () -> getBlankNarrative(), entry::setText);
		includeIfFieldSelected(R5ObjectFields.MetadataResource.PUBLISHER, () -> getPublisher(resource.getSettings()), entry::setPublisher);
		includeIfFieldSelected(R5ObjectFields.MetadataResource.DATE, () -> toDateTimeType(resource.getEffectiveTime()), entry::setDateElement);
		// addContact(contact) is a no-op if contact is null so we can safely call it here
		includeIfFieldSelected(R5ObjectFields.MetadataResource.CONTACT, () -> toContactDetail(resource.getContact()), entry::addContact);

		includeIfFieldSelected(R5ObjectFields.CodeSystem.URL, () -> getUrl(resource), entry::setUrl);
		includeIfFieldSelected(R5ObjectFields.CodeSystem.VERSION, () -> getVersion(resource), entry::setVersion);
		
		expandResourceSpecificFields(context, entry, resource);

		return entry;
	}

	private Bundle.BundleEntryComponent toFhirResourceEntry(final RepositoryContext context, final ResourceFragment fragment) {
		return new Bundle.BundleEntryComponent().setResource(toFhirResource(context, fragment));
	}

	@Override
	protected final Bundle doExecute(final RepositoryContext context) throws IOException {
		// Match version documents with the given resource identifier
		final ExpressionBuilder query = Expressions.bool()
			.filter(VersionDocument.Expressions.resourceType(getResourceType()));
		
		addResourceFilter(query);
		addVersionFilter(query);
		addSinceFilter(query);
		addAtFilter(query);
		
		// Map requested fields to their internal counterpart; remove FHIR-specific fields that do not map to a document field 
		final List<String> internalFields = replaceFieldsToLoad(fields());

		// Execute the search query across both resource and version documents, mapping them to a common ResourceFragment representation
		final Hits<ResourceFragment> internalResources = context.service(RevisionSearcher.class)
			.search(Query.select(ResourceFragment.class)
				.from(VersionDocument.class)
				.fields(internalFields)
				.where(query.build())
				.searchAfter(searchAfter())
				.limit(limit())
				.minScore(minScore())
				.sortBy(querySortBy(context))
				.build());
			
		if (internalResources.isEmpty() && componentIds() != null && componentIds().size() == 1) {
			String componentId = Iterables.getOnlyElement(componentIds());
			// No versions were found, check if we have just filtered the versions or the ID does not even exist.
			final Hits<ResourceFragment> existingResources = context.service(RevisionSearcher.class)
				.search(Query.select(ResourceFragment.class)
				.from(ResourceDocument.class)
				.where(ResourceDocument.Expressions.id(componentId))
				.limit(0)
				.build());
			if (existingResources.getTotal() == 0) {
				 throw new NotFoundException(StringUtils.splitCamelCaseAndCapitalize(getResourceTypeClass().getSimpleName()), componentId);
			 }	
		}
		
		final List<BundleEntryComponent> entries = internalResources.stream()
			.map(fragment -> toFhirResourceEntry(context, fragment))
			.collect(Collectors.toList());
		
		final Bundle searchResults = prepareBundle() 
			.setEntry(entries)
			.setTotal(internalResources.getTotal());
		
		if (internalResources.getSearchAfter() != null) {
			searchResults.setUserData("searchAfter", internalResources.getSearchAfter());
		}

		return searchResults;
	}

}

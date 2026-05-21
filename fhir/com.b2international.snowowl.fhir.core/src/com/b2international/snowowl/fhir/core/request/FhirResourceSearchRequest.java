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
package com.b2international.snowowl.fhir.core.request;

import static com.google.common.collect.Sets.newHashSet;

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
import com.b2international.fhir.FhirCodeSystems;
import com.b2international.index.Hits;
import com.b2international.index.query.Expression;
import com.b2international.index.query.Expressions;
import com.b2international.index.query.Expressions.ExpressionBuilder;
import com.b2international.index.query.Query;
import com.b2international.index.revision.RevisionSearcher;
import com.b2international.snowowl.core.ResourceFragment;
import com.b2international.snowowl.core.domain.RepositoryContext;
import com.b2international.snowowl.core.id.IDs;
import com.b2international.snowowl.core.internal.ResourceDocument;
import com.b2international.snowowl.core.request.SearchResourceRequest;
import com.b2international.snowowl.core.request.search.TermFilter;
import com.b2international.snowowl.core.version.VersionDocument;
import com.b2international.snowowl.fhir.core.FhirModelHelpers;
import com.b2international.snowowl.fhir.core.R5ObjectFields;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Retrieves FHIR terminology resources (CodeSystem, ValueSet, ConceptMap) based
 * on the provided search criteria and field selection, performing field mapping
 * and transformation between the native (Snow Owl) and the external (FHIR) model.
 * 
 * @since 8.0
 */
public abstract class FhirResourceSearchRequest<T extends MetadataResource> extends SearchResourceRequest<RepositoryContext, Bundle> {

	private static final long serialVersionUID = 1L;

	private static final Set<String> EXTERNAL_FHIR_RESOURCE_FIELDS = Set.of(
		R5ObjectFields.MetadataResource.NAME,
		R5ObjectFields.MetadataResource.META,
		R5ObjectFields.MetadataResource.TEXT,
		R5ObjectFields.MetadataResource.EFFECTIVE_PERIOD
	);
	
	/**
	 * @since 8.0
	 */
	public enum OptionKey {
		URL,
		NAME,
		TITLE, 
		CONTENT,
		VERSION,
		STATUS,
		
		LAST_UPDATED, 
	}
	
	private Bundle prepareBundle() {
		final Meta meta = new Meta();
		meta.setLastUpdatedElement(FhirModelHelpers.toInstantElement(new Date()));

		if (!CompareUtils.isEmpty(fields())) {
			meta.addTag(FhirCodeSystems.CODING_SUBSETTED);
		}

		final Bundle bundle = new Bundle(BundleType.SEARCHSET);
		bundle.setId(IDs.base62UUID());
		bundle.setMeta(meta);
		
		return bundle;
	}

	@Override
	protected final Bundle createEmptyResult(final int limit) {
		return prepareBundle().setTotal(0);
	}
	
	/**
	 * @return the Snow Owl resource type representation to search for the
	 * appropriate documents in the underlying index
	 */
	protected abstract String getResourceType();

	private void addFhirIdFilter(final ExpressionBuilder query) {
		// Both documents have an "id" and a "url" field; we will use the expression from ResourceDocument purely by choice
		addIdFilter(query, ids -> Expressions.bool()
			.should(ResourceDocument.Expressions.ids(ids))
			.should(ResourceDocument.Expressions.urls(ids))
			.build());
	}

	private void addUrlFilter(final ExpressionBuilder query) {
		if (!containsKey(OptionKey.URL)) {
			return;
		}
		
		final Set<String> urls = newHashSet(getCollection(OptionKey.URL, String.class));
		
		/*
		 * Remove all user-supplied SNOMED CT URIs and versioned FHIR URLs from the URL
		 * values. From the FHIR API's perspective these resources have different URLs
		 * so they should not be returned when a user requests them under the versioned alias.  
		 */
		urls.removeIf(FhirModelHelpers::isEditionSnomedUri);
		urls.removeIf(FhirModelHelpers::isRegularVersionedUri);
		
		// Also remove the base SNOMED CT URI temporarily as it needs special handling
		final boolean hadBaseSnomedUri = urls.remove(FhirModelHelpers.SNOMED_BASE_URI_STRING);

		Expression urlSnomedExpression = null;
		if (hadBaseSnomedUri) {
			// Convert this fact into a filter that matches any "qualified" version of the SNOMED CT URI (module and/or effective time appended)
			urlSnomedExpression = ResourceDocument.Expressions.urlPrefix(FhirModelHelpers.SNOMED_BASE_URI_STRING + "/");
		}
		
		Expression urlRegularExpression = null;
		if (!urls.isEmpty()) {
			// Each non-versioned URL should also match any versioned URL with the same base
			final Set<String> urlVersionPrefixes = urls.stream()
				.map(FhirModelHelpers::addRegularVersionSuffix)
				.collect(Collectors.toSet());
			
			urlRegularExpression = Expressions.bool()
					.should(ResourceDocument.Expressions.urls(urls))
					.should(ResourceDocument.Expressions.urlPrefixes(urlVersionPrefixes))
					.build();
		}
		
		if (urlSnomedExpression != null && urlRegularExpression != null) {
			// Both SNOMED and regular URL filters are present, combine them with OR
			query.filter(Expressions.bool()
				.should(urlSnomedExpression)
				.should(urlRegularExpression)
				.build());
		} else if (urlSnomedExpression != null) {
			query.filter(urlSnomedExpression);
		} else if (urlRegularExpression != null) {
			query.filter(urlRegularExpression);
		} else {
			// An explicit URL filter was provided but all values got eliminated, impossible to match anything
			throw new NoResultException();
		}
	}
		
	private void addVersionFilter(final ExpressionBuilder query) {
		if (!containsKey(OptionKey.VERSION)) {
			return;
		}
		
		// Separate SNOMED CT version IDs (which are actually URLs) from regular version IDs
		final Map<Boolean, List<String>> versionsByKind = getCollection(OptionKey.VERSION, String.class)
				.stream()
				.collect(Collectors.partitioningBy(FhirModelHelpers::isEditionSnomedUri));
		
		final List<String> snomedVersions = versionsByKind.get(true);
		final List<String> nonSnomedVersions = versionsByKind.get(false);

		
		Expression versionSnomedExpression = null;
		if (!snomedVersions.isEmpty()) {
			// SNOMED CT version IDs are actually URLs, so they should be matched against the URL field
			versionSnomedExpression = ResourceDocument.Expressions.urls(snomedVersions);
		}
			
		Expression versionRegularExpression = null;
		if (!nonSnomedVersions.isEmpty()) {
			// Regular version IDs should be matched against the version field
			versionRegularExpression = VersionDocument.Expressions.versions(nonSnomedVersions);
		}

		if (versionSnomedExpression != null && versionRegularExpression != null) {
			// Both SNOMED and regular version filters are present, combine them with OR
			query.filter(Expressions.bool()
				.should(versionSnomedExpression)
				.should(versionRegularExpression)
				.build());
		} else if (versionSnomedExpression != null) {
			query.filter(versionSnomedExpression);
		} else if (versionRegularExpression != null) {
			query.filter(versionRegularExpression);
		} else {
			// An explicit version filter was provided but all values got eliminated, impossible to match anything
			throw new NoResultException();
		}
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
		final List<String> internalFields = Lists.newArrayList(fields);
		
		// If any fields were listed for field selection, make sure the ones we usually need are still included
		if (!internalFields.isEmpty()) {
			
			if (!internalFields.contains(ResourceDocument.Fields.ID)) {
				internalFields.add(ResourceDocument.Fields.ID);
			}
			
			if (!internalFields.contains(ResourceDocument.Fields.RESOURCE_TYPE)) {
				internalFields.add(ResourceDocument.Fields.RESOURCE_TYPE);
			}
			
			if (!internalFields.contains(ResourceDocument.Fields.TOOLING_ID)) {
				internalFields.add(ResourceDocument.Fields.TOOLING_ID);
			}
			
			if (!internalFields.contains(ResourceDocument.Fields.BRANCH_PATH)) {
				internalFields.add(ResourceDocument.Fields.BRANCH_PATH);
			}
			
			if (!internalFields.contains(ResourceDocument.Fields.CREATED_AT)) {
				internalFields.add(ResourceDocument.Fields.CREATED_AT);
			}
			
			if (!internalFields.contains(ResourceDocument.Fields.UPDATED_AT)) {
				internalFields.add(ResourceDocument.Fields.UPDATED_AT);
			}
			
			if (!internalFields.contains(ResourceDocument.Fields.DEPENDENCIES)) {
				internalFields.add(ResourceDocument.Fields.DEPENDENCIES);
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
			internalFields.add(ResourceDocument.Fields.SETTINGS);
		}

		// Replace identifier with internal OID field
		if (internalFields.remove(R5ObjectFields.CodeSystem.IDENTIFIER)) {
			internalFields.add(ResourceDocument.Fields.OID);
		}
		
		// Replace date with internal effectiveTime field
		if (internalFields.remove(R5ObjectFields.MetadataResource.DATE)) {
			internalFields.add(VersionDocument.Fields.EFFECTIVE_TIME);
		}
		
		// Consult search request subclasses for any additional field mapping
		configureFieldsToLoad(internalFields);
		
		return internalFields;
	}

	private void fillResourceOnlyProperties(
		final RepositoryContext context, 
		final Hits<ResourceFragment> internalResources, 
		final List<String> fields
	) throws IOException {
		
		for (final ResourceFragment fragment : internalResources) {
			
			if (CompareUtils.isEmpty(fragment.getVersion())) {
				// This fragment was created from a resource document, set resourceDescription and continue
				fragment.setResourceDescription(fragment.getDescription());
				continue;
			}
			
			if (!CompareUtils.isEmpty(fragment.getStatus())) {
				// This fragment was created from a version with snapshot, nothing to do
				// (the required information is baked into version documents since 8.7.0)
				continue;
			}
			
			// Retrieve resource representation with the same "created" branch timestamp (we defer to the low-level Searcher for this) 
			final Hits<ResourceFragment> resourceFragments = context.service(RevisionSearcher.class)
				.searcher()
				.search(Query.select(ResourceFragment.class)
				.from(ResourceDocument.class)
				.fields(fields)
				.where(Expressions.bool()
					.filter(ResourceDocument.Expressions.id(fragment.getResourceURI().getResourceId()))
					.filter(ResourceDocument.Expressions.validAsOf(fragment.getCreatedAt()))
					.build())
				.limit(1)
				.build());
			
			final ResourceFragment resourceSnapshot = Iterables.getFirst(resourceFragments, null);
			if (resourceSnapshot != null) {
				fragment.setTitle(resourceSnapshot.getTitle());
				fragment.setStatus(resourceSnapshot.getStatus());
				fragment.setContact(resourceSnapshot.getContact());
				fragment.setCopyright(resourceSnapshot.getCopyright());
				fragment.setLanguage(resourceSnapshot.getLanguage());
				fragment.setPurpose(resourceSnapshot.getPurpose());
				fragment.setOid(resourceSnapshot.getOid());
				fragment.setSettings(resourceSnapshot.getSettings());
				fragment.setResourceDescription(resourceSnapshot.getDescription());
			}
		}
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
		final DateTimeType startElement = new DateTimeType();
		
		startElement.setValue(new Date(start));
		startElement.setTimeZone(TimeZone.getTimeZone("UTC"));
		
		period.setStartElement(startElement);
		
		return period;
	}

	private String getUrl(final String url) {
		if (FhirModelHelpers.isEditionSnomedUri(url)) {
			return FhirModelHelpers.SNOMED_BASE_URI_STRING;
		} else if (FhirModelHelpers.isRegularVersionedUri(url)) {
			return FhirModelHelpers.getRegularUrlBase(url);
		} else {
			return url;
		}
	}
	
	private String getVersion(final String url, final String version) {
		if (FhirModelHelpers.isEditionSnomedUri(url)) {
			return url;
		} else {
			return version;
		}
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
		
		entry.setId(resource.getId());
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

		includeIfFieldSelected(R5ObjectFields.CodeSystem.URL, () -> getUrl(resource.getUrl()), entry::setUrl);
		includeIfFieldSelected(R5ObjectFields.CodeSystem.VERSION, () -> getVersion(resource.getUrl(), resource.getVersion()), entry::setVersion);
		
		expandResourceSpecificFields(context, entry, resource);

		return entry;
	}

	private Bundle.BundleEntryComponent toFhirResourceEntry(final RepositoryContext context, final ResourceFragment fragment) {
		return new Bundle.BundleEntryComponent().setResource(toFhirResource(context, fragment));
	}

	@Override
	protected final Bundle doExecute(final RepositoryContext context) throws IOException {
		
		// Match resource and version documents with the given resource type
		final ExpressionBuilder query = Expressions.bool()
			.filter(ResourceDocument.Expressions.resourceType(getResourceType())); 
		
		// TODO: Support filtering by the FHIR resource ID which needs to be transformed to the internal ID first
		addFhirIdFilter(query);
		addUrlFilter(query);
		addVersionFilter(query);
		
		/*
		 * The "name" property can be used to query the native resource ID. Values do
		 * not always line up with the regexp restrictions in the FHIR specification for
		 * "name" but failing that test should only result in a validation warning.
		 */
		addFilter(query, OptionKey.NAME, String.class, ResourceDocument.Expressions::ids);
		
		// Smart search for titles
		if (containsKey(OptionKey.TITLE)) {
			final String titleFilterValue = getString(OptionKey.TITLE);
			
			// Both ResourceDocument and VersionDocument have a "title" field; we will use the constant from ResourceDocument
			final Expression titleMatchExpression = TermFilter.match()
				.term(titleFilterValue)
				.build()
				.toExpression(ResourceDocument.Fields.TITLE);
			
			if (containsKey(OptionKey.NAME)) {
				
				// The name filter already locks the search to a specific resource, so we can apply the title filter as a simple matcher
				query.must(titleMatchExpression);
				
			} else {
				
				// Without a name filter, the title filter will also try to match native IDs with a high boost
				final Expression idMatchExpression = Expressions.matchAny(ResourceDocument.Fields.ID, 
					List.of(titleFilterValue, titleFilterValue.toUpperCase()));
				
				query.must(Expressions.bool()
					.should(titleMatchExpression)
					.should(Expressions.boost(idMatchExpression, 100.0f))
					.build());
			}
		}

		// The rest of the filters (currently only "status") are applied without special handling
		addFilter(query, OptionKey.STATUS, String.class, ResourceDocument.Expressions::statuses);

		// Map requested fields to their internal counterpart; remove FHIR-specific fields that do not map to a document field 
		final List<String> internalFields = replaceFieldsToLoad(fields());

		// Execute the search query across both resource and version documents, mapping them to a common ResourceFragment representation
		final Hits<ResourceFragment> internalResources = context.service(RevisionSearcher.class)
			.search(Query.select(ResourceFragment.class)
			.from(ResourceDocument.class, VersionDocument.class)
			.fields(internalFields)
			.where(query.build())
			.searchAfter(searchAfter())
			.limit(limit())
			.minScore(minScore())
			.sortBy(querySortBy(context))
			.build());
		
		// Populate properties that only exist on the resource document (either copied to version documents in newer Snow Owl releases or from the state  
		fillResourceOnlyProperties(context, internalResources, internalFields);
			
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

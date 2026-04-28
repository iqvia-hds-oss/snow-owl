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

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.b2international.index.revision.RevisionBranchPoint;
import com.b2international.index.revision.RevisionSearcher;
import com.b2international.snowowl.core.ResourceURI;
import com.b2international.snowowl.core.TerminologyResource;
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
		R5ObjectFields.MetadataResource.TEXT
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

	private final void addFhirIdFilter(final RepositoryContext context, final ExpressionBuilder query) {
		addIdFilter(query, ids -> {
	
			// Decide if the incoming FHIR-compatible IDs are complete or truncated
			final Map<Boolean, Set<String>> fhirIds = ids.stream()
				.collect(Collectors.partitioningBy(
					FhirModelHelpers::isTruncatedFhirResourceId, 
					Collectors.toSet()));
			
			// Run a pre-flight search request to find the complete IDs for the truncated ones first
			final Set<String> truncatedFhirIds = fhirIds.get(Boolean.TRUE);
			final Set<String> truncatedInternalIdPrefixes = truncatedFhirIds.stream()
				.map(FhirModelHelpers::fromFhirResourceId)
				.collect(Collectors.toSet());

			final Set<String> completeInternalIds = fhirIds.get(Boolean.FALSE).stream()
				.map(FhirModelHelpers::fromFhirResourceId)
				.collect(Collectors.toSet());

			if (!truncatedFhirIds.isEmpty()) {
				Hits<String> idHits;
				
				try {
					
					idHits = context.service(RevisionSearcher.class)
						.search(Query.select(String.class)
							.from(ResourceDocument.class, VersionDocument.class)
							.fields(ResourceDocument.Fields.ID)
							.where(ResourceDocument.Expressions.idPrefixes(truncatedInternalIdPrefixes))
							.limit(truncatedInternalIdPrefixes.size())
							.build());
					
				} catch (final IOException e) {
					throw new RuntimeException(e);
				}
				
				// Apply the transformation from internal to FHIR ID again and see which ones were actually requested in the original filter
				idHits.stream()
					.filter(id -> truncatedFhirIds.contains(FhirModelHelpers.toFhirResourceId(id)))
					.forEach(completeInternalIds::add);
			}
			
			// Both resource and version documents have an "id" field; we will use the expression from ResourceDocument
			return ResourceDocument.Expressions.ids(completeInternalIds);
		});
	}

	/**
	 * Subclasses may override this method to add special handling for the URL
	 * filter. The default implementation uses the provided values to match the
	 * "url" field on both ResourceDocument and VersionDocument instances.
	 * 
	 * @param query
	 */
	protected void addUrlFilter(final ExpressionBuilder query) {
		addFilter(query, OptionKey.URL, String.class, ResourceDocument.Expressions::urls);
	}

	/**
	 * Subclasses may override this method to add special handling for the version
	 * filter. The default implementation uses the provided values to match the
	 * "version" field on VersionDocument instances.
	 * 
	 * @param query
	 */
	protected void addVersionFilter(final ExpressionBuilder query) {
		addFilter(query, OptionKey.VERSION, String.class, VersionDocument.Expressions::versions);
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
			
			if (!internalFields.contains(ResourceDocument.Fields.CREATED_AT)) {
				internalFields.add(ResourceDocument.Fields.CREATED_AT);
			}
			
			if (!internalFields.contains(ResourceDocument.Fields.UPDATED_AT)) {
				internalFields.add(ResourceDocument.Fields.UPDATED_AT);
			}
			
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
		
		entry.setId(FhirModelHelpers.toFhirResourceId(resource.getId()));
		entry.setStatus(toPublicationStatus(resource.getStatus()));
		entry.setMeta(toMeta(resource.getUpdatedAt(), resource.getCreatedAt()));
		
		// Add tooling ID and "native" resource URI as user data to be used for later processing if needed
		entry.setUserData(TerminologyResource.Fields.TOOLING_ID, resource.getToolingId());
		entry.setUserData(TerminologyResource.Fields.RESOURCE_URI, resource.getResourceURI());
		
		// Copy Snow Owl's native identifier to "name" when requested to be included
		includeIfFieldSelected(R5ObjectFields.MetadataResource.NAME, resource::getId, entry::setName);
		
		// We have a description field available both for the resource and the version, here the one for the resource is needed
		includeIfFieldSelected(R5ObjectFields.MetadataResource.DESCRIPTION, resource::getResourceDescription, entry::setDescription);
		includeIfFieldSelected(R5ObjectFields.MetadataResource.TITLE, resource::getTitle, entry::setTitle);
		includeIfFieldSelected(R5ObjectFields.Resource.LANGUAGE, resource::getLanguage, entry::setLanguage);
		includeIfFieldSelected(R5ObjectFields.MetadataResource.PURPOSE, resource::getPurpose, entry::setPurpose);
		includeIfFieldSelected(R5ObjectFields.MetadataResource.COPYRIGHT, resource::getCopyright, entry::setCopyright);

		includeIfFieldSelected(R5ObjectFields.DomainResource.TEXT, () -> getBlankNarrative(), entry::setText);
		includeIfFieldSelected(R5ObjectFields.MetadataResource.PUBLISHER, () -> getPublisher(resource.getSettings()), entry::setPublisher);
		includeIfFieldSelected(R5ObjectFields.MetadataResource.DATE, () -> toDateTimeType(resource.getEffectiveTime()), entry::setDateElement);
		// addContact(contact) is a no-op if contact is null so we can safely call it here
		includeIfFieldSelected(R5ObjectFields.MetadataResource.CONTACT, () -> toContactDetail(resource.getContact()), entry::addContact);
		
		/*
		 * XXX: Subclasses usually don't modify properties that are already set here in the superclass, however
		 * "url" and "version" may require special handling for some resource types. In case of CodeSystems, 
		 * this happens in FhirCodeSystemSearchRequest#expandResourceSpecificFields.
		 */
		includeIfFieldSelected(R5ObjectFields.MetadataResource.URL, resource::getUrl, entry::setUrl);
		includeIfFieldSelected(R5ObjectFields.MetadataResource.VERSION, resource::getVersion, entry::setVersion);
		
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
		
		// Support filtering by the FHIR resource ID which needs to be transformed to the internal ID first
		addFhirIdFilter(context, query);
		
		/*
		 * The "name" property can be used to query the native resource ID. Values do
		 * not always line up with the regexp restrictions in the FHIR specification for
		 * "name" but failing that test should only result in a validation warning.
		 */
		addFilter(query, OptionKey.NAME, String.class, ResourceDocument.Expressions::ids);
		
		// Let subclasses decide whether special handling is needed for URL and version filters
		addUrlFilter(query);
		addVersionFilter(query);
		
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

	/**
	 * Common representation for both resource and version documents to simplify search and mapping logic.
	 * 
	 * @since 8.0
	 */
	protected static class ResourceFragment {
		private String id;
		private String version;
		private String description;
		private String resourceType;
		private Long createdAt;
		private Long updatedAt;
		private String toolingId;
		private String url;
		private String branchPath;
		private Long effectiveTime;
		
		private String resourceDescription;
		private String title;
		private String status;
		private String contact;
		private String copyright;
		private String language;
		private String purpose;
		private String oid;
		private Map<String, Object> settings;
		
		private RevisionBranchPoint created;
		
		public final ResourceURI getResourceURI() {
			return ResourceURI.of(resourceType, id);
		}
		
		public String getId() {
			return id;
		}
		
		public String getVersion() {
			return version;
		}
		
		public Long getEffectiveTime() {
			return effectiveTime;
		}
		
		public String getDescription() {
			return description;
		}
		
		public String getResourceType() {
			return resourceType;
		}
		
		public Long getCreatedAt() {
			return createdAt;
		}
		
		public Long getUpdatedAt() {
			return updatedAt;
		}
		
		public String getToolingId() {
			return toolingId;
		}
		
		public String getUrl() {
			return url;
		}
		
		public String getBranchPath() {
			return branchPath;
		}
		
		public String getResourceDescription() {
			return resourceDescription;
		}
		
		public String getTitle() {
			return title;
		}
		
		public String getStatus() {
			return status;
		}
		
		public String getContact() {
			return contact;
		}
		
		public String getCopyright() {
			return copyright;
		}
		
		public String getLanguage() {
			return language;
		}
		
		public String getPurpose() {
			return purpose;
		}
		
		public String getOid() {
			return oid;
		}
		
		public Map<String, Object> getSettings() {
			return settings;
		}
		
		public RevisionBranchPoint getCreated() {
			return created;
		}
		
		public void setId(final String id) {
			this.id = id;
		}
		
		public void setVersion(final String version) {
			this.version = version;
		}
		
		public void setDescription(final String description) {
			this.description = description;
		}
		
		public void setResourceType(final String resourceType) {
			this.resourceType = resourceType;
		}
		
		public void setCreatedAt(final Long createdAt) {
			this.createdAt = createdAt;
		}
		
		public void setUpdatedAt(final Long updatedAt) {
			this.updatedAt = updatedAt;
		}
		
		public void setToolingId(final String toolingId) {
			this.toolingId = toolingId;
		}
		
		public void setUrl(final String url) {
			this.url = url;
		}
		
		public void setBranchPath(final String branchPath) {
			this.branchPath = branchPath;
		}
		
		public void setResourceDescription(final String resourceDescription) {
			this.resourceDescription = resourceDescription;
		}
		
		public void setTitle(final String title) {
			this.title = title;
		}
		
		public void setStatus(final String status) {
			this.status = status;
		}
		
		public void setContact(final String contact) {
			this.contact = contact;
		}
		
		public void setCopyright(final String copyright) {
			this.copyright = copyright;
		}
		
		public void setLanguage(final String language) {
			this.language = language;
		}
		
		public void setPurpose(final String purpose) {
			this.purpose = purpose;
		}
		
		public void setOid(final String oid) {
			this.oid = oid;
		}
		
		public void setSettings(final Map<String, Object> settings) {
			this.settings = settings;
		}
		
		public void setCreated(final RevisionBranchPoint created) {
			this.created = created;
		}
	}
}

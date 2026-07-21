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

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.Set;

import org.hl7.fhir.r5.model.Bundle;

import com.b2international.snowowl.core.context.ResourceRepositoryRequestBuilder;
import com.b2international.snowowl.core.domain.RepositoryContext;
import com.b2international.snowowl.core.request.SearchResourceRequest;
import com.b2international.snowowl.core.request.SearchResourceRequestBuilder;
import com.b2international.snowowl.core.version.VersionDocument;
import com.b2international.snowowl.fhir.core.HistorySort;
import com.b2international.snowowl.fhir.core.Summary;
import com.b2international.snowowl.fhir.core.exceptions.BadRequestException;
import com.b2international.snowowl.fhir.core.request.FhirResourceHistoryGetRequest.OptionKey;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

/**
 * @since 10.3
 */
public abstract class FhirResourceHistoryGetRequestBuilder<B extends FhirResourceHistoryGetRequestBuilder<B>> 
		extends SearchResourceRequestBuilder<B, RepositoryContext, Bundle>
		implements ResourceRepositoryRequestBuilder<Bundle> {
	
	protected final String resourceId;
	
	public FhirResourceHistoryGetRequestBuilder(String resourceId) {
		this.resourceId = resourceId;
		// Set default sorting
		sortHistoryBy(HistorySort.LAST_UPDATED_DESCENDING);
	}
	
	protected String getResourceId() {
		return resourceId;
	}
	
	public final B filterByVersion(String version) {
		return addOption(OptionKey.VERSION, version);
	}
	
	public final B filterByVersions(Iterable<String> versions) {
		return addOption(OptionKey.VERSION, versions);
	}
	
	public final B filterBySince(String since) {
		if (since != null) {
			try {
				return filterBySince(Instant.parse(since).toEpochMilli());
			} catch (DateTimeParseException e) {
				throw new BadRequestException(String.format("'%s' is not a valid _since instant value.", since));
			}
		} else {
			return getSelf();
		}
	}
	
	public final B filterBySince(long since) {
		return addOption(OptionKey.SINCE, since);
	}
	
	public final B filterByAt(String at) {
		if (at != null) {
			try {
				return filterByAt(Instant.parse(at).toEpochMilli());
			} catch (DateTimeParseException e) {
				throw new BadRequestException(String.format("'%s' is not a valid _at instant value.", at));
			}
		} else {
			return getSelf();
		}
	}
	
	public final B filterByAt(long at) {
		return addOption(OptionKey.AT, at);
	}
	
	public final B sortHistoryBy(String sort) {
		if (sort != null) {
			if (HistorySort.LAST_UPDATED_DESCENDING.equalsIgnoreCase(sort))
				return sortBy(SearchResourceRequest.SortField.of(VersionDocument.Fields.UPDATED_AT, false));
			else if (HistorySort.LAST_UPDATED_ASCENDING.equalsIgnoreCase(sort)) {
				return sortBy(SearchResourceRequest.SortField.of(VersionDocument.Fields.UPDATED_AT, true));
			} else if (HistorySort.NONE.equalsIgnoreCase(sort)) {
				// This will not take effect due to the default sorting applied
				return getSelf(); 
			} else {
				throw new BadRequestException(String.format("'%s' is unrecognized or not yet supported _sort value. Supported values are: '%s'", sort, HistorySort.VALUES));
			}
		} else {
			return getSelf();
		}
	}

	public final B setSummary(String summary) {
		if (summary == null || Summary.FALSE.equalsIgnoreCase(summary)) {
			return getSelf();
		} else if (Summary.TRUE.equalsIgnoreCase(summary)) {
			return setElements(getSummaryFields());
		} else if (Summary.TEXT.equalsIgnoreCase(summary)) {
			return setElements(getSummaryTextFields());
		} else if (Summary.DATA.equalsIgnoreCase(summary)) {
			return setElements(getSummaryDataFields());
		} else if (Summary.COUNT.equalsIgnoreCase(summary)) {
			return setLimit(0);
		} else {
			throw new BadRequestException(String.format("'%s' is unrecognized or not yet supported _summary value. Supported values are: '%s'", summary, Summary.VALUES));
		}
	}
	
	public final B setElements(Iterable<String> elements) {
		return setElements(elements, true);
	}
	
	public final B setElements(Iterable<String> elements, boolean appendMandatoryFields) {
		if (elements == null) {
			return getSelf();
		} else {
			// register the newly added fields only, throw away the previous set
			final Set<String> fields = new LinkedHashSet<>();
			
			// first, append mandatory if requested
			if (appendMandatoryFields) {
				fields.addAll(getMandatoryFields());
			}
			
			// then, append all explicitly requested fields
			elements.forEach(fields::add);
			
			// check for anything not supported by this search request (per resource type)
			Set<String> unrecognizedElements = Sets.difference(fields, getKnownResourceFields());
			if (!unrecognizedElements.isEmpty()) {
				throw new BadRequestException(String.format(
					"'%s' %s unrecognized or not yet supported _elements value(s). Supported values are: '%s'", 
					unrecognizedElements, 
					unrecognizedElements.size() == 1 ? "is" : "are",
					getKnownResourceFields()
				));
			}
			
			return setFields(ImmutableList.copyOf(fields));
		}
	}

	protected abstract Set<String> getMandatoryFields();
	protected abstract Set<String> getSummaryFields();
	protected abstract Set<String> getSummaryTextFields();
	protected abstract Set<String> getSummaryDataFields();
	protected abstract Set<String> getKnownResourceFields();
	
	public B setCount(int count) {
		return setLimit(count);
	}
	
}

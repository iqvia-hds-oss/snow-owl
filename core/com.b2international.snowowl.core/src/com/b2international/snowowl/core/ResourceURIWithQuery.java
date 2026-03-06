/*
 * Copyright 2022-2025 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.core;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;
import java.util.Objects;

import com.b2international.commons.CompareUtils;
import com.b2international.commons.exceptions.BadRequestException;
import com.b2international.snowowl.core.branch.Branch;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

/**
 * @since 8.5
 */
public final class ResourceURIWithQuery implements Serializable, Comparable<ResourceURIWithQuery> {

	private static final long serialVersionUID = 1L;

	public static final String QUERY_PART_SEPARATOR = "?";
	private static final char QUERY_KEY_SEPARATOR = '&';
	private static final char QUERY_KEY_VALUE_SEPARATOR = '=';
	
	private final String uri;
	private final ResourceURI resourceUri;
	private final String query;
	
	private transient Multimap<String, String> queryValues;
	
	@JsonCreator
	public ResourceURIWithQuery(String uri) {
		if (Strings.isNullOrEmpty(uri)) {
			throw new BadRequestException("Malformed Resource URI value: '%s' is empty.", uri);
		}
		
		int firstQueryCharAt = uri.indexOf(QUERY_PART_SEPARATOR);
		// if no query part specified, then set end of text
		if (firstQueryCharAt == -1) {
			firstQueryCharAt = uri.length();
		}
		this.uri = uri;
		this.resourceUri = new ResourceURI(uri.substring(0, firstQueryCharAt));
		this.query = firstQueryCharAt == uri.length() ? "" : uri.substring(firstQueryCharAt + 1, uri.length());
	}
	
	ResourceURIWithQuery(ResourceURI resourceUri, String query) {
		this.resourceUri = resourceUri;
		if (Strings.isNullOrEmpty(query)) {
			this.query = query;
			this.uri = resourceUri.toString();
		} else if (query.startsWith(QUERY_PART_SEPARATOR)) {
			this.query = query.substring(1);
			this.uri = String.join("", resourceUri.toString(), query);
		} else {
			this.query = query;
			this.uri = String.join(QUERY_PART_SEPARATOR, resourceUri.toString(), query);
		}
	}
	
	public String getUri() {
		return uri;
	}
	
	public ResourceURI getResourceUri() {
		return resourceUri;
	}
	
	public String getQuery() {
		return query;
	}
	
	public boolean hasQueryPart() {
		return !CompareUtils.isEmpty(getQuery());
	}
	
	public boolean isHead() {
		return resourceUri.isHead();
	}
	
	public boolean isLatest() {
		return resourceUri.isLatest();
	}
	
	public Multimap<String, String> getQueryValues() {
		if (queryValues == null) {
			queryValues = parseQueryValues(query);
		}
		return queryValues;
	}
	
	private static Multimap<String, String> parseQueryValues(final String query) {
		final Multimap<String, String> result = HashMultimap.create();
		
		if (Strings.isNullOrEmpty(query)) {
			return result;
		}
		
		int pos = 0;
		while (pos < query.length()) {

			// Find the next '=' that separates key and value
			final int valueSepIdx = query.indexOf(QUERY_KEY_VALUE_SEPARATOR, pos);
			if (valueSepIdx == -1) {
				throw new BadRequestException("Query string '%s' is missing '%c' after key '%s'.", query, QUERY_KEY_VALUE_SEPARATOR, query.substring(pos));
			}
			
			final String key = query.substring(pos, valueSepIdx);
			if (key.isEmpty()) {
				throw new BadRequestException("Query string '%s' has an empty parameter key at position %d.", query, pos);
			}
			
			if (key.startsWith(String.valueOf(QUERY_KEY_SEPARATOR))) {
				throw new BadRequestException("Query string '%s' has an empty parameter key at position %d.", query, pos);
			}
			
			if (CharMatcher.whitespace().matchesAnyOf(key)) {
				throw new BadRequestException("Query string '%s' has a parameter key with whitespace, starting at position %d.", query, pos);
			}
			
			// Find the next '&' that ends the value, '=' is intentionally allowed here
			final int keySepIdx = query.indexOf(QUERY_KEY_SEPARATOR, valueSepIdx + 1);
			if (keySepIdx == -1) {
				// The value for the current key runs to end of string
				result.put(key, query.substring(valueSepIdx + 1));
				break;
			} else {
				// The value for the current key runs until the next '&' we just found
				result.put(key, query.substring(valueSepIdx + 1, keySepIdx));
				pos = keySepIdx + 1;
				if (pos == query.length()) {
					throw new BadRequestException("Query string '%s' has a trailing '%c' at position %d.", query, QUERY_KEY_SEPARATOR, keySepIdx);
				}
			}
		}
		
		return result;
	}
	
	@Override
	public int compareTo(ResourceURIWithQuery o) {
		return toString().compareTo(o.toString());
	}
	
	@JsonValue
	@Override
	public String toString() {
		return getUri();
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(uri, resourceUri, query);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		ResourceURIWithQuery other = (ResourceURIWithQuery) obj;
		return Objects.equals(uri, other.uri) 
				&& Objects.equals(resourceUri, other.resourceUri)
				&& Objects.equals(query, other.query);
	}

	public static ResourceURIWithQuery of(String resourceType, String resourceIdWithQuery) {
		checkNotNull(resourceType, "'resourceType' must be specified");
		checkNotNull(resourceIdWithQuery, "'resourceIdWithQuery' must be specified");
		return new ResourceURIWithQuery(String.join(Branch.SEPARATOR, resourceType, resourceIdWithQuery));
	}
	
	public static ResourceURIWithQuery of(String resourceType, String resourceId, String query) {
		checkNotNull(resourceType, "'resourceType' must be specified");
		checkNotNull(resourceId, "'resourceId' must be specified");
		checkNotNull(query, "'query' must be specified");
		return new ResourceURIWithQuery(String.join(QUERY_PART_SEPARATOR, String.join(Branch.SEPARATOR, resourceType, resourceId), query));
	}
	
	public static ResourceURIWithQuery of(ResourceURI resourceUri) {
		return of(resourceUri, null);
	}
	
	public static ResourceURIWithQuery of(ResourceURI resourceUri, String query) {
		return new ResourceURIWithQuery(resourceUri, query);
	}

}

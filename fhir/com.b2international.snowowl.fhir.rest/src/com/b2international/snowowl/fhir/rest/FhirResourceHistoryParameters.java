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
package com.b2international.snowowl.fhir.rest;

import java.util.Set;
import java.util.SortedSet;

import org.hl7.fhir.exceptions.FHIRFormatError;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @since 10.3
 */
public class FhirResourceHistoryParameters {

	private static final String PARAM_COUNT = "_count";
	private static final String PARAM_SINCE = "_since";
	private static final String PARAM_AT = "_at";
	private static final String PARAM_SORT = "_sort";
	private static final String PARAM_AFTER = "_after";

	private static final SortedSet<String> ACCEPTED_PARAMS = ImmutableSortedSet.of(
		PARAM_COUNT,
		PARAM_SINCE,
		PARAM_AT,
		PARAM_SORT,
		PARAM_AFTER
	);

	// paging
	@Parameter(description = "The maximum number of items to return", schema = @Schema(defaultValue = "10"))
	private int _count = 10;
	
	// filters
	@Parameter(description = PARAM_SINCE)
	private String _since;
	
	@Parameter(description = PARAM_AT)
	private String _at;
		
	@Parameter(description = PARAM_SORT)
	private String _sort;
	
	// extensions (paging)
	@Schema
	private String _after;

	private final Set<String> unknownParameterNames = Sets.newHashSet();
	
	
	public int get_count() {
		return _count;
	}

	public String get_since() {
		return _since;
	}

	public String get_at() {
		return _at;
	}

	public String get_sort() {
		return _sort;
	}

	public String get_after() {
		return _after;
	}

	public void set_count(int _count) {
		this._count = _count;
	}

	public void set_since(String _since) {
		this._since = _since;
	}

	public void set_at(String _at) {
		this._at = _at;
	}

	public void set_sort(String _sort) {
		this._sort = _sort;
	}

	public void set_after(String _after) {
		this._after = _after;
	}

	@JsonAnySetter
	public void setAdditionalParameter(String parameterName, Object _parameterValue) {
		// We are only interested in the name of each unknown search parameter but the method signature needs to include the value
		if (!ACCEPTED_PARAMS.contains(parameterName)) {
			unknownParameterNames.add(parameterName);
		}
	}
	
	@Override
	public String toString() {
		return MoreObjects.toStringHelper(getClass())
			.omitNullValues()
			.add(PARAM_COUNT, _count)
			.add(PARAM_SINCE, _since)
			.add(PARAM_AT, _at)
			.add(PARAM_SORT, _sort)
			.add(PARAM_AFTER, _after)
			.toString();
	}
	
	/**
	 * @throws FHIRFormatError - if there are unknown/unrecognized parameters specified
	 */ 
	public final void checkParameters() {
		Set<String> acceptedParameterNames = getAcceptedParameterNames();
		
		if (acceptedParameterNames == null || acceptedParameterNames.isEmpty()) {
			return;
		}
		
		if (!unknownParameterNames.isEmpty()) {
			throw new FHIRFormatError(String.format("Unknown/Unsupported parameters found in the request '%s'. Accepted parameters are: %s.", unknownParameterNames, acceptedParameterNames));
		}
	}
	
	/**
	 * Subclasses may optionally override this method to provide support for parameter validation via the {@link #checkParameters(boolean)} method.
	 * @return
	 */
	protected SortedSet<String> getAcceptedParameterNames() {
		return ACCEPTED_PARAMS;
	}	
}

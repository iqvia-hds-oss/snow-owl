/*
 * Copyright 2017-2025 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.core.jobs;

import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.jobs.SearchJobRequest.OptionKey;
import com.b2international.snowowl.core.request.SearchPageableCollectionResourceRequestBuilder;
import com.b2international.snowowl.core.request.SearchResourceRequest;
import com.b2international.snowowl.core.request.SystemRequestBuilder;
import com.google.common.collect.Iterables;

/**
 * A request builder that builds a request to search/list {@link RemoteJob job entries}.
 * 
 * @since 5.7
 */
public final class SearchJobRequestBuilder 
		extends SearchPageableCollectionResourceRequestBuilder<SearchJobRequestBuilder, ServiceProvider, RemoteJobs> 
		implements SystemRequestBuilder<RemoteJobs> {

	SearchJobRequestBuilder() {
	}

	/**
	 * Filter {@link RemoteJob job entries} by the assigned user.
	 * 
	 * @param user
	 * @return
	 */
	public SearchJobRequestBuilder filterByUser(String user) {
		return addOption(OptionKey.USER, user);
	}
	
	/**
	 * Filter {@link RemoteJob job entries} by the assigned users.
	 * 
	 * @param users
	 * @return
	 */
	public SearchJobRequestBuilder filterByUsers(Iterable<String> users) {
		return addOption(OptionKey.USER, users);
	}
	
	public SearchJobRequestBuilder filterByState(String state) {
		return filterByState(RemoteJobState.valueOfIgnoreCase(state));
	}
	
	public SearchJobRequestBuilder filterByState(Iterable<String> states) {
		return filterByStates(states == null ? null : Iterables.transform(states, RemoteJobState::valueOfIgnoreCase));
	}
	
	public SearchJobRequestBuilder filterByState(RemoteJobState state) {
		return addOption(OptionKey.STATE, state);
	}
	
	public SearchJobRequestBuilder filterByStates(Iterable<RemoteJobState> states) {
		return addOption(OptionKey.STATE, states);
	}
	
	public SearchJobRequestBuilder filterByType(String type) {
		return addOption(OptionKey.TYPE, type);
	}
	
	public SearchJobRequestBuilder filterByTypes(Iterable<String> types) {
		return addOption(OptionKey.TYPE, types);
	}
	
	public SearchJobRequestBuilder filterByTerm(String filterTerm) {
		return addOption(OptionKey.TERM, filterTerm);
	}
	
	public SearchJobRequestBuilder filterByKey(String key) {
		return addOption(OptionKey.KEY, key);
	}
	
	public SearchJobRequestBuilder filterByKeys(Iterable<String> keys) {
		return addOption(OptionKey.KEY, keys);
	}
	
	@Override
	protected SearchResourceRequest<ServiceProvider, RemoteJobs> createSearch() {
		return new SearchJobRequest();
	}

}

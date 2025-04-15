/*
 * Copyright 2011-2025 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.snomed.core.rest.request;

import static com.google.common.collect.Maps.newHashMap;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Stream;

import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.events.Request;
import com.b2international.snowowl.snomed.core.rest.domain.BaseSnomedResourceRestChange;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @since 4.5
 */
public final class RefSetMemberBulkRestRequest extends BaseSnomedResourceRestChange {

	private final Collection<RestRequest> requests;

	@JsonCreator
	public RefSetMemberBulkRestRequest(@JsonProperty("requests") Collection<RestRequest> requests) {
		this.requests = requests;
	}
	
	public <C extends ServiceProvider> Stream<Request<C, ?>> resolve(RequestResolver<C> resolver) {
		return requests.stream().map(request -> request.resolve(resolver));
	}
	
	public Collection<RestRequest> getRequests() {
		return requests;
	}
	
	public static final class RestRequest {

		private String action;

		private Map<String, Object> source = newHashMap();

		@JsonCreator
		public RestRequest(@JsonProperty("action") String action) {
			this.action = action;
		}

		@JsonAnySetter
		public void setSource(String key, Object value) {
			source.put(key, value);
		}
		
		/**
		 * Converts this {@link RestRequest} to a {@link Request} with the given {@link RequestResolver} that can be executed.
		 *
		 * @param resolver
		 *            - the resolver to use for {@link Request} resolution
		 * @return
		 */
		public <C extends ServiceProvider> Request<C, ?> resolve(RequestResolver<C> resolver) {
			return resolver.resolve(action, source);
		}

	}

}

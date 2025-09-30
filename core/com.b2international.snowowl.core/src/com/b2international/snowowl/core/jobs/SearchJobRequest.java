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

import java.util.Collections;

import com.b2international.index.Hits;
import com.b2international.index.Searcher;
import com.b2international.index.query.Expression;
import com.b2international.index.query.Expressions;
import com.b2international.index.query.Expressions.ExpressionBuilder;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.request.SearchIndexResourceRequest;

/**
 * @since 5.7
 */
final class SearchJobRequest extends SearchIndexResourceRequest<ServiceProvider, RemoteJobs, RemoteJobEntry> {

	private static final long serialVersionUID = 1L;

	SearchJobRequest() {
	}
	
	enum OptionKey {
		
		/**
		 * Filter matches unique job keys.
		 */
		KEY,

		/**
		 * Filter matches based on their executed request type 
		 */
		TYPE,
		
		/**
		 * Filter matches by description, status, user and created/started/finished date.
		 */
		TERM,
		
		/**
		 * Filter matches by user name value.
		 */
		USER,
		
		/**
		 * Filter matches by current state value. 
		 */
		STATE,
	}

	@Override
	protected Expression prepareQuery(ServiceProvider context) {
		final ExpressionBuilder queryBuilder = Expressions.bool();
		
		addIdFilter(queryBuilder, RemoteJobEntry.Expressions::ids);

		addFilter(queryBuilder, OptionKey.KEY, String.class, RemoteJobEntry.Expressions::keys);
		addFilter(queryBuilder, OptionKey.TYPE, String.class, RemoteJobEntry.Expressions::types);
		
		if (containsKey(OptionKey.TERM)) {
			String searchTerm = getString(OptionKey.TERM);
			queryBuilder.must(
					Expressions.bool()
						.should(Expressions.prefixMatch(RemoteJobEntry.Fields.USER, searchTerm))
						.should(Expressions.prefixMatch(RemoteJobEntry.Fields.STATE, searchTerm.toUpperCase()))
						.should(Expressions.matchTextAll(RemoteJobEntry.Fields.DESCRIPTION_PREFIX, searchTerm))
					.build()
			);
		}
		
		addFilter(queryBuilder, OptionKey.USER, String.class, RemoteJobEntry.Expressions::users);
		addFilter(queryBuilder, OptionKey.STATE, RemoteJobState.class, RemoteJobEntry.Expressions::states);
		
		return queryBuilder.build();
	}
	
	@Override
	protected RemoteJobs toCollectionResource(ServiceProvider context, Hits<RemoteJobEntry> hits) {
		return new RemoteJobs(hits.getHits(), hits.getSearchAfter(), hits.getLimit(), hits.getTotal());
	}

	@Override
	protected Searcher searcher(ServiceProvider context) {
		return context.service(RemoteJobTracker.class).searcher();
	}
	
	@Override
	protected Class<RemoteJobEntry> getDocumentType() {
		return RemoteJobEntry.class;
	}
	
	@Override
	protected RemoteJobs createEmptyResult(int limit) {
		return new RemoteJobs(Collections.emptyList(), null, limit, 0);
	}

}

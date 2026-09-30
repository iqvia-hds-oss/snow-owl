/*
 * Copyright 2020-2025 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.core.request;

import com.b2international.commons.options.Options;
import com.b2international.snowowl.core.ResourceURI;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.domain.Concept;
import com.b2international.snowowl.core.domain.Concepts;
import com.b2international.snowowl.core.events.util.Promise;

/**
 * @since 7.5
 */
public interface ConceptSearchRequestEvaluator {

	public enum OptionKey {

		/**
		 * Explicit ID filter to return all concepts that have any of the given IDs.
		 */
		ID,

		/**
		 * Match concepts that have the specified active status. Accepts a boolean <code>true</code> or <code>false</code> value.
		 */
		ACTIVE,

		/**
		 * A term filter that matches concepts having a term match. The exact semantics of how a term match works depends on the given code system,
		 * but usually it supports exact, partial word and prefix matches.
		 */
		TERM,

		/**
		 * One or more query expressions (defined in the target code system's query language) to include matches.
		 */
		QUERY,

		/**
		 * One or more query expressions (defined in the target code system's query language) to exclude matches from the results.
		 */
		MUST_NOT_QUERY,

		/**
		 * Language locales (tag, Accept-Language header, etc.) to use in order of preference when determining the display label or term for a match.
		 */
		LOCALES,

		/**
		 * Search matches after the specified sort key.
		 */
		AFTER,

		/**
		 * Number of matches to return.
		 */
		LIMIT,
		
		/**
		 * Minimum score to match.
		 */
		MIN_SCORE,
		
		/**
		 * Specific fields to load when requested content (consumers of the API must be familiar with the underlying schema)
		 */
		FIELDS,
		
		/**
		 * Expand additional data requested by the client. If set, implementers should set the {@link Concept#setInternalConcept(Object)} to the
		 * fully loaded internal tooling representation of the code and return it along with the generic {@link Concept} object.
		 */
		EXPAND,
		
		/**
		 * Set the preferred display type to return
		 */
		DISPLAY,
		
		/**
		 * Filters terms by their type.
		 */
		TERM_TYPE,

		/**
		 * Filters concepts by their type.
		 */
		TYPE, 
		
		/**
		 * Filters concepts by their direct parents.
		 */
		PARENT,
		
		/**
		 * Filters concepts by their ancestors (direct or indirect parents).
		 */
		ANCESTOR, 

		/**
		 * Filter by semantic similarity using a query vector
		 */
		KNN,
		
		/**
		 * Filter by semantic similarity using only query vectors based on concept description terms
		 */
		DESCRIPTION_KNN,
		
	}
	
	
	/**
	 * Evaluate the given search options on the given context and return generic {@link Concept} instances back in a {@link Concepts} pageable
	 * resource.
	 * 
	 * @param uri
	 *            - the code system uri where the search is being evaluated
	 * @param context
	 *            - the context to perform the search on
	 * @param search
	 *            - the search filters and options to apply to the code system specific search
	 * @return
	 */
	Concepts evaluate(ResourceURI uri, ServiceProvider context, Options search);
	
	/**
	 * Evaluate the given search options on the given context and return generic {@link Concept} instances back in a {@link Concepts} pageable
	 * resource.
	 * 
	 * @param uri
	 *            - the code system uri where the search is being evaluated
	 * @param context
	 *            - the context to perform the search on
	 * @param search
	 *            - the search filters and options to apply to the code system specific search
	 * @return
	 */
	Promise<Concepts> evaluateAsync(ResourceURI uri, ServiceProvider context, Options search);

	/**
	 * No-op request evaluator that returns zero results
	 * 
	 * @since 7.5
	 */
	ConceptSearchRequestEvaluator NOOP = new ConceptSearchRequestEvaluator() {
		
		@Override
		public Concepts evaluate(ResourceURI uri, ServiceProvider context, Options search) {
			return new Concepts(0, 0);
		}

		@Override
		public Promise<Concepts> evaluateAsync(ResourceURI uri, ServiceProvider context, Options search) {
			return Promise.immediate(new Concepts(0, 0));
		}
	};
}

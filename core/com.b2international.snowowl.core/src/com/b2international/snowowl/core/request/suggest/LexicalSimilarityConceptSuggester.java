/*
 * Copyright 2025 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.core.request.suggest;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.b2international.commons.http.ExtendedLocale;
import com.b2international.snowowl.core.codesystem.CodeSystemRequests;
import com.b2international.snowowl.core.events.util.Promise;
import com.b2international.snowowl.core.identity.User;
import com.b2international.snowowl.core.plugin.Component;
import com.b2international.snowowl.core.request.SearchIndexResourceRequest;
import com.b2international.snowowl.core.request.search.LexicalSimilarityTermFilter;
import com.b2international.snowowl.core.request.search.TermFilter;
import com.b2international.snowowl.eventbus.IEventBus;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * @since 9.8.0
 */
@Component
@JsonTypeName("lexical")
public class LexicalSimilarityConceptSuggester implements ConceptSuggester {

	/**
	 * The number of most frequent tokens (words) to consider from the selected like text corpus. Default value is 9.
	 */
	@JsonProperty
	private Integer topTokenCount = 9;
	
	/**
	 * The number of most frequent tokens (topTokenCount) to match in order to be accepted as a suggestion. Default value is 3. 
	 */
	@JsonProperty
	private Integer minOccurenceCount = 3;
	
	/**
	 * The minimum length for a token to be considered as potential top token. Default value is 0, accepting all token lengths.
	 */
	@JsonProperty
	private int minTokenLength = 0;
	
	/**
	 * Whether to ignore any stopwords in the selected text corpus or not. Default value is <code>true</code>.
	 */
	@JsonProperty
	private boolean ignoreStopwords = true;
	
	/**
	 * Whether to perform english word stemming on the tokens or not. Default value is false.
	 */
	@JsonProperty
	private boolean stemming = false;
	
	/**
	 * Whether to allow top tokens to fuzzy match among candidates. Default value is false. 
	 */
	@JsonProperty
	private boolean fuzzy = false;
	
	@Override
	public Promise<Suggestions> suggest(ConceptSuggestionContext context, int limit, String display,
			List<ExtendedLocale> locales) {
		// gather both the like texts and separately the top tokens from the context
		List<String> likes = context.streamLikes().limit(LexicalSimilarityTermFilter.MAX_EXACT_TERMS).toList();
		List<String> topTokens = context.topTokens(topTokenCount, minTokenLength, stemming);
		
		// if there are no tokens to search for then shortcut here
		if (topTokens.isEmpty()) {
			return Promise.immediate(new Suggestions(topTokens, limit, 0));
		}
		
		final TermFilter termFilter = TermFilter.lexicalSimilarity()
				.terms(likes, String.join(" ", topTokens))
				.minShouldMatch(Math.min(minOccurenceCount, topTokens.size()))
				.ignoreStopwords(ignoreStopwords)
				.fuzziness(fuzzy ? "AUTO" : null)
				// TODO make fuzziness options configurable in term suggester settings
				.build();
		
		// get the ECL query of the from code system
		final String inclusionQuery = context.getInclusionQueries();
		// get the dynamically computed exclusion query set
		final Collection<String> exclusionQueries = context.exclusionQuery(context.from().getResourceUri());
		
		return CodeSystemRequests.prepareSearchConcepts()
				// always return active concepts only
				// TODO support in suggest API settings?
				.filterByActive(true)
				// configure from, resource and optional ECL query
				.filterByCodeSystemUri(context.from().getResourceUri())
				.filterByQuery(inclusionQuery)
				// make sure we won't suggest the same concepts as defined in like and unlike arrays (for the same code system)
				.filterByExclusions(exclusionQueries.isEmpty() ? null : exclusionQueries)
				// configure lexical match as basis of suggestion
				.filterByTerm(termFilter)
				// configure display, limit and locales
				.setPreferredDisplay(display)
				.setLimit(limit)
				.setMinScore(context.minConfidenceScore())
				.setLocales(locales)
				// always order by score
				.sortBy(SearchIndexResourceRequest.SCORE)
				.build()
				.async(Map.of(User.class, context.service(User.class)))
				.execute(context.service(IEventBus.class))
				.then(concepts -> {
					return new Suggestions(topTokens, concepts.getItems(), concepts.getSearchAfter(), limit, concepts.getTotal());
				});
	}
	
}

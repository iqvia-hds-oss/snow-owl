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

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.b2international.commons.CompareUtils;
import com.b2international.commons.exceptions.BadRequestException;
import com.b2international.commons.exceptions.NotImplementedException;
import com.b2international.commons.http.ExtendedLocale;
import com.b2international.snowowl.core.conceptmap.ConceptMapMappingSearchRequestBuilder;
import com.b2international.snowowl.core.conceptmap.ConceptMapRequests;
import com.b2international.snowowl.core.domain.Concept;
import com.b2international.snowowl.core.domain.ConceptMapMappings;
import com.b2international.snowowl.core.events.util.Promise;
import com.b2international.snowowl.core.identity.User;
import com.b2international.snowowl.core.plugin.Component;
import com.b2international.snowowl.core.request.SearchIndexResourceRequest;
import com.b2international.snowowl.eventbus.IEventBus;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSortedSet;

/**
 * @since 9.7.0
 */
@Component
@JsonTypeName("conceptmap")
public final class ConceptMapMappingConceptSuggester implements ConceptSuggester {

	public static final String SCOPE_SOURCE = "source";
	public static final String SCOPE_TARGET = "target";
	
	private static final Set<String> SCOPE_VALUES = Set.of(SCOPE_SOURCE, SCOPE_TARGET);
	 
	@JsonProperty
	private String uri;
	
	/**
	 * Specifies which side of the concept map should be suggested based on the other side's lexical or code matches. Defaults to the target side;
	 */
	@JsonProperty
	private String scope;

	/**
	 * Selects only active mappings or when disabled all mappings including inactives. Defaults to true.
	 */
	@JsonProperty
	private Boolean activeOnly;
	
	/**
	 * The number of most frequent tokens (words) to consider from the selected like text corpus. Default value is 9.
	 */
	@JsonProperty
	private Integer topTokenCount = 9;
	
	/**
	 * The minimum length for a token to be considered as potential top token. Default value is 0, accepting all token lengths.
	 */
	@JsonProperty
	private int minTokenLength = 0;
	
	/**
	 * Whether to perform english word stemming on the tokens or not. Default value is false.
	 */
	@JsonProperty
	private boolean stemming = false;
	
	@Override
	public Promise<Suggestions> suggest(ConceptSuggestionContext context, int limit, String display, List<ExtendedLocale> locales) {
		if (Strings.isNullOrEmpty(this.uri)) {
			throw new BadRequestException("'uri' argument is required to select the concept map for the existing mapping suggestions.");
		}

		// unlikes are not supported yet
		if (!CompareUtils.isEmpty(context.unlikes())) {
			throw new NotImplementedException("'unlike' is not supported yet for conceptmap based suggester.");
		}
		
		// init defaults and validate
		if (this.scope == null) {
			this.scope = SCOPE_TARGET;
		} else if (!SCOPE_VALUES.contains(this.scope)) {
			throw new BadRequestException("Invalid 'scope' configuration value '%s'. Allowed values are: ['source', 'target']", this.scope);
		}
		
		if (this.activeOnly == null) {
			this.activeOnly = Boolean.TRUE;
		}
		
		List<String> topTokens = context.topTokens(topTokenCount, minTokenLength, stemming);
		
		// ensure that the context form parameter matches the source/target part of the
		
		ConceptMapMappingSearchRequestBuilder req = ConceptMapRequests.prepareSearchConceptMapMappings()
			.filterByActive(this.activeOnly)
			.filterByConceptMap(this.uri);
		
		switch (this.scope) {
		case SCOPE_SOURCE:
			req.filterByMapTargetTerm(String.join(" ", topTokens));
			break;
		case SCOPE_TARGET:
			req.filterByMapSourceTerm(String.join(" ", topTokens));
			break;
		default:
			throw new IllegalStateException("Should not happen");
		}
		
		req
			// configure display, limit and locales
			.setLimit(limit)
			.setPreferredDisplay(display)
			.setLocales(locales)
			// always order by score
			.sortBy(SearchIndexResourceRequest.SCORE)
			.build()
			.async(Map.of(User.class, context.service(User.class)))
			.execute(context.service(IEventBus.class))
			.then(mappings -> toSuggestions(topTokens, mappings, limit));
		
		return Promise.immediate(new Suggestions(null, 0, 0));
	}
	
	private Suggestions toSuggestions(List<String> topTokens, ConceptMapMappings mappings, int limit) {
		final List<Concept> concepts = mappings.stream().map(mapping -> {
			switch (this.scope) {
			case SCOPE_SOURCE:
				return new Concept(mapping.getTargetComponentURI(), null /*active flag is unknown at this point*/, mapping.getTargetTerm(), ImmutableSortedSet.of(), mapping.getTargetIconId(), null, null, 0.0f);
			case SCOPE_TARGET:
				return new Concept(mapping.getSourceComponentURI(), null /*active flag is unknown at this point*/, mapping.getSourceTerm(), ImmutableSortedSet.of(), mapping.getSourceIconId(), null, null, 0.0f);
			default:
				throw new IllegalStateException("Should not happen");
			}
		}).toList();
		return new Suggestions(topTokens, concepts, null, limit, mappings.getTotal());
	}

}

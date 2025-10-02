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
package com.b2international.snowowl.core.request.suggest;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.tartarus.snowball.ext.EnglishStemmer;

import com.b2international.commons.collections.Collections3;
import com.b2international.commons.exceptions.ApiException;
import com.b2international.commons.exceptions.BadRequestException;
import com.b2international.commons.http.ExtendedLocale;
import com.b2international.index.compat.TextConstants;
import com.b2international.snomed.ecl.Ecl;
import com.b2international.snowowl.core.ResourceTypeConverter;
import com.b2international.snowowl.core.ResourceURI;
import com.b2international.snowowl.core.ResourceURIWithQuery;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.api.SnowowlRuntimeException;
import com.b2international.snowowl.core.branch.Branch;
import com.b2international.snowowl.core.codesystem.CodeSystem;
import com.b2international.snowowl.core.codesystem.CodeSystemRequests;
import com.b2international.snowowl.core.domain.Concept;
import com.b2international.snowowl.core.domain.Concepts;
import com.b2international.snowowl.core.domain.DelegatingContext;
import com.b2international.snowowl.core.domain.Description;
import com.google.common.base.Splitter;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.*;

/**
 * @since 8.5
 */
public final class ConceptSuggestionContext extends DelegatingContext {

	// Split terms at delimiter or whitespace separators
	private static final Splitter TOKEN_SPLITTER = Splitter.on(TextConstants.WHITESPACE_OR_DELIMITER_MATCHER)
			.trimResults()
			.omitEmptyStrings();
	
	private final ResourceURIWithQuery from;
	private final SortedSet<String> likes;
	private final SortedSet<String> unlikes;
	private final List<ExtendedLocale> locales;
	
	// provides a cache to avoid loading the same content again from the stores and compute the topTokens only once per run for a given config
	private final LoadingCache<TopTokenConfig, List<String>> topTokenCache = CacheBuilder.newBuilder().build(new CacheLoader<>() {
		@Override
		public List<String> load(TopTokenConfig config) throws Exception {
			
			// Gather tokens based on the config and the English language characteristics
			final Multiset<String> tokenOccurrences = HashMultiset.create();
			final EnglishStemmer stemmer = new EnglishStemmer();
			
			// tokens to consider are streamed based on the like parameter configuration
			streamLikes()
				.map(term -> term.toLowerCase(Locale.US))
				.flatMap(lowerCaseTerm -> TOKEN_SPLITTER.splitToList(lowerCaseTerm).stream())
				.filter(token -> token.length() >= config.minTokenLength) // skip short tokens
				.filter(token -> !TextConstants.STOP_WORDS_EN.contains(token)) // ignore stopwords from top tokens, so they won't interfere with minShouldMatch
				.map(token -> config.stemming ? stemToken(stemmer, token) : token)
				.forEach(tokenOccurrences::add);
			
			return Multisets.copyHighestCountFirst(tokenOccurrences)
				.elementSet()
				.stream()
				.limit(config.topTokenCount)
				.collect(Collectors.toList());
		}
	});
	
	// dynamically computed exclusion items during like item computation
	private Multimap<ResourceURI, String> exclusionQueriesPerResourceUri = HashMultimap.create();

	public ConceptSuggestionContext(ServiceProvider context, String from, List<String> likes, List<String> unlikes, List<ExtendedLocale> locales) {
		super(context);
		this.locales = locales;
		var resolvedUri = resolveUri(context, from);
		if (resolvedUri == null) {
			// treat unresolved URIs as CodeSystems for now, it would be better to lookup a resource if present with this ID and throw an error if not
			resolvedUri = CodeSystem.uriWithQuery(from);
		}
		this.from = resolvedUri;
		this.likes = Collections3.toImmutableSortedSet(likes);
		this.unlikes = Collections3.toImmutableSortedSet(unlikes);
	}

	public SortedSet<String> likes() {
		return likes;
	}
	
	public SortedSet<String> unlikes() {
		return unlikes;
	}

	public Stream<String> streamLikes() {
		Multimap<ResourceURI, String> unlikeQueriesByResource = HashMultimap.create();
		
		unlikes.forEach(unlike -> {
			final ResourceURIWithQuery uri = resolveUri(this, unlike);
			if (uri == null) {
				// not URI, skip
				// TODO figure out how to represent unlike keywords in a query, ECL NOT {{ term: <x> }} ?
				return;
			} 
			Collection<String> eclQueries = uri.getQueryValues().get("ecl");
			if (eclQueries.isEmpty()) {
				throw new BadRequestException("Selecting an entire Code System as unlike is not supported yet. Specify an ECL query part like this: %s?ecl=<your_query>", uri.getResourceUri().withoutResourceType());
			} else {
				eclQueries.forEach(q -> {
					unlikeQueriesByResource.put(uri.getResourceUri(), q);
					exclusionQueriesPerResourceUri.put(uri.getResourceUri(), q);
				});
			}
		});
		
		// TODO optimize multiple likes specifying the same source system
		return likes
			.stream()
			.flatMap(like -> {
				final ResourceURIWithQuery uri = resolveUri(this, like);
				if (uri == null) {
					// not URI, use as is for lexical matching
					return List.of(like).stream();
				}
				
				// raw URIs are not supported yet, because those can select too many concepts
				Collection<String> eclQueries = uri.getQueryValues().get("ecl");
				if (eclQueries.isEmpty()) {
					throw new BadRequestException("Selecting an entire Code System as like is not supported yet. Specify an ECL query part like this: %s?ecl=<your_query>", uri.getResourceUri().withoutResourceType());
				}
				
				Collection<String> exclusionsForThisLike = unlikeQueriesByResource.get(uri.getResourceUri());
				String exclusionQuery = exclusionsForThisLike.isEmpty() ? null : Ecl.or(exclusionsForThisLike);
				
				// register this like query as global exclusion filter for the final suggestion search
				eclQueries.forEach(q -> {
					exclusionQueriesPerResourceUri.put(uri.getResourceUri(), q);
				});
				
				// Get the suggestion base set of concepts in case of URIs with queries
				return CodeSystemRequests.prepareSearchConcepts()
						.filterByCodeSystemUri(uri.getResourceUri())
						.filterByQuery(Ecl.or(eclQueries))
						.filterByExclusion(exclusionQuery)
						.setLimit(getPageSize())
						.setLocales(locales)
						.stream(this)
						.flatMap(Concepts::stream)
						.flatMap(concept -> getAllTerms(concept).stream());
			});
	}

	public ResourceURIWithQuery from() {
		return from;
	}
	
	public List<ExtendedLocale> locales() {
		return locales;
	}
	
	private Set<String> getAllTerms(Concept concept) {
		final Set<String> allTerms = new HashSet<>();
		
		// just in case keep adding the selected display term even though the description list of the generic concept should already contain all descriptions, not just alternatives
		if (concept.getTerm() != null) {
			allTerms.add(concept.getTerm());
		}
		
		// all other terms should be filtered by language
		if (concept.getDescriptions() != null) {
			concept.getDescriptions()
				.stream()
				.filter(d -> hasMatchingLanguage(d))
				.map(Description::getTerm)
				.forEach(allTerms::add);
		}
		
		return allTerms;
	}

	private boolean hasMatchingLanguage(Description description) {
		return locales.stream()
			.anyMatch(locale -> locale.getLanguage().equals(description.getLanguage()));
	}

	public Collection<String> exclusionQuery(ResourceURI resourceUri) {
		return exclusionQueriesPerResourceUri.get(resourceUri);
	}

	public String getInclusionQueries() {
		final Collection<String> inclusionQueries = from().getQueryValues().get("ecl");
		return inclusionQueries.isEmpty() ? null : Ecl.or(inclusionQueries);
	}
	
	private ResourceURIWithQuery resolveUri(ServiceProvider context, String uriToResolve) {
		// find the appropriate resource for this URI by looking at the plugged in resources types
		for (ResourceTypeConverter resourceTypeConverter : context.service(ResourceTypeConverter.Registry.class).getResourceTypeConverters().values()) {
			if (uriToResolve.startsWith(resourceTypeConverter.getResourceType() + Branch.SEPARATOR)) {
				return resourceTypeConverter.resolveToCodeSystemUriWithQuery(context, uriToResolve);
			}
		}
		
		// not an URI
		return null;
	}

	public List<String> topTokens(int topTokenCount, int minTokenLength, boolean stemming) {
		try {
			return topTokenCache.get(new TopTokenConfig(topTokenCount, minTokenLength, stemming));
		} catch (ExecutionException e) {
			if (e.getCause() instanceof ApiException) {
				throw (ApiException) e.getCause();
			} else {
				throw new SnowowlRuntimeException("Couldn't compute top tokens based on requested config: ", e);
			}
		}
	}
	
	private String stemToken(EnglishStemmer stemmer, String token) {
		stemmer.setCurrent(token);
		stemmer.stem();
		return stemmer.getCurrent();
	}
	
	private record TopTokenConfig(int topTokenCount, int minTokenLength, boolean stemming) {}
	
}

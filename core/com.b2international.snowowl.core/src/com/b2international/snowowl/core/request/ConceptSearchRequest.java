/*
 * Copyright 2020-2026 B2i Healthcare, https://b2ihealthcare.com
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

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.b2international.commons.exceptions.BadRequestException;
import com.b2international.commons.options.Options;
import com.b2international.snowowl.core.*;
import com.b2international.snowowl.core.codesystem.CodeSystem;
import com.b2international.snowowl.core.codesystem.CodeSystemRequests;
import com.b2international.snowowl.core.codesystem.CodeSystems;
import com.b2international.snowowl.core.config.RepositoryConfiguration;
import com.b2international.snowowl.core.config.SnowOwlConfiguration;
import com.b2international.snowowl.core.domain.Concepts;
import com.b2international.snowowl.core.events.util.Promise;
import com.google.common.collect.Iterables;

/**
 * A generic concept search request that can be executed in any code system using generic query expressions and filters to get back primary
 * components/concepts from that code system.
 * 
 * @since 7.5
 * @see ConceptSearchRequestEvaluator
 * @see ConceptSearchRequestBuilder
 */
public final class ConceptSearchRequest extends SearchResourceRequest<ServiceProvider, Concepts> {

	private static final long serialVersionUID = 1L;

	public enum OptionKey {
		
		/**
		 * Filters concepts by their associated resource.
		 */
		CODESYSTEM,
		
	}
	
	@Override
	protected Concepts createEmptyResult(int limit) {
		return new Concepts(limit, 0);
	}

	@Override
	protected Concepts doExecute(ServiceProvider context) throws IOException {
		List<ResourceURI> codeSystemUris = null;
		
		if (containsKey(OptionKey.CODESYSTEM)) {
			codeSystemUris = getCollection(OptionKey.CODESYSTEM, ResourceURI.class).stream()
					.distinct()
					.collect(Collectors.toList());
		}
		
		if (codeSystemUris == null || codeSystemUris.isEmpty()) {
			throw new BadRequestException("One or more code systems must be provided");
		} else if (codeSystemUris.size() > 1 && searchAfter() != null) {
			throw new BadRequestException("Using searchAfter is not supported with multiple code systems");
		}
		
		final int maxThread = context.service(SnowOwlConfiguration.class).getModuleConfig(RepositoryConfiguration.class).getMaxThreadsGenericConceptSearch();
		if (codeSystemUris.size() > maxThread) {
			throw new BadRequestException("Too many code systems supplied, maximum allowed number is %d", maxThread);
		}
		
		final Set<ResourceURI> codeSystemUrisWithoutPath = codeSystemUris.stream()
				.map(ResourceURI::withoutPath)
				.collect(Collectors.toSet());
		
		final Set<String> codeSystemIds = codeSystemUris.stream()
			.map(ResourceURI::getResourceId)
			.collect(Collectors.toSet());
		
		if (codeSystemUris.size() != codeSystemIds.size()) {
			throw new BadRequestException("Searching multiple versions of the same code system is not supported");
		}
		
		CodeSystems codeSystems = CodeSystemRequests.prepareSearchCodeSystem()
			.filterByIds(codeSystemIds)
//			.filterByToolingIds(toolingIds) TODO perform TOOLING filtering
//			.filterByUrls(urls) TODO perform URL filtering
			.setFields(List.of(TerminologyResource.Fields.ID, TerminologyResource.Fields.TOOLING_ID, "dependencies"))
			.setLimit(codeSystemIds.size())
			.buildAsync()
			.execute(context);
		
		// No code system was found
		if (codeSystems.isEmpty()) {
			return new Concepts(0, 0);
		}
		
		// Validate that code systems are not dependent on each other
		// so we can avoid cases where the same concept would be returned twice
		Set<ResourceURI> dependencies = codeSystems
				.stream()
				.filter(codeSystem -> codeSystem.getDependencies() != null)
				.flatMap(codeSystem -> codeSystem.getDependencies().stream())
				.map(Dependency::getUri)
				.map(ResourceURIWithQuery::getResourceUri)
				.map(ResourceURI::withoutPath)
				.collect(Collectors.toSet());
		
		if (!Collections.disjoint(codeSystemUrisWithoutPath, dependencies)) {
			throw new BadRequestException("Searching dependent code systems is not supported");
		}
		
		Map<String, String> toolingByCodeSystemId = codeSystems
				.stream()
				.collect(Collectors.toMap(CodeSystem::getId, CodeSystem::getToolingId));
		
		List<Promise<Concepts>> conceptPromises = codeSystemUris
			.stream()
			.map(codeSystemUri -> runConceptSearch(context, toolingByCodeSystemId.get(codeSystemUri.getResourceId()), codeSystemUri))
			.collect(Collectors.toList());
		
		List<Concepts> concepts = Promise.all(conceptPromises)
			.getSync(1, TimeUnit.MINUTES)
			.stream()
			.map(Concepts.class::cast)
			.collect(Collectors.toList());
		
		// for single CodeSystem searches, sorting, paging works as it should
		if (concepts.size() == 1) {
			return Iterables.getOnlyElement(concepts);
		}
		
		// calculate grand total
		int total = concepts.stream()
				.mapToInt(Concepts::getTotal)
				.sum();
		
		return new Concepts(
			concepts.stream().flatMap(Concepts::stream).limit(limit()).collect(Collectors.toList()), // TODO add manual sorting here if multiple resources have been fetched 
			null, /* not supported across codesystems */
			limit(), 
			total
		);
	}

	private Promise<Concepts> runConceptSearch(ServiceProvider context, final String toolingId, final ResourceURI codeSystemUri) {
		final Repository repository = context.service(RepositoryManager.class).get(toolingId);
		if (repository == null) {
			context.log().warn("Tooling module '{}' is missing from this deployment.", toolingId);
			return Promise.immediate(new Concepts(0, 0));
		}
		
		Options conceptSearchOptions = Options.builder()
				.putAll(options())
				.put(ConceptSearchRequestEvaluator.OptionKey.ID, componentIds())
				.put(ConceptSearchRequestEvaluator.OptionKey.AFTER, searchAfter())
				.put(ConceptSearchRequestEvaluator.OptionKey.LIMIT, limit())
				.put(ConceptSearchRequestEvaluator.OptionKey.MIN_SCORE, minScore())
				.put(ConceptSearchRequestEvaluator.OptionKey.LOCALES, locales())
				.put(ConceptSearchRequestEvaluator.OptionKey.FIELDS, fields())
				.put(ConceptSearchRequestEvaluator.OptionKey.EXPAND, expand())
				.put(SearchResourceRequest.OptionKey.SORT_BY, sortBy())
				.build();
		return context.service(RepositoryManager.class).get(toolingId)
				.service(ConceptSearchRequestEvaluator.class)
				.evaluateAsync(codeSystemUri, context, conceptSearchOptions);
	}

}

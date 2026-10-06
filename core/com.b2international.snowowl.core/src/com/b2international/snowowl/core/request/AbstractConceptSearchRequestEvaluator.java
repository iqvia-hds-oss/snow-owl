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
package com.b2international.snowowl.core.request;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;

import java.util.*;
import java.util.stream.Collectors;

import com.b2international.commons.options.Options;
import com.b2international.snomed.ecl.Ecl;
import com.b2international.snomed.ecl.ecl.*;
import com.b2international.snowowl.core.ResourceURI;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.context.TerminologyResourceContentRequestBuilder;
import com.b2international.snowowl.core.domain.*;
import com.b2international.snowowl.core.ecl.EclParser;
import com.b2international.snowowl.core.events.util.Promise;
import com.b2international.snowowl.core.request.ecl.AbstractComponentSearchRequestBuilder;
import com.b2international.snowowl.core.request.search.TermFilter;
import com.b2international.snowowl.core.request.search.TermFilterSupport;
import com.b2international.snowowl.eventbus.IEventBus;

/**
 * @since 10.4
 */
public abstract class AbstractConceptSearchRequestEvaluator<B extends SearchPageableCollectionResourceRequestBuilder<B, BranchContext, R> & TerminologyResourceContentRequestBuilder<R>, R extends PageableCollectionResource<?>>
	implements ConceptSearchRequestEvaluator {
	
	/**
	 * Determine if search can be evaluated, if not then an empty {@link Concepts} will be returned during evaluation.
	 * 
	 * @param uri
	 *            - the code system uri where the search is being evaluated
	 * @param context
	 *            - the context to perform the search on
	 * @param search
	 *            - the search filters and options to apply to the code system specific search
	 * @return
	 */
	protected boolean canEvaluate(ResourceURI uri, ServiceProvider context, Options search) {
		return true;
	}
	
	/**
	 * Prepare search request.
	 * 
	 * @param uri
	 *            - the code system uri where the search is being evaluated
	 * @param context
	 *            - the context to perform the search on
	 * @param search
	 *            - the search filters and options to apply to the code system specific search
	 * @return
	 */
	protected abstract B prepareSearchConcept(ResourceURI uri, ServiceProvider context, Options search);
	
	/**
	 * Convert tooling specific search result to generic {@link Concepts}.
	 * 
	 * @param matches
	 *            - the tooling specific search result
	 * @param uri
	 *            - the code system uri where the search is being evaluated
	 * @param context
	 *            - the context to perform the search on
	 * @param search
	 *            - the search filters and options to apply to the code system specific search
	 * @return
	 */
	protected abstract Concepts toConcepts(R matches, ResourceURI uri, ServiceProvider context, Options search);
	
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
	@Override
	public final Concepts evaluate(ResourceURI uri, ServiceProvider context, Options search) {
		if (!canEvaluate(uri, context, search)) {
			return new Concepts(0, 0);
		}
		R matches = prepareSearchConcept(uri, context, search)
			.build(uri)
			.execute(context);
		return toConcepts(matches, uri, context, search);
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
	@Override
	public final Promise<Concepts> evaluateAsync(ResourceURI uri, ServiceProvider context, Options search) {
		if (!canEvaluate(uri, context, search)) {
			return Promise.immediate(new Concepts(0, 0));
		}
		return prepareSearchConcept(uri, context, search)
			.build(uri)
			.withContext(context)
			.execute(context.service(IEventBus.class))
			.then(matches -> toConcepts(matches, uri, context, search));
	}

	/**
	 * Subclasses may optionally use this method to initialize the common concept model from their tooling specific model.
	 * 
	 * @param codeSystem
	 * @param concept
	 * @param iconId
	 * @param term
	 * @param score
	 * @return
	 */
	protected final Concept toConcept(ResourceURI codeSystem, IComponent concept, String iconId, String term, Float score) {
		Concept result = new Concept(codeSystem, concept.getComponentType());
		result.setId(concept.getId());
		result.setReleased(concept.isReleased());
		result.setIconId(iconId);
		result.setTerm(term);
		result.setScore(score);
		// treat all concepts active by protected, so terminology plugin that does not support statuses can be simplified
		result.setActive(true);
		result.setInternalConcept(concept);
		mapCodeSystemSpecificFields(result, concept);
		return result;
	}

	/**
	 * Maps all remaining fields on the given result {@link Concept} model object based on the tooling specific concept received in the second argument.
	 * 
	 * @param result
	 * @param concept
	 */
	protected void mapCodeSystemSpecificFields(Concept result, IComponent concept) {
	}

	/**
	 * Prepares an ID filter from the ID option key only. Use as the protected ID filter.
	 * 
	 * @param requestBuilder
	 * @param search
	 */
	protected final void evaluateIdFilterOptions(SearchResourceRequestBuilder<?, ?, ?> requestBuilder, Options search) {
		if (search.containsKey(OptionKey.ID)) {
			requestBuilder.filterByIds(search.getCollection(OptionKey.ID, String.class));
		}
	}
	
	/**
	 * Prepares an ID filter from the ID, QUERY and MUST_NOT_QUERY option keys. Use only if the underlying tooling does not support any kind of special query parameters and you'd like to handle basic query support for enumerated list of component IDs.
	 * 
	 * @param requestBuilder
	 * @param search
	 */
	protected final void evaluateIdQueryMustNotQueryOptionsAsIdFilter(SearchResourceRequestBuilder<?, ?, ?> requestBuilder, Options search) {
		if (!search.containsKey(OptionKey.ID) && !search.containsKey(OptionKey.QUERY) && !search.containsKey(OptionKey.MUST_NOT_QUERY)) {
			return;
		}
		
		Set<String> idFilter = newHashSet();
		
		if (search.containsKey(OptionKey.ID)) {
			idFilter.addAll(search.getCollection(OptionKey.ID, String.class));
		}
		
		if (search.containsKey(OptionKey.QUERY)) {
			idFilter.addAll(extractIds(search.getCollection(OptionKey.QUERY, String.class)));
		}
		
		if (search.containsKey(OptionKey.MUST_NOT_QUERY)) {
			idFilter.removeAll(extractIds(search.getCollection(OptionKey.MUST_NOT_QUERY, String.class)));
		}
		
		requestBuilder.filterByIds(idFilter);
	}
	
	protected final void evaluateTermFilterOptions(TermFilterSupport<?> requestBuilder, Options search) {
		if (search.containsKey(OptionKey.TERM)) {
			requestBuilder.filterByTerm(search.get(OptionKey.TERM, TermFilter.class));
		}
	}
	
	/**
	 * Configures knn filtering if the necessary configuration present in the given search options.
	 * 
	 * @param requestBuilder
	 * @param search
	 */
	protected final void evaluateKnnFilterOptions(KnnFilterSupport<?> requestBuilder, Options search) {
		if (search.containsKey(OptionKey.KNN)) {
			requestBuilder.filterByKnn(search.get(OptionKey.KNN, KnnFilter.class));
		}
		if (search.containsKey(OptionKey.DESCRIPTION_KNN) && requestBuilder instanceof DescriptionKnnFilterSupport<?> knnSupport) {
			knnSupport.filterByDescriptionKnn(search.get(OptionKey.DESCRIPTION_KNN, KnnFilter.class));
		}
	}
	
	/**
	 * Appends an ECL filter to the given component search request filter when either a QUERY or MUST_NOT_QUERY part is present in the given options.
	 * 
	 * @param context
	 * @param req
	 * @param search
	 */
	protected final void evaluateQueryOptions(ServiceProvider context, AbstractComponentSearchRequestBuilder<?, ?, ?> req, Options search) {
		if (search == null) {
			return;
		}
		
		if (search.containsKey(OptionKey.QUERY) || search.containsKey(OptionKey.MUST_NOT_QUERY)) {
			StringBuilder query = new StringBuilder();
			
			if (search.containsKey(OptionKey.QUERY)) {
				Collection<String> inclusions = search.getCollection(OptionKey.QUERY, String.class);
				query
					.append("(")
					.append(joinEclExpressions(context, inclusions))
					.append(")");
			} else {
				query.append(Ecl.ANY);
			}
			
			if (search.containsKey(OptionKey.MUST_NOT_QUERY)) {
				Collection<String> exclusions = search.getCollection(OptionKey.MUST_NOT_QUERY, String.class);
				query
					.append(" MINUS (")
					.append(joinEclExpressions(context, exclusions))
					.append(")");
			}
			
			req.filterByEcl(query.toString());
		}
	}

	/**
	 * Join the given list of individual ECL expressions to a single ECL expression. Usually this uses OR boolean operator to generate the final
	 * expression, but some implementations might offer optimized alternatives. When there are more than 100 expressions present in the given
	 * collection the system will try to optimize single ID clauses into a proper ID filter so ECL evaluation is efficient.
	 * 
	 * @param context
	 * @param expressions
	 * @return
	 * @see Ecl#or(Collection)
	 */
	private String joinEclExpressions(ServiceProvider context, Collection<String> expressions) {
		// in case of having more than a hundred individual expressions, try to run an early optimization
		if (expressions.size() > 100) {
			var parser = context.service(EclParser.class);
			
			final SortedSet<String> singleConceptIds = new TreeSet<>();
			final SortedSet<String> descendantOfConceptIds = new TreeSet<>();
			final SortedSet<String> descendantOrSelfOfConceptIds = new TreeSet<>();
			final SortedSet<String> remainingExpressions = new TreeSet<>();
			
			for (String expression : expressions) {
				ExpressionConstraint expressionConstraint = parser.parse(expression, getIgnoredSyntaxErrorCodes());
				
				Optional<EclConceptReference> eclConceptReference = extractEclGrammarElement(EclConceptReference.class, expressionConstraint);
				if (eclConceptReference.isPresent()) {
					singleConceptIds.add(eclConceptReference.get().getId());
					continue;
				}
				
				Optional<DescendantOf> descendantOf = extractEclGrammarElement(DescendantOf.class, expressionConstraint);
				Optional<EclConceptReference> descendantOfReference = descendantOf.flatMap(d -> extractEclGrammarElement(EclConceptReference.class, d.getConstraint()));
				if (descendantOfReference.isPresent()) {
					descendantOfConceptIds.add(descendantOfReference.get().getId());
					continue;
				}
				
				Optional<DescendantOrSelfOf> descendantOrSelfOf = extractEclGrammarElement(DescendantOrSelfOf.class, expressionConstraint);
				Optional<EclConceptReference> descendantOrSelfOfReference = descendantOrSelfOf.flatMap(d -> extractEclGrammarElement(EclConceptReference.class, d.getConstraint()));
				if (descendantOrSelfOfReference.isPresent()) {
					descendantOrSelfOfConceptIds.add(descendantOrSelfOfReference.get().getId());
					continue;
				}
				
				remainingExpressions.add(expression);
			}
			
			final List<String> rewrittenExpressions = newArrayList();
			
			if (!descendantOrSelfOfConceptIds.isEmpty()) {
				final String descendantOrSelfOfWithFilter = String.format("<< (* {{ C id = %s }})", toConceptSet(descendantOrSelfOfConceptIds));
				rewrittenExpressions.add(descendantOrSelfOfWithFilter);
			}
			
			if (!descendantOfConceptIds.isEmpty()) {
				final String descendantOfWithFilter = String.format("< (* {{ C id = %s }})", toConceptSet(descendantOfConceptIds));
				rewrittenExpressions.add(descendantOfWithFilter);
			}
			
			if (!singleConceptIds.isEmpty()) {
				final String singleConceptIdsWithFilter = String.format("* {{ C id = %s }}", toConceptSet(singleConceptIds));
				rewrittenExpressions.add(singleConceptIdsWithFilter);
			}
			
			rewrittenExpressions.addAll(remainingExpressions);
			return Ecl.or(rewrittenExpressions);	
		}
		
		return Ecl.or(expressions);
	}

	private String toConceptSet(final Collection<String> conceptIds) {
		return conceptIds.stream().collect(Collectors.joining(" ", "(", ")"));
	}
	
	/**
	 * A {@link Set} of syntax error codes to ignore when parsing ECL expressions. By protected this method returns an empty set and considers
	 * everything to be fully ECL compatible.
	 * 
	 * @return
	 */
	protected Set<String> getIgnoredSyntaxErrorCodes() {
		return Collections.emptySet();
	}

	/**
	 * Extract IDs from ID|TERM| like query strings. If the query does not have a PIPE character in it, then treat the entire query as an ID.
	 * 
	 * @since 7.7
	 * @return a collection of extracted IDs
	 * @see Concept#fromConceptString(String)
	 */
	private static Collection<String> extractIds(Collection<String> queries) {
		return queries.stream().map(query -> Concept.fromConceptString(query)[0]).collect(Collectors.toList());
	}

	private static <T> Optional<T> extractEclGrammarElement(final Class<T> elementClass, final ExpressionConstraint expression) {
		if (elementClass.isInstance(expression)) {
			return Optional.of(elementClass.cast(expression));
		} else if (expression instanceof NestedExpression nestedExpression) {
			return extractEclGrammarElement(elementClass, nestedExpression.getNested());
		} else {
			return Optional.empty();
		}
	}
}

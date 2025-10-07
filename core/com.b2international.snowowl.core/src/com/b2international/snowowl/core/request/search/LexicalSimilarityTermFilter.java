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
package com.b2international.snowowl.core.request.search;

import static com.b2international.index.query.Expressions.dismax;
import static com.b2international.index.query.Expressions.matchTextAll;
import static com.b2international.index.query.Expressions.matchTextAny;

import java.util.Set;

import com.b2international.commons.exceptions.BadRequestException;
import com.b2international.index.query.Expression;
import com.b2international.index.query.Expressions;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

/**
 * @since 9.8.0
 */
public final class LexicalSimilarityTermFilter extends TermFilter {

	private static final long serialVersionUID = 1L;
	
	// TODO make configurable?
	public static final int MAX_EXACT_TERMS = 100;

	private final Set<String> exactTerms;
	private final String minTerm;
	private final Integer minShouldMatch;
	
	private final Boolean ignoreStopwords;
	private final Boolean caseSensitive;
	private final Boolean synonyms;
	
	private final String fuzziness;
	private final Integer prefixLength;
	private final Integer maxExpansions;
	
	LexicalSimilarityTermFilter(final Set<String> exactTerms, final String minTerm, final Integer minShouldMatch, final Boolean ignoreStopwords, final Boolean caseSensitive, final Boolean synonyms, final String fuzziness, final Integer prefixLength, final Integer maxExpansions) {
		if (exactTerms.isEmpty()) {
			throw new BadRequestException("At least one exact term must be provided.");
		}
		if (exactTerms.size() > MAX_EXACT_TERMS) {
			throw new BadRequestException("A maximum of %s exact terms can be provided, but %s were given.", MAX_EXACT_TERMS, exactTerms.size());
		}
		if (minTerm == null || minTerm.isBlank()) {
			throw new BadRequestException("minTerm must be provided.");
		}
		this.exactTerms = exactTerms;
		this.minTerm = minTerm;
		this.minShouldMatch = minShouldMatch;
		this.ignoreStopwords = ignoreStopwords;
		this.caseSensitive = caseSensitive;
		this.synonyms = synonyms;
		this.fuzziness = fuzziness;
		this.prefixLength = prefixLength;
		this.maxExpansions = maxExpansions;
	}

	public Set<String> getExactTerms() {
		return exactTerms;
	}
	
	public String getMinTerm() {
		return minTerm;
	}
	
	@Override
	public Set<String> getTerms() {
		return Sets.union(exactTerms, Set.of(minTerm));
	}
	
	public Integer getMinShouldMatch() {
		return minShouldMatch;
	}
	
	public Boolean isIgnoreStopwords() {
		return ignoreStopwords;
	}
	
	public Boolean isCaseSensitive() {
		return caseSensitive;
	}
	
	public Boolean isSynonyms() {
		return synonyms;
	}
	
	public String getFuzziness() {
		return fuzziness;
	}
	
	public Integer getPrefixLength() {
		return prefixLength;
	}
	
	public Integer getMaxExpansions() {
		return maxExpansions;
	}
	
	@JsonIgnore
	public boolean isAnyMatch() {
		return getMinShouldMatch() != null;
	}
	
	public LexicalSimilarityTermFilter withIgnoreStopwords() {
		return new Builder(this).ignoreStopwords(true).build();
	}
	
	@Override
	public Expression toExpression(String field, String textFieldSuffix, String exactFieldSuffix, String prefixFieldSuffix) {
		// exact match overrides anything and should be scored the highest
		// for each received exactTerm, inject an exact match clause in a dismax query
		// the first received exact match is prioritized to be 100% score
		// while anything else received as exact will be treated as secondary and will receive a score of 99%
		final Expression exactMatch = TermFilter.exact().term(Iterables.getFirst(exactTerms, null)).caseSensitive(isCaseSensitive()).build().toExpression(field, textFieldSuffix, exactFieldSuffix, prefixFieldSuffix);
		final Expression nearExactMatch;
		if (exactTerms.size() > 1) {
			var nearExactMatches = Expressions.bool();
			exactTerms.stream().skip(1).forEach(nearExactTerm -> {
				nearExactMatches.should(TermFilter.exact().term(nearExactTerm).caseSensitive(isCaseSensitive()).build().toExpression(field, textFieldSuffix, exactFieldSuffix, prefixFieldSuffix));
			});
			nearExactMatch = nearExactMatches.build();
		} else {
			nearExactMatch = Expressions.matchNone();
		}

		// matching based on fuzziness should receive a higher scores than matching words in different order or leaving out words
		var fuzzyExactMatches = Expressions.bool();
		exactTerms.forEach(exactTerm -> {
			fuzzyExactMatches.should(matchTextAll(fieldAlias(field, exactFieldSuffix), exactTerm).withFuzziness(fuzziness, prefixLength, maxExpansions));
		});
		var fuzzyExactMatch = fuzzyExactMatches.build();
		
		// matching based on synonyms and tokenized text where order does not matter anymore
		var matchAllWithSynonymsIgnoreStopwords = matchTextAll(fieldAlias(field, textFieldSuffix), getMinTerm())
			.withIgnoreStopwords(isIgnoreStopwords())
			.withSynonymsEnabled(isSynonyms());
		
		// leaving out words but still matching some completely should generate a better similarity than prefix only
		var matchAnyWithSynonymsIgnoreStopwords = matchTextAny(fieldAlias(field, textFieldSuffix), getMinTerm(), getMinShouldMatch())
			.withIgnoreStopwords(isIgnoreStopwords())
			.withSynonymsEnabled(isSynonyms());
		
		var allPrefixMatch = matchTextAll(fieldAlias(field, prefixFieldSuffix), getMinTerm());
		var anyPrefixMatch = matchTextAny(fieldAlias(field, prefixFieldSuffix), getMinTerm(), getMinShouldMatch());

		// using dismax to select the best score from a single matching route as should would generate scores from each matching routes
		return dismax(
			// as per suggester contract, the highest score is 1.0f, meaning the best possible match, 100%
			exactMatch.constantScore(1.0f),
			// second best are the near exact matches, they probably match a secondary term in the system
			nearExactMatch.constantScore(0.99f),
			// almost as good as any exact but a bit fuzzy gets the third best score, 95%
			fuzzyExactMatch.constantScore(0.95f),
			
			// TODO for the next four we should check if we can reuse the computed score from ES somehow in a sensible way
			
			// then word order does not matter anymore but we still need to match all words and the input text can use synonyms and we can ignore stopwords, 90%
			matchAllWithSynonymsIgnoreStopwords.constantScore(0.90f),
			// then word order does not matter and we can leave out some words from the match, 75%
			matchAnyWithSynonymsIgnoreStopwords.constantScore(0.75f),
			// then try to match the input on the prefix indexed fields, scoring 65%
			allPrefixMatch.constantScore(0.65f),
			// then again on the prefix indexed fields, but some words can be left out, 50%
			anyPrefixMatch.constantScore(0.50f)
		);
	}
	
	public static final class Builder {
		
		private Set<String> exactTerms;
		private String minTerm;
		private Integer minShouldMatch;
		
		private Boolean ignoreStopwords;
		private Boolean caseSensitive;
		private Boolean synonyms;
		
		private String fuzziness;
		private Integer prefixLength;
		private Integer maxExpansions;
		
		Builder() {
		}
		
		Builder(LexicalSimilarityTermFilter from) {
			this.exactTerms = from.getExactTerms();
			this.minTerm = from.getMinTerm();
			this.minShouldMatch = from.getMinShouldMatch();
			this.ignoreStopwords = from.isIgnoreStopwords();
			this.caseSensitive = from.isCaseSensitive();
			this.synonyms = from.isSynonyms();
			this.fuzziness = from.getFuzziness();
			this.prefixLength = from.getPrefixLength();
			this.maxExpansions = from.getMaxExpansions();
		}
		
		public Builder terms(Iterable<String> exactTerms, String minTerm) {
			this.exactTerms = exactTerms == null ? null : ImmutableSet.copyOf(exactTerms);
			this.minTerm = minTerm;
			return this;
		}
		
		public Builder minShouldMatch(Integer minShouldMatch) {
			this.minShouldMatch = minShouldMatch;
			return this;
		}
		
		public Builder ignoreStopwords(Boolean ignoreStopwords) {
			this.ignoreStopwords = ignoreStopwords;
			return this;
		}
		
		public Builder caseSensitive(Boolean caseSensitive) {
			this.caseSensitive = caseSensitive;
			return this;
		}
		
		public Builder synonyms(Boolean synonyms) {
			this.synonyms = synonyms;
			return this;
		}
		
		public Builder fuzzy() {
			return fuzziness("AUTO");
		}
		
		public Builder fuzziness(String fuzziness) {
			this.fuzziness = fuzziness;
			return this;
		}
		
		public Builder prefixLength(Integer prefixLength) {
			this.prefixLength = prefixLength;
			return this;
		}
		
		public Builder maxExpansions(Integer maxExpansions) {
			this.maxExpansions = maxExpansions;
			return this;
		}
		
		public LexicalSimilarityTermFilter build() {
			return new LexicalSimilarityTermFilter(exactTerms, minTerm, minShouldMatch, ignoreStopwords, caseSensitive, synonyms, fuzziness, prefixLength, maxExpansions);
		}

	}
	
}

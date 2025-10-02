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
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * @since 9.8.0
 */
public final class LexicalSimilarityTermFilter extends TermFilter {

	private static final long serialVersionUID = 1L;

	private final String term;
	private final Integer minShouldMatch;
	
	private final Boolean ignoreStopwords;
	private final Boolean caseSensitive;
	private final Boolean synonyms;
	
	private final String fuzziness;
	private final Integer prefixLength;
	private final Integer maxExpansions;
	
	LexicalSimilarityTermFilter(final String term, final Integer minShouldMatch, final Boolean ignoreStopwords, final Boolean caseSensitive, final Boolean synonyms, final String fuzziness, final Integer prefixLength, final Integer maxExpansions) {
		if (term == null) {
			throw new BadRequestException("'term' filter parameter was null.");
		}
		this.term = term.trim();
		this.minShouldMatch = minShouldMatch;
		this.ignoreStopwords = ignoreStopwords;
		this.caseSensitive = caseSensitive;
		this.synonyms = synonyms;
		this.fuzziness = fuzziness;
		this.prefixLength = prefixLength;
		this.maxExpansions = maxExpansions;
	}
	
	public String getTerm() {
		return term;
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
	
	public LexicalSimilarityTermFilter withTerm(String newTerm) {
		return new Builder(this).term(newTerm).build();
	}
	
	@Override
	public Set<String> getTerms() {
		return Set.of(term);
	}
	
	@Override
	public Expression toExpression(String field, String textFieldSuffix, String exactFieldSuffix, String prefixFieldSuffix) {
		// exact match overrides anything and should be scored the highest
		var exactMatch = TermFilter.exact().term(getTerm()).caseSensitive(isCaseSensitive()).build().toExpression(field, textFieldSuffix, exactFieldSuffix, prefixFieldSuffix);

		// matching based on fuzziness should receive a higher scores than matching words in different order or leaving out words
		var fuzzyExactMatch = matchTextAll(fieldAlias(field, exactFieldSuffix), getTerm()).withFuzziness(fuzziness, prefixLength, maxExpansions);
		
		// matching based on synonyms and tokenized text where order does not matter anymore
		var matchAllWithSynonymsIgnoreStopwords = matchTextAll(fieldAlias(field, textFieldSuffix), getTerm())
			.withIgnoreStopwords(isIgnoreStopwords())
			.withSynonymsEnabled(isSynonyms());
		
		// leaving out words but still matching some completely should generate a better similarity than prefix only
		var matchAnyWithSynonymsIgnoreStopwords = matchTextAny(fieldAlias(field, textFieldSuffix), getTerm(), getMinShouldMatch())
			.withIgnoreStopwords(isIgnoreStopwords())
			.withSynonymsEnabled(isSynonyms());
		
		var allPrefixMatch = matchTextAll(fieldAlias(field, prefixFieldSuffix), getTerm());
		var anyPrefixMatch = matchTextAny(fieldAlias(field, prefixFieldSuffix), getTerm(), getMinShouldMatch());

		// using dismax to select the best score from a single matching route as should would generate scores from each matching routes
		return dismax(
			// as per suggester contract, the highest score is 1.0f, meaning the best possible match, 100%
			exactMatch.constantScore(1.0f),
			// almost as good as the exact but a bit fuzzy gets the second best score, 95%
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
		
		private String term;
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
			this.term = from.getTerm();
			this.minShouldMatch = from.getMinShouldMatch();
			this.ignoreStopwords = from.isIgnoreStopwords();
			this.caseSensitive = from.isCaseSensitive();
			this.synonyms = from.isSynonyms();
			this.fuzziness = from.getFuzziness();
			this.prefixLength = from.getPrefixLength();
			this.maxExpansions = from.getMaxExpansions();
		}
		
		public Builder term(String term) {
			this.term = term;
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
			return new LexicalSimilarityTermFilter(term, minShouldMatch, ignoreStopwords, caseSensitive, synonyms, fuzziness, prefixLength, maxExpansions);
		}

	}
	
}

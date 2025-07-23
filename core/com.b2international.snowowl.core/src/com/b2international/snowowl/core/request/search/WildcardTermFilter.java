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

import java.util.Set;

import com.b2international.index.query.Expression;
import com.b2international.index.query.Expressions;
import com.google.common.collect.ImmutableSet;

/**
 * @since 9.7.2
 */
public final class WildcardTermFilter extends TermFilter {

	private static final long serialVersionUID = 1L;

	private final String term;
	private final boolean caseSensitive;

	WildcardTermFilter(final String term, final boolean caseSensitive) {
		this.term = term;
		this.caseSensitive = caseSensitive;
	}

	@Override
	public Set<String> getTerms() {
		return (term == null) ? ImmutableSet.of() : ImmutableSet.of(term);
	}

	public boolean isCaseSensitive() {
		return caseSensitive;
	}

	@Override
	public Expression toExpression(final String field, final String textFieldSuffix, final String exactFieldSuffix, final String prefixFieldSuffix) {
		if (term == null) {
			return Expressions.matchNone();
		} else {
			return Expressions.wildcard(String.join(".", field, exactFieldSuffix), term, caseSensitive);
		}
	}

	public static final class Builder {

		private String term;
		private boolean caseSensitive;

		Builder() { }

		Builder(final WildcardTermFilter from) {
			this.term = from.getSingleTermOrNull();
			this.caseSensitive = from.isCaseSensitive();
		}

		public Builder term(final String term) {
			this.term = term;
			return this; 
		}

		public Builder caseSensitive(final boolean caseSensitive) {
			this.caseSensitive = caseSensitive;
			return this;
		}

		public WildcardTermFilter build() {
			return new WildcardTermFilter(term, caseSensitive);
		}

	}
}

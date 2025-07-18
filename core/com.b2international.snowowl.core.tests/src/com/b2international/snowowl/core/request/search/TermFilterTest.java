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

import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.Objects;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.b2international.index.*;
import com.b2international.index.mapping.Field;
import com.b2international.index.mapping.FieldAlias;
import com.b2international.index.mapping.FieldAlias.FieldAliasType;
import com.b2international.index.mapping.Mappings;
import com.b2international.index.query.Query;
import com.b2international.snowowl.core.repository.JsonSupport;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @since 9.8.0
 */
public class TermFilterTest {

	@Doc(type = "data")
	public static class Data {

		@ID
		private final String id;

		@Field(
			aliases = {
				@FieldAlias(name = "text", type = FieldAliasType.TEXT, analyzer=Analyzers.CASE_SENSITIVE),
				@FieldAlias(name = "prefix", type = FieldAliasType.TEXT, analyzer = Analyzers.PREFIX, searchAnalyzer = Analyzers.TOKENIZED),
				@FieldAlias(name = "exact", type = FieldAliasType.KEYWORD)
			} 
		)
		private String analyzedField;

		@JsonCreator
		public Data(@JsonProperty("id") final String id) {
			this.id = id;
		}

		public String getId() {
			return id;
		}

		public String getAnalyzedField() {
			return analyzedField;
		}

		public void setAnalyzedField(final String analyzedField) {
			this.analyzedField = analyzedField;
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj) return true;
			if (obj == null || getClass() != obj.getClass()) return false;
			final Data other = (Data) obj;

			return id.equals(other.id) && analyzedField.equals(other.analyzedField);
		}

		@Override
		public int hashCode() {
			return Objects.hash(id, analyzedField);
		}

		@Override
		public String toString() {
			return "Data [id=" + id + ", analyzedField=" + analyzedField + "]";
		}
	}

	private ObjectMapper mapper;
	private Index index;

	@Before
	public void setup() {
		mapper = JsonSupport.getDefaultObjectMapper();
		index = Indexes.createIndex("data", mapper, new Mappings(Data.class));
		index.admin().create();
	}

	@After
	public void after() {
		this.index.admin().delete();
	}

	private void indexDocuments(final String matchValue, final String nonMatchValue) {
		index.write(index -> {
			final Data match = new Data("match");
			match.setAnalyzedField(matchValue);
			index.put(match);

			final Data nonMatch = new Data("nonMatch");
			nonMatch.setAnalyzedField(nonMatchValue);
			index.put(nonMatch);

			index.commit();
			return null;
		});
	}

	private List<Data> search(final TermFilter filter) {
		return index.read(searcher -> {
			final Query<Data> matchQuery = Query.select(Data.class)
				.where(filter.toExpression("analyzedField"))
				.limit(2)
				.build();

			return matchQuery.search(searcher)
				.getHits();
		});
	}

	private void assertMatchReturned(final TermFilter filter) {
		final List<Data> results = search(filter);
		assertEquals(1, results.size());
		assertEquals("match", results.get(0).getId());
	}

	@Test
	public void testMatchTermFilter() {
		indexDocuments("Heart Attack", "Stroke");
		final TermFilter filter = TermFilter.match().term("Heart Attack").build();
		assertMatchReturned(filter);
	}

	@Test
	public void testExactTermFilter() {
		indexDocuments("ExactMatch", "NotExactMatch");
		final TermFilter filter = TermFilter.exact().term("ExactMatch").build();
		assertMatchReturned(filter);
	}

	@Test
	public void testParsedTermFilter() {
		indexDocuments("parsed term", "other term");
		final TermFilter filter = TermFilter.parsed().term("parse? t*").build();
		assertMatchReturned(filter);
	}

	@Test
	public void testMoreLikeThisTermFilter() {
		indexDocuments("diabetes mellitus", "hypertension");
		final TermFilter filter = TermFilter.mlt().likeTexts(List.of("diabetes mellitus")).build();
		assertMatchReturned(filter);
	}

	@Test
	public void testRegexTermFilter() {
		indexDocuments("abc123", "xyz789");
		final TermFilter filter = TermFilter.regex().term("abc.*").build();
		assertMatchReturned(filter);
	}

	@Test
	public void testWildcardTermFilter() {
		indexDocuments("wildcardTest", "noMatch");
		final TermFilter filter = TermFilter.wild().term("wildc*").build();
		assertMatchReturned(filter);
	}

}

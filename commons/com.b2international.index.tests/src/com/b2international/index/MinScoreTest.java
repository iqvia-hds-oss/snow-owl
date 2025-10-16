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
package com.b2international.index;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

import com.b2international.index.Fixtures.Data;
import com.b2international.index.query.Expressions;
import com.b2international.index.query.Query;

/**
 * @since 9.8.0
 */
public class MinScoreTest extends BaseIndexTest {

	@Override
	protected Collection<Class<?>> getTypes() {
		return List.of(Fixtures.Data.class);
	}
	
	@Ignore("Try to provide deterministic scoring in all envs")
	@Test
	public void minScore() throws Exception {
		var data1 = new Fixtures.Data(KEY1);
		data1.setAnalyzedField("Clinical findings in cerebrospinal fluid, unspecified");
		var data2 = new Fixtures.Data(KEY2);
		data2.setAnalyzedField("Clinical findings in cerebrospinal fluid");
		indexDocuments(data1, data2);
		
		var hits = search(Query.select(Data.class).where(
			Expressions.dismax(
				Expressions.matchTextAll("analyzedField.text", "unspecified").boost(10.0f),
				Expressions.matchTextAll("analyzedField.text", "cerebrospinal")
			)
		).minScore(1.0f).build());
		assertThat(hits)
			.extracting(Data::getId)
			.containsOnly(KEY1);
	}
	
}

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
package com.b2international.index.revision;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;

import org.junit.Test;

import com.b2international.index.query.Query;
import com.b2international.index.revision.RevisionFixtures.RevisionData;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @since 10.3.0
 */
public class RevisionMergeWithChangeProcessorTest extends BaseRevisionIndexTest {

	@Override
	protected Collection<Class<?>> getTypes() {
		return List.<Class<?>>of(RevisionData.class);
	}
	
	@Override
	protected void configureMapper(ObjectMapper mapper) {
		super.configureMapper(mapper);
		mapper.setSerializationInclusion(Include.NON_NULL);
	}
	
	@Test
	public void multiple_branch_rebases_no_changeprocessors() throws Exception {
		// assume we have three documents initially with initial values
		var doc1 = new RevisionData(STORAGE_KEY1, "doc1_field1_initialValue", "doc1_field2_initialValue");
		var doc2 = new RevisionData(STORAGE_KEY2, "doc2_field1_initialValue", "doc2_field2_initialValue");
		var doc3 = new RevisionData(STORAGE_KEY3, "doc3_field1_initialValue", "doc3_field2_initialValue");
		indexRevision(MAIN, doc1, doc2, doc3);
		
		// open the first two branches
		var branchA = createBranch(MAIN, "a");
		var branchB = createBranch(MAIN, "b");
		
		// apply the same change to doc1 on these two branches
		var doc1Update = new RevisionData(STORAGE_KEY1, "doc1_field1_firstUpdate", "doc1_field2_firstUpdate");
		indexRevision(branchA, doc1Update);
		indexRevision(branchB, doc1Update);
		
		// then before merging these open two additional branches and apply separate unrelated changes to doc2
		var branchC = createBranch(MAIN, "c");
		var doc2Update = new RevisionData(STORAGE_KEY2, "doc2_field1_firstUpdate", "doc2_field2_firstUpdate");
		indexRevision(branchC, doc2Update);
		
		var branchD = createBranch(MAIN, "d");
		var doc3Update = new RevisionData(STORAGE_KEY3, "doc3_field1_firstUpdate", "doc3_field2_firstUpdate");
		indexRevision(branchD, doc3Update);
		
		// then start the merge sync operations
		
		// omit branchA changes
		
		// promote branchB into MAIN
		squashMerge(branchB, MAIN);
		assertSingleRevisionVisible(MAIN, STORAGE_KEY1);

		// synchronize branch D new changes and make sure we did not get any duplication
		merge(MAIN, branchD);
		assertSingleRevisionVisible(branchD, STORAGE_KEY1);
		assertSingleRevisionVisible(branchD, STORAGE_KEY3);
		
		// synchronize branch C new changes and make sure we did not get any duplication
		merge(MAIN, branchC);
		assertSingleRevisionVisible(branchC, STORAGE_KEY1);
		assertSingleRevisionVisible(branchC, STORAGE_KEY2);
		
		// promote C changes
		squashMerge(branchC, MAIN);
		assertSingleRevisionVisible(MAIN, STORAGE_KEY1);
		assertSingleRevisionVisible(MAIN, STORAGE_KEY2);
		
		// sync branch D
		merge(MAIN, branchD);
		assertSingleRevisionVisible(branchD, STORAGE_KEY1);
		assertSingleRevisionVisible(branchD, STORAGE_KEY2);
		assertSingleRevisionVisible(branchD, STORAGE_KEY3);
	}
	
	@Test
	public void multiple_branch_rebases_with_nodiff_changeprocessor() throws Exception {
		withHook(new Hooks.PreCommitHook() {
			@Override
			public void run(StagingArea staging) {
				
				// gather all changed items, but produce the same revision as it was put before in the staging area, simulating an already prepared good revision scenario, no derived changes, etc.
				for (RevisionData changedRevision : staging.getChangedRevisions(RevisionData.class).map(diff -> (RevisionData) diff.newRevision).toList()) {
					// fetch the current state from the index, rebuild the object and stage it again
					staging.stageChange(changedRevision, new RevisionData(changedRevision.getId(), changedRevision.getField1(), changedRevision.getField2()), true);
				}
				
			}
		});
		
		// run the original test scenario, but it should not break with a change processor putting back revision into commitable state but with no changes
		multiple_branch_rebases_no_changeprocessors();
	}
	
	private void assertSingleRevisionVisible(String branch, String id) {
		var hits = search(MAIN, Query.select(RevisionData.class).where(Revision.Expressions.id(id)).limit(2).build());
		assertThat(hits).hasSize(1);
	}
	
}

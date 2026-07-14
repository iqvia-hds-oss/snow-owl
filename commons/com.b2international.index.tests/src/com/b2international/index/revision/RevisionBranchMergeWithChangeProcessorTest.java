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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Collection;
import java.util.List;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import com.b2international.index.query.Query;
import com.b2international.index.revision.RevisionBranch.BranchState;
import com.b2international.index.revision.RevisionFixtures.RevisionData;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @since 10.3.0
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RevisionBranchMergeWithChangeProcessorTest extends BaseRevisionIndexTest {

	private static final RevisionData NEW_DATA = new RevisionData(STORAGE_KEY1, "field1", "field2");
	private static final RevisionData NEW_DATA2 = new RevisionData(STORAGE_KEY2, "field1", "field2");
	private static final RevisionData CHANGED_DATA = new RevisionData(STORAGE_KEY1, "field1Changed", "field2");
	
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
	public void rebase_single_empty_changeprocessors_add() throws Exception {
		registerRestageChangeProcessor();
		String a = createBranch(MAIN, "a");
		// create a revision on MAIN branch
		indexRevision(MAIN, NEW_DATA);
		// after commit on parent branch state becomes BEHIND
		assertBranchState(a, MAIN, BranchState.BEHIND);
		// do non-squash sync merge
		var commit = merge(MAIN, a);
		// commit should NOT contain commit detail information about the new revision (since there were no conflicts or anything between the two branches)
		assertThat(commit.getDetails()).isEmpty();
		// after rebase revision should be visible from branch branch
		assertNotNull(getRevision(a, RevisionData.class, STORAGE_KEY1));
		// and state should be UP_TO_DATE
		assertBranchState(a, MAIN, BranchState.UP_TO_DATE);
	}
	
	@Test
	public void rebase_single_empty_changeprocessors_change() throws Exception {
		registerRestageChangeProcessor();
		indexRevision(MAIN, NEW_DATA);
		String branch = createBranch(MAIN, "a");
		// create a revision on branch branch
		indexChange(MAIN, NEW_DATA, CHANGED_DATA);
		// do non-squash sync merge
		var commit = merge(MAIN, branch);
		// commit should NOT contain commit detail information about the changed revision (since there were no conflicts or anything between the two branches)
		assertThat(commit.getDetails()).isEmpty();
		// after merge revision should be visible from MAIN branch
		RevisionData afterRebase = getRevision(branch, RevisionData.class, STORAGE_KEY1);
		assertDocEquals(CHANGED_DATA, afterRebase);
	}
	
	@Test
	public void rebase_single_empty_changeprocessors_remove() throws Exception {
		registerRestageChangeProcessor();
		indexRevision(MAIN, NEW_DATA);
		
		String branch = createBranch(MAIN, "a");
		indexRemove(MAIN, NEW_DATA);
		// do non-squash sync merge
		var commit = merge(MAIN, branch);
		// commit should NOT contain commit detail information about the deleted revision (since there were no conflicts or anything between the two branches)
		assertThat(commit.getDetails()).isEmpty();
		
		assertNull(getRevision(branch, RevisionData.class, STORAGE_KEY1));
	}
	
	@Test
	public void rebase_single_notempty_changeprocessors_add() throws Exception {
		registerRestageChangeProcessor();
		String branch = createBranch(MAIN, "a");
		indexRevision(branch, NEW_DATA2);
		// create a revision on MAIN branch
		indexRevision(MAIN, NEW_DATA);
		// do non-squash sync merge
		var commit = merge(MAIN, branch);
		// commit should NOT contain commit detail information about the new revision (since there were no conflicts or anything between the two branches)
		assertThat(commit.getDetails()).isEmpty();
		// after rebase revision should be visible from branch branch
		assertNotNull(getRevision(branch, RevisionData.class, STORAGE_KEY1));
	}
	
	@Test
	public void rebase_single_notempty_changeprocessors_change() throws Exception {
		registerRestageChangeProcessor();
		indexRevision(MAIN, NEW_DATA);
		
		String branch = createBranch(MAIN, "a");
		// make sure branch branch has a change, so merge is to fast-forward
		indexRevision(branch, NEW_DATA2);
		
		// update revision on main
		indexChange(MAIN, NEW_DATA, CHANGED_DATA);
		// do non-squash sync merge
		var commit = merge(MAIN, branch);
		// XXX commit should NOT contain commit detail information about the changed revision (since there were no conflicts or anything between the two branches)
		assertThat(commit.getDetails()).isEmpty();
		
		// after merge revision should be visible from MAIN branch
		RevisionData afterRebase = getRevision(branch, RevisionData.class, STORAGE_KEY1);
		assertDocEquals(CHANGED_DATA, afterRebase);
	}
	
	@Test
	public void rebase_single_notempty_changeprocessors_remove() throws Exception {
		registerRestageChangeProcessor();
		indexRevision(MAIN, NEW_DATA);
		
		String branch = createBranch(MAIN, "a");
		indexRevision(branch, NEW_DATA2);
		
		// perform remove
		indexRemove(MAIN, NEW_DATA);
		// do non-squash sync merge
		var commit = merge(MAIN, branch);
		// commit should NOT contain commit detail information about the deleted revision (since there were no conflicts or anything between the two branches)
		assertThat(commit.getDetails()).isEmpty();
		
		assertNull(getRevision(branch, RevisionData.class, STORAGE_KEY1));
	}
	
	@Test
	public void rebase_multiple_non_empty_no_changeprocessors() throws Exception {
		// assume we have three documents initially with initial values
		var doc1 = new RevisionData(STORAGE_KEY1, "doc1_field1_initialValue", "doc1_field2_initialValue");
		var doc2 = new RevisionData(STORAGE_KEY2, "doc2_field1_initialValue", "doc2_field2_initialValue");
		var doc3 = new RevisionData(STORAGE_KEY3, "doc3_field1_initialValue", "doc3_field2_initialValue");
		indexRevision(MAIN, doc1, doc2, doc3);
		
		// open the first branch
		var branchA = createBranch(MAIN, "a");
		
		// apply a change to doc1
		var doc1Update = new RevisionData(STORAGE_KEY1, "doc1_field1_firstUpdate", "doc1_field2_firstUpdate");
		indexRevision(branchA, doc1Update);
		
		// then before merging it, apply separate unrelated changes to doc2 and doc3 on two separate branches
		var branchB = createBranch(MAIN, "c");
		var doc2Update = new RevisionData(STORAGE_KEY2, "doc2_field1_firstUpdate", "doc2_field2_firstUpdate");
		indexRevision(branchB, doc2Update);
		
		var branchC = createBranch(MAIN, "d");
		var doc3Update = new RevisionData(STORAGE_KEY3, "doc3_field1_firstUpdate", "doc3_field2_firstUpdate");
		indexRevision(branchC, doc3Update);
		
		// then start the merge sync operations
		
		// promote branchA into MAIN
		squashMerge(branchA, MAIN);
		assertSingleRevisionVisible(MAIN, STORAGE_KEY1);

		// synchronize branch C new changes and make sure we did not get any duplication
		merge(MAIN, branchC);
		assertSingleRevisionVisible(branchC, STORAGE_KEY1);
		assertSingleRevisionVisible(branchC, STORAGE_KEY3);
		
		// synchronize branch B new changes and make sure we did not get any duplication
		merge(MAIN, branchB);
		assertSingleRevisionVisible(branchB, STORAGE_KEY1);
		assertSingleRevisionVisible(branchB, STORAGE_KEY2);
		
		// promote B changes
		squashMerge(branchB, MAIN);
		assertSingleRevisionVisible(MAIN, STORAGE_KEY1);
		assertSingleRevisionVisible(MAIN, STORAGE_KEY2);
		
		// sync branch C (this generates a state where revision duplication can be observed for doc1)
		merge(MAIN, branchC);
		assertSingleRevisionVisible(branchC, STORAGE_KEY1);
		assertSingleRevisionVisible(branchC, STORAGE_KEY2);
		assertSingleRevisionVisible(branchC, STORAGE_KEY3);
	}
	
	@Test
	public void multiple_branch_rebases_with_restage_changeprocessor() throws Exception {
		registerRestageChangeProcessor();
		// run the original test scenario, but it should not break with a change processor putting back revision into commitable state but with no changes
		rebase_multiple_non_empty_no_changeprocessors();
	}

	private void registerRestageChangeProcessor() {
		// gather all staged items and restage them with commit=true simulating an already prepared good revision scenario, no derived changes, etc.
		withHook(new Hooks.PreCommitHook() {
			@Override
			public void run(StagingArea staging) {
				
				for (RevisionData newRevision : staging.getNewObjects(RevisionData.class).toList()) {
					staging.stageNew(newRevision, true);
				}
				
				for (RevisionData changedRevision : staging.getChangedRevisions(RevisionData.class).map(diff -> (RevisionData) diff.newRevision).toList()) {
					staging.stageChange(changedRevision, new RevisionData.Builder(changedRevision).build(), true);
				}
				
				for (RevisionData removedRevision : staging.getRemovedObjects(RevisionData.class).toList()) {
					staging.stageRemove(removedRevision, true);
				}
				
			}
		});
	}
	
	private void assertSingleRevisionVisible(String branch, String id) {
		var hits = search(branch, Query.select(RevisionData.class).where(Revision.Expressions.id(id)).limit(2).build());
		assertThat(hits).hasSize(1);
	}
	
}

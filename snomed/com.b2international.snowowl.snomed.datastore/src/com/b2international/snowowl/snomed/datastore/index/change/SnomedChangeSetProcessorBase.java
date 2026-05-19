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
package com.b2international.snowowl.snomed.datastore.index.change;

import java.util.Collection;

import com.b2international.snowowl.core.repository.ChangeSetProcessorBase;
import com.b2international.snowowl.snomed.datastore.index.entry.SnomedComponentDocument;
import com.b2international.snowowl.snomed.datastore.index.refset.RefSetMemberChange;
import com.b2international.snowowl.snomed.datastore.index.refset.RefSetMemberChange.MemberChangeKind;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

/**
 * @since 10.2.0
 */
public abstract class SnomedChangeSetProcessorBase extends ChangeSetProcessorBase {

	protected SnomedChangeSetProcessorBase(String description) {
		super(description);
	}

	protected final void updateReferenceSetMemberships(Collection<RefSetMemberChange> memberChanges, SnomedComponentDocument cleanRevision, SnomedComponentDocument.Builder<?, ?> doc) {
		final Collection<String> currentMemberOf = cleanRevision == null ? null : cleanRevision.getMemberOf();
		final Collection<String> currentActiveMemberOf = cleanRevision == null ? null : cleanRevision.getActiveMemberOf();
		
		// XXX allow null fields to be supplied (both the revision or the tracking fields on it), but ensure we always have a final valid non-null collection written to the index 
		// use multisets to keep track of multiple membership properly 
		final Multiset<String> newMemberOf = currentMemberOf == null ? HashMultiset.create() : HashMultiset.create(currentMemberOf);
		final Multiset<String> newActiveMemberOf = currentActiveMemberOf == null ? HashMultiset.create() : HashMultiset.create(currentActiveMemberOf);
		
		memberChanges
			.stream()
			.filter(c -> c.getChangeKind() == MemberChangeKind.ADDED)
			.forEach(change -> {
				if (change.isActive()) {
					newActiveMemberOf.add(change.getRefSetId());
				}
				newMemberOf.add(change.getRefSetId());
			});
	
		memberChanges
			.stream()
			.filter(c -> c.getChangeKind() == MemberChangeKind.CHANGED)
			.forEach(change -> {
				// if the new state is active, then add it to the activeMemberOf otherwise remove it from that
				// this state transition won't change the memberOf field were all referring refsets are tracked
				if (change.isActive()) {
					newActiveMemberOf.add(change.getRefSetId());
				} else {
					newActiveMemberOf.remove(change.getRefSetId());
				}
			});
	
		memberChanges
			.stream()
			.filter(c -> c.getChangeKind() == MemberChangeKind.REMOVED)
			.forEach(change -> {
				if (change.isActive()) {
					newActiveMemberOf.remove(change.getRefSetId());
				}
				newMemberOf.remove(change.getRefSetId());
			});
		
		// re-add reference set membership fields
		doc.memberOf(newMemberOf);
		doc.activeMemberOf(newActiveMemberOf);
	}
	
}

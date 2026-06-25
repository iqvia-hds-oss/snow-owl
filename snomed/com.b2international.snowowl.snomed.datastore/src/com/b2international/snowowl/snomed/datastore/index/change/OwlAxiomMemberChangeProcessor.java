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

import static com.b2international.snowowl.snomed.core.domain.refset.SnomedRefSetType.OWL_AXIOM;

import java.io.IOException;

import org.elasticsearch.core.Set;

import com.b2international.index.revision.RevisionSearcher;
import com.b2international.index.revision.StagingArea;
import com.b2international.snowowl.core.repository.ChangeSetProcessorBase;
import com.b2international.snowowl.snomed.common.SnomedRf2Headers;
import com.b2international.snowowl.snomed.datastore.index.entry.SnomedRefSetMemberIndexEntry;
import com.b2international.snowowl.snomed.datastore.request.SnomedOWLExpressionConverter;
import com.b2international.snowowl.snomed.datastore.request.SnomedOWLExpressionConverterResult;

/**
 * @since 10.2.0
 */
public final class OwlAxiomMemberChangeProcessor extends ChangeSetProcessorBase {

	private final SnomedOWLExpressionConverter converter;

	public OwlAxiomMemberChangeProcessor(SnomedOWLExpressionConverter converter) {
		super("axiom member changes");
		this.converter = converter;
	}

	@Override
	public void process(StagingArea staging, RevisionSearcher searcher) throws IOException {
		
		staging.getNewObjects(SnomedRefSetMemberIndexEntry.class)
			.filter(member -> OWL_AXIOM.equals(member.getReferenceSetType()))
			.filter(member -> member.getOwlExpression() != null)
			.filter(member -> member.isActive())
			.forEach(member -> {
				SnomedRefSetMemberIndexEntry.Builder updatedMember = SnomedRefSetMemberIndexEntry.builder(member);
				
				SnomedOWLExpressionConverterResult converterResult = converter.toSnomedOWLRelationships(member.getReferencedComponentId(), member.getOwlExpression());
				updatedMember.classAxiomRelationships(converterResult.getClassAxiomRelationships());
				updatedMember.gciAxiomRelationships(converterResult.getGciAxiomRelationships());
				
				stageNew(updatedMember.build());
			});
		
		staging.getChangedRevisions(SnomedRefSetMemberIndexEntry.class, Set.of(SnomedRf2Headers.FIELD_OWL_EXPRESSION))
			.forEach(memberDiff -> {
				SnomedRefSetMemberIndexEntry member = (SnomedRefSetMemberIndexEntry) memberDiff.newRevision;
				
				if (member.isActive() && OWL_AXIOM.equals(member.getReferenceSetType()) && member.getOwlExpression() != null) {
					SnomedRefSetMemberIndexEntry.Builder updatedMember = SnomedRefSetMemberIndexEntry.builder(member);
					SnomedOWLExpressionConverterResult converterResult = converter.toSnomedOWLRelationships(member.getReferencedComponentId(), member.getOwlExpression());
					updatedMember.classAxiomRelationships(converterResult.getClassAxiomRelationships());
					updatedMember.gciAxiomRelationships(converterResult.getGciAxiomRelationships());
					stageChange(member, updatedMember.build());				
				}
				
		});
		
	}
}

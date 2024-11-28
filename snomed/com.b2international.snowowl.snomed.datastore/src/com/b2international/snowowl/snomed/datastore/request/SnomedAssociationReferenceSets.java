/*
 * Copyright 2024 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.snomed.datastore.request;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Set;
import java.util.stream.Collectors;

import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.domain.BranchContext;
import com.b2international.snowowl.snomed.core.domain.refset.SnomedRefSetType;
import com.b2international.snowowl.snomed.core.domain.refset.SnomedReferenceSet;

/**
 * Fetches and memoizes SNOMED CT Association Reference Set IDs.
 * Works as a cache on top of multiple SNOMED CT specific requests. Requests can access this cache via {@link ServiceProvider#service(Class)} method.
 * 
 * @since 9.5
 */
public final class SnomedAssociationReferenceSets {

	private final BranchContext context;
	private Set<String> associationReferenceSets;

	public SnomedAssociationReferenceSets(BranchContext context) {
		this.context = checkNotNull(context, "context");
	}
	
	public Set<String> get() {
		if (associationReferenceSets == null) {
			associationReferenceSets = SnomedRequests.prepareSearchRefSet()
					.all()
					.filterByActive(true)
					.filterByType(SnomedRefSetType.ASSOCIATION)
					.setFields(SnomedReferenceSet.Fields.ID)
					.build()
					.execute(context)
					.stream()
					.map(SnomedReferenceSet::getId)
					.collect(Collectors.toSet());
		}
		return associationReferenceSets;
	}
	
}

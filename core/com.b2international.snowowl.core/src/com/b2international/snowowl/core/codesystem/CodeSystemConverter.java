/*
 * Copyright 2020-2023 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.core.codesystem;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.b2international.commons.http.ExtendedLocale;
import com.b2international.commons.options.Options;
import com.b2international.index.revision.BaseRevisionBranching;
import com.b2international.index.revision.RevisionBranch;
import com.b2international.index.revision.RevisionBranch.BranchState;
import com.b2international.snowowl.core.Dependency;
import com.b2international.snowowl.core.RepositoryManager;
import com.b2international.snowowl.core.ResourceURI;
import com.b2international.snowowl.core.TerminologyResource;
import com.b2international.snowowl.core.branch.BranchInfo;
import com.b2international.snowowl.core.domain.RepositoryContext;
import com.b2international.snowowl.core.request.ResourceRequests;
import com.b2international.snowowl.core.request.resource.BaseMetadataResourceConverter;
import com.b2international.snowowl.core.uri.ResourceURIPathResolver;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * @since 7.6
 */
final class CodeSystemConverter extends BaseMetadataResourceConverter<CodeSystem, CodeSystems> {

	public CodeSystemConverter(RepositoryContext context, Options expand, List<ExtendedLocale> locales) {
		super(context, expand, locales);
	}
	
	@Override
	protected CodeSystems createCollectionResource(List<CodeSystem> results, String searchAfter, int limit, int total) {
		return new CodeSystems(results, searchAfter, limit, total);
	}
	
	@Override
	public void expand(List<CodeSystem> results) {
		super.expand(results);
		expandUpgradeOfInfo(results);
	}

	private void expandUpgradeOfInfo(List<CodeSystem> results) {
		if (!expand().containsKey(CodeSystem.Expand.UPGRADE_INFO)) {
			return;
		}
		
		final List<ResourceURI> upgradeOfURIs = results.stream()
				.filter(codeSystem -> codeSystem.getUpgradeOf() != null)
				.map(codeSystem -> codeSystem.getUpgradeOf().withoutPath())
				.collect(Collectors.toList());
		
		// nothing to expand, quit early
		if (upgradeOfURIs.isEmpty()) {
			return;
		}
		
		final List<String> upgradeOfBranches = context().service(ResourceURIPathResolver.class).resolve(context(), upgradeOfURIs);
		
		final Map<ResourceURI, String> branchesByUpgradeOf = Maps.newHashMap();
		Iterator<ResourceURI> uriIterator = upgradeOfURIs.iterator();
		Iterator<String> branchIterator = upgradeOfBranches.iterator();
		while (uriIterator.hasNext() && branchIterator.hasNext()) {
			ResourceURI uri = uriIterator.next();
			String branch = branchIterator.next();
			branchesByUpgradeOf.put(uri, branch);
		}

		for (CodeSystem cs : results) {
			
			if (cs.getUpgradeOf() == null) {
				continue;
			}
			
			Dependency upgradeOfDependency = cs.getDependency(TerminologyResource.DependencyScope.UPGRADE_OF).get();
			
			String upgradeOfCodeSystemBranchPath = branchesByUpgradeOf.get(cs.getUpgradeOf().withoutPath());
			
			if (!Strings.isNullOrEmpty(upgradeOfCodeSystemBranchPath)) {
				RepositoryContext ctx = context().service(RepositoryManager.class).getContext(cs.getToolingId());
				BaseRevisionBranching branching = ctx.service(BaseRevisionBranching.class);
				
				RevisionBranch branch = branching.getBranch(cs.getBranchPath());
				BranchState branchState = branching.getBranchState(cs.getBranchPath(), upgradeOfCodeSystemBranchPath);
				BranchInfo mainInfo = new BranchInfo(branch.getPath(), branchState, branch.getBaseTimestamp(), branch.getHeadTimestamp());
				
				List<ResourceURI> availableVersions = Lists.newArrayList();
				List<BranchInfo> versionBranchInfo = Lists.newArrayList();

				if (!cs.getUpgradeOf().isHead()) {
					long startTimestamp;
					final String upgradeOfVersionBranch = context().service(ResourceURIPathResolver.class).resolve(context(), List.of(cs.getUpgradeOf())).stream().findFirst().orElse("");

					if (!Strings.isNullOrEmpty(upgradeOfVersionBranch)) {
						startTimestamp = branching.getBranch(upgradeOfVersionBranch).getBaseTimestamp() + 1;
					} else {
						startTimestamp = Long.MIN_VALUE;
					}
					
					ResourceRequests.prepareSearchVersion()
						.all()
						.filterByResource(cs.getUpgradeOf().withoutPath())
						.build()
						.execute(context())
						.stream()
						.filter(csv -> !csv.getVersionResourceURI().isHead())
						.forEach(csv -> {
							RevisionBranch versionBranch = branching.getBranch(csv.getBranchPath());
							
							if (versionBranch.getParentPath().equals(upgradeOfCodeSystemBranchPath)) {
								
								if (versionBranch.getBaseTimestamp() > startTimestamp) {
									BranchState versionBranchState = branching.getBranchState(cs.getBranchPath(), versionBranch.getPath());
									if (versionBranchState == BranchState.BEHIND || versionBranchState == BranchState.DIVERGED) {
										availableVersions.add(csv.getVersionResourceURI());
									}
									
									versionBranchInfo.add(new BranchInfo(versionBranch.getPath(), versionBranchState, versionBranch.getBaseTimestamp(), versionBranch.getHeadTimestamp()));
								}
								
							}
						});
				}
				
				upgradeOfDependency.setUpgradeInfo(new UpgradeInfo(mainInfo, versionBranchInfo, availableVersions));
			}
		}
	}

}

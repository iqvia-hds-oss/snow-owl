/*
 * Copyright 2023-2024 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.core.locks;

import java.io.Serializable;
import java.util.Objects;

import com.b2international.commons.StringUtils;

/**
 * @since 9.0.0
 */
public record Lockable(String repositoryId, String branchPath) implements Serializable {

	private static final String _ALL = "all";

	public static final Lockable ALL = new Lockable(_ALL, _ALL);
	
	public Lockable(String repositoryId, String branchPath) {
		this.repositoryId = Objects.requireNonNull(repositoryId, "RepositoryId may not be null");
		this.branchPath = StringUtils.isEmpty(branchPath) ? _ALL : branchPath;
    }
	
	public boolean conflicts(final Lockable other) {
		if (this.equals(ALL) || other.equals(ALL)) {
			return true;
		}
		
		if (_ALL.equals(branchPath()) || _ALL.equals(other.branchPath())) {
			return repositoryId().equals(other.repositoryId());
		}
		
		return equals(other);
	}
	
	@Override
	public String toString() {
		if (ALL.equals(this)) {
			return "all repositories";
		} else if (_ALL.equals(branchPath)) {
			return "repository '" + repositoryId + "'";
		} else {
			return "repository '" + repositoryId + "' and branch '" + branchPath + "'";
		}
	}
}

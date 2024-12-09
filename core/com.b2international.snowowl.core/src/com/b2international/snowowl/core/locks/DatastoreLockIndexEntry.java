/*
 * Copyright 2019-2024 B2i Healthcare, https://b2ihealthcare.com
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

import static com.b2international.index.query.Expressions.exactMatch;
import static com.b2international.index.query.Expressions.matchAny;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.b2international.index.Doc;
import com.b2international.index.ID;
import com.b2international.index.migrate.DocumentMappingMigrationStrategy;
import com.b2international.index.migrate.SchemaRevision;
import com.b2international.index.query.Expression;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * @since 7.1.0
 */
@Doc(type = "lock", revisions = {
	@SchemaRevision(version = 2L, description = "Introduce contexts and timestamp field", strategy = DocumentMappingMigrationStrategy.NO_REINDEX)
})
@JsonDeserialize(builder = DatastoreLockIndexEntry.Builder.class)
public final class DatastoreLockIndexEntry implements Serializable {
	
	private static final long serialVersionUID = 2L;
	
	public static final class Fields {
		public static final String ID = "id";
		public static final String USER_ID = "userId";
		public static final String CONTEXTS = "contexts";
		public static final String REPOSITORY_ID = "repositoryId";
		public static final String BRANCH_PATH = "branchPath";
		public static final String TIMESTAMP = "timestamp";
	}
	
	public static class Expressions {

		public static Expression id(final String id) {
			return exactMatch(Fields.ID, id);
		}
		
		public static Expression ids(final Collection<String> ids) {
			return matchAny(Fields.ID, ids);
		}
		
		public static Expression userId(final String userId) {
			return exactMatch(Fields.USER_ID, userId);
		}
		
		public static Expression repositoryId(final String repositoryId) {
			return exactMatch(Fields.REPOSITORY_ID, repositoryId);
		}
		
		public static Expression branchPath(final String branchPath) {
			return exactMatch(Fields.BRANCH_PATH, branchPath);
		}
	}
	 
	public static DatastoreLockIndexEntry.Builder from(DatastoreLockIndexEntry source) {
		return builder()
			.id(source.getId())
			.userId(source.getUserId())
			.contexts(source.getContexts())
			.repositoryId(source.getRepositoryId())
			.branchPath(source.getBranchPath())
			.timestamp(source.getTimestamp());
	}
	
	public static DatastoreLockIndexEntry.Builder builder() {
		return new Builder();
	}
	
	@JsonPOJOBuilder(withPrefix="")
	public static class Builder {
		
		private String id;
		private String userId;
		private List<String> contexts = new ArrayList<>(2);
		private String repositoryId;
		private String branchPath;
		private Long timestamp;
		
		@JsonCreator
		private Builder() {
		}
		
		public Builder id(final String id) {
			this.id = id;
			return this;
		}
		
		public Builder userId(final String userId) {
			this.userId = userId;
			return this;
		}
		
		public Builder contexts(final Collection<String> contexts) {
			this.contexts.clear();
			this.contexts.addAll(contexts);
			return this;
		}
		
		public Builder addContext(final String context) {
			this.contexts.add(context);
			return this;
		}
		
		public Builder repositoryId(final String repositoryId) {
			Preconditions.checkNotNull(repositoryId);
			this.repositoryId = repositoryId;
			return this;
		}
		
		public Builder branchPath(final String branchPath) {
			this.branchPath = branchPath;
			return this;
		}
		
		public Builder timestamp(final Long timestamp) {
			this.timestamp = timestamp;
			return this;
		}
	
		@JsonSetter
		Builder description(final String description) {
			// lock documents with description value should propagate their values to contexts
			addContext(description);
			return this;
		}
		
		@JsonSetter
		Builder parentDescription(final String parentDescription) {
			// lock documents with a parentDescription value should propagate their values to contexts
			addContext(parentDescription);
			return this;
		}
		
		public DatastoreLockIndexEntry build() {
			return new DatastoreLockIndexEntry(id, userId, contexts, repositoryId, branchPath, timestamp);
		}
	}
	
	@ID
	private final String id;
	private final String userId;
	private final String repositoryId;
	private final String branchPath;
	private final Long timestamp;
	private final List<String> contexts;

	// Only left in for backwards compatibility reasons
	@Deprecated
	private final String description = null;

	// Only left in for backwards compatibility reasons
	@Deprecated
	private final String parentDescription = null;
	
	
	private DatastoreLockIndexEntry(
		final String id, 
		final String userId, 
		final List<String> contexts, 
		final String repositoryId, 
		final String branchPath, 
		final Long timestamp
	) {
		this.id = id;
		this.userId = userId;
		this.contexts = ImmutableList.copyOf(contexts);
		this.repositoryId = repositoryId;
		this.branchPath = branchPath;
		this.timestamp = timestamp;
	}
	
	public String getId() {
		return id;
	}
	
	public String getUserId() {
		return userId;
	}
	
	public List<String> getContexts() {
		return contexts;
	}
	
	public String getRepositoryId() {
		return repositoryId;
	}
	
	public String getBranchPath() {
		return branchPath;
	}
	
	public Long getTimestamp() {
		return timestamp;
	}
	
	@Override
	public int hashCode() {
		return Objects.hash(id, contexts, branchPath, repositoryId, userId, timestamp);
	}
	
	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		
		if (other == null) {
			return false;
		}
		
		if (!(other instanceof DatastoreLockIndexEntry)) {
			return false;
		}
		
		final DatastoreLockIndexEntry otherEntry = (DatastoreLockIndexEntry) other;
		return Objects.equals(id, otherEntry.getId()) 
			&& Objects.equals(userId, otherEntry.getUserId())
			&& Objects.equals(contexts, otherEntry.getContexts())
			&& Objects.equals(repositoryId, otherEntry.getRepositoryId())
			&& Objects.equals(branchPath, otherEntry.getBranchPath())
			&& Objects.equals(timestamp, otherEntry.getTimestamp());
	}
	
	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
			.add("id", id)
			.add("userId", userId)
			.add("contexts", contexts)
			.add("repositoryId", repositoryId)
			.add("branchPath", branchPath)
			.add("timestamp", timestamp)
			.toString();
	}
}

/*
 * Copyright 2011-2026 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.snomed.datastore.index.entry;

import static com.b2international.index.query.Expressions.exactMatch;
import static com.b2international.index.query.Expressions.matchAny;

import java.util.Collection;
import java.util.List;

import com.b2international.index.query.Expression;
import com.b2international.snowowl.snomed.cis.SnomedIdentifiers;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.google.common.base.MoreObjects.ToStringHelper;
import com.google.common.base.Strings;

/**
 * @since 4.7
 */
public abstract class SnomedComponentDocument extends SnomedDocument {

	public static abstract class Expressions extends SnomedDocument.Expressions {
		
		protected Expressions() {}
		
		public static final Expression namespace(String namespace) {
			return exactMatch(Fields.NAMESPACE, namespace);
		}
		
		public static final Expression namespaces(Iterable<String> namespaces) {
			return matchAny(Fields.NAMESPACE, namespaces);
		}
		
		public static final Expression memberOf(String refsetId) {
			return exactMatch(Fields.MEMBER_OF, refsetId);
		}
		
		public static final Expression memberOf(Iterable<String> refsetIds) {
			return matchAny(Fields.MEMBER_OF, refsetIds);
		}
		
		public static final Expression activeMemberOf(String refsetId) {
			return exactMatch(Fields.ACTIVE_MEMBER_OF, refsetId);
		}
		
		public static final Expression activeMemberOf(Iterable<String> refsetIds) {
			return matchAny(Fields.ACTIVE_MEMBER_OF, refsetIds);
		}
		
	}

	public static class Fields extends SnomedDocument.Fields {
		public static final String NAMESPACE = "namespace";
		public static final String MEMBER_OF = "memberOf";
		public static final String ACTIVE_MEMBER_OF = "activeMemberOf";
	}
	
	public static abstract class Builder<B extends Builder<B, T>, T extends SnomedComponentDocument> extends SnomedDocument.Builder<B, T> {
		
		protected List<String> memberOf;
		protected List<String> activeMemberOf;

		@Override
		public B id(String id) {
			return super.id(id);
		}
		
		public B namespace(String namespace) {
			return getSelf();
		}
		
		@JsonIgnore
		public B activeMemberOf(Collection<String> refsetIds) {
			return activeMemberOf(refsetIds == null ? null : List.copyOf(refsetIds));
		}
		
		@JsonSetter
		public B activeMemberOf(List<String> refsetIds) {
			this.activeMemberOf = refsetIds;
			return getSelf();
		}
		
		@JsonIgnore
		public B memberOf(Collection<String> refsetIds) {
			return memberOf(refsetIds == null ? null : List.copyOf(refsetIds));
		}
		
		@JsonSetter
		public B memberOf(List<String> refsetIds) {
			this.memberOf = refsetIds;
			return getSelf();
		}
		
	}
	
	private final String namespace;
	private final List<String> memberOf;
	private final List<String> activeMemberOf;
	
	SnomedComponentDocument(String id, 
			String iconId, 
			String moduleId,
			Boolean released, 
			Boolean active,
			Long effectiveTime,
			List<String> memberOf,
			List<String> activeMemberOf) {
		super(id, iconId, moduleId, released, active, effectiveTime);
		this.namespace = !Strings.isNullOrEmpty(id) ? SnomedIdentifiers.getNamespace(id) : null;
		this.memberOf = memberOf;
		this.activeMemberOf = activeMemberOf;
	}
	
	public final String getNamespace() {
		return namespace;
	}

	public List<String> getMemberOf() {
		return memberOf;
	}
	
	public List<String> getActiveMemberOf() {
		return activeMemberOf;
	}
	
	@Override
	protected ToStringHelper doToString() {
		return super.doToString()
				.add("namespace", namespace)
				.add("memberOf", memberOf)
				.add("activeMemberOf", activeMemberOf);
	}

}

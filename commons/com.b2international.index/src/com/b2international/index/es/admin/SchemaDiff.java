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
package com.b2international.index.es.admin;

import java.util.SortedSet;

import com.b2international.index.mapping.DocumentMapping;
import com.b2international.index.util.JsonDiff;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;

/**
 * @since 10.2.0
 */
public final class SchemaDiff {

	private SortedSet<String> compatibleChanges = Sets.newTreeSet();
	private SortedSet<String> incompatibleChanges = Sets.newTreeSet();
	
	private SchemaDiff(JsonNode oldSchema, JsonNode newSchema) {
		JsonDiff.diff(oldSchema, newSchema).forEach(change -> {
			
			// ignore _meta changes
			if (change.getFieldPath().startsWith(DocumentMapping._META)) {
				return;
			}
			
			if (change.isAdd()) {
				
				// XXX object type is the default type, so if the current mapping does not contain this node, we shouldn't trigger an update
				if (change.getFieldPath().endsWith("/type") && "object".equals(change.serializeValue())) {
					return;
				}
				
				compatibleChanges.add(change.getFieldPath());
				
			} else if (change.isMove() || change.isReplace()) {
				incompatibleChanges.add(change.getFieldPath());
			} else if (change.isRemove()) {
				
				// XXX while remove is bad it is hard to detect true incompatibility where we try to support dynamic fields (like Maps)
				// throw the incompatibility error only when a root field is being reported, not a nested property under the root property
				if (change.getFieldPath().contains("/properties")) {
					return;
				}
				
				incompatibleChanges.add(change.getFieldPath());
			}
			
		});
	}
	
	public SortedSet<String> getCompatibleChanges() {
		return compatibleChanges;
	}
	
	public SortedSet<String> getIncompatibleChanges() {
		return incompatibleChanges;
	}
	
	public static SchemaDiff diff(JsonNode oldSchema, JsonNode newSchema) {
		return new SchemaDiff(oldSchema, newSchema);
	}
	
}

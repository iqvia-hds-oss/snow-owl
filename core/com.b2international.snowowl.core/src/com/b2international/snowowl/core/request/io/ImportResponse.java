/*
 * Copyright 2020-2026 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.core.request.io;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.b2international.commons.CompareUtils;
import com.b2international.commons.collections.Collections3;
import com.b2international.snowowl.core.uri.ComponentURI;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;

/**
 * @since 7.12
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ImportResponse implements Serializable {

	private static final long serialVersionUID = 1L;
	private static final int LIMIT = 1000;
	
	private final String error;
	private final Set<ComponentURI> visitedComponents;
	private final List<ImportDefect> defects;
	private final int changeCount;
	
	@JsonCreator
	public ImportResponse(
			@JsonProperty("error") final String error, 
			@JsonProperty("visitedComponents") final Set<ComponentURI> visitedComponents, 
			@JsonProperty("defects") final List<ImportDefect> defects,
			@JsonProperty("changeCount") final int changeCount) {
		this.error = error;
		
		Set<ComponentURI> limitedVisitedComponents = null; 
		if (!CompareUtils.isEmpty(visitedComponents)) {
			limitedVisitedComponents = visitedComponents.stream()
						.limit(LIMIT)
						.sorted(Comparator.comparing(ComponentURI::componentType)
								.thenComparing(ComponentURI::identifier))
						.collect(Collectors.toSet());
		}
		
		List<ImportDefect> limitedDefects = null;
		if (!CompareUtils.isEmpty(defects)) {
			limitedDefects = defects.size() <= LIMIT ? defects : defects.stream().limit(LIMIT).toList();
		}
				
		this.visitedComponents = limitedVisitedComponents;
		this.defects = limitedDefects;
		this.changeCount = changeCount;
	}
	
	public boolean isSuccess() {
		return Strings.isNullOrEmpty(error);
	}

	/**
	 * @return all defects registered in this response
	 */
	public List<ImportDefect> getDefects() {
		return defects;
	}
	
	@JsonIgnore
	public List<ImportDefect> getErrors() {
		return Collections3.toImmutableList(getDefects()).stream().filter(ImportDefect::isError).toList();
	}
	
	@JsonIgnore
	public List<ImportDefect> getWarnings() {
		return Collections3.toImmutableList(getDefects()).stream().filter(ImportDefect::isWarning).toList();
	}
	
	@JsonIgnore
	public List<ImportDefect> getInfos() {
		return Collections3.toImmutableList(getDefects()).stream().filter(ImportDefect::isInfo).toList();
	}
	
	public String getError() {
		return error;
	}
	
	public Set<ComponentURI> getVisitedComponents() {
		return visitedComponents;
	}
	
	public int getChangeCount() {
		return changeCount;
	}

	public static ImportResponse error(String error) {
		return new ImportResponse(error, Set.of(), List.of(), 0);
	}
	
	public static ImportResponse success() {
		return new ImportResponse(null, Set.of(), List.of(), 0);
	}

	public static ImportResponse success(Set<ComponentURI> visitedComponents) {
		return success(visitedComponents, List.of());
	}
	
	public static ImportResponse success(Set<ComponentURI> visitedComponents, List<ImportDefect> defects) {
		return new ImportResponse(null, visitedComponents, defects, visitedComponents.size());
	}
	
	public static ImportResponse success(Set<ComponentURI> visitedComponents, List<ImportDefect> defects, int changeCount) {
		return new ImportResponse(null, visitedComponents, defects, changeCount);
	}
	
	public static ImportResponse defects(List<ImportDefect> defects) {
		return new ImportResponse(String.format("There are '%s' issues with the import file.", defects.size()), Set.of(), defects, 0);
	}

	@Override
	public int hashCode() {
		return Objects.hash(defects, error, visitedComponents, changeCount);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		ImportResponse other = (ImportResponse) obj;
		return Objects.equals(defects, other.defects) 
				&& Objects.equals(error, other.error)
				&& Objects.equals(visitedComponents, other.visitedComponents)
				&& changeCount == other.changeCount;
	}
}

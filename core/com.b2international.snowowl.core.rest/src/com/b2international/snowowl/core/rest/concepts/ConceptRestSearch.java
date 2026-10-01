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
package com.b2international.snowowl.core.rest.concepts;

import java.util.List;

import com.b2international.snowowl.core.rest.domain.ObjectRestSearch;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @since 10.4.0
 */
public class ConceptRestSearch extends ObjectRestSearch {

	@Parameter(description = "The concept status to match")
	private Boolean active;
	
	@Parameter(description = "The code system identifier(s) or versioned URI(s) containing the concepts")
	private List<String> codeSystem;
	
	@Parameter(description = "The concept term to match")
	private String term;
	
	@Parameter(description = "The query expression to match in the target code system's query language")
	private String query;
	
	@Parameter(description = "The concept parent to match")
	private List<String> parent;
	
	@Parameter(description = "The concept ancestor to match (direct or indirect parents)")
	private List<String> ancestor;
	
	@Parameter(description = "The preferred term display in case of SNOMED CT", 
		example = "PT", 
		schema = @Schema(allowableValues = { "FSN", "PT" }, 
		defaultValue = "PT")
	)
	private String preferredDisplay = "PT";
	
	public Boolean getActive() {
		return active;
	}
	
	public List<String> getCodeSystem() {
		return codeSystem;
	}
	
	public String getTerm() {
		return term;
	}
	
	public String getQuery() {
		return query;
	}
	
	public List<String> getParent() {
		return parent;
	}

	public List<String> getAncestor() {
		return ancestor;
	}

	public String getPreferredDisplay() {
		return preferredDisplay;
	}
	
	public void setActive(Boolean active) {
		this.active = active;
	}
	
	public void setCodeSystem(List<String> codeSystem) {
		this.codeSystem = codeSystem;
	}
	
	public void setTerm(String term) {
		this.term = term;
	}
	
	public void setQuery(String query) {
		this.query = query;
	}
	
	public void setParent(List<String> parent) {
		this.parent = parent;
	}

	public void setAncestor(List<String> ancestor) {
		this.ancestor = ancestor;
	}

	public void setPreferredDisplay(String preferredDisplay) {
		this.preferredDisplay = preferredDisplay;
	}
}
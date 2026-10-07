/*
 * Copyright 2023 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.core.compare;

import static com.google.common.base.Preconditions.checkArgument;

import com.b2international.commons.StringUtils;
import com.b2international.snowowl.core.ResourceURI;

/**
 * @since 9.0.0
 */
public record AnalysisCompareResultItem(
	String id, 
	String label, 
	String iconId, 
	AnalysisCompareChangeKind changeKind, 
	Boolean auxiliary, 
	ResourceURI codeSystem
) {
	public static class Builder {
		private String id;
		private String label;
		private String iconId;
		private AnalysisCompareChangeKind changeKind;
		private Boolean auxiliary = Boolean.FALSE; // Former "short constructors" defaulted to false
		private ResourceURI codeSystem;

		public Builder id(final String id) {
			this.id = id;
			return this;
		}

		public Builder label(final String label) {
			this.label = label;
			return this;
		}

		public Builder iconId(final String iconId) {
			this.iconId = iconId;
			return this;
		}

		public Builder changeKind(final AnalysisCompareChangeKind changeKind) {
			this.changeKind = changeKind;
			return this;
		}

		public Builder auxiliary(final Boolean auxiliary) {
			this.auxiliary = auxiliary;
			return this;
		}

		public Builder codeSystem(final ResourceURI codeSystem) {
			this.codeSystem = codeSystem;
			return this;
		}

		public AnalysisCompareResultItem build() {
			return new AnalysisCompareResultItem(id, label, iconId, changeKind, auxiliary, codeSystem);
		}
	}
	
	public static Builder builder() {
		return new Builder();
	}
	
	public AnalysisCompareResultItem {
		checkArgument(!StringUtils.isEmpty(id), "id cannot be null or empty");
		checkArgument(changeKind != null, "changeKind cannot be null");
	}
}

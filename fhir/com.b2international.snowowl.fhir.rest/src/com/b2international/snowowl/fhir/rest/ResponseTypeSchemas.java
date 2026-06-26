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
package com.b2international.snowowl.fhir.rest;

import java.util.List;

/**
 * @since 10.2
 */
public abstract class ResponseTypeSchemas {
	
	/**
	 * Schema describing FHIR Operation Outcome.
	 * 
	 * @see <a href="https://hl7.org/fhir/operationoutcome.html">FHIR documentation: OperationOutcome</a>
	 * 
	 * @since 10.2
	 */
	public interface OperationOutcome {
		public interface Details {
			public String getText();
		}
		
		public interface Issue {
			public String getSeverity();
			public String getCode();
			public Details getDetails();
			public String getDiagnostics();
			public List<String> getExpression();
		}
		
		public String getResourceType();
		public List<Issue> getIssue();
	}
}

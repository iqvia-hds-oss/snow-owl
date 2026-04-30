/*
 * Copyright 2022-2026 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.fhir.core.request;

/**
 * @since 8.6.0
 */
public class FhirResourceUpdateResult {
	
	public enum Action {
		CREATED, 
		UPDATED,
		SKIPPED
	}
	
	private Action action;
	private String id;
	
	public FhirResourceUpdateResult(Action action, String id) {
		this.action = action;
		this.id = id;
	}

	public Action getAction() {
		return action;
	}
	
	public String getId() {
		return id;
	}
	
	public boolean isCreated() {
		return Action.CREATED == action;
	}
	
	public boolean isUpdated() {
		return Action.UPDATED == action;
	}
	public boolean isSkipped() {
		return Action.SKIPPED == action;
	}

	public static FhirResourceUpdateResult created(String resourceId) {
		return new FhirResourceUpdateResult(Action.CREATED, resourceId);
	}
	
	public static FhirResourceUpdateResult updated(String resourceId) {
		return new FhirResourceUpdateResult(Action.UPDATED, resourceId);
	}
	
	public static FhirResourceUpdateResult skipped(String resourceId) {
		return new FhirResourceUpdateResult(Action.SKIPPED, resourceId);
	}
}

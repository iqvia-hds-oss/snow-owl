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
	private String message;
	private String version;
	
	public FhirResourceUpdateResult(Action action, String id, String message, String version) {
		this.action = action;
		this.id = id;
		this.message = message;
		this.version = version;
	}
	
	public Action getAction() {
		return action;
	}
	
	public String getId() {
		return id;
	}
	
	public String getMessage() {
		return message;
	}
	
	public String getVersion() {
		return version;
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
	
	public static FhirResourceUpdateResult created(String resourceId, String message, String version) {
		return new FhirResourceUpdateResult(Action.CREATED, resourceId, message, version);
	}
	
	public static FhirResourceUpdateResult updated(String resourceId, String message, String version) {
		return new FhirResourceUpdateResult(Action.UPDATED, resourceId, message, version);
	}
	
	public static FhirResourceUpdateResult skipped(String resourceId, String message) {
		return new FhirResourceUpdateResult(Action.SKIPPED, resourceId, message, null);
	}
}

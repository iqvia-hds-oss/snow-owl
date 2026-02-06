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
package com.b2international.snowowl.core.rest.io;

import java.io.Serializable;
import java.util.function.Function;

import com.b2international.commons.exceptions.ApiError;
import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.jobs.RemoteJobEntry;
import com.b2international.snowowl.core.jobs.RemoteJobState;
import com.b2international.snowowl.core.request.io.ImportResponse;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @since 10.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public final class ImportJob implements Serializable {
	private static final long serialVersionUID = 1L;
	
	private final String id;
	private final RemoteJobState status;
	private final ApiError error;
	private final ImportResponse response;
	
	@JsonCreator
	public ImportJob(
			@JsonProperty("id") final String id,
			@JsonProperty("status") final RemoteJobState status,
			@JsonProperty("error") final ApiError error,
			@JsonProperty("response") final ImportResponse response) {
		this.id = id;
		this.status = status;
		this.error = error;
		this.response = response;
	}
	
	public String getId() {
		return id;
	}
	
	public RemoteJobState getStatus() {
		return status;
	}
	
	public ApiError getError() {
		return error;
	}
	
	public ImportResponse getResponse() {
		return response;
	}
	
	public static ImportJob fromRemoteJobEntry(RemoteJobEntry job) {
		return fromRemoteJobEntry(job, (mapper) -> job.getResultAs(mapper, ImportResponse.class));
	}
	
	public static ImportJob fromRemoteJobEntry(RemoteJobEntry job, Function<ObjectMapper, ImportResponse> successMapper) {
		ApiError error = null;
		ImportResponse response = null;
		ObjectMapper mapper = ApplicationContext.getServiceForClass(ObjectMapper.class);
		if (RemoteJobState.FAILED == job.getState()) {
			error = job.getResultAs(mapper, ApiError.class);
		} else if (job.isSuccessful()) {
			response = successMapper.apply(mapper);
		}
		return new ImportJob(job.getId(), job.getState(), error, response);
	}
}

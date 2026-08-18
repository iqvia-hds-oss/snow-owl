/*
 * Copyright 2017-2024 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.core.jobs;

import java.util.Objects;
import java.util.Optional;

import org.eclipse.core.runtime.jobs.ILock;
import org.eclipse.core.runtime.jobs.Job;

import com.b2international.commons.exceptions.AlreadyExistsException;
import com.b2international.commons.exceptions.BadRequestException;
import com.b2international.commons.exceptions.ConflictException;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.events.Request;
import com.b2international.snowowl.core.id.IDs;
import com.b2international.snowowl.core.identity.User;
import com.b2international.snowowl.core.identity.request.UserRequests;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * @since 5.7
 */
final class ScheduleJobRequest implements Request<ServiceProvider, String> {

	private static final long serialVersionUID = 1L;
	
	private static final ILock SCHEDULE_LOCK = Job.getJobManager().newLock();
	
	@JsonProperty
	@NotEmpty
	private final String key;
	
	@JsonProperty
	private final String user;
	
	@JsonProperty
	@NotEmpty
	private final String description;
	
	@JsonProperty
	@NotNull
	private final Request<ServiceProvider, ?> request;
	
	private final boolean autoClean;
	
	private final boolean restart;
	
	private final boolean cached;

	private final SerializableSchedulingRule schedulingRule;

	ScheduleJobRequest(String key, String user, String description, Request<ServiceProvider, ?> request, SerializableSchedulingRule schedulingRule, boolean autoClean, boolean restart, boolean cached) {
		this.key = key;
		this.user = user;
		this.request = request;
		this.description = description;
		this.schedulingRule = schedulingRule;
		this.autoClean = autoClean;
		this.restart = restart;
		this.cached = cached;
	}
	
	@Override
	public String execute(ServiceProvider context) {
		
		if (cached && autoClean) {
			throw new BadRequestException("Automatically cleaned jobs cannot be cached.");
		}
		
		final String id = IDs.sha1(key);
		
		try {
			SCHEDULE_LOCK.acquire();
			
			final Optional<RemoteJobEntry> existingJob = JobRequests.prepareSearch()
				.one()
				.filterById(id)
				.setFields(RemoteJobEntry.Fields.ID, RemoteJobEntry.Fields.DELETED, RemoteJobEntry.Fields.STATE)
				.build()
				.execute(context)
				.first();
			
			if (existingJob.isPresent()) {
				RemoteJobEntry job = existingJob.get();

				/*
				 * Cancellation has been requested (the job is marked as deleted), but it is
				 * still running. We will have to wait it out because the job manager might send
				 * notifications with the same ID for this "previous run."
				 */
				if (job.isDeleted() && !job.isDone()) {
					throw new ConflictException("An existing job is present with the same '%s' key, but it is being cancelled. Please wait "
						+ "for the operation to complete before scheduling a new job.", key);
				}
				
				/*
				 * We know that the job is either not deleted, or it is deleted but in a
				 * terminal state. Deleted + done jobs should be indistinguishable from missing
				 * jobs, so the rest of the checks only need to be performed if the job is not
				 * deleted.
				 */
				if (!job.isDeleted()) {
					
					/*
					 * In cached mode, others can join in on tracking the progress (or look at the results) 
					 * of an existing job if it is either still running or completed without a restart request.
					 */
					if (cached && (!job.isDone() || !restart)) {
						return id;
					}
					
					/*
					 * We know that sharing job IDs is not possible and so a new job should be
					 * scheduled. If a job is still running, we cannot do so due to key/ID
					 * conflicts.
					 */
					if (!job.isDone()) {
						throw new AlreadyExistsException(String.format("Job[%s]", request.getType()), key);
					}
	
					/*
					 * The previous job is in a terminal state, but if the user did not request a
					 * restart, we cannot override the entry. Others might want to take a look at
					 * the results first.
					 */
					if (!restart) {
						throw new ConflictException("An existing job is present with the same '%s' key. Request 'restart' if the previous job can be safely overriden.", key);
					}
				}
				
				/*
				 * Fall through to (re-)scheduling the job. The existing job is in a terminal
				 * state and either restart was requested or the job is marked as deleted.
				 * 
				 * No explicit cancellation is needed as the job is in a terminal state
				 * (succeeded, canceled or failed) at this point; scheduling again will override
				 * existing job document contents.
				 */
			}
			
			User user;
			
			if (User.isSystem(this.user)) {
				// hidden system jobs
				user = User.SYSTEM;
			} else if (!Strings.isNullOrEmpty(this.user) && !Objects.equals(this.user, context.service(User.class).getUserId())) {
				// run jobs on behalf of others
				user = UserRequests.prepareGet(this.user).build().execute(context);
			} else {
				// run jobs as the current user
				user = context.service(User.class);
			}
			
			RemoteJob job = new RemoteJob(id, key, description, user, context, request, autoClean);
			job.setSystem(true);
			
			if (schedulingRule != null) {
				job.setRule(schedulingRule);
			}
			
			return context.service(RemoteJobTracker.class).schedule(job);
		} finally {
			SCHEDULE_LOCK.release();
		}
	}
	
}

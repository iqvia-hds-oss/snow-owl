/*
 * Copyright 2011-2025 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.snomed.datastore.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * SNOMED CT related application level configuration parameters.
 * 
 * @since 3.4
 */
public class SnomedCoreConfiguration {
	
	public static final String ELK_REASONER_ID = "org.semanticweb.elk.elk.reasoner.factory"; //$NON-NLS-1$
	public static final String DEFAULT_REASONER = ELK_REASONER_ID;
	public static final int DEFAULT_MAXIMUM_REASONER_COUNT = 2;
	public static final int DEFAULT_MAXIMUM_REASONER_RUNS = 1000;
	public static final long DEFAULT_CLASSIFICATION_CLEANUP_INTERVAL = 30L;
	
	@Min(1)
	@Max(3)
	private int maxReasonerCount = DEFAULT_MAXIMUM_REASONER_COUNT;
	
	@Min(1)
	@Max(1_000_000)
	private int maxReasonerRuns = DEFAULT_MAXIMUM_REASONER_RUNS;
	
	@Min(5)
	@Max(60)
	private long classificationCleanUpInterval = DEFAULT_CLASSIFICATION_CLEANUP_INTERVAL;
	
	@NotNull
	private SnomedMrcmConfig mrcmConfiguration = new SnomedMrcmConfig();
	
	/**
	 * @return the number of reasoners that are permitted to run simultaneously.
	 */
	@JsonProperty
	public int getMaxReasonerCount() {
		return maxReasonerCount;
	}
	
	/**
	 * @param maxReasonerCount the maxReasonerCount to set
	 */
	@JsonProperty
	public void setMaxReasonerCount(int maxReasonerCount) {
		this.maxReasonerCount = maxReasonerCount;
	}
	
	/**
	 * @return the number of classification run details to preserve. Details include inferred and redundant 
	 *         relationships, the list of equivalent concepts found during classification, and job metadata
	 *         (creation, start and end times, final state, requesting user). 
	 */
	@JsonProperty
	public int getMaxReasonerRuns() {
		return maxReasonerRuns;
	}
	
	@JsonProperty
	public void setMaxReasonerRuns(int maxReasonerRuns) {
		this.maxReasonerRuns = maxReasonerRuns;
	}
	
	public long getClassificationCleanUpInterval() {
		return classificationCleanUpInterval;
	}

	@JsonProperty("mrcm")
	public SnomedMrcmConfig getMrcmConfiguration() {
		return mrcmConfiguration;
	}

	@JsonProperty("mrcm")
	public void setMrcmConfiguration(SnomedMrcmConfig mrcmConfiguration) {
		this.mrcmConfiguration = mrcmConfiguration;
	}
		
}

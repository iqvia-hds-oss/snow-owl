/*
 * Copyright 2022 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.fhir.core.request.conceptmap;

import java.time.LocalDate;
import java.util.Optional;

import com.b2international.snowowl.core.ResourceURI;
import com.b2international.snowowl.core.ServiceProvider;
import com.b2international.snowowl.core.date.EffectiveTimes;
import com.b2international.snowowl.core.request.ResourceRequests;
import com.b2international.snowowl.core.version.Version;
import com.b2international.snowowl.fhir.core.exceptions.BadRequestException;

/**
 * @since 8.7.1
 */
public interface FhirWriteSupport {

	/**
	 * @since 10.1.0
	 */
	enum BusinessVersionCheckResult {

		/**
		 * Version described either by version or effective date is not present in the system, write operation can proceed
		 */
		VERSION_NOT_PRESENT,

		/**
		 * Version present in the system with the same version identifier, write operation can patch or overwrite if requested
		 */
		VERSION_EXISTS,

		/**
		 * Newer version present in the system, write operation should raise an error
		 */
		NEWER_VERSION_EXISTS
	}
	
	/**
	 * Checks whether a FHIR resource snapshot with an optional version and/or date can be imported based on the current latest version.
	 * 
	 * @param context
	 * @param resourceUri - the resource to import the version for
	 * @param newVersionToCreate - optional version identifier present in the FHIR resource
	 * @param newVersionEffectiveDate - optional date value present in the FHIR resource or date value provided during import
	 * @return a check result that describes how to proceed with the write operation
	 */
	default BusinessVersionCheckResult checkBusinessVersion(
			final ServiceProvider context,
			final ResourceURI resourceUri,
			final Optional<String> newVersionToCreate,
			final Optional<LocalDate> newVersionEffectiveDate) {
		
		return newVersionToCreate
			// check version ID presence first
			.map(versionId -> {
				var versionPresent = ResourceRequests.prepareSearchVersion()
						.setLimit(0)
						.filterByVersionId(versionId)
						.buildAsync()
						.execute(context)
						.getTotal() > 0;
				return versionPresent ? BusinessVersionCheckResult.VERSION_EXISTS : null;
			})
			// if no version id, or no version entry with this version id, we need to check if the desired effective date is earlier than the current latest version
			.orElseGet(() -> {
				
				if (newVersionEffectiveDate.isPresent()) {
					final Optional<Version> latestVersion = ResourceRequests.prepareSearchVersion()
						.one()
						.filterByResource(resourceUri)
						.sortBy("effectiveTime:desc")
						.buildAsync()
						.execute(context)
						.first();
					
					if (latestVersion.isPresent() && !latestVersion.get().getEffectiveTime().isBefore(newVersionEffectiveDate.get())) {
						return BusinessVersionCheckResult.NEWER_VERSION_EXISTS;
					}
					
				}
				
				// if there is no newer effective date or the resource just did not advertize any version information that we can rely on we can import it (version creation is up to the caller in this case)
				return BusinessVersionCheckResult.VERSION_NOT_PRESENT;
			});
	}
	
}

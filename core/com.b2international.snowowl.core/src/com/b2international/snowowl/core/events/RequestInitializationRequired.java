/*
 * Copyright 2025 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.core.events;

import com.b2international.snowowl.core.ServiceProvider;

/**
 * Marker interface for requests that require additional context aware initialization before they can be serialized into a transportable form, like JSON.
 * This is being used in for example when deserializing important parameters from a request hierarchy during remote job execution before scheduling the actual job and the execution of the request.
 * 
 * @since 9.8.0 
 */
public interface RequestInitializationRequired {

	/**
	 * Initializes the request preferably to a state where important parameters can be deserialized and persisted when needed.
	 * 
	 * @param context
	 */
	void initializeRequestContext(ServiceProvider context);
	
}

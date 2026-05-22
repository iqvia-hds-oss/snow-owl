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
package com.b2international.snowowl.core;

/**
 * @since 10.2.0
 */
public final class Role {
	
	private Role() {
		// this is a pure namespace class with constants and functions only, not intended to be instantiated
	}
	
	public static final String BASE = "base";
	public static final String NATIVE_API = "native-api";
	public static final String FHIR_API = "fhir-api";
	public static final String AUTHORING = "authoring";
	public static final String INGEST = "ingest";
	public static final String VALIDATION = "validation";
	public static final String CLASSIFICATION = "classification";
	public static final String SYNDICATION = "syndication";
	
}


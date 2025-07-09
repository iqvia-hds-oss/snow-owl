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
package com.b2international.snowowl.test.commons;

import org.junit.rules.ExternalResource;

import com.b2international.snowowl.core.plugin.ClassPathScanner;

/**
 * Simple JUnit Rule to cache and share a classpath scanner instance amongst many junit tests. 
 * 
 * @since 9.8.0
 */
public final class ClassPathScannerRule extends ExternalResource {

	private ClassPathScanner scanner;
	
	public ClassPathScannerRule() {
	}
	
	@Override
	protected void before() throws Throwable {
		this.scanner = new ClassPathScanner("com.b2international");		
	}
	
	@Override
	protected void after() {
		this.scanner = null;
	}
	
	public ClassPathScanner getScanner() {
		return scanner;
	}
	
}

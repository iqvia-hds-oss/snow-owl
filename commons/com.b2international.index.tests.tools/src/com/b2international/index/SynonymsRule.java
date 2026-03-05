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
package com.b2international.index;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.rules.ExternalResource;

/**
 * @since 7.29.0
 */
public final class SynonymsRule extends ExternalResource {

	private final List<String> synonyms;
	
	private Path synonymsFile;

	public SynonymsRule(String...synonyms) {
		this(List.of(synonyms));
	}
	
	public SynonymsRule(List<String> synonyms) {
		this.synonyms = synonyms;
	}
	
	public Path getSynonymsFile() {
		return synonymsFile;
	}
	
	@Override
	protected void before() throws Throwable {
		this.synonymsFile = Files.createTempFile(SynonymsRule.class.getSimpleName() + "synonyms", ".txt");
		Files.write(synonymsFile, synonyms);
	}
	
	@Override
	protected void after() {
		try {
			Files.deleteIfExists(this.synonymsFile);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
}

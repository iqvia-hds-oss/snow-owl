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
package com.b2international.snowowl.core.identity;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Before;

import com.b2international.commons.config.ConfigurationFactory;
import com.b2international.commons.validation.ApiValidation;
import com.b2international.snowowl.core.setup.Environment;
import com.b2international.snowowl.core.util.PlatformUtil;

/**
 * @since 9.6.0
 */
public abstract class BaseIdentityPluginTest {

	protected Path path;
	protected Environment env;

	@Before
	public void setup() {
		this.path = Paths.get("target");
		this.env = new Environment(path, path.resolve("configuration"), path.resolve("data"));
	}
	
	protected final IdentityConfiguration readConfig(String configFile) throws Exception {
		return new ConfigurationFactory<>(IdentityConfiguration.class, ApiValidation.getValidator()).build(PlatformUtil.toAbsolutePath(BaseIdentityPluginTest.class, configFile).toFile());
	}
	
}

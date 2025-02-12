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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.b2international.snowowl.core.plugin.ClassPathScanner;
import com.b2international.snowowl.core.setup.Plugin;
import com.b2international.snowowl.core.setup.Plugins;
import com.google.common.collect.ImmutableList;

/**
 * @since 9.6.0
 */
public class IdentityProviderConfigurationTest extends BaseIdentityPluginTest {

	@Before
	@Override
	public void setup() {
		super.setup();
		ClassPathScanner scanner = new ClassPathScanner("com.b2international.snowowl.core.identity.file");
		env.services().registerService(ClassPathScanner.class, scanner);
		
		List<Plugin> plugins = ImmutableList.<Plugin>builder()
				.addAll(scanner.getComponentsBySuperclass(Plugin.class))
				.build();
		
		env.services().registerService(Plugins.class, new Plugins(plugins));
	}
	
	@Test
	public void multiple_providers_with_same_type() throws Exception {
		var config = readConfig("multi_file.yml");
		
		IdentityProvider ip = new IdentityPlugin().initIdentityProvider(env, config);
		assertThat(ip).isInstanceOf(MultiIdentityProvider.class);
		assertThat(((MultiIdentityProvider) ip).getProviders()).hasSize(2);
	}
	
}

/*
 * Copyright 2018-2026 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.core.setup;

import java.util.Set;

import com.b2international.snowowl.core.Role;
import com.b2international.snowowl.core.SnowOwl;
import com.b2international.snowowl.core.config.SnowOwlConfiguration;
import com.b2international.snowowl.core.plugin.ClassPathScanner;

/**
 * @since 7.0
 */
public abstract class Plugin {

	/**
	 * Each plugin must provide at least one associated role. At least one role must be assigned to the terminology server in order for the plugin to get initialized and available runtime.
	 * Always on plugins are considered the base set, assign them to the {@link Role#BASE} value.
	 * @return
	 */
	public abstract Set<String> getRoles();
	
	/**
	 * Plug-ins can provide additional configuration types to extend the main snowowl.yml configuration file capabilities with their own
	 * configurations.
	 * 
	 * @param registry
	 */
	public void addConfigurations(ConfigurationRegistry registry) {
	}
	
	/**
	 * Initializes the plug-ins base services.
	 * 
	 * @param configuration
	 *            - Snow Owl Application configuration
	 * @param env
	 *            - the environment within this plug-in will be initialized
	 * @param scanner
	 *            - the scanner that can be used to dependency inject various implementations into services through class path scanning
	 * @throws Exception
	 */
	public void init(SnowOwlConfiguration configuration, Environment env, ClassPathScanner scanner) throws Exception {
	}

	/**
	 * Initializes application modules before running it completely. The method can use any required dependency which registered in
	 * {@link #init(SnowOwlConfiguration, Environment)}.
	 * 
	 * @param configuration
	 * @param env
	 * @param scanner
	 *            - the scanner that can be used to dependency inject various implementations into services through class path scanning
	 * @throws Exception
	 */
	public void preRun(SnowOwlConfiguration configuration, Environment env, ClassPathScanner scanner) throws Exception {
	}

	/**
	 * Invoked by {@link SnowOwl} at the end of the bootstrap process to let plug-ins initialize themselves finally before indicating that
	 * Snow Owl is ready to receive requests, data, etc.
	 * 
	 * @param configuration
	 *            - Snow Owl Application configuration
	 * @param env
	 *            - the environment
	 * @param scanner
	 *            - the scanner that can be used to dependency inject various implementations into services through class path scanning
	 * @throws Exception
	 */
	public void run(SnowOwlConfiguration configuration, Environment env, ClassPathScanner scanner) throws Exception {
	}

	/**
	 * Executed after application {@link #init(SnowOwlConfiguration, Environment)} and
	 * {@link #run(SnowOwlConfiguration, Environment, org.eclipse.core.runtime.IProgressMonitor)} methods.
	 * 
	 * @param configuration
	 * @param env
	 * @param scanner
	 *            - the scanner that can be used to dependency inject various implementations into services through class path scanning
	 * @throws Exception
	 */
	public void postRun(SnowOwlConfiguration configuration, Environment env, ClassPathScanner scanner) throws Exception {
	}
	
	@Override
	public String toString() {
		return getClass().getName();
	}

}
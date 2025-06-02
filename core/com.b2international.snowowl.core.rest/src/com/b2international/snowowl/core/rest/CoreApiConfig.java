/*
 * Copyright 2019 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.core.rest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import springfox.documentation.spring.web.plugins.Docket;

/**
 * @since 7.2
 */
@Configuration
public class CoreApiConfig extends BaseApiConfig {

	@Override
	public String getApiBaseUrl() {
		return "/admin";
	}
	
	@Bean
	public Docket coreDocs() {
		return docs(
			getApiBaseUrl(),
			"admin",
			"1.0",
			"Admin API",
			"https://b2ihealthcare.com",
			"support@b2ihealthcare.com",
			"API License",
			"https://b2ihealthcare.com",
			"This describes the resources that make up the official Snow Owl® Admin API. <br > Detailed documentation is available at the [official documentation site](https://docs.b2ihealthcare.com/snow-owl/api/core)."
		);
	}
	
}

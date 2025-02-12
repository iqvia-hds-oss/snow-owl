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
package com.b2international.snowowl.core.rest.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.Test;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.b2international.commons.json.Json;
import com.b2international.snowowl.core.date.Dates;
import com.b2international.snowowl.test.commons.rest.RestExtensions;

/**
 * @since 9.6.0
 */
public class ApiKeyApiTest {

	@Test
	public void generateApiKey_empty_body() throws Exception {
		RestExtensions.assertGenerateToken(Json.object())
		.statusCode(400)
		.body("message", equalTo("Invalid authentication credentials provided."));
	}
	
	@Test
	public void generateApiKey_username_only() throws Exception {
		RestExtensions.assertGenerateToken(Json.object(
			"username", RestExtensions.USER
		))
		.statusCode(400)
		.body("message", equalTo("Invalid authentication credentials provided."));
	}
	
	@Test
	public void generateApiKey_password_only() throws Exception {
		RestExtensions.assertGenerateToken(Json.object(
			"password", RestExtensions.PASS
		))
		.statusCode(400)
		.body("message", equalTo("Invalid authentication credentials provided."));
	}
	
	@Test
	public void generateApiKey_username_password() throws Exception {
		var token = RestExtensions.assertGenerateToken(Json.object(
			"username", RestExtensions.USER,
			"password", RestExtensions.PASS
			// expiration by default 1d
		))
		.statusCode(200)
		.body("accessToken", notNullValue())
		.extract()
		.body().<String>path("accessToken");
		
		DecodedJWT decoded = JWT.decode(token);
		assertThat(decoded.getClaim("permissions").asList(String.class)).containsOnly("*:*");
		assertThat(decoded.getExpiresAt()).isNotNull();
	}
	
	@Test
	public void generateApiKey_username_password_expiration() throws Exception {
		long expectedExpirationMin = Dates.todayGmt().toInstant().plus(2, ChronoUnit.DAYS).toEpochMilli();
		var token = RestExtensions.assertGenerateToken(Json.object(
			"username", RestExtensions.USER,
			"password", RestExtensions.PASS,
			"expiration", "3d"
		))
		.statusCode(200)
		.body("accessToken", notNullValue())
		.extract()
		.body().<String>path("accessToken");
		
		DecodedJWT decoded = JWT.decode(token);
		assertThat(decoded.getClaim("permissions").asList(String.class)).containsOnly("*:*");
		assertThat(decoded.getExpiresAt().getTime()).isGreaterThan(expectedExpirationMin);
	}
	
	@Test
	public void generateApiKey_username_password_no_expiration() throws Exception {
		var token = RestExtensions.assertGenerateToken(Json.object(
			"username", RestExtensions.USER,
			"password", RestExtensions.PASS,
			"expiration", ""
		))
		.statusCode(200)
		.body("accessToken", notNullValue())
		.extract()
		.body().<String>path("accessToken");
		
		DecodedJWT decoded = JWT.decode(token);
		assertThat(decoded.getClaim("permissions").asList(String.class)).containsOnly("*:*");
		assertThat(decoded.getExpiresAt()).isNull();
	}
	
	@Test
	public void generateApiKey_username_password_permissions() throws Exception {
		var token = RestExtensions.assertGenerateToken(Json.object(
			"username", RestExtensions.USER,
			"password", RestExtensions.PASS,
			"permissions", Json.array("read:SNOMEDCT")
		))
		.statusCode(200)
		.body("accessToken", notNullValue())
		.extract()
		.body().<String>path("accessToken");
		
		DecodedJWT decoded = JWT.decode(token);
		assertThat(decoded.getClaim("permissions").asList(String.class)).containsOnly("read:SNOMEDCT");
		assertThat(decoded.getExpiresAt()).isNotNull();
	}
	
	@Test
	public void generateApiKey_refresh_no_permissions() throws Exception {
		var nextDay = Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli();
		
		var tokenWith2SecExp = RestExtensions.assertGenerateToken(Json.object(
			"username", RestExtensions.USER,
			"password", RestExtensions.PASS,
			"expiration", "5s"
		))
		.statusCode(200)
		.body("accessToken", notNullValue())
		.extract()
		.body().<String>path("accessToken");
		
		DecodedJWT decodedExp = JWT.decode(tokenWith2SecExp);
		assertThat(decodedExp.getExpiresAt().getTime()).isLessThan(nextDay);
		assertThat(decodedExp.getClaim("permissions").asList(String.class)).containsOnly("*:*");
		
		var refreshedToken = RestExtensions.assertGenerateToken(Json.object(
			"token", tokenWith2SecExp,
			"expiration", "2d"
		))
		.statusCode(200)
		.body("accessToken", notNullValue())
		.extract()
		.body().<String>path("accessToken");
		
		DecodedJWT decodedRefreshed = JWT.decode(refreshedToken);
		assertThat(decodedRefreshed.getClaim("permissions").asList(String.class)).containsOnly("*:*");
		assertThat(decodedRefreshed.getExpiresAt().getTime()).isGreaterThan(nextDay);
	}
	
	@Test
	public void generateApiKey_refresh_diff_permissions() throws Exception {
		var firstToken = RestExtensions.assertGenerateToken(Json.object(
			"username", RestExtensions.USER,
			"password", RestExtensions.PASS
		))
		.statusCode(200)
		.body("accessToken", notNullValue())
		.extract()
		.body().<String>path("accessToken");
		
		RestExtensions.assertGenerateToken(Json.object(
			"token", firstToken,
			"permissions", Json.array("read:SNOMEDCT")
		))
		.statusCode(400)
		.body("message", equalTo("Token cannot be refreshed when permissions argument is also set."));
	}
	
	@Test
	public void generateApiKey_refresh_same_permissions_received() throws Exception {
		var firstToken = RestExtensions.assertGenerateToken(Json.object(
			"username", RestExtensions.USER,
			"password", RestExtensions.PASS,
			"permissions", Json.array("read:SNOMEDCT")
		))
		.statusCode(200)
		.body("accessToken", notNullValue())
		.extract()
		.body().<String>path("accessToken");
		
		var refreshedToken = RestExtensions.assertGenerateToken(Json.object(
			"token", firstToken
		))
		.statusCode(200)
		.body("accessToken", notNullValue())
		.extract()
		.body().<String>path("accessToken");
		
		DecodedJWT decoded = JWT.decode(refreshedToken);
		assertThat(decoded.getClaim("permissions").asList(String.class)).containsOnly("read:SNOMEDCT");
	}
	
}

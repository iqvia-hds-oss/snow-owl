/*
 * Copyright 2022-2024 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.core.identity.jwks;

import com.b2international.snowowl.core.identity.IdentityProviderConfig;
import com.b2international.snowowl.core.identity.JWTConfiguration;
import com.fasterxml.jackson.annotation.JsonTypeName;

import jakarta.validation.constraints.NotEmpty;

/**
 * @since 8.8.0
 */
@JsonTypeName(JwksIdentityProvider.TYPE)
public class JwksIdentityProviderConfig implements IdentityProviderConfig {

	@NotEmpty
	private String issuer;
	
	@NotEmpty
	private String jws;
	
	@NotEmpty
	private String jwksUrl;
	
	private String userIdClaimProperty = "sub";
	private String permissionsClaimProperty = "permissions";
	
	public String getIssuer() {
		return issuer;
	}
	
	public String getJws() {
		return jws;
	}
	
	public String getJwksUrl() {
		return jwksUrl;
	}
	
	public String getPermissionsClaimProperty() {
		return permissionsClaimProperty;
	}
	
	public String getUserIdClaimProperty() {
		return userIdClaimProperty;
	}
	
	public void setIssuer(String issuer) {
		this.issuer = issuer;
	}
	
	public void setJws(String jws) {
		this.jws = jws;
	}
	
	public void setJwksUrl(String jwksUrl) {
		this.jwksUrl = jwksUrl;
	}
	
	public void setPermissionsClaimProperty(String permissionsClaimProperty) {
		this.permissionsClaimProperty = permissionsClaimProperty;
	}
	
	public void setUserIdClaimProperty(String userIdClaimProperty) {
		this.userIdClaimProperty = userIdClaimProperty;
	}

	// Setter retained for backwards compatibility
	@Deprecated
	public void setEmailClaimProperty(String emailClaimProperty) {
		this.userIdClaimProperty = emailClaimProperty;
	}

	public JWTConfiguration toJWTConfiguration() {
		final JWTConfiguration jwtConfiguration = new JWTConfiguration();
		jwtConfiguration.setJws(jws);
		jwtConfiguration.setIssuer(issuer);
		jwtConfiguration.setUserIdClaimProperty(userIdClaimProperty);
		jwtConfiguration.setPermissionsClaimProperty(permissionsClaimProperty);
		return jwtConfiguration;
	}

}

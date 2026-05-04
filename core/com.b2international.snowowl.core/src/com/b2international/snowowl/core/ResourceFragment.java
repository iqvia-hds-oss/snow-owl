/*
 * Copyright 2021-2026 B2i Healthcare, https://b2ihealthcare.com
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

import java.util.Map;

import com.b2international.index.revision.RevisionBranchPoint;

/**
 * Common representation for both resource and version documents to simplify search and mapping logic.
 * 
 * @since 8.0
 */
public final class ResourceFragment {

	private String id;
	private String version;
	private String description;
	private String resourceType;
	private Long createdAt;
	private Long updatedAt;
	private String toolingId;
	private String url;
	private String branchPath;
	private Long effectiveTime;
	
	private String resourceDescription;
	private String title;
	private String status;
	private String contact;
	private String copyright;
	private String language;
	private String purpose;
	private String oid;
	private Map<String, Object> settings;
	
	private RevisionBranchPoint created;
	
	public final ResourceURI getResourceURI() {
		return ResourceURI.of(resourceType, id);
	}
	
	public String getId() {
		return id;
	}
	
	public String getVersion() {
		return version;
	}
	
	public Long getEffectiveTime() {
		return effectiveTime;
	}
	
	public String getDescription() {
		return description;
	}
	
	public String getResourceType() {
		return resourceType;
	}
	
	public Long getCreatedAt() {
		return createdAt;
	}
	
	public Long getUpdatedAt() {
		return updatedAt;
	}
	
	public String getToolingId() {
		return toolingId;
	}
	
	public String getUrl() {
		return url;
	}
	
	public String getBranchPath() {
		return branchPath;
	}
	
	public String getResourceDescription() {
		return resourceDescription;
	}
	
	public String getTitle() {
		return title;
	}
	
	public String getStatus() {
		return status;
	}
	
	public String getContact() {
		return contact;
	}
	
	public String getCopyright() {
		return copyright;
	}
	
	public String getLanguage() {
		return language;
	}
	
	public String getPurpose() {
		return purpose;
	}
	
	public String getOid() {
		return oid;
	}
	
	public Map<String, Object> getSettings() {
		return settings;
	}
	
	public RevisionBranchPoint getCreated() {
		return created;
	}
	
	public void setId(final String id) {
		this.id = id;
	}
	
	public void setVersion(final String version) {
		this.version = version;
	}
	
	public void setDescription(final String description) {
		this.description = description;
	}
	
	public void setResourceType(final String resourceType) {
		this.resourceType = resourceType;
	}
	
	public void setCreatedAt(final Long createdAt) {
		this.createdAt = createdAt;
	}
	
	public void setUpdatedAt(final Long updatedAt) {
		this.updatedAt = updatedAt;
	}
	
	public void setToolingId(final String toolingId) {
		this.toolingId = toolingId;
	}
	
	public void setUrl(final String url) {
		this.url = url;
	}
	
	public void setBranchPath(final String branchPath) {
		this.branchPath = branchPath;
	}
	
	public void setResourceDescription(final String resourceDescription) {
		this.resourceDescription = resourceDescription;
	}
	
	public void setTitle(final String title) {
		this.title = title;
	}
	
	public void setStatus(final String status) {
		this.status = status;
	}
	
	public void setContact(final String contact) {
		this.contact = contact;
	}
	
	public void setCopyright(final String copyright) {
		this.copyright = copyright;
	}
	
	public void setLanguage(final String language) {
		this.language = language;
	}
	
	public void setPurpose(final String purpose) {
		this.purpose = purpose;
	}
	
	public void setOid(final String oid) {
		this.oid = oid;
	}
	
	public void setSettings(final Map<String, Object> settings) {
		this.settings = settings;
	}
	
	public void setCreated(final RevisionBranchPoint created) {
		this.created = created;
	}
}
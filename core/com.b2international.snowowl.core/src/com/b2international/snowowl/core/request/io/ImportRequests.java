package com.b2international.snowowl.core.request.io;

import com.b2international.snowowl.core.ResourceURI;

public class ImportRequests {
	public static final String IMPORT_PREFIX = "import-"; 
	
	public static String importJobKey(ResourceURI resourceUri) {
		return importJobKey(resourceUri.toString());
	}
	
	public static String importJobKey(String path) {
		return IMPORT_PREFIX.concat(path);
	}
}

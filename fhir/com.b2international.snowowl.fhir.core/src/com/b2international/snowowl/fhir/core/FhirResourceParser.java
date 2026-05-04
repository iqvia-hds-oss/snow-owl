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
package com.b2international.snowowl.fhir.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.hl7.fhir.convertors.factory.VersionConvertorFactory_40_50;
import org.hl7.fhir.convertors.factory.VersionConvertorFactory_43_50;
import org.hl7.fhir.exceptions.FHIRFormatError;
import org.hl7.fhir.r5.elementmodel.Manager;
import org.hl7.fhir.r5.elementmodel.Manager.FhirFormat;
import org.hl7.fhir.r5.model.Resource;
import org.hl7.fhir.r5.model.Enumerations.FHIRVersion;

import com.b2international.commons.exceptions.NotImplementedException;
import com.b2international.fhir.conv.OperationConvertor_40_50;
import com.b2international.fhir.conv.OperationConvertor_43_50;
import com.b2international.fhir.formats.XmlParser;
import com.b2international.fhir.operations.OperationParametersFactory;
import com.b2international.fhir.r5.operations.BaseParameters;

/**
 * @since 10.1.0
 */
public class FhirResourceParser {

	// Short values are only admitted as _format parameters
	public static final String FORMAT_JSON = "json";
	public static final String FORMAT_XML = "xml";
	
	private final FhirFormat fhirFormat;
	private final FHIRVersion fhirVersion;

	public FhirResourceParser(String fhirFormat, String fhirVersionValue) {
		if (fhirFormat.contains(FORMAT_JSON)) {
			this.fhirFormat = Manager.FhirFormat.JSON;
		} else if (fhirFormat.contains(FORMAT_XML)) {
			this.fhirFormat = Manager.FhirFormat.XML;
		} else {
			throw new IllegalStateException("Unsupported FHIR format: " + fhirFormat);
		}
		var fhirVersion = FHIRVersion.fromCode(fhirVersionValue);
		// ensure we always use the full three digit version variant (this should be handled during parsing so this is just a safety net here)
		switch (fhirVersion) {
		case _4_0:
			fhirVersion = FHIRVersion._4_0_1;
			break;
		case _4_3:
			fhirVersion = FHIRVersion._4_3_0;
			break;
		case _5_0:
			fhirVersion = FHIRVersion._5_0_0;
			break;
		default:
			break;
		}
		this.fhirVersion = fhirVersion;
	}

	public FhirFormat getFhirFormat() {
		return fhirFormat;
	}
	
	public FHIRVersion getFhirVersion() {
		return fhirVersion;
	}
	
	public Resource parseResource(InputStream in) throws FHIRFormatError, IOException {
		switch (fhirFormat) {
		case JSON:
			return parseResourceJson(in);
		case XML:
			return parseResourceXml(in); 
		default: 
			throw new NotImplementedException("No parser implementation found for format: " + fhirFormat);
		}
	}
	
	private Resource parseResourceJson(InputStream in) throws FHIRFormatError, IOException {
		switch (fhirVersion) {
		case _4_0_1:
			org.hl7.fhir.r4.model.Resource r4 = new org.hl7.fhir.r4.formats.JsonParser().parse(in);
			return VersionConvertorFactory_40_50.convertResource(r4);
		case _4_3_0:
			org.hl7.fhir.r4b.model.Resource r4b = new org.hl7.fhir.r4b.formats.JsonParser().parse(in);
			return VersionConvertorFactory_43_50.convertResource(r4b);
		case _5_0_0:
			org.hl7.fhir.r5.model.Resource r5 = new org.hl7.fhir.r5.formats.JsonParser().parse(in);
			return r5;
		default: 
			throw new NotImplementedException("No JSON parser implementation found for version: " + fhirVersion);
		}
	}
	
	private Resource parseResourceXml(InputStream in) throws FHIRFormatError, IOException {
		switch (fhirVersion) {
		case _4_0_1:
			org.hl7.fhir.r4.model.Resource r4 = XmlParser.parseR4(in);
			return VersionConvertorFactory_40_50.convertResource(r4);
		case _4_3_0:
			org.hl7.fhir.r4b.model.Resource r4b = XmlParser.parseR4B(in);
			return VersionConvertorFactory_43_50.convertResource(r4b);
		case _5_0_0:
			org.hl7.fhir.r5.model.Resource r5 = XmlParser.parseR5(in);
			return r5;
		default: 
			throw new NotImplementedException("No XML parser implementation found for version: " + fhirVersion);
		}
	}

	public void writeResource(ByteArrayOutputStream baos, Resource resource, boolean pretty) throws FHIRFormatError, IOException {
		switch (fhirFormat) {
		case JSON:
			writeResourceJson(baos, resource, pretty);
			break;
		case XML:
			writeResourceXml(baos, resource, pretty);
			break;
		default: 
			throw new NotImplementedException("No serializer implementation found for format: " + fhirFormat);
		}
	}
	
	private void writeResourceJson(ByteArrayOutputStream baos, Resource resource, boolean pretty) throws FHIRFormatError, IOException {
		switch (fhirVersion) {
		case _4_0_1:
			org.hl7.fhir.r4.model.Resource r4 = VersionConvertorFactory_40_50.convertResource(resource);
			new org.hl7.fhir.r4.formats.JsonParser().setOutputStyle(pretty ? org.hl7.fhir.r4.formats.IParser.OutputStyle.PRETTY : org.hl7.fhir.r4.formats.IParser.OutputStyle.NORMAL).compose(baos, r4);
			break;
		case _4_3_0:
			org.hl7.fhir.r4b.model.Resource r4b = VersionConvertorFactory_43_50.convertResource(resource);
			new org.hl7.fhir.r4b.formats.JsonParser().setOutputStyle(pretty ? org.hl7.fhir.r4b.formats.IParser.OutputStyle.PRETTY : org.hl7.fhir.r4b.formats.IParser.OutputStyle.NORMAL).compose(baos, r4b);
			break;
		case _5_0_0:
			new org.hl7.fhir.r5.formats.JsonParser().setOutputStyle(pretty ? org.hl7.fhir.r5.formats.IParser.OutputStyle.PRETTY : org.hl7.fhir.r5.formats.IParser.OutputStyle.NORMAL).compose(baos, resource);
			break;
		default: 
			throw new NotImplementedException("No JSON serializer implementation found for version: " + fhirVersion);
		}
	}
	
	private void writeResourceXml(ByteArrayOutputStream baos, Resource resource, boolean pretty) throws FHIRFormatError, IOException {
		switch (fhirVersion) {
		case _4_0_1:
			org.hl7.fhir.r4.model.Resource r4 = VersionConvertorFactory_40_50.convertResource(resource);
			XmlParser.composeR4(baos, r4, pretty);
			break;
		case _4_3_0:
			org.hl7.fhir.r4b.model.Resource r4b = VersionConvertorFactory_43_50.convertResource(resource);
			XmlParser.composeR4B(baos, r4b, pretty);
			break;
		case _5_0_0:
			XmlParser.composeR5(baos, resource, pretty);
			break;
		default: 
			throw new NotImplementedException("No XML serializer implementation found for version: " + fhirVersion);
		}
	}
	
	public void writeParameters(ByteArrayOutputStream baos, BaseParameters parameters, boolean pretty) throws FHIRFormatError, IOException {
		switch (fhirFormat) {
		case JSON:
			writeParametersJson(baos, parameters, pretty);
			break;
		case XML:
			writeParametersXml(baos, parameters, pretty);
			break;
		default: 
			throw new NotImplementedException("No serializer implementation found for format: " + fhirFormat);
		}
	}
	
	private void writeParametersJson(ByteArrayOutputStream baos, BaseParameters parameters, boolean pretty) throws FHIRFormatError, IOException {
		switch (fhirVersion) {
		case _4_0_1:
			org.hl7.fhir.r4.model.Resource r4 = OperationConvertor_40_50.convert(parameters).getParameters();
			new org.hl7.fhir.r4.formats.JsonParser().setOutputStyle(pretty ? org.hl7.fhir.r4.formats.IParser.OutputStyle.PRETTY : org.hl7.fhir.r4.formats.IParser.OutputStyle.NORMAL).compose(baos, r4);
			break;
		case _4_3_0:
			org.hl7.fhir.r4b.model.Resource r4b = OperationConvertor_43_50.convert(parameters).getParameters();
			new org.hl7.fhir.r4b.formats.JsonParser().setOutputStyle(pretty ? org.hl7.fhir.r4b.formats.IParser.OutputStyle.PRETTY : org.hl7.fhir.r4b.formats.IParser.OutputStyle.NORMAL).compose(baos, r4b);
			break;
		case _5_0_0:
			new org.hl7.fhir.r5.formats.JsonParser().setOutputStyle(pretty ? org.hl7.fhir.r5.formats.IParser.OutputStyle.PRETTY : org.hl7.fhir.r5.formats.IParser.OutputStyle.NORMAL).compose(baos, parameters.getParameters());
			break;
		default: 
			throw new NotImplementedException("No JSON serializer implementation found for version: " + fhirVersion);
		}
	}
	
	private void writeParametersXml(ByteArrayOutputStream baos, BaseParameters parameters, boolean pretty) throws FHIRFormatError, IOException {
		switch (fhirVersion) {
		case _4_0_1:
			org.hl7.fhir.r4.model.Resource r4 = OperationConvertor_40_50.convert(parameters).getParameters();
			XmlParser.composeR4(baos, r4, pretty);
			break;
		case _4_3_0:
			org.hl7.fhir.r4b.model.Resource r4b = OperationConvertor_43_50.convert(parameters).getParameters();
			XmlParser.composeR4B(baos, r4b, pretty);
			break;
		case _5_0_0:
			XmlParser.composeR5(baos, parameters.getParameters(), pretty);
			break;
		default: 
			throw new NotImplementedException("No XML serializer implementation found for version: " + fhirVersion);
		}
	}
	
	public BaseParameters parseParameters(InputStream in, OperationParametersFactory factory, boolean strict) throws FHIRFormatError, IOException {
		switch (fhirFormat) {
		case JSON:
			return parseParametersJson(in, factory, strict);
		case XML:
			return parseParametersXml(in, factory, strict); 
		default: 
			throw new NotImplementedException("No parser implementation found for format: " + fhirFormat);
		}
	}
	
	private BaseParameters parseParametersJson(InputStream in, OperationParametersFactory factory, boolean strict) throws FHIRFormatError, IOException {
		switch (fhirVersion) {
		case _4_0_1:
			org.hl7.fhir.r4.model.Parameters r4 = (org.hl7.fhir.r4.model.Parameters) new org.hl7.fhir.r4.formats.JsonParser().parse(in);
			return OperationConvertor_40_50.convert(factory.create(r4, strict));
		case _4_3_0:
			org.hl7.fhir.r4b.model.Parameters r4b = (org.hl7.fhir.r4b.model.Parameters) new org.hl7.fhir.r4b.formats.JsonParser().parse(in);
			return OperationConvertor_43_50.convert(factory.create(r4b, strict));
		case _5_0_0:
			org.hl7.fhir.r5.model.Parameters r5 = (org.hl7.fhir.r5.model.Parameters) new org.hl7.fhir.r5.formats.JsonParser().parse(in);
			return factory.create(r5, strict);
		default: 
			throw new NotImplementedException("No JSON parser implementation found for version: " + fhirVersion);
		}
	}
	
	private BaseParameters parseParametersXml(InputStream in, OperationParametersFactory factory, boolean strict) throws FHIRFormatError, IOException {
		switch (fhirVersion) {
		case _4_0_1:
			org.hl7.fhir.r4.model.Parameters r4 = (org.hl7.fhir.r4.model.Parameters) XmlParser.parseR4(in);
			return OperationConvertor_40_50.convert(factory.create(r4, strict));
		case _4_3_0:
			org.hl7.fhir.r4b.model.Parameters r4b = (org.hl7.fhir.r4b.model.Parameters) XmlParser.parseR4B(in);
			return OperationConvertor_43_50.convert(factory.create(r4b, strict));
		case _5_0_0:
			org.hl7.fhir.r5.model.Parameters r5 = (org.hl7.fhir.r5.model.Parameters) XmlParser.parseR5(in);
			return factory.create(r5, strict);
		default: 
			throw new NotImplementedException("No XML parser implementation found for version: " + fhirVersion);
		}
	}

}

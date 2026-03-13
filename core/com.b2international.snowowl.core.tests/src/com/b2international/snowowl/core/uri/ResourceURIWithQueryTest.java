/*
 * Copyright 2022-2026 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.core.uri;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import com.b2international.commons.exceptions.BadRequestException;
import com.b2international.snowowl.core.ResourceURI;
import com.b2international.snowowl.core.ResourceURIWithQuery;

/**
 * @since 8.5
 */
public class ResourceURIWithQueryTest {

	@Test(expected = BadRequestException.class)
	public void nullUri() throws Exception {
		new ResourceURIWithQuery(null);
	}
	
	@Test(expected = BadRequestException.class)
	public void emptyUri() throws Exception {
		new ResourceURIWithQuery("");
	}
	
	@Test
	public void basic_noQueryPart() throws Exception {
		ResourceURIWithQuery uri = new ResourceURIWithQuery("codesystems/SNOMEDCT");
		assertThat(uri.getResourceUri()).isEqualTo(new ResourceURI("codesystems/SNOMEDCT"));
		assertThat(uri.getQuery()).isEmpty();
	}
	
	@Test
	public void versioned_noQueryPart() throws Exception {
		ResourceURIWithQuery uri = new ResourceURIWithQuery("codesystems/SNOMEDCT/2022-07-31");
		assertThat(uri.getResourceUri()).isEqualTo(new ResourceURI("codesystems/SNOMEDCT/2022-07-31"));
		assertThat(uri.getQuery()).isEmpty();
	}
	
	@Test
	public void basic_query() throws Exception {
		ResourceURIWithQuery uri = new ResourceURIWithQuery("codesystems/SNOMEDCT?ecl=123123123|TERM|");
		assertThat(uri.getResourceUri()).isEqualTo(new ResourceURI("codesystems/SNOMEDCT"));
		assertThat(uri.getQuery()).isEqualTo("ecl=123123123|TERM|");
	}
	
	@Test
	public void versioned_query() throws Exception {
		ResourceURIWithQuery uri = new ResourceURIWithQuery("codesystems/SNOMEDCT/2022-07-31?ecl=<12312313|TERM|");
		assertThat(uri.getResourceUri()).isEqualTo(new ResourceURI("codesystems/SNOMEDCT/2022-07-31"));
		assertThat(uri.getQuery()).isEqualTo("ecl=<12312313|TERM|");
	}
	
	@Test
	public void withQuery() throws Exception {
		ResourceURIWithQuery uri = new ResourceURI("codesystems/SNOMEDCT").withQuery("ecl=12123123");
		assertThat(uri.getResourceUri()).isEqualTo(new ResourceURI("codesystems/SNOMEDCT"));
		assertThat(uri.getQueryValues().get("ecl")).containsOnly("12123123");
		assertThat(uri.getQuery()).isEqualTo("ecl=12123123");
	}
	
	@Test
	public void withQuery_ExistingQuestionMark() throws Exception {
		ResourceURIWithQuery uri = new ResourceURI("codesystems/SNOMEDCT").withQuery("?ecl=12123123");
		assertThat(uri.getResourceUri()).isEqualTo(new ResourceURI("codesystems/SNOMEDCT"));
		assertThat(uri.getQueryValues().get("ecl")).containsOnly("12123123");
		assertThat(uri.getQuery()).isEqualTo("ecl=12123123");
	}

	@Test
	public void queryValues_zeroParam() throws Exception {
		ResourceURIWithQuery uri = new ResourceURIWithQuery("codesystems/SNOMEDCT");
		assertThat(uri.getQueryValues().isEmpty()).isTrue();
	}

	@Test
	public void queryValues_singleParam() throws Exception {
		ResourceURIWithQuery uri = new ResourceURIWithQuery("codesystems/SNOMEDCT?key=value");
		assertThat(uri.getQueryValues().get("key")).containsOnly("value");
	}

	@Test
	public void queryValues_multipleParams() throws Exception {
		ResourceURIWithQuery uri = new ResourceURIWithQuery("codesystems/SNOMEDCT?key1=value1&key2=value2");
		assertThat(uri.getQueryValues().get("key1")).containsOnly("value1");
		assertThat(uri.getQueryValues().get("key2")).containsOnly("value2");
	}

	@Test
	public void queryValues_repeatingParams() throws Exception {
		ResourceURIWithQuery uri = new ResourceURIWithQuery("codesystems/SNOMEDCT?key=value1&key=value2");
		assertThat(uri.getQueryValues().get("key")).containsOnly("value1", "value2");
	}

	@Test
	public void queryValues_equalsInsideValue() throws Exception {
		ResourceURIWithQuery uri = new ResourceURIWithQuery("codesystems/SNOMEDCT?key=a=b=c");
		assertThat(uri.getQueryValues().get("key")).containsOnly("a=b=c");
	}

	@Test
	public void queryValues_equalsInsideFirstValue() throws Exception {
		ResourceURIWithQuery uri = new ResourceURIWithQuery("codesystems/SNOMEDCT?key=a=b&other=value");
		assertThat(uri.getQueryValues().get("key")).containsOnly("a=b");
		assertThat(uri.getQueryValues().get("other")).containsOnly("value");
	}

	@Test
	public void queryValues_equalsInsideSecondValue() throws Exception {
		ResourceURIWithQuery uri = new ResourceURIWithQuery("codesystems/SNOMEDCT?a=1&b=x=y");
		assertThat(uri.getQueryValues().get("a")).containsOnly("1");
		assertThat(uri.getQueryValues().get("b")).containsOnly("x=y");
	}

	@Test
	public void queryValues_emptyValue() throws Exception {
		ResourceURIWithQuery uri = new ResourceURIWithQuery("codesystems/SNOMEDCT?key=");
		assertThat(uri.getQueryValues().get("key")).containsOnly("");
	}

	@Test
	public void queryValues_emptyValueNotLastParam() throws Exception {
		ResourceURIWithQuery uri = new ResourceURIWithQuery("codesystems/SNOMEDCT?key=&other=value");
		assertThat(uri.getQueryValues().get("key")).containsOnly("");
		assertThat(uri.getQueryValues().get("other")).containsOnly("value");
	}

	@Test(expected = BadRequestException.class)
	public void queryValues_emptyKey() throws Exception {
		new ResourceURIWithQuery("codesystems/SNOMEDCT?=value").getQueryValues();
	}

	@Test(expected = BadRequestException.class)
	public void queryValues_missingValue() throws Exception {
		new ResourceURIWithQuery("codesystems/SNOMEDCT?keyonly").getQueryValues();
	}

	@Test(expected = BadRequestException.class)
	public void queryValues_missingValueSecondParam() throws Exception {
		new ResourceURIWithQuery("codesystems/SNOMEDCT?key=value&keyonly").getQueryValues();
	}

	@Test(expected = BadRequestException.class)
	public void queryValues_missingKey() throws Exception {
		new ResourceURIWithQuery("codesystems/SNOMEDCT?key=value&&other=value2").getQueryValues();
	}

	@Test(expected = BadRequestException.class)
	public void queryValues_missingKeySecondParam() throws Exception {
		new ResourceURIWithQuery("codesystems/SNOMEDCT?key=value&=other").getQueryValues();
	}

	@Test(expected = BadRequestException.class)
	public void queryValues_missingKeyLastParam() throws Exception {
		new ResourceURIWithQuery("codesystems/SNOMEDCT?key=value&").getQueryValues();
	}
	
	@Test(expected = BadRequestException.class)
	public void queryValues_spaceInKey() throws Exception {
		new ResourceURIWithQuery("codesystems/SNOMEDCT?key=value& blank=value2&other=value3").getQueryValues();
	}

}

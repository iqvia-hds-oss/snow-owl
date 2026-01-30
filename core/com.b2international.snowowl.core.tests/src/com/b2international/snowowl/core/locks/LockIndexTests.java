/*
 * Copyright 2019-2024 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.snowowl.core.locks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Field;

import org.junit.Before;
import org.junit.Test;

import com.b2international.index.BaseElasticsearchAwareTest;
import com.b2international.index.Index;
import com.b2international.index.Indexes;
import com.b2international.index.WithScore;
import com.b2international.index.mapping.Mappings;
import com.b2international.index.revision.Revision;
import com.b2international.index.util.Reflections;
import com.b2international.snowowl.core.internal.locks.DatastoreLockContextDescriptions;
import com.b2international.snowowl.core.repository.JsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @since 7.1.0
 */
public class LockIndexTests extends BaseElasticsearchAwareTest {

	private static final String USER = "test@b2ihealthcare.com";

	private Index index;
	private ObjectMapper mapper;

	@Before
	public void setup() {
		mapper = JsonSupport.getDefaultObjectMapper();
		index = Indexes.createIndex("locks", mapper, new Mappings(DatastoreLockIndexEntry.class), getIndexClientConfiguration());
		index.admin().create();
	}

	@Test
	public void indexLockEntry() {
		final String lockId = "1";

		final DatastoreLockIndexEntry lock = DatastoreLockIndexEntry.builder()
			.id(lockId)
			.userId(USER)
			.addContext(DatastoreLockContextDescriptions.CLASSIFY)
			.repositoryId("repositoryUuid")
			.branchPath("branchPath")
			.build();

		indexDocument(lock);

		final DatastoreLockIndexEntry actual = getDocument(lockId);
		assertDocEquals(lock, actual);
	}

	@Test
	public void updateLockEntry() {
		final String lockId = "2";

		final DatastoreLockIndexEntry lock = DatastoreLockIndexEntry.builder()
			.id(lockId)
			.userId(USER)
			.addContext(DatastoreLockContextDescriptions.CREATE_VERSION)
			.repositoryId("repositoryUuid")
			.branchPath("branchPath")
			.build();

		indexDocument(lock);

		final DatastoreLockIndexEntry updatedLock = DatastoreLockIndexEntry.from(lock)
			.addContext(DatastoreLockContextDescriptions.COMMIT)
			.build();

		indexDocument(updatedLock);

		final DatastoreLockIndexEntry actual = getDocument(lockId);
		assertDocEquals(updatedLock, actual);
	}

	@Test
	public void deleteLockEntry() {
		final String lockId = "3";

		final DatastoreLockIndexEntry lock = DatastoreLockIndexEntry.builder()
			.id(lockId)
			.userId(USER)
			.addContext(DatastoreLockContextDescriptions.CREATE_VERSION)
			.repositoryId("repositoryUuid")
			.branchPath("branchPath")
			.build();

		indexDocument(lock);
		deleteDocument(lockId);
		
		assertNull(getDocument(lockId));
	}

	private void indexDocument(final DatastoreLockIndexEntry doc) {
		index.write(writer -> {
			writer.put(doc);
			writer.commit();
			return null;
		});
	}

	private DatastoreLockIndexEntry getDocument(final String lockId) {
		return index.read(searcher -> searcher.get(DatastoreLockIndexEntry.class, lockId));
	}

	private void deleteDocument(final String id) {
		index.write(writer -> {
			writer.remove(DatastoreLockIndexEntry.class, id);
			writer.commit();
			return null;
		});
	}

	private void assertDocEquals(final DatastoreLockIndexEntry expected, final DatastoreLockIndexEntry actual) {
		assertNotNull("Actual document is missing from index", actual);

		for (final Field f : index.admin().getIndexMapping().getMapping(expected.getClass()).getFields()) {
			if (Revision.Fields.CREATED.equals(f.getName()) 
				|| Revision.Fields.REVISED.equals(f.getName())
				|| WithScore.SCORE.equals(f.getName())
			) {
				// skip revision fields from equality check
				continue;
			}

			assertEquals(String.format("Field '%s' should be equal", f.getName()), Reflections.getValue(expected, f), Reflections.getValue(actual, f));
		}
	}

}

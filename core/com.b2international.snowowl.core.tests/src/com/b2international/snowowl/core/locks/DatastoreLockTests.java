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
import static org.junit.Assert.assertThrows;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.b2international.commons.exceptions.LockedException;
import com.b2international.index.Index;
import com.b2international.index.Indexes;
import com.b2international.index.mapping.Mappings;
import com.b2international.snowowl.core.internal.locks.DatastoreLockContext;
import com.b2international.snowowl.core.internal.locks.DatastoreLockContextDescriptions;
import com.b2international.snowowl.core.internal.locks.Slf4jOperationLockTargetListener;
import com.b2international.snowowl.core.repository.JsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @since 7.1.0
 */
public class DatastoreLockTests {

	private static final String USER1 = "user1@b2ihealthcare.com";
	private static final String USER2 = "user2@b2ihealthcare.com";

	private static final DatastoreLockContext CONTEXT_GRANTED = new DatastoreLockContext(
		USER1, 
		DatastoreLockContextDescriptions.MAINTENANCE
	);
	
	private static final DatastoreLockContext CONTEXT_DIFFERENT_USER = new DatastoreLockContext(
		USER2, 
		DatastoreLockContextDescriptions.MAINTENANCE
	);
	
	private static final DatastoreLockContext CONTEXT_NESTED = new DatastoreLockContext(
		USER1, 
		DatastoreLockContextDescriptions.COMMIT, 
		DatastoreLockContextDescriptions.MAINTENANCE
	);
	
	private static final Lockable TARGET_ALL = Lockable.ALL;
	private static final Lockable TARGET_REPOSITORY = new Lockable("snomed", null);
	private static final Lockable TARGET_REPOSITORY_BRANCH = new Lockable("snomed", "MAIN/a/b");

	private static final long TIMEOUT = 100L;
	
	private DefaultOperationLockManager manager;

	@Before
	public void setup() {
		final ObjectMapper mapper = JsonSupport.getDefaultObjectMapper();
		final Index index = Indexes.createIndex("locks", mapper, new Mappings(DatastoreLockIndexEntry.class));
		
		manager = new DefaultOperationLockManager(index);
		manager.addLockTargetListener(new Slf4jOperationLockTargetListener());
		manager.unlockAll();
	}
	
	private void testLock(Lockable target) {
		// Take the lock
		manager.lock(CONTEXT_GRANTED, TIMEOUT, target);
		checkIfLockExists(CONTEXT_GRANTED, true, target);
		
		// Different user, same target is rejected
		assertThrows(LockedException.class, () -> manager.lock(CONTEXT_DIFFERENT_USER, TIMEOUT, target));
		checkIfLockExists(CONTEXT_DIFFERENT_USER, false, target);
		
		// Same user, improper nesting (same parent description) is also rejected
		assertThrows(LockedException.class, () -> manager.lock(CONTEXT_GRANTED, TIMEOUT, target));
		
		// Same user, proper nesting, same target is allowed
		manager.lock(CONTEXT_NESTED, TIMEOUT, TARGET_ALL);
		checkIfLockExists(CONTEXT_NESTED, true, TARGET_ALL);
		manager.unlock(CONTEXT_NESTED, TARGET_ALL);
		checkIfLockExists(CONTEXT_NESTED, false, TARGET_ALL);
		
		// Same user, proper nesting, repository target is also allowed
		manager.lock(CONTEXT_NESTED, TIMEOUT, TARGET_REPOSITORY);
		checkIfLockExists(CONTEXT_NESTED, true, TARGET_REPOSITORY);
		manager.unlock(CONTEXT_NESTED, TARGET_REPOSITORY);
		checkIfLockExists(CONTEXT_NESTED, false, TARGET_REPOSITORY);
		
		// Same user, proper nesting, repository + branch target is, yet again, allowed
		manager.lock(CONTEXT_NESTED, TIMEOUT, TARGET_REPOSITORY_BRANCH);
		checkIfLockExists(CONTEXT_NESTED, true, TARGET_REPOSITORY_BRANCH);
		manager.unlock(CONTEXT_NESTED, TARGET_REPOSITORY_BRANCH);
		checkIfLockExists(CONTEXT_NESTED, false, TARGET_REPOSITORY_BRANCH);
		
		// Release the first lock
		manager.unlock(CONTEXT_GRANTED, target);
		checkIfLockExists(CONTEXT_GRANTED, false, target);
	}
	
	@Test
	public void testLockAll() {
		testLock(TARGET_ALL);
	}
	
	@Test
	public void testLockRepository() {
		testLock(TARGET_REPOSITORY);
	}
	
	@Test
	public void testLockRepositoryBranch() {
		testLock(TARGET_REPOSITORY_BRANCH);
	}
	
	private void checkIfLockExists(DatastoreLockContext context, boolean expected, Lockable...targets) {
		final List<OperationLockInfo> locks = manager.getLocks();
		
		for (final Lockable target : targets) {
			final boolean lockExists = locks.stream()
				.filter(info -> info.getTarget().equals(target) && info.getContext().equals(context))
				.findFirst()
				.isPresent();
			
			assertEquals(expected, lockExists);
		}
	}
	
}

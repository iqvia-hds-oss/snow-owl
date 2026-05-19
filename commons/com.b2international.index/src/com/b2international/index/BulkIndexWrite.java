/*
 * Copyright 2011-2026 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 
 */
public final class BulkIndexWrite<T> implements IndexWrite<List<T>> {

	private final List<IndexWrite<T>> indexWrites;
	
	@SafeVarargs
	public BulkIndexWrite(final IndexWrite<T>... writes) {
		indexWrites = List.of(writes);
	}
	
	public BulkIndexWrite(final List<IndexWrite<T>> writes) {
		indexWrites = List.copyOf(writes);
	}
	
	@Override
	public List<T> execute(final Writer index) throws IOException {
		final List<T> results = new ArrayList<>(indexWrites.size());
		
		for (final IndexWrite<T> write : indexWrites) {
			results.add(write.execute(index));
		}
		
		return results;
	}
	
}

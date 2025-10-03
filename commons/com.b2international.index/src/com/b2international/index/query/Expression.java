/*
 * Copyright 2011-2025 B2i Healthcare, https://b2ihealthcare.com
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
package com.b2international.index.query;

/**
 * @since 4.7
 */
public interface Expression {
	
	default boolean isMatchAll() {
		return this instanceof MatchAll;
	}
	
	default boolean isMatchNone() {
		return this instanceof MatchNone;
	}

	/**
	 * @since 9.8
	 */
	default Expression boost(float boost) {
		return Expressions.boost(this, boost);
	}
	
	/**
	 * @since 9.8
	 */
	default Expression constantScore(float score) {
		return Expressions.constantScore(this, score);
	}
	
	/**
	 * @since 9.8
	 */
	default Expression saturateScores() {
		return Expressions.normalizeScores(this, 0);
	}
	
	/**
	 * @since 9.8
	 */
	default Expression saturateScores(float minScore) {
		return Expressions.normalizeScores(this, minScore);
	}
	
}
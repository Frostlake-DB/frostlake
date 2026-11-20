/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.frostlake.executor.commands;

/**
 * A DROP or a replacement refused because a network policy lists a network rule it would take away (see
 * {@link NetworkRuleAssociations}). IF EXISTS forgives a missing object and never this.
 */
final class NetworkRuleAssociationRefusal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param sentence the refusal, whole
     */
    NetworkRuleAssociationRefusal(final String sentence) {
        super(sentence);
    }
}

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
package dev.frostlake.parser;

/** Where a SHOW statement the parse could not finish stands, as {@link ShowScopeReadBackLines} reads it. */
public enum ShowStatementPlacement {
    /** A statement of its own, outside every block. */
    STANDALONE,
    /** A statement of the outermost block's own list, or of one of its exception handlers. */
    BLOCK,
    /** A statement of a block's own list, the block itself a statement of another block's own list. */
    NESTED,
    /** A statement of an IF's body. */
    CONSTRUCT,
    /** A statement of a loop's body: a WHILE's, a LOOP's or a FOR's. */
    LOOP,
    /** A statement of a CASE's branch or of a REPEAT's body. */
    BRANCH,
    /** Anywhere else. */
    OTHER
}

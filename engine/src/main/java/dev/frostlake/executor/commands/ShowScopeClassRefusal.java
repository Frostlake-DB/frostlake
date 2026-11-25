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
 * A SHOW listing's scope refused at its class name, a word that names no kind of scope: the sentence is a bare
 * syntax-error line, without the compilation error's heading, and the code and the state are 1002 / 42601 — in a
 * block and outside one alike (live-verified).
 */
public class ShowScopeClassRefusal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The error code a client reads. */
    public static final int CODE = 1002;

    /** The SQLSTATE a client reads. */
    public static final String STATE = "42601";

    /**
     * @param sentence the syntax-error line
     */
    public ShowScopeClassRefusal(final String sentence) {
        super(sentence);
    }
}

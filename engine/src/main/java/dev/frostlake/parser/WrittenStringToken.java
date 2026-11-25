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

import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Token;

/**
 * A quoted string read in place of a dollar string (see {@link DollarStringLiterals}): it reads as the quoted string,
 * and remembers the spelling it was written in, for what names a value as written.
 */
public final class WrittenStringToken extends CommonToken {

    private static final long serialVersionUID = 1L;

    private final String written;

    /**
     * @param read    the quoted string as it was read
     * @param written the dollar string as it was written
     */
    WrittenStringToken(final Token read, final String written) {
        super(read);
        this.written = written;
    }

    /** The dollar string as it was written. */
    String written() {
        return written;
    }
}

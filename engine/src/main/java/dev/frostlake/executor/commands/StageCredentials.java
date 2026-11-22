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

import dev.frostlake.executor.FileFormatClause;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.model.FileFormat;
import dev.frostlake.parser.FrostlakeParser;

import java.util.List;
import java.util.Locale;

/**
 * What a stage's CREDENTIALS let their AWS keys be (live-verified), at CREATE STAGE and at ALTER STAGE … SET alike.
 *
 * <p>A colon is no part of an AWS_KEY_ID, an AWS_SECRET_KEY or an AWS_TOKEN: the first key written with one is
 * refused by its value and under the name as written, {@code invalid value 's3://b/p' for property 'AWS_KEY_ID'},
 * whatever the stage is - one that does not exist, an internal one, one of another cloud - and after a FILE_FORMAT
 * group of the statement is judged. Any other text passes, a slash, a blank or a quote inside it included.
 *
 * <p>The stage of an S3 location then needs both of its keys filled: an AWS_KEY_ID or an AWS_SECRET_KEY that is
 * empty or blank is {@code Valid credentials are required for this stage location.}; an empty AWS_TOKEN passes.
 * The account also hands the keys of a new stage to its cloud, which may refuse them; nothing is asked of a cloud
 * here.
 */
final class StageCredentials {

    /** The refusal of an S3 stage's key left blank. */
    static final String REQUIRED = "Valid credentials are required for this stage location.";

    private static final String CREDENTIALS = "CREDENTIALS";
    private static final String AWS_KEY_ID = "AWS_KEY_ID";
    private static final String AWS_SECRET_KEY = "AWS_SECRET_KEY";
    private static final String AWS_TOKEN = "AWS_TOKEN";

    private StageCredentials() {
    }

    /**
     * The refusal of the first AWS key written with a colon.
     *
     * @param group the CREDENTIALS list, or null where the property was given no list
     * @return the refusal, or null where every key passes
     */
    private static RuntimeException malformed(final FrostlakeParser.ParenOptionListContext group) {
        if (group == null) {
            return null;
        }
        for (final FrostlakeParser.ParenOptionContext option : group.parenOption()) {
            final String name = option.optionKey().getText().toUpperCase(Locale.ROOT);
            final String given = text(option);
            if (given != null && given.indexOf(':') >= 0
                    && (AWS_KEY_ID.equals(name) || AWS_SECRET_KEY.equals(name) || AWS_TOKEN.equals(name))) {
                return new RuntimeException(SqlCompilationError.invalidValueForProperty(given,
                    option.optionKey().getText()));
            }
        }
        return null;
    }

    /**
     * Refuses an S3 stage's AWS_KEY_ID or AWS_SECRET_KEY that is empty or blank.
     *
     * @param group    the CREDENTIALS list, or null where the property was given no list
     * @param location the stage's location, or null for an internal stage
     */
    static void requireFilled(final FrostlakeParser.ParenOptionListContext group, final String location) {
        if (group == null || location == null || !location.regionMatches(true, 0, "s3", 0, 2)) {
            return;
        }
        for (final FrostlakeParser.ParenOptionContext option : group.parenOption()) {
            final String name = option.optionKey().getText().toUpperCase(Locale.ROOT);
            final String given = text(option);
            if (given != null && given.trim().isEmpty() && (AWS_KEY_ID.equals(name) || AWS_SECRET_KEY.equals(name))) {
                throw new RuntimeException(REQUIRED);
            }
        }
    }

    /** The text an option was given, or null for a value that is no text. */
    /**
     * Refuses a statement whose CREDENTIALS hold a malformed key, once its FILE_FORMAT groups are judged.
     *
     * @param credentials the statement's CREDENTIALS lists, in the order written
     * @param formats     its FILE_FORMAT lists
     */
    static void requireWellFormed(final List<FrostlakeParser.ParenOptionListContext> credentials,
                                  final List<FrostlakeParser.ParenOptionListContext> formats) {
        for (final FrostlakeParser.ParenOptionListContext group : credentials) {
            final RuntimeException refusal = malformed(group);
            if (refusal != null) {
                for (final FrostlakeParser.ParenOptionListContext format : formats) {
                    FileFormatClause.read(format, new FileFormat("WRITTEN", "CSV"), true);
                }
                throw refusal;
            }
        }
    }

    /** Whether a property's name, written as a plain word, is CREDENTIALS. */
    static boolean names(final FrostlakeParser.IdentifierContext name) {
        return name != null && CREDENTIALS.equalsIgnoreCase(name.getText());
    }

    private static String text(final FrostlakeParser.ParenOptionContext option) {
        final FrostlakeParser.CopyOptionValueContext value = option.copyOptionValue();
        return value == null || value.STRING_LITERAL() == null ? null
            : ParseTreeText.extractStringLiteral(value.STRING_LITERAL());
    }
}

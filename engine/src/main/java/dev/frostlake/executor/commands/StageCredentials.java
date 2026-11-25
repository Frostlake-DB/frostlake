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

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.parser.FrostlakeParser;
import org.antlr.v4.runtime.ParserRuleContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What a stage's CREDENTIALS may hold (live-verified), at CREATE STAGE and at ALTER STAGE … SET alike.
 *
 * <p>First the value as written, whatever the stage is - one that does not exist, an internal one, one of any cloud.
 * A value that is no property list is {@code invalid value [Not a property list: 'x'] for parameter '{1}'}, its text
 * as written, and an empty pair of parentheses the same refusal of {@code TOK_CONSTANT_LIST}. Its keys are then read
 * in the order written, each name and value as written: a name other than AWS_KEY_ID, AWS_SECRET_KEY, AWS_TOKEN,
 * AWS_ROLE and AZURE_SAS_TOKEN is {@code invalid parameter 'FOO'}, a value that is no text {@code invalid value [1]
 * for parameter 'AWS_KEY_ID'} ({@code TOK_CONSTANT_LIST} for a parenthesized one), and an AWS_KEY_ID, an
 * AWS_SECRET_KEY or an AWS_TOKEN holding a colon {@code invalid value 's3://b/p' for property 'AWS_KEY_ID'}.
 *
 * <p>Then the last CREDENTIALS the statement writes, against the stage's location, a key written twice counting once
 * with its last value:
 *
 * <ul>
 *   <li>an S3 location takes an AWS_KEY_ID with an AWS_SECRET_KEY and optionally an AWS_TOKEN, or an AWS_ROLE alone;
 *       any other set is {@code Invalid AWS external stage credentials specified. Provide only AWS_KEY_ID,
 *       AWS_SECRET_KEY, and optionally AWS_TOKEN.} An AWS_KEY_ID, an AWS_SECRET_KEY or an AWS_ROLE left blank is
 *       {@code Valid credentials are required for this stage location.}, and an AWS_ROLE that is not six fields
 *       parted by colons, its last one filled, {@code AWS_ROLE credentials are invalid.};</li>
 *   <li>an Azure location takes an AZURE_SAS_TOKEN alone, blank or not; any other set is {@code Invalid Azure
 *       credentials specified. Provide only AZURE_SAS_TOKEN or triplet of AZURE_CLIENT_ID, AZURE_CLIENT_SECRET and
 *       AZURE_TENANT_ID.};</li>
 *   <li>a GCS location takes none: an AWS_ROLE or two keys or more are {@code GCS credential is invalid}, a single
 *       other key {@code GCS service account key or access token is missing}.</li>
 * </ul>
 *
 * <p>The statement's FILE_FORMAT, DIRECTORY and ENCRYPTION lists are judged by their own shape before any of these
 * refusals; the location's own checks, an integration's lookup, the stage's name and its other properties come after.
 * The account also hands the keys of a new stage to its cloud, which may refuse them; nothing is asked of a cloud
 * here.
 */
final class StageCredentials {

    /** The refusal of an S3 stage's key left blank. */
    static final String REQUIRED = "Valid credentials are required for this stage location.";

    /** The refusal of an S3 stage's keys of another set than its own. */
    static final String AWS_SET = "Invalid AWS external stage credentials specified. Provide only AWS_KEY_ID,"
        + " AWS_SECRET_KEY, and optionally AWS_TOKEN.";

    /** The refusal of an Azure stage's keys of another set than its own. */
    static final String AZURE_SET = "Invalid Azure credentials specified. Provide only AZURE_SAS_TOKEN or triplet of"
        + " AZURE_CLIENT_ID, AZURE_CLIENT_SECRET and AZURE_TENANT_ID.";

    /** The refusal of a GCS stage's AWS_ROLE or several keys. */
    static final String GCS_INVALID = "GCS credential is invalid";

    /** The refusal of a GCS stage's single key. */
    static final String GCS_MISSING = "GCS service account key or access token is missing";

    /** The refusal of an S3 stage's AWS_ROLE of no role's shape. */
    static final String ROLE_INVALID = "AWS_ROLE credentials are invalid.";

    private static final String CREDENTIALS = "CREDENTIALS";
    private static final String AWS_KEY_ID = "AWS_KEY_ID";
    private static final String AWS_SECRET_KEY = "AWS_SECRET_KEY";
    private static final String AWS_TOKEN = "AWS_TOKEN";
    private static final String AWS_ROLE = "AWS_ROLE";
    private static final String AZURE_SAS_TOKEN = "AZURE_SAS_TOKEN";
    private static final String CONSTANT_LIST = "TOK_CONSTANT_LIST";

    /** The keys a CREDENTIALS list knows. */
    private static final Set<String> KEYS = Set.of(AWS_KEY_ID, AWS_SECRET_KEY, AWS_TOKEN, AWS_ROLE, AZURE_SAS_TOKEN);

    /** The fields an AWS_ROLE's ARN is parted into by its colons. */
    private static final int ROLE_FIELDS = 6;

    private StageCredentials() {
    }

    /**
     * The refusal a statement's CREDENTIALS earn, or null where they pass.
     *
     * @param written  the statement's CREDENTIALS values in the order written: each a property list or a value
     * @param location the stage's location, or null where it is not known or the stage is internal; the values are
     *                 then judged as written alone
     * @return the refusal, or null
     */
    static RuntimeException refusal(final List<ParserRuleContext> written, final String location) {
        for (final ParserRuleContext value : written) {
            final RuntimeException refusal = malformed(value);
            if (refusal != null) {
                return refusal;
            }
        }
        if (written.isEmpty() || location == null) {
            return null;
        }
        final String sentence = misplaced(read(written.get(written.size() - 1)), location);
        return sentence == null ? null : new RuntimeException(sentence);
    }

    /** The refusal of a value as written, or null where it passes. */
    private static RuntimeException malformed(final ParserRuleContext value) {
        if (!(value instanceof FrostlakeParser.ParenOptionListContext)) {
            return new RuntimeException(SqlCompilationError.invalidValueForParameter("Not a property list: "
                + ParseTreeText.getOriginalText(value), "{1}"));
        }
        final List<FrostlakeParser.ParenOptionContext> options = ((FrostlakeParser.ParenOptionListContext) value)
            .parenOption();
        if (options.isEmpty()) {
            return new RuntimeException(SqlCompilationError.invalidValueForParameter("Not a property list: "
                + CONSTANT_LIST, "{1}"));
        }
        for (final FrostlakeParser.ParenOptionContext option : options) {
            final String spelled = option.optionKey().getText();
            final String name = spelled.toUpperCase(Locale.ROOT);
            if (!KEYS.contains(name)) {
                return new RuntimeException(SqlCompilationError.of("invalid parameter '" + spelled + "'"));
            }
            final String given = text(option);
            if (given == null) {
                final FrostlakeParser.CopyOptionValueContext other = option.copyOptionValue();
                return new RuntimeException(SqlCompilationError.invalidValueForParameter(other != null
                    ? ParseTreeText.getOriginalText(other)
                    : option.NULL() != null && option.LPAREN() == null ? option.NULL().getText() : CONSTANT_LIST,
                    spelled));
            }
            if (given.indexOf(':') >= 0 && !AWS_ROLE.equals(name) && !AZURE_SAS_TOKEN.equals(name)) {
                return new RuntimeException(SqlCompilationError.invalidValueForProperty(given, spelled));
            }
        }
        return null;
    }

    /** A list's keys, each with its last value. */
    private static Map<String, String> read(final ParserRuleContext value) {
        final Map<String, String> keys = new LinkedHashMap<>();
        for (final FrostlakeParser.ParenOptionContext option
                : ((FrostlakeParser.ParenOptionListContext) value).parenOption()) {
            keys.put(option.optionKey().getText().toUpperCase(Locale.ROOT), text(option));
        }
        return keys;
    }

    /** The refusal of a set of keys at a location, or null where the location takes them. */
    private static String misplaced(final Map<String, String> keys, final String location) {
        final int colon = location.indexOf(':');
        final String scheme = colon <= 0 ? "" : location.substring(0, colon).toLowerCase(Locale.ROOT);
        if (scheme.startsWith("s3")) {
            return onS3(keys);
        }
        if ("azure".equals(scheme)) {
            return keys.size() == 1 && keys.containsKey(AZURE_SAS_TOKEN) ? null : SqlCompilationError.of(AZURE_SET);
        }
        if ("gcs".equals(scheme)) {
            return SqlCompilationError.of(keys.containsKey(AWS_ROLE) || keys.size() > 1 ? GCS_INVALID : GCS_MISSING);
        }
        return null;
    }

    /** The refusal of an S3 stage's keys, or null where they pass. */
    private static String onS3(final Map<String, String> keys) {
        if (keys.size() == 1 && keys.containsKey(AWS_ROLE)) {
            final String role = keys.get(AWS_ROLE);
            if (role.trim().isEmpty()) {
                return REQUIRED;
            }
            final String[] fields = role.split(":", -1);
            return fields.length == ROLE_FIELDS && !fields[ROLE_FIELDS - 1].trim().isEmpty() ? null
                : SqlCompilationError.of(ROLE_INVALID);
        }
        if (!keys.containsKey(AWS_KEY_ID) || !keys.containsKey(AWS_SECRET_KEY)
                || keys.size() > (keys.containsKey(AWS_TOKEN) ? 3 : 2)) {
            return SqlCompilationError.of(AWS_SET);
        }
        if (keys.get(AWS_KEY_ID).trim().isEmpty() || keys.get(AWS_SECRET_KEY).trim().isEmpty()) {
            return REQUIRED;
        }
        return null;
    }

    /** Whether a property's name, written as a plain word, is CREDENTIALS. */
    static boolean names(final FrostlakeParser.IdentifierContext name) {
        return name != null && CREDENTIALS.equalsIgnoreCase(name.getText());
    }

    /** The text an option was given, or null for a value that is no text. */
    private static String text(final FrostlakeParser.ParenOptionContext option) {
        final FrostlakeParser.CopyOptionValueContext value = option.copyOptionValue();
        return value == null || value.STRING_LITERAL() == null ? null
            : ParseTreeText.extractStringLiteral(value.STRING_LITERAL());
    }
}

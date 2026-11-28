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
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.UnmodelledDropKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.antlr.v4.runtime.Token;

/**
 * The properties an ALTER of a kind Frostlake models no object of, or of a class's instance, sets or unsets, judged
 * while the statement compiles, before the object is looked for (all live-verified):
 *
 * <pre>
 *   ALTER foo bar SET COMMENT = 'a', COMMENT = 'b'     duplicate property 'COMMENT';      (any list, first)
 *   ALTER foo bar SET X = 1                            invalid property 'X' for 'INSTANCE'
 *   ALTER MODEL m UNSET X                              invalid property 'X' for 'MODEL'
 *   ALTER foo bar SET COMMENT = 1                      invalid value [1] for parameter 'COMMENT'
 *   ALTER foo bar SET COMMENT = $n                     the variable missing, or invalid type of property '$n' for
 *                                                      'COMMENT' when it holds no text
 * </pre>
 *
 * <p>A name is written as it stands, a quoted one with its quotes: {@code "COMMENT"} is no property of any kind. A
 * comment takes a string, NULL, a name or a parenthesised list of them; a number or a Boolean is an invalid value.
 */
final class UnmodelledAlteration {

    private static final String COMMENT = "COMMENT";

    /** An application package's properties besides its comment. */
    private static final String[] APPLICATION_PACKAGE_PROPERTIES = {"DATA_RETENTION_TIME_IN_DAYS",
        "MAX_DATA_EXTENSION_TIME_IN_DAYS", "DEFAULT_DDL_COLLATION", "DISTRIBUTION", "MULTIPLE_INSTANCES",
        "ENABLE_RELEASE_CHANNELS", "LISTING_AUTO_REFRESH", "AUTOMATIC_APPLICATION_MAINTENANCE"};

    /** An application's properties besides its comment. */
    private static final String[] APPLICATION_PROPERTIES = {"SHARE_EVENTS_WITH_PROVIDER", "DEBUG_MODE",
        "AUTHORIZE_TELEMETRY_EVENT_SHARING"};

    /** The properties whose value is a number of days: a text is an invalid value. */
    private static final String[] DAY_COUNTS = {"DATA_RETENTION_TIME_IN_DAYS", "MAX_DATA_EXTENSION_TIME_IN_DAYS"};

    /** The properties a dataset's new version takes. */
    private static final String[] DATASET_VERSION_PROPERTIES = {COMMENT, "METADATA"};

    private final QueryExecutor queryExecutor;

    UnmodelledAlteration(final QueryExecutor queryExecutor) {
        this.queryExecutor = queryExecutor;
    }

    /**
     * Judge the properties the action sets or unsets.
     *
     * @param kind   the kind's words
     * @param action the action
     */
    void judge(final FrostlakeParser.UnmodelledCommentKindContext kind,
               final FrostlakeParser.UnmodelledAlterActionContext action) {
        final List<FrostlakeParser.UnmodelledPropertyNameContext> names =
            new ArrayList<FrostlakeParser.UnmodelledPropertyNameContext>();
        if (action.SET() != null) {
            for (final FrostlakeParser.UnmodelledPropertyContext property : action.unmodelledProperty()) {
                names.add(property.unmodelledPropertyName());
            }
        } else if (action.UNSET() != null) {
            names.addAll(action.unmodelledPropertyName());
        }
        final Set<String> seen = new HashSet<String>();
        for (final FrostlakeParser.UnmodelledPropertyNameContext name : names) {
            if (!seen.add(canonical(name))) {
                throw new RuntimeException(SqlCompilationError.of("duplicate property '" + name.getText() + "';"));
            }
        }
        final String owner = ownerWord(kind);
        for (final FrostlakeParser.UnmodelledPropertyNameContext name : names) {
            if (!isProperty(kind, name)) {
                throw new RuntimeException(SqlCompilationError.invalidPropertyFor(name.getText(), owner));
            }
        }
        for (final FrostlakeParser.UnmodelledPropertyContext property : action.unmodelledProperty()) {
            final FrostlakeParser.UnmodelledPropertyNameContext name = property.unmodelledPropertyName();
            judgeValue(property.unmodelledPropertyValue(), name.getText(), COMMENT.equals(canonical(name)));
            if (isDayCount(canonical(name)) && property.unmodelledPropertyValue().stringText() != null) {
                throw new RuntimeException(SqlCompilationError.invalidValueForParameter(
                    ParseTreeText.getOriginalText(property.unmodelledPropertyValue()), name.getText()));
            }
        }
    }

    /**
     * A dataset's ADD VERSION or DROP VERSION: a property the new version does not take is refused, and the dataset is
     * then missing, as no dataset is modelled, IF EXISTS or not and whatever its schema; the sentence names it by its
     * last part (live-verified).
     *
     * @param action the action
     * @param parts  the dataset's name, its canonical parts
     * @return the refusal
     */
    RuntimeException missingDataset(final FrostlakeParser.UnmodelledAlterActionContext action, final String[] parts) {
        for (final FrostlakeParser.DatasetVersionPropertyContext property : action.datasetVersionProperty()) {
            final String written = canonical(property.unmodelledPropertyName());
            boolean known = false;
            for (final String name : DATASET_VERSION_PROPERTIES) {
                known = known || name.equals(written);
            }
            if (!known) {
                return new RuntimeException("Invalid dataset property " + written + ".");
            }
        }
        return new RuntimeException("Dataset " + parts[parts.length - 1] + " does not exist.");
    }

    private static boolean isDayCount(final String written) {
        for (final String name : DAY_COUNTS) {
            if (name.equals(written)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The comment the action gives the object, once judged: the text a SET COMMENT names — a string, a name as it is
     * spelled ({@code abc} is ABC), a session variable's text — or null for a NULL, an UNSET or any other action.
     *
     * @param action the action
     * @return the comment, or null
     */
    String comment(final FrostlakeParser.UnmodelledAlterActionContext action) {
        for (final FrostlakeParser.UnmodelledPropertyContext property : action.unmodelledProperty()) {
            if (COMMENT.equals(canonical(property.unmodelledPropertyName()))) {
                return text(property.unmodelledPropertyValue());
            }
        }
        return null;
    }

    private String text(final FrostlakeParser.UnmodelledPropertyValueContext value) {
        if (!value.unmodelledPropertyValue().isEmpty()) {
            return text(value.unmodelledPropertyValue(0));
        }
        if (value.stringText() != null) {
            return ParseTreeText.stringValue(value.stringText());
        }
        if (value.qualifiedName() != null) {
            return SqlIdentifiers.canonicalText(value.qualifiedName().getText());
        }
        if (value.SESSION_VAR_REF() != null) {
            final Object held = TagValues.sessionVariable(value.SESSION_VAR_REF().getSymbol(), queryExecutor);
            return held == null ? null : held.toString();
        }
        return null;
    }

    /** A comment's value, or any other property's session variables, which must be set. */
    private void judgeValue(final FrostlakeParser.UnmodelledPropertyValueContext value, final String name,
                            final boolean comment) {
        if (!value.unmodelledPropertyValue().isEmpty()) {
            for (final FrostlakeParser.UnmodelledPropertyValueContext item : value.unmodelledPropertyValue()) {
                judgeValue(item, name, comment);
            }
            return;
        }
        if (value.SESSION_VAR_REF() != null) {
            final Token variable = value.SESSION_VAR_REF().getSymbol();
            final Object held = TagValues.sessionVariable(variable, queryExecutor);
            if (comment && held != null && !(held instanceof String)) {
                throw new RuntimeException(SqlCompilationError.of("invalid type of property '" + variable.getText()
                    + "' for '" + name + "'"));
            }
            return;
        }
        if (comment && (value.booleanValue() != null || value.INTEGER_LITERAL() != null
                || value.FLOAT_LITERAL() != null)) {
            throw new RuntimeException(SqlCompilationError.invalidValueForParameter(value.getText(), name));
        }
    }

    /** A property's name as the account compares it: an unquoted one upper-cased, a quoted one as written. */
    private static String canonical(final FrostlakeParser.UnmodelledPropertyNameContext name) {
        final String text = name.getText();
        return text.startsWith("\"") ? text : text.toUpperCase(Locale.ROOT);
    }

    /** Whether the kind has the property: COMMENT, and a few kinds' own settings. */
    private static boolean isProperty(final FrostlakeParser.UnmodelledCommentKindContext kind,
                                      final FrostlakeParser.UnmodelledPropertyNameContext name) {
        final String written = canonical(name);
        if (COMMENT.equals(written)) {
            return true;
        }
        if (KindComments.namesClass(kind)) {
            return false;
        }
        final UnmodelledDropKind unmodelled = KindComments.unmodelledKind(kind);
        if (unmodelled == UnmodelledDropKind.MODEL) {
            return "DEFAULT_VERSION".equals(written);
        }
        if (unmodelled == UnmodelledDropKind.GIT_REPOSITORY) {
            return "API_INTEGRATION".equals(written) || "GIT_CREDENTIALS".equals(written);
        }
        if (unmodelled == UnmodelledDropKind.DBT_PROJECT) {
            return "DEFAULT_TARGET".equals(written) || "EXTERNAL_ACCESS_INTEGRATIONS".equals(written);
        }
        if (unmodelled == UnmodelledDropKind.APPLICATION_PACKAGE || unmodelled == UnmodelledDropKind.APPLICATION) {
            final String[] properties = unmodelled == UnmodelledDropKind.APPLICATION ? APPLICATION_PROPERTIES
                : APPLICATION_PACKAGE_PROPERTIES;
            for (final String property : properties) {
                if (property.equals(written)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The word an invalid property's refusal names the kind by. */
    private static String ownerWord(final FrostlakeParser.UnmodelledCommentKindContext kind) {
        if (KindComments.namesClass(kind)) {
            return "INSTANCE";
        }
        final UnmodelledDropKind unmodelled = KindComments.unmodelledKind(kind);
        return unmodelled == UnmodelledDropKind.SNAPSHOT ? "SERVICE_SNAPSHOT" : unmodelled.name();
    }
}

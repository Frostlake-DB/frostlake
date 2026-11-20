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

import dev.frostlake.executor.ContainerParameterCatalog;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.metastore.model.ObjectParameters;
import dev.frostlake.parser.FrostlakeParser;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The parameters ALTER FUNCTION and ALTER PROCEDURE … SET give a routine itself, the ones SHOW PARAMETERS IN FUNCTION
 * and IN PROCEDURE list, each judged as the account judges it and kept as written (live-verified):
 *
 * <pre>
 *   LOG_LEVEL, LOG_EVENT_LEVEL   TRACE, DEBUG, INFO, WARN, ERROR, FATAL or OFF, a string in any case
 *   TRACE_LEVEL                  ALWAYS, ON_EVENT, PROPAGATE or OFF
 *   METRIC_LEVEL                 ALL or NONE
 *   ENABLE_CONSOLE_OUTPUT        TRUE or FALSE
 *   AUTO_EVENT_LOGGING           LOGGING, TRACING, ALL or OFF, on a procedure alone
 * </pre>
 *
 * A level of another kind is {@code invalid value [5] for parameter 'LOG_LEVEL'}, a word no level is {@code Invalid
 * value for log level. Allowed values are TRACE,DEBUG,INFO,WARN,ERROR,FATAL,OFF.}, and a function refuses the
 * procedure's own parameter, {@code cannot set parameter 'AUTO_EVENT_LOGGING' at 'FUNCTION'}.
 */
final class RoutineParameters {

    private static final String AUTO_EVENT_LOGGING = "AUTO_EVENT_LOGGING";

    private static final List<String> AUTO_EVENT_LOGGING_VALUES = Arrays.asList("LOGGING", "TRACING", "ALL", "OFF");

    private RoutineParameters() {
    }

    /**
     * Whether ALTER … SET and UNSET take the name as a parameter of the routine.
     *
     * @param name     the property's name, upper-cased
     * @param function whether the routine is a function
     * @return whether it is one of these parameters
     */
    static boolean takes(final String name, final boolean function) {
        return function ? ContainerParameterCatalog.isFunctionParameter(name)
            : ContainerParameterCatalog.isProcedureParameter(name);
    }

    /**
     * Refuses the procedure's own parameter on a function, or does nothing.
     *
     * @param name     the property's name, upper-cased
     * @param function whether the routine is a function
     */
    static void requireLevel(final String name, final boolean function) {
        if (function && AUTO_EVENT_LOGGING.equals(name)) {
            throw new RuntimeException(SqlCompilationError.of("cannot set parameter '" + name + "' at 'FUNCTION'"));
        }
    }

    /**
     * Judges one setting and gives the routine its value.
     *
     * @param own     the routine's own parameters
     * @param name    the parameter's name, upper-cased
     * @param literal the value as written
     */
    static void set(final ObjectParameters own, final String name, final FrostlakeParser.LiteralContext literal) {
        if (literal.NULL() != null) {
            own.unset(name);
            return;
        }
        final boolean string = literal.STRING_LITERAL() != null || literal.DOLLAR_QUOTED_STRING() != null;
        final String written = literal.getText();
        final String text = literal.STRING_LITERAL() != null ? SqlStringLiterals.decode(written)
            : literal.DOLLAR_QUOTED_STRING() != null ? written.substring(2, written.length() - 2) : written;
        if ("ENABLE_CONSOLE_OUTPUT".equals(name)) {
            final String flag = text.trim().toLowerCase(Locale.ROOT);
            if (literal.TRUE() == null && literal.FALSE() == null
                    && !(string && ("true".equals(flag) || "false".equals(flag)))) {
                throw new RuntimeException(SqlCompilationError.invalidValueForParameter(text, name));
            }
            own.set(name, flag);
            return;
        }
        if (!string) {
            throw new RuntimeException(SqlCompilationError.invalidValueForParameter(text, name));
        }
        if (AUTO_EVENT_LOGGING.equals(name)) {
            if (!AUTO_EVENT_LOGGING_VALUES.contains(text.trim().toUpperCase(Locale.ROOT))) {
                throw new RuntimeException(SqlCompilationError.invalidValueForParameter(text, name));
            }
        } else {
            TaskParameterValues.requireText(name, text);
        }
        own.set(name, text);
    }
}

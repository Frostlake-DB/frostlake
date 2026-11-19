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
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;

/**
 * The items of a security object's list property, in the order written (live-verified). An item is a string or a
 * name, and a name is read as the text it stands for: a plain one as an identifier names - {@code nr1} is
 * {@code NR1} and {@code "x y"} is {@code x y} - and a qualified one as it is written, quotes and all, so that
 * {@code db.s."my rule"} reads as the string {@code 'db.s."my rule"'} does. What the list then makes of the text is
 * the list's own business:
 *
 * <pre>
 *   ALLOWED_NETWORK_RULE_LIST = ("DB"."S"."R", s.r2, r3, 'db.s.r4')   four rules
 *   ALLOWED_NETWORK_RULE_LIST = ("my rule")                           the text my rule, which names MY RULE
 *   ALLOWED_IP_LIST = (nr1)                                           the address NR1, which is none
 *   AUTHENTICATION_METHODS = (PASSWORD)                               the method PASSWORD
 * </pre>
 */
final class SecurityListItems {

    private SecurityListItems() {
    }

    /**
     * The strings and names a parenthesised list holds.
     *
     * @param value the property's value, a list
     * @return each item's text, in the order written
     */
    static List<String> of(final FrostlakeParser.SecurityPropertyValueContext value) {
        final List<String> items = new ArrayList<>();
        for (int i = 0; i < value.getChildCount(); i++) {
            final ParseTree child = value.getChild(i);
            if (child instanceof FrostlakeParser.SecurityListNameContext) {
                items.add(text((FrostlakeParser.SecurityListNameContext) child));
            } else if (child instanceof TerminalNode
                    && ((TerminalNode) child).getSymbol().getType() == FrostlakeLexer.STRING_LITERAL) {
                items.add(SqlStringLiterals.decode(child.getText()));
            }
        }
        return items;
    }

    /**
     * Whether a list holds no string, no name and no variable.
     *
     * @param value the property's value, a list
     * @return whether nothing but numbers, if anything, stands between its parentheses
     */
    static boolean holdsNoText(final FrostlakeParser.SecurityPropertyValueContext value) {
        return value.STRING_LITERAL().isEmpty() && value.SESSION_VAR_REF().isEmpty()
            && value.securityListName().isEmpty();
    }

    private static String text(final FrostlakeParser.SecurityListNameContext name) {
        return name.identifier().size() == 1 ? SqlIdentifiers.canonical(name.identifier(0))
            : ParseTreeText.getOriginalText(name);
    }
}

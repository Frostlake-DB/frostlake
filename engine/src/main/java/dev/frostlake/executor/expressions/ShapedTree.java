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

package dev.frostlake.executor.expressions;

import dev.frostlake.parser.FrostlakeParser;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import org.antlr.v4.runtime.Token;

/** A parse tree kept with the tokens it holds, rebuilt into an expression for each text of its shape. */
final class ShapedTree {

    private final FrostlakeParser.BooleanExprContext tree;

    private final List<Token> tokens;

    private final ReentrantLock lock = new ReentrantLock();

    /**
     * @param tree   the parse tree, holding only tokens of the list
     * @param tokens the tokens it was parsed from, each a {@link ReboundToken}
     */
    ShapedTree(final FrostlakeParser.BooleanExprContext tree, final List<Token> tokens) {
        this.tree = tree;
        this.tokens = tokens;
    }

    /**
     * The expression of the text the given tokens were read from, a text of this tree's shape.
     *
     * @param text the text's tokens, as many as this tree's and of the same types
     * @return the expression a parse of that text builds, or null while another thread rebuilds this tree
     */
    Expression rebuiltFor(final List<Token> text) {
        if (!lock.tryLock()) {
            return null;
        }
        try {
            if (text != tokens) {
                for (int i = 0; i < tokens.size(); i++) {
                    ((ReboundToken) tokens.get(i)).rebindTo(text.get(i));
                }
            }
            return new ExpressionAstBuilder().build(tree);
        } finally {
            lock.unlock();
        }
    }
}

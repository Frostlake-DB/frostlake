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

package dev.frostlake.functions.table;

import dev.frostlake.executor.expressions.SequenceRead;
import dev.frostlake.functions.TableFunction;
import dev.frostlake.metastore.model.Sequence;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GETNEXTVAL — a sequence's next value as a table: {@code FROM t, TABLE(GETNEXTVAL(seq)) s} answers one row, one
 * column NEXTVAL, NUMBER(19,0), for every row of the relations written before it, each holding a value of its own
 * (live-verified). The one argument names the sequence as a name, not as a value — {@code seq}, a qualified name,
 * a quoted one or {@code IDENTIFIER('seq')} — and may be written under any argument name. The name is resolved, and
 * a call that names no sequence refused, while the statement is planned (see the executor), so the arguments this
 * function receives are the resolved sequence and whether the value is only looked at: a plan or a relation's
 * shape draws nothing.
 */
public final class Getnextval extends TableFunction {

    /** The function's name. */
    public static final String NAME = "GETNEXTVAL";

    /** The argument holding the resolved {@link Sequence}. */
    public static final String SEQUENCE = "SEQUENCE";

    /** The argument that, when TRUE, only looks at the value the sequence would hand out. */
    public static final String PEEK = "PEEK";

    /** The column the function answers. */
    public static final String COLUMN = "NEXTVAL";

    public Getnextval() {
        super(NAME);
    }

    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        final Sequence sequence = (Sequence) namedArgs.get(SEQUENCE);
        final boolean peek = Boolean.TRUE.equals(namedArgs.get(PEEK));
        final List<Object> values = new ArrayList<>();
        values.add(peek ? sequence.peekNextValue() : sequence.nextVal());
        final List<Row> rows = new ArrayList<>();
        rows.add(new Row(values));
        return new ResultSet(columns(), rows);
    }

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        // The sequence is resolved, and a call naming none refused, while the statement is planned.
    }

    /**
     * The one column every GETNEXTVAL answers: NEXTVAL, declared and tagged as a sequence read is.
     *
     * @return the columns
     */
    public static List<ResultSetColumn> columns() {
        final List<ResultSetColumn> columns = new ArrayList<>();
        columns.add(new ResultSetColumn(COLUMN, NumericType.BIGINT, null, SequenceRead.TYPE, true, false,
            SequenceRead.range()));
        return columns;
    }

    @Override
    public List<ResultSetColumn> outputColumns(final Map<String, Object> namedArgs) {
        return columns();
    }
}

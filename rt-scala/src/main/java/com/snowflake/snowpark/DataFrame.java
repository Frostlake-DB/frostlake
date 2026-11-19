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
package com.snowflake.snowpark;

import scala.Option;

import java.util.Optional;

/**
 * The Scala Snowpark DataFrame, over the Java API's ({@code com.snowflake.snowpark_java.DataFrame}): a handler
 * collects its rows, counts them, or hands it back as a table procedure's answer.
 */
public final class DataFrame {

    private final com.snowflake.snowpark_java.DataFrame frame;

    DataFrame(final com.snowflake.snowpark_java.DataFrame frame) {
        this.frame = frame;
    }

    /** The Java API's DataFrame this one reads. */
    com.snowflake.snowpark_java.DataFrame frame() {
        return frame;
    }

    /**
     * Runs the DataFrame.
     *
     * @return its rows
     */
    public Row[] collect() {
        return rows(frame.collect());
    }

    /**
     * Runs the DataFrame for the number of its rows.
     *
     * @return the count
     */
    public long count() {
        return frame.count();
    }

    /**
     * Runs the DataFrame for its first row.
     *
     * @return the row, or none when the DataFrame has no row
     */
    public Option<Row> first() {
        final Optional<com.snowflake.snowpark_java.Row> row = frame.first();
        return row.isPresent() ? Option.apply(new Row(row.get())) : Option.<Row>empty();
    }

    /**
     * Runs the DataFrame for its first rows.
     *
     * @param n how many rows
     * @return the rows
     */
    public Row[] first(final int n) {
        return rows(frame.first(n));
    }

    /**
     * @param n the most rows the result holds
     * @return a DataFrame of this one's first rows
     */
    public DataFrame limit(final int n) {
        return new DataFrame(frame.limit(n));
    }

    /**
     * @return a DataFrame of this one's rows, each once
     */
    public DataFrame distinct() {
        return new DataFrame(frame.distinct());
    }

    /**
     * @param other another DataFrame
     * @return the rows of both, each once
     */
    public DataFrame union(final DataFrame other) {
        return new DataFrame(frame.union(other.frame));
    }

    /**
     * @param other another DataFrame
     * @return the rows of both
     */
    public DataFrame unionAll(final DataFrame other) {
        return new DataFrame(frame.unionAll(other.frame));
    }

    /**
     * @param other another DataFrame
     * @return the rows of this one the other lacks
     */
    public DataFrame except(final DataFrame other) {
        return new DataFrame(frame.except(other.frame));
    }

    /**
     * @param other another DataFrame
     * @return the rows both hold
     */
    public DataFrame intersect(final DataFrame other) {
        return new DataFrame(frame.intersect(other.frame));
    }

    private static Row[] rows(final com.snowflake.snowpark_java.Row[] read) {
        final Row[] rows = new Row[read.length];
        for (int i = 0; i < read.length; i++) {
            rows[i] = new Row(read[i]);
        }
        return rows;
    }
}

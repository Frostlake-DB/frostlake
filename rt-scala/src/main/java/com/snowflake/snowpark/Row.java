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

import scala.collection.immutable.Seq;
import scala.jdk.javaapi.CollectionConverters;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;

/**
 * The Scala Snowpark Row, over the Java API's ({@code com.snowflake.snowpark_java.Row}): a cell is read by its
 * place, {@code row(0)} or {@code row.get(0)}, or as the class a typed accessor names, which a NULL fails for a
 * primitive ({@code getInt} of a NULL is a NullPointerException) and answers null for a text.
 */
public final class Row {

    private final com.snowflake.snowpark_java.Row row;

    Row(final com.snowflake.snowpark_java.Row row) {
        this.row = row;
    }

    /**
     * A row of the given values, written {@code Row(1, "a")}.
     *
     * @param values the values
     * @return the row
     */
    public static Row apply(final Object... values) {
        return new Row(com.snowflake.snowpark_java.Row.create(values));
    }

    /**
     * A row of a sequence's values.
     *
     * @param values the values
     * @return the row
     */
    public static Row fromSeq(final scala.collection.Seq<Object> values) {
        return new Row(new com.snowflake.snowpark_java.Row(CollectionConverters.asJava(values)));
    }

    /**
     * @param index a place, from 0
     * @return the cell there
     */
    public Object apply(final int index) {
        return row.get(index);
    }

    /**
     * @param index a place, from 0
     * @return the cell there
     */
    public Object get(final int index) {
        return row.get(index);
    }

    /**
     * @return the number of cells
     */
    public int length() {
        return row.size();
    }

    /**
     * @return the number of cells
     */
    public int size() {
        return row.size();
    }

    /**
     * @param index a place, from 0
     * @return whether the cell there is NULL
     */
    public boolean isNullAt(final int index) {
        return row.isNullAt(index);
    }

    /**
     * @return the cells, in their order
     */
    public Seq<Object> toSeq() {
        return CollectionConverters.asScala(row.toList()).toSeq();
    }

    /**
     * @param index a place, from 0
     * @return the cell there as a BOOLEAN
     */
    public boolean getBoolean(final int index) {
        return row.getBoolean(index);
    }

    /**
     * @param index a place, from 0
     * @return the cell there as a byte
     */
    public byte getByte(final int index) {
        return row.getByte(index);
    }

    /**
     * @param index a place, from 0
     * @return the cell there as a short
     */
    public short getShort(final int index) {
        return row.getShort(index);
    }

    /**
     * @param index a place, from 0
     * @return the cell there as an int
     */
    public int getInt(final int index) {
        return row.getInt(index);
    }

    /**
     * @param index a place, from 0
     * @return the cell there as a long
     */
    public long getLong(final int index) {
        return row.getLong(index);
    }

    /**
     * @param index a place, from 0
     * @return the cell there as a float
     */
    public float getFloat(final int index) {
        return row.getFloat(index);
    }

    /**
     * @param index a place, from 0
     * @return the cell there as a double
     */
    public double getDouble(final int index) {
        return row.getDouble(index);
    }

    /**
     * @param index a place, from 0
     * @return the cell there as a text
     */
    public String getString(final int index) {
        return row.getString(index);
    }

    /**
     * @param index a place, from 0
     * @return the cell there as a decimal
     */
    public BigDecimal getDecimal(final int index) {
        return row.getDecimal(index);
    }

    /**
     * @param index a place, from 0
     * @return the cell there as a date
     */
    public Date getDate(final int index) {
        return row.getDate(index);
    }

    /**
     * @param index a place, from 0
     * @return the cell there as a time
     */
    public Time getTime(final int index) {
        return row.getTime(index);
    }

    /**
     * @param index a place, from 0
     * @return the cell there as a timestamp
     */
    public Timestamp getTimestamp(final int index) {
        return row.getTimestamp(index);
    }

    /**
     * @param index a place, from 0
     * @return the cell there as bytes
     */
    public byte[] getBinary(final int index) {
        return row.getBinary(index);
    }

    @Override
    public String toString() {
        return row.toString();
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof Row && row.equals(((Row) other).row);
    }

    @Override
    public int hashCode() {
        return row.hashCode();
    }
}

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
package dev.frostlake.metastore.model;

/**
 * What makes a table an external table: the stage location it was declared over and its file format, as SHOW
 * EXTERNAL TABLES reports them. Frostlake reads no file through it, so the table holds no rows.
 */
public class ExternalTableMetadata {

    private String stage;
    private String location;
    private String fileFormatName;
    private String fileFormatType;
    private String tableFormat;
    private String cloud;

    /** The stage the location names, fully qualified. */
    public String getStage() {
        return stage;
    }

    /** Sets the stage. */
    public void setStage(final String stage) {
        this.stage = stage;
    }

    /** The location as written: the stage reference with its path. */
    public String getLocation() {
        return location;
    }

    /** Sets the location. */
    public void setLocation(final String location) {
        this.location = location;
    }

    /** The named file format, or null when the format was written inline. */
    public String getFileFormatName() {
        return fileFormatName;
    }

    /** Sets the named file format. */
    public void setFileFormatName(final String fileFormatName) {
        this.fileFormatName = fileFormatName;
    }

    /** The file format's type - CSV, JSON, AVRO, ORC or PARQUET - or null when a named format gives it. */
    public String getFileFormatType() {
        return fileFormatType;
    }

    /** Sets the file format's type. */
    public void setFileFormatType(final String fileFormatType) {
        this.fileFormatType = fileFormatType;
    }

    /** The table format, DELTA when written, or null. */
    public String getTableFormat() {
        return tableFormat;
    }

    /** Sets the table format. */
    public void setTableFormat(final String tableFormat) {
        this.tableFormat = tableFormat;
    }

    /** The cloud the stage's URL names - AWS, GCP or AZURE - or the empty string. */
    public String getCloud() {
        return cloud;
    }

    /** Sets the cloud. */
    public void setCloud(final String cloud) {
        this.cloud = cloud;
    }
}

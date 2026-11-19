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

import dev.frostlake.metastore.SqlObject;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A resource monitor: an account-level object naming a credit quota, the interval it resets at and the thresholds
 * at which it notifies or suspends. Frostlake keeps what was declared and meters nothing, so no threshold is ever
 * reached.
 */
public class ResourceMonitor extends SqlObject {

    private BigDecimal creditQuota;
    private String frequency;
    private Instant startTime;
    private Instant endTime;
    private final List<String> notifyUsers = new ArrayList<>();
    private final List<Integer> notifyAt = new ArrayList<>();
    private Integer suspendAt;
    private Integer suspendImmediatelyAt;

    /** @param name the monitor's canonical name */
    public ResourceMonitor(final String name) {
        super(name);
        this.owner = "ACCOUNTADMIN";
    }

    @Override
    public String getObjectType() {
        return "RESOURCE MONITOR";
    }

    /** The credits allocated per interval, or null for no quota. */
    public BigDecimal getCreditQuota() {
        return creditQuota;
    }

    /** Sets the credit quota; null takes it away. */
    public void setCreditQuota(final BigDecimal creditQuota) {
        this.creditQuota = creditQuota;
    }

    /** The interval the usage resets at - MONTHLY, DAILY, WEEKLY, YEARLY or NEVER - or null when none was written. */
    public String getFrequency() {
        return frequency;
    }

    /** Sets the reset interval. */
    public void setFrequency(final String frequency) {
        this.frequency = frequency;
    }

    /** When the monitor starts monitoring, or null when none was written. */
    public Instant getStartTime() {
        return startTime;
    }

    /** Sets the start. */
    public void setStartTime(final Instant startTime) {
        this.startTime = startTime;
    }

    /** When the monitor suspends its warehouses, or null. */
    public Instant getEndTime() {
        return endTime;
    }

    /** Sets the end; null takes it away. */
    public void setEndTime(final Instant endTime) {
        this.endTime = endTime;
    }

    /** The users notified, by canonical name, in their written order. */
    public List<String> getNotifyUsers() {
        return notifyUsers;
    }

    /** The thresholds, in percent of the quota, a notification is sent at, in their written order. */
    public List<Integer> getNotifyAt() {
        return notifyAt;
    }

    /** The threshold the warehouses are suspended at, or null. */
    public Integer getSuspendAt() {
        return suspendAt;
    }

    /** Sets the suspend threshold. */
    public void setSuspendAt(final Integer suspendAt) {
        this.suspendAt = suspendAt;
    }

    /** The threshold the warehouses are suspended at with their running statements cancelled, or null. */
    public Integer getSuspendImmediatelyAt() {
        return suspendImmediatelyAt;
    }

    /** Sets the immediate-suspend threshold. */
    public void setSuspendImmediatelyAt(final Integer suspendImmediatelyAt) {
        this.suspendImmediatelyAt = suspendImmediatelyAt;
    }
}

/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.jfr.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jdk.jfr.SettingDescriptor;
import jdk.jfr.events.ActiveSettingEvent;
import jdk.jfr.internal.periodic.PeriodicEvents;
import jdk.jfr.internal.util.ImplicitFields;
import jdk.jfr.internal.util.TimespanRate;
import jdk.jfr.internal.util.Utils;
import jdk.jfr.internal.settings.Throttler;
import jdk.jfr.internal.tracing.Modification;

/**
 * Implementation of event type.
 *
 * To avoid memory leaks, this class must not hold strong reference to an event
 * class or a setting class
 */
public final class PlatformEventType extends Type {
    private final boolean isJVM;
    private final boolean isJDK;
    private final boolean isMethodSampling;
    private final boolean isCPUTimeMethodSampling;
    private final List<SettingDescriptor> settings = new ArrayList<>(5);
    private final boolean dynamicSettings;
    private final int stackTraceOffset;
    private long startFilterId = -1;

    // default values
    private boolean largeSize = false;
    private boolean enabled = false;
    private boolean stackTraceEnabled = true;
    private long thresholdTicks = 0;
    private long period = 0;
    private TimespanRate cpuRate;
    private boolean hasHook;

    private boolean beginChunk;
    private boolean endChunk;
    private boolean hasPeriod = true;
    private boolean hasCutoff = false;
    private boolean hasThrottle = false;
    private boolean isInstrumented;
    private boolean markForInstrumentation;
    private boolean registered = true;
    private boolean committable = enabled && registered;
    private boolean hasLevel = false;
    private Throttler throttler;

    // package private
    PlatformEventType(String name, long id, boolean isJDK, boolean dynamicSettings) {
        super(name, Type.SUPER_TYPE_EVENT, id);
        this.dynamicSettings = dynamicSettings;
        this.isJVM = Type.isDefinedByJVM(id);
        this.isMethodSampling = determineMethodSampling();
        this.isCPUTimeMethodSampling = isJVM && name.equals(Type.EVENT_NAME_PREFIX + "CPUTimeSample");
        this.isJDK = isJDK;
        this.stackTraceOffset = determineStackTraceOffset();
    }

    private boolean isExceptionEvent() {
        switch (getName()) {
            case Type.EVENT_NAME_PREFIX + "JavaErrorThrow" :
            case Type.EVENT_NAME_PREFIX + "JavaExceptionThrow" :
                return true;
        }
        return false;
    }

    private int determineStackTraceOffset() {
        if (isJDK) {
            // Order matters
            if (isExceptionEvent()) {
                return 4;
            }
            if (getModification() == Modification.TRACING) {
                return 5;
            }
            return switch (getName()) {
                case Type.EVENT_NAME_PREFIX + "SocketRead",
                     Type.EVENT_NAME_PREFIX + "SocketWrite",
                     Type.EVENT_NAME_PREFIX + "FileWrite" -> 6;
                case Type.EVENT_NAME_PREFIX + "FileRead",
                     Type.EVENT_NAME_PREFIX + "FileForce" -> 5;
                default -> 3;
            };
        }
        return 3;
    }

    private boolean determineMethodSampling() {
        if (!isJVM) {
            return false;
        }
        switch (getName()) {
            case Type.EVENT_NAME_PREFIX + "ExecutionSample":
            case Type.EVENT_NAME_PREFIX + "NativeMethodSample":
                return true;
        }
        return false;
    }

    public Modification getModification() {
        switch (getName()) {
            case Type.EVENT_NAME_PREFIX + "MethodTrace":
                return Modification.TRACING;
            case Type.EVENT_NAME_PREFIX + "MethodTiming":
                return Modification.TIMING;
        }
        return Modification.NONE;
    }

    public void add(SettingDescriptor settingDescriptor) {
        Objects.requireNonNull(settingDescriptor);
        settings.add(settingDescriptor);
    }

    public List<SettingDescriptor> getSettings() {
        if (dynamicSettings) {
            List<SettingDescriptor> list = new ArrayList<>(settings.size());
            for (SettingDescriptor s : settings) {
                if (Utils.isSettingVisible(s.getTypeId(), hasHook)) {
                    list.add(s);
                }
            }
            return list;
        }
        return settings;
    }

    public List<SettingDescriptor> getAllSettings() {
        return settings;
    }

    public void setHasCutoff(boolean hasCutoff) {
       this.hasCutoff = hasCutoff;
    }

    public void setHasThrottle(boolean hasThrottle) {
        this.hasThrottle = hasThrottle;
    }

    public void setCutoff(long cutoffNanos) {
        if (isJVM) {
            long cutoffTicks = JVMSupport.nanosToTicks(cutoffNanos);
            JVM.setMiscellaneous(getId(), cutoffTicks);
        }
    }

    public void setLevel(long level) {
        setMiscellaneous(level);
    }

    private void setMiscellaneous(long value) {
        if (isJVM) {
            JVM.setMiscellaneous(getId(), value);
        }
    }

    public void setThrottle(long eventSampleSize, long periodInMillis) {
        if (isJVM) {
            JVM.setThrottle(getId(), eventSampleSize, periodInMillis);
        } else {
            throttler.configure(eventSampleSize, periodInMillis);
        }
    }

    public void setCPUThrottle(TimespanRate rate) {
        if (isCPUTimeMethodSampling) {
            this.cpuRate = rate;
            if (isEnabled()) {
                if (rate.isRate()) {
                    JVM.setCPURate(rate.rate());
                } else {
                    JVM.setCPUPeriod(rate.periodNanos());
                }
            }
        }
    }

    public void setHasPeriod(boolean hasPeriod) {
        this.hasPeriod = hasPeriod;
    }

    public void setHasLevel(boolean hasLevel) {
        this.hasLevel = hasLevel;
    }

    public boolean hasLevel() {
        return this.hasLevel;
    }

    public boolean hasStackTrace() {
        return getField(ImplicitFields.STACK_TRACE) != null;
    }

    public boolean hasThreshold() {
        if (hasCutoff) {
            // Event has a duration, but not a threshold. Used by OldObjectSample
            return false;
        }
        return getField(ImplicitFields.DURATION) != null;
    }

    public boolean hasPeriod() {
        return this.hasPeriod;
    }

    public boolean hasCutoff() {
        return this.hasCutoff;
    }

    public boolean hasThrottle() {
        return this.hasThrottle;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isSystem() {
        return isJVM || isJDK;
    }

    public boolean isJVM() {
        return isJVM;
    }

    public boolean isJDK() {
        return isJDK;
    }

    public void setEnabled(boolean enabled) {
        boolean changed = enabled != this.enabled;
        this.enabled = enabled;
        updateCommittable();
        if (isJVM) {
            if (isMethodSampling) {
                long p = enabled ? period : 0;
                JVM.setMethodSamplingPeriod(getId(), p);
            } else if (isCPUTimeMethodSampling) {
                TimespanRate r = enabled ? cpuRate : TimespanRate.OFF;
                if (r.isRate()) {
                    JVM.setCPURate(r.rate());
                } else {
                    JVM.setCPUPeriod(r.periodNanos());
                }
            } else {
                JVM.setEnabled(getId(), enabled);
            }
        }
        if (changed) {
            PeriodicEvents.setChanged();
        }
    }

    public void setPeriod(long periodMillis, boolean beginChunk, boolean endChunk) {
        if (isMethodSampling) {
            long p = enabled ? periodMillis : 0;
            JVM.setMethodSamplingPeriod(getId(), p);
        }
        this.beginChunk = beginChunk;
        this.endChunk = endChunk;
        boolean changed = period != periodMillis;
        this.period = periodMillis;
        if (changed) {
            PeriodicEvents.setChanged();
        }
    }

    public void setStackTraceEnabled(boolean stackTraceEnabled) {
        this.stackTraceEnabled = stackTraceEnabled;
        if (isJVM) {
            JVM.setStackTraceEnabled(getId(), stackTraceEnabled);
        }
    }

    public void setThreshold(long thresholdNanos) {
        this.thresholdTicks = JVMSupport.nanosToTicks(thresholdNanos);
        if (isJVM) {
            JVM.setThreshold(getId(), thresholdTicks);
        }
    }

    /**
     * Returns true if "beginChunk", "endChunk" or "everyChunk" have
     * been set.
     */
    public boolean isChunkTime() {
        return period == 0;
    }

    public boolean getStackTraceEnabled() {
        return stackTraceEnabled;
    }

    public long getThresholdTicks() {
        return thresholdTicks;
    }

    public long getPeriod() {
        return period;
    }

    public boolean hasEventHook() {
        return hasHook;
    }

    public void setEventHook(boolean hasHook) {
        this.hasHook = hasHook;
        PeriodicEvents.setChanged();
    }

    public boolean isBeginChunk() {
        return beginChunk;
    }

    public boolean isEndChunk() {
        return endChunk;
    }

    public boolean isInstrumented() {
        return isInstrumented;
    }

    public void setInstrumented() {
        isInstrumented = true;
    }

    public void markForInstrumentation(boolean markForInstrumentation) {
        this.markForInstrumentation = markForInstrumentation;
    }

    public boolean isMarkedForInstrumentation() {
        return markForInstrumentation;
    }

    public boolean setRegistered(boolean registered) {
        if (this.registered != registered) {
            this.registered = registered;
            updateCommittable();
            LogTag logTag = isSystem() ? LogTag.JFR_SYSTEM_METADATA : LogTag.JFR_METADATA;
            if (registered) {
                Logger.log(logTag, LogLevel.INFO, "Registered " + getLogName());
            } else {
                Logger.log(logTag, LogLevel.INFO, "Unregistered " + getLogName());
            }
            if (!registered) {
                MetadataRepository.getInstance().setUnregistered();
            }
            return true;
        }
        return false;
    }

    private void updateCommittable() {
        this.committable = enabled && registered;
    }

    public final boolean isRegistered() {
        return registered;
    }

    // Efficient check of enabled && registered
    public boolean isCommittable() {
        return committable;
    }

    public int getStackTraceOffset() {
        return stackTraceOffset;
    }

    public boolean isLargeSize() {
        return largeSize;
    }

    public void setLargeSize() {
        largeSize = true;
    }

    public boolean isMethodSampling() {
        return isMethodSampling;
    }

    public boolean isCPUTimeMethodSampling() {
        return isCPUTimeMethodSampling;
    }

    public void setStackFilterId(long id) {
        startFilterId = id;
    }

    public boolean hasStackFilters() {
        return startFilterId >= 0;
    }

    public long getStackFilterId() {
        return startFilterId;
    }

    public Throttler getThrottler() {
        return throttler;
    }

    public void setThrottler(Throttler throttler) {
       this.throttler = throttler;
    }
}

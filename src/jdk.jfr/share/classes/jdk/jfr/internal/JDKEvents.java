/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Properties;

import jdk.internal.access.SharedSecrets;
import jdk.internal.event.JFRTracing;
import jdk.internal.event.ThrowableTracer;
import jdk.internal.platform.Container;
import jdk.internal.platform.Metrics;
import jdk.jfr.Event;
import jdk.jfr.events.ActiveRecordingEvent;
import jdk.jfr.events.ActiveSettingEvent;
import jdk.jfr.events.ContainerCPUThrottlingEvent;
import jdk.jfr.events.ContainerCPUUsageEvent;
import jdk.jfr.events.ContainerConfigurationEvent;
import jdk.jfr.events.ContainerIOUsageEvent;
import jdk.jfr.events.ContainerMemoryUsageEvent;
import jdk.jfr.events.DirectBufferStatisticsEvent;
import jdk.jfr.events.InitialSecurityPropertyEvent;
import jdk.jfr.events.MethodTimingEvent;
import jdk.jfr.events.MethodTraceEvent;
import jdk.jfr.internal.periodic.PeriodicEvents;
import jdk.jfr.internal.settings.MethodSetting;
import jdk.jfr.internal.tracing.PlatformTracer;
import jdk.jfr.tracing.MethodTracer;

public final class JDKEvents {

    private static final Class<?>[] eventClasses = {
        ActiveSettingEvent.class,
        ActiveRecordingEvent.class,
        // jdk.internal.event.* classes need their mirror
        // event class to be listed in the MirrorEvents class.
        jdk.internal.event.DeserializationEvent.class,
        jdk.internal.event.ErrorThrownEvent.class,
        jdk.internal.event.ExceptionStatisticsEvent.class,
        jdk.internal.event.ExceptionThrownEvent.class,
        jdk.internal.event.FileForceEvent.class,
        jdk.internal.event.FileReadEvent.class,
        jdk.internal.event.FileWriteEvent.class,
        jdk.internal.event.ProcessStartEvent.class,
        jdk.internal.event.SecurityPropertyModificationEvent.class,
        jdk.internal.event.SecurityProviderServiceEvent.class,
        jdk.internal.event.SerializationMisdeclarationEvent.class,
        jdk.internal.event.SocketReadEvent.class,
        jdk.internal.event.SocketWriteEvent.class,
        jdk.internal.event.ThreadSleepEvent.class,
        jdk.internal.event.TLSHandshakeEvent.class,
        jdk.internal.event.VirtualThreadStartEvent.class,
        jdk.internal.event.VirtualThreadEndEvent.class,
        jdk.internal.event.VirtualThreadSubmitFailedEvent.class,
        jdk.internal.event.X509CertificateEvent.class,
        jdk.internal.event.X509ValidationEvent.class,
        DirectBufferStatisticsEvent.class,
        InitialSecurityPropertyEvent.class,
        MethodTraceEvent.class,
        MethodTimingEvent.class,
    };

    private static final Runnable emitExceptionStatistics = JDKEvents::emitExceptionStatistics;
    private static final Runnable emitDirectBufferStatistics = JDKEvents::emitDirectBufferStatistics;
    private static final Runnable emitContainerConfiguration = JDKEvents::emitContainerConfiguration;
    private static final Runnable emitContainerCPUUsage = JDKEvents::emitContainerCPUUsage;
    private static final Runnable emitContainerCPUThrottling = JDKEvents::emitContainerCPUThrottling;
    private static final Runnable emitContainerMemoryUsage = JDKEvents::emitContainerMemoryUsage;
    private static final Runnable emitContainerIOUsage = JDKEvents::emitContainerIOUsage;
    private static final Runnable emitInitialSecurityProperties = JDKEvents::emitInitialSecurityProperties;
    private static final Runnable emitMethodTiming = JDKEvents::emitMethodTiming;
    private static Metrics containerMetrics = null;
    private static boolean initializationTriggered;

    @SuppressWarnings("unchecked")
    public static synchronized void initialize() {
        try {
            if (initializationTriggered == false) {
                for (Class<?> eventClass : eventClasses) {
                    MetadataRepository.getInstance().register((Class<? extends Event>) eventClass);
                }
                PeriodicEvents.addJavaEvent(jdk.internal.event.ExceptionStatisticsEvent.class, emitExceptionStatistics);
                PeriodicEvents.addJavaEvent(DirectBufferStatisticsEvent.class, emitDirectBufferStatistics);
                PeriodicEvents.addJavaEvent(InitialSecurityPropertyEvent.class, emitInitialSecurityProperties);
                PeriodicEvents.addJavaEvent(MethodTimingEvent.class, emitMethodTiming);

                initializeContainerEvents();
                JFRTracing.enable();
                initializationTriggered = true;
            }
        } catch (Exception e) {
            Logger.log(LogTag.JFR_SYSTEM, LogLevel.WARN, "Could not initialize JDK events. " + e.getMessage());
        }
    }

    private static void initializeContainerEvents() {
        if (JVM.isContainerized() ) {
            Logger.log(LogTag.JFR_SYSTEM, LogLevel.DEBUG, "JVM is containerized");
            containerMetrics = Container.metrics();
            if (containerMetrics != null) {
                Logger.log(LogTag.JFR_SYSTEM, LogLevel.DEBUG, "Container metrics are available");
            }
        }
        // The registration of events and hooks are needed to provide metadata,
        // even when not running in a container
        registerEvent(ContainerConfigurationEvent.class);
        registerEvent(ContainerCPUUsageEvent.class);
        registerEvent(ContainerCPUThrottlingEvent.class);
        registerEvent(ContainerMemoryUsageEvent.class);
        registerEvent(ContainerIOUsageEvent.class);

        PeriodicEvents.addJavaEvent(ContainerConfigurationEvent.class, emitContainerConfiguration);
        PeriodicEvents.addJavaEvent(ContainerCPUUsageEvent.class, emitContainerCPUUsage);
        PeriodicEvents.addJavaEvent(ContainerCPUThrottlingEvent.class, emitContainerCPUThrottling);
        PeriodicEvents.addJavaEvent(ContainerMemoryUsageEvent.class, emitContainerMemoryUsage);
        PeriodicEvents.addJavaEvent(ContainerIOUsageEvent.class, emitContainerIOUsage);
    }

    private static void registerEvent(Class<? extends jdk.internal.event.Event> eventClass) {
        MetadataRepository.getInstance().register(eventClass);
    }

    private static void emitExceptionStatistics() {
        ThrowableTracer.emitStatistics();
    }

    private static void emitContainerConfiguration() {
        if (containerMetrics != null) {
            ContainerConfigurationEvent t = new ContainerConfigurationEvent();
            t.containerType = containerMetrics.getProvider();
            t.cpuSlicePeriod = containerMetrics.getCpuPeriod();
            t.cpuQuota = containerMetrics.getCpuQuota();
            t.cpuShares = containerMetrics.getCpuShares();
            t.effectiveCpuCount = containerMetrics.getEffectiveCpuCount();
            t.memorySoftLimit = containerMetrics.getMemorySoftLimit();
            t.memoryLimit = containerMetrics.getMemoryLimit();
            t.swapMemoryLimit = containerMetrics.getMemoryAndSwapLimit();
            t.hostTotalMemory = JVM.hostTotalMemory();
            t.hostTotalSwapMemory = JVM.hostTotalSwapMemory();
            t.commit();
        }
    }

    private static void emitContainerCPUUsage() {
        if (containerMetrics != null) {
            ContainerCPUUsageEvent event = new ContainerCPUUsageEvent();

            event.cpuTime = containerMetrics.getCpuUsage();
            event.cpuSystemTime = containerMetrics.getCpuSystemUsage();
            event.cpuUserTime = containerMetrics.getCpuUserUsage();
            event.commit();
        }
    }
    private static void emitContainerMemoryUsage() {
        if (containerMetrics != null) {
            ContainerMemoryUsageEvent event = new ContainerMemoryUsageEvent();

            event.memoryFailCount = containerMetrics.getMemoryFailCount();
            event.memoryUsage = containerMetrics.getMemoryUsage();
            event.swapMemoryUsage = containerMetrics.getMemoryAndSwapUsage();
            event.commit();
        }
    }

    private static void emitContainerIOUsage() {
        if (containerMetrics != null) {
            ContainerIOUsageEvent event = new ContainerIOUsageEvent();

            event.serviceRequests = containerMetrics.getBlkIOServiceCount();
            event.dataTransferred = containerMetrics.getBlkIOServiced();
            event.commit();
        }
    }

    private static void emitContainerCPUThrottling() {
        if (containerMetrics != null) {
            ContainerCPUThrottlingEvent event = new ContainerCPUThrottlingEvent();

            event.cpuElapsedSlices = containerMetrics.getCpuNumPeriods();
            event.cpuThrottledSlices = containerMetrics.getCpuNumThrottled();
            event.cpuThrottledTime = containerMetrics.getCpuThrottledTime();
            event.commit();
        }
    }

    public static void remove() {
        PeriodicEvents.removeEvent(emitExceptionStatistics);
        PeriodicEvents.removeEvent(emitDirectBufferStatistics);
        PeriodicEvents.removeEvent(emitInitialSecurityProperties);
        PeriodicEvents.removeEvent(emitMethodTiming);

        PeriodicEvents.removeEvent(emitContainerConfiguration);
        PeriodicEvents.removeEvent(emitContainerCPUUsage);
        PeriodicEvents.removeEvent(emitContainerCPUThrottling);
        PeriodicEvents.removeEvent(emitContainerMemoryUsage);
        PeriodicEvents.removeEvent(emitContainerIOUsage);
    }

    private static void emitDirectBufferStatistics() {
        DirectBufferStatisticsEvent e = new DirectBufferStatisticsEvent();
        e.commit();
    }

    private static void emitInitialSecurityProperties() {
        Properties p = SharedSecrets.getJavaSecurityPropertiesAccess().getInitialProperties();
        if (p != null) {
            for (String key : p.stringPropertyNames()) {
                InitialSecurityPropertyEvent e = new InitialSecurityPropertyEvent();
                e.key = key;
                e.value = p.getProperty(key);
                e.commit();
            }
        }
    }

    private static void emitMethodTiming() {
        if (MethodSetting.isInitialized() && MethodTimingEvent.enabled()) {
            PlatformTracer.emitTiming();
        }
    }
}

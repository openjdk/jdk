/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal.instrument;

import java.util.ArrayList;
import java.util.List;

import jdk.internal.module.Modules;
import jdk.jfr.Event;
import jdk.jfr.FlightRecorder;
import jdk.jfr.events.ActiveRecordingEvent;
import jdk.jfr.events.ActiveSettingEvent;
import jdk.jfr.events.ErrorThrownEvent;
import jdk.jfr.events.ExceptionStatisticsEvent;
import jdk.jfr.events.ExceptionThrownEvent;
import jdk.jfr.events.FileForceEvent;
import jdk.jfr.events.FileReadEvent;
import jdk.jfr.events.FileWriteEvent;
import jdk.jfr.events.SocketReadEvent;
import jdk.jfr.events.SocketWriteEvent;
import jdk.jfr.internal.JVM;
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;
import jdk.jfr.internal.RequestEngine;
import jdk.jfr.internal.SecuritySupport;
import jdk.jfr.internal.Utils;

public final class JDKEvents {

    private static final Class<?>[] mirrorEventClasses = {
    };

    private static final Class<?>[] eventClasses = {
        FileForceEvent.class,
        FileReadEvent.class,
        FileWriteEvent.class,
        SocketReadEvent.class,
        SocketWriteEvent.class,
        ExceptionThrownEvent.class,
        ExceptionStatisticsEvent.class,
        ErrorThrownEvent.class,
        ActiveSettingEvent.class,
        ActiveRecordingEvent.class
    };

    // This is a list of the classes with instrumentation code that should be applied.
    private static final Class<?>[] instrumentationClasses = new Class<?>[] {
        FileInputStreamInstrumentor.class,
        FileOutputStreamInstrumentor.class,
        RandomAccessFileInstrumentor.class,
        FileChannelImplInstrumentor.class,
        SocketInputStreamInstrumentor.class,
        SocketOutputStreamInstrumentor.class,
        SocketChannelImplInstrumentor.class
    };

    private static final Class<?>[] targetClasses = new Class<?>[instrumentationClasses.length];
    private static final JVM jvm = JVM.getJVM();
    private static final Runnable emitExceptionStatistics = JDKEvents::emitExceptionStatistics;
    private static boolean initializationTriggered;

    @SuppressWarnings("unchecked")
    public synchronized static void initialize() {
        try {
            if (initializationTriggered == false) {
                Module jdkJfrModule = Event.class.getModule();
                Module javaBaseModule = Object.class.getModule();
                Modules.addReads(javaBaseModule, jdkJfrModule);
                Modules.addExports(jdkJfrModule, Utils.EVENTS_PACKAGE_NAME, javaBaseModule);
                Modules.addExports(jdkJfrModule, Utils.INSTRUMENT_PACKAGE_NAME, javaBaseModule);
                Modules.addExports(jdkJfrModule, Utils.HANDLERS_PACKAGE_NAME, javaBaseModule);
                for (Class<?> mirrorEventClass : mirrorEventClasses) {
                    SecuritySupport.registerMirror(((Class<? extends Event>)mirrorEventClass));
                }
                for (Class<?> eventClass : eventClasses) {
                    SecuritySupport.registerEvent((Class<? extends Event>) eventClass);
                }
                initializationTriggered = true;
                FlightRecorder.addPeriodicEvent(ExceptionStatisticsEvent.class, emitExceptionStatistics);
            }
        } catch (Exception e) {
            Logger.log(LogTag.JFR_SYSTEM, LogLevel.WARN, "Could not initialize JDK events. " + e.getMessage());
        }
    }

    public static void addInstrumentation() {
        try {
            List<Class<?>> list = new ArrayList<>();
            for (int i = 0; i < instrumentationClasses.length; i++) {
                JIInstrumentationTarget tgt = instrumentationClasses[i].getAnnotation(JIInstrumentationTarget.class);
                Class<?> clazz = Class.forName(tgt.value());
                targetClasses[i] = clazz;
                list.add(clazz);
            }
            list.add(java.lang.Throwable.class);
            list.add(java.lang.Error.class);
            Logger.log(LogTag.JFR_SYSTEM, LogLevel.INFO, "Retransformed JDK classes");
            jvm.retransformClasses(list.toArray(new Class<?>[list.size()]));
        } catch (Exception e) {
            Logger.log(LogTag.JFR_SYSTEM, LogLevel.WARN, "Could not add instrumentation for JDK events. " + e.getMessage());
        }
    }

    private static void emitExceptionStatistics() {
        ExceptionStatisticsEvent t = new ExceptionStatisticsEvent();
        t.throwables = ThrowableTracer.numThrowables();
        t.commit();
    }

    @SuppressWarnings("deprecation")
    public static byte[] retransformCallback(Class<?> klass, byte[] oldBytes) throws Throwable {
        if (java.lang.Throwable.class == klass) {
            Logger.log(LogTag.JFR_SYSTEM, LogLevel.TRACE, "Instrumenting java.lang.Throwable");
            return ConstructorTracerWriter.generateBytes(java.lang.Throwable.class, oldBytes);
        }

        if (java.lang.Error.class == klass) {
            Logger.log(LogTag.JFR_SYSTEM, LogLevel.TRACE, "Instrumenting java.lang.Error");
            return ConstructorTracerWriter.generateBytes(java.lang.Error.class, oldBytes);
        }

        for (int i = 0; i < targetClasses.length; i++) {
            if (targetClasses[i].equals(klass)) {
                Class<?> c = instrumentationClasses[i];
                Logger.log(LogTag.JFR_SYSTEM, LogLevel.TRACE, () -> "Processing instrumentation class: " + c);
                return new JIClassInstrumentation(instrumentationClasses[i], klass, oldBytes).getNewBytes();
            }
        }
        return oldBytes;
    }

    public static void remove() {
        RequestEngine.removeHook(JDKEvents::emitExceptionStatistics);
    }
}

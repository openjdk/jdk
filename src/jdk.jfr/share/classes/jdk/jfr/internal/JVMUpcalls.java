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

import java.lang.reflect.Modifier;
import jdk.jfr.internal.event.EventConfiguration;
import jdk.jfr.internal.util.Bytecode;
import jdk.jfr.internal.util.Utils;
import jdk.jfr.internal.tracing.PlatformTracer;

/**
 * All upcalls from the JVM should go through this class.
 *
 */
// Called by native
final class JVMUpcalls {
    /**
     * Called by the JVM when a retransform happens on a tagged class
     *
     * @param traceId
     *            Id of the class
     * @param dummy1
     *            not used, but act as padding so bytesForEagerInstrumentation and
     *            onRetransform can have identical method signatures, which simplifies the
     *            invoke machinery in native
     *
     * @param dummy2
     *            not used, but act as padding so bytesForEagerInstrumentation and
     *            onRetransform can have identical method signatures, which simplifies the
     *            invoke machinery in native
     *
     * @param clazz
     *            class being retransformed
     * @param oldBytes
     *            byte code
     * @return byte code to use
     * @throws Throwable
     */
    static byte[] onRetransform(long traceId, boolean dummy1, boolean dummy2, Class<?> clazz, byte[] oldBytes) throws Throwable {
        try {
            if (jdk.internal.event.Event.class.isAssignableFrom(clazz) && !Modifier.isAbstract(clazz.getModifiers())) {
                if (!JVMSupport.shouldInstrument(Utils.isJDKClass(clazz), clazz.getName())) {
                    Logger.log(LogTag.JFR_SYSTEM, LogLevel.INFO, "Skipping instrumentation for " + clazz.getName() + " since container support is missing");
                    return oldBytes;
                }
                EventConfiguration configuration = JVMSupport.getConfiguration(clazz.asSubclass(jdk.internal.event.Event.class));
                if (configuration == null) {
                    Logger.log(LogTag.JFR_SYSTEM, LogLevel.INFO, "No event configuration found for " + clazz.getName() + ". Ignoring instrumentation request.");
                    // Probably triggered by some other agent
                    return oldBytes;
                }
                boolean jdkClass = Utils.isJDKClass(clazz);
                Logger.log(LogTag.JFR_SYSTEM, LogLevel.INFO, "Adding instrumentation to event class " + clazz.getName() + " using retransform");
                ClassInspector c = new ClassInspector(clazz.getSuperclass(), oldBytes, jdkClass);
                EventInstrumentation ei = new EventInstrumentation(c, traceId, false);
                byte[] bytes = ei.buildInstrumented();
                Bytecode.log(clazz.getName(), bytes);
                return bytes;
            }
            return oldBytes;
        } catch (Throwable t) {
            Logger.log(LogTag.JFR_SYSTEM, LogLevel.WARN, "Unexpected error when adding instrumentation to event class " + clazz.getName());
        }
        return oldBytes;
    }

    /**
     * Called by the JVM when requested to do an "eager" instrumentation. Would
     * normally happen when JVMTI retransform capabilities are not available.
     *
     * @param traceId
     *            Id of the class
     * @param forceInstrumentation
     *            add instrumentation regardless if event is enabled or not.
     * @param superClass
     *            the super class of the class being processed
     * @param oldBytes
     *            byte code
     * @return byte code to use
     * @throws Throwable
     */
    static byte[] bytesForEagerInstrumentation(long traceId, boolean forceInstrumentation, boolean bootClassLoader, Class<?> superClass, byte[] oldBytes) throws Throwable {
        if (JVMSupport.isNotAvailable()) {
            return oldBytes;
        }
        String eventName = "<Unknown>";
        try {
            ClassInspector c = new ClassInspector(superClass, oldBytes, bootClassLoader);
            eventName = c.getEventName();
            if (!JVMSupport.shouldInstrument(bootClassLoader,  c.getEventName())) {
                Logger.log(LogTag.JFR_SYSTEM, LogLevel.INFO, "Skipping instrumentation for " + eventName + " since container support is missing");
                return oldBytes;
            }

            if (!forceInstrumentation) {
                // Assume we are recording
                MetadataRepository mr = MetadataRepository.getInstance();
                // No need to generate bytecode if:
                // 1) Event class is disabled, and there is not an external configuration that overrides.
                // 2) Event class has @Registered(false)
                if (!mr.isEnabled(c.getEventName()) && !c.isEnabled() || !c.isRegistered()) {
                    Logger.log(LogTag.JFR_SYSTEM, LogLevel.INFO, "Skipping instrumentation for event type " + eventName + " since event was disabled on class load");
                    return oldBytes;
                }
            }
            EventInstrumentation ei = new EventInstrumentation(c, traceId, true);
            byte[] bytes = ei.buildInstrumented();
            Logger.log(LogTag.JFR_SYSTEM, LogLevel.INFO, "Adding " + (forceInstrumentation ? "forced " : "") + "instrumentation for event type " + eventName + " during initial class load");
            Bytecode.log(c.getClassName() + "(" + traceId + ")", bytes);
            return bytes;
        } catch (Throwable t) {
            Logger.log(LogTag.JFR_SYSTEM, LogLevel.WARN, "Unexpected error when adding instrumentation for event type " + eventName + ". " + t.getMessage());
            return oldBytes;
        }
    }

    /**
     * Called by the JVM to ensure metadata for internal events/types become public.
     *
     * Must be called after metadata repository has been initialized (JFR created).
     *
     */
    static void unhideInternalTypes() {
        MetadataRepository.unhideInternalTypes();
    }

    /**
     * Called by the JVM to create the recorder thread.
     *
     * @param systemThreadGroup  the system thread group
     *
     * @param contextClassLoader the context class loader.
     *
     * @return a new thread
     */
    static Thread createRecorderThread(ThreadGroup systemThreadGroup, ClassLoader contextClassLoader) {
        Thread thread = new Thread(systemThreadGroup, "JFR Recorder Thread");
        thread.setContextClassLoader(contextClassLoader);
        return thread;
    }

    /**
     * Called by the JVM to update method tracing instrumentation.
     * <p>
     * @param module the module the class belongs to
     * @param classLoader the class loader the class is being loaded for
     * @param className the internal class name, i.e. java/lang/String.
     * @param bytecode the bytecode to modify
     * @param methodIds the method IDs
     * @param names constant pool indices of method names
     * @param signatures constant pool indices of method signatures
     * @param modifications integer mask describing the modification
     *
     * @return the instrumented bytecode, or null if the class can't or shouldn't be modified.
     */
    public static byte[] onMethodTrace(Module module, ClassLoader classLoader, String className,
                                       byte[] bytecode, long[] methodIds, String[] names, String[] signatures,
                                       int[] modifications) {
        return PlatformTracer.onMethodTrace(module, classLoader, className,
                                            bytecode, methodIds, names, signatures,
                                            modifications);
    }

    /**
     * Called by the JVM to publish a class ID that can safely be used by the Method Timing event.
     * <p>
     * @param classId the methods to be published
     */
    public static void publishMethodTimersForClass(long classId) {
        PlatformTracer.publishClass(classId);
    }
}

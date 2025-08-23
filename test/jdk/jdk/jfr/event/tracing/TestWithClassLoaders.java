/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package jdk.jfr.event.tracing;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedMethod;
import jdk.jfr.consumer.RecordingStream;

/**
 * @test
 * @summary Tests that filters can be applied to classes across multiple class loaders and that
 *          method tracing works after class unloading.
 * @requires vm.flagless
 * @requires vm.hasJFR
 * @library /test/lib
 * @build jdk.jfr.event.tracing.Car
 * @run main/othervm -XX:-DisableExplicitGC -Xlog:jfr+methodtrace=trace
 *      jdk.jfr.event.tracing.TestWithClassLoaders
 **/
public class TestWithClassLoaders {
    private static final String METHOD_TRACE = "jdk.MethodTrace";
    private static final String METHOD_TIMING = "jdk.MethodTiming";
    private static final String CLASS_NAME = "jdk.jfr.event.tracing.Car";

    public static void main(String... args) throws Exception {
        var traceEvents = new CopyOnWriteArrayList<RecordedEvent>();
        var timingEvents = new CopyOnWriteArrayList<RecordedEvent>();
        try (var r = new RecordingStream()) {
            Runnable beforeCar = createCar("before");
            r.setReuse(false);
            r.enable(METHOD_TRACE)
             .with("filter", CLASS_NAME + "::run");
            r.enable(METHOD_TIMING)
             .with("filter", CLASS_NAME + "::run").with("period", "endChunk");
            r.onEvent(METHOD_TRACE, traceEvents::add);
            r.onEvent(METHOD_TIMING, timingEvents::add);
            r.startAsync();
            Runnable duringCar = createCar("during");
            Runnable garbageCar = createCar("garbage");
            beforeCar.run();
            duringCar.run();
            garbageCar.run();
            garbageCar = null;
            System.gc();
            System.gc();
            r.stop();
            System.out.println("Method Trace events:");
            System.out.println(traceEvents);
            if (traceEvents.size() != 3) {
                throw new Exception("Expected 3 Method Trace events, one for each class loader");
            }
            for (RecordedEvent event : traceEvents) {
                RecordedMethod method = event.getValue("method");
                String methodName = method.getName();
                if (!methodName.equals("run")) {
                    throw new Exception("Expected method name to be 'run'");
                }
            }
            System.out.println("Method Timing events:");
            System.out.println(timingEvents);
            if (timingEvents.size() != 3) {
                throw new Exception("Expected 3 Method Timing events, one for each class loader");
            }
            int totalInvocations = 0;
            for (RecordedEvent event : timingEvents) {
                totalInvocations += event.getLong("invocations");
            }
            if (totalInvocations != 3) {
                throw new Exception("Expected three invocations in total, was " + totalInvocations);
            }
        }
    }

    public static Runnable createCar(String name) throws Exception {
        byte[] bytes = loadCarBytes();
        ClassLoader parent = TestWithClassLoaders.class.getClassLoader();
        CarLoader loader = new CarLoader(name, bytes, parent);
        Class<?> clazz = loader.loadClass(CLASS_NAME);
        Object instance = clazz.getConstructor().newInstance();
        return (Runnable) instance;
    }

    private static byte[] loadCarBytes() throws IOException {
        String location = "/" + CLASS_NAME.replaceAll("\\.", "/").concat(".class");
        try (var is = TestWithClassLoaders.class.getResourceAsStream(location)) {
            return is.readAllBytes();
        }
    }

    public static class CarLoader extends ClassLoader {
        private final byte[] bytes;

        public CarLoader(String name, byte[] bytes, ClassLoader parent) {
            super(name, parent);
            this.bytes = bytes;
        }

        protected Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
            Class<?> clazz = findLoadedClass(className);
            if (clazz == null && className.equals(CLASS_NAME)) {
                clazz = defineClass(className, bytes, 0, bytes.length);
            } else {
                clazz = super.loadClass(className, resolve);
            }
            if (resolve) {
                resolveClass(clazz);
            }
            return clazz;
        }
    }
}

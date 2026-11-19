/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8392362
 * @summary Obsolete lambda, record, and switch helpers can unload after redefinition or retransformation
 * @requires vm.jvmti & (vm.gc != "Epsilon")
 * @library /test/lib
 * @modules java.instrument
 * @build LambdaClassUnloading
 * @run driver jdk.test.lib.util.JavaAgentBuilder
 *      LambdaClassUnloading LambdaClassUnloading.jar
 * @run main/othervm -javaagent:LambdaClassUnloading.jar -Xshare:off
 *      -XX:+ClassUnloading -XX:-DisableExplicitGC -XX:-ExplicitGCInvokesConcurrent
 *      LambdaClassUnloading retransform
 * @run main/othervm -javaagent:LambdaClassUnloading.jar -Xshare:off
 *      -XX:+ClassUnloading -XX:-DisableExplicitGC -XX:-ExplicitGCInvokesConcurrent
 *      LambdaClassUnloading redefine
 */

import java.io.InputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import jdk.test.lib.util.ForceGC;

public class LambdaClassUnloading {
    private static Instrumentation instrumentation;

    public static class Host {
        public static IntSupplier capturing(int value) {
            return () -> value;
        }

        public static IntSupplier nonCapturing() {
            return () -> 4711;
        }
    }

    public record RecordHost(Object value) {}

    public static class SwitchHost {
        public static int typeSwitch(Object value) {
            return switch (value) {
                case String text -> text.length();
                case Integer number -> number.intValue();
                default -> -1;
            };
        }
    }

    public static void premain(String arguments, Instrumentation agent) {
        instrumentation = agent;
    }

    public static void main(String[] arguments) throws Exception {
        boolean retransform = switch (arguments[0]) {
            case "retransform" -> true;
            case "redefine" -> false;
            default -> throw new IllegalArgumentException(arguments[0]);
        };
        if (arguments.length == 2) {
            testHelpers(retransform, arguments[1]);
            return;
        }
        byte[] originalBytes;
        try (InputStream stream = Host.class.getResourceAsStream("LambdaClassUnloading$Host.class")) {
            originalBytes = stream.readAllBytes();
        }
        ClassLoader loader = Host.class.getClassLoader();
        Asserts.assertTrue(instrumentation.isModifiableClass(Host.class));
        List<WeakReference<Class<?>>> classes = exercise(retransform, originalBytes);
        awaitUnloading(classes, "Released lambda implementation classes remain reachable");
        Reference.reachabilityFence(Host.class);
        Reference.reachabilityFence(loader);
        System.out.println(arguments[0] + ": all " + classes.size() + " implementation classes reclaimed");

        testHelpers(retransform, "recordEquals");
        testHelpers(retransform, "recordHashCode");
        testHelpers(retransform, "typeSwitch");
    }

    private static List<WeakReference<Class<?>>> exercise(boolean retransform, byte[] originalBytes) throws Exception {
        List<WeakReference<Class<?>>> classes = new ArrayList<>();
        transform(retransform, originalBytes);
        IntSupplier retainedCapturing = Host.capturing(123);
        IntSupplier retainedNonCapturing = Host.nonCapturing();
        track(classes, retainedCapturing, 123);
        track(classes, retainedNonCapturing, 4711);

        for (int generation = 0; generation < 10; generation++) {
            transform(retransform, originalBytes);
            generate(classes, generation);
        }
        transform(retransform, originalBytes);
        awaitUnloading(classes.subList(2, classes.size()), "Obsolete lambda implementation classes remain reachable");
        Asserts.assertFalse(classes.get(0).refersTo(null), "Retained capturing lambda class reclaimed");
        Asserts.assertFalse(classes.get(1).refersTo(null), "Retained noncapturing lambda class reclaimed");
        Asserts.assertEquals(retainedCapturing.getAsInt(), 123);
        Asserts.assertEquals(retainedNonCapturing.getAsInt(), 4711);
        Reference.reachabilityFence(retainedCapturing);
        Reference.reachabilityFence(retainedNonCapturing);
        return classes;
    }

    private static void transform(boolean retransform, byte[] originalBytes) throws Exception {
        transform(retransform, Host.class, originalBytes);
    }

    private static void transform(boolean retransform, Class<?> host, byte[] originalBytes) throws Exception {
        if (retransform) {
            instrumentation.retransformClasses(host);
        } else {
            instrumentation.redefineClasses(new ClassDefinition(host, originalBytes));
        }
    }

    private static void testHelpers(boolean retransform, String kind) throws Exception {
        Class<?> host = switch (kind) {
            case "recordEquals", "recordHashCode" -> RecordHost.class;
            case "typeSwitch" -> SwitchHost.class;
            default -> throw new IllegalArgumentException(kind);
        };
        String suffix = switch (kind) {
            case "recordEquals" -> "$$EqualatorSpecializer/";
            case "recordHashCode" -> "$$HasherSpecializer/";
            case "typeSwitch" -> "$$TypeSwitch/";
            default -> throw new IllegalArgumentException(kind);
        };
        byte[] originalBytes;
        try (InputStream stream = host.getResourceAsStream(host.getName() + ".class")) {
            originalBytes = stream.readAllBytes();
        }
        ClassLoader loader = host.getClassLoader();
        Asserts.assertTrue(instrumentation.isModifiableClass(host));
        List<WeakReference<Class<?>>> classes = new ArrayList<>();
        for (int generation = 0; generation <= 10; generation++) {
            // Transform before the first link too, avoiding an initial STRONG helper.
            transform(retransform, host, originalBytes);
            runHelperWorkload(kind);
            trackNewHelper(classes, host.getName() + suffix);
        }

        // The current call site must keep its helper alive while obsolete
        // generations unload. The weak references themselves retain no classes.
        awaitUnloading(classes.subList(0, classes.size() - 1), kind + ": obsolete helper classes remain reachable");
        Asserts.assertFalse(classes.getLast().refersTo(null), kind + ": current call site's helper reclaimed");
        runHelperWorkload(kind);

        // Unlink the final site without invoking it again.
        transform(retransform, host, originalBytes);
        awaitUnloading(classes, kind + ": released helper classes remain reachable");
        Reference.reachabilityFence(host);
        Reference.reachabilityFence(loader);
        System.out.println(kind + ": all " + classes.size() + " helper classes reclaimed");
    }

    private static void runHelperWorkload(String kind) {
        switch (kind) {
            case "recordEquals" -> {
                RecordHost value = new RecordHost("payload");
                Asserts.assertTrue(value.equals(new RecordHost("payload")));
                Asserts.assertFalse(value.equals(new RecordHost("different")));
            }
            case "recordHashCode" ->
                Asserts.assertEquals(new RecordHost("payload").hashCode(), "payload".hashCode());
            case "typeSwitch" -> {
                Asserts.assertEquals(SwitchHost.typeSwitch("payload"), 7);
                Asserts.assertEquals(SwitchHost.typeSwitch(Integer.valueOf(4711)), 4711);
                Asserts.assertEquals(SwitchHost.typeSwitch(Boolean.TRUE), -1);
            }
            default -> throw new IllegalArgumentException(kind);
        }
    }

    private static void trackNewHelper(List<WeakReference<Class<?>>> classes, String prefix) {
        Class<?> found = null;
        // Keep the enumeration's strong Class references inside this method,
        // so none survives into the unloading checks.
        for (Class<?> candidate : instrumentation.getAllLoadedClasses()) {
            if (!candidate.getName().startsWith(prefix) || alreadyTracked(classes, candidate)) {
                continue;
            }
            Asserts.assertTrue(candidate.isHidden(), "Expected a hidden helper class");
            Asserts.assertNull(found, "More than one new helper for " + prefix);
            found = candidate;
        }
        Asserts.assertNotNull(found, "No new helper for " + prefix);
        classes.add(new WeakReference<>(found));
    }

    private static boolean alreadyTracked(List<WeakReference<Class<?>>> classes, Class<?> candidate) {
        for (WeakReference<Class<?>> reference : classes) {
            if (reference.refersTo(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static void generate(List<WeakReference<Class<?>>> classes, int value) {
        track(classes, Host.capturing(value), value);
        track(classes, Host.nonCapturing(), 4711);
    }

    private static void track(List<WeakReference<Class<?>>> classes, IntSupplier instance, int expected) {
        Asserts.assertEquals(instance.getAsInt(), expected);
        Asserts.assertTrue(instance.getClass().isHidden(), "Expected a hidden lambda implementation class");
        Asserts.assertFalse(alreadyTracked(classes, instance.getClass()), "Expected a new lambda implementation class");
        classes.add(new WeakReference<>(instance.getClass()));
    }

    private static void awaitUnloading(List<WeakReference<Class<?>>> classes, String message) {
        Asserts.assertTrue(ForceGC.waitFor(() -> allCleared(classes), Utils.adjustTimeout(10_000)), message);
    }

    private static boolean allCleared(List<WeakReference<Class<?>>> classes) {
        for (WeakReference<Class<?>> reference : classes) {
            if (!reference.refersTo(null)) {
                return false;
            }
        }
        return true;
    }
}

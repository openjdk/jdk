/*
 * @test
 * @bug 8392362
 * @summary Obsolete lambda implementation classes can unload after redefinition or retransformation
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

    public static void premain(String arguments, Instrumentation agent) {
        instrumentation = agent;
    }

    public static void main(String[] arguments) throws Exception {
        boolean retransform = switch (arguments[0]) {
            case "retransform" -> true;
            case "redefine" -> false;
            default -> throw new IllegalArgumentException(arguments[0]);
        };
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
        if (retransform) {
            instrumentation.retransformClasses(Host.class);
        } else {
            instrumentation.redefineClasses(new ClassDefinition(Host.class, originalBytes));
        }
    }

    private static void generate(List<WeakReference<Class<?>>> classes, int value) {
        track(classes, Host.capturing(value), value);
        track(classes, Host.nonCapturing(), 4711);
    }

    private static void track(List<WeakReference<Class<?>>> classes, IntSupplier instance, int expected) {
        Asserts.assertEquals(instance.getAsInt(), expected);
        Asserts.assertTrue(instance.getClass().isHidden(), "Expected a hidden lambda implementation class");
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

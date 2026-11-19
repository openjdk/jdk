/*
 * @test
 * @bug 8397416
 * @summary Class loading counters must not include scratch classes created by redefinition
 * @requires vm.flagless & vm.jvmti
 * @library /test/lib
 * @modules java.instrument java.management
 * @build RedefinitionCounts
 * @run driver jdk.test.lib.util.JavaAgentBuilder RedefinitionCounts RedefinitionCounts.jar
 * @run main/othervm -javaagent:RedefinitionCounts.jar RedefinitionCounts retransform
 * @run main/othervm -javaagent:RedefinitionCounts.jar RedefinitionCounts redefine
 */

import java.io.InputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;

import jdk.test.lib.Asserts;

public class RedefinitionCounts {
    private static final ClassLoadingMXBean CLASS_LOADING = ManagementFactory.getClassLoadingMXBean();
    private static Instrumentation instrumentation;

    public static class Target {
        public static int value() {
            return 4711;
        }
    }

    public static void premain(String arguments, Instrumentation agent) {
        instrumentation = agent;
    }

    private record Counts(long loaded, long total, long unloaded) {}

    public static void main(String[] arguments) throws Exception {
        boolean retransform = arguments[0].equals("retransform");
        Class<?>[] targets = { Target.class };
        ClassDefinition[] definitions;
        try (InputStream stream = Target.class.getResourceAsStream("RedefinitionCounts$Target.class")) {
            definitions = new ClassDefinition[] { new ClassDefinition(Target.class, stream.readAllBytes()) };
        }
        Asserts.assertEquals(4711, Target.value());

        for (int iteration = 0; iteration < 10; iteration++) {
            transform(retransform, targets, definitions);
            readCounts();
        }

        Counts before = readCounts();
        for (int iteration = 0; iteration < 100; iteration++) {
            transform(retransform, targets, definitions);
        }
        Counts after = readCounts();

        System.out.println("Mode: " + arguments[0]);
        System.out.println("Before: " + before);
        System.out.println("After: " + after);

        Asserts.assertEquals(before.total(), after.total(),
            "Redefinition must not increase the total loaded class count");
        Asserts.assertEquals(before.unloaded() - after.unloaded(), after.loaded() - before.loaded(),
            "Redefinition must not inflate the current loaded class count");
        Asserts.assertEquals(4711, Target.value());
    }

    private static void transform(boolean retransform, Class<?>[] targets,
                                  ClassDefinition[] definitions) throws Exception {
        if (retransform) {
            instrumentation.retransformClasses(targets);
        } else {
            instrumentation.redefineClasses(definitions);
        }
    }

    private static Counts readCounts() {
        return new Counts(CLASS_LOADING.getLoadedClassCount(),
                          CLASS_LOADING.getTotalLoadedClassCount(),
                          CLASS_LOADING.getUnloadedClassCount());
    }
}

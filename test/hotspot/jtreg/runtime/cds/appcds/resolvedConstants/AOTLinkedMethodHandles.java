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
 *
 */

/*
 * @test
 * @summary AOT resolution of MethodHandle invocation
 * @bug 8398128
 * @requires vm.cds
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib
 * @build AOTLinkedMethodHandles
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 AOTLinkedMethodHandlesApp AOTLinkedMethodHandlesApp$Behaviour
 * @run driver AOTLinkedMethodHandles
 */

import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static java.lang.invoke.MethodType.methodType;

public class AOTLinkedMethodHandles {

    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = AOTLinkedMethodHandlesApp.class.getName();

    private static final List<String> codes = List.of("\\(A4\\)", "\\(AP4\\)");
    private static final List<String> invokeExacts = List.of(
        "()V",
        "(LAOTLinkedMethodHandlesApp$Behaviour;)V",
        "(LAOTLinkedMethodHandlesApp$Behaviour;I)V",
        "(LAOTLinkedMethodHandlesApp$Behaviour;I)J",
        "(LAOTLinkedMethodHandlesApp$Behaviour;Ljava/time/LocalDate;)V",
        "(LAOTLinkedMethodHandlesApp$Behaviour;ILjava/time/LocalDate;)V",
        "(LAOTLinkedMethodHandlesApp$Behaviour;ILjava/time/LocalDate;)Ljava/time/LocalDate;"
    );

    private static final List<String> invokes = List.of(
        "()Ljava/lang/Object;",
        "(Ljava/lang/Object;)Ljava/lang/Object;",
        "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
        "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
    );

    public static void main(String[] args) throws Exception {
        SimpleCDSAppTester t = SimpleCDSAppTester.of("AOTLinkedMethodHandles")
            .classpath(appJar)
            .appCommandLine(mainClass)
            .addVmArgs("-esa",
                "-XX:+AOTClassLinking",
                "-Xlog:aot+resolve=trace",
                "-Xlog:aot+codecache+nmethod=info",
                "-XX:CompileCommand=dontinline,AOTLinkedMethodHandlesApp::*Hot",
                "-Xlog:cds+class=debug")
            .setTrainingChecker((OutputAnalyzer output) -> {
                output.shouldContain("Hello AOTLinkedMethodHandles");
            })
            .setAssemblyChecker((OutputAnalyzer output) -> {
                invokeExacts.forEach(as -> output.shouldMatch(buildSignatureStringMatcher("invokeExact", as)));
                invokes.forEach(as -> output.shouldMatch(buildSignatureStringMatcher("invoke", as)));
                output.shouldNotContain("rejected .* CP entry.*");
                var methodNames = Arrays.stream(AOTLinkedMethodHandlesApp.class.getDeclaredMethods())
                    .map(Method::getName)
                    .filter(m -> m.endsWith("Hot"))
                    .toList();
                methodNames.forEach(m ->
                    output.shouldNotMatch("'AOTLinkedMethodHandlesApp\\.%s\\(.*store to AOT Code Cache failed".formatted(m))
                );
                methodNames.forEach(m -> codes.forEach(c ->
                    output.shouldMatch("%s 'AOTLinkedMethodHandlesApp\\.%s\\(.*wrote to AOT Code Cache".formatted(c, m))
                ));

            })
            .runAOTTrainingAndAssemblyWorkflow();

        t.setVmArgs("-Xlog:cds,aot", "-esa")
            .setProductionChecker((OutputAnalyzer output) -> {
                output.shouldContain("Hello AOTLinkedMethodHandles");
            })
            .productionRun();
    }

    private static String buildSignatureStringMatcher(String methodName, String args) {
        var s = "archived method CP entry.* AOTLinkedMethodHandlesApp ";
        return s + "java/lang/invoke/MethodHandle." + methodName + ":" + Pattern.quote(args) + " => ";
    }

}

class AOTLinkedMethodHandlesApp {

    private static final MethodHandle STATIC_CALL;                               // ()void
    private static final MethodHandle EMPTY_INSTANCE;                            // (Behaviour)void
    private static final MethodHandle INSTANCE_WITH_PRIMITIVE_PARAM;             // (Behaviour,int)void
    private static final MethodHandle INSTANCE_WITH_OBJECT_PARAM;                // (Behaviour,LocalDate)void
    private static final MethodHandle INSTANCE_WITH_MIXED_PARAMS;                // (Behaviour,int,LocalDate)void
    private static final MethodHandle INSTANCE_WITH_PRIMITIVE_PARAM_AND_RETURN;  // (Behaviour,int)long
    private static final MethodHandle INSTANCE_WITH_MIXED_PARAMS_AND_RETURN;     // (Behaviour,int,LocalDate)LocalDate
    private static final int HOT_ITERATIONS = 20_000;
    private static final Behaviour receiver = new Behaviour();
    private static final int intArg = 42;
    private static final LocalDate dateArg = LocalDate.of(2026, 1, 1);
    private static volatile Object sink;
    private static volatile long longSink;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            Class<Behaviour> b = Behaviour.class;
            STATIC_CALL = lookup.findStatic(b, "staticCall", methodType(void.class));
            EMPTY_INSTANCE = lookup.findVirtual(b, "emptyInstance", methodType(void.class));
            INSTANCE_WITH_PRIMITIVE_PARAM = lookup.findVirtual(b, "instanceWithPrimitiveParam", methodType(void.class, int.class));
            INSTANCE_WITH_OBJECT_PARAM = lookup.findVirtual(b, "instanceWithObjectParam", methodType(void.class, LocalDate.class));
            INSTANCE_WITH_MIXED_PARAMS = lookup.findVirtual(b, "instanceWithMixedParams", methodType(void.class, int.class, LocalDate.class));
            INSTANCE_WITH_PRIMITIVE_PARAM_AND_RETURN = lookup.findVirtual(b, "instanceWithPrimitiveParamAndReturn", methodType(long.class, int.class));
            INSTANCE_WITH_MIXED_PARAMS_AND_RETURN = lookup.findVirtual(b, "instanceWithMixedParamsAndReturn", methodType(LocalDate.class, int.class, LocalDate.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static void main(String[] args) throws Throwable {

        System.out.println("Hello AOTLinkedMethodHandles");

        for (int n = 0; n < HOT_ITERATIONS; n++) staticCallExactHot();
        for (int n = 0; n < HOT_ITERATIONS; n++) emptyInstanceExactHot(receiver);
        for (int n = 0; n < HOT_ITERATIONS; n++) primitiveParamExactHot(receiver, intArg);
        for (int n = 0; n < HOT_ITERATIONS; n++) objectParamExactHot(receiver, dateArg);
        for (int n = 0; n < HOT_ITERATIONS; n++) mixedParamsExactHot(receiver, intArg, dateArg);
        for (int n = 0; n < HOT_ITERATIONS; n++) longSink = primitiveReturnExactHot(receiver, intArg);
        for (int n = 0; n < HOT_ITERATIONS; n++) sink = objectReturnExactHot(receiver, intArg, dateArg);

        for (int n = 0; n < HOT_ITERATIONS; n++) sink = staticCallInvokeHot();
        for (int n = 0; n < HOT_ITERATIONS; n++) sink = emptyInstanceInvokeHot(receiver);
        for (int n = 0; n < HOT_ITERATIONS; n++) sink = primitiveParamInvokeHot(receiver, intArg);
        for (int n = 0; n < HOT_ITERATIONS; n++) sink = objectParamInvokeHot(receiver, dateArg);
        for (int n = 0; n < HOT_ITERATIONS; n++) sink = mixedParamsInvokeHot(receiver, intArg, dateArg);
        for (int n = 0; n < HOT_ITERATIONS; n++) sink = primitiveReturnInvokeHot(receiver, intArg);
        for (int n = 0; n < HOT_ITERATIONS; n++) sink = objectReturnInvokeHot(receiver, intArg, dateArg);
    }

    static void staticCallExactHot() throws Throwable {
        STATIC_CALL.invokeExact();
    }

    static void emptyInstanceExactHot(Behaviour b) throws Throwable {
        EMPTY_INSTANCE.invokeExact(b);
    }

    static void primitiveParamExactHot(Behaviour b, int i) throws Throwable {
        INSTANCE_WITH_PRIMITIVE_PARAM.invokeExact(b, i);
    }

    static void objectParamExactHot(Behaviour b, LocalDate d) throws Throwable {
        INSTANCE_WITH_OBJECT_PARAM.invokeExact(b, d);
    }

    static void mixedParamsExactHot(Behaviour b, int i, LocalDate d) throws Throwable {
        INSTANCE_WITH_MIXED_PARAMS.invokeExact(b, i, d);
    }

    static long primitiveReturnExactHot(Behaviour b, int i) throws Throwable {
        return (long) INSTANCE_WITH_PRIMITIVE_PARAM_AND_RETURN.invokeExact(b, i);
    }

    static LocalDate objectReturnExactHot(Behaviour b, int i, LocalDate d) throws Throwable {
        return (LocalDate) INSTANCE_WITH_MIXED_PARAMS_AND_RETURN.invokeExact(b, i, d);
    }

    static Object staticCallInvokeHot() throws Throwable {
        return STATIC_CALL.invoke();
    }

    static Object emptyInstanceInvokeHot(Object b) throws Throwable {
        return EMPTY_INSTANCE.invoke(b);
    }

    static Object primitiveParamInvokeHot(Object b, Object i) throws Throwable {
        return INSTANCE_WITH_PRIMITIVE_PARAM.invoke(b, i);
    }

    static Object objectParamInvokeHot(Object b, Object d) throws Throwable {
        return INSTANCE_WITH_OBJECT_PARAM.invoke(b, d);
    }

    static Object mixedParamsInvokeHot(Object b, Object i, Object d) throws Throwable {
        return INSTANCE_WITH_MIXED_PARAMS.invoke(b, i, d);
    }

    static Object primitiveReturnInvokeHot(Object b, Object i) throws Throwable {
        return INSTANCE_WITH_PRIMITIVE_PARAM_AND_RETURN.invoke(b, i);
    }

    static Object objectReturnInvokeHot(Object b, Object i, Object d) throws Throwable {
        return INSTANCE_WITH_MIXED_PARAMS_AND_RETURN.invoke(b, i, d);
    }

    public static class Behaviour {

        public static void staticCall() {
            System.out.println("staticCall");
        }

        public void emptyInstance() {
            System.out.println("emptyInstance");
        }

        public void instanceWithPrimitiveParam(int i) {
            System.out.println("instanceWithPrimitiveParam: " + i);
        }

        public void instanceWithObjectParam(LocalDate localDate) {
            System.out.println("instanceWithObjectParam: " + localDate);
        }

        public void instanceWithMixedParams(int i, LocalDate localDate) {
            System.out.println("instanceWithMixedParams: " + i + ", " + localDate);
        }

        public long instanceWithPrimitiveParamAndReturn(int i) {
            System.out.println("instanceWithPrimitiveParamAndReturn: " + i);
            return System.currentTimeMillis();
        }

        public LocalDate instanceWithMixedParamsAndReturn(int i, LocalDate localDate) {
            System.out.println("instanceWithMixedParamsAndReturn: " + i + ", " + localDate);
            return LocalDate.now();
        }

    }

}

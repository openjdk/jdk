/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.methodhandle;

import java.io.PrintWriter;
import java.lang.classfile.TypeKind;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A helper program to generate the VarHandleGuards class with a set of
 * static guard methods each of which corresponds to a particular shape and
 * performs a type check of the symbolic type descriptor with the VarHandle
 * type descriptor before linking/invoking to the underlying operation as
 * characterized by the operation member name on the VarForm of the
 * VarHandle.
 * <p>
 * The generated class essentially encapsulates pre-compiled LambdaForms,
 * one for each method, for the most common set of method signatures.
 * This reduces static initialization costs, footprint costs, and circular
 * dependencies that may arise if a class is generated per LambdaForm.
 * <p>
 * A maximum of L*T*S methods will be generated where L is the number of
 * access modes kinds (or unique operation signatures) and T is the number
 * of variable types and S is the number of shapes (such as instance field,
 * static field, or array access).
 * If there are 4 unique operation signatures, 5 basic types (Object, int,
 * long, float, double), and 3 shapes then a maximum of 60 methods will be
 * generated.  However, the number is likely to be less since there may
 * be duplicate signatures.
 * <p>
 * Each method is annotated with @LambdaForm.Compiled to inform the runtime
 * that such methods should be treated as if a method of a class that is the
 * result of compiling a LambdaForm.  Annotation of such methods is
 * important for correct evaluation of certain assertions and method return
 * type profiling in HotSpot.
 *
 * @see java.lang.invoke.GenerateJLIClassesHelper
 */
public final class VarHandleGuardMethodGenerator {

    static final String CLASS_HEADER = """
            /*
             * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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
            package java.lang.invoke;

            import jdk.internal.vm.annotation.AOTSafeClassInitializer;
            import jdk.internal.vm.annotation.ForceInline;
            import jdk.internal.vm.annotation.Hidden;

            // This file is generated by build.tools.methodhandle.VarHandleGuardMethodGenerator.
            // Do not edit!
            @AOTSafeClassInitializer
            final class VarHandleGuards {
            """;

    static final String GUARD_METHOD_SIG_TEMPLATE = "<RETURN> <NAME>_<SIGNATURE>(<PARAMS>)";

    static final String GUARD_METHOD_TEMPLATE =
            """
                    @ForceInline
                    @LambdaForm.Compiled
                    @Hidden
                    static final <METHOD> throws Throwable {
                        boolean direct = handle.checkAccessModeThenIsDirect(ad);
                        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
                            <RESULT_ERASED>MethodHandle.linkToStatic(<LINK_TO_STATIC_ARGS>);<RETURN_ERASED>
                        } else {
                            MethodHandle mh = handle.getMethodHandle(ad.mode);
                            <RETURN>mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(<LINK_TO_INVOKER_ARGS>);
                        }
                    }""";

    static final String GUARD_METHOD_TEMPLATE_V =
            """
                    @ForceInline
                    @LambdaForm.Compiled
                    @Hidden
                    static final <METHOD> throws Throwable {
                        boolean direct = handle.checkAccessModeThenIsDirect(ad);
                        if (direct && handle.vform.methodType_table[ad.type] == ad.symbolicMethodTypeErased) {
                            MethodHandle.linkToStatic(<LINK_TO_STATIC_ARGS>);
                        } else if (direct && handle.vform.getMethodType_V(ad.type) == ad.symbolicMethodTypeErased) {
                            MethodHandle.linkToStatic(<LINK_TO_STATIC_ARGS>);
                        } else {
                            MethodHandle mh = handle.getMethodHandle(ad.mode);
                            mh.asType(ad.symbolicMethodTypeInvoker).invokeBasic(<LINK_TO_INVOKER_ARGS>);
                        }
                    }""";

    // A template for deriving the operations
    // could be supported by annotating VarHandle directly with the
    // operation kind and shape
    interface VarHandleTemplate {
        Object get();

        void set(Object value);

        boolean compareAndSet(Object actualValue, Object expectedValue);

        Object compareAndExchange(Object actualValue, Object expectedValue);

        Object getAndUpdate(Object value);
    }

    record HandleType(Class<?> receiver, Class<?>... intermediates) {
    }

    public static void main(String... args) throws Throwable {
        if (args.length != 1) {
            System.err.println("Usage: java VarHandleGuardMethodGenerator VarHandleGuards.java");
            System.exit(1);
        }

        Path outputFile = Path.of(args[0]);

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(
                outputFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING))) {
            print(pw);
        }
    }

    public static void print(PrintWriter pw) {
        pw.println(CLASS_HEADER);

        // Declare the stream of shapes
        List<HandleType> hts = List.of(
                // Object->T
                new HandleType(Object.class),

                // <static>->T
                new HandleType(null),

                // Array[index]->T
                new HandleType(Object.class, int.class),

                // MS[base]->T
                new HandleType(Object.class, long.class),

                // MS[base][offset]->T
                new HandleType(Object.class, long.class, long.class)
        );

        // The 5 JVM calling convention types
        List<Class<?>> basicTypes = List.of(Object.class, int.class, long.class, float.class, double.class);

        Stream.of(VarHandleTemplate.class.getMethods()).<MethodType>
                        mapMulti((m, sink) -> {
                    for (var ht : hts) {
                        for (var bt : basicTypes) {
                            sink.accept(generateMethodType(m, ht.receiver, bt, ht.intermediates));
                        }
                    }
                }).
                distinct().
                map(VarHandleGuardMethodGenerator::generateMethod).
                forEach(pw::println);

        pw.println("}");
    }

    static MethodType generateMethodType(Method m, Class<?> receiver, Class<?> value, Class<?>... intermediates) {
        Class<?> returnType = m.getReturnType() == Object.class
                ? value : m.getReturnType();

        List<Class<?>> params = new ArrayList<>();
        if (receiver != null)
            params.add(receiver);
        Collections.addAll(params, intermediates);
        for (var p : m.getParameters()) {
            params.add(value);
        }
        return MethodType.methodType(returnType, params);
    }

    static String generateMethod(MethodType mt) {
        Class<?> returnType = mt.returnType();

        var params = new LinkedHashMap<String, String>();
        params.put("handle", className(VarHandle.class));
        for (int i = 0; i < mt.parameterCount(); i++) {
            params.put("arg" + i, className(mt.parameterType(i)));
        }
        params.put("ad", "VarHandle.AccessDescriptor");

        // Generate method signature line
        String RETURN = className(returnType);
        String NAME = "guard";
        String SIGNATURE = getSignature(mt);
        String PARAMS = params.entrySet().stream().
                map(e -> e.getValue() + " " + e.getKey()).
                collect(Collectors.joining(", "));
        String METHOD = GUARD_METHOD_SIG_TEMPLATE.
                replace("<RETURN>", RETURN).
                replace("<NAME>", NAME).
                replace("<SIGNATURE>", SIGNATURE).
                replace("<PARAMS>", PARAMS);

        // Generate method
        params.remove("ad");

        List<String> LINK_TO_STATIC_ARGS = new ArrayList<>(params.keySet());
        LINK_TO_STATIC_ARGS.add("handle.vform.getMemberName(ad.mode)");

        List<String> LINK_TO_INVOKER_ARGS = new ArrayList<>(params.keySet());
        LINK_TO_INVOKER_ARGS.set(0, LINK_TO_INVOKER_ARGS.get(0) + ".asDirect()");

        RETURN = returnType == void.class
                ? ""
                : returnType == Object.class
                ? "return "
                : "return (" + returnType.getName() + ") ";

        String RESULT_ERASED = returnType == void.class
                ? ""
                : returnType != Object.class
                ? "return (" + returnType.getName() + ") "
                : "Object r = ";

        String RETURN_ERASED = returnType != Object.class
                ? ""
                : "\n        return ad.returnType.cast(r);";

        String template = returnType == void.class
                ? GUARD_METHOD_TEMPLATE_V
                : GUARD_METHOD_TEMPLATE;
        return template.
                replace("<METHOD>", METHOD).
                replace("<NAME>", NAME).
                replaceAll("<RETURN>", RETURN).
                replace("<RESULT_ERASED>", RESULT_ERASED).
                replace("<RETURN_ERASED>", RETURN_ERASED).
                replaceAll("<LINK_TO_STATIC_ARGS>", String.join(", ", LINK_TO_STATIC_ARGS)).
                replace("<LINK_TO_INVOKER_ARGS>", String.join(", ", LINK_TO_INVOKER_ARGS))
                .indent(4);
    }

    static String className(Class<?> c) {
        String n = c.getCanonicalName();
        if (n == null)
            throw new IllegalArgumentException("Not representable in source code: " + c);
        if (!c.isPrimitive() && c.getPackageName().equals("java.lang")) {
            n = n.substring("java.lang.".length());
        } else if (c.getPackageName().equals("java.lang.invoke")) {
            n = n.substring("java.lang.invoke.".length());
        }
        return n;
    }

    static String getSignature(MethodType m) {
        StringBuilder sb = new StringBuilder(m.parameterCount() + 1);

        for (int i = 0; i < m.parameterCount(); i++) {
            Class<?> pt = m.parameterType(i);
            sb.append(getCharType(pt));
        }

        sb.append('_').append(getCharType(m.returnType()));

        return sb.toString();
    }

    static char getCharType(Class<?> pt) {
        return TypeKind.from(pt).upperBound().descriptorString().charAt(0);
    }
}

/*
 * Copyright (c) 2009, 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.classfile.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.annotation.*;
import java.lang.constant.ClassDesc;
import java.lang.reflect.*;
import java.util.*;

import static java.lang.String.format;

public class Driver {

    private static final PrintStream out = System.err;

    private final Object testObject;

    public Driver(Class<?> clazz) throws IllegalAccessException, InstantiationException {
        testObject = clazz.newInstance();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1)
            throw new IllegalArgumentException("Usage: java Driver <test-name>");
        String name = args[0];
        new Driver(Class.forName(name)).runDriver();
    }

    private final String[][] extraParamsCombinations = new String[][] {
        new String[] { },
        new String[] { "-g" },
    };

    private final String[] retentionPolicies = {RetentionPolicy.CLASS.toString(), RetentionPolicy.RUNTIME.toString()};

    protected void runDriver() {
        int passed = 0, failed = 0;
        Class<?> clazz = testObject.getClass();
        out.println("Tests for " + clazz.getName());

        // Find methods
        for (Method method : clazz.getMethods()) {
            try {
                Map<String, TypeAnnotation> expected = expectedOf(method);
                if (expected == null)
                    continue;
                if (method.getReturnType() != String.class)
                    throw new IllegalArgumentException("Test method needs to return a string: " + method);

                String compact = (String) method.invoke(testObject);
                for (String retentionPolicy : retentionPolicies) {
                    String testClassName = getTestClassName(method, retentionPolicy);
                    String testClass = testClassOf(method, testClassName);
                    String fullFile = wrap(compact, new HashMap<>() {{
                        put("%RETENTION_POLICY%", retentionPolicy);
                        put("%TEST_CLASS_NAME%", testClassName);
                    }});
                    for (String[] extraParams : extraParamsCombinations) {
                        try {
                            ClassModel cm = compileAndReturn(fullFile, testClass, extraParams);
                            List<TypeAnnotation> actual = ReferenceInfoUtil.extendedAnnotationsOf(cm);
                            ReferenceInfoUtil.compare(expected, actual);
                            out.format("PASSED:  %s %s%n", testClassName, Arrays.toString(extraParams));
                            ++passed;
                        } catch (Throwable e) {
                            out.format("FAILED:  %s %s%n", testClassName, Arrays.toString(extraParams));
                            out.println(fullFile);
                            out.println("    " + e.toString());
                            e.printStackTrace(out);
                            ++failed;
                        }
                    }
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                out.println("FAILED:  " + method.getName());
                out.println("    " + e);
                e.printStackTrace(out);
                ++failed;
            }
        }

        out.println();
        int total = passed + failed;
        out.println(total + " total tests: " + passed + " PASSED, " + failed + " FAILED");

        out.flush();

        if (failed != 0)
            throw new RuntimeException(failed + " tests failed");
    }

    private Map<String, TypeAnnotation> expectedOf(Method m) {
        TADescription ta = m.getAnnotation(TADescription.class);
        TADescriptions tas = m.getAnnotation(TADescriptions.class);

        if (ta == null && tas == null)
            return null;

        Map<String, TypeAnnotation> result =
            new HashMap<>();

        if (ta != null)
            result.putAll(expectedOf(ta));

        if (tas != null) {
            for (TADescription a : tas.value()) {
                result.putAll(expectedOf(a));
            }
        }

        return result;
    }

    private Map<String, TypeAnnotation> expectedOf(TADescription d) {
        String annoName = d.annotation();
        TypeAnnotation.TargetInfo p = null;
        Label label = null;
        switch (d.type()) {
            case CAST -> {
                p = TypeAnnotation.TargetInfo.ofCastExpr(null, d.typeIndex());
            }
            case CLASS_EXTENDS -> {
                p = TypeAnnotation.TargetInfo.ofClassExtends(d.typeIndex());
            }
            case CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT -> {
                p = TypeAnnotation.TargetInfo.ofConstructorInvocationTypeArgument(null, d.typeIndex());
            }
            case CLASS_TYPE_PARAMETER -> {
                p = TypeAnnotation.TargetInfo.ofClassTypeParameter(d.paramIndex());
            }
            case CLASS_TYPE_PARAMETER_BOUND -> {
                p = TypeAnnotation.TargetInfo.ofClassTypeParameterBound(d.paramIndex(), d.boundIndex());
            }
            case EXCEPTION_PARAMETER -> {
                p = TypeAnnotation.TargetInfo.ofExceptionParameter(d.exceptionIndex());
            }
            case FIELD -> {
                p = TypeAnnotation.TargetInfo.ofField();
            }
            case INSTANCEOF -> {
                p = TypeAnnotation.TargetInfo.ofInstanceofExpr(null);
            }
            case LOCAL_VARIABLE -> {
                List<TypeAnnotation.LocalVarTargetInfo> table = new ArrayList<>();
                for (int idx = 0; idx < d.lvarOffset().length; ++idx)
                    table.add(TypeAnnotation.LocalVarTargetInfo.of(null, null, d.lvarIndex()[idx]));
                p = TypeAnnotation.TargetInfo.ofLocalVariable(table);
            }
            case METHOD_FORMAL_PARAMETER -> {
                p = TypeAnnotation.TargetInfo.ofMethodFormalParameter(d.paramIndex());
            }
            case METHOD_INVOCATION_TYPE_ARGUMENT -> {
                p = TypeAnnotation.TargetInfo.ofMethodInvocationTypeArgument(null, d.typeIndex());
            }
            case METHOD_RECEIVER -> {
                p = TypeAnnotation.TargetInfo.ofMethodReceiver();
            }
            case METHOD_RETURN -> {
                p = TypeAnnotation.TargetInfo.ofMethodReturn();
            }
            case METHOD_TYPE_PARAMETER -> {
                p = TypeAnnotation.TargetInfo.ofMethodTypeParameter(d.paramIndex());
            }
            case METHOD_TYPE_PARAMETER_BOUND -> {
                p = TypeAnnotation.TargetInfo.ofMethodTypeParameterBound(d.paramIndex(), d.boundIndex());
            }
            case NEW -> {
                p = TypeAnnotation.TargetInfo.ofNewExpr(null);
            }
            case RESOURCE_VARIABLE -> {
                List<TypeAnnotation.LocalVarTargetInfo> table = new ArrayList<>();
                for (int idx = 0; idx < d.lvarOffset().length; ++idx)
                    table.add(TypeAnnotation.LocalVarTargetInfo.of(null, null, d.lvarIndex()[idx]));
                p = TypeAnnotation.TargetInfo.ofResourceVariable(table);
            }
            case THROWS -> {
                p = TypeAnnotation.TargetInfo.ofThrows(d.typeIndex());
            }
            case CONSTRUCTOR_REFERENCE, CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT, METHOD_REFERENCE, METHOD_REFERENCE_TYPE_ARGUMENT -> {
            }
        }
        List<Integer> numPaths = wrapIntArray(d.genericLocation());
        List<TypeAnnotation.TypePathComponent> targetPaths = new ArrayList<>(numPaths.size()/2);
        int idx = 0;
        while (idx < numPaths.size()) {
            if (idx + 1 == numPaths.size()) throw new AssertionError("Could not decode type path: " + numPaths);
            targetPaths.add(fromBinary(numPaths.get(idx), numPaths.get(idx + 1)));
            idx += 2;
        }
        TypeAnnotation t = TypeAnnotation.of(p, targetPaths, ClassDesc.of(d.annotation()), AnnotationElement.ofInt("annotation", 0));
        return Collections.singletonMap(annoName, t);
    }

    private TypeAnnotation.TypePathComponent fromBinary(int tag, int arg) {
        if (arg != 0 && tag != 3) {
            throw new AssertionError("Invalid TypePathEntry tag/arg: " + tag + "/" + arg);
        } else {
            return switch (tag) {
                case 0 -> TypeAnnotation.TypePathComponent.ARRAY;
                case 1 -> TypeAnnotation.TypePathComponent.INNER_TYPE;
                case 2 -> TypeAnnotation.TypePathComponent.WILDCARD;
                case 3 -> TypeAnnotation.TypePathComponent.of(TypeAnnotation.TypePathComponent.Kind.TYPE_ARGUMENT, arg);
                default -> throw new AssertionError("Invalid TypePathEntryKind tag: " + tag);
            };
        }
    }

    private List<Integer> wrapIntArray(int[] ints) {
        List<Integer> list = new ArrayList<>(ints.length);
        for (int i : ints)
            list.add(i);
        return list;
    }

    private String getTestClassName(Method m, String retentionPolicy) {
        return format("%s_%s_%s", testObject.getClass().getSimpleName(),
                m.getName(), retentionPolicy);
    }

    private String testClassOf(Method m, String testClassName) {
        TestClass tc = m.getAnnotation(TestClass.class);
        if (tc != null) {
            return tc.value().replace("%TEST_CLASS_NAME%", testClassName);
        } else {
            return testClassName;
        }
    }

    private ClassModel compileAndReturn(String fullFile, String testClass, String... extraParams) throws Exception {
        File source = writeTestFile(fullFile, testClass);
        File clazzFile = compileTestFile(source, testClass, extraParams);
        return Classfile.of().parse(clazzFile.toPath());
    }

    protected File writeTestFile(String fullFile, String testClass) throws IOException {
        File f = new File(getClassDir(), format("%s.java", testClass));
        try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(f)))) {
            out.println(fullFile);
            return f;
        }
    }

    private String getClassDir() {
        return System.getProperty("test.classes", Objects.requireNonNull(Driver.class.getResource(".")).getPath());
    }

    protected File compileTestFile(File f, String testClass, String... extraParams) {
        List<String> options = new ArrayList<>();
        options.addAll(Arrays.asList(extraParams));
        options.add(f.getPath());
        int rc = com.sun.tools.javac.Main.compile(options.toArray(new String[options.size()]));
        if (rc != 0)
            throw new Error("compilation failed. rc=" + rc);
        String path = f.getParent() != null ? f.getParent() : "";
        return new File(path, format("%s.class", testClass));
    }

    private String wrap(String compact, Map<String, String> replacements) {
        StringBuilder sb = new StringBuilder();

        // Automatically import java.util
        sb.append("\nimport java.io.*;");
        sb.append("\nimport java.util.*;");
        sb.append("\nimport java.lang.annotation.*;");

        sb.append("\n\n");
        boolean isSnippet = !(compact.startsWith("class")
                              || compact.contains(" class"))
                            && !compact.contains("interface")
                            && !compact.contains("enum");
        if (isSnippet) {
            sb.append("class %TEST_CLASS_NAME% {\n");
            sb.append("class Nested {}\n");
        }

        sb.append(compact);
        sb.append("\n");

        if (isSnippet)
            sb.append("}\n\n");

        if (isSnippet) {
            // Have a few common nested types for testing
            sb.append("class Outer { class Inner {} class Middle { class MInner {} } }");
            sb.append("class SOuter { static class SInner {} }");
            sb.append("class GOuter<X, Y> { class GInner<X, Y> {} }");
        }

        // create A ... F annotation declarations
        sb.append("\n@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface A {}");
        sb.append("\n@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface B {}");
        sb.append("\n@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface C {}");
        sb.append("\n@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface D {}");
        sb.append("\n@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface E {}");
        sb.append("\n@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface F {}");

        // create TA ... TF proper type annotations
        sb.append("\n");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                " @Retention(RetentionPolicy.%RETENTION_POLICY%)  @interface TA {}");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface TB {}");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface TC {}");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface TD {}");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface TE {}");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface TF {}");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface TG {}");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface TH {}");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface TI {}");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface TJ {}");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface TK {}");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface TL {}");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface TM {}");

        // create RT?, RT?s for repeating type annotations
        sb.append("\n");
        sb.append("\n@Repeatable(RTAs.class) @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface RTA {}");
        sb.append("\n@Repeatable(RTBs.class) @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface RTB {}");
        sb.append("\n@Repeatable(RTCs.class) @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface RTC {}");
        sb.append("\n@Repeatable(RTDs.class) @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface RTD {}");
        sb.append("\n@Repeatable(RTEs.class) @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface RTE {}");
        sb.append("\n@Repeatable(RTFs.class) @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface RTF {}");
        sb.append("\n@Repeatable(RTGs.class) @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface RTG {}");
        sb.append("\n@Repeatable(RTHs.class) @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface RTH {}");
        sb.append("\n@Repeatable(RTIs.class) @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface RTI {}");
        sb.append("\n@Repeatable(RTJs.class) @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface RTJ {}");
        sb.append("\n@Repeatable(RTKs.class) @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface RTK {}");

        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface RTAs { RTA[] value(); }");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface RTBs { RTB[] value(); }");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface RTCs { RTC[] value(); }");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface RTDs { RTD[] value(); }");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface RTEs { RTE[] value(); }");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface RTFs { RTF[] value(); }");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface RTGs { RTG[] value(); }");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface RTHs { RTH[] value(); }");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface RTIs { RTI[] value(); }");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface RTJs { RTJ[] value(); }");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})" +
                "@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface RTKs { RTK[] value(); }");

        sb.append("\n@Target(value={ElementType.TYPE,ElementType.FIELD,ElementType.METHOD," +
                "ElementType.PARAMETER,ElementType.CONSTRUCTOR,ElementType.LOCAL_VARIABLE})");
        sb.append("\n@Retention(RetentionPolicy.%RETENTION_POLICY%) @interface Decl {}");

        return replaceAll(sb.toString(), replacements);
    }

    private String replaceAll(String src, Map<String, String> replacements) {
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            src = src.replace(entry.getKey(), entry.getValue());
        }
        return src;
    }

    public static final int NOT_SET = Integer.MIN_VALUE;

}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(TADescriptions.class)
@interface TADescription {
    String annotation();

    TypeAnnotation.TargetType type();
    int offset() default Driver.NOT_SET;
    int[] lvarOffset() default { };
    int[] lvarLength() default { };
    int[] lvarIndex() default { };
    int boundIndex() default Driver.NOT_SET;
    int paramIndex() default Driver.NOT_SET;
    int typeIndex() default Driver.NOT_SET;
    int exceptionIndex() default Driver.NOT_SET;

    int[] genericLocation() default {};
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface TADescriptions {
    TADescription[] value() default {};
}

/**
 * The name of the class that should be analyzed.
 * Should only need to be provided when analyzing inner classes.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface TestClass {
    String value();
}

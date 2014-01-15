/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.TypeAnnotation;
import com.sun.tools.classfile.TypeAnnotation.TargetType;

public class Driver {

    private static final PrintStream out = System.out;

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args.length > 1)
            throw new IllegalArgumentException("Usage: java Driver <test-name>");
        String name = args[0];
        Class<?> clazz = Class.forName(name);
        new Driver().runDriver(clazz.newInstance());
    }

    String[][] extraParamsCombinations = new String[][] {
        new String[] { },
        new String[] { "-g" },
    };

    protected void runDriver(Object object) throws Exception {
        int passed = 0, failed = 0;
        Class<?> clazz = object.getClass();
        out.println("Tests for " + clazz.getName());

        // Find methods
        for (Method method : clazz.getMethods()) {
            Map<String, TypeAnnotation.Position> expected = expectedOf(method);
            if (expected == null)
                continue;
            if (method.getReturnType() != String.class)
                throw new IllegalArgumentException("Test method needs to return a string: " + method);
            String testClass = testClassOf(method);

            for (String[] extraParams : extraParamsCombinations) {
                try {
                    String compact = (String)method.invoke(object);
                    String fullFile = wrap(compact);
                    ClassFile cf = compileAndReturn(fullFile, testClass, extraParams);
                    List<TypeAnnotation> actual = ReferenceInfoUtil.extendedAnnotationsOf(cf);
                    ReferenceInfoUtil.compare(expected, actual, cf);
                    out.println("PASSED:  " + method.getName());
                    ++passed;
                } catch (Throwable e) {
                    out.println("FAILED:  " + method.getName());
                    out.println("    " + e.toString());
                    ++failed;
                }
            }
        }

        out.println();
        int total = passed + failed;
        out.println(total + " total tests: " + passed + " PASSED, " + failed + " FAILED");

        out.flush();

        if (failed != 0)
            throw new RuntimeException(failed + " tests failed");
    }

    private Map<String, TypeAnnotation.Position> expectedOf(Method m) {
        TADescription ta = m.getAnnotation(TADescription.class);
        TADescriptions tas = m.getAnnotation(TADescriptions.class);

        if (ta == null && tas == null)
            return null;

        Map<String, TypeAnnotation.Position> result =
            new HashMap<String, TypeAnnotation.Position>();

        if (ta != null)
            result.putAll(expectedOf(ta));

        if (tas != null) {
            for (TADescription a : tas.value()) {
                result.putAll(expectedOf(a));
            }
        }

        return result;
    }

    private Map<String, TypeAnnotation.Position> expectedOf(TADescription d) {
        String annoName = d.annotation();

        TypeAnnotation.Position p = new TypeAnnotation.Position();
        p.type = d.type();
        if (d.offset() != NOT_SET)
            p.offset = d.offset();
        if (d.lvarOffset().length != 0)
            p.lvarOffset = d.lvarOffset();
        if (d.lvarLength().length != 0)
            p.lvarLength = d.lvarLength();
        if (d.lvarIndex().length != 0)
            p.lvarIndex = d.lvarIndex();
        if (d.boundIndex() != NOT_SET)
            p.bound_index = d.boundIndex();
        if (d.paramIndex() != NOT_SET)
            p.parameter_index = d.paramIndex();
        if (d.typeIndex() != NOT_SET)
            p.type_index = d.typeIndex();
        if (d.exceptionIndex() != NOT_SET)
            p.exception_index = d.exceptionIndex();
        if (d.genericLocation().length != 0) {
            p.location = TypeAnnotation.Position.getTypePathFromBinary(wrapIntArray(d.genericLocation()));
        }

        return Collections.singletonMap(annoName, p);
    }

    private List<Integer> wrapIntArray(int[] ints) {
        List<Integer> list = new ArrayList<Integer>(ints.length);
        for (int i : ints)
            list.add(i);
        return list;
    }

    private String testClassOf(Method m) {
        TestClass tc = m.getAnnotation(TestClass.class);
        if (tc != null) {
            return tc.value();
        } else {
            return "Test";
        }
    }

    private ClassFile compileAndReturn(String fullFile, String testClass, String... extraParams) throws Exception {
        File source = writeTestFile(fullFile);
        File clazzFile = compileTestFile(source, testClass);
        return ClassFile.read(clazzFile);
    }

    protected File writeTestFile(String fullFile) throws IOException {
        File f = new File("Test.java");
        PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(f)));
        out.println(fullFile);
        out.close();
        return f;
    }

    protected File compileTestFile(File f, String testClass, String... extraParams) {
        List<String> options = new ArrayList<>();
        options.addAll(Arrays.asList("-source", "1.8"));
        options.addAll(Arrays.asList(extraParams));
        options.add(f.getPath());
        int rc = com.sun.tools.javac.Main.compile(options.toArray(new String[options.size()]));
        if (rc != 0)
            throw new Error("compilation failed. rc=" + rc);
        String path;
        if (f.getParent() != null) {
            path = f.getParent();
        } else {
            path = "";
        }

        return new File(path + testClass + ".class");
    }

    private String wrap(String compact) {
        StringBuilder sb = new StringBuilder();

        // Automatically import java.util
        sb.append("\nimport java.util.*;");
        sb.append("\nimport java.lang.annotation.*;");

        sb.append("\n\n");
        boolean isSnippet = !(compact.startsWith("class")
                              || compact.contains(" class"))
                            && !compact.contains("interface")
                            && !compact.contains("enum");
        if (isSnippet)
            sb.append("class Test {\n");

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
        sb.append("\n@interface A {}");
        sb.append("\n@interface B {}");
        sb.append("\n@interface C {}");
        sb.append("\n@interface D {}");
        sb.append("\n@interface E {}");
        sb.append("\n@interface F {}");

        // create TA ... TF proper type annotations
        sb.append("\n");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface TA {}");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface TB {}");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface TC {}");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface TD {}");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface TE {}");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface TF {}");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface TG {}");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface TH {}");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface TI {}");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface TJ {}");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface TK {}");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface TL {}");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface TM {}");

        // create RTA, RTAs, RTB, RTBs for repeating type annotations
        sb.append("\n");
        sb.append("\n@Repeatable(RTAs.class) @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface RTA {}");
        sb.append("\n@Repeatable(RTBs.class) @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface RTB {}");

        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface RTAs { RTA[] value(); }");
        sb.append("\n@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface RTBs { RTB[] value(); }");

        sb.append("\n@Target(value={ElementType.TYPE,ElementType.FIELD,ElementType.METHOD,ElementType.PARAMETER,ElementType.CONSTRUCTOR,ElementType.LOCAL_VARIABLE})");
        sb.append("\n@interface Decl {}");

        return sb.toString();
    }

    public static final int NOT_SET = -888;

}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface TADescription {
    String annotation();

    TargetType type();
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
    String value() default "Test";
}

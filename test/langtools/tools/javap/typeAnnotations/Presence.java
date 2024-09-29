/*
 * Copyright (c) 2008, 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.io.*;
import java.lang.annotation.ElementType;
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;

/*
 * @test Presence
 * @bug 6843077
 * @summary test that all type annotations are present in the classfile
 * @enablePreview
 */

public class Presence {
    public static void main(String[] args) throws Exception {
        new Presence().run();
    }

    public void run() throws Exception {
        File javaFile = writeTestFile();
        File classFile = compileTestFile(javaFile);

        ClassModel cm = ClassFile.of().parse(classFile.toPath());
        test(cm);
        for (FieldModel fm : cm.fields()) {
            test(fm);
        }
        for (MethodModel mm: cm.methods()) {
            test(mm);
        }

        countAnnotations();

        if (errors > 0)
            throw new Exception(errors + " errors found");
        System.out.println("PASSED");
    }

    void test(AttributedElement m) {
        test(m, Attributes.runtimeVisibleTypeAnnotations());
        test(m, Attributes.runtimeInvisibleTypeAnnotations());
    }

    // test the result of AttributedElement.findAttribute according to expectations
    <T extends Attribute<T>> void test(AttributedElement m, AttributeMapper<T> attr_name) {
        Object attr_instance = m.findAttribute(attr_name).orElse(null);
        if (attr_instance != null) {
            switch (attr_instance) {
                case RuntimeVisibleTypeAnnotationsAttribute tAttr -> {
                    all += tAttr.annotations().size();
                    visibles += tAttr.annotations().size();
                }
                case RuntimeInvisibleTypeAnnotationsAttribute tAttr -> {
                    all += tAttr.annotations().size();
                    invisibles += tAttr.annotations().size();
                }
                default -> throw new AssertionError();
            }
        }
        if (m instanceof MethodModel) {
            attr_instance = m.findAttribute(Attributes.code()).orElse(null);
            if(attr_instance!= null) {
                CodeAttribute cAttr = (CodeAttribute)attr_instance;
                attr_instance = cAttr.findAttribute(attr_name).orElse(null);
                if(attr_instance!= null) {
                    switch (attr_instance) {
                        case RuntimeVisibleTypeAnnotationsAttribute tAttr -> {
                            all += tAttr.annotations().size();
                            visibles += tAttr.annotations().size();
                        }
                        case RuntimeInvisibleTypeAnnotationsAttribute tAttr -> {
                            all += tAttr.annotations().size();
                            invisibles += tAttr.annotations().size();
                        }
                        default -> throw new AssertionError();
                    }
                }
            }
        }
    }



    File writeTestFile() throws IOException {
        File f = new File("TestPresence.java");
        PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(f)));
        out.println("import java.util.*;");
        out.println("import java.lang.annotation.*;");

        out.println("class TestPresence<@TestPresence.A T extends @TestPresence.A List<@TestPresence.A String>> { ");
        out.println("  @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})");
        out.println("  @interface A { }");

        out.println("  Map<@A String, Map<@A String, @A String>> f1;");

        out.println("  <@A TM extends @A List<@A String>>");
        out.println("  Map<@A String, @A List<@A String>>");
        out.println("  method(@A TestPresence<T> this, List<@A String> @A [] param1, String @A [] @A ... param2)");
        out.println("  throws @A Exception {");
        out.println("    @A String lc1 = null;");
        out.println("    @A List<@A String> lc2 = null;");
        out.println("    @A String @A [] [] @A[] lc3 = null;");
        out.println("    List<? extends @A List<@A String>> lc4 = null;");
        out.println("    Object lc5 = (@A List<@A String>) null;");
        out.println("    boolean lc6 = lc1 instanceof @A String;");
        out.println("    boolean lc7 = lc5 instanceof @A String @A [] @A [];");
        out.println("    new @A ArrayList<@A String>();");
        out.println("    Object lc8 = new @A String @A [4];");
        out.println("    try {");
        out.println("      Object lc10 = int.class;");
        out.println("    } catch (@A Exception e) { e.toString(); }");
        out.println("    return null;");
        out.println("  }");
        out.println("  void vararg1(String @A ... t) { } ");
        out.println("}");
        out.close();
        return f;
    }

    File compileTestFile(File f) {
        int rc = com.sun.tools.javac.Main.compile(new String[] {"-g", f.getPath() });
        if (rc != 0)
            throw new Error("compilation failed. rc=" + rc);
        String path = f.getPath();
        return new File(path.substring(0, path.length() - 5) + ".class");
    }

    void countAnnotations() {
        int expected_visibles = 0, expected_invisibles = 38;
        int expected_all = expected_visibles + expected_invisibles;

        if (expected_all != all) {
            errors++;
            System.err.println("expected " + expected_all
                    + " annotations but found " + all);
        }

        if (expected_visibles != visibles) {
            errors++;
            System.err.println("expected " + expected_visibles
                    + " visibles annotations but found " + visibles);
        }

        if (expected_invisibles != invisibles) {
            errors++;
            System.err.println("expected " + expected_invisibles
                    + " invisibles annotations but found " + invisibles);
        }

    }

    int errors;
    int all;
    int visibles;
    int invisibles;
}

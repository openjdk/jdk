/*
 * Copyright (c) 2009, 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.classfile.*;
import java.lang.classfile.attribute.*;

/*
 * @test PresenceInner
 * @bug 6843077
 * @summary test that annotations in inner types count only once
 * @enablePreview
 */

public class PresenceInner {
    public static void main(String[] args) throws Exception {
        new PresenceInner().run();
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

        // counts are zero when vising outer class
        countAnnotations(0);

        // visit inner class
        File innerFile = new File("Test$1Inner.class");
        ClassModel icm = ClassFile.of().parse(innerFile.toPath());
        test(icm);
        for (FieldModel fm : icm.fields()) {
            test(fm);
        }
        for (MethodModel mm: icm.methods()) {
            test(mm);
        }

        countAnnotations(1);
        if (errors > 0)
            throw new Exception(errors + " errors found");
        System.out.println("PASSED");
    }

    void test(AttributedElement m) {
        test(m, Attributes.RUNTIME_VISIBLE_TYPE_ANNOTATIONS);
        test(m, Attributes.RUNTIME_INVISIBLE_TYPE_ANNOTATIONS);
    }

    // test the result of AttributedElement.findAttribute according to expectations
    <T extends Attribute<T>> void test(AttributedElement m, AttributeMapper<T> attr_name) {
        Attribute<T> attr_instance = m.findAttribute(attr_name).orElse(null);
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
    }

    File writeTestFile() throws IOException {
        File f = new File("Test.java");
        PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(f)));

        out.println("import java.lang.annotation.*;");
        out.println("class Test {");
        out.println("  void method() {");
        out.println("    class Inner<T extends @A Object> { }");
        out.println("  }");
        out.println("}");
        out.println("@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})");
        out.println("@interface A { }");
        out.close();
        System.out.println(f.getAbsolutePath());
        return f;
    }

    File compileTestFile(File f) {
        int rc = com.sun.tools.javac.Main.compile(new String[] {"-g", f.getPath() });
        if (rc != 0)
            throw new Error("compilation failed. rc=" + rc);
        String path = f.getPath();
        return new File(path.substring(0, path.length() - 5) + ".class");
    }

    void countAnnotations(int expected_invisibles) {
        int expected_visibles = 0;
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

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
import java.lang.classfile.*;
import java.lang.classfile.Attributes;
import java.lang.classfile.attribute.*;

/*
 * @test JSR175Annotations
 * @bug 6843077
 * @summary test that only type annotations are recorded as such in classfile
 * @enablePreview
 */

public class JSR175Annotations {
    public static void main(String[] args) throws Exception {
        new JSR175Annotations().run();
    }

    public void run() throws Exception {
        File javaFile = writeTestFile();
        File classFile = compileTestFile(javaFile);

        ClassModel cm = ClassFile.of().parse(classFile.toPath());
        for (MethodModel mm: cm.methods()) {
            test(mm);
        }
        for (FieldModel fm: cm.fields()) {
            test(fm);
        }

        countAnnotations();

        if (errors > 0)
            throw new Exception(errors + " errors found");
        System.out.println("PASSED");
    }

    void test(AttributedElement m) {
        test(m, Attributes.RUNTIME_VISIBLE_TYPE_ANNOTATIONS);
        test(m, Attributes.RUNTIME_INVISIBLE_TYPE_ANNOTATIONS);
    }

    // test the result of AttributedElement.findAttribute according to expectations
    <T extends java.lang.classfile.Attribute<T>> void test(AttributedElement m, AttributeMapper<T> attr_name) {
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
        out.println("abstract class Test { ");
        out.println("  @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})");
        out.println("  @Retention(RetentionPolicy.RUNTIME)");
        out.println("  @interface A { }");
        out.println("  @A String m;");
        out.println("  @A String method(@A String a) {");
        out.println("    return a;");
        out.println("  }");
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
        int expected_visibles = 0, expected_invisibles = 0;
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

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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URL;
import java.util.List;

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;

/*
 * @test NoTargetAnnotations
 * @summary test that annotations with no Target meta type is emitted
 *          only once as declaration annotation
 * @enablePreview
 * @modules java.base/jdk.internal.classfile.impl
 */
public class NoTargetAnnotations extends ClassfileTestHelper {

    public static void main(String[] args) throws Exception {
        new NoTargetAnnotations().run();
    }

    public void run() throws Exception {
        ClassModel cm = getClassFile("NoTargetAnnotations$Test.class");
        for (FieldModel fm : cm.fields()) {
            test(fm);
            testDeclaration(fm);
        }
        for (MethodModel mm: cm.methods()) {
            test(mm);
            testDeclaration(mm);
        }

        countAnnotations();

        if (errors > 0)
            throw new Exception(errors + " errors found");
        System.out.println("PASSED");
    }

    ClassModel getClassFile(String name) throws IOException {
        URL url = getClass().getResource(name);
        assert url != null;
        try (InputStream in = url.openStream()) {
            return ClassFile.of().parse(in.readAllBytes());
        }
    }


    void testDeclaration(AttributedElement m) {
        testDecl(m, Attributes.runtimeVisibleAnnotations());
        testDecl(m, Attributes.runtimeInvisibleAnnotations());
    }

    // test the result of AttributedElement.findAttribute according to expectations
    // encoded in the method's name
    <T extends Attribute<T>> void testDecl(AttributedElement m, AttributeMapper<T> name) {
        Attribute<T> attr = m.findAttribute(name).orElse(null);
        if (attr != null) {
            switch (attr) {
                case RuntimeVisibleAnnotationsAttribute tAttr -> {
                    this.declAnnotations += tAttr.annotations().size();
                }
                case RuntimeInvisibleAnnotationsAttribute tAttr -> {
                    this.declAnnotations += tAttr.annotations().size();
                }
                default -> throw new AssertionError();
            }
        }
    }

    File compileTestFile(File f) {
        int rc = com.sun.tools.javac.Main.compile(new String[] { "-XDTA:writer", "-g", f.getPath() });
        if (rc != 0)
            throw new Error("compilation failed. rc=" + rc);
        String path = f.getPath();
        return new File(path.substring(0, path.length() - 5) + ".class");
    }

    void countAnnotations() {
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

        if (expected_decl != declAnnotations) {
            errors++;
            System.err.println("expected " + expected_decl
                    + " declaration annotations but found " + declAnnotations);
        }
    }

    int errors;
    int all;
    int visibles;
    int invisibles;

    int declAnnotations;

    /*********************** Test class *************************/
    static int expected_invisibles = 0;
    static int expected_visibles = 0;
    static int expected_decl = 1;

    static class Test {
        @Retention(RetentionPolicy.RUNTIME)
        @interface A {}

        @A String method() {
            return null;
        }
    }
}

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

/*
 * @test 6716452
 * @summary need a method to get an index of an attribute
 * @enablePreview
 */

import java.io.*;
import java.nio.file.Files;

import java.lang.classfile.*;
import java.lang.classfile.attribute.*;

public class T6716452 {
    public static void main(String[] args) throws Exception {
        new T6716452().run();
    }

    public void run() throws Exception {
        File javaFile = writeTestFile();
        File classFile = compileTestFile(javaFile);
        ClassModel cm = ClassFile.of().parse(classFile.toPath());
        for (MethodModel mm: cm.methods()) {
            test(mm);
        }

        if (errors > 0)
            throw new Exception(errors + " errors found");
    }

    void test(MethodModel mm) {
        test(mm, Attributes.CODE, CodeAttribute.class);
        test(mm, Attributes.EXCEPTIONS, ExceptionsAttribute.class);
    }

    // test the result of MethodModel.findAttribute, MethodModel.attributes().indexOf() according to expectations
    // encoded in the method's name
    <T extends Attribute<T>> void test(MethodModel mm, AttributeMapper<T> attr, Class<?> c) {
        Attribute<T> attr_instance = mm.findAttribute(attr).orElse(null);
        int index = mm.attributes().indexOf(attr_instance);
        String mm_name = mm.methodName().stringValue();
        System.err.println("Method " + mm_name + " name:" + attr.name() + " index:" + index + " class: " + c);
        boolean expect = (mm_name.equals("<init>") && attr.name().equals("Code"))
                || (mm_name.contains(attr.name()));
        boolean found = (index != -1);
        if (expect) {
            if (found) {
                if (!c.isAssignableFrom(mm.attributes().get(index).getClass())) {
                    error(mm + ": unexpected attribute found,"
                            + " expected " + c.getName()
                            + " found " + mm.attributes().get(index).attributeName());
                }
            } else {
                error(mm + ": expected attribute " + attr.name() + " not found");
            }
        } else {
            if (found) {
                error(mm + ": unexpected attribute " + attr.name());
            }
        }
    }

    File writeTestFile() throws IOException {
        File f = new File("Test.java");
        PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(f)));
        out.println("abstract class Test { ");
        out.println("  abstract void m();");
        out.println("  void m_Code() { }");
        out.println("  abstract void m_Exceptions() throws Exception;");
        out.println("  void m_Code_Exceptions() throws Exception { }");
        out.println("}");
        out.close();
        return f;
    }

    File compileTestFile(File f) {
        int rc = com.sun.tools.javac.Main.compile(new String[] { "-g", f.getPath() });
        if (rc != 0)
            throw new Error("compilation failed. rc=" + rc);
        String path = f.getPath();
        return new File(path.substring(0, path.length() - 5) + ".class");
    }

    void error(String msg) {
        System.err.println("error: " + msg);
        errors++;
    }

    int errors;
}

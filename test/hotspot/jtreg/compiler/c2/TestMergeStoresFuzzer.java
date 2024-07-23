/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8318446 8335392
 * @summary Test merging of consecutive stores, and more specifically the MemPointer.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @compile ../lib/ir_framework/TestFramework.java
 * @run driver TestMergeStoresFuzzer
 */

import compiler.lib.ir_framework.*;
import compiler.lib.compile_framework.CompileFramework;
import compiler.lib.compile_framework.JavaSourceFromString;

import java.io.StringWriter;
import java.io.PrintWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

public class TestMergeStoresFuzzer {

    public static String generate() {
        StringWriter writer = new StringWriter();
        PrintWriter out = new PrintWriter(writer);
        out.println("import compiler.lib.ir_framework.*;");
        out.println("");
        out.println("public class XYZ {");
        out.println("    public static void main(String args[]) {");
        out.println("        System.out.println(\"This is in another java file\");");
        out.println("        TestFramework.run(XYZ.class);");
        out.println("        System.out.println(\"Done with IR framework.\");");
        out.println("    }");
        out.println("");
        out.println("    @Test");
        out.println("    static void test() {");
        out.println("        throw new RuntimeException(\"xyz\");");
        out.println("    }");
        out.println("}");
        out.close();
        return writer.toString();
    }

    public static void main(String args[]) throws IOException {
        String src = generate();
        JavaSourceFromString file = new JavaSourceFromString("XYZ", src);

        CompileFramework comp = new CompileFramework();
        comp.add(file);

        comp.compile();

        Class c = comp.getClass("XYZ");

        try {
            c.getDeclaredMethod("main", new Class[] { String[].class }).invoke(null, new Object[] { null });
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("No such method:", e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Illegal access:", e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Invocation target:", e);
        }
    }
}

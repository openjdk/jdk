/*
 * Copyright (c) 2026, Google LLC and/or its affiliates. All rights reserved.
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
 * @summary test lambda deserialization for Object method references on interfaces
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.javap
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main SerializableObjectMethodReferencesOnInterfaces
 */

import java.io.Serializable;
import java.lang.classfile.*;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

public class SerializableObjectMethodReferencesOnInterfaces {

    public static void main(String... args) throws Exception {
        new SerializableObjectMethodReferencesOnInterfaces().run();
    }

    static class Test {
        interface I1 extends Serializable {}

        interface I2 extends I1 {
            @Override
            public int hashCode();
        }

        interface F<T, R> extends Serializable {
            R apply(T t);
        }

        void f() throws Exception {
            F<I1, Integer> f1 = I1::hashCode;
            F<I2, Integer> f2 = I2::hashCode;
        }
    }

    public void run() throws Exception {
        URL url =
                SerializableObjectMethodReferencesOnInterfaces.class.getResource(
                        "SerializableObjectMethodReferencesOnInterfaces$Test.class");
        Path file = Paths.get(url.toURI());

        ClassModel cf = ClassFile.of().parse(file);
        String actual = printDeserializationTests(cf);
        String expected =
                """
                getImplMethodKind 5
                getFunctionalInterfaceClass SerializableObjectMethodReferencesOnInterfaces$Test$F
                getFunctionalInterfaceMethodName apply
                getFunctionalInterfaceMethodSignature (Ljava/lang/Object;)Ljava/lang/Object;
                getImplClass java/lang/Object
                getImplMethodSignature ()I
                getImplMethodKind 5
                getFunctionalInterfaceClass SerializableObjectMethodReferencesOnInterfaces$Test$F
                getFunctionalInterfaceMethodName apply
                getFunctionalInterfaceMethodSignature (Ljava/lang/Object;)Ljava/lang/Object;
                getImplClass java/lang/Object
                getImplMethodSignature ()I
                """;
        if (!actual.equals(expected)) {
            throw new AssertionError(
                    "Unexpected deserialization tests, expected:\n"
                            + expected
                            + "\nactual:\n"
                            + actual);
        }
    }

    private static String printDeserializationTests(ClassModel cf) {
        MethodModel m =
                cf.methods().stream()
                        .filter(
                                x ->
                                        x.methodName()
                                                .stringValue()
                                                .contentEquals("$deserializeLambda$"))
                        .findFirst()
                        .get();
        Iterator<CodeElement> it = m.code().get().iterator();
        StringBuilder sb = new StringBuilder();
        while (it.hasNext()) {
            CodeElement curr = it.next();
            if (curr instanceof InvokeInstruction i
                    && i.method()
                            .owner()
                            .asInternalName()
                            .contentEquals("java/lang/invoke/SerializedLambda")) {
                CodeElement next = it.next();
                if (next instanceof ConstantInstruction c) {
                    sb.append(
                            i.method().name().stringValue()
                                    + " "
                                    + c.constantValue().toString()
                                    + "\n");
                }
            }
        }
        return sb.toString();
    }
}

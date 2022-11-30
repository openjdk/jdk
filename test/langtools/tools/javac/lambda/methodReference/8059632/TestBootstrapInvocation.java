/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8059632
 * @summary Method reference compilation uses incorrect qualifying type
 * @library /tools/javac/lib
 * @modules jdk.jdeps/com.sun.tools.classfile
 *          jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.jvm
 */

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import javax.tools.Diagnostic;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import com.sun.tools.classfile.Attribute;
import com.sun.tools.classfile.BootstrapMethods_attribute;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.javac.api.JavacTaskImpl;
import static com.sun.tools.javac.jvm.ClassFile.*;
import com.sun.tools.classfile.ConstantPool.*;

public class TestBootstrapInvocation {

    public static void main(String... args) throws Exception {
        JavaCompiler comp = ToolProvider.getSystemJavaCompiler();
        new TestBootstrapInvocation().run(comp);
    }

    DiagChecker dc;

    TestBootstrapInvocation() {
        dc = new DiagChecker();
    }

    public void run(JavaCompiler comp) {
        JavaSource source = new JavaSource();
        JavacTaskImpl ct = (JavacTaskImpl)comp.getTask(null, null, dc,
                Arrays.asList("-g"), null, Arrays.asList(source));
        try {
            ct.generate();
        } catch (Throwable t) {
            t.printStackTrace();
            throw new AssertionError(
                    String.format("Error thrown when compiling following code\n%s",
                            source.source));
        }
        if (dc.diagFound) {
            throw new AssertionError(
                    String.format("Diags found when compiling following code\n%s\n\n%s",
                            source.source, dc.printDiags()));
        }
        verifyBytecode();
    }

    void verifyBytecode() {
        File compiledTest = new File("Test.class");
        try {
            ClassFile cf = ClassFile.read(compiledTest);
            BootstrapMethods_attribute bsm_attr =
                    (BootstrapMethods_attribute)cf
                            .getAttribute(Attribute.BootstrapMethods);
            int length = bsm_attr.bootstrap_method_specifiers.length;
            if (length != 1) {
                throw new Error("Bad number of method specifiers " +
                        "in BootstrapMethods attribute: " + length);
            }
            BootstrapMethods_attribute.BootstrapMethodSpecifier bsm_spec =
                    bsm_attr.bootstrap_method_specifiers[0];

            if (bsm_spec.bootstrap_arguments.length != 3) {
                throw new Error("Bad number of static invokedynamic args " +
                        "in BootstrapMethod attribute");
            }
            CONSTANT_MethodHandle_info mh =
                    (CONSTANT_MethodHandle_info)cf.constant_pool.get(bsm_spec.bootstrap_arguments[1]);

            if (mh.reference_kind != RefKind.REF_invokeVirtual) {
                throw new Error("Bad invoke kind in implementation method handle: " + mh.reference_kind);
            }
            if (!mh.getCPRefInfo().getClassName().equals("Test$C")) {
                throw new Error("Unexpected class name: " + mh.getCPRefInfo().getClassName());
            }
            if (!mh.getCPRefInfo().getNameAndTypeInfo().getName().equals("m")) {
                throw new Error("Unexpected method referenced in method handle in bootstrap section: " +
                                     mh.getCPRefInfo().getNameAndTypeInfo().getName());
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new Error("error reading " + compiledTest +": " + e);
        }
    }

    class JavaSource extends SimpleJavaFileObject {

        static final String source =

            """
            class Test {
                interface I { void m(); }
                abstract class C implements I {}
                public void test(C arg) {
                    Runnable r = arg::m;
                }
            }
            """;

        JavaSource() {
            super(URI.create("myfo:/Test.java"), JavaFileObject.Kind.SOURCE);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    static class DiagChecker
            implements javax.tools.DiagnosticListener<JavaFileObject> {

        boolean diagFound;
        ArrayList<String> diags = new ArrayList<>();

        public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
            diags.add(diagnostic.getMessage(Locale.getDefault()));
            diagFound = true;
        }

        String printDiags() {
            StringBuilder buf = new StringBuilder();
            for (String s : diags) {
                buf.append(s);
                buf.append("\n");
            }
            return buf.toString();
        }
    }
}

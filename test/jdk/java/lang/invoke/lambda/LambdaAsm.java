/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8027232
 * @library /test/lib/
 * @modules jdk.zipfs
 * @enablePreview
 * @compile LambdaAsm.java
 * @run main/othervm LambdaAsm
 * @summary ensures that j.l.i.InvokerByteCodeGenerator and Class-File API
 * generate bytecodes with correct constant pool references
 */
import java.io.IOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.constantpool.ConstantPool;
import java.lang.classfile.constantpool.MethodRefEntry;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.process.OutputAnalyzer;

import static java.lang.constant.ConstantDescs.*;
import static java.lang.classfile.ClassFile.*;
import static java.nio.file.Files.*;
import static jdk.test.lib.process.ProcessTools.*;

public class LambdaAsm {
    static final Path DUMP_LAMBDA_PROXY_CLASS_FILES = Path.of("DUMP_LAMBDA_PROXY_CLASS_FILES");
    static final Path SRC = Path.of("src");
    static final Path CLASSES = Path.of("classes");

    static void init() throws Exception {
        emitCode();
        CompilerUtils.compile(SRC, CLASSES);
        OutputAnalyzer outputAnalyzer = executeProcess(createTestJavaProcessBuilder(
                "-Djdk.invoke.LambdaMetafactory.dumpProxyClassFiles=true",
                "-cp", CLASSES.toString(), "A"));
        outputAnalyzer.shouldHaveExitValue(0);
    }

    static void emitCode() throws IOException {
        ArrayList<String> scratch = new ArrayList<>();
        scratch.add("import java.util.function.*;");
        scratch.add("class A {");
        scratch.add("   interface I {");
        scratch.add("       default Supplier<Integer> a() { return () -> 1; }");
        scratch.add("       default Supplier<Integer> b(int i) { return () -> i; }");
        scratch.add("       default Supplier<Integer> c(int i) { return () -> m(i); }");
        scratch.add("       int m(int i);");
        scratch.add("       static Integer d() { return 0; }");
        scratch.add("   }");
        scratch.add("   static class C implements I {");
        scratch.add("       public int m(int i) { return i;}");
        scratch.add("   }");
        scratch.add("   public static void main(String[] args) {");
        scratch.add("       I i = new C();");
        scratch.add("       i.a();");
        scratch.add("       i.b(1);");
        scratch.add("       i.c(1);");
        scratch.add("       I.d();");
        scratch.add("   }");
        scratch.add("}");

        Path testFile = SRC.resolve("A.java");
        Files.createDirectories(SRC);
        Files.write(testFile, scratch, Charset.defaultCharset());
    }

    static void checkMethod(String cname, String mname, ConstantPool cp,
            CodeAttribute code) throws IllegalArgumentException {
        for (var inst : code.elements()) {
            if (inst instanceof InvokeInstruction inv && (inv.opcode() == Opcode.INVOKESPECIAL
                    || inv.opcode() == Opcode.INVOKEINTERFACE)) {
                var ref = inv.method();
                System.out.println("Verifying " + cname + ":" + mname +
                        " instruction:" + inv.opcode() + " index @" + ref.index());
                if (ref instanceof MethodRefEntry) {
                    throw new RuntimeException("unexpected CP type expected "
                            + "InterfaceMethodRef, got MethodRef, " + cname
                            + ", " + mname);
                }
            }
        }
    }

    static int checkMethod(ClassModel cf, String mthd) throws Exception {
        if (cf.majorVersion() < 52) {
            throw new RuntimeException("unexpected class file version, in "
                    + cf.thisClass().asInternalName() + "expected 52, got "
                    + cf.majorVersion());
        }
        int count = 0;
        for (var m : cf.methods()) {
            String mname = m.methodName().stringValue();
            if (mname.equals(mthd)) {
                for (var a : m.findAttributes(Attributes.code())) {
                    count++;
                    checkMethod(cf.thisClass().asInternalName(), mname,
                            cf.constantPool(), a);
                }
            }
        }
        return count;
    }

    static void verifyInvokerBytecodeGenerator() throws Exception {
        int count = 0;
        int mcount = 0;
        try (DirectoryStream<Path> ds = newDirectoryStream(DUMP_LAMBDA_PROXY_CLASS_FILES,
                // filter in lambda proxy classes
                "A$I$$Lambda.*.class")) {
            for (Path p : ds) {
                System.out.println(p.toFile());
                ClassModel cm = ClassFile.of().parse(p);
                // Check those methods implementing Supplier.get
                mcount += checkMethod(cm, "get");
                count++;
            }
        }
        if (count < 3) {
            throw new RuntimeException("unexpected number of files, "
                    + "expected atleast 3 files, but got only " + count);
        }
        if (mcount < 3) {
            throw new RuntimeException("unexpected number of methods, "
                    + "expected atleast 3 methods, but got only " + mcount);
        }
    }

    static void verifyASM() throws Exception {
        var functionDesc = ClassDesc.ofInternalName("java/util/function/Function");
        byte[] carray = ClassFile.of().build(ClassDesc.of("X"), clb -> clb
                .withVersion(JAVA_8_VERSION, 0)
                .withFlags(ACC_PUBLIC)
                .withSuperclass(CD_Object)
                .withMethodBody("foo", MTD_void, ACC_STATIC, cob -> cob
                        .invokestatic(functionDesc, "identity", MethodTypeDesc.of(functionDesc), true)
                )
        );
        // for debugging
        // write((new File("X.class")).toPath(), carray, CREATE, TRUNCATE_EXISTING);

        // verify using javap/classfile reader
        ClassModel cm = ClassFile.of().parse(carray);
        int mcount = checkMethod(cm, "foo");
        if (mcount < 1) {
            throw new RuntimeException("unexpected method count, expected 1" +
                    "but got " + mcount);
        }
    }

    public static void main(String... args) throws Exception {
        init();
        verifyInvokerBytecodeGenerator();
        verifyASM();
    }
}

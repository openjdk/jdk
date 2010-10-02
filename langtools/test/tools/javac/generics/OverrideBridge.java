/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6337171
 * @summary  javac should create bridge methods when type variable bounds restricted
 * @run main OverrideBridge
 */

import java.io.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

import com.sun.source.util.JavacTask;
import com.sun.tools.classfile.ClassFile;
import com.sun.tools.classfile.ConstantPoolException;
import com.sun.tools.classfile.Descriptor.InvalidDescriptor;
import com.sun.tools.classfile.Method;

public class OverrideBridge {

    enum Implementation {
        IMPLICIT(""),
        EXPLICIT("@Override public abstract X m(X x);");

        String impl;

        Implementation(String impl) {
            this.impl = impl;
        }
    }

    static class JavaSource extends SimpleJavaFileObject {

        final static String sourceStub =
                        "abstract class A<X> {\n" +
                        "   public abstract X m(X x);\n" +
                        "}\n" +
                        "interface I<X> {\n" +
                        "X m(X x);\n" +
                        "}\n" +
                        "abstract class B<X extends B<X>> extends A<X> implements I<X> { #B }\n" +
                        "abstract class C<X extends C<X>> extends B<X>  { #C }\n" +
                        "abstract class D<X extends D<X>> extends C<X>  { #D }\n";

        String source;

        public JavaSource(Implementation implB, Implementation implC, Implementation implD) {
            super(URI.create("myfo:/Test.java"), JavaFileObject.Kind.SOURCE);
            source = sourceStub.replace("#B", implB.impl).replace("#C", implC.impl).replace("#D", implD.impl);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    public static void main(String... args) throws Exception {
        Map<ClassFile, List<Method>> refMembers =
                compile(Implementation.EXPLICIT, Implementation.EXPLICIT, Implementation.EXPLICIT, "ref");
        int i = 0;
        for (Implementation implB : Implementation.values()) {
            for (Implementation implC : Implementation.values()) {
                for (Implementation implD : Implementation.values()) {
                    Map<ClassFile, List<Method>> membersToCheck = compile(implB, implC, implD, "out_" + i++);
                    check(refMembers, membersToCheck);
                }
            }
        }
    }

    static String workDir = System.getProperty("user.dir");

    static Map<ClassFile, List<Method>> compile(Implementation implB, Implementation implC, Implementation implD, String destPath) throws Exception {
        File destDir = new File(workDir, destPath); destDir.mkdir();
        final JavaCompiler tool = ToolProvider.getSystemJavaCompiler();
        JavaSource source = new JavaSource(implB, implC, implD);
        JavacTask ct = (JavacTask)tool.getTask(null, null, null,
                Arrays.asList("-d", destPath), null, Arrays.asList(source));
        ct.generate();
        Map<ClassFile, List<Method>> members = new HashMap<>();
        addMembers(destDir, members);
        return members;
    }

    static void addMembers(File destDir, Map<ClassFile, List<Method>> members) {
        String[] names = { "B.class", "C.class", "D.class" };
        try {
            for (String name : names) {
                File f = new File(destDir, name);
                ClassFile cf = ClassFile.read(f);
                members.put(cf, readMethod(cf, "m"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new Error("error reading classes");
        }
    }

    static List<Method> readMethod(ClassFile cf, String name) throws ConstantPoolException {
        List<Method> buf = new ArrayList<>();
        for (Method m : cf.methods) {
            if (m.getName(cf.constant_pool).equals(name)) {
                buf.add(m);
            }
        }
        return buf;
    }

    static void check(Map<ClassFile, List<Method>> refMembers, Map<ClassFile, List<Method>> membersToCheck) throws ConstantPoolException, InvalidDescriptor {
        for (Map.Entry<ClassFile, List<Method>> ref : refMembers.entrySet()) {
            ClassFile cRef = ref.getKey();
            for (Method mRef : ref.getValue()) {
                boolean ok = false;
                for (Map.Entry<ClassFile, List<Method>> toCheck : membersToCheck.entrySet()) {
                    ClassFile cToCheck = toCheck.getKey();
                    for (Method mToCheck : toCheck.getValue()) {
                        if (cRef.getName().equals(cToCheck.getName()) &&
                                mRef.descriptor.getReturnType(cRef.constant_pool).equals(
                                mToCheck.descriptor.getReturnType(cToCheck.constant_pool)) &&
                                mRef.descriptor.getParameterTypes(cRef.constant_pool).equals(
                                mToCheck.descriptor.getParameterTypes(cToCheck.constant_pool))) {
                            ok = true;
                        }
                    }
                }
                if (!ok) {
                    throw new AssertionError("Matching method descriptor for " + mRef.descriptor.getParameterTypes(cRef.constant_pool) + "not found");
                }
            }
        }
    }
}

/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @bug 8322726
 * @library /test/lib
 * @library /asm
 *
 * @compile TestMHUnloaded.java TestMHUnloadedHelper.java
 * @run driver jdk.test.lib.helpers.ClassFileInstaller compiler.runtime.unloaded.TestMHUnloadedHelper
 *             jdk.internal.org.objectweb.asm.ClassWriter jdk.internal.org.objectweb.asm.ClassVisitor
 *             jdk.internal.org.objectweb.asm.SymbolTable jdk.internal.org.objectweb.asm.SymbolTable$Entry
 *             jdk.internal.org.objectweb.asm.Symbol jdk.internal.org.objectweb.asm.ByteVector
 *             jdk.internal.org.objectweb.asm.MethodWriter jdk.internal.org.objectweb.asm.MethodVisitor
 *             jdk.internal.org.objectweb.asm.Type jdk.internal.org.objectweb.asm.Label
 *             jdk.internal.org.objectweb.asm.Handler jdk.internal.org.objectweb.asm.Attribute
 *             jdk.internal.org.objectweb.asm.AnnotationWriter jdk.internal.org.objectweb.asm.AnnotationVisitor
 *
 * @run main/othervm -Xbootclasspath/a:.
 *                   -Xbatch -XX:-TieredCompilation -XX:CompileCommand=exclude,*::test
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+PrintCompilation -XX:+PrintInlining
 *                      compiler.runtime.unloaded.TestMHUnloaded
 *
 * @run main/othervm -Xbootclasspath/a:.
 *                   -Xbatch -XX:-TieredCompilation -XX:CompileCommand=exclude,*::test
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+PrintCompilation -XX:+PrintInlining
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:+AlwaysIncrementalInline
 *                      compiler.runtime.unloaded.TestMHUnloaded
 */

package compiler.runtime.unloaded;

import java.lang.invoke.MethodHandles;

public class TestMHUnloaded {
    public static void main(String[] args) {
        TestMHUnloadedHelper.test(MethodHandles.lookup()); // launch test in bootstrap loader context
        TestMHUnloadedHelper.testConstant(MethodHandles.lookup()); // launch test in bootstrap loader context
        System.out.println("TEST PASSED");
    }
}

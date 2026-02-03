/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8332934
 * @summary Incorrect defined locals escaped from continue in do loop
 * @library /tools/lib /test/lib
 * @run main DoLoopLocalEscapeThroughContinueTest
 */

import jdk.test.lib.ByteCodeLoader;
import jdk.test.lib.compiler.InMemoryJavaCompiler;

import java.lang.classfile.ClassFile;
import java.lang.invoke.MethodHandles;

public class DoLoopLocalEscapeThroughContinueTest {
    public static void main(String... args) throws Throwable {
        String source = """
                static void main(String[] k) {
                  do {
                    int b = 1;
                    continue;
                  } while (Math.random() > 0.5D) ;
                  switch (2) {
                  case 3:
                    double d;
                  case 4:
                    k.toString();
                  }
                }
                """;
        var bytes = InMemoryJavaCompiler.compile("Test", source, "-XDdebug.code");
        System.out.println(ClassFile.of().parse(bytes).toDebugString());
        var clz = ByteCodeLoader.load("Test", bytes);
        MethodHandles.privateLookupIn(clz, MethodHandles.lookup()).ensureInitialized(clz); // force verification
    }
}

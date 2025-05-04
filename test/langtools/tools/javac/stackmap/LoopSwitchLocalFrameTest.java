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
 * @bug 8329534 8332934
 * @summary Redundant stack map case
 * @library /tools/lib /test/lib
 * @run main LoopSwitchLocalFrameTest
 */

import jdk.test.lib.ByteCodeLoader;
import jdk.test.lib.compiler.InMemoryJavaCompiler;

import java.lang.invoke.MethodHandles;

void main() throws Throwable {
    final String source = """
            static void main(String[] k) {
              do {
                int b = 1;
                continue;
              } while (k.length > -1);
              switch (2) {
              case 3:
                double d;
              case 4:
                k.toString();
              }
            }
            """;
    var bytes = InMemoryJavaCompiler.compile("Test", source);
    var clz = ByteCodeLoader.load("Test", bytes);
    MethodHandles.lookup().ensureInitialized(clz); // force verification
}

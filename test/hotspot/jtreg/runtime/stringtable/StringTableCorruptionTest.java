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
 * @bug 8333356
 * @summary Verify new error message for corrupting string table contents.
 * @requires vm.flagless
 * @modules java.base/java.lang:open
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @run driver StringTableCorruptionTest test
 */

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class StringTableCorruptionTest {
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder("--add-opens", "java.base/java.lang=ALL-UNNAMED",
                                                             "-XX:-CreateCoredumpOnCrash", "StringTableCorruptionTest");
            OutputAnalyzer output = new OutputAnalyzer(pb.start());
            output.shouldContain("Node hash code has changed possibly due to corruption of the contents.");
            output.shouldNotHaveExitValue(0);
            return;
        }

        Field f = String.class.getDeclaredField("value");
        f.setAccessible(true);
        f.set("s1".intern(), f.get("s2"));
        for (int i = 0; i < 4_000_000; i++) {
            ("s_" + i).intern();
        }
    }
}

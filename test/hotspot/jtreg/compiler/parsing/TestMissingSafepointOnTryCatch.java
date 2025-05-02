/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8313626
 * @summary  assert(false) failed: malformed control flow to missing safepoint on backedge of a try-catch
 * @library /test/lib
 * @compile MissingSafepointOnTryCatch.jasm
 * @run main/othervm -XX:CompileCommand=quiet
 *      -XX:CompileCommand=compileonly,MissingSafepointOnTryCatch::test*
 *      -XX:CompileCommand=dontinline,MissingSafepointOnTryCatch::m
 *      -XX:CompileCommand=inline,MissingSafepointOnTryCatch::th
 *      -XX:-TieredCompilation -Xcomp TestMissingSafepointOnTryCatch
 */

import jdk.test.lib.Utils;

public class TestMissingSafepointOnTryCatch {

    public static void infiniteLoop() {
        try {
            Thread thread = new Thread() {
                public void run() {
                    MissingSafepointOnTryCatch.testInfinite();
                }
            };
            thread.setDaemon(true);
            thread.start();
            Thread.sleep(Utils.adjustTimeout(500));
        } catch (Exception e) {}
    }

    public static void main(String[] args) {
        try {
            // to make sure java/lang/Exception class is resolved
            MissingSafepointOnTryCatch.th();
        } catch (Exception e) {}
        MissingSafepointOnTryCatch.test1();
        MissingSafepointOnTryCatch.test2();
        MissingSafepointOnTryCatch.test3();
        MissingSafepointOnTryCatch.test4();
        infiniteLoop();
    }
}

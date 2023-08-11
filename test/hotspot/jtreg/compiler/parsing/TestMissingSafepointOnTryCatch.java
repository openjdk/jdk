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
 * @run main/othervm -Xcomp  -XX:CompileCommand=compileonly,MissingSafepointOnTryCatch::test* -XX:CompileCommand=dontinline,MissingSafepointOnTryCatch::m
 * -XX:-TieredCompilation -XX:CompileOnly=MissingSafepointOnTryCatch::test* TestMissingSafepointOnTryCatch
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
        //MissingSafepointOnTryCatch.test1(); //  assert(false) failed: malformed control flow

        //MissingSafepointOnTryCatch.test2(); //  assert((Opcode() != Op_If && Opcode() != Op_RangeCheck) || outcnt() == 2) failed: bad if #1

        //MissingSafepointOnTryCatch.test3(); //  assert(false) failed: malformed control flow VS assert(_ltree_root->_child == nullptr || C->has_loops() || only_has_infinite_loops() || C->has_exception_backedge()) failed: parsing found no loops but there are some

        MissingSafepointOnTryCatch.test4(); //  assert(false) failed: malformed control flow

        //infiniteLoop();
    }



}

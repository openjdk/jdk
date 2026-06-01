/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=Xcomp-noTieredCompilation
 * @bug 8368175
 * @summary Exercise single frame thaw in fast path
 * @requires vm.debug == true & vm.continuations
 * @modules java.base/jdk.internal.vm
 * @library /test/lib /test/hotspot/jtreg
 * @run main/othervm -XX:+ForceSingleFrameThaw -Xcomp -XX:-TieredCompilation SingleFrameThaw 1000
 */

/*
 * @test id=Xcomp-TieredStopAtLevel3
 * @bug 8368175
 * @summary Exercise single frame thaw in fast path
 * @requires vm.debug == true & vm.continuations
 * @modules java.base/jdk.internal.vm
 * @library /test/lib /test/hotspot/jtreg
 * @run main/othervm -XX:+ForceSingleFrameThaw -Xcomp -XX:TieredStopAtLevel=3 SingleFrameThaw 1000
 */

import jdk.internal.vm.Continuation;
import jdk.internal.vm.ContinuationScope;

import jdk.test.lib.Asserts;

public class SingleFrameThaw {
    static final ContinuationScope FOO = new ContinuationScope() {};
    static int counter;

    public static void main(String[] args) {
        int iterations = args.length > 0 ? Integer.parseInt(args[0]) : 1000;

        Continuation cont = new Continuation(FOO, () -> {
            for (int i = 0; i < iterations; i++) {
              counter++;
              Continuation.yield(FOO);
            }
        });

        for (int i = 0; i < iterations; i++) {
            Asserts.assertTrue(!cont.isDone(), "continuation done");
            cont.run();
        }

        Asserts.assertTrue(!cont.isDone(), "continuation done");
        cont.run();
        Asserts.assertTrue(cont.isDone(), "continuation not done");
        Asserts.assertTrue(counter == iterations, "wrong count");
    }
}

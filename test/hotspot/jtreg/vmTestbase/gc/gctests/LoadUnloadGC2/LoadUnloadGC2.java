/*
 * Copyright (c) 2007, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @key stress randomness
 *
 * @summary converted from VM Testbase gc/gctests/LoadUnloadGC2.
 * VM Testbase keywords: [gc, stress, stressopt, nonconcurrent, quick]
 *
 * @library /vmTestbase
 *          /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI gc.gctests.LoadUnloadGC2.LoadUnloadGC2
 */

package gc.gctests.LoadUnloadGC2;

import jdk.test.whitebox.WhiteBox;
import nsk.share.*;
import nsk.share.test.*;
import nsk.share.gc.*;
import nsk.share.gc.gp.*;
import nsk.share.gc.gp.classload.*;
import java.lang.reflect.Array;

public class LoadUnloadGC2 extends GCTestBase {
        private static int CYCLE = 1000;
        public void run() {
                Stresser stresser = new Stresser(runParams.getStressOptions());
                stresser.start(500000);
                int iteration = 0;
                try {
                        GarbageProducer garbageProducer = new GeneratedClassProducer();
                        while (stresser.iteration()) {
                                garbageProducer.create(512L);
                                if(iteration++ > CYCLE) {
                                    // Unload once every cycle.
                                    iteration = 0;
                                    garbageProducer = null;
                                    // Perform GC so that
                                    // class gets unloaded
                                    WhiteBox.getWhiteBox().fullGC();
                                    garbageProducer = new GeneratedClassProducer();
                               }
                        }
                } finally {
                        stresser.finish();
                }
        }

        public static void main(String[] args) {
                Tests.runTest(new LoadUnloadGC2(), args);
        }
}

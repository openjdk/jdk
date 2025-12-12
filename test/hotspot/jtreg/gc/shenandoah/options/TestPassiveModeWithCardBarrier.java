/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 *
 */

/*
 * @test
 * @summary Test passive mode with card barrier with a gc heavy app. A simple hello world in TestSelectiveBarrierFlags
 * does not always surface crashes
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 *
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UnlockDiagnosticVMOptions -XX:+UseShenandoahGC -Xmx128m -XX:ShenandoahGCMode=passive -XX:+ShenandoahCardBarrier TestPassiveModeWithCardBarrier
 */

import java.util.*;
import java.util.concurrent.*;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class TestPassiveModeWithCardBarrier {
    public static void main(String[] args) throws Exception {
        List<byte[]> junk = new ArrayList<>();
        int junkLength = 1000;
        int totalRounds = 10;
        int round = 0;

        while (round++ < totalRounds) {
            for (int i = 0; i < junkLength; i++) {
                junk.add(new byte[1024]);
            }

            System.out.println(junk.hashCode());
        }

        // trigger a full gc in case it was all degen
        System.gc();
    }
}

/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.event.compiler;

import java.util.List;
import java.util.Comparator;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.Asserts;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;
import jdk.test.whitebox.WhiteBox;
import jdk.test.whitebox.code.BlobType;

/**
 * @test TestJitRestart
 * @requires vm.hasJFR
 *
 * @library /test/lib
 * @modules jdk.jfr
 *          jdk.management.jfr
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xbootclasspath/a:.
 *     -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *     -XX:+SegmentedCodeCache -XX:-UseLargePages jdk.jfr.event.compiler.TestJitRestart
 */
public class TestJitRestart {

    public static void main(String[] args) throws Exception {
        boolean checkJitRestartCompilation = false;
        for (BlobType btype : BlobType.getAvailable()) {
            boolean jr = testWithBlobType(btype, calculateAvailableSize(btype));
            if (jr) {
                System.out.println("JIT restart event / Compilation event check for BlobType " + btype + " was successful");
                checkJitRestartCompilation = true;
            }
        }
        Asserts.assertTrue(checkJitRestartCompilation, "No JIT restart event found and unexpected compilation seen");
    }

    private static final WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    private static boolean testWithBlobType(BlobType btype, long availableSize) throws Exception {
        Recording r = new Recording();
        r.enable(EventNames.CodeCacheFull);
        r.enable(EventNames.Compilation);
        r.enable(EventNames.JITRestart);
        r.start();
        long addr = WHITE_BOX.allocateCodeBlob(availableSize, btype.id);
        WHITE_BOX.freeCodeBlob(addr);
        WHITE_BOX.forceNMethodSweep();
        r.stop();

        List<RecordedEvent> events = Events.fromRecording(r);
        System.out.println("---------------------------------------------");
        System.out.println("# events:" + events.size());
        Events.hasEvents(events);
        events.sort(Comparator.comparing(RecordedEvent::getStartTime));

        boolean compilationCanHappen = true;
        for (RecordedEvent evt: events) {
            System.out.println(evt);
            if (evt.getEventType().getName().equals("jdk.CodeCacheFull")) {
                System.out.println("--> jdk.CodeCacheFull found");
                compilationCanHappen = false;
            }
            if (evt.getEventType().getName().equals("jdk.Compilation") && !compilationCanHappen) {
                return false;
            }
            if (evt.getEventType().getName().equals("jdk.JITRestart")) {
                System.out.println("--> jdk.JitRestart found");
                Events.assertField(evt, "codeCacheMaxCapacity").notEqual(0L);
                Events.assertField(evt, "freedMemory").notEqual(0L);
                System.out.println("JIT restart event found for BlobType " + btype);
                return true;
            }
        }
        System.out.println("---------------------------------------------");

        // in some seldom cases we do not see the JitRestart event; but then
        // do not fail (as long as no compilation happened before)
        return true;
    }

    // Compute the available size for this BlobType by taking into account
    // that it may be stored in a different code heap in case it does not fit
    // into the current one.
    private static long calculateAvailableSize(BlobType btype) {
        long availableSize = btype.getSize();
        for (BlobType alternative : BlobType.getAvailable()) {
            if (btype.allowTypeWhenOverflow(alternative)) {
                availableSize = Math.max(availableSize, alternative.getSize());
            }
        }
        return availableSize;
    }
}

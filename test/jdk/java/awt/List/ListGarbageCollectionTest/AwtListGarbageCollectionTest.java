/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Frame;
import java.awt.List;
import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;

import jdk.test.lib.util.ForceGC;

/*
 * @test
 * @key headful
 * @bug 8040076
 * @summary AwtList not garbage collected
 * @library /test/lib/
 * @build jdk.test.lib.util.ForceGC
 * @run main/othervm -Xmx100m -Xlog:gc=debug AwtListGarbageCollectionTest
 */
public class AwtListGarbageCollectionTest {

    private static final long ENQUEUE_TIMEOUT = 50;

    public static void main(String[] args) throws InterruptedException {
        Frame frame = new Frame("List leak test");
        try {
            test(frame);
        } finally {
            frame.dispose();
        }
    }

    private static void test(Frame frame) {
        frame.setSize(300, 200);
        frame.setVisible(true);

        List strongListRef = new List();
        frame.add(strongListRef);
        strongListRef.setMultipleMode(true);
        frame.remove(strongListRef);

        final ReferenceQueue<List> referenceQueue = new ReferenceQueue<>();
        final PhantomReference<List> phantomListRef =
                new PhantomReference<>(strongListRef, referenceQueue);
        System.out.println("phantomListRef: " + phantomListRef);

        strongListRef = null; // Clear the strong reference

        System.out.println("Waiting for the reference to be cleared");
        if (!ForceGC.wait(() -> phantomListRef == remove(referenceQueue))) {
            throw new RuntimeException("List wasn't garbage collected");
        }
    }

    private static Reference<?> remove(ReferenceQueue<?> queue) {
        try {
            return queue.remove(ENQUEUE_TIMEOUT);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}

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

package jdk.jfr.event.oldobject;

import jdk.internal.misc.Unsafe;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.internal.vm.annotation.NullRestricted;
import jdk.jfr.consumer.RecordedObject;
import jdk.jfr.internal.test.WhiteBox;
import jdk.test.lib.Asserts;

/**
 * @test
 * @bug 8389454
 * @summary Verify physical skip distances in old-object paths through flattened fields
 * @requires vm.hasJFR & vm.flagless & vm.bits == "64"
 * @library /test/lib /test/jdk
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.misc
 *          java.base/jdk.internal.vm.annotation
 *          jdk.jfr/jdk.jfr.internal.test
 * @enablePreview
 * @build jdk.jfr.event.oldobject.TestFlatArrayInformation
 * @run main/othervm -Xmx128m -XX:TLABSize=2k -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseFieldFlattening -XX:+UseNullFreeNonAtomicValueFlattening -XX:+UseCompressedOops jdk.jfr.event.oldobject.TestFlatReferenceChainLimit
 * @run main/othervm -Xmx128m -XX:TLABSize=2k -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+UseFieldFlattening -XX:+UseNullFreeNonAtomicValueFlattening -XX:-UseCompressedOops jdk.jfr.event.oldobject.TestFlatReferenceChainLimit
 */
public class TestFlatReferenceChainLimit {
    private static final int CHAIN_LENGTH = 2 * OldObjects.MAX_CHAIN_LENGTH;

    @LooselyConsistentValue
    static value class Link {
        final long padding;
        final Object ref;

        Link(Object ref) {
            padding = 42;
            this.ref = ref;
        }
    }

    static class Node {
        @NullRestricted final Link link;

        Node(Object next) {
            link = new Link(next);
            super();
        }
    }

    // Marks the end of our chain without depending on the path from KEEP
    // through its backing array and the test's class mirror to a GC root.
    static class Root {
        final Object head;

        Root(Object head) {
            this.head = head;
        }
    }

    public static void main(String[] args) throws Exception {
        WhiteBox.setWriteAllObjectSamples(true);
        new Node(null);
        Asserts.assertTrue(Unsafe.getUnsafe().isFlatField(Node.class.getDeclaredField("link")));
        TestFlatArrayInformation.check("flat chain with skipped physical edges",
            TestFlatReferenceChainLimit::allocate, Node.class, TestFlatReferenceChainLimit::verify);
    }

    private static void allocate() {
        Object next = new byte[OldObjects.MIN_SIZE];
        for (int i = 0; i < CHAIN_LENGTH; i++) {
            next = new Node(next);
        }
        TestFlatArrayInformation.KEEP.add(new Root(next));
    }

    private static void verify(RecordedObject ref) {
        int physicalNodes = 0;
        int logicalNodes = 0;
        int skippedNodes = 0;
        int skipEdges = 0;
        RecordedObject object;
        // A recorded path can start with a skip, without a logical holder
        // for the sampled object's first reference.
        int firstSkip = ref.getInt("skip");
        Asserts.assertTrue(firstSkip >= 0);
        if (firstSkip != 0) {
            Asserts.assertNull(ref.getValue("array"));
            Asserts.assertNull(ref.getValue("field"));
            object = ref.getValue("object");
            Asserts.assertNotNull(object);
            Asserts.assertTrue(isType(object, Node.class));
            skippedNodes = firstSkip;
            skipEdges = 1;
        } else {
            object = TestFlatArrayInformation.verifyField(ref, "ref", Link.class, true);
        }
        for (int steps = 0; !isType(object, Root.class); steps++) {
            Asserts.assertTrue(steps < 2 * CHAIN_LENGTH, "Reference chain did not reach its root marker");
            boolean logical = isType(object, Link.class);
            if (logical) {
                logicalNodes++;
                Asserts.assertEquals(object.getLong("address"), 0L);
                Asserts.assertNull(object.getString("description"));
            } else {
                Asserts.assertTrue(isType(object, Node.class));
                Asserts.assertTrue(object.getLong("address") != 0);
                physicalNodes++;
            }

            RecordedObject next = object.getValue("referrer");
            Asserts.assertNotNull(next);
            Asserts.assertNull(next.getValue("array"));
            RecordedObject parent = next.getValue("object");
            Asserts.assertNotNull(parent);
            int skip = next.getInt("skip");
            Asserts.assertTrue(skip >= 0);
            if (skip != 0) {
                // A skip connects physical nodes directly, with no field
                // metadata or logical holder inserted at that boundary.
                Asserts.assertFalse(logical);
                Asserts.assertTrue(isType(parent, Node.class));
                Asserts.assertTrue(parent.getLong("address") != 0);
                Asserts.assertNull(next.getValue("field"));
                skippedNodes += skip;
                skipEdges++;
            } else if (logical) {
                TestFlatArrayInformation.verifyField(next, "link", Node.class, false);
            } else if (isType(parent, Root.class)) {
                TestFlatArrayInformation.verifyField(next, "head", Root.class, false);
            } else {
                TestFlatArrayInformation.verifyField(next, "ref", Link.class, true);
            }
            object = parent;
        }

        Asserts.assertTrue(skipEdges > 0, "Expected a truncated physical chain");
        Asserts.assertEquals(physicalNodes + skippedNodes, CHAIN_LENGTH,
                             "Skip counts must account for exactly the omitted physical nodes");
        Asserts.assertEquals(logicalNodes, physicalNodes - skipEdges,
                             "Only unskipped physical edges introduce logical holders");
    }

    private static boolean isType(RecordedObject object, Class<?> type) {
        return object.getClass("type").getName().equals(type.getName());
    }
}

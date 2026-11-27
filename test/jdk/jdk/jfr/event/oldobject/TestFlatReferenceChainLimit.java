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
 *          jdk.management
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

    // Marks the end of our chain without depending on the path from RETAINED_OBJECTS
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
        TestFlatArrayInformation.recordAndVerifyReferencePath("flat chain with skipped physical edges",
            TestFlatReferenceChainLimit::allocateChain, Node.class, TestFlatReferenceChainLimit::verifyTruncatedReferenceChain);
    }

    private static void allocateChain() {
        Object next = new byte[OldObjects.MIN_SIZE];
        for (int nodeIndex = 0; nodeIndex < CHAIN_LENGTH; nodeIndex++) {
            next = new Node(next);
        }
        TestFlatArrayInformation.RETAINED_OBJECTS.add(new Root(next));
    }

    private static void verifyTruncatedReferenceChain(RecordedObject ref) {
        int recordedNodeCount = 0;
        int recordedFlattenedLinkCount = 0;
        int omittedNodeCount = 0;
        int skipReferenceCount = 0;
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
            omittedNodeCount = firstSkip;
            skipReferenceCount = 1;
        } else {
            object = TestFlatArrayInformation.verifyFieldInFlattenedValue(ref, "ref", Link.class);
        }
        for (int steps = 0; !isType(object, Root.class); steps++) {
            Asserts.assertTrue(steps < 2 * CHAIN_LENGTH, "Reference chain did not reach its root marker");
            boolean isFlattenedLink = isType(object, Link.class);
            if (isFlattenedLink) {
                recordedFlattenedLinkCount++;
                TestFlatArrayInformation.verifySyntheticAddress(object);
                Asserts.assertNull(object.getString("description"));
            } else {
                Asserts.assertTrue(isType(object, Node.class));
                Asserts.assertTrue(object.getLong("address") != 0);
                recordedNodeCount++;
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
                Asserts.assertFalse(isFlattenedLink);
                Asserts.assertTrue(isType(parent, Node.class));
                Asserts.assertTrue(parent.getLong("address") != 0);
                Asserts.assertNull(next.getValue("field"));
                omittedNodeCount += skip;
                skipReferenceCount++;
            } else if (isFlattenedLink) {
                TestFlatArrayInformation.verifyFieldInObject(next, "link", Node.class);
            } else if (isType(parent, Root.class)) {
                TestFlatArrayInformation.verifyFieldInObject(next, "head", Root.class);
            } else {
                TestFlatArrayInformation.verifyFieldInFlattenedValue(next, "ref", Link.class);
            }
            object = parent;
        }

        Asserts.assertTrue(skipReferenceCount > 0, "Expected at least one skip reference in the recorded Node chain");
        Asserts.assertEquals(recordedNodeCount + omittedNodeCount, CHAIN_LENGTH,
                             "Recorded and omitted Node objects must account for the entire allocated chain");
        Asserts.assertEquals(recordedFlattenedLinkCount, recordedNodeCount - skipReferenceCount,
                             "Each recorded Node has a flattened Link except at a skip reference");
    }

    private static boolean isType(RecordedObject object, Class<?> type) {
        return object.getClass("type").getName().equals(type.getName());
    }
}

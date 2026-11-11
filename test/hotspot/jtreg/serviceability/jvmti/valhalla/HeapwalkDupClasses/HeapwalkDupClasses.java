/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8368799
 * @summary Verify heapwalking API does not report array classes several times.
 * @requires vm.jvmti
 * @modules java.base/jdk.internal.vm.annotation java.base/jdk.internal.value
 * @enablePreview
 * @run main/othervm/native -agentlib:HeapwalkDupClasses HeapwalkDupClasses
 */

import java.lang.ref.Reference;
import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.NullRestricted;

public class HeapwalkDupClasses {

    static native int tagWithFollowReferences(long tag);
    static native int tagWithIterateOverReachableObjects(long tag);
    static native Object[] getObjectsWithTags(long tag);

    public static void main(String[] args) throws Exception {
        System.loadLibrary("HeapwalkDupClasses");

        Integer instance = new Integer(0);
        Object[] testObjects = new Object[] {
            new Integer[5],
            ValueClass.newNullableAtomicArray(Integer.class, 5),
            ValueClass.newNullRestrictedNonAtomicArray(Integer.class, 5, instance)
        };

        for (long tag = 1; tag <= 2; tag++) {
            int taggedClasses;
            if (tag == 1) {
                System.out.println("FollowReferences");
                taggedClasses = tagWithFollowReferences(tag);
            } else {
                System.out.println("IterateOverReachableObjects");
                taggedClasses = tagWithIterateOverReachableObjects(tag);
            }
            System.out.println("Tagged " + taggedClasses + " classes");

            Object[] taggedObjects = getObjectsWithTags(tag);
            System.out.println("Tagged objects (total " + taggedObjects.length + "):");

            int duplicates = 0;
            boolean foundTestObjectClass[] = new boolean[testObjects.length];

            for (int i = 0; i < taggedObjects.length; i++) {
                System.out.println("[" + i + "] " + taggedObjects[i]);
                for (int j = 0; j < i; j++) {
                    if (taggedObjects[i].equals(taggedObjects[j])) {
                        duplicates++;
                        System.out.println("  ERROR: duplicate (" + j + ")");
                    }
                }
                for (int j = 0; j < testObjects.length; j++) {
                    if (taggedObjects[i].equals(testObjects[j].getClass())) {
                        foundTestObjectClass[j] = true;
                        System.out.println("  FOUND expected array class");
                    }
                }
            }
            if (duplicates != 0) {
            throw new RuntimeException("Found " + duplicates + " duplicate classes");
            }
            for (int i = 0; i < foundTestObjectClass.length; i++) {
                if (!foundTestObjectClass[i]) {
                    throw new RuntimeException("Expected class not found: " + testObjects[i].getClass());
                }
            }
        }
    }
}

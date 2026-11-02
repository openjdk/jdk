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
 * @test
 * @bug 8396399
 * @summary Test verifies how VMObjectAlloc works for clone() method.
 *
 * @requires vm.jvmti
 *
 * @run main/othervm/native -agentlib:VMObjectAllocClone VMObjectAllocCloneTest
 */

import java.lang.ref.Reference;
public class VMObjectAllocCloneTest {
    private static final String AGENT_LIB = "VMObjectAllocClone";

    private static native void init(Class<?> payloadClass);
    private static native int eventsCount();

    static {
        System.loadLibrary(AGENT_LIB);
    }
    public static void main(String[] args) {
        Payload object = createPayload();
        init(Payload.class);
        Payload cloneObject = cloneObject(object);
        if (eventsCount() != 1) {
            throw new RuntimeException("No expected VMObjectAlloc event for object clone().");
        }
        Reference.reachabilityFence(cloneObject);

        int[] array = new int[10];
        init(int[].class);
        int[] cloneArray = cloneArray(array);
        if (eventsCount() != 1) {
            throw new RuntimeException("No expected VMObjectAlloc event for array clone().");
        }

        Reference.reachabilityFence(cloneArray);

    }

    private static Payload createPayload() {
        return new Payload(0x12345);
    }

    // Use separate method because main() might be deoptimized.
    private static Payload cloneObject(Payload object) {
        return object.clone();
    }

    private static int[] cloneArray(int[] array) {
        return array.clone();
    }

    record Payload(int value) implements Cloneable {

        @Override
        public Payload clone() {
            try {
                return (Payload) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException("Unexpected CloneNotSupportedException", e);
            }
        }
    }

}

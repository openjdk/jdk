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
 * @test id=Z
 * @bug 8383787
 * @summary Test verifies that SampledObjectAlloc event is called with after clone().
 * @requires vm.jvmti
 * @run main/othervm/native -agentlib:SampledObjecAfterCloning SampledObjecAfterCloningTest
 */


public class SampledObjecAfterCloningTest {
    private static final String AGENT_LIB = "SampledObjecAfterCloning";

    private static native void init(Class<?> payloadClass);
    private static native int sampledObjectField(Object object);

    static {
        System.loadLibrary(AGENT_LIB);
    }
    public static void main(String[] args) {
        Payload object1 = createPayload();
        init(Payload.class);
        Payload cloneObject = object1.clone();
        if (sampledObjectField(cloneObject) != cloneObject.value) {
            throw new RuntimeException("Sampled object field for clone was not initialized in the sampled object.");
        }

    }

    private static Payload createPayload() {
        return new Payload(0x12345);
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

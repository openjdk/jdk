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
 * @bug 8383421
 * @summary Exercise ZGC clone barriers on tenured new allocation.
 * @requires vm.jvmti & vm.gc.Z
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/native -Xbootclasspath/a:.
 *                          -XX:+UnlockDiagnosticVMOptions
 *                          -XX:+WhiteBoxAPI
 *                          -XX:+UseZGC
 *                          -Xms128M
 *                          -Xmx128M
 *                          -Xint
 *                          -agentlib:ZCloneWithTenuredAllocation
 *                          ZCloneWithTenuredAllocation
 */

/*
 * @test id=ZVerify
 * @bug 8383421
 * @summary Exercise ZGC clone barriers on tenured new allocation.
 * @requires vm.jvmti & vm.gc.Z & vm.debug
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/native -Xbootclasspath/a:.
 *                          -XX:+UnlockDiagnosticVMOptions
 *                          -XX:+WhiteBoxAPI
 *                          -XX:+UseZGC
 *                          -XX:+ZVerifyRoots
 *                          -XX:+ZVerifyObjects
 *                          -XX:+ZVerifyMarking
 *                          -XX:+ZVerifyForwarding
 *                          -XX:+ZVerifyRemembered
 *                          -XX:+ZVerifyOops
 *                          -Xms128M
 *                          -Xmx128M
 *                          -Xint
 *                          -agentlib:ZCloneWithTenuredAllocation
 *                          ZCloneWithTenuredAllocation
 */

import java.util.Arrays;

import jdk.test.whitebox.WhiteBox;

public class ZCloneWithTenuredAllocation {
    private static final String AGENT_LIB = "ZCloneWithTenuredAllocation";

    private static final WhiteBox WB = WhiteBox.getWhiteBox();

    private static native void init(Class<?> payloadClass, Class<?> markerPairArrayClass);
    private static native boolean isSampledObject(Object object);

    static {
        System.loadLibrary(AGENT_LIB);
    }

    public static void main(String[] args) {
        init(Payload.class, MarkerPair[].class);

        testObjectClone();
        testObjectArrayClone();
    }

    private static void testObjectClone() {
        final var clone = createPayload().clone();

        if (!isSampledObject(clone)) {
            throw new RuntimeException("Payload clone was not the sampled object");
        }

        // Run young collection to catch missing rememberset
        WB.youngGC();

        final var expected = createPayload();
        if (!expected.equals(clone)) {
            throw new RuntimeException("Unexpected Payload clone: " + clone);
        }
    }

    private static void testObjectArrayClone() {
        final var clone = createMarkerPairArray().clone();

        if (!isSampledObject(clone)) {
            throw new RuntimeException("MarkerPair[] clone was not the sampled object");
        }

        // Run young collection to catch missing rememberset
        WB.youngGC();

        final var expected = createMarkerPairArray();
        if (!Arrays.equals(expected, clone)) {
            throw new RuntimeException("Unexpected MarkerPair[] clone: " + Arrays.toString(clone));
        }
    }

    private static Payload createPayload() {
        return new Payload(true,
                           (byte) 12,
                           '\u2345',
                           (short) 1234,
                           0x12345678,
                           0x1122334455667788L,
                           12.25F,
                           23.5D,
                           Boolean.TRUE,
                           Byte.valueOf((byte) 34),
                           Character.valueOf('\u2345'),
                           Short.valueOf((short) 1234),
                           Integer.valueOf(123456),
                           Long.valueOf(0x1122334455667788L),
                           Float.valueOf(12.25F),
                           Double.valueOf(23.5D),
                           new Marker(1),
                           new MarkerPair(new Marker(2), new Marker(3)));
    }

    private static MarkerPair[] createMarkerPairArray() {
        return new MarkerPair[] {
            new MarkerPair(new Marker(101), new Marker(102)),
            null,
            new MarkerPair(new Marker(103), new Marker(104)),
            new MarkerPair(new Marker(105), new Marker(106))
        };
    }

    private static void tenure(Object sampledObject) {
        while (!WB.isObjectInOldGen(sampledObject)) {
            WB.fullGC();
        }
    }

    private record Payload(boolean booleanValue,
                           byte byteValue,
                           char charValue,
                           short shortValue,
                           int intValue,
                           long longValue,
                           float floatValue,
                           double doubleValue,
                           Boolean boxedBoolean,
                           Byte boxedByte,
                           Character boxedCharacter,
                           Short boxedShort,
                           Integer boxedInteger,
                           Long boxedLong,
                           Float boxedFloat,
                           Double boxedDouble,
                           Marker marker,
                           MarkerPair markerPair) implements Cloneable {
        @Override
        public Payload clone() {
            try {
                return (Payload) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException("Unexpected CloneNotSupportedException", e);
            }
        }
    }

    private record Marker(int id) {}

    private record MarkerPair(Marker first, Marker second) {}
}

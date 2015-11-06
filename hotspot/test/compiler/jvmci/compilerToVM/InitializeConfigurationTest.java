/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8136421
 * @requires (os.simpleArch == "x64" | os.simpleArch == "sparcv9") & os.arch != "aarch64"
 * @library / /testlibrary
 * @compile ../common/CompilerToVMHelper.java
 * @build compiler.jvmci.compilerToVM.InitializeConfigurationTest
 * @run main ClassFileInstaller
 *      jdk.vm.ci.hotspot.CompilerToVMHelper
 * @run main/othervm -Xbootclasspath/a:.
 *     -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
 *     compiler.jvmci.compilerToVM.InitializeConfigurationTest
 */

package compiler.jvmci.compilerToVM;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Consumer;
import jdk.vm.ci.hotspot.CompilerToVMHelper;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import sun.misc.Unsafe;

public class InitializeConfigurationTest {
    private static final Unsafe UNSAFE = Utils.getUnsafe();

    public static void main(String args[]) {
        new InitializeConfigurationTest().runTest(generateTestCases());
    }

    private static List<TestCase> generateTestCases() {
        List<TestCase> result = new ArrayList<>();
        result.add(new TestCase("CodeCache", "_high_bound", "address",
                InitializeConfigurationTest::verifyLongIsNotZero));
        result.add(new TestCase("StubRoutines", "_jint_arraycopy", "address",
                InitializeConfigurationTest::verifyLongIsNotZero));
        return result;
    }

    private static void verifyLongIsNotZero(Object o) {
        Asserts.assertNotNull(o, "Got null value");
        Asserts.assertEQ(o.getClass(), Long.class, "Unexpected value type");
        Asserts.assertNE(o, 0L, "Got null address");
    }

    private void runTest(List<TestCase> tcases) {
        VMStructDataReader reader = new VMStructDataReader(
                CompilerToVMHelper.initializeConfiguration(HotSpotJVMCIRuntime.runtime().getConfig()));
        while (reader.hasNext()) {
            VMFieldData data = reader.next();
            for (TestCase tcase : tcases) {
                tcase.check(data);
            }
        }
        // now check if all passed
        for (TestCase tcase: tcases) {
            Asserts.assertTrue(tcase.isFound(), "Case failed: " + tcase);
        }
    }

    private static class VMStructDataReader implements Iterator<VMFieldData> {
        // see jvmciCompilerToVM:105 static uintptr_t ciHotSpotVMData[28];
        private static final int HOTSPOT_VM_DATA_INDEX_COUNT = 28;
        private final long addresses[];
        private final long vmStructsBase;
        private final long entityNameFieldOffset;
        private final long nameFieldOffset;
        private final long typeStringFieldOffset;
        private final long addressOffset;
        private final long entrySize;
        private long nextElementAddress;
        private VMFieldData nextElement;

        public VMStructDataReader(long gHotSpotVMData) {
            Asserts.assertNE(gHotSpotVMData, 0L, "Got null base address");
            addresses = new long[HOTSPOT_VM_DATA_INDEX_COUNT];
            for (int i = 0; i < HOTSPOT_VM_DATA_INDEX_COUNT; i++) {
                addresses[i] = UNSAFE.getAddress(
                        gHotSpotVMData + Unsafe.ADDRESS_SIZE * i);
            }
            vmStructsBase = addresses[0];
            entityNameFieldOffset = addresses[1];
            nameFieldOffset = addresses[2];
            typeStringFieldOffset = addresses[3];
            addressOffset = addresses[6];
            entrySize = addresses[7];
            nextElementAddress = vmStructsBase;
            nextElement = read();
        }

        @Override
        public boolean hasNext() {
            return nextElement != null;
        }

        @Override
        public VMFieldData next() {
            if (nextElement == null) {
                throw new NoSuchElementException("Next element is null");
            }
            VMFieldData toReturn = nextElement;
            nextElementAddress += entrySize;
            nextElement = read();
            return toReturn;
        }

        private VMFieldData read() {
            String entityFieldName = readCString(
                    UNSAFE.getAddress(nextElementAddress + nameFieldOffset));
            if (entityFieldName == null) {
                return null;
            }
            String fieldType = readCString(UNSAFE.getAddress(
                    nextElementAddress + typeStringFieldOffset));
            String entityName = readCString(UNSAFE.getAddress(
                    nextElementAddress + entityNameFieldOffset));
            Object value;
            if ("address".equals(fieldType)) {
                long address = UNSAFE.getAddress(
                        nextElementAddress + addressOffset);
                value = address;
            } else {
                // non-address cases are not supported
                value = null;
            }
            return new VMFieldData(entityName, entityFieldName, fieldType,
                    value);
        }

        private static String readCString(long address) {
            if (address == 0) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0;; i++) {
                char c = (char) UNSAFE.getByte(address + i);
                if (c == 0) {
                    break;
                }
                sb.append(c);
            }
            return sb.toString();
        }
    }

    private static class VMFieldData {
        public final String entityFieldName;
        public final String entityName;
        public final String fieldType;
        public final Object value;

        private VMFieldData(String entityName, String entityFieldName,
                String fieldType, Object value) {
            this.entityName = entityName;
            this.entityFieldName = entityFieldName;
            this.fieldType = fieldType;
            this.value = value;
        }
    }

    private static class TestCase {
        public final String entityName;
        public final String fieldType;
        public final String entityFieldName;
        public final Consumer consumer;
        private boolean found;

        public TestCase(String entityName, String entityFieldName,
                String fieldType, Consumer predicate) {
            Objects.requireNonNull(entityName, "Got null entityName");
            Objects.requireNonNull(entityFieldName, "Got null entityFieldName");
            Objects.requireNonNull(fieldType, "Got null type");
            if (!"address".equals(fieldType)) {
                throw new Error("TESTBUG: unsupported testcase with fieldType="
                        + fieldType);
            }
            this.entityName = entityName;
            this.fieldType = fieldType;
            this.entityFieldName = entityFieldName;
            this.consumer = predicate;
            this.found = false;
        }

        public void check(VMFieldData data) {
            if (entityFieldName.equals(data.entityFieldName)
                    && entityName.equals(data.entityName)
                    && fieldType.equals(data.fieldType)) {
                Asserts.assertFalse(found, "Found 2 entries of " + this);
                found = true;
                consumer.accept(data.value);
            }
        }

        @Override
        public String toString() {
            return "CASE: entityName=" + entityName + " entityFieldName="
                    + entityFieldName + " fieldType=" + fieldType;
        }

        public boolean isFound() {
            return found;
        }
    }
}

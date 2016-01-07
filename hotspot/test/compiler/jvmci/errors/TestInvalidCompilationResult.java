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

/**
 * @test
 * @requires (os.simpleArch == "x64" | os.simpleArch == "sparcv9" | os.simpleArch == "aarch64")
 * @compile CodeInstallerTest.java
 * @run junit/othervm -da:jdk.vm.ci... -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI compiler.jvmci.errors.TestInvalidCompilationResult
 */

package compiler.jvmci.errors;

import static jdk.vm.ci.code.CompilationResult.ConstantReference;
import static jdk.vm.ci.code.CompilationResult.DataPatch;
import static jdk.vm.ci.code.CompilationResult.DataSectionReference;
import static jdk.vm.ci.code.CompilationResult.Infopoint;
import static jdk.vm.ci.code.CompilationResult.Reference;
import static jdk.vm.ci.code.DataSection.Data;
import static jdk.vm.ci.code.DataSection.DataBuilder;
import static jdk.vm.ci.meta.Assumptions.Assumption;

import jdk.vm.ci.code.CompilationResult;
import jdk.vm.ci.code.InfopointReason;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotConstant;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.VMConstant;

import org.junit.Test;

/**
 * Tests for errors in the code installer.
 */
public class TestInvalidCompilationResult extends CodeInstallerTest {

    private static class InvalidAssumption extends Assumption {
    }

    private static class InvalidVMConstant implements VMConstant {

        public boolean isDefaultForKind() {
            return false;
        }

        public String toValueString() {
            return null;
        }
    }

    private static class InvalidReference extends Reference {

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public boolean equals(Object obj) {
            return false;
        }
    }

    @Test(expected = JVMCIError.class)
    public void testInvalidAssumption() {
        CompilationResult result = createEmptyCompilationResult();
        result.setAssumptions(new Assumption[]{new InvalidAssumption()});
        installCode(result);
    }

    @Test(expected = JVMCIError.class)
    public void testInvalidAlignment() {
        CompilationResult result = createEmptyCompilationResult();
        result.getDataSection().insertData(new Data(7, 1, DataBuilder.zero(1)));
        installCode(result);
    }

    @Test(expected = NullPointerException.class)
    public void testNullDataPatchInDataSection() {
        CompilationResult result = createEmptyCompilationResult();
        Data data = new Data(1, 1, (buffer, patch) -> {
            patch.accept(null);
            buffer.put((byte) 0);
        });
        result.getDataSection().insertData(data);
        installCode(result);
    }

    @Test(expected = NullPointerException.class)
    public void testNullReferenceInDataSection() {
        CompilationResult result = createEmptyCompilationResult();
        Data data = new Data(1, 1, (buffer, patch) -> {
            patch.accept(new DataPatch(buffer.position(), null));
            buffer.put((byte) 0);
        });
        result.getDataSection().insertData(data);
        installCode(result);
    }

    @Test(expected = JVMCIError.class)
    public void testInvalidDataSectionReference() {
        CompilationResult result = createEmptyCompilationResult();
        DataSectionReference ref = result.getDataSection().insertData(new Data(1, 1, DataBuilder.zero(1)));
        Data data = new Data(1, 1, (buffer, patch) -> {
            patch.accept(new DataPatch(buffer.position(), ref));
            buffer.put((byte) 0);
        });
        result.getDataSection().insertData(data);
        installCode(result);
    }

    @Test(expected = JVMCIError.class)
    public void testInvalidNarrowMethodInDataSection() {
        CompilationResult result = createEmptyCompilationResult();
        HotSpotConstant c = (HotSpotConstant) dummyMethod.getEncoding();
        Data data = new Data(4, 4, (buffer, patch) -> {
            patch.accept(new DataPatch(buffer.position(), new ConstantReference((VMConstant) c.compress())));
            buffer.putInt(0);
        });
        result.getDataSection().insertData(data);
        installCode(result);
    }

    @Test(expected = NullPointerException.class)
    public void testNullConstantInDataSection() {
        CompilationResult result = createEmptyCompilationResult();
        Data data = new Data(1, 1, (buffer, patch) -> {
            patch.accept(new DataPatch(buffer.position(), new ConstantReference(null)));
        });
        result.getDataSection().insertData(data);
        installCode(result);
    }

    @Test(expected = JVMCIError.class)
    public void testInvalidConstantInDataSection() {
        CompilationResult result = createEmptyCompilationResult();
        Data data = new Data(1, 1, (buffer, patch) -> {
            patch.accept(new DataPatch(buffer.position(), new ConstantReference(new InvalidVMConstant())));
        });
        result.getDataSection().insertData(data);
        installCode(result);
    }

    @Test(expected = NullPointerException.class)
    public void testNullReferenceInCode() {
        CompilationResult result = createEmptyCompilationResult();
        result.recordDataPatch(0, null);
        installCode(result);
    }

    @Test(expected = NullPointerException.class)
    public void testNullConstantInCode() {
        CompilationResult result = createEmptyCompilationResult();
        result.recordDataPatch(0, new ConstantReference(null));
        installCode(result);
    }

    @Test(expected = JVMCIError.class)
    public void testInvalidConstantInCode() {
        CompilationResult result = createEmptyCompilationResult();
        result.recordDataPatch(0, new ConstantReference(new InvalidVMConstant()));
        installCode(result);
    }

    @Test(expected = JVMCIError.class)
    public void testInvalidReference() {
        CompilationResult result = createEmptyCompilationResult();
        result.recordDataPatch(0, new InvalidReference());
        installCode(result);
    }

    @Test(expected = JVMCIError.class)
    public void testOutOfBoundsDataSectionReference() {
        CompilationResult result = createEmptyCompilationResult();
        DataSectionReference ref = new DataSectionReference();
        ref.setOffset(0x1000);
        result.recordDataPatch(0, ref);
        installCode(result);
    }

    @Test(expected = JVMCIError.class)
    public void testInvalidMark() {
        CompilationResult result = createEmptyCompilationResult();
        result.recordMark(0, new Object());
        installCode(result);
    }

    @Test(expected = JVMCIError.class)
    public void testInvalidMarkInt() {
        CompilationResult result = createEmptyCompilationResult();
        result.recordMark(0, -1);
        installCode(result);
    }

    @Test(expected = NullPointerException.class)
    public void testNullInfopoint() {
        CompilationResult result = createEmptyCompilationResult();
        result.addInfopoint(null);
        installCode(result);
    }

    @Test(expected = JVMCIError.class)
    public void testInfopointMissingDebugInfo() {
        CompilationResult result = createEmptyCompilationResult();
        result.addInfopoint(new Infopoint(0, null, InfopointReason.METHOD_START));
        installCode(result);
    }

    @Test(expected = JVMCIError.class)
    public void testSafepointMissingDebugInfo() {
        CompilationResult result = createEmptyCompilationResult();
        result.addInfopoint(new Infopoint(0, null, InfopointReason.SAFEPOINT));
        installCode(result);
    }
}

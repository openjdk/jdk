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
 * @requires (os.simpleArch == "x64" | os.simpleArch == "sparcv9") & os.arch != "aarch64"
 * @compile CodeInstallationTest.java TestAssembler.java amd64/AMD64TestAssembler.java sparc/SPARCTestAssembler.java
 * @run junit/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI compiler.jvmci.code.DataPatchTest
 */

package compiler.jvmci.code;

import jdk.vm.ci.code.CompilationResult.DataSectionReference;
import jdk.vm.ci.code.DataSection.Data;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.hotspot.HotSpotConstant;
import jdk.vm.ci.hotspot.HotSpotVMConfig;
import jdk.vm.ci.meta.ResolvedJavaType;

import org.junit.Assume;
import org.junit.Test;

/**
 * Test code installation with data patches.
 */
public class DataPatchTest extends CodeInstallationTest {

    public static Class<?> getConstClass() {
        return DataPatchTest.class;
    }

    private void test(TestCompiler compiler) {
        test(compiler, getMethod("getConstClass"));
    }


    @Test
    public void testInlineObject() {
        test(asm -> {
            ResolvedJavaType type = metaAccess.lookupJavaType(getConstClass());
            HotSpotConstant c = (HotSpotConstant) type.getJavaClass();
            Register ret = asm.emitLoadPointer(c);
            asm.emitPointerRet(ret);
        });
    }

    @Test
    public void testInlineNarrowObject() {
        Assume.assumeTrue(HotSpotVMConfig.config().useCompressedOops);
        test(asm -> {
            ResolvedJavaType type = metaAccess.lookupJavaType(getConstClass());
            HotSpotConstant c = (HotSpotConstant) type.getJavaClass();
            Register compressed = asm.emitLoadPointer((HotSpotConstant) c.compress());
            Register ret = asm.emitUncompressPointer(compressed, HotSpotVMConfig.config().narrowOopBase, HotSpotVMConfig.config().narrowOopShift);
            asm.emitPointerRet(ret);
        });
    }

    @Test
    public void testDataSectionReference() {
        test(asm -> {
            ResolvedJavaType type = metaAccess.lookupJavaType(getConstClass());
            HotSpotConstant c = (HotSpotConstant) type.getJavaClass();
            Data data = codeCache.createDataItem(c);
            DataSectionReference ref = asm.result.getDataSection().insertData(data);
            Register ret = asm.emitLoadPointer(ref);
            asm.emitPointerRet(ret);
        });
    }

    @Test
    public void testNarrowDataSectionReference() {
        Assume.assumeTrue(HotSpotVMConfig.config().useCompressedOops);
        test(asm -> {
            ResolvedJavaType type = metaAccess.lookupJavaType(getConstClass());
            HotSpotConstant c = (HotSpotConstant) type.getJavaClass();
            HotSpotConstant cCompressed = (HotSpotConstant) c.compress();
            Data data = codeCache.createDataItem(cCompressed);
            DataSectionReference ref = asm.result.getDataSection().insertData(data);
            Register compressed = asm.emitLoadNarrowPointer(ref);
            Register ret = asm.emitUncompressPointer(compressed, HotSpotVMConfig.config().narrowOopBase, HotSpotVMConfig.config().narrowOopShift);
            asm.emitPointerRet(ret);
        });
    }

    @Test
    public void testInlineMetadata() {
        test(asm -> {
            ResolvedJavaType type = metaAccess.lookupJavaType(getConstClass());
            Register klass = asm.emitLoadPointer((HotSpotConstant) type.getObjectHub());
            Register ret = asm.emitLoadPointer(klass, HotSpotVMConfig.config().classMirrorOffset);
            asm.emitPointerRet(ret);
        });
    }

    @Test
    public void testInlineNarrowMetadata() {
        Assume.assumeTrue(HotSpotVMConfig.config().useCompressedClassPointers);
        test(asm -> {
            ResolvedJavaType type = metaAccess.lookupJavaType(getConstClass());
            HotSpotConstant hub = (HotSpotConstant) type.getObjectHub();
            Register narrowKlass = asm.emitLoadPointer((HotSpotConstant) hub.compress());
            Register klass = asm.emitUncompressPointer(narrowKlass, HotSpotVMConfig.config().narrowKlassBase, HotSpotVMConfig.config().narrowKlassShift);
            Register ret = asm.emitLoadPointer(klass, HotSpotVMConfig.config().classMirrorOffset);
            asm.emitPointerRet(ret);
        });
    }

    @Test
    public void testMetadataInDataSection() {
        test(asm -> {
            ResolvedJavaType type = metaAccess.lookupJavaType(getConstClass());
            HotSpotConstant hub = (HotSpotConstant) type.getObjectHub();
            Data data = codeCache.createDataItem(hub);
            DataSectionReference ref = asm.result.getDataSection().insertData(data);
            Register klass = asm.emitLoadPointer(ref);
            Register ret = asm.emitLoadPointer(klass, HotSpotVMConfig.config().classMirrorOffset);
            asm.emitPointerRet(ret);
        });
    }

    @Test
    public void testNarrowMetadataInDataSection() {
        Assume.assumeTrue(HotSpotVMConfig.config().useCompressedClassPointers);
        test(asm -> {
            ResolvedJavaType type = metaAccess.lookupJavaType(getConstClass());
            HotSpotConstant hub = (HotSpotConstant) type.getObjectHub();
            HotSpotConstant narrowHub = (HotSpotConstant) hub.compress();
            Data data = codeCache.createDataItem(narrowHub);
            DataSectionReference ref = asm.result.getDataSection().insertData(data);
            Register narrowKlass = asm.emitLoadNarrowPointer(ref);
            Register klass = asm.emitUncompressPointer(narrowKlass, HotSpotVMConfig.config().narrowKlassBase, HotSpotVMConfig.config().narrowKlassShift);
            Register ret = asm.emitLoadPointer(klass, HotSpotVMConfig.config().classMirrorOffset);
            asm.emitPointerRet(ret);
        });
    }
}

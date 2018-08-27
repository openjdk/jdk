/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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



package jdk.tools.jaotc;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.HashMap;
import java.util.HashSet;

import jdk.tools.jaotc.binformat.BinaryContainer;
import jdk.tools.jaotc.binformat.ReadOnlyDataContainer;
import jdk.tools.jaotc.AOTCompiledClass.AOTKlassData;
import org.graalvm.compiler.code.CompilationResult;

import jdk.vm.ci.code.site.Mark;
import jdk.vm.ci.code.site.Site;
import jdk.vm.ci.hotspot.HotSpotCompiledCode;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;

final class CompiledMethodInfo {

    static final String archStr = System.getProperty("os.arch").toLowerCase();

    private static final int UNINITIALIZED_OFFSET = -1;

    private static class AOTMethodOffsets {
        /**
         * Offset in metaspace names section.
         */
        private int nameOffset;

        /**
         * Offset in the text section at which compiled code starts.
         */
        private int textSectionOffset;

        /**
         * Offset in the metadata section.
         */
        private int metadataOffset;

        /**
         * Offset to the metadata in the GOT table.
         */
        private int metadataGotOffset;

        /**
         * Size of the metadata.
         */
        private int metadataGotSize;

        /**
         * The sequential number corresponding to the order of methods code in code buffer.
         */
        private int codeId;

        AOTMethodOffsets() {
            this.nameOffset = UNINITIALIZED_OFFSET;
            this.textSectionOffset = UNINITIALIZED_OFFSET;
            this.metadataOffset = UNINITIALIZED_OFFSET;
            this.metadataGotOffset = UNINITIALIZED_OFFSET;
            this.metadataGotSize = -1;
            this.codeId = -1;
        }

        void addMethodOffsets(ReadOnlyDataContainer container, String name) {
            verify(name);
            // @formatter:off
            /*
             * The offsets layout should match AOTMethodOffsets structure in AOT JVM runtime
             */
                      // Add the offset to the name in the .metaspace.names section
            container.appendInt(nameOffset).
                      // Add the offset to the code in the .text section
                      appendInt(textSectionOffset).
                      // Add the offset to the metadata in the .method.metadata section
                      appendInt(metadataOffset).
                      // Add the offset to the metadata in the .metadata.got section
                      appendInt(metadataGotOffset).
                      // Add the size of the metadata
                      appendInt(metadataGotSize).
                      // Add code ID.
                      appendInt(codeId);
            // @formatter:on
        }

        private void verify(String name) {
            assert nameOffset >= 0 : "incorrect nameOffset: " + nameOffset + " for method: " + name;
            assert textSectionOffset > 0 : "incorrect textSectionOffset: " + textSectionOffset + " for method: " + name;
            assert metadataOffset >= 0 : "incorrect metadataOffset: " + metadataOffset + " for method: " + name;
            assert metadataGotOffset >= 0 : "incorrect metadataGotOffset: " + metadataGotOffset + " for method: " + name;
            assert metadataGotSize >= 0 : "incorrect metadataGotSize: " + metadataGotSize + " for method: " + name;
            assert codeId >= 0 : "incorrect codeId: " + codeId + " for method: " + name;
        }

        protected void setNameOffset(int offset) {
            nameOffset = offset;
        }

        protected void setTextSectionOffset(int textSectionOffset) {
            this.textSectionOffset = textSectionOffset;
        }

        protected int getTextSectionOffset() {
            return textSectionOffset;
        }

        protected void setCodeId(int codeId) {
            this.codeId = codeId;
        }

        protected int getCodeId() {
            return codeId;
        }

        protected void setMetadataOffset(int offset) {
            metadataOffset = offset;
        }

        protected void setMetadataGotOffset(int metadataGotOffset) {
            this.metadataGotOffset = metadataGotOffset;
        }

        protected void setMetadataGotSize(int length) {
            this.metadataGotSize = length;
        }
    }

    /**
     * Method name.
     */
    private String name;

    /**
     * Result of graal compilation.
     */
    private CompilationResult compilationResult;

    /**
     * HotSpotResolvedJavaMethod or Stub corresponding to the compilation result.
     */
    private JavaMethodInfo methodInfo;

    /**
     * Compiled code from installation.
     */
    private HotSpotCompiledCode code;

    /**
     * Offset to stubs.
     */
    private int stubsOffset;

    /**
     * The total size in bytes of the stub section.
     */
    private int totalStubSize;

    /**
     * Method's offsets.
     */
    private AOTMethodOffsets methodOffsets;

    /**
     * List of stubs (PLT trampoline).
     */
    private HashMap<String, StubInformation> stubs = new HashMap<>();

    /**
     * List of referenced classes.
     */
    private HashSet<AOTKlassData> dependentKlasses = new HashSet<>();

    /**
     * Methods count used to generate unique global method id.
     */
    private static final AtomicInteger methodsCount = new AtomicInteger();

    CompiledMethodInfo(CompilationResult compilationResult, JavaMethodInfo methodInfo) {
        this.name = methodInfo.getNameAndSignature();
        this.compilationResult = compilationResult;
        this.methodInfo = methodInfo;
        this.stubsOffset = UNINITIALIZED_OFFSET;
        this.methodOffsets = new AOTMethodOffsets();
    }

    String name() {
        return name;
    }

    void addMethodOffsets(BinaryContainer binaryContainer, ReadOnlyDataContainer container) {
        this.methodOffsets.setNameOffset(binaryContainer.addMetaspaceName(name));
        this.methodOffsets.addMethodOffsets(container, name);
        for (AOTKlassData data : dependentKlasses) {
            data.addDependentMethod(this);
        }
    }

    CompilationResult getCompilationResult() {
        return compilationResult;
    }

    JavaMethodInfo getMethodInfo() {
        return methodInfo;
    }

    void setTextSectionOffset(int textSectionOffset) {
        methodOffsets.setTextSectionOffset(textSectionOffset);
    }

    public int getTextSectionOffset() {
        return methodOffsets.getTextSectionOffset();
    }

    void setCodeId() {
        methodOffsets.setCodeId(CompiledMethodInfo.getNextCodeId());
    }

    int getCodeId() {
        return this.methodOffsets.getCodeId();
    }

    static int getMethodsCount() {
        return methodsCount.get();
    }

    static int getNextCodeId() {
        return methodsCount.getAndIncrement();
    }

    int getCodeSize() {
        return stubsOffset + getStubCodeSize();
    }

    int getStubCodeSize() {
        return totalStubSize;
    }

    void setMetadataOffset(int offset) {
        this.methodOffsets.setMetadataOffset(offset);
    }

    /**
     * Offset into the code of this method where the stub section starts.
     */
    void setStubsOffset(int offset) {
        stubsOffset = offset;
    }

    int getStubsOffset() {
        return stubsOffset;
    }

    void setMetadataGotOffset(int metadataGotOffset) {
        this.methodOffsets.setMetadataGotOffset(metadataGotOffset);
    }

    void setMetadataGotSize(int length) {
        this.methodOffsets.setMetadataGotSize(length);
    }

    void addStubCode(String call, StubInformation stub) {
        stubs.put(call, stub);
        totalStubSize += stub.getSize();
    }

    StubInformation getStubFor(String call) {
        StubInformation stub = stubs.get(call);
        assert stub != null : "missing stub for call " + call;
        stub.verify();
        return stub;
    }

    void addDependentKlassData(BinaryContainer binaryContainer, HotSpotResolvedObjectType type) {
        AOTKlassData klassData = AOTCompiledClass.addFingerprintKlassData(binaryContainer, type);
        dependentKlasses.add(klassData);
    }

    AOTKlassData getDependentKlassData(HotSpotResolvedObjectType type) {
        AOTKlassData klassData = AOTCompiledClass.getAOTKlassData(type);
        if (dependentKlasses.contains(klassData)) {
            return klassData;
        }
        return null;
    }

    boolean hasMark(Site call, MarkId id) {
        for (Mark m : compilationResult.getMarks()) {
            int adjOffset = m.pcOffset;
            if (archStr.equals("aarch64")) {
                // The mark is at the end of a group of three instructions:
                // adrp; add; ldr
                adjOffset += 12;
            } else {
                // X64-specific code.
                // Call instructions are aligned to 8
                // bytes - 1 on x86 to patch address atomically,
                adjOffset = (adjOffset & (-8)) + 7;
            }
            // Mark points before aligning nops.
            if ((call.pcOffset == adjOffset) && MarkId.getEnum((int) m.id) == id) {
                return true;
            }
        }
        return false;
    }

    String asTag() {
        return "[" + methodInfo.getSymbolName() + "]";
    }

    HotSpotCompiledCode compiledCode() {
        if (code == null) {
            code = methodInfo.compiledCode(compilationResult);
        }
        return code;
    }

    // Free memory
    void clear() {
        this.dependentKlasses = null;
        this.name = null;
    }

    void clearCompileData() {
        this.code = null;
        this.stubs = null;
        this.compilationResult = null;
        this.methodInfo = null;
    }
}

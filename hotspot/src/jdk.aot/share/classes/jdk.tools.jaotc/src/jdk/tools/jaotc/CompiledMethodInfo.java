/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Map;

import jdk.tools.jaotc.binformat.BinaryContainer;
import jdk.tools.jaotc.binformat.ReadOnlyDataContainer;
import jdk.tools.jaotc.AOTCompiledClass.AOTKlassData;
import org.graalvm.compiler.code.CompilationResult;

import jdk.vm.ci.code.site.Mark;
import jdk.vm.ci.code.site.Site;
import jdk.vm.ci.hotspot.HotSpotCompiledCode;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;

public class CompiledMethodInfo {

    public static class StubInformation {
        int stubOffset;         // the offset inside the code (text + stubOffset)
        int stubSize;           // the stub size
        int dispatchJumpOffset; // offset after main dispatch jump instruction
        int resolveJumpOffset;  // offset after jump instruction to runtime call resolution
                               // function.
        int resolveJumpStart;   // offset of jump instruction to VM runtime call resolution
                              // function.
        int c2iJumpOffset;      // offset after jump instruction to c2i adapter for static calls.
        int movOffset; // offset after move instruction which loads from got cell:
                       // - Method* for static call
                       // - Klass* for virtual call

        boolean isVirtual;  // virtual call stub

        // maybe add type of stub as well, right now we only have static stubs

        public StubInformation(int stubOffset, boolean isVirtual) {
            this.stubOffset = stubOffset;
            this.isVirtual = isVirtual;
            this.stubSize = -1;
            this.movOffset = -1;
            this.c2iJumpOffset = -1;
            this.resolveJumpOffset = -1;
            this.resolveJumpStart = -1;
            this.dispatchJumpOffset = -1;
        }

        public int getOffset() {
            return stubOffset;
        }

        public boolean isVirtual() {
            return isVirtual;
        }

        public void setSize(int stubSize) {
            this.stubSize = stubSize;
        }

        public int getSize() {
            return stubSize;
        }

        public void setMovOffset(int movOffset) {
            this.movOffset = movOffset + stubOffset;
        }

        public int getMovOffset() {
            return movOffset;
        }

        public void setC2IJumpOffset(int c2iJumpOffset) {
            this.c2iJumpOffset = c2iJumpOffset + stubOffset;
        }

        public int getC2IJumpOffset() {
            return c2iJumpOffset;
        }

        public void setResolveJumpOffset(int resolveJumpOffset) {
            this.resolveJumpOffset = resolveJumpOffset + stubOffset;
        }

        public int getResolveJumpOffset() {
            return resolveJumpOffset;
        }

        public void setResolveJumpStart(int resolveJumpStart) {
            this.resolveJumpStart = resolveJumpStart + stubOffset;
        }

        public int getResolveJumpStart() {
            return resolveJumpStart;
        }

        public void setDispatchJumpOffset(int dispatchJumpOffset) {
            this.dispatchJumpOffset = dispatchJumpOffset + stubOffset;
        }

        public int getDispatchJumpOffset() {
            return dispatchJumpOffset;
        }

        public void verify() {
            assert stubOffset > 0 : "incorrect stubOffset: " + stubOffset;
            assert stubSize > 0 : "incorrect stubSize: " + stubSize;
            assert movOffset > 0 : "incorrect movOffset: " + movOffset;
            assert dispatchJumpOffset > 0 : "incorrect dispatchJumpOffset: " + dispatchJumpOffset;
            assert resolveJumpStart > 0 : "incorrect resolveJumpStart: " + resolveJumpStart;
            assert resolveJumpOffset > 0 : "incorrect resolveJumpOffset: " + resolveJumpOffset;
            if (!isVirtual) {
                assert c2iJumpOffset > 0 : "incorrect c2iJumpOffset: " + c2iJumpOffset;
            }
        }
    }

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

        public AOTMethodOffsets() {
            this.nameOffset = UNINITIALIZED_OFFSET;
            this.textSectionOffset = UNINITIALIZED_OFFSET;
            this.metadataOffset = UNINITIALIZED_OFFSET;
            this.metadataGotOffset = UNINITIALIZED_OFFSET;
            this.metadataGotSize = -1;
            this.codeId = -1;
        }

        protected void addMethodOffsets(ReadOnlyDataContainer container, String name) {
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
     * Method name
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
    private Map<String, StubInformation> stubs = new HashMap<>();

    /**
     * List of referenced classes.
     */
    private Map<String, AOTKlassData> dependentKlasses = new HashMap<>();

    /**
     * Methods count used to generate unique global method id.
     */
    private static final AtomicInteger methodsCount = new AtomicInteger();

    public CompiledMethodInfo(CompilationResult compilationResult, JavaMethodInfo methodInfo) {
        this.name = methodInfo.getNameAndSignature();
        this.compilationResult = compilationResult;
        this.methodInfo = methodInfo;
        this.stubsOffset = UNINITIALIZED_OFFSET;
        this.methodOffsets = new AOTMethodOffsets();
    }

    public String name() {
        return name;
    }

    public void addMethodOffsets(BinaryContainer binaryContainer, ReadOnlyDataContainer container) {
        this.methodOffsets.setNameOffset(binaryContainer.addMetaspaceName(name));
        this.methodOffsets.addMethodOffsets(container, name);
        for (AOTKlassData data : dependentKlasses.values()) {
            data.addDependentMethod(this);
        }
    }

    public CompilationResult getCompilationResult() {
        return compilationResult;
    }

    public JavaMethodInfo getMethodInfo() {
        return methodInfo;
    }

    public void setTextSectionOffset(int textSectionOffset) {
        methodOffsets.setTextSectionOffset(textSectionOffset);
    }

    public int getTextSectionOffset() {
        return methodOffsets.getTextSectionOffset();
    }

    public void setCodeId() {
        methodOffsets.setCodeId(CompiledMethodInfo.getNextCodeId());
    }

    public int getCodeId() {
        return this.methodOffsets.getCodeId();
    }

    public static int getMethodsCount() {
        return methodsCount.get();
    }

    public static int getNextCodeId() {
        return methodsCount.getAndIncrement();
    }

    public int getCodeSize() {
        return stubsOffset + getStubCodeSize();
    }

    public int getStubCodeSize() {
        return totalStubSize;
    }

    public void setMetadataOffset(int offset) {
        this.methodOffsets.setMetadataOffset(offset);
    }

    /**
     * Offset into the code of this method where the stub section starts.
     */
    public void setStubsOffset(int offset) {
        stubsOffset = offset;
    }

    public int getStubsOffset() {
        return stubsOffset;
    }

    public void setMetadataGotOffset(int metadataGotOffset) {
        this.methodOffsets.setMetadataGotOffset(metadataGotOffset);
    }

    public void setMetadataGotSize(int length) {
        this.methodOffsets.setMetadataGotSize(length);
    }

    public void addStubCode(String call, StubInformation stub) {
        stubs.put(call, stub);
        totalStubSize += stub.getSize();
    }

    public StubInformation getStubFor(String call) {
        StubInformation stub = stubs.get(call);
        assert stub != null : "missing stub for call " + call;
        stub.verify();
        return stub;
    }

    public void addDependentKlassData(BinaryContainer binaryContainer, HotSpotResolvedObjectType type) {
        AOTKlassData klassData = AOTCompiledClass.addFingerprintKlassData(binaryContainer, type);
        String klassName = type.getName();

        if (dependentKlasses.containsKey(klassName)) {
            assert dependentKlasses.get(klassName) == klassData : "duplicated data for klass: " + klassName;
        } else {
            dependentKlasses.put(klassName, klassData);
        }
    }

    public AOTKlassData getDependentKlassData(String klassName) {
        return dependentKlasses.get(klassName);
    }

    public boolean hasMark(Site call, MarkId id) {
        for (Mark m : compilationResult.getMarks()) {
            // TODO: X64-specific code.
            // Call instructions are aligned to 8
            // bytes - 1 on x86 to patch address atomically,
            int adjOffset = (m.pcOffset & (-8)) + 7;
            // Mark points before aligning nops.
            if ((call.pcOffset == adjOffset) && MarkId.getEnum((int) m.id) == id) {
                return true;
            }
        }
        return false;
    }

    public String asTag() {
        return "[" + methodInfo.getSymbolName() + "]";
    }

    public HotSpotCompiledCode compiledCode() {
        if (code == null) {
            code = methodInfo.compiledCode(compilationResult);
        }
        return code;
    }

    // Free memory
    public void clear() {
        this.dependentKlasses = null;
        this.name = null;
    }

    public void clearCompileData() {
        this.code = null;
        this.stubs = null;
        this.compilationResult = null;
        this.methodInfo = null;
    }
}

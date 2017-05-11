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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import jdk.tools.jaotc.binformat.BinaryContainer;
import jdk.tools.jaotc.binformat.ByteContainer;
import jdk.tools.jaotc.binformat.HeaderContainer;
import jdk.tools.jaotc.utils.Timer;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.Debug.Scope;
import org.graalvm.compiler.hotspot.HotSpotHostBackend;
import org.graalvm.compiler.hotspot.stubs.Stub;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotVMConfigStore;
import jdk.vm.ci.hotspot.VMField;

class DataBuilder {

    private final Main main;

    private final HotSpotHostBackend backend;

    private final List<AOTCompiledClass> classes;

    /**
     * Target-independent container in which text symbols and code bytes are created.
     */
    private final BinaryContainer binaryContainer;

    private final HashMap<Long, String> vmAddresses = new HashMap<>();

    public DataBuilder(Main main, HotSpotHostBackend backend, List<AOTCompiledClass> classes, BinaryContainer binaryContainer) {
        this.main = main;
        this.backend = backend;
        this.classes = classes;
        this.binaryContainer = binaryContainer;
        fillVMAddresses(HotSpotJVMCIRuntime.runtime().getConfigStore());
    }

    /**
     * Returns a value-name map of all {@link VMField} fields.
     */
    private void fillVMAddresses(HotSpotVMConfigStore config) {
        for (VMField vmField : config.getFields().values()) {
            if (vmField.value != null && vmField.value instanceof Long) {
                final long address = (Long) vmField.value;
                String value = vmField.name;
                /*
                 * Some fields don't contain addresses but integer values. At least don't add zero
                 * entries to avoid matching null addresses.
                 */
                if (address != 0) {
                    vmAddresses.put(address, value);
                }
            }
        }
        for (Entry<String, Long> vmAddress : config.getAddresses().entrySet()) {
            final long address = vmAddress.getValue();
            String value = vmAddress.getKey();
            String old = vmAddresses.put(address, value);
            if (old != null) {
                throw new InternalError("already in map: address: " + address + ", current: " + value + ", old: " + old);
            }
        }
    }

    /**
     * Get the C/C++ function name associated with the foreign call target {@code address}.
     *
     * @param address native address
     * @return C/C++ functio name associated with the native address
     */
    public String getVMFunctionNameForAddress(long address) {
        return vmAddresses.get(address);
    }

    /**
     * Returns the host backend used for this compilation.
     *
     * @return host backend
     */
    public HotSpotHostBackend getBackend() {
        return backend;
    }

    /**
     * Returns the binary container for this compilation.
     *
     * @return binary container
     */
    public BinaryContainer getBinaryContainer() {
        return binaryContainer;
    }

    /**
     * Prepare data with all compiled classes and stubs.
     *
     * @throws Exception
     */
    @SuppressWarnings("try")
    public void prepareData() throws Exception {
        try (Timer t = new Timer(main, "Parsing compiled code")) {
            /*
             * Copy compiled code into code section container and calls stubs (PLT trampoline).
             */
            CodeSectionProcessor codeSectionProcessor = new CodeSectionProcessor(this);
            for (AOTCompiledClass c : classes) {
                // For each class we need 2 GOT slots:
                // first - for initialized klass
                // second - only for loaded klass
                c.addAOTKlassData(binaryContainer);
                codeSectionProcessor.process(c);
            }
        }

        AOTCompiledClass stubCompiledCode = retrieveStubCode();

        // Free memory!
        try (Timer t = main.options.verbose ? new Timer(main, "Freeing memory") : null) {
            main.printMemoryUsage();
            System.gc();
        }

        MetadataBuilder metadataBuilder = null;
        try (Timer t = new Timer(main, "Processing metadata")) {
            /*
             * Generate metadata for compiled code and copy it into metadata section. Create
             * relocation information for all references (call, constants, etc) in compiled code.
             */
            metadataBuilder = new MetadataBuilder(this);
            metadataBuilder.processMetadata(classes, stubCompiledCode);
        }

        // Free memory!
        try (Timer t = main.options.verbose ? new Timer(main, "Freeing memory") : null) {
            main.printMemoryUsage();
            System.gc();
        }

        try (Timer t = new Timer(main, "Preparing stubs binary")) {
            prepareStubsBinary(stubCompiledCode);
        }
        try (Timer t = new Timer(main, "Preparing compiled binary")) {
            // Should be called after Stubs because they can set dependent klasses.
            prepareCompiledBinary(metadataBuilder);
        }
    }

    /**
     * Get all stubs from Graal and add them to the code section.
     */
    @SuppressWarnings("try")
    private AOTCompiledClass retrieveStubCode() {
        ArrayList<CompiledMethodInfo> stubs = new ArrayList<>();
        for (Stub stub : Stub.getStubs()) {
            try (Scope scope = Debug.scope("CompileStubs")) {
                CompilationResult result = stub.getCompilationResult(backend);
                CompiledMethodInfo cm = new CompiledMethodInfo(result, new AOTStub(stub, backend));
                stubs.add(cm);
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
        }
        AOTCompiledClass stubCompiledCode = new AOTCompiledClass(stubs);
        CodeSectionProcessor codeSectionProcessor = new CodeSectionProcessor(this);
        codeSectionProcessor.process(stubCompiledCode);
        return stubCompiledCode;
    }

    /**
     * Prepare metaspace.offsets section.
     */
    private void prepareCompiledBinary(MetadataBuilder metadataBuilder) {
        for (AOTCompiledClass c : classes) {
            // Create records for compiled AOT methods.
            c.putMethodsData(binaryContainer);
        }
        // Create records for compiled AOT classes.
        AOTCompiledClass.putAOTKlassData(binaryContainer);

        // Fill in AOTHeader
        HeaderContainer header = binaryContainer.getHeaderContainer();
        header.setClassesCount(AOTCompiledClass.getClassesCount());
        header.setMethodsCount(CompiledMethodInfo.getMethodsCount());
        // Record size of got sections
        ByteContainer bc = binaryContainer.getMetaspaceGotContainer();
        header.setMetaspaceGotSize((bc.getByteStreamSize() / 8));
        bc = binaryContainer.getMetadataGotContainer();
        header.setMetadataGotSize((bc.getByteStreamSize() / 8));
        bc = binaryContainer.getOopGotContainer();
        header.setOopGotSize((bc.getByteStreamSize() / 8));
    }

    /**
     * Prepare stubs.offsets section.
     */
    private void prepareStubsBinary(AOTCompiledClass compiledClass) {
        // For each of the compiled stubs, create records holding information about
        // them.
        ArrayList<CompiledMethodInfo> compiledStubs = compiledClass.getCompiledMethods();
        int cntStubs = compiledStubs.size();
        binaryContainer.addMethodsCount(cntStubs, binaryContainer.getStubsOffsetsContainer());
        for (CompiledMethodInfo methodInfo : compiledStubs) {
            // Note, stubs have different offsets container.
            methodInfo.addMethodOffsets(binaryContainer, binaryContainer.getStubsOffsetsContainer());
        }
    }

}

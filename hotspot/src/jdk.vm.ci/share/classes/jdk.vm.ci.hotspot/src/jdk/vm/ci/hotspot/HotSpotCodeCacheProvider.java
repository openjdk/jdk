/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.hotspot;

import static jdk.vm.ci.hotspot.HotSpotCompressedNullConstant.COMPRESSED_NULL;

import java.lang.reflect.Field;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.CompilationResult;
import jdk.vm.ci.code.CompilationResult.Call;
import jdk.vm.ci.code.CompilationResult.ConstantReference;
import jdk.vm.ci.code.CompilationResult.DataPatch;
import jdk.vm.ci.code.CompilationResult.Mark;
import jdk.vm.ci.code.DataSection;
import jdk.vm.ci.code.DataSection.Data;
import jdk.vm.ci.code.DataSection.DataBuilder;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.SerializableConstant;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.VMConstant;

/**
 * HotSpot implementation of {@link CodeCacheProvider}.
 */
public class HotSpotCodeCacheProvider implements CodeCacheProvider {

    protected final HotSpotJVMCIRuntimeProvider runtime;
    public final HotSpotVMConfig config;
    protected final TargetDescription target;
    protected final RegisterConfig regConfig;

    public HotSpotCodeCacheProvider(HotSpotJVMCIRuntimeProvider runtime, HotSpotVMConfig config, TargetDescription target, RegisterConfig regConfig) {
        this.runtime = runtime;
        this.config = config;
        this.target = target;
        this.regConfig = regConfig;
    }

    @Override
    public String getMarkName(Mark mark) {
        int markId = (int) mark.id;
        Field[] fields = runtime.getConfig().getClass().getDeclaredFields();
        for (Field f : fields) {
            if (f.getName().startsWith("MARKID_")) {
                f.setAccessible(true);
                try {
                    if (f.getInt(runtime.getConfig()) == markId) {
                        return f.getName();
                    }
                } catch (Exception e) {
                }
            }
        }
        return CodeCacheProvider.super.getMarkName(mark);
    }

    /**
     * Decodes a call target to a mnemonic if possible.
     */
    @Override
    public String getTargetName(Call call) {
        Field[] fields = runtime.getConfig().getClass().getDeclaredFields();
        for (Field f : fields) {
            if (f.getName().endsWith("Stub")) {
                f.setAccessible(true);
                try {
                    Object address = f.get(runtime.getConfig());
                    if (address.equals(call.target)) {
                        return f.getName() + ":0x" + Long.toHexString((Long) address);
                    }
                } catch (Exception e) {
                }
            }
        }
        return CodeCacheProvider.super.getTargetName(call);
    }

    @Override
    public RegisterConfig getRegisterConfig() {
        return regConfig;
    }

    @Override
    public int getMinimumOutgoingSize() {
        return runtime.getConfig().runtimeCallStackSize;
    }

    private InstalledCode logOrDump(InstalledCode installedCode, CompilationResult compResult) {
        ((HotSpotJVMCIRuntime) runtime).notifyInstall(this, installedCode, compResult);
        return installedCode;
    }

    public InstalledCode installCode(CompilationRequest compRequest, CompilationResult compResult, InstalledCode installedCode, SpeculationLog log, boolean isDefault) {
        HotSpotResolvedJavaMethod method = compRequest != null ? (HotSpotResolvedJavaMethod) compRequest.getMethod() : null;
        InstalledCode resultInstalledCode;
        if (installedCode == null) {
            if (method == null) {
                // Must be a stub
                resultInstalledCode = new HotSpotRuntimeStub(compResult.getName());
            } else {
                resultInstalledCode = new HotSpotNmethod(method, compResult.getName(), isDefault);
            }
        } else {
            resultInstalledCode = installedCode;
        }
        HotSpotCompiledCode compiledCode;
        if (method != null) {
            final int id;
            final long jvmciEnv;
            if (compRequest instanceof HotSpotCompilationRequest) {
                HotSpotCompilationRequest hsCompRequest = (HotSpotCompilationRequest) compRequest;
                id = hsCompRequest.getId();
                jvmciEnv = hsCompRequest.getJvmciEnv();
            } else {
                id = method.allocateCompileId(compRequest.getEntryBCI());
                jvmciEnv = 0L;
            }
            compiledCode = new HotSpotCompiledNmethod(method, compResult, id, jvmciEnv);
        } else {
            compiledCode = new HotSpotCompiledCode(compResult);
        }
        int result = runtime.getCompilerToVM().installCode(target, compiledCode, resultInstalledCode, (HotSpotSpeculationLog) log);
        if (result != config.codeInstallResultOk) {
            String resultDesc = config.getCodeInstallResultDescription(result);
            if (compiledCode instanceof HotSpotCompiledNmethod) {
                HotSpotCompiledNmethod compiledNmethod = (HotSpotCompiledNmethod) compiledCode;
                String msg = compiledNmethod.getInstallationFailureMessage();
                if (msg != null) {
                    msg = String.format("Code installation failed: %s%n%s", resultDesc, msg);
                } else {
                    msg = String.format("Code installation failed: %s", resultDesc);
                }
                if (result == config.codeInstallResultDependenciesInvalid) {
                    throw new AssertionError(resultDesc + " " + msg);
                }
                throw new BailoutException(result != config.codeInstallResultDependenciesFailed, msg);
            } else {
                throw new BailoutException("Error installing %s: %s", compResult.getName(), resultDesc);
            }
        }
        return logOrDump(resultInstalledCode, compResult);
    }

    public void invalidateInstalledCode(InstalledCode installedCode) {
        runtime.getCompilerToVM().invalidateInstalledCode(installedCode);
    }

    private Data createSingleDataItem(Constant constant) {
        int size;
        DataBuilder builder;
        if (constant instanceof VMConstant) {
            VMConstant vmConstant = (VMConstant) constant;
            boolean compressed;
            if (constant instanceof HotSpotConstant) {
                HotSpotConstant c = (HotSpotConstant) vmConstant;
                compressed = c.isCompressed();
            } else {
                throw new JVMCIError(String.valueOf(constant));
            }

            size = compressed ? 4 : target.wordSize;
            if (size == 4) {
                builder = (buffer, patch) -> {
                    patch.accept(new DataPatch(buffer.position(), new ConstantReference(vmConstant)));
                    buffer.putInt(0xDEADDEAD);
                };
            } else {
                assert size == 8;
                builder = (buffer, patch) -> {
                    patch.accept(new DataPatch(buffer.position(), new ConstantReference(vmConstant)));
                    buffer.putLong(0xDEADDEADDEADDEADL);
                };
            }
        } else if (JavaConstant.isNull(constant)) {
            boolean compressed = COMPRESSED_NULL.equals(constant);
            size = compressed ? 4 : target.wordSize;
            builder = DataBuilder.zero(size);
        } else if (constant instanceof SerializableConstant) {
            SerializableConstant s = (SerializableConstant) constant;
            size = s.getSerializedSize();
            builder = DataBuilder.serializable(s);
        } else {
            throw new JVMCIError(String.valueOf(constant));
        }

        return new Data(size, size, builder);
    }

    public Data createDataItem(Constant... constants) {
        assert constants.length > 0;
        if (constants.length == 1) {
            return createSingleDataItem(constants[0]);
        } else {
            DataBuilder[] builders = new DataBuilder[constants.length];
            int size = 0;
            int alignment = 1;
            for (int i = 0; i < constants.length; i++) {
                Data data = createSingleDataItem(constants[i]);

                assert size % data.getAlignment() == 0 : "invalid alignment in packed constants";
                alignment = DataSection.lcm(alignment, data.getAlignment());

                builders[i] = data.getBuilder();
                size += data.getSize();
            }
            DataBuilder ret = (buffer, patches) -> {
                for (DataBuilder b : builders) {
                    b.emit(buffer, patches);
                }
            };
            return new Data(alignment, size, ret);
        }
    }

    @Override
    public TargetDescription getTarget() {
        return target;
    }

    public String disassemble(InstalledCode code) {
        if (code.isValid()) {
            return runtime.getCompilerToVM().disassembleCodeBlob(code);
        }
        return null;
    }

    public SpeculationLog createSpeculationLog() {
        return new HotSpotSpeculationLog();
    }

    public long getMaxCallTargetOffset(long address) {
        return runtime.getCompilerToVM().getMaxCallTargetOffset(address);
    }

    public boolean shouldDebugNonSafepoints() {
        return runtime.getCompilerToVM().shouldDebugNonSafepoints();
    }

    /**
     * Notifies the VM of statistics for a completed compilation.
     *
     * @param id the identifier of the compilation
     * @param method the method compiled
     * @param osr specifies if the compilation was for on-stack-replacement
     * @param processedBytecodes the number of bytecodes processed during the compilation, including
     *            the bytecodes of all inlined methods
     * @param time the amount time spent compiling {@code method}
     * @param timeUnitsPerSecond the granularity of the units for the {@code time} value
     * @param installedCode the nmethod installed as a result of the compilation
     */
    public void notifyCompilationStatistics(int id, HotSpotResolvedJavaMethod method, boolean osr, int processedBytecodes, long time, long timeUnitsPerSecond, InstalledCode installedCode) {
        runtime.getCompilerToVM().notifyCompilationStatistics(id, (HotSpotResolvedJavaMethodImpl) method, osr, processedBytecodes, time, timeUnitsPerSecond, installedCode);
    }

    /**
     * Resets all compilation statistics.
     */
    public void resetCompilationStatistics() {
        runtime.getCompilerToVM().resetCompilationStatistics();
    }
}

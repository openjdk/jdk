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

import static jdk.vm.ci.hotspot.HotSpotCompressedNullConstant.*;

import java.lang.reflect.*;

import jdk.vm.ci.code.*;
import jdk.vm.ci.code.CompilationResult.*;
import jdk.vm.ci.code.DataSection.*;
import jdk.vm.ci.common.*;
import jdk.vm.ci.meta.*;

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

    public InstalledCode logOrDump(InstalledCode installedCode, CompilationResult compResult) {
        HotSpotJVMCIRuntime.runtime().notifyInstall(this, installedCode, compResult);
        return installedCode;
    }

    private InstalledCode installCode(CompilationResult compResult, HotSpotCompiledNmethod compiledCode, InstalledCode installedCode, SpeculationLog log) {
        int result = runtime.getCompilerToVM().installCode(target, compiledCode, installedCode, log);
        if (result != config.codeInstallResultOk) {
            String msg = compiledCode.getInstallationFailureMessage();
            String resultDesc = config.getCodeInstallResultDescription(result);
            if (msg != null) {
                msg = String.format("Code installation failed: %s%n%s", resultDesc, msg);
            } else {
                msg = String.format("Code installation failed: %s", resultDesc);
            }
            if (result == config.codeInstallResultDependenciesInvalid) {
                throw new AssertionError(resultDesc + " " + msg);
            }
            throw new BailoutException(result != config.codeInstallResultDependenciesFailed, msg);
        }
        return logOrDump(installedCode, compResult);
    }

    public InstalledCode installMethod(HotSpotResolvedJavaMethod method, CompilationResult compResult, long jvmciEnv, boolean isDefault) {
        if (compResult.getId() == -1) {
            compResult.setId(method.allocateCompileId(compResult.getEntryBCI()));
        }
        HotSpotInstalledCode installedCode = new HotSpotNmethod(method, compResult.getName(), isDefault);
        HotSpotCompiledNmethod compiledCode = new HotSpotCompiledNmethod(method, compResult, jvmciEnv);
        return installCode(compResult, compiledCode, installedCode, method.getSpeculationLog());
    }

    @Override
    public InstalledCode addMethod(ResolvedJavaMethod method, CompilationResult compResult, SpeculationLog log, InstalledCode predefinedInstalledCode) {
        HotSpotResolvedJavaMethod hotspotMethod = (HotSpotResolvedJavaMethod) method;
        if (compResult.getId() == -1) {
            compResult.setId(hotspotMethod.allocateCompileId(compResult.getEntryBCI()));
        }
        InstalledCode installedCode = predefinedInstalledCode;
        if (installedCode == null) {
            HotSpotInstalledCode code = new HotSpotNmethod(hotspotMethod, compResult.getName(), false);
            installedCode = code;
        }
        HotSpotCompiledNmethod compiledCode = new HotSpotCompiledNmethod(hotspotMethod, compResult);
        return installCode(compResult, compiledCode, installedCode, log);
    }

    @Override
    public InstalledCode setDefaultMethod(ResolvedJavaMethod method, CompilationResult compResult) {
        HotSpotResolvedJavaMethod hotspotMethod = (HotSpotResolvedJavaMethod) method;
        return installMethod(hotspotMethod, compResult, 0L, true);
    }

    public HotSpotNmethod addExternalMethod(ResolvedJavaMethod method, CompilationResult compResult) {
        HotSpotResolvedJavaMethod javaMethod = (HotSpotResolvedJavaMethod) method;
        if (compResult.getId() == -1) {
            compResult.setId(javaMethod.allocateCompileId(compResult.getEntryBCI()));
        }
        HotSpotNmethod code = new HotSpotNmethod(javaMethod, compResult.getName(), false, true);
        HotSpotCompiledNmethod compiled = new HotSpotCompiledNmethod(javaMethod, compResult);
        CompilerToVM vm = runtime.getCompilerToVM();
        int result = vm.installCode(target, compiled, code, null);
        if (result != runtime.getConfig().codeInstallResultOk) {
            return null;
        }
        return code;
    }

    public boolean needsDataPatch(JavaConstant constant) {
        return constant instanceof HotSpotMetaspaceConstant;
    }

    private Data createSingleDataItem(Constant constant) {
        int size;
        DataBuilder builder;
        if (constant instanceof VMConstant) {
            VMConstant vmConstant = (VMConstant) constant;
            boolean compressed;
            long raw;
            if (constant instanceof HotSpotObjectConstant) {
                HotSpotObjectConstant c = (HotSpotObjectConstant) vmConstant;
                compressed = c.isCompressed();
                raw = 0xDEADDEADDEADDEADL;
            } else if (constant instanceof HotSpotMetaspaceConstant) {
                HotSpotMetaspaceConstant meta = (HotSpotMetaspaceConstant) constant;
                compressed = meta.isCompressed();
                raw = meta.rawValue();
            } else {
                throw new JVMCIError(String.valueOf(constant));
            }

            size = target.getSizeInBytes(compressed ? JavaKind.Int : target.wordKind);
            if (size == 4) {
                builder = (buffer, patch) -> {
                    patch.accept(new DataPatch(buffer.position(), new ConstantReference(vmConstant)));
                    buffer.putInt((int) raw);
                };
            } else {
                assert size == 8;
                builder = (buffer, patch) -> {
                    patch.accept(new DataPatch(buffer.position(), new ConstantReference(vmConstant)));
                    buffer.putLong(raw);
                };
            }
        } else if (JavaConstant.isNull(constant)) {
            boolean compressed = COMPRESSED_NULL.equals(constant);
            size = target.getSizeInBytes(compressed ? JavaKind.Int : target.wordKind);
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
            long codeBlob = code.getAddress();
            return runtime.getCompilerToVM().disassembleCodeBlob(codeBlob);
        }
        return null;
    }

    public SpeculationLog createSpeculationLog() {
        return new HotSpotSpeculationLog();
    }
}

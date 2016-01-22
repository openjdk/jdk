/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CompiledCode;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.Mark;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;

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

    private InstalledCode logOrDump(InstalledCode installedCode, CompiledCode compiledCode) {
        ((HotSpotJVMCIRuntime) runtime).notifyInstall(this, installedCode, compiledCode);
        return installedCode;
    }

    public InstalledCode installCode(ResolvedJavaMethod method, CompiledCode compiledCode, InstalledCode installedCode, SpeculationLog log, boolean isDefault) {
        InstalledCode resultInstalledCode;
        if (installedCode == null) {
            if (method == null) {
                // Must be a stub
                resultInstalledCode = new HotSpotRuntimeStub(((HotSpotCompiledCode) compiledCode).getName());
            } else {
                resultInstalledCode = new HotSpotNmethod((HotSpotResolvedJavaMethod) method, ((HotSpotCompiledCode) compiledCode).getName(), isDefault);
            }
        } else {
            resultInstalledCode = installedCode;
        }

        int result = runtime.getCompilerToVM().installCode(target, (HotSpotCompiledCode) compiledCode, resultInstalledCode, (HotSpotSpeculationLog) log);
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
                throw new BailoutException("Error installing %s: %s", ((HotSpotCompiledCode) compiledCode).getName(), resultDesc);
            }
        }
        return logOrDump(resultInstalledCode, compiledCode);
    }

    public void invalidateInstalledCode(InstalledCode installedCode) {
        runtime.getCompilerToVM().invalidateInstalledCode(installedCode);
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
     * Resets all compilation statistics.
     */
    public void resetCompilationStatistics() {
        runtime.getCompilerToVM().resetCompilationStatistics();
    }
}

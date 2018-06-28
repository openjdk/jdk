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


package org.graalvm.compiler.code;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.CodeUtil.DefaultRefMapFormatter;
import jdk.vm.ci.code.CodeUtil.RefMapFormatter;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.code.site.ExceptionHandler;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.code.site.Mark;

import org.graalvm.compiler.serviceprovider.ServiceProvider;

/**
 * {@link HexCodeFile} based implementation of {@link DisassemblerProvider}.
 */
@ServiceProvider(DisassemblerProvider.class)
public class HexCodeFileDisassemblerProvider implements DisassemblerProvider {

    @Override
    public String disassembleCompiledCode(CodeCacheProvider codeCache, CompilationResult compResult) {
        assert compResult != null;
        return disassemble(codeCache, compResult, null);
    }

    @Override
    public String getName() {
        return "hcf";
    }

    @Override
    public String disassembleInstalledCode(CodeCacheProvider codeCache, CompilationResult compResult, InstalledCode installedCode) {
        assert installedCode != null;
        return installedCode.isValid() ? disassemble(codeCache, compResult, installedCode) : null;
    }

    private static String disassemble(CodeCacheProvider codeCache, CompilationResult compResult, InstalledCode installedCode) {
        TargetDescription target = codeCache.getTarget();
        RegisterConfig regConfig = codeCache.getRegisterConfig();
        byte[] code = installedCode == null ? Arrays.copyOf(compResult.getTargetCode(), compResult.getTargetCodeSize()) : installedCode.getCode();
        if (code == null) {
            // Method was deoptimized/invalidated
            return "";
        }
        long start = installedCode == null ? 0L : installedCode.getStart();
        HexCodeFile hcf = new HexCodeFile(code, start, target.arch.getName(), target.wordSize * 8);
        if (compResult != null) {
            HexCodeFile.addAnnotations(hcf, compResult.getAnnotations());
            addExceptionHandlersComment(compResult, hcf);
            Register fp = regConfig.getFrameRegister();
            RefMapFormatter slotFormatter = new DefaultRefMapFormatter(target.wordSize, fp, 0);
            for (Infopoint infopoint : compResult.getInfopoints()) {
                if (infopoint instanceof Call) {
                    Call call = (Call) infopoint;
                    if (call.debugInfo != null) {
                        hcf.addComment(call.pcOffset + call.size, CodeUtil.append(new StringBuilder(100), call.debugInfo, slotFormatter).toString());
                    }
                    addOperandComment(hcf, call.pcOffset, "{" + codeCache.getTargetName(call) + "}");
                } else {
                    if (infopoint.debugInfo != null) {
                        hcf.addComment(infopoint.pcOffset, CodeUtil.append(new StringBuilder(100), infopoint.debugInfo, slotFormatter).toString());
                    }
                    addOperandComment(hcf, infopoint.pcOffset, "{infopoint: " + infopoint.reason + "}");
                }
            }
            for (DataPatch site : compResult.getDataPatches()) {
                hcf.addOperandComment(site.pcOffset, "{" + site.reference.toString() + "}");
            }
            for (Mark mark : compResult.getMarks()) {
                hcf.addComment(mark.pcOffset, codeCache.getMarkName(mark));
            }
        }
        String hcfEmbeddedString = hcf.toEmbeddedString();
        return HexCodeFileDisTool.tryDisassemble(hcfEmbeddedString);
    }

    private static void addExceptionHandlersComment(CompilationResult compResult, HexCodeFile hcf) {
        if (!compResult.getExceptionHandlers().isEmpty()) {
            String nl = HexCodeFile.NEW_LINE;
            StringBuilder buf = new StringBuilder("------ Exception Handlers ------").append(nl);
            for (ExceptionHandler e : compResult.getExceptionHandlers()) {
                buf.append("    ").append(e.pcOffset).append(" -> ").append(e.handlerPos).append(nl);
                hcf.addComment(e.pcOffset, "[exception -> " + e.handlerPos + "]");
                hcf.addComment(e.handlerPos, "[exception handler for " + e.pcOffset + "]");
            }
            hcf.addComment(0, buf.toString());
        }
    }

    private static void addOperandComment(HexCodeFile hcf, int pos, String comment) {
        hcf.addOperandComment(pos, comment);
    }

    /**
     * Interface to the tool for disassembling an {@link HexCodeFile#toEmbeddedString() embedded}
     * {@link HexCodeFile}.
     */
    static class HexCodeFileDisTool {
        static final MethodHandle processMethod;

        static {
            MethodHandle toolMethod = null;
            try {
                Class<?> toolClass = Class.forName("com.oracle.max.hcfdis.HexCodeFileDis", true, ClassLoader.getSystemClassLoader());
                toolMethod = MethodHandles.lookup().unreflect(toolClass.getDeclaredMethod("processEmbeddedString", String.class));
            } catch (Exception e) {
                // Tool not available on the class path
            }
            processMethod = toolMethod;
        }

        public static String tryDisassemble(String hcfEmbeddedString) {
            if (processMethod != null) {
                try {
                    return (String) processMethod.invokeExact(hcfEmbeddedString);
                } catch (Throwable e) {
                    // If the tool is available, for now let's be noisy when it fails
                    throw new InternalError(e);
                }
            }
            return hcfEmbeddedString;
        }
    }

}

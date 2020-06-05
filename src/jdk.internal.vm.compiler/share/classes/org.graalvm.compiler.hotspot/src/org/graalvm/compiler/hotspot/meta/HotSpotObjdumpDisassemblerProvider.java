/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.hotspot.meta;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.code.CompilationResult.CodeAnnotation;
import org.graalvm.compiler.code.DisassemblerProvider;
import org.graalvm.compiler.serviceprovider.ServiceProvider;
import org.graalvm.util.CollectionsUtil;

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
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.hotspot.HotSpotCodeCacheProvider;
import jdk.vm.ci.services.Services;

/**
 * A provider that uses the {@code GNU objdump} utility to disassemble code.
 */
@ServiceProvider(DisassemblerProvider.class)
public class HotSpotObjdumpDisassemblerProvider extends HotSpotDisassemblerProvider {

    private final String objdump = getObjdump();

    @Override
    public String disassembleCompiledCode(CodeCacheProvider codeCache, CompilationResult compResult) {
        if (objdump == null) {
            return null;
        }
        File tmp = null;
        try {
            tmp = File.createTempFile("compiledBinary", ".bin");
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(compResult.getTargetCode());
            }

            String[] cmdline;
            String arch = Services.getSavedProperties().get("os.arch");
            if (arch.equals("amd64") || arch.equals("x86_64")) {
                cmdline = new String[]{objdump, "-D", "-b", "binary", "-M", "x86-64", "-m", "i386", tmp.getAbsolutePath()};
            } else if (arch.equals("aarch64")) {
                cmdline = new String[]{objdump, "-D", "-b", "binary", "-m", "aarch64", tmp.getAbsolutePath()};
            } else {
                return null;
            }

            Pattern p = Pattern.compile(" *(([0-9a-fA-F]+):\t.*)");

            TargetDescription target = codeCache.getTarget();
            RegisterConfig regConfig = codeCache.getRegisterConfig();
            Register fp = regConfig.getFrameRegister();
            RefMapFormatter slotFormatter = new DefaultRefMapFormatter(target.wordSize, fp, 0);

            Map<Integer, String> annotations = new HashMap<>();
            for (DataPatch site : compResult.getDataPatches()) {
                putAnnotation(annotations, site.pcOffset, "{" + site.reference.toString() + "}");
            }
            for (CompilationResult.CodeMark mark : compResult.getMarks()) {
                putAnnotation(annotations, mark.pcOffset, mark.id.getName());
            }
            for (CodeAnnotation a : compResult.getCodeAnnotations()) {
                putAnnotation(annotations, a.getPosition(), a.toString());
            }
            for (Infopoint infopoint : compResult.getInfopoints()) {
                if (infopoint instanceof Call) {
                    Call call = (Call) infopoint;
                    if (call.debugInfo != null) {
                        putAnnotation(annotations, call.pcOffset + call.size, CodeUtil.append(new StringBuilder(100), call.debugInfo, slotFormatter).toString());
                    }
                    putAnnotation(annotations, call.pcOffset, "{" + codeCache.getTargetName(call) + "}");
                } else {
                    if (infopoint.debugInfo != null) {
                        putAnnotation(annotations, infopoint.pcOffset, CodeUtil.append(new StringBuilder(100), infopoint.debugInfo, slotFormatter).toString());
                    }
                    putAnnotation(annotations, infopoint.pcOffset, "{infopoint: " + infopoint.reason + "}");
                }
            }

            Process proc = Runtime.getRuntime().exec(cmdline);
            InputStream is = proc.getInputStream();
            StringBuilder sb = new StringBuilder();

            InputStreamReader isr = new InputStreamReader(is);
            try (BufferedReader br = new BufferedReader(isr)) {
                String line;
                while ((line = br.readLine()) != null) {
                    Matcher m = p.matcher(line);
                    if (m.find()) {
                        int address = Integer.parseInt(m.group(2), 16);
                        String annotation = annotations.get(address);
                        if (annotation != null) {
                            annotation = annotation.replace("\n", "\n; ");
                            sb.append("; ").append(annotation).append('\n');
                        }
                        line = m.replaceAll("0x$1");
                    }
                    sb.append(line).append("\n");
                }
            }
            try (BufferedReader ebr = new BufferedReader(new InputStreamReader(proc.getErrorStream()))) {
                String errLine = ebr.readLine();
                if (errLine != null) {
                    System.err.println("Error output from executing: " + CollectionsUtil.mapAndJoin(cmdline, e -> quoteShellArg(String.valueOf(e)), " "));
                    System.err.println(errLine);
                    while ((errLine = ebr.readLine()) != null) {
                        System.err.println(errLine);
                    }
                }
            }
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (tmp != null) {
                tmp.delete();
            }
        }
    }

    /**
     * Pattern for a single shell command argument that does not need to quoted.
     */
    private static final Pattern SAFE_SHELL_ARG = Pattern.compile("[A-Za-z0-9@%_\\-\\+=:,\\./]+");

    /**
     * Reliably quote a string as a single shell command argument.
     */
    public static String quoteShellArg(String arg) {
        if (arg.isEmpty()) {
            return "\"\"";
        }
        Matcher m = SAFE_SHELL_ARG.matcher(arg);
        if (m.matches()) {
            return arg;
        }
        // See http://stackoverflow.com/a/1250279
        return "'" + arg.replace("'", "'\"'\"'") + "'";
    }

    /**
     * Searches for a valid GNU objdump executable.
     */
    private static String getObjdump() {
        // On macOS, `brew install binutils` will provide
        // an executable named gobjdump
        for (String candidate : new String[]{"objdump", "gobjdump"}) {
            try {
                String[] cmd = {candidate, "--version"};
                Process proc = Runtime.getRuntime().exec(cmd);
                InputStream is = proc.getInputStream();
                int exitValue = proc.waitFor();
                if (exitValue == 0) {
                    byte[] buf = new byte[is.available()];
                    int pos = 0;
                    while (pos < buf.length) {
                        int read = is.read(buf, pos, buf.length - pos);
                        pos += read;
                    }
                    String output = new String(buf);
                    if (output.contains("GNU objdump")) {
                        return candidate;
                    }
                }
            } catch (IOException | InterruptedException e) {
            }
        }
        return null;
    }

    private static void putAnnotation(Map<Integer, String> annotations, int idx, String txt) {
        String newAnnotation = annotations.getOrDefault(idx, "") + "\n" + txt;
        annotations.put(idx, newAnnotation);
    }

    @Override
    public String disassembleInstalledCode(CodeCacheProvider codeCache, CompilationResult compResult, InstalledCode code) {
        return ((HotSpotCodeCacheProvider) codeCache).disassemble(code);
    }

    @Override
    public String getName() {
        return "hsdis-objdump";
    }
}

/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.jpackage.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import jdk.jpackage.internal.WixTool.Wix3Toolset;
import jdk.jpackage.internal.WixTool.WixToolsetBase;

/**
 * WiX pipeline. Compiles and links WiX sources.
 */
public class WixPipeline {
    WixPipeline() {
        sources = new ArrayList<>();
        lightOptions = new ArrayList<>();
    }

    WixPipeline setToolset(WixToolsetBase v) {
        toolset = v;
        return this;
    }

    WixPipeline setWixVariables(Map<String, String> v) {
        wixVariables = v;
        return this;
    }

    WixPipeline setWixObjDir(Path v) {
        wixObjDir = v;
        return this;
    }

    WixPipeline setWorkDir(Path v) {
        workDir = v;
        return this;
    }

    WixPipeline addSource(Path source, Map<String, String> wixVariables) {
        WixSource entry = new WixSource();
        entry.source = source;
        entry.variables = wixVariables;
        sources.add(entry);
        return this;
    }

    WixPipeline addLightOptions(String ... v) {
        lightOptions.addAll(List.of(v));
        return this;
    }

    void buildMsi(Path msi) throws IOException {
        if (toolset instanceof Wix3Toolset) {
            buildMsiWix3(msi);
        } else {
            buildMsiWix4(msi);
        }
    }

    private void buildMsiWix4(Path msi) throws IOException {

    }

    private void buildMsiWix3(Path msi) throws IOException {
        List<Path> wixObjs = new ArrayList<>();
        for (var source : sources) {
            wixObjs.add(compileWix3(source));
        }

        List<String> lightCmdline = new ArrayList<>(List.of(
                toolset.getToolPath(WixTool.Light3).toString(),
                "-nologo",
                "-spdb",
                "-ext", "WixUtilExtension",
                "-out", msi.toString()
        ));

        lightCmdline.addAll(lightOptions);
        wixObjs.stream().map(Path::toString).forEach(lightCmdline::add);

        Files.createDirectories(IOUtils.getParent(msi));
        execute(lightCmdline);
    }

    private Path compileWix3(WixSource wixSource) throws IOException {
        UnaryOperator<Path> adjustPath = path -> {
            return workDir != null ? path.toAbsolutePath() : path;
        };

        Path wixObj = adjustPath.apply(wixObjDir).resolve(IOUtils.replaceSuffix(
                IOUtils.getFileName(wixSource.source), ".wixobj"));

        List<String> cmdline = new ArrayList<>(List.of(
                toolset.getToolPath(WixTool.Candle3).toString(),
                "-nologo",
                adjustPath.apply(wixSource.source).toString(),
                "-ext", "WixUtilExtension",
                "-arch", WixFragmentBuilder.is64Bit() ? "x64" : "x86",
                "-out", wixObj.toAbsolutePath().toString()
        ));

        Stream.of(wixVariables, wixSource.variables).filter(Objects::nonNull).
                reduce((a, b) -> {
                    a.putAll(b);
                    return a;
                }).ifPresent(wixVars -> {
            wixVars.entrySet().stream().map(wixVar -> {
                return String.format("-d%s=%s", wixVar.getKey(), wixVar.
                        getValue());
            }).reduce(cmdline, (ctnr, wixVar) -> {
                ctnr.add(wixVar);
                return ctnr;
            }, (x, y) -> {
                x.addAll(y);
                return x;
            });
        });

        execute(cmdline);

        return wixObj;
    }

    private void execute(List<String> cmdline) throws IOException {
        Executor.of(new ProcessBuilder(cmdline).directory(
                workDir != null ? workDir.toFile() : null)).executeExpectSuccess();
    }

    private static final class WixSource {
        Path source;
        Map<String, String> variables;
    }

    private WixToolsetBase toolset;
    private Map<String, String> wixVariables;
    private List<String> lightOptions;
    private Path wixObjDir;
    private Path workDir;
    private List<WixSource> sources;
}

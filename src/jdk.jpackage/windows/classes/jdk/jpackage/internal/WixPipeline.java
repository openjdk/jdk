/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.internal.ShortPathUtils.adjustPath;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import jdk.jpackage.internal.util.PathUtils;

/**
 * WiX pipeline. Compiles and links WiX sources.
 */
final class WixPipeline {

    static final class Builder {
        Builder() {
        }

        WixPipeline create(WixToolset toolset) {
            Objects.requireNonNull(toolset);
            Objects.requireNonNull(workDir);
            Objects.requireNonNull(wixObjDir);
            if (sources.isEmpty()) {
                throw new IllegalArgumentException("no sources");
            }

            final var absWorkDir = workDir.normalize().toAbsolutePath();

            final var absObjWorkDir = PathUtils.normalizedAbsolutePath(wixObjDir);

            final var absSources = sources.stream().map(source -> {
                return source.copyWithPath(PathUtils.normalizedAbsolutePath(source.path));
            }).toList();

            return new WixPipeline(
                    toolset,
                    adjustPath(absWorkDir),
                    absObjWorkDir,
                    wixVariables.createdImmutableCopy(),
                    mapLightOptions(PathUtils::normalizedAbsolutePath),
                    absSources,
                    msiMutators);
        }

        Builder setWixObjDir(Path v) {
            wixObjDir = v;
            return this;
        }

        Builder setWorkDir(Path v) {
            workDir = v;
            return this;
        }

        Builder putWixVariables(WixVariables v) {
            wixVariables.putAll(v);
            return this;
        }

        Builder putWixVariables(Map<String, String> v) {
            wixVariables.putAll(v);
            return this;
        }

        Builder addSource(Path source, WixVariables wixVariables) {
            sources.add(new WixSource(source, wixVariables.createdImmutableCopy()));
            return this;
        }

        Builder addMsiMutator(MsiMutator msiMutator, List<String> args) {
            msiMutators.add(new MsiMutatorWithArgs(msiMutator, args));
            return this;
        }

        Builder addSource(Path source) {
            return addSource(source, WixVariables.EMPTY);
        }

        Builder addLightOptions(String ... v) {
            lightOptions.addAll(List.of(v));
            return this;
        }

        private List<String> mapLightOptions(UnaryOperator<Path> normalizePath) {
            var pathOptions = Set.of("-b", "-loc");
            List<String> reply = new ArrayList<>();
            boolean convPath = false;
            for (var opt : lightOptions) {
                if (convPath) {
                    opt = normalizePath.apply(Path.of(opt)).toString();
                    convPath = false;
                } else if (pathOptions.contains(opt)) {
                    convPath = true;
                }
                reply.add(opt);
            }
            return reply;
        }

        private Path workDir;
        private Path wixObjDir;
        private final WixVariables wixVariables = new WixVariables();
        private final List<String> lightOptions = new ArrayList<>();
        private final List<WixSource> sources = new ArrayList<>();
        private final List<MsiMutatorWithArgs> msiMutators = new ArrayList<>();
    }

    static Builder build() {
        return new Builder();
    }

    private WixPipeline(
            WixToolset toolset,
            Path workDir,
            Path wixObjDir,
            WixVariables wixVariables,
            List<String> lightOptions,
            List<WixSource> sources,
            List<MsiMutatorWithArgs> msiMutators) {

        this.toolset = Objects.requireNonNull(toolset);
        this.workDir = Objects.requireNonNull(workDir);
        this.wixObjDir = Objects.requireNonNull(wixObjDir);
        this.wixVariables = Objects.requireNonNull(wixVariables);
        this.lightOptions = Objects.requireNonNull(lightOptions);
        this.sources = Objects.requireNonNull(sources);
        this.msiMutators = Objects.requireNonNull(msiMutators);
    }

    void buildMsi(Path msi) throws IOException {

        // Use short path to the output msi to workaround
        // WiX limitations of handling long paths.
        var transientMsi = wixObjDir.resolve("a.msi");

        var configRoot = workDir.resolve(transientMsi).getParent();

        for (var msiMutator : msiMutators) {
            msiMutator.addToConfigRoot(configRoot);
        }

        switch (toolset.getType()) {
            case Wix3 -> buildMsiWix3(transientMsi);
            case Wix4 -> buildMsiWix4(transientMsi);
        }

        for (var msiMutator : msiMutators) {
            msiMutator.execute(configRoot, workDir.resolve(transientMsi));
        }

        IOUtils.copyFile(workDir.resolve(transientMsi), msi);
    }

    private void buildMsiWix4(Path msi) throws IOException {

        List<String> cmdline = new ArrayList<>(List.of(
                toolset.getToolPath(WixTool.Wix4).toString(),
                "build",
                "-nologo",
                "-pdbtype", "none",
                "-intermediatefolder", wixObjDir.toString(),
                "-ext", "WixToolset.Util.wixext",
                "-arch", WixFragmentBuilder.is64Bit() ? "x64" : "x86"
        ));

        cmdline.addAll(lightOptions);

        addWixVariablesToCommandLine(sources.stream(), cmdline::addAll);

        cmdline.addAll(sources.stream().map(wixSource -> {
            return wixSource.path.toString();
        }).toList());

        cmdline.addAll(List.of("-out", msi.toString()));

        execute(cmdline);
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

        execute(lightCmdline);
    }

    private Path compileWix3(WixSource wixSource) throws IOException {
        Path wixObj = wixObjDir.toAbsolutePath().resolve(PathUtils.replaceSuffix(
                wixSource.path.getFileName(), ".wixobj"));

        List<String> cmdline = new ArrayList<>(List.of(
                toolset.getToolPath(WixTool.Candle3).toString(),
                "-nologo",
                wixSource.path.toString(),
                "-ext", "WixUtilExtension",
                "-arch", WixFragmentBuilder.is64Bit() ? "x64" : "x86",
                "-out", wixObj.toString()
        ));

        if (toolset.needFipsParameter()) {
            cmdline.add("-fips");
        }

        addWixVariablesToCommandLine(Stream.of(wixSource), cmdline::addAll);

        execute(cmdline);

        return wixObj;
    }

    private void execute(List<String> cmdline) throws IOException {
        Executor.of(new ProcessBuilder(cmdline).directory(workDir.toFile())).executeExpectSuccess();
    }

    private void addWixVariablesToCommandLine(Stream<WixSource> wixSources, Consumer<List<String>> sink) {
        sink.accept(wixSources.map(WixSource::variables).reduce(wixVariables, (a, b) -> {
            return new WixVariables().putAll(a).putAll(b);
        }).toWixCommandLine(toolset.getType()));
    }

    private record WixSource(Path path, WixVariables variables) {
        WixSource {
            Objects.requireNonNull(path);
            Objects.requireNonNull(variables);
        }

        WixSource copyWithPath(Path path) {
            return new WixSource(path, variables);
        }
    }

    private record MsiMutatorWithArgs(MsiMutator mutator, List<String> args) {
        MsiMutatorWithArgs {
            Objects.requireNonNull(mutator);
            Objects.requireNonNull(args);
        }

        void addToConfigRoot(Path configRoot) throws IOException {
            mutator.addToConfigRoot(configRoot);
        }

        void execute(Path configRoot, Path transientMsi) throws IOException {
            Executor.of("cscript", "//Nologo")
                    .args(PathUtils.normalizedAbsolutePathString(configRoot.resolve(mutator.pathInConfigRoot())))
                    .args(PathUtils.normalizedAbsolutePathString(transientMsi))
                    .args(args)
                    .executeExpectSuccess();
        }
    }

    private final WixToolset toolset;
    private final WixVariables wixVariables;
    private final List<String> lightOptions;
    private final Path wixObjDir;
    private final Path workDir;
    private final List<WixSource> sources;
    private final List<MsiMutatorWithArgs> msiMutators;
}

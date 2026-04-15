/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import jdk.jpackage.internal.model.Package;

final class Packager<T extends Package> {

    static <T extends Package> Packager<T> build() {
        return new Packager<>();
    }

    Packager<T> pkg(T v) {
        pkg = v;
        return this;
    }

    Packager<T> env(BuildEnv v) {
        env = v;
        return this;
    }

    Packager<T> outputDir(Path v) {
        outputDir = v;
        return this;
    }

    Packager<T> pipelineBuilderMutatorFactory(PipelineBuilderMutatorFactory<T> v) {
        pipelineBuilderMutatorFactory = v;
        return this;
    }

    T pkg() {
        return Objects.requireNonNull(pkg);
    }

    Path outputDir() {
        return Objects.requireNonNull(outputDir);
    }

    BuildEnv env() {
        return Objects.requireNonNull(env);
    }

    Path execute(PackagingPipeline.Builder pipelineBuilder) {
        Objects.requireNonNull(pkg);
        Objects.requireNonNull(env);
        Objects.requireNonNull(outputDir);

        IOUtils.writableOutputDir(outputDir);

        final var startupParameters = pipelineBuilder.createStartupParameters(env, pkg, outputDir);

        pipelineBuilderMutatorFactory().ifPresent(factory -> {
            factory.create(startupParameters.packagingEnv(), pkg, outputDir).accept(pipelineBuilder);
        });

        pipelineBuilder.create().execute(startupParameters);

        return outputDir.resolve(pkg.packageFileNameWithSuffix());
    }


    @FunctionalInterface
    interface PipelineBuilderMutatorFactory<T extends Package> {
        Consumer<PackagingPipeline.Builder> create(BuildEnv env, T pkg, Path outputDir);
    }


    private Optional<PipelineBuilderMutatorFactory<T>> pipelineBuilderMutatorFactory() {
        return Optional.ofNullable(pipelineBuilderMutatorFactory);
    }

    private T pkg;
    private BuildEnv env;
    private Path outputDir;
    private PipelineBuilderMutatorFactory<T> pipelineBuilderMutatorFactory;
}

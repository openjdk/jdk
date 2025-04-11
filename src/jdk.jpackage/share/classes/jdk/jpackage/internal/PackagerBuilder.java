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
import jdk.jpackage.internal.PackagingPipeline.StartupParameters;
import jdk.jpackage.internal.model.Package;
import jdk.jpackage.internal.model.PackagerException;

abstract class PackagerBuilder<T extends Package, U extends PackagerBuilder<T, U>> {

    U pkg(T v) {
        pkg = v;
        return thiz();
    }

    U env(BuildEnv v) {
        env = v;
        return thiz();
    }

    U outputDir(Path v) {
        outputDir = v;
        return thiz();
    }

    @SuppressWarnings("unchecked")
    private U thiz() {
        return (U)this;
    }

    protected abstract void configurePackagingPipeline(PackagingPipeline.Builder pipelineBuilder,
            StartupParameters startupParameters);

    Path execute(PackagingPipeline.Builder pipelineBuilder) throws PackagerException {
        Objects.requireNonNull(pkg);
        Objects.requireNonNull(env);
        Objects.requireNonNull(outputDir);

        final var startupParameters = pipelineBuilder.createStartupParameters(env, pkg, outputDir);

        configurePackagingPipeline(pipelineBuilder, startupParameters);

        pipelineBuilder.create().execute(startupParameters);

        return outputDir.resolve(pkg.packageFileNameWithSuffix());
    }

    protected T pkg;
    protected BuildEnv env;
    protected Path outputDir;
}

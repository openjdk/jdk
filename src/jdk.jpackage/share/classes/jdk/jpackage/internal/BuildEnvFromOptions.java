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

import static jdk.jpackage.internal.cli.StandardOption.PREDEFINED_APP_IMAGE;
import static jdk.jpackage.internal.cli.StandardOption.PREDEFINED_RUNTIME_IMAGE;
import static jdk.jpackage.internal.cli.StandardOption.RESOURCE_DIR;
import static jdk.jpackage.internal.cli.StandardOption.TEMP_ROOT;
import static jdk.jpackage.internal.cli.StandardOption.VERBOSE;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.Package;
import jdk.jpackage.internal.model.RuntimeLayout;

final class BuildEnvFromOptions {

    BuildEnvFromOptions() {
        predefinedRuntimeImageLayout(RuntimeLayout.DEFAULT);
    }

    BuildEnvFromOptions predefinedAppImageLayout(Function<Path, ApplicationLayout> v) {
        predefinedAppImageLayout = v;
        return this;
    }

    BuildEnvFromOptions predefinedAppImageLayout(ApplicationLayout v) {
        return predefinedAppImageLayout(path -> v.resolveAt(path));
    }

    BuildEnvFromOptions predefinedRuntimeImageLayout(Function<Path, RuntimeLayout> v) {
        predefinedRuntimeImageLayout = v;
        return this;
    }

    BuildEnvFromOptions predefinedRuntimeImageLayout(RuntimeLayout v) {
        return predefinedRuntimeImageLayout(path -> v.resolveAt(path));
    }

    BuildEnv create(Options options, Application app) {
        return create(options, app, Optional.empty());
    }

    BuildEnv create(Options options, Package pkg) {
        return create(options, pkg.app(), Optional.of(pkg));
    }

    private BuildEnv create(Options options, Application app, Optional<Package> pkg) {
        Objects.requireNonNull(options);
        Objects.requireNonNull(app);
        Objects.requireNonNull(pkg);
        Objects.requireNonNull(predefinedAppImageLayout);
        Objects.requireNonNull(predefinedRuntimeImageLayout);

        final var builder = new BuildEnvBuilder(TEMP_ROOT.getFrom(options));

        RESOURCE_DIR.ifPresentIn(options, builder::resourceDir);
        VERBOSE.ifPresentIn(options, builder::verbose);

        if (app.isRuntime()) {
            var path = PREDEFINED_RUNTIME_IMAGE.getFrom(options);
            builder.appImageLayout(predefinedRuntimeImageLayout.apply(path));
        } else if (PREDEFINED_APP_IMAGE.containsIn(options)) {
            var path = PREDEFINED_APP_IMAGE.getFrom(options);
            builder.appImageLayout(predefinedAppImageLayout.apply(path));
        } else {
            pkg.ifPresentOrElse(builder::appImageDirFor, () -> {
                builder.appImageDirFor(app);
            });
        }

        return builder.create();
    }

    private Function<Path, ApplicationLayout> predefinedAppImageLayout;
    private Function<Path, RuntimeLayout> predefinedRuntimeImageLayout;
}

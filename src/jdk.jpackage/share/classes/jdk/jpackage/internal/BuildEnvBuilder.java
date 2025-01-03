/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import jdk.jpackage.internal.model.ConfigException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import static jdk.jpackage.internal.I18N.buildConfigException;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.resources.ResourceLocator;

final class BuildEnvBuilder {

    BuildEnvBuilder(Path root) {
        this.root = Objects.requireNonNull(root);
    }

    BuildEnv create() throws ConfigException {
        Objects.requireNonNull(appImageDir);

        var exceptionBuilder = buildConfigException("ERR_BuildRootInvalid", root);
        if (!Files.exists(root)) {
        } else if (!Files.isDirectory(root)) {
            throw exceptionBuilder.create();
        } else {
            try (var rootDirContents = Files.list(root)) {
                if (rootDirContents.findAny().isPresent()) {
                    throw exceptionBuilder.create();
                }
            } catch (IOException ioe) {
                throw exceptionBuilder.cause(ioe).create();
            }
        }

        return BuildEnv.withAppImageDir(BuildEnv.create(root, Optional.ofNullable(resourceDir), ResourceLocator.class), appImageDir);
    }

    BuildEnvBuilder resourceDir(Path v) {
        resourceDir = v;
        return this;
    }

    BuildEnvBuilder appImageDir(Path v) {
        appImageDir = v;
        return this;
    }

    BuildEnvBuilder appImageDirFor(Application app) {
        appImageDir = root.resolve("image").resolve(app.appImageDirName());
        return this;
    }

    private Path appImageDir;
    private Path resourceDir;

    private final Path root;
}

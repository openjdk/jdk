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
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;
import static java.util.stream.Collectors.toSet;
import jdk.jpackage.internal.Codesign.CodesignException;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.util.function.ExceptionBox;
import static jdk.jpackage.internal.util.function.ThrowingConsumer.toConsumer;


final class AppImageSigner {

    static Consumer<Path> createSigner(Application app, SigningConfig signingCfg) {
        return toConsumer(appImage -> {
            try {
                new AppImageSigner(app, signingCfg).accept(appImage);
            } catch (CodesignException ex) {
                throw Codesign.handleCodesignException(app, ex);
            } catch (ExceptionBox ex) {
                if (ex.getCause() instanceof CodesignException codesignEx) {
                    Codesign.handleCodesignException(app, codesignEx);
                }
                throw ex;
            }
        });
    }

    static Consumer<Path> createUnsigner(Application app) {
        return toConsumer(new AppImageSigner(app)::accept);
    }

    void accept(Path appImage) throws IOException, CodesignException {
        var appLayout = ApplicationLayoutUtils.PLATFORM_APPLICATION_LAYOUT.resolveAt(appImage);

        try (var content = Files.walk(appImage)) {
            var launchersDir = appLayout.launchersDirectory();
            content.filter(path -> {
                return testDoSign(launchersDir, path);
            }).forEachOrdered(toConsumer(path -> {
                final var origPerms = ensureCanWrite(path);
                try {
                    unsign(path);
                    sign(path);
                } finally {
                    if (origPerms != null) {
                        Files.setPosixFilePermissions(path, origPerms);
                    }
                }
            }));
        }

        codesignDir.accept(appLayout.runtimeDirectory());

        var frameworkPath = appImage.resolve("Contents/Frameworks");
        if (Files.isDirectory(frameworkPath)) {
            try (var content = Files.list(frameworkPath)) {
                content.forEach(toConsumer(path -> {
                    codesignDir.accept(path);
                }));
            }
        }

        // sign the app itself
        codesignDir.accept(appImage);
    }

    private static Set<PosixFilePermission> ensureCanWrite(Path path) throws IOException {
        final var origPerms = Files.getPosixFilePermissions(path);
        if (origPerms.contains(PosixFilePermission.OWNER_WRITE)) {
            return null;
        } else {
            var pfp = EnumSet.copyOf(origPerms);
            pfp.add(PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(path, pfp);
            return origPerms;
        }
    }

    private boolean testDoSign(Path launchersDir, Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }

        if (!Files.isExecutable(path) && !path.getFileName().toString().endsWith(".dylib")) {
            return false;
        }

        if (path.toString().contains("dylib.dSYM/Contents")) {
            return false;
        }

        if (path.getParent().equals(launchersDir) && launcherExecutables.contains(path.getFileName().toString())) {
            // Don't sign launchers
            return false;
        }

        return true;
    }

    private static void unsign(Path path) throws IOException {
        // run quietly
        Executor.of("/usr/bin/codesign", "--remove-signature", path.toString())
                .setQuiet(true)
                .executeExpectSuccess();
    }

    private void sign(Path path) {
        var codesign = Files.isExecutable(path) ? codesignExecutableFile : codesignOtherFile;
        codesign.accept(path);
    }

    private AppImageSigner(Application app) {
        launcherExecutables = app.launchers().stream().map(
                Launcher::executableNameWithSuffix).collect(toSet());
        Consumer<Path> nop = path -> {};
        codesignExecutableFile = nop;
        codesignOtherFile = nop;
        codesignDir = nop;
    }

    private AppImageSigner(Application app, SigningConfig signingCfg) {
        launcherExecutables = app.launchers().stream().map(
                Launcher::executableNameWithSuffix).collect(toSet());

        var signingCfgWithoutEntitlements = SigningConfig.build(signingCfg).entitlements(null).create();

        codesignExecutableFile = Codesign.build(signingCfg).quiet(true).create().asConsumer();
        codesignOtherFile = Codesign.build(signingCfgWithoutEntitlements).quiet(true).create().asConsumer();
        codesignDir = Codesign.build(signingCfgWithoutEntitlements).force(true).create().asConsumer();
    }

    private final Set<String> launcherExecutables;
    private final Consumer<Path> codesignExecutableFile;
    private final Consumer<Path> codesignOtherFile;
    private final Consumer<Path> codesignDir;
}

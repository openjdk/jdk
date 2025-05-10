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

import static java.util.stream.Collectors.joining;
import static jdk.jpackage.internal.util.function.ThrowingConsumer.toConsumer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import jdk.jpackage.internal.Codesign.CodesignException;
import jdk.jpackage.internal.model.Application;
import jdk.jpackage.internal.model.ApplicationLayout;
import jdk.jpackage.internal.model.Launcher;
import jdk.jpackage.internal.model.MacApplication;
import jdk.jpackage.internal.util.PathUtils;
import jdk.jpackage.internal.util.function.ExceptionBox;


final class AppImageSigner {

    static Consumer<Path> createSigner(MacApplication app, CodesignConfig signingCfg) {
        return toConsumer(appImage -> {
            try {
                new AppImageSigner(Codesigners.create(signingCfg)).sign(app, appImage);
            } catch (CodesignException ex) {
                throw handleCodesignException(app, ex);
            } catch (ExceptionBox ex) {
                if (ex.getCause() instanceof CodesignException codesignEx) {
                    handleCodesignException(app, codesignEx);
                }
                throw ex;
            }
        });
    }

    private static final class SignFilter implements Predicate<Path> {

        SignFilter(Application app, Path appImage) {
            Objects.requireNonNull(appImage);

            // Don't explicitly sign main launcher. It will be implicitly signed when the bundle is signed.
            otherExcludePaths = app.asApplicationLayout().map(appLayout -> {
                return appLayout.resolveAt(appImage);
            }).map(ApplicationLayout::launchersDirectory).flatMap(launchersDir -> {
                return app.mainLauncher().map(Launcher::executableNameWithSuffix).map(launchersDir::resolve);
            }).map(Set::of).orElseGet(Set::of);
        }

        @Override
        public boolean test(Path path) {
            if (!Files.isRegularFile(path) || otherExcludePaths.contains(path)) {
                return false;
            }

            if (Files.isExecutable(path) || path.getFileName().toString().endsWith(".dylib")) {
                if (path.toString().contains("dylib.dSYM/Contents")) {
                    return false;
                }

                return true;
            }

            return false;
        }

        private final Set<Path> otherExcludePaths;
    }

    private void sign(MacApplication app, Path appImage) throws CodesignException, IOException {

        final var fileFilter = new SignFilter(app, appImage);

        try (var content = Files.walk(appImage)) {
            content.filter(fileFilter).forEach(toConsumer(path -> {
                final var origPerms = ensureCanWrite(path);
                try {
                    unsign(path);
                    sign(path);
                } finally {
                    if (!origPerms.isEmpty()) {
                        Files.setPosixFilePermissions(path, origPerms);
                    }
                }
            }));
        }

        // Sign runtime root directory if present
        app.asApplicationLayout().map(appLayout -> {
            return appLayout.resolveAt(appImage);
        }).map(MacApplicationLayout.class::cast).map(MacApplicationLayout::runtimeRootDirectory).ifPresent(codesigners);

        final var frameworkPath = appImage.resolve("Contents/Frameworks");
        if (Files.isDirectory(frameworkPath)) {
            try (var content = Files.list(frameworkPath)) {
                content.forEach(toConsumer(path -> {
                    codesigners.codesignDir().accept(path);
                }));
            }
        }

        // Sign the app image itself
        codesigners.accept(appImage);
    }

    private static Set<PosixFilePermission> ensureCanWrite(Path path) {
        try {
            final var origPerms = Files.getPosixFilePermissions(path);
            if (origPerms.contains(PosixFilePermission.OWNER_WRITE)) {
                return Set.of();
            } else {
                final var newPerms = EnumSet.copyOf(origPerms);
                newPerms.add(PosixFilePermission.OWNER_WRITE);
                Files.setPosixFilePermissions(path, newPerms);
                return origPerms;
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static CodesignException handleCodesignException(MacApplication app, CodesignException ex) {
        // Log output of "codesign" in case of error. It should help
        // user to diagnose issues when using --mac-app-image-sign-identity.
        // In addition add possible reason for failure. For example
        // "--app-content" can fail "codesign".

        if (!app.contentDirs().isEmpty()) {
            Log.info(I18N.getString("message.codesign.failed.reason.app.content"));
        }

        // Signing might not work without Xcode with command line
        // developer tools. Show user if Xcode is missing as possible
        // reason.
        if (!isXcodeDevToolsInstalled()) {
            Log.info(I18N.getString("message.codesign.failed.reason.xcode.tools"));
        }

        // Log "codesign" output
        Log.info(I18N.format("error.tool.failed.with.output", "codesign"));
        Log.info(Stream.of(ex.getOutput()).collect(joining("\n")).strip());

        return ex;
    }

    private static boolean isXcodeDevToolsInstalled() {
        try {
            return Executor.of("/usr/bin/xcrun", "--help").setQuiet(true).execute() == 0;
        } catch (IOException ex) {
            return false;
        }
    }

    private static void unsign(Path path) throws IOException {
        // run quietly
        Executor.of("/usr/bin/codesign", "--remove-signature", path.toString())
                .setQuiet(true)
                .executeExpectSuccess();
    }

    private void sign(Path path) {
        codesigners.accept(path);
    }

    private AppImageSigner(Codesigners codesigners) {
        this.codesigners = Objects.requireNonNull(codesigners);
    }

    private record Codesigners(Consumer<Path> codesignFile, Consumer<Path> codesignExecutableFile, Consumer<Path> codesignDir) implements Consumer<Path> {
        Codesigners {
            Objects.requireNonNull(codesignFile);
            Objects.requireNonNull(codesignExecutableFile);
            Objects.requireNonNull(codesignDir);
        }

        @Override
        public void accept(Path path) {
            findCodesigner(path).orElseThrow(() -> {
                return new IllegalArgumentException(String.format("No codesigner for %s path", PathUtils.normalizedAbsolutePathString(path)));
            }).accept(path);
        }

        private Optional<Consumer<Path>> findCodesigner(Path path) {
            if (Files.isDirectory(path)) {
                return Optional.of(codesignDir);
            } else if (Files.isRegularFile(path)) {
                if (Files.isExecutable(path)) {
                    return Optional.of(codesignExecutableFile);
                } else {
                    return Optional.of(codesignFile);
                }
            }
            return Optional.empty();
        }

        static Codesigners create(CodesignConfig signingCfg) {
            final var signingCfgWithoutEntitlements = CodesignConfig.build().from(signingCfg).entitlements(null).create();

            final var codesignExecutableFile = Codesign.build(signingCfg::toCodesignArgs).quiet(true).create().asConsumer();
            final var codesignFile = Codesign.build(signingCfgWithoutEntitlements::toCodesignArgs).quiet(true).create().asConsumer();
            final var codesignDir = Codesign.build(signingCfgWithoutEntitlements::toCodesignArgs).force(true).create().asConsumer();

            return new Codesigners(codesignFile, codesignExecutableFile, codesignDir);
        }
    }

    private final Codesigners codesigners;
}

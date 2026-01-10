/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.jpackage.internal.MacFromOptions.createMacApplication;
import static jdk.jpackage.internal.MacPackagingPipeline.APPLICATION_LAYOUT;
import static jdk.jpackage.internal.MacPackagingPipeline.createSignAppImagePackage;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.CREATE_MAC_APP_IMAGE;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.CREATE_MAC_DMG;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.CREATE_MAC_PKG;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.SIGN_MAC_APP_IMAGE;

import java.util.Optional;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.model.MacPackage;
import jdk.jpackage.internal.model.Package;

public class MacBundlingEnvironment extends DefaultBundlingEnvironment {

    public MacBundlingEnvironment() {
        super(build()
                .defaultOperation(CREATE_MAC_DMG)
                .bundler(SIGN_MAC_APP_IMAGE, MacBundlingEnvironment::signAppImage)
                .bundler(CREATE_MAC_APP_IMAGE, MacBundlingEnvironment::createAppImage)
                .bundler(CREATE_MAC_DMG, MacDmgSystemEnvironment::create, MacBundlingEnvironment::createDmdPackage)
                .bundler(CREATE_MAC_PKG, MacBundlingEnvironment::createPkgPackage));
    }

    private static void createDmdPackage(Options options, MacDmgSystemEnvironment sysEnv) {
        createNativePackage(options,
                MacFromOptions.createMacDmgPackage(options),
                buildEnv()::create,
                MacBundlingEnvironment::buildPipeline,
                (env, pkg, outputDir) -> {
                    Log.verbose(I18N.format("message.building-dmg", pkg.app().name()));
                    return new MacDmgPackager(env, pkg, outputDir, sysEnv);
                });
    }

    private static void createPkgPackage(Options options) {
        createNativePackage(options,
                MacFromOptions.createMacPkgPackage(options),
                buildEnv()::create,
                MacBundlingEnvironment::buildPipeline,
                (env, pkg, outputDir) -> {
                    Log.verbose(I18N.format("message.building-pkg", pkg.app().name()));
                    return new MacPkgPackager(env, pkg, outputDir);
                });
    }

    private static void signAppImage(Options options) {

        final var app = createMacApplication(options);

        final var env = buildEnv().create(options, app);

        final var pkg = createSignAppImagePackage(app, env);

        buildPipeline(pkg).create().execute(env, pkg, env.appImageDir());
    }

    private static void createAppImage(Options options) {

        final var app = createMacApplication(options);

        createApplicationImage(options, app, MacPackagingPipeline.build(Optional.empty()));
    }

    private static PackagingPipeline.Builder buildPipeline(Package pkg) {
        return MacPackagingPipeline.build(Optional.of(pkg));
    }

    private static BuildEnvFromOptions buildEnv() {
        return new BuildEnvFromOptions()
                .predefinedAppImageLayout(APPLICATION_LAYOUT)
                .predefinedRuntimeImageLayout(MacPackage::guessRuntimeLayout);
    }
}

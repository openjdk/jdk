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

import static jdk.jpackage.internal.WinFromOptions.createWinApplication;
import static jdk.jpackage.internal.WinPackagingPipeline.APPLICATION_LAYOUT;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.CREATE_WIN_APP_IMAGE;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.CREATE_WIN_EXE;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.CREATE_WIN_MSI;

import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.util.Result;

public class WinBundlingEnvironment extends DefaultBundlingEnvironment {

    public WinBundlingEnvironment() {
        super(build()
                .defaultOperation(CREATE_WIN_EXE)
                .bundler(CREATE_WIN_APP_IMAGE, WinBundlingEnvironment::createAppImage)
                .bundler(CREATE_WIN_EXE, LazyLoad::sysEnv, WinBundlingEnvironment::createExePackage)
                .bundler(CREATE_WIN_MSI, LazyLoad::sysEnv, WinBundlingEnvironment::createMsiPackage));
    }

    private static void createMsiPackage(Options options, WinSystemEnvironment sysEnv) {

        createNativePackage(options,
                WinFromOptions.createWinMsiPackage(options),
                buildEnv()::create,
                WinPackagingPipeline.build(),
                (env, pkg, outputDir) -> {

                    traceWixToolset(sysEnv);

                    return new WinMsiPackager(env, pkg, outputDir, sysEnv);
                });
    }

    private static void createExePackage(Options options, WinSystemEnvironment sysEnv) {

        createNativePackage(options,
                WinFromOptions.createWinExePackage(options),
                buildEnv()::create,
                WinPackagingPipeline.build(),
                (env, pkg, outputDir) -> {

                    traceWixToolset(sysEnv);

                    final var msiOutputDir = env.buildRoot().resolve("msi");

                    var msiPackager = new WinMsiPackager(env, pkg.msiPackage(),
                            msiOutputDir, sysEnv);
                    var exePackager = new WinExePackager(env, pkg, outputDir, msiOutputDir);

                    return msiPackager.andThen(exePackager);
                });
    }

    private static void createAppImage(Options options) {

        final var app = createWinApplication(options);

        createApplicationImage(options, app, WinPackagingPipeline.build());
    }

    private static BuildEnvFromOptions buildEnv() {
        return new BuildEnvFromOptions().predefinedAppImageLayout(APPLICATION_LAYOUT);
    }

    private static void traceWixToolset(WinSystemEnvironment sysEnv) {
        final var wixToolset = sysEnv.wixToolset();

        for (var tool : wixToolset.getType().getTools()) {
            Log.verbose(I18N.format("message.tool-version",
                    wixToolset.getToolPath(tool).getFileName(),
                    wixToolset.getVersion()));
        }
    }

    private static final class LazyLoad {

        static Result<WinSystemEnvironment> sysEnv() {
            return SYS_ENV;
        }

        private static final Result<WinSystemEnvironment> SYS_ENV = WinSystemEnvironment.create();
    }
}

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

import static java.util.stream.Collectors.toMap;
import static jdk.jpackage.internal.LinuxFromOptions.createLinuxApplication;
import static jdk.jpackage.internal.LinuxPackagingPipeline.APPLICATION_LAYOUT;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.CREATE_LINUX_APP_IMAGE;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.CREATE_LINUX_DEB;
import static jdk.jpackage.internal.cli.StandardBundlingOperation.CREATE_LINUX_RPM;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.cli.StandardBundlingOperation;
import jdk.jpackage.internal.model.BundlingOperationDescriptor;
import jdk.jpackage.internal.model.LinuxPackage;
import jdk.jpackage.internal.model.PackageType;
import jdk.jpackage.internal.util.Result;

public class LinuxBundlingEnvironment extends DefaultBundlingEnvironment {

    public LinuxBundlingEnvironment() {
        super(build()
                .defaultOperation(() -> {
                    return LazyLoad.SYS_ENV.value().map(LinuxSystemEnvironment::nativePackageType).map(DESCRIPTORS::get);
                })
                .bundler(CREATE_LINUX_APP_IMAGE, LinuxBundlingEnvironment::createAppImage)
                .bundler(CREATE_LINUX_DEB, LazyLoad::debSysEnv, LinuxBundlingEnvironment::createDebPackage)
                .bundler(CREATE_LINUX_RPM, LazyLoad::rpmSysEnv, LinuxBundlingEnvironment::createRpmPackage));
    }

    private static void createDebPackage(Options options, LinuxDebSystemEnvironment sysEnv) {

        createNativePackage(options,
                LinuxFromOptions.createLinuxDebPackage(options),
                buildEnv()::create,
                LinuxBundlingEnvironment::buildPipeline,
                (env, pkg, outputDir) -> {
                    return new LinuxDebPackager(env, pkg, outputDir, sysEnv);
                });
    }

    private static void createRpmPackage(Options options, LinuxRpmSystemEnvironment sysEnv) {

        createNativePackage(options,
                LinuxFromOptions.createLinuxRpmPackage(options),
                buildEnv()::create,
                LinuxBundlingEnvironment::buildPipeline,
                (env, pkg, outputDir) -> {
                    return new LinuxRpmPackager(env, pkg, outputDir, sysEnv);
                });
    }

    private static void createAppImage(Options options) {

        final var app = createLinuxApplication(options);

        createApplicationImage(options, app, LinuxPackagingPipeline.build(Optional.empty()));
    }

    private static PackagingPipeline.Builder buildPipeline(LinuxPackage pkg) {
        return LinuxPackagingPipeline.build(Optional.of(pkg));
    }

    private static BuildEnvFromOptions buildEnv() {
        return new BuildEnvFromOptions().predefinedAppImageLayout(APPLICATION_LAYOUT);
    }

    private static final class LazyLoad {

        static Result<LinuxDebSystemEnvironment> debSysEnv() {
            return DEB_SYS_ENV;
        }

        static Result<LinuxRpmSystemEnvironment> rpmSysEnv() {
            return RPM_SYS_ENV;
        }

        private static final Result<LinuxSystemEnvironment> SYS_ENV = LinuxSystemEnvironment.create();

        private static final Result<LinuxDebSystemEnvironment> DEB_SYS_ENV = LinuxDebSystemEnvironment.create(SYS_ENV);

        private static final Result<LinuxRpmSystemEnvironment> RPM_SYS_ENV = LinuxRpmSystemEnvironment.create(SYS_ENV);
    }

    private static final Map<PackageType, BundlingOperationDescriptor> DESCRIPTORS = Stream.of(
            CREATE_LINUX_DEB,
            CREATE_LINUX_RPM
    ).collect(toMap(StandardBundlingOperation::packageType, StandardBundlingOperation::descriptor));
}

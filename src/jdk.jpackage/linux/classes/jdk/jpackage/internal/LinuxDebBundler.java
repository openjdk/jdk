/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Map;
import java.util.Optional;
import jdk.jpackage.internal.model.LinuxDebPackage;
import jdk.jpackage.internal.model.PackagerException;
import jdk.jpackage.internal.model.StandardPackageType;
import jdk.jpackage.internal.util.Result;

public class LinuxDebBundler extends LinuxPackageBundler {

    public LinuxDebBundler() {
        super(LinuxFromParams.DEB_PACKAGE);
    }

    @Override
    public String getName() {
        return I18N.getString("deb.bundler.name");
    }

    @Override
    public String getID() {
        return "deb";
    }

    @Override
    public Path execute(Map<String, ? super Object> params, Path outputParentDir) throws PackagerException {

        var pkg = LinuxFromParams.DEB_PACKAGE.fetchFrom(params);

        return Packager.<LinuxDebPackage>build().outputDir(outputParentDir)
                .pkg(pkg)
                .env(BuildEnvFromParams.BUILD_ENV.fetchFrom(params))
                .pipelineBuilderMutatorFactory((env, _, outputDir) -> {
                    return new LinuxDebPackager(env, pkg, outputDir, sysEnv.orElseThrow());
                }).execute(LinuxPackagingPipeline.build(Optional.of(pkg)));
    }

    @Override
    protected Result<LinuxDebSystemEnvironment> sysEnv() {
        return sysEnv;
    }

    @Override
    public boolean isDefault() {
        return sysEnv.value()
                .map(LinuxSystemEnvironment::nativePackageType)
                .map(StandardPackageType.LINUX_DEB::equals)
                .orElse(false);
    }

    private final Result<LinuxDebSystemEnvironment> sysEnv = LinuxDebSystemEnvironment.create(SYS_ENV);
}

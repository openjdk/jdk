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

import static jdk.jpackage.internal.model.ConfigException.rethrowConfigException;

import java.nio.file.Path;
import java.util.Map;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.Package;
import jdk.jpackage.internal.model.PackagerException;
import jdk.jpackage.internal.model.WinMsiPackage;
import jdk.jpackage.internal.util.Result;

public class WinMsiBundler  extends AbstractBundler {

    public WinMsiBundler() {
    }

    @Override
    public String getName() {
        return I18N.getString("msi.bundler.name");
    }

    @Override
    public String getID() {
        return "msi";
    }

    @Override
    public String getBundleType() {
        return "INSTALLER";
    }

    @Override
    public boolean supported(boolean platformInstaller) {
        try {
            try {
                sysEnv.orElseThrow();
                return true;
            } catch (RuntimeException ex) {
                ConfigException.rethrowConfigException(ex);
            }
        } catch (ConfigException ce) {
            Log.error(ce.getMessage());
            if (ce.getAdvice() != null) {
                Log.error(ce.getAdvice());
            }
        } catch (Exception e) {
            Log.error(e.getMessage());
        }
        return false;
    }

    @Override
    public boolean isDefault() {
        return false;
    }

    @Override
    public boolean validate(Map<String, ? super Object> params) throws ConfigException {
        return validate(params, WinFromParams.MSI_PACKAGE);
    }

    boolean validate(Map<String, ? super Object> params, BundlerParamInfo<? extends Package> pkgParam)
            throws ConfigException {
        try {
            // Order is important!
            pkgParam.fetchFrom(params);
            BuildEnvFromParams.BUILD_ENV.fetchFrom(params);

            final var wixToolset = sysEnv.orElseThrow().wixToolset();

            for (var tool : wixToolset.getType().getTools()) {
                Log.verbose(I18N.format("message.tool-version",
                        wixToolset.getToolPath(tool).getFileName(),
                        wixToolset.getVersion()));
            }

            return true;
        } catch (RuntimeException re) {
            throw rethrowConfigException(re);
        }
    }

    @Override
    public Path execute(Map<String, ? super Object> params,
            Path outputParentDir) throws PackagerException {

        return Packager.<WinMsiPackage>build().outputDir(outputParentDir)
                .pkg(WinFromParams.MSI_PACKAGE.fetchFrom(params))
                .env(BuildEnvFromParams.BUILD_ENV.fetchFrom(params))
                .pipelineBuilderMutatorFactory((env, pkg, outputDir) -> {
                    return new WinMsiPackager(env, pkg, outputDir, sysEnv.orElseThrow());
                }).execute(WinPackagingPipeline.build());
    }

    final Result<WinSystemEnvironment> sysEnv = WinSystemEnvironment.create();
}

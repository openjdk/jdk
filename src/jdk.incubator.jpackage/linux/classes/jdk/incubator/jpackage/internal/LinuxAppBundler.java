/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.jpackage.internal;

import java.io.File;
import java.text.MessageFormat;
import java.util.*;

import static jdk.incubator.jpackage.internal.StandardBundlerParam.*;

public class LinuxAppBundler extends AbstractImageBundler {

    static final BundlerParamInfo<File> ICON_PNG =
            new StandardBundlerParam<>(
            "icon.png",
            File.class,
            params -> {
                File f = ICON.fetchFrom(params);
                if (f != null && !f.getName().toLowerCase().endsWith(".png")) {
                    Log.error(MessageFormat.format(
                            I18N.getString("message.icon-not-png"), f));
                    return null;
                }
                return f;
            },
            (s, p) -> new File(s));

    static final BundlerParamInfo<String> LINUX_INSTALL_DIR =
            new StandardBundlerParam<>(
            "linux-install-dir",
            String.class,
            params -> {
                 String dir = INSTALL_DIR.fetchFrom(params);
                 if (dir != null) {
                     if (dir.endsWith("/")) {
                         dir = dir.substring(0, dir.length()-1);
                     }
                     return dir;
                 }
                 return "/opt";
             },
            (s, p) -> s
    );

    static final BundlerParamInfo<String> LINUX_PACKAGE_DEPENDENCIES =
            new StandardBundlerParam<>(
            Arguments.CLIOptions.LINUX_PACKAGE_DEPENDENCIES.getId(),
            String.class,
            params -> {
                 return "";
             },
            (s, p) -> s
    );

    @Override
    public boolean validate(Map<String, ? super Object> params)
            throws ConfigException {
        try {
            Objects.requireNonNull(params);
            return doValidate(params);
        } catch (RuntimeException re) {
            if (re.getCause() instanceof ConfigException) {
                throw (ConfigException) re.getCause();
            } else {
                throw new ConfigException(re);
            }
        }
    }

    private boolean doValidate(Map<String, ? super Object> params)
            throws ConfigException {

        imageBundleValidation(params);

        return true;
    }

    File doBundle(Map<String, ? super Object> params, File outputDirectory,
            boolean dependentTask) throws PackagerException {
        if (StandardBundlerParam.isRuntimeInstaller(params)) {
            return PREDEFINED_RUNTIME_IMAGE.fetchFrom(params);
        } else {
            return doAppBundle(params, outputDirectory, dependentTask);
        }
    }

    private File doAppBundle(Map<String, ? super Object> params,
            File outputDirectory, boolean dependentTask)
            throws PackagerException {
        try {
            File rootDirectory = createRoot(params, outputDirectory,
                    dependentTask, APP_NAME.fetchFrom(params));
            AbstractAppImageBuilder appBuilder = new LinuxAppImageBuilder(
                    params, outputDirectory.toPath());
            if (PREDEFINED_RUNTIME_IMAGE.fetchFrom(params) == null ) {
                JLinkBundlerHelper.execute(params, appBuilder);
            } else {
                StandardBundlerParam.copyPredefinedRuntimeImage(
                        params, appBuilder);
            }
            return rootDirectory;
        } catch (PackagerException pe) {
            throw pe;
        } catch (Exception ex) {
            Log.verbose(ex);
            throw new PackagerException(ex);
        }
    }

    @Override
    public String getName() {
        return I18N.getString("app.bundler.name");
    }

    @Override
    public String getID() {
        return "linux.app";
    }

    @Override
    public String getBundleType() {
        return "IMAGE";
    }

    @Override
    public File execute(Map<String, ? super Object> params,
            File outputParentDir) throws PackagerException {
        return doBundle(params, outputParentDir, false);
    }

    @Override
    public boolean supported(boolean runtimeInstaller) {
        return true;
    }

    @Override
    public boolean isDefault() {
        return false;
    }

}

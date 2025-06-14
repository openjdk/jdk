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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import jdk.jpackage.internal.model.ConfigException;
import jdk.jpackage.internal.model.PackagerException;

public class MacDmgBundler extends MacBaseInstallerBundler {

    @Override
    public String getName() {
        return I18N.getString("dmg.bundler.name");
    }

    @Override
    public String getID() {
        return "dmg";
    }

    @Override
    public boolean validate(Map<String, ? super Object> params)
            throws ConfigException {
        try {
            Objects.requireNonNull(params);

            MacFromParams.DMG_PACKAGE.fetchFrom(params);

            //run basic validation to ensure requirements are met
            //we are not interested in return code, only possible exception
            validateAppImageAndBundeler(params);

            return true;
        } catch (RuntimeException re) {
            if (re.getCause() instanceof ConfigException) {
                throw (ConfigException) re.getCause();
            } else {
                throw new ConfigException(re);
            }
        }
    }

    @Override
    public Path execute(Map<String, ? super Object> params,
            Path outputParentDir) throws PackagerException {

        final var pkg = MacFromParams.DMG_PACKAGE.fetchFrom(params);
        var env = BuildEnvFromParams.BUILD_ENV.fetchFrom(params);

        final var packager = MacDmgPackager.build().outputDir(outputParentDir).pkg(pkg).env(env);

        MacDmgPackager.findSetFileUtility().ifPresent(packager::setFileUtility);

        return packager.execute();
    }

    @Override
    public boolean supported(boolean runtimeInstaller) {
        return isSupported();
    }

    public static final String[] required =
            {"/usr/bin/hdiutil", "/usr/bin/osascript"};
    public static boolean isSupported() {
        try {
            for (String s : required) {
                Path f = Path.of(s);
                if (!Files.exists(f) || !Files.isExecutable(f)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isDefault() {
        return true;
    }
}

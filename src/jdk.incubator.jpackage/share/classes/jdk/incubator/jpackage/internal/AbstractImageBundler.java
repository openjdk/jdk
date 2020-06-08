/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.text.MessageFormat;
import java.util.Map;
import java.io.File;

import static jdk.incubator.jpackage.internal.StandardBundlerParam.*;

/**
 * AbstractImageBundler
 *
 * This is the base class for each of the Application Image Bundlers.
 *
 * It contains methods and parameters common to all Image Bundlers.
 *
 * Application Image Bundlers are created in "create-app-image" mode,
 * or as an intermediate step in "create-installer" mode.
 *
 * The concrete implementations are in the platform specific Bundlers.
 */
public abstract class AbstractImageBundler extends AbstractBundler {

    protected void imageBundleValidation(Map<String, ? super Object> params)
             throws ConfigException {
        if (!params.containsKey(PREDEFINED_APP_IMAGE.getID())
                && !StandardBundlerParam.isRuntimeInstaller(params)) {
            StandardBundlerParam.LAUNCHER_DATA.fetchFrom(params);
        }
    }

    protected File createRoot(Map<String, ? super Object> params,
            File outputDirectory, boolean dependentTask, String name)
            throws PackagerException {

        IOUtils.writableOutputDir(outputDirectory.toPath());

        if (!dependentTask) {
            Log.verbose(MessageFormat.format(
                    I18N.getString("message.creating-app-bundle"),
                    name, outputDirectory.getAbsolutePath()));
        }

        // Create directory structure
        File rootDirectory = new File(outputDirectory, name);

        if (rootDirectory.exists()) {
            throw new PackagerException("error.root-exists",
                    rootDirectory.getAbsolutePath());
        }

        rootDirectory.mkdirs();

        return rootDirectory;
    }

}

/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 */
public class SplitRuntime {

    private String name;
    private String versionSpec;
    private String installDir;
    private String icon;
    private String searchPath;

    /*
     * The format of the split-runtime arg (like add-launcher) is:
     * --split-runtime <name>=<property file path>
     * Valid properties are "version", "icon", and "install-dir"
     * The version spec will default to the version being (with "+", meaning
     * any later update in this family.
     * The icon will default to jpackage default icon (not the app's icon).
     * The install-dir will default to the installer name given.
     *
     * Possible additional properties not implemented:
     * "search-path" - app or enterprise specific places to look for runtime
     */
    public SplitRuntime(String name, String propFile) {
        this.name = name;
        Properties props = new Properties();
        if (propFile != null) {
            try (Reader reader = Files.newBufferedReader(Path.of(propFile))) {
                props.load(reader);
            } catch (IOException e) {
                // ignore - treat as no properties
            }
            versionSpec = props.getProperty("version");
            icon = props.getProperty("icon");
            installDir = props.getProperty("install-dir");
            searchPath = props.getProperty("search-path");
        }
        // if property file doesn't exist, or just doesn't have the needed entries,
        // provide defaults:
        if (versionSpec == null) {
            String[] parts = System.getProperty("java.version").split("-", 2);
            versionSpec = parts[0] + "+";
        }
        if (installDir == null) {
            installDir = name;
        }
    }

    public String getName() {
        return name;
    }

    public String getVersionSpec() {
        return versionSpec;
    }

    public String getIcon() {
        return icon;
    }

    public String getInstallDir() {
        return installDir;
    }

    public String getSearchPath() {
        return searchPath;
    }

    public String toString() {
        return "SplitRuntime: " + name + " version spec: " + versionSpec +
                " icon: " + icon + " installDir: " + installDir +
                " searchPath: " + searchPath;
    }

}

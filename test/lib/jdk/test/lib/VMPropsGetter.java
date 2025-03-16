/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package jdk.test.lib;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class VMPropsGetter {
    private static final Properties vmProps = init();

    /**
     * @return the directory specified with 'jtreg -workDir:<dir>'
     */
    private static Path getJtregWorkDir() {
        Path pwd = Paths.get("").toAbsolutePath();
        Path dir = pwd;
        if (dir.getFileName().toString().matches("^[0-9]+$")) {
            dir = dir.getParent();
        }

        if (dir.getFileName().toString().equals("scratch")) {
            return dir.getParent();
        }
        throw new RuntimeException("The current directory '" + pwd +
                                   "' does not end with /scratch((/[0-9]+)|)");
    }

    static Properties init() {
        Path workDir = getJtregWorkDir();
        Path input = workDir.resolve("vm.properties");
        Properties props = new Properties();

        try (FileInputStream in = new FileInputStream(input.toFile())) {
            props.load(in);
            System.out.println("Loaded " + props.size() + " propertis from '" +
                               input + "'");
        } catch (IOException e) {
            e.printStackTrace(System.err);
            throw new RuntimeException("Failed to load properties from '"
                                       + input + "'", e);
        }

        return props;
    }

    public static String get(String key) {
        return vmProps.getProperty(key);
    }
}

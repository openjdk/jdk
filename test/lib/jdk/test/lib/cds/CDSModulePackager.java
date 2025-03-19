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
 *
 */

package jdk.test.lib.cds;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import jdk.test.lib.StringArrayUtils;
import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.util.FileUtils;
import jdk.test.lib.cds.CDSJarUtils.JarOptions;

public class CDSModulePackager {
    private Path srcRoot;

    // By default: ./test-modules in the scratch directory of the Jtreg test case.
    private Path outputDir;

    private String extraModulePaths;

    // Create modules where the source code is located in ${test.src}/${srcRoot},
    // where test.src is the directory that contains the current Jtreg test case.
    //
    // All modules will be packaged under ./${outputDir}/
    public CDSModulePackager(String srcRoot, String outputDir) {
        String testSrc = System.getProperty("test.src");
        this.srcRoot = Paths.get(testSrc, srcRoot);
        this.outputDir = Paths.get(outputDir);
    }

    public CDSModulePackager(String srcRoot) {
        this(srcRoot, "test-modules");
    }

    public CDSModulePackager(Path srcRoot, Path outputDir) {
        this.srcRoot = srcRoot;
        this.outputDir = outputDir;
    }

    public CDSModulePackager(Path srcRoot) {
        this(srcRoot, Paths.get("test-modules"));
    }

    public Path getOutputDir() {
        return outputDir;
    }

    public void addExtraModulePath(String... extra) {
        for (String s : extra) {
            if (extraModulePaths == null) {
                extraModulePaths = s;
            } else {
                extraModulePaths += File.pathSeparator + s;
            }
        }
    }

     public void addExtraModulePath(Path... extra) {
        for (Path p : extra) {
            if (extraModulePaths == null) {
                extraModulePaths = p.toString();
            } else {
                extraModulePaths += File.pathSeparator + p.toString();
            }
        }
    }

    public Path createModularJar(String moduleName) throws Exception {
       return createModularJarWithMainClass(moduleName, null, (JarOptions)null);
    }

    public Path createModularJar(String moduleName, String... javacOptions) throws Exception {
       return createModularJarWithMainClass(moduleName, null, (JarOptions)null, javacOptions);
    }

    public Path createModularJarWithMainClass(String moduleName, String mainClass, String... javacOptions) throws Exception {
        return createModularJarWithMainClass(moduleName, mainClass, (JarOptions)null, javacOptions);
    }

    // Compile all files under ${this.srcRoot}/${moduleName}.
    public Path createModularJarWithMainClass(String moduleName, String mainClass, JarOptions jarOptions, String... javacOptions) throws Exception {
        Path src = srcRoot.resolve(moduleName);

        // We always include the outputDir in the --module-path, so that you can compile new modules
        // that are dependent on modules already inside the outputDir.
        String modulePath = outputDir.toString();
        if (extraModulePaths != null) {
            modulePath += File.pathSeparator + extraModulePaths;
        }
        if (javacOptions == null) {            
            javacOptions = new String[] {"--module-path", modulePath};
        } else {
            javacOptions = StringArrayUtils.concat(javacOptions, "--module-path", modulePath);
        }

        if (mainClass != null) {
            if (jarOptions == null) {
                jarOptions = JarOptions.of();
            }
            jarOptions.setMainClass(mainClass);
        }

        Path jarFile = outputDir.resolve(moduleName + ".jar");
        CDSJarUtils.buildFromSourceDirectory(jarFile.toString(), src.toString(), jarOptions, javacOptions);

        return jarFile;
    }
}

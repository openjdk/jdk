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

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GetWorkspaceFolders {
    private static final Set<String> LANGTOOLS_MODULES = Set.of(
            "java.compiler", "jdk.compiler", "jdk.javadoc", "jdk.jshell"
    );

    public static void main(String... args) {
        String topDir = args[0];
        String outDir = args[1];
        String modulesList = args[2].strip();

        if (!modulesList.isEmpty()) {
            Set<String> modules = new HashSet<>(List.of(modulesList.split(" +")));

            if (modules.stream().anyMatch(LANGTOOLS_MODULES::contains)) {
                modules.add("java.compiler");
            }

            boolean hasJavac = modules.stream()
                                      .anyMatch(LANGTOOLS_MODULES::contains);
            boolean onlyHasJavac = modules.stream()
                                          .allMatch(LANGTOOLS_MODULES::contains);

            String comma = "";

            for (String module : modules) {
                comma = generateFolder("Sources of " + module, topDir + "/src/" + module, comma);
            }
            for (String module : modules) {
                comma = generateFolder("Generated sources of " + module, outDir + "/support/gensrc/" + module, comma);
            }
            if (!onlyHasJavac) {
                comma = generateFolder("JDK tests", topDir + "/test/jdk", comma);
            }
            if (hasJavac) {
                comma = generateFolder("langtools tests", topDir + "/test/langtools", comma);
            }
        } else {
            System.out.println("""
                               {
                                       "name": "Source root",
                                       "path": "{{TOPDIR}}"
                               },
                               {
                                       "name": "Build artifacts",
                                       "path": "{{OUTPUTDIR}}"
                               }
                               """.replace("{{TOPDIR}}", topDir)
                                  .replace("{{OUTPUTDIR}}", outDir));
        }
    }

    private static String generateFolder(String folderName, String folderPath, String comma) {
        File folderPathFile = new File(folderPath);

        if (!folderPathFile.isDirectory()) {
            //TODO: non-existing folders break indexing - should be investigated
            return comma;
        }

        System.out.println(comma);
        System.out.println("{");
        System.out.println("\"name\": \"" + folderName + "\",");
        System.out.println("\"path\": \"" + folderPath + "\""); //TODO: Windows?
        System.out.println("}");

        return ",\n";
    }
}
/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.tools.jaotc;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Stream;

final class Linker {

    private final Options options;
    private String objectFileName;
    private String libraryFileName;
    private String linkerCmd;

    String objFile() {
        return objectFileName;
    }

    String libFile() {
        return libraryFileName;
    }

    Linker(Main main) throws Exception {
        this.options = main.options;
        String name = options.outputName;
        objectFileName = name;
        libraryFileName = name;

        if (options.linkerpath != null && !(new File(options.linkerpath).exists())) {
            throw new InternalError("Invalid linker path: " + options.linkerpath);
        }
        String linkerPath;
        String linkerCheck;

        switch (options.osName) {
            case "Linux":
                if (name.endsWith(".so")) {
                    objectFileName = name.substring(0, name.length() - ".so".length());
                }
                linkerPath = (options.linkerpath != null) ? options.linkerpath : "ld";
                linkerCmd = linkerPath + " -shared -z noexecstack -o " + libraryFileName + " " + objectFileName;
                linkerCheck = linkerPath + " -v";
                break;
            case "SunOS":
                if (name.endsWith(".so")) {
                    objectFileName = name.substring(0, name.length() - ".so".length());
                }
                objectFileName = objectFileName + ".o";
                linkerPath = (options.linkerpath != null) ? options.linkerpath : "ld";
                linkerCmd = linkerPath + " -shared -o " + libraryFileName + " " + objectFileName;
                linkerCheck = linkerPath + " -V";
                break;
            case "Mac OS X":
                if (name.endsWith(".dylib")) {
                    objectFileName = name.substring(0, name.length() - ".dylib".length());
                }
                objectFileName = objectFileName + ".o";
                linkerPath = (options.linkerpath != null) ? options.linkerpath : "ld";
                linkerCmd = linkerPath + " -dylib -o " + libraryFileName + " " + objectFileName;
                linkerCheck = linkerPath + " -v";
                break;
            default:
                if (options.osName.startsWith("Windows")) {
                    if (name.endsWith(".dll")) {
                        objectFileName = name.substring(0, name.length() - ".dll".length());
                    }
                    objectFileName = objectFileName + ".obj";
                    linkerPath = (options.linkerpath != null) ? options.linkerpath : getWindowsLinkPath();
                    if (linkerPath == null) {
                        throw new InternalError("Can't locate Microsoft Visual Studio amd64 link.exe");
                    }
                    linkerCmd = linkerPath + " /DLL /OPT:NOREF /NOLOGO /NOENTRY" + " /OUT:" + libraryFileName + " " + objectFileName;
                    linkerCheck = null; // link.exe presence is verified already
                    break;
                } else {
                    throw new InternalError("Unsupported platform: " + options.osName);
                }
        }

        // Check linker presence on platforms by printing its version
        if (linkerCheck != null) {
            Process p = Runtime.getRuntime().exec(linkerCheck);
            final int exitCode = p.waitFor();
            if (exitCode != 0) {
                InputStream stderr = p.getErrorStream();
                BufferedReader br = new BufferedReader(new InputStreamReader(stderr));
                Stream<String> lines = br.lines();
                StringBuilder sb = new StringBuilder();
                lines.iterator().forEachRemaining(e -> sb.append(e));
                throw new InternalError(sb.toString());
            }
        }
    }

    void link() throws Exception {
        Process p = Runtime.getRuntime().exec(linkerCmd);
        final int exitCode = p.waitFor();
        if (exitCode != 0) {
            InputStream stderr = p.getErrorStream();
            if (stderr.read() == -1) {
                stderr = p.getInputStream();
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(stderr));
            Stream<String> lines = br.lines();
            StringBuilder sb = new StringBuilder();
            lines.iterator().forEachRemaining(e -> sb.append(e));
            throw new InternalError(sb.toString());
        }
        File objFile = new File(objectFileName);
        if (objFile.exists()) {
            if (!objFile.delete()) {
                throw new InternalError("Failed to delete " + objectFileName + " file");
            }
        }
        // Make non-executable for all.
        File libFile = new File(libraryFileName);
        if (libFile.exists() && !options.osName.startsWith("Windows")) {
            if (!libFile.setExecutable(false, false)) {
                throw new InternalError("Failed to change attribute for " + libraryFileName + " file");
            }
        }

    }

    /**
     * Visual Studio supported versions Search Order is: VS2013, VS2015, VS2012
     */
    public enum VSVERSIONS {
        VS2013("VS120COMNTOOLS", "C:\\Program Files (x86)\\Microsoft Visual Studio 12.0\\VC\\bin\\amd64\\link.exe"),
        VS2015("VS140COMNTOOLS", "C:\\Program Files (x86)\\Microsoft Visual Studio 14.0\\VC\\bin\\amd64\\link.exe"),
        VS2012("VS110COMNTOOLS", "C:\\Program Files (x86)\\Microsoft Visual Studio 11.0\\VC\\bin\\amd64\\link.exe");

        private final String envvariable;
        private final String wkp;

        VSVERSIONS(String envvariable, String wellknownpath) {
            this.envvariable = envvariable;
            this.wkp = wellknownpath;
        }

        String EnvVariable() {
            return envvariable;
        }

        String WellKnownPath() {
            return wkp;
        }
    }

    /**
     * Search for Visual Studio link.exe Search Order is: VS2013, VS2015, VS2012
     */
    private static String getWindowsLinkPath() {
        String link = "\\VC\\bin\\amd64\\link.exe";

        /**
         * First try searching the paths pointed to by the VS environment variables.
         */
        for (VSVERSIONS vs : VSVERSIONS.values()) {
            String vspath = System.getenv(vs.EnvVariable());
            if (vspath != null) {
                File commonTools = new File(vspath);
                File vsRoot = commonTools.getParentFile().getParentFile();
                File linkPath = new File(vsRoot, link);
                if (linkPath.exists()) {
                    return linkPath.getPath();
                }
            }
        }

        /**
         * If we didn't find via the VS environment variables, try the well known paths
         */
        for (VSVERSIONS vs : VSVERSIONS.values()) {
            String wkp = vs.WellKnownPath();
            if (new File(wkp).exists()) {
                return wkp;
            }
        }

        return null;
    }

}

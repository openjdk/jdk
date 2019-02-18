/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;

/**
 * A helper class that retrieves the main class name for
 * a running Java process using the proc filesystem (procfs)
 */
public class ProcessHelper implements sun.tools.common.ProcessHelper {


    private static final String CMD_PREFIX = "cmd:";
    private static final ProcessHelper INSTANCE = new ProcessHelper();

    public static ProcessHelper getInstance() {
        return INSTANCE;
    }

    /**
     * Gets the main class name for the given Java process by parsing the
     * process command line.
     * @param pid - process ID (pid)
     * @return main class name or null if the process no longer exists or
     * was started with a native launcher (e.g. jcmd etc)
     */

    public String getMainClass(String pid) {
        String cmdLine = getCommandLine(pid);
        if (cmdLine == null) {
            return null;
        }
        if (cmdLine.startsWith(CMD_PREFIX)) {
            cmdLine = cmdLine.substring(CMD_PREFIX.length());
        }
        String[] parts = cmdLine.split(" ");
        String mainClass = null;

        if(parts.length == 0) {
            return null;
        }

        // Check the executable
        String[] executablePath = parts[0].split("/");
        if (executablePath.length > 0) {
            String binaryName = executablePath[executablePath.length - 1];
            if (!"java".equals(binaryName)) {
                // Skip the process if it is not started with java launcher
                return null;
            }
        }

        // If -jar option is used then read the main class name from the manifest file.
        // Otherwise, the main class name is either specified in -m or --module options or it
        // is the first part that is not a Java option (doesn't start with '-' and is not a
        // classpath or a module path).

        for (int i = 1; i < parts.length && mainClass == null; i++) {
            if (i < parts.length - 1) {
                // Check if the module is executed with explicitly specified main class
                if ((parts[i].equals("-m") || parts[i].equals("--module"))) {
                    return getMainClassFromModuleArg(parts[i + 1]);
                }
                // Check if the main class needs to be read from the manifest.mf in a JAR file
                if (parts[i].equals("-jar")) {
                    return getMainClassFromJar(parts[i + 1], pid);
                }
            }
            // If this is a classpath or a module path option then skip the next part
            // (the classpath or the module path itself)
            if (parts[i].equals("-cp") || parts[i].equals("-classpath") ||  parts[i].equals("--class-path") ||
                    parts[i].equals("-p") || parts[i].equals("--module-path")) {
                i++;
                continue;
            }
            // Skip all other Java options
            if (parts[i].startsWith("-")) {
                continue;
            }
            mainClass = parts[i];
        }
        return mainClass;

    }

    private String getMainClassFromModuleArg(String moduleArg) {
        int pos = moduleArg.lastIndexOf("/");
        return (pos > 0 && pos < moduleArg.length()-1) ? moduleArg.substring(pos + 1) : null;
    }

    private String getMainClassFromJar(String jar, String pid) {
        if (!jar.startsWith("/")) {
            String cwd = getCurrentWorkingDir(pid);
            if (cwd != null) {
                jar = cwd + "/" + jar;
            }
        }
        try (JarFile jarFile = new JarFile(jar)) {
            Manifest mf = jarFile.getManifest();
            if (mf != null) {
                Attributes mainAttributes = mf.getMainAttributes();
                return mainAttributes.getValue("Main-Class");
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    private static String getCurrentWorkingDir(String pid) {
        return ("/proc/" + pid + "/cwd");
    }

    private static String getCommandLine(String pid) {
        try (Stream<String> lines =
                     Files.lines(Paths.get("/proc/" + pid + "/cmdline"))) {
            return lines.map(x -> x.replaceAll("\0", " ")).findFirst().orElse(null);
        } catch (IOException | UncheckedIOException e) {
            return null;
        }
    }
}



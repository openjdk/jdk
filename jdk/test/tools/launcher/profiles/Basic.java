/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8003255
 * @compile -XDignore.symbol.file Basic.java Main.java Logging.java
 * @run main Basic
 * @summary Test the launcher checks the Profile attribute of executable JAR
 *     files. Also checks that libraries that specify the Profile attribute
 *     are not loaded if the runtime does not support the required profile.
 */

import java.io.*;
import java.util.jar.*;
import static java.util.jar.JarFile.MANIFEST_NAME;
import java.util.zip.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class Basic {

    static final String MANIFEST_DIR = "META-INF/";

    static final String JAVA_HOME = System.getProperty("java.home");
    static final String OS_NAME = System.getProperty("os.name");
    static final String OS_ARCH = System.getProperty("os.arch");

    static final String JAVA_CMD =
            OS_NAME.startsWith("Windows") ? "java.exe" : "java";

    static final boolean NEED_D64 =
            OS_NAME.equals("SunOS") &&
            (OS_ARCH.equals("sparcv9") || OS_ARCH.equals("amd64"));

    /**
     * Creates a JAR file with the given attributes and the given entries.
     * Class files are assumed to be in ${test.classes}. Note that this this
     * method cannot use the "jar" tool as it may not be present in the image.
     */
    static void createJarFile(String jarfile,
                              String mainAttributes,
                              String... entries)
        throws IOException
    {
        // create Manifest
        Manifest manifest = new Manifest();
        Attributes jarAttrs = manifest.getMainAttributes();
        jarAttrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (mainAttributes.length() > 0) {
            for (String attr: mainAttributes.split(",")) {
                String[] s = attr.split("=");
                jarAttrs.put(new Attributes.Name(s[0]), s[1]);
            }
        }

        try (OutputStream out = Files.newOutputStream(Paths.get(jarfile));
             ZipOutputStream zos = new JarOutputStream(out))
        {
            // add manifest directory and manifest file
            ZipEntry e = new JarEntry(MANIFEST_DIR);
            e.setTime(System.currentTimeMillis());
            e.setSize(0);
            e.setCrc(0);
            zos.putNextEntry(e);
            e = new ZipEntry(MANIFEST_NAME);
            e.setTime(System.currentTimeMillis());
            zos.putNextEntry(e);
            manifest.write(zos);
            zos.closeEntry();

            // entries in JAR file
            for (String entry: entries) {
                e = new JarEntry(entry);
                Path path;
                if (entry.endsWith(".class")) {
                    path = Paths.get(System.getProperty("test.classes"), entry);
                } else {
                    path = Paths.get(entry);
                }
                BasicFileAttributes attrs =
                    Files.readAttributes(path, BasicFileAttributes.class);
                e.setTime(attrs.lastModifiedTime().toMillis());
                if (attrs.size() == 0) {
                    e.setMethod(ZipEntry.STORED);
                    e.setSize(0);
                    e.setCrc(0);
                }
                zos.putNextEntry(e);
                if (attrs.isRegularFile())
                    Files.copy(path, zos);
                zos.closeEntry();
            }
        }
    }

    /**
     * Execute the given executable JAR file with the given arguments. This
     * method blocks until the launched VM terminates. Any output or error
     * message from the launched VM are printed to System.out. Returns the
     * exit value.
     */
    static int exec(String jf, String... args) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(Paths.get(JAVA_HOME, "bin", JAVA_CMD).toString());
        if (NEED_D64)
            sb.append(" -d64");
        sb.append(" -jar ");
        sb.append(Paths.get(jf).toAbsolutePath());
        for (String arg: args) {
            sb.append(' ');
            sb.append(arg);
        }
        String[] cmd = sb.toString().split(" ");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }
        try {
            return p.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException("Should not happen");
        }
    }

    static void checkRun(String jf, String... args) throws IOException {
        if (exec(jf) != 0)
            throw new RuntimeException(jf + " failed!!!");
    }

    static void checkRunFail(String jf, String... args) throws IOException {
        if (exec(jf) == 0)
            throw new RuntimeException(jf + " did not fail!!!");
        System.out.println("Failed as expected");
    }

    public static void main(String[] args) throws IOException {
        // ## replace this if there is a standard way to determine the profile
        String profile = sun.misc.Version.profileName();

        int thisProfile = 4;
        if ("compact1".equals(profile)) thisProfile = 1;
        if ("compact2".equals(profile)) thisProfile = 2;
        if ("compact3".equals(profile)) thisProfile = 3;

        // "library" JAR file used by the test
        createJarFile("Logging.jar", "", "Logging.class");

        // Executable JAR file without the Profile attribute
        if (thisProfile <= 3) {
            createJarFile("Main.jar",
                          "Main-Class=Main,Class-Path=Logging.jar",
                          "Main.class");
            checkRunFail("Main.jar");
        }

        // Executable JAR file with Profile attribute, Library JAR file without
        for (int p=1; p<=3; p++) {
            String attrs = "Main-Class=Main,Class-Path=Logging.jar" +
                 ",Profile=compact" + p;
            createJarFile("Main.jar", attrs,  "Main.class");
            if (p <= thisProfile) {
                checkRun("Main.jar");
            } else {
                checkRunFail("Main.jar");
            }
        }

        // Executable JAR file with Profile attribute that has invalid profile
        // name, including incorrect case.
        createJarFile("Main.jar",
                      "Main-Class=Main,Class-Path=Logging.jar,Profile=BadName",
                      "Main.class");
        checkRunFail("Main.jar");

        createJarFile("Main.jar",
                      "Main-Class=Main,Class-Path=Logging.jar,Profile=Compact1",
                      "Main.class");
        checkRunFail("Main.jar");

        // Executable JAR file and Librrary JAR file with Profile attribute
        createJarFile("Main.jar",
                      "Main-Class=Main,Class-Path=Logging.jar,Profile=compact1",
                      "Main.class");
        for (int p=1; p<=3; p++) {
            String attrs = "Profile=compact" + p;
            createJarFile("Logging.jar", attrs, "Logging.class");
            if (p <= thisProfile) {
                checkRun("Main.jar");
            } else {
                checkRunFail("Main.jar");
            }
        }

        // Executable JAR file and Library JAR with Profile attribute, value
        // of Profile not recognized
        createJarFile("Logging.jar", "Profile=BadName", "Logging.class");
        createJarFile("Main.jar",
                      "Main-Class=Main,Class-Path=Logging.jar,Profile=compact1",
                      "Main.class");
        checkRunFail("Main.jar");

        System.out.println("TEST PASSED.");
    }

}

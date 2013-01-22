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
 * @bug 8003256
 * @compile -XDignore.symbol.file VersionCheck.java
 * @run main VersionCheck
 * @summary Tests that "java -version" includes the name of the profile and that
 *     it matches the name in the release file
 */

import java.nio.file.*;
import java.io.*;
import java.util.Properties;

public class VersionCheck {

    static final String JAVA_HOME = System.getProperty("java.home");
    static final String OS_NAME = System.getProperty("os.name");
    static final String OS_ARCH = System.getProperty("os.arch");

    static final String JAVA_CMD =
            OS_NAME.startsWith("Windows") ? "java.exe" : "java";

    static final boolean NEED_D64 =
            OS_NAME.equals("SunOS") &&
            (OS_ARCH.equals("sparcv9") || OS_ARCH.equals("amd64"));

    /**
     * Returns {@code true} if the given class is present.
     */
    static boolean isPresent(String cn) {
        try {
            Class.forName(cn);
            return true;
        } catch (ClassNotFoundException ignore) {
            return false;
        }
    }

    /**
     * Determines the profile by checking whether specific classes are present.
     * Returns the empty string if this runtime does not appear to be a profile
     * of Java SE.
     */
    static String probeProfile() {
        if (isPresent("java.awt.Window"))
            return "";
        if (isPresent("java.lang.management.ManagementFactory"))
            return "compact3";
        if (isPresent("java.sql.DriverManager"))
            return "compact2";
        return "compact1";
    }

    /**
     * Execs java with the given parameters. The method blocks until the
     * process terminates. Returns a {@code ByteArrayOutputStream} with any
     * stdout or stderr from the process.
     */
    static ByteArrayOutputStream execJava(String... args)
        throws IOException
    {
        StringBuilder sb = new StringBuilder();
        sb.append(Paths.get(JAVA_HOME, "bin", JAVA_CMD).toString());
        if (NEED_D64)
            sb.append(" -d64");
        for (String arg: args) {
            sb.append(' ');
            sb.append(arg);
        }
        String[] cmd = sb.toString().split(" ");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        do {
            n = p.getInputStream().read(buf);
            if (n > 0)
               baos.write(buf, 0, n);
        } while (n > 0);
        try {
            int exitCode = p.waitFor();
            if (exitCode != 0)
                throw new RuntimeException("Exit code: " + exitCode);
        } catch (InterruptedException e) {
            throw new RuntimeException("Should not happen");
        }
        return baos;
    }

    public static void main(String[] args) throws IOException {
        String reported = sun.misc.Version.profileName();
        String probed = probeProfile();
        if (!reported.equals(probed)) {
            throw new RuntimeException("sun.misc.Version reports: " + reported
               + ", but probing reports: " + probed);
        }

        String profile = probed;
        boolean isFullJre = (profile.length() == 0);

        // check that java -version includes "profile compactN"
        String expected = "profile " + profile;
        System.out.println("Checking java -version ...");
        ByteArrayOutputStream baos = execJava("-version");
        ByteArrayInputStream bain = new ByteArrayInputStream(baos.toByteArray());
        BufferedReader reader = new BufferedReader(new InputStreamReader(bain));
        boolean found = false;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.contains(expected)) {
                found = true;
                break;
            }
        }
        if (found && isFullJre)
           throw new RuntimeException(expected + " found in java -version output");
        if (!found && !isFullJre)
            throw new RuntimeException("java -version did not include " + expected);

        // check that the profile name matches the release file
        System.out.println("Checking release file ...");
        Properties props = new Properties();

        Path home = Paths.get(JAVA_HOME);
        if (home.getFileName().toString().equals("jre"))
            home = home.getParent();
        Path release = home.resolve("release");
        try (InputStream in = Files.newInputStream(release)) {
            props.load(in);
        }
        String value = props.getProperty("JAVA_PROFILE");
        if (isFullJre) {
            if (value != null)
                throw new RuntimeException("JAVA_PROFILE should not be present");
        } else {
            if (value == null)
                throw new RuntimeException("JAVA_PROFILE not present in release file");
            if (!value.equals("\"" + profile + "\""))
                throw new RuntimeException("Unexpected value of JAVA_PROFILE: " + value);
        }

        System.out.println("Test passed.");
    }
}

/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jdk.testlibrary.ProcessTools;

/**
 * @test
 * @bug 6434402 8004926
 * @library /lib/testlibrary
 * @build jdk.testlibrary.ProcessTools
 * @build TestManager TestApplication CustomLauncherTest
 * @run main/othervm CustomLauncherTest
 * @author Jaroslav Bachorik
 */
public class CustomLauncherTest {
    private static final  String TEST_CLASSPATH = System.getProperty("test.class.path");
    private static final  String TEST_JDK = System.getProperty("test.jdk");

    private static final  String TEST_SRC = System.getProperty("test.src");
    private static final  String OSNAME = System.getProperty("os.name");
    private static final  String ARCH;
    private static final  String LIBARCH;

    static {
        // magic with os.arch
        String osarch = System.getProperty("os.arch");
        switch (osarch) {
            case "i386":
            case "i486":
            case "i586":
            case "i686":
            case "i786":
            case "i886":
            case "i986": {
                ARCH = "i586";
                break;
            }
            case "x86_64":
            case "amd64": {
                ARCH = "amd64";
                break;
            }
            case "sparc":
                ARCH = "sparcv9";
                break;
            default: {
                ARCH = osarch;
            }
        }
        LIBARCH = ARCH.equals("i586") ? "i386" : ARCH;
    }

    public static void main(String[] args) throws Exception {
        if (TEST_CLASSPATH == null || TEST_CLASSPATH.isEmpty()) {
            System.out.println("Test is designed to be run from jtreg only");
            return;
        }

        String PLATFORM = "";
        switch (OSNAME.toLowerCase()) {
            case "linux": {
                PLATFORM = "linux";
                break;
            }
            case "sunos": {
                PLATFORM = "solaris";
                break;
            }
            default: {
                System.out.println("Test not designed to run on this operating " +
                                   "system (" + OSNAME + "), skipping...");
                return;
            }
        }

        String LAUNCHER = TEST_SRC + File.separator + PLATFORM + "-" + ARCH +
                          File.separator + "launcher";

        final FileSystem FS = FileSystems.getDefault();
        Path launcherPath = FS.getPath(LAUNCHER);

        final boolean hasLauncher = Files.isRegularFile(launcherPath, LinkOption.NOFOLLOW_LINKS)&&
                                    Files.isReadable(launcherPath);
        if (!hasLauncher) {
            System.out.println("Launcher [" + LAUNCHER + "] does not exist. Skipping the test.");
            return;
        }

        Path libjvmPath = findLibjvm(FS);
        if (libjvmPath == null) {
            throw new Error("Unable to locate 'libjvm.so' in " + TEST_JDK);
        }

        Process serverPrc = null, clientPrc = null;

        final Set<PosixFilePermission> launcherOrigPerms =
            Files.getPosixFilePermissions(launcherPath, LinkOption.NOFOLLOW_LINKS);
        try {
            // It is impossible to store an executable file in the source control
            // We need to set the executable flag here
            if (!Files.isExecutable(launcherPath)) {
                Set<PosixFilePermission> perms = new HashSet<>(launcherOrigPerms);
                perms.add(PosixFilePermission.OWNER_EXECUTE);
                Files.setPosixFilePermissions(launcherPath, perms);
            }

            System.out.println("Starting custom launcher:");
            System.out.println("=========================");
            System.out.println("  launcher  : " + LAUNCHER);
            System.out.println("  libjvm    : " + libjvmPath.toString());
            System.out.println("  classpath : " + TEST_CLASSPATH);
            ProcessBuilder server = new ProcessBuilder(LAUNCHER, libjvmPath.toString(), TEST_CLASSPATH, "TestApplication");

            final AtomicReference<String> port = new AtomicReference<>();
            final AtomicReference<String> pid = new AtomicReference<>();

            serverPrc = ProcessTools.startProcess(
                "Launcher",
                server,
                (String line) -> {
                    if (line.startsWith("port:")) {
                         port.set(line.split("\\:")[1]);
                     } else  if (line.startsWith("pid:")) {
                         pid.set(line.split("\\:")[1]);
                     } else if (line.startsWith("waiting")) {
                         return true;
                     }
                     return false;
                },
                5,
                TimeUnit.SECONDS
            );

            System.out.println("Attaching test manager:");
            System.out.println("=========================");
            System.out.println("  PID           : " + pid.get());
            System.out.println("  shutdown port : " + port.get());

            ProcessBuilder client = ProcessTools.createJavaProcessBuilder(
                "-cp",
                TEST_CLASSPATH +
                    File.pathSeparator +
                    TEST_JDK +
                    File.separator +
                    "lib" +
                    File.separator +
                    "tools.jar",
                "TestManager",
                pid.get(),
                port.get(),
                "true"
            );

            clientPrc = ProcessTools.startProcess(
                "TestManager",
                client,
                (String line) -> line.startsWith("Starting TestManager for PID"),
                10,
                TimeUnit.SECONDS
            );

            int clientExitCode = clientPrc.waitFor();
            int serverExitCode = serverPrc.waitFor();

            if (clientExitCode != 0 || serverExitCode != 0) {
                throw new Error("Test failed");
            }
        } finally {
            // Let's restore the original launcher permissions
            Files.setPosixFilePermissions(launcherPath, launcherOrigPerms);
            if (clientPrc != null) {
                clientPrc.destroy();
                clientPrc.waitFor();
            }
            if (serverPrc != null) {
                serverPrc.destroy();
                serverPrc.waitFor();
            }
        }
    }

    private static Path findLibjvm(FileSystem FS) {
        Path libjvmPath = findLibjvm(FS.getPath(TEST_JDK, "jre", "lib", LIBARCH));
        if (libjvmPath == null) {
            libjvmPath = findLibjvm(FS.getPath(TEST_JDK, "lib", LIBARCH));
        }
        return libjvmPath;
    }

    private static Path findLibjvm(Path libPath) {
        // ARCH/libjvm.so -> ARCH/server/libjvm.so -> ARCH/client/libjvm.so
        Path libjvmPath = libPath.resolve("libjvm.so");
        if (isFileOk(libjvmPath)) {
            return libjvmPath;
        }
        libjvmPath = libPath.resolve("server/libjvm.so");
        if (isFileOk(libjvmPath)) {
            return libjvmPath;
        }
        libjvmPath = libPath.resolve("client/libjvm.so");
        if (isFileOk(libPath)) {
            return libjvmPath;
        }

        return null;
    }

    private static boolean isFileOk(Path path) {
        return Files.isRegularFile(path) && Files.isReadable(path);
    }
}

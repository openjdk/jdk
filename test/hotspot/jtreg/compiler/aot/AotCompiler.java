/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

package compiler.aot;

import jdk.test.lib.Platform;
import jdk.test.lib.artifacts.Artifact;
import jdk.test.lib.artifacts.ArtifactResolver;
import jdk.test.lib.artifacts.ArtifactResolverException;
import jdk.test.lib.process.OutputAnalyzer;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;

/**
 * A simple class calling AOT compiler over requested items
 */
public class AotCompiler {

    private final static String METHODS_LIST_FILENAME = "methodsList.txt";

    public static void main(String args[]) {
        String className = null;
        List<String> compileList = new ArrayList<>();
        String libName = null;
        List<String> extraopts = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-class":
                    className = args[++i];
                    break;
                case "-compile":
                    compileList.add("compileOnly " + args[++i]);
                    break;
                case "-libname":
                    libName = args[++i];
                    break;
                case "-extraopt":
                    extraopts.add(args[++i]);
                    break;
                default:
                    throw new Error("Unknown option: " + args[i]);
            }
        }
        extraopts.add("-classpath");
        extraopts.add(Utils.TEST_CLASS_PATH + File.pathSeparator + Utils.TEST_SRC);
        if (className != null && libName != null) {
            OutputAnalyzer oa = launchCompiler(libName, className, extraopts, compileList);
            oa.shouldHaveExitValue(0);
        } else {
            printUsage();
            throw new Error("Mandatory arguments aren't passed");
        }
    }

    public static OutputAnalyzer launchCompilerSimple(String... args) {
        return launchJaotc(Arrays.asList(args), null);
    }

    public static OutputAnalyzer launchCompiler(String libName, String item, List<String> extraopts,
            List<String> compList) {
        Path file = null;
        if (compList != null && !compList.isEmpty()) {
            file = Paths.get(METHODS_LIST_FILENAME);
            try {
                Files.write(file, compList, StandardOpenOption.CREATE);
            } catch (IOException e) {
                throw new Error("Couldn't write " + METHODS_LIST_FILENAME + " " + e, e);
            }
        }
        List<String> args = new ArrayList<>();
        args.add("--compile-with-assertions");
        args.add("--info");
        args.add("--output");
        args.add(libName);
        if (file != null) {
            args.add("--compile-commands");
            args.add(file.toString());
        }
        args.add("--class-name");
        args.add(item);
        String linker = resolveLinker();
        if (linker != null) {
            args.add("--linker-path");
            args.add(linker);
        }
        // Execute with asserts
        args.add("-J-ea");
        args.add("-J-esa");
        return launchJaotc(args, extraopts);
    }

    private static OutputAnalyzer launchJaotc(List<String> args, List<String> extraVmOpts) {
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jaotc");
        for (String vmOpt : Utils.getTestJavaOpts()) {
            launcher.addVMArg(vmOpt);
        }
        if (extraVmOpts != null) {
            for (String vmOpt : extraVmOpts) {
                launcher.addVMArg(vmOpt);
            }
        }
        for (String arg : args) {
            launcher.addToolArg(arg);
        }
        try {
            return ProcessTools.executeCommand(new ProcessBuilder(launcher.getCommand()).redirectErrorStream(true));
        } catch (Throwable e) {
            throw new Error("Can't start test process: " + e, e);
        }
    }

    public static void printUsage() {
        System.err.println("Usage: " + AotCompiler.class.getName()
                + " -class <class> -libname <.so name>"
                + " [-compile <compileItems>]* [-extraopt <java option>]*");
    }

    // runs ld -v (or ld -V on solaris) and check its exit code
    private static boolean checkLd(Path bin) {
        try {
            return 0 == ProcessTools.executeCommand(bin.toString(),
                                                    Platform.isSolaris() ? "-V" : "-v")
                                    .getExitValue();
        } catch (Throwable t) {
            // any errors mean ld doesn't work
            return false;
        }
    }

    public static String resolveLinker() {
        Path linker = null;
        // if non windows, 1st, check if PATH has ld
        if (!Platform.isWindows()) {
            String bin = "ld";
            for (String path : System.getenv("PATH").split(File.pathSeparator)) {
                Path ld = Paths.get(path).resolve("ld");
                if (Files.exists(ld)) {
                    // there is ld in PATH
                    if (checkLd(ld)) {
                        System.out.println("found working linker: " + ld);
                        // ld works, jaotc is supposed to find and use it
                        return null;
                    } else {
                        System.out.println("found broken linker: " + ld);
                        // ld exists in PATH, but doesn't work, have to use devkit
                        break;
                    }
                }
            }
        }
        // there is no ld in PATH, will use ld from devkit
        // artifacts are got from common/conf/jib-profiles.js
        try {
            if (Platform.isWindows()) {
                if (Platform.isX64()) {
                    @Artifact(organization = "jpg.infra.builddeps",
                            name = "devkit-windows_x64",
                            revision = "VS2017-15.5.5+1.0",
                            extension = "tar.gz")
                    class DevkitWindowsX64 { }
                    String artifactName = "jpg.infra.builddeps."
                            + "devkit-windows_x64-"
                            + "VS2017-15.5.5+1.0";
                    Path devkit = ArtifactResolver.resolve(DevkitWindowsX64.class)
                                                  .get(artifactName);
                    linker = devkit.resolve("VC")
                                   .resolve("bin")
                                   .resolve("x64")
                                   .resolve("link.exe");
                }
            } else if (Platform.isOSX()) {
                @Artifact(organization =  "jpg.infra.builddeps",
                        name = "devkit-macosx_x64",
                        revision = "Xcode6.3-MacOSX10.9+1.0",
                        extension = "tar.gz")
                class DevkitMacosx { }
                String artifactName = "jpg.infra.builddeps."
                        + "devkit-macosx_x64-"
                        + "Xcode6.3-MacOSX10.9+1.0";
                Path devkit = ArtifactResolver.resolve(DevkitMacosx.class)
                                              .get(artifactName);
                linker = devkit.resolve("Xcode.app")
                               .resolve("Contents")
                               .resolve("Developer")
                               .resolve("Toolchains")
                               .resolve("XcodeDefault.xctoolchain")
                               .resolve("usr")
                               .resolve("bin")
                               .resolve("ld");
            } else if (Platform.isSolaris()) {
                if (Platform.isSparc()) {
                    @Artifact(organization =  "jpg.infra.builddeps",
                            name = "devkit-solaris_sparcv9",
                            revision = "SS12u4-Solaris11u1+1.1",
                            extension = "tar.gz")
                    class DevkitSolarisSparc { }

                    String artifactName = "jpg.infra.builddeps."
                            + "devkit-solaris_sparcv9-"
                            + "SS12u4-Solaris11u1+1.1";
                    Path devkit = ArtifactResolver.resolve(DevkitSolarisSparc.class)
                                                  .get(artifactName);
                    linker = devkit.resolve("SS12u4-Solaris11u1")
                                   .resolve("gnu")
                                   .resolve("bin")
                                   .resolve("ld");
                } else if (Platform.isX64()) {
                    @Artifact(organization =  "jpg.infra.builddeps",
                            name = "devkit-solaris_x64",
                            revision = "SS12u4-Solaris11u1+1.0",
                            extension = "tar.gz")
                    class DevkitSolarisX64 { }

                    String artifactName = "jpg.infra.builddeps."
                            + "devkit-solaris_x64-"
                            + "SS12u4-Solaris11u1+1.0";
                    Path devkit = ArtifactResolver.resolve(DevkitSolarisX64.class)
                                                  .get(artifactName);
                    linker = devkit.resolve("SS12u4-Solaris11u1")
                                   .resolve("bin")
                                   .resolve("amd64")
                                   .resolve("ld");
                }
            } else if (Platform.isLinux()) {
                if (Platform.isAArch64()) {
                    @Artifact(organization = "jpg.infra.builddeps",
                            name = "devkit-linux_aarch64",
                            revision = "gcc-linaro-aarch64-linux-gnu-4.8-2013.11_linux+1.0",
                            extension = "tar.gz")
                    class DevkitLinuxAArch64 { }

                    String artifactName = "jpg.infra.builddeps."
                            + "devkit-linux_aarch64-"
                            + "gcc-linaro-aarch64-linux-gnu-4.8-2013.11_linux+1.0";
                    Path devkit = ArtifactResolver.resolve(DevkitLinuxAArch64.class)
                                                  .get(artifactName);
                    linker = devkit.resolve("aarch64-linux-gnu")
                                   .resolve("bin")
                                   .resolve("ld");
                } else if (Platform.isARM()) {
                    @Artifact(organization = "jpg.infra.builddeps",
                            name = "devkit-linux_arm",
                            revision = "gcc-linaro-arm-linux-gnueabihf-raspbian-2012.09-20120921_linux+1.0",
                            extension = "tar.gz")
                    class DevkitLinuxARM { }

                    String artifactName = "jpg.infra.builddeps."
                            + "devkit-linux_arm-"
                            + "gcc-linaro-arm-linux-gnueabihf-raspbian-2012.09-20120921_linux+1.0";
                    Path devkit = ArtifactResolver.resolve(DevkitLinuxARM.class)
                                                  .get(artifactName);
                    linker = devkit.resolve("arm-linux-gnueabihf")
                                   .resolve("bin")
                                   .resolve("ld");
                } else if (Platform.isX64()) {
                    @Artifact(organization = "jpg.infra.builddeps",
                            name = "devkit-linux_x64",
                            revision = "gcc7.3.0-OEL6.4+1.0",
                            extension = "tar.gz")
                    class DevkitLinuxX64 { }

                    String artifactName = "jpg.infra.builddeps."
                            + "devkit-linux_x64-"
                            + "gcc7.3.0-OEL6.4+1.0";
                    Path devkit = ArtifactResolver.resolve(DevkitLinuxX64.class)
                                                  .get(artifactName);
                    linker = devkit.resolve("bin")
                                   .resolve("ld");
                }
            }
        } catch (ArtifactResolverException e) {
            System.err.println("artifact resolution error: " + e);
            // let jaotc try to find linker
            return null;
        }
        if (linker != null) {
            return linker.toAbsolutePath().toString();
        }
        return null;
    }
}

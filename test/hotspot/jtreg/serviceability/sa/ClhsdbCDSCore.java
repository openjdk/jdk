/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8174994 8200613
 * @summary Test the clhsdb commands 'printmdo', 'printall', 'jstack' on a CDS enabled corefile.
 * @requires vm.cds
 * @requires vm.hasSA
 * @requires os.family != "windows"
 * @requires vm.flavor == "server"
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 * @run main/othervm/timeout=2400 -Xmx1g ClhsdbCDSCore
 */

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.cds.CDSOptions;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import jdk.test.lib.Asserts;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import jdk.internal.misc.Unsafe;
import java.util.Scanner;
import jtreg.SkippedException;

class CrashApp {
    public static void main(String[] args) {
        Unsafe.getUnsafe().putInt(0L, 0);
    }
}

public class ClhsdbCDSCore {

    private static final String TEST_CDS_CORE_FILE_NAME = "cds_core_file";
    private static final String LOCATIONS_STRING = "location: ";
    private static final String RUN_SHELL_NO_LIMIT = "ulimit -c unlimited && ";
    private static final String SHARED_ARCHIVE_NAME = "ArchiveForClhsdbCDSCore.jsa";
    private static final String CORE_PATTERN_FILE_NAME = "/proc/sys/kernel/core_pattern";

    public static void main(String[] args) throws Exception {
        System.out.println("Starting ClhsdbCDSCore test");
        cleanup();

        try {
            CDSOptions opts = (new CDSOptions()).setArchiveName(SHARED_ARCHIVE_NAME);
            CDSTestUtils.createArchiveAndCheck(opts);

            String[] jArgs = {
                "-Xmx512m",
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:SharedArchiveFile=" + SHARED_ARCHIVE_NAME,
                "-XX:+CreateCoredumpOnCrash",
                "-Xshare:auto",
                "-XX:+ProfileInterpreter",
                "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
                CrashApp.class.getName()
            };

            OutputAnalyzer crashOut;
            try {
               List<String> options = new ArrayList<>();
               options.addAll(Arrays.asList(jArgs));
               crashOut =
                   ProcessTools.executeProcess(getTestJavaCommandlineWithPrefix(
                   RUN_SHELL_NO_LIMIT, options.toArray(new String[0])));
            } catch (Throwable t) {
               throw new Error("Can't execute the java cds process.", t);
            }

            System.out.println(crashOut.getOutput());
            String crashOutputString = crashOut.getOutput();
            String coreFileLocation = getCoreFileLocation(crashOutputString);
            if (coreFileLocation == null) {
                if (Platform.isOSX()) {
                    File coresDir = new File("/cores");
                    if (!coresDir.isDirectory() || !coresDir.canWrite()) {
                        throw new Error("cores is not a directory or does not have write permissions");
                    }
                } else if (Platform.isLinux()) {
                    // Check if a crash report tool is installed.
                    File corePatternFile = new File(CORE_PATTERN_FILE_NAME);
                    Scanner scanner = new Scanner(corePatternFile);
                    while (scanner.hasNextLine()) {
                        String line = scanner.nextLine();
                        line = line.trim();
                        System.out.println(line);
                        if (line.startsWith("|")) {
                            System.out.println(
                                "\nThis system uses a crash report tool ($cat /proc/sys/kernel/core_pattern).\n" +
                                "Core files might not be generated. Please reset /proc/sys/kernel/core_pattern\n" +
                                "to enable core generation. Skipping this test.");
                            cleanup();
                            throw new SkippedException("This system uses a crash report tool");
                        }
                    }
                }
                throw new Error("Couldn't find core file location in: '" + crashOutputString + "'");
            }
            try {
                Asserts.assertGT(new File(coreFileLocation).length(), 0L, "Unexpected core size");
                Files.move(Paths.get(coreFileLocation), Paths.get(TEST_CDS_CORE_FILE_NAME));
            } catch (IOException ioe) {
                throw new Error("Can't move core file: " + ioe, ioe);
            }

            ClhsdbLauncher test = new ClhsdbLauncher();

            // Ensure that UseSharedSpaces is turned on.
            List<String> cmds = List.of("flags UseSharedSpaces");

            String useSharedSpacesOutput = test.runOnCore(TEST_CDS_CORE_FILE_NAME, cmds,
                                                          null, null);

            if (useSharedSpacesOutput == null) {
                // Output could be null due to attach permission issues.
                cleanup();
                throw new SkippedException("Could not determine the UseSharedSpaces value");
            }

            if (!useSharedSpacesOutput.contains("true")) {
                // CDS archive is not mapped. Skip the rest of the test.
                cleanup();
                throw new SkippedException("The CDS archive is not mapped");
            }

            cmds = List.of("printmdo -a", "printall", "jstack -v");

            Map<String, List<String>> expStrMap = new HashMap<>();
            Map<String, List<String>> unExpStrMap = new HashMap<>();
            expStrMap.put("printmdo -a", List.of(
                "CounterData",
                "BranchData"));
            unExpStrMap.put("printmdo -a", List.of(
                "No suitable match for type of address"));
            expStrMap.put("printall", List.of(
                "aload_0",
                "_nofast_aload_0",
                "_nofast_getfield",
                "_nofast_putfield",
                "Constant Pool of",
                "public static void main(java.lang.String[])",
                "Bytecode",
                "invokevirtual",
                "checkcast",
                "Exception Table",
                "invokedynamic"));
            unExpStrMap.put("printall", List.of(
                "sun.jvm.hotspot.types.WrongTypeException",
                "illegal code",
                "Failure occurred at bci",
                "No suitable match for type of address"));
            expStrMap.put("jstack -v", List.of(
                "Common-Cleaner",
                "Method*"));
            unExpStrMap.put("jstack -v", List.of(
                "sun.jvm.hotspot.debugger.UnmappedAddressException"));
            test.runOnCore(TEST_CDS_CORE_FILE_NAME, cmds, expStrMap, unExpStrMap);
        } catch (SkippedException e) {
            throw e;
        } catch (Exception ex) {
            throw new RuntimeException("Test ERROR " + ex, ex);
        }
        cleanup();
        System.out.println("Test PASSED");
    }

    // lets search for a few possible locations using process output and return existing location
    private static String getCoreFileLocation(String crashOutputString) {
        Asserts.assertTrue(crashOutputString.contains(LOCATIONS_STRING),
            "Output doesn't contain the location of core file.");
        String stringWithLocation = Arrays.stream(crashOutputString.split("\\r?\\n"))
            .filter(str -> str.contains(LOCATIONS_STRING))
            .findFirst()
            .get();
        stringWithLocation = stringWithLocation.substring(stringWithLocation
            .indexOf(LOCATIONS_STRING) + LOCATIONS_STRING.length());
        System.out.println("getCoreFileLocation found stringWithLocation = " + stringWithLocation);
        String coreWithPid;
        if (stringWithLocation.contains("or ")) {
            Matcher m = Pattern.compile("or.* ([^ ]+[^\\)])\\)?").matcher(stringWithLocation);
            if (!m.find()) {
                throw new Error("Couldn't find path to core inside location string");
            }
            coreWithPid = m.group(1);
        } else {
            coreWithPid = stringWithLocation.trim();
        }
        if (new File(coreWithPid).exists()) {
            return coreWithPid;
        }
        String justCore = Paths.get("core").toString();
        if (new File(justCore).exists()) {
            return justCore;
        }
        Path coreWithPidPath = Paths.get(coreWithPid);
        String justFile = coreWithPidPath.getFileName().toString();
        if (new File(justFile).exists()) {
            return justFile;
        }
        Path parent = coreWithPidPath.getParent();
        if (parent != null) {
            String coreWithoutPid = parent.resolve("core").toString();
            if (new File(coreWithoutPid).exists()) {
                return coreWithoutPid;
            }
        }
        return null;
    }

    private static String[] getTestJavaCommandlineWithPrefix(String prefix, String... args) {
        try {
            String cmd = ProcessTools.getCommandLine(ProcessTools.createJavaProcessBuilder(true, args));
            return new String[]{"sh", "-c", prefix + cmd};
        } catch (Throwable t) {
            throw new Error("Can't create process builder: " + t, t);
        }
    }

    private static void cleanup() {
        remove(TEST_CDS_CORE_FILE_NAME);
        remove(SHARED_ARCHIVE_NAME);
    }

    private static void remove(String item) {
        File toDelete = new File(item);
        toDelete.delete();
    }
}

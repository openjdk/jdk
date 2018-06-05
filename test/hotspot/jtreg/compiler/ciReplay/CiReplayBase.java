/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package compiler.ciReplay;

import compiler.whitebox.CompilerWhiteBoxTest;
import java.io.IOException;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

public abstract class CiReplayBase {
    public static final String REPLAY_FILE_NAME = "test_replay.txt";
    public static final boolean CLIENT_VM_AVAILABLE;
    public static final boolean SERVER_VM_AVAILABLE;
    public static final String TIERED_ENABLED_VM_OPTION = "-XX:+TieredCompilation";
    public static final String TIERED_DISABLED_VM_OPTION = "-XX:-TieredCompilation";
    public static final String ENABLE_COREDUMP_ON_CRASH = "-XX:+CreateCoredumpOnCrash";
    public static final String DISABLE_COREDUMP_ON_CRASH = "-XX:-CreateCoredumpOnCrash";
    public static final String CLIENT_VM_OPTION = "-client";
    public static final String SERVER_VM_OPTION = "-server";
    public static final String TEST_CORE_FILE_NAME = "test_core";
    public static final String RUN_SHELL_NO_LIMIT = "ulimit -c unlimited && ";
    private static final String REPLAY_FILE_OPTION = "-XX:ReplayDataFile=" + REPLAY_FILE_NAME;
    private static final String LOCATIONS_STRING = "location: ";
    private static final String HS_ERR_NAME = "hs_err_pid";
    private static final String RUN_SHELL_ZERO_LIMIT = "ulimit -S -c 0 && ";
    private static final String VERSION_OPTION = "-version";
    private static final String[] REPLAY_GENERATION_OPTIONS = new String[]{"-Xms128m", "-Xmx128m",
        "-XX:MetaspaceSize=4m", "-XX:MaxMetaspaceSize=16m", "-XX:InitialCodeCacheSize=512k",
        "-XX:ReservedCodeCacheSize=4m", "-XX:ThreadStackSize=512", "-XX:VMThreadStackSize=512",
        "-XX:CompilerThreadStackSize=512", "-XX:ParallelGCThreads=1", "-XX:CICompilerCount=2",
        "-Xcomp", "-XX:CICrashAt=1", "-XX:+DumpReplayDataOnError", "-XX:-TransmitErrorReport",
        "-XX:+PreferInterpreterNativeStubs", "-XX:+PrintCompilation", REPLAY_FILE_OPTION};
    private static final String[] REPLAY_OPTIONS = new String[]{DISABLE_COREDUMP_ON_CRASH,
        "-XX:+ReplayCompiles", REPLAY_FILE_OPTION};
    protected final Optional<Boolean> runServer;

    static {
        try {
            CLIENT_VM_AVAILABLE = ProcessTools.executeTestJvm(CLIENT_VM_OPTION, VERSION_OPTION)
                    .getOutput().contains("Client");
            SERVER_VM_AVAILABLE = ProcessTools.executeTestJvm(SERVER_VM_OPTION, VERSION_OPTION)
                    .getOutput().contains("Server");
        } catch(Throwable t) {
            throw new Error("Initialization failed: " + t, t);
        }
    }

    public CiReplayBase() {
        runServer = Optional.empty();
    }

    public CiReplayBase(String args[]) {
        if (args.length != 1 || (!"server".equals(args[0]) && !"client".equals(args[0]))) {
            throw new Error("Expected 1 argument: [server|client]");
        }
        runServer = Optional.of("server".equals(args[0]));
    }

    public void runTest(boolean needCoreDump, String... args) {
        cleanup();
        if (generateReplay(needCoreDump)) {
            testAction();
            cleanup();
        } else {
            throw new Error("Host is not configured to generate cores");
        }
    }

    public abstract void testAction();

    private static void remove(String item) {
        File toDelete = new File(item);
        toDelete.delete();
        if (Platform.isWindows()) {
            Utils.waitForCondition(() -> !toDelete.exists());
        }
    }

    private static void removeFromCurrentDirectoryStartingWith(String prefix) {
        Arrays.stream(new File(".").listFiles())
                .filter(f -> f.getName().startsWith(prefix))
                .forEach(File::delete);
    }

    public static void cleanup() {
        removeFromCurrentDirectoryStartingWith("core");
        removeFromCurrentDirectoryStartingWith("replay");
        removeFromCurrentDirectoryStartingWith(HS_ERR_NAME);
        remove(TEST_CORE_FILE_NAME);
        remove(REPLAY_FILE_NAME);
    }

    public boolean generateReplay(boolean needCoreDump, String... vmopts) {
        OutputAnalyzer crashOut;
        String crashOutputString;
        try {
            List<String> options = new ArrayList<>();
            options.addAll(Arrays.asList(REPLAY_GENERATION_OPTIONS));
            options.addAll(Arrays.asList(vmopts));
            options.add(needCoreDump ? ENABLE_COREDUMP_ON_CRASH : DISABLE_COREDUMP_ON_CRASH);
            options.add(VERSION_OPTION);
            if (needCoreDump) {
                crashOut = ProcessTools.executeProcess(getTestJavaCommandlineWithPrefix(
                        RUN_SHELL_NO_LIMIT, options.toArray(new String[0])));
            } else {
                crashOut = ProcessTools.executeProcess(ProcessTools.createJavaProcessBuilder(true,
                        options.toArray(new String[0])));
            }
            crashOutputString = crashOut.getOutput();
            Asserts.assertNotEquals(crashOut.getExitValue(), 0, "Crash JVM exits gracefully");
            Files.write(Paths.get("crash.out"), crashOutputString.getBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Throwable t) {
            throw new Error("Can't create replay: " + t, t);
        }
        if (needCoreDump) {
            String coreFileLocation = getCoreFileLocation(crashOutputString);
            if (coreFileLocation == null) {
                if (Platform.isOSX()) {
                    File coresDir = new File("/cores");
                    if (!coresDir.isDirectory() || !coresDir.canWrite()) {
                        return false;
                    }
                }
                throw new Error("Couldn't find core file location in: '" + crashOutputString + "'");
            }
            try {
                Asserts.assertGT(new File(coreFileLocation).length(), 0L, "Unexpected core size");
                Files.move(Paths.get(coreFileLocation), Paths.get(TEST_CORE_FILE_NAME));
            } catch (IOException ioe) {
                throw new Error("Can't move core file: " + ioe, ioe);
            }
        }
        removeFromCurrentDirectoryStartingWith(HS_ERR_NAME);
        return true;
    }

    public void commonTests() {
        positiveTest();
        if (Platform.isTieredSupported()) {
            positiveTest(TIERED_ENABLED_VM_OPTION);
        }
    }

    public int startTest(String... additionalVmOpts) {
        try {
            List<String> allAdditionalOpts = new ArrayList<>();
            allAdditionalOpts.addAll(Arrays.asList(REPLAY_OPTIONS));
            allAdditionalOpts.addAll(Arrays.asList(additionalVmOpts));
            OutputAnalyzer oa = ProcessTools.executeProcess(getTestJavaCommandlineWithPrefix(
                    RUN_SHELL_ZERO_LIMIT, allAdditionalOpts.toArray(new String[0])));
            return oa.getExitValue();
        } catch (Throwable t) {
            throw new Error("Can't run replay process: " + t, t);
        }
    }

    public void runVmTests() {
        boolean runServerValue = runServer.orElseThrow(() -> new Error("runServer must be set"));
        if (runServerValue) {
            if (CLIENT_VM_AVAILABLE) {
                negativeTest(CLIENT_VM_OPTION);
            }
        } else {
            if (SERVER_VM_AVAILABLE) {
                negativeTest(TIERED_DISABLED_VM_OPTION, SERVER_VM_OPTION);
                if (Platform.isTieredSupported()) {
                    positiveTest(TIERED_ENABLED_VM_OPTION, SERVER_VM_OPTION);
                }
            }
        }
        nonTieredTests(runServerValue ? CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION
                : CompilerWhiteBoxTest.COMP_LEVEL_SIMPLE);
    }

    public int getCompLevelFromReplay() {
        try(BufferedReader br = new BufferedReader(new FileReader(REPLAY_FILE_NAME))) {
            return br.lines()
                    .filter(s -> s.startsWith("compile "))
                    .map(s -> s.split("\\s+")[5])
                    .map(Integer::parseInt)
                    .findAny()
                    .get();
        } catch (IOException ioe) {
            throw new Error("Failed to read replay data: " + ioe, ioe);
        }
    }

    public void positiveTest(String... additionalVmOpts) {
        Asserts.assertEQ(startTest(additionalVmOpts), 0, "Unexpected exit code for positive case: "
                + Arrays.toString(additionalVmOpts));
    }

    public void negativeTest(String... additionalVmOpts) {
        Asserts.assertNE(startTest(additionalVmOpts), 0, "Unexpected exit code for negative case: "
                + Arrays.toString(additionalVmOpts));
    }

    public void nonTieredTests(int compLevel) {
        int replayDataCompLevel = getCompLevelFromReplay();
        if (replayDataCompLevel == compLevel) {
            positiveTest(TIERED_DISABLED_VM_OPTION);
        } else {
            negativeTest(TIERED_DISABLED_VM_OPTION);
        }
    }

    // lets search few possible locations using process output and return existing location
    private String getCoreFileLocation(String crashOutputString) {
        Asserts.assertTrue(crashOutputString.contains(LOCATIONS_STRING),
                "Output doesn't contain the location of core file, see crash.out");
        String stringWithLocation = Arrays.stream(crashOutputString.split("\\r?\\n"))
                .filter(str -> str.contains(LOCATIONS_STRING))
                .findFirst()
                .get();
        stringWithLocation = stringWithLocation.substring(stringWithLocation
                .indexOf(LOCATIONS_STRING) + LOCATIONS_STRING.length());
        String coreWithPid;
        if (stringWithLocation.contains("or ") && !Platform.isWindows()) {
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

    private String[] getTestJavaCommandlineWithPrefix(String prefix, String... args) {
        try {
            String cmd = ProcessTools.getCommandLine(ProcessTools.createJavaProcessBuilder(true, args));
            return new String[]{"sh", "-c", prefix
                    + (Platform.isWindows() ? cmd.replace('\\', '/').replace(";", "\\;") : cmd)};
        } catch(Throwable t) {
            throw new Error("Can't create process builder: " + t, t);
        }
    }
}

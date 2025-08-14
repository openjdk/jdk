/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.cds;

import java.io.File;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.StringArrayUtils;
import jdk.test.whitebox.WhiteBox;
import jtreg.SkippedException;

/*
 * This is a base class used for testing CDS functionalities with complex applications.
 * You can define the application by overridding the vmArgs(), classpath() and appCommandLine()
 * methods. Application-specific validation checks can be implemented with checkExecution().
 *
 * The AOT workflow runs with one-step training by default. For debugging purposes, run
 * jtreg with -vmoption:-DCDSAppTester.two.step.training=true. This will run -XX:AOTMode=record
 * and -XX:AOTMode=record in two separate processes that you can rerun easily inside a debugger.
 * Also, the log files are easier to read.
 */
abstract public class CDSAppTester {
    private final String name;
    private final String classListFile;
    private final String classListFileLog;
    private final String aotConfigurationFile;
    private final String aotConfigurationFileLog;
    private final String staticArchiveFile;
    private final String staticArchiveFileLog;
    private final String aotCacheFile;
    private final String aotCacheFileLog;
    private final String dynamicArchiveFile;
    private final String dynamicArchiveFileLog;
    private final String tempBaseArchiveFile;
    private int numProductionRuns = 0;
    private String whiteBoxJar = null;
    private boolean inOneStepTraining = false;

    /**
     * All files created in the CDS/AOT workflow will be name + extension. E.g.
     * - name.aot
     * - name.aotconfig
     * - name.classlist
     * - name.jsa
     */
    public CDSAppTester(String name) {
        if (CDSTestUtils.DYNAMIC_DUMP) {
            throw new SkippedException("Tests based on CDSAppTester should be excluded when -Dtest.dynamic.cds.archive is specified");
        }

        this.name = name;
        classListFile = name() + ".classlist";
        classListFileLog = logFileName(classListFile);
        aotConfigurationFile = name() + ".aotconfig";
        aotConfigurationFileLog = logFileName(aotConfigurationFile);
        staticArchiveFile = name() + ".static.jsa";
        staticArchiveFileLog = logFileName(staticArchiveFile);
        aotCacheFile = name() + ".aot";
        aotCacheFileLog = logFileName(aotCacheFile);;
        dynamicArchiveFile = name() + ".dynamic.jsa";
        dynamicArchiveFileLog = logFileName(dynamicArchiveFile);
        tempBaseArchiveFile = name() + ".temp-base.jsa";
    }

    private String productionRunLog() {
        if (numProductionRuns == 0) {
            return logFileName(name() + ".production");
        } else {
            return logFileName(name() + ".production." + numProductionRuns);
        }
    }

    private static String logFileName(String file) {
        file = file.replace("\"", "%22");
        file = file.replace("'", "%27");
        return file + ".log";
    }

    private enum Workflow {
        STATIC,        // classic -Xshare:dump workflow
        DYNAMIC,       // classic -XX:ArchiveClassesAtExit
        AOT,           // JEP 483 Ahead-of-Time Class Loading & Linking
    }

    public enum RunMode {
        TRAINING,       // -XX:DumpLoadedClassList OR {-XX:AOTMode=record -XX:AOTConfiguration}
        DUMP_STATIC,    // -Xshare:dump
        DUMP_DYNAMIC,   // -XX:ArchiveClassesArExit
        ASSEMBLY,       // JEP 483 (assembly phase, app logic not executed)
        PRODUCTION;     // Running with the CDS archive produced from the above steps

        public boolean isStaticDump() {
            return this == DUMP_STATIC;
        }
        public boolean isProductionRun() {
            return this == PRODUCTION;
        }

        // When <code>CDSAppTester::checkExecution(out, runMode)</code> is called, has the application been
        // executed? If so, <code>out</code> should contain logs printed by the application's own logic.
        public boolean isApplicationExecuted() {
            return (this != ASSEMBLY) && (this != DUMP_STATIC);
        }
    }

    public boolean isDumping(RunMode runMode) {
        if (isStaticWorkflow()) {
            return runMode == RunMode.DUMP_STATIC;
        } else if (isDynamicWorkflow()) {
            return runMode == RunMode.DUMP_DYNAMIC;
        } else if (isAOTWorkflow()) {
            return runMode == RunMode.TRAINING || runMode == RunMode.ASSEMBLY;
        } else {
            return false;
        }
    }

    public final String name() {
        return this.name;
    }

    // optional
    public String[] vmArgs(RunMode runMode) {
        return new String[0];
    }

    // optional
    public String classpath(RunMode runMode) {
        return null;
    }

    // optional
    public String modulepath(RunMode runMode) {
        return null;
    }

    // must override
    // main class, followed by arguments to the main class
    abstract public String[] appCommandLine(RunMode runMode);

    // optional
    public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {}

    private Workflow workflow;
    private boolean checkExitValue = true;

    public final void setCheckExitValue(boolean b) {
        checkExitValue = b;
    }

    public final void useWhiteBox(String whiteBoxJar) {
        this.whiteBoxJar = whiteBoxJar;
    }

    public final boolean isStaticWorkflow() {
        return workflow == Workflow.STATIC;
    }

    public final boolean isDynamicWorkflow() {
        return workflow == Workflow.DYNAMIC;
    }

    public final boolean isAOTWorkflow() {
        return workflow == Workflow.AOT;
    }

    private String logToFile(String logFile, String... logTags) {
        StringBuilder sb = new StringBuilder("-Xlog:arguments");
        String prefix = ",";
        for (String tag : logTags) {
            sb.append(prefix);
            sb.append(tag);
        }
        sb.append(":file=" + logFile + "::filesize=0");
        return sb.toString();
    }

    private void listOutputFile(String file) {
        File f = new File(file);
        if (f.exists()) {
            System.out.println("[output file: " + file + " " + f.length() + " bytes]");
        } else {
            System.out.println("[output file: " + file + " does not exist]");
        }
    }

    private OutputAnalyzer executeAndCheck(String[] cmdLine, RunMode runMode, String... logFiles) throws Exception {
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(cmdLine);
        Process process = pb.start();
        OutputAnalyzer output = CDSTestUtils.executeAndLog(process, runMode.toString());
        for (String logFile : logFiles) {
            listOutputFile(logFile);
        }
        if (checkExitValue) {
            output.shouldHaveExitValue(0);
        }
        output.shouldNotContain(CDSTestUtils.MSG_STATIC_FIELD_MAY_HOLD_DIFFERENT_VALUE);
        CDSTestUtils.checkCommonExecExceptions(output);
        checkExecution(output, runMode);
        return output;
    }

    private String[] addCommonVMArgs(RunMode runMode, String[] cmdLine) {
        cmdLine = addClassOrModulePath(runMode, cmdLine);
        cmdLine = addWhiteBox(cmdLine);
        return cmdLine;
    }

    private String[] addClassOrModulePath(RunMode runMode, String[] cmdLine) {
        String cp = classpath(runMode);
        if (cp == null) {
            // Override the "-cp ...." added by Jtreg
            cmdLine = StringArrayUtils.concat(cmdLine, "-Djava.class.path=");
        } else {
            cmdLine = StringArrayUtils.concat(cmdLine, "-cp", cp);
        }
        String mp = modulepath(runMode);
        if (mp != null) {
            cmdLine = StringArrayUtils.concat(cmdLine, "--module-path", mp);
        }
        return cmdLine;
    }

    private String[] addWhiteBox(String[] cmdLine) {
        if (whiteBoxJar != null) {
            cmdLine = StringArrayUtils.concat(cmdLine,
                                              "-XX:+UnlockDiagnosticVMOptions",
                                              "-XX:+WhiteBoxAPI",
                                              "-Xbootclasspath/a:" + whiteBoxJar);
        }
        return cmdLine;
    }

    private OutputAnalyzer recordAOTConfiguration() throws Exception {
        RunMode runMode = RunMode.TRAINING;
        String[] cmdLine = StringArrayUtils.concat(vmArgs(runMode),
                                                   "-XX:AOTMode=record",
                                                   "-XX:AOTConfiguration=" + aotConfigurationFile,
                                                   logToFile(aotConfigurationFileLog,
                                                             "class+load=debug",
                                                             "aot=debug",
                                                             "cds=debug",
                                                             "aot+class=debug"));
        cmdLine = addCommonVMArgs(runMode, cmdLine);
        cmdLine = StringArrayUtils.concat(cmdLine, appCommandLine(runMode));
        return executeAndCheck(cmdLine, runMode, aotConfigurationFile, aotConfigurationFileLog);
    }

    private OutputAnalyzer createAOTCacheOneStep() throws Exception {
        RunMode runMode = RunMode.TRAINING;
        String[] cmdLine = StringArrayUtils.concat(vmArgs(runMode),
                                                   "-XX:AOTMode=record",
                                                   "-XX:AOTCacheOutput=" + aotCacheFile,
                                                   logToFile(aotCacheFileLog,
                                                             "class+load=debug",
                                                             "aot=debug",
                                                             "aot+class=debug",
                                                             "cds=debug"));
        cmdLine = addCommonVMArgs(runMode, cmdLine);
        cmdLine = StringArrayUtils.concat(cmdLine, appCommandLine(runMode));
        OutputAnalyzer out =  executeAndCheck(cmdLine, runMode, aotCacheFile, aotCacheFileLog);
        listOutputFile(aotCacheFileLog + ".0"); // the log file for the training run
        return out;
    }

    private OutputAnalyzer createClassList() throws Exception {
        RunMode runMode = RunMode.TRAINING;
        String[] cmdLine = StringArrayUtils.concat(vmArgs(runMode),
                                                   "-Xshare:off",
                                                   "-XX:DumpLoadedClassList=" + classListFile,
                                                   logToFile(classListFileLog,
                                                             "class+load=debug"));
        cmdLine = addCommonVMArgs(runMode, cmdLine);
        cmdLine = StringArrayUtils.concat(cmdLine, appCommandLine(runMode));
        return executeAndCheck(cmdLine, runMode, classListFile, classListFileLog);
    }

    private OutputAnalyzer dumpStaticArchive() throws Exception {
        RunMode runMode = RunMode.DUMP_STATIC;
        String[] cmdLine = StringArrayUtils.concat(vmArgs(runMode),
                                                   "-Xlog:aot",
                                                   "-Xlog:aot+heap=error",
                                                   "-Xlog:cds",
                                                   "-Xshare:dump",
                                                   "-XX:SharedArchiveFile=" + staticArchiveFile,
                                                   "-XX:SharedClassListFile=" + classListFile,
                                                   logToFile(staticArchiveFileLog,
                                                             "aot=debug",
                                                             "cds=debug",
                                                             "cds+class=debug",
                                                             "aot+heap=warning",
                                                             "aot+resolve=debug"));
        cmdLine = addCommonVMArgs(runMode, cmdLine);
        cmdLine = StringArrayUtils.concat(cmdLine, appCommandLine(runMode));
        return executeAndCheck(cmdLine, runMode, staticArchiveFile, staticArchiveFileLog);
    }

    private OutputAnalyzer createAOTCache() throws Exception {
        RunMode runMode = RunMode.ASSEMBLY;
        String[] cmdLine = StringArrayUtils.concat(vmArgs(runMode),
                                                   "-Xlog:aot",
                                                   "-Xlog:aot+heap=error",
                                                   "-Xlog:cds",
                                                   "-XX:AOTMode=create",
                                                   "-XX:AOTConfiguration=" + aotConfigurationFile,
                                                   "-XX:AOTCache=" + aotCacheFile,
                                                   logToFile(aotCacheFileLog,
                                                             "cds=debug",
                                                             "aot=debug",
                                                             "aot+class=debug",
                                                             "aot+heap=warning",
                                                             "aot+resolve=debug"));
        cmdLine = addCommonVMArgs(runMode, cmdLine);
        cmdLine = StringArrayUtils.concat(cmdLine, appCommandLine(runMode));
        return executeAndCheck(cmdLine, runMode, aotCacheFile, aotCacheFileLog);
    }

    // Creating a dynamic CDS archive (with -XX:ArchiveClassesAtExit=<foo>.jsa) requires that the current
    // JVM process is using a static archive (which is usually the default CDS archive included in the JDK).
    // However, if the JDK doesn't include a default CDS archive that's compatible with the set of
    // VM options used by this test, we need to create a temporary static archive to be used with -XX:ArchiveClassesAtExit.
    private String getBaseArchiveForDynamicArchive() throws Exception {
        WhiteBox wb = WhiteBox.getWhiteBox();
        if (wb.isSharingEnabled()) {
            // This current JVM is able to use a default CDS archive included by the JDK, so
            // if we launch a JVM child process (with the same set of options as the current JVM),
            // that process is also able to use the same default CDS archive for creating
            // a dynamic archive.
            return null;
        } else {
            // This current JVM is unable to use a default CDS archive, so let's create a temporary
            // static archive to be used with -XX:ArchiveClassesAtExit.
            File f = new File(tempBaseArchiveFile);
            if (!f.exists()) {
                CDSOptions opts = new CDSOptions();
                opts.setArchiveName(tempBaseArchiveFile);
                opts.addSuffix("-Djava.class.path=");
                OutputAnalyzer out = CDSTestUtils.createArchive(opts);
                CDSTestUtils.checkBaseDump(out);
            }
            return tempBaseArchiveFile;
        }
    }

    private OutputAnalyzer dumpDynamicArchive() throws Exception {
        RunMode runMode = RunMode.DUMP_DYNAMIC;
        String[] cmdLine = new String[0];
        String baseArchive = getBaseArchiveForDynamicArchive();
        if (isDynamicWorkflow()) {
          // "classic" dynamic archive
          cmdLine = StringArrayUtils.concat(vmArgs(runMode),
                                            "-Xlog:aot",
                                            "-Xlog:cds",
                                            "-XX:ArchiveClassesAtExit=" + dynamicArchiveFile,
                                            logToFile(dynamicArchiveFileLog,
                                                      "aot=debug",
                                                      "cds=debug",
                                                      "cds+class=debug",
                                                      "aot+resolve=debug",
                                                      "class+load=debug"));
          cmdLine = addCommonVMArgs(runMode, cmdLine);
        }
        if (baseArchive != null) {
            cmdLine = StringArrayUtils.concat(cmdLine, "-XX:SharedArchiveFile=" + baseArchive);
        }
        cmdLine = StringArrayUtils.concat(cmdLine, appCommandLine(runMode));
        return executeAndCheck(cmdLine, runMode, dynamicArchiveFile, dynamicArchiveFileLog);
    }

    public OutputAnalyzer productionRun() throws Exception {
        return productionRun(null, null);
    }

    public OutputAnalyzer productionRun(String[] extraVmArgs) throws Exception {
        return productionRun(extraVmArgs, null);
    }

    // After calling run(String[]), you can call this method to run the app again, with the AOTCache
    // using different args to the VM and application.
    public OutputAnalyzer productionRun(String[] extraVmArgs, String[] extraAppArgs) throws Exception {
        RunMode runMode = RunMode.PRODUCTION;
        String[] cmdLine = StringArrayUtils.concat(vmArgs(runMode),
                                                   "-XX:+UnlockDiagnosticVMOptions",
                                                   "-XX:VerifyArchivedFields=2", // make sure archived heap objects are good.
                                                   logToFile(productionRunLog(), "aot", "cds"));
        cmdLine = addCommonVMArgs(runMode, cmdLine);

        if (isStaticWorkflow()) {
            cmdLine = StringArrayUtils.concat(cmdLine, "-Xshare:on", "-XX:SharedArchiveFile=" + staticArchiveFile);
        } else if (isDynamicWorkflow()) {
            cmdLine = StringArrayUtils.concat(cmdLine, "-Xshare:on", "-XX:SharedArchiveFile=" + dynamicArchiveFile);
       } else if (isAOTWorkflow()) {
            cmdLine = StringArrayUtils.concat(cmdLine, "-XX:AOTMode=on", "-XX:AOTCache=" + aotCacheFile);
        }

        if (extraVmArgs != null) {
            cmdLine = StringArrayUtils.concat(cmdLine, extraVmArgs);
        }

        cmdLine = StringArrayUtils.concat(cmdLine, appCommandLine(runMode));

        if (extraAppArgs != null) {
            cmdLine = StringArrayUtils.concat(cmdLine, extraAppArgs);
        }

        OutputAnalyzer out = executeAndCheck(cmdLine, runMode, productionRunLog());
        numProductionRuns ++;
        return out;
    }

    public void run(String... args) throws Exception {
        String err = "Must have at least one command line argument of the following: ";
        String prefix = "";
        for (Workflow wf : Workflow.values()) {
            err += prefix;
            err += wf;
            prefix = ", ";
        }
        if (args.length < 1) {
            throw new RuntimeException(err);
        } else {
            if (args[0].equals("STATIC")) {
                runStaticWorkflow();
            } else if (args[0].equals("DYNAMIC")) {
                runDynamicWorkflow();
            } else if (args[0].equals("AOT")) {
                runAOTWorkflow(args);
            } else {
                throw new RuntimeException(err);
            }
        }
    }

    public void runStaticWorkflow() throws Exception {
        this.workflow = Workflow.STATIC;
        createClassList();
        dumpStaticArchive();
        productionRun();
    }

    public void runDynamicWorkflow() throws Exception {
        this.workflow = Workflow.DYNAMIC;
        dumpDynamicArchive();
        productionRun();
    }

    // See JEP 483
    public void runAOTWorkflow(String... args) throws Exception {
        this.workflow = Workflow.AOT;
        boolean oneStepTraining = true; // by default use onestep trainning

        if (System.getProperty("CDSAppTester.two.step.training") != null) {
            oneStepTraining = false;
        }

        if (args.length > 1) {
            // Tests such as test/hotspot/jtreg/runtime/cds/appcds/aotCache/SpecialCacheNames.java
            // use --one-step-training or --two-step-training to force a certain training workflow.
            if (args[1].equals("--one-step-training")) {
                oneStepTraining = true;
            } else if (args[1].equals("--two-step-training")) {
                oneStepTraining = false;
            } else {
                throw new RuntimeException("Unknown option: " + args[1]);
            }
        }

        if (oneStepTraining) {
            try {
                inOneStepTraining = true;
                createAOTCacheOneStep();
            } finally {
                inOneStepTraining = false;
            }
        } else {
            recordAOTConfiguration();
            createAOTCache();
        }
        productionRun();
    }

    // See JEP 483; stop at the assembly run; do not execute production run
    public void runAOTAssemblyWorkflow() throws Exception {
        this.workflow = Workflow.AOT;
        recordAOTConfiguration();
        createAOTCache();
    }
}

/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
*/
abstract public class CDSAppTester {
    private final String name;
    private final String classListFile;
    private final String classListFileLog;
    private final String staticArchiveFile;
    private final String staticArchiveFileLog;
    private final String dynamicArchiveFile;
    private final String dynamicArchiveFileLog;
    private final String tempBaseArchiveFile;
    private int numProductionRuns = 0;

    public CDSAppTester(String name) {
        if (CDSTestUtils.DYNAMIC_DUMP) {
            throw new SkippedException("Tests based on CDSAppTester should be excluded when -Dtest.dynamic.cds.archive is specified");
        }

        // Old workflow
        this.name = name;
        classListFile = name() + ".classlist";
        classListFileLog = classListFile + ".log";
        staticArchiveFile = name() + ".static.jsa";
        staticArchiveFileLog = staticArchiveFile + ".log";
        dynamicArchiveFile = name() + ".dynamic.jsa";
        dynamicArchiveFileLog = dynamicArchiveFile + ".log";
        tempBaseArchiveFile = name() + ".temp-base.jsa";
    }

    private String productionRunLog() {
        if (numProductionRuns == 0) {
            return name() + ".production.log";
        } else {
            return name() + ".production." + numProductionRuns + ".log";
        }
    }

    private enum Workflow {
        STATIC,        // classic -Xshare:dump workflow
        DYNAMIC,       // classic -XX:ArchiveClassesAtExit
    }

    public enum RunMode {
        CLASSLIST,
        DUMP_STATIC,
        DUMP_DYNAMIC,
        PRODUCTION;

        public boolean isStaticDump() {
            return this == DUMP_STATIC;
        }
        public boolean isProductionRun() {
            return this == PRODUCTION;
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

    public final boolean isStaticWorkflow() {
        return workflow == Workflow.STATIC;
    }

    public final boolean isDynamicWorkflow() {
        return workflow == Workflow.DYNAMIC;
    }

    private String logToFile(String logFile, String... logTags) {
        StringBuilder sb = new StringBuilder("-Xlog:");
        String prefix = "";
        for (String tag : logTags) {
            sb.append(prefix);
            sb.append(tag);
            prefix = ",";
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

    private OutputAnalyzer createClassList() throws Exception {
        RunMode runMode = RunMode.CLASSLIST;
        String[] cmdLine = StringArrayUtils.concat(vmArgs(runMode),
                                                   "-Xshare:off",
                                                   "-XX:DumpLoadedClassList=" + classListFile,
                                                   "-cp", classpath(runMode),
                                                   logToFile(classListFileLog,
                                                             "class+load=debug"));
        cmdLine = StringArrayUtils.concat(cmdLine, appCommandLine(runMode));
        return executeAndCheck(cmdLine, runMode, classListFile, classListFileLog);
    }

    private OutputAnalyzer dumpStaticArchive() throws Exception {
        RunMode runMode = RunMode.DUMP_STATIC;
        String[] cmdLine = StringArrayUtils.concat(vmArgs(runMode),
                                                   "-Xlog:cds",
                                                   "-Xlog:cds+heap=error",
                                                   "-Xshare:dump",
                                                   "-XX:SharedArchiveFile=" + staticArchiveFile,
                                                   "-XX:SharedClassListFile=" + classListFile,
                                                   "-cp", classpath(runMode),
                                                   logToFile(staticArchiveFileLog,
                                                             "cds=debug",
                                                             "cds+class=debug",
                                                             "cds+heap=warning",
                                                             "cds+resolve=debug"));
        return executeAndCheck(cmdLine, runMode, staticArchiveFile, staticArchiveFileLog);
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
                                            "-Xlog:cds",
                                            "-XX:ArchiveClassesAtExit=" + dynamicArchiveFile,
                                            "-cp", classpath(runMode),
                                            logToFile(dynamicArchiveFileLog,
                                                      "cds=debug",
                                                      "cds+class=debug",
                                                      "cds+resolve=debug",
                                                      "class+load=debug"));
        }
        if (baseArchive != null) {
            cmdLine = StringArrayUtils.concat(cmdLine, "-XX:SharedArchiveFile=" + baseArchive);
        }
        cmdLine = StringArrayUtils.concat(cmdLine, appCommandLine(runMode));
        return executeAndCheck(cmdLine, runMode, dynamicArchiveFile, dynamicArchiveFileLog);
    }

    private OutputAnalyzer productionRun() throws Exception {
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
                                                   "-cp", classpath(runMode),
                                                   logToFile(productionRunLog(), "cds"));

        if (isStaticWorkflow()) {
            cmdLine = StringArrayUtils.concat(cmdLine, "-Xshare:on", "-XX:SharedArchiveFile=" + staticArchiveFile);
        } else if (isDynamicWorkflow()) {
            cmdLine = StringArrayUtils.concat(cmdLine, "-Xshare:on", "-XX:SharedArchiveFile=" + dynamicArchiveFile);
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

    public void run(String args[]) throws Exception {
        String err = "Must have exactly one command line argument of the following: ";
        String prefix = "";
        for (Workflow wf : Workflow.values()) {
            err += prefix;
            err += wf;
            prefix = ", ";
        }
        if (args.length != 1) {
            throw new RuntimeException(err);
        } else {
            if (args[0].equals("STATIC")) {
                runStaticWorkflow();
            } else if (args[0].equals("DYNAMIC")) {
                runDynamicWorkflow();
            } else {
                throw new RuntimeException(err);
            }
        }
    }

    private void runStaticWorkflow() throws Exception {
        this.workflow = Workflow.STATIC;
        createClassList();
        dumpStaticArchive();
        productionRun();
    }

    private void runDynamicWorkflow() throws Exception {
        this.workflow = Workflow.DYNAMIC;
        dumpDynamicArchive();
        productionRun();
    }
}

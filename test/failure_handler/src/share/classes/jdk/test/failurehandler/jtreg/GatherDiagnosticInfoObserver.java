/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.failurehandler.jtreg;

import com.sun.javatest.Harness;
import com.sun.javatest.Parameters;
import com.sun.javatest.TestResult;
import com.sun.javatest.InterviewParameters;
import jdk.test.failurehandler.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * The jtreg test execution observer, which gathers info about
 * system and dumps it to a file.
 */
public class GatherDiagnosticInfoObserver implements Harness.Observer {
    public static final String LOG_FILENAME = "environment.log";
    public static final String ENVIRONMENT_OUTPUT = "environment.html";

    private String compileJdk;
    private String testJdk;

    /*
     * The harness calls this method after each test.
     */
    @Override
    public void finishedTest(TestResult tr) {
        if (!tr.getStatus().isError() && !tr.getStatus().isFailed()) {
            return;
        }

        String jtrFile = tr.getFile().toString();
        final Path workDir = Paths.get(
                jtrFile.substring(0, jtrFile.lastIndexOf('.')));
        workDir.toFile().mkdir();

        String name = getClass().getName();
        PrintWriter log;
        boolean needClose = false;
        try {
            log = new PrintWriter(new FileWriter(
                    workDir.resolve(LOG_FILENAME).toFile(), true), true);
            needClose = true;
        } catch (IOException e) {
            log = new PrintWriter(System.out);
            log.printf("ERROR: %s cannot open log file %s", name,
                    LOG_FILENAME);
            e.printStackTrace(log);
        }
        try {
            log.printf("%s ---%n", name);
            GathererFactory gathererFactory = new GathererFactory(
                    OS.current().family, workDir, log,
                    Paths.get(testJdk), Paths.get(compileJdk));
            gatherEnvInfo(workDir, name, log,
                    gathererFactory.getEnvironmentInfoGatherer());
        } catch (Throwable e) {
            log.printf("ERROR: exception in observer %s:", name);
            e.printStackTrace(log);
        } finally {
            log.printf("--- %s%n", name);
            if (needClose) {
                log.close();
            } else {
                log.flush();
            }
        }
    }

    private void gatherEnvInfo(Path workDir, String name, PrintWriter log,
                               EnvironmentInfoGatherer gatherer) {
        File output = workDir.resolve(ENVIRONMENT_OUTPUT).toFile();
        try (HtmlPage html = new HtmlPage(new PrintWriter(
                new FileWriter(output, true), true))) {
            try (ElapsedTimePrinter timePrinter
                         = new ElapsedTimePrinter(new Stopwatch(), name, log)) {
                gatherer.gatherEnvironmentInfo(html.getRootSection());
            }
        } catch (Throwable e) {
            log.printf("ERROR: exception in observer on getting environment "
                    + "information %s:", name);
            e.printStackTrace(log);
        }
    }

    /*
     * The harness calls this method one time per run, not per test.
     */
    @Override
    public void startingTestRun(Parameters params) {
        // TODO find a better way to get JDKs
        InterviewParameters rp = (InterviewParameters) params;
        Map<String, String> map = new HashMap<>();
        rp.save(map);
        compileJdk = map.get("regtest.compilejdk");
        testJdk = map.get("regtest.testjdk");
    }

    @Override
    public void startingTest(TestResult tr) {
        // no-op
    }

    @Override
    public void stoppingTestRun() {
        // no-op
    }

    @Override
    public void finishedTesting() {
        // no-op
    }

    @Override
    public void finishedTestRun(boolean allOK) {
        // no-op
    }

    @Override
    public void error(String msg) {
        // no-op
    }
}

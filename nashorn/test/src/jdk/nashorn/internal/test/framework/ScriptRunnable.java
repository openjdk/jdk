/*
 * Copyright (c) 2010, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.internal.test.framework;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jdk.nashorn.tools.Shell;
import org.testng.Assert;
import org.testng.ITest;
import org.testng.annotations.Test;

/**
 * Compiles a single JavaScript script source file and executes the resulting
 * class. Optionally, output from running the script is compared against the
 * corresponding .EXPECTED file.
 */
@SuppressWarnings("javadoc")
public final class ScriptRunnable extends AbstractScriptRunnable implements ITest {
    public ScriptRunnable(final String framework, final File testFile, final List<String> engineOptions, final Map<String, String> testOptions,  final List<String> scriptArguments) {
        super(framework, testFile, engineOptions, testOptions, scriptArguments);

        if (this.shouldRun) {
          // add --dump-on-error option always so that we can get detailed error msg.
          engineOptions.add("-doe");
        }
    }

    @Override
    public String getTestName() {
        return testFile.getAbsolutePath();
    }

    @Test
    @Override
    public void runTest() throws IOException {
        try {
            super.runTest();
        } catch(final AssertionError e) {
            throw new AssertionError("Failed executing test " + testFile, e);
        }
    }

    @Override
    protected void execute() {
        if (fork) {
            executeInNewProcess();
        } else {
            executeInThisProcess();
        }
    }

    // avoid direct System.out.println - use reporter to capture
    @Override
    protected void log(final String msg) {
        org.testng.Reporter.log(msg, true);
    }

    // throw Assert fail - but log as well so that user can see this at console
    @Override
    protected void fail(final String msg) {
        log(msg);
        Assert.fail(msg);
    }

    @Override
    protected void compile() throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        final List<String> args = getCompilerArgs();
        int errors;

        try {
            errors = evaluateScript(out, err, args.toArray(new String[0]));
        } catch (final AssertionError e) {
            final PrintWriter writer = new PrintWriter(err);
            e.printStackTrace(writer);
            writer.flush();
            errors = 1;
        }

        if (errors != 0 || checkCompilerMsg) {
            if (expectCompileFailure || checkCompilerMsg) {
                final PrintStream outputDest = new PrintStream(new FileOutputStream(errorFileName));
                TestHelper.dumpFile(outputDest, new StringReader(new String(err.toByteArray())));
                outputDest.println("--");
            } else {
                log(new String(err.toByteArray()));
            }

            if (errors != 0 && !expectCompileFailure) {
                fail(String.format("%d errors compiling %s", errors, testFile));
            }
            if (checkCompilerMsg) {
                compare(errorFileName, expectedFileName, true);
            }
        }

        if (expectCompileFailure && errors == 0) {
            fail(String.format("No errors encountered compiling negative test %s", testFile));
        }
    }

    private void executeInThisProcess() {
        final List<String> args = getRuntimeArgs();
        final File outputFileHandle = new File(outputFileName);
        final File errorFileHandle  = new File(errorFileName);

        try (OutputStream outputFile = new FileOutputStream(outputFileName); OutputStream errorFile = new FileOutputStream(errorFileName)) {
            final int errors = evaluateScript(outputFile, errorFile, args.toArray(new String[0]));

            if (errors != 0 || errorFileHandle.length() > 0) {
                if (expectRunFailure) {
                    return;
                }

                if (!ignoreStdError) {
                    if (outputFileHandle.length() > 0) {
                        TestHelper.dumpFile(outputFileHandle);
                    }
                    fail(TestHelper.fullContent(errorFileHandle));
                }
            }

            if (compare) {
                compare(outputFileName, expectedFileName, false);
            }
        } catch (final IOException e) {
            if (!expectRunFailure) {
                fail("Failure running test " + testFile + ": " + e.getMessage());
                // else success
            }
        }
    }

    private void executeInNewProcess() {

        final String separator = System.getProperty("file.separator");
        final List<String> cmd = new ArrayList<>();

        cmd.add(System.getProperty("java.home") + separator + "bin" + separator + "java");
        for (final String str : forkJVMOptions) {
            if(!str.isEmpty()) {
                cmd.add(str);
        }
        }
        cmd.add(Shell.class.getName());
        // now add the rest of the "in process" runtime arguments
        cmd.addAll(getRuntimeArgs());

        final File outputFileHandle = new File(outputFileName);
        final File errorFileHandle = new File(errorFileName);

        try {
            final ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectOutput(outputFileHandle);
            pb.redirectError(errorFileHandle);
            final Process process = pb.start();

            process.waitFor();

            if (errorFileHandle.length() > 0) {
                if (expectRunFailure) {
                    return;
                }
                if (!ignoreStdError) {
                    if (outputFileHandle.length() > 0) {
                        TestHelper.dumpFile(outputFileHandle);
                    }
                    fail(TestHelper.fullContent(errorFileHandle));
                }
            }

            if (compare) {
                compare(outputFileName, expectedFileName, false);
            }
        } catch (final IOException | InterruptedException e) {
            if (!expectRunFailure) {
                fail("Failure running test " + testFile + ": " + e.getMessage());
                // else success
            }
        }
    }

    private void compare(final String outputFileName0, final String expectedFileName0, final boolean compareCompilerMsg) throws IOException {
        final File expectedFile = new File(expectedFileName0);

        BufferedReader expected;
        if (expectedFile.exists()) {
            expected = new BufferedReader(new InputStreamReader(new FileInputStream(expectedFileName0)));
            // copy expected file overwriting existing file and preserving last
            // modified time of source
            try {
                Files.copy(FileSystems.getDefault().getPath(expectedFileName0),
                        FileSystems.getDefault().getPath(copyExpectedFileName),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
            } catch (final IOException ex) {
                fail("failed to copy expected " + expectedFileName + " to " + copyExpectedFileName + ": " + ex.getMessage());
            }
        } else {
            expected = new BufferedReader(new StringReader(""));
        }

        final BufferedReader actual = new BufferedReader(new InputStreamReader(new FileInputStream(outputFileName0)));
        compare(actual, expected, compareCompilerMsg);
    }
}

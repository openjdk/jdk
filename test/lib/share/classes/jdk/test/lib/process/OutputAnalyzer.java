/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.process;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OutputAnalyzer {

  private final String stdout;
  private final String stderr;
  private final int exitValue;

  /**
   * Create an OutputAnalyzer, a utility class for verifying output and exit
   * value from a Process
   *
   * @param process Process to analyze
   * @throws IOException If an I/O error occurs.
   */
  public OutputAnalyzer(Process process) throws IOException {
    OutputBuffer output = ProcessTools.getOutput(process);
    exitValue = process.exitValue();
    this.stdout = output.getStdout();
    this.stderr = output.getStderr();
  }

  /**
   * Create an OutputAnalyzer, a utility class for verifying output
   *
   * @param buf String buffer to analyze
   */
  public OutputAnalyzer(String buf) {
    this(buf, buf);
  }

  /**
   * Create an OutputAnalyzer, a utility class for verifying output
   *
   * @param stdout stdout buffer to analyze
   * @param stderr stderr buffer to analyze
   */
  public OutputAnalyzer(String stdout, String stderr) {
    this.stdout = stdout;
    this.stderr = stderr;
    exitValue = -1;
  }

  /**
   * Verify that the stdout contents of output buffer is empty
   *
   * @throws RuntimeException
   *             If stdout was not empty
   */
  public void stdoutShouldBeEmpty() {
    if (!getStdout().isEmpty()) {
      reportDiagnosticSummary();
      throw new RuntimeException("stdout was not empty");
    }
  }

  /**
   * Verify that the stderr contents of output buffer is empty
   *
   * @throws RuntimeException
   *             If stderr was not empty
   */
  public void stderrShouldBeEmpty() {
    if (!getStderr().isEmpty()) {
      reportDiagnosticSummary();
      throw new RuntimeException("stderr was not empty");
    }
  }

  /**
   * Verify that the stdout contents of output buffer is not empty
   *
   * @throws RuntimeException
   *             If stdout was empty
   */
  public void stdoutShouldNotBeEmpty() {
    if (getStdout().isEmpty()) {
      reportDiagnosticSummary();
      throw new RuntimeException("stdout was empty");
    }
  }

  /**
   * Verify that the stderr contents of output buffer is not empty
   *
   * @throws RuntimeException
   *             If stderr was empty
   */
  public void stderrShouldNotBeEmpty() {
    if (getStderr().isEmpty()) {
      reportDiagnosticSummary();
      throw new RuntimeException("stderr was empty");
    }
  }

    /**
   * Verify that the stdout and stderr contents of output buffer contains the string
   *
   * @param expectedString String that buffer should contain
   * @throws RuntimeException If the string was not found
   */
  public OutputAnalyzer shouldContain(String expectedString) {
    if (!stdout.contains(expectedString) && !stderr.contains(expectedString)) {
        reportDiagnosticSummary();
        throw new RuntimeException("'" + expectedString + "' missing from stdout/stderr \n");
    }
    return this;
  }

  /**
   * Verify that the stdout contents of output buffer contains the string
   *
   * @param expectedString String that buffer should contain
   * @throws RuntimeException If the string was not found
   */
  public OutputAnalyzer stdoutShouldContain(String expectedString) {
    if (!stdout.contains(expectedString)) {
        reportDiagnosticSummary();
        throw new RuntimeException("'" + expectedString + "' missing from stdout \n");
    }
    return this;
  }

  /**
   * Verify that the stderr contents of output buffer contains the string
   *
   * @param expectedString String that buffer should contain
   * @throws RuntimeException If the string was not found
   */
  public OutputAnalyzer stderrShouldContain(String expectedString) {
    if (!stderr.contains(expectedString)) {
        reportDiagnosticSummary();
        throw new RuntimeException("'" + expectedString + "' missing from stderr \n");
    }
    return this;
  }

  /**
   * Verify that the stdout and stderr contents of output buffer does not contain the string
   *
   * @param expectedString String that the buffer should not contain
   * @throws RuntimeException If the string was found
   */
  public OutputAnalyzer shouldNotContain(String notExpectedString) {
    if (stdout.contains(notExpectedString)) {
        reportDiagnosticSummary();
        throw new RuntimeException("'" + notExpectedString + "' found in stdout \n");
    }
    if (stderr.contains(notExpectedString)) {
        reportDiagnosticSummary();
        throw new RuntimeException("'" + notExpectedString + "' found in stderr \n");
    }
    return this;
  }

  /**
   * Verify that the stdout contents of output buffer does not contain the string
   *
   * @param expectedString String that the buffer should not contain
   * @throws RuntimeException If the string was found
   */
  public OutputAnalyzer stdoutShouldNotContain(String notExpectedString) {
    if (stdout.contains(notExpectedString)) {
        reportDiagnosticSummary();
        throw new RuntimeException("'" + notExpectedString + "' found in stdout \n");
    }
    return this;
  }

  /**
   * Verify that the stderr contents of output buffer does not contain the string
   *
   * @param expectedString String that the buffer should not contain
   * @throws RuntimeException If the string was found
   */
  public OutputAnalyzer stderrShouldNotContain(String notExpectedString) {
    if (stderr.contains(notExpectedString)) {
        reportDiagnosticSummary();
        throw new RuntimeException("'" + notExpectedString + "' found in stderr \n");
    }
    return this;
  }

  /**
   * Verify that the stdout and stderr contents of output buffer matches
   * the pattern
   *
   * @param pattern
   * @throws RuntimeException If the pattern was not found
   */
  public OutputAnalyzer shouldMatch(String pattern) {
      Matcher stdoutMatcher = Pattern.compile(pattern, Pattern.MULTILINE).matcher(stdout);
      Matcher stderrMatcher = Pattern.compile(pattern, Pattern.MULTILINE).matcher(stderr);
      if (!stdoutMatcher.find() && !stderrMatcher.find()) {
          reportDiagnosticSummary();
          throw new RuntimeException("'" + pattern
                + "' missing from stdout/stderr \n");
      }
      return this;
  }

  /**
   * Verify that the stdout contents of output buffer matches the
   * pattern
   *
   * @param pattern
   * @throws RuntimeException If the pattern was not found
   */
  public OutputAnalyzer stdoutShouldMatch(String pattern) {
      Matcher matcher = Pattern.compile(pattern, Pattern.MULTILINE).matcher(stdout);
      if (!matcher.find()) {
          reportDiagnosticSummary();
          throw new RuntimeException("'" + pattern
                + "' missing from stdout \n");
      }
      return this;
  }

  /**
   * Verify that the stderr contents of output buffer matches the
   * pattern
   *
   * @param pattern
   * @throws RuntimeException If the pattern was not found
   */
  public OutputAnalyzer stderrShouldMatch(String pattern) {
      Matcher matcher = Pattern.compile(pattern, Pattern.MULTILINE).matcher(stderr);
      if (!matcher.find()) {
          reportDiagnosticSummary();
          throw new RuntimeException("'" + pattern
                + "' missing from stderr \n");
      }
      return this;
  }

  /**
   * Verify that the stdout and stderr contents of output buffer does not
   * match the pattern
   *
   * @param pattern
   * @throws RuntimeException If the pattern was found
   */
  public OutputAnalyzer shouldNotMatch(String pattern) {
      Matcher matcher = Pattern.compile(pattern, Pattern.MULTILINE).matcher(stdout);
      if (matcher.find()) {
          reportDiagnosticSummary();
          throw new RuntimeException("'" + pattern
                  + "' found in stdout: '" + matcher.group() + "' \n");
      }
      matcher = Pattern.compile(pattern, Pattern.MULTILINE).matcher(stderr);
      if (matcher.find()) {
          reportDiagnosticSummary();
          throw new RuntimeException("'" + pattern
                  + "' found in stderr: '" + matcher.group() + "' \n");
      }
      return this;
  }

  /**
   * Verify that the stdout contents of output buffer does not match the
   * pattern
   *
   * @param pattern
   * @throws RuntimeException If the pattern was found
   */
  public OutputAnalyzer stdoutShouldNotMatch(String pattern) {
      Matcher matcher = Pattern.compile(pattern, Pattern.MULTILINE).matcher(stdout);
      if (matcher.find()) {
          reportDiagnosticSummary();
          throw new RuntimeException("'" + pattern
                  + "' found in stdout \n");
      }
      return this;
  }

  /**
   * Verify that the stderr contents of output buffer does not match the
   * pattern
   *
   * @param pattern
   * @throws RuntimeException If the pattern was found
   */
  public OutputAnalyzer stderrShouldNotMatch(String pattern) {
      Matcher matcher = Pattern.compile(pattern, Pattern.MULTILINE).matcher(stderr);
      if (matcher.find()) {
          reportDiagnosticSummary();
          throw new RuntimeException("'" + pattern
                  + "' found in stderr \n");
      }
      return this;
  }

  /**
   * Get the captured group of the first string matching the pattern.
   * stderr is searched before stdout.
   *
   * @param pattern The multi-line pattern to match
   * @param group The group to capture
   * @return The matched string or null if no match was found
   */
  public String firstMatch(String pattern, int group) {
    Matcher stderrMatcher = Pattern.compile(pattern, Pattern.MULTILINE).matcher(stderr);
    Matcher stdoutMatcher = Pattern.compile(pattern, Pattern.MULTILINE).matcher(stdout);
    if (stderrMatcher.find()) {
      return stderrMatcher.group(group);
    }
    if (stdoutMatcher.find()) {
      return stdoutMatcher.group(group);
    }
    return null;
  }

  /**
   * Get the first string matching the pattern.
   * stderr is searched before stdout.
   *
   * @param pattern The multi-line pattern to match
   * @return The matched string or null if no match was found
   */
  public String firstMatch(String pattern) {
    return firstMatch(pattern, 0);
  }

  /**
   * Verify the exit value of the process
   *
   * @param expectedExitValue Expected exit value from process
   * @throws RuntimeException If the exit value from the process did not match the expected value
   */
  public OutputAnalyzer shouldHaveExitValue(int expectedExitValue) {
      if (getExitValue() != expectedExitValue) {
          reportDiagnosticSummary();
          throw new RuntimeException("Expected to get exit value of ["
                  + expectedExitValue + "]\n");
      }
      return this;
  }


  /**
   * Report summary that will help to diagnose the problem
   * Currently includes:
   *  - standard input produced by the process under test
   *  - standard output
   *  - exit code
   *  Note: the command line is printed by the ProcessTools
   */
    private void reportDiagnosticSummary() {
        String msg =
            " stdout: [" + stdout + "];\n" +
            " stderr: [" + stderr + "]\n" +
            " exitValue = " + getExitValue() + "\n";

        System.err.println(msg);
    }


  /**
   * Get the contents of the output buffer (stdout and stderr)
   *
   * @return Content of the output buffer
   */
  public String getOutput() {
    return stdout + stderr;
  }

  /**
   * Get the contents of the stdout buffer
   *
   * @return Content of the stdout buffer
   */
  public String getStdout() {
    return stdout;
  }

  /**
   * Get the contents of the stderr buffer
   *
   * @return Content of the stderr buffer
   */
  public String getStderr() {
    return stderr;
  }

  /**
   * Get the process exit value
   *
   * @return Process exit value
   */
  public int getExitValue() {
    return exitValue;
  }

  /**
   * Get the contents of the output buffer (stdout and stderr) as list of strings.
   * Output will be split by newlines.
   *
   * @return Contents of the output buffer as list of strings
   */
  public List<String> asLines() {
    return asLines(getOutput());
  }

  private List<String> asLines(String buffer) {
    return Arrays.asList(buffer.split("(\\r\\n|\\n|\\r)"));
  }
}

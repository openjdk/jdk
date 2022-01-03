/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.File;
import java.io.ObjectInputFilter;

/*
 * @test
 * @bug 8269336
 * @summary Test that an invalid pattern value for the jdk.serialFilter system property causes an
 * exception to be thrown in the class initialization of java.io.ObjectInputFilter.Config class
 * @library /test/lib
 * @run driver InvalidGlobalFilterTest
 */
public class InvalidGlobalFilterTest {
    private static final String serialPropName = "jdk.serialFilter";

    /**
     * Launches multiple instances of a Java program by passing each instance an invalid value
     * for the {@code jdk.serialFilter} system property. The launched program then triggers the
     * class initialization of {@code ObjectInputFilter.Config} class to have it parse the (invalid)
     * value of the system property. The launched program is expected to propagate the exception
     * raised by the {@code ObjectInputFilter.Config} initialization and the test asserts that the
     * launched program did indeed fail with this expected exception.
     */
    public static void main(final String[] args) throws Exception {
        final String[] invalidPatterns = {".*", ".**", "!", "/java.util.Hashtable", "java.base/", "/"};
        for (final String invalidPattern : invalidPatterns) {
            final ProcessBuilder processBuilder = ProcessTools.createJavaProcessBuilder(
                    "-D" + serialPropName + "=" + invalidPattern,
                    "-Djava.util.logging.config.file=" + System.getProperty("test.src")
                            + File.separator + "logging.properties",
                    ObjectInputFilterConfigLoader.class.getName());
            // launch a process by passing it an invalid value for -Djdk.serialFilter
            final OutputAnalyzer outputAnalyzer = ProcessTools.executeProcess(processBuilder);
            try {
                // we expect the JVM launch to fail
                outputAnalyzer.shouldNotHaveExitValue(0);
                // do an additional check to be sure it failed for the right reason
                outputAnalyzer.stderrShouldContain("java.lang.ExceptionInInitializerError");
            } finally {
                // fail or pass, we print out the generated output from the launched program
                // for any debugging
                System.err.println("Diagnostics from process " + outputAnalyzer.pid() + ":");
                // print out any stdout/err that was generated in the launched program
                outputAnalyzer.reportDiagnosticSummary();
            }
        }
    }

    // A main() class which just triggers the class initialization of ObjectInputFilter.Config
    private static final class ObjectInputFilterConfigLoader {

        public static void main(final String[] args) throws Exception {
            System.out.println("JVM was launched with " + serialPropName
                    + " system property set to " + System.getProperty(serialPropName));
            // this call is expected to fail and we aren't interested in the result.
            // we just let the exception propagate out of this call and fail the
            // launched program. The test which launched this main, then asserts
            // that the exception was indeed thrown.
            ObjectInputFilter.Config.getSerialFilter();
        }
    }
}

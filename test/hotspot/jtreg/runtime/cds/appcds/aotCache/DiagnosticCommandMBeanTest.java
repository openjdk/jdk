/*
 * Copyright (c) 2025, Microsoft, Inc. All rights reserved.
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
 *
 */


/*
 * @test
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.cds.write.archived.java.heap
 * @summary Sanity test for DiagnosticCommand MBean ability to invoke AOT.end_recording
 * @library /test/jdk/lib/testlibrary /test/lib
 *          /test/hotspot/jtreg/runtime/cds/appcds/aotCache/test-classes
 * @build DiagnosticCommandMBeanTest
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar DiagnosticCommandMBeanApp
 * @run driver DiagnosticCommandMBeanTest
 */

import java.lang.management.ManagementFactory;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.ReflectionException;
import javax.management.MalformedObjectNameException;

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class DiagnosticCommandMBeanTest {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = "DiagnosticCommandMBeanApp";
    public static void main(String[] args) throws Exception {
        Tester tester = new Tester();
        tester.runAOTWorkflow();
    }

    static class Tester extends CDSAppTester {
        public Tester() {
            super(mainClass);
        }

        @Override
        public String classpath(RunMode runMode) {
            return appJar;
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            return new String[] {
               "-Xlog:cds+class=trace",
                "--add-modules=jdk.management"
            };
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                mainClass, runMode.name()
            };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) {
            var name = runMode.name();
            if (runMode.isApplicationExecuted()) {
                if(runMode == RunMode.TRAINING) {
                    out.shouldContain("Hello Leyden " + name);
                    out.shouldContain("AOT.end_recording invoked successfully");
                    out.shouldContain("Successfully stopped recording");
                } else if (runMode == RunMode.ASSEMBLY) {
                    out.shouldNotContain("Hello Leyden ");
                } else if (runMode == RunMode.PRODUCTION) {
                    out.shouldContain("Hello Leyden " + name);
                    out.shouldContain("AOT.end_recording invoked successfully");
                    out.shouldContain("Failed to stop recording");
                }
                out.shouldNotContain("Exception occurred!");
                out.shouldHaveExitValue(0);
            }
        }
    }
}

class DiagnosticCommandMBeanApp {
    public static void main(String[] args) {
        System.out.println("Hello Leyden " + args[0]);
       /*
        * The following code is based on: docs/api/jdk.management/com/sun/management/DiagnosticCommandMBean.html
        *
        * Copied from the documentation for reference:
        *
        * ... The DiagnosticCommandMBean is generated at runtime and is subject to modifications during the lifetime of
        * the Java virtual machine. A diagnostic command is represented as an operation of the DiagnosticCommandMBean
        * interface. Each diagnostic command has:
        *
        *    - the diagnostic command name which is the name being referenced in the HotSpot Virtual Machine
        *    - the MBean operation name which is the name generated for the diagnostic command operation invocation. The
        *      MBean operation name is implementation dependent
        *
        * The recommended way to transform a diagnostic command name into a MBean operation name is as follows:
        *
        *    - All characters from the first one to the first dot are set to be lower-case characters
        *    - Every dot or underline character is removed and the following character is set to be an upper-case character
        *    - All other characters are copied without modification
        *
        * A diagnostic command may or may not support options or arguments. All the operations return String and
        * either take no parameter for operations that do not support any option or argument, or take a String[]
        * parameter for operations that support at least one option or argument. Each option or argument must be stored in
        * a single String.
        */
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ObjectName diagName = new ObjectName("com.sun.management:type=DiagnosticCommand");

            // The DiagnosticCommand MBean operations expect a String array parameter for command arguments
            // Even though AOT.end_recording doesn't need any arguments, you still need to pass an empty String array
            // The MBean framework requires you to specify both the parameters and their types (signatures)
            Object[] params = { new String[0] };
            String[] signature = { "[Ljava.lang.String;" };

            // The JCmd AOT.end_recording is invoked using 'aotEndRecording'
            String result = (String) server.invoke(diagName, "aotEndRecording", params, signature);

            // The result is the string output from the command
            System.out.println("AOT.end_recording invoked successfully");
            if (result.contains("Recording ended successfully")) {
                System.out.println("Successfully stopped recording");
            } else {
                System.out.println("Failed to stop recording");
            }
        } catch (MBeanException e) {
            System.out.println("MBeanException occurred!");
        } catch (ReflectionException e) {
            System.out.println("ReflectionException occurred!");
        } catch (MalformedObjectNameException e) {
            System.out.println("MalformedObjectNameException occurred!");
        } catch (InstanceNotFoundException e) {
            System.out.println("InstanceNotFoundException occurred!");
        }
    }
}
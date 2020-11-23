/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

/*
 * @test
 * @bug 8033441
 * @summary Test to ensure that line numbers are now present with the -XX:+PrintOptoAssembly command line option
 *
 * @requires vm.compiler2.enabled & vm.debug == true
 *
 * @library /compiler/patches /test/lib
 * @run main/othervm compiler.arguments.TestPrintOptoAssemblyLineNumbers
*/

package compiler.arguments;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestPrintOptoAssemblyLineNumbers {
    public static void main(String[] args) throws Throwable {
        // create subprocess to run some code with -XX:+PrintOptoAssembly enabled
        String[] procArgs = new String[]{
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:-TieredCompilation",
            "-XX:+PrintOptoAssembly",
            "compiler.arguments.TestPrintOptoAssemblyLineNumbers$CheckC2OptoAssembly"
            };

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(procArgs);
        String output = new OutputAnalyzer(pb.start()).getOutput();

        if(output.contains("TestPrintOptoAssemblyLineNumbers$CheckC2OptoAssembly::main @ bci:11")){
            // if C2 optimizer invoked ensure output includes line numbers:
            Asserts.assertTrue(output.contains("TestPrintOptoAssemblyLineNumbers$CheckC2OptoAssembly::main @ bci:11 (line 68)"));
        }
    }

    public static class CheckC2OptoAssembly{ // contents of this class serves to just invoke C2
        public static boolean foo(String arg){
            return arg.contains("45");
        }

        public static void main(String[] args){
            int count = 0;
            for(int x = 0; x < 200_000; x++){
                if(foo("something" + x)){ // <- test expects this line of code to be on line 68
                    count += 1;
                }
            }
            System.out.println("count: " + count);
        }
    }
}

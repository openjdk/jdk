/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @test CheckCompileCommandOption
 * @summary Checks parsing of -XX:CompileCommand=option
 * @bug 8055286 8056964 8059847 8069035
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver compiler.oracle.CheckCompileCommandOption
 */

package compiler.oracle;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

import java.io.File;

public class CheckCompileCommandOption {

    // Currently, two types of trailing options can be used with
    // -XX:CompileCommand=option
    //
    // (1) CompileCommand=option,Klass::method,flag
    // (2) CompileCommand=option,Klass::method,type,flag,value
    //
    // Type (1) is used to enable a boolean flag for a method.
    //
    // Type (2) is used to support flags with a value. Values can
    // have the the following types: intx, uintx, bool, ccstr,
    // ccstrlist, and double.

    private static final String[][] FILE_ARGUMENTS = {
        {
            "-XX:CompileCommandFile=" + new File(System.getProperty("test.src", "."), "command1.txt"),
            "-version"
        },
        {
            "-XX:CompileCommandFile=" + new File(System.getProperty("test.src", "."), "command2.txt"),
            "-version"
        }
    };

    private static final String[][] FILE_EXPECTED_OUTPUT = {
        {
            "CompileCommand: option com/oracle/Test.test bool MyBoolOption1 = true",
            "CompileCommand: option com/oracle/Test.test bool MyBoolOption2 = true",
            "CompileCommand: option com/oracle/Test.test bool MyBoolOption3 = true",
            "CompileCommand: option com/oracle/Test.test bool MyBoolOption5 = true",
            "CompileCommand: option com/oracle/Test.test bool MyBoolOption6 = true",
            "CompileCommand: option com/oracle/Test.test bool MyBoolOption7 = true",
            "CompileCommand: option com/oracle/Test.test bool MyBoolOption8 = true",
            "CompileCommand: option com/oracle/Test.test(I) bool MyBoolOption9 = true",
            "CompileCommand: option com/oracle/Test.test(I) bool MyBoolOption10 = true",
            "CompileCommand: option com/oracle/Test.test(I) bool MyBoolOption11 = true",
            "CompileCommand: option com/oracle/Test.test(I) bool MyBoolOption13 = true",
            "CompileCommand: option com/oracle/Test.test(I) bool MyBoolOption14 = true",
            "CompileCommand: option com/oracle/Test.test(I) bool MyBoolOption15 = true",
            "CompileCommand: option com/oracle/Test.test(I) bool MyBoolOption16 = true"
        },
        {
            "CompileCommand: option Test.test const char* MyListOption = '_foo _bar'",
            "CompileCommand: option Test.test const char* MyStrOption = '_foo'",
            "CompileCommand: option Test.test bool MyBoolOption = false",
            "CompileCommand: option Test.test intx MyIntxOption = -1",
            "CompileCommand: option Test.test uintx MyUintxOption = 1",
            "CompileCommand: option Test.test bool MyFlag = true",
            "CompileCommand: option Test.test double MyDoubleOption = 1.123000"
        }
    };

    private static final String[][] TYPE_1_ARGUMENTS = {
        {
            "-XX:CompileCommand=option,com/oracle/Test.test,MyBoolOption1",
            "-XX:CompileCommand=option,com/oracle/Test,test,MyBoolOption2",
            "-XX:CompileCommand=option,com.oracle.Test::test,MyBoolOption3",
            "-XX:CompileCommand=option,com/oracle/Test.test,MyBoolOption5,MyBoolOption6",
            "-XX:CompileCommand=option,com/oracle/Test,test,MyBoolOption7,MyBoolOption8",
            "-version"
        }
    };

    private static final String[][] TYPE_1_EXPECTED_OUTPUTS = {
        {
            "CompileCommand: option com/oracle/Test.test bool MyBoolOption1 = true",
            "CompileCommand: option com/oracle/Test.test bool MyBoolOption2 = true",
            "CompileCommand: option com/oracle/Test.test bool MyBoolOption3 = true",
            "CompileCommand: option com/oracle/Test.test bool MyBoolOption5 = true",
            "CompileCommand: option com/oracle/Test.test bool MyBoolOption6 = true",
            "CompileCommand: option com/oracle/Test.test bool MyBoolOption7 = true",
            "CompileCommand: option com/oracle/Test.test bool MyBoolOption8 = true"
        }
    };

    private static final String[][] TYPE_2_ARGUMENTS = {
        {
            "-XX:CompileCommand=option,Test::test,ccstrlist,MyListOption,_foo,_bar",
            "-XX:CompileCommand=option,Test::test,ccstr,MyStrOption,_foo",
            "-XX:CompileCommand=option,Test::test,bool,MyBoolOption,false",
            "-XX:CompileCommand=option,Test::test,intx,MyIntxOption,-1",
            "-XX:CompileCommand=option,Test::test,uintx,MyUintxOption,1",
            "-XX:CompileCommand=option,Test::test,MyFlag",
            "-XX:CompileCommand=option,Test::test,double,MyDoubleOption1,1.123",
            "-XX:CompileCommand=option,Test.test,double,MyDoubleOption2,1.123",
            "-XX:CompileCommand=option,Test::test,bool,MyBoolOptionX,false,intx,MyIntxOptionX,-1,uintx,MyUintxOptionX,1,MyFlagX,double,MyDoubleOptionX,1.123",
            "-version"
        }
    };

    private static final String[][] TYPE_2_EXPECTED_OUTPUTS = {
        {
            "CompileCommand: option Test.test const char* MyListOption = '_foo _bar'",
            "CompileCommand: option Test.test const char* MyStrOption = '_foo'",
            "CompileCommand: option Test.test bool MyBoolOption = false",
            "CompileCommand: option Test.test intx MyIntxOption = -1",
            "CompileCommand: option Test.test uintx MyUintxOption = 1",
            "CompileCommand: option Test.test bool MyFlag = true",
            "CompileCommand: option Test.test double MyDoubleOption1 = 1.123000",
            "CompileCommand: option Test.test double MyDoubleOption2 = 1.123000",
            "CompileCommand: option Test.test bool MyBoolOptionX = false",
            "CompileCommand: option Test.test intx MyIntxOptionX = -1",
            "CompileCommand: option Test.test uintx MyUintxOptionX = 1",
            "CompileCommand: option Test.test bool MyFlagX = true",
            "CompileCommand: option Test.test double MyDoubleOptionX = 1.123000",
        }
    };

    private static final String[][] TYPE_2_INVALID_ARGUMENTS = {
        {
            // bool flag name missing
            "-XX:CompileCommand=option,Test::test,bool",
            "-version"
        },
        {
            // bool flag value missing
            "-XX:CompileCommand=option,Test::test,bool,MyBoolOption",
            "-version"
        },
        {
            // wrong value for bool flag
            "-XX:CompileCommand=option,Test::test,bool,MyBoolOption,100",
            "-version"
        },
        {
            // intx flag name missing
            "-XX:CompileCommand=option,Test::test,bool,MyBoolOption,false,intx",
            "-version"
        },
        {
            // intx flag value missing
            "-XX:CompileCommand=option,Test::test,bool,MyBoolOption,false,intx,MyIntOption",
            "-version"
        },
        {
            // wrong value for intx flag
            "-XX:CompileCommand=option,Test::test,bool,MyBoolOption,false,intx,MyIntOption,true",
            "-version"
        },
        {
            // wrong value for flag double flag
            "-XX:CompileCommand=option,Test::test,double,MyDoubleOption,1",
            "-version"
        }
    };

    private static void verifyValidOption(String[] arguments, String[] expected_outputs) throws Exception {
        ProcessBuilder pb;
        OutputAnalyzer out;

        pb = ProcessTools.createJavaProcessBuilder(arguments);
        out = new OutputAnalyzer(pb.start());

        for (String expected_output : expected_outputs) {
            out.shouldContain(expected_output);
        }

        out.shouldNotContain("CompileCommand: An error occurred during parsing");
        out.shouldHaveExitValue(0);
    }

    private static void verifyInvalidOption(String[] arguments) throws Exception {
        ProcessBuilder pb;
        OutputAnalyzer out;

        pb = ProcessTools.createJavaProcessBuilder(arguments);
        out = new OutputAnalyzer(pb.start());

        out.shouldContain("CompileCommand: An error occurred during parsing");
        out.shouldHaveExitValue(0);
    }

    public static void main(String[] args) throws Exception {

        if (TYPE_1_ARGUMENTS.length != TYPE_1_EXPECTED_OUTPUTS.length) {
            throw new RuntimeException("Test is set up incorrectly: length of arguments and expected outputs for type (1) options does not match.");
        }

        if (TYPE_2_ARGUMENTS.length != TYPE_2_EXPECTED_OUTPUTS.length) {
            throw new RuntimeException("Test is set up incorrectly: length of arguments and expected outputs for type (2) options does not match.");
        }

        // Check if type (1) options are parsed correctly
        for (int i = 0; i < TYPE_1_ARGUMENTS.length; i++) {
            verifyValidOption(TYPE_1_ARGUMENTS[i], TYPE_1_EXPECTED_OUTPUTS[i]);
        }

        // Check if type (2) options are parsed correctly
        for (int i = 0; i < TYPE_2_ARGUMENTS.length; i++) {
            verifyValidOption(TYPE_2_ARGUMENTS[i], TYPE_2_EXPECTED_OUTPUTS[i]);
        }

        // Check if error is reported for invalid type (2) options
        // (flags with type information specified)
        for (String[] arguments: TYPE_2_INVALID_ARGUMENTS) {
            verifyInvalidOption(arguments);
        }

        // Check if commands in command file are parsed correctly
        for (int i = 0; i < FILE_ARGUMENTS.length; i++) {
            verifyValidOption(FILE_ARGUMENTS[i], FILE_EXPECTED_OUTPUT[i]);
        }
    }
}

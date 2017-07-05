/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.java.testlibrary.*;

/*
 * @test CheckCompileCommandOption
 * @bug 8055286 8056964 8059847
 * @summary "Checks parsing of -XX:+CompileCommand=option"
 * @library /testlibrary
 * @run main CheckCompileCommandOption
 */

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

    private static final String[][] TYPE_1_ARGUMENTS = {
        {
            "-XX:CompileCommand=option,com/oracle/Test.test,MyBoolOption1",
            "-XX:CompileCommand=option,com/oracle/Test,test,MyBoolOption2",
            "-XX:CompileCommand=option,com.oracle.Test::test,MyBoolOption3",
            "-XX:CompileCommand=option,com/oracle/Test::test,MyBoolOption4",
            "-version"
        },
        {
            "-XX:CompileCommand=option,com/oracle/Test.test,MyBoolOption1,MyBoolOption2",
            "-version"
        },
        {
            "-XX:CompileCommand=option,com/oracle/Test,test,MyBoolOption1,MyBoolOption2",
            "-version"
        }
    };

    private static final String[][] TYPE_1_EXPECTED_OUTPUTS = {
        {
            "CompilerOracle: option com/oracle/Test.test bool MyBoolOption1 = true",
            "CompilerOracle: option com/oracle/Test.test bool MyBoolOption2 = true",
            "CompilerOracle: option com/oracle/Test.test bool MyBoolOption3 = true",
            "CompilerOracle: option com/oracle/Test.test bool MyBoolOption4 = true"
        },
        {
            "CompilerOracle: option com/oracle/Test.test bool MyBoolOption1 = true",
            "CompilerOracle: option com/oracle/Test.test bool MyBoolOption2 = true",
        },
        {
            "CompilerOracle: option com/oracle/Test.test bool MyBoolOption1 = true",
            "CompilerOracle: option com/oracle/Test.test bool MyBoolOption2 = true",
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
            "-XX:CompileCommand=option,Test::test,double,MyDoubleOption,1.123",
            "-version"
        },
        {
            "-XX:CompileCommand=option,Test.test,double,MyDoubleOption,1.123",
            "-version"
        },
        {
            "-XX:CompileCommand=option,Test::test,bool,MyBoolOption,false,intx,MyIntxOption,-1,uintx,MyUintxOption,1,MyFlag,double,MyDoubleOption,1.123",
            "-version"
        }
    };

    private static final String[][] TYPE_2_EXPECTED_OUTPUTS = {
        {
            "CompilerOracle: option Test.test const char* MyListOption = '_foo _bar'",
            "CompilerOracle: option Test.test const char* MyStrOption = '_foo'",
            "CompilerOracle: option Test.test bool MyBoolOption = false",
            "CompilerOracle: option Test.test intx MyIntxOption = -1",
            "CompilerOracle: option Test.test uintx MyUintxOption = 1",
            "CompilerOracle: option Test.test bool MyFlag = true",
            "CompilerOracle: option Test.test double MyDoubleOption = 1.123000"
        },
        {
            "CompilerOracle: option Test.test double MyDoubleOption = 1.123000"
        },
        {
            "CompilerOracle: option Test.test bool MyBoolOption = false",
            "CompilerOracle: option Test.test intx MyIntxOption = -1",
            "CompilerOracle: option Test.test uintx MyUintxOption = 1",
            "CompilerOracle: option Test.test bool MyFlag = true",
            "CompilerOracle: option Test.test double MyDoubleOption = 1.123000",
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

        out.shouldNotContain("CompilerOracle: unrecognized line");
        out.shouldHaveExitValue(0);
    }

    private static void verifyInvalidOption(String[] arguments) throws Exception {
        ProcessBuilder pb;
        OutputAnalyzer out;

        pb = ProcessTools.createJavaProcessBuilder(arguments);
        out = new OutputAnalyzer(pb.start());

        out.shouldContain("CompilerOracle: unrecognized line");
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
    }
}

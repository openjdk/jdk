/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Tests the format checking of LF_RESOLVE in classlist.
 *
 * @requires vm.cds
 * @library /test/lib
 * @compile test-classes/Hello.java
 * @run driver DumpClassListWithLF
 */

public class DumpClassListWithLF extends ClassListFormatBase {
    static final String MESSAGE_OK = "Replaced class java/lang/invoke/DirectMethodHandle$Holder";
    static final String MESSAGE_NOT_OK = "Failed call to java/lang/invoke/GenerateJLIClassesHelper.cdsGenerateHolderClasses";

    public static void main(String[] args) throws Throwable {

        String appJar = JarBuilder.getOrCreateHelloJar();
        //
        // Note the class regeneration via java/lang/invoke/GenerateJLIClassesHelper.cdsGenerateHolderClasses(String[] lines)
        // Whether the regeneration successes or fails, the dump should pass. Only the message can be checked for result.
        //
        // 1. The two lines are copied from build default_jli.txt which will be add to class generating list as additional (besides the default)
        //  classlist), but will be ignored since they will be added to the (methodType) set which already contain them.
        dumpShouldPass(
            "TESTCASE 1: With correct output format",
            appJar, classlist(
                "Hello",
                "@lambda-form-invoker [LF_RESOLVE] java.lang.invoke.DirectMethodHandle$Holder invokeStatic L7_L",
                "@lambda-form-invoker [LF_RESOLVE] java.lang.invoke.DirectMethodHandle$Holder invokeStatic LL_I"),
                MESSAGE_OK);

        // 2. The two lines with incorrect format of function signitures lead regeneration of holder class failed.
        dumpShouldPass(
            "TESTCASE 2: With incorrect signature",
            appJar, classlist(
                "Hello",
                "@lambda-form-invoker [LF_RESOLVE] java.lang.invoke.DirectMethodHandle$Holder invokeStatic L7_L-XXX",
                "@lambda-form-invoker [LF_RESOLVE] java.lang.invoke.DirectMethodHandle$Holder invokeStatic LL_I-YYY"),
                MESSAGE_NOT_OK);
        // 3. The two lines with arbitrary invoke names is OK, also ending with any word is OK.
        dumpShouldPass(
            "TESTCASE 3: With incorrect invoke names is OK",
            appJar, classlist(
                "Hello",
                "@lambda-form-invoker [LF_RESOLVE] java.lang.invoke.DirectMethodHandle$Holder invokeNothing  L7_L (anyword)",
                "@lambda-form-invoker [LF_RESOLVE] java.lang.invoke.DirectMethodHandle$Holder invokeNothing  LL_I anyword"),
                MESSAGE_OK);
        // 4. The two lines with non existed class name, since only 4 holder classes recognizable, all other names will be ignored.
        dumpShouldPass(
            "TESTCASE 4: With incorrect class name will be ignored",
            appJar, classlist(
                "Hello",
                "@lambda-form-invoker [LF_RESOLVE] my.nonexist.package.MyNonExistClassName$holder invokeStatic  L7_L",
                "@lambda-form-invoker [LF_RESOLVE] my.nonexist.package.MyNonExistClassName$holder invokeStatic  LL_I"),
                MESSAGE_OK);
        // 5. The two lines with worng LF format
        dumpShouldPass(
            "TESTCASE 5: With incorrect LF format, the line will be ignored",
            appJar, classlist(
                "Hello",
                "@lambda-form-invoker [LF_XYRESOLVE] java.lang.invoke.DirectMethodHandle$Holder invokeStatic  L7_L (any)",
                "@lambda-form-invoker [LF_XYRESOLVE] java.lang.invoke.DirectMethodHandle$Holder invokeStatic  LL_I (any)"),
                MESSAGE_OK);
    }
}

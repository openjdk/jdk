/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug      7195131
 * @author   sogoel
 * @summary  Combo test for all possible combinations for Target values
 * @ignore   8008339 Test TargetAnnoCombo.java is broken
 * @build    Helper
 * @compile  TargetAnnoCombo.java TestCaseGenerator.java
 * @run main TargetAnnoCombo
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;

/*
 * TargetAnnoCombo gets a list of test case numbers using TestCaseGenerator.
 * For each of the test case number, @Target sets for base and container annotations
 * are determined, source files are generated, compiled, and the result is verified
 * based on if the @Target set for base and container is a positive or negative combination.
 *
 * @Target sets for base and container annotations are determined using a bit mapping of
 * 10 ElementType enum constants defined in JDK8.
 *
 * Bit      Target value
 *  0  "ElementType.ANNOTATION_TYPE"
 *  1  "ElementType.CONSTRUCTOR"
 *  2  "ElementType.FIELD"
 *  3  "ElementType.LOCAL_VARIABLE"
 *  4  "ElementType.METHOD"
 *  5  "ElementType.TYPE"
 *  6  "ElementType.PARAMETER"
 *  7  "ElementType.PACKAGE"
 *  8  "ElementType.TYPE_USE"
 *  9  "ElementType.TYPE_PARAMETER"
 *
 * Group 1:
 * 20 bits mapping, representing a test case number, is used for all target set
 * combinations ( 0 to 1048575 ) including empty @Target sets => @Target({}).
 * From this 20 bits, 10 bits are for base followed by 10 bits for container
 * where each bit maps to an ElementType enum constant defined in JDK8.
 *
 * Examples:
 * Test case number: 4, binary: 100 => container=100, base=[], container=["ElementType.FIELD"]
 * Test case number: 1003575, binary: 11110101000000110111 => base=1111010100, container=0000110111;
 *                   base=["ElementType.PARAMETER", "ElementType.TYPE_USE", "ElementType.METHOD", "ElementType.FIELD", "ElementType.PACKAGE", "ElementType.TYPE_PARAMETER"],
 *                   container=["ElementType.TYPE", "ElementType.METHOD", "ElementType.ANNOTATION_TYPE", "ElementType.CONSTRUCTOR", "ElementType.FIELD"]
 *
 * In the following groups, no @Target set is represented by null.
 * Group 2:
 * @Target is not defined on base.
 * Target sets for container are determined using the 10-bit binary number
 * resulting in 1024 test cases, mapping them to test case numbers from
 * 1048576 to (1048576 + 1023) => 1048576 to 1049599.
 *
 * Example:
 * Test case number: 1048587 => 1048587 - 1048576 = test case 11 in Group 2, binary: 1011 =>
 *                   base = null,
 *                   container = ["ElementType.ANNOTATION_TYPE","ElementType.CONSTRUCTOR","ElementType.LOCAL_VARIABLE"]
 *
 * Group 3:
 * @Target is not defined on container
 * Target sets for base are determined using the 10-bit binary number
 * resulting in 1024 test cases, mapping them to test case numbers from
 * 1049600 to (1049600 + 1023) => 1049600 to 1050623.
 *
 * Example:
 * Test case number: 1049708 => 1049708 - 1049600 = test case 108 in Group 3, binary: 1101100 =>
 *                   base = ["ElementType.FIELD", "ElementType.LOCAL_VARIABLE", "ElementType.TYPE", "ElementType.PARAMETER"],
 *                   container = null
 *
 * For the above group, test case number: 1049855 gives compiler error, JDK-8006547 filed
 *
 * Group 4:
 * @Target not defined for both base and container annotations.
 *
 * This is the last test and corresponds to test case number 1050624. base=null, container=null
 *
 * Examples to run this test:
 * 1. Run a specific test case number:
 *    ${JTREG} -DTestCaseNum=10782 -samevm -jdk:${JAVA_TEST} -reportDir ${REPORT} -workDir ${WORK} TargetAnnoCombo.java
 * 2. Run specific number of tests:
 *    ${JTREG} -DNumberOfTests=4 -samevm -jdk:${JAVA_TEST} -reportDir ${REPORT} -workDir ${WORK} TargetAnnoCombo.java
 * 3. Run specific number of tests with a seed:
 *    ${JTREG} -DNumberOfTests=4 -DTestSeed=-972894659 -samevm -jdk:${JAVA_TEST} -reportDir ${REPORT} -workDir ${WORK} TargetAnnoCombo.java
 * 4. Run tests in default mode (number of tests = 1000):
 *    ${JTREG} -DTestMode=DEFAULT -samevm -jdk:${JAVA_TEST} -reportDir ${REPORT} -workDir ${WORK} TargetAnnoCombo.java
 * 5. Run all tests (FULL mode):
 *    ${JTREG} -DTestMode=FULL -samevm -jdk:${JAVA_TEST} -reportDir ${REPORT} -workDir ${WORK} TargetAnnoCombo.java
 *
 */

public class TargetAnnoCombo {
    int errors = 0;
    static final String TESTPKG = "testpkg";
    /*
     *  Set it to true to get more debug information including base and
     *  container target sets for a given test case number
     */
    static final boolean DEBUG = false;

    // JDK 5/6/7/8 Targets
    static final String[] targetVals = {"ElementType.ANNOTATION_TYPE",
      "ElementType.CONSTRUCTOR", "ElementType.FIELD",
      "ElementType.LOCAL_VARIABLE", "ElementType.METHOD",
      "ElementType.TYPE", "ElementType.PARAMETER",
      "ElementType.PACKAGE", "ElementType.TYPE_USE",
      "ElementType.TYPE_PARAMETER"};

    // TYPE_USE and TYPE_PARAMETER (added in JDK8) are not part of default Target set
    static final int DEFAULT_TARGET_CNT = 8;

    public static void main(String args[]) throws Exception {

        /* maxTestNum = (base and container combinations of targetVals elems [0 - 1048575 combos])
         *              + (combinations where base or container has no Target [1024 combos])
         *              + (no -1 even though 1st test is number 0 as last test is where both
         *                 base and container have no target)
         */

        int maxTestNum = (int)Math.pow(2, 2*targetVals.length) + 2*(int)Math.pow(2, targetVals.length);
        TestCaseGenerator tcg = new TestCaseGenerator(maxTestNum);
        TargetAnnoCombo tac = new TargetAnnoCombo();

        int testCtr = 0;
        int testCase = -1;
        while ( (testCase=tcg.getNextTestCase()) != -1 ) {
            tac.executeTestCase(testCase, maxTestNum);
            testCtr++;
        }

        System.out.println("Total tests run: " + testCtr);
        if (tac.errors > 0)
            throw new Exception(tac.errors + " errors found");
    }

    /*
     * For given testCase, determine the base and container annotation Target sets,
     * get if testCase should compile, get test source file(s), get compilation result and verify.
     *
     */
    private void executeTestCase(int testCase, int maxTestNum) {

        // Determine base and container annotation Target sets for the testCase
        Set<String> baseAnnoTarget = null;
        Set<String> conAnnoTarget = null;

        //Number of base and container combinations [0 - 1048575 combos]
        int baseContCombos = (int)Math.pow(2, 2*targetVals.length);
        //Number of either base or container combinations when one of them has no @Target [1024 combos]
        int targetValsCombos = (int)Math.pow(2, targetVals.length);

        if (testCase >= baseContCombos) {
            //Base annotation do not have @Target
            if (testCase < baseContCombos + targetValsCombos) {
                baseAnnoTarget = null;
                conAnnoTarget = getSetFromBitVec(Integer.toBinaryString(testCase - baseContCombos));
            } else if (testCase < baseContCombos + 2*targetValsCombos) {
                //Container annotation do not have @Target
                baseAnnoTarget = getSetFromBitVec(Integer.toBinaryString(testCase - baseContCombos - targetValsCombos));
                conAnnoTarget = null;
            } else {
                //Both Base and Container annotation do not have @Target
                baseAnnoTarget = null;
                conAnnoTarget = null;
            }
        } else {
            //TestCase number is represented as 10-bits for base followed by container bits
            String bin = Integer.toBinaryString(testCase);
            String base="", cont=bin;
            if (bin.length() > targetVals.length){
                base = bin.substring(0, bin.length() - targetVals.length);
                cont = bin.substring(bin.length() - targetVals.length,bin.length());
            }
            baseAnnoTarget = getSetFromBitVec(base);
            conAnnoTarget = getSetFromBitVec(cont);
        }

        debugPrint("Test case number = " + testCase + " => binary = " + Integer.toBinaryString(testCase));
        debugPrint(" => baseAnnoTarget = " + baseAnnoTarget);
        debugPrint(" => containerAnnoTarget = " + conAnnoTarget);

        // Determine if a testCase should compile or not
        String className = "TC" + testCase;
        boolean shouldCompile = isValidSubSet(baseAnnoTarget, conAnnoTarget);

        // Get test source file(s)
        Iterable<? extends JavaFileObject> files = getFileList(className, baseAnnoTarget,
                conAnnoTarget, shouldCompile);

        // Get result of compiling test src file(s)
        boolean result = getCompileResult(className, shouldCompile, files);

        // List test src code if test fails
        if(!result) {
            System.out.println("FAIL: Test " + testCase);
            try {
                for (JavaFileObject f: files) {
                    System.out.println("File: " + f.getName() + "\n" + f.getCharContent(true));
                }
            } catch (IOException ioe) {
                System.out.println("Exception: " + ioe);
            }
        } else {
            debugPrint("PASS: Test " + testCase);
        }
    }

    // Get a Set<String> based on bits that are set to 1
    public Set<String> getSetFromBitVec(String bitVec) {
        Set<String> ret = new HashSet<>();
        char[] bit = bitVec.toCharArray();
        for (int i=bit.length-1, j=0; i>=0; i--, j++){
            if (bit[i] == '1') {
                ret.add(targetVals[j]);
            }
        }
        return ret;
    }

    // Compile the test source file(s) and return test result
    private boolean getCompileResult(String className, boolean shouldCompile,
            Iterable<? extends JavaFileObject> files) {

        DiagnosticCollector<JavaFileObject> diagnostics =
                new DiagnosticCollector<JavaFileObject>();
        Helper.compileCode(diagnostics, files);

        // Test case pass or fail
        boolean ok = false;

        String errMesg = "";
        int numDiags = diagnostics.getDiagnostics().size();

        if (numDiags == 0) {
            if (shouldCompile) {
                debugPrint("Test passed, compiled as expected.");
                ok = true;
            } else {
                errMesg = "Test failed, compiled unexpectedly.";
                ok = false;
            }
        } else {
            if (shouldCompile) {
                // did not compile
                errMesg = "Test failed, did not compile.";
                ok = false;
            } else {
                // Error in compilation as expected
                String expectedErrKey = "compiler.err.invalid.repeatable." +
                        "annotation.incompatible.target";
                for (Diagnostic<?> d : diagnostics.getDiagnostics()) {
                    if((d.getKind() == Diagnostic.Kind.ERROR) &&
                        d.getCode().contains(expectedErrKey)) {
                        // Error message as expected
                        debugPrint("Error message as expected.");
                        ok = true;
                        break;
                    } else {
                        // error message is incorrect
                        ok = false;
                    }
                }
                if (!ok) {
                    errMesg = "Incorrect error received when compiling " +
                        className + ", expected: " + expectedErrKey;
                }
            }
        }

        if(!ok) {
            error(errMesg);
            for (Diagnostic<?> d : diagnostics.getDiagnostics())
                System.out.println(" Diags: " + d);
        }
        return ok;
    }

    private void debugPrint(String string) {
        if(DEBUG)
            System.out.println(string);
    }

    // Create src code and corresponding JavaFileObjects
    private Iterable<? extends JavaFileObject> getFileList(String className,
            Set<String> baseAnnoTarget, Set<String> conAnnoTarget,
            boolean shouldCompile) {

        String srcContent = "";
        String pkgInfoContent = "";
        String template = Helper.template;
        String baseTarget = "", conTarget = "";

        String target = Helper.ContentVars.TARGET.getVal();
        if(baseAnnoTarget != null) {
            baseTarget = target.replace("#VAL", baseAnnoTarget.toString())
                                  .replace("[", "{").replace("]", "}");
        }
        if(conAnnoTarget != null) {
            conTarget = target.replace("#VAL", conAnnoTarget.toString())
                                 .replace("[", "{").replace("]", "}");
        }

        String annoData = Helper.ContentVars.IMPORTSTMTS.getVal() +
                          conTarget +
                          Helper.ContentVars.CONTAINER.getVal() +
                          baseTarget +
                          Helper.ContentVars.REPEATABLE.getVal() +
                          Helper.ContentVars.BASE.getVal();

        JavaFileObject pkgInfoFile = null;

        /*
         *  If shouldCompile = true and no @Target is specified for container annotation,
         *  then all 8 ElementType enum constants are applicable as targets for
         *  container annotation.
         */
        if(shouldCompile && conAnnoTarget == null) {
            //conAnnoTarget = new HashSet<String>(Arrays.asList(targetVals));
            conAnnoTarget = getDefaultTargetSet();
        }

        if(shouldCompile) {
            boolean isPkgCasePresent = new ArrayList<String>(conAnnoTarget).contains("ElementType.PACKAGE");
            String repeatableAnno = Helper.ContentVars.BASEANNO.getVal() + " " + Helper.ContentVars.BASEANNO.getVal();
            for(String s: conAnnoTarget) {
                s = s.replace("ElementType.","");
                String replaceStr = "/*"+s+"*/";
                if(s.equalsIgnoreCase("PACKAGE")) {
                    //Create packageInfo file
                    String pkgInfoName = TESTPKG + "." + "package-info";
                    pkgInfoContent = repeatableAnno + "\npackage " + TESTPKG + ";" + annoData;
                    pkgInfoFile = Helper.getFile(pkgInfoName, pkgInfoContent);
                } else {
                    template = template.replace(replaceStr, repeatableAnno);
                    //srcContent = template.replace("#ClassName",className);
                    if(!isPkgCasePresent) {
                        srcContent = template.replace("/*ANNODATA*/", annoData).replace("#ClassName",className);
                    } else {
                        replaceStr = "/*PACKAGE*/";
                        srcContent = template.replace(replaceStr, "package " + TESTPKG + ";")
                                     .replace("#ClassName", className);
                    }
                }
            }
        } else {
            // For invalid cases, compilation should fail at declaration site
            template = "class #ClassName {}";
            srcContent = annoData + template.replace("#ClassName",className);
        }
        JavaFileObject srcFile = Helper.getFile(className, srcContent);
        Iterable<? extends JavaFileObject> files = null;
        if(pkgInfoFile != null)
            files = Arrays.asList(pkgInfoFile,srcFile);
        else
            files = Arrays.asList(srcFile);
        return files;
    }

    private Set<String> getDefaultTargetSet() {
        Set<String> defaultSet = new HashSet<>();
        int ctr = 0;
        for(String s : targetVals) {
            if(ctr++ < DEFAULT_TARGET_CNT) {
                defaultSet.add(s);
            }
        }
        return defaultSet;
    }

    private boolean isValidSubSet(Set<String> baseAnnoTarget, Set<String> conAnnoTarget) {
        /*
         *  RULE 1: conAnnoTarget should be a subset of baseAnnoTarget
         *  RULE 2: For empty @Target ({}) - annotation cannot be applied anywhere
         *         - Empty sets for both is valid
         *         - Empty baseTarget set is invalid with non-empty conTarget set
         *         - Non-empty baseTarget set is valid with empty conTarget set
         *  RULE 3: For no @Target specified - annotation can be applied to any JDK 7 targets
         *         - No @Target for both is valid
         *         - No @Target for baseTarget set with @Target conTarget set is valid
         *         - @Target for baseTarget set with no @Target for conTarget is invalid
         */


        /* If baseAnno has no @Target, Foo can be either applied to @Target specified for container annotation
         * else will be applicable for all default targets if no @Target is present for container annotation.
         * In both cases, the set will be a valid set with no @Target for base annotation
         */
        if(baseAnnoTarget == null) {
            if(conAnnoTarget == null) return true;
            return !(conAnnoTarget.contains("ElementType.TYPE_USE") || conAnnoTarget.contains("ElementType.TYPE_PARAMETER"));
        }

        Set<String> tempBaseSet = new HashSet<>(baseAnnoTarget);
        // If BaseAnno has TYPE, then ANNOTATION_TYPE is allowed by default
        if(baseAnnoTarget.contains("ElementType.TYPE")) {
            tempBaseSet.add("ElementType.ANNOTATION_TYPE");
        }

        /*
         * If containerAnno has no @Target, only valid case if baseAnnoTarget has all targets defined
         * else invalid set
         */
        if(conAnnoTarget == null) {
            return (tempBaseSet.containsAll(getDefaultTargetSet()));
        }

        // At this point, neither conAnnoTarget or baseAnnoTarget are null
        if(conAnnoTarget.size() == 0) return true;

        // At this point, conAnnoTarget is non-empty
        if (baseAnnoTarget.size() == 0) return false;

        // At this point, neither conAnnoTarget or baseAnnoTarget are empty
        return tempBaseSet.containsAll(conAnnoTarget);
    }

    void error(String msg) {
        System.out.println("ERROR: " + msg);
        errors++;
    }

    // Lists the start and end range for the given set of target vals
    void showGroups() {
        //Group 1: All target set combinations ( 0 to 1048575 ) including empty @Target sets => @Target({})
        int grpEnd1 = (int)Math.pow(2, 2*targetVals.length) - 1;
        System.out.println("[Group 1]: 0 - " + grpEnd1);

        //Group 2: @Target not defined for base annotation ( 1048576 - 1049599 ).
        System.out.print("[Group 2]: " + (grpEnd1 + 1) + " - ");
        int grpEnd2 = grpEnd1 + 1 + (int)Math.pow(2, targetVals.length) - 1;
        System.out.println(grpEnd2);

        //Group 3: @Target not defined for container annotation ( 1049600 - 1050623 ).
        System.out.print("[Group 3]: " + (grpEnd2 + 1) + " - ");
        int grpEnd3 = grpEnd2 + 1 + (int)Math.pow(2, targetVals.length) - 1;
        System.out.println(grpEnd3);

        //Group 4: @Target not defined for both base and container annotations ( 1050624 ).
        System.out.println("[Group 4]: " + (grpEnd3 + 1));
    }
}

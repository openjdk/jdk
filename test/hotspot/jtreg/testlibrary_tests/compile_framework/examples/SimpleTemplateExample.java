/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Example test to use the Compile Framework with Templates
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver compile_framework.examples.SimpleTemplateExample
 */

package compile_framework.examples;

import static compiler.lib.test_generator.InputTemplate.INTEGER_VALUES;
import static compiler.lib.test_generator.InputTemplate.getRandomValue;
import static compiler.lib.test_generator.InputTemplate.getRandomValueAsString;
import static compiler.lib.test_generator.TemplateGenerator.*;

import compiler.lib.compile_framework.*;
import compiler.lib.test_generator.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * This test shows a simple compilation of java source code, and its invocation.
 */
public class SimpleTemplateExample {

    public static CodeSegment getTemplate() {
        /* TODO:
         * all defined functions should use uniqueId to avoid conflict
         * Nesting : we want to be able to nest CodeTemplate in another CodeTemplate e.g. at \{thing}
         *           this would require replacing conflicting variables e.g. $i with $i1 and $i2,
         *           and also replace \{init} from the inner CodeTemplate with a var $limit from outer CodeTemplate
         **/
        String imports =
            """
            """;
        String statics =
            """
            //InputTemplate1
            static long $lFld;
            static A $a = new A();
            static boolean $flag;
            static class A {
                int $i;
            }
            """;
        String call =
            """
            test_\\{uniqueId}();
            """;
        String method =
            """
            public static int test_\\{uniqueId}() {
                long $limit = $lFld;
                for (int $i =\\{init1}; $i < \\{limit}; $i \\{arithm1}= \\{stride1}) {
                    // Use stride > Integer.MAX_VALUE such that LongCountedLoopNode is not split further into loop nests.
                    for (long $j = \\{init2}; $j < $limit; $j \\{arithm2}=\\{stride2}) {
                        $a.$i += \\{val1}; // NullCheck with trap on false path -> reason to peel
                        \\{thing}
                        \\{template1}
                        if ($j > 0) { // After peeling: j > 0 always true -> loop folded away
                            \\{template2}
                            break;
                        }
                    }
                }
                return 10;
            }
            """;
        return new CodeSegment(statics, call, method, imports);
    }

    public static String getTemplate1(String variable) {
        String statics =
            """
            int $a =\\{val};
            long $b = \\{var};
            """;
        String method =
            """
            do {
                $a--;
                $b++;
            } while ($a > 0);
            """;
        String template = statics + method;
        String template_com = avoidConflict(template);
        Map<String, String> replacements = new HashMap<>();
        String val = getRandomValueAsString(INTEGER_VALUES);
        replacements.put("val", val);
        replacements.put("var", variable);
        return performReplacements(template_com, replacements);
    }

    public static String getTemplate2(String variable) {
        String statics =
            """
            int $x, $y;
            boolean $flag=\\{bool};
            """;
        String method =
            """
            int $a;
            if ($flag) {
                $a = \\{val1};
            } else {
                $a = \\{val2};
            }
            // y = 34; // Make it more interesting
            if ($a > \\{val2}) {
                $x = \\{val3};
            } else {
                $x = \\{val4};
            }
            """;
        String template = statics + method;
        String template_com = avoidConflict(template);
        Map<String, String> replacements = new HashMap<>();
        String val1 = getRandomValueAsString(INTEGER_VALUES);
        String val2 = getRandomValueAsString(INTEGER_VALUES);
        String val3 = getRandomValueAsString(INTEGER_VALUES);
        String val4 = getRandomValueAsString(INTEGER_VALUES);
        String bool = getRandomValue(new String[] { "false", "true" });
        replacements.put("val1", val1);
        replacements.put("val2", val2);
        replacements.put("val3", val3);
        replacements.put("val4", val4);
        replacements.put("bool", bool);
        return performReplacements(template_com, replacements);
    }

    public static Map<String, String> getRandomReplacements(int numTest) {
        String template_nes1 = getTemplate1("$j");
        String template_nes2 = getTemplate2("$i");
        Map<String, String> replacements = new HashMap<>();
        String init1 = getRandomValueAsString(INTEGER_VALUES);
        String init2 = getRandomValueAsString(INTEGER_VALUES);
        String limit = getRandomValueAsString(POSITIVE_INTEGER_VALUES);
        String val1 = getRandomValueAsString(INTEGER_VALUES);
        String stride1 = getRandomValueAsString(INTEGER_VALUES_NON_ZERO);
        String stride2 = getRandomValueAsString(INTEGER_VALUES_NON_ZERO);
        String arithm1 = getRandomValue(new String[] { "+", "-" });
        String arithm2 = getRandomValue(new String[] { "+", "-" });
        String thing = getRandomValue(
            new String[] { "", "synchronized (new Object()) { }" }
        );
        String uniqueId = String.valueOf(numTest);
        replacements.put("init1", init1);
        replacements.put("init2", init2);
        replacements.put("limit", limit);
        replacements.put("val1", val1);
        replacements.put("arithm1", arithm1);
        replacements.put("arithm2", arithm2);
        replacements.put("stride1", stride1);
        replacements.put("stride2", stride2);
        replacements.put("thing", thing);
        replacements.put("uniqueId", uniqueId);
        replacements.put("template1", template_nes1);
        replacements.put("template2", template_nes2);
        return replacements;
    }

    public static String[] getCompileFlags() {
        return new String[] { "-Xcomp", "-XX:-CreateCoredumpOnCrash" };
    }

    public int getNumberOfTests() {
        return 1;
    }

    public static int getNumberOfTestMethods() {
        return 10;
    }

    // Generate a source jasm file as String
    public static String generate() {
        // Retrieve compile flags specific to the input template

        // Retrieve the code segment template from the input template
        CodeSegment inputTemplate = getTemplate();

        // Generate replacement mappings for the test methods
        ArrayList<Map<String, String>> replacements = new ArrayList<>();
        // Loop to generate replacements for each test method
        for (int j = 0; j < getNumberOfTestMethods(); j++) {
            // Retrieve a random set of replacements and increment the unique ID
            replacements.add(getRandomReplacements(j));
        }

        return generateJavaCode(inputTemplate, replacements, 42);
    }

    public static void main(String[] args) {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        String[] compileFlags = getCompileFlags();

        // Add a java source file.
        comp.addJavaSourceCode("GeneratedTest42", generate());

        // Compile the source file.
        comp.compile();

        // Object ret = XYZ.test(5);
        Object ret = comp.invoke("GeneratedTest42", "test_1", new Object[] {});
        Object ret2 = comp.invoke("GeneratedTest42", "test_3", new Object[] {});

        // Extract return value of invocation, verify its value.
        int i = (int) ret;
        System.out.println("Result of call: " + i);
        if (i != 10) {
            throw new RuntimeException("wrong value: " + i);
        }
    }
}

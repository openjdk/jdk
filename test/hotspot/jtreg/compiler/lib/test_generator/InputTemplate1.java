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
package compiler.lib.test_generator;
import java.util.HashMap;
import java.util.Map;
public class InputTemplate1 extends InputTemplate {
    public InputTemplate1() {
    }
    @Override
    public CodeSegment getTemplate() {
        /* TODO:
         * use $limit, $i, $lFld for variables
         * all defined functions should use uniqueId to avoid conflict
         * Nesting : we want to be able to nest CodeTemplate in another CodeTemplate e.g. at \{thing}
         *           this would require replacing conflicting variables e.g. $i with $i1 and $i2,
         *           and also replace \{init} from the inner CodeTemplate with a var $limit from outer CodeTemplate
        **/
        String imports= """
                """;
        String statics = """
                //InputTemplate1
                static long lFld;
                static A a = new A();
                static boolean flag;
                static class A {
                    int i;
                }
                """;
        String call = """
                test_\\{uniqueId}();
                """;
        String method = """
            public static void test_\\{uniqueId}() {
                long limit = lFld;
                for (int i =\\{init1}; i < \\{limit}; i \\{arithm1}= \\{stride1}) {
                    // Use stride > Integer.MAX_VALUE such that LongCountedLoopNode is not split further into loop nests.
                    for (long j = \\{init2}; j < limit; j\\{arithm2}=\\{stride2}) {
                        a.i += \\{val1}; // NullCheck with trap on false path -> reason to peel
                        \\{thing}
                        \\{template1}
                        if (j > 0) { // After peeling: j > 0 always true -> loop folded away
                            \\{template2}
                            break;
                        }
                    }
                }
            }
            """;
        return new CodeSegment(statics, call, method,imports);
    }
    @Override
    public Map<String, String> getRandomReplacements(int numTest) {
        Template template1 = new Template1();
        Template template2 = new Template4();
        String template_nes1= template1.getTemplate("j");
        String template_nes2= template2.getTemplate("i");
        Map<String, String> replacements = new HashMap<>();
        String init1 = getRandomValueAsString(integerValues);
        String init2 = getRandomValueAsString(integerValues);
        String limit = getRandomValueAsString(positiveIntegerValues);
        String val1 = getRandomValueAsString(integerValues);
        String stride1 = getRandomValueAsString(integerValuesNonZero);
        String stride2 = getRandomValueAsString(integerValuesNonZero);
        String arithm1 = getRandomValue(new String[]{"+", "-"});
        String arithm2 = getRandomValue(new String[]{"+", "-"});
        String thing = getRandomValue(new String[]{"", "synchronized (new Object()) { }"});
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
    @Override
    public String[] getCompileFlags() {
        return new String[] {
                "-Xcomp",
                "-XX:-CreateCoredumpOnCrash"
        };
    }
    @Override
    public int getNumberOfTests(){
        return 1;
    }
    @Override
    public int getNumberOfTestMethods() {
        return 10;
    }
}

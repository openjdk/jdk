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
 * @summary Test some basic Template instantiations.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver template_framework.tests.TestTemplate
 */

package template_framework.tests;

import java.util.Arrays;
import java.util.HashSet;

import compiler.lib.template_framework.*;

public class TestTemplate {

    public static void main(String[] args) {
        test1();
        test2();
        test3();
        test4();
        test5();
        test6();
        // TODO dispatch, variables, choose, con, fields, etc
    }

    public static void test1() {
        Template template = new Template("my_template","Hello World!");
        String code = template.instantiate();
        checkEQ(code, "Hello World!");
    }

    public static void test2() {
        Template template = new Template("my_template",
            """
            Code on more
            than a single line
            """
        );
        String code = template.instantiate();
        String expected = 
            """
            Code on more
            than a single line
            """;
        checkEQ(code, expected);
    }

    public static void test3() {
        Template template = new Template("my_template","start #{p1} #{p2} end");
        checkEQ(template.where("p1", "x").where("p2", "y").instantiate(), "start x y end");
        checkEQ(template.where("p1", "a").where("p2", "b").instantiate(), "start a b end");
        checkEQ(template.where("p1", "").where("p2", "").instantiate(), "start   end");
    }

    public static void test4() {
        HashSet<CodeGenerator> codeGenerators = new HashSet<CodeGenerator>();

        codeGenerators.add(new Template("my_generator_1", "proton"));

        codeGenerators.add(new Template("my_generator_2",
            """
            electron #{param1}
            neutron #{param2}
            """
        ));

        codeGenerators.add(new Template("my_generator_3",
            """
            Universe #{:my_generator_1} {
                #{:my_generator_2(param1=up,param2=down)}
                #{:my_generator_2(param1=#param1,param2=#param2)}
            }
            """
        ));
        CodeGeneratorLibrary library = new CodeGeneratorLibrary(null, codeGenerators);

        Template template = new Template("my_template",
            """
            #{:my_generator_3(param1=low,param2=high)}
            {
                #{:my_generator_3(param1=42,param2=24)}
            }
            """
        );

        String code = template.with(library).instantiate();
        String expected =
            """
            Universe proton {
                electron up
                neutron down

                electron low
                neutron high

            }

            {
                Universe proton {
                    electron up
                    neutron down

                    electron 42
                    neutron 24

                }

            }
            """;
        checkEQ(code, expected);
    }

    public static void test5() {
        TestClassInstantiator instantiator = new TestClassInstantiator("p.xyz", "InnerTest");

        Template staticsTemplate = new Template("my_example_statics",
            """
            static #{param1} #{param2}
            """
        );
        Template mainTemplate = new Template("my_example_main",
            """
            main #{param1} #{param2}
            """
        );
        Template testTemplate = new Template("my_example_test",
            """
            test #{param1} #{param2}
            """
        );

        instantiator.where("param1", "abc")
                    .where("param2", "xyz")
                    .add(staticsTemplate, mainTemplate, testTemplate);

        instantiator.where("param1", Arrays.asList("aaa", "bbb"))
                    .where("param2", Arrays.asList("xxx", "yyy"))
                    .add(staticsTemplate, mainTemplate, testTemplate);

        Template test2Template = new Template("my_example_test2",
            """
            test2 #{param1} #{param2}
            """
        );

        instantiator.where("param1", "alice")
                    .where("param2", "bob")
                    .repeat(3)
                    .add(null, null, test2Template);

        String code = instantiator.instantiate();
        String expected =
            """
            package p.xyz;

            public class InnerTest {
                static abc xyz

                static aaa xxx

                static aaa yyy

                static bbb xxx

                static bbb yyy

                public static void main(String[] args) {
                    main abc xyz
                    main aaa xxx
                    main aaa yyy
                    main bbb xxx
                    main bbb yyy

                }

                test abc xyz

                test aaa xxx

                test aaa yyy

                test bbb xxx

                test bbb yyy

                test2 alice bob

                test2 alice bob

                test2 alice bob

            }""";
        checkEQ(code, expected);
    }

    public static void test6() {
        HashSet<CodeGenerator> codeGenerators = new HashSet<CodeGenerator>();

        codeGenerators.add(new Template("my_generator_1",
            """
            test1 #{param1} #{param2}
            """
        ));

        CodeGeneratorLibrary library = new CodeGeneratorLibrary(CodeGeneratorLibrary.standard(), codeGenerators);

        Template template = new Template("my_template",
            """
            #{:repeat(call=my_generator_1,repeat=5,param1=alpha,param2=beta)}
            {
                #{:repeat(call=my_generator_1,repeat=5,param1=gamma,param2=delta)}
            }
            """
        );

        String code = template.with(library).instantiate();
        String expected =
            """
            test1 alpha beta
            test1 alpha beta
            test1 alpha beta
            test1 alpha beta
            test1 alpha beta

            {
                test1 gamma delta
                test1 gamma delta
                test1 gamma delta
                test1 gamma delta
                test1 gamma delta

            }
            """;
        checkEQ(code, expected);
    }

    public static void checkEQ(String code, String expected) {
        if (!code.equals(expected)) {
            System.out.println("\"" + code + "\"");
            System.out.println("\"" + expected + "\"");
            throw new RuntimeException("Template instantiation mismatch!");
        }
    }
}

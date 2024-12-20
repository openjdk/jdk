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
 * @summary Test some basic Template instantiations. We do not necessarily generate correct
 *          java code, we just test that the code generation deterministically creates the
 *          expected String.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver template_framework.tests.TestTemplate
 */

package template_framework.tests;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;

import jdk.test.lib.Utils;

import compiler.lib.template_framework.*;
import compiler.lib.compile_framework.*;

public class TestTemplate {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        testSingleLine();
        testMultiLine();
        testMultiLineWithParameters();
        testCustomLibrary();
        testClassInstantiator();
        testRepeat();
        testDispatch();
        testClassInstantiatorAndDispatch();
        testChoose();
        testFieldsAndVariables();
        testFieldsAndVariablesDispatch();
        testIntCon();
        testLongCon();
        testFuel();
        testRecursiveCalls();
    }

    public static void testSingleLine() {
        Template template = new Template("my_template","Hello World!");
        String code = template.instantiate();
        checkEQ(code, "Hello World!");
    }

    public static void testMultiLine() {
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

    public static void testMultiLineWithParameters() {
        Template template = new Template("my_template","start #{p1} #{p2} end");
        checkEQ(template.where("p1", "x").where("p2", "y").instantiate(), "start x y end");
        checkEQ(template.where("p1", "a").where("p2", "b").instantiate(), "start a b end");
        checkEQ(template.where("p1", "").where("p2", "").instantiate(), "start   end");
    }

    public static void testCustomLibrary() {
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

    public static void testClassInstantiator() {
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

    public static void testRepeat() {
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

    public static void testDispatch() {
        HashSet<CodeGenerator> codeGenerators = new HashSet<CodeGenerator>();

        codeGenerators.add(new Template("my_generator",
            """
            test1a #{param1}
            test1b #{param2}
            """
        ));

        CodeGeneratorLibrary library = new CodeGeneratorLibrary(CodeGeneratorLibrary.standard(), codeGenerators);

        Template template = new Template("my_template",
            """
            alpha
            {
                #open(class)
                beta
                gamma
                {
                    eins
                    #open(method)
                    zwei
                    x#{:dispatch(scope=class,call=my_generator,param1=abc,param2=bcd)}x
                    y#{:dispatch(scope=class,call=my_generator,param1=cde,param2=def)}y
                    z#{:dispatch(scope=method,call=my_generator,param1=efg,param2=gfh)}z
                    w#{:dispatch(scope=method,call=my_generator,param1=fhi,param2=hij)}w
                    drei
                    #close(method)
                }
                delta
                {
                    un
                    #open(method)
                    deux
                    x#{:dispatch(scope=class,call=my_generator,param1=123,param2=234)}x
                    y#{:dispatch(scope=class,call=my_generator,param1=345,param2=456)}y
                    z#{:dispatch(scope=method,call=my_generator,param1=567,param2=678)}z
                    w#{:dispatch(scope=method,call=my_generator,param1=789,param2=890)}w
                    troi
                    #close(method)
                }
                epsilon
                x#{:dispatch(scope=class,call=my_generator,param1=xxx,param2=yyy)}x
                y#{:dispatch(scope=class,call=my_generator,param1=zzz,param2=www)}y
                {
                    monday
                    #open(class)
                    tuesday
                    x#{:dispatch(scope=class,call=my_generator,param1=bar,param2=foo)}x
                    y#{:dispatch(scope=class,call=my_generator,param1=alice,param2=bob)}y
                    {
                        uno
                        #open(method)
                        due
                        x#{:dispatch(scope=class,call=my_generator,param1=alef,param2=bet)}x
                        y#{:dispatch(scope=class,call=my_generator,param1=vet,param2=gimel)}y
                        z#{:dispatch(scope=method,call=my_generator,param1=dalet,param2=he)}z
                        w#{:dispatch(scope=method,call=my_generator,param1=vav,param2=zayin)}w
                        tre
                        #close(method)
                        quatro
                    }
                    wednesday
                    #close(class)
                    thursday
                    x#{:dispatch(scope=class,call=my_generator,param1=aaa,param2=sss)}x
                    y#{:dispatch(scope=class,call=my_generator,param1=ddd,param2=fff)}y
                    friday
                }
                zeta
                eta
                x#{:dispatch(scope=class,call=my_generator,param1=up,param2=down)}x
                y#{:dispatch(scope=class,call=my_generator,param1=left,param2=right)}y
                theta
                #close(class)
            }
            """
        );

        String code = template.with(library).instantiate();
        String expected =
            """
            alpha
            {
                test1a abc
                test1b bcd

                test1a cde
                test1b def

                test1a 123
                test1b 234

                test1a 345
                test1b 456

                test1a xxx
                test1b yyy

                test1a zzz
                test1b www

                test1a aaa
                test1b sss

                test1a ddd
                test1b fff

                test1a up
                test1b down

                test1a left
                test1b right


                beta
                gamma
                {
                    eins
                    test1a efg
                    test1b gfh

                    test1a fhi
                    test1b hij


                    zwei
                    xx
                    yy
                    zz
                    ww
                    drei

                }
                delta
                {
                    un
                    test1a 567
                    test1b 678

                    test1a 789
                    test1b 890


                    deux
                    xx
                    yy
                    zz
                    ww
                    troi

                }
                epsilon
                xx
                yy
                {
                    monday
                    test1a bar
                    test1b foo

                    test1a alice
                    test1b bob

                    test1a alef
                    test1b bet

                    test1a vet
                    test1b gimel


                    tuesday
                    xx
                    yy
                    {
                        uno
                        test1a dalet
                        test1b he

                        test1a vav
                        test1b zayin


                        due
                        xx
                        yy
                        zz
                        ww
                        tre

                        quatro
                    }
                    wednesday

                    thursday
                    xx
                    yy
                    friday
                }
                zeta
                eta
                xx
                yy
                theta

            }
            """;
        checkEQ(code, expected);
    }

    public static void testClassInstantiatorAndDispatch() {
        HashSet<CodeGenerator> codeGenerators = new HashSet<CodeGenerator>();

        codeGenerators.add(new Template("my_generator",
            """
            gen1 #{param1}
            gen2 #{param2}
            """
        ));
        CodeGeneratorLibrary library = new CodeGeneratorLibrary(CodeGeneratorLibrary.standard(), codeGenerators);

        TestClassInstantiator instantiator = new TestClassInstantiator("p.xyz", "InnerTest", library);

        Template staticsTemplate = new Template("my_example_statics",
            """
            static #{param1} #{param2}
            x#{:dispatch(scope=class,call=my_generator,param1=#param1,param2=123)}x
            """
        );
        Template mainTemplate = new Template("my_example_main",
            """
            main #{param1} #{param2}
            x#{:dispatch(scope=class,call=my_generator,param1=234,param2=#param2)}x
            y#{:dispatch(scope=method,call=my_generator,param1=foo,param2=bar)}y
            """
        );
        Template testTemplate = new Template("my_example_test",
            """
            test #{param1} #{param2}
            x#{:dispatch(scope=class,call=my_generator,param1=uno,param2=#param1)}x
            {
                #open(method)
                hello
                x#{:dispatch(scope=class,call=my_generator,param1=due,param2=tre)}x
                y#{:dispatch(scope=method,call=my_generator,param1=quatro,param2=chinque)}y
                world
                #close(method)
            }
            """
        );

        instantiator.where("param1", "abc")
                    .where("param2", "xyz")
                    .add(staticsTemplate, mainTemplate, testTemplate);

        String code = instantiator.instantiate();
        String expected =
            """
            package p.xyz;

            public class InnerTest {
                gen1 abc
                gen2 123

                gen1 234
                gen2 xyz

                gen1 uno
                gen2 abc

                gen1 due
                gen2 tre

                static abc xyz
                xx

                public static void main(String[] args) {
                    gen1 foo
                    gen2 bar

                    main abc xyz
                    xx
                    yy

                }

                test abc xyz
                xx
                {
                    gen1 quatro
                    gen2 chinque


                    hello
                    xx
                    yy
                    world

                }

            }""";
        checkEQ(code, expected);
    }

    public static void testChoose() {
        Template template = new Template("my_template",
            """
            x#{v1:choose(from=11|11|11)}x#{v1}x
            x#{v2:choose(from=)}x#{v2}x
            x#{v3:choose(from=abc)}x#{v3}x
            """
        );
        String code = template.instantiate();
        String expected =
            """
            x11x11x
            xxx
            xabcxabcx
            """;
        checkEQ(code, expected);
    }

    public static void testFieldsAndVariables() {
        // We use dummy types like "my_int_1" etc. to make sure we have exactly 1 valid
        // option, so that we get a deterministic output string from the instantiation.
        //
        // We generate 2 variables for "my_int_2", but only "hardCoded2" is mutable.
        // Same for "my_int_4".
        //
        // "hardCoded7" is added to "my_int_4", which has also variables declared in the
        // method test, but should not be available outside method test.
        Template template = new Template("my_template",
            """
            public class XYZ {
                #open(class)
                public static int hardCoded1 = 1;
                #{:add_var(scope=class,name=hardCoded1,type=my_int_1)}
                public static int hardCoded2 = 1;
                public static final int hardCoded3 = 1;
                #{:add_var(scope=class,name=hardCoded2,type=my_int_2)}
                #{:add_var(scope=class,name=hardCoded3,type=my_int_2,mutable=false)}

                static void test() {
                    #open(method)
                    int hardCoded4 = 1;
                    #{:add_var(scope=method,name=hardCoded4,type=my_int_3)}
                    int hardCoded5 = 1;
                    final int hardCoded6 = 1;
                    #{:add_var(scope=method,name=hardCoded5,type=my_int_4)}
                    #{:add_var(scope=method,name=hardCoded6,type=my_int_4,mutable=false)}

                    #{:mutable_var(type=my_int_1)} = #{:var(type=my_int_1)} + 1;
                    #{:mutable_var(type=my_int_2)} = 5;

                    #{:mutable_var(type=my_int_3)} = #{:var(type=my_int_3)} + 1;
                    #{:mutable_var(type=my_int_4)} = 5;

                    #close(method)
                }

                public static int hardCoded0 = 1;
                #{:add_var(scope=class,name=hardCoded0,type=my_int_4)}

                static void foo() {
                    #open(method)
                    int hardCoded7 = 1;
                    #{:add_var(scope=method,name=hardCoded7,type=my_int_5)}
                    int hardCoded8 = 1;
                    final int hardCoded9 = 1;
                    #{:add_var(scope=method,name=hardCoded8,type=my_int_6)}
                    #{:add_var(scope=method,name=hardCoded9,type=my_int_6,mutable=false)}

                    #{:mutable_var(type=my_int_5)} = #{:var(type=my_int_5)} + 1;
                    #{:mutable_var(type=my_int_6)} = 5;

                    #{:mutable_var(type=my_int_4)} = #{:var(type=my_int_4)} + 1;
                    #close(method)
                }
                #close(class)
            }
            """
        );
        String code = template.instantiate();
        String expected =
            """
            public class XYZ {

                public static int hardCoded1 = 1;

                public static int hardCoded2 = 1;
                public static final int hardCoded3 = 1;



                static void test() {

                    int hardCoded4 = 1;

                    int hardCoded5 = 1;
                    final int hardCoded6 = 1;



                    hardCoded1 = hardCoded1 + 1;
                    hardCoded2 = 5;

                    hardCoded4 = hardCoded4 + 1;
                    hardCoded5 = 5;


                }

                public static int hardCoded0 = 1;


                static void foo() {

                    int hardCoded7 = 1;

                    int hardCoded8 = 1;
                    final int hardCoded9 = 1;



                    hardCoded7 = hardCoded7 + 1;
                    hardCoded8 = 5;

                    hardCoded0 = hardCoded0 + 1;

                }

            }
            """;
        checkEQ(code, expected);
    }

    public static void testFieldsAndVariablesDispatch() {
        Template template = new Template("my_template",
            """
            public class XYZ {
                #open(class)
                // class body

                static void test() {
                    #open(method)
                    // method body
                    #{:_internal_def_var(name=var1,prefix=final int,value=1,type=my_int_1,mutable=false)}
                    int x1 = #{:var(type=my_int_1)} + 1;
                    #{:_internal_def_var(name=var2,prefix=int,value=2,type=my_int_1,mutable=true)}
                    #{:mutable_var(type=my_int_1)} += 2;
                    #{:def_final_var(name=var3,prefix=final int,value=3,type=my_int_2)}
                    int x2 = #{:var(type=my_int_2)} + 1;
                    #{:def_var(name=var4,prefix=int,value=4,type=my_int_2)}
                    #{:mutable_var(type=my_int_2)} += 2;
                    #{:def_final_field(name=field1,prefix=public static final int,value=5,type=my_int_3)}
                    int x3 = #{:var(type=my_int_3)} + 1;
                    #{:def_field(name=field2,prefix=public static int,value=6,type=my_int_3)}
                    #{:mutable_var(type=my_int_3)} += 2;
                    #close(method)
                }
                #close(class)
            }
            """
        );
        String code = template.instantiate();
        String expected =
            """
            public class XYZ {
                public static final int field1 = 5;
                public static int field2 = 6;

                // class body

                static void test() {
                    final int var3 = 3;
                    int var4 = 4;

                    // method body
                    final int var1 = 1;
                    int x1 = var1 + 1;
                    int var2 = 2;
                    var2 += 2;

                    int x2 = var3 + 1;

                    var4 += 2;

                    int x3 = field1 + 1;

                    field2 += 2;

                }

            }
            """;
        checkEQ(code, expected);
    }

    public static void testIntCon() {
        // To keep the result deterministic, we have to always keep hi=lo.
        Template template = new Template("my_template",
            """
            x#{c1:int_con(lo=min_int,hi=min_int)}x
            x#{c2:int_con(lo=max_int,hi=max_int)}x
            x#{c3:int_con(lo=42,hi=42)}x
            x#{c4:int_con(lo=-2147483648,hi=-2147483648)}x
            x#{c5:int_con(lo=2147483647,hi=2147483647)}x
            y#{c3}y
            x#{c6:int_con(lo=#param,hi=#param)}x
            y#{param}y
            """
        );
        String param = String.valueOf(RANDOM.nextInt());
        String code = template.where("param", param).instantiate();
        String expected =
            """
            x-2147483648x
            x2147483647x
            x42x
            x-2147483648x
            x2147483647x
            y42y
            x"""
            + param +
            """
            x
            y"""
            + param +
            """
            y
            """;
        checkEQ(code, expected);
    }

    public static void testLongCon() {
        // To keep the result deterministic, we have to always keep hi=lo.
        Template template = new Template("my_template",
            """
            x#{c1:long_con(lo=min_int,hi=min_int)}x
            x#{c2:long_con(lo=max_int,hi=max_int)}x
            x#{c3:long_con(lo=42,hi=42)}x
            x#{c4:long_con(lo=-2147483648,hi=-2147483648)}x
            x#{c5:long_con(lo=2147483647,hi=2147483647)}x
            y#{c3}y
            x#{c6:long_con(lo=#param1,hi=#param1)}x
            y#{param1}y
            x#{l1:long_con(lo=min_long,hi=min_long)}x
            x#{l2:long_con(lo=max_long,hi=max_long)}x
            x#{l4:long_con(lo=-9223372036854775808,hi=-9223372036854775808)}x
            x#{l5:long_con(lo=9223372036854775807,hi=9223372036854775807)}x
            x#{l6:long_con(lo=#param2,hi=#param2)}x
            y#{param2}y
            """
        );
        String param1 = String.valueOf(RANDOM.nextInt());
        String param2 = String.valueOf(RANDOM.nextLong());
        String code = template.where("param1", param1).where("param2", param2).instantiate();
        String expected =
            """
            x-2147483648Lx
            x2147483647Lx
            x42Lx
            x-2147483648Lx
            x2147483647Lx
            y42Ly
            x"""
            + param1 +
            """
            Lx
            y"""
            + param1 +
            """
            y
            x-9223372036854775808Lx
            x9223372036854775807Lx
            x-9223372036854775808Lx
            x9223372036854775807Lx
            x"""
            + param2 +
            """
            Lx
            y"""
            + param2 +
            """
            y
            """;
        checkEQ(code, expected);
    }

    public static void testFuel() {
        HashSet<CodeGenerator> codeGenerators = new HashSet<CodeGenerator>();

        codeGenerators.add(new Template("my_leaf",
            "leaf"
        ));

        // Default fuel is 50, the cost of this is set to 25 so we get 2 recursion levels.
        codeGenerators.add(new Template("my_split",
            """
            begin
                #{:my_code}
            mid
                #{:my_code}
            end""", 25
        ));

        // The selector picks my_split as long as there is enough fuel, and once there is no fuel left, it picks
        // my_leaf.
        SelectorCodeGenerator selector = new SelectorCodeGenerator("my_code", "my_leaf");
        selector.add("my_split", 100);
        codeGenerators.add(selector);

        CodeGeneratorLibrary library = new CodeGeneratorLibrary(CodeGeneratorLibrary.standard(), codeGenerators);

        Template template = new Template("my_template",
            """
            #{:my_code}
            """
        );

        String code = template.with(library).instantiate();
        String expected =
            """
            begin
                begin
                    leaf
                mid
                    leaf
                end
            mid
                begin
                    leaf
                mid
                    leaf
                end
            end
            """;
        checkEQ(code, expected);
    }

    public static void testRecursiveCalls() {
        // Create a new CompileFramework instance.
        CompileFramework comp = new CompileFramework();

        // Add a java source file.
        comp.addJavaSourceCode("p.xyz.InnerTest", generateRecursiveCalls());

        // Compile the source file.
        comp.compile();

        // int ret1 = InnerTest.test1(5);
        int ret1 = (int)comp.invoke("p.xyz.InnerTest", "test1", new Object[] {5});
        if (ret1 != 5 * 3) {
            throw new RuntimeException("Unexpected result: " + ret1);
        }

        // int ret2 = InnerTest.test1(5);
        int ret2 = (int)comp.invoke("p.xyz.InnerTest", "test2", new Object[] {5});
        if (ret2 != 5 * 7) {
            throw new RuntimeException("Unexpected result: " + ret2);
        }

    }

    public static String generateRecursiveCalls() {
        // First, we set up some additional code generators for the library.
        HashSet<CodeGenerator> codeGenerators = new HashSet<CodeGenerator>();

        codeGenerators.add(new Template("my_param_op_var",
            "#{param} #{op} #{:var(type=#type)}"
        ));

        CodeGeneratorLibrary library = new CodeGeneratorLibrary(CodeGeneratorLibrary.standard(), codeGenerators);

        // Now, we start generating code.
        TestClassInstantiator instantiator = new TestClassInstantiator("p.xyz", "InnerTest", library);

        // test1(in) -> in * 3
        Template test1Template = new Template("my_test1",
            """
            public static int test1(int in) {
                return in * #{param};
            }
            """
        );
        instantiator.where("param", "3").add(null, null, test1Template);

        // test2(in) -> in * 7
        // Register $in as a local variable, then pass it on with ":$in",
        // and sample it as the only option inside my_param_op_var.
        Template test2Template = new Template("my_test2",
            """
            public static int test2(int ${in:my_int_2:immutable}) {
                return #{:my_param_op_var(param=#param,op=#op,type=my_int_2):$in};
            }
            """
        );
        instantiator.where("param", "7")
                    .where("op", "*")
                    .add(null, null, test2Template);

        return instantiator.instantiate();
    }

    public static void checkEQ(String code, String expected) {
        if (!code.equals(expected)) {
            System.out.println("\"" + code + "\"");
            System.out.println("\"" + expected + "\"");
            throw new RuntimeException("Template instantiation mismatch!");
        }
    }
}

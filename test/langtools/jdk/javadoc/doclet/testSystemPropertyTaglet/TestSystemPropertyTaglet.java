/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5076751 8234746 8345555
 * @summary System properties documentation needed in javadoc
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.* toolbox.ToolBox builder.ClassBuilder
 * @run main TestSystemPropertyTaglet
 */

import java.nio.file.Path;
import java.nio.file.Paths;

import builder.ClassBuilder;
import builder.ClassBuilder.MethodBuilder;
import toolbox.ToolBox;

import javadoc.tester.JavadocTester;

public class TestSystemPropertyTaglet extends JavadocTester {

    final ToolBox tb;

    public static void main(String... args) throws Exception {
        var tester = new TestSystemPropertyTaglet();
        tester.runTests();
    }

    TestSystemPropertyTaglet() {
        tb = new ToolBox();
    }

    @Test
    public void test(Path base) throws Exception {
        javadoc("-d", base.resolve("out").toString(),
                "--module-source-path", testSrc,
                "--module", "mymodule");

        checkExit(Exit.OK);

        checkOrder("mymodule/mypackage/MyAnnotation.html",
                   """
                       <h1 title="Annotation Interface MyAnnotation" class="title">Annotation Interface MyAnnotation</h1>""",
                   """
                       (annotation) the <code><span id="test.property" class="search-tag-result">test.property</span></code> system property.""",
                   "<h2>Element Details</h2>",
                   """
                       (annotation/method) the <code><span id="test.property-1" class="search-tag-resul\
                       t">test.property</span></code> system property.""",
                   "");

        checkOrder("mymodule/mypackage/MyClass.html",
                   """
                       <h1 title="Class MyClass" class="title">Class MyClass</h1>""",
                   """
                       (class) the <code><span id="test.property" class="search-tag-result">test.property</span></code> system property.""",
                   "<h2>Field Details</h2>",
                   """
                       (class/field) the <code><span id="test.property-1" class="search-tag-result">tes\
                       t.property</span></code> system property.""",
                   """
                       (class/static-field) the <code><span id="test.property-2" class="search-tag-resu\
                       lt">test.property</span></code> system property.""",
                   "<h2>Constructor Details</h2>",
                   """
                       (class/constructor) the <code><span id="test.property-3" class="search-tag-resul\
                       t">test.property</span></code> system property.""",
                   "<h2>Method Details</h2>",
                   """
                       (class/static-method) the <code><span id="test.property-4" class="search-tag-res\
                       ult">test.property</span></code> system property.""",
                   """
                       (class/method) the <code><span id="test.property-5" class="search-tag-result">te\
                       st.property</span></code> system property.""",
                   "");

        checkOrder("mymodule/mypackage/MyEnum.html",
                   """
                       <h1 title="Enum Class MyEnum" class="title">Enum Class MyEnum</h1>""",
                   """
                       (enum) the <code><span id="test.property" class="search-tag-result">test.property</span></code> system property.""",
                   "<h2>Enum Constant Details</h2>",
                   """
                       (enum/constant) the <code><span id="test.property-1" class="search-tag-result">t\
                       est.property</span></code> system property.""",
                   "");

        checkOrder("mymodule/mypackage/MyError.html",
                   """
                       <h1 title="Class MyError" class="title">Class MyError</h1>""",
                   """
                       (error) the <code><span id="test.property" class="search-tag-result">test.property</span></code> system property.""",
                   "<h2>Constructor Details</h2>",
                   """
                       (error/constructor) the <code><span id="test.property-1" class="search-tag-resul\
                       t">test.property</span></code> system property.""",
                   "");

        checkOrder("mymodule/mypackage/MyException.html",
                   """
                       <h1 title="Class MyException" class="title">Class MyException</h1>""",
                   """
                       (exception) the <code><span id="test.property" class="search-tag-result">test.property</span></code> system property.""",
                   "<h2>Constructor Details</h2>",
                   """
                       (exception/constructor) the <code><span id="test.property-1" class="search-tag-result">test.property</span></code>""",
                   "");

        checkOrder("mymodule/mypackage/MyInterface.html",
                   """
                       <h1 title="Interface MyInterface" class="title">Interface MyInterface</h1>""",
                   """
                       (interface) the <code><span id="test.property" class="search-tag-result">test.property</span></code> system property.""",
                   "<h2>Field Details</h2>",
                   """
                       (interface/constant) the <code><span id="test.property-1" class="search-tag-resu\
                       lt">test.property</span></code> system property.""",
                   "<h2>Method Details</h2>",
                   """
                       (interface/method-1) the <code><span id="test.property-2" class="search-tag-resu\
                       lt">test.property</span></code> system property.""",
                   """
                       (interface/method-2) the <code><span id="test.property-3" class="search-tag-resu\
                       lt">test.property</span></code> system property.""",
                   "");

        checkOrder("mymodule/module-summary.html",
                   """
                       <h1 title="Module mymodule" class="title">Module mymodule</h1>""",
                   """
                       (module) the <code><span id="test.property" class="search-tag-result">test.property</span></code> system property.""",
                   "");

        checkOrder("mymodule/mypackage/package-summary.html",
                   """
                       <h1 title="Package mypackage" class="title">Package mypackage</h1>""",
                   """
                       (package) the <code><span id="test.property" class="search-tag-result">test.property</span></code> system property.""",
                   "");

        checkOrder("index-all.html",
                   """
                       <h2 class="title" id="I:T">T</h2>""",
                   """
                       <dt><a href="mymodule/mypackage/MyAnnotation.html#\
                       test.property" class="search-tag-link">test.property</a> - System property in annotation interface mypackag\
                       e.MyAnnotation</dt>
                       <dd>&nbsp;</dd>""",
                   """
                       <dt><a href="mymodule/mypackage/MyClass.html#test.\
                       property" class="search-tag-link">test.property</a> - System property in class mypackage.MyClass</dt>
                       <dd>&nbsp;</dd>""",
                   """
                       <dt><a href="mymodule/mypackage/MyEnum.html#test.p\
                       roperty" class="search-tag-link">test.property</a> - System property in enum class mypackage.MyEnum</dt>
                       <dd>&nbsp;</dd>""",
                   """
                       <dt><a href="mymodule/mypackage/MyError.html#test.\
                       property" class="search-tag-link">test.property</a> - System property in exception class mypackage.MyE\
                       rror</dt>
                       <dd>&nbsp;</dd>""",
                   """
                       <dt><a href="mymodule/mypackage/MyException.html#t\
                       est.property" class="search-tag-link">test.property</a> - System property in exception class mypackage\
                       .MyException</dt>
                       <dd>&nbsp;</dd>""",
                   """
                       <dt><a href="mymodule/mypackage/MyInterface.html#t\
                       est.property" class="search-tag-link">test.property</a> - System property in interface mypackage.MyInt\
                       erface</dt>
                       <dd>&nbsp;</dd>""",
                   """
                       <dt><a href="mymodule/module-summary.html#test.pro\
                       perty" class="search-tag-link">test.property</a> - System property in module mymodule</dt>
                       <dd>&nbsp;</dd>""",
                   """
                       <dt><a href="mymodule/mypackage/MyAnnotation.html#\
                       test.property-1" class="search-tag-link">test.property</a> - System property in mypackage.MyAnnotation\
                       .value()</dt>
                       <dd>&nbsp;</dd>""",
                   """
                       <dt><a href="mymodule/mypackage/MyClass.html#test.\
                       property-2" class="search-tag-link">test.property</a> - System property in mypackage.MyClass.INT_CONST\
                       ANT</dt>
                       <dd>&nbsp;</dd>""",
                   """
                       <dt><a href="mymodule/mypackage/MyClass.html#test.\
                       property-3" class="search-tag-link">test.property</a> - System property in mypackage.MyClass.MyClass()\
                       </dt>
                       <dd>&nbsp;</dd>""",
                   """
                       <dt><a href="mymodule/mypackage/MyClass.html#test.\
                       property-1" class="search-tag-link">test.property</a> - System property in mypackage.MyClass.intField<\
                       /dt>
                       <dd>&nbsp;</dd>""",
                   """
                       <dt><a href="mymodule/mypackage/MyClass.html#test.\
                       property-5" class="search-tag-link">test.property</a> - System property in mypackage.MyClass.run()</dt\
                       >
                       <dd>&nbsp;</dd>""",
                   """
                       <dt><a href="mymodule/mypackage/MyClass.html#test.\
                       property-4" class="search-tag-link">test.property</a> - System property in mypackage.MyClass.value()</\
                       dt>
                       <dd>&nbsp;</dd>""",
                   """
                       <dt><a href="mymodule/mypackage/MyEnum.html#test.p\
                       roperty-1" class="search-tag-link">test.property</a> - System property in mypackage.MyEnum.X</dt>
                       <dd>&nbsp;</dd>""",
                   """
                       <dt><a href="mymodule/mypackage/MyEnum.html#test.p\
                       roperty-2" class="search-tag-link">test.property</a> - System property in mypackage.MyEnum.m()</dt>
                       <dd>&nbsp;</dd>""",
                   """
                       <dt><a href="mymodule/mypackage/MyError.html#test.\
                       property-1" class="search-tag-link">test.property</a> - System property in mypackage.MyError.MyError()\
                       </dt>
                       <dd>&nbsp;</dd>""",
                   """
                       <dt><a href="mymodule/mypackage/MyException.html#t\
                       est.property-1" class="search-tag-link">test.property</a> - System property in mypackage.MyException.M\
                       yException()</dt>
                       <dd>&nbsp;</dd>""",
                   """
                       <dt><a href="mymodule/mypackage/MyInterface.html#t\
                       est.property-1" class="search-tag-link">test.property</a> - System property in mypackage.MyInterface.I\
                       NT_CONSTANT</dt>
                       <dd>&nbsp;</dd>""",
                   """
                       <dt><a href="mymodule/mypackage/MyInterface.html#t\
                       est.property-2" class="search-tag-link">test.property</a> - System property in mypackage.MyInterface.m\
                       ()</dt>
                       <dd>&nbsp;</dd>""",
                   """
                       <dt><a href="mymodule/mypackage/MyInterface.html#t\
                       est.property-3" class="search-tag-link">test.property</a> - System property in mypackage.MyInterface.m\
                       (String...)</dt>
                       <dd>&nbsp;</dd>""",
                   """
                       <dt><a href="mymodule/mypackage/package-summary.ht\
                       ml#test.property" class="search-tag-link">test.property</a> - System property in package mypackage</dt\
                       >
                       <dd>&nbsp;</dd>""",
                   "");

        checkOutput("tag-search-index.js", true,
                    """
                        {"l":"test.property","h":"annotation interface mypackage.MyAnnotation","k":"15","u":"mymodule/mypackage/MyAnnotation.html#test.property"}""",
                    """
                        {"l":"test.property","h":"class mypackage.MyClass","k":"15","u":"my\
                        module/mypackage/MyClass.html#test.property"}""",
                    """
                        {"l":"test.property","h":"enum class mypackage.MyEnum","k":"15","u"\
                        :"mymodule/mypackage/MyEnum.html#test.property"}""",
                    """
                        {"l":"test.property","h":"exception class mypackage.MyError","k":"15","u":"mymodule/mypackage/MyError.html#test.property"}""",
                    """
                        {"l":"test.property","h":"exception class mypackage.MyException","k":"15","u":"mymodule/mypackage/MyException.html#test.property"}""",
                    """
                        {"l":"test.property","h":"interface mypackage.MyInterface","k":"15"\
                        ,"u":"mymodule/mypackage/MyInterface.html#test.property"}""",
                    """
                        {"l":"test.property","h":"module mymodule","k":"15","u":"mymodule/m\
                        odule-summary.html#test.property"}""",
                    """
                        {"l":"test.property","h":"mypackage.MyAnnotation.value()","k":"15",\
                        "u":"mymodule/mypackage/MyAnnotation.html#test.property-1"}""",
                    """
                        {"l":"test.property","h":"mypackage.MyClass.INT_CONSTANT","k":"15",\
                        "u":"mymodule/mypackage/MyClass.html#test.property-2"}""",
                    """
                        {"l":"test.property","h":"mypackage.MyClass.MyClass()","k":"15","u"\
                        :"mymodule/mypackage/MyClass.html#test.property-3"}""",
                    """
                        {"l":"test.property","h":"mypackage.MyClass.intField","k":"15","u":\
                        "mymodule/mypackage/MyClass.html#test.property-1"}""",
                    """
                        {"l":"test.property","h":"mypackage.MyClass.run()","k":"15","u":"my\
                        module/mypackage/MyClass.html#test.property-5"}""",
                    """
                        {"l":"test.property","h":"mypackage.MyClass.value()","k":"15","u":"\
                        mymodule/mypackage/MyClass.html#test.property-4"}""",
                    """
                        {"l":"test.property","h":"mypackage.MyEnum.X","k":"15","u":"mymodul\
                        e/mypackage/MyEnum.html#test.property-1"}""",
                    """
                        {"l":"test.property","h":"mypackage.MyEnum.m()","k":"15","u":"mymod\
                        ule/mypackage/MyEnum.html#test.property-2"}""",
                    """
                        {"l":"test.property","h":"mypackage.MyError.MyError()","k":"15","u"\
                        :"mymodule/mypackage/MyError.html#test.property-1"}""",
                    """
                        {"l":"test.property","h":"mypackage.MyException.MyException()","k":"15","u":"mymodule/mypackage/MyException.html#test.property-1"}""",
                    """
                        {"l":"test.property","h":"mypackage.MyInterface.INT_CONSTANT","k":"15","u":"mymodule/mypackage/MyInterface.html#test.property-1"}""",
                    """
                        {"l":"test.property","h":"mypackage.MyInterface.m()","k":"15","u":"\
                        mymodule/mypackage/MyInterface.html#test.property-2"}""",
                    """
                        {"l":"test.property","h":"mypackage.MyInterface.m(String...)","k":"15","u":"mymodule/mypackage/MyInterface.html#test.property-3"}""",
                    """
                        {"l":"test.property","h":"package mypackage","k":"15","u":"mymodule\
                        /mypackage/package-summary.html#test.property"}""",
                    "");
    }

    @Test
    public void testSystemPropertyWithinATag(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");

        new ClassBuilder(tb, "pkg2.A")
                .setModifiers("public", "class")
                .addMembers(MethodBuilder.parse("public void func(){}")
                        .setComments("a within a : <a href='..'>{@systemProperty user.name}</a>"))
                .write(srcDir);

        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg2");

        checkExit(Exit.OK);

        checkOutput(Output.OUT, true,
                "warning: {@systemProperty} tag, which expands to <a>, within <a>");
    }
}

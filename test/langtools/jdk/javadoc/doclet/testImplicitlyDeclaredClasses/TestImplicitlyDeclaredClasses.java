/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8308715
 * @library /tools/lib ../../lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.* toolbox.ToolBox toolbox.ModuleBuilder builder.ClassBuilder
 * @run main/othervm TestImplicitlyDeclaredClasses
 */

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestImplicitlyDeclaredClasses extends JavadocTester {

    private final ToolBox tb = new ToolBox();

    public static void main(String... args) throws Exception {
        new TestImplicitlyDeclaredClasses().runTests();
    }

    @Test
    public void test(Path base) throws IOException {
        int i = 0;
        for (Method main : mainMethods())
            for (Method otherMethod : otherMethods()) {
                var methods = List.of(main, otherMethod);
                var index = String.valueOf(i++);
                var src = base.resolve(Path.of("src-" + index, "MyClass.java"));
                tb.writeFile(src, methods.stream()
                        .map(Object::toString)
                        .collect(Collectors.joining("\n")));
                // TODO: remove preview-related options once "Implicitly Declared
                //  Classes and Instance Main Methods" has been standardized
                javadoc("--enable-preview", "--source=" + Runtime.version().feature(),
                        "-d", base.resolve("out-" + index).toString(),
                        src.toString());

                checkExit(Exit.OK);
                // there must be no warning on undocumented (default) constructor
                checkOutput(Output.OUT, false, """
                        warning: use of default constructor, which does not provide a comment""");
                // the (default) constructor must neither be linked nor mentioned
                checkOutput("MyClass.html", false, "%3Cinit%3E");
                checkOutput("MyClass.html", false, "Constructor");
                // a method that is public, protected or declared with package
                // access must either be documented or, if it doesn't have a
                // comment, must be warned about
                int nWarnedMethods = 0;
                for (var m : methods) {
                    if (m.accessModifier.compareTo(Access.PACKAGE) >= 0) {
                        if (m.comment.isEmpty()) {
                            checkOutput(Output.OUT, true, "warning: no comment\n" + m);
                            nWarnedMethods++;
                        } else {
                            checkOutput("MyClass.html", true,
                                    """
                                            <span class="return-type">%s</span>"""
                                            .formatted(m.returnValue),
                                    """
                                            <span class="element-name">%s</span>"""
                                            .formatted(m.name));
                        }
                    }
                }
                // there must be no warning on uncommented implicitly declared class
                //
                // Here's a non-obvious part. A warning message for an uncommented
                // class is the same as that of a method. Moreover, since the class
                // is implicit, its AST position is that of the first method.
                //
                // Put differently, if the class is uncommented, the warning about
                // it is indistinguishable from that of the first method, if that
                // method is uncommented.
                //
                // Here's how this check works: if an undocumented class warning
                // is present, then the total count of undocumented element warnings
                // is one greater than that of undocumented methods.
                //
                // Of course, it's possible, although seemingly unlikely, that
                // this check passes, when it should fail: the warning for class
                // is generated, but the warning for the first method is not.
                // Numbers are equal, test passes.
                checking("uncommented class warning");
                long all = Pattern.compile("warning: no comment")
                        .matcher(getOutput(Output.OUT))
                        .results()
                        .count();
                if (all != nWarnedMethods) {
                    failed("%d/%d".formatted(all, nWarnedMethods));
                } else {
                    passed("");
                }
            }
    }

    private Iterable<Method> mainMethods() {
        return generate(
                List.of("/** main comment */", ""),
                // adding PRIVATE will increase test output size and run time
                EnumSet.of(Access.PUBLIC, Access.PROTECTED, Access.PACKAGE),
                // adding final will increase test output size and run time
                List.of("static", ""),
                List.of("void"),
                "main",
                List.of("String[] args", "")
        );
    }

    private Iterable<Method> otherMethods() {
        return generate(
                List.of("/** other comment */", ""),
                // adding PROTECTED or PUBLIC will increase test output size and run time
                EnumSet.of(Access.PACKAGE, Access.PRIVATE),
                // adding final or static will increase test output size and run time
                List.of(""),
                List.of("void"),
                "other",
                List.of(""));
    }

    private Iterable<Method> generate(Iterable<String> comments,
                                      Iterable<Access> accessModifiers,
                                      Iterable<String> otherModifiers,
                                      Iterable<String> returnValues,
                                      String name,
                                      Iterable<String> args) {
        var methods = new ArrayList<Method>();
        for (var comment : comments)
            for (var accessModifier : accessModifiers)
                for (var otherModifier : otherModifiers)
                    for (var returnValue : returnValues)
                        for (var arg : args)
                            methods.add(new Method(comment, accessModifier,
                                    otherModifier, returnValue, name, arg));
        return methods;
    }

    enum Access {PRIVATE, PACKAGE, PROTECTED, PUBLIC}

    record Method(String comment,
                  Access accessModifier,
                  String otherModifier,
                  String returnValue,
                  String name,
                  String arg) {

        @Override
        public String toString() {
            return Stream.of(comment, access(accessModifier), otherModifier,
                            returnValue, name + "(" + arg + ") { }")
                    .map(Object::toString)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.joining(" "));
        }
    }

    private static String access(Access accessModifier) {
        return switch (accessModifier) {
            case PRIVATE -> "private";
            case PACKAGE -> "";
            case PROTECTED -> "protected";
            case PUBLIC -> "public";
        };
    }
}
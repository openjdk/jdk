/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8307184
 * @summary Test basic operation of Elements.getDocComment
 * @library /tools/lib /tools/javac/lib
 * @build   toolbox.ToolBox JavacTestingAbstractProcessor TestGetDocComment_Line
 * @compile -processor TestGetDocComment_Line -proc:only TestGetDocComment_Line.java
 */

import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;

import toolbox.ToolBox;

/**
 * Test basic operation of Elements.getDocComment for line comments
 */
public class TestGetDocComment_Line extends JavacTestingAbstractProcessor {
    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment roundEnv) {
        if (!roundEnv.processingOver()) {
            boolean elementSeen = false;

            for (TypeElement typeRoot : ElementFilter.typesIn(roundEnv.getRootElements()) ) {
                for (Element element : typeRoot.getEnclosedElements()) {
                    ExpectedComment expectedComment = element.getAnnotation(ExpectedComment.class);
                    if (expectedComment != null ) {
                        elementSeen = true;
                        String expectedCommentStr = expectedComment.value();
                        String actualComment = elements.getDocComment(element);

                        if (!expectedCommentStr.equals(actualComment)) {
                            messager.printError("Unexpected doc comment found", element);
                            System.err.println("expect>>" + expectedCommentStr + "<<");
                            System.err.println("actual>>" + actualComment + "<<");
                            (new ToolBox()).checkEqual(expectedCommentStr.lines().toList(),
                                                       actualComment.lines().toList());
                        }
                    }
                }

                if (!elementSeen) {
                    throw new RuntimeException("No elements seen.");
                }
            }
        }
        return true;
    }

    @interface ExpectedComment {
        String value();
    }

    // Basic processing of interior lines
    ///Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do
    ///eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut
    ///enim ad minim veniam, quis nostrud exercitation ullamco laboris
    ///nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor
    ///in reprehenderit in voluptate velit esse cillum dolore eu
    ///fugiat nulla pariatur. Excepteur sint occaecat cupidatat non
    ///proident, sunt in culpa qui officia deserunt mollit anim id est
    ///laborum.
    @ExpectedComment("""
     Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do
     eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut
     enim ad minim veniam, quis nostrud exercitation ullamco laboris
     nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor
     in reprehenderit in voluptate velit esse cillum dolore eu
     fugiat nulla pariatur. Excepteur sint occaecat cupidatat non
     proident, sunt in culpa qui officia deserunt mollit anim id est
     laborum.""")
    // End-of-line-style comment
    @SuppressWarnings("") // A second preceding annotation
    /* Traditional comment */
    private void foo() {return ;}


    // Check removal of space characters;
    // excess / characters are not removed
    // use Unicode escape to test tab removal
///Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do
////eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut
/////enim ad minim veniam, quis nostrud exercitation ullamco laboris
//////nisi ut aliquip ex ea commodo consequat.
 \u0009///Duis aute irure dolor in reprehenderit in voluptate velit esse
 ////cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat
  /////cupidatat non proident, sunt in culpa qui officia deserunt mollit
                                            ///anim id est laborum.
    @ExpectedComment("""
       Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do
       /eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut
       //enim ad minim veniam, quis nostrud exercitation ullamco laboris
       ///nisi ut aliquip ex ea commodo consequat.
       Duis aute irure dolor in reprehenderit in voluptate velit esse
       /cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat
       //cupidatat non proident, sunt in culpa qui officia deserunt mollit
       anim id est laborum.""")
    @SuppressWarnings("") // A second preceding annotation
    // End-of-line-style comment
    /*
     * Traditional comment over multiple lines.
     */
    private void bar() {return ;}

    // Incidental whitespace _after_ the /// prefix is deleted;
    // additional indentation is _not_ deleted
    ///  Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do
    ///  eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut
    ///   enim ad minim veniam, quis nostrud exercitation ullamco laboris
    ///    nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor
    ///     in reprehenderit in voluptate velit esse cillum dolore eu
    ///  fugiat nulla pariatur. Excepteur sint occaecat cupidatat non
    ///  proident, sunt in culpa qui officia deserunt mollit anim id est
    ///  laborum.
    @ExpectedComment("""
     Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do
     eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut
      enim ad minim veniam, quis nostrud exercitation ullamco laboris
       nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor
        in reprehenderit in voluptate velit esse cillum dolore eu
     fugiat nulla pariatur. Excepteur sint occaecat cupidatat non
     proident, sunt in culpa qui officia deserunt mollit anim id est
     laborum.""")
    private void baz() {return ;}

    // Incidental space after /// is removed, but not space at the end of the line
    ///   Totality\u0020
    @ExpectedComment("Totality ")
    private void quux() {return ;}

    ///
    @ExpectedComment("")
    private void empty() {return ;}

    /// tail\u0020
    @ExpectedComment("tail ")
    private void tail() {return ;}

    // Testing of line terminators, javac implementation normalizes them:
    // * newline: \u000A
    // * carriage return: \u000D
    // * * carriage return + newline: \u000D\u000A
    /// Lorem\u000A/// \u000D/// \u000D\u000A/// ipsum
    @ExpectedComment("Lorem\n\n\nipsum")
    private void wombat2() {return ;}
}

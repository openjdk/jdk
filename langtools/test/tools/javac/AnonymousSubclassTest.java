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

/*
 * @test
 * @bug 8023945
 * @summary javac wrongly allows a subclass of an anonymous class
 * @library /tools/javac/lib
 * @build ToolBox
 * @run main AnonymousSubclassTest
 */

import java.util.ArrayList;
import java.io.IOException;

public class AnonymousSubclassTest {
    public static void main(String... args) throws Exception {
        new AnonymousSubclassTest().run();
    }

    // To trigger the error we want, first we need to compile
    // a class with an anonymous inner class: Foo$1.
    final String foo =
        "public class Foo {" +
        "  void m() { Foo f = new Foo() {}; }" +
        "}";

    // Then, we try to subclass the anonymous class
    // Note: we must do this in two classes because a different
    // error will be generated if we don't load Foo$1 through the
    // class reader.
    final String test1 =
        "public class Test1 {" +
        "  void m() {"+
        "    Foo f1 = new Foo();"+
        "    Foo f2 = new Foo$1(f1) {};"+
        "  }" +
        "}";

    final String test2 =
        "public class Test2 {" +
        "  class T extends Foo$1 {" +
        "    public T(Foo f) { super(f); }" +
        "  }"+
        "}";

    void compOk(String code) throws Exception {
        ToolBox.javac(new ToolBox.JavaToolArgs().setSources(code));
    }

    void compFail(String code) throws Exception {
        ArrayList<String> errors = new ArrayList<>();
        ToolBox.JavaToolArgs args = new ToolBox.JavaToolArgs();
        args.setSources(code)
            .appendArgs("-cp", ".", "-XDrawDiagnostics")
            .set(ToolBox.Expect.FAIL)
            .setErrOutput(errors);
        ToolBox.javac(args);

        if (!errors.get(0).contains("cant.inherit.from.anon")) {
            System.out.println(errors.get(0));
            throw new Exception("test failed");
        }
    }

    void run() throws Exception {
        compOk(foo);
        compFail(test1);
        compFail(test2);
    }
}

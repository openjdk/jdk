/*
 * Copyright (c) 2005, 2008, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6304912
 * @summary unit test for Log
 */
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.parser.Parser;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.parser.Scanner;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.Options;

public class TestLog
{
    public static void main(String... args) throws IOException {
        test(false);
        test(true);
    }

    static void test(boolean genEndPos) throws IOException {
        Context context = new Context();

        Options options = Options.instance(context);
        options.put("diags", "%b:%s/%o/%e:%_%t%m|%p%m");

        Log log = Log.instance(context);
        log.multipleErrors = true;

        JavacFileManager.preRegister(context);
        Scanner.Factory sfac = Scanner.Factory.instance(context);
        ParserFactory pfac = ParserFactory.instance(context);

        final String text =
              "public class Foo {\n"
            + "  public static void main(String[] args) {\n"
            + "    if (args.length == 0)\n"
            + "      System.out.println(\"no args\");\n"
            + "    else\n"
            + "      System.out.println(args.length + \" args\");\n"
            + "  }\n"
            + "}\n";
        JavaFileObject fo = new StringJavaFileObject("Foo", text);
        log.useSource(fo);

        CharSequence cs = fo.getCharContent(true);
        Parser parser = pfac.newParser(cs, false, genEndPos, false);
        JCTree.JCCompilationUnit tree = parser.parseCompilationUnit();
        log.setEndPosTable(fo, tree.endPositions);

        TreeScanner ts = new LogTester(log, tree.endPositions);
        ts.scan(tree);

        check(log.nerrors, 4, "errors");
        check(log.nwarnings, 4, "warnings");
    }

    private static void check(int found, int expected, String name) {
        if (found == expected)
            System.err.println(found + " " + name + " found, as expected.");
        else {
            System.err.println("incorrect number of " + name + " found.");
            System.err.println("expected: " + expected);
            System.err.println("   found: " + found);
            throw new IllegalStateException("test failed");
        }
    }

    private static class LogTester extends TreeScanner {
        LogTester(Log log, java.util.Map<JCTree, Integer> endPositions) {
            this.log = log;
            this.endPositions = endPositions;
        }

        public void visitIf(JCTree.JCIf tree) {
            JCDiagnostic.DiagnosticPosition nil = null;
            // generate dummy messages to exercise the log API
            log.error("not.stmt");
            log.error(tree.pos, "not.stmt");
            log.error(tree.pos(), "not.stmt");
            log.error(nil, "not.stmt");

            log.warning("div.zero");
            log.warning(tree.pos, "div.zero");
            log.warning(tree.pos(), "div.zero");
            log.warning(nil, "div.zero");
        }

        private Log log;
        private java.util.Map<JCTree, Integer> endPositions;
    }

    private static class StringJavaFileObject extends SimpleJavaFileObject {
        StringJavaFileObject(String name, String text) {
            super(URI.create(name), JavaFileObject.Kind.SOURCE);
            this.text = text;
        }
        public CharSequence getCharContent(boolean b) {
            return text;
        }
        public InputStream openInputStream() {
            throw new UnsupportedOperationException();
        }
        public OutputStream openOutputStream() {
            throw new UnsupportedOperationException();
        }
        private String text;
    }
}

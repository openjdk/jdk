/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8254073
 * @modules jdk.compiler/com.sun.tools.javac.parser
 *          jdk.compiler/com.sun.tools.javac.util
 * @summary Proper lexing of various token kinds.
 */

import java.io.IOException;
import java.net.URI;
import java.util.Objects;

import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;

import com.sun.tools.javac.parser.JavaTokenizer;
import com.sun.tools.javac.parser.ScannerFactory;
import com.sun.tools.javac.parser.Tokens.Token;
import com.sun.tools.javac.parser.Tokens.TokenKind;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;

import static com.sun.tools.javac.parser.Tokens.TokenKind.*;

public class JavaLexerTest2 {
    static final TestTuple[] TESTS = {
            new TestTuple("0bL",         LONGLITERAL, true),
            new TestTuple("0b20L",       LONGLITERAL, true),

            new TestTuple("0xL",         LONGLITERAL, true),
            new TestTuple("0xG000L",     LONGLITERAL, true),

            new TestTuple("0.0f",        FLOATLITERAL, false),
            new TestTuple("0.0F",        FLOATLITERAL, false),
            new TestTuple(".0F",         FLOATLITERAL, false),
            new TestTuple("0.F",         FLOATLITERAL, false),
            new TestTuple("0E0F",        FLOATLITERAL, false),
            new TestTuple("0E+0F",       FLOATLITERAL, false),
            new TestTuple("0E-0F",       FLOATLITERAL, false),
            new TestTuple("0E*0F",       FLOATLITERAL, true),

            new TestTuple("0.0d",        DOUBLELITERAL, false),
            new TestTuple("0.0D",        DOUBLELITERAL, false),
            new TestTuple(".0D",         DOUBLELITERAL, false),
            new TestTuple("0.D",         DOUBLELITERAL, false),
            new TestTuple("0E0D",        DOUBLELITERAL, false),
            new TestTuple("0E+0D",       DOUBLELITERAL, false),
            new TestTuple("0E-0D",       DOUBLELITERAL, false),
            new TestTuple("0E*0D",       DOUBLELITERAL, true),

            new TestTuple("0x0.0p0d",    DOUBLELITERAL, false),
            new TestTuple("0xff.0p8d",   DOUBLELITERAL, false),
            new TestTuple("0xp8d",       DOUBLELITERAL, true),
            new TestTuple("0x8pd",       DOUBLELITERAL, true),
            new TestTuple("0xpd",        DOUBLELITERAL, true),

            new TestTuple("\"\\u2022\"", STRINGLITERAL, false),
            new TestTuple("\"\\u20\"",   STRINGLITERAL, true),
            new TestTuple("\"\\u\"",     STRINGLITERAL, true),
            new TestTuple("\"\\uG000\"", STRINGLITERAL, true),
            new TestTuple("\"\\u \"",    STRINGLITERAL, true),

            new TestTuple("\"\\b\\t\\n\\f\\r\\\'\\\"\\\\\"", STRINGLITERAL, false),
            new TestTuple("\"\\q\"",     STRINGLITERAL, true),

            new TestTuple("\'\'",        CHARLITERAL, true),
            new TestTuple("\'\\b\'",     CHARLITERAL, false),
            new TestTuple("\'\\t\'",     CHARLITERAL, false),
            new TestTuple("\'\\n\'",     CHARLITERAL, false),
            new TestTuple("\'\\f\'",     CHARLITERAL, false),
            new TestTuple("\'\\r\'",     CHARLITERAL, false),
            new TestTuple("\'\\'\'",     CHARLITERAL, false),
            new TestTuple("\'\\\\'",     CHARLITERAL, false),
            new TestTuple("\'\\\'\'",    CHARLITERAL, false),
            new TestTuple("\'\\\"\'",    CHARLITERAL, false),
            new TestTuple("\'\\q\'",     CHARLITERAL, true),

            new TestTuple("abc\\u0005def",IDENTIFIER, false),
    };

    static class TestTuple {
        String input;
        TokenKind kind;
        String expected;
        boolean willFail;

        TestTuple(String input, TokenKind kind, String expected, boolean willFail) {
            this.input = input;
            this.kind = kind;
            this.expected = expected;
            this.willFail = willFail;
        }

        TestTuple(String input, TokenKind kind, boolean willFail) {
            this(input, kind, input, willFail);
        }
    }

    void assertTest(Token token, TestTuple test) {
        boolean normal = token != null == !test.willFail;

        if (!normal) {
            String message = test.willFail ? "Expected to fail: "
                                           : "Expected to pass: ";
            throw new AssertionError(message + test.input);
        }

        if (token != null) {
            String actual = test.input.substring(token.pos, token.endPos);

            if (token.kind != test.kind) {
                throw new AssertionError("Unexpected token kind: " + token.kind);
            }

            if (!Objects.equals(test.expected, actual)) {
                throw new AssertionError("Unexpected token content: " + actual);
            }
        }
    }

    Token readToken(String input) throws Exception {
        Context ctx = new Context();
        Log log = Log.instance(ctx);

        log.useSource(new SimpleJavaFileObject(new URI("mem://Test.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
                return input;
            }
        });

        char[] inputArr = input.toCharArray();
        JavaTokenizer tokenizer = new JavaTokenizer(ScannerFactory.instance(ctx), inputArr, inputArr.length) {};
        Token token = tokenizer.readToken();

        return log.nerrors == 0 ? token : null;
    }

    void run() throws Exception {
        for (TestTuple test : TESTS) {
            Token token = readToken(test.input);
            assertTest(token, test);
        }

        System.out.println("Done!");
    }

    public static void main(String[] args) throws Exception {
        new JavaLexerTest2().run();
    }
}

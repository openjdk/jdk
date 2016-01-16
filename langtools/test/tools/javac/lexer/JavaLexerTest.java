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
 * @bug 8056897
 * @summary Proper lexing of integer literals.
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

public class JavaLexerTest {
    public static void main(String... args) throws Exception {
        new JavaLexerTest().run();
    }

    void run() throws Exception {
        Context ctx = new Context();
        Log log = Log.instance(ctx);
        String input = "0bL 0b20L 0xL ";
        log.useSource(new SimpleJavaFileObject(new URI("mem://Test.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
                return input;
            }
        });
        char[] inputArr = input.toCharArray();
        JavaTokenizer tokenizer = new JavaTokenizer(ScannerFactory.instance(ctx), inputArr, inputArr.length) {
        };

        assertKind(input, tokenizer, TokenKind.LONGLITERAL, "0bL");
        assertKind(input, tokenizer, TokenKind.LONGLITERAL, "0b20L");
        assertKind(input, tokenizer, TokenKind.LONGLITERAL, "0xL");
    }

    void assertKind(String input, JavaTokenizer tokenizer, TokenKind kind, String expectedText) {
        Token token = tokenizer.readToken();

        if (token.kind != kind) {
            throw new AssertionError("Unexpected token kind: " + token.kind);
        }

        String actualText = input.substring(token.pos, token.endPos);

        if (!Objects.equals(actualText, expectedText)) {
            throw new AssertionError("Unexpected token text: " + actualText);
        }
    }
}
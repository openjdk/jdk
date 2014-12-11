/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.oracle.security.ucrypto;

import java.io.*;
import static java.io.StreamTokenizer.*;
import java.math.BigInteger;
import java.util.*;

import java.security.*;

import sun.security.action.GetPropertyAction;
import sun.security.util.PropertyExpander;

/**
 * Configuration container and file parsing.
 *
 * Currently, there is only one supported entry "disabledServices"
 * for disabling crypto services. Its syntax is as follows:
 *
 * disabledServices = {
 * <ServiceType>.<Algorithm>
 * ...
 * }
 *
 * where <Service> can be "MessageDigest", "Cipher", etc. and <Algorithm>
 * reprepresents the value that's passed into the various getInstance() calls.
 *
 * @since   1.9
 */
final class Config {

    // Reader and StringTokenizer used during parsing
    private Reader reader;

    private StreamTokenizer st;

    private Set<String> parsedKeywords;

    // set of disabled crypto services, e.g. MessageDigest.SHA1, or
    // Cipher.AES/ECB/PKCS5Padding
    private Set<String> disabledServices;

    Config(String filename) throws IOException {
        FileInputStream in = new FileInputStream(expand(filename));
        reader = new BufferedReader(new InputStreamReader(in));
        parsedKeywords = new HashSet<String>();
        st = new StreamTokenizer(reader);
        setupTokenizer();
        parse();
    }

    String[] getDisabledServices() {
        if (disabledServices != null) {
            return disabledServices.toArray(new String[disabledServices.size()]);
        } else {
            return new String[0];
        }
    }

    private static String expand(final String s) throws IOException {
        try {
            return PropertyExpander.expand(s);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private void setupTokenizer() {
        st.resetSyntax();
        st.wordChars('a', 'z');
        st.wordChars('A', 'Z');
        st.wordChars('0', '9');
        st.wordChars(':', ':');
        st.wordChars('.', '.');
        st.wordChars('_', '_');
        st.wordChars('-', '-');
        st.wordChars('/', '/');
        st.wordChars('\\', '\\');
        st.wordChars('$', '$');
        st.wordChars('{', '{'); // need {} for property subst
        st.wordChars('}', '}');
        st.wordChars('*', '*');
        st.wordChars('+', '+');
        st.wordChars('~', '~');
        // XXX check ASCII table and add all other characters except special

        // special: #="(),
        st.whitespaceChars(0, ' ');
        st.commentChar('#');
        st.eolIsSignificant(true);
        st.quoteChar('\"');
    }

    private ConfigException excToken(String msg) {
        return new ConfigException(msg + " " + st);
    }

    private ConfigException excLine(String msg) {
        return new ConfigException(msg + ", line " + st.lineno());
    }

    private void parse() throws IOException {
        while (true) {
            int token = nextToken();
            if (token == TT_EOF) {
                break;
            }
            if (token == TT_EOL) {
                continue;
            }
            if (token != TT_WORD) {
                throw excToken("Unexpected token:");
            }
            String word = st.sval;
            if (word.equals("disabledServices")) {
                parseDisabledServices(word);
            } else {
                throw new ConfigException
                        ("Unknown keyword '" + word + "', line " + st.lineno());
            }
            parsedKeywords.add(word);
        }
        reader.close();
        reader = null;
        st = null;
        parsedKeywords = null;
    }

    //
    // Parsing helper methods
    //
    private int nextToken() throws IOException {
        int token = st.nextToken();
        return token;
    }

    private void parseEquals() throws IOException {
        int token = nextToken();
        if (token != '=') {
            throw excToken("Expected '=', read");
        }
    }

    private void parseOpenBraces() throws IOException {
        while (true) {
            int token = nextToken();
            if (token == TT_EOL) {
                continue;
            }
            if ((token == TT_WORD) && st.sval.equals("{")) {
                return;
            }
            throw excToken("Expected '{', read");
        }
    }

    private boolean isCloseBraces(int token) {
        return (token == TT_WORD) && st.sval.equals("}");
    }

    private void checkDup(String keyword) throws IOException {
        if (parsedKeywords.contains(keyword)) {
            throw excLine(keyword + " must only be specified once");
        }
    }

    private void parseDisabledServices(String keyword) throws IOException {
        checkDup(keyword);
        disabledServices = new HashSet<String>();
        parseEquals();
        parseOpenBraces();
        while (true) {
            int token = nextToken();
            if (isCloseBraces(token)) {
                break;
            }
            if (token == TT_EOL) {
                continue;
            }
            if (token != TT_WORD) {
                throw excToken("Expected mechanism, read");
            }
            disabledServices.add(st.sval);
        }
    }
}

class ConfigException extends IOException {
    private static final long serialVersionUID = 254492758127673194L;
    ConfigException(String msg) {
        super(msg);
    }
}

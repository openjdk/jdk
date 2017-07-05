/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.activation.registries;

/**
 *      A tokenizer for strings in the form of "foo/bar; prop1=val1; ... ".
 *      Useful for parsing MIME content types.
 */
public class MailcapTokenizer {

    public static final int UNKNOWN_TOKEN = 0;
    public static final int START_TOKEN = 1;
    public static final int STRING_TOKEN = 2;
    public static final int EOI_TOKEN = 5;
    public static final int SLASH_TOKEN = '/';
    public static final int SEMICOLON_TOKEN = ';';
    public static final int EQUALS_TOKEN = '=';

    /**
     *  Constructor
     *
     *  @param  inputString the string to tokenize
     */
    public MailcapTokenizer(String inputString) {
        data = inputString;
        dataIndex = 0;
        dataLength = inputString.length();

        currentToken = START_TOKEN;
        currentTokenValue = "";

        isAutoquoting = false;
        autoquoteChar = ';';
    }

    /**
     *  Set whether auto-quoting is on or off.
     *
     *  Auto-quoting means that all characters after the first
     *  non-whitespace, non-control character up to the auto-quote
     *  terminator character or EOI (minus any whitespace immediatley
     *  preceeding it) is considered a token.
     *
     *  This is required for handling command strings in a mailcap entry.
     */
    public void setIsAutoquoting(boolean value) {
        isAutoquoting = value;
    }

    /**
     *  Retrieve current token.
     *
     *  @return    The current token value
     */
    public int getCurrentToken() {
        return currentToken;
    }

    /*
     *  Get a String that describes the given token.
     */
    public static String nameForToken(int token) {
        String name = "really unknown";

        switch(token) {
            case UNKNOWN_TOKEN:
                name = "unknown";
                break;
            case START_TOKEN:
                name = "start";
                break;
            case STRING_TOKEN:
                name = "string";
                break;
            case EOI_TOKEN:
                name = "EOI";
                break;
            case SLASH_TOKEN:
                name = "'/'";
                break;
            case SEMICOLON_TOKEN:
                name = "';'";
                break;
            case EQUALS_TOKEN:
                name = "'='";
                break;
        }

        return name;
    }

    /*
     *  Retrieve current token value.
     *
     *  @return    A String containing the current token value
     */
    public String getCurrentTokenValue() {
        return currentTokenValue;
    }
    /*
     *  Process the next token.
     *
     *  @return    the next token
     */
    public int nextToken() {
        if (dataIndex < dataLength) {
            //  skip white space
            while ((dataIndex < dataLength) &&
                    (isWhiteSpaceChar(data.charAt(dataIndex)))) {
                ++dataIndex;
            }

            if (dataIndex < dataLength) {
                //  examine the current character and see what kind of token we have
                char c = data.charAt(dataIndex);
                if (isAutoquoting) {
                    if (c == ';' || c == '=') {
                        currentToken = c;
                        currentTokenValue = new Character(c).toString();
                        ++dataIndex;
                    } else {
                        processAutoquoteToken();
                    }
                } else {
                    if (isStringTokenChar(c)) {
                        processStringToken();
                    } else if ((c == '/') || (c == ';') || (c == '=')) {
                        currentToken = c;
                        currentTokenValue = new Character(c).toString();
                        ++dataIndex;
                    } else {
                        currentToken = UNKNOWN_TOKEN;
                        currentTokenValue = new Character(c).toString();
                        ++dataIndex;
                    }
                }
            } else {
                currentToken = EOI_TOKEN;
                currentTokenValue = null;
            }
        } else {
            currentToken = EOI_TOKEN;
            currentTokenValue = null;
        }

        return currentToken;
    }

    private void processStringToken() {
        //  capture the initial index
        int initialIndex = dataIndex;

        //  skip to 1st non string token character
        while ((dataIndex < dataLength) &&
                isStringTokenChar(data.charAt(dataIndex))) {
            ++dataIndex;
        }

        currentToken = STRING_TOKEN;
        currentTokenValue = data.substring(initialIndex, dataIndex);
    }

    private void processAutoquoteToken() {
        //  capture the initial index
        int initialIndex = dataIndex;

        //  now skip to the 1st non-escaped autoquote termination character
        //  XXX - doesn't actually consider escaping
        boolean foundTerminator = false;
        while ((dataIndex < dataLength) && !foundTerminator) {
            char c = data.charAt(dataIndex);
            if (c != autoquoteChar) {
                ++dataIndex;
            } else {
                foundTerminator = true;
            }
        }

        currentToken = STRING_TOKEN;
        currentTokenValue =
            fixEscapeSequences(data.substring(initialIndex, dataIndex));
    }

    private static boolean isSpecialChar(char c) {
        boolean lAnswer = false;

        switch(c) {
            case '(':
            case ')':
            case '<':
            case '>':
            case '@':
            case ',':
            case ';':
            case ':':
            case '\\':
            case '"':
            case '/':
            case '[':
            case ']':
            case '?':
            case '=':
                lAnswer = true;
                break;
        }

        return lAnswer;
    }

    private static boolean isControlChar(char c) {
        return Character.isISOControl(c);
    }

    private static boolean isWhiteSpaceChar(char c) {
        return Character.isWhitespace(c);
    }

    private static boolean isStringTokenChar(char c) {
        return !isSpecialChar(c) && !isControlChar(c) && !isWhiteSpaceChar(c);
    }

    private static String fixEscapeSequences(String inputString) {
        int inputLength = inputString.length();
        StringBuffer buffer = new StringBuffer();
        buffer.ensureCapacity(inputLength);

        for (int i = 0; i < inputLength; ++i) {
            char currentChar = inputString.charAt(i);
            if (currentChar != '\\') {
                buffer.append(currentChar);
            } else {
                if (i < inputLength - 1) {
                    char nextChar = inputString.charAt(i + 1);
                    buffer.append(nextChar);

                    //  force a skip over the next character too
                    ++i;
                } else {
                    buffer.append(currentChar);
                }
            }
        }

        return buffer.toString();
    }

    private String  data;
    private int     dataIndex;
    private int     dataLength;
    private int     currentToken;
    private String  currentTokenValue;
    private boolean isAutoquoting;
    private char    autoquoteChar;

    /*
    public static void main(String[] args) {
        for (int i = 0; i < args.length; ++i) {
            MailcapTokenizer tokenizer = new MailcapTokenizer(args[i]);

            System.out.println("Original: |" + args[i] + "|");

            int currentToken = tokenizer.nextToken();
            while (currentToken != EOI_TOKEN) {
                switch(currentToken) {
                    case UNKNOWN_TOKEN:
                        System.out.println("  Unknown Token:           |" + tokenizer.getCurrentTokenValue() + "|");
                        break;
                    case START_TOKEN:
                        System.out.println("  Start Token:             |" + tokenizer.getCurrentTokenValue() + "|");
                        break;
                    case STRING_TOKEN:
                        System.out.println("  String Token:            |" + tokenizer.getCurrentTokenValue() + "|");
                        break;
                    case EOI_TOKEN:
                        System.out.println("  EOI Token:               |" + tokenizer.getCurrentTokenValue() + "|");
                        break;
                    case SLASH_TOKEN:
                        System.out.println("  Slash Token:             |" + tokenizer.getCurrentTokenValue() + "|");
                        break;
                    case SEMICOLON_TOKEN:
                        System.out.println("  Semicolon Token:         |" + tokenizer.getCurrentTokenValue() + "|");
                        break;
                    case EQUALS_TOKEN:
                        System.out.println("  Equals Token:            |" + tokenizer.getCurrentTokenValue() + "|");
                        break;
                    default:
                        System.out.println("  Really Unknown Token:    |" + tokenizer.getCurrentTokenValue() + "|");
                        break;
                }

                currentToken = tokenizer.nextToken();
            }

            System.out.println("");
        }
    }
    */
}

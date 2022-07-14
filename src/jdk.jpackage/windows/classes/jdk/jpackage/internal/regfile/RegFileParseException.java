/*
 * Copyright (c) 2022, Red Hat Inc. and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal.regfile;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.jpackage.internal.regfile.parser.ParseException;
import jdk.jpackage.internal.regfile.parser.Token;
import jdk.jpackage.internal.regfile.parser.TokenMgrError;

public class RegFileParseException extends Exception {


    private RegFileParseException(int beginLine, int beginColumn, int endLine, int endColumn,
                                  String parseErrorMessage) {
        super(createMessage(beginLine, beginColumn, endLine, endColumn, parseErrorMessage));
        this.beginLine = beginLine;
        this.beginColumn = beginColumn;
        this.endLine = endLine;
        this.endColumn = endColumn;
        this.parseErrorMessage = parseErrorMessage;
    }

    public static RegFileParseException fromTokenMgrError(TokenMgrError tokenMgrError) {
        Matcher matcher = TOKEN_MGR_ERROR_REGEX.matcher(tokenMgrError.getMessage());
        int beginLine = 0;
        int beginColumn = 0;
        if (matcher.matches()) {
            beginLine = Integer.parseInt(matcher.group(1));
            beginColumn = Integer.parseInt(matcher.group(2));
        }
        return new RegFileParseException(beginLine, beginColumn, beginLine, beginColumn, tokenMgrError.getMessage());
    }

    public static RegFileParseException fromParseException(ParseException parseException) {
        return fromToken(parseException.currentToken, parseException.getMessage());
    }

    public static RegFileParseException fromTokenException(RegFileTokenException tokenException) {
        return fromToken(tokenException.getToken(), tokenException.getMessage());
    }

    private static RegFileParseException fromToken(Token token, String parseErrorMessage) {
        return new RegFileParseException(token.beginLine, token.beginColumn, token.endLine,
                token.endColumn, parseErrorMessage);
    }

    private static String createMessage(int beginLine, int beginColumn, int endLine, int endColumn,
                                              String parseErrorMessage) {
        return new StringBuilder("Registry file parsing error,")
                .append(" begin line: ").append(beginLine).append(",")
                .append(" begin column: ").append(beginColumn).append(",")
                .append(" end line: ").append(endLine).append(",")
                .append(" end column: ").append(endColumn).append(",")
                .append(" message: ").append(parseErrorMessage)
                .toString();
    }

    public int getBeginLine() {
        return beginLine;
    }

    public int getBeginColumn() {
        return beginColumn;
    }

    public int getEndLine() {
        return endLine;
    }

    public int getEndColumn() {
        return endColumn;
    }

    public String getParseErrorMessage() {
        return parseErrorMessage;
    }

    private final int beginLine;
    private final int beginColumn;
    private final int endLine;
    private final int endColumn;
    private final String parseErrorMessage;

    private static final long serialVersionUID = 1L;
    private static final Pattern TOKEN_MGR_ERROR_REGEX =
            Pattern.compile("^Lexical error at line (\\d+), column (\\d+)\\..*$");
}

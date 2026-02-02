/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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


#ifndef StringProcessor_h
#define StringProcessor_h

#include <map>
#include "tstrings.h"


namespace StringProcessing {

enum TokenType {
    STRING,
    VARIABLE,
    ESCAPED_CHAR
};

class Token {
public:
    Token(TokenType type, const tstring& str): theType(type), theStr(str) {
    }

    TokenType type() const {
        return theType;
    }

    const tstring& value() const {
        return theStr;
    }

private:
    TokenType theType;
    tstring theStr;
};

typedef std::vector<Token> TokenizedString;
typedef std::vector<tstring> VariableNameList;
#ifdef _WIN32
struct less_ignore_case {
    bool operator() (const tstring& x, const tstring& y) const {
        return std::less<tstring>()(tstrings::toLower(x), tstrings::toLower(y));
    }
};
typedef std::map<tstring, tstring, less_ignore_case> VariableValues;
#else
typedef std::map<tstring, tstring> VariableValues;
#endif

TokenizedString tokenize(const tstring& str);

tstring stringify(const TokenizedString& tokens);

VariableNameList extractVariableNames(const TokenizedString& tokens);

void expandVariables(TokenizedString& tokens, const VariableValues& variableValues);

} // namespace StringProcessing

#endif // StringProcessor_h

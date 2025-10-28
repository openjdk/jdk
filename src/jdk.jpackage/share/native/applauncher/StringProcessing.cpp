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

#include <algorithm>
#include "StringProcessing.h"


namespace StringProcessing {

namespace {

class TokenBuilder {
public:
    TokenBuilder(const tstring& v): cur(v.c_str()) {
    }

    void addNextToken(tstring::const_pointer end, TokenType type, TokenizedString& tokens) {
        if (end != cur) {
            const auto value = tstring(cur, end - cur);
            cur = end;
            tokens.push_back(Token(type, value));
        }
    }

private:
    tstring::const_pointer cur;
};

bool isValidVariableFirstChar(tstring::value_type chr) {
    if ('A' <= chr && chr <= 'Z') {
        return true;
    } else if ('a' <= chr && chr <= 'z') {
        return true;
    } else if ('_' == chr) {
        return true;
    } else {
        return false;
    }
}

bool isValidVariableOtherChar(tstring::value_type chr) {
    if (isValidVariableFirstChar(chr)) {
        return true;
    } else if ('0' <= chr && chr <= '9') {
        return true;
    } else {
        return false;
    }
}

} //  namespace

TokenizedString tokenize(const tstring& str) {
    TokenizedString tokens;
    TokenBuilder tb(str);
    tstring::const_pointer cur = str.c_str();
    const tstring::const_pointer end = cur + str.length();
    while (cur != end) {
        if (*cur == '\\' && cur + 1 != end) {
            const auto maybeNextToken = cur++;
            if (*cur == '\\' || *cur == '$') {
                tb.addNextToken(maybeNextToken, STRING, tokens);
                tb.addNextToken(++cur, ESCAPED_CHAR, tokens);
                continue;
            }
        } else if (*cur == '$' && cur + 1 != end) {
            const auto maybeNextToken = cur++;
            bool variableFound = false;
            if (*cur == '{') {
                do {
                    cur++;
                } while (cur != end && *cur != '}');
                if (cur != end) {
                    variableFound = true;
                    cur++;
                }
            } else if (isValidVariableFirstChar(*cur)) {
                variableFound = true;
                do {
                    cur++;
                } while (cur != end && isValidVariableOtherChar(*cur));
            } else {
                continue;
            }
            if (variableFound) {
                tb.addNextToken(maybeNextToken, STRING, tokens);
                tb.addNextToken(cur, VARIABLE, tokens);
            }
        } else {
            ++cur;
        }
    }
    tb.addNextToken(cur, STRING, tokens);
    return tokens;
}


tstring stringify(const TokenizedString& tokens) {
    tstringstream ss;
    TokenizedString::const_iterator it = tokens.begin();
    const TokenizedString::const_iterator end = tokens.end();
    for (; it != end; ++it) {
        if (it->type() == ESCAPED_CHAR) {
            ss << it->value().substr(1);
        } else {
            ss << it->value();
        }
    }
    return ss.str();
}


namespace {

tstring getVariableName(const tstring& str) {
    if (tstrings::endsWith(str, _T("}"))) {
        // ${VAR}
        return str.substr(2, str.length() - 3);
    } else {
        // $VAR
        return str.substr(1);
    }
}

} // namespace

VariableNameList extractVariableNames(const TokenizedString& tokens) {
    VariableNameList reply;

    TokenizedString::const_iterator it = tokens.begin();
    const TokenizedString::const_iterator end = tokens.end();
    for (; it != end; ++it) {
        if (it->type() == VARIABLE) {
            reply.push_back(getVariableName(it->value()));
        }
    }

    std::sort(reply.begin(), reply.end());
    reply.erase(std::unique(reply.begin(), reply.end()), reply.end());
    return reply;
}


void expandVariables(TokenizedString& tokens, const VariableValues& variableValues) {
    TokenizedString::iterator it = tokens.begin();
    const TokenizedString::iterator end = tokens.end();
    for (; it != end; ++it) {
        if (it->type() == VARIABLE) {
            const auto entry = variableValues.find(getVariableName(it->value()));
            if (entry != variableValues.end()) {
                auto newToken = Token(STRING, entry->second);
                std::swap(*it, newToken);
            }
        }
    }
}

} // namespace StringProcessing

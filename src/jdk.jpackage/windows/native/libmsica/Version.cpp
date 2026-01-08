/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

#define NOMINMAX
#include "Version.h"



namespace VersionDetails {

size_t Parser::operator ()(const tstring& str, int& buffer,
                                                    size_t& bufferSize) const {
    if (bufferSize < 1) {
        JP_THROW(tstrings::any() << "Destination buffer can't be empty");
    }

    tstring_array strComponents;

    tstrings::split(strComponents, str, _T("."));

    // Temporary storage. Needed to preserve destination buffer from
    // partial update if parsing fails.
    std::vector<int> recognizedComponents;

    tstring_array::const_iterator it = strComponents.begin();
    tstring_array::const_iterator end =
                            it + std::min(strComponents.size(), bufferSize);

    // Number of successfully parsed characters in 'str'.
    size_t cursor = 0;

    while (it != end) {
        const tstring& strComponent(*it);

        try {
            recognizedComponents.push_back(parseComponent(strComponent));
        } catch (const std::exception&) {
            // error parsing version component
            break;
        }

        cursor += strComponent.size();
        if (++it != end) {
            ++cursor;
        }
    }

    if (str.size() < cursor) {
        // Should never happen.
        JP_THROW(tstrings::any()
                        << "[" << cursor << " < " << str.size() << "] failed");
    }

    // Publish results only after successful parse.
    bufferSize = recognizedComponents.size();
    if (bufferSize) {
        memcpy(&buffer, &*recognizedComponents.begin(),
                                                bufferSize * sizeof(buffer));
    }

    if (!strComponents.empty() && strComponents.back().size() == 0
                                                    && str.size() == cursor) {
        // Input string ends with dot character (.). Mark it as unrecognized.
        --cursor;
    }

    return (str.size() - cursor);
}


int parseComponent (const tstring& str) {
    tistringstream input(str);

    do {
        if (str.empty() || !isdigit(str[0])) {
            break;
        }

        int reply;
        input >> reply;

        if (!input.eof() || input.fail()) {
            break;
        }

        return reply;
    } while (false);

    JP_THROW(tstrings::any()
            << "Failed to recognize version component in [" << str << "]");
}

} // namespace VersionDetails

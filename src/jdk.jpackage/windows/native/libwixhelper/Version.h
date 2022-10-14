/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef Version_h
#define Version_h

#include <cstring>
#include <stdexcept>

#include "tstrings.h"
#include "ErrorHandling.h"




/**
 * Generic version. Version is given with string.
 * String consists of version components separated with dot char (.).
 * E.g.: 1.45.6.778.89
 * Maximum number of components in version string is specified with 'N'
 * template parameter.
 */


namespace VersionDetails {

struct Parser {
    /**
     * Parses version components from the given string.
     * Returns number of trailing unrecognised characters or 0 if the whole
     * string has been recognized.
     *
     *  @param str
     *      string to parse;
     *  @param buffer
     *      reference to buffer accepting parsed version components;
     *  @param bufferSize[in,out]
     *      number of elements in the destination buffer; on return is set
     *      a number of components recognized in the input string.
     */
    size_t operator () (const tstring& str, int& buffer,
                                                    size_t& bufferSize) const;
};


/**
 * Returns parsed single version component from the given string.
 * Throws std::exception if error occurs parsing the given string.
 *
 *  @param str
 *      string to parse;
 */
int parseComponent (const tstring& str);


template <int N, class Parser, int MinComponentCount=0>
class Base {
public:
    enum { ComponentCount = N };
    Base() {
        memset(components, 0, sizeof(components));
    }

private:
    bool verifyComponentCount(size_t recognizedComponentCount) const {
        return true;
    }

protected:
    void init(const tstring& str) {
        size_t recognizedComponentCount = N;
        const size_t unrecognisedChars = Parser()(str, *components,
                                                    recognizedComponentCount);
        if (unrecognisedChars) {
            JP_THROW(tstrings::any()
                    << "Failed to parse [" << str << "] version string completely."
                    << " Number of unrecognized characters is " << unrecognisedChars);
        }

        if (recognizedComponentCount < MinComponentCount || !verifyComponentCount(recognizedComponentCount)) {
            // Input string is too short.
            JP_THROW(tstrings::any() << "Failed to parse [" << str
                                      << "] version string. The string is too short");

        }
        strValue = str;
    }

public:
    const tstring& source() const {
        return strValue;
    }

    bool operator < (const Base& other) const {
        for (int i = 0; i < N; ++i) {
            const int a = components[i];
            const int b = other.components[i];
            if (a < b) {
                return true;
            }
            if (b < a) {
                return false;
            }
        }
        return false;
    }

    bool operator <= (const Base& other) const {
        return *this == other || *this < other;
    }

    bool operator > (const Base& other) const {
        return ! (*this <= other);
    }

    bool operator >= (const Base& other) const {
        return ! (*this < other);
    }

    bool operator == (const Base& other) const {
        return (0 == memcmp(components, other.components, sizeof(components)));
    }

    bool operator != (const Base& other) const {
        return ! (*this == other);
    }

protected:
    int components[N];

private:
    tstring strValue;
};

} // namespace VersionDetails


template <class Base>
struct Version: public Base {
    Version() {
    }

    explicit Version(const tstring& str) {
        Base::init(str);
    }

    Version(const Base& other): Base(other) {
    }
};

#endif // #ifndef Version_h

/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

 #ifndef SHARE_RUNTIME_STACKORDER_HPP
 #define SHARE_RUNTIME_STACKORDER_HPP

 #include "memory/allStatic.hpp"
 #include "utilities/checkedCast.hpp"
 #include "utilities/globalDefinitions.hpp"
 #include "utilities/macros.hpp"

class StackOrder : public AllStatic {
private:
    // Defines the direction of stack growth, true on down-growing, false on up-growing.
    // Now defines the direction globally, can be platform-specific.
    static inline bool older_frame_address_is_greater() {
        return true;
    };

public:
    static inline intptr_t* towards_older(intptr_t* p, int words) {
        assert(p != nullptr && words >= 0, "");
        return older_frame_address_is_greater() ? p + words : p - words;
    }

    static inline intptr_t* towards_younger(intptr_t* p, int words) {
        assert(p != nullptr && words >= 0, "");
        return older_frame_address_is_greater() ? p - words : p + words;
    }

    static inline bool is_older(const intptr_t* a, const intptr_t* b) {
        assert(a != nullptr, "");
        assert(b != nullptr, "");
        const uintptr_t ua = reinterpret_cast<uintptr_t>(a);
        const uintptr_t ub = reinterpret_cast<uintptr_t>(b);
        return older_frame_address_is_greater() ? ua > ub : ua < ub;
    }

    static inline bool is_older_or_equal(const intptr_t* a, const intptr_t* b) {
        return a == b || is_older(a, b);
    }

    static inline bool is_younger(const intptr_t* a, const intptr_t* b) {
        return is_older(b, a);
    }

    static inline bool is_younger_or_equal(const intptr_t* a, const intptr_t* b) {
        return a == b || is_younger(a, b);
    }

    static inline int words_between(const intptr_t* older, const intptr_t* younger) {
        assert(younger != nullptr && older != nullptr, "");
        assert(is_older_or_equal(older, younger), "");
        const uintptr_t uolder = reinterpret_cast<uintptr_t>(older);
        const uintptr_t uyonger = reinterpret_cast<uintptr_t>(younger);
        const uintptr_t bytes = older_frame_address_is_greater() ? (uolder - uyonger) : (uyonger - uolder);
        assert(bytes % sizeof(intptr_t) == 0, "");
        return checked_cast<int>(bytes / sizeof(intptr_t));
    }

    static inline bool contains_closed(const intptr_t* p, const intptr_t* older, const intptr_t* younger) {
        assert(p != nullptr && younger != nullptr && older != nullptr, "");
        assert(is_older_or_equal(older, younger), "");
        return is_younger_or_equal(p, older) && is_older_or_equal(p, younger);
    }
};


#endif // SHARE_RUNTIME_STACKORDER_HPP
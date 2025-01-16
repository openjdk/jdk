/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/symbolTable.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "unittest.hpp"

// Tests that strings are interned and returns the same string when interning from different string types

// Simple ASCII string "Java(R)!!"
static const char static_ascii_utf8_str[] = {0x4A, 0x61, 0x76, 0x61, 0x28, 0x52, 0x29, 0x21, 0x21};
static const size_t ASCII_LENGTH = 9;

// Complex string "Jāvá®!☺☻", has character lengths 13122133 = 16
static const unsigned char static_utf8_str[] = {0x4A, 0x61, 0xCC, 0x84, 0x76, 0xC3, 0xA1, 0xC2, 0xAE, 0x21, 0xE2, 0x98, 0xBA, 0xE2, 0x98, 0xBB};
static const size_t COMPLEX_LENGTH = 16;

void test_intern(const char* utf8_str, size_t utf8_length) {
    JavaThread* THREAD = JavaThread::current();
    ThreadInVMfromNative ThreadInVMfromNative(THREAD);
    HandleMark hm(THREAD);

    oop interned_string_from_utf8 = StringTable::intern(utf8_str, THREAD);

    EXPECT_TRUE(java_lang_String::equals(interned_string_from_utf8, utf8_str, utf8_length));
    EXPECT_EQ(java_lang_String::hash_code(utf8_str, utf8_length),java_lang_String::hash_code(interned_string_from_utf8));

    Symbol* symbol_from_utf8 = SymbolTable::new_symbol(utf8_str, static_cast<int>(utf8_length));
    oop interned_string_from_symbol = StringTable::intern(symbol_from_utf8, THREAD);

    EXPECT_EQ(interned_string_from_utf8, interned_string_from_symbol);

    oop interned_string_from_oop1 = StringTable::intern(interned_string_from_utf8, THREAD);

    EXPECT_EQ(interned_string_from_utf8, interned_string_from_oop1);

}

TEST_VM(StringIntern, intern_ascii) {
    const char utf8_str[ASCII_LENGTH + 1] = { };
    memcpy((unsigned char*)utf8_str, static_ascii_utf8_str, ASCII_LENGTH);
    test_intern(utf8_str, ASCII_LENGTH);
}

TEST_VM(StringIntern, intern_varlen) {
    const char utf8_str[COMPLEX_LENGTH + 1] = { };
    memcpy((unsigned char*)utf8_str, static_utf8_str, COMPLEX_LENGTH);
    test_intern(utf8_str, COMPLEX_LENGTH);
}

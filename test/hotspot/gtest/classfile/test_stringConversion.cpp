/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "runtime/interfaceSupport.inline.hpp"
#include "unittest.hpp"

// Tests that string functions (hash code/equals) stay consistant when comparing equal strings and converting between strings types

// Simple ASCII string "Java(R)!!"
// Same length in both UTF8 and Unicode
static const char static_ascii_utf8_str[] = {0x4A, 0x61, 0x76, 0x61, 0x28, 0x52, 0x29, 0x21, 0x21};
static const jchar static_ascii_unicode_str[] = {0x004A, 0x0061, 0x0076, 0x0061, 0x0028, 0x0052, 0x0029, 0x0021, 0x0021};

// Complex string "Jāvá®!☺☻", UTF8 has character lengths 13122133 = 16
static const unsigned char static_utf8_str[] = {0x4A, 0x61, 0xCC, 0x84, 0x76, 0xC3, 0xA1, 0xC2, 0xAE, 0x21, 0xE2, 0x98, 0xBA, 0xE2, 0x98, 0xBB};
static const jchar static_unicode_str[] = { 0x004A, 0x0061, 0x0304, 0x0076, 0x00E1, 0x00AE, 0x0021, 0x263A, 0x263B};

static const int ASCII_LENGTH = 9;
static const size_t UTF8_LENGTH = 16;
static const int UNICODE_LENGTH = 9;

void compare_utf8_utf8(const char* utf8_str1, const char* utf8_str2, size_t utf8_len) {
    EXPECT_EQ(java_lang_String::hash_code(utf8_str1, utf8_len), java_lang_String::hash_code(utf8_str2, utf8_len));
    EXPECT_STREQ(utf8_str1, utf8_str2);
}

void compare_utf8_unicode(const char* utf8_str, const jchar* unicode_str, size_t utf8_len, int unicode_len) {
    EXPECT_EQ(java_lang_String::hash_code(utf8_str, utf8_len), java_lang_String::hash_code(unicode_str, unicode_len));
}

void compare_utf8_oop(const char* utf8_str, Handle oop_str, size_t utf8_len, int unicode_len) {
    EXPECT_EQ(java_lang_String::hash_code(utf8_str, utf8_len), java_lang_String::hash_code(oop_str()));
    EXPECT_TRUE(java_lang_String::equals(oop_str(), utf8_str, utf8_len));
}

void compare_unicode_unicode(const jchar* unicode_str1, const jchar* unicode_str2, int unicode_len) {
    EXPECT_EQ(java_lang_String::hash_code(unicode_str1, unicode_len), java_lang_String::hash_code(unicode_str2, unicode_len));
    for (int i = 0; i < unicode_len; i++) {
        EXPECT_EQ(unicode_str1[i], unicode_str2[i]);
    }
}

void compare_unicode_oop(const jchar* unicode_str, Handle oop_str, int unicode_len) {
    EXPECT_EQ(java_lang_String::hash_code(unicode_str, unicode_len), java_lang_String::hash_code(oop_str()));
    EXPECT_TRUE(java_lang_String::equals(oop_str(), unicode_str, unicode_len));
}

void compare_oop_oop(Handle oop_str1, Handle oop_str2) {
    EXPECT_EQ(java_lang_String::hash_code(oop_str1()), java_lang_String::hash_code(oop_str2()));
    EXPECT_TRUE(java_lang_String::equals(oop_str1(), oop_str2()));
}

void test_utf8_convert(const char* utf8_str, size_t utf8_len, int unicode_len) {
    EXPECT_TRUE(UTF8::is_legal_utf8((unsigned char*)utf8_str, strlen(utf8_str), false));

    JavaThread* THREAD = JavaThread::current();
    ThreadInVMfromNative ThreadInVMfromNative(THREAD);
    ResourceMark rm(THREAD);
    HandleMark hm(THREAD);

    jchar* unicode_str_from_utf8 = NEW_RESOURCE_ARRAY(jchar, unicode_len);
    UTF8::convert_to_unicode(utf8_str, unicode_str_from_utf8, unicode_len);
    Handle oop_str_from_utf8 = java_lang_String::create_from_str(utf8_str, THREAD);

    compare_utf8_unicode(utf8_str, unicode_str_from_utf8, utf8_len, unicode_len);
    compare_utf8_oop(utf8_str, oop_str_from_utf8, utf8_len, unicode_len);

    size_t length = unicode_len;
    const char* utf8_str_from_unicode = UNICODE::as_utf8(unicode_str_from_utf8, length);
    const char* utf8_str_from_oop = java_lang_String::as_utf8_string(oop_str_from_utf8());

    EXPECT_TRUE(UTF8::is_legal_utf8((unsigned char*)utf8_str_from_unicode, strlen(utf8_str_from_unicode), false));
    EXPECT_TRUE(UTF8::is_legal_utf8((unsigned char*)utf8_str_from_oop, strlen(utf8_str_from_oop), false));

    compare_utf8_utf8(utf8_str, utf8_str_from_unicode, utf8_len);
    compare_utf8_utf8(utf8_str, utf8_str_from_oop, utf8_len);
}

void test_unicode_convert(const jchar* unicode_str, size_t utf8_len, int unicode_len) {
    JavaThread* THREAD = JavaThread::current();
    ThreadInVMfromNative ThreadInVMfromNative(THREAD);
    ResourceMark rm(THREAD);
    HandleMark hm(THREAD);

    size_t length = unicode_len;
    const char* utf8_str_from_unicode = UNICODE::as_utf8(unicode_str, length);
    Handle oop_str_from_unicode = java_lang_String::create_from_unicode(unicode_str, unicode_len, THREAD);

    EXPECT_TRUE(UTF8::is_legal_utf8((unsigned char*)utf8_str_from_unicode, strlen(utf8_str_from_unicode), false));

    compare_utf8_unicode(utf8_str_from_unicode, unicode_str, utf8_len, unicode_len);
    compare_unicode_oop(unicode_str, oop_str_from_unicode, unicode_len);

    int _;
    jchar* unicode_str_from_utf8 = NEW_RESOURCE_ARRAY(jchar, unicode_len);
    UTF8::convert_to_unicode(utf8_str_from_unicode, unicode_str_from_utf8, unicode_len);
    const jchar* unicode_str_from_oop = java_lang_String::as_unicode_string(oop_str_from_unicode(), _, THREAD);

    compare_unicode_unicode(unicode_str, unicode_str_from_utf8, unicode_len);
    compare_unicode_unicode(unicode_str, unicode_str_from_oop, unicode_len);
}

void test_utf8_unicode_cross(const char* utf8_str, const jchar* unicode_str, size_t utf8_len, int unicode_len) {
    compare_utf8_unicode(utf8_str, unicode_str, utf8_len, unicode_len);

    JavaThread* THREAD = JavaThread::current();
    ThreadInVMfromNative ThreadInVMfromNative(THREAD);
    ResourceMark rm(THREAD);
    HandleMark hm(THREAD);

    size_t length = unicode_len;
    const char* utf8_str_from_unicode = UNICODE::as_utf8(unicode_str, length);

    jchar* unicode_str_from_utf8 = NEW_RESOURCE_ARRAY(jchar, unicode_len);
    UTF8::convert_to_unicode(utf8_str, unicode_str_from_utf8, unicode_len);

    Handle oop_str_from_unicode = java_lang_String::create_from_unicode(unicode_str, unicode_len, THREAD);
    Handle oop_str_from_utf8 = java_lang_String::create_from_str(utf8_str, THREAD);

    compare_utf8_utf8(utf8_str, utf8_str_from_unicode, utf8_len);
    compare_utf8_oop(utf8_str, oop_str_from_unicode, utf8_len, unicode_len);

    compare_unicode_unicode(unicode_str, unicode_str_from_utf8, unicode_len);
    compare_unicode_oop(unicode_str, oop_str_from_utf8, unicode_len);

    compare_utf8_oop(utf8_str_from_unicode, oop_str_from_utf8, utf8_len, unicode_len);
    compare_unicode_oop(unicode_str_from_utf8, oop_str_from_unicode, unicode_len);

    compare_utf8_unicode(utf8_str_from_unicode, unicode_str_from_utf8, utf8_len, unicode_len);
    compare_oop_oop(oop_str_from_utf8, oop_str_from_unicode);
}

TEST_VM(StringConversion, fromUTF8_ascii) {
    const char utf8_str[ASCII_LENGTH + 1] = { };
    memcpy((unsigned char*)utf8_str, static_ascii_utf8_str, ASCII_LENGTH);
    test_utf8_convert(utf8_str, ASCII_LENGTH, ASCII_LENGTH);
}

TEST_VM(StringConversion, fromUTF8_varlen) {
    const char utf8_str[UTF8_LENGTH + 1] = { };
    memcpy((unsigned char*)utf8_str, static_utf8_str, UTF8_LENGTH);
    test_utf8_convert(utf8_str, UTF8_LENGTH, UNICODE_LENGTH);
}

TEST_VM(StringConversion, fromUnicode_ascii) {
    jchar unicode_str[ASCII_LENGTH] = { };
    memcpy(unicode_str, static_ascii_unicode_str, ASCII_LENGTH * sizeof(jchar));
    test_unicode_convert(unicode_str, ASCII_LENGTH, ASCII_LENGTH);
}

TEST_VM(StringConversion, fromUnicode_varlen) {
    jchar unicode_str[UNICODE_LENGTH] = { };
    memcpy(unicode_str, static_unicode_str, UNICODE_LENGTH * sizeof(jchar));
    test_unicode_convert(unicode_str, UTF8_LENGTH, UNICODE_LENGTH);
}

TEST_VM(StringConversion, cross_ascii) {
    const char utf8_str[ASCII_LENGTH + 1] = { };
    jchar unicode_str[ASCII_LENGTH] = { };
    memcpy((unsigned char*)utf8_str, static_ascii_utf8_str, ASCII_LENGTH);
    memcpy(unicode_str, static_ascii_unicode_str, ASCII_LENGTH * sizeof(jchar));

    test_utf8_unicode_cross(utf8_str, unicode_str, ASCII_LENGTH, ASCII_LENGTH);
}

TEST_VM(StringConversion, cross_varlen) {
    const char utf8_str[UTF8_LENGTH + 1] = { };
    jchar unicode_str[UNICODE_LENGTH] = { };
    memcpy((unsigned char*)utf8_str, static_utf8_str, UTF8_LENGTH);
    memcpy(unicode_str, static_unicode_str, UNICODE_LENGTH * sizeof(jchar));

    test_utf8_unicode_cross(utf8_str, unicode_str, UTF8_LENGTH, UNICODE_LENGTH);
}

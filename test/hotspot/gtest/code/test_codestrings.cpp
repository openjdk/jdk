/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef PRODUCT
#ifndef ZERO

#include "asm/macroAssembler.inline.hpp"
#include "compiler/disassembler.hpp"
#include "memory/resourceArea.hpp"
#include "unittest.hpp"

#include <regex>

static const char* replace_addr_expr(const char* str)
{
    // Remove any address expression "0x0123456789abcdef" found in order to
    // aid string comparison. Also remove any trailing printout from a padded
    // buffer.

    std::basic_string<char> tmp = std::regex_replace(str, std::regex("0x[0-9a-fA-F]+"), "<addr>");
    std::basic_string<char> red = std::regex_replace(tmp, std::regex("\\s+<addr>:\\s+\\.inst\\t<addr> ; undefined"), "");

    return os::strdup(red.c_str());
}

static const char* delete_header_line(const char* str)
{
    // Remove (second) header line in output, e.g.:
    // Decoding CodeBlob, name: CodeStringTest, at [<addr>, <addr>] 8 bytes\n

    std::basic_string<char> red = std::regex_replace(str, std::regex("Decoding.+bytes\\n"), "");

    return os::strdup(red.c_str());
}

static void asm_remarks_check(const AsmRemarks &rem1,
                              const AsmRemarks &rem2)
{
    ASSERT_EQ(rem1.ref(), rem2.ref()) << "Should share the same collection.";
}

static void dbg_strings_check(const DbgStrings &dbg1,
                              const DbgStrings &dbg2)
{
    ASSERT_EQ(dbg1.ref(), dbg2.ref()) << "Should share the same collection.";
}

static void disasm_string_check(CodeBuffer* cbuf, CodeBlob* blob)
{
    if (Disassembler::is_abstract())
    {
        return;   // No disassembler available (no comments will be used).
    }
    stringStream out1, out2;

    Disassembler::decode(cbuf->insts_begin(), cbuf->insts_end(), &out1, &cbuf->asm_remarks());
    Disassembler::decode(blob->code_begin(), blob->code_end(), &out2, &blob->asm_remarks());

    EXPECT_STREQ(replace_addr_expr(out1.as_string()),
                 replace_addr_expr(out2.as_string()))
        << "1. Output should be identical.";

    stringStream out3;

    Disassembler::decode(blob, &out3);

    EXPECT_STREQ(replace_addr_expr(out2.as_string()),
                 replace_addr_expr(delete_header_line(out3.as_string())))
        << "2. Output should be identical.";
}

static void copy_and_compare(CodeBuffer* cbuf)
{
    bool remarks_empty = cbuf->asm_remarks().is_empty();
    bool strings_empty = cbuf->dbg_strings().is_empty();

    BufferBlob* blob = BufferBlob::create("CodeBuffer Copy&Compare", cbuf);

    // 1. Check Assembly Remarks are shared by buffer and blob.
    asm_remarks_check(cbuf->asm_remarks(), blob->asm_remarks());

    // 2. Check Debug Strings are shared by buffer and blob.
    dbg_strings_check(cbuf->dbg_strings(), blob->dbg_strings());

    // 3. Check that the disassembly output matches.
    disasm_string_check(cbuf, blob);

    BufferBlob::free(blob);

    ASSERT_EQ(remarks_empty, cbuf->asm_remarks().is_empty())
        << "Expecting property to be unchanged.";
    ASSERT_EQ(strings_empty, cbuf->dbg_strings().is_empty())
        << "Expecting property to be unchanged.";
}

static void code_buffer_test()
{
    constexpr int BUF_SZ = 256;

    ResourceMark rm;
    CodeBuffer cbuf("CodeStringTest", BUF_SZ, BUF_SZ);
    MacroAssembler as(&cbuf);

    ASSERT_TRUE(cbuf.asm_remarks().is_empty());
    ASSERT_TRUE(cbuf.dbg_strings().is_empty());

    ASSERT_TRUE(cbuf.blob()->asm_remarks().is_empty());
    ASSERT_TRUE(cbuf.blob()->dbg_strings().is_empty());

    int re, sz, n;

    re = cbuf.insts_remaining();

    // 1. Generate a first entry.
    as.block_comment("First block comment.");
    as.nop();

    sz = re - cbuf.insts_remaining();

    ASSERT_TRUE(sz > 0);

    ASSERT_FALSE(cbuf.asm_remarks().is_empty());
    ASSERT_TRUE(cbuf.dbg_strings().is_empty());

    ASSERT_TRUE(cbuf.blob()->asm_remarks().is_empty());
    ASSERT_TRUE(cbuf.blob()->dbg_strings().is_empty());

    copy_and_compare(&cbuf);

    n = re/sz;
    ASSERT_TRUE(n > 0);

    // 2. Generate additional entries without causing the buffer to expand.
    for (unsigned i = 0; i < unsigned(n)/2; i++)
    {
        ASSERT_FALSE(cbuf.insts()->maybe_expand_to_ensure_remaining(sz));
        ASSERT_TRUE(cbuf.insts_remaining()/sz >= n/2);

        stringStream strm;
        strm.print("Comment No. %d", i);
        as.block_comment(strm.as_string());
        as.nop();
    }
    ASSERT_FALSE(cbuf.asm_remarks().is_empty());

    copy_and_compare(&cbuf);

    re = cbuf.insts_remaining();

    // 3. Generate a single code with a debug string.
    as.unimplemented("First debug string.");

    ASSERT_FALSE(cbuf.asm_remarks().is_empty());
    ASSERT_FALSE(cbuf.dbg_strings().is_empty());

    sz = re - cbuf.insts_remaining();
    n = (re - sz)/sz;
    ASSERT_TRUE(n > 0);

    // 4. Generate additional code with debug strings.
    for (unsigned i = 0; i < unsigned(n); i++)
    {
        ASSERT_TRUE(cbuf.insts_remaining() >= sz);

        stringStream strm;
        strm.print("Fixed address string No. %d", i);
        as.unimplemented(strm.as_string());
    }
    ASSERT_TRUE(cbuf.insts_remaining() >= 0);

    ASSERT_FALSE(cbuf.asm_remarks().is_empty());
    ASSERT_FALSE(cbuf.dbg_strings().is_empty());

    ASSERT_TRUE(cbuf.blob()->asm_remarks().is_empty());
    ASSERT_TRUE(cbuf.blob()->dbg_strings().is_empty());

    copy_and_compare(&cbuf);
}

static void buffer_blob_test()
{
    constexpr int BUF_SZ = 256;

    ResourceMark rm;
    BufferBlob* blob = BufferBlob::create("BufferBlob Test", BUF_SZ);
    CodeBuffer cbuf(blob);
    MacroAssembler as(&cbuf);

    ASSERT_FALSE(cbuf.insts()->has_locs());

    // The x86-64 version of 'stop' will use relocation info. that will result
    // in tainting the location start and limit if no location info. buffer is
    // present.
    static uint8_t s_loc_buf[BUF_SZ];  // Raw memory buffer used for relocInfo.
    cbuf.insts()->initialize_shared_locs((relocInfo*)&s_loc_buf[0], BUF_SZ);

    int re = cbuf.insts_remaining();

    as.block_comment("First block comment.");
    as.nop();
    as.unimplemented("First debug string.");

    int sz = re - cbuf.insts_remaining();

    ASSERT_TRUE(sz > 0);
    constexpr int LIM_GEN = 51; // Limit number of entries generated.

    for (unsigned i = 0; i < LIM_GEN; i++)
    {
        if (cbuf.insts_remaining() < sz) break;

        stringStream strm1;
        strm1.print("Comment No. %d", i);
        as.block_comment(strm1.as_string());
        as.nop();

        stringStream strm2;
        strm2.print("Fixed address string No. %d", i);
        as.unimplemented(strm2.as_string());
    }
    ASSERT_TRUE(cbuf.insts_remaining() >= 0);

    ASSERT_FALSE(cbuf.asm_remarks().is_empty());
    ASSERT_FALSE(cbuf.dbg_strings().is_empty());

    copy_and_compare(&cbuf);

    ASSERT_TRUE(blob->asm_remarks().is_empty());
    ASSERT_TRUE(blob->dbg_strings().is_empty());

    BufferBlob::free(blob);
}

TEST_VM(codestrings, validate)
{
    code_buffer_test();
    buffer_blob_test();
}

#endif // not ZERO
#endif // not PRODUCT

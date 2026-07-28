/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#if defined(AARCH64) && !defined(ZERO)

#include "asm/macroAssembler.hpp"
#include "asm/macroAssembler.inline.hpp"
#include "code/relocInfo.hpp"
#include "memory/resourceArea.hpp"
#include "nativeInst_aarch64.hpp"
#include "oops/method.hpp"
#include "runtime/sharedRuntime.hpp"
#include "unittest.hpp"

#define __ _masm.

// Unit tests for MacroAssembler::ensure_static_call_dispatch_adapter().
//
// The adapter is emitted once per nmethod into the stubs section:
//
//   ldr   rscratch1, [rmethod,   #Method::adapter_offset()]
//   ldr   rscratch1, [rscratch1, #AdapterHandlerEntry::c2i_entry_offset()]
//   br    rscratch1

// The first call of instance() must happen inside a TEST_VM body, so the VM and
// CodeCache are initialized.
class StaticCallDispatchAdapter {
 private:
  uint32_t _code[3];

  StaticCallDispatchAdapter() {
    BufferBlob* b = BufferBlob::create("adapterExpected", 64);
    guarantee(b != nullptr, "no CodeCache space for expected-adapter blob");
    CodeBuffer code(b);
    code.set_blob(b); // the buffer owns (and will free) its blob
    MacroAssembler _masm(&code);
    __ ldr(rscratch1, Address(rmethod, Method::adapter_offset()));
    __ ldr(rscratch1, Address(rscratch1, AdapterHandlerEntry::c2i_entry_offset()));
    __ br(rscratch1);
    guarantee(code.insts()->size() == (CodeBuffer::csize_t)sizeof(_code),
              "expected adapter is not %d bytes", (int)sizeof(_code));
    memcpy(_code, code.insts()->start(), sizeof(_code));
  }

 public:
  static const StaticCallDispatchAdapter& instance() {
    static StaticCallDispatchAdapter adapter;
    return adapter;
  }

  static constexpr int code_size() {
    return sizeof(_code);
  }

  static constexpr int num_instructions() {
    return sizeof(_code) / sizeof(_code[0]);
  }

  const uint32_t* code() const { return _code; }
};

static void check_code(const uint32_t* code, const char* when) {
  const uint32_t* expected = StaticCallDispatchAdapter::instance().code();
  for (int i = 0; i < StaticCallDispatchAdapter::num_instructions(); i++) {
    EXPECT_EQ(code[i], expected[i]) << when << ": adapter word " << i << " mismatch";
  }
}

TEST_VM(StaticCallDispatchAdapter, emit_adapter) {
  ResourceMark rm;
  BufferBlob* b = BufferBlob::create("adapterTest", 256);
  ASSERT_TRUE(b != nullptr);
  CodeBuffer code(b);
  code.set_blob(b); // the buffer owns (and will free) its blob
  code.initialize_stubs_size(64);
  MacroAssembler _masm(&code);

  // Nothing emitted yet.
  EXPECT_EQ(code.static_call_dispatch_adapter_offset(), -1);
  EXPECT_EQ(code.stubs()->size(), (CodeBuffer::csize_t)0);

  ASSERT_TRUE(__ ensure_static_call_dispatch_adapter());
  EXPECT_EQ(_masm.code_section(), code.insts());

  address adapter = __ static_call_dispatch_adapter();
  CodeBuffer::csize_t size_after_first = code.stubs()->size();
  int off_after_first = code.static_call_dispatch_adapter_offset();
  EXPECT_EQ(size_after_first, (CodeBuffer::csize_t)StaticCallDispatchAdapter::code_size());
  EXPECT_EQ(off_after_first, 0);
  EXPECT_EQ(adapter, code.stubs()->start());
  check_code((const uint32_t*)adapter, "emit_adapter");

  // A second call must be a no-op.
  ASSERT_TRUE(__ ensure_static_call_dispatch_adapter());
  EXPECT_EQ(__ static_call_dispatch_adapter(), adapter);
  EXPECT_EQ(code.stubs()->size(), size_after_first);
  EXPECT_EQ(code.static_call_dispatch_adapter_offset(), off_after_first);
}

TEST_VM(StaticCallDispatchAdapter, adapter_offset_after_expansion) {
  ResourceMark rm;
  BufferBlob* b = BufferBlob::create("adapterTest", 256);
  ASSERT_TRUE(b != nullptr);
  CodeBuffer code(b);
  code.set_blob(b); // the buffer owns (and will free) its blob
  code.initialize_stubs_size(64);
  MacroAssembler _masm(&code);

  ASSERT_TRUE(__ ensure_static_call_dispatch_adapter());
  EXPECT_EQ(code.static_call_dispatch_adapter_offset(), 0);
  check_code((const uint32_t*)code.stubs()->start(), "pre-expansion");

  address stubs_start_before = code.stubs()->start();
  code.insts()->maybe_expand_to_ensure_remaining(512);
  address stubs_start_after = code.stubs()->start();
  ASSERT_NE(stubs_start_after, stubs_start_before) << "buffer was not expanded";
  EXPECT_EQ(code.static_call_dispatch_adapter_offset(), 0);
  EXPECT_EQ(__ static_call_dispatch_adapter(), code.stubs()->start());
  check_code((const uint32_t*)code.stubs()->start(), "post-expansion");

  // A second call must be a no-op.
  ASSERT_TRUE(__ ensure_static_call_dispatch_adapter());
  EXPECT_EQ(__ static_call_dispatch_adapter(), code.stubs()->start());
  EXPECT_EQ(code.stubs()->size(), (CodeBuffer::csize_t)StaticCallDispatchAdapter::code_size());
}

#endif // AARCH64 && !ZERO

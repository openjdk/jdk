/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "precompiled.hpp"
#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/guardedMemory.hpp"
#include "runtime/os.hpp"

void* GuardedMemory::wrap_copy(const void* ptr, const size_t len, const void* tag) {
  size_t total_sz = GuardedMemory::get_total_size(len);
  void* outerp = os::malloc(total_sz, mtInternal);
  if (outerp != NULL) {
    GuardedMemory guarded(outerp, len, tag);
    void* innerp = guarded.get_user_ptr();
    memcpy(innerp, ptr, len);
    return innerp;
  }
  return NULL; // OOM
}

bool GuardedMemory::free_copy(void* p) {
  if (p == NULL) {
    return true;
  }
  GuardedMemory guarded((u_char*)p);
  bool verify_ok = guarded.verify_guards();

  /* always attempt to free, pass problem on to any nested memchecker */
  os::free(guarded.release_for_freeing());

  return verify_ok;
}

void GuardedMemory::print_on(outputStream* st) const {
  if (_base_addr == NULL) {
    st->print_cr("GuardedMemory(" PTR_FORMAT ") not associated to any memory", p2i(this));
    return;
  }
  st->print_cr("GuardedMemory(" PTR_FORMAT ") base_addr=" PTR_FORMAT
      " tag=" PTR_FORMAT " user_size=" SIZE_FORMAT " user_data=" PTR_FORMAT,
      p2i(this), p2i(_base_addr), p2i(get_tag()), get_user_size(), p2i(get_user_ptr()));

  Guard* guard = get_head_guard();
  st->print_cr("  Header guard @" PTR_FORMAT " is %s", p2i(guard), (guard->verify() ? "OK" : "BROKEN"));
  guard = get_tail_guard();
  st->print_cr("  Trailer guard @" PTR_FORMAT " is %s", p2i(guard), (guard->verify() ? "OK" : "BROKEN"));

  u_char udata = *get_user_ptr();
  switch (udata) {
  case uninitBlockPad:
    st->print_cr("  User data appears unused");
    break;
  case freeBlockPad:
    st->print_cr("  User data appears to have been freed");
    break;
  default:
    st->print_cr("  User data appears to be in use");
    break;
  }
}

// test code...

#ifndef PRODUCT

static void guarded_memory_test_check(void* p, size_t sz, void* tag) {
  assert(p != NULL, "NULL pointer given to check");
  u_char* c = (u_char*) p;
  GuardedMemory guarded(c);
  assert(guarded.get_tag() == tag, "Tag is not the same as supplied");
  assert(guarded.get_user_ptr() == c, "User pointer is not the same as supplied");
  assert(guarded.get_user_size() == sz, "User size is not the same as supplied");
  assert(guarded.verify_guards(), "Guard broken");
}

void GuardedMemory::test_guarded_memory() {
  // Test the basic characteristics...
  size_t total_sz = GuardedMemory::get_total_size(1);
  assert(total_sz > 1 && total_sz >= (sizeof(GuardHeader) + 1 + sizeof(Guard)), "Unexpected size");
  u_char* basep = (u_char*) os::malloc(total_sz, mtInternal);

  GuardedMemory guarded(basep, 1, (void*)0xf000f000);

  assert(*basep == badResourceValue, "Expected guard in the form of badResourceValue");
  u_char* userp = guarded.get_user_ptr();
  assert(*userp == uninitBlockPad, "Expected uninitialized data in the form of uninitBlockPad");
  guarded_memory_test_check(userp, 1, (void*)0xf000f000);

  void* freep = guarded.release_for_freeing();
  assert((u_char*)freep == basep, "Expected the same pointer guard was ");
  assert(*userp == freeBlockPad, "Expected user data to be free block padded");
  assert(!guarded.verify_guards(), "Expected failed");
  os::free(freep);

  // Test a number of odd sizes...
  size_t sz = 0;
  do {
    void* p = os::malloc(GuardedMemory::get_total_size(sz), mtInternal);
    void* up = guarded.wrap_with_guards(p, sz, (void*)1);
    memset(up, 0, sz);
    guarded_memory_test_check(up, sz, (void*)1);
    os::free(guarded.release_for_freeing());
    sz = (sz << 4) + 1;
  } while (sz < (256 * 1024));

  // Test buffer overrun into head...
  basep = (u_char*) os::malloc(GuardedMemory::get_total_size(1), mtInternal);
  guarded.wrap_with_guards(basep, 1);
  *basep = 0;
  assert(!guarded.verify_guards(), "Expected failure");
  os::free(basep);

  // Test buffer overrun into tail with a number of odd sizes...
  sz = 1;
  do {
    void* p = os::malloc(GuardedMemory::get_total_size(sz), mtInternal);
    void* up = guarded.wrap_with_guards(p, sz, (void*)1);
    memset(up, 0, sz + 1); // Buffer-overwrite (within guard)
    assert(!guarded.verify_guards(), "Guard was not broken as expected");
    os::free(guarded.release_for_freeing());
    sz = (sz << 4) + 1;
  } while (sz < (256 * 1024));

  // Test wrap_copy/wrap_free...
  assert(GuardedMemory::free_copy(NULL), "Expected free NULL to be OK");

  const char* str = "Check my bounds out";
  size_t str_sz = strlen(str) + 1;
  char* str_copy = (char*) GuardedMemory::wrap_copy(str, str_sz);
  guarded_memory_test_check(str_copy, str_sz, NULL);
  assert(strcmp(str, str_copy) == 0, "Not identical copy");
  assert(GuardedMemory::free_copy(str_copy), "Free copy failed to verify");

  void* no_data = NULL;
  void* no_data_copy = GuardedMemory::wrap_copy(no_data, 0);
  assert(GuardedMemory::free_copy(no_data_copy), "Expected valid guards even for no data copy");
}

#endif // !PRODUCT


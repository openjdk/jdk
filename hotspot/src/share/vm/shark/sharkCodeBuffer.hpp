/*
 * Copyright (c) 1999, 2007, Oracle and/or its affiliates. All rights reserved.
 * Copyright 2009 Red Hat, Inc.
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

class SharkCodeBuffer : public StackObj {
 public:
  SharkCodeBuffer(MacroAssembler* masm)
    : _masm(masm), _base_pc(NULL) {}

 private:
  MacroAssembler* _masm;
  llvm::Value*    _base_pc;

 private:
  MacroAssembler* masm() const {
    return _masm;
  }

 public:
  llvm::Value* base_pc() const {
    return _base_pc;
  }
  void set_base_pc(llvm::Value* base_pc) {
    assert(_base_pc == NULL, "only do this once");
    _base_pc = base_pc;
  }

  // Allocate some space in the buffer and return its address.
  // This buffer will have been relocated by the time the method
  // is installed, so you can't inline the result in code.
 public:
  void* malloc(size_t size) const {
    masm()->align(BytesPerWord);
    void *result = masm()->pc();
    masm()->advance(size);
    return result;
  }

  // Create a unique offset in the buffer.
 public:
  int create_unique_offset() const {
    int offset = masm()->offset();
    masm()->advance(1);
    return offset;
  }

  // Inline an oop into the buffer and return its offset.
 public:
  int inline_oop(jobject object) const {
    masm()->align(BytesPerWord);
    int offset = masm()->offset();
    masm()->store_oop(object);
    return offset;
  }

  // Inline a block of non-oop data into the buffer and return its offset.
 public:
  int inline_data(void *src, size_t size) const {
    masm()->align(BytesPerWord);
    int offset = masm()->offset();
    void *dst = masm()->pc();
    masm()->advance(size);
    memcpy(dst, src, size);
    return offset;
  }
};

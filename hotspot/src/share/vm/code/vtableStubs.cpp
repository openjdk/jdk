/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "code/vtableStubs.hpp"
#include "compiler/compileBroker.hpp"
#include "compiler/disassembler.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klassVtable.hpp"
#include "oops/oop.inline.hpp"
#include "prims/forte.hpp"
#include "prims/jvmtiExport.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/sharedRuntime.hpp"
#ifdef COMPILER2
#include "opto/matcher.hpp"
#endif

PRAGMA_FORMAT_MUTE_WARNINGS_FOR_GCC

// -----------------------------------------------------------------------------------------
// Implementation of VtableStub

address VtableStub::_chunk             = NULL;
address VtableStub::_chunk_end         = NULL;
VMReg   VtableStub::_receiver_location = VMRegImpl::Bad();


void* VtableStub::operator new(size_t size, int code_size) throw() {
  assert(size == sizeof(VtableStub), "mismatched size");
  // compute real VtableStub size (rounded to nearest word)
  const int real_size = round_to(code_size + sizeof(VtableStub), wordSize);
  // malloc them in chunks to minimize header overhead
  const int chunk_factor = 32;
  if (_chunk == NULL || _chunk + real_size > _chunk_end) {
    const int bytes = chunk_factor * real_size + pd_code_alignment();

   // There is a dependency on the name of the blob in src/share/vm/prims/jvmtiCodeBlobEvents.cpp
   // If changing the name, update the other file accordingly.
    BufferBlob* blob = BufferBlob::create("vtable chunks", bytes);
    if (blob == NULL) {
      return NULL;
    }
    _chunk = blob->content_begin();
    _chunk_end = _chunk + bytes;
    Forte::register_stub("vtable stub", _chunk, _chunk_end);
    align_chunk();
  }
  assert(_chunk + real_size <= _chunk_end, "bad allocation");
  void* res = _chunk;
  _chunk += real_size;
  align_chunk();
 return res;
}


void VtableStub::print_on(outputStream* st) const {
  st->print("vtable stub (index = %d, receiver_location = %d, code = [" INTPTR_FORMAT ", " INTPTR_FORMAT "[)",
             index(), receiver_location(), code_begin(), code_end());
}


// -----------------------------------------------------------------------------------------
// Implementation of VtableStubs
//
// For each hash value there's a linked list of vtable stubs (with that
// hash value). Each list is anchored in a little hash _table, indexed
// by that hash value.

VtableStub* VtableStubs::_table[VtableStubs::N];
int VtableStubs::_number_of_vtable_stubs = 0;


void VtableStubs::initialize() {
  VtableStub::_receiver_location = SharedRuntime::name_for_receiver();
  {
    MutexLocker ml(VtableStubs_lock);
    assert(_number_of_vtable_stubs == 0, "potential performance bug: VtableStubs initialized more than once");
    assert(is_power_of_2(N), "N must be a power of 2");
    for (int i = 0; i < N; i++) {
      _table[i] = NULL;
    }
  }
}


address VtableStubs::find_stub(bool is_vtable_stub, int vtable_index) {
  assert(vtable_index >= 0, "must be positive");

  VtableStub* s = ShareVtableStubs ? lookup(is_vtable_stub, vtable_index) : NULL;
  if (s == NULL) {
    if (is_vtable_stub) {
      s = create_vtable_stub(vtable_index);
    } else {
      s = create_itable_stub(vtable_index);
    }

    // Creation of vtable or itable can fail if there is not enough free space in the code cache.
    if (s == NULL) {
      return NULL;
    }

    enter(is_vtable_stub, vtable_index, s);
    if (PrintAdapterHandlers) {
      tty->print_cr("Decoding VtableStub %s[%d]@%d",
                    is_vtable_stub? "vtbl": "itbl", vtable_index, VtableStub::receiver_location());
      Disassembler::decode(s->code_begin(), s->code_end());
    }
    // Notify JVMTI about this stub. The event will be recorded by the enclosing
    // JvmtiDynamicCodeEventCollector and posted when this thread has released
    // all locks.
    if (JvmtiExport::should_post_dynamic_code_generated()) {
      JvmtiExport::post_dynamic_code_generated_while_holding_locks(is_vtable_stub? "vtable stub": "itable stub",
                                                                   s->code_begin(), s->code_end());
    }
  }
  return s->entry_point();
}


inline uint VtableStubs::hash(bool is_vtable_stub, int vtable_index){
  // Assumption: receiver_location < 4 in most cases.
  int hash = ((vtable_index << 2) ^ VtableStub::receiver_location()->value()) + vtable_index;
  return (is_vtable_stub ? ~hash : hash)  & mask;
}


VtableStub* VtableStubs::lookup(bool is_vtable_stub, int vtable_index) {
  MutexLocker ml(VtableStubs_lock);
  unsigned hash = VtableStubs::hash(is_vtable_stub, vtable_index);
  VtableStub* s = _table[hash];
  while( s && !s->matches(is_vtable_stub, vtable_index)) s = s->next();
  return s;
}


void VtableStubs::enter(bool is_vtable_stub, int vtable_index, VtableStub* s) {
  MutexLocker ml(VtableStubs_lock);
  assert(s->matches(is_vtable_stub, vtable_index), "bad vtable stub");
  unsigned int h = VtableStubs::hash(is_vtable_stub, vtable_index);
  // enter s at the beginning of the corresponding list
  s->set_next(_table[h]);
  _table[h] = s;
  _number_of_vtable_stubs++;
}


bool VtableStubs::is_entry_point(address pc) {
  MutexLocker ml(VtableStubs_lock);
  VtableStub* stub = (VtableStub*)(pc - VtableStub::entry_offset());
  uint hash = VtableStubs::hash(stub->is_vtable_stub(), stub->index());
  VtableStub* s;
  for (s = _table[hash]; s != NULL && s != stub; s = s->next()) {}
  return s == stub;
}


bool VtableStubs::contains(address pc) {
  // simple solution for now - we may want to use
  // a faster way if this function is called often
  return stub_containing(pc) != NULL;
}


VtableStub* VtableStubs::stub_containing(address pc) {
  // Note: No locking needed since any change to the data structure
  //       happens with an atomic store into it (we don't care about
  //       consistency with the _number_of_vtable_stubs counter).
  for (int i = 0; i < N; i++) {
    for (VtableStub* s = _table[i]; s != NULL; s = s->next()) {
      if (s->contains(pc)) return s;
    }
  }
  return NULL;
}

void vtableStubs_init() {
  VtableStubs::initialize();
}

void VtableStubs::vtable_stub_do(void f(VtableStub*)) {
    for (int i = 0; i < N; i++) {
        for (VtableStub* s = _table[i]; s != NULL; s = s->next()) {
            f(s);
        }
    }
}


//-----------------------------------------------------------------------------------------------------
// Non-product code
#ifndef PRODUCT

extern "C" void bad_compiled_vtable_index(JavaThread* thread, oop receiver, int index) {
  ResourceMark rm;
  HandleMark hm;
  Klass* klass = receiver->klass();
  InstanceKlass* ik = InstanceKlass::cast(klass);
  klassVtable* vt = ik->vtable();
  ik->print();
  fatal("bad compiled vtable dispatch: receiver " INTPTR_FORMAT ", "
        "index %d (vtable length %d)",
        (address)receiver, index, vt->length());
}

#endif // PRODUCT

/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/allocation.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/arena.hpp"
#include "memory/metaspace.hpp"
#include "memory/resourceArea.hpp"
#include "nmt/memTracker.hpp"
#include "runtime/os.hpp"
#include "runtime/task.hpp"
#include "utilities/ostream.hpp"

// allocate using malloc; will fail if no memory available
char* AllocateHeap(size_t size,
                   MemTag mem_tag,
                   const NativeCallStack& stack,
                   AllocFailType alloc_failmode /* = AllocFailStrategy::EXIT_OOM*/) {
  char* p = (char*) os::malloc(size, mem_tag, stack);
  if (p == nullptr && alloc_failmode == AllocFailStrategy::EXIT_OOM) {
    vm_exit_out_of_memory(size, OOM_MALLOC_ERROR, "AllocateHeap");
  }
  return p;
}

char* AllocateHeap(size_t size,
                   MemTag mem_tag,
                   AllocFailType alloc_failmode /* = AllocFailStrategy::EXIT_OOM*/) {
  return AllocateHeap(size, mem_tag, CALLER_PC, alloc_failmode);
}

char* ReallocateHeap(char *old,
                     size_t size,
                     MemTag mem_tag,
                     AllocFailType alloc_failmode) {
  char* p = (char*) os::realloc(old, size, mem_tag, CALLER_PC);
  if (p == nullptr && alloc_failmode == AllocFailStrategy::EXIT_OOM) {
    vm_exit_out_of_memory(size, OOM_MALLOC_ERROR, "ReallocateHeap");
  }
  return p;
}

// handles null pointers
void FreeHeap(void* p) {
  os::free(p);
}

void* MetaspaceObj::_shared_metaspace_base = nullptr;
void* MetaspaceObj::_shared_metaspace_top  = nullptr;

void* MetaspaceObj::operator new(size_t size, ClassLoaderData* loader_data,
                                 size_t word_size,
                                 MetaspaceObj::Type type, TRAPS) throw() {
  // Klass has its own operator new
  assert(type != ClassType, "class has its own operator new");
  return Metaspace::allocate(loader_data, word_size, type, /*use_class_space*/ false, THREAD);
}

void* MetaspaceObj::operator new(size_t size, ClassLoaderData* loader_data,
                                 size_t word_size,
                                 MetaspaceObj::Type type) throw() {
  assert(!Thread::current()->is_Java_thread(), "only allowed by non-Java thread");
  assert(type != ClassType, "class has its own operator new");
  return Metaspace::allocate(loader_data, word_size, type, /*use_class_space*/ false);
}

// This is used for allocating training data. We are allocating training data in many cases where a GC cannot be triggered.
void* MetaspaceObj::operator new(size_t size, MemTag flags) {
  void* p = AllocateHeap(size, flags, CALLER_PC);
  memset(p, 0, size);
  return p;
}

bool MetaspaceObj::is_valid(const MetaspaceObj* p) {
  // Weed out obvious bogus values first without traversing metaspace
  if ((size_t)p < os::min_page_size()) {
    return false;
  } else if (!is_aligned((address)p, sizeof(MetaWord))) {
    return false;
  }
  return Metaspace::contains((void*)p);
}

void MetaspaceObj::print_address_on(outputStream* st) const {
  st->print(" {" PTR_FORMAT "}", p2i(this));
}

//
// ArenaObj
//

void* ArenaObj::operator new(size_t size, Arena *arena) throw() {
  return arena->Amalloc(size);
}

//
// AnyObj
//

void* AnyObj::operator new(size_t size, Arena *arena) {
  address res = (address)arena->Amalloc(size);
  DEBUG_ONLY(set_allocation_type(res, ARENA);)
  return res;
}

void* AnyObj::operator new(size_t size, MemTag mem_tag) throw() {
  address res = (address)AllocateHeap(size, mem_tag, CALLER_PC);
  DEBUG_ONLY(set_allocation_type(res, C_HEAP);)
  return res;
}

void* AnyObj::operator new(size_t size, const std::nothrow_t&  nothrow_constant,
    MemTag mem_tag) throw() {
  // should only call this with std::nothrow, use other operator new() otherwise
    address res = (address)AllocateHeap(size, mem_tag, CALLER_PC, AllocFailStrategy::RETURN_NULL);
    DEBUG_ONLY(if (res!= nullptr) set_allocation_type(res, C_HEAP);)
  return res;
}

void AnyObj::operator delete(void* p) {
  if (p == nullptr) {
    return;
  }
  assert(((AnyObj *)p)->allocated_on_C_heap(),
         "delete only allowed for C_HEAP objects");
  DEBUG_ONLY(((AnyObj *)p)->_allocation_t[0] = (uintptr_t)badHeapOopVal;)
  FreeHeap(p);
}

#ifdef ASSERT
void AnyObj::set_allocation_type(address res, allocation_type type) {
  // Set allocation type in the resource object
  uintptr_t allocation = (uintptr_t)res;
  assert((allocation & allocation_mask) == 0, "address should be aligned to 4 bytes at least: " PTR_FORMAT, p2i(res));
  assert(type <= allocation_mask, "incorrect allocation type");
  AnyObj* resobj = (AnyObj *)res;
  resobj->_allocation_t[0] = ~(allocation + type);
  if (type != STACK_OR_EMBEDDED) {
    // Called from operator new(), set verification value.
    resobj->_allocation_t[1] = (uintptr_t)&(resobj->_allocation_t[1]) + type;
  }
}

AnyObj::allocation_type AnyObj::get_allocation_type() const {
  assert(~(_allocation_t[0] | allocation_mask) == (uintptr_t)this, "lost resource object");
  return (allocation_type)((~_allocation_t[0]) & allocation_mask);
}

bool AnyObj::is_type_set() const {
  allocation_type type = (allocation_type)(_allocation_t[1] & allocation_mask);
  return get_allocation_type()  == type &&
         (_allocation_t[1] - type) == (uintptr_t)(&_allocation_t[1]);
}

// This whole business of passing information from AnyObj::operator new
// to the AnyObj constructor via fields in the "object" is technically UB.
// But it seems to work within the limitations of HotSpot usage (such as no
// multiple inheritance) with the compilers and compiler options we're using.
// And it gives some possibly useful checking for misuse of AnyObj.
void AnyObj::initialize_allocation_info() {
  if (~(_allocation_t[0] | allocation_mask) != (uintptr_t)this) {
    // Operator new() is not called for allocations
    // on stack and for embedded objects.
    set_allocation_type((address)this, STACK_OR_EMBEDDED);
  } else if (allocated_on_stack_or_embedded()) { // STACK_OR_EMBEDDED
    // For some reason we got a value which resembles
    // an embedded or stack object (operator new() does not
    // set such type). Keep it since it is valid value
    // (even if it was garbage).
    // Ignore garbage in other fields.
  } else if (is_type_set()) {
    // Operator new() was called and type was set.
    assert(!allocated_on_stack_or_embedded(),
           "not embedded or stack, this(" PTR_FORMAT ") type %d a[0]=(" PTR_FORMAT ") a[1]=(" PTR_FORMAT ")",
           p2i(this), get_allocation_type(), _allocation_t[0], _allocation_t[1]);
  } else {
    // Operator new() was not called.
    // Assume that it is embedded or stack object.
    set_allocation_type((address)this, STACK_OR_EMBEDDED);
  }
  _allocation_t[1] = 0; // Zap verification value
}

AnyObj::AnyObj() {
  initialize_allocation_info();
}

AnyObj::AnyObj(const AnyObj&) {
  // Initialize _allocation_t as a new object, ignoring object being copied.
  initialize_allocation_info();
}

AnyObj& AnyObj::operator=(const AnyObj& r) {
  assert(allocated_on_stack_or_embedded(),
         "copy only into local, this(" PTR_FORMAT ") type %d a[0]=(" PTR_FORMAT ") a[1]=(" PTR_FORMAT ")",
         p2i(this), get_allocation_type(), _allocation_t[0], _allocation_t[1]);
  // Keep current _allocation_t value;
  return *this;
}

AnyObj::~AnyObj() {
  // allocated_on_C_heap() also checks that encoded (in _allocation) address == this.
  if (!allocated_on_C_heap()) { // AnyObj::delete() will zap _allocation for C_heap.
    _allocation_t[0] = (uintptr_t)badHeapOopVal; // zap type
  }
}
#endif // ASSERT

//--------------------------------------------------------------------------------------
// Non-product code

#ifndef PRODUCT
void AnyObj::print() const       { print_on(tty); }

void AnyObj::print_on(outputStream* st) const {
  st->print_cr("AnyObj(" PTR_FORMAT ")", p2i(this));
}

ReallocMark::ReallocMark() {
#ifdef ASSERT
  Thread *thread = Thread::current();
  _nesting = thread->resource_area()->nesting();
#endif
}

void ReallocMark::check(Arena* arena) {
#ifdef ASSERT
  if ((arena == nullptr || arena == Thread::current()->resource_area()) &&
      _nesting != Thread::current()->resource_area()->nesting()) {
    fatal("allocation bug: array could grow within nested ResourceMark");
  }
#endif
}

#endif // Non-product

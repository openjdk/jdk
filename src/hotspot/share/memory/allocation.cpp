/*
 * Copyright (c) 1997, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/arena.hpp"
#include "memory/metaspace.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/os.hpp"
#include "runtime/task.hpp"
#include "runtime/threadCritical.hpp"
#include "services/memTracker.hpp"
#include "utilities/ostream.hpp"

// allocate using malloc; will fail if no memory available
char* AllocateHeap(size_t size,
                   MEMFLAGS flags,
                   const NativeCallStack& stack,
                   AllocFailType alloc_failmode /* = AllocFailStrategy::EXIT_OOM*/) {
  char* p = (char*) os::malloc(size, flags, stack);
  if (p == NULL && alloc_failmode == AllocFailStrategy::EXIT_OOM) {
    vm_exit_out_of_memory(size, OOM_MALLOC_ERROR, "AllocateHeap");
  }
  return p;
}

char* AllocateHeap(size_t size,
                   MEMFLAGS flags,
                   AllocFailType alloc_failmode /* = AllocFailStrategy::EXIT_OOM*/) {
  return AllocateHeap(size, flags, CALLER_PC);
}

char* ReallocateHeap(char *old,
                     size_t size,
                     MEMFLAGS flag,
                     AllocFailType alloc_failmode) {
  char* p = (char*) os::realloc(old, size, flag, CALLER_PC);
  if (p == NULL && alloc_failmode == AllocFailStrategy::EXIT_OOM) {
    vm_exit_out_of_memory(size, OOM_MALLOC_ERROR, "ReallocateHeap");
  }
  return p;
}

// handles NULL pointers
void FreeHeap(void* p) {
  os::free(p);
}

void* MetaspaceObj::_shared_metaspace_base = NULL;
void* MetaspaceObj::_shared_metaspace_top  = NULL;

void* StackObj::operator new(size_t size)     throw() { ShouldNotCallThis(); return 0; }
void  StackObj::operator delete(void* p)              { ShouldNotCallThis(); }
void* StackObj::operator new [](size_t size)  throw() { ShouldNotCallThis(); return 0; }
void  StackObj::operator delete [](void* p)           { ShouldNotCallThis(); }

void* MetaspaceObj::operator new(size_t size, ClassLoaderData* loader_data,
                                 size_t word_size,
                                 MetaspaceObj::Type type, TRAPS) throw() {
  // Klass has its own operator new
  return Metaspace::allocate(loader_data, word_size, type, THREAD);
}

void* MetaspaceObj::operator new(size_t size, ClassLoaderData* loader_data,
                                 size_t word_size,
                                 MetaspaceObj::Type type) throw() {
  assert(!Thread::current()->is_Java_thread(), "only allowed by non-Java thread");
  return Metaspace::allocate(loader_data, word_size, type);
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
  st->print(" {" INTPTR_FORMAT "}", p2i(this));
}

void* ResourceObj::operator new(size_t size, Arena *arena) throw() {
  address res = (address)arena->Amalloc(size);
  DEBUG_ONLY(_thread_last_allocated = ARENA;)
  return res;
}

void* ResourceObj::operator new(size_t size, allocation_type type, MEMFLAGS flags) throw() {
  address res = NULL;
  switch (type) {
   case C_HEAP:
    res = (address)AllocateHeap(size, flags, CALLER_PC);
    DEBUG_ONLY(_thread_last_allocated = C_HEAP;)
    break;
   case RESOURCE_AREA:
    // new(size) sets allocation type RESOURCE_AREA.
    res = (address)operator new(size);
    break;
   default:
    ShouldNotReachHere();
  }
  return res;
}

void* ResourceObj::operator new(size_t size, const std::nothrow_t&  nothrow_constant,
    allocation_type type, MEMFLAGS flags) throw() {
  // should only call this with std::nothrow, use other operator new() otherwise
  address res = NULL;
  switch (type) {
   case C_HEAP:
    res = (address)AllocateHeap(size, flags, CALLER_PC, AllocFailStrategy::RETURN_NULL);
    DEBUG_ONLY(if (res!= NULL) _thread_last_allocated = C_HEAP;)
    break;
   case RESOURCE_AREA:
    // new(size) sets allocation type RESOURCE_AREA.
    res = (address)operator new(size, std::nothrow);
    break;
   default:
    ShouldNotReachHere();
  }
  return res;
}

void ResourceObj::operator delete(void* p) {
  assert(((ResourceObj *)p)->allocated_on_C_heap(),
         "delete only allowed for C_HEAP objects");
  FreeHeap(p);
}

#ifdef ASSERT
THREAD_LOCAL ResourceObj::allocation_type ResourceObj::_thread_last_allocated = STACK_OR_EMBEDDED;

ResourceObj::ResourceObj() : _type(_thread_last_allocated) { // _thread_last_allocated will be updated iff an operator new was called, and will default to STACK_OR_EMBEDDED
  _thread_last_allocated = STACK_OR_EMBEDDED; // reset _thread_last_allocated as the default STACK_OR_EMBEDDED can not be set by an operator new
}

ResourceObj::ResourceObj(const ResourceObj&) : _type(_thread_last_allocated) { // _thread_last_allocated will be updated iff an operator new was called, and will default to STACK_OR_EMBEDDED
  _thread_last_allocated = STACK_OR_EMBEDDED; // reset _thread_last_allocated as the default STACK_OR_EMBEDDED can not be set by an operator new
}

ResourceObj& ResourceObj::operator=(const ResourceObj& r) {
  return *this; // allocation type should *not* be updated on assignment, only on allocation
}

ResourceObj::allocation_type ResourceObj::get_allocation_type() const {
  return _type;
}
#endif //ASSERT
//--------------------------------------------------------------------------------------
// Non-product code

#ifndef PRODUCT
void AllocatedObj::print() const       { print_on(tty); }
void AllocatedObj::print_value() const { print_value_on(tty); }

void AllocatedObj::print_on(outputStream* st) const {
  st->print_cr("AllocatedObj(" INTPTR_FORMAT ")", p2i(this));
}

void AllocatedObj::print_value_on(outputStream* st) const {
  st->print("AllocatedObj(" INTPTR_FORMAT ")", p2i(this));
}

ReallocMark::ReallocMark() {
#ifdef ASSERT
  Thread *thread = Thread::current();
  _nesting = thread->resource_area()->nesting();
#endif
}

void ReallocMark::check() {
#ifdef ASSERT
  if (_nesting != Thread::current()->resource_area()->nesting()) {
    fatal("allocation bug: array could grow within nested ResourceMark");
  }
#endif
}

#endif // Non-product

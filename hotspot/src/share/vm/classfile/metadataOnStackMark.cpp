/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/metadataOnStackMark.hpp"
#include "code/codeCache.hpp"
#include "compiler/compileBroker.hpp"
#include "oops/metadata.hpp"
#include "prims/jvmtiImpl.hpp"
#include "runtime/synchronizer.hpp"
#include "runtime/thread.hpp"
#include "services/threadService.hpp"
#include "utilities/chunkedList.hpp"

volatile MetadataOnStackBuffer* MetadataOnStackMark::_used_buffers = NULL;
volatile MetadataOnStackBuffer* MetadataOnStackMark::_free_buffers = NULL;

NOT_PRODUCT(bool MetadataOnStackMark::_is_active = false;)

// Walk metadata on the stack and mark it so that redefinition doesn't delete
// it.  Class unloading also walks the previous versions and might try to
// delete it, so this class is used by class unloading also.
MetadataOnStackMark::MetadataOnStackMark(bool visit_code_cache) {
  assert(SafepointSynchronize::is_at_safepoint(), "sanity check");
  assert(_used_buffers == NULL, "sanity check");
  NOT_PRODUCT(_is_active = true;)

  Threads::metadata_do(Metadata::mark_on_stack);
  if (visit_code_cache) {
    CodeCache::alive_nmethods_do(nmethod::mark_on_stack);
  }
  CompileBroker::mark_on_stack();
  JvmtiCurrentBreakpoints::metadata_do(Metadata::mark_on_stack);
  ThreadService::metadata_do(Metadata::mark_on_stack);
}

MetadataOnStackMark::~MetadataOnStackMark() {
  assert(SafepointSynchronize::is_at_safepoint(), "sanity check");
  // Unmark everything that was marked.   Can't do the same walk because
  // redefine classes messes up the code cache so the set of methods
  // might not be the same.

  retire_buffer_for_thread(Thread::current());

  MetadataOnStackBuffer* buffer = const_cast<MetadataOnStackBuffer* >(_used_buffers);
  while (buffer != NULL) {
    // Clear on stack state for all metadata.
    size_t size = buffer->size();
    for (size_t i  = 0; i < size; i++) {
      Metadata* md = buffer->at(i);
      md->set_on_stack(false);
    }

    MetadataOnStackBuffer* next = buffer->next_used();

    // Move the buffer to the free list.
    buffer->clear();
    buffer->set_next_used(NULL);
    buffer->set_next_free(const_cast<MetadataOnStackBuffer*>(_free_buffers));
    _free_buffers = buffer;

    // Step to next used buffer.
    buffer = next;
  }

  _used_buffers = NULL;

  NOT_PRODUCT(_is_active = false;)
}

void MetadataOnStackMark::retire_buffer(MetadataOnStackBuffer* buffer) {
  if (buffer == NULL) {
    return;
  }

  MetadataOnStackBuffer* old_head;

  do {
    old_head = const_cast<MetadataOnStackBuffer*>(_used_buffers);
    buffer->set_next_used(old_head);
  } while (Atomic::cmpxchg_ptr(buffer, &_used_buffers, old_head) != old_head);
}

void MetadataOnStackMark::retire_buffer_for_thread(Thread* thread) {
  retire_buffer(thread->metadata_on_stack_buffer());
  thread->set_metadata_on_stack_buffer(NULL);
}

bool MetadataOnStackMark::has_buffer_for_thread(Thread* thread) {
  return thread->metadata_on_stack_buffer() != NULL;
}

MetadataOnStackBuffer* MetadataOnStackMark::allocate_buffer() {
  MetadataOnStackBuffer* allocated;
  MetadataOnStackBuffer* new_head;

  do {
    allocated = const_cast<MetadataOnStackBuffer*>(_free_buffers);
    if (allocated == NULL) {
      break;
    }
    new_head = allocated->next_free();
  } while (Atomic::cmpxchg_ptr(new_head, &_free_buffers, allocated) != allocated);

  if (allocated == NULL) {
    allocated = new MetadataOnStackBuffer();
  }

  assert(!allocated->is_full(), err_msg("Should not be full: " PTR_FORMAT, p2i(allocated)));

  return allocated;
}

// Record which objects are marked so we can unmark the same objects.
void MetadataOnStackMark::record(Metadata* m, Thread* thread) {
  assert(_is_active, "metadata on stack marking is active");

  MetadataOnStackBuffer* buffer =  thread->metadata_on_stack_buffer();

  if (buffer != NULL && buffer->is_full()) {
    retire_buffer(buffer);
    buffer = NULL;
  }

  if (buffer == NULL) {
    buffer = allocate_buffer();
    thread->set_metadata_on_stack_buffer(buffer);
  }

  buffer->push(m);
}

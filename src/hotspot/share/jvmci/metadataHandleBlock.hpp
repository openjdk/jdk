/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JVMCI_METADATAHANDLEBLOCK_HPP
#define SHARE_JVMCI_METADATAHANDLEBLOCK_HPP

#include "oops/constantPool.hpp"
#include "oops/metadata.hpp"
#include "oops/method.hpp"
#include "runtime/handles.hpp"
#include "runtime/os.hpp"

#ifdef ASSERT
#define METADATA_TRACK_NAMES
#endif

struct _jmetadata {
 private:
  Metadata* _value;
#ifdef METADATA_TRACK_NAMES
  // Debug data for tracking stale metadata
  const char* _name;
#endif

 public:
  Metadata* value() { return _value; }

#ifdef METADATA_TRACK_NAMES
  void initialize() {
    _value = NULL;
    _name = NULL;
  }
#endif

  void set_value(Metadata* value) {
    _value = value;
  }

#ifdef METADATA_TRACK_NAMES
  const char* name() { return _name; }
  void set_name(const char* name) {
    if (_name != NULL) {
      os::free((void*) _name);
      _name = NULL;
    }
    if (name != NULL) {
      _name = os::strdup(name);
    }
  }
#endif
};

typedef struct _jmetadata HandleRecord;
typedef struct _jmetadata *jmetadata;

// JVMCI maintains direct references to metadata. To make these references safe in the face of
// class redefinition, they are held in handles so they can be scanned during GC. They are
// managed in a cooperative way between the Java code and HotSpot. A handle is filled in and
// passed back to the Java code which is responsible for setting the handle to NULL when it
// is no longer in use. This is done by jdk.vm.ci.hotspot.HandleCleaner. The
// rebuild_free_list function notices when the handle is clear and reclaims it for re-use.
class MetadataHandleBlock : public CHeapObj<mtJVMCI> {
 private:
  enum SomeConstants {
    block_size_in_handles  = 32,      // Number of handles per handle block
    ptr_tag = 1,
    ptr_mask = ~((intptr_t)ptr_tag)
  };


  // Free handles always have their low bit set so those pointers can
  // be distinguished from handles which are in use.  The last handle
  // on the free list has a NULL pointer with the tag bit set, so it's
  // clear that the handle has been reclaimed.  The _free_list is
  // always a real pointer to a handle.

  HandleRecord    _handles[block_size_in_handles]; // The handles
  int             _top;                         // Index of next unused handle
  MetadataHandleBlock* _next;                   // Link to next block

  // The following instance variables are only used by the first block in a chain.
  // Having two types of blocks complicates the code and the space overhead is negligible.
  static MetadataHandleBlock* _last;                   // Last block in use
  static intptr_t        _free_list;                   // Handle free list
  static int             _allocate_before_rebuild;     // Number of blocks to allocate before rebuilding free list

  MetadataHandleBlock() {
    _top = 0;
    _next = NULL;
#ifdef METADATA_TRACK_NAMES
    for (int i = 0; i < block_size_in_handles; i++) {
      _handles[i].initialize();
    }
#endif
  }

  const char* get_name(int index) {
#ifdef METADATA_TRACK_NAMES
    return _handles[index].name();
#else
    return "<missing>";
#endif
  }

  static HandleRecord* get_free_handle() {
    assert(_free_list != 0, "should check before calling");
    HandleRecord* handle = (HandleRecord*) (_free_list & ptr_mask);
    _free_list = (ptr_mask & (intptr_t) (handle->value()));
    assert(_free_list != ptr_tag, "should be null");
    handle->set_value(NULL);
    return handle;
  }

  static HandleRecord* get_handle() {
    assert(_last != NULL, "sanity");
    // Try last block
    if (_last->_top < block_size_in_handles) {
      return &(_last->_handles)[_last->_top++];
    } else if (_free_list != 0) {
      // Try free list
      return get_free_handle();
    }
    return NULL;
  }

  void rebuild_free_list();

  jmetadata allocate_metadata_handle(Metadata* metadata);

 public:
  jmetadata allocate_handle(const methodHandle& handle)       { return allocate_metadata_handle(handle()); }
  jmetadata allocate_handle(const constantPoolHandle& handle) { return allocate_metadata_handle(handle()); }

  static MetadataHandleBlock* allocate_block() { return new MetadataHandleBlock(); }

  // Adds `handle` to the free list in this block
  static void chain_free_list(HandleRecord* handle) {
    handle->set_value((Metadata*) (ptr_tag | _free_list));
#ifdef METADATA_TRACK_NAMES
    handle->set_name(NULL);
#endif
    _free_list = (intptr_t) handle;
  }

  void metadata_do(void f(Metadata*));

  void do_unloading();
};

#endif // SHARE_JVMCI_METADATAHANDLEBLOCK_HPP

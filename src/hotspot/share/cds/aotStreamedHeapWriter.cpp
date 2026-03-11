/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/aotReferenceObjSupport.hpp"
#include "cds/aotStreamedHeapWriter.hpp"
#include "cds/cdsConfig.hpp"
#include "cds/filemap.hpp"
#include "cds/heapShared.inline.hpp"
#include "cds/regeneratedClasses.hpp"
#include "classfile/modules.hpp"
#include "classfile/stringTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/oopFactory.hpp"
#include "memory/universe.hpp"
#include "oops/compressedOops.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopHandle.inline.hpp"
#include "oops/typeArrayKlass.hpp"
#include "oops/typeArrayOop.hpp"
#include "runtime/java.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/bitMap.inline.hpp"
#include "utilities/stack.inline.hpp"

#if INCLUDE_CDS_JAVA_HEAP

GrowableArrayCHeap<u1, mtClassShared>* AOTStreamedHeapWriter::_buffer = nullptr;

// The following are offsets from buffer_bottom()
size_t AOTStreamedHeapWriter::_buffer_used;
size_t AOTStreamedHeapWriter::_roots_offset;
size_t AOTStreamedHeapWriter::_forwarding_offset;
size_t AOTStreamedHeapWriter::_root_highest_object_index_table_offset;

GrowableArrayCHeap<oop, mtClassShared>* AOTStreamedHeapWriter::_source_objs;

AOTStreamedHeapWriter::BufferOffsetToSourceObjectTable* AOTStreamedHeapWriter::_buffer_offset_to_source_obj_table;
AOTStreamedHeapWriter::SourceObjectToDFSOrderTable* AOTStreamedHeapWriter::_dfs_order_table;

int* AOTStreamedHeapWriter::_roots_highest_dfs;
size_t* AOTStreamedHeapWriter::_dfs_to_archive_object_table;

static const int max_table_capacity = 0x3fffffff;

void AOTStreamedHeapWriter::init() {
  if (CDSConfig::is_dumping_heap()) {
    _buffer_offset_to_source_obj_table = new (mtClassShared) BufferOffsetToSourceObjectTable(8, max_table_capacity);

    int initial_source_objs_capacity = 10000;
    _source_objs = new GrowableArrayCHeap<oop, mtClassShared>(initial_source_objs_capacity);
  }
}

void AOTStreamedHeapWriter::delete_tables_with_raw_oops() {
  delete _source_objs;
  _source_objs = nullptr;

  delete _dfs_order_table;
  _dfs_order_table = nullptr;
}

void AOTStreamedHeapWriter::add_source_obj(oop src_obj) {
  _source_objs->append(src_obj);
}

class FollowOopIterateClosure: public BasicOopIterateClosure {
  Stack<oop, mtClassShared>* _dfs_stack;
  oop _src_obj;
  bool _is_java_lang_ref;

public:
  FollowOopIterateClosure(Stack<oop, mtClassShared>* dfs_stack, oop src_obj, bool is_java_lang_ref) :
    _dfs_stack(dfs_stack),
    _src_obj(src_obj),
    _is_java_lang_ref(is_java_lang_ref) {}

  void do_oop(narrowOop *p) { do_oop_work(p); }
  void do_oop(      oop *p) { do_oop_work(p); }

private:
  template <class T> void do_oop_work(T *p) {
    size_t field_offset = pointer_delta(p, _src_obj, sizeof(char));
    oop obj = HeapShared::maybe_remap_referent(_is_java_lang_ref, field_offset, HeapAccess<>::oop_load(p));
    if (obj != nullptr) {
      _dfs_stack->push(obj);
    }
  }
};

int AOTStreamedHeapWriter::cmp_dfs_order(oop* o1, oop* o2) {
  int* o1_dfs = _dfs_order_table->get(*o1);
  int* o2_dfs = _dfs_order_table->get(*o2);
  return *o1_dfs - *o2_dfs;
}

void AOTStreamedHeapWriter::order_source_objs(GrowableArrayCHeap<oop, mtClassShared>* roots) {
  Stack<oop, mtClassShared> dfs_stack;
  _dfs_order_table = new (mtClassShared) SourceObjectToDFSOrderTable(8, max_table_capacity);
  _roots_highest_dfs = NEW_C_HEAP_ARRAY(int, (size_t)roots->length(), mtClassShared);
  _dfs_to_archive_object_table = NEW_C_HEAP_ARRAY(size_t, (size_t)_source_objs->length() + 1, mtClassShared);

  for (int i = 0; i < _source_objs->length(); ++i) {
    oop obj = _source_objs->at(i);
    _dfs_order_table->put(cast_from_oop<void*>(obj), -1);
    _dfs_order_table->maybe_grow();
  }

  int dfs_order = 0;

  for (int i = 0; i < roots->length(); ++i) {
    oop root = roots->at(i);

    if (root == nullptr) {
      log_info(aot, heap)("null root at %d", i);
      continue;
    }

    dfs_stack.push(root);

    while (!dfs_stack.is_empty()) {
      oop obj = dfs_stack.pop();
      assert(obj != nullptr, "null root");
      int* dfs_number = _dfs_order_table->get(cast_from_oop<void*>(obj));
      if (*dfs_number != -1) {
        // Already visited in the traversal
        continue;
      }
      _dfs_order_table->put(cast_from_oop<void*>(obj), ++dfs_order);
      _dfs_order_table->maybe_grow();

      FollowOopIterateClosure cl(&dfs_stack, obj, AOTReferenceObjSupport::check_if_ref_obj(obj));
      obj->oop_iterate(&cl);
    }

    _roots_highest_dfs[i] = dfs_order;
  }

  _source_objs->sort(cmp_dfs_order);
}

void AOTStreamedHeapWriter::write(GrowableArrayCHeap<oop, mtClassShared>* roots,
                                  AOTStreamedHeapInfo* heap_info) {
  assert(CDSConfig::is_dumping_heap(), "sanity");
  allocate_buffer();
  order_source_objs(roots);
  copy_source_objs_to_buffer(roots);
  map_embedded_oops(heap_info);
  populate_archive_heap_info(heap_info);
}

void AOTStreamedHeapWriter::allocate_buffer() {
  int initial_buffer_size = 100000;
  _buffer = new GrowableArrayCHeap<u1, mtClassShared>(initial_buffer_size);
  _buffer_used = 0;
  ensure_buffer_space(1); // so that buffer_bottom() works
}

void AOTStreamedHeapWriter::ensure_buffer_space(size_t min_bytes) {
  // We usually have very small heaps. If we get a huge one it's probably caused by a bug.
  guarantee(min_bytes <= max_jint, "we dont support archiving more than 2G of objects");
  _buffer->at_grow(to_array_index(min_bytes));
}

void AOTStreamedHeapWriter::copy_roots_to_buffer(GrowableArrayCHeap<oop, mtClassShared>* roots) {
  int length = roots->length();
  size_t byte_size = align_up(sizeof(int) + sizeof(int) * (size_t)length, (size_t)HeapWordSize);

  size_t new_used = _buffer_used + byte_size;
  ensure_buffer_space(new_used);

  int* mem = offset_to_buffered_address<int*>(_buffer_used);
  memset(mem, 0, byte_size);
  *mem = length;

  for (int i = 0; i < length; i++) {
    // Do not use arrayOop->obj_at_put(i, o) as arrayOop is outside of the real heap!
    oop o = roots->at(i);
    int dfs_index = o == nullptr ? 0 : *_dfs_order_table->get(cast_from_oop<void*>(o));
    mem[i + 1] = dfs_index;
  }
  log_info(aot, heap)("archived obj roots[%d] = %zu bytes, mem = %p", length, byte_size, mem);

  _roots_offset = _buffer_used;
  _buffer_used = new_used;
}

template <typename T>
void AOTStreamedHeapWriter::write(T value) {
  size_t new_used = _buffer_used + sizeof(T);
  ensure_buffer_space(new_used);
  T* mem = offset_to_buffered_address<T*>(_buffer_used);
  *mem = value;
  _buffer_used = new_used;
}

void AOTStreamedHeapWriter::copy_forwarding_to_buffer() {
  _forwarding_offset = _buffer_used;

  write<size_t>(0); // The first entry is the null entry

  // Write a mapping from object index to buffer offset
  for (int i = 1; i <= _source_objs->length(); i++) {
    size_t buffer_offset = _dfs_to_archive_object_table[i];
    write(buffer_offset);
  }
}

void AOTStreamedHeapWriter::copy_roots_max_dfs_to_buffer(int roots_length) {
  _root_highest_object_index_table_offset = _buffer_used;

  for (int i = 0; i < roots_length; ++i) {
    int highest_dfs = _roots_highest_dfs[i];
    write(highest_dfs);
  }

  if ((roots_length % 2) != 0) {
    write(-1); // Align up to a 64 bit word
  }
}

static bool is_interned_string(oop obj) {
  if (!java_lang_String::is_instance(obj)) {
    return false;
  }

  ResourceMark rm;
  int len;
  jchar* name = java_lang_String::as_unicode_string_or_null(obj, len);
  if (name == nullptr) {
    fatal("Insufficient memory for dumping");
  }
  return StringTable::lookup(name, len) == obj;
}

static BitMap::idx_t bit_idx_for_buffer_offset(size_t buffer_offset) {
  if (UseCompressedOops) {
    return BitMap::idx_t(buffer_offset / sizeof(narrowOop));
  } else {
    return BitMap::idx_t(buffer_offset / sizeof(HeapWord));
  }
}

bool AOTStreamedHeapWriter::is_dumped_interned_string(oop obj) {
  return is_interned_string(obj) && HeapShared::get_cached_oop_info(obj) != nullptr;
}

void AOTStreamedHeapWriter::copy_source_objs_to_buffer(GrowableArrayCHeap<oop, mtClassShared>* roots) {
  for (int i = 0; i < _source_objs->length(); i++) {
    oop src_obj = _source_objs->at(i);
    HeapShared::CachedOopInfo* info = HeapShared::get_cached_oop_info(src_obj);
    assert(info != nullptr, "must be");
    size_t buffer_offset = copy_one_source_obj_to_buffer(src_obj);
    info->set_buffer_offset(buffer_offset);

    OopHandle handle(Universe::vm_global(), src_obj);
    _buffer_offset_to_source_obj_table->put_when_absent(buffer_offset, handle);
    _buffer_offset_to_source_obj_table->maybe_grow();

    int dfs_order = i + 1;
    _dfs_to_archive_object_table[dfs_order] = buffer_offset;
  }

  copy_roots_to_buffer(roots);
  copy_forwarding_to_buffer();
  copy_roots_max_dfs_to_buffer(roots->length());

  log_info(aot)("Size of heap region = %zu bytes, %d objects, %d roots",
                _buffer_used, _source_objs->length() + 1, roots->length());
}

template <typename T>
void update_buffered_object_field(address buffered_obj, int field_offset, T value) {
  T* field_addr = cast_to_oop(buffered_obj)->field_addr<T>(field_offset);
  *field_addr = value;
}

static bool needs_explicit_size(oop src_obj) {
  Klass* klass = src_obj->klass();
  int lh = klass->layout_helper();

  // Simple instances or arrays don't need explicit size
  if (Klass::layout_helper_is_instance(lh)) {
    return Klass::layout_helper_needs_slow_path(lh);
  }

  return !Klass::layout_helper_is_array(lh);
}

size_t AOTStreamedHeapWriter::copy_one_source_obj_to_buffer(oop src_obj) {
  if (needs_explicit_size(src_obj)) {
    // Explicitly write object size for more complex objects, to avoid having to
    // pretend the buffer objects are objects when loading the objects, in order
    // to read the size. Most of the time, the layout helper of the class is enough.
    write<size_t>(src_obj->size());
  }
  size_t byte_size = src_obj->size() * HeapWordSize;
  assert(byte_size > 0, "no zero-size objects");

  size_t new_used = _buffer_used + byte_size;
  assert(new_used > _buffer_used, "no wrap around");

  ensure_buffer_space(new_used);

  if (is_interned_string(src_obj)) {
    java_lang_String::hash_code(src_obj);                   // Sets the hash code field(s)
    java_lang_String::set_deduplication_forbidden(src_obj); // Allows faster interning at runtime
    assert(java_lang_String::hash_is_set(src_obj), "hash must be set");
  }

  address from = cast_from_oop<address>(src_obj);
  address to = offset_to_buffered_address<address>(_buffer_used);
  assert(is_object_aligned(_buffer_used), "sanity");
  assert(is_object_aligned(byte_size), "sanity");
  memcpy(to, from, byte_size);

  if (java_lang_Module::is_instance(src_obj)) {
    // These native pointers will be restored explicitly at run time.
    Modules::check_archived_module_oop(src_obj);
    update_buffered_object_field<ModuleEntry*>(to, java_lang_Module::module_entry_offset(), nullptr);
  } else if (java_lang_ClassLoader::is_instance(src_obj)) {
#ifdef ASSERT
    // We only archive these loaders
    if (src_obj != SystemDictionary::java_platform_loader() &&
        src_obj != SystemDictionary::java_system_loader()) {
      assert(src_obj->klass()->name()->equals("jdk/internal/loader/ClassLoaders$BootClassLoader"), "must be");
    }
#endif
    update_buffered_object_field<ClassLoaderData*>(to, java_lang_ClassLoader::loader_data_offset(), nullptr);
  }

  size_t buffered_obj_offset = _buffer_used;
  _buffer_used = new_used;

  return buffered_obj_offset;
}

// Oop mapping

inline void AOTStreamedHeapWriter::store_oop_in_buffer(oop* buffered_addr, int dfs_index) {
  *(ssize_t*)buffered_addr = dfs_index;
}

inline void AOTStreamedHeapWriter::store_oop_in_buffer(narrowOop* buffered_addr, int dfs_index) {
  *(int32_t*)buffered_addr = (int32_t)dfs_index;
}

template <typename T> void AOTStreamedHeapWriter::mark_oop_pointer(T* buffered_addr, CHeapBitMap* oopmap) {
  // Mark the pointer in the oopmap
  size_t buffered_offset = buffered_address_to_offset((address)buffered_addr);
  BitMap::idx_t idx = bit_idx_for_buffer_offset(buffered_offset);
  oopmap->set_bit(idx);
}

template <typename T> void AOTStreamedHeapWriter::map_oop_field_in_buffer(oop obj, T* field_addr_in_buffer, CHeapBitMap* oopmap) {
  if (obj == nullptr) {
    store_oop_in_buffer(field_addr_in_buffer, 0);
  } else {
    int dfs_index = *_dfs_order_table->get(obj);
    store_oop_in_buffer(field_addr_in_buffer, dfs_index);
  }

  mark_oop_pointer<T>(field_addr_in_buffer, oopmap);
}

void AOTStreamedHeapWriter::update_header_for_buffered_addr(address buffered_addr, oop src_obj,  Klass* src_klass) {
  assert(UseCompressedClassPointers, "Archived heap only supported for compressed klasses");
  narrowKlass nk = ArchiveBuilder::current()->get_requested_narrow_klass(src_klass);

  markWord mw = markWord::prototype();
  oopDesc* fake_oop = (oopDesc*)buffered_addr;

  // We need to retain the identity_hash, because it may have been used by some hashtables
  // in the shared heap. This also has the side effect of pre-initializing the
  // identity_hash for all shared objects, so they are less likely to be written
  // into during run time, increasing the potential of memory sharing.
  if (src_obj != nullptr) {
    intptr_t src_hash = src_obj->identity_hash();
    mw = mw.copy_set_hash(src_hash);
  }

  if (is_interned_string(src_obj)) {
    // Mark the mark word of interned string so the loader knows to link these to
    // the string table at runtime.
    mw = mw.set_marked();
  }

  if (UseCompactObjectHeaders) {
    fake_oop->set_mark(mw.set_narrow_klass(nk));
  } else {
    fake_oop->set_mark(mw);
    fake_oop->set_narrow_klass(nk);
  }
}

class AOTStreamedHeapWriter::EmbeddedOopMapper: public BasicOopIterateClosure {
  oop _src_obj;
  address _buffered_obj;
  CHeapBitMap* _oopmap;
  bool _is_java_lang_ref;

public:
  EmbeddedOopMapper(oop src_obj, address buffered_obj, CHeapBitMap* oopmap)
    : _src_obj(src_obj),
      _buffered_obj(buffered_obj),
      _oopmap(oopmap),
      _is_java_lang_ref(AOTReferenceObjSupport::check_if_ref_obj(src_obj)) {}

  void do_oop(narrowOop *p) { EmbeddedOopMapper::do_oop_work(p); }
  void do_oop(      oop *p) { EmbeddedOopMapper::do_oop_work(p); }

private:
  template <typename T>
  void do_oop_work(T *p) {
    size_t field_offset = pointer_delta(p, _src_obj, sizeof(char));
    oop obj = HeapShared::maybe_remap_referent(_is_java_lang_ref, field_offset, HeapAccess<>::oop_load(p));
    AOTStreamedHeapWriter::map_oop_field_in_buffer<T>(obj, (T*)(_buffered_obj + field_offset), _oopmap);
  }
};

static void log_bitmap_usage(const char* which, BitMap* bitmap, size_t total_bits) {
  // The whole heap is covered by total_bits, but there are only non-zero bits within [start ... end).
  size_t start = bitmap->find_first_set_bit(0);
  size_t end = bitmap->size();
  log_info(aot)("%s = %7zu ... %7zu (%3zu%% ... %3zu%% = %3zu%%)", which,
                start, end,
                start * 100 / total_bits,
                end * 100 / total_bits,
                (end - start) * 100 / total_bits);
}

// Update all oop fields embedded in the buffered objects
void AOTStreamedHeapWriter::map_embedded_oops(AOTStreamedHeapInfo* heap_info) {
  size_t oopmap_unit = (UseCompressedOops ? sizeof(narrowOop) : sizeof(oop));
  size_t heap_region_byte_size = _buffer_used;
  heap_info->oopmap()->resize(heap_region_byte_size / oopmap_unit);

  for (int i = 0; i < _source_objs->length(); i++) {
    oop src_obj = _source_objs->at(i);
    HeapShared::CachedOopInfo* info = HeapShared::get_cached_oop_info(src_obj);
    assert(info != nullptr, "must be");
    address buffered_obj = offset_to_buffered_address<address>(info->buffer_offset());

    update_header_for_buffered_addr(buffered_obj, src_obj, src_obj->klass());

    EmbeddedOopMapper mapper(src_obj, buffered_obj, heap_info->oopmap());
    src_obj->oop_iterate(&mapper);
    HeapShared::remap_dumped_metadata(src_obj, buffered_obj);
  };

  size_t total_bytes = (size_t)_buffer->length();
  log_bitmap_usage("oopmap", heap_info->oopmap(), total_bytes / oopmap_unit);
}

size_t AOTStreamedHeapWriter::source_obj_to_buffered_offset(oop src_obj) {
  HeapShared::CachedOopInfo* p = HeapShared::get_cached_oop_info(src_obj);
  return p->buffer_offset();
}

address AOTStreamedHeapWriter::source_obj_to_buffered_addr(oop src_obj) {
  return offset_to_buffered_address<address>(source_obj_to_buffered_offset(src_obj));
}

oop AOTStreamedHeapWriter::buffered_offset_to_source_obj(size_t buffered_offset) {
  OopHandle* oh = _buffer_offset_to_source_obj_table->get(buffered_offset);
  if (oh != nullptr) {
    return oh->resolve();
  } else {
    return nullptr;
  }
}

oop AOTStreamedHeapWriter::buffered_addr_to_source_obj(address buffered_addr) {
  return buffered_offset_to_source_obj(buffered_address_to_offset(buffered_addr));
}

void AOTStreamedHeapWriter::populate_archive_heap_info(AOTStreamedHeapInfo* info) {
  assert(!info->is_used(), "only set once");

  size_t heap_region_byte_size = _buffer_used;
  assert(heap_region_byte_size > 0, "must archived at least one object!");

  info->set_buffer_region(MemRegion(offset_to_buffered_address<HeapWord*>(0),
                                    offset_to_buffered_address<HeapWord*>(_buffer_used)));
  info->set_roots_offset(_roots_offset);
  info->set_num_roots((size_t)HeapShared::pending_roots()->length());
  info->set_forwarding_offset(_forwarding_offset);
  info->set_root_highest_object_index_table_offset(_root_highest_object_index_table_offset);
  info->set_num_archived_objects((size_t)_source_objs->length());
}

AOTMapLogger::OopDataIterator* AOTStreamedHeapWriter::oop_iterator(AOTStreamedHeapInfo* heap_info) {
  class StreamedWriterOopIterator : public AOTStreamedHeapOopIterator {
  private:
    int _num_archived_roots;
    int* _roots;

  public:
    StreamedWriterOopIterator(address buffer_start,
                              int num_archived_objects,
                              int num_archived_roots,
                              int* roots)
      : AOTStreamedHeapOopIterator(buffer_start, num_archived_objects),
        _num_archived_roots(num_archived_roots),
        _roots(roots) {}

    AOTMapLogger::OopData capture(int dfs_index) override {
      size_t buffered_offset = _dfs_to_archive_object_table[dfs_index];
      address buffered_addr = _buffer_start + buffered_offset;
      oop src_obj = AOTStreamedHeapWriter::buffered_offset_to_source_obj(buffered_offset);
      assert(src_obj != nullptr, "why is this null?");
      oopDesc* raw_oop = (oopDesc*)buffered_addr;
      Klass* klass = src_obj->klass();
      size_t size = src_obj->size();

      intptr_t target_location = (intptr_t)buffered_offset;
      uint32_t narrow_location = checked_cast<uint32_t>(dfs_index);

      address requested_addr = (address)buffered_offset;

      return { buffered_addr,
               requested_addr,
               target_location,
               narrow_location,
               raw_oop,
               klass,
               size,
               false };
    }

    GrowableArrayCHeap<AOTMapLogger::OopData, mtClass>* roots() override {
      GrowableArrayCHeap<AOTMapLogger::OopData, mtClass>* result = new GrowableArrayCHeap<AOTMapLogger::OopData, mtClass>();

      for (int i = 0; i < _num_archived_roots; ++i) {
        int object_index = _roots[i];
        result->append(capture(object_index));
      }

      return result;
    }
  };

  MemRegion r = heap_info->buffer_region();
  address buffer_start = address(r.start());

  size_t roots_offset = heap_info->roots_offset();
  int* roots = ((int*)(buffer_start + roots_offset)) + 1;

  return new StreamedWriterOopIterator(buffer_start, (int)heap_info->num_archived_objects(), (int)heap_info->num_roots(), roots);
}

#endif // INCLUDE_CDS_JAVA_HEAP

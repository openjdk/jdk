/*
 * Copyright (c) 2003, 2008, Oracle and/or its affiliates. All rights reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_compactingPermGenGen.cpp.incl"


// An ObjectClosure helper: Recursively adjust all pointers in an object
// and all objects by referenced it. Clear marks on objects in order to
// prevent visiting any object twice. This helper is used when the
// RedefineClasses() API has been called.

class AdjustSharedObjectClosure : public ObjectClosure {
public:
  void do_object(oop obj) {
    if (obj->is_shared_readwrite()) {
      if (obj->mark()->is_marked()) {
        obj->init_mark();         // Don't revisit this object.
        obj->adjust_pointers();   // Adjust this object's references.
      }
    }
  }
};


// An OopClosure helper: Recursively adjust all pointers in an object
// and all objects by referenced it. Clear marks on objects in order
// to prevent visiting any object twice.

class RecursiveAdjustSharedObjectClosure : public OopClosure {
 protected:
  template <class T> inline void do_oop_work(T* p) {
    oop obj = oopDesc::load_decode_heap_oop_not_null(p);
    if (obj->is_shared_readwrite()) {
      if (obj->mark()->is_marked()) {
        obj->init_mark();         // Don't revisit this object.
        obj->oop_iterate(this);   // Recurse - adjust objects referenced.
        obj->adjust_pointers();   // Adjust this object's references.

        // Special case: if a class has a read-only constant pool,
        // then the read-write objects referenced by the pool must
        // have their marks reset.

        if (obj->klass() == Universe::instanceKlassKlassObj()) {
          instanceKlass* ik = instanceKlass::cast((klassOop)obj);
          constantPoolOop cp = ik->constants();
          if (cp->is_shared_readonly()) {
            cp->oop_iterate(this);
          }
        }
      }
    }
  }
 public:
  virtual void do_oop(oop* p)       { RecursiveAdjustSharedObjectClosure::do_oop_work(p); }
  virtual void do_oop(narrowOop* p) { RecursiveAdjustSharedObjectClosure::do_oop_work(p); }
};


// We need to go through all placeholders in the system dictionary and
// try to resolve them into shared classes. Other threads might be in
// the process of loading a shared class and have strong roots on
// their stack to the class without having added the class to the
// dictionary yet. This means the class will be marked during phase 1
// but will not be unmarked during the application of the
// RecursiveAdjustSharedObjectClosure to the SystemDictionary. Note
// that we must not call find_shared_class with non-read-only symbols
// as doing so can cause hash codes to be computed, destroying
// forwarding pointers.
class TraversePlaceholdersClosure : public OopClosure {
 protected:
  template <class T> inline void do_oop_work(T* p) {
    oop obj = oopDesc::load_decode_heap_oop_not_null(p);
    if (obj->klass() == Universe::symbolKlassObj() &&
        obj->is_shared_readonly()) {
      symbolHandle sym((symbolOop) obj);
      oop k = SystemDictionary::find_shared_class(sym);
      if (k != NULL) {
        RecursiveAdjustSharedObjectClosure clo;
        clo.do_oop(&k);
      }
    }
  }
 public:
  virtual void do_oop(oop* p)       { TraversePlaceholdersClosure::do_oop_work(p); }
  virtual void do_oop(narrowOop* p) { TraversePlaceholdersClosure::do_oop_work(p); }

};


void CompactingPermGenGen::initialize_performance_counters() {

  const char* gen_name = "perm";

  // Generation Counters - generation 2, 1 subspace
  _gen_counters = new GenerationCounters(gen_name, 2, 1, &_virtual_space);

  _space_counters = new CSpaceCounters(gen_name, 0,
                                       _virtual_space.reserved_size(),
                                      _the_space, _gen_counters);
}

void CompactingPermGenGen::update_counters() {
  if (UsePerfData) {
    _space_counters->update_all();
    _gen_counters->update_all();
  }
}


CompactingPermGenGen::CompactingPermGenGen(ReservedSpace rs,
                                           ReservedSpace shared_rs,
                                           size_t initial_byte_size,
                                           int level, GenRemSet* remset,
                                           ContiguousSpace* space,
                                           PermanentGenerationSpec* spec_) :
  OneContigSpaceCardGeneration(rs, initial_byte_size, MinPermHeapExpansion,
                               level, remset, space) {

  set_spec(spec_);
  if (!UseSharedSpaces && !DumpSharedSpaces) {
    spec()->disable_sharing();
  }

  // Break virtual space into address ranges for all spaces.

  if (spec()->enable_shared_spaces()) {
    shared_end = (HeapWord*)(shared_rs.base() + shared_rs.size());
      misccode_end = shared_end;
      misccode_bottom = misccode_end - heap_word_size(spec()->misc_code_size());
      miscdata_end = misccode_bottom;
      miscdata_bottom = miscdata_end - heap_word_size(spec()->misc_data_size());
      readwrite_end = miscdata_bottom;
      readwrite_bottom =
        readwrite_end - heap_word_size(spec()->read_write_size());
      readonly_end = readwrite_bottom;
      readonly_bottom =
        readonly_end - heap_word_size(spec()->read_only_size());
    shared_bottom = readonly_bottom;
    unshared_end = shared_bottom;
    assert((char*)shared_bottom == shared_rs.base(), "shared space mismatch");
  } else {
    shared_end = (HeapWord*)(rs.base() + rs.size());
      misccode_end = shared_end;
      misccode_bottom = shared_end;
      miscdata_end = shared_end;
      miscdata_bottom = shared_end;
      readwrite_end = shared_end;
      readwrite_bottom = shared_end;
      readonly_end = shared_end;
      readonly_bottom = shared_end;
    shared_bottom = shared_end;
    unshared_end = shared_bottom;
  }
  unshared_bottom = (HeapWord*) rs.base();

  // Verify shared and unshared spaces adjacent.
  assert((char*)shared_bottom == rs.base()+rs.size(), "shared space mismatch");
  assert(unshared_end > unshared_bottom, "shared space mismatch");

  // Split reserved memory into pieces.

  ReservedSpace ro_rs   = shared_rs.first_part(spec()->read_only_size(),
                                              UseSharedSpaces);
  ReservedSpace tmp_rs1 = shared_rs.last_part(spec()->read_only_size());
  ReservedSpace rw_rs   = tmp_rs1.first_part(spec()->read_write_size(),
                                             UseSharedSpaces);
  ReservedSpace tmp_rs2 = tmp_rs1.last_part(spec()->read_write_size());
  ReservedSpace md_rs   = tmp_rs2.first_part(spec()->misc_data_size(),
                                             UseSharedSpaces);
  ReservedSpace mc_rs   = tmp_rs2.last_part(spec()->misc_data_size());

  _shared_space_size = spec()->read_only_size()
                     + spec()->read_write_size()
                     + spec()->misc_data_size()
                     + spec()->misc_code_size();

  // Allocate the unshared (default) space.
  _the_space = new ContigPermSpace(_bts,
               MemRegion(unshared_bottom, heap_word_size(initial_byte_size)));
  if (_the_space == NULL)
    vm_exit_during_initialization("Could not allocate an unshared"
                                  " CompactingPermGen Space");

  // Allocate shared spaces
  if (spec()->enable_shared_spaces()) {

    // If mapping a shared file, the space is not committed, don't
    // mangle.
    NOT_PRODUCT(bool old_ZapUnusedHeapArea = ZapUnusedHeapArea;)
    NOT_PRODUCT(if (UseSharedSpaces) ZapUnusedHeapArea = false;)

    // Commit the memory behind the shared spaces if dumping (not
    // mapping).
    if (DumpSharedSpaces) {
      _ro_vs.initialize(ro_rs, spec()->read_only_size());
      _rw_vs.initialize(rw_rs, spec()->read_write_size());
      _md_vs.initialize(md_rs, spec()->misc_data_size());
      _mc_vs.initialize(mc_rs, spec()->misc_code_size());
    }

    // Allocate the shared spaces.
    _ro_bts = new BlockOffsetSharedArray(
                  MemRegion(readonly_bottom,
                            heap_word_size(spec()->read_only_size())),
                  heap_word_size(spec()->read_only_size()));
    _ro_space = new OffsetTableContigSpace(_ro_bts,
                  MemRegion(readonly_bottom, readonly_end));
    _rw_bts = new BlockOffsetSharedArray(
                  MemRegion(readwrite_bottom,
                            heap_word_size(spec()->read_write_size())),
                  heap_word_size(spec()->read_write_size()));
    _rw_space = new OffsetTableContigSpace(_rw_bts,
                  MemRegion(readwrite_bottom, readwrite_end));

    // Restore mangling flag.
    NOT_PRODUCT(ZapUnusedHeapArea = old_ZapUnusedHeapArea;)

    if (_ro_space == NULL || _rw_space == NULL)
      vm_exit_during_initialization("Could not allocate a shared space");

    // Cover both shared spaces entirely with cards.
    _rs->resize_covered_region(MemRegion(readonly_bottom, readwrite_end));

    if (UseSharedSpaces) {

      // Map in the regions in the shared file.
      FileMapInfo* mapinfo = FileMapInfo::current_info();
      size_t image_alignment = mapinfo->alignment();
      CollectedHeap* ch = Universe::heap();
      if ((!mapinfo->map_space(ro, ro_rs, _ro_space)) ||
          (!mapinfo->map_space(rw, rw_rs, _rw_space)) ||
          (!mapinfo->map_space(md, md_rs, NULL))      ||
          (!mapinfo->map_space(mc, mc_rs, NULL))      ||
          // check the alignment constraints
          (ch == NULL || ch->kind() != CollectedHeap::GenCollectedHeap ||
           image_alignment !=
           ((GenCollectedHeap*)ch)->gen_policy()->max_alignment())) {
        // Base addresses didn't match; skip sharing, but continue
        shared_rs.release();
        spec()->disable_sharing();
        // If -Xshare:on is specified, print out the error message and exit VM,
        // otherwise, set UseSharedSpaces to false and continue.
        if (RequireSharedSpaces) {
          vm_exit_during_initialization("Unable to use shared archive.", NULL);
        } else {
          FLAG_SET_DEFAULT(UseSharedSpaces, false);
        }

        // Note: freeing the block offset array objects does not
        // currently free up the underlying storage.
        delete _ro_bts;
        _ro_bts = NULL;
        delete _ro_space;
        _ro_space = NULL;
        delete _rw_bts;
        _rw_bts = NULL;
        delete _rw_space;
        _rw_space = NULL;
        shared_end = (HeapWord*)(rs.base() + rs.size());
        _rs->resize_covered_region(MemRegion(shared_bottom, shared_bottom));
      }
    }

    // Reserved region includes shared spaces for oop.is_in_reserved().
    _reserved.set_end(shared_end);

  } else {
    _ro_space = NULL;
    _rw_space = NULL;
  }
}


// Do a complete scan of the shared read write space to catch all
// objects which contain references to any younger generation.  Forward
// the pointers.  Avoid space_iterate, as actually visiting all the
// objects in the space will page in more objects than we need.
// Instead, use the system dictionary as strong roots into the read
// write space.
//
// If a RedefineClasses() call has been made, then we have to iterate
// over the entire shared read-write space in order to find all the
// objects that need to be forwarded. For example, it is possible for
// an nmethod to be found and marked in GC phase-1 only for the nmethod
// to be freed by the time we reach GC phase-3. The underlying method
// is still marked, but we can't (easily) find it in GC phase-3 so we
// blow up in GC phase-4. With RedefineClasses() we want replaced code
// (EMCP or obsolete) to go away (i.e., be collectible) once it is no
// longer being executed by any thread so we keep minimal attachments
// to the replaced code. However, we can't guarantee when those EMCP
// or obsolete methods will be collected so they may still be out there
// even after we've severed our minimal attachments.

void CompactingPermGenGen::pre_adjust_pointers() {
  if (spec()->enable_shared_spaces()) {
    if (JvmtiExport::has_redefined_a_class()) {
      // RedefineClasses() requires a brute force approach
      AdjustSharedObjectClosure blk;
      rw_space()->object_iterate(&blk);
    } else {
      RecursiveAdjustSharedObjectClosure blk;
      Universe::oops_do(&blk);
      StringTable::oops_do(&blk);
      SystemDictionary::always_strong_classes_do(&blk);
      TraversePlaceholdersClosure tpc;
      SystemDictionary::placeholders_do(&tpc);
    }
  }
}


#ifdef ASSERT
class VerifyMarksClearedClosure : public ObjectClosure {
public:
  void do_object(oop obj) {
    assert(SharedSkipVerify || !obj->mark()->is_marked(),
           "Shared oop still marked?");
  }
};
#endif


void CompactingPermGenGen::post_compact() {
#ifdef ASSERT
  if (!SharedSkipVerify && spec()->enable_shared_spaces()) {
    VerifyMarksClearedClosure blk;
    rw_space()->object_iterate(&blk);
  }
#endif
}


// Do not use in time-critical operations due to the possibility of paging
// in otherwise untouched or previously unread portions of the perm gen,
// for instance, the shared spaces. NOTE: Because CompactingPermGenGen
// derives from OneContigSpaceCardGeneration which is supposed to have a
// single space, and does not override its object_iterate() method,
// object iteration via that interface does not look at the objects in
// the shared spaces when using CDS. This should be fixed; see CR 6897798.
void CompactingPermGenGen::space_iterate(SpaceClosure* blk, bool usedOnly) {
  OneContigSpaceCardGeneration::space_iterate(blk, usedOnly);
  if (spec()->enable_shared_spaces()) {
    // Making the rw_space walkable will page in the entire space, and
    // is to be avoided in the case of time-critical operations.
    // However, this is required for Verify and heap dump operations.
    blk->do_space(ro_space());
    blk->do_space(rw_space());
  }
}


void CompactingPermGenGen::print_on(outputStream* st) const {
  OneContigSpaceCardGeneration::print_on(st);
  if (spec()->enable_shared_spaces()) {
    st->print("    ro");
    ro_space()->print_on(st);
    st->print("    rw");
    rw_space()->print_on(st);
  } else {
    st->print_cr("No shared spaces configured.");
  }
}


// References from the perm gen to the younger generation objects may
// occur in static fields in Java classes or in constant pool references
// to String objects.

void CompactingPermGenGen::younger_refs_iterate(OopsInGenClosure* blk) {
  OneContigSpaceCardGeneration::younger_refs_iterate(blk);
  if (spec()->enable_shared_spaces()) {
    blk->set_generation(this);
    // ro_space has no younger gen refs.
    _rs->younger_refs_in_space_iterate(rw_space(), blk);
    blk->reset_generation();
  }
}


// Shared spaces are addressed in pre_adjust_pointers.
void CompactingPermGenGen::adjust_pointers() {
  the_space()->adjust_pointers();
}


void CompactingPermGenGen::compact() {
  the_space()->compact();
}


size_t CompactingPermGenGen::contiguous_available() const {
  // Don't include shared spaces.
  return OneContigSpaceCardGeneration::contiguous_available()
         - _shared_space_size;
}

size_t CompactingPermGenGen::max_capacity() const {
  // Don't include shared spaces.
  assert(UseSharedSpaces || (_shared_space_size == 0),
    "If not used, the size of shared spaces should be 0");
  return OneContigSpaceCardGeneration::max_capacity()
          - _shared_space_size;
}


// No young generation references, clear this generation's main space's
// card table entries.  Do NOT clear the card table entries for the
// read-only space (always clear) or the read-write space (valuable
// information).

void CompactingPermGenGen::clear_remembered_set() {
  _rs->clear(MemRegion(the_space()->bottom(), the_space()->end()));
}


// Objects in this generation's main space may have moved, invalidate
// that space's cards.  Do NOT invalidate the card table entries for the
// read-only or read-write spaces, as those objects never move.

void CompactingPermGenGen::invalidate_remembered_set() {
  _rs->invalidate(used_region());
}


void CompactingPermGenGen::verify(bool allow_dirty) {
  the_space()->verify(allow_dirty);
  if (!SharedSkipVerify && spec()->enable_shared_spaces()) {
    ro_space()->verify(allow_dirty);
    rw_space()->verify(allow_dirty);
  }
}


HeapWord* CompactingPermGenGen::unshared_bottom;
HeapWord* CompactingPermGenGen::unshared_end;
HeapWord* CompactingPermGenGen::shared_bottom;
HeapWord* CompactingPermGenGen::shared_end;
HeapWord* CompactingPermGenGen::readonly_bottom;
HeapWord* CompactingPermGenGen::readonly_end;
HeapWord* CompactingPermGenGen::readwrite_bottom;
HeapWord* CompactingPermGenGen::readwrite_end;
HeapWord* CompactingPermGenGen::miscdata_bottom;
HeapWord* CompactingPermGenGen::miscdata_end;
HeapWord* CompactingPermGenGen::misccode_bottom;
HeapWord* CompactingPermGenGen::misccode_end;

// JVM/TI RedefineClasses() support:
bool CompactingPermGenGen::remap_shared_readonly_as_readwrite() {
  assert(SafepointSynchronize::is_at_safepoint(), "must be at safepoint");

  if (UseSharedSpaces) {
    // remap the shared readonly space to shared readwrite, private
    FileMapInfo* mapinfo = FileMapInfo::current_info();
    if (!mapinfo->remap_shared_readonly_as_readwrite()) {
      return false;
    }
  }
  return true;
}

void** CompactingPermGenGen::_vtbl_list;

/*
 * Copyright 2003-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

// All heaps contains a "permanent generation," containing permanent
// (reflective) objects.  This is like a regular generation in some ways,
// but unlike one in others, and so is split apart.

class PermanentGenerationSpec;

// This is the "generation" view of a CompactingPermGen.
// NOTE: the shared spaces used for CDS are here handled in
// a somewhat awkward and potentially buggy fashion, see CR 6801625.
// This infelicity should be fixed, see CR 6897789.
class CompactingPermGenGen: public OneContigSpaceCardGeneration {
  friend class VMStructs;
  // Abstractly, this is a subtype that gets access to protected fields.
  friend class CompactingPermGen;

private:
  // Shared spaces
  PermanentGenerationSpec* _spec;
  size_t _shared_space_size;
  VirtualSpace _ro_vs;
  VirtualSpace _rw_vs;
  VirtualSpace _md_vs;
  VirtualSpace _mc_vs;
  BlockOffsetSharedArray* _ro_bts;
  BlockOffsetSharedArray* _rw_bts;
  OffsetTableContigSpace* _ro_space;
  OffsetTableContigSpace* _rw_space;

  // With shared spaces there is a dichotomy in the use of the
  // _virtual_space of the generation.  There is a portion of the
  // _virtual_space that is used for the unshared part of the
  // permanent generation and a portion that is reserved for the shared part.
  // The _reserved field in the generation represents both the
  // unshared and shared parts of the generation.  The _reserved
  // variable is initialized for only the unshared part but is
  // later extended to include the shared part during initialization
  // if shared spaces are being used.
  // The reserved size for the _virtual_space for CompactingPermGenGen
  // is the size of the space for the permanent generation including the
  // the shared spaces.  This can be seen by the use of MaxPermSize
  // in the allocation of PermanentGenerationSpec.  The space for the
  // shared spaces is committed separately (???).
  // In general at initialization only a part of the
  // space for the unshared part of the permanent generation is
  // committed and more is committed as the permanent generation is
  // grown.  In growing the permanent generation the capacity() and
  // max_capacity() of the generation are used.  For the permanent
  // generation (implemented with a CompactingPermGenGen) the capacity()
  // is taken from the capacity of the space (_the_space variable used for the
  // unshared part of the generation) and the max_capacity() is based
  // on the size of the _reserved variable (which includes the size of the
  // shared spaces) minus the size of the shared spaces.

  // These values are redundant, but are called out separately to avoid
  // going through heap/space/gen pointers for performance.
  static HeapWord* unshared_bottom;
  static HeapWord* unshared_end;
  static HeapWord* shared_bottom;
  static HeapWord* readonly_bottom;
  static HeapWord* readonly_end;
  static HeapWord* readwrite_bottom;
  static HeapWord* readwrite_end;
  static HeapWord* miscdata_bottom;
  static HeapWord* miscdata_end;
  static HeapWord* misccode_bottom;
  static HeapWord* misccode_end;
  static HeapWord* shared_end;

  // List of klassOops whose vtbl entries are used to patch others.
  static void**        _vtbl_list;

  // Performance Counters
  GenerationCounters*  _gen_counters;
  CSpaceCounters*      _space_counters;

  void initialize_performance_counters();

public:

  enum {
    vtbl_list_size = 16, // number of entries in the shared space vtable list.
    num_virtuals = 200   // number of virtual methods in Klass (or
                         // subclass) objects, or greater.
  };

  enum {
    ro = 0,  // read-only shared space in the heap
    rw = 1,  // read-write shared space in the heap
    md = 2,  // miscellaneous data for initializing tables, etc.
    mc = 3,  // miscellaneous code - vtable replacement.
    n_regions = 4
  };

  CompactingPermGenGen(ReservedSpace rs, ReservedSpace shared_rs,
                       size_t initial_byte_size, int level, GenRemSet* remset,
                       ContiguousSpace* space,
                       PermanentGenerationSpec* perm_spec);

  const char* name() const {
    return "compacting perm gen";
  }

  const char* short_name() const {
    return "Perm";
  }

  // Return the maximum capacity for the object space.  This
  // explicitly does not include the shared spaces.
  size_t max_capacity() const;

  void update_counters();

  void compute_new_size() {
    assert(false, "Should not call this -- handled at PermGen level.");
  }

  bool must_be_youngest() const { return false; }
  bool must_be_oldest() const { return false; }

  OffsetTableContigSpace* ro_space() const { return _ro_space; }
  OffsetTableContigSpace* rw_space() const { return _rw_space; }
  VirtualSpace*           md_space()       { return &_md_vs; }
  VirtualSpace*           mc_space()       { return &_mc_vs; }
  ContiguousSpace* unshared_space() const { return _the_space; }

  static bool inline is_shared(const oopDesc* p) {
    return (HeapWord*)p >= shared_bottom && (HeapWord*)p < shared_end;
  }
  // RedefineClasses note: this tester is used to check residence of
  // the specified oop in the shared readonly space and not whether
  // the oop is readonly.
  static bool inline is_shared_readonly(const oopDesc* p) {
    return (HeapWord*)p >= readonly_bottom && (HeapWord*)p < readonly_end;
  }
  // RedefineClasses note: this tester is used to check residence of
  // the specified oop in the shared readwrite space and not whether
  // the oop is readwrite.
  static bool inline is_shared_readwrite(const oopDesc* p) {
    return (HeapWord*)p >= readwrite_bottom && (HeapWord*)p < readwrite_end;
  }

  bool is_in_unshared(const void* p) const {
    return OneContigSpaceCardGeneration::is_in(p);
  }

  bool is_in_shared(const void* p) const {
   return p >= shared_bottom && p < shared_end;
   }

  inline bool is_in(const void* p) const {
    return is_in_unshared(p) || is_in_shared(p);
  }

  inline PermanentGenerationSpec* spec() const { return _spec; }
  inline void set_spec(PermanentGenerationSpec* spec) { _spec = spec; }

  void pre_adjust_pointers();
  void adjust_pointers();
  void space_iterate(SpaceClosure* blk, bool usedOnly = false);
  void print_on(outputStream* st) const;
  void younger_refs_iterate(OopsInGenClosure* blk);
  void compact();
  void post_compact();
  size_t contiguous_available() const;

  void clear_remembered_set();
  void invalidate_remembered_set();

  inline bool block_is_obj(const HeapWord* addr) const {
    if      (addr < the_space()->top()) return true;
    else if (addr < the_space()->end()) return false;
    else if (addr < ro_space()->top())  return true;
    else if (addr < ro_space()->end())  return false;
    else if (addr < rw_space()->top())  return true;
    else                                return false;
  }


  inline size_t block_size(const HeapWord* addr) const {
    if (addr < the_space()->top()) {
      return oop(addr)->size();
    }
    else if (addr < the_space()->end()) {
      assert(addr == the_space()->top(), "non-block head arg to block_size");
      return the_space()->end() - the_space()->top();
    }

    else if (addr < ro_space()->top()) {
      return oop(addr)->size();
    }
    else if (addr < ro_space()->end()) {
      assert(addr == ro_space()->top(), "non-block head arg to block_size");
      return ro_space()->end() - ro_space()->top();
    }

    else if (addr < rw_space()->top()) {
      return oop(addr)->size();
    }
    else {
      assert(addr == rw_space()->top(), "non-block head arg to block_size");
      return rw_space()->end() - rw_space()->top();
    }
  }

  static void generate_vtable_methods(void** vtbl_list,
                                      void** vtable,
                                      char** md_top, char* md_end,
                                      char** mc_top, char* mc_end);

  void verify(bool allow_dirty);

  // Serialization
  static void initialize_oops() KERNEL_RETURN;
  static void serialize_oops(SerializeOopClosure* soc);
  void serialize_bts(SerializeOopClosure* soc);

  // Initiate dumping of shared file.
  static jint dump_shared(GrowableArray<oop>* class_promote_order, TRAPS);

  // JVM/TI RedefineClasses() support:
  // Remap the shared readonly space to shared readwrite, private if
  // sharing is enabled. Simply returns true if sharing is not enabled
  // or if the remapping has already been done by a prior call.
  static bool remap_shared_readonly_as_readwrite();
};

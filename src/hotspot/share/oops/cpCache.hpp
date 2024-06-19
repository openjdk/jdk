/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OOPS_CPCACHE_HPP
#define SHARE_OOPS_CPCACHE_HPP

#include "interpreter/bytecodes.hpp"
#include "memory/allocation.hpp"
#include "oops/array.hpp"
#include "oops/oopHandle.hpp"
#include "runtime/handles.hpp"
#include "utilities/align.hpp"
#include "utilities/constantTag.hpp"
#include "utilities/growableArray.hpp"

// The ConstantPoolCache is not a cache! It is the resolution table that the
// interpreter uses to avoid going into the runtime and a way to access resolved
// values.

class CallInfo;
class ResolvedFieldEntry;
class ResolvedIndyEntry;
class ResolvedMethodEntry;

// A constant pool cache is a runtime data structure set aside to a constant pool. The cache
// holds runtime information for all field access and invoke bytecodes. The cache
// is created and initialized before a class is actively used (i.e., initialized), the indivi-
// dual cache entries are filled at resolution (i.e., "link") time (see also: rewriter.*).

class ConstantPoolCache: public MetaspaceObj {
  friend class VMStructs;
  friend class MetadataFactory;
 private:
  // If you add a new field that points to any metaspace object, you
  // must add this field to ConstantPoolCache::metaspace_pointers_do().

  // The narrowOop pointer to the archived resolved_references. Set at CDS dump
  // time when caching java heap object is supported.
  CDS_JAVA_HEAP_ONLY(int _archived_references_index;) // Gap on LP64

  ConstantPool*   _constant_pool;          // the corresponding constant pool

  // The following fields need to be modified at runtime, so they cannot be
  // stored in the ConstantPool, which is read-only.
  // Array of resolved objects from the constant pool and map from resolved
  // object index to original constant pool index
  OopHandle            _resolved_references;
  Array<u2>*           _reference_map;

  // RedefineClasses support
  uint64_t             _gc_epoch;

  Array<ResolvedIndyEntry>*   _resolved_indy_entries;
  Array<ResolvedFieldEntry>*  _resolved_field_entries;
  Array<ResolvedMethodEntry>* _resolved_method_entries;

  // Sizing
  debug_only(friend class ClassVerifier;)

  public:
    // specific but defiinitions for ldc
    enum {
      // high order bits are the TosState corresponding to field type or method return type
      tos_state_bits             = 4,
      tos_state_mask             = right_n_bits(tos_state_bits),
      tos_state_shift            = BitsPerInt - tos_state_bits,  // see verify_tos_state_shift below
      // low order bits give field index (for FieldInfo) or method parameter size:
      field_index_bits           = 16,
      field_index_mask           = right_n_bits(field_index_bits),
    };

  // Constructor
  ConstantPoolCache(const intStack& invokedynamic_references_map,
                    Array<ResolvedIndyEntry>* indy_info,
                    Array<ResolvedFieldEntry>* field_entries,
                    Array<ResolvedMethodEntry>* mehtod_entries);

  // Initialization
  void initialize(const intArray& invokedynamic_references_map);
 public:
  static ConstantPoolCache* allocate(ClassLoaderData* loader_data,
                                     const intStack& invokedynamic_references_map,
                                     const GrowableArray<ResolvedIndyEntry> indy_entries,
                                     const GrowableArray<ResolvedFieldEntry> field_entries,
                                     const GrowableArray<ResolvedMethodEntry> method_entries,
                                     TRAPS);

  void metaspace_pointers_do(MetaspaceClosure* it);
  MetaspaceObj::Type type() const         { return ConstantPoolCacheType; }

  oop  archived_references() NOT_CDS_JAVA_HEAP_RETURN_(nullptr);
  void set_archived_references(int root_index) NOT_CDS_JAVA_HEAP_RETURN;
  void clear_archived_references() NOT_CDS_JAVA_HEAP_RETURN;

  inline objArrayOop resolved_references();
  void set_resolved_references(OopHandle s) { _resolved_references = s; }
  Array<u2>* reference_map() const        { return _reference_map; }
  void set_reference_map(Array<u2>* o)    { _reference_map = o; }

 private:
  void set_direct_or_vtable_call(
    Bytecodes::Code invoke_code,                 // the bytecode used for invoking the method
    int method_index,                            // Index into the resolved method entry array
    const methodHandle& method,                  // the method/prototype if any (null, otherwise)
    int             vtable_index,                // the vtable index if any, else negative
    bool            sender_is_interface
  );

 public:
  void set_direct_call(                          // sets entry to exact concrete method entry
    Bytecodes::Code invoke_code,                 // the bytecode used for invoking the method
    int method_index,                            // Index into the resolved method entry array
    const methodHandle& method,                  // the method to call
    bool            sender_is_interface
  );

  void set_vtable_call(                          // sets entry to vtable index
    Bytecodes::Code invoke_code,                 // the bytecode used for invoking the method
    int method_index,                            // Index into the resolved method entry array
    const methodHandle& method,                  // resolved method which declares the vtable index
    int             vtable_index                 // the vtable index
  );

  void set_itable_call(
    Bytecodes::Code invoke_code,                 // the bytecode used; must be invokeinterface
    int method_index,                            // Index into the resolved method entry array
    Klass* referenced_klass,                     // the referenced klass in the InterfaceMethodref
    const methodHandle& method,                  // the resolved interface method
    int itable_index                             // index into itable for the method
  );

  // The "appendix" is an optional call-site-specific parameter which is
  // pushed by the JVM at the end of the argument list.  This argument may
  // be a MethodType for the MH.invokes and a CallSite for an invokedynamic
  // instruction.  However, its exact type and use depends on the Java upcall,
  // which simply returns a compiled LambdaForm along with any reference
  // that LambdaForm needs to complete the call.  If the upcall returns a
  // null appendix, the argument is not passed at all.
  //
  // The appendix is *not* represented in the signature of the symbolic
  // reference for the call site, but (if present) it *is* represented in
  // the Method* bound to the site.  This means that static and dynamic
  // resolution logic needs to make slightly different assessments about the
  // number and types of arguments.
  ResolvedMethodEntry* set_method_handle(
    int method_index,
    const CallInfo &call_info                    // Call link information
  );

  Method*      method_if_resolved(int method_index) const;

  Array<ResolvedFieldEntry>* resolved_field_entries()          { return _resolved_field_entries; }
  inline ResolvedFieldEntry* resolved_field_entry_at(int field_index) const;
  inline int resolved_field_entries_length() const;
  void print_resolved_field_entries(outputStream* st) const;

  Array<ResolvedIndyEntry>* resolved_indy_entries()          { return _resolved_indy_entries; }
  inline ResolvedIndyEntry* resolved_indy_entry_at(int index) const;
  inline int resolved_indy_entries_length() const;
  void print_resolved_indy_entries(outputStream* st)   const;

  Array<ResolvedMethodEntry>* resolved_method_entries()          { return _resolved_method_entries; }
  inline ResolvedMethodEntry* resolved_method_entry_at(int method_index) const;
  inline int resolved_method_entries_length() const;
  void print_resolved_method_entries(outputStream* st) const;

  // Assembly code support
  static ByteSize resolved_references_offset()     { return byte_offset_of(ConstantPoolCache, _resolved_references);     }
  static ByteSize invokedynamic_entries_offset()   { return byte_offset_of(ConstantPoolCache, _resolved_indy_entries);   }
  static ByteSize field_entries_offset()           { return byte_offset_of(ConstantPoolCache, _resolved_field_entries);  }
  static ByteSize method_entries_offset()          { return byte_offset_of(ConstantPoolCache, _resolved_method_entries); }

#if INCLUDE_CDS
  void remove_unshareable_info();
#endif

 public:
  static int size() { return align_metadata_size(sizeof(ConstantPoolCache) / wordSize); }

 private:
  // Helpers
  ConstantPool**        constant_pool_addr()     { return &_constant_pool; }

 public:
  // Accessors
  void set_constant_pool(ConstantPool* pool)   { _constant_pool = pool; }
  ConstantPool* constant_pool() const          { return _constant_pool; }

  // Code generation
  static ByteSize base_offset()                  { return in_ByteSize(sizeof(ConstantPoolCache)); }

#if INCLUDE_JVMTI
  // RedefineClasses() API support:
  // If any entry of this ConstantPoolCache points to any of
  // old_methods, replace it with the corresponding new_method.
  // trace_name_printed is set to true if the current call has
  // printed the klass name so that other routines in the adjust_*
  // group don't print the klass name.
  void adjust_method_entries(bool* trace_name_printed);
  bool check_no_old_or_obsolete_entries();
  void dump_cache();
#endif // INCLUDE_JVMTI

#if INCLUDE_CDS
  void remove_resolved_field_entries_if_non_deterministic();
#endif

  // RedefineClasses support
  DEBUG_ONLY(bool on_stack() { return false; })
  void deallocate_contents(ClassLoaderData* data);
  bool is_klass() const { return false; }
  void record_gc_epoch();
  uint64_t gc_epoch() { return _gc_epoch; }

  // Return TRUE if resolution failed and this thread got to record the failure
  // status.  Return FALSE if another thread succeeded or failed in resolving
  // the method and recorded the success or failure before this thread had a
  // chance to record its failure.
  bool save_and_throw_indy_exc(const constantPoolHandle& cpool, int cpool_index, int index, constantTag tag, TRAPS);
  oop set_dynamic_call(const CallInfo &call_info, int index);
  oop appendix_if_resolved(int method_index) const;
  oop appendix_if_resolved(ResolvedMethodEntry* method_entry) const;

  // Printing
  void print_on(outputStream* st) const;
  void print_value_on(outputStream* st) const;

  const char* internal_name() const { return "{constant pool cache}"; }

  // Verify
  void verify_on(outputStream* st);
};

#endif // SHARE_OOPS_CPCACHE_HPP

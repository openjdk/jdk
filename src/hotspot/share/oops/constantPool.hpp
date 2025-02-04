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

#ifndef SHARE_OOPS_CONSTANTPOOL_HPP
#define SHARE_OOPS_CONSTANTPOOL_HPP

#include "memory/allocation.hpp"
#include "oops/arrayOop.hpp"
#include "oops/cpCache.hpp"
#include "oops/objArrayOop.hpp"
#include "oops/oopHandle.hpp"
#include "oops/symbol.hpp"
#include "oops/typeArrayOop.hpp"
#include "runtime/handles.hpp"
#include "runtime/javaThread.hpp"
#include "utilities/align.hpp"
#include "utilities/bytes.hpp"
#include "utilities/constantTag.hpp"
#include "utilities/macros.hpp"
#include "utilities/resourceHash.hpp"

// A ConstantPool is an array containing class constants as described in the
// class file.
//
// Most of the constant pool entries are written during class parsing, which
// is safe.  For klass types, the constant pool entry is
// modified when the entry is resolved.  If a klass constant pool
// entry is read without a lock, only the resolved state guarantees that
// the entry in the constant pool is a klass object and not a Symbol*.

// This represents a JVM_CONSTANT_Class, JVM_CONSTANT_UnresolvedClass, or
// JVM_CONSTANT_UnresolvedClassInError slot in the constant pool.
class CPKlassSlot {
  // cp->symbol_at(_name_index) gives the name of the class.
  int _name_index;

  // cp->_resolved_klasses->at(_resolved_klass_index) gives the Klass* for the class.
  int _resolved_klass_index;
public:
  enum {
    // This is used during constant pool merging where the resolved klass index is
    // not yet known, and will be computed at a later stage (during a call to
    // initialize_unresolved_klasses()).
    _temp_resolved_klass_index = 0xffff
  };
  CPKlassSlot(int n, int rk) {
    _name_index = n;
    _resolved_klass_index = rk;
  }
  int name_index() const {
    return _name_index;
  }
  int resolved_klass_index() const {
    assert(_resolved_klass_index != _temp_resolved_klass_index, "constant pool merging was incomplete");
    return _resolved_klass_index;
  }
};

class ConstantPool : public Metadata {
  friend class VMStructs;
  friend class JVMCIVMStructs;
  friend class BytecodeInterpreter;  // Directly extracts a klass in the pool for fast instanceof/checkcast
  friend class Universe;             // For null constructor
  friend class AOTConstantPoolResolver;

 private:
  // If you add a new field that points to any metaspace object, you
  // must add this field to ConstantPool::metaspace_pointers_do().
  Array<u1>*           _tags;        // the tag array describing the constant pool's contents
  ConstantPoolCache*   _cache;       // the cache holding interpreter runtime information
  InstanceKlass*       _pool_holder; // the corresponding class

  // Consider using an array of compressed klass pointers to
  // save space on 64-bit platforms.
  Array<Klass*>*       _resolved_klasses;

  // Support for indy/condy BootstrapMethods attribute, with variable-sized entries
  Array<u4>*           _bsm_attribute_offsets;  // offsets to the BSMAEs
  Array<u2>*           _bsm_attribute_entries;  // BSMAttributeEntry structs (variable-sized)

  u2              _major_version;        // major version number of class file
  u2              _minor_version;        // minor version number of class file

  // Constant pool index to the utf8 entry of the Generic signature,
  // or 0 if none.
  u2              _generic_signature_index;
  // Constant pool index to the utf8 entry for the name of source file
  // containing this klass, 0 if not specified.
  u2              _source_file_name_index;

  enum {
    _has_preresolution    = 1,       // Flags
    _on_stack             = 2,
    _is_shared            = 4,
    _has_dynamic_constant = 8,
    _is_for_method_handle_intrinsic = 16
  };

  u2              _flags;  // old fashioned bit twiddling

  int             _length; // number of elements in the array

  union {
    // set for CDS to restore resolved references
    int                _resolved_reference_length;
    // keeps version number for redefined classes (used in backtrace)
    int                _version;
  } _saved;

  void set_tags(Array<u1>* tags)                 { _tags = tags; }
  void tag_at_put(int cp_index, jbyte t)         { tags()->at_put(cp_index, t); }
  void release_tag_at_put(int cp_index, jbyte t) { tags()->release_at_put(cp_index, t); }

  u1* tag_addr_at(int cp_index) const            { return tags()->adr_at(cp_index); }

  void set_bsm_attribute_offsets(Array<u4>* offs) { _bsm_attribute_offsets = offs; }
  void set_bsm_attribute_entries(Array<u2>* data) { _bsm_attribute_entries = data; }

  u2 flags() const                             { return _flags; }
  void set_flags(u2 f)                         { _flags = f; }

 private:
  intptr_t* base() const { return (intptr_t*) (((char*) this) + sizeof(ConstantPool)); }

  intptr_t* obj_at_addr(int cp_index) const {
    assert(is_within_bounds(cp_index), "index out of bounds");
    return (intptr_t*) &base()[cp_index];
  }

  jint* int_at_addr(int cp_index) const {
    assert(is_within_bounds(cp_index), "index out of bounds");
    return (jint*) &base()[cp_index];
  }

  jlong* long_at_addr(int cp_index) const {
    assert(is_within_bounds(cp_index), "index out of bounds");
    return (jlong*) &base()[cp_index];
  }

  jfloat* float_at_addr(int cp_index) const {
    assert(is_within_bounds(cp_index), "index out of bounds");
    return (jfloat*) &base()[cp_index];
  }

  jdouble* double_at_addr(int cp_index) const {
    assert(is_within_bounds(cp_index), "index out of bounds");
    return (jdouble*) &base()[cp_index];
  }
  static void check_and_add_dumped_interned_string(oop obj);

  #define assert_valid_tag(cp, cp_index, expr)                         \
    assert(cp->tag_at(cp_index).expr,                                  \
           "Corrupted constant pool"                                   \
           " [%d]: tag=%d", cp_index, cp->tag_at(cp_index).value())

  ConstantPool(Array<u1>* tags);
  ConstantPool();
 public:
  static ConstantPool* allocate(ClassLoaderData* loader_data, int length, TRAPS);

  virtual bool is_constantPool() const      { return true; }

  Array<u1>* tags() const                   { return _tags; }
  Array<u2>* bsm_attribute_entries() const  { return _bsm_attribute_entries; }
  Array<u4>* bsm_attribute_offsets() const  { return _bsm_attribute_offsets; }

  bool has_preresolution() const            { return (_flags & _has_preresolution) != 0; }
  void set_has_preresolution() {
    assert(!is_shared(), "should never be called on shared ConstantPools");
    _flags |= _has_preresolution;
  }

  // minor and major version numbers of class file
  u2 major_version() const                 { return _major_version; }
  void set_major_version(u2 major_version) { _major_version = major_version; }
  u2 minor_version() const                 { return _minor_version; }
  void set_minor_version(u2 minor_version) { _minor_version = minor_version; }

  // generics support
  Symbol* generic_signature() const {
    return (_generic_signature_index == 0) ?
      nullptr : symbol_at(_generic_signature_index);
  }
  u2 generic_signature_index() const                   { return _generic_signature_index; }
  void set_generic_signature_index(u2 sig_index)       { _generic_signature_index = sig_index; }

  // source file name
  Symbol* source_file_name() const {
    return (_source_file_name_index == 0) ?
      nullptr : symbol_at(_source_file_name_index);
  }
  u2 source_file_name_index() const                    { return _source_file_name_index; }
  void set_source_file_name_index(u2 sourcefile_index) { _source_file_name_index = sourcefile_index; }

  void copy_fields(const ConstantPool* orig);

  // Redefine classes support.  If a method referring to this constant pool
  // is on the executing stack, or as a handle in vm code, this constant pool
  // can't be removed from the set of previous versions saved in the instance
  // class.
  bool on_stack() const;
  bool is_maybe_on_stack() const;
  void set_on_stack(const bool value);

  // Faster than MetaspaceObj::is_shared() - used by set_on_stack()
  bool is_shared() const                     { return (_flags & _is_shared) != 0; }

  bool has_dynamic_constant() const       { return (_flags & _has_dynamic_constant) != 0; }
  void set_has_dynamic_constant()         { _flags |= _has_dynamic_constant; }

  bool is_for_method_handle_intrinsic() const  { return (_flags & _is_for_method_handle_intrinsic) != 0; }
  void set_is_for_method_handle_intrinsic()    { _flags |= _is_for_method_handle_intrinsic; }

  // Klass holding pool
  InstanceKlass* pool_holder() const      { return _pool_holder; }
  void set_pool_holder(InstanceKlass* k)  { _pool_holder = k; }
  InstanceKlass** pool_holder_addr()      { return &_pool_holder; }

  // Interpreter runtime support
  ConstantPoolCache* cache() const        { return _cache; }
  void set_cache(ConstantPoolCache* cache){ _cache = cache; }

  virtual void metaspace_pointers_do(MetaspaceClosure* iter);
  virtual MetaspaceObj::Type type() const { return ConstantPoolType; }

  // Create object cache in the constant pool
  void initialize_resolved_references(ClassLoaderData* loader_data,
                                      const intStack& reference_map,
                                      int constant_pool_map_length,
                                      TRAPS);

  // resolved strings, methodHandles and callsite objects from the constant pool
  objArrayOop resolved_references()  const;
  objArrayOop resolved_references_or_null()  const;
  oop resolved_reference_at(int obj_index) const;
  oop set_resolved_reference_at(int index, oop new_value);

  // mapping resolved object array indexes to cp indexes and back.
  int object_to_cp_index(int index)         { return reference_map()->at(index); }
  int cp_to_object_index(int index);

  void set_resolved_klasses(Array<Klass*>* rk)  { _resolved_klasses = rk; }
  Array<Klass*>* resolved_klasses() const       { return _resolved_klasses; }
  void allocate_resolved_klasses(ClassLoaderData* loader_data, int num_klasses, TRAPS);
  void initialize_unresolved_klasses(ClassLoaderData* loader_data, TRAPS);

  // Correct access to resolved_klasses:
  Klass* resolved_klass_at_acquire(int resolved_klass_index) const {
    // NOT: return resolved_klasses()->at(resolved_klass_index);
    return resolved_klasses()->at_acquire(resolved_klass_index);
    // Must do an acquire here in case another thread resolved the klass
    // behind our back, lest we later load stale values thru the oop.
  }
  void resolved_klass_release_at_put(int resolved_klass_index, Klass* klass) const {
    resolved_klasses()->release_at_put(resolved_klass_index, klass);
  }

  // Assembly code support
  static ByteSize tags_offset()         { return byte_offset_of(ConstantPool, _tags); }
  static ByteSize cache_offset()        { return byte_offset_of(ConstantPool, _cache); }
  static ByteSize pool_holder_offset()  { return byte_offset_of(ConstantPool, _pool_holder); }
  static ByteSize resolved_klasses_offset()    { return byte_offset_of(ConstantPool, _resolved_klasses); }

  // Storing constants

  // For temporary use while constructing constant pool
  void klass_index_at_put(int cp_index, int name_index) {
    tag_at_put(cp_index, JVM_CONSTANT_ClassIndex);
    *int_at_addr(cp_index) = name_index;
  }

  // Hidden class support:
  void klass_at_put(int class_index, Klass* k);

  void unresolved_klass_at_put(int cp_index, int name_index, int resolved_klass_index) {
    release_tag_at_put(cp_index, JVM_CONSTANT_UnresolvedClass);

    assert((name_index & 0xffff0000) == 0, "must be");
    assert((resolved_klass_index & 0xffff0000) == 0, "must be");
    *int_at_addr(cp_index) =
      build_int_from_shorts((jushort)resolved_klass_index, (jushort)name_index);
  }

  void method_handle_index_at_put(int cp_index, int ref_kind, int ref_index) {
    tag_at_put(cp_index, JVM_CONSTANT_MethodHandle);
    *int_at_addr(cp_index) = ((jint) ref_index<<16) | ref_kind;
  }

  void method_type_index_at_put(int cp_index, int ref_index) {
    tag_at_put(cp_index, JVM_CONSTANT_MethodType);
    *int_at_addr(cp_index) = ref_index;
  }

  void dynamic_constant_at_put(int cp_index, int bsm_attribute_index, int name_and_type_index) {
    tag_at_put(cp_index, JVM_CONSTANT_Dynamic);
    *int_at_addr(cp_index) = ((jint) name_and_type_index<<16) | bsm_attribute_index;
  }

  void invoke_dynamic_at_put(int cp_index, int bsm_attribute_index, int name_and_type_index) {
    tag_at_put(cp_index, JVM_CONSTANT_InvokeDynamic);
    *int_at_addr(cp_index) = ((jint) name_and_type_index<<16) | bsm_attribute_index;
  }

  void unresolved_string_at_put(int cp_index, Symbol* s) {
    assert(s->refcount() != 0, "should have nonzero refcount");
    // Note that release_tag_at_put is not needed here because this is called only
    // when constructing a ConstantPool in a single thread, with no possibility
    // of concurrent access.
    tag_at_put(cp_index, JVM_CONSTANT_String);
    *symbol_at_addr(cp_index) = s;
  }

  void int_at_put(int cp_index, jint i) {
    tag_at_put(cp_index, JVM_CONSTANT_Integer);
    *int_at_addr(cp_index) = i;
  }

  void long_at_put(int cp_index, jlong l) {
    tag_at_put(cp_index, JVM_CONSTANT_Long);
    // *long_at_addr(which) = l;
    Bytes::put_native_u8((address)long_at_addr(cp_index), *((u8*) &l));
  }

  void float_at_put(int cp_index, jfloat f) {
    tag_at_put(cp_index, JVM_CONSTANT_Float);
    *float_at_addr(cp_index) = f;
  }

  void double_at_put(int cp_index, jdouble d) {
    tag_at_put(cp_index, JVM_CONSTANT_Double);
    // *double_at_addr(which) = d;
    // u8 temp = *(u8*) &d;
    Bytes::put_native_u8((address) double_at_addr(cp_index), *((u8*) &d));
  }

  Symbol** symbol_at_addr(int cp_index) const {
    assert(is_within_bounds(cp_index), "index out of bounds");
    return (Symbol**) &base()[cp_index];
  }

  void symbol_at_put(int cp_index, Symbol* s) {
    assert(s->refcount() != 0, "should have nonzero refcount");
    tag_at_put(cp_index, JVM_CONSTANT_Utf8);
    *symbol_at_addr(cp_index) = s;
  }

  void string_at_put(int obj_index, oop str);

  // For temporary use while constructing constant pool
  void string_index_at_put(int cp_index, int string_index) {
    tag_at_put(cp_index, JVM_CONSTANT_StringIndex);
    *int_at_addr(cp_index) = string_index;
  }

  void field_at_put(int cp_index, int class_index, int name_and_type_index) {
    tag_at_put(cp_index, JVM_CONSTANT_Fieldref);
    *int_at_addr(cp_index) = ((jint) name_and_type_index<<16) | class_index;
  }

  void method_at_put(int cp_index, int class_index, int name_and_type_index) {
    tag_at_put(cp_index, JVM_CONSTANT_Methodref);
    *int_at_addr(cp_index) = ((jint) name_and_type_index<<16) | class_index;
  }

  void interface_method_at_put(int cp_index, int class_index, int name_and_type_index) {
    tag_at_put(cp_index, JVM_CONSTANT_InterfaceMethodref);
    *int_at_addr(cp_index) = ((jint) name_and_type_index<<16) | class_index;  // Not so nice
  }

  void name_and_type_at_put(int cp_index, int name_index, int signature_index) {
    tag_at_put(cp_index, JVM_CONSTANT_NameAndType);
    *int_at_addr(cp_index) = ((jint) signature_index<<16) | name_index;  // Not so nice
  }

  // Tag query

  constantTag tag_at(int cp_index) const { return (constantTag)tags()->at_acquire(cp_index); }

  // Fetching constants

  Klass* klass_at(int cp_index, TRAPS) {
    constantPoolHandle h_this(THREAD, this);
    return klass_at_impl(h_this, cp_index, THREAD);
  }

  CPKlassSlot klass_slot_at(int cp_index) const {
    assert_valid_tag(this, cp_index, is_klass_or_reference());
    int value = *int_at_addr(cp_index);
    int name_index = extract_high_short_from_int(value);
    int resolved_klass_index = extract_low_short_from_int(value);
    return CPKlassSlot(name_index, resolved_klass_index);
  }

  Symbol* klass_name_at(int cp_index) const {
    return symbol_at(klass_name_index_at(cp_index));
  }
  int klass_name_index_at(int cp_index) const {
    return klass_slot_at(cp_index).name_index();
  }

  Klass* resolved_klass_at(int cp_index) const;  // Used by Compiler

  // RedefineClasses() API support:
  void temp_unresolved_klass_at_put(int cp_index, int name_index) {
    // Used only during constant pool merging for class redefinition. The resolved klass index
    // will be initialized later by a call to initialize_unresolved_klasses().
    unresolved_klass_at_put(cp_index, name_index, CPKlassSlot::_temp_resolved_klass_index);
  }

  jint int_at(int cp_index) const {
    assert_valid_tag(this, cp_index, is_int());
    return *int_at_addr(cp_index);
  }

  jlong long_at(int cp_index) {
    assert_valid_tag(this, cp_index, is_long());
    // return *long_at_addr(cp_index);
    u8 tmp = Bytes::get_native_u8((address)&base()[cp_index]);
    return *((jlong*)&tmp);
  }

  jfloat float_at(int cp_index) {
    assert_valid_tag(this, cp_index, is_float());
    return *float_at_addr(cp_index);
  }

  jdouble double_at(int cp_index) {
    assert_valid_tag(this, cp_index, is_double());
    u8 tmp = Bytes::get_native_u8((address)&base()[cp_index]);
    return *((jdouble*)&tmp);
  }

  Symbol* symbol_at(int cp_index) const {
    assert_valid_tag(this, cp_index, is_utf8());
    return *symbol_at_addr(cp_index);
  }

  oop string_at(int cp_index, int obj_index, TRAPS) {
    constantPoolHandle h_this(THREAD, this);
    return string_at_impl(h_this, cp_index, obj_index, THREAD);
  }
  oop string_at(int cp_index, TRAPS) {
    int obj_index = cp_to_object_index(cp_index);
    return string_at(cp_index, obj_index, THREAD);
  }

  // Version that can be used before string oop array is created.
  oop uncached_string_at(int cp_index, TRAPS);

  // only called when we are sure a string entry is already resolved (via an
  // earlier string_at call.
  oop resolved_string_at(int cp_index) {
    assert_valid_tag(this, cp_index, is_string());
    // Must do an acquire here in case another thread resolved the klass
    // behind our back, lest we later load stale values thru the oop.
    // we might want a volatile_obj_at in ObjArrayKlass.
    int obj_index = cp_to_object_index(cp_index);
    return resolved_reference_at(obj_index);
  }

  Symbol* unresolved_string_at(int cp_index) {
    assert_valid_tag(this, cp_index, is_string());
    return *symbol_at_addr(cp_index);
  }

  // Returns an UTF8 for a CONSTANT_String entry at a given index.
  // UTF8 char* representation was chosen to avoid conversion of
  // java_lang_Strings at resolved entries into Symbol*s
  // or vice versa.
  char* string_at_noresolve(int cp_index);

  // All the relevant components of a CONSTANT_NameAndType CP entry.
  // Other more complex CP entries, that represent symbolic references,
  // are built on top of this one.  We use inheritance, specifically
  // instance layout extension, to build the symbolic references on
  // top of this one.  That way, all of them inherit the same name
  // and signature API.
  //
  // This struct and its subtypes is cheap to build.  Also, it is cheap
  // to optimize away unused field values within the struct, if the
  // object construction is inlined correctly.
  //
  // Warning:  Please keep these structs simple, just tuples of
  // index values and other small integers.  It's probably a bad
  // idea to add destructors, virtual methods, or pointer fields.
  class NTReference {
    // some of these could be u1, but let's just keep it uniform
   protected:
    u2 _tag;                      // tag of the CP node *using* the name&type
                                  // it is protected so it can be reassigned
   private:
    u2 _nt_index;                 // index of the name&type being used
    u2 _name_index;               // index of a name symbol (from name&type)
    u2 _signature_index;          // index of a signature symbol

    DEBUG_ONLY(ConstantPool* _check_cp;)

   protected:
    void record_cp(ConstantPool* cp) {
      DEBUG_ONLY(_check_cp = cp);
    }
    #ifdef ASSERT
    bool is_same_cp(ConstantPool* cp) const {
      return _check_cp == cp;  // "wrong CP pointer"
    }
    #endif

   public:
    constantTag tag() const       { return constantTag(tag_byte()); }
    jbyte tag_byte() const        { return (jbyte)_tag; }
    int name_index() const        { return _name_index; }
    int signature_index() const   { return _signature_index; }
    int nt_index() const          { return _nt_index; }

    // utility methods for mapping indexes to metadata items
    Symbol* name(ConstantPool* cp) const {
      assert(is_same_cp(cp), "wrong CP pointer");
      return cp->symbol_at(name_index());
    }
    Symbol* signature(ConstantPool* cp) const {
      assert(is_same_cp(cp), "wrong CP pointer");
      return cp->symbol_at(signature_index());
    }
    // and a duplicate set for clients holding a CP handle
    Symbol* name(const constantPoolHandle& cp) const {
      return name(cp());
    }
    Symbol* signature(const constantPoolHandle& cp) const {
      return signature(cp());
    }

    // You can always create a blank one, containing only zeroes.
    NTReference()
      : _tag(JVM_CONSTANT_Invalid),
        _nt_index(0), _name_index(0), _signature_index(0)
    {
      record_cp(nullptr);  // non-garbage value
    }

    // usage:
    //   NTReference nt(my_cp, my_cp_index);
    //   int ni = nt.name_index();
    //   Symbol* sig = nt.signature(my_cp);
    NTReference(ConstantPool* cp, u2 cp_index)
      : NTReference(/*allow_malformed*/ false, cp, cp_index) { }
    NTReference(const constantPoolHandle& cp, u2 cp_index)
      : NTReference(cp(), cp_index) { }

  private:
    // The allow_malformed option is only used by the classfile
    // parser's early validation logic.  It suppresses a single
    // assert that is normally done.  If the assert would
    // fail because there is no NameAndType at the address,
    // the data structure is not populated with name and type.
    friend class ConstantPool;
    NTReference(bool allow_malformed, ConstantPool* cp, u2 cp_index) {
      DEBUG_ONLY(record_cp(cp));
      _nt_index = cp_index;
      if (allow_malformed && !cp->tag_at(cp_index).is_name_and_type()) {
        // This path is used only in CP validation phases of the classfile parser.
        // In addition, it only happens when the classfile parser is parsing
        // a classfile with a malformed NameAndType reference but has not yet
        // gotten around to throwing an exception for the classfile.
        // The only calls which can set allow_malformed to true originate
        // from the classfile parser, which is a friend of this class
        // (and also RawReference) so it can set that flag to true.
        //
        // We could make a separate function for it.  Common code is better,
        // because then there are fewer code paths that need exercise.
        return;
      }
      // This is where 99.9999% of the code goes:
      assert_valid_tag(cp, cp_index, is_name_and_type());
      jint bits        = *cp->int_at_addr(cp_index);
      _name_index      = extract_low_short_from_int(bits);
      _signature_index = extract_high_short_from_int(bits);
      _tag             = JVM_CONSTANT_NameAndType;  // may be overwritten
      // Note: subtypes of NTReference will overwrite this->_tag.
    }
  };

  constantTag tag_ref_at(int which, Bytecodes::Code code) {
    return tag_at(to_cp_index(which, code));
  }

  int to_cp_index(int which, Bytecodes::Code code);

  bool is_resolved(int which, Bytecodes::Code code);

  // The following method expects to be passed indices obtained
  // directly from the bytecode.
  // If the indices are meant to refer to fields or methods, they are
  // actually rewritten indices that point to entries in their respective structures
  // i.e. ResolvedMethodEntries or ResolvedFieldEntries.
  // The routine to_cp_index manages the adjustment
  // of these values back to constant pool indices.

  // Resolve string constants (to prevent allocation during compilation)
  void resolve_string_constants(TRAPS) {
    constantPoolHandle h_this(THREAD, this);
    resolve_string_constants_impl(h_this, CHECK);
  }

#if INCLUDE_CDS
  // CDS support
  objArrayOop prepare_resolved_references_for_archiving() NOT_CDS_JAVA_HEAP_RETURN_(nullptr);
  void add_dumped_interned_strings() NOT_CDS_JAVA_HEAP_RETURN;
  void remove_unshareable_info();
  void restore_unshareable_info(TRAPS);
private:
  void remove_unshareable_entries();
  void remove_resolved_klass_if_non_deterministic(int cp_index);
  template <typename Function> void iterate_archivable_resolved_references(Function function);
#endif

 private:
  enum { _no_index_sentinel = -1, _possible_index_sentinel = -2 };
 public:

  // Get the tag for a constant, which may involve a constant dynamic
  constantTag constant_tag_at(int cp_index);
  // Get the basic type for a constant, which may involve a constant dynamic
  BasicType basic_type_for_constant_at(int cp_index);

  // Resolve late bound constants.
  oop resolve_constant_at(int cp_index, TRAPS) {
    constantPoolHandle h_this(THREAD, this);
    return resolve_constant_at_impl(h_this, cp_index, _no_index_sentinel, nullptr, THREAD);
  }

  oop resolve_cached_constant_at(int cache_index, TRAPS) {
    constantPoolHandle h_this(THREAD, this);
    return resolve_constant_at_impl(h_this, _no_index_sentinel, cache_index, nullptr, THREAD);
  }

  oop resolve_possibly_cached_constant_at(int cp_index, TRAPS) {
    constantPoolHandle h_this(THREAD, this);
    return resolve_constant_at_impl(h_this, cp_index, _possible_index_sentinel, nullptr, THREAD);
  }

  oop find_cached_constant_at(int cp_index, bool& found_it, TRAPS) {
    constantPoolHandle h_this(THREAD, this);
    return resolve_constant_at_impl(h_this, cp_index, _possible_index_sentinel, &found_it, THREAD);
  }

  void copy_bootstrap_arguments_at(int bsme_index,
                                   int start_arg, int end_arg,
                                   objArrayHandle info, int pos,
                                   bool must_resolve, Handle if_not_available, TRAPS) {
    constantPoolHandle h_this(THREAD, this);
    copy_bootstrap_arguments_at_impl(h_this, bsme_index,
                                     start_arg, end_arg,
                                     info, pos, must_resolve, if_not_available, THREAD);
  }

  // Klass name matches name at offset
  bool klass_name_at_matches(const InstanceKlass* k, int cp_index);

  // Sizing
  int length() const                   { return _length; }
  void set_length(int length)          { _length = length; }

  // Tells whether index is within bounds.
  bool is_within_bounds(int index) const {
    return 0 <= index && index < length();
  }

  // Sizing (in words)
  static int header_size()             {
    return align_up((int)sizeof(ConstantPool), wordSize) / wordSize;
  }
  static int size(int length)          { return align_metadata_size(header_size() + length); }
  int size() const                     { return size(length()); }

  // ConstantPools should be stored in the read-only region of CDS archive.
  static bool is_read_only_by_default() { return true; }

  friend class ClassFileParser;
  friend class SystemDictionary;

  // Used by CDS. These classes need to access the private ConstantPool() constructor.
  template <class T> friend class CppVtableTesterA;
  template <class T> friend class CppVtableTesterB;
  template <class T> friend class CppVtableCloner;

  // Used by compiler to prevent classloading.
  static Method*          method_at_if_loaded      (const constantPoolHandle& this_cp, int which);
  static bool       has_appendix_at_if_loaded      (const constantPoolHandle& this_cp, int which, Bytecodes::Code code);
  static oop            appendix_at_if_loaded      (const constantPoolHandle& this_cp, int which, Bytecodes::Code code);
  static bool has_local_signature_at_if_loaded     (const constantPoolHandle& this_cp, int which, Bytecodes::Code code);
  static Klass*            klass_at_if_loaded      (const constantPoolHandle& this_cp, int which);

  // Common structure for the following tags:
  // CONSTANT_Fieldref, CONSTANT_Methodref, CONSTANT_InterfaceMethodref
  // CONSTANT_InvokeDynamic, CONSTANT_Dynamic
  // This is less common than FMReference or BootstrapReference,
  // but is handy in a few places.
  // A "raw" reference is a 3-tuple of (x, name, type), where x
  // is the index of a klass or a bootstrap attribute entry.
  // Since the CP node structure is identical, we treat "triples"
  // using common code.
  class FMReference;         // subclass of RawReference
  class BootstrapReference;  // ditto
  class RawReference : public NTReference {
   private:
    // a NameAndType has two u2 indexes, and here is the third:
    u2 _third_index;              // klass index or bsme index

   public:
    // utility methods for mapping indexes to metadata items
    int third_index() const       { return _third_index; }

    // You can always create a blank one, containing only zeroes.
    RawReference() : _third_index(0) { }

    // usage:
    //   RawReference r3(my_cp, my_cp_index);
    //   int ni = r3.name_index();
    //   Symbol* sig = r3.signature(my_cp);
    //   int bsme = r3.tag().has_bootstrap() ? r3.third_index() : -1;
    RawReference(ConstantPool* cp, u2 cp_index)
      : RawReference(false, cp, cp_index) { }
    RawReference(const constantPoolHandle& cp, u2 cp_index)
      : RawReference(cp(), cp_index) { }

    RawReference(ConstantPool* cp, int cp_cache_index, Bytecodes::Code code)
      : RawReference(cp, cp->to_cp_index(cp_cache_index, code)) { }
    RawReference(const constantPoolHandle& cp, int cp_cache_index, Bytecodes::Code code)
      : RawReference(cp(), cp_cache_index, code) { }

    // checked narrowing operations (in-place downcasts)
    const FMReference& as_FMRef() {
      assert(tag().is_field_or_method(), "must be valid as FMReference");
      return *(const FMReference*)this;
    }
    const BootstrapReference& as_BSRef() {
      assert(tag().has_bootstrap(), "must be valid as BootstrapReference");
      return *(const BootstrapReference*)this;
    }

   private:
    // The allow_malformed option is only used by the classfile
    // parser's early validation logic.  It suppresses a single
    // assert that is normally done.  If the assert would
    // fail because there is no NameAndType at the address,
    // the data structure is not populated with name and type.
    friend class ConstantPool;
    RawReference(bool allow_malformed,
                 ConstantPool* cp, u2 cp_index) {
      constantTag tag = cp->tag_at(cp_index);
      assert_valid_tag(cp, cp_index, has_name_and_type());
      // unpack immediate bits in this CP entry
      jint ref_bits = *cp->int_at_addr(cp_index);
      int nt_index    = extract_high_short_from_int(ref_bits);
      int third_index = extract_low_short_from_int(ref_bits);

      // given nt_index, we can now initialize the NameAndType parts:
      *(NTReference*)this = cp->possibly_malformed_NTReference_at(nt_index, allow_malformed);
      // allow_malformed relaxes one assert on the nested NameAndType,
      // when called from classfile parser, only.

      // fill in the subclass bits
      _third_index = third_index;
      assert(tag.has_name_and_type(), "");  // caller responsibility
      _tag         = tag.value();  // must overwrite earlier tag, do it last
    }
  };
 private:
  // These are private to hide the possibility of malformation from public view.
  NTReference possibly_malformed_NTReference_at(u2 cp_index, bool allow_malformed) {
    return NTReference(allow_malformed, this, cp_index);
  }
  RawReference possibly_malformed_RawReference_at(u2 cp_index) {
    bool allow_malformed = true;
    return RawReference(allow_malformed, this, cp_index);
  }
 public:

  // CONSTANT_Fieldref, CONSTANT_Methodref, CONSTANT_InterfaceMethodref
  class FMReference : public RawReference {
   public:
    int klass_index() const       { return third_index(); }

    Symbol* klass_name(ConstantPool* cp) const {
      assert(is_same_cp(cp), "wrong CP pointer");
      return cp->klass_name_at(klass_index());
    }
    Klass* klass(ConstantPool* cp, TRAPS) const {
      assert(is_same_cp(cp), "wrong CP pointer");
      return cp->klass_at(klass_index(), THREAD);
    }
    Symbol* klass_name(const constantPoolHandle& cp) const {
      return klass_name(cp());
    }
    Klass* klass(const constantPoolHandle& cp, TRAPS) const {
      return klass(cp(), THREAD);
    }

    // You can always create a blank one, containing only zeroes.
    FMReference() { }

    // usage:
    //   FMReference mref(my_cp, my_cp_index);
    //   int ni = mref.name_index();
    //   Symbol* sig = mref.signature(my_cp);
    //   Klass* holder = mref.klass(my_cp, CHECK);
    FMReference(ConstantPool* cp, u2 cp_index) {
      assert_valid_tag(cp, cp_index, is_field_or_method());
      *(RawReference*)this = RawReference(cp, cp_index);
      assert(sizeof(*this) == sizeof(RawReference), "no new fields in this class");
    }
    FMReference(const constantPoolHandle& cp, u2 cp_index)
      : FMReference(cp(), cp_index) { }

    FMReference(ConstantPool* cp, int cp_cache_index, Bytecodes::Code code)
      : FMReference(cp, cp->to_cp_index(cp_cache_index, code)) { }
    FMReference(const constantPoolHandle& cp, int cp_cache_index, Bytecodes::Code code)
      : FMReference(cp(), cp_cache_index, code) { }
  };

  // CONSTANT_MethodHandle is the most complex.  It contains a whole
  // field or method reference (with NameAndType) and adds more bits.
  class MethodHandleReference : public FMReference {
   private:
    u2 _ref_kind;
    u2 _ref_index;

   public:
    int ref_kind() const          { return _ref_kind; }
    int ref_index() const         { return _ref_index; }

    // You can always create a blank one, containing only zeroes.
    MethodHandleReference() { }

    MethodHandleReference(ConstantPool* cp, u2 cp_index) {
      assert_valid_tag(cp, cp_index, is_method_handle_or_error());
      jint bits  = *cp->int_at_addr(cp_index);
      int kind   = extract_low_short_from_int(bits);  // mask out unwanted ref_index bits
      int member = extract_high_short_from_int(bits);  // shift out unwanted ref_kind bits
      *(FMReference*)this = FMReference(cp, member);
      _ref_kind  = kind;
      _ref_index = member;
      _tag       = JVM_CONSTANT_MethodHandle;  // must overwrite earlier tag
    }
    MethodHandleReference(const constantPoolHandle& cp, u2 cp_index)
      : MethodHandleReference(cp(), cp_index) { }
  };

  // CONSTANT_MethodType is very simple.  It has only a signature, not
  // even a name&type.  But for better uniformity we will wrap it up.
  class MethodTypeReference : public NTReference {
   public:
    // You can always create a blank one, containing only zeroes.
    MethodTypeReference() { }

    MethodTypeReference(ConstantPool* cp, u2 cp_index) {
      DEBUG_ONLY(record_cp(cp));
      assert_valid_tag(cp, cp_index, is_method_type_or_error());
      int signature_index = *cp->int_at_addr(cp_index);
      _signature_index = signature_index;
      _tag = JVM_CONSTANT_MethodType;
    }
    MethodTypeReference(const constantPoolHandle& cp, u2 cp_index)
      : MethodTypeReference(cp(), cp_index) { }
  };

  // A single entry from a BootstrapMethods (BSM) attribute.
  // It is an overlaid view of two or more consuctive u2 words within the
  // ConstantPool::bsm_attribute_entries array.
  // To its closest friends, a BSMAttributeEntry goes by the name "bsme".
  class BSMAttributeEntry {
   private:
    u2 _bootstrap_method_index;
    u2 _argument_count;
    // C++ does not have a "Flexible Array Member" feature.
    //u2 _argument_indexes[_argument_count];
    const u2* argument_indexes() const { return (const u2*)(this+1); }

    // These are overlays on top of Array<u2> data.  Do not construct.
    BSMAttributeEntry() { ShouldNotReachHere(); }

    // See also parse_classfile_bootstrap_methods_attribute in the class
    // file parser, which builds this layout.

   public:
    int bootstrap_method_index() const { return _bootstrap_method_index; }
    int argument_count()         const { return _argument_count; }
    int argument_index(int n)    const { assert((uint)n < _argument_count, "oob");
                                         return argument_indexes()[n];
                                       }

    // utility methods for mapping BSM index to the metadata item
    MethodHandleReference bootstrap_method(ConstantPool* cp) const {
      return MethodHandleReference(cp, bootstrap_method_index());
    }
    // and a duplicate for clients holding a CP handle
    MethodHandleReference bootstrap_method(const constantPoolHandle& cp) const {
      return bootstrap_method(cp());
    }

   private:
    // how to locate one of these inside a packed u2 data array:
    friend class ConstantPool;  // uses entry_at_offset
    static BSMAttributeEntry* entry_at_offset(Array<u2>* entries, int offset) {
      assert(0 <= offset && offset+2 <= entries->length(), "oob-1");
      // do not bother to copy u2 data; just overlay the struct within the array
      BSMAttributeEntry* bsme = (BSMAttributeEntry*) entries->adr_at(offset);
      assert(offset+2+bsme->argument_count() <= entries->length(), "oob-2");
      return bsme;
    }
  };

  BSMAttributeEntry* bsm_attribute_entry(int bsm_attribute_index) {
    int offset = bsm_attribute_offsets()->at(bsm_attribute_index);
    return BSMAttributeEntry::entry_at_offset(bsm_attribute_entries(), offset);
  }
  int bsm_attribute_count() const {
    if (bsm_attribute_offsets() == nullptr)  return 0;  // just in case
    return bsm_attribute_offsets()->length();
  }

  // Compare BSM attribute entries between two CPs
  bool compare_bsme_to(int bsme_index1, const constantPoolHandle& cp2,
                       int bsme_index2);
  // Find a matching BSM attribute entries in another CP
  int find_matching_bsme(int bsme_index, const constantPoolHandle& search_cp,
                         int search_len);
  // Resize the BSM data arrays with delta_len and delta_size
  void resize_bsm_data(int delta_len, int delta_size, TRAPS);
  // Extend the BSM data arrays with the length and size of the BSM data in ext_cp
  void extend_bsm_data(const constantPoolHandle& ext_cp, TRAPS);
  // Shrink the BSM data arrays to a smaller array with new_len length
  void shrink_bsm_data(int new_len, TRAPS);

  // CONSTANT_InvokeDynamic, CONSTANT_Dynamic
  class BootstrapReference : public RawReference {
   public:
    int bsme_index() const        { return third_index(); }

    BSMAttributeEntry* bsme(ConstantPool* cp) const {
      assert(is_same_cp(cp), "wrong CP pointer");
      return cp->bsm_attribute_entry(bsme_index());
    }
    // and a duplicate for clients holding a CP handle
    BSMAttributeEntry* bsme(const constantPoolHandle& cp) const {
      return bsme(cp());
    }

    // You can always create a blank one, containing only zeroes.
    BootstrapReference() { }

    // usage:
    //   BootstrapReference bsref(my_cp, my_cp_index);
    //   int ni = bsref.name_index();
    //   Symbol* sig = bsref.signature(my_cp);
    //   BSMAttributeEntry* bsme = bsref.bsme(my_cp);
    BootstrapReference(ConstantPool* cp, u2 cp_index) {
      assert_valid_tag(cp, cp_index, has_bootstrap());
      *(RawReference*)this = RawReference(cp, cp_index);
      assert(sizeof(*this) == sizeof(RawReference), "no new fields in this class");
    }
    BootstrapReference(const constantPoolHandle& cp, u2 cp_index)
      : BootstrapReference(cp(), cp_index) { }

    BootstrapReference(ConstantPool* cp, int cp_cache_index, Bytecodes::Code code)
      : BootstrapReference(cp, cp->to_cp_index(cp_cache_index, code)) { }
    BootstrapReference(const constantPoolHandle& cp, int cp_cache_index, Bytecodes::Code code)
      : BootstrapReference(cp(), cp_cache_index, code) { }
  };

  // Sharing
  int pre_resolve_shared_klasses(TRAPS);

  // Debugging
  const char* printable_name_at(int cp_index) PRODUCT_RETURN_NULL;

 private:

  void set_resolved_references(OopHandle s) { _cache->set_resolved_references(s); }
  Array<u2>* reference_map() const        {  return (_cache == nullptr) ? nullptr :  _cache->reference_map(); }
  void set_reference_map(Array<u2>* o)    { _cache->set_reference_map(o); }

  // Used while constructing constant pool (only by ClassFileParser)
  jint klass_index_at(int cp_index) {
    assert_valid_tag(this, cp_index, is_klass_index());
    return *int_at_addr(cp_index);
  }

  jint string_index_at(int cp_index) {
    assert_valid_tag(this, cp_index, is_string_index());
    return *int_at_addr(cp_index);
  }

  // Performs the LinkResolver checks
  static void verify_constant_pool_resolve(const constantPoolHandle& this_cp, Klass* klass, TRAPS);

  // Implementation of methods that needs an exposed 'this' pointer, in order to
  // handle GC while executing the method
  static Klass* klass_at_impl(const constantPoolHandle& this_cp, int cp_index, TRAPS);
  static oop string_at_impl(const constantPoolHandle& this_cp, int cp_index, int obj_index, TRAPS);

  static void trace_class_resolution(const constantPoolHandle& this_cp, Klass* k);

  // Resolve string constants (to prevent allocation during compilation)
  static void resolve_string_constants_impl(const constantPoolHandle& this_cp, TRAPS);

  static oop resolve_constant_at_impl(const constantPoolHandle& this_cp, int cp_index, int cache_index,
                                      bool* status_return, TRAPS);
  static void copy_bootstrap_arguments_at_impl(const constantPoolHandle& this_cp,
                                               int bsme_index,
                                               int start_arg, int end_arg,
                                               objArrayHandle info, int pos,
                                               bool must_resolve, Handle if_not_available, TRAPS);

  // Exception handling
  static void save_and_throw_exception(const constantPoolHandle& this_cp, int cp_index, constantTag tag, TRAPS);

 public:
  // Exception handling
  static void throw_resolution_error(const constantPoolHandle& this_cp, int which, TRAPS);

  // Merging ConstantPool* support:
  bool compare_entry_to(int index1, const constantPoolHandle& cp2, int index2);
  void copy_cp_to(int start_cpi, int end_cpi, const constantPoolHandle& to_cp, int to_cpi, TRAPS) {
    constantPoolHandle h_this(THREAD, this);
    copy_cp_to_impl(h_this, start_cpi, end_cpi, to_cp, to_cpi, THREAD);
  }
  static void copy_cp_to_impl(const constantPoolHandle& from_cp, int start_cpi, int end_cpi, const constantPoolHandle& to_cp, int to_cpi, TRAPS);
  static void copy_entry_to(const constantPoolHandle& from_cp, int from_cpi, const constantPoolHandle& to_cp, int to_cpi);
  static void copy_bsm_data(const constantPoolHandle& from_cp, const constantPoolHandle& to_cp, TRAPS);
  int  find_matching_entry(int pattern_i, const constantPoolHandle& search_cp);
  int  version() const                    { return _saved._version; }
  void set_version(int version)           { _saved._version = version; }
  void increment_and_save_version(int version) {
    _saved._version = version >= 0 ? (version + 1) : version;  // keep overflow
  }

  void set_resolved_reference_length(int length) { _saved._resolved_reference_length = length; }
  int  resolved_reference_length() const  { return _saved._resolved_reference_length; }

  // Decrease ref counts of symbols that are in the constant pool
  // when the holder class is unloaded
  void unreference_symbols();

  // Deallocate constant pool for RedefineClasses
  void deallocate_contents(ClassLoaderData* loader_data);
  void release_C_heap_structures();

  // JVMTI access - GetConstantPool, RetransformClasses, ...
  friend class JvmtiConstantPoolReconstituter;

 private:
  class SymbolHash: public CHeapObj<mtSymbol> {
    ResourceHashtable<const Symbol*, u2, 256, AnyObj::C_HEAP, mtSymbol, Symbol::compute_hash> _table;

   public:
    void add_if_absent(const Symbol* sym, u2 value) {
      bool created;
      _table.put_if_absent(sym, value, &created);
    }

    u2 symbol_to_value(const Symbol* sym) {
      u2* value = _table.get(sym);
      return (value == nullptr) ? 0 : *value;
    }
  }; // End SymbolHash class

  jint cpool_entry_size(jint idx);
  jint hash_entries_to(SymbolHash *symmap, SymbolHash *classmap);

  // Copy cpool bytes into byte array.
  // Returns:
  //  int > 0, count of the raw cpool bytes that have been copied
  //        0, OutOfMemory error
  //       -1, Internal error
  int  copy_cpool_bytes(int cpool_size,
                        SymbolHash* tbl,
                        unsigned char *bytes);

 public:
  // Verify
  void verify_on(outputStream* st);

  // Printing
  void print_on(outputStream* st) const;
  void print_value_on(outputStream* st) const;
  void print_entry_on(int index, outputStream* st);

  const char* internal_name() const { return "{constant pool}"; }

  // ResolvedFieldEntry getters
  inline ResolvedFieldEntry* resolved_field_entry_at(int field_index);
  inline int resolved_field_entries_length() const;

  // ResolvedMethodEntry getters
  inline ResolvedMethodEntry* resolved_method_entry_at(int method_index);
  inline int resolved_method_entries_length() const;
  inline oop appendix_if_resolved(int method_index) const;

  // ResolvedIndyEntry getters
  inline ResolvedIndyEntry* resolved_indy_entry_at(int index);
  inline int resolved_indy_entries_length() const;
  inline oop resolved_reference_from_indy(int index) const;
  inline oop resolved_reference_from_method(int index) const;

  #undef assert_valid_tag
};

// FIXME: Do we want these?
using NTReference = ConstantPool::NTReference;
using RawReference = ConstantPool::RawReference;
using FMReference = ConstantPool::FMReference;
using BootstrapReference = ConstantPool::BootstrapReference;
using MethodHandleReference = ConstantPool::MethodHandleReference;
using MethodTypeReference = ConstantPool::MethodTypeReference;
using BSMAttributeEntry = ConstantPool::BSMAttributeEntry;

#endif // SHARE_OOPS_CONSTANTPOOL_HPP

/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

// A constantPool is an array containing class constants as described in the
// class file.
//
// Most of the constant pool entries are written during class parsing, which
// is safe.  For klass and string types, the constant pool entry is
// modified when the entry is resolved.  If a klass or string constant pool
// entry is read without a lock, only the resolved state guarantees that
// the entry in the constant pool is a klass or String object and
// not a symbolOop.

class SymbolHashMap;

class constantPoolOopDesc : public oopDesc {
  friend class VMStructs;
  friend class BytecodeInterpreter;  // Directly extracts an oop in the pool for fast instanceof/checkcast
 private:
  typeArrayOop         _tags; // the tag array describing the constant pool's contents
  constantPoolCacheOop _cache;         // the cache holding interpreter runtime information
  klassOop             _pool_holder;   // the corresponding class
  int                  _flags;         // a few header bits to describe contents for GC
  int                  _length; // number of elements in the array
  volatile bool        _is_conc_safe; // if true, safe for concurrent
                                      // GC processing
  // only set to non-zero if constant pool is merged by RedefineClasses
  int                  _orig_length;

  void set_tags(typeArrayOop tags)             { oop_store_without_check((oop*)&_tags, tags); }
  void tag_at_put(int which, jbyte t)          { tags()->byte_at_put(which, t); }
  void release_tag_at_put(int which, jbyte t)  { tags()->release_byte_at_put(which, t); }

  enum FlagBit {
    FB_has_invokedynamic = 1,
    FB_has_pseudo_string = 2
  };

  int flags() const                         { return _flags; }
  void set_flags(int f)                     { _flags = f; }
  bool flag_at(FlagBit fb) const            { return (_flags & (1 << (int)fb)) != 0; }
  void set_flag_at(FlagBit fb);
  // no clear_flag_at function; they only increase

 private:
  intptr_t* base() const { return (intptr_t*) (((char*) this) + sizeof(constantPoolOopDesc)); }
  oop* tags_addr()       { return (oop*)&_tags; }
  oop* cache_addr()      { return (oop*)&_cache; }

  oop* obj_at_addr(int which) const {
    assert(is_within_bounds(which), "index out of bounds");
    return (oop*) &base()[which];
  }

  jint* int_at_addr(int which) const {
    assert(is_within_bounds(which), "index out of bounds");
    return (jint*) &base()[which];
  }

  jlong* long_at_addr(int which) const {
    assert(is_within_bounds(which), "index out of bounds");
    return (jlong*) &base()[which];
  }

  jfloat* float_at_addr(int which) const {
    assert(is_within_bounds(which), "index out of bounds");
    return (jfloat*) &base()[which];
  }

  jdouble* double_at_addr(int which) const {
    assert(is_within_bounds(which), "index out of bounds");
    return (jdouble*) &base()[which];
  }

 public:
  typeArrayOop tags() const                 { return _tags; }

  bool has_pseudo_string() const            { return flag_at(FB_has_pseudo_string); }
  bool has_invokedynamic() const            { return flag_at(FB_has_invokedynamic); }
  void set_pseudo_string()                  {    set_flag_at(FB_has_pseudo_string); }
  void set_invokedynamic()                  {    set_flag_at(FB_has_invokedynamic); }

  // Klass holding pool
  klassOop pool_holder() const              { return _pool_holder; }
  void set_pool_holder(klassOop k)          { oop_store_without_check((oop*)&_pool_holder, (oop) k); }
  oop* pool_holder_addr()                   { return (oop*)&_pool_holder; }

  // Interpreter runtime support
  constantPoolCacheOop cache() const        { return _cache; }
  void set_cache(constantPoolCacheOop cache){ oop_store((oop*)&_cache, cache); }

  // Assembly code support
  static int tags_offset_in_bytes()         { return offset_of(constantPoolOopDesc, _tags); }
  static int cache_offset_in_bytes()        { return offset_of(constantPoolOopDesc, _cache); }
  static int pool_holder_offset_in_bytes()  { return offset_of(constantPoolOopDesc, _pool_holder); }

  // Storing constants

  void klass_at_put(int which, klassOop k) {
    oop_store_without_check((volatile oop *)obj_at_addr(which), oop(k));
    // The interpreter assumes when the tag is stored, the klass is resolved
    // and the klassOop is a klass rather than a symbolOop, so we need
    // hardware store ordering here.
    release_tag_at_put(which, JVM_CONSTANT_Class);
    if (UseConcMarkSweepGC) {
      // In case the earlier card-mark was consumed by a concurrent
      // marking thread before the tag was updated, redirty the card.
      oop_store_without_check((volatile oop *)obj_at_addr(which), oop(k));
    }
  }

  // For temporary use while constructing constant pool
  void klass_index_at_put(int which, int name_index) {
    tag_at_put(which, JVM_CONSTANT_ClassIndex);
    *int_at_addr(which) = name_index;
  }

  // Temporary until actual use
  void unresolved_klass_at_put(int which, symbolOop s) {
    // Overwrite the old index with a GC friendly value so
    // that if GC looks during the transition it won't try
    // to treat a small integer as oop.
    *obj_at_addr(which) = NULL;
    release_tag_at_put(which, JVM_CONSTANT_UnresolvedClass);
    oop_store_without_check(obj_at_addr(which), oop(s));
  }

  // Temporary until actual use
  void unresolved_string_at_put(int which, symbolOop s) {
    *obj_at_addr(which) = NULL;
    release_tag_at_put(which, JVM_CONSTANT_UnresolvedString);
    oop_store_without_check(obj_at_addr(which), oop(s));
  }

  void int_at_put(int which, jint i) {
    tag_at_put(which, JVM_CONSTANT_Integer);
    *int_at_addr(which) = i;
  }

  void long_at_put(int which, jlong l) {
    tag_at_put(which, JVM_CONSTANT_Long);
    // *long_at_addr(which) = l;
    Bytes::put_native_u8((address)long_at_addr(which), *((u8*) &l));
  }

  void float_at_put(int which, jfloat f) {
    tag_at_put(which, JVM_CONSTANT_Float);
    *float_at_addr(which) = f;
  }

  void double_at_put(int which, jdouble d) {
    tag_at_put(which, JVM_CONSTANT_Double);
    // *double_at_addr(which) = d;
    // u8 temp = *(u8*) &d;
    Bytes::put_native_u8((address) double_at_addr(which), *((u8*) &d));
  }

  void symbol_at_put(int which, symbolOop s) {
    tag_at_put(which, JVM_CONSTANT_Utf8);
    oop_store_without_check(obj_at_addr(which), oop(s));
  }

  void string_at_put(int which, oop str) {
    oop_store((volatile oop*)obj_at_addr(which), str);
    release_tag_at_put(which, JVM_CONSTANT_String);
    if (UseConcMarkSweepGC) {
      // In case the earlier card-mark was consumed by a concurrent
      // marking thread before the tag was updated, redirty the card.
      oop_store_without_check((volatile oop *)obj_at_addr(which), str);
    }
  }

  // For temporary use while constructing constant pool
  void string_index_at_put(int which, int string_index) {
    tag_at_put(which, JVM_CONSTANT_StringIndex);
    *int_at_addr(which) = string_index;
  }

  void field_at_put(int which, int class_index, int name_and_type_index) {
    tag_at_put(which, JVM_CONSTANT_Fieldref);
    *int_at_addr(which) = ((jint) name_and_type_index<<16) | class_index;
  }

  void method_at_put(int which, int class_index, int name_and_type_index) {
    tag_at_put(which, JVM_CONSTANT_Methodref);
    *int_at_addr(which) = ((jint) name_and_type_index<<16) | class_index;
  }

  void interface_method_at_put(int which, int class_index, int name_and_type_index) {
    tag_at_put(which, JVM_CONSTANT_InterfaceMethodref);
    *int_at_addr(which) = ((jint) name_and_type_index<<16) | class_index;  // Not so nice
  }

  void name_and_type_at_put(int which, int name_index, int signature_index) {
    tag_at_put(which, JVM_CONSTANT_NameAndType);
    *int_at_addr(which) = ((jint) signature_index<<16) | name_index;  // Not so nice
  }

  // Tag query

  constantTag tag_at(int which) const { return (constantTag)tags()->byte_at_acquire(which); }

  // Whether the entry is a pointer that must be GC'd.
  bool is_pointer_entry(int which) {
    constantTag tag = tag_at(which);
    return tag.is_klass() ||
      tag.is_unresolved_klass() ||
      tag.is_symbol() ||
      tag.is_unresolved_string() ||
      tag.is_string();
  }

  // Fetching constants

  klassOop klass_at(int which, TRAPS) {
    constantPoolHandle h_this(THREAD, this);
    return klass_at_impl(h_this, which, CHECK_NULL);
  }

  symbolOop klass_name_at(int which);  // Returns the name, w/o resolving.

  klassOop resolved_klass_at(int which) {  // Used by Compiler
    guarantee(tag_at(which).is_klass(), "Corrupted constant pool");
    // Must do an acquire here in case another thread resolved the klass
    // behind our back, lest we later load stale values thru the oop.
    return klassOop((oop)OrderAccess::load_ptr_acquire(obj_at_addr(which)));
  }

  // This method should only be used with a cpool lock or during parsing or gc
  symbolOop unresolved_klass_at(int which) {     // Temporary until actual use
    symbolOop s = symbolOop((oop)OrderAccess::load_ptr_acquire(obj_at_addr(which)));
    // check that the klass is still unresolved.
    assert(tag_at(which).is_unresolved_klass(), "Corrupted constant pool");
    return s;
  }

  // RedefineClasses() API support:
  symbolOop klass_at_noresolve(int which) { return klass_name_at(which); }

  jint int_at(int which) {
    assert(tag_at(which).is_int(), "Corrupted constant pool");
    return *int_at_addr(which);
  }

  jlong long_at(int which) {
    assert(tag_at(which).is_long(), "Corrupted constant pool");
    // return *long_at_addr(which);
    u8 tmp = Bytes::get_native_u8((address)&base()[which]);
    return *((jlong*)&tmp);
  }

  jfloat float_at(int which) {
    assert(tag_at(which).is_float(), "Corrupted constant pool");
    return *float_at_addr(which);
  }

  jdouble double_at(int which) {
    assert(tag_at(which).is_double(), "Corrupted constant pool");
    u8 tmp = Bytes::get_native_u8((address)&base()[which]);
    return *((jdouble*)&tmp);
  }

  symbolOop symbol_at(int which) {
    assert(tag_at(which).is_utf8(), "Corrupted constant pool");
    return symbolOop(*obj_at_addr(which));
  }

  oop string_at(int which, TRAPS) {
    constantPoolHandle h_this(THREAD, this);
    return string_at_impl(h_this, which, CHECK_NULL);
  }

  // A "pseudo-string" is an non-string oop that has found is way into
  // a String entry.
  // Under AnonymousClasses this can happen if the user patches a live
  // object into a CONSTANT_String entry of an anonymous class.
  // Method oops internally created for method handles may also
  // use pseudo-strings to link themselves to related metaobjects.

  bool is_pseudo_string_at(int which);

  oop pseudo_string_at(int which) {
    assert(tag_at(which).is_string(), "Corrupted constant pool");
    return *obj_at_addr(which);
  }

  void pseudo_string_at_put(int which, oop x) {
    assert(AnonymousClasses, "");
    set_pseudo_string();        // mark header
    assert(tag_at(which).is_string() || tag_at(which).is_unresolved_string(), "Corrupted constant pool");
    string_at_put(which, x);    // this works just fine
  }

  // only called when we are sure a string entry is already resolved (via an
  // earlier string_at call.
  oop resolved_string_at(int which) {
    assert(tag_at(which).is_string(), "Corrupted constant pool");
    // Must do an acquire here in case another thread resolved the klass
    // behind our back, lest we later load stale values thru the oop.
    return (oop)OrderAccess::load_ptr_acquire(obj_at_addr(which));
  }

  // This method should only be used with a cpool lock or during parsing or gc
  symbolOop unresolved_string_at(int which) {    // Temporary until actual use
    symbolOop s = symbolOop((oop)OrderAccess::load_ptr_acquire(obj_at_addr(which)));
    // check that the string is still unresolved.
    assert(tag_at(which).is_unresolved_string(), "Corrupted constant pool");
    return s;
  }

  // Returns an UTF8 for a CONSTANT_String entry at a given index.
  // UTF8 char* representation was chosen to avoid conversion of
  // java_lang_Strings at resolved entries into symbolOops
  // or vice versa.
  // Caller is responsible for checking for pseudo-strings.
  char* string_at_noresolve(int which);

  jint name_and_type_at(int which) {
    assert(tag_at(which).is_name_and_type(), "Corrupted constant pool");
    return *int_at_addr(which);
  }

  // The following methods (name/signature/klass_ref_at, klass_ref_at_noresolve,
  // name_and_type_ref_index_at) all expect to be passed indices obtained
  // directly from the bytecode, and extracted according to java byte order.
  // If the indices are meant to refer to fields or methods, they are
  // actually potentially byte-swapped, rewritten constant pool cache indices.
  // The routine remap_instruction_operand_from_cache manages the adjustment
  // of these values back to constant pool indices.

  // There are also "uncached" versions which do not adjust the operand index; see below.

  // Lookup for entries consisting of (klass_index, name_and_type index)
  klassOop klass_ref_at(int which, TRAPS);
  symbolOop klass_ref_at_noresolve(int which);
  symbolOop name_ref_at(int which)                { return impl_name_ref_at(which, false); }
  symbolOop signature_ref_at(int which)           { return impl_signature_ref_at(which, false); }

  int klass_ref_index_at(int which)               { return impl_klass_ref_index_at(which, false); }
  int name_and_type_ref_index_at(int which)       { return impl_name_and_type_ref_index_at(which, false); }

  // Lookup for entries consisting of (name_index, signature_index)
  int name_ref_index_at(int which_nt);            // ==  low-order jshort of name_and_type_at(which_nt)
  int signature_ref_index_at(int which_nt);       // == high-order jshort of name_and_type_at(which_nt)

  BasicType basic_type_for_signature_at(int which);

  // Resolve string constants (to prevent allocation during compilation)
  void resolve_string_constants(TRAPS) {
    constantPoolHandle h_this(THREAD, this);
    resolve_string_constants_impl(h_this, CHECK);
  }

  // Klass name matches name at offset
  bool klass_name_at_matches(instanceKlassHandle k, int which);

  // Sizing
  int length() const                   { return _length; }
  void set_length(int length)          { _length = length; }

  // Tells whether index is within bounds.
  bool is_within_bounds(int index) const {
    return 0 <= index && index < length();
  }

  static int header_size()             { return sizeof(constantPoolOopDesc)/HeapWordSize; }
  static int object_size(int length)   { return align_object_size(header_size() + length); }
  int object_size()                    { return object_size(length()); }

  bool is_conc_safe()                  { return _is_conc_safe; }
  void set_is_conc_safe(bool v)        { _is_conc_safe = v; }

  friend class constantPoolKlass;
  friend class ClassFileParser;
  friend class SystemDictionary;

  // Used by compiler to prevent classloading.
  static klassOop klass_at_if_loaded          (constantPoolHandle this_oop, int which);
  static klassOop klass_ref_at_if_loaded      (constantPoolHandle this_oop, int which);
  // Same as above - but does LinkResolving.
  static klassOop klass_ref_at_if_loaded_check(constantPoolHandle this_oop, int which, TRAPS);

  // Routines currently used for annotations (only called by jvm.cpp) but which might be used in the
  // future by other Java code. These take constant pool indices rather than possibly-byte-swapped
  // constant pool cache indices as do the peer methods above.
  symbolOop uncached_name_ref_at(int which)                 { return impl_name_ref_at(which, true); }
  symbolOop uncached_signature_ref_at(int which)            { return impl_signature_ref_at(which, true); }
  int       uncached_klass_ref_index_at(int which)          { return impl_klass_ref_index_at(which, true); }
  int       uncached_name_and_type_ref_index_at(int which)  { return impl_name_and_type_ref_index_at(which, true); }

  // Sharing
  int pre_resolve_shared_klasses(TRAPS);
  void shared_symbols_iterate(OopClosure* closure0);
  void shared_tags_iterate(OopClosure* closure0);
  void shared_strings_iterate(OopClosure* closure0);

  // Debugging
  const char* printable_name_at(int which) PRODUCT_RETURN0;

 private:

  symbolOop impl_name_ref_at(int which, bool uncached);
  symbolOop impl_signature_ref_at(int which, bool uncached);
  int       impl_klass_ref_index_at(int which, bool uncached);
  int       impl_name_and_type_ref_index_at(int which, bool uncached);

  int remap_instruction_operand_from_cache(int operand);

  // Used while constructing constant pool (only by ClassFileParser)
  jint klass_index_at(int which) {
    assert(tag_at(which).is_klass_index(), "Corrupted constant pool");
    return *int_at_addr(which);
  }

  jint string_index_at(int which) {
    assert(tag_at(which).is_string_index(), "Corrupted constant pool");
    return *int_at_addr(which);
  }

  // Performs the LinkResolver checks
  static void verify_constant_pool_resolve(constantPoolHandle this_oop, KlassHandle klass, TRAPS);

  // Implementation of methods that needs an exposed 'this' pointer, in order to
  // handle GC while executing the method
  static klassOop klass_at_impl(constantPoolHandle this_oop, int which, TRAPS);
  static oop string_at_impl(constantPoolHandle this_oop, int which, TRAPS);

  // Resolve string constants (to prevent allocation during compilation)
  static void resolve_string_constants_impl(constantPoolHandle this_oop, TRAPS);

 public:
  // Merging constantPoolOop support:
  bool compare_entry_to(int index1, constantPoolHandle cp2, int index2, TRAPS);
  void copy_cp_to(int start_i, int end_i, constantPoolHandle to_cp, int to_i,
    TRAPS);
  void copy_entry_to(int from_i, constantPoolHandle to_cp, int to_i, TRAPS);
  int  find_matching_entry(int pattern_i, constantPoolHandle search_cp, TRAPS);
  int  orig_length() const                { return _orig_length; }
  void set_orig_length(int orig_length)   { _orig_length = orig_length; }


  // JVMTI accesss - GetConstantPool, RetransformClasses, ...
  friend class JvmtiConstantPoolReconstituter;

 private:
  jint cpool_entry_size(jint idx);
  jint hash_entries_to(SymbolHashMap *symmap, SymbolHashMap *classmap);

  // Copy cpool bytes into byte array.
  // Returns:
  //  int > 0, count of the raw cpool bytes that have been copied
  //        0, OutOfMemory error
  //       -1, Internal error
  int  copy_cpool_bytes(int cpool_size,
                        SymbolHashMap* tbl,
                        unsigned char *bytes);
};

class SymbolHashMapEntry : public CHeapObj {
 private:
  unsigned int        _hash;   // 32-bit hash for item
  SymbolHashMapEntry* _next;   // Next element in the linked list for this bucket
  symbolOop           _symbol; // 1-st part of the mapping: symbol => value
  u2                  _value;  // 2-nd part of the mapping: symbol => value

 public:
  unsigned   int hash() const             { return _hash;   }
  void       set_hash(unsigned int hash)  { _hash = hash;   }

  SymbolHashMapEntry* next() const        { return _next;   }
  void set_next(SymbolHashMapEntry* next) { _next = next;   }

  symbolOop  symbol() const               { return _symbol; }
  void       set_symbol(symbolOop sym)    { _symbol = sym;  }

  u2         value() const                {  return _value; }
  void       set_value(u2 value)          { _value = value; }

  SymbolHashMapEntry(unsigned int hash, symbolOop symbol, u2 value)
    : _hash(hash), _symbol(symbol), _value(value), _next(NULL) {}

}; // End SymbolHashMapEntry class


class SymbolHashMapBucket : public CHeapObj {

private:
  SymbolHashMapEntry*    _entry;

public:
  SymbolHashMapEntry* entry() const         {  return _entry; }
  void set_entry(SymbolHashMapEntry* entry) { _entry = entry; }
  void clear()                              { _entry = NULL;  }

}; // End SymbolHashMapBucket class


class SymbolHashMap: public CHeapObj {

 private:
  // Default number of entries in the table
  enum SymbolHashMap_Constants {
    _Def_HashMap_Size = 256
  };

  int                   _table_size;
  SymbolHashMapBucket*  _buckets;

  void initialize_table(int table_size) {
    _table_size = table_size;
    _buckets = NEW_C_HEAP_ARRAY(SymbolHashMapBucket, table_size);
    for (int index = 0; index < table_size; index++) {
      _buckets[index].clear();
    }
  }

 public:

  int table_size() const        { return _table_size; }

  SymbolHashMap()               { initialize_table(_Def_HashMap_Size); }
  SymbolHashMap(int table_size) { initialize_table(table_size); }

  // hash P(31) from Kernighan & Ritchie
  static unsigned int compute_hash(const char* str, int len) {
    unsigned int hash = 0;
    while (len-- > 0) {
      hash = 31*hash + (unsigned) *str;
      str++;
    }
    return hash;
  }

  SymbolHashMapEntry* bucket(int i) {
    return _buckets[i].entry();
  }

  void add_entry(symbolOop sym, u2 value);
  SymbolHashMapEntry* find_entry(symbolOop sym);

  u2 symbol_to_value(symbolOop sym) {
    SymbolHashMapEntry *entry = find_entry(sym);
    return (entry == NULL) ? 0 : entry->value();
  }

  ~SymbolHashMap() {
    SymbolHashMapEntry* next;
    for (int i = 0; i < _table_size; i++) {
      for (SymbolHashMapEntry* cur = bucket(i); cur != NULL; cur = next) {
        next = cur->next();
        delete(cur);
      }
    }
    delete _buckets;
  }
}; // End SymbolHashMap class

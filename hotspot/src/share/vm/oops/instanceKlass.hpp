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

// An instanceKlass is the VM level representation of a Java class.
// It contains all information needed for at class at execution runtime.

//  instanceKlass layout:
//    [header                     ] klassOop
//    [klass pointer              ] klassOop
//    [C++ vtbl pointer           ] Klass
//    [subtype cache              ] Klass
//    [instance size              ] Klass
//    [java mirror                ] Klass
//    [super                      ] Klass
//    [access_flags               ] Klass
//    [name                       ] Klass
//    [first subklass             ] Klass
//    [next sibling               ] Klass
//    [array klasses              ]
//    [methods                    ]
//    [local interfaces           ]
//    [transitive interfaces      ]
//    [number of implementors     ]
//    [implementors               ] klassOop[2]
//    [fields                     ]
//    [constants                  ]
//    [class loader               ]
//    [protection domain          ]
//    [signers                    ]
//    [source file name           ]
//    [inner classes              ]
//    [static field size          ]
//    [nonstatic field size       ]
//    [static oop fields size     ]
//    [nonstatic oop maps size    ]
//    [has finalize method        ]
//    [deoptimization mark bit    ]
//    [initialization state       ]
//    [initializing thread        ]
//    [Java vtable length         ]
//    [oop map cache (stack maps) ]
//    [EMBEDDED Java vtable             ] size in words = vtable_len
//    [EMBEDDED static oop fields       ] size in words = static_oop_fields_size
//    [         static non-oop fields   ] size in words = static_field_size - static_oop_fields_size
//    [EMBEDDED nonstatic oop-map blocks] size in words = nonstatic_oop_map_size
//
//    The embedded nonstatic oop-map blocks are short pairs (offset, length) indicating
//    where oops are located in instances of this klass.


// forward declaration for class -- see below for definition
class SuperTypeClosure;
class JNIid;
class jniIdMapBase;
class BreakpointInfo;
class fieldDescriptor;
class DepChange;
class nmethodBucket;
class PreviousVersionNode;
class JvmtiCachedClassFieldMap;

// This is used in iterators below.
class FieldClosure: public StackObj {
public:
  virtual void do_field(fieldDescriptor* fd) = 0;
};

#ifndef PRODUCT
// Print fields.
// If "obj" argument to constructor is NULL, prints static fields, otherwise prints non-static fields.
class FieldPrinter: public FieldClosure {
   oop _obj;
   outputStream* _st;
 public:
   FieldPrinter(outputStream* st, oop obj = NULL) : _obj(obj), _st(st) {}
   void do_field(fieldDescriptor* fd);
};
#endif  // !PRODUCT

// ValueObjs embedded in klass. Describes where oops are located in instances of
// this klass.
class OopMapBlock VALUE_OBJ_CLASS_SPEC {
 public:
  // Byte offset of the first oop mapped by this block.
  int offset() const          { return _offset; }
  void set_offset(int offset) { _offset = offset; }

  // Number of oops in this block.
  uint count() const         { return _count; }
  void set_count(uint count) { _count = count; }

  // sizeof(OopMapBlock) in HeapWords.
  static const int size_in_words() {
    return align_size_up(int(sizeof(OopMapBlock)), HeapWordSize) >>
      LogHeapWordSize;
  }

 private:
  int  _offset;
  uint _count;
};

class instanceKlass: public Klass {
  friend class VMStructs;
 public:
  // See "The Java Virtual Machine Specification" section 2.16.2-5 for a detailed description
  // of the class loading & initialization procedure, and the use of the states.
  enum ClassState {
    unparsable_by_gc = 0,               // object is not yet parsable by gc. Value of _init_state at object allocation.
    allocated,                          // allocated (but not yet linked)
    loaded,                             // loaded and inserted in class hierarchy (but not linked yet)
    linked,                             // successfully linked/verified (but not initialized yet)
    being_initialized,                  // currently running class initializer
    fully_initialized,                  // initialized (successfull final state)
    initialization_error                // error happened during initialization
  };

 public:
  oop* oop_block_beg() const { return adr_array_klasses(); }
  oop* oop_block_end() const { return adr_methods_default_annotations() + 1; }

  enum {
    implementors_limit = 2              // how many implems can we track?
  };

 protected:
  //
  // The oop block.  See comment in klass.hpp before making changes.
  //

  // Array classes holding elements of this class.
  klassOop        _array_klasses;
  // Method array.
  objArrayOop     _methods;
  // Int array containing the original order of method in the class file (for
  // JVMTI).
  typeArrayOop    _method_ordering;
  // Interface (klassOops) this class declares locally to implement.
  objArrayOop     _local_interfaces;
  // Interface (klassOops) this class implements transitively.
  objArrayOop     _transitive_interfaces;
  // Instance and static variable information, 5-tuples of shorts [access, name
  // index, sig index, initval index, offset].
  typeArrayOop    _fields;
  // Constant pool for this class.
  constantPoolOop _constants;
  // Class loader used to load this class, NULL if VM loader used.
  oop             _class_loader;
  // Protection domain.
  oop             _protection_domain;
  // Host class, which grants its access privileges to this class also.
  // This is only non-null for an anonymous class (AnonymousClasses enabled).
  // The host class is either named, or a previously loaded anonymous class.
  klassOop        _host_klass;
  // Class signers.
  objArrayOop     _signers;
  // Name of source file containing this klass, NULL if not specified.
  symbolOop       _source_file_name;
  // the source debug extension for this klass, NULL if not specified.
  symbolOop       _source_debug_extension;
  // inner_classes attribute.
  typeArrayOop    _inner_classes;
  // Implementors of this interface (not valid if it overflows)
  klassOop        _implementors[implementors_limit];
  // Generic signature, or null if none.
  symbolOop       _generic_signature;
  // invokedynamic bootstrap method (a java.dyn.MethodHandle)
  oop             _bootstrap_method;
  // Annotations for this class, or null if none.
  typeArrayOop    _class_annotations;
  // Annotation objects (byte arrays) for fields, or null if no annotations.
  // Indices correspond to entries (not indices) in fields array.
  objArrayOop     _fields_annotations;
  // Annotation objects (byte arrays) for methods, or null if no annotations.
  // Index is the idnum, which is initially the same as the methods array index.
  objArrayOop     _methods_annotations;
  // Annotation objects (byte arrays) for methods' parameters, or null if no
  // such annotations.
  // Index is the idnum, which is initially the same as the methods array index.
  objArrayOop     _methods_parameter_annotations;
  // Annotation objects (byte arrays) for methods' default values, or null if no
  // such annotations.
  // Index is the idnum, which is initially the same as the methods array index.
  objArrayOop     _methods_default_annotations;

  //
  // End of the oop block.
  //

  // Number of heapOopSize words used by non-static fields in this klass
  // (including inherited fields but after header_size()).
  int             _nonstatic_field_size;
  int             _static_field_size;    // number words used by static fields (oop and non-oop) in this klass
  int             _static_oop_field_size;// number of static oop fields in this klass
  int             _nonstatic_oop_map_size;// size in words of nonstatic oop map blocks
  bool            _is_marked_dependent;  // used for marking during flushing and deoptimization
  bool            _rewritten;            // methods rewritten.
  bool            _has_nonstatic_fields; // for sizing with UseCompressedOops
  u2              _minor_version;        // minor version number of class file
  u2              _major_version;        // major version number of class file
  ClassState      _init_state;           // state of class
  Thread*         _init_thread;          // Pointer to current thread doing initialization (to handle recusive initialization)
  int             _vtable_len;           // length of Java vtable (in words)
  int             _itable_len;           // length of Java itable (in words)
  ReferenceType   _reference_type;       // reference type
  OopMapCache*    volatile _oop_map_cache;   // OopMapCache for all methods in the klass (allocated lazily)
  JNIid*          _jni_ids;              // First JNI identifier for static fields in this class
  jmethodID*      _methods_jmethod_ids;  // jmethodIDs corresponding to method_idnum, or NULL if none
  int*            _methods_cached_itable_indices;  // itable_index cache for JNI invoke corresponding to methods idnum, or NULL
  nmethodBucket*  _dependencies;         // list of dependent nmethods
  nmethod*        _osr_nmethods_head;    // Head of list of on-stack replacement nmethods for this class
  BreakpointInfo* _breakpoints;          // bpt lists, managed by methodOop
  int             _nof_implementors;     // No of implementors of this interface (zero if not an interface)
  // Array of interesting part(s) of the previous version(s) of this
  // instanceKlass. See PreviousVersionWalker below.
  GrowableArray<PreviousVersionNode *>* _previous_versions;
  u2              _enclosing_method_class_index;  // Constant pool index for class of enclosing method, or 0 if none
  u2              _enclosing_method_method_index; // Constant pool index for name and type of enclosing method, or 0 if none
  // JVMTI fields can be moved to their own structure - see 6315920
  unsigned char * _cached_class_file_bytes;       // JVMTI: cached class file, before retransformable agent modified it in CFLH
  jint            _cached_class_file_len;         // JVMTI: length of above
  JvmtiCachedClassFieldMap* _jvmti_cached_class_field_map;  // JVMTI: used during heap iteration
  volatile u2     _idnum_allocated_count;         // JNI/JVMTI: increments with the addition of methods, old ids don't change

  // embedded Java vtable follows here
  // embedded Java itables follows here
  // embedded static fields follows here
  // embedded nonstatic oop-map blocks follows here

  friend class instanceKlassKlass;
  friend class SystemDictionary;

 public:
  bool has_nonstatic_fields() const        { return _has_nonstatic_fields; }
  void set_has_nonstatic_fields(bool b)    { _has_nonstatic_fields = b; }

  // field sizes
  int nonstatic_field_size() const         { return _nonstatic_field_size; }
  void set_nonstatic_field_size(int size)  { _nonstatic_field_size = size; }

  int static_field_size() const            { return _static_field_size; }
  void set_static_field_size(int size)     { _static_field_size = size; }

  int static_oop_field_size() const        { return _static_oop_field_size; }
  void set_static_oop_field_size(int size) { _static_oop_field_size = size; }

  // Java vtable
  int  vtable_length() const               { return _vtable_len; }
  void set_vtable_length(int len)          { _vtable_len = len; }

  // Java itable
  int  itable_length() const               { return _itable_len; }
  void set_itable_length(int len)          { _itable_len = len; }

  // array klasses
  klassOop array_klasses() const           { return _array_klasses; }
  void set_array_klasses(klassOop k)       { oop_store_without_check((oop*) &_array_klasses, (oop) k); }

  // methods
  objArrayOop methods() const              { return _methods; }
  void set_methods(objArrayOop a)          { oop_store_without_check((oop*) &_methods, (oop) a); }
  methodOop method_with_idnum(int idnum);

  // method ordering
  typeArrayOop method_ordering() const     { return _method_ordering; }
  void set_method_ordering(typeArrayOop m) { oop_store_without_check((oop*) &_method_ordering, (oop) m); }

  // interfaces
  objArrayOop local_interfaces() const          { return _local_interfaces; }
  void set_local_interfaces(objArrayOop a)      { oop_store_without_check((oop*) &_local_interfaces, (oop) a); }
  objArrayOop transitive_interfaces() const     { return _transitive_interfaces; }
  void set_transitive_interfaces(objArrayOop a) { oop_store_without_check((oop*) &_transitive_interfaces, (oop) a); }

  // fields
  // Field info extracted from the class file and stored
  // as an array of 7 shorts
  enum FieldOffset {
    access_flags_offset    = 0,
    name_index_offset      = 1,
    signature_index_offset = 2,
    initval_index_offset   = 3,
    low_offset             = 4,
    high_offset            = 5,
    generic_signature_offset = 6,
    next_offset            = 7
  };

  typeArrayOop fields() const              { return _fields; }
  int offset_from_fields( int index ) const {
    return build_int_from_shorts( fields()->ushort_at(index + low_offset),
                                  fields()->ushort_at(index + high_offset) );
  }

  void set_fields(typeArrayOop f)          { oop_store_without_check((oop*) &_fields, (oop) f); }

  // inner classes
  typeArrayOop inner_classes() const       { return _inner_classes; }
  void set_inner_classes(typeArrayOop f)   { oop_store_without_check((oop*) &_inner_classes, (oop) f); }

  enum InnerClassAttributeOffset {
    // From http://mirror.eng/products/jdk/1.1/docs/guide/innerclasses/spec/innerclasses.doc10.html#18814
    inner_class_inner_class_info_offset = 0,
    inner_class_outer_class_info_offset = 1,
    inner_class_inner_name_offset = 2,
    inner_class_access_flags_offset = 3,
    inner_class_next_offset = 4
  };

  // method override check
  bool is_override(methodHandle super_method, Handle targetclassloader, symbolHandle targetclassname, TRAPS);

  // package
  bool is_same_class_package(klassOop class2);
  bool is_same_class_package(oop classloader2, symbolOop classname2);
  static bool is_same_class_package(oop class_loader1, symbolOop class_name1, oop class_loader2, symbolOop class_name2);

  // find an enclosing class (defined where original code was, in jvm.cpp!)
  klassOop compute_enclosing_class(symbolOop& simple_name_result, TRAPS) {
    instanceKlassHandle self(THREAD, this->as_klassOop());
    return compute_enclosing_class_impl(self, simple_name_result, THREAD);
  }
  static klassOop compute_enclosing_class_impl(instanceKlassHandle self,
                                               symbolOop& simple_name_result, TRAPS);

  // tell if two classes have the same enclosing class (at package level)
  bool is_same_package_member(klassOop class2, TRAPS) {
    instanceKlassHandle self(THREAD, this->as_klassOop());
    return is_same_package_member_impl(self, class2, THREAD);
  }
  static bool is_same_package_member_impl(instanceKlassHandle self,
                                          klassOop class2, TRAPS);

  // initialization state
  bool is_loaded() const                   { return _init_state >= loaded; }
  bool is_linked() const                   { return _init_state >= linked; }
  bool is_initialized() const              { return _init_state == fully_initialized; }
  bool is_not_initialized() const          { return _init_state <  being_initialized; }
  bool is_being_initialized() const        { return _init_state == being_initialized; }
  bool is_in_error_state() const           { return _init_state == initialization_error; }
  bool is_reentrant_initialization(Thread *thread)  { return thread == _init_thread; }
  int  get_init_state()                    { return _init_state; } // Useful for debugging
  bool is_rewritten() const                { return _rewritten; }

  // marking
  bool is_marked_dependent() const         { return _is_marked_dependent; }
  void set_is_marked_dependent(bool value) { _is_marked_dependent = value; }

  // initialization (virtuals from Klass)
  bool should_be_initialized() const;  // means that initialize should be called
  void initialize(TRAPS);
  void link_class(TRAPS);
  bool link_class_or_fail(TRAPS); // returns false on failure
  void unlink_class();
  void rewrite_class(TRAPS);
  methodOop class_initializer();

  // set the class to initialized if no static initializer is present
  void eager_initialize(Thread *thread);

  // reference type
  ReferenceType reference_type() const     { return _reference_type; }
  void set_reference_type(ReferenceType t) { _reference_type = t; }

  // find local field, returns true if found
  bool find_local_field(symbolOop name, symbolOop sig, fieldDescriptor* fd) const;
  // find field in direct superinterfaces, returns the interface in which the field is defined
  klassOop find_interface_field(symbolOop name, symbolOop sig, fieldDescriptor* fd) const;
  // find field according to JVM spec 5.4.3.2, returns the klass in which the field is defined
  klassOop find_field(symbolOop name, symbolOop sig, fieldDescriptor* fd) const;
  // find instance or static fields according to JVM spec 5.4.3.2, returns the klass in which the field is defined
  klassOop find_field(symbolOop name, symbolOop sig, bool is_static, fieldDescriptor* fd) const;

  // find a non-static or static field given its offset within the class.
  bool contains_field_offset(int offset) {
    return instanceOopDesc::contains_field_offset(offset, nonstatic_field_size());
  }

  bool find_local_field_from_offset(int offset, bool is_static, fieldDescriptor* fd) const;
  bool find_field_from_offset(int offset, bool is_static, fieldDescriptor* fd) const;

  // find a local method (returns NULL if not found)
  methodOop find_method(symbolOop name, symbolOop signature) const;
  static methodOop find_method(objArrayOop methods, symbolOop name, symbolOop signature);

  // lookup operation (returns NULL if not found)
  methodOop uncached_lookup_method(symbolOop name, symbolOop signature) const;

  // lookup a method in all the interfaces that this class implements
  // (returns NULL if not found)
  methodOop lookup_method_in_all_interfaces(symbolOop name, symbolOop signature) const;

  // constant pool
  constantPoolOop constants() const        { return _constants; }
  void set_constants(constantPoolOop c)    { oop_store_without_check((oop*) &_constants, (oop) c); }

  // class loader
  oop class_loader() const                 { return _class_loader; }
  void set_class_loader(oop l)             { oop_store((oop*) &_class_loader, l); }

  // protection domain
  oop protection_domain()                  { return _protection_domain; }
  void set_protection_domain(oop pd)       { oop_store((oop*) &_protection_domain, pd); }

  // host class
  oop host_klass() const                   { return _host_klass; }
  void set_host_klass(oop host)            { oop_store((oop*) &_host_klass, host); }
  bool is_anonymous() const                { return _host_klass != NULL; }

  // signers
  objArrayOop signers() const              { return _signers; }
  void set_signers(objArrayOop s)          { oop_store((oop*) &_signers, oop(s)); }

  // source file name
  symbolOop source_file_name() const       { return _source_file_name; }
  void set_source_file_name(symbolOop n)   { oop_store_without_check((oop*) &_source_file_name, (oop) n); }

  // minor and major version numbers of class file
  u2 minor_version() const                 { return _minor_version; }
  void set_minor_version(u2 minor_version) { _minor_version = minor_version; }
  u2 major_version() const                 { return _major_version; }
  void set_major_version(u2 major_version) { _major_version = major_version; }

  // source debug extension
  symbolOop source_debug_extension() const    { return _source_debug_extension; }
  void set_source_debug_extension(symbolOop n){ oop_store_without_check((oop*) &_source_debug_extension, (oop) n); }

  // nonstatic oop-map blocks
  static int nonstatic_oop_map_size(unsigned int oop_map_count) {
    return oop_map_count * OopMapBlock::size_in_words();
  }
  unsigned int nonstatic_oop_map_count() const {
    return _nonstatic_oop_map_size / OopMapBlock::size_in_words();
  }
  int nonstatic_oop_map_size() const { return _nonstatic_oop_map_size; }
  void set_nonstatic_oop_map_size(int words) {
    _nonstatic_oop_map_size = words;
  }

  // RedefineClasses() support for previous versions:
  void add_previous_version(instanceKlassHandle ikh, BitMap *emcp_methods,
         int emcp_method_count);
  bool has_previous_version() const;
  void init_previous_versions() {
    _previous_versions = NULL;
  }
  GrowableArray<PreviousVersionNode *>* previous_versions() const {
    return _previous_versions;
  }

  // JVMTI: Support for caching a class file before it is modified by an agent that can do retransformation
  void set_cached_class_file(unsigned char *class_file_bytes,
                             jint class_file_len)     { _cached_class_file_len = class_file_len;
                                                        _cached_class_file_bytes = class_file_bytes; }
  jint get_cached_class_file_len()                    { return _cached_class_file_len; }
  unsigned char * get_cached_class_file_bytes()       { return _cached_class_file_bytes; }

  // JVMTI: Support for caching of field indices, types, and offsets
  void set_jvmti_cached_class_field_map(JvmtiCachedClassFieldMap* descriptor) {
    _jvmti_cached_class_field_map = descriptor;
  }
  JvmtiCachedClassFieldMap* jvmti_cached_class_field_map() const {
    return _jvmti_cached_class_field_map;
  }

  // for adding methods, constMethodOopDesc::UNSET_IDNUM means no more ids available
  inline u2 next_method_idnum();
  void set_initial_method_idnum(u2 value)             { _idnum_allocated_count = value; }

  // generics support
  symbolOop generic_signature() const                 { return _generic_signature; }
  void set_generic_signature(symbolOop sig)           { oop_store_without_check((oop*)&_generic_signature, (oop)sig); }
  u2 enclosing_method_class_index() const             { return _enclosing_method_class_index; }
  u2 enclosing_method_method_index() const            { return _enclosing_method_method_index; }
  void set_enclosing_method_indices(u2 class_index,
                                    u2 method_index)  { _enclosing_method_class_index  = class_index;
                                                        _enclosing_method_method_index = method_index; }

  // JSR 292 support
  oop bootstrap_method() const                        { return _bootstrap_method; }
  void set_bootstrap_method(oop mh)                   { oop_store(&_bootstrap_method, mh); }

  // jmethodID support
  static jmethodID get_jmethod_id(instanceKlassHandle ik_h, size_t idnum,
                                  jmethodID new_id, jmethodID* new_jmeths);
  static jmethodID jmethod_id_for_impl(instanceKlassHandle ik_h, methodHandle method_h);
  jmethodID jmethod_id_or_null(methodOop method);

  // cached itable index support
  void set_cached_itable_index(size_t idnum, int index);
  int cached_itable_index(size_t idnum);

  // annotations support
  typeArrayOop class_annotations() const              { return _class_annotations; }
  objArrayOop fields_annotations() const              { return _fields_annotations; }
  objArrayOop methods_annotations() const             { return _methods_annotations; }
  objArrayOop methods_parameter_annotations() const   { return _methods_parameter_annotations; }
  objArrayOop methods_default_annotations() const     { return _methods_default_annotations; }
  void set_class_annotations(typeArrayOop md)            { oop_store_without_check((oop*)&_class_annotations, (oop)md); }
  void set_fields_annotations(objArrayOop md)            { set_annotations(md, &_fields_annotations); }
  void set_methods_annotations(objArrayOop md)           { set_annotations(md, &_methods_annotations); }
  void set_methods_parameter_annotations(objArrayOop md) { set_annotations(md, &_methods_parameter_annotations); }
  void set_methods_default_annotations(objArrayOop md)   { set_annotations(md, &_methods_default_annotations); }
  typeArrayOop get_method_annotations_of(int idnum)
                                                { return get_method_annotations_from(idnum, _methods_annotations); }
  typeArrayOop get_method_parameter_annotations_of(int idnum)
                                                { return get_method_annotations_from(idnum, _methods_parameter_annotations); }
  typeArrayOop get_method_default_annotations_of(int idnum)
                                                { return get_method_annotations_from(idnum, _methods_default_annotations); }
  void set_method_annotations_of(int idnum, typeArrayOop anno)
                                                { set_methods_annotations_of(idnum, anno, &_methods_annotations); }
  void set_method_parameter_annotations_of(int idnum, typeArrayOop anno)
                                                { set_methods_annotations_of(idnum, anno, &_methods_annotations); }
  void set_method_default_annotations_of(int idnum, typeArrayOop anno)
                                                { set_methods_annotations_of(idnum, anno, &_methods_annotations); }

  // allocation
  DEFINE_ALLOCATE_PERMANENT(instanceKlass);
  instanceOop allocate_instance(TRAPS);
  instanceOop allocate_permanent_instance(TRAPS);

  // additional member function to return a handle
  instanceHandle allocate_instance_handle(TRAPS)      { return instanceHandle(THREAD, allocate_instance(THREAD)); }

  objArrayOop allocate_objArray(int n, int length, TRAPS);
  // Helper function
  static instanceOop register_finalizer(instanceOop i, TRAPS);

  // Check whether reflection/jni/jvm code is allowed to instantiate this class;
  // if not, throw either an Error or an Exception.
  virtual void check_valid_for_instantiation(bool throwError, TRAPS);

  // initialization
  void call_class_initializer(TRAPS);
  void set_initialization_state_and_notify(ClassState state, TRAPS);

  // OopMapCache support
  OopMapCache* oop_map_cache()               { return _oop_map_cache; }
  void set_oop_map_cache(OopMapCache *cache) { _oop_map_cache = cache; }
  void mask_for(methodHandle method, int bci, InterpreterOopMap* entry);

  // JNI identifier support (for static fields - for jni performance)
  JNIid* jni_ids()                               { return _jni_ids; }
  void set_jni_ids(JNIid* ids)                   { _jni_ids = ids; }
  JNIid* jni_id_for(int offset);

  // maintenance of deoptimization dependencies
  int mark_dependent_nmethods(DepChange& changes);
  void add_dependent_nmethod(nmethod* nm);
  void remove_dependent_nmethod(nmethod* nm);

  // On-stack replacement support
  nmethod* osr_nmethods_head() const         { return _osr_nmethods_head; };
  void set_osr_nmethods_head(nmethod* h)     { _osr_nmethods_head = h; };
  void add_osr_nmethod(nmethod* n);
  void remove_osr_nmethod(nmethod* n);
  nmethod* lookup_osr_nmethod(const methodOop m, int bci) const;

  // Breakpoint support (see methods on methodOop for details)
  BreakpointInfo* breakpoints() const       { return _breakpoints; };
  void set_breakpoints(BreakpointInfo* bps) { _breakpoints = bps; };

  // support for stub routines
  static int init_state_offset_in_bytes()    { return offset_of(instanceKlass, _init_state); }
  static int init_thread_offset_in_bytes()   { return offset_of(instanceKlass, _init_thread); }

  // subclass/subinterface checks
  bool implements_interface(klassOop k) const;

  // Access to implementors of an interface. We only store the count
  // of implementors, and in case, there are only a few
  // implementors, we store them in a short list.
  // This accessor returns NULL if we walk off the end of the list.
  klassOop implementor(int i) const {
    return (i < implementors_limit)? _implementors[i]: (klassOop) NULL;
  }
  int  nof_implementors() const       { return _nof_implementors; }
  void add_implementor(klassOop k);  // k is a new class that implements this interface
  void init_implementor();           // initialize

  // link this class into the implementors list of every interface it implements
  void process_interfaces(Thread *thread);

  // virtual operations from Klass
  bool is_leaf_class() const               { return _subklass == NULL; }
  objArrayOop compute_secondary_supers(int num_extra_slots, TRAPS);
  bool compute_is_subtype_of(klassOop k);
  bool can_be_primary_super_slow() const;
  klassOop java_super() const              { return super(); }
  int oop_size(oop obj)  const             { return size_helper(); }
  int klass_oop_size() const               { return object_size(); }
  bool oop_is_instance_slow() const        { return true; }

  // Iterators
  void do_local_static_fields(FieldClosure* cl);
  void do_nonstatic_fields(FieldClosure* cl); // including inherited fields
  void do_local_static_fields(void f(fieldDescriptor*, TRAPS), TRAPS);

  void methods_do(void f(methodOop method));
  void array_klasses_do(void f(klassOop k));
  void with_array_klasses_do(void f(klassOop k));
  bool super_types_do(SuperTypeClosure* blk);

  // Casting from klassOop
  static instanceKlass* cast(klassOop k) {
    Klass* kp = k->klass_part();
    assert(kp->null_vtbl() || kp->oop_is_instance_slow(), "cast to instanceKlass");
    return (instanceKlass*) kp;
  }

  // Sizing (in words)
  static int header_size()            { return align_object_offset(oopDesc::header_size() + sizeof(instanceKlass)/HeapWordSize); }
  int object_size() const             { return object_size(align_object_offset(vtable_length()) + align_object_offset(itable_length()) + static_field_size() + nonstatic_oop_map_size()); }
  static int vtable_start_offset()    { return header_size(); }
  static int vtable_length_offset()   { return oopDesc::header_size() + offset_of(instanceKlass, _vtable_len) / HeapWordSize; }
  static int object_size(int extra)   { return align_object_size(header_size() + extra); }

  intptr_t* start_of_vtable() const        { return ((intptr_t*)as_klassOop()) + vtable_start_offset(); }
  intptr_t* start_of_itable() const        { return start_of_vtable() + align_object_offset(vtable_length()); }
  int  itable_offset_in_words() const { return start_of_itable() - (intptr_t*)as_klassOop(); }

  // Static field offset is an offset into the Heap, should be converted by
  // based on UseCompressedOop for traversal
  HeapWord* start_of_static_fields() const {
    return (HeapWord*)(start_of_itable() + align_object_offset(itable_length()));
  }

  intptr_t* end_of_itable() const          { return start_of_itable() + itable_length(); }

  int offset_of_static_fields() const {
    return (intptr_t)start_of_static_fields() - (intptr_t)as_klassOop();
  }

  OopMapBlock* start_of_nonstatic_oop_maps() const {
    return (OopMapBlock*) (start_of_static_fields() + static_field_size());
  }

  // Allocation profiling support
  juint alloc_size() const            { return _alloc_count * size_helper(); }
  void set_alloc_size(juint n)        {}

  // Use this to return the size of an instance in heap words:
  int size_helper() const {
    return layout_helper_to_size_helper(layout_helper());
  }

  // This bit is initialized in classFileParser.cpp.
  // It is false under any of the following conditions:
  //  - the class is abstract (including any interface)
  //  - the class has a finalizer (if !RegisterFinalizersAtInit)
  //  - the class size is larger than FastAllocateSizeLimit
  //  - the class is java/lang/Class, which cannot be allocated directly
  bool can_be_fastpath_allocated() const {
    return !layout_helper_needs_slow_path(layout_helper());
  }

  // Java vtable/itable
  klassVtable* vtable() const;        // return new klassVtable wrapper
  inline methodOop method_at_vtable(int index);
  klassItable* itable() const;        // return new klassItable wrapper
  methodOop method_at_itable(klassOop holder, int index, TRAPS);

  // Garbage collection
  void oop_follow_contents(oop obj);
  void follow_static_fields();
  void adjust_static_fields();
  int  oop_adjust_pointers(oop obj);
  bool object_is_parsable() const { return _init_state != unparsable_by_gc; }
       // Value of _init_state must be zero (unparsable_by_gc) when klass field is set.

  void follow_weak_klass_links(
    BoolObjectClosure* is_alive, OopClosure* keep_alive);
  void release_C_heap_structures();

  // Parallel Scavenge and Parallel Old
  PARALLEL_GC_DECLS

#ifndef SERIALGC
  // Parallel Scavenge
  void copy_static_fields(PSPromotionManager* pm);
  void push_static_fields(PSPromotionManager* pm);

  // Parallel Old
  void follow_static_fields(ParCompactionManager* cm);
  void copy_static_fields(ParCompactionManager* cm);
  void update_static_fields();
  void update_static_fields(HeapWord* beg_addr, HeapWord* end_addr);
#endif // SERIALGC

  // Naming
  char* signature_name() const;

  // Iterators
  int oop_oop_iterate(oop obj, OopClosure* blk) {
    return oop_oop_iterate_v(obj, blk);
  }

  int oop_oop_iterate_m(oop obj, OopClosure* blk, MemRegion mr) {
    return oop_oop_iterate_v_m(obj, blk, mr);
  }

#define InstanceKlass_OOP_OOP_ITERATE_DECL(OopClosureType, nv_suffix)      \
  int  oop_oop_iterate##nv_suffix(oop obj, OopClosureType* blk);           \
  int  oop_oop_iterate##nv_suffix##_m(oop obj, OopClosureType* blk,        \
                                      MemRegion mr);

  ALL_OOP_OOP_ITERATE_CLOSURES_1(InstanceKlass_OOP_OOP_ITERATE_DECL)
  ALL_OOP_OOP_ITERATE_CLOSURES_2(InstanceKlass_OOP_OOP_ITERATE_DECL)

#ifndef SERIALGC
#define InstanceKlass_OOP_OOP_ITERATE_BACKWARDS_DECL(OopClosureType, nv_suffix) \
  int  oop_oop_iterate_backwards##nv_suffix(oop obj, OopClosureType* blk);

  ALL_OOP_OOP_ITERATE_CLOSURES_1(InstanceKlass_OOP_OOP_ITERATE_BACKWARDS_DECL)
  ALL_OOP_OOP_ITERATE_CLOSURES_2(InstanceKlass_OOP_OOP_ITERATE_BACKWARDS_DECL)
#endif // !SERIALGC

  void iterate_static_fields(OopClosure* closure);
  void iterate_static_fields(OopClosure* closure, MemRegion mr);

private:
  // initialization state
#ifdef ASSERT
  void set_init_state(ClassState state);
#else
  void set_init_state(ClassState state) { _init_state = state; }
#endif
  void set_rewritten()                  { _rewritten = true; }
  void set_init_thread(Thread *thread)  { _init_thread = thread; }

  u2 idnum_allocated_count() const      { return _idnum_allocated_count; }
  jmethodID* methods_jmethod_ids_acquire() const
         { return (jmethodID*)OrderAccess::load_ptr_acquire(&_methods_jmethod_ids); }
  void release_set_methods_jmethod_ids(jmethodID* jmeths)
         { OrderAccess::release_store_ptr(&_methods_jmethod_ids, jmeths); }

  int* methods_cached_itable_indices_acquire() const
         { return (int*)OrderAccess::load_ptr_acquire(&_methods_cached_itable_indices); }
  void release_set_methods_cached_itable_indices(int* indices)
         { OrderAccess::release_store_ptr(&_methods_cached_itable_indices, indices); }

  inline typeArrayOop get_method_annotations_from(int idnum, objArrayOop annos);
  void set_annotations(objArrayOop md, objArrayOop* md_p)  { oop_store_without_check((oop*)md_p, (oop)md); }
  void set_methods_annotations_of(int idnum, typeArrayOop anno, objArrayOop* md_p);

  // Offsets for memory management
  oop* adr_array_klasses() const     { return (oop*)&this->_array_klasses;}
  oop* adr_methods() const           { return (oop*)&this->_methods;}
  oop* adr_method_ordering() const   { return (oop*)&this->_method_ordering;}
  oop* adr_local_interfaces() const  { return (oop*)&this->_local_interfaces;}
  oop* adr_transitive_interfaces() const  { return (oop*)&this->_transitive_interfaces;}
  oop* adr_fields() const            { return (oop*)&this->_fields;}
  oop* adr_constants() const         { return (oop*)&this->_constants;}
  oop* adr_class_loader() const      { return (oop*)&this->_class_loader;}
  oop* adr_protection_domain() const { return (oop*)&this->_protection_domain;}
  oop* adr_host_klass() const        { return (oop*)&this->_host_klass;}
  oop* adr_signers() const           { return (oop*)&this->_signers;}
  oop* adr_source_file_name() const  { return (oop*)&this->_source_file_name;}
  oop* adr_source_debug_extension() const { return (oop*)&this->_source_debug_extension;}
  oop* adr_inner_classes() const     { return (oop*)&this->_inner_classes;}
  oop* adr_implementors() const      { return (oop*)&this->_implementors[0];}
  oop* adr_generic_signature() const { return (oop*)&this->_generic_signature;}
  oop* adr_bootstrap_method() const  { return (oop*)&this->_bootstrap_method;}
  oop* adr_methods_jmethod_ids() const             { return (oop*)&this->_methods_jmethod_ids;}
  oop* adr_methods_cached_itable_indices() const   { return (oop*)&this->_methods_cached_itable_indices;}
  oop* adr_class_annotations() const   { return (oop*)&this->_class_annotations;}
  oop* adr_fields_annotations() const  { return (oop*)&this->_fields_annotations;}
  oop* adr_methods_annotations() const { return (oop*)&this->_methods_annotations;}
  oop* adr_methods_parameter_annotations() const { return (oop*)&this->_methods_parameter_annotations;}
  oop* adr_methods_default_annotations() const { return (oop*)&this->_methods_default_annotations;}

  // Static methods that are used to implement member methods where an exposed this pointer
  // is needed due to possible GCs
  static bool link_class_impl                           (instanceKlassHandle this_oop, bool throw_verifyerror, TRAPS);
  static bool verify_code                               (instanceKlassHandle this_oop, bool throw_verifyerror, TRAPS);
  static void initialize_impl                           (instanceKlassHandle this_oop, TRAPS);
  static void eager_initialize_impl                     (instanceKlassHandle this_oop);
  static void set_initialization_state_and_notify_impl  (instanceKlassHandle this_oop, ClassState state, TRAPS);
  static void call_class_initializer_impl               (instanceKlassHandle this_oop, TRAPS);
  static klassOop array_klass_impl                      (instanceKlassHandle this_oop, bool or_null, int n, TRAPS);
  static void do_local_static_fields_impl               (instanceKlassHandle this_oop, void f(fieldDescriptor* fd, TRAPS), TRAPS);
  /* jni_id_for_impl for jfieldID only */
  static JNIid* jni_id_for_impl                         (instanceKlassHandle this_oop, int offset);

  // Returns the array class for the n'th dimension
  klassOop array_klass_impl(bool or_null, int n, TRAPS);

  // Returns the array class with this class as element type
  klassOop array_klass_impl(bool or_null, TRAPS);

public:
  // sharing support
  virtual void remove_unshareable_info();
  void field_names_and_sigs_iterate(OopClosure* closure);

  // jvm support
  jint compute_modifier_flags(TRAPS) const;

public:
  // JVMTI support
  jint jvmti_class_status() const;

#ifndef PRODUCT
 public:
  // Printing
  void oop_print_on      (oop obj, outputStream* st);
  void oop_print_value_on(oop obj, outputStream* st);

  void print_dependent_nmethods(bool verbose = false);
  bool is_dependent_nmethod(nmethod* nm);
#endif

 public:
  // Verification
  const char* internal_name() const;
  void oop_verify_on(oop obj, outputStream* st);

#ifndef PRODUCT
  static void verify_class_klass_nonstatic_oop_maps(klassOop k) PRODUCT_RETURN;
#endif
};

inline methodOop instanceKlass::method_at_vtable(int index)  {
#ifndef PRODUCT
  assert(index >= 0, "valid vtable index");
  if (DebugVtables) {
    verify_vtable_index(index);
  }
#endif
  vtableEntry* ve = (vtableEntry*)start_of_vtable();
  return ve[index].method();
}

inline typeArrayOop instanceKlass::get_method_annotations_from(int idnum, objArrayOop annos) {
  if (annos == NULL || annos->length() <= idnum) {
    return NULL;
  }
  return typeArrayOop(annos->obj_at(idnum));
}

// for adding methods
// UNSET_IDNUM return means no more ids available
inline u2 instanceKlass::next_method_idnum() {
  if (_idnum_allocated_count == constMethodOopDesc::MAX_IDNUM) {
    return constMethodOopDesc::UNSET_IDNUM; // no more ids available
  } else {
    return _idnum_allocated_count++;
  }
}


/* JNIid class for jfieldIDs only */
class JNIid: public CHeapObj {
  friend class VMStructs;
 private:
  klassOop           _holder;
  JNIid*             _next;
  int                _offset;
#ifdef ASSERT
  bool               _is_static_field_id;
#endif

 public:
  // Accessors
  klassOop holder() const         { return _holder; }
  int offset() const              { return _offset; }
  JNIid* next()                   { return _next; }
  // Constructor
  JNIid(klassOop holder, int offset, JNIid* next);
  // Identifier lookup
  JNIid* find(int offset);

  // Garbage collection support
  oop* holder_addr() { return (oop*)&_holder; }
  void oops_do(OopClosure* f);
  static void deallocate(JNIid* id);
  // Debugging
#ifdef ASSERT
  bool is_static_field_id() const { return _is_static_field_id; }
  void set_is_static_field_id()   { _is_static_field_id = true; }
#endif
  void verify(klassOop holder);
};


// If breakpoints are more numerous than just JVMTI breakpoints,
// consider compressing this data structure.
// It is currently a simple linked list defined in methodOop.hpp.

class BreakpointInfo;


// A collection point for interesting information about the previous
// version(s) of an instanceKlass. This class uses weak references to
// the information so that the information may be collected as needed
// by the system. If the information is shared, then a regular
// reference must be used because a weak reference would be seen as
// collectible. A GrowableArray of PreviousVersionNodes is attached
// to the instanceKlass as needed. See PreviousVersionWalker below.
class PreviousVersionNode : public CHeapObj {
 private:
  // A shared ConstantPool is never collected so we'll always have
  // a reference to it so we can update items in the cache. We'll
  // have a weak reference to a non-shared ConstantPool until all
  // of the methods (EMCP or obsolete) have been collected; the
  // non-shared ConstantPool becomes collectible at that point.
  jobject _prev_constant_pool;  // regular or weak reference
  bool    _prev_cp_is_weak;     // true if not a shared ConstantPool

  // If the previous version of the instanceKlass doesn't have any
  // EMCP methods, then _prev_EMCP_methods will be NULL. If all the
  // EMCP methods have been collected, then _prev_EMCP_methods can
  // have a length of zero.
  GrowableArray<jweak>* _prev_EMCP_methods;

public:
  PreviousVersionNode(jobject prev_constant_pool, bool prev_cp_is_weak,
    GrowableArray<jweak>* prev_EMCP_methods);
  ~PreviousVersionNode();
  jobject prev_constant_pool() const {
    return _prev_constant_pool;
  }
  GrowableArray<jweak>* prev_EMCP_methods() const {
    return _prev_EMCP_methods;
  }
};


// A Handle-ized version of PreviousVersionNode.
class PreviousVersionInfo : public ResourceObj {
 private:
  constantPoolHandle   _prev_constant_pool_handle;
  // If the previous version of the instanceKlass doesn't have any
  // EMCP methods, then _prev_EMCP_methods will be NULL. Since the
  // methods cannot be collected while we hold a handle,
  // _prev_EMCP_methods should never have a length of zero.
  GrowableArray<methodHandle>* _prev_EMCP_method_handles;

public:
  PreviousVersionInfo(PreviousVersionNode *pv_node);
  ~PreviousVersionInfo();
  constantPoolHandle prev_constant_pool_handle() const {
    return _prev_constant_pool_handle;
  }
  GrowableArray<methodHandle>* prev_EMCP_method_handles() const {
    return _prev_EMCP_method_handles;
  }
};


// Helper object for walking previous versions. This helper cleans up
// the Handles that it allocates when the helper object is destroyed.
// The PreviousVersionInfo object returned by next_previous_version()
// is only valid until a subsequent call to next_previous_version() or
// the helper object is destroyed.
class PreviousVersionWalker : public StackObj {
 private:
  GrowableArray<PreviousVersionNode *>* _previous_versions;
  int                                   _current_index;
  // Fields for cleaning up when we are done walking the previous versions:
  // A HandleMark for the PreviousVersionInfo handles:
  HandleMark                            _hm;

  // It would be nice to have a ResourceMark field in this helper also,
  // but the ResourceMark code says to be careful to delete handles held
  // in GrowableArrays _before_ deleting the GrowableArray. Since we
  // can't guarantee the order in which the fields are destroyed, we
  // have to let the creator of the PreviousVersionWalker object do
  // the right thing. Also, adding a ResourceMark here causes an
  // include loop.

  // A pointer to the current info object so we can handle the deletes.
  PreviousVersionInfo *                 _current_p;

 public:
  PreviousVersionWalker(instanceKlass *ik);
  ~PreviousVersionWalker();

  // Return the interesting information for the next previous version
  // of the klass. Returns NULL if there are no more previous versions.
  PreviousVersionInfo* next_previous_version();
};

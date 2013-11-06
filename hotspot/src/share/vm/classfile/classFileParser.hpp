/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CLASSFILE_CLASSFILEPARSER_HPP
#define SHARE_VM_CLASSFILE_CLASSFILEPARSER_HPP

#include "classfile/classFileStream.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "oops/typeArrayOop.hpp"
#include "runtime/handles.inline.hpp"
#include "utilities/accessFlags.hpp"
#include "classfile/symbolTable.hpp"

class FieldAllocationCount;
class FieldLayoutInfo;


// Parser for for .class files
//
// The bytes describing the class file structure is read from a Stream object

class ClassFileParser VALUE_OBJ_CLASS_SPEC {
 private:
  bool _need_verify;
  bool _relax_verify;
  u2   _major_version;
  u2   _minor_version;
  Symbol* _class_name;
  ClassLoaderData* _loader_data;
  KlassHandle _host_klass;
  GrowableArray<Handle>* _cp_patches; // overrides for CP entries

  // precomputed flags
  bool _has_finalizer;
  bool _has_empty_finalizer;
  bool _has_vanilla_constructor;
  int _max_bootstrap_specifier_index;  // detects BSS values

  // class attributes parsed before the instance klass is created:
  bool       _synthetic_flag;
  int        _sde_length;
  char*      _sde_buffer;
  u2         _sourcefile_index;
  u2         _generic_signature_index;

  // Metadata created before the instance klass is created.  Must be deallocated
  // if not transferred to the InstanceKlass upon successful class loading
  // in which case these pointers have been set to NULL.
  instanceKlassHandle _super_klass;
  ConstantPool*    _cp;
  Array<u2>*       _fields;
  Array<Method*>*  _methods;
  Array<u2>*       _inner_classes;
  Array<Klass*>*   _local_interfaces;
  Array<Klass*>*   _transitive_interfaces;
  AnnotationArray* _annotations;
  AnnotationArray* _type_annotations;
  Array<AnnotationArray*>* _fields_annotations;
  Array<AnnotationArray*>* _fields_type_annotations;
  InstanceKlass*   _klass;  // InstanceKlass once created.

  void set_class_synthetic_flag(bool x)        { _synthetic_flag = x; }
  void set_class_sourcefile_index(u2 x)        { _sourcefile_index = x; }
  void set_class_generic_signature_index(u2 x) { _generic_signature_index = x; }
  void set_class_sde_buffer(char* x, int len)  { _sde_buffer = x; _sde_length = len; }

  void init_parsed_class_attributes(ClassLoaderData* loader_data) {
    _loader_data = loader_data;
    _synthetic_flag = false;
    _sourcefile_index = 0;
    _generic_signature_index = 0;
    _sde_buffer = NULL;
    _sde_length = 0;
    // initialize the other flags too:
    _has_finalizer = _has_empty_finalizer = _has_vanilla_constructor = false;
    _max_bootstrap_specifier_index = -1;
    clear_class_metadata();
    _klass = NULL;
  }
  void apply_parsed_class_attributes(instanceKlassHandle k);  // update k
  void apply_parsed_class_metadata(instanceKlassHandle k, int fields_count, TRAPS);
  void clear_class_metadata() {
    // metadata created before the instance klass is created.  Must be
    // deallocated if classfile parsing returns an error.
    _cp = NULL;
    _fields = NULL;
    _methods = NULL;
    _inner_classes = NULL;
    _local_interfaces = NULL;
    _transitive_interfaces = NULL;
    _annotations = _type_annotations = NULL;
    _fields_annotations = _fields_type_annotations = NULL;
  }

  class AnnotationCollector {
  public:
    enum Location { _in_field, _in_method, _in_class };
    enum ID {
      _unknown = 0,
      _method_CallerSensitive,
      _method_ForceInline,
      _method_DontInline,
      _method_LambdaForm_Compiled,
      _method_LambdaForm_Hidden,
      _sun_misc_Contended,
      _field_Stable,
      _annotation_LIMIT
    };
    const Location _location;
    int _annotations_present;
    u2 _contended_group;

    AnnotationCollector(Location location)
    : _location(location), _annotations_present(0)
    {
      assert((int)_annotation_LIMIT <= (int)sizeof(_annotations_present) * BitsPerByte, "");
    }
    // If this annotation name has an ID, report it (or _none).
    ID annotation_index(ClassLoaderData* loader_data, Symbol* name);
    // Set the annotation name:
    void set_annotation(ID id) {
      assert((int)id >= 0 && (int)id < (int)_annotation_LIMIT, "oob");
      _annotations_present |= nth_bit((int)id);
    }

    void remove_annotation(ID id) {
      assert((int)id >= 0 && (int)id < (int)_annotation_LIMIT, "oob");
      _annotations_present &= ~nth_bit((int)id);
    }

    // Report if the annotation is present.
    bool has_any_annotations() const { return _annotations_present != 0; }
    bool has_annotation(ID id) const { return (nth_bit((int)id) & _annotations_present) != 0; }

    void set_contended_group(u2 group) { _contended_group = group; }
    u2 contended_group() const { return _contended_group; }

    bool is_contended() const { return has_annotation(_sun_misc_Contended); }

    void set_stable(bool stable) { set_annotation(_field_Stable); }
    bool is_stable() const { return has_annotation(_field_Stable); }
  };

  // This class also doubles as a holder for metadata cleanup.
  class FieldAnnotationCollector: public AnnotationCollector {
    ClassLoaderData* _loader_data;
    AnnotationArray* _field_annotations;
    AnnotationArray* _field_type_annotations;
  public:
    FieldAnnotationCollector(ClassLoaderData* loader_data) :
                                 AnnotationCollector(_in_field),
                                 _loader_data(loader_data),
                                 _field_annotations(NULL),
                                 _field_type_annotations(NULL) {}
    void apply_to(FieldInfo* f);
    ~FieldAnnotationCollector();
    AnnotationArray* field_annotations()      { return _field_annotations; }
    AnnotationArray* field_type_annotations() { return _field_type_annotations; }

    void set_field_annotations(AnnotationArray* a)      { _field_annotations = a; }
    void set_field_type_annotations(AnnotationArray* a) { _field_type_annotations = a; }
  };

  class MethodAnnotationCollector: public AnnotationCollector {
  public:
    MethodAnnotationCollector() : AnnotationCollector(_in_method) { }
    void apply_to(methodHandle m);
  };
  class ClassAnnotationCollector: public AnnotationCollector {
  public:
    ClassAnnotationCollector() : AnnotationCollector(_in_class) { }
    void apply_to(instanceKlassHandle k);
  };

  enum { fixed_buffer_size = 128 };
  u_char linenumbertable_buffer[fixed_buffer_size];

  ClassFileStream* _stream;              // Actual input stream

  enum { LegalClass, LegalField, LegalMethod }; // used to verify unqualified names

  // Accessors
  ClassFileStream* stream()                        { return _stream; }
  void set_stream(ClassFileStream* st)             { _stream = st; }

  // Constant pool parsing
  void parse_constant_pool_entries(int length, TRAPS);

  constantPoolHandle parse_constant_pool(TRAPS);

  // Interface parsing
  Array<Klass*>* parse_interfaces(int length,
                                  Handle protection_domain,
                                  Symbol* class_name,
                                  bool* has_default_methods,
                                  TRAPS);
  void record_defined_class_dependencies(instanceKlassHandle defined_klass, TRAPS);

  instanceKlassHandle parse_super_class(int super_class_index, TRAPS);
  // Field parsing
  void parse_field_attributes(u2 attributes_count,
                              bool is_static, u2 signature_index,
                              u2* constantvalue_index_addr,
                              bool* is_synthetic_addr,
                              u2* generic_signature_index_addr,
                              FieldAnnotationCollector* parsed_annotations,
                              TRAPS);
  Array<u2>* parse_fields(Symbol* class_name,
                          bool is_interface,
                          FieldAllocationCount *fac,
                          u2* java_fields_count_ptr, TRAPS);

  void print_field_layout(Symbol* name,
                          Array<u2>* fields,
                          constantPoolHandle cp,
                          int instance_size,
                          int instance_fields_start,
                          int instance_fields_end,
                          int static_fields_end);

  // Method parsing
  methodHandle parse_method(bool is_interface,
                            AccessFlags* promoted_flags,
                            TRAPS);
  Array<Method*>* parse_methods(bool is_interface,
                                AccessFlags* promoted_flags,
                                bool* has_final_method,
                                bool* has_default_method,
                                TRAPS);
  intArray* sort_methods(Array<Method*>* methods);

  u2* parse_exception_table(u4 code_length, u4 exception_table_length,
                            TRAPS);
  void parse_linenumber_table(
      u4 code_attribute_length, u4 code_length,
      CompressedLineNumberWriteStream** write_stream, TRAPS);
  u2* parse_localvariable_table(u4 code_length, u2 max_locals, u4 code_attribute_length,
                                u2* localvariable_table_length,
                                bool isLVTT, TRAPS);
  u2* parse_checked_exceptions(u2* checked_exceptions_length, u4 method_attribute_length,
                               TRAPS);
  void parse_type_array(u2 array_length, u4 code_length, u4* u1_index, u4* u2_index,
                        u1* u1_array, u2* u2_array, TRAPS);
  u1* parse_stackmap_table(u4 code_attribute_length, TRAPS);

  // Classfile attribute parsing
  void parse_classfile_sourcefile_attribute(TRAPS);
  void parse_classfile_source_debug_extension_attribute(int length, TRAPS);
  u2   parse_classfile_inner_classes_attribute(u1* inner_classes_attribute_start,
                                               bool parsed_enclosingmethod_attribute,
                                               u2 enclosing_method_class_index,
                                               u2 enclosing_method_method_index,
                                               TRAPS);
  void parse_classfile_attributes(ClassAnnotationCollector* parsed_annotations,
                                  TRAPS);
  void parse_classfile_synthetic_attribute(TRAPS);
  void parse_classfile_signature_attribute(TRAPS);
  void parse_classfile_bootstrap_methods_attribute(u4 attribute_length, TRAPS);

  // Annotations handling
  AnnotationArray* assemble_annotations(u1* runtime_visible_annotations,
                                        int runtime_visible_annotations_length,
                                        u1* runtime_invisible_annotations,
                                        int runtime_invisible_annotations_length, TRAPS);
  int skip_annotation(u1* buffer, int limit, int index);
  int skip_annotation_value(u1* buffer, int limit, int index);
  void parse_annotations(u1* buffer, int limit,
                         /* Results (currently, only one result is supported): */
                         AnnotationCollector* result,
                         TRAPS);

  // Final setup
  unsigned int compute_oop_map_count(instanceKlassHandle super,
                                     unsigned int nonstatic_oop_count,
                                     int first_nonstatic_oop_offset);
  void fill_oop_maps(instanceKlassHandle k,
                     unsigned int nonstatic_oop_map_count,
                     int* nonstatic_oop_offsets,
                     unsigned int* nonstatic_oop_counts);
  void set_precomputed_flags(instanceKlassHandle k);
  Array<Klass*>* compute_transitive_interfaces(instanceKlassHandle super,
                                               Array<Klass*>* local_ifs, TRAPS);

  // Format checker methods
  void classfile_parse_error(const char* msg, TRAPS);
  void classfile_parse_error(const char* msg, int index, TRAPS);
  void classfile_parse_error(const char* msg, const char *name, TRAPS);
  void classfile_parse_error(const char* msg, int index, const char *name, TRAPS);
  inline void guarantee_property(bool b, const char* msg, TRAPS) {
    if (!b) { classfile_parse_error(msg, CHECK); }
  }

  inline void assert_property(bool b, const char* msg, TRAPS) {
#ifdef ASSERT
    if (!b) {
      ResourceMark rm(THREAD);
      fatal(err_msg(msg, _class_name->as_C_string()));
    }
#endif
  }

  inline void assert_property(bool b, const char* msg, int index, TRAPS) {
#ifdef ASSERT
    if (!b) {
      ResourceMark rm(THREAD);
      fatal(err_msg(msg, index, _class_name->as_C_string()));
    }
#endif
  }

  inline void check_property(bool property, const char* msg, int index, TRAPS) {
    if (_need_verify) {
      guarantee_property(property, msg, index, CHECK);
    } else {
      assert_property(property, msg, index, CHECK);
    }
  }

  inline void check_property(bool property, const char* msg, TRAPS) {
    if (_need_verify) {
      guarantee_property(property, msg, CHECK);
    } else {
      assert_property(property, msg, CHECK);
    }
  }

  inline void guarantee_property(bool b, const char* msg, int index, TRAPS) {
    if (!b) { classfile_parse_error(msg, index, CHECK); }
  }
  inline void guarantee_property(bool b, const char* msg, const char *name, TRAPS) {
    if (!b) { classfile_parse_error(msg, name, CHECK); }
  }
  inline void guarantee_property(bool b, const char* msg, int index, const char *name, TRAPS) {
    if (!b) { classfile_parse_error(msg, index, name, CHECK); }
  }

  void throwIllegalSignature(
      const char* type, Symbol* name, Symbol* sig, TRAPS);

  bool is_supported_version(u2 major, u2 minor);
  bool has_illegal_visibility(jint flags);

  void verify_constantvalue(int constantvalue_index, int signature_index, TRAPS);
  void verify_legal_utf8(const unsigned char* buffer, int length, TRAPS);
  void verify_legal_class_name(Symbol* name, TRAPS);
  void verify_legal_field_name(Symbol* name, TRAPS);
  void verify_legal_method_name(Symbol* name, TRAPS);
  void verify_legal_field_signature(Symbol* fieldname, Symbol* signature, TRAPS);
  int  verify_legal_method_signature(Symbol* methodname, Symbol* signature, TRAPS);
  void verify_legal_class_modifiers(jint flags, TRAPS);
  void verify_legal_field_modifiers(jint flags, bool is_interface, TRAPS);
  void verify_legal_method_modifiers(jint flags, bool is_interface, Symbol* name, TRAPS);
  bool verify_unqualified_name(char* name, unsigned int length, int type);
  char* skip_over_field_name(char* name, bool slash_ok, unsigned int length);
  char* skip_over_field_signature(char* signature, bool void_ok, unsigned int length, TRAPS);

  bool is_anonymous() {
    assert(EnableInvokeDynamic || _host_klass.is_null(), "");
    return _host_klass.not_null();
  }
  bool has_cp_patch_at(int index) {
    assert(EnableInvokeDynamic, "");
    assert(index >= 0, "oob");
    return (_cp_patches != NULL
            && index < _cp_patches->length()
            && _cp_patches->adr_at(index)->not_null());
  }
  Handle cp_patch_at(int index) {
    assert(has_cp_patch_at(index), "oob");
    return _cp_patches->at(index);
  }
  Handle clear_cp_patch_at(int index) {
    Handle patch = cp_patch_at(index);
    _cp_patches->at_put(index, Handle());
    assert(!has_cp_patch_at(index), "");
    return patch;
  }
  void patch_constant_pool(constantPoolHandle cp, int index, Handle patch, TRAPS);

  // Wrapper for constantTag.is_klass_[or_]reference.
  // In older versions of the VM, Klass*s cannot sneak into early phases of
  // constant pool construction, but in later versions they can.
  // %%% Let's phase out the old is_klass_reference.
  bool valid_klass_reference_at(int index) {
    return _cp->is_within_bounds(index) &&
         (EnableInvokeDynamic
            ? _cp->tag_at(index).is_klass_or_reference()
            : _cp->tag_at(index).is_klass_reference());
  }

  // Checks that the cpool index is in range and is a utf8
  bool valid_symbol_at(int cpool_index) {
    return (_cp->is_within_bounds(cpool_index) &&
            _cp->tag_at(cpool_index).is_utf8());
  }

  void copy_localvariable_table(ConstMethod* cm, int lvt_cnt,
                                u2* localvariable_table_length,
                                u2** localvariable_table_start,
                                int lvtt_cnt,
                                u2* localvariable_type_table_length,
                                u2** localvariable_type_table_start,
                                TRAPS);

  void copy_method_annotations(ConstMethod* cm,
                               u1* runtime_visible_annotations,
                               int runtime_visible_annotations_length,
                               u1* runtime_invisible_annotations,
                               int runtime_invisible_annotations_length,
                               u1* runtime_visible_parameter_annotations,
                               int runtime_visible_parameter_annotations_length,
                               u1* runtime_invisible_parameter_annotations,
                               int runtime_invisible_parameter_annotations_length,
                               u1* runtime_visible_type_annotations,
                               int runtime_visible_type_annotations_length,
                               u1* runtime_invisible_type_annotations,
                               int runtime_invisible_type_annotations_length,
                               u1* annotation_default,
                               int annotation_default_length,
                               TRAPS);

  // lays out fields in class and returns the total oopmap count
  void layout_fields(Handle class_loader, FieldAllocationCount* fac,
                     ClassAnnotationCollector* parsed_annotations,
                     FieldLayoutInfo* info, TRAPS);

 public:
  // Constructor
  ClassFileParser(ClassFileStream* st) { set_stream(st); }
  ~ClassFileParser();

  // Parse .class file and return new Klass*. The Klass* is not hooked up
  // to the system dictionary or any other structures, so a .class file can
  // be loaded several times if desired.
  // The system dictionary hookup is done by the caller.
  //
  // "parsed_name" is updated by this method, and is the name found
  // while parsing the stream.
  instanceKlassHandle parseClassFile(Symbol* name,
                                     ClassLoaderData* loader_data,
                                     Handle protection_domain,
                                     TempNewSymbol& parsed_name,
                                     bool verify,
                                     TRAPS) {
    KlassHandle no_host_klass;
    return parseClassFile(name, loader_data, protection_domain, no_host_klass, NULL, parsed_name, verify, THREAD);
  }
  instanceKlassHandle parseClassFile(Symbol* name,
                                     ClassLoaderData* loader_data,
                                     Handle protection_domain,
                                     KlassHandle host_klass,
                                     GrowableArray<Handle>* cp_patches,
                                     TempNewSymbol& parsed_name,
                                     bool verify,
                                     TRAPS);

  // Verifier checks
  static void check_super_class_access(instanceKlassHandle this_klass, TRAPS);
  static void check_super_interface_access(instanceKlassHandle this_klass, TRAPS);
  static void check_final_method_override(instanceKlassHandle this_klass, TRAPS);
  static void check_illegal_static_method(instanceKlassHandle this_klass, TRAPS);
};

#endif // SHARE_VM_CLASSFILE_CLASSFILEPARSER_HPP

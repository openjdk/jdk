/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/referenceType.hpp"
#include "runtime/handles.inline.hpp"
#include "oops/constantPool.hpp"
#include "oops/typeArrayOop.hpp"
#include "utilities/accessFlags.hpp"

class Annotations;
template <typename T>
class Array;
class ClassFileStream;
class ClassLoaderData;
class CompressedLineNumberWriteStream;
class ConstMethod;
class FieldInfo;
template <typename T>
class GrowableArray;
class InstanceKlass;
class intArray;
class Symbol;
class TempNewSymbol;

// Parser for for .class files
//
// The bytes describing the class file structure is read from a Stream object

class ClassFileParser VALUE_OBJ_CLASS_SPEC {

 class ClassAnnotationCollector;
 class FieldAllocationCount;
 class FieldAnnotationCollector;
 class FieldLayoutInfo;

 public:
  // The ClassFileParser has an associated "publicity" level
  // It is used to control which subsystems (if any)
  // will observe the parsing (logging, events, tracing).
  // Default level is "BROADCAST", which is equivalent to
  // a "public" parsing attempt.
  //
  // "INTERNAL" level should be entirely private to the
  // caller - this allows for internal reuse of ClassFileParser
  //
  enum Publicity {
    INTERNAL,
    BROADCAST,
    NOF_PUBLICITY_LEVELS
  };

 private:
  const ClassFileStream* _stream; // Actual input stream
  const Symbol* _requested_name;
  Symbol* _class_name;
  mutable ClassLoaderData* _loader_data;
  const Klass* _host_klass;
  GrowableArray<Handle>* _cp_patches; // overrides for CP entries
  TempNewSymbol* _parsed_name;

  // Metadata created before the instance klass is created.  Must be deallocated
  // if not transferred to the InstanceKlass upon successful class loading
  // in which case these pointers have been set to NULL.
  const InstanceKlass* _super_klass;
  ConstantPool* _cp;
  Array<u2>* _fields;
  Array<Method*>* _methods;
  Array<u2>* _inner_classes;
  Array<Klass*>* _local_interfaces;
  Array<Klass*>* _transitive_interfaces;
  Annotations* _combined_annotations;
  AnnotationArray* _annotations;
  AnnotationArray* _type_annotations;
  Array<AnnotationArray*>* _fields_annotations;
  Array<AnnotationArray*>* _fields_type_annotations;
  InstanceKlass* _klass;  // InstanceKlass* once created.
  InstanceKlass* _klass_to_deallocate; // an InstanceKlass* to be destroyed

  ClassAnnotationCollector* _parsed_annotations;
  FieldAllocationCount* _fac;
  FieldLayoutInfo* _field_info;
  const intArray* _method_ordering;
  GrowableArray<Method*>* _all_mirandas;

  enum { fixed_buffer_size = 128 };
  u_char _linenumbertable_buffer[fixed_buffer_size];

  // Size of Java vtable (in words)
  int _vtable_size;
  int _itable_size;

  int _num_miranda_methods;

  ReferenceType _rt;
  Handle _protection_domain;
  AccessFlags _access_flags;

  // for tracing and notifications
  Publicity _pub_level;

  // class attributes parsed before the instance klass is created:
  bool _synthetic_flag;
  int _sde_length;
  const char* _sde_buffer;
  u2 _sourcefile_index;
  u2 _generic_signature_index;

  u2 _major_version;
  u2 _minor_version;
  u2 _this_class_index;
  u2 _super_class_index;
  u2 _itfs_len;
  u2 _java_fields_count;

  bool _need_verify;
  bool _relax_verify;

  bool _has_default_methods;
  bool _declares_default_methods;
  bool _has_final_method;

  // precomputed flags
  bool _has_finalizer;
  bool _has_empty_finalizer;
  bool _has_vanilla_constructor;
  int _max_bootstrap_specifier_index;  // detects BSS values

  void parse_stream(const ClassFileStream* const stream, TRAPS);

  void post_process_parsed_stream(const ClassFileStream* const stream,
                                  ConstantPool* cp,
                                  TRAPS);

  void fill_instance_klass(InstanceKlass* ik, TRAPS);
  void set_klass(InstanceKlass* instance);

  void set_class_synthetic_flag(bool x)        { _synthetic_flag = x; }
  void set_class_sourcefile_index(u2 x)        { _sourcefile_index = x; }
  void set_class_generic_signature_index(u2 x) { _generic_signature_index = x; }
  void set_class_sde_buffer(const char* x, int len)  { _sde_buffer = x; _sde_length = len; }

  void create_combined_annotations(TRAPS);
  void apply_parsed_class_attributes(InstanceKlass* k);  // update k
  void apply_parsed_class_metadata(InstanceKlass* k, int fields_count, TRAPS);
  void clear_class_metadata();

  // Constant pool parsing
  void parse_constant_pool_entries(const ClassFileStream* const stream,
                                   ConstantPool* cp,
                                   const int length,
                                   TRAPS);

  void parse_constant_pool(const ClassFileStream* const cfs,
                           ConstantPool* const cp,
                           const int length,
                           TRAPS);

  // Interface parsing
  void parse_interfaces(const ClassFileStream* const stream,
                        const int itfs_len,
                        ConstantPool* const cp,
                        bool* has_default_methods,
                        TRAPS);

  const InstanceKlass* parse_super_class(ConstantPool* const cp,
                                         const int super_class_index,
                                         const bool need_verify,
                                         TRAPS);

  // Field parsing
  void parse_field_attributes(const ClassFileStream* const cfs,
                              u2 attributes_count,
                              bool is_static,
                              u2 signature_index,
                              u2* const constantvalue_index_addr,
                              bool* const is_synthetic_addr,
                              u2* const generic_signature_index_addr,
                              FieldAnnotationCollector* parsed_annotations,
                              TRAPS);

  void parse_fields(const ClassFileStream* const cfs,
                    bool is_interface,
                    FieldAllocationCount* const fac,
                    ConstantPool* cp,
                    const int cp_size,
                    u2* const java_fields_count_ptr,
                    TRAPS);

  // Method parsing
  Method* parse_method(const ClassFileStream* const cfs,
                       bool is_interface,
                       const ConstantPool* cp,
                       AccessFlags* const promoted_flags,
                       TRAPS);

  void parse_methods(const ClassFileStream* const cfs,
                     bool is_interface,
                     AccessFlags* const promoted_flags,
                     bool* const has_final_method,
                     bool* const declares_default_methods,
                     TRAPS);

  const u2* parse_exception_table(const ClassFileStream* const stream,
                                  u4 code_length,
                                  u4 exception_table_length,
                                  TRAPS);

  void parse_linenumber_table(u4 code_attribute_length,
                              u4 code_length,
                              CompressedLineNumberWriteStream**const write_stream,
                              TRAPS);

  const u2* parse_localvariable_table(const ClassFileStream* const cfs,
                                      u4 code_length,
                                      u2 max_locals,
                                      u4 code_attribute_length,
                                      u2* const localvariable_table_length,
                                      bool isLVTT,
                                      TRAPS);

  const u2* parse_checked_exceptions(const ClassFileStream* const cfs,
                                     u2* const checked_exceptions_length,
                                     u4 method_attribute_length,
                                     TRAPS);

  void parse_type_array(u2 array_length,
                        u4 code_length,
                        u4* const u1_index,
                        u4* const u2_index,
                        u1* const u1_array,
                        u2* const u2_array,
                        TRAPS);

  // Classfile attribute parsing
  u2 parse_generic_signature_attribute(const ClassFileStream* const cfs, TRAPS);
  void parse_classfile_sourcefile_attribute(const ClassFileStream* const cfs, TRAPS);
  void parse_classfile_source_debug_extension_attribute(const ClassFileStream* const cfs,
                                                        int length,
                                                        TRAPS);

  u2   parse_classfile_inner_classes_attribute(const ClassFileStream* const cfs,
                                               const u1* const inner_classes_attribute_start,
                                               bool parsed_enclosingmethod_attribute,
                                               u2 enclosing_method_class_index,
                                               u2 enclosing_method_method_index,
                                               TRAPS);

  void parse_classfile_attributes(const ClassFileStream* const cfs,
                                  ConstantPool* cp,
                                  ClassAnnotationCollector* parsed_annotations,
                                  TRAPS);

  void parse_classfile_synthetic_attribute(TRAPS);
  void parse_classfile_signature_attribute(const ClassFileStream* const cfs, TRAPS);
  void parse_classfile_bootstrap_methods_attribute(const ClassFileStream* const cfs,
                                                   ConstantPool* cp,
                                                   u4 attribute_length,
                                                   TRAPS);

  // Annotations handling
  AnnotationArray* assemble_annotations(const u1* const runtime_visible_annotations,
                                        int runtime_visible_annotations_length,
                                        const u1* const runtime_invisible_annotations,
                                        int runtime_invisible_annotations_length,
                                        TRAPS);

  void set_precomputed_flags(InstanceKlass* k);

  // Format checker methods
  void classfile_parse_error(const char* msg, TRAPS) const;
  void classfile_parse_error(const char* msg, int index, TRAPS) const;
  void classfile_parse_error(const char* msg, const char *name, TRAPS) const;
  void classfile_parse_error(const char* msg,
                             int index,
                             const char *name,
                             TRAPS) const;

  inline void guarantee_property(bool b, const char* msg, TRAPS) const {
    if (!b) { classfile_parse_error(msg, CHECK); }
  }

  void report_assert_property_failure(const char* msg, TRAPS) const PRODUCT_RETURN;
  void report_assert_property_failure(const char* msg, int index, TRAPS) const PRODUCT_RETURN;

  inline void assert_property(bool b, const char* msg, TRAPS) const {
#ifdef ASSERT
    if (!b) {
      report_assert_property_failure(msg, THREAD);
    }
#endif
  }

  inline void assert_property(bool b, const char* msg, int index, TRAPS) const {
#ifdef ASSERT
    if (!b) {
      report_assert_property_failure(msg, index, THREAD);
    }
#endif
  }

  inline void check_property(bool property,
                             const char* msg,
                             int index,
                             TRAPS) const {
    if (_need_verify) {
      guarantee_property(property, msg, index, CHECK);
    } else {
      assert_property(property, msg, index, CHECK);
    }
  }

  inline void check_property(bool property, const char* msg, TRAPS) const {
    if (_need_verify) {
      guarantee_property(property, msg, CHECK);
    } else {
      assert_property(property, msg, CHECK);
    }
  }

  inline void guarantee_property(bool b,
                                 const char* msg,
                                 int index,
                                 TRAPS) const {
    if (!b) { classfile_parse_error(msg, index, CHECK); }
  }

  inline void guarantee_property(bool b,
                                 const char* msg,
                                 const char *name,
                                 TRAPS) const {
    if (!b) { classfile_parse_error(msg, name, CHECK); }
  }

  inline void guarantee_property(bool b,
                                 const char* msg,
                                 int index,
                                 const char *name,
                                 TRAPS) const {
    if (!b) { classfile_parse_error(msg, index, name, CHECK); }
  }

  void throwIllegalSignature(const char* type,
                             const Symbol* name,
                             const Symbol* sig,
                             TRAPS) const;

  void verify_constantvalue(const ConstantPool* const cp,
                            int constantvalue_index,
                            int signature_index,
                            TRAPS) const;

  void verify_legal_utf8(const unsigned char* buffer, int length, TRAPS) const;
  void verify_legal_class_name(const Symbol* name, TRAPS) const;
  void verify_legal_field_name(const Symbol* name, TRAPS) const;
  void verify_legal_method_name(const Symbol* name, TRAPS) const;

  void verify_legal_field_signature(const Symbol* fieldname,
                                    const Symbol* signature,
                                    TRAPS) const;
  int  verify_legal_method_signature(const Symbol* methodname,
                                     const Symbol* signature,
                                     TRAPS) const;

  void verify_legal_class_modifiers(jint flags, TRAPS) const;
  void verify_legal_field_modifiers(jint flags, bool is_interface, TRAPS) const;
  void verify_legal_method_modifiers(jint flags,
                                     bool is_interface,
                                     const Symbol* name,
                                     TRAPS) const;

  const char* skip_over_field_signature(const char* signature,
                                        bool void_ok,
                                        unsigned int length,
                                        TRAPS) const;

  bool has_cp_patch_at(int index) const {
    assert(index >= 0, "oob");
    return (_cp_patches != NULL
            && index < _cp_patches->length()
            && _cp_patches->adr_at(index)->not_null());
  }

  Handle cp_patch_at(int index) const {
    assert(has_cp_patch_at(index), "oob");
    return _cp_patches->at(index);
  }

  Handle clear_cp_patch_at(int index) {
    Handle patch = cp_patch_at(index);
    _cp_patches->at_put(index, Handle());
    assert(!has_cp_patch_at(index), "");
    return patch;
  }

  void patch_constant_pool(ConstantPool* cp,
                           int index,
                           Handle patch,
                           TRAPS);

  // Wrapper for constantTag.is_klass_[or_]reference.
  // In older versions of the VM, Klass*s cannot sneak into early phases of
  // constant pool construction, but in later versions they can.
  // %%% Let's phase out the old is_klass_reference.
  bool valid_klass_reference_at(int index) const {
    return _cp->is_within_bounds(index) &&
             _cp->tag_at(index).is_klass_or_reference();
  }

  // Checks that the cpool index is in range and is a utf8
  bool valid_symbol_at(int cpool_index) const {
    return _cp->is_within_bounds(cpool_index) &&
             _cp->tag_at(cpool_index).is_utf8();
  }

  void copy_localvariable_table(const ConstMethod* cm,
                                int lvt_cnt,
                                u2* const localvariable_table_length,
                                const u2**const localvariable_table_start,
                                int lvtt_cnt,
                                u2* const localvariable_type_table_length,
                                const u2** const localvariable_type_table_start,
                                TRAPS);

  void copy_method_annotations(ConstMethod* cm,
                               const u1* runtime_visible_annotations,
                               int runtime_visible_annotations_length,
                               const u1* runtime_invisible_annotations,
                               int runtime_invisible_annotations_length,
                               const u1* runtime_visible_parameter_annotations,
                               int runtime_visible_parameter_annotations_length,
                               const u1* runtime_invisible_parameter_annotations,
                               int runtime_invisible_parameter_annotations_length,
                               const u1* runtime_visible_type_annotations,
                               int runtime_visible_type_annotations_length,
                               const u1* runtime_invisible_type_annotations,
                               int runtime_invisible_type_annotations_length,
                               const u1* annotation_default,
                               int annotation_default_length,
                               TRAPS);

  // lays out fields in class and returns the total oopmap count
  void layout_fields(ConstantPool* cp,
                     const FieldAllocationCount* fac,
                     const ClassAnnotationCollector* parsed_annotations,
                     FieldLayoutInfo* info,
                     TRAPS);

 public:
  ClassFileParser(ClassFileStream* stream,
                  Symbol* name,
                  ClassLoaderData* loader_data,
                  Handle protection_domain,
                  TempNewSymbol* parsed_name,
                  const Klass* host_klass,
                  GrowableArray<Handle>* cp_patches,
                  Publicity pub_level,
                  TRAPS);

  ~ClassFileParser();

  InstanceKlass* create_instance_klass(TRAPS);

  const ClassFileStream* clone_stream() const;

  void set_klass_to_deallocate(InstanceKlass* klass);

  int static_field_size() const;
  int total_oop_map_count() const;
  jint layout_size() const;

  int vtable_size() const { return _vtable_size; }
  int itable_size() const { return _itable_size; }

  u2 this_class_index() const { return _this_class_index; }
  u2 super_class_index() const { return _super_class_index; }

  bool is_anonymous() const { return _host_klass != NULL; }
  bool is_interface() const { return _access_flags.is_interface(); }

  const Klass* host_klass() const { return _host_klass; }
  const GrowableArray<Handle>* cp_patches() const { return _cp_patches; }
  ClassLoaderData* loader_data() const { return _loader_data; }
  const Symbol* class_name() const { return _class_name; }
  const Klass* super_klass() const { return _super_klass; }

  ReferenceType reference_type() const { return _rt; }
  AccessFlags access_flags() const { return _access_flags; }

  bool is_internal() const { return INTERNAL == _pub_level; }

};

#endif // SHARE_VM_CLASSFILE_CLASSFILEPARSER_HPP

/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

// Parser for for .class files
//
// The bytes describing the class file structure is read from a Stream object

class ClassFileParser VALUE_OBJ_CLASS_SPEC {
 private:
  bool _need_verify;
  bool _relax_verify;
  u2   _major_version;
  u2   _minor_version;
  symbolHandle _class_name;
  KlassHandle _host_klass;
  GrowableArray<Handle>* _cp_patches; // overrides for CP entries

  bool _has_finalizer;
  bool _has_empty_finalizer;
  bool _has_vanilla_constructor;

  enum { fixed_buffer_size = 128 };
  u_char linenumbertable_buffer[fixed_buffer_size];

  ClassFileStream* _stream;              // Actual input stream

  enum { LegalClass, LegalField, LegalMethod }; // used to verify unqualified names

  // Accessors
  ClassFileStream* stream()                        { return _stream; }
  void set_stream(ClassFileStream* st)             { _stream = st; }

  // Constant pool parsing
  void parse_constant_pool_entries(constantPoolHandle cp, int length, TRAPS);

  constantPoolHandle parse_constant_pool(TRAPS);

  // Interface parsing
  objArrayHandle parse_interfaces(constantPoolHandle cp,
                                  int length,
                                  Handle class_loader,
                                  Handle protection_domain,
                                  symbolHandle class_name,
                                  TRAPS);

  // Field parsing
  void parse_field_attributes(constantPoolHandle cp, u2 attributes_count,
                              bool is_static, u2 signature_index,
                              u2* constantvalue_index_addr,
                              bool* is_synthetic_addr,
                              u2* generic_signature_index_addr,
                              typeArrayHandle* field_annotations, TRAPS);
  typeArrayHandle parse_fields(constantPoolHandle cp, bool is_interface,
                               struct FieldAllocationCount *fac,
                               objArrayHandle* fields_annotations, TRAPS);

  // Method parsing
  methodHandle parse_method(constantPoolHandle cp, bool is_interface,
                            AccessFlags* promoted_flags,
                            typeArrayHandle* method_annotations,
                            typeArrayHandle* method_parameter_annotations,
                            typeArrayHandle* method_default_annotations,
                            TRAPS);
  objArrayHandle parse_methods (constantPoolHandle cp, bool is_interface,
                                AccessFlags* promoted_flags,
                                bool* has_final_method,
                                objArrayOop* methods_annotations_oop,
                                objArrayOop* methods_parameter_annotations_oop,
                                objArrayOop* methods_default_annotations_oop,
                                TRAPS);
  typeArrayHandle sort_methods (objArrayHandle methods,
                                objArrayHandle methods_annotations,
                                objArrayHandle methods_parameter_annotations,
                                objArrayHandle methods_default_annotations,
                                TRAPS);
  typeArrayHandle parse_exception_table(u4 code_length, u4 exception_table_length,
                                        constantPoolHandle cp, TRAPS);
  void parse_linenumber_table(
      u4 code_attribute_length, u4 code_length,
      CompressedLineNumberWriteStream** write_stream, TRAPS);
  u2* parse_localvariable_table(u4 code_length, u2 max_locals, u4 code_attribute_length,
                                constantPoolHandle cp, u2* localvariable_table_length,
                                bool isLVTT, TRAPS);
  u2* parse_checked_exceptions(u2* checked_exceptions_length, u4 method_attribute_length,
                               constantPoolHandle cp, TRAPS);
  void parse_type_array(u2 array_length, u4 code_length, u4* u1_index, u4* u2_index,
                        u1* u1_array, u2* u2_array, constantPoolHandle cp, TRAPS);
  typeArrayOop parse_stackmap_table(u4 code_attribute_length, TRAPS);

  // Classfile attribute parsing
  void parse_classfile_sourcefile_attribute(constantPoolHandle cp, instanceKlassHandle k, TRAPS);
  void parse_classfile_source_debug_extension_attribute(constantPoolHandle cp,
                                                instanceKlassHandle k, int length, TRAPS);
  u2   parse_classfile_inner_classes_attribute(constantPoolHandle cp,
                                               instanceKlassHandle k, TRAPS);
  void parse_classfile_attributes(constantPoolHandle cp, instanceKlassHandle k, TRAPS);
  void parse_classfile_synthetic_attribute(constantPoolHandle cp, instanceKlassHandle k, TRAPS);
  void parse_classfile_signature_attribute(constantPoolHandle cp, instanceKlassHandle k, TRAPS);

  // Annotations handling
  typeArrayHandle assemble_annotations(u1* runtime_visible_annotations,
                                       int runtime_visible_annotations_length,
                                       u1* runtime_invisible_annotations,
                                       int runtime_invisible_annotations_length, TRAPS);

  // Final setup
  unsigned int compute_oop_map_count(instanceKlassHandle super,
                                     unsigned int nonstatic_oop_count,
                                     int first_nonstatic_oop_offset);
  void fill_oop_maps(instanceKlassHandle k,
                     unsigned int nonstatic_oop_map_count,
                     int* nonstatic_oop_offsets,
                     unsigned int* nonstatic_oop_counts);
  void set_precomputed_flags(instanceKlassHandle k);
  objArrayHandle compute_transitive_interfaces(instanceKlassHandle super,
                                               objArrayHandle local_ifs, TRAPS);

  // Special handling for certain classes.
  // Add the "discovered" field to java.lang.ref.Reference if
  // it does not exist.
  void java_lang_ref_Reference_fix_pre(typeArrayHandle* fields_ptr,
    constantPoolHandle cp, FieldAllocationCount *fac_ptr, TRAPS);
  // Adjust the field allocation counts for java.lang.Class to add
  // fake fields.
  void java_lang_Class_fix_pre(objArrayHandle* methods_ptr,
    FieldAllocationCount *fac_ptr, TRAPS);
  // Adjust the next_nonstatic_oop_offset to place the fake fields
  // before any Java fields.
  void java_lang_Class_fix_post(int* next_nonstatic_oop_offset);
  // Adjust the field allocation counts for java.dyn.MethodHandle to add
  // a fake address (void*) field.
  void java_dyn_MethodHandle_fix_pre(constantPoolHandle cp,
                                     typeArrayHandle fields,
                                     FieldAllocationCount *fac_ptr, TRAPS);

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
    if (!b) { fatal(msg); }
#endif
  }

  inline void check_property(bool property, const char* msg, int index, TRAPS) {
    if (_need_verify) {
      guarantee_property(property, msg, index, CHECK);
    } else {
      assert_property(property, msg, CHECK);
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
      const char* type, symbolHandle name, symbolHandle sig, TRAPS);

  bool is_supported_version(u2 major, u2 minor);
  bool has_illegal_visibility(jint flags);

  void verify_constantvalue(int constantvalue_index, int signature_index, constantPoolHandle cp, TRAPS);
  void verify_legal_utf8(const unsigned char* buffer, int length, TRAPS);
  void verify_legal_class_name(symbolHandle name, TRAPS);
  void verify_legal_field_name(symbolHandle name, TRAPS);
  void verify_legal_method_name(symbolHandle name, TRAPS);
  void verify_legal_field_signature(symbolHandle fieldname, symbolHandle signature, TRAPS);
  int  verify_legal_method_signature(symbolHandle methodname, symbolHandle signature, TRAPS);
  void verify_legal_class_modifiers(jint flags, TRAPS);
  void verify_legal_field_modifiers(jint flags, bool is_interface, TRAPS);
  void verify_legal_method_modifiers(jint flags, bool is_interface, symbolHandle name, TRAPS);
  bool verify_unqualified_name(char* name, unsigned int length, int type);
  char* skip_over_field_name(char* name, bool slash_ok, unsigned int length);
  char* skip_over_field_signature(char* signature, bool void_ok, unsigned int length, TRAPS);

  bool is_anonymous() {
    assert(AnonymousClasses || _host_klass.is_null(), "");
    return _host_klass.not_null();
  }
  bool has_cp_patch_at(int index) {
    assert(AnonymousClasses, "");
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
  // In older versions of the VM, klassOops cannot sneak into early phases of
  // constant pool construction, but in later versions they can.
  // %%% Let's phase out the old is_klass_reference.
  bool is_klass_reference(constantPoolHandle cp, int index) {
    return ((LinkWellKnownClasses || AnonymousClasses)
            ? cp->tag_at(index).is_klass_or_reference()
            : cp->tag_at(index).is_klass_reference());
  }

 public:
  // Constructor
  ClassFileParser(ClassFileStream* st) { set_stream(st); }

  // Parse .class file and return new klassOop. The klassOop is not hooked up
  // to the system dictionary or any other structures, so a .class file can
  // be loaded several times if desired.
  // The system dictionary hookup is done by the caller.
  //
  // "parsed_name" is updated by this method, and is the name found
  // while parsing the stream.
  instanceKlassHandle parseClassFile(symbolHandle name,
                                     Handle class_loader,
                                     Handle protection_domain,
                                     symbolHandle& parsed_name,
                                     bool verify,
                                     TRAPS) {
    KlassHandle no_host_klass;
    return parseClassFile(name, class_loader, protection_domain, no_host_klass, NULL, parsed_name, verify, THREAD);
  }
  instanceKlassHandle parseClassFile(symbolHandle name,
                                     Handle class_loader,
                                     Handle protection_domain,
                                     KlassHandle host_klass,
                                     GrowableArray<Handle>* cp_patches,
                                     symbolHandle& parsed_name,
                                     bool verify,
                                     TRAPS);

  // Verifier checks
  static void check_super_class_access(instanceKlassHandle this_klass, TRAPS);
  static void check_super_interface_access(instanceKlassHandle this_klass, TRAPS);
  static void check_final_method_override(instanceKlassHandle this_klass, TRAPS);
  static void check_illegal_static_method(instanceKlassHandle this_klass, TRAPS);
};

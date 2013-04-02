/*
 * Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 */

// -*- C++ -*-
struct entry;
struct cpindex;
struct unpacker;

struct band {
  const char*   name;
  int           bn;             // band_number of this band
  coding*       defc;           // default coding method
  cpindex*      ix;             // CP entry mapping, if CPRefBand
  byte          ixTag;          // 0 or 1; null is coded as (nullOK?0:-1)
  byte          nullOK;         // 0 or 1; null is coded as (nullOK?0:-1)
  int           length;         // expected # values
  unpacker*     u;              // back pointer

  value_stream  vs[2];         // source of values
  coding_method cm;            // method used for initial state of vs[0]
  byte*         rplimit;       // end of band (encoded, transmitted)

  int           total_memo;    // cached value of getIntTotal, or -1
  int*          hist0;         // approximate. histogram
  enum { HIST0_MIN = 0, HIST0_MAX = 255 }; // catches the usual cases

  // properties for attribute layout elements:
  byte          le_kind;       // EK_XXX
  byte          le_bci;        // 0,EK_BCI,EK_BCD,EK_BCO
  byte          le_back;       // ==EF_BACK
  byte          le_len;        // 0,1,2,4 (size in classfile), or call addr
  band**        le_body;       // body of repl, union, call (null-terminated)
  // Note:  EK_CASE elements use hist0 to record union tags.
  #define       le_casetags    hist0

  band& nextBand() { return this[1]; }
  band& prevBand() { return this[-1]; }

  void init(unpacker* u_, int bn_, coding* defc_) {
    u    = u_;
    cm.u = u_;
    bn   = bn_;
    defc = defc_;
  }
  void init(unpacker* u_, int bn_, int defcSpec) {
    init(u_, bn_, coding::findBySpec(defcSpec));
  }
  void initRef(int ixTag_ = 0, bool nullOK_ = false) {
    ixTag  = ixTag_;
    nullOK = nullOK_;
    setIndexByTag(ixTag);
  }

  void expectMoreLength(int l) {
    assert(length >= 0);      // able to accept a length
    assert((int)l >= 0);      // no overflow
    assert(rplimit == null);  // readData not yet called
    length += l;
    assert(length >= l);      // no overflow
  }

  void setIndex(cpindex* ix_);
  void setIndexByTag(byte tag);

  // Parse the band and its meta-coding header.
  void readData(int expectedLength = 0);

  // Reset the band for another pass (Cf. Java Band.resetForSecondPass.)
  void rewind() {
    cm.reset(&vs[0]);
  }

  byte* &curRP()    { return vs[0].rp; }
  byte*  minRP()    { return cm.vs0.rp; }
  byte*  maxRP()    { return rplimit; }
  size_t size()     { return maxRP() - minRP(); }

  int    getByte()  { assert(ix == null); return vs[0].getByte(); }
  int    getInt()   { assert(ix == null); return vs[0].getInt(); }
  entry* getRefN()  { return getRefCommon(ix, true); }
  entry* getRef()   { return getRefCommon(ix, false); }
  entry* getRefUsing(cpindex* ix2)
                    { assert(ix == null); return getRefCommon(ix2, true); }
  entry* getRefCommon(cpindex* ix, bool nullOK);
  jlong  getLong(band& lo_band, bool have_hi);

  static jlong makeLong(uint hi, uint lo) {
    return ((julong)hi << 32) + (((julong)lo << 32) >> 32);
  }

  int    getIntTotal();
  int    getIntCount(int tag);

  static band* makeBands(unpacker* u);
  static void initIndexes(unpacker* u);

#ifndef PRODUCT
  void dump();
#endif

  void abort(const char* msg = null); //{ u->abort(msg); }
  bool aborting(); //{ return u->aborting(); }
};

extern band all_bands[];

#define BAND_LOCAL /* \
  band* band_temp = all_bands; \
  band* all_bands = band_temp */

// Band schema:
enum band_number {
  //e_archive_magic,
  //e_archive_header,
  //e_band_headers,

    // constant pool contents
    e_cp_Utf8_prefix,
    e_cp_Utf8_suffix,
    e_cp_Utf8_chars,
    e_cp_Utf8_big_suffix,
    e_cp_Utf8_big_chars,
    e_cp_Int,
    e_cp_Float,
    e_cp_Long_hi,
    e_cp_Long_lo,
    e_cp_Double_hi,
    e_cp_Double_lo,
    e_cp_String,
    e_cp_Class,
    e_cp_Signature_form,
    e_cp_Signature_classes,
    e_cp_Descr_name,
    e_cp_Descr_type,
    e_cp_Field_class,
    e_cp_Field_desc,
    e_cp_Method_class,
    e_cp_Method_desc,
    e_cp_Imethod_class,
    e_cp_Imethod_desc,
    e_cp_MethodHandle_refkind,
    e_cp_MethodHandle_member,
    e_cp_MethodType,
    e_cp_BootstrapMethod_ref,
    e_cp_BootstrapMethod_arg_count,
    e_cp_BootstrapMethod_arg,
    e_cp_InvokeDynamic_spec,
    e_cp_InvokeDynamic_desc,

    // bands which define transmission of attributes
    e_attr_definition_headers,
    e_attr_definition_name,
    e_attr_definition_layout,

    // band for hardwired InnerClasses attribute (shared across the package)
    e_ic_this_class,
    e_ic_flags,
    // These bands contain data only where flags sets ACC_IC_LONG_FORM:
    e_ic_outer_class,
    e_ic_name,

    // bands for carrying class schema information:
    e_class_this,
    e_class_super,
    e_class_interface_count,
    e_class_interface,

    // bands for class members
    e_class_field_count,
    e_class_method_count,

    e_field_descr,
    e_field_flags_hi,
    e_field_flags_lo,
    e_field_attr_count,
    e_field_attr_indexes,
    e_field_attr_calls,
    e_field_ConstantValue_KQ,
    e_field_Signature_RS,
    e_field_metadata_bands,
    e_field_attr_bands,

    e_method_descr,
    e_method_flags_hi,
    e_method_flags_lo,
    e_method_attr_count,
    e_method_attr_indexes,
    e_method_attr_calls,
    e_method_Exceptions_N,
    e_method_Exceptions_RC,
    e_method_Signature_RS,
    e_method_metadata_bands,
    e_method_MethodParameters_NB,
    e_method_MethodParameters_name_RUN,
    e_method_MethodParameters_flag_FH,
    e_method_attr_bands,

    e_class_flags_hi,
    e_class_flags_lo,
    e_class_attr_count,
    e_class_attr_indexes,
    e_class_attr_calls,
    e_class_SourceFile_RUN,
    e_class_EnclosingMethod_RC,
    e_class_EnclosingMethod_RDN,
    e_class_Signature_RS,
    e_class_metadata_bands,
    e_class_InnerClasses_N,
    e_class_InnerClasses_RC,
    e_class_InnerClasses_F,
    e_class_InnerClasses_outer_RCN,
    e_class_InnerClasses_name_RUN,
    e_class_ClassFile_version_minor_H,
    e_class_ClassFile_version_major_H,
    e_class_attr_bands,

    e_code_headers,
    e_code_max_stack,
    e_code_max_na_locals,
    e_code_handler_count,
    e_code_handler_start_P,
    e_code_handler_end_PO,
    e_code_handler_catch_PO,
    e_code_handler_class_RCN,

    // code attributes
    e_code_flags_hi,
    e_code_flags_lo,
    e_code_attr_count,
    e_code_attr_indexes,
    e_code_attr_calls,
    e_code_StackMapTable_N,
    e_code_StackMapTable_frame_T,
    e_code_StackMapTable_local_N,
    e_code_StackMapTable_stack_N,
    e_code_StackMapTable_offset,
    e_code_StackMapTable_T,
    e_code_StackMapTable_RC,
    e_code_StackMapTable_P,
    e_code_LineNumberTable_N,
    e_code_LineNumberTable_bci_P,
    e_code_LineNumberTable_line,
    e_code_LocalVariableTable_N,
    e_code_LocalVariableTable_bci_P,
    e_code_LocalVariableTable_span_O,
    e_code_LocalVariableTable_name_RU,
    e_code_LocalVariableTable_type_RS,
    e_code_LocalVariableTable_slot,
    e_code_LocalVariableTypeTable_N,
    e_code_LocalVariableTypeTable_bci_P,
    e_code_LocalVariableTypeTable_span_O,
    e_code_LocalVariableTypeTable_name_RU,
    e_code_LocalVariableTypeTable_type_RS,
    e_code_LocalVariableTypeTable_slot,
    e_code_attr_bands,

    // bands for bytecodes
    e_bc_codes,
    // remaining bands provide typed opcode fields required by the bc_codes

    e_bc_case_count,
    e_bc_case_value,
    e_bc_byte,
    e_bc_short,
    e_bc_local,
    e_bc_label,

    // ldc* operands:
    e_bc_intref,
    e_bc_floatref,
    e_bc_longref,
    e_bc_doubleref,
    e_bc_stringref,
    e_bc_loadablevalueref,
    e_bc_classref,

    e_bc_fieldref,
    e_bc_methodref,
    e_bc_imethodref,
    e_bc_indyref,

    // _self_linker_op family
    e_bc_thisfield,
    e_bc_superfield,
    e_bc_thismethod,
    e_bc_supermethod,

    // bc_invokeinit family:
    e_bc_initref,

    // bytecode escape sequences
    e_bc_escref,
    e_bc_escrefsize,
    e_bc_escsize,
    e_bc_escbyte,

    // file attributes and contents
    e_file_name,
    e_file_size_hi,
    e_file_size_lo,
    e_file_modtime,
    e_file_options,
    //e_file_bits,  // handled specially as an appendix

    BAND_LIMIT
};

// Symbolic names for bands, as if in a giant global struct:
//#define archive_magic all_bands[e_archive_magic]
//#define archive_header all_bands[e_archive_header]
//#define band_headers all_bands[e_band_headers]
#define cp_Utf8_prefix all_bands[e_cp_Utf8_prefix]
#define cp_Utf8_suffix all_bands[e_cp_Utf8_suffix]
#define cp_Utf8_chars all_bands[e_cp_Utf8_chars]
#define cp_Utf8_big_suffix all_bands[e_cp_Utf8_big_suffix]
#define cp_Utf8_big_chars all_bands[e_cp_Utf8_big_chars]
#define cp_Int all_bands[e_cp_Int]
#define cp_Float all_bands[e_cp_Float]
#define cp_Long_hi all_bands[e_cp_Long_hi]
#define cp_Long_lo all_bands[e_cp_Long_lo]
#define cp_Double_hi all_bands[e_cp_Double_hi]
#define cp_Double_lo all_bands[e_cp_Double_lo]
#define cp_String all_bands[e_cp_String]
#define cp_Class all_bands[e_cp_Class]
#define cp_Signature_form all_bands[e_cp_Signature_form]
#define cp_Signature_classes all_bands[e_cp_Signature_classes]
#define cp_Descr_name all_bands[e_cp_Descr_name]
#define cp_Descr_type all_bands[e_cp_Descr_type]
#define cp_Field_class all_bands[e_cp_Field_class]
#define cp_Field_desc all_bands[e_cp_Field_desc]
#define cp_Method_class all_bands[e_cp_Method_class]
#define cp_Method_desc all_bands[e_cp_Method_desc]
#define cp_Imethod_class all_bands[e_cp_Imethod_class]
#define cp_Imethod_desc all_bands[e_cp_Imethod_desc]
#define cp_MethodHandle_refkind all_bands[e_cp_MethodHandle_refkind]
#define cp_MethodHandle_member all_bands[e_cp_MethodHandle_member]
#define cp_MethodType all_bands[e_cp_MethodType]
#define cp_BootstrapMethod_ref all_bands[e_cp_BootstrapMethod_ref]
#define cp_BootstrapMethod_arg_count all_bands[e_cp_BootstrapMethod_arg_count]
#define cp_BootstrapMethod_arg all_bands[e_cp_BootstrapMethod_arg]
#define cp_InvokeDynamic_spec  all_bands[e_cp_InvokeDynamic_spec]
#define cp_InvokeDynamic_desc all_bands[e_cp_InvokeDynamic_desc]
#define attr_definition_headers all_bands[e_attr_definition_headers]
#define attr_definition_name all_bands[e_attr_definition_name]
#define attr_definition_layout all_bands[e_attr_definition_layout]
#define ic_this_class all_bands[e_ic_this_class]
#define ic_flags all_bands[e_ic_flags]
#define ic_outer_class all_bands[e_ic_outer_class]
#define ic_name all_bands[e_ic_name]
#define class_this all_bands[e_class_this]
#define class_super all_bands[e_class_super]
#define class_interface_count all_bands[e_class_interface_count]
#define class_interface all_bands[e_class_interface]
#define class_field_count all_bands[e_class_field_count]
#define class_method_count all_bands[e_class_method_count]
#define field_descr all_bands[e_field_descr]
#define field_flags_hi all_bands[e_field_flags_hi]
#define field_flags_lo all_bands[e_field_flags_lo]
#define field_attr_count all_bands[e_field_attr_count]
#define field_attr_indexes all_bands[e_field_attr_indexes]
#define field_ConstantValue_KQ all_bands[e_field_ConstantValue_KQ]
#define field_Signature_RS all_bands[e_field_Signature_RS]
#define field_attr_bands all_bands[e_field_attr_bands]
#define method_descr all_bands[e_method_descr]
#define method_flags_hi all_bands[e_method_flags_hi]
#define method_flags_lo all_bands[e_method_flags_lo]
#define method_attr_count all_bands[e_method_attr_count]
#define method_attr_indexes all_bands[e_method_attr_indexes]
#define method_Exceptions_N all_bands[e_method_Exceptions_N]
#define method_Exceptions_RC all_bands[e_method_Exceptions_RC]
#define method_Signature_RS all_bands[e_method_Signature_RS]
#define method_MethodParameters_NB all_bands[e_method_MethodParameters_NB]
#define method_MethodParameters_name_RUN all_bands[e_method_MethodParameters_name_RUN]
#define method_MethodParameters_flag_FH all_bands[e_method_MethodParameters_flag_FH]
#define method_attr_bands all_bands[e_method_attr_bands]
#define class_flags_hi all_bands[e_class_flags_hi]
#define class_flags_lo all_bands[e_class_flags_lo]
#define class_attr_count all_bands[e_class_attr_count]
#define class_attr_indexes all_bands[e_class_attr_indexes]
#define class_SourceFile_RUN all_bands[e_class_SourceFile_RUN]
#define class_EnclosingMethod_RC all_bands[e_class_EnclosingMethod_RC]
#define class_EnclosingMethod_RDN all_bands[e_class_EnclosingMethod_RDN]
#define class_Signature_RS all_bands[e_class_Signature_RS]
#define class_InnerClasses_N all_bands[e_class_InnerClasses_N]
#define class_InnerClasses_RC all_bands[e_class_InnerClasses_RC]
#define class_InnerClasses_F all_bands[e_class_InnerClasses_F]
#define class_InnerClasses_outer_RCN all_bands[e_class_InnerClasses_outer_RCN]
#define class_InnerClasses_name_RUN all_bands[e_class_InnerClasses_name_RUN]
#define class_ClassFile_version_minor_H all_bands[e_class_ClassFile_version_minor_H]
#define class_ClassFile_version_major_H all_bands[e_class_ClassFile_version_major_H]
#define class_attr_bands all_bands[e_class_attr_bands]
#define code_headers all_bands[e_code_headers]
#define code_max_stack all_bands[e_code_max_stack]
#define code_max_na_locals all_bands[e_code_max_na_locals]
#define code_handler_count all_bands[e_code_handler_count]
#define code_handler_start_P all_bands[e_code_handler_start_P]
#define code_handler_end_PO all_bands[e_code_handler_end_PO]
#define code_handler_catch_PO all_bands[e_code_handler_catch_PO]
#define code_handler_class_RCN all_bands[e_code_handler_class_RCN]
#define code_flags_hi all_bands[e_code_flags_hi]
#define code_flags_lo all_bands[e_code_flags_lo]
#define code_attr_count all_bands[e_code_attr_count]
#define code_attr_indexes all_bands[e_code_attr_indexes]
#define code_StackMapTable_N all_bands[e_code_StackMapTable_N]
#define code_StackMapTable_frame_T all_bands[e_code_StackMapTable_frame_T]
#define code_StackMapTable_local_N all_bands[e_code_StackMapTable_local_N]
#define code_StackMapTable_stack_N all_bands[e_code_StackMapTable_stack_N]
#define code_StackMapTable_offset all_bands[e_code_StackMapTable_offset]
#define code_StackMapTable_T all_bands[e_code_StackMapTable_T]
#define code_StackMapTable_RC all_bands[e_code_StackMapTable_RC]
#define code_StackMapTable_P all_bands[e_code_StackMapTable_P]
#define code_LineNumberTable_N all_bands[e_code_LineNumberTable_N]
#define code_LineNumberTable_bci_P all_bands[e_code_LineNumberTable_bci_P]
#define code_LineNumberTable_line all_bands[e_code_LineNumberTable_line]
#define code_LocalVariableTable_N all_bands[e_code_LocalVariableTable_N]
#define code_LocalVariableTable_bci_P all_bands[e_code_LocalVariableTable_bci_P]
#define code_LocalVariableTable_span_O all_bands[e_code_LocalVariableTable_span_O]
#define code_LocalVariableTable_name_RU all_bands[e_code_LocalVariableTable_name_RU]
#define code_LocalVariableTable_type_RS all_bands[e_code_LocalVariableTable_type_RS]
#define code_LocalVariableTable_slot all_bands[e_code_LocalVariableTable_slot]
#define code_LocalVariableTypeTable_N all_bands[e_code_LocalVariableTypeTable_N]
#define code_LocalVariableTypeTable_bci_P all_bands[e_code_LocalVariableTypeTable_bci_P]
#define code_LocalVariableTypeTable_span_O all_bands[e_code_LocalVariableTypeTable_span_O]
#define code_LocalVariableTypeTable_name_RU all_bands[e_code_LocalVariableTypeTable_name_RU]
#define code_LocalVariableTypeTable_type_RS all_bands[e_code_LocalVariableTypeTable_type_RS]
#define code_LocalVariableTypeTable_slot all_bands[e_code_LocalVariableTypeTable_slot]
#define code_attr_bands all_bands[e_code_attr_bands]
#define bc_codes all_bands[e_bc_codes]
#define bc_case_count all_bands[e_bc_case_count]
#define bc_case_value all_bands[e_bc_case_value]
#define bc_byte all_bands[e_bc_byte]
#define bc_short all_bands[e_bc_short]
#define bc_local all_bands[e_bc_local]
#define bc_label all_bands[e_bc_label]
#define bc_intref all_bands[e_bc_intref]
#define bc_floatref all_bands[e_bc_floatref]
#define bc_longref all_bands[e_bc_longref]
#define bc_doubleref all_bands[e_bc_doubleref]
#define bc_stringref all_bands[e_bc_stringref]
#define bc_loadablevalueref all_bands[e_bc_loadablevalueref]
#define bc_classref all_bands[e_bc_classref]
#define bc_fieldref all_bands[e_bc_fieldref]
#define bc_methodref all_bands[e_bc_methodref]
#define bc_imethodref all_bands[e_bc_imethodref]
#define bc_indyref all_bands[e_bc_indyref]
#define bc_thisfield all_bands[e_bc_thisfield]
#define bc_superfield all_bands[e_bc_superfield]
#define bc_thismethod all_bands[e_bc_thismethod]
#define bc_supermethod all_bands[e_bc_supermethod]
#define bc_initref all_bands[e_bc_initref]
#define bc_escref all_bands[e_bc_escref]
#define bc_escrefsize all_bands[e_bc_escrefsize]
#define bc_escsize all_bands[e_bc_escsize]
#define bc_escbyte all_bands[e_bc_escbyte]
#define file_name all_bands[e_file_name]
#define file_size_hi all_bands[e_file_size_hi]
#define file_size_lo all_bands[e_file_size_lo]
#define file_modtime all_bands[e_file_modtime]
#define file_options all_bands[e_file_options]

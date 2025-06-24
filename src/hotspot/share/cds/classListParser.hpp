/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CDS_CLASSLISTPARSER_HPP
#define SHARE_CDS_CLASSLISTPARSER_HPP

#include "utilities/exceptions.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/istream.hpp"
#include "utilities/resizeableResourceHash.hpp"

class constantPoolHandle;
class Thread;

class CDSIndyInfo {
  GrowableArray<const char*>* _items;
public:
  CDSIndyInfo() : _items(nullptr) {}
  void add_item(const char* item) {
    if (_items == nullptr) {
      _items = new GrowableArray<const char*>(9);
    }
    assert(_items != nullptr, "sanity");
    _items->append(item);
  }
  void add_ref_kind(int ref_kind) {
    switch (ref_kind) {
    case JVM_REF_getField         : _items->append("REF_getField"); break;
    case JVM_REF_getStatic        : _items->append("REF_getStatic"); break;
    case JVM_REF_putField         : _items->append("REF_putField"); break;
    case JVM_REF_putStatic        : _items->append("REF_putStatic"); break;
    case JVM_REF_invokeVirtual    : _items->append("REF_invokeVirtual"); break;
    case JVM_REF_invokeStatic     : _items->append("REF_invokeStatic"); break;
    case JVM_REF_invokeSpecial    : _items->append("REF_invokeSpecial"); break;
    case JVM_REF_newInvokeSpecial : _items->append("REF_newInvokeSpecial"); break;
    case JVM_REF_invokeInterface  : _items->append("REF_invokeInterface"); break;
    default                       : ShouldNotReachHere();
    }
  }
  GrowableArray<const char*>* items() {
    return _items;
  }
};

class ClassListParser : public StackObj {
  static const char* CONSTANT_POOL_TAG;
  static const char* LAMBDA_FORM_TAG;
  static const char* LAMBDA_PROXY_TAG;

public:
  enum ParseMode {
    _parse_all,
    _parse_lambda_forms_invokers_only,
  };

private:
  // Must be C_HEAP allocated -- we don't want nested resource allocations.
  typedef ResizeableResourceHashtable<int, InstanceKlass*,
                                      AnyObj::C_HEAP, mtClassShared> ID2KlassTable;

  enum {
    _unspecified      = -999,
  };

  // Use a small initial size in debug build to test resizing logic
  static const int INITIAL_TABLE_SIZE = DEBUG_ONLY(17) NOT_DEBUG(1987);
  static const int MAX_TABLE_SIZE = 61333;
  static volatile Thread* _parsing_thread; // the thread that created _instance
  static ClassListParser* _instance; // the singleton.
  const char* _classlist_file;

  ID2KlassTable _id2klass_table;

  FileInput           _file_input;
  inputStream         _input_stream;
  char*               _line;                  // The buffer that holds the current line. Some characters in
                                              // the buffer may be overwritten by '\0' during parsing.
  int                 _line_len;              // Original length of the input line.
  const char*         _class_name;
  GrowableArray<const char*>* _indy_items;    // items related to invoke dynamic for archiving lambda proxy classes
  int                 _id;
  int                 _super;
  GrowableArray<int>* _interfaces;
  bool                _interfaces_specified;
  const char*         _source;
  ParseMode           _parse_mode;

  bool parse_int_option(const char* option_name, int* value);
  bool parse_uint_option(const char* option_name, int* value);
  InstanceKlass* load_class_from_source(Symbol* class_name, TRAPS);
  ID2KlassTable* id2klass_table() {
    return &_id2klass_table;
  }
  InstanceKlass* lookup_class_by_id(int id);
  void print_specified_interfaces();
  void print_actual_interfaces(InstanceKlass *ik);
  bool is_matching_cp_entry(const constantPoolHandle &pool, int cp_index, TRAPS);

  InstanceKlass* find_builtin_class_helper(JavaThread* current, Symbol* class_name_symbol, oop class_loader_oop);
  InstanceKlass* find_builtin_class(JavaThread* current, const char* class_name);

  void resolve_indy(JavaThread* current, Symbol* class_name_symbol);
  void resolve_indy_impl(Symbol* class_name_symbol, TRAPS);
  void clean_up_input_line();
  void read_class_name_and_attributes();
  void parse_class_name_and_attributes(TRAPS);
  Klass* load_current_class(Symbol* class_name_symbol, TRAPS);
  void parse_constant_pool_tag();

  size_t lineno() { return _input_stream.lineno(); }
  FILE* do_open(const char* file);
  ClassListParser(const char* file, ParseMode _parse_mode);
  ~ClassListParser();
  void print_diagnostic_info(outputStream* st, const char* msg, va_list ap) ATTRIBUTE_PRINTF(3, 0);
  void print_diagnostic_info(outputStream* st, const char* msg, ...) ATTRIBUTE_PRINTF(3, 0);
  void constant_pool_resolution_warning(const char* msg, ...) ATTRIBUTE_PRINTF(2, 0);
  void error(const char* msg, ...) ATTRIBUTE_PRINTF(2, 0);
  GrowableArray<InstanceKlass*> get_specified_interfaces();
  void check_supertype_obstruction(int specified_supertype_id, const InstanceKlass* specified_supertype, TRAPS);

public:
  static void parse_classlist(const char* classlist_path, ParseMode parse_mode, TRAPS);

  static bool is_parsing_thread();
  static ClassListParser* instance() {
    assert(is_parsing_thread(), "call this only in the thread that created ClassListParsing::_instance");
    assert(_instance != nullptr, "must be");
    return _instance;
  }
  static const char* lambda_proxy_tag() {
    return LAMBDA_PROXY_TAG;
  }
  static const char* lambda_form_tag() {
    return LAMBDA_FORM_TAG;
  }

  void parse(TRAPS);
  void split_tokens_by_whitespace(int offset, GrowableArray<const char*>* items);
  int split_at_tag_from_line();
  void parse_at_tags(TRAPS);
  char* _token;
  void parse_int(int* value);
  void parse_uint(int* value);
  bool try_parse_uint(int* value);
  bool skip_token(const char* option_name);
  void skip_whitespaces();
  void skip_non_whitespaces();

  bool parse_lambda_forms_invokers_only() {
    return _parse_mode == _parse_lambda_forms_invokers_only;
  }
  bool is_id_specified() {
    return _id != _unspecified;
  }
  bool is_super_specified() {
    return _super != _unspecified;
  }
  bool are_interfaces_specified() {
    return _interfaces->length() > 0;
  }
  int id() {
    assert(is_id_specified(), "do not query unspecified id");
    return _id;
  }
  int super() {
    assert(is_super_specified(), "do not query unspecified super");
    return _super;
  }
  void check_already_loaded(const char* which, int id) {
    if (!id2klass_table()->contains(id)) {
      error("%s id %d is not yet loaded", which, id);
    }
  }
  void check_class_name(const char* class_name);

  const char* current_class_name() {
    return _class_name;
  }

  bool is_loading_from_source();
  static void populate_cds_indy_info(const constantPoolHandle &pool, int cp_index, CDSIndyInfo* cii, TRAPS);
};
#endif // SHARE_CDS_CLASSLISTPARSER_HPP

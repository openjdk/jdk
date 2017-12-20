/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_MEMORY_CLASSLISTPARSER_HPP
#define SHARE_VM_MEMORY_CLASSLISTPARSER_HPP

#include "utilities/exceptions.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/hashtable.hpp"

class CDSClassInfo;

// Look up from ID -> InstanceKlass*
class ID2KlassTable : public Hashtable<InstanceKlass*, mtClass> {
public:
  ID2KlassTable() : Hashtable<InstanceKlass*, mtClass>(1987, sizeof(HashtableEntry<InstanceKlass*, mtClass>)) { }
  void add(int id, InstanceKlass* klass) {
    unsigned int hash = (unsigned int)id;
    HashtableEntry<InstanceKlass*, mtClass>* entry = new_entry(hash, klass);
    add_entry(hash_to_index(hash), entry);
  }

  InstanceKlass* lookup(int id) {
    unsigned int hash = (unsigned int)id;
    int index = hash_to_index(id);
    for (HashtableEntry<InstanceKlass*, mtClass>* e = bucket(index); e != NULL; e = e->next()) {
      if (e->hash() == hash) {
        return e->literal();
      }
    }
    return NULL;
  }
};

class ClassListParser : public StackObj {
  enum {
    _unspecified      = -999,

    // Max number of bytes allowed per line in the classlist.
    // Theoretically Java class names could be 65535 bytes in length. Also, an input line
    // could have a very long path name up to JVM_MAXPATHLEN bytes in length. In reality,
    // 4K bytes is more than enough.
    _max_allowed_line_len = 4096,
    _line_buf_extra       = 10, // for detecting input too long
    _line_buf_size        = _max_allowed_line_len + _line_buf_extra
  };

  static ClassListParser* _instance; // the singleton.
  const char* _classlist_file;
  FILE* _file;

  ID2KlassTable _id2klass_table;

  // The following field contains information from the *current* line being
  // parsed.
  char                _line[_line_buf_size];  // The buffer that holds the current line. Some characters in
                                              // the buffer may be overwritten by '\0' during parsing.
  int                 _line_len;              // Original length of the input line.
  int                 _line_no;               // Line number for current line being parsed
  const char*         _class_name;
  int                 _id;
  int                 _super;
  GrowableArray<int>* _interfaces;
  bool                _interfaces_specified;
  const char*         _source;

  bool parse_int_option(const char* option_name, int* value);
  InstanceKlass* load_class_from_source(Symbol* class_name, TRAPS);
  ID2KlassTable *table() {
    return &_id2klass_table;
  }
  InstanceKlass* lookup_class_by_id(int id);
  void print_specified_interfaces();
  void print_actual_interfaces(InstanceKlass *ik);
public:
  ClassListParser(const char* file);
  ~ClassListParser();

  static ClassListParser* instance() {
    return _instance;
  }
  bool parse_one_line();
  char* _token;
  void error(const char* msg, ...);
  void parse_int(int* value);
  bool try_parse_int(int* value);
  bool skip_token(const char* option_name);
  void skip_whitespaces();
  void skip_non_whitespaces();

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
    if (_id2klass_table.lookup(id) == NULL) {
      error("%s id %d is not yet loaded", which, id);
    }
  }

  const char* current_class_name() {
    return _class_name;
  }

  Klass* load_current_class(TRAPS);

  bool is_loading_from_source();

  // Look up the super or interface of the current class being loaded
  // (in this->load_current_class()).
  InstanceKlass* lookup_super_for_current_class(Symbol* super_name);
  InstanceKlass* lookup_interface_for_current_class(Symbol* interface_name);
};
#endif

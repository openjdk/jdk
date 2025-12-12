/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/classLoaderData.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/classPrinter.hpp"
#include "memory/iterator.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "oops/method.hpp"
#include "oops/symbol.hpp"
#include "utilities/ostream.hpp"
#include "utilities/growableArray.hpp"

class ClassPrinter::KlassPrintClosure : public LockedClassesDo {
  const char* _class_name_pattern;
  const char* _method_name_pattern;
  const char* _method_signature_pattern;
  bool _always_print_class_name;
  int _flags;
  outputStream* _st;
  int _num;
  bool _has_printed_methods;
  GrowableArray<InstanceKlass*> _klasses;

public:
  KlassPrintClosure(const char* class_name_pattern,
                    const char* method_name_pattern,
                    const char* method_signature_pattern,
                    bool always_print_class_name,
                    int flags, outputStream* st)
    : _always_print_class_name(always_print_class_name),
      _flags(flags), _st(st), _num(0), _has_printed_methods(false)
  {
    if (has_mode(_flags, PRINT_METHOD_HANDLE)) {
      _flags |= (PRINT_METHOD_NAME | PRINT_BYTECODE);
    }
    if (has_mode(_flags, PRINT_DYNAMIC)) {
      _flags |= (PRINT_METHOD_NAME | PRINT_BYTECODE);
    }
    if (has_mode(_flags, PRINT_BYTECODE_ADDR)) {
      _flags |= (PRINT_METHOD_NAME | PRINT_BYTECODE);
    }
    if (has_mode(_flags, PRINT_BYTECODE)) {
      _flags |= (PRINT_METHOD_NAME);
    }

    if (has_mode(_flags, PRINT_CLASS_DETAILS)) {
      _always_print_class_name = true;
    }

    _class_name_pattern = copy_pattern(class_name_pattern);
    _method_name_pattern = copy_pattern(method_name_pattern);
    _method_signature_pattern = copy_pattern(method_signature_pattern);
  }

  static const char* copy_pattern(const char* pattern) {
    if (pattern == nullptr) {
      return nullptr;
    }
    char* copy = ResourceArea::strdup(pattern);
    for (char* p = copy; *p; p++) {
      if (*p == '.') {
        *p = '/';
      }
    }
    return copy;
  }

  virtual void do_klass(Klass* k) {
    if (!k->is_instance_klass()) {
      return;
    }
    InstanceKlass* ik = InstanceKlass::cast(k);
    if (ik->is_loaded() && ik->name()->is_star_match(_class_name_pattern)) {
      _klasses.append(ik);
    }
  }

  void print() {
    _klasses.sort(compare_klasses_alphabetically);
    for (int i = 0; i < _klasses.length(); i++) {
      print_instance_klass(_klasses.at(i));
    }
  }


  static bool match(const char* pattern, Symbol* sym) {
    return (pattern == nullptr || sym->is_star_match(pattern));
  }

  static int compare_klasses_alphabetically(InstanceKlass** a, InstanceKlass** b) {
    return compare_symbols_alphabetically((*a)->name(), (*b)->name());
  }

  static int compare_methods_alphabetically(const void* a, const void* b) {
    Method* ma = *(Method**)a;
    Method* mb = *(Method**)b;
    int n = compare_symbols_alphabetically(ma->name(), mb->name());
    if (n == 0) {
      n = compare_symbols_alphabetically(ma->signature(), mb->signature());
    }
    return n;
  }

  static int compare_symbols_alphabetically(Symbol* a, Symbol *b) {
    if (a == b) {
      return 0;
    }
    if (a != nullptr && b == nullptr) {
      return 1;
    }
    if (a == nullptr && b != nullptr) {
      return -1;
    }

    return strcmp(a->as_C_string(), b->as_C_string());
  }

  void print_klass_name(InstanceKlass* ik) {
    _st->print("[%3d] " INTPTR_FORMAT " class: %s mirror: " INTPTR_FORMAT " ", _num++,
               p2i(ik), ik->name()->as_C_string(), p2i(ik->java_mirror()));
    ik->class_loader_data()->print_value_on(_st);
    _st->cr();
  }

  void print_instance_klass(InstanceKlass* ik) {
    ResourceMark rm;
    if (_has_printed_methods) {
      // We have printed some methods in the previous class.
      // Print a new line to separate the two classes
      _st->cr();
    }
    _has_printed_methods = false;
    if (_always_print_class_name) {
      print_klass_name(ik);
    }

    if (has_mode(_flags, ClassPrinter::PRINT_CLASS_DETAILS)) {
      _st->print("InstanceKlass: ");
      ik->print_on(_st);
      oop mirror = ik->java_mirror();
      if (mirror != nullptr) {
        _st->print("\nJava mirror oop for %s: ", ik->name()->as_C_string());
        mirror->print_on(_st);
      }
    }

    if (has_mode(_flags, ClassPrinter::PRINT_METHOD_NAME)) {
      bool print_codes = has_mode(_flags, ClassPrinter::PRINT_BYTECODE);
      int len = ik->methods()->length();
      int num_methods_printed = 0;

      Method** sorted_methods = NEW_RESOURCE_ARRAY(Method*, len);
      for (int index = 0; index < len; index++) {
        sorted_methods[index] = ik->methods()->at(index);
      }

      qsort(sorted_methods, len, sizeof(Method*), compare_methods_alphabetically);

      for (int index = 0; index < len; index++) {
        Method* m = sorted_methods[index];
        if (match(_method_name_pattern, m->name()) &&
            match(_method_signature_pattern, m->signature())) {
          if (print_codes && num_methods_printed++ > 0) {
            _st->cr();
          }

          if (_has_printed_methods == false) {
            if (!_always_print_class_name) {
              print_klass_name(ik);
            }
            _has_printed_methods = true;
          }
          print_method(m);
        }
      }
    }
  }

  void print_method(Method* m) {
    _st->print_cr(INTPTR_FORMAT " %smethod %s : %s", p2i(m),
                  m->is_static() ? "static " : "",
                  m->name()->as_C_string(), m->signature()->as_C_string());

    if (has_mode(_flags, ClassPrinter::PRINT_METHOD_DETAILS)) {
      m->print_on(_st);
    }

    if (has_mode(_flags, ClassPrinter::PRINT_BYTECODE)) {
      m->print_codes_on(_st, _flags);
    }
  }
};

void ClassPrinter::print_flags_help(outputStream* os) {
  os->print_cr("flags (bitmask):");
  os->print_cr("   0x%02x  - print names of methods", PRINT_METHOD_NAME);
  os->print_cr("   0x%02x  - print bytecodes", PRINT_BYTECODE);
  os->print_cr("   0x%02x  - print the address of bytecodes", PRINT_BYTECODE_ADDR);
  os->print_cr("   0x%02x  - print info for invokedynamic", PRINT_DYNAMIC);
  os->print_cr("   0x%02x  - print info for invokehandle",  PRINT_METHOD_HANDLE);
  os->print_cr("   0x%02x  - print details of the C++ and Java objects that represent classes",  PRINT_CLASS_DETAILS);
  os->print_cr("   0x%02x  - print details of the C++ objects that represent methods",  PRINT_METHOD_DETAILS);
  os->cr();
}

void ClassPrinter::print_classes(const char* class_name_pattern, int flags, outputStream* os) {
  ResourceMark rm;
  KlassPrintClosure closure(class_name_pattern, nullptr, nullptr, true, flags, os);
  ClassLoaderDataGraph::classes_do(&closure);
  closure.print();
}

void ClassPrinter::print_methods(const char* class_name_pattern,
                                 const char* method_pattern, int flags, outputStream* os) {
  ResourceMark rm;
  const char* method_name_pattern;
  const char* method_signature_pattern;

  const char* colon = strchr(method_pattern, ':');
  if (colon == nullptr) {
    method_name_pattern = method_pattern;
    method_signature_pattern = nullptr;
  } else {
    ptrdiff_t name_pat_len = colon - method_pattern;
    assert(name_pat_len >= 0, "sanity");
    char* buf = NEW_RESOURCE_ARRAY(char, name_pat_len + 1);
    strncpy(buf, method_pattern, name_pat_len);
    buf[name_pat_len] = 0;

    method_name_pattern = buf;
    method_signature_pattern = colon + 1;
  }

  KlassPrintClosure closure(class_name_pattern, method_name_pattern, method_signature_pattern,
                            false, flags | PRINT_METHOD_NAME, os);
  ClassLoaderDataGraph::classes_do(&closure);
  closure.print();
}

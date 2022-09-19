/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "classfile/classLoaderData.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/classPrinter.hpp"
#include "memory/iterator.hpp"
#include "memory/resourceArea.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.hpp"
#include "oops/method.hpp"
#include "oops/symbol.hpp"
#include "utilities/ostream.hpp"

class ClassPrinter::KlassPrintClosure : public LockedClassesDo {
  const char* _class_name_pattern;
  const char* _method_name_pattern;
  const char* _method_signature_pattern;
  int _flags;
  outputStream* _st;
  int _num;
  bool _last_printed_methods;
public:
  KlassPrintClosure(const char* class_name_pattern,
                    const char* method_name_pattern,
                    const char* method_signature_pattern,
                    int flags, outputStream* st)
    : _class_name_pattern(class_name_pattern),
      _method_name_pattern(method_name_pattern),
      _method_signature_pattern(method_signature_pattern),
      _flags(flags), _st(st), _num(0), _last_printed_methods(false)
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
  }

  virtual void do_klass(Klass* k) {
    if (!k->is_instance_klass()) {
      return;
    }
    print_instance_klass(InstanceKlass::cast(k));
  }

  void print_instance_klass(InstanceKlass* ik) {
    if (ik->is_loaded() && matches(_class_name_pattern, ik->name())) {
      ResourceMark rm;
      if (_last_printed_methods) {
        _st->cr();
      }
      _last_printed_methods = false;
      _st->print("[%3d] " INTPTR_FORMAT " class %s ", _num++, p2i(ik), ik->name()->as_C_string());
      ik->class_loader_data()->print_value_on(_st);
      _st->cr();

      if (has_mode(_flags, ClassPrinter::PRINT_METHOD_NAME)) {
        bool print_codes = has_mode(_flags, ClassPrinter::PRINT_BYTECODE);
        int len = ik->methods()->length();
        int num_methods_printed = 0;

        for (int index = 0; index < len; index++) {
          Method* m = ik->methods()->at(index);
          if (_method_name_pattern != NULL &&
              !matches(_method_name_pattern, m->name())) {
            continue;
          }
          if (_method_signature_pattern != NULL &&
              !matches(_method_signature_pattern, m->signature())) {
            continue;
          }
          if (print_codes && num_methods_printed++ > 0) {
            _st->cr();
          }
          print_method(m);
          _last_printed_methods = true;
        }
      }
    }
  }

  void print_method(Method* m) {
    bool print_codes = has_mode(_flags, ClassPrinter::PRINT_BYTECODE);
    _st->print_cr(INTPTR_FORMAT " %smethod %s : %s", p2i(m),
                  m->is_static() ? "static " : "",
                  m->name()->as_C_string(), m->signature()->as_C_string());
    if (print_codes) {
      m->print_codes_on(_st, _flags);
    }
  }
};

bool ClassPrinter::matches(const char *pattern, const char *candidate, int p, int c) {
  if (pattern[p] == '\0') {
    return candidate[c] == '\0';
  } else if (pattern[p] == '*') {
    for (; candidate[c] != '\0'; c++) {
      if (matches(pattern, candidate, p+1, c))
        return true;
    }
    return matches(pattern, candidate, p+1, c);
  } else if (pattern[p] != '?' && pattern[p] != candidate[c]) {
    return false;
  }  else {
    return matches(pattern, candidate, p+1, c+1);
  }
}

bool ClassPrinter::matches(const char* pattern, Symbol* symbol) {
  if (pattern == NULL) {
    return true;
  }
  if (strchr(pattern, '*') == NULL) {
    return symbol->equals(pattern);
  } else {
    ResourceMark rm;
    char* buf = symbol->as_C_string();
    return matches(pattern, buf, 0, 0);
  }
}

void ClassPrinter::print_help() {
  tty->print_cr("flags (bitmask):");
  tty->print_cr("   0x%02x  - print names of methods", PRINT_METHOD_NAME);
  tty->print_cr("   0x%02x  - print bytecodes", PRINT_BYTECODE);
  tty->print_cr("   0x%02x  - print the address of bytecodes", PRINT_BYTECODE_ADDR);
  tty->print_cr("   0x%02x  - print info for invokedynamic", PRINT_DYNAMIC);
  tty->print_cr("   0x%02x  - print info for invokehandle",  PRINT_METHOD_HANDLE);
  tty->cr();
}

void ClassPrinter::print_classes(const char* class_name_pattern, int flags) {
  print_help();
  KlassPrintClosure closure(class_name_pattern, NULL, NULL, flags, tty);
  ClassLoaderDataGraph::classes_do(&closure);
}

void ClassPrinter::print_methods(const char* class_name_pattern,
                                 const char* method_name_pattern, int flags) {
  print_help();
  KlassPrintClosure closure(class_name_pattern, method_name_pattern, NULL,
                            flags | PRINT_METHOD_NAME, tty);
  ClassLoaderDataGraph::classes_do(&closure);

}

void ClassPrinter::print_methods(const char* class_name_pattern,
                                 const char* method_name_pattern,
                                 const char* method_signature_pattern, int flags) {
  print_help();
  KlassPrintClosure closure(class_name_pattern, method_name_pattern, method_signature_pattern,
                            flags | PRINT_METHOD_NAME, tty);
  ClassLoaderDataGraph::classes_do(&closure);
}

void ClassPrinter::print_class(InstanceKlass* k, int flags) {
  print_help();
  KlassPrintClosure closure(NULL, NULL, NULL, flags, tty);
  closure.print_instance_klass(k);
}

void ClassPrinter::print_method(Method* m, int flags) {
  print_help();
  KlassPrintClosure closure(NULL, "", "", flags | PRINT_METHOD_NAME | PRINT_BYTECODE, tty);
  closure.print_instance_klass(m->method_holder());
  closure.print_method(m);
}

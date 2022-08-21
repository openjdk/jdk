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

class ClassPrinter::KlassPrintClosure : public KlassClosure {
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
      _flags(flags), _st(st), _num(0), _last_printed_methods(false) {}

  virtual void do_klass(Klass* k) {
    if (!k->is_instance_klass()) {
      return;
    }

    InstanceKlass* ik = InstanceKlass::cast(k);

    if (ik->is_loaded() && matches(_class_name_pattern, k->name())) {
      ResourceMark rm;
      if (_last_printed_methods) {
        _st->cr();
      }
      _last_printed_methods = false;
      _st->print("[%d] " INTPTR_FORMAT " %s, ", _num++, p2i(k), ik->external_name());
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
          _st->print_cr(INTPTR_FORMAT " %s : %s", p2i(m),
                        m->name()->as_C_string(), m->signature()->as_C_string());
          if (print_codes) {
            m->print_codes_on(_st, _flags);
          }
          _last_printed_methods = true;
        }
      }
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
  if (strchr(pattern, '*') == NULL) {
    return symbol->equals(pattern);
  } else {
    ResourceMark rm;
    char* buf = symbol->as_C_string();
    return matches(pattern, buf, 0, 0);
  }
}

void ClassPrinter::print_classes_unlocked(const char* class_name_pattern, int flags) {
  KlassPrintClosure closure(class_name_pattern, NULL, NULL, flags, tty);
  ClassLoaderDataGraph::classes_do(&closure);
}

void ClassPrinter::print_methods_unlocked(const char* class_name_pattern,
                                          const char* method_name_pattern, int flags) {
  KlassPrintClosure closure(class_name_pattern, method_name_pattern, NULL,
                            flags | PRINT_METHOD_NAME, tty);
  ClassLoaderDataGraph::classes_do(&closure);

}

void ClassPrinter::print_methods_unlocked(const char* class_name_pattern,
                                          const char* method_name_pattern,
                                          const char* method_signature_pattern, int flags) {
  KlassPrintClosure closure(class_name_pattern, method_name_pattern, method_signature_pattern,
                            flags | PRINT_METHOD_NAME, tty);
  ClassLoaderDataGraph::classes_do(&closure);
}

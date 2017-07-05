/*
 * Copyright 1999-2001 Sun Microsystems, Inc.  All Rights Reserved.
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

// ciSymbolKlass
//
// This class represents a klassOop in the HotSpot virtual machine
// whose Klass part in a symbolKlass.  Although, in the VM
// Klass hierarchy, symbolKlass is a direct subclass of typeArrayKlass,
// we do not model this relationship in the ciObject hierarchy -- the
// subclassing is used to share implementation and is not of note
// to compiler writers.
class ciSymbolKlass : public ciKlass {
  CI_PACKAGE_ACCESS

protected:
  ciSymbolKlass(KlassHandle h_k)
    : ciKlass(h_k, ciSymbol::make("unique_symbolKlass")) {
    assert(get_Klass()->oop_is_symbol(), "wrong type");
  }

  symbolKlass* get_symbolKlass() { return (symbolKlass*)get_Klass(); }

  const char* type_string() { return "ciSymbolKlass"; }

public:
  // What kind of ciObject is this?
  bool is_symbol_klass() { return true; }

  // Return the distinguished ciSymbolKlass instance.
  static ciSymbolKlass* make();
};

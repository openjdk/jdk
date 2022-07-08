/*
 * Copyright (c) 1997, 2022, Oracle and/or its affiliates. All rights reserved.
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

bool key_comparison(InvokeMethodKey k1, InvokeMethodKey k2){
    if (k1._symbol == k2._symbol && k1._symbol_mode == k2._symbol_mode) {
        return true;
    } else {
        return false;
    }
}

unsigned int compute_hash(Symbol* sym, intptr_t symbol_mode) {
    unsigned int hash = (unsigned int) name->identity_hash();
    return hash ^ symbol_mode;
}

//key for the _invoke_method_table, which contains the symbol and its intrisic id
class InvokeMethodKey : public StackObj {
  private:
    Symbol* _symbol;
    intptr_t _iid;

  public:
    InvokeMethodKey(Symbol* symbol, intptr_t symbol_mode) :
        _symbol(symbol),
        _symbol_mode(symbol_mode) {}

    static bool key_comparison(InvokeMethodKey const &k1, InvokeMethodKey const &k2){
        return k1._symbol == k2._symbol && k1._symbol_mode == k2._symbol_mode;
    }  

    static unsigned int compute_hash(Symbol* sym, intptr_t symbol_mode) {
        unsigned int hash = (unsigned int) name->identity_hash();
        return hash ^ symbol_mode;
    }

}

class InvokeMethodValue : public StackObj {
 private:
  Method*   _method;
  OopHandle _method_type;

  public:
    InvokeMethodValue(Methods* method, oop p) :
        _method(methods),
        _method_type(OopHandle(Universe::vm_global(), p)) {}

}

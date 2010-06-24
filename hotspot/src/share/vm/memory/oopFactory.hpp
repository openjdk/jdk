/*
 * Copyright (c) 1997, 2009, Oracle and/or its affiliates. All rights reserved.
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

// oopFactory is a class used for creating new objects.

class vframeArray;

class oopFactory: AllStatic {
 public:
  // Basic type leaf array allocation
  static typeArrayOop    new_boolArray  (int length, TRAPS) { return typeArrayKlass::cast(Universe::boolArrayKlassObj  ())->allocate(length, CHECK_NULL); }
  static typeArrayOop    new_charArray  (int length, TRAPS) { return typeArrayKlass::cast(Universe::charArrayKlassObj  ())->allocate(length, CHECK_NULL); }
  static typeArrayOop    new_singleArray(int length, TRAPS) { return typeArrayKlass::cast(Universe::singleArrayKlassObj())->allocate(length, CHECK_NULL); }
  static typeArrayOop    new_doubleArray(int length, TRAPS) { return typeArrayKlass::cast(Universe::doubleArrayKlassObj())->allocate(length, CHECK_NULL); }
  static typeArrayOop    new_byteArray  (int length, TRAPS) { return typeArrayKlass::cast(Universe::byteArrayKlassObj  ())->allocate(length, CHECK_NULL); }
  static typeArrayOop    new_shortArray (int length, TRAPS) { return typeArrayKlass::cast(Universe::shortArrayKlassObj ())->allocate(length, CHECK_NULL); }
  static typeArrayOop    new_intArray   (int length, TRAPS) { return typeArrayKlass::cast(Universe::intArrayKlassObj   ())->allocate(length, CHECK_NULL); }
  static typeArrayOop    new_longArray  (int length, TRAPS) { return typeArrayKlass::cast(Universe::longArrayKlassObj  ())->allocate(length, CHECK_NULL); }

  // create java.lang.Object[]
  static objArrayOop     new_objectArray(int length, TRAPS)  {
    return objArrayKlass::
      cast(Universe::objectArrayKlassObj())->allocate(length, CHECK_NULL);
  }

  static typeArrayOop    new_charArray           (const char* utf8_str,  TRAPS);
  static typeArrayOop    new_permanent_charArray (int length, TRAPS);
  static typeArrayOop    new_permanent_byteArray (int length, TRAPS);  // used for class file structures
  static typeArrayOop    new_permanent_shortArray(int length, TRAPS);  // used for class file structures
  static typeArrayOop    new_permanent_intArray  (int length, TRAPS);  // used for class file structures

  static typeArrayOop    new_typeArray(BasicType type, int length, TRAPS);

  // Symbols
  static symbolOop new_symbol(const char* utf8_buffer, int length, TRAPS) {
    assert(utf8_buffer != NULL, "just checking");
    return SymbolTable::lookup(utf8_buffer, length, CHECK_NULL);
  }
  static void new_symbols(constantPoolHandle cp, int names_count,
                          const char** name, int* lengths,
                          int* cp_indices, unsigned int* hashValues,
                          TRAPS) {
    SymbolTable::add(cp, names_count, name, lengths, cp_indices,
                     hashValues, CHECK);
  }

  static symbolOop       new_symbol(char* name, TRAPS) { return new_symbol(name, (int)strlen(name), CHECK_NULL); }
  static symbolOop       new_symbol(const char* name, TRAPS) { return new_symbol(name, (int)strlen(name), CHECK_NULL); }
  static symbolOop       new_symbol(symbolHandle sym, int begin, int end, TRAPS) {
    assert(begin <= end && end <= sym->utf8_length(), "just checking");
    return SymbolTable::lookup(sym, begin, end, CHECK_NULL);
  }

  // Create symbols as above but return a handle
  static symbolHandle    new_symbol_handle(const char* name, int length, TRAPS) {
    symbolOop sym = new_symbol(name, length, THREAD);
    return symbolHandle(THREAD, sym);
  }
  static symbolHandle    new_symbol_handle(char* name, TRAPS) { return new_symbol_handle(name, (int)strlen(name), CHECK_(symbolHandle())); }
  static symbolHandle    new_symbol_handle(const char* name, TRAPS) { return new_symbol_handle(name, (int)strlen(name), CHECK_(symbolHandle())); }

  // Constant pools
  static constantPoolOop      new_constantPool     (int length,
                                                    bool is_conc_safe,
                                                    TRAPS);
  static constantPoolCacheOop new_constantPoolCache(int length,
                                                    bool is_conc_safe,
                                                    TRAPS);

  // Instance classes
  static klassOop        new_instanceKlass(int vtable_len, int itable_len,
                                           int static_field_size,
                                           unsigned int nonstatic_oop_map_count,
                                           ReferenceType rt, TRAPS);

  // Methods
private:
  static constMethodOop  new_constMethod(int byte_code_size,
                                         int compressed_line_number_size,
                                         int localvariable_table_length,
                                         int checked_exceptions_length,
                                         bool is_conc_safe,
                                         TRAPS);
public:
  // Set is_conc_safe for methods which cannot safely be
  // processed by concurrent GC even after the return of
  // the method.
  static methodOop       new_method(int byte_code_size,
                                    AccessFlags access_flags,
                                    int compressed_line_number_size,
                                    int localvariable_table_length,
                                    int checked_exceptions_length,
                                    bool is_conc_safe,
                                    TRAPS);

  // Method Data containers
  static methodDataOop   new_methodData(methodHandle method, TRAPS);

  // System object arrays
  static objArrayOop     new_system_objArray(int length, TRAPS);

  // Regular object arrays
  static objArrayOop     new_objArray(klassOop klass, int length, TRAPS);

  // For compiled ICs
  static compiledICHolderOop new_compiledICHolder(methodHandle method, KlassHandle klass, TRAPS);
};

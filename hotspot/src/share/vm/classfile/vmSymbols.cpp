/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/vmSymbols.hpp"
#include "memory/oopFactory.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "utilities/xmlstream.hpp"


Symbol* vmSymbols::_symbols[vmSymbols::SID_LIMIT];

Symbol* vmSymbols::_type_signatures[T_VOID+1] = { NULL /*, NULL...*/ };

inline int compare_symbol(Symbol* a, Symbol* b) {
  if (a == b)  return 0;
  // follow the natural address order:
  return (address)a > (address)b ? +1 : -1;
}

static vmSymbols::SID vm_symbol_index[vmSymbols::SID_LIMIT];
extern "C" {
  static int compare_vmsymbol_sid(const void* void_a, const void* void_b) {
    Symbol* a = vmSymbols::symbol_at(*((vmSymbols::SID*) void_a));
    Symbol* b = vmSymbols::symbol_at(*((vmSymbols::SID*) void_b));
    return compare_symbol(a, b);
  }
}

#ifdef ASSERT
#define VM_SYMBOL_ENUM_NAME_BODY(name, string) #name "\0"
static const char* vm_symbol_enum_names =
  VM_SYMBOLS_DO(VM_SYMBOL_ENUM_NAME_BODY, VM_ALIAS_IGNORE)
  "\0";
static const char* vm_symbol_enum_name(vmSymbols::SID sid) {
  const char* string = &vm_symbol_enum_names[0];
  int skip = (int)sid - (int)vmSymbols::FIRST_SID;
  for (; skip != 0; skip--) {
    size_t skiplen = strlen(string);
    if (skiplen == 0)  return "<unknown>";  // overflow
    string += skiplen+1;
  }
  return string;
}
#endif //ASSERT

// Put all the VM symbol strings in one place.
// Makes for a more compact libjvm.
#define VM_SYMBOL_BODY(name, string) string "\0"
static const char* vm_symbol_bodies = VM_SYMBOLS_DO(VM_SYMBOL_BODY, VM_ALIAS_IGNORE);

void vmSymbols::initialize(TRAPS) {
  assert((int)SID_LIMIT <= (1<<log2_SID_LIMIT), "must fit in this bitfield");
  assert((int)SID_LIMIT*5 > (1<<log2_SID_LIMIT), "make the bitfield smaller, please");
  assert(vmIntrinsics::FLAG_LIMIT <= (1 << vmIntrinsics::log2_FLAG_LIMIT), "must fit in this bitfield");

  if (!UseSharedSpaces) {
    const char* string = &vm_symbol_bodies[0];
    for (int index = (int)FIRST_SID; index < (int)SID_LIMIT; index++) {
      Symbol* sym = SymbolTable::new_permanent_symbol(string, CHECK);
      _symbols[index] = sym;
      string += strlen(string); // skip string body
      string += 1;              // skip trailing null
    }

    _type_signatures[T_BYTE]    = byte_signature();
    _type_signatures[T_CHAR]    = char_signature();
    _type_signatures[T_DOUBLE]  = double_signature();
    _type_signatures[T_FLOAT]   = float_signature();
    _type_signatures[T_INT]     = int_signature();
    _type_signatures[T_LONG]    = long_signature();
    _type_signatures[T_SHORT]   = short_signature();
    _type_signatures[T_BOOLEAN] = bool_signature();
    _type_signatures[T_VOID]    = void_signature();
    // no single signatures for T_OBJECT or T_ARRAY
  }

#ifdef ASSERT
  // Check for duplicates:
  for (int i1 = (int)FIRST_SID; i1 < (int)SID_LIMIT; i1++) {
    Symbol* sym = symbol_at((SID)i1);
    for (int i2 = (int)FIRST_SID; i2 < i1; i2++) {
      if (symbol_at((SID)i2) == sym) {
        tty->print("*** Duplicate VM symbol SIDs %s(%d) and %s(%d): \"",
                   vm_symbol_enum_name((SID)i2), i2,
                   vm_symbol_enum_name((SID)i1), i1);
        sym->print_symbol_on(tty);
        tty->print_cr("\"");
      }
    }
  }
#endif //ASSERT

  // Create an index for find_id:
  {
    for (int index = (int)FIRST_SID; index < (int)SID_LIMIT; index++) {
      vm_symbol_index[index] = (SID)index;
    }
    int num_sids = SID_LIMIT-FIRST_SID;
    qsort(&vm_symbol_index[FIRST_SID], num_sids, sizeof(vm_symbol_index[0]),
          compare_vmsymbol_sid);
  }

#ifdef ASSERT
  {
    // Spot-check correspondence between strings, symbols, and enums:
    assert(_symbols[NO_SID] == NULL, "must be");
    const char* str = "java/lang/Object";
    TempNewSymbol jlo = SymbolTable::new_permanent_symbol(str, CHECK);
    assert(strncmp(str, (char*)jlo->base(), jlo->utf8_length()) == 0, "");
    assert(jlo == java_lang_Object(), "");
    SID sid = VM_SYMBOL_ENUM_NAME(java_lang_Object);
    assert(find_sid(jlo) == sid, "");
    assert(symbol_at(sid) == jlo, "");

    // Make sure find_sid produces the right answer in each case.
    for (int index = (int)FIRST_SID; index < (int)SID_LIMIT; index++) {
      Symbol* sym = symbol_at((SID)index);
      sid = find_sid(sym);
      assert(sid == (SID)index, "symbol index works");
      // Note:  If there are duplicates, this assert will fail.
      // A "Duplicate VM symbol" message will have already been printed.
    }

    // The string "format" happens (at the moment) not to be a vmSymbol,
    // though it is a method name in java.lang.String.
    str = "format";
    TempNewSymbol fmt = SymbolTable::new_permanent_symbol(str, CHECK);
    sid = find_sid(fmt);
    assert(sid == NO_SID, "symbol index works (negative test)");
  }
#endif
}


#ifndef PRODUCT
const char* vmSymbols::name_for(vmSymbols::SID sid) {
  if (sid == NO_SID)
    return "NO_SID";
  const char* string = &vm_symbol_bodies[0];
  for (int index = (int)FIRST_SID; index < (int)SID_LIMIT; index++) {
    if (index == (int)sid)
      return string;
    string += strlen(string); // skip string body
    string += 1;              // skip trailing null
  }
  return "BAD_SID";
}
#endif



void vmSymbols::symbols_do(SymbolClosure* f) {
  for (int index = (int)FIRST_SID; index < (int)SID_LIMIT; index++) {
    f->do_symbol(&_symbols[index]);
  }
  for (int i = 0; i < T_VOID+1; i++) {
    f->do_symbol(&_type_signatures[i]);
  }
}

void vmSymbols::serialize(SerializeClosure* soc) {
  soc->do_region((u_char*)&_symbols[FIRST_SID],
                 (SID_LIMIT - FIRST_SID) * sizeof(_symbols[0]));
  soc->do_region((u_char*)_type_signatures, sizeof(_type_signatures));
}


BasicType vmSymbols::signature_type(Symbol* s) {
  assert(s != NULL, "checking");
  for (int i = T_BOOLEAN; i < T_VOID+1; i++) {
    if (s == _type_signatures[i]) {
      return (BasicType)i;
    }
  }
  return T_OBJECT;
}


static int mid_hint = (int)vmSymbols::FIRST_SID+1;

#ifndef PRODUCT
static int find_sid_calls, find_sid_probes;
// (Typical counts are calls=7000 and probes=17000.)
#endif

vmSymbols::SID vmSymbols::find_sid(Symbol* symbol) {
  // Handle the majority of misses by a bounds check.
  // Then, use a binary search over the index.
  // Expected trip count is less than log2_SID_LIMIT, about eight.
  // This is slow but acceptable, given that calls are not
  // dynamically common.  (Method*::intrinsic_id has a cache.)
  NOT_PRODUCT(find_sid_calls++);
  int min = (int)FIRST_SID, max = (int)SID_LIMIT - 1;
  SID sid = NO_SID, sid1;
  int cmp1;
  sid1 = vm_symbol_index[min];
  cmp1 = compare_symbol(symbol, symbol_at(sid1));
  if (cmp1 <= 0) {              // before the first
    if (cmp1 == 0)  sid = sid1;
  } else {
    sid1 = vm_symbol_index[max];
    cmp1 = compare_symbol(symbol, symbol_at(sid1));
    if (cmp1 >= 0) {            // after the last
      if (cmp1 == 0)  sid = sid1;
    } else {
      // After checking the extremes, do a binary search.
      ++min; --max;             // endpoints are done
      int mid = mid_hint;       // start at previous success
      while (max >= min) {
        assert(mid >= min && mid <= max, "");
        NOT_PRODUCT(find_sid_probes++);
        sid1 = vm_symbol_index[mid];
        cmp1 = compare_symbol(symbol, symbol_at(sid1));
        if (cmp1 == 0) {
          mid_hint = mid;
          sid = sid1;
          break;
        }
        if (cmp1 < 0)
          max = mid - 1;        // symbol < symbol_at(sid)
        else
          min = mid + 1;

        // Pick a new probe point:
        mid = (max + min) / 2;
      }
    }
  }

#ifdef ASSERT
  // Perform the exhaustive self-check the first 1000 calls,
  // and every 100 calls thereafter.
  static int find_sid_check_count = -2000;
  if ((uint)++find_sid_check_count > (uint)100) {
    if (find_sid_check_count > 0)  find_sid_check_count = 0;

    // Make sure this is the right answer, using linear search.
    // (We have already proven that there are no duplicates in the list.)
    SID sid2 = NO_SID;
    for (int index = (int)FIRST_SID; index < (int)SID_LIMIT; index++) {
      Symbol* sym2 = symbol_at((SID)index);
      if (sym2 == symbol) {
        sid2 = (SID)index;
        break;
      }
    }
    // Unless it's a duplicate, assert that the sids are the same.
    if (_symbols[sid] != _symbols[sid2]) {
      assert(sid == sid2, "binary same as linear search");
    }
  }
#endif //ASSERT

  return sid;
}

vmSymbols::SID vmSymbols::find_sid(const char* symbol_name) {
  Symbol* symbol = SymbolTable::probe(symbol_name, (int) strlen(symbol_name));
  if (symbol == NULL)  return NO_SID;
  return find_sid(symbol);
}

static vmIntrinsics::ID wrapper_intrinsic(BasicType type, bool unboxing) {
#define TYPE2(type, unboxing) ((int)(type)*2 + ((unboxing) ? 1 : 0))
  switch (TYPE2(type, unboxing)) {
#define BASIC_TYPE_CASE(type, box, unbox) \
    case TYPE2(type, false):  return vmIntrinsics::box; \
    case TYPE2(type, true):   return vmIntrinsics::unbox
    BASIC_TYPE_CASE(T_BOOLEAN, _Boolean_valueOf,   _booleanValue);
    BASIC_TYPE_CASE(T_BYTE,    _Byte_valueOf,      _byteValue);
    BASIC_TYPE_CASE(T_CHAR,    _Character_valueOf, _charValue);
    BASIC_TYPE_CASE(T_SHORT,   _Short_valueOf,     _shortValue);
    BASIC_TYPE_CASE(T_INT,     _Integer_valueOf,   _intValue);
    BASIC_TYPE_CASE(T_LONG,    _Long_valueOf,      _longValue);
    BASIC_TYPE_CASE(T_FLOAT,   _Float_valueOf,     _floatValue);
    BASIC_TYPE_CASE(T_DOUBLE,  _Double_valueOf,    _doubleValue);
#undef BASIC_TYPE_CASE
  }
#undef TYPE2
  return vmIntrinsics::_none;
}

vmIntrinsics::ID vmIntrinsics::for_boxing(BasicType type) {
  return wrapper_intrinsic(type, false);
}
vmIntrinsics::ID vmIntrinsics::for_unboxing(BasicType type) {
  return wrapper_intrinsic(type, true);
}

vmIntrinsics::ID vmIntrinsics::for_raw_conversion(BasicType src, BasicType dest) {
#define SRC_DEST(s,d) (((int)(s) << 4) + (int)(d))
  switch (SRC_DEST(src, dest)) {
  case SRC_DEST(T_INT, T_FLOAT):   return vmIntrinsics::_intBitsToFloat;
  case SRC_DEST(T_FLOAT, T_INT):   return vmIntrinsics::_floatToRawIntBits;

  case SRC_DEST(T_LONG, T_DOUBLE): return vmIntrinsics::_longBitsToDouble;
  case SRC_DEST(T_DOUBLE, T_LONG): return vmIntrinsics::_doubleToRawLongBits;
  }
#undef SRC_DEST

  return vmIntrinsics::_none;
}


#define VM_INTRINSIC_INITIALIZE(id, klass, name, sig, flags) #id "\0"
static const char* vm_intrinsic_name_bodies =
  VM_INTRINSICS_DO(VM_INTRINSIC_INITIALIZE,
                   VM_SYMBOL_IGNORE, VM_SYMBOL_IGNORE, VM_SYMBOL_IGNORE, VM_ALIAS_IGNORE);

static const char* vm_intrinsic_name_table[vmIntrinsics::ID_LIMIT];

const char* vmIntrinsics::name_at(vmIntrinsics::ID id) {
  const char** nt = &vm_intrinsic_name_table[0];
  if (nt[_none] == NULL) {
    char* string = (char*) &vm_intrinsic_name_bodies[0];
    for (int index = FIRST_ID; index < ID_LIMIT; index++) {
      nt[index] = string;
      string += strlen(string); // skip string body
      string += 1;              // skip trailing null
    }
    assert(!strcmp(nt[_hashCode], "_hashCode"), "lined up");
    nt[_none] = "_none";
  }
  if ((uint)id < (uint)ID_LIMIT)
    return vm_intrinsic_name_table[(uint)id];
  else
    return "(unknown intrinsic)";
}

// These are flag-matching functions:
inline bool match_F_R(jshort flags) {
  const int req = 0;
  const int neg = JVM_ACC_STATIC | JVM_ACC_SYNCHRONIZED;
  return (flags & (req | neg)) == req;
}
inline bool match_F_Y(jshort flags) {
  const int req = JVM_ACC_SYNCHRONIZED;
  const int neg = JVM_ACC_STATIC;
  return (flags & (req | neg)) == req;
}
inline bool match_F_RN(jshort flags) {
  const int req = JVM_ACC_NATIVE;
  const int neg = JVM_ACC_STATIC | JVM_ACC_SYNCHRONIZED;
  return (flags & (req | neg)) == req;
}
inline bool match_F_S(jshort flags) {
  const int req = JVM_ACC_STATIC;
  const int neg = JVM_ACC_SYNCHRONIZED;
  return (flags & (req | neg)) == req;
}
inline bool match_F_SN(jshort flags) {
  const int req = JVM_ACC_STATIC | JVM_ACC_NATIVE;
  const int neg = JVM_ACC_SYNCHRONIZED;
  return (flags & (req | neg)) == req;
}
inline bool match_F_RNY(jshort flags) {
  const int req = JVM_ACC_NATIVE | JVM_ACC_SYNCHRONIZED;
  const int neg = JVM_ACC_STATIC;
  return (flags & (req | neg)) == req;
}

// These are for forming case labels:
#define ID3(x, y, z) (( jlong)(z) +                                  \
                      ((jlong)(y) <<    vmSymbols::log2_SID_LIMIT) + \
                      ((jlong)(x) << (2*vmSymbols::log2_SID_LIMIT))  )
#define SID_ENUM(n) vmSymbols::VM_SYMBOL_ENUM_NAME(n)

vmIntrinsics::ID vmIntrinsics::find_id_impl(vmSymbols::SID holder,
                                            vmSymbols::SID name,
                                            vmSymbols::SID sig,
                                            jshort flags) {
  assert((int)vmSymbols::SID_LIMIT <= (1<<vmSymbols::log2_SID_LIMIT), "must fit");

  // Let the C compiler build the decision tree.

#define VM_INTRINSIC_CASE(id, klass, name, sig, fcode) \
  case ID3(SID_ENUM(klass), SID_ENUM(name), SID_ENUM(sig)): \
    if (!match_##fcode(flags))  break; \
    return id;

  switch (ID3(holder, name, sig)) {
    VM_INTRINSICS_DO(VM_INTRINSIC_CASE,
                     VM_SYMBOL_IGNORE, VM_SYMBOL_IGNORE, VM_SYMBOL_IGNORE, VM_ALIAS_IGNORE);
  }
  return vmIntrinsics::_none;

#undef VM_INTRINSIC_CASE
}


const char* vmIntrinsics::short_name_as_C_string(vmIntrinsics::ID id, char* buf, int buflen) {
  const char* str = name_at(id);
#ifndef PRODUCT
  const char* kname = vmSymbols::name_for(class_for(id));
  const char* mname = vmSymbols::name_for(name_for(id));
  const char* sname = vmSymbols::name_for(signature_for(id));
  const char* fname = "";
  switch (flags_for(id)) {
  case F_Y:  fname = "synchronized ";  break;
  case F_RN: fname = "native ";        break;
  case F_SN: fname = "native static "; break;
  case F_S:  fname = "static ";        break;
  case F_RNY:fname = "native synchronized "; break;
  }
  const char* kptr = strrchr(kname, '/');
  if (kptr != NULL)  kname = kptr + 1;
  int len = jio_snprintf(buf, buflen, "%s: %s%s.%s%s",
                         str, fname, kname, mname, sname);
  if (len < buflen)
    str = buf;
#endif //PRODUCT
  return str;
}


// These are to get information about intrinsics.

#define ID4(x, y, z, f) ((ID3(x, y, z) << vmIntrinsics::log2_FLAG_LIMIT) | (jlong) (f))

static const jlong intrinsic_info_array[vmIntrinsics::ID_LIMIT+1] = {
#define VM_INTRINSIC_INFO(ignore_id, klass, name, sig, fcode) \
  ID4(SID_ENUM(klass), SID_ENUM(name), SID_ENUM(sig), vmIntrinsics::fcode),

  0, VM_INTRINSICS_DO(VM_INTRINSIC_INFO,
                     VM_SYMBOL_IGNORE, VM_SYMBOL_IGNORE, VM_SYMBOL_IGNORE, VM_ALIAS_IGNORE)
    0
#undef VM_INTRINSIC_INFO
};

inline jlong intrinsic_info(vmIntrinsics::ID id) {
  return intrinsic_info_array[vmIntrinsics::ID_from((int)id)];
}

vmSymbols::SID vmIntrinsics::class_for(vmIntrinsics::ID id) {
  jlong info = intrinsic_info(id);
  int shift = 2*vmSymbols::log2_SID_LIMIT + log2_FLAG_LIMIT, mask = right_n_bits(vmSymbols::log2_SID_LIMIT);
  assert(((ID4(1021,1022,1023,15) >> shift) & mask) == 1021, "");
  return vmSymbols::SID( (info >> shift) & mask );
}

vmSymbols::SID vmIntrinsics::name_for(vmIntrinsics::ID id) {
  jlong info = intrinsic_info(id);
  int shift = vmSymbols::log2_SID_LIMIT + log2_FLAG_LIMIT, mask = right_n_bits(vmSymbols::log2_SID_LIMIT);
  assert(((ID4(1021,1022,1023,15) >> shift) & mask) == 1022, "");
  return vmSymbols::SID( (info >> shift) & mask );
}

vmSymbols::SID vmIntrinsics::signature_for(vmIntrinsics::ID id) {
  jlong info = intrinsic_info(id);
  int shift = log2_FLAG_LIMIT, mask = right_n_bits(vmSymbols::log2_SID_LIMIT);
  assert(((ID4(1021,1022,1023,15) >> shift) & mask) == 1023, "");
  return vmSymbols::SID( (info >> shift) & mask );
}

vmIntrinsics::Flags vmIntrinsics::flags_for(vmIntrinsics::ID id) {
  jlong info = intrinsic_info(id);
  int shift = 0, mask = right_n_bits(log2_FLAG_LIMIT);
  assert(((ID4(1021,1022,1023,15) >> shift) & mask) == 15, "");
  return Flags( (info >> shift) & mask );
}


#ifndef PRODUCT
// verify_method performs an extra check on a matched intrinsic method

static bool match_method(Method* m, Symbol* n, Symbol* s) {
  return (m->name() == n &&
          m->signature() == s);
}

static vmIntrinsics::ID match_method_with_klass(Method* m, Symbol* mk) {
#define VM_INTRINSIC_MATCH(id, klassname, namepart, sigpart, flags) \
  { Symbol* k = vmSymbols::klassname(); \
    if (mk == k) { \
      Symbol* n = vmSymbols::namepart(); \
      Symbol* s = vmSymbols::sigpart(); \
      if (match_method(m, n, s)) \
        return vmIntrinsics::id; \
    } }
  VM_INTRINSICS_DO(VM_INTRINSIC_MATCH,
                   VM_SYMBOL_IGNORE, VM_SYMBOL_IGNORE, VM_SYMBOL_IGNORE, VM_ALIAS_IGNORE);
  return vmIntrinsics::_none;
#undef VM_INTRINSIC_MATCH
}

void vmIntrinsics::verify_method(ID actual_id, Method* m) {
  Symbol* mk = m->method_holder()->name();
  ID declared_id = match_method_with_klass(m, mk);

  if (declared_id == actual_id)  return; // success

  if (declared_id == _none && actual_id != _none && mk == vmSymbols::java_lang_StrictMath()) {
    // Here are a few special cases in StrictMath not declared in vmSymbols.hpp.
    switch (actual_id) {
    case _min:
    case _max:
    case _dsqrt:
      declared_id = match_method_with_klass(m, vmSymbols::java_lang_Math());
      if (declared_id == actual_id)  return; // acceptable alias
      break;
    }
  }

  const char* declared_name = name_at(declared_id);
  const char* actual_name   = name_at(actual_id);
  methodHandle mh = m;
  m = NULL;
  ttyLocker ttyl;
  if (xtty != NULL) {
    xtty->begin_elem("intrinsic_misdeclared actual='%s' declared='%s'",
                     actual_name, declared_name);
    xtty->method(mh);
    xtty->end_elem("");
  }
  if (PrintMiscellaneous && (WizardMode || Verbose)) {
    tty->print_cr("*** misidentified method; %s(%d) should be %s(%d):",
                  declared_name, declared_id, actual_name, actual_id);
    mh()->print_short_name(tty);
    tty->cr();
  }
}
#endif //PRODUCT

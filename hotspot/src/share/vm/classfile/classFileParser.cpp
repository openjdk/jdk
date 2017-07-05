/*
 * Copyright 1997-2009 Sun Microsystems, Inc.  All Rights Reserved.
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

#include "incls/_precompiled.incl"
#include "incls/_classFileParser.cpp.incl"

// We generally try to create the oops directly when parsing, rather than allocating
// temporary data structures and copying the bytes twice. A temporary area is only
// needed when parsing utf8 entries in the constant pool and when parsing line number
// tables.

// We add assert in debug mode when class format is not checked.

#define JAVA_CLASSFILE_MAGIC              0xCAFEBABE
#define JAVA_MIN_SUPPORTED_VERSION        45
#define JAVA_MAX_SUPPORTED_VERSION        51
#define JAVA_MAX_SUPPORTED_MINOR_VERSION  0

// Used for two backward compatibility reasons:
// - to check for new additions to the class file format in JDK1.5
// - to check for bug fixes in the format checker in JDK1.5
#define JAVA_1_5_VERSION                  49

// Used for backward compatibility reasons:
// - to check for javac bug fixes that happened after 1.5
// - also used as the max version when running in jdk6
#define JAVA_6_VERSION                    50


void ClassFileParser::parse_constant_pool_entries(constantPoolHandle cp, int length, TRAPS) {
  // Use a local copy of ClassFileStream. It helps the C++ compiler to optimize
  // this function (_current can be allocated in a register, with scalar
  // replacement of aggregates). The _current pointer is copied back to
  // stream() when this function returns. DON'T call another method within
  // this method that uses stream().
  ClassFileStream* cfs0 = stream();
  ClassFileStream cfs1 = *cfs0;
  ClassFileStream* cfs = &cfs1;
#ifdef ASSERT
  u1* old_current = cfs0->current();
#endif

  // Used for batching symbol allocations.
  const char* names[SymbolTable::symbol_alloc_batch_size];
  int lengths[SymbolTable::symbol_alloc_batch_size];
  int indices[SymbolTable::symbol_alloc_batch_size];
  unsigned int hashValues[SymbolTable::symbol_alloc_batch_size];
  int names_count = 0;

  // parsing  Index 0 is unused
  for (int index = 1; index < length; index++) {
    // Each of the following case guarantees one more byte in the stream
    // for the following tag or the access_flags following constant pool,
    // so we don't need bounds-check for reading tag.
    u1 tag = cfs->get_u1_fast();
    switch (tag) {
      case JVM_CONSTANT_Class :
        {
          cfs->guarantee_more(3, CHECK);  // name_index, tag/access_flags
          u2 name_index = cfs->get_u2_fast();
          cp->klass_index_at_put(index, name_index);
        }
        break;
      case JVM_CONSTANT_Fieldref :
        {
          cfs->guarantee_more(5, CHECK);  // class_index, name_and_type_index, tag/access_flags
          u2 class_index = cfs->get_u2_fast();
          u2 name_and_type_index = cfs->get_u2_fast();
          cp->field_at_put(index, class_index, name_and_type_index);
        }
        break;
      case JVM_CONSTANT_Methodref :
        {
          cfs->guarantee_more(5, CHECK);  // class_index, name_and_type_index, tag/access_flags
          u2 class_index = cfs->get_u2_fast();
          u2 name_and_type_index = cfs->get_u2_fast();
          cp->method_at_put(index, class_index, name_and_type_index);
        }
        break;
      case JVM_CONSTANT_InterfaceMethodref :
        {
          cfs->guarantee_more(5, CHECK);  // class_index, name_and_type_index, tag/access_flags
          u2 class_index = cfs->get_u2_fast();
          u2 name_and_type_index = cfs->get_u2_fast();
          cp->interface_method_at_put(index, class_index, name_and_type_index);
        }
        break;
      case JVM_CONSTANT_String :
        {
          cfs->guarantee_more(3, CHECK);  // string_index, tag/access_flags
          u2 string_index = cfs->get_u2_fast();
          cp->string_index_at_put(index, string_index);
        }
        break;
      case JVM_CONSTANT_Integer :
        {
          cfs->guarantee_more(5, CHECK);  // bytes, tag/access_flags
          u4 bytes = cfs->get_u4_fast();
          cp->int_at_put(index, (jint) bytes);
        }
        break;
      case JVM_CONSTANT_Float :
        {
          cfs->guarantee_more(5, CHECK);  // bytes, tag/access_flags
          u4 bytes = cfs->get_u4_fast();
          cp->float_at_put(index, *(jfloat*)&bytes);
        }
        break;
      case JVM_CONSTANT_Long :
        // A mangled type might cause you to overrun allocated memory
        guarantee_property(index+1 < length,
                           "Invalid constant pool entry %u in class file %s",
                           index, CHECK);
        {
          cfs->guarantee_more(9, CHECK);  // bytes, tag/access_flags
          u8 bytes = cfs->get_u8_fast();
          cp->long_at_put(index, bytes);
        }
        index++;   // Skip entry following eigth-byte constant, see JVM book p. 98
        break;
      case JVM_CONSTANT_Double :
        // A mangled type might cause you to overrun allocated memory
        guarantee_property(index+1 < length,
                           "Invalid constant pool entry %u in class file %s",
                           index, CHECK);
        {
          cfs->guarantee_more(9, CHECK);  // bytes, tag/access_flags
          u8 bytes = cfs->get_u8_fast();
          cp->double_at_put(index, *(jdouble*)&bytes);
        }
        index++;   // Skip entry following eigth-byte constant, see JVM book p. 98
        break;
      case JVM_CONSTANT_NameAndType :
        {
          cfs->guarantee_more(5, CHECK);  // name_index, signature_index, tag/access_flags
          u2 name_index = cfs->get_u2_fast();
          u2 signature_index = cfs->get_u2_fast();
          cp->name_and_type_at_put(index, name_index, signature_index);
        }
        break;
      case JVM_CONSTANT_Utf8 :
        {
          cfs->guarantee_more(2, CHECK);  // utf8_length
          u2  utf8_length = cfs->get_u2_fast();
          u1* utf8_buffer = cfs->get_u1_buffer();
          assert(utf8_buffer != NULL, "null utf8 buffer");
          // Got utf8 string, guarantee utf8_length+1 bytes, set stream position forward.
          cfs->guarantee_more(utf8_length+1, CHECK);  // utf8 string, tag/access_flags
          cfs->skip_u1_fast(utf8_length);

          // Before storing the symbol, make sure it's legal
          if (_need_verify) {
            verify_legal_utf8((unsigned char*)utf8_buffer, utf8_length, CHECK);
          }

          if (AnonymousClasses && has_cp_patch_at(index)) {
            Handle patch = clear_cp_patch_at(index);
            guarantee_property(java_lang_String::is_instance(patch()),
                               "Illegal utf8 patch at %d in class file %s",
                               index, CHECK);
            char* str = java_lang_String::as_utf8_string(patch());
            // (could use java_lang_String::as_symbol instead, but might as well batch them)
            utf8_buffer = (u1*) str;
            utf8_length = (int) strlen(str);
          }

          unsigned int hash;
          symbolOop result = SymbolTable::lookup_only((char*)utf8_buffer, utf8_length, hash);
          if (result == NULL) {
            names[names_count] = (char*)utf8_buffer;
            lengths[names_count] = utf8_length;
            indices[names_count] = index;
            hashValues[names_count++] = hash;
            if (names_count == SymbolTable::symbol_alloc_batch_size) {
              oopFactory::new_symbols(cp, names_count, names, lengths, indices, hashValues, CHECK);
              names_count = 0;
            }
          } else {
            cp->symbol_at_put(index, result);
          }
        }
        break;
      default:
        classfile_parse_error(
          "Unknown constant tag %u in class file %s", tag, CHECK);
        break;
    }
  }

  // Allocate the remaining symbols
  if (names_count > 0) {
    oopFactory::new_symbols(cp, names_count, names, lengths, indices, hashValues, CHECK);
  }

  // Copy _current pointer of local copy back to stream().
#ifdef ASSERT
  assert(cfs0->current() == old_current, "non-exclusive use of stream()");
#endif
  cfs0->set_current(cfs1.current());
}

bool inline valid_cp_range(int index, int length) { return (index > 0 && index < length); }

constantPoolHandle ClassFileParser::parse_constant_pool(TRAPS) {
  ClassFileStream* cfs = stream();
  constantPoolHandle nullHandle;

  cfs->guarantee_more(3, CHECK_(nullHandle)); // length, first cp tag
  u2 length = cfs->get_u2_fast();
  guarantee_property(
    length >= 1, "Illegal constant pool size %u in class file %s",
    length, CHECK_(nullHandle));
  constantPoolOop constant_pool =
                      oopFactory::new_constantPool(length,
                                                   methodOopDesc::IsSafeConc,
                                                   CHECK_(nullHandle));
  constantPoolHandle cp (THREAD, constant_pool);

  cp->set_partially_loaded();    // Enables heap verify to work on partial constantPoolOops

  // parsing constant pool entries
  parse_constant_pool_entries(cp, length, CHECK_(nullHandle));

  int index = 1;  // declared outside of loops for portability

  // first verification pass - validate cross references and fixup class and string constants
  for (index = 1; index < length; index++) {          // Index 0 is unused
    switch (cp->tag_at(index).value()) {
      case JVM_CONSTANT_Class :
        ShouldNotReachHere();     // Only JVM_CONSTANT_ClassIndex should be present
        break;
      case JVM_CONSTANT_Fieldref :
        // fall through
      case JVM_CONSTANT_Methodref :
        // fall through
      case JVM_CONSTANT_InterfaceMethodref : {
        if (!_need_verify) break;
        int klass_ref_index = cp->klass_ref_index_at(index);
        int name_and_type_ref_index = cp->name_and_type_ref_index_at(index);
        check_property(valid_cp_range(klass_ref_index, length) &&
                       is_klass_reference(cp, klass_ref_index),
                       "Invalid constant pool index %u in class file %s",
                       klass_ref_index,
                       CHECK_(nullHandle));
        check_property(valid_cp_range(name_and_type_ref_index, length) &&
                       cp->tag_at(name_and_type_ref_index).is_name_and_type(),
                       "Invalid constant pool index %u in class file %s",
                       name_and_type_ref_index,
                       CHECK_(nullHandle));
        break;
      }
      case JVM_CONSTANT_String :
        ShouldNotReachHere();     // Only JVM_CONSTANT_StringIndex should be present
        break;
      case JVM_CONSTANT_Integer :
        break;
      case JVM_CONSTANT_Float :
        break;
      case JVM_CONSTANT_Long :
      case JVM_CONSTANT_Double :
        index++;
        check_property(
          (index < length && cp->tag_at(index).is_invalid()),
          "Improper constant pool long/double index %u in class file %s",
          index, CHECK_(nullHandle));
        break;
      case JVM_CONSTANT_NameAndType : {
        if (!_need_verify) break;
        int name_ref_index = cp->name_ref_index_at(index);
        int signature_ref_index = cp->signature_ref_index_at(index);
        check_property(
          valid_cp_range(name_ref_index, length) &&
            cp->tag_at(name_ref_index).is_utf8(),
          "Invalid constant pool index %u in class file %s",
          name_ref_index, CHECK_(nullHandle));
        check_property(
          valid_cp_range(signature_ref_index, length) &&
            cp->tag_at(signature_ref_index).is_utf8(),
          "Invalid constant pool index %u in class file %s",
          signature_ref_index, CHECK_(nullHandle));
        break;
      }
      case JVM_CONSTANT_Utf8 :
        break;
      case JVM_CONSTANT_UnresolvedClass :         // fall-through
      case JVM_CONSTANT_UnresolvedClassInError:
        ShouldNotReachHere();     // Only JVM_CONSTANT_ClassIndex should be present
        break;
      case JVM_CONSTANT_ClassIndex :
        {
          int class_index = cp->klass_index_at(index);
          check_property(
            valid_cp_range(class_index, length) &&
              cp->tag_at(class_index).is_utf8(),
            "Invalid constant pool index %u in class file %s",
            class_index, CHECK_(nullHandle));
          cp->unresolved_klass_at_put(index, cp->symbol_at(class_index));
        }
        break;
      case JVM_CONSTANT_UnresolvedString :
        ShouldNotReachHere();     // Only JVM_CONSTANT_StringIndex should be present
        break;
      case JVM_CONSTANT_StringIndex :
        {
          int string_index = cp->string_index_at(index);
          check_property(
            valid_cp_range(string_index, length) &&
              cp->tag_at(string_index).is_utf8(),
            "Invalid constant pool index %u in class file %s",
            string_index, CHECK_(nullHandle));
          symbolOop sym = cp->symbol_at(string_index);
          cp->unresolved_string_at_put(index, sym);
        }
        break;
      default:
        fatal1("bad constant pool tag value %u", cp->tag_at(index).value());
        ShouldNotReachHere();
        break;
    } // end of switch
  } // end of for

  if (_cp_patches != NULL) {
    // need to treat this_class specially...
    assert(AnonymousClasses, "");
    int this_class_index;
    {
      cfs->guarantee_more(8, CHECK_(nullHandle));  // flags, this_class, super_class, infs_len
      u1* mark = cfs->current();
      u2 flags         = cfs->get_u2_fast();
      this_class_index = cfs->get_u2_fast();
      cfs->set_current(mark);  // revert to mark
    }

    for (index = 1; index < length; index++) {          // Index 0 is unused
      if (has_cp_patch_at(index)) {
        guarantee_property(index != this_class_index,
                           "Illegal constant pool patch to self at %d in class file %s",
                           index, CHECK_(nullHandle));
        patch_constant_pool(cp, index, cp_patch_at(index), CHECK_(nullHandle));
      }
    }
    // Ensure that all the patches have been used.
    for (index = 0; index < _cp_patches->length(); index++) {
      guarantee_property(!has_cp_patch_at(index),
                         "Unused constant pool patch at %d in class file %s",
                         index, CHECK_(nullHandle));
    }
  }

  if (!_need_verify) {
    return cp;
  }

  // second verification pass - checks the strings are of the right format.
  // but not yet to the other entries
  for (index = 1; index < length; index++) {
    jbyte tag = cp->tag_at(index).value();
    switch (tag) {
      case JVM_CONSTANT_UnresolvedClass: {
        symbolHandle class_name(THREAD, cp->unresolved_klass_at(index));
        // check the name, even if _cp_patches will overwrite it
        verify_legal_class_name(class_name, CHECK_(nullHandle));
        break;
      }
      case JVM_CONSTANT_Fieldref:
      case JVM_CONSTANT_Methodref:
      case JVM_CONSTANT_InterfaceMethodref: {
        int name_and_type_ref_index = cp->name_and_type_ref_index_at(index);
        // already verified to be utf8
        int name_ref_index = cp->name_ref_index_at(name_and_type_ref_index);
        // already verified to be utf8
        int signature_ref_index = cp->signature_ref_index_at(name_and_type_ref_index);
        symbolHandle name(THREAD, cp->symbol_at(name_ref_index));
        symbolHandle signature(THREAD, cp->symbol_at(signature_ref_index));
        if (tag == JVM_CONSTANT_Fieldref) {
          verify_legal_field_name(name, CHECK_(nullHandle));
          verify_legal_field_signature(name, signature, CHECK_(nullHandle));
        } else {
          verify_legal_method_name(name, CHECK_(nullHandle));
          verify_legal_method_signature(name, signature, CHECK_(nullHandle));
          if (tag == JVM_CONSTANT_Methodref) {
            // 4509014: If a class method name begins with '<', it must be "<init>".
            assert(!name.is_null(), "method name in constant pool is null");
            unsigned int name_len = name->utf8_length();
            assert(name_len > 0, "bad method name");  // already verified as legal name
            if (name->byte_at(0) == '<') {
              if (name() != vmSymbols::object_initializer_name()) {
                classfile_parse_error(
                  "Bad method name at constant pool index %u in class file %s",
                  name_ref_index, CHECK_(nullHandle));
              }
            }
          }
        }
        break;
      }
    }  // end of switch
  }  // end of for

  return cp;
}


void ClassFileParser::patch_constant_pool(constantPoolHandle cp, int index, Handle patch, TRAPS) {
  assert(AnonymousClasses, "");
  BasicType patch_type = T_VOID;
  switch (cp->tag_at(index).value()) {

  case JVM_CONSTANT_UnresolvedClass :
    // Patching a class means pre-resolving it.
    // The name in the constant pool is ignored.
    if (patch->klass() == SystemDictionary::class_klass()) { // %%% java_lang_Class::is_instance
      guarantee_property(!java_lang_Class::is_primitive(patch()),
                         "Illegal class patch at %d in class file %s",
                         index, CHECK);
      cp->klass_at_put(index, java_lang_Class::as_klassOop(patch()));
    } else {
      guarantee_property(java_lang_String::is_instance(patch()),
                         "Illegal class patch at %d in class file %s",
                         index, CHECK);
      symbolHandle name = java_lang_String::as_symbol(patch(), CHECK);
      cp->unresolved_klass_at_put(index, name());
    }
    break;

  case JVM_CONSTANT_UnresolvedString :
    // Patching a string means pre-resolving it.
    // The spelling in the constant pool is ignored.
    // The constant reference may be any object whatever.
    // If it is not a real interned string, the constant is referred
    // to as a "pseudo-string", and must be presented to the CP
    // explicitly, because it may require scavenging.
    cp->pseudo_string_at_put(index, patch());
    break;

  case JVM_CONSTANT_Integer : patch_type = T_INT;    goto patch_prim;
  case JVM_CONSTANT_Float :   patch_type = T_FLOAT;  goto patch_prim;
  case JVM_CONSTANT_Long :    patch_type = T_LONG;   goto patch_prim;
  case JVM_CONSTANT_Double :  patch_type = T_DOUBLE; goto patch_prim;
  patch_prim:
    {
      jvalue value;
      BasicType value_type = java_lang_boxing_object::get_value(patch(), &value);
      guarantee_property(value_type == patch_type,
                         "Illegal primitive patch at %d in class file %s",
                         index, CHECK);
      switch (value_type) {
      case T_INT:    cp->int_at_put(index,   value.i); break;
      case T_FLOAT:  cp->float_at_put(index, value.f); break;
      case T_LONG:   cp->long_at_put(index,  value.j); break;
      case T_DOUBLE: cp->double_at_put(index, value.d); break;
      default:       assert(false, "");
      }
    }
    break;

  default:
    // %%% TODO: put method handles into CONSTANT_InterfaceMethodref, etc.
    guarantee_property(!has_cp_patch_at(index),
                       "Illegal unexpected patch at %d in class file %s",
                       index, CHECK);
    return;
  }

  // On fall-through, mark the patch as used.
  clear_cp_patch_at(index);
}



class NameSigHash: public ResourceObj {
 public:
  symbolOop     _name;       // name
  symbolOop     _sig;        // signature
  NameSigHash*  _next;       // Next entry in hash table
};


#define HASH_ROW_SIZE 256

unsigned int hash(symbolOop name, symbolOop sig) {
  unsigned int raw_hash = 0;
  raw_hash += ((unsigned int)(uintptr_t)name) >> (LogHeapWordSize + 2);
  raw_hash += ((unsigned int)(uintptr_t)sig) >> LogHeapWordSize;

  return (raw_hash + (unsigned int)(uintptr_t)name) % HASH_ROW_SIZE;
}


void initialize_hashtable(NameSigHash** table) {
  memset((void*)table, 0, sizeof(NameSigHash*) * HASH_ROW_SIZE);
}

// Return false if the name/sig combination is found in table.
// Return true if no duplicate is found. And name/sig is added as a new entry in table.
// The old format checker uses heap sort to find duplicates.
// NOTE: caller should guarantee that GC doesn't happen during the life cycle
// of table since we don't expect symbolOop's to move.
bool put_after_lookup(symbolOop name, symbolOop sig, NameSigHash** table) {
  assert(name != NULL, "name in constant pool is NULL");

  // First lookup for duplicates
  int index = hash(name, sig);
  NameSigHash* entry = table[index];
  while (entry != NULL) {
    if (entry->_name == name && entry->_sig == sig) {
      return false;
    }
    entry = entry->_next;
  }

  // No duplicate is found, allocate a new entry and fill it.
  entry = new NameSigHash();
  entry->_name = name;
  entry->_sig = sig;

  // Insert into hash table
  entry->_next = table[index];
  table[index] = entry;

  return true;
}


objArrayHandle ClassFileParser::parse_interfaces(constantPoolHandle cp,
                                                 int length,
                                                 Handle class_loader,
                                                 Handle protection_domain,
                                                 symbolHandle class_name,
                                                 TRAPS) {
  ClassFileStream* cfs = stream();
  assert(length > 0, "only called for length>0");
  objArrayHandle nullHandle;
  objArrayOop interface_oop = oopFactory::new_system_objArray(length, CHECK_(nullHandle));
  objArrayHandle interfaces (THREAD, interface_oop);

  int index;
  for (index = 0; index < length; index++) {
    u2 interface_index = cfs->get_u2(CHECK_(nullHandle));
    KlassHandle interf;
    check_property(
      valid_cp_range(interface_index, cp->length()) &&
      is_klass_reference(cp, interface_index),
      "Interface name has bad constant pool index %u in class file %s",
      interface_index, CHECK_(nullHandle));
    if (cp->tag_at(interface_index).is_klass()) {
      interf = KlassHandle(THREAD, cp->resolved_klass_at(interface_index));
    } else {
      symbolHandle unresolved_klass (THREAD, cp->klass_name_at(interface_index));

      // Don't need to check legal name because it's checked when parsing constant pool.
      // But need to make sure it's not an array type.
      guarantee_property(unresolved_klass->byte_at(0) != JVM_SIGNATURE_ARRAY,
                         "Bad interface name in class file %s", CHECK_(nullHandle));

      // Call resolve_super so classcircularity is checked
      klassOop k = SystemDictionary::resolve_super_or_fail(class_name,
                    unresolved_klass, class_loader, protection_domain,
                    false, CHECK_(nullHandle));
      interf = KlassHandle(THREAD, k);

      if (LinkWellKnownClasses)  // my super type is well known to me
        cp->klass_at_put(interface_index, interf()); // eagerly resolve
    }

    if (!Klass::cast(interf())->is_interface()) {
      THROW_MSG_(vmSymbols::java_lang_IncompatibleClassChangeError(), "Implementing class", nullHandle);
    }
    interfaces->obj_at_put(index, interf());
  }

  if (!_need_verify || length <= 1) {
    return interfaces;
  }

  // Check if there's any duplicates in interfaces
  ResourceMark rm(THREAD);
  NameSigHash** interface_names = NEW_RESOURCE_ARRAY_IN_THREAD(
    THREAD, NameSigHash*, HASH_ROW_SIZE);
  initialize_hashtable(interface_names);
  bool dup = false;
  {
    debug_only(No_Safepoint_Verifier nsv;)
    for (index = 0; index < length; index++) {
      klassOop k = (klassOop)interfaces->obj_at(index);
      symbolOop name = instanceKlass::cast(k)->name();
      // If no duplicates, add (name, NULL) in hashtable interface_names.
      if (!put_after_lookup(name, NULL, interface_names)) {
        dup = true;
        break;
      }
    }
  }
  if (dup) {
    classfile_parse_error("Duplicate interface name in class file %s",
                          CHECK_(nullHandle));
  }

  return interfaces;
}


void ClassFileParser::verify_constantvalue(int constantvalue_index, int signature_index, constantPoolHandle cp, TRAPS) {
  // Make sure the constant pool entry is of a type appropriate to this field
  guarantee_property(
    (constantvalue_index > 0 &&
      constantvalue_index < cp->length()),
    "Bad initial value index %u in ConstantValue attribute in class file %s",
    constantvalue_index, CHECK);
  constantTag value_type = cp->tag_at(constantvalue_index);
  switch ( cp->basic_type_for_signature_at(signature_index) ) {
    case T_LONG:
      guarantee_property(value_type.is_long(), "Inconsistent constant value type in class file %s", CHECK);
      break;
    case T_FLOAT:
      guarantee_property(value_type.is_float(), "Inconsistent constant value type in class file %s", CHECK);
      break;
    case T_DOUBLE:
      guarantee_property(value_type.is_double(), "Inconsistent constant value type in class file %s", CHECK);
      break;
    case T_BYTE: case T_CHAR: case T_SHORT: case T_BOOLEAN: case T_INT:
      guarantee_property(value_type.is_int(), "Inconsistent constant value type in class file %s", CHECK);
      break;
    case T_OBJECT:
      guarantee_property((cp->symbol_at(signature_index)->equals("Ljava/lang/String;", 18)
                         && (value_type.is_string() || value_type.is_unresolved_string())),
                         "Bad string initial value in class file %s", CHECK);
      break;
    default:
      classfile_parse_error(
        "Unable to set initial value %u in class file %s",
        constantvalue_index, CHECK);
  }
}


// Parse attributes for a field.
void ClassFileParser::parse_field_attributes(constantPoolHandle cp,
                                             u2 attributes_count,
                                             bool is_static, u2 signature_index,
                                             u2* constantvalue_index_addr,
                                             bool* is_synthetic_addr,
                                             u2* generic_signature_index_addr,
                                             typeArrayHandle* field_annotations,
                                             TRAPS) {
  ClassFileStream* cfs = stream();
  assert(attributes_count > 0, "length should be greater than 0");
  u2 constantvalue_index = 0;
  u2 generic_signature_index = 0;
  bool is_synthetic = false;
  u1* runtime_visible_annotations = NULL;
  int runtime_visible_annotations_length = 0;
  u1* runtime_invisible_annotations = NULL;
  int runtime_invisible_annotations_length = 0;
  while (attributes_count--) {
    cfs->guarantee_more(6, CHECK);  // attribute_name_index, attribute_length
    u2 attribute_name_index = cfs->get_u2_fast();
    u4 attribute_length = cfs->get_u4_fast();
    check_property(valid_cp_range(attribute_name_index, cp->length()) &&
                   cp->tag_at(attribute_name_index).is_utf8(),
                   "Invalid field attribute index %u in class file %s",
                   attribute_name_index,
                   CHECK);
    symbolOop attribute_name = cp->symbol_at(attribute_name_index);
    if (is_static && attribute_name == vmSymbols::tag_constant_value()) {
      // ignore if non-static
      if (constantvalue_index != 0) {
        classfile_parse_error("Duplicate ConstantValue attribute in class file %s", CHECK);
      }
      check_property(
        attribute_length == 2,
        "Invalid ConstantValue field attribute length %u in class file %s",
        attribute_length, CHECK);
      constantvalue_index = cfs->get_u2(CHECK);
      if (_need_verify) {
        verify_constantvalue(constantvalue_index, signature_index, cp, CHECK);
      }
    } else if (attribute_name == vmSymbols::tag_synthetic()) {
      if (attribute_length != 0) {
        classfile_parse_error(
          "Invalid Synthetic field attribute length %u in class file %s",
          attribute_length, CHECK);
      }
      is_synthetic = true;
    } else if (attribute_name == vmSymbols::tag_deprecated()) { // 4276120
      if (attribute_length != 0) {
        classfile_parse_error(
          "Invalid Deprecated field attribute length %u in class file %s",
          attribute_length, CHECK);
      }
    } else if (_major_version >= JAVA_1_5_VERSION) {
      if (attribute_name == vmSymbols::tag_signature()) {
        if (attribute_length != 2) {
          classfile_parse_error(
            "Wrong size %u for field's Signature attribute in class file %s",
            attribute_length, CHECK);
        }
        generic_signature_index = cfs->get_u2(CHECK);
      } else if (attribute_name == vmSymbols::tag_runtime_visible_annotations()) {
        runtime_visible_annotations_length = attribute_length;
        runtime_visible_annotations = cfs->get_u1_buffer();
        assert(runtime_visible_annotations != NULL, "null visible annotations");
        cfs->skip_u1(runtime_visible_annotations_length, CHECK);
      } else if (PreserveAllAnnotations && attribute_name == vmSymbols::tag_runtime_invisible_annotations()) {
        runtime_invisible_annotations_length = attribute_length;
        runtime_invisible_annotations = cfs->get_u1_buffer();
        assert(runtime_invisible_annotations != NULL, "null invisible annotations");
        cfs->skip_u1(runtime_invisible_annotations_length, CHECK);
      } else {
        cfs->skip_u1(attribute_length, CHECK);  // Skip unknown attributes
      }
    } else {
      cfs->skip_u1(attribute_length, CHECK);  // Skip unknown attributes
    }
  }

  *constantvalue_index_addr = constantvalue_index;
  *is_synthetic_addr = is_synthetic;
  *generic_signature_index_addr = generic_signature_index;
  *field_annotations = assemble_annotations(runtime_visible_annotations,
                                            runtime_visible_annotations_length,
                                            runtime_invisible_annotations,
                                            runtime_invisible_annotations_length,
                                            CHECK);
  return;
}


// Field allocation types. Used for computing field offsets.

enum FieldAllocationType {
  STATIC_OOP,           // Oops
  STATIC_BYTE,          // Boolean, Byte, char
  STATIC_SHORT,         // shorts
  STATIC_WORD,          // ints
  STATIC_DOUBLE,        // long or double
  STATIC_ALIGNED_DOUBLE,// aligned long or double
  NONSTATIC_OOP,
  NONSTATIC_BYTE,
  NONSTATIC_SHORT,
  NONSTATIC_WORD,
  NONSTATIC_DOUBLE,
  NONSTATIC_ALIGNED_DOUBLE
};


struct FieldAllocationCount {
  unsigned int static_oop_count;
  unsigned int static_byte_count;
  unsigned int static_short_count;
  unsigned int static_word_count;
  unsigned int static_double_count;
  unsigned int nonstatic_oop_count;
  unsigned int nonstatic_byte_count;
  unsigned int nonstatic_short_count;
  unsigned int nonstatic_word_count;
  unsigned int nonstatic_double_count;
};

typeArrayHandle ClassFileParser::parse_fields(constantPoolHandle cp, bool is_interface,
                                              struct FieldAllocationCount *fac,
                                              objArrayHandle* fields_annotations, TRAPS) {
  ClassFileStream* cfs = stream();
  typeArrayHandle nullHandle;
  cfs->guarantee_more(2, CHECK_(nullHandle));  // length
  u2 length = cfs->get_u2_fast();
  // Tuples of shorts [access, name index, sig index, initial value index, byte offset, generic signature index]
  typeArrayOop new_fields = oopFactory::new_permanent_shortArray(length*instanceKlass::next_offset, CHECK_(nullHandle));
  typeArrayHandle fields(THREAD, new_fields);

  int index = 0;
  typeArrayHandle field_annotations;
  for (int n = 0; n < length; n++) {
    cfs->guarantee_more(8, CHECK_(nullHandle));  // access_flags, name_index, descriptor_index, attributes_count

    AccessFlags access_flags;
    jint flags = cfs->get_u2_fast() & JVM_RECOGNIZED_FIELD_MODIFIERS;
    verify_legal_field_modifiers(flags, is_interface, CHECK_(nullHandle));
    access_flags.set_flags(flags);

    u2 name_index = cfs->get_u2_fast();
    int cp_size = cp->length();
    check_property(
      valid_cp_range(name_index, cp_size) && cp->tag_at(name_index).is_utf8(),
      "Invalid constant pool index %u for field name in class file %s",
      name_index, CHECK_(nullHandle));
    symbolHandle name(THREAD, cp->symbol_at(name_index));
    verify_legal_field_name(name, CHECK_(nullHandle));

    u2 signature_index = cfs->get_u2_fast();
    check_property(
      valid_cp_range(signature_index, cp_size) &&
        cp->tag_at(signature_index).is_utf8(),
      "Invalid constant pool index %u for field signature in class file %s",
      signature_index, CHECK_(nullHandle));
    symbolHandle sig(THREAD, cp->symbol_at(signature_index));
    verify_legal_field_signature(name, sig, CHECK_(nullHandle));

    u2 constantvalue_index = 0;
    bool is_synthetic = false;
    u2 generic_signature_index = 0;
    bool is_static = access_flags.is_static();

    u2 attributes_count = cfs->get_u2_fast();
    if (attributes_count > 0) {
      parse_field_attributes(cp, attributes_count, is_static, signature_index,
                             &constantvalue_index, &is_synthetic,
                             &generic_signature_index, &field_annotations,
                             CHECK_(nullHandle));
      if (field_annotations.not_null()) {
        if (fields_annotations->is_null()) {
          objArrayOop md = oopFactory::new_system_objArray(length, CHECK_(nullHandle));
          *fields_annotations = objArrayHandle(THREAD, md);
        }
        (*fields_annotations)->obj_at_put(n, field_annotations());
      }
      if (is_synthetic) {
        access_flags.set_is_synthetic();
      }
    }

    fields->short_at_put(index++, access_flags.as_short());
    fields->short_at_put(index++, name_index);
    fields->short_at_put(index++, signature_index);
    fields->short_at_put(index++, constantvalue_index);

    // Remember how many oops we encountered and compute allocation type
    BasicType type = cp->basic_type_for_signature_at(signature_index);
    FieldAllocationType atype;
    if ( is_static ) {
      switch ( type ) {
        case  T_BOOLEAN:
        case  T_BYTE:
          fac->static_byte_count++;
          atype = STATIC_BYTE;
          break;
        case  T_LONG:
        case  T_DOUBLE:
          if (Universe::field_type_should_be_aligned(type)) {
            atype = STATIC_ALIGNED_DOUBLE;
          } else {
            atype = STATIC_DOUBLE;
          }
          fac->static_double_count++;
          break;
        case  T_CHAR:
        case  T_SHORT:
          fac->static_short_count++;
          atype = STATIC_SHORT;
          break;
        case  T_FLOAT:
        case  T_INT:
          fac->static_word_count++;
          atype = STATIC_WORD;
          break;
        case  T_ARRAY:
        case  T_OBJECT:
          fac->static_oop_count++;
          atype = STATIC_OOP;
          break;
        case  T_ADDRESS:
        case  T_VOID:
        default:
          assert(0, "bad field type");
      }
    } else {
      switch ( type ) {
        case  T_BOOLEAN:
        case  T_BYTE:
          fac->nonstatic_byte_count++;
          atype = NONSTATIC_BYTE;
          break;
        case  T_LONG:
        case  T_DOUBLE:
          if (Universe::field_type_should_be_aligned(type)) {
            atype = NONSTATIC_ALIGNED_DOUBLE;
          } else {
            atype = NONSTATIC_DOUBLE;
          }
          fac->nonstatic_double_count++;
          break;
        case  T_CHAR:
        case  T_SHORT:
          fac->nonstatic_short_count++;
          atype = NONSTATIC_SHORT;
          break;
        case  T_FLOAT:
        case  T_INT:
          fac->nonstatic_word_count++;
          atype = NONSTATIC_WORD;
          break;
        case  T_ARRAY:
        case  T_OBJECT:
          fac->nonstatic_oop_count++;
          atype = NONSTATIC_OOP;
          break;
        case  T_ADDRESS:
        case  T_VOID:
        default:
          assert(0, "bad field type");
      }
    }

    // The correct offset is computed later (all oop fields will be located together)
    // We temporarily store the allocation type in the offset field
    fields->short_at_put(index++, atype);
    fields->short_at_put(index++, 0);  // Clear out high word of byte offset
    fields->short_at_put(index++, generic_signature_index);
  }

  if (_need_verify && length > 1) {
    // Check duplicated fields
    ResourceMark rm(THREAD);
    NameSigHash** names_and_sigs = NEW_RESOURCE_ARRAY_IN_THREAD(
      THREAD, NameSigHash*, HASH_ROW_SIZE);
    initialize_hashtable(names_and_sigs);
    bool dup = false;
    {
      debug_only(No_Safepoint_Verifier nsv;)
      for (int i = 0; i < length*instanceKlass::next_offset; i += instanceKlass::next_offset) {
        int name_index = fields->ushort_at(i + instanceKlass::name_index_offset);
        symbolOop name = cp->symbol_at(name_index);
        int sig_index = fields->ushort_at(i + instanceKlass::signature_index_offset);
        symbolOop sig = cp->symbol_at(sig_index);
        // If no duplicates, add name/signature in hashtable names_and_sigs.
        if (!put_after_lookup(name, sig, names_and_sigs)) {
          dup = true;
          break;
        }
      }
    }
    if (dup) {
      classfile_parse_error("Duplicate field name&signature in class file %s",
                            CHECK_(nullHandle));
    }
  }

  return fields;
}


static void copy_u2_with_conversion(u2* dest, u2* src, int length) {
  while (length-- > 0) {
    *dest++ = Bytes::get_Java_u2((u1*) (src++));
  }
}


typeArrayHandle ClassFileParser::parse_exception_table(u4 code_length,
                                                       u4 exception_table_length,
                                                       constantPoolHandle cp,
                                                       TRAPS) {
  ClassFileStream* cfs = stream();
  typeArrayHandle nullHandle;

  // 4-tuples of ints [start_pc, end_pc, handler_pc, catch_type index]
  typeArrayOop eh = oopFactory::new_permanent_intArray(exception_table_length*4, CHECK_(nullHandle));
  typeArrayHandle exception_handlers = typeArrayHandle(THREAD, eh);

  int index = 0;
  cfs->guarantee_more(8 * exception_table_length, CHECK_(nullHandle)); // start_pc, end_pc, handler_pc, catch_type_index
  for (unsigned int i = 0; i < exception_table_length; i++) {
    u2 start_pc = cfs->get_u2_fast();
    u2 end_pc = cfs->get_u2_fast();
    u2 handler_pc = cfs->get_u2_fast();
    u2 catch_type_index = cfs->get_u2_fast();
    // Will check legal target after parsing code array in verifier.
    if (_need_verify) {
      guarantee_property((start_pc < end_pc) && (end_pc <= code_length),
                         "Illegal exception table range in class file %s", CHECK_(nullHandle));
      guarantee_property(handler_pc < code_length,
                         "Illegal exception table handler in class file %s", CHECK_(nullHandle));
      if (catch_type_index != 0) {
        guarantee_property(valid_cp_range(catch_type_index, cp->length()) &&
                           is_klass_reference(cp, catch_type_index),
                           "Catch type in exception table has bad constant type in class file %s", CHECK_(nullHandle));
      }
    }
    exception_handlers->int_at_put(index++, start_pc);
    exception_handlers->int_at_put(index++, end_pc);
    exception_handlers->int_at_put(index++, handler_pc);
    exception_handlers->int_at_put(index++, catch_type_index);
  }
  return exception_handlers;
}

void ClassFileParser::parse_linenumber_table(
    u4 code_attribute_length, u4 code_length,
    CompressedLineNumberWriteStream** write_stream, TRAPS) {
  ClassFileStream* cfs = stream();
  unsigned int num_entries = cfs->get_u2(CHECK);

  // Each entry is a u2 start_pc, and a u2 line_number
  unsigned int length_in_bytes = num_entries * (sizeof(u2) + sizeof(u2));

  // Verify line number attribute and table length
  check_property(
    code_attribute_length == sizeof(u2) + length_in_bytes,
    "LineNumberTable attribute has wrong length in class file %s", CHECK);

  cfs->guarantee_more(length_in_bytes, CHECK);

  if ((*write_stream) == NULL) {
    if (length_in_bytes > fixed_buffer_size) {
      (*write_stream) = new CompressedLineNumberWriteStream(length_in_bytes);
    } else {
      (*write_stream) = new CompressedLineNumberWriteStream(
        linenumbertable_buffer, fixed_buffer_size);
    }
  }

  while (num_entries-- > 0) {
    u2 bci  = cfs->get_u2_fast(); // start_pc
    u2 line = cfs->get_u2_fast(); // line_number
    guarantee_property(bci < code_length,
        "Invalid pc in LineNumberTable in class file %s", CHECK);
    (*write_stream)->write_pair(bci, line);
  }
}


// Class file LocalVariableTable elements.
class Classfile_LVT_Element VALUE_OBJ_CLASS_SPEC {
 public:
  u2 start_bci;
  u2 length;
  u2 name_cp_index;
  u2 descriptor_cp_index;
  u2 slot;
};


class LVT_Hash: public CHeapObj {
 public:
  LocalVariableTableElement  *_elem;  // element
  LVT_Hash*                   _next;  // Next entry in hash table
};

unsigned int hash(LocalVariableTableElement *elem) {
  unsigned int raw_hash = elem->start_bci;

  raw_hash = elem->length        + raw_hash * 37;
  raw_hash = elem->name_cp_index + raw_hash * 37;
  raw_hash = elem->slot          + raw_hash * 37;

  return raw_hash % HASH_ROW_SIZE;
}

void initialize_hashtable(LVT_Hash** table) {
  for (int i = 0; i < HASH_ROW_SIZE; i++) {
    table[i] = NULL;
  }
}

void clear_hashtable(LVT_Hash** table) {
  for (int i = 0; i < HASH_ROW_SIZE; i++) {
    LVT_Hash* current = table[i];
    LVT_Hash* next;
    while (current != NULL) {
      next = current->_next;
      current->_next = NULL;
      delete(current);
      current = next;
    }
    table[i] = NULL;
  }
}

LVT_Hash* LVT_lookup(LocalVariableTableElement *elem, int index, LVT_Hash** table) {
  LVT_Hash* entry = table[index];

  /*
   * 3-tuple start_bci/length/slot has to be unique key,
   * so the following comparison seems to be redundant:
   *       && elem->name_cp_index == entry->_elem->name_cp_index
   */
  while (entry != NULL) {
    if (elem->start_bci           == entry->_elem->start_bci
     && elem->length              == entry->_elem->length
     && elem->name_cp_index       == entry->_elem->name_cp_index
     && elem->slot                == entry->_elem->slot
    ) {
      return entry;
    }
    entry = entry->_next;
  }
  return NULL;
}

// Return false if the local variable is found in table.
// Return true if no duplicate is found.
// And local variable is added as a new entry in table.
bool LVT_put_after_lookup(LocalVariableTableElement *elem, LVT_Hash** table) {
  // First lookup for duplicates
  int index = hash(elem);
  LVT_Hash* entry = LVT_lookup(elem, index, table);

  if (entry != NULL) {
      return false;
  }
  // No duplicate is found, allocate a new entry and fill it.
  if ((entry = new LVT_Hash()) == NULL) {
    return false;
  }
  entry->_elem = elem;

  // Insert into hash table
  entry->_next = table[index];
  table[index] = entry;

  return true;
}

void copy_lvt_element(Classfile_LVT_Element *src, LocalVariableTableElement *lvt) {
  lvt->start_bci           = Bytes::get_Java_u2((u1*) &src->start_bci);
  lvt->length              = Bytes::get_Java_u2((u1*) &src->length);
  lvt->name_cp_index       = Bytes::get_Java_u2((u1*) &src->name_cp_index);
  lvt->descriptor_cp_index = Bytes::get_Java_u2((u1*) &src->descriptor_cp_index);
  lvt->signature_cp_index  = 0;
  lvt->slot                = Bytes::get_Java_u2((u1*) &src->slot);
}

// Function is used to parse both attributes:
//       LocalVariableTable (LVT) and LocalVariableTypeTable (LVTT)
u2* ClassFileParser::parse_localvariable_table(u4 code_length,
                                               u2 max_locals,
                                               u4 code_attribute_length,
                                               constantPoolHandle cp,
                                               u2* localvariable_table_length,
                                               bool isLVTT,
                                               TRAPS) {
  ClassFileStream* cfs = stream();
  const char * tbl_name = (isLVTT) ? "LocalVariableTypeTable" : "LocalVariableTable";
  *localvariable_table_length = cfs->get_u2(CHECK_NULL);
  unsigned int size = (*localvariable_table_length) * sizeof(Classfile_LVT_Element) / sizeof(u2);
  // Verify local variable table attribute has right length
  if (_need_verify) {
    guarantee_property(code_attribute_length == (sizeof(*localvariable_table_length) + size * sizeof(u2)),
                       "%s has wrong length in class file %s", tbl_name, CHECK_NULL);
  }
  u2* localvariable_table_start = cfs->get_u2_buffer();
  assert(localvariable_table_start != NULL, "null local variable table");
  if (!_need_verify) {
    cfs->skip_u2_fast(size);
  } else {
    cfs->guarantee_more(size * 2, CHECK_NULL);
    for(int i = 0; i < (*localvariable_table_length); i++) {
      u2 start_pc = cfs->get_u2_fast();
      u2 length = cfs->get_u2_fast();
      u2 name_index = cfs->get_u2_fast();
      u2 descriptor_index = cfs->get_u2_fast();
      u2 index = cfs->get_u2_fast();
      // Assign to a u4 to avoid overflow
      u4 end_pc = (u4)start_pc + (u4)length;

      if (start_pc >= code_length) {
        classfile_parse_error(
          "Invalid start_pc %u in %s in class file %s",
          start_pc, tbl_name, CHECK_NULL);
      }
      if (end_pc > code_length) {
        classfile_parse_error(
          "Invalid length %u in %s in class file %s",
          length, tbl_name, CHECK_NULL);
      }
      int cp_size = cp->length();
      guarantee_property(
        valid_cp_range(name_index, cp_size) &&
          cp->tag_at(name_index).is_utf8(),
        "Name index %u in %s has bad constant type in class file %s",
        name_index, tbl_name, CHECK_NULL);
      guarantee_property(
        valid_cp_range(descriptor_index, cp_size) &&
          cp->tag_at(descriptor_index).is_utf8(),
        "Signature index %u in %s has bad constant type in class file %s",
        descriptor_index, tbl_name, CHECK_NULL);

      symbolHandle name(THREAD, cp->symbol_at(name_index));
      symbolHandle sig(THREAD, cp->symbol_at(descriptor_index));
      verify_legal_field_name(name, CHECK_NULL);
      u2 extra_slot = 0;
      if (!isLVTT) {
        verify_legal_field_signature(name, sig, CHECK_NULL);

        // 4894874: check special cases for double and long local variables
        if (sig() == vmSymbols::type_signature(T_DOUBLE) ||
            sig() == vmSymbols::type_signature(T_LONG)) {
          extra_slot = 1;
        }
      }
      guarantee_property((index + extra_slot) < max_locals,
                          "Invalid index %u in %s in class file %s",
                          index, tbl_name, CHECK_NULL);
    }
  }
  return localvariable_table_start;
}


void ClassFileParser::parse_type_array(u2 array_length, u4 code_length, u4* u1_index, u4* u2_index,
                                      u1* u1_array, u2* u2_array, constantPoolHandle cp, TRAPS) {
  ClassFileStream* cfs = stream();
  u2 index = 0; // index in the array with long/double occupying two slots
  u4 i1 = *u1_index;
  u4 i2 = *u2_index + 1;
  for(int i = 0; i < array_length; i++) {
    u1 tag = u1_array[i1++] = cfs->get_u1(CHECK);
    index++;
    if (tag == ITEM_Long || tag == ITEM_Double) {
      index++;
    } else if (tag == ITEM_Object) {
      u2 class_index = u2_array[i2++] = cfs->get_u2(CHECK);
      guarantee_property(valid_cp_range(class_index, cp->length()) &&
                         is_klass_reference(cp, class_index),
                         "Bad class index %u in StackMap in class file %s",
                         class_index, CHECK);
    } else if (tag == ITEM_Uninitialized) {
      u2 offset = u2_array[i2++] = cfs->get_u2(CHECK);
      guarantee_property(
        offset < code_length,
        "Bad uninitialized type offset %u in StackMap in class file %s",
        offset, CHECK);
    } else {
      guarantee_property(
        tag <= (u1)ITEM_Uninitialized,
        "Unknown variable type %u in StackMap in class file %s",
        tag, CHECK);
    }
  }
  u2_array[*u2_index] = index;
  *u1_index = i1;
  *u2_index = i2;
}

typeArrayOop ClassFileParser::parse_stackmap_table(
    u4 code_attribute_length, TRAPS) {
  if (code_attribute_length == 0)
    return NULL;

  ClassFileStream* cfs = stream();
  u1* stackmap_table_start = cfs->get_u1_buffer();
  assert(stackmap_table_start != NULL, "null stackmap table");

  // check code_attribute_length first
  stream()->skip_u1(code_attribute_length, CHECK_NULL);

  if (!_need_verify && !DumpSharedSpaces) {
    return NULL;
  }

  typeArrayOop stackmap_data =
    oopFactory::new_permanent_byteArray(code_attribute_length, CHECK_NULL);

  stackmap_data->set_length(code_attribute_length);
  memcpy((void*)stackmap_data->byte_at_addr(0),
         (void*)stackmap_table_start, code_attribute_length);
  return stackmap_data;
}

u2* ClassFileParser::parse_checked_exceptions(u2* checked_exceptions_length,
                                              u4 method_attribute_length,
                                              constantPoolHandle cp, TRAPS) {
  ClassFileStream* cfs = stream();
  cfs->guarantee_more(2, CHECK_NULL);  // checked_exceptions_length
  *checked_exceptions_length = cfs->get_u2_fast();
  unsigned int size = (*checked_exceptions_length) * sizeof(CheckedExceptionElement) / sizeof(u2);
  u2* checked_exceptions_start = cfs->get_u2_buffer();
  assert(checked_exceptions_start != NULL, "null checked exceptions");
  if (!_need_verify) {
    cfs->skip_u2_fast(size);
  } else {
    // Verify each value in the checked exception table
    u2 checked_exception;
    u2 len = *checked_exceptions_length;
    cfs->guarantee_more(2 * len, CHECK_NULL);
    for (int i = 0; i < len; i++) {
      checked_exception = cfs->get_u2_fast();
      check_property(
        valid_cp_range(checked_exception, cp->length()) &&
        is_klass_reference(cp, checked_exception),
        "Exception name has bad type at constant pool %u in class file %s",
        checked_exception, CHECK_NULL);
    }
  }
  // check exceptions attribute length
  if (_need_verify) {
    guarantee_property(method_attribute_length == (sizeof(*checked_exceptions_length) +
                                                   sizeof(u2) * size),
                      "Exceptions attribute has wrong length in class file %s", CHECK_NULL);
  }
  return checked_exceptions_start;
}


#define MAX_ARGS_SIZE 255
#define MAX_CODE_SIZE 65535
#define INITIAL_MAX_LVT_NUMBER 256

// Note: the parse_method below is big and clunky because all parsing of the code and exceptions
// attribute is inlined. This is curbersome to avoid since we inline most of the parts in the
// methodOop to save footprint, so we only know the size of the resulting methodOop when the
// entire method attribute is parsed.
//
// The promoted_flags parameter is used to pass relevant access_flags
// from the method back up to the containing klass. These flag values
// are added to klass's access_flags.

methodHandle ClassFileParser::parse_method(constantPoolHandle cp, bool is_interface,
                                           AccessFlags *promoted_flags,
                                           typeArrayHandle* method_annotations,
                                           typeArrayHandle* method_parameter_annotations,
                                           typeArrayHandle* method_default_annotations,
                                           TRAPS) {
  ClassFileStream* cfs = stream();
  methodHandle nullHandle;
  ResourceMark rm(THREAD);
  // Parse fixed parts
  cfs->guarantee_more(8, CHECK_(nullHandle)); // access_flags, name_index, descriptor_index, attributes_count

  int flags = cfs->get_u2_fast();
  u2 name_index = cfs->get_u2_fast();
  int cp_size = cp->length();
  check_property(
    valid_cp_range(name_index, cp_size) &&
      cp->tag_at(name_index).is_utf8(),
    "Illegal constant pool index %u for method name in class file %s",
    name_index, CHECK_(nullHandle));
  symbolHandle name(THREAD, cp->symbol_at(name_index));
  verify_legal_method_name(name, CHECK_(nullHandle));

  u2 signature_index = cfs->get_u2_fast();
  guarantee_property(
    valid_cp_range(signature_index, cp_size) &&
      cp->tag_at(signature_index).is_utf8(),
    "Illegal constant pool index %u for method signature in class file %s",
    signature_index, CHECK_(nullHandle));
  symbolHandle signature(THREAD, cp->symbol_at(signature_index));

  AccessFlags access_flags;
  if (name == vmSymbols::class_initializer_name()) {
    // We ignore the access flags for a class initializer. (JVM Spec. p. 116)
    flags = JVM_ACC_STATIC;
  } else {
    verify_legal_method_modifiers(flags, is_interface, name, CHECK_(nullHandle));
  }

  int args_size = -1;  // only used when _need_verify is true
  if (_need_verify) {
    args_size = ((flags & JVM_ACC_STATIC) ? 0 : 1) +
                 verify_legal_method_signature(name, signature, CHECK_(nullHandle));
    if (args_size > MAX_ARGS_SIZE) {
      classfile_parse_error("Too many arguments in method signature in class file %s", CHECK_(nullHandle));
    }
  }

  access_flags.set_flags(flags & JVM_RECOGNIZED_METHOD_MODIFIERS);

  // Default values for code and exceptions attribute elements
  u2 max_stack = 0;
  u2 max_locals = 0;
  u4 code_length = 0;
  u1* code_start = 0;
  u2 exception_table_length = 0;
  typeArrayHandle exception_handlers(THREAD, Universe::the_empty_int_array());
  u2 checked_exceptions_length = 0;
  u2* checked_exceptions_start = NULL;
  CompressedLineNumberWriteStream* linenumber_table = NULL;
  int linenumber_table_length = 0;
  int total_lvt_length = 0;
  u2 lvt_cnt = 0;
  u2 lvtt_cnt = 0;
  bool lvt_allocated = false;
  u2 max_lvt_cnt = INITIAL_MAX_LVT_NUMBER;
  u2 max_lvtt_cnt = INITIAL_MAX_LVT_NUMBER;
  u2* localvariable_table_length;
  u2** localvariable_table_start;
  u2* localvariable_type_table_length;
  u2** localvariable_type_table_start;
  bool parsed_code_attribute = false;
  bool parsed_checked_exceptions_attribute = false;
  bool parsed_stackmap_attribute = false;
  // stackmap attribute - JDK1.5
  typeArrayHandle stackmap_data;
  u2 generic_signature_index = 0;
  u1* runtime_visible_annotations = NULL;
  int runtime_visible_annotations_length = 0;
  u1* runtime_invisible_annotations = NULL;
  int runtime_invisible_annotations_length = 0;
  u1* runtime_visible_parameter_annotations = NULL;
  int runtime_visible_parameter_annotations_length = 0;
  u1* runtime_invisible_parameter_annotations = NULL;
  int runtime_invisible_parameter_annotations_length = 0;
  u1* annotation_default = NULL;
  int annotation_default_length = 0;

  // Parse code and exceptions attribute
  u2 method_attributes_count = cfs->get_u2_fast();
  while (method_attributes_count--) {
    cfs->guarantee_more(6, CHECK_(nullHandle));  // method_attribute_name_index, method_attribute_length
    u2 method_attribute_name_index = cfs->get_u2_fast();
    u4 method_attribute_length = cfs->get_u4_fast();
    check_property(
      valid_cp_range(method_attribute_name_index, cp_size) &&
        cp->tag_at(method_attribute_name_index).is_utf8(),
      "Invalid method attribute name index %u in class file %s",
      method_attribute_name_index, CHECK_(nullHandle));

    symbolOop method_attribute_name = cp->symbol_at(method_attribute_name_index);
    if (method_attribute_name == vmSymbols::tag_code()) {
      // Parse Code attribute
      if (_need_verify) {
        guarantee_property(!access_flags.is_native() && !access_flags.is_abstract(),
                        "Code attribute in native or abstract methods in class file %s",
                         CHECK_(nullHandle));
      }
      if (parsed_code_attribute) {
        classfile_parse_error("Multiple Code attributes in class file %s", CHECK_(nullHandle));
      }
      parsed_code_attribute = true;

      // Stack size, locals size, and code size
      if (_major_version == 45 && _minor_version <= 2) {
        cfs->guarantee_more(4, CHECK_(nullHandle));
        max_stack = cfs->get_u1_fast();
        max_locals = cfs->get_u1_fast();
        code_length = cfs->get_u2_fast();
      } else {
        cfs->guarantee_more(8, CHECK_(nullHandle));
        max_stack = cfs->get_u2_fast();
        max_locals = cfs->get_u2_fast();
        code_length = cfs->get_u4_fast();
      }
      if (_need_verify) {
        guarantee_property(args_size <= max_locals,
                           "Arguments can't fit into locals in class file %s", CHECK_(nullHandle));
        guarantee_property(code_length > 0 && code_length <= MAX_CODE_SIZE,
                           "Invalid method Code length %u in class file %s",
                           code_length, CHECK_(nullHandle));
      }
      // Code pointer
      code_start = cfs->get_u1_buffer();
      assert(code_start != NULL, "null code start");
      cfs->guarantee_more(code_length, CHECK_(nullHandle));
      cfs->skip_u1_fast(code_length);

      // Exception handler table
      cfs->guarantee_more(2, CHECK_(nullHandle));  // exception_table_length
      exception_table_length = cfs->get_u2_fast();
      if (exception_table_length > 0) {
        exception_handlers =
              parse_exception_table(code_length, exception_table_length, cp, CHECK_(nullHandle));
      }

      // Parse additional attributes in code attribute
      cfs->guarantee_more(2, CHECK_(nullHandle));  // code_attributes_count
      u2 code_attributes_count = cfs->get_u2_fast();

      unsigned int calculated_attribute_length = 0;

      if (_major_version > 45 || (_major_version == 45 && _minor_version > 2)) {
        calculated_attribute_length =
            sizeof(max_stack) + sizeof(max_locals) + sizeof(code_length);
      } else {
        // max_stack, locals and length are smaller in pre-version 45.2 classes
        calculated_attribute_length = sizeof(u1) + sizeof(u1) + sizeof(u2);
      }
      calculated_attribute_length +=
        code_length +
        sizeof(exception_table_length) +
        sizeof(code_attributes_count) +
        exception_table_length *
            ( sizeof(u2) +   // start_pc
              sizeof(u2) +   // end_pc
              sizeof(u2) +   // handler_pc
              sizeof(u2) );  // catch_type_index

      while (code_attributes_count--) {
        cfs->guarantee_more(6, CHECK_(nullHandle));  // code_attribute_name_index, code_attribute_length
        u2 code_attribute_name_index = cfs->get_u2_fast();
        u4 code_attribute_length = cfs->get_u4_fast();
        calculated_attribute_length += code_attribute_length +
                                       sizeof(code_attribute_name_index) +
                                       sizeof(code_attribute_length);
        check_property(valid_cp_range(code_attribute_name_index, cp_size) &&
                       cp->tag_at(code_attribute_name_index).is_utf8(),
                       "Invalid code attribute name index %u in class file %s",
                       code_attribute_name_index,
                       CHECK_(nullHandle));
        if (LoadLineNumberTables &&
            cp->symbol_at(code_attribute_name_index) == vmSymbols::tag_line_number_table()) {
          // Parse and compress line number table
          parse_linenumber_table(code_attribute_length, code_length,
            &linenumber_table, CHECK_(nullHandle));

        } else if (LoadLocalVariableTables &&
                   cp->symbol_at(code_attribute_name_index) == vmSymbols::tag_local_variable_table()) {
          // Parse local variable table
          if (!lvt_allocated) {
            localvariable_table_length = NEW_RESOURCE_ARRAY_IN_THREAD(
              THREAD, u2,  INITIAL_MAX_LVT_NUMBER);
            localvariable_table_start = NEW_RESOURCE_ARRAY_IN_THREAD(
              THREAD, u2*, INITIAL_MAX_LVT_NUMBER);
            localvariable_type_table_length = NEW_RESOURCE_ARRAY_IN_THREAD(
              THREAD, u2,  INITIAL_MAX_LVT_NUMBER);
            localvariable_type_table_start = NEW_RESOURCE_ARRAY_IN_THREAD(
              THREAD, u2*, INITIAL_MAX_LVT_NUMBER);
            lvt_allocated = true;
          }
          if (lvt_cnt == max_lvt_cnt) {
            max_lvt_cnt <<= 1;
            REALLOC_RESOURCE_ARRAY(u2, localvariable_table_length, lvt_cnt, max_lvt_cnt);
            REALLOC_RESOURCE_ARRAY(u2*, localvariable_table_start, lvt_cnt, max_lvt_cnt);
          }
          localvariable_table_start[lvt_cnt] =
            parse_localvariable_table(code_length,
                                      max_locals,
                                      code_attribute_length,
                                      cp,
                                      &localvariable_table_length[lvt_cnt],
                                      false,    // is not LVTT
                                      CHECK_(nullHandle));
          total_lvt_length += localvariable_table_length[lvt_cnt];
          lvt_cnt++;
        } else if (LoadLocalVariableTypeTables &&
                   _major_version >= JAVA_1_5_VERSION &&
                   cp->symbol_at(code_attribute_name_index) == vmSymbols::tag_local_variable_type_table()) {
          if (!lvt_allocated) {
            localvariable_table_length = NEW_RESOURCE_ARRAY_IN_THREAD(
              THREAD, u2,  INITIAL_MAX_LVT_NUMBER);
            localvariable_table_start = NEW_RESOURCE_ARRAY_IN_THREAD(
              THREAD, u2*, INITIAL_MAX_LVT_NUMBER);
            localvariable_type_table_length = NEW_RESOURCE_ARRAY_IN_THREAD(
              THREAD, u2,  INITIAL_MAX_LVT_NUMBER);
            localvariable_type_table_start = NEW_RESOURCE_ARRAY_IN_THREAD(
              THREAD, u2*, INITIAL_MAX_LVT_NUMBER);
            lvt_allocated = true;
          }
          // Parse local variable type table
          if (lvtt_cnt == max_lvtt_cnt) {
            max_lvtt_cnt <<= 1;
            REALLOC_RESOURCE_ARRAY(u2, localvariable_type_table_length, lvtt_cnt, max_lvtt_cnt);
            REALLOC_RESOURCE_ARRAY(u2*, localvariable_type_table_start, lvtt_cnt, max_lvtt_cnt);
          }
          localvariable_type_table_start[lvtt_cnt] =
            parse_localvariable_table(code_length,
                                      max_locals,
                                      code_attribute_length,
                                      cp,
                                      &localvariable_type_table_length[lvtt_cnt],
                                      true,     // is LVTT
                                      CHECK_(nullHandle));
          lvtt_cnt++;
        } else if (UseSplitVerifier &&
                   _major_version >= Verifier::STACKMAP_ATTRIBUTE_MAJOR_VERSION &&
                   cp->symbol_at(code_attribute_name_index) == vmSymbols::tag_stack_map_table()) {
          // Stack map is only needed by the new verifier in JDK1.5.
          if (parsed_stackmap_attribute) {
            classfile_parse_error("Multiple StackMapTable attributes in class file %s", CHECK_(nullHandle));
          }
          typeArrayOop sm =
            parse_stackmap_table(code_attribute_length, CHECK_(nullHandle));
          stackmap_data = typeArrayHandle(THREAD, sm);
          parsed_stackmap_attribute = true;
        } else {
          // Skip unknown attributes
          cfs->skip_u1(code_attribute_length, CHECK_(nullHandle));
        }
      }
      // check method attribute length
      if (_need_verify) {
        guarantee_property(method_attribute_length == calculated_attribute_length,
                           "Code segment has wrong length in class file %s", CHECK_(nullHandle));
      }
    } else if (method_attribute_name == vmSymbols::tag_exceptions()) {
      // Parse Exceptions attribute
      if (parsed_checked_exceptions_attribute) {
        classfile_parse_error("Multiple Exceptions attributes in class file %s", CHECK_(nullHandle));
      }
      parsed_checked_exceptions_attribute = true;
      checked_exceptions_start =
            parse_checked_exceptions(&checked_exceptions_length,
                                     method_attribute_length,
                                     cp, CHECK_(nullHandle));
    } else if (method_attribute_name == vmSymbols::tag_synthetic()) {
      if (method_attribute_length != 0) {
        classfile_parse_error(
          "Invalid Synthetic method attribute length %u in class file %s",
          method_attribute_length, CHECK_(nullHandle));
      }
      // Should we check that there hasn't already been a synthetic attribute?
      access_flags.set_is_synthetic();
    } else if (method_attribute_name == vmSymbols::tag_deprecated()) { // 4276120
      if (method_attribute_length != 0) {
        classfile_parse_error(
          "Invalid Deprecated method attribute length %u in class file %s",
          method_attribute_length, CHECK_(nullHandle));
      }
    } else if (_major_version >= JAVA_1_5_VERSION) {
      if (method_attribute_name == vmSymbols::tag_signature()) {
        if (method_attribute_length != 2) {
          classfile_parse_error(
            "Invalid Signature attribute length %u in class file %s",
            method_attribute_length, CHECK_(nullHandle));
        }
        cfs->guarantee_more(2, CHECK_(nullHandle));  // generic_signature_index
        generic_signature_index = cfs->get_u2_fast();
      } else if (method_attribute_name == vmSymbols::tag_runtime_visible_annotations()) {
        runtime_visible_annotations_length = method_attribute_length;
        runtime_visible_annotations = cfs->get_u1_buffer();
        assert(runtime_visible_annotations != NULL, "null visible annotations");
        cfs->skip_u1(runtime_visible_annotations_length, CHECK_(nullHandle));
      } else if (PreserveAllAnnotations && method_attribute_name == vmSymbols::tag_runtime_invisible_annotations()) {
        runtime_invisible_annotations_length = method_attribute_length;
        runtime_invisible_annotations = cfs->get_u1_buffer();
        assert(runtime_invisible_annotations != NULL, "null invisible annotations");
        cfs->skip_u1(runtime_invisible_annotations_length, CHECK_(nullHandle));
      } else if (method_attribute_name == vmSymbols::tag_runtime_visible_parameter_annotations()) {
        runtime_visible_parameter_annotations_length = method_attribute_length;
        runtime_visible_parameter_annotations = cfs->get_u1_buffer();
        assert(runtime_visible_parameter_annotations != NULL, "null visible parameter annotations");
        cfs->skip_u1(runtime_visible_parameter_annotations_length, CHECK_(nullHandle));
      } else if (PreserveAllAnnotations && method_attribute_name == vmSymbols::tag_runtime_invisible_parameter_annotations()) {
        runtime_invisible_parameter_annotations_length = method_attribute_length;
        runtime_invisible_parameter_annotations = cfs->get_u1_buffer();
        assert(runtime_invisible_parameter_annotations != NULL, "null invisible parameter annotations");
        cfs->skip_u1(runtime_invisible_parameter_annotations_length, CHECK_(nullHandle));
      } else if (method_attribute_name == vmSymbols::tag_annotation_default()) {
        annotation_default_length = method_attribute_length;
        annotation_default = cfs->get_u1_buffer();
        assert(annotation_default != NULL, "null annotation default");
        cfs->skip_u1(annotation_default_length, CHECK_(nullHandle));
      } else {
        // Skip unknown attributes
        cfs->skip_u1(method_attribute_length, CHECK_(nullHandle));
      }
    } else {
      // Skip unknown attributes
      cfs->skip_u1(method_attribute_length, CHECK_(nullHandle));
    }
  }

  if (linenumber_table != NULL) {
    linenumber_table->write_terminator();
    linenumber_table_length = linenumber_table->position();
  }

  // Make sure there's at least one Code attribute in non-native/non-abstract method
  if (_need_verify) {
    guarantee_property(access_flags.is_native() || access_flags.is_abstract() || parsed_code_attribute,
                      "Absent Code attribute in method that is not native or abstract in class file %s", CHECK_(nullHandle));
  }

  // All sizing information for a methodOop is finally available, now create it
  methodOop m_oop  = oopFactory::new_method(
    code_length, access_flags, linenumber_table_length,
    total_lvt_length, checked_exceptions_length,
    methodOopDesc::IsSafeConc, CHECK_(nullHandle));
  methodHandle m (THREAD, m_oop);

  ClassLoadingService::add_class_method_size(m_oop->size()*HeapWordSize);

  // Fill in information from fixed part (access_flags already set)
  m->set_constants(cp());
  m->set_name_index(name_index);
  m->set_signature_index(signature_index);
  m->set_generic_signature_index(generic_signature_index);
#ifdef CC_INTERP
  // hmm is there a gc issue here??
  ResultTypeFinder rtf(cp->symbol_at(signature_index));
  m->set_result_index(rtf.type());
#endif

  if (args_size >= 0) {
    m->set_size_of_parameters(args_size);
  } else {
    m->compute_size_of_parameters(THREAD);
  }
#ifdef ASSERT
  if (args_size >= 0) {
    m->compute_size_of_parameters(THREAD);
    assert(args_size == m->size_of_parameters(), "");
  }
#endif

  // Fill in code attribute information
  m->set_max_stack(max_stack);
  m->set_max_locals(max_locals);
  m->constMethod()->set_stackmap_data(stackmap_data());

  /**
   * The exception_table field is the flag used to indicate
   * that the methodOop and it's associated constMethodOop are partially
   * initialized and thus are exempt from pre/post GC verification.  Once
   * the field is set, the oops are considered fully initialized so make
   * sure that the oops can pass verification when this field is set.
   */
  m->set_exception_table(exception_handlers());

  // Copy byte codes
  if (code_length > 0) {
    memcpy(m->code_base(), code_start, code_length);
  }

  // Copy line number table
  if (linenumber_table != NULL) {
    memcpy(m->compressed_linenumber_table(),
           linenumber_table->buffer(), linenumber_table_length);
  }

  // Copy checked exceptions
  if (checked_exceptions_length > 0) {
    int size = checked_exceptions_length * sizeof(CheckedExceptionElement) / sizeof(u2);
    copy_u2_with_conversion((u2*) m->checked_exceptions_start(), checked_exceptions_start, size);
  }

  /* Copy class file LVT's/LVTT's into the HotSpot internal LVT.
   *
   * Rules for LVT's and LVTT's are:
   *   - There can be any number of LVT's and LVTT's.
   *   - If there are n LVT's, it is the same as if there was just
   *     one LVT containing all the entries from the n LVT's.
   *   - There may be no more than one LVT entry per local variable.
   *     Two LVT entries are 'equal' if these fields are the same:
   *        start_pc, length, name, slot
   *   - There may be no more than one LVTT entry per each LVT entry.
   *     Each LVTT entry has to match some LVT entry.
   *   - HotSpot internal LVT keeps natural ordering of class file LVT entries.
   */
  if (total_lvt_length > 0) {
    int tbl_no, idx;

    promoted_flags->set_has_localvariable_table();

    LVT_Hash** lvt_Hash = NEW_RESOURCE_ARRAY(LVT_Hash*, HASH_ROW_SIZE);
    initialize_hashtable(lvt_Hash);

    // To fill LocalVariableTable in
    Classfile_LVT_Element*  cf_lvt;
    LocalVariableTableElement* lvt = m->localvariable_table_start();

    for (tbl_no = 0; tbl_no < lvt_cnt; tbl_no++) {
      cf_lvt = (Classfile_LVT_Element *) localvariable_table_start[tbl_no];
      for (idx = 0; idx < localvariable_table_length[tbl_no]; idx++, lvt++) {
        copy_lvt_element(&cf_lvt[idx], lvt);
        // If no duplicates, add LVT elem in hashtable lvt_Hash.
        if (LVT_put_after_lookup(lvt, lvt_Hash) == false
          && _need_verify
          && _major_version >= JAVA_1_5_VERSION ) {
          clear_hashtable(lvt_Hash);
          classfile_parse_error("Duplicated LocalVariableTable attribute "
                                "entry for '%s' in class file %s",
                                 cp->symbol_at(lvt->name_cp_index)->as_utf8(),
                                 CHECK_(nullHandle));
        }
      }
    }

    // To merge LocalVariableTable and LocalVariableTypeTable
    Classfile_LVT_Element* cf_lvtt;
    LocalVariableTableElement lvtt_elem;

    for (tbl_no = 0; tbl_no < lvtt_cnt; tbl_no++) {
      cf_lvtt = (Classfile_LVT_Element *) localvariable_type_table_start[tbl_no];
      for (idx = 0; idx < localvariable_type_table_length[tbl_no]; idx++) {
        copy_lvt_element(&cf_lvtt[idx], &lvtt_elem);
        int index = hash(&lvtt_elem);
        LVT_Hash* entry = LVT_lookup(&lvtt_elem, index, lvt_Hash);
        if (entry == NULL) {
          if (_need_verify) {
            clear_hashtable(lvt_Hash);
            classfile_parse_error("LVTT entry for '%s' in class file %s "
                                  "does not match any LVT entry",
                                   cp->symbol_at(lvtt_elem.name_cp_index)->as_utf8(),
                                   CHECK_(nullHandle));
          }
        } else if (entry->_elem->signature_cp_index != 0 && _need_verify) {
          clear_hashtable(lvt_Hash);
          classfile_parse_error("Duplicated LocalVariableTypeTable attribute "
                                "entry for '%s' in class file %s",
                                 cp->symbol_at(lvtt_elem.name_cp_index)->as_utf8(),
                                 CHECK_(nullHandle));
        } else {
          // to add generic signatures into LocalVariableTable
          entry->_elem->signature_cp_index = lvtt_elem.descriptor_cp_index;
        }
      }
    }
    clear_hashtable(lvt_Hash);
  }

  *method_annotations = assemble_annotations(runtime_visible_annotations,
                                             runtime_visible_annotations_length,
                                             runtime_invisible_annotations,
                                             runtime_invisible_annotations_length,
                                             CHECK_(nullHandle));
  *method_parameter_annotations = assemble_annotations(runtime_visible_parameter_annotations,
                                                       runtime_visible_parameter_annotations_length,
                                                       runtime_invisible_parameter_annotations,
                                                       runtime_invisible_parameter_annotations_length,
                                                       CHECK_(nullHandle));
  *method_default_annotations = assemble_annotations(annotation_default,
                                                     annotation_default_length,
                                                     NULL,
                                                     0,
                                                     CHECK_(nullHandle));

  if (name() == vmSymbols::finalize_method_name() &&
      signature() == vmSymbols::void_method_signature()) {
    if (m->is_empty_method()) {
      _has_empty_finalizer = true;
    } else {
      _has_finalizer = true;
    }
  }
  if (name() == vmSymbols::object_initializer_name() &&
      signature() == vmSymbols::void_method_signature() &&
      m->is_vanilla_constructor()) {
    _has_vanilla_constructor = true;
  }

  if (EnableMethodHandles && m->is_method_handle_invoke()) {
    THROW_MSG_(vmSymbols::java_lang_VirtualMachineError(),
               "Method handle invokers must be defined internally to the VM", nullHandle);
  }

  return m;
}


// The promoted_flags parameter is used to pass relevant access_flags
// from the methods back up to the containing klass. These flag values
// are added to klass's access_flags.

objArrayHandle ClassFileParser::parse_methods(constantPoolHandle cp, bool is_interface,
                                              AccessFlags* promoted_flags,
                                              bool* has_final_method,
                                              objArrayOop* methods_annotations_oop,
                                              objArrayOop* methods_parameter_annotations_oop,
                                              objArrayOop* methods_default_annotations_oop,
                                              TRAPS) {
  ClassFileStream* cfs = stream();
  objArrayHandle nullHandle;
  typeArrayHandle method_annotations;
  typeArrayHandle method_parameter_annotations;
  typeArrayHandle method_default_annotations;
  cfs->guarantee_more(2, CHECK_(nullHandle));  // length
  u2 length = cfs->get_u2_fast();
  if (length == 0) {
    return objArrayHandle(THREAD, Universe::the_empty_system_obj_array());
  } else {
    objArrayOop m = oopFactory::new_system_objArray(length, CHECK_(nullHandle));
    objArrayHandle methods(THREAD, m);
    HandleMark hm(THREAD);
    objArrayHandle methods_annotations;
    objArrayHandle methods_parameter_annotations;
    objArrayHandle methods_default_annotations;
    for (int index = 0; index < length; index++) {
      methodHandle method = parse_method(cp, is_interface,
                                         promoted_flags,
                                         &method_annotations,
                                         &method_parameter_annotations,
                                         &method_default_annotations,
                                         CHECK_(nullHandle));
      if (method->is_final()) {
        *has_final_method = true;
      }
      methods->obj_at_put(index, method());
      if (method_annotations.not_null()) {
        if (methods_annotations.is_null()) {
          objArrayOop md = oopFactory::new_system_objArray(length, CHECK_(nullHandle));
          methods_annotations = objArrayHandle(THREAD, md);
        }
        methods_annotations->obj_at_put(index, method_annotations());
      }
      if (method_parameter_annotations.not_null()) {
        if (methods_parameter_annotations.is_null()) {
          objArrayOop md = oopFactory::new_system_objArray(length, CHECK_(nullHandle));
          methods_parameter_annotations = objArrayHandle(THREAD, md);
        }
        methods_parameter_annotations->obj_at_put(index, method_parameter_annotations());
      }
      if (method_default_annotations.not_null()) {
        if (methods_default_annotations.is_null()) {
          objArrayOop md = oopFactory::new_system_objArray(length, CHECK_(nullHandle));
          methods_default_annotations = objArrayHandle(THREAD, md);
        }
        methods_default_annotations->obj_at_put(index, method_default_annotations());
      }
    }
    if (_need_verify && length > 1) {
      // Check duplicated methods
      ResourceMark rm(THREAD);
      NameSigHash** names_and_sigs = NEW_RESOURCE_ARRAY_IN_THREAD(
        THREAD, NameSigHash*, HASH_ROW_SIZE);
      initialize_hashtable(names_and_sigs);
      bool dup = false;
      {
        debug_only(No_Safepoint_Verifier nsv;)
        for (int i = 0; i < length; i++) {
          methodOop m = (methodOop)methods->obj_at(i);
          // If no duplicates, add name/signature in hashtable names_and_sigs.
          if (!put_after_lookup(m->name(), m->signature(), names_and_sigs)) {
            dup = true;
            break;
          }
        }
      }
      if (dup) {
        classfile_parse_error("Duplicate method name&signature in class file %s",
                              CHECK_(nullHandle));
      }
    }

    *methods_annotations_oop = methods_annotations();
    *methods_parameter_annotations_oop = methods_parameter_annotations();
    *methods_default_annotations_oop = methods_default_annotations();

    return methods;
  }
}


typeArrayHandle ClassFileParser::sort_methods(objArrayHandle methods,
                                              objArrayHandle methods_annotations,
                                              objArrayHandle methods_parameter_annotations,
                                              objArrayHandle methods_default_annotations,
                                              TRAPS) {
  typeArrayHandle nullHandle;
  int length = methods()->length();
  // If JVMTI original method ordering is enabled we have to
  // remember the original class file ordering.
  // We temporarily use the vtable_index field in the methodOop to store the
  // class file index, so we can read in after calling qsort.
  if (JvmtiExport::can_maintain_original_method_order()) {
    for (int index = 0; index < length; index++) {
      methodOop m = methodOop(methods->obj_at(index));
      assert(!m->valid_vtable_index(), "vtable index should not be set");
      m->set_vtable_index(index);
    }
  }
  // Sort method array by ascending method name (for faster lookups & vtable construction)
  // Note that the ordering is not alphabetical, see symbolOopDesc::fast_compare
  methodOopDesc::sort_methods(methods(),
                              methods_annotations(),
                              methods_parameter_annotations(),
                              methods_default_annotations());

  // If JVMTI original method ordering is enabled construct int array remembering the original ordering
  if (JvmtiExport::can_maintain_original_method_order()) {
    typeArrayOop new_ordering = oopFactory::new_permanent_intArray(length, CHECK_(nullHandle));
    typeArrayHandle method_ordering(THREAD, new_ordering);
    for (int index = 0; index < length; index++) {
      methodOop m = methodOop(methods->obj_at(index));
      int old_index = m->vtable_index();
      assert(old_index >= 0 && old_index < length, "invalid method index");
      method_ordering->int_at_put(index, old_index);
      m->set_vtable_index(methodOopDesc::invalid_vtable_index);
    }
    return method_ordering;
  } else {
    return typeArrayHandle(THREAD, Universe::the_empty_int_array());
  }
}


void ClassFileParser::parse_classfile_sourcefile_attribute(constantPoolHandle cp, instanceKlassHandle k, TRAPS) {
  ClassFileStream* cfs = stream();
  cfs->guarantee_more(2, CHECK);  // sourcefile_index
  u2 sourcefile_index = cfs->get_u2_fast();
  check_property(
    valid_cp_range(sourcefile_index, cp->length()) &&
      cp->tag_at(sourcefile_index).is_utf8(),
    "Invalid SourceFile attribute at constant pool index %u in class file %s",
    sourcefile_index, CHECK);
  k->set_source_file_name(cp->symbol_at(sourcefile_index));
}



void ClassFileParser::parse_classfile_source_debug_extension_attribute(constantPoolHandle cp,
                                                                       instanceKlassHandle k,
                                                                       int length, TRAPS) {
  ClassFileStream* cfs = stream();
  u1* sde_buffer = cfs->get_u1_buffer();
  assert(sde_buffer != NULL, "null sde buffer");

  // Don't bother storing it if there is no way to retrieve it
  if (JvmtiExport::can_get_source_debug_extension()) {
    // Optimistically assume that only 1 byte UTF format is used
    // (common case)
    symbolOop sde_symbol = oopFactory::new_symbol((char*)sde_buffer,
                                                  length, CHECK);
    k->set_source_debug_extension(sde_symbol);
  }
  // Got utf8 string, set stream position forward
  cfs->skip_u1(length, CHECK);
}


// Inner classes can be static, private or protected (classic VM does this)
#define RECOGNIZED_INNER_CLASS_MODIFIERS (JVM_RECOGNIZED_CLASS_MODIFIERS | JVM_ACC_PRIVATE | JVM_ACC_PROTECTED | JVM_ACC_STATIC)

// Return number of classes in the inner classes attribute table
u2 ClassFileParser::parse_classfile_inner_classes_attribute(constantPoolHandle cp, instanceKlassHandle k, TRAPS) {
  ClassFileStream* cfs = stream();
  cfs->guarantee_more(2, CHECK_0);  // length
  u2 length = cfs->get_u2_fast();

  // 4-tuples of shorts [inner_class_info_index, outer_class_info_index, inner_name_index, inner_class_access_flags]
  typeArrayOop ic = oopFactory::new_permanent_shortArray(length*4, CHECK_0);
  typeArrayHandle inner_classes(THREAD, ic);
  int index = 0;
  int cp_size = cp->length();
  cfs->guarantee_more(8 * length, CHECK_0);  // 4-tuples of u2
  for (int n = 0; n < length; n++) {
    // Inner class index
    u2 inner_class_info_index = cfs->get_u2_fast();
    check_property(
      inner_class_info_index == 0 ||
        (valid_cp_range(inner_class_info_index, cp_size) &&
        is_klass_reference(cp, inner_class_info_index)),
      "inner_class_info_index %u has bad constant type in class file %s",
      inner_class_info_index, CHECK_0);
    // Outer class index
    u2 outer_class_info_index = cfs->get_u2_fast();
    check_property(
      outer_class_info_index == 0 ||
        (valid_cp_range(outer_class_info_index, cp_size) &&
        is_klass_reference(cp, outer_class_info_index)),
      "outer_class_info_index %u has bad constant type in class file %s",
      outer_class_info_index, CHECK_0);
    // Inner class name
    u2 inner_name_index = cfs->get_u2_fast();
    check_property(
      inner_name_index == 0 || (valid_cp_range(inner_name_index, cp_size) &&
        cp->tag_at(inner_name_index).is_utf8()),
      "inner_name_index %u has bad constant type in class file %s",
      inner_name_index, CHECK_0);
    if (_need_verify) {
      guarantee_property(inner_class_info_index != outer_class_info_index,
                         "Class is both outer and inner class in class file %s", CHECK_0);
    }
    // Access flags
    AccessFlags inner_access_flags;
    jint flags = cfs->get_u2_fast() & RECOGNIZED_INNER_CLASS_MODIFIERS;
    if ((flags & JVM_ACC_INTERFACE) && _major_version < JAVA_6_VERSION) {
      // Set abstract bit for old class files for backward compatibility
      flags |= JVM_ACC_ABSTRACT;
    }
    verify_legal_class_modifiers(flags, CHECK_0);
    inner_access_flags.set_flags(flags);

    inner_classes->short_at_put(index++, inner_class_info_index);
    inner_classes->short_at_put(index++, outer_class_info_index);
    inner_classes->short_at_put(index++, inner_name_index);
    inner_classes->short_at_put(index++, inner_access_flags.as_short());
  }

  // 4347400: make sure there's no duplicate entry in the classes array
  if (_need_verify && _major_version >= JAVA_1_5_VERSION) {
    for(int i = 0; i < inner_classes->length(); i += 4) {
      for(int j = i + 4; j < inner_classes->length(); j += 4) {
        guarantee_property((inner_classes->ushort_at(i)   != inner_classes->ushort_at(j) ||
                            inner_classes->ushort_at(i+1) != inner_classes->ushort_at(j+1) ||
                            inner_classes->ushort_at(i+2) != inner_classes->ushort_at(j+2) ||
                            inner_classes->ushort_at(i+3) != inner_classes->ushort_at(j+3)),
                            "Duplicate entry in InnerClasses in class file %s",
                            CHECK_0);
      }
    }
  }

  // Update instanceKlass with inner class info.
  k->set_inner_classes(inner_classes());
  return length;
}

void ClassFileParser::parse_classfile_synthetic_attribute(constantPoolHandle cp, instanceKlassHandle k, TRAPS) {
  k->set_is_synthetic();
}

void ClassFileParser::parse_classfile_signature_attribute(constantPoolHandle cp, instanceKlassHandle k, TRAPS) {
  ClassFileStream* cfs = stream();
  u2 signature_index = cfs->get_u2(CHECK);
  check_property(
    valid_cp_range(signature_index, cp->length()) &&
      cp->tag_at(signature_index).is_utf8(),
    "Invalid constant pool index %u in Signature attribute in class file %s",
    signature_index, CHECK);
  k->set_generic_signature(cp->symbol_at(signature_index));
}

void ClassFileParser::parse_classfile_attributes(constantPoolHandle cp, instanceKlassHandle k, TRAPS) {
  ClassFileStream* cfs = stream();
  // Set inner classes attribute to default sentinel
  k->set_inner_classes(Universe::the_empty_short_array());
  cfs->guarantee_more(2, CHECK);  // attributes_count
  u2 attributes_count = cfs->get_u2_fast();
  bool parsed_sourcefile_attribute = false;
  bool parsed_innerclasses_attribute = false;
  bool parsed_enclosingmethod_attribute = false;
  u1* runtime_visible_annotations = NULL;
  int runtime_visible_annotations_length = 0;
  u1* runtime_invisible_annotations = NULL;
  int runtime_invisible_annotations_length = 0;
  // Iterate over attributes
  while (attributes_count--) {
    cfs->guarantee_more(6, CHECK);  // attribute_name_index, attribute_length
    u2 attribute_name_index = cfs->get_u2_fast();
    u4 attribute_length = cfs->get_u4_fast();
    check_property(
      valid_cp_range(attribute_name_index, cp->length()) &&
        cp->tag_at(attribute_name_index).is_utf8(),
      "Attribute name has bad constant pool index %u in class file %s",
      attribute_name_index, CHECK);
    symbolOop tag = cp->symbol_at(attribute_name_index);
    if (tag == vmSymbols::tag_source_file()) {
      // Check for SourceFile tag
      if (_need_verify) {
        guarantee_property(attribute_length == 2, "Wrong SourceFile attribute length in class file %s", CHECK);
      }
      if (parsed_sourcefile_attribute) {
        classfile_parse_error("Multiple SourceFile attributes in class file %s", CHECK);
      } else {
        parsed_sourcefile_attribute = true;
      }
      parse_classfile_sourcefile_attribute(cp, k, CHECK);
    } else if (tag == vmSymbols::tag_source_debug_extension()) {
      // Check for SourceDebugExtension tag
      parse_classfile_source_debug_extension_attribute(cp, k, (int)attribute_length, CHECK);
    } else if (tag == vmSymbols::tag_inner_classes()) {
      // Check for InnerClasses tag
      if (parsed_innerclasses_attribute) {
        classfile_parse_error("Multiple InnerClasses attributes in class file %s", CHECK);
      } else {
        parsed_innerclasses_attribute = true;
      }
      u2 num_of_classes = parse_classfile_inner_classes_attribute(cp, k, CHECK);
      if (_need_verify && _major_version >= JAVA_1_5_VERSION) {
        guarantee_property(attribute_length == sizeof(num_of_classes) + 4 * sizeof(u2) * num_of_classes,
                          "Wrong InnerClasses attribute length in class file %s", CHECK);
      }
    } else if (tag == vmSymbols::tag_synthetic()) {
      // Check for Synthetic tag
      // Shouldn't we check that the synthetic flags wasn't already set? - not required in spec
      if (attribute_length != 0) {
        classfile_parse_error(
          "Invalid Synthetic classfile attribute length %u in class file %s",
          attribute_length, CHECK);
      }
      parse_classfile_synthetic_attribute(cp, k, CHECK);
    } else if (tag == vmSymbols::tag_deprecated()) {
      // Check for Deprecatd tag - 4276120
      if (attribute_length != 0) {
        classfile_parse_error(
          "Invalid Deprecated classfile attribute length %u in class file %s",
          attribute_length, CHECK);
      }
    } else if (_major_version >= JAVA_1_5_VERSION) {
      if (tag == vmSymbols::tag_signature()) {
        if (attribute_length != 2) {
          classfile_parse_error(
            "Wrong Signature attribute length %u in class file %s",
            attribute_length, CHECK);
        }
        parse_classfile_signature_attribute(cp, k, CHECK);
      } else if (tag == vmSymbols::tag_runtime_visible_annotations()) {
        runtime_visible_annotations_length = attribute_length;
        runtime_visible_annotations = cfs->get_u1_buffer();
        assert(runtime_visible_annotations != NULL, "null visible annotations");
        cfs->skip_u1(runtime_visible_annotations_length, CHECK);
      } else if (PreserveAllAnnotations && tag == vmSymbols::tag_runtime_invisible_annotations()) {
        runtime_invisible_annotations_length = attribute_length;
        runtime_invisible_annotations = cfs->get_u1_buffer();
        assert(runtime_invisible_annotations != NULL, "null invisible annotations");
        cfs->skip_u1(runtime_invisible_annotations_length, CHECK);
      } else if (tag == vmSymbols::tag_enclosing_method()) {
        if (parsed_enclosingmethod_attribute) {
          classfile_parse_error("Multiple EnclosingMethod attributes in class file %s", CHECK);
        }   else {
          parsed_enclosingmethod_attribute = true;
        }
        cfs->guarantee_more(4, CHECK);  // class_index, method_index
        u2 class_index  = cfs->get_u2_fast();
        u2 method_index = cfs->get_u2_fast();
        if (class_index == 0) {
          classfile_parse_error("Invalid class index in EnclosingMethod attribute in class file %s", CHECK);
        }
        // Validate the constant pool indices and types
        if (!cp->is_within_bounds(class_index) ||
            !is_klass_reference(cp, class_index)) {
          classfile_parse_error("Invalid or out-of-bounds class index in EnclosingMethod attribute in class file %s", CHECK);
        }
        if (method_index != 0 &&
            (!cp->is_within_bounds(method_index) ||
             !cp->tag_at(method_index).is_name_and_type())) {
          classfile_parse_error("Invalid or out-of-bounds method index in EnclosingMethod attribute in class file %s", CHECK);
        }
        k->set_enclosing_method_indices(class_index, method_index);
      } else {
        // Unknown attribute
        cfs->skip_u1(attribute_length, CHECK);
      }
    } else {
      // Unknown attribute
      cfs->skip_u1(attribute_length, CHECK);
    }
  }
  typeArrayHandle annotations = assemble_annotations(runtime_visible_annotations,
                                                     runtime_visible_annotations_length,
                                                     runtime_invisible_annotations,
                                                     runtime_invisible_annotations_length,
                                                     CHECK);
  k->set_class_annotations(annotations());
}


typeArrayHandle ClassFileParser::assemble_annotations(u1* runtime_visible_annotations,
                                                      int runtime_visible_annotations_length,
                                                      u1* runtime_invisible_annotations,
                                                      int runtime_invisible_annotations_length, TRAPS) {
  typeArrayHandle annotations;
  if (runtime_visible_annotations != NULL ||
      runtime_invisible_annotations != NULL) {
    typeArrayOop anno = oopFactory::new_permanent_byteArray(runtime_visible_annotations_length +
                                                            runtime_invisible_annotations_length, CHECK_(annotations));
    annotations = typeArrayHandle(THREAD, anno);
    if (runtime_visible_annotations != NULL) {
      memcpy(annotations->byte_at_addr(0), runtime_visible_annotations, runtime_visible_annotations_length);
    }
    if (runtime_invisible_annotations != NULL) {
      memcpy(annotations->byte_at_addr(runtime_visible_annotations_length), runtime_invisible_annotations, runtime_invisible_annotations_length);
    }
  }
  return annotations;
}


static void initialize_static_field(fieldDescriptor* fd, TRAPS) {
  KlassHandle h_k (THREAD, fd->field_holder());
  assert(h_k.not_null() && fd->is_static(), "just checking");
  if (fd->has_initial_value()) {
    BasicType t = fd->field_type();
    switch (t) {
      case T_BYTE:
        h_k()->byte_field_put(fd->offset(), fd->int_initial_value());
              break;
      case T_BOOLEAN:
        h_k()->bool_field_put(fd->offset(), fd->int_initial_value());
              break;
      case T_CHAR:
        h_k()->char_field_put(fd->offset(), fd->int_initial_value());
              break;
      case T_SHORT:
        h_k()->short_field_put(fd->offset(), fd->int_initial_value());
              break;
      case T_INT:
        h_k()->int_field_put(fd->offset(), fd->int_initial_value());
        break;
      case T_FLOAT:
        h_k()->float_field_put(fd->offset(), fd->float_initial_value());
        break;
      case T_DOUBLE:
        h_k()->double_field_put(fd->offset(), fd->double_initial_value());
        break;
      case T_LONG:
        h_k()->long_field_put(fd->offset(), fd->long_initial_value());
        break;
      case T_OBJECT:
        {
          #ifdef ASSERT
          symbolOop sym = oopFactory::new_symbol("Ljava/lang/String;", CHECK);
          assert(fd->signature() == sym, "just checking");
          #endif
          oop string = fd->string_initial_value(CHECK);
          h_k()->obj_field_put(fd->offset(), string);
        }
        break;
      default:
        THROW_MSG(vmSymbols::java_lang_ClassFormatError(),
                  "Illegal ConstantValue attribute in class file");
    }
  }
}


void ClassFileParser::java_lang_ref_Reference_fix_pre(typeArrayHandle* fields_ptr,
  constantPoolHandle cp, FieldAllocationCount *fac_ptr, TRAPS) {
  // This code is for compatibility with earlier jdk's that do not
  // have the "discovered" field in java.lang.ref.Reference.  For 1.5
  // the check for the "discovered" field should issue a warning if
  // the field is not found.  For 1.6 this code should be issue a
  // fatal error if the "discovered" field is not found.
  //
  // Increment fac.nonstatic_oop_count so that the start of the
  // next type of non-static oops leaves room for the fake oop.
  // Do not increment next_nonstatic_oop_offset so that the
  // fake oop is place after the java.lang.ref.Reference oop
  // fields.
  //
  // Check the fields in java.lang.ref.Reference for the "discovered"
  // field.  If it is not present, artifically create a field for it.
  // This allows this VM to run on early JDK where the field is not
  // present.

  //
  // Increment fac.nonstatic_oop_count so that the start of the
  // next type of non-static oops leaves room for the fake oop.
  // Do not increment next_nonstatic_oop_offset so that the
  // fake oop is place after the java.lang.ref.Reference oop
  // fields.
  //
  // Check the fields in java.lang.ref.Reference for the "discovered"
  // field.  If it is not present, artifically create a field for it.
  // This allows this VM to run on early JDK where the field is not
  // present.
  int reference_sig_index = 0;
  int reference_name_index = 0;
  int reference_index = 0;
  int extra = java_lang_ref_Reference::number_of_fake_oop_fields;
  const int n = (*fields_ptr)()->length();
  for (int i = 0; i < n; i += instanceKlass::next_offset ) {
    int name_index =
    (*fields_ptr)()->ushort_at(i + instanceKlass::name_index_offset);
    int sig_index  =
      (*fields_ptr)()->ushort_at(i + instanceKlass::signature_index_offset);
    symbolOop f_name = cp->symbol_at(name_index);
    symbolOop f_sig  = cp->symbol_at(sig_index);
    if (f_sig == vmSymbols::reference_signature() && reference_index == 0) {
      // Save the index for reference signature for later use.
      // The fake discovered field does not entries in the
      // constant pool so the index for its signature cannot
      // be extracted from the constant pool.  It will need
      // later, however.  It's signature is vmSymbols::reference_signature()
      // so same an index for that signature.
      reference_sig_index = sig_index;
      reference_name_index = name_index;
      reference_index = i;
    }
    if (f_name == vmSymbols::reference_discovered_name() &&
      f_sig == vmSymbols::reference_signature()) {
      // The values below are fake but will force extra
      // non-static oop fields and a corresponding non-static
      // oop map block to be allocated.
      extra = 0;
      break;
    }
  }
  if (extra != 0) {
    fac_ptr->nonstatic_oop_count += extra;
    // Add the additional entry to "fields" so that the klass
    // contains the "discoverd" field and the field will be initialized
    // in instances of the object.
    int fields_with_fix_length = (*fields_ptr)()->length() +
      instanceKlass::next_offset;
    typeArrayOop ff = oopFactory::new_permanent_shortArray(
                                                fields_with_fix_length, CHECK);
    typeArrayHandle fields_with_fix(THREAD, ff);

    // Take everything from the original but the length.
    for (int idx = 0; idx < (*fields_ptr)->length(); idx++) {
      fields_with_fix->ushort_at_put(idx, (*fields_ptr)->ushort_at(idx));
    }

    // Add the fake field at the end.
    int i = (*fields_ptr)->length();
    // There is no name index for the fake "discovered" field nor
    // signature but a signature is needed so that the field will
    // be properly initialized.  Use one found for
    // one of the other reference fields. Be sure the index for the
    // name is 0.  In fieldDescriptor::initialize() the index of the
    // name is checked.  That check is by passed for the last nonstatic
    // oop field in a java.lang.ref.Reference which is assumed to be
    // this artificial "discovered" field.  An assertion checks that
    // the name index is 0.
    assert(reference_index != 0, "Missing signature for reference");

    int j;
    for (j = 0; j < instanceKlass::next_offset; j++) {
      fields_with_fix->ushort_at_put(i + j,
        (*fields_ptr)->ushort_at(reference_index +j));
    }
    // Clear the public access flag and set the private access flag.
    short flags;
    flags =
      fields_with_fix->ushort_at(i + instanceKlass::access_flags_offset);
    assert(!(flags & JVM_RECOGNIZED_FIELD_MODIFIERS), "Unexpected access flags set");
    flags = flags & (~JVM_ACC_PUBLIC);
    flags = flags | JVM_ACC_PRIVATE;
    AccessFlags access_flags;
    access_flags.set_flags(flags);
    assert(!access_flags.is_public(), "Failed to clear public flag");
    assert(access_flags.is_private(), "Failed to set private flag");
    fields_with_fix->ushort_at_put(i + instanceKlass::access_flags_offset,
      flags);

    assert(fields_with_fix->ushort_at(i + instanceKlass::name_index_offset)
      == reference_name_index, "The fake reference name is incorrect");
    assert(fields_with_fix->ushort_at(i + instanceKlass::signature_index_offset)
      == reference_sig_index, "The fake reference signature is incorrect");
    // The type of the field is stored in the low_offset entry during
    // parsing.
    assert(fields_with_fix->ushort_at(i + instanceKlass::low_offset) ==
      NONSTATIC_OOP, "The fake reference type is incorrect");

    // "fields" is allocated in the permanent generation.  Disgard
    // it and let it be collected.
    (*fields_ptr) = fields_with_fix;
  }
  return;
}


void ClassFileParser::java_lang_Class_fix_pre(objArrayHandle* methods_ptr,
  FieldAllocationCount *fac_ptr, TRAPS) {
  // Add fake fields for java.lang.Class instances
  //
  // This is not particularly nice. We should consider adding a
  // private transient object field at the Java level to
  // java.lang.Class. Alternatively we could add a subclass of
  // instanceKlass which provides an accessor and size computer for
  // this field, but that appears to be more code than this hack.
  //
  // NOTE that we wedge these in at the beginning rather than the
  // end of the object because the Class layout changed between JDK
  // 1.3 and JDK 1.4 with the new reflection implementation; some
  // nonstatic oop fields were added at the Java level. The offsets
  // of these fake fields can't change between these two JDK
  // versions because when the offsets are computed at bootstrap
  // time we don't know yet which version of the JDK we're running in.

  // The values below are fake but will force two non-static oop fields and
  // a corresponding non-static oop map block to be allocated.
  const int extra = java_lang_Class::number_of_fake_oop_fields;
  fac_ptr->nonstatic_oop_count += extra;
}


void ClassFileParser::java_lang_Class_fix_post(int* next_nonstatic_oop_offset_ptr) {
  // Cause the extra fake fields in java.lang.Class to show up before
  // the Java fields for layout compatibility between 1.3 and 1.4
  // Incrementing next_nonstatic_oop_offset here advances the
  // location where the real java fields are placed.
  const int extra = java_lang_Class::number_of_fake_oop_fields;
  (*next_nonstatic_oop_offset_ptr) += (extra * heapOopSize);
}


// Force MethodHandle.vmentry to be an unmanaged pointer.
// There is no way for a classfile to express this, so we must help it.
void ClassFileParser::java_dyn_MethodHandle_fix_pre(constantPoolHandle cp,
                                                    typeArrayHandle* fields_ptr,
                                                    FieldAllocationCount *fac_ptr,
                                                    TRAPS) {
  // Add fake fields for java.dyn.MethodHandle instances
  //
  // This is not particularly nice, but since there is no way to express
  // a native wordSize field in Java, we must do it at this level.

  if (!EnableMethodHandles)  return;

  int word_sig_index = 0;
  const int cp_size = cp->length();
  for (int index = 1; index < cp_size; index++) {
    if (cp->tag_at(index).is_utf8() &&
        cp->symbol_at(index) == vmSymbols::machine_word_signature()) {
      word_sig_index = index;
      break;
    }
  }

  if (word_sig_index == 0)
    THROW_MSG(vmSymbols::java_lang_VirtualMachineError(),
              "missing I or J signature (for vmentry) in java.dyn.MethodHandle");

  bool found_vmentry = false;

  const int n = (*fields_ptr)()->length();
  for (int i = 0; i < n; i += instanceKlass::next_offset) {
    int name_index = (*fields_ptr)->ushort_at(i + instanceKlass::name_index_offset);
    int sig_index  = (*fields_ptr)->ushort_at(i + instanceKlass::signature_index_offset);
    int acc_flags  = (*fields_ptr)->ushort_at(i + instanceKlass::access_flags_offset);
    symbolOop f_name = cp->symbol_at(name_index);
    symbolOop f_sig  = cp->symbol_at(sig_index);
    if (f_sig == vmSymbols::byte_signature() &&
        f_name == vmSymbols::vmentry_name() &&
        (acc_flags & JVM_ACC_STATIC) == 0) {
      // Adjust the field type from byte to an unmanaged pointer.
      assert(fac_ptr->nonstatic_byte_count > 0, "");
      fac_ptr->nonstatic_byte_count -= 1;
      (*fields_ptr)->ushort_at_put(i + instanceKlass::signature_index_offset,
                                   word_sig_index);
      if (wordSize == jintSize) {
        fac_ptr->nonstatic_word_count += 1;
      } else {
        fac_ptr->nonstatic_double_count += 1;
      }

      FieldAllocationType atype = (FieldAllocationType) (*fields_ptr)->ushort_at(i+4);
      assert(atype == NONSTATIC_BYTE, "");
      FieldAllocationType new_atype = NONSTATIC_WORD;
      if (wordSize > jintSize) {
        if (Universe::field_type_should_be_aligned(T_LONG)) {
          atype = NONSTATIC_ALIGNED_DOUBLE;
        } else {
          atype = NONSTATIC_DOUBLE;
        }
      }
      (*fields_ptr)->ushort_at_put(i+4, new_atype);

      found_vmentry = true;
      break;
    }
  }

  if (!found_vmentry)
    THROW_MSG(vmSymbols::java_lang_VirtualMachineError(),
              "missing vmentry byte field in java.dyn.MethodHandle");

}


instanceKlassHandle ClassFileParser::parseClassFile(symbolHandle name,
                                                    Handle class_loader,
                                                    Handle protection_domain,
                                                    KlassHandle host_klass,
                                                    GrowableArray<Handle>* cp_patches,
                                                    symbolHandle& parsed_name,
                                                    TRAPS) {
  // So that JVMTI can cache class file in the state before retransformable agents
  // have modified it
  unsigned char *cached_class_file_bytes = NULL;
  jint cached_class_file_length;

  ClassFileStream* cfs = stream();
  // Timing
  assert(THREAD->is_Java_thread(), "must be a JavaThread");
  JavaThread* jt = (JavaThread*) THREAD;

  PerfClassTraceTime ctimer(ClassLoader::perf_class_parse_time(),
                            ClassLoader::perf_class_parse_selftime(),
                            NULL,
                            jt->get_thread_stat()->perf_recursion_counts_addr(),
                            jt->get_thread_stat()->perf_timers_addr(),
                            PerfClassTraceTime::PARSE_CLASS);

  _has_finalizer = _has_empty_finalizer = _has_vanilla_constructor = false;

  if (JvmtiExport::should_post_class_file_load_hook()) {
    unsigned char* ptr = cfs->buffer();
    unsigned char* end_ptr = cfs->buffer() + cfs->length();

    JvmtiExport::post_class_file_load_hook(name, class_loader, protection_domain,
                                           &ptr, &end_ptr,
                                           &cached_class_file_bytes,
                                           &cached_class_file_length);

    if (ptr != cfs->buffer()) {
      // JVMTI agent has modified class file data.
      // Set new class file stream using JVMTI agent modified
      // class file data.
      cfs = new ClassFileStream(ptr, end_ptr - ptr, cfs->source());
      set_stream(cfs);
    }
  }

  _host_klass = host_klass;
  _cp_patches = cp_patches;

  instanceKlassHandle nullHandle;

  // Figure out whether we can skip format checking (matching classic VM behavior)
  _need_verify = Verifier::should_verify_for(class_loader());

  // Set the verify flag in stream
  cfs->set_verify(_need_verify);

  // Save the class file name for easier error message printing.
  _class_name = name.not_null()? name : vmSymbolHandles::unknown_class_name();

  cfs->guarantee_more(8, CHECK_(nullHandle));  // magic, major, minor
  // Magic value
  u4 magic = cfs->get_u4_fast();
  guarantee_property(magic == JAVA_CLASSFILE_MAGIC,
                     "Incompatible magic value %u in class file %s",
                     magic, CHECK_(nullHandle));

  // Version numbers
  u2 minor_version = cfs->get_u2_fast();
  u2 major_version = cfs->get_u2_fast();

  // Check version numbers - we check this even with verifier off
  if (!is_supported_version(major_version, minor_version)) {
    if (name.is_null()) {
      Exceptions::fthrow(
        THREAD_AND_LOCATION,
        vmSymbolHandles::java_lang_UnsupportedClassVersionError(),
        "Unsupported major.minor version %u.%u",
        major_version,
        minor_version);
    } else {
      ResourceMark rm(THREAD);
      Exceptions::fthrow(
        THREAD_AND_LOCATION,
        vmSymbolHandles::java_lang_UnsupportedClassVersionError(),
        "%s : Unsupported major.minor version %u.%u",
        name->as_C_string(),
        major_version,
        minor_version);
    }
    return nullHandle;
  }

  _major_version = major_version;
  _minor_version = minor_version;


  // Check if verification needs to be relaxed for this class file
  // Do not restrict it to jdk1.0 or jdk1.1 to maintain backward compatibility (4982376)
  _relax_verify = Verifier::relax_verify_for(class_loader());

  // Constant pool
  constantPoolHandle cp = parse_constant_pool(CHECK_(nullHandle));
  int cp_size = cp->length();

  cfs->guarantee_more(8, CHECK_(nullHandle));  // flags, this_class, super_class, infs_len

  // Access flags
  AccessFlags access_flags;
  jint flags = cfs->get_u2_fast() & JVM_RECOGNIZED_CLASS_MODIFIERS;

  if ((flags & JVM_ACC_INTERFACE) && _major_version < JAVA_6_VERSION) {
    // Set abstract bit for old class files for backward compatibility
    flags |= JVM_ACC_ABSTRACT;
  }
  verify_legal_class_modifiers(flags, CHECK_(nullHandle));
  access_flags.set_flags(flags);

  // This class and superclass
  instanceKlassHandle super_klass;
  u2 this_class_index = cfs->get_u2_fast();
  check_property(
    valid_cp_range(this_class_index, cp_size) &&
      cp->tag_at(this_class_index).is_unresolved_klass(),
    "Invalid this class index %u in constant pool in class file %s",
    this_class_index, CHECK_(nullHandle));

  symbolHandle class_name (THREAD, cp->unresolved_klass_at(this_class_index));
  assert(class_name.not_null(), "class_name can't be null");

  // It's important to set parsed_name *before* resolving the super class.
  // (it's used for cleanup by the caller if parsing fails)
  parsed_name = class_name;

  // Update _class_name which could be null previously to be class_name
  _class_name = class_name;

  // Don't need to check whether this class name is legal or not.
  // It has been checked when constant pool is parsed.
  // However, make sure it is not an array type.
  if (_need_verify) {
    guarantee_property(class_name->byte_at(0) != JVM_SIGNATURE_ARRAY,
                       "Bad class name in class file %s",
                       CHECK_(nullHandle));
  }

  klassOop preserve_this_klass;   // for storing result across HandleMark

  // release all handles when parsing is done
  { HandleMark hm(THREAD);

    // Checks if name in class file matches requested name
    if (name.not_null() && class_name() != name()) {
      ResourceMark rm(THREAD);
      Exceptions::fthrow(
        THREAD_AND_LOCATION,
        vmSymbolHandles::java_lang_NoClassDefFoundError(),
        "%s (wrong name: %s)",
        name->as_C_string(),
        class_name->as_C_string()
      );
      return nullHandle;
    }

    if (TraceClassLoadingPreorder) {
      tty->print("[Loading %s", name()->as_klass_external_name());
      if (cfs->source() != NULL) tty->print(" from %s", cfs->source());
      tty->print_cr("]");
    }

    u2 super_class_index = cfs->get_u2_fast();
    if (super_class_index == 0) {
      check_property(class_name() == vmSymbols::java_lang_Object(),
                     "Invalid superclass index %u in class file %s",
                     super_class_index,
                     CHECK_(nullHandle));
    } else {
      check_property(valid_cp_range(super_class_index, cp_size) &&
                     is_klass_reference(cp, super_class_index),
                     "Invalid superclass index %u in class file %s",
                     super_class_index,
                     CHECK_(nullHandle));
      // The class name should be legal because it is checked when parsing constant pool.
      // However, make sure it is not an array type.
      bool is_array = false;
      if (cp->tag_at(super_class_index).is_klass()) {
        super_klass = instanceKlassHandle(THREAD, cp->resolved_klass_at(super_class_index));
        if (_need_verify)
          is_array = super_klass->oop_is_array();
      } else if (_need_verify) {
        is_array = (cp->unresolved_klass_at(super_class_index)->byte_at(0) == JVM_SIGNATURE_ARRAY);
      }
      if (_need_verify) {
        guarantee_property(!is_array,
                          "Bad superclass name in class file %s", CHECK_(nullHandle));
      }
    }

    // Interfaces
    u2 itfs_len = cfs->get_u2_fast();
    objArrayHandle local_interfaces;
    if (itfs_len == 0) {
      local_interfaces = objArrayHandle(THREAD, Universe::the_empty_system_obj_array());
    } else {
      local_interfaces = parse_interfaces(cp, itfs_len, class_loader, protection_domain, _class_name, CHECK_(nullHandle));
    }

    // Fields (offsets are filled in later)
    struct FieldAllocationCount fac = {0,0,0,0,0,0,0,0,0,0};
    objArrayHandle fields_annotations;
    typeArrayHandle fields = parse_fields(cp, access_flags.is_interface(), &fac, &fields_annotations, CHECK_(nullHandle));
    // Methods
    bool has_final_method = false;
    AccessFlags promoted_flags;
    promoted_flags.set_flags(0);
    // These need to be oop pointers because they are allocated lazily
    // inside parse_methods inside a nested HandleMark
    objArrayOop methods_annotations_oop = NULL;
    objArrayOop methods_parameter_annotations_oop = NULL;
    objArrayOop methods_default_annotations_oop = NULL;
    objArrayHandle methods = parse_methods(cp, access_flags.is_interface(),
                                           &promoted_flags,
                                           &has_final_method,
                                           &methods_annotations_oop,
                                           &methods_parameter_annotations_oop,
                                           &methods_default_annotations_oop,
                                           CHECK_(nullHandle));

    objArrayHandle methods_annotations(THREAD, methods_annotations_oop);
    objArrayHandle methods_parameter_annotations(THREAD, methods_parameter_annotations_oop);
    objArrayHandle methods_default_annotations(THREAD, methods_default_annotations_oop);

    // We check super class after class file is parsed and format is checked
    if (super_class_index > 0 && super_klass.is_null()) {
      symbolHandle sk (THREAD, cp->klass_name_at(super_class_index));
      if (access_flags.is_interface()) {
        // Before attempting to resolve the superclass, check for class format
        // errors not checked yet.
        guarantee_property(sk() == vmSymbols::java_lang_Object(),
                           "Interfaces must have java.lang.Object as superclass in class file %s",
                           CHECK_(nullHandle));
      }
      klassOop k = SystemDictionary::resolve_super_or_fail(class_name,
                                                           sk,
                                                           class_loader,
                                                           protection_domain,
                                                           true,
                                                           CHECK_(nullHandle));

      KlassHandle kh (THREAD, k);
      super_klass = instanceKlassHandle(THREAD, kh());
      if (LinkWellKnownClasses)  // my super class is well known to me
        cp->klass_at_put(super_class_index, super_klass()); // eagerly resolve
    }
    if (super_klass.not_null()) {
      if (super_klass->is_interface()) {
        ResourceMark rm(THREAD);
        Exceptions::fthrow(
          THREAD_AND_LOCATION,
          vmSymbolHandles::java_lang_IncompatibleClassChangeError(),
          "class %s has interface %s as super class",
          class_name->as_klass_external_name(),
          super_klass->external_name()
        );
        return nullHandle;
      }
      // Make sure super class is not final
      if (super_klass->is_final()) {
        THROW_MSG_(vmSymbols::java_lang_VerifyError(), "Cannot inherit from final class", nullHandle);
      }
    }

    // Compute the transitive list of all unique interfaces implemented by this class
    objArrayHandle transitive_interfaces = compute_transitive_interfaces(super_klass, local_interfaces, CHECK_(nullHandle));

    // sort methods
    typeArrayHandle method_ordering = sort_methods(methods,
                                                   methods_annotations,
                                                   methods_parameter_annotations,
                                                   methods_default_annotations,
                                                   CHECK_(nullHandle));

    // promote flags from parse_methods() to the klass' flags
    access_flags.add_promoted_flags(promoted_flags.as_int());

    // Size of Java vtable (in words)
    int vtable_size = 0;
    int itable_size = 0;
    int num_miranda_methods = 0;

    klassVtable::compute_vtable_size_and_num_mirandas(vtable_size,
                                                      num_miranda_methods,
                                                      super_klass(),
                                                      methods(),
                                                      access_flags,
                                                      class_loader,
                                                      class_name,
                                                      local_interfaces(),
                                                      CHECK_(nullHandle));

    // Size of Java itable (in words)
    itable_size = access_flags.is_interface() ? 0 : klassItable::compute_itable_size(transitive_interfaces);

    // Field size and offset computation
    int nonstatic_field_size = super_klass() == NULL ? 0 : super_klass->nonstatic_field_size();
#ifndef PRODUCT
    int orig_nonstatic_field_size = 0;
#endif
    int static_field_size = 0;
    int next_static_oop_offset;
    int next_static_double_offset;
    int next_static_word_offset;
    int next_static_short_offset;
    int next_static_byte_offset;
    int next_static_type_offset;
    int next_nonstatic_oop_offset;
    int next_nonstatic_double_offset;
    int next_nonstatic_word_offset;
    int next_nonstatic_short_offset;
    int next_nonstatic_byte_offset;
    int next_nonstatic_type_offset;
    int first_nonstatic_oop_offset;
    int first_nonstatic_field_offset;
    int next_nonstatic_field_offset;

    // Calculate the starting byte offsets
    next_static_oop_offset      = (instanceKlass::header_size() +
                                  align_object_offset(vtable_size) +
                                  align_object_offset(itable_size)) * wordSize;
    next_static_double_offset   = next_static_oop_offset +
                                  (fac.static_oop_count * heapOopSize);
    if ( fac.static_double_count &&
         (Universe::field_type_should_be_aligned(T_DOUBLE) ||
          Universe::field_type_should_be_aligned(T_LONG)) ) {
      next_static_double_offset = align_size_up(next_static_double_offset, BytesPerLong);
    }

    next_static_word_offset     = next_static_double_offset +
                                  (fac.static_double_count * BytesPerLong);
    next_static_short_offset    = next_static_word_offset +
                                  (fac.static_word_count * BytesPerInt);
    next_static_byte_offset     = next_static_short_offset +
                                  (fac.static_short_count * BytesPerShort);
    next_static_type_offset     = align_size_up((next_static_byte_offset +
                                  fac.static_byte_count ), wordSize );
    static_field_size           = (next_static_type_offset -
                                  next_static_oop_offset) / wordSize;
    first_nonstatic_field_offset = instanceOopDesc::base_offset_in_bytes() +
                                   nonstatic_field_size * heapOopSize;
    next_nonstatic_field_offset = first_nonstatic_field_offset;

    // Add fake fields for java.lang.Class instances (also see below)
    if (class_name() == vmSymbols::java_lang_Class() && class_loader.is_null()) {
      java_lang_Class_fix_pre(&methods, &fac, CHECK_(nullHandle));
    }

    // adjust the vmentry field declaration in java.dyn.MethodHandle
    if (EnableMethodHandles && class_name() == vmSymbols::sun_dyn_MethodHandleImpl() && class_loader.is_null()) {
      java_dyn_MethodHandle_fix_pre(cp, &fields, &fac, CHECK_(nullHandle));
    }

    // Add a fake "discovered" field if it is not present
    // for compatibility with earlier jdk's.
    if (class_name() == vmSymbols::java_lang_ref_Reference()
      && class_loader.is_null()) {
      java_lang_ref_Reference_fix_pre(&fields, cp, &fac, CHECK_(nullHandle));
    }
    // end of "discovered" field compactibility fix

    unsigned int nonstatic_double_count = fac.nonstatic_double_count;
    unsigned int nonstatic_word_count   = fac.nonstatic_word_count;
    unsigned int nonstatic_short_count  = fac.nonstatic_short_count;
    unsigned int nonstatic_byte_count   = fac.nonstatic_byte_count;
    unsigned int nonstatic_oop_count    = fac.nonstatic_oop_count;

    bool super_has_nonstatic_fields =
            (super_klass() != NULL && super_klass->has_nonstatic_fields());
    bool has_nonstatic_fields  =  super_has_nonstatic_fields ||
            ((nonstatic_double_count + nonstatic_word_count +
              nonstatic_short_count + nonstatic_byte_count +
              nonstatic_oop_count) != 0);


    // Prepare list of oops for oop map generation.
    int* nonstatic_oop_offsets;
    unsigned int* nonstatic_oop_counts;
    unsigned int nonstatic_oop_map_count = 0;

    nonstatic_oop_offsets = NEW_RESOURCE_ARRAY_IN_THREAD(
              THREAD, int, nonstatic_oop_count + 1);
    nonstatic_oop_counts  = NEW_RESOURCE_ARRAY_IN_THREAD(
              THREAD, unsigned int, nonstatic_oop_count + 1);

    // Add fake fields for java.lang.Class instances (also see above).
    // FieldsAllocationStyle and CompactFields values will be reset to default.
    if(class_name() == vmSymbols::java_lang_Class() && class_loader.is_null()) {
      java_lang_Class_fix_post(&next_nonstatic_field_offset);
      nonstatic_oop_offsets[0] = first_nonstatic_field_offset;
      const uint fake_oop_count = (next_nonstatic_field_offset -
                                   first_nonstatic_field_offset) / heapOopSize;
      nonstatic_oop_counts[0] = fake_oop_count;
      nonstatic_oop_map_count = 1;
      nonstatic_oop_count -= fake_oop_count;
      first_nonstatic_oop_offset = first_nonstatic_field_offset;
    } else {
      first_nonstatic_oop_offset = 0; // will be set for first oop field
    }

#ifndef PRODUCT
    if( PrintCompactFieldsSavings ) {
      next_nonstatic_double_offset = next_nonstatic_field_offset +
                                     (nonstatic_oop_count * heapOopSize);
      if ( nonstatic_double_count > 0 ) {
        next_nonstatic_double_offset = align_size_up(next_nonstatic_double_offset, BytesPerLong);
      }
      next_nonstatic_word_offset  = next_nonstatic_double_offset +
                                    (nonstatic_double_count * BytesPerLong);
      next_nonstatic_short_offset = next_nonstatic_word_offset +
                                    (nonstatic_word_count * BytesPerInt);
      next_nonstatic_byte_offset  = next_nonstatic_short_offset +
                                    (nonstatic_short_count * BytesPerShort);
      next_nonstatic_type_offset  = align_size_up((next_nonstatic_byte_offset +
                                    nonstatic_byte_count ), heapOopSize );
      orig_nonstatic_field_size   = nonstatic_field_size +
      ((next_nonstatic_type_offset - first_nonstatic_field_offset)/heapOopSize);
    }
#endif
    bool compact_fields   = CompactFields;
    int  allocation_style = FieldsAllocationStyle;
    if( allocation_style < 0 || allocation_style > 1 ) { // Out of range?
      assert(false, "0 <= FieldsAllocationStyle <= 1");
      allocation_style = 1; // Optimistic
    }

    // The next classes have predefined hard-coded fields offsets
    // (see in JavaClasses::compute_hard_coded_offsets()).
    // Use default fields allocation order for them.
    if( (allocation_style != 0 || compact_fields ) && class_loader.is_null() &&
        (class_name() == vmSymbols::java_lang_AssertionStatusDirectives() ||
         class_name() == vmSymbols::java_lang_Class() ||
         class_name() == vmSymbols::java_lang_ClassLoader() ||
         class_name() == vmSymbols::java_lang_ref_Reference() ||
         class_name() == vmSymbols::java_lang_ref_SoftReference() ||
         class_name() == vmSymbols::java_lang_StackTraceElement() ||
         class_name() == vmSymbols::java_lang_String() ||
         class_name() == vmSymbols::java_lang_Throwable() ||
         class_name() == vmSymbols::java_lang_Boolean() ||
         class_name() == vmSymbols::java_lang_Character() ||
         class_name() == vmSymbols::java_lang_Float() ||
         class_name() == vmSymbols::java_lang_Double() ||
         class_name() == vmSymbols::java_lang_Byte() ||
         class_name() == vmSymbols::java_lang_Short() ||
         class_name() == vmSymbols::java_lang_Integer() ||
         class_name() == vmSymbols::java_lang_Long())) {
      allocation_style = 0;     // Allocate oops first
      compact_fields   = false; // Don't compact fields
    }

    if( allocation_style == 0 ) {
      // Fields order: oops, longs/doubles, ints, shorts/chars, bytes
      next_nonstatic_oop_offset    = next_nonstatic_field_offset;
      next_nonstatic_double_offset = next_nonstatic_oop_offset +
                                      (nonstatic_oop_count * heapOopSize);
    } else if( allocation_style == 1 ) {
      // Fields order: longs/doubles, ints, shorts/chars, bytes, oops
      next_nonstatic_double_offset = next_nonstatic_field_offset;
    } else {
      ShouldNotReachHere();
    }

    int nonstatic_oop_space_count   = 0;
    int nonstatic_word_space_count  = 0;
    int nonstatic_short_space_count = 0;
    int nonstatic_byte_space_count  = 0;
    int nonstatic_oop_space_offset;
    int nonstatic_word_space_offset;
    int nonstatic_short_space_offset;
    int nonstatic_byte_space_offset;

    if( nonstatic_double_count > 0 ) {
      int offset = next_nonstatic_double_offset;
      next_nonstatic_double_offset = align_size_up(offset, BytesPerLong);
      if( compact_fields && offset != next_nonstatic_double_offset ) {
        // Allocate available fields into the gap before double field.
        int length = next_nonstatic_double_offset - offset;
        assert(length == BytesPerInt, "");
        nonstatic_word_space_offset = offset;
        if( nonstatic_word_count > 0 ) {
          nonstatic_word_count      -= 1;
          nonstatic_word_space_count = 1; // Only one will fit
          length -= BytesPerInt;
          offset += BytesPerInt;
        }
        nonstatic_short_space_offset = offset;
        while( length >= BytesPerShort && nonstatic_short_count > 0 ) {
          nonstatic_short_count       -= 1;
          nonstatic_short_space_count += 1;
          length -= BytesPerShort;
          offset += BytesPerShort;
        }
        nonstatic_byte_space_offset = offset;
        while( length > 0 && nonstatic_byte_count > 0 ) {
          nonstatic_byte_count       -= 1;
          nonstatic_byte_space_count += 1;
          length -= 1;
        }
        // Allocate oop field in the gap if there are no other fields for that.
        nonstatic_oop_space_offset = offset;
        if( length >= heapOopSize && nonstatic_oop_count > 0 &&
            allocation_style != 0 ) { // when oop fields not first
          nonstatic_oop_count      -= 1;
          nonstatic_oop_space_count = 1; // Only one will fit
          length -= heapOopSize;
          offset += heapOopSize;
        }
      }
    }

    next_nonstatic_word_offset  = next_nonstatic_double_offset +
                                  (nonstatic_double_count * BytesPerLong);
    next_nonstatic_short_offset = next_nonstatic_word_offset +
                                  (nonstatic_word_count * BytesPerInt);
    next_nonstatic_byte_offset  = next_nonstatic_short_offset +
                                  (nonstatic_short_count * BytesPerShort);

    int notaligned_offset;
    if( allocation_style == 0 ) {
      notaligned_offset = next_nonstatic_byte_offset + nonstatic_byte_count;
    } else { // allocation_style == 1
      next_nonstatic_oop_offset = next_nonstatic_byte_offset + nonstatic_byte_count;
      if( nonstatic_oop_count > 0 ) {
        next_nonstatic_oop_offset = align_size_up(next_nonstatic_oop_offset, heapOopSize);
      }
      notaligned_offset = next_nonstatic_oop_offset + (nonstatic_oop_count * heapOopSize);
    }
    next_nonstatic_type_offset = align_size_up(notaligned_offset, heapOopSize );
    nonstatic_field_size = nonstatic_field_size + ((next_nonstatic_type_offset
                                   - first_nonstatic_field_offset)/heapOopSize);

    // Iterate over fields again and compute correct offsets.
    // The field allocation type was temporarily stored in the offset slot.
    // oop fields are located before non-oop fields (static and non-static).
    int len = fields->length();
    for (int i = 0; i < len; i += instanceKlass::next_offset) {
      int real_offset;
      FieldAllocationType atype = (FieldAllocationType) fields->ushort_at(i+4);
      switch (atype) {
        case STATIC_OOP:
          real_offset = next_static_oop_offset;
          next_static_oop_offset += heapOopSize;
          break;
        case STATIC_BYTE:
          real_offset = next_static_byte_offset;
          next_static_byte_offset += 1;
          break;
        case STATIC_SHORT:
          real_offset = next_static_short_offset;
          next_static_short_offset += BytesPerShort;
          break;
        case STATIC_WORD:
          real_offset = next_static_word_offset;
          next_static_word_offset += BytesPerInt;
          break;
        case STATIC_ALIGNED_DOUBLE:
        case STATIC_DOUBLE:
          real_offset = next_static_double_offset;
          next_static_double_offset += BytesPerLong;
          break;
        case NONSTATIC_OOP:
          if( nonstatic_oop_space_count > 0 ) {
            real_offset = nonstatic_oop_space_offset;
            nonstatic_oop_space_offset += heapOopSize;
            nonstatic_oop_space_count  -= 1;
          } else {
            real_offset = next_nonstatic_oop_offset;
            next_nonstatic_oop_offset += heapOopSize;
          }
          // Update oop maps
          if( nonstatic_oop_map_count > 0 &&
              nonstatic_oop_offsets[nonstatic_oop_map_count - 1] ==
              real_offset -
              int(nonstatic_oop_counts[nonstatic_oop_map_count - 1]) *
              heapOopSize ) {
            // Extend current oop map
            nonstatic_oop_counts[nonstatic_oop_map_count - 1] += 1;
          } else {
            // Create new oop map
            nonstatic_oop_offsets[nonstatic_oop_map_count] = real_offset;
            nonstatic_oop_counts [nonstatic_oop_map_count] = 1;
            nonstatic_oop_map_count += 1;
            if( first_nonstatic_oop_offset == 0 ) { // Undefined
              first_nonstatic_oop_offset = real_offset;
            }
          }
          break;
        case NONSTATIC_BYTE:
          if( nonstatic_byte_space_count > 0 ) {
            real_offset = nonstatic_byte_space_offset;
            nonstatic_byte_space_offset += 1;
            nonstatic_byte_space_count  -= 1;
          } else {
            real_offset = next_nonstatic_byte_offset;
            next_nonstatic_byte_offset += 1;
          }
          break;
        case NONSTATIC_SHORT:
          if( nonstatic_short_space_count > 0 ) {
            real_offset = nonstatic_short_space_offset;
            nonstatic_short_space_offset += BytesPerShort;
            nonstatic_short_space_count  -= 1;
          } else {
            real_offset = next_nonstatic_short_offset;
            next_nonstatic_short_offset += BytesPerShort;
          }
          break;
        case NONSTATIC_WORD:
          if( nonstatic_word_space_count > 0 ) {
            real_offset = nonstatic_word_space_offset;
            nonstatic_word_space_offset += BytesPerInt;
            nonstatic_word_space_count  -= 1;
          } else {
            real_offset = next_nonstatic_word_offset;
            next_nonstatic_word_offset += BytesPerInt;
          }
          break;
        case NONSTATIC_ALIGNED_DOUBLE:
        case NONSTATIC_DOUBLE:
          real_offset = next_nonstatic_double_offset;
          next_nonstatic_double_offset += BytesPerLong;
          break;
        default:
          ShouldNotReachHere();
      }
      fields->short_at_put(i+4, extract_low_short_from_int(real_offset) );
      fields->short_at_put(i+5, extract_high_short_from_int(real_offset) );
    }

    // Size of instances
    int instance_size;

    next_nonstatic_type_offset = align_size_up(notaligned_offset, wordSize );
    instance_size = align_object_size(next_nonstatic_type_offset / wordSize);

    assert(instance_size == align_object_size(align_size_up((instanceOopDesc::base_offset_in_bytes() + nonstatic_field_size*heapOopSize), wordSize) / wordSize), "consistent layout helper value");

    // Number of non-static oop map blocks allocated at end of klass.
    const unsigned int total_oop_map_count =
      compute_oop_map_count(super_klass, nonstatic_oop_map_count,
                            first_nonstatic_oop_offset);

    // Compute reference type
    ReferenceType rt;
    if (super_klass() == NULL) {
      rt = REF_NONE;
    } else {
      rt = super_klass->reference_type();
    }

    // We can now create the basic klassOop for this klass
    klassOop ik = oopFactory::new_instanceKlass(vtable_size, itable_size,
                                                static_field_size,
                                                total_oop_map_count,
                                                rt, CHECK_(nullHandle));
    instanceKlassHandle this_klass (THREAD, ik);

    assert(this_klass->static_field_size() == static_field_size, "sanity");
    assert(this_klass->nonstatic_oop_map_count() == total_oop_map_count,
           "sanity");

    // Fill in information already parsed
    this_klass->set_access_flags(access_flags);
    jint lh = Klass::instance_layout_helper(instance_size, false);
    this_klass->set_layout_helper(lh);
    assert(this_klass->oop_is_instance(), "layout is correct");
    assert(this_klass->size_helper() == instance_size, "correct size_helper");
    // Not yet: supers are done below to support the new subtype-checking fields
    //this_klass->set_super(super_klass());
    this_klass->set_class_loader(class_loader());
    this_klass->set_nonstatic_field_size(nonstatic_field_size);
    this_klass->set_has_nonstatic_fields(has_nonstatic_fields);
    this_klass->set_static_oop_field_size(fac.static_oop_count);
    cp->set_pool_holder(this_klass());
    this_klass->set_constants(cp());
    this_klass->set_local_interfaces(local_interfaces());
    this_klass->set_fields(fields());
    this_klass->set_methods(methods());
    if (has_final_method) {
      this_klass->set_has_final_method();
    }
    this_klass->set_method_ordering(method_ordering());
    this_klass->set_initial_method_idnum(methods->length());
    this_klass->set_name(cp->klass_name_at(this_class_index));
    if (LinkWellKnownClasses || is_anonymous())  // I am well known to myself
      cp->klass_at_put(this_class_index, this_klass()); // eagerly resolve
    this_klass->set_protection_domain(protection_domain());
    this_klass->set_fields_annotations(fields_annotations());
    this_klass->set_methods_annotations(methods_annotations());
    this_klass->set_methods_parameter_annotations(methods_parameter_annotations());
    this_klass->set_methods_default_annotations(methods_default_annotations());

    this_klass->set_minor_version(minor_version);
    this_klass->set_major_version(major_version);

    // Set up methodOop::intrinsic_id as soon as we know the names of methods.
    // (We used to do this lazily, but now we query it in Rewriter,
    // which is eagerly done for every method, so we might as well do it now,
    // when everything is fresh in memory.)
    if (methodOopDesc::klass_id_for_intrinsics(this_klass->as_klassOop()) != vmSymbols::NO_SID) {
      for (int j = 0; j < methods->length(); j++) {
        ((methodOop)methods->obj_at(j))->init_intrinsic_id();
      }
    }

    if (cached_class_file_bytes != NULL) {
      // JVMTI: we have an instanceKlass now, tell it about the cached bytes
      this_klass->set_cached_class_file(cached_class_file_bytes,
                                        cached_class_file_length);
    }

    // Miranda methods
    if ((num_miranda_methods > 0) ||
        // if this class introduced new miranda methods or
        (super_klass.not_null() && (super_klass->has_miranda_methods()))
        // super class exists and this class inherited miranda methods
        ) {
      this_klass->set_has_miranda_methods(); // then set a flag
    }

    // Additional attributes
    parse_classfile_attributes(cp, this_klass, CHECK_(nullHandle));

    // Make sure this is the end of class file stream
    guarantee_property(cfs->at_eos(), "Extra bytes at the end of class file %s", CHECK_(nullHandle));

    // Initialize static fields
    this_klass->do_local_static_fields(&initialize_static_field, CHECK_(nullHandle));

    // VerifyOops believes that once this has been set, the object is completely loaded.
    // Compute transitive closure of interfaces this class implements
    this_klass->set_transitive_interfaces(transitive_interfaces());

    // Fill in information needed to compute superclasses.
    this_klass->initialize_supers(super_klass(), CHECK_(nullHandle));

    // Initialize itable offset tables
    klassItable::setup_itable_offset_table(this_klass);

    // Do final class setup
    fill_oop_maps(this_klass, nonstatic_oop_map_count, nonstatic_oop_offsets, nonstatic_oop_counts);

    set_precomputed_flags(this_klass);

    // reinitialize modifiers, using the InnerClasses attribute
    int computed_modifiers = this_klass->compute_modifier_flags(CHECK_(nullHandle));
    this_klass->set_modifier_flags(computed_modifiers);

    // check if this class can access its super class
    check_super_class_access(this_klass, CHECK_(nullHandle));

    // check if this class can access its superinterfaces
    check_super_interface_access(this_klass, CHECK_(nullHandle));

    // check if this class overrides any final method
    check_final_method_override(this_klass, CHECK_(nullHandle));

    // check that if this class is an interface then it doesn't have static methods
    if (this_klass->is_interface()) {
      check_illegal_static_method(this_klass, CHECK_(nullHandle));
    }

    ClassLoadingService::notify_class_loaded(instanceKlass::cast(this_klass()),
                                             false /* not shared class */);

    if (TraceClassLoading) {
      // print in a single call to reduce interleaving of output
      if (cfs->source() != NULL) {
        tty->print("[Loaded %s from %s]\n", this_klass->external_name(),
                   cfs->source());
      } else if (class_loader.is_null()) {
        if (THREAD->is_Java_thread()) {
          klassOop caller = ((JavaThread*)THREAD)->security_get_caller_class(1);
          tty->print("[Loaded %s by instance of %s]\n",
                     this_klass->external_name(),
                     instanceKlass::cast(caller)->external_name());
        } else {
          tty->print("[Loaded %s]\n", this_klass->external_name());
        }
      } else {
        ResourceMark rm;
        tty->print("[Loaded %s from %s]\n", this_klass->external_name(),
                   instanceKlass::cast(class_loader->klass())->external_name());
      }
    }

    if (TraceClassResolution) {
      // print out the superclass.
      const char * from = Klass::cast(this_klass())->external_name();
      if (this_klass->java_super() != NULL) {
        tty->print("RESOLVE %s %s (super)\n", from, instanceKlass::cast(this_klass->java_super())->external_name());
      }
      // print out each of the interface classes referred to by this class.
      objArrayHandle local_interfaces(THREAD, this_klass->local_interfaces());
      if (!local_interfaces.is_null()) {
        int length = local_interfaces->length();
        for (int i = 0; i < length; i++) {
          klassOop k = klassOop(local_interfaces->obj_at(i));
          instanceKlass* to_class = instanceKlass::cast(k);
          const char * to = to_class->external_name();
          tty->print("RESOLVE %s %s (interface)\n", from, to);
        }
      }
    }

#ifndef PRODUCT
    if( PrintCompactFieldsSavings ) {
      if( nonstatic_field_size < orig_nonstatic_field_size ) {
        tty->print("[Saved %d of %d bytes in %s]\n",
                 (orig_nonstatic_field_size - nonstatic_field_size)*heapOopSize,
                 orig_nonstatic_field_size*heapOopSize,
                 this_klass->external_name());
      } else if( nonstatic_field_size > orig_nonstatic_field_size ) {
        tty->print("[Wasted %d over %d bytes in %s]\n",
                 (nonstatic_field_size - orig_nonstatic_field_size)*heapOopSize,
                 orig_nonstatic_field_size*heapOopSize,
                 this_klass->external_name());
      }
    }
#endif

    // preserve result across HandleMark
    preserve_this_klass = this_klass();
  }

  // Create new handle outside HandleMark
  instanceKlassHandle this_klass (THREAD, preserve_this_klass);
  debug_only(this_klass->as_klassOop()->verify();)

  return this_klass;
}


unsigned int
ClassFileParser::compute_oop_map_count(instanceKlassHandle super,
                                       unsigned int nonstatic_oop_map_count,
                                       int first_nonstatic_oop_offset) {
  unsigned int map_count =
    super.is_null() ? 0 : super->nonstatic_oop_map_count();
  if (nonstatic_oop_map_count > 0) {
    // We have oops to add to map
    if (map_count == 0) {
      map_count = nonstatic_oop_map_count;
    } else {
      // Check whether we should add a new map block or whether the last one can
      // be extended
      OopMapBlock* const first_map = super->start_of_nonstatic_oop_maps();
      OopMapBlock* const last_map = first_map + map_count - 1;

      int next_offset = last_map->offset() + last_map->count() * heapOopSize;
      if (next_offset == first_nonstatic_oop_offset) {
        // There is no gap bettwen superklass's last oop field and first
        // local oop field, merge maps.
        nonstatic_oop_map_count -= 1;
      } else {
        // Superklass didn't end with a oop field, add extra maps
        assert(next_offset < first_nonstatic_oop_offset, "just checking");
      }
      map_count += nonstatic_oop_map_count;
    }
  }
  return map_count;
}


void ClassFileParser::fill_oop_maps(instanceKlassHandle k,
                                    unsigned int nonstatic_oop_map_count,
                                    int* nonstatic_oop_offsets,
                                    unsigned int* nonstatic_oop_counts) {
  OopMapBlock* this_oop_map = k->start_of_nonstatic_oop_maps();
  const instanceKlass* const super = k->superklass();
  const unsigned int super_count = super ? super->nonstatic_oop_map_count() : 0;
  if (super_count > 0) {
    // Copy maps from superklass
    OopMapBlock* super_oop_map = super->start_of_nonstatic_oop_maps();
    for (unsigned int i = 0; i < super_count; ++i) {
      *this_oop_map++ = *super_oop_map++;
    }
  }

  if (nonstatic_oop_map_count > 0) {
    if (super_count + nonstatic_oop_map_count > k->nonstatic_oop_map_count()) {
      // The counts differ because there is no gap between superklass's last oop
      // field and the first local oop field.  Extend the last oop map copied
      // from the superklass instead of creating new one.
      nonstatic_oop_map_count--;
      nonstatic_oop_offsets++;
      this_oop_map--;
      this_oop_map->set_count(this_oop_map->count() + *nonstatic_oop_counts++);
      this_oop_map++;
    }

    // Add new map blocks, fill them
    while (nonstatic_oop_map_count-- > 0) {
      this_oop_map->set_offset(*nonstatic_oop_offsets++);
      this_oop_map->set_count(*nonstatic_oop_counts++);
      this_oop_map++;
    }
    assert(k->start_of_nonstatic_oop_maps() + k->nonstatic_oop_map_count() ==
           this_oop_map, "sanity");
  }
}


void ClassFileParser::set_precomputed_flags(instanceKlassHandle k) {
  klassOop super = k->super();

  // Check if this klass has an empty finalize method (i.e. one with return bytecode only),
  // in which case we don't have to register objects as finalizable
  if (!_has_empty_finalizer) {
    if (_has_finalizer ||
        (super != NULL && super->klass_part()->has_finalizer())) {
      k->set_has_finalizer();
    }
  }

#ifdef ASSERT
  bool f = false;
  methodOop m = k->lookup_method(vmSymbols::finalize_method_name(),
                                 vmSymbols::void_method_signature());
  if (m != NULL && !m->is_empty_method()) {
    f = true;
  }
  assert(f == k->has_finalizer(), "inconsistent has_finalizer");
#endif

  // Check if this klass supports the java.lang.Cloneable interface
  if (SystemDictionary::cloneable_klass_loaded()) {
    if (k->is_subtype_of(SystemDictionary::cloneable_klass())) {
      k->set_is_cloneable();
    }
  }

  // Check if this klass has a vanilla default constructor
  if (super == NULL) {
    // java.lang.Object has empty default constructor
    k->set_has_vanilla_constructor();
  } else {
    if (Klass::cast(super)->has_vanilla_constructor() &&
        _has_vanilla_constructor) {
      k->set_has_vanilla_constructor();
    }
#ifdef ASSERT
    bool v = false;
    if (Klass::cast(super)->has_vanilla_constructor()) {
      methodOop constructor = k->find_method(vmSymbols::object_initializer_name(
), vmSymbols::void_method_signature());
      if (constructor != NULL && constructor->is_vanilla_constructor()) {
        v = true;
      }
    }
    assert(v == k->has_vanilla_constructor(), "inconsistent has_vanilla_constructor");
#endif
  }

  // If it cannot be fast-path allocated, set a bit in the layout helper.
  // See documentation of instanceKlass::can_be_fastpath_allocated().
  assert(k->size_helper() > 0, "layout_helper is initialized");
  if ((!RegisterFinalizersAtInit && k->has_finalizer())
      || k->is_abstract() || k->is_interface()
      || (k->name() == vmSymbols::java_lang_Class()
          && k->class_loader() == NULL)
      || k->size_helper() >= FastAllocateSizeLimit) {
    // Forbid fast-path allocation.
    jint lh = Klass::instance_layout_helper(k->size_helper(), true);
    k->set_layout_helper(lh);
  }
}


// utility method for appending and array with check for duplicates

void append_interfaces(objArrayHandle result, int& index, objArrayOop ifs) {
  // iterate over new interfaces
  for (int i = 0; i < ifs->length(); i++) {
    oop e = ifs->obj_at(i);
    assert(e->is_klass() && instanceKlass::cast(klassOop(e))->is_interface(), "just checking");
    // check for duplicates
    bool duplicate = false;
    for (int j = 0; j < index; j++) {
      if (result->obj_at(j) == e) {
        duplicate = true;
        break;
      }
    }
    // add new interface
    if (!duplicate) {
      result->obj_at_put(index++, e);
    }
  }
}

objArrayHandle ClassFileParser::compute_transitive_interfaces(instanceKlassHandle super, objArrayHandle local_ifs, TRAPS) {
  // Compute maximum size for transitive interfaces
  int max_transitive_size = 0;
  int super_size = 0;
  // Add superclass transitive interfaces size
  if (super.not_null()) {
    super_size = super->transitive_interfaces()->length();
    max_transitive_size += super_size;
  }
  // Add local interfaces' super interfaces
  int local_size = local_ifs->length();
  for (int i = 0; i < local_size; i++) {
    klassOop l = klassOop(local_ifs->obj_at(i));
    max_transitive_size += instanceKlass::cast(l)->transitive_interfaces()->length();
  }
  // Finally add local interfaces
  max_transitive_size += local_size;
  // Construct array
  objArrayHandle result;
  if (max_transitive_size == 0) {
    // no interfaces, use canonicalized array
    result = objArrayHandle(THREAD, Universe::the_empty_system_obj_array());
  } else if (max_transitive_size == super_size) {
    // no new local interfaces added, share superklass' transitive interface array
    result = objArrayHandle(THREAD, super->transitive_interfaces());
  } else if (max_transitive_size == local_size) {
    // only local interfaces added, share local interface array
    result = local_ifs;
  } else {
    objArrayHandle nullHandle;
    objArrayOop new_objarray = oopFactory::new_system_objArray(max_transitive_size, CHECK_(nullHandle));
    result = objArrayHandle(THREAD, new_objarray);
    int index = 0;
    // Copy down from superclass
    if (super.not_null()) {
      append_interfaces(result, index, super->transitive_interfaces());
    }
    // Copy down from local interfaces' superinterfaces
    for (int i = 0; i < local_ifs->length(); i++) {
      klassOop l = klassOop(local_ifs->obj_at(i));
      append_interfaces(result, index, instanceKlass::cast(l)->transitive_interfaces());
    }
    // Finally add local interfaces
    append_interfaces(result, index, local_ifs());

    // Check if duplicates were removed
    if (index != max_transitive_size) {
      assert(index < max_transitive_size, "just checking");
      objArrayOop new_result = oopFactory::new_system_objArray(index, CHECK_(nullHandle));
      for (int i = 0; i < index; i++) {
        oop e = result->obj_at(i);
        assert(e != NULL, "just checking");
        new_result->obj_at_put(i, e);
      }
      result = objArrayHandle(THREAD, new_result);
    }
  }
  return result;
}


void ClassFileParser::check_super_class_access(instanceKlassHandle this_klass, TRAPS) {
  klassOop super = this_klass->super();
  if ((super != NULL) &&
      (!Reflection::verify_class_access(this_klass->as_klassOop(), super, false))) {
    ResourceMark rm(THREAD);
    Exceptions::fthrow(
      THREAD_AND_LOCATION,
      vmSymbolHandles::java_lang_IllegalAccessError(),
      "class %s cannot access its superclass %s",
      this_klass->external_name(),
      instanceKlass::cast(super)->external_name()
    );
    return;
  }
}


void ClassFileParser::check_super_interface_access(instanceKlassHandle this_klass, TRAPS) {
  objArrayHandle local_interfaces (THREAD, this_klass->local_interfaces());
  int lng = local_interfaces->length();
  for (int i = lng - 1; i >= 0; i--) {
    klassOop k = klassOop(local_interfaces->obj_at(i));
    assert (k != NULL && Klass::cast(k)->is_interface(), "invalid interface");
    if (!Reflection::verify_class_access(this_klass->as_klassOop(), k, false)) {
      ResourceMark rm(THREAD);
      Exceptions::fthrow(
        THREAD_AND_LOCATION,
        vmSymbolHandles::java_lang_IllegalAccessError(),
        "class %s cannot access its superinterface %s",
        this_klass->external_name(),
        instanceKlass::cast(k)->external_name()
      );
      return;
    }
  }
}


void ClassFileParser::check_final_method_override(instanceKlassHandle this_klass, TRAPS) {
  objArrayHandle methods (THREAD, this_klass->methods());
  int num_methods = methods->length();

  // go thru each method and check if it overrides a final method
  for (int index = 0; index < num_methods; index++) {
    methodOop m = (methodOop)methods->obj_at(index);

    // skip private, static and <init> methods
    if ((!m->is_private()) &&
        (!m->is_static()) &&
        (m->name() != vmSymbols::object_initializer_name())) {

      symbolOop name = m->name();
      symbolOop signature = m->signature();
      klassOop k = this_klass->super();
      methodOop super_m = NULL;
      while (k != NULL) {
        // skip supers that don't have final methods.
        if (k->klass_part()->has_final_method()) {
          // lookup a matching method in the super class hierarchy
          super_m = instanceKlass::cast(k)->lookup_method(name, signature);
          if (super_m == NULL) {
            break; // didn't find any match; get out
          }

          if (super_m->is_final() &&
              // matching method in super is final
              (Reflection::verify_field_access(this_klass->as_klassOop(),
                                               super_m->method_holder(),
                                               super_m->method_holder(),
                                               super_m->access_flags(), false))
            // this class can access super final method and therefore override
            ) {
            ResourceMark rm(THREAD);
            Exceptions::fthrow(
              THREAD_AND_LOCATION,
              vmSymbolHandles::java_lang_VerifyError(),
              "class %s overrides final method %s.%s",
              this_klass->external_name(),
              name->as_C_string(),
              signature->as_C_string()
            );
            return;
          }

          // continue to look from super_m's holder's super.
          k = instanceKlass::cast(super_m->method_holder())->super();
          continue;
        }

        k = k->klass_part()->super();
      }
    }
  }
}


// assumes that this_klass is an interface
void ClassFileParser::check_illegal_static_method(instanceKlassHandle this_klass, TRAPS) {
  assert(this_klass->is_interface(), "not an interface");
  objArrayHandle methods (THREAD, this_klass->methods());
  int num_methods = methods->length();

  for (int index = 0; index < num_methods; index++) {
    methodOop m = (methodOop)methods->obj_at(index);
    // if m is static and not the init method, throw a verify error
    if ((m->is_static()) && (m->name() != vmSymbols::class_initializer_name())) {
      ResourceMark rm(THREAD);
      Exceptions::fthrow(
        THREAD_AND_LOCATION,
        vmSymbolHandles::java_lang_VerifyError(),
        "Illegal static method %s in interface %s",
        m->name()->as_C_string(),
        this_klass->external_name()
      );
      return;
    }
  }
}

// utility methods for format checking

void ClassFileParser::verify_legal_class_modifiers(jint flags, TRAPS) {
  if (!_need_verify) { return; }

  const bool is_interface  = (flags & JVM_ACC_INTERFACE)  != 0;
  const bool is_abstract   = (flags & JVM_ACC_ABSTRACT)   != 0;
  const bool is_final      = (flags & JVM_ACC_FINAL)      != 0;
  const bool is_super      = (flags & JVM_ACC_SUPER)      != 0;
  const bool is_enum       = (flags & JVM_ACC_ENUM)       != 0;
  const bool is_annotation = (flags & JVM_ACC_ANNOTATION) != 0;
  const bool major_gte_15  = _major_version >= JAVA_1_5_VERSION;

  if ((is_abstract && is_final) ||
      (is_interface && !is_abstract) ||
      (is_interface && major_gte_15 && (is_super || is_enum)) ||
      (!is_interface && major_gte_15 && is_annotation)) {
    ResourceMark rm(THREAD);
    Exceptions::fthrow(
      THREAD_AND_LOCATION,
      vmSymbolHandles::java_lang_ClassFormatError(),
      "Illegal class modifiers in class %s: 0x%X",
      _class_name->as_C_string(), flags
    );
    return;
  }
}

bool ClassFileParser::has_illegal_visibility(jint flags) {
  const bool is_public    = (flags & JVM_ACC_PUBLIC)    != 0;
  const bool is_protected = (flags & JVM_ACC_PROTECTED) != 0;
  const bool is_private   = (flags & JVM_ACC_PRIVATE)   != 0;

  return ((is_public && is_protected) ||
          (is_public && is_private) ||
          (is_protected && is_private));
}

bool ClassFileParser::is_supported_version(u2 major, u2 minor) {
  u2 max_version = JDK_Version::is_gte_jdk17x_version() ?
    JAVA_MAX_SUPPORTED_VERSION : JAVA_6_VERSION;
  return (major >= JAVA_MIN_SUPPORTED_VERSION) &&
         (major <= max_version) &&
         ((major != max_version) ||
          (minor <= JAVA_MAX_SUPPORTED_MINOR_VERSION));
}

void ClassFileParser::verify_legal_field_modifiers(
    jint flags, bool is_interface, TRAPS) {
  if (!_need_verify) { return; }

  const bool is_public    = (flags & JVM_ACC_PUBLIC)    != 0;
  const bool is_protected = (flags & JVM_ACC_PROTECTED) != 0;
  const bool is_private   = (flags & JVM_ACC_PRIVATE)   != 0;
  const bool is_static    = (flags & JVM_ACC_STATIC)    != 0;
  const bool is_final     = (flags & JVM_ACC_FINAL)     != 0;
  const bool is_volatile  = (flags & JVM_ACC_VOLATILE)  != 0;
  const bool is_transient = (flags & JVM_ACC_TRANSIENT) != 0;
  const bool is_enum      = (flags & JVM_ACC_ENUM)      != 0;
  const bool major_gte_15 = _major_version >= JAVA_1_5_VERSION;

  bool is_illegal = false;

  if (is_interface) {
    if (!is_public || !is_static || !is_final || is_private ||
        is_protected || is_volatile || is_transient ||
        (major_gte_15 && is_enum)) {
      is_illegal = true;
    }
  } else { // not interface
    if (has_illegal_visibility(flags) || (is_final && is_volatile)) {
      is_illegal = true;
    }
  }

  if (is_illegal) {
    ResourceMark rm(THREAD);
    Exceptions::fthrow(
      THREAD_AND_LOCATION,
      vmSymbolHandles::java_lang_ClassFormatError(),
      "Illegal field modifiers in class %s: 0x%X",
      _class_name->as_C_string(), flags);
    return;
  }
}

void ClassFileParser::verify_legal_method_modifiers(
    jint flags, bool is_interface, symbolHandle name, TRAPS) {
  if (!_need_verify) { return; }

  const bool is_public       = (flags & JVM_ACC_PUBLIC)       != 0;
  const bool is_private      = (flags & JVM_ACC_PRIVATE)      != 0;
  const bool is_static       = (flags & JVM_ACC_STATIC)       != 0;
  const bool is_final        = (flags & JVM_ACC_FINAL)        != 0;
  const bool is_native       = (flags & JVM_ACC_NATIVE)       != 0;
  const bool is_abstract     = (flags & JVM_ACC_ABSTRACT)     != 0;
  const bool is_bridge       = (flags & JVM_ACC_BRIDGE)       != 0;
  const bool is_strict       = (flags & JVM_ACC_STRICT)       != 0;
  const bool is_synchronized = (flags & JVM_ACC_SYNCHRONIZED) != 0;
  const bool major_gte_15    = _major_version >= JAVA_1_5_VERSION;
  const bool is_initializer  = (name == vmSymbols::object_initializer_name());

  bool is_illegal = false;

  if (is_interface) {
    if (!is_abstract || !is_public || is_static || is_final ||
        is_native || (major_gte_15 && (is_synchronized || is_strict))) {
      is_illegal = true;
    }
  } else { // not interface
    if (is_initializer) {
      if (is_static || is_final || is_synchronized || is_native ||
          is_abstract || (major_gte_15 && is_bridge)) {
        is_illegal = true;
      }
    } else { // not initializer
      if (is_abstract) {
        if ((is_final || is_native || is_private || is_static ||
            (major_gte_15 && (is_synchronized || is_strict)))) {
          is_illegal = true;
        }
      }
      if (has_illegal_visibility(flags)) {
        is_illegal = true;
      }
    }
  }

  if (is_illegal) {
    ResourceMark rm(THREAD);
    Exceptions::fthrow(
      THREAD_AND_LOCATION,
      vmSymbolHandles::java_lang_ClassFormatError(),
      "Method %s in class %s has illegal modifiers: 0x%X",
      name->as_C_string(), _class_name->as_C_string(), flags);
    return;
  }
}

void ClassFileParser::verify_legal_utf8(const unsigned char* buffer, int length, TRAPS) {
  assert(_need_verify, "only called when _need_verify is true");
  int i = 0;
  int count = length >> 2;
  for (int k=0; k<count; k++) {
    unsigned char b0 = buffer[i];
    unsigned char b1 = buffer[i+1];
    unsigned char b2 = buffer[i+2];
    unsigned char b3 = buffer[i+3];
    // For an unsigned char v,
    // (v | v - 1) is < 128 (highest bit 0) for 0 < v < 128;
    // (v | v - 1) is >= 128 (highest bit 1) for v == 0 or v >= 128.
    unsigned char res = b0 | b0 - 1 |
                        b1 | b1 - 1 |
                        b2 | b2 - 1 |
                        b3 | b3 - 1;
    if (res >= 128) break;
    i += 4;
  }
  for(; i < length; i++) {
    unsigned short c;
    // no embedded zeros
    guarantee_property((buffer[i] != 0), "Illegal UTF8 string in constant pool in class file %s", CHECK);
    if(buffer[i] < 128) {
      continue;
    }
    if ((i + 5) < length) { // see if it's legal supplementary character
      if (UTF8::is_supplementary_character(&buffer[i])) {
        c = UTF8::get_supplementary_character(&buffer[i]);
        i += 5;
        continue;
      }
    }
    switch (buffer[i] >> 4) {
      default: break;
      case 0x8: case 0x9: case 0xA: case 0xB: case 0xF:
        classfile_parse_error("Illegal UTF8 string in constant pool in class file %s", CHECK);
      case 0xC: case 0xD:  // 110xxxxx  10xxxxxx
        c = (buffer[i] & 0x1F) << 6;
        i++;
        if ((i < length) && ((buffer[i] & 0xC0) == 0x80)) {
          c += buffer[i] & 0x3F;
          if (_major_version <= 47 || c == 0 || c >= 0x80) {
            // for classes with major > 47, c must a null or a character in its shortest form
            break;
          }
        }
        classfile_parse_error("Illegal UTF8 string in constant pool in class file %s", CHECK);
      case 0xE:  // 1110xxxx 10xxxxxx 10xxxxxx
        c = (buffer[i] & 0xF) << 12;
        i += 2;
        if ((i < length) && ((buffer[i-1] & 0xC0) == 0x80) && ((buffer[i] & 0xC0) == 0x80)) {
          c += ((buffer[i-1] & 0x3F) << 6) + (buffer[i] & 0x3F);
          if (_major_version <= 47 || c >= 0x800) {
            // for classes with major > 47, c must be in its shortest form
            break;
          }
        }
        classfile_parse_error("Illegal UTF8 string in constant pool in class file %s", CHECK);
    }  // end of switch
  } // end of for
}

// Checks if name is a legal class name.
void ClassFileParser::verify_legal_class_name(symbolHandle name, TRAPS) {
  if (!_need_verify || _relax_verify) { return; }

  char buf[fixed_buffer_size];
  char* bytes = name->as_utf8_flexible_buffer(THREAD, buf, fixed_buffer_size);
  unsigned int length = name->utf8_length();
  bool legal = false;

  if (length > 0) {
    char* p;
    if (bytes[0] == JVM_SIGNATURE_ARRAY) {
      p = skip_over_field_signature(bytes, false, length, CHECK);
      legal = (p != NULL) && ((p - bytes) == (int)length);
    } else if (_major_version < JAVA_1_5_VERSION) {
      if (bytes[0] != '<') {
        p = skip_over_field_name(bytes, true, length);
        legal = (p != NULL) && ((p - bytes) == (int)length);
      }
    } else {
      // 4900761: relax the constraints based on JSR202 spec
      // Class names may be drawn from the entire Unicode character set.
      // Identifiers between '/' must be unqualified names.
      // The utf8 string has been verified when parsing cpool entries.
      legal = verify_unqualified_name(bytes, length, LegalClass);
    }
  }
  if (!legal) {
    ResourceMark rm(THREAD);
    Exceptions::fthrow(
      THREAD_AND_LOCATION,
      vmSymbolHandles::java_lang_ClassFormatError(),
      "Illegal class name \"%s\" in class file %s", bytes,
      _class_name->as_C_string()
    );
    return;
  }
}

// Checks if name is a legal field name.
void ClassFileParser::verify_legal_field_name(symbolHandle name, TRAPS) {
  if (!_need_verify || _relax_verify) { return; }

  char buf[fixed_buffer_size];
  char* bytes = name->as_utf8_flexible_buffer(THREAD, buf, fixed_buffer_size);
  unsigned int length = name->utf8_length();
  bool legal = false;

  if (length > 0) {
    if (_major_version < JAVA_1_5_VERSION) {
      if (bytes[0] != '<') {
        char* p = skip_over_field_name(bytes, false, length);
        legal = (p != NULL) && ((p - bytes) == (int)length);
      }
    } else {
      // 4881221: relax the constraints based on JSR202 spec
      legal = verify_unqualified_name(bytes, length, LegalField);
    }
  }

  if (!legal) {
    ResourceMark rm(THREAD);
    Exceptions::fthrow(
      THREAD_AND_LOCATION,
      vmSymbolHandles::java_lang_ClassFormatError(),
      "Illegal field name \"%s\" in class %s", bytes,
      _class_name->as_C_string()
    );
    return;
  }
}

// Checks if name is a legal method name.
void ClassFileParser::verify_legal_method_name(symbolHandle name, TRAPS) {
  if (!_need_verify || _relax_verify) { return; }

  assert(!name.is_null(), "method name is null");
  char buf[fixed_buffer_size];
  char* bytes = name->as_utf8_flexible_buffer(THREAD, buf, fixed_buffer_size);
  unsigned int length = name->utf8_length();
  bool legal = false;

  if (length > 0) {
    if (bytes[0] == '<') {
      if (name == vmSymbols::object_initializer_name() || name == vmSymbols::class_initializer_name()) {
        legal = true;
      }
    } else if (_major_version < JAVA_1_5_VERSION) {
      char* p;
      p = skip_over_field_name(bytes, false, length);
      legal = (p != NULL) && ((p - bytes) == (int)length);
    } else {
      // 4881221: relax the constraints based on JSR202 spec
      legal = verify_unqualified_name(bytes, length, LegalMethod);
    }
  }

  if (!legal) {
    ResourceMark rm(THREAD);
    Exceptions::fthrow(
      THREAD_AND_LOCATION,
      vmSymbolHandles::java_lang_ClassFormatError(),
      "Illegal method name \"%s\" in class %s", bytes,
      _class_name->as_C_string()
    );
    return;
  }
}


// Checks if signature is a legal field signature.
void ClassFileParser::verify_legal_field_signature(symbolHandle name, symbolHandle signature, TRAPS) {
  if (!_need_verify) { return; }

  char buf[fixed_buffer_size];
  char* bytes = signature->as_utf8_flexible_buffer(THREAD, buf, fixed_buffer_size);
  unsigned int length = signature->utf8_length();
  char* p = skip_over_field_signature(bytes, false, length, CHECK);

  if (p == NULL || (p - bytes) != (int)length) {
    ResourceMark rm(THREAD);
    Exceptions::fthrow(
      THREAD_AND_LOCATION,
      vmSymbolHandles::java_lang_ClassFormatError(),
      "Field \"%s\" in class %s has illegal signature \"%s\"",
      name->as_C_string(), _class_name->as_C_string(), bytes
    );
    return;
  }
}

// Checks if signature is a legal method signature.
// Returns number of parameters
int ClassFileParser::verify_legal_method_signature(symbolHandle name, symbolHandle signature, TRAPS) {
  if (!_need_verify) {
    // make sure caller's args_size will be less than 0 even for non-static
    // method so it will be recomputed in compute_size_of_parameters().
    return -2;
  }

  unsigned int args_size = 0;
  char buf[fixed_buffer_size];
  char* p = signature->as_utf8_flexible_buffer(THREAD, buf, fixed_buffer_size);
  unsigned int length = signature->utf8_length();
  char* nextp;

  // The first character must be a '('
  if ((length > 0) && (*p++ == JVM_SIGNATURE_FUNC)) {
    length--;
    // Skip over legal field signatures
    nextp = skip_over_field_signature(p, false, length, CHECK_0);
    while ((length > 0) && (nextp != NULL)) {
      args_size++;
      if (p[0] == 'J' || p[0] == 'D') {
        args_size++;
      }
      length -= nextp - p;
      p = nextp;
      nextp = skip_over_field_signature(p, false, length, CHECK_0);
    }
    // The first non-signature thing better be a ')'
    if ((length > 0) && (*p++ == JVM_SIGNATURE_ENDFUNC)) {
      length--;
      if (name->utf8_length() > 0 && name->byte_at(0) == '<') {
        // All internal methods must return void
        if ((length == 1) && (p[0] == JVM_SIGNATURE_VOID)) {
          return args_size;
        }
      } else {
        // Now we better just have a return value
        nextp = skip_over_field_signature(p, true, length, CHECK_0);
        if (nextp && ((int)length == (nextp - p))) {
          return args_size;
        }
      }
    }
  }
  // Report error
  ResourceMark rm(THREAD);
  Exceptions::fthrow(
    THREAD_AND_LOCATION,
    vmSymbolHandles::java_lang_ClassFormatError(),
    "Method \"%s\" in class %s has illegal signature \"%s\"",
    name->as_C_string(),  _class_name->as_C_string(), p
  );
  return 0;
}


// Unqualified names may not contain the characters '.', ';', or '/'.
// Method names also may not contain the characters '<' or '>', unless <init> or <clinit>.
// Note that method names may not be <init> or <clinit> in this method.
// Because these names have been checked as special cases before calling this method
// in verify_legal_method_name.
bool ClassFileParser::verify_unqualified_name(char* name, unsigned int length, int type) {
  jchar ch;

  for (char* p = name; p != name + length; ) {
    ch = *p;
    if (ch < 128) {
      p++;
      if (ch == '.' || ch == ';') {
        return false;   // do not permit '.' or ';'
      }
      if (type != LegalClass && ch == '/') {
        return false;   // do not permit '/' unless it's class name
      }
      if (type == LegalMethod && (ch == '<' || ch == '>')) {
        return false;   // do not permit '<' or '>' in method names
      }
    } else {
      char* tmp_p = UTF8::next(p, &ch);
      p = tmp_p;
    }
  }
  return true;
}


// Take pointer to a string. Skip over the longest part of the string that could
// be taken as a fieldname. Allow '/' if slash_ok is true.
// Return a pointer to just past the fieldname.
// Return NULL if no fieldname at all was found, or in the case of slash_ok
// being true, we saw consecutive slashes (meaning we were looking for a
// qualified path but found something that was badly-formed).
char* ClassFileParser::skip_over_field_name(char* name, bool slash_ok, unsigned int length) {
  char* p;
  jchar ch;
  jboolean last_is_slash = false;
  jboolean not_first_ch = false;

  for (p = name; p != name + length; not_first_ch = true) {
    char* old_p = p;
    ch = *p;
    if (ch < 128) {
      p++;
      // quick check for ascii
      if ((ch >= 'a' && ch <= 'z') ||
          (ch >= 'A' && ch <= 'Z') ||
          (ch == '_' || ch == '$') ||
          (not_first_ch && ch >= '0' && ch <= '9')) {
        last_is_slash = false;
        continue;
      }
      if (slash_ok && ch == '/') {
        if (last_is_slash) {
          return NULL;  // Don't permit consecutive slashes
        }
        last_is_slash = true;
        continue;
      }
    } else {
      jint unicode_ch;
      char* tmp_p = UTF8::next_character(p, &unicode_ch);
      p = tmp_p;
      last_is_slash = false;
      // Check if ch is Java identifier start or is Java identifier part
      // 4672820: call java.lang.Character methods directly without generating separate tables.
      EXCEPTION_MARK;
      instanceKlassHandle klass (THREAD, SystemDictionary::char_klass());

      // return value
      JavaValue result(T_BOOLEAN);
      // Set up the arguments to isJavaIdentifierStart and isJavaIdentifierPart
      JavaCallArguments args;
      args.push_int(unicode_ch);

      // public static boolean isJavaIdentifierStart(char ch);
      JavaCalls::call_static(&result,
                             klass,
                             vmSymbolHandles::isJavaIdentifierStart_name(),
                             vmSymbolHandles::int_bool_signature(),
                             &args,
                             THREAD);

      if (HAS_PENDING_EXCEPTION) {
        CLEAR_PENDING_EXCEPTION;
        return 0;
      }
      if (result.get_jboolean()) {
        continue;
      }

      if (not_first_ch) {
        // public static boolean isJavaIdentifierPart(char ch);
        JavaCalls::call_static(&result,
                               klass,
                               vmSymbolHandles::isJavaIdentifierPart_name(),
                               vmSymbolHandles::int_bool_signature(),
                               &args,
                               THREAD);

        if (HAS_PENDING_EXCEPTION) {
          CLEAR_PENDING_EXCEPTION;
          return 0;
        }

        if (result.get_jboolean()) {
          continue;
        }
      }
    }
    return (not_first_ch) ? old_p : NULL;
  }
  return (not_first_ch) ? p : NULL;
}


// Take pointer to a string. Skip over the longest part of the string that could
// be taken as a field signature. Allow "void" if void_ok.
// Return a pointer to just past the signature.
// Return NULL if no legal signature is found.
char* ClassFileParser::skip_over_field_signature(char* signature,
                                                 bool void_ok,
                                                 unsigned int length,
                                                 TRAPS) {
  unsigned int array_dim = 0;
  while (length > 0) {
    switch (signature[0]) {
      case JVM_SIGNATURE_VOID: if (!void_ok) { return NULL; }
      case JVM_SIGNATURE_BOOLEAN:
      case JVM_SIGNATURE_BYTE:
      case JVM_SIGNATURE_CHAR:
      case JVM_SIGNATURE_SHORT:
      case JVM_SIGNATURE_INT:
      case JVM_SIGNATURE_FLOAT:
      case JVM_SIGNATURE_LONG:
      case JVM_SIGNATURE_DOUBLE:
        return signature + 1;
      case JVM_SIGNATURE_CLASS: {
        if (_major_version < JAVA_1_5_VERSION) {
          // Skip over the class name if one is there
          char* p = skip_over_field_name(signature + 1, true, --length);

          // The next character better be a semicolon
          if (p && (p - signature) > 1 && p[0] == ';') {
            return p + 1;
          }
        } else {
          // 4900761: For class version > 48, any unicode is allowed in class name.
          length--;
          signature++;
          while (length > 0 && signature[0] != ';') {
            if (signature[0] == '.') {
              classfile_parse_error("Class name contains illegal character '.' in descriptor in class file %s", CHECK_0);
            }
            length--;
            signature++;
          }
          if (signature[0] == ';') { return signature + 1; }
        }

        return NULL;
      }
      case JVM_SIGNATURE_ARRAY:
        array_dim++;
        if (array_dim > 255) {
          // 4277370: array descriptor is valid only if it represents 255 or fewer dimensions.
          classfile_parse_error("Array type descriptor has more than 255 dimensions in class file %s", CHECK_0);
        }
        // The rest of what's there better be a legal signature
        signature++;
        length--;
        void_ok = false;
        break;

      default:
        return NULL;
    }
  }
  return NULL;
}

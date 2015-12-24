/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classFileParser.hpp"
#include "classfile/classFileStream.hpp"
#include "classfile/classLoader.hpp"
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/defaultMethods.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/systemDictionary.hpp"
#include "classfile/verificationType.hpp"
#include "classfile/verifier.hpp"
#include "classfile/vmSymbols.hpp"
#include "gc/shared/gcLocker.hpp"
#include "memory/allocation.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/oopFactory.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.inline.hpp"
#include "oops/annotations.hpp"
#include "oops/fieldStreams.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/instanceMirrorKlass.hpp"
#include "oops/klass.inline.hpp"
#include "oops/klassVtable.hpp"
#include "oops/metadata.hpp"
#include "oops/method.hpp"
#include "oops/oop.inline.hpp"
#include "oops/symbol.hpp"
#include "prims/jvm.h"
#include "prims/jvmtiExport.hpp"
#include "prims/jvmtiThreadState.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/perfData.hpp"
#include "runtime/reflection.hpp"
#include "runtime/signature.hpp"
#include "runtime/timer.hpp"
#include "services/classLoadingService.hpp"
#include "services/threadService.hpp"
#include "trace/traceMacros.hpp"
#include "utilities/array.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"
#include "utilities/resourceHash.hpp"
#if INCLUDE_CDS
#include "classfile/systemDictionaryShared.hpp"
#endif

// We generally try to create the oops directly when parsing, rather than
// allocating temporary data structures and copying the bytes twice. A
// temporary area is only needed when parsing utf8 entries in the constant
// pool and when parsing line number tables.

// We add assert in debug mode when class format is not checked.

#define JAVA_CLASSFILE_MAGIC              0xCAFEBABE
#define JAVA_MIN_SUPPORTED_VERSION        45
#define JAVA_MAX_SUPPORTED_VERSION        52
#define JAVA_MAX_SUPPORTED_MINOR_VERSION  0

// Used for two backward compatibility reasons:
// - to check for new additions to the class file format in JDK1.5
// - to check for bug fixes in the format checker in JDK1.5
#define JAVA_1_5_VERSION                  49

// Used for backward compatibility reasons:
// - to check for javac bug fixes that happened after 1.5
// - also used as the max version when running in jdk6
#define JAVA_6_VERSION                    50

// Used for backward compatibility reasons:
// - to check NameAndType_info signatures more aggressively
// - to disallow argument and require ACC_STATIC for <clinit> methods
#define JAVA_7_VERSION                    51

// Extension method support.
#define JAVA_8_VERSION                    52

enum { LegalClass, LegalField, LegalMethod }; // used to verify unqualified names

void ClassFileParser::parse_constant_pool_entries(const ClassFileStream* const stream,
                                                  ConstantPool* cp,
                                                  const int length,
                                                  TRAPS) {
  assert(stream != NULL, "invariant");
  assert(cp != NULL, "invariant");

  // Use a local copy of ClassFileStream. It helps the C++ compiler to optimize
  // this function (_current can be allocated in a register, with scalar
  // replacement of aggregates). The _current pointer is copied back to
  // stream() when this function returns. DON'T call another method within
  // this method that uses stream().
  const ClassFileStream cfs1 = *stream;
  const ClassFileStream* const cfs = &cfs1;

  assert(cfs->allocated_on_stack(), "should be local");
  debug_only(const u1* const old_current = stream->current();)

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
    const u1 tag = cfs->get_u1_fast();
    switch (tag) {
      case JVM_CONSTANT_Class : {
        cfs->guarantee_more(3, CHECK);  // name_index, tag/access_flags
        const u2 name_index = cfs->get_u2_fast();
        cp->klass_index_at_put(index, name_index);
        break;
      }
      case JVM_CONSTANT_Fieldref: {
        cfs->guarantee_more(5, CHECK);  // class_index, name_and_type_index, tag/access_flags
        const u2 class_index = cfs->get_u2_fast();
        const u2 name_and_type_index = cfs->get_u2_fast();
        cp->field_at_put(index, class_index, name_and_type_index);
        break;
      }
      case JVM_CONSTANT_Methodref: {
        cfs->guarantee_more(5, CHECK);  // class_index, name_and_type_index, tag/access_flags
        const u2 class_index = cfs->get_u2_fast();
        const u2 name_and_type_index = cfs->get_u2_fast();
        cp->method_at_put(index, class_index, name_and_type_index);
        break;
      }
      case JVM_CONSTANT_InterfaceMethodref: {
        cfs->guarantee_more(5, CHECK);  // class_index, name_and_type_index, tag/access_flags
        const u2 class_index = cfs->get_u2_fast();
        const u2 name_and_type_index = cfs->get_u2_fast();
        cp->interface_method_at_put(index, class_index, name_and_type_index);
        break;
      }
      case JVM_CONSTANT_String : {
        cfs->guarantee_more(3, CHECK);  // string_index, tag/access_flags
        const u2 string_index = cfs->get_u2_fast();
        cp->string_index_at_put(index, string_index);
        break;
      }
      case JVM_CONSTANT_MethodHandle :
      case JVM_CONSTANT_MethodType: {
        if (_major_version < Verifier::INVOKEDYNAMIC_MAJOR_VERSION) {
          classfile_parse_error(
            "Class file version does not support constant tag %u in class file %s",
            tag, CHECK);
        }
        if (tag == JVM_CONSTANT_MethodHandle) {
          cfs->guarantee_more(4, CHECK);  // ref_kind, method_index, tag/access_flags
          const u1 ref_kind = cfs->get_u1_fast();
          const u2 method_index = cfs->get_u2_fast();
          cp->method_handle_index_at_put(index, ref_kind, method_index);
        }
        else if (tag == JVM_CONSTANT_MethodType) {
          cfs->guarantee_more(3, CHECK);  // signature_index, tag/access_flags
          const u2 signature_index = cfs->get_u2_fast();
          cp->method_type_index_at_put(index, signature_index);
        }
        else {
          ShouldNotReachHere();
        }
        break;
      }
      case JVM_CONSTANT_InvokeDynamic : {
        if (_major_version < Verifier::INVOKEDYNAMIC_MAJOR_VERSION) {
          classfile_parse_error(
              "Class file version does not support constant tag %u in class file %s",
              tag, CHECK);
        }
        cfs->guarantee_more(5, CHECK);  // bsm_index, nt, tag/access_flags
        const u2 bootstrap_specifier_index = cfs->get_u2_fast();
        const u2 name_and_type_index = cfs->get_u2_fast();
        if (_max_bootstrap_specifier_index < (int) bootstrap_specifier_index) {
          _max_bootstrap_specifier_index = (int) bootstrap_specifier_index;  // collect for later
        }
        cp->invoke_dynamic_at_put(index, bootstrap_specifier_index, name_and_type_index);
        break;
      }
      case JVM_CONSTANT_Integer: {
        cfs->guarantee_more(5, CHECK);  // bytes, tag/access_flags
        const u4 bytes = cfs->get_u4_fast();
        cp->int_at_put(index, (jint)bytes);
        break;
      }
      case JVM_CONSTANT_Float: {
        cfs->guarantee_more(5, CHECK);  // bytes, tag/access_flags
        const u4 bytes = cfs->get_u4_fast();
        cp->float_at_put(index, *(jfloat*)&bytes);
        break;
      }
      case JVM_CONSTANT_Long: {
        // A mangled type might cause you to overrun allocated memory
        guarantee_property(index + 1 < length,
                           "Invalid constant pool entry %u in class file %s",
                           index,
                           CHECK);
        cfs->guarantee_more(9, CHECK);  // bytes, tag/access_flags
        const u8 bytes = cfs->get_u8_fast();
        cp->long_at_put(index, bytes);
        index++;   // Skip entry following eigth-byte constant, see JVM book p. 98
        break;
      }
      case JVM_CONSTANT_Double: {
        // A mangled type might cause you to overrun allocated memory
        guarantee_property(index+1 < length,
                           "Invalid constant pool entry %u in class file %s",
                           index,
                           CHECK);
        cfs->guarantee_more(9, CHECK);  // bytes, tag/access_flags
        const u8 bytes = cfs->get_u8_fast();
        cp->double_at_put(index, *(jdouble*)&bytes);
        index++;   // Skip entry following eigth-byte constant, see JVM book p. 98
        break;
      }
      case JVM_CONSTANT_NameAndType: {
        cfs->guarantee_more(5, CHECK);  // name_index, signature_index, tag/access_flags
        const u2 name_index = cfs->get_u2_fast();
        const u2 signature_index = cfs->get_u2_fast();
        cp->name_and_type_at_put(index, name_index, signature_index);
        break;
      }
      case JVM_CONSTANT_Utf8 : {
        cfs->guarantee_more(2, CHECK);  // utf8_length
        u2  utf8_length = cfs->get_u2_fast();
        const u1* utf8_buffer = cfs->get_u1_buffer();
        assert(utf8_buffer != NULL, "null utf8 buffer");
        // Got utf8 string, guarantee utf8_length+1 bytes, set stream position forward.
        cfs->guarantee_more(utf8_length+1, CHECK);  // utf8 string, tag/access_flags
        cfs->skip_u1_fast(utf8_length);

        // Before storing the symbol, make sure it's legal
        if (_need_verify) {
          verify_legal_utf8(utf8_buffer, utf8_length, CHECK);
        }

        if (has_cp_patch_at(index)) {
          Handle patch = clear_cp_patch_at(index);
          guarantee_property(java_lang_String::is_instance(patch()),
                             "Illegal utf8 patch at %d in class file %s",
                             index,
                             CHECK);
          const char* const str = java_lang_String::as_utf8_string(patch());
          // (could use java_lang_String::as_symbol instead, but might as well batch them)
          utf8_buffer = (const u1*) str;
          utf8_length = (int) strlen(str);
        }

        unsigned int hash;
        Symbol* const result = SymbolTable::lookup_only((const char*)utf8_buffer,
                                                        utf8_length,
                                                        hash);
        if (result == NULL) {
          names[names_count] = (const char*)utf8_buffer;
          lengths[names_count] = utf8_length;
          indices[names_count] = index;
          hashValues[names_count++] = hash;
          if (names_count == SymbolTable::symbol_alloc_batch_size) {
            SymbolTable::new_symbols(_loader_data,
                                     cp,
                                     names_count,
                                     names,
                                     lengths,
                                     indices,
                                     hashValues,
                                     CHECK);
            names_count = 0;
          }
        } else {
          cp->symbol_at_put(index, result);
        }
        break;
      }
      default: {
        classfile_parse_error("Unknown constant tag %u in class file %s",
                              tag,
                              CHECK);
        break;
      }
    } // end of switch(tag)
  } // end of for

  // Allocate the remaining symbols
  if (names_count > 0) {
    SymbolTable::new_symbols(_loader_data,
                             cp,
                             names_count,
                             names,
                             lengths,
                             indices,
                             hashValues,
                             CHECK);
  }

  // Copy _current pointer of local copy back to stream.
  assert(stream->current() == old_current, "non-exclusive use of stream");
  stream->set_current(cfs1.current());

}

static inline bool valid_cp_range(int index, int length) {
  return (index > 0 && index < length);
}

static inline Symbol* check_symbol_at(const ConstantPool* cp, int index) {
  assert(cp != NULL, "invariant");
  if (valid_cp_range(index, cp->length()) && cp->tag_at(index).is_utf8()) {
    return cp->symbol_at(index);
  }
  return NULL;
}

#ifdef ASSERT
PRAGMA_DIAG_PUSH
PRAGMA_FORMAT_NONLITERAL_IGNORED
void ClassFileParser::report_assert_property_failure(const char* msg, TRAPS) const {
  ResourceMark rm(THREAD);
  fatal(msg, _class_name->as_C_string());
}

void ClassFileParser::report_assert_property_failure(const char* msg,
                                                     int index,
                                                     TRAPS) const {
  ResourceMark rm(THREAD);
  fatal(msg, index, _class_name->as_C_string());
}
PRAGMA_DIAG_POP
#endif

void ClassFileParser::parse_constant_pool(const ClassFileStream* const stream,
                                          ConstantPool* const cp,
                                          const int length,
                                          TRAPS) {
  assert(cp != NULL, "invariant");
  assert(stream != NULL, "invariant");

  // parsing constant pool entries
  parse_constant_pool_entries(stream, cp, length, CHECK);

  int index = 1;  // declared outside of loops for portability

  // first verification pass - validate cross references
  // and fixup class and string constants
  for (index = 1; index < length; index++) {          // Index 0 is unused
    const jbyte tag = cp->tag_at(index).value();
    switch (tag) {
      case JVM_CONSTANT_Class: {
        ShouldNotReachHere();     // Only JVM_CONSTANT_ClassIndex should be present
        break;
      }
      case JVM_CONSTANT_Fieldref:
        // fall through
      case JVM_CONSTANT_Methodref:
        // fall through
      case JVM_CONSTANT_InterfaceMethodref: {
        if (!_need_verify) break;
        const int klass_ref_index = cp->klass_ref_index_at(index);
        const int name_and_type_ref_index = cp->name_and_type_ref_index_at(index);
        check_property(valid_klass_reference_at(klass_ref_index),
                       "Invalid constant pool index %u in class file %s",
                       klass_ref_index, CHECK);
        check_property(valid_cp_range(name_and_type_ref_index, length) &&
          cp->tag_at(name_and_type_ref_index).is_name_and_type(),
          "Invalid constant pool index %u in class file %s",
          name_and_type_ref_index, CHECK);
        break;
      }
      case JVM_CONSTANT_String: {
        ShouldNotReachHere();     // Only JVM_CONSTANT_StringIndex should be present
        break;
      }
      case JVM_CONSTANT_Integer:
        break;
      case JVM_CONSTANT_Float:
        break;
      case JVM_CONSTANT_Long:
      case JVM_CONSTANT_Double: {
        index++;
        check_property(
          (index < length && cp->tag_at(index).is_invalid()),
          "Improper constant pool long/double index %u in class file %s",
          index, CHECK);
        break;
      }
      case JVM_CONSTANT_NameAndType: {
        if (!_need_verify) break;
        const int name_ref_index = cp->name_ref_index_at(index);
        const int signature_ref_index = cp->signature_ref_index_at(index);
        check_property(valid_symbol_at(name_ref_index),
          "Invalid constant pool index %u in class file %s",
          name_ref_index, CHECK);
        check_property(valid_symbol_at(signature_ref_index),
          "Invalid constant pool index %u in class file %s",
          signature_ref_index, CHECK);
        break;
      }
      case JVM_CONSTANT_Utf8:
        break;
      case JVM_CONSTANT_UnresolvedClass:         // fall-through
      case JVM_CONSTANT_UnresolvedClassInError: {
        ShouldNotReachHere();     // Only JVM_CONSTANT_ClassIndex should be present
        break;
      }
      case JVM_CONSTANT_ClassIndex: {
        const int class_index = cp->klass_index_at(index);
        check_property(valid_symbol_at(class_index),
          "Invalid constant pool index %u in class file %s",
          class_index, CHECK);
        cp->unresolved_klass_at_put(index, cp->symbol_at(class_index));
        break;
      }
      case JVM_CONSTANT_StringIndex: {
        const int string_index = cp->string_index_at(index);
        check_property(valid_symbol_at(string_index),
          "Invalid constant pool index %u in class file %s",
          string_index, CHECK);
        Symbol* const sym = cp->symbol_at(string_index);
        cp->unresolved_string_at_put(index, sym);
        break;
      }
      case JVM_CONSTANT_MethodHandle: {
        const int ref_index = cp->method_handle_index_at(index);
        check_property(valid_cp_range(ref_index, length),
          "Invalid constant pool index %u in class file %s",
          ref_index, CHECK);
        const constantTag tag = cp->tag_at(ref_index);
        const int ref_kind = cp->method_handle_ref_kind_at(index);

        switch (ref_kind) {
          case JVM_REF_getField:
          case JVM_REF_getStatic:
          case JVM_REF_putField:
          case JVM_REF_putStatic: {
            check_property(
              tag.is_field(),
              "Invalid constant pool index %u in class file %s (not a field)",
              ref_index, CHECK);
            break;
          }
          case JVM_REF_invokeVirtual:
          case JVM_REF_newInvokeSpecial: {
            check_property(
              tag.is_method(),
              "Invalid constant pool index %u in class file %s (not a method)",
              ref_index, CHECK);
            break;
          }
          case JVM_REF_invokeStatic:
          case JVM_REF_invokeSpecial: {
            check_property(
              tag.is_method() ||
              ((_major_version >= JAVA_8_VERSION) && tag.is_interface_method()),
              "Invalid constant pool index %u in class file %s (not a method)",
              ref_index, CHECK);
            break;
          }
          case JVM_REF_invokeInterface: {
            check_property(
              tag.is_interface_method(),
              "Invalid constant pool index %u in class file %s (not an interface method)",
              ref_index, CHECK);
            break;
          }
          default: {
            classfile_parse_error(
              "Bad method handle kind at constant pool index %u in class file %s",
              index, CHECK);
          }
        } // switch(refkind)
        // Keep the ref_index unchanged.  It will be indirected at link-time.
        break;
      } // case MethodHandle
      case JVM_CONSTANT_MethodType: {
        const int ref_index = cp->method_type_index_at(index);
        check_property(valid_symbol_at(ref_index),
          "Invalid constant pool index %u in class file %s",
          ref_index, CHECK);
        break;
      }
      case JVM_CONSTANT_InvokeDynamic: {
        const int name_and_type_ref_index =
          cp->invoke_dynamic_name_and_type_ref_index_at(index);

        check_property(valid_cp_range(name_and_type_ref_index, length) &&
          cp->tag_at(name_and_type_ref_index).is_name_and_type(),
          "Invalid constant pool index %u in class file %s",
          name_and_type_ref_index, CHECK);
        // bootstrap specifier index must be checked later,
        // when BootstrapMethods attr is available
        break;
      }
      default: {
        fatal("bad constant pool tag value %u", cp->tag_at(index).value());
        ShouldNotReachHere();
        break;
      }
    } // switch(tag)
  } // end of for

  if (_cp_patches != NULL) {
    // need to treat this_class specially...
    int this_class_index;
    {
      stream->guarantee_more(8, CHECK);  // flags, this_class, super_class, infs_len
      const u1* const mark = stream->current();
      stream->skip_u2_fast(1); // skip flags
      this_class_index = stream->get_u2_fast();
      stream->set_current(mark);  // revert to mark
    }

    for (index = 1; index < length; index++) {          // Index 0 is unused
      if (has_cp_patch_at(index)) {
        guarantee_property(index != this_class_index,
          "Illegal constant pool patch to self at %d in class file %s",
          index, CHECK);
        patch_constant_pool(cp, index, cp_patch_at(index), CHECK);
      }
    }
  }

  if (!_need_verify) {
    return;
  }

  // second verification pass - checks the strings are of the right format.
  // but not yet to the other entries
  for (index = 1; index < length; index++) {
    const jbyte tag = cp->tag_at(index).value();
    switch (tag) {
      case JVM_CONSTANT_UnresolvedClass: {
        const Symbol* const class_name = cp->klass_name_at(index);
        // check the name, even if _cp_patches will overwrite it
        verify_legal_class_name(class_name, CHECK);
        break;
      }
      case JVM_CONSTANT_NameAndType: {
        if (_need_verify && _major_version >= JAVA_7_VERSION) {
          const int sig_index = cp->signature_ref_index_at(index);
          const int name_index = cp->name_ref_index_at(index);
          const Symbol* const name = cp->symbol_at(name_index);
          const Symbol* const sig = cp->symbol_at(sig_index);
          guarantee_property(sig->utf8_length() != 0,
            "Illegal zero length constant pool entry at %d in class %s",
            sig_index, CHECK);
          if (sig->byte_at(0) == JVM_SIGNATURE_FUNC) {
            verify_legal_method_signature(name, sig, CHECK);
          } else {
            verify_legal_field_signature(name, sig, CHECK);
          }
        }
        break;
      }
      case JVM_CONSTANT_InvokeDynamic:
      case JVM_CONSTANT_Fieldref:
      case JVM_CONSTANT_Methodref:
      case JVM_CONSTANT_InterfaceMethodref: {
        const int name_and_type_ref_index =
          cp->name_and_type_ref_index_at(index);
        // already verified to be utf8
        const int name_ref_index =
          cp->name_ref_index_at(name_and_type_ref_index);
        // already verified to be utf8
        const int signature_ref_index =
          cp->signature_ref_index_at(name_and_type_ref_index);
        const Symbol* const name = cp->symbol_at(name_ref_index);
        const Symbol* const signature = cp->symbol_at(signature_ref_index);
        if (tag == JVM_CONSTANT_Fieldref) {
          verify_legal_field_name(name, CHECK);
          if (_need_verify && _major_version >= JAVA_7_VERSION) {
            // Signature is verified above, when iterating NameAndType_info.
            // Need only to be sure it's non-zero length and the right type.
            if (signature->utf8_length() == 0 ||
                signature->byte_at(0) == JVM_SIGNATURE_FUNC) {
              throwIllegalSignature(
                "Field", name, signature, CHECK);
            }
          } else {
            verify_legal_field_signature(name, signature, CHECK);
          }
        } else {
          verify_legal_method_name(name, CHECK);
          if (_need_verify && _major_version >= JAVA_7_VERSION) {
            // Signature is verified above, when iterating NameAndType_info.
            // Need only to be sure it's non-zero length and the right type.
            if (signature->utf8_length() == 0 ||
                signature->byte_at(0) != JVM_SIGNATURE_FUNC) {
              throwIllegalSignature(
                "Method", name, signature, CHECK);
            }
          } else {
            verify_legal_method_signature(name, signature, CHECK);
          }
          if (tag == JVM_CONSTANT_Methodref) {
            // 4509014: If a class method name begins with '<', it must be "<init>".
            assert(name != NULL, "method name in constant pool is null");
            const unsigned int name_len = name->utf8_length();
            if (name_len != 0 && name->byte_at(0) == '<') {
              if (name != vmSymbols::object_initializer_name()) {
                classfile_parse_error(
                  "Bad method name at constant pool index %u in class file %s",
                  name_ref_index, CHECK);
              }
            }
          }
        }
        break;
      }
      case JVM_CONSTANT_MethodHandle: {
        const int ref_index = cp->method_handle_index_at(index);
        const int ref_kind = cp->method_handle_ref_kind_at(index);
        switch (ref_kind) {
          case JVM_REF_invokeVirtual:
          case JVM_REF_invokeStatic:
          case JVM_REF_invokeSpecial:
          case JVM_REF_newInvokeSpecial: {
            const int name_and_type_ref_index =
              cp->name_and_type_ref_index_at(ref_index);
            const int name_ref_index =
              cp->name_ref_index_at(name_and_type_ref_index);
            const Symbol* const name = cp->symbol_at(name_ref_index);
            if (ref_kind == JVM_REF_newInvokeSpecial) {
              if (name != vmSymbols::object_initializer_name()) {
                classfile_parse_error(
                  "Bad constructor name at constant pool index %u in class file %s",
                    name_ref_index, CHECK);
              }
            } else {
              if (name == vmSymbols::object_initializer_name()) {
                classfile_parse_error(
                  "Bad method name at constant pool index %u in class file %s",
                  name_ref_index, CHECK);
              }
            }
            break;
          }
          // Other ref_kinds are already fully checked in previous pass.
        } // switch(ref_kind)
        break;
      }
      case JVM_CONSTANT_MethodType: {
        const Symbol* const no_name = vmSymbols::type_name(); // place holder
        const Symbol* const signature = cp->method_type_signature_at(index);
        verify_legal_method_signature(no_name, signature, CHECK);
        break;
      }
      case JVM_CONSTANT_Utf8: {
        assert(cp->symbol_at(index)->refcount() != 0, "count corrupted");
      }
    }  // switch(tag)
  }  // end of for
}

void ClassFileParser::patch_constant_pool(ConstantPool* cp,
                                          int index,
                                          Handle patch,
                                          TRAPS) {
  assert(cp != NULL, "invariant");

  BasicType patch_type = T_VOID;

  switch (cp->tag_at(index).value()) {

    case JVM_CONSTANT_UnresolvedClass: {
      // Patching a class means pre-resolving it.
      // The name in the constant pool is ignored.
      if (java_lang_Class::is_instance(patch())) {
        guarantee_property(!java_lang_Class::is_primitive(patch()),
                           "Illegal class patch at %d in class file %s",
                           index, CHECK);
        cp->klass_at_put(index, java_lang_Class::as_Klass(patch()));
      } else {
        guarantee_property(java_lang_String::is_instance(patch()),
                           "Illegal class patch at %d in class file %s",
                           index, CHECK);
        Symbol* const name = java_lang_String::as_symbol(patch(), CHECK);
        cp->unresolved_klass_at_put(index, name);
      }
      break;
    }

    case JVM_CONSTANT_String: {
      // skip this patch and don't clear it.  Needs the oop array for resolved
      // references to be created first.
      return;
    }
    case JVM_CONSTANT_Integer: patch_type = T_INT;    goto patch_prim;
    case JVM_CONSTANT_Float:   patch_type = T_FLOAT;  goto patch_prim;
    case JVM_CONSTANT_Long:    patch_type = T_LONG;   goto patch_prim;
    case JVM_CONSTANT_Double:  patch_type = T_DOUBLE; goto patch_prim;
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
    } // end patch_prim label
    break;

    default: {
      // %%% TODO: put method handles into CONSTANT_InterfaceMethodref, etc.
      guarantee_property(!has_cp_patch_at(index),
                         "Illegal unexpected patch at %d in class file %s",
                         index, CHECK);
      return;
    }
  } // end of switch(tag)

  // On fall-through, mark the patch as used.
  clear_cp_patch_at(index);
}
class NameSigHash: public ResourceObj {
 public:
  const Symbol*       _name;       // name
  const Symbol*       _sig;        // signature
  NameSigHash*  _next;             // Next entry in hash table
};

static const int HASH_ROW_SIZE = 256;

static unsigned int hash(const Symbol* name, const Symbol* sig) {
  unsigned int raw_hash = 0;
  raw_hash += ((unsigned int)(uintptr_t)name) >> (LogHeapWordSize + 2);
  raw_hash += ((unsigned int)(uintptr_t)sig) >> LogHeapWordSize;

  return (raw_hash + (unsigned int)(uintptr_t)name) % HASH_ROW_SIZE;
}


static void initialize_hashtable(NameSigHash** table) {
  memset((void*)table, 0, sizeof(NameSigHash*) * HASH_ROW_SIZE);
}
// Return false if the name/sig combination is found in table.
// Return true if no duplicate is found. And name/sig is added as a new entry in table.
// The old format checker uses heap sort to find duplicates.
// NOTE: caller should guarantee that GC doesn't happen during the life cycle
// of table since we don't expect Symbol*'s to move.
static bool put_after_lookup(const Symbol* name, const Symbol* sig, NameSigHash** table) {
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

// Side-effects: populates the _local_interfaces field
void ClassFileParser::parse_interfaces(const ClassFileStream* const stream,
                                       const int itfs_len,
                                       ConstantPool* const cp,
                                       bool* const has_default_methods,
                                       TRAPS) {
  assert(stream != NULL, "invariant");
  assert(cp != NULL, "invariant");
  assert(has_default_methods != NULL, "invariant");

  if (itfs_len == 0) {
    _local_interfaces = Universe::the_empty_klass_array();
  } else {
    assert(itfs_len > 0, "only called for len>0");
    _local_interfaces = MetadataFactory::new_array<Klass*>(_loader_data, itfs_len, NULL, CHECK);

    int index;
    for (index = 0; index < itfs_len; index++) {
      const u2 interface_index = stream->get_u2(CHECK);
      KlassHandle interf;
      check_property(
        valid_klass_reference_at(interface_index),
        "Interface name has bad constant pool index %u in class file %s",
        interface_index, CHECK);
      if (cp->tag_at(interface_index).is_klass()) {
        interf = KlassHandle(THREAD, cp->resolved_klass_at(interface_index));
      } else {
        Symbol* const unresolved_klass  = cp->klass_name_at(interface_index);

        // Don't need to check legal name because it's checked when parsing constant pool.
        // But need to make sure it's not an array type.
        guarantee_property(unresolved_klass->byte_at(0) != JVM_SIGNATURE_ARRAY,
                           "Bad interface name in class file %s", CHECK);

        // Call resolve_super so classcircularity is checked
        const Klass* const k =
          SystemDictionary::resolve_super_or_fail(_class_name,
                                                  unresolved_klass,
                                                  _loader_data->class_loader(),
                                                  _protection_domain,
                                                  false,
                                                  CHECK);
        interf = KlassHandle(THREAD, k);
      }

      if (!interf()->is_interface()) {
        THROW_MSG(vmSymbols::java_lang_IncompatibleClassChangeError(),
                   "Implementing class");
      }

      if (InstanceKlass::cast(interf())->has_default_methods()) {
        *has_default_methods = true;
      }
      _local_interfaces->at_put(index, interf());
    }

    if (!_need_verify || itfs_len <= 1) {
      return;
    }

    // Check if there's any duplicates in interfaces
    ResourceMark rm(THREAD);
    NameSigHash** interface_names = NEW_RESOURCE_ARRAY_IN_THREAD(THREAD,
                                                                 NameSigHash*,
                                                                 HASH_ROW_SIZE);
    initialize_hashtable(interface_names);
    bool dup = false;
    {
      debug_only(No_Safepoint_Verifier nsv;)
      for (index = 0; index < itfs_len; index++) {
        const Klass* const k = _local_interfaces->at(index);
        const Symbol* const name = InstanceKlass::cast(k)->name();
        // If no duplicates, add (name, NULL) in hashtable interface_names.
        if (!put_after_lookup(name, NULL, interface_names)) {
          dup = true;
          break;
        }
      }
    }
    if (dup) {
      classfile_parse_error("Duplicate interface name in class file %s", CHECK);
    }
  }
}

void ClassFileParser::verify_constantvalue(const ConstantPool* const cp,
                                           int constantvalue_index,
                                           int signature_index,
                                           TRAPS) const {
  // Make sure the constant pool entry is of a type appropriate to this field
  guarantee_property(
    (constantvalue_index > 0 &&
      constantvalue_index < cp->length()),
    "Bad initial value index %u in ConstantValue attribute in class file %s",
    constantvalue_index, CHECK);

  const constantTag value_type = cp->tag_at(constantvalue_index);
  switch(cp->basic_type_for_signature_at(signature_index)) {
    case T_LONG: {
      guarantee_property(value_type.is_long(),
                         "Inconsistent constant value type in class file %s",
                         CHECK);
      break;
    }
    case T_FLOAT: {
      guarantee_property(value_type.is_float(),
                         "Inconsistent constant value type in class file %s",
                         CHECK);
      break;
    }
    case T_DOUBLE: {
      guarantee_property(value_type.is_double(),
                         "Inconsistent constant value type in class file %s",
                         CHECK);
      break;
    }
    case T_BYTE:
    case T_CHAR:
    case T_SHORT:
    case T_BOOLEAN:
    case T_INT: {
      guarantee_property(value_type.is_int(),
                         "Inconsistent constant value type in class file %s",
                         CHECK);
      break;
    }
    case T_OBJECT: {
      guarantee_property((cp->symbol_at(signature_index)->equals("Ljava/lang/String;")
                         && value_type.is_string()),
                         "Bad string initial value in class file %s",
                         CHECK);
      break;
    }
    default: {
      classfile_parse_error("Unable to set initial value %u in class file %s",
                             constantvalue_index,
                             CHECK);
    }
  }
}

class AnnotationCollector : public ResourceObj{
public:
  enum Location { _in_field, _in_method, _in_class };
  enum ID {
    _unknown = 0,
    _method_CallerSensitive,
    _method_ForceInline,
    _method_DontInline,
    _method_InjectedProfile,
    _method_LambdaForm_Compiled,
    _method_LambdaForm_Hidden,
    _method_HotSpotIntrinsicCandidate,
    _jdk_internal_vm_annotation_Contended,
    _field_Stable,
    _jdk_internal_vm_annotation_ReservedStackAccess,
    _annotation_LIMIT
  };
  const Location _location;
  int _annotations_present;
  u2 _contended_group;

  AnnotationCollector(Location location)
    : _location(location), _annotations_present(0)
  {
    assert((int)_annotation_LIMIT <= (int)sizeof(_annotations_present) * BitsPerByte, "");
  }
  // If this annotation name has an ID, report it (or _none).
  ID annotation_index(const ClassLoaderData* loader_data, const Symbol* name);
  // Set the annotation name:
  void set_annotation(ID id) {
    assert((int)id >= 0 && (int)id < (int)_annotation_LIMIT, "oob");
    _annotations_present |= nth_bit((int)id);
  }

  void remove_annotation(ID id) {
    assert((int)id >= 0 && (int)id < (int)_annotation_LIMIT, "oob");
    _annotations_present &= ~nth_bit((int)id);
  }

  // Report if the annotation is present.
  bool has_any_annotations() const { return _annotations_present != 0; }
  bool has_annotation(ID id) const { return (nth_bit((int)id) & _annotations_present) != 0; }

  void set_contended_group(u2 group) { _contended_group = group; }
  u2 contended_group() const { return _contended_group; }

  bool is_contended() const { return has_annotation(_jdk_internal_vm_annotation_Contended); }

  void set_stable(bool stable) { set_annotation(_field_Stable); }
  bool is_stable() const { return has_annotation(_field_Stable); }
};

// This class also doubles as a holder for metadata cleanup.
class ClassFileParser::FieldAnnotationCollector : public AnnotationCollector {
private:
  ClassLoaderData* _loader_data;
  AnnotationArray* _field_annotations;
  AnnotationArray* _field_type_annotations;
public:
  FieldAnnotationCollector(ClassLoaderData* loader_data) :
    AnnotationCollector(_in_field),
    _loader_data(loader_data),
    _field_annotations(NULL),
    _field_type_annotations(NULL) {}
  ~FieldAnnotationCollector();
  void apply_to(FieldInfo* f);
  AnnotationArray* field_annotations()      { return _field_annotations; }
  AnnotationArray* field_type_annotations() { return _field_type_annotations; }

  void set_field_annotations(AnnotationArray* a)      { _field_annotations = a; }
  void set_field_type_annotations(AnnotationArray* a) { _field_type_annotations = a; }
};

class MethodAnnotationCollector : public AnnotationCollector{
public:
  MethodAnnotationCollector() : AnnotationCollector(_in_method) { }
  void apply_to(methodHandle m);
};

class ClassFileParser::ClassAnnotationCollector : public AnnotationCollector{
public:
  ClassAnnotationCollector() : AnnotationCollector(_in_class) { }
  void apply_to(InstanceKlass* ik);
};


static int skip_annotation_value(const u1*, int, int); // fwd decl

// Skip an annotation.  Return >=limit if there is any problem.
static int skip_annotation(const u1* buffer, int limit, int index) {
  assert(buffer != NULL, "invariant");
  // annotation := atype:u2 do(nmem:u2) {member:u2 value}
  // value := switch (tag:u1) { ... }
  index += 2;  // skip atype
  if ((index += 2) >= limit)  return limit;  // read nmem
  int nmem = Bytes::get_Java_u2((address)buffer + index - 2);
  while (--nmem >= 0 && index < limit) {
    index += 2; // skip member
    index = skip_annotation_value(buffer, limit, index);
  }
  return index;
}

// Skip an annotation value.  Return >=limit if there is any problem.
static int skip_annotation_value(const u1* buffer, int limit, int index) {
  assert(buffer != NULL, "invariant");

  // value := switch (tag:u1) {
  //   case B, C, I, S, Z, D, F, J, c: con:u2;
  //   case e: e_class:u2 e_name:u2;
  //   case s: s_con:u2;
  //   case [: do(nval:u2) {value};
  //   case @: annotation;
  //   case s: s_con:u2;
  // }
  if ((index += 1) >= limit)  return limit;  // read tag
  const u1 tag = buffer[index - 1];
  switch (tag) {
    case 'B':
    case 'C':
    case 'I':
    case 'S':
    case 'Z':
    case 'D':
    case 'F':
    case 'J':
    case 'c':
    case 's':
      index += 2;  // skip con or s_con
      break;
    case 'e':
      index += 4;  // skip e_class, e_name
      break;
    case '[':
    {
      if ((index += 2) >= limit)  return limit;  // read nval
      int nval = Bytes::get_Java_u2((address)buffer + index - 2);
      while (--nval >= 0 && index < limit) {
        index = skip_annotation_value(buffer, limit, index);
      }
    }
    break;
    case '@':
      index = skip_annotation(buffer, limit, index);
      break;
    default:
      return limit;  //  bad tag byte
  }
  return index;
}

// Sift through annotations, looking for those significant to the VM:
static void parse_annotations(const ConstantPool* const cp,
                              const u1* buffer, int limit,
                              AnnotationCollector* coll,
                              ClassLoaderData* loader_data,
                              TRAPS) {

  assert(cp != NULL, "invariant");
  assert(buffer != NULL, "invariant");
  assert(coll != NULL, "invariant");
  assert(loader_data != NULL, "invariant");

  // annotations := do(nann:u2) {annotation}
  int index = 0;
  if ((index += 2) >= limit)  return;  // read nann
  int nann = Bytes::get_Java_u2((address)buffer + index - 2);
  enum {  // initial annotation layout
    atype_off = 0,      // utf8 such as 'Ljava/lang/annotation/Retention;'
    count_off = 2,      // u2   such as 1 (one value)
    member_off = 4,     // utf8 such as 'value'
    tag_off = 6,        // u1   such as 'c' (type) or 'e' (enum)
    e_tag_val = 'e',
    e_type_off = 7,   // utf8 such as 'Ljava/lang/annotation/RetentionPolicy;'
    e_con_off = 9,    // utf8 payload, such as 'SOURCE', 'CLASS', 'RUNTIME'
    e_size = 11,     // end of 'e' annotation
    c_tag_val = 'c',    // payload is type
    c_con_off = 7,    // utf8 payload, such as 'I'
    c_size = 9,       // end of 'c' annotation
    s_tag_val = 's',    // payload is String
    s_con_off = 7,    // utf8 payload, such as 'Ljava/lang/String;'
    s_size = 9,
    min_size = 6        // smallest possible size (zero members)
  };
  while ((--nann) >= 0 && (index - 2 + min_size <= limit)) {
    int index0 = index;
    index = skip_annotation(buffer, limit, index);
    const u1* const abase = buffer + index0;
    const int atype = Bytes::get_Java_u2((address)abase + atype_off);
    const int count = Bytes::get_Java_u2((address)abase + count_off);
    const Symbol* const aname = check_symbol_at(cp, atype);
    if (aname == NULL)  break;  // invalid annotation name
    const Symbol* member = NULL;
    if (count >= 1) {
      const int member_index = Bytes::get_Java_u2((address)abase + member_off);
      member = check_symbol_at(cp, member_index);
      if (member == NULL)  break;  // invalid member name
    }

    // Here is where parsing particular annotations will take place.
    AnnotationCollector::ID id = coll->annotation_index(loader_data, aname);
    if (AnnotationCollector::_unknown == id)  continue;
    coll->set_annotation(id);

    if (AnnotationCollector::_jdk_internal_vm_annotation_Contended == id) {
      // @Contended can optionally specify the contention group.
      //
      // Contended group defines the equivalence class over the fields:
      // the fields within the same contended group are not treated distinct.
      // The only exception is default group, which does not incur the
      // equivalence. Naturally, contention group for classes is meaningless.
      //
      // While the contention group is specified as String, annotation
      // values are already interned, and we might as well use the constant
      // pool index as the group tag.
      //
      u2 group_index = 0; // default contended group
      if (count == 1
        && s_size == (index - index0)  // match size
        && s_tag_val == *(abase + tag_off)
        && member == vmSymbols::value_name()) {
        group_index = Bytes::get_Java_u2((address)abase + s_con_off);
        if (cp->symbol_at(group_index)->utf8_length() == 0) {
          group_index = 0; // default contended group
        }
      }
      coll->set_contended_group(group_index);
    }
  }
}


// Parse attributes for a field.
void ClassFileParser::parse_field_attributes(const ClassFileStream* const cfs,
                                             u2 attributes_count,
                                             bool is_static, u2 signature_index,
                                             u2* const constantvalue_index_addr,
                                             bool* const is_synthetic_addr,
                                             u2* const generic_signature_index_addr,
                                             ClassFileParser::FieldAnnotationCollector* parsed_annotations,
                                             TRAPS) {
  assert(cfs != NULL, "invariant");
  assert(constantvalue_index_addr != NULL, "invariant");
  assert(is_synthetic_addr != NULL, "invariant");
  assert(generic_signature_index_addr != NULL, "invariant");
  assert(parsed_annotations != NULL, "invariant");
  assert(attributes_count > 0, "attributes_count should be greater than 0");

  u2 constantvalue_index = 0;
  u2 generic_signature_index = 0;
  bool is_synthetic = false;
  const u1* runtime_visible_annotations = NULL;
  int runtime_visible_annotations_length = 0;
  const u1* runtime_invisible_annotations = NULL;
  int runtime_invisible_annotations_length = 0;
  const u1* runtime_visible_type_annotations = NULL;
  int runtime_visible_type_annotations_length = 0;
  const u1* runtime_invisible_type_annotations = NULL;
  int runtime_invisible_type_annotations_length = 0;
  bool runtime_invisible_annotations_exists = false;
  bool runtime_invisible_type_annotations_exists = false;
  const ConstantPool* const cp = _cp;

  while (attributes_count--) {
    cfs->guarantee_more(6, CHECK);  // attribute_name_index, attribute_length
    const u2 attribute_name_index = cfs->get_u2_fast();
    const u4 attribute_length = cfs->get_u4_fast();
    check_property(valid_symbol_at(attribute_name_index),
                   "Invalid field attribute index %u in class file %s",
                   attribute_name_index,
                   CHECK);

    const Symbol* const attribute_name = cp->symbol_at(attribute_name_index);
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
        verify_constantvalue(cp, constantvalue_index, signature_index, CHECK);
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
        generic_signature_index = parse_generic_signature_attribute(cfs, CHECK);
      } else if (attribute_name == vmSymbols::tag_runtime_visible_annotations()) {
        if (runtime_visible_annotations != NULL) {
          classfile_parse_error(
            "Multiple RuntimeVisibleAnnotations attributes for field in class file %s", CHECK);
        }
        runtime_visible_annotations_length = attribute_length;
        runtime_visible_annotations = cfs->get_u1_buffer();
        assert(runtime_visible_annotations != NULL, "null visible annotations");
        parse_annotations(cp,
                          runtime_visible_annotations,
                          runtime_visible_annotations_length,
                          parsed_annotations,
                          _loader_data,
                          CHECK);
        cfs->skip_u1(runtime_visible_annotations_length, CHECK);
      } else if (attribute_name == vmSymbols::tag_runtime_invisible_annotations()) {
        if (runtime_invisible_annotations_exists) {
          classfile_parse_error(
            "Multiple RuntimeInvisibleAnnotations attributes for field in class file %s", CHECK);
        }
        runtime_invisible_annotations_exists = true;
        if (PreserveAllAnnotations) {
          runtime_invisible_annotations_length = attribute_length;
          runtime_invisible_annotations = cfs->get_u1_buffer();
          assert(runtime_invisible_annotations != NULL, "null invisible annotations");
        }
        cfs->skip_u1(attribute_length, CHECK);
      } else if (attribute_name == vmSymbols::tag_runtime_visible_type_annotations()) {
        if (runtime_visible_type_annotations != NULL) {
          classfile_parse_error(
            "Multiple RuntimeVisibleTypeAnnotations attributes for field in class file %s", CHECK);
        }
        runtime_visible_type_annotations_length = attribute_length;
        runtime_visible_type_annotations = cfs->get_u1_buffer();
        assert(runtime_visible_type_annotations != NULL, "null visible type annotations");
        cfs->skip_u1(runtime_visible_type_annotations_length, CHECK);
      } else if (attribute_name == vmSymbols::tag_runtime_invisible_type_annotations()) {
        if (runtime_invisible_type_annotations_exists) {
          classfile_parse_error(
            "Multiple RuntimeInvisibleTypeAnnotations attributes for field in class file %s", CHECK);
        } else {
          runtime_invisible_type_annotations_exists = true;
        }
        if (PreserveAllAnnotations) {
          runtime_invisible_type_annotations_length = attribute_length;
          runtime_invisible_type_annotations = cfs->get_u1_buffer();
          assert(runtime_invisible_type_annotations != NULL, "null invisible type annotations");
        }
        cfs->skip_u1(attribute_length, CHECK);
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
  AnnotationArray* a = assemble_annotations(runtime_visible_annotations,
                                            runtime_visible_annotations_length,
                                            runtime_invisible_annotations,
                                            runtime_invisible_annotations_length,
                                            CHECK);
  parsed_annotations->set_field_annotations(a);
  a = assemble_annotations(runtime_visible_type_annotations,
                           runtime_visible_type_annotations_length,
                           runtime_invisible_type_annotations,
                           runtime_invisible_type_annotations_length,
                           CHECK);
  parsed_annotations->set_field_type_annotations(a);
  return;
}


// Field allocation types. Used for computing field offsets.

enum FieldAllocationType {
  STATIC_OOP,           // Oops
  STATIC_BYTE,          // Boolean, Byte, char
  STATIC_SHORT,         // shorts
  STATIC_WORD,          // ints
  STATIC_DOUBLE,        // aligned long or double
  NONSTATIC_OOP,
  NONSTATIC_BYTE,
  NONSTATIC_SHORT,
  NONSTATIC_WORD,
  NONSTATIC_DOUBLE,
  MAX_FIELD_ALLOCATION_TYPE,
  BAD_ALLOCATION_TYPE = -1
};

static FieldAllocationType _basic_type_to_atype[2 * (T_CONFLICT + 1)] = {
  BAD_ALLOCATION_TYPE, // 0
  BAD_ALLOCATION_TYPE, // 1
  BAD_ALLOCATION_TYPE, // 2
  BAD_ALLOCATION_TYPE, // 3
  NONSTATIC_BYTE ,     // T_BOOLEAN     =  4,
  NONSTATIC_SHORT,     // T_CHAR        =  5,
  NONSTATIC_WORD,      // T_FLOAT       =  6,
  NONSTATIC_DOUBLE,    // T_DOUBLE      =  7,
  NONSTATIC_BYTE,      // T_BYTE        =  8,
  NONSTATIC_SHORT,     // T_SHORT       =  9,
  NONSTATIC_WORD,      // T_INT         = 10,
  NONSTATIC_DOUBLE,    // T_LONG        = 11,
  NONSTATIC_OOP,       // T_OBJECT      = 12,
  NONSTATIC_OOP,       // T_ARRAY       = 13,
  BAD_ALLOCATION_TYPE, // T_VOID        = 14,
  BAD_ALLOCATION_TYPE, // T_ADDRESS     = 15,
  BAD_ALLOCATION_TYPE, // T_NARROWOOP   = 16,
  BAD_ALLOCATION_TYPE, // T_METADATA    = 17,
  BAD_ALLOCATION_TYPE, // T_NARROWKLASS = 18,
  BAD_ALLOCATION_TYPE, // T_CONFLICT    = 19,
  BAD_ALLOCATION_TYPE, // 0
  BAD_ALLOCATION_TYPE, // 1
  BAD_ALLOCATION_TYPE, // 2
  BAD_ALLOCATION_TYPE, // 3
  STATIC_BYTE ,        // T_BOOLEAN     =  4,
  STATIC_SHORT,        // T_CHAR        =  5,
  STATIC_WORD,         // T_FLOAT       =  6,
  STATIC_DOUBLE,       // T_DOUBLE      =  7,
  STATIC_BYTE,         // T_BYTE        =  8,
  STATIC_SHORT,        // T_SHORT       =  9,
  STATIC_WORD,         // T_INT         = 10,
  STATIC_DOUBLE,       // T_LONG        = 11,
  STATIC_OOP,          // T_OBJECT      = 12,
  STATIC_OOP,          // T_ARRAY       = 13,
  BAD_ALLOCATION_TYPE, // T_VOID        = 14,
  BAD_ALLOCATION_TYPE, // T_ADDRESS     = 15,
  BAD_ALLOCATION_TYPE, // T_NARROWOOP   = 16,
  BAD_ALLOCATION_TYPE, // T_METADATA    = 17,
  BAD_ALLOCATION_TYPE, // T_NARROWKLASS = 18,
  BAD_ALLOCATION_TYPE, // T_CONFLICT    = 19,
};

static FieldAllocationType basic_type_to_atype(bool is_static, BasicType type) {
  assert(type >= T_BOOLEAN && type < T_VOID, "only allowable values");
  FieldAllocationType result = _basic_type_to_atype[type + (is_static ? (T_CONFLICT + 1) : 0)];
  assert(result != BAD_ALLOCATION_TYPE, "bad type");
  return result;
}

class ClassFileParser::FieldAllocationCount : public ResourceObj {
 public:
  u2 count[MAX_FIELD_ALLOCATION_TYPE];

  FieldAllocationCount() {
    for (int i = 0; i < MAX_FIELD_ALLOCATION_TYPE; i++) {
      count[i] = 0;
    }
  }

  FieldAllocationType update(bool is_static, BasicType type) {
    FieldAllocationType atype = basic_type_to_atype(is_static, type);
    // Make sure there is no overflow with injected fields.
    assert(count[atype] < 0xFFFF, "More than 65535 fields");
    count[atype]++;
    return atype;
  }
};

// Side-effects: populates the _fields, _fields_annotations,
// _fields_type_annotations fields
void ClassFileParser::parse_fields(const ClassFileStream* const cfs,
                                   bool is_interface,
                                   FieldAllocationCount* const fac,
                                   ConstantPool* cp,
                                   const int cp_size,
                                   u2* const java_fields_count_ptr,
                                   TRAPS) {

  assert(cfs != NULL, "invariant");
  assert(fac != NULL, "invariant");
  assert(cp != NULL, "invariant");
  assert(java_fields_count_ptr != NULL, "invariant");

  assert(NULL == _fields, "invariant");
  assert(NULL == _fields_annotations, "invariant");
  assert(NULL == _fields_type_annotations, "invariant");

  cfs->guarantee_more(2, CHECK);  // length
  const u2 length = cfs->get_u2_fast();
  *java_fields_count_ptr = length;

  int num_injected = 0;
  const InjectedField* const injected = JavaClasses::get_injected(_class_name,
                                                                  &num_injected);
  const int total_fields = length + num_injected;

  // The field array starts with tuples of shorts
  // [access, name index, sig index, initial value index, byte offset].
  // A generic signature slot only exists for field with generic
  // signature attribute. And the access flag is set with
  // JVM_ACC_FIELD_HAS_GENERIC_SIGNATURE for that field. The generic
  // signature slots are at the end of the field array and after all
  // other fields data.
  //
  //   f1: [access, name index, sig index, initial value index, low_offset, high_offset]
  //   f2: [access, name index, sig index, initial value index, low_offset, high_offset]
  //       ...
  //   fn: [access, name index, sig index, initial value index, low_offset, high_offset]
  //       [generic signature index]
  //       [generic signature index]
  //       ...
  //
  // Allocate a temporary resource array for field data. For each field,
  // a slot is reserved in the temporary array for the generic signature
  // index. After parsing all fields, the data are copied to a permanent
  // array and any unused slots will be discarded.
  ResourceMark rm(THREAD);
  u2* const fa = NEW_RESOURCE_ARRAY_IN_THREAD(THREAD,
                                              u2,
                                              total_fields * (FieldInfo::field_slots + 1));

  // The generic signature slots start after all other fields' data.
  int generic_signature_slot = total_fields * FieldInfo::field_slots;
  int num_generic_signature = 0;
  for (int n = 0; n < length; n++) {
    // access_flags, name_index, descriptor_index, attributes_count
    cfs->guarantee_more(8, CHECK);

    AccessFlags access_flags;
    const jint flags = cfs->get_u2_fast() & JVM_RECOGNIZED_FIELD_MODIFIERS;
    verify_legal_field_modifiers(flags, is_interface, CHECK);
    access_flags.set_flags(flags);

    const u2 name_index = cfs->get_u2_fast();
    check_property(valid_symbol_at(name_index),
      "Invalid constant pool index %u for field name in class file %s",
      name_index, CHECK);
    const Symbol* const name = cp->symbol_at(name_index);
    verify_legal_field_name(name, CHECK);

    const u2 signature_index = cfs->get_u2_fast();
    check_property(valid_symbol_at(signature_index),
      "Invalid constant pool index %u for field signature in class file %s",
      signature_index, CHECK);
    const Symbol* const sig = cp->symbol_at(signature_index);
    verify_legal_field_signature(name, sig, CHECK);

    u2 constantvalue_index = 0;
    bool is_synthetic = false;
    u2 generic_signature_index = 0;
    const bool is_static = access_flags.is_static();
    FieldAnnotationCollector parsed_annotations(_loader_data);

    const u2 attributes_count = cfs->get_u2_fast();
    if (attributes_count > 0) {
      parse_field_attributes(cfs,
                             attributes_count,
                             is_static,
                             signature_index,
                             &constantvalue_index,
                             &is_synthetic,
                             &generic_signature_index,
                             &parsed_annotations,
                             CHECK);

      if (parsed_annotations.field_annotations() != NULL) {
        if (_fields_annotations == NULL) {
          _fields_annotations = MetadataFactory::new_array<AnnotationArray*>(
                                             _loader_data, length, NULL,
                                             CHECK);
        }
        _fields_annotations->at_put(n, parsed_annotations.field_annotations());
        parsed_annotations.set_field_annotations(NULL);
      }
      if (parsed_annotations.field_type_annotations() != NULL) {
        if (_fields_type_annotations == NULL) {
          _fields_type_annotations =
            MetadataFactory::new_array<AnnotationArray*>(_loader_data,
                                                         length,
                                                         NULL,
                                                         CHECK);
        }
        _fields_type_annotations->at_put(n, parsed_annotations.field_type_annotations());
        parsed_annotations.set_field_type_annotations(NULL);
      }

      if (is_synthetic) {
        access_flags.set_is_synthetic();
      }
      if (generic_signature_index != 0) {
        access_flags.set_field_has_generic_signature();
        fa[generic_signature_slot] = generic_signature_index;
        generic_signature_slot ++;
        num_generic_signature ++;
      }
    }

    FieldInfo* const field = FieldInfo::from_field_array(fa, n);
    field->initialize(access_flags.as_short(),
                      name_index,
                      signature_index,
                      constantvalue_index);
    const BasicType type = cp->basic_type_for_signature_at(signature_index);

    // Remember how many oops we encountered and compute allocation type
    const FieldAllocationType atype = fac->update(is_static, type);
    field->set_allocation_type(atype);

    // After field is initialized with type, we can augment it with aux info
    if (parsed_annotations.has_any_annotations())
      parsed_annotations.apply_to(field);
  }

  int index = length;
  if (num_injected != 0) {
    for (int n = 0; n < num_injected; n++) {
      // Check for duplicates
      if (injected[n].may_be_java) {
        const Symbol* const name      = injected[n].name();
        const Symbol* const signature = injected[n].signature();
        bool duplicate = false;
        for (int i = 0; i < length; i++) {
          const FieldInfo* const f = FieldInfo::from_field_array(fa, i);
          if (name      == cp->symbol_at(f->name_index()) &&
              signature == cp->symbol_at(f->signature_index())) {
            // Symbol is desclared in Java so skip this one
            duplicate = true;
            break;
          }
        }
        if (duplicate) {
          // These will be removed from the field array at the end
          continue;
        }
      }

      // Injected field
      FieldInfo* const field = FieldInfo::from_field_array(fa, index);
      field->initialize(JVM_ACC_FIELD_INTERNAL,
                        injected[n].name_index,
                        injected[n].signature_index,
                        0);

      const BasicType type = FieldType::basic_type(injected[n].signature());

      // Remember how many oops we encountered and compute allocation type
      const FieldAllocationType atype = fac->update(false, type);
      field->set_allocation_type(atype);
      index++;
    }
  }

  assert(NULL == _fields, "invariant");

  _fields =
    MetadataFactory::new_array<u2>(_loader_data,
                                   index * FieldInfo::field_slots + num_generic_signature,
                                   CHECK);
  // Sometimes injected fields already exist in the Java source so
  // the fields array could be too long.  In that case the
  // fields array is trimed. Also unused slots that were reserved
  // for generic signature indexes are discarded.
  {
    int i = 0;
    for (; i < index * FieldInfo::field_slots; i++) {
      _fields->at_put(i, fa[i]);
    }
    for (int j = total_fields * FieldInfo::field_slots;
         j < generic_signature_slot; j++) {
      _fields->at_put(i++, fa[j]);
    }
    assert(_fields->length() == i, "");
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
      for (AllFieldStream fs(_fields, cp); !fs.done(); fs.next()) {
        const Symbol* const name = fs.name();
        const Symbol* const sig = fs.signature();
        // If no duplicates, add name/signature in hashtable names_and_sigs.
        if (!put_after_lookup(name, sig, names_and_sigs)) {
          dup = true;
          break;
        }
      }
    }
    if (dup) {
      classfile_parse_error("Duplicate field name&signature in class file %s",
                            CHECK);
    }
  }
}


static void copy_u2_with_conversion(u2* dest, const u2* src, int length) {
  while (length-- > 0) {
    *dest++ = Bytes::get_Java_u2((u1*) (src++));
  }
}

const u2* ClassFileParser::parse_exception_table(const ClassFileStream* const cfs,
                                                 u4 code_length,
                                                 u4 exception_table_length,
                                                 TRAPS) {
  assert(cfs != NULL, "invariant");

  const u2* const exception_table_start = cfs->get_u2_buffer();
  assert(exception_table_start != NULL, "null exception table");

  cfs->guarantee_more(8 * exception_table_length, CHECK_NULL); // start_pc,
                                                               // end_pc,
                                                               // handler_pc,
                                                               // catch_type_index

  // Will check legal target after parsing code array in verifier.
  if (_need_verify) {
    for (unsigned int i = 0; i < exception_table_length; i++) {
      const u2 start_pc = cfs->get_u2_fast();
      const u2 end_pc = cfs->get_u2_fast();
      const u2 handler_pc = cfs->get_u2_fast();
      const u2 catch_type_index = cfs->get_u2_fast();
      guarantee_property((start_pc < end_pc) && (end_pc <= code_length),
                         "Illegal exception table range in class file %s",
                         CHECK_NULL);
      guarantee_property(handler_pc < code_length,
                         "Illegal exception table handler in class file %s",
                         CHECK_NULL);
      if (catch_type_index != 0) {
        guarantee_property(valid_klass_reference_at(catch_type_index),
                           "Catch type in exception table has bad constant type in class file %s", CHECK_NULL);
      }
    }
  } else {
    cfs->skip_u2_fast(exception_table_length * 4);
  }
  return exception_table_start;
}

void ClassFileParser::parse_linenumber_table(u4 code_attribute_length,
                                             u4 code_length,
                                             CompressedLineNumberWriteStream**const write_stream,
                                             TRAPS) {

  const ClassFileStream* const cfs = _stream;
  unsigned int num_entries = cfs->get_u2(CHECK);

  // Each entry is a u2 start_pc, and a u2 line_number
  const unsigned int length_in_bytes = num_entries * (sizeof(u2) * 2);

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
        _linenumbertable_buffer, fixed_buffer_size);
    }
  }

  while (num_entries-- > 0) {
    const u2 bci  = cfs->get_u2_fast(); // start_pc
    const u2 line = cfs->get_u2_fast(); // line_number
    guarantee_property(bci < code_length,
        "Invalid pc in LineNumberTable in class file %s", CHECK);
    (*write_stream)->write_pair(bci, line);
  }
}


class LVT_Hash : public AllStatic {
 public:

  static bool equals(LocalVariableTableElement const& e0, LocalVariableTableElement const& e1) {
  /*
   * 3-tuple start_bci/length/slot has to be unique key,
   * so the following comparison seems to be redundant:
   *       && elem->name_cp_index == entry->_elem->name_cp_index
   */
    return (e0.start_bci     == e1.start_bci &&
            e0.length        == e1.length &&
            e0.name_cp_index == e1.name_cp_index &&
            e0.slot          == e1.slot);
  }

  static unsigned int hash(LocalVariableTableElement const& e0) {
    unsigned int raw_hash = e0.start_bci;

    raw_hash = e0.length        + raw_hash * 37;
    raw_hash = e0.name_cp_index + raw_hash * 37;
    raw_hash = e0.slot          + raw_hash * 37;

    return raw_hash;
  }
};


// Class file LocalVariableTable elements.
class Classfile_LVT_Element VALUE_OBJ_CLASS_SPEC {
 public:
  u2 start_bci;
  u2 length;
  u2 name_cp_index;
  u2 descriptor_cp_index;
  u2 slot;
};

static void copy_lvt_element(const Classfile_LVT_Element* const src,
                             LocalVariableTableElement* const lvt) {
  lvt->start_bci           = Bytes::get_Java_u2((u1*) &src->start_bci);
  lvt->length              = Bytes::get_Java_u2((u1*) &src->length);
  lvt->name_cp_index       = Bytes::get_Java_u2((u1*) &src->name_cp_index);
  lvt->descriptor_cp_index = Bytes::get_Java_u2((u1*) &src->descriptor_cp_index);
  lvt->signature_cp_index  = 0;
  lvt->slot                = Bytes::get_Java_u2((u1*) &src->slot);
}

// Function is used to parse both attributes:
// LocalVariableTable (LVT) and LocalVariableTypeTable (LVTT)
const u2* ClassFileParser::parse_localvariable_table(const ClassFileStream* cfs,
                                                     u4 code_length,
                                                     u2 max_locals,
                                                     u4 code_attribute_length,
                                                     u2* const localvariable_table_length,
                                                     bool isLVTT,
                                                     TRAPS) {
  const char* const tbl_name = (isLVTT) ? "LocalVariableTypeTable" : "LocalVariableTable";
  *localvariable_table_length = cfs->get_u2(CHECK_NULL);
  const unsigned int size =
    (*localvariable_table_length) * sizeof(Classfile_LVT_Element) / sizeof(u2);

  const ConstantPool* const cp = _cp;

  // Verify local variable table attribute has right length
  if (_need_verify) {
    guarantee_property(code_attribute_length == (sizeof(*localvariable_table_length) + size * sizeof(u2)),
                       "%s has wrong length in class file %s", tbl_name, CHECK_NULL);
  }

  const u2* const localvariable_table_start = cfs->get_u2_buffer();
  assert(localvariable_table_start != NULL, "null local variable table");
  if (!_need_verify) {
    cfs->skip_u2_fast(size);
  } else {
    cfs->guarantee_more(size * 2, CHECK_NULL);
    for(int i = 0; i < (*localvariable_table_length); i++) {
      const u2 start_pc = cfs->get_u2_fast();
      const u2 length = cfs->get_u2_fast();
      const u2 name_index = cfs->get_u2_fast();
      const u2 descriptor_index = cfs->get_u2_fast();
      const u2 index = cfs->get_u2_fast();
      // Assign to a u4 to avoid overflow
      const u4 end_pc = (u4)start_pc + (u4)length;

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
      const int cp_size = cp->length();
      guarantee_property(valid_symbol_at(name_index),
        "Name index %u in %s has bad constant type in class file %s",
        name_index, tbl_name, CHECK_NULL);
      guarantee_property(valid_symbol_at(descriptor_index),
        "Signature index %u in %s has bad constant type in class file %s",
        descriptor_index, tbl_name, CHECK_NULL);

      const Symbol* const name = cp->symbol_at(name_index);
      const Symbol* const sig = cp->symbol_at(descriptor_index);
      verify_legal_field_name(name, CHECK_NULL);
      u2 extra_slot = 0;
      if (!isLVTT) {
        verify_legal_field_signature(name, sig, CHECK_NULL);

        // 4894874: check special cases for double and long local variables
        if (sig == vmSymbols::type_signature(T_DOUBLE) ||
            sig == vmSymbols::type_signature(T_LONG)) {
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


void ClassFileParser::parse_type_array(u2 array_length,
                                       u4 code_length,
                                       u4* const u1_index,
                                       u4* const u2_index,
                                       u1* const u1_array,
                                       u2* const u2_array,
                                       TRAPS) {
  const ClassFileStream* const cfs = _stream;
  u2 index = 0; // index in the array with long/double occupying two slots
  u4 i1 = *u1_index;
  u4 i2 = *u2_index + 1;
  for(int i = 0; i < array_length; i++) {
    const u1 tag = u1_array[i1++] = cfs->get_u1(CHECK);
    index++;
    if (tag == ITEM_Long || tag == ITEM_Double) {
      index++;
    } else if (tag == ITEM_Object) {
      const u2 class_index = u2_array[i2++] = cfs->get_u2(CHECK);
      guarantee_property(valid_klass_reference_at(class_index),
                         "Bad class index %u in StackMap in class file %s",
                         class_index, CHECK);
    } else if (tag == ITEM_Uninitialized) {
      const u2 offset = u2_array[i2++] = cfs->get_u2(CHECK);
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

static const u1* parse_stackmap_table(const ClassFileStream* const cfs,
                                      u4 code_attribute_length,
                                      bool need_verify,
                                      TRAPS) {
  assert(cfs != NULL, "invariant");

  if (0 == code_attribute_length) {
    return NULL;
  }

  const u1* const stackmap_table_start = cfs->get_u1_buffer();
  assert(stackmap_table_start != NULL, "null stackmap table");

  // check code_attribute_length first
  cfs->skip_u1(code_attribute_length, CHECK_NULL);

  if (!need_verify && !DumpSharedSpaces) {
    return NULL;
  }
  return stackmap_table_start;
}

const u2* ClassFileParser::parse_checked_exceptions(const ClassFileStream* const cfs,
                                                    u2* const checked_exceptions_length,
                                                    u4 method_attribute_length,
                                                    TRAPS) {
  assert(cfs != NULL, "invariant");
  assert(checked_exceptions_length != NULL, "invariant");

  cfs->guarantee_more(2, CHECK_NULL);  // checked_exceptions_length
  *checked_exceptions_length = cfs->get_u2_fast();
  const unsigned int size =
    (*checked_exceptions_length) * sizeof(CheckedExceptionElement) / sizeof(u2);
  const u2* const checked_exceptions_start = cfs->get_u2_buffer();
  assert(checked_exceptions_start != NULL, "null checked exceptions");
  if (!_need_verify) {
    cfs->skip_u2_fast(size);
  } else {
    // Verify each value in the checked exception table
    u2 checked_exception;
    const u2 len = *checked_exceptions_length;
    cfs->guarantee_more(2 * len, CHECK_NULL);
    for (int i = 0; i < len; i++) {
      checked_exception = cfs->get_u2_fast();
      check_property(
        valid_klass_reference_at(checked_exception),
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

void ClassFileParser::throwIllegalSignature(const char* type,
                                            const Symbol* name,
                                            const Symbol* sig,
                                            TRAPS) const {
  assert(name != NULL, "invariant");
  assert(sig != NULL, "invariant");

  ResourceMark rm(THREAD);
  Exceptions::fthrow(THREAD_AND_LOCATION,
      vmSymbols::java_lang_ClassFormatError(),
      "%s \"%s\" in class %s has illegal signature \"%s\"", type,
      name->as_C_string(), _class_name->as_C_string(), sig->as_C_string());
}

AnnotationCollector::ID
AnnotationCollector::annotation_index(const ClassLoaderData* loader_data,
                                      const Symbol* name) {
  const vmSymbols::SID sid = vmSymbols::find_sid(name);
  // Privileged code can use all annotations.  Other code silently drops some.
  const bool privileged = loader_data->is_the_null_class_loader_data() ||
                          loader_data->is_ext_class_loader_data() ||
                          loader_data->is_anonymous();
  switch (sid) {
    case vmSymbols::VM_SYMBOL_ENUM_NAME(sun_reflect_CallerSensitive_signature): {
      if (_location != _in_method)  break;  // only allow for methods
      if (!privileged)              break;  // only allow in privileged code
      return _method_CallerSensitive;
    }
    case vmSymbols::VM_SYMBOL_ENUM_NAME(java_lang_invoke_ForceInline_signature): {
      if (_location != _in_method)  break;  // only allow for methods
      if (!privileged)              break;  // only allow in privileged code
      return _method_ForceInline;
    }
    case vmSymbols::VM_SYMBOL_ENUM_NAME(java_lang_invoke_DontInline_signature): {
      if (_location != _in_method)  break;  // only allow for methods
      if (!privileged)              break;  // only allow in privileged code
      return _method_DontInline;
    }
    case vmSymbols::VM_SYMBOL_ENUM_NAME(java_lang_invoke_InjectedProfile_signature): {
      if (_location != _in_method)  break;  // only allow for methods
      if (!privileged)              break;  // only allow in privileged code
      return _method_InjectedProfile;
    }
    case vmSymbols::VM_SYMBOL_ENUM_NAME(java_lang_invoke_LambdaForm_Compiled_signature): {
      if (_location != _in_method)  break;  // only allow for methods
      if (!privileged)              break;  // only allow in privileged code
      return _method_LambdaForm_Compiled;
    }
    case vmSymbols::VM_SYMBOL_ENUM_NAME(java_lang_invoke_LambdaForm_Hidden_signature): {
      if (_location != _in_method)  break;  // only allow for methods
      if (!privileged)              break;  // only allow in privileged code
      return _method_LambdaForm_Hidden;
    }
    case vmSymbols::VM_SYMBOL_ENUM_NAME(jdk_internal_HotSpotIntrinsicCandidate_signature): {
      if (_location != _in_method)  break;  // only allow for methods
      if (!privileged)              break;  // only allow in privileged code
      return _method_HotSpotIntrinsicCandidate;
    }
#if INCLUDE_JVMCI
    case vmSymbols::VM_SYMBOL_ENUM_NAME(jdk_vm_ci_hotspot_Stable_signature): {
      if (_location != _in_field)   break;  // only allow for fields
      if (!privileged)              break;  // only allow in privileged code
      return _field_Stable;
    }
#endif
    case vmSymbols::VM_SYMBOL_ENUM_NAME(java_lang_invoke_Stable_signature): {
      if (_location != _in_field)   break;  // only allow for fields
      if (!privileged)              break;  // only allow in privileged code
      return _field_Stable;
    }
    case vmSymbols::VM_SYMBOL_ENUM_NAME(jdk_internal_vm_annotation_Contended_signature): {
      if (_location != _in_field && _location != _in_class) {
        break;  // only allow for fields and classes
      }
      if (!EnableContended || (RestrictContended && !privileged)) {
        break;  // honor privileges
      }
      return _jdk_internal_vm_annotation_Contended;
    }
    case vmSymbols::VM_SYMBOL_ENUM_NAME(jdk_internal_vm_annotation_ReservedStackAccess_signature): {
      if (_location != _in_method)  break;  // only allow for methods
      if (RestrictReservedStack && !privileged) break; // honor privileges
      return _jdk_internal_vm_annotation_ReservedStackAccess;
    }
    default: {
      break;
    }
  }
  return AnnotationCollector::_unknown;
}

void ClassFileParser::FieldAnnotationCollector::apply_to(FieldInfo* f) {
  if (is_contended())
    f->set_contended_group(contended_group());
  if (is_stable())
    f->set_stable(true);
}

ClassFileParser::FieldAnnotationCollector::~FieldAnnotationCollector() {
  // If there's an error deallocate metadata for field annotations
  MetadataFactory::free_array<u1>(_loader_data, _field_annotations);
  MetadataFactory::free_array<u1>(_loader_data, _field_type_annotations);
}

void MethodAnnotationCollector::apply_to(methodHandle m) {
  if (has_annotation(_method_CallerSensitive))
    m->set_caller_sensitive(true);
  if (has_annotation(_method_ForceInline))
    m->set_force_inline(true);
  if (has_annotation(_method_DontInline))
    m->set_dont_inline(true);
  if (has_annotation(_method_InjectedProfile))
    m->set_has_injected_profile(true);
  if (has_annotation(_method_LambdaForm_Compiled) && m->intrinsic_id() == vmIntrinsics::_none)
    m->set_intrinsic_id(vmIntrinsics::_compiledLambdaForm);
  if (has_annotation(_method_LambdaForm_Hidden))
    m->set_hidden(true);
  if (has_annotation(_method_HotSpotIntrinsicCandidate) && !m->is_synthetic())
    m->set_intrinsic_candidate(true);
  if (has_annotation(_jdk_internal_vm_annotation_ReservedStackAccess))
    m->set_has_reserved_stack_access(true);
}

void ClassFileParser::ClassAnnotationCollector::apply_to(InstanceKlass* ik) {
  assert(ik != NULL, "invariant");
  ik->set_is_contended(is_contended());
}

#define MAX_ARGS_SIZE 255
#define MAX_CODE_SIZE 65535
#define INITIAL_MAX_LVT_NUMBER 256

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
void ClassFileParser::copy_localvariable_table(const ConstMethod* cm,
                                               int lvt_cnt,
                                               u2* const localvariable_table_length,
                                               const u2**const localvariable_table_start,
                                               int lvtt_cnt,
                                               u2* const localvariable_type_table_length,
                                               const u2**const localvariable_type_table_start,
                                               TRAPS) {

  ResourceMark rm(THREAD);

  typedef ResourceHashtable<LocalVariableTableElement, LocalVariableTableElement*,
                            &LVT_Hash::hash, &LVT_Hash::equals> LVT_HashTable;

  LVT_HashTable* const table = new LVT_HashTable();

  // To fill LocalVariableTable in
  const Classfile_LVT_Element* cf_lvt;
  LocalVariableTableElement* lvt = cm->localvariable_table_start();

  for (int tbl_no = 0; tbl_no < lvt_cnt; tbl_no++) {
    cf_lvt = (Classfile_LVT_Element *) localvariable_table_start[tbl_no];
    for (int idx = 0; idx < localvariable_table_length[tbl_no]; idx++, lvt++) {
      copy_lvt_element(&cf_lvt[idx], lvt);
      // If no duplicates, add LVT elem in hashtable.
      if (table->put(*lvt, lvt) == false
          && _need_verify
          && _major_version >= JAVA_1_5_VERSION) {
        classfile_parse_error("Duplicated LocalVariableTable attribute "
                              "entry for '%s' in class file %s",
                               _cp->symbol_at(lvt->name_cp_index)->as_utf8(),
                               CHECK);
      }
    }
  }

  // To merge LocalVariableTable and LocalVariableTypeTable
  const Classfile_LVT_Element* cf_lvtt;
  LocalVariableTableElement lvtt_elem;

  for (int tbl_no = 0; tbl_no < lvtt_cnt; tbl_no++) {
    cf_lvtt = (Classfile_LVT_Element *) localvariable_type_table_start[tbl_no];
    for (int idx = 0; idx < localvariable_type_table_length[tbl_no]; idx++) {
      copy_lvt_element(&cf_lvtt[idx], &lvtt_elem);
      LocalVariableTableElement** entry = table->get(lvtt_elem);
      if (entry == NULL) {
        if (_need_verify) {
          classfile_parse_error("LVTT entry for '%s' in class file %s "
                                "does not match any LVT entry",
                                 _cp->symbol_at(lvtt_elem.name_cp_index)->as_utf8(),
                                 CHECK);
        }
      } else if ((*entry)->signature_cp_index != 0 && _need_verify) {
        classfile_parse_error("Duplicated LocalVariableTypeTable attribute "
                              "entry for '%s' in class file %s",
                               _cp->symbol_at(lvtt_elem.name_cp_index)->as_utf8(),
                               CHECK);
      } else {
        // to add generic signatures into LocalVariableTable
        (*entry)->signature_cp_index = lvtt_elem.descriptor_cp_index;
      }
    }
  }
}


void ClassFileParser::copy_method_annotations(ConstMethod* cm,
                                       const u1* runtime_visible_annotations,
                                       int runtime_visible_annotations_length,
                                       const u1* runtime_invisible_annotations,
                                       int runtime_invisible_annotations_length,
                                       const u1* runtime_visible_parameter_annotations,
                                       int runtime_visible_parameter_annotations_length,
                                       const u1* runtime_invisible_parameter_annotations,
                                       int runtime_invisible_parameter_annotations_length,
                                       const u1* runtime_visible_type_annotations,
                                       int runtime_visible_type_annotations_length,
                                       const u1* runtime_invisible_type_annotations,
                                       int runtime_invisible_type_annotations_length,
                                       const u1* annotation_default,
                                       int annotation_default_length,
                                       TRAPS) {

  AnnotationArray* a;

  if (runtime_visible_annotations_length +
      runtime_invisible_annotations_length > 0) {
     a = assemble_annotations(runtime_visible_annotations,
                              runtime_visible_annotations_length,
                              runtime_invisible_annotations,
                              runtime_invisible_annotations_length,
                              CHECK);
     cm->set_method_annotations(a);
  }

  if (runtime_visible_parameter_annotations_length +
      runtime_invisible_parameter_annotations_length > 0) {
    a = assemble_annotations(runtime_visible_parameter_annotations,
                             runtime_visible_parameter_annotations_length,
                             runtime_invisible_parameter_annotations,
                             runtime_invisible_parameter_annotations_length,
                             CHECK);
    cm->set_parameter_annotations(a);
  }

  if (annotation_default_length > 0) {
    a = assemble_annotations(annotation_default,
                             annotation_default_length,
                             NULL,
                             0,
                             CHECK);
    cm->set_default_annotations(a);
  }

  if (runtime_visible_type_annotations_length +
      runtime_invisible_type_annotations_length > 0) {
    a = assemble_annotations(runtime_visible_type_annotations,
                             runtime_visible_type_annotations_length,
                             runtime_invisible_type_annotations,
                             runtime_invisible_type_annotations_length,
                             CHECK);
    cm->set_type_annotations(a);
  }
}


// Note: the parse_method below is big and clunky because all parsing of the code and exceptions
// attribute is inlined. This is cumbersome to avoid since we inline most of the parts in the
// Method* to save footprint, so we only know the size of the resulting Method* when the
// entire method attribute is parsed.
//
// The promoted_flags parameter is used to pass relevant access_flags
// from the method back up to the containing klass. These flag values
// are added to klass's access_flags.

Method* ClassFileParser::parse_method(const ClassFileStream* const cfs,
                                      bool is_interface,
                                      const ConstantPool* cp,
                                      AccessFlags* const promoted_flags,
                                      TRAPS) {
  assert(cfs != NULL, "invariant");
  assert(cp != NULL, "invariant");
  assert(promoted_flags != NULL, "invariant");

  ResourceMark rm(THREAD);
  // Parse fixed parts:
  // access_flags, name_index, descriptor_index, attributes_count
  cfs->guarantee_more(8, CHECK_NULL);

  int flags = cfs->get_u2_fast();
  const u2 name_index = cfs->get_u2_fast();
  const int cp_size = cp->length();
  check_property(
    valid_symbol_at(name_index),
    "Illegal constant pool index %u for method name in class file %s",
    name_index, CHECK_NULL);
  const Symbol* const name = cp->symbol_at(name_index);
  verify_legal_method_name(name, CHECK_NULL);

  const u2 signature_index = cfs->get_u2_fast();
  guarantee_property(
    valid_symbol_at(signature_index),
    "Illegal constant pool index %u for method signature in class file %s",
    signature_index, CHECK_NULL);
  const Symbol* const signature = cp->symbol_at(signature_index);

  if (name == vmSymbols::class_initializer_name()) {
    // We ignore the other access flags for a valid class initializer.
    // (JVM Spec 2nd ed., chapter 4.6)
    if (_major_version < 51) { // backward compatibility
      flags = JVM_ACC_STATIC;
    } else if ((flags & JVM_ACC_STATIC) == JVM_ACC_STATIC) {
      flags &= JVM_ACC_STATIC | JVM_ACC_STRICT;
    } else {
      classfile_parse_error("Method <clinit> is not static in class file %s", CHECK_NULL);
    }
  } else {
    verify_legal_method_modifiers(flags, is_interface, name, CHECK_NULL);
  }

  if (name == vmSymbols::object_initializer_name() && is_interface) {
    classfile_parse_error("Interface cannot have a method named <init>, class file %s", CHECK_NULL);
  }

  int args_size = -1;  // only used when _need_verify is true
  if (_need_verify) {
    args_size = ((flags & JVM_ACC_STATIC) ? 0 : 1) +
                 verify_legal_method_signature(name, signature, CHECK_NULL);
    if (args_size > MAX_ARGS_SIZE) {
      classfile_parse_error("Too many arguments in method signature in class file %s", CHECK_NULL);
    }
  }

  AccessFlags access_flags(flags & JVM_RECOGNIZED_METHOD_MODIFIERS);

  // Default values for code and exceptions attribute elements
  u2 max_stack = 0;
  u2 max_locals = 0;
  u4 code_length = 0;
  const u1* code_start = 0;
  u2 exception_table_length = 0;
  const u2* exception_table_start = NULL;
  Array<int>* exception_handlers = Universe::the_empty_int_array();
  u2 checked_exceptions_length = 0;
  const u2* checked_exceptions_start = NULL;
  CompressedLineNumberWriteStream* linenumber_table = NULL;
  int linenumber_table_length = 0;
  int total_lvt_length = 0;
  u2 lvt_cnt = 0;
  u2 lvtt_cnt = 0;
  bool lvt_allocated = false;
  u2 max_lvt_cnt = INITIAL_MAX_LVT_NUMBER;
  u2 max_lvtt_cnt = INITIAL_MAX_LVT_NUMBER;
  u2* localvariable_table_length = NULL;
  const u2** localvariable_table_start = NULL;
  u2* localvariable_type_table_length = NULL;
  const u2** localvariable_type_table_start = NULL;
  int method_parameters_length = -1;
  const u1* method_parameters_data = NULL;
  bool method_parameters_seen = false;
  bool parsed_code_attribute = false;
  bool parsed_checked_exceptions_attribute = false;
  bool parsed_stackmap_attribute = false;
  // stackmap attribute - JDK1.5
  const u1* stackmap_data = NULL;
  int stackmap_data_length = 0;
  u2 generic_signature_index = 0;
  MethodAnnotationCollector parsed_annotations;
  const u1* runtime_visible_annotations = NULL;
  int runtime_visible_annotations_length = 0;
  const u1* runtime_invisible_annotations = NULL;
  int runtime_invisible_annotations_length = 0;
  const u1* runtime_visible_parameter_annotations = NULL;
  int runtime_visible_parameter_annotations_length = 0;
  const u1* runtime_invisible_parameter_annotations = NULL;
  int runtime_invisible_parameter_annotations_length = 0;
  const u1* runtime_visible_type_annotations = NULL;
  int runtime_visible_type_annotations_length = 0;
  const u1* runtime_invisible_type_annotations = NULL;
  int runtime_invisible_type_annotations_length = 0;
  bool runtime_invisible_annotations_exists = false;
  bool runtime_invisible_type_annotations_exists = false;
  bool runtime_invisible_parameter_annotations_exists = false;
  const u1* annotation_default = NULL;
  int annotation_default_length = 0;

  // Parse code and exceptions attribute
  u2 method_attributes_count = cfs->get_u2_fast();
  while (method_attributes_count--) {
    cfs->guarantee_more(6, CHECK_NULL);  // method_attribute_name_index, method_attribute_length
    const u2 method_attribute_name_index = cfs->get_u2_fast();
    const u4 method_attribute_length = cfs->get_u4_fast();
    check_property(
      valid_symbol_at(method_attribute_name_index),
      "Invalid method attribute name index %u in class file %s",
      method_attribute_name_index, CHECK_NULL);

    const Symbol* const method_attribute_name = cp->symbol_at(method_attribute_name_index);
    if (method_attribute_name == vmSymbols::tag_code()) {
      // Parse Code attribute
      if (_need_verify) {
        guarantee_property(
            !access_flags.is_native() && !access_flags.is_abstract(),
                        "Code attribute in native or abstract methods in class file %s",
                         CHECK_NULL);
      }
      if (parsed_code_attribute) {
        classfile_parse_error("Multiple Code attributes in class file %s",
                              CHECK_NULL);
      }
      parsed_code_attribute = true;

      // Stack size, locals size, and code size
      if (_major_version == 45 && _minor_version <= 2) {
        cfs->guarantee_more(4, CHECK_NULL);
        max_stack = cfs->get_u1_fast();
        max_locals = cfs->get_u1_fast();
        code_length = cfs->get_u2_fast();
      } else {
        cfs->guarantee_more(8, CHECK_NULL);
        max_stack = cfs->get_u2_fast();
        max_locals = cfs->get_u2_fast();
        code_length = cfs->get_u4_fast();
      }
      if (_need_verify) {
        guarantee_property(args_size <= max_locals,
                           "Arguments can't fit into locals in class file %s",
                           CHECK_NULL);
        guarantee_property(code_length > 0 && code_length <= MAX_CODE_SIZE,
                           "Invalid method Code length %u in class file %s",
                           code_length, CHECK_NULL);
      }
      // Code pointer
      code_start = cfs->get_u1_buffer();
      assert(code_start != NULL, "null code start");
      cfs->guarantee_more(code_length, CHECK_NULL);
      cfs->skip_u1_fast(code_length);

      // Exception handler table
      cfs->guarantee_more(2, CHECK_NULL);  // exception_table_length
      exception_table_length = cfs->get_u2_fast();
      if (exception_table_length > 0) {
        exception_table_start = parse_exception_table(cfs,
                                                      code_length,
                                                      exception_table_length,
                                                      CHECK_NULL);
      }

      // Parse additional attributes in code attribute
      cfs->guarantee_more(2, CHECK_NULL);  // code_attributes_count
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
        cfs->guarantee_more(6, CHECK_NULL);  // code_attribute_name_index, code_attribute_length
        const u2 code_attribute_name_index = cfs->get_u2_fast();
        const u4 code_attribute_length = cfs->get_u4_fast();
        calculated_attribute_length += code_attribute_length +
                                       sizeof(code_attribute_name_index) +
                                       sizeof(code_attribute_length);
        check_property(valid_symbol_at(code_attribute_name_index),
                       "Invalid code attribute name index %u in class file %s",
                       code_attribute_name_index,
                       CHECK_NULL);
        if (LoadLineNumberTables &&
            cp->symbol_at(code_attribute_name_index) == vmSymbols::tag_line_number_table()) {
          // Parse and compress line number table
          parse_linenumber_table(code_attribute_length,
                                 code_length,
                                 &linenumber_table,
                                 CHECK_NULL);

        } else if (LoadLocalVariableTables &&
                   cp->symbol_at(code_attribute_name_index) == vmSymbols::tag_local_variable_table()) {
          // Parse local variable table
          if (!lvt_allocated) {
            localvariable_table_length = NEW_RESOURCE_ARRAY_IN_THREAD(
              THREAD, u2,  INITIAL_MAX_LVT_NUMBER);
            localvariable_table_start = NEW_RESOURCE_ARRAY_IN_THREAD(
              THREAD, const u2*, INITIAL_MAX_LVT_NUMBER);
            localvariable_type_table_length = NEW_RESOURCE_ARRAY_IN_THREAD(
              THREAD, u2,  INITIAL_MAX_LVT_NUMBER);
            localvariable_type_table_start = NEW_RESOURCE_ARRAY_IN_THREAD(
              THREAD, const u2*, INITIAL_MAX_LVT_NUMBER);
            lvt_allocated = true;
          }
          if (lvt_cnt == max_lvt_cnt) {
            max_lvt_cnt <<= 1;
            localvariable_table_length = REALLOC_RESOURCE_ARRAY(u2, localvariable_table_length, lvt_cnt, max_lvt_cnt);
            localvariable_table_start  = REALLOC_RESOURCE_ARRAY(const u2*, localvariable_table_start, lvt_cnt, max_lvt_cnt);
          }
          localvariable_table_start[lvt_cnt] =
            parse_localvariable_table(cfs,
                                      code_length,
                                      max_locals,
                                      code_attribute_length,
                                      &localvariable_table_length[lvt_cnt],
                                      false,    // is not LVTT
                                      CHECK_NULL);
          total_lvt_length += localvariable_table_length[lvt_cnt];
          lvt_cnt++;
        } else if (LoadLocalVariableTypeTables &&
                   _major_version >= JAVA_1_5_VERSION &&
                   cp->symbol_at(code_attribute_name_index) == vmSymbols::tag_local_variable_type_table()) {
          if (!lvt_allocated) {
            localvariable_table_length = NEW_RESOURCE_ARRAY_IN_THREAD(
              THREAD, u2,  INITIAL_MAX_LVT_NUMBER);
            localvariable_table_start = NEW_RESOURCE_ARRAY_IN_THREAD(
              THREAD, const u2*, INITIAL_MAX_LVT_NUMBER);
            localvariable_type_table_length = NEW_RESOURCE_ARRAY_IN_THREAD(
              THREAD, u2,  INITIAL_MAX_LVT_NUMBER);
            localvariable_type_table_start = NEW_RESOURCE_ARRAY_IN_THREAD(
              THREAD, const u2*, INITIAL_MAX_LVT_NUMBER);
            lvt_allocated = true;
          }
          // Parse local variable type table
          if (lvtt_cnt == max_lvtt_cnt) {
            max_lvtt_cnt <<= 1;
            localvariable_type_table_length = REALLOC_RESOURCE_ARRAY(u2, localvariable_type_table_length, lvtt_cnt, max_lvtt_cnt);
            localvariable_type_table_start  = REALLOC_RESOURCE_ARRAY(const u2*, localvariable_type_table_start, lvtt_cnt, max_lvtt_cnt);
          }
          localvariable_type_table_start[lvtt_cnt] =
            parse_localvariable_table(cfs,
                                      code_length,
                                      max_locals,
                                      code_attribute_length,
                                      &localvariable_type_table_length[lvtt_cnt],
                                      true,     // is LVTT
                                      CHECK_NULL);
          lvtt_cnt++;
        } else if (_major_version >= Verifier::STACKMAP_ATTRIBUTE_MAJOR_VERSION &&
                   cp->symbol_at(code_attribute_name_index) == vmSymbols::tag_stack_map_table()) {
          // Stack map is only needed by the new verifier in JDK1.5.
          if (parsed_stackmap_attribute) {
            classfile_parse_error("Multiple StackMapTable attributes in class file %s", CHECK_NULL);
          }
          stackmap_data = parse_stackmap_table(cfs, code_attribute_length, _need_verify, CHECK_NULL);
          stackmap_data_length = code_attribute_length;
          parsed_stackmap_attribute = true;
        } else {
          // Skip unknown attributes
          cfs->skip_u1(code_attribute_length, CHECK_NULL);
        }
      }
      // check method attribute length
      if (_need_verify) {
        guarantee_property(method_attribute_length == calculated_attribute_length,
                           "Code segment has wrong length in class file %s",
                           CHECK_NULL);
      }
    } else if (method_attribute_name == vmSymbols::tag_exceptions()) {
      // Parse Exceptions attribute
      if (parsed_checked_exceptions_attribute) {
        classfile_parse_error("Multiple Exceptions attributes in class file %s",
                              CHECK_NULL);
      }
      parsed_checked_exceptions_attribute = true;
      checked_exceptions_start =
            parse_checked_exceptions(cfs,
                                     &checked_exceptions_length,
                                     method_attribute_length,
                                     CHECK_NULL);
    } else if (method_attribute_name == vmSymbols::tag_method_parameters()) {
      // reject multiple method parameters
      if (method_parameters_seen) {
        classfile_parse_error("Multiple MethodParameters attributes in class file %s",
                              CHECK_NULL);
      }
      method_parameters_seen = true;
      method_parameters_length = cfs->get_u1_fast();
      const u2 real_length = (method_parameters_length * 4u) + 1u;
      if (method_attribute_length != real_length) {
        classfile_parse_error(
          "Invalid MethodParameters method attribute length %u in class file",
          method_attribute_length, CHECK_NULL);
      }
      method_parameters_data = cfs->get_u1_buffer();
      cfs->skip_u2_fast(method_parameters_length);
      cfs->skip_u2_fast(method_parameters_length);
      // ignore this attribute if it cannot be reflected
      if (!SystemDictionary::Parameter_klass_loaded())
        method_parameters_length = -1;
    } else if (method_attribute_name == vmSymbols::tag_synthetic()) {
      if (method_attribute_length != 0) {
        classfile_parse_error(
          "Invalid Synthetic method attribute length %u in class file %s",
          method_attribute_length, CHECK_NULL);
      }
      // Should we check that there hasn't already been a synthetic attribute?
      access_flags.set_is_synthetic();
    } else if (method_attribute_name == vmSymbols::tag_deprecated()) { // 4276120
      if (method_attribute_length != 0) {
        classfile_parse_error(
          "Invalid Deprecated method attribute length %u in class file %s",
          method_attribute_length, CHECK_NULL);
      }
    } else if (_major_version >= JAVA_1_5_VERSION) {
      if (method_attribute_name == vmSymbols::tag_signature()) {
        if (method_attribute_length != 2) {
          classfile_parse_error(
            "Invalid Signature attribute length %u in class file %s",
            method_attribute_length, CHECK_NULL);
        }
        generic_signature_index = parse_generic_signature_attribute(cfs, CHECK_NULL);
      } else if (method_attribute_name == vmSymbols::tag_runtime_visible_annotations()) {
        if (runtime_visible_annotations != NULL) {
          classfile_parse_error(
            "Multiple RuntimeVisibleAnnotations attributes for method in class file %s",
            CHECK_NULL);
        }
        runtime_visible_annotations_length = method_attribute_length;
        runtime_visible_annotations = cfs->get_u1_buffer();
        assert(runtime_visible_annotations != NULL, "null visible annotations");
        parse_annotations(cp,
                          runtime_visible_annotations,
                          runtime_visible_annotations_length,
                          &parsed_annotations,
                          _loader_data,
                          CHECK_NULL);
        cfs->skip_u1(runtime_visible_annotations_length, CHECK_NULL);
      } else if (method_attribute_name == vmSymbols::tag_runtime_invisible_annotations()) {
        if (runtime_invisible_annotations_exists) {
          classfile_parse_error(
            "Multiple RuntimeInvisibleAnnotations attributes for method in class file %s",
            CHECK_NULL);
        }
        runtime_invisible_annotations_exists = true;
        if (PreserveAllAnnotations) {
          runtime_invisible_annotations_length = method_attribute_length;
          runtime_invisible_annotations = cfs->get_u1_buffer();
          assert(runtime_invisible_annotations != NULL, "null invisible annotations");
        }
        cfs->skip_u1(method_attribute_length, CHECK_NULL);
      } else if (method_attribute_name == vmSymbols::tag_runtime_visible_parameter_annotations()) {
        if (runtime_visible_parameter_annotations != NULL) {
          classfile_parse_error(
            "Multiple RuntimeVisibleParameterAnnotations attributes for method in class file %s",
            CHECK_NULL);
        }
        runtime_visible_parameter_annotations_length = method_attribute_length;
        runtime_visible_parameter_annotations = cfs->get_u1_buffer();
        assert(runtime_visible_parameter_annotations != NULL, "null visible parameter annotations");
        cfs->skip_u1(runtime_visible_parameter_annotations_length, CHECK_NULL);
      } else if (method_attribute_name == vmSymbols::tag_runtime_invisible_parameter_annotations()) {
        if (runtime_invisible_parameter_annotations_exists) {
          classfile_parse_error(
            "Multiple RuntimeInvisibleParameterAnnotations attributes for method in class file %s",
            CHECK_NULL);
        }
        runtime_invisible_parameter_annotations_exists = true;
        if (PreserveAllAnnotations) {
          runtime_invisible_parameter_annotations_length = method_attribute_length;
          runtime_invisible_parameter_annotations = cfs->get_u1_buffer();
          assert(runtime_invisible_parameter_annotations != NULL,
            "null invisible parameter annotations");
        }
        cfs->skip_u1(method_attribute_length, CHECK_NULL);
      } else if (method_attribute_name == vmSymbols::tag_annotation_default()) {
        if (annotation_default != NULL) {
          classfile_parse_error(
            "Multiple AnnotationDefault attributes for method in class file %s",
            CHECK_NULL);
        }
        annotation_default_length = method_attribute_length;
        annotation_default = cfs->get_u1_buffer();
        assert(annotation_default != NULL, "null annotation default");
        cfs->skip_u1(annotation_default_length, CHECK_NULL);
      } else if (method_attribute_name == vmSymbols::tag_runtime_visible_type_annotations()) {
        if (runtime_visible_type_annotations != NULL) {
          classfile_parse_error(
            "Multiple RuntimeVisibleTypeAnnotations attributes for method in class file %s",
            CHECK_NULL);
        }
        runtime_visible_type_annotations_length = method_attribute_length;
        runtime_visible_type_annotations = cfs->get_u1_buffer();
        assert(runtime_visible_type_annotations != NULL, "null visible type annotations");
        // No need for the VM to parse Type annotations
        cfs->skip_u1(runtime_visible_type_annotations_length, CHECK_NULL);
      } else if (method_attribute_name == vmSymbols::tag_runtime_invisible_type_annotations()) {
        if (runtime_invisible_type_annotations_exists) {
          classfile_parse_error(
            "Multiple RuntimeInvisibleTypeAnnotations attributes for method in class file %s",
            CHECK_NULL);
        } else {
          runtime_invisible_type_annotations_exists = true;
        }
        if (PreserveAllAnnotations) {
          runtime_invisible_type_annotations_length = method_attribute_length;
          runtime_invisible_type_annotations = cfs->get_u1_buffer();
          assert(runtime_invisible_type_annotations != NULL, "null invisible type annotations");
        }
        cfs->skip_u1(method_attribute_length, CHECK_NULL);
      } else {
        // Skip unknown attributes
        cfs->skip_u1(method_attribute_length, CHECK_NULL);
      }
    } else {
      // Skip unknown attributes
      cfs->skip_u1(method_attribute_length, CHECK_NULL);
    }
  }

  if (linenumber_table != NULL) {
    linenumber_table->write_terminator();
    linenumber_table_length = linenumber_table->position();
  }

  // Make sure there's at least one Code attribute in non-native/non-abstract method
  if (_need_verify) {
    guarantee_property(access_flags.is_native() ||
                       access_flags.is_abstract() ||
                       parsed_code_attribute,
                       "Absent Code attribute in method that is not native or abstract in class file %s",
                       CHECK_NULL);
  }

  // All sizing information for a Method* is finally available, now create it
  InlineTableSizes sizes(
      total_lvt_length,
      linenumber_table_length,
      exception_table_length,
      checked_exceptions_length,
      method_parameters_length,
      generic_signature_index,
      runtime_visible_annotations_length +
           runtime_invisible_annotations_length,
      runtime_visible_parameter_annotations_length +
           runtime_invisible_parameter_annotations_length,
      runtime_visible_type_annotations_length +
           runtime_invisible_type_annotations_length,
      annotation_default_length,
      0);

  Method* const m = Method::allocate(_loader_data,
                                     code_length,
                                     access_flags,
                                     &sizes,
                                     ConstMethod::NORMAL,
                                     CHECK_NULL);

  ClassLoadingService::add_class_method_size(m->size()*HeapWordSize);

  // Fill in information from fixed part (access_flags already set)
  m->set_constants(_cp);
  m->set_name_index(name_index);
  m->set_signature_index(signature_index);
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
  if (stackmap_data != NULL) {
    m->constMethod()->copy_stackmap_data(_loader_data,
                                         (u1*)stackmap_data,
                                         stackmap_data_length,
                                         CHECK_NULL);
  }

  // Copy byte codes
  m->set_code((u1*)code_start);

  // Copy line number table
  if (linenumber_table != NULL) {
    memcpy(m->compressed_linenumber_table(),
           linenumber_table->buffer(),
           linenumber_table_length);
  }

  // Copy exception table
  if (exception_table_length > 0) {
    int size =
      exception_table_length * sizeof(ExceptionTableElement) / sizeof(u2);
    copy_u2_with_conversion((u2*) m->exception_table_start(),
                            exception_table_start, size);
  }

  // Copy method parameters
  if (method_parameters_length > 0) {
    MethodParametersElement* elem = m->constMethod()->method_parameters_start();
    for (int i = 0; i < method_parameters_length; i++) {
      elem[i].name_cp_index = Bytes::get_Java_u2((address)method_parameters_data);
      method_parameters_data += 2;
      elem[i].flags = Bytes::get_Java_u2((address)method_parameters_data);
      method_parameters_data += 2;
    }
  }

  // Copy checked exceptions
  if (checked_exceptions_length > 0) {
    const int size =
      checked_exceptions_length * sizeof(CheckedExceptionElement) / sizeof(u2);
    copy_u2_with_conversion((u2*) m->checked_exceptions_start(),
                            checked_exceptions_start,
                            size);
  }

  // Copy class file LVT's/LVTT's into the HotSpot internal LVT.
  if (total_lvt_length > 0) {
    promoted_flags->set_has_localvariable_table();
    copy_localvariable_table(m->constMethod(),
                             lvt_cnt,
                             localvariable_table_length,
                             localvariable_table_start,
                             lvtt_cnt,
                             localvariable_type_table_length,
                             localvariable_type_table_start,
                             CHECK_NULL);
  }

  if (parsed_annotations.has_any_annotations())
    parsed_annotations.apply_to(m);

  // Copy annotations
  copy_method_annotations(m->constMethod(),
                          runtime_visible_annotations,
                          runtime_visible_annotations_length,
                          runtime_invisible_annotations,
                          runtime_invisible_annotations_length,
                          runtime_visible_parameter_annotations,
                          runtime_visible_parameter_annotations_length,
                          runtime_invisible_parameter_annotations,
                          runtime_invisible_parameter_annotations_length,
                          runtime_visible_type_annotations,
                          runtime_visible_type_annotations_length,
                          runtime_invisible_type_annotations,
                          runtime_invisible_type_annotations_length,
                          annotation_default,
                          annotation_default_length,
                          CHECK_NULL);

  if (name == vmSymbols::finalize_method_name() &&
      signature == vmSymbols::void_method_signature()) {
    if (m->is_empty_method()) {
      _has_empty_finalizer = true;
    } else {
      _has_finalizer = true;
    }
  }
  if (name == vmSymbols::object_initializer_name() &&
      signature == vmSymbols::void_method_signature() &&
      m->is_vanilla_constructor()) {
    _has_vanilla_constructor = true;
  }

  NOT_PRODUCT(m->verify());
  return m;
}


// The promoted_flags parameter is used to pass relevant access_flags
// from the methods back up to the containing klass. These flag values
// are added to klass's access_flags.
// Side-effects: populates the _methods field in the parser
void ClassFileParser::parse_methods(const ClassFileStream* const cfs,
                                    bool is_interface,
                                    AccessFlags* promoted_flags,
                                    bool* has_final_method,
                                    bool* declares_default_methods,
                                    TRAPS) {
  assert(cfs != NULL, "invariant");
  assert(promoted_flags != NULL, "invariant");
  assert(has_final_method != NULL, "invariant");
  assert(declares_default_methods != NULL, "invariant");

  assert(NULL == _methods, "invariant");

  cfs->guarantee_more(2, CHECK);  // length
  const u2 length = cfs->get_u2_fast();
  if (length == 0) {
    _methods = Universe::the_empty_method_array();
  } else {
    _methods = MetadataFactory::new_array<Method*>(_loader_data,
                                                   length,
                                                   NULL,
                                                   CHECK);

    HandleMark hm(THREAD);
    for (int index = 0; index < length; index++) {
      Method* method = parse_method(cfs,
                                    is_interface,
                                    _cp,
                                    promoted_flags,
                                    CHECK);

      if (method->is_final()) {
        *has_final_method = true;
      }
      // declares_default_methods: declares concrete instance methods, any access flags
      // used for interface initialization, and default method inheritance analysis
      if (is_interface && !(*declares_default_methods)
        && !method->is_abstract() && !method->is_static()) {
        *declares_default_methods = true;
      }
      _methods->at_put(index, method);
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
          const Method* const m = _methods->at(i);
          // If no duplicates, add name/signature in hashtable names_and_sigs.
          if (!put_after_lookup(m->name(), m->signature(), names_and_sigs)) {
            dup = true;
            break;
          }
        }
      }
      if (dup) {
        classfile_parse_error("Duplicate method name&signature in class file %s",
                              CHECK);
      }
    }
  }
}

static const intArray* sort_methods(Array<Method*>* methods) {
  const int length = methods->length();
  // If JVMTI original method ordering or sharing is enabled we have to
  // remember the original class file ordering.
  // We temporarily use the vtable_index field in the Method* to store the
  // class file index, so we can read in after calling qsort.
  // Put the method ordering in the shared archive.
  if (JvmtiExport::can_maintain_original_method_order() || DumpSharedSpaces) {
    for (int index = 0; index < length; index++) {
      Method* const m = methods->at(index);
      assert(!m->valid_vtable_index(), "vtable index should not be set");
      m->set_vtable_index(index);
    }
  }
  // Sort method array by ascending method name (for faster lookups & vtable construction)
  // Note that the ordering is not alphabetical, see Symbol::fast_compare
  Method::sort_methods(methods);

  intArray* method_ordering = NULL;
  // If JVMTI original method ordering or sharing is enabled construct int
  // array remembering the original ordering
  if (JvmtiExport::can_maintain_original_method_order() || DumpSharedSpaces) {
    method_ordering = new intArray(length);
    for (int index = 0; index < length; index++) {
      Method* const m = methods->at(index);
      const int old_index = m->vtable_index();
      assert(old_index >= 0 && old_index < length, "invalid method index");
      method_ordering->at_put(index, old_index);
      m->set_vtable_index(Method::invalid_vtable_index);
    }
  }
  return method_ordering;
}

// Parse generic_signature attribute for methods and fields
u2 ClassFileParser::parse_generic_signature_attribute(const ClassFileStream* const cfs,
                                                      TRAPS) {
  assert(cfs != NULL, "invariant");

  cfs->guarantee_more(2, CHECK_0);  // generic_signature_index
  const u2 generic_signature_index = cfs->get_u2_fast();
  check_property(
    valid_symbol_at(generic_signature_index),
    "Invalid Signature attribute at constant pool index %u in class file %s",
    generic_signature_index, CHECK_0);
  return generic_signature_index;
}

void ClassFileParser::parse_classfile_sourcefile_attribute(const ClassFileStream* const cfs,
                                                           TRAPS) {

  assert(cfs != NULL, "invariant");

  cfs->guarantee_more(2, CHECK);  // sourcefile_index
  const u2 sourcefile_index = cfs->get_u2_fast();
  check_property(
    valid_symbol_at(sourcefile_index),
    "Invalid SourceFile attribute at constant pool index %u in class file %s",
    sourcefile_index, CHECK);
  set_class_sourcefile_index(sourcefile_index);
}

void ClassFileParser::parse_classfile_source_debug_extension_attribute(const ClassFileStream* const cfs,
                                                                       int length,
                                                                       TRAPS) {
  assert(cfs != NULL, "invariant");

  const u1* const sde_buffer = cfs->get_u1_buffer();
  assert(sde_buffer != NULL, "null sde buffer");

  // Don't bother storing it if there is no way to retrieve it
  if (JvmtiExport::can_get_source_debug_extension()) {
    assert((length+1) > length, "Overflow checking");
    u1* const sde = NEW_RESOURCE_ARRAY_IN_THREAD(THREAD, u1, length+1);
    for (int i = 0; i < length; i++) {
      sde[i] = sde_buffer[i];
    }
    sde[length] = '\0';
    set_class_sde_buffer((const char*)sde, length);
  }
  // Got utf8 string, set stream position forward
  cfs->skip_u1(length, CHECK);
}


// Inner classes can be static, private or protected (classic VM does this)
#define RECOGNIZED_INNER_CLASS_MODIFIERS ( JVM_RECOGNIZED_CLASS_MODIFIERS | \
                                           JVM_ACC_PRIVATE |                \
                                           JVM_ACC_PROTECTED |              \
                                           JVM_ACC_STATIC                   \
                                         )

// Return number of classes in the inner classes attribute table
u2 ClassFileParser::parse_classfile_inner_classes_attribute(const ClassFileStream* const cfs,
                                                            const u1* const inner_classes_attribute_start,
                                                            bool parsed_enclosingmethod_attribute,
                                                            u2 enclosing_method_class_index,
                                                            u2 enclosing_method_method_index,
                                                            TRAPS) {
  const u1* const current_mark = cfs->current();
  u2 length = 0;
  if (inner_classes_attribute_start != NULL) {
    cfs->set_current(inner_classes_attribute_start);
    cfs->guarantee_more(2, CHECK_0);  // length
    length = cfs->get_u2_fast();
  }

  // 4-tuples of shorts of inner classes data and 2 shorts of enclosing
  // method data:
  //   [inner_class_info_index,
  //    outer_class_info_index,
  //    inner_name_index,
  //    inner_class_access_flags,
  //    ...
  //    enclosing_method_class_index,
  //    enclosing_method_method_index]
  const int size = length * 4 + (parsed_enclosingmethod_attribute ? 2 : 0);
  Array<u2>* const inner_classes = MetadataFactory::new_array<u2>(_loader_data, size, CHECK_0);
  _inner_classes = inner_classes;

  int index = 0;
  const int cp_size = _cp->length();
  cfs->guarantee_more(8 * length, CHECK_0);  // 4-tuples of u2
  for (int n = 0; n < length; n++) {
    // Inner class index
    const u2 inner_class_info_index = cfs->get_u2_fast();
    check_property(
      valid_klass_reference_at(inner_class_info_index),
      "inner_class_info_index %u has bad constant type in class file %s",
      inner_class_info_index, CHECK_0);
    // Outer class index
    const u2 outer_class_info_index = cfs->get_u2_fast();
    check_property(
      outer_class_info_index == 0 ||
        valid_klass_reference_at(outer_class_info_index),
      "outer_class_info_index %u has bad constant type in class file %s",
      outer_class_info_index, CHECK_0);
    // Inner class name
    const u2 inner_name_index = cfs->get_u2_fast();
    check_property(
      inner_name_index == 0 || valid_symbol_at(inner_name_index),
      "inner_name_index %u has bad constant type in class file %s",
      inner_name_index, CHECK_0);
    if (_need_verify) {
      guarantee_property(inner_class_info_index != outer_class_info_index,
                         "Class is both outer and inner class in class file %s", CHECK_0);
    }
    // Access flags
    jint flags = cfs->get_u2_fast() & RECOGNIZED_INNER_CLASS_MODIFIERS;
    if ((flags & JVM_ACC_INTERFACE) && _major_version < JAVA_6_VERSION) {
      // Set abstract bit for old class files for backward compatibility
      flags |= JVM_ACC_ABSTRACT;
    }
    verify_legal_class_modifiers(flags, CHECK_0);
    AccessFlags inner_access_flags(flags);

    inner_classes->at_put(index++, inner_class_info_index);
    inner_classes->at_put(index++, outer_class_info_index);
    inner_classes->at_put(index++, inner_name_index);
    inner_classes->at_put(index++, inner_access_flags.as_short());
  }

  // 4347400: make sure there's no duplicate entry in the classes array
  if (_need_verify && _major_version >= JAVA_1_5_VERSION) {
    for(int i = 0; i < length * 4; i += 4) {
      for(int j = i + 4; j < length * 4; j += 4) {
        guarantee_property((inner_classes->at(i)   != inner_classes->at(j) ||
                            inner_classes->at(i+1) != inner_classes->at(j+1) ||
                            inner_classes->at(i+2) != inner_classes->at(j+2) ||
                            inner_classes->at(i+3) != inner_classes->at(j+3)),
                            "Duplicate entry in InnerClasses in class file %s",
                            CHECK_0);
      }
    }
  }

  // Set EnclosingMethod class and method indexes.
  if (parsed_enclosingmethod_attribute) {
    inner_classes->at_put(index++, enclosing_method_class_index);
    inner_classes->at_put(index++, enclosing_method_method_index);
  }
  assert(index == size, "wrong size");

  // Restore buffer's current position.
  cfs->set_current(current_mark);

  return length;
}

void ClassFileParser::parse_classfile_synthetic_attribute(TRAPS) {
  set_class_synthetic_flag(true);
}

void ClassFileParser::parse_classfile_signature_attribute(const ClassFileStream* const cfs, TRAPS) {
  assert(cfs != NULL, "invariant");

  const u2 signature_index = cfs->get_u2(CHECK);
  check_property(
    valid_symbol_at(signature_index),
    "Invalid constant pool index %u in Signature attribute in class file %s",
    signature_index, CHECK);
  set_class_generic_signature_index(signature_index);
}

void ClassFileParser::parse_classfile_bootstrap_methods_attribute(const ClassFileStream* const cfs,
                                                                  ConstantPool* cp,
                                                                  u4 attribute_byte_length,
                                                                  TRAPS) {
  assert(cfs != NULL, "invariant");
  assert(cp != NULL, "invariant");

  const u1* const current_start = cfs->current();

  guarantee_property(attribute_byte_length >= sizeof(u2),
                     "Invalid BootstrapMethods attribute length %u in class file %s",
                     attribute_byte_length,
                     CHECK);

  cfs->guarantee_more(attribute_byte_length, CHECK);

  const int attribute_array_length = cfs->get_u2_fast();

  guarantee_property(_max_bootstrap_specifier_index < attribute_array_length,
                     "Short length on BootstrapMethods in class file %s",
                     CHECK);


  // The attribute contains a counted array of counted tuples of shorts,
  // represending bootstrap specifiers:
  //    length*{bootstrap_method_index, argument_count*{argument_index}}
  const int operand_count = (attribute_byte_length - sizeof(u2)) / sizeof(u2);
  // operand_count = number of shorts in attr, except for leading length

  // The attribute is copied into a short[] array.
  // The array begins with a series of short[2] pairs, one for each tuple.
  const int index_size = (attribute_array_length * 2);

  Array<u2>* const operands =
    MetadataFactory::new_array<u2>(_loader_data, index_size + operand_count, CHECK);

  // Eagerly assign operands so they will be deallocated with the constant
  // pool if there is an error.
  cp->set_operands(operands);

  int operand_fill_index = index_size;
  const int cp_size = cp->length();

  for (int n = 0; n < attribute_array_length; n++) {
    // Store a 32-bit offset into the header of the operand array.
    ConstantPool::operand_offset_at_put(operands, n, operand_fill_index);

    // Read a bootstrap specifier.
    cfs->guarantee_more(sizeof(u2) * 2, CHECK);  // bsm, argc
    const u2 bootstrap_method_index = cfs->get_u2_fast();
    const u2 argument_count = cfs->get_u2_fast();
    check_property(
      valid_cp_range(bootstrap_method_index, cp_size) &&
      cp->tag_at(bootstrap_method_index).is_method_handle(),
      "bootstrap_method_index %u has bad constant type in class file %s",
      bootstrap_method_index,
      CHECK);

    guarantee_property((operand_fill_index + 1 + argument_count) < operands->length(),
      "Invalid BootstrapMethods num_bootstrap_methods or num_bootstrap_arguments value in class file %s",
      CHECK);

    operands->at_put(operand_fill_index++, bootstrap_method_index);
    operands->at_put(operand_fill_index++, argument_count);

    cfs->guarantee_more(sizeof(u2) * argument_count, CHECK);  // argv[argc]
    for (int j = 0; j < argument_count; j++) {
      const u2 argument_index = cfs->get_u2_fast();
      check_property(
        valid_cp_range(argument_index, cp_size) &&
        cp->tag_at(argument_index).is_loadable_constant(),
        "argument_index %u has bad constant type in class file %s",
        argument_index,
        CHECK);
      operands->at_put(operand_fill_index++, argument_index);
    }
  }
  guarantee_property(current_start + attribute_byte_length == cfs->current(),
                     "Bad length on BootstrapMethods in class file %s",
                     CHECK);
}

void ClassFileParser::parse_classfile_attributes(const ClassFileStream* const cfs,
                                                 ConstantPool* cp,
                 ClassFileParser::ClassAnnotationCollector* parsed_annotations,
                                                 TRAPS) {
  assert(cfs != NULL, "invariant");
  assert(cp != NULL, "invariant");
  assert(parsed_annotations != NULL, "invariant");

  // Set inner classes attribute to default sentinel
  _inner_classes = Universe::the_empty_short_array();
  cfs->guarantee_more(2, CHECK);  // attributes_count
  u2 attributes_count = cfs->get_u2_fast();
  bool parsed_sourcefile_attribute = false;
  bool parsed_innerclasses_attribute = false;
  bool parsed_enclosingmethod_attribute = false;
  bool parsed_bootstrap_methods_attribute = false;
  const u1* runtime_visible_annotations = NULL;
  int runtime_visible_annotations_length = 0;
  const u1* runtime_invisible_annotations = NULL;
  int runtime_invisible_annotations_length = 0;
  const u1* runtime_visible_type_annotations = NULL;
  int runtime_visible_type_annotations_length = 0;
  const u1* runtime_invisible_type_annotations = NULL;
  int runtime_invisible_type_annotations_length = 0;
  bool runtime_invisible_type_annotations_exists = false;
  bool runtime_invisible_annotations_exists = false;
  bool parsed_source_debug_ext_annotations_exist = false;
  const u1* inner_classes_attribute_start = NULL;
  u4  inner_classes_attribute_length = 0;
  u2  enclosing_method_class_index = 0;
  u2  enclosing_method_method_index = 0;
  // Iterate over attributes
  while (attributes_count--) {
    cfs->guarantee_more(6, CHECK);  // attribute_name_index, attribute_length
    const u2 attribute_name_index = cfs->get_u2_fast();
    const u4 attribute_length = cfs->get_u4_fast();
    check_property(
      valid_symbol_at(attribute_name_index),
      "Attribute name has bad constant pool index %u in class file %s",
      attribute_name_index, CHECK);
    const Symbol* const tag = cp->symbol_at(attribute_name_index);
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
      parse_classfile_sourcefile_attribute(cfs, CHECK);
    } else if (tag == vmSymbols::tag_source_debug_extension()) {
      // Check for SourceDebugExtension tag
      if (parsed_source_debug_ext_annotations_exist) {
          classfile_parse_error(
            "Multiple SourceDebugExtension attributes in class file %s", CHECK);
      }
      parsed_source_debug_ext_annotations_exist = true;
      parse_classfile_source_debug_extension_attribute(cfs, (int)attribute_length, CHECK);
    } else if (tag == vmSymbols::tag_inner_classes()) {
      // Check for InnerClasses tag
      if (parsed_innerclasses_attribute) {
        classfile_parse_error("Multiple InnerClasses attributes in class file %s", CHECK);
      } else {
        parsed_innerclasses_attribute = true;
      }
      inner_classes_attribute_start = cfs->get_u1_buffer();
      inner_classes_attribute_length = attribute_length;
      cfs->skip_u1(inner_classes_attribute_length, CHECK);
    } else if (tag == vmSymbols::tag_synthetic()) {
      // Check for Synthetic tag
      // Shouldn't we check that the synthetic flags wasn't already set? - not required in spec
      if (attribute_length != 0) {
        classfile_parse_error(
          "Invalid Synthetic classfile attribute length %u in class file %s",
          attribute_length, CHECK);
      }
      parse_classfile_synthetic_attribute(CHECK);
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
        parse_classfile_signature_attribute(cfs, CHECK);
      } else if (tag == vmSymbols::tag_runtime_visible_annotations()) {
        if (runtime_visible_annotations != NULL) {
          classfile_parse_error(
            "Multiple RuntimeVisibleAnnotations attributes in class file %s", CHECK);
        }
        runtime_visible_annotations_length = attribute_length;
        runtime_visible_annotations = cfs->get_u1_buffer();
        assert(runtime_visible_annotations != NULL, "null visible annotations");
        parse_annotations(cp,
                          runtime_visible_annotations,
                          runtime_visible_annotations_length,
                          parsed_annotations,
                          _loader_data,
                          CHECK);
        cfs->skip_u1(runtime_visible_annotations_length, CHECK);
      } else if (tag == vmSymbols::tag_runtime_invisible_annotations()) {
        if (runtime_invisible_annotations_exists) {
          classfile_parse_error(
            "Multiple RuntimeInvisibleAnnotations attributes in class file %s", CHECK);
        }
        runtime_invisible_annotations_exists = true;
        if (PreserveAllAnnotations) {
          runtime_invisible_annotations_length = attribute_length;
          runtime_invisible_annotations = cfs->get_u1_buffer();
          assert(runtime_invisible_annotations != NULL, "null invisible annotations");
        }
        cfs->skip_u1(attribute_length, CHECK);
      } else if (tag == vmSymbols::tag_enclosing_method()) {
        if (parsed_enclosingmethod_attribute) {
          classfile_parse_error("Multiple EnclosingMethod attributes in class file %s", CHECK);
        } else {
          parsed_enclosingmethod_attribute = true;
        }
        guarantee_property(attribute_length == 4,
          "Wrong EnclosingMethod attribute length %u in class file %s",
          attribute_length, CHECK);
        cfs->guarantee_more(4, CHECK);  // class_index, method_index
        enclosing_method_class_index  = cfs->get_u2_fast();
        enclosing_method_method_index = cfs->get_u2_fast();
        if (enclosing_method_class_index == 0) {
          classfile_parse_error("Invalid class index in EnclosingMethod attribute in class file %s", CHECK);
        }
        // Validate the constant pool indices and types
        check_property(valid_klass_reference_at(enclosing_method_class_index),
          "Invalid or out-of-bounds class index in EnclosingMethod attribute in class file %s", CHECK);
        if (enclosing_method_method_index != 0 &&
            (!cp->is_within_bounds(enclosing_method_method_index) ||
             !cp->tag_at(enclosing_method_method_index).is_name_and_type())) {
          classfile_parse_error("Invalid or out-of-bounds method index in EnclosingMethod attribute in class file %s", CHECK);
        }
      } else if (tag == vmSymbols::tag_bootstrap_methods() &&
                 _major_version >= Verifier::INVOKEDYNAMIC_MAJOR_VERSION) {
        if (parsed_bootstrap_methods_attribute)
          classfile_parse_error("Multiple BootstrapMethods attributes in class file %s", CHECK);
        parsed_bootstrap_methods_attribute = true;
        parse_classfile_bootstrap_methods_attribute(cfs, cp, attribute_length, CHECK);
      } else if (tag == vmSymbols::tag_runtime_visible_type_annotations()) {
        if (runtime_visible_type_annotations != NULL) {
          classfile_parse_error(
            "Multiple RuntimeVisibleTypeAnnotations attributes in class file %s", CHECK);
        }
        runtime_visible_type_annotations_length = attribute_length;
        runtime_visible_type_annotations = cfs->get_u1_buffer();
        assert(runtime_visible_type_annotations != NULL, "null visible type annotations");
        // No need for the VM to parse Type annotations
        cfs->skip_u1(runtime_visible_type_annotations_length, CHECK);
      } else if (tag == vmSymbols::tag_runtime_invisible_type_annotations()) {
        if (runtime_invisible_type_annotations_exists) {
          classfile_parse_error(
            "Multiple RuntimeInvisibleTypeAnnotations attributes in class file %s", CHECK);
        } else {
          runtime_invisible_type_annotations_exists = true;
        }
        if (PreserveAllAnnotations) {
          runtime_invisible_type_annotations_length = attribute_length;
          runtime_invisible_type_annotations = cfs->get_u1_buffer();
          assert(runtime_invisible_type_annotations != NULL, "null invisible type annotations");
        }
        cfs->skip_u1(attribute_length, CHECK);
      } else {
        // Unknown attribute
        cfs->skip_u1(attribute_length, CHECK);
      }
    } else {
      // Unknown attribute
      cfs->skip_u1(attribute_length, CHECK);
    }
  }
  _annotations = assemble_annotations(runtime_visible_annotations,
                                      runtime_visible_annotations_length,
                                      runtime_invisible_annotations,
                                      runtime_invisible_annotations_length,
                                      CHECK);
  _type_annotations = assemble_annotations(runtime_visible_type_annotations,
                                           runtime_visible_type_annotations_length,
                                           runtime_invisible_type_annotations,
                                           runtime_invisible_type_annotations_length,
                                           CHECK);

  if (parsed_innerclasses_attribute || parsed_enclosingmethod_attribute) {
    const u2 num_of_classes = parse_classfile_inner_classes_attribute(
                            cfs,
                            inner_classes_attribute_start,
                            parsed_innerclasses_attribute,
                            enclosing_method_class_index,
                            enclosing_method_method_index,
                            CHECK);
    if (parsed_innerclasses_attribute &&_need_verify && _major_version >= JAVA_1_5_VERSION) {
      guarantee_property(
        inner_classes_attribute_length == sizeof(num_of_classes) + 4 * sizeof(u2) * num_of_classes,
        "Wrong InnerClasses attribute length in class file %s", CHECK);
    }
  }

  if (_max_bootstrap_specifier_index >= 0) {
    guarantee_property(parsed_bootstrap_methods_attribute,
                       "Missing BootstrapMethods attribute in class file %s", CHECK);
  }
}

void ClassFileParser::apply_parsed_class_attributes(InstanceKlass* k) {
  assert(k != NULL, "invariant");

  if (_synthetic_flag)
    k->set_is_synthetic();
  if (_sourcefile_index != 0) {
    k->set_source_file_name_index(_sourcefile_index);
  }
  if (_generic_signature_index != 0) {
    k->set_generic_signature_index(_generic_signature_index);
  }
  if (_sde_buffer != NULL) {
    k->set_source_debug_extension(_sde_buffer, _sde_length);
  }
}

// Create the Annotations object that will
// hold the annotations array for the Klass.
void ClassFileParser::create_combined_annotations(TRAPS) {
    if (_annotations == NULL &&
        _type_annotations == NULL &&
        _fields_annotations == NULL &&
        _fields_type_annotations == NULL) {
      // Don't create the Annotations object unnecessarily.
      return;
    }

    Annotations* const annotations = Annotations::allocate(_loader_data, CHECK);
    annotations->set_class_annotations(_annotations);
    annotations->set_class_type_annotations(_type_annotations);
    annotations->set_fields_annotations(_fields_annotations);
    annotations->set_fields_type_annotations(_fields_type_annotations);

    // This is the Annotations object that will be
    // assigned to InstanceKlass being constructed.
    _combined_annotations = annotations;

    // The annotations arrays below has been transfered the
    // _combined_annotations so these fields can now be cleared.
    _annotations             = NULL;
    _type_annotations        = NULL;
    _fields_annotations      = NULL;
    _fields_type_annotations = NULL;
}

// Transfer ownership of metadata allocated to the InstanceKlass.
void ClassFileParser::apply_parsed_class_metadata(
                                            InstanceKlass* this_klass,
                                            int java_fields_count, TRAPS) {
  assert(this_klass != NULL, "invariant");

  _cp->set_pool_holder(this_klass);
  this_klass->set_constants(_cp);
  this_klass->set_fields(_fields, java_fields_count);
  this_klass->set_methods(_methods);
  this_klass->set_inner_classes(_inner_classes);
  this_klass->set_local_interfaces(_local_interfaces);
  this_klass->set_transitive_interfaces(_transitive_interfaces);
  this_klass->set_annotations(_combined_annotations);

  // Clear out these fields so they don't get deallocated by the destructor
  clear_class_metadata();
}

AnnotationArray* ClassFileParser::assemble_annotations(const u1* const runtime_visible_annotations,
                                                       int runtime_visible_annotations_length,
                                                       const u1* const runtime_invisible_annotations,
                                                       int runtime_invisible_annotations_length,
                                                       TRAPS) {
  AnnotationArray* annotations = NULL;
  if (runtime_visible_annotations != NULL ||
      runtime_invisible_annotations != NULL) {
    annotations = MetadataFactory::new_array<u1>(_loader_data,
                                          runtime_visible_annotations_length +
                                          runtime_invisible_annotations_length,
                                          CHECK_(annotations));
    if (runtime_visible_annotations != NULL) {
      for (int i = 0; i < runtime_visible_annotations_length; i++) {
        annotations->at_put(i, runtime_visible_annotations[i]);
      }
    }
    if (runtime_invisible_annotations != NULL) {
      for (int i = 0; i < runtime_invisible_annotations_length; i++) {
        int append = runtime_visible_annotations_length+i;
        annotations->at_put(append, runtime_invisible_annotations[i]);
      }
    }
  }
  return annotations;
}

const InstanceKlass* ClassFileParser::parse_super_class(ConstantPool* const cp,
                                                        const int super_class_index,
                                                        const bool need_verify,
                                                        TRAPS) {
  assert(cp != NULL, "invariant");
  const InstanceKlass* super_klass = NULL;

  if (super_class_index == 0) {
    check_property(_class_name == vmSymbols::java_lang_Object(),
                   "Invalid superclass index %u in class file %s",
                   super_class_index,
                   CHECK_NULL);
  } else {
    check_property(valid_klass_reference_at(super_class_index),
                   "Invalid superclass index %u in class file %s",
                   super_class_index,
                   CHECK_NULL);
    // The class name should be legal because it is checked when parsing constant pool.
    // However, make sure it is not an array type.
    bool is_array = false;
    if (cp->tag_at(super_class_index).is_klass()) {
      super_klass = InstanceKlass::cast(cp->resolved_klass_at(super_class_index));
      if (need_verify)
        is_array = super_klass->is_array_klass();
    } else if (need_verify) {
      is_array = (cp->klass_name_at(super_class_index)->byte_at(0) == JVM_SIGNATURE_ARRAY);
    }
    if (need_verify) {
      guarantee_property(!is_array,
                        "Bad superclass name in class file %s", CHECK_NULL);
    }
  }
  return super_klass;
}

static unsigned int compute_oop_map_count(const InstanceKlass* super,
                                          unsigned int nonstatic_oop_map_count,
                                          int first_nonstatic_oop_offset) {

  unsigned int map_count =
    NULL == super ? 0 : super->nonstatic_oop_map_count();
  if (nonstatic_oop_map_count > 0) {
    // We have oops to add to map
    if (map_count == 0) {
      map_count = nonstatic_oop_map_count;
    }
    else {
      // Check whether we should add a new map block or whether the last one can
      // be extended
      const OopMapBlock* const first_map = super->start_of_nonstatic_oop_maps();
      const OopMapBlock* const last_map = first_map + map_count - 1;

      const int next_offset = last_map->offset() + last_map->count() * heapOopSize;
      if (next_offset == first_nonstatic_oop_offset) {
        // There is no gap bettwen superklass's last oop field and first
        // local oop field, merge maps.
        nonstatic_oop_map_count -= 1;
      }
      else {
        // Superklass didn't end with a oop field, add extra maps
        assert(next_offset < first_nonstatic_oop_offset, "just checking");
      }
      map_count += nonstatic_oop_map_count;
    }
  }
  return map_count;
}

#ifndef PRODUCT
static void print_field_layout(const Symbol* name,
                               Array<u2>* fields,
                               constantPoolHandle cp,
                               int instance_size,
                               int instance_fields_start,
                               int instance_fields_end,
                               int static_fields_end) {

  assert(name != NULL, "invariant");

  tty->print("%s: field layout\n", name->as_klass_external_name());
  tty->print("  @%3d %s\n", instance_fields_start, "--- instance fields start ---");
  for (AllFieldStream fs(fields, cp); !fs.done(); fs.next()) {
    if (!fs.access_flags().is_static()) {
      tty->print("  @%3d \"%s\" %s\n",
        fs.offset(),
        fs.name()->as_klass_external_name(),
        fs.signature()->as_klass_external_name());
    }
  }
  tty->print("  @%3d %s\n", instance_fields_end, "--- instance fields end ---");
  tty->print("  @%3d %s\n", instance_size * wordSize, "--- instance ends ---");
  tty->print("  @%3d %s\n", InstanceMirrorKlass::offset_of_static_fields(), "--- static fields start ---");
  for (AllFieldStream fs(fields, cp); !fs.done(); fs.next()) {
    if (fs.access_flags().is_static()) {
      tty->print("  @%3d \"%s\" %s\n",
        fs.offset(),
        fs.name()->as_klass_external_name(),
        fs.signature()->as_klass_external_name());
    }
  }
  tty->print("  @%3d %s\n", static_fields_end, "--- static fields end ---");
  tty->print("\n");
}
#endif

// Values needed for oopmap and InstanceKlass creation
class ClassFileParser::FieldLayoutInfo : public ResourceObj {
 public:
  int*          nonstatic_oop_offsets;
  unsigned int* nonstatic_oop_counts;
  unsigned int  nonstatic_oop_map_count;
  unsigned int  total_oop_map_count;
  int           instance_size;
  int           nonstatic_field_size;
  int           static_field_size;
  bool          has_nonstatic_fields;
};

// Layout fields and fill in FieldLayoutInfo.  Could use more refactoring!
void ClassFileParser::layout_fields(ConstantPool* cp,
                                    const FieldAllocationCount* fac,
                                    const ClassAnnotationCollector* parsed_annotations,
                                    FieldLayoutInfo* info,
                                    TRAPS) {

  assert(cp != NULL, "invariant");

  // Field size and offset computation
  int nonstatic_field_size = _super_klass == NULL ? 0 :
                               _super_klass->nonstatic_field_size();

  // Count the contended fields by type.
  //
  // We ignore static fields, because @Contended is not supported for them.
  // The layout code below will also ignore the static fields.
  int nonstatic_contended_count = 0;
  FieldAllocationCount fac_contended;
  for (AllFieldStream fs(_fields, cp); !fs.done(); fs.next()) {
    FieldAllocationType atype = (FieldAllocationType) fs.allocation_type();
    if (fs.is_contended()) {
      fac_contended.count[atype]++;
      if (!fs.access_flags().is_static()) {
        nonstatic_contended_count++;
      }
    }
  }


  // Calculate the starting byte offsets
  int next_static_oop_offset    = InstanceMirrorKlass::offset_of_static_fields();
  int next_static_double_offset = next_static_oop_offset +
                                      ((fac->count[STATIC_OOP]) * heapOopSize);
  if ( fac->count[STATIC_DOUBLE] &&
       (Universe::field_type_should_be_aligned(T_DOUBLE) ||
        Universe::field_type_should_be_aligned(T_LONG)) ) {
    next_static_double_offset = align_size_up(next_static_double_offset, BytesPerLong);
  }

  int next_static_word_offset   = next_static_double_offset +
                                    ((fac->count[STATIC_DOUBLE]) * BytesPerLong);
  int next_static_short_offset  = next_static_word_offset +
                                    ((fac->count[STATIC_WORD]) * BytesPerInt);
  int next_static_byte_offset   = next_static_short_offset +
                                  ((fac->count[STATIC_SHORT]) * BytesPerShort);

  int nonstatic_fields_start  = instanceOopDesc::base_offset_in_bytes() +
                                nonstatic_field_size * heapOopSize;

  int next_nonstatic_field_offset = nonstatic_fields_start;

  const bool is_contended_class     = parsed_annotations->is_contended();

  // Class is contended, pad before all the fields
  if (is_contended_class) {
    next_nonstatic_field_offset += ContendedPaddingWidth;
  }

  // Compute the non-contended fields count.
  // The packing code below relies on these counts to determine if some field
  // can be squeezed into the alignment gap. Contended fields are obviously
  // exempt from that.
  unsigned int nonstatic_double_count = fac->count[NONSTATIC_DOUBLE] - fac_contended.count[NONSTATIC_DOUBLE];
  unsigned int nonstatic_word_count   = fac->count[NONSTATIC_WORD]   - fac_contended.count[NONSTATIC_WORD];
  unsigned int nonstatic_short_count  = fac->count[NONSTATIC_SHORT]  - fac_contended.count[NONSTATIC_SHORT];
  unsigned int nonstatic_byte_count   = fac->count[NONSTATIC_BYTE]   - fac_contended.count[NONSTATIC_BYTE];
  unsigned int nonstatic_oop_count    = fac->count[NONSTATIC_OOP]    - fac_contended.count[NONSTATIC_OOP];

  // Total non-static fields count, including every contended field
  unsigned int nonstatic_fields_count = fac->count[NONSTATIC_DOUBLE] + fac->count[NONSTATIC_WORD] +
                                        fac->count[NONSTATIC_SHORT] + fac->count[NONSTATIC_BYTE] +
                                        fac->count[NONSTATIC_OOP];

  const bool super_has_nonstatic_fields =
          (_super_klass != NULL && _super_klass->has_nonstatic_fields());
  const bool has_nonstatic_fields =
    super_has_nonstatic_fields || (nonstatic_fields_count != 0);


  // Prepare list of oops for oop map generation.
  //
  // "offset" and "count" lists are describing the set of contiguous oop
  // regions. offset[i] is the start of the i-th region, which then has
  // count[i] oops following. Before we know how many regions are required,
  // we pessimistically allocate the maps to fit all the oops into the
  // distinct regions.
  //
  // TODO: We add +1 to always allocate non-zero resource arrays; we need
  // to figure out if we still need to do this.
  unsigned int nonstatic_oop_map_count = 0;
  unsigned int max_nonstatic_oop_maps  = fac->count[NONSTATIC_OOP] + 1;

  int* nonstatic_oop_offsets = NEW_RESOURCE_ARRAY_IN_THREAD(
            THREAD, int, max_nonstatic_oop_maps);
  unsigned int* const nonstatic_oop_counts  = NEW_RESOURCE_ARRAY_IN_THREAD(
            THREAD, unsigned int, max_nonstatic_oop_maps);

  int first_nonstatic_oop_offset = 0; // will be set for first oop field

  bool compact_fields   = CompactFields;
  int allocation_style = FieldsAllocationStyle;
  if( allocation_style < 0 || allocation_style > 2 ) { // Out of range?
    assert(false, "0 <= FieldsAllocationStyle <= 2");
    allocation_style = 1; // Optimistic
  }

  // The next classes have predefined hard-coded fields offsets
  // (see in JavaClasses::compute_hard_coded_offsets()).
  // Use default fields allocation order for them.
  if( (allocation_style != 0 || compact_fields ) && _loader_data->class_loader() == NULL &&
      (_class_name == vmSymbols::java_lang_AssertionStatusDirectives() ||
       _class_name == vmSymbols::java_lang_Class() ||
       _class_name == vmSymbols::java_lang_ClassLoader() ||
       _class_name == vmSymbols::java_lang_ref_Reference() ||
       _class_name == vmSymbols::java_lang_ref_SoftReference() ||
       _class_name == vmSymbols::java_lang_StackTraceElement() ||
       _class_name == vmSymbols::java_lang_String() ||
       _class_name == vmSymbols::java_lang_Throwable() ||
       _class_name == vmSymbols::java_lang_Boolean() ||
       _class_name == vmSymbols::java_lang_Character() ||
       _class_name == vmSymbols::java_lang_Float() ||
       _class_name == vmSymbols::java_lang_Double() ||
       _class_name == vmSymbols::java_lang_Byte() ||
       _class_name == vmSymbols::java_lang_Short() ||
       _class_name == vmSymbols::java_lang_Integer() ||
       _class_name == vmSymbols::java_lang_Long())) {
    allocation_style = 0;     // Allocate oops first
    compact_fields   = false; // Don't compact fields
  }

  int next_nonstatic_oop_offset = 0;
  int next_nonstatic_double_offset = 0;

  // Rearrange fields for a given allocation style
  if( allocation_style == 0 ) {
    // Fields order: oops, longs/doubles, ints, shorts/chars, bytes, padded fields
    next_nonstatic_oop_offset    = next_nonstatic_field_offset;
    next_nonstatic_double_offset = next_nonstatic_oop_offset +
                                    (nonstatic_oop_count * heapOopSize);
  } else if( allocation_style == 1 ) {
    // Fields order: longs/doubles, ints, shorts/chars, bytes, oops, padded fields
    next_nonstatic_double_offset = next_nonstatic_field_offset;
  } else if( allocation_style == 2 ) {
    // Fields allocation: oops fields in super and sub classes are together.
    if( nonstatic_field_size > 0 && _super_klass != NULL &&
        _super_klass->nonstatic_oop_map_size() > 0 ) {
      const unsigned int map_count = _super_klass->nonstatic_oop_map_count();
      const OopMapBlock* const first_map = _super_klass->start_of_nonstatic_oop_maps();
      const OopMapBlock* const last_map = first_map + map_count - 1;
      const int next_offset = last_map->offset() + (last_map->count() * heapOopSize);
      if (next_offset == next_nonstatic_field_offset) {
        allocation_style = 0;   // allocate oops first
        next_nonstatic_oop_offset    = next_nonstatic_field_offset;
        next_nonstatic_double_offset = next_nonstatic_oop_offset +
                                       (nonstatic_oop_count * heapOopSize);
      }
    }
    if( allocation_style == 2 ) {
      allocation_style = 1;     // allocate oops last
      next_nonstatic_double_offset = next_nonstatic_field_offset;
    }
  } else {
    ShouldNotReachHere();
  }

  int nonstatic_oop_space_count   = 0;
  int nonstatic_word_space_count  = 0;
  int nonstatic_short_space_count = 0;
  int nonstatic_byte_space_count  = 0;
  int nonstatic_oop_space_offset = 0;
  int nonstatic_word_space_offset = 0;
  int nonstatic_short_space_offset = 0;
  int nonstatic_byte_space_offset = 0;

  // Try to squeeze some of the fields into the gaps due to
  // long/double alignment.
  if (nonstatic_double_count > 0) {
    int offset = next_nonstatic_double_offset;
    next_nonstatic_double_offset = align_size_up(offset, BytesPerLong);
    if (compact_fields && offset != next_nonstatic_double_offset) {
      // Allocate available fields into the gap before double field.
      int length = next_nonstatic_double_offset - offset;
      assert(length == BytesPerInt, "");
      nonstatic_word_space_offset = offset;
      if (nonstatic_word_count > 0) {
        nonstatic_word_count      -= 1;
        nonstatic_word_space_count = 1; // Only one will fit
        length -= BytesPerInt;
        offset += BytesPerInt;
      }
      nonstatic_short_space_offset = offset;
      while (length >= BytesPerShort && nonstatic_short_count > 0) {
        nonstatic_short_count       -= 1;
        nonstatic_short_space_count += 1;
        length -= BytesPerShort;
        offset += BytesPerShort;
      }
      nonstatic_byte_space_offset = offset;
      while (length > 0 && nonstatic_byte_count > 0) {
        nonstatic_byte_count       -= 1;
        nonstatic_byte_space_count += 1;
        length -= 1;
      }
      // Allocate oop field in the gap if there are no other fields for that.
      nonstatic_oop_space_offset = offset;
      if (length >= heapOopSize && nonstatic_oop_count > 0 &&
          allocation_style != 0) { // when oop fields not first
        nonstatic_oop_count      -= 1;
        nonstatic_oop_space_count = 1; // Only one will fit
        length -= heapOopSize;
        offset += heapOopSize;
      }
    }
  }

  int next_nonstatic_word_offset = next_nonstatic_double_offset +
                                     (nonstatic_double_count * BytesPerLong);
  int next_nonstatic_short_offset = next_nonstatic_word_offset +
                                      (nonstatic_word_count * BytesPerInt);
  int next_nonstatic_byte_offset = next_nonstatic_short_offset +
                                     (nonstatic_short_count * BytesPerShort);
  int next_nonstatic_padded_offset = next_nonstatic_byte_offset +
                                       nonstatic_byte_count;

  // let oops jump before padding with this allocation style
  if( allocation_style == 1 ) {
    next_nonstatic_oop_offset = next_nonstatic_padded_offset;
    if( nonstatic_oop_count > 0 ) {
      next_nonstatic_oop_offset = align_size_up(next_nonstatic_oop_offset, heapOopSize);
    }
    next_nonstatic_padded_offset = next_nonstatic_oop_offset + (nonstatic_oop_count * heapOopSize);
  }

  // Iterate over fields again and compute correct offsets.
  // The field allocation type was temporarily stored in the offset slot.
  // oop fields are located before non-oop fields (static and non-static).
  for (AllFieldStream fs(_fields, cp); !fs.done(); fs.next()) {

    // skip already laid out fields
    if (fs.is_offset_set()) continue;

    // contended instance fields are handled below
    if (fs.is_contended() && !fs.access_flags().is_static()) continue;

    int real_offset = 0;
    const FieldAllocationType atype = (const FieldAllocationType) fs.allocation_type();

    // pack the rest of the fields
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

        // Record this oop in the oop maps
        if( nonstatic_oop_map_count > 0 &&
            nonstatic_oop_offsets[nonstatic_oop_map_count - 1] ==
            real_offset -
            int(nonstatic_oop_counts[nonstatic_oop_map_count - 1]) *
            heapOopSize ) {
          // This oop is adjacent to the previous one, add to current oop map
          assert(nonstatic_oop_map_count - 1 < max_nonstatic_oop_maps, "range check");
          nonstatic_oop_counts[nonstatic_oop_map_count - 1] += 1;
        } else {
          // This oop is not adjacent to the previous one, create new oop map
          assert(nonstatic_oop_map_count < max_nonstatic_oop_maps, "range check");
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
      case NONSTATIC_DOUBLE:
        real_offset = next_nonstatic_double_offset;
        next_nonstatic_double_offset += BytesPerLong;
        break;
      default:
        ShouldNotReachHere();
    }
    fs.set_offset(real_offset);
  }


  // Handle the contended cases.
  //
  // Each contended field should not intersect the cache line with another contended field.
  // In the absence of alignment information, we end up with pessimistically separating
  // the fields with full-width padding.
  //
  // Additionally, this should not break alignment for the fields, so we round the alignment up
  // for each field.
  if (nonstatic_contended_count > 0) {

    // if there is at least one contended field, we need to have pre-padding for them
    next_nonstatic_padded_offset += ContendedPaddingWidth;

    // collect all contended groups
    BitMap bm(cp->size());
    for (AllFieldStream fs(_fields, cp); !fs.done(); fs.next()) {
      // skip already laid out fields
      if (fs.is_offset_set()) continue;

      if (fs.is_contended()) {
        bm.set_bit(fs.contended_group());
      }
    }

    int current_group = -1;
    while ((current_group = (int)bm.get_next_one_offset(current_group + 1)) != (int)bm.size()) {

      for (AllFieldStream fs(_fields, cp); !fs.done(); fs.next()) {

        // skip already laid out fields
        if (fs.is_offset_set()) continue;

        // skip non-contended fields and fields from different group
        if (!fs.is_contended() || (fs.contended_group() != current_group)) continue;

        // handle statics below
        if (fs.access_flags().is_static()) continue;

        int real_offset = 0;
        FieldAllocationType atype = (FieldAllocationType) fs.allocation_type();

        switch (atype) {
          case NONSTATIC_BYTE:
            next_nonstatic_padded_offset = align_size_up(next_nonstatic_padded_offset, 1);
            real_offset = next_nonstatic_padded_offset;
            next_nonstatic_padded_offset += 1;
            break;

          case NONSTATIC_SHORT:
            next_nonstatic_padded_offset = align_size_up(next_nonstatic_padded_offset, BytesPerShort);
            real_offset = next_nonstatic_padded_offset;
            next_nonstatic_padded_offset += BytesPerShort;
            break;

          case NONSTATIC_WORD:
            next_nonstatic_padded_offset = align_size_up(next_nonstatic_padded_offset, BytesPerInt);
            real_offset = next_nonstatic_padded_offset;
            next_nonstatic_padded_offset += BytesPerInt;
            break;

          case NONSTATIC_DOUBLE:
            next_nonstatic_padded_offset = align_size_up(next_nonstatic_padded_offset, BytesPerLong);
            real_offset = next_nonstatic_padded_offset;
            next_nonstatic_padded_offset += BytesPerLong;
            break;

          case NONSTATIC_OOP:
            next_nonstatic_padded_offset = align_size_up(next_nonstatic_padded_offset, heapOopSize);
            real_offset = next_nonstatic_padded_offset;
            next_nonstatic_padded_offset += heapOopSize;

            // Record this oop in the oop maps
            if( nonstatic_oop_map_count > 0 &&
                nonstatic_oop_offsets[nonstatic_oop_map_count - 1] ==
                real_offset -
                int(nonstatic_oop_counts[nonstatic_oop_map_count - 1]) *
                heapOopSize ) {
              // This oop is adjacent to the previous one, add to current oop map
              assert(nonstatic_oop_map_count - 1 < max_nonstatic_oop_maps, "range check");
              nonstatic_oop_counts[nonstatic_oop_map_count - 1] += 1;
            } else {
              // This oop is not adjacent to the previous one, create new oop map
              assert(nonstatic_oop_map_count < max_nonstatic_oop_maps, "range check");
              nonstatic_oop_offsets[nonstatic_oop_map_count] = real_offset;
              nonstatic_oop_counts [nonstatic_oop_map_count] = 1;
              nonstatic_oop_map_count += 1;
              if( first_nonstatic_oop_offset == 0 ) { // Undefined
                first_nonstatic_oop_offset = real_offset;
              }
            }
            break;

          default:
            ShouldNotReachHere();
        }

        if (fs.contended_group() == 0) {
          // Contended group defines the equivalence class over the fields:
          // the fields within the same contended group are not inter-padded.
          // The only exception is default group, which does not incur the
          // equivalence, and so requires intra-padding.
          next_nonstatic_padded_offset += ContendedPaddingWidth;
        }

        fs.set_offset(real_offset);
      } // for

      // Start laying out the next group.
      // Note that this will effectively pad the last group in the back;
      // this is expected to alleviate memory contention effects for
      // subclass fields and/or adjacent object.
      // If this was the default group, the padding is already in place.
      if (current_group != 0) {
        next_nonstatic_padded_offset += ContendedPaddingWidth;
      }
    }

    // handle static fields
  }

  // Entire class is contended, pad in the back.
  // This helps to alleviate memory contention effects for subclass fields
  // and/or adjacent object.
  if (is_contended_class) {
    next_nonstatic_padded_offset += ContendedPaddingWidth;
  }

  int notaligned_nonstatic_fields_end = next_nonstatic_padded_offset;

  int nonstatic_fields_end      = align_size_up(notaligned_nonstatic_fields_end, heapOopSize);
  int instance_end              = align_size_up(notaligned_nonstatic_fields_end, wordSize);
  int static_fields_end         = align_size_up(next_static_byte_offset, wordSize);

  int static_field_size         = (static_fields_end -
                                   InstanceMirrorKlass::offset_of_static_fields()) / wordSize;
  nonstatic_field_size          = nonstatic_field_size +
                                  (nonstatic_fields_end - nonstatic_fields_start) / heapOopSize;

  int instance_size             = align_object_size(instance_end / wordSize);

  assert(instance_size == align_object_size(align_size_up(
         (instanceOopDesc::base_offset_in_bytes() + nonstatic_field_size*heapOopSize),
          wordSize) / wordSize), "consistent layout helper value");

  // Invariant: nonstatic_field end/start should only change if there are
  // nonstatic fields in the class, or if the class is contended. We compare
  // against the non-aligned value, so that end alignment will not fail the
  // assert without actually having the fields.
  assert((notaligned_nonstatic_fields_end == nonstatic_fields_start) ||
         is_contended_class ||
         (nonstatic_fields_count > 0), "double-check nonstatic start/end");

  // Number of non-static oop map blocks allocated at end of klass.
  const unsigned int total_oop_map_count =
    compute_oop_map_count(_super_klass, nonstatic_oop_map_count,
                          first_nonstatic_oop_offset);

#ifndef PRODUCT
  if (PrintFieldLayout) {
    print_field_layout(_class_name,
          _fields,
          cp,
          instance_size,
          nonstatic_fields_start,
          nonstatic_fields_end,
          static_fields_end);
  }

#endif
  // Pass back information needed for InstanceKlass creation
  info->nonstatic_oop_offsets = nonstatic_oop_offsets;
  info->nonstatic_oop_counts = nonstatic_oop_counts;
  info->nonstatic_oop_map_count = nonstatic_oop_map_count;
  info->total_oop_map_count = total_oop_map_count;
  info->instance_size = instance_size;
  info->static_field_size = static_field_size;
  info->nonstatic_field_size = nonstatic_field_size;
  info->has_nonstatic_fields = has_nonstatic_fields;
}

static void fill_oop_maps(const InstanceKlass* k,
                          unsigned int nonstatic_oop_map_count,
                          const int* nonstatic_oop_offsets,
                          const unsigned int* nonstatic_oop_counts) {

  assert(k != NULL, "invariant");

  OopMapBlock* this_oop_map = k->start_of_nonstatic_oop_maps();
  const InstanceKlass* const super = k->superklass();
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


void ClassFileParser::set_precomputed_flags(InstanceKlass* ik) {
  assert(ik != NULL, "invariant");

  const Klass* const super = ik->super();

  // Check if this klass has an empty finalize method (i.e. one with return bytecode only),
  // in which case we don't have to register objects as finalizable
  if (!_has_empty_finalizer) {
    if (_has_finalizer ||
        (super != NULL && super->has_finalizer())) {
      ik->set_has_finalizer();
    }
  }

#ifdef ASSERT
  bool f = false;
  const Method* const m = ik->lookup_method(vmSymbols::finalize_method_name(),
                                           vmSymbols::void_method_signature());
  if (m != NULL && !m->is_empty_method()) {
      f = true;
  }

  // Spec doesn't prevent agent from redefinition of empty finalizer.
  // Despite the fact that it's generally bad idea and redefined finalizer
  // will not work as expected we shouldn't abort vm in this case
  if (!ik->has_redefined_this_or_super()) {
    assert(ik->has_finalizer() == f, "inconsistent has_finalizer");
  }
#endif

  // Check if this klass supports the java.lang.Cloneable interface
  if (SystemDictionary::Cloneable_klass_loaded()) {
    if (ik->is_subtype_of(SystemDictionary::Cloneable_klass())) {
      ik->set_is_cloneable();
    }
  }

  // Check if this klass has a vanilla default constructor
  if (super == NULL) {
    // java.lang.Object has empty default constructor
    ik->set_has_vanilla_constructor();
  } else {
    if (super->has_vanilla_constructor() &&
        _has_vanilla_constructor) {
      ik->set_has_vanilla_constructor();
    }
#ifdef ASSERT
    bool v = false;
    if (super->has_vanilla_constructor()) {
      const Method* const constructor =
        ik->find_method(vmSymbols::object_initializer_name(),
                       vmSymbols::void_method_signature());
      if (constructor != NULL && constructor->is_vanilla_constructor()) {
        v = true;
      }
    }
    assert(v == ik->has_vanilla_constructor(), "inconsistent has_vanilla_constructor");
#endif
  }

  // If it cannot be fast-path allocated, set a bit in the layout helper.
  // See documentation of InstanceKlass::can_be_fastpath_allocated().
  assert(ik->size_helper() > 0, "layout_helper is initialized");
  if ((!RegisterFinalizersAtInit && ik->has_finalizer())
      || ik->is_abstract() || ik->is_interface()
      || (ik->name() == vmSymbols::java_lang_Class() && ik->class_loader() == NULL)
      || ik->size_helper() >= FastAllocateSizeLimit) {
    // Forbid fast-path allocation.
    const jint lh = Klass::instance_layout_helper(ik->size_helper(), true);
    ik->set_layout_helper(lh);
  }
}

// Attach super classes and interface classes to class loader data
static void record_defined_class_dependencies(const InstanceKlass* defined_klass,
                                              TRAPS) {
  assert(defined_klass != NULL, "invariant");

  ClassLoaderData* const defining_loader_data = defined_klass->class_loader_data();
  if (defining_loader_data->is_the_null_class_loader_data()) {
      // Dependencies to null class loader data are implicit.
      return;
  } else {
    // add super class dependency
    Klass* const super = defined_klass->super();
    if (super != NULL) {
      defining_loader_data->record_dependency(super, CHECK);
    }

    // add super interface dependencies
    const Array<Klass*>* const local_interfaces = defined_klass->local_interfaces();
    if (local_interfaces != NULL) {
      const int length = local_interfaces->length();
      for (int i = 0; i < length; i++) {
        defining_loader_data->record_dependency(local_interfaces->at(i), CHECK);
      }
    }
  }
}

// utility methods for appending an array with check for duplicates

static void append_interfaces(GrowableArray<Klass*>* result,
                              const Array<Klass*>* const ifs) {
  // iterate over new interfaces
  for (int i = 0; i < ifs->length(); i++) {
    Klass* const e = ifs->at(i);
    assert(e->is_klass() && InstanceKlass::cast(e)->is_interface(), "just checking");
    // add new interface
    result->append_if_missing(e);
  }
}

static Array<Klass*>* compute_transitive_interfaces(const InstanceKlass* super,
                                                    Array<Klass*>* local_ifs,
                                                    ClassLoaderData* loader_data,
                                                    TRAPS) {
  assert(local_ifs != NULL, "invariant");
  assert(loader_data != NULL, "invariant");

  // Compute maximum size for transitive interfaces
  int max_transitive_size = 0;
  int super_size = 0;
  // Add superclass transitive interfaces size
  if (super != NULL) {
    super_size = super->transitive_interfaces()->length();
    max_transitive_size += super_size;
  }
  // Add local interfaces' super interfaces
  const int local_size = local_ifs->length();
  for (int i = 0; i < local_size; i++) {
    Klass* const l = local_ifs->at(i);
    max_transitive_size += InstanceKlass::cast(l)->transitive_interfaces()->length();
  }
  // Finally add local interfaces
  max_transitive_size += local_size;
  // Construct array
  if (max_transitive_size == 0) {
    // no interfaces, use canonicalized array
    return Universe::the_empty_klass_array();
  } else if (max_transitive_size == super_size) {
    // no new local interfaces added, share superklass' transitive interface array
    return super->transitive_interfaces();
  } else if (max_transitive_size == local_size) {
    // only local interfaces added, share local interface array
    return local_ifs;
  } else {
    ResourceMark rm;
    GrowableArray<Klass*>* const result = new GrowableArray<Klass*>(max_transitive_size);

    // Copy down from superclass
    if (super != NULL) {
      append_interfaces(result, super->transitive_interfaces());
    }

    // Copy down from local interfaces' superinterfaces
    for (int i = 0; i < local_size; i++) {
      Klass* const l = local_ifs->at(i);
      append_interfaces(result, InstanceKlass::cast(l)->transitive_interfaces());
    }
    // Finally add local interfaces
    append_interfaces(result, local_ifs);

    // length will be less than the max_transitive_size if duplicates were removed
    const int length = result->length();
    assert(length <= max_transitive_size, "just checking");
    Array<Klass*>* const new_result =
      MetadataFactory::new_array<Klass*>(loader_data, length, CHECK_NULL);
    for (int i = 0; i < length; i++) {
      Klass* const e = result->at(i);
      assert(e != NULL, "just checking");
      new_result->at_put(i, e);
    }
    return new_result;
  }
}

static void check_super_class_access(const InstanceKlass* this_klass, TRAPS) {
  assert(this_klass != NULL, "invariant");
  const Klass* const super = this_klass->super();
  if ((super != NULL) &&
      (!Reflection::verify_class_access(this_klass, super, false))) {
    ResourceMark rm(THREAD);
    Exceptions::fthrow(
      THREAD_AND_LOCATION,
      vmSymbols::java_lang_IllegalAccessError(),
      "class %s cannot access its superclass %s",
      this_klass->external_name(),
      super->external_name()
    );
    return;
  }
}


static void check_super_interface_access(const InstanceKlass* this_klass, TRAPS) {
  assert(this_klass != NULL, "invariant");
  const Array<Klass*>* const local_interfaces = this_klass->local_interfaces();
  const int lng = local_interfaces->length();
  for (int i = lng - 1; i >= 0; i--) {
    Klass* const k = local_interfaces->at(i);
    assert (k != NULL && k->is_interface(), "invalid interface");
    if (!Reflection::verify_class_access(this_klass, k, false)) {
      ResourceMark rm(THREAD);
      Exceptions::fthrow(
        THREAD_AND_LOCATION,
        vmSymbols::java_lang_IllegalAccessError(),
        "class %s cannot access its superinterface %s",
        this_klass->external_name(),
        k->external_name()
      );
      return;
    }
  }
}


static void check_final_method_override(const InstanceKlass* this_klass, TRAPS) {
  assert(this_klass != NULL, "invariant");
  const Array<Method*>* const methods = this_klass->methods();
  const int num_methods = methods->length();

  // go thru each method and check if it overrides a final method
  for (int index = 0; index < num_methods; index++) {
    const Method* const m = methods->at(index);

    // skip private, static, and <init> methods
    if ((!m->is_private() && !m->is_static()) &&
        (m->name() != vmSymbols::object_initializer_name())) {

      const Symbol* const name = m->name();
      const Symbol* const signature = m->signature();
      const Klass* k = this_klass->super();
      const Method* super_m = NULL;
      while (k != NULL) {
        // skip supers that don't have final methods.
        if (k->has_final_method()) {
          // lookup a matching method in the super class hierarchy
          super_m = InstanceKlass::cast(k)->lookup_method(name, signature);
          if (super_m == NULL) {
            break; // didn't find any match; get out
          }

          if (super_m->is_final() && !super_m->is_static() &&
              // matching method in super is final, and not static
              (Reflection::verify_field_access(this_klass,
                                               super_m->method_holder(),
                                               super_m->method_holder(),
                                               super_m->access_flags(), false))
            // this class can access super final method and therefore override
            ) {
            ResourceMark rm(THREAD);
            Exceptions::fthrow(
              THREAD_AND_LOCATION,
              vmSymbols::java_lang_VerifyError(),
              "class %s overrides final method %s.%s%s",
              this_klass->external_name(),
              super_m->method_holder()->external_name(),
              name->as_C_string(),
              signature->as_C_string()
            );
            return;
          }

          // continue to look from super_m's holder's super.
          k = super_m->method_holder()->super();
          continue;
        }

        k = k->super();
      }
    }
  }
}


// assumes that this_klass is an interface
static void check_illegal_static_method(const InstanceKlass* this_klass, TRAPS) {
  assert(this_klass != NULL, "invariant");
  assert(this_klass->is_interface(), "not an interface");
  const Array<Method*>* methods = this_klass->methods();
  const int num_methods = methods->length();

  for (int index = 0; index < num_methods; index++) {
    const Method* const m = methods->at(index);
    // if m is static and not the init method, throw a verify error
    if ((m->is_static()) && (m->name() != vmSymbols::class_initializer_name())) {
      ResourceMark rm(THREAD);
      Exceptions::fthrow(
        THREAD_AND_LOCATION,
        vmSymbols::java_lang_VerifyError(),
        "Illegal static method %s in interface %s",
        m->name()->as_C_string(),
        this_klass->external_name()
      );
      return;
    }
  }
}

// utility methods for format checking

void ClassFileParser::verify_legal_class_modifiers(jint flags, TRAPS) const {
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
      vmSymbols::java_lang_ClassFormatError(),
      "Illegal class modifiers in class %s: 0x%X",
      _class_name->as_C_string(), flags
    );
    return;
  }
}

static bool has_illegal_visibility(jint flags) {
  const bool is_public    = (flags & JVM_ACC_PUBLIC)    != 0;
  const bool is_protected = (flags & JVM_ACC_PROTECTED) != 0;
  const bool is_private   = (flags & JVM_ACC_PRIVATE)   != 0;

  return ((is_public && is_protected) ||
          (is_public && is_private) ||
          (is_protected && is_private));
}

static bool is_supported_version(u2 major, u2 minor){
  const u2 max_version = JAVA_MAX_SUPPORTED_VERSION;
  return (major >= JAVA_MIN_SUPPORTED_VERSION) &&
         (major <= max_version) &&
         ((major != max_version) ||
          (minor <= JAVA_MAX_SUPPORTED_MINOR_VERSION));
}

void ClassFileParser::verify_legal_field_modifiers(jint flags,
                                                   bool is_interface,
                                                   TRAPS) const {
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
      vmSymbols::java_lang_ClassFormatError(),
      "Illegal field modifiers in class %s: 0x%X",
      _class_name->as_C_string(), flags);
    return;
  }
}

void ClassFileParser::verify_legal_method_modifiers(jint flags,
                                                    bool is_interface,
                                                    const Symbol* name,
                                                    TRAPS) const {
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
  const bool is_protected    = (flags & JVM_ACC_PROTECTED)    != 0;
  const bool major_gte_15    = _major_version >= JAVA_1_5_VERSION;
  const bool major_gte_8     = _major_version >= JAVA_8_VERSION;
  const bool is_initializer  = (name == vmSymbols::object_initializer_name());

  bool is_illegal = false;

  if (is_interface) {
    if (major_gte_8) {
      // Class file version is JAVA_8_VERSION or later Methods of
      // interfaces may set any of the flags except ACC_PROTECTED,
      // ACC_FINAL, ACC_NATIVE, and ACC_SYNCHRONIZED; they must
      // have exactly one of the ACC_PUBLIC or ACC_PRIVATE flags set.
      if ((is_public == is_private) || /* Only one of private and public should be true - XNOR */
          (is_native || is_protected || is_final || is_synchronized) ||
          // If a specific method of a class or interface has its
          // ACC_ABSTRACT flag set, it must not have any of its
          // ACC_FINAL, ACC_NATIVE, ACC_PRIVATE, ACC_STATIC,
          // ACC_STRICT, or ACC_SYNCHRONIZED flags set.  No need to
          // check for ACC_FINAL, ACC_NATIVE or ACC_SYNCHRONIZED as
          // those flags are illegal irrespective of ACC_ABSTRACT being set or not.
          (is_abstract && (is_private || is_static || is_strict))) {
        is_illegal = true;
      }
    } else if (major_gte_15) {
      // Class file version in the interval [JAVA_1_5_VERSION, JAVA_8_VERSION)
      if (!is_public || is_static || is_final || is_synchronized ||
          is_native || !is_abstract || is_strict) {
        is_illegal = true;
      }
    } else {
      // Class file version is pre-JAVA_1_5_VERSION
      if (!is_public || is_static || is_final || is_native || !is_abstract) {
        is_illegal = true;
      }
    }
  } else { // not interface
    if (has_illegal_visibility(flags)) {
      is_illegal = true;
    } else {
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
      }
    }
  }

  if (is_illegal) {
    ResourceMark rm(THREAD);
    Exceptions::fthrow(
      THREAD_AND_LOCATION,
      vmSymbols::java_lang_ClassFormatError(),
      "Method %s in class %s has illegal modifiers: 0x%X",
      name->as_C_string(), _class_name->as_C_string(), flags);
    return;
  }
}

void ClassFileParser::verify_legal_utf8(const unsigned char* buffer,
                                        int length,
                                        TRAPS) const {
  assert(_need_verify, "only called when _need_verify is true");
  int i = 0;
  const int count = length >> 2;
  for (int k=0; k<count; k++) {
    unsigned char b0 = buffer[i];
    unsigned char b1 = buffer[i+1];
    unsigned char b2 = buffer[i+2];
    unsigned char b3 = buffer[i+3];
    // For an unsigned char v,
    // (v | v - 1) is < 128 (highest bit 0) for 0 < v < 128;
    // (v | v - 1) is >= 128 (highest bit 1) for v == 0 or v >= 128.
    const unsigned char res = b0 | b0 - 1 |
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

// Unqualified names may not contain the characters '.', ';', '[', or '/'.
// Method names also may not contain the characters '<' or '>', unless <init>
// or <clinit>.  Note that method names may not be <init> or <clinit> in this
// method.  Because these names have been checked as special cases before
// calling this method in verify_legal_method_name.
static bool verify_unqualified_name(const char* name,
                                    unsigned int length,
                                    int type) {
  for (const char* p = name; p != name + length;) {
    jchar ch = *p;
    if (ch < 128) {
      p++;
      if (ch == '.' || ch == ';' || ch == '[') {
        return false;   // do not permit '.', ';', or '['
      }
      if (type != LegalClass && ch == '/') {
        return false;   // do not permit '/' unless it's class name
      }
      if (type == LegalMethod && (ch == '<' || ch == '>')) {
        return false;   // do not permit '<' or '>' in method names
      }
    }
    else {
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
static const char* skip_over_field_name(const char* name,
                                        bool slash_ok,
                                        unsigned int length) {
  const char* p;
  jboolean last_is_slash = false;
  jboolean not_first_ch = false;

  for (p = name; p != name + length; not_first_ch = true) {
    const char* old_p = p;
    jchar ch = *p;
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
    }
    else {
      jint unicode_ch;
      char* tmp_p = UTF8::next_character(p, &unicode_ch);
      p = tmp_p;
      last_is_slash = false;
      // Check if ch is Java identifier start or is Java identifier part
      // 4672820: call java.lang.Character methods directly without generating separate tables.
      EXCEPTION_MARK;

      // return value
      JavaValue result(T_BOOLEAN);
      // Set up the arguments to isJavaIdentifierStart and isJavaIdentifierPart
      JavaCallArguments args;
      args.push_int(unicode_ch);

      // public static boolean isJavaIdentifierStart(char ch);
      JavaCalls::call_static(&result,
        SystemDictionary::Character_klass(),
        vmSymbols::isJavaIdentifierStart_name(),
        vmSymbols::int_bool_signature(),
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
          SystemDictionary::Character_klass(),
          vmSymbols::isJavaIdentifierPart_name(),
          vmSymbols::int_bool_signature(),
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
const char* ClassFileParser::skip_over_field_signature(const char* signature,
                                                       bool void_ok,
                                                       unsigned int length,
                                                       TRAPS) const {
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
        const char* const p = skip_over_field_name(signature + 1, true, --length);

        // The next character better be a semicolon
        if (p && (p - signature) > 1 && p[0] == ';') {
          return p + 1;
        }
      }
      else {
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

// Checks if name is a legal class name.
void ClassFileParser::verify_legal_class_name(const Symbol* name, TRAPS) const {
  if (!_need_verify || _relax_verify) { return; }

  char buf[fixed_buffer_size];
  char* bytes = name->as_utf8_flexible_buffer(THREAD, buf, fixed_buffer_size);
  unsigned int length = name->utf8_length();
  bool legal = false;

  if (length > 0) {
    const char* p;
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
    assert(_class_name != NULL, "invariant");
    Exceptions::fthrow(
      THREAD_AND_LOCATION,
      vmSymbols::java_lang_ClassFormatError(),
      "Illegal class name \"%s\" in class file %s", bytes,
      _class_name->as_C_string()
    );
    return;
  }
}

// Checks if name is a legal field name.
void ClassFileParser::verify_legal_field_name(const Symbol* name, TRAPS) const {
  if (!_need_verify || _relax_verify) { return; }

  char buf[fixed_buffer_size];
  char* bytes = name->as_utf8_flexible_buffer(THREAD, buf, fixed_buffer_size);
  unsigned int length = name->utf8_length();
  bool legal = false;

  if (length > 0) {
    if (_major_version < JAVA_1_5_VERSION) {
      if (bytes[0] != '<') {
        const char* p = skip_over_field_name(bytes, false, length);
        legal = (p != NULL) && ((p - bytes) == (int)length);
      }
    } else {
      // 4881221: relax the constraints based on JSR202 spec
      legal = verify_unqualified_name(bytes, length, LegalField);
    }
  }

  if (!legal) {
    ResourceMark rm(THREAD);
    assert(_class_name != NULL, "invariant");
    Exceptions::fthrow(
      THREAD_AND_LOCATION,
      vmSymbols::java_lang_ClassFormatError(),
      "Illegal field name \"%s\" in class %s", bytes,
      _class_name->as_C_string()
    );
    return;
  }
}

// Checks if name is a legal method name.
void ClassFileParser::verify_legal_method_name(const Symbol* name, TRAPS) const {
  if (!_need_verify || _relax_verify) { return; }

  assert(name != NULL, "method name is null");
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
      const char* p;
      p = skip_over_field_name(bytes, false, length);
      legal = (p != NULL) && ((p - bytes) == (int)length);
    } else {
      // 4881221: relax the constraints based on JSR202 spec
      legal = verify_unqualified_name(bytes, length, LegalMethod);
    }
  }

  if (!legal) {
    ResourceMark rm(THREAD);
    assert(_class_name != NULL, "invariant");
    Exceptions::fthrow(
      THREAD_AND_LOCATION,
      vmSymbols::java_lang_ClassFormatError(),
      "Illegal method name \"%s\" in class %s", bytes,
      _class_name->as_C_string()
    );
    return;
  }
}


// Checks if signature is a legal field signature.
void ClassFileParser::verify_legal_field_signature(const Symbol* name,
                                                   const Symbol* signature,
                                                   TRAPS) const {
  if (!_need_verify) { return; }

  char buf[fixed_buffer_size];
  const char* const bytes = signature->as_utf8_flexible_buffer(THREAD, buf, fixed_buffer_size);
  const unsigned int length = signature->utf8_length();
  const char* const p = skip_over_field_signature(bytes, false, length, CHECK);

  if (p == NULL || (p - bytes) != (int)length) {
    throwIllegalSignature("Field", name, signature, CHECK);
  }
}

// Checks if signature is a legal method signature.
// Returns number of parameters
int ClassFileParser::verify_legal_method_signature(const Symbol* name,
                                                   const Symbol* signature,
                                                   TRAPS) const {
  if (!_need_verify) {
    // make sure caller's args_size will be less than 0 even for non-static
    // method so it will be recomputed in compute_size_of_parameters().
    return -2;
  }

  // Class initializers cannot have args for class format version >= 51.
  if (name == vmSymbols::class_initializer_name() &&
      signature != vmSymbols::void_method_signature() &&
      _major_version >= JAVA_7_VERSION) {
    throwIllegalSignature("Method", name, signature, CHECK_0);
    return 0;
  }

  unsigned int args_size = 0;
  char buf[fixed_buffer_size];
  const char* p = signature->as_utf8_flexible_buffer(THREAD, buf, fixed_buffer_size);
  unsigned int length = signature->utf8_length();
  const char* nextp;

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
  throwIllegalSignature("Method", name, signature, CHECK_0);
  return 0;
}

int ClassFileParser::static_field_size() const {
  assert(_field_info != NULL, "invariant");
  return _field_info->static_field_size;
}

int ClassFileParser::total_oop_map_count() const {
  assert(_field_info != NULL, "invariant");
  return _field_info->total_oop_map_count;
}

jint ClassFileParser::layout_size() const {
  assert(_field_info != NULL, "invariant");
  return _field_info->instance_size;
}

static void check_methods_for_intrinsics(const InstanceKlass* ik,
                                         const Array<Method*>* methods) {
  assert(ik != NULL, "invariant");
  assert(methods != NULL, "invariant");

  // Set up Method*::intrinsic_id as soon as we know the names of methods.
  // (We used to do this lazily, but now we query it in Rewriter,
  // which is eagerly done for every method, so we might as well do it now,
  // when everything is fresh in memory.)
  const vmSymbols::SID klass_id = Method::klass_id_for_intrinsics(ik);

  if (klass_id != vmSymbols::NO_SID) {
    for (int j = 0; j < methods->length(); ++j) {
      Method* method = methods->at(j);
      method->init_intrinsic_id();

      if (CheckIntrinsics) {
        // Check if an intrinsic is defined for method 'method',
        // but the method is not annotated with @HotSpotIntrinsicCandidate.
        if (method->intrinsic_id() != vmIntrinsics::_none &&
            !method->intrinsic_candidate()) {
              tty->print("Compiler intrinsic is defined for method [%s], "
              "but the method is not annotated with @HotSpotIntrinsicCandidate.%s",
              method->name_and_sig_as_C_string(),
              NOT_DEBUG(" Method will not be inlined.") DEBUG_ONLY(" Exiting.")
            );
          tty->cr();
          DEBUG_ONLY(vm_exit(1));
        }
        // Check is the method 'method' is annotated with @HotSpotIntrinsicCandidate,
        // but there is no intrinsic available for it.
        if (method->intrinsic_candidate() &&
          method->intrinsic_id() == vmIntrinsics::_none) {
            tty->print("Method [%s] is annotated with @HotSpotIntrinsicCandidate, "
              "but no compiler intrinsic is defined for the method.%s",
              method->name_and_sig_as_C_string(),
              NOT_DEBUG("") DEBUG_ONLY(" Exiting.")
            );
          tty->cr();
          DEBUG_ONLY(vm_exit(1));
        }
      }
    } // end for

#ifdef ASSERT
    if (CheckIntrinsics) {
      // Check for orphan methods in the current class. A method m
      // of a class C is orphan if an intrinsic is defined for method m,
      // but class C does not declare m.
      // The check is potentially expensive, therefore it is available
      // only in debug builds.

      for (int id = vmIntrinsics::FIRST_ID; id < (int)vmIntrinsics::ID_LIMIT; ++id) {
        if (vmIntrinsics::_compiledLambdaForm == id) {
          // The _compiledLamdbdaForm intrinsic is a special marker for bytecode
          // generated for the JVM from a LambdaForm and therefore no method
          // is defined for it.
          continue;
        }

        if (vmIntrinsics::class_for(vmIntrinsics::ID_from(id)) == klass_id) {
          // Check if the current class contains a method with the same
          // name, flags, signature.
          bool match = false;
          for (int j = 0; j < methods->length(); ++j) {
            const Method* method = methods->at(j);
            if (method->intrinsic_id() == id) {
              match = true;
              break;
            }
          }

          if (!match) {
            char buf[1000];
            tty->print("Compiler intrinsic is defined for method [%s], "
                       "but the method is not available in class [%s].%s",
                        vmIntrinsics::short_name_as_C_string(vmIntrinsics::ID_from(id),
                                                             buf, sizeof(buf)),
                        ik->name()->as_C_string(),
                        NOT_DEBUG("") DEBUG_ONLY(" Exiting.")
            );
            tty->cr();
            DEBUG_ONLY(vm_exit(1));
          }
        }
      } // end for
    } // CheckIntrinsics
#endif // ASSERT
  }
}

InstanceKlass* ClassFileParser::create_instance_klass(TRAPS) {
  if (_klass != NULL) {
    return _klass;
  }

  InstanceKlass* const ik =
    InstanceKlass::allocate_instance_klass(*this, CHECK_NULL);

  fill_instance_klass(ik, CHECK_NULL);

  assert(_klass == ik, "invariant");

  return ik;
}

void ClassFileParser::fill_instance_klass(InstanceKlass* ik, TRAPS) {
  assert(ik != NULL, "invariant");

  set_klass_to_deallocate(ik);

  assert(_field_info != NULL, "invariant");
  assert(ik->static_field_size() == _field_info->static_field_size, "sanity");
  assert(ik->nonstatic_oop_map_count() == _field_info->total_oop_map_count,
    "sanity");

  assert(ik->is_instance_klass(), "sanity");
  assert(ik->size_helper() == _field_info->instance_size, "sanity");

  // Fill in information already parsed
  ik->set_should_verify_class(_need_verify);

  // Not yet: supers are done below to support the new subtype-checking fields
  ik->set_class_loader_data(_loader_data);
  ik->set_nonstatic_field_size(_field_info->nonstatic_field_size);
  ik->set_has_nonstatic_fields(_field_info->has_nonstatic_fields);
  assert(_fac != NULL, "invariant");
  ik->set_static_oop_field_count(_fac->count[STATIC_OOP]);

  // this transfers ownership of a lot of arrays from
  // the parser onto the InstanceKlass*
  apply_parsed_class_metadata(ik, _java_fields_count, CHECK);

  // note that is not safe to use the fields in the parser from this point on
  assert(NULL == _cp, "invariant");
  assert(NULL == _fields, "invariant");
  assert(NULL == _methods, "invariant");
  assert(NULL == _inner_classes, "invariant");
  assert(NULL == _local_interfaces, "invariant");
  assert(NULL == _transitive_interfaces, "invariant");
  assert(NULL == _combined_annotations, "invariant");

  if (_has_final_method) {
    ik->set_has_final_method();
  }

  ik->copy_method_ordering(_method_ordering, CHECK);
  // The InstanceKlass::_methods_jmethod_ids cache
  // is managed on the assumption that the initial cache
  // size is equal to the number of methods in the class. If
  // that changes, then InstanceKlass::idnum_can_increment()
  // has to be changed accordingly.
  ik->set_initial_method_idnum(ik->methods()->length());

  ik->set_name(_class_name);

  if (is_anonymous()) {
    // I am well known to myself
    ik->constants()->klass_at_put(_this_class_index, ik); // eagerly resolve
  }

  ik->set_minor_version(_minor_version);
  ik->set_major_version(_major_version);
  ik->set_has_default_methods(_has_default_methods);
  ik->set_declares_default_methods(_declares_default_methods);

  if (_host_klass != NULL) {
    assert (ik->is_anonymous(), "should be the same");
    ik->set_host_klass(_host_klass);
  }

  const Array<Method*>* const methods = ik->methods();
  assert(methods != NULL, "invariant");
  const int methods_len = methods->length();

  check_methods_for_intrinsics(ik, methods);

  // Fill in field values obtained by parse_classfile_attributes
  if (_parsed_annotations->has_any_annotations()) {
    _parsed_annotations->apply_to(ik);
  }

  apply_parsed_class_attributes(ik);

  // Miranda methods
  if ((_num_miranda_methods > 0) ||
      // if this class introduced new miranda methods or
      (_super_klass != NULL && _super_klass->has_miranda_methods())
        // super class exists and this class inherited miranda methods
     ) {
       ik->set_has_miranda_methods(); // then set a flag
  }

  // Fill in information needed to compute superclasses.
  ik->initialize_supers(const_cast<InstanceKlass*>(_super_klass), CHECK);

  // Initialize itable offset tables
  klassItable::setup_itable_offset_table(ik);

  // Compute transitive closure of interfaces this class implements
  // Do final class setup
  fill_oop_maps(ik,
                _field_info->nonstatic_oop_map_count,
                _field_info->nonstatic_oop_offsets,
                _field_info->nonstatic_oop_counts);

  // Fill in has_finalizer, has_vanilla_constructor, and layout_helper
  set_precomputed_flags(ik);

  // check if this class can access its super class
  check_super_class_access(ik, CHECK);

  // check if this class can access its superinterfaces
  check_super_interface_access(ik, CHECK);

  // check if this class overrides any final method
  check_final_method_override(ik, CHECK);

  // check that if this class is an interface then it doesn't have static methods
  if (ik->is_interface()) {
    /* An interface in a JAVA 8 classfile can be static */
    if (_major_version < JAVA_8_VERSION) {
      check_illegal_static_method(ik, CHECK);
    }
  }

  // Allocate mirror and initialize static fields
  // The create_mirror() call will also call compute_modifiers()
  java_lang_Class::create_mirror(ik,
                                 _loader_data->class_loader(),
                                 _protection_domain,
                                 CHECK);

  assert(_all_mirandas != NULL, "invariant");

  // Generate any default methods - default methods are interface methods
  // that have a default implementation.  This is new with Lambda project.
  if (_has_default_methods ) {
    DefaultMethods::generate_default_methods(ik,
                                             _all_mirandas,
                                             CHECK);
  }

  // Update the loader_data graph.
  record_defined_class_dependencies(ik, CHECK);

  ClassLoadingService::notify_class_loaded(ik, false /* not shared class */);

  if (!is_internal()) {
    if (TraceClassLoading) {
      ResourceMark rm;
      // print in a single call to reduce interleaving of output
      if (_stream->source() != NULL) {
        tty->print("[Loaded %s from %s]\n",
                   ik->external_name(),
                   _stream->source());
      } else if (_loader_data->class_loader() == NULL) {
        const Klass* const caller =
          THREAD->is_Java_thread()
                ? ((JavaThread*)THREAD)->security_get_caller_class(1)
                : NULL;
        // caller can be NULL, for example, during a JVMTI VM_Init hook
        if (caller != NULL) {
          tty->print("[Loaded %s by instance of %s]\n",
                     ik->external_name(),
                     caller->external_name());
        } else {
          tty->print("[Loaded %s]\n", ik->external_name());
        }
      } else {
        tty->print("[Loaded %s from %s]\n", ik->external_name(),
                   _loader_data->class_loader()->klass()->external_name());
      }
    }

    if (log_is_enabled(Info, classresolve))  {
      ResourceMark rm;
      // print out the superclass.
      const char * from = ik->external_name();
      if (ik->java_super() != NULL) {
        log_info(classresolve)("%s %s (super)",
                   from,
                   ik->java_super()->external_name());
      }
      // print out each of the interface classes referred to by this class.
      const Array<Klass*>* const local_interfaces = ik->local_interfaces();
      if (local_interfaces != NULL) {
        const int length = local_interfaces->length();
        for (int i = 0; i < length; i++) {
          const Klass* const k = local_interfaces->at(i);
          const char * to = k->external_name();
          log_info(classresolve)("%s %s (interface)", from, to);
        }
      }
    }
  }

  TRACE_INIT_ID(ik);

  // If we reach here, all is well.
  // Now remove the InstanceKlass* from the _klass_to_deallocate field
  // in order for it to not be destroyed in the ClassFileParser destructor.
  set_klass_to_deallocate(NULL);

  // it's official
  set_klass(ik);

  debug_only(ik->verify();)
}

ClassFileParser::ClassFileParser(ClassFileStream* stream,
                                 Symbol* name,
                                 ClassLoaderData* loader_data,
                                 Handle protection_domain,
                                 TempNewSymbol* parsed_name,
                                 const Klass* host_klass,
                                 GrowableArray<Handle>* cp_patches,
                                 Publicity pub_level,
                                 TRAPS) :
  _stream(stream),
  _requested_name(name),
  _loader_data(loader_data),
  _host_klass(host_klass),
  _cp_patches(cp_patches),
  _parsed_name(parsed_name),
  _super_klass(),
  _cp(NULL),
  _fields(NULL),
  _methods(NULL),
  _inner_classes(NULL),
  _local_interfaces(NULL),
  _transitive_interfaces(NULL),
  _combined_annotations(NULL),
  _annotations(NULL),
  _type_annotations(NULL),
  _fields_annotations(NULL),
  _fields_type_annotations(NULL),
  _klass(NULL),
  _klass_to_deallocate(NULL),
  _parsed_annotations(NULL),
  _fac(NULL),
  _field_info(NULL),
  _method_ordering(NULL),
  _all_mirandas(NULL),
  _vtable_size(0),
  _itable_size(0),
  _num_miranda_methods(0),
  _rt(REF_NONE),
  _protection_domain(protection_domain),
  _access_flags(),
  _pub_level(pub_level),
  _synthetic_flag(false),
  _sde_length(false),
  _sde_buffer(NULL),
  _sourcefile_index(0),
  _generic_signature_index(0),
  _major_version(0),
  _minor_version(0),
  _this_class_index(0),
  _super_class_index(0),
  _itfs_len(0),
  _java_fields_count(0),
  _need_verify(false),
  _relax_verify(false),
  _has_default_methods(false),
  _declares_default_methods(false),
  _has_final_method(false),
  _has_finalizer(false),
  _has_empty_finalizer(false),
  _has_vanilla_constructor(false),
  _max_bootstrap_specifier_index(-1) {

  _class_name = name != NULL ? name : vmSymbols::unknown_class_name();

  assert(THREAD->is_Java_thread(), "invariant");
  assert(_loader_data != NULL, "invariant");
  assert(stream != NULL, "invariant");
  assert(_stream != NULL, "invariant");
  assert(_stream->buffer() == _stream->current(), "invariant");
  assert(_class_name != NULL, "invariant");
  assert(0 == _access_flags.as_int(), "invariant");

  // Figure out whether we can skip format checking (matching classic VM behavior)
  if (DumpSharedSpaces) {
    // verify == true means it's a 'remote' class (i.e., non-boot class)
    // Verification decision is based on BytecodeVerificationRemote flag
    // for those classes.
    _need_verify = (stream->need_verify()) ? BytecodeVerificationRemote :
                                              BytecodeVerificationLocal;
  }
  else {
    _need_verify = Verifier::should_verify_for(_loader_data->class_loader(),
                                               stream->need_verify());
  }

  // synch back verification state to stream
  stream->set_verify(_need_verify);

  // Check if verification needs to be relaxed for this class file
  // Do not restrict it to jdk1.0 or jdk1.1 to maintain backward compatibility (4982376)
  _relax_verify = Verifier::relax_verify_for(_loader_data->class_loader());

  parse_stream(stream, CHECK);

  post_process_parsed_stream(stream, _cp, CHECK);
}

void ClassFileParser::clear_class_metadata() {
  // metadata created before the instance klass is created.  Must be
  // deallocated if classfile parsing returns an error.
  _cp = NULL;
  _fields = NULL;
  _methods = NULL;
  _inner_classes = NULL;
  _local_interfaces = NULL;
  _transitive_interfaces = NULL;
  _combined_annotations = NULL;
  _annotations = _type_annotations = NULL;
  _fields_annotations = _fields_type_annotations = NULL;
}

// Destructor to clean up
ClassFileParser::~ClassFileParser() {
  if (_cp != NULL) {
    MetadataFactory::free_metadata(_loader_data, _cp);
  }
  if (_fields != NULL) {
    MetadataFactory::free_array<u2>(_loader_data, _fields);
  }

  if (_methods != NULL) {
    // Free methods
    InstanceKlass::deallocate_methods(_loader_data, _methods);
  }

  // beware of the Universe::empty_blah_array!!
  if (_inner_classes != NULL && _inner_classes != Universe::the_empty_short_array()) {
    MetadataFactory::free_array<u2>(_loader_data, _inner_classes);
  }

  // Free interfaces
  InstanceKlass::deallocate_interfaces(_loader_data, _super_klass,
                                       _local_interfaces, _transitive_interfaces);

  if (_combined_annotations != NULL) {
    // After all annotations arrays have been created, they are installed into the
    // Annotations object that will be assigned to the InstanceKlass being created.

    // Deallocate the Annotations object and the installed annotations arrays.
    _combined_annotations->deallocate_contents(_loader_data);

    // If the _combined_annotations pointer is non-NULL,
    // then the other annotations fields should have been cleared.
    assert(_annotations             == NULL, "Should have been cleared");
    assert(_type_annotations        == NULL, "Should have been cleared");
    assert(_fields_annotations      == NULL, "Should have been cleared");
    assert(_fields_type_annotations == NULL, "Should have been cleared");
  } else {
    // If the annotations arrays were not installed into the Annotations object,
    // then they have to be deallocated explicitly.
    MetadataFactory::free_array<u1>(_loader_data, _annotations);
    MetadataFactory::free_array<u1>(_loader_data, _type_annotations);
    Annotations::free_contents(_loader_data, _fields_annotations);
    Annotations::free_contents(_loader_data, _fields_type_annotations);
  }

  clear_class_metadata();

  // deallocate the klass if already created.  Don't directly deallocate, but add
  // to the deallocate list so that the klass is removed from the CLD::_klasses list
  // at a safepoint.
  if (_klass_to_deallocate != NULL) {
    _loader_data->add_to_deallocate_list(_klass_to_deallocate);
  }
}

void ClassFileParser::parse_stream(const ClassFileStream* const stream,
                                   TRAPS) {

  assert(stream != NULL, "invariant");
  assert(_class_name != NULL, "invariant");

  // BEGIN STREAM PARSING
  stream->guarantee_more(8, CHECK);  // magic, major, minor
  // Magic value
  const u4 magic = stream->get_u4_fast();
  guarantee_property(magic == JAVA_CLASSFILE_MAGIC,
                     "Incompatible magic value %u in class file %s",
                     magic, CHECK);

  // Version numbers
  _minor_version = stream->get_u2_fast();
  _major_version = stream->get_u2_fast();

  if (DumpSharedSpaces && _major_version < JAVA_1_5_VERSION) {
    ResourceMark rm;
    warning("Pre JDK 1.5 class not supported by CDS: %u.%u %s",
            _major_version,  _minor_version, _class_name->as_C_string());
    Exceptions::fthrow(
      THREAD_AND_LOCATION,
      vmSymbols::java_lang_UnsupportedClassVersionError(),
      "Unsupported major.minor version for dump time %u.%u",
      _major_version,
      _minor_version);
  }

  // Check version numbers - we check this even with verifier off
  if (!is_supported_version(_major_version, _minor_version)) {
    ResourceMark rm(THREAD);
    Exceptions::fthrow(
      THREAD_AND_LOCATION,
      vmSymbols::java_lang_UnsupportedClassVersionError(),
      "%s has been compiled by a more recent version of the Java Runtime (class file version %u.%u), "
      "this version of the Java Runtime only recognizes class file versions up to %u.%u",
      _class_name->as_C_string(),
      _major_version,
      _minor_version,
      JAVA_MAX_SUPPORTED_VERSION,
      JAVA_MAX_SUPPORTED_MINOR_VERSION);
    return;
  }

  stream->guarantee_more(3, CHECK); // length, first cp tag
  const u2 cp_size = stream->get_u2_fast();

  guarantee_property(
    cp_size >= 1, "Illegal constant pool size %u in class file %s",
    cp_size, CHECK);

  _cp = ConstantPool::allocate(_loader_data,
                               cp_size,
                               CHECK);

  ConstantPool* const cp = _cp;

  parse_constant_pool(stream, cp, cp_size, CHECK);

  assert(cp_size == (const u2)cp->length(), "invariant");

  // ACCESS FLAGS
  stream->guarantee_more(8, CHECK);  // flags, this_class, super_class, infs_len

  // Access flags
  jint flags = stream->get_u2_fast() & JVM_RECOGNIZED_CLASS_MODIFIERS;

  if ((flags & JVM_ACC_INTERFACE) && _major_version < JAVA_6_VERSION) {
    // Set abstract bit for old class files for backward compatibility
    flags |= JVM_ACC_ABSTRACT;
  }

  _access_flags.set_flags(flags);

  verify_legal_class_modifiers((jint)_access_flags.as_int(), CHECK);

  // This class and superclass
  _this_class_index = stream->get_u2_fast();
  check_property(
    valid_cp_range(_this_class_index, cp_size) &&
      cp->tag_at(_this_class_index).is_unresolved_klass(),
    "Invalid this class index %u in constant pool in class file %s",
    _this_class_index, CHECK);

  Symbol* const class_name_in_cp = cp->klass_name_at(_this_class_index);
  assert(class_name_in_cp != NULL, "class_name can't be null");

  if (_parsed_name != NULL) {
    // It's important to set parsed_name *before* resolving the super class.
    // (it's used for cleanup by the caller if parsing fails)
    *_parsed_name = class_name_in_cp;
    // parsed_name is returned and can be used if there's an error, so add to
    // its reference count.  Caller will decrement the refcount.
    (*_parsed_name)->increment_refcount();
  }

  // Update _class_name which could be null previously
  // to reflect the name in the constant pool
  _class_name = class_name_in_cp;

  // Don't need to check whether this class name is legal or not.
  // It has been checked when constant pool is parsed.
  // However, make sure it is not an array type.
  if (_need_verify) {
    guarantee_property(_class_name->byte_at(0) != JVM_SIGNATURE_ARRAY,
                       "Bad class name in class file %s",
                       CHECK);
  }

  // Checks if name in class file matches requested name
  if (_requested_name != NULL && _requested_name != _class_name) {
    ResourceMark rm(THREAD);
    Exceptions::fthrow(
      THREAD_AND_LOCATION,
      vmSymbols::java_lang_NoClassDefFoundError(),
      "%s (wrong name: %s)",
      _class_name->as_C_string(),
      _requested_name != NULL ? _requested_name->as_C_string() : "NoName"
    );
    return;
  }

  if (!is_internal()) {
    if (TraceClassLoadingPreorder) {
      tty->print("[Loading %s",
        _class_name->as_klass_external_name());

      if (stream->source() != NULL) {
        tty->print(" from %s", stream->source());
      }
      tty->print_cr("]");
    }
#if INCLUDE_CDS
    if (DumpLoadedClassList != NULL && stream->source() != NULL && classlist_file->is_open()) {
      // Only dump the classes that can be stored into CDS archive
      if (SystemDictionaryShared::is_sharing_possible(_loader_data)) {
        ResourceMark rm(THREAD);
        classlist_file->print_cr("%s", _class_name->as_C_string());
        classlist_file->flush();
      }
    }
#endif
  }

  // SUPERKLASS
  _super_class_index = stream->get_u2_fast();
  _super_klass = parse_super_class(cp,
                                   _super_class_index,
                                   _need_verify,
                                   CHECK);

  // Interfaces
  _itfs_len = stream->get_u2_fast();
  parse_interfaces(stream,
                   _itfs_len,
                   cp,
                   &_has_default_methods,
                   CHECK);

  assert(_local_interfaces != NULL, "invariant");

  // Fields (offsets are filled in later)
  _fac = new FieldAllocationCount();
  parse_fields(stream,
               _access_flags.is_interface(),
               _fac,
               cp,
               cp_size,
               &_java_fields_count,
               CHECK);

  assert(_fields != NULL, "invariant");

  // Methods
  AccessFlags promoted_flags;
  parse_methods(stream,
                _access_flags.is_interface(),
                &promoted_flags,
                &_has_final_method,
                &_declares_default_methods,
                CHECK);

  assert(_methods != NULL, "invariant");

  // promote flags from parse_methods() to the klass' flags
  _access_flags.add_promoted_flags(promoted_flags.as_int());

  if (_declares_default_methods) {
    _has_default_methods = true;
  }

  // Additional attributes/annotations
  _parsed_annotations = new ClassAnnotationCollector();
  parse_classfile_attributes(stream, cp, _parsed_annotations, CHECK);

  assert(_inner_classes != NULL, "invariant");

  // Finalize the Annotations metadata object,
  // now that all annotation arrays have been created.
  create_combined_annotations(CHECK);

  // Make sure this is the end of class file stream
  guarantee_property(stream->at_eos(),
                     "Extra bytes at the end of class file %s",
                     CHECK);

  // all bytes in stream read and parsed
}

void ClassFileParser::post_process_parsed_stream(const ClassFileStream* const stream,
                                                 ConstantPool* cp,
                                                 TRAPS) {
  assert(stream != NULL, "invariant");
  assert(stream->at_eos(), "invariant");
  assert(cp != NULL, "invariant");
  assert(_loader_data != NULL, "invariant");

  // We check super class after class file is parsed and format is checked
  if (_super_class_index > 0 && NULL ==_super_klass) {
    Symbol* const super_class_name = cp->klass_name_at(_super_class_index);
    if (_access_flags.is_interface()) {
      // Before attempting to resolve the superclass, check for class format
      // errors not checked yet.
      guarantee_property(super_class_name == vmSymbols::java_lang_Object(),
        "Interfaces must have java.lang.Object as superclass in class file %s",
        CHECK);
      }
      _super_klass = (const InstanceKlass*)
                       SystemDictionary::resolve_super_or_fail(_class_name,
                                                               super_class_name,
                                                               _loader_data->class_loader(),
                                                               _protection_domain,
                                                               true,
                                                               CHECK);
  }

  if (_super_klass != NULL) {
    if (_super_klass->has_default_methods()) {
      _has_default_methods = true;
    }

    if (_super_klass->is_interface()) {
      ResourceMark rm(THREAD);
      Exceptions::fthrow(
        THREAD_AND_LOCATION,
        vmSymbols::java_lang_IncompatibleClassChangeError(),
        "class %s has interface %s as super class",
        _class_name->as_klass_external_name(),
        _super_klass->external_name()
      );
      return;
    }
    // Make sure super class is not final
    if (_super_klass->is_final()) {
      THROW_MSG(vmSymbols::java_lang_VerifyError(), "Cannot inherit from final class");
    }
  }

  // Compute the transitive list of all unique interfaces implemented by this class
  _transitive_interfaces =
    compute_transitive_interfaces(_super_klass,
                                  _local_interfaces,
                                  _loader_data,
                                  CHECK);

  assert(_transitive_interfaces != NULL, "invariant");

  // sort methods
  _method_ordering = sort_methods(_methods);

  _all_mirandas = new GrowableArray<Method*>(20);

  klassVtable::compute_vtable_size_and_num_mirandas(&_vtable_size,
                                                    &_num_miranda_methods,
                                                    _all_mirandas,
                                                    _super_klass,
                                                    _methods,
                                                    _access_flags,
                                                    _loader_data->class_loader(),
                                                    _class_name,
                                                    _local_interfaces,
                                                    CHECK);

  // Size of Java itable (in words)
  _itable_size = _access_flags.is_interface() ? 0 :
    klassItable::compute_itable_size(_transitive_interfaces);

  assert(_fac != NULL, "invariant");
  assert(_parsed_annotations != NULL, "invariant");

  _field_info = new FieldLayoutInfo();
  layout_fields(cp, _fac, _parsed_annotations, _field_info, CHECK);

  // Compute reference typ
  _rt = (NULL ==_super_klass) ? REF_NONE : _super_klass->reference_type();

}

void ClassFileParser::set_klass(InstanceKlass* klass) {

#ifdef ASSERT
  if (klass != NULL) {
    assert(NULL == _klass, "leaking?");
  }
#endif

  _klass = klass;
}

void ClassFileParser::set_klass_to_deallocate(InstanceKlass* klass) {

#ifdef ASSERT
  if (klass != NULL) {
    assert(NULL == _klass_to_deallocate, "leaking?");
  }
#endif

  _klass_to_deallocate = klass;
}

// Caller responsible for ResourceMark
// clone stream with rewound position
const ClassFileStream* ClassFileParser::clone_stream() const {
  assert(_stream != NULL, "invariant");

  return _stream->clone();
}

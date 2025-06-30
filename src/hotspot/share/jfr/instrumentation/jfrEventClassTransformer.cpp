/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "classfile/classFileParser.hpp"
#include "classfile/classFileStream.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/moduleEntry.hpp"
#include "classfile/modules.hpp"
#include "classfile/stackMapTable.hpp"
#include "classfile/symbolTable.hpp"
#include "interpreter/bytecodes.hpp"
#include "jfr/instrumentation/jfrClassTransformer.hpp"
#include "jfr/instrumentation/jfrEventClassTransformer.hpp"
#include "jfr/jfr.hpp"
#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/jni/jfrUpcalls.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/support/jfrAnnotationElementIterator.hpp"
#include "jfr/support/jfrAnnotationIterator.hpp"
#include "jfr/support/jfrJdkJfrEvent.hpp"
#include "jfr/writers/jfrBigEndianWriter.hpp"
#include "jvm.h"
#include "logging/log.hpp"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/array.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/klass.inline.hpp"
#include "oops/method.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/jniHandles.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

static const u2 number_of_new_methods = 5;
static const u2 number_of_new_fields = 3;
static const int extra_stream_bytes = 0x280;
static const u2 invalid_cp_index = 0;

static const char* utf8_constants[] = {
  "Code",         // 0
  "J",            // 1
  "commit",       // 2
  "eventConfiguration", // 3
  "duration",     // 4
  "begin",        // 5
  "()V",          // 6
  "isEnabled",    // 7
  "()Z",          // 8
  "end",          // 9
  "shouldCommit", // 10
  "startTime",    // 11 // LAST_REQUIRED_UTF8
  "Ljdk/jfr/internal/event/EventConfiguration;", // 12
  "Ljava/lang/Object;", // 13
  "<clinit>",     // 14
  "jdk/jfr/FlightRecorder", // 15
  "register",     // 16
  "(Ljava/lang/Class;)V", // 17
  "StackMapTable", // 18
  "Exceptions", // 19
  "LineNumberTable", // 20
  "LocalVariableTable", // 21
  "LocalVariableTypeTable", // 22
  "RuntimeVisibleAnnotation", // 23
};

enum utf8_req_symbols {
  UTF8_REQ_Code,
  UTF8_REQ_J_FIELD_DESC,
  UTF8_REQ_commit,
  UTF8_REQ_eventConfiguration,
  UTF8_REQ_duration,
  UTF8_REQ_begin,
  UTF8_REQ_EMPTY_VOID_METHOD_DESC,
  UTF8_REQ_isEnabled,
  UTF8_REQ_EMPTY_BOOLEAN_METHOD_DESC,
  UTF8_REQ_end,
  UTF8_REQ_shouldCommit,
  UTF8_REQ_startTime,
  NOF_UTF8_REQ_SYMBOLS
};

enum utf8_opt_symbols {
  UTF8_OPT_eventConfiguration_FIELD_DESC = NOF_UTF8_REQ_SYMBOLS,
  UTF8_OPT_LjavaLangObject,
  UTF8_OPT_clinit,
  UTF8_OPT_FlightRecorder,
  UTF8_OPT_register,
  UTF8_OPT_CLASS_VOID_METHOD_DESC,
  UTF8_OPT_StackMapTable,
  UTF8_OPT_Exceptions,
  UTF8_OPT_LineNumberTable,
  UTF8_OPT_LocalVariableTable,
  UTF8_OPT_LocalVariableTypeTable,
  UTF8_OPT_RuntimeVisibleAnnotation,
  NOF_UTF8_SYMBOLS
};

static u1 empty_void_method_code_attribute[] = {
  0x0,
  0x0,
  0x0,
  0xd, // attribute len
  0x0,
  0x0, // max stack
  0x0,
  0x1, // max locals
  0x0,
  0x0,
  0x0,
  0x1, // code length
  Bytecodes::_return,
  0x0,
  0x0, // ex table len
  0x0,
  0x0  // attributes_count
};

static u1 boolean_method_code_attribute[] = {
  0x0,
  0x0,
  0x0,
  0xe,
  0x0,
  0x1, // max stack
  0x0,
  0x1, // max locals
  0x0,
  0x0,
  0x0,
  0x2,
  Bytecodes::_iconst_0,
  Bytecodes::_ireturn,
  0x0,
  0x0, // ex table len
  0x0,
  0x0, // attributes_count
};

static JfrAnnotationElementIterator elements_iterator(const InstanceKlass* ik, const JfrAnnotationIterator& it) {
  const address buffer = it.buffer();
  int current = it.current();
  int next = it.next();
  assert(current < next, "invariant");
  return JfrAnnotationElementIterator(ik, buffer + current, next - current);
}

static const char value_name[] = "value";
static bool has_annotation(const InstanceKlass* ik, const Symbol* annotation_type, bool default_value, bool& value) {
  assert(annotation_type != nullptr, "invariant");
  AnnotationArray* class_annotations = ik->class_annotations();
  if (class_annotations == nullptr) {
    return false;
  }
  const JfrAnnotationIterator annotation_iterator(ik, class_annotations);
  while (annotation_iterator.has_next()) {
    annotation_iterator.move_to_next();
    if (annotation_iterator.type() == annotation_type) {
      // target annotation found
      static const Symbol* value_symbol =
        SymbolTable::probe(value_name, sizeof value_name - 1);
      assert(value_symbol != nullptr, "invariant");
      JfrAnnotationElementIterator element_iterator = elements_iterator(ik, annotation_iterator);
      if (!element_iterator.has_next()) {
        // Default values are not stored in the annotation element, so if the
        // element-value pair is empty, return the default value.
        value = default_value;
        return true;
      }
      while (element_iterator.has_next()) {
        element_iterator.move_to_next();
        if (value_symbol == element_iterator.name()) {
          // "value" element
          assert('Z' == element_iterator.value_type(), "invariant");
          value = element_iterator.read_bool();
          return true;
        }
      }
    }
  }
  return false;
}

// Evaluate to the value of the first found Symbol* annotation type.
// Searching moves upwards in the klass hierarchy in order to support
// inherited annotations in addition to the ability to override.
static bool annotation_value(const InstanceKlass* ik, const Symbol* annotation_type, bool default_value, bool& value) {
  assert(ik != nullptr, "invariant");
  assert(annotation_type != nullptr, "invariant");
  assert(JdkJfrEvent::is_a(ik), "invariant");
  if (has_annotation(ik, annotation_type, default_value, value)) {
    return true;
  }
  InstanceKlass* const super = InstanceKlass::cast(ik->super());
  return super != nullptr && JdkJfrEvent::is_a(super) ? annotation_value(super, annotation_type, default_value, value) : false;
}

static const char jdk_jfr_module_name[] = "jdk.jfr";

static bool java_base_can_read_jdk_jfr() {
  static bool can_read = false;
  if (can_read) {
    return true;
  }
  static Symbol* jdk_jfr_module_symbol = nullptr;
  if (jdk_jfr_module_symbol == nullptr) {
    jdk_jfr_module_symbol = SymbolTable::probe(jdk_jfr_module_name, sizeof jdk_jfr_module_name - 1);
    if (jdk_jfr_module_symbol == nullptr) {
      return false;
    }
  }
  assert(jdk_jfr_module_symbol != nullptr, "invariant");
  ModuleEntryTable* const table = Modules::get_module_entry_table(Handle());
  assert(table != nullptr, "invariant");
  const ModuleEntry* const java_base_module = table->javabase_moduleEntry();
  if (java_base_module == nullptr) {
    return false;
  }
  assert(java_base_module != nullptr, "invariant");
  ModuleEntry* jdk_jfr_module;
  {
    MutexLocker ml(Module_lock);
    jdk_jfr_module = table->lookup_only(jdk_jfr_module_symbol);
    if (jdk_jfr_module == nullptr) {
      return false;
    }
  }
  assert(jdk_jfr_module != nullptr, "invariant");
  if (java_base_module->can_read(jdk_jfr_module)) {
    can_read = true;
  }
  return can_read;
}

static const char registered_constant[] = "Ljdk/jfr/Registered;";

// Evaluate to the value of the first found "Ljdk/jfr/Registered;" annotation.
// Searching moves upwards in the klass hierarchy in order to support
// inherited annotations in addition to the ability to override.
static bool should_register_klass(const InstanceKlass* ik, bool& untypedEventHandler) {
  assert(ik != nullptr, "invariant");
  assert(JdkJfrEvent::is_a(ik), "invariant");
  assert(!untypedEventHandler, "invariant");
  static const Symbol* registered_symbol = nullptr;
  if (registered_symbol == nullptr) {
    registered_symbol = SymbolTable::probe(registered_constant, sizeof registered_constant - 1);
    if (registered_symbol == nullptr) {
      untypedEventHandler = true;
      return false;
    }
  }
  assert(registered_symbol != nullptr, "invariant");
  bool value = false; // to be set by annotation_value
  untypedEventHandler = !(annotation_value(ik, registered_symbol, true, value) || java_base_can_read_jdk_jfr());
  return value;
}

/*
 * Map an utf8 constant back to its CONSTANT_UTF8_INFO
 */
static u2 utf8_info_index(const InstanceKlass* ik, const Symbol* const target, TRAPS) {
  assert(target != nullptr, "invariant");
  const ConstantPool* cp = ik->constants();
  const int cp_len = cp->length();
  for (int index = 1; index < cp_len; ++index) {
    const constantTag tag = cp->tag_at(index);
    if (tag.is_utf8()) {
      const Symbol* const utf8_sym = cp->symbol_at(index);
      assert(utf8_sym != nullptr, "invariant");
      if (utf8_sym == target) {
        return static_cast<u2>(index);
      }
    }
  }
  // not in constant pool
  return invalid_cp_index;
}

#ifdef ASSERT
static bool is_index_within_range(u2 index, u2 orig_cp_len, u2 new_cp_entries_len) {
  return index > 0 && index < orig_cp_len + new_cp_entries_len;
}
#endif

static u2 add_utf8_info(JfrBigEndianWriter& writer, const char* utf8_constant, u2 orig_cp_len, u2& new_cp_entries_len) {
  assert(utf8_constant != nullptr, "invariant");
  writer.write<u1>(JVM_CONSTANT_Utf8);
  writer.write_utf8_u2_len(utf8_constant);
  assert(writer.is_valid(), "invariant");
  // return index for the added utf8 info
  return orig_cp_len + new_cp_entries_len++;
}

static u2 add_method_ref_info(JfrBigEndianWriter& writer,
                              u2 cls_name_index,
                              u2 method_index,
                              u2 desc_index,
                              u2 orig_cp_len,
                              u2& number_of_new_constants,
                              TRAPS) {
  assert(cls_name_index != invalid_cp_index, "invariant");
  assert(method_index != invalid_cp_index, "invariant");
  assert(desc_index != invalid_cp_index, "invariant");
  assert(is_index_within_range(cls_name_index, orig_cp_len, number_of_new_constants), "invariant");
  assert(is_index_within_range(method_index, orig_cp_len, number_of_new_constants), "invariant");
  assert(is_index_within_range(desc_index, orig_cp_len, number_of_new_constants), "invariant");
  writer.write<u1>(JVM_CONSTANT_Class);
  writer.write<u2>(cls_name_index);
  const u2 cls_entry_index = orig_cp_len + number_of_new_constants;
  ++number_of_new_constants;
  writer.write<u1>(JVM_CONSTANT_NameAndType);
  writer.write<u2>(method_index);
  writer.write<u2>(desc_index);
  const u2 nat_entry_index = orig_cp_len + number_of_new_constants;
  ++number_of_new_constants;
  writer.write<u1>(JVM_CONSTANT_Methodref);
  writer.write<u2>(cls_entry_index);
  writer.write<u2>(nat_entry_index);
  // post-increment number_of_new_constants
  // value returned is the index to the added method_ref
  return orig_cp_len + number_of_new_constants++;
}

static u2 add_flr_register_method_constants(JfrBigEndianWriter& writer,
                                            const u2* utf8_indexes,
                                            u2 orig_cp_len,
                                            u2& number_of_new_constants,
                                            TRAPS) {
  assert(utf8_indexes != nullptr, "invariant");
  return add_method_ref_info(writer,
                             utf8_indexes[UTF8_OPT_FlightRecorder],
                             utf8_indexes[UTF8_OPT_register],
                             utf8_indexes[UTF8_OPT_CLASS_VOID_METHOD_DESC],
                             orig_cp_len,
                             number_of_new_constants,
                             THREAD);
}

/*
 * field_info {
 *   u2             access_flags;
 *   u2             name_index;
 *   u2             descriptor_index;
 *   u2             attributes_count;
 *   attribute_info attributes[attributes_count];
 * }
 */
static jlong add_field_info(JfrBigEndianWriter& writer, u2 name_index, u2 desc_index, bool is_static = false) {
  assert(name_index != invalid_cp_index, "invariant");
  assert(desc_index != invalid_cp_index, "invariant");
  DEBUG_ONLY(const jlong start_offset = writer.current_offset();)
  writer.write<u2>(JVM_ACC_SYNTHETIC | JVM_ACC_PRIVATE | (is_static ? JVM_ACC_STATIC : JVM_ACC_TRANSIENT)); // flags
  writer.write(name_index);
  writer.write(desc_index);
  writer.write((u2)0x0); // attributes_count
  assert(writer.is_valid(), "invariant");
  DEBUG_ONLY(assert(start_offset + 8 == writer.current_offset(), "invariant");)
  return writer.current_offset();
}

static u2 add_field_infos(JfrBigEndianWriter& writer, const u2* utf8_indexes, bool untypedEventConfiguration) {
  assert(utf8_indexes != nullptr, "invariant");
  add_field_info(writer,
                 utf8_indexes[UTF8_REQ_eventConfiguration],
                 untypedEventConfiguration ? utf8_indexes[UTF8_OPT_LjavaLangObject] : utf8_indexes[UTF8_OPT_eventConfiguration_FIELD_DESC],
                 true); // static

  add_field_info(writer,
                 utf8_indexes[UTF8_REQ_startTime],
                 utf8_indexes[UTF8_REQ_J_FIELD_DESC]);

  add_field_info(writer,
                 utf8_indexes[UTF8_REQ_duration],
                 utf8_indexes[UTF8_REQ_J_FIELD_DESC]);

  return number_of_new_fields;
}

/*
 * method_info {
 *  u2             access_flags;
 *  u2             name_index;
 *  u2             descriptor_index;
 *  u2             attributes_count;
 *  attribute_info attributes[attributes_count];
 * }
 *
 * Code_attribute {
 *   u2 attribute_name_index;
 *   u4 attribute_length;
 *   u2 max_stack;
 *   u2 max_locals;
 *   u4 code_length;
 *   u1 code[code_length];
 *   u2 exception_table_length;
 *   {   u2 start_pc;
 *       u2 end_pc;
 *       u2 handler_pc;
 *       u2 catch_type;
 *   } exception_table[exception_table_length];
 *   u2 attributes_count;
 *   attribute_info attributes[attributes_count];
 * }
 */

static jlong add_method_info(JfrBigEndianWriter& writer,
                             u2 name_index,
                             u2 desc_index,
                             u2 code_index,
                             const u1* const code,
                             const size_t code_len) {
  assert(name_index > 0, "invariant");
  assert(desc_index > 0, "invariant");
  assert(code_index > 0, "invariant");
  DEBUG_ONLY(const jlong start_offset = writer.current_offset();)
  writer.write<u2>(JVM_ACC_SYNTHETIC | JVM_ACC_PUBLIC); // flags
  writer.write(name_index);
  writer.write(desc_index);
  writer.write<u2>(0x1); // attributes_count ; 1 for "Code" attribute
  assert(writer.is_valid(), "invariant");
  DEBUG_ONLY(assert(start_offset + 8 == writer.current_offset(), "invariant");)
  // Code attribute
  writer.write(code_index); // "Code"
  writer.write_bytes(code, code_len);
  DEBUG_ONLY(assert((start_offset + 8 + 2 + (jlong)code_len) == writer.current_offset(), "invariant");)
  return writer.current_offset();
}

/*
 * On return, the passed stream will be positioned
 * just after the constant pool section in the classfile
 * and the cp length is returned.
 *
 * Stream should come in at the start position.
 */
static u2 position_stream_after_cp(const ClassFileStream* stream) {
  assert(stream != nullptr, "invariant");
  assert(stream->current_offset() == 0, "invariant");
  stream->skip_u4_fast(2);  // 8 bytes skipped
  const u2 cp_len = stream->get_u2_fast();
  assert(cp_len > 0, "invariant");
  // now spin the stream position to just after the constant pool
  for (u2 index = 1; index < cp_len; ++index) {
    const u1 tag = stream->get_u1_fast(); // cp tag
    switch (tag) {
      case JVM_CONSTANT_Class:
      case JVM_CONSTANT_String: {
        stream->skip_u2_fast(1); // skip 2 bytes
        continue;
      }
      case JVM_CONSTANT_Fieldref:
      case JVM_CONSTANT_Methodref:
      case JVM_CONSTANT_InterfaceMethodref:
      case JVM_CONSTANT_Integer:
      case JVM_CONSTANT_Float:
      case JVM_CONSTANT_NameAndType:
      case JVM_CONSTANT_InvokeDynamic: {
        stream->skip_u4_fast(1); // skip 4 bytes
        continue;
      }
      case JVM_CONSTANT_Long:
      case JVM_CONSTANT_Double: {
        stream->skip_u4_fast(2); // skip 8 bytes
        // Skip entry following eigth-byte constant, see JVM book p. 98
        ++index;
        continue;
      }
      case JVM_CONSTANT_Utf8: {
        int utf8_length = static_cast<int>(stream->get_u2_fast());
        stream->skip_u1_fast(utf8_length); // skip 2 + len bytes
        continue;
      }
      case JVM_CONSTANT_MethodHandle:
      case JVM_CONSTANT_MethodType: {
        if (tag == JVM_CONSTANT_MethodHandle) {
          stream->skip_u1_fast(1);
          stream->skip_u2_fast(1); // skip 3 bytes
        }
        else if (tag == JVM_CONSTANT_MethodType) {
          stream->skip_u2_fast(1); // skip 3 bytes
        }
      }
      continue;
      case JVM_CONSTANT_Dynamic:
        stream->skip_u2_fast(1);
        stream->skip_u2_fast(1);
      continue;
      default:
        assert(false, "error in skip logic!");
        break;
    } // end switch(tag)
  }
  return cp_len;
}

/*
* On return, the passed stream will be positioned
* just after the fields section in the classfile
* and the number of fields will be returned.
*
* Stream should come in positioned just before fields_count
*/
static u2 position_stream_after_fields(const ClassFileStream* stream) {
  assert(stream != nullptr, "invariant");
  assert(stream->current_offset() > 0, "invariant");
  // fields len
  const u2 orig_fields_len = stream->get_u2_fast();
  // fields
  for (u2 i = 0; i < orig_fields_len; ++i) {
    stream->skip_u2_fast(3);
    const u2 attrib_info_len = stream->get_u2_fast();
    for (u2 j = 0; j < attrib_info_len; ++j) {
      stream->skip_u2_fast(1);
      stream->skip_u1_fast(static_cast<int>(stream->get_u4_fast()));
    }
  }
  return orig_fields_len;
}

/*
* On return, the passed stream will be positioned
* just after the methods section in the classfile
* and the number of methods will be returned.
*
* Stream should come in positioned just before methods_count
*/
static u2 position_stream_after_methods(JfrBigEndianWriter& writer,
                                        const ClassFileStream* stream,
                                        const u2* utf8_indexes,
                                        bool register_klass,
                                        const Method* clinit_method,
                                        u4& orig_method_len_offset) {
  assert(stream != nullptr, "invariant");
  assert(stream->current_offset() > 0, "invariant");
  assert(utf8_indexes != nullptr, "invariant");
  // We will come back to this location when we
  // know how many methods there will be.
  writer.reserve(sizeof(u2));
  const u2 orig_methods_len = stream->get_u2_fast();
  // Move copy position past original method_count
  // in order to not copy the original count
  orig_method_len_offset += 2;
  for (u2 i = 0; i < orig_methods_len; ++i) {
    const u4 method_offset = stream->current_offset();
    stream->skip_u2_fast(1); // Access Flags
    const u2 name_index = stream->get_u2_fast(); // Name index
    stream->skip_u2_fast(1); // Descriptor index
    const u2 attributes_count = stream->get_u2_fast();
    for (u2 j = 0; j < attributes_count; ++j) {
      stream->skip_u2_fast(1);
      stream->skip_u1_fast(static_cast<int>(stream->get_u4_fast()));
    }
    if (clinit_method != nullptr && name_index == clinit_method->name_index()) {
      // The method just parsed is an existing <clinit> method.
      // If the class has the @Registered(false) annotation, i.e. marking a class
      // for opting out from automatic registration, then we do not need to do anything.
      if (!register_klass) {
        continue;
      }
      // Automatic registration with the jfr system is acccomplished
      // by pre-pending code to the <clinit> method of the class.
      // We will need to re-create a new <clinit> in a later step.
      // For now, ensure that this method is excluded from the methods
      // being copied.
      writer.write_bytes(stream->buffer() + orig_method_len_offset,
                         method_offset - orig_method_len_offset);
      assert(writer.is_valid(), "invariant");

      // Update copy position to skip copy of <clinit> method
      orig_method_len_offset = stream->current_offset();
    }
  }
  return orig_methods_len;
}

static u2 add_method_infos(JfrBigEndianWriter& writer, const u2* utf8_indexes) {
  assert(utf8_indexes != nullptr, "invariant");
  add_method_info(writer,
                  utf8_indexes[UTF8_REQ_begin],
                  utf8_indexes[UTF8_REQ_EMPTY_VOID_METHOD_DESC],
                  utf8_indexes[UTF8_REQ_Code],
                  empty_void_method_code_attribute,
                  sizeof(empty_void_method_code_attribute));

  assert(writer.is_valid(), "invariant");

  add_method_info(writer,
                  utf8_indexes[UTF8_REQ_end],
                  utf8_indexes[UTF8_REQ_EMPTY_VOID_METHOD_DESC],
                  utf8_indexes[UTF8_REQ_Code],
                  empty_void_method_code_attribute,
                  sizeof(empty_void_method_code_attribute));

  assert(writer.is_valid(), "invariant");

  add_method_info(writer,
                  utf8_indexes[UTF8_REQ_commit],
                  utf8_indexes[UTF8_REQ_EMPTY_VOID_METHOD_DESC],
                  utf8_indexes[UTF8_REQ_Code],
                  empty_void_method_code_attribute,
                  sizeof(empty_void_method_code_attribute));

  assert(writer.is_valid(), "invariant");

  add_method_info(writer,
                  utf8_indexes[UTF8_REQ_isEnabled],
                  utf8_indexes[UTF8_REQ_EMPTY_BOOLEAN_METHOD_DESC],
                  utf8_indexes[UTF8_REQ_Code],
                  boolean_method_code_attribute,
                  sizeof(boolean_method_code_attribute));

  assert(writer.is_valid(), "invariant");

  add_method_info(writer,
                  utf8_indexes[UTF8_REQ_shouldCommit],
                  utf8_indexes[UTF8_REQ_EMPTY_BOOLEAN_METHOD_DESC],
                  utf8_indexes[UTF8_REQ_Code],
                  boolean_method_code_attribute,
                  sizeof(boolean_method_code_attribute));
  assert(writer.is_valid(), "invariant");
  return number_of_new_methods;
}

static void adjust_exception_table(JfrBigEndianWriter& writer, u2 bci_adjustment_offset, const Method* method, TRAPS) {
  const u2 ex_table_length = method != nullptr ? (u2)method->exception_table_length() : 0;
  writer.write<u2>(ex_table_length); // Exception table length
  if (ex_table_length > 0) {
    assert(method != nullptr, "invariant");
    const ExceptionTableElement* const ex_elements = method->exception_table_start();
    for (int i = 0; i < ex_table_length; ++i) {
      assert(ex_elements != nullptr, "invariant");
      writer.write<u2>(ex_elements[i].start_pc + bci_adjustment_offset);
      writer.write<u2>(ex_elements[i].end_pc + bci_adjustment_offset);
      writer.write<u2>(ex_elements[i].handler_pc + bci_adjustment_offset);
      writer.write<u2>(ex_elements[i].catch_type_index); // no adjustment
    }
  }
}

enum StackMapFrameTypes : u1 {
  SAME_FRAME_BEGIN = 0,
  SAME_FRAME_END = 63,
  SAME_LOCALS_1_STACK_ITEM_FRAME_BEGIN = 64,
  SAME_LOCALS_1_STACK_ITEM_FRAME_END = 127,
  SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED = 247,
  CHOP_FRAME_BEGIN = 248,
  CHOP_FRAME_END = 250,
  SAME_FRAME_EXTENDED = 251,
  APPEND_FRAME_BEGIN = 252,
  APPEND_FRAME_END = 254,
  FULL_FRAME = 255
};

static void adjust_stack_map(JfrBigEndianWriter& writer,
                             Array<u1>* stack_map,
                             const u2* utf8_indexes,
                             u2 bci_adjustment_offset,
                             TRAPS) {
  assert(stack_map != nullptr, "invariant");
  assert(utf8_indexes != nullptr, "invariant");
  writer.write<u2>(utf8_indexes[UTF8_OPT_StackMapTable]);
  const jlong stack_map_attrib_len_offset = writer.current_offset();
  writer.reserve(sizeof(u4));
  StackMapStream stream(stack_map);
  const u2 stack_map_entries = stream.get_u2(THREAD);
  // number of entries
  writer.write<u2>(stack_map_entries); // new stack map entry added
  const u1 frame_type = stream.get_u1(THREAD);
  // SAME_FRAME and SAME_LOCALS_1_STACK_ITEM_FRAME encode
  // their offset_delta into the actual frame type itself.
  // If such a frame type is the first frame, then we transform
  // it to a SAME_FRAME_EXTENDED or a SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED frame.
  // This is done in order to not overflow frame types accidentally
  // when adjusting the offset_delta. In changing the frame types,
  // we can work with an explicit u2 offset_delta field (like the other frame types)
  if (frame_type <= SAME_FRAME_END) {
    writer.write<u1>(SAME_FRAME_EXTENDED);
    writer.write<u2>(frame_type + bci_adjustment_offset);
  } else if (frame_type >= SAME_LOCALS_1_STACK_ITEM_FRAME_BEGIN &&
             frame_type <= SAME_LOCALS_1_STACK_ITEM_FRAME_END) {
    writer.write<u1>(SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED);
    const u2 value = frame_type - SAME_LOCALS_1_STACK_ITEM_FRAME_BEGIN;
    writer.write<u2>(value + bci_adjustment_offset);
  } else if (frame_type >= SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED) {
      // SAME_LOCALS_1_STACK_ITEM_FRAME_EXTENDED to FULL_FRAME
      // has a u2 offset_delta field
      writer.write<u1>(frame_type);
      writer.write<u2>(stream.get_u2(THREAD) + bci_adjustment_offset);
  } else {
    assert(false, "stackMapFrame type is invalid");
  }

  while (!stream.at_end()) {
    writer.write<u1>(stream.get_u1(THREAD));
  }

  u4 stack_map_attrib_len = static_cast<u4>(writer.current_offset() - stack_map_attrib_len_offset);
  // the stack_map_table_attributes_length value is exclusive
  stack_map_attrib_len -= 4;
  writer.write_at_offset(stack_map_attrib_len, stack_map_attrib_len_offset);
}

static void adjust_line_number_table(JfrBigEndianWriter& writer,
                                     const u2* utf8_indexes,
                                     u4 bci_adjustement_offset,
                                     const Method* method,
                                     TRAPS) {
  assert(utf8_indexes != nullptr, "invariant");
  assert(method != nullptr, "invariant");
  assert(method->has_linenumber_table(), "invariant");
  writer.write(utf8_indexes[UTF8_OPT_LineNumberTable]);
  const jlong lnt_attributes_length_offset = writer.current_offset();
  writer.reserve(sizeof(u4));
  const jlong lnt_attributes_entries_offset = writer.current_offset();
  writer.reserve(sizeof(u2));
  u1* lnt = method->compressed_linenumber_table();
  CompressedLineNumberReadStream lnt_stream(lnt);
  u2 line_number_table_entries = 0;
  while (lnt_stream.read_pair()) {
    ++line_number_table_entries;
    const u2 bci = (u2)lnt_stream.bci();
    writer.write<u2>(bci + (u2)bci_adjustement_offset);
    writer.write<u2>((u2)lnt_stream.line());
  }
  writer.write_at_offset(line_number_table_entries, lnt_attributes_entries_offset);
  u4 lnt_table_attributes_len = static_cast<u4>(writer.current_offset() - lnt_attributes_length_offset);
  // the line_number_table_attributes_length value is exclusive
  lnt_table_attributes_len -= 4;
  writer.write_at_offset(lnt_table_attributes_len, lnt_attributes_length_offset);
}

// returns the number of lvtt entries
static u2 adjust_local_variable_table(JfrBigEndianWriter& writer,
                                      const u2* utf8_indexes,
                                      u2 bci_adjustment_offset,
                                      const Method* method,
                                      TRAPS) {
  assert(utf8_indexes != nullptr, "invariant");
  assert(method != nullptr, "invariant");
  assert(method->has_localvariable_table(), "invariant");
  writer.write<u2>(utf8_indexes[UTF8_OPT_LocalVariableTable]);
  const jlong lvt_attributes_length_offset = writer.current_offset();
  writer.reserve(sizeof(u4));
  const int lvt_len = method->localvariable_table_length();
  writer.write<u2>((u2)lvt_len);
  const LocalVariableTableElement* table = method->localvariable_table_start();
  assert(table != nullptr, "invariant");
  u2 num_lvtt_entries = 0;
  for (int i = 0; i < lvt_len; ++i) {
    writer.write<u2>(table[i].start_bci + bci_adjustment_offset);
    writer.write<u2>(table[i].length);
    writer.write<u2>(table[i].name_cp_index);
    writer.write<u2>(table[i].descriptor_cp_index);
    writer.write<u2>(table[i].slot);
    if (table[i].signature_cp_index > 0) {
      ++num_lvtt_entries;
    }
  }
  u4 lvt_table_attributes_len = static_cast<u4>(writer.current_offset() - lvt_attributes_length_offset);
  // the lvt_table_attributes_length value is exclusive
  lvt_table_attributes_len -= 4;
  writer.write_at_offset(lvt_table_attributes_len, lvt_attributes_length_offset);
  return num_lvtt_entries;
}

static void adjust_local_variable_type_table(JfrBigEndianWriter& writer,
                                            const u2* utf8_indexes,
                                            u2 bci_adjustment_offset,
                                            u2 num_lvtt_entries,
                                            const Method* method,
                                            TRAPS) {
  assert(num_lvtt_entries > 0, "invariant");
  writer.write<u2>(utf8_indexes[UTF8_OPT_LocalVariableTypeTable]);
  const jlong lvtt_attributes_length_offset = writer.current_offset();
  writer.reserve(sizeof(u4));
  writer.write<u2>(num_lvtt_entries);
  const LocalVariableTableElement* table = method->localvariable_table_start();
  assert(table != nullptr, "invariant");
  const int lvt_len = method->localvariable_table_length();
  for (int i = 0; i < lvt_len; ++i) {
    if (table[i].signature_cp_index > 0) {
      writer.write<u2>(table[i].start_bci + bci_adjustment_offset);
      writer.write<u2>(table[i].length);
      writer.write<u2>(table[i].name_cp_index);
      writer.write<u2>(table[i].signature_cp_index);
      writer.write<u2>(table[i].slot);
    }
  }
  u4 lvtt_table_attributes_len = static_cast<u4>(writer.current_offset() - lvtt_attributes_length_offset);
  // the lvtt_table_attributes_length value is exclusive
  lvtt_table_attributes_len -= 4;
  writer.write_at_offset(lvtt_table_attributes_len, lvtt_attributes_length_offset);
}

static void adjust_code_attributes(JfrBigEndianWriter& writer,
                                   const u2* utf8_indexes,
                                   u2 bci_adjustment_offset,
                                   const Method* clinit_method,
                                   TRAPS) {
  // "Code" attributes
  assert(utf8_indexes != nullptr, "invariant");
  const jlong code_attributes_offset = writer.current_offset();
  writer.reserve(sizeof(u2));
  u2 number_of_code_attributes = 0;
  if (clinit_method != nullptr) {
    Array<u1>* stack_map = clinit_method->stackmap_data();
    if (stack_map != nullptr) {
      ++number_of_code_attributes;
      adjust_stack_map(writer, stack_map, utf8_indexes, bci_adjustment_offset, THREAD);
      assert(writer.is_valid(), "invariant");
    }
    if (clinit_method != nullptr && clinit_method->has_linenumber_table()) {
      ++number_of_code_attributes;
      adjust_line_number_table(writer, utf8_indexes, bci_adjustment_offset, clinit_method, THREAD);
      assert(writer.is_valid(), "invariant");
    }
    if (clinit_method != nullptr && clinit_method->has_localvariable_table()) {
      ++number_of_code_attributes;
      const u2 num_of_lvtt_entries = adjust_local_variable_table(writer, utf8_indexes, bci_adjustment_offset, clinit_method, THREAD);
      assert(writer.is_valid(), "invariant");
      if (num_of_lvtt_entries > 0) {
        ++number_of_code_attributes;
        adjust_local_variable_type_table(writer, utf8_indexes, bci_adjustment_offset, num_of_lvtt_entries, clinit_method, THREAD);
        assert(writer.is_valid(), "invariant");
      }
    }
  }

  // Store the number of code_attributes
  writer.write_at_offset(number_of_code_attributes, code_attributes_offset);
}

static jlong insert_clinit_method(const InstanceKlass* ik,
                                  const ClassFileParser& parser,
                                  JfrBigEndianWriter& writer,
                                  u2 orig_constant_pool_len,
                                  const u2* utf8_indexes,
                                  const u2 register_method_ref_index,
                                  const Method* clinit_method,
                                  TRAPS) {
  assert(utf8_indexes != nullptr, "invariant");
  // The injected code length is always this value.
  // This is to ensure that padding can be done
  // where needed and to simplify size calculations.
  static const u2 injected_code_length = 8;
  const u2 name_index = utf8_indexes[UTF8_OPT_clinit];
  assert(name_index != invalid_cp_index, "invariant");
  const u2 desc_index = utf8_indexes[UTF8_REQ_EMPTY_VOID_METHOD_DESC];
  const u2 max_stack = MAX2<u2>(clinit_method != nullptr ? clinit_method->verifier_max_stack() : 1, 1);
  const u2 max_locals = MAX2<u2>(clinit_method != nullptr ? clinit_method->max_locals() : 0, 0);
  const u2 orig_bytecodes_length = clinit_method != nullptr ? (u2)clinit_method->code_size() : 0;
  const address orig_bytecodes = clinit_method != nullptr ? clinit_method->code_base() : nullptr;
  const u2 new_code_length = injected_code_length + orig_bytecodes_length;
  DEBUG_ONLY(const jlong start_offset = writer.current_offset();)
  writer.write<u2>(JVM_ACC_STATIC); // flags
  writer.write<u2>(name_index);
  writer.write<u2>(desc_index);
  writer.write<u2>((u2)0x1); // attributes_count // "Code"
  assert(writer.is_valid(), "invariant");
  DEBUG_ONLY(assert(start_offset + 8 == writer.current_offset(), "invariant");)
  // "Code" attribute
  writer.write<u2>(utf8_indexes[UTF8_REQ_Code]); // "Code"
  const jlong code_attribute_length_offset = writer.current_offset();
  writer.reserve(sizeof(u4));
  writer.write<u2>(max_stack); // max stack
  writer.write<u2>(max_locals); // max locals
  writer.write<u4>((u4)new_code_length); // code length

  /* BEGIN CLINIT CODE */

  // Note the use of ldc_w here instead of ldc.
  // This is to handle all values of "this_class_index"
  writer.write<u1>((u1)Bytecodes::_ldc_w);
  writer.write<u2>((u2)parser.this_class_index()); // load constant "this class"
  writer.write<u1>((u1)Bytecodes::_invokestatic);
  // invoke "FlightRecorder.register(Ljava/lang/Class;")
  writer.write<u2>(register_method_ref_index);
  if (clinit_method == nullptr) {
    writer.write<u1>((u1)Bytecodes::_nop);
    writer.write<u1>((u1)Bytecodes::_return);
  } else {
    // If we are pre-pending to original code,
    // do padding to minimize disruption to the original.
    // It might have dependencies on 4-byte boundaries
    // i.e. lookupswitch and tableswitch instructions
    writer.write<u1>((u1)Bytecodes::_nop);
    writer.write<u1>((u1)Bytecodes::_nop);
    // insert original clinit code
    writer.write_bytes(orig_bytecodes, orig_bytecodes_length);
  }

  /* END CLINIT CODE */

  assert(writer.is_valid(), "invariant");
  adjust_exception_table(writer, injected_code_length, clinit_method, THREAD);
  assert(writer.is_valid(), "invariant");
  adjust_code_attributes(writer, utf8_indexes, injected_code_length, clinit_method, THREAD);
  assert(writer.is_valid(), "invariant");
  u4 code_attribute_len = static_cast<u4>(writer.current_offset() - code_attribute_length_offset);
  // the code_attribute_length value is exclusive
  code_attribute_len -= 4;
  writer.write_at_offset(code_attribute_len, code_attribute_length_offset);
  return writer.current_offset();
}

static Symbol* begin = nullptr;
static Symbol* end = nullptr;
static Symbol* commit = nullptr;
static Symbol* isEnabled = nullptr;
static Symbol* shouldCommit = nullptr;
static Symbol* void_method_sig = nullptr;
static Symbol* boolean_method_sig = nullptr;

static void initialize_symbols() {
  if (begin == nullptr) {
    begin = SymbolTable::probe("begin", 5);
    assert(begin != nullptr, "invariant");
    end = SymbolTable::probe("end", 3);
    assert(end != nullptr, "invariant");
    commit = SymbolTable::probe("commit", 6);
    assert(commit != nullptr, "invariant");
    isEnabled = SymbolTable::probe("isEnabled", 9);
    assert(isEnabled != nullptr, "invariant");
    shouldCommit = SymbolTable::probe("shouldCommit", 12);
    assert(shouldCommit != nullptr, "invariant");
    void_method_sig = SymbolTable::probe("()V", 3);
    assert(void_method_sig != nullptr, "invariant");
    boolean_method_sig = SymbolTable::probe("()Z", 3);
    assert(boolean_method_sig != nullptr, "invariant");
  }
}

// Caller needs ResourceMark
static ClassFileStream* schema_extend_event_klass_bytes(const InstanceKlass* ik, const ClassFileParser& parser, TRAPS) {
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  initialize_symbols();
  static const u2 public_final_flag_mask = JVM_ACC_PUBLIC | JVM_ACC_FINAL;
  const ClassFileStream* const orig_stream = parser.clone_stream();
  assert(orig_stream != nullptr, "invariant");
  const int orig_stream_length = orig_stream->length();
  // allocate an identically sized buffer
  u1* const new_buffer = NEW_RESOURCE_ARRAY_IN_THREAD_RETURN_NULL(THREAD, u1, orig_stream_length);
  if (new_buffer == nullptr) {
    return nullptr;
  }
  assert(new_buffer != nullptr, "invariant");
  // memcpy the entire [B
  memcpy(new_buffer, orig_stream->buffer(), orig_stream_length);
  const u2 orig_cp_len = position_stream_after_cp(orig_stream);
  assert(orig_cp_len > 0, "invariant");
  assert(orig_stream->current_offset() > 0, "invariant");
  orig_stream->skip_u2_fast(3); // access_flags, this_class_index, super_class_index
  const u2 iface_len = orig_stream->get_u2_fast();
  orig_stream->skip_u2_fast(iface_len);
  // fields len
  const u2 orig_fields_len = orig_stream->get_u2_fast();
  // fields
  for (u2 i = 0; i < orig_fields_len; ++i) {
    orig_stream->skip_u2_fast(3);
    const u2 attrib_info_len = orig_stream->get_u2_fast();
    for (u2 j = 0; j < attrib_info_len; ++j) {
      orig_stream->skip_u2_fast(1);
      const u4 attrib_len = orig_stream->get_u4_fast();
      orig_stream->skip_u1_fast(attrib_len);
    }
  }
  // methods
  const u2 orig_methods_len = orig_stream->get_u2_fast();
  for (u2 i = 0; i < orig_methods_len; ++i) {
    const u4 access_flag_offset = orig_stream->current_offset();
    const u2 flags = orig_stream->get_u2_fast();
    // Rewrite JVM_ACC_FINAL -> JVM_ACC_PUBLIC
    if (public_final_flag_mask == flags) {
      JfrBigEndianWriter accessflagsrewriter(new_buffer + access_flag_offset, sizeof(u2));
      accessflagsrewriter.write<u2>(JVM_ACC_PUBLIC);
      assert(accessflagsrewriter.is_valid(), "invariant");
    }
    orig_stream->skip_u2_fast(2);
    const u2 attributes_count = orig_stream->get_u2_fast();
    for (u2 j = 0; j < attributes_count; ++j) {
      orig_stream->skip_u2_fast(1);
      const u4 attrib_len = orig_stream->get_u4_fast();
      orig_stream->skip_u1_fast(attrib_len);
    }
  }
  return new ClassFileStream(new_buffer, orig_stream_length, nullptr);
}

// Attempt to locate an existing UTF8_INFO mapping the utf8_constant.
// If no UTF8_INFO exists, add (append) a new one to the constant pool.
static u2 find_or_add_utf8_info(JfrBigEndianWriter& writer,
                                const InstanceKlass* ik,
                                const char* const utf8_constant,
                                u2 orig_cp_len,
                                u2& added_cp_entries,
                                TRAPS) {
  assert(utf8_constant != nullptr, "invariant");
  TempNewSymbol utf8_sym = SymbolTable::new_symbol(utf8_constant);
  // lookup existing
  const u2 utf8_orig_idx = utf8_info_index(ik, utf8_sym, THREAD);
  if (utf8_orig_idx != invalid_cp_index) {
    // existing constant pool entry found
    return utf8_orig_idx;
  }
  // no existing match, need to add a new utf8 cp entry
  assert(invalid_cp_index == utf8_orig_idx, "invariant");
  // add / append new
  return add_utf8_info(writer, utf8_constant, orig_cp_len, added_cp_entries);
}

/*
 * This routine will resolve the required utf8_constants array
 * to their constant pool indexes (mapping to their UTF8_INFO's)
 * Only if a constant is actually needed and does not already exist
 * will it be added.
 *
 * The passed in indexes array will be populated with the resolved indexes.
 * The number of newly added constant pool entries is returned.
 */
static u2 resolve_utf8_indexes(JfrBigEndianWriter& writer,
                               const InstanceKlass* ik,
                               u2* const utf8_indexes,
                               u2 orig_cp_len,
                               const Method* clinit_method,
                               bool register_klass,
                               bool untypedEventConfiguration,
                               TRAPS) {
  assert(utf8_indexes != nullptr, "invariant");
  u2 added_cp_entries = 0;
  // resolve all required symbols
  for (u2 index = 0; index < NOF_UTF8_REQ_SYMBOLS; ++index) {
    utf8_indexes[index] = find_or_add_utf8_info(writer, ik, utf8_constants[index], orig_cp_len, added_cp_entries, THREAD);
  }

  // resolve optional constants
  utf8_indexes[UTF8_OPT_eventConfiguration_FIELD_DESC] = untypedEventConfiguration ? invalid_cp_index :
    find_or_add_utf8_info(writer, ik, utf8_constants[UTF8_OPT_eventConfiguration_FIELD_DESC], orig_cp_len, added_cp_entries, THREAD);

  utf8_indexes[UTF8_OPT_LjavaLangObject] = untypedEventConfiguration ?
    find_or_add_utf8_info(writer, ik, utf8_constants[UTF8_OPT_LjavaLangObject], orig_cp_len, added_cp_entries, THREAD) : invalid_cp_index;

  if (register_klass) {
    utf8_indexes[UTF8_OPT_clinit] =
      find_or_add_utf8_info(writer, ik, utf8_constants[UTF8_OPT_clinit], orig_cp_len, added_cp_entries, THREAD);
    utf8_indexes[UTF8_OPT_FlightRecorder] =
      find_or_add_utf8_info(writer, ik, utf8_constants[UTF8_OPT_FlightRecorder], orig_cp_len, added_cp_entries, THREAD);
    utf8_indexes[UTF8_OPT_register] =
      find_or_add_utf8_info(writer, ik, utf8_constants[UTF8_OPT_register], orig_cp_len, added_cp_entries, THREAD);
    utf8_indexes[UTF8_OPT_CLASS_VOID_METHOD_DESC] =
      find_or_add_utf8_info(writer, ik, utf8_constants[UTF8_OPT_CLASS_VOID_METHOD_DESC], orig_cp_len, added_cp_entries, THREAD);
  } else {
    utf8_indexes[UTF8_OPT_clinit] = invalid_cp_index;
    utf8_indexes[UTF8_OPT_FlightRecorder] = invalid_cp_index;
    utf8_indexes[UTF8_OPT_register] = invalid_cp_index;
    utf8_indexes[UTF8_OPT_CLASS_VOID_METHOD_DESC] = invalid_cp_index;
  }

  if (clinit_method != nullptr && clinit_method->has_stackmap_table()) {
    utf8_indexes[UTF8_OPT_StackMapTable] =
      find_or_add_utf8_info(writer, ik, utf8_constants[UTF8_OPT_StackMapTable], orig_cp_len, added_cp_entries, THREAD);
  } else {
    utf8_indexes[UTF8_OPT_StackMapTable] = invalid_cp_index;
  }

  if (clinit_method != nullptr && clinit_method->has_linenumber_table()) {
    utf8_indexes[UTF8_OPT_LineNumberTable] =
      find_or_add_utf8_info(writer, ik, utf8_constants[UTF8_OPT_LineNumberTable], orig_cp_len, added_cp_entries, THREAD);
  } else {
    utf8_indexes[UTF8_OPT_LineNumberTable] = invalid_cp_index;
  }

  if (clinit_method != nullptr && clinit_method->has_localvariable_table()) {
    utf8_indexes[UTF8_OPT_LocalVariableTable] =
      find_or_add_utf8_info(writer, ik, utf8_constants[UTF8_OPT_LocalVariableTable], orig_cp_len, added_cp_entries, THREAD);
    utf8_indexes[UTF8_OPT_LocalVariableTypeTable] =
      find_or_add_utf8_info(writer, ik, utf8_constants[UTF8_OPT_LocalVariableTypeTable], orig_cp_len, added_cp_entries, THREAD);
  } else {
    utf8_indexes[UTF8_OPT_LocalVariableTable] = invalid_cp_index;
    utf8_indexes[UTF8_OPT_LocalVariableTypeTable] = invalid_cp_index;
  }

  return added_cp_entries;
}

static u1* schema_extend_event_subklass_bytes(const InstanceKlass* ik,
                                              const ClassFileParser& parser,
                                              jint& size_of_new_bytes,
                                              TRAPS) {
  assert(ik != nullptr, "invariant");
  // If the class already has a clinit method
  // we need to take that into account
  const Method* clinit_method = ik->class_initializer();
  bool untypedEventHandler = false;
  const bool register_klass = should_register_klass(ik, untypedEventHandler);
  const ClassFileStream* const orig_stream = parser.clone_stream();
  const int orig_stream_size = orig_stream->length();
  assert(orig_stream->current_offset() == 0, "invariant");
  const u2 orig_cp_len = position_stream_after_cp(orig_stream);
  assert(orig_cp_len > 0, "invariant");
  assert(orig_stream->current_offset() > 0, "invariant");
  // Dimension and allocate a working byte buffer
  // to be used in building up a modified class [B.
  const jint new_buffer_size = extra_stream_bytes + orig_stream_size;
  u1* const new_buffer = NEW_RESOURCE_ARRAY_IN_THREAD_RETURN_NULL(THREAD, u1, new_buffer_size);
  if (new_buffer == nullptr) {
    log_error(jfr, system) ("Thread local allocation (native) for %zu"
      " bytes failed in JfrEventClassTransformer::on_klass_creation", static_cast<size_t>(new_buffer_size));
    return nullptr;
  }
  assert(new_buffer != nullptr, "invariant");
  // [B wrapped in a big endian writer
  JfrBigEndianWriter writer(new_buffer, new_buffer_size);
  assert(writer.current_offset() == 0, "invariant");
  const u4 orig_access_flag_offset = orig_stream->current_offset();
  // Copy original stream from the beginning up to AccessFlags
  // This means the original constant pool contents are copied unmodified
  writer.write_bytes(orig_stream->buffer(), orig_access_flag_offset);
  assert(writer.is_valid(), "invariant");
  assert(writer.current_offset() == (intptr_t)orig_access_flag_offset, "invariant"); // same positions
  // Our writer now sits just after the last original constant pool entry.
  // I.e. we are in a good position to append new constant pool entries
  // This array will contain the resolved indexes
  // in order to reference UTF8_INFO's needed
  u2 utf8_indexes[NOF_UTF8_SYMBOLS];
  // Resolve_utf8_indexes will be conservative in attempting to
  // locate an existing UTF8_INFO; it will only append constants
  // that is absolutely required
  u2 number_of_new_constants =
    resolve_utf8_indexes(writer, ik, utf8_indexes, orig_cp_len, clinit_method, register_klass, untypedEventHandler, THREAD);
  // UTF8_INFO entries now added to the constant pool
  // In order to invoke a method we would need additional
  // constants, JVM_CONSTANT_Class, JVM_CONSTANT_NameAndType
  // and JVM_CONSTANT_Methodref.
  const u2 flr_register_method_ref_index =
    register_klass ?
      add_flr_register_method_constants(writer,
                                        utf8_indexes,
                                        orig_cp_len,
                                        number_of_new_constants,
                                        THREAD) :  invalid_cp_index;

  // New constant pool entries added and all UTF8_INFO indexes resolved
  // Now update the class file constant_pool_count with an updated count
  writer.write_at_offset<u2>(orig_cp_len + number_of_new_constants, 8);
  assert(writer.is_valid(), "invariant");
  orig_stream->skip_u2_fast(3); // access_flags, this_class_index, super_class_index
  const u2 iface_len = orig_stream->get_u2_fast(); // interfaces
  orig_stream->skip_u2_fast(iface_len);
  const u4 orig_fields_len_offset = orig_stream->current_offset();
  // Copy from AccessFlags up to and including interfaces
  writer.write_bytes(orig_stream->buffer() + orig_access_flag_offset,
                     orig_fields_len_offset - orig_access_flag_offset);
  assert(writer.is_valid(), "invariant");
  const jlong new_fields_len_offset = writer.current_offset();
  const u2 orig_fields_len = position_stream_after_fields(orig_stream);
  u4 orig_method_len_offset = orig_stream->current_offset();
  // Copy up to and including fields
  writer.write_bytes(orig_stream->buffer() + orig_fields_len_offset, orig_method_len_offset - orig_fields_len_offset);
  assert(writer.is_valid(), "invariant");
  // We are sitting just after the original number of field_infos
  // so this is a position where we can add (append) new field_infos
  const u2 number_of_new_fields = add_field_infos(writer, utf8_indexes, untypedEventHandler);
  assert(writer.is_valid(), "invariant");
  const jlong new_method_len_offset = writer.current_offset();
  // Additional field_infos added, update classfile fields_count
  writer.write_at_offset<u2>(orig_fields_len + number_of_new_fields, new_fields_len_offset);
  assert(writer.is_valid(), "invariant");
  // Our current location is now at classfile methods_count
  const u2 orig_methods_len = position_stream_after_methods(writer,
                                                            orig_stream,
                                                            utf8_indexes,
                                                            register_klass,
                                                            clinit_method,
                                                            orig_method_len_offset);
  const u4 orig_attributes_count_offset = orig_stream->current_offset();
  // Copy existing methods
  writer.write_bytes(orig_stream->buffer() + orig_method_len_offset, orig_attributes_count_offset - orig_method_len_offset);
  assert(writer.is_valid(), "invariant");
  // We are sitting just after the original number of method_infos
  // so this is a position where we can add (append) new method_infos
  u2 number_of_new_methods = add_method_infos(writer, utf8_indexes);

  // We have just added the new methods.
  //
  // What about the state of <clinit>?
  // We would need to do:
  // 1. Nothing (@Registered(false) annotation)
  // 2. Build up a new <clinit> - and if the original class already contains a <clinit>,
  //                              merging will be necessary.
  //
  if (register_klass) {
    insert_clinit_method(ik, parser, writer, orig_cp_len, utf8_indexes, flr_register_method_ref_index, clinit_method, THREAD);
    if (clinit_method == nullptr) {
      ++number_of_new_methods;
    }
  }
  // Update classfile methods_count
  writer.write_at_offset<u2>(orig_methods_len + number_of_new_methods, new_method_len_offset);
  assert(writer.is_valid(), "invariant");
  // Copy last remaining bytes
  writer.write_bytes(orig_stream->buffer() + orig_attributes_count_offset, orig_stream_size - orig_attributes_count_offset);
  assert(writer.is_valid(), "invariant");
  assert(writer.current_offset() > orig_stream->length(), "invariant");
  size_of_new_bytes = (jint)writer.current_offset();
  return new_buffer;
}

static bool should_force_instrumentation() {
  return !JfrOptionSet::allow_event_retransforms() || JfrEventClassTransformer::is_force_instrumentation();
}

static void log_pending_exception(oop throwable) {
  assert(throwable != nullptr, "invariant");
  oop msg = java_lang_Throwable::message(throwable);
  if (msg != nullptr) {
    char* text = java_lang_String::as_utf8_string(msg);
    if (text != nullptr) {
      log_error(jfr, system) ("%s", text);
    }
  }
}

static bool has_pending_exception(TRAPS) {
  assert(THREAD != nullptr, "invariant");
  if (HAS_PENDING_EXCEPTION) {
    log_pending_exception(PENDING_EXCEPTION);
    CLEAR_PENDING_EXCEPTION;
    return true;
  }
  return false;
}

static bool has_local_method_implementation(const InstanceKlass* ik, const Symbol* name, const Symbol* signature) {
  assert(ik != nullptr, "invariant");
  assert(name != nullptr, "invariant");
  assert(signature != nullptr, "invariant");
  return nullptr != ik->find_local_method(name, signature, Klass::OverpassLookupMode::skip, Klass::StaticLookupMode::find,
                                          Klass::PrivateLookupMode::find);
}

// If for a subklass, on initial class load, an implementation exist for any of the final methods declared in Event,
// then constraints are considered breached.
static bool invalid_preconditions_for_subklass_on_initial_load(const InstanceKlass* ik) {
  assert(ik != nullptr, "invariant");
  return has_local_method_implementation(ik, begin, void_method_sig) ||
         has_local_method_implementation(ik, end, void_method_sig) ||
         has_local_method_implementation(ik, commit, void_method_sig) ||
         has_local_method_implementation(ik, isEnabled, boolean_method_sig) ||
         has_local_method_implementation(ik, shouldCommit, boolean_method_sig);
}

static ClassFileStream* schema_extend_event_subklass_bytes(const InstanceKlass* ik, const ClassFileParser& parser, bool& is_instrumented, TRAPS) {
  assert(JdkJfrEvent::is_a(ik), "invariant");
  assert(!is_instrumented, "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  if (invalid_preconditions_for_subklass_on_initial_load(ik)) {
    // Remove the tag denoting this as a jdk.jfr.Event subklass. No instrumentation, hence no events can be written.
    // The class is allowed to load as-is, but it is classified as outside of the jfr system.
    JdkJfrEvent::remove(ik);
    return nullptr;
  }
  jint size_of_new_bytes = 0;
  const u1* new_bytes = schema_extend_event_subklass_bytes(ik, parser, size_of_new_bytes, THREAD);
  if (new_bytes == nullptr) {
    return nullptr;
  }
  assert(new_bytes != nullptr, "invariant");
  assert(size_of_new_bytes > 0, "invariant");
  const bool force_instrumentation = should_force_instrumentation();
  if (Jfr::is_recording() || force_instrumentation) {
    jint size_of_instrumented_bytes = 0;
    unsigned char* instrumented_bytes = nullptr;
    const jclass super = static_cast<jclass>(JfrJavaSupport::local_jni_handle(ik->super()->java_mirror(), THREAD));
    const jboolean boot_class_loader = ik->class_loader_data()->is_boot_class_loader_data();
    JfrUpcalls::new_bytes_eager_instrumentation(JfrTraceId::load_raw(ik),
                                                force_instrumentation,
                                                boot_class_loader,
                                                super,
                                                size_of_new_bytes,
                                                new_bytes,
                                                &size_of_instrumented_bytes,
                                                &instrumented_bytes,
                                                THREAD);
    JfrJavaSupport::destroy_local_jni_handle(super);
    if (has_pending_exception(THREAD)) {
      return nullptr;
    }
    assert(instrumented_bytes != nullptr, "invariant");
    assert(size_of_instrumented_bytes > 0, "invariant");
    new_bytes = instrumented_bytes;
    size_of_new_bytes = size_of_instrumented_bytes;
    is_instrumented = true;
  }
  return new ClassFileStream(new_bytes, size_of_new_bytes, nullptr);
}

static bool _force_instrumentation = false;

void JfrEventClassTransformer::set_force_instrumentation(bool force_instrumentation) {
  _force_instrumentation = force_instrumentation;
}

bool JfrEventClassTransformer::is_force_instrumentation() {
  return _force_instrumentation;
}

static ClassFileStream* retransform_bytes(const Klass* existing_klass, const ClassFileParser& parser, bool& is_instrumented, TRAPS) {
  assert(existing_klass != nullptr, "invariant");
  assert(!is_instrumented, "invariant");
  assert(JdkJfrEvent::is_a(existing_klass) || JdkJfrEvent::is_host(existing_klass), "invariant");
  DEBUG_ONLY(JfrJavaSupport::check_java_thread_in_vm(THREAD));
  jint size_of_new_bytes = 0;
  unsigned char* new_bytes = nullptr;
  const ClassFileStream* const stream = parser.clone_stream();
  assert(stream != nullptr, "invariant");
  const jclass clazz = static_cast<jclass>(JfrJavaSupport::local_jni_handle(existing_klass->java_mirror(), THREAD));
  JfrUpcalls::on_retransform(JfrTraceId::load_raw(existing_klass),
                              clazz,
                              stream->length(),
                              stream->buffer(),
                              &size_of_new_bytes,
                              &new_bytes,
                              THREAD);
  JfrJavaSupport::destroy_local_jni_handle(clazz);
  if (has_pending_exception(THREAD)) {
    return nullptr;
  }
  assert(new_bytes != nullptr, "invariant");
  assert(size_of_new_bytes > 0, "invariant");
  is_instrumented = true;
  return new ClassFileStream(new_bytes, size_of_new_bytes, nullptr);
}

// If code size is 1, it is 0xb1, i.e. the return instruction.
static inline bool is_commit_method_instrumented(const Method* m) {
  assert(m != nullptr, "invariant");
  assert(m->name() == commit, "invariant");
  assert(m->constMethod()->code_size() > 0, "invariant");
  return m->constMethod()->code_size() > 1;
}

static bool bless_static_commit_method(const Array<Method*>* methods) {
  assert(methods != nullptr, "invariant");
  for (int i = 0; i < methods->length(); ++i) {
    const Method* const m = methods->at(i);
    // Method is of the form "static void UserEvent::commit(...)" and instrumented
    if (m->is_static() && m->name() == commit && is_commit_method_instrumented(m)) {
      BLESS_METHOD(m);
      return true;
    }
  }
  return false;
}

static void bless_instance_commit_method(const Array<Method*>* methods) {
  assert(methods != nullptr, "invariant");
  for (int i = 0; i < methods->length(); ++i) {
    const Method* const m = methods->at(i);
    // Method is of the form "void UserEvent:commit()" and instrumented
    if (!m->is_static() &&
         m->name() == commit &&
         m->signature() == void_method_sig &&
         is_commit_method_instrumented(m)) {
      BLESS_METHOD(m);
    }
  }
}

// A blessed method is a method that is allowed to link to system sensitive code.
// It is primarily the class file schema extended instance 'commit()V' method.
// Jdk events can also define a static commit method with an arbitrary signature.
static void bless_commit_method(const InstanceKlass* new_ik) {
  assert(new_ik != nullptr, "invariant");
  assert(JdkJfrEvent::is_subklass(new_ik), "invariant");
  const Array<Method*>* const methods = new_ik->methods();
  if (new_ik->class_loader() == nullptr) {
    // JDK events are allowed an additional commit method that is static.
    // Search precedence must therefore inspect static methods first.
    if (bless_static_commit_method(methods)) {
      return;
    }
  }
  bless_instance_commit_method(methods);
}

static void transform(InstanceKlass*& ik, ClassFileParser& parser, JavaThread* thread) {
  assert(IS_EVENT_OR_HOST_KLASS(ik), "invariant");
  bool is_instrumented = false;
  ClassFileStream* stream = nullptr;
  const Klass* const existing_klass = JfrClassTransformer::find_existing_klass(ik, thread);
  if (existing_klass != nullptr) {
    // There is already a klass defined, implying we are redefining / retransforming.
    stream = retransform_bytes(existing_klass, parser, is_instrumented, thread);
  } else {
    // No existing klass, implying this is the initial load.
    stream = JdkJfrEvent::is(ik) ? schema_extend_event_klass_bytes(ik, parser, thread) : schema_extend_event_subklass_bytes(ik, parser, is_instrumented, thread);
  }
  InstanceKlass* const new_ik = JfrClassTransformer::create_instance_klass(ik, stream, existing_klass == nullptr, thread);
  if (new_ik == nullptr) {
    return;
  }
  if (existing_klass != nullptr) {
    JfrClassTransformer::transfer_cached_class_file_data(ik, new_ik, parser, thread);
  } else {
    JfrClassTransformer::cache_class_file_data(new_ik, stream, thread);
  }
  if (is_instrumented && JdkJfrEvent::is_subklass(new_ik)) {
    bless_commit_method(new_ik);
  }
  JfrClassTransformer::copy_traceid(ik, new_ik);
  JfrClassTransformer::rewrite_klass_pointer(ik, new_ik, parser, thread);
}

// Target for the JFR_ON_KLASS_CREATION hook.
// Extends the class file schema on initial class load or reinstruments on redefine / retransform.
// The passed in parameter 'ik' acts as an in-out parameter: it is rewritten to point to a replaced
// instance of the passed in InstanceKlass. The original 'ik' will be set onto the passed parser,
// for destruction when the parser goes out of scope.
void JfrEventClassTransformer::on_klass_creation(InstanceKlass*& ik, ClassFileParser& parser, TRAPS) {
  assert(ik != nullptr, "invariant");
  assert(IS_EVENT_OR_HOST_KLASS(ik), "invariant");
  if (ik->is_abstract() && !JdkJfrEvent::is(ik)) {
    assert(JdkJfrEvent::is_subklass(ik), "invariant");
    // Abstract subklasses are not instrumented.
    return;
  }
  ResourceMark rm(THREAD);
  HandleMark hm(THREAD);
  transform(ik, parser, THREAD);
}

static bool is_static_commit_method_blessed(const Array<Method*>* methods) {
  assert(methods != nullptr, "invariant");
  for (int i = 0; i < methods->length(); ++i) {
    const Method* const m = methods->at(i);
    // Must be of form: static void UserEvent::commit(...)
    if (m->is_static() && m->name() == commit) {
      return IS_METHOD_BLESSED(m);
    }
  }
  return false;
}

static bool is_instance_commit_method_blessed(const Array<Method*>* methods) {
  assert(methods != nullptr, "invariant");
  for (int i = 0; i < methods->length(); ++i) {
    const Method* const m = methods->at(i);
    // Must be of form: void UserEvent::commit()
    if (!m->is_static() && m->name() == commit && m->signature() == void_method_sig) {
      return IS_METHOD_BLESSED(m);
    }
  }
  return false;
}

bool JfrEventClassTransformer::is_instrumented(const InstanceKlass* ik) {
  assert(ik != nullptr, "invariant");
  assert(JdkJfrEvent::is_subklass(ik), "invariant");
  const Array<Method*>* const methods = ik->methods();
  if (ik->class_loader() == nullptr) {
    // JDK events are allowed an additional commit method that is static.
    // Search precedence must therefore inspect static methods first.
    if (is_static_commit_method_blessed(methods)) {
      return true;
    }
  }
  return is_instance_commit_method_blessed(methods);
}

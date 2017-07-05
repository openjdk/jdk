/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/symbolTable.hpp"
#include "interpreter/bytecodeStream.hpp"
#include "prims/jvmtiClassFileReconstituter.hpp"
#include "runtime/signature.hpp"
#ifdef TARGET_ARCH_x86
# include "bytes_x86.hpp"
#endif
#ifdef TARGET_ARCH_sparc
# include "bytes_sparc.hpp"
#endif
#ifdef TARGET_ARCH_zero
# include "bytes_zero.hpp"
#endif
// FIXME: add Deprecated, LVT, LVTT attributes
// FIXME: fix Synthetic attribute
// FIXME: per Serguei, add error return handling for constantPoolOopDesc::copy_cpool_bytes()


// Write the field information portion of ClassFile structure
// JVMSpec|     u2 fields_count;
// JVMSpec|     field_info fields[fields_count];
void JvmtiClassFileReconstituter::write_field_infos() {
  HandleMark hm(thread());
  typeArrayHandle fields(thread(), ikh()->fields());
  int fields_length = fields->length();
  int num_fields = fields_length / instanceKlass::next_offset;
  objArrayHandle fields_anno(thread(), ikh()->fields_annotations());

  write_u2(num_fields);
  for (int index = 0; index < fields_length; index += instanceKlass::next_offset) {
    AccessFlags access_flags;
    int flags = fields->ushort_at(index + instanceKlass::access_flags_offset);
    access_flags.set_flags(flags);
    int name_index = fields->ushort_at(index + instanceKlass::name_index_offset);
    int signature_index = fields->ushort_at(index + instanceKlass::signature_index_offset);
    int initial_value_index = fields->ushort_at(index + instanceKlass::initval_index_offset);
    guarantee(name_index != 0 && signature_index != 0, "bad constant pool index for field");
    int offset = ikh()->offset_from_fields( index );
    int generic_signature_index =
                        fields->ushort_at(index + instanceKlass::generic_signature_offset);
    typeArrayHandle anno(thread(), fields_anno.not_null() ?
                                 (typeArrayOop)(fields_anno->obj_at(index / instanceKlass::next_offset)) :
                                 (typeArrayOop)NULL);

    // JVMSpec|   field_info {
    // JVMSpec|         u2 access_flags;
    // JVMSpec|         u2 name_index;
    // JVMSpec|         u2 descriptor_index;
    // JVMSpec|         u2 attributes_count;
    // JVMSpec|         attribute_info attributes[attributes_count];
    // JVMSpec|   }

    write_u2(flags & JVM_RECOGNIZED_FIELD_MODIFIERS);
    write_u2(name_index);
    write_u2(signature_index);
    int attr_count = 0;
    if (initial_value_index != 0) {
      ++attr_count;
    }
    if (access_flags.is_synthetic()) {
      // ++attr_count;
    }
    if (generic_signature_index != 0) {
      ++attr_count;
    }
    if (anno.not_null()) {
      ++attr_count;     // has RuntimeVisibleAnnotations attribute
    }

    write_u2(attr_count);

    if (initial_value_index != 0) {
      write_attribute_name_index("ConstantValue");
      write_u4(2); //length always 2
      write_u2(initial_value_index);
    }
    if (access_flags.is_synthetic()) {
      // write_synthetic_attribute();
    }
    if (generic_signature_index != 0) {
      write_signature_attribute(generic_signature_index);
    }
    if (anno.not_null()) {
      write_annotations_attribute("RuntimeVisibleAnnotations", anno);
    }
  }
}

// Write Code attribute
// JVMSpec|   Code_attribute {
// JVMSpec|     u2 attribute_name_index;
// JVMSpec|     u4 attribute_length;
// JVMSpec|     u2 max_stack;
// JVMSpec|     u2 max_locals;
// JVMSpec|     u4 code_length;
// JVMSpec|     u1 code[code_length];
// JVMSpec|     u2 exception_table_length;
// JVMSpec|     {       u2 start_pc;
// JVMSpec|             u2 end_pc;
// JVMSpec|             u2  handler_pc;
// JVMSpec|             u2  catch_type;
// JVMSpec|     }       exception_table[exception_table_length];
// JVMSpec|     u2 attributes_count;
// JVMSpec|     attribute_info attributes[attributes_count];
// JVMSpec|   }
void JvmtiClassFileReconstituter::write_code_attribute(methodHandle method) {
  constMethodHandle const_method(thread(), method->constMethod());
  u2 line_num_cnt = 0;
  int stackmap_len = 0;

  // compute number and length of attributes -- FIXME: for now no LVT
  int attr_count = 0;
  int attr_size = 0;
  if (const_method->has_linenumber_table()) {
    line_num_cnt = line_number_table_entries(method);
    if (line_num_cnt != 0) {
      ++attr_count;
      // Compute the complete size of the line number table attribute:
      //      LineNumberTable_attribute {
      //        u2 attribute_name_index;
      //        u4 attribute_length;
      //        u2 line_number_table_length;
      //        {  u2 start_pc;
      //           u2 line_number;
      //        } line_number_table[line_number_table_length];
      //      }
      attr_size += 2 + 4 + 2 + line_num_cnt * (2 + 2);
    }
  }
  if (method->has_stackmap_table()) {
    stackmap_len = method->stackmap_data()->length();
    if (stackmap_len != 0) {
      ++attr_count;
      // Compute the  size of the stack map table attribute (VM stores raw):
      //      StackMapTable_attribute {
      //        u2 attribute_name_index;
      //        u4 attribute_length;
      //        u2 number_of_entries;
      //        stack_map_frame_entries[number_of_entries];
      //      }
      attr_size += 2 + 4 + stackmap_len;
    }
  }

  typeArrayHandle exception_table(thread(), const_method->exception_table());
  int exception_table_length = exception_table->length();
  int exception_table_entries = exception_table_length / 4;
  int code_size = const_method->code_size();
  int size =
    2+2+4 +                                // max_stack, max_locals, code_length
    code_size +                            // code
    2 +                                    // exception_table_length
    (2+2+2+2) * exception_table_entries +  // exception_table
    2 +                                    // attributes_count
    attr_size;                             // attributes

  write_attribute_name_index("Code");
  write_u4(size);
  write_u2(method->max_stack());
  write_u2(method->max_locals());
  write_u4(code_size);
  copy_bytecodes(method, (unsigned char*)writeable_address(code_size));
  write_u2(exception_table_entries);
  for (int index = 0; index < exception_table_length; ) {
    write_u2(exception_table->int_at(index++));
    write_u2(exception_table->int_at(index++));
    write_u2(exception_table->int_at(index++));
    write_u2(exception_table->int_at(index++));
  }
  write_u2(attr_count);
  if (line_num_cnt != 0) {
    write_line_number_table_attribute(method, line_num_cnt);
  }
  if (stackmap_len != 0) {
    write_stackmap_table_attribute(method, stackmap_len);
  }

  // FIXME: write LVT attribute
}

// Write Exceptions attribute
// JVMSpec|   Exceptions_attribute {
// JVMSpec|     u2 attribute_name_index;
// JVMSpec|     u4 attribute_length;
// JVMSpec|     u2 number_of_exceptions;
// JVMSpec|     u2 exception_index_table[number_of_exceptions];
// JVMSpec|   }
void JvmtiClassFileReconstituter::write_exceptions_attribute(constMethodHandle const_method) {
  CheckedExceptionElement* checked_exceptions = const_method->checked_exceptions_start();
  int checked_exceptions_length = const_method->checked_exceptions_length();
  int size =
    2 +                                    // number_of_exceptions
    2 * checked_exceptions_length;         // exception_index_table

  write_attribute_name_index("Exceptions");
  write_u4(size);
  write_u2(checked_exceptions_length);
  for (int index = 0; index < checked_exceptions_length; index++) {
    write_u2(checked_exceptions[index].class_cp_index);
  }
}

// Write SourceFile attribute
// JVMSpec|   SourceFile_attribute {
// JVMSpec|     u2 attribute_name_index;
// JVMSpec|     u4 attribute_length;
// JVMSpec|     u2 sourcefile_index;
// JVMSpec|   }
void JvmtiClassFileReconstituter::write_source_file_attribute() {
  assert(ikh()->source_file_name() != NULL, "caller must check");

  write_attribute_name_index("SourceFile");
  write_u4(2);  // always length 2
  write_u2(symbol_to_cpool_index(ikh()->source_file_name()));
}

// Write SourceDebugExtension attribute
// JSR45|   SourceDebugExtension_attribute {
// JSR45|       u2 attribute_name_index;
// JSR45|       u4 attribute_length;
// JSR45|       u2 sourcefile_index;
// JSR45|   }
void JvmtiClassFileReconstituter::write_source_debug_extension_attribute() {
  assert(ikh()->source_debug_extension() != NULL, "caller must check");

  write_attribute_name_index("SourceDebugExtension");
  write_u4(2);  // always length 2
  write_u2(symbol_to_cpool_index(ikh()->source_debug_extension()));
}

// Write (generic) Signature attribute
// JVMSpec|   Signature_attribute {
// JVMSpec|     u2 attribute_name_index;
// JVMSpec|     u4 attribute_length;
// JVMSpec|     u2 signature_index;
// JVMSpec|   }
void JvmtiClassFileReconstituter::write_signature_attribute(u2 generic_signature_index) {
  write_attribute_name_index("Signature");
  write_u4(2);  // always length 2
  write_u2(generic_signature_index);
}

// Compute the number of entries in the InnerClasses attribute
u2 JvmtiClassFileReconstituter::inner_classes_attribute_length() {
  typeArrayOop inner_class_list = ikh()->inner_classes();
  return (inner_class_list == NULL) ? 0 : inner_class_list->length();
}

// Write an annotation attribute.  The VM stores them in raw form, so all we need
// to do is add the attrubute name and fill in the length.
// JSR202|   *Annotations_attribute {
// JSR202|     u2 attribute_name_index;
// JSR202|     u4 attribute_length;
// JSR202|     ...
// JSR202|   }
void JvmtiClassFileReconstituter::write_annotations_attribute(const char* attr_name,
                                                              typeArrayHandle annos) {
  u4 length = annos->length();
  write_attribute_name_index(attr_name);
  write_u4(length);
  memcpy(writeable_address(length), annos->byte_at_addr(0), length);
}


// Write InnerClasses attribute
// JVMSpec|   InnerClasses_attribute {
// JVMSpec|     u2 attribute_name_index;
// JVMSpec|     u4 attribute_length;
// JVMSpec|     u2 number_of_classes;
// JVMSpec|     {  u2 inner_class_info_index;
// JVMSpec|        u2 outer_class_info_index;
// JVMSpec|        u2 inner_name_index;
// JVMSpec|        u2 inner_class_access_flags;
// JVMSpec|     } classes[number_of_classes];
// JVMSpec|   }
void JvmtiClassFileReconstituter::write_inner_classes_attribute(int length) {
  typeArrayOop inner_class_list = ikh()->inner_classes();
  guarantee(inner_class_list != NULL && inner_class_list->length() == length,
            "caller must check");
  typeArrayHandle inner_class_list_h(thread(), inner_class_list);
  assert (length % instanceKlass::inner_class_next_offset == 0, "just checking");
  u2 entry_count = length / instanceKlass::inner_class_next_offset;
  u4 size = 2 + entry_count * (2+2+2+2);

  write_attribute_name_index("InnerClasses");
  write_u4(size);
  write_u2(entry_count);
  for (int i = 0; i < length; i += instanceKlass::inner_class_next_offset) {
    write_u2(inner_class_list_h->ushort_at(
                      i + instanceKlass::inner_class_inner_class_info_offset));
    write_u2(inner_class_list_h->ushort_at(
                      i + instanceKlass::inner_class_outer_class_info_offset));
    write_u2(inner_class_list_h->ushort_at(
                      i + instanceKlass::inner_class_inner_name_offset));
    write_u2(inner_class_list_h->ushort_at(
                      i + instanceKlass::inner_class_access_flags_offset));
  }
}

// Write Synthetic attribute
// JVMSpec|   Synthetic_attribute {
// JVMSpec|     u2 attribute_name_index;
// JVMSpec|     u4 attribute_length;
// JVMSpec|   }
void JvmtiClassFileReconstituter::write_synthetic_attribute() {
  write_attribute_name_index("Synthetic");
  write_u4(0); //length always zero
}

// Compute size of LineNumberTable
u2 JvmtiClassFileReconstituter::line_number_table_entries(methodHandle method) {
  // The line number table is compressed so we don't know how big it is until decompressed.
  // Decompression is really fast so we just do it twice.
  u2 num_entries = 0;
  CompressedLineNumberReadStream stream(method->compressed_linenumber_table());
  while (stream.read_pair()) {
    num_entries++;
  }
  return num_entries;
}

// Write LineNumberTable attribute
// JVMSpec|   LineNumberTable_attribute {
// JVMSpec|     u2 attribute_name_index;
// JVMSpec|     u4 attribute_length;
// JVMSpec|     u2 line_number_table_length;
// JVMSpec|     {  u2 start_pc;
// JVMSpec|        u2 line_number;
// JVMSpec|     } line_number_table[line_number_table_length];
// JVMSpec|   }
void JvmtiClassFileReconstituter::write_line_number_table_attribute(methodHandle method,
                                                                    u2 num_entries) {

  write_attribute_name_index("LineNumberTable");
  write_u4(2 + num_entries * (2 + 2));
  write_u2(num_entries);

  CompressedLineNumberReadStream stream(method->compressed_linenumber_table());
  while (stream.read_pair()) {
    write_u2(stream.bci());
    write_u2(stream.line());
  }
}

// Write stack map table attribute
// JSR-202|   StackMapTable_attribute {
// JSR-202|     u2 attribute_name_index;
// JSR-202|     u4 attribute_length;
// JSR-202|     u2 number_of_entries;
// JSR-202|     stack_map_frame_entries[number_of_entries];
// JSR-202|   }
void JvmtiClassFileReconstituter::write_stackmap_table_attribute(methodHandle method,
                                                                 int stackmap_len) {

  write_attribute_name_index("StackMapTable");
  write_u4(stackmap_len);
  memcpy(
    writeable_address(stackmap_len),
    (void*)(method->stackmap_data()->byte_at_addr(0)),
    stackmap_len);
}

// Write one method_info structure
// JVMSpec|   method_info {
// JVMSpec|     u2 access_flags;
// JVMSpec|     u2 name_index;
// JVMSpec|     u2 descriptor_index;
// JVMSpec|     u2 attributes_count;
// JVMSpec|     attribute_info attributes[attributes_count];
// JVMSpec|   }
void JvmtiClassFileReconstituter::write_method_info(methodHandle method) {
  AccessFlags access_flags = method->access_flags();
  constMethodHandle const_method(thread(), method->constMethod());
  u2 generic_signature_index = const_method->generic_signature_index();
  typeArrayHandle anno(thread(), method->annotations());
  typeArrayHandle param_anno(thread(), method->parameter_annotations());
  typeArrayHandle default_anno(thread(), method->annotation_default());

  write_u2(access_flags.get_flags() & JVM_RECOGNIZED_METHOD_MODIFIERS);
  write_u2(const_method->name_index());
  write_u2(const_method->signature_index());

  // write attributes in the same order javac does, so we can test with byte for
  // byte comparison
  int attr_count = 0;
  if (const_method->code_size() != 0) {
    ++attr_count;     // has Code attribute
  }
  if (const_method->has_checked_exceptions()) {
    ++attr_count;     // has Exceptions attribute
  }
  if (default_anno.not_null()) {
    ++attr_count;     // has AnnotationDefault attribute
  }
  // Deprecated attribute would go here
  if (access_flags.is_synthetic()) { // FIXME
    // ++attr_count;
  }
  if (generic_signature_index != 0) {
    ++attr_count;
  }
  if (anno.not_null()) {
    ++attr_count;     // has RuntimeVisibleAnnotations attribute
  }
  if (param_anno.not_null()) {
    ++attr_count;     // has RuntimeVisibleParameterAnnotations attribute
  }

  write_u2(attr_count);
  if (const_method->code_size() > 0) {
    write_code_attribute(method);
  }
  if (const_method->has_checked_exceptions()) {
    write_exceptions_attribute(const_method);
  }
  if (default_anno.not_null()) {
    write_annotations_attribute("AnnotationDefault", default_anno);
  }
  // Deprecated attribute would go here
  if (access_flags.is_synthetic()) {
    // write_synthetic_attribute();
  }
  if (generic_signature_index != 0) {
    write_signature_attribute(generic_signature_index);
  }
  if (anno.not_null()) {
    write_annotations_attribute("RuntimeVisibleAnnotations", anno);
  }
  if (param_anno.not_null()) {
    write_annotations_attribute("RuntimeVisibleParameterAnnotations", param_anno);
  }
}

// Write the class attributes portion of ClassFile structure
// JVMSpec|     u2 attributes_count;
// JVMSpec|     attribute_info attributes[attributes_count];
void JvmtiClassFileReconstituter::write_class_attributes() {
  u2 inner_classes_length = inner_classes_attribute_length();
  symbolHandle generic_signature(thread(), ikh()->generic_signature());
  typeArrayHandle anno(thread(), ikh()->class_annotations());

  int attr_count = 0;
  if (generic_signature() != NULL) {
    ++attr_count;
  }
  if (ikh()->source_file_name() != NULL) {
    ++attr_count;
  }
  if (ikh()->source_debug_extension() != NULL) {
    ++attr_count;
  }
  if (inner_classes_length > 0) {
    ++attr_count;
  }
  if (anno.not_null()) {
    ++attr_count;     // has RuntimeVisibleAnnotations attribute
  }

  write_u2(attr_count);

  if (generic_signature() != NULL) {
    write_signature_attribute(symbol_to_cpool_index(generic_signature()));
  }
  if (ikh()->source_file_name() != NULL) {
    write_source_file_attribute();
  }
  if (ikh()->source_debug_extension() != NULL) {
    write_source_debug_extension_attribute();
  }
  if (inner_classes_length > 0) {
    write_inner_classes_attribute(inner_classes_length);
  }
  if (anno.not_null()) {
    write_annotations_attribute("RuntimeVisibleAnnotations", anno);
  }
}

// Write the method information portion of ClassFile structure
// JVMSpec|     u2 methods_count;
// JVMSpec|     method_info methods[methods_count];
void JvmtiClassFileReconstituter::write_method_infos() {
  HandleMark hm(thread());
  objArrayHandle methods(thread(), ikh()->methods());
  int num_methods = methods->length();

  write_u2(num_methods);
  if (JvmtiExport::can_maintain_original_method_order()) {
    int index;
    int original_index;
    int* method_order = NEW_RESOURCE_ARRAY(int, num_methods);

    // invert the method order mapping
    for (index = 0; index < num_methods; index++) {
      original_index = ikh()->method_ordering()->int_at(index);
      assert(original_index >= 0 && original_index < num_methods,
             "invalid original method index");
      method_order[original_index] = index;
    }

    // write in original order
    for (original_index = 0; original_index < num_methods; original_index++) {
      index = method_order[original_index];
      methodHandle method(thread(), (methodOop)(ikh()->methods()->obj_at(index)));
      write_method_info(method);
    }
  } else {
    // method order not preserved just dump the method infos
    for (int index = 0; index < num_methods; index++) {
      methodHandle method(thread(), (methodOop)(ikh()->methods()->obj_at(index)));
      write_method_info(method);
    }
  }
}

void JvmtiClassFileReconstituter::write_class_file_format() {
  ReallocMark();

  // JVMSpec|   ClassFile {
  // JVMSpec|           u4 magic;
  write_u4(0xCAFEBABE);

  // JVMSpec|           u2 minor_version;
  // JVMSpec|           u2 major_version;
  write_u2(ikh()->minor_version());
  u2 major = ikh()->major_version();
  write_u2(major);

  // JVMSpec|           u2 constant_pool_count;
  // JVMSpec|           cp_info constant_pool[constant_pool_count-1];
  write_u2(cpool()->length());
  copy_cpool_bytes(writeable_address(cpool_size()));

  // JVMSpec|           u2 access_flags;
  write_u2(ikh()->access_flags().get_flags() & JVM_RECOGNIZED_CLASS_MODIFIERS);

  // JVMSpec|           u2 this_class;
  // JVMSpec|           u2 super_class;
  write_u2(class_symbol_to_cpool_index(ikh()->name()));
  klassOop super_class = ikh()->super();
  write_u2(super_class == NULL? 0 :  // zero for java.lang.Object
                class_symbol_to_cpool_index(super_class->klass_part()->name()));

  // JVMSpec|           u2 interfaces_count;
  // JVMSpec|           u2 interfaces[interfaces_count];
  objArrayHandle interfaces(thread(), ikh()->local_interfaces());
  int num_interfaces = interfaces->length();
  write_u2(num_interfaces);
  for (int index = 0; index < num_interfaces; index++) {
    HandleMark hm(thread());
    instanceKlassHandle iikh(thread(), klassOop(interfaces->obj_at(index)));
    write_u2(class_symbol_to_cpool_index(iikh->name()));
  }

  // JVMSpec|           u2 fields_count;
  // JVMSpec|           field_info fields[fields_count];
  write_field_infos();

  // JVMSpec|           u2 methods_count;
  // JVMSpec|           method_info methods[methods_count];
  write_method_infos();

  // JVMSpec|           u2 attributes_count;
  // JVMSpec|           attribute_info attributes[attributes_count];
  // JVMSpec|   } /* end ClassFile 8?
  write_class_attributes();
}

address JvmtiClassFileReconstituter::writeable_address(size_t size) {
  size_t used_size = _buffer_ptr - _buffer;
  if (size + used_size >= _buffer_size) {
    // compute the new buffer size: must be at least twice as big as before
    // plus whatever new is being used; then convert to nice clean block boundary
    size_t new_buffer_size = (size + _buffer_size*2 + 1) / initial_buffer_size
                                                         * initial_buffer_size;

    // VM goes belly-up if the memory isn't available, so cannot do OOM processing
    _buffer = REALLOC_RESOURCE_ARRAY(u1, _buffer, _buffer_size, new_buffer_size);
    _buffer_size = new_buffer_size;
    _buffer_ptr = _buffer + used_size;
  }
  u1* ret_ptr = _buffer_ptr;
  _buffer_ptr += size;
  return ret_ptr;
}

void JvmtiClassFileReconstituter::write_attribute_name_index(const char* name) {
  unsigned int hash_ignored;
  symbolOop sym = SymbolTable::lookup_only(name, (int)strlen(name), hash_ignored);
  assert(sym != NULL, "attribute name symbol not found");
  u2 attr_name_index = symbol_to_cpool_index(sym);
  assert(attr_name_index != 0, "attribute name symbol not in constant pool");
  write_u2(attr_name_index);
}

void JvmtiClassFileReconstituter::write_u1(u1 x) {
  *writeable_address(1) = x;
}

void JvmtiClassFileReconstituter::write_u2(u2 x) {
  Bytes::put_Java_u2(writeable_address(2), x);
}

void JvmtiClassFileReconstituter::write_u4(u4 x) {
  Bytes::put_Java_u4(writeable_address(4), x);
}

void JvmtiClassFileReconstituter::write_u8(u8 x) {
  Bytes::put_Java_u8(writeable_address(8), x);
}

void JvmtiClassFileReconstituter::copy_bytecodes(methodHandle mh,
                                                 unsigned char* bytecodes) {
  // use a BytecodeStream to iterate over the bytecodes. JVM/fast bytecodes
  // and the breakpoint bytecode are converted to their original bytecodes.

  BytecodeStream bs(mh);

  unsigned char* p = bytecodes;
  Bytecodes::Code code;
  bool is_rewritten = instanceKlass::cast(mh->method_holder())->is_rewritten();

  while ((code = bs.next()) >= 0) {
    assert(Bytecodes::is_java_code(code), "sanity check");
    assert(code != Bytecodes::_breakpoint, "sanity check");

    // length of bytecode (mnemonic + operands)
    address bcp = bs.bcp();
    int     len = bs.instruction_size();
    assert(len > 0, "length must be > 0");

    // copy the bytecodes
    *p = (unsigned char) (bs.is_wide()? Bytecodes::_wide : code);
    if (len > 1) {
      memcpy(p+1, bcp+1, len-1);
    }

    // During linking the get/put and invoke instructions are rewritten
    // with an index into the constant pool cache. The original constant
    // pool index must be returned to caller.  Rewrite the index.
    if (is_rewritten && len >= 3) {
      switch (code) {
      case Bytecodes::_getstatic       :  // fall through
      case Bytecodes::_putstatic       :  // fall through
      case Bytecodes::_getfield        :  // fall through
      case Bytecodes::_putfield        :  // fall through
      case Bytecodes::_invokevirtual   :  // fall through
      case Bytecodes::_invokespecial   :  // fall through
      case Bytecodes::_invokestatic    :  // fall through
      case Bytecodes::_invokedynamic   :  // fall through
      case Bytecodes::_invokeinterface :
        assert(len == 3 || (code == Bytecodes::_invokeinterface && len ==5),
               "sanity check");
        int cpci = Bytes::get_native_u2(bcp+1);
        bool is_invokedynamic = (EnableInvokeDynamic && code == Bytecodes::_invokedynamic);
        if (is_invokedynamic)
          cpci = Bytes::get_native_u4(bcp+1);
        // cache cannot be pre-fetched since some classes won't have it yet
        ConstantPoolCacheEntry* entry =
          mh->constants()->cache()->main_entry_at(cpci);
        int i = entry->constant_pool_index();
        assert(i < mh->constants()->length(), "sanity check");
        Bytes::put_Java_u2((address)(p+1), (u2)i);     // java byte ordering
        if (is_invokedynamic)  *(p+3) = *(p+4) = 0;
        break;
      }
    }

    p += len;
  }
}

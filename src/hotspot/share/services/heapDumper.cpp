/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "classfile/classLoaderData.inline.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "classfile/javaClasses.inline.hpp"
#include "classfile/symbolTable.hpp"
#include "classfile/vmClasses.hpp"
#include "classfile/vmSymbols.hpp"
#include "gc/shared/gcLocker.hpp"
#include "gc/shared/gcVMOperations.hpp"
#include "gc/shared/workerThread.hpp"
#include "jfr/jfrEvents.hpp"
#include "jvm.h"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "memory/universe.hpp"
#include "oops/klass.inline.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/objArrayOop.inline.hpp"
#include "oops/oop.inline.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaCalls.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/os.hpp"
#include "runtime/reflectionUtils.hpp"
#include "runtime/threads.hpp"
#include "runtime/threadSMR.hpp"
#include "runtime/vframe.hpp"
#include "runtime/vmOperations.hpp"
#include "runtime/vmThread.hpp"
#include "runtime/timerTrace.hpp"
#include "services/heapDumper.hpp"
#include "services/heapDumperWriter.hpp"
#include "services/threadService.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"

// Support class with a collection of functions used when dumping the heap

class DumperSupport : AllStatic {
 public:

  // write a header of the given type
  static void write_header(AbstractDumpWriter* writer, hprofTag tag, u4 len);

  // returns hprof tag for the given type signature
  static hprofTag sig2tag(Symbol* sig);
  // returns hprof tag for the given basic type
  static hprofTag type2tag(BasicType type);
  // Returns the size of the data to write.
  static u4 sig2size(Symbol* sig);

  // returns the size of the instance of the given class
  static u4 instance_size(Klass* k);

  // dump a jfloat
  static void dump_float(AbstractDumpWriter* writer, jfloat f);
  // dump a jdouble
  static void dump_double(AbstractDumpWriter* writer, jdouble d);
  // dumps the raw value of the given field
  static void dump_field_value(AbstractDumpWriter* writer, char type, oop obj, int offset);
  // returns the size of the static fields; also counts the static fields
  static u4 get_static_fields_size(InstanceKlass* ik, u2& field_count);
  // dumps static fields of the given class
  static void dump_static_fields(AbstractDumpWriter* writer, Klass* k);
  // dump the raw values of the instance fields of the given object
  static void dump_instance_fields(AbstractDumpWriter* writer, oop o);
  // get the count of the instance fields for a given class
  static u2 get_instance_fields_count(InstanceKlass* ik);
  // dumps the definition of the instance fields for a given class
  static void dump_instance_field_descriptors(AbstractDumpWriter* writer, Klass* k);
  // creates HPROF_GC_INSTANCE_DUMP record for the given object
  static void dump_instance(AbstractDumpWriter* writer, oop o);
  // creates HPROF_GC_CLASS_DUMP record for the given instance class
  static void dump_instance_class(AbstractDumpWriter* writer, Klass* k);
  // creates HPROF_GC_CLASS_DUMP record for a given array class
  static void dump_array_class(AbstractDumpWriter* writer, Klass* k);

  // creates HPROF_GC_OBJ_ARRAY_DUMP record for the given object array
  static void dump_object_array(AbstractDumpWriter* writer, objArrayOop array);
  // creates HPROF_GC_PRIM_ARRAY_DUMP record for the given type array
  static void dump_prim_array(AbstractDumpWriter* writer, typeArrayOop array);
  // create HPROF_FRAME record for the given method and bci
  static void dump_stack_frame(AbstractDumpWriter* writer, int frame_serial_num, int class_serial_num, Method* m, int bci);

  // check if we need to truncate an array
  static int calculate_array_max_length(AbstractDumpWriter* writer, arrayOop array, short header_size);

  // fixes up the current dump record and writes HPROF_HEAP_DUMP_END record
  static void end_of_dump(AbstractDumpWriter* writer);

  static oop mask_dormant_archived_object(oop o) {
    if (o != nullptr && o->klass()->java_mirror() == nullptr) {
      // Ignore this object since the corresponding java mirror is not loaded.
      // Might be a dormant archive object.
      return nullptr;
    } else {
      return o;
    }
  }
};

// write a header of the given type
void DumperSupport:: write_header(AbstractDumpWriter* writer, hprofTag tag, u4 len) {
  writer->write_u1(tag);
  writer->write_u4(0);                  // current ticks
  writer->write_u4(len);
}

// returns hprof tag for the given type signature
hprofTag DumperSupport::sig2tag(Symbol* sig) {
  switch (sig->char_at(0)) {
    case JVM_SIGNATURE_CLASS    : return HPROF_NORMAL_OBJECT;
    case JVM_SIGNATURE_ARRAY    : return HPROF_NORMAL_OBJECT;
    case JVM_SIGNATURE_BYTE     : return HPROF_BYTE;
    case JVM_SIGNATURE_CHAR     : return HPROF_CHAR;
    case JVM_SIGNATURE_FLOAT    : return HPROF_FLOAT;
    case JVM_SIGNATURE_DOUBLE   : return HPROF_DOUBLE;
    case JVM_SIGNATURE_INT      : return HPROF_INT;
    case JVM_SIGNATURE_LONG     : return HPROF_LONG;
    case JVM_SIGNATURE_SHORT    : return HPROF_SHORT;
    case JVM_SIGNATURE_BOOLEAN  : return HPROF_BOOLEAN;
    default : ShouldNotReachHere(); /* to shut up compiler */ return HPROF_BYTE;
  }
}

hprofTag DumperSupport::type2tag(BasicType type) {
  switch (type) {
    case T_BYTE     : return HPROF_BYTE;
    case T_CHAR     : return HPROF_CHAR;
    case T_FLOAT    : return HPROF_FLOAT;
    case T_DOUBLE   : return HPROF_DOUBLE;
    case T_INT      : return HPROF_INT;
    case T_LONG     : return HPROF_LONG;
    case T_SHORT    : return HPROF_SHORT;
    case T_BOOLEAN  : return HPROF_BOOLEAN;
    default : ShouldNotReachHere(); /* to shut up compiler */ return HPROF_BYTE;
  }
}

u4 DumperSupport::sig2size(Symbol* sig) {
  switch (sig->char_at(0)) {
    case JVM_SIGNATURE_CLASS:
    case JVM_SIGNATURE_ARRAY: return sizeof(address);
    case JVM_SIGNATURE_BOOLEAN:
    case JVM_SIGNATURE_BYTE: return 1;
    case JVM_SIGNATURE_SHORT:
    case JVM_SIGNATURE_CHAR: return 2;
    case JVM_SIGNATURE_INT:
    case JVM_SIGNATURE_FLOAT: return 4;
    case JVM_SIGNATURE_LONG:
    case JVM_SIGNATURE_DOUBLE: return 8;
    default: ShouldNotReachHere(); /* to shut up compiler */ return 0;
  }
}

template<typename T, typename F> T bit_cast(F from) { // replace with the real thing when we can use c++20
  T to;
  static_assert(sizeof(to) == sizeof(from), "must be of the same size");
  memcpy(&to, &from, sizeof(to));
  return to;
}

// dump a jfloat
void DumperSupport::dump_float(AbstractDumpWriter* writer, jfloat f) {
  if (g_isnan(f)) {
    writer->write_u4(0x7fc00000); // collapsing NaNs
  } else {
    writer->write_u4(bit_cast<u4>(f));
  }
}

// dump a jdouble
void DumperSupport::dump_double(AbstractDumpWriter* writer, jdouble d) {
  if (g_isnan(d)) {
    writer->write_u8(0x7ff80000ull << 32); // collapsing NaNs
  } else {
    writer->write_u8(bit_cast<u8>(d));
  }
}

// dumps the raw value of the given field
void DumperSupport::dump_field_value(AbstractDumpWriter* writer, char type, oop obj, int offset) {
  switch (type) {
    case JVM_SIGNATURE_CLASS :
    case JVM_SIGNATURE_ARRAY : {
      oop o = obj->obj_field_access<ON_UNKNOWN_OOP_REF | AS_NO_KEEPALIVE>(offset);
      if (o != nullptr && log_is_enabled(Debug, cds, heap) && mask_dormant_archived_object(o) == nullptr) {
        ResourceMark rm;
        log_debug(cds, heap)("skipped dormant archived object " INTPTR_FORMAT " (%s) referenced by " INTPTR_FORMAT " (%s)",
                             p2i(o), o->klass()->external_name(),
                             p2i(obj), obj->klass()->external_name());
      }
      o = mask_dormant_archived_object(o);
      assert(oopDesc::is_oop_or_null(o), "Expected an oop or nullptr at " PTR_FORMAT, p2i(o));
      writer->write_objectID(o);
      break;
    }
    case JVM_SIGNATURE_BYTE : {
      jbyte b = obj->byte_field(offset);
      writer->write_u1(b);
      break;
    }
    case JVM_SIGNATURE_CHAR : {
      jchar c = obj->char_field(offset);
      writer->write_u2(c);
      break;
    }
    case JVM_SIGNATURE_SHORT : {
      jshort s = obj->short_field(offset);
      writer->write_u2(s);
      break;
    }
    case JVM_SIGNATURE_FLOAT : {
      jfloat f = obj->float_field(offset);
      dump_float(writer, f);
      break;
    }
    case JVM_SIGNATURE_DOUBLE : {
      jdouble d = obj->double_field(offset);
      dump_double(writer, d);
      break;
    }
    case JVM_SIGNATURE_INT : {
      jint i = obj->int_field(offset);
      writer->write_u4(i);
      break;
    }
    case JVM_SIGNATURE_LONG : {
      jlong l = obj->long_field(offset);
      writer->write_u8(l);
      break;
    }
    case JVM_SIGNATURE_BOOLEAN : {
      jboolean b = obj->bool_field(offset);
      writer->write_u1(b);
      break;
    }
    default : {
      ShouldNotReachHere();
      break;
    }
  }
}

// returns the size of the instance of the given class
u4 DumperSupport::instance_size(Klass* k) {
  InstanceKlass* ik = InstanceKlass::cast(k);
  u4 size = 0;

  for (FieldStream fld(ik, false, false); !fld.eos(); fld.next()) {
    if (!fld.access_flags().is_static()) {
      size += sig2size(fld.signature());
    }
  }
  return size;
}

u4 DumperSupport::get_static_fields_size(InstanceKlass* ik, u2& field_count) {
  field_count = 0;
  u4 size = 0;

  for (FieldStream fldc(ik, true, true); !fldc.eos(); fldc.next()) {
    if (fldc.access_flags().is_static()) {
      field_count++;
      size += sig2size(fldc.signature());
    }
  }

  // Add in resolved_references which is referenced by the cpCache
  // The resolved_references is an array per InstanceKlass holding the
  // strings and other oops resolved from the constant pool.
  oop resolved_references = ik->constants()->resolved_references_or_null();
  if (resolved_references != nullptr) {
    field_count++;
    size += sizeof(address);

    // Add in the resolved_references of the used previous versions of the class
    // in the case of RedefineClasses
    InstanceKlass* prev = ik->previous_versions();
    while (prev != nullptr && prev->constants()->resolved_references_or_null() != nullptr) {
      field_count++;
      size += sizeof(address);
      prev = prev->previous_versions();
    }
  }

  // We write the value itself plus a name and a one byte type tag per field.
  return size + field_count * (sizeof(address) + 1);
}

// dumps static fields of the given class
void DumperSupport::dump_static_fields(AbstractDumpWriter* writer, Klass* k) {
  InstanceKlass* ik = InstanceKlass::cast(k);

  // dump the field descriptors and raw values
  for (FieldStream fld(ik, true, true); !fld.eos(); fld.next()) {
    if (fld.access_flags().is_static()) {
      Symbol* sig = fld.signature();

      writer->write_symbolID(fld.name());   // name
      writer->write_u1(sig2tag(sig));       // type

      // value
      dump_field_value(writer, sig->char_at(0), ik->java_mirror(), fld.offset());
    }
  }

  // Add resolved_references for each class that has them
  oop resolved_references = ik->constants()->resolved_references_or_null();
  if (resolved_references != nullptr) {
    writer->write_symbolID(vmSymbols::resolved_references_name());  // name
    writer->write_u1(sig2tag(vmSymbols::object_array_signature())); // type
    writer->write_objectID(resolved_references);

    // Also write any previous versions
    InstanceKlass* prev = ik->previous_versions();
    while (prev != nullptr && prev->constants()->resolved_references_or_null() != nullptr) {
      writer->write_symbolID(vmSymbols::resolved_references_name());  // name
      writer->write_u1(sig2tag(vmSymbols::object_array_signature())); // type
      writer->write_objectID(prev->constants()->resolved_references());
      prev = prev->previous_versions();
    }
  }
}

// dump the raw values of the instance fields of the given object
void DumperSupport::dump_instance_fields(AbstractDumpWriter* writer, oop o) {
  InstanceKlass* ik = InstanceKlass::cast(o->klass());

  for (FieldStream fld(ik, false, false); !fld.eos(); fld.next()) {
    if (!fld.access_flags().is_static()) {
      Symbol* sig = fld.signature();
      dump_field_value(writer, sig->char_at(0), o, fld.offset());
    }
  }
}

// dumps the definition of the instance fields for a given class
u2 DumperSupport::get_instance_fields_count(InstanceKlass* ik) {
  u2 field_count = 0;

  for (FieldStream fldc(ik, true, true); !fldc.eos(); fldc.next()) {
    if (!fldc.access_flags().is_static()) field_count++;
  }

  return field_count;
}

// dumps the definition of the instance fields for a given class
void DumperSupport::dump_instance_field_descriptors(AbstractDumpWriter* writer, Klass* k) {
  InstanceKlass* ik = InstanceKlass::cast(k);

  // dump the field descriptors
  for (FieldStream fld(ik, true, true); !fld.eos(); fld.next()) {
    if (!fld.access_flags().is_static()) {
      Symbol* sig = fld.signature();

      writer->write_symbolID(fld.name());   // name
      writer->write_u1(sig2tag(sig));       // type
    }
  }
}

// creates HPROF_GC_INSTANCE_DUMP record for the given object
void DumperSupport::dump_instance(AbstractDumpWriter* writer, oop o) {
  InstanceKlass* ik = InstanceKlass::cast(o->klass());
  u4 is = instance_size(ik);
  u4 size = 1 + sizeof(address) + 4 + sizeof(address) + 4 + is;

  writer->start_sub_record(HPROF_GC_INSTANCE_DUMP, size);
  writer->write_objectID(o);
  writer->write_u4(STACK_TRACE_ID);

  // class ID
  writer->write_classID(ik);

  // number of bytes that follow
  writer->write_u4(is);

  // field values
  dump_instance_fields(writer, o);

  writer->end_sub_record();
}

// creates HPROF_GC_CLASS_DUMP record for the given instance class
void DumperSupport::dump_instance_class(AbstractDumpWriter* writer, Klass* k) {
  InstanceKlass* ik = InstanceKlass::cast(k);

  // We can safepoint and do a heap dump at a point where we have a Klass,
  // but no java mirror class has been setup for it. So we need to check
  // that the class is at least loaded, to avoid crash from a null mirror.
  if (!ik->is_loaded()) {
    return;
  }

  u2 static_fields_count = 0;
  u4 static_size = get_static_fields_size(ik, static_fields_count);
  u2 instance_fields_count = get_instance_fields_count(ik);
  u4 instance_fields_size = instance_fields_count * (sizeof(address) + 1);
  u4 size = 1 + sizeof(address) + 4 + 6 * sizeof(address) + 4 + 2 + 2 + static_size + 2 + instance_fields_size;

  writer->start_sub_record(HPROF_GC_CLASS_DUMP, size);

  // class ID
  writer->write_classID(ik);
  writer->write_u4(STACK_TRACE_ID);

  // super class ID
  InstanceKlass* java_super = ik->java_super();
  if (java_super == nullptr) {
    writer->write_objectID(oop(nullptr));
  } else {
    writer->write_classID(java_super);
  }

  writer->write_objectID(ik->class_loader());
  writer->write_objectID(ik->signers());
  writer->write_objectID(ik->protection_domain());

  // reserved
  writer->write_objectID(oop(nullptr));
  writer->write_objectID(oop(nullptr));

  // instance size
  writer->write_u4(DumperSupport::instance_size(ik));

  // size of constant pool - ignored by HAT 1.1
  writer->write_u2(0);

  // static fields
  writer->write_u2(static_fields_count);
  dump_static_fields(writer, ik);

  // description of instance fields
  writer->write_u2(instance_fields_count);
  dump_instance_field_descriptors(writer, ik);

  writer->end_sub_record();
}

// creates HPROF_GC_CLASS_DUMP record for the given array class
void DumperSupport::dump_array_class(AbstractDumpWriter* writer, Klass* k) {
  InstanceKlass* ik = nullptr; // bottom class for object arrays, null for primitive type arrays
  if (k->is_objArray_klass()) {
    Klass *bk = ObjArrayKlass::cast(k)->bottom_klass();
    assert(bk != nullptr, "checking");
    if (bk->is_instance_klass()) {
      ik = InstanceKlass::cast(bk);
    }
  }

  u4 size = 1 + sizeof(address) + 4 + 6 * sizeof(address) + 4 + 2 + 2 + 2;
  writer->start_sub_record(HPROF_GC_CLASS_DUMP, size);
  writer->write_classID(k);
  writer->write_u4(STACK_TRACE_ID);

  // super class of array classes is java.lang.Object
  InstanceKlass* java_super = k->java_super();
  assert(java_super != nullptr, "checking");
  writer->write_classID(java_super);

  writer->write_objectID(ik == nullptr ? oop(nullptr) : ik->class_loader());
  writer->write_objectID(ik == nullptr ? oop(nullptr) : ik->signers());
  writer->write_objectID(ik == nullptr ? oop(nullptr) : ik->protection_domain());

  writer->write_objectID(oop(nullptr));    // reserved
  writer->write_objectID(oop(nullptr));
  writer->write_u4(0);             // instance size
  writer->write_u2(0);             // constant pool
  writer->write_u2(0);             // static fields
  writer->write_u2(0);             // instance fields

  writer->end_sub_record();

}

// Hprof uses an u4 as record length field,
// which means we need to truncate arrays that are too long.
int DumperSupport::calculate_array_max_length(AbstractDumpWriter* writer, arrayOop array, short header_size) {
  BasicType type = ArrayKlass::cast(array->klass())->element_type();
  assert(type >= T_BOOLEAN && type <= T_OBJECT, "invalid array element type");

  int length = array->length();

  int type_size;
  if (type == T_OBJECT) {
    type_size = sizeof(address);
  } else {
    type_size = type2aelembytes(type);
  }

  size_t length_in_bytes = (size_t)length * type_size;
  uint max_bytes = max_juint - header_size;

  if (length_in_bytes > max_bytes) {
    length = max_bytes / type_size;
    length_in_bytes = (size_t)length * type_size;

    warning("cannot dump array of type %s[] with length %d; truncating to length %d",
            type2name_tab[type], array->length(), length);
  }
  return length;
}

// creates HPROF_GC_OBJ_ARRAY_DUMP record for the given object array
void DumperSupport::dump_object_array(AbstractDumpWriter* writer, objArrayOop array) {
  // sizeof(u1) + 2 * sizeof(u4) + sizeof(objectID) + sizeof(classID)
  short header_size = 1 + 2 * 4 + 2 * sizeof(address);
  int length = calculate_array_max_length(writer, array, header_size);
  u4 size = header_size + length * sizeof(address);

  writer->start_sub_record(HPROF_GC_OBJ_ARRAY_DUMP, size);
  writer->write_objectID(array);
  writer->write_u4(STACK_TRACE_ID);
  writer->write_u4(length);

  // array class ID
  writer->write_classID(array->klass());

  // [id]* elements
  for (int index = 0; index < length; index++) {
    oop o = array->obj_at(index);
    if (o != nullptr && log_is_enabled(Debug, cds, heap) && mask_dormant_archived_object(o) == nullptr) {
      ResourceMark rm;
      log_debug(cds, heap)("skipped dormant archived object " INTPTR_FORMAT " (%s) referenced by " INTPTR_FORMAT " (%s)",
                           p2i(o), o->klass()->external_name(),
                           p2i(array), array->klass()->external_name());
    }
    o = mask_dormant_archived_object(o);
    writer->write_objectID(o);
  }

  writer->end_sub_record();
}

#define WRITE_ARRAY(Array, Type, Size, Length) \
  for (int i = 0; i < Length; i++) { writer->write_##Size((Size)Array->Type##_at(i)); }

// creates HPROF_GC_PRIM_ARRAY_DUMP record for the given type array
void DumperSupport::dump_prim_array(AbstractDumpWriter* writer, typeArrayOop array) {
  BasicType type = TypeArrayKlass::cast(array->klass())->element_type();
  // 2 * sizeof(u1) + 2 * sizeof(u4) + sizeof(objectID)
  short header_size = 2 * 1 + 2 * 4 + sizeof(address);

  int length = calculate_array_max_length(writer, array, header_size);
  int type_size = type2aelembytes(type);
  u4 length_in_bytes = (u4)length * type_size;
  u4 size = header_size + length_in_bytes;

  writer->start_sub_record(HPROF_GC_PRIM_ARRAY_DUMP, size);
  writer->write_objectID(array);
  writer->write_u4(STACK_TRACE_ID);
  writer->write_u4(length);
  writer->write_u1(type2tag(type));

  // nothing to copy
  if (length == 0) {
    writer->end_sub_record();
    return;
  }

  // If the byte ordering is big endian then we can copy most types directly

  switch (type) {
    case T_INT : {
      if (Endian::is_Java_byte_ordering_different()) {
        WRITE_ARRAY(array, int, u4, length);
      } else {
        writer->write_raw(array->int_at_addr(0), length_in_bytes);
      }
      break;
    }
    case T_BYTE : {
      writer->write_raw(array->byte_at_addr(0), length_in_bytes);
      break;
    }
    case T_CHAR : {
      if (Endian::is_Java_byte_ordering_different()) {
        WRITE_ARRAY(array, char, u2, length);
      } else {
        writer->write_raw(array->char_at_addr(0), length_in_bytes);
      }
      break;
    }
    case T_SHORT : {
      if (Endian::is_Java_byte_ordering_different()) {
        WRITE_ARRAY(array, short, u2, length);
      } else {
        writer->write_raw(array->short_at_addr(0), length_in_bytes);
      }
      break;
    }
    case T_BOOLEAN : {
      if (Endian::is_Java_byte_ordering_different()) {
        WRITE_ARRAY(array, bool, u1, length);
      } else {
        writer->write_raw(array->bool_at_addr(0), length_in_bytes);
      }
      break;
    }
    case T_LONG : {
      if (Endian::is_Java_byte_ordering_different()) {
        WRITE_ARRAY(array, long, u8, length);
      } else {
        writer->write_raw(array->long_at_addr(0), length_in_bytes);
      }
      break;
    }

    // handle float/doubles in a special value to ensure than NaNs are
    // written correctly. TO DO: Check if we can avoid this on processors that
    // use IEEE 754.

    case T_FLOAT : {
      for (int i = 0; i < length; i++) {
        dump_float(writer, array->float_at(i));
      }
      break;
    }
    case T_DOUBLE : {
      for (int i = 0; i < length; i++) {
        dump_double(writer, array->double_at(i));
      }
      break;
    }
    default : ShouldNotReachHere();
  }

  writer->end_sub_record();
}

// create a HPROF_FRAME record of the given Method* and bci
void DumperSupport::dump_stack_frame(AbstractDumpWriter* writer,
                                     int frame_serial_num,
                                     int class_serial_num,
                                     Method* m,
                                     int bci) {
  int line_number;
  if (m->is_native()) {
    line_number = -3;  // native frame
  } else {
    line_number = m->line_number_from_bci(bci);
  }

  write_header(writer, HPROF_FRAME, 4*oopSize + 2*sizeof(u4));
  writer->write_id(frame_serial_num);               // frame serial number
  writer->write_symbolID(m->name());                // method's name
  writer->write_symbolID(m->signature());           // method's signature

  assert(m->method_holder()->is_instance_klass(), "not InstanceKlass");
  writer->write_symbolID(m->method_holder()->source_file_name());  // source file name
  writer->write_u4(class_serial_num);               // class serial number
  writer->write_u4((u4) line_number);               // line number
}


// Support class used to generate HPROF_UTF8 records from the entries in the
// SymbolTable.

class SymbolTableDumper : public SymbolClosure {
 private:
  AbstractDumpWriter* _writer;
  AbstractDumpWriter* writer() const                { return _writer; }
 public:
  SymbolTableDumper(AbstractDumpWriter* writer)     { _writer = writer; }
  void do_symbol(Symbol** p);
};

void SymbolTableDumper::do_symbol(Symbol** p) {
  ResourceMark rm;
  Symbol* sym = *p;
  int len = sym->utf8_length();
  if (len > 0) {
    char* s = sym->as_utf8();
    DumperSupport::write_header(writer(), HPROF_UTF8, oopSize + len);
    writer()->write_symbolID(sym);
    writer()->write_raw(s, len);
  }
}

// Support class used to generate HPROF_GC_ROOT_JNI_LOCAL records

class JNILocalsDumper : public OopClosure {
 private:
  AbstractDumpWriter* _writer;
  u4 _thread_serial_num;
  int _frame_num;
  AbstractDumpWriter* writer() const                { return _writer; }
 public:
  JNILocalsDumper(AbstractDumpWriter* writer, u4 thread_serial_num) {
    _writer = writer;
    _thread_serial_num = thread_serial_num;
    _frame_num = -1;  // default - empty stack
  }
  void set_frame_number(int n) { _frame_num = n; }
  void do_oop(oop* obj_p);
  void do_oop(narrowOop* obj_p) { ShouldNotReachHere(); }
};


void JNILocalsDumper::do_oop(oop* obj_p) {
  // ignore null handles
  oop o = *obj_p;
  if (o != nullptr) {
    u4 size = 1 + sizeof(address) + 4 + 4;
    writer()->start_sub_record(HPROF_GC_ROOT_JNI_LOCAL, size);
    writer()->write_objectID(o);
    writer()->write_u4(_thread_serial_num);
    writer()->write_u4((u4)_frame_num);
    writer()->end_sub_record();
  }
}


// Support class used to generate HPROF_GC_ROOT_JNI_GLOBAL records

class JNIGlobalsDumper : public OopClosure {
 private:
  AbstractDumpWriter* _writer;
  AbstractDumpWriter* writer() const                { return _writer; }

 public:
  JNIGlobalsDumper(AbstractDumpWriter* writer) {
    _writer = writer;
  }
  void do_oop(oop* obj_p);
  void do_oop(narrowOop* obj_p) { ShouldNotReachHere(); }
};

void JNIGlobalsDumper::do_oop(oop* obj_p) {
  oop o = NativeAccess<AS_NO_KEEPALIVE>::oop_load(obj_p);

  // ignore these
  if (o == nullptr) return;
  // we ignore global ref to symbols and other internal objects
  if (o->is_instance() || o->is_objArray() || o->is_typeArray()) {
    u4 size = 1 + 2 * sizeof(address);
    writer()->start_sub_record(HPROF_GC_ROOT_JNI_GLOBAL, size);
    writer()->write_objectID(o);
    writer()->write_rootID(obj_p);      // global ref ID
    writer()->end_sub_record();
  }
};

// Support class used to generate HPROF_GC_ROOT_STICKY_CLASS records

class StickyClassDumper : public KlassClosure {
 private:
  AbstractDumpWriter* _writer;
  AbstractDumpWriter* writer() const                { return _writer; }
 public:
  StickyClassDumper(AbstractDumpWriter* writer) {
    _writer = writer;
  }
  void do_klass(Klass* k) {
    if (k->is_instance_klass()) {
      InstanceKlass* ik = InstanceKlass::cast(k);
      u4 size = 1 + sizeof(address);
      writer()->start_sub_record(HPROF_GC_ROOT_STICKY_CLASS, size);
      writer()->write_classID(ik);
      writer()->end_sub_record();
    }
  }
};

class VM_HeapDumper;

// Support class using when iterating over the heap.
class HeapObjectDumper : public ObjectClosure {
 private:
  AbstractDumpWriter* _writer;
  AbstractDumpWriter* writer()                  { return _writer; }

 public:
  HeapObjectDumper(AbstractDumpWriter* writer) {
    _writer = writer;
  }

  // called for each object in the heap
  void do_object(oop o);
};

void HeapObjectDumper::do_object(oop o) {
  // skip classes as these emitted as HPROF_GC_CLASS_DUMP records
  if (o->klass() == vmClasses::Class_klass()) {
    if (!java_lang_Class::is_primitive(o)) {
      return;
    }
  }

  if (DumperSupport::mask_dormant_archived_object(o) == nullptr) {
    log_debug(cds, heap)("skipped dormant archived object " INTPTR_FORMAT " (%s)", p2i(o), o->klass()->external_name());
    return;
  }

  if (o->is_instance()) {
    // create a HPROF_GC_INSTANCE record for each object
    DumperSupport::dump_instance(writer(), o);
  } else if (o->is_objArray()) {
    // create a HPROF_GC_OBJ_ARRAY_DUMP record for each object array
    DumperSupport::dump_object_array(writer(), objArrayOop(o));
  } else if (o->is_typeArray()) {
    // create a HPROF_GC_PRIM_ARRAY_DUMP record for each type array
    DumperSupport::dump_prim_array(writer(), typeArrayOop(o));
  }
}

// The dumper controller for parallel heap dump
class DumperController : public CHeapObj<mtInternal> {
 private:
   Monitor* _lock;
   uint   _dumper_number;
   uint   _complete_number;

 public:
   DumperController(uint number) :
     _lock(new (std::nothrow) PaddedMonitor(Mutex::safepoint, "DumperController_lock")),
     _dumper_number(number),
     _complete_number(0) { }

   ~DumperController() { delete _lock; }

   void dumper_complete(DumpWriter* local_writer, DumpWriter* global_writer) {
     MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
     _complete_number++;
     // propagate local error to global if any
     if (local_writer->has_error()) {
      global_writer->set_error(local_writer->error());
     }
     ml.notify();
   }

   void wait_all_dumpers_complete() {
     MonitorLocker ml(_lock, Mutex::_no_safepoint_check_flag);
     while (_complete_number != _dumper_number) {
        ml.wait();
     }
   }
};

class VM_HeapDumpMerge : public VM_Operation {
private:
  DumpWriter* _writer;
  const char* _path;
  bool _has_error;

  void merge_file(char* path);
  void merge_done();
public:
  VM_HeapDumpMerge(const char* path, DumpWriter* writer)
    : _writer(writer), _path(path), _has_error(_writer->has_error()) {}
  VMOp_Type type() const { return VMOp_HeapDumpMerge; }
  // heap dump merge could happen outside safepoint
  virtual bool evaluate_at_safepoint() const { return false; }
  void doit();
};

// The VM operation that performs the heap dump
class VM_HeapDumper : public VM_GC_Operation, public WorkerTask {
 private:
  static VM_HeapDumper*   _global_dumper;
  static DumpWriter*      _global_writer;
  DumpWriter*             _local_writer;
  JavaThread*             _oome_thread;
  Method*                 _oome_constructor;
  bool                    _gc_before_heap_dump;
  GrowableArray<Klass*>*  _klass_map;
  ThreadStackTrace**      _stack_traces;
  int                     _num_threads;
  // parallel heap dump support
  uint                    _num_dumper_threads;
  DumperController*       _dumper_controller;
  ParallelObjectIterator* _poi;
  // worker id of VMDumper thread.
  static const size_t VMDumperWorkerId = 0;
  // VM dumper dumps both heap and non-heap data, other dumpers dump heap-only data.
  static bool is_vm_dumper(uint worker_id) { return worker_id == VMDumperWorkerId; }
  static DumpWriter* create_dump_writer();

  // accessors and setters
  static VM_HeapDumper* dumper()         {  assert(_global_dumper != nullptr, "Error"); return _global_dumper; }
  static DumpWriter* writer()            {  assert(_global_writer != nullptr, "Error"); return _global_writer; }
  void set_global_dumper() {
    assert(_global_dumper == nullptr, "Error");
    _global_dumper = this;
  }
  void set_global_writer() {
    assert(_global_writer == nullptr, "Error");
    _global_writer = _local_writer;
  }
  void clear_global_dumper() { _global_dumper = nullptr; }
  void clear_global_writer() { _global_writer = nullptr; }

  bool skip_operation() const;

  // writes a HPROF_LOAD_CLASS record
  static void do_load_class(Klass* k);

  // writes a HPROF_GC_CLASS_DUMP record for the given class
  static void do_class_dump(Klass* k);

  // HPROF_GC_ROOT_THREAD_OBJ records
  int do_thread(JavaThread* thread, u4 thread_serial_num);
  void do_threads();

  void add_class_serial_number(Klass* k, int serial_num) {
    _klass_map->at_put_grow(serial_num, k);
  }

  // HPROF_TRACE and HPROF_FRAME records
  void dump_stack_traces();

 public:
  VM_HeapDumper(DumpWriter* writer, bool gc_before_heap_dump, bool oome, uint num_dump_threads) :
    VM_GC_Operation(0 /* total collections,      dummy, ignored */,
                    GCCause::_heap_dump /* GC Cause */,
                    0 /* total full collections, dummy, ignored */,
                    gc_before_heap_dump),
    WorkerTask("dump heap") {
    _local_writer = writer;
    _gc_before_heap_dump = gc_before_heap_dump;
    _klass_map = new (mtServiceability) GrowableArray<Klass*>(INITIAL_CLASS_COUNT, mtServiceability);
    _stack_traces = nullptr;
    _num_threads = 0;
    _num_dumper_threads = num_dump_threads;
    _dumper_controller = nullptr;
    _poi = nullptr;
    if (oome) {
      assert(!Thread::current()->is_VM_thread(), "Dump from OutOfMemoryError cannot be called by the VMThread");
      // get OutOfMemoryError zero-parameter constructor
      InstanceKlass* oome_ik = vmClasses::OutOfMemoryError_klass();
      _oome_constructor = oome_ik->find_method(vmSymbols::object_initializer_name(),
                                                          vmSymbols::void_method_signature());
      // get thread throwing OOME when generating the heap dump at OOME
      _oome_thread = JavaThread::current();
    } else {
      _oome_thread = nullptr;
      _oome_constructor = nullptr;
    }
  }

  ~VM_HeapDumper() {
    if (_stack_traces != nullptr) {
      for (int i=0; i < _num_threads; i++) {
        delete _stack_traces[i];
      }
      FREE_C_HEAP_ARRAY(ThreadStackTrace*, _stack_traces);
    }
    if (_dumper_controller != nullptr) {
      delete _dumper_controller;
      _dumper_controller = nullptr;
    }
    delete _klass_map;
  }
  bool is_parallel_dump()  { return _num_dumper_threads > 1; }
  bool can_parallel_dump() {
    const char* base_path = writer()->get_file_path();
    if ((strlen(base_path) + 7/*.p\d\d\d\d\0*/) >= JVM_MAXPATHLEN) {
      // no extra path room for separate heap dump files
      return false;
    }
    return true;
  }
  VMOp_Type type() const { return VMOp_HeapDumper; }
  void doit();
  void work(uint worker_id);
};

VM_HeapDumper* VM_HeapDumper::_global_dumper = nullptr;
DumpWriter*    VM_HeapDumper::_global_writer = nullptr;

bool VM_HeapDumper::skip_operation() const {
  return false;
}

// fixes up the current dump record and writes HPROF_HEAP_DUMP_END record
void DumperSupport::end_of_dump(AbstractDumpWriter* writer) {
  writer->finish_dump_segment();

  writer->write_u1(HPROF_HEAP_DUMP_END);
  writer->write_u4(0);
  writer->write_u4(0);
}

// writes a HPROF_LOAD_CLASS record for the class
void VM_HeapDumper::do_load_class(Klass* k) {
  static u4 class_serial_num = 0;

  // len of HPROF_LOAD_CLASS record
  u4 remaining = 2*oopSize + 2*sizeof(u4);

  DumperSupport::write_header(writer(), HPROF_LOAD_CLASS, remaining);

  // class serial number is just a number
  writer()->write_u4(++class_serial_num);

  // class ID
  writer()->write_classID(k);

  // add the Klass* and class serial number pair
  dumper()->add_class_serial_number(k, class_serial_num);

  writer()->write_u4(STACK_TRACE_ID);

  // class name ID
  Symbol* name = k->name();
  writer()->write_symbolID(name);
}

// writes a HPROF_GC_CLASS_DUMP record for the given class
void VM_HeapDumper::do_class_dump(Klass* k) {
  if (k->is_instance_klass()) {
    DumperSupport::dump_instance_class(writer(), k);
  } else {
    DumperSupport::dump_array_class(writer(), k);
  }
}

// Walk the stack of the given thread.
// Dumps a HPROF_GC_ROOT_JAVA_FRAME record for each local
// Dumps a HPROF_GC_ROOT_JNI_LOCAL record for each JNI local
//
// It returns the number of Java frames in this thread stack
int VM_HeapDumper::do_thread(JavaThread* java_thread, u4 thread_serial_num) {
  JNILocalsDumper blk(writer(), thread_serial_num);

  oop threadObj = java_thread->threadObj();
  assert(threadObj != nullptr, "sanity check");

  int stack_depth = 0;
  if (java_thread->has_last_Java_frame()) {

    // vframes are resource allocated
    Thread* current_thread = Thread::current();
    ResourceMark rm(current_thread);
    HandleMark hm(current_thread);

    RegisterMap reg_map(java_thread,
                        RegisterMap::UpdateMap::include,
                        RegisterMap::ProcessFrames::include,
                        RegisterMap::WalkContinuation::skip);
    frame f = java_thread->last_frame();
    vframe* vf = vframe::new_vframe(&f, &reg_map, java_thread);
    frame* last_entry_frame = nullptr;
    int extra_frames = 0;

    if (java_thread == _oome_thread && _oome_constructor != nullptr) {
      extra_frames++;
    }
    while (vf != nullptr) {
      blk.set_frame_number(stack_depth);
      if (vf->is_java_frame()) {

        // java frame (interpreted, compiled, ...)
        javaVFrame *jvf = javaVFrame::cast(vf);
        if (!(jvf->method()->is_native())) {
          StackValueCollection* locals = jvf->locals();
          for (int slot=0; slot<locals->size(); slot++) {
            if (locals->at(slot)->type() == T_OBJECT) {
              oop o = locals->obj_at(slot)();

              if (o != nullptr) {
                u4 size = 1 + sizeof(address) + 4 + 4;
                writer()->start_sub_record(HPROF_GC_ROOT_JAVA_FRAME, size);
                writer()->write_objectID(o);
                writer()->write_u4(thread_serial_num);
                writer()->write_u4((u4) (stack_depth + extra_frames));
                writer()->end_sub_record();
              }
            }
          }
          StackValueCollection *exprs = jvf->expressions();
          for(int index = 0; index < exprs->size(); index++) {
            if (exprs->at(index)->type() == T_OBJECT) {
               oop o = exprs->obj_at(index)();
               if (o != nullptr) {
                 u4 size = 1 + sizeof(address) + 4 + 4;
                 writer()->start_sub_record(HPROF_GC_ROOT_JAVA_FRAME, size);
                 writer()->write_objectID(o);
                 writer()->write_u4(thread_serial_num);
                 writer()->write_u4((u4) (stack_depth + extra_frames));
                 writer()->end_sub_record();
               }
             }
          }
        } else {
          // native frame
          if (stack_depth == 0) {
            // JNI locals for the top frame.
            java_thread->active_handles()->oops_do(&blk);
          } else {
            if (last_entry_frame != nullptr) {
              // JNI locals for the entry frame
              assert(last_entry_frame->is_entry_frame(), "checking");
              last_entry_frame->entry_frame_call_wrapper()->handles()->oops_do(&blk);
            }
          }
        }
        // increment only for Java frames
        stack_depth++;
        last_entry_frame = nullptr;

      } else {
        // externalVFrame - if it's an entry frame then report any JNI locals
        // as roots when we find the corresponding native javaVFrame
        frame* fr = vf->frame_pointer();
        assert(fr != nullptr, "sanity check");
        if (fr->is_entry_frame()) {
          last_entry_frame = fr;
        }
      }
      vf = vf->sender();
    }
  } else {
    // no last java frame but there may be JNI locals
    java_thread->active_handles()->oops_do(&blk);
  }
  return stack_depth;
}


// write a HPROF_GC_ROOT_THREAD_OBJ record for each java thread. Then walk
// the stack so that locals and JNI locals are dumped.
void VM_HeapDumper::do_threads() {
  for (int i=0; i < _num_threads; i++) {
    JavaThread* thread = _stack_traces[i]->thread();
    oop threadObj = thread->threadObj();
    u4 thread_serial_num = i+1;
    u4 stack_serial_num = thread_serial_num + STACK_TRACE_ID;
    u4 size = 1 + sizeof(address) + 4 + 4;
    writer()->start_sub_record(HPROF_GC_ROOT_THREAD_OBJ, size);
    writer()->write_objectID(threadObj);
    writer()->write_u4(thread_serial_num);  // thread number
    writer()->write_u4(stack_serial_num);   // stack trace serial number
    writer()->end_sub_record();
    int num_frames = do_thread(thread, thread_serial_num);
    assert(num_frames == _stack_traces[i]->get_stack_depth(),
           "total number of Java frames not matched");
  }
}


// The VM operation that dumps the heap. The dump consists of the following
// records:
//
//  HPROF_HEADER
//  [HPROF_UTF8]*
//  [HPROF_LOAD_CLASS]*
//  [[HPROF_FRAME]*|HPROF_TRACE]*
//  [HPROF_GC_CLASS_DUMP]*
//  [HPROF_HEAP_DUMP_SEGMENT]*
//  HPROF_HEAP_DUMP_END
//
// The HPROF_TRACE records represent the stack traces where the heap dump
// is generated and a "dummy trace" record which does not include
// any frames. The dummy trace record is used to be referenced as the
// unknown object alloc site.
//
// Each HPROF_HEAP_DUMP_SEGMENT record has a length followed by sub-records.
// To allow the heap dump be generated in a single pass we remember the position
// of the dump length and fix it up after all sub-records have been written.
// To generate the sub-records we iterate over the heap, writing
// HPROF_GC_INSTANCE_DUMP, HPROF_GC_OBJ_ARRAY_DUMP, and HPROF_GC_PRIM_ARRAY_DUMP
// records as we go. Once that is done we write records for some of the GC
// roots.

void VM_HeapDumper::doit() {

  CollectedHeap* ch = Universe::heap();

  ch->ensure_parsability(false); // must happen, even if collection does
                                 // not happen (e.g. due to GCLocker)

  if (_gc_before_heap_dump) {
    if (GCLocker::is_active()) {
      warning("GC locker is held; pre-heapdump GC was skipped");
    } else {
      ch->collect_as_vm_thread(GCCause::_heap_dump);
    }
  }

  // At this point we should be the only dumper active, so
  // the following should be safe.
  set_global_dumper();
  set_global_writer();

  WorkerThreads* workers = ch->safepoint_workers();
  uint  num_active_workers = workers != nullptr ? workers->active_workers() : 0;
  uint requested_num_dump_thread = _num_dumper_threads;

  if (num_active_workers <= 1 ||         // serial gc?
      requested_num_dump_thread <= 1 ||  // request serial dump?
     !can_parallel_dump()) {             // can not dump in parallel?
    // Use serial dump, set dumper threads and writer threads number to 1.
    _num_dumper_threads = 1;
    work(0);
  } else {
    // Use parallel dump otherwise
    _num_dumper_threads = clamp(requested_num_dump_thread, 2U, num_active_workers);
    uint heap_only_dumper_threads = _num_dumper_threads - 1 /* VMDumper thread */;
    _dumper_controller = new (std::nothrow) DumperController(heap_only_dumper_threads);
    ParallelObjectIterator poi(_num_dumper_threads);
    _poi = &poi;
    workers->run_task(this, _num_dumper_threads);
    _poi = nullptr;
  }

  // Now we clear the global variables, so that a future dumper can run.
  clear_global_dumper();
  clear_global_writer();
}

static int volatile dump_seq = 0;

void VM_HeapDumpMerge::merge_done() {
  // Writes the HPROF_HEAP_DUMP_END record.
  if (!_has_error) {
    DumperSupport::end_of_dump(_writer);
    _writer->flush();
  }
  dump_seq = 0; //reset
}

void VM_HeapDumpMerge::merge_file(char* path) {
  assert(!SafepointSynchronize::is_at_safepoint(), "merging happens outside safepoint");
  TraceTime timer("Merge segmented heap file", TRACETIME_LOG(Info, heapdump));

  fileStream part_fs(path, "r");
  if (!part_fs.is_open()) {
    log_error(heapdump)("Can not open segmented heap file %s during merging", path);
    _writer->set_error("Can not open segmented heap file during merging");
    _has_error = true;
    return;
  }

  jlong total = 0;
  int cnt = 0;
  char read_buf[4096];
  while ((cnt = part_fs.read(read_buf, 1, 4096)) != 0) {
    _writer->write_raw(read_buf, cnt);
    total += cnt;
  }

  _writer->flush();
  if (part_fs.fileSize() != total) {
    log_error(heapdump)("Merged heap dump %s is incomplete", path);
    _writer->set_error("Merged heap dump is incomplete");
    _has_error = true;
  }
}

void VM_HeapDumpMerge::doit() {
  assert(!SafepointSynchronize::is_at_safepoint(), "merging happens outside safepoint");
  TraceTime timer("Merge heap files complete", TRACETIME_LOG(Info, heapdump));

  // Since contents in segmented heap file were already zipped, we don't need to zip
  // them again during merging.
  AbstractCompressor* saved_compressor = _writer->compressor();
  _writer->set_compressor(nullptr);

  // merge segmented heap file and remove it anyway
  char path[JVM_MAXPATHLEN];
  for (int i = 0; i < dump_seq; i++) {
    memset(path, 0, JVM_MAXPATHLEN);
    os::snprintf(path, JVM_MAXPATHLEN, "%s.p%d", _path, i);
    if (!_has_error) {
      merge_file(path);
    }
    remove(path);
  }

  // restore compressor for further use
  _writer->set_compressor(saved_compressor);
  merge_done();
}

// prepare DumpWriter for every parallel dump thread
DumpWriter* VM_HeapDumper::create_dump_writer() {
  char* path = NEW_RESOURCE_ARRAY(char, JVM_MAXPATHLEN);
  memset(path, 0, JVM_MAXPATHLEN);
  const char* base_path = writer()->get_file_path();
  AbstractCompressor* compressor = writer()->compressor();
  int seq = Atomic::fetch_and_add(&dump_seq, 1);
  os::snprintf(path, JVM_MAXPATHLEN, "%s.p%d", base_path, seq);
  FileWriter* file_writer = new (std::nothrow) FileWriter(path, writer()->is_overwrite());
  DumpWriter* new_writer = new DumpWriter(file_writer, compressor);
  return new_writer;
}

void VM_HeapDumper::work(uint worker_id) {
  // VM Dumper works on all non-heap data dumping and part of heap iteration.
  if (is_vm_dumper(worker_id)) {
    TraceTime timer("Dump non-objects", TRACETIME_LOG(Info, heapdump));
    // Write the file header - we always use 1.0.2
    const char* header = "JAVA PROFILE 1.0.2";

    // header is few bytes long - no chance to overflow int
    writer()->write_raw(header, strlen(header) + 1); // NUL terminated
    writer()->write_u4(oopSize);
    // timestamp is current time in ms
    writer()->write_u8(os::javaTimeMillis());
    // HPROF_UTF8 records
    SymbolTableDumper sym_dumper(writer());
    SymbolTable::symbols_do(&sym_dumper);

    // write HPROF_LOAD_CLASS records
    {
      LockedClassesDo locked_load_classes(&do_load_class);
      ClassLoaderDataGraph::classes_do(&locked_load_classes);
    }

    // write HPROF_FRAME and HPROF_TRACE records
    // this must be called after _klass_map is built when iterating the classes above.
    dump_stack_traces();

    // Writes HPROF_GC_CLASS_DUMP records
    {
      LockedClassesDo locked_dump_class(&do_class_dump);
      ClassLoaderDataGraph::classes_do(&locked_dump_class);
    }

    // HPROF_GC_ROOT_THREAD_OBJ + frames + jni locals
    do_threads();

    // HPROF_GC_ROOT_JNI_GLOBAL
    JNIGlobalsDumper jni_dumper(writer());
    JNIHandles::oops_do(&jni_dumper);
    // technically not jni roots, but global roots
    // for things like preallocated throwable backtraces
    Universe::vm_global()->oops_do(&jni_dumper);
    // HPROF_GC_ROOT_STICKY_CLASS
    // These should be classes in the null class loader data, and not all classes
    // if !ClassUnloading
    StickyClassDumper class_dumper(writer());
    ClassLoaderData::the_null_class_loader_data()->classes_do(&class_dumper);
  }

  // Heap iteration.
  // writes HPROF_GC_INSTANCE_DUMP records.
  // After each sub-record is written check_segment_length will be invoked
  // to check if the current segment exceeds a threshold. If so, a new
  // segment is started.
  // The HPROF_GC_CLASS_DUMP and HPROF_GC_INSTANCE_DUMP are the vast bulk
  // of the heap dump.
  if (!is_parallel_dump()) {
    assert(worker_id == 0, "must be");
    // == Serial dump
    TraceTime timer("Dump heap objects", TRACETIME_LOG(Info, heapdump));
    HeapObjectDumper obj_dumper(writer());
    Universe::heap()->object_iterate(&obj_dumper);
    writer()->finish_dump_segment();
    // Writes the HPROF_HEAP_DUMP_END record because merge does not happen in serial dump
    DumperSupport::end_of_dump(writer());
    writer()->flush();
  } else {
    // == Parallel dump
    ResourceMark rm;
    TraceTime timer("Dump heap objects in parallel", TRACETIME_LOG(Info, heapdump));
    DumpWriter* dw = is_vm_dumper(worker_id) ? writer() : create_dump_writer();
    {
      HeapObjectDumper obj_dumper(dw);
      _poi->object_iterate(&obj_dumper, worker_id);
    }
    dw->finish_dump_segment();
    dw->flush();
    if (is_vm_dumper(worker_id)) {
      _dumper_controller->wait_all_dumpers_complete();
    } else {
      _dumper_controller->dumper_complete(dw, writer());
      return;
    }
  }
  // At this point, all fragments of the heapdump have been written to separate files.
  // We need to merge them into a complete heapdump and write HPROF_HEAP_DUMP_END at that time.
}

void VM_HeapDumper::dump_stack_traces() {
  // write a HPROF_TRACE record without any frames to be referenced as object alloc sites
  DumperSupport::write_header(writer(), HPROF_TRACE, 3*sizeof(u4));
  writer()->write_u4((u4) STACK_TRACE_ID);
  writer()->write_u4(0);                    // thread number
  writer()->write_u4(0);                    // frame count

  _stack_traces = NEW_C_HEAP_ARRAY(ThreadStackTrace*, Threads::number_of_threads(), mtInternal);
  int frame_serial_num = 0;
  for (JavaThreadIteratorWithHandle jtiwh; JavaThread *thread = jtiwh.next(); ) {
    oop threadObj = thread->threadObj();
    if (threadObj != nullptr && !thread->is_exiting() && !thread->is_hidden_from_external_view()) {
      // dump thread stack trace
      Thread* current_thread = Thread::current();
      ResourceMark rm(current_thread);
      HandleMark hm(current_thread);

      ThreadStackTrace* stack_trace = new ThreadStackTrace(thread, false);
      stack_trace->dump_stack_at_safepoint(-1, /* ObjectMonitorsHashtable is not needed here */ nullptr, true);
      _stack_traces[_num_threads++] = stack_trace;

      // write HPROF_FRAME records for this thread's stack trace
      int depth = stack_trace->get_stack_depth();
      int thread_frame_start = frame_serial_num;
      int extra_frames = 0;
      // write fake frame that makes it look like the thread, which caused OOME,
      // is in the OutOfMemoryError zero-parameter constructor
      if (thread == _oome_thread && _oome_constructor != nullptr) {
        int oome_serial_num = _klass_map->find(_oome_constructor->method_holder());
        // the class serial number starts from 1
        assert(oome_serial_num > 0, "OutOfMemoryError class not found");
        DumperSupport::dump_stack_frame(writer(), ++frame_serial_num, oome_serial_num,
                                        _oome_constructor, 0);
        extra_frames++;
      }
      for (int j=0; j < depth; j++) {
        StackFrameInfo* frame = stack_trace->stack_frame_at(j);
        Method* m = frame->method();
        int class_serial_num = _klass_map->find(m->method_holder());
        // the class serial number starts from 1
        assert(class_serial_num > 0, "class not found");
        DumperSupport::dump_stack_frame(writer(), ++frame_serial_num, class_serial_num, m, frame->bci());
      }
      depth += extra_frames;

      // write HPROF_TRACE record for one thread
      DumperSupport::write_header(writer(), HPROF_TRACE, 3*sizeof(u4) + depth*oopSize);
      int stack_serial_num = _num_threads + STACK_TRACE_ID;
      writer()->write_u4(stack_serial_num);      // stack trace serial number
      writer()->write_u4((u4) _num_threads);     // thread serial number
      writer()->write_u4(depth);                 // frame count
      for (int j=1; j <= depth; j++) {
        writer()->write_id(thread_frame_start + j);
      }
    }
  }
}

// dump the heap to given path.
int HeapDumper::dump(const char* path, outputStream* out, int compression, bool overwrite, uint num_dump_threads) {
  assert(path != nullptr && strlen(path) > 0, "path missing");

  // print message in interactive case
  if (out != nullptr) {
    out->print_cr("Dumping heap to %s ...", path);
    timer()->start();
  }
  // create JFR event
  EventHeapDump event;

  AbstractCompressor* compressor = nullptr;

  if (compression > 0) {
    compressor = new (std::nothrow) GZipCompressor(compression);

    if (compressor == nullptr) {
      set_error("Could not allocate gzip compressor");
      return -1;
    }
  }

  DumpWriter writer(new (std::nothrow) FileWriter(path, overwrite), compressor);

  if (writer.error() != nullptr) {
    set_error(writer.error());
    if (out != nullptr) {
      out->print_cr("Unable to create %s: %s", path,
        (error() != nullptr) ? error() : "reason unknown");
    }
    return -1;
  }

  // generate the segmented heap dump into separate files
  VM_HeapDumper dumper(&writer, _gc_before_heap_dump, _oome, num_dump_threads);
  if (Thread::current()->is_VM_thread()) {
    assert(SafepointSynchronize::is_at_safepoint(), "Expected to be called at a safepoint");
    dumper.doit();
  } else {
    VMThread::execute(&dumper);
  }

  // record any error that the writer may have encountered
  set_error(writer.error());

  // emit JFR event
  if (error() == nullptr) {
    event.set_destination(path);
    event.set_gcBeforeDump(_gc_before_heap_dump);
    event.set_size(writer.bytes_written());
    event.set_onOutOfMemoryError(_oome);
    event.set_overwrite(overwrite);
    event.set_compression(compression);
    event.commit();
  } else {
    log_debug(cds, heap)("Error %s while dumping heap", error());
  }

  // merge segmented dump files into a complete one, this is not required for serial dump
  if (dumper.is_parallel_dump()) {
    VM_HeapDumpMerge op(path, &writer);
    VMThread::execute(&op);
    set_error(writer.error());
  }

  // print message in interactive case
  if (out != nullptr) {
    timer()->stop();
    if (error() == nullptr) {
      out->print_cr("Heap dump file created [" JULONG_FORMAT " bytes in %3.3f secs]",
                    writer.bytes_written(), timer()->seconds());
    } else {
      out->print_cr("Dump file is incomplete: %s", writer.error());
    }
  }

  return (writer.error() == nullptr) ? 0 : -1;
}

// stop timer (if still active), and free any error string we might be holding
HeapDumper::~HeapDumper() {
  if (timer()->is_active()) {
    timer()->stop();
  }
  set_error(nullptr);
}


// returns the error string (resource allocated), or null
char* HeapDumper::error_as_C_string() const {
  if (error() != nullptr) {
    char* str = NEW_RESOURCE_ARRAY(char, strlen(error())+1);
    strcpy(str, error());
    return str;
  } else {
    return nullptr;
  }
}

// set the error string
void HeapDumper::set_error(char const* error) {
  if (_error != nullptr) {
    os::free(_error);
  }
  if (error == nullptr) {
    _error = nullptr;
  } else {
    _error = os::strdup(error);
    assert(_error != nullptr, "allocation failure");
  }
}

// Called by out-of-memory error reporting by a single Java thread
// outside of a JVM safepoint
void HeapDumper::dump_heap_from_oome() {
  HeapDumper::dump_heap(true);
}

// Called by error reporting by a single Java thread outside of a JVM safepoint,
// or by heap dumping by the VM thread during a (GC) safepoint. Thus, these various
// callers are strictly serialized and guaranteed not to interfere below. For more
// general use, however, this method will need modification to prevent
// inteference when updating the static variables base_path and dump_file_seq below.
void HeapDumper::dump_heap() {
  HeapDumper::dump_heap(false);
}

void HeapDumper::dump_heap(bool oome) {
  static char base_path[JVM_MAXPATHLEN] = {'\0'};
  static uint dump_file_seq = 0;
  char* my_path;
  const int max_digit_chars = 20;

  const char* dump_file_name = "java_pid";
  const char* dump_file_ext  = HeapDumpGzipLevel > 0 ? ".hprof.gz" : ".hprof";

  // The dump file defaults to java_pid<pid>.hprof in the current working
  // directory. HeapDumpPath=<file> can be used to specify an alternative
  // dump file name or a directory where dump file is created.
  if (dump_file_seq == 0) { // first time in, we initialize base_path
    // Calculate potentially longest base path and check if we have enough
    // allocated statically.
    const size_t total_length =
                      (HeapDumpPath == nullptr ? 0 : strlen(HeapDumpPath)) +
                      strlen(os::file_separator()) + max_digit_chars +
                      strlen(dump_file_name) + strlen(dump_file_ext) + 1;
    if (total_length > sizeof(base_path)) {
      warning("Cannot create heap dump file.  HeapDumpPath is too long.");
      return;
    }

    bool use_default_filename = true;
    if (HeapDumpPath == nullptr || HeapDumpPath[0] == '\0') {
      // HeapDumpPath=<file> not specified
    } else {
      strcpy(base_path, HeapDumpPath);
      // check if the path is a directory (must exist)
      DIR* dir = os::opendir(base_path);
      if (dir == nullptr) {
        use_default_filename = false;
      } else {
        // HeapDumpPath specified a directory. We append a file separator
        // (if needed).
        os::closedir(dir);
        size_t fs_len = strlen(os::file_separator());
        if (strlen(base_path) >= fs_len) {
          char* end = base_path;
          end += (strlen(base_path) - fs_len);
          if (strcmp(end, os::file_separator()) != 0) {
            strcat(base_path, os::file_separator());
          }
        }
      }
    }
    // If HeapDumpPath wasn't a file name then we append the default name
    if (use_default_filename) {
      const size_t dlen = strlen(base_path);  // if heap dump dir specified
      jio_snprintf(&base_path[dlen], sizeof(base_path)-dlen, "%s%d%s",
                   dump_file_name, os::current_process_id(), dump_file_ext);
    }
    const size_t len = strlen(base_path) + 1;
    my_path = (char*)os::malloc(len, mtInternal);
    if (my_path == nullptr) {
      warning("Cannot create heap dump file.  Out of system memory.");
      return;
    }
    strncpy(my_path, base_path, len);
  } else {
    // Append a sequence number id for dumps following the first
    const size_t len = strlen(base_path) + max_digit_chars + 2; // for '.' and \0
    my_path = (char*)os::malloc(len, mtInternal);
    if (my_path == nullptr) {
      warning("Cannot create heap dump file.  Out of system memory.");
      return;
    }
    jio_snprintf(my_path, len, "%s.%d", base_path, dump_file_seq);
  }
  dump_file_seq++;   // increment seq number for next time we dump

  HeapDumper dumper(false /* no GC before heap dump */,
                    oome  /* pass along out-of-memory-error flag */);
  dumper.dump(my_path, tty, HeapDumpGzipLevel);
  os::free(my_path);
}

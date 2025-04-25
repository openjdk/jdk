/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "memory/resourceArea.hpp"
#include "oops/fieldInfo.inline.hpp"
#include "runtime/atomic.hpp"

void FieldInfo::print(outputStream* os, ConstantPool* cp) {
  os->print_cr("index=%d name_index=%d name=%s signature_index=%d signature=%s offset=%d "
               "AccessFlags=%d FieldFlags=%d "
               "initval_index=%d gen_signature_index=%d, gen_signature=%s contended_group=%d",
                index(),
                name_index(), name(cp)->as_utf8(),
                signature_index(), signature(cp)->as_utf8(),
                offset(),
                access_flags().as_field_flags(),
                field_flags().as_uint(),
                initializer_index(),
                generic_signature_index(),
                _field_flags.is_injected() ? lookup_symbol(generic_signature_index())->as_utf8() : cp->symbol_at(generic_signature_index())->as_utf8(),
                contended_group());
}

void FieldInfo::print_from_growable_array(outputStream* os, GrowableArray<FieldInfo>* array, ConstantPool* cp) {
  for (int i = 0; i < array->length(); i++) {
    array->adr_at(i)->print(os, cp);
  }
}

int FieldInfoStream::compare_symbols(const Symbol *s1, const Symbol *s2) {
  // not lexicographical sort, since we need only total ordering
  int l1 = s1->utf8_length();
  int l2 = s2->utf8_length();
  if (l1 == l2) {
    for (int i = 0; i < l1; ++i) {
      char c1 = s1->char_at(i);
      char c2 = s2->char_at(i);
      if (c1 != c2) {
        return c1 - c2;
      }
    }
    return 0;
  } else {
    return l1 - l2;
  }
}

Array<u1>* FieldInfoStream::create_FieldInfoStream(ConstantPool* constants, GrowableArray<FieldInfo>* fields, int java_fields, int injected_fields,
                                                          ClassLoaderData* loader_data, TRAPS) {
  // The stream format described in fieldInfo.hpp is:
  //   FieldInfoStream := j=num_java_fields k=num_injected_fields JumpTable_offset(0/4 bytes) Field[j+k] JumpTable[(j - 1)/16 > 0] End
  //   JumpTable := stream_index[(j - 1)/16]
  //   Field := name sig offset access flags Optionals(flags)
  //   Optionals(i) := initval?[i&is_init]     // ConstantValue attr
  //                   gsig?[i&is_generic]     // signature attr
  //                   group?[i&is_contended]  // Contended anno (group)
  //   End = 0

  // We create JumpTable only for java_fields; JavaFieldStream relies on non-injected fields preceding injected
#ifdef ASSERT
  if (java_fields > JUMP_TABLE_STRIDE) {
    for (int i = 1; i < java_fields; ++i) {
      assert(compare_symbols(fields->adr_at(i - 1)->name(constants), fields->adr_at(i)->name(constants)) < 0, "Fields should be sorted");
    }
  }
#endif

  using StreamSizer = UNSIGNED5::Sizer<>;
  using StreamFieldSizer = Mapper<StreamSizer>;
  StreamSizer s;
  StreamFieldSizer sizer(&s);

  sizer.consumer()->accept_uint(java_fields);
  sizer.consumer()->accept_uint(injected_fields);
  assert(fields->length() == java_fields + injected_fields, "must be");
  // We need to put JumpTable at end because the position of fields must not depend
  // on the size of JumpTable.
  if (java_fields > JUMP_TABLE_STRIDE) {
    sizer.consumer()->accept_bytes(sizeof(uint32_t));
  }
  ResourceMark rm;
  int *positions = java_fields > JUMP_TABLE_STRIDE ? NEW_RESOURCE_ARRAY(int, (java_fields - 1) / JUMP_TABLE_STRIDE) : nullptr;
  for (int i = 0; i < fields->length(); i++) {
    if (i > 0 && i < java_fields && i % JUMP_TABLE_STRIDE == 0) {
      positions[i / JUMP_TABLE_STRIDE - 1] = sizer.consumer()->position();
    }
    FieldInfo* fi = fields->adr_at(i);
    sizer.map_field_info(*fi);
  }
  for (int i = JUMP_TABLE_STRIDE; i < java_fields; i += JUMP_TABLE_STRIDE) {
    sizer.consumer()->accept_uint(positions[i / JUMP_TABLE_STRIDE - 1]);
  }
  // Originally there was an extra byte with 0 terminating the reading;
  // no we check limits instead as there may be the JumpTable
  int storage_size = sizer.consumer()->position();
  Array<u1>* const fis = MetadataFactory::new_array<u1>(loader_data, storage_size, CHECK_NULL);

  using StreamWriter = UNSIGNED5::Writer<Array<u1>*, int, ArrayHelper<Array<u1>*, int>>;
  using StreamFieldWriter = Mapper<StreamWriter>;
  StreamWriter w(fis);
  StreamFieldWriter writer(&w);

  writer.consumer()->accept_uint(java_fields);
  writer.consumer()->accept_uint(injected_fields);
  int jump_table_offset_pos = w.position();
  if (java_fields > JUMP_TABLE_STRIDE) {
    w.set_position(w.position() + sizeof(uint32_t));
  }
  for (int i = 0; i < fields->length(); i++) {
    FieldInfo* fi = fields->adr_at(i);
    assert(i == 0 || i >= java_fields || i % JUMP_TABLE_STRIDE != 0 ||
      w.position() == positions[i / JUMP_TABLE_STRIDE - 1], "must be");
    writer.map_field_info(*fi);
  }
  if (java_fields > JUMP_TABLE_STRIDE) {
    *reinterpret_cast<uint32_t*>(w.array()->adr_at(jump_table_offset_pos)) = checked_cast<uint32_t>(w.position());
  }
  for (int i = JUMP_TABLE_STRIDE; i < java_fields; i += JUMP_TABLE_STRIDE) {
    writer.consumer()->accept_uint(positions[i / JUMP_TABLE_STRIDE - 1]);
  }

#ifdef ASSERT
  FieldInfoReader r(fis);
  int jfc, ifc;
  r.read_field_counts(&jfc, &ifc);
  assert(jfc == java_fields, "Must be");
  assert(ifc == injected_fields, "Must be");
  for (int i = 0; i < jfc + ifc; i++) {
    FieldInfo fi;
    r.read_field_info(fi);
    FieldInfo* fi_ref = fields->adr_at(i);
    assert(fi_ref->name_index() == fi.name_index(), "Must be");
    assert(fi_ref->signature_index() == fi.signature_index(), "Must be");
    assert(fi_ref->offset() == fi.offset(), "Must be");
    assert(fi_ref->access_flags().as_field_flags() == fi.access_flags().as_field_flags(), "Must be");
    assert(fi_ref->field_flags().as_uint() == fi.field_flags().as_uint(), " Must be");
    if(fi_ref->field_flags().is_initialized()) {
      assert(fi_ref->initializer_index() == fi.initializer_index(), "Must be");
    }
    if (fi_ref->field_flags().is_generic()) {
      assert(fi_ref->generic_signature_index() == fi.generic_signature_index(), "Must be");
    }
    if (fi_ref->field_flags().is_contended()) {
      assert(fi_ref->contended_group() == fi.contended_group(), "Must be");
    }
  }
#endif // ASSERT

  return fis;
}

GrowableArray<FieldInfo>* FieldInfoStream::create_FieldInfoArray(const Array<u1>* fis, int* java_fields_count, int* injected_fields_count) {
  FieldInfoReader r(fis);
  r.read_field_counts(java_fields_count, injected_fields_count);
  int length = *java_fields_count + *injected_fields_count;

  GrowableArray<FieldInfo>* array = new GrowableArray<FieldInfo>(length);
  while (r.has_next()) {
    FieldInfo fi;
    r.read_field_info(fi);
    array->append(fi);
  }
  assert(array->length() == length, "Must be");
  return array;
}

void FieldInfoStream::print_from_fieldinfo_stream(Array<u1>* fis, outputStream* os, ConstantPool* cp) {
  FieldInfoReader r(fis);
  int java_fields_count;
  int injected_fields_count;
  r.read_field_counts(&java_fields_count, &injected_fields_count);
  while (r.has_next()) {
    FieldInfo fi;
    r.read_field_info(fi);
    fi.print(os, cp);
  }
}

int FieldInfoReader::skip_fields_until(const Symbol *name, ConstantPool *cp, int java_fields) {
  int jump_table_size = (java_fields - 1) / JUMP_TABLE_STRIDE;
  if (jump_table_size == 0) {
    return -1;
  }
  int field_pos = -1;
  int field_index = -1;
  UNSIGNED5::Reader<const u1*, int> r2(_r.array());
  r2.set_position(_r.limit());
  for (int i = 0; i < jump_table_size; ++i) {
    int pos = r2.next_uint();
    int pos2 = pos; // read_uint updates this by reference
    uint32_t name_index = UNSIGNED5::read_uint<const u1 *, int>(_r.array(), pos2, _r.limit());
    Symbol *sym = cp->symbol_at(name_index);
    if (FieldInfoStream::compare_symbols(name, sym) < 0) {
      break;
    }
    field_pos = pos;
    field_index = (i + 1) * JUMP_TABLE_STRIDE;
  }
  if (field_pos >= 0) {
    _r.set_position(field_pos);
    _next_index = field_index;
  }
  return field_index;
}

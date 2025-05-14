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
  //   FieldInfoStream := j=num_java_fields k=num_injected_fields SortedFieldTable_offset(0/4 bytes) Field[j+k] SortedFieldRecord[j] End
  //   SortedFieldRecord := stream_position(2-3 bytes) field_index(1-2 bytes)
  //   Field := name sig offset access flags Optionals(flags)
  //   Optionals(i) := initval?[i&is_init]     // ConstantValue attr
  //                   gsig?[i&is_generic]     // signature attr
  //                   group?[i&is_contended]  // Contended anno (group)
  //   End = 0

  using StreamSizer = UNSIGNED5::Sizer<>;
  using StreamFieldSizer = Mapper<StreamSizer>;
  StreamSizer s;
  StreamFieldSizer sizer(&s);

  sizer.consumer()->accept_uint(java_fields);
  sizer.consumer()->accept_uint(injected_fields);
  assert(fields->length() == java_fields + injected_fields, "must be");
  // We need to put JumpTable at end because the position of fields must not depend
  // on the size of JumpTable.
  if (java_fields > SORTED_FIELD_TABLE_THRESHOLD) {
    sizer.consumer()->accept_bytes(sizeof(uint32_t));
  }
  ResourceMark rm;
  // We use both name and signature during the comparison; while JLS require unique
  // names for fields, JVMS requires only unique name + signature combination.
  typedef struct {
    Symbol *name;
    Symbol *signature;
    int index;
    int position;
  } field_pos_t;
  field_pos_t *positions = nullptr;
  int sorted_table_position_width = 0;
  int sorted_table_index_width = 0;
  int sorted_table_item_width = 0;
  if (java_fields > SORTED_FIELD_TABLE_THRESHOLD) {
    positions = NEW_RESOURCE_ARRAY(field_pos_t, java_fields);
    for (int i = 0; i < java_fields; ++i) {
      positions[i].name = fields->at(i).name(constants);
      positions[i].signature = fields->at(i).signature(constants);
      positions[i].index = i;
      positions[i].position = sizer.consumer()->position();
      sizer.map_field_info(fields->at(i));
    }
    for (int i = java_fields; i < fields->length(); ++i) {
      sizer.map_field_info(fields->at(i));
    }
    auto compare_pair = [](const void *v1, const void *v2) {
      int name_result = FieldInfoStream::compare_symbols(
        reinterpret_cast<const field_pos_t *>(v1)->name,
        reinterpret_cast<const field_pos_t *>(v2)->name);
      if (name_result != 0) {
        return name_result;
      }
      return FieldInfoStream::compare_symbols(
        reinterpret_cast<const field_pos_t *>(v1)->signature,
        reinterpret_cast<const field_pos_t *>(v2)->signature);
    };
    qsort(positions, java_fields, sizeof(field_pos_t), compare_pair);

    // We use fixed width to let us skip through the table during binary search.
    // With the max of 65536 fields (and at most tens of bytes per field),
    // 3-byte offsets would suffice. In the common case with < 64kB stream 2-byte offsets are enough.
    sorted_table_position_width = sizer.consumer()->position() > (UINT16_MAX + 1) ? 3 : 2;
    sorted_table_index_width = java_fields > (UINT8_MAX + 1) ? 2 : 1;
    sorted_table_item_width = sorted_table_position_width + sorted_table_index_width;
  } else {
    for (int i = 0; i < fields->length(); ++i) {
      sizer.map_field_info(fields->at(i));
    }
  }
  // Originally there was an extra byte with 0 terminating the reading;
  // now we check limits instead as there may be the SortedTable
  int storage_size = sizer.consumer()->position() + java_fields * sorted_table_item_width;
  Array<u1>* const fis = MetadataFactory::new_array<u1>(loader_data, storage_size, CHECK_NULL);

  using StreamWriter = UNSIGNED5::Writer<Array<u1>*, int, ArrayHelper<Array<u1>*, int>>;
  using StreamFieldWriter = Mapper<StreamWriter>;
  StreamWriter w(fis);
  StreamFieldWriter writer(&w);

  writer.consumer()->accept_uint(java_fields);
  writer.consumer()->accept_uint(injected_fields);
  int sorted_table_offset_pos = w.position();
  if (positions != nullptr) {
    w.set_position(w.position() + sizeof(uint32_t));
  }
  for (int i = 0; i < fields->length(); i++) {
    writer.map_field_info(fields->at(i));
  }
  if (java_fields > SORTED_FIELD_TABLE_THRESHOLD) {
    *reinterpret_cast<uint32_t*>(w.array()->adr_at(sorted_table_offset_pos)) = checked_cast<uint32_t>(w.position());

    auto write_position = sorted_table_position_width == 2 ?
      [](u1 *ptr, int position) { *reinterpret_cast<u2*>(ptr) = checked_cast<u2>(position); } :
      [](u1 *ptr, int position) {
        ptr[0] = static_cast<u1>(position);
        ptr[1] = static_cast<u1>(position >> 8);
        ptr[2] = checked_cast<u1>(position >> 16);
      };
    auto write_index = sorted_table_index_width == 1 ?
      [](u1 *ptr, int index) { *ptr = checked_cast<u1>(index); } :
      [](u1 *ptr, int index) { *reinterpret_cast<u2 *>(ptr) = checked_cast<u2>(index); };
     for (int i = 0; i < java_fields; ++i) {
      u1 *ptr = w.array()->adr_at(w.position() + sorted_table_item_width * i);
      write_position(ptr, positions[i].position);
      write_index(ptr + sorted_table_position_width, positions[i].index);
    }
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

int FieldInfoReader::sorted_table_lookup(const Symbol *name, const Symbol *signature, ConstantPool *cp, int java_fields) {
  UNSIGNED5::Reader<const u1*, int> r2(_r.array());
  int low = 0, high = java_fields - 1;
  int table_offset = _r.limit();
  int position_width = (table_offset > UINT16_MAX + 1 ? 3 : 2);
  int item_width = position_width  + (java_fields > UINT8_MAX + 1 ? 2 : 1);
  auto read_position = table_offset > UINT16_MAX + 1 ?
    [](const u1 *ptr) { return (int) ptr[0] + (((int) ptr[1] << 8)) + (((int) ptr[2]) << 16); } :
    [](const u1 *ptr) { return (int) *reinterpret_cast<const u2 *>(ptr); };
  while (low <= high) {
    int mid = low + (high - low) / 2;
    const u1 *ptr = _r.array() + table_offset + item_width * mid;
    int position = read_position(ptr);
    r2.set_position(position);
    Symbol *mid_name = cp->symbol_at(checked_cast<u2>(r2.next_uint()));
    Symbol *mid_sig = cp->symbol_at(checked_cast<u2>(r2.next_uint()));

    if (mid_name == name && mid_sig == signature) {
      _r.set_position(position);
      _next_index = java_fields > UINT8_MAX + 1 ?
        *reinterpret_cast<const u2 *>(ptr + position_width) : ptr[position_width];
      return _next_index;
    }

    int cmp = FieldInfoStream::compare_symbols(name, mid_name);
    if (cmp < 0) {
      high = mid - 1;
      continue;
    } else if (cmp > 0) {
      low = mid + 1;
      continue;
    }
    cmp = FieldInfoStream::compare_symbols(signature, mid_sig);
    assert(cmp != 0, "Equality check above did not match");
    if (cmp < 0) {
      high = mid - 1;
    } else {
      low = mid + 1;
    }
  }
  return -1;
}

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

Array<u1>* FieldInfoStream::create_FieldInfoStream(GrowableArray<FieldInfo>* fields, int java_fields, int injected_fields,
                                                          ClassLoaderData* loader_data, TRAPS) {
  // The stream format described in fieldInfo.hpp is:
  //   FieldInfoStream := j=num_java_fields k=num_injected_fields Field[j+k] End
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
  for (int i = 0; i < fields->length(); i++) {
    FieldInfo* fi = fields->adr_at(i);
    sizer.map_field_info(*fi);
  }
  int storage_size = sizer.consumer()->position() + 1;
  Array<u1>* const fis = MetadataFactory::new_array<u1>(loader_data, storage_size, CHECK_NULL);

  using StreamWriter = UNSIGNED5::Writer<Array<u1>*, int, ArrayHelper<Array<u1>*, int>>;
  using StreamFieldWriter = Mapper<StreamWriter>;
  StreamWriter w(fis);
  StreamFieldWriter writer(&w);

  writer.consumer()->accept_uint(java_fields);
  writer.consumer()->accept_uint(injected_fields);
  for (int i = 0; i < fields->length(); i++) {
    FieldInfo* fi = fields->adr_at(i);
    writer.map_field_info(*fi);
  }

#ifdef ASSERT
  FieldInfoReader r(fis);
  int jfc = r.next_uint();
  assert(jfc == java_fields, "Must be");
  int ifc = r.next_uint();
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
  int length = FieldInfoStream::num_total_fields(fis);
  GrowableArray<FieldInfo>* array = new GrowableArray<FieldInfo>(length);
  FieldInfoReader r(fis);
  *java_fields_count = r.next_uint();
  *injected_fields_count = r.next_uint();
  while (r.has_next()) {
    FieldInfo fi;
    r.read_field_info(fi);
    array->append(fi);
  }
  assert(array->length() == length, "Must be");
  assert(array->length() == *java_fields_count + *injected_fields_count, "Must be");
  return array;
}

void FieldInfoStream::print_from_fieldinfo_stream(Array<u1>* fis, outputStream* os, ConstantPool* cp) {
  int length = FieldInfoStream::num_total_fields(fis);
  FieldInfoReader r(fis);
  int java_field_count = r.next_uint();
  int injected_fields_count = r.next_uint();
  while (r.has_next()) {
    FieldInfo fi;
    r.read_field_info(fi);
    fi.print(os, cp);
  }
}

/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "oops/fieldInfo.hpp"
#include "runtime/atomic.hpp"

void FieldStatus::atomic_set_bits(u1& flags, u1 mask) {
  // Atomically update the flags with the bits given
  u1 old_flags, new_flags, witness;
  do {
    old_flags = flags;
    new_flags = old_flags | mask;
    witness = Atomic::cmpxchg(&flags, old_flags, new_flags);
  } while(witness != old_flags);
}

void FieldStatus::atomic_clear_bits(u1& flags, u1 mask) {
  // Atomically update the flags with the bits given
  u1 old_flags, new_flags, witness;
  do {
    old_flags = flags;
    new_flags = old_flags & ~mask;
    witness = Atomic::cmpxchg(&flags, old_flags, new_flags);
  } while(witness != old_flags);
}

FieldInfoReader::FieldInfoReader(const Array<u1>* fi)
  : _r(fi->data(), 0),
    _next_index(0) { }

Array<u1>* FieldInfoStream::create_FieldInfoStream(GrowableArray<FieldInfo>* fields, int java_fields, int injected_fields,
                                                          ClassLoaderData* loader_data, TRAPS) {
  // Creation of the stream might be incorrect, the format described in fieldInfo.hpp is:
  //   FieldInfo := j=num_java_fields k=num_internal_fields Field*[j+k] End
  //   Field := name sig offset access internal Optionals(internal)
  //   Optionals(i) := initval?[i&is_init]     // ConstantValue attr
  //                   gsig?[i&is_generic]     // signature attr
  //                   group?[i&is_contended]  // Contended anno (group)
  //   End = 0

  UNSIGNED5::Sizer<> s;
  Mapper<UNSIGNED5::Sizer<>> sm(&s);
  sm.consumer()->accept_uint(java_fields);
  sm.consumer()->accept_uint(injected_fields);
  for (int i = 0; i < fields->length(); i++) {
    FieldInfo* fi = fields->adr_at(i);
    if (sm.map_required_field_info(fi->name_index(),
                                  fi->signature_index(),
                                  fi->offset(),
                                  fi->access_flags(),
                                  fi->field_flags())) {
      sm.map_optional_field_info(fi->field_flags(),
                                  fi->initializer_index(),
                                  fi->generic_signature_index(),
                                  fi->is_contended() ? fi->contended_group() : 0);
    }
  }
  int storage_size = sm.consumer()->position() + 1;

  Array<u1>* const fis = MetadataFactory::new_array<u1>(loader_data,
                                  storage_size,
                                  CHECK_NULL);

  UNSIGNED5::Writer<Array<u1>*, int, ArrayHelper<Array<u1>*, int>> w(fis);
  Mapper<UNSIGNED5::Writer<Array<u1>*, int, ArrayHelper<Array<u1>*, int>>> wm(&w);
  wm.consumer()->accept_uint(java_fields);
  wm.consumer()->accept_uint(injected_fields);
  for (int i = 0; i < fields->length(); i++) {
    FieldInfo* fi = fields->adr_at(i);
    if (wm.map_required_field_info(fi->name_index(),
                                  fi->signature_index(),
                                  fi->offset(),
                                  fi->access_flags(),
                                  fi->field_flags())) {
      wm.map_optional_field_info(fi->field_flags(),
                                  fi->initializer_index(),
                                  fi->generic_signature_index(),
                                  fi->is_contended() ? fi->contended_group() : 0);
    }
  }

#ifdef ASSERT
  FieldInfoReader r(fis);
  u2 jfc = r.next_uint();
  assert(jfc == java_fields, "Must be");
  int ifc = r.next_uint();
  assert(ifc == injected_fields, "Must be");
  for (int i = 0; i < jfc + ifc; i++) {
    FieldInfo fi;
    bool opt = r.read_required_field_info(fi);
    if (opt) {
      r.read_optional_field_info(fi);
    }
    FieldInfo* fi_ref = fields->adr_at(i);
    assert(fi_ref->name_index() == fi.name_index(), "Must be");
    assert(fi_ref->signature_index() == fi.signature_index(), "Must be");
    assert(fi_ref->offset() == fi.offset(), "Must be");
    assert(fi_ref->access_flags().as_int() == fi.access_flags().as_int(), "Must be");
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
  while(r.has_next()) {
    FieldInfo fi;
    bool opt = r.read_required_field_info(fi);
    if (opt) {
      r.read_optional_field_info(fi);
    }
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
  while(r.has_next()) {
    FieldInfo fi;
    bool opt = r.read_required_field_info(fi);
    if (opt) {
      r.read_optional_field_info(fi);
    }
    fi.print(os, cp);
  }
}
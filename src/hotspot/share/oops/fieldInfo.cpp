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

#include "cds/cdsConfig.hpp"
#include "memory/resourceArea.hpp"
#include "oops/fieldInfo.inline.hpp"
#include "runtime/atomic.hpp"
#include "utilities/packedTable.hpp"

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
                _field_flags.is_generic() ? (_field_flags.is_injected() ?
                  lookup_symbol(generic_signature_index())->as_utf8() : cp->symbol_at(generic_signature_index())->as_utf8()
                  ) : "",
                is_contended() ? contended_group() : 0);
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

  assert(fields->length() == java_fields + injected_fields, "must be");

  sizer.consumer()->accept_uint(java_fields);
  sizer.consumer()->accept_uint(injected_fields);
  for (int i = 0; i < fields->length(); i++) {
    FieldInfo* fi = fields->adr_at(i);
    sizer.map_field_info(*fi);
  }
  // Originally there was an extra byte with 0 terminating the reading;
  // now we check limits instead.
  int storage_size = sizer.consumer()->position();
  Array<u1>* const fis = MetadataFactory::new_array<u1>(loader_data, storage_size, CHECK_NULL);

  using StreamWriter = UNSIGNED5::Writer<Array<u1>*, int, ArrayHelper<Array<u1>*, int>>;
  using StreamFieldWriter = Mapper<StreamWriter>;
  StreamWriter w(fis);
  StreamFieldWriter writer(&w);

  writer.consumer()->accept_uint(java_fields);
  writer.consumer()->accept_uint(injected_fields);
  for (int i = 0; i < fields->length(); i++) {
    writer.map_field_info(fields->at(i));
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

int FieldInfoStream::compare_name_and_sig(const Symbol* n1, const Symbol* s1, const Symbol* n2, const Symbol* s2) {
  int cmp = n1->fast_compare(n2);
  return cmp != 0 ? cmp : s1->fast_compare(s2);
}


// We use both name and signature during the comparison; while JLS require unique
// names for fields, JVMS requires only unique name + signature combination.
struct field_pos {
  Symbol* _name;
  Symbol* _signature;
  int _index;
  int _position;
};

class FieldInfoSupplier: public PackedTableBuilder::Supplier {
  const field_pos* _positions;
  size_t _elements;

public:
  FieldInfoSupplier(const field_pos* positions, size_t elements): _positions(positions), _elements(elements) {}

  bool next(uint32_t* key, uint32_t* value) override {
    if (_elements == 0) {
      return false;
    }
    *key = _positions->_position;
    *value = _positions->_index;
    ++_positions;
    --_elements;
    return true;
  }
};

Array<u1>* FieldInfoStream::create_search_table(ConstantPool* cp, const Array<u1>* fis, ClassLoaderData* loader_data, TRAPS) {
  if (CDSConfig::is_dumping_dynamic_archive()) {
    // We cannot use search table; in case of dynamic archives it should be sorted by "requested" addresses,
    // but Symbol* addresses are coming from _constants, which has "buffered" addresses.
    // For background, see new comments inside allocate_node_impl in symbolTable.cpp
    return nullptr;
  }

  FieldInfoReader r(fis);
  int java_fields;
  int injected_fields;
  r.read_field_counts(&java_fields, &injected_fields);
  assert(java_fields >= 0, "must be");
  if (java_fields == 0 || fis->length() == 0 || static_cast<uint>(java_fields) < BinarySearchThreshold) {
    return nullptr;
  }

  ResourceMark rm;
  field_pos* positions = NEW_RESOURCE_ARRAY(field_pos, java_fields);
  for (int i = 0; i < java_fields; ++i) {
    assert(r.has_next(), "number of fields must match");

    positions[i]._position = r.position();
    FieldInfo fi;
    r.read_field_info(fi);

    positions[i]._name = fi.name(cp);
    positions[i]._signature = fi.signature(cp);
    positions[i]._index = i;
  }
  auto compare_pair = [](const void* v1, const void* v2) {
    const field_pos* p1 = reinterpret_cast<const field_pos*>(v1);
    const field_pos* p2 = reinterpret_cast<const field_pos*>(v2);
    return compare_name_and_sig(p1->_name, p1->_signature, p2->_name, p2->_signature);
  };
  qsort(positions, java_fields, sizeof(field_pos), compare_pair);

  PackedTableBuilder builder(fis->length() - 1, java_fields - 1);
  Array<u1>* table = MetadataFactory::new_array<u1>(loader_data, java_fields * builder.element_bytes(), CHECK_NULL);
  FieldInfoSupplier supplier(positions, java_fields);
  builder.fill(table->data(), static_cast<size_t>(table->length()), supplier);
  return table;
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

class FieldInfoComparator: public PackedTableLookup::Comparator {
  const FieldInfoReader* _reader;
  ConstantPool* _cp;
  const Symbol* _name;
  const Symbol* _signature;

public:
  FieldInfoComparator(const FieldInfoReader* reader, ConstantPool* cp, const Symbol* name, const Symbol* signature):
    _reader(reader), _cp(cp), _name(name), _signature(signature) {}

  int compare_to(uint32_t position) override {
    FieldInfoReader r2(*_reader);
    r2.set_position_and_next_index(position, -1);
    u2 name_index, sig_index;
    r2.read_name_and_signature(&name_index, &sig_index);
    Symbol* mid_name = _cp->symbol_at(name_index);
    Symbol* mid_sig = _cp->symbol_at(sig_index);

    return FieldInfoStream::compare_name_and_sig(_name, _signature, mid_name, mid_sig);
  }

#ifdef ASSERT
  void reset(uint32_t position) override {
    FieldInfoReader r2(*_reader);
    r2.set_position_and_next_index(position, -1);
    u2 name_index, signature_index;
    r2.read_name_and_signature(&name_index, &signature_index);
    _name = _cp->symbol_at(name_index);
    _signature = _cp->symbol_at(signature_index);
  }
#endif // ASSERT
};

#ifdef ASSERT
void FieldInfoStream::validate_search_table(ConstantPool* cp, const Array<u1>* fis, const Array<u1>* search_table) {
  if (search_table == nullptr) {
    return;
  }
  FieldInfoReader reader(fis);
  int java_fields, injected_fields;
  reader.read_field_counts(&java_fields, &injected_fields);
  assert(java_fields > 0, "must be");

  PackedTableLookup lookup(fis->length() - 1, java_fields - 1, search_table);
  assert(lookup.element_bytes() * java_fields == static_cast<unsigned int>(search_table->length()), "size does not match");

  FieldInfoComparator comparator(&reader, cp, nullptr, nullptr);
  // Check 1: assert that elements have the correct order based on the comparison function
  lookup.validate_order(comparator);

  // Check 2: Iterate through the original stream (not just search_table) and try if lookup works as expected
  reader.set_position_and_next_index(0, 0);
  reader.read_field_counts(&java_fields, &injected_fields);
  while (reader.has_next()) {
    int field_start = reader.position();
    FieldInfo fi;
    reader.read_field_info(fi);
    if (fi.field_flags().is_injected()) {
      // checking only java fields that precede injected ones
      break;
    }

    FieldInfoReader r2(fis);
    int index = r2.search_table_lookup(search_table, fi.name(cp), fi.signature(cp), cp, java_fields);
    assert(index == static_cast<int>(fi.index()), "wrong index: %d != %u", index, fi.index());
    assert(index == r2.next_index(), "index should match");
    assert(field_start == r2.position(), "must find the same position");
  }
}
#endif // ASSERT

void FieldInfoStream::print_search_table(outputStream* st, ConstantPool* cp, const Array<u1>* fis, const Array<u1>* search_table) {
  if (search_table == nullptr) {
    return;
  }
  FieldInfoReader reader(fis);
  int java_fields, injected_fields;
  reader.read_field_counts(&java_fields, &injected_fields);
  assert(java_fields > 0, "must be");
  PackedTableLookup lookup(fis->length() - 1, java_fields - 1, search_table);
  auto printer = [&] (size_t offset, uint32_t position, uint32_t index) {
    reader.set_position_and_next_index(position, -1);
    u2 name_index, sig_index;
    reader.read_name_and_signature(&name_index, &sig_index);
    Symbol* name = cp->symbol_at(name_index);
    Symbol* sig = cp->symbol_at(sig_index);
    st->print("   [%zu] #%d,#%d = ", offset, name_index, sig_index);
    name->print_symbol_on(st);
    st->print(":");
    sig->print_symbol_on(st);
    st->print(" @ %p,%p", name, sig);
    st->cr();
  };

  lookup.iterate(printer);
}

int FieldInfoReader::search_table_lookup(const Array<u1>* search_table, const Symbol* name, const Symbol* signature, ConstantPool* cp, int java_fields) {
  assert(java_fields >= 0, "must be");
  if (java_fields == 0) {
    return -1;
  }
  FieldInfoComparator comp(this, cp, name, signature);
  PackedTableLookup lookup(_r.limit() - 1, java_fields - 1, search_table);
  uint32_t position;
  static_assert(sizeof(uint32_t) == sizeof(_next_index), "field size assert");
  if (lookup.search(comp, &position, reinterpret_cast<uint32_t*>(&_next_index))) {
    _r.set_position(static_cast<int>(position));
    return _next_index;
  } else {
    return -1;
  }
}

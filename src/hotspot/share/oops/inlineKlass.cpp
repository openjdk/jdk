/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "cds/archiveUtils.hpp"
#include "cds/cdsConfig.hpp"
#include "classfile/vmSymbols.hpp"
#include "code/codeCache.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/collectedHeap.inline.hpp"
#include "gc/shared/gcLocker.inline.hpp"
#include "interpreter/interpreter.hpp"
#include "logging/log.hpp"
#include "memory/metadataFactory.hpp"
#include "memory/metaspaceClosure.hpp"
#include "oops/access.hpp"
#include "oops/arrayKlass.hpp"
#include "oops/compressedOops.inline.hpp"
#include "oops/fieldStreams.inline.hpp"
#include "oops/flatArrayKlass.hpp"
#include "oops/inlineKlass.inline.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "oops/layoutKind.hpp"
#include "oops/method.hpp"
#include "oops/objArrayKlass.hpp"
#include "oops/oop.inline.hpp"
#include "oops/oopsHierarchy.hpp"
#include "oops/refArrayKlass.hpp"
#include "runtime/fieldDescriptor.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/interfaceSupport.inline.hpp"
#include "runtime/registerMap.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/signature.hpp"
#include "runtime/thread.inline.hpp"
#include "utilities/copy.hpp"
#include "utilities/stringUtils.hpp"

InlineKlass::Members::Members()
  : _extended_sig(nullptr),
    _return_regs(nullptr),
    _pack_handler(nullptr),
    _pack_handler_jobject(nullptr),
    _unpack_handler(nullptr),
    _null_reset_value_offset(0),
    _payload_offset(-1),
    _payload_size_in_bytes(-1),
    _payload_alignment(-1),
    _null_free_non_atomic_size_in_bytes(-1),
    _null_free_non_atomic_alignment(-1),
    _null_free_atomic_size_in_bytes(-1),
    _nullable_atomic_size_in_bytes(-1),
    _nullable_non_atomic_size_in_bytes(-1),
    _null_marker_offset(-1) {
}

InlineKlass::InlineKlass() {
  assert(CDSConfig::is_dumping_archive() || UseSharedSpaces, "only for CDS");
}

// Constructor
InlineKlass::InlineKlass(const ClassFileParser& parser)
    : InstanceKlass(parser, InlineKlass::Kind, markWord::inline_type_prototype()) {
  assert(is_inline_klass(), "sanity");
  assert(prototype_header().is_inline_type(), "sanity");

  // Set up the offset to the members of this klass
  _adr_inline_klass_members = calculate_members_address();

  // Placement install the members
  new (_adr_inline_klass_members) Members();

  // Sanity check construction of the members
  assert(pack_handler() == nullptr, "pack handler not null");
}

address InlineKlass::calculate_members_address() const {
  // The members are placed after all other contents inherited from the InstanceKlass
  return end_of_instance_klass();
}

oop InlineKlass::null_reset_value() const {
  assert(is_initialized() || is_being_initialized() || is_in_error_state(), "null reset value is set at the beginning of initialization");
  oop val = java_mirror()->obj_field_acquire(null_reset_value_offset());
  assert(val != nullptr, "Sanity check");
  return val;
}

void InlineKlass::set_null_reset_value(oop val) {
  assert(val != nullptr, "Sanity check");
  assert(oopDesc::is_oop(val), "Sanity check");
  assert(val->is_inline_type(), "Sanity check");
  assert(val->klass() == this, "sanity check");
  java_mirror()->obj_field_put(null_reset_value_offset(), val);
}

inlineOop InlineKlass::allocate_instance(TRAPS) {
  inlineOop oop = (inlineOop)InstanceKlass::allocate_instance(CHECK_NULL);
  assert(oop->mark().is_inline_type(), "Expected inline type");
  return oop;
}

int InlineKlass::nonstatic_oop_count() {
  int oops = 0;
  int map_count = nonstatic_oop_map_count();
  OopMapBlock* block = start_of_nonstatic_oop_maps();
  OopMapBlock* end = block + map_count;
  while (block != end) {
    oops += block->count();
    block++;
  }
  return oops;
}

// Arrays of...

bool InlineKlass::maybe_flat_in_array() {
  if (!UseArrayFlattening) {
    return false;
  }
  // Too many embedded oops
  if ((FlatArrayElementMaxOops >= 0) && (nonstatic_oop_count() > FlatArrayElementMaxOops)) {
    return false;
  }
  // No flat layout?
  if (!has_nullable_atomic_layout() && !has_null_free_atomic_layout() && !has_null_free_non_atomic_layout()) {
    return false;
  }
  return true;
}

bool InlineKlass::is_always_flat_in_array() {
  if (!UseArrayFlattening) {
    return false;
  }
  // Too many embedded oops
  if ((FlatArrayElementMaxOops >= 0) && (nonstatic_oop_count() > FlatArrayElementMaxOops)) {
    return false;
  }

  // An instance is always flat in an array if we have all layouts. Note that this could change in the future when the
  // flattening policies are updated or if new APIs are added that allow the creation of reference arrays directly.
  return has_nullable_atomic_layout() && has_null_free_atomic_layout() && has_null_free_non_atomic_layout();
}

// Inline type arguments are not passed by reference, instead each
// field of the inline type is passed as an argument. This helper
// function collects the flat field (recursively)
// in a list. Included with the field's type is
// the offset of each field in the inline type: i2c and c2i adapters
// need that to load or store fields. Finally, the list of fields is
// sorted in order of increasing offsets: the adapters and the
// compiled code need to agree upon the order of fields.
//
// The list of basic types that is returned starts with a T_METADATA
// and ends with an extra T_VOID. T_METADATA/T_VOID pairs are used as
// delimiters. Every entry between the two is a field of the inline
// type. If there's an embedded inline type in the list, it also starts
// with a T_METADATA and ends with a T_VOID. This is so we can
// generate a unique fingerprint for the method's adapters and we can
// generate the list of basic types from the interpreter point of view
// (inline types passed as reference: iterate on the list until a
// T_METADATA, drop everything until and including the closing
// T_VOID) or the compiler point of view (each field of the inline
// types is an argument: drop all T_METADATA/T_VOID from the list).
//
// Value classes could also have fields in abstract super value classes.
// Use a HierarchicalFieldStream to get them as well.
int InlineKlass::collect_fields(GrowableArray<SigEntry>* sig, int base_off, int null_marker_offset) {
  int count = 0;
  SigEntry::add_entry(sig, T_METADATA, name(), base_off);
  for (TopDownHierarchicalNonStaticFieldStreamBase fs(this); !fs.done(); fs.next()) {
    assert(!fs.access_flags().is_static(), "TopDownHierarchicalNonStaticFieldStreamBase should not let static fields pass.");
    int offset = base_off + fs.offset() - (base_off > 0 ? payload_offset() : 0);
    InstanceKlass* field_holder = fs.field_descriptor().field_holder();
    // TODO 8284443 Use different heuristic to decide what should be scalarized in the calling convention
    if (fs.is_flat()) {
      // Resolve klass of flat field and recursively collect fields
      int field_null_marker_offset = -1;
      if (!fs.is_null_free_inline_type()) {
        field_null_marker_offset = base_off + fs.null_marker_offset() - (base_off > 0 ? payload_offset() : 0);
      }
      Klass* vk = field_holder->get_inline_type_field_klass(fs.index());
      count += InlineKlass::cast(vk)->collect_fields(sig, offset, field_null_marker_offset);
    } else {
      BasicType bt = Signature::basic_type(fs.signature());
      SigEntry::add_entry(sig, bt,  fs.name(), offset);
      count += type2size[bt];
    }
  }
  int offset = base_off + size_helper()*HeapWordSize - (base_off > 0 ? payload_offset() : 0);
  // Null markers are no real fields, add them manually at the end (C2 relies on this) of the flat fields
  if (null_marker_offset != -1) {
    SigEntry::add_null_marker(sig, name(), null_marker_offset);
    count++;
  }
  SigEntry::add_entry(sig, T_VOID, name(), offset);
  assert(sig->at(0)._bt == T_METADATA && sig->at(sig->length()-1)._bt == T_VOID, "broken structure");
  return count;
}

void InlineKlass::initialize_calling_convention(TRAPS) {
  // Because the pack and unpack handler addresses need to be loadable from generated code,
  // they are stored at a fixed offset in the klass metadata. Since inline type klasses do
  // not have a vtable, the vtable offset is used to store these addresses.
  if (InlineTypeReturnedAsFields || InlineTypePassFieldsAsArgs) {
    ResourceMark rm;
    GrowableArray<SigEntry> sig_vk;
    int nb_fields = collect_fields(&sig_vk);
    if (*PrintInlineKlassFields != '\0') {
      const char* class_name_str = _name->as_C_string();
      if (StringUtils::class_list_match(PrintInlineKlassFields, class_name_str)) {
        ttyLocker ttyl;
        tty->print_cr("Fields of InlineKlass: %s", class_name_str);
        for (const SigEntry& entry : sig_vk) {
          tty->print("  %s: %s+%d", entry._name->as_C_string(), type2name(entry._bt), entry._offset);
          if (entry._null_marker) {
            tty->print(" (null marker)");
          }
          tty->print_cr("");
        }
      }
    }
    Array<SigEntry>* extended_sig = MetadataFactory::new_array<SigEntry>(class_loader_data(), sig_vk.length(), CHECK);
    set_extended_sig(extended_sig);
    for (int i = 0; i < sig_vk.length(); i++) {
      extended_sig->at_put(i, sig_vk.at(i));
    }
    if (can_be_returned_as_fields(/* init= */ true)) {
      nb_fields++;
      BasicType* sig_bt = NEW_RESOURCE_ARRAY(BasicType, nb_fields);
      sig_bt[0] = T_METADATA;
      SigEntry::fill_sig_bt(&sig_vk, sig_bt+1);
      VMRegPair* regs = NEW_RESOURCE_ARRAY(VMRegPair, nb_fields);
      int total = SharedRuntime::java_return_convention(sig_bt, regs, nb_fields);

      if (total > 0) {
        Array<VMRegPair>* return_regs = MetadataFactory::new_array<VMRegPair>(class_loader_data(), nb_fields, CHECK);
        set_return_regs(return_regs);
        for (int i = 0; i < nb_fields; i++) {
          return_regs->at_put(i, regs[i]);
        }

        BufferedInlineTypeBlob* buffered_blob = SharedRuntime::generate_buffered_inline_type_adapter(this);
        if (buffered_blob == nullptr) {
          THROW_MSG(vmSymbols::java_lang_OutOfMemoryError(), "Out of space in CodeCache for adapters");
        }
        set_pack_handler(buffered_blob->pack_fields());
        set_pack_handler_jobject(buffered_blob->pack_fields_jobject());
        set_unpack_handler(buffered_blob->unpack_fields());
        assert(CodeCache::find_blob(pack_handler()) == buffered_blob, "lost track of blob");
        assert(can_be_returned_as_fields(), "sanity");
      }
    }
    if (!can_be_returned_as_fields() && !can_be_passed_as_fields()) {
      MetadataFactory::free_array<SigEntry>(class_loader_data(), extended_sig);
      assert(return_regs() == nullptr, "sanity");
    }
  }
}

void InlineKlass::deallocate_contents(ClassLoaderData* loader_data) {
  if (extended_sig() != nullptr) {
    MetadataFactory::free_array<SigEntry>(loader_data, members()._extended_sig);
    set_extended_sig(nullptr);
  }
  if (return_regs() != nullptr) {
    MetadataFactory::free_array<VMRegPair>(loader_data, members()._return_regs);
    set_return_regs(nullptr);
  }
  cleanup_blobs();
  InstanceKlass::deallocate_contents(loader_data);
}

void InlineKlass::cleanup(InlineKlass* ik) {
  ik->cleanup_blobs();
}

void InlineKlass::cleanup_blobs() {
  if (pack_handler() != nullptr) {
    CodeBlob* buffered_blob = CodeCache::find_blob(pack_handler());
    assert(buffered_blob->is_buffered_inline_type_blob(), "bad blob type");
    BufferBlob::free((BufferBlob*)buffered_blob);
    set_pack_handler(nullptr);
    set_pack_handler_jobject(nullptr);
    set_unpack_handler(nullptr);
  }
}

// Can this inline type be passed as multiple values?
bool InlineKlass::can_be_passed_as_fields() const {
  return InlineTypePassFieldsAsArgs;
}

// Can this inline type be returned as multiple values?
bool InlineKlass::can_be_returned_as_fields(bool init) const {
  return InlineTypeReturnedAsFields && (init || return_regs() != nullptr);
}

// Create handles for all oop fields returned in registers that are going to be live across a safepoint
void InlineKlass::save_oop_fields(const RegisterMap& reg_map, GrowableArray<Handle>& handles) const {
  Thread* thread = Thread::current();
  const Array<SigEntry>* sig_vk = extended_sig();
  const Array<VMRegPair>* regs = return_regs();
  int j = 1;

  for (int i = 0; i < sig_vk->length(); i++) {
    BasicType bt = sig_vk->at(i)._bt;
    if (bt == T_OBJECT || bt == T_ARRAY) {
      VMRegPair pair = regs->at(j);
      oop* loc = (oop*)reg_map.location(pair.first(), nullptr);
      guarantee(loc != nullptr, "bad register save location");
      oop o = *loc;
      assert(oopDesc::is_oop_or_null(o), "Bad oop value: " PTR_FORMAT, p2i(o));
      handles.push(Handle(thread, o));
    }
    if (bt == T_METADATA) {
      continue;
    }
    if (bt == T_VOID &&
        sig_vk->at(i-1)._bt != T_LONG &&
        sig_vk->at(i-1)._bt != T_DOUBLE) {
      continue;
    }
    j++;
  }
  assert(j == regs->length(), "missed a field?");
}

// Update oop fields in registers from handles after a safepoint
void InlineKlass::restore_oop_results(RegisterMap& reg_map, GrowableArray<Handle>& handles) const {
  assert(InlineTypeReturnedAsFields, "Inline types should never be returned as fields");
  const Array<SigEntry>* sig_vk = extended_sig();
  const Array<VMRegPair>* regs = return_regs();
  assert(regs != nullptr, "inconsistent");

  int j = 1;
  int k = 0;
  for (int i = 0; i < sig_vk->length(); i++) {
    BasicType bt = sig_vk->at(i)._bt;
    if (bt == T_OBJECT || bt == T_ARRAY) {
      VMRegPair pair = regs->at(j);
      oop* loc = (oop*)reg_map.location(pair.first(), nullptr);
      guarantee(loc != nullptr, "bad register save location");
      *loc = handles.at(k++)();
    }
    if (bt == T_METADATA) {
      continue;
    }
    if (bt == T_VOID &&
        sig_vk->at(i-1)._bt != T_LONG &&
        sig_vk->at(i-1)._bt != T_DOUBLE) {
      continue;
    }
    j++;
  }
  assert(k == handles.length(), "missed a handle?");
  assert(j == regs->length(), "missed a field?");
}

// Fields are in registers. Create an instance of the inline type and
// initialize it with the values of the fields.
oop InlineKlass::realloc_result(const RegisterMap& reg_map, const GrowableArray<Handle>& handles, TRAPS) {
  oop new_vt = allocate_instance(CHECK_NULL);
  const Array<SigEntry>* sig_vk = extended_sig();
  const Array<VMRegPair>* regs = return_regs();

  int j = 1;
  int k = 0;
  for (int i = 0; i < sig_vk->length(); i++) {
    BasicType bt = sig_vk->at(i)._bt;
    if (bt == T_METADATA) {
      continue;
    }
    if (bt == T_VOID) {
      if (sig_vk->at(i-1)._bt == T_LONG ||
          sig_vk->at(i-1)._bt == T_DOUBLE) {
        j++;
      }
      continue;
    }
    int off = sig_vk->at(i)._offset;
    assert(off > 0, "offset in object should be positive");
    VMRegPair pair = regs->at(j);
    address loc = reg_map.location(pair.first(), nullptr);
    guarantee(loc != nullptr, "bad register save location");
    switch(bt) {
    case T_BOOLEAN: {
      new_vt->bool_field_put(off, *(jboolean*)loc);
      break;
    }
    case T_CHAR: {
      new_vt->char_field_put(off, *(jchar*)loc);
      break;
    }
    case T_BYTE: {
      new_vt->byte_field_put(off, *(jbyte*)loc);
      break;
    }
    case T_SHORT: {
      new_vt->short_field_put(off, *(jshort*)loc);
      break;
    }
    case T_INT: {
      new_vt->int_field_put(off, *(jint*)loc);
      break;
    }
    case T_LONG: {
      new_vt->long_field_put(off, *(jlong*)loc);
      break;
    }
    case T_OBJECT:
    case T_ARRAY: {
      Handle handle = handles.at(k++);
      new_vt->obj_field_put(off, handle());
      break;
    }
    case T_FLOAT: {
      new_vt->float_field_put(off, *(jfloat*)loc);
      break;
    }
    case T_DOUBLE: {
      new_vt->double_field_put(off, *(jdouble*)loc);
      break;
    }
    default:
      ShouldNotReachHere();
    }
    *(intptr_t*)loc = 0xDEAD;
    j++;
  }
  assert(j == regs->length(), "missed a field?");
  assert(k == handles.length(), "missed an oop?");
  return new_vt;
}

// Check if we return an inline type in scalarized form, i.e. check if either
// - The return value is a tagged InlineKlass pointer, or
// - The return value is an inline type oop that is also returned in scalarized form
InlineKlass* InlineKlass::returned_inline_klass(const RegisterMap& map, bool* return_oop, Method* method) {
  BasicType bt = T_METADATA;
  VMRegPair pair;
  int nb = SharedRuntime::java_return_convention(&bt, &pair, 1);
  assert(nb == 1, "broken");

  intptr_t* loc = (intptr_t*)map.location(pair.first(), nullptr);
  guarantee(loc != nullptr, "bad register save location");
  intptr_t ptr = *loc;
  if (is_set_nth_bit(ptr, 0)) {
    // Return value is tagged, must be an InlineKlass pointer
    clear_nth_bit(ptr, 0);
    assert(Metaspace::contains((void*)ptr), "should be klass");
    InlineKlass* vk = (InlineKlass*)ptr;
    assert(vk->can_be_returned_as_fields(), "must be able to return as fields");
    if (return_oop != nullptr) {
      // Not returning an oop
      *return_oop = false;
    }
    return vk;
  }
  // Return value is not tagged, must be a valid oop
  oop o = cast_to_oop(ptr);
  assert(oopDesc::is_oop_or_null(o), "Bad oop return: " PTR_FORMAT, ptr);
  if (return_oop != nullptr && o != nullptr && o->is_inline_type()) {
    // Check if inline type is also returned in scalarized form
    InlineKlass* vk_val = InlineKlass::cast(o->klass());
    InlineKlass* vk_sig = method->returns_inline_type();
    if (vk_val->can_be_returned_as_fields() && vk_sig != nullptr) {
      assert(vk_val == vk_sig, "Unexpected return value");
      return vk_val;
    }
  }
  return nullptr;
}

// CDS support
#if INCLUDE_CDS

void InlineKlass::remove_unshareable_info() {
  InstanceKlass::remove_unshareable_info();

  // update it to point to the "buffered" copy of this class.
  _adr_inline_klass_members = calculate_members_address();
  ArchivePtrMarker::mark_pointer(&_adr_inline_klass_members);

  set_extended_sig(nullptr);
  set_return_regs(nullptr);
  set_pack_handler(nullptr);
  set_pack_handler_jobject(nullptr);
  set_unpack_handler(nullptr);

  assert(pack_handler() == nullptr, "pack handler not null");
}

#endif // CDS

#define BULLET  " - "

void InlineKlass::print_on(outputStream* st) const {
  InstanceKlass::print_on(st);
  members().print_on(st);
  st->print_cr(BULLET"---- LayoutKinds:");
  auto print_layout_kind = [&](LayoutKind lk) {
    if (is_layout_supported(lk)) {
      st->print_cr(BULLET"%s layout: %d/%d",
                   LayoutKindHelper::layout_kind_as_string(lk),
                   layout_size_in_bytes(lk), layout_alignment(lk));
    } else {
      st->print_cr(BULLET"%s layout: -/-",
                   LayoutKindHelper::layout_kind_as_string(lk));
    }
  };
  print_layout_kind(LayoutKind::BUFFERED);
  print_layout_kind(LayoutKind::NULL_FREE_NON_ATOMIC_FLAT);
  print_layout_kind(LayoutKind::NULL_FREE_ATOMIC_FLAT);
  print_layout_kind(LayoutKind::NULLABLE_ATOMIC_FLAT);
  print_layout_kind(LayoutKind::NULLABLE_NON_ATOMIC_FLAT);
}

// Verification

void InlineKlass::verify_on(outputStream* st) {
  InstanceKlass::verify_on(st);
  guarantee(prototype_header().is_inline_type(), "Prototype header is not inline type");
}

void InlineKlass::oop_verify_on(oop obj, outputStream* st) {
  InstanceKlass::oop_verify_on(obj, st);
  guarantee(obj->mark().is_inline_type(), "Header is not inline type");
}

void InlineKlass::Members::print_on(outputStream* st) const {
  st->print_cr(BULLET"---- inline type members:");
  st->print(BULLET"extended signature registers:      ");
  InstanceKlass::print_array_on(st, _extended_sig, [](outputStream* ost, SigEntry pair){
    pair.print_on(ost);
  });
  st->print(BULLET"return registers:                  ");
  InstanceKlass::print_array_on(st, _return_regs, [](outputStream* ost, VMRegPair pair) {
    pair.print_on(ost);
  });
  st->print_cr(BULLET"pack handler:                      " PTR_FORMAT, p2i(_pack_handler));
  st->print_cr(BULLET"pack handler (jobject):            " PTR_FORMAT, p2i(_pack_handler_jobject));
  st->print_cr(BULLET"unpack handler:                    " PTR_FORMAT, p2i(_unpack_handler));
  st->print_cr(BULLET"null reset offset:                 %d", _null_reset_value_offset);
  st->print_cr(BULLET"payload offset:                    %d", _payload_offset);
  st->print_cr(BULLET"payload size (bytes):              %d", _payload_size_in_bytes);
  st->print_cr(BULLET"payload alignment:                 %d", _payload_alignment);
  st->print_cr(BULLET"null-free non-atomic size (bytes): %d", _null_free_non_atomic_size_in_bytes);
  st->print_cr(BULLET"null-free non-atomic alignment:    %d", _null_free_non_atomic_alignment);
  st->print_cr(BULLET"null-free atomic size (bytes):     %d", _null_free_atomic_size_in_bytes);
  st->print_cr(BULLET"nullable atomic size (bytes):      %d", _nullable_atomic_size_in_bytes);
  st->print_cr(BULLET"nullable non-atomic size (bytes):  %d", _nullable_non_atomic_size_in_bytes);
  st->print_cr(BULLET"null marker offset:                %d", _null_marker_offset);
}

#undef BULLET

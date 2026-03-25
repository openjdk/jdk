/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OOPS_VALUEPAYLOAD_INLINE_HPP
#define SHARE_VM_OOPS_VALUEPAYLOAD_INLINE_HPP

#include "oops/valuePayload.hpp"

#include "cppstdlib/type_traits.hpp"
#include "oops/flatArrayKlass.inline.hpp"
#include "oops/flatArrayOop.inline.hpp"
#include "oops/inlineKlass.inline.hpp"
#include "oops/layoutKind.hpp"
#include "oops/oopHandle.inline.hpp"
#include "oops/oopsHierarchy.hpp"
#include "oops/resolvedFieldEntry.hpp"
#include "runtime/fieldDescriptor.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "runtime/javaThread.inline.hpp"
#include "utilities/debug.hpp"
#include "utilities/ostream.hpp"
#include "utilities/vmError.hpp"

template <typename OopOrHandle>
inline ValuePayload::StorageImpl<OopOrHandle>::StorageImpl()
    : _container(nullptr),
      _offset(BAD_OFFSET),
      _klass(nullptr),
      _layout_kind(LayoutKind::UNKNOWN),
      _uses_absolute_addr(false) {}

template <typename OopOrHandle>
inline ValuePayload::StorageImpl<OopOrHandle>::StorageImpl(OopOrHandle container,
                                                           ptrdiff_t offset,
                                                           InlineKlass* klass,
                                                           LayoutKind layout_kind)
    : _container(container),
      _offset(offset),
      _klass(klass),
      _layout_kind(layout_kind),
      _uses_absolute_addr(false) {}

template <typename OopOrHandle>
inline ValuePayload::StorageImpl<OopOrHandle>::StorageImpl(address absolute_addr,
                                                           InlineKlass* klass,
                                                           LayoutKind layout_kind)
    : _absolute_addr(absolute_addr),
      _klass(klass),
      _layout_kind(layout_kind),
      _uses_absolute_addr(true) {}

template <typename OopOrHandle>
inline ValuePayload::StorageImpl<OopOrHandle>::~StorageImpl() {
#ifdef CHECK_UNHANDLED_OOPS
  if (!_uses_absolute_addr) {
    _container.~OopOrHandle();
  }
#else  // CHECK_UNHANDLED_OOPS
  static_assert(std::is_trivially_destructible_v<OopOrHandle>);
#endif // CHECK_UNHANDLED_OOPS
}

template <typename OopOrHandle>
inline ValuePayload::StorageImpl<OopOrHandle>::StorageImpl(const StorageImpl& other)
    : _klass(other._klass),
      _layout_kind(other._layout_kind),
      _uses_absolute_addr(other._uses_absolute_addr) {
  if (_uses_absolute_addr) {
    _absolute_addr = other._absolute_addr;
  } else {
    _container = other._container;
    _offset = other._offset;
  }
}

template <typename OopOrHandle>
inline ValuePayload::StorageImpl<OopOrHandle>&
ValuePayload::StorageImpl<OopOrHandle>::operator=(const StorageImpl& other) {
  if (&other != this) {
    _klass = other._klass;
    _layout_kind = other._layout_kind;
    _uses_absolute_addr = other._uses_absolute_addr;
    if (_uses_absolute_addr) {
      _absolute_addr = other._absolute_addr;
    } else {
      _container = other._container;
      _offset = other._offset;
    }
  }
  return *this;
}

template <typename OopOrHandle>
inline OopOrHandle& ValuePayload::StorageImpl<OopOrHandle>::container() {
  precond(!_uses_absolute_addr);
  return _container;
}

template <typename OopOrHandle>
inline OopOrHandle ValuePayload::StorageImpl<OopOrHandle>::container() const {
  precond(!_uses_absolute_addr);
  return _container;
}

template <typename OopOrHandle>
inline ptrdiff_t& ValuePayload::StorageImpl<OopOrHandle>::offset() {
  precond(!_uses_absolute_addr);
  return _offset;
}

template <typename OopOrHandle>
inline ptrdiff_t ValuePayload::StorageImpl<OopOrHandle>::offset() const {
  precond(!_uses_absolute_addr);
  return _offset;
}

template <typename OopOrHandle>
inline address& ValuePayload::StorageImpl<OopOrHandle>::absolute_addr() {
  precond(_uses_absolute_addr);
  return _absolute_addr;
}

template <typename OopOrHandle>
inline address ValuePayload::StorageImpl<OopOrHandle>::absolute_addr() const {
  precond(_uses_absolute_addr);
  return _absolute_addr;

}

template <typename OopOrHandle>
inline InlineKlass* ValuePayload::StorageImpl<OopOrHandle>::klass() const {
  return _klass;
}

template <typename OopOrHandle>
inline LayoutKind ValuePayload::StorageImpl<OopOrHandle>::layout_kind() const {
  return _layout_kind;
}

template <typename OopOrHandle>
inline bool ValuePayload::StorageImpl<OopOrHandle>::uses_absolute_addr() const {
  return _uses_absolute_addr;
}

inline ValuePayload::ValuePayload(oop container,
                                  ptrdiff_t offset,
                                  InlineKlass* klass,
                                  LayoutKind layout_kind)
    : _storage{container, offset, klass, layout_kind} {
  assert_post_construction_invariants();
}

inline ValuePayload::ValuePayload(address absolute_addr,
                                  InlineKlass* klass,
                                  LayoutKind layout_kind)
    : _storage{absolute_addr, klass, layout_kind} {
  assert_post_construction_invariants();
}

inline void ValuePayload::set_offset(ptrdiff_t offset) {
  _storage.offset() = offset;
}

inline void ValuePayload::copy(const ValuePayload& src,
                               const ValuePayload& dst,
                               LayoutKind copy_layout_kind) {
  assert_pre_copy_invariants(src, dst, copy_layout_kind);

  InlineKlass* const klass = src.klass();

  switch (copy_layout_kind) {
  case LayoutKind::NULLABLE_ATOMIC_FLAT:
  case LayoutKind::NULLABLE_NON_ATOMIC_FLAT: {
    if (src.is_payload_null()) {
      HeapAccess<>::value_store_null(dst);
    } else {
      HeapAccess<>::value_copy(src, dst);
    }
  } break;
  case LayoutKind::BUFFERED:
  case LayoutKind::NULL_FREE_ATOMIC_FLAT:
  case LayoutKind::NULL_FREE_NON_ATOMIC_FLAT: {
    if (!klass->is_empty_inline_type()) {
      HeapAccess<>::value_copy(src, dst);
    }
  } break;
  default:
    ShouldNotReachHere();
  }
}

inline void ValuePayload::mark_as_non_null() {
  precond(has_null_marker());
  klass()->mark_payload_as_non_null(addr());
}

inline void ValuePayload::mark_as_null() {
  precond(has_null_marker());
  klass()->mark_payload_as_null(addr());
}

inline bool ValuePayload::uses_absolute_addr() const {
  return _storage.uses_absolute_addr();
}

inline oop& ValuePayload::container() {
  return _storage.container();
}

inline oop ValuePayload::container() const {
  return _storage.container();
}

#ifdef ASSERT

inline void ValuePayload::print_on(outputStream* st) const {
  if (uses_absolute_addr()) {
    st->print_cr("--- absolute_addr ---");
    StreamIndentor si(st);
    st->print_cr("_absolute_addr: " PTR_FORMAT, p2i(_storage.absolute_addr()));
  } else {
    {
      oop container = _storage.container();
      st->print_cr("--- container ---");
      StreamIndentor si(st);
      st->print_cr("_container: " PTR_FORMAT, p2i(container));
      if (container != nullptr) {
        container->print_on(st);
        st->cr();
      }
    }
    {
      st->print_cr("--- offset ---");
      StreamIndentor si(st);
      st->print_cr("_offset: %zd", _storage.offset());
    }
  }
  {
    InlineKlass* const klass = _storage.klass();
    st->print_cr("--- klass ---");
    StreamIndentor si(st);
    st->print_cr("_klass: " PTR_FORMAT, p2i(klass));
    if (klass != nullptr) {
      klass->print_on(st);
      st->cr();
    }
  }
  {
    const LayoutKind layout_kind = _storage.layout_kind();
    st->print_cr("--- layout_kind ---");
    StreamIndentor si(st);
    st->print_cr("_layout_kind: %u", (uint32_t)layout_kind);
    LayoutKindHelper::print_on(layout_kind, st);
    st->cr();
  }
}

inline void ValuePayload::assert_is_flat_field(const InstanceKlass* klass, int offset) const {
  OnVMError on_assertion_failure_field_descriptor([&](outputStream* st) {
    st->print_cr("=== assert_is_flat_field(" PTR_FORMAT ", %d) failure ===", p2i(klass), offset);
    StreamIndentor si(st);
    if (klass != nullptr) {
      klass->print_on(st);
      st->cr();
    }
  });

  fieldDescriptor field_descriptor;
  postcond(klass->find_flat_field_containing_offset(offset, &field_descriptor));

  const InlineLayoutInfo inline_layout_info = field_descriptor.field_holder()->inline_layout_info(field_descriptor.index());

  OnVMError on_assertion_failure_inline_layout_info([&](outputStream* st) {
    st->print_cr("=== assert_is_flat_field(" PTR_FORMAT ", %d) failure ===", p2i(klass), offset);
      StreamIndentor si(st);
      st->print("field_descriptor: ");
      field_descriptor.print_on(st);
      st->cr();
      st->print("inline_layout_info: ");
      inline_layout_info.print_on(st);
      st->cr();
  });

  if (inline_layout_info.klass() == this->klass()) {
    // Found the field in klass
    postcond(offset == field_descriptor.offset());
    postcond(inline_layout_info.kind() == layout_kind());
    postcond(field_descriptor.layout_kind() == layout_kind());
    postcond(field_descriptor.is_flat());
  } else {
    // Nested flat field
    postcond(offset >= field_descriptor.offset());
    const InlineKlass* const field_klass = inline_layout_info.klass();
    const int payload_offset = field_klass->payload_offset();
    assert_is_flat_field(field_klass, offset - field_descriptor.offset() + payload_offset);
  }
}

inline void ValuePayload::assert_post_construction_invariants() const {
  OnVMError on_assertion_failure([&](outputStream* st) {
    st->print_cr("=== assert_post_construction_invariants failure ===");
    StreamIndentor si(st);
    print_on(st);
    st->cr();
  });

  postcond(layout_kind() != LayoutKind::REFERENCE);
  postcond(layout_kind() != LayoutKind::UNKNOWN);
  postcond(klass()->is_layout_supported(layout_kind()));

  if (!uses_absolute_addr()) {
    postcond(container() != nullptr);
    const Klass* const container_klass = container()->klass();
    if (container_klass == klass()) {
      postcond(layout_kind() == LayoutKind::BUFFERED);
    } else {
      postcond(layout_kind() != LayoutKind::BUFFERED);
      if (container_klass->is_mirror_instance_klass()) {
        fatal("java.lang.Class has no flat fields. Static fields are not flattened");
      } else if (container_klass->is_instance_klass()) {
        assert_is_flat_field(InstanceKlass::cast(container_klass), checked_cast<int>(offset()));
      } else {
        const FlatArrayKlass* const container_flat_array_klass = FlatArrayKlass::cast(container_klass);
        if (container_flat_array_klass->element_klass() == klass()) {
          postcond(container_flat_array_klass->layout_kind() == layout_kind());
        } else {
          // Accessing nested flat field
          const InlineKlass* const element_klass = container_flat_array_klass->element_klass();
          const int element_offset =
              (checked_cast<int>(this->offset()) -
               checked_cast<int>(flatArrayOopDesc::base_offset_in_bytes())) %
              container_flat_array_klass->element_byte_size();
          const int payload_offset = element_klass->payload_offset();
          assert_is_flat_field(element_klass, element_offset + payload_offset);
        }
      }
    }
  }
}

inline void ValuePayload::assert_pre_copy_invariants(const ValuePayload& src,
                                                     const ValuePayload& dst,
                                                     LayoutKind copy_layout_kind) {
  OnVMError on_assertion_failuire([&](outputStream* st) {
    st->print_cr("=== assert_post_construction_invariants failure ===");
    StreamIndentor si(st);
    {
      st->print_cr("--- src payload ---");
      StreamIndentor si(st);
      src.print_on(st);
      st->cr();
    }
    {
      st->print_cr("--- dst payload ---");
      StreamIndentor si(st);
      dst.print_on(st);
      st->cr();
    }
    {
      st->print_cr("--- copy layout kind ---");
      StreamIndentor si(st);
      LayoutKindHelper::print_on(copy_layout_kind, st);
      st->cr();
    }
  });

  const InlineKlass* const src_klass = src.klass();
  const InlineKlass* const dst_klass = dst.klass();

  precond(src_klass == dst_klass);

  const bool src_is_buffered = src.layout_kind() == LayoutKind::BUFFERED;
  const bool dst_is_buffered = dst.layout_kind() == LayoutKind::BUFFERED;
  const bool src_and_dst_same_layout_kind = src.layout_kind() == dst.layout_kind();
  const bool src_has_copy_layout = src.layout_kind() == copy_layout_kind;
  const bool dst_has_copy_layout = dst.layout_kind() == copy_layout_kind;

  precond(src_is_buffered || dst_is_buffered || src_and_dst_same_layout_kind);
  precond(src_has_copy_layout || dst_has_copy_layout);

  if (src_is_buffered) {
    oop container = src.uses_absolute_addr()
        ? cast_to_oop(src.addr() - src_klass->payload_offset())
        : src.container();

    precond(container != src_klass->null_reset_value());
  }

  const int src_layout_size_in_bytes = src_klass->layout_size_in_bytes(src.layout_kind());
  const int dst_layout_size_in_bytes = dst_klass->layout_size_in_bytes(dst.layout_kind());
  const int copy_layout_size_in_bytes =
      src_has_copy_layout
          ? src_layout_size_in_bytes
          : dst_layout_size_in_bytes;

  precond(copy_layout_size_in_bytes <= src_layout_size_in_bytes);
  precond(copy_layout_size_in_bytes <= dst_layout_size_in_bytes);
  precond(LayoutKindHelper::get_copy_layout(src.layout_kind(),
                                            dst.layout_kind()) == copy_layout_kind);
}

#endif // ASSERT

inline InlineKlass* ValuePayload::klass() const {
  return _storage.klass();
}

inline ptrdiff_t ValuePayload::offset() const {
  precond(_storage.offset() != BAD_OFFSET);
  return _storage.offset();
}

inline LayoutKind ValuePayload::layout_kind() const {
  return _storage.layout_kind();
}

inline address ValuePayload::addr() const {
  return uses_absolute_addr()
      ? _storage.absolute_addr()
      : (cast_from_oop<address>(container()) + offset());
}

inline bool ValuePayload::has_null_marker() const {
  return klass()->layout_has_null_marker(layout_kind());
}

inline bool ValuePayload::is_payload_null() const {
  return has_null_marker() && klass()->is_payload_marked_as_null(addr());
}

inline ValuePayload ValuePayload::construct_from_parts(address absolute_addr,
                                                       InlineKlass* klass,
                                                       LayoutKind layout_kind) {
  return ValuePayload(absolute_addr, klass, layout_kind);
}

inline BufferedValuePayload::BufferedValuePayload(inlineOop container,
                                                  ptrdiff_t offset,
                                                  InlineKlass* klass,
                                                  LayoutKind layout_kind)
    : ValuePayload(container, offset, klass, layout_kind) {}

inline BufferedValuePayload::BufferedValuePayload(inlineOop buffer)
    : BufferedValuePayload(buffer, InlineKlass::cast(buffer->klass())) {}

inline BufferedValuePayload::BufferedValuePayload(inlineOop buffer,
                                                  InlineKlass* klass)
    : ValuePayload(buffer, klass->payload_offset(), klass, LayoutKind::BUFFERED) {}

inline inlineOop BufferedValuePayload::container() const {
  return inlineOop(ValuePayload::container());
}

inline void BufferedValuePayload::copy_to(const BufferedValuePayload& dst) {
  copy(*this, dst, LayoutKind::BUFFERED);
}

inline FlatValuePayload::FlatValuePayload(oop container,
                                          ptrdiff_t offset,
                                          InlineKlass* klass,
                                          LayoutKind layout_kind)
    : ValuePayload(container, offset, klass, layout_kind) {}

inline inlineOop FlatValuePayload::allocate_instance(TRAPS) {
  // Preserve the container oop across the instance allocation.
  oop& container = this->container();
  ::Handle container_handle(THREAD, container);
  inlineOop res = klass()->allocate_instance(THREAD);
  container = container_handle();
  return res;
}

inline bool FlatValuePayload::copy_to(BufferedValuePayload& dst) {
  // Copy from FLAT to BUFFERED, null marker fix may be required.

  // Copy the payload to the buffered object.
  copy(*this, dst, layout_kind());

  if (!has_null_marker() && dst.has_null_marker()) {
    // We must fix the null marker if the src does not have a null marker but
    // the buffered object does.
    dst.mark_as_non_null();

    // The buffered object was just marked non null.
    return true;
  }

  if (dst.is_payload_null()) {
    // A null payload is not a valid payload for a buffered value.
    return false;
  }

  return true;
}

inline void FlatValuePayload::copy_from(BufferedValuePayload& src) {
  // Copy from BUFFERED to FLAT, null marker fix may be required.

  if (has_null_marker()) {
    // The FLAT payload has a null mark. So make sure that buffered is marked as
    // non null. It is the callers responsibility to ensure that this is a
    // valid non null value.
    src.mark_as_non_null();
  }
  copy(src, *this, layout_kind());
}

inline void FlatValuePayload::copy_to(const FlatValuePayload& dst) {
  copy(*this, dst, layout_kind());
}

inline inlineOop FlatValuePayload::read(TRAPS) {
  switch (layout_kind()) {
  case LayoutKind::NULLABLE_ATOMIC_FLAT:
  case LayoutKind::NULLABLE_NON_ATOMIC_FLAT: {
    if (is_payload_null()) {
      return nullptr;
    }
  } // Fallthrough
  case LayoutKind::NULL_FREE_ATOMIC_FLAT:
  case LayoutKind::NULL_FREE_NON_ATOMIC_FLAT: {
    inlineOop res = allocate_instance(CHECK_NULL);
    BufferedValuePayload dst(res, klass());
    if (!copy_to(dst)) {
      // copy_to may fail if the payload has been updated with a null value
      // between our is_payload_null() check above and the copy.
      // In this case we have copied a null value into the buffer the payload.
      return nullptr;
    }
    // Must ensure the content of the buffered value is visible
    // before publishing the buffered value oop
    OrderAccess::storestore();
    return res;
  } break;
  default:
    ShouldNotReachHere();
  }
}

inline void FlatValuePayload::write_without_nullability_check(inlineOop obj) {
  if (obj == nullptr) {
    assert(has_null_marker(), "Payload must support null values");
    HeapAccess<>::value_store_null(*this);
  } else {
    // Copy the obj payload
    BufferedValuePayload obj_payload(obj);
    copy_from(obj_payload);
  }
}

inline void FlatValuePayload::write(inlineOop obj, TRAPS) {
  if (obj == nullptr && !has_null_marker()) {
    // This payload does not have a null marker and cannot represent a null
    // value.
    THROW_MSG(vmSymbols::java_lang_NullPointerException(), "Value is null");
  }
  write_without_nullability_check(obj);
}

inline FlatValuePayload FlatValuePayload::construct_from_parts(oop container,
                                                               ptrdiff_t offset,
                                                               InlineKlass* klass,
                                                               LayoutKind layout_kind) {
  return FlatValuePayload(container, offset, klass, layout_kind);
}

inline FlatFieldPayload::FlatFieldPayload(instanceOop container,
                                          ptrdiff_t offset,
                                          InlineKlass* klass,
                                          LayoutKind layout_kind)
    : FlatValuePayload(container, offset, klass, layout_kind) {}

inline FlatFieldPayload::FlatFieldPayload(instanceOop container,
                                          ptrdiff_t offset,
                                          InlineLayoutInfo* inline_layout_info)
    : FlatValuePayload(container, offset, inline_layout_info->klass(), inline_layout_info->kind()) {}

#ifdef ASSERT

inline void FlatFieldPayload::assert_post_construction_invariants(instanceOop container,
                                                                  ResolvedFieldEntry* resolved_field_entry,
                                                                  InstanceKlass* klass) const {
  OnVMError on_assertion_failure([&](outputStream* st) {
    st->print_cr("=== assert_post_construction_invariants failure ===");
    StreamIndentor si(st);
    print_on(st);
    st->cr();
  });

  postcond(container->klass()->is_subclass_of(klass));
  postcond(klass == resolved_field_entry->field_holder());
  postcond(resolved_field_entry->is_flat());
}

inline void FlatFieldPayload::assert_post_construction_invariants(instanceOop container,
                                                                  fieldDescriptor* field_descriptor,
                                                                  InstanceKlass* klass) const {
  OnVMError on_assertion_failure([&](outputStream* st) {
    st->print_cr("=== assert_post_construction_invariants failure ===");
    StreamIndentor si(st);
    print_on(st);
    st->cr();
  });

  postcond(container->klass()->is_subclass_of(klass));
  postcond(klass == field_descriptor->field_holder());
  postcond(field_descriptor->is_flat());
}

#endif // ASSERT

inline FlatFieldPayload::FlatFieldPayload(instanceOop container,
                                          fieldDescriptor* field_descriptor)
    : FlatFieldPayload(container, field_descriptor,
                       InstanceKlass::cast(container->klass())) {}

inline FlatFieldPayload::FlatFieldPayload(instanceOop container,
                                          fieldDescriptor* field_descriptor,
                                          InstanceKlass* klass)
    : FlatFieldPayload(container,
                       field_descriptor->offset(),
                       klass->inline_layout_info_adr(field_descriptor->index())) {
  assert_post_construction_invariants(container, field_descriptor, klass);
}

inline FlatFieldPayload::FlatFieldPayload(instanceOop container,
                                          ResolvedFieldEntry* resolved_field_entry)
    : FlatFieldPayload(container,
                       resolved_field_entry,
                       resolved_field_entry->field_holder()) {}

inline FlatFieldPayload::FlatFieldPayload(instanceOop container,
                                          ResolvedFieldEntry* resolved_field_entry,
                                          InstanceKlass* klass)
    : FlatFieldPayload(container,
                       resolved_field_entry->field_offset(),
                       klass->inline_layout_info_adr(resolved_field_entry->field_index())) {
  assert_post_construction_invariants(container, resolved_field_entry, klass);
}

inline instanceOop FlatFieldPayload::container() const {
  return instanceOop(ValuePayload::container());
}

inline FlatArrayPayload::FlatArrayPayload(flatArrayOop container,
                                          ptrdiff_t offset,
                                          InlineKlass* klass,
                                          LayoutKind layout_kind,
                                          jint layout_helper,
                                          int element_size)
    : FlatValuePayload(container, offset, klass, layout_kind),
      _storage{layout_helper, element_size} {}

inline FlatArrayPayload::FlatArrayPayload(flatArrayOop container)
    : FlatArrayPayload(container, container->klass()) {}

inline FlatArrayPayload::FlatArrayPayload(flatArrayOop container, FlatArrayKlass* klass)
    : FlatArrayPayload(container,
                       BAD_OFFSET,
                       klass->element_klass(),
                       klass->layout_kind(),
                       klass->layout_helper(),
                       klass->element_byte_size()) {
  postcond(container->klass() == klass);
}

inline FlatArrayPayload::FlatArrayPayload(flatArrayOop container, int index)
    : FlatArrayPayload(container, index, container->klass()) {}

inline FlatArrayPayload::FlatArrayPayload(flatArrayOop container,
                                          int index,
                                          FlatArrayKlass* klass)
    : FlatArrayPayload(container,
                       (ptrdiff_t)container->value_offset(index, klass->layout_helper()),
                       klass->element_klass(),
                       klass->layout_kind(),
                       klass->layout_helper(),
                       klass->element_byte_size()) {
  postcond(container->klass() == klass);
}

inline flatArrayOop FlatArrayPayload::container() const {
  return flatArrayOop(ValuePayload::container());
}

inline void FlatArrayPayload::set_index(int index) {
  set_offset((ptrdiff_t)container()->value_offset(index, _storage._layout_helper));
}

inline void FlatArrayPayload::advance_index(int delta) {
  set_offset(this->offset() + delta * _storage._element_size);
}

inline void FlatArrayPayload::next_element() {
  advance_index(1);
}

inline void FlatArrayPayload::previous_element() {
  advance_index(-1);
}

inline void FlatArrayPayload::set_offset(ptrdiff_t offset) {
#if defined(ASSERT) && defined(_LP64)
  // For ease of use as iterators we allow the offset to point one element size
  // beyond the first and last element. If there are no elements only the base
  // offset is allowed. However we treat these as terminal states, and set the
  // offset to a BAD_OFFSET in debug builds.

  const ptrdiff_t element_size = _storage._element_size;
  const ptrdiff_t length = container()->length();
  const ptrdiff_t base_offset = (ptrdiff_t)flatArrayOopDesc::base_offset_in_bytes();

  const ptrdiff_t min_offset = base_offset - (length == 0 ? 0 : element_size);
  const ptrdiff_t max_offset = base_offset + length * element_size;
  assert(min_offset <= offset && offset <= max_offset,
         "Offset out-ouf-bounds: %zd <= %zd <= %zd", min_offset, offset, max_offset);

  if (offset == min_offset || offset == max_offset) {
    // Terminal state of iteration, set a bad value.
    ValuePayload::set_offset(BAD_OFFSET);
  } else {
    ValuePayload::set_offset(offset);
  }
#else  // ASSERT
  ValuePayload::set_offset(offset);
#endif // ASSERT
}

inline ValuePayload::Handle::Handle(const ValuePayload& payload, JavaThread* thread)
    : _storage{::Handle(thread, payload.container()),
               payload.offset(),
               payload.klass(),
               payload.layout_kind()} {}

inline oop ValuePayload::Handle::container() const {
  return _storage.container()();
}

inline InlineKlass* ValuePayload::Handle::klass() const {
  return _storage.klass();
}

inline ptrdiff_t ValuePayload::Handle::offset() const {
  return _storage.offset();
}

inline LayoutKind ValuePayload::Handle::layout_kind() const {
  return _storage.layout_kind();
}

inline ValuePayload::OopHandle::OopHandle(const ValuePayload& payload, OopStorage* storage)
    : _storage{::OopHandle(storage, payload.container()),
               payload.offset(),
               payload.klass(),
               payload.layout_kind()} {}

inline oop ValuePayload::OopHandle::container() const {
  return _storage.container().resolve();
}

inline void ValuePayload::OopHandle::release(OopStorage* storage) {
  return _storage.container().release(storage);
}

inline InlineKlass* ValuePayload::OopHandle::klass() const {
  return _storage.klass();
}

inline ptrdiff_t ValuePayload::OopHandle::offset() const {
  return _storage.offset();
}

inline LayoutKind ValuePayload::OopHandle::layout_kind() const {
  return _storage.layout_kind();
}

inline BufferedValuePayload::Handle::Handle(const BufferedValuePayload& payload, JavaThread* thread)
    : ValuePayload::Handle(payload, thread) {}

inline BufferedValuePayload BufferedValuePayload::Handle::operator()() const {
  return BufferedValuePayload(container(), offset(), klass(), layout_kind());
}

inline inlineOop BufferedValuePayload::Handle::container() const {
  return inlineOop(ValuePayload::Handle::container());
}

inline BufferedValuePayload::Handle BufferedValuePayload::make_handle(JavaThread* thread) const {
  return Handle(*this, thread);
}

inline BufferedValuePayload::OopHandle::OopHandle(const BufferedValuePayload& payload,
                                                  OopStorage* storage)
    : ValuePayload::OopHandle(payload, storage) {}

inline BufferedValuePayload BufferedValuePayload::OopHandle::operator()() const {
  return BufferedValuePayload(container(), offset(), klass(), layout_kind());
}

inline inlineOop BufferedValuePayload::OopHandle::container() const {
  return inlineOop(ValuePayload::OopHandle::container());
}

inline BufferedValuePayload::OopHandle BufferedValuePayload::make_oop_handle(OopStorage* storage) const {
  return OopHandle(*this, storage);
}

inline FlatValuePayload::Handle::Handle(const FlatValuePayload& payload, JavaThread* thread)
    : ValuePayload::Handle(payload, thread) {}

inline FlatValuePayload FlatValuePayload::Handle::operator()() const {
  return FlatValuePayload(container(), offset(), klass(), layout_kind());
}

inline FlatValuePayload::Handle FlatValuePayload::make_handle(JavaThread* thread) const {
  return Handle(*this, thread);
}

inline FlatValuePayload::OopHandle::OopHandle(const FlatValuePayload& payload, OopStorage* storage)
    : ValuePayload::OopHandle(payload, storage) {}

inline FlatValuePayload FlatValuePayload::OopHandle::operator()() const {
  return FlatValuePayload(container(), offset(), klass(), layout_kind());
}

inline FlatValuePayload::OopHandle FlatValuePayload::make_oop_handle(OopStorage* storage) const {
  return OopHandle(*this, storage);
}

inline FlatFieldPayload::Handle::Handle(const FlatFieldPayload& payload, JavaThread* thread)
    : FlatValuePayload::Handle(payload, thread) {}

inline FlatFieldPayload FlatFieldPayload::Handle::operator()() const {
  return FlatFieldPayload(container(), offset(), klass(), layout_kind());
}

inline instanceOop FlatFieldPayload::Handle::container() const {
  return instanceOop(ValuePayload::Handle::container());
}

inline FlatFieldPayload::Handle FlatFieldPayload::make_handle(JavaThread* thread) const {
  return Handle(*this, thread);
}

inline FlatFieldPayload::OopHandle::OopHandle(const FlatFieldPayload& payload, OopStorage* storage)
    : FlatValuePayload::OopHandle(payload, storage) {}

inline FlatFieldPayload FlatFieldPayload::OopHandle::operator()() const {
  return FlatFieldPayload(container(), offset(), klass(), layout_kind());
}

inline instanceOop FlatFieldPayload::OopHandle::container() const {
  return instanceOop(ValuePayload::OopHandle::container());
}

inline FlatFieldPayload::OopHandle FlatFieldPayload::make_oop_handle(OopStorage* storage) const {
  return OopHandle(*this, storage);
}

inline FlatArrayPayload::Handle::Handle(const FlatArrayPayload& payload, JavaThread* thread)
    : FlatValuePayload::Handle(payload, thread), _storage(payload._storage) {}

inline FlatArrayPayload FlatArrayPayload::Handle::operator()() const {
  return FlatArrayPayload(container(),
                          offset(),
                          klass(),
                          layout_kind(),
                          _storage._layout_helper,
                          _storage._element_size);
}

inline flatArrayOop FlatArrayPayload::Handle::container() const {
  return flatArrayOop(ValuePayload::Handle::container());
}

inline FlatArrayPayload::Handle FlatArrayPayload::make_handle(JavaThread* thread) const {
  return Handle(*this, thread);
}

inline FlatArrayPayload::OopHandle::OopHandle(const FlatArrayPayload& payload, OopStorage* storage)
    : FlatValuePayload::OopHandle(payload, storage),
      _storage(payload._storage) {}

inline FlatArrayPayload FlatArrayPayload::OopHandle::operator()() const {
  return FlatArrayPayload(container(),
                          offset(),
                          klass(),
                          layout_kind(),
                          _storage._layout_helper,
                          _storage._element_size);
}

inline flatArrayOop FlatArrayPayload::OopHandle::container() const {
  return flatArrayOop(ValuePayload::OopHandle::container());
}

inline FlatArrayPayload::OopHandle FlatArrayPayload::make_oop_handle(OopStorage* storage) const {
  return OopHandle(*this, storage);
}

#endif // SHARE_VM_OOPS_VALUEPAYLOAD_INLINE_HPP

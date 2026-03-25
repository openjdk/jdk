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

#ifndef SHARE_VM_OOPS_INLINEKLASS_HPP
#define SHARE_VM_OOPS_INLINEKLASS_HPP

#include "oops/inlineOop.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/layoutKind.hpp"
#include "oops/oopsHierarchy.hpp"
#include "oops/valuePayload.hpp"
#include "runtime/handles.hpp"
#include "utilities/exceptions.hpp"
#include "utilities/globalDefinitions.hpp"

template <typename T>
class Array;
class ClassFileParser;
template <typename T>
class GrowableArray;
class Method;
class RegisterMap;
class SigEntry;

// An InlineKlass is a specialized InstanceKlass for concrete value classes
// (abstract value classes are represented by InstanceKlass)

class InlineKlass: public InstanceKlass {
  friend class VMStructs;
  friend class InstanceKlass;

 public:
  static const KlassKind Kind = InlineKlassKind;

  // The member fields of the InlineKlass.
  //
  // All Klass objects have vtables starting at offset `sizeof(InstanceKlass)`.
  //
  // This has the effect that sub-klasses of InstanceKlass can't have their own
  // C++ fields, because those would overlap with the vtables (or some of the
  // other dynamically-sized sections).
  //
  // To work around this we stamp out the block members *after* all
  // dynamically-sized sections belonging to the InstanceKlass part of the
  // object.
  //
  // InlineKlass object layout:
  //   +-----------------------+
  //   | sizeof(InstanceKlass) |
  //   +-----------------------+ <= InstanceKlass:header_size()
  //   | vtable                |
  //   +-----------------------+
  //   | other sections        |
  //   +-----------------------+ <= end_of_instance_klass()
  //   | InlineKlass::Members  |
  //   +-----------------------+
  //
  class Members {
    friend class InlineKlass;

    // Addresses used for inline type calling convention
    Array<SigEntry>* _extended_sig;
    Array<VMRegPair>* _return_regs;

    address _pack_handler;
    address _pack_handler_jobject;
    address _unpack_handler;

    int _null_reset_value_offset;
    int _payload_offset;           // offset of the beginning of the payload in a heap buffered instance
    int _payload_size_in_bytes;    // size of payload layout
    int _payload_alignment;        // alignment required for payload
    int _null_free_non_atomic_size_in_bytes; // size of null-free non-atomic flat layout
    int _null_free_non_atomic_alignment;     // alignment requirement for null-free non-atomic layout
    int _null_free_atomic_size_in_bytes;     // size and alignment requirement for a null-free atomic layout, -1 if no atomic flat layout is possible
    int _nullable_atomic_size_in_bytes;      // size and alignment requirement for a nullable layout (always atomic), -1 if no nullable flat layout is possible
    int _nullable_non_atomic_size_in_bytes;  // size and alignment requirement for a nullable non-atomic layout, -1 if not available
    int _null_marker_offset;       // expressed as an offset from the beginning of the object for a heap buffered value
                                   // payload_offset must be subtracted to get the offset from the beginning of the payload

    Members();

    void print_on(outputStream* st) const;
  };

  InlineKlass();

 private:

  // Constructor
  InlineKlass(const ClassFileParser& parser);

  // Calculates where the members are supposed to be placed
  address calculate_members_address() const;

  Members& members() {
    assert(_adr_inline_klass_members != nullptr, "Should have been initialized");
    return *reinterpret_cast<Members*>(_adr_inline_klass_members);
  }

  inline const Members& members() const {
    InlineKlass* ik = const_cast<InlineKlass*>(this);
    return const_cast<const Members&>(ik->members());
  }

 public:

  bool is_empty_inline_type() const   { return _misc_flags.is_empty_inline_type(); }
  void set_is_empty_inline_type()     { _misc_flags.set_is_empty_inline_type(true); }

  // Members access functions

  const Array<SigEntry>* extended_sig() const                 {return members()._extended_sig; }
  void set_extended_sig(Array<SigEntry>* extended_sig)        { members()._extended_sig = extended_sig; }

  const Array<VMRegPair>* return_regs() const                 { return members()._return_regs; }
  void set_return_regs(Array<VMRegPair>* return_regs)         { members()._return_regs = return_regs; }

  // pack and unpack handlers for inline types return

  address pack_handler() const                                { return members()._pack_handler; }
  void set_pack_handler(address pack_handler)                 { members()._pack_handler = pack_handler; }

  address pack_handler_jobject() const                        { return members()._pack_handler_jobject; }
  void set_pack_handler_jobject(address pack_handler_jobject) { members()._pack_handler_jobject = pack_handler_jobject; }

  address unpack_handler() const                              { return members()._unpack_handler; }
  void set_unpack_handler(address unpack_handler)             { members()._unpack_handler = unpack_handler; }

  int null_reset_value_offset() const {
    int offset = members()._null_reset_value_offset;
    assert(offset != 0, "must not be called if not initialized");
    return offset;
  }
  void set_null_reset_value_offset(int offset)                { members()._null_reset_value_offset = offset; }

  int payload_offset() const {
    int offset = members()._payload_offset;
    assert(offset != 0, "Must be initialized before use");
    return offset;
  }
  void set_payload_offset(int offset)                         { members()._payload_offset = offset; }

  int payload_size_in_bytes() const                           { return members()._payload_size_in_bytes; }
  void set_payload_size_in_bytes(int payload_size)            { members()._payload_size_in_bytes = payload_size; }

  int payload_alignment() const                               { return members()._payload_alignment; }
  void set_payload_alignment(int alignment)                   { members()._payload_alignment = alignment; }

  int null_free_non_atomic_size_in_bytes() const              { return members()._null_free_non_atomic_size_in_bytes; }
  void set_null_free_non_atomic_size_in_bytes(int size)       { members()._null_free_non_atomic_size_in_bytes = size; }
  bool has_null_free_non_atomic_layout() const                { return null_free_non_atomic_size_in_bytes() != -1; }

  int null_free_non_atomic_alignment() const                  { return members()._null_free_non_atomic_alignment; }
  void set_null_free_non_atomic_alignment(int alignment)      { members()._null_free_non_atomic_alignment = alignment; }

  int null_free_atomic_size_in_bytes() const                  { return members()._null_free_atomic_size_in_bytes; }
  void set_null_free_atomic_size_in_bytes(int size)           { members()._null_free_atomic_size_in_bytes = size; }
  bool has_null_free_atomic_layout() const                    { return null_free_atomic_size_in_bytes() != -1; }

  int nullable_atomic_size_in_bytes() const                   { return members()._nullable_atomic_size_in_bytes; }
  void set_nullable_atomic_size_in_bytes(int size)            { members()._nullable_atomic_size_in_bytes = size; }
  bool has_nullable_atomic_layout() const                     { return nullable_atomic_size_in_bytes() != -1; }

  int nullable_non_atomic_size_in_bytes() const               { return members()._nullable_non_atomic_size_in_bytes; }
  void set_nullable_non_atomic_size_in_bytes(int size)        { members()._nullable_non_atomic_size_in_bytes = size; }
  bool has_nullable_non_atomic_layout() const                 { return nullable_non_atomic_size_in_bytes() != -1; }

  int null_marker_offset() const                              { return members()._null_marker_offset; }
  void set_null_marker_offset(int offset)                     { members()._null_marker_offset = offset; }
  int null_marker_offset_in_payload() const                   { return null_marker_offset() - payload_offset(); }

  bool supports_nullable_layouts() const {
    return has_nullable_non_atomic_layout() || has_nullable_atomic_layout();
  }

  jbyte* null_marker_address(address payload) {
    assert(supports_nullable_layouts(), " Must do");
    return (jbyte*)payload + null_marker_offset_in_payload();
  }

  bool is_payload_marked_as_null(address payload) {
    assert(supports_nullable_layouts(), " Must do");
    return *null_marker_address(payload) == 0;
  }

  void mark_payload_as_non_null(address payload) {
    assert(supports_nullable_layouts(), " Must do");
    *null_marker_address(payload) = 1;
  }

  void mark_payload_as_null(address payload) {
    assert(supports_nullable_layouts(), " Must do");
    *null_marker_address(payload) = 0;
  }

  inline bool layout_has_null_marker(LayoutKind lk) const;

  inline bool is_layout_supported(LayoutKind lk) const;

  inline int layout_alignment(LayoutKind kind) const;
  inline int layout_size_in_bytes(LayoutKind kind) const;

#if INCLUDE_CDS
  void remove_unshareable_info() override;
#endif

 private:
  int collect_fields(GrowableArray<SigEntry>* sig, int base_off = 0, int null_marker_offset = -1);

  void cleanup_blobs();

 public:
  // Type testing
  bool is_inline_klass_slow() const override { return true; }

  // Casting from Klass*

  static InlineKlass* cast(Klass* k) {
    return const_cast<InlineKlass*>(cast(const_cast<const Klass*>(k)));
  }

  static const InlineKlass* cast(const Klass* k) {
    assert(k != nullptr, "k should not be null");
    assert(k->is_inline_klass(), "cast to InlineKlass");
    return static_cast<const InlineKlass*>(k);
  }

  // Allocates a stand alone value in the Java heap
  // initialized to default value (cleared memory)
  inlineOop allocate_instance(TRAPS);

  address payload_addr(oop o) const;

  bool maybe_flat_in_array();
  bool is_always_flat_in_array();

  bool contains_oops() const { return nonstatic_oop_map_count() > 0; }
  int nonstatic_oop_count();

  // oop iterate raw inline type data pointer (where oop_addr may not be an oop, but backing/array-element)
  template <typename T, class OopClosureType>
  inline void oop_iterate_specialized(const address oop_addr, OopClosureType* closure);

  template <typename T, class OopClosureType>
  inline void oop_iterate_specialized_bounded(const address oop_addr, OopClosureType* closure, uintptr_t lo, uintptr_t hi);

  // calling convention support
  void initialize_calling_convention(TRAPS);

  bool can_be_passed_as_fields() const;
  bool can_be_returned_as_fields(bool init = false) const;
  void save_oop_fields(const RegisterMap& map, GrowableArray<Handle>& handles) const;
  void restore_oop_results(RegisterMap& map, GrowableArray<Handle>& handles) const;
  oop realloc_result(const RegisterMap& reg_map, const GrowableArray<Handle>& handles, TRAPS);
  static InlineKlass* returned_inline_klass(const RegisterMap& reg_map, bool* return_oop = nullptr, Method* method = nullptr);

  static ByteSize adr_members_offset() {
    return InstanceKlass::adr_inline_klass_members_offset();
  }

  // pack and unpack handlers. Need to be loadable from generated code
  // so at a fixed offset from the base of the klass pointer.
  static ByteSize pack_handler_offset() {
    return byte_offset_of(Members, _pack_handler);
  }

  static ByteSize pack_handler_jobject_offset() {
    return byte_offset_of(Members, _pack_handler_jobject);
  }

  static ByteSize unpack_handler_offset() {
    return byte_offset_of(Members, _unpack_handler);
  }

  static ByteSize null_reset_value_offset_offset() {
    return byte_offset_of(Members, _null_reset_value_offset);
  }

  static ByteSize payload_offset_offset() {
    return byte_offset_of(Members, _payload_offset);
  }

  static ByteSize null_marker_offset_offset() {
    return byte_offset_of(Members, _null_marker_offset);
  }

  oop null_reset_value() const;
  void set_null_reset_value(oop val);

  void deallocate_contents(ClassLoaderData* loader_data);
  static void cleanup(InlineKlass* ik) ;

  void print_on(outputStream* st) const override;

  // Verification
  void verify_on(outputStream* st) override;
  void oop_verify_on(oop obj, outputStream* st) override;
};

#endif // SHARE_VM_OOPS_INLINEKLASS_HPP

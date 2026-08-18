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

    // When we can't intrinsify the substitutability check, we can still avoid the call to isSubstitutable at runtime if the
    // value object is small enough.  If all the fields are contained at once in a single long, we can load such a long from
    // both operands, use a bitwise mask to remove the extra bits (from header, padding...), and compare these masked long.
    //
    // This doesn't always apply, for instance, if there are oops among the fields, we shouldn't carelessly load and compare:
    // the GC might move the object in between.
    // To signal this fast path cannot be done on this current class, simply put -1 in _fast_acmp_offset.
    //
    // We also should take care of not loading further than the object, even if it means reading part of the header.
    // For this reason, we can't use _payload_offset, but we need our special offset.
    // The offset doesn't need to be aligned on word boundary, or anything else.
    //
    int _fast_acmp_offset;    // if < 0, fast acmp doesn't apply
    int64_t _fast_acmp_mask;  // can be 0 for empty value classes

    // When we can't intrinsify the identityHashCode, we can still avoid the Java call at runtime if the value object is nice
    // enough. This fast path basically implements the method ValueObjectMethods::valueObjectHashCode in a special case. This
    // special case is when there is at most one no-oop segment in the acmp maps, that this segment (if it exists) is 1, 2, 4
    // or 8 byte long, and there is no oop in the acmp maps. Basically, valueObjectHashCode makes 0 or 1 iteration of the big
    // outer loop, and one iteration of one of the inner loops. The fast path loads a long at the given offset, isolates the
    // numeric value we are interested in, and does the arithmetic.
    //
    // There are cases:
    // 1. hashcode fast path doesn't apply: we set _fast_hashcode_offset < 0
    // 2. the object has no segments (i.e. it is empty): we set _fast_hashcode_offset = 0
    // 3. the object has one segment: we set _fast_hashcode_offset according to where we should load.
    //
    // Alike for the acmp fast path, we must not load further than the object, and we use the same trick as for acmp, and we
    // load possibly some part of the header. The cases 2. and 3. cannot collide since loading at offset 0 would read only the
    // header, and no payload.
    //
    // But unlike acmp, we need the actual arithmetic value, and resetting irrelevant bits is not correct. To do that, we need
    // to have a different logic wrt. endianness. Moreover, the fast path needs to handle differently when the segment is 8-byte
    // long, just as valueObjectHashCode does, while the arithmetic for segments of size 1, 2 or 4 is the same. This is also known
    // by a endianness-dependent test.
    int _fast_hashcode_offset;   // if < 0, fast hashcode doesn't apply

    // It turns out we need the same helping data for little and big endian at the moment. Yet, the logic is not quite the same.
    //
    // === LITTLE ENDIAN ===
    // In little endian, the memory layout, with a 4-byte segment whose value (as returned by getInt) would be 0x01 02 03 04. The
    // memory layout of the object would be something like:
    //                v- start of payload
    // ....header.... | 04 03 02 01
    //    \___________|___________/
    // Not to load too far, we load at offset "start of payload" - 4, so, we get some header bytes, and we get the long value
    // 0x01 02 03 04 HH HH HH HH, where HH are header bytes. To get the integer value, we can simply do an arithmetic right shift,
    // by 4 bytes (32 bits) in this case. By doing an arithmetic right shift, we conserve the mathematical value, even if we cut
    // higher bits (as long as we leave at least as much as the block we load).
    //
    // === BIG ENDIAN ===
    // In big endian, the memory layout, with a 4-byte segment whose value (as returned by getInt) would be 0x01 02 03 04. The
    // memory layout of the object would be something like:
    //                v- start of payload
    // ....header.... | 01 02 03 04
    //    \___________|___________/
    // Not to load too far, we load at offset "start of payload" - 4, so, we get some header bytes, and we get the long value
    // 0xHH HH HH HH 01 02 03 04, where HH are header bytes. To get the integer value, we can simply so a left shift, which
    // fills the lower bits with 0, followed by a arithmetic right shift, to preserve the mathematical value. The shift magnitude
    // is equal to the number of bits we need to discard. In this example, that is 32.
    //
    // === COMMON ===
    // This field is saying by how much we need to shift. Since we keep 1, 2, 4 or 8 bytes, the legal values of _fast_hashcode_shift
    // are 8 * (8 - (1, 2, 4, 8)) = 8 * (7, 6, 4, 0) = 56, 48, 32, 0.
    //
    // The fast path is aware we are loading a long if the shift is 0.
    // Value is not specified (and does not matter) if _fast_hashcode_offset <= 0
    int _fast_hashcode_shift;

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

  int fast_acmp_offset() const                                { return members()._fast_acmp_offset; }
  void set_fast_acmp_offset(int offset)                       { members()._fast_acmp_offset = offset; }

  int64_t fast_acmp_mask() const                              { return members()._fast_acmp_mask; }
  void set_fast_acmp_mask(int64_t mask)                       { members()._fast_acmp_mask = mask; }

  int fast_hashcode_offset() const                            { return members()._fast_hashcode_offset; }
  void set_fast_hashcode_offset(int offset)                   { members()._fast_hashcode_offset = offset; }

  int fast_hashcode_shift() const                             { return members()._fast_hashcode_shift; }
  void set_fast_hashcode_shift(int shift)                     { members()._fast_hashcode_shift = shift; }

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

  bool contains_oops() const { return nonstatic_oop_map_count() > 0; }
  int nonstatic_oop_count();

  // oop iterate the payload of a value object.
  //
  // * Function: void function(T* p)
  template <typename T, typename Function>
  inline void oop_iterate_value_payload_f(address payload, Function function);

  template <typename T, class OopClosureType>
  inline void oop_iterate_value_payload(address payload, OopClosureType* closure);

  template <typename T, class OopClosureType>
  inline void oop_iterate_value_payload_bounded(address payload, OopClosureType* closure, uintptr_t lo, uintptr_t hi);

  // Support for the scalarized calling convention
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

  static ByteSize fast_acmp_offset_offset() {
    return byte_offset_of(Members, _fast_acmp_offset);
  }

  static ByteSize fast_acmp_mask_offset() {
    return byte_offset_of(Members, _fast_acmp_mask);
  }

  static ByteSize fast_hashcode_offset_offset() {
    return byte_offset_of(Members, _fast_hashcode_offset);
  }

  static ByteSize fast_hashcode_shift_offset() {
    return byte_offset_of(Members, _fast_hashcode_shift);
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

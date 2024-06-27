/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_CODE_RELOCINFO_HPP
#define SHARE_CODE_RELOCINFO_HPP

#include "memory/allocation.hpp"
#include "oops/oopsHierarchy.hpp"
#include "runtime/osInfo.hpp"
#include "utilities/checkedCast.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

#include <new>

class CodeBlob;
class Metadata;
class NativeMovConstReg;
class nmethod;

// Types in this file:
//    relocInfo
//      One element of an array of halfwords encoding compressed relocations.
//      Also, the source of relocation types (relocInfo::oop_type, ...).
//    Relocation
//      A flyweight object representing a single relocation.
//      It is fully unpacked from the compressed relocation array.
//    metadata_Relocation, ... (subclasses of Relocation)
//      The location of some type-specific operations (metadata_addr, ...).
//      Also, the source of relocation specs (metadata_Relocation::spec, ...).
//    oop_Relocation, ... (subclasses of Relocation)
//      oops in the code stream (strings, class loaders)
//      Also, the source of relocation specs (oop_Relocation::spec, ...).
//    RelocationHolder
//      A value type which acts as a union holding a Relocation object.
//      Represents a relocation spec passed into a CodeBuffer during assembly.
//    RelocIterator
//      A StackObj which iterates over the relocations associated with
//      a range of code addresses.  Can be used to operate a copy of code.


// Notes on relocType:
//
// These hold enough information to read or write a value embedded in
// the instructions of an CodeBlob.  They're used to update:
//
//   1) embedded oops     (isOop()          == true)
//   2) inline caches     (isIC()           == true)
//   3) runtime calls     (isRuntimeCall()  == true)
//   4) internal word ref (isInternalWord() == true)
//   5) external word ref (isExternalWord() == true)
//
// when objects move (GC) or if code moves (compacting the code heap).
// They are also used to patch the code (if a call site must change)
//
// A relocInfo is represented in 16 bits:
//   4 bits indicating the relocation type
//  12 bits indicating the offset from the previous relocInfo address
//
// The offsets accumulate along the relocInfo stream to encode the
// address within the CodeBlob, which is named RelocIterator::addr().
// The address of a particular relocInfo always points to the first
// byte of the relevant instruction (and not to any of its subfields
// or embedded immediate constants).
//
// The offset value is scaled appropriately for the target machine.
// (See relocInfo_<arch>.hpp for the offset scaling.)
//
// On some machines, there may also be a "format" field which may provide
// additional information about the format of the instruction stream
// at the corresponding code address.  The format value is usually zero.
// Any machine (such as Intel) whose instructions can sometimes contain
// more than one relocatable constant needs format codes to distinguish
// which operand goes with a given relocation.
//
// If the target machine needs N format bits, the offset has 12-N bits,
// the format is encoded between the offset and the type, and the
// relocInfo_<arch>.hpp file has manifest constants for the format codes.
//
// If the type is "data_prefix_tag" then the offset bits are further encoded,
// and in fact represent not a code-stream offset but some inline data.
// The data takes the form of a counted sequence of halfwords, which
// precedes the actual relocation record.  (Clients never see it directly.)
// The interpretation of this extra data depends on the relocation type.
//
// On machines that have 32-bit immediate fields, there is usually
// little need for relocation "prefix" data, because the instruction stream
// is a perfectly reasonable place to store the value.  On machines in
// which 32-bit values must be "split" across instructions, the relocation
// data is the "true" specification of the value, which is then applied
// to some field of the instruction (22 or 13 bits, on SPARC).
//
// Whenever the location of the CodeBlob changes, any PC-relative
// relocations, and any internal_word_type relocations, must be reapplied.
// After the GC runs, oop_type relocations must be reapplied.
//
//
// Here are meanings of the types:
//
// relocInfo::none -- a filler record
//   Value:  none
//   Instruction: The corresponding code address is ignored
//   Data:  Any data prefix and format code are ignored
//   (This means that any relocInfo can be disabled by setting
//   its type to none.  See relocInfo::remove.)
//
// relocInfo::oop_type, relocInfo::metadata_type -- a reference to an oop or meta data
//   Value:  an oop, or else the address (handle) of an oop
//   Instruction types: memory (load), set (load address)
//   Data:  []       an oop stored in 4 bytes of instruction
//          [[N]n]   the index of an oop in the CodeBlob's oop pool
//
// relocInfo::internal_word_type -- an address within the same CodeBlob
// relocInfo::section_word_type -- same, but can refer to another section
//   Value:  an address in the CodeBlob's code or constants section
//   Instruction types: memory (load), set (load address)
//   Data:  []     stored in 4 bytes of instruction
//          [[L]l] a relative offset (see [About Offsets] below)
//   In the case of section_word_type, the offset is relative to a section
//   base address, and the section number (e.g., SECT_INSTS) is encoded
//   into the low two bits of the offset L.
//
// relocInfo::external_word_type -- a fixed address in the runtime system
//   Value:  an address
//   Instruction types: memory (load), set (load address)
//   Data:  []   stored in 4 bytes of instruction
//          [n]  the index of a "well-known" stub (usual case on RISC)
//          [Ll] a 32-bit address
//
// relocInfo::runtime_call_type -- a fixed subroutine in the runtime system
//   Value:  an address
//   Instruction types: PC-relative call (or a PC-relative branch)
//   Data:  []   stored in 4 bytes of instruction
//
// relocInfo::static_call_type -- a static call
//   Value:  an CodeBlob, a stub, or a fixup routine
//   Instruction types: a call
//   Data:  []
//   The identity of the callee is extracted from debugging information.
//   //%note reloc_3
//
// relocInfo::virtual_call_type -- a virtual call site (which includes an inline
//                                 cache)
//   Value:  an CodeBlob, a stub, the interpreter, or a fixup routine
//   Instruction types: a call, plus some associated set-oop instructions
//   Data:  []       the associated set-oops are adjacent to the call
//          [n]      n is a relative offset to the first set-oop
//          [[N]n l] and l is a limit within which the set-oops occur
//          [Nn Ll]  both n and l may be 32 bits if necessary
//   The identity of the callee is extracted from debugging information.
//
// relocInfo::opt_virtual_call_type -- a virtual call site that is statically bound
//
//    Same info as a static_call_type. We use a special type, so the handling of
//    virtuals and statics are separated.
//
//
//   The offset n points to the first set-oop.  (See [About Offsets] below.)
//   In turn, the set-oop instruction specifies or contains an oop cell devoted
//   exclusively to the IC call, which can be patched along with the call.
//
//   The locations of any other set-oops are found by searching the relocation
//   information starting at the first set-oop, and continuing until all
//   relocations up through l have been inspected.  The value l is another
//   relative offset.  (Both n and l are relative to the call's first byte.)
//
//   The limit l of the search is exclusive.  However, if it points within
//   the call (e.g., offset zero), it is adjusted to point after the call and
//   any associated machine-specific delay slot.
//
//   Since the offsets could be as wide as 32-bits, these conventions
//   put no restrictions whatever upon code reorganization.
//
//   The compiler is responsible for ensuring that transition from a clean
//   state to a monomorphic compiled state is MP-safe.  This implies that
//   the system must respond well to intermediate states where a random
//   subset of the set-oops has been correctly from the clean state
//   upon entry to the VEP of the compiled method.  In the case of a
//   machine (Intel) with a single set-oop instruction, the 32-bit
//   immediate field must not straddle a unit of memory coherence.
//   //%note reloc_3
//
// relocInfo::static_stub_type -- an extra stub for each static_call_type
//   Value:  none
//   Instruction types: a virtual call:  { set_oop; jump; }
//   Data:  [[N]n]  the offset of the associated static_call reloc
//   This stub becomes the target of a static call which must be upgraded
//   to a virtual call (because the callee is interpreted).
//   See [About Offsets] below.
//   //%note reloc_2
//
// relocInfo::poll_[return_]type -- a safepoint poll
//   Value:  none
//   Instruction types: memory load or test
//   Data:  none
//
// For example:
//
//   INSTRUCTIONS                        RELOC: TYPE    PREFIX DATA
//   ------------                               ----    -----------
// sethi      %hi(myObject),  R               oop_type [n(myObject)]
// ld      [R+%lo(myObject)+fldOffset], R2    oop_type [n(myObject) fldOffset]
// add R2, 1, R2
// st  R2, [R+%lo(myObject)+fldOffset]        oop_type [n(myObject) fldOffset]
//%note reloc_1
//
// This uses 4 instruction words, 8 relocation halfwords,
// and an entry (which is shareable) in the CodeBlob's oop pool,
// for a total of 36 bytes.
//
// Note that the compiler is responsible for ensuring the "fldOffset" when
// added to "%lo(myObject)" does not overflow the immediate fields of the
// memory instructions.
//
//
// [About Offsets] Relative offsets are supplied to this module as
// positive byte offsets, but they may be internally stored scaled
// and/or negated, depending on what is most compact for the target
// system.  Since the object pointed to by the offset typically
// precedes the relocation address, it is profitable to store
// these negative offsets as positive numbers, but this decision
// is internal to the relocation information abstractions.
//

class Relocation;
class CodeBuffer;
class CodeSection;
class RelocIterator;

class relocInfo {
  friend class RelocIterator;
 public:
  enum relocType {
    none                    =  0, // Used when no relocation should be generated
    oop_type                =  1, // embedded oop
    virtual_call_type       =  2, // a standard inline cache call for a virtual send
    opt_virtual_call_type   =  3, // a virtual call that has been statically bound (i.e., no IC cache)
    static_call_type        =  4, // a static send
    static_stub_type        =  5, // stub-entry for static send  (takes care of interpreter case)
    runtime_call_type       =  6, // call to fixed external routine
    external_word_type      =  7, // reference to fixed external address
    internal_word_type      =  8, // reference within the current code blob
    section_word_type       =  9, // internal, but a cross-section reference
    poll_type               = 10, // polling instruction for safepoints
    poll_return_type        = 11, // polling instruction for safepoints at return
    metadata_type           = 12, // metadata that used to be oops
    trampoline_stub_type    = 13, // stub-entry for trampoline
    runtime_call_w_cp_type  = 14, // Runtime call which may load its target from the constant pool
    data_prefix_tag         = 15, // tag for a prefix (carries data arguments)
    post_call_nop_type      = 16, // A tag for post call nop relocations
    entry_guard_type        = 17, // A tag for an nmethod entry barrier guard value
    barrier_type            = 18, // GC barrier data
    type_mask               = 31  // A mask which selects only the above values
  };

 private:
  unsigned short _value;

  static const enum class RawBitsToken {} RAW_BITS{};

  relocInfo(relocType type, RawBitsToken, int bits)
    : _value(checked_cast<unsigned short>((type << nontype_width) + bits)) { }

  static relocType check_relocType(relocType type) NOT_DEBUG({ return type; });

  static void check_offset_and_format(int offset, int format) NOT_DEBUG_RETURN;

  static int compute_bits(int offset, int format) {
    check_offset_and_format(offset, format);
    return (offset / offset_unit) + (format << offset_width);
  }

 public:
  relocInfo(relocType type, int offset, int format = 0)
    : relocInfo(check_relocType(type), RAW_BITS, compute_bits(offset, format)) {}

  #define APPLY_TO_RELOCATIONS(visitor) \
    visitor(oop) \
    visitor(metadata) \
    visitor(virtual_call) \
    visitor(opt_virtual_call) \
    visitor(static_call) \
    visitor(static_stub) \
    visitor(runtime_call) \
    visitor(runtime_call_w_cp) \
    visitor(external_word) \
    visitor(internal_word) \
    visitor(poll) \
    visitor(poll_return) \
    visitor(section_word) \
    visitor(trampoline_stub) \
    visitor(post_call_nop) \
    visitor(entry_guard) \
    visitor(barrier) \


 public:
  enum : unsigned short{
    value_width             = sizeof(unsigned short) * BitsPerByte,
    type_width              = 5,   // == log2(type_mask+1)
    nontype_width           = value_width - type_width,
    datalen_width           = nontype_width-1,
    datalen_tag             = 1 << datalen_width,  // or-ed into _value
    datalen_limit           = 1 << datalen_width,
    datalen_mask            = (1 << datalen_width)-1
  };

  // accessors
 public:
  relocType  type()       const { return (relocType)((unsigned)_value >> nontype_width); }
  int  format()           const { return format_mask==0? 0: format_mask &
                                         ((unsigned)_value >> offset_width); }
  int  addr_offset()      const { assert(!is_prefix(), "must have offset");
                                  return (_value & offset_mask)*offset_unit; }

 protected:
  const short* data()       const { assert(is_datalen(), "must have data");
                                    return (const short*)(this + 1); }
  unsigned short datalen()  const { assert(is_datalen(), "must have data");
                                  return (_value & datalen_mask); }
  unsigned short immediate() const { assert(is_immediate(), "must have immed");
                                  return (_value & datalen_mask); }
 public:
  static int addr_unit()        { return offset_unit; }
  static int offset_limit()     { return (1 << offset_width) * offset_unit; }

  void set_type(relocType type);

  void remove() { set_type(none); }

 protected:
  bool is_none()                const { return type() == none; }
  bool is_prefix()              const { return type() == data_prefix_tag; }
  bool is_datalen()             const { assert(is_prefix(), "must be prefix");
                                        return (_value & datalen_tag) != 0; }
  bool is_immediate()           const { assert(is_prefix(), "must be prefix");
                                        return (_value & datalen_tag) == 0; }

 public:
  // Occasionally records of type relocInfo::none will appear in the stream.
  // We do not bother to filter these out, but clients should ignore them.
  // These records serve as "filler" in three ways:
  //  - to skip large spans of unrelocated code (this is rare)
  //  - to pad out the relocInfo array to the required oop alignment
  //  - to disable old relocation information which is no longer applicable

  static relocInfo filler_info() {
    return relocInfo(relocInfo::none, relocInfo::offset_limit() - relocInfo::offset_unit);
  }

  // Every non-prefix relocation may be preceded by at most one prefix,
  // which supplies 1 or more halfwords of associated data.  Conventionally,
  // an int is represented by 0, 1, or 2 halfwords, depending on how
  // many bits are required to represent the value.  (In addition,
  // if the sole halfword is a 10-bit unsigned number, it is made
  // "immediate" in the prefix header word itself.  This optimization
  // is invisible outside this module.)

  static relocInfo prefix_info(int datalen = 0) {
    assert(relocInfo::fits_into_immediate(datalen), "datalen in limits");
    return relocInfo(relocInfo::data_prefix_tag, relocInfo::RAW_BITS, relocInfo::datalen_tag | datalen);
  }

 private:
  // an immediate relocInfo optimizes a prefix with one 10-bit unsigned value
  static relocInfo immediate_relocInfo(int data0) {
    assert(fits_into_immediate(data0), "data0 in limits");
    return relocInfo(relocInfo::data_prefix_tag, RAW_BITS, data0);
  }
  static bool fits_into_immediate(int data0) {
    return (data0 >= 0 && data0 < datalen_limit);
  }

 public:
  // Support routines for compilers.

  // This routine takes an infant relocInfo (unprefixed) and
  // edits in its prefix, if any.  It also updates dest.locs_end.
  void initialize(CodeSection* dest, Relocation* reloc);

  // This routine updates a prefix and returns the limit pointer.
  // It tries to compress the prefix from 32 to 16 bits, and if
  // successful returns a reduced "prefix_limit" pointer.
  relocInfo* finish_prefix(short* prefix_limit);

  // bit-packers for the data array:

  // As it happens, the bytes within the shorts are ordered natively,
  // but the shorts within the word are ordered big-endian.
  // This is an arbitrary choice, made this way mainly to ease debugging.
  static short data0_from_int(jint x)         { return (short)(x >> value_width); }
  static short data1_from_int(jint x)         { return (short)x; }
  static jint jint_from_data(short* data) {
    return (data[0] << value_width) + (unsigned short)data[1];
  }

  static jint short_data_at(int n, short* data, int datalen) {
    return datalen > n ? data[n] : 0;
  }

  static jint jint_data_at(int n, short* data, int datalen) {
    return datalen > n+1 ? jint_from_data(&data[n]) : short_data_at(n, data, datalen);
  }

  // Update methods for relocation information
  // (since code is dynamically patched, we also need to dynamically update the relocation info)
  // Both methods takes old_type, so it is able to perform sanity checks on the information removed.
  static void change_reloc_info_for_address(RelocIterator *itr, address pc, relocType old_type, relocType new_type);

  // Machine dependent stuff
#include CPU_HEADER(relocInfo)

 protected:
  // Derived constant, based on format_width which is PD:
  enum {
    offset_width       = nontype_width - format_width,
    offset_mask        = (1<<offset_width) - 1,
    format_mask        = (1<<format_width) - 1
  };
 public:
  enum {
#ifdef _LP64
    // for use in format
    // format_width must be at least 1 on _LP64
    narrow_oop_in_const = 1,
#endif
    // Conservatively large estimate of maximum length (in shorts)
    // of any relocation record.
    // Extended format is length prefix, data words, and tag/offset suffix.
    length_limit       = 1 + 1 + (3*BytesPerWord/BytesPerShort) + 1,
    have_format        = format_width > 0
  };
};

#define FORWARD_DECLARE_EACH_CLASS(name)              \
class name##_Relocation;
APPLY_TO_RELOCATIONS(FORWARD_DECLARE_EACH_CLASS)
#undef FORWARD_DECLARE_EACH_CLASS

// Holder for flyweight relocation objects.
// Although the flyweight subclasses are of varying sizes,
// the holder is "one size fits all".
class RelocationHolder {
  friend class Relocation;

 private:
  // A Relocation is "held" by placement constructing a Relocation into
  // _relocbuf. Hence, _relocbuf must accomodate all subclasses of
  // Relocation. We also need the Relocation base class to be at the same
  // address as the start of the object, e.g. at the address of _relocbuf.
  // Both of these requirements are checked (see emplace_relocation).
  // The placement of the base class subobject isn't guaranteed by C++, since
  // these aren't standard layout classes, but all supported implementations
  // provide that behavior.  If that changes, we can instead add a Relocation*
  // _reloc member to capture the result of the placement new, and use that to
  // access the base subobject.
  static const size_t _relocbuf_size = 5 * sizeof(void*);
  alignas(void*) char _relocbuf[_relocbuf_size];

  template<typename Reloc, typename... Args>
  void emplace_relocation(const Args&... args) {
    static_assert(std::is_base_of<Relocation, Reloc>::value, "not Relocation");
    static_assert(sizeof(Reloc) <= sizeof(_relocbuf), "_relocbuf too small");
    Relocation* reloc = ::new (_relocbuf) Reloc(args...);
    // Verify the base class subobject of the object constructed into
    // _relocbuf is at the same address as the derived object.
    assert(static_cast<const void*>(reloc) == _relocbuf, "invariant");
  }

  // Support for Relocation::copy_into.
  // reloc should be a most derived object.
  template<typename Reloc>
  void copy_into_impl(const Reloc& reloc) {
    emplace_relocation<Reloc>(reloc);
  }

  // Tag for selecting the constructor below and carrying the type of the
  // relocation object the new holder will (initially) contain.
  template<typename Reloc> struct Construct {};

  // Constructor used by construct().  Constructs a new holder containing a
  // relocation of type Reloc that is constructed using the provided args.
  template<typename Reloc, typename... Args>
  RelocationHolder(Construct<Reloc>, const Args&... args) {
    emplace_relocation<Reloc>(args...);
  }

 public:
  Relocation* reloc() const { return (Relocation*)_relocbuf; }
  inline relocInfo::relocType type() const;

  // Return a holder containing a relocation of type Reloc, constructed using args.
  template<typename Reloc, typename... Args>
  static RelocationHolder construct(const Args&... args) {
    return RelocationHolder(Construct<Reloc>(), args...);
  }

  RelocationHolder();           // Initializes type to none.

  // Depends on the destructor for all relocation types being trivial
  // (verified in .cpp file).
  ~RelocationHolder() = default;

  RelocationHolder(const RelocationHolder& from);
  RelocationHolder& operator=(const RelocationHolder& from);

  static const RelocationHolder none;
};

// A RelocIterator iterates through the relocation information of a CodeBlob.
// It provides access to successive relocations as it is advanced through a
// code stream.
// Usage:
//   RelocIterator iter(nm);
//   while (iter.next()) {
//     iter.reloc()->some_operation();
//   }
// or:
//   RelocIterator iter(nm);
//   while (iter.next()) {
//     switch (iter.type()) {
//      case relocInfo::oop_type          :
//      case relocInfo::ic_type           :
//      case relocInfo::prim_type         :
//      case relocInfo::uncommon_type     :
//      case relocInfo::runtime_call_type :
//      case relocInfo::internal_word_type:
//      case relocInfo::external_word_type:
//      ...
//     }
//   }

class RelocIterator : public StackObj {
  friend class section_word_Relocation; // for section verification
  enum { SECT_LIMIT = 3 };  // must be equal to CodeBuffer::SECT_LIMIT, checked in ctor
  friend class Relocation;
  friend class relocInfo;   // for change_reloc_info_for_address only
  typedef relocInfo::relocType relocType;

 private:
  address         _limit;   // stop producing relocations after this _addr
  relocInfo*      _current; // the current relocation information
  relocInfo*      _end;     // end marker; we're done iterating when _current == _end
  nmethod*        _code;    // compiled method containing _addr
  address         _addr;    // instruction to which the relocation applies
  short           _databuf; // spare buffer for compressed data
  short*          _data;    // pointer to the relocation's data
  short           _datalen; // number of halfwords in _data

  // Base addresses needed to compute targets of section_word_type relocs.
  address _section_start[SECT_LIMIT];
  address _section_end  [SECT_LIMIT];

  void set_has_current(bool b) {
    _datalen = !b ? -1 : 0;
    debug_only(_data = nullptr);
  }
  void set_current(relocInfo& ri) {
    _current = &ri;
    set_has_current(true);
  }

  RelocationHolder _rh; // where the current relocation is allocated

  relocInfo* current() const { assert(has_current(), "must have current");
                               return _current; }

  void set_limits(address begin, address limit);

  void advance_over_prefix();    // helper method

  void initialize_misc();

  void initialize(nmethod* nm, address begin, address limit);

  RelocIterator() { initialize_misc(); }

 public:
  // constructor
  RelocIterator(nmethod* nm, address begin = nullptr, address limit = nullptr);
  RelocIterator(CodeSection* cb, address begin = nullptr, address limit = nullptr);

  // get next reloc info, return !eos
  bool next() {
    _current++;
    assert(_current <= _end, "must not overrun relocInfo");
    if (_current == _end) {
      set_has_current(false);
      return false;
    }
    set_has_current(true);

    if (_current->is_prefix()) {
      advance_over_prefix();
      assert(!current()->is_prefix(), "only one prefix at a time");
    }

    _addr += _current->addr_offset();

    if (_limit != nullptr && _addr >= _limit) {
      set_has_current(false);
      return false;
    }

    return true;
  }

  // accessors
  address      limit()        const { return _limit; }
  relocType    type()         const { return current()->type(); }
  int          format()       const { return (relocInfo::have_format) ? current()->format() : 0; }
  address      addr()         const { return _addr; }
  nmethod*     code()         const { return _code; }
  short*       data()         const { return _data; }
  int          datalen()      const { return _datalen; }
  bool     has_current()      const { return _datalen >= 0; }
  bool   addr_in_const()      const;

  address section_start(int n) const {
    assert(_section_start[n], "must be initialized");
    return _section_start[n];
  }
  address section_end(int n) const {
    assert(_section_end[n], "must be initialized");
    return _section_end[n];
  }

  // The address points to the affected displacement part of the instruction.
  // For RISC, this is just the whole instruction.
  // For Intel, this is an unaligned 32-bit word.

  // type-specific relocation accessors:  oop_Relocation* oop_reloc(), etc.
  #define EACH_TYPE(name)                               \
  inline name##_Relocation* name##_reloc();
  APPLY_TO_RELOCATIONS(EACH_TYPE)
  #undef EACH_TYPE
  // generic relocation accessor; switches on type to call the above
  Relocation* reloc();

#ifndef PRODUCT
 public:
  void print();
  void print_current();
#endif
};


// A Relocation is a flyweight object allocated within a RelocationHolder.
// It represents the relocation data of relocation record.
// So, the RelocIterator unpacks relocInfos into Relocations.

class Relocation {
  friend class RelocIterator;

 private:
  // When a relocation has been created by a RelocIterator,
  // this field is non-null.  It allows the relocation to know
  // its context, such as the address to which it applies.
  RelocIterator* _binding;

  relocInfo::relocType _rtype;

 protected:
  RelocIterator* binding() const {
    assert(_binding != nullptr, "must be bound");
    return _binding;
  }
  void set_binding(RelocIterator* b) {
    assert(_binding == nullptr, "must be unbound");
    _binding = b;
    assert(_binding != nullptr, "must now be bound");
  }

  explicit Relocation(relocInfo::relocType rtype) : _binding(nullptr), _rtype(rtype) { }

  // Helper for copy_into functions for derived classes.
  // Forwards operation to RelocationHolder::copy_into_impl so that
  // RelocationHolder only needs to befriend this class, rather than all
  // derived classes that implement copy_into.
  template<typename Reloc>
  static void copy_into_helper(const Reloc& reloc, RelocationHolder& holder) {
    holder.copy_into_impl(reloc);
  }

 public:
  // make a generic relocation for a given type (if possible)
  static RelocationHolder spec_simple(relocInfo::relocType rtype);

  // here is the type-specific hook which writes relocation data:
  virtual void pack_data_to(CodeSection* dest) { }

  // here is the type-specific hook which reads (unpacks) relocation data:
  virtual void unpack_data() {
    assert(datalen()==0 || type()==relocInfo::none, "no data here");
  }

 protected:
  // Helper functions for pack_data_to() and unpack_data().

  // Most of the compression logic is confined here.
  // (The "immediate data" mechanism of relocInfo works independently
  // of this stuff, and acts to further compress most 1-word data prefixes.)

  // A variable-width int is encoded as a short if it will fit in 16 bits.
  // The decoder looks at datalen to decide whether to unpack short or jint.
  // Most relocation records are quite simple, containing at most two ints.

  static bool is_short(jint x) { return x == (short)x; }
  static short* add_short(short* p, short x)  { *p++ = x; return p; }
  static short* add_jint (short* p, jint x) {
    *p++ = relocInfo::data0_from_int(x); *p++ = relocInfo::data1_from_int(x);
    return p;
  }
  static short* add_var_int(short* p, jint x) {   // add a variable-width int
    if (is_short(x))  p = add_short(p, (short)x);
    else              p = add_jint (p, x);
    return p;
  }

  static short* pack_1_int_to(short* p, jint x0) {
    // Format is one of:  [] [x] [Xx]
    if (x0 != 0)  p = add_var_int(p, x0);
    return p;
  }
  int unpack_1_int() {
    assert(datalen() <= 2, "too much data");
    return relocInfo::jint_data_at(0, data(), datalen());
  }

  // With two ints, the short form is used only if both ints are short.
  short* pack_2_ints_to(short* p, jint x0, jint x1) {
    // Format is one of:  [] [x y?] [Xx Y?y]
    if (x0 == 0 && x1 == 0) {
      // no halfwords needed to store zeroes
    } else if (is_short(x0) && is_short(x1)) {
      // 1-2 halfwords needed to store shorts
      p = add_short(p, (short)x0); if (x1!=0) p = add_short(p, (short)x1);
    } else {
      // 3-4 halfwords needed to store jints
      p = add_jint(p, x0);             p = add_var_int(p, x1);
    }
    return p;
  }
  void unpack_2_ints(jint& x0, jint& x1) {
    int    dlen = datalen();
    short* dp  = data();
    if (dlen <= 2) {
      x0 = relocInfo::short_data_at(0, dp, dlen);
      x1 = relocInfo::short_data_at(1, dp, dlen);
    } else {
      assert(dlen <= 4, "too much data");
      x0 = relocInfo::jint_data_at(0, dp, dlen);
      x1 = relocInfo::jint_data_at(2, dp, dlen);
    }
  }

 protected:
  // platform-independent utility for patching constant section
  void       const_set_data_value    (address x);
  void       const_verify_data_value (address x);
  // platform-dependent utilities for decoding and patching instructions
  void       pd_set_data_value       (address x, bool verify_only = false); // a set or mem-ref
  void       pd_verify_data_value    (address x) { pd_set_data_value(x, true); }
  address    pd_call_destination     (address orig_addr = nullptr);
  void       pd_set_call_destination (address x);

  // this extracts the address of an address in the code stream instead of the reloc data
  address* pd_address_in_code       ();

  // this extracts an address from the code stream instead of the reloc data
  address  pd_get_address_from_code ();

  // these convert from byte offsets, to scaled offsets, to addresses
  static jint scaled_offset(address x, address base) {
    int byte_offset = checked_cast<int>(x - base);
    int offset = -byte_offset / relocInfo::addr_unit();
    assert(address_from_scaled_offset(offset, base) == x, "just checkin'");
    return offset;
  }
  static jint scaled_offset_null_special(address x, address base) {
    // Some relocations treat offset=0 as meaning nullptr.
    // Handle this extra convention carefully.
    if (x == nullptr)  return 0;
    assert(x != base, "offset must not be zero");
    return scaled_offset(x, base);
  }
  static address address_from_scaled_offset(jint offset, address base) {
    int byte_offset = -( offset * relocInfo::addr_unit() );
    return base + byte_offset;
  }

  // helpers for mapping between old and new addresses after a move or resize
  address old_addr_for(address newa, const CodeBuffer* src, CodeBuffer* dest);
  address new_addr_for(address olda, const CodeBuffer* src, CodeBuffer* dest);
  void normalize_address(address& addr, const CodeSection* dest, bool allow_other_sections = false);

 public:
  // accessors which only make sense for a bound Relocation
  address         addr()            const { return binding()->addr(); }
  nmethod*        code()            const { return binding()->code(); }
  bool            addr_in_const()   const { return binding()->addr_in_const(); }
 protected:
  short*   data()         const { return binding()->data(); }
  int      datalen()      const { return binding()->datalen(); }

 public:
  // Make a filler relocation.
  Relocation() : Relocation(relocInfo::none) {}

  // Intentionally public non-virtual destructor, even though polymorphic.  We
  // never heap allocate a Relocation, so never delete through a base pointer.
  // RelocationHolder depends on the destructor for all relocation types being
  // trivial, so this must not be virtual (and hence non-trivial).
  ~Relocation() = default;

  int      format()       const { return binding()->format(); }

  relocInfo::relocType type()              const { return _rtype; }

  // Copy this relocation into holder.
  virtual void copy_into(RelocationHolder& holder) const;

  // is it a call instruction?
  virtual bool is_call()                         { return false; }

  // is it a data movement instruction?
  virtual bool is_data()                         { return false; }

  // some relocations can compute their own values
  virtual address  value();

  // all relocations are able to reassert their values
  virtual void set_value(address x);

  virtual void clear_inline_cache() {}

  // This method assumes that all virtual/static (inline) caches are cleared (since for static_call_type and
  // ic_call_type is not always position dependent (depending on the state of the cache)). However, this is
  // probably a reasonable assumption, since empty caches simplifies code reloacation.
  virtual void fix_relocation_after_move(const CodeBuffer* src, CodeBuffer* dest) { }
};


// certain inlines must be deferred until class Relocation is defined:

inline RelocationHolder::RelocationHolder() :
  RelocationHolder(Construct<Relocation>())
{}

inline RelocationHolder::RelocationHolder(const RelocationHolder& from) {
  from.reloc()->copy_into(*this);
}

inline RelocationHolder& RelocationHolder::operator=(const RelocationHolder& from) {
  // All Relocation types are trivially destructible (verified in .cpp file),
  // so we don't need to destruct our old value before copying over it.
  // If not for that we would need to decide what to do about self-assignment.
  from.reloc()->copy_into(*this);
  return *this;
}

relocInfo::relocType RelocationHolder::type() const {
  return reloc()->type();
}

// A DataRelocation always points at a memory or load-constant instruction..
// It is absolute on most machines, and the constant is split on RISCs.
// The specific subtypes are oop, external_word, and internal_word.
class DataRelocation : public Relocation {
 public:
  DataRelocation(relocInfo::relocType type) : Relocation(type) {}

  bool    is_data() override { return true; }

  // target must be computed somehow from relocation data
  address value() override = 0;
  void    set_value(address x) override {
    if (addr_in_const()) {
      const_set_data_value(x);
    } else {
      pd_set_data_value(x);
    }
  }
  void    verify_value(address x) {
    if (addr_in_const()) {
      const_verify_data_value(x);
    } else {
      pd_verify_data_value(x);
    }
  }
};

class post_call_nop_Relocation : public Relocation {
  friend class RelocationHolder;

public:
  post_call_nop_Relocation() : Relocation(relocInfo::post_call_nop_type) { }

  static RelocationHolder spec() {
    return RelocationHolder::construct<post_call_nop_Relocation>();
  }

  void copy_into(RelocationHolder& holder) const override;
};

class entry_guard_Relocation : public Relocation {
  friend class RelocationHolder;

public:
  entry_guard_Relocation() : Relocation(relocInfo::entry_guard_type) { }

  static RelocationHolder spec() {
    return RelocationHolder::construct<entry_guard_Relocation>();
  }

  void copy_into(RelocationHolder& holder) const override;
};

// A CallRelocation always points at a call instruction.
// It is PC-relative on most machines.
class CallRelocation : public Relocation {
 public:
  CallRelocation(relocInfo::relocType type) : Relocation(type) { }

  bool is_call() override { return true; }

  address  destination()                    { return pd_call_destination(); }
  void     set_destination(address x); // pd_set_call_destination

  void     fix_relocation_after_move(const CodeBuffer* src, CodeBuffer* dest) override;
  address  value() override                 { return destination();  }
  void     set_value(address x) override    { set_destination(x); }
};

class oop_Relocation : public DataRelocation {
 public:
  // an oop in the CodeBlob's oop pool; encoded as [n] or [Nn]
  static RelocationHolder spec(int oop_index) {
    assert(oop_index > 0, "must be a pool-resident oop");
    return RelocationHolder::construct<oop_Relocation>(oop_index);
  }
  // an oop in the instruction stream; encoded as []
  static RelocationHolder spec_for_immediate() {
    // If no immediate oops are generated, we can skip some walks over nmethods.
    // Assert that they don't get generated accidentally!
    assert(relocInfo::mustIterateImmediateOopsInCode(),
           "Must return true so we will search for oops as roots etc. in the code.");
    const int oop_index = 0;
    return RelocationHolder::construct<oop_Relocation>(oop_index);
  }

  void copy_into(RelocationHolder& holder) const override;

 private:
  jint _oop_index;                  // if > 0, index into CodeBlob::oop_at

  oop_Relocation(int oop_index)
    : DataRelocation(relocInfo::oop_type), _oop_index(oop_index) { }

  friend class RelocationHolder;
  oop_Relocation() : DataRelocation(relocInfo::oop_type) {}

 public:
  int oop_index() { return _oop_index; }

  // oop_index is packed in "1_int" format:  [n] or [Nn]
  void pack_data_to(CodeSection* dest) override;
  void unpack_data() override;

  void fix_oop_relocation();        // reasserts oop value

  void verify_oop_relocation();

  address value() override { return *reinterpret_cast<address*>(oop_addr()); }

  bool oop_is_immediate()  { return oop_index() == 0; }

  oop* oop_addr();                  // addr or &pool[jint_data]
  oop  oop_value();                 // *oop_addr
  // Note:  oop_value transparently converts Universe::non_oop_word to nullptr.
};


// copy of oop_Relocation for now but may delete stuff in both/either
class metadata_Relocation : public DataRelocation {

 public:
  // an metadata in the CodeBlob's metadata pool; encoded as [n] or [Nn]
  static RelocationHolder spec(int metadata_index) {
    assert(metadata_index > 0, "must be a pool-resident metadata");
    return RelocationHolder::construct<metadata_Relocation>(metadata_index);
  }
  // an metadata in the instruction stream; encoded as []
  static RelocationHolder spec_for_immediate() {
    const int metadata_index = 0;
    return RelocationHolder::construct<metadata_Relocation>(metadata_index);
  }

  void copy_into(RelocationHolder& holder) const override;

 private:
  jint _metadata_index;            // if > 0, index into nmethod::metadata_at

  metadata_Relocation(int metadata_index)
    : DataRelocation(relocInfo::metadata_type), _metadata_index(metadata_index) { }

  friend class RelocationHolder;
  metadata_Relocation() : DataRelocation(relocInfo::metadata_type) { }

  // Fixes a Metadata pointer in the code. Most platforms embeds the
  // Metadata pointer in the code at compile time so this is empty
  // for them.
  void pd_fix_value(address x);

 public:
  int metadata_index() { return _metadata_index; }

  // metadata_index is packed in "1_int" format:  [n] or [Nn]
  void pack_data_to(CodeSection* dest) override;
  void unpack_data() override;

  void fix_metadata_relocation();        // reasserts metadata value

  address value() override { return (address) *metadata_addr(); }

  bool metadata_is_immediate()  { return metadata_index() == 0; }

  Metadata**   metadata_addr();                  // addr or &pool[jint_data]
  Metadata*    metadata_value();                 // *metadata_addr
  // Note:  metadata_value transparently converts Universe::non_metadata_word to nullptr.
};


class barrier_Relocation : public Relocation {

 public:
  // The uninitialized value used before the relocation has been patched.
  // Code assumes that the unpatched value is zero.
  static const int16_t unpatched = 0;

  static RelocationHolder spec() {
    return RelocationHolder::construct<barrier_Relocation>();
  }

  void copy_into(RelocationHolder& holder) const override;

 private:
  friend class RelocIterator;
  friend class RelocationHolder;
  barrier_Relocation() : Relocation(relocInfo::barrier_type) { }
};


class virtual_call_Relocation : public CallRelocation {

 public:
  // "cached_value" points to the first associated set-oop.
  // The oop_limit helps find the last associated set-oop.
  // (See comments at the top of this file.)
  static RelocationHolder spec(address cached_value, jint method_index = 0) {
    return RelocationHolder::construct<virtual_call_Relocation>(cached_value, method_index);
  }

  void copy_into(RelocationHolder& holder) const override;

 private:
  address _cached_value; // location of set-value instruction
  jint    _method_index; // resolved method for a Java call

  virtual_call_Relocation(address cached_value, int method_index)
    : CallRelocation(relocInfo::virtual_call_type),
      _cached_value(cached_value),
      _method_index(method_index) {
    assert(cached_value != nullptr, "first oop address must be specified");
  }

  friend class RelocationHolder;
  virtual_call_Relocation() : CallRelocation(relocInfo::virtual_call_type) { }

 public:
  address cached_value();

  int     method_index() { return _method_index; }
  Method* method_value();

  // data is packed as scaled offsets in "2_ints" format:  [f l] or [Ff Ll]
  // oop_limit is set to 0 if the limit falls somewhere within the call.
  // When unpacking, a zero oop_limit is taken to refer to the end of the call.
  // (This has the effect of bringing in the call's delay slot on SPARC.)
  void pack_data_to(CodeSection* dest) override;
  void unpack_data() override;

  void clear_inline_cache() override;
};


class opt_virtual_call_Relocation : public CallRelocation {
 public:
  static RelocationHolder spec(int method_index = 0) {
    return RelocationHolder::construct<opt_virtual_call_Relocation>(method_index);
  }

  void copy_into(RelocationHolder& holder) const override;

 private:
  jint _method_index; // resolved method for a Java call

  opt_virtual_call_Relocation(int method_index)
    : CallRelocation(relocInfo::opt_virtual_call_type),
      _method_index(method_index) { }

  friend class RelocationHolder;
  opt_virtual_call_Relocation() : CallRelocation(relocInfo::opt_virtual_call_type) {}

 public:
  int     method_index() { return _method_index; }
  Method* method_value();

  void pack_data_to(CodeSection* dest) override;
  void unpack_data() override;

  void clear_inline_cache() override;

  // find the matching static_stub
  address static_stub();
};


class static_call_Relocation : public CallRelocation {
 public:
  static RelocationHolder spec(int method_index = 0) {
    return RelocationHolder::construct<static_call_Relocation>(method_index);
  }

  void copy_into(RelocationHolder& holder) const override;

 private:
  jint _method_index; // resolved method for a Java call

  static_call_Relocation(int method_index)
    : CallRelocation(relocInfo::static_call_type),
    _method_index(method_index) { }

  friend class RelocationHolder;
  static_call_Relocation() : CallRelocation(relocInfo::static_call_type) {}

 public:
  int     method_index() { return _method_index; }
  Method* method_value();

  void pack_data_to(CodeSection* dest) override;
  void unpack_data() override;

  void clear_inline_cache() override;

  // find the matching static_stub
  address static_stub();
};

class static_stub_Relocation : public Relocation {
 public:
  static RelocationHolder spec(address static_call) {
    return RelocationHolder::construct<static_stub_Relocation>(static_call);
  }

  void copy_into(RelocationHolder& holder) const override;

 private:
  address _static_call;  // location of corresponding static_call

  static_stub_Relocation(address static_call)
    : Relocation(relocInfo::static_stub_type),
      _static_call(static_call) { }

  friend class RelocationHolder;
  static_stub_Relocation() : Relocation(relocInfo::static_stub_type) { }

 public:
  void clear_inline_cache() override;

  address static_call() { return _static_call; }

  // data is packed as a scaled offset in "1_int" format:  [c] or [Cc]
  void pack_data_to(CodeSection* dest) override;
  void unpack_data() override;
};

class runtime_call_Relocation : public CallRelocation {

 public:
  static RelocationHolder spec() {
    return RelocationHolder::construct<runtime_call_Relocation>();
  }

  void copy_into(RelocationHolder& holder) const override;

 private:
  friend class RelocationHolder;
  runtime_call_Relocation() : CallRelocation(relocInfo::runtime_call_type) { }
};


class runtime_call_w_cp_Relocation : public CallRelocation {
 public:
  static RelocationHolder spec() {
    return RelocationHolder::construct<runtime_call_w_cp_Relocation>();
  }

  void copy_into(RelocationHolder& holder) const override;

 private:
  friend class RelocationHolder;
  runtime_call_w_cp_Relocation()
    : CallRelocation(relocInfo::runtime_call_w_cp_type),
      _offset(-4) /* <0 = invalid */ { }

  // On z/Architecture, runtime calls are either a sequence
  // of two instructions (load destination of call from constant pool + do call)
  // or a pc-relative call. The pc-relative call is faster, but it can only
  // be used if the destination of the call is not too far away.
  // In order to be able to patch a pc-relative call back into one using
  // the constant pool, we have to remember the location of the call's destination
  // in the constant pool.
  int _offset;

 public:
  void set_constant_pool_offset(int offset) { _offset = offset; }
  int get_constant_pool_offset() { return _offset; }
  void pack_data_to(CodeSection * dest) override;
  void unpack_data() override;
};

// Trampoline Relocations.
// A trampoline allows to encode a small branch in the code, even if there
// is the chance that this branch can not reach all possible code locations.
// If the relocation finds that a branch is too far for the instruction
// in the code, it can patch it to jump to the trampoline where is
// sufficient space for a far branch. Needed on PPC.
class trampoline_stub_Relocation : public Relocation {
 public:
  static RelocationHolder spec(address static_call) {
    return RelocationHolder::construct<trampoline_stub_Relocation>(static_call);
  }

  void copy_into(RelocationHolder& holder) const override;

 private:
  address _owner;    // Address of the NativeCall that owns the trampoline.

  trampoline_stub_Relocation(address owner)
    : Relocation(relocInfo::trampoline_stub_type),
      _owner(owner) { }

  friend class RelocationHolder;
  trampoline_stub_Relocation() : Relocation(relocInfo::trampoline_stub_type) { }

 public:

  // Return the address of the NativeCall that owns the trampoline.
  address owner() { return _owner; }

  void pack_data_to(CodeSection * dest) override;
  void unpack_data() override;

  // Find the trampoline stub for a call.
  static address get_trampoline_for(address call, nmethod* code);
};

class external_word_Relocation : public DataRelocation {
 public:
  static RelocationHolder spec(address target) {
    assert(target != nullptr, "must not be null");
    return RelocationHolder::construct<external_word_Relocation>(target);
  }

  // Use this one where all 32/64 bits of the target live in the code stream.
  // The target must be an intptr_t, and must be absolute (not relative).
  static RelocationHolder spec_for_immediate() {
    return RelocationHolder::construct<external_word_Relocation>(nullptr);
  }

  void copy_into(RelocationHolder& holder) const override;

  // Some address looking values aren't safe to treat as relocations
  // and should just be treated as constants.
  static bool can_be_relocated(address target) {
    assert(target == nullptr || (uintptr_t)target >= (uintptr_t)OSInfo::vm_page_size(), INTPTR_FORMAT, (intptr_t)target);
    return target != nullptr;
  }

 private:
  address _target;                  // address in runtime

  external_word_Relocation(address target)
    : DataRelocation(relocInfo::external_word_type), _target(target) { }

  friend class RelocationHolder;
  external_word_Relocation() : DataRelocation(relocInfo::external_word_type) { }

 public:
  // data is packed as a well-known address in "1_int" format:  [a] or [Aa]
  // The function runtime_address_to_index is used to turn full addresses
  // to short indexes, if they are pre-registered by the stub mechanism.
  // If the "a" value is 0 (i.e., _target is nullptr), the address is stored
  // in the code stream.  See external_word_Relocation::target().
  void pack_data_to(CodeSection* dest) override;
  void unpack_data() override;

  void fix_relocation_after_move(const CodeBuffer* src, CodeBuffer* dest) override;
  address  target();        // if _target==nullptr, fetch addr from code stream
  address  value() override { return target(); }
};

class internal_word_Relocation : public DataRelocation {

 public:
  static RelocationHolder spec(address target) {
    assert(target != nullptr, "must not be null");
    return RelocationHolder::construct<internal_word_Relocation>(target);
  }

  // use this one where all the bits of the target can fit in the code stream:
  static RelocationHolder spec_for_immediate() {
    return RelocationHolder::construct<internal_word_Relocation>(nullptr);
  }

  void copy_into(RelocationHolder& holder) const override;

  // default section -1 means self-relative
  internal_word_Relocation(address target, int section = -1,
    relocInfo::relocType type = relocInfo::internal_word_type)
    : DataRelocation(type), _target(target), _section(section) { }

 protected:
  address _target;                  // address in CodeBlob
  int     _section;                 // section providing base address, if any

  friend class RelocationHolder;
  internal_word_Relocation(relocInfo::relocType type = relocInfo::internal_word_type)
    : DataRelocation(type) { }

  // bit-width of LSB field in packed offset, if section >= 0
  enum { section_width = 2 }; // must equal CodeBuffer::sect_bits

 public:
  // data is packed as a scaled offset in "1_int" format:  [o] or [Oo]
  // If the "o" value is 0 (i.e., _target is nullptr), the offset is stored
  // in the code stream.  See internal_word_Relocation::target().
  // If _section is not -1, it is appended to the low bits of the offset.
  void pack_data_to(CodeSection* dest) override;
  void unpack_data() override;

  void fix_relocation_after_move(const CodeBuffer* src, CodeBuffer* dest) override;
  address  target();        // if _target==nullptr, fetch addr from code stream
  int      section()        { return _section;   }
  address  value() override { return target();   }
};

class section_word_Relocation : public internal_word_Relocation {
 public:
  static RelocationHolder spec(address target, int section) {
    return RelocationHolder::construct<section_word_Relocation>(target, section);
  }

  void copy_into(RelocationHolder& holder) const override;

  section_word_Relocation(address target, int section)
    : internal_word_Relocation(target, section, relocInfo::section_word_type) {
    assert(target != nullptr, "must not be null");
    assert(section >= 0 && section < RelocIterator::SECT_LIMIT, "must be a valid section");
  }

  //void pack_data_to -- inherited
  void unpack_data() override;

 private:
  friend class RelocationHolder;
  section_word_Relocation() : internal_word_Relocation(relocInfo::section_word_type) { }
};


class poll_Relocation : public Relocation {
  bool is_data() override { return true; }
  void fix_relocation_after_move(const CodeBuffer* src, CodeBuffer* dest) override;
 public:
  poll_Relocation(relocInfo::relocType type = relocInfo::poll_type) : Relocation(type) { }

  void copy_into(RelocationHolder& holder) const override;
};

class poll_return_Relocation : public poll_Relocation {
 public:
  poll_return_Relocation() : poll_Relocation(relocInfo::relocInfo::poll_return_type) { }

  void copy_into(RelocationHolder& holder) const override;
};

// We know all the xxx_Relocation classes, so now we can define these:
#define EACH_CASE_AUX(Accessor, Reloc)                                  \
inline Reloc* RelocIterator::Accessor() {                               \
  static const RelocationHolder proto = RelocationHolder::construct<Reloc>(); \
  assert(type() == proto.type(), "type must agree");                    \
  _rh = proto;                                                          \
  Reloc* r = static_cast<Reloc*>(_rh.reloc());                          \
  r->set_binding(this);                                                 \
  r->Reloc::unpack_data();                                              \
  return r;                                                             \
}
#define EACH_CASE(name) \
  EACH_CASE_AUX(PASTE_TOKENS(name, _reloc), PASTE_TOKENS(name, _Relocation))
APPLY_TO_RELOCATIONS(EACH_CASE);
#undef EACH_CASE_AUX
#undef EACH_CASE

inline RelocIterator::RelocIterator(nmethod* nm, address begin, address limit) {
  initialize(nm, begin, limit);
}

#endif // SHARE_CODE_RELOCINFO_HPP

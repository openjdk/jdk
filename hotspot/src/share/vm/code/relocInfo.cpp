/*
 * Copyright (c) 1997, 2007, Oracle and/or its affiliates. All rights reserved.
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

# include "incls/_precompiled.incl"
# include "incls/_relocInfo.cpp.incl"


const RelocationHolder RelocationHolder::none; // its type is relocInfo::none


// Implementation of relocInfo

#ifdef ASSERT
relocInfo::relocInfo(relocType t, int off, int f) {
  assert(t != data_prefix_tag, "cannot build a prefix this way");
  assert((t & type_mask) == t, "wrong type");
  assert((f & format_mask) == f, "wrong format");
  assert(off >= 0 && off < offset_limit(), "offset out off bounds");
  assert((off & (offset_unit-1)) == 0, "misaligned offset");
  (*this) = relocInfo(t, RAW_BITS, off, f);
}
#endif

void relocInfo::initialize(CodeSection* dest, Relocation* reloc) {
  relocInfo* data = this+1;  // here's where the data might go
  dest->set_locs_end(data);  // sync end: the next call may read dest.locs_end
  reloc->pack_data_to(dest); // maybe write data into locs, advancing locs_end
  relocInfo* data_limit = dest->locs_end();
  if (data_limit > data) {
    relocInfo suffix = (*this);
    data_limit = this->finish_prefix((short*) data_limit);
    // Finish up with the suffix.  (Hack note: pack_data_to might edit this.)
    *data_limit = suffix;
    dest->set_locs_end(data_limit+1);
  }
}

relocInfo* relocInfo::finish_prefix(short* prefix_limit) {
  assert(sizeof(relocInfo) == sizeof(short), "change this code");
  short* p = (short*)(this+1);
  assert(prefix_limit >= p, "must be a valid span of data");
  int plen = prefix_limit - p;
  if (plen == 0) {
    debug_only(_value = 0xFFFF);
    return this;                         // no data: remove self completely
  }
  if (plen == 1 && fits_into_immediate(p[0])) {
    (*this) = immediate_relocInfo(p[0]); // move data inside self
    return this+1;
  }
  // cannot compact, so just update the count and return the limit pointer
  (*this) = prefix_relocInfo(plen);   // write new datalen
  assert(data() + datalen() == prefix_limit, "pointers must line up");
  return (relocInfo*)prefix_limit;
}


void relocInfo::set_type(relocType t) {
  int old_offset = addr_offset();
  int old_format = format();
  (*this) = relocInfo(t, old_offset, old_format);
  assert(type()==(int)t, "sanity check");
  assert(addr_offset()==old_offset, "sanity check");
  assert(format()==old_format, "sanity check");
}


void relocInfo::set_format(int f) {
  int old_offset = addr_offset();
  assert((f & format_mask) == f, "wrong format");
  _value = (_value & ~(format_mask << offset_width)) | (f << offset_width);
  assert(addr_offset()==old_offset, "sanity check");
}


void relocInfo::change_reloc_info_for_address(RelocIterator *itr, address pc, relocType old_type, relocType new_type) {
  bool found = false;
  while (itr->next() && !found) {
    if (itr->addr() == pc) {
      assert(itr->type()==old_type, "wrong relocInfo type found");
      itr->current()->set_type(new_type);
      found=true;
    }
  }
  assert(found, "no relocInfo found for pc");
}


void relocInfo::remove_reloc_info_for_address(RelocIterator *itr, address pc, relocType old_type) {
  change_reloc_info_for_address(itr, pc, old_type, none);
}


// ----------------------------------------------------------------------------------------------------
// Implementation of RelocIterator

void RelocIterator::initialize(CodeBlob* cb, address begin, address limit) {
  initialize_misc();

  if (cb == NULL && begin != NULL) {
    // allow CodeBlob to be deduced from beginning address
    cb = CodeCache::find_blob(begin);
  }
  assert(cb != NULL, "must be able to deduce nmethod from other arguments");

  _code    = cb;
  _current = cb->relocation_begin()-1;
  _end     = cb->relocation_end();
  _addr    = (address) cb->instructions_begin();

  assert(!has_current(), "just checking");
  address code_end = cb->instructions_end();

  assert(begin == NULL || begin >= cb->instructions_begin(), "in bounds");
 // FIX THIS  assert(limit == NULL || limit <= code_end,     "in bounds");
  set_limits(begin, limit);
}


RelocIterator::RelocIterator(CodeSection* cs, address begin, address limit) {
  initialize_misc();

  _current = cs->locs_start()-1;
  _end     = cs->locs_end();
  _addr    = cs->start();
  _code    = NULL; // Not cb->blob();

  CodeBuffer* cb = cs->outer();
  assert((int)SECT_LIMIT == CodeBuffer::SECT_LIMIT, "my copy must be equal");
  for (int n = 0; n < (int)SECT_LIMIT; n++) {
    _section_start[n] = cb->code_section(n)->start();
  }

  assert(!has_current(), "just checking");

  assert(begin == NULL || begin >= cs->start(), "in bounds");
  assert(limit == NULL || limit <= cs->end(),   "in bounds");
  set_limits(begin, limit);
}


enum { indexCardSize = 128 };
struct RelocIndexEntry {
  jint addr_offset;          // offset from header_end of an addr()
  jint reloc_offset;         // offset from header_end of a relocInfo (prefix)
};


static inline int num_cards(int code_size) {
  return (code_size-1) / indexCardSize;
}


int RelocIterator::locs_and_index_size(int code_size, int locs_size) {
  if (!UseRelocIndex)  return locs_size;   // no index
  code_size = round_to(code_size, oopSize);
  locs_size = round_to(locs_size, oopSize);
  int index_size = num_cards(code_size) * sizeof(RelocIndexEntry);
  // format of indexed relocs:
  //   relocation_begin:   relocInfo ...
  //   index:              (addr,reloc#) ...
  //                       indexSize           :relocation_end
  return locs_size + index_size + BytesPerInt;
}


void RelocIterator::create_index(relocInfo* dest_begin, int dest_count, relocInfo* dest_end) {
  address relocation_begin = (address)dest_begin;
  address relocation_end   = (address)dest_end;
  int     total_size       = relocation_end - relocation_begin;
  int     locs_size        = dest_count * sizeof(relocInfo);
  if (!UseRelocIndex) {
    Copy::fill_to_bytes(relocation_begin + locs_size, total_size-locs_size, 0);
    return;
  }
  int     index_size       = total_size - locs_size - BytesPerInt;      // find out how much space is left
  int     ncards           = index_size / sizeof(RelocIndexEntry);
  assert(total_size == locs_size + index_size + BytesPerInt, "checkin'");
  assert(index_size >= 0 && index_size % sizeof(RelocIndexEntry) == 0, "checkin'");
  jint*   index_size_addr  = (jint*)relocation_end - 1;

  assert(sizeof(jint) == BytesPerInt, "change this code");

  *index_size_addr = index_size;
  if (index_size != 0) {
    assert(index_size > 0, "checkin'");

    RelocIndexEntry* index = (RelocIndexEntry *)(relocation_begin + locs_size);
    assert(index == (RelocIndexEntry*)index_size_addr - ncards, "checkin'");

    // walk over the relocations, and fill in index entries as we go
    RelocIterator iter;
    const address    initial_addr    = NULL;
    relocInfo* const initial_current = dest_begin - 1;  // biased by -1 like elsewhere

    iter._code    = NULL;
    iter._addr    = initial_addr;
    iter._limit   = (address)(intptr_t)(ncards * indexCardSize);
    iter._current = initial_current;
    iter._end     = dest_begin + dest_count;

    int i = 0;
    address next_card_addr = (address)indexCardSize;
    int addr_offset = 0;
    int reloc_offset = 0;
    while (true) {
      // Checkpoint the iterator before advancing it.
      addr_offset  = iter._addr    - initial_addr;
      reloc_offset = iter._current - initial_current;
      if (!iter.next())  break;
      while (iter.addr() >= next_card_addr) {
        index[i].addr_offset  = addr_offset;
        index[i].reloc_offset = reloc_offset;
        i++;
        next_card_addr += indexCardSize;
      }
    }
    while (i < ncards) {
      index[i].addr_offset  = addr_offset;
      index[i].reloc_offset = reloc_offset;
      i++;
    }
  }
}


void RelocIterator::set_limits(address begin, address limit) {
  int index_size = 0;
  if (UseRelocIndex && _code != NULL) {
    index_size = ((jint*)_end)[-1];
    _end = (relocInfo*)( (address)_end - index_size - BytesPerInt );
  }

  _limit = limit;

  // the limit affects this next stuff:
  if (begin != NULL) {
#ifdef ASSERT
    // In ASSERT mode we do not actually use the index, but simply
    // check that its contents would have led us to the right answer.
    address addrCheck = _addr;
    relocInfo* infoCheck = _current;
#endif // ASSERT
    if (index_size > 0) {
      // skip ahead
      RelocIndexEntry* index       = (RelocIndexEntry*)_end;
      RelocIndexEntry* index_limit = (RelocIndexEntry*)((address)index + index_size);
      assert(_addr == _code->instructions_begin(), "_addr must be unadjusted");
      int card = (begin - _addr) / indexCardSize;
      if (card > 0) {
        if (index+card-1 < index_limit)  index += card-1;
        else                             index = index_limit - 1;
#ifdef ASSERT
        addrCheck = _addr    + index->addr_offset;
        infoCheck = _current + index->reloc_offset;
#else
        // Advance the iterator immediately to the last valid state
        // for the previous card.  Calling "next" will then advance
        // it to the first item on the required card.
        _addr    += index->addr_offset;
        _current += index->reloc_offset;
#endif // ASSERT
      }
    }

    relocInfo* backup;
    address    backup_addr;
    while (true) {
      backup      = _current;
      backup_addr = _addr;
#ifdef ASSERT
      if (backup == infoCheck) {
        assert(backup_addr == addrCheck, "must match"); addrCheck = NULL; infoCheck = NULL;
      } else {
        assert(addrCheck == NULL || backup_addr <= addrCheck, "must not pass addrCheck");
      }
#endif // ASSERT
      if (!next() || addr() >= begin) break;
    }
    assert(addrCheck == NULL || addrCheck == backup_addr, "must have matched addrCheck");
    assert(infoCheck == NULL || infoCheck == backup,      "must have matched infoCheck");
    // At this point, either we are at the first matching record,
    // or else there is no such record, and !has_current().
    // In either case, revert to the immediatly preceding state.
    _current = backup;
    _addr    = backup_addr;
    set_has_current(false);
  }
}


void RelocIterator::set_limit(address limit) {
  address code_end = (address)code() + code()->size();
  assert(limit == NULL || limit <= code_end, "in bounds");
  _limit = limit;
}


void PatchingRelocIterator:: prepass() {
  // turn breakpoints off during patching
  _init_state = (*this);        // save cursor
  while (next()) {
    if (type() == relocInfo::breakpoint_type) {
      breakpoint_reloc()->set_active(false);
    }
  }
  (RelocIterator&)(*this) = _init_state;        // reset cursor for client
}


void PatchingRelocIterator:: postpass() {
  // turn breakpoints back on after patching
  (RelocIterator&)(*this) = _init_state;        // reset cursor again
  while (next()) {
    if (type() == relocInfo::breakpoint_type) {
      breakpoint_Relocation* bpt = breakpoint_reloc();
      bpt->set_active(bpt->enabled());
    }
  }
}


// All the strange bit-encodings are in here.
// The idea is to encode relocation data which are small integers
// very efficiently (a single extra halfword).  Larger chunks of
// relocation data need a halfword header to hold their size.
void RelocIterator::advance_over_prefix() {
  if (_current->is_datalen()) {
    _data    = (short*) _current->data();
    _datalen =          _current->datalen();
    _current += _datalen + 1;   // skip the embedded data & header
  } else {
    _databuf = _current->immediate();
    _data = &_databuf;
    _datalen = 1;
    _current++;                 // skip the header
  }
  // The client will see the following relocInfo, whatever that is.
  // It is the reloc to which the preceding data applies.
}


address RelocIterator::compute_section_start(int n) const {
// This routine not only computes a section start, but also
// memoizes it for later.
#define CACHE ((RelocIterator*)this)->_section_start[n]
  CodeBlob* cb = code();
  guarantee(cb != NULL, "must have a code blob");
  if (n == CodeBuffer::SECT_INSTS)
    return CACHE = cb->instructions_begin();
  assert(cb->is_nmethod(), "only nmethods have these sections");
  nmethod* nm = (nmethod*) cb;
  address res = NULL;
  switch (n) {
  case CodeBuffer::SECT_STUBS:
    res = nm->stub_begin();
    break;
  case CodeBuffer::SECT_CONSTS:
    res = nm->consts_begin();
    break;
  default:
    ShouldNotReachHere();
  }
  assert(nm->contains(res) || res == nm->instructions_end(), "tame pointer");
  CACHE = res;
  return res;
#undef CACHE
}


Relocation* RelocIterator::reloc() {
  // (take the "switch" out-of-line)
  relocInfo::relocType t = type();
  if (false) {}
  #define EACH_TYPE(name)                             \
  else if (t == relocInfo::name##_type) {             \
    return name##_reloc();                            \
  }
  APPLY_TO_RELOCATIONS(EACH_TYPE);
  #undef EACH_TYPE
  assert(t == relocInfo::none, "must be padding");
  return new(_rh) Relocation();
}


//////// Methods for flyweight Relocation types


RelocationHolder RelocationHolder::plus(int offset) const {
  if (offset != 0) {
    switch (type()) {
    case relocInfo::none:
      break;
    case relocInfo::oop_type:
      {
        oop_Relocation* r = (oop_Relocation*)reloc();
        return oop_Relocation::spec(r->oop_index(), r->offset() + offset);
      }
    default:
      ShouldNotReachHere();
    }
  }
  return (*this);
}


void Relocation::guarantee_size() {
  guarantee(false, "Make _relocbuf bigger!");
}

    // some relocations can compute their own values
address Relocation::value() {
  ShouldNotReachHere();
  return NULL;
}


void Relocation::set_value(address x) {
  ShouldNotReachHere();
}


RelocationHolder Relocation::spec_simple(relocInfo::relocType rtype) {
  if (rtype == relocInfo::none)  return RelocationHolder::none;
  relocInfo ri = relocInfo(rtype, 0);
  RelocIterator itr;
  itr.set_current(ri);
  itr.reloc();
  return itr._rh;
}


static inline bool is_index(intptr_t index) {
  return 0 < index && index < os::vm_page_size();
}


int32_t Relocation::runtime_address_to_index(address runtime_address) {
  assert(!is_index((intptr_t)runtime_address), "must not look like an index");

  if (runtime_address == NULL)  return 0;

  StubCodeDesc* p = StubCodeDesc::desc_for(runtime_address);
  if (p != NULL && p->begin() == runtime_address) {
    assert(is_index(p->index()), "there must not be too many stubs");
    return (int32_t)p->index();
  } else {
    // Known "miscellaneous" non-stub pointers:
    // os::get_polling_page(), SafepointSynchronize::address_of_state()
    if (PrintRelocations) {
      tty->print_cr("random unregistered address in relocInfo: " INTPTR_FORMAT, runtime_address);
    }
#ifndef _LP64
    return (int32_t) (intptr_t)runtime_address;
#else
    // didn't fit return non-index
    return -1;
#endif /* _LP64 */
  }
}


address Relocation::index_to_runtime_address(int32_t index) {
  if (index == 0)  return NULL;

  if (is_index(index)) {
    StubCodeDesc* p = StubCodeDesc::desc_for_index(index);
    assert(p != NULL, "there must be a stub for this index");
    return p->begin();
  } else {
#ifndef _LP64
    // this only works on 32bit machines
    return (address) ((intptr_t) index);
#else
    fatal("Relocation::index_to_runtime_address, int32_t not pointer sized");
    return NULL;
#endif /* _LP64 */
  }
}

address Relocation::old_addr_for(address newa,
                                 const CodeBuffer* src, CodeBuffer* dest) {
  int sect = dest->section_index_of(newa);
  guarantee(sect != CodeBuffer::SECT_NONE, "lost track of this address");
  address ostart = src->code_section(sect)->start();
  address nstart = dest->code_section(sect)->start();
  return ostart + (newa - nstart);
}

address Relocation::new_addr_for(address olda,
                                 const CodeBuffer* src, CodeBuffer* dest) {
  debug_only(const CodeBuffer* src0 = src);
  int sect = CodeBuffer::SECT_NONE;
  // Look for olda in the source buffer, and all previous incarnations
  // if the source buffer has been expanded.
  for (; src != NULL; src = src->before_expand()) {
    sect = src->section_index_of(olda);
    if (sect != CodeBuffer::SECT_NONE)  break;
  }
  guarantee(sect != CodeBuffer::SECT_NONE, "lost track of this address");
  address ostart = src->code_section(sect)->start();
  address nstart = dest->code_section(sect)->start();
  return nstart + (olda - ostart);
}

void Relocation::normalize_address(address& addr, const CodeSection* dest, bool allow_other_sections) {
  address addr0 = addr;
  if (addr0 == NULL || dest->allocates2(addr0))  return;
  CodeBuffer* cb = dest->outer();
  addr = new_addr_for(addr0, cb, cb);
  assert(allow_other_sections || dest->contains2(addr),
         "addr must be in required section");
}


void CallRelocation::set_destination(address x) {
  pd_set_call_destination(x);
}

void CallRelocation::fix_relocation_after_move(const CodeBuffer* src, CodeBuffer* dest) {
  // Usually a self-relative reference to an external routine.
  // On some platforms, the reference is absolute (not self-relative).
  // The enhanced use of pd_call_destination sorts this all out.
  address orig_addr = old_addr_for(addr(), src, dest);
  address callee    = pd_call_destination(orig_addr);
  // Reassert the callee address, this time in the new copy of the code.
  pd_set_call_destination(callee);
}


//// pack/unpack methods

void oop_Relocation::pack_data_to(CodeSection* dest) {
  short* p = (short*) dest->locs_end();
  p = pack_2_ints_to(p, _oop_index, _offset);
  dest->set_locs_end((relocInfo*) p);
}


void oop_Relocation::unpack_data() {
  unpack_2_ints(_oop_index, _offset);
}


void virtual_call_Relocation::pack_data_to(CodeSection* dest) {
  short*  p     = (short*) dest->locs_end();
  address point =          dest->locs_point();

  // Try to make a pointer NULL first.
  if (_oop_limit >= point &&
      _oop_limit <= point + NativeCall::instruction_size) {
    _oop_limit = NULL;
  }
  // If the _oop_limit is NULL, it "defaults" to the end of the call.
  // See ic_call_Relocation::oop_limit() below.

  normalize_address(_first_oop, dest);
  normalize_address(_oop_limit, dest);
  jint x0 = scaled_offset_null_special(_first_oop, point);
  jint x1 = scaled_offset_null_special(_oop_limit, point);
  p = pack_2_ints_to(p, x0, x1);
  dest->set_locs_end((relocInfo*) p);
}


void virtual_call_Relocation::unpack_data() {
  jint x0, x1; unpack_2_ints(x0, x1);
  address point = addr();
  _first_oop = x0==0? NULL: address_from_scaled_offset(x0, point);
  _oop_limit = x1==0? NULL: address_from_scaled_offset(x1, point);
}


void static_stub_Relocation::pack_data_to(CodeSection* dest) {
  short* p = (short*) dest->locs_end();
  CodeSection* insts = dest->outer()->insts();
  normalize_address(_static_call, insts);
  p = pack_1_int_to(p, scaled_offset(_static_call, insts->start()));
  dest->set_locs_end((relocInfo*) p);
}

void static_stub_Relocation::unpack_data() {
  address base = binding()->section_start(CodeBuffer::SECT_INSTS);
  _static_call = address_from_scaled_offset(unpack_1_int(), base);
}


void external_word_Relocation::pack_data_to(CodeSection* dest) {
  short* p = (short*) dest->locs_end();
  int32_t index = runtime_address_to_index(_target);
#ifndef _LP64
  p = pack_1_int_to(p, index);
#else
  if (is_index(index)) {
    p = pack_2_ints_to(p, index, 0);
  } else {
    jlong t = (jlong) _target;
    int32_t lo = low(t);
    int32_t hi = high(t);
    p = pack_2_ints_to(p, lo, hi);
    DEBUG_ONLY(jlong t1 = jlong_from(hi, lo));
    assert(!is_index(t1) && (address) t1 == _target, "not symmetric");
  }
#endif /* _LP64 */
  dest->set_locs_end((relocInfo*) p);
}


void external_word_Relocation::unpack_data() {
#ifndef _LP64
  _target = index_to_runtime_address(unpack_1_int());
#else
  int32_t lo, hi;
  unpack_2_ints(lo, hi);
  jlong t = jlong_from(hi, lo);;
  if (is_index(t)) {
    _target = index_to_runtime_address(t);
  } else {
    _target = (address) t;
  }
#endif /* _LP64 */
}


void internal_word_Relocation::pack_data_to(CodeSection* dest) {
  short* p = (short*) dest->locs_end();
  normalize_address(_target, dest, true);

  // Check whether my target address is valid within this section.
  // If not, strengthen the relocation type to point to another section.
  int sindex = _section;
  if (sindex == CodeBuffer::SECT_NONE && _target != NULL
      && (!dest->allocates(_target) || _target == dest->locs_point())) {
    sindex = dest->outer()->section_index_of(_target);
    guarantee(sindex != CodeBuffer::SECT_NONE, "must belong somewhere");
    relocInfo* base = dest->locs_end() - 1;
    assert(base->type() == this->type(), "sanity");
    // Change the written type, to be section_word_type instead.
    base->set_type(relocInfo::section_word_type);
  }

  // Note: An internal_word relocation cannot refer to its own instruction,
  // because we reserve "0" to mean that the pointer itself is embedded
  // in the code stream.  We use a section_word relocation for such cases.

  if (sindex == CodeBuffer::SECT_NONE) {
    assert(type() == relocInfo::internal_word_type, "must be base class");
    guarantee(_target == NULL || dest->allocates2(_target), "must be within the given code section");
    jint x0 = scaled_offset_null_special(_target, dest->locs_point());
    assert(!(x0 == 0 && _target != NULL), "correct encoding of null target");
    p = pack_1_int_to(p, x0);
  } else {
    assert(_target != NULL, "sanity");
    CodeSection* sect = dest->outer()->code_section(sindex);
    guarantee(sect->allocates2(_target), "must be in correct section");
    address base = sect->start();
    jint offset = scaled_offset(_target, base);
    assert((uint)sindex < (uint)CodeBuffer::SECT_LIMIT, "sanity");
    assert(CodeBuffer::SECT_LIMIT <= (1 << section_width), "section_width++");
    p = pack_1_int_to(p, (offset << section_width) | sindex);
  }

  dest->set_locs_end((relocInfo*) p);
}


void internal_word_Relocation::unpack_data() {
  jint x0 = unpack_1_int();
  _target = x0==0? NULL: address_from_scaled_offset(x0, addr());
  _section = CodeBuffer::SECT_NONE;
}


void section_word_Relocation::unpack_data() {
  jint    x      = unpack_1_int();
  jint    offset = (x >> section_width);
  int     sindex = (x & ((1<<section_width)-1));
  address base   = binding()->section_start(sindex);

  _section = sindex;
  _target  = address_from_scaled_offset(offset, base);
}


void breakpoint_Relocation::pack_data_to(CodeSection* dest) {
  short* p = (short*) dest->locs_end();
  address point = dest->locs_point();

  *p++ = _bits;

  assert(_target != NULL, "sanity");

  if (internal())  normalize_address(_target, dest);

  jint target_bits =
    (jint)( internal() ? scaled_offset           (_target, point)
                       : runtime_address_to_index(_target) );
  if (settable()) {
    // save space for set_target later
    p = add_jint(p, target_bits);
  } else {
    p = add_var_int(p, target_bits);
  }

  for (int i = 0; i < instrlen(); i++) {
    // put placeholder words until bytes can be saved
    p = add_short(p, (short)0x7777);
  }

  dest->set_locs_end((relocInfo*) p);
}


void breakpoint_Relocation::unpack_data() {
  _bits = live_bits();

  int targetlen = datalen() - 1 - instrlen();
  jint target_bits = 0;
  if (targetlen == 0)       target_bits = 0;
  else if (targetlen == 1)  target_bits = *(data()+1);
  else if (targetlen == 2)  target_bits = relocInfo::jint_from_data(data()+1);
  else                      { ShouldNotReachHere(); }

  _target = internal() ? address_from_scaled_offset(target_bits, addr())
                       : index_to_runtime_address  (target_bits);
}


//// miscellaneous methods
oop* oop_Relocation::oop_addr() {
  int n = _oop_index;
  if (n == 0) {
    // oop is stored in the code stream
    return (oop*) pd_address_in_code();
  } else {
    // oop is stored in table at CodeBlob::oops_begin
    return code()->oop_addr_at(n);
  }
}


oop oop_Relocation::oop_value() {
  oop v = *oop_addr();
  // clean inline caches store a special pseudo-null
  if (v == (oop)Universe::non_oop_word())  v = NULL;
  return v;
}


void oop_Relocation::fix_oop_relocation() {
  if (!oop_is_immediate()) {
    // get the oop from the pool, and re-insert it into the instruction:
    set_value(value());
  }
}


RelocIterator virtual_call_Relocation::parse_ic(CodeBlob* &code, address &ic_call, address &first_oop,
                                                oop* &oop_addr, bool *is_optimized) {
  assert(ic_call != NULL, "ic_call address must be set");
  assert(ic_call != NULL || first_oop != NULL, "must supply a non-null input");
  if (code == NULL) {
    if (ic_call != NULL) {
      code = CodeCache::find_blob(ic_call);
    } else if (first_oop != NULL) {
      code = CodeCache::find_blob(first_oop);
    }
    assert(code != NULL, "address to parse must be in CodeBlob");
  }
  assert(ic_call   == NULL || code->contains(ic_call),   "must be in CodeBlob");
  assert(first_oop == NULL || code->contains(first_oop), "must be in CodeBlob");

  address oop_limit = NULL;

  if (ic_call != NULL) {
    // search for the ic_call at the given address
    RelocIterator iter(code, ic_call, ic_call+1);
    bool ret = iter.next();
    assert(ret == true, "relocInfo must exist at this address");
    assert(iter.addr() == ic_call, "must find ic_call");
    if (iter.type() == relocInfo::virtual_call_type) {
      virtual_call_Relocation* r = iter.virtual_call_reloc();
      first_oop = r->first_oop();
      oop_limit = r->oop_limit();
      *is_optimized = false;
    } else {
      assert(iter.type() == relocInfo::opt_virtual_call_type, "must be a virtual call");
      *is_optimized = true;
      oop_addr = NULL;
      first_oop = NULL;
      return iter;
    }
  }

  // search for the first_oop, to get its oop_addr
  RelocIterator all_oops(code, first_oop);
  RelocIterator iter = all_oops;
  iter.set_limit(first_oop+1);
  bool found_oop = false;
  while (iter.next()) {
    if (iter.type() == relocInfo::oop_type) {
      assert(iter.addr() == first_oop, "must find first_oop");
      oop_addr = iter.oop_reloc()->oop_addr();
      found_oop = true;
      break;
    }
  }
  assert(found_oop, "must find first_oop");

  bool did_reset = false;
  while (ic_call == NULL) {
    // search forward for the ic_call matching the given first_oop
    while (iter.next()) {
      if (iter.type() == relocInfo::virtual_call_type) {
        virtual_call_Relocation* r = iter.virtual_call_reloc();
        if (r->first_oop() == first_oop) {
          ic_call   = r->addr();
          oop_limit = r->oop_limit();
          break;
        }
      }
    }
    guarantee(!did_reset, "cannot find ic_call");
    iter = RelocIterator(code); // search the whole CodeBlob
    did_reset = true;
  }

  assert(oop_limit != NULL && first_oop != NULL && ic_call != NULL, "");
  all_oops.set_limit(oop_limit);
  return all_oops;
}


address virtual_call_Relocation::first_oop() {
  assert(_first_oop != NULL && _first_oop < addr(), "must precede ic_call");
  return _first_oop;
}


address virtual_call_Relocation::oop_limit() {
  if (_oop_limit == NULL)
    return addr() + NativeCall::instruction_size;
  else
    return _oop_limit;
}



void virtual_call_Relocation::clear_inline_cache() {
  // No stubs for ICs
  // Clean IC
  ResourceMark rm;
  CompiledIC* icache = CompiledIC_at(this);
  icache->set_to_clean();
}


void opt_virtual_call_Relocation::clear_inline_cache() {
  // No stubs for ICs
  // Clean IC
  ResourceMark rm;
  CompiledIC* icache = CompiledIC_at(this);
  icache->set_to_clean();
}


address opt_virtual_call_Relocation::static_stub() {
  // search for the static stub who points back to this static call
  address static_call_addr = addr();
  RelocIterator iter(code());
  while (iter.next()) {
    if (iter.type() == relocInfo::static_stub_type) {
      if (iter.static_stub_reloc()->static_call() == static_call_addr) {
        return iter.addr();
      }
    }
  }
  return NULL;
}


void static_call_Relocation::clear_inline_cache() {
  // Safe call site info
  CompiledStaticCall* handler = compiledStaticCall_at(this);
  handler->set_to_clean();
}


address static_call_Relocation::static_stub() {
  // search for the static stub who points back to this static call
  address static_call_addr = addr();
  RelocIterator iter(code());
  while (iter.next()) {
    if (iter.type() == relocInfo::static_stub_type) {
      if (iter.static_stub_reloc()->static_call() == static_call_addr) {
        return iter.addr();
      }
    }
  }
  return NULL;
}


void static_stub_Relocation::clear_inline_cache() {
  // Call stub is only used when calling the interpreted code.
  // It does not really need to be cleared, except that we want to clean out the methodoop.
  CompiledStaticCall::set_stub_to_clean(this);
}


void external_word_Relocation::fix_relocation_after_move(const CodeBuffer* src, CodeBuffer* dest) {
  address target = _target;
  if (target == NULL) {
    // An absolute embedded reference to an external location,
    // which means there is nothing to fix here.
    return;
  }
  // Probably this reference is absolute, not relative, so the
  // following is probably a no-op.
  assert(src->section_index_of(target) == CodeBuffer::SECT_NONE, "sanity");
  set_value(target);
}


address external_word_Relocation::target() {
  address target = _target;
  if (target == NULL) {
    target = pd_get_address_from_code();
  }
  return target;
}


void internal_word_Relocation::fix_relocation_after_move(const CodeBuffer* src, CodeBuffer* dest) {
  address target = _target;
  if (target == NULL) {
    if (addr_in_const()) {
      target = new_addr_for(*(address*)addr(), src, dest);
    } else {
      target = new_addr_for(pd_get_address_from_code(), src, dest);
    }
  }
  set_value(target);
}


address internal_word_Relocation::target() {
  address target = _target;
  if (target == NULL) {
    target = pd_get_address_from_code();
  }
  return target;
}


breakpoint_Relocation::breakpoint_Relocation(int kind, address target, bool internal) {
  bool active    = false;
  bool enabled   = (kind == initialization);
  bool removable = (kind != safepoint);
  bool settable  = (target == NULL);

  int bits = kind;
  if (enabled)    bits |= enabled_state;
  if (internal)   bits |= internal_attr;
  if (removable)  bits |= removable_attr;
  if (settable)   bits |= settable_attr;

  _bits = bits | high_bit;
  _target = target;

  assert(this->kind()      == kind,      "kind encoded");
  assert(this->enabled()   == enabled,   "enabled encoded");
  assert(this->active()    == active,    "active encoded");
  assert(this->internal()  == internal,  "internal encoded");
  assert(this->removable() == removable, "removable encoded");
  assert(this->settable()  == settable,  "settable encoded");
}


address breakpoint_Relocation::target() const {
  return _target;
}


void breakpoint_Relocation::set_target(address x) {
  assert(settable(), "must be settable");
  jint target_bits =
    (jint)(internal() ? scaled_offset           (x, addr())
                      : runtime_address_to_index(x));
  short* p = &live_bits() + 1;
  p = add_jint(p, target_bits);
  assert(p == instrs(), "new target must fit");
  _target = x;
}


void breakpoint_Relocation::set_enabled(bool b) {
  if (enabled() == b) return;

  if (b) {
    set_bits(bits() | enabled_state);
  } else {
    set_active(false);          // remove the actual breakpoint insn, if any
    set_bits(bits() & ~enabled_state);
  }
}


void breakpoint_Relocation::set_active(bool b) {
  assert(!b || enabled(), "cannot activate a disabled breakpoint");

  if (active() == b) return;

  // %%% should probably seize a lock here (might not be the right lock)
  //MutexLockerEx ml_patch(Patching_lock, true);
  //if (active() == b)  return;         // recheck state after locking

  if (b) {
    set_bits(bits() | active_state);
    if (instrlen() == 0)
      fatal("breakpoints in original code must be undoable");
    pd_swap_in_breakpoint (addr(), instrs(), instrlen());
  } else {
    set_bits(bits() & ~active_state);
    pd_swap_out_breakpoint(addr(), instrs(), instrlen());
  }
}


//---------------------------------------------------------------------------------
// Non-product code

#ifndef PRODUCT

static const char* reloc_type_string(relocInfo::relocType t) {
  switch (t) {
  #define EACH_CASE(name) \
  case relocInfo::name##_type: \
    return #name;

  APPLY_TO_RELOCATIONS(EACH_CASE);
  #undef EACH_CASE

  case relocInfo::none:
    return "none";
  case relocInfo::data_prefix_tag:
    return "prefix";
  default:
    return "UNKNOWN RELOC TYPE";
  }
}


void RelocIterator::print_current() {
  if (!has_current()) {
    tty->print_cr("(no relocs)");
    return;
  }
  tty->print("relocInfo@" INTPTR_FORMAT " [type=%d(%s) addr=" INTPTR_FORMAT,
             _current, type(), reloc_type_string((relocInfo::relocType) type()), _addr);
  if (current()->format() != 0)
    tty->print(" format=%d", current()->format());
  if (datalen() == 1) {
    tty->print(" data=%d", data()[0]);
  } else if (datalen() > 0) {
    tty->print(" data={");
    for (int i = 0; i < datalen(); i++) {
      tty->print("%04x", data()[i] & 0xFFFF);
    }
    tty->print("}");
  }
  tty->print("]");
  switch (type()) {
  case relocInfo::oop_type:
    {
      oop_Relocation* r = oop_reloc();
      oop* oop_addr  = NULL;
      oop  raw_oop   = NULL;
      oop  oop_value = NULL;
      if (code() != NULL || r->oop_is_immediate()) {
        oop_addr  = r->oop_addr();
        raw_oop   = *oop_addr;
        oop_value = r->oop_value();
      }
      tty->print(" | [oop_addr=" INTPTR_FORMAT " *=" INTPTR_FORMAT " offset=%d]",
                 oop_addr, (address)raw_oop, r->offset());
      // Do not print the oop by default--we want this routine to
      // work even during GC or other inconvenient times.
      if (WizardMode && oop_value != NULL) {
        tty->print("oop_value=" INTPTR_FORMAT ": ", (address)oop_value);
        oop_value->print_value_on(tty);
      }
      break;
    }
  case relocInfo::external_word_type:
  case relocInfo::internal_word_type:
  case relocInfo::section_word_type:
    {
      DataRelocation* r = (DataRelocation*) reloc();
      tty->print(" | [target=" INTPTR_FORMAT "]", r->value()); //value==target
      break;
    }
  case relocInfo::static_call_type:
  case relocInfo::runtime_call_type:
    {
      CallRelocation* r = (CallRelocation*) reloc();
      tty->print(" | [destination=" INTPTR_FORMAT "]", r->destination());
      break;
    }
  case relocInfo::virtual_call_type:
    {
      virtual_call_Relocation* r = (virtual_call_Relocation*) reloc();
      tty->print(" | [destination=" INTPTR_FORMAT " first_oop=" INTPTR_FORMAT " oop_limit=" INTPTR_FORMAT "]",
                 r->destination(), r->first_oop(), r->oop_limit());
      break;
    }
  case relocInfo::static_stub_type:
    {
      static_stub_Relocation* r = (static_stub_Relocation*) reloc();
      tty->print(" | [static_call=" INTPTR_FORMAT "]", r->static_call());
      break;
    }
  }
  tty->cr();
}


void RelocIterator::print() {
  RelocIterator save_this = (*this);
  relocInfo* scan = _current;
  if (!has_current())  scan += 1;  // nothing to scan here!

  bool skip_next = has_current();
  bool got_next;
  while (true) {
    got_next = (skip_next || next());
    skip_next = false;

    tty->print("         @" INTPTR_FORMAT ": ", scan);
    relocInfo* newscan = _current+1;
    if (!has_current())  newscan -= 1;  // nothing to scan here!
    while (scan < newscan) {
      tty->print("%04x", *(short*)scan & 0xFFFF);
      scan++;
    }
    tty->cr();

    if (!got_next)  break;
    print_current();
  }

  (*this) = save_this;
}

// For the debugger:
extern "C"
void print_blob_locs(CodeBlob* cb) {
  cb->print();
  RelocIterator iter(cb);
  iter.print();
}
extern "C"
void print_buf_locs(CodeBuffer* cb) {
  FlagSetting fs(PrintRelocations, true);
  cb->print();
}
#endif // !PRODUCT

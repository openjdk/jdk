/*
 * Copyright (c) 1997, 2009, Oracle and/or its affiliates. All rights reserved.
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
# include "incls/_codeBuffer.cpp.incl"

// The structure of a CodeSection:
//
//    _start ->           +----------------+
//                        | machine code...|
//    _end ->             |----------------|
//                        |                |
//                        |    (empty)     |
//                        |                |
//                        |                |
//                        +----------------+
//    _limit ->           |                |
//
//    _locs_start ->      +----------------+
//                        |reloc records...|
//                        |----------------|
//    _locs_end ->        |                |
//                        |                |
//                        |    (empty)     |
//                        |                |
//                        |                |
//                        +----------------+
//    _locs_limit ->      |                |
// The _end (resp. _limit) pointer refers to the first
// unused (resp. unallocated) byte.

// The structure of the CodeBuffer while code is being accumulated:
//
//    _total_start ->    \
//    _insts._start ->              +----------------+
//                                  |                |
//                                  |     Code       |
//                                  |                |
//    _stubs._start ->              |----------------|
//                                  |                |
//                                  |    Stubs       | (also handlers for deopt/exception)
//                                  |                |
//    _consts._start ->             |----------------|
//                                  |                |
//                                  |   Constants    |
//                                  |                |
//                                  +----------------+
//    + _total_size ->              |                |
//
// When the code and relocations are copied to the code cache,
// the empty parts of each section are removed, and everything
// is copied into contiguous locations.

typedef CodeBuffer::csize_t csize_t;  // file-local definition

// external buffer, in a predefined CodeBlob or other buffer area
// Important: The code_start must be taken exactly, and not realigned.
CodeBuffer::CodeBuffer(address code_start, csize_t code_size) {
  assert(code_start != NULL, "sanity");
  initialize_misc("static buffer");
  initialize(code_start, code_size);
  assert(verify_section_allocation(), "initial use of buffer OK");
}

void CodeBuffer::initialize(csize_t code_size, csize_t locs_size) {
  // Compute maximal alignment.
  int align = _insts.alignment();
  // Always allow for empty slop around each section.
  int slop = (int) CodeSection::end_slop();

  assert(blob() == NULL, "only once");
  set_blob(BufferBlob::create(_name, code_size + (align+slop) * (SECT_LIMIT+1)));
  if (blob() == NULL) {
    // The assembler constructor will throw a fatal on an empty CodeBuffer.
    return;  // caller must test this
  }

  // Set up various pointers into the blob.
  initialize(_total_start, _total_size);

  assert((uintptr_t)code_begin() % CodeEntryAlignment == 0, "instruction start not code entry aligned");

  pd_initialize();

  if (locs_size != 0) {
    _insts.initialize_locs(locs_size / sizeof(relocInfo));
  }

  assert(verify_section_allocation(), "initial use of blob is OK");
}


CodeBuffer::~CodeBuffer() {
  // If we allocate our code buffer from the CodeCache
  // via a BufferBlob, and it's not permanent, then
  // free the BufferBlob.
  // The rest of the memory will be freed when the ResourceObj
  // is released.
  assert(verify_section_allocation(), "final storage configuration still OK");
  for (CodeBuffer* cb = this; cb != NULL; cb = cb->before_expand()) {
    // Previous incarnations of this buffer are held live, so that internal
    // addresses constructed before expansions will not be confused.
    cb->free_blob();
  }

  // free any overflow storage
  delete _overflow_arena;

#ifdef ASSERT
  Copy::fill_to_bytes(this, sizeof(*this), badResourceValue);
#endif
}

void CodeBuffer::initialize_oop_recorder(OopRecorder* r) {
  assert(_oop_recorder == &_default_oop_recorder && _default_oop_recorder.is_unused(), "do this once");
  DEBUG_ONLY(_default_oop_recorder.oop_size());  // force unused OR to be frozen
  _oop_recorder = r;
}

void CodeBuffer::initialize_section_size(CodeSection* cs, csize_t size) {
  assert(cs != &_insts, "insts is the memory provider, not the consumer");
#ifdef ASSERT
  for (int n = (int)SECT_INSTS+1; n < (int)SECT_LIMIT; n++) {
    CodeSection* prevCS = code_section(n);
    if (prevCS == cs)  break;
    assert(!prevCS->is_allocated(), "section allocation must be in reverse order");
  }
#endif
  csize_t slop = CodeSection::end_slop();  // margin between sections
  int align = cs->alignment();
  assert(is_power_of_2(align), "sanity");
  address start  = _insts._start;
  address limit  = _insts._limit;
  address middle = limit - size;
  middle -= (intptr_t)middle & (align-1);  // align the division point downward
  guarantee(middle - slop > start, "need enough space to divide up");
  _insts._limit = middle - slop;  // subtract desired space, plus slop
  cs->initialize(middle, limit - middle);
  assert(cs->start() == middle, "sanity");
  assert(cs->limit() == limit,  "sanity");
  // give it some relocations to start with, if the main section has them
  if (_insts.has_locs())  cs->initialize_locs(1);
}

void CodeBuffer::freeze_section(CodeSection* cs) {
  CodeSection* next_cs = (cs == consts())? NULL: code_section(cs->index()+1);
  csize_t frozen_size = cs->size();
  if (next_cs != NULL) {
    frozen_size = next_cs->align_at_start(frozen_size);
  }
  address old_limit = cs->limit();
  address new_limit = cs->start() + frozen_size;
  relocInfo* old_locs_limit = cs->locs_limit();
  relocInfo* new_locs_limit = cs->locs_end();
  // Patch the limits.
  cs->_limit = new_limit;
  cs->_locs_limit = new_locs_limit;
  cs->_frozen = true;
  if (!next_cs->is_allocated() && !next_cs->is_frozen()) {
    // Give remaining buffer space to the following section.
    next_cs->initialize(new_limit, old_limit - new_limit);
    next_cs->initialize_shared_locs(new_locs_limit,
                                    old_locs_limit - new_locs_limit);
  }
}

void CodeBuffer::set_blob(BufferBlob* blob) {
  _blob = blob;
  if (blob != NULL) {
    address start = blob->instructions_begin();
    address end   = blob->instructions_end();
    // Round up the starting address.
    int align = _insts.alignment();
    start += (-(intptr_t)start) & (align-1);
    _total_start = start;
    _total_size  = end - start;
  } else {
    #ifdef ASSERT
    // Clean out dangling pointers.
    _total_start    = badAddress;
    _insts._start   = _insts._end   = badAddress;
    _stubs._start   = _stubs._end   = badAddress;
    _consts._start  = _consts._end  = badAddress;
    #endif //ASSERT
  }
}

void CodeBuffer::free_blob() {
  if (_blob != NULL) {
    BufferBlob::free(_blob);
    set_blob(NULL);
  }
}

const char* CodeBuffer::code_section_name(int n) {
#ifdef PRODUCT
  return NULL;
#else //PRODUCT
  switch (n) {
  case SECT_INSTS:             return "insts";
  case SECT_STUBS:             return "stubs";
  case SECT_CONSTS:            return "consts";
  default:                     return NULL;
  }
#endif //PRODUCT
}

int CodeBuffer::section_index_of(address addr) const {
  for (int n = 0; n < (int)SECT_LIMIT; n++) {
    const CodeSection* cs = code_section(n);
    if (cs->allocates(addr))  return n;
  }
  return SECT_NONE;
}

int CodeBuffer::locator(address addr) const {
  for (int n = 0; n < (int)SECT_LIMIT; n++) {
    const CodeSection* cs = code_section(n);
    if (cs->allocates(addr)) {
      return locator(addr - cs->start(), n);
    }
  }
  return -1;
}

address CodeBuffer::locator_address(int locator) const {
  if (locator < 0)  return NULL;
  address start = code_section(locator_sect(locator))->start();
  return start + locator_pos(locator);
}

address CodeBuffer::decode_begin() {
  address begin = _insts.start();
  if (_decode_begin != NULL && _decode_begin > begin)
    begin = _decode_begin;
  return begin;
}


GrowableArray<int>* CodeBuffer::create_patch_overflow() {
  if (_overflow_arena == NULL) {
    _overflow_arena = new Arena();
  }
  return new (_overflow_arena) GrowableArray<int>(_overflow_arena, 8, 0, 0);
}


// Helper function for managing labels and their target addresses.
// Returns a sensible address, and if it is not the label's final
// address, notes the dependency (at 'branch_pc') on the label.
address CodeSection::target(Label& L, address branch_pc) {
  if (L.is_bound()) {
    int loc = L.loc();
    if (index() == CodeBuffer::locator_sect(loc)) {
      return start() + CodeBuffer::locator_pos(loc);
    } else {
      return outer()->locator_address(loc);
    }
  } else {
    assert(allocates2(branch_pc), "sanity");
    address base = start();
    int patch_loc = CodeBuffer::locator(branch_pc - base, index());
    L.add_patch_at(outer(), patch_loc);

    // Need to return a pc, doesn't matter what it is since it will be
    // replaced during resolution later.
    // Don't return NULL or badAddress, since branches shouldn't overflow.
    // Don't return base either because that could overflow displacements
    // for shorter branches.  It will get checked when bound.
    return branch_pc;
  }
}

void CodeSection::relocate(address at, RelocationHolder const& spec, int format) {
  Relocation* reloc = spec.reloc();
  relocInfo::relocType rtype = (relocInfo::relocType) reloc->type();
  if (rtype == relocInfo::none)  return;

  // The assertion below has been adjusted, to also work for
  // relocation for fixup.  Sometimes we want to put relocation
  // information for the next instruction, since it will be patched
  // with a call.
  assert(start() <= at && at <= end()+1,
         "cannot relocate data outside code boundaries");

  if (!has_locs()) {
    // no space for relocation information provided => code cannot be
    // relocated.  Make sure that relocate is only called with rtypes
    // that can be ignored for this kind of code.
    assert(rtype == relocInfo::none              ||
           rtype == relocInfo::runtime_call_type ||
           rtype == relocInfo::internal_word_type||
           rtype == relocInfo::section_word_type ||
           rtype == relocInfo::external_word_type,
           "code needs relocation information");
    // leave behind an indication that we attempted a relocation
    DEBUG_ONLY(_locs_start = _locs_limit = (relocInfo*)badAddress);
    return;
  }

  // Advance the point, noting the offset we'll have to record.
  csize_t offset = at - locs_point();
  set_locs_point(at);

  // Test for a couple of overflow conditions; maybe expand the buffer.
  relocInfo* end = locs_end();
  relocInfo* req = end + relocInfo::length_limit;
  // Check for (potential) overflow
  if (req >= locs_limit() || offset >= relocInfo::offset_limit()) {
    req += (uint)offset / (uint)relocInfo::offset_limit();
    if (req >= locs_limit()) {
      // Allocate or reallocate.
      expand_locs(locs_count() + (req - end));
      // reload pointer
      end = locs_end();
    }
  }

  // If the offset is giant, emit filler relocs, of type 'none', but
  // each carrying the largest possible offset, to advance the locs_point.
  while (offset >= relocInfo::offset_limit()) {
    assert(end < locs_limit(), "adjust previous paragraph of code");
    *end++ = filler_relocInfo();
    offset -= filler_relocInfo().addr_offset();
  }

  // If it's a simple reloc with no data, we'll just write (rtype | offset).
  (*end) = relocInfo(rtype, offset, format);

  // If it has data, insert the prefix, as (data_prefix_tag | data1), data2.
  end->initialize(this, reloc);
}

void CodeSection::initialize_locs(int locs_capacity) {
  assert(_locs_start == NULL, "only one locs init step, please");
  // Apply a priori lower limits to relocation size:
  csize_t min_locs = MAX2(size() / 16, (csize_t)4);
  if (locs_capacity < min_locs)  locs_capacity = min_locs;
  relocInfo* locs_start = NEW_RESOURCE_ARRAY(relocInfo, locs_capacity);
  _locs_start    = locs_start;
  _locs_end      = locs_start;
  _locs_limit    = locs_start + locs_capacity;
  _locs_own      = true;
}

void CodeSection::initialize_shared_locs(relocInfo* buf, int length) {
  assert(_locs_start == NULL, "do this before locs are allocated");
  // Internal invariant:  locs buf must be fully aligned.
  // See copy_relocations_to() below.
  while ((uintptr_t)buf % HeapWordSize != 0 && length > 0) {
    ++buf; --length;
  }
  if (length > 0) {
    _locs_start = buf;
    _locs_end   = buf;
    _locs_limit = buf + length;
    _locs_own   = false;
  }
}

void CodeSection::initialize_locs_from(const CodeSection* source_cs) {
  int lcount = source_cs->locs_count();
  if (lcount != 0) {
    initialize_shared_locs(source_cs->locs_start(), lcount);
    _locs_end = _locs_limit = _locs_start + lcount;
    assert(is_allocated(), "must have copied code already");
    set_locs_point(start() + source_cs->locs_point_off());
  }
  assert(this->locs_count() == source_cs->locs_count(), "sanity");
}

void CodeSection::expand_locs(int new_capacity) {
  if (_locs_start == NULL) {
    initialize_locs(new_capacity);
    return;
  } else {
    int old_count    = locs_count();
    int old_capacity = locs_capacity();
    if (new_capacity < old_capacity * 2)
      new_capacity = old_capacity * 2;
    relocInfo* locs_start;
    if (_locs_own) {
      locs_start = REALLOC_RESOURCE_ARRAY(relocInfo, _locs_start, old_capacity, new_capacity);
    } else {
      locs_start = NEW_RESOURCE_ARRAY(relocInfo, new_capacity);
      Copy::conjoint_jbytes(_locs_start, locs_start, old_capacity * sizeof(relocInfo));
      _locs_own = true;
    }
    _locs_start    = locs_start;
    _locs_end      = locs_start + old_count;
    _locs_limit    = locs_start + new_capacity;
  }
}


/// Support for emitting the code to its final location.
/// The pattern is the same for all functions.
/// We iterate over all the sections, padding each to alignment.

csize_t CodeBuffer::total_code_size() const {
  csize_t code_size_so_far = 0;
  for (int n = 0; n < (int)SECT_LIMIT; n++) {
    const CodeSection* cs = code_section(n);
    if (cs->is_empty())  continue;  // skip trivial section
    code_size_so_far = cs->align_at_start(code_size_so_far);
    code_size_so_far += cs->size();
  }
  return code_size_so_far;
}

void CodeBuffer::compute_final_layout(CodeBuffer* dest) const {
  address buf = dest->_total_start;
  csize_t buf_offset = 0;
  assert(dest->_total_size >= total_code_size(), "must be big enough");

  {
    // not sure why this is here, but why not...
    int alignSize = MAX2((intx) sizeof(jdouble), CodeEntryAlignment);
    assert( (dest->_total_start - _insts.start()) % alignSize == 0, "copy must preserve alignment");
  }

  const CodeSection* prev_cs      = NULL;
  CodeSection*       prev_dest_cs = NULL;
  for (int n = 0; n < (int)SECT_LIMIT; n++) {
    // figure compact layout of each section
    const CodeSection* cs = code_section(n);
    address cstart = cs->start();
    address cend   = cs->end();
    csize_t csize  = cend - cstart;

    CodeSection* dest_cs = dest->code_section(n);
    if (!cs->is_empty()) {
      // Compute initial padding; assign it to the previous non-empty guy.
      // Cf. figure_expanded_capacities.
      csize_t padding = cs->align_at_start(buf_offset) - buf_offset;
      if (padding != 0) {
        buf_offset += padding;
        assert(prev_dest_cs != NULL, "sanity");
        prev_dest_cs->_limit += padding;
      }
      #ifdef ASSERT
      if (prev_cs != NULL && prev_cs->is_frozen() && n < SECT_CONSTS) {
        // Make sure the ends still match up.
        // This is important because a branch in a frozen section
        // might target code in a following section, via a Label,
        // and without a relocation record.  See Label::patch_instructions.
        address dest_start = buf+buf_offset;
        csize_t start2start = cs->start() - prev_cs->start();
        csize_t dest_start2start = dest_start - prev_dest_cs->start();
        assert(start2start == dest_start2start, "cannot stretch frozen sect");
      }
      #endif //ASSERT
      prev_dest_cs = dest_cs;
      prev_cs      = cs;
    }

    debug_only(dest_cs->_start = NULL);  // defeat double-initialization assert
    dest_cs->initialize(buf+buf_offset, csize);
    dest_cs->set_end(buf+buf_offset+csize);
    assert(dest_cs->is_allocated(), "must always be allocated");
    assert(cs->is_empty() == dest_cs->is_empty(), "sanity");

    buf_offset += csize;
  }

  // Done calculating sections; did it come out to the right end?
  assert(buf_offset == total_code_size(), "sanity");
  assert(dest->verify_section_allocation(), "final configuration works");
}

csize_t CodeBuffer::total_offset_of(address addr) const {
  csize_t code_size_so_far = 0;
  for (int n = 0; n < (int)SECT_LIMIT; n++) {
    const CodeSection* cs = code_section(n);
    if (!cs->is_empty()) {
      code_size_so_far = cs->align_at_start(code_size_so_far);
    }
    if (cs->contains2(addr)) {
      return code_size_so_far + (addr - cs->start());
    }
    code_size_so_far += cs->size();
  }
#ifndef PRODUCT
  tty->print_cr("Dangling address " PTR_FORMAT " in:", addr);
  ((CodeBuffer*)this)->print();
#endif
  ShouldNotReachHere();
  return -1;
}

csize_t CodeBuffer::total_relocation_size() const {
  csize_t lsize = copy_relocations_to(NULL);  // dry run only
  csize_t csize = total_code_size();
  csize_t total = RelocIterator::locs_and_index_size(csize, lsize);
  return (csize_t) align_size_up(total, HeapWordSize);
}

csize_t CodeBuffer::copy_relocations_to(CodeBlob* dest) const {
  address buf = NULL;
  csize_t buf_offset = 0;
  csize_t buf_limit = 0;
  if (dest != NULL) {
    buf = (address)dest->relocation_begin();
    buf_limit = (address)dest->relocation_end() - buf;
    assert((uintptr_t)buf % HeapWordSize == 0, "buf must be fully aligned");
    assert(buf_limit % HeapWordSize == 0, "buf must be evenly sized");
  }
  // if dest == NULL, this is just the sizing pass

  csize_t code_end_so_far = 0;
  csize_t code_point_so_far = 0;
  for (int n = 0; n < (int)SECT_LIMIT; n++) {
    // pull relocs out of each section
    const CodeSection* cs = code_section(n);
    assert(!(cs->is_empty() && cs->locs_count() > 0), "sanity");
    if (cs->is_empty())  continue;  // skip trivial section
    relocInfo* lstart = cs->locs_start();
    relocInfo* lend   = cs->locs_end();
    csize_t    lsize  = (csize_t)( (address)lend - (address)lstart );
    csize_t    csize  = cs->size();
    code_end_so_far = cs->align_at_start(code_end_so_far);

    if (lsize > 0) {
      // Figure out how to advance the combined relocation point
      // first to the beginning of this section.
      // We'll insert one or more filler relocs to span that gap.
      // (Don't bother to improve this by editing the first reloc's offset.)
      csize_t new_code_point = code_end_so_far;
      for (csize_t jump;
           code_point_so_far < new_code_point;
           code_point_so_far += jump) {
        jump = new_code_point - code_point_so_far;
        relocInfo filler = filler_relocInfo();
        if (jump >= filler.addr_offset()) {
          jump = filler.addr_offset();
        } else {  // else shrink the filler to fit
          filler = relocInfo(relocInfo::none, jump);
        }
        if (buf != NULL) {
          assert(buf_offset + (csize_t)sizeof(filler) <= buf_limit, "filler in bounds");
          *(relocInfo*)(buf+buf_offset) = filler;
        }
        buf_offset += sizeof(filler);
      }

      // Update code point and end to skip past this section:
      csize_t last_code_point = code_end_so_far + cs->locs_point_off();
      assert(code_point_so_far <= last_code_point, "sanity");
      code_point_so_far = last_code_point; // advance past this guy's relocs
    }
    code_end_so_far += csize;  // advance past this guy's instructions too

    // Done with filler; emit the real relocations:
    if (buf != NULL && lsize != 0) {
      assert(buf_offset + lsize <= buf_limit, "target in bounds");
      assert((uintptr_t)lstart % HeapWordSize == 0, "sane start");
      if (buf_offset % HeapWordSize == 0) {
        // Use wordwise copies if possible:
        Copy::disjoint_words((HeapWord*)lstart,
                             (HeapWord*)(buf+buf_offset),
                             (lsize + HeapWordSize-1) / HeapWordSize);
      } else {
        Copy::conjoint_jbytes(lstart, buf+buf_offset, lsize);
      }
    }
    buf_offset += lsize;
  }

  // Align end of relocation info in target.
  while (buf_offset % HeapWordSize != 0) {
    if (buf != NULL) {
      relocInfo padding = relocInfo(relocInfo::none, 0);
      assert(buf_offset + (csize_t)sizeof(padding) <= buf_limit, "padding in bounds");
      *(relocInfo*)(buf+buf_offset) = padding;
    }
    buf_offset += sizeof(relocInfo);
  }

  assert(code_end_so_far == total_code_size(), "sanity");

  // Account for index:
  if (buf != NULL) {
    RelocIterator::create_index(dest->relocation_begin(),
                                buf_offset / sizeof(relocInfo),
                                dest->relocation_end());
  }

  return buf_offset;
}

void CodeBuffer::copy_code_to(CodeBlob* dest_blob) {
#ifndef PRODUCT
  if (PrintNMethods && (WizardMode || Verbose)) {
    tty->print("done with CodeBuffer:");
    ((CodeBuffer*)this)->print();
  }
#endif //PRODUCT

  CodeBuffer dest(dest_blob->instructions_begin(),
                  dest_blob->instructions_size());
  assert(dest_blob->instructions_size() >= total_code_size(), "good sizing");
  this->compute_final_layout(&dest);
  relocate_code_to(&dest);

  // transfer comments from buffer to blob
  dest_blob->set_comments(_comments);

  // Done moving code bytes; were they the right size?
  assert(round_to(dest.total_code_size(), oopSize) == dest_blob->instructions_size(), "sanity");

  // Flush generated code
  ICache::invalidate_range(dest_blob->instructions_begin(),
                           dest_blob->instructions_size());
}

// Move all my code into another code buffer.
// Consult applicable relocs to repair embedded addresses.
void CodeBuffer::relocate_code_to(CodeBuffer* dest) const {
  DEBUG_ONLY(address dest_end = dest->_total_start + dest->_total_size);
  for (int n = 0; n < (int)SECT_LIMIT; n++) {
    // pull code out of each section
    const CodeSection* cs = code_section(n);
    if (cs->is_empty())  continue;  // skip trivial section
    CodeSection* dest_cs = dest->code_section(n);
    assert(cs->size() == dest_cs->size(), "sanity");
    csize_t usize = dest_cs->size();
    csize_t wsize = align_size_up(usize, HeapWordSize);
    assert(dest_cs->start() + wsize <= dest_end, "no overflow");
    // Copy the code as aligned machine words.
    // This may also include an uninitialized partial word at the end.
    Copy::disjoint_words((HeapWord*)cs->start(),
                         (HeapWord*)dest_cs->start(),
                         wsize / HeapWordSize);

    if (dest->blob() == NULL) {
      // Destination is a final resting place, not just another buffer.
      // Normalize uninitialized bytes in the final padding.
      Copy::fill_to_bytes(dest_cs->end(), dest_cs->remaining(),
                          Assembler::code_fill_byte());
    }

    assert(cs->locs_start() != (relocInfo*)badAddress,
           "this section carries no reloc storage, but reloc was attempted");

    // Make the new code copy use the old copy's relocations:
    dest_cs->initialize_locs_from(cs);

    { // Repair the pc relative information in the code after the move
      RelocIterator iter(dest_cs);
      while (iter.next()) {
        iter.reloc()->fix_relocation_after_move(this, dest);
      }
    }
  }
}

csize_t CodeBuffer::figure_expanded_capacities(CodeSection* which_cs,
                                               csize_t amount,
                                               csize_t* new_capacity) {
  csize_t new_total_cap = 0;

  int prev_n = -1;
  for (int n = 0; n < (int)SECT_LIMIT; n++) {
    const CodeSection* sect = code_section(n);

    if (!sect->is_empty()) {
      // Compute initial padding; assign it to the previous non-empty guy.
      // Cf. compute_final_layout.
      csize_t padding = sect->align_at_start(new_total_cap) - new_total_cap;
      if (padding != 0) {
        new_total_cap += padding;
        assert(prev_n >= 0, "sanity");
        new_capacity[prev_n] += padding;
      }
      prev_n = n;
    }

    csize_t exp = sect->size();  // 100% increase
    if ((uint)exp < 4*K)  exp = 4*K;       // minimum initial increase
    if (sect == which_cs) {
      if (exp < amount)  exp = amount;
      if (StressCodeBuffers)  exp = amount;  // expand only slightly
    } else if (n == SECT_INSTS) {
      // scale down inst increases to a more modest 25%
      exp = 4*K + ((exp - 4*K) >> 2);
      if (StressCodeBuffers)  exp = amount / 2;  // expand only slightly
    } else if (sect->is_empty()) {
      // do not grow an empty secondary section
      exp = 0;
    }
    // Allow for inter-section slop:
    exp += CodeSection::end_slop();
    csize_t new_cap = sect->size() + exp;
    if (new_cap < sect->capacity()) {
      // No need to expand after all.
      new_cap = sect->capacity();
    }
    new_capacity[n] = new_cap;
    new_total_cap += new_cap;
  }

  return new_total_cap;
}

void CodeBuffer::expand(CodeSection* which_cs, csize_t amount) {
#ifndef PRODUCT
  if (PrintNMethods && (WizardMode || Verbose)) {
    tty->print("expanding CodeBuffer:");
    this->print();
  }

  if (StressCodeBuffers && blob() != NULL) {
    static int expand_count = 0;
    if (expand_count >= 0)  expand_count += 1;
    if (expand_count > 100 && is_power_of_2(expand_count)) {
      tty->print_cr("StressCodeBuffers: have expanded %d times", expand_count);
      // simulate an occasional allocation failure:
      free_blob();
    }
  }
#endif //PRODUCT

  // Resizing must be allowed
  {
    if (blob() == NULL)  return;  // caller must check for blob == NULL
    for (int n = 0; n < (int)SECT_LIMIT; n++) {
      guarantee(!code_section(n)->is_frozen(), "resizing not allowed when frozen");
    }
  }

  // Figure new capacity for each section.
  csize_t new_capacity[SECT_LIMIT];
  csize_t new_total_cap
    = figure_expanded_capacities(which_cs, amount, new_capacity);

  // Create a new (temporary) code buffer to hold all the new data
  CodeBuffer cb(name(), new_total_cap, 0);
  if (cb.blob() == NULL) {
    // Failed to allocate in code cache.
    free_blob();
    return;
  }

  // Create an old code buffer to remember which addresses used to go where.
  // This will be useful when we do final assembly into the code cache,
  // because we will need to know how to warp any internal address that
  // has been created at any time in this CodeBuffer's past.
  CodeBuffer* bxp = new CodeBuffer(_total_start, _total_size);
  bxp->take_over_code_from(this);  // remember the old undersized blob
  DEBUG_ONLY(this->_blob = NULL);  // silence a later assert
  bxp->_before_expand = this->_before_expand;
  this->_before_expand = bxp;

  // Give each section its required (expanded) capacity.
  for (int n = (int)SECT_LIMIT-1; n >= SECT_INSTS; n--) {
    CodeSection* cb_sect   = cb.code_section(n);
    CodeSection* this_sect = code_section(n);
    if (new_capacity[n] == 0)  continue;  // already nulled out
    if (n > SECT_INSTS) {
      cb.initialize_section_size(cb_sect, new_capacity[n]);
    }
    assert(cb_sect->capacity() >= new_capacity[n], "big enough");
    address cb_start = cb_sect->start();
    cb_sect->set_end(cb_start + this_sect->size());
    if (this_sect->mark() == NULL) {
      cb_sect->clear_mark();
    } else {
      cb_sect->set_mark(cb_start + this_sect->mark_off());
    }
  }

  // Move all the code and relocations to the new blob:
  relocate_code_to(&cb);

  // Copy the temporary code buffer into the current code buffer.
  // Basically, do {*this = cb}, except for some control information.
  this->take_over_code_from(&cb);
  cb.set_blob(NULL);

  // Zap the old code buffer contents, to avoid mistakenly using them.
  debug_only(Copy::fill_to_bytes(bxp->_total_start, bxp->_total_size,
                                 badCodeHeapFreeVal));

  _decode_begin = NULL;  // sanity

  // Make certain that the new sections are all snugly inside the new blob.
  assert(verify_section_allocation(), "expanded allocation is ship-shape");

#ifndef PRODUCT
  if (PrintNMethods && (WizardMode || Verbose)) {
    tty->print("expanded CodeBuffer:");
    this->print();
  }
#endif //PRODUCT
}

void CodeBuffer::take_over_code_from(CodeBuffer* cb) {
  // Must already have disposed of the old blob somehow.
  assert(blob() == NULL, "must be empty");
#ifdef ASSERT

#endif
  // Take the new blob away from cb.
  set_blob(cb->blob());
  // Take over all the section pointers.
  for (int n = 0; n < (int)SECT_LIMIT; n++) {
    CodeSection* cb_sect   = cb->code_section(n);
    CodeSection* this_sect = code_section(n);
    this_sect->take_over_code_from(cb_sect);
  }
  _overflow_arena = cb->_overflow_arena;
  // Make sure the old cb won't try to use it or free it.
  DEBUG_ONLY(cb->_blob = (BufferBlob*)badAddress);
}

#ifdef ASSERT
bool CodeBuffer::verify_section_allocation() {
  address tstart = _total_start;
  if (tstart == badAddress)  return true;  // smashed by set_blob(NULL)
  address tend   = tstart + _total_size;
  if (_blob != NULL) {
    assert(tstart >= _blob->instructions_begin(), "sanity");
    assert(tend   <= _blob->instructions_end(),   "sanity");
  }
  address tcheck = tstart;  // advancing pointer to verify disjointness
  for (int n = 0; n < (int)SECT_LIMIT; n++) {
    CodeSection* sect = code_section(n);
    if (!sect->is_allocated())  continue;
    assert(sect->start() >= tcheck, "sanity");
    tcheck = sect->start();
    assert((intptr_t)tcheck % sect->alignment() == 0
           || sect->is_empty() || _blob == NULL,
           "start is aligned");
    assert(sect->end()   >= tcheck, "sanity");
    assert(sect->end()   <= tend,   "sanity");
  }
  return true;
}
#endif //ASSERT

#ifndef PRODUCT

void CodeSection::dump() {
  address ptr = start();
  for (csize_t step; ptr < end(); ptr += step) {
    step = end() - ptr;
    if (step > jintSize * 4)  step = jintSize * 4;
    tty->print(PTR_FORMAT ": ", ptr);
    while (step > 0) {
      tty->print(" " PTR32_FORMAT, *(jint*)ptr);
      ptr += jintSize;
    }
    tty->cr();
  }
}


void CodeSection::decode() {
  Disassembler::decode(start(), end());
}


void CodeBuffer::block_comment(intptr_t offset, const char * comment) {
  _comments.add_comment(offset, comment);
}


class CodeComment: public CHeapObj {
 private:
  friend class CodeComments;
  intptr_t     _offset;
  const char * _comment;
  CodeComment* _next;

  ~CodeComment() {
    assert(_next == NULL, "wrong interface for freeing list");
    os::free((void*)_comment);
  }

 public:
  CodeComment(intptr_t offset, const char * comment) {
    _offset = offset;
    _comment = os::strdup(comment);
    _next = NULL;
  }

  intptr_t     offset()  const { return _offset;  }
  const char * comment() const { return _comment; }
  CodeComment* next()          { return _next; }

  void set_next(CodeComment* next) { _next = next; }

  CodeComment* find(intptr_t offset) {
    CodeComment* a = this;
    while (a != NULL && a->_offset != offset) {
      a = a->_next;
    }
    return a;
  }
};


void CodeComments::add_comment(intptr_t offset, const char * comment) {
  CodeComment* c = new CodeComment(offset, comment);
  CodeComment* insert = NULL;
  if (_comments != NULL) {
    CodeComment* c = _comments->find(offset);
    insert = c;
    while (c && c->offset() == offset) {
      insert = c;
      c = c->next();
    }
  }
  if (insert) {
    // insert after comments with same offset
    c->set_next(insert->next());
    insert->set_next(c);
  } else {
    c->set_next(_comments);
    _comments = c;
  }
}


void CodeComments::assign(CodeComments& other) {
  assert(_comments == NULL, "don't overwrite old value");
  _comments = other._comments;
}


void CodeComments::print_block_comment(outputStream* stream, intptr_t offset) {
  if (_comments != NULL) {
    CodeComment* c = _comments->find(offset);
    while (c && c->offset() == offset) {
      stream->bol();
      stream->print("  ;; ");
      stream->print_cr(c->comment());
      c = c->next();
    }
  }
}


void CodeComments::free() {
  CodeComment* n = _comments;
  while (n) {
    // unlink the node from the list saving a pointer to the next
    CodeComment* p = n->_next;
    n->_next = NULL;
    delete n;
    n = p;
  }
  _comments = NULL;
}



void CodeBuffer::decode() {
  Disassembler::decode(decode_begin(), code_end());
  _decode_begin = code_end();
}


void CodeBuffer::skip_decode() {
  _decode_begin = code_end();
}


void CodeBuffer::decode_all() {
  for (int n = 0; n < (int)SECT_LIMIT; n++) {
    // dump contents of each section
    CodeSection* cs = code_section(n);
    tty->print_cr("! %s:", code_section_name(n));
    if (cs != consts())
      cs->decode();
    else
      cs->dump();
  }
}


void CodeSection::print(const char* name) {
  csize_t locs_size = locs_end() - locs_start();
  tty->print_cr(" %7s.code = " PTR_FORMAT " : " PTR_FORMAT " : " PTR_FORMAT " (%d of %d)%s",
                name, start(), end(), limit(), size(), capacity(),
                is_frozen()? " [frozen]": "");
  tty->print_cr(" %7s.locs = " PTR_FORMAT " : " PTR_FORMAT " : " PTR_FORMAT " (%d of %d) point=%d",
                name, locs_start(), locs_end(), locs_limit(), locs_size, locs_capacity(), locs_point_off());
  if (PrintRelocations) {
    RelocIterator iter(this);
    iter.print();
  }
}

void CodeBuffer::print() {
  if (this == NULL) {
    tty->print_cr("NULL CodeBuffer pointer");
    return;
  }

  tty->print_cr("CodeBuffer:");
  for (int n = 0; n < (int)SECT_LIMIT; n++) {
    // print each section
    CodeSection* cs = code_section(n);
    cs->print(code_section_name(n));
  }
}

#endif // PRODUCT

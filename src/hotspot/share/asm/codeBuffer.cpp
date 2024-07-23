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

#include "precompiled.hpp"
#include "asm/codeBuffer.hpp"
#include "code/compiledIC.hpp"
#include "code/oopRecorder.inline.hpp"
#include "compiler/disassembler.hpp"
#include "logging/log.hpp"
#include "oops/klass.inline.hpp"
#include "oops/methodData.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/icache.hpp"
#include "runtime/safepointVerifiers.hpp"
#include "utilities/align.hpp"
#include "utilities/copy.hpp"
#include "utilities/powerOfTwo.hpp"
#include "utilities/xmlstream.hpp"

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
//    _consts._start ->             +----------------+
//                                  |                |
//                                  |   Constants    |
//                                  |                |
//    _insts._start ->              |----------------|
//                                  |                |
//                                  |     Code       |
//                                  |                |
//    _stubs._start ->              |----------------|
//                                  |                |
//                                  |    Stubs       | (also handlers for deopt/exception)
//                                  |                |
//                                  +----------------+
//    + _total_size ->              |                |
//
// When the code and relocations are copied to the code cache,
// the empty parts of each section are removed, and everything
// is copied into contiguous locations.

typedef CodeBuffer::csize_t csize_t;  // file-local definition

// External buffer, in a predefined CodeBlob.
// Important: The code_start must be taken exactly, and not realigned.
CodeBuffer::CodeBuffer(CodeBlob* blob) DEBUG_ONLY(: Scrubber(this, sizeof(*this))) {
  // Provide code buffer with meaningful name
  initialize_misc(blob->name());
  initialize(blob->content_begin(), blob->content_size());
  debug_only(verify_section_allocation();)
}

void CodeBuffer::initialize(csize_t code_size, csize_t locs_size) {
  // Always allow for empty slop around each section.
  int slop = (int) CodeSection::end_slop();

  assert(SECT_LIMIT == 3, "total_size explicitly lists all section alignments");
  int total_size = code_size + _consts.alignment() + _insts.alignment() + _stubs.alignment() + SECT_LIMIT * slop;

  assert(blob() == nullptr, "only once");
  set_blob(BufferBlob::create(_name, total_size));
  if (blob() == nullptr) {
    // The assembler constructor will throw a fatal on an empty CodeBuffer.
    return;  // caller must test this
  }

  // Set up various pointers into the blob.
  initialize(_total_start, _total_size);

  assert((uintptr_t)insts_begin() % CodeEntryAlignment == 0, "instruction start not code entry aligned");

  pd_initialize();

  if (locs_size != 0) {
    _insts.initialize_locs(locs_size / sizeof(relocInfo));
  }

  debug_only(verify_section_allocation();)
}


CodeBuffer::~CodeBuffer() {
  verify_section_allocation();

  // If we allocated our code buffer from the CodeCache via a BufferBlob, and
  // it's not permanent, then free the BufferBlob.  The rest of the memory
  // will be freed when the ResourceObj is released.
  for (CodeBuffer* cb = this; cb != nullptr; cb = cb->before_expand()) {
    // Previous incarnations of this buffer are held live, so that internal
    // addresses constructed before expansions will not be confused.
    cb->free_blob();
  }
  if (_overflow_arena != nullptr) {
    // free any overflow storage
    delete _overflow_arena;
  }
  if (_shared_trampoline_requests != nullptr) {
    delete _shared_trampoline_requests;
  }

  NOT_PRODUCT(clear_strings());
}

void CodeBuffer::initialize_oop_recorder(OopRecorder* r) {
  assert(_oop_recorder == &_default_oop_recorder && _default_oop_recorder.is_unused(), "do this once");
  DEBUG_ONLY(_default_oop_recorder.freeze());  // force unused OR to be frozen
  _oop_recorder = r;
}

void CodeBuffer::initialize_section_size(CodeSection* cs, csize_t size) {
  assert(cs != &_insts, "insts is the memory provider, not the consumer");
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

void CodeBuffer::set_blob(BufferBlob* blob) {
  _blob = blob;
  if (blob != nullptr) {
    address start = blob->content_begin();
    address end   = blob->content_end();
    // Round up the starting address.
    int align = _insts.alignment();
    start += (-(intptr_t)start) & (align-1);
    _total_start = start;
    _total_size  = end - start;
  } else {
#ifdef ASSERT
    // Clean out dangling pointers.
    _total_start    = badAddress;
    _consts._start  = _consts._end  = badAddress;
    _insts._start   = _insts._end   = badAddress;
    _stubs._start   = _stubs._end   = badAddress;
#endif //ASSERT
  }
}

void CodeBuffer::free_blob() {
  if (_blob != nullptr) {
    BufferBlob::free(_blob);
    set_blob(nullptr);
  }
}

const char* CodeBuffer::code_section_name(int n) {
#ifdef PRODUCT
  return nullptr;
#else //PRODUCT
  switch (n) {
  case SECT_CONSTS:            return "consts";
  case SECT_INSTS:             return "insts";
  case SECT_STUBS:             return "stubs";
  default:                     return nullptr;
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


bool CodeBuffer::is_backward_branch(Label& L) {
  return L.is_bound() && insts_end() <= locator_address(L.loc());
}

#ifndef PRODUCT
address CodeBuffer::decode_begin() {
  address begin = _insts.start();
  if (_decode_begin != nullptr && _decode_begin > begin)
    begin = _decode_begin;
  return begin;
}
#endif // !PRODUCT

GrowableArray<int>* CodeBuffer::create_patch_overflow() {
  if (_overflow_arena == nullptr) {
    _overflow_arena = new (mtCode) Arena(mtCode);
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
    // Don't return null or badAddress, since branches shouldn't overflow.
    // Don't return base either because that could overflow displacements
    // for shorter branches.  It will get checked when bound.
    return branch_pc;
  }
}

void CodeSection::relocate(address at, relocInfo::relocType rtype, int format, jint method_index) {
  RelocationHolder rh;
  switch (rtype) {
    case relocInfo::none: return;
    case relocInfo::opt_virtual_call_type: {
      rh = opt_virtual_call_Relocation::spec(method_index);
      break;
    }
    case relocInfo::static_call_type: {
      rh = static_call_Relocation::spec(method_index);
      break;
    }
    case relocInfo::virtual_call_type: {
      assert(method_index == 0, "resolved method overriding is not supported");
      rh = Relocation::spec_simple(rtype);
      break;
    }
    default: {
      rh = Relocation::spec_simple(rtype);
      break;
    }
  }
  relocate(at, rh, format);
}

void CodeSection::relocate(address at, RelocationHolder const& spec, int format) {
  // Do not relocate in scratch buffers.
  if (scratch_emit()) { return; }
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
           rtype == relocInfo::external_word_type||
           rtype == relocInfo::barrier_type,
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
    *end++ = relocInfo::filler_info();
    offset -= relocInfo::filler_info().addr_offset();
  }

  // If it's a simple reloc with no data, we'll just write (rtype | offset).
  (*end) = relocInfo(rtype, offset, format);

  // If it has data, insert the prefix, as (data_prefix_tag | data1), data2.
  end->initialize(this, reloc);
}

void CodeSection::initialize_locs(int locs_capacity) {
  assert(_locs_start == nullptr, "only one locs init step, please");
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
  assert(_locs_start == nullptr, "do this before locs are allocated");
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
  if (_locs_start == nullptr) {
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

int CodeSection::alignment() const {
  if (_index == CodeBuffer::SECT_CONSTS) {
    // CodeBuffer controls the alignment of the constants section
    return _outer->_const_section_alignment;
  }
  if (_index == CodeBuffer::SECT_INSTS) {
    return (int) CodeEntryAlignment;
  }
  if (_index == CodeBuffer::SECT_STUBS) {
    // CodeBuffer installer expects sections to be HeapWordSize aligned
    return HeapWordSize;
  }
  ShouldNotReachHere();
  return 0;
}

/// Support for emitting the code to its final location.
/// The pattern is the same for all functions.
/// We iterate over all the sections, padding each to alignment.

csize_t CodeBuffer::total_content_size() const {
  csize_t size_so_far = 0;
  for (int n = 0; n < (int)SECT_LIMIT; n++) {
    const CodeSection* cs = code_section(n);
    if (cs->is_empty())  continue;  // skip trivial section
    size_so_far = cs->align_at_start(size_so_far);
    size_so_far += cs->size();
  }
  return size_so_far;
}

void CodeBuffer::compute_final_layout(CodeBuffer* dest) const {
  address buf = dest->_total_start;
  csize_t buf_offset = 0;
  assert(dest->_total_size >= total_content_size(), "must be big enough");
  assert(!_finalize_stubs, "non-finalized stubs");

  {
    // not sure why this is here, but why not...
    int alignSize = MAX2((intx) sizeof(jdouble), CodeEntryAlignment);
    assert( (dest->_total_start - _insts.start()) % alignSize == 0, "copy must preserve alignment");
  }

  const CodeSection* prev_cs      = nullptr;
  CodeSection*       prev_dest_cs = nullptr;

  for (int n = (int) SECT_FIRST; n < (int) SECT_LIMIT; n++) {
    // figure compact layout of each section
    const CodeSection* cs = code_section(n);
    csize_t csize = cs->size();

    CodeSection* dest_cs = dest->code_section(n);
    if (!cs->is_empty()) {
      // Compute initial padding; assign it to the previous non-empty guy.
      // Cf. figure_expanded_capacities.
      csize_t padding = cs->align_at_start(buf_offset) - buf_offset;
      if (prev_dest_cs != nullptr) {
        if (padding != 0) {
          buf_offset += padding;
          prev_dest_cs->_limit += padding;
        }
      } else {
        guarantee(padding == 0, "In first iteration no padding should be needed.");
      }
      prev_dest_cs = dest_cs;
      prev_cs      = cs;
    }

    debug_only(dest_cs->_start = nullptr);  // defeat double-initialization assert
    dest_cs->initialize(buf+buf_offset, csize);
    dest_cs->set_end(buf+buf_offset+csize);
    assert(dest_cs->is_allocated(), "must always be allocated");
    assert(cs->is_empty() == dest_cs->is_empty(), "sanity");

    buf_offset += csize;
  }

  // Done calculating sections; did it come out to the right end?
  assert(buf_offset == total_content_size(), "sanity");
  debug_only(dest->verify_section_allocation();)
}

// Append an oop reference that keeps the class alive.
static void append_oop_references(GrowableArray<oop>* oops, Klass* k) {
  oop cl = k->klass_holder();
  if (cl != nullptr && !oops->contains(cl)) {
    oops->append(cl);
  }
}

void CodeBuffer::finalize_oop_references(const methodHandle& mh) {
  NoSafepointVerifier nsv;

  GrowableArray<oop> oops;

  // Make sure that immediate metadata records something in the OopRecorder
  for (int n = (int) SECT_FIRST; n < (int) SECT_LIMIT; n++) {
    // pull code out of each section
    CodeSection* cs = code_section(n);
    if (cs->is_empty() || (cs->locs_count() == 0)) continue;  // skip trivial section
    RelocIterator iter(cs);
    while (iter.next()) {
      if (iter.type() == relocInfo::metadata_type) {
        metadata_Relocation* md = iter.metadata_reloc();
        if (md->metadata_is_immediate()) {
          Metadata* m = md->metadata_value();
          if (oop_recorder()->is_real(m)) {
            if (m->is_methodData()) {
              m = ((MethodData*)m)->method();
            }
            if (m->is_method()) {
              m = ((Method*)m)->method_holder();
            }
            if (m->is_klass()) {
              append_oop_references(&oops, (Klass*)m);
            } else {
              // XXX This will currently occur for MDO which don't
              // have a backpointer.  This has to be fixed later.
              m->print();
              ShouldNotReachHere();
            }
          }
        }
      }
    }
  }

  if (!oop_recorder()->is_unused()) {
    for (int i = 0; i < oop_recorder()->metadata_count(); i++) {
      Metadata* m = oop_recorder()->metadata_at(i);
      if (oop_recorder()->is_real(m)) {
        if (m->is_methodData()) {
          m = ((MethodData*)m)->method();
        }
        if (m->is_method()) {
          m = ((Method*)m)->method_holder();
        }
        if (m->is_klass()) {
          append_oop_references(&oops, (Klass*)m);
        } else {
          m->print();
          ShouldNotReachHere();
        }
      }
    }

  }

  // Add the class loader of Method* for the nmethod itself
  append_oop_references(&oops, mh->method_holder());

  // Add any oops that we've found
  Thread* thread = Thread::current();
  for (int i = 0; i < oops.length(); i++) {
    oop_recorder()->find_index((jobject)thread->handle_area()->allocate_handle(oops.at(i)));
  }
}



csize_t CodeBuffer::total_offset_of(const CodeSection* cs) const {
  csize_t size_so_far = 0;
  for (int n = (int) SECT_FIRST; n < (int) SECT_LIMIT; n++) {
    const CodeSection* cur_cs = code_section(n);
    if (!cur_cs->is_empty()) {
      size_so_far = cur_cs->align_at_start(size_so_far);
    }
    if (cur_cs->index() == cs->index()) {
      return size_so_far;
    }
    size_so_far += cur_cs->size();
  }
  ShouldNotReachHere();
  return -1;
}

int CodeBuffer::total_skipped_instructions_size() const {
  int total_skipped_size = 0;
  for (int n = (int) SECT_FIRST; n < (int) SECT_LIMIT; n++) {
    const CodeSection* cur_cs = code_section(n);
    if (!cur_cs->is_empty()) {
      total_skipped_size += cur_cs->_skipped_instructions_size;
    }
  }
  return total_skipped_size;
}

csize_t CodeBuffer::total_relocation_size() const {
  csize_t total = copy_relocations_to(nullptr);  // dry run only
  return (csize_t) align_up(total, HeapWordSize);
}

csize_t CodeBuffer::copy_relocations_to(address buf, csize_t buf_limit, bool only_inst) const {
  csize_t buf_offset = 0;
  csize_t code_end_so_far = 0;
  csize_t code_point_so_far = 0;

  assert((uintptr_t)buf % HeapWordSize == 0, "buf must be fully aligned");
  assert(buf_limit % HeapWordSize == 0, "buf must be evenly sized");

  for (int n = (int) SECT_FIRST; n < (int)SECT_LIMIT; n++) {
    if (only_inst && (n != (int)SECT_INSTS)) {
      // Need only relocation info for code.
      continue;
    }
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
        relocInfo filler = relocInfo::filler_info();
        if (jump >= filler.addr_offset()) {
          jump = filler.addr_offset();
        } else {  // else shrink the filler to fit
          filler = relocInfo(relocInfo::none, jump);
        }
        if (buf != nullptr) {
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
    if (buf != nullptr && lsize != 0) {
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
    if (buf != nullptr) {
      relocInfo padding = relocInfo(relocInfo::none, 0);
      assert(buf_offset + (csize_t)sizeof(padding) <= buf_limit, "padding in bounds");
      *(relocInfo*)(buf+buf_offset) = padding;
    }
    buf_offset += sizeof(relocInfo);
  }

  assert(only_inst || code_end_so_far == total_content_size(), "sanity");

  return buf_offset;
}

csize_t CodeBuffer::copy_relocations_to(CodeBlob* dest) const {
  address buf = nullptr;
  csize_t buf_offset = 0;
  csize_t buf_limit = 0;

  if (dest != nullptr) {
    buf = (address)dest->relocation_begin();
    buf_limit = (address)dest->relocation_end() - buf;
  }
  // if dest is null, this is just the sizing pass
  //
  buf_offset = copy_relocations_to(buf, buf_limit, false);

  return buf_offset;
}

void CodeBuffer::copy_code_to(CodeBlob* dest_blob) {
#ifndef PRODUCT
  if (PrintNMethods && (WizardMode || Verbose)) {
    tty->print("done with CodeBuffer:");
    ((CodeBuffer*)this)->print();
  }
#endif //PRODUCT

  CodeBuffer dest(dest_blob);
  assert(dest_blob->content_size() >= total_content_size(), "good sizing");
  this->compute_final_layout(&dest);

  // Set beginning of constant table before relocating.
  dest_blob->set_ctable_begin(dest.consts()->start());

  relocate_code_to(&dest);

  // Share assembly remarks and debug strings with the blob.
  NOT_PRODUCT(dest_blob->use_remarks(_asm_remarks));
  NOT_PRODUCT(dest_blob->use_strings(_dbg_strings));

  // Done moving code bytes; were they the right size?
  assert((int)align_up(dest.total_content_size(), oopSize) == dest_blob->content_size(), "sanity");

  // Flush generated code
  ICache::invalidate_range(dest_blob->code_begin(), dest_blob->code_size());
}

// Move all my code into another code buffer.  Consult applicable
// relocs to repair embedded addresses.  The layout in the destination
// CodeBuffer is different to the source CodeBuffer: the destination
// CodeBuffer gets the final layout (consts, insts, stubs in order of
// ascending address).
void CodeBuffer::relocate_code_to(CodeBuffer* dest) const {
  address dest_end = dest->_total_start + dest->_total_size;
  address dest_filled = nullptr;
  for (int n = (int) SECT_FIRST; n < (int) SECT_LIMIT; n++) {
    // pull code out of each section
    const CodeSection* cs = code_section(n);
    if (cs->is_empty())  continue;  // skip trivial section
    CodeSection* dest_cs = dest->code_section(n);
    assert(cs->size() == dest_cs->size(), "sanity");
    csize_t usize = dest_cs->size();
    csize_t wsize = align_up(usize, HeapWordSize);
    assert(dest_cs->start() + wsize <= dest_end, "no overflow");
    // Copy the code as aligned machine words.
    // This may also include an uninitialized partial word at the end.
    Copy::disjoint_words((HeapWord*)cs->start(),
                         (HeapWord*)dest_cs->start(),
                         wsize / HeapWordSize);

    if (dest->blob() == nullptr) {
      // Destination is a final resting place, not just another buffer.
      // Normalize uninitialized bytes in the final padding.
      Copy::fill_to_bytes(dest_cs->end(), dest_cs->remaining(),
                          Assembler::code_fill_byte());
    }
    // Keep track of the highest filled address
    dest_filled = MAX2(dest_filled, dest_cs->end() + dest_cs->remaining());

    assert(cs->locs_start() != (relocInfo*)badAddress,
           "this section carries no reloc storage, but reloc was attempted");

    // Make the new code copy use the old copy's relocations:
    dest_cs->initialize_locs_from(cs);
  }

  // Do relocation after all sections are copied.
  // This is necessary if the code uses constants in stubs, which are
  // relocated when the corresponding instruction in the code (e.g., a
  // call) is relocated. Stubs are placed behind the main code
  // section, so that section has to be copied before relocating.
  for (int n = (int) SECT_FIRST; n < (int)SECT_LIMIT; n++) {
    CodeSection* dest_cs = dest->code_section(n);
    if (dest_cs->is_empty() || (dest_cs->locs_count() == 0)) continue;  // skip trivial section
    { // Repair the pc relative information in the code after the move
      RelocIterator iter(dest_cs);
      while (iter.next()) {
        iter.reloc()->fix_relocation_after_move(this, dest);
      }
    }
  }

  if (dest->blob() == nullptr && dest_filled != nullptr) {
    // Destination is a final resting place, not just another buffer.
    // Normalize uninitialized bytes in the final padding.
    Copy::fill_to_bytes(dest_filled, dest_end - dest_filled,
                        Assembler::code_fill_byte());

  }
}

csize_t CodeBuffer::figure_expanded_capacities(CodeSection* which_cs,
                                               csize_t amount,
                                               csize_t* new_capacity) {
  csize_t new_total_cap = 0;

  for (int n = (int) SECT_FIRST; n < (int) SECT_LIMIT; n++) {
    const CodeSection* sect = code_section(n);

    if (!sect->is_empty()) {
      // Compute initial padding; assign it to the previous section,
      // even if it's empty (e.g. consts section can be empty).
      // Cf. compute_final_layout
      csize_t padding = sect->align_at_start(new_total_cap) - new_total_cap;
      if (padding != 0) {
        new_total_cap += padding;
        assert(n - 1 >= SECT_FIRST, "sanity");
        new_capacity[n - 1] += padding;
      }
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

  if (StressCodeBuffers && blob() != nullptr) {
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
    if (blob() == nullptr)  return;  // caller must check if blob is null
  }

  // Figure new capacity for each section.
  csize_t new_capacity[SECT_LIMIT];
  memset(new_capacity, 0, sizeof(csize_t) * SECT_LIMIT);
  csize_t new_total_cap
    = figure_expanded_capacities(which_cs, amount, new_capacity);

  // Create a new (temporary) code buffer to hold all the new data
  CodeBuffer cb(name(), new_total_cap, 0);
  if (cb.blob() == nullptr) {
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
  DEBUG_ONLY(this->_blob = nullptr);  // silence a later assert
  bxp->_before_expand = this->_before_expand;
  this->_before_expand = bxp;

  // Give each section its required (expanded) capacity.
  for (int n = (int)SECT_LIMIT-1; n >= SECT_FIRST; n--) {
    CodeSection* cb_sect   = cb.code_section(n);
    CodeSection* this_sect = code_section(n);
    if (new_capacity[n] == 0)  continue;  // already nulled out
    if (n != SECT_INSTS) {
      cb.initialize_section_size(cb_sect, new_capacity[n]);
    }
    assert(cb_sect->capacity() >= new_capacity[n], "big enough");
    address cb_start = cb_sect->start();
    cb_sect->set_end(cb_start + this_sect->size());
    if (this_sect->mark() == nullptr) {
      cb_sect->clear_mark();
    } else {
      cb_sect->set_mark(cb_start + this_sect->mark_off());
    }
  }

  // Needs to be initialized when calling fix_relocation_after_move.
  cb.blob()->set_ctable_begin(cb.consts()->start());

  // Move all the code and relocations to the new blob:
  relocate_code_to(&cb);

  // some internal addresses, _last_insn _last_label, are used during code emission,
  // adjust them in expansion
  adjust_internal_address(insts_begin(), cb.insts_begin());

  // Copy the temporary code buffer into the current code buffer.
  // Basically, do {*this = cb}, except for some control information.
  this->take_over_code_from(&cb);
  cb.set_blob(nullptr);

  // Zap the old code buffer contents, to avoid mistakenly using them.
  debug_only(Copy::fill_to_bytes(bxp->_total_start, bxp->_total_size,
                                 badCodeHeapFreeVal);)

  // Make certain that the new sections are all snugly inside the new blob.
  debug_only(verify_section_allocation();)

#ifndef PRODUCT
  _decode_begin = nullptr;  // sanity
  if (PrintNMethods && (WizardMode || Verbose)) {
    tty->print("expanded CodeBuffer:");
    this->print();
  }
#endif //PRODUCT
}

void CodeBuffer::adjust_internal_address(address from, address to) {
  if (_last_insn != nullptr) {
    _last_insn += to - from;
  }
  if (_last_label != nullptr) {
    _last_label += to - from;
  }
}

void CodeBuffer::take_over_code_from(CodeBuffer* cb) {
  // Must already have disposed of the old blob somehow.
  assert(blob() == nullptr, "must be empty");
  // Take the new blob away from cb.
  set_blob(cb->blob());
  // Take over all the section pointers.
  for (int n = 0; n < (int)SECT_LIMIT; n++) {
    CodeSection* cb_sect   = cb->code_section(n);
    CodeSection* this_sect = code_section(n);
    this_sect->take_over_code_from(cb_sect);
  }
  // Make sure the old cb won't try to use it or free it.
  DEBUG_ONLY(cb->_blob = (BufferBlob*)badAddress);
}

void CodeBuffer::verify_section_allocation() {
  address tstart = _total_start;
  if (tstart == badAddress)  return;  // smashed by set_blob(nullptr)
  address tend   = tstart + _total_size;
  if (_blob != nullptr) {
    guarantee(tstart >= _blob->content_begin(), "sanity");
    guarantee(tend   <= _blob->content_end(),   "sanity");
  }
  // Verify disjointness.
  for (int n = (int) SECT_FIRST; n < (int) SECT_LIMIT; n++) {
    CodeSection* sect = code_section(n);
    if (!sect->is_allocated() || sect->is_empty()) {
      continue;
    }
    guarantee(_blob == nullptr || is_aligned(sect->start(), sect->alignment()),
           "start is aligned");
    for (int m = n + 1; m < (int) SECT_LIMIT; m++) {
      CodeSection* other = code_section(m);
      if (!other->is_allocated() || other == sect) {
        continue;
      }
      guarantee(other->disjoint(sect), "sanity");
    }
    guarantee(sect->end() <= tend, "sanity");
    guarantee(sect->end() <= sect->limit(), "sanity");
  }
}

void CodeBuffer::log_section_sizes(const char* name) {
  if (xtty != nullptr) {
    ttyLocker ttyl;
    // log info about buffer usage
    xtty->print_cr("<blob name='%s' total_size='%d'>", name, _total_size);
    for (int n = (int) CodeBuffer::SECT_FIRST; n < (int) CodeBuffer::SECT_LIMIT; n++) {
      CodeSection* sect = code_section(n);
      if (!sect->is_allocated() || sect->is_empty())  continue;
      xtty->print_cr("<sect index='%d' capacity='%d' size='%d' remaining='%d'/>",
                     n, sect->capacity(), sect->size(), sect->remaining());
    }
    xtty->print_cr("</blob>");
  }
}

bool CodeBuffer::finalize_stubs() {
  if (_finalize_stubs && !pd_finalize_stubs()) {
    // stub allocation failure
    return false;
  }
  _finalize_stubs = false;
  return true;
}

void CodeBuffer::shared_stub_to_interp_for(ciMethod* callee, csize_t call_offset) {
  if (_shared_stub_to_interp_requests == nullptr) {
    _shared_stub_to_interp_requests = new SharedStubToInterpRequests(8);
  }
  SharedStubToInterpRequest request(callee, call_offset);
  _shared_stub_to_interp_requests->push(request);
  _finalize_stubs = true;
}

#ifndef PRODUCT
void CodeBuffer::block_comment(ptrdiff_t offset, const char* comment) {
  if (_collect_comments) {
    const char* str = _asm_remarks.insert(offset, comment);
    postcond(str != comment);
  }
}

const char* CodeBuffer::code_string(const char* str) {
  const char* tmp = _dbg_strings.insert(str);
  postcond(tmp != str);
  return tmp;
}

void CodeBuffer::decode() {
  ttyLocker ttyl;
  Disassembler::decode(decode_begin(), insts_end(), tty NOT_PRODUCT(COMMA &asm_remarks()));
  _decode_begin = insts_end();
}

void CodeSection::print(const char* name) {
  csize_t locs_size = locs_end() - locs_start();
  tty->print_cr(" %7s.code = " PTR_FORMAT " : " PTR_FORMAT " : " PTR_FORMAT " (%d of %d)",
                name, p2i(start()), p2i(end()), p2i(limit()), size(), capacity());
  tty->print_cr(" %7s.locs = " PTR_FORMAT " : " PTR_FORMAT " : " PTR_FORMAT " (%d of %d) point=%d",
                name, p2i(locs_start()), p2i(locs_end()), p2i(locs_limit()), locs_size, locs_capacity(), locs_point_off());
  if (PrintRelocations && (locs_size != 0)) {
    RelocIterator iter(this);
    iter.print();
  }
}

void CodeBuffer::print() {
  tty->print_cr("CodeBuffer:");
  for (int n = 0; n < (int)SECT_LIMIT; n++) {
    // print each section
    CodeSection* cs = code_section(n);
    cs->print(code_section_name(n));
  }
}

// ----- CHeapString -----------------------------------------------------------

class CHeapString : public CHeapObj<mtCode> {
 public:
  CHeapString(const char* str) : _string(os::strdup(str)) {}
 ~CHeapString() {
    os::free((void*)_string);
    _string = nullptr;
  }
  const char* string() const { return _string; }

 private:
  const char* _string;
};

// ----- AsmRemarkCollection ---------------------------------------------------

class AsmRemarkCollection : public CHeapObj<mtCode> {
 public:
  AsmRemarkCollection() : _ref_cnt(1), _remarks(nullptr), _next(nullptr) {}
 ~AsmRemarkCollection() {
    assert(is_empty(), "Must 'clear()' before deleting!");
    assert(_ref_cnt == 0, "No uses must remain when deleting!");
  }
  AsmRemarkCollection* reuse() {
    precond(_ref_cnt > 0);
    return _ref_cnt++, this;
  }

  const char* insert(uint offset, const char* remark);
  const char* lookup(uint offset) const;
  const char* next(uint offset) const;

  bool is_empty() const { return _remarks == nullptr; }
  uint clear();

 private:
  struct Cell : CHeapString {
    Cell(const char* remark, uint offset) :
        CHeapString(remark), offset(offset), prev(nullptr), next(nullptr) {}
    void push_back(Cell* cell) {
      Cell* head = this;
      Cell* tail = prev;
      tail->next = cell;
      cell->next = head;
      cell->prev = tail;
      prev = cell;
    }
    uint offset;
    Cell* prev;
    Cell* next;
  };
  uint  _ref_cnt;
  Cell* _remarks;
  // Using a 'mutable' iteration pointer to allow 'const' on lookup/next (that
  // does not change the state of the list per se), supportig a simplistic
  // iteration scheme.
  mutable Cell* _next;
};

// ----- DbgStringCollection ---------------------------------------------------

class DbgStringCollection : public CHeapObj<mtCode> {
 public:
  DbgStringCollection() : _ref_cnt(1), _strings(nullptr) {}
 ~DbgStringCollection() {
    assert(is_empty(), "Must 'clear()' before deleting!");
    assert(_ref_cnt == 0, "No uses must remain when deleting!");
  }
  DbgStringCollection* reuse() {
    precond(_ref_cnt > 0);
    return _ref_cnt++, this;
  }

  const char* insert(const char* str);
  const char* lookup(const char* str) const;

  bool is_empty() const { return _strings == nullptr; }
  uint clear();

 private:
  struct Cell : CHeapString {
    Cell(const char* dbgstr) :
        CHeapString(dbgstr), prev(nullptr), next(nullptr) {}
    void push_back(Cell* cell) {
      Cell* head = this;
      Cell* tail = prev;
      tail->next = cell;
      cell->next = head;
      cell->prev = tail;
      prev = cell;
    }
    Cell* prev;
    Cell* next;
  };
  uint  _ref_cnt;
  Cell* _strings;
};

// ----- AsmRemarks ------------------------------------------------------------
//
// Acting as interface to reference counted mapping [offset -> remark], where
// offset is a byte offset into an instruction stream (CodeBuffer, CodeBlob or
// other memory buffer) and remark is a string (comment).
//
AsmRemarks::AsmRemarks() : _remarks(new AsmRemarkCollection()) {
  assert(_remarks != nullptr, "Allocation failure!");
}

AsmRemarks::~AsmRemarks() {
  assert(_remarks == nullptr, "Must 'clear()' before deleting!");
}

const char* AsmRemarks::insert(uint offset, const char* remstr) {
  precond(remstr != nullptr);
  return _remarks->insert(offset, remstr);
}

bool AsmRemarks::is_empty() const {
  return _remarks->is_empty();
}

void AsmRemarks::share(const AsmRemarks &src) {
  precond(is_empty());
  clear();
  _remarks = src._remarks->reuse();
}

void AsmRemarks::clear() {
  if (_remarks->clear() == 0) {
    delete _remarks;
  }
  _remarks = nullptr;
}

uint AsmRemarks::print(uint offset, outputStream* strm) const {
  uint count = 0;
  const char* prefix = " ;; ";
  const char* remstr = _remarks->lookup(offset);
  while (remstr != nullptr) {
    strm->bol();
    strm->print("%s", prefix);
    // Don't interpret as format strings since it could contain '%'.
    strm->print_raw(remstr);
    // Advance to next line iff string didn't contain a cr() at the end.
    strm->bol();
    remstr = _remarks->next(offset);
    count++;
  }
  return count;
}

// ----- DbgStrings ------------------------------------------------------------
//
// Acting as interface to reference counted collection of (debug) strings used
// in the code generated, and thus requiring a fixed address.
//
DbgStrings::DbgStrings() : _strings(new DbgStringCollection()) {
  assert(_strings != nullptr, "Allocation failure!");
}

DbgStrings::~DbgStrings() {
  assert(_strings == nullptr, "Must 'clear()' before deleting!");
}

const char* DbgStrings::insert(const char* dbgstr) {
  const char* str = _strings->lookup(dbgstr);
  return str != nullptr ? str : _strings->insert(dbgstr);
}

bool DbgStrings::is_empty() const {
  return _strings->is_empty();
}

void DbgStrings::share(const DbgStrings &src) {
  precond(is_empty());
  clear();
  _strings = src._strings->reuse();
}

void DbgStrings::clear() {
  if (_strings->clear() == 0) {
    delete _strings;
  }
  _strings = nullptr;
}

// ----- AsmRemarkCollection ---------------------------------------------------

const char* AsmRemarkCollection::insert(uint offset, const char* remstr) {
  precond(remstr != nullptr);
  Cell* cell = new Cell { remstr, offset };
  if (is_empty()) {
    cell->prev = cell;
    cell->next = cell;
    _remarks = cell;
  } else {
    _remarks->push_back(cell);
  }
  return cell->string();
}

const char* AsmRemarkCollection::lookup(uint offset) const {
  _next = _remarks;
  return next(offset);
}

const char* AsmRemarkCollection::next(uint offset) const {
  if (_next != nullptr) {
    Cell* i = _next;
    do {
      if (i->offset == offset) {
        _next = i->next == _remarks ? nullptr : i->next;
        return i->string();
      }
      i = i->next;
    } while (i != _remarks);
    _next = nullptr;
  }
  return nullptr;
}

uint AsmRemarkCollection::clear() {
  precond(_ref_cnt > 0);
  if (--_ref_cnt > 0) {
    return _ref_cnt;
  }
  if (!is_empty()) {
    uint count = 0;
    Cell* i = _remarks;
    do {
      Cell* next = i->next;
      delete i;
      i = next;
      count++;
    } while (i != _remarks);

    log_debug(codestrings)("Clear %u asm-remark%s.", count, count == 1 ? "" : "s");
    _remarks = nullptr;
  }
  return 0; // i.e. _ref_cnt == 0
}

// ----- DbgStringCollection ---------------------------------------------------

const char* DbgStringCollection::insert(const char* dbgstr) {
  precond(dbgstr != nullptr);
  Cell* cell = new Cell { dbgstr };

  if (is_empty()) {
     cell->prev = cell;
     cell->next = cell;
     _strings = cell;
  } else {
    _strings->push_back(cell);
  }
  return cell->string();
}

const char* DbgStringCollection::lookup(const char* dbgstr) const {
  precond(dbgstr != nullptr);
  if (_strings != nullptr) {
    Cell* i = _strings;
    do {
      if (strcmp(i->string(), dbgstr) == 0) {
        return i->string();
      }
      i = i->next;
    } while (i != _strings);
  }
  return nullptr;
}

uint DbgStringCollection::clear() {
  precond(_ref_cnt > 0);
  if (--_ref_cnt > 0) {
    return _ref_cnt;
  }
  if (!is_empty()) {
    uint count = 0;
    Cell* i = _strings;
    do {
      Cell* next = i->next;
      delete i;
      i = next;
      count++;
    } while (i != _strings);

    log_debug(codestrings)("Clear %u dbg-string%s.", count, count == 1 ? "" : "s");
    _strings = nullptr;
  }
  return 0; // i.e. _ref_cnt == 0
}

#endif // not PRODUCT

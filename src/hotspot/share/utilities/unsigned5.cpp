/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.hpp"
#include "oops/array.hpp"
#include "utilities/unsigned5.inline.hpp"
#include "utilities/xmlstream.hpp"

// For the record, UNSIGNED5 was defined around 2001 and was first
// published in the initial Pack200 spec.  See:
// https://docs.oracle.com/en/java/javase/11/docs/specs/pack-spec.html
// in Section 6.1, "Encoding of Small Whole Numbers".

// Zero suppression logic (mainly useful for DebugInfo, which is zero-rich)

#define APPLY_ArrayGetSet(ARR,OFF) \
  UNSIGNED5::ArrayGetSet<ARR,OFF>

#define FOR_EACH_TEMPLATE_INSTANCE(FN) \
  FN(char*,int,APPLY_ArrayGetSet(char*,int)) \
  FN(u1*,int,APPLY_ArrayGetSet(u1*,int)) \
  FN(address,size_t,APPLY_ArrayGetSet(address,size_t)) \
  FN(Array<u1>*,int,Array<u1>::GetSetHelper) \
  /*end*/

template<typename ARR, typename OFF, typename GET>
using ZSReader = ZeroSuppressingU5::ZSReader<ARR,OFF,GET>;

template<typename ARR, typename OFF, typename SET>
using ZSWriter = ZeroSuppressingU5::ZSWriter<ARR,OFF,SET>;

template<typename ARR, typename OFF, typename GET>
uint32_t ZeroSuppressingU5::ZSReader<ARR,OFF,GET>::next_uint_uncompressing() {
  uint32_t zm = _zero_mask;
  uint32_t bc = _block_count;
  if (is_clean()) {
    uint32_t cmd = _r.next_uint();
    if (is_block_count_code(cmd)) {
      bc = decode_block_count(cmd);
      assert(bc <= MAX_BLOCK_COUNT && MAX_BLOCK_COUNT < PASSTHROUGH_BLOCK_COUNT, "");
      if (bc == 0)  bc = PASSTHROUGH_BLOCK_COUNT;
      _block_count = bc;
    } else {
      zm = decode_zero_mask(cmd);
      assert(zm != 0, "");
      _zero_mask = zm;
    }
  }
  // Execute the next step of the current command.
  assert(!is_clean(), "");
  if (zm != 0) {
    _zero_mask = zm >> 1;
    if ((zm & 1) != 0) {
      return 0;
    }
    // else fall through
  } else {
    assert(bc != 0, "");
    if ((bc + 1) > 1) {   // decrement if not passthrough
      assert(bc > 0 && bc != PASSTHROUGH_BLOCK_COUNT, "");
      _block_count = bc - 1;
    }
    // and fall through
  }
  return _r.next_uint();
}

template<typename ARR, typename OFF, typename GET>
void ZeroSuppressingU5::ZSReader<ARR,OFF,GET>::setup(ARR array, OFF limit) {
  _sticky_passthrough = false;
  _r.setup(array, limit);
  reset();
}
template<typename ARR, typename OFF, typename GET>
void ZeroSuppressingU5::ZSReader<ARR,OFF,GET>::reset() {
  _r.reset();
  set_clean_or_passthrough();
}


template<typename ARR, typename OFF, typename SET>
void ZSWriter<ARR,OFF,SET>::setup(ARR array, OFF limit) {
  _sticky_passthrough = false;
  _w.setup(array, limit);
  reset();
}

template<typename ARR, typename OFF, typename SET>
void ZSWriter<ARR,OFF,SET>::reset() {
  _w.reset();
  _suppressed_zeroes = 0;
  _zero_mask_length = 0;
  _zero_mask_start = 0;
  _zero_mask = 0;
  _block_length = 0;
  _block_start = 0;
  assert(is_clean(), "");
  set_clean_or_passthrough();
}

template<typename ARR, typename OFF, typename SET>
void ZSWriter<ARR,OFF,SET>::grow_array(ARR array, OFF limit) {
  _w.grow_array(array, limit);
}

template<typename ARR, typename OFF, typename SET>
void ZSWriter<ARR,OFF,SET>::accept_end_byte() {
  commit(false, false);
  _w.accept_end_byte();
  set_clean_or_passthrough();
}

template<typename ARR, typename OFF, typename SET>

OFF ZSWriter<ARR,OFF,SET>::advance_position(OFF start, int count) {
  ARR arr = array();
  OFF pos = start;
  int rem = count;
  while (rem > 0) {
    int len = UNSIGNED5::check_length(arr, pos, (OFF)0, SET());
    assert(len > 0, "");
    pos += len;
    rem -= 1;
  }
  return pos;
}

// extra slots beyond 32, in the uint64_t _zero_mask
#define MASK_SLOP 2

int ZSWriter_extra_sanity_checks = 1000;

template<typename ARR, typename OFF, typename SET>
bool ZSWriter<ARR,OFF,SET>::sanity_checks() {
  const int zmlen = _zero_mask_length;
  const int bklen = _block_length;
  if (is_passthrough()) {
    assert(bklen == (int)PASSTHROUGH_BLOCK_COUNT, "");
    assert(zmlen == 0, "");
    const OFF bks = _block_start;
    assert(bks >= 0 && bks <= _w.position(), "");
    return true;
  }
  assert(zmlen >= 0 && zmlen <= (int)MAX_MASK_WIDTH+MASK_SLOP, "");
  assert((bklen >= 0 && bklen <= (int)MAX_BLOCK_COUNT), "");
  // the advance_position logic is extremely expensive
  if (ZSWriter_extra_sanity_checks == 0) {
    return true;
  } else if (ZSWriter_extra_sanity_checks > 0) {
    --ZSWriter_extra_sanity_checks;
  }
  const OFF zms = (zmlen != 0) ? _zero_mask_start : _w.position();
  const OFF zme = advance_position(zms, zmlen);
  const OFF bks = (bklen != 0) ? _block_start : zms;
  const OFF bke = advance_position(bks, bklen);
  assert(zme == _w.position(), "");
  assert(bke == zms, "");
  // The writer stores three consecutive areas, always: The committed
  // part (already done), the current block being accumulated, and the
  // zero mask area.  The compression process shifts items from the
  // third area into the second, and from both latter areas into the
  // first.
  //
  //  ... X Y Z | A B C D ... | P 0 Q 0 0 R S 0 T ... |
  // (...done) bs (block...) zs                      w.pos
  //             \__ blen ___/ \_ zm(010110010...) __/
  return true;
}

template<typename ARR, typename OFF, typename SET>
void ZSWriter<ARR,OFF,SET>::digest_multiple_uints(OFF start_pos, int count) {
  assert(count >= 1 && count <= 3, "");
  uint32_t zm = 0;
  OFF pos = start_pos;
  for (int i = 0; i < count; i++) {
    if (SET()(array(), pos) == UNSIGNED5::MIN_ENCODING_BYTE) {
      zm += 1 << i;
      ++pos;
      continue;
    }
    if (i+1 == count)  break;  // no more work to do
    // compute next pos, based on data in the array:
    auto len = UNSIGNED5::check_length(array(), pos, (OFF)0, SET());
    if (len == 0)  break;
    pos += len;
  }
  digest_uint_mask(zm, count, start_pos);
}

template<typename ARR, typename OFF, typename SET>
void ZSWriter<ARR,OFF,SET>::digest_uint_mask(uint32_t more_zm, int more_zm_len, OFF start_pos) {
  if (is_passthrough()) {
    return;  // no more compression, but it's OK to keep accumulating
  }
  int blen = _block_length;
  if (blen > (int)GIVE_UP_AFTER) {
    commit(false, true);
    return;
  }
  int zml = _zero_mask_length;
  assert(zml >= 0 && zml < (int)MAX_MASK_WIDTH+MASK_SLOP, "");
  if (zml == 0) {
    assert(_zero_mask == 0, "");
    if (blen != 0) {
      if (more_zm == 0) {
        _block_length = blen + more_zm_len;
        assert(sanity_checks(), "");
        return;  // do not start a mask here
      }
      while ((more_zm & 1) == 0) {
        // transfer any leading non-zero values into the block
        more_zm >>= 1;
        more_zm_len -= 1;
        blen++;
      }
      _block_length = blen;
    }
    _zero_mask_start = start_pos;
    _zero_mask = 0;  // initialize the mask
  }
  // add to zero mask (or maybe start a new one)
  _zero_mask |= (uint64_t)more_zm << zml;
  _zero_mask_length = (zml += more_zm_len);
  assert(sanity_checks(), "");
  if (zml >= (int)MAX_MASK_WIDTH) {
    // A full mask means that we can finalize some decisions, with the
    // result of removing some items from the zero mask area.
    drain_zero_mask(MAX_MASK_WIDTH-1);
  }
}

template<typename ARR, typename OFF, typename SET>
void ZSWriter<ARR,OFF,SET>::expand_current_block(int trim) {
  // current block (middle area) takes leading items from zero mask area
  assert(trim > 0 && trim <= _zero_mask_length, "");
  assert(have_zero_mask(), "");
  if (_block_length == 0) {
    _block_start = _zero_mask_start;
  }
  _block_length += trim;
  _zero_mask_length -= trim; // remove trimmed items from zero mask
  _zero_mask_start = advance_position(_zero_mask_start, trim);
  assert(trim < BitsPerLong && sizeof(_zero_mask) == BitsPerLong/BitsPerByte, "");
  _zero_mask >>= trim;       // shift out the zero-tracking data also
  assert(sanity_checks(), "");
}

template<typename ARR, typename OFF, typename SET>
void ZSWriter<ARR,OFF,SET>::drain_zero_mask(int target_zero_mask_length) {
  // Drain the zero mask area until it is at most the target size.
  const int zml = _zero_mask_length;
  if (zml <= target_zero_mask_length)  return;
  const int blen = _block_length;
  const uint32_t bcmd = encode_block_count(blen);
  const int bcmd_size = UNSIGNED5::encoded_length(bcmd);
  uint32_t zm = (uint32_t) _zero_mask;
  uint32_t best_zm = 0;
  if (is_valid_zero_mask(zm)) {
    const int RESTART_BLOCK_MODE = 1;
    int min_profit = (blen == 0) ? 0 : RESTART_BLOCK_MODE + bcmd_size;
    // If we are not in block mode, even a mask with zero profit
    // (that is, a wash) is enough to keep us in mask mode.  In
    // any given run of multiple masks, the first mask has enough
    // profit to pay for breaking out of block mode, plus the
    // eventual one byte required to start a new block mode after
    // a run of masks.  Therefore, all subsequent masks can
    // tolerate zero profit, as long as there is no loss.  A more
    // subtle algorithm would track the profit across a series of
    // mask commands, and trade off the total size of the masks
    // against the total profit.  The benefit would be small in
    // many cases, so it's probably not worth extra complexity.
    best_zm = best_zero_mask(zm, min_profit);
  }

  if (best_zm == 0) {
    // There are too many leading non-zero items, or a zero mask
    // that is not dense enough to be profitable.  The remedy is
    // to skip to the next zero, if any, in the mask.  Because we
    // must skip at least one mask position, be sure to count the
    // LSB as unset (hence the <=1 and &~1).
    int trim = (zm <= 1) ? zml : count_trailing_zeros(zm & ~1);
    expand_current_block(trim);  // absorb trimmed items into current block
    assert(_zero_mask_length == zml - trim, "");
  } else {
    do_compression(best_zm);
  }
  assert(sanity_checks(), "");

  assert(_zero_mask_length < zml, "");  // must make progress
  if (_zero_mask_length > target_zero_mask_length) {
    // go around again if that is needed to hit the target 
    drain_zero_mask(target_zero_mask_length);
  }
}

template<typename ARR, typename OFF, typename SET>
void ZSWriter<ARR,OFF,SET>::do_compression(uint32_t best_zm) {
  // Act on the chosen zero mask.
  assert(best_zm != 0, "");  // must have something to compress
  assert((best_zm & _zero_mask) == best_zm, "subset mask");
  assert(sanity_checks(), "");

  // Split _zero_mask right away:
  const int best_zm_length = zero_mask_length(best_zm);
  const int rest_zm_length = _zero_mask_length - best_zm_length;
  const uint64_t rest_zm = _zero_mask >> best_zm_length;
  assert(best_zm_length >  0 && best_zm_length <= (int)MAX_MASK_WIDTH, "");
  assert(rest_zm_length >= 0 && rest_zm_length < (int)MAX_MASK_WIDTH+MASK_SLOP, "");

  // Old contents of _w:
  //  ... X Y Z | A B C D ... | P 0 Q 0 0 R S 0 T ... |
  // (...done) bs (block...) zs                      w.pos
  //             \__ blen ___/ \_ zm(010110010...) __/
  //
  // New contents of _w:
  //  ... X Y Z | bh(blen) : A B C D ... | zm(01011) : P Q | &
  //  (...done  ...done   ...done       ...done     ...done)
  //          & | R S 0 T ... |
  // (...done) zs            w.pos
  //     blen=0  \_zm(0010...)_/
  //
  // This complicated transformation is the only way that zeroes
  // are eliminated.  It only takes place if there is a profit.
  // That is, the number of bytes stored in _w.array() must not
  // increase.  A side benefit of the profitability condition is
  // that the transformation does not require growing the array.
  //
  // The zero mask is encoded and placed in the buffer, and it must
  // not be too long.  Any increases in length must be "paid for" by
  // removing zero items from between the PQRST, in effect shrinking
  // them to single bits in the zero mask command zm(01011).  The
  // other bits in the zero mask command are a "steering tax" required
  // to place the zeroes correctly between the non-zero items.
  //
  // The bh(blen), which also must not be too long, is inserted before
  // the ABCD.  But if ABCD is empty (blen=0) then the whole block
  // command |bh(blen):ABCD| is suppressed.  If ABCD is a one or two
  // items A or AB, then the block command is |bh(1):A| or |bh(2):AB|,
  // and so on.  Blocks up to length 11 require only one byte for a
  // header, followed immediately by the encoded items of the block.
  // That may be immediately followed by a mask command with its data
  // PQ, or else a null marker, or the end of the data.

  // Buffer for the bytes of the zero mask area (e.g., P0Q00RS0T...).
  uint8_t buffer[(MAX_MASK_WIDTH+MASK_SLOP+1) * UNSIGNED5::MAX_LENGTH];

  // Copy out all items covered in the zero mask window (and beyond).
  // We absolutely need to do this if bcmd_size>1 later on.
  const OFF zms = _zero_mask_start;
  const OFF zme = _w.position();  // could include 1-2 items beyond the zm
  const ARR array  = _w.array();
  const OFF alimit = _w.limit();
  OFF zmp = zms;  // source scan pointer
  OFF bufp = 0;   // destination fill pointer
  int zm1_count = 0, zm1_zero_count = 0;
  for (uint32_t zm = best_zm; zm != 0; zm >>= 1) {
    if ((zm & 1) != 0) {
      // this is the only compression, the point of all the bookkeeping
      assert(SET()(array, zmp) == ZERO_ENCODING, "");
      zm1_zero_count++;
      zmp++;
    } else {
      // where is Copy::disjoint_bytes?
      const int len = UNSIGNED5::check_length(array, zmp, alimit, SET());
      Copy::conjoint_jbytes(&array[zmp], &buffer[bufp], len);
      zmp += len;
      bufp += len;
    }
    assert(bufp < (OFF) sizeof(buffer), "");
    zm1_count += 1;
  }
  assert(zm1_count == best_zm_length, "");
  _suppressed_zeroes += zm1_zero_count;
  const OFF buffer_zm1_size = bufp;         // size of payload for best_zm
  const OFF buffer_zm2_size = (zme - zmp);  // size of payload for rest_zm
  if (buffer_zm2_size != 0) {
    assert(zme > zmp, "");
    Copy::conjoint_jbytes(&array[zmp], &buffer[buffer_zm1_size], buffer_zm2_size);
  }
  assert(buffer_zm1_size + buffer_zm2_size < (OFF) sizeof(buffer), "");

  // Temporarily remove the buffered data, while we close the block:
  _w.set_position(zms);
  _zero_mask_length = 0;

  // Close off the current block (if any) with a definite size.
  if (have_current_block()) {
    emit_block_command(false);
  }

  emit_zero_mask_command(best_zm);
  assert(_w.array() == array, "no growth please");

  const OFF w_zm1_start = _w.position();
  Copy::conjoint_jbytes(&buffer[0], &array[w_zm1_start], buffer_zm1_size + buffer_zm2_size);
  _w.set_position(w_zm1_start + buffer_zm1_size + buffer_zm2_size);
  // The copy also pasted, directly after the zero-mask command, any
  // unused zero mask area bytes (buffer_zm2_size).  At this point,
  // the executed zero mask command (zmcmd) is committed, there is
  // no current block area, and the new zero mask area might have
  // something in it (if buffer_zm2_size>0).
  _zero_mask_start = w_zm1_start + buffer_zm1_size;
  _zero_mask_length = rest_zm_length;
  _zero_mask = rest_zm;
  assert(sanity_checks(), "");
}

template<typename ARR, typename OFF, typename SET>
void ZSWriter<ARR,OFF,SET>::emit_block_command(bool use_indefinite_length) {
  assert(!have_zero_mask(), "");
  assert(have_current_block(), "");
  assert(sanity_checks(), "");
  // Insert non-empty block command, after shifting the payloads.
  const uint32_t bcmd = encode_block_count(use_indefinite_length ? 0 : _block_length);
  const int bcmd_size = UNSIGNED5::encoded_length(bcmd);
  // Note: Block copy bypasses the GET functor, so it fails for
  // Array<u1>.  To fix this, the GET functor should return a
  // reference, so we can take an address.
  const OFF bs = _block_start;
  const OFF be = _w.position();
  const ARR a = _w.array();
  Copy::conjoint_jbytes(&a[bs], &a[bs + bcmd_size], be - bs);
  _w.set_position(be + bcmd_size);
  OFF wp = bs;
  UNSIGNED5::write_uint(bcmd, a, wp, (OFF)0, SET());
  assert(wp == bs + bcmd_size, "");
  _block_start = 0;
  _block_length = 0;
  assert(is_clean(), "");
  if (use_indefinite_length) {
    // After an indefinite header, the only thing we can do after this
    // is pass through additional items uncompressed.  We could also
    // issue a null byte and restart everything.  But for now, we are
    // committed to passthrough mode.  This can be a temporary
    // condition.  It is a choice made by the compressor, not the user.
    // Therefore, we do not set the sticky bit.
    _block_length = PASSTHROUGH_BLOCK_COUNT;  // not set_passthrough
    assert(!_sticky_passthrough, "");  // this is not a sticky state
  }
}

template<typename ARR, typename OFF, typename SET>
void ZSWriter<ARR,OFF,SET>::emit_zero_mask_command(uint32_t best_zm) {
  assert(is_clean(), "");
  assert(sanity_checks(), "");
  // Emit the zero mask command, including its buffered payload data.
  uint32_t zmcmd = encode_zero_mask(best_zm);
  _w.accept_uint(zmcmd);
}

template<typename ARR, typename OFF, typename SET>
void ZSWriter<ARR,OFF,SET>::commit(bool require_clean,
                                   bool require_passthrough) {
  assert(!require_clean || !require_passthrough, "");  // not both
  if (is_passthrough()) {   // already passing through uncompressed
    assert(!require_clean, "");
    return;
  }
  if (is_clean()) {   // already clean
    if (require_passthrough) {   // need an explicit command
      _w.accept_uint(encode_block_count(0));
      _block_length = PASSTHROUGH_BLOCK_COUNT;  // not set_passthrough
      assert(!_sticky_passthrough, "");  // this is not a sticky state
    }
    return;
  }
  // finalize compression decisions
  drain_zero_mask(0);
  assert(!have_zero_mask(), "");
  if (have_current_block()) {
    bool use_indefinite_length = !require_clean;
    emit_block_command(use_indefinite_length);
  }
  assert(!require_clean || is_clean(), "");
  assert(!require_passthrough || is_passthrough(), "");
}

template<typename ARR, typename OFF, typename GET>
void ZSReader<ARR,OFF,GET>::print_on(outputStream* st) {
  UNSIGNED5::Reader<ARR,OFF,GET> r(_r.array(), _r.limit());
  OFF pos = _r.position();
  st->print("CR");
  if (is_passthrough()) {
    st->print("(PT)");
    r.print_on(st);
    return;
  }
  st->print("[");
  int command_count = 0, payload_count = 0, null_count = 0;
  for (;;) {
    if (try_skip_end_byte()) {
      null_count++;
      st->print(" null");
      if (_r.limit() == 0)  break;
      continue;
    }
    if (!r.has_next())  break;
    command_count++;
    uint32_t cmd = r.next_uint();
    int cmdlen = UNSIGNED5::encoded_length(cmd);
    uint32_t bc = 0, zm = 0;
    if (is_block_count_code(cmd)) {
      bc = decode_block_count(cmd);
      assert(bc <= MAX_BLOCK_COUNT && MAX_BLOCK_COUNT < PASSTHROUGH_BLOCK_COUNT, "");
      if (bc == 0) {
        bc = PASSTHROUGH_BLOCK_COUNT;
        st->print(" [END]");
      } else {
        st->print(" [B%d]", bc);
      }
    } else {
      zm = decode_zero_mask(cmd);
      st->print(" [ZM%x%s]", zm, is_valid_zero_mask(cmd) ? "" : "*");
    }
    st->print("%x", cmd);
    if (cmdlen > 1)  st->print(":%d", cmdlen);
    while (bc != 0) {
      if (!r.has_next())  break;
      st->print(" %d", r.next_uint());
      ++payload_count;
      --bc;
    }
    while (zm != 0) {
      if ((zm & 1) != 0) {
        st->print(" .");
      } else if (!r.has_next()) {
        break;
      } else {
        st->print(" %d", r.next_uint());
        ++payload_count;
      }
      zm >>= 1;
    }
  }
  st->print(" ] (commands=%d/payloads=%d/length=%d/nulls=%d)",
            command_count,
            payload_count,
            (int)r.position(),
            null_count);
  if (is_passthrough()) {
    st->print(" (state=PT)");
  } else {
    st->print(" (state=BC%d,ZM%x)", (int)_block_count, (int)_zero_mask);
  }
  st->cr();
}

template<typename ARR, typename OFF, typename SET>
void ZSWriter<ARR,OFF,SET>::print_on(outputStream* st) {
  ZSReader<ARR,OFF,SET> r(_w.array(), _w.position());
  if (is_passthrough())  r.set_passthrough();
  st->print("CW[");
  if (is_clean())  st->print("clean");
  if (is_passthrough())  st->print("passthrough");
  if (have_current_block() && !is_passthrough()) {
    st->print("bk=@%d[%d]", (int)_block_start, (int)_block_length);
  }
  if (have_zero_mask()) {
    if (have_current_block())  st->print(";");
    st->print("zm=@%d[%d]%x", (int)_zero_mask_start, (int)_zero_mask_length, (int)_zero_mask);
  }
  st->print("]:");
  r.print_on(st);
}

#define READER_WRITER_INSTANCES(ARR,OFF,GET) \
  template class UNSIGNED5::Reader<ARR,OFF,GET>; \
  template class UNSIGNED5::Writer<ARR,OFF,GET>; \
  template class ZeroSuppressingU5::ZSReader<ARR,OFF,GET>; \
  template class ZeroSuppressingU5::ZSWriter<ARR,OFF,GET>; \
  /*end*/

FOR_EACH_TEMPLATE_INSTANCE(READER_WRITER_INSTANCES)
#undef READER_WRITER_INSTANCES


template void UNSIGNED5::Reader<char*,int>::
print_on(outputStream* st, int count, const char* left, const char* right);
template void UNSIGNED5::Reader<u1*,int>::
print_on(outputStream* st, int count, const char* left, const char* right);
template void UNSIGNED5::Reader<address,size_t>::
print_on(outputStream* st, int count, const char* left, const char* right);

template<typename ARR, typename OFF, typename GET>
void UNSIGNED5::Statistics::
record_one_stream(ARR array, OFF limit, GET get,
                  size_t original_size,
                  size_t* pair_counts,
                  size_t suppressed_zeroes) {
  size_t csize = limit;
  _stream_count += 1;
  _compressed_size += csize;
  _suppressed_zeroes += suppressed_zeroes;
  if (pair_counts != nullptr) {
    for (int pc = 1; pc <= 3; pc++) {
      _pair_counts[pc-1] += pair_counts[pc-1];
    }
  }
  if (original_size != 0) {
    _original_size_count += 1;
    _original_size += original_size;
  }
  auto r = Reader<ARR,OFF,GET>(array, limit);
  size_t lastp = 0;
  while (r.position() < limit) {
    if (r.try_skip_end_byte()) {
      _null_count += 1;
      lastp = r.position();
    } else if (r.try_skip(1)) {
      size_t nextp = r.position();
      int len = nextp - lastp;
      assert(len >= 1 && len <= MAX_LENGTH, "");
      lastp = nextp;
      uint8_t lastb = GET()(array, lastp-1);
      assert(lastb >= 0 && (lastb < X+L || len == MAX_LENGTH), "");
      unsigned int bits = lastb - X;  // unbias the byte to more accurately assess its width
      size_t sigi = BitsPerByte * (len-1);
      while (bits != 0) { bits >>= 1; sigi += 1; }
      assert(sigi >= 0 && sigi < sizeof(_bit_width_counts)/sizeof(_bit_width_counts[0]), "");
      _bit_width_counts[sigi] += 1;
      _uint_count += 1;
    } else {
      assert(false, "");
      break;
    }
  }
}

template void UNSIGNED5::Statistics::
record_one_stream(char* array, int limit,
                  UNSIGNED5::ArrayGetSet<char*,int> get,
                  size_t original_size,
                  size_t* pair_counts,
                  size_t suppressed_zeroes);
template void UNSIGNED5::Statistics::
record_one_stream(u1* array, int limit,
                  UNSIGNED5::ArrayGetSet<u1*,int> get,
                  size_t original_size,
                  size_t* pair_counts,
                  size_t suppressed_zeroes);
template void UNSIGNED5::Statistics::
record_one_stream(address array, size_t limit,
                  UNSIGNED5::ArrayGetSet<address,size_t> get,
                  size_t original_size,
                  size_t* pair_counts,
                  size_t suppressed_zeroes);
template void UNSIGNED5::Statistics::
record_one_stream(Array<u1>* array, int limit,
                  Array<u1>::GetSetHelper get,
                  size_t original_size,
                  size_t* pair_counts,
                  size_t suppressed_zeroes);


UNSIGNED5::Statistics UNSIGNED5::Statistics::_table[UNSIGNED5::Statistics::LIMIT];

void UNSIGNED5::Statistics::print_statistics() {
  assert(tty != nullptr, "");
  if (xtty != nullptr)  xtty->head("compression_statistics");
  for (int i = 0; i < LIMIT; i++) {
    if (_table[i]._stream_count == 0)  continue;
    _table[i].print_on(tty);
  }
  if (xtty != nullptr)  xtty->tail("compression_statistics");
}

void UNSIGNED5::Statistics::print_on(outputStream* st) {
  Kind kind = Kind(this - &_table[0]);
  static const char* KS_TAB[LIMIT*2] = {
    #define DECLARE_KS(AB,CD) #AB, #CD,
    FOR_EACH_Statistics_Kind(DECLARE_KS)
    #undef DECLARE_KS
  };
  const char* *kdescs = &KS_TAB[kind >= UK && kind < LIMIT ? 2*kind : 0];
  const char* kd = kdescs[0];
  const bool have_pairs = ((_pair_counts[0] | _pair_counts[1] | _pair_counts[2]) != 0);
  const bool have_zsupp = (_suppressed_zeroes != 0);
  const double num_strm = _stream_count;
  const double num_byte = _compressed_size;
  const double num_uint = _uint_count;
  const double num_item = _uint_count + _null_count;
  const double num_uint_pre_transform = (num_uint + _suppressed_zeroes * 0.80 +
                                         (_pair_counts[0] - _pair_counts[2]));
  // Zero suppression replaces zero bytes by zero bits, so 1 zero byte
  // input is replaced by a bitmask bit (1/8 of a byte) output.
  // Sadly, steering information is required to recover bit positions.
  // Adding that back in 1 zero byte input is replaced by about 1/5
  // byte of bitmask plus steering information; so 1 in => 0.20 out.
  st->print_cr("%s: stream %s %d count, "
               "average size/uint/nulls %.2f / %.2f / %.2f",
               kd, kdescs[1], (int)_stream_count,
               _compressed_size/num_strm, _uint_count/num_strm, _null_count/num_strm);
  st->print_cr("%s: total size/uint/nulls " SIZE_FORMAT " / %d / %d",
               kd, (size_t)_compressed_size, (int)_uint_count, (int)_null_count);
  if (have_pairs | have_zsupp) {
    st->print_cr("%s: efficiency %.2f bytes/uint in, %.2f bytes/uint out (%s%s%s)",
                 kd,
                 (_compressed_size - _null_count)/num_uint_pre_transform,
                 (_compressed_size - _null_count)/num_uint,
                 have_pairs ? "uint pairing" : "",
                 (have_pairs & have_zsupp) ? ", " : "",
                 have_zsupp ? "zero suppression" : "");
  } else {
    st->print_cr("%s: efficiency %.2f bytes/uint (no uint transforms)",
                 kd, (_compressed_size - _null_count)/num_uint);
  }
  switch (kind) {
  case FI:
    st->print_cr("%s: -XX:FICompressionOptions=%d", kd, FICompressionOptions);
    break;
  case LT:
    st->print_cr("%s: -XX:LTCompressionOptions=%d", kd, LTCompressionOptions);
    break;
  case DI:
    st->print_cr("%s: -XX:DICompressionOptions=%d", kd, DICompressionOptions);
    st->print("%s: code counts", kd);
    {
      extern size_t* report_di_code_counts(int& length);
      int num_code_counts = 0;
      size_t* code_counts = report_di_code_counts(num_code_counts);
      for (int i = 0; i < num_code_counts; i++) {
        st->print(" %d", (int)code_counts[i]);
      }
    }
    st->cr();
    break;
  default: break;
  }
  if (_null_count != 0) {
    st->print_cr("%s: nulls %.2f per stream, %d total, %.2f%% of bytes",
                 kd, _null_count / num_strm, (int)_null_count, 
                 100.0 * _null_count / num_byte);
  }
  const size_t zero_count = _bit_width_counts[0];  // only zero is zero bits wide
  st->print_cr("%s: zeroes %.2f per stream, %d bytes total, %.2f%% / %.2f%% of ints/bytes",
               kd, zero_count / num_strm, (int)zero_count,
               100.0 * zero_count / num_uint,
               100.0 * zero_count / num_byte);
  if (have_pairs != 0) {
    st->print_cr("%s: pairs in 1/2/3 words, %d / %d / %d total,"
                 " %.2f%% / %.2f%% / %.2f%% of ints",
                 kd,
                 (int)_pair_counts[0], (int)_pair_counts[1], (int)_pair_counts[2],
                 100.0*_pair_counts[0]/num_uint,
                 200.0*_pair_counts[1]/num_uint,
                 300.0*_pair_counts[2]/num_uint);
  }
  if (_suppressed_zeroes != 0) {
    st->print_cr("%s: suppressed zeroes %d", kd, (int)_suppressed_zeroes);
  }
  if (_original_size_count != 0) {
    st->print_cr("%s: original size average %.2f total %d", kd,
                 (double)_original_size / _original_size_count, (int)_original_size);
  }

  const int NBWC = sizeof(_bit_width_counts)/sizeof(_bit_width_counts[0]);
  int max_bwc = 0;
  size_t sum_count = 0, sum_size = 0;
  int i;
  for (i = 0; i < NBWC; i++) {
    double est_bytes = ((i == 0 ? 1 : i) + 7) / 8 + ((i & 7) == 7 && i < 32 ? 0.5 : 0);
    size_t bwc = _bit_width_counts[i];
    if (bwc == 0)  continue;
    sum_count += bwc;
    sum_size += bwc * est_bytes;  // this is an estimate
    max_bwc = i;
  }
  tty->print_cr("%s: bw MDF/CDF/CSZ   count bit-width histogram "
                "count/size %d / ~~%d",
                kd, (int)sum_count, (int)sum_size);
  const double total_count = sum_count;
  const double total_size  = sum_size;
  sum_count = sum_size = 0;  // reset for another go-around
  for (i = 0; i <= max_bwc; i++) {
    double est_bytes = ((i == 0 ? 1 : i) + 7) / 8 + ((i & 7) == 7 && i < 32 ? 0.5 : 0);
    size_t bwc = _bit_width_counts[i];
    sum_count += bwc;
    double mdf = bwc       / total_count; // mass density function (of counts)
    double cdf = sum_count / total_count; // cumulative density function
    sum_size += bwc * est_bytes;          // this is an estimate
    double csz = sum_size / total_size;   // CDF of estimated byte-size
    const char STARS[] = "**************************************************";
    tty->print_cr("%s: %-2d%5.2f%3.0f%3.0f %8d %.*s",
                  kd, i,
                  100*mdf, 100*cdf, 100*csz,
                  (int)bwc,
                  (int)(cdf*(sizeof(STARS)-1)+0.5), STARS);
  }
}


PRAGMA_DIAG_PUSH
PRAGMA_FORMAT_NONLITERAL_IGNORED

// For debugging, even in product builds (see debug.cpp).
template<typename ARR, typename OFF, typename GET>
void UNSIGNED5::Reader<ARR,OFF,GET>::
print_on(outputStream* st, int count,
         const char* left,   // "U5: ["
         const char* right   // "] (values=%d/length=%d)\n"
         ) {
  OFF original_position = _position;  // save for restore at end
  if (left == nullptr)   left = "U5: [";
  if (right == nullptr)  right = "] (values=%d/length=%d)\n";
  st->print("%s", left);
  OFF window_start = 0;   // where we will start printing in the stream
  if (original_position > 0 && count > 0) {
    // Advance left_part to skip stuff we don't want to print.
    int window_size = 0;
    int window_skip = 0;
    Reader pr(array(), original_position);
    while (pr.try_skip(1) || pr.try_skip_end_byte()) {
      if (window_size > count) {
        window_skip += 1;
      } else {
        window_size += 1;
      }
    }
    pr.set_position(0);
    while (window_skip > 0 && (pr.try_skip(1) || pr.try_skip_end_byte())) {
      --window_skip;
    }
    window_start = pr.position();
  }
  bool is_first = true;
  if (window_start != 0 && window_start != original_position) {
    st->print("...[@%d]", (int)window_start);
    _position = window_start;
    is_first = false;
  }
  uint32_t null_count = 0, uint_count = 0;
  for (;;) {
    if (count >= 0 && uint_count + null_count >= (uint32_t)count)  break;
    if (is_first) { is_first = false; } else { st->print(" "); }
    if (_position == original_position && _position != 0) {
      st->print("[pos@%d] ", (int)_position);
    }
    if (!has_next()) {
      if ((_limit == 0 || _position < _limit) && GET()(_array, _position) == 0) {
        st->print("null");
        ++_position;  // skip null byte
        ++null_count;
        if (_limit != 0)  continue;  // keep going to explicit limit
        if (_position < original_position)  continue;
      }
      break;
    }
    ++uint_count;
    uint32_t value = next_uint();
    st->print("%d", value);
  }
  st->print(right,
            // these arguments may or may not be used in the format string:
            (int)uint_count,
            (int)position());
  _position = original_position;  // restore at end
}

PRAGMA_DIAG_POP

// Explicit instantiation for supported types.
template void UNSIGNED5::Reader<char*,int>::
print_on(outputStream* st, int count, const char* left, const char* right);
template void UNSIGNED5::Reader<u1*,int>::
print_on(outputStream* st, int count, const char* left, const char* right);
template void UNSIGNED5::Reader<address,size_t>::
print_on(outputStream* st, int count, const char* left, const char* right);

/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "memory/allocation.hpp"
#include "runtime/os.hpp"
#include "utilities/unsigned5.inline.hpp"
#include "unittest.hpp"

TEST_VM(unsigned5, max_encoded_in_length) {
  int maxlen = UNSIGNED5::MAX_LENGTH;
  EXPECT_EQ(maxlen, 5);
  for (int i = 0; i <= 190; i++) {
    uint32_t interesting = i;
    EXPECT_EQ(UNSIGNED5::encoded_length(interesting), 1);
    EXPECT_EQ(UNSIGNED5::encoded_length(~interesting), maxlen);
  }
  for (int len = 1; len <= maxlen; len++) {
    uint32_t interesting = UNSIGNED5::max_encoded_in_length(len);
    EXPECT_EQ(UNSIGNED5::encoded_length(interesting-1), len);
    EXPECT_EQ(UNSIGNED5::encoded_length(interesting), len);
    if (len < 5) {
      EXPECT_EQ(UNSIGNED5::encoded_length(interesting+1), len+1);
      EXPECT_EQ(UNSIGNED5::encoded_length(interesting*2), len+1);
    }
    const int offset = -123;
    const int good_limit = offset + len;
    const int bad_limit  = good_limit - 1;
    EXPECT_TRUE(UNSIGNED5::fits_in_limit(interesting, offset, good_limit));
    EXPECT_TRUE(!UNSIGNED5::fits_in_limit(interesting, offset, bad_limit));
  }
}

// Call FN on a nice list of "interesting" uint32_t values to encode/decode.
// For each length in [1..5], the maximum encodable value of that
// length is "interesting", as are one more and one less than that
// value.  For each nybble (aligned 4-bit field) of a uint32_t, each
// possible value (in [0..15]) stored in that nybble is "interesting".
// Also "interesting" are some other values created by perturbing
// lower bits of that nybble-bearing number, by subtracting a power
// of -7 (up to -7^7).  That makes just over 1000 distinct numbers.
//
// Calls to this function are repeatable, so you can call it to pack
// an output array, and then call it again to read an input array
// verifying that the retrieved values match the stored ones.
template<typename FN>
inline int enumerate_cases(FN fn) {
  // boundary values around the maximum encoded in each byte-length
  for (int len = 1; len <= 5; len++) {
    uint32_t interesting = UNSIGNED5::max_encoded_in_length(len);
    int res = fn(interesting-1);
    if (res)  return res;
    res = fn(interesting);
    if (res)  return res;
    if (interesting < (uint32_t)-1) {
      res = fn(interesting+1);
      if (res)  return res;
    }
  }
  // for each nybble, for each value in the nybble
  for (uint32_t npos = 0; npos < 32; npos += 4) {
    for (uint32_t nval = 0; nval <= 15; nval++) {
      uint32_t interesting = nval << npos;
      int res = fn(interesting);
      if (res)  return res;
      // mix in some crazy-looking values: powers of -7 to -7^7
      for (int pon7 = 1; pon7 < 1000000; pon7 *= -7) {
        uint32_t interesting2 = interesting - pon7;
        res = fn(interesting2);
        if (res)  return res;
      }
    }
  }
  return 0;
}

TEST_VM(unsigned5, transcode_single) {
  const int limit = UNSIGNED5::MAX_LENGTH;
  u_char buffer[limit + 1];
  auto each_case = [&](uint32_t value) -> uint32_t {
    //printf("case %08X len=%d\n", value, UNSIGNED5::encoded_length(value));
    int offset = 0;
    UNSIGNED5::write_uint(value, buffer, offset, limit);
    int length = offset;
    EXPECT_TRUE(length <= UNSIGNED5::MAX_LENGTH);
    EXPECT_EQ(length, UNSIGNED5::encoded_length(value)) << "for value=" << value;
    buffer[length] = 0;
    offset = 0;
    uint32_t check = UNSIGNED5::read_uint(buffer, offset, limit);
    EXPECT_EQ(offset, length) << "for value=" << value;
    EXPECT_EQ(value, check);
    return 0;
  };
  auto z = enumerate_cases(each_case);
  EXPECT_TRUE(!z);
}

static int count_cases() {
  int case_count = 0;
  auto inc_case_count = [&](uint32_t){ ++case_count; return 0; };
  enumerate_cases(inc_case_count);
  return case_count;
}

TEST_VM(unsigned5, transcode_multiple) {
  int case_count = count_cases();
  const int limit = 200;
  ASSERT_TRUE(limit < case_count*UNSIGNED5::MAX_LENGTH);
  u_char buffer[limit + 1];
  //printf("%d cases total\n", case_count);  //1166 cases total
  for (int sublimit = limit - 20; sublimit < limit; sublimit++) {
    int offset = 0;
    int count = 0;
    // write each number into an array
    auto write_case = [&](uint32_t value) -> uint32_t {
      if (!UNSIGNED5::fits_in_limit(value, offset, sublimit))
        return value|1;
      UNSIGNED5::write_uint(value, buffer, offset, sublimit);
      count++;
      return 0;
    };
    auto done = enumerate_cases(write_case);
    EXPECT_TRUE(done) << "must have hit the sublimit";
    EXPECT_TRUE(count < case_count);
    int length = offset;
    EXPECT_TRUE(length <= sublimit && length + UNSIGNED5::MAX_LENGTH > sublimit)
           << "length=" << length << " sublimit=" << sublimit;
    for (int i = length; i <= sublimit; i++) {
      buffer[i] = 0;
    }
    if (sublimit == limit-1) {
      UNSIGNED5::print_count(case_count + 1, &buffer[0], sublimit);
    }
    //printf("encoded %d values in %d bytes: [[%s]]\n", count, length, buffer);
    // now read it all back
    offset = 0;
    int count2 = 0;
    auto read_back_case = [&](uint32_t value) -> uint32_t {
      int clen = UNSIGNED5::check_length(buffer, offset, sublimit);
      if (clen == 0)  return value|1;
      EXPECT_EQ(clen, UNSIGNED5::encoded_length(value));
      int begin = offset;
      uint32_t check = UNSIGNED5::read_uint(buffer, offset, sublimit);
      EXPECT_EQ(offset, begin + clen);
      EXPECT_EQ(value, check);
      count2++;
      return 0;
    };
    auto done2 = enumerate_cases(read_back_case);
    EXPECT_EQ(done, done2);
    EXPECT_EQ(count, count2);
    EXPECT_EQ(offset, length);
  }
}

inline void init_ints(int len, int* ints) {
  for (int i = 0; i < len; i++) {
    ints[i] = (i * ((i%3) ? i : 1001)) ^ -((i % 11) & 1);
  }
}

struct MyReaderHelper {
  uint8_t operator()(char* a, int i) const { return a[i]; }
};
using MyReader = UNSIGNED5::Reader<char*, int, MyReaderHelper>;

  template<typename W, typename R>
  class TranscodeTest {  // into writer and back out of reader
    W w;
    R r;
  };

TEST_VM(unsigned5, reader) {
  const int LEN = 100;  // we will test larger workloads with ZSWriter below
  int ints[LEN];
  init_ints(LEN, ints);
  int i;
  UNSIGNED5::Sizer<> szr;
  for (i = 0; i < LEN; i++) {
    szr.accept_uint(ints[i]);
  }
  //printf("count=%d, size=%d\n", szr.count(), szr.position());
  char buf[LEN * UNSIGNED5::MAX_LENGTH + 1];
  int buflen;
  {
    int pos = 0;
    for (int i = 0; i < LEN; i++) {
      UNSIGNED5::write_uint(ints[i], buf, pos, 0);
    }
    EXPECT_TRUE(pos+1 < (int)sizeof(buf)) << pos;
    buflen = pos;
    buf[buflen] = 0;
  }
  EXPECT_EQ((int)szr.position(), buflen);
  MyReader r1(buf);
  i = 0;
  while (r1.has_next()) {
    int x = r1.next_uint();
    int y = ints[i++];
    ASSERT_EQ(x, y) << i;
    if (i < LEN - 20 && (i & 15) == 3) {
      int skip = i % 10;
      int actual = r1.try_skip(skip);
      ASSERT_EQ(skip, actual) << i;
      i += skip;
    }
  }
  ASSERT_EQ(i, LEN);
  //MyReader r2(buf, buflen / 2);
  auto r2 = UNSIGNED5::Reader<const char*,size_t>(buf, buflen / 2);
  i = 0;
  while (r2.has_next()) {
    int x = r2.next_uint();
    int y = ints[i++];
    ASSERT_EQ(x, y) << i;
  }
  ASSERT_TRUE(i < LEN);

  // copy from readers to writers
  union { int i; char c[sizeof(int)]; } no_buffer;
  no_buffer.i = -1;
  char* buf0 = no_buffer.c;
  UNSIGNED5::Reader<char*,int> r3;
  UNSIGNED5::Writer<char*,int> w3;
  ZeroSuppressingU5::ZSReader<char*,int> zsr3;
  ZeroSuppressingU5::ZSWriter<char*,int> zsw3;
  for (int which_rw = 0; which_rw <= 3; which_rw++) {
    const bool zsw = ((which_rw & 1) != 0), zsr = ((which_rw & 2) != 0);
    r3.setup(buf);
    w3.setup(buf0, 0);
    zsr3.setup(buf);
    zsr3.set_passthrough();  // should behave like a vanilla reader
    zsw3.setup(buf0, 0);
    zsw3.set_passthrough();  // should behave like a vanilla writer
    auto array_grow = [&](int){
      auto oal = zsw ? zsw3.limit() : w3.limit();
      auto nal = oal + 10;
      //printf("growing array from %d to %d\n", oal, nal);
      if (zsw)
        zsw3.grow_array(new char[nal + 1], nal);
      else
        w3.grow_array(new char[nal + 1], nal);
    };
    uint32_t n = 0;
    while (zsr ? zsr3.has_next() : r3.has_next()) {
      ++n;
      uint32_t x = zsr ? zsr3.next_uint() : r3.next_uint();
      if (zsw)
        zsw3.accept_uint_grow(x, array_grow);
      else
        w3.accept_uint_grow(x, array_grow);
      ASSERT_EQ(no_buffer.i, -1);
      if (zsw && n % 17 == 0) {
        auto& w = zsw3.writer_for_testing();
        size_t pcs0 = w.pair_count_stats()[0];
        auto ckpt = zsw3.checkpoint();
        for (int i = (n % 8) + 28; i > 0; i--) {
          zsw3.accept_uint_pair_grow(1, i & 1, i & 2, array_grow);
        }
        w.pair_count_stats()[0]++;  // inject an extra delta
        assert(pcs0 < w.pair_count_stats()[0], "");
        zsw3.restore(ckpt);
        assert(pcs0 == w.pair_count_stats()[0], "");
      }
    }
    zsw3.flush();
    zsw3.collect_stats(UNSIGNED5::Statistics::UK);
    // we always allocated one more than the limit!
    auto arr = (zsw ? zsw3.array() : w3.array());
    auto len = (zsw ? zsw3.position() : w3.position());
    ASSERT_EQ(len, buflen);
    ASSERT_EQ(len, (int)strlen(arr));
    std::string buf_s(buf, len);
    std::string arr_s(arr, len);
    ASSERT_EQ(buf_s, arr_s);
  }

  // try printing:
  {
    char stbuf[1000];
    stringStream st(stbuf, sizeof(stbuf)-1);
    UNSIGNED5::Reader<char*,int> printer(buf);
    printer.print_on(&st, 4, "(", ")");
    std::string st_s(st.base(), st.size());
    char buf2[sizeof(stbuf)];
    os::snprintf_checked(buf2, sizeof(buf2), "(%d %d %d %d)", ints[0], ints[1], ints[2], ints[3]);
    std::string exp_s(buf2, strlen(buf2));
    ASSERT_EQ(exp_s, st_s);
  }
}

TEST_VM(unsigned5, read_pair) {
  const int LEN = 1000;
  int ints[LEN];
  init_ints(LEN, ints);
  for (int s = 0; s <= 31; s++) {
    int i;
    UNSIGNED5::Sizer<> szr;
    for (i = 0; i+1 < LEN; i+=2) {
      szr.accept_uint_pair(s, ints[i], ints[i+1]);
    }
    //printf("count=%d, size=%d\n", (int)szr.count(), (int)szr.position());
    char buf[LEN * (UNSIGNED5::MAX_LENGTH*2 + 1)];
    int buflen, bufcount;
    {
      int pos = 0, cnt = 0;
      for (int i = 0; i+1 < LEN; i+=2) {
        auto writer = [&](uint32_t x) { UNSIGNED5::write_uint(x, buf, pos, 0); };
        cnt += UNSIGNED5::write_uint_pair(s, ints[i], ints[i+1], writer);
      }
      EXPECT_TRUE(pos+1 < (int)sizeof(buf)) << pos;
      buflen = pos;
      buf[buflen] = 0;
      bufcount = cnt;
    }
    EXPECT_EQ((int)szr.position(), buflen);
    EXPECT_EQ((int)szr.count(), bufcount);
    MyReader r1(buf);
    i = 0;
    while (r1.has_next()) {
      uint32_t x, y;
      r1.next_uint_pair(s, x, y);
      uint32_t x0 = ints[i++];
      uint32_t y0 = ints[i++];
      ASSERT_EQ(x, x0) << i;
      ASSERT_EQ(y, y0) << i;
    }
    ASSERT_EQ(i, LEN);
    // copy from reader to writer
    UNSIGNED5::Reader<char*,int> r3(buf);
    UNSIGNED5::Writer<char*,int> w3;
    auto array_grow = [&](int){
      auto oal = w3.limit();
      auto nal = oal + 15;
      //printf("growing array from %d to %d\n", oal, nal);
      w3.grow_array(new char[nal + 1], nal);
    };
    array_grow(1);
    while (r3.has_next()) {
      uint32_t x, y;
      r3.next_uint_pair(s, x, y);
      w3.accept_uint_pair_grow(s, x, y, array_grow);
    }
    w3.accept_end_byte();  // we always allocated one more than the limit!
    std::string buf_s(buf, buflen);
    std::string arr_s(w3.array(), strlen(w3.array()));
    ASSERT_EQ(buf_s, arr_s);
  }
}

TEST_VM(unsigned5, encode_signed) {
  // for all allowed sign bit counts, including 0 and 1:
  for (int sb = 0; sb <= 15; sb++) {
    // test patterns are small shifted numbers, plus negations & complements
    for (int i = -6; i < 6; i++) {
      for (int j = 0; j <= 30; j++) {
        uint32_t x = (uint32_t)(i|1) << j;
        if ((i&1) == 0) x = ~x;
        uint32_t ex  = UNSIGNED5::encode_multi_sign(sb, x);
        uint32_t dex = UNSIGNED5::decode_multi_sign(sb, ex);
        ASSERT_EQ(dex, x) << sb << " bits: " << x << " E=> " << ex << " D=> " << dex;
        // test both directions of bijection:
        uint32_t dx  = UNSIGNED5::decode_multi_sign(sb, x);
        uint32_t edx = UNSIGNED5::encode_multi_sign(sb, dx);
        ASSERT_EQ(dex, x) << sb << " bits: " << x << " D=> " << dx << " E=> " << edx;
      }
    }
  }
}

bool PRINT_DECILES = trueInDebug;

TEST_VM(unsigned5, zero_suppress) {
  int zero_mask_len = 8, exp_mask_len = 8;
  // For all pairs of 8-bit patterns (exhaustively) we then expand the
  // first pattern by the second, creating various patterns of 1s and
  // 0s, with 0s predominating in varying amounts, depending on the
  // second 8-bit pattern.  We generate the resulting pattern,
  // repeating up to the given length (a power of 10).
  //
  // We ensure that the resulting data set compresses and decompresses
  // without loss.  We ensure that the compressed data is never more
  // than one storage unit larger than the original data. (That is the
  // theoretical optimum.)  We also ensure that the compression rate
  // roughly improves as the proportion of zeroes improves.
  // Basically, any zeroes after the first 20% must be completely
  // suppressed; in practice we often do better than this.  We also
  // test the "disable_compression" modes, which convert ZS
  // reader/writers into vanilla reader/writers.
  //
  // For extra error detection, each non-zero items is given a
  // semi-unique value which fits in one UNSIGNED5 byte, (i&0x7F)+1.
  // Obviously, if the ZS compressor messes up badly the non-zero
  // bytes won't decompress in the same order.
  const int MAX_PLEN = 250;  // 6250 requires many seconds
  uint32_t payload[MAX_PLEN+10];
  u_char compressed[MAX_PLEN*UNSIGNED5::MAX_LENGTH+10];
  for (int plen = 10; plen <= MAX_PLEN; plen *= 5) {
    for (int which_variation = 0; which_variation < 4; which_variation++) {
      // passthrough means we disable compression in the R/W pair
      const bool passthrough = (which_variation & 1) != 0;
      // wide_values means token sizes will not be just one byte
      const bool wide_values = (which_variation & 2) != 0;
      assert(exp_mask_len >= 1, "");
      const int zero_poor_mask = 3 << (exp_mask_len - 2);
      struct Case {
        uint64_t zm;
        int zc;
        int cc;
        int compareTo(Case* that) {
          int c;
          if ((c = compare(this->zm, that->zm)) != 0)  return c;
          if ((c = compare(this->cc, that->cc)) != 0)  return c;
          if ((c = compare(this->zc, that->zc)) != 0)  return c;
          return 0;
        }
        static int compare(int64_t x, int64_t y) {
          return (x > y) ? 1 : (x < y) ? -1 : 0;
        }
      };
      Case* cases = new Case[1 << (zero_mask_len + exp_mask_len)];
      int caseno = 0;
      for (int expansion = 1; expansion <= 1 << exp_mask_len; expansion++) {
        // Test both zero-rich and zero-poor input.
        const bool zero_poor = (expansion & zero_poor_mask) == zero_poor_mask;
        const int xm = !zero_poor ? expansion : -(expansion << (32-exp_mask_len)) >> (32-exp_mask_len);
        assert(((xm - 1) & (-1 << exp_mask_len)) == 0, "");
        const int xmlen = 32 - count_leading_zeros(xm);
        for (int zm = 0; zm < (1 << zero_mask_len); zm++) {
          int zero_count = 0;
          uint64_t longzm = 0;
          int payload_length_in_bytes = 0;
          for (int i = 0, zi = 0, xi = 0; i < plen; i++) {
            bool is_zero;
            if (((xm >> xi) & 1) == 0) {  // it is the background pattern
              is_zero = !zero_poor;
            } else {  // it is a bit sampled from the mask
              is_zero = (((uint32_t)zm >> zi) & 1) != 0;
              if (++zi == zero_mask_len)  zi = 0;
            }
            if (++xi == xmlen)  xi = 0;
            uint32_t pval = (is_zero ? 0 : (i&0x7F)+1);
            if (wide_values)  pval <<= (i % 5) * 6;
            payload[i] = pval;
            payload_length_in_bytes += UNSIGNED5::encoded_length(pval);
            if (is_zero && i < 64)  longzm |= (uint64_t)1 << i;
            if (is_zero)  zero_count++;
          }
          ZeroSuppressingU5::ZSWriter<address,int> cw(compressed, sizeof(compressed));
          if (passthrough)  cw.set_passthrough();
          for (int i = 0; i < plen; i++) {
            cw.accept_uint(payload[i]);
          }
          cw.flush();
          int clen = cw.position();
          if (passthrough)
            ASSERT_EQ(clen, payload_length_in_bytes);
          else
            ASSERT_LE(clen, payload_length_in_bytes+1);
          ZeroSuppressingU5::ZSReader<address,int> cr(compressed, clen);
          if (passthrough)  cr.set_passthrough();
          for (int i = 0; i < plen; i++) {
            ASSERT_TRUE(cr.has_next());
            auto x = cr.next_uint();
            ASSERT_EQ(x, payload[i]) << i;
          }
          ASSERT_FALSE(cr.has_next());

          if (which_variation == 0) {
            // Record the case for PRINT_DECILES
            Case c;
            c.zm = longzm;
            c.zc = zero_count;
            c.cc = clen;
            double zero_pct = zero_count * 100.0 / plen;
            double compress_pct = clen * 100.0 / (plen+1);
            double bound = 100.0 - zero_pct + 20 + 64.0/plen;
            // Check that compression is not totally broken:
            ASSERT_LE(compress_pct, bound) << c.zm;
            bool found = false;
            for (int i = 0; i < caseno; i++) {
              if (cases[i].compareTo(&c) == 0) {
                found = true;
                break;
              }
            }
            if (!found)  cases[caseno++] = c;
          }
        }
      }
      if (which_variation == 0) {
        printf("batch with plen=%d zero_mask_len=%d, exp_mask_len=%d generates %d cases\n",
               plen, zero_mask_len, exp_mask_len, caseno);
        if (PRINT_DECILES) {
          // Summarize deciles.
          for (int decile = 1; decile <= 10; decile++) {
            int lo = (decile-1) * plen / 10 + (decile > 1 ? 1 : 0);
            int hi = decile * plen / 10;
            int pc = 0, zc = 0, cc = 0;
            int dcases = 0;
            double maxpct = 0, minpct = 100;
            for (int i = 0; i < caseno; i++) {
              Case& c = cases[i];
              const int czc = c.zc, ccc = c.cc;
              if (czc >= lo && czc <= hi) {
                ++dcases;
                pc += plen;
                zc += czc;
                cc += ccc;
                double pct = ccc * 100.0 / plen;
                if (maxpct < pct)  maxpct = pct;
                if (minpct > pct)  minpct = pct;
              }
            }
            printf("D%02d comp = %6.2f%% (%6.2f%%..%6.2f%%) "
                   "for %6.2f%% zeroes out of %.2fkb [%3d cases]\n",
                   decile,
                   100.0 * cc / pc, minpct, maxpct,
                   100.0 * zc / pc, pc / 1000.0,
                   dcases);
          }
        }
      }
    }
  }
}

// Here is some object code to look at if we want to do a manual
// study.  One could find the build file named test_unsigned5.o.cmdline
// and hand-edit the command line to produce assembly code in
// test_unsigned5.s.
//
// Or, given the two empty "fence functions", one could do a
// quick scan like this:
//
// $ objdump -D $(find build/*release -name test_unsigned5.o) \
//   | sed -n /start_code_quality/,/end_code_quality/p \
//   | egrep -B10 bswap  # or grep -B20 cfi_endproc

void start_code_quality_unsigned5() { }

uint32_t code_quality_max_encoded_in_length(int i) {
  return UNSIGNED5::max_encoded_in_length(i);  // should compile like 5-switch
}

int code_quality_encoded_length(uint32_t x) {
  return UNSIGNED5::encoded_length(x);  // should compile to 4-way comparison
}

int code_quality_check_length(char* a) {
  return UNSIGNED5::check_length(a, 0);  // should compile with fast-path
}

int code_quality_read_int(char* a) {
  int i = 0;
  return UNSIGNED5::read_uint(a, i, 0);  // should compile with fast-path
}

int code_quality_read_signed_int(char* a) {
  int i = 0;
  uint32_t x = UNSIGNED5::read_uint(a, i, 0);
  return UNSIGNED5::decode_sign(x);
}

int code_quality_read_multi_signed_int(char* a) {
  int i = 0;
  uint32_t x = UNSIGNED5::read_uint(a, i, 0);
  return UNSIGNED5::decode_multi_sign(x, 4);
}

int code_quality_read_int_pair(char* a) {
  int i = 0;
  uint32_t x, y;
  auto reader = [&]{ return UNSIGNED5::read_uint(a, i, 0); };
  UNSIGNED5::read_uint_pair(5, x, y, reader);
  return ((uint64_t)y << 32) + x;
}

int code_quality_int_reader(char* a) {
  MyReader r1(a);
  if (!r1.has_next())  return -1;
  return r1.next_uint();
}

int code_quality_int_sizer(int* a, int n) {
  UNSIGNED5::Sizer<> s;
  for (int i = 0; i < n; i++)  s.accept_uint(a[i]);
  return s.position();
}

void end_code_quality_unsigned5() { }

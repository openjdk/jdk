/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "jvm.h"
#include "memory/allocation.inline.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/os.hpp"
#include "utilities/istream.hpp"
#include "unittest.hpp"

template<typename BlockClass>
class BlockInputStream : public inputStream {
  BlockClass _input;
 public:
  template<typename... Arg>
  BlockInputStream(Arg... arg)
    : _input(arg...) {
    set_input(&_input);
  }
};

#define EXPECT_MEMEQ(s1, s2, len) \
  EXPECT_PRED_FORMAT3(CmpHelperMEMEQ, s1, s2, len)
// cf. ::testing::internal::CmpHelperSTREQ

testing::AssertionResult CmpHelperMEMEQ(const char* s1_expression,
                                        const char* s2_expression,
                                        const char* len_expression,
                                        const char* s1, const char* s2,
                                        size_t len) {
  if (s1 == nullptr || s2 == nullptr) {
    return testing::internal::CmpHelperEQ(s1_expression, s2_expression,
                                          s1, s2);
  }
  int c = ::memcmp(s1, s2, len);
  if (c == 0) {
    return testing::AssertionSuccess();
  }
  ::std::string str1, str2;
  for (auto which = 0; which <= 1; which++) {
    auto  s   = which ? s1   : s2;
    auto &str = which ? str1 : str2;
    std::stringstream buf;
    buf << "{";
    for (size_t i = 0; i < len; i++) {
      char c = s[i];
      switch (c) {
      case '\0':  buf << "\\0"; break;
      case '\n':  buf << "\\n"; break;
      case '\\':  buf << "\\\\"; break;
      default:    buf << c; break;
      }
    }
    buf << "}[" << len_expression << "=" << len << "]";
    str = buf.str();
  }
  return testing::internal::CmpHelperSTREQ(s1_expression, s2_expression,
                                           str1.c_str(), str2.c_str());
}

static int firstdiff(char* b1, char* b2, int blen) {
  for (int i = 0; i < blen; i++) {
    if (b1[i] != b2[i])  return i;
  }
  return -1;
}

static char* get_temp_file(bool VERBOSE, const char* filename) {
  const char* tmp_dir = os::get_temp_directory();
  const char* file_sep = os::file_separator();
  size_t temp_file_len = strlen(tmp_dir) + strlen(file_sep) + strlen(filename) + 28;
  char* temp_file = NEW_C_HEAP_ARRAY(char, temp_file_len, mtInternal);
  int ret = jio_snprintf(temp_file, temp_file_len, "%s%spid%d.%s",
                         tmp_dir, file_sep,
                         os::current_process_id(), filename);
  if (VERBOSE)  tty->print_cr("temp_file = %s", temp_file);
  return temp_file;
}

static const char* get_temp_file(bool VERBOSE) {
  static const char* temp_file = get_temp_file(VERBOSE, "test_istream");
  return temp_file;
}

#define EIGHTY 80
#define LC0(x)     ('/' + (((unsigned)(x)+1) % EIGHTY))
#define LC(line,col)  LC0((col) * (line))

#define COLS 30

static int cases, total, zeroes;
#ifdef ASSERT
#define istream_coverage_mode(mode, a,b,c) \
  inputStream::coverage_mode(mode, a,b,c)
#else
#define istream_coverage_mode(mode, a,b,c)
#endif

// Fill in a test pattern of ascii characters.
// Each line is ncols long, plus a line termination of lelen (1 or 2).
// Each character is a fixed, static function of the line and column.
// This enables test logic to predict exactly what will be read in each line.
static void fill_pattern(bool VERBOSE,
                         char* pat, int patlen, int ncols, int lelen,
                         int& full_lines, int& partial_line,
                         const char* &line_end,
                         const char* &partial_line_end) {
  full_lines = partial_line = 0;
  for (int i = 0; i < patlen; i++) {
    int line = (i / (ncols+lelen)) + 1;  // 1-based line number
    int col  = (i % (ncols+lelen)) + 1;  // 1-based column number
    if (col <= ncols) {
      pat[i] = LC(line, col);
      partial_line = 1;
    } else if (col < ncols+lelen) {
      pat[i] = i == patlen - 1 ? '!' : '%';
      partial_line = 1;
    } else {
      assert(col == ncols+lelen, "");
      pat[i] = '!';
      full_lines++;
      partial_line = 0;
    }
  }
  pat[patlen] = '\0';
  if (VERBOSE)  tty->print_cr("PATTERN=%d+%d[%s]",
                              full_lines, partial_line, pat);
  for (int i = 0; i < patlen; i++) {
    assert(pat[i] != '%' || (i+1 < patlen && pat[i+1] == '!'), "");
    if (pat[i] == '!')  pat[i] = '\n';
    if (pat[i] == '%')  pat[i] = '\r';
  }
  assert(pat[patlen-1] != '\r', "");

  line_end = (lelen == 2 ? "\r\n" : "\n");
  int partial_line_bytes = patlen - (full_lines * (ncols + lelen));
  assert(partial_line_bytes < ncols + lelen, "");
  partial_line_end = (partial_line_bytes == ncols + 1) ? "\n" : "";
}

static const int MAX_PATLEN = COLS * (COLS-1);

static void istream_test_driver(const bool VERBOSE,
                                const int patlen,
                                const int ncols,
                                const int lelen,
                                const bool TEST_SET_POSITION,
                                const bool TEST_PUSH_BACK,
                                const bool TEST_EXPAND_REDUCE) {
  DEBUG_ONLY( istream_coverage_mode(VERBOSE ? 2 : 1, cases, total, zeroes) );
  const char* temp_file = get_temp_file(VERBOSE);
  unlink(temp_file);
  char pat[MAX_PATLEN+1];
  int full_lines = 0, partial_line = 0;
  const char* line_end = "\n";
  const char* partial_line_end = "";
  fill_pattern(VERBOSE, pat, patlen, ncols, lelen,
               full_lines, partial_line,
               line_end, partial_line_end);

  char pat2[sizeof(pat)];  // copy of pat to help detect scribbling
  memcpy(pat2, pat, sizeof(pat));
  // Make three kinds of stream and test them all.
  MemoryInput _min(pat2, patlen);
  inputStream sin(&_min);
  if (VERBOSE) {
    tty->print("at %llx ", (unsigned long long)(intptr_t)&sin);
    sin.dump("sin");
  }
  {
    fileStream tfs(temp_file);
    guarantee(tfs.is_open(), "cannot open temp file");
    tfs.write(pat, patlen);
  }
  BlockInputStream<FileInput> fin(temp_file);
  if (VERBOSE) {
    tty->print("at %llx ", (unsigned long long)(intptr_t)&fin);
    fin.dump("fin");
  }
  BlockInputStream<MemoryInput> min(&pat2[0], patlen);
  if (VERBOSE) {
    tty->print("at %llx ", (unsigned long long)(intptr_t)&min);
    sin.dump("min");
  }
  inputStream* ins[] = { &sin, &fin, &min };
  const char* in_names[] = { "sin", "fin", "min" };
  const char* test_mode = (TEST_SET_POSITION
                           ? (!TEST_PUSH_BACK ? "(seek)" : "(seek/push)")
                           : TEST_EXPAND_REDUCE
                           ? (!TEST_PUSH_BACK ? "(exp/red)" : "(exp/red/push)")
                           : (!TEST_PUSH_BACK ? "(plain)" : "(push)"));
  for (int which = 0; which < 3; which++) {
    inputStream& in = *ins[which];
    const char* in_name = in_names[which];
    int lineno;
    char* lp = (char*)"--";
#define LPEQ                                                    \
    in_name << test_mode                                        \
            << " ncols=" << ncols << " lelen=" << lelen         \
            << " full=" << full_lines << " lineno=" << lineno   \
            << " [" << lp << "]" << (in.dump("expect"), "")
    if (VERBOSE)
      tty->print_cr("testing %s%s patlen=%d ncols=%d full_lines=%d partial_line=%d",
                    in_name, test_mode,
                    patlen, ncols, full_lines, partial_line);
    int pos_to_set = 0, line_to_set = 1;  // for TEST_SET_POSITION only
    for (int phase = 0; phase <= (TEST_SET_POSITION ? 1 : 0); phase++) {
      lineno = 1;
      for (; lineno <= full_lines + partial_line; lineno++) {
        EXPECT_EQ(-1, firstdiff(pat, pat2, patlen + 1));
        if (VERBOSE)  in.dump("!done?");
        bool done = in.done();
        EXPECT_TRUE(!done)  <<LPEQ;
        if (done)  break;
        lp = in.current_line();
        const char* expect_endl =
          (lineno <= full_lines) ? line_end : partial_line_end;

        bool verify_lp = true;
        if (verify_lp) {
          int actual_lineno = (int) in.lineno();
          if (VERBOSE)  in.dump("CL    ");
          EXPECT_EQ(actual_lineno, lineno)  <<LPEQ;
          int len = (int) in.current_line_length();
          EXPECT_EQ(len, (int) strlen(lp))  <<LPEQ;
          int expect_len = ncols;
          if (lineno > full_lines)
            expect_len = MIN2(ncols, patlen % (ncols+lelen));
          EXPECT_EQ(len, expect_len)  <<LPEQ;
          for (int j = 0; j < len; j++) {
            int lc = LC(lineno, j+1);   // 1-based column
            EXPECT_EQ(lc, lp[j])  <<LPEQ;
          }
          if (len != expect_len || len != (int)strlen(lp)) {
            return;  // no error cascades please
          }
        }
        if (VERBOSE)  in.dump("next  ");
        in.next();
      }

      for (int done_test = 0; done_test <= 3; done_test++) {
        if (done_test == 2)  in.set_done();
        lp = in.current_line();  // should be empty line
        if (VERBOSE)  in.dump("done!!");
        EXPECT_TRUE(lp != nullptr);
        EXPECT_TRUE(in.done())  <<LPEQ;
        if (!in.done())  break;
        EXPECT_EQ((int)in.current_line_length(), 0)   <<LPEQ;
        EXPECT_EQ(strlen(lp), in.current_line_length())  <<LPEQ;
        bool extra_next = in.next();
        EXPECT_TRUE(!extra_next)  <<LPEQ;
      }

      // no memory side effects
      EXPECT_EQ(-1, firstdiff(pat, pat2, patlen + 1));
    }
  }
  unlink(temp_file);
}

static void istream_test_driver(const bool VERBOSE,
                                const bool TEST_SET_POSITION,
                                const bool TEST_PUSH_BACK,
                                const bool TEST_EXPAND_REDUCE) {
  ResourceMark rm;
  int patlen = MAX_PATLEN;
  const bool SHORT_TEST = false;
  const int SHORT_NCOLS = 1, SHORT_PATLEN = 37;
  if (SHORT_TEST)  patlen = SHORT_PATLEN;
  for (int ncols = 0; ncols <= patlen; ncols++) {
    if (SHORT_TEST) {
      if (ncols < SHORT_NCOLS)  ncols = SHORT_NCOLS;
      if (ncols > SHORT_NCOLS)  break;
    } else if (ncols > COLS && ncols < patlen - COLS) {
      ncols += ncols / 7;
      if (ncols > patlen - COLS)  ncols = (patlen - COLS);
    }
    for (int lelen = 1; lelen <= 2; lelen++) {  // try both kinds of newline
      istream_test_driver(VERBOSE,
                          patlen, ncols, lelen,
                          TEST_SET_POSITION, TEST_PUSH_BACK, TEST_EXPAND_REDUCE);
    }
  }
}

TEST_VM(istream, basic) {
  const bool VERBOSE = false;
  istream_test_driver(VERBOSE, false, false, false);
}

TEST_VM(istream, coverage) {
  const bool VERBOSE = false;
#ifdef ASSERT
  istream_coverage_mode(0, cases, total, zeroes);
  if (cases == 0)  return;
  if (VERBOSE || zeroes != 0)
    istream_coverage_mode(-1, cases, total, zeroes);
  EXPECT_EQ(zeroes, 0) << "zeroes: " << zeroes << "/" << cases;
#endif //ASSERT
}

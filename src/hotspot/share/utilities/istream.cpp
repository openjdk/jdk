/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/allocation.inline.hpp"
#include "runtime/orderAccess.hpp"
#include "utilities/istream.hpp"
#include "utilities/ostream.hpp"
#include "utilities/xmlstream.hpp"

#ifndef ASSERT
#define COV(casen) {}
#else //ASSERT
// Support for coverage testing.  Used by the gtest.
/* $ sed < istream.cpp '/^.* COV(\([A-Z][^)]*\)).*$/!d;s//COV_FN(\1)/' |
     tr '\12' ' ' | fold -sw72 | sed 's| $||;s|.*|  & \\|'
  */
#define DO_COV_CASES(COV_FN) \
  COV_FN(NXT_L) COV_FN(NXT_N) COV_FN(FIB_P) COV_FN(FIB_E) COV_FN(FIB_N) \
  COV_FN(FIB_L) COV_FN(PFB_C) COV_FN(PFB_P) COV_FN(PFB_A) \
  COV_FN(PFB_G) COV_FN(PFB_H) COV_FN(SBC_C) COV_FN(SBC_B) COV_FN(SBC_N) \
  COV_FN(SBC_L) COV_FN(EXB_R) COV_FN(EXB_A)
  /**/
#define COV_COUNT(casename) coverage_case_##casename
#define DECLARE_COV_CASE(casename) static int COV_COUNT(casename);
DO_COV_CASES(DECLARE_COV_CASE)
#undef DECLARE_COV_CASE

static int current_coverage_mode = 0;
#define COV(casename) {                                 \
    if (current_coverage_mode != 0) {                   \
      COV_COUNT(casename)++;                            \
    }                                                  }
#endif //ASSERT

bool inputStream::next() {
  // We have to look at the current line first, just in case nobody
  // actually called current_line() or done().
  preload();
  if (definitely_done()) {
    return false;         // OK to call this->next() after done is true
  }
  // current line is at buffer[beg..end]; now skip past its '\0'
  assert(have_current_line(), "");

  set_buffer_content(_next, _content_end);
  if (!need_to_read()) {  // any next line was already in the buffer
    COV(NXT_L);
    assert(have_current_line(), "");
    return true;
  } else {                // go back to the source for more
    COV(NXT_N);
    return fill_buffer();
  }
}

void inputStream::set_done() {
  size_t end = _beg = _end = _content_end;
  _next = end + NEXT_PHANTOM;
  assert(definitely_done(), "");
}

void inputStream::set_error(bool error_condition) {
  if (error_condition) {
    set_done();
    _input_state = IState::ERR_STATE;
    assert(error(), "");
  } else if (error()) {
    _input_state = definitely_done() ? IState::EOF_STATE : IState::NTR_STATE;
  }
}

void inputStream::clear_buffer() {
  _content_end = _beg = _end = _next = 0;
}

const char* inputStream::next_content(size_t& next_content_length) const {
  assert(is_sane(), "");
  size_t len = buffered_content_length(false);
  next_content_length = len;
  return len == 0 ? "" : &_buffer[_next];
}

void inputStream::set_input(inputStream::Input* input) {
  clear_buffer();
  _input = input;
  _input_state = IState::NTR_STATE;
}

bool inputStream::fill_buffer() {
  size_t fill_offset, fill_length;
  assert(!definitely_done(), "");  // caller responsibility
  while (need_to_read()) {
    prepare_to_fill_buffer(fill_offset, fill_length);
    if (error())  return false;
    assert(fill_length > 0, "");
    assert(fill_offset < _buffer_size, "");
    assert(fill_offset + fill_length <= _buffer_size, "");
    size_t nr = 0;
    if (_input != nullptr && _input_state == IState::NTR_STATE) {
      nr = _input->read(&_buffer[fill_offset], fill_length);
      if (nr == 0)  _input_state = IState::EOF_STATE;  // do not get EOF twice
    }
    bool last_partial = false;
    if (nr > 0) {
      fill_offset += nr;
    } else if (_beg == _end) {  // no partial line, so end it now
      // we hit the end of the file (or there was never anything there)
      COV(FIB_P);
      assert(!definitely_done(), "");
      set_done();
      assert(definitely_done(), "");
      return false;
    } else {
      // pretend to read a newline, to complete the last partial line
      COV(FIB_E);
      _buffer[fill_offset++] = '\n';  // insert phantom newline
      last_partial = true;
    }
    set_buffer_content(_beg, fill_offset);
    assert(!definitely_done(), "");
    if (need_to_read()) { COV(FIB_N); }
    else                { COV(FIB_L); }
    if (last_partial) {
      assert(have_current_line(), "");
      _content_end -= 1;  // reverse insertion of phantom newline
      assert(_next == _content_end + NEXT_PHANTOM, "");
      assert(have_current_line(), "");
    }
  }
  return true;
}

// Find some space in the buffer for reading.  If there is already a
// partial line in the buffer, new space must follow it immediately.
// The partial line is between _beg and _end, and no other parts of
// the buffer are in use.
void inputStream::prepare_to_fill_buffer(size_t& fill_offset,
                                         size_t& fill_length) {
  assert(need_to_read(), "");  // _next pointer out of the way
  size_t end = _content_end;
  if (_beg == end) { // if no partial line present...
    COV(PFB_C);
    clear_buffer();
    fill_offset = 0;
    fill_length = _buffer_size;
    return;   // use the whole buffer
  }
  // at this point we have a pending line that needs more input
  if (_beg > 0 && (_input != nullptr || end == _buffer_size)) {
    COV(PFB_P);
    // compact the buffer by overwriting characters from previous lines
    size_t shift_left = _beg;
    ::memmove(_buffer, _buffer + shift_left, _content_end - _beg);
    _beg -= shift_left;
    _end -= shift_left;
    _next -= shift_left;
    _content_end -= shift_left;
    end = _content_end;
  }
  if (end < _buffer_size) {
    COV(PFB_A);
    fill_offset = end;
    fill_length = _buffer_size - end;
    return;   // use the whole buffer except partial line at the beginning
  }
  // the whole buffer contains a partial line, which means we must expand
  COV(PFB_G);
  size_t new_size = (_buffer_size < BIG_SIZE ? BIG_SIZE
                     : _buffer_size + _buffer_size / 2);
  assert(new_size > _buffer_size, "");
  if (expand_buffer(new_size)) {
    COV(PFB_H);
    fill_offset = end;
    fill_length = _buffer_size - end;
    return;   // use the expanded buffer, except the partial line
  }
  // no recovery from failed allocation; just set the error state and bail
  set_error();
}

// The only buffer content is between the given offsets.
// Set _beg, _end, _next, and _content_end appropriately.
void inputStream::set_buffer_content(size_t content_start,
                                     size_t content_end) {
  assert(content_end <= _buffer_size, "");
  assert(content_start <= content_end + NEXT_PHANTOM, "");
  if (content_start >= content_end) {   // empty content; clear buffer
    COV(SBC_C);
    clear_buffer();
    return;
  }
  COV(SBC_B);
  size_t content_len = content_end - content_start;
  _beg = content_start;
  _content_end = content_end;

  // this is where we scan for newlines
  char* nl = (char*) memchr(&_buffer[content_start], '\n', content_len);
  if (nl == nullptr) {
    COV(SBC_N);
    _next = _end = content_end;
    assert(need_to_read(), "");
  } else {
    COV(SBC_L);
    *nl = '\0';  // so that this->current_line() will work
    ++_line_count;
    size_t end = nl - &_buffer[0];
    _next = end + 1;
    assert(_next != _content_end + NEXT_PHANTOM, "");
    if (end > content_start && nl[-1] == '\r') { // yuck
      // again, for this->current_line(), remove '\r' before '\n'
      nl[-1] = '\0';
      --end;
      // Note: we could treat '\r' alone as a line ending on some
      // platforms, but that is way too much work.  Newline '\n' is
      // supported everywhere, and some tools insist on accompanying
      // it with return as well, so we remove that.  But return '\r'
      // by itself is an obsolete format, and also inconsistent with
      // outputStream, which standarizes on '\n' and never emits '\r'.
      // Postel's law suggests that we write '\n' only and grudgingly
      // accept '\r' before '\n'.
    }
    _end = end;  // now this->current_line() points to buf[beg..end]
    assert(have_current_line(), "");
    assert(current_line() == &_buffer[_beg], "");
    assert(current_line_length() == _end - _beg, "");
  }
}

// Return true iff we expanded the buffer to the given length.
bool inputStream::expand_buffer(size_t new_length) {
  assert(new_length > _buffer_size, "");
  char* new_buf = nullptr;
  assert(new_length > sizeof(_small_buffer), "");
  if (_buffer == &_small_buffer[0]) {
    // fresh alloc from c-heap
    COV(EXB_A);
    new_buf = NEW_C_HEAP_ARRAY(char, new_length, mtInternal);
    assert(new_buf != nullptr, "would have exited VM if OOM");
    if (_content_end > 0) {
      assert(_content_end <= _buffer_size, "");
      ::memcpy(new_buf, _buffer, _content_end);  // copy only the active content
    }
  } else {
    // realloc
    COV(EXB_R);
    new_buf = REALLOC_C_HEAP_ARRAY(char, _buffer, new_length, mtInternal);
    assert(new_buf != nullptr, "would have exited VM if OOM");
  }

  if (new_buf == nullptr) {
    return false;   // do not further update _buffer etc.
  }
  _buffer = new_buf;
  _buffer_size = new_length;
  return true;
}

inputStream::~inputStream() {
  if (has_c_heap_buffer()) {
    FreeHeap(_buffer);
    DEBUG_ONLY(_buffer = (char*)((uintptr_t)0xdeadbeef)); // sanity
  }
}

#ifdef ASSERT
void inputStream::dump(const char* what) {
  int diff = (int)(_end - _beg);
  if (!_buffer || _beg > _buffer_size || _end > _buffer_size)
    diff = 0;

  bool ntr = (_next == _end),
       hcl = (_beg < _content_end && _end < _next),
       ddn = (_beg == _content_end && _next > _content_end);
  tty->print_cr("%s%sistream %s%s%s%s%s [%d<%.*s>%d/%d..%d] "
                " B=%llx%s[%d], LN=%d, CH=%d",
                what ? what : "", what ? ": " : "",
                _buffer == nullptr ? "U" : "",
                ntr ? "R" : "",
                hcl ? "L" : "",
                ddn ? "D" : "",
                (_next < _content_end ? "" :
                 _next == _content_end ? "N" : "P"),
                (int)_beg,
                diff < 0 ? 0 : diff > 10 ? 10 : diff,
                _buffer ? &_buffer[_beg] : "",
                (int)_end, (int)_next, (int)_content_end,
                (unsigned long long)(intptr_t)_buffer,
                _buffer == _small_buffer ? "(SB)" : "",
                (int)_buffer_size,
                (int)_line_count,
                has_c_heap_buffer());
  assert(is_sane(), "");
}
#endif

#ifdef ASSERT
// More support for coverage testing.
int inputStream::coverage_mode(int start,
                               int& cases, int& total, int& zeroes) {
  int old_mode = current_coverage_mode;
  current_coverage_mode = start;
  int num_cases = 0, zero_count = 0, case_count = 0;
#define COUNT_COV_CASE(casename) {              \
    int tem = COV_COUNT(casename);              \
    case_count += tem;                          \
    if (tem == 0)  ++zero_count;                \
    num_cases++;                                \
  }
  DO_COV_CASES(COUNT_COV_CASE)
#undef COUNT_COV_CASE
  if (start < 0) {
    tty->print("istream coverage:");
    #define PRINT_COV_CASE(casename) \
      tty->print(" %s:%d", #casename, COV_COUNT(casename));
    DO_COV_CASES(PRINT_COV_CASE)
    tty->cr();
    #undef PRINT_COV_CASE
    if (zero_count != 0) {
      case_count = -case_count;
      #define ZERO_COV_CASE(casename)                  \
        if (COV_COUNT(casename) == 0)                  \
          tty->print_cr("%s: no coverage for %s",      \
                        __FILE__, #casename);          \
      DO_COV_CASES(ZERO_COV_CASE)
      #undef ZERO_COV_CASE
    }
  }
  if (start >= 2 || start < 0) {
    #define CLEAR_COV_CASE(casename) \
       COV_COUNT(casename) = 0;
    DO_COV_CASES(CLEAR_COV_CASE)
    #undef CLEAR_COV_CASE
  }
  cases  = num_cases;
  total  = case_count;
  zeroes = zero_count;
  return old_mode;
}
#endif //ASSERT

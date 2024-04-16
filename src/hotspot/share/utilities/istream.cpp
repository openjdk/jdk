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
  COV_FN(FIB_L) COV_FN(PFB_X) COV_FN(PFB_C) COV_FN(PFB_P) COV_FN(PFB_A) \
  COV_FN(PFB_G) COV_FN(PFB_H) COV_FN(SBC_C) COV_FN(SBC_B) COV_FN(SBC_N) \
  COV_FN(SBC_L) COV_FN(EXB_S) COV_FN(EXB_R) COV_FN(EXB_A) COV_FN(XCL_Z) \
  COV_FN(XCL_S) COV_FN(XCL_E) COV_FN(XCL_X) COV_FN(XCL_N) COV_FN(RCL_S) \
  COV_FN(RCL_E) COV_FN(RCL_Z) \
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
  _line_ending = 0;
  assert(definitely_done(), "");
}

void inputStream::set_error(bool error_condition) {
  if (error_condition) {
    set_done();
    _input_state = ERR_STATE;
    assert(error(), "");
  } else if (error()) {
    _input_state = definitely_done() ? EOF_STATE : NTR_STATE;
  }
}

void inputStream::clear_buffer() {
  _content_end = _beg = _end = _next = 0;
  _line_ending = 0;
  _clean_read_position = _expected_read_position;
}

const char* inputStream::next_content(size_t& next_content_length) const {
  assert(is_sane(), "");
  size_t len = buffered_content_length(false);
  next_content_length = len;
  return len == 0 ? "" : &_buffer[_next];
}

void inputStream::set_input(inputStream::Input* input) {
  clear_buffer();
  if (_input != nullptr && _input != input) {
    _input->close();
  }
  _input = input;
  _input_state = NTR_STATE;
  _clean_read_position = _expected_read_position = 0;
  if (input != nullptr) {
    size_t ip = input->position();
    if (ip != (size_t)-1) {
      _expected_read_position = ip;
    }
  }
}

bool inputStream::fill_buffer() {
  assert(!definitely_done(), "");  // caller responsibility
  while (need_to_read()) {
    size_t fill_offset, fill_length;
    prepare_to_fill_buffer(fill_offset, fill_length);
    if (error())  return false;
    assert(fill_length > 0, "");
    assert(fill_offset < _buffer_size, "");
    assert(fill_offset + fill_length <= _buffer_size, "");
    size_t nr = 0;
    if (_input != nullptr && _input_state == NTR_STATE) {
      nr = _input->read(&_buffer[fill_offset], fill_length);
      if (nr == 0)  _input_state = EOF_STATE;  // do not get EOF twice
      // do not expect _input to track its own position, but track mine
      _expected_read_position += nr;
      // _clean_read_position lags behind unless disturbed by edits
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
      _line_ending = 0;
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
  if (_buffer_size == 0) {
    COV(PFB_X);
    expand_buffer(sizeof(_small_buffer));
    assert(_buffer_size > 0, "");
    // and continue with at least a little buffer
  }
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
    _line_ending = 0;
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
    _line_ending = (int)(_next - end);
    assert(have_current_line(), "");
    assert(current_line() == &_buffer[_beg], "");
    assert(current_line_length() == _end - _beg, "");
  }
}

// Return true iff we expanded the buffer to the given length.
bool inputStream::expand_buffer(size_t new_length) {
  assert(new_length > _buffer_size, "");
  char* new_buf = nullptr;
  if (new_length <= sizeof(_small_buffer)) {
    COV(EXB_S);
    new_buf = &_small_buffer[0];
    new_length = sizeof(_small_buffer);
  } else if (_buffer != nullptr && _buffer == _must_free) {
    COV(EXB_R);
    new_buf = REALLOC_C_HEAP_ARRAY(char, _buffer, new_length, mtInternal);
    if (new_buf != nullptr) {
      _must_free = new_buf;
    }
  } else {  // fresh allocation
    COV(EXB_A);
    new_buf = NEW_C_HEAP_ARRAY(char, new_length, mtInternal);
    if (new_buf != nullptr) {
      assert(_must_free == nullptr, "dropped free");
      _must_free = new_buf;
      if (_content_end > 0) {
        assert(_content_end <= _buffer_size, "");
        ::memcpy(new_buf, _buffer, _content_end);  // copy only the active content
      }
    }
  }
  if (new_buf == nullptr) {
    return false;   // do not further update _buffer etc.
  }
  _buffer = new_buf;
  _buffer_size = new_length;
  return true;
}

void inputStream::handle_free() {
  void* to_free = _must_free;
  if (to_free == nullptr)  return;
  _must_free = nullptr;
  FreeHeap(to_free);
}

#ifdef ASSERT
void inputStream::dump(const char* what) {
  int diff = (int)(_end - _beg);
  if (!_buffer || _beg > _buffer_size || _end > _buffer_size)
    diff = 0;

  bool ntr = (_next == _end),
       hcl = (_beg < _content_end && _end < _next),
       ddn = (_beg == _content_end && _next > _content_end);
  tty->print_cr("%s%sistream %s%s%s%s%s [%d<%.*s>%d/%d..%d] LE=%d,"
                " B=%llx%s[%d], LN=%d%+d, C..ERP=%d..%d, MF=%llx",
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
                _line_ending,
                (unsigned long long)(intptr_t)_buffer,
                _buffer == _small_buffer ? "(SB)" : "",
                (int)_buffer_size,
                (int)_line_count, (int)_adjust_count,
                (int)_clean_read_position,
                (int)_expected_read_position,
                (unsigned long long)(intptr_t)_must_free);
  assert(is_sane(), "");
}
#endif

char* inputStream::save_line(bool c_heap) const {
  size_t len;
  char* line = current_line(len);
  size_t alloc_len = len + 1;
  char* copy = (c_heap
                ? NEW_C_HEAP_ARRAY(char, alloc_len, mtInternal)
                : NEW_RESOURCE_ARRAY(char, alloc_len));
  if (copy == nullptr) {
    return (char*)"";  // recover by returning a valid string
  }
  ::memcpy(copy, line, len);
  copy[len] = '\0';  // terminating null
  // Note: There may also be embedded nulls in the line.  The caller
  // must deal with this by saving a count as well, or else previously
  // testing for nulls.
  if (c_heap) {
    // Need to ensure our content is written to memory before we return
    // the pointer to it.
    OrderAccess::storestore();
  }
  return copy;
}

const char* inputStream::current_line_ending() const {
  assert(is_sane(), "");
  preload();
  switch (_line_ending) {
  case 1: return "\n";
  case 2: return "\r\n";
  default: return "";
  }
  // If we were to support more kinds of newline, such as '\r' or
  // Unicode line ends, we could add more state and logic here.
}

char* inputStream::prepare_to_expand_current_line(size_t increase, bool at_start) {
  assert(is_sane(), "");
  size_t llen = current_line_length();
  size_t nlen = llen + increase;
  if (nlen <= llen) {
    // It could be that nlen < llen, if somebody requested a ridiculous increase.
    COV(XCL_Z);
    return (nlen == llen) ? &_buffer[at_start ? _beg : _end] : nullptr;
  }

  // Find out if we are sharing the buffer with any pending input.
  int next_phantom = (_next == _content_end + NEXT_PHANTOM) ? NEXT_PHANTOM : 0;
  size_t pending_beg = _next - next_phantom;

  // If dirtying the current line, advance clean position to next line.
  if (position_is_clean(pending_beg)) {
    _clean_read_position = _expected_read_position - (_content_end - pending_beg);
    assert(position_is_clean(pending_beg), "");  //still clean, just barely
  }

  // See if we have room for an O(1) fast path, otherwise moves stuff around.
  size_t fillp;
  if (at_start && increase <= _beg) {
    COV(XCL_S);
    _beg -= increase;
    fillp = _beg;
  } else if (!at_start && _end + increase < pending_beg) {
    COV(XCL_E);
    fillp = _end;
    _end += increase;
  } else {
    size_t pending_len = (_content_end + next_phantom) - pending_beg;
    size_t current_need = nlen + 1;  // nlen, plus space for final \0
    if (current_need > pending_beg) {
      COV(XCL_X);
      // This is where the buffer expansion happens.
      size_t total_need = current_need + pending_len;
      if (_buffer_size < total_need && !expand_buffer(total_need)) {
        set_error();
        return nullptr;
      }
      // Shift everything after the current line as high as possible.
      size_t fillp = _buffer_size - pending_len;
      if (fillp != pending_beg && pending_len != 0) {
        ::memmove(&_buffer[fillp], &_buffer[pending_beg], pending_len);
      }
      pending_beg = fillp;
      assert(current_need <= pending_beg, "");
      _next        = pending_beg + next_phantom;
      _content_end = pending_beg + pending_len - next_phantom;
    } else {
      COV(XCL_N);
    }
    // We might not need to reallocate the buffer if we just shift the line.
    // Whether we allocated or not, shift the line to make space at both ends.
    size_t spare_room = pending_beg - current_need;
    // We are free to position the line anywhere in {beg=0,...beg=spare_room].
    // Put more spare room near the start or end, based on the request.
    size_t new_beg = (at_start ? spare_room - spare_room / 4 : spare_room / 4);
    size_t copy_pos = new_beg + (at_start ? increase : 0);
    fillp =           new_beg + (at_start ? 0 : llen);
    ::memmove(&_buffer[copy_pos], &_buffer[_beg], llen);
    _beg = new_beg;
    _end = new_beg + nlen;
  }
  assert(_end < _next, "");
  _buffer[_end] = '\0';
  return &_buffer[fillp];
  // caller responsibility: memset(&_buffer[fillp], ' ', increase)
}

char* inputStream::reduce_current_line(size_t decrease, bool at_start) {
  assert(is_sane(), "");
  size_t llen = current_line_length();
  size_t nlen = (llen >= decrease) ? llen - decrease : 0;
  if (nlen < llen) {
    if (at_start) {
      COV(RCL_S);
      _beg = _end - nlen;
    } else {
      COV(RCL_E);
      _end = _beg + nlen;
      assert(_end < _next, "");
      _buffer[_end] = '\0';
    }
    // If dirtying the current line, advance clean position to next line.
    size_t still_clean = _next < _content_end ? _next : _content_end;
    if (position_is_clean(still_clean)) {
      _clean_read_position = _expected_read_position - (_content_end - still_clean);
      assert(position_is_clean(still_clean), "");  //still clean, just barely
    }
  } else {
    COV(RCL_Z);
  }
  return &_buffer[at_start ? _beg : _end];
}

// Forces the given data into the buffer, before the current line
// or overwriting the current line, depending on the flag.
void inputStream::push_back_input(const char* chars, size_t length,
                                  bool overwrite_current_line) {
  assert(is_sane(), "");
  bool was_done = (_buffer_size == 0) || definitely_done();
  if (overwrite_current_line) {
    preload();   // we need to know how much to overwrite...
  }
  if (!have_current_line()) {
    overwrite_current_line = false;  // nothing to overwrite
  }
  if (length == 0) {
    if (overwrite_current_line)  next();
    return;  // there is nothing else to do here
  }
  prepare_to_push_back(length, chars, overwrite_current_line);
  if (was_done) {
    // set things up so that the position of line 1 is zero:
    _clean_read_position = 0;
    _expected_read_position = length;
  }
}

size_t inputStream::prepare_to_push_back(size_t length,
                                         const char* chars,
                                         bool overwrite_current_line) {
  assert(chars != nullptr, "");
  const bool current_line_only = (chars == nullptr);  // two use cases

  // Some decisions are different if the pushback does not end in \n
  const int partial_line = (length > 0 && chars[length-1] != '\n' ? 1 : 0);

  // The logic below requires current line (_end+LE) to fit into _next exactly.
  size_t required_end = (_next <= _content_end
                         ? _next - _line_ending
                         : _content_end - _line_ending);
  if (_end != required_end && _content_end > 0) {
    size_t llen = _end - _beg;
    assert(llen + _line_ending <= required_end, "");
    size_t new_beg = required_end - llen;
    if (llen > 0)  ::memmove(&_buffer[new_beg], &_buffer[_beg], llen);
    _beg = new_beg;
    _end = new_beg + llen;
    _buffer[_end] = '\0';
  }

  // Decide exactly which pending bytes we will leave undisturbed in buffer.
  int next_phantom = (_next == _content_end + NEXT_PHANTOM) ? NEXT_PHANTOM : 0;
  size_t pending_beg = !overwrite_current_line ? _beg : _next - next_phantom;
  size_t pending_len = (_content_end + next_phantom) - pending_beg;

  // If dirtying the current line, advance clean position to next line.
  // Otherwise, advance clean position to current line, but not any earlier.
  size_t still_clean = partial_line == 0 ? pending_beg : _next - next_phantom;
  if (position_is_clean(still_clean)) {
    _clean_read_position = _expected_read_position - (_content_end - still_clean);
    assert(position_is_clean(still_clean), "");  //still clean, just barely
  }

  // We may need to recompute line boundaries after this operation
  if (have_current_line()) {
    add_to_lineno(-1);     // we will see some \n again, or we are deleting it
    if (pending_beg <= _end && _end < _content_end) {
      assert(!overwrite_current_line, "");
      // Prepare to recognize the current line ending a second time.
      assert(_buffer[_end] == '\0', "");
      const char* le = current_line_ending();
      strncpy(&_buffer[_end], le, _next - _end);
      // set_buffer_content will see the line ending again
    }
  }

  // How much space do we need?  New stuff + pending stuff + maybe a new phantom.
  size_t buflen = length + pending_len + (pending_len == 0 ? partial_line : 0);
  if (_buffer_size < buflen && !expand_buffer(buflen)) {
    set_error();
    return (size_t)-1;
  }
  assert(length + pending_len <= _buffer_size, "");
  size_t fillp = _buffer_size;
  if (pending_len == 0) {
    // Welcome a phantom \n, since we are going to need one.
    fillp -= partial_line;
    if (DEBUG_ONLY(partial_line)+0 == 1)  _buffer[fillp] = '#';
  } else if (length <= pending_beg) {
    fillp = pending_beg;   // avoid the memmove in the next paragraph
  } else {
    fillp -= pending_len;
    if (fillp != pending_beg) {
      assert(fillp + pending_len <= _buffer_size, "");
      ::memmove(&_buffer[fillp], &_buffer[pending_beg], pending_len);
      // We will rely on set_buffer_content to fix up beg/end/next/content_end.
    }
  }
  assert(fillp >= length, "");
  fillp -= length;
  ::memcpy(&_buffer[fillp], chars, length);
  set_buffer_content(fillp, fillp + length + pending_len - next_phantom);
  if (_next == _content_end && next_phantom != 0) {
    assert(_next < _buffer_size, "");
    _buffer[_next++] = '\0';
  }
  preload();
  return fillp;
}

size_t inputStream::current_line_position() const {
  size_t buflen = definitely_done() ? 0 : buffered_content_length(true);
  size_t content_end_pos = _expected_read_position;
  return (position_is_clean(_content_end - buflen)
          ? content_end_pos - buflen
          : (size_t)-1);
}

size_t inputStream::set_current_line_position(size_t position,
                                              julong lineno) {
  bool do_set_lineno = (lineno != 0);
  if (position == current_line_position()) {
    if (do_set_lineno)  set_lineno(lineno);
    return position;  // nop
  }
  size_t nclen = buffered_content_length(false);
  // is new pos in the same buffer?  if so, just bump pointers forward
  if (_expected_read_position < nclen) {
    nclen = 0;  // should not happen, but recover
  }
  size_t next_pos = _expected_read_position - nclen;
  if (position >= next_pos && position <= _expected_read_position) {
    size_t new_next = _next + (position - next_pos);
    set_buffer_content(new_next, _content_end);
  } else {
    if (_input == nullptr) {
      set_error();
      return (size_t)-1;
    }
    position = _input->set_position(position);
    if (position == (size_t)-1)  return position;
    // the next call sets _expected_read_position and _clean_read_position:
    set_input(_input);
  }
  preload();
  if (do_set_lineno)  set_lineno(lineno);
  return current_line_position();
}

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

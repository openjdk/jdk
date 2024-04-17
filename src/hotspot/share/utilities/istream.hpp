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

#ifndef SHARE_UTILITIES_ISTREAM_HPP
#define SHARE_UTILITIES_ISTREAM_HPP

#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"

// Input streams for reading line-oriented textual data. These streams
// treat newline '\n' very differently from all other bytes.  Carriage
// return '\r' is just another bit of whitespace, although it is
// removed just before newline.
//
// Null '\0' is just a data byte, although it also terminates C
// strings; the `current_line` function adds a null after removing any
// line terminator but does not specially process any nulls embedded
// in the line.
//
// There are sizing access functions which allow lines to contain
// null, but the simpler function assumes null termination, and thus
// lines containing null will "look" shorter when viewed as C strings.
// Use the sizing access functions if you care about this.
//
// Formatting guidelines:
//
// Configuration data should be line-oriented.  It should be readable
// by humans (though perhaps with difficulty).  It should be easily
// processed by text editors and by widely available text processing
// tools such as grep, sed, and awk.
//
// Configuration data should not require "compilers" to generate, if
// possible.  It should be editable by hand, if possible.  In cases
// where binary data is strongly required, pick a binary format
// already native to Hotspot, such as classfile, jar, or jmod.
//
// Each line should be separately parseable; the parsing can be ad
// hoc.  For constructs inherently larger than single lines (such as
// complex method configuration information), try to use a structuring
// principle that allows "leaf" data to be line-oriented, and delimits
// that data with markup lines of some sort.  Try to pick a
// line-friendly version of a standard format like XML or Markdown.
// JSON is somewhat problematic because there is no line-friendly leaf
// syntax: everything at the leaves must be a quoted string in JSON.
//
// Use simple parsing via scanf-like formats for simple applications.
// But, keep in mind that these formats may lose data when applied to
// unusual strings, such as class names that contain spaces, or method
// names that contain punctuation.  For more robust transmission of
// potentially unusual names, consider wrapping them in XML-flavored
// lines like <tag attr='pay load'/>.
//
// Note: Input streams are never MT-safe.

class inputStream : public CHeapObjBase {
 public:
  class Input;

 private:
  NONCOPYABLE(inputStream);

  static constexpr size_t SMALL_SIZE =  240 DEBUG_ONLY(*0 + 10);
  static constexpr size_t BIG_SIZE   = 2048 DEBUG_ONLY(*0 + 20);

 protected:
  // Values for _input_state, to distinguish some phases of history:
  // Do we need to read more input (NTR)?  Did we see EOF already?
  // Was there an error getting input or allocating buffer space?
  enum IState { NTR_STATE, EOF_STATE, ERR_STATE };

  // Named offset for _next relative to _content_end, of phantom '\n'.
  static const int NEXT_PHANTOM = 1;

  Input* _input;   // where the input comes from or else nullptr
  IState _input_state;  // one of {NTR,EOF,ERR}_STATE
  char   _line_ending;  // one of {0,1,2} for "", "\n", "\r\n"
  char*  _buffer;       // scratch buffer holding at least the current line
  size_t _buffer_size;  // allocated size of buffer
  size_t _content_end;  // offset to end of valid contents of buffer
  size_t _beg;          // offset in buffer to start of current line
  size_t _end;          // offset to end of known current line (else content_end)
  size_t _next;         // offset to known start of next line (else =end)
  void*  _must_free;    // unless null, a malloc pointer which we must free
  size_t _line_count;   // increasing non-resettable count of lines read
  char   _small_buffer[SMALL_SIZE];  // buffer for holding lines

  void handle_free();

  // Buffer states
  //
  // The current line (less any line ending) is always [beg..end).
  // It is always the case that 0 <= beg <= end <= con_end <= buffer_size.
  // When there is a current line buffered, end < next <= 1+con_end.
  // In that case, the value of next is end + max(1, strlen(lend)),
  // where lend is "\n", "\r\n", or (for a last partial line) "".
  // But if next == end, we need to read more input, or observe an EOF.
  //
  //   beg ==end ==next ==  con_end => nothing buffered, we need to read
  //   beg <=end < next <=  con_end => have current line, with terminator
  //   beg < end < next ==1+con_end => have partial current line (saw EOF)
  //   beg < end ==next ==  con_end => partial line, we need to read
  //   beg ==end < next ==1+con_end => definitely done; no more I/O
  //
  // These states are in three mutually exclusive groups:
  //   need_to_read()      <= nothing or partial line in buffer
  //   have_current_line() <= beg/end point to valid line (partial only if EOF)
  //   definitely_done()   <= consumed all lines && (hit EOF || hit error)
  // These states are internal; the user can only look at next/done/error.
  //
  // A call to set_current_line_position (re-)fetches the indicated line.
  //
  // Relative to these states, everything already read from the input
  // before the first byte of the current line is logically present
  // (but not accessible) before _beg, while everything not yet read
  // from the input is after _content_end.  The difference between
  // these two pointers is constant, except when characters change
  // from being in the current line to being (logically) before it,
  // when next is called.

  bool is_sane() const {
    assert((_buffer == nullptr) == (_buffer_size == 0), "");
    assert(_content_end <= _buffer_size, "");
    assert(_beg <= _end && _end <= _content_end, "");
    assert(_end <= _next && _next <= _content_end + NEXT_PHANTOM, "");
    assert(_buffer_size == 0 || _next <= _buffer_size, "");
    return true;
  }

  bool need_to_read() const {
    assert(is_sane(), "");
    return _next == _end;
  }
  bool have_current_line() const {
    assert(is_sane(), "");
    // _beg < _content_end because there is an \0 (was \n) at _end,
    // or else it is a non-empty partial line and the \0 is at
    // _content_end.  In either case, if _end == _next we are
    // still searching for more input.
    return (_beg < _content_end && _end < _next);
  }
  bool definitely_done() const {
    assert(is_sane(), "");
    // If _beg < _content_end we still have a line of some sort.
    // Otherwise, if _next > _content_end, we have seen EOF or error.
    return (_beg == _content_end && _next > _content_end);
  }

  // Reset indexes within the buffer to point to no content.
  void clear_buffer();

  // Reset indexes within the buffer to point to the given content.
  // This is where we scan for newlines as well.
  void set_buffer_content(size_t content_start, size_t content_end);

  // Try to make the buffer bigger.  This may be necessary in order to
  // buffer a very long line.  Returns false if there was an
  // allocation failure.
  //
  // On allocation failure, just make do with whatever buffer there
  // was to start with; the caller must check for this condition and
  // avoid buffering more data in the non-expanded buffer.  However,
  // the buffer will always be non-null, so at least one line can be
  // buffered, if it is of normal size.
  bool expand_buffer(size_t new_length);

  // Make sure there is at least one line in the buffer, and set
  // _beg/_end to indicate where it is.  Any content before _beg can
  // be overwritten to make more room in the buffer.  If there is no
  // more input, set the state up to indicate we are done.
  bool fill_buffer();

  // Find some room in the buffer so we call read on it.
  // This might call expand_buffer but will try not to.
  // The assumption is that read already buffers slow I/O calls.
  // The purpose for the small buffer managed here is to store whole lines,
  // and perhaps edit them in-place.
  void prepare_to_fill_buffer(size_t& fill_offset, size_t& fill_length);

  // Quick check for an initially incomplete buffer...
  void preload() const {
    if (need_to_read()) {
      const_cast<inputStream*>(this)->fill_buffer();
    }
  }

  // How much content is buffered (if any) after the current line?
  size_t buffered_content_length(bool include_current) const {
    return (include_current       ? _content_end - _beg :
            _content_end >= _next ? _content_end - _next : 0);
  }

  // Returns a pointer and count to characters buffered after the
  // current line, but not yet read from my input source.  Only useful
  // if you are trying to stack input streams on top of each other
  // somehow.  You can also ask the input source if it thinks it has
  // more bytes.
  const char* next_content(size_t& next_content_length) const;

 public:
  // Create an empty input stream.
  // Call push_back_input or set_input to configure.
  inputStream() {
    _input = nullptr;
    _buffer = nullptr;
    _must_free = nullptr;
    _buffer_size = 0;
    _line_count = 0;
    _beg = _end = _next = _content_end = 0;
    _line_ending = 0;
    _input_state = NTR_STATE;
  }

  // Take input from the given source.  Buffer only a modest amount.
  inputStream(Input* input)
    : inputStream()
  {
    set_input(input);
  }

  virtual ~inputStream() {
    if (_must_free)         handle_free();
    if (_input != nullptr)  set_input(nullptr);
  }

  // Discards any previous input and sets the given input source.
  void set_input(Input* input);

  // Returns a pointer to a null terminated mutable copy of the current line.
  // Note that embedded nulls may make the line appear shorter than it really is.
  // This may trigger input activity if there is not enough data buffered.
  // If there are no more lines, return an empty line, statically allocated.
  char* current_line() const {
    preload();
    if (definitely_done())
      return (char*)"";
    return &_buffer[_beg];
  }

  // Return the size of the current line, exclusive of any line terminator.
  // If no lines have been read yet, or there are none remaining, return zero.
  size_t current_line_length() const {
    preload();
    return _end - _beg;
  }

  // Reports my current input source, if any, else a null pointer.
  Input* input() const { return _input; }

  // Discards the current line, gets ready to report the next line.
  // Returns true if there is one, which is always the opposite of done().
  // Fetches input if necessary.
  bool next();

  // Reports if there are no more lines.  Fetches input if necessary.
  bool done() const  {
    preload();
    return definitely_done();
  }

  // Discard pending input and do not read any more.
  // Takes no action if already done, whether in an error state or not.
  void set_done();

  // Reports if this stream has had an error was reported on it.
  bool error() const {
    return _input_state == ERR_STATE;
  }

  // Set this stream done with an error, if the argument is true.
  // If it is false but there is an error condition, clear the error.
  // Otherwise do nothing.
  void set_error(bool error_condition = true);

  // lineno is the 1-based ordinal of the current line; it starts at one
  size_t lineno() const         { preload(); return _line_count; }

  // Copy the current line to the given output stream.
  void print_on(outputStream* out);

  // Copy the current line to the given output stream, and also call cr().
  void print_cr_on(outputStream* out) {
    print_on(out); out->cr();
  }

#ifdef ASSERT
  void dump(const char* what = nullptr);
  static int coverage_mode(int mode, int& cases, int& total, int& zeroes);
#else
  void dump(const char* what = nullptr) { }
#endif


  // Block-oriented input, which treats all bytes equally.
  class Input : public CHeapObjBase {
  public:
    // Read some characters from an external source into the line buffer.
    // If there are no more, return zero, otherwise return non-zero.
    // It must be OK to call read even after it returns zero.
    virtual size_t read(char* buf, size_t size) = 0;
    // Example: read(b,s) { return fread(b, 1, s, _my_fp); }
    // Example: read(b,s) { return 0; } // never more than the initial buffer

    // Give the current number of bytes already produced by the source.
    // Give (size_t)-1 if this source does have a tracked position.
    // A tracked position increments by the result of every call to read.
    virtual size_t position() { return -1; }

    // Give the remaining number of bytes which might be produced in the future.
    // Give (size_t)-1 if this source does not keep track of that number.
    virtual size_t remaining() { return -1; }

    // Rewind so that the position appears to be the given one.
    // Return the new position, or else (size_t)-1 if the request fails.
    virtual size_t set_position(size_t position) { return -1; }

    // If it is backed by a resource that needs closing, do so.
    virtual void close() { }
  };
};

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

// for reading lines from files
class FileInput : public inputStream::Input {
  NONCOPYABLE(FileInput);

 protected:
  fileStream& _fs;
  fileStream _private_fs;

  // it does not seem likely there are such file streams around
  FileInput(fileStream& fs)
    : _fs(fs)
  { }

 public:
  // just forward all the constructor arguments to the wrapped line-input class
  template<typename... Arg>
  FileInput(Arg... arg)
    : _fs(_private_fs), _private_fs(arg...)
  {
  }

  FileInput(const char* file_name)
    : FileInput(file_name, "rt")
  { }

  bool is_open() const { return _fs.is_open(); }

 protected:
  virtual size_t read(char* buf, size_t size) {
    return _fs.read(buf, size);
  }
  virtual size_t position() {
    return _fs.position();
  }
  virtual size_t remaining() {
    return _fs.remaining();
  }
  virtual size_t set_position(size_t position) {
    return _fs.set_position(position);
  }
  virtual void close() {
    _fs.close();
  }
};

class MemoryInput : public inputStream::Input {
  const void* _base;
  const size_t _limit;
  size_t      _offset;
  const void* _must_free;  // unless null, a malloc pointer which we must free

 public:
  MemoryInput(const void* base, size_t size,
              bool must_free = false,
              size_t offset = 0)
    : _base(base), _limit(size), _offset(offset)
  {
    _must_free = must_free ? base : nullptr;
  }

  MemoryInput(const char* start)
    : MemoryInput(start, 0, strlen(start))
  { }

 protected:
  virtual size_t read(char* buf, size_t size) {
    size_t nr = size;
    if (nr > _limit - _offset) {
      nr = _limit - _offset;
    }
    if (nr > 0) {
      ::memcpy(buf, (char*)_base + _offset, nr);
      _offset += nr;
    }
    return nr;
  }
  virtual size_t position() {
    return _offset;
  }
  virtual size_t remaining() {
    return _limit - _offset;
  }
  virtual size_t set_position(size_t position) {
    if (position <= _limit) {
      _offset = position;
    } else {
      position = (size_t)-1;
    }
    return position;
  }
};

#endif // SHARE_UTILITIES_ISTREAM_HPP

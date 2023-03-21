/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_UTILITIES_XMLSTREAM_HPP
#define SHARE_UTILITIES_XMLSTREAM_HPP

#include "runtime/handles.hpp"
#include "utilities/ostream.hpp"

class xmlStream;
class defaultStream;

// If you need structuring beyond the level of single lines, or robust
// quoting of arbitrary strings, consider XML-flavored syntax.
//
// If a Hotspot-related file uses XML syntax to organize data, we
// call it "XML-flavored".  General XML is not read or written by
// Hotspot, but only a limited form.  (See, for example, the output
// of -XX:+LogCompilation.)  Every line in an XML-flavored file
// is either unmarked text no XML syntax except possibly escapes
// of the form "&lt;", etc, or else the line is a "markup line",
// an XML element or tag (enclosed in '<' and '>') which occupies
// the entire line.
//
// XML-flavored files can encapsulate marked bundles of flat text
// by wrapping them in XML tags like this:
//
//   <some_dependencies klass='foo/Bar'>
//   something about the first dependency
//   something about the second dependency
//   </some_dependencies>
//
// The other trick they can do is XML encapsulated small record-like
// items with multiple fields like this:
//
//   <my_favorite klass='foo/Bar' reason='I prefer foo bars'/>
//
// Hotspots reader for XML-flavored text makes it easy to pick apart
// such records.
//
// Note that XML requires that attribute names never be repeated, and
// that it reserves the right to reorder attributes.  Therefore, do
// not repeat attributes, and do not use their order to convey
// information.
//
// Although general XML allows a rich syntax for tag and attribute
// names, XML-flavored text must not use any names other than C-like
// identifiers composed of ASCII letters, numbers, and underscore.
//
// In XML-flavored text, character escaping more restricted than in
// general XML.  Necessarily, the characters in "&<>" are escaped,
// using exactly the sequences in "&amp;&lt;&gt;" and no other
// sequences (not "&#60;" or "&GT;" for "<", for example).  In
// addition, both single and double quotes are escaped, as
// "&apos;&quot;".  Finally, the sequence "&#10;" may stand in for a
// hidden newline; such hidden newlines may be useful in XML attribute
// strings, or even hidden in a physical line; otherwise there would
// be no way to talk about string constants or class names which
// contain the newline character.  Most importantly, apart from the
// Special Six characters "&<>'\"\n", no other character escapes will
// ever used in XML-flavored text used by Hotspot.  (If such escapes
// appear, different processing tools may observe different texts; a
// fully compliant XML parser will decode all escapes, while a
// Unix-based tool may only decode the Special Six.)
//
// Hotspot will not encode or decode any other escapes.  If it finds
// XML syntax in a place it does not expect, or of a kind it does not
// expect, it will just treat it as plain text on input, and on output
// add escapes, but just to the Special Six.
//
// The escaping of the double quote character (as "&quot;") is a
// historical artifact from the design of the Hotspot log (the first
// XML-flavored file).  There are no current uses of double-quote '"'
// to delimit text in XML flavored files.
//
// As a concession to general XML parsers, processing instructions
// (like "<?xml version='1.0' encoding='UTF-8'?>" as found in the main
// Hotspot log) will be skipped at the top of an XML-flavored input
// file.  But they must be alone on single lines, not accompanied
// even by whitespace at the beginning or end of the line.
//
// Some input files (like compiler command files) auto-detect XML
// flavoring, and apply XML escapes after unambiguously XML-flavored
// input has already been seen.  Until a line of the form /<.*>/ has
// been read, a reader in auto-detect mode will not expand escapes
// like "&amp;".  After such a line, all escapes will be expanded.  To
// allow real XML parsers to read it, or to trigger auto-detection
// immediately, such a file can look like this:
//
//    <?xml version='1.0' encoding='UTF-8'?>
//    <my_file>
//    ... rest of file comes here ...
//    </my_file>
//
// The body of the file should be enclosed in a matching pair of
// top-level tags, again just so that a real XML parser can handle it.
// (But many readers, including Hotspot, do not require this.  It is
// easy for Unix-like tools to add these trimmings to a file if it
// must be passed to a real XML parser.)
//
// Nearly any flat-text input can be enclosed tags, passed as
// attribute values, or passed to an auto-detecting stream, and
// the possibility of additional XML syntax will not affect the
// validity of the text.  But, necessarily, there are caveats.
//
//  - any line of the form /^<.*>$/ needs to be valid XML syntax
//  - other lines need not escape '<' and '>' (until real XML is needed)
//  - quotes and newlines in attribute strings need to be escaped
//  - the Special Six in attribute strings should be escaped (for real XML)
//  - lines with "&" need special care if they seem to contain escapes
//  - the Special Six in text lines should be escaped (for real XML)
//
// To be as interoperable as possible, XML-flavored files should
// always escape the Special Six "&<>'\"\n", using exactly the
// supported escapes "&amp;", unless those characters are used for
// XML-flavored markup.  Also, no other XML features should be used,
// to stay within the subset of XML understood by Hotspot.
//
// Here is a grammar that summarizes these rules:
//
//   xml_flavored_file = xml_compliant_file | [content NL]* [content]?
//   content = [looks_like_markup => markup] | text
//   text = [ escape | NOTNL ]*             -- real XML has more constraints
//   escape = /&lt;/ | /&gt;/ | /&amp;/ | /&apos/ | /&quot/ | /&#10;/
//   looks_like_markup = /^[<].*[>]$/
//   markup = elem | head NL [content NL]* tail
//   elem = "<" NAME [attr]* "/>"
//   head = "<" NAME [attr]* ">"
//   tail = "</" NAME ">"                   -- real XML requires names to match
//   attr = SP NAME "=" SQ attrstring SQ
//   attrstring = [ escape | NOTSQNL ]*     -- real XML has more constraints
//   xml_compliant_file = [xml_header]* markup
//   xml_header = [ /^[<][?].*[>]$/ NL ]*   -- real XML has more constraints
//   NAME = /[a-zA-Z_][a-zA-Z_0-9]*/        -- real XML allows additional names
//   NL = '\n'
//   NOTNL = /[^\n]/
//   NOTSQNL = /[^'\n]/
//   SP = / /  -- exactly one space
//   SQ = /'/  -- exactly this quote
//   ATOZ = //

// Sub-stream for writing regular text, as opposed to markup.
// Any "Special Six" characters written to this stream will be
// escaped, as '<' => "&lt;", etc.
class xmlTextStream : public outputStream {
  friend class xmlStream;
  friend class defaultStream; // tty
 private:

  xmlStream* _outer_xmlStream;

  xmlTextStream() { _outer_xmlStream = nullptr; }

 public:
   virtual void flush(); // _outer.flush();
   virtual void write(const char* str, size_t len); // _outer->write_text()
};


// Output stream for writing XML-structured logs.
// To write markup, use special calls elem, head/tail, etc.
// Use the xmlStream::text() stream to write unmarked text.
// Text written that way will be escaped as necessary using '&lt;', etc.
// Characters written directly to an xmlStream via print_cr, etc.,
// are directly written to the encapsulated stream, xmlStream::out().
// This can be used to produce markup directly, character by character.
// (Such writes are not checked for markup syntax errors.)

class xmlStream : public outputStream {
  friend class defaultStream; // tty
 public:
  enum MarkupState { BODY,       // after end_head() call, in text
                     HEAD,       // after begin_head() call, in attrs
                     ELEM };     // after begin_elem() call, in attrs

 protected:
  outputStream* _out;            // file stream by which it goes
  julong        _last_flush;     // last position of flush
  MarkupState   _markup_state;   // where in the elem/head/tail dance
  outputStream* _text;           // text stream
  xmlTextStream _text_init;

  // for subclasses
  xmlStream() {}
  void initialize(outputStream* out);

  // protect this from public use:
  outputStream* out()                            { return _out; }

  // helpers for writing XML elements
  void          va_tag(bool push, const char* format, va_list ap) ATTRIBUTE_PRINTF(3, 0);
  virtual void see_tag(const char* tag, bool push) NOT_DEBUG({});
  virtual void pop_tag(const char* tag) NOT_DEBUG({});

#ifdef ASSERT
  // in debug mode, we verify matching of opening and closing tags
  int   _element_depth;              // number of unfinished elements
  char* _element_close_stack_high;   // upper limit of down-growing stack
  char* _element_close_stack_low;    // upper limit of down-growing stack
  char* _element_close_stack_ptr;    // pointer of down-growing stack
#endif

 public:
  // creation
  xmlStream(outputStream* out) { initialize(out); }
  DEBUG_ONLY(virtual ~xmlStream();)

  bool is_open() { return _out != nullptr; }

  // text output
  bool inside_attrs() { return _markup_state != BODY; }

  // flushing
  virtual void flush();  // flushes out, sets _last_flush = count()
  virtual void write(const char* s, size_t len);
  void    write_text(const char* s, size_t len);
  int unflushed_count() { return (int)(out()->count() - _last_flush); }

  // handling escaped XML content, limited to only the Special Six
  static const int MAX_ESCAPE_LEN = 6;  //strlen("&apos;")
  static size_t find_escape(      const char* s, size_t len, int &esc_len, char& unesc);
  static size_t find_to_escape(   const char* s, size_t len, const char*& esc);
  static size_t escaped_length(   const char* s, size_t len);
  static size_t write_escaped(    const char* s, size_t len, outputStream* out,
                                  const char* pass_these_through = nullptr);
  static size_t write_unescaped(  const char* s, size_t len, outputStream* out);
  static size_t unescape_in_place( char* buffer, size_t len);

  // writing complete XML elements
  void          elem(const char* format, ...) ATTRIBUTE_PRINTF(2, 3);
  void    begin_elem(const char* format, ...) ATTRIBUTE_PRINTF(2, 3);
  void      end_elem(const char* format, ...) ATTRIBUTE_PRINTF(2, 3);
  void      end_elem();
  void          head(const char* format, ...) ATTRIBUTE_PRINTF(2, 3);
  void    begin_head(const char* format, ...) ATTRIBUTE_PRINTF(2, 3);
  void      end_head(const char* format, ...) ATTRIBUTE_PRINTF(2, 3);
  void      end_head();
  void          done(const char* format, ...) ATTRIBUTE_PRINTF(2, 3);  // xxx_done event, plus tail
  void          done_raw(const char * kind);
  void          tail(const char* kind);

  // va_list versions
  void       va_elem(const char* format, va_list ap) ATTRIBUTE_PRINTF(2, 0);
  void va_begin_elem(const char* format, va_list ap) ATTRIBUTE_PRINTF(2, 0);
  void       va_head(const char* format, va_list ap) ATTRIBUTE_PRINTF(2, 0);
  void va_begin_head(const char* format, va_list ap) ATTRIBUTE_PRINTF(2, 0);
  void       va_done(const char* format, va_list ap) ATTRIBUTE_PRINTF(2, 0);

  // write text (with quoting of special XML characters <>&'" etc.)
  outputStream* text() { return _text; }
  void          text(const char* format, ...) ATTRIBUTE_PRINTF(2, 3);
  void       va_text(const char* format, va_list ap) ATTRIBUTE_PRINTF(2, 0) {
    text()->vprint(format, ap);
  }

  // commonly used XML attributes
  void          stamp();                 // stamp='1.234'
  void          method(Method* m);       // method='k n s' ...
  void          klass(Klass* k);         // klass='name'
  void          name(const Symbol* s);   // name='name'
  void          object(const char* attr, Metadata* val);
  void          object(const char* attr, Handle val);

  // print the text alone (sans ''):
  void          method_text(Method* m);
  void          klass_text(Klass* k);         // klass='name'
  void          name_text(const Symbol* s);   // name='name'
  void          object_text(Metadata* x);
  void          object_text(Handle x);

  /*  Example uses:

      // Empty element, simple case.
      elem("X Y='Z'");          <X Y='Z'/> \n

      // Empty element, general case.
      begin_elem("X Y='Z'");    <X Y='Z'
      ...attrs...               ...attrs...
      end_elem();               />

      // Compound element, simple case.
      head("X Y='Z'");          <X Y='Z'> \n
      ...body...                ...body...
      tail("X");                </X> \n

      // Compound element, general case.
      begin_head("X Y='Z'");    <X Y='Z'
      ...attrs...               ...attrs...
      end_head();               > \n
      ...body...                ...body...
      tail("X");                </X> \n

      // Printf-style formatting:
      elem("X Y='%s'", "Z");    <X Y='Z'/> \n

   */

};

#define XML_SPECIAL_SIX "&<>'\"\n"

// Standard log file, null if no logging is happening.
extern xmlStream* xtty;

// Note:  If ::xtty != nullptr, ::tty == ::xtty->text().

#endif // SHARE_UTILITIES_XMLSTREAM_HPP

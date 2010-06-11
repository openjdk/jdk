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

// FILEBUFF.CPP - Routines for handling a parser file buffer
#include "adlc.hpp"

using namespace std;

//------------------------------FileBuff---------------------------------------
// Create a new parsing buffer
FileBuff::FileBuff( BufferedFile *fptr, ArchDesc& archDesc) : _fp(fptr), _AD(archDesc) {
  _err = fseek(_fp->_fp, 0, SEEK_END);  // Seek to end of file
  if (_err) {
    file_error(SEMERR, 0, "File seek error reading input file");
    exit(1);                    // Exit on seek error
  }
  _filepos = ftell(_fp->_fp);   // Find offset of end of file
  _bufferSize = _filepos + 5;   // Filepos points to last char, so add padding
  _err = fseek(_fp->_fp, 0, SEEK_SET);  // Reset to beginning of file
  if (_err) {
    file_error(SEMERR, 0, "File seek error reading input file\n");
    exit(1);                    // Exit on seek error
  }
  _filepos = ftell(_fp->_fp);      // Reset current file position
  _linenum = 0;

  _bigbuf = new char[_bufferSize]; // Create buffer to hold text for parser
  if( !_bigbuf ) {
    file_error(SEMERR, 0, "Buffer allocation failed\n");
    exit(1);                    // Exit on allocation failure
  }
  *_bigbuf = '\n';               // Lead with a sentinel newline
  _buf = _bigbuf+1;                     // Skip sentinel
  _bufmax = _buf;               // Buffer is empty
  _bufeol = _bigbuf;              // _bufeol points at sentinel
  _filepos = -1;                 // filepos is in sync with _bufeol
  _bufoff = _offset = 0L;       // Offset at file start

  _bufmax += fread(_buf, 1, _bufferSize-2, _fp->_fp); // Fill buffer & set end value
  if (_bufmax == _buf) {
    file_error(SEMERR, 0, "File read error, no input read\n");
    exit(1);                     // Exit on read error
  }
  *_bufmax = '\n';               // End with a sentinel new-line
  *(_bufmax+1) = '\0';           // Then end with a sentinel NULL
}

//------------------------------~FileBuff--------------------------------------
// Nuke the FileBuff
FileBuff::~FileBuff() {
  delete _bigbuf;
}

//------------------------------get_line----------------------------------------
char *FileBuff::get_line(void) {
  char *retval;

  // Check for end of file & return NULL
  if (_bufeol >= _bufmax) return NULL;

  _linenum++;
  retval = ++_bufeol;      // return character following end of previous line
  if (*retval == '\0') return NULL; // Check for EOF sentinel
  // Search for newline character which must end each line
  for(_filepos++; *_bufeol != '\n'; _bufeol++)
    _filepos++;                    // keep filepos in sync with _bufeol
  // _bufeol & filepos point at end of current line, so return pointer to start
  return retval;
}

//------------------------------FileBuffRegion---------------------------------
// Create a new region in a FileBuff.
FileBuffRegion::FileBuffRegion( FileBuff* bufr, int soln, int ln,
                                int off, int len)
: _bfr(bufr), _sol(soln), _line(ln), _offset(off), _length(len) {
  _next = NULL;                 // No chained regions
}

//------------------------------~FileBuffRegion--------------------------------
// Delete the entire linked list of buffer regions.
FileBuffRegion::~FileBuffRegion() {
  if( _next ) delete _next;
}

//------------------------------copy-------------------------------------------
// Deep copy a FileBuffRegion
FileBuffRegion *FileBuffRegion::copy() {
  if( !this ) return NULL;      // The empty buffer region
  FileBuffRegion *br = new FileBuffRegion(_bfr,_sol,_line,_offset,_length);
  if( _next ) br->_next = _next->copy();
  return br;
}

//------------------------------merge------------------------------------------
// Merge another buffer region into this buffer region.  Make overlapping areas
// become a single region.  Remove (delete) the input FileBuffRegion.
// Since the buffer regions are sorted by file offset, this is a varient of a
// "sorted-merge" running in linear time.
FileBuffRegion *FileBuffRegion::merge( FileBuffRegion *br ) {
  if( !br ) return this;        // Merging nothing
  if( !this ) return br;        // Merging into nothing

  assert( _bfr == br->_bfr, "" );     // Check for pointer-equivalent buffers

  if( _offset < br->_offset ) { // "this" starts before "br"
    if( _offset+_length < br->_offset ) { // "this" ends before "br"
      if( _next ) _next->merge( br );    // Merge with remainder of list
      else _next = br;                 // No more in this list; just append.
    } else {                           // Regions overlap.
      int l = br->_offset + br->_length - _offset;
      if( l > _length ) _length = l;     // Pick larger region
      FileBuffRegion *nr = br->_next;     // Get rest of region
      br->_next = NULL;         // Remove indication of rest of region
      delete br;                // Delete this region (it's been subsumed).
      if( nr ) merge( nr );     // Merge with rest of region
    }                           // End of if regions overlap or not.
  } else {                      // "this" starts after "br"
    if( br->_offset+br->_length < _offset ) {    // "br" ends before "this"
      FileBuffRegion *nr = new FileBuffRegion(_bfr,_sol,_line,_offset,_length);
      nr->_next = _next;                // Structure copy "this" guy to "nr"
      *this = *br;              // Structure copy "br" over "this".
      br->_next = NULL;         // Remove indication of rest of region
      delete br;                // Delete this region (it's been copied)
      merge( nr );              // Finish merging
    } else {                    // Regions overlap.
      int l = _offset + _length - br->_offset;
      if( l > _length ) _length = l;    // Pick larger region
      _offset = br->_offset;            // Start with earlier region
      _sol = br->_sol;                  // Also use earlier line start
      _line = br->_line;                        // Also use earlier line
      FileBuffRegion *nr = br->_next;   // Get rest of region
      br->_next = NULL;         // Remove indication of rest of region
      delete br;                // Delete this region (it's been subsumed).
      if( nr ) merge( nr );     // Merge with rest of region
    }                           // End of if regions overlap or not.
  }
  return this;
}

//------------------------------expandtab--------------------------------------
static int expandtab( ostream &os, int off, char c, char fill1, char fill2 ) {
  if( c == '\t' ) {             // Tab?
    do os << fill1;             // Expand the tab; Output space
    while( (++off) & 7 );       // Expand to tab stop
  } else {                      // Normal character
    os << fill2;                // Display normal character
    off++;                      // Increment "cursor" offset
  }
  return off;
}

//------------------------------printline--------------------------------------
// Print and highlite a region of a line.  Return the amount of highliting left
// to do (i.e. highlite length minus length of line).
static int printline( ostream& os, const char *fname, int line,
                        const char *_sol, int skip, int len ) {

  // Display the entire tab-expanded line
  os << fname << ":" << line << ": ";
  const char *t = strchr(_sol,'\n')+1; // End of line
  int off = 0;                  // Cursor offset for tab expansion
  const char *s = _sol;         // Nice string pointer
  while( t-s ) {                // Display whole line
    char c = *s++;              // Get next character to display
    off = expandtab(os,off,c,' ',c);
  }

  // Display the tab-expanded skippings before underlining.
  os << fname << ":" << line << ": ";
  off = 0;                      // Cursor offset for tab expansion
  s = _sol;                     // Restart string pointer

  // Start underlining.
  if( skip != -1 ) {            // The no-start-indicating flag
    const char *u = _sol+skip;  // Amount to skip
    while( u-s )                // Display skipped part
      off = expandtab(os,off,*s++,' ',' ');
    os << '^';                  // Start region
    off++;                      // Moved cursor
    len--;                      // 1 less char to do
    if( *s++ == '\t' )          // Starting character is a tab?
      off = expandtab(os,off,'\t','-','^');
  }

  // Long region doesn't end on this line
  int llen = (int)(t-s);        // Length of line, minus what's already done
  if( len > llen ) {            // Doing entire rest of line?
    while( t-s )                // Display rest of line
      off = expandtab(os,off,*s++,'-','-');
    os << '\n';                 // EOL
    return len-llen;            // Return what's not yet done.
  }

  // Region does end on this line.  This code fails subtly if the region ends
  // in a tab character.
  int i;
  for( i=1; i<len; i++ )        // Underline just what's needed
    off = expandtab(os,off,*s++,'-','-');
  if( i == len ) os << '^';     // Mark end of region
  os << '\n';                   // End of marked line
  return 0;                     // All done
}

//------------------------------print------------------------------------------
//std::ostream& operator<< ( std::ostream& os, FileBuffRegion &br ) {
ostream& operator<< ( ostream& os, FileBuffRegion &br ) {
  if( &br == NULL ) return os;  // The empty buffer region
  FileBuffRegion *brp = &br;    // Pointer to region
  while( brp ) {                // While have chained regions
    brp->print(os);             // Print region
    brp = brp->_next;           // Chain to next
  }
  return os;                    // Return final stream
}

//------------------------------print------------------------------------------
// Print the FileBuffRegion to a stream. FileBuffRegions are printed with the
// filename and line number to the left, and complete text lines to the right.
// Selected portions (portions of a line actually in the FileBuffRegion are
// underlined.  Ellipses are used for long multi-line regions.
//void FileBuffRegion::print( std::ostream& os ) {
void FileBuffRegion::print( ostream& os ) {
  if( !this ) return;           // Nothing to print
  char *s = _bfr->get_line();
  int skip = (int)(_offset - _sol);     // Amount to skip to start of data
  int len = printline( os, _bfr->_fp->_name, _line, s, skip, _length );

  if( !len ) return;                    // All done; exit

  // Here we require at least 2 lines
  int off1 = _length - len + skip;      // Length of line 1
  int off2 = off1 + _sol;               // Offset to start of line 2
  char *s2 = _bfr->get_line();           // Start of line 2
  char *s3 = strchr( s2, '\n' )+1;      // Start of line 3 (unread)
  if( len <= (s3-s2) ) {                // It all fits on the next line
    printline( os, _bfr->_fp->_name, _line+1, s2, -1, len ); // Print&underline
    return;
  }

  // Here we require at least 3 lines
  int off3 = off2 + (int)(s3-s2);       // Offset to start of line 3
  s3 = _bfr->get_line();                // Start of line 3 (read)
  const char *s4 = strchr( s3, '\n' )+1;// Start of line 4 (unread)
  if( len < (s4-s3) ) {                 // It all fits on the next 2 lines
    s2 = _bfr->get_line();
    len = printline( os, _bfr->_fp->_name, _line+1, s2, -1, len ); // Line 2
    s3 = _bfr->get_line();
    printline( os, _bfr->_fp->_name, _line+2, s3, -1, len );     // Line 3
    return;
  }

  // Here we require at least 4 lines.
  // Print only the 1st and last line, with ellipses in middle.
  os << "...\n";                // The ellipses
  int cline = _line+1;          // Skipped 2 lines
  do {                          // Do until find last line
    len -= (int)(s3-s2);        // Remove length of line
    cline++;                    // Next line
    s2 = _bfr->get_line();      // Get next line from end of this line
    s3 = strchr( s2, '\n' ) + 1;// Get end of next line
  } while( len > (s3-s2) );     // Repeat until last line
  printline( os, _bfr->_fp->_name, cline, s2, -1, len ); // Print & underline
}

//------------------------------file_error-------------------------------------
void FileBuff::file_error(int flag, int linenum, const char *fmt, ...)
{
  va_list args;

  va_start(args, fmt);
  switch (flag) {
  case 0: _AD._warnings += _AD.emit_msg(0, flag, linenum, fmt, args);
  case 1: _AD._syntax_errs += _AD.emit_msg(0, flag, linenum, fmt, args);
  case 2: _AD._semantic_errs += _AD.emit_msg(0, flag, linenum, fmt, args);
  default: assert(0, ""); break;
  }
  va_end(args);
  _AD._no_output = 1;
}

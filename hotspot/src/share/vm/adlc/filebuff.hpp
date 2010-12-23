/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_ADLC_FILEBUFF_HPP
#define SHARE_VM_ADLC_FILEBUFF_HPP

// FILEBUFF.HPP - Definitions for parser file buffering routines
#include <iostream>

using namespace std;

// STRUCTURE FOR HANDLING INPUT AND OUTPUT FILES
typedef struct {
  const char *_name;
  FILE *_fp;
} BufferedFile;

class ArchDesc;

//------------------------------FileBuff--------------------------------------
// This class defines a nicely behaved buffer of text.  Entire file of text
// is read into buffer at creation, with sentinels at start and end.
class FileBuff {
  friend class FileBuffRegion;
 private:
  long  _bufferSize;            // Size of text holding buffer.
  long  _offset;                // Expected filepointer offset.
  long  _bufoff;                // Start of buffer file offset

  char *_buf;                   // The buffer itself.
  char *_bigbuf;                // The buffer plus sentinels; actual heap area
  char *_bufmax;                // A pointer to the buffer end sentinel
  char *_bufeol;                // A pointer to the last complete line end

  int   _err;                   // Error flag for file seek/read operations
  long  _filepos;               // Current offset from start of file
  int   _linenum;

  ArchDesc& _AD;                // Reference to Architecture Description

  // Error reporting function
  void file_error(int flag, int linenum, const char *fmt, ...);

 public:
  const BufferedFile *_fp;           // File to be buffered

  FileBuff(BufferedFile *fp, ArchDesc& archDesc); // Constructor
  ~FileBuff();                  // Destructor

  // This returns a pointer to the start of the current line in the buffer,
  // and increments bufeol and filepos to point at the end of that line.
  char *get_line(void);
  int linenum() const { return _linenum; }
  void set_linenum(int line) { _linenum = line; }

  // This converts a pointer into the buffer to a file offset.  It only works
  // when the pointer is valid (i.e. just obtained from getline()).
  long getoff(const char* s) { return _bufoff + (long)(s - _buf); }
};

//------------------------------FileBuffRegion---------------------------------
// A buffer region is really a region of some file, specified as a linked list
// of offsets and lengths.  These regions can be merged; overlapping regions
// will coalesce.
class FileBuffRegion {
 public:                        // Workaround dev-studio friend/private bug
  FileBuffRegion *_next;        // Linked list of regions sorted by offset.
 private:
  FileBuff       *_bfr;         // The Buffer of the file
  int _offset, _length;         // The file area
  int             _sol;         // Start of line where the file area starts
  int             _line;        // First line of region

 public:
  FileBuffRegion(FileBuff*, int sol, int line, int offset, int len);
  ~FileBuffRegion();

  FileBuffRegion *copy();                   // Deep copy
  FileBuffRegion *merge(FileBuffRegion*); // Merge 2 regions; delete input

  void print(ostream&);
  friend ostream& operator<< (ostream&, FileBuffRegion&);
};

#endif // SHARE_VM_ADLC_FILEBUFF_HPP

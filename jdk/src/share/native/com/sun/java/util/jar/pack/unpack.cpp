/*
 * Copyright (c) 2001, 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

// -*- C++ -*-
// Program for unpacking specially compressed Java packages.
// John R. Rose

/*
 * When compiling for a 64bit LP64 system (longs and pointers being 64bits),
 *    the printf format %ld is correct and use of %lld will cause warning
 *    errors from some compilers (gcc/g++).
 * _LP64 can be explicitly set (used on Linux).
 * Solaris compilers will define __sparcv9 or __x86_64 on 64bit compilations.
 */
#if defined(_LP64) || defined(__sparcv9) || defined(__x86_64)
  #define LONG_LONG_FORMAT "%ld"
  #define LONG_LONG_HEX_FORMAT "%lx"
#else
  #define LONG_LONG_FORMAT "%lld"
  #define LONG_LONG_HEX_FORMAT "%016llx"
#endif

#include <sys/types.h>

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <stdarg.h>

#include <limits.h>
#include <time.h>




#include "defines.h"
#include "bytes.h"
#include "utils.h"
#include "coding.h"
#include "bands.h"

#include "constants.h"

#include "zip.h"

#include "unpack.h"


// tags, in canonical order:
static const byte TAGS_IN_ORDER[] = {
  CONSTANT_Utf8,
  CONSTANT_Integer,
  CONSTANT_Float,
  CONSTANT_Long,
  CONSTANT_Double,
  CONSTANT_String,
  CONSTANT_Class,
  CONSTANT_Signature,
  CONSTANT_NameandType,
  CONSTANT_Fieldref,
  CONSTANT_Methodref,
  CONSTANT_InterfaceMethodref
};
#define N_TAGS_IN_ORDER (sizeof TAGS_IN_ORDER)

#ifndef PRODUCT
static const char* TAG_NAME[] = {
  "*None",
  "Utf8",
  "*Unicode",
  "Integer",
  "Float",
  "Long",
  "Double",
  "Class",
  "String",
  "Fieldref",
  "Methodref",
  "InterfaceMethodref",
  "NameandType",
  "*Signature",
  0
};

static const char* ATTR_CONTEXT_NAME[] = {  // match ATTR_CONTEXT_NAME, etc.
  "class", "field", "method", "code"
};

#else

#define ATTR_CONTEXT_NAME ((const char**)null)

#endif


// REQUESTED must be -2 for u2 and REQUESTED_LDC must be -1 for u1
enum { NOT_REQUESTED = 0, REQUESTED = -2, REQUESTED_LDC = -1 };

#define NO_INORD ((uint)-1)

struct entry {
  byte tag;

  #if 0
  byte bits;
  enum {
    //EB_EXTRA = 1,
    EB_SUPER = 2
  };
  #endif
  unsigned short nrefs;  // pack w/ tag

  int  outputIndex;
  uint inord;   // &cp.entries[cp.tag_base[this->tag]+this->inord] == this

  entry* *refs;

  // put last to pack best
  union {
    bytes b;
    int i;
    jlong l;
  } value;

  void requestOutputIndex(cpool& cp, int req = REQUESTED);
  int getOutputIndex() {
    assert(outputIndex > NOT_REQUESTED);
    return outputIndex;
  }

  entry* ref(int refnum) {
    assert((uint)refnum < nrefs);
    return refs[refnum];
  }

  const char* utf8String() {
    assert(tagMatches(CONSTANT_Utf8));
    assert(value.b.len == strlen((const char*)value.b.ptr));
    return (const char*)value.b.ptr;
  }

  entry* className() {
    assert(tagMatches(CONSTANT_Class));
    return ref(0);
  }

  entry* memberClass() {
    assert(tagMatches(CONSTANT_Member));
    return ref(0);
  }

  entry* memberDescr() {
    assert(tagMatches(CONSTANT_Member));
    return ref(1);
  }

  entry* descrName() {
    assert(tagMatches(CONSTANT_NameandType));
    return ref(0);
  }

  entry* descrType() {
    assert(tagMatches(CONSTANT_NameandType));
    return ref(1);
  }

  int typeSize();

  bytes& asUtf8();
  int    asInteger() { assert(tag == CONSTANT_Integer); return value.i; }

  bool isUtf8(bytes& b) { return tagMatches(CONSTANT_Utf8) && value.b.equals(b); }

  bool isDoubleWord() { return tag == CONSTANT_Double || tag == CONSTANT_Long; }

  bool tagMatches(byte tag2) {
    return (tag2 == tag)
      || (tag2 == CONSTANT_Utf8 && tag == CONSTANT_Signature)
      #ifndef PRODUCT
      || (tag2 == CONSTANT_Literal
          && tag >= CONSTANT_Integer && tag <= CONSTANT_String && tag != CONSTANT_Class)
      || (tag2 == CONSTANT_Member
          && tag >= CONSTANT_Fieldref && tag <= CONSTANT_InterfaceMethodref)
      #endif
      ;
  }

#ifdef PRODUCT
  char* string() { return 0; }
#else
  char* string();  // see far below
#endif
};

entry* cpindex::get(uint i) {
  if (i >= len)
    return null;
  else if (base1 != null)
    // primary index
    return &base1[i];
  else
    // secondary index
    return base2[i];
}

inline bytes& entry::asUtf8() {
  assert(tagMatches(CONSTANT_Utf8));
  return value.b;
}

int entry::typeSize() {
  assert(tagMatches(CONSTANT_Utf8));
  const char* sigp = (char*) value.b.ptr;
  switch (*sigp) {
  case '(': sigp++; break;  // skip opening '('
  case 'D':
  case 'J': return 2; // double field
  default:  return 1; // field
  }
  int siglen = 0;
  for (;;) {
    int ch = *sigp++;
    switch (ch) {
    case 'D': case 'J':
      siglen += 1;
      break;
    case '[':
      // Skip rest of array info.
      while (ch == '[') { ch = *sigp++; }
      if (ch != 'L')  break;
      // else fall through
    case 'L':
      sigp = strchr(sigp, ';');
      if (sigp == null) {
          unpack_abort("bad data");
          return 0;
      }
      sigp += 1;
      break;
    case ')':  // closing ')'
      return siglen;
    }
    siglen += 1;
  }
}

inline cpindex* cpool::getFieldIndex(entry* classRef) {
  assert(classRef->tagMatches(CONSTANT_Class));
  assert((uint)classRef->inord < (uint)tag_count[CONSTANT_Class]);
  return &member_indexes[classRef->inord*2+0];
}
inline cpindex* cpool::getMethodIndex(entry* classRef) {
  assert(classRef->tagMatches(CONSTANT_Class));
  assert((uint)classRef->inord < (uint)tag_count[CONSTANT_Class]);
  return &member_indexes[classRef->inord*2+1];
}

struct inner_class {
  entry* inner;
  entry* outer;
  entry* name;
  int    flags;
  inner_class* next_sibling;
  bool   requested;
};

// Here is where everything gets deallocated:
void unpacker::free() {
  int i;
  assert(jniobj == null); // caller resp.
  assert(infileptr == null);  // caller resp.
  if (jarout != null)  jarout->reset();
  if (gzin != null)    { gzin->free(); gzin = null; }
  if (free_input)  input.free();
  // free everybody ever allocated with U_NEW or (recently) with T_NEW
  assert(smallbuf.base()  == null || mallocs.contains(smallbuf.base()));
  assert(tsmallbuf.base() == null || tmallocs.contains(tsmallbuf.base()));
  mallocs.freeAll();
  tmallocs.freeAll();
  smallbuf.init();
  tsmallbuf.init();
  bcimap.free();
  class_fixup_type.free();
  class_fixup_offset.free();
  class_fixup_ref.free();
  code_fixup_type.free();
  code_fixup_offset.free();
  code_fixup_source.free();
  requested_ics.free();
  cur_classfile_head.free();
  cur_classfile_tail.free();
  for (i = 0; i < ATTR_CONTEXT_LIMIT; i++)
    attr_defs[i].free();

  // free CP state
  cp.outputEntries.free();
  for (i = 0; i < CONSTANT_Limit; i++)
    cp.tag_extras[i].free();
}

// input handling
// Attempts to advance rplimit so that (rplimit-rp) is at least 'more'.
// Will eagerly read ahead by larger chunks, if possible.
// Returns false if (rplimit-rp) is not at least 'more',
// unless rplimit hits input.limit().
bool unpacker::ensure_input(jlong more) {
  julong want = more - input_remaining();
  if ((jlong)want <= 0)          return true;  // it's already in the buffer
  if (rplimit == input.limit())  return true;  // not expecting any more

  if (read_input_fn == null) {
    // assume it is already all there
    bytes_read += input.limit() - rplimit;
    rplimit = input.limit();
    return true;
  }
  CHECK_0;

  julong remaining = (input.limit() - rplimit);  // how much left to read?
  byte* rpgoal = (want >= remaining)? input.limit(): rplimit + (size_t)want;
  enum { CHUNK_SIZE = (1<<14) };
  julong fetch = want;
  if (fetch < CHUNK_SIZE)
    fetch = CHUNK_SIZE;
  if (fetch > remaining*3/4)
    fetch = remaining;
  // Try to fetch at least "more" bytes.
  while ((jlong)fetch > 0) {
    jlong nr = (*read_input_fn)(this, rplimit, fetch, remaining);
    if (nr <= 0) {
      return (rplimit >= rpgoal);
    }
    remaining -= nr;
    rplimit += nr;
    fetch -= nr;
    bytes_read += nr;
    assert(remaining == (julong)(input.limit() - rplimit));
  }
  return true;
}

// output handling

fillbytes* unpacker::close_output(fillbytes* which) {
  assert(wp != null);
  if (which == null) {
    if (wpbase == cur_classfile_head.base()) {
      which = &cur_classfile_head;
    } else {
      which = &cur_classfile_tail;
    }
  }
  assert(wpbase  == which->base());
  assert(wplimit == which->end());
  which->setLimit(wp);
  wp      = null;
  wplimit = null;
  //wpbase = null;
  return which;
}

//maybe_inline
void unpacker::ensure_put_space(size_t size) {
  if (wp + size <= wplimit)  return;
  // Determine which segment needs expanding.
  fillbytes* which = close_output();
  byte* wp0 = which->grow(size);
  wpbase  = which->base();
  wplimit = which->end();
  wp = wp0;
}

maybe_inline
byte* unpacker::put_space(size_t size) {
  byte* wp0 = wp;
  byte* wp1 = wp0 + size;
  if (wp1 > wplimit) {
    ensure_put_space(size);
    wp0 = wp;
    wp1 = wp0 + size;
  }
  wp = wp1;
  return wp0;
}

maybe_inline
void unpacker::putu2_at(byte* wp, int n) {
  if (n != (unsigned short)n) {
    unpack_abort(ERROR_OVERFLOW);
    return;
  }
  wp[0] = (n) >> 8;
  wp[1] = (n) >> 0;
}

maybe_inline
void unpacker::putu4_at(byte* wp, int n) {
  wp[0] = (n) >> 24;
  wp[1] = (n) >> 16;
  wp[2] = (n) >> 8;
  wp[3] = (n) >> 0;
}

maybe_inline
void unpacker::putu8_at(byte* wp, jlong n) {
  putu4_at(wp+0, (int)((julong)n >> 32));
  putu4_at(wp+4, (int)((julong)n >> 0));
}

maybe_inline
void unpacker::putu2(int n) {
  putu2_at(put_space(2), n);
}

maybe_inline
void unpacker::putu4(int n) {
  putu4_at(put_space(4), n);
}

maybe_inline
void unpacker::putu8(jlong n) {
  putu8_at(put_space(8), n);
}

maybe_inline
int unpacker::putref_index(entry* e, int size) {
  if (e == null)
    return 0;
  else if (e->outputIndex > NOT_REQUESTED)
    return e->outputIndex;
  else if (e->tag == CONSTANT_Signature)
    return putref_index(e->ref(0), size);
  else {
    e->requestOutputIndex(cp, -size);
    // Later on we'll fix the bits.
    class_fixup_type.addByte(size);
    class_fixup_offset.add((int)wpoffset());
    class_fixup_ref.add(e);
#ifdef PRODUCT
    return 0;
#else
    return 0x20+size;  // 0x22 is easy to eyeball
#endif
  }
}

maybe_inline
void unpacker::putref(entry* e) {
  int oidx = putref_index(e, 2);
  putu2_at(put_space(2), oidx);
}

maybe_inline
void unpacker::putu1ref(entry* e) {
  int oidx = putref_index(e, 1);
  putu1_at(put_space(1), oidx);
}


static int total_cp_size[] = {0, 0};
static int largest_cp_ref[] = {0, 0};
static int hash_probes[] = {0, 0};

// Allocation of small and large blocks.

enum { CHUNK = (1 << 14), SMALL = (1 << 9) };

// Call malloc.  Try to combine small blocks and free much later.
void* unpacker::alloc_heap(size_t size, bool smallOK, bool temp) {
  CHECK_0;
  if (!smallOK || size > SMALL) {
    void* res = must_malloc((int)size);
    (temp ? &tmallocs : &mallocs)->add(res);
    return res;
  }
  fillbytes& xsmallbuf = *(temp ? &tsmallbuf : &smallbuf);
  if (!xsmallbuf.canAppend(size+1)) {
    xsmallbuf.init(CHUNK);
    (temp ? &tmallocs : &mallocs)->add(xsmallbuf.base());
  }
  int growBy = (int)size;
  growBy += -growBy & 7;  // round up mod 8
  return xsmallbuf.grow(growBy);
}

maybe_inline
void unpacker::saveTo(bytes& b, byte* ptr, size_t len) {
  b.ptr = U_NEW(byte, add_size(len,1));
  if (aborting()) {
    b.len = 0;
    return;
  }
  b.len = len;
  b.copyFrom(ptr, len);
}

// Read up through band_headers.
// Do the archive_size dance to set the size of the input mega-buffer.
void unpacker::read_file_header() {
  // Read file header to determine file type and total size.
  enum {
    MAGIC_BYTES = 4,
    AH_LENGTH_0 = 3,  //minver, majver, options are outside of archive_size
    AH_LENGTH_0_MAX = AH_LENGTH_0 + 1,  // options might have 2 bytes
    AH_LENGTH   = 26, //maximum archive header length (w/ all fields)
    // Length contributions from optional header fields:
    AH_FILE_HEADER_LEN = 5, // sizehi/lo/next/modtime/files
    AH_ARCHIVE_SIZE_LEN = 2, // sizehi/lo only; part of AH_FILE_HEADER_LEN
    AH_CP_NUMBER_LEN = 4,  // int/float/long/double
    AH_SPECIAL_FORMAT_LEN = 2, // layouts/band-headers
    AH_LENGTH_MIN = AH_LENGTH
        -(AH_FILE_HEADER_LEN+AH_SPECIAL_FORMAT_LEN+AH_CP_NUMBER_LEN),
    ARCHIVE_SIZE_MIN = AH_LENGTH_MIN - (AH_LENGTH_0 + AH_ARCHIVE_SIZE_LEN),
    FIRST_READ  = MAGIC_BYTES + AH_LENGTH_MIN
  };

  assert(AH_LENGTH_MIN    == 15); // # of UNSIGNED5 fields required after archive_magic
  assert(ARCHIVE_SIZE_MIN == 10); // # of UNSIGNED5 fields required after archive_size
  // An absolute minimum null archive is magic[4], {minver,majver,options}[3],
  // archive_size[0], cp_counts[8], class_counts[4], for a total of 19 bytes.
  // (Note that archive_size is optional; it may be 0..10 bytes in length.)
  // The first read must capture everything up through the options field.
  // This happens to work even if {minver,majver,options} is a pathological
  // 15 bytes long.  Legal pack files limit those three fields to 1+1+2 bytes.
  assert(FIRST_READ >= MAGIC_BYTES + AH_LENGTH_0 * B_MAX);

  // Up through archive_size, the largest possible archive header is
  // magic[4], {minver,majver,options}[4], archive_size[10].
  // (Note only the low 12 bits of options are allowed to be non-zero.)
  // In order to parse archive_size, we need at least this many bytes
  // in the first read.  Of course, if archive_size_hi is more than
  // a byte, we probably will fail to allocate the buffer, since it
  // will be many gigabytes long.  This is a practical, not an
  // architectural limit to Pack200 archive sizes.
  assert(FIRST_READ >= MAGIC_BYTES + AH_LENGTH_0_MAX + 2*B_MAX);

  bool foreign_buf = (read_input_fn == null);
  byte initbuf[(int)FIRST_READ + (int)C_SLOP + 200];  // 200 is for JAR I/O
  if (foreign_buf) {
    // inbytes is all there is
    input.set(inbytes);
    rp      = input.base();
    rplimit = input.limit();
  } else {
    // inbytes, if not empty, contains some read-ahead we must use first
    // ensure_input will take care of copying it into initbuf,
    // then querying read_input_fn for any additional data needed.
    // However, the caller must assume that we use up all of inbytes.
    // There is no way to tell the caller that we used only part of them.
    // Therefore, the caller must use only a bare minimum of read-ahead.
    if (inbytes.len > FIRST_READ) {
      abort("too much read-ahead");
      return;
    }
    input.set(initbuf, sizeof(initbuf));
    input.b.clear();
    input.b.copyFrom(inbytes);
    rplimit = rp = input.base();
    rplimit += inbytes.len;
    bytes_read += inbytes.len;
  }
  // Read only 19 bytes, which is certain to contain #archive_options fields,
  // but is certain not to overflow past the archive_header.
  input.b.len = FIRST_READ;
  if (!ensure_input(FIRST_READ))
    abort("EOF reading archive magic number");

  if (rp[0] == 'P' && rp[1] == 'K') {
#ifdef UNPACK_JNI
    // Java driver must handle this case before we get this far.
    abort("encountered a JAR header in unpacker");
#else
    // In the Unix-style program, we simply simulate a copy command.
    // Copy until EOF; assume the JAR file is the last segment.
    fprintf(errstrm, "Copy-mode.\n");
    for (;;) {
      jarout->write_data(rp, (int)input_remaining());
      if (foreign_buf)
        break;  // one-time use of a passed in buffer
      if (input.size() < CHUNK) {
        // Get some breathing room.
        input.set(U_NEW(byte, (size_t) CHUNK + C_SLOP), (size_t) CHUNK);
        CHECK;
      }
      rp = rplimit = input.base();
      if (!ensure_input(1))
        break;
    }
    jarout->closeJarFile(false);
#endif
    return;
  }

  // Read the magic number.
  magic = 0;
  for (int i1 = 0; i1 < (int)sizeof(magic); i1++) {
    magic <<= 8;
    magic += (*rp++ & 0xFF);
  }

  // Read the first 3 values from the header.
  value_stream hdr;
  int          hdrVals = 0;
  int          hdrValsSkipped = 0;  // debug only
  hdr.init(rp, rplimit, UNSIGNED5_spec);
  minver = hdr.getInt();
  majver = hdr.getInt();
  hdrVals += 2;

  if (magic != (int)JAVA_PACKAGE_MAGIC ||
      (majver != JAVA5_PACKAGE_MAJOR_VERSION  &&
       majver != JAVA6_PACKAGE_MAJOR_VERSION) ||
      (minver != JAVA5_PACKAGE_MINOR_VERSION  &&
       minver != JAVA6_PACKAGE_MINOR_VERSION)) {
    char message[200];
    sprintf(message, "@" ERROR_FORMAT ": magic/ver = "
            "%08X/%d.%d should be %08X/%d.%d OR %08X/%d.%d\n",
            magic, majver, minver,
            JAVA_PACKAGE_MAGIC, JAVA5_PACKAGE_MAJOR_VERSION, JAVA5_PACKAGE_MINOR_VERSION,
            JAVA_PACKAGE_MAGIC, JAVA6_PACKAGE_MAJOR_VERSION, JAVA6_PACKAGE_MINOR_VERSION);
    abort(message);
  }
  CHECK;

  archive_options = hdr.getInt();
  hdrVals += 1;
  assert(hdrVals == AH_LENGTH_0);  // first three fields only

#define ORBIT(bit) |(bit)
  int OPTION_LIMIT = (0 ARCHIVE_BIT_DO(ORBIT));
#undef ORBIT
  if ((archive_options & ~OPTION_LIMIT) != 0) {
    fprintf(errstrm, "Warning: Illegal archive options 0x%x\n",
        archive_options);
    abort("illegal archive options");
    return;
  }

  if ((archive_options & AO_HAVE_FILE_HEADERS) != 0) {
    uint hi = hdr.getInt();
    uint lo = hdr.getInt();
    julong x = band::makeLong(hi, lo);
    archive_size = (size_t) x;
    if (archive_size != x) {
      // Silly size specified; force overflow.
      archive_size = PSIZE_MAX+1;
    }
    hdrVals += 2;
  } else {
    hdrValsSkipped += 2;
  }

  // Now we can size the whole archive.
  // Read everything else into a mega-buffer.
  rp = hdr.rp;
  int header_size_0 = (int)(rp - input.base()); // used-up header (4byte + 3int)
  int header_size_1 = (int)(rplimit - rp);      // buffered unused initial fragment
  int header_size   = header_size_0+header_size_1;
  unsized_bytes_read = header_size_0;
  CHECK;
  if (foreign_buf) {
    if (archive_size > (size_t)header_size_1) {
      abort("EOF reading fixed input buffer");
      return;
    }
  } else if (archive_size != 0) {
    if (archive_size < ARCHIVE_SIZE_MIN) {
      abort("impossible archive size");  // bad input data
      return;
    }
    if (archive_size < header_size_1) {
      abort("too much read-ahead");  // somehow we pre-fetched too much?
      return;
    }
    input.set(U_NEW(byte, add_size(header_size_0, archive_size, C_SLOP)),
              (size_t) header_size_0 + archive_size);
    CHECK;
    assert(input.limit()[0] == 0);
    // Move all the bytes we read initially into the real buffer.
    input.b.copyFrom(initbuf, header_size);
    rp      = input.b.ptr + header_size_0;
    rplimit = input.b.ptr + header_size;
  } else {
    // It's more complicated and painful.
    // A zero archive_size means that we must read until EOF.
    input.init(CHUNK*2);
    CHECK;
    input.b.len = input.allocated;
    rp = rplimit = input.base();
    // Set up input buffer as if we already read the header:
    input.b.copyFrom(initbuf, header_size);
    CHECK;
    rplimit += header_size;
    while (ensure_input(input.limit() - rp)) {
      size_t dataSoFar = input_remaining();
      size_t nextSize = add_size(dataSoFar, CHUNK);
      input.ensureSize(nextSize);
      CHECK;
      input.b.len = input.allocated;
      rp = rplimit = input.base();
      rplimit += dataSoFar;
    }
    size_t dataSize = (rplimit - input.base());
    input.b.len = dataSize;
    input.grow(C_SLOP);
    CHECK;
    free_input = true;  // free it later
    input.b.len = dataSize;
    assert(input.limit()[0] == 0);
    rp = rplimit = input.base();
    rplimit += dataSize;
    rp += header_size_0;  // already scanned these bytes...
  }
  live_input = true;    // mark as "do not reuse"
  if (aborting()) {
    abort("cannot allocate large input buffer for package file");
    return;
  }

  // read the rest of the header fields
  ensure_input((AH_LENGTH-AH_LENGTH_0) * B_MAX);
  CHECK;
  hdr.rp      = rp;
  hdr.rplimit = rplimit;

  if ((archive_options & AO_HAVE_FILE_HEADERS) != 0) {
    archive_next_count = hdr.getInt();
    CHECK_COUNT(archive_next_count);
    archive_modtime = hdr.getInt();
    file_count = hdr.getInt();
    CHECK_COUNT(file_count);
    hdrVals += 3;
  } else {
    hdrValsSkipped += 3;
  }

  if ((archive_options & AO_HAVE_SPECIAL_FORMATS) != 0) {
    band_headers_size = hdr.getInt();
    CHECK_COUNT(band_headers_size);
    attr_definition_count = hdr.getInt();
    CHECK_COUNT(attr_definition_count);
    hdrVals += 2;
  } else {
    hdrValsSkipped += 2;
  }

  int cp_counts[N_TAGS_IN_ORDER];
  for (int k = 0; k < (int)N_TAGS_IN_ORDER; k++) {
    if (!(archive_options & AO_HAVE_CP_NUMBERS)) {
      switch (TAGS_IN_ORDER[k]) {
      case CONSTANT_Integer:
      case CONSTANT_Float:
      case CONSTANT_Long:
      case CONSTANT_Double:
        cp_counts[k] = 0;
        hdrValsSkipped += 1;
        continue;
      }
    }
    cp_counts[k] = hdr.getInt();
    CHECK_COUNT(cp_counts[k]);
    hdrVals += 1;
  }

  ic_count = hdr.getInt();
  CHECK_COUNT(ic_count);
  default_class_minver = hdr.getInt();
  default_class_majver = hdr.getInt();
  class_count = hdr.getInt();
  CHECK_COUNT(class_count);
  hdrVals += 4;

  // done with archive_header
  hdrVals += hdrValsSkipped;
  assert(hdrVals == AH_LENGTH);
#ifndef PRODUCT
  int assertSkipped = AH_LENGTH - AH_LENGTH_MIN;
  if ((archive_options & AO_HAVE_FILE_HEADERS) != 0)
    assertSkipped -= AH_FILE_HEADER_LEN;
  if ((archive_options & AO_HAVE_SPECIAL_FORMATS) != 0)
    assertSkipped -= AH_SPECIAL_FORMAT_LEN;
  if ((archive_options & AO_HAVE_CP_NUMBERS) != 0)
    assertSkipped -= AH_CP_NUMBER_LEN;
  assert(hdrValsSkipped == assertSkipped);
#endif //PRODUCT

  rp = hdr.rp;
  if (rp > rplimit)
    abort("EOF reading archive header");

  // Now size the CP.
#ifndef PRODUCT
  bool x = (N_TAGS_IN_ORDER == cpool::NUM_COUNTS);
  assert(x);
#endif //PRODUCT
  cp.init(this, cp_counts);
  CHECK;

  default_file_modtime = archive_modtime;
  if (default_file_modtime == 0 && !(archive_options & AO_HAVE_FILE_MODTIME))
    default_file_modtime = DEFAULT_ARCHIVE_MODTIME;  // taken from driver
  if ((archive_options & AO_DEFLATE_HINT) != 0)
    default_file_options |= FO_DEFLATE_HINT;

  // meta-bytes, if any, immediately follow archive header
  //band_headers.readData(band_headers_size);
  ensure_input(band_headers_size);
  if (input_remaining() < (size_t)band_headers_size) {
    abort("EOF reading band headers");
    return;
  }
  bytes band_headers;
  // The "1+" allows an initial byte to be pushed on the front.
  band_headers.set(1+U_NEW(byte, 1+band_headers_size+C_SLOP),
                   band_headers_size);
  CHECK;
  // Start scanning band headers here:
  band_headers.copyFrom(rp, band_headers.len);
  rp += band_headers.len;
  assert(rp <= rplimit);
  meta_rp = band_headers.ptr;
  // Put evil meta-codes at the end of the band headers,
  // so we are sure to throw an error if we run off the end.
  bytes::of(band_headers.limit(), C_SLOP).clear(_meta_error);
}

void unpacker::finish() {
  if (verbose >= 1) {
    fprintf(errstrm,
            "A total of "
            LONG_LONG_FORMAT " bytes were read in %d segment(s).\n",
            (bytes_read_before_reset+bytes_read),
            segments_read_before_reset+1);
    fprintf(errstrm,
            "A total of "
            LONG_LONG_FORMAT " file content bytes were written.\n",
            (bytes_written_before_reset+bytes_written));
    fprintf(errstrm,
            "A total of %d files (of which %d are classes) were written to output.\n",
            files_written_before_reset+files_written,
            classes_written_before_reset+classes_written);
  }
  if (jarout != null)
    jarout->closeJarFile(true);
  if (errstrm != null) {
    if (errstrm == stdout || errstrm == stderr) {
      fflush(errstrm);
    } else {
      fclose(errstrm);
    }
    errstrm = null;
    errstrm_name = null;
  }
}


// Cf. PackageReader.readConstantPoolCounts
void cpool::init(unpacker* u_, int counts[NUM_COUNTS]) {
  this->u = u_;

  // Fill-pointer for CP.
  int next_entry = 0;

  // Size the constant pool:
  for (int k = 0; k < (int)N_TAGS_IN_ORDER; k++) {
    byte tag = TAGS_IN_ORDER[k];
    int  len = counts[k];
    tag_count[tag] = len;
    tag_base[tag] = next_entry;
    next_entry += len;
    // Detect and defend against constant pool size overflow.
    // (Pack200 forbids the sum of CP counts to exceed 2^29-1.)
    enum {
      CP_SIZE_LIMIT = (1<<29),
      IMPLICIT_ENTRY_COUNT = 1  // empty Utf8 string
    };
    if (len >= (1<<29) || len < 0
        || next_entry >= CP_SIZE_LIMIT+IMPLICIT_ENTRY_COUNT) {
      abort("archive too large:  constant pool limit exceeded");
      return;
    }
  }

  // Close off the end of the CP:
  nentries = next_entry;

  // place a limit on future CP growth:
  int generous = 0;
  generous = add_size(generous, u->ic_count); // implicit name
  generous = add_size(generous, u->ic_count); // outer
  generous = add_size(generous, u->ic_count); // outer.utf8
  generous = add_size(generous, 40); // WKUs, misc
  generous = add_size(generous, u->class_count); // implicit SourceFile strings
  maxentries = add_size(nentries, generous);

  // Note that this CP does not include "empty" entries
  // for longs and doubles.  Those are introduced when
  // the entries are renumbered for classfile output.

  entries = U_NEW(entry, maxentries);
  CHECK;

  first_extra_entry = &entries[nentries];

  // Initialize the standard indexes.
  tag_count[CONSTANT_All] = nentries;
  tag_base[ CONSTANT_All] = 0;
  for (int tag = 0; tag < CONSTANT_Limit; tag++) {
    entry* cpMap = &entries[tag_base[tag]];
    tag_index[tag].init(tag_count[tag], cpMap, tag);
  }

  // Initialize hashTab to a generous power-of-two size.
  uint pow2 = 1;
  uint target = maxentries + maxentries/2;  // 60% full
  while (pow2 < target)  pow2 <<= 1;
  hashTab = U_NEW(entry*, hashTabLength = pow2);
}

static byte* store_Utf8_char(byte* cp, unsigned short ch) {
  if (ch >= 0x001 && ch <= 0x007F) {
    *cp++ = (byte) ch;
  } else if (ch <= 0x07FF) {
    *cp++ = (byte) (0xC0 | ((ch >>  6) & 0x1F));
    *cp++ = (byte) (0x80 | ((ch >>  0) & 0x3F));
  } else {
    *cp++ = (byte) (0xE0 | ((ch >> 12) & 0x0F));
    *cp++ = (byte) (0x80 | ((ch >>  6) & 0x3F));
    *cp++ = (byte) (0x80 | ((ch >>  0) & 0x3F));
  }
  return cp;
}

static byte* skip_Utf8_chars(byte* cp, int len) {
  for (;; cp++) {
    int ch = *cp & 0xFF;
    if ((ch & 0xC0) != 0x80) {
      if (len-- == 0)
        return cp;
      if (ch < 0x80 && len == 0)
        return cp+1;
    }
  }
}

static int compare_Utf8_chars(bytes& b1, bytes& b2) {
  int l1 = (int)b1.len;
  int l2 = (int)b2.len;
  int l0 = (l1 < l2) ? l1 : l2;
  byte* p1 = b1.ptr;
  byte* p2 = b2.ptr;
  int c0 = 0;
  for (int i = 0; i < l0; i++) {
    int c1 = p1[i] & 0xFF;
    int c2 = p2[i] & 0xFF;
    if (c1 != c2) {
      // Before returning the obvious answer,
      // check to see if c1 or c2 is part of a 0x0000,
      // which encodes as {0xC0,0x80}.  The 0x0000 is the
      // lowest-sorting Java char value, and yet it encodes
      // as if it were the first char after 0x7F, which causes
      // strings containing nulls to sort too high.  All other
      // comparisons are consistent between Utf8 and Java chars.
      if (c1 == 0xC0 && (p1[i+1] & 0xFF) == 0x80)  c1 = 0;
      if (c2 == 0xC0 && (p2[i+1] & 0xFF) == 0x80)  c2 = 0;
      if (c0 == 0xC0) {
        assert(((c1|c2) & 0xC0) == 0x80);  // c1 & c2 are extension chars
        if (c1 == 0x80)  c1 = 0;  // will sort below c2
        if (c2 == 0x80)  c2 = 0;  // will sort below c1
      }
      return c1 - c2;
    }
    c0 = c1;  // save away previous char
  }
  // common prefix is identical; return length difference if any
  return l1 - l2;
}

// Cf. PackageReader.readUtf8Bands
local_inline
void unpacker::read_Utf8_values(entry* cpMap, int len) {
  // Implicit first Utf8 string is the empty string.
  enum {
    // certain bands begin with implicit zeroes
    PREFIX_SKIP_2 = 2,
    SUFFIX_SKIP_1 = 1
  };

  int i;

  // First band:  Read lengths of shared prefixes.
  if (len > PREFIX_SKIP_2)
    cp_Utf8_prefix.readData(len - PREFIX_SKIP_2);
    NOT_PRODUCT(else cp_Utf8_prefix.readData(0));  // for asserts

  // Second band:  Read lengths of unshared suffixes:
  if (len > SUFFIX_SKIP_1)
    cp_Utf8_suffix.readData(len - SUFFIX_SKIP_1);
    NOT_PRODUCT(else cp_Utf8_suffix.readData(0));  // for asserts

  bytes* allsuffixes = T_NEW(bytes, len);
  CHECK;

  int nbigsuf = 0;
  fillbytes charbuf;    // buffer to allocate small strings
  charbuf.init();

  // Third band:  Read the char values in the unshared suffixes:
  cp_Utf8_chars.readData(cp_Utf8_suffix.getIntTotal());
  for (i = 0; i < len; i++) {
    int suffix = (i < SUFFIX_SKIP_1)? 0: cp_Utf8_suffix.getInt();
    if (suffix < 0) {
      abort("bad utf8 suffix");
      return;
    }
    if (suffix == 0 && i >= SUFFIX_SKIP_1) {
      // chars are packed in cp_Utf8_big_chars
      nbigsuf += 1;
      continue;
    }
    bytes& chars  = allsuffixes[i];
    uint size3    = suffix * 3;     // max Utf8 length
    bool isMalloc = (suffix > SMALL);
    if (isMalloc) {
      chars.malloc(size3);
    } else {
      if (!charbuf.canAppend(size3+1)) {
        assert(charbuf.allocated == 0 || tmallocs.contains(charbuf.base()));
        charbuf.init(CHUNK);  // Reset to new buffer.
        tmallocs.add(charbuf.base());
      }
      chars.set(charbuf.grow(size3+1), size3);
    }
    CHECK;
    byte* chp = chars.ptr;
    for (int j = 0; j < suffix; j++) {
      unsigned short ch = cp_Utf8_chars.getInt();
      chp = store_Utf8_char(chp, ch);
    }
    // shrink to fit:
    if (isMalloc) {
      chars.realloc(chp - chars.ptr);
      CHECK;
      tmallocs.add(chars.ptr); // free it later
    } else {
      int shrink = (int)(chars.limit() - chp);
      chars.len -= shrink;
      charbuf.b.len -= shrink;  // ungrow to reclaim buffer space
      // Note that we did not reclaim the final '\0'.
      assert(chars.limit() == charbuf.limit()-1);
      assert(strlen((char*)chars.ptr) == chars.len);
    }
  }
  //cp_Utf8_chars.done();
#ifndef PRODUCT
  charbuf.b.set(null, 0); // tidy
#endif

  // Fourth band:  Go back and size the specially packed strings.
  int maxlen = 0;
  cp_Utf8_big_suffix.readData(nbigsuf);
  cp_Utf8_suffix.rewind();
  for (i = 0; i < len; i++) {
    int suffix = (i < SUFFIX_SKIP_1)? 0: cp_Utf8_suffix.getInt();
    int prefix = (i < PREFIX_SKIP_2)? 0: cp_Utf8_prefix.getInt();
    if (prefix < 0 || prefix+suffix < 0) {
       abort("bad utf8 prefix");
       return;
    }
    bytes& chars = allsuffixes[i];
    if (suffix == 0 && i >= SUFFIX_SKIP_1) {
      suffix = cp_Utf8_big_suffix.getInt();
      assert(chars.ptr == null);
      chars.len = suffix;  // just a momentary hack
    } else {
      assert(chars.ptr != null);
    }
    if (maxlen < prefix + suffix) {
      maxlen = prefix + suffix;
    }
  }
  //cp_Utf8_suffix.done();      // will use allsuffixes[i].len (ptr!=null)
  //cp_Utf8_big_suffix.done();  // will use allsuffixes[i].len

  // Fifth band(s):  Get the specially packed characters.
  cp_Utf8_big_suffix.rewind();
  for (i = 0; i < len; i++) {
    bytes& chars = allsuffixes[i];
    if (chars.ptr != null)  continue;  // already input
    int suffix = (int)chars.len;  // pick up the hack
    uint size3 = suffix * 3;
    if (suffix == 0)  continue;  // done with empty string
    chars.malloc(size3);
    byte* chp = chars.ptr;
    band saved_band = cp_Utf8_big_chars;
    cp_Utf8_big_chars.readData(suffix);
    for (int j = 0; j < suffix; j++) {
      unsigned short ch = cp_Utf8_big_chars.getInt();
      chp = store_Utf8_char(chp, ch);
    }
    chars.realloc(chp - chars.ptr);
    CHECK;
    tmallocs.add(chars.ptr);  // free it later
    //cp_Utf8_big_chars.done();
    cp_Utf8_big_chars = saved_band;  // reset the band for the next string
  }
  cp_Utf8_big_chars.readData(0);  // zero chars
  //cp_Utf8_big_chars.done();

  // Finally, sew together all the prefixes and suffixes.
  bytes bigbuf;
  bigbuf.malloc(maxlen * 3 + 1);  // max Utf8 length, plus slop for null
  CHECK;
  int prevlen = 0;  // previous string length (in chars)
  tmallocs.add(bigbuf.ptr);  // free after this block
  cp_Utf8_prefix.rewind();
  for (i = 0; i < len; i++) {
    bytes& chars = allsuffixes[i];
    int prefix = (i < PREFIX_SKIP_2)? 0: cp_Utf8_prefix.getInt();
    int suffix = (int)chars.len;
    byte* fillp;
    // by induction, the buffer is already filled with the prefix
    // make sure the prefix value is not corrupted, though:
    if (prefix > prevlen) {
       abort("utf8 prefix overflow");
       return;
    }
    fillp = skip_Utf8_chars(bigbuf.ptr, prefix);
    // copy the suffix into the same buffer:
    fillp = chars.writeTo(fillp);
    assert(bigbuf.inBounds(fillp));
    *fillp = 0;  // bigbuf must contain a well-formed Utf8 string
    int length = (int)(fillp - bigbuf.ptr);
    bytes& value = cpMap[i].value.b;
    value.set(U_NEW(byte, add_size(length,1)), length);
    value.copyFrom(bigbuf.ptr, length);
    CHECK;
    // Index all Utf8 strings
    entry* &htref = cp.hashTabRef(CONSTANT_Utf8, value);
    if (htref == null) {
      // Note that if two identical strings are transmitted,
      // the first is taken to be the canonical one.
      htref = &cpMap[i];
    }
    prevlen = prefix + suffix;
  }
  //cp_Utf8_prefix.done();

  // Free intermediate buffers.
  free_temps();
}

local_inline
void unpacker::read_single_words(band& cp_band, entry* cpMap, int len) {
  cp_band.readData(len);
  for (int i = 0; i < len; i++) {
    cpMap[i].value.i = cp_band.getInt();  // coding handles signs OK
  }
}

maybe_inline
void unpacker::read_double_words(band& cp_bands, entry* cpMap, int len) {
  band& cp_band_hi = cp_bands;
  band& cp_band_lo = cp_bands.nextBand();
  cp_band_hi.readData(len);
  cp_band_lo.readData(len);
  for (int i = 0; i < len; i++) {
    cpMap[i].value.l = cp_band_hi.getLong(cp_band_lo, true);
  }
  //cp_band_hi.done();
  //cp_band_lo.done();
}

maybe_inline
void unpacker::read_single_refs(band& cp_band, byte refTag, entry* cpMap, int len) {
  assert(refTag == CONSTANT_Utf8);
  cp_band.setIndexByTag(refTag);
  cp_band.readData(len);
  CHECK;
  int indexTag = (cp_band.bn == e_cp_Class) ? CONSTANT_Class : 0;
  for (int i = 0; i < len; i++) {
    entry& e = cpMap[i];
    e.refs = U_NEW(entry*, e.nrefs = 1);
    entry* utf = cp_band.getRef();
    CHECK;
    e.refs[0] = utf;
    e.value.b = utf->value.b;  // copy value of Utf8 string to self
    if (indexTag != 0) {
      // Maintain cross-reference:
      entry* &htref = cp.hashTabRef(indexTag, e.value.b);
      if (htref == null) {
        // Note that if two identical classes are transmitted,
        // the first is taken to be the canonical one.
        htref = &e;
      }
    }
  }
  //cp_band.done();
}

maybe_inline
void unpacker::read_double_refs(band& cp_band, byte ref1Tag, byte ref2Tag,
                                entry* cpMap, int len) {
  band& cp_band1 = cp_band;
  band& cp_band2 = cp_band.nextBand();
  cp_band1.setIndexByTag(ref1Tag);
  cp_band2.setIndexByTag(ref2Tag);
  cp_band1.readData(len);
  cp_band2.readData(len);
  CHECK;
  for (int i = 0; i < len; i++) {
    entry& e = cpMap[i];
    e.refs = U_NEW(entry*, e.nrefs = 2);
    e.refs[0] = cp_band1.getRef();
    e.refs[1] = cp_band2.getRef();
    CHECK;
  }
  //cp_band1.done();
  //cp_band2.done();
}

// Cf. PackageReader.readSignatureBands
maybe_inline
void unpacker::read_signature_values(entry* cpMap, int len) {
  cp_Signature_form.setIndexByTag(CONSTANT_Utf8);
  cp_Signature_form.readData(len);
  CHECK;
  int ncTotal = 0;
  int i;
  for (i = 0; i < len; i++) {
    entry& e = cpMap[i];
    entry& form = *cp_Signature_form.getRef();
    CHECK;
    int nc = 0;

    for ( const char* ncp = form.utf8String() ; *ncp; ncp++) {
      if (*ncp == 'L')  nc++;
    }

    ncTotal += nc;
    e.refs = U_NEW(entry*, cpMap[i].nrefs = 1 + nc);
    CHECK;
    e.refs[0] = &form;
  }
  //cp_Signature_form.done();
  cp_Signature_classes.setIndexByTag(CONSTANT_Class);
  cp_Signature_classes.readData(ncTotal);
  for (i = 0; i < len; i++) {
    entry& e = cpMap[i];
    for (int j = 1; j < e.nrefs; j++) {
      e.refs[j] = cp_Signature_classes.getRef();
      CHECK;
    }
  }
  //cp_Signature_classes.done();
}

// Cf. PackageReader.readConstantPool
void unpacker::read_cp() {
  byte* rp0 = rp;

  int i;

  for (int k = 0; k < (int)N_TAGS_IN_ORDER; k++) {
    byte tag = TAGS_IN_ORDER[k];
    int  len = cp.tag_count[tag];
    int base = cp.tag_base[tag];

    PRINTCR((1,"Reading %d %s entries...", len, NOT_PRODUCT(TAG_NAME[tag])+0));
    entry* cpMap = &cp.entries[base];
    for (i = 0; i < len; i++) {
      cpMap[i].tag = tag;
      cpMap[i].inord = i;
    }

    switch (tag) {
    case CONSTANT_Utf8:
      read_Utf8_values(cpMap, len);
      break;
    case CONSTANT_Integer:
      read_single_words(cp_Int, cpMap, len);
      break;
    case CONSTANT_Float:
      read_single_words(cp_Float, cpMap, len);
      break;
    case CONSTANT_Long:
      read_double_words(cp_Long_hi /*& cp_Long_lo*/, cpMap, len);
      break;
    case CONSTANT_Double:
      read_double_words(cp_Double_hi /*& cp_Double_lo*/, cpMap, len);
      break;
    case CONSTANT_String:
      read_single_refs(cp_String, CONSTANT_Utf8, cpMap, len);
      break;
    case CONSTANT_Class:
      read_single_refs(cp_Class, CONSTANT_Utf8, cpMap, len);
      break;
    case CONSTANT_Signature:
      read_signature_values(cpMap, len);
      break;
    case CONSTANT_NameandType:
      read_double_refs(cp_Descr_name /*& cp_Descr_type*/,
                       CONSTANT_Utf8, CONSTANT_Signature,
                       cpMap, len);
      break;
    case CONSTANT_Fieldref:
      read_double_refs(cp_Field_class /*& cp_Field_desc*/,
                       CONSTANT_Class, CONSTANT_NameandType,
                       cpMap, len);
      break;
    case CONSTANT_Methodref:
      read_double_refs(cp_Method_class /*& cp_Method_desc*/,
                       CONSTANT_Class, CONSTANT_NameandType,
                       cpMap, len);
      break;
    case CONSTANT_InterfaceMethodref:
      read_double_refs(cp_Imethod_class /*& cp_Imethod_desc*/,
                       CONSTANT_Class, CONSTANT_NameandType,
                       cpMap, len);
      break;
    default:
      assert(false);
      break;
    }

    // Initialize the tag's CP index right away, since it might be needed
    // in the next pass to initialize the CP for another tag.
#ifndef PRODUCT
    cpindex* ix = &cp.tag_index[tag];
    assert(ix->ixTag == tag);
    assert((int)ix->len   == len);
    assert(ix->base1 == cpMap);
#endif
    CHECK;
  }

  cp.expandSignatures();
  CHECK;
  cp.initMemberIndexes();
  CHECK;

  PRINTCR((1,"parsed %d constant pool entries in %d bytes", cp.nentries, (rp - rp0)));

  #define SNAME(n,s) #s "\0"
  const char* symNames = (
    ALL_ATTR_DO(SNAME)
    "<init>"
  );
  #undef SNAME

  for (int sn = 0; sn < cpool::s_LIMIT; sn++) {
    assert(symNames[0] >= '0' && symNames[0] <= 'Z');  // sanity
    bytes name; name.set(symNames);
    if (name.len > 0 && name.ptr[0] != '0') {
      cp.sym[sn] = cp.ensureUtf8(name);
      PRINTCR((4, "well-known sym %d=%s", sn, cp.sym[sn]->string()));
    }
    symNames += name.len + 1;  // skip trailing null to next name
  }

  band::initIndexes(this);
}

static band* no_bands[] = { null };  // shared empty body

inline
band& unpacker::attr_definitions::fixed_band(int e_class_xxx) {
  return u->all_bands[xxx_flags_hi_bn + (e_class_xxx-e_class_flags_hi)];
}
inline band& unpacker::attr_definitions::xxx_flags_hi()
  { return fixed_band(e_class_flags_hi); }
inline band& unpacker::attr_definitions::xxx_flags_lo()
  { return fixed_band(e_class_flags_lo); }
inline band& unpacker::attr_definitions::xxx_attr_count()
  { return fixed_band(e_class_attr_count); }
inline band& unpacker::attr_definitions::xxx_attr_indexes()
  { return fixed_band(e_class_attr_indexes); }
inline band& unpacker::attr_definitions::xxx_attr_calls()
  { return fixed_band(e_class_attr_calls); }


inline
unpacker::layout_definition*
unpacker::attr_definitions::defineLayout(int idx,
                                         entry* nameEntry,
                                         const char* layout) {
  const char* name = nameEntry->value.b.strval();
  layout_definition* lo = defineLayout(idx, name, layout);
  CHECK_0;
  lo->nameEntry = nameEntry;
  return lo;
}

unpacker::layout_definition*
unpacker::attr_definitions::defineLayout(int idx,
                                         const char* name,
                                         const char* layout) {
  assert(flag_limit != 0);  // must be set up already
  if (idx >= 0) {
    // Fixed attr.
    if (idx >= (int)flag_limit)
      abort("attribute index too large");
    if (isRedefined(idx))
      abort("redefined attribute index");
    redef |= ((julong)1<<idx);
  } else {
    idx = flag_limit + overflow_count.length();
    overflow_count.add(0);  // make a new counter
  }
  layout_definition* lo = U_NEW(layout_definition, 1);
  CHECK_0;
  lo->idx = idx;
  lo->name = name;
  lo->layout = layout;
  for (int adds = (idx+1) - layouts.length(); adds > 0; adds--) {
    layouts.add(null);
  }
  CHECK_0;
  layouts.get(idx) = lo;
  return lo;
}

band**
unpacker::attr_definitions::buildBands(unpacker::layout_definition* lo) {
  int i;
  if (lo->elems != null)
    return lo->bands();
  if (lo->layout[0] == '\0') {
    lo->elems = no_bands;
  } else {
    // Create bands for this attribute by parsing the layout.
    bool hasCallables = lo->hasCallables();
    bands_made = 0x10000;  // base number for bands made
    const char* lp = lo->layout;
    lp = parseLayout(lp, lo->elems, -1);
    CHECK_0;
    if (lp[0] != '\0' || band_stack.length() > 0) {
      abort("garbage at end of layout");
    }
    band_stack.popTo(0);
    CHECK_0;

    // Fix up callables to point at their callees.
    band** bands = lo->elems;
    assert(bands == lo->bands());
    int num_callables = 0;
    if (hasCallables) {
      while (bands[num_callables] != null) {
        if (bands[num_callables]->le_kind != EK_CBLE) {
          abort("garbage mixed with callables");
          break;
        }
        num_callables += 1;
      }
    }
    for (i = 0; i < calls_to_link.length(); i++) {
      band& call = *(band*) calls_to_link.get(i);
      assert(call.le_kind == EK_CALL);
      // Determine the callee.
      int call_num = call.le_len;
      if (call_num < 0 || call_num >= num_callables) {
        abort("bad call in layout");
        break;
      }
      band& cble = *bands[call_num];
      // Link the call to it.
      call.le_body[0] = &cble;
      // Distinguish backward calls and callables:
      assert(cble.le_kind == EK_CBLE);
      assert(cble.le_len == call_num);
      cble.le_back |= call.le_back;
    }
    calls_to_link.popTo(0);
  }
  return lo->elems;
}

/* attribute layout language parser

  attribute_layout:
        ( layout_element )* | ( callable )+
  layout_element:
        ( integral | replication | union | call | reference )

  callable:
        '[' body ']'
  body:
        ( layout_element )+

  integral:
        ( unsigned_int | signed_int | bc_index | bc_offset | flag )
  unsigned_int:
        uint_type
  signed_int:
        'S' uint_type
  any_int:
        ( unsigned_int | signed_int )
  bc_index:
        ( 'P' uint_type | 'PO' uint_type )
  bc_offset:
        'O' any_int
  flag:
        'F' uint_type
  uint_type:
        ( 'B' | 'H' | 'I' | 'V' )

  replication:
        'N' uint_type '[' body ']'

  union:
        'T' any_int (union_case)* '(' ')' '[' (body)? ']'
  union_case:
        '(' union_case_tag (',' union_case_tag)* ')' '[' (body)? ']'
  union_case_tag:
        ( numeral | numeral '-' numeral )
  call:
        '(' numeral ')'

  reference:
        reference_type ( 'N' )? uint_type
  reference_type:
        ( constant_ref | schema_ref | utf8_ref | untyped_ref )
  constant_ref:
        ( 'KI' | 'KJ' | 'KF' | 'KD' | 'KS' | 'KQ' )
  schema_ref:
        ( 'RC' | 'RS' | 'RD' | 'RF' | 'RM' | 'RI' )
  utf8_ref:
        'RU'
  untyped_ref:
        'RQ'

  numeral:
        '(' ('-')? (digit)+ ')'
  digit:
        ( '0' | '1' | '2' | '3' | '4' | '5' | '6' | '7' | '8' | '9' )

*/

const char*
unpacker::attr_definitions::parseIntLayout(const char* lp, band* &res,
                                           byte le_kind, bool can_be_signed) {
  const char* lp0 = lp;
  band* b = U_NEW(band, 1);
  CHECK_(lp);
  char le = *lp++;
  int spec = UNSIGNED5_spec;
  if (le == 'S' && can_be_signed) {
    // Note:  This is the last use of sign.  There is no 'EF_SIGN'.
    spec = SIGNED5_spec;
    le = *lp++;
  } else if (le == 'B') {
    spec = BYTE1_spec;  // unsigned byte
  }
  b->init(u, bands_made++, spec);
  b->le_kind = le_kind;
  int le_len = 0;
  switch (le) {
  case 'B': le_len = 1; break;
  case 'H': le_len = 2; break;
  case 'I': le_len = 4; break;
  case 'V': le_len = 0; break;
  default:  abort("bad layout element");
  }
  b->le_len = le_len;
  band_stack.add(b);
  res = b;
  return lp;
}

const char*
unpacker::attr_definitions::parseNumeral(const char* lp, int &res) {
  const char* lp0 = lp;
  bool sgn = false;
  if (*lp == '0') { res = 0; return lp+1; }  // special case '0'
  if (*lp == '-') { sgn = true; lp++; }
  const char* dp = lp;
  int con = 0;
  while (*dp >= '0' && *dp <= '9') {
    int con0 = con;
    con *= 10;
    con += (*dp++) - '0';
    if (con <= con0) { con = -1; break; }  //  numeral overflow
  }
  if (lp == dp) {
    abort("missing numeral in layout");
    return "";
  }
  lp = dp;
  if (con < 0 && !(sgn && con == -con)) {
    // (Portability note:  Misses the error if int is not 32 bits.)
    abort("numeral overflow");
    return "" ;
  }
  if (sgn)  con = -con;
  res = con;
  return lp;
}

band**
unpacker::attr_definitions::popBody(int bs_base) {
  // Return everything that was pushed, as a null-terminated pointer array.
  int bs_limit = band_stack.length();
  if (bs_base == bs_limit) {
    return no_bands;
  } else {
    int nb = bs_limit - bs_base;
    band** res = U_NEW(band*, add_size(nb, 1));
    CHECK_(no_bands);
    for (int i = 0; i < nb; i++) {
      band* b = (band*) band_stack.get(bs_base + i);
      res[i] = b;
    }
    band_stack.popTo(bs_base);
    return res;
  }
}

const char*
unpacker::attr_definitions::parseLayout(const char* lp, band** &res,
                                        int curCble) {
  const char* lp0 = lp;
  int bs_base = band_stack.length();
  bool top_level = (bs_base == 0);
  band* b;
  enum { can_be_signed = true };  // optional arg to parseIntLayout

  for (bool done = false; !done; ) {
    switch (*lp++) {
    case 'B': case 'H': case 'I': case 'V': // unsigned_int
    case 'S': // signed_int
      --lp;  // reparse
    case 'F':
      lp = parseIntLayout(lp, b, EK_INT);
      break;
    case 'P':
      {
        int le_bci = EK_BCI;
        if (*lp == 'O') {
          ++lp;
          le_bci = EK_BCID;
        }
        assert(*lp != 'S');  // no PSH, etc.
        lp = parseIntLayout(lp, b, EK_INT);
        b->le_bci = le_bci;
        if (le_bci == EK_BCI)
          b->defc = coding::findBySpec(BCI5_spec);
        else
          b->defc = coding::findBySpec(BRANCH5_spec);
      }
      break;
    case 'O':
      lp = parseIntLayout(lp, b, EK_INT, can_be_signed);
      b->le_bci = EK_BCO;
      b->defc = coding::findBySpec(BRANCH5_spec);
      break;
    case 'N': // replication: 'N' uint '[' elem ... ']'
      lp = parseIntLayout(lp, b, EK_REPL);
      assert(*lp == '[');
      ++lp;
      lp = parseLayout(lp, b->le_body, curCble);
      CHECK_(lp);
      break;
    case 'T': // union: 'T' any_int union_case* '(' ')' '[' body ']'
      lp = parseIntLayout(lp, b, EK_UN, can_be_signed);
      {
        int union_base = band_stack.length();
        for (;;) {   // for each case
          band& k_case = *U_NEW(band, 1);
          CHECK_(lp);
          band_stack.add(&k_case);
          k_case.le_kind = EK_CASE;
          k_case.bn = bands_made++;
          if (*lp++ != '(') {
            abort("bad union case");
            return "";
          }
          if (*lp++ != ')') {
            --lp;  // reparse
            // Read some case values.  (Use band_stack for temp. storage.)
            int case_base = band_stack.length();
            for (;;) {
              int caseval = 0;
              lp = parseNumeral(lp, caseval);
              band_stack.add((void*)(size_t)caseval);
              if (*lp == '-') {
                // new in version 160, allow (1-5) for (1,2,3,4,5)
                if (u->majver < JAVA6_PACKAGE_MAJOR_VERSION) {
                  abort("bad range in union case label (old archive format)");
                  return "";
                }
                int caselimit = caseval;
                lp++;
                lp = parseNumeral(lp, caselimit);
                if (caseval >= caselimit
                    || (uint)(caselimit - caseval) > 0x10000) {
                  // Note:  0x10000 is arbitrary implementation restriction.
                  // We can remove it later if it's important to.
                  abort("bad range in union case label");
                  return "";
                }
                for (;;) {
                  ++caseval;
                  band_stack.add((void*)(size_t)caseval);
                  if (caseval == caselimit)  break;
                }
              }
              if (*lp != ',')  break;
              lp++;
            }
            if (*lp++ != ')') {
              abort("bad case label");
              return "";
            }
            // save away the case labels
            int ntags = band_stack.length() - case_base;
            int* tags = U_NEW(int, add_size(ntags, 1));
            CHECK_(lp);
            k_case.le_casetags = tags;
            *tags++ = ntags;
            for (int i = 0; i < ntags; i++) {
              *tags++ = ptrlowbits(band_stack.get(case_base+i));
            }
            band_stack.popTo(case_base);
            CHECK_(lp);
          }
          // Got le_casetags.  Now grab the body.
          assert(*lp == '[');
          ++lp;
          lp = parseLayout(lp, k_case.le_body, curCble);
          CHECK_(lp);
          if (k_case.le_casetags == null)  break;  // done
        }
        b->le_body = popBody(union_base);
      }
      break;
    case '(': // call: '(' -?NN* ')'
      {
        band& call = *U_NEW(band, 1);
        CHECK_(lp);
        band_stack.add(&call);
        call.le_kind = EK_CALL;
        call.bn = bands_made++;
        call.le_body = U_NEW(band*, 2); // fill in later
        int call_num = 0;
        lp = parseNumeral(lp, call_num);
        call.le_back = (call_num <= 0);
        call_num += curCble;  // numeral is self-relative offset
        call.le_len = call_num;  //use le_len as scratch
        calls_to_link.add(&call);
        CHECK_(lp);
        if (*lp++ != ')') {
          abort("bad call label");
          return "";
        }
      }
      break;
    case 'K': // reference_type: constant_ref
    case 'R': // reference_type: schema_ref
      {
        int ixTag = CONSTANT_None;
        if (lp[-1] == 'K') {
          switch (*lp++) {
          case 'I': ixTag = CONSTANT_Integer; break;
          case 'J': ixTag = CONSTANT_Long; break;
          case 'F': ixTag = CONSTANT_Float; break;
          case 'D': ixTag = CONSTANT_Double; break;
          case 'S': ixTag = CONSTANT_String; break;
          case 'Q': ixTag = CONSTANT_Literal; break;
          }
        } else {
          switch (*lp++) {
          case 'C': ixTag = CONSTANT_Class; break;
          case 'S': ixTag = CONSTANT_Signature; break;
          case 'D': ixTag = CONSTANT_NameandType; break;
          case 'F': ixTag = CONSTANT_Fieldref; break;
          case 'M': ixTag = CONSTANT_Methodref; break;
          case 'I': ixTag = CONSTANT_InterfaceMethodref; break;
          case 'U': ixTag = CONSTANT_Utf8; break; //utf8_ref
          case 'Q': ixTag = CONSTANT_All; break; //untyped_ref
          }
        }
        if (ixTag == CONSTANT_None) {
          abort("bad reference layout");
          break;
        }
        bool nullOK = false;
        if (*lp == 'N') {
          nullOK = true;
          lp++;
        }
        lp = parseIntLayout(lp, b, EK_REF);
        b->defc = coding::findBySpec(UNSIGNED5_spec);
        b->initRef(ixTag, nullOK);
      }
      break;
    case '[':
      {
        // [callable1][callable2]...
        if (!top_level) {
          abort("bad nested callable");
          break;
        }
        curCble += 1;
        NOT_PRODUCT(int call_num = band_stack.length() - bs_base);
        band& cble = *U_NEW(band, 1);
        CHECK_(lp);
        band_stack.add(&cble);
        cble.le_kind = EK_CBLE;
        NOT_PRODUCT(cble.le_len = call_num);
        cble.bn = bands_made++;
        lp = parseLayout(lp, cble.le_body, curCble);
      }
      break;
    case ']':
      // Hit a closing brace.  This ends whatever body we were in.
      done = true;
      break;
    case '\0':
      // Hit a null.  Also ends the (top-level) body.
      --lp;  // back up, so caller can see the null also
      done = true;
      break;
    default:
      abort("bad layout");
      break;
    }
    CHECK_(lp);
  }

  // Return the accumulated bands:
  res = popBody(bs_base);
  return lp;
}

void unpacker::read_attr_defs() {
  int i;

  // Tell each AD which attrc it is and where its fixed flags are:
  attr_defs[ATTR_CONTEXT_CLASS].attrc            = ATTR_CONTEXT_CLASS;
  attr_defs[ATTR_CONTEXT_CLASS].xxx_flags_hi_bn  = e_class_flags_hi;
  attr_defs[ATTR_CONTEXT_FIELD].attrc            = ATTR_CONTEXT_FIELD;
  attr_defs[ATTR_CONTEXT_FIELD].xxx_flags_hi_bn  = e_field_flags_hi;
  attr_defs[ATTR_CONTEXT_METHOD].attrc           = ATTR_CONTEXT_METHOD;
  attr_defs[ATTR_CONTEXT_METHOD].xxx_flags_hi_bn = e_method_flags_hi;
  attr_defs[ATTR_CONTEXT_CODE].attrc             = ATTR_CONTEXT_CODE;
  attr_defs[ATTR_CONTEXT_CODE].xxx_flags_hi_bn   = e_code_flags_hi;

  // Decide whether bands for the optional high flag words are present.
  attr_defs[ATTR_CONTEXT_CLASS]
    .setHaveLongFlags((archive_options & AO_HAVE_CLASS_FLAGS_HI) != 0);
  attr_defs[ATTR_CONTEXT_FIELD]
    .setHaveLongFlags((archive_options & AO_HAVE_FIELD_FLAGS_HI) != 0);
  attr_defs[ATTR_CONTEXT_METHOD]
    .setHaveLongFlags((archive_options & AO_HAVE_METHOD_FLAGS_HI) != 0);
  attr_defs[ATTR_CONTEXT_CODE]
    .setHaveLongFlags((archive_options & AO_HAVE_CODE_FLAGS_HI) != 0);

  // Set up built-in attrs.
  // (The simple ones are hard-coded.  The metadata layouts are not.)
  const char* md_layout = (
    // parameter annotations:
#define MDL0 \
    "[NB[(1)]]"
    MDL0
    // annotations:
#define MDL1 \
    "[NH[(1)]]" \
    "[RSHNH[RUH(1)]]"
    MDL1
    // member_value:
    "[TB"
      "(66,67,73,83,90)[KIH]"
      "(68)[KDH]"
      "(70)[KFH]"
      "(74)[KJH]"
      "(99)[RSH]"
      "(101)[RSHRUH]"
      "(115)[RUH]"
      "(91)[NH[(0)]]"
      "(64)["
        // nested annotation:
        "RSH"
        "NH[RUH(0)]"
        "]"
      "()[]"
    "]"
    );

  const char* md_layout_P = md_layout;
  const char* md_layout_A = md_layout+strlen(MDL0);
  const char* md_layout_V = md_layout+strlen(MDL0 MDL1);
  assert(0 == strncmp(&md_layout_A[-3], ")]][", 4));
  assert(0 == strncmp(&md_layout_V[-3], ")]][", 4));

  for (i = 0; i < ATTR_CONTEXT_LIMIT; i++) {
    attr_definitions& ad = attr_defs[i];
    ad.defineLayout(X_ATTR_RuntimeVisibleAnnotations,
                    "RuntimeVisibleAnnotations", md_layout_A);
    ad.defineLayout(X_ATTR_RuntimeInvisibleAnnotations,
                    "RuntimeInvisibleAnnotations", md_layout_A);
    if (i != ATTR_CONTEXT_METHOD)  continue;
    ad.defineLayout(METHOD_ATTR_RuntimeVisibleParameterAnnotations,
                    "RuntimeVisibleParameterAnnotations", md_layout_P);
    ad.defineLayout(METHOD_ATTR_RuntimeInvisibleParameterAnnotations,
                    "RuntimeInvisibleParameterAnnotations", md_layout_P);
    ad.defineLayout(METHOD_ATTR_AnnotationDefault,
                    "AnnotationDefault", md_layout_V);
  }

  attr_definition_headers.readData(attr_definition_count);
  attr_definition_name.readData(attr_definition_count);
  attr_definition_layout.readData(attr_definition_count);

  CHECK;

  // Initialize correct predef bits, to distinguish predefs from new defs.
#define ORBIT(n,s) |((julong)1<<n)
  attr_defs[ATTR_CONTEXT_CLASS].predef
    = (0 X_ATTR_DO(ORBIT) CLASS_ATTR_DO(ORBIT));
  attr_defs[ATTR_CONTEXT_FIELD].predef
    = (0 X_ATTR_DO(ORBIT) FIELD_ATTR_DO(ORBIT));
  attr_defs[ATTR_CONTEXT_METHOD].predef
    = (0 X_ATTR_DO(ORBIT) METHOD_ATTR_DO(ORBIT));
  attr_defs[ATTR_CONTEXT_CODE].predef
    = (0 O_ATTR_DO(ORBIT) CODE_ATTR_DO(ORBIT));
#undef ORBIT
  // Clear out the redef bits, folding them back into predef.
  for (i = 0; i < ATTR_CONTEXT_LIMIT; i++) {
    attr_defs[i].predef |= attr_defs[i].redef;
    attr_defs[i].redef = 0;
  }

  // Now read the transmitted locally defined attrs.
  // This will set redef bits again.
  for (i = 0; i < attr_definition_count; i++) {
    int    header  = attr_definition_headers.getByte();
    int    attrc   = ADH_BYTE_CONTEXT(header);
    int    idx     = ADH_BYTE_INDEX(header);
    entry* name    = attr_definition_name.getRef();
    entry* layout  = attr_definition_layout.getRef();
    CHECK;
    attr_defs[attrc].defineLayout(idx, name, layout->value.b.strval());
  }
}

#define NO_ENTRY_YET ((entry*)-1)

static bool isDigitString(bytes& x, int beg, int end) {
  if (beg == end)  return false;  // null string
  byte* xptr = x.ptr;
  for (int i = beg; i < end; i++) {
    char ch = xptr[i];
    if (!(ch >= '0' && ch <= '9'))  return false;
  }
  return true;
}

enum {  // constants for parsing class names
  SLASH_MIN = '.',
  SLASH_MAX = '/',
  DOLLAR_MIN = 0,
  DOLLAR_MAX = '-'
};

static int lastIndexOf(int chmin, int chmax, bytes& x, int pos) {
  byte* ptr = x.ptr;
  for (byte* cp = ptr + pos; --cp >= ptr; ) {
    assert(x.inBounds(cp));
    if (*cp >= chmin && *cp <= chmax)
      return (int)(cp - ptr);
  }
  return -1;
}

maybe_inline
inner_class* cpool::getIC(entry* inner) {
  if (inner == null)  return null;
  assert(inner->tag == CONSTANT_Class);
  if (inner->inord == NO_INORD)  return null;
  inner_class* ic = ic_index[inner->inord];
  assert(ic == null || ic->inner == inner);
  return ic;
}

maybe_inline
inner_class* cpool::getFirstChildIC(entry* outer) {
  if (outer == null)  return null;
  assert(outer->tag == CONSTANT_Class);
  if (outer->inord == NO_INORD)  return null;
  inner_class* ic = ic_child_index[outer->inord];
  assert(ic == null || ic->outer == outer);
  return ic;
}

maybe_inline
inner_class* cpool::getNextChildIC(inner_class* child) {
  inner_class* ic = child->next_sibling;
  assert(ic == null || ic->outer == child->outer);
  return ic;
}

void unpacker::read_ics() {
  int i;
  int index_size = cp.tag_count[CONSTANT_Class];
  inner_class** ic_index       = U_NEW(inner_class*, index_size);
  inner_class** ic_child_index = U_NEW(inner_class*, index_size);
  cp.ic_index = ic_index;
  cp.ic_child_index = ic_child_index;
  ics = U_NEW(inner_class, ic_count);
  ic_this_class.readData(ic_count);
  ic_flags.readData(ic_count);
  CHECK;
  // Scan flags to get count of long-form bands.
  int long_forms = 0;
  for (i = 0; i < ic_count; i++) {
    int flags = ic_flags.getInt();  // may be long form!
    if ((flags & ACC_IC_LONG_FORM) != 0) {
      long_forms += 1;
      ics[i].name = NO_ENTRY_YET;
    }
    flags &= ~ACC_IC_LONG_FORM;
    entry* inner = ic_this_class.getRef();
    CHECK;
    uint inord = inner->inord;
    assert(inord < (uint)cp.tag_count[CONSTANT_Class]);
    if (ic_index[inord] != null) {
      abort("identical inner class");
      break;
    }
    ic_index[inord] = &ics[i];
    ics[i].inner = inner;
    ics[i].flags = flags;
    assert(cp.getIC(inner) == &ics[i]);
  }
  CHECK;
  //ic_this_class.done();
  //ic_flags.done();
  ic_outer_class.readData(long_forms);
  ic_name.readData(long_forms);
  for (i = 0; i < ic_count; i++) {
    if (ics[i].name == NO_ENTRY_YET) {
      // Long form.
      ics[i].outer = ic_outer_class.getRefN();
      ics[i].name  = ic_name.getRefN();
    } else {
      // Fill in outer and name based on inner.
      bytes& n = ics[i].inner->value.b;
      bytes pkgOuter;
      bytes number;
      bytes name;
      // Parse n into pkgOuter and name (and number).
      PRINTCR((5, "parse short IC name %s", n.ptr));
      int dollar1, dollar2;  // pointers to $ in the pattern
      // parse n = (<pkg>/)*<outer>($<number>)?($<name>)?
      int nlen = (int)n.len;
      int pkglen = lastIndexOf(SLASH_MIN,  SLASH_MAX,  n, nlen) + 1;
      dollar2    = lastIndexOf(DOLLAR_MIN, DOLLAR_MAX, n, nlen);
      if (dollar2 < 0) {
         abort();
         return;
      }
      assert(dollar2 >= pkglen);
      if (isDigitString(n, dollar2+1, nlen)) {
        // n = (<pkg>/)*<outer>$<number>
        number = n.slice(dollar2+1, nlen);
        name.set(null,0);
        dollar1 = dollar2;
      } else if (pkglen < (dollar1
                           = lastIndexOf(DOLLAR_MIN, DOLLAR_MAX, n, dollar2-1))
                 && isDigitString(n, dollar1+1, dollar2)) {
        // n = (<pkg>/)*<outer>$<number>$<name>
        number = n.slice(dollar1+1, dollar2);
        name = n.slice(dollar2+1, nlen);
      } else {
        // n = (<pkg>/)*<outer>$<name>
        dollar1 = dollar2;
        number.set(null,0);
        name = n.slice(dollar2+1, nlen);
      }
      if (number.ptr == null)
        pkgOuter = n.slice(0, dollar1);
      else
        pkgOuter.set(null,0);
      PRINTCR((5,"=> %s$ 0%s $%s",
              pkgOuter.string(), number.string(), name.string()));

      if (pkgOuter.ptr != null)
        ics[i].outer = cp.ensureClass(pkgOuter);

      if (name.ptr != null)
        ics[i].name = cp.ensureUtf8(name);
    }

    // update child/sibling list
    if (ics[i].outer != null) {
      uint outord = ics[i].outer->inord;
      if (outord != NO_INORD) {
        assert(outord < (uint)cp.tag_count[CONSTANT_Class]);
        ics[i].next_sibling = ic_child_index[outord];
        ic_child_index[outord] = &ics[i];
      }
    }
  }
  //ic_outer_class.done();
  //ic_name.done();
}

void unpacker::read_classes() {
  PRINTCR((1,"  ...scanning %d classes...", class_count));
  class_this.readData(class_count);
  class_super.readData(class_count);
  class_interface_count.readData(class_count);
  class_interface.readData(class_interface_count.getIntTotal());

  CHECK;

  #if 0
  int i;
  // Make a little mark on super-classes.
  for (i = 0; i < class_count; i++) {
    entry* e = class_super.getRefN();
    if (e != null)  e->bits |= entry::EB_SUPER;
  }
  class_super.rewind();
  #endif

  // Members.
  class_field_count.readData(class_count);
  class_method_count.readData(class_count);

  CHECK;

  int field_count = class_field_count.getIntTotal();
  int method_count = class_method_count.getIntTotal();

  field_descr.readData(field_count);
  read_attrs(ATTR_CONTEXT_FIELD, field_count);
  CHECK;

  method_descr.readData(method_count);
  read_attrs(ATTR_CONTEXT_METHOD, method_count);

  CHECK;

  read_attrs(ATTR_CONTEXT_CLASS, class_count);
  CHECK;

  read_code_headers();

  PRINTCR((1,"scanned %d classes, %d fields, %d methods, %d code headers",
          class_count, field_count, method_count, code_count));
}

maybe_inline
int unpacker::attr_definitions::predefCount(uint idx) {
  return isPredefined(idx) ? flag_count[idx] : 0;
}

void unpacker::read_attrs(int attrc, int obj_count) {
  attr_definitions& ad = attr_defs[attrc];
  assert(ad.attrc == attrc);

  int i, idx, count;

  CHECK;

  bool haveLongFlags = ad.haveLongFlags();

  band& xxx_flags_hi = ad.xxx_flags_hi();
  assert(endsWith(xxx_flags_hi.name, "_flags_hi"));
  if (haveLongFlags)
    xxx_flags_hi.readData(obj_count);
  CHECK;

  band& xxx_flags_lo = ad.xxx_flags_lo();
  assert(endsWith(xxx_flags_lo.name, "_flags_lo"));
  xxx_flags_lo.readData(obj_count);
  CHECK;

  // pre-scan flags, counting occurrences of each index bit
  julong indexMask = ad.flagIndexMask();  // which flag bits are index bits?
  for (i = 0; i < obj_count; i++) {
    julong indexBits = xxx_flags_hi.getLong(xxx_flags_lo, haveLongFlags);
    if ((indexBits & ~indexMask) > (ushort)-1) {
      abort("undefined attribute flag bit");
      return;
    }
    indexBits &= indexMask;  // ignore classfile flag bits
    for (idx = 0; indexBits != 0; idx++, indexBits >>= 1) {
      ad.flag_count[idx] += (int)(indexBits & 1);
    }
  }
  // we'll scan these again later for output:
  xxx_flags_lo.rewind();
  xxx_flags_hi.rewind();

  band& xxx_attr_count = ad.xxx_attr_count();
  assert(endsWith(xxx_attr_count.name, "_attr_count"));
  // There is one count element for each 1<<16 bit set in flags:
  xxx_attr_count.readData(ad.predefCount(X_ATTR_OVERFLOW));
  CHECK;

  band& xxx_attr_indexes = ad.xxx_attr_indexes();
  assert(endsWith(xxx_attr_indexes.name, "_attr_indexes"));
  int overflowIndexCount = xxx_attr_count.getIntTotal();
  xxx_attr_indexes.readData(overflowIndexCount);
  CHECK;
  // pre-scan attr indexes, counting occurrences of each value
  for (i = 0; i < overflowIndexCount; i++) {
    idx = xxx_attr_indexes.getInt();
    if (!ad.isIndex(idx)) {
      abort("attribute index out of bounds");
      return;
    }
    ad.getCount(idx) += 1;
  }
  xxx_attr_indexes.rewind();  // we'll scan it again later for output

  // We will need a backward call count for each used backward callable.
  int backwardCounts = 0;
  for (idx = 0; idx < ad.layouts.length(); idx++) {
    layout_definition* lo = ad.getLayout(idx);
    if (lo != null && ad.getCount(idx) != 0) {
      // Build the bands lazily, only when they are used.
      band** bands = ad.buildBands(lo);
      CHECK;
      if (lo->hasCallables()) {
        for (i = 0; bands[i] != null; i++) {
          if (bands[i]->le_back) {
            assert(bands[i]->le_kind == EK_CBLE);
            backwardCounts += 1;
          }
        }
      }
    }
  }
  ad.xxx_attr_calls().readData(backwardCounts);
  CHECK;

  // Read built-in bands.
  // Mostly, these are hand-coded equivalents to readBandData().
  switch (attrc) {
  case ATTR_CONTEXT_CLASS:

    count = ad.predefCount(CLASS_ATTR_SourceFile);
    class_SourceFile_RUN.readData(count);
    CHECK;

    count = ad.predefCount(CLASS_ATTR_EnclosingMethod);
    class_EnclosingMethod_RC.readData(count);
    class_EnclosingMethod_RDN.readData(count);
    CHECK;

    count = ad.predefCount(X_ATTR_Signature);
    class_Signature_RS.readData(count);
    CHECK;

    ad.readBandData(X_ATTR_RuntimeVisibleAnnotations);
    ad.readBandData(X_ATTR_RuntimeInvisibleAnnotations);

    count = ad.predefCount(CLASS_ATTR_InnerClasses);
    class_InnerClasses_N.readData(count);
    CHECK;

    count = class_InnerClasses_N.getIntTotal();
    class_InnerClasses_RC.readData(count);
    class_InnerClasses_F.readData(count);
    CHECK;
    // Drop remaining columns wherever flags are zero:
    count -= class_InnerClasses_F.getIntCount(0);
    class_InnerClasses_outer_RCN.readData(count);
    class_InnerClasses_name_RUN.readData(count);
    CHECK;

    count = ad.predefCount(CLASS_ATTR_ClassFile_version);
    class_ClassFile_version_minor_H.readData(count);
    class_ClassFile_version_major_H.readData(count);
    CHECK;
    break;

  case ATTR_CONTEXT_FIELD:

    count = ad.predefCount(FIELD_ATTR_ConstantValue);
    field_ConstantValue_KQ.readData(count);
    CHECK;

    count = ad.predefCount(X_ATTR_Signature);
    field_Signature_RS.readData(count);
    CHECK;

    ad.readBandData(X_ATTR_RuntimeVisibleAnnotations);
    ad.readBandData(X_ATTR_RuntimeInvisibleAnnotations);
    CHECK;
    break;

  case ATTR_CONTEXT_METHOD:

    code_count = ad.predefCount(METHOD_ATTR_Code);
    // Code attrs are handled very specially below...

    count = ad.predefCount(METHOD_ATTR_Exceptions);
    method_Exceptions_N.readData(count);
    count = method_Exceptions_N.getIntTotal();
    method_Exceptions_RC.readData(count);
    CHECK;

    count = ad.predefCount(X_ATTR_Signature);
    method_Signature_RS.readData(count);
    CHECK;

    ad.readBandData(X_ATTR_RuntimeVisibleAnnotations);
    ad.readBandData(X_ATTR_RuntimeInvisibleAnnotations);
    ad.readBandData(METHOD_ATTR_RuntimeVisibleParameterAnnotations);
    ad.readBandData(METHOD_ATTR_RuntimeInvisibleParameterAnnotations);
    ad.readBandData(METHOD_ATTR_AnnotationDefault);
    CHECK;
    break;

  case ATTR_CONTEXT_CODE:
    // (keep this code aligned with its brother in unpacker::write_attrs)
    count = ad.predefCount(CODE_ATTR_StackMapTable);
    // disable this feature in old archives!
    if (count != 0 && majver < JAVA6_PACKAGE_MAJOR_VERSION) {
      abort("undefined StackMapTable attribute (old archive format)");
      return;
    }
    code_StackMapTable_N.readData(count);
    CHECK;
    count = code_StackMapTable_N.getIntTotal();
    code_StackMapTable_frame_T.readData(count);
    CHECK;
    // the rest of it depends in a complicated way on frame tags
    {
      int fat_frame_count = 0;
      int offset_count = 0;
      int type_count = 0;
      for (int k = 0; k < count; k++) {
        int tag = code_StackMapTable_frame_T.getByte();
        if (tag <= 127) {
          // (64-127)  [(2)]
          if (tag >= 64)  type_count++;
        } else if (tag <= 251) {
          // (247)     [(1)(2)]
          // (248-251) [(1)]
          if (tag >= 247)  offset_count++;
          if (tag == 247)  type_count++;
        } else if (tag <= 254) {
          // (252)     [(1)(2)]
          // (253)     [(1)(2)(2)]
          // (254)     [(1)(2)(2)(2)]
          offset_count++;
          type_count += (tag - 251);
        } else {
          // (255)     [(1)NH[(2)]NH[(2)]]
          fat_frame_count++;
        }
      }

      // done pre-scanning frame tags:
      code_StackMapTable_frame_T.rewind();

      // deal completely with fat frames:
      offset_count += fat_frame_count;
      code_StackMapTable_local_N.readData(fat_frame_count);
      CHECK;
      type_count += code_StackMapTable_local_N.getIntTotal();
      code_StackMapTable_stack_N.readData(fat_frame_count);
      type_count += code_StackMapTable_stack_N.getIntTotal();
      CHECK;
      // read the rest:
      code_StackMapTable_offset.readData(offset_count);
      code_StackMapTable_T.readData(type_count);
      CHECK;
      // (7) [RCH]
      count = code_StackMapTable_T.getIntCount(7);
      code_StackMapTable_RC.readData(count);
      CHECK;
      // (8) [PH]
      count = code_StackMapTable_T.getIntCount(8);
      code_StackMapTable_P.readData(count);
      CHECK;
    }

    count = ad.predefCount(CODE_ATTR_LineNumberTable);
    code_LineNumberTable_N.readData(count);
    count = code_LineNumberTable_N.getIntTotal();
    code_LineNumberTable_bci_P.readData(count);
    code_LineNumberTable_line.readData(count);

    count = ad.predefCount(CODE_ATTR_LocalVariableTable);
    code_LocalVariableTable_N.readData(count);
    count = code_LocalVariableTable_N.getIntTotal();
    code_LocalVariableTable_bci_P.readData(count);
    code_LocalVariableTable_span_O.readData(count);
    code_LocalVariableTable_name_RU.readData(count);
    code_LocalVariableTable_type_RS.readData(count);
    code_LocalVariableTable_slot.readData(count);

    count = ad.predefCount(CODE_ATTR_LocalVariableTypeTable);
    code_LocalVariableTypeTable_N.readData(count);
    count = code_LocalVariableTypeTable_N.getIntTotal();
    code_LocalVariableTypeTable_bci_P.readData(count);
    code_LocalVariableTypeTable_span_O.readData(count);
    code_LocalVariableTypeTable_name_RU.readData(count);
    code_LocalVariableTypeTable_type_RS.readData(count);
    code_LocalVariableTypeTable_slot.readData(count);
    break;
  }

  // Read compressor-defined bands.
  for (idx = 0; idx < ad.layouts.length(); idx++) {
    if (ad.getLayout(idx) == null)
      continue;  // none at this fixed index <32
    if (idx < (int)ad.flag_limit && ad.isPredefined(idx))
      continue;  // already handled
    if (ad.getCount(idx) == 0)
      continue;  // no attributes of this type (then why transmit layouts?)
    ad.readBandData(idx);
  }
}

void unpacker::attr_definitions::readBandData(int idx) {
  int j;
  uint count = getCount(idx);
  if (count == 0)  return;
  layout_definition* lo = getLayout(idx);
  if (lo != null) {
    PRINTCR((1, "counted %d [redefined = %d predefined = %d] attributes of type %s.%s",
            count, isRedefined(idx), isPredefined(idx),
            ATTR_CONTEXT_NAME[attrc], lo->name));
  }
  bool hasCallables = lo->hasCallables();
  band** bands = lo->bands();
  if (!hasCallables) {
    // Read through the rest of the bands in a regular way.
    readBandData(bands, count);
  } else {
    // Deal with the callables.
    // First set up the forward entry count for each callable.
    // This is stored on band::length of the callable.
    bands[0]->expectMoreLength(count);
    for (j = 0; bands[j] != null; j++) {
      band& j_cble = *bands[j];
      assert(j_cble.le_kind == EK_CBLE);
      if (j_cble.le_back) {
        // Add in the predicted effects of backward calls, too.
        int back_calls = xxx_attr_calls().getInt();
        j_cble.expectMoreLength(back_calls);
        // In a moment, more forward calls may increment j_cble.length.
      }
    }
    // Now consult whichever callables have non-zero entry counts.
    readBandData(bands, (uint)-1);
  }
}

// Recursive helper to the previous function:
void unpacker::attr_definitions::readBandData(band** body, uint count) {
  int j, k;
  for (j = 0; body[j] != null; j++) {
    band& b = *body[j];
    if (b.defc != null) {
      // It has data, so read it.
      b.readData(count);
    }
    switch (b.le_kind) {
    case EK_REPL:
      {
        int reps = b.getIntTotal();
        readBandData(b.le_body, reps);
      }
      break;
    case EK_UN:
      {
        int remaining = count;
        for (k = 0; b.le_body[k] != null; k++) {
          band& k_case = *b.le_body[k];
          int   k_count = 0;
          if (k_case.le_casetags == null) {
            k_count = remaining;  // last (empty) case
          } else {
            int* tags = k_case.le_casetags;
            int ntags = *tags++;  // 1st element is length (why not?)
            while (ntags-- > 0) {
              int tag = *tags++;
              k_count += b.getIntCount(tag);
            }
          }
          readBandData(k_case.le_body, k_count);
          remaining -= k_count;
        }
        assert(remaining == 0);
      }
      break;
    case EK_CALL:
      // Push the count forward, if it is not a backward call.
      if (!b.le_back) {
        band& cble = *b.le_body[0];
        assert(cble.le_kind == EK_CBLE);
        cble.expectMoreLength(count);
      }
      break;
    case EK_CBLE:
      assert((int)count == -1);  // incoming count is meaningless
      k = b.length;
      assert(k >= 0);
      // This is intended and required for non production mode.
      assert((b.length = -1)); // make it unable to accept more calls now.
      readBandData(b.le_body, k);
      break;
    }
  }
}

static inline
band** findMatchingCase(int matchTag, band** cases) {
  for (int k = 0; cases[k] != null; k++) {
    band& k_case = *cases[k];
    if (k_case.le_casetags != null) {
      // If it has tags, it must match a tag.
      int* tags = k_case.le_casetags;
      int ntags = *tags++;  // 1st element is length
      for (; ntags > 0; ntags--) {
        int tag = *tags++;
        if (tag == matchTag)
          break;
      }
      if (ntags == 0)
        continue;   // does not match
    }
    return k_case.le_body;
  }
  return null;
}

// write attribute band data:
void unpacker::putlayout(band** body) {
  int i;
  int prevBII = -1;
  int prevBCI = -1;
  for (i = 0; body[i] != null; i++) {
    band& b = *body[i];
    byte le_kind = b.le_kind;

    // Handle scalar part, if any.
    int    x = 0;
    entry* e = null;
    if (b.defc != null) {
      // It has data, so unparse an element.
      if (b.ixTag != CONSTANT_None) {
        assert(le_kind == EK_REF);
        if (b.ixTag == CONSTANT_Literal)
          e = b.getRefUsing(cp.getKQIndex());
        else
          e = b.getRefN();
        switch (b.le_len) {
        case 0: break;
        case 1: putu1ref(e); break;
        case 2: putref(e); break;
        case 4: putu2(0); putref(e); break;
        default: assert(false);
        }
      } else {
        assert(le_kind == EK_INT || le_kind == EK_REPL || le_kind == EK_UN);
        x = b.getInt();

        assert(!b.le_bci || prevBCI == (int)to_bci(prevBII));
        switch (b.le_bci) {
        case EK_BCI:   // PH:  transmit R(bci), store bci
          x = to_bci(prevBII = x);
          prevBCI = x;
          break;
        case EK_BCID:  // POH: transmit D(R(bci)), store bci
          x = to_bci(prevBII += x);
          prevBCI = x;
          break;
        case EK_BCO:   // OH:  transmit D(R(bci)), store D(bci)
          x = to_bci(prevBII += x) - prevBCI;
          prevBCI += x;
          break;
        }
        assert(!b.le_bci || prevBCI == (int)to_bci(prevBII));

        switch (b.le_len) {
        case 0: break;
        case 1: putu1(x); break;
        case 2: putu2(x); break;
        case 4: putu4(x); break;
        default: assert(false);
        }
      }
    }

    // Handle subparts, if any.
    switch (le_kind) {
    case EK_REPL:
      // x is the repeat count
      while (x-- > 0) {
        putlayout(b.le_body);
      }
      break;
    case EK_UN:
      // x is the tag
      putlayout(findMatchingCase(x, b.le_body));
      break;
    case EK_CALL:
      {
        band& cble = *b.le_body[0];
        assert(cble.le_kind == EK_CBLE);
        assert(cble.le_len == b.le_len);
        putlayout(cble.le_body);
      }
      break;

    #ifndef PRODUCT
    case EK_CBLE:
    case EK_CASE:
      assert(false);  // should not reach here
    #endif
    }
  }
}

void unpacker::read_files() {
  file_name.readData(file_count);
  if ((archive_options & AO_HAVE_FILE_SIZE_HI) != 0)
    file_size_hi.readData(file_count);
  file_size_lo.readData(file_count);
  if ((archive_options & AO_HAVE_FILE_MODTIME) != 0)
    file_modtime.readData(file_count);
  int allFiles = file_count + class_count;
  if ((archive_options & AO_HAVE_FILE_OPTIONS) != 0) {
    file_options.readData(file_count);
    // FO_IS_CLASS_STUB might be set, causing overlap between classes and files
    for (int i = 0; i < file_count; i++) {
      if ((file_options.getInt() & FO_IS_CLASS_STUB) != 0) {
        allFiles -= 1;  // this one counts as both class and file
      }
    }
    file_options.rewind();
  }
  assert((default_file_options & FO_IS_CLASS_STUB) == 0);
  files_remaining = allFiles;
}

maybe_inline
void unpacker::get_code_header(int& max_stack,
                               int& max_na_locals,
                               int& handler_count,
                               int& cflags) {
  int sc = code_headers.getByte();
  if (sc == 0) {
    max_stack = max_na_locals = handler_count = cflags = -1;
    return;
  }
  // Short code header is the usual case:
  int nh;
  int mod;
  if (sc < 1 + 12*12) {
    sc -= 1;
    nh = 0;
    mod = 12;
  } else if (sc < 1 + 12*12 + 8*8) {
    sc -= 1 + 12*12;
    nh = 1;
    mod = 8;
  } else {
    assert(sc < 1 + 12*12 + 8*8 + 7*7);
    sc -= 1 + 12*12 + 8*8;
    nh = 2;
    mod = 7;
  }
  max_stack     = sc % mod;
  max_na_locals = sc / mod;  // caller must add static, siglen
  handler_count = nh;
  if ((archive_options & AO_HAVE_ALL_CODE_FLAGS) != 0)
    cflags      = -1;
  else
    cflags      = 0;  // this one has no attributes
}

// Cf. PackageReader.readCodeHeaders
void unpacker::read_code_headers() {
  code_headers.readData(code_count);
  CHECK;
  int totalHandlerCount = 0;
  int totalFlagsCount   = 0;
  for (int i = 0; i < code_count; i++) {
    int max_stack, max_locals, handler_count, cflags;
    get_code_header(max_stack, max_locals, handler_count, cflags);
    if (max_stack < 0)      code_max_stack.expectMoreLength(1);
    if (max_locals < 0)     code_max_na_locals.expectMoreLength(1);
    if (handler_count < 0)  code_handler_count.expectMoreLength(1);
    else                    totalHandlerCount += handler_count;
    if (cflags < 0)         totalFlagsCount += 1;
  }
  code_headers.rewind();  // replay later during writing

  code_max_stack.readData();
  code_max_na_locals.readData();
  code_handler_count.readData();
  totalHandlerCount += code_handler_count.getIntTotal();
  CHECK;

  // Read handler specifications.
  // Cf. PackageReader.readCodeHandlers.
  code_handler_start_P.readData(totalHandlerCount);
  code_handler_end_PO.readData(totalHandlerCount);
  code_handler_catch_PO.readData(totalHandlerCount);
  code_handler_class_RCN.readData(totalHandlerCount);
  CHECK;

  read_attrs(ATTR_CONTEXT_CODE, totalFlagsCount);
  CHECK;
}

static inline bool is_in_range(uint n, uint min, uint max) {
  return n - min <= max - min;  // unsigned arithmetic!
}
static inline bool is_field_op(int bc) {
  return is_in_range(bc, bc_getstatic, bc_putfield);
}
static inline bool is_invoke_init_op(int bc) {
  return is_in_range(bc, _invokeinit_op, _invokeinit_limit-1);
}
static inline bool is_self_linker_op(int bc) {
  return is_in_range(bc, _self_linker_op, _self_linker_limit-1);
}
static bool is_branch_op(int bc) {
  return is_in_range(bc, bc_ifeq,   bc_jsr)
      || is_in_range(bc, bc_ifnull, bc_jsr_w);
}
static bool is_local_slot_op(int bc) {
  return is_in_range(bc, bc_iload,  bc_aload)
      || is_in_range(bc, bc_istore, bc_astore)
      || bc == bc_iinc || bc == bc_ret;
}
band* unpacker::ref_band_for_op(int bc) {
  switch (bc) {
  case bc_ildc:
  case bc_ildc_w:
    return &bc_intref;
  case bc_fldc:
  case bc_fldc_w:
    return &bc_floatref;
  case bc_lldc2_w:
    return &bc_longref;
  case bc_dldc2_w:
    return &bc_doubleref;
  case bc_aldc:
  case bc_aldc_w:
    return &bc_stringref;
  case bc_cldc:
  case bc_cldc_w:
    return &bc_classref;

  case bc_getstatic:
  case bc_putstatic:
  case bc_getfield:
  case bc_putfield:
    return &bc_fieldref;

  case bc_invokevirtual:
  case bc_invokespecial:
  case bc_invokestatic:
    return &bc_methodref;
  case bc_invokeinterface:
    return &bc_imethodref;

  case bc_new:
  case bc_anewarray:
  case bc_checkcast:
  case bc_instanceof:
  case bc_multianewarray:
    return &bc_classref;
  }
  return null;
}

maybe_inline
band* unpacker::ref_band_for_self_op(int bc, bool& isAloadVar, int& origBCVar) {
  if (!is_self_linker_op(bc))  return null;
  int idx = (bc - _self_linker_op);
  bool isSuper = (idx >= _self_linker_super_flag);
  if (isSuper)  idx -= _self_linker_super_flag;
  bool isAload = (idx >= _self_linker_aload_flag);
  if (isAload)  idx -= _self_linker_aload_flag;
  int origBC = _first_linker_op + idx;
  bool isField = is_field_op(origBC);
  isAloadVar = isAload;
  origBCVar  = _first_linker_op + idx;
  if (!isSuper)
    return isField? &bc_thisfield: &bc_thismethod;
  else
    return isField? &bc_superfield: &bc_supermethod;
}

// Cf. PackageReader.readByteCodes
inline  // called exactly once => inline
void unpacker::read_bcs() {
  PRINTCR((3, "reading compressed bytecodes and operands for %d codes...",
          code_count));

  // read from bc_codes and bc_case_count
  fillbytes all_switch_ops;
  all_switch_ops.init();
  CHECK;

  // Read directly from rp/rplimit.
  //Do this later:  bc_codes.readData(...)
  byte* rp0 = rp;

  band* bc_which;
  byte* opptr = rp;
  byte* oplimit = rplimit;

  bool  isAload;  // passed by ref and then ignored
  int   junkBC;   // passed by ref and then ignored
  for (int k = 0; k < code_count; k++) {
    // Scan one method:
    for (;;) {
      if (opptr+2 > oplimit) {
        rp = opptr;
        ensure_input(2);
        oplimit = rplimit;
        rp = rp0;  // back up
      }
      if (opptr == oplimit) { abort(); break; }
      int bc = *opptr++ & 0xFF;
      bool isWide = false;
      if (bc == bc_wide) {
        if (opptr == oplimit) { abort(); break; }
        bc = *opptr++ & 0xFF;
        isWide = true;
      }
      // Adjust expectations of various band sizes.
      switch (bc) {
      case bc_tableswitch:
      case bc_lookupswitch:
        all_switch_ops.addByte(bc);
        break;
      case bc_iinc:
        bc_local.expectMoreLength(1);
        bc_which = isWide ? &bc_short : &bc_byte;
        bc_which->expectMoreLength(1);
        break;
      case bc_sipush:
        bc_short.expectMoreLength(1);
        break;
      case bc_bipush:
        bc_byte.expectMoreLength(1);
        break;
      case bc_newarray:
        bc_byte.expectMoreLength(1);
        break;
      case bc_multianewarray:
        assert(ref_band_for_op(bc) == &bc_classref);
        bc_classref.expectMoreLength(1);
        bc_byte.expectMoreLength(1);
        break;
      case bc_ref_escape:
        bc_escrefsize.expectMoreLength(1);
        bc_escref.expectMoreLength(1);
        break;
      case bc_byte_escape:
        bc_escsize.expectMoreLength(1);
        // bc_escbyte will have to be counted too
        break;
      default:
        if (is_invoke_init_op(bc)) {
          bc_initref.expectMoreLength(1);
          break;
        }
        bc_which = ref_band_for_self_op(bc, isAload, junkBC);
        if (bc_which != null) {
          bc_which->expectMoreLength(1);
          break;
        }
        if (is_branch_op(bc)) {
          bc_label.expectMoreLength(1);
          break;
        }
        bc_which = ref_band_for_op(bc);
        if (bc_which != null) {
          bc_which->expectMoreLength(1);
          assert(bc != bc_multianewarray);  // handled elsewhere
          break;
        }
        if (is_local_slot_op(bc)) {
          bc_local.expectMoreLength(1);
          break;
        }
        break;
      case bc_end_marker:
        // Increment k and test against code_count.
        goto doneScanningMethod;
      }
    }
  doneScanningMethod:{}
    if (aborting())  break;
  }

  // Go through the formality, so we can use it in a regular fashion later:
  assert(rp == rp0);
  bc_codes.readData((int)(opptr - rp));

  int i = 0;

  // To size instruction bands correctly, we need info on switches:
  bc_case_count.readData((int)all_switch_ops.size());
  for (i = 0; i < (int)all_switch_ops.size(); i++) {
    int caseCount = bc_case_count.getInt();
    int bc        = all_switch_ops.getByte(i);
    bc_label.expectMoreLength(1+caseCount); // default label + cases
    bc_case_value.expectMoreLength(bc == bc_tableswitch ? 1 : caseCount);
    PRINTCR((2, "switch bc=%d caseCount=%d", bc, caseCount));
  }
  bc_case_count.rewind();  // uses again for output

  all_switch_ops.free();

  for (i = e_bc_case_value; i <= e_bc_escsize; i++) {
    all_bands[i].readData();
  }

  // The bc_escbyte band is counted by the immediately previous band.
  bc_escbyte.readData(bc_escsize.getIntTotal());

  PRINTCR((3, "scanned %d opcode and %d operand bytes for %d codes...",
          (int)(bc_codes.size()),
          (int)(bc_escsize.maxRP() - bc_case_value.minRP()),
          code_count));
}

void unpacker::read_bands() {
  byte* rp0 = rp;

  read_file_header();
  CHECK;

  if (cp.nentries == 0) {
    // read_file_header failed to read a CP, because it copied a JAR.
    return;
  }

  // Do this after the file header has been read:
  check_options();

  read_cp();
  CHECK;
  read_attr_defs();
  CHECK;
  read_ics();
  CHECK;
  read_classes();
  CHECK;
  read_bcs();
  CHECK;
  read_files();
}

/// CP routines

entry*& cpool::hashTabRef(byte tag, bytes& b) {
  PRINTCR((5, "hashTabRef tag=%d %s[%d]", tag, b.string(), b.len));
  uint hash = tag + (int)b.len;
  for (int i = 0; i < (int)b.len; i++) {
    hash = hash * 31 + (0xFF & b.ptr[i]);
  }
  entry**  ht = hashTab;
  int    hlen = hashTabLength;
  assert((hlen & (hlen-1)) == 0);  // must be power of 2
  uint hash1 = hash & (hlen-1);    // == hash % hlen
  uint hash2 = 0;                  // lazily computed (requires mod op.)
  int probes = 0;
  while (ht[hash1] != null) {
    entry& e = *ht[hash1];
    if (e.value.b.equals(b) && e.tag == tag)
      break;
    if (hash2 == 0)
      // Note:  hash2 must be relatively prime to hlen, hence the "|1".
      hash2 = (((hash % 499) & (hlen-1)) | 1);
    hash1 += hash2;
    if (hash1 >= (uint)hlen)  hash1 -= hlen;
    assert(hash1 < (uint)hlen);
    assert(++probes < hlen);
  }
  #ifndef PRODUCT
  hash_probes[0] += 1;
  hash_probes[1] += probes;
  #endif
  PRINTCR((5, " => @%d %p", hash1, ht[hash1]));
  return ht[hash1];
}

maybe_inline
static void insert_extra(entry* e, ptrlist& extras) {
  // This ordering helps implement the Pack200 requirement
  // of a predictable CP order in the class files produced.
  e->inord = NO_INORD;  // mark as an "extra"
  extras.add(e);
  // Note:  We will sort the list (by string-name) later.
}

entry* cpool::ensureUtf8(bytes& b) {
  entry*& ix = hashTabRef(CONSTANT_Utf8, b);
  if (ix != null)  return ix;
  // Make one.
  if (nentries == maxentries) {
    abort("cp utf8 overflow");
    return &entries[tag_base[CONSTANT_Utf8]];  // return something
  }
  entry& e = entries[nentries++];
  e.tag = CONSTANT_Utf8;
  u->saveTo(e.value.b, b);
  assert(&e >= first_extra_entry);
  insert_extra(&e, tag_extras[CONSTANT_Utf8]);
  PRINTCR((4,"ensureUtf8 miss %s", e.string()));
  return ix = &e;
}

entry* cpool::ensureClass(bytes& b) {
  entry*& ix = hashTabRef(CONSTANT_Class, b);
  if (ix != null)  return ix;
  // Make one.
  if (nentries == maxentries) {
    abort("cp class overflow");
    return &entries[tag_base[CONSTANT_Class]];  // return something
  }
  entry& e = entries[nentries++];
  e.tag = CONSTANT_Class;
  e.nrefs = 1;
  e.refs = U_NEW(entry*, 1);
  ix = &e;  // hold my spot in the index
  entry* utf = ensureUtf8(b);
  e.refs[0] = utf;
  e.value.b = utf->value.b;
  assert(&e >= first_extra_entry);
  insert_extra(&e, tag_extras[CONSTANT_Class]);
  PRINTCR((4,"ensureClass miss %s", e.string()));
  return &e;
}

void cpool::expandSignatures() {
  int i;
  int nsigs = 0;
  int nreused = 0;
  int first_sig = tag_base[CONSTANT_Signature];
  int sig_limit = tag_count[CONSTANT_Signature] + first_sig;
  fillbytes buf;
  buf.init(1<<10);
  CHECK;
  for (i = first_sig; i < sig_limit; i++) {
    entry& e = entries[i];
    assert(e.tag == CONSTANT_Signature);
    int refnum = 0;
    bytes form = e.refs[refnum++]->asUtf8();
    buf.empty();
    for (int j = 0; j < (int)form.len; j++) {
      int c = form.ptr[j];
      buf.addByte(c);
      if (c == 'L') {
        entry* cls = e.refs[refnum++];
        buf.append(cls->className()->asUtf8());
      }
    }
    assert(refnum == e.nrefs);
    bytes& sig = buf.b;
    PRINTCR((5,"signature %d %s -> %s", i, form.ptr, sig.ptr));

    // try to find a pre-existing Utf8:
    entry* &e2 = hashTabRef(CONSTANT_Utf8, sig);
    if (e2 != null) {
      assert(e2->isUtf8(sig));
      e.value.b = e2->value.b;
      e.refs[0] = e2;
      e.nrefs = 1;
      PRINTCR((5,"signature replaced %d => %s", i, e.string()));
      nreused++;
    } else {
      // there is no other replacement; reuse this CP entry as a Utf8
      u->saveTo(e.value.b, sig);
      e.tag = CONSTANT_Utf8;
      e.nrefs = 0;
      e2 = &e;
      PRINTCR((5,"signature changed %d => %s", e.inord, e.string()));
    }
    nsigs++;
  }
  PRINTCR((1,"expanded %d signatures (reused %d utfs)", nsigs, nreused));
  buf.free();

  // go expunge all references to remaining signatures:
  for (i = 0; i < (int)nentries; i++) {
    entry& e = entries[i];
    for (int j = 0; j < e.nrefs; j++) {
      entry*& e2 = e.refs[j];
      if (e2 != null && e2->tag == CONSTANT_Signature)
        e2 = e2->refs[0];
    }
  }
}

void cpool::initMemberIndexes() {
  // This function does NOT refer to any class schema.
  // It is totally internal to the cpool.
  int i, j;

  // Get the pre-existing indexes:
  int   nclasses = tag_count[CONSTANT_Class];
  entry* classes = tag_base[CONSTANT_Class] + entries;
  int   nfields  = tag_count[CONSTANT_Fieldref];
  entry* fields  = tag_base[CONSTANT_Fieldref] + entries;
  int   nmethods = tag_count[CONSTANT_Methodref];
  entry* methods = tag_base[CONSTANT_Methodref] + entries;

  int*     field_counts  = T_NEW(int, nclasses);
  int*     method_counts = T_NEW(int, nclasses);
  cpindex* all_indexes   = U_NEW(cpindex, nclasses*2);
  entry**  field_ix      = U_NEW(entry*, add_size(nfields, nclasses));
  entry**  method_ix     = U_NEW(entry*, add_size(nmethods, nclasses));

  for (j = 0; j < nfields; j++) {
    entry& f = fields[j];
    i = f.memberClass()->inord;
    assert(i < nclasses);
    field_counts[i]++;
  }
  for (j = 0; j < nmethods; j++) {
    entry& m = methods[j];
    i = m.memberClass()->inord;
    assert(i < nclasses);
    method_counts[i]++;
  }

  int fbase = 0, mbase = 0;
  for (i = 0; i < nclasses; i++) {
    int fc = field_counts[i];
    int mc = method_counts[i];
    all_indexes[i*2+0].init(fc, field_ix+fbase,
                            CONSTANT_Fieldref  + SUBINDEX_BIT);
    all_indexes[i*2+1].init(mc, method_ix+mbase,
                            CONSTANT_Methodref + SUBINDEX_BIT);
    // reuse field_counts and member_counts as fill pointers:
    field_counts[i] = fbase;
    method_counts[i] = mbase;
    PRINTCR((3, "class %d fields @%d[%d] methods @%d[%d]",
            i, fbase, fc, mbase, mc));
    fbase += fc+1;
    mbase += mc+1;
    // (the +1 leaves a space between every subarray)
  }
  assert(fbase == nfields+nclasses);
  assert(mbase == nmethods+nclasses);

  for (j = 0; j < nfields; j++) {
    entry& f = fields[j];
    i = f.memberClass()->inord;
    field_ix[field_counts[i]++] = &f;
  }
  for (j = 0; j < nmethods; j++) {
    entry& m = methods[j];
    i = m.memberClass()->inord;
    method_ix[method_counts[i]++] = &m;
  }

  member_indexes = all_indexes;

#ifndef PRODUCT
  // Test the result immediately on every class and field.
  int fvisited = 0, mvisited = 0;
  int prevord, len;
  for (i = 0; i < nclasses; i++) {
    entry*   cls = &classes[i];
    cpindex* fix = getFieldIndex(cls);
    cpindex* mix = getMethodIndex(cls);
    PRINTCR((2, "field and method index for %s [%d] [%d]",
            cls->string(), mix->len, fix->len));
    prevord = -1;
    for (j = 0, len = fix->len; j < len; j++) {
      entry* f = fix->get(j);
      assert(f != null);
      PRINTCR((3, "- field %s", f->string()));
      assert(f->memberClass() == cls);
      assert(prevord < (int)f->inord);
      prevord = f->inord;
      fvisited++;
    }
    assert(fix->base2[j] == null);
    prevord = -1;
    for (j = 0, len = mix->len; j < len; j++) {
      entry* m = mix->get(j);
      assert(m != null);
      PRINTCR((3, "- method %s", m->string()));
      assert(m->memberClass() == cls);
      assert(prevord < (int)m->inord);
      prevord = m->inord;
      mvisited++;
    }
    assert(mix->base2[j] == null);
  }
  assert(fvisited == nfields);
  assert(mvisited == nmethods);
#endif

  // Free intermediate buffers.
  u->free_temps();
}

void entry::requestOutputIndex(cpool& cp, int req) {
  assert(outputIndex <= NOT_REQUESTED);  // must not have assigned indexes yet
  if (tag == CONSTANT_Signature) {
    ref(0)->requestOutputIndex(cp, req);
    return;
  }
  assert(req == REQUESTED || req == REQUESTED_LDC);
  if (outputIndex != NOT_REQUESTED) {
    if (req == REQUESTED_LDC)
      outputIndex = req;  // this kind has precedence
    return;
  }
  outputIndex = req;
  //assert(!cp.outputEntries.contains(this));
  assert(tag != CONSTANT_Signature);
  cp.outputEntries.add(this);
  for (int j = 0; j < nrefs; j++) {
    ref(j)->requestOutputIndex(cp);
  }
}

void cpool::resetOutputIndexes() {
  int i;
  int    noes =           outputEntries.length();
  entry** oes = (entry**) outputEntries.base();
  for (i = 0; i < noes; i++) {
    entry& e = *oes[i];
    e.outputIndex = NOT_REQUESTED;
  }
  outputIndexLimit = 0;
  outputEntries.empty();
#ifndef PRODUCT
  // they must all be clear now
  for (i = 0; i < (int)nentries; i++)
    assert(entries[i].outputIndex == NOT_REQUESTED);
#endif
}

static const byte TAG_ORDER[CONSTANT_Limit] = {
  0, 1, 0, 2, 3, 4, 5, 7, 6, 10, 11, 12, 9, 8
};

extern "C"
int outputEntry_cmp(const void* e1p, const void* e2p) {
  // Sort entries according to the Pack200 rules for deterministic
  // constant pool ordering.
  //
  // The four sort keys as follows, in order of decreasing importance:
  //   1. ldc first, then non-ldc guys
  //   2. normal cp_All entries by input order (i.e., address order)
  //   3. after that, extra entries by lexical order (as in tag_extras[*])
  entry& e1 = *(entry*) *(void**) e1p;
  entry& e2 = *(entry*) *(void**) e2p;
  int   oi1 = e1.outputIndex;
  int   oi2 = e2.outputIndex;
  assert(oi1 == REQUESTED || oi1 == REQUESTED_LDC);
  assert(oi2 == REQUESTED || oi2 == REQUESTED_LDC);
  if (oi1 != oi2) {
    if (oi1 == REQUESTED_LDC)  return 0-1;
    if (oi2 == REQUESTED_LDC)  return 1-0;
    // Else fall through; neither is an ldc request.
  }
  if (e1.inord != NO_INORD || e2.inord != NO_INORD) {
    // One or both is normal.  Use input order.
    if (&e1 > &e2)  return 1-0;
    if (&e1 < &e2)  return 0-1;
    return 0;  // equal pointers
  }
  // Both are extras.  Sort by tag and then by value.
  if (e1.tag != e2.tag) {
    return TAG_ORDER[e1.tag] - TAG_ORDER[e2.tag];
  }
  // If the tags are the same, use string comparison.
  return compare_Utf8_chars(e1.value.b, e2.value.b);
}

void cpool::computeOutputIndexes() {
  int i;

#ifndef PRODUCT
  // outputEntries must be a complete list of those requested:
  static uint checkStart = 0;
  int checkStep = 1;
  if (nentries > 100)  checkStep = nentries / 100;
  for (i = (int)(checkStart++ % checkStep); i < (int)nentries; i += checkStep) {
    entry& e = entries[i];
    if (e.outputIndex != NOT_REQUESTED) {
      assert(outputEntries.contains(&e));
    } else {
      assert(!outputEntries.contains(&e));
    }
  }

  // check hand-initialization of TAG_ORDER
  for (i = 0; i < (int)N_TAGS_IN_ORDER; i++) {
    byte tag = TAGS_IN_ORDER[i];
    assert(TAG_ORDER[tag] == i+1);
  }
#endif

  int    noes =           outputEntries.length();
  entry** oes = (entry**) outputEntries.base();

  // Sort the output constant pool into the order required by Pack200.
  PTRLIST_QSORT(outputEntries, outputEntry_cmp);

  // Allocate a new index for each entry that needs one.
  // We do this in two passes, one for LDC entries and one for the rest.
  int nextIndex = 1;  // always skip index #0 in output cpool
  for (i = 0; i < noes; i++) {
    entry& e = *oes[i];
    assert(e.outputIndex == REQUESTED || e.outputIndex == REQUESTED_LDC);
    e.outputIndex = nextIndex++;
    if (e.isDoubleWord())  nextIndex++;  // do not use the next index
  }
  outputIndexLimit = nextIndex;
  PRINTCR((3,"renumbering CP to %d entries", outputIndexLimit));
}

#ifndef PRODUCT
// debugging goo

unpacker* debug_u;

static bytes& getbuf(int len) {  // for debugging only!
  static int bn = 0;
  static bytes bufs[8];
  bytes& buf = bufs[bn++ & 7];
  while ((int)buf.len < len+10)
    buf.realloc(buf.len ? buf.len * 2 : 1000);
  buf.ptr[0] = 0;  // for the sake of strcat
  return buf;
}

char* entry::string() {
  bytes buf;
  switch (tag) {
  case CONSTANT_None:
    return (char*)"<empty>";
  case CONSTANT_Signature:
    if (value.b.ptr == null)
      return ref(0)->string();
    // else fall through:
  case CONSTANT_Utf8:
    buf = value.b;
    break;
  case CONSTANT_Integer:
  case CONSTANT_Float:
    buf = getbuf(12);
    sprintf((char*)buf.ptr, "0x%08x", value.i);
    break;
  case CONSTANT_Long:
  case CONSTANT_Double:
    buf = getbuf(24);
    sprintf((char*)buf.ptr, "0x" LONG_LONG_HEX_FORMAT, value.l);
    break;
  default:
    if (nrefs == 0) {
      buf = getbuf(20);
      sprintf((char*)buf.ptr, "<tag=%d>", tag);
    } else if (nrefs == 1) {
      return refs[0]->string();
    } else {
      char* s1 = refs[0]->string();
      char* s2 = refs[1]->string();
      buf = getbuf((int)strlen(s1) + 1 + (int)strlen(s2) + 4 + 1);
      buf.strcat(s1).strcat(" ").strcat(s2);
      if (nrefs > 2)  buf.strcat(" ...");
    }
  }
  return (char*)buf.ptr;
}

void print_cp_entry(int i) {
  entry& e = debug_u->cp.entries[i];
  char buf[30];
  sprintf(buf, ((uint)e.tag < CONSTANT_Limit)? TAG_NAME[e.tag]: "%d", e.tag);
  printf(" %d\t%s %s\n", i, buf, e.string());
}

void print_cp_entries(int beg, int end) {
  for (int i = beg; i < end; i++)
    print_cp_entry(i);
}

void print_cp() {
  print_cp_entries(0, debug_u->cp.nentries);
}

#endif

// Unpacker Start

const char str_tf[] = "true\0false";
#undef STR_TRUE
#undef STR_FALSE
#define STR_TRUE   (&str_tf[0])
#define STR_FALSE  (&str_tf[5])

const char* unpacker::get_option(const char* prop) {
  if (prop == null )  return null;
  if (strcmp(prop, UNPACK_DEFLATE_HINT) == 0) {
    return deflate_hint_or_zero == 0? null : STR_TF(deflate_hint_or_zero > 0);
#ifdef HAVE_STRIP
  } else if (strcmp(prop, UNPACK_STRIP_COMPILE) == 0) {
    return STR_TF(strip_compile);
  } else if (strcmp(prop, UNPACK_STRIP_DEBUG) == 0) {
    return STR_TF(strip_debug);
  } else if (strcmp(prop, UNPACK_STRIP_JCOV) == 0) {
    return STR_TF(strip_jcov);
#endif /*HAVE_STRIP*/
  } else if (strcmp(prop, UNPACK_REMOVE_PACKFILE) == 0) {
    return STR_TF(remove_packfile);
  } else if (strcmp(prop, DEBUG_VERBOSE) == 0) {
    return saveIntStr(verbose);
  } else if (strcmp(prop, UNPACK_MODIFICATION_TIME) == 0) {
    return (modification_time_or_zero == 0)? null:
      saveIntStr(modification_time_or_zero);
  } else if (strcmp(prop, UNPACK_LOG_FILE) == 0) {
    return log_file;
  } else {
    return NULL; // unknown option ignore
  }
}

bool unpacker::set_option(const char* prop, const char* value) {
  if (prop == NULL)  return false;
  if (strcmp(prop, UNPACK_DEFLATE_HINT) == 0) {
    deflate_hint_or_zero = ( (value == null || strcmp(value, "keep") == 0)
                                ? 0: BOOL_TF(value) ? +1: -1);
#ifdef HAVE_STRIP
  } else if (strcmp(prop, UNPACK_STRIP_COMPILE) == 0) {
    strip_compile = STR_TF(value);
  } else if (strcmp(prop, UNPACK_STRIP_DEBUG) == 0) {
    strip_debug = STR_TF(value);
  } else if (strcmp(prop, UNPACK_STRIP_JCOV) == 0) {
    strip_jcov = STR_TF(value);
#endif /*HAVE_STRIP*/
  } else if (strcmp(prop, UNPACK_REMOVE_PACKFILE) == 0) {
    remove_packfile = STR_TF(value);
  } else if (strcmp(prop, DEBUG_VERBOSE) == 0) {
    verbose = (value == null)? 0: atoi(value);
  } else if (strcmp(prop, DEBUG_VERBOSE ".bands") == 0) {
#ifndef PRODUCT
    verbose_bands = (value == null)? 0: atoi(value);
#endif
  } else if (strcmp(prop, UNPACK_MODIFICATION_TIME) == 0) {
    if (value == null || (strcmp(value, "keep") == 0)) {
      modification_time_or_zero = 0;
    } else if (strcmp(value, "now") == 0) {
      time_t now;
      time(&now);
      modification_time_or_zero = (int) now;
    } else {
      modification_time_or_zero = atoi(value);
      if (modification_time_or_zero == 0)
        modification_time_or_zero = 1;  // make non-zero
    }
  } else if (strcmp(prop, UNPACK_LOG_FILE) == 0) {
    log_file = (value == null)? value: saveStr(value);
  } else {
    return false; // unknown option ignore
  }
  return true;
}

// Deallocate all internal storage and reset to a clean state.
// Do not disturb any input or output connections, including
// infileptr, infileno, inbytes, read_input_fn, jarout, or errstrm.
// Do not reset any unpack options.
void unpacker::reset() {
  bytes_read_before_reset      += bytes_read;
  bytes_written_before_reset   += bytes_written;
  files_written_before_reset   += files_written;
  classes_written_before_reset += classes_written;
  segments_read_before_reset   += 1;
  if (verbose >= 2) {
    fprintf(errstrm,
            "After segment %d, "
            LONG_LONG_FORMAT " bytes read and "
            LONG_LONG_FORMAT " bytes written.\n",
            segments_read_before_reset-1,
            bytes_read_before_reset, bytes_written_before_reset);
    fprintf(errstrm,
            "After segment %d, %d files (of which %d are classes) written to output.\n",
            segments_read_before_reset-1,
            files_written_before_reset, classes_written_before_reset);
    if (archive_next_count != 0) {
      fprintf(errstrm,
              "After segment %d, %d segment%s remaining (estimated).\n",
              segments_read_before_reset-1,
              archive_next_count, archive_next_count==1?"":"s");
    }
  }

  unpacker save_u = (*this);  // save bytewise image
  infileptr = null;  // make asserts happy
  jniobj = null;  // make asserts happy
  jarout = null;  // do not close the output jar
  gzin = null;  // do not close the input gzip stream
  bytes esn;
  if (errstrm_name != null) {
    esn.saveFrom(errstrm_name);
  } else {
    esn.set(null, 0);
  }
  this->free();
  mtrace('s', 0, 0);  // note the boundary between segments
  this->init(read_input_fn);

  // restore selected interface state:
#define SAVE(x) this->x = save_u.x
  SAVE(jniobj);
  SAVE(jnienv);
  SAVE(infileptr);  // buffered
  SAVE(infileno);   // unbuffered
  SAVE(inbytes);    // direct
  SAVE(jarout);
  SAVE(gzin);
  //SAVE(read_input_fn);
  SAVE(errstrm);
  SAVE(verbose);  // verbose level, 0 means no output
  SAVE(strip_compile);
  SAVE(strip_debug);
  SAVE(strip_jcov);
  SAVE(remove_packfile);
  SAVE(deflate_hint_or_zero);  // ==0 means not set, otherwise -1 or 1
  SAVE(modification_time_or_zero);
  SAVE(bytes_read_before_reset);
  SAVE(bytes_written_before_reset);
  SAVE(files_written_before_reset);
  SAVE(classes_written_before_reset);
  SAVE(segments_read_before_reset);
#undef SAVE
  if (esn.len > 0) {
    errstrm_name = saveStr(esn.strval());
    esn.free();
  }
  log_file = errstrm_name;
  // Note:  If we use strip_names, watch out:  They get nuked here.
}

void unpacker::init(read_input_fn_t input_fn) {
  int i;
  NOT_PRODUCT(debug_u = this);
  BYTES_OF(*this).clear();
#ifndef PRODUCT
  free();  // just to make sure freeing is idempotent
#endif
  this->u = this;    // self-reference for U_NEW macro
  errstrm = stdout;  // default error-output
  log_file = LOGFILE_STDOUT;
  read_input_fn = input_fn;
  all_bands = band::makeBands(this);
  // Make a default jar buffer; caller may safely overwrite it.
  jarout = U_NEW(jar, 1);
  jarout->init(this);
  for (i = 0; i < ATTR_CONTEXT_LIMIT; i++)
    attr_defs[i].u = u;  // set up outer ptr
}

const char* unpacker::get_abort_message() {
   return abort_message;
}

void unpacker::dump_options() {
  static const char* opts[] = {
    UNPACK_LOG_FILE,
    UNPACK_DEFLATE_HINT,
#ifdef HAVE_STRIP
    UNPACK_STRIP_COMPILE,
    UNPACK_STRIP_DEBUG,
    UNPACK_STRIP_JCOV,
#endif /*HAVE_STRIP*/
    UNPACK_REMOVE_PACKFILE,
    DEBUG_VERBOSE,
    UNPACK_MODIFICATION_TIME,
    null
  };
  for (int i = 0; opts[i] != null; i++) {
    const char* str = get_option(opts[i]);
    if (str == null) {
      if (verbose == 0)  continue;
      str = "(not set)";
    }
    fprintf(errstrm, "%s=%s\n", opts[i], str);
  }
}


// Usage: unpack a byte buffer
// packptr is a reference to byte buffer containing a
// packed file and len is the length of the buffer.
// If null, the callback is used to fill an internal buffer.
void unpacker::start(void* packptr, size_t len) {
  NOT_PRODUCT(debug_u = this);
  if (packptr != null && len != 0) {
    inbytes.set((byte*) packptr, len);
  }
  read_bands();
}

void unpacker::check_options() {
  const char* strue  = "true";
  const char* sfalse = "false";
  if (deflate_hint_or_zero != 0) {
    bool force_deflate_hint = (deflate_hint_or_zero > 0);
    if (force_deflate_hint)
      default_file_options |= FO_DEFLATE_HINT;
    else
      default_file_options &= ~FO_DEFLATE_HINT;
    // Turn off per-file deflate hint by force.
    suppress_file_options |= FO_DEFLATE_HINT;
  }
  if (modification_time_or_zero != 0) {
    default_file_modtime = modification_time_or_zero;
    // Turn off per-file modtime by force.
    archive_options &= ~AO_HAVE_FILE_MODTIME;
  }
  // %%% strip_compile, etc...
}

// classfile writing

void unpacker::reset_cur_classfile() {
  // set defaults
  cur_class_minver = default_class_minver;
  cur_class_majver = default_class_majver;

  // reset constant pool state
  cp.resetOutputIndexes();

  // reset fixups
  class_fixup_type.empty();
  class_fixup_offset.empty();
  class_fixup_ref.empty();
  requested_ics.empty();
}

cpindex* cpool::getKQIndex() {
  char ch = '?';
  if (u->cur_descr != null) {
    entry* type = u->cur_descr->descrType();
    ch = type->value.b.ptr[0];
  }
  byte tag = CONSTANT_Integer;
  switch (ch) {
  case 'L': tag = CONSTANT_String;   break;
  case 'I': tag = CONSTANT_Integer;  break;
  case 'J': tag = CONSTANT_Long;     break;
  case 'F': tag = CONSTANT_Float;    break;
  case 'D': tag = CONSTANT_Double;   break;
  case 'B': case 'S': case 'C':
  case 'Z': tag = CONSTANT_Integer;  break;
  default:  abort("bad KQ reference"); break;
  }
  return getIndex(tag);
}

uint unpacker::to_bci(uint bii) {
  uint  len =         bcimap.length();
  uint* map = (uint*) bcimap.base();
  assert(len > 0);  // must be initialized before using to_bci
  if (bii < len)
    return map[bii];
  // Else it's a fractional or out-of-range BCI.
  uint key = bii-len;
  for (int i = len; ; i--) {
    if (map[i-1]-(i-1) <= key)
      break;
    else
      --bii;
  }
  return bii;
}

void unpacker::put_stackmap_type() {
  int tag = code_StackMapTable_T.getByte();
  putu1(tag);
  switch (tag) {
  case 7: // (7) [RCH]
    putref(code_StackMapTable_RC.getRef());
    break;
  case 8: // (8) [PH]
    putu2(to_bci(code_StackMapTable_P.getInt()));
    break;
  }
}

// Functions for writing code.

maybe_inline
void unpacker::put_label(int curIP, int size) {
  code_fixup_type.addByte(size);
  code_fixup_offset.add((int)put_empty(size));
  code_fixup_source.add(curIP);
}

inline  // called exactly once => inline
void unpacker::write_bc_ops() {
  bcimap.empty();
  code_fixup_type.empty();
  code_fixup_offset.empty();
  code_fixup_source.empty();

  band* bc_which;

  byte*  opptr = bc_codes.curRP();
  // No need for oplimit, since the codes are pre-counted.

  size_t codeBase = wpoffset();

  bool   isAload;  // copy-out result
  int    origBC;

  entry* thisClass  = cur_class;
  entry* superClass = cur_super;
  entry* newClass   = null;  // class of last _new opcode

  // overwrite any prior index on these bands; it changes w/ current class:
  bc_thisfield.setIndex(    cp.getFieldIndex( thisClass));
  bc_thismethod.setIndex(   cp.getMethodIndex(thisClass));
  if (superClass != null) {
    bc_superfield.setIndex( cp.getFieldIndex( superClass));
    bc_supermethod.setIndex(cp.getMethodIndex(superClass));
  } else {
    NOT_PRODUCT(bc_superfield.setIndex(null));
    NOT_PRODUCT(bc_supermethod.setIndex(null));
  }

  for (int curIP = 0; ; curIP++) {
    int curPC = (int)(wpoffset() - codeBase);
    bcimap.add(curPC);
    ensure_put_space(10);  // covers most instrs w/o further bounds check
    int bc = *opptr++ & 0xFF;

    putu1_fast(bc);
    // Note:  See '--wp' below for pseudo-bytecodes like bc_end_marker.

    bool isWide = false;
    if (bc == bc_wide) {
      bc = *opptr++ & 0xFF;
      putu1_fast(bc);
      isWide = true;
    }
    switch (bc) {
    case bc_end_marker:
      --wp;  // not really part of the code
      assert(opptr <= bc_codes.maxRP());
      bc_codes.curRP() = opptr;  // advance over this in bc_codes
      goto doneScanningMethod;
    case bc_tableswitch: // apc:  (df, lo, hi, (hi-lo+1)*(label))
    case bc_lookupswitch: // apc:  (df, nc, nc*(case, label))
      {
        int caseCount = bc_case_count.getInt();
        while (((wpoffset() - codeBase) % 4) != 0)  putu1_fast(0);
        ensure_put_space(30 + caseCount*8);
        put_label(curIP, 4);  //int df = bc_label.getInt();
        if (bc == bc_tableswitch) {
          int lo = bc_case_value.getInt();
          int hi = lo + caseCount-1;
          putu4(lo);
          putu4(hi);
          for (int j = 0; j < caseCount; j++) {
            put_label(curIP, 4); //int lVal = bc_label.getInt();
            //int cVal = lo + j;
          }
        } else {
          putu4(caseCount);
          for (int j = 0; j < caseCount; j++) {
            int cVal = bc_case_value.getInt();
            putu4(cVal);
            put_label(curIP, 4); //int lVal = bc_label.getInt();
          }
        }
        assert((int)to_bci(curIP) == curPC);
        continue;
      }
    case bc_iinc:
      {
        int local = bc_local.getInt();
        int delta = (isWide ? bc_short : bc_byte).getInt();
        if (isWide) {
          putu2(local);
          putu2(delta);
        } else {
          putu1_fast(local);
          putu1_fast(delta);
        }
        continue;
      }
    case bc_sipush:
      {
        int val = bc_short.getInt();
        putu2(val);
        continue;
      }
    case bc_bipush:
    case bc_newarray:
      {
        int val = bc_byte.getByte();
        putu1_fast(val);
        continue;
      }
    case bc_ref_escape:
      {
        // Note that insnMap has one entry for this.
        --wp;  // not really part of the code
        int size = bc_escrefsize.getInt();
        entry* ref = bc_escref.getRefN();
        CHECK;
        switch (size) {
        case 1: putu1ref(ref); break;
        case 2: putref(ref);   break;
        default: assert(false);
        }
        continue;
      }
    case bc_byte_escape:
      {
        // Note that insnMap has one entry for all these bytes.
        --wp;  // not really part of the code
        int size = bc_escsize.getInt();
        ensure_put_space(size);
        for (int j = 0; j < size; j++)
          putu1_fast(bc_escbyte.getByte());
        continue;
      }
    default:
      if (is_invoke_init_op(bc)) {
        origBC = bc_invokespecial;
        entry* classRef;
        switch (bc - _invokeinit_op) {
        case _invokeinit_self_option:   classRef = thisClass;  break;
        case _invokeinit_super_option:  classRef = superClass; break;
        default: assert(bc == _invokeinit_op+_invokeinit_new_option);
        case _invokeinit_new_option:    classRef = newClass;   break;
        }
        wp[-1] = origBC;  // overwrite with origBC
        int coding = bc_initref.getInt();
        // Find the nth overloading of <init> in classRef.
        entry*   ref = null;
        cpindex* ix = (classRef == null)? null: cp.getMethodIndex(classRef);
        for (int j = 0, which_init = 0; ; j++) {
          ref = (ix == null)? null: ix->get(j);
          if (ref == null)  break;  // oops, bad input
          assert(ref->tag == CONSTANT_Methodref);
          if (ref->memberDescr()->descrName() == cp.sym[cpool::s_lt_init_gt]) {
            if (which_init++ == coding)  break;
          }
        }
        putref(ref);
        continue;
      }
      bc_which = ref_band_for_self_op(bc, isAload, origBC);
      if (bc_which != null) {
        if (!isAload) {
          wp[-1] = origBC;  // overwrite with origBC
        } else {
          wp[-1] = bc_aload_0;  // overwrite with _aload_0
          // Note: insnMap keeps the _aload_0 separate.
          bcimap.add(++curPC);
          ++curIP;
          putu1_fast(origBC);
        }
        entry* ref = bc_which->getRef();
        CHECK;
        putref(ref);
        continue;
      }
      if (is_branch_op(bc)) {
        //int lVal = bc_label.getInt();
        if (bc < bc_goto_w) {
          put_label(curIP, 2);  //putu2(lVal & 0xFFFF);
        } else {
          assert(bc <= bc_jsr_w);
          put_label(curIP, 4);  //putu4(lVal);
        }
        assert((int)to_bci(curIP) == curPC);
        continue;
      }
      bc_which = ref_band_for_op(bc);
      if (bc_which != null) {
        entry* ref = bc_which->getRefCommon(bc_which->ix, bc_which->nullOK);
        CHECK;
        if (ref == null && bc_which == &bc_classref) {
          // Shorthand for class self-references.
          ref = thisClass;
        }
        origBC = bc;
        switch (bc) {
        case bc_ildc:
        case bc_cldc:
        case bc_fldc:
        case bc_aldc:
          origBC = bc_ldc;
          break;
        case bc_ildc_w:
        case bc_cldc_w:
        case bc_fldc_w:
        case bc_aldc_w:
          origBC = bc_ldc_w;
          break;
        case bc_lldc2_w:
        case bc_dldc2_w:
          origBC = bc_ldc2_w;
          break;
        case bc_new:
          newClass = ref;
          break;
        }
        wp[-1] = origBC;  // overwrite with origBC
        if (origBC == bc_ldc) {
          putu1ref(ref);
        } else {
          putref(ref);
        }
        if (origBC == bc_multianewarray) {
          // Copy the trailing byte also.
          int val = bc_byte.getByte();
          putu1_fast(val);
        } else if (origBC == bc_invokeinterface) {
          int argSize = ref->memberDescr()->descrType()->typeSize();
          putu1_fast(1 + argSize);
          putu1_fast(0);
        }
        continue;
      }
      if (is_local_slot_op(bc)) {
        int local = bc_local.getInt();
        if (isWide) {
          putu2(local);
          if (bc == bc_iinc) {
            int iVal = bc_short.getInt();
            putu2(iVal);
          }
        } else {
          putu1_fast(local);
          if (bc == bc_iinc) {
            int iVal = bc_byte.getByte();
            putu1_fast(iVal);
          }
        }
        continue;
      }
      // Random bytecode.  Just copy it.
      assert(bc < bc_bytecode_limit);
    }
  }
 doneScanningMethod:{}
  //bcimap.add(curPC);  // PC limit is already also in map, from bc_end_marker

  // Armed with a bcimap, we can now fix up all the labels.
  for (int i = 0; i < (int)code_fixup_type.size(); i++) {
    int   type   = code_fixup_type.getByte(i);
    byte* bp     = wp_at(code_fixup_offset.get(i));
    int   curIP  = code_fixup_source.get(i);
    int   destIP = curIP + bc_label.getInt();
    int   span   = to_bci(destIP) - to_bci(curIP);
    switch (type) {
    case 2: putu2_at(bp, (ushort)span); break;
    case 4: putu4_at(bp,         span); break;
    default: assert(false);
    }
  }
}

inline  // called exactly once => inline
void unpacker::write_code() {
  int j;

  int max_stack, max_locals, handler_count, cflags;
  get_code_header(max_stack, max_locals, handler_count, cflags);

  if (max_stack < 0)      max_stack = code_max_stack.getInt();
  if (max_locals < 0)     max_locals = code_max_na_locals.getInt();
  if (handler_count < 0)  handler_count = code_handler_count.getInt();

  int siglen = cur_descr->descrType()->typeSize();
  CHECK;
  if ((cur_descr_flags & ACC_STATIC) == 0)  siglen++;
  max_locals += siglen;

  putu2(max_stack);
  putu2(max_locals);
  size_t bcbase = put_empty(4);

  // Write the bytecodes themselves.
  write_bc_ops();
  CHECK;

  byte* bcbasewp = wp_at(bcbase);
  putu4_at(bcbasewp, (int)(wp - (bcbasewp+4)));  // size of code attr

  putu2(handler_count);
  for (j = 0; j < handler_count; j++) {
    int bii = code_handler_start_P.getInt();
    putu2(to_bci(bii));
    bii    += code_handler_end_PO.getInt();
    putu2(to_bci(bii));
    bii    += code_handler_catch_PO.getInt();
    putu2(to_bci(bii));
    putref(code_handler_class_RCN.getRefN());
    CHECK;
  }

  julong indexBits = cflags;
  if (cflags < 0) {
    bool haveLongFlags = attr_defs[ATTR_CONTEXT_CODE].haveLongFlags();
    indexBits = code_flags_hi.getLong(code_flags_lo, haveLongFlags);
  }
  write_attrs(ATTR_CONTEXT_CODE, indexBits);
}

int unpacker::write_attrs(int attrc, julong indexBits) {
  CHECK_0;
  if (indexBits == 0) {
    // Quick short-circuit.
    putu2(0);
    return 0;
  }

  attr_definitions& ad = attr_defs[attrc];

  int i, j, j2, idx, count;

  int oiCount = 0;
  if (ad.isPredefined(X_ATTR_OVERFLOW)
      && (indexBits & ((julong)1<<X_ATTR_OVERFLOW)) != 0) {
    indexBits -= ((julong)1<<X_ATTR_OVERFLOW);
    oiCount = ad.xxx_attr_count().getInt();
  }

  int bitIndexes[X_ATTR_LIMIT_FLAGS_HI];
  int biCount = 0;

  // Fill bitIndexes with index bits, in order.
  for (idx = 0; indexBits != 0; idx++, indexBits >>= 1) {
    if ((indexBits & 1) != 0)
      bitIndexes[biCount++] = idx;
  }
  assert(biCount <= (int)lengthof(bitIndexes));

  // Write a provisional attribute count, perhaps to be corrected later.
  int naOffset = (int)wpoffset();
  int na0 = biCount + oiCount;
  putu2(na0);

  int na = 0;
  for (i = 0; i < na0; i++) {
    if (i < biCount)
      idx = bitIndexes[i];
    else
      idx = ad.xxx_attr_indexes().getInt();
    assert(ad.isIndex(idx));
    entry* aname = null;
    entry* ref;  // scratch
    size_t abase = put_empty(2+4);
    CHECK_0;
    if (idx < (int)ad.flag_limit && ad.isPredefined(idx)) {
      // Switch on the attrc and idx simultaneously.
      switch (ADH_BYTE(attrc, idx)) {

      case ADH_BYTE(ATTR_CONTEXT_CLASS,  X_ATTR_OVERFLOW):
      case ADH_BYTE(ATTR_CONTEXT_FIELD,  X_ATTR_OVERFLOW):
      case ADH_BYTE(ATTR_CONTEXT_METHOD, X_ATTR_OVERFLOW):
      case ADH_BYTE(ATTR_CONTEXT_CODE,   X_ATTR_OVERFLOW):
        // no attribute at all, so back up on this one
        wp = wp_at(abase);
        continue;

      case ADH_BYTE(ATTR_CONTEXT_CLASS, CLASS_ATTR_ClassFile_version):
        cur_class_minver = class_ClassFile_version_minor_H.getInt();
        cur_class_majver = class_ClassFile_version_major_H.getInt();
        // back up; not a real attribute
        wp = wp_at(abase);
        continue;

      case ADH_BYTE(ATTR_CONTEXT_CLASS, CLASS_ATTR_InnerClasses):
        // note the existence of this attr, but save for later
        if (cur_class_has_local_ics)
          abort("too many InnerClasses attrs");
        cur_class_has_local_ics = true;
        wp = wp_at(abase);
        continue;

      case ADH_BYTE(ATTR_CONTEXT_CLASS, CLASS_ATTR_SourceFile):
        aname = cp.sym[cpool::s_SourceFile];
        ref = class_SourceFile_RUN.getRefN();
        CHECK_0;
        if (ref == null) {
          bytes& n = cur_class->ref(0)->value.b;
          // parse n = (<pkg>/)*<outer>?($<id>)*
          int pkglen = lastIndexOf(SLASH_MIN,  SLASH_MAX,  n, (int)n.len)+1;
          bytes prefix = n.slice(pkglen, n.len);
          for (;;) {
            // Work backwards, finding all '$', '#', etc.
            int dollar = lastIndexOf(DOLLAR_MIN, DOLLAR_MAX, prefix, (int)prefix.len);
            if (dollar < 0)  break;
            prefix = prefix.slice(0, dollar);
          }
          const char* suffix = ".java";
          int len = (int)(prefix.len + strlen(suffix));
          bytes name; name.set(T_NEW(byte, add_size(len, 1)), len);
          name.strcat(prefix).strcat(suffix);
          ref = cp.ensureUtf8(name);
        }
        putref(ref);
        break;

      case ADH_BYTE(ATTR_CONTEXT_CLASS, CLASS_ATTR_EnclosingMethod):
        aname = cp.sym[cpool::s_EnclosingMethod];
        putref(class_EnclosingMethod_RC.getRefN());
        putref(class_EnclosingMethod_RDN.getRefN());
        break;

      case ADH_BYTE(ATTR_CONTEXT_FIELD, FIELD_ATTR_ConstantValue):
        aname = cp.sym[cpool::s_ConstantValue];
        putref(field_ConstantValue_KQ.getRefUsing(cp.getKQIndex()));
        break;

      case ADH_BYTE(ATTR_CONTEXT_METHOD, METHOD_ATTR_Code):
        aname = cp.sym[cpool::s_Code];
        write_code();
        break;

      case ADH_BYTE(ATTR_CONTEXT_METHOD, METHOD_ATTR_Exceptions):
        aname = cp.sym[cpool::s_Exceptions];
        putu2(count = method_Exceptions_N.getInt());
        for (j = 0; j < count; j++) {
          putref(method_Exceptions_RC.getRefN());
        }
        break;

      case ADH_BYTE(ATTR_CONTEXT_CODE, CODE_ATTR_StackMapTable):
        aname = cp.sym[cpool::s_StackMapTable];
        // (keep this code aligned with its brother in unpacker::read_attrs)
        putu2(count = code_StackMapTable_N.getInt());
        for (j = 0; j < count; j++) {
          int tag = code_StackMapTable_frame_T.getByte();
          putu1(tag);
          if (tag <= 127) {
            // (64-127)  [(2)]
            if (tag >= 64)  put_stackmap_type();
          } else if (tag <= 251) {
            // (247)     [(1)(2)]
            // (248-251) [(1)]
            if (tag >= 247)  putu2(code_StackMapTable_offset.getInt());
            if (tag == 247)  put_stackmap_type();
          } else if (tag <= 254) {
            // (252)     [(1)(2)]
            // (253)     [(1)(2)(2)]
            // (254)     [(1)(2)(2)(2)]
            putu2(code_StackMapTable_offset.getInt());
            for (int k = (tag - 251); k > 0; k--) {
              put_stackmap_type();
            }
          } else {
            // (255)     [(1)NH[(2)]NH[(2)]]
            putu2(code_StackMapTable_offset.getInt());
            putu2(j2 = code_StackMapTable_local_N.getInt());
            while (j2-- > 0)  put_stackmap_type();
            putu2(j2 = code_StackMapTable_stack_N.getInt());
            while (j2-- > 0)  put_stackmap_type();
          }
        }
        break;

      case ADH_BYTE(ATTR_CONTEXT_CODE, CODE_ATTR_LineNumberTable):
        aname = cp.sym[cpool::s_LineNumberTable];
        putu2(count = code_LineNumberTable_N.getInt());
        for (j = 0; j < count; j++) {
          putu2(to_bci(code_LineNumberTable_bci_P.getInt()));
          putu2(code_LineNumberTable_line.getInt());
        }
        break;

      case ADH_BYTE(ATTR_CONTEXT_CODE, CODE_ATTR_LocalVariableTable):
        aname = cp.sym[cpool::s_LocalVariableTable];
        putu2(count = code_LocalVariableTable_N.getInt());
        for (j = 0; j < count; j++) {
          int bii = code_LocalVariableTable_bci_P.getInt();
          int bci = to_bci(bii);
          putu2(bci);
          bii    += code_LocalVariableTable_span_O.getInt();
          putu2(to_bci(bii) - bci);
          putref(code_LocalVariableTable_name_RU.getRefN());
          putref(code_LocalVariableTable_type_RS.getRefN());
          putu2(code_LocalVariableTable_slot.getInt());
        }
        break;

      case ADH_BYTE(ATTR_CONTEXT_CODE, CODE_ATTR_LocalVariableTypeTable):
        aname = cp.sym[cpool::s_LocalVariableTypeTable];
        putu2(count = code_LocalVariableTypeTable_N.getInt());
        for (j = 0; j < count; j++) {
          int bii = code_LocalVariableTypeTable_bci_P.getInt();
          int bci = to_bci(bii);
          putu2(bci);
          bii    += code_LocalVariableTypeTable_span_O.getInt();
          putu2(to_bci(bii) - bci);
          putref(code_LocalVariableTypeTable_name_RU.getRefN());
          putref(code_LocalVariableTypeTable_type_RS.getRefN());
          putu2(code_LocalVariableTypeTable_slot.getInt());
        }
        break;

      case ADH_BYTE(ATTR_CONTEXT_CLASS, X_ATTR_Signature):
        aname = cp.sym[cpool::s_Signature];
        putref(class_Signature_RS.getRefN());
        break;

      case ADH_BYTE(ATTR_CONTEXT_FIELD, X_ATTR_Signature):
        aname = cp.sym[cpool::s_Signature];
        putref(field_Signature_RS.getRefN());
        break;

      case ADH_BYTE(ATTR_CONTEXT_METHOD, X_ATTR_Signature):
        aname = cp.sym[cpool::s_Signature];
        putref(method_Signature_RS.getRefN());
        break;

      case ADH_BYTE(ATTR_CONTEXT_CLASS,  X_ATTR_Deprecated):
      case ADH_BYTE(ATTR_CONTEXT_FIELD,  X_ATTR_Deprecated):
      case ADH_BYTE(ATTR_CONTEXT_METHOD, X_ATTR_Deprecated):
        aname = cp.sym[cpool::s_Deprecated];
        // no data
        break;
      }
    }

    if (aname == null) {
      // Unparse a compressor-defined attribute.
      layout_definition* lo = ad.getLayout(idx);
      if (lo == null) {
        abort("bad layout index");
        break;
      }
      assert((int)lo->idx == idx);
      aname = lo->nameEntry;
      if (aname == null) {
        bytes nameb; nameb.set(lo->name);
        aname = cp.ensureUtf8(nameb);
        // Cache the name entry for next time.
        lo->nameEntry = aname;
      }
      // Execute all the layout elements.
      band** bands = lo->bands();
      if (lo->hasCallables()) {
        band& cble = *bands[0];
        assert(cble.le_kind == EK_CBLE);
        bands = cble.le_body;
      }
      putlayout(bands);
    }

    if (aname == null)
      abort("bad attribute index");
    CHECK_0;

    byte* wp1 = wp;
    wp = wp_at(abase);

    // DTRT if this attr is on the strip-list.
    // (Note that we emptied the data out of the band first.)
    if (ad.strip_names.contains(aname)) {
      continue;
    }

    // patch the name and length
    putref(aname);
    putu4((int)(wp1 - (wp+4)));  // put the attr size
    wp = wp1;
    na++;  // count the attrs actually written
  }

  if (na != na0)
    // Refresh changed count.
    putu2_at(wp_at(naOffset), na);
  return na;
}

void unpacker::write_members(int num, int attrc) {
  CHECK;
  attr_definitions& ad = attr_defs[attrc];
  band& member_flags_hi = ad.xxx_flags_hi();
  band& member_flags_lo = ad.xxx_flags_lo();
  band& member_descr = (&member_flags_hi)[e_field_descr-e_field_flags_hi];
  assert(endsWith(member_descr.name, "_descr"));
  assert(endsWith(member_flags_lo.name, "_flags_lo"));
  assert(endsWith(member_flags_lo.name, "_flags_lo"));
  bool haveLongFlags = ad.haveLongFlags();

  putu2(num);
  julong indexMask = attr_defs[attrc].flagIndexMask();
  for (int i = 0; i < num; i++) {
    julong mflags = member_flags_hi.getLong(member_flags_lo, haveLongFlags);
    entry* mdescr = member_descr.getRef();
    cur_descr = mdescr;
    putu2(cur_descr_flags = (ushort)(mflags & ~indexMask));
    CHECK;
    putref(mdescr->descrName());
    putref(mdescr->descrType());
    write_attrs(attrc, (mflags & indexMask));
    CHECK;
  }
  cur_descr = null;
}

extern "C"
int raw_address_cmp(const void* p1p, const void* p2p) {
  void* p1 = *(void**) p1p;
  void* p2 = *(void**) p2p;
  return (p1 > p2)? 1: (p1 < p2)? -1: 0;
}

void unpacker::write_classfile_tail() {
  cur_classfile_tail.empty();
  set_output(&cur_classfile_tail);

  int i, num;

  attr_definitions& ad = attr_defs[ATTR_CONTEXT_CLASS];

  bool haveLongFlags = ad.haveLongFlags();
  julong kflags = class_flags_hi.getLong(class_flags_lo, haveLongFlags);
  julong indexMask = ad.flagIndexMask();

  cur_class = class_this.getRef();
  cur_super = class_super.getRef();

  CHECK;

  if (cur_super == cur_class)  cur_super = null;
  // special representation for java/lang/Object

  putu2((ushort)(kflags & ~indexMask));
  putref(cur_class);
  putref(cur_super);

  putu2(num = class_interface_count.getInt());
  for (i = 0; i < num; i++) {
    putref(class_interface.getRef());
  }

  write_members(class_field_count.getInt(),  ATTR_CONTEXT_FIELD);
  write_members(class_method_count.getInt(), ATTR_CONTEXT_METHOD);
  CHECK;

  cur_class_has_local_ics = false;  // may be set true by write_attrs


  int naOffset = (int)wpoffset();
  int na = write_attrs(ATTR_CONTEXT_CLASS, (kflags & indexMask));


  // at the very last, choose which inner classes (if any) pertain to k:
#ifdef ASSERT
  for (i = 0; i < ic_count; i++) {
    assert(!ics[i].requested);
  }
#endif
  // First, consult the global table and the local constant pool,
  // and decide on the globally implied inner classes.
  // (Note that we read the cpool's outputIndex fields, but we
  // do not yet write them, since the local IC attribute might
  // reverse a global decision to declare an IC.)
  assert(requested_ics.length() == 0);  // must start out empty
  // Always include all members of the current class.
  for (inner_class* child = cp.getFirstChildIC(cur_class);
       child != null;
       child = cp.getNextChildIC(child)) {
    child->requested = true;
    requested_ics.add(child);
  }
  // And, for each inner class mentioned in the constant pool,
  // include it and all its outers.
  int    noes =           cp.outputEntries.length();
  entry** oes = (entry**) cp.outputEntries.base();
  for (i = 0; i < noes; i++) {
    entry& e = *oes[i];
    if (e.tag != CONSTANT_Class)  continue;  // wrong sort
    for (inner_class* ic = cp.getIC(&e);
         ic != null;
         ic = cp.getIC(ic->outer)) {
      if (ic->requested)  break;  // already processed
      ic->requested = true;
      requested_ics.add(ic);
    }
  }
  int local_ics = requested_ics.length();
  // Second, consult a local attribute (if any) and adjust the global set.
  inner_class* extra_ics = null;
  int      num_extra_ics = 0;
  if (cur_class_has_local_ics) {
    // adjust the set of ICs by symmetric set difference w/ the locals
    num_extra_ics = class_InnerClasses_N.getInt();
    if (num_extra_ics == 0) {
      // Explicit zero count has an irregular meaning:  It deletes the attr.
      local_ics = 0;  // (short-circuit all tests of requested bits)
    } else {
      extra_ics = T_NEW(inner_class, num_extra_ics);
      // Note:  extra_ics will be freed up by next call to get_next_file().
    }
  }
  for (i = 0; i < num_extra_ics; i++) {
    inner_class& extra_ic = extra_ics[i];
    extra_ic.inner = class_InnerClasses_RC.getRef();
    CHECK;
    // Find the corresponding equivalent global IC:
    inner_class* global_ic = cp.getIC(extra_ic.inner);
    int flags = class_InnerClasses_F.getInt();
    if (flags == 0) {
      // The extra IC is simply a copy of a global IC.
      if (global_ic == null) {
        abort("bad reference to inner class");
        break;
      }
      extra_ic = (*global_ic);  // fill in rest of fields
    } else {
      flags &= ~ACC_IC_LONG_FORM;  // clear high bit if set to get clean zero
      extra_ic.flags = flags;
      extra_ic.outer = class_InnerClasses_outer_RCN.getRefN();
      extra_ic.name  = class_InnerClasses_name_RUN.getRefN();
      // Detect if this is an exact copy of the global tuple.
      if (global_ic != null) {
        if (global_ic->flags != extra_ic.flags ||
            global_ic->outer != extra_ic.outer ||
            global_ic->name  != extra_ic.name) {
          global_ic = null;  // not really the same, so break the link
        }
      }
    }
    if (global_ic != null && global_ic->requested) {
      // This local repetition reverses the globally implied request.
      global_ic->requested = false;
      extra_ic.requested = false;
      local_ics -= 1;
    } else {
      // The global either does not exist, or is not yet requested.
      extra_ic.requested = true;
      local_ics += 1;
    }
  }
  // Finally, if there are any that survived, put them into an attribute.
  // (Note that a zero-count attribute is always deleted.)
  // The putref calls below will tell the constant pool to add any
  // necessary local CP references to support the InnerClasses attribute.
  // This step must be the last round of additions to the local CP.
  if (local_ics > 0) {
    // append the new attribute:
    putref(cp.sym[cpool::s_InnerClasses]);
    putu4(2 + 2*4*local_ics);
    putu2(local_ics);
    PTRLIST_QSORT(requested_ics, raw_address_cmp);
    int num_global_ics = requested_ics.length();
    for (i = -num_global_ics; i < num_extra_ics; i++) {
      inner_class* ic;
      if (i < 0)
        ic = (inner_class*) requested_ics.get(num_global_ics+i);
      else
        ic = &extra_ics[i];
      if (ic->requested) {
        putref(ic->inner);
        putref(ic->outer);
        putref(ic->name);
        putu2(ic->flags);
        NOT_PRODUCT(local_ics--);
      }
    }
    assert(local_ics == 0);           // must balance
    putu2_at(wp_at(naOffset), ++na);  // increment class attr count
  }

  // Tidy up global 'requested' bits:
  for (i = requested_ics.length(); --i >= 0; ) {
    inner_class* ic = (inner_class*) requested_ics.get(i);
    ic->requested = false;
  }
  requested_ics.empty();

  CHECK;
  close_output();

  // rewrite CP references in the tail
  cp.computeOutputIndexes();
  int nextref = 0;
  for (i = 0; i < (int)class_fixup_type.size(); i++) {
    int    type = class_fixup_type.getByte(i);
    byte*  fixp = wp_at(class_fixup_offset.get(i));
    entry* e    = (entry*)class_fixup_ref.get(nextref++);
    int    idx  = e->getOutputIndex();
    switch (type) {
    case 1:  putu1_at(fixp, idx);  break;
    case 2:  putu2_at(fixp, idx);  break;
    default: assert(false);  // should not reach here
    }
  }
  CHECK;
}

void unpacker::write_classfile_head() {
  cur_classfile_head.empty();
  set_output(&cur_classfile_head);

  putu4(JAVA_MAGIC);
  putu2(cur_class_minver);
  putu2(cur_class_majver);
  putu2(cp.outputIndexLimit);

  int checkIndex = 1;
  int    noes =           cp.outputEntries.length();
  entry** oes = (entry**) cp.outputEntries.base();
  for (int i = 0; i < noes; i++) {
    entry& e = *oes[i];
    assert(e.getOutputIndex() == checkIndex++);
    byte tag = e.tag;
    assert(tag != CONSTANT_Signature);
    putu1(tag);
    switch (tag) {
    case CONSTANT_Utf8:
      putu2((int)e.value.b.len);
      put_bytes(e.value.b);
      break;
    case CONSTANT_Integer:
    case CONSTANT_Float:
      putu4(e.value.i);
      break;
    case CONSTANT_Long:
    case CONSTANT_Double:
      putu8(e.value.l);
      assert(checkIndex++);
      break;
    case CONSTANT_Class:
    case CONSTANT_String:
      // just write the ref
      putu2(e.refs[0]->getOutputIndex());
      break;
    case CONSTANT_Fieldref:
    case CONSTANT_Methodref:
    case CONSTANT_InterfaceMethodref:
    case CONSTANT_NameandType:
      putu2(e.refs[0]->getOutputIndex());
      putu2(e.refs[1]->getOutputIndex());
      break;
    default:
      abort(ERROR_INTERNAL);
    }
  }

#ifndef PRODUCT
  total_cp_size[0] += cp.outputIndexLimit;
  total_cp_size[1] += (int)cur_classfile_head.size();
#endif
  close_output();
}

unpacker::file* unpacker::get_next_file() {
  CHECK_0;
  free_temps();
  if (files_remaining == 0) {
    // Leave a clue that we're exhausted.
    cur_file.name = null;
    cur_file.size = null;
    if (archive_size != 0) {
      julong predicted_size = unsized_bytes_read + archive_size;
      if (predicted_size != bytes_read)
        abort("archive header had incorrect size");
    }
    return null;
  }
  files_remaining -= 1;
  assert(files_written < file_count || classes_written < class_count);
  cur_file.name = "";
  cur_file.size = 0;
  cur_file.modtime = default_file_modtime;
  cur_file.options = default_file_options;
  cur_file.data[0].set(null, 0);
  cur_file.data[1].set(null, 0);
  if (files_written < file_count) {
    entry* e = file_name.getRef();
    CHECK_0;
    cur_file.name = e->utf8String();
    bool haveLongSize = ((archive_options & AO_HAVE_FILE_SIZE_HI) != 0);
    cur_file.size = file_size_hi.getLong(file_size_lo, haveLongSize);
    if ((archive_options & AO_HAVE_FILE_MODTIME) != 0)
      cur_file.modtime += file_modtime.getInt();  //relative to archive modtime
    if ((archive_options & AO_HAVE_FILE_OPTIONS) != 0)
      cur_file.options |= file_options.getInt() & ~suppress_file_options;
  } else if (classes_written < class_count) {
    // there is a class for a missing file record
    cur_file.options |= FO_IS_CLASS_STUB;
  }
  if ((cur_file.options & FO_IS_CLASS_STUB) != 0) {
    assert(classes_written < class_count);
    classes_written += 1;
    if (cur_file.size != 0) {
      abort("class file size transmitted");
      return null;
    }
    reset_cur_classfile();

    // write the meat of the classfile:
    write_classfile_tail();
    cur_file.data[1] = cur_classfile_tail.b;
    CHECK_0;

    // write the CP of the classfile, second:
    write_classfile_head();
    cur_file.data[0] = cur_classfile_head.b;
    CHECK_0;

    cur_file.size += cur_file.data[0].len;
    cur_file.size += cur_file.data[1].len;
    if (cur_file.name[0] == '\0') {
      bytes& prefix = cur_class->ref(0)->value.b;
      const char* suffix = ".class";
      int len = (int)(prefix.len + strlen(suffix));
      bytes name; name.set(T_NEW(byte, add_size(len, 1)), len);
      cur_file.name = name.strcat(prefix).strcat(suffix).strval();
    }
  } else {
    // If there is buffered file data, produce a pointer to it.
    if (cur_file.size != (size_t) cur_file.size) {
      // Silly size specified.
      abort("resource file too large");
      return null;
    }
    size_t rpleft = input_remaining();
    if (rpleft > 0) {
      if (rpleft > cur_file.size)
        rpleft = (size_t) cur_file.size;
      cur_file.data[0].set(rp, rpleft);
      rp += rpleft;
    }
    if (rpleft < cur_file.size) {
      // Caller must read the rest.
      size_t fleft = (size_t)cur_file.size - rpleft;
      bytes_read += fleft;  // Credit it to the overall archive size.
    }
  }
  CHECK_0;
  bytes_written += cur_file.size;
  files_written += 1;
  return &cur_file;
}

// Write a file to jarout.
void unpacker::write_file_to_jar(unpacker::file* f) {
  size_t htsize = f->data[0].len + f->data[1].len;
  julong fsize = f->size;
#ifndef PRODUCT
  if (nowrite NOT_PRODUCT(|| skipfiles-- > 0)) {
    PRINTCR((2,"would write %d bytes to %s", (int) fsize, f->name));
    return;
  }
#endif
  if (htsize == fsize) {
    jarout->addJarEntry(f->name, f->deflate_hint(), f->modtime,
                        f->data[0], f->data[1]);
  } else {
    assert(input_remaining() == 0);
    bytes part1, part2;
    part1.len = f->data[0].len;
    part1.set(T_NEW(byte, part1.len), part1.len);
    part1.copyFrom(f->data[0]);
    assert(f->data[1].len == 0);
    part2.set(null, 0);
    size_t fleft = (size_t) fsize - part1.len;
    assert(bytes_read > fleft);  // part2 already credited by get_next_file
    bytes_read -= fleft;
    if (fleft > 0) {
      // Must read some more.
      if (live_input) {
        // Stop using the input buffer.  Make a new one:
        if (free_input)  input.free();
        input.init(fleft > (1<<12) ? fleft : (1<<12));
        free_input = true;
        live_input = false;
      } else {
        // Make it large enough.
        assert(free_input);  // must be reallocable
        input.ensureSize(fleft);
      }
      rplimit = rp = input.base();
      CHECK;
      input.setLimit(rp + fleft);
      if (!ensure_input(fleft))
        abort("EOF reading resource file");
      part2.ptr = input_scan();
      part2.len = input_remaining();
      rplimit = rp = input.base();
    }
    jarout->addJarEntry(f->name, f->deflate_hint(), f->modtime,
                        part1, part2);
  }
  if (verbose >= 3) {
    fprintf(errstrm, "Wrote "
                     LONG_LONG_FORMAT " bytes to: %s\n", fsize, f->name);
  }
}

// Redirect the stdio to the specified file in the unpack.log.file option
void unpacker::redirect_stdio() {
  if (log_file == null) {
    log_file = LOGFILE_STDOUT;
  }
  if (log_file == errstrm_name)
    // Nothing more to be done.
    return;
  errstrm_name = log_file;
  if (strcmp(log_file, LOGFILE_STDERR) == 0) {
    errstrm = stderr;
    return;
  } else if (strcmp(log_file, LOGFILE_STDOUT) == 0) {
    errstrm = stdout;
    return;
  } else if (log_file[0] != '\0' && (errstrm = fopen(log_file,"a+")) != NULL) {
    return;
  } else {
    char log_file_name[PATH_MAX+100];
    char tmpdir[PATH_MAX];
#ifdef WIN32
    int n = GetTempPath(PATH_MAX,tmpdir); //API returns with trailing '\'
    if (n < 1 || n > PATH_MAX) {
      sprintf(tmpdir,"C:\\");
    }
    sprintf(log_file_name, "%sunpack.log", tmpdir);
#else
    sprintf(tmpdir,"/tmp");
    sprintf(log_file_name, "/tmp/unpack.log");
#endif
    if ((errstrm = fopen(log_file_name, "a+")) != NULL) {
      log_file = errstrm_name = saveStr(log_file_name);
      return ;
    }

    char *tname = tempnam(tmpdir,"#upkg");
    sprintf(log_file_name, "%s", tname);
    if ((errstrm = fopen(log_file_name, "a+")) != NULL) {
      log_file = errstrm_name = saveStr(log_file_name);
      return ;
    }
#ifndef WIN32
    sprintf(log_file_name, "/dev/null");
    // On windows most likely it will fail.
    if ( (errstrm = fopen(log_file_name, "a+")) != NULL) {
      log_file = errstrm_name = saveStr(log_file_name);
      return ;
    }
#endif
    // Last resort
    // (Do not use stdout, since it might be jarout->jarfp.)
    errstrm = stderr;
    log_file = errstrm_name = LOGFILE_STDERR;
  }
}

#ifndef PRODUCT
int unpacker::printcr_if_verbose(int level, const char* fmt ...) {
  if (verbose < level+10)  return 0;
  va_list vl;
  va_start(vl, fmt);
  char fmtbuf[300];
  strcpy(fmtbuf+100, fmt);
  strcat(fmtbuf+100, "\n");
  char* fmt2 = fmtbuf+100;
  while (level-- > 0)  *--fmt2 = ' ';
  vfprintf(errstrm, fmt2, vl);
  return 1;  // for ?: usage
}
#endif

void unpacker::abort(const char* message) {
  if (message == null)  message = "error unpacking archive";
#ifdef UNPACK_JNI
  if (message[0] == '@') {  // secret convention for sprintf
     bytes saved;
     saved.saveFrom(message+1);
     mallocs.add(message = saved.strval());
   }
  abort_message = message;
  return;
#else
  if (message[0] == '@')  ++message;
  fprintf(errstrm, "%s\n", message);
#ifndef PRODUCT
  fflush(errstrm);
  ::abort();
#else
  exit(-1);
#endif
#endif // JNI
}

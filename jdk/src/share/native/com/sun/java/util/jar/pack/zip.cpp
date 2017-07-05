/*
 * Copyright 2001-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/**
 * Note: Lifted from uncrunch.c from jdk sources
 */
#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <time.h>

#include <stdlib.h>

#ifndef _MSC_VER
#include <strings.h>
#endif

#include "defines.h"
#include "bytes.h"
#include "utils.h"

#include "constants.h"
#include "unpack.h"

#include "zip.h"

#ifdef NO_ZLIB

inline bool jar::deflate_bytes(bytes& head, bytes& tail) {
  return false;
}
inline uint jar::get_crc32(uint c, uchar *ptr, uint len) { return 0; }
#define Z_NULL NULL

#else // Have ZLIB

#include <zlib.h>

inline uint jar::get_crc32(uint c, uchar *ptr, uint len) { return crc32(c, ptr, len); }

#endif // End of ZLIB

#ifdef sparc
#define SWAP_BYTES(a) \
    ((((a) << 8) & 0xff00) | 0x00ff) & (((a) >> 8) | 0xff00)
#else
#define SWAP_BYTES(a)  (a)
#endif

#define GET_INT_LO(a) \
    SWAP_BYTES(a & 0xFFFF)

#define GET_INT_HI(a) \
    SWAP_BYTES((a >> 16) & 0xFFFF);


void jar::init(unpacker* u_) {
  BYTES_OF(*this).clear();
  u = u_;
  u->jarout = this;
}

// Write data to the ZIP output stream.
void jar::write_data(void* buff, int len) {
  while (len > 0) {
    int rc = (int)fwrite(buff, 1, len, jarfp);
    if (rc <= 0) {
      fprintf(u->errstrm, "Error: write on output file failed err=%d\n",errno);
      exit(1); // Called only from the native standalone unpacker
    }
    output_file_offset += rc;
    buff = ((char *)buff) + rc;
    len -= rc;
  }
}

void jar::add_to_jar_directory(const char* fname, bool store, int modtime,
                               int len, int clen, uLong crc) {
  uint fname_length = (uint)strlen(fname);
  ushort header[23];
  if (modtime == 0)  modtime = default_modtime;
  uLong dostime = get_dostime(modtime);

  header[0] = (ushort)SWAP_BYTES(0x4B50);
  header[1] = (ushort)SWAP_BYTES(0x0201);
  header[2] = (ushort)SWAP_BYTES(0xA);

  // required version
  header[3] = (ushort)SWAP_BYTES(0xA);

  // flags 02 = maximum  sub-compression flag
  header[4] = ( store ) ? 0x0 : SWAP_BYTES(0x2);

  // Compression method 8=deflate.
  header[5] = ( store ) ? 0x0 : SWAP_BYTES(0x08);

  // Last modified date and time.
  header[6] = (ushort)GET_INT_LO(dostime);
  header[7] = (ushort)GET_INT_HI(dostime);

  // CRC
  header[8] = (ushort)GET_INT_LO(crc);
  header[9] = (ushort)GET_INT_HI(crc);

  // Compressed length:
  header[10] = (ushort)GET_INT_LO(clen);
  header[11] = (ushort)GET_INT_HI(clen);

  // Uncompressed length.
  header[12] = (ushort)GET_INT_LO(len);
  header[13] = (ushort)GET_INT_HI(len);

  // Filename length
  header[14] = (ushort)SWAP_BYTES(fname_length);
  // So called "extra field" length.
  header[15] = 0;
  // So called "comment" length.
  header[16] = 0;
  // Disk number start
  header[17] = 0;
  // File flags => binary
  header[18] = 0;
  // More file flags
  header[19] = 0;
  header[20] = 0;
  // Offset within ZIP file.
  header[21] = (ushort)GET_INT_LO(output_file_offset);
  header[22] = (ushort)GET_INT_HI(output_file_offset);

  // Copy the whole thing into the central directory.
  central_directory.append(header, sizeof(header));

  // Copy the fname to the header.
  central_directory.append(fname, fname_length);

  central_directory_count++;
}

void jar::write_jar_header(const char* fname, bool store, int modtime,
                           int len, int clen, uint crc) {
  uint fname_length = (uint)strlen(fname);
  ushort header[15];
  if (modtime == 0)  modtime = default_modtime;
  uLong dostime = get_dostime(modtime);

  // ZIP LOC magic.
  header[0] = (ushort)SWAP_BYTES(0x4B50);
  header[1] = (ushort)SWAP_BYTES(0x0403);

  // Version
  header[2] = (ushort)SWAP_BYTES(0xA);

  // flags 02 = maximum  sub-compression flag
  header[3] = ( store ) ? 0x0 : SWAP_BYTES(0x2);

  // Compression method = deflate
  header[4] = ( store ) ? 0x0 : SWAP_BYTES(0x08);

  // Last modified date and time.
  header[5] = (ushort)GET_INT_LO(dostime);
  header[6] = (ushort)GET_INT_HI(dostime);

  // CRC
  header[7] = (ushort)GET_INT_LO(crc);
  header[8] = (ushort)GET_INT_HI(crc);

  // Compressed length:
  header[9] = (ushort)GET_INT_LO(clen);
  header[10] = (ushort)GET_INT_HI(clen);

  // Uncompressed length.
  header[11] = (ushort)GET_INT_LO(len);
  header[12] = (ushort)GET_INT_HI(len);

  // Filename length
  header[13] = (ushort)SWAP_BYTES(fname_length);
  // So called "extra field" length.
  header[14] = 0;

  // Write the LOC header to the output file.
  write_data(header, (int)sizeof(header));

  // Copy the fname to the header.
  write_data((char*)fname, (int)fname_length);
}

static const char marker_comment[] = ZIP_ARCHIVE_MARKER_COMMENT;

void jar::write_central_directory() {
  bytes mc; mc.set(marker_comment);

  ushort header[11];

  // Create the End of Central Directory structure.
  header[0] = (ushort)SWAP_BYTES(0x4B50);
  header[1] = (ushort)SWAP_BYTES(0x0605);
  // disk numbers
  header[2] = 0;
  header[3] = 0;
  // Number of entries in central directory.
  header[4] = (ushort)SWAP_BYTES(central_directory_count);
  header[5] = (ushort)SWAP_BYTES(central_directory_count);
  // Size of the central directory}
  header[6] = (ushort)GET_INT_LO((int)central_directory.size());
  header[7] = (ushort)GET_INT_HI((int)central_directory.size());
  // Offset of central directory within disk.
  header[8] = (ushort)GET_INT_LO(output_file_offset);
  header[9] = (ushort)GET_INT_HI(output_file_offset);
  // zipfile comment length;
  header [10] = (ushort)SWAP_BYTES((int)mc.len);

  // Write the central directory.
  PRINTCR((2, "Central directory at %d\n", output_file_offset));
  write_data(central_directory.b);

  // Write the End of Central Directory structure.
  PRINTCR((2, "end-of-directory at %d\n", output_file_offset));
  write_data(header, (int)sizeof(header));

  PRINTCR((2, "writing zip comment\n"));
  // Write the comment.
  write_data(mc);
}

// Public API

// Open a Jar file and initialize.
void jar::openJarFile(const char* fname) {
  if (!jarfp) {
    PRINTCR((1, "jar::openJarFile: opening %s\n",fname));
    jarfp = fopen(fname, "wb");
    if (!jarfp) {
      fprintf(u->errstrm, "Error: Could not open jar file: %s\n",fname);
      exit(3); // Called only from the native standalone unpacker
    }
  }
}

// Add a ZIP entry and copy the file data
void jar::addJarEntry(const char* fname,
                      bool deflate_hint, int modtime,
                      bytes& head, bytes& tail) {
  int len = (int)(head.len + tail.len);
  int clen = 0;

  uint crc = get_crc32(0,Z_NULL,0);
  if (head.len != 0)
    crc = get_crc32(crc, (uchar *)head.ptr, (uint)head.len);
  if (tail.len != 0)
    crc = get_crc32(crc, (uchar *)tail.ptr, (uint)tail.len);

  bool deflate = (deflate_hint && len > 0);

  if (deflate) {
    if (deflate_bytes(head, tail) == false) {
      PRINTCR((2, "Reverting to store fn=%s\t%d -> %d\n",
              fname, len, deflated.size()));
      deflate = false;
    }
  }
  clen = (int)((deflate) ? deflated.size() : len);
  add_to_jar_directory(fname, !deflate, modtime, len, clen, crc);
  write_jar_header(    fname, !deflate, modtime, len, clen, crc);

  if (deflate) {
    write_data(deflated.b);
  } else {
    write_data(head);
    write_data(tail);
  }
}

// Add a ZIP entry for a directory name no data
void jar::addDirectoryToJarFile(const char* dir_name) {
  bool store = true;
  add_to_jar_directory((const char*)dir_name, store, default_modtime, 0, 0, 0);
  write_jar_header(    (const char*)dir_name, store, default_modtime, 0, 0, 0);
}

// Write out the central directory and close the jar file.
void jar::closeJarFile(bool central) {
  if (jarfp) {
    fflush(jarfp);
    if (central) write_central_directory();
    fflush(jarfp);
    fclose(jarfp);
    PRINTCR((2, "jar::closeJarFile:closed jar-file\n"));
  }
  reset();
}

/* Convert the date y/n/d and time h:m:s to a four byte DOS date and
 *  time (date in high two bytes, time in low two bytes allowing magnitude
 *  comparison).
 */
inline
uLong jar::dostime(int y, int n, int d, int h, int m, int s) {
  return y < 1980 ? dostime(1980, 1, 1, 0, 0, 0) :
    (((uLong)y - 1980) << 25) | ((uLong)n << 21) | ((uLong)d << 16) |
    ((uLong)h << 11) | ((uLong)m << 5) | ((uLong)s >> 1);
}

#ifdef _REENTRANT // solaris
extern "C" struct tm *gmtime_r(const time_t *, struct tm *);
#else
#define gmtime_r(t, s) gmtime(t)
#endif
/*
 * Return the Unix time in DOS format
 */
uLong jar::get_dostime(int modtime) {
  // see defines.h
  if (modtime != 0 && modtime == modtime_cache)
    return dostime_cache;
  if (modtime != 0 && default_modtime == 0)
    default_modtime = modtime;  // catch a reasonable default
  time_t t = modtime;
  struct tm sbuf;
  (void)memset((void*)&sbuf,0, sizeof(sbuf));
  struct tm* s = gmtime_r(&t, &sbuf);
  modtime_cache = modtime;
  dostime_cache = dostime(s->tm_year + 1900, s->tm_mon + 1, s->tm_mday,
                          s->tm_hour, s->tm_min, s->tm_sec);
  //printf("modtime %d => %d\n", modtime_cache, dostime_cache);
  return dostime_cache;
}



#ifndef NO_ZLIB

/* Returns true on success, and will set the clen to the compressed
   length, the caller should verify if true and clen less than the
   input data
*/
bool jar::deflate_bytes(bytes& head, bytes& tail) {
  int len = (int)(head.len + tail.len);

  z_stream zs;
  BYTES_OF(zs).clear();

  // NOTE: the window size should always be -MAX_WBITS normally -15.
  // unzip/zipup.c and java/Deflater.c

  int error = deflateInit2(&zs, Z_BEST_COMPRESSION, Z_DEFLATED,
                           -MAX_WBITS, 8, Z_DEFAULT_STRATEGY);
  if (error != Z_OK) {
    switch (error) {
    case Z_MEM_ERROR:
      PRINTCR((2, "Error: deflate error : Out of memory \n"));
      break;
    case Z_STREAM_ERROR:
      PRINTCR((2,"Error: deflate error : Invalid compression level \n"));
      break;
    case Z_VERSION_ERROR:
      PRINTCR((2,"Error: deflate error : Invalid version\n"));
      break;
    default:
      PRINTCR((2,"Error: Internal deflate error error = %d\n", error));
    }
    return false;
  }

  deflated.empty();
  zs.next_out  = (uchar*) deflated.grow(len + (len/2));
  zs.avail_out = (int)deflated.size();

  zs.next_in = (uchar*)head.ptr;
  zs.avail_in = (int)head.len;

  bytes* first = &head;
  bytes* last  = &tail;
  if (last->len == 0) {
    first = null;
    last = &head;
  } else if (first->len == 0) {
    first = null;
  }

  if (first != null && error == Z_OK) {
    zs.next_in = (uchar*) first->ptr;
    zs.avail_in = (int)first->len;
    error = deflate(&zs, Z_NO_FLUSH);
  }
  if (error == Z_OK) {
    zs.next_in = (uchar*) last->ptr;
    zs.avail_in = (int)last->len;
    error = deflate(&zs, Z_FINISH);
  }
  if (error == Z_STREAM_END) {
    if (len > (int)zs.total_out ) {
      PRINTCR((2, "deflate compressed data %d -> %d\n", len, zs.total_out));
      deflated.b.len = zs.total_out;
      deflateEnd(&zs);
      return true;
    }
    PRINTCR((2, "deflate expanded data %d -> %d\n", len, zs.total_out));
    deflateEnd(&zs);
    return false;
  }

  deflateEnd(&zs);
  PRINTCR((2, "Error: deflate error deflate did not finish error=%d\n",error));
  return false;
}

// Callback for fetching data from a GZIP input stream
static jlong read_input_via_gzip(unpacker* u,
                                  void* buf, jlong minlen, jlong maxlen) {
  assert(minlen <= maxlen);  // don't talk nonsense
  jlong numread = 0;
  char* bufptr = (char*) buf;
  char* inbuf = u->gzin->inbuf;
  size_t inbuflen = sizeof(u->gzin->inbuf);
  unpacker::read_input_fn_t read_gzin_fn =
    (unpacker::read_input_fn_t) u->gzin->read_input_fn;
  z_stream& zs = *(z_stream*) u->gzin->zstream;
  while (numread < minlen) {
    int readlen = (1 << 16);  // pretty arbitrary
    if (readlen > (maxlen - numread))
      readlen = (int)(maxlen - numread);
    zs.next_out = (uchar*) bufptr;
    zs.avail_out = readlen;
    if (zs.avail_in == 0) {
      zs.avail_in = (int) read_gzin_fn(u, inbuf, 1, inbuflen);
      zs.next_in = (uchar*) inbuf;
    }
    int error = inflate(&zs, Z_NO_FLUSH);
    if (error != Z_OK && error != Z_STREAM_END) {
      u->abort("error inflating input");
      break;
    }
    int nr = readlen - zs.avail_out;
    numread += nr;
    bufptr += nr;
    assert(numread <= maxlen);
    if (error == Z_STREAM_END) {
      enum { TRAILER_LEN = 8 };
      // skip 8-byte trailer
      if (zs.avail_in >= TRAILER_LEN) {
        zs.avail_in -= TRAILER_LEN;
      } else {
        // Bug: 5023768,we read past the TRAILER_LEN to see if there is
        // any extraneous data, as we dont support concatenated .gz
        // files just yet.
        int extra = (int) read_gzin_fn(u, inbuf, 1, inbuflen);
        zs.avail_in += extra - TRAILER_LEN;
      }
      // %%% should check final CRC and length here
      // %%% should check for concatenated *.gz files here
      if (zs.avail_in > 0)
        u->abort("garbage after end of deflated input stream");
      // pop this filter off:
      u->gzin->free();
      break;
    }
  }

  //fprintf(u->errstrm, "readInputFn(%d,%d) => %d (gunzip)\n",
  //        (int)minlen, (int)maxlen, (int)numread);
  return numread;
}

void gunzip::init(unpacker* u_) {
  BYTES_OF(*this).clear();
  u = u_;
  assert(u->gzin == null);  // once only, please
  read_input_fn = (void*)u->read_input_fn;
  zstream = NEW(z_stream, 1);
  u->gzin = this;
  u->read_input_fn = read_input_via_gzip;
}

void gunzip::start(int magic) {
  assert((magic & GZIP_MAGIC_MASK) == GZIP_MAGIC);
  int gz_flg = (magic & 0xFF);  // keep "flg", discard other 3 bytes
  enum {
    FHCRC    = (1<<1),
    FEXTRA   = (1<<2),
    FNAME    = (1<<3),
    FCOMMENT = (1<<4)
  };
  char gz_mtime[4];
  char gz_xfl[1];
  char gz_os[1];
  char gz_extra_len[2];
  char gz_hcrc[2];
  char gz_ignore;
  // do not save extra, name, comment
  read_fixed_field(gz_mtime, sizeof(gz_mtime));
  read_fixed_field(gz_xfl, sizeof(gz_xfl));
  read_fixed_field(gz_os, sizeof(gz_os));
  if (gz_flg & FEXTRA) {
    read_fixed_field(gz_extra_len, sizeof(gz_extra_len));
    int extra_len = gz_extra_len[0] & 0xFF;
    extra_len += (gz_extra_len[1] & 0xFF) << 8;
    for (; extra_len > 0; extra_len--) {
      read_fixed_field(&gz_ignore, 1);
    }
  }
  int null_terms = 0;
  if (gz_flg & FNAME)     null_terms++;
  if (gz_flg & FCOMMENT)  null_terms++;
  for (; null_terms; null_terms--) {
    for (;;) {
      gz_ignore = 0;
      read_fixed_field(&gz_ignore, 1);
      if (gz_ignore == 0)  break;
    }
  }
  if (gz_flg & FHCRC)
    read_fixed_field(gz_hcrc, sizeof(gz_hcrc));

  if (aborting())  return;

  // now the input stream is ready to read into the inflater
  int error = inflateInit2((z_stream*) zstream, -MAX_WBITS);
  if (error != Z_OK) { abort("cannot create input"); return; }
}

void gunzip::free() {
  assert(u->gzin == this);
  u->gzin = null;
  u->read_input_fn = (unpacker::read_input_fn_t) this->read_input_fn;
  inflateEnd((z_stream*) zstream);
  mtrace('f', zstream, 0);
  ::free(zstream);
  zstream = null;
  mtrace('f', this, 0);
  ::free(this);
}

void gunzip::read_fixed_field(char* buf, size_t buflen) {
  if (aborting())  return;
  jlong nr = ((unpacker::read_input_fn_t)read_input_fn)
    (u, buf, buflen, buflen);
  if ((size_t)nr != buflen)
    u->abort("short stream header");
}

#else // NO_ZLIB

void gunzip::free() {
}

#endif // NO_ZLIB

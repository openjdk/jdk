/*
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

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file and, per its terms, should not be removed:
 *
 * inftrees.h -- header to use inftrees.c
 * Copyright (C) 1995-1998 Mark Adler
 * For conditions of distribution and use, see copyright notice in zlib.h
 */

/* WARNING: this file should *not* be used by applications. It is
   part of the implementation of the compression library and is
   subject to change. Applications should only use zlib.h.
 */

/* Huffman code lookup table entry--this entry is four bytes for machines
   that have 16-bit pointers (e.g. PC's in the small or medium model). */

typedef struct inflate_huft_s FAR inflate_huft;

struct inflate_huft_s {
  union {
    struct {
      Byte Exop;        /* number of extra bits or operation */
      Byte Bits;        /* number of bits in this code or subcode */
    } what;
    uInt pad;           /* pad structure to a power of 2 (4 bytes for */
  } word;               /*  16-bit, 8 bytes for 32-bit int's) */
  uInt base;            /* literal, length base, distance base,
                           or table offset */
};

/* Maximum size of dynamic tree.  The maximum found in a long but non-
   exhaustive search was 1004 huft structures (850 for length/literals
   and 154 for distances, the latter actually the result of an
   exhaustive search).  The actual maximum is not known, but the
   value below is more than safe. */
#define MANY 1440

extern int inflate_trees_bits OF((
    uIntf *,                    /* 19 code lengths */
    uIntf *,                    /* bits tree desired/actual depth */
    inflate_huft * FAR *,       /* bits tree result */
    inflate_huft *,             /* space for trees */
    z_streamp));                /* for messages */

extern int inflate_trees_dynamic OF((
    uInt,                       /* number of literal/length codes */
    uInt,                       /* number of distance codes */
    uIntf *,                    /* that many (total) code lengths */
    uIntf *,                    /* literal desired/actual bit depth */
    uIntf *,                    /* distance desired/actual bit depth */
    inflate_huft * FAR *,       /* literal/length tree result */
    inflate_huft * FAR *,       /* distance tree result */
    inflate_huft *,             /* space for trees */
    z_streamp));                /* for messages */

extern int inflate_trees_fixed OF((
    uIntf *,                    /* literal desired/actual bit depth */
    uIntf *,                    /* distance desired/actual bit depth */
    inflate_huft * FAR *,       /* literal/length tree result */
    inflate_huft * FAR *,       /* distance tree result */
    z_streamp));                /* for memory allocation */

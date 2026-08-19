/*
 * Copyright (c) 1995, 2026, Oracle and/or its affiliates. All rights reserved.
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

/*
 * Prototypes for zip file support
 */

#ifndef _ZIP_H_
#define _ZIP_H_

#include "jni.h"

/*
 * Support for reading ZIP/JAR files. Some things worth noting:
 *
 * - Zip file entries larger than 2**32 bytes are not supported.
 * - jzentry time and crc fields are signed even though they really
 *   represent unsigned quantities.
 * - If csize is zero then the entry is uncompressed.
 * - If extra != 0 then the first two bytes are the length of the extra
 *   data in intel byte order.
 * - If pos <= 0 then it is the position of entry LOC header.
 *   If pos > 0 then it is the position of entry data.
 *   pos should not be accessed directly, but only by ZIP_GetEntryDataOffset.
 * - entry name may include embedded null character, use nlen for length
 */

typedef struct jzentry {  /* Zip file entry */
    char *name;           /* entry name */
    jlong time;           /* modification time */
    jlong size;           /* size of uncompressed data */
    jlong csize;          /* size of compressed data (zero if uncompressed) */
    jint crc;             /* crc of uncompressed data */
    char *comment;        /* optional zip file comment */
    jbyte *extra;         /* optional extra data */
    jlong pos;            /* position of LOC header or entry data */
    jint flag;            /* general purpose flag */
    jint nlen;            /* length of the entry name */
} jzentry;

/*
 * In-memory hash table cell.
 * In a typical system we have a *lot* of these, as we have one for
 * every entry in every active JAR.
 * Note that in order to save space we don't keep the name in memory,
 * but merely remember a 32 bit hash.
 */
typedef struct jzcell {
    unsigned int hash;    /* 32 bit hashcode on name */
    unsigned int next;    /* hash chain: index into jzfile->entries */
    jlong cenpos;         /* Offset of central directory file header */
} jzcell;

typedef struct cencache {
    char *data;           /* A cached page of CEN headers */
    jlong pos;            /* file offset of data */
} cencache;

/*
 * Use ZFILE to represent access to a file in a platform-indepenent
 * fashion.
 */
#ifdef WIN32
#define ZFILE jlong
#else
#define ZFILE int
#endif

/*
 * Descriptor for a ZIP file.
 */
typedef struct jzfile {   /* Zip file */
    char *name;           /* zip file name */
    jint refs;            /* number of active references */
    jlong len;            /* length (in bytes) of zip file */
#ifdef USE_MMAP
    unsigned char *maddr; /* beginning address of the CEN & ENDHDR */
    jlong mlen;           /* length (in bytes) mmapped */
    jlong offset;         /* offset of the mmapped region from the
                             start of the file. */
    jboolean usemmap;     /* if mmap is used. */
#endif
    jboolean locsig;      /* if zip file starts with LOCSIG */
    cencache cencache;    /* CEN header cache */
    ZFILE zfd;            /* open file descriptor */
    void *lock;           /* read lock */
    char *comment;        /* zip file comment */
    jint clen;            /* length of the zip file comment */
    char *msg;            /* zip error message */
    jzcell *entries;      /* array of hash cells */
    jint total;           /* total number of entries */
    jint *table;          /* Hash chain heads: indexes into entries */
    jint tablelen;        /* number of hash heads */
    struct jzfile *next;  /* next zip file in search list */
    jzentry *cache;       /* we cache the most recently freed jzentry */
    /* Information on metadata names in META-INF directory */
    char **metanames;     /* array of meta names (may have null names) */
    jint metacurrent;     /* the next empty slot in metanames array */
    jint metacount;       /* number of slots in metanames array */
    jlong lastModified;   /* last modified time */
    jlong locpos;         /* position of first LOC header (usually 0) */
} jzfile;

/*
 * Returns the ZIP entry corresponding to the given (NULL terminated)
 * entry name. Returns NULL if no entry is found by that name.
 * If the entry is found, then the value of the given sizeP will be
 * updated to the ZIP entry's size and the value of nameLenP will be
 * updated to the ZIP entry name's length.
 */
JNIEXPORT jzentry *
ZIP_FindEntry(jzfile *zip, const char *name, jint *sizeP, jint *nameLenP);

JNIEXPORT jboolean
ZIP_ReadEntry(jzfile *zip, jzentry *entry, unsigned char *buf, char *entrynm);

JNIEXPORT jzentry *
ZIP_GetNextEntry(jzfile *zip, jint n);

JNIEXPORT jzfile *
ZIP_Open(const char *name, char **pmsg);

JNIEXPORT void
ZIP_Close(jzfile *zip);

/*
 * Returns the ZIP entry corresponding to the given (NULL terminated)
 * entry name. Returns NULL if no entry is found by that name.
 */
jzentry *
ZIP_GetEntry(jzfile *zip, const char *name);
JNIEXPORT void
ZIP_FreeEntry(jzfile *zip, jzentry *ze);

JNIEXPORT jboolean
ZIP_InflateFully(void *inBuf, jlong inLen, void *outBuf, jlong outLen, char **pmsg);

#endif /* !_ZIP_H_ */

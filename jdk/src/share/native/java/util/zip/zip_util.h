/*
 * Copyright 1995-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Prototypes for zip file support
 */

#ifndef _ZIP_H_
#define _ZIP_H_

/*
 * Header signatures
 */
#define LOCSIG 0x04034b50L          /* "PK\003\004" */
#define EXTSIG 0x08074b50L          /* "PK\007\008" */
#define CENSIG 0x02014b50L          /* "PK\001\002" */
#define ENDSIG 0x06054b50L          /* "PK\005\006" */

/*
 * Header sizes including signatures
 */
#ifdef USE_MMAP
#define SIGSIZ  4
#endif
#define LOCHDR 30
#define EXTHDR 16
#define CENHDR 46
#define ENDHDR 22

/*
 * Header field access macros
 */
#define CH(b, n) (((unsigned char *)(b))[n])
#define SH(b, n) (CH(b, n) | (CH(b, n+1) << 8))
#define LG(b, n) (SH(b, n) | (SH(b, n+2) << 16))
#define GETSIG(b) LG(b, 0)

/*
 * Macros for getting local file (LOC) header fields
 */
#define LOCVER(b) SH(b, 4)          /* version needed to extract */
#define LOCFLG(b) SH(b, 6)          /* general purpose bit flags */
#define LOCHOW(b) SH(b, 8)          /* compression method */
#define LOCTIM(b) LG(b, 10)         /* modification time */
#define LOCCRC(b) LG(b, 14)         /* crc of uncompressed data */
#define LOCSIZ(b) LG(b, 18)         /* compressed data size */
#define LOCLEN(b) LG(b, 22)         /* uncompressed data size */
#define LOCNAM(b) SH(b, 26)         /* filename length */
#define LOCEXT(b) SH(b, 28)         /* extra field length */

/*
 * Macros for getting extra local (EXT) header fields
 */
#define EXTCRC(b) LG(b, 4)          /* crc of uncompressed data */
#define EXTSIZ(b) LG(b, 8)          /* compressed size */
#define EXTLEN(b) LG(b, 12)         /* uncompressed size */

/*
 * Macros for getting central directory header (CEN) fields
 */
#define CENVEM(b) SH(b, 4)          /* version made by */
#define CENVER(b) SH(b, 6)          /* version needed to extract */
#define CENFLG(b) SH(b, 8)          /* general purpose bit flags */
#define CENHOW(b) SH(b, 10)         /* compression method */
#define CENTIM(b) LG(b, 12)         /* modification time */
#define CENCRC(b) LG(b, 16)         /* crc of uncompressed data */
#define CENSIZ(b) LG(b, 20)         /* compressed size */
#define CENLEN(b) LG(b, 24)         /* uncompressed size */
#define CENNAM(b) SH(b, 28)         /* length of filename */
#define CENEXT(b) SH(b, 30)         /* length of extra field */
#define CENCOM(b) SH(b, 32)         /* file comment length */
#define CENDSK(b) SH(b, 34)         /* disk number start */
#define CENATT(b) SH(b, 36)         /* internal file attributes */
#define CENATX(b) LG(b, 38)         /* external file attributes */
#define CENOFF(b) LG(b, 42)         /* offset of local header */

/*
 * Macros for getting end of central directory header (END) fields
 */
#define ENDSUB(b) SH(b, 8)          /* number of entries on this disk */
#define ENDTOT(b) SH(b, 10)         /* total number of entries */
#define ENDSIZ(b) LG(b, 12)         /* central directory size */
#define ENDOFF(b) LG(b, 16)         /* central directory offset */
#define ENDCOM(b) SH(b, 20)         /* size of zip file comment */

/*
 * Supported compression methods
 */
#define STORED      0
#define DEFLATED    8

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
    unsigned int cenpos;  /* Offset of central directory file header */
    unsigned int next;    /* hash chain: index into jzfile->entries */
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
    jlong mlen;           /* length (in bytes) mmaped */
    jlong offset;         /* offset of the mmapped region from the
                             start of the file. */
#else
    cencache cencache;    /* CEN header cache */
#endif
    ZFILE zfd;            /* open file descriptor */
    void *lock;           /* read lock */
    char *comment;        /* zip file comment */
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
 * Index representing end of hash chain
 */
#define ZIP_ENDCHAIN ((jint)-1)

jzentry * JNICALL
ZIP_FindEntry(jzfile *zip, char *name, jint *sizeP, jint *nameLenP);

jboolean JNICALL
ZIP_ReadEntry(jzfile *zip, jzentry *entry, unsigned char *buf, char *entrynm);

jzentry * JNICALL
ZIP_GetNextEntry(jzfile *zip, jint n);

jzfile * JNICALL
ZIP_Open(const char *name, char **pmsg);

jzfile *
ZIP_Open_Generic(const char *name, char **pmsg, int mode, jlong lastModified);

jzfile *
ZIP_Get_From_Cache(const char *name, char **pmsg, jlong lastModified);

jzfile *
ZIP_Put_In_Cache(const char *name, ZFILE zfd, char **pmsg, jlong lastModified);

void JNICALL
ZIP_Close(jzfile *zip);

jzentry * ZIP_GetEntry(jzfile *zip, char *name, jint ulen);
void ZIP_Lock(jzfile *zip);
void ZIP_Unlock(jzfile *zip);
jint ZIP_Read(jzfile *zip, jzentry *entry, jlong pos, void *buf, jint len);
void ZIP_FreeEntry(jzfile *zip, jzentry *ze);
jlong ZIP_GetEntryDataOffset(jzfile *zip, jzentry *entry);

#endif /* !_ZIP_H_ */

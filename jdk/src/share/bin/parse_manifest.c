/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jli_util.h"

#include <zlib.h>
#include "manifest_info.h"

static char     *manifest;

static const char       *manifest_name = "META-INF/MANIFEST.MF";

/*
 * Inflate the manifest file (or any file for that matter).
 *
 *   fd:        File descriptor of the jar file.
 *   entry:     Contains the information necessary to perform the inflation
 *              (the compressed and uncompressed sizes and the offset in
 *              the file where the compressed data is located).
 *   size_out:  Returns the size of the inflated file.
 *
 * Upon success, it returns a pointer to a NUL-terminated malloc'd buffer
 * containing the inflated manifest file.  When the caller is done with it,
 * this buffer should be released by a call to free().  Upon failure,
 * returns NULL.
 */
static char *
inflate_file(int fd, zentry *entry, int *size_out)
{
    char        *in;
    char        *out;
    z_stream    zs;

    if (entry->csize == (size_t) -1 || entry->isize == (size_t) -1 )
        return (NULL);
    if (JLI_Lseek(fd, entry->offset, SEEK_SET) < (jlong)0)
        return (NULL);
    if ((in = malloc(entry->csize + 1)) == NULL)
        return (NULL);
    if ((size_t)(read(fd, in, (unsigned int)entry->csize)) != entry->csize) {
        free(in);
        return (NULL);
    }
    if (entry->how == STORED) {
        *(char *)((size_t)in + entry->csize) = '\0';
        if (size_out) {
            *size_out = (int)entry->csize;
        }
        return (in);
    } else if (entry->how == DEFLATED) {
        zs.zalloc = (alloc_func)Z_NULL;
        zs.zfree = (free_func)Z_NULL;
        zs.opaque = (voidpf)Z_NULL;
        zs.next_in = (Byte*)in;
        zs.avail_in = (uInt)entry->csize;
        if (inflateInit2(&zs, -MAX_WBITS) < 0) {
            free(in);
            return (NULL);
        }
        if ((out = malloc(entry->isize + 1)) == NULL) {
            free(in);
            return (NULL);
        }
        zs.next_out = (Byte*)out;
        zs.avail_out = (uInt)entry->isize;
        if (inflate(&zs, Z_PARTIAL_FLUSH) < 0) {
            free(in);
            free(out);
            return (NULL);
        }
        *(char *)((size_t)out + entry->isize) = '\0';
        free(in);
        if (inflateEnd(&zs) < 0) {
            free(out);
            return (NULL);
        }
        if (size_out) {
            *size_out = (int)entry->isize;
        }
        return (out);
    }
    free(in);
    return (NULL);
}

static jboolean zip64_present = JNI_FALSE;

/*
 * Checks to see if we have ZIP64 archive, and save
 * the check for later use
 */
static int
haveZIP64(Byte *p) {
    jlong cenlen, cenoff, centot;
    cenlen = ENDSIZ(p);
    cenoff = ENDOFF(p);
    centot = ENDTOT(p);
    zip64_present = (cenlen == ZIP64_MAGICVAL ||
                     cenoff == ZIP64_MAGICVAL ||
                     centot == ZIP64_MAGICCOUNT);
    return zip64_present;
}

static jlong
find_end64(int fd, Byte *ep, jlong pos)
{
    jlong end64pos;
    jlong bytes;
    if ((end64pos = JLI_Lseek(fd, pos - ZIP64_LOCHDR, SEEK_SET)) < (jlong)0)
        return -1;
    if ((bytes = read(fd, ep, ZIP64_LOCHDR)) < 0)
        return -1;
    if (GETSIG(ep) == ZIP64_LOCSIG)
       return end64pos;
    return -1;
}

/*
 * A very little used routine to handle the case that zip file has
 * a comment at the end. Believe it or not, the only way to find the
 * END record is to walk backwards, byte by bloody byte looking for
 * the END record signature.
 *
 *      fd:     File descriptor of the jar file.
 *      eb:     Pointer to a buffer to receive a copy of the END header.
 *
 * Returns the offset of the END record in the file on success,
 * -1 on failure.
 */
static jlong
find_end(int fd, Byte *eb)
{
    jlong   len;
    jlong   pos;
    jlong   flen;
    int     bytes;
    Byte    *cp;
    Byte    *endpos;
    Byte    *buffer;

    /*
     * 99.44% (or more) of the time, there will be no comment at the
     * end of the zip file.  Try reading just enough to read the END
     * record from the end of the file, at this time we should also
     * check to see if we have a ZIP64 archive.
     */
    if ((pos = JLI_Lseek(fd, -ENDHDR, SEEK_END)) < (jlong)0)
        return (-1);
    if ((bytes = read(fd, eb, ENDHDR)) < 0)
        return (-1);
    if (GETSIG(eb) == ENDSIG) {
        return haveZIP64(eb) ? find_end64(fd, eb, pos) : pos;
    }

    /*
     * Shucky-Darn,... There is a comment at the end of the zip file.
     *
     * Allocate and fill a buffer with enough of the zip file
     * to meet the specification for a maximal comment length.
     */
    if ((flen = JLI_Lseek(fd, 0, SEEK_END)) < (jlong)0)
        return (-1);
    len = (flen < END_MAXLEN) ? flen : END_MAXLEN;
    if (JLI_Lseek(fd, -len, SEEK_END) < (jlong)0)
        return (-1);
    if ((buffer = malloc(END_MAXLEN)) == NULL)
        return (-1);
    if ((bytes = read(fd, buffer, len)) < 0) {
        free(buffer);
        return (-1);
    }

    /*
     * Search backwards from the end of file stopping when the END header
     * signature is found. (The first condition of the "if" is just a
     * fast fail, because the GETSIG macro isn't always cheap.  The
     * final condition protects against false positives.)
     */
    endpos = &buffer[bytes];
    for (cp = &buffer[bytes - ENDHDR]; cp >= &buffer[0]; cp--)
        if ((*cp == (ENDSIG & 0xFF)) && (GETSIG(cp) == ENDSIG) &&
          (cp + ENDHDR + ENDCOM(cp) == endpos)) {
            (void) memcpy(eb, cp, ENDHDR);
            free(buffer);
            pos = flen - (endpos - cp);
            return haveZIP64(eb) ? find_end64(fd, eb, pos) : pos;
        }
    free(buffer);
    return (-1);
}

#define BUFSIZE (3 * 65536 + CENHDR + SIGSIZ)
#define MINREAD 1024

/*
 * Computes and positions at the start of the CEN header, ie. the central
 * directory, this will also return the offset if there is a zip file comment
 * at the end of the archive, for most cases this would be 0.
 */
static jlong
compute_cen(int fd, Byte *bp)
{
    int bytes;
    Byte *p;
    jlong base_offset;
    jlong offset;
    char buffer[MINREAD];
    p = (Byte*) buffer;
    /*
     * Read the END Header, which is the starting point for ZIP files.
     * (Clearly designed to make writing a zip file easier than reading
     * one. Now isn't that precious...)
     */
    if ((base_offset = find_end(fd, bp)) == -1) {
        return (-1);
    }
    p = bp;
    /*
     * There is a historical, but undocumented, ability to allow for
     * additional "stuff" to be prepended to the zip/jar file. It seems
     * that this has been used to prepend an actual java launcher
     * executable to the jar on Windows.  Although this is just another
     * form of statically linking a small piece of the JVM to the
     * application, we choose to continue to support it.  Note that no
     * guarantees have been made (or should be made) to the customer that
     * this will continue to work.
     *
     * Therefore, calculate the base offset of the zip file (within the
     * expanded file) by assuming that the central directory is followed
     * immediately by the end record.
     */
    if (zip64_present) {
        if ((offset = ZIP64_LOCOFF(p)) < (jlong)0) {
            return -1;
        }
        if (JLI_Lseek(fd, offset, SEEK_SET) < (jlong) 0) {
            return (-1);
        }
        if ((bytes = read(fd, buffer, MINREAD)) < 0) {
            return (-1);
        }
        if (GETSIG(buffer) != ZIP64_ENDSIG) {
            return -1;
        }
        if ((offset = ZIP64_ENDOFF(buffer)) < (jlong)0) {
            return -1;
        }
        if (JLI_Lseek(fd, offset, SEEK_SET) < (jlong)0) {
            return (-1);
        }
        p = (Byte*) buffer;
        base_offset = base_offset - ZIP64_ENDSIZ(p) - ZIP64_ENDOFF(p) - ZIP64_ENDHDR;
    } else {
        base_offset = base_offset - ENDSIZ(p) - ENDOFF(p);
        /*
         * The END Header indicates the start of the Central Directory
         * Headers. Remember that the desired Central Directory Header (CEN)
         * will almost always be the second one and the first one is a small
         * directory entry ("META-INF/"). Keep the code optimized for
         * that case.
         *
         * Seek to the beginning of the Central Directory.
         */
        if (JLI_Lseek(fd, base_offset + ENDOFF(p), SEEK_SET) < (jlong) 0) {
            return (-1);
        }
    }
    return base_offset;
}

/*
 * Locate the manifest file with the zip/jar file.
 *
 *      fd:     File descriptor of the jar file.
 *      entry:  To be populated with the information necessary to perform
 *              the inflation (the compressed and uncompressed sizes and
 *              the offset in the file where the compressed data is located).
 *
 * Returns zero upon success. Returns a negative value upon failure.
 *
 * The buffer for reading the Central Directory if the zip/jar file needs
 * to be large enough to accommodate the largest possible single record
 * and the signature of the next record which is:
 *
 *      3*2**16 + CENHDR + SIGSIZ
 *
 * Each of the three variable sized fields (name, comment and extension)
 * has a maximum possible size of 64k.
 *
 * Typically, only a small bit of this buffer is used with bytes shuffled
 * down to the beginning of the buffer.  It is one thing to allocate such
 * a large buffer and another thing to actually start faulting it in.
 *
 * In most cases, all that needs to be read are the first two entries in
 * a typical jar file (META-INF and META-INF/MANIFEST.MF). Keep this factoid
 * in mind when optimizing this code.
 */
static int
find_file(int fd, zentry *entry, const char *file_name)
{
    int     bytes;
    int     res;
    int     entry_size;
    int     read_size;
    jlong   base_offset;
    Byte    *p;
    Byte    *bp;
    Byte    *buffer;
    Byte    locbuf[LOCHDR];

    if ((buffer = (Byte*)malloc(BUFSIZE)) == NULL) {
        return(-1);
    }

    bp = buffer;
    base_offset = compute_cen(fd, bp);
    if (base_offset == -1) {
        free(buffer);
        return -1;
    }

    if ((bytes = read(fd, bp, MINREAD)) < 0) {
        free(buffer);
        return (-1);
    }
    p = bp;
    /*
     * Loop through the Central Directory Headers. Note that a valid zip/jar
     * must have an ENDHDR (with ENDSIG) after the Central Directory.
     */
    while (GETSIG(p) == CENSIG) {

        /*
         * If a complete header isn't in the buffer, shift the contents
         * of the buffer down and refill the buffer.  Note that the check
         * for "bytes < CENHDR" must be made before the test for the entire
         * size of the header, because if bytes is less than CENHDR, the
         * actual size of the header can't be determined. The addition of
         * SIGSIZ guarantees that the next signature is also in the buffer
         * for proper loop termination.
         */
        if (bytes < CENHDR) {
            p = memmove(bp, p, bytes);
            if ((res = read(fd, bp + bytes, MINREAD)) <= 0) {
                free(buffer);
                return (-1);
            }
            bytes += res;
        }
        entry_size = CENHDR + CENNAM(p) + CENEXT(p) + CENCOM(p);
        if (bytes < entry_size + SIGSIZ) {
            if (p != bp)
                p = memmove(bp, p, bytes);
            read_size = entry_size - bytes + SIGSIZ;
            read_size = (read_size < MINREAD) ? MINREAD : read_size;
            if ((res = read(fd, bp + bytes,  read_size)) <= 0) {
                free(buffer);
                return (-1);
            }
            bytes += res;
        }

        /*
         * Check if the name is the droid we are looking for; the jar file
         * manifest.  If so, build the entry record from the data found in
         * the header located and return success.
         */
        if ((size_t)CENNAM(p) == JLI_StrLen(file_name) &&
          memcmp((p + CENHDR), file_name, JLI_StrLen(file_name)) == 0) {
            if (JLI_Lseek(fd, base_offset + CENOFF(p), SEEK_SET) < (jlong)0) {
                free(buffer);
                return (-1);
            }
            if (read(fd, locbuf, LOCHDR) < 0) {
                free(buffer);
                return (-1);
            }
            if (GETSIG(locbuf) != LOCSIG) {
                free(buffer);
                return (-1);
            }
            entry->isize = CENLEN(p);
            entry->csize = CENSIZ(p);
            entry->offset = base_offset + CENOFF(p) + LOCHDR +
                LOCNAM(locbuf) + LOCEXT(locbuf);
            entry->how = CENHOW(p);
            free(buffer);
            return (0);
        }

        /*
         * Point to the next entry and decrement the count of valid remaining
         * bytes.
         */
        bytes -= entry_size;
        p += entry_size;
    }
    free(buffer);
    return (-1);        /* Fell off the end the loop without a Manifest */
}

/*
 * Parse a Manifest file header entry into a distinct "name" and "value".
 * Continuation lines are joined into a single "value". The documented
 * syntax for a header entry is:
 *
 *      header: name ":" value
 *
 *      name: alphanum *headerchar
 *
 *      value: SPACE *otherchar newline *continuation
 *
 *      continuation: SPACE *otherchar newline
 *
 *      newline: CR LF | LF | CR (not followed by LF)
 *
 *      alphanum: {"A"-"Z"} | {"a"-"z"} | {"0"-"9"}
 *
 *      headerchar: alphanum | "-" | "_"
 *
 *      otherchar: any UTF-8 character except NUL, CR and LF
 *
 * Note that a manifest file may be composed of multiple sections,
 * each of which may contain multiple headers.
 *
 *      section: *header +newline
 *
 *      nonempty-section: +header +newline
 *
 * (Note that the point of "nonempty-section" is unclear, because it isn't
 * referenced elsewhere in the full specification for the Manifest file.)
 *
 * Arguments:
 *      lp      pointer to a character pointer which points to the start
 *              of a valid header.
 *      name    pointer to a character pointer which will be set to point
 *              to the name portion of the header (nul terminated).
 *      value   pointer to a character pointer which will be set to point
 *              to the value portion of the header (nul terminated).
 *
 * Returns:
 *    1 Successful parsing of an NV pair.  lp is updated to point to the
 *      next character after the terminating newline in the string
 *      representing the Manifest file. name and value are updated to
 *      point to the strings parsed.
 *    0 A valid end of section indicator was encountered.  lp, name, and
 *      value are not modified.
 *   -1 lp does not point to a valid header. Upon return, the values of
 *      lp, name, and value are undefined.
 */
static int
parse_nv_pair(char **lp, char **name, char **value)
{
    char    *nl;
    char    *cp;

    /*
     * End of the section - return 0. The end of section condition is
     * indicated by either encountering a blank line or the end of the
     * Manifest "string" (EOF).
     */
    if (**lp == '\0' || **lp == '\n' || **lp == '\r')
        return (0);

    /*
     * Getting to here, indicates that *lp points to an "otherchar".
     * Turn the "header" into a string on its own.
     */
    nl = JLI_StrPBrk(*lp, "\n\r");
    if (nl == NULL) {
        nl = JLI_StrChr(*lp, (int)'\0');
    } else {
        cp = nl;                        /* For merging continuation lines */
        if (*nl == '\r' && *(nl+1) == '\n')
            *nl++ = '\0';
        *nl++ = '\0';

        /*
         * Process any "continuation" line(s), by making them part of the
         * "header" line. Yes, I know that we are "undoing" the NULs we
         * just placed here, but continuation lines are the fairly rare
         * case, so we shouldn't unnecessarily complicate the code above.
         *
         * Note that an entire continuation line is processed each iteration
         * through the outer while loop.
         */
        while (*nl == ' ') {
            nl++;                       /* First character to be moved */
            while (*nl != '\n' && *nl != '\r' && *nl != '\0')
                *cp++ = *nl++;          /* Shift string */
            if (*nl == '\0')
                return (-1);            /* Error: newline required */
            *cp = '\0';
            if (*nl == '\r' && *(nl+1) == '\n')
                *nl++ = '\0';
            *nl++ = '\0';
        }
    }

    /*
     * Separate the name from the value;
     */
    cp = JLI_StrChr(*lp, (int)':');
    if (cp == NULL)
        return (-1);
    *cp++ = '\0';               /* The colon terminates the name */
    if (*cp != ' ')
        return (-1);
    *cp++ = '\0';               /* Eat the required space */
    *name = *lp;
    *value = cp;
    *lp = nl;
    return (1);
}

/*
 * Read the manifest from the specified jar file and fill in the manifest_info
 * structure with the information found within.
 *
 * Error returns are as follows:
 *    0 Success
 *   -1 Unable to open jarfile
 *   -2 Error accessing the manifest from within the jarfile (most likely
 *      a manifest is not present, or this isn't a valid zip/jar file).
 */
int
JLI_ParseManifest(char *jarfile, manifest_info *info)
{
    int     fd;
    zentry  entry;
    char    *lp;
    char    *name;
    char    *value;
    int     rc;
    char    *splashscreen_name = NULL;

    if ((fd = open(jarfile, O_RDONLY
#ifdef O_LARGEFILE
        | O_LARGEFILE /* large file mode */
#endif
#ifdef O_BINARY
        | O_BINARY /* use binary mode on windows */
#endif
        )) == -1) {
        return (-1);
    }
    info->manifest_version = NULL;
    info->main_class = NULL;
    info->jre_version = NULL;
    info->jre_restrict_search = 0;
    info->splashscreen_image_file_name = NULL;
    if (rc = find_file(fd, &entry, manifest_name) != 0) {
        close(fd);
        return (-2);
    }
    manifest = inflate_file(fd, &entry, NULL);
    if (manifest == NULL) {
        close(fd);
        return (-2);
    }
    lp = manifest;
    while ((rc = parse_nv_pair(&lp, &name, &value)) > 0) {
        if (JLI_StrCaseCmp(name, "Manifest-Version") == 0)
            info->manifest_version = value;
        else if (JLI_StrCaseCmp(name, "Main-Class") == 0)
            info->main_class = value;
        else if (JLI_StrCaseCmp(name, "JRE-Version") == 0)
            info->jre_version = value;
        else if (JLI_StrCaseCmp(name, "JRE-Restrict-Search") == 0) {
            if (JLI_StrCaseCmp(value, "true") == 0)
                info->jre_restrict_search = 1;
        } else if (JLI_StrCaseCmp(name, "Splashscreen-Image") == 0) {
            info->splashscreen_image_file_name = value;
        }
    }
    close(fd);
    if (rc == 0)
        return (0);
    else
        return (-2);
}

/*
 * Opens the jar file and unpacks the specified file from its contents.
 * Returns NULL on failure.
 */
void *
JLI_JarUnpackFile(const char *jarfile, const char *filename, int *size) {
    int     fd;
    zentry  entry;
    void    *data = NULL;

    if ((fd = open(jarfile, O_RDONLY
#ifdef O_LARGEFILE
        | O_LARGEFILE /* large file mode */
#endif
#ifdef O_BINARY
        | O_BINARY /* use binary mode on windows */
#endif
        )) == -1) {
        return NULL;
    }
    if (find_file(fd, &entry, filename) == 0) {
        data = inflate_file(fd, &entry, size);
    }
    close(fd);
    return (data);
}

/*
 * Specialized "free" function.
 */
void
JLI_FreeManifest()
{
    if (manifest)
        free(manifest);
}

/*
 * Iterate over the manifest of the specified jar file and invoke the provided
 * closure function for each attribute encountered.
 *
 * Error returns are as follows:
 *    0 Success
 *   -1 Unable to open jarfile
 *   -2 Error accessing the manifest from within the jarfile (most likely
 *      this means a manifest is not present, or it isn't a valid zip/jar file).
 */
int
JLI_ManifestIterate(const char *jarfile, attribute_closure ac, void *user_data)
{
    int     fd;
    zentry  entry;
    char    *mp;        /* manifest pointer */
    char    *lp;        /* pointer into manifest, updated during iteration */
    char    *name;
    char    *value;
    int     rc;

    if ((fd = open(jarfile, O_RDONLY
#ifdef O_LARGEFILE
        | O_LARGEFILE /* large file mode */
#endif
#ifdef O_BINARY
        | O_BINARY /* use binary mode on windows */
#endif
        )) == -1) {
        return (-1);
    }

    if (rc = find_file(fd, &entry, manifest_name) != 0) {
        close(fd);
        return (-2);
    }

    mp = inflate_file(fd, &entry, NULL);
    if (mp == NULL) {
        close(fd);
        return (-2);
    }

    lp = mp;
    while ((rc = parse_nv_pair(&lp, &name, &value)) > 0) {
        (*ac)(name, value, user_data);
    }
    free(mp);
    close(fd);
    return (rc == 0) ? 0 : -2;
}

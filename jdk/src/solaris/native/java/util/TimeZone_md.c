/*
 * Copyright (c) 1999, 2004, Oracle and/or its affiliates. All rights reserved.
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

#include <stdlib.h>
#include <stdio.h>
#include <strings.h>
#include <time.h>
#include <limits.h>
#include <errno.h>
#include <stddef.h>

#ifdef __linux__
#include <string.h>
#include <dirent.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#endif

#include "jvm.h"

#define SKIP_SPACE(p)   while (*p == ' ' || *p == '\t') p++;

#if !defined(__solaris__) || defined(__sparcv9) || defined(amd64)
#define fileopen        fopen
#define filegets        fgets
#define fileclose       fclose
#endif

#ifdef __linux__

static const char *ETC_TIMEZONE_FILE = "/etc/timezone";
static const char *ZONEINFO_DIR = "/usr/share/zoneinfo";
static const char *DEFAULT_ZONEINFO_FILE = "/etc/localtime";

/*
 * Returns a point to the zone ID portion of the given zoneinfo file
 * name.
 */
static char *
getZoneName(char *str)
{
    static const char *zidir = "zoneinfo/";

    char * pos = strstr((const char *)str, zidir);
    if (pos == NULL) {
        return NULL;
    }
    return pos + strlen(zidir);
}

/*
 * Returns a path name created from the given 'dir' and 'name' under
 * UNIX. This function allocates memory for the pathname calling
 * malloc().
 */
static char *
getPathName(const char *dir, const char *name) {
    char *path;

    path = (char *) malloc(strlen(dir) + strlen(name) + 2);
    if (path == NULL) {
        return NULL;
    }
    return strcat(strcat(strcpy(path, dir), "/"), name);
}

/*
 * Scans the specified directory and its subdirectories to find a
 * zoneinfo file which has the same content as /etc/localtime given in
 * 'buf'. Returns a zone ID if found, otherwise, NULL is returned.
 */
static char *
findZoneinfoFile(char *buf, size_t size, const char *dir)
{
    DIR *dirp = NULL;
    struct stat statbuf;
    union {
        struct dirent d;
        char b[offsetof (struct dirent, d_name) + NAME_MAX + 1];
    } entry;
    struct dirent *dp;
    char *pathname = NULL;
    int fd = -1;
    char *dbuf = NULL;
    char *tz = NULL;

    dirp = opendir(dir);
    if (dirp == NULL) {
        return NULL;
    }

    while (readdir_r(dirp, &entry.d, &dp) == 0 && dp != NULL) {
        /*
         * Skip '.' and '..' (and possibly other .* files)
         */
        if (dp->d_name[0] == '.') {
            continue;
        }

        /*
         * Skip "ROC", "posixrules", and "localtime" since Java doesn't
         * support them.
         */
        if ((strcmp(dp->d_name, "ROC") == 0)
            || (strcmp(dp->d_name, "posixrules") == 0)
            || (strcmp(dp->d_name, "localtime") == 0)) {
            continue;
        }

        pathname = getPathName(dir, dp->d_name);
        if (pathname == NULL) {
            break;
        }
        if (stat(pathname, &statbuf) == -1) {
            break;
        }

        if (S_ISDIR(statbuf.st_mode)) {
            tz = findZoneinfoFile(buf, size, pathname);
            if (tz != NULL) {
                break;
            }
        } else if (S_ISREG(statbuf.st_mode) && (size_t)statbuf.st_size == size) {
            dbuf = (char *) malloc(size);
            if (dbuf == NULL) {
                break;
            }
            if ((fd = open(pathname, O_RDONLY)) == -1) {
                fd = 0;
                break;
            }
            if (read(fd, dbuf, size) != (ssize_t) size) {
                break;
            }
            if (memcmp(buf, dbuf, size) == 0) {
                tz = getZoneName(pathname);
                if (tz != NULL) {
                    tz = strdup(tz);
                }
                break;
            }
            free((void *) dbuf);
            dbuf = NULL;
            (void) close(fd);
            fd = 0;
        }
        free((void *) pathname);
        pathname = NULL;
    }

    if (dirp != NULL) {
        (void) closedir(dirp);
    }
    if (pathname != NULL) {
        free((void *) pathname);
    }
    if (fd != 0) {
        (void) close(fd);
    }
    if (dbuf != NULL) {
        free((void *) dbuf);
    }
    return tz;
}

/*
 * Performs libc implementation specific mapping and returns a zone ID
 * if found. Otherwise, NULL is returned.
 */
static char *
getPlatformTimeZoneID()
{
    struct stat statbuf;
    char *tz = NULL;
    FILE *fp;
    int fd;
    char *buf;
    size_t size;

    /*
     * Try reading the /etc/timezone file for Debian distros. There's
     * no spec of the file format available. This parsing assumes that
     * there's one line of an Olson tzid followed by a '\n', no
     * leading or trailing spaces, no comments.
     */
    if ((fp = fopen(ETC_TIMEZONE_FILE, "r")) != NULL) {
        char line[256];

        if (fgets(line, sizeof(line), fp) != NULL) {
            char *p = strchr(line, '\n');
            if (p != NULL) {
                *p = '\0';
            }
            if (strlen(line) > 0) {
                tz = strdup(line);
            }
        }
        (void) fclose(fp);
        if (tz != NULL) {
            return tz;
        }
    }

    /*
     * Next, try /etc/localtime to find the zone ID.
     */
    if (lstat(DEFAULT_ZONEINFO_FILE, &statbuf) == -1) {
        return NULL;
    }

    /*
     * If it's a symlink, get the link name and its zone ID part. (The
     * older versions of timeconfig created a symlink as described in
     * the Red Hat man page. It was changed in 1999 to create a copy
     * of a zoneinfo file. It's no longer possible to get the zone ID
     * from /etc/localtime.)
     */
    if (S_ISLNK(statbuf.st_mode)) {
        char linkbuf[PATH_MAX+1];
        int len;

        if ((len = readlink(DEFAULT_ZONEINFO_FILE, linkbuf, sizeof(linkbuf)-1)) == -1) {
            jio_fprintf(stderr, (const char *) "can't get a symlink of %s\n",
                        DEFAULT_ZONEINFO_FILE);
            return NULL;
        }
        linkbuf[len] = '\0';
        tz = getZoneName(linkbuf);
        if (tz != NULL) {
            tz = strdup(tz);
        }
        return tz;
    }

    /*
     * If it's a regular file, we need to find out the same zoneinfo file
     * that has been copied as /etc/localtime.
     */
    size = (size_t) statbuf.st_size;
    buf = (char *) malloc(size);
    if (buf == NULL) {
        return NULL;
    }
    if ((fd = open(DEFAULT_ZONEINFO_FILE, O_RDONLY)) == -1) {
        free((void *) buf);
        return NULL;
    }

    if (read(fd, buf, size) != (ssize_t) size) {
        (void) close(fd);
        free((void *) buf);
        return NULL;
    }
    (void) close(fd);

    tz = findZoneinfoFile(buf, size, ZONEINFO_DIR);
    free((void *) buf);
    return tz;
}
#else
#ifdef __solaris__
#if !defined(__sparcv9) && !defined(amd64)

/*
 * Those file* functions mimic the UNIX stream io functions. This is
 * because of the limitation of the number of open files on Solaris
 * (32-bit mode only) due to the System V ABI.
 */

#define BUFFER_SIZE     4096

static struct iobuffer {
    int     magic;      /* -1 to distinguish from the real FILE */
    int     fd;         /* file descriptor */
    char    *buffer;    /* pointer to buffer */
    char    *ptr;       /* current read pointer */
    char    *endptr;    /* end pointer */
};

static int
fileclose(FILE *stream)
{
    struct iobuffer *iop = (struct iobuffer *) stream;

    if (iop->magic != -1) {
        return fclose(stream);
    }

    if (iop == NULL) {
        return 0;
    }
    close(iop->fd);
    free((void *)iop->buffer);
    free((void *)iop);
    return 0;
}

static FILE *
fileopen(const char *fname, const char *fmode)
{
    FILE *fp;
    int fd;
    struct iobuffer *iop;

    if ((fp = fopen(fname, fmode)) != NULL) {
        return fp;
    }

    /*
     * It assumes read open.
     */
    if ((fd = open(fname, O_RDONLY)) == -1) {
        return NULL;
    }

    /*
     * Allocate struct iobuffer and its buffer
     */
    iop = malloc(sizeof(struct iobuffer));
    if (iop == NULL) {
        (void) close(fd);
        errno = ENOMEM;
        return NULL;
    }
    iop->magic = -1;
    iop->fd = fd;
    iop->buffer = malloc(BUFFER_SIZE);
    if (iop->buffer == NULL) {
        (void) close(fd);
        free((void *) iop);
        errno = ENOMEM;
        return NULL;
    }
    iop->ptr = iop->buffer;
    iop->endptr = iop->buffer;
    return (FILE *)iop;
}

/*
 * This implementation assumes that n is large enough and the line
 * separator is '\n'.
 */
static char *
filegets(char *s, int n, FILE *stream)
{
    struct iobuffer *iop = (struct iobuffer *) stream;
    char *p;

    if (iop->magic != -1) {
        return fgets(s, n, stream);
    }

    p = s;
    for (;;) {
        char c;

        if (iop->ptr == iop->endptr) {
            ssize_t len;

            if ((len = read(iop->fd, (void *)iop->buffer, BUFFER_SIZE)) == -1) {
                return NULL;
            }
            if (len == 0) {
                *p = 0;
                if (s == p) {
                    return NULL;
                }
                return s;
            }
            iop->ptr = iop->buffer;
            iop->endptr = iop->buffer + len;
        }
        c = *iop->ptr++;
        *p++ = c;
        if ((p - s) == (n - 1)) {
            *p = 0;
            return s;
        }
        if (c == '\n') {
            *p = 0;
            return s;
        }
    }
    /*NOTREACHED*/
}
#endif /* not __sparcv9 */

static const char *sys_init_file = "/etc/default/init";

/*
 * Performs libc implementation dependent mapping. Returns a zone ID
 * if found. Otherwise, NULL is returned.  Solaris libc looks up
 * "/etc/default/init" to get a default TZ value if TZ is not defined
 * as an environment variable.
 */
static char *
getPlatformTimeZoneID()
{
    char *tz = NULL;
    FILE *fp;

    /*
     * Try the TZ entry in /etc/default/init.
     */
    if ((fp = fileopen(sys_init_file, "r")) != NULL) {
        char line[256];
        char quote = '\0';

        while (filegets(line, sizeof(line), fp) != NULL) {
            char *p = line;
            char *s;
            char c;

            /* quick check for comment lines */
            if (*p == '#') {
                continue;
            }
            if (strncmp(p, "TZ=", 3) == 0) {
                p += 3;
                SKIP_SPACE(p);
                c = *p;
                if (c == '"' || c == '\'') {
                    quote = c;
                    p++;
                }

                /*
                 * PSARC/2001/383: quoted string support
                 */
                for (s = p; (c = *s) != '\0' && c != '\n'; s++) {
                    /* No '\\' is supported here. */
                    if (c == quote) {
                        quote = '\0';
                        break;
                    }
                    if (c == ' ' && quote == '\0') {
                        break;
                    }
                }
                if (quote != '\0') {
                    jio_fprintf(stderr, "ZoneInfo: unterminated time zone name in /etc/TIMEZONE\n");
                }
                *s = '\0';
                tz = strdup(p);
                break;
            }
        }
        (void) fileclose(fp);
    }
    return tz;
}

#endif
#endif

/*
 * findJavaTZ_md() maps platform time zone ID to Java time zone ID
 * using <java_home>/lib/tzmappings. If the TZ value is not found, it
 * trys some libc implementation dependent mappings. If it still
 * can't map to a Java time zone ID, it falls back to the GMT+/-hh:mm
 * form. `country', which can be null, is not used for UNIX platforms.
 */
/*ARGSUSED1*/
char *
findJavaTZ_md(const char *java_home_dir, const char *country)
{
    char *tz;
    char *javatz = NULL;
    char *freetz = NULL;

    tz = getenv("TZ");

#ifdef __linux__
    if (tz == NULL) {
#else
#ifdef __solaris__
    if (tz == NULL || *tz == '\0') {
#endif
#endif
        tz = getPlatformTimeZoneID();
        freetz = tz;
    }

    if (tz != NULL) {
        if (*tz == ':') {
            tz++;
        }
#ifdef __linux__
        /*
         * Ignore "posix/" prefix.
         */
        if (strncmp(tz, "posix/", 6) == 0) {
            tz += 6;
        }
#endif
        javatz = strdup(tz);
        if (freetz != NULL) {
            free((void *) freetz);
        }
    }
    return javatz;
}

/**
 * Returns a GMT-offset-based time zone ID. (e.g., "GMT-08:00")
 */
char *
getGMTOffsetID()
{
    time_t offset;
    char sign, buf[16];

    if (timezone == 0) {
        return strdup("GMT");
    }

    /* Note that the time offset direction is opposite. */
    if (timezone > 0) {
        offset = timezone;
        sign = '-';
    } else {
        offset = -timezone;
        sign = '+';
    }
    sprintf(buf, (const char *)"GMT%c%02d:%02d",
            sign, (int)(offset/3600), (int)((offset%3600)/60));
    return strdup(buf);
}

/*
 * Copyright (c) 1999, 2014, Oracle and/or its affiliates. All rights reserved.
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
#include <sys/stat.h>
#include <sys/types.h>
#include <string.h>
#include <dirent.h>
#include <unistd.h>
#ifdef __solaris__
#include <libscf.h>
#endif

#include "jvm.h"

#define SKIP_SPACE(p)   while (*p == ' ' || *p == '\t') p++;

#if !defined(__solaris__) || defined(__sparcv9) || defined(amd64)
#define fileopen        fopen
#define filegets        fgets
#define fileclose       fclose
#endif

#if defined(__linux__) || defined(_ALLBSD_SOURCE)


static const char *ETC_TIMEZONE_FILE = "/etc/timezone";
static const char *ZONEINFO_DIR = "/usr/share/zoneinfo";
static const char *DEFAULT_ZONEINFO_FILE = "/etc/localtime";
#else
#ifdef _AIX
static const char *ETC_ENVIRONMENT_FILE = "/etc/environment";
#endif
static const char *SYS_INIT_FILE = "/etc/default/init";
static const char *ZONEINFO_DIR = "/usr/share/lib/zoneinfo";
static const char *DEFAULT_ZONEINFO_FILE = "/usr/share/lib/zoneinfo/localtime";
#endif /*__linux__*/

/*
 * Returns a pointer to the zone ID portion of the given zoneinfo file
 * name, or NULL if the given string doesn't contain "zoneinfo/".
 */
static char *
getZoneName(char *str)
{
    static const char *zidir = "zoneinfo/";

    char *pos = strstr((const char *)str, zidir);
    if (pos == NULL) {
        return NULL;
    }
    return pos + strlen(zidir);
}

/*
 * Returns a path name created from the given 'dir' and 'name' under
 * UNIX. This function allocates memory for the pathname calling
 * malloc(). NULL is returned if malloc() fails.
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
 * zoneinfo file which has the same content as /etc/localtime on Linux
 * or /usr/share/lib/zoneinfo/localtime on Solaris given in 'buf'.
 * If file is symbolic link, then the contents it points to are in buf.
 * Returns a zone ID if found, otherwise, NULL is returned.
 */
static char *
findZoneinfoFile(char *buf, size_t size, const char *dir)
{
    DIR *dirp = NULL;
    struct stat statbuf;
    struct dirent *dp = NULL;
    struct dirent *entry = NULL;
    char *pathname = NULL;
    int fd = -1;
    char *dbuf = NULL;
    char *tz = NULL;

    dirp = opendir(dir);
    if (dirp == NULL) {
        return NULL;
    }

    entry = (struct dirent *) malloc((size_t) pathconf(dir, _PC_NAME_MAX));
    if (entry == NULL) {
        (void) closedir(dirp);
        return NULL;
    }

#if defined(_AIX) || defined(__linux__) || defined(MACOSX) || (defined(__solaris__) \
    && (defined(_POSIX_PTHREAD_SEMANTICS) || defined(_LP64)))
    while (readdir_r(dirp, entry, &dp) == 0 && dp != NULL) {
#else
    while ((dp = readdir_r(dirp, entry)) != NULL) {
#endif

        /*
         * Skip '.' and '..' (and possibly other .* files)
         */
        if (dp->d_name[0] == '.') {
            continue;
        }

        /*
         * Skip "ROC", "posixrules", and "localtime".
         */
        if ((strcmp(dp->d_name, "ROC") == 0)
            || (strcmp(dp->d_name, "posixrules") == 0)
#ifdef __solaris__
            /*
             * Skip the "src" and "tab" directories on Solaris.
             */
            || (strcmp(dp->d_name, "src") == 0)
            || (strcmp(dp->d_name, "tab") == 0)
#endif
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
            fd = -1;
        }
        free((void *) pathname);
        pathname = NULL;
    }

    if (entry != NULL) {
        free((void *) entry);
    }
    if (dirp != NULL) {
        (void) closedir(dirp);
    }
    if (pathname != NULL) {
        free((void *) pathname);
    }
    if (fd != -1) {
        (void) close(fd);
    }
    if (dbuf != NULL) {
        free((void *) dbuf);
    }
    return tz;
}

#if defined(__linux__) || defined(MACOSX)

/*
 * Performs Linux specific mapping and returns a zone ID
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

#ifdef __linux__
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
#endif /* __linux__ */

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
            return tz;
        }
    }

    /*
     * If it's a regular file, we need to find out the same zoneinfo file
     * that has been copied as /etc/localtime.
     * If initial symbolic link resolution failed, we should treat target
     * file as a regular file.
     */
    if ((fd = open(DEFAULT_ZONEINFO_FILE, O_RDONLY)) == -1) {
        return NULL;
    }
    if (fstat(fd, &statbuf) == -1) {
        (void) close(fd);
        return NULL;
    }
    size = (size_t) statbuf.st_size;
    buf = (char *) malloc(size);
    if (buf == NULL) {
        (void) close(fd);
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


/*
 * Performs Solaris dependent mapping. Returns a zone ID if
 * found. Otherwise, NULL is returned.  Solaris libc looks up
 * "/etc/default/init" to get the default TZ value if TZ is not defined
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
    if ((fp = fileopen(SYS_INIT_FILE, "r")) != NULL) {
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

#define TIMEZONE_FMRI   "svc:/system/timezone:default"
#define TIMEZONE_PG     "timezone"
#define LOCALTIME_PROP  "localtime"

static void
cleanupScf(scf_handle_t *h,
           scf_snapshot_t *snap,
           scf_instance_t *inst,
           scf_propertygroup_t *pg,
           scf_property_t *prop,
           scf_value_t *val,
           char *buf) {
    if (buf != NULL) {
        free(buf);
    }
    if (snap != NULL) {
        scf_snapshot_destroy(snap);
    }
    if (val != NULL) {
        scf_value_destroy(val);
    }
    if (prop != NULL) {
        scf_property_destroy(prop);
    }
    if (pg != NULL) {
        scf_pg_destroy(pg);
    }
    if (inst != NULL) {
        scf_instance_destroy(inst);
    }
    if (h != NULL) {
        scf_handle_destroy(h);
    }
}

/*
 * Retruns a zone ID of Solaris when the TZ value is "localtime".
 * First, it tries scf. If scf fails, it looks for the same file as
 * /usr/share/lib/zoneinfo/localtime under /usr/share/lib/zoneinfo/.
 */
static char *
getSolarisDefaultZoneID() {
    char *tz = NULL;
    struct stat statbuf;
    size_t size;
    char *buf;
    int fd;
    /* scf specific variables */
    scf_handle_t *h = NULL;
    scf_snapshot_t *snap = NULL;
    scf_instance_t *inst = NULL;
    scf_propertygroup_t *pg = NULL;
    scf_property_t *prop = NULL;
    scf_value_t *val = NULL;

    if ((h = scf_handle_create(SCF_VERSION)) != NULL
        && scf_handle_bind(h) == 0
        && (inst = scf_instance_create(h)) != NULL
        && (snap = scf_snapshot_create(h)) != NULL
        && (pg = scf_pg_create(h)) != NULL
        && (prop = scf_property_create(h)) != NULL
        && (val = scf_value_create(h)) != NULL
        && scf_handle_decode_fmri(h, TIMEZONE_FMRI, NULL, NULL, inst,
                                  NULL, NULL, SCF_DECODE_FMRI_REQUIRE_INSTANCE) == 0
        && scf_instance_get_snapshot(inst, "running", snap) == 0
        && scf_instance_get_pg_composed(inst, snap, TIMEZONE_PG, pg) == 0
        && scf_pg_get_property(pg, LOCALTIME_PROP, prop) == 0
        && scf_property_get_value(prop, val) == 0) {
        ssize_t len;

        /* Gets the length of the zone ID string */
        len = scf_value_get_astring(val, NULL, 0);
        if (len != -1) {
            tz = malloc(++len); /* +1 for a null byte */
            if (tz != NULL && scf_value_get_astring(val, tz, len) != -1) {
                cleanupScf(h, snap, inst, pg, prop, val, NULL);
                return tz;
            }
        }
    }
    cleanupScf(h, snap, inst, pg, prop, val, tz);

    if (stat(DEFAULT_ZONEINFO_FILE, &statbuf) == -1) {
        return NULL;
    }
    size = (size_t) statbuf.st_size;
    buf = malloc(size);
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
#endif /*__solaris__*/
#endif /*__linux__*/

#ifdef _AIX
static char *
getPlatformTimeZoneID()
{
    FILE *fp;
    char *tz = NULL;
    char *tz_key = "TZ=";
    char line[256];
    size_t tz_key_len = strlen(tz_key);

    if ((fp = fopen(ETC_ENVIRONMENT_FILE, "r")) != NULL) {
        while (fgets(line, sizeof(line), fp) != NULL) {
            char *p = strchr(line, '\n');
            if (p != NULL) {
                *p = '\0';
            }
            if (0 == strncmp(line, tz_key, tz_key_len)) {
                tz = strdup(line + tz_key_len);
                break;
            }
        }
        (void) fclose(fp);
    }

    return tz;
}
static char *mapPlatformToJavaTimezone(const char *java_home_dir, const char *tz);
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

#if defined(__linux__) || defined(_ALLBSD_SOURCE)
    if (tz == NULL) {
#else
#if defined (__solaris__) || defined(_AIX)
    if (tz == NULL || *tz == '\0') {
#endif
#endif
        tz = getPlatformTimeZoneID();
        freetz = tz;
    }

    /*
     * Remove any preceding ':'
     */
    if (tz != NULL && *tz == ':') {
        tz++;
    }

#ifdef __solaris__
    if (tz != NULL && strcmp(tz, "localtime") == 0) {
        tz = getSolarisDefaultZoneID();
        freetz = tz;
    }
#endif

    if (tz != NULL) {
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

#ifdef _AIX
        freetz = mapPlatformToJavaTimezone(java_home_dir, javatz);
        if (javatz != NULL) {
            free((void *) javatz);
        }
        javatz = freetz;
#endif
    }

    return javatz;
}

/**
 * Returns a GMT-offset-based zone ID. (e.g., "GMT-08:00")
 */

#ifdef MACOSX

char *
getGMTOffsetID()
{
    time_t offset;
    char sign, buf[32];
    struct tm *local_tm;
    time_t clock;
    time_t currenttime;

    clock = time(NULL);
    tzset();
    local_tm = localtime(&clock);
    if (local_tm->tm_gmtoff >= 0) {
        offset = (time_t) local_tm->tm_gmtoff;
        sign = "+";
    } else {
        offset = (time_t) -local_tm->tm_gmtoff;
        sign = "-";
    }
    sprintf(buf, (const char *)"GMT%c%02d:%02d",
            sign, (int)(offset/3600), (int)((offset%3600)/60));
    return strdup(buf);
}
#else

char *
getGMTOffsetID()
{
    time_t offset;
    char sign, buf[32];
#ifdef __solaris__
    struct tm localtm;
    time_t currenttime;

    currenttime = time(NULL);
    if (localtime_r(&currenttime, &localtm) == NULL) {
        return NULL;
    }

    offset = localtm.tm_isdst ? altzone : timezone;
#else
    offset = timezone;
#endif /*__linux__*/

    if (offset == 0) {
        return strdup("GMT");
    }

    /* Note that the time offset direction is opposite. */
    if (offset > 0) {
        sign = '-';
    } else {
        offset = -offset;
        sign = '+';
    }
    sprintf(buf, (const char *)"GMT%c%02d:%02d",
            sign, (int)(offset/3600), (int)((offset%3600)/60));
    return strdup(buf);
}
#endif /* MACOSX */

#ifdef _AIX
static char *
mapPlatformToJavaTimezone(const char *java_home_dir, const char *tz) {
    FILE *tzmapf;
    char mapfilename[PATH_MAX+1];
    char line[256];
    int linecount = 0;
    char temp[100], *temp_tz;
    char *javatz = NULL;
    char *str_tmp = NULL;
    size_t temp_tz_len = 0;

    /* On AIX, the TZ environment variable may end with a comma
     * followed by modifier fields. These are ignored here.
     */
    strncpy(temp, tz, 100);
    temp_tz = strtok_r(temp, ",", &str_tmp);

    if(temp_tz == NULL)
        goto tzerr;

    temp_tz_len = strlen(temp_tz);

    if (strlen(java_home_dir) >= (PATH_MAX - 15)) {
        jio_fprintf(stderr, "java.home longer than maximum path length \n");
        goto tzerr;
    }

    strncpy(mapfilename, java_home_dir, PATH_MAX);
    strcat(mapfilename, "/lib/tzmappings");

    if ((tzmapf = fopen(mapfilename, "r")) == NULL) {
        jio_fprintf(stderr, "can't open %s\n", mapfilename);
        goto tzerr;
    }

    while (fgets(line, sizeof(line), tzmapf) != NULL) {
        char *p = line;
        char *sol = line;
        char *java;
        int result;

        linecount++;
        /*
         * Skip comments and blank lines
         */
        if (*p == '#' || *p == '\n') {
            continue;
        }

        /*
         * Get the first field, platform zone ID
         */
        while (*p != '\0' && *p != '\t') {
            p++;
        }
        if (*p == '\0') {
            /* mapping table is broken! */
            jio_fprintf(stderr, "tzmappings: Illegal format at near line %d.\n", linecount);
            break;
        }

        *p++ = '\0';
        if ((result = strncmp(temp_tz, sol, temp_tz_len)) == 0) {
            /*
             * If this is the current platform zone ID,
             * take the Java time zone ID (2nd field).
             */
            java = p;
            while (*p != '\0' && *p != '\n') {
                p++;
            }

            if (*p == '\0') {
                /* mapping table is broken! */
                jio_fprintf(stderr, "tzmappings: Illegal format at line %d.\n", linecount);
                break;
            }

            *p = '\0';
            javatz = strdup(java);
            break;
        } else if (result < 0) {
            break;
        }
    }
    (void) fclose(tzmapf);

tzerr:
    if (javatz == NULL) {
        return getGMTOffsetID();
    }

    return javatz;
}
#endif


/*
 * Copyright (c) 2026, NVIDIA CORPORATION & AFFILIATES. All rights reserved.
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
 * Heuristic for the javac tool launcher: for small compilations (explicit
 * .java inputs or @argfile entries whose total size is below **2 MiB**), prefer
 * C1-only compilation via -XX:TieredStopAtLevel=1 to speed up the compilation.
 *
 * The heuristic does not add the option if JDK_JAVAC_OPTIONS is set, whatever
 * the value is, including empty.
 *
 * The added option comes before the user's -J options, so a
 * -J-XX:TieredStopAtLevel=N on the command line overrides it.
 * JAVA_TOOL_OPTIONS does not, because the VM applies it before the command
 * line.
 *
 * An argument starting with '-' is never a source. Any other argument ending
 * in .java is stat'd, including the separate value of an option. If that path
 * cannot be stat'd, the heuristic does not add TieredStopAtLevel=1.
 *
 * Some options make javac compile additional sources that are not listed, run
 * plugins, or use an AOT cache. When any of these options appears, on the
 * command line or in an @argfile, the heuristic declines, that is, does not add
 * -XX:TieredStopAtLevel=1:
 *   -sourcepath, --source-path      implicit sources from a source path
 *   --module-source-path            implicit sources from module paths
 *   -m, --module                    sources of the named modules
 *   --patch-module                  sources in patched modules
 *   -processorpath, --processor-path,
 *   --processor-module-path,
 *   -processor                      annotation processors may generate sources
 *   -proc:full, -proc:only,
 *   -Xprint, -A...                  request annotation processing, so javac
 *                                   also looks for processors on the class
 *                                   path, which may generate sources
 *   -Xplugin:...                    compiler plugins may dominate runtime
 *   -J-XX:AOTCache=...,
 *   -J-XX:AOTCacheOutput=...,
 *   -J-XX:AOTConfiguration=...,
 *   -J-XX:AOTMode=...               user-supplied AOT cache options
 *
 * Long options also match their --name=value form. Other options, including
 * -proc:none, do not decline. Only the user's arguments are scanned, not the
 * launcher's built-in ones.
 *
 * Known gap: sources that javac finds implicitly on the class path are not
 * counted. Without -sourcepath, javac also searches the class path for the
 * sources of referenced classes.
 */

#include <stdio.h>
#include <stdlib.h>
#include <sys/stat.h>
#include "java.h"
#include "jli_util.h"

#define JAVAC_TIERED_STOP_BYTES_THRESHOLD (2LL * 1024 * 1024) /* 2 MiB */

typedef struct {
    int count;       /* Number of .java arguments successfully stat'd. */
    long long bytes; /* Total size of the .java source files, in bytes. */
} SourceTotal;

/* True when JDK_JAVAC_OPTIONS is present, including an empty value. */
static jboolean
is_jdk_javac_options_set(void) {
    return getenv("JDK_JAVAC_OPTIONS") != NULL;
}

/*
 * Return true if arg is a javac option named option.
 * A long option (--name) also matches in the --name=value form. A single-dash
 * option (-name) must match exactly, because javac does not accept -name=value.
 */
static jboolean
is_javac_option(const char *arg, const char *option) {
    if (JLI_StrCmp(arg, option) == 0) {
        return JNI_TRUE;
    }
    size_t n = JLI_StrLen(option);
    return option[0] == '-' && option[1] == '-' &&
           JLI_StrNCmp(arg, option, n) == 0 && arg[n] == '=';
}

/*
 * Return true if arg is a decline option: an option that may make javac
 * compile sources not listed in the argument list, run compiler plugins, or
 * use an AOT cache.
 */
static jboolean
is_decline_option(const char *arg) {
    return is_javac_option(arg, "-sourcepath") ||
           is_javac_option(arg, "--source-path") ||
           is_javac_option(arg, "--module-source-path") ||
           is_javac_option(arg, "-m") ||
           is_javac_option(arg, "--module") ||
           is_javac_option(arg, "--patch-module") ||
           is_javac_option(arg, "-processor") ||
           is_javac_option(arg, "-processorpath") ||
           is_javac_option(arg, "--processor-path") ||
           is_javac_option(arg, "--processor-module-path") ||
           is_javac_option(arg, "-proc:full") ||
           is_javac_option(arg, "-proc:only") ||
           is_javac_option(arg, "-Xprint") ||
           JLI_StrNCmp(arg, "-A", 2) == 0 ||
           JLI_StrNCmp(arg, "-Xplugin:", 9) == 0 ||
           JLI_StrNCmp(arg, "-J-XX:AOTCache=", 15) == 0 ||
           JLI_StrNCmp(arg, "-J-XX:AOTCacheOutput=", 21) == 0 ||
           JLI_StrNCmp(arg, "-J-XX:AOTConfiguration=", 23) == 0 ||
           JLI_StrNCmp(arg, "-J-XX:AOTMode=", 14) == 0;
}

/* '@file', but not the '@@' escape that passes a literal @ argument. */
static jboolean
is_argfile(const char *arg) {
    return arg != NULL && arg[0] == '@' && arg[1] != '\0' && arg[1] != '@';
}

/*
 * Classify one javac argument.
 *   2  @argfile
 *   1  explicit .java source
 *   0  not a source
 *  -1  decline option
 */
static int
classify_javac_arg(const char *arg) {
    if (is_decline_option(arg)) {
        return -1;
    }
    if (is_argfile(arg)) {
        return 2;
    }
    /* javac treats every argument starting with '-' as an option. */
    if (arg[0] == '-') {
        return 0;
    }
    if (JLI_HasSuffix(arg, ".java")) {
        return 1;
    }
    return 0;
}

/* Same argument separators as javac's @argfile parser. */
static int
is_argfile_space(int c) {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f';
}

/*
 * Read one whitespace-delimited argument from an @argfile.
 *
 * Returns:
 *   1 and a NUL-terminated argument;
 *   0 on clean EOF;
 *   -1 on I/O error or an argument that does not fit in buf.
 *
 * Single or double quotes are removed.
 * For example, "My Source.java" becomes My Source.java.
 *
 * Inside quotes, backslash escapes are decoded like javac:
 *   \n, \r, \t, and \f become newline, carriage return, tab, and form feed;
 *
 *   Backslash followed by a physical newline continues the argument on the
 *   next non-whitespace character;
 *
 *   Before any other character, the backslash is dropped; for example,
 *   "My\ Source.java" becomes My Source.java, and "dir\\File.java" becomes
 *   dir\File.java.
 *
 * A '#' at the start of an argument begins a comment through the end of the
 * line; for example, # generated sources is skipped. A '#' after other
 * characters is part of the argument, so A.java#1 remains A.java#1.
 */
static int
read_one_argument(FILE *fp, char *buf, size_t buf_size) {
    size_t len = 0;           /* Number of characters stored in buf. */
    int argument_started = 0; /* The current argument has started. */
    int in_quote = 0;         /* Reading inside a quoted section. */
    char quote = 0;           /* The active quote character, ' or ". */
    int c;                    /* Current input character. */

    while ((c = fgetc(fp)) != EOF) {
        if (in_quote) {
            if (c == quote) {
                /* End of quoted section. */
                in_quote = 0;
                continue;
            }
            /* Backslash escape. */
            if (c == '\\') {
                c = fgetc(fp);
                if (c == '\n' || c == '\r') {
                    /*
                     * Backslash-newline continues the argument at the next
                     * non-whitespace character. For example,
                     * "A<backslash><newline>  B.java" becomes AB.java.
                     */
                    do {
                        c = fgetc(fp);
                    } while (is_argfile_space(c));
                    if (c != EOF) {
                        ungetc(c, fp);
                    }
                    continue;
                }
                if (c == EOF) {
                    break;
                }
                /*
                 * Decode the escapes recognized by javac. For example, \t
                 * becomes a tab. For any other character, the backslash is
                 * dropped and the character is kept: \\ becomes one
                 * backslash, and \q becomes q.
                 */
                switch (c) {
                    case 'n': c = '\n'; break;
                    case 'r': c = '\r'; break;
                    case 't': c = '\t'; break;
                    case 'f': c = '\f'; break;
                    default: break;
                }
            }
            if (len + 1 < buf_size) {
                buf[len++] = (char)c;
            } else {
                return -1;
            }
            continue;
        }

        if (!argument_started && c == '#') {
            do {
                c = fgetc(fp);
            } while (c != '\n' && c != '\r' && c != EOF);
            continue;
        }

        if (c == '\'' || c == '"') {
            in_quote = 1;
            quote = (char)c;
            argument_started = 1;
            continue;
        }

        if (is_argfile_space(c)) {
            if (argument_started) {
                buf[len] = '\0';
                return 1;
            }
            continue;
        }

        argument_started = 1;
        if (len + 1 < buf_size) {
            buf[len++] = (char)c;
        } else {
            return -1;
        }
    }

    if (ferror(fp)) {
        return -1;
    }
    if (argument_started) {
        buf[len] = '\0';
        return 1;
    }
    return 0;
}

/*
 * Stat path and add its size to total. Returns 0 on success, or -1 if the file
 * cannot be stat'd, has a negative size, or the total reaches the byte limit.
 */
static int
count_java_source(const char *path, SourceTotal *total, long long bytes_limit) {
    struct stat st;
    if (stat(path, &st) != 0 || st.st_size < 0) {
        return -1;
    }
    long long size = (long long)st.st_size;
    if (size >= bytes_limit - total->bytes) {
        return -1;
    }
    total->count++;
    total->bytes += size;
    return 0;
}

/*
 * Sum the sizes of the .java arguments in an @argfile into total.
 * Returns 0 on success, or -1 on an I/O error, a decline option, a .java
 * argument that cannot be stat'd, or when the total reaches the byte limit.
 */
static int
count_argfile(const char *path, SourceTotal *total, long long bytes_limit) {
    FILE *fp = fopen(path, "rb");
    if (fp == NULL) {
        return -1;
    }

    char arg[4096];
    int rc = 0;
    int n;
    while ((n = read_one_argument(fp, arg, sizeof(arg))) > 0) {
        int kind = classify_javac_arg(arg);
        if (kind < 0) {
            rc = -1;
            break;
        }
        if (kind == 1 && count_java_source(arg, total, bytes_limit) != 0) {
            rc = -1;
            break;
        }
    }
    if (n < 0) {
        rc = -1;
    }

    fclose(fp);
    return rc;
}

/*
 * Sum the sizes of the explicit .java sources in argv, including those listed
 * in @argfiles, and store the result in total. Returns 0 on success, or -1 if
 * the sources cannot be summed reliably (for example, a decline option or a
 * file that cannot be stat'd) or the total reaches the byte limit. In both
 * cases the heuristic does not add the option.
 */
static int
sum_java_sources(int argc, char **argv, long long bytes_limit,
                 SourceTotal *total) {
    total->count = 0;
    total->bytes = 0;

    for (int i = 0; i < argc; i++) {
        const char *arg = argv[i];
        if (arg == NULL) {
            continue;
        }

        int kind = classify_javac_arg(arg);
        /* Decline option */
        if (kind < 0) {
            return -1;
        }

        /* @argfile */
        if (kind == 2) {
            if (count_argfile(arg + 1, total, bytes_limit) != 0) {
                return -1;
            }
            continue;
        }

        /* .java source */
        if (kind == 1 && count_java_source(arg, total, bytes_limit) != 0) {
            return -1;
        }
    }

    return 0;
}

/*
 * For the javac launcher, add -XX:TieredStopAtLevel=1 when the explicit .java
 * sources in argv (including those in @argfiles) total less than
 * JAVAC_TIERED_STOP_BYTES_THRESHOLD. Does nothing for other launchers, when
 * JDK_JAVAC_OPTIONS is set, when there are no .java sources, or when the
 * sources cannot be summed reliably.
 *
 * Called before TranslateApplicationArgs merges in the launcher's built-in
 * arguments and before ParseArguments, so that a later user-provided
 * -J-XX:TieredStopAtLevel=... still wins.
 */
void
JLI_ApplyJavacTieredStopHeuristic(const char *progname,
                                  int argc, char **argv) {
    if (progname == NULL || JLI_StrCmp(progname, "javac") != 0) {
        return;
    }

    if (is_jdk_javac_options_set()) {
        JLI_TraceLauncher("javac heuristic: JDK_JAVAC_OPTIONS set, not adding\n");
        return;
    }

    const long long bytes_limit = JAVAC_TIERED_STOP_BYTES_THRESHOLD;
    SourceTotal total;
    int rc = sum_java_sources(argc, argv, bytes_limit, &total);
    JLI_TraceLauncher("javac heuristic: sources=%d bytes=%lld bytes_limit=%lld rc=%d\n",
                      total.count, total.bytes, bytes_limit, rc);

    if (rc == 0 && total.count > 0) {
        AddOption("-XX:TieredStopAtLevel=1", NULL);
        JLI_TraceLauncher("javac heuristic: added -XX:TieredStopAtLevel=1\n");
    }
}

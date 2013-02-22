/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * Converts a single string command line to the traditional argc, argv.
 * There are rules which govern the breaking of the the arguments, and
 * these rules are embodied in the regression tests below, and duplicated
 * in the jdk regression tests.
 */

#ifndef IDE_STANDALONE
#include "java.h"
#include "jli_util.h"
#else /* IDE_STANDALONE */
// The defines we need for stand alone testing
#include <stdio.h>
#include <stdlib.h>
#include <Windows.h>
#define JNI_TRUE       TRUE
#define JNI_FALSE      FALSE
#define JLI_MemRealloc realloc
#define JLI_StringDup  _strdup
#define JLI_MemFree    free
#define jboolean       boolean
typedef struct  {
    char* arg;
    boolean has_wildcard;
} StdArg ;
#endif
static StdArg *stdargs;
static int    stdargc;

static char* next_arg(char* cmdline, char* arg, jboolean* wildcard) {

    char* src = cmdline;
    char* dest = arg;
    jboolean separator = JNI_FALSE;
    int quotes = 0;
    int slashes = 0;

    char prev = 0;
    char ch = 0;
    int i;
    jboolean done = JNI_FALSE;

    *wildcard = JNI_FALSE;
    while ((ch = *src) != 0 && !done) {
        switch (ch) {
        case '"':
            if (separator) {
                done = JNI_TRUE;
                break;
            }
            if (prev == '\\') {
                for (i = 1; i < slashes; i += 2) {
                    *dest++ = prev;
                }
                if (slashes % 2 == 1) {
                    *dest++ = ch;
                } else {
                    quotes++;
                }
            } else if (prev == '"' && quotes % 2 == 0) {
                quotes++;
                *dest++ = ch; // emit every other consecutive quote
            } else if (quotes == 0) {
                quotes++; // starting quote
            } else {
                quotes--; // matching quote
            }
            slashes = 0;
            break;

        case '\\':
            slashes++;
            if (separator) {
                done = JNI_TRUE;
                separator = JNI_FALSE;
            }
            break;

        case ' ':
        case '\t':
            if (prev == '\\') {
                for (i = 0 ; i < slashes; i++) {
                   *dest++ = prev;
                }
            }
            if (quotes % 2 == 1) {
                *dest++ = ch;
            } else {
                separator = JNI_TRUE;
            }
            slashes = 0;
            break;

        case '*':
        case '?':
            if (separator) {
                done = JNI_TRUE;
                separator = JNI_FALSE;
                break;
            }
            if (quotes % 2 == 0) {
                *wildcard = JNI_TRUE;
            }
            if (prev == '\\') {
                *dest++ = prev;
            }
            *dest++ = ch;
            break;

        default:
            if (prev == '\\') {
                for (i = 0 ; i < slashes ; i++) {
                   *dest++ = prev;
                }
                *dest++ = ch;
            } else if (separator) {
                done = JNI_TRUE;
            } else {
                *dest++ = ch;
            }
            slashes = 0;
        }

        if (!done) {
            prev = ch;
            src++;
        }
    }
    if (prev == '\\') {
        for (i = 0; i < slashes; i++) {
            *dest++ = prev;
        }
    }
    *dest = 0;
    return done ? src : NULL;
}

int JLI_GetStdArgc() {
    return stdargc;
}

StdArg* JLI_GetStdArgs() {
    return stdargs;
}

void JLI_CmdToArgs(char* cmdline) {
    int nargs = 0;
    StdArg* argv = NULL;
    jboolean wildcard = JNI_FALSE;
    char* src = cmdline;

    // allocate arg buffer with sufficient space to receive the largest arg
    char* arg = JLI_StringDup(cmdline);

    do {
        src = next_arg(src, arg, &wildcard);
        // resize to accommodate another Arg
        argv = (StdArg*) JLI_MemRealloc(argv, (nargs+1) * sizeof(StdArg));
        argv[nargs].arg = JLI_StringDup(arg);
        argv[nargs].has_wildcard = wildcard;

        nargs++;
    } while (src != NULL);

    stdargc = nargs;
    stdargs = argv;
}

#ifdef IDE_STANDALONE
void doexit(int rv) {
    printf("Hit any key to quit\n");
    int c = getchar();
    exit(rv);
}

void doabort() {
    doexit(1);
}

class Vector {
public:
    char* cmdline;
    int argc;
    char* argv[10];
    boolean wildcard[10];
    boolean enabled;

    Vector(){}
    // Initialize our test vector with the program name, argv[0]
    // and the single string command line.
    Vector(char* pname, char* cline) {
        argv[0] = pname;
        wildcard[0] = FALSE;
        cmdline = cline;
        argc = 1;
        enabled = TRUE;
    }

    // add our expected strings, the program name has already been
    // added so ignore that
    void add(char* arg, boolean w) {
        argv[argc] = arg;
        wildcard[argc] = w;
        argc++;
    }

    void disable() {
        enabled = FALSE;
    }

    // validate the returned arguments with the expected arguments, using the
    // new CmdToArgs method.
    bool check() {
        // "pgmname" rest of cmdline ie. pgmname + 2 double quotes + space + cmdline from windows
        char* cptr = (char*) malloc(strlen(argv[0]) + sizeof(char) * 3 + strlen(cmdline) + 1);
        _snprintf(cptr, MAX_PATH, "\"%s\" %s", argv[0], cmdline);
        JLI_CmdToArgs(cptr);
        free(cptr);
        StdArg *kargv = JLI_GetStdArgs();
        int     kargc = JLI_GetStdArgc();
        bool retval = true;
        printf("\n===========================\n");
        printf("cmdline=%s\n", cmdline);
        if (argc != kargc) {
            printf("*** argument count does not match\n");
            printme();
            printtest(kargc, kargv);
            doabort();
        }
        for (int i = 0 ; i < argc && retval == true ; i++) {
            if (strcmp(argv[i], kargv[i].arg) != 0) {
                printf("*** argument at [%d] don't match\n  got: %s\n  exp: %s\n",
                       i, kargv[i].arg, argv[i]);
                doabort();
            }
        }
        for (int i = 0 ; i < argc && retval == true ; i++) {
            if (wildcard[i] != kargv[i].has_wildcard) {
                printf("*** expansion flag at [%d] doesn't match\n  got: %d\n  exp: %d\n",
                       i, kargv[i].has_wildcard, wildcard[i]);
                doabort();
            }
        }
        for (int i = 0 ; i < kargc ; i++) {
            printf("k[%d]=%s\n", i, kargv[i].arg);
            printf(" [%d]=%s\n", i, argv[i]);
        }
        return retval;
    }
    void printtest(int kargc, StdArg* kargv) {
        for (int i = 0 ; i < kargc ; i++) {
            printf("k[%d]=%s\n", i, kargv[i].arg);
        }
    }
    void printme() {
        for (int i = 0 ; i < argc ; i++) {
            printf(" [%d]=%s\n", i, argv[i]);
        }
    }
};

void dotest(Vector** vectors) {
    Vector* v = vectors[0];
    for (int i = 0 ; v != NULL;) {
        if (v->enabled) {
            v->check();
        }
        v = vectors[++i];
    }
}

#define MAXV 128
int main(int argc, char* argv[]) {

    int n;
    for (n=1; n < argc; n++) {
        printf("%d %s\n", n, argv[n]);
    }
    if (n > 1) {
        JLI_CmdToArgs(GetCommandLine());
        for (n = 0; n < stdargc; n++) {
            printf(" [%d]=%s\n", n, stdargs[n].arg);
            printf(" [%d]=%s\n", n, stdargs[n].has_wildcard ? "TRUE" : "FALSE");
        }
        doexit(0);
    }

    Vector *vectors[MAXV];

    memset(vectors, 0, sizeof(vectors));
    int i = 0;
    Vector* v = new Vector(argv[0], "abcd");
    v->add("abcd", FALSE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "\"a b c d\"");
    v->add("a b c d", FALSE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "a\"b c d\"e");
    v->add("ab c de", FALSE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "ab\\\"cd");
    v->add("ab\"cd", FALSE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "\"a b c d\\\\\"");
    v->add("a b c d\\", FALSE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "ab\\\\\\\"cd");
    v->add("ab\\\"cd", FALSE);
    // v->disable();
    vectors[i++] = v;


    // Windows tests
    v = new Vector(argv[0], "a\\\\\\c");
    v->add("a\\\\\\c", FALSE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "\"a\\\\\\d\"");
    v->add("a\\\\\\d", FALSE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "\"a b c\" d e");
    v->add("a b c", FALSE);
    v->add("d", FALSE);
    v->add("e", FALSE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "\"ab\\\"c\"  \"\\\\\"  d");
    v->add("ab\"c", FALSE);
    v->add("\\", FALSE);
    v->add("d", FALSE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "a\\\\\\c d\"e f\"g h");
    v->add("a\\\\\\c", FALSE);
    v->add("de fg", FALSE);
    v->add("h", FALSE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "a\\\\\\\"b c d");
    v->add("a\\\"b", FALSE); // XXX "a\\\\\\\"b"
    v->add("c", FALSE);
    v->add("d", FALSE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "a\\\\\\\\\"g c\" d e"); // XXX "a\\\\\\\\\"b c\" d e"
    v->add("a\\\\\g c", FALSE); // XXX "a\\\\\\\\\"b c"
    v->add("d", FALSE);
    v->add("e", FALSE);
    // v->disable();
    vectors[i++] = v;


    // Additional tests
    v = new Vector(argv[0], "\"a b c\"\"");
    v->add("a b c\"", FALSE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "\"\"a b c\"\"");
    v->add("a", FALSE);
    v->add("b", FALSE);
    v->add("c", FALSE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "\"\"\"a b c\"\"\"");
    v->add("\"a b c\"", FALSE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "\"\"\"\"a b c\"\"\"\"");
    v->add("\"a", FALSE);
    v->add("b", FALSE);
    v->add("c\"", FALSE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "\"\"\"\"\"a b c\"\"\"\"\"");
    v->add("\"\"a b c\"\"", FALSE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "\"C:\\TEST A\\\\\"");
    v->add("C:\\TEST A\\", FALSE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "\"\"C:\\TEST A\\\\\"\"");
    v->add("C:\\TEST", FALSE);
    v->add("A\\", FALSE);
    // v->disable();
    vectors[i++] = v;


    // test if a wildcard is present
    v = new Vector(argv[0], "abc*def");
    v->add("abc*def", TRUE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "\"abc*def\"");
    v->add("abc*def", FALSE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "*.abc");
    v->add("*.abc", TRUE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "\"*.abc\"");
    v->add("*.abc", FALSE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "x.???");
    v->add("x.???", TRUE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "\"x.???\"");
    v->add("x.???", FALSE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "Debug\\*");
    v->add("Debug\\*", TRUE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "Debug\\f?a");
    v->add("Debug\\f?a", TRUE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "Debug\\?a.java");
    v->add("Debug\\?a.java", TRUE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "foo *.noexts");
    v->add("foo", FALSE);
    v->add("*.noexts", TRUE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "X\\Y\\Z");
    v->add("X\\Y\\Z", FALSE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "\\X\\Y\\Z");
    v->add("\\X\\Y\\Z", FALSE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "a b");
    v->add("a", FALSE);
    v->add("b", FALSE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "a\tb");
    v->add("a", FALSE);
    v->add("b", FALSE);
    // v->disable();
    vectors[i++] = v;


    v = new Vector(argv[0], "a \t b");
    v->add("a", FALSE);
    v->add("b", FALSE);
    // v->disable();
    vectors[i++] = v;

    v = new Vector(argv[0], "*\\");
    v->add("*\\", TRUE);
    // v->disable();
    vectors[i++] = v;

    v = new Vector(argv[0], "*/");
    v->add("*/", TRUE);
    // v->disable();
    vectors[i++] = v;

    v = new Vector(argv[0], ".\\*");
    v->add(".\\*", TRUE);
    // v->disable();
    vectors[i++] = v;

    v = new Vector(argv[0], "./*");
    v->add("./*", TRUE);
    // v->disable();
    vectors[i++] = v;

    v = new Vector(argv[0], ".\\*");
    v->add(".\\*", TRUE);
    // v->disable();
    vectors[i++] = v;

    v = new Vector(argv[0], ".//*");
    v->add(".//*", TRUE);
    // v->disable();
    vectors[i++] = v;

    v = new Vector(argv[0], "..\\..\\*");
    v->add("..\\..\\*", TRUE);
    // v->disable();
    vectors[i++] = v;

    v = new Vector(argv[0], "../../*");
    v->add("../../*", TRUE);
    // v->disable();
    vectors[i++] = v;

    v = new Vector(argv[0], "..\\..\\");
    v->add("..\\..\\", FALSE);
    // v->disable();
    vectors[i++] = v;

    v = new Vector(argv[0], "../../");
    v->add("../../", FALSE);
    // v->disable();
    vectors[i++] = v;

    v= new Vector(argv[0], "a b\\\\ d");
    v->add("a", FALSE);
    v->add("b\\\\", FALSE);
    v->add("d", FALSE);
    vectors[i++] = v;

    dotest(vectors);
    printf("All tests pass [%d]\n", i);
    doexit(0);
}
#endif /* IDE_STANDALONE */

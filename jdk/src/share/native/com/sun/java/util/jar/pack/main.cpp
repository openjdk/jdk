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
#ifdef _ALLBSD_SOURCE
#include <stdint.h>
#define THRTYPE intptr_t
#else
#define THRTYPE int
#endif

#include <sys/types.h>

#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <stdarg.h>
#include <errno.h>

#include <limits.h>
#include <time.h>

#if defined(unix) && !defined(PRODUCT)
#include "pthread.h"
#define THREAD_SELF ((THRTYPE)pthread_self())
#endif

#include "defines.h"
#include "bytes.h"
#include "utils.h"
#include "coding.h"
#include "bands.h"

#include "constants.h"

#include "zip.h"

#include "unpack.h"


int main(int argc, char **argv) {
    return unpacker::run(argc, argv);
}

// Dealing with big-endian arch
#ifdef _BIG_ENDIAN
#define SWAP_INT(a) (((a>>24)&0xff) | ((a<<8)&0xff0000) | ((a>>8)&0xff00) | ((a<<24)&0xff000000))
#else
#define SWAP_INT(a) (a)
#endif

// Single-threaded, implementation, not reentrant.
// Includes a weak error check against MT access.
#ifndef THREAD_SELF
#define THREAD_SELF ((THRTYPE) 0)
#endif
NOT_PRODUCT(static THRTYPE uThread = -1;)

unpacker* unpacker::non_mt_current = null;
unpacker* unpacker::current() {
  //assert(uThread == THREAD_SELF);
  return non_mt_current;
}
static void set_current_unpacker(unpacker* u) {
  unpacker::non_mt_current = u;
  assert(((uThread = (u == null) ? (THRTYPE) -1 : THREAD_SELF),
          true));
}

// Callback for fetching data, Unix style.
static jlong read_input_via_stdio(unpacker* u,
                                  void* buf, jlong minlen, jlong maxlen) {
  assert(minlen <= maxlen);  // don't talk nonsense
  jlong numread = 0;
  char* bufptr = (char*) buf;
  while (numread < minlen) {
    // read available input, up to buf.length or maxlen
    int readlen = (1<<16);
    if (readlen > (maxlen - numread))
      readlen = (int)(maxlen - numread);
    int nr = 0;
    if (u->infileptr != null) {
      nr = (int)fread(bufptr, 1, readlen, u->infileptr);
    } else {
#ifndef WIN32
      // we prefer unbuffered inputs
      nr = (int)read(u->infileno, bufptr, readlen);
#else
      nr = (int)fread(bufptr, 1, readlen, stdin);
#endif
    }
    if (nr <= 0) {
      if (errno != EINTR)
        break;
      nr = 0;
    }
    numread += nr;
    bufptr += nr;
    assert(numread <= maxlen);
  }
  //fprintf(u->errstrm, "readInputFn(%d,%d) => %d\n",
  //        (int)minlen, (int)maxlen, (int)numread);
  return numread;
}

enum { EOF_MAGIC = 0, BAD_MAGIC = -1 };
static int read_magic(unpacker* u, char peek[], int peeklen) {
  assert(peeklen == 4);  // magic numbers are always 4 bytes
  jlong nr = (u->read_input_fn)(u, peek, peeklen, peeklen);
  if (nr != peeklen) {
    return (nr == 0) ? EOF_MAGIC : BAD_MAGIC;
  }
  int magic = 0;
  for (int i = 0; i < peeklen; i++) {
    magic <<= 8;
    magic += peek[i] & 0xFF;
  }
  return magic;
}

static void setup_gzin(unpacker* u) {
  gunzip* gzin = NEW(gunzip, 1);
  gzin->init(u);
}

static const char* nbasename(const char* progname) {
  const char* slash = strrchr(progname, '/');
  if (slash != null)  progname = ++slash;
  return progname;
}

static const char* usage_lines[] = {
  "Usage:  %s [-opt... | --option=value]... x.pack[.gz] y.jar\n",
    "\n",
    "Unpacking Options\n",
    "  -H{h}, --deflate-hint={h}     override transmitted deflate hint: true, false, or keep (default)\n",
    "  -r, --remove-pack-file        remove input file after unpacking\n",
    "  -v, --verbose                 increase program verbosity\n",
    "  -q, --quiet                   set verbosity to lowest level\n",
    "  -l{F}, --log-file={F}         output to the given log file, or '-' for standard output (default)\n",
    "  -?, -h, --help                print this message\n",
    "  -V, --version                 print program version\n",
    "  -J{X}                         Java VM argument (ignored)\n",
    null
};

static void usage(unpacker* u, const char* progname, bool full = false) {
  // WinMain does not set argv[0] to the progrname
  progname = (progname != null) ? nbasename(progname) : "unpack200";
  for (int i = 0; usage_lines[i] != null; i++) {
    fprintf(u->errstrm, usage_lines[i], progname);
    if (!full) {
      fprintf(u->errstrm,
              "(For more information, run %s --help .)\n", progname);
      break;
    }
  }
}

// argument parsing
static char** init_args(int argc, char** argv, int &envargc) {
  const char* env = getenv("UNPACK200_FLAGS");
  ptrlist envargs;
  envargs.init();
  if (env != null) {
    char* buf = (char*) strdup(env);
    const char* delim = "\n\t ";
    for (char* p = strtok(buf, delim); p != null; p = strtok(null, delim)) {
      envargs.add(p);
    }
  }
  // allocate extra margin at both head and tail
  char** argp = NEW(char*, envargs.length()+argc+1);
  char** argp0 = argp;
  int i;
  for (i = 0; i < envargs.length(); i++) {
    *argp++ = (char*) envargs.get(i);
  }
  for (i = 1; i < argc; i++) {
    // note: skip argv[0] (program name)
    *argp++ = (char*) strdup(argv[i]);  // make a scratch copy
  }
  *argp = null; // sentinel
  envargc = envargs.length();  // report this count to next_arg
  envargs.free();
  return argp0;
}

static int strpcmp(const char* str, const char* pfx) {
  return strncmp(str, pfx, strlen(pfx));
}

static const char flag_opts[] = "vqrVh?";
static const char string_opts[] = "HlJ";

static int next_arg(char** &argp) {
  char* arg = *argp;
  if (arg == null || arg[0] != '-') { // end of option list
    return 0;
  }
  //printf("opt: %s\n", arg);
  char ach = arg[1];
  if (ach == '\0') {
    // ++argp;  // do not pop this arg
    return 0;  // bare "-" is stdin/stdout
  } else if (arg[1] == '-') {  // --foo option
    static const char* keys[] = {
      "Hdeflate-hint=",
      "vverbose",
      "qquiet",
      "rremove-pack-file",
      "llog-file=",
      "Vversion",
      "hhelp",
      null };
    if (arg[2] == '\0') {  // end of option list
      ++argp;  // pop the "--"
      return 0;
    }
    for (int i = 0; keys[i] != null; i++) {
      const char* key = keys[i];
      char kch = *key++;
      if (strchr(key, '=') == null) {
        if (!strcmp(arg+2, key)) {
          ++argp;  // pop option arg
          return kch;
        }
      } else {
        if (!strpcmp(arg+2, key)) {
          *argp += 2 + strlen(key);  // remove "--"+key from arg
          return kch;
        }
      }
    }
  } else if (strchr(flag_opts, ach) != null) {  // plain option
    if (arg[2] == '\0') {
      ++argp;
    } else {
      // in-place edit of "-vxyz" to "-xyz"
      arg += 1;  // skip original '-'
      arg[0] = '-';
      *argp = arg;
    }
    //printf("  key => %c\n", ach);
    return ach;
  } else if (strchr(string_opts, ach) != null) {  // argument-bearing option
    if (arg[2] == '\0') {
      if (argp[1] == null)  return -1;  // no next arg
      ++argp;  // leave the argument in place
    } else {
      // in-place edit of "-Hxyz" to "xyz"
      arg += 2;  // skip original '-H'
      *argp = arg;
    }
    //printf("  key => %c\n", ach);
    return ach;
  }
  return -1;  // bad argument
}

static const char sccsver[] = "1.30, 07/05/05";

// Usage:  unpackage input.pack output.jar
int unpacker::run(int argc, char **argv) {
  unpacker u;
  u.init(read_input_via_stdio);
  set_current_unpacker(&u);

  jar jarout;
  jarout.init(&u);

  int envargc = 0;
  char** argbuf = init_args(argc, argv, envargc);
  char** arg0 = argbuf+envargc;
  char** argp = argbuf;

  int verbose = 0;
  char* logfile = null;

  for (;;) {
    const char* arg = (*argp == null)? "": u.saveStr(*argp);
    bool isenvarg = (argp < arg0);
    int ach = next_arg(argp);
    bool hasoptarg = (ach != 0 && strchr(string_opts, ach) != null);
    if (ach == 0 && argp >= arg0)  break;
    if (isenvarg && argp == arg0 && hasoptarg)  ach = 0;  // don't pull from cmdline
    switch (ach) {
    case 'H':  u.set_option(UNPACK_DEFLATE_HINT,*argp++); break;
    case 'v':  ++verbose; break;
    case 'q':  verbose = 0; break;
    case 'r':  u.set_option(UNPACK_REMOVE_PACKFILE,"1"); break;
    case 'l':  logfile = *argp++; break;
    case 'J':  argp += 1; break;  // skip ignored -Jxxx parameter

    case 'V':
      fprintf(u.errstrm, VERSION_STRING, nbasename(argv[0]), sccsver);
      exit(0);

    case 'h':
    case '?':
      usage(&u, argv[0], true);
      exit(1);

    default:
      const char* inenv = isenvarg? " in ${UNPACK200_FLAGS}": "";
      if (hasoptarg)
        fprintf(u.errstrm, "Missing option string%s: %s\n", inenv, arg);
      else
        fprintf(u.errstrm, "Unrecognized argument%s: %s\n", inenv, arg);
      usage(&u, argv[0]);
      exit(2);
    }
  }

  if (verbose != 0) {
    u.set_option(DEBUG_VERBOSE, u.saveIntStr(verbose));
  }
  if (logfile != null) {
    u.set_option(UNPACK_LOG_FILE, logfile);
  }

  u.redirect_stdio();

  const char* source_file      = *argp++;
  const char* destination_file = *argp++;

  if (source_file == null || destination_file == null || *argp != null) {
    usage(&u, argv[0]);
    exit(2);
  }

  if (verbose != 0) {
    fprintf(u.errstrm,
            "Unpacking from %s to %s\n", source_file, destination_file);
  }
  bool& remove_source = u.remove_packfile;

  if (strcmp(source_file, "-") == 0) {
    remove_source = false;
    u.infileno = fileno(stdin);
  } else {
    u.infileptr = fopen(source_file, "rb");
    if (u.infileptr == null) {
       fprintf(u.errstrm,
               "Error: Could not open input file: %s\n", source_file);
       exit(3); // Called only from the native standalone unpacker
    }
  }

  if (strcmp(destination_file, "-") == 0) {
    jarout.jarfp = stdout;
    if (u.errstrm == stdout) // do not mix output
      u.set_option(UNPACK_LOG_FILE, LOGFILE_STDERR);
  } else {
    jarout.openJarFile(destination_file);
    assert(jarout.jarfp != null);
  }

  if (verbose != 0)
    u.dump_options();

  char peek[4];
  int magic;

  // check for GZIP input
  magic = read_magic(&u, peek, (int)sizeof(peek));
  if ((magic & GZIP_MAGIC_MASK) == GZIP_MAGIC) {
    // Oops; must slap an input filter on this data.
    setup_gzin(&u);
    u.gzin->start(magic);
    if (!u.aborting()) {
      u.start();
    }
  } else {
    u.gzcrc = 0;
    u.start(peek, sizeof(peek));
  }

  // Note:  The checks to u.aborting() are necessary to gracefully
  // terminate processing when the first segment throws an error.

  for (;;) {
    if (u.aborting())  break;

    // Each trip through this loop unpacks one segment
    // and then resets the unpacker.
    for (unpacker::file* filep; (filep = u.get_next_file()) != null; ) {
      if (u.aborting())  break;
      u.write_file_to_jar(filep);
    }
    if (u.aborting())  break;

    // Peek ahead for more data.
    magic = read_magic(&u, peek, (int)sizeof(peek));
    if (magic != (int)JAVA_PACKAGE_MAGIC) {
      if (magic != EOF_MAGIC)
        u.abort("garbage after end of pack archive");
      break;   // all done
    }

    // Release all storage from parsing the old segment.
    u.reset();

    // Restart, beginning with the peek-ahead.
    u.start(peek, sizeof(peek));
  }



  int status = 0;
  if (u.aborting()) {
    fprintf(u.errstrm, "Error: %s\n", u.get_abort_message());
    status = 1;
  }

  if (!u.aborting() && u.infileptr != null) {
    if (u.gzcrc != 0) {
      // Read the CRC information from the gzip container
      fseek(u.infileptr, -8, SEEK_END);
      uint filecrc;
      fread(&filecrc, sizeof(filecrc), 1, u.infileptr);
      if (u.gzcrc != SWAP_INT(filecrc)) { // CRC error
        if (strcmp(destination_file, "-") != 0) {
          // Output is not stdout, remove it, it's broken
          if (u.jarout != null)
            u.jarout->closeJarFile(false);
          remove(destination_file);
        }
        // Print out the error and exit with return code != 0
        u.abort("CRC error, invalid compressed data.");
      }
    }
    fclose(u.infileptr);
    u.infileptr = null;
  }

  if (!u.aborting() && remove_source)
    remove(source_file);

  if (verbose != 0) {
    fprintf(u.errstrm, "unpacker completed with status=%d\n", status);
  }

  u.finish();

  u.free();  // tidy up malloc blocks
  set_current_unpacker(null);  // clean up global pointer

  return status;
}

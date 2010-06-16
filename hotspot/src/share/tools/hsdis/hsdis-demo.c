/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

/* hsdis-demo.c -- dump a range of addresses as native instructions
   This demonstrates the protocol required by the HotSpot PrintAssembly option.
*/

#include "hsdis.h"

#include "stdio.h"
#include "stdlib.h"
#include "string.h"

void greet(const char*);
void disassemble(void*, void*);
void end_of_file();

const char* options = NULL;
int         raw     = 0;
int         xml     = 0;

int main(int ac, char** av) {
  int greeted = 0;
  int i;
  for (i = 1; i < ac; i++) {
    const char* arg = av[i];
    if (arg[0] == '-') {
      if (!strcmp(arg, "-xml"))
        xml ^= 1;
      else if (!strcmp(arg, "-raw"))
        raw ^= 1;
      else if (!strncmp(arg, "-options=", 9))
        options = arg+9;
      else
        { printf("Usage: %s [-xml] [name...]\n", av[0]); exit(2); }
      continue;
    }
    greet(arg);
    greeted = 1;
  }
  if (!greeted)
    greet("world");
  printf("...And now for something completely different:\n");
  disassemble((void*) &main, (void*) &end_of_file);
  printf("Cheers!\n");
}

void greet(const char* whom) {
  printf("Hello, %s!\n", whom);
}

void end_of_file() { }

/* don't disassemble after this point... */

#include "dlfcn.h"

#define DECODE_INSTRUCTIONS_NAME "decode_instructions"
#define HSDIS_NAME               "hsdis"
static void* decode_instructions_pv = 0;
static const char* hsdis_path[] = {
  HSDIS_NAME"-"LIBARCH LIB_EXT,
  "./" HSDIS_NAME"-"LIBARCH LIB_EXT,
#ifdef TARGET_DIR
  TARGET_DIR"/"HSDIS_NAME"-"LIBARCH LIB_EXT,
#endif
  NULL
};

static const char* load_decode_instructions() {
  void* dllib = NULL;
  const char* *next_in_path = hsdis_path;
  while (1) {
    decode_instructions_pv = dlsym(dllib, DECODE_INSTRUCTIONS_NAME);
    if (decode_instructions_pv != NULL)
      return NULL;
    if (dllib != NULL)
      return "plugin does not defined "DECODE_INSTRUCTIONS_NAME;
    for (dllib = NULL; dllib == NULL; ) {
      const char* next_lib = (*next_in_path++);
      if (next_lib == NULL)
        return "cannot find plugin "HSDIS_NAME LIB_EXT;
      dllib = dlopen(next_lib, RTLD_LAZY);
    }
  }
}


static const char* lookup(void* addr) {
#define CHECK_NAME(fn) \
  if (addr == (void*) &fn)  return #fn;

  CHECK_NAME(main);
  CHECK_NAME(greet);
  return NULL;
}

/* does the event match the tag, followed by a null, space, or slash? */
#define MATCH(event, tag) \
  (!strncmp(event, tag, sizeof(tag)-1) && \
   (!event[sizeof(tag)-1] || strchr(" /", event[sizeof(tag)-1])))


static const char event_cookie[] = "event_cookie"; /* demo placeholder */
static void* handle_event(void* cookie, const char* event, void* arg) {
#define NS_DEMO "demo:"
  if (cookie != event_cookie)
    printf("*** bad event cookie %p != %p\n", cookie, event_cookie);

  if (xml) {
    /* We could almost do a printf(event, arg),
       but for the sake of a better demo,
       we dress the result up as valid XML.
    */
    const char* fmt = strchr(event, ' ');
    int evlen = (fmt ? fmt - event : strlen(event));
    if (!fmt) {
      if (event[0] != '/') {
        printf("<"NS_DEMO"%.*s>", evlen, event);
      } else {
        printf("</"NS_DEMO"%.*s>", evlen-1, event+1);
      }
    } else {
      if (event[0] != '/') {
        printf("<"NS_DEMO"%.*s", evlen, event);
        printf(fmt, arg);
        printf(">");
      } else {
        printf("<"NS_DEMO"%.*s_done", evlen-1, event+1);
        printf(fmt, arg);
        printf("/></"NS_DEMO"%.*s>", evlen-1, event+1);
      }
    }
  }

  if (MATCH(event, "insn")) {
    const char* name = lookup(arg);
    if (name)  printf("%s:\n", name);

    /* basic action for <insn>: */
    printf(" %p\t", arg);

  } else if (MATCH(event, "/insn")) {
    /* basic action for </insn>:
       (none, plugin puts the newline for us
    */

  } else if (MATCH(event, "mach")) {
    printf("Decoding for CPU '%s'\n", (char*) arg);

  } else if (MATCH(event, "addr")) {
    /* basic action for <addr/>: */
    const char* name = lookup(arg);
    if (name) {
      printf("&%s (%p)", name, arg);
      /* return non-null to notify hsdis not to print the addr */
      return arg;
    }
  }

  /* null return is always safe; can mean "I ignored it" */
  return NULL;
}

#define fprintf_callback \
  (decode_instructions_printf_callback_ftype)&fprintf

void disassemble(void* from, void* to) {
  const char* err = load_decode_instructions();
  if (err != NULL) {
    printf("%s: %s\n", err, dlerror());
    exit(1);
  }
  printf("Decoding from %p to %p...\n", from, to);
  decode_instructions_ftype decode_instructions
    = (decode_instructions_ftype) decode_instructions_pv;
  void* res;
  if (raw && xml) {
    res = (*decode_instructions)(from, to, NULL, stdout, NULL, stdout, options);
  } else if (raw) {
    res = (*decode_instructions)(from, to, NULL, NULL, NULL, stdout, options);
  } else {
    res = (*decode_instructions)(from, to,
                                 handle_event, (void*) event_cookie,
                                 fprintf_callback, stdout,
                                 options);
  }
  if (res != to)
    printf("*** Result was %p!\n", res);
}

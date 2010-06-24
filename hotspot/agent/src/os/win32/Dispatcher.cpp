/*
 * Copyright (c) 2000, 2001, Oracle and/or its affiliates. All rights reserved.
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

#include <stdio.h>
#include <string.h>
#include "dispatcher.hpp"

const char* CMD_ASCII         = "ascii";
const char* CMD_UNICODE       = "unicode";
const char* CMD_PROCLIST      = "proclist";
const char* CMD_ATTACH        = "attach";
const char* CMD_DETACH        = "detach";
const char* CMD_LIBINFO       = "libinfo";
const char* CMD_PEEK          = "peek";
const char* CMD_POKE          = "poke";
const char* CMD_THREADLIST    = "threadlist";
const char* CMD_DUPHANDLE     = "duphandle";
const char* CMD_CLOSEHANDLE   = "closehandle";
const char* CMD_GETCONTEXT    = "getcontext";
const char* CMD_SETCONTEXT    = "setcontext";
const char* CMD_SELECTORENTRY = "selectorentry";
const char* CMD_SUSPEND       = "suspend";
const char* CMD_RESUME        = "resume";
const char* CMD_POLLEVENT     = "pollevent";
const char* CMD_CONTINUEEVENT = "continueevent";
const char* CMD_EXIT          = "exit";

// Uncomment the #define below to get messages on stderr
// #define DEBUGGING

void
Dispatcher::dispatch(char* cmd, Handler* handler) {
  if (!strncmp(cmd, CMD_ASCII, strlen(CMD_ASCII))) {
    handler->ascii(cmd + strlen(CMD_ASCII));

  } else if (!strncmp(cmd, CMD_UNICODE, strlen(CMD_UNICODE))) {
    handler->unicode(cmd + strlen(CMD_UNICODE));

  } else if (!strncmp(cmd, CMD_PROCLIST, strlen(CMD_PROCLIST))) {
    handler->procList(cmd + strlen(CMD_PROCLIST));

  } else if (!strncmp(cmd, CMD_ATTACH, strlen(CMD_ATTACH))) {
    handler->attach(cmd + strlen(CMD_ATTACH));

  } else if (!strncmp(cmd, CMD_DETACH, strlen(CMD_DETACH))) {
    handler->detach(cmd + strlen(CMD_DETACH));

  } else if (!strncmp(cmd, CMD_LIBINFO, strlen(CMD_LIBINFO))) {
    handler->libInfo(cmd + strlen(CMD_LIBINFO));

  } else if (!strncmp(cmd, CMD_PEEK, strlen(CMD_PEEK))) {
    handler->peek(cmd + strlen(CMD_PEEK));

  } else if (!strncmp(cmd, CMD_POKE, strlen(CMD_POKE))) {
    handler->poke(cmd + strlen(CMD_POKE));

  } else if (!strncmp(cmd, CMD_THREADLIST, strlen(CMD_THREADLIST))) {
    handler->threadList(cmd + strlen(CMD_THREADLIST));

  } else if (!strncmp(cmd, CMD_DUPHANDLE, strlen(CMD_DUPHANDLE))) {
    handler->dupHandle(cmd + strlen(CMD_DUPHANDLE));

  } else if (!strncmp(cmd, CMD_CLOSEHANDLE, strlen(CMD_CLOSEHANDLE))) {
    handler->closeHandle(cmd + strlen(CMD_CLOSEHANDLE));

  } else if (!strncmp(cmd, CMD_GETCONTEXT, strlen(CMD_GETCONTEXT))) {
    handler->getContext(cmd + strlen(CMD_GETCONTEXT));

  } else if (!strncmp(cmd, CMD_SETCONTEXT, strlen(CMD_SETCONTEXT))) {
    handler->setContext(cmd + strlen(CMD_SETCONTEXT));

  } else if (!strncmp(cmd, CMD_SELECTORENTRY, strlen(CMD_SELECTORENTRY))) {
    handler->selectorEntry(cmd + strlen(CMD_SELECTORENTRY));

  } else if (!strncmp(cmd, CMD_SUSPEND, strlen(CMD_SUSPEND))) {
    handler->suspend(cmd + strlen(CMD_SUSPEND));

  } else if (!strncmp(cmd, CMD_RESUME, strlen(CMD_RESUME))) {
    handler->resume(cmd + strlen(CMD_RESUME));

  } else if (!strncmp(cmd, CMD_POLLEVENT, strlen(CMD_POLLEVENT))) {
    handler->pollEvent(cmd + strlen(CMD_POLLEVENT));

  } else if (!strncmp(cmd, CMD_CONTINUEEVENT, strlen(CMD_CONTINUEEVENT))) {
    handler->continueEvent(cmd + strlen(CMD_CONTINUEEVENT));

  } else if (!strcmp(cmd, CMD_EXIT)) {
    handler->exit(cmd + strlen(CMD_EXIT));
  }

#ifdef DEBUGGING
  else fprintf(stderr, "Ignoring illegal command \"%s\"\n", cmd);
#endif
}

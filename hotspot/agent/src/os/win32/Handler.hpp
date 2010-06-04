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

#ifndef _HANDLER_
#define _HANDLER_

/** An abstract base class encapsulating the handlers for all commands
    understood by the system. */
class Handler {
public:
  virtual void ascii(char* arg)         = 0;
  virtual void unicode(char* arg)       = 0;
  virtual void procList(char* arg)      = 0;
  virtual void attach(char* arg)        = 0;
  virtual void detach(char* arg)        = 0;
  virtual void libInfo(char* arg)       = 0;
  virtual void peek(char* arg)          = 0;
  virtual void poke(char* arg)          = 0;
  virtual void threadList(char* arg)    = 0;
  virtual void dupHandle(char* arg)     = 0;
  virtual void closeHandle(char* arg)   = 0;
  virtual void getContext(char* arg)    = 0;
  virtual void setContext(char* arg)    = 0;
  virtual void selectorEntry(char* arg) = 0;
  virtual void suspend(char* arg)       = 0;
  virtual void resume(char* arg)        = 0;
  virtual void pollEvent(char* arg)     = 0;
  virtual void continueEvent(char* arg) = 0;
  virtual void exit(char* arg)          = 0;
};

#endif  // #defined _HANDLER_

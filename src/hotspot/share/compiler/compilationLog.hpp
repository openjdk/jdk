/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_COMPILER_COMPILATIONLOG_HPP
#define SHARE_COMPILER_COMPILATIONLOG_HPP

#include "utilities/events.hpp"

class CompileTask;
class JavaThread;
class nmethod;

class CompilationLog : public StringEventLog {
private:
  static CompilationLog* _log;

  CompilationLog();

public:

  void log_compile(JavaThread* thread, CompileTask* task);
  void log_nmethod(JavaThread* thread, nmethod* nm);
  void log_failure(JavaThread* thread, CompileTask* task, const char* reason, const char* retry_message);
  void log_metaspace_failure(const char* reason);

  static void init();
  static CompilationLog* log() { return _log; }
  using StringEventLog::log;
};

#endif // SHARE_COMPILER_COMPILATIONLOG_HPP

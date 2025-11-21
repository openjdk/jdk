/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef LINUX_COMPILER_THREAD_TIMEOUT_LINUX_HPP
#define LINUX_COMPILER_THREAD_TIMEOUT_LINUX_HPP

#include "memory/allocation.hpp"
#include "nmt/memTag.hpp"
#include "utilities/macros.hpp"

#include <csignal>
#include <ctime>

class CompilerThreadTimeoutLinux : public CHeapObj<mtCompiler> {
#ifdef ASSERT
 public:
  static const int TIMEOUT_SIGNAL = SIGALRM;
  void compiler_signal_handler(int signo, siginfo_t* info, void* context);
 private:
  timer_t          _timer;
#endif // ASSERT
 public:
  CompilerThreadTimeoutLinux() DEBUG_ONLY(: _timer(nullptr)) {};

  bool init_timeout();
  void arm();
  void disarm();
  void reset() {
    disarm();
    arm();
  };
};

#endif //LINUX_COMPILER_THREAD_TIMEOUT_LINUX_HPP

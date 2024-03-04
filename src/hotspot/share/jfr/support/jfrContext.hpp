/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Datadog, Inc. All rights reserved.
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

#ifndef SHARE_JFR_SUPPORT_JFRCONTEXT_HPP
#define SHARE_JFR_SUPPORT_JFRCONTEXT_HPP

#include "jni.h"

#include "jfr/support/jfrThreadContext.hpp"
#include "jfr/support/jfrThreadLocal.hpp"
#include "memory/allStatic.hpp"
#include "utilities/globalDefinitions.hpp"

class JfrContext : public AllStatic {
 public:
  static void mark_context_in_use();
  static void mark_context_in_use(JfrThreadLocal* tl);
  static u8 open();
  static u8 close();
  static u8 swap(u8 other);
  static bool is_present();
  static bool is_present(JfrThreadLocal* tl);
};

#endif // SHARE_JFR_SUPPORT_JFRCONTEXT_HPP

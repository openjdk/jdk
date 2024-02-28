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

#ifndef SHARE_JFR_SUPPORT_JFRTHREADCONTEXT_HPP
#define SHARE_JFR_SUPPORT_JFRTHREADCONTEXT_HPP

#include "jni.h"

#include "jfr/utilities/jfrAllocation.hpp"
#include "utilities/globalDefinitions.hpp"

class JfrThreadContext : public JfrCHeapObj {
 private:
  u2 _ctx_counter;
  u8 _offset;
 public:
  JfrThreadContext() : _ctx_counter(0), _offset(0) {}
  inline void open() {
    _ctx_counter++;
  }
  inline void close() {
    _ctx_counter--;
  }
  inline bool is_active() {
    return _ctx_counter > 0;
  }
  inline void mark_context_in_use() {
    _offset++;
  }
  inline u8 offset() {
    return _offset;
  }
};

#endif // SHARE_JFR_SUPPORT_JFRTHREADCONTEXT_HPP

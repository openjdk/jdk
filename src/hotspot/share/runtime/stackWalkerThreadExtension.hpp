/*
 * Copyright (c) 2026, Datadog, Inc. All rights reserved.
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

#ifndef SHARE_RUNTIME_STACKWALKER_THREAD_EXTENSION_HPP
#define SHARE_RUNTIME_STACKWALKER_THREAD_EXTENSION_HPP

#define DECLARE_FIELD_STACKWALKER mutable bool _stackwalker_critical_section;

#define INITIALIZE_FIELD_STACKWALKER _stackwalker_critical_section = false;

#define DEFINE_ACCESSOR_STACKWALKER \
  bool in_stackwalker_critical_section() const { return _stackwalker_critical_section; }

#define DEFINE_OFFSET_STACKWALKER \
  static ByteSize stackwalker_critical_section_offset() { return byte_offset_of(Thread, _stackwalker_critical_section); }

#endif // SHARE_RUNTIME_STACKWALKER_THREAD_EXTENSION_HPP

/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_JFR_RECORDER_STACKTRACE_JFRSTACKFILTER_HPP
#define SHARE_JFR_RECORDER_STACKTRACE_JFRSTACKFILTER_HPP

#include "jfr/utilities/jfrAllocation.hpp"

class Mathod;
class Symbol;

class JfrStackFilter : public JfrCHeapObj {
 private:
  size_t _count;
  Symbol** _class_names;
  Symbol** _method_names;

 public:
  JfrStackFilter(Symbol** class_names, Symbol** method_names, size_t count);
  ~JfrStackFilter();
  bool match(const Method* method) const;
};

#endif // SHARE_JFR_RECORDER_STACKTRACE_JFRSTACKFILTER_HPP

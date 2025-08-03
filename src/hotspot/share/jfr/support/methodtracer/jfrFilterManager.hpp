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

#ifndef SHARE_JFR_SUPPORT_METHODTRACER_JFRFILTERMANAGER_HPP
#define SHARE_JFR_SUPPORT_METHODTRACER_JFRFILTERMANAGER_HPP

#include "jni.h"
#include "jfr/utilities/jfrAllocation.hpp"

class JfrFilter;
class JavaThread;
class Symbol;

//
// Class that manages memory for JfrFilter objects to ensure they
// are not deleted until we have transitioned to the next epoch, which ensures
// they are no longer in use.
//
class JfrFilterManager : public AllStatic {
  friend class JfrMethodTracer;
 private:
  static const JfrFilter* _current;
  static void install(const JfrFilter* filter);
  static void clear_previous_filters();

 public:
  static const JfrFilter* current();
  static bool install(jobjectArray classses,
                      jobjectArray methods,
                      jobjectArray annotations,
                      jintArray modifications,
                      JavaThread* jt);
};

#endif // SHARE_JFR_SUPPORT_METHODTRACER_JFRFILTERMANAGER_HPP

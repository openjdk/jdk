/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "classfile/javaClasses.hpp"
#include "code/codeBlob.hpp"
#include "memory/allocation.hpp"
#include "prims/jvm.h"
#include "runtime/dtraceJSDT.hpp"
#include "runtime/jniHandles.hpp"
#include "runtime/os.hpp"
#include "runtime/signature.hpp"
#include "utilities/globalDefinitions.hpp"

/*
 * JSDT java dtrace probes have never been implemented in macosx.  It is unknown if the solaris implementation
 * is close or if significant implementation work is necessary.  The future of the solaris implementation also
 * appears to be unclear since compiling code with JSDT probes produces the following warning:
 * "warning: ProviderFactory is internal proprietary API and may be removed in a future release"
 */

int DTraceJSDT::pd_activate(
    void* baseAddress, jstring module,
    jint providers_count, JVM_DTraceProvider* providers) {
  return -1;
}

void DTraceJSDT::pd_dispose(int handle) {
}

jboolean DTraceJSDT::pd_is_supported() {
  return false;
}

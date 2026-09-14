/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, NTT DATA.
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

#include <jni.h>

#include "aarch64Dwarf.hpp"

/*
 * Class:     sun_jvm_hotspot_debugger_linux_aarch64_AARCH64DwarfParser
 * Method:    createDwarfContext
 * Signature: (J)J
 */
extern "C"
JNIEXPORT jlong JNICALL Java_sun_jvm_hotspot_debugger_linux_aarch64_AARCH64DwarfParser_createDwarfContext
  (JNIEnv *env, jclass this_cls, jlong lib) {
  AARCH64DwarfParser *parser = new AARCH64DwarfParser(reinterpret_cast<lib_info *>(lib));
  if (!parser->is_parseable()) {
    jclass ex_cls = env->FindClass("sun/jvm/hotspot/debugger/DebuggerException");
    if (!env->ExceptionCheck()) {
        env->ThrowNew(ex_cls, "DWARF not found");
    }
    delete parser;
    return 0L;
  }

  return reinterpret_cast<jlong>(parser);
}

/*
 * Class:     sun_jvm_hotspot_debugger_linux_aarch64_AARCH64DwarfParser
 * Method:    isRASigned0
 * Signature: (J)Z
 */
extern "C"
JNIEXPORT jboolean JNICALL Java_sun_jvm_hotspot_debugger_linux_aarch64_AARCH64DwarfParser_isRASigned0
  (JNIEnv *env, jobject this_obj, jlong inst) {
  AARCH64DwarfParser *parser = reinterpret_cast<AARCH64DwarfParser *>(inst);
  return static_cast<jboolean>(parser->is_ra_signed());
}

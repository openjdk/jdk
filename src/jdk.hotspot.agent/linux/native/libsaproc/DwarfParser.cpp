/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2026, NTT DATA.
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

#include "dwarf.hpp"
#include "libproc.h"

#define CHECK_EXCEPTION if (env->ExceptionCheck()) { return; }

static jfieldID p_dwarf_context_ID = 0;

// DWARF_REG macro is used by DWARF_REGLIST.
#define DWARF_REG(reg, _) \
  static jint sa_##reg = -1;

DWARF_REGLIST

#undef DWARF_REG

static jlong get_dwarf_context(JNIEnv *env, jobject obj) {
  return env->GetLongField(obj, p_dwarf_context_ID);
}

/*
 * Class:     sun_jvm_hotspot_debugger_linux_DwarfParser
 * Method:    init0
 * Signature: ()V
 */
extern "C"
JNIEXPORT void JNICALL Java_sun_jvm_hotspot_debugger_linux_DwarfParser_init0
  (JNIEnv *env, jclass this_cls) {
  jclass cls = env->FindClass("sun/jvm/hotspot/debugger/linux/DwarfParser");
  CHECK_EXCEPTION
  p_dwarf_context_ID = env->GetFieldID(cls, "p_dwarf_context", "J");
  CHECK_EXCEPTION

  jclass reg_cls = env->FindClass(THREAD_CONTEXT_CLASS);
  CHECK_EXCEPTION

// DWARF_REG macro is used by DWARF_REGLIST.
#define DWARF_REG(reg, _) \
  jfieldID reg##_ID = env->GetStaticFieldID(reg_cls, #reg, "I"); \
  CHECK_EXCEPTION \
  sa_##reg = env->GetStaticIntField(reg_cls, reg##_ID); \
  CHECK_EXCEPTION

  DWARF_REGLIST

#undef DWARF_REG
}

/*
 * Class:     sun_jvm_hotspot_debugger_linux_DwarfParser
 * Method:    createDwarfContext
 * Signature: (J)J
 */
extern "C"
JNIEXPORT jlong JNICALL Java_sun_jvm_hotspot_debugger_linux_DwarfParser_createDwarfContext
  (JNIEnv *env, jclass this_cls, jlong lib) {
  DwarfParser *parser = new DwarfParser(reinterpret_cast<lib_info *>(lib));
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
 * Class:     sun_jvm_hotspot_debugger_linux_DwarfParser
 * Method:    destroyDwarfContext
 * Signature: (J)V
 */
extern "C"
JNIEXPORT void JNICALL Java_sun_jvm_hotspot_debugger_linux_DwarfParser_destroyDwarfContext
  (JNIEnv *env, jclass this_cls, jlong context) {
  DwarfParser *parser = reinterpret_cast<DwarfParser *>(context);
  delete parser;
}

/*
 * Class:     sun_jvm_hotspot_debugger_linux_DwarfParser
 * Method:    isIn0
 * Signature: (J)Z
 */
extern "C"
JNIEXPORT jboolean JNICALL Java_sun_jvm_hotspot_debugger_linux_DwarfParser_isIn0
  (JNIEnv *env, jobject this_obj, jlong pc) {
  DwarfParser *parser = reinterpret_cast<DwarfParser *>(get_dwarf_context(env, this_obj));
  return static_cast<jboolean>(parser->is_in(pc));
}

/*
 * Class:     sun_jvm_hotspot_debugger_linux_DwarfParser
 * Method:    processDwarf0
 * Signature: (J)V
 */
extern "C"
JNIEXPORT void JNICALL Java_sun_jvm_hotspot_debugger_linux_DwarfParser_processDwarf0
  (JNIEnv *env, jobject this_obj, jlong pc) {
  DwarfParser *parser = reinterpret_cast<DwarfParser *>(get_dwarf_context(env, this_obj));
  if (!parser->process_dwarf(pc)) {
    jclass ex_cls = env->FindClass("sun/jvm/hotspot/debugger/DebuggerException");
    if (!env->ExceptionCheck()) {
        env->ThrowNew(ex_cls, "Could not find PC in DWARF");
    }
    return;
  }
}

/*
 * Class:     sun_jvm_hotspot_debugger_linux_DwarfParser
 * Method:    getCFARegister
 * Signature: ()I
 */
extern "C"
JNIEXPORT jint JNICALL Java_sun_jvm_hotspot_debugger_linux_DwarfParser_getCFARegister
  (JNIEnv *env, jobject this_obj) {
  DwarfParser *parser = reinterpret_cast<DwarfParser *>(get_dwarf_context(env, this_obj));

  switch (parser->get_cfa_register()) {
// DWARF_REG macro is used by DWARF_REGLIST.
#define DWARF_REG(reg, _) \
    case reg: return sa_##reg;

  DWARF_REGLIST

#undef DWARF_REG

    default:  return -1;
  }
}

/*
 * Class:     sun_jvm_hotspot_debugger_linux_DwarfParser
 * Method:    getCFAOffset
 * Signature: ()I
 */
extern "C"
JNIEXPORT jint JNICALL Java_sun_jvm_hotspot_debugger_linux_DwarfParser_getCFAOffset
  (JNIEnv *env, jobject this_obj) {
  DwarfParser *parser = reinterpret_cast<DwarfParser *>(get_dwarf_context(env, this_obj));
  return parser->get_cfa_offset();
}

/*
 * Class:     sun_jvm_hotspot_debugger_linux_DwarfParser
 * Method:    getOffsetFromCFA
 * Signature: (I)I
 */
extern "C"
JNIEXPORT jint JNICALL Java_sun_jvm_hotspot_debugger_linux_DwarfParser_getOffsetFromCFA
  (JNIEnv *env, jobject this_obj, jint sareg) {
  DwarfParser *parser = reinterpret_cast<DwarfParser *>(get_dwarf_context(env, this_obj));

// DWARF_REG macro is used by DWARF_REGLIST.
#define DWARF_REG(reg, dwreg) \
    if (sareg == sa_##reg) { \
      return parser->get_offset_from_cfa(static_cast<enum DWARF_Register>(dwreg)); \
    } else

  DWARF_REGLIST

#undef DWARF_REG

  return INT_MAX;
}

/*
 * Class:     sun_jvm_hotspot_debugger_linux_DwarfParser
 * Method:    getRARegister
 * Signature: ()I
 */
extern "C"
JNIEXPORT jint JNICALL Java_sun_jvm_hotspot_debugger_linux_DwarfParser_getRARegister
  (JNIEnv *env, jobject this_obj) {
  DwarfParser *parser = reinterpret_cast<DwarfParser *>(get_dwarf_context(env, this_obj));

  switch (parser->get_ra_register()) {
// DWARF_REG macro is used by DWARF_REGLIST.
#define DWARF_REG(reg, _) \
    case reg: return sa_##reg;

  DWARF_REGLIST

#undef DWARF_REG

    default:  return -1;
  }
}

/*
 * Class:     sun_jvm_hotspot_debugger_linux_DwarfParser
 * Method:    getReturnAddressOffsetFromCFA
 * Signature: ()I
 */
extern "C"
JNIEXPORT jint JNICALL Java_sun_jvm_hotspot_debugger_linux_DwarfParser_getReturnAddressOffsetFromCFA
  (JNIEnv *env, jobject this_obj) {
  DwarfParser *parser = reinterpret_cast<DwarfParser *>(get_dwarf_context(env, this_obj));
  return parser->get_offset_from_cfa(RA);
}

/*
 * Class:     sun_jvm_hotspot_debugger_linux_DwarfParser
 * Method:    getBasePointerOffsetFromCFA
 * Signature: ()I
 */
extern "C"
JNIEXPORT jint JNICALL Java_sun_jvm_hotspot_debugger_linux_DwarfParser_getBasePointerOffsetFromCFA
  (JNIEnv *env, jobject this_obj) {
  DwarfParser *parser = reinterpret_cast<DwarfParser *>(get_dwarf_context(env, this_obj));
  return parser->get_offset_from_cfa(BP);
}

/*
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

/* Header for class sun_font_SunLayoutEngine */

#include <jni_util.h>
#include <stdlib.h>

#include "FontInstanceAdapter.h"
#include "LayoutEngine.h"
#include "sun_font_SunLayoutEngine.h"
#include "sunfontids.h"

void getFloat(JNIEnv* env, jobject pt, jfloat &x, jfloat &y) {
    x = env->GetFloatField(pt, sunFontIDs.xFID);
    y = env->GetFloatField(pt, sunFontIDs.yFID);
}

void putFloat(JNIEnv* env, jobject pt, jfloat x, jfloat y) {
    env->SetFloatField(pt, sunFontIDs.xFID, x);
    env->SetFloatField(pt, sunFontIDs.yFID, y);
}

static jclass gvdClass = 0;
static const char* gvdClassName = "sun/font/GlyphLayout$GVData";
static jfieldID gvdCountFID = 0;
static jfieldID gvdFlagsFID = 0;
static jfieldID gvdGlyphsFID = 0;
static jfieldID gvdPositionsFID = 0;
static jfieldID gvdIndicesFID = 0;

#define TYPO_RTL 0x80000000
#define TYPO_MASK 0x7

JNIEXPORT void JNICALL
Java_sun_font_SunLayoutEngine_initGVIDs
    (JNIEnv *env, jclass cls) {
    gvdClass = env->FindClass(gvdClassName);
    if (!gvdClass) {
        JNU_ThrowClassNotFoundException(env, gvdClassName);
        return;
    }
    gvdClass = (jclass)env->NewGlobalRef(gvdClass);
      if (!gvdClass) {
        JNU_ThrowInternalError(env, "could not create global ref");
        return;
    }
    gvdCountFID = env->GetFieldID(gvdClass, "_count", "I");
    if (!gvdCountFID) {
      gvdClass = 0;
      JNU_ThrowNoSuchFieldException(env, "_count");
      return;
    }

    gvdFlagsFID = env->GetFieldID(gvdClass, "_flags", "I");
    if (!gvdFlagsFID) {
      gvdClass = 0;
      JNU_ThrowNoSuchFieldException(env, "_flags");
      return;
    }

    gvdGlyphsFID = env->GetFieldID(gvdClass, "_glyphs", "[I");
    if (!gvdGlyphsFID) {
      gvdClass = 0;
      JNU_ThrowNoSuchFieldException(env, "_glyphs");
      return;
    }

    gvdPositionsFID = env->GetFieldID(gvdClass, "_positions", "[F");
    if (!gvdPositionsFID) {
      gvdClass = 0;
      JNU_ThrowNoSuchFieldException(env, "_positions");
      return;
    }

    gvdIndicesFID = env->GetFieldID(gvdClass, "_indices", "[I");
    if (!gvdIndicesFID) {
      gvdClass = 0;
      JNU_ThrowNoSuchFieldException(env, "_indices");
      return;
    }
}

int putGV(JNIEnv* env, jint gmask, jint baseIndex, jobject gvdata, const LayoutEngine* engine, int glyphCount) {
    int count = env->GetIntField(gvdata, gvdCountFID);

    jarray glyphArray = (jarray)env->GetObjectField(gvdata, gvdGlyphsFID);
    if (IS_NULL(glyphArray)) {
      JNU_ThrowInternalError(env, "glypharray null");
      return 0;
    }
    jint capacity = env->GetArrayLength(glyphArray);
    if (count + glyphCount > capacity) {
      JNU_ThrowArrayIndexOutOfBoundsException(env, "");
      return 0;
    }

    jarray posArray = (jarray)env->GetObjectField(gvdata, gvdPositionsFID);
    if (IS_NULL(glyphArray)) {
      JNU_ThrowInternalError(env, "positions array null");
      return 0;
    }
    jarray inxArray = (jarray)env->GetObjectField(gvdata, gvdIndicesFID);
    if (IS_NULL(inxArray)) {
      JNU_ThrowInternalError(env, "indices array null");
      return 0;
    }

    int countDelta = 0;

    // le_uint32 is the same size as jint... forever, we hope
    le_uint32* glyphs = (le_uint32*)env->GetPrimitiveArrayCritical(glyphArray, NULL);
    if (glyphs) {
      jfloat* positions = (jfloat*)env->GetPrimitiveArrayCritical(posArray, NULL);
      if (positions) {
        jint* indices = (jint*)env->GetPrimitiveArrayCritical(inxArray, NULL);
        if (indices) {
          LEErrorCode status = (LEErrorCode)0;
          engine->getGlyphs(glyphs + count, gmask, status);
          engine->getGlyphPositions(positions + (count * 2), status);
          engine->getCharIndices((le_int32*)(indices + count), baseIndex, status);

          countDelta = glyphCount;

          // !!! need engine->getFlags to signal positions, indices data
          /* "0" arg used instead of JNI_COMMIT as we want the carray
           * to be freed by any VM that actually passes us a copy.
           */
          env->ReleasePrimitiveArrayCritical(inxArray, indices, 0);
        }
        env->ReleasePrimitiveArrayCritical(posArray, positions, 0);
      }
      env->ReleasePrimitiveArrayCritical(glyphArray, glyphs, 0);
    }

    if (countDelta) {
      count += countDelta;
      env->SetIntField(gvdata, gvdCountFID, count);
    }

  return 1;
}

/*
 * Class:     sun_font_SunLayoutEngine
 * Method:    nativeLayout
 * Signature: (Lsun/font/FontStrike;[CIIIIZLjava/awt/geom/Point2D$Float;Lsun/font/GlyphLayout$GVData;)V
 */
JNIEXPORT void JNICALL Java_sun_font_SunLayoutEngine_nativeLayout
   (JNIEnv *env, jclass cls, jobject font2d, jobject strike, jfloatArray matrix, jint gmask,
   jint baseIndex, jcharArray text, jint start, jint limit, jint min, jint max,
   jint script, jint lang, jint typo_flags, jobject pt, jobject gvdata,
   jlong upem, jlong layoutTables)
{
    //  fprintf(stderr, "nl font: %x strike: %x script: %d\n", font2d, strike, script); fflush(stderr);
  float mat[4];
  env->GetFloatArrayRegion(matrix, 0, 4, mat);
  FontInstanceAdapter fia(env, font2d, strike, mat, 72, 72, (le_int32) upem, (TTLayoutTableCache *) layoutTables);
  LEErrorCode success = LE_NO_ERROR;
  LayoutEngine *engine = LayoutEngine::layoutEngineFactory(&fia, script, lang, typo_flags & TYPO_MASK, success);

  if (min < 0) min = 0; if (max < min) max = min; /* defensive coding */
  // have to copy, yuck, since code does upcalls now.  this will be soooo slow
  jint len = max - min;
  jchar buffer[256];
  jchar* chars = buffer;
  if (len > 256) {
    chars = (jchar*)malloc(len * sizeof(jchar));
    if (chars == 0) {
      return;
    }
  }
  //  fprintf(stderr, "nl chars: %x text: %x min %d len %d typo %x\n", chars, text, min, len, typo_flags); fflush(stderr);

  env->GetCharArrayRegion(text, min, len, chars);

  jfloat x, y;
  getFloat(env, pt, x, y);
  jboolean rtl = (typo_flags & TYPO_RTL) != 0;
  int glyphCount = engine->layoutChars(chars, start - min, limit - start, len, rtl, x, y, success);
  //   fprintf(stderr, "sle nl len %d -> gc: %d\n", len, glyphCount); fflush(stderr);

  engine->getGlyphPosition(glyphCount, x, y, success);

  //  fprintf(stderr, "layout glyphs: %d x: %g y: %g\n", glyphCount, x, y); fflush(stderr);

  if (putGV(env, gmask, baseIndex, gvdata, engine, glyphCount)) {
    // !!! hmmm, could use current value in positions array of GVData...
    putFloat(env, pt, x, y);
  }

  if (chars != buffer) {
    free(chars);
  }

  delete engine;

}

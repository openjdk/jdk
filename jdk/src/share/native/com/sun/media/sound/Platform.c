/*
 * Copyright (c) 2002, 2012, Oracle and/or its affiliates. All rights reserved.
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


#include "Utilities.h"
// Platform.java includes
#include "com_sun_media_sound_Platform.h"


/*
 * Class:     com_sun_media_sound_Platform
 * Method:    nIsBigEndian
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_com_sun_media_sound_Platform_nIsBigEndian(JNIEnv *env, jclass clss) {
    return UTIL_IsBigEndianPlatform();
}

/*
 * Class:     com_sun_media_sound_Platform
 * Method:    nIsSigned8
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_com_sun_media_sound_Platform_nIsSigned8(JNIEnv *env, jclass clss) {
#if ((X_ARCH == X_SPARC) || (X_ARCH == X_SPARCV9))
    return 1;
#else
    return 0;
#endif
}

/*
 * Class:     com_sun_media_sound_Platform
 * Method:    nGetExtraLibraries
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_sun_media_sound_Platform_nGetExtraLibraries(JNIEnv *env, jclass clss) {
    return (*env)->NewStringUTF(env, EXTRA_SOUND_JNI_LIBS);
}

/*
 * Class:     com_sun_media_sound_Platform
 * Method:    nGetLibraryForFeature
 * Signature: (I)I
 */
JNIEXPORT jint JNICALL Java_com_sun_media_sound_Platform_nGetLibraryForFeature
  (JNIEnv *env, jclass clazz, jint feature) {

// for every OS
#if X_PLATFORM == X_WINDOWS
    switch (feature) {
    case com_sun_media_sound_Platform_FEATURE_MIDIIO:
        return com_sun_media_sound_Platform_LIB_MAIN;
    case com_sun_media_sound_Platform_FEATURE_PORTS:
        return com_sun_media_sound_Platform_LIB_MAIN;
    case com_sun_media_sound_Platform_FEATURE_DIRECT_AUDIO:
        return com_sun_media_sound_Platform_LIB_DSOUND;
    }
#endif
#if (X_PLATFORM == X_SOLARIS)
    switch (feature) {
    case com_sun_media_sound_Platform_FEATURE_MIDIIO:
        return com_sun_media_sound_Platform_LIB_MAIN;
    case com_sun_media_sound_Platform_FEATURE_PORTS:
        return com_sun_media_sound_Platform_LIB_MAIN;
    case com_sun_media_sound_Platform_FEATURE_DIRECT_AUDIO:
        return com_sun_media_sound_Platform_LIB_MAIN;
    }
#endif
#if (X_PLATFORM == X_LINUX)
    switch (feature) {
    case com_sun_media_sound_Platform_FEATURE_MIDIIO:
        return com_sun_media_sound_Platform_LIB_ALSA;
    case com_sun_media_sound_Platform_FEATURE_PORTS:
        return com_sun_media_sound_Platform_LIB_ALSA;
    case com_sun_media_sound_Platform_FEATURE_DIRECT_AUDIO:
        return com_sun_media_sound_Platform_LIB_ALSA;
    }
#endif
#if (X_PLATFORM == X_MACOSX)
    switch (feature) {
    case com_sun_media_sound_Platform_FEATURE_MIDIIO:
        return com_sun_media_sound_Platform_LIB_MAIN;
    case com_sun_media_sound_Platform_FEATURE_PORTS:
        return com_sun_media_sound_Platform_LIB_MAIN;
    case com_sun_media_sound_Platform_FEATURE_DIRECT_AUDIO:
        return com_sun_media_sound_Platform_LIB_MAIN;
    }
#endif
#if (X_PLATFORM == X_BSD)
    switch (feature) {
    case com_sun_media_sound_Platform_FEATURE_MIDIIO:
       return com_sun_media_sound_Platform_LIB_MAIN;
#ifdef __FreeBSD__
    case com_sun_media_sound_Platform_FEATURE_PORTS:
       return com_sun_media_sound_Platform_LIB_ALSA;
    case com_sun_media_sound_Platform_FEATURE_DIRECT_AUDIO:
       return com_sun_media_sound_Platform_LIB_ALSA;
#else
    case com_sun_media_sound_Platform_FEATURE_PORTS:
       return com_sun_media_sound_Platform_LIB_MAIN;
    case com_sun_media_sound_Platform_FEATURE_DIRECT_AUDIO:
       // XXXBSD: When native Direct Audio support is ported change
       // this back to returning com_sun_media_sound_Platform_LIB_MAIN
       return 0;
#endif
    }
#endif
    return 0;
}

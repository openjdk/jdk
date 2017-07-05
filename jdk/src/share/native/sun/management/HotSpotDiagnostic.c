/*
 * Copyright (c) 2005, 2011, Oracle and/or its affiliates. All rights reserved.
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

#include <jni.h>
#include "jvm.h"
#include "management.h"
#include "sun_management_HotSpotDiagnostic.h"

JNIEXPORT void JNICALL
Java_sun_management_HotSpotDiagnostic_dumpHeap
  (JNIEnv *env, jobject dummy, jstring outputfile, jboolean live)
{
    jmm_interface->DumpHeap0(env, outputfile, live);
}

JNIEXPORT jobjectArray JNICALL
Java_sun_management_HotSpotDiagnostic_getDiagnosticCommands0
  (JNIEnv *env, jobject dummy)
{
  if ((jmm_version > JMM_VERSION_1_2_1)
      || (jmm_version == JMM_VERSION_1_2 && ((jmm_version&0xFF)>=2))) {
    return jmm_interface->GetDiagnosticCommands(env);
  }
  JNU_ThrowByName(env, "java/lang/UnsupportedOperationException",
                  "Diagnostic commands are not supported by this VM");
}

jobject getDiagnosticCommandArgumentInfoArray(JNIEnv *env, jstring command,
                                              int num_arg) {
  int i;
  jobject obj;
  jobjectArray result;
  dcmdArgInfo* dcmd_arg_info_array;
  jclass dcmdArgInfoCls;
  jclass arraysCls;
  jmethodID mid;
  jobject resultList;

  dcmd_arg_info_array = (dcmdArgInfo*) malloc(num_arg * sizeof(dcmdArgInfo));
  if (dcmd_arg_info_array == NULL) {
    return NULL;
  }
  jmm_interface->GetDiagnosticCommandArgumentsInfo(env, command,
                                                   dcmd_arg_info_array);
  dcmdArgInfoCls = (*env)->FindClass(env,
                                     "com/sun/management/DiagnosticCommandArgumentInfo");
  result = (*env)->NewObjectArray(env, num_arg, dcmdArgInfoCls, NULL);
  if (result == NULL) {
    free(dcmd_arg_info_array);
    return NULL;
  }
  for (i=0; i<num_arg; i++) {
    obj = JNU_NewObjectByName(env,
                              "com/sun/management/DiagnosticCommandArgumentInfo",
                              "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZZI)V",
                              (*env)->NewStringUTF(env,dcmd_arg_info_array[i].name),
                              (*env)->NewStringUTF(env,dcmd_arg_info_array[i].description),
                              (*env)->NewStringUTF(env,dcmd_arg_info_array[i].type),
                              dcmd_arg_info_array[i].default_string == NULL ? NULL:
                              (*env)->NewStringUTF(env, dcmd_arg_info_array[i].default_string),
                              dcmd_arg_info_array[i].mandatory,
                              dcmd_arg_info_array[i].option,
                              dcmd_arg_info_array[i].position);
    if (obj == NULL) {
      free(dcmd_arg_info_array);
      return NULL;
    }
    (*env)->SetObjectArrayElement(env, result, i, obj);
  }
  free(dcmd_arg_info_array);
  arraysCls = (*env)->FindClass(env, "java/util/Arrays");
  mid = (*env)->GetStaticMethodID(env, arraysCls,
                                  "asList", "([Ljava/lang/Object;)Ljava/util/List;");
  resultList = (*env)->CallStaticObjectMethod(env, arraysCls, mid, result);
  return resultList;
}

/* Throws IllegalArgumentException if at least one the diagnostic command
 * passed in argument is not supported by the JVM
 */
JNIEXPORT jobjectArray JNICALL
Java_sun_management_HotSpotDiagnostic_getDiagnosticCommandInfo0
(JNIEnv *env, jobject dummy, jobjectArray commands)
{
  int i;
  jclass dcmdInfoCls;
  jobject result;
  jobjectArray args;
  jobject obj;

  if (commands == NULL) {
    JNU_ThrowNullPointerException(env, "Invalid String Array");
    return NULL;
  }
  if ((jmm_version > JMM_VERSION_1_2_1)
      || (jmm_version == JMM_VERSION_1_2 && ((jmm_version&0xFF)>=2))) {
    jsize num_commands = (*env)->GetArrayLength(env, commands);
    dcmdInfo* dcmd_info_array = (dcmdInfo*) malloc(num_commands *
                                                   sizeof(dcmdInfo));
    if (dcmd_info_array == NULL) {
      JNU_ThrowOutOfMemoryError(env, NULL);
    }
    jmm_interface->GetDiagnosticCommandInfo(env, commands, dcmd_info_array);
    dcmdInfoCls = (*env)->FindClass(env,
                                    "com/sun/management/DiagnosticCommandInfo");
    result = (*env)->NewObjectArray(env, num_commands, dcmdInfoCls, NULL);
    if (result == NULL) {
      free(dcmd_info_array);
      JNU_ThrowOutOfMemoryError(env, 0);
    }
    for (i=0; i<num_commands; i++) {
      args = getDiagnosticCommandArgumentInfoArray(env,
                                                   (*env)->GetObjectArrayElement(env,commands,i),
                                                   dcmd_info_array[i].num_arguments);
      if (args == NULL) {
        free(dcmd_info_array);
        JNU_ThrowOutOfMemoryError(env, 0);
      }
      obj = JNU_NewObjectByName(env,
                                "com/sun/management/DiagnosticCommandInfo",
                                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZLjava/util/List;)V",
                                (*env)->NewStringUTF(env,dcmd_info_array[i].name),
                                (*env)->NewStringUTF(env,dcmd_info_array[i].description),
                                (*env)->NewStringUTF(env,dcmd_info_array[i].impact),
                                dcmd_info_array[i].enabled,
                                args);
      if (obj == NULL) {
        free(dcmd_info_array);
        JNU_ThrowOutOfMemoryError(env, 0);
      }
      (*env)->SetObjectArrayElement(env, result, i, obj);
    }
    free(dcmd_info_array);
    return result;
  }
  JNU_ThrowByName(env, "java/lang/UnsupportedOperationException",
                  "Diagnostic commands are not supported by this VM");
}

/* Throws IllegalArgumentException if the diagnostic command
 * passed in argument is not supported by the JVM
 */
JNIEXPORT jstring JNICALL
Java_sun_management_HotSpotDiagnostic_executeDiagnosticCommand0
(JNIEnv *env, jobject dummy, jstring command) {
  if((jmm_version > JMM_VERSION_1_2_1 )
     || (jmm_version == JMM_VERSION_1_2 && ((jmm_version&0xFF)>=2))) {
    return jmm_interface->ExecuteDiagnosticCommand(env, command);
  }
  JNU_ThrowByName(env, "java/lang/UnsupportedOperationException",
                  "Diagnostic commands are not supported by this VM");
}

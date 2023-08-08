/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 */

#include <jni.h>

/*
 * Class:     NullJNIWeak
 * Method:    newWeakGlobalRef
 * Signature: (Ljava/lang/Object;)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL
Java_NullJNIWeak_newWeakGlobalRef(JNIEnv *env, jclass clazz, jobject obj) {
  return (*env)->NewWeakGlobalRef(env, obj);
}

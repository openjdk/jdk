#include <stdlib.h>
#include <jni.h>

#include "jlong.h"

JNIEXPORT void xor_op(jbyte *restrict src, jbyte *restrict dst, jint len) {
    for (int i = 0; i < len; ++i) {
        dst[i] ^= src[i];
    }
}

/*
 * Class:     com_oracle_jnitest_GetArrayCriticalXorOpImpl
 * Method:    xor
 * Signature: ([BI[BII)V
 */
JNIEXPORT void JNICALL Java_org_openjdk_bench_java_lang_foreign_xor_GetArrayCriticalXorOpImpl_xor
  (JNIEnv *env, jobject obj, jbyteArray src, jint sOff, jbyteArray dst, jint dOff, jint len) {
    jbyte *sbuf = NULL;
    jbyte *dbuf = NULL;
    jboolean sIsCopy = JNI_FALSE;
    jboolean dIsCopy = JNI_FALSE;

    sbuf = (*env)->GetPrimitiveArrayCritical(env, src, &sIsCopy);
    dbuf = (*env)->GetPrimitiveArrayCritical(env, dst, &dIsCopy);
    xor_op(&sbuf[sOff], &dbuf[dOff], len);
    (*env)->ReleasePrimitiveArrayCritical(env, dst, dbuf, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, src, sbuf, JNI_ABORT);
}

/*
 * Class:     com_oracle_jnitest_GetArrayElementsXorOpImpl
 * Method:    xor
 * Signature: ([BI[BII)V
 */
JNIEXPORT void JNICALL Java_org_openjdk_bench_java_lang_foreign_xor_GetArrayElementsXorOpImpl_xor
  (JNIEnv *env, jobject obj, jbyteArray src, jint sOff, jbyteArray dst, jint dOff, jint len) {
    jbyte *sbuf = NULL;
    jbyte *dbuf = NULL;
    jboolean sIsCopy = JNI_FALSE;
    jboolean dIsCopy = JNI_FALSE;

    sbuf = (*env)->GetByteArrayElements(env, src, &sIsCopy);
    dbuf = (*env)->GetByteArrayElements(env, dst, &dIsCopy);
    xor_op(&sbuf[sOff], &dbuf[dOff], len);
    (*env)->ReleaseByteArrayElements(env, dst, dbuf, 0);
    (*env)->ReleaseByteArrayElements(env, src, sbuf, JNI_ABORT);
}

/*
 * Class:     com_oracle_jnitest_GetArrayRegionXorOpImpl
 * Method:    xor
 * Signature: ([BI[BII)V
 */
JNIEXPORT void JNICALL Java_org_openjdk_bench_java_lang_foreign_xor_GetArrayRegionXorOpImpl_xor
  (JNIEnv *env, jobject obj, jbyteArray src, jint sOff, jbyteArray dst, jint dOff, jint len) {
    jbyte *sbuf = NULL;
    jbyte *dbuf = NULL;

    sbuf = malloc(len);
    dbuf = malloc(len);

    (*env)->GetByteArrayRegion(env, src, sOff, len, sbuf);
    (*env)->GetByteArrayRegion(env, dst, dOff, len, dbuf);
    xor_op(sbuf, dbuf, len);
    (*env)->SetByteArrayRegion(env, dst, dOff, len, dbuf);

    free(dbuf);
    free(sbuf);
}

JNIEXPORT void JNICALL Java_org_openjdk_bench_java_lang_foreign_xor_GetArrayUnsafeXorOpImpl_xorOp
  (JNIEnv *env, jobject obj, jlong src, jlong dst, jint len) {
    jbyte *sbuf = (jbyte*)jlong_to_ptr(src);
    jbyte *dbuf = (jbyte*)jlong_to_ptr(dst);
    xor_op(sbuf, dbuf, len);
}

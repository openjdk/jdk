#include <stdlib.h>
#include <jni.h>

void xor_op(jbyte *restrict src, jbyte *restrict dst, jint len);

/*
 * Class:     com_oracle_jnitest_GetArrayCriticalXorOpImpl
 * Method:    xor
 * Signature: ([BI[BII)V
 */
JNIEXPORT void JNICALL Java_org_openjdk_bench_java_lang_foreign_xor_GetArrayCriticalXorOpImpl_xor
  (JNIEnv *env, jobject obj, jbyteArray src, jint sOff, jbyteArray dst, jint dOff, jint len)
{
    jbyte *sbuf = NULL;
    jbyte *dbuf = NULL;
    jboolean sIsCopy = JNI_FALSE;
    jboolean dIsCopy = JNI_FALSE;

    sbuf = (*env)->GetPrimitiveArrayCritical(env, src, &sIsCopy);
    dbuf = (*env)->GetPrimitiveArrayCritical(env, dst, &dIsCopy);
    xor_op(&sbuf[sOff], &dbuf[dOff], len);
    (*env)->ReleasePrimitiveArrayCritical(env, dst, dbuf, 0);
    (*env)->ReleasePrimitiveArrayCritical(env, src, sbuf, JNI_ABORT);
    if (sIsCopy) {
        fprintf(stderr, "SRC is copy - GetPrimitiveArrayCritical\n");
        fflush(stderr);
    }
    if (dIsCopy) {
        fprintf(stderr, "DST is copy - GetPrimitiveArrayCritical\n");
        fflush(stderr);
    }
}

/*
 * Class:     com_oracle_jnitest_GetArrayElementsXorOpImpl
 * Method:    xor
 * Signature: ([BI[BII)V
 */
JNIEXPORT void JNICALL Java_org_openjdk_bench_java_lang_foreign_xor_GetArrayElementsXorOpImpl_xor
  (JNIEnv *env, jobject obj, jbyteArray src, jint sOff, jbyteArray dst, jint dOff, jint len)
{
    jbyte *sbuf = NULL;
    jbyte *dbuf = NULL;
    jboolean sIsCopy = JNI_FALSE;
    jboolean dIsCopy = JNI_FALSE;

    sbuf = (*env)->GetByteArrayElements(env, src, &sIsCopy);
    dbuf = (*env)->GetByteArrayElements(env, dst, &dIsCopy);
    xor_op(&sbuf[sOff], &dbuf[dOff], len);
    (*env)->ReleaseByteArrayElements(env, dst, dbuf, 0);
    (*env)->ReleaseByteArrayElements(env, src, sbuf, JNI_ABORT);
    if (sIsCopy) {
        //fprintf(stderr, "SRC is copy - GetByteArrayElements\n");
        fflush(stderr);
    }
    if (dIsCopy) {
        //fprintf(stderr, "DST is copy - GetByteArrayElements\n");
        fflush(stderr);
    }
}

/*
 * Class:     com_oracle_jnitest_GetArrayRegionXorOpImpl
 * Method:    xor
 * Signature: ([BI[BII)V
 */
JNIEXPORT void JNICALL Java_org_openjdk_bench_java_lang_foreign_xor_GetArrayRegionXorOpImpl_xor
  (JNIEnv *env, jobject obj, jbyteArray src, jint sOff, jbyteArray dst, jint dOff, jint len)
{
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

JNIEXPORT void JNICALL Java_org_openjdk_bench_java_lang_foreign_xor_GetArrayCriticalXorOpImpl_copy
  (JNIEnv *env, jobject obj, jint count, jbyteArray src, jint sOff, jbyteArray dst, jint dOff, jint len)
{
    jbyte *sbuf = NULL;
    jbyte *dbuf = NULL;
    jboolean sIsCopy = JNI_FALSE;
    jboolean dIsCopy = JNI_FALSE;

    for (int i = 0; i < count; i++) {
        sbuf = (*env)->GetPrimitiveArrayCritical(env, src, &sIsCopy);
        dbuf = (*env)->GetPrimitiveArrayCritical(env, dst, &dIsCopy);
        (*env)->ReleasePrimitiveArrayCritical(env, dst, dbuf, JNI_ABORT);
        (*env)->ReleasePrimitiveArrayCritical(env, src, sbuf, 0);
        if (sIsCopy) {
            fprintf(stderr, "SRC is copy - GetPrimitiveArrayCritical\n");
            fflush(stderr);
        }
        if (dIsCopy) {
            fprintf(stderr, "DST is copy - GetPrimitiveArrayCritical\n");
            fflush(stderr);
        }
    }
}

JNIEXPORT void JNICALL Java_org_openjdk_bench_java_lang_foreign_xor_GetArrayElementsXorOpImpl_copy
  (JNIEnv *env, jobject obj, jint count, jbyteArray src, jint sOff, jbyteArray dst, jint dOff, jint len)
{
    jbyte *sbuf = NULL;
    jbyte *dbuf = NULL;
    jboolean sIsCopy = JNI_FALSE;
    jboolean dIsCopy = JNI_FALSE;

    for (int i = 0; i < count; i++) {
        dbuf = (*env)->GetByteArrayElements(env, dst, &dIsCopy);
        dbuf = (*env)->GetByteArrayElements(env, src, &sIsCopy);
        // (*env)->ReleaseByteArrayElements(env, dst, dbuf, JNI_ABORT);
        (*env)->ReleaseByteArrayElements(env, dst, dbuf, 0);
        if (sIsCopy) {
            //fprintf(stderr, "SRC is copy - GetByteArrayElements\n");
            // fflush(stderr);
        }
        if (dIsCopy) {
            //fprintf(stderr, "DST is copy - GetByteArrayElements\n");
            // fflush(stderr);
        }
    }
}

JNIEXPORT void JNICALL Java_org_openjdk_bench_java_lang_foreign_xor_GetArrayRegionXorOpImpl_copy
  (JNIEnv *env, jobject obj, jint count, jbyteArray src, jint sOff, jbyteArray dst, jint dOff, jint len)
{
    jbyte *sbuf = malloc(len);
    jbyte *dbuf = malloc(len);

    for (int i = 0; i < count; i++) {
        (*env)->GetByteArrayRegion(env, src, sOff, len, sbuf);
        (*env)->GetByteArrayRegion(env, dst, dOff, len, dbuf);
        (*env)->SetByteArrayRegion(env, dst, dOff, len, sbuf);
    }

    free(dbuf);
    free(sbuf);
}

__attribute__((visibility("default")))
void xor_op(jbyte *restrict src, jbyte *restrict dst, jint len)
{
    jint i;

    for (i = 0; i < len; ++i) {
        dst[i] ^= src[i];
    }
}


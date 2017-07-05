/*
 * Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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

#include "sun_security_jgss_wrapper_GSSLibStub.h"
#include "NativeUtil.h"
#include "NativeFunc.h"
#include "jlong.h"

/* Constants for indicating what type of info is needed for inqueries */
const int TYPE_CRED_NAME = 10;
const int TYPE_CRED_TIME = 11;
const int TYPE_CRED_USAGE = 12;

/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    init
 * Signature: (Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_init(JNIEnv *env,
                                               jclass jcls,
                                               jstring jlibName) {
    const char *libName;
    char *error = NULL;

    if (jlibName == NULL) {
        debug(env, "[GSSLibStub_init] GSS lib name is NULL");
        return JNI_FALSE;
    }

    libName = (*env)->GetStringUTFChars(env, jlibName, NULL);
    sprintf(debugBuf, "[GSSLibStub_init] libName=%s", libName);
    debug(env, debugBuf);

    /* initialize global function table */
    error = loadNative(libName);
    (*env)->ReleaseStringUTFChars(env, jlibName, libName);

    if (error == NULL) {
        return JNI_TRUE;
    } else {
        debug(env, error);
        return JNI_FALSE;
    }
}

/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    getMechPtr
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_getMechPtr(JNIEnv *env,
                                                     jclass jcls,
                                                     jbyteArray jbytes) {
  gss_OID cOid;
  unsigned int i, len;
  jbyte* bytes;
  jthrowable gssEx;
  jboolean found;

  if (jbytes != NULL) {
    found = JNI_FALSE;
    len = (unsigned int)((*env)->GetArrayLength(env, jbytes) - 2);
    bytes = (*env)->GetByteArrayElements(env, jbytes, NULL);
    if (bytes != NULL) {
      for (i = 0; i < ftab->mechs->count; i++) {
        cOid = &(ftab->mechs->elements[i]);
        if (len == cOid->length &&
            (memcmp(cOid->elements, (bytes + 2), len) == 0)) {
          // Found a match
          found = JNI_TRUE;
          break;
        }
      }
      (*env)->ReleaseByteArrayElements(env, jbytes, bytes, 0);
    }
    if (found != JNI_TRUE) {
      checkStatus(env, NULL, GSS_S_BAD_MECH, 0, "[GSSLibStub_getMechPtr]");
      return ptr_to_jlong(NULL);
    } else return ptr_to_jlong(cOid);
  } else return ptr_to_jlong(GSS_C_NO_OID);
}


/*
 * Utility routine which creates a gss_channel_bindings_t structure
 * using the specified org.ietf.jgss.ChannelBinding object.
 */
gss_channel_bindings_t getGSSCB(JNIEnv *env, jobject jcb) {
  gss_channel_bindings_t cb;
  jobject jinetAddr;
  jbyteArray value;

  if (jcb == NULL) {
    return GSS_C_NO_CHANNEL_BINDINGS;
  }
  cb = malloc(sizeof(struct gss_channel_bindings_struct));
  /* set up initiator address */
  jinetAddr =
    (*env)->CallObjectMethod(env, jcb,
                             MID_ChannelBinding_getInitiatorAddr);
  if (jinetAddr != NULL) {
    cb->initiator_addrtype = GSS_C_AF_INET;
    value = (*env)->CallObjectMethod(env, jinetAddr,
                                     MID_InetAddress_getAddr);
    initGSSBuffer(env, value, &(cb->initiator_address));
  } else {
    cb->initiator_addrtype = GSS_C_AF_NULLADDR;
    cb->initiator_address.length = 0;
    cb->initiator_address.value = NULL;
  }
  /* set up acceptor address */
  jinetAddr =
    (*env)->CallObjectMethod(env, jcb,
                             MID_ChannelBinding_getAcceptorAddr);
  if (jinetAddr != NULL) {
    cb->acceptor_addrtype = GSS_C_AF_INET;
    value = (*env)->CallObjectMethod(env, jinetAddr,
                                     MID_InetAddress_getAddr);
    initGSSBuffer(env, value, &(cb->acceptor_address));
  } else {
    cb->acceptor_addrtype = GSS_C_AF_NULLADDR;
    cb->acceptor_address.length = 0;
    cb->acceptor_address.value = NULL;
  }
  /* set up application data */
  value = (*env)->CallObjectMethod(env, jcb,
                                   MID_ChannelBinding_getAppData);
  if (value != NULL) {
    initGSSBuffer(env, value, &(cb->application_data));
  } else {
    cb->application_data.length = 0;
    cb->application_data.value = NULL;
  }
  return cb;
}

/*
 * Utility routine which releases the specified gss_channel_bindings_t
 * structure.
 */
void releaseGSSCB(JNIEnv *env, jobject jcb, gss_channel_bindings_t cb) {
  jobject jinetAddr;
  jbyteArray value;

  if (cb == GSS_C_NO_CHANNEL_BINDINGS) return;
  /* release initiator address */
  if (cb->initiator_addrtype != GSS_C_AF_NULLADDR) {
    jinetAddr =
      (*env)->CallObjectMethod(env, jcb,
                               MID_ChannelBinding_getInitiatorAddr);
    value = (*env)->CallObjectMethod(env, jinetAddr,
                                     MID_InetAddress_getAddr);
    resetGSSBuffer(env, value, &(cb->initiator_address));
  }
  /* release acceptor address */
  if (cb->acceptor_addrtype != GSS_C_AF_NULLADDR) {
    jinetAddr =
      (*env)->CallObjectMethod(env, jcb,
                               MID_ChannelBinding_getAcceptorAddr);
    value = (*env)->CallObjectMethod(env, jinetAddr,
                                     MID_InetAddress_getAddr);
    resetGSSBuffer(env, value, &(cb->acceptor_address));
  }
  /* release application data */
  if (cb->application_data.length != 0) {
    value = (*env)->CallObjectMethod(env, jcb,
                                     MID_ChannelBinding_getAppData);
    resetGSSBuffer(env, value, &(cb->application_data));
  }
  free(cb);
}

/*
 * Utility routine for storing the supplementary information
 * into the specified org.ietf.jgss.MessageProp object.
 */
void setSupplementaryInfo(JNIEnv *env, jobject jstub, jobject jprop,
                          int suppInfo, int minor) {
  jboolean isDuplicate, isOld, isUnseq, hasGap;
  jstring minorMsg;

  if (suppInfo != GSS_S_COMPLETE) {
    isDuplicate = ((suppInfo & GSS_S_DUPLICATE_TOKEN) != 0);
    isOld = ((suppInfo & GSS_S_OLD_TOKEN) != 0);
    isUnseq = ((suppInfo & GSS_S_UNSEQ_TOKEN) != 0);
    hasGap = ((suppInfo & GSS_S_GAP_TOKEN) != 0);
    minorMsg = getMinorMessage(env, jstub, minor);
    (*env)->CallVoidMethod(env, jprop, MID_MessageProp_setSupplementaryStates,
                           isDuplicate, isOld, isUnseq, hasGap, minor,
                           minorMsg);
  }
}

/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    indicateMechs
 * Signature: ()[Lorg/ietf/jgss/Oid;
 */
JNIEXPORT jobjectArray JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_indicateMechs(JNIEnv *env,
                                                        jclass jcls)
{
  if (ftab->mechs != NULL && ftab->mechs != GSS_C_NO_OID_SET) {
    return getJavaOIDArray(env, ftab->mechs);
  } else return NULL;
}

/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    inquireNamesForMech
 * Signature: ()[Lorg/ietf/jgss/Oid;
 */
JNIEXPORT jobjectArray JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_inquireNamesForMech(JNIEnv *env,
                                                              jobject jobj)
{
  OM_uint32 minor, major;
  gss_OID mech;
  gss_OID_set nameTypes;
  jobjectArray result;

  if (ftab->inquireNamesForMech != NULL) {

  mech = (gss_OID)jlong_to_ptr((*env)->GetLongField(env, jobj, FID_GSSLibStub_pMech));
  nameTypes = GSS_C_NO_OID_SET;

  /* gss_inquire_names_for_mech(...) => N/A */
  major = (*ftab->inquireNamesForMech)(&minor, mech, &nameTypes);

  result = getJavaOIDArray(env, nameTypes);

  /* release intermediate buffers */
  deleteGSSOIDSet(nameTypes);

  checkStatus(env, jobj, major, minor, "[GSSLibStub_inquireNamesForMech]");
  return result;
  } else return NULL;
}

/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    releaseName
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_releaseName(JNIEnv *env,
                                                      jobject jobj,
                                                      jlong pName)
{
  OM_uint32 minor, major;
  gss_name_t nameHdl;

  nameHdl = (gss_name_t) jlong_to_ptr(pName);

  sprintf(debugBuf, "[GSSLibStub_releaseName] %ld", (long) pName);
  debug(env, debugBuf);

  if (nameHdl != GSS_C_NO_NAME) {
    /* gss_release_name(...) => GSS_S_BAD_NAME */
    major = (*ftab->releaseName)(&minor, &nameHdl);
    checkStatus(env, jobj, major, minor, "[GSSLibStub_releaseName]");
  }
}

/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    importName
 * Signature: ([BLorg/ietf/jgss/Oid;)J
 */
JNIEXPORT jlong JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_importName(JNIEnv *env,
                                                     jobject jobj,
                                                     jbyteArray jnameVal,
                                                     jobject jnameType)
{
  OM_uint32 minor, major;
  gss_buffer_desc nameVal;
  gss_OID nameType;
  gss_name_t nameHdl;

  debug(env, "[GSSLibStub_importName]");

  initGSSBuffer(env, jnameVal, &nameVal);
  nameType = newGSSOID(env, jnameType);
  nameHdl = GSS_C_NO_NAME;

  /* gss_import_name(...) => GSS_S_BAD_NAMETYPE, GSS_S_BAD_NAME,
     GSS_S_BAD_MECH */
  major = (*ftab->importName)(&minor, &nameVal, nameType, &nameHdl);

  sprintf(debugBuf, "[GSSLibStub_importName] %ld", (long) nameHdl);
  debug(env, debugBuf);

  /* release intermediate buffers */
  deleteGSSOID(nameType);
  resetGSSBuffer(env, jnameVal, &nameVal);

  checkStatus(env, jobj, major, minor, "[GSSLibStub_importName]");
  return ptr_to_jlong(nameHdl);
}


/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    compareName
 * Signature: (JJ)Z
 */
JNIEXPORT jboolean JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_compareName(JNIEnv *env,
                                                      jobject jobj,
                                                      jlong pName1,
                                                      jlong pName2)
{
  OM_uint32 minor, major;
  gss_name_t nameHdl1, nameHdl2;
  int isEqual;

  isEqual = 0;
  nameHdl1 = (gss_name_t) jlong_to_ptr(pName1);
  nameHdl2 = (gss_name_t) jlong_to_ptr(pName2);

  sprintf(debugBuf, "[GSSLibStub_compareName] %ld %ld", (long) pName1,
    (long) pName2);
  debug(env, debugBuf);

  if ((nameHdl1 != GSS_C_NO_NAME) && (nameHdl2 != GSS_C_NO_NAME)) {

    /* gss_compare_name(...) => GSS_S_BAD_NAMETYPE, GSS_S_BAD_NAME(!) */
    major = (*ftab->compareName)(&minor, nameHdl1, nameHdl2, &isEqual);

    checkStatus(env, jobj, major, minor, "[GSSLibStub_compareName]");
  }
  return (isEqual != 0);
}

/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    canonicalizeName
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_canonicalizeName(JNIEnv *env,
                                                           jobject jobj,
                                                           jlong pName)
{
  OM_uint32 minor, major;
  gss_name_t nameHdl, mnNameHdl;
  gss_OID mech;

  nameHdl = (gss_name_t) jlong_to_ptr(pName);
  sprintf(debugBuf, "[GSSLibStub_canonicalizeName] %ld", (long) pName);
  debug(env, debugBuf);

  if (nameHdl != GSS_C_NO_NAME) {
    mech = (gss_OID) jlong_to_ptr((*env)->GetLongField(env, jobj, FID_GSSLibStub_pMech));
    mnNameHdl = GSS_C_NO_NAME;

    /* gss_canonicalize_name(...) may return GSS_S_BAD_NAMETYPE,
       GSS_S_BAD_NAME, GSS_S_BAD_MECH */
    major = (*ftab->canonicalizeName)(&minor, nameHdl, mech, &mnNameHdl);

    sprintf(debugBuf, "[GSSLibStub_canonicalizeName] MN=%ld",
        (long)mnNameHdl);
    debug(env, debugBuf);

    /* release intermediate buffers */

    checkStatus(env, jobj, major, minor, "[GSSLibStub_canonicalizeName]");
  } else mnNameHdl = GSS_C_NO_NAME;

  return ptr_to_jlong(mnNameHdl);
}

/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    exportName
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_exportName(JNIEnv *env,
                                                     jobject jobj,
                                                     jlong pName) {
  OM_uint32 minor, major;
  gss_name_t nameHdl, mNameHdl;
  gss_buffer_desc outBuf;
  jbyteArray jresult;

  nameHdl = (gss_name_t) jlong_to_ptr(pName);
  sprintf(debugBuf, "[GSSLibStub_exportName] %ld", (long) pName);
  debug(env, debugBuf);

  /* gss_export_name(...) => GSS_S_NAME_NOT_MN, GSS_S_BAD_NAMETYPE,
     GSS_S_BAD_NAME */
  major = (*ftab->exportName)(&minor, nameHdl, &outBuf);

  /* canonicalize the internal name to MN and retry */
  if (major == GSS_S_NAME_NOT_MN) {
    debug(env, "[GSSLibStub_exportName] canonicalize and re-try");

    mNameHdl = (gss_name_t)jlong_to_ptr(
        Java_sun_security_jgss_wrapper_GSSLibStub_canonicalizeName
                                        (env, jobj, pName));
    /* return immediately if an exception has occurred */
    if ((*env)->ExceptionCheck(env)) {
      return NULL;
    }
    major = (*ftab->exportName)(&minor, mNameHdl, &outBuf);
    Java_sun_security_jgss_wrapper_GSSLibStub_releaseName
                                        (env, jobj, ptr_to_jlong(mNameHdl));
    /* return immediately if an exception has occurred */
    if ((*env)->ExceptionCheck(env)) {
      return NULL;
    }
  }

  /* release intermediate buffers */
  jresult = getJavaBuffer(env, &outBuf);

  checkStatus(env, jobj, major, minor, "[GSSLibStub_exportName]");
  return jresult;
}

/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    displayName
 * Signature: (J)[Ljava/lang/Object;
 */
JNIEXPORT jobjectArray JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_displayName(JNIEnv *env,
                                                      jobject jobj,
                                                      jlong pName) {
  OM_uint32 minor, major;
  gss_name_t nameHdl;
  gss_buffer_desc outNameBuf;
  gss_OID outNameType;
  jstring jname;
  jobject jtype;
  jobjectArray jresult;

  nameHdl = (gss_name_t) jlong_to_ptr(pName);
  sprintf(debugBuf, "[GSSLibStub_displayName] %ld", (long) pName);
  debug(env, debugBuf);

  if (nameHdl == GSS_C_NO_NAME) {
    checkStatus(env, jobj, GSS_S_BAD_NAME, 0, "[GSSLibStub_displayName]");
    return NULL;
  }

  /* gss_display_name(...) => GSS_S_BAD_NAME */
  major = (*ftab->displayName)(&minor, nameHdl, &outNameBuf, &outNameType);

  /* release intermediate buffers */
  jname = getJavaString(env, &outNameBuf);

  jtype = getJavaOID(env, outNameType);
  jresult = (*env)->NewObjectArray(env, 2, CLS_Object, NULL);

  /* return immediately if an exception has occurred */
  if ((*env)->ExceptionCheck(env)) {
    return NULL;
  }

  (*env)->SetObjectArrayElement(env, jresult, 0, jname);
  (*env)->SetObjectArrayElement(env, jresult, 1, jtype);

  checkStatus(env, jobj, major, minor, "[GSSLibStub_displayName]");
  return jresult;
}

/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    acquireCred
 * Signature: (JII)J
 */
JNIEXPORT jlong JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_acquireCred(JNIEnv *env,
                                                      jobject jobj,
                                                      jlong pName,
                                                      jint reqTime,
                                                      jint usage)
{
  OM_uint32 minor, major;
  gss_OID mech;
  gss_OID_set mechs;
  gss_cred_usage_t credUsage;
  gss_name_t nameHdl;
  gss_cred_id_t credHdl;

  debug(env, "[GSSLibStub_acquireCred]");


  mech = (gss_OID) jlong_to_ptr((*env)->GetLongField(env, jobj, FID_GSSLibStub_pMech));
  mechs = newGSSOIDSet(env, mech);
  credUsage = (gss_cred_usage_t) usage;
  nameHdl = (gss_name_t) jlong_to_ptr(pName);
  credHdl = GSS_C_NO_CREDENTIAL;

  sprintf(debugBuf, "[GSSLibStub_acquireCred] pName=%ld, usage=%d",
    (long) pName, usage);
  debug(env, debugBuf);

  /* gss_acquire_cred(...) => GSS_S_BAD_MECH, GSS_S_BAD_NAMETYPE,
     GSS_S_BAD_NAME, GSS_S_CREDENTIALS_EXPIRED, GSS_S_NO_CRED */
  major =
    (*ftab->acquireCred)(&minor, nameHdl, reqTime, mechs,
                     credUsage, &credHdl, NULL, NULL);
  /* release intermediate buffers */
  deleteGSSOIDSet(mechs);

  sprintf(debugBuf, "[GSSLibStub_acquireCred] pCred=%ld", (long) credHdl);
  debug(env, debugBuf);

  checkStatus(env, jobj, major, minor, "[GSSLibStub_acquireCred]");
  return ptr_to_jlong(credHdl);
}

/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    releaseCred
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_releaseCred(JNIEnv *env,
                                                      jobject jobj,
                                                      jlong pCred)
{
  OM_uint32 minor, major;
  gss_cred_id_t credHdl;

  credHdl = (gss_cred_id_t) jlong_to_ptr(pCred);

  sprintf(debugBuf, "[GSSLibStub_releaseCred] %ld", (long int)pCred);
  debug(env, debugBuf);

  if (credHdl != GSS_C_NO_CREDENTIAL) {

    /* gss_release_cred(...) => GSS_S_NO_CRED(!) */
    major = (*ftab->releaseCred)(&minor, &credHdl);

    checkStatus(env, jobj, major, minor, "[GSSLibStub_releaseCred]");
  }
  return ptr_to_jlong(credHdl);
}

/*
 * Utility routine for obtaining info about a credential.
 */
void inquireCred(JNIEnv *env, jobject jobj, gss_cred_id_t pCred,
                 jint type, void *result) {
  OM_uint32 minor, major=GSS_C_QOP_DEFAULT;
  OM_uint32 routineErr;
  gss_cred_id_t credHdl;

  credHdl = pCred;

  sprintf(debugBuf, "[gss_inquire_cred] %ld", (long) pCred);
  debug(env, debugBuf);

  /* gss_inquire_cred(...) => GSS_S_DEFECTIVE_CREDENTIAL(!),
     GSS_S_CREDENTIALS_EXPIRED(!), GSS_S_NO_CRED(!) */
  if (type == TYPE_CRED_NAME) {
    major = (*ftab->inquireCred)(&minor, credHdl, result, NULL, NULL, NULL);
  } else if (type == TYPE_CRED_TIME) {
    major = (*ftab->inquireCred)(&minor, credHdl, NULL, result, NULL, NULL);
  } else if (type == TYPE_CRED_USAGE) {
    major = (*ftab->inquireCred)(&minor, credHdl, NULL, NULL, result, NULL);
  }

  /* release intermediate buffers */

  routineErr = GSS_ROUTINE_ERROR(major);
  if (routineErr == GSS_S_CREDENTIALS_EXPIRED) {
    /* ignore GSS_S_CREDENTIALS_EXPIRED for query  */
    major = GSS_CALLING_ERROR(major) |
      GSS_SUPPLEMENTARY_INFO(major);
  } else if (routineErr == GSS_S_NO_CRED) {
    /* twik since Java API throws BAD_MECH instead of NO_CRED */
    major = GSS_CALLING_ERROR(major) |
      GSS_S_BAD_MECH  | GSS_SUPPLEMENTARY_INFO(major);
  }
  checkStatus(env, jobj, major, minor, "[gss_inquire_cred]");
}

/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    getCredName
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_getCredName(JNIEnv *env,
                                                      jobject jobj,
                                                      jlong pCred)
{
  gss_name_t nameHdl;
  gss_cred_id_t credHdl;

  credHdl = (gss_cred_id_t) jlong_to_ptr(pCred);

  sprintf(debugBuf, "[GSSLibStub_getCredName] %ld", (long int)pCred);
  debug(env, debugBuf);

  nameHdl = GSS_C_NO_NAME;
  inquireCred(env, jobj, credHdl, TYPE_CRED_NAME, &nameHdl);

  /* return immediately if an exception has occurred */
  if ((*env)->ExceptionCheck(env)) {
    return 0;
  }

  sprintf(debugBuf, "[GSSLibStub_getCredName] pName=%ld", (long) nameHdl);
  debug(env, debugBuf);

  return ptr_to_jlong(nameHdl);
}

/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    getCredTime
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_getCredTime(JNIEnv *env,
                                                      jobject jobj,
                                                      jlong pCred)
{
  gss_cred_id_t credHdl;
  OM_uint32 lifetime;

  credHdl = (gss_cred_id_t) jlong_to_ptr(pCred);

  sprintf(debugBuf, "[GSSLibStub_getCredTime] %ld", (long int)pCred);
  debug(env, debugBuf);

  lifetime = 0;
  inquireCred(env, jobj, credHdl, TYPE_CRED_TIME, &lifetime);

  /* return immediately if an exception has occurred */
  if ((*env)->ExceptionCheck(env)) {
    return 0;
  }
  return getJavaTime(lifetime);
}

/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    getCredUsage
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_getCredUsage(JNIEnv *env,
                                                       jobject jobj,
                                                       jlong pCred)
{
  gss_cred_usage_t usage;
  gss_cred_id_t credHdl;

  credHdl = (gss_cred_id_t) jlong_to_ptr(pCred);

  sprintf(debugBuf, "[GSSLibStub_getCredUsage] %ld", (long int)pCred);
  debug(env, debugBuf);

  inquireCred(env, jobj, credHdl, TYPE_CRED_USAGE, &usage);

  /* return immediately if an exception has occurred */
  if ((*env)->ExceptionCheck(env)) {
    return -1;
  }
  return (jint) usage;
}
/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    importContext
 * Signature: ([B)Lsun/security/jgss/wrapper/NativeGSSContext;
 */
JNIEXPORT jobject JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_importContext(JNIEnv *env,
                                                        jobject jobj,
                                                        jbyteArray jctxtToken)
{
  OM_uint32 minor, major;
  gss_buffer_desc ctxtToken;
  gss_ctx_id_t contextHdl;
  gss_OID mech, mech2;

  debug(env, "[GSSLibStub_importContext]");

  contextHdl = GSS_C_NO_CONTEXT;
  initGSSBuffer(env, jctxtToken, &ctxtToken);

  /* gss_import_sec_context(...) => GSS_S_NO_CONTEXT, GSS_S_DEFECTIVE_TOKEN,
     GSS_S_UNAVAILABLE, GSS_S_UNAUTHORIZED */
  major = (*ftab->importSecContext)(&minor, &ctxtToken, &contextHdl);

  sprintf(debugBuf, "[GSSLibStub_importContext] pContext=%ld",
                                        (long) contextHdl);
  debug(env, debugBuf);

  /* release intermediate buffers */
  resetGSSBuffer(env, jctxtToken, &ctxtToken);

  checkStatus(env, jobj, major, minor, "[GSSLibStub_importContext]");
  /* return immediately if an exception has occurred */
  if ((*env)->ExceptionCheck(env)) {
    return NULL;
  }

  /* now that the context has been imported, proceed to find out
     its mech */
  major = (*ftab->inquireContext)(&minor, contextHdl, NULL, NULL,
                              NULL, &mech, NULL, NULL, NULL);

  checkStatus(env, jobj, major, minor, "[GSSLibStub_importContext] getMech");
  /* return immediately if an exception has occurred */
  if ((*env)->ExceptionCheck(env)) {
    return NULL;
  }

  mech2 = (gss_OID) jlong_to_ptr((*env)->GetLongField(env, jobj, FID_GSSLibStub_pMech));

  if (sameMech(env, mech, mech2) == JNI_TRUE) {
    /* mech match - return the context object */
    return (*env)->NewObject(env, CLS_NativeGSSContext,
                                 MID_NativeGSSContext_ctor,
                                 ptr_to_jlong(contextHdl), jobj);
  } else {
    /* mech mismatch - clean up then return null */
    major = (*ftab->deleteSecContext)(&minor, &contextHdl, GSS_C_NO_BUFFER);
    checkStatus(env, jobj, major, minor,
      "[GSSLibStub_importContext] cleanup");
    return NULL;
  }
}
/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    initContext
 * Signature: (JJLorg/ietf/jgss/ChannelBinding;[BLsun/security/jgss/wrapper/NativeGSSContext;)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_initContext(JNIEnv *env,
                                                      jobject jobj,
                                                      jlong pCred,
                                                      jlong pName,
                                                      jobject jcb,
                                                      jbyteArray jinToken,
                                                      jobject jcontextSpi)
{
  OM_uint32 minor, major;
  gss_cred_id_t credHdl ;
  gss_ctx_id_t contextHdl;
  gss_name_t targetName;
  gss_OID mech;
  OM_uint32 flags, aFlags;
  OM_uint32 time, aTime;
  gss_channel_bindings_t cb;
  gss_buffer_desc inToken;
  gss_buffer_desc outToken;
  jbyteArray jresult;
/* UNCOMMENT after SEAM bug#6287358 is backported to S10
  gss_OID aMech;
  jobject jMech;
*/
  debug(env, "[GSSLibStub_initContext]");

  credHdl = (gss_cred_id_t) jlong_to_ptr(pCred);
  contextHdl = (gss_ctx_id_t) jlong_to_ptr(
    (*env)->GetLongField(env, jcontextSpi, FID_NativeGSSContext_pContext));
  targetName = (gss_name_t) jlong_to_ptr(pName);
  mech = (gss_OID) jlong_to_ptr((*env)->GetLongField(env, jobj, FID_GSSLibStub_pMech));
  flags = (OM_uint32) (*env)->GetIntField(env, jcontextSpi,
                                          FID_NativeGSSContext_flags);
  time = getGSSTime((*env)->GetIntField(env, jcontextSpi,
                                        FID_NativeGSSContext_lifetime));
  cb = getGSSCB(env, jcb);
  initGSSBuffer(env, jinToken, &inToken);

  sprintf(debugBuf,
          "[GSSLibStub_initContext] before: pCred=%ld, pContext=%ld",
          (long)credHdl, (long)contextHdl);
  debug(env, debugBuf);

  /* gss_init_sec_context(...) => GSS_S_CONTINUE_NEEDED(!),
     GSS_S_DEFECTIVE_TOKEN, GSS_S_NO_CRED, GSS_S_DEFECTIVE_CREDENTIAL(!),
     GSS_S_CREDENTIALS_EXPIRED, GSS_S_BAD_BINDINGS, GSS_S_BAD_MIC,
     GSS_S_OLD_TOKEN, GSS_S_DUPLICATE_TOKEN, GSS_S_NO_CONTEXT(!),
     GSS_S_BAD_NAMETYPE, GSS_S_BAD_NAME(!), GSS_S_BAD_MECH */
  major = (*ftab->initSecContext)(&minor, credHdl,
                               &contextHdl, targetName, mech,
                               flags, time, cb, &inToken, NULL /*aMech*/,
                               &outToken, &aFlags, &aTime);

  sprintf(debugBuf, "[GSSLibStub_initContext] after: pContext=%ld",
          (long)contextHdl);
  debug(env, debugBuf);
  sprintf(debugBuf, "[GSSLibStub_initContext] outToken len=%ld",
          (long)outToken.length);
  debug(env, debugBuf);

  if (GSS_ERROR(major) == GSS_S_COMPLETE) {
    /* update member values if needed */
    (*env)->SetLongField(env, jcontextSpi, FID_NativeGSSContext_pContext,
                        ptr_to_jlong(contextHdl));
    (*env)->SetIntField(env, jcontextSpi, FID_NativeGSSContext_flags, aFlags);
    sprintf(debugBuf, "[GSSLibStub_initContext] set flags=0x%x", aFlags);
    debug(env, debugBuf);

    if (major == GSS_S_COMPLETE) {
      (*env)->SetIntField(env, jcontextSpi, FID_NativeGSSContext_lifetime,
                          getJavaTime(aTime));
      debug(env, "[GSSLibStub_initContext] context established");

      (*env)->SetBooleanField(env, jcontextSpi,
                              FID_NativeGSSContext_isEstablished,
                              JNI_TRUE);

/* UNCOMMENT after SEAM bug#6287358 is backported to S10
      jMech = getJavaOID(env, aMech);
      (*env)->SetObjectField(env, jcontextSpi,
                             FID_NativeGSSContext_actualMech, jMech);
*/
    } else if (major & GSS_S_CONTINUE_NEEDED) {
      debug(env, "[GSSLibStub_initContext] context not established");
      major -= GSS_S_CONTINUE_NEEDED;
    }
  }
  /* release intermediate buffers */
  releaseGSSCB(env, jcb, cb);
  resetGSSBuffer(env, jinToken, &inToken);
  jresult = getJavaBuffer(env, &outToken);

  checkStatus(env, jobj, major, minor, "[GSSLibStub_initContext]");
  return jresult;
}

/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    acceptContext
 * Signature: (JLorg/ietf/jgss/ChannelBinding;[BLsun/security/jgss/wrapper/NativeGSSContext;)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_acceptContext(JNIEnv *env,
                                                        jobject jobj,
                                                        jlong pCred,
                                                        jobject jcb,
                                                        jbyteArray jinToken,
                                                        jobject jcontextSpi)
{
  OM_uint32 minor, major;
  OM_uint32 minor2, major2;
  gss_ctx_id_t contextHdl;
  gss_cred_id_t credHdl;
  gss_buffer_desc inToken;
  gss_channel_bindings_t cb;
  gss_name_t srcName;
  gss_buffer_desc outToken;
  gss_OID aMech;
  OM_uint32 aFlags;
  OM_uint32 aTime;
  gss_cred_id_t delCred;
  jobject jsrcName=GSS_C_NO_NAME;
  jobject jdelCred;
  jobject jMech;
  jbyteArray jresult;
  jboolean setTarget;
  gss_name_t targetName;
  jobject jtargetName;

  debug(env, "[GSSLibStub_acceptContext]");

  contextHdl = (gss_ctx_id_t)jlong_to_ptr(
    (*env)->GetLongField(env, jcontextSpi, FID_NativeGSSContext_pContext));
  credHdl = (gss_cred_id_t) jlong_to_ptr(pCred);
  initGSSBuffer(env, jinToken, &inToken);
  cb = getGSSCB(env, jcb);
  srcName = GSS_C_NO_NAME;
  delCred = GSS_C_NO_CREDENTIAL;
  setTarget = (credHdl == GSS_C_NO_CREDENTIAL);
  aFlags = 0;

  sprintf(debugBuf,
          "[GSSLibStub_acceptContext] before: pCred=%ld, pContext=%ld",
          (long) credHdl, (long) contextHdl);
  debug(env, debugBuf);

  /* gss_accept_sec_context(...) => GSS_S_CONTINUE_NEEDED(!),
     GSS_S_DEFECTIVE_TOKEN, GSS_S_DEFECTIVE_CREDENTIAL(!),
     GSS_S_NO_CRED, GSS_S_CREDENTIALS_EXPIRED, GSS_S_BAD_BINDINGS,
     GSS_S_NO_CONTEXT(!), GSS_S_BAD_MIC, GSS_S_OLD_TOKEN,
     GSS_S_DUPLICATE_TOKEN, GSS_S_BAD_MECH */
  major =
    (*ftab->acceptSecContext)(&minor, &contextHdl, credHdl,
                           &inToken, cb, &srcName, &aMech, &outToken,
                           &aFlags, &aTime, &delCred);

  sprintf(debugBuf,
        "[GSSLibStub_acceptContext] after: pCred=%ld, pContext=%ld, pDelegCred=%ld",
        (long)credHdl, (long)contextHdl, (long) delCred);
  debug(env, debugBuf);

  if (GSS_ERROR(major) == GSS_S_COMPLETE) {
    /* update member values if needed */
    (*env)->SetLongField(env, jcontextSpi, FID_NativeGSSContext_pContext,
                        ptr_to_jlong(contextHdl));
    sprintf(debugBuf, "[GSSLibStub_acceptContext] set pContext=%ld",
            (long)contextHdl);
    debug(env, debugBuf);
    // WORKAROUND for a Heimdal bug
    if (delCred == GSS_C_NO_CREDENTIAL) {
        aFlags &= 0xfffffffe;
    }
    (*env)->SetIntField(env, jcontextSpi, FID_NativeGSSContext_flags, aFlags);
    sprintf(debugBuf, "[GSSLibStub_acceptContext] set flags=0x%x",
            aFlags);
    debug(env, debugBuf);
    if (setTarget) {
      major2 = (*ftab->inquireContext)(&minor2, contextHdl, NULL,
                              &targetName, NULL, NULL, NULL,
                              NULL, NULL);
      jtargetName = (*env)->NewObject(env, CLS_GSSNameElement,
                                MID_GSSNameElement_ctor,
                                ptr_to_jlong(targetName), jobj);

      /* return immediately if an exception has occurred */
      if ((*env)->ExceptionCheck(env)) {
        return NULL;
      }
      sprintf(debugBuf, "[GSSLibStub_acceptContext] set targetName=%ld",
              (long)targetName);
      debug(env, debugBuf);
      (*env)->SetObjectField(env, jcontextSpi, FID_NativeGSSContext_targetName,
                             jtargetName);
    }
    if (srcName != GSS_C_NO_NAME) {
      jsrcName = (*env)->NewObject(env, CLS_GSSNameElement,
                                   MID_GSSNameElement_ctor,
                                   ptr_to_jlong(srcName), jobj);
      /* return immediately if an exception has occurred */
      if ((*env)->ExceptionCheck(env)) {
        return NULL;
      }
      sprintf(debugBuf, "[GSSLibStub_acceptContext] set srcName=%ld",
              (long)srcName);
      debug(env, debugBuf);
      (*env)->SetObjectField(env, jcontextSpi, FID_NativeGSSContext_srcName,
                             jsrcName);
    }
    if (major == GSS_S_COMPLETE) {
      debug(env, "[GSSLibStub_acceptContext] context established");

      (*env)->SetIntField(env, jcontextSpi, FID_NativeGSSContext_lifetime,
                          getJavaTime(aTime));

      (*env)->SetBooleanField(env, jcontextSpi,
                              FID_NativeGSSContext_isEstablished,
                              JNI_TRUE);
      jMech = getJavaOID(env, aMech);
      (*env)->SetObjectField(env, jcontextSpi,
                             FID_NativeGSSContext_actualMech, jMech);
      if (delCred != GSS_C_NO_CREDENTIAL) {
        jdelCred = (*env)->NewObject(env, CLS_GSSCredElement,
                                     MID_GSSCredElement_ctor,
                                     ptr_to_jlong(delCred), jsrcName, jMech);
        /* return immediately if an exception has occurred */
        if ((*env)->ExceptionCheck(env)) {
          return NULL;
        }
        (*env)->SetObjectField(env, jcontextSpi,
                               FID_NativeGSSContext_delegatedCred,
                               jdelCred);
        sprintf(debugBuf, "[GSSLibStub_acceptContext] set delegatedCred=%ld",
                (long) delCred);
        debug(env, debugBuf);
      }
    } else if (major & GSS_S_CONTINUE_NEEDED) {
      debug(env, "[GSSLibStub_acceptContext] context not established");

      if (aFlags & GSS_C_PROT_READY_FLAG) {
        (*env)->SetIntField(env, jcontextSpi, FID_NativeGSSContext_lifetime,
                            getJavaTime(aTime));
      }
      major -= GSS_S_CONTINUE_NEEDED;
    }
  }
  /* release intermediate buffers */
  releaseGSSCB(env, jcb, cb);
  resetGSSBuffer(env, jinToken, &inToken);
  jresult = getJavaBuffer(env, &outToken);

  checkStatus(env, jobj, major, minor, "[GSSLibStub_acceptContext]");
  return jresult;
}

/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    inquireContext
 * Signature: (J)[J
 */
JNIEXPORT jlongArray JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_inquireContext(JNIEnv *env,
                                                         jobject jobj,
                                                         jlong pContext)
{
  OM_uint32 minor, major;
  gss_ctx_id_t contextHdl;
  gss_name_t srcName, targetName;
  OM_uint32 time;
  OM_uint32 flags;
  int isInitiator, isEstablished;
  jlong result[6];
  jlongArray jresult;

  contextHdl = (gss_ctx_id_t) jlong_to_ptr(pContext);

  sprintf(debugBuf, "[GSSLibStub_inquireContext] %ld", (long)contextHdl);
  debug(env, debugBuf);

  srcName = targetName = GSS_C_NO_NAME;
  time = 0;
  flags = isInitiator = isEstablished = 0;

  /* gss_inquire_context(...) => GSS_S_NO_CONTEXT(!) */
  major = (*ftab->inquireContext)(&minor, contextHdl, &srcName,
                              &targetName, &time, NULL, &flags,
                              &isInitiator, &isEstablished);
  /* update member values if needed */
  sprintf(debugBuf, "[GSSLibStub_inquireContext] srcName %ld", (long)srcName);
  debug(env, debugBuf);
  sprintf(debugBuf, "[GSSLibStub_inquireContext] targetName %ld",
                                                (long)targetName);
  debug(env, debugBuf);

  result[0] = ptr_to_jlong(srcName);
  result[1] = ptr_to_jlong(targetName);
  result[2] = (jlong) isInitiator;
  result[3] = (jlong) isEstablished;
  result[4] = (jlong) flags;
  result[5] = (jlong) getJavaTime(time);

  jresult = (*env)->NewLongArray(env, 6);
  (*env)->SetLongArrayRegion(env, jresult, 0, 6, result);

  /* release intermediate buffers */

  checkStatus(env, jobj, major, minor, "[GSSLibStub_inquireContext]");
  return jresult;
}

/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    getContextMech
 * Signature: (J)Lorg/ietf/jgss/Oid;
 */
JNIEXPORT jobject JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_getContextMech(JNIEnv *env,
                                                         jobject jobj,
                                                         jlong pContext)
{
  OM_uint32 minor, major;
  gss_OID mech;
  gss_ctx_id_t contextHdl;

  contextHdl = (gss_ctx_id_t) jlong_to_ptr(pContext);

  sprintf(debugBuf, "[GSSLibStub_getContextMech] %ld", (long int)pContext);
  debug(env, debugBuf);

  major = (*ftab->inquireContext)(&minor, contextHdl, NULL, NULL,
                                NULL, &mech, NULL,  NULL, NULL);

  checkStatus(env, jobj, major, minor, "[GSSLibStub_getContextMech]");
  /* return immediately if an exception has occurred */
  if ((*env)->ExceptionCheck(env)) {
    return NULL;
  }

  return getJavaOID(env, mech);
}

/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    getContextName
 * Signature: (JZ)J
 */
JNIEXPORT jlong JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_getContextName(JNIEnv *env,
  jobject jobj, jlong pContext, jboolean isSrc)
{
  OM_uint32 minor, major;
  gss_name_t nameHdl;
  gss_ctx_id_t contextHdl;

  contextHdl = (gss_ctx_id_t) jlong_to_ptr(pContext);

  sprintf(debugBuf, "[GSSLibStub_getContextName] %ld, isSrc=%d",
          (long)contextHdl, isSrc);
  debug(env, debugBuf);

  nameHdl = GSS_C_NO_NAME;
  if (isSrc == JNI_TRUE) {
    major = (*ftab->inquireContext)(&minor, contextHdl, &nameHdl, NULL,
                                NULL, NULL, NULL,  NULL, NULL);
  } else {
    major = (*ftab->inquireContext)(&minor, contextHdl, NULL, &nameHdl,
                                NULL, NULL, NULL,  NULL, NULL);
  }

  checkStatus(env, jobj, major, minor, "[GSSLibStub_inquireContextAll]");
  /* return immediately if an exception has occurred */
  if ((*env)->ExceptionCheck(env)) {
    return ptr_to_jlong(NULL);
  }

  sprintf(debugBuf, "[GSSLibStub_getContextName] pName=%ld", (long) nameHdl);
  debug(env, debugBuf);

  return ptr_to_jlong(nameHdl);
}

/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    getContextTime
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_getContextTime(JNIEnv *env,
                                                         jobject jobj,
                                                         jlong pContext) {
  OM_uint32 minor, major;
  gss_ctx_id_t contextHdl;
  OM_uint32 time;

  contextHdl = (gss_ctx_id_t) jlong_to_ptr(pContext);
  sprintf(debugBuf, "[GSSLibStub_getContextTime] %ld", (long)contextHdl);
  debug(env, debugBuf);

  if (contextHdl == GSS_C_NO_CONTEXT) return 0;

  /* gss_context_time(...) => GSS_S_CONTEXT_EXPIRED(!),
     GSS_S_NO_CONTEXT(!) */
  major = (*ftab->contextTime)(&minor, contextHdl, &time);
  if (GSS_ROUTINE_ERROR(major) == GSS_S_CONTEXT_EXPIRED) {
    major = GSS_CALLING_ERROR(major) | GSS_SUPPLEMENTARY_INFO(major);
  }
  checkStatus(env, jobj, major, minor, "[GSSLibStub_getContextTime]");
  return getJavaTime(time);
}

/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    deleteContext
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_deleteContext(JNIEnv *env,
                                                        jobject jobj,
                                                        jlong pContext)
{
  OM_uint32 minor, major;
  gss_ctx_id_t contextHdl;

  contextHdl = (gss_ctx_id_t) jlong_to_ptr(pContext);
  sprintf(debugBuf, "[GSSLibStub_deleteContext] %ld", (long)contextHdl);
  debug(env, debugBuf);

  if (contextHdl == GSS_C_NO_CONTEXT) return ptr_to_jlong(GSS_C_NO_CONTEXT);

  /* gss_delete_sec_context(...) => GSS_S_NO_CONTEXT(!) */
  major = (*ftab->deleteSecContext)(&minor, &contextHdl, GSS_C_NO_BUFFER);

  checkStatus(env, jobj, major, minor, "[GSSLibStub_deleteContext]");
  return (jlong) ptr_to_jlong(contextHdl);
}

/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    wrapSizeLimit
 * Signature: (JIII)I
 */
JNIEXPORT jint JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_wrapSizeLimit(JNIEnv *env,
                                                        jobject jobj,
                                                        jlong pContext,
                                                        jint reqFlag,
                                                        jint jqop,
                                                        jint joutSize)
{
  OM_uint32 minor, major;
  gss_ctx_id_t contextHdl;
  OM_uint32 outSize, maxInSize;
  gss_qop_t qop;

  contextHdl = (gss_ctx_id_t) jlong_to_ptr(pContext);
  sprintf(debugBuf, "[GSSLibStub_wrapSizeLimit] %ld", (long)contextHdl);
  debug(env, debugBuf);

  // Check context handle??

  qop = (gss_qop_t) jqop;
  outSize = (OM_uint32) joutSize;
  maxInSize = 0;
  /* gss_wrap_size_limit(...) => GSS_S_NO_CONTEXT(!), GSS_S_CONTEXT_EXPIRED,
     GSS_S_BAD_QOP */
  major = (*ftab->wrapSizeLimit)(&minor, contextHdl, reqFlag,
                              qop, outSize, &maxInSize);

  checkStatus(env, jobj, major, minor, "[GSSLibStub_wrapSizeLimit]");
  return (jint) maxInSize;
}

/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    exportContext
 * Signature: (J)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_exportContext(JNIEnv *env,
                                                        jobject jobj,
                                                        jlong pContext)
{
  OM_uint32 minor, major;
  gss_ctx_id_t contextHdl;
  gss_buffer_desc interProcToken;
  jbyteArray jresult;

  contextHdl = (gss_ctx_id_t) jlong_to_ptr(pContext);
  sprintf(debugBuf, "[GSSLibStub_exportContext] %ld", (long)contextHdl);
  debug(env, debugBuf);

  if (contextHdl == GSS_C_NO_CONTEXT) {
    // Twik per javadoc
    checkStatus(env, jobj, GSS_S_NO_CONTEXT, 0, "[GSSLibStub_exportContext]");
    return NULL;
  }
  /* gss_export_sec_context(...) => GSS_S_CONTEXT_EXPIRED,
     GSS_S_NO_CONTEXT, GSS_S_UNAVAILABLE */
  major =
    (*ftab->exportSecContext)(&minor, &contextHdl, &interProcToken);

  /* release intermediate buffers */
  jresult = getJavaBuffer(env, &interProcToken);
  checkStatus(env, jobj, major, minor, "[GSSLibStub_exportContext]");
  return jresult;
}

/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    getMic
 * Signature: (JI[B)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_getMic(JNIEnv *env, jobject jobj,
                                                 jlong pContext, jint jqop,
                                                 jbyteArray jmsg)
{
  OM_uint32 minor, major;
  gss_ctx_id_t contextHdl;
  gss_qop_t qop;
  gss_buffer_desc msg;
  gss_buffer_desc msgToken;
  jbyteArray jresult;

  contextHdl = (gss_ctx_id_t) jlong_to_ptr(pContext);
  sprintf(debugBuf, "[GSSLibStub_getMic] %ld", (long)contextHdl);
  debug(env, debugBuf);

  if (contextHdl == GSS_C_NO_CONTEXT) {
    // Twik per javadoc
    checkStatus(env, jobj, GSS_S_CONTEXT_EXPIRED, 0, "[GSSLibStub_getMic]");
    return NULL;
  }
  contextHdl = (gss_ctx_id_t) jlong_to_ptr(pContext);
  qop = (gss_qop_t) jqop;
  initGSSBuffer(env, jmsg, &msg);

  /* gss_get_mic(...) => GSS_S_CONTEXT_EXPIRED, GSS_S_NO_CONTEXT(!),
     GSS_S_BAD_QOP */
  major =
    (*ftab->getMic)(&minor, contextHdl, qop, &msg, &msgToken);

  /* release intermediate buffers */
  resetGSSBuffer(env, jmsg, &msg);
  jresult = getJavaBuffer(env, &msgToken);

  checkStatus(env, jobj, major, minor, "[GSSLibStub_getMic]");
  return jresult;
}

/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    verifyMic
 * Signature: (J[B[BLorg/ietf/jgss/MessageProp;)V
 */
JNIEXPORT void JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_verifyMic(JNIEnv *env,
                                                    jobject jobj,
                                                    jlong pContext,
                                                    jbyteArray jmsgToken,
                                                    jbyteArray jmsg,
                                                    jobject jprop)
{
  OM_uint32 minor, major;
  gss_ctx_id_t contextHdl;
  gss_buffer_desc msg;
  gss_buffer_desc msgToken;
  gss_qop_t qop;

  contextHdl = (gss_ctx_id_t) jlong_to_ptr(pContext);
  sprintf(debugBuf, "[GSSLibStub_verifyMic] %ld", (long)contextHdl);
  debug(env, debugBuf);

  if (contextHdl == GSS_C_NO_CONTEXT) {
    // Twik per javadoc
    checkStatus(env, jobj, GSS_S_CONTEXT_EXPIRED, 0,
        "[GSSLibStub_verifyMic]");
    return;
  }
  initGSSBuffer(env, jmsg, &msg);
  initGSSBuffer(env, jmsgToken, &msgToken);
  qop = (gss_qop_t) (*env)->CallIntMethod(env, jprop, MID_MessageProp_getQOP);
  /* gss_verify_mic(...) => GSS_S_DEFECTIVE_TOKEN, GSS_S_BAD_MIC,
     GSS_S_CONTEXT_EXPIRED, GSS_S_DUPLICATE_TOKEN(!), GSS_S_OLD_TOKEN(!),
     GSS_S_UNSEQ_TOKEN(!), GSS_S_GAP_TOKEN(!), GSS_S_NO_CONTEXT(!) */
  major =
    (*ftab->verifyMic)(&minor, contextHdl, &msg, &msgToken, &qop);

  /* release intermediate buffers */
  resetGSSBuffer(env, jmsg, &msg);
  resetGSSBuffer(env, jmsgToken, &msgToken);

  (*env)->CallVoidMethod(env, jprop, MID_MessageProp_setQOP, qop);
  setSupplementaryInfo(env, jobj, jprop, GSS_SUPPLEMENTARY_INFO(major),
                       minor);
  checkStatus(env, jobj, GSS_ERROR(major), minor, "[GSSLibStub_verifyMic]");
}

/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    wrap
 * Signature: (J[BLorg/ietf/jgss/MessageProp;)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_wrap(JNIEnv *env,
                                               jobject jobj,
                                               jlong pContext,
                                               jbyteArray jmsg,
                                               jobject jprop)
{
  OM_uint32 minor, major;
  jboolean confFlag;
  gss_qop_t qop;
  gss_buffer_desc msg;
  gss_buffer_desc msgToken;
  int confState;
  gss_ctx_id_t contextHdl;
  jbyteArray jresult;

  contextHdl = (gss_ctx_id_t) jlong_to_ptr(pContext);
  sprintf(debugBuf, "[GSSLibStub_wrap] %ld", (long)contextHdl);
  debug(env, debugBuf);

  if (contextHdl == GSS_C_NO_CONTEXT) {
    // Twik per javadoc
    checkStatus(env, jobj, GSS_S_CONTEXT_EXPIRED, 0, "[GSSLibStub_wrap]");
    return NULL;
  }

  confFlag =
    (*env)->CallBooleanMethod(env, jprop, MID_MessageProp_getPrivacy);
  qop = (gss_qop_t)
    (*env)->CallIntMethod(env, jprop, MID_MessageProp_getQOP);
  initGSSBuffer(env, jmsg, &msg);
  /* gss_wrap(...) => GSS_S_CONTEXT_EXPIRED, GSS_S_NO_CONTEXT(!),
     GSS_S_BAD_QOP */
  major = (*ftab->wrap)(&minor, contextHdl, confFlag, qop, &msg, &confState,
                   &msgToken);

  (*env)->CallVoidMethod(env, jprop, MID_MessageProp_setPrivacy,
                         (confState? JNI_TRUE:JNI_FALSE));

  /* release intermediate buffers */
  resetGSSBuffer(env, jmsg, &msg);
  jresult = getJavaBuffer(env, &msgToken);

  checkStatus(env, jobj, major, minor, "[GSSLibStub_wrap]");
  return jresult;
}

/*
 * Class:     sun_security_jgss_wrapper_GSSLibStub
 * Method:    unwrap
 * Signature: (J[BLorg/ietf/jgss/MessageProp;)[B
 */
JNIEXPORT jbyteArray JNICALL
Java_sun_security_jgss_wrapper_GSSLibStub_unwrap(JNIEnv *env,
                                                 jobject jobj,
                                                 jlong pContext,
                                                 jbyteArray jmsgToken,
                                                 jobject jprop)
{
  OM_uint32 minor, major;
  gss_ctx_id_t contextHdl;
  gss_buffer_desc msgToken;
  gss_buffer_desc msg;
  int confState;
  gss_qop_t qop;
  jbyteArray jresult;

  contextHdl = (gss_ctx_id_t) jlong_to_ptr(pContext);
  sprintf(debugBuf, "[GSSLibStub_unwrap] %ld", (long)contextHdl);
  debug(env, debugBuf);

  if (contextHdl == GSS_C_NO_CONTEXT) {
    // Twik per javadoc
    checkStatus(env, jobj, GSS_S_CONTEXT_EXPIRED, 0, "[GSSLibStub_unwrap]");
    return NULL;
  }
  initGSSBuffer(env, jmsgToken, &msgToken);
  confState = 0;
  qop = GSS_C_QOP_DEFAULT;
  /* gss_unwrap(...) => GSS_S_DEFECTIVE_TOKEN, GSS_S_BAD_MIC,
     GSS_S_CONTEXT_EXPIRED, GSS_S_DUPLICATE_TOKEN(!), GSS_S_OLD_TOKEN(!),
     GSS_S_UNSEQ_TOKEN(!), GSS_S_GAP_TOKEN(!), GSS_S_NO_CONTEXT(!) */
  major =
    (*ftab->unwrap)(&minor, contextHdl, &msgToken, &msg, &confState, &qop);
  /* update the message prop with relevant info */
  (*env)->CallVoidMethod(env, jprop, MID_MessageProp_setPrivacy,
                         (confState != 0));
  (*env)->CallVoidMethod(env, jprop, MID_MessageProp_setQOP, qop);
  setSupplementaryInfo(env, jobj, jprop, GSS_SUPPLEMENTARY_INFO(major),
                       minor);

  /* release intermediate buffers */
  resetGSSBuffer(env, jmsgToken, &msgToken);
  jresult = getJavaBuffer(env, &msg);

  checkStatus(env, jobj, GSS_ERROR(major), minor, "[GSSLibStub_unwrap]");
  return jresult;
}

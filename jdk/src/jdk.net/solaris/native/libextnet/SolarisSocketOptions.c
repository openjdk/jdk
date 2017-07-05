/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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


#include "SolarisSocketOptions.h"

static jfieldID sf_priority;
static jfieldID sf_bandwidth;

static int initialized = 0;

/*
 * Class:     jdk_net_SolarisSocketOptions
 * Method:    init
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_jdk_net_SolarisSocketOptions_init
  (JNIEnv *env, jclass unused)
{
    if (!initialized) {
        jclass c = (*env)->FindClass(env, "jdk/net/SocketFlow");
        CHECK_NULL(c);
        sf_priority = (*env)->GetFieldID(env, c, "priority", "I");
        CHECK_NULL(sf_priority);
        sf_bandwidth = (*env)->GetFieldID(env, c, "bandwidth", "J");
        CHECK_NULL(sf_bandwidth);
        initialized = 1;
    }
}

/** Return the Status value. */
static jint toStatus(int errval)
{
    switch (errval) {
      case 0:           return jdk_net_SocketFlow_OK_VALUE;
      case EPERM:       return jdk_net_SocketFlow_NO_PERMISSION_VALUE;
      case ENOTCONN:    return jdk_net_SocketFlow_NOT_CONNECTED_VALUE;
      case EOPNOTSUPP:  return jdk_net_SocketFlow_NOT_SUPPORTED_VALUE;
      case EALREADY:    return jdk_net_SocketFlow_ALREADY_CREATED_VALUE;
      case EINPROGRESS: return jdk_net_SocketFlow_IN_PROGRESS_VALUE;
      default:          return jdk_net_SocketFlow_OTHER_VALUE;
    }
}

void throwByNameWithLastError
  (JNIEnv *env, const char *name, const char *defaultDetail)
{
  char defaultMsg[255];
  sprintf(defaultMsg, "errno: %d, %s", errno, defaultDetail);
  JNU_ThrowByNameWithLastError(env, name, defaultMsg);
}

/*
 * Class:     jdk_net_SolarisSocketOptions
 * Method:    setFlowOption0
 * Signature: (IIJ)I
 */
JNIEXPORT jint JNICALL Java_jdk_net_SolarisSocketOptions_setFlowOption
  (JNIEnv *env, jobject unused, jint fd, jint priority, jlong bandwidth)
{
    int rv;
    sock_flow_props_t props;
    memset(&props, 0, sizeof(props));
    props.sfp_version = SOCK_FLOW_PROP_VERSION1;

    if (priority != jdk_net_SocketFlow_UNSET) {
        props.sfp_mask |= SFP_PRIORITY;
        props.sfp_priority = priority;
    }
    if (bandwidth > jdk_net_SocketFlow_UNSET)  {
        props.sfp_mask |= SFP_MAXBW;
        props.sfp_maxbw = (uint64_t) bandwidth;
    }

    rv = setsockopt(fd, SOL_SOCKET, SO_FLOW_SLA, &props, sizeof(props));

    if (rv < 0) {
        if (errno == ENOPROTOOPT) {
            JNU_ThrowByName(env, "java/lang/UnsupportedOperationException",
                            "unsupported socket option");
        } else if (errno == EACCES || errno == EPERM) {
            JNU_ThrowByName(env, "java/net/SocketException", "Permission denied");
        } else {
            throwByNameWithLastError(env, "java/net/SocketException",
                                     "set option SO_FLOW_SLA failed");
        }
        return 0;
    }
    return toStatus(props.sfp_status);
}

/*
 * Class:     jdk_net_SolarisSocketOptions
 * Method:    getFlowOption0
 * Signature: (ILjdk/net/SocketFlow;)I
 */
JNIEXPORT jint JNICALL Java_jdk_net_SolarisSocketOptions_getFlowOption
  (JNIEnv *env, jobject unused, jint fd, jobject flow)
{
    sock_flow_props_t props;
    socklen_t sz = sizeof(props);

    int rv = getsockopt(fd, SOL_SOCKET, SO_FLOW_SLA, &props, &sz);

    if (rv < 0) {
        if (errno == ENOPROTOOPT) {
            JNU_ThrowByName(env, "java/lang/UnsupportedOperationException",
                            "unsupported socket option");
        } else if (errno == EACCES || errno == EPERM) {
            JNU_ThrowByName(env, "java/net/SocketException", "Permission denied");
        } else {
            throwByNameWithLastError(env, "java/net/SocketException",
                                     "get option SO_FLOW_SLA failed");
        }
        return -1;
    }
    /* first check status to see if flow exists */
    if (props.sfp_status == 0) { /* OK */
        /* can set the other fields now */
        if (props.sfp_mask & SFP_PRIORITY) {
            (*env)->SetIntField(env, flow, sf_priority, props.sfp_priority);
        }
        if (props.sfp_mask & SFP_MAXBW) {
            (*env)->SetLongField(env, flow, sf_bandwidth,
                                    (jlong)props.sfp_maxbw);
        }
    }
    return toStatus(props.sfp_status);
}

JNIEXPORT jboolean JNICALL Java_jdk_net_SolarisSocketOptions_flowSupported
  (JNIEnv *env, jobject unused)
{
    /* Do a simple dummy call, and try to figure out from that */
    sock_flow_props_t props;
    int rv, s;

    s = socket(PF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (s < 0) {
        return JNI_FALSE;
    }
    memset(&props, 0, sizeof(props));
    props.sfp_version = SOCK_FLOW_PROP_VERSION1;
    props.sfp_mask |= SFP_PRIORITY;
    props.sfp_priority = SFP_PRIO_NORMAL;
    rv = setsockopt(s, SOL_SOCKET, SO_FLOW_SLA, &props, sizeof(props));
    if (rv != 0 && errno == ENOPROTOOPT) {
        rv = JNI_FALSE;
    } else {
        rv = JNI_TRUE;
    }
    close(s);
    return rv;
}

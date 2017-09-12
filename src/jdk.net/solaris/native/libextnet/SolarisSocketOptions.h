/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SOLARIS_SOCKET_OPTIONS_H
#define SOLARIS_SOCKET_OPTIONS_H

#include <sys/socket.h>
#include <jni.h>
#include <string.h>
#include <errno.h>
#include <unistd.h>

#include "jni_util.h"
#include "jdk_net_SocketFlow.h"
#include "SolarisSocketOptions.h"
#include "jdk_net_SolarisSocketOptions.h"

#ifndef SO_FLOW_SLA
#define SO_FLOW_SLA 0x1018

#if _LONG_LONG_ALIGNMENT == 8 && _LONG_LONG_ALIGNMENT_32 == 4
#pragma pack(4)
#endif

/*
 * Used with the setsockopt(SO_FLOW_SLA, ...) call to set
 * per socket service level properties.
 * When the application uses per-socket API, we will enforce the properties
 * on both outbound and inbound packets.
 *
 * For now, only priority and maxbw are supported in SOCK_FLOW_PROP_VERSION1.
 */
typedef struct sock_flow_props_s {
        int             sfp_version;
        uint32_t        sfp_mask;
        int             sfp_priority;   /* flow priority */
        uint64_t        sfp_maxbw;      /* bandwidth limit in bps */
        int             sfp_status;     /* flow create status for getsockopt */
} sock_flow_props_t;

#define SOCK_FLOW_PROP_VERSION1 1

/* bit mask values for sfp_mask */
#define SFP_MAXBW       0x00000001      /* Flow Bandwidth Limit */
#define SFP_PRIORITY    0x00000008      /* Flow priority */

/* possible values for sfp_priority */
#define SFP_PRIO_NORMAL 1
#define SFP_PRIO_HIGH   2

#if _LONG_LONG_ALIGNMENT == 8 && _LONG_LONG_ALIGNMENT_32 == 4
#pragma pack()
#endif /* _LONG_LONG_ALIGNMENT */

#endif /* SO_FLOW_SLA */

#endif /* SOLARIS_SOCKET_OPTIONS_H */

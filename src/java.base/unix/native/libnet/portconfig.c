/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <errno.h>

#if defined(_ALLBSD_SOURCE)
#include <sys/sysctl.h>
#endif

#include "jni.h"
#include "net_util.h"
#include "sun_net_PortConfig.h"

#ifdef __cplusplus
extern "C" {
#endif

struct portrange {
    int lower;
    int higher;
};

static int getPortRange(struct portrange *range)
{
#ifdef __linux__
    {
        FILE *f;
        int ret;

        f = fopen("/proc/sys/net/ipv4/ip_local_port_range", "r");
        if (f != NULL) {
            ret = fscanf(f, "%d %d", &range->lower, &range->higher);
            fclose(f);
            return ret == 2 ? 0 : -1;
        }
        return -1;
    }
#elif defined(__OpenBSD__)
    {
        // OpenBSD has no sysctlbyname(3); the pair is reached by its numbers,
        // and it calls them portfirst/portlast.
        int ret;
        size_t size = sizeof(range->lower);
        int lower_mib[4] = { CTL_NET, PF_INET, IPPROTO_IP,
                             IPCTL_IPPORT_FIRSTAUTO };
        int higher_mib[4] = { CTL_NET, PF_INET, IPPROTO_IP,
                              IPCTL_IPPORT_LASTAUTO };

        ret = sysctl(lower_mib, 4, &range->lower, &size, NULL, 0);
        if (ret == -1) {
            return -1;
        }
        size = sizeof(range->higher);
        return sysctl(higher_mib, 4, &range->higher, &size, NULL, 0);
    }
#elif defined(_ALLBSD_SOURCE)
    {
        int ret;
        size_t size = sizeof(range->lower);
        // NetBSD calls the pair anonportmin/anonportmax; macOS and FreeBSD
        // call it portrange.first/last.
#ifdef __NetBSD__
        const char *lower_name = "net.inet.ip.anonportmin";
        const char *higher_name = "net.inet.ip.anonportmax";
#else
        const char *lower_name = "net.inet.ip.portrange.first";
        const char *higher_name = "net.inet.ip.portrange.last";
#endif
        ret = sysctlbyname(lower_name, &range->lower, &size, 0, 0);
        if (ret == -1) {
            return -1;
        }
        size = sizeof(range->higher);
        ret = sysctlbyname(higher_name, &range->higher, &size, 0, 0);
        return ret;
    }
#else
    return -1;
#endif
}

/*
 * Class:     sun_net_PortConfig
 * Method:    getLower0
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sun_net_PortConfig_getLower0
  (JNIEnv *env, jclass clazz)
{
    struct portrange range;
    if (getPortRange(&range) < 0) {
        return -1;
    }
    return range.lower;
}

/*
 * Class:     sun_net_PortConfig
 * Method:    getUpper0
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_sun_net_PortConfig_getUpper0
  (JNIEnv *env, jclass clazz)
{
    struct portrange range;
    if (getPortRange(&range) < 0) {
        return -1;
    }
    return range.higher;
}

#ifdef __cplusplus
}
#endif

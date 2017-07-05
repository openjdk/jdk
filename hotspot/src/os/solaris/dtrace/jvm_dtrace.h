/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

#ifndef _JVM_DTRACE_H_
#define _JVM_DTRACE_H_

/*
 * Interface to dynamically turn on probes in Hotspot JVM. Currently,
 * this interface can be used to dynamically enable certain DTrace
 * probe points that are costly to have "always on".
 */

#ifdef __cplusplus
extern "C" {
#endif

#include <sys/types.h>


struct _jvm_t;
typedef struct _jvm_t jvm_t;


/* Attach to the given JVM process. Returns NULL on failure.
   jvm_get_last_error() returns last error message. */
jvm_t* jvm_attach(pid_t pid);

/* Returns the last error message from this library or NULL if none. */
const char* jvm_get_last_error();

/* few well-known probe type constants for 'probe_types' param below */

#define JVM_DTPROBE_METHOD_ENTRY         "method-entry"
#define JVM_DTPROBE_METHOD_RETURN        "method-return"
#define JVM_DTPROBE_MONITOR_ENTER        "monitor-contended-enter"
#define JVM_DTPROBE_MONITOR_ENTERED      "monitor-contended-entered"
#define JVM_DTPROBE_MONITOR_EXIT         "monitor-contended-exit"
#define JVM_DTPROBE_MONITOR_WAIT         "monitor-wait"
#define JVM_DTPROBE_MONITOR_WAITED       "monitor-waited"
#define JVM_DTPROBE_MONITOR_NOTIFY       "monitor-notify"
#define JVM_DTPROBE_MONITOR_NOTIFYALL    "monitor-notifyall"
#define JVM_DTPROBE_OBJECT_ALLOC         "object-alloc"
#define JVM_DTPROBE_ALL                  "*"

/* Enable the specified DTrace probes of given probe types on
 * the specified JVM. Returns >= 0 on success, -1 on failure.
 * On success, this returns number of probe_types enabled.
 * On failure, jvm_get_last_error() returns the last error message.
 */
int jvm_enable_dtprobes(jvm_t* jvm, int num_probe_types, const char** probe_types);

/* Note: There is no jvm_disable_dtprobes function. Probes are automatically
 * disabled when there are no more clients requiring those probes.
 */

/* Detach the given JVM. Returns 0 on success, -1 on failure.
 * jvm_get_last_error() returns the last error message.
 */
int jvm_detach(jvm_t* jvm);

#ifdef __cplusplus
}
#endif

#endif /* _JVM_DTRACE_H_ */

/*
 * Copyright (c) 2011, 2015, Oracle and/or its affiliates. All rights reserved.
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

#include <fcntl.h>
#include <kstat.h>
#include <procfs.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <sys/sysinfo.h>
#include <sys/lwp.h>
#include <pthread.h>
#include <utmpx.h>
#include <dlfcn.h>
#include <sys/loadavg.h>
#include <jni.h>
#include "jvm.h"
#include "com_sun_management_internal_OperatingSystemImpl.h"

typedef struct {
    kstat_t *kstat;
    uint64_t  last_idle;
    uint64_t  last_total;
    double  last_ratio;
} cpuload_t;

static cpuload_t   *cpu_loads = NULL;
static unsigned int num_cpus;
static kstat_ctl_t *kstat_ctrl = NULL;

static void map_cpu_kstat_counters() {
    kstat_t     *kstat;
    int          i;

    // Get number of CPU(s)
    if ((num_cpus = sysconf(_SC_NPROCESSORS_ONLN)) == -1) {
        num_cpus = 1;
    }

    // Data structure for saving CPU load
    if ((cpu_loads = calloc(num_cpus,sizeof(cpuload_t))) == NULL) {
        return;
    }

    // Get kstat cpu_stat counters for every CPU
    // (loop over kstat to find our cpu_stat(s)
    i = 0;
    for (kstat = kstat_ctrl->kc_chain; kstat != NULL; kstat = kstat->ks_next) {
        if (strncmp(kstat->ks_module, "cpu_stat", 8) == 0) {

            if (kstat_read(kstat_ctrl, kstat, NULL) == -1) {
            // Failed to initialize kstat for this CPU so ignore it
            continue;
            }

            if (i == num_cpus) {
            // Found more cpu_stats than reported CPUs
            break;
            }

            cpu_loads[i++].kstat = kstat;
        }
    }
}

static int init_cpu_kstat_counters() {
    static int initialized = 0;

    // Concurrence in this method is prevented by the lock in
    // the calling method get_cpu_load();
    if(!initialized) {
        if ((kstat_ctrl = kstat_open()) != NULL) {
            map_cpu_kstat_counters();
            initialized = 1;
        }
    }
    return initialized ? 0 : -1;
}

static void update_cpu_kstat_counters() {
    if(kstat_chain_update(kstat_ctrl) != 0) {
        free(cpu_loads);
        map_cpu_kstat_counters();
    }
}

int read_cpustat(cpuload_t *load, cpu_stat_t *cpu_stat) {
    if (load->kstat == NULL) {
        // no handle.
        return -1;
    }
    if (kstat_read(kstat_ctrl, load->kstat, cpu_stat) == -1) {
        //  disabling for now, a kstat chain update is likely to happen next time
        load->kstat = NULL;
        return -1;
    }
    return 0;
}

double get_single_cpu_load(unsigned int n) {
    cpuload_t  *load;
    cpu_stat_t  cpu_stat;
    uint_t     *usage;
    uint64_t          c_idle;
    uint64_t          c_total;
    uint64_t          d_idle;
    uint64_t          d_total;
    int           i;

    if (n >= num_cpus) {
        return -1.0;
    }

    load = &cpu_loads[n];
    if (read_cpustat(load, &cpu_stat) < 0) {
        return -1.0;
    }

    usage   = cpu_stat.cpu_sysinfo.cpu;
    c_idle  = usage[CPU_IDLE];

    for (c_total = 0, i = 0; i < CPU_STATES; i++) {
        c_total += usage[i];
    }

    // Calculate diff against previous snapshot
    d_idle  = c_idle - load->last_idle;
    d_total = c_total - load->last_total;

    /** update if weve moved */
    if (d_total > 0) {
        // Save current values for next time around
        load->last_idle  = c_idle;
        load->last_total = c_total;
        load->last_ratio = (double) (d_total - d_idle) / d_total;
    }

    return load->last_ratio;
}

int get_info(const char *path, void *info, size_t s, off_t o) {
    int fd;
    int ret = 0;
    if ((fd = open(path, O_RDONLY)) < 0) {
        return -1;
    }
    if (pread(fd, info, s, o) != s) {
        ret = -1;
    }
    close(fd);
    return ret;
}

#define MIN(a, b)           ((a < b) ? a : b)

static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;

/**
 * Return the cpu load (0-1) for proc number 'which' (or average all if which == -1)
 */
double  get_cpu_load(int which) {
    double load =.0;

    pthread_mutex_lock(&lock);
    if(init_cpu_kstat_counters()==0) {

        update_cpu_kstat_counters();

        if (which == -1) {
            unsigned int i;
            double       t;

            for (t = .0, i = 0; i < num_cpus; i++) {
                t += get_single_cpu_load(i);
            }

            // Cap total systemload to 1.0
            load = MIN((t / num_cpus), 1.0);
        } else {
            load = MIN(get_single_cpu_load(which), 1.0);
        }
    } else {
        load = -1.0;
    }
    pthread_mutex_unlock(&lock);

    return load;
}

/**
 * Return the cpu load (0-1) for the current process (i.e the JVM)
 * or -1.0 if the get_info() call failed
 */
double get_process_load(void) {
    psinfo_t info;

    // Get the percentage of "recent cpu usage" from all the lwp:s in the JVM:s
    // process. This is returned as a value between 0.0 and 1.0 multiplied by 0x8000.
    if (get_info("/proc/self/psinfo",&info.pr_pctcpu, sizeof(info.pr_pctcpu), offsetof(psinfo_t, pr_pctcpu)) == 0) {
        return (double) info.pr_pctcpu / 0x8000;
    }
    return -1.0;
}

JNIEXPORT jdouble JNICALL
Java_com_sun_management_internal_OperatingSystemImpl_getSystemCpuLoad0
(JNIEnv *env, jobject dummy)
{
    return get_cpu_load(-1);
}

JNIEXPORT jdouble JNICALL
Java_com_sun_management_internal_OperatingSystemImpl_getProcessCpuLoad0
(JNIEnv *env, jobject dummy)
{
    return get_process_load();
}


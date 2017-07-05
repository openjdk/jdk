#!/usr/sbin/dtrace -Zs
/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/*
*/

/*
 * Usage:
 *    1. gc_time_stat.d -c "java ..." INTERVAL_SECS
 *    2. gc_time_stat.d -p JAVA_PID INTERVAL_SECS
 *
 * This script measures the duration of a time spent in GC.  The duration is
 * measured for every memory pool every INTERVAL_SECS seconds.  If
 * INTERVAL_SECS is not set then 10 seconds interval is used.
 *
 */

#pragma D option quiet
#pragma D option destructive
#pragma D option defaultargs
#pragma D option aggrate=100ms


string TEST_NAME;
self char *str_ptr;
self string mgr_name;
self string pool_name;

int INTERVAL_SECS;

:::BEGIN
{
    SAMPLE_NAME = "hotspot GC tracing";

    START_TIME = timestamp;
    gc_total_time = 0;
    gc_total_count = 0;

    INTERVAL_SECS = $1 ? $1 : 10;
    SAMPLING_TIME = timestamp + INTERVAL_SECS * 1000000000ull;

    LINE_SEP = "--------------------------------------------------------";

    printf("BEGIN %s\n\n", SAMPLE_NAME);
}


/*
 * hotspot:::gc-begin
 *  arg0: uintptr_t,    boolean value which indicates
 *                      if this is to be a full GC or not
 */
hotspot$target:::gc-begin
{
    self->gc_ts = timestamp;
    printf("\nGC started: %Y\n", walltimestamp);
    printf("%20s | %-20s | %10s\n", "manager", "pool", "time (ms)");
    printf(" %s\n", LINE_SEP);
}

hotspot$target:::gc-end
/self->gc_ts/
{
    self->time = (timestamp - self->gc_ts) / 1000;

    printf(" %s\n", LINE_SEP);
    printf("   %40s | %10d\n", "GC total", self->time);

    gc_total_time += self->time;
    gc_total_count ++;
    self->gc_ts = 0;
}

/*
 * hotspot:::mem-pool-gc-begin, hotspot:::mem-pool-gc-end
 *  arg0: char*,        a pointer to mUTF-8 string data which contains the name
 *                          of the manager which manages this memory pool
 *  arg1: uintptr_t,    the length of the manager name (in bytes
 *  arg2: char*,        a pointer to mUTF-8 string data which contains the name
 *                          of the memory pool
 *  arg3: uintptr_t,    the length of the memory pool name (in bytes)
 *  arg4: uintptr_t,    the initial size of the memory pool (in bytes)
 *  arg5: uintptr_t,    the amount of memory in use in the memory pool
 *                          (in bytes)
 *  arg6: uintptr_t,    the the number of committed pages in the memory pool
 *  arg7: uintptr_t,    the the maximum size of the memory pool
 */
hotspot$target:::mem-pool-gc-begin
{
    self->str_ptr = (char*) copyin(arg0, arg1+1);
    self->str_ptr[arg1] = '\0';
    self->mgr_name = (string) self->str_ptr;

    self->str_ptr = (char*) copyin(arg2, arg3+1);
    self->str_ptr[arg3] = '\0';
    self->pool_name = (string) self->str_ptr;

    self->mem_pool_ts[self->mgr_name, self->pool_name] = timestamp;
}

hotspot$target:::mem-pool-gc-end
{
    self->str_ptr = (char*) copyin(arg0, arg1+1);
    self->str_ptr[arg1] = '\0';
    self->mgr_name = (string) self->str_ptr;

    self->str_ptr = (char*) copyin(arg2, arg3+1);
    self->str_ptr[arg3] = '\0';
    self->pool_name = (string) self->str_ptr;

    self->time =
        (timestamp - self->mem_pool_ts[self->mgr_name, self->pool_name]) / 1000;

    printf(
        "%20s | %-20s | %10d\n", self->mgr_name, self->pool_name, self->time);

    @mem_pool_total_time[self->mgr_name, self->pool_name] = sum(self->time);
    self->mem_pool_ts[self->mgr_name, self->pool_name] = 0;

    @mem_pool_count[self->mgr_name, self->pool_name] = count();
}

tick-1sec
/timestamp > SAMPLING_TIME/
{
    trace_time = (timestamp - START_TIME) / 1000;

    printf(" %s\n", LINE_SEP);
    printf("\nGC statistics, time: %Y\n\n", walltimestamp);
    printf("%20s | %-20s | %10s\n", "manager", "pool", "total time");
    printf(" %s\n", LINE_SEP);
    printa("%20s | %-20s | %10@d\n", @mem_pool_total_time);
    printf(" %s\n", LINE_SEP);
    printf("   %40s | %10d\n", "total", gc_total_time);

    printf("\n");
    printf("%20s | %-20s | %10s\n", "manager", "pool", "# of calls");
    printf(" %s\n", LINE_SEP);
    printa("%20s | %-20s | %10@d\n", @mem_pool_count);
    printf(" %s\n", LINE_SEP);
    printf("   %40s | %10d\n", "total", gc_total_count);

    SAMPLING_TIME = timestamp + INTERVAL_SECS * 1000000000ull;
}

:::END
{
    trace_time = (timestamp - START_TIME) / 1000;

    printf(" %s\n", LINE_SEP);
    printf("\nGC statistics, time: %Y\n\n", walltimestamp);
    printf("%20s | %-20s | %10s\n", "manager", "pool", "total time");
    printf(" %s\n", LINE_SEP);
    printa("%20s | %-20s | %10@d\n", @mem_pool_total_time);
    printf(" %s\n", LINE_SEP);
    printf("   %40s | %10d\n", "total", gc_total_time);

    printf("\n");
    printf("%20s | %-20s | %10s\n", "manager", "pool", "# of calls");
    printf(" %s\n", LINE_SEP);
    printa("%20s | %-20s | %10@d\n", @mem_pool_count);
    printf(" %s\n", LINE_SEP);
    printf("   %40s | %10d\n", "total", gc_total_count);


    printf("\nEND of %s\n", SAMPLE_NAME);
}

syscall::rexit:entry,
syscall::exit:entry
/pid == $target/
{
   exit(0);
}

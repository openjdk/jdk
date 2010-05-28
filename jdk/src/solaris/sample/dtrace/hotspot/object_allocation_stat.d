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
 *    1. object_allocation_stat.d -c "java ..." TOP_RESULTS_COUNT INTERVAL_SECS
 *    2. object_allocation_stat.d -p JAVA_PID TOP_RESULTS_COUNT INTERVAL_SECS
 *
 * This script collects statistics about TOP_RESULTS_COUNT (default is 25)
 * object allocations every INTERVAL_SECS (default is 60) seconds.
 *
 * The results are displayed in ascending order which means that the highest
 * allocated type is listed last. The script can be improved to sort the
 * results in reverse order when DTrace supports it.
 *
 * Notes:
 *  - The object-alloc probe is disabled by default since it incurs
 *    performance overhead to the application. To trace object-alloc probe,
 *    you need to turn on the ExtendedDTraceProbes VM option.
 *    You can either start the application with -XX:+ExtendedDTraceProbes
 *    option or use the jinfo command to enable it at runtime as follows:
 *
 *       jinfo -flag +ExtendedDTraceProbes <java_pid>
 *
 */

#pragma D option quiet
#pragma D option destructive
#pragma D option defaultargs
#pragma D option bufsize=16m
#pragma D option aggrate=100ms


self char *str_ptr;
self string class_name;

long long ALLOCATED_OBJECTS_CNT;

int INTERVAL_SECS;

:::BEGIN
{
    SAMPLE_NAME = "hotspot object allocation tracing";

    TOP_RESULTS_COUNT = $1 ? $1 : 25;
    INTERVAL_SECS = $2 ? $2 : 60;

    ALLOCATED_OBJECTS_CNT = 0;

    SAMPLING_TIME = timestamp + INTERVAL_SECS * 1000000000ull;

    LINE_SEP =
    "------------------------------------------------------------------------";

    printf("BEGIN %s\n\n", SAMPLE_NAME);
}

/*
 * hotspot:::object-alloc probe arguments:
 *  arg0: uintptr_t,    Java thread id
 *  arg1: char*,        a pointer to mUTF-8 string containing the name of
 *                          the class of the object being allocated
 *  arg2: uintptr_t,    the length of the class name (in bytes)
 *  arg3: uintptr_t,    the size of the object being allocated
 */
hotspot$target:::object-alloc
{
    ALLOCATED_OBJECTS_CNT ++;

    self->str_ptr = (char*) copyin(arg1, arg2+1);
    self->str_ptr[arg2] = '\0';
    self->class_name = (string) self->str_ptr;


    @allocs_count[self->class_name] = count();
    @allocs_size[self->class_name] = sum(arg3);
}

tick-1sec
/timestamp > SAMPLING_TIME/
{
    printf("\n");
    printf("%s\n", LINE_SEP);
    printf("%Y\n", walltimestamp);
    printf("%s\n", LINE_SEP);

    printf("\n");
    printf("Top %d allocations by size:\n", TOP_RESULTS_COUNT);
    trunc(@allocs_size, TOP_RESULTS_COUNT);
    printa("%10@d %s\n", @allocs_size);

    printf("\n");
    printf("Top %d allocations by count:\n", TOP_RESULTS_COUNT);
    trunc(@allocs_count, TOP_RESULTS_COUNT);
    printa("%10@d %s\n", @allocs_count);

    printf("\nTotal number of allocated objects: %d\n", ALLOCATED_OBJECTS_CNT);

    SAMPLING_TIME = timestamp + INTERVAL_SECS * 1000000000ull;
}

:::END
{
    printf("\n");
    printf("%s\n", LINE_SEP);
    printf("%Y\n", walltimestamp);
    printf("%s\n", LINE_SEP);

    printf("\n");
    printf("Top %d allocations by size:\n", TOP_RESULTS_COUNT);
    trunc(@allocs_size, TOP_RESULTS_COUNT);
    printa("%10@d %s\n", @allocs_size);

    printf("\n");
    printf("Top %d allocations by count:\n", TOP_RESULTS_COUNT);
    trunc(@allocs_count, TOP_RESULTS_COUNT);
    printa("%10@d %s\n", @allocs_count);

    printf("\nTotal number of allocated objects: %d\n", ALLOCATED_OBJECTS_CNT);

    printf("\nEND of %s\n", SAMPLE_NAME);
}

syscall::rexit:entry,
syscall::exit:entry
/pid == $target/
{
   exit(0);
}

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
 *    1. method_invocation_stat.d -c "java ..."
 *    2. method_invocation_stat.d -p JAVA_PID
 *
 * This script collects statistics about Java method invocations.
 *
 * Notes:
 *  - These probes are disabled by default since it incurs performance
 *    overhead to the application. To trace the method-entry and
 *    method-exit probes, you need to turn on the ExtendedDTraceProbes VM
 *    option.  
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
self string method_name;
self string signature;
self string package_name;
self string last_class_name;

long long JAVA_CALLS;
long long JNI_CALLS;
long long SYS_CALLS;

int SYS_DEEP;
long long LAST_SYS_TS;

long long START_TIME;
long long JAVA_TIME;
long long RUN_TIME;
long long SYS_TIME;

BEGIN
{
    SAMPLE_NAME = "hotspot method invocation tracing";

    START_TIME = timestamp;
    SYS_TIME = 0;

    printf("BEGIN %s\n\n", SAMPLE_NAME);
}

/*
 * hotspot:::method-entry, hotspot:::method-return probe arguments:
 *  arg0: uintptr_t,    Java thread id
 *  arg1: char*,        a pointer to mUTF-8 string containing the name of
 *                          the class of the method being entered
 *  arg2: uintptr_t,    the length of the class name (in bytes)
 *  arg3: char*,        a pointer to mUTF-8 string data which contains the
 *                          name of the method being entered
 *  arg4: uintptr_t,    the length of the method name (in bytes)
 *  arg5: char*,        a pointer to mUTF-8 string data which contains the
 *                          signature of the method being entered
 *  arg6: uintptr_t,    the length of the signature(in bytes)
 */
hotspot$target:::method-entry
{
    self->str_ptr = (char*) copyin(arg1, arg2+1);
    self->str_ptr[arg2] = '\0';
    self->class_name = (string) self->str_ptr;

    self->str_ptr = (char*) copyin(arg3, arg4+1);
    self->str_ptr[arg4] = '\0';
    self->method_name = (string) self->str_ptr;

    self->str_ptr = (char*) copyin(arg5, arg6+1);
    self->str_ptr[arg6] = '\0';
    self->signature = (string) self->str_ptr;


    self->package_name = dirname(self->class_name);

    JAVA_CALLS ++;
    @method_calls[self->class_name,
                  self->method_name, self->signature] = count();
    @class_calls[self->class_name] = count();
    @package_calls[self->package_name] = count();
}


hotspot_jni$target:::*-entry
{
    JNI_CALLS ++;

    @jni_calls[probename] = count();
}

syscall:::entry
/pid == $target && SYS_DEEP == 0/
{
    LAST_SYS_TS = timestamp;
}

syscall:::entry
/pid == $target/
{
    SYS_DEEP ++;
    @sys_calls[probefunc] = count();
    SYS_CALLS ++;
}

syscall:::return
/pid == $target/
{
    SYS_DEEP --;
}

syscall:::return
/pid == $target && SYS_DEEP == 0/
{
    SYS_TIME += (timestamp - LAST_SYS_TS);
}


:::END
{
    RUN_TIME = (timestamp - START_TIME);
    JAVA_TIME = (RUN_TIME - SYS_TIME);

    printf("System calls:\n");
    printa("%10@d %s\n", @sys_calls);
    printf("\n");

    printf("JNI calls:\n");
    printa("%10@d %s\n", @jni_calls);
    printf("\n");

    printf("Top packages calls:\n");
    printa("%10@d %s\n", @package_calls);
    printf("\n");

    printf("Top class calls:\n");
    printa("%10@d %s\n", @class_calls);
    printf("\n");

    printf("Top method calls:\n");
    printa("%10@d %s:%s:%s\n", @method_calls);
    printf("\n");

    printf("=======================================\n");
    printf("JAVA_CALLS: %10d\n", JAVA_CALLS);
    printf(" JNI_CALLS: %10d\n", JNI_CALLS);
    printf(" SYS_CALLS: %10d\n", SYS_CALLS);
    printf("\n");

    printf("Run time:       %15d\n", RUN_TIME);
    printf("Syscall time:   %15d\n", SYS_TIME);
    printf("Java+JNI time:  %15d\n", JAVA_TIME);
    printf("\n");
}

:::END
{
    printf("\nEND %s\n", SAMPLE_NAME);
}

syscall::rexit:entry,
syscall::exit:entry
/pid == $target/
{
    exit(0);
}

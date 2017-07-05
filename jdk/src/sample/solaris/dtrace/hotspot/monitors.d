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
 *   1. monitors.d -c "java ..."
 *   2. monitors.d -p JAVA_PID
 *
 * The script traces monitor related probes.
 *
 * Notes:
 *  - These probes are disabled by default since it incurs performance
 *    overhead to the application. To trace the monitor-* probes, you need
 *    to turn on the ExtendedDTraceProbes VM option.
 *    You can either start the application with -XX:+ExtendedDTraceProbes
 *    option or use the jinfo command to enable it at runtime as follows:
 *
 *       jinfo -flag +ExtendedDTraceProbes <java_pid>
 *
 */

#pragma D option quiet
#pragma D option destructive
#pragma D option defaultargs
#pragma D option aggrate=100ms


self string thread_name;
self char* str_ptr;

:::BEGIN
{
    SAMPLE_NAME = "hotspot monitors tracing";

    printf("BEGIN %s\n\n", SAMPLE_NAME);
}

/*
 * hotspot:::thread-start, hotspot:::thread-stop probe arguments:
 *  arg0: char*,        thread name passed as mUTF8 string
 *  arg1: uintptr_t,    thread name length
 *  arg2: uintptr_t,    Java thread id
 *  arg3: uintptr_t,    native/OS thread id
 *  arg4: uintptr_t,    is a daemon or not
 */
hotspot$target:::thread-start
{
    self->str_ptr = (char*) copyin(arg0, arg1+1);
    self->str_ptr[arg1] = '\0';
    self->thread_name = (string) self->str_ptr;

    printf("thread-start: id=%d, is_daemon=%d, name=%s, os_id=%d\n",
            arg2, arg4, self->thread_name, arg3);

    threads[arg2] = self->thread_name;
}


hotspot$target:::thread-stop
{
    self->str_ptr = (char*) copyin(arg0, arg1+1);
    self->str_ptr[arg1] = '\0';
    self->thread_name = (string) self->str_ptr;


    printf("thread-stop: id=%d, is_daemon=%d, name=%s, os_id=%d\n",
            arg2, arg4, self->thread_name, arg3);
}

/*
 *
 * hotspot::monitor-contended-enter, hotspot::monitor-contended-entered
 *
 *  arg0: uintptr_t,    the Java thread identifier for the thread peforming
 *                          the monitor operation
 *  arg1: uintptr_t,    a unique, but opaque identifier for the specific
 *                          monitor that the action is performed upon
 *  arg2: char*,        a pointer to mUTF-8 string data which contains the
 *                          name of the class of the object being acted upon
 *  arg3: uintptr_t,    the length of the class name (in bytes)
 */

hotspot$target:::monitor-contended-enter
{
    /* (uintptr_t thread_id, uintptr_t monitor_id,
       char* obj_class_name, uintptr_t obj_class_name_len) */

    self->str_ptr = (char*) copyin(arg2, arg3+1);
    self->str_ptr[arg3] = '\0';
    self->class_name = (string) self->str_ptr;

    monitors[arg1] = self->class_name;

    monitors_enter[arg1] = arg0;
    printf("%s: -> enter monitor (%d) %s\n",
        threads[arg0], arg1, monitors[arg1]);
}

hotspot$target:::monitor-contended-entered
{
    /* (uintptr_t thread_id, uintptr_t monitor_id, char* obj_class_name,
        uintptr_t obj_class_name_len) */

    monitors_entered[arg1] = arg0;
    printf("%s: <- entered monitor (%d) %s\n",
        threads[arg0], arg1, monitors[arg1]);
}


:::END
{
    printf("\nEND of %s\n", SAMPLE_NAME);
}

syscall::rexit:entry,
syscall::exit:entry
/pid == $target/
{
   exit(0);
}

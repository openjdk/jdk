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
 *   1. hotspot_calls_tree.d -c "java ..."
 *   2. hotspot_calls_tree.d -p JAVA_PID
 *
 * This script prints calls tree of fired 'hotspot' probes.
 *
 * Notes: 
 *    The script uses 'monitors' probes which are disabled by default since
 *    it incurs performance overhead to the application. To enable them, you
 *    need to turn on the ExtendedDTraceProbes VM option. You can either
 *    start the application with -XX:+ExtendedDTraceProbes option or use the
 *    jinfo command to enable it at runtime as follows:
 *
 *       jinfo -flag +ExtendedDTraceProbes <java_pid>
 *
 */

#pragma D option quiet
#pragma D option destructive
#pragma D option defaultargs
#pragma D option aggrate=100ms

self int indent;
string PAUSE_AT_STARTUP_FILE;

:::BEGIN
{
    SAMPLE_NAME = "hotspot probes tracing";

    printf("BEGIN %s\n\n", SAMPLE_NAME);

    self->indent = 10;
}

hotspot$target:::class-loaded,
hotspot$target:::class-unloaded,
hotspot$target:::compiled-method-load,
hotspot$target:::compiled-method-unload,
hotspot$target:::monitor-notify,
hotspot$target:::monitor-notifyAll
{
    printf("%d %*s <-> %s\n", curcpu->cpu_id, self->indent, "", probename);
}

hotspot$target:::vm-init-begin,
hotspot$target:::gc-begin,
hotspot$target:::mem-pool-gc-begin,
hotspot$target:::thread-start,
hotspot$target:::method-compile-begin,
hotspot$target:::monitor-contended-enter,
hotspot$target:::monitor-wait
{
    self->indent ++;
    printf("%d %*s -> %s\n", curcpu->cpu_id, self->indent, "", probename);
}

hotspot$target:::vm-init-end,
hotspot$target:::vm-shutdown,
hotspot$target:::gc-end,
hotspot$target:::mem-pool-gc-end,
hotspot$target:::thread-stop,
hotspot$target:::method-compile-end,
hotspot$target:::monitor-contended-entered,
hotspot$target:::monitor-contended-exit,
hotspot$target:::monitor-waited
{
    printf("%d %*s <- %s\n", curcpu->cpu_id, self->indent, "", probename);
    self->indent --;
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

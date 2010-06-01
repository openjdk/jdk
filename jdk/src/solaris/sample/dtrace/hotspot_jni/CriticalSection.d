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
 *    1. CriticalSection.d -c "java ..."
 *    2. CriticalSection.d -p JAVA_PID
 *
 * The script inspect a JNI application for Critical Section violations.
 *
 * Critical section is the space between calls to JNI methods:
 *   - GetPrimitiveArrayCritical and ReleasePrimitiveArrayCritical; or
 *   - GetStringCritical and ReleaseStringCritical.
 *
 * Inside a critical section, native code must not call other JNI functions,
 * or any system call that may cause the current thread to block and wait
 * for another Java thread. (For example, the current thread must not call
 * read on a stream being written by another Java thread.)
 *
 */

#pragma D option quiet
#pragma D option destructive
#pragma D option defaultargs
#pragma D option bufsize=16m
#pragma D option aggrate=100ms


self int in_critical_section;
self string critical_section_name;

int CRITICAL_SECTION_VIOLATION_CNT;

:::BEGIN
{
    SAMPLE_NAME = "critical section violation checks";

    printf("BEGIN %s\n", SAMPLE_NAME);
}

/*
 *   Multiple pairs of GetPrimitiveArrayCritical/ReleasePrimitiveArrayCritical,
 *   GetStringCritical/ReleaseStringCritical may be nested
 */
hotspot_jni$target:::*_entry
/self->in_critical_section > 0 &&
  probename != "GetPrimitiveArrayCritical_entry" &&
  probename != "GetStringCritical_entry" &&
  probename != "ReleasePrimitiveArrayCritical_entry" &&
  probename != "ReleaseStringCritical_entry" &&
  probename != "GetPrimitiveArrayCritical_return" &&
  probename != "GetStringCritical_return" &&
  probename != "ReleasePrimitiveArrayCritical_return" &&
  probename != "ReleaseStringCritical_return"/
{
    printf("\nJNI call %s made from JNI critical region '%s'\n",
        probename, self->critical_section_name);

    printf("Jstack:\n");
    jstack(50, 500);

    CRITICAL_SECTION_VIOLATION_CNT ++;
}

syscall:::entry
/pid == $target && self->in_critical_section > 0/
{
    printf("\nSystem call %s made in JNI critical region '%s'\n",
        probefunc, self->critical_section_name);

    printf("Jstack:\n");
    jstack(50, 500);

    CRITICAL_SECTION_VIOLATION_CNT ++;
}

hotspot_jni$target:::ReleasePrimitiveArrayCritical_entry,
hotspot_jni$target:::ReleaseStringCritical_entry
/self->in_critical_section > 0/
{
    self->in_critical_section --;
}

hotspot_jni$target:::GetPrimitiveArrayCritical_return
{
    self->in_critical_section ++;
    self->critical_section_name = "GetPrimitiveArrayCritical";
}

hotspot_jni$target:::GetStringCritical_return
{
    self->in_critical_section ++;
    self->critical_section_name = "GetStringCritical";
}


:::END
{
    printf("%d critical section violations have been discovered\n",
        CRITICAL_SECTION_VIOLATION_CNT);

    printf("\nEND of %s\n", SAMPLE_NAME);
}

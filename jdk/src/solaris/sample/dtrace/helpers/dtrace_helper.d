#!/usr/sbin/dtrace -s
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
 * Description:
 * dtrace -c option launches the command specified in the -c argument and
 * starts tracing the process. Typically, you can run a D script and trace
 * a Java application as follows:
 *    dscript.d -Zc "java HelloWorld"
 *
 * The -Z option is needed to permit probe descriptions that match zero
 * probes because Hotspot probes definitions are located in libjvm.so which
 * has not been yet loaded and thus can't be enabled until the application
 * is started.
 *
 * Straightforward attempt to run D script may fail, e.g.: 
 *    dscript.d -c "java HelloWorld" 
 *    "probe description hotspotPID:::probename does not match any probes"
 *
 * This is because DTrace tries to enable probes before libjvm.so is loaded.
 * The -Z option requires Solaris patch 118822-30 installed on your system.
 *
 * In case you don't have this Solaris patch use dtrace_helper.d script.
 * This script waits until the Hotspot DTrace probes are loaded and then
 * stops the Java process (passed as '-c' options). After the process is
 * stopped, another D script (passed as first argument) is called to do real
 * trace of Java process.
 *
 * Usage example:
 *   dtrace_helper.d -c "java ..." ../hotspot/class_loading_stat.d
 */

#pragma D option quiet
#pragma D option destructive


pid$target::dlopen:entry
{
    self->filename = arg0;
}


pid$target::dlopen:return
/self->filename && basename(copyinstr(self->filename)) == "libjvm.so"/
{
    printf(" loaded %s\n", basename(copyinstr(self->filename)));
    self->filename = 0;

    stop();
    printf(" stopped java process with pid=%d \n", $target);

    printf(" run: %s -p %d &", $1, $target);
    system("(%s -p %d) &", $1, $target);
    exit(0);
}

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
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


/*
 * This file defines 'jstack' function to print stack traces of
 * threads.'jstack' function which can be called once or periodically 
 * from a timer thread (calling it periodically would slow down the target
 * application). To call this once, just call 'jstack()' in script
 * console prompt. To call jtop in a timer thread, you can use
 *
 *     var t = setTimeout(function () { jstack(print); }, 5000); 
 *
 * The above call prints threads in sorted order for every 5 seconds.
 * The print output goes to OS console window from which jconsole was 
 * started. The timer can be cancelled later by clearTimeout() function
 * as shown below:
 * 
 *     clearTimeout(t);    
 */


/**
 * print given ThreadInfo using given printFunc
 */
function printThreadInfo(ti, printFunc) {
    printFunc(ti.threadId + " - " + ti.threadName + " - " + ti.threadState);
    var stackTrace = ti.stackTrace;
    for (var i in stackTrace) {
        printFunc("\t" + stackTrace[i]);
    }
}

/**
 * print stack traces of all threads. 
 *
 * @param printFunc function called to print [optional]
 * @param maxFrames maximum number of frames to print [optional]
 */
function jstack(printFunc, maxFrames) {
    // by default use 'echo' to print. Other choices could be
    // 'print' or custom function that writes in a text file
    if (printFunc == undefined) {
        printFunc = echo;
    }

    // by default print 25 frames
    if (maxFrames == undefined) {
        maxFrames = 25;
    }

    var tmbean = newPlatformMXBeanProxy(
        "java.lang:type=Threading",
        java.lang.management.ThreadMXBean);

    var tids = tmbean.allThreadIds;
    var tinfos = tmbean["getThreadInfo(long[],int)"](tids, maxFrames);

    for (var i in tinfos) {
        printThreadInfo(tinfos[i], printFunc);
    }
}

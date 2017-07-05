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
 * This script demonstrates "getMBeanAttribute"
 * and "setMBeanAttribute" functions. Instead of using
 * MXBean proxy or script wrapper object returned by
 * 'mbean' function, this file uses direct get/set MBean
 * attribute functions.
 *
 * To use this particular script, load this script file in
 * script console prompt and call verboseGC or verboseClass
 * functions. These functions based on events such as 
 * heap threshold crossing a given limit. i.e., A timer thread
 * can keep checking for threshold event and then turn on
 * verbose:gc or verbose:class based on expected event.

 */

/**
 * Get or set verbose GC flag.
 *
 * @param flag verbose mode flag [optional]
 *
 * If flag is not passed verboseGC returns current
 * flag value.
 */
function verboseGC(flag) {
    if (flag == undefined) {
        // no argument passed. interpret this as 'get'
        return getMBeanAttribute("java.lang:type=Memory", "Verbose");    
    } else {
        return setMBeanAttribute("java.lang:type=Memory", "Verbose", flag);
    }
}

/**
 * Get or set verbose class flag.
 *
 * @param flag verbose mode flag [optional]
 *
 * If flag is not passed verboseClass returns current
 * flag value.
 */
function verboseClass(flag) {
    if (flag == undefined) {
        // no argument passed. interpret this as 'get'
        return getMBeanAttribute("java.lang:type=ClassLoading", "Verbose");    
    } else {
        return setMBeanAttribute("java.lang:type=ClassLoading", "Verbose", flag);
    }
}

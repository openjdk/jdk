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
 * This file defines heapdump function to heap dump
 * in binary format. User can call this function
 * based on events. For example, a timer thread can
 * keep checking heap threshold and depending on 
 * specific expected threshold value, it can call
 * heapdump to dump the keep. File name can contain
 * timestamp so that multiple heapdumps can be generated
 * for the same process.
 */

/**
 * Function to dump heap in binary format.
 *
 * @param file heap dump file name [optional]
 */
function heapdump(file) {
    // no file specified, show file open dialog
    if (file == undefined) {
        file = fileDialog();
        // check whether user cancelled the dialog
        if (file == null) return;
    }

    /* 
     * Get HotSpotDiagnostic MBean and wrap it as convenient
     * script wrapper using 'mbean' function. Instead of using
     * MBean proxies 'mbean' function creates a script wrapper 
     * that provides similar convenience but uses explicit 
     * invocation behind the scene. This implies that mbean 
     * wrapper would the same for dynamic MBeans as well.
     */
    var diagBean = mbean("com.sun.management:type=HotSpotDiagnostic");

    // dump the heap in the file
    diagBean.dumpHeap(file, true);
}

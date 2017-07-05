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


// this checker function runs asynchronously
function memoryChecker(memoryBean, threshold, interval) {
    while (true) {
        var memUsage = memoryBean.HeapMemoryUsage;
        var usage = memUsage.get("used") / (1024 * 1024);
        println(usage);
        if (usage > threshold) {
            alert("Hey! heap usage threshold exceeded!");
            // after first alert just return.
            return;
        }
        java.lang.Thread.currentThread().sleep(interval);
    }
}


// add "Tools->Memory Monitor" menu item
if (this.application != undefined) {
    this.application.addTool("Memory Monitor", 
        function () {
            // show threshold box with default of 50 MB
            var threshold = prompt("Threshold (mb)", 50);
            // show interval box with default of 1000 millisec.
            var interval = prompt("Sample Interval (ms):", 1000);
            var memoryBean = mbean("java.lang:type=Memory");

            // ".future" makes the function to be called 
            // asynchronously in a separate thread.
            memoryChecker.future(memoryBean, threshold, interval);
        });
}


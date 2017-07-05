/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.demo.jvmti.hprof;

/* This class and it's methods are used by hprof when injecting bytecodes
 *   into class file images.
 *   See the directory src/share/demo/jvmti/hprof and the file README.txt
 *   for more details.
 */

public class Tracker {

    /* Master switch that activates calls to native functions. */

    private static int engaged = 0;

    /* To track memory allocated, we need to catch object init's and arrays. */

    /* At the beginning of java.jang.Object.<init>(), a call to
     *   Tracker.ObjectInit() is injected.
     */

    private static native void nativeObjectInit(Object thr, Object obj);

    public static void ObjectInit(Object obj)
    {
        if ( engaged != 0) {
            if (obj == null) {
                throw new IllegalArgumentException("Null object.");
            }
            nativeObjectInit(Thread.currentThread(), obj);
        }
    }

    /* Immediately following any of the newarray bytecodes, a call to
     *   Tracker.NewArray() is injected.
     */

    private static native void nativeNewArray(Object thr, Object obj);

    public static void NewArray(Object obj)
    {
        if ( engaged != 0) {
            if (obj == null) {
                throw new IllegalArgumentException("Null object.");
            }
            nativeNewArray(Thread.currentThread(), obj);
        }
    }

    /* For cpu time spent in methods, we need to inject for every method. */

    /* At the very beginning of every method, a call to
     *   Tracker.CallSite() is injected.
     */

    private static native void nativeCallSite(Object thr, int cnum, int mnum);

    public static void CallSite(int cnum, int mnum)
    {
        if ( engaged != 0 ) {
            if (cnum < 0) {
                throw new IllegalArgumentException("Negative class index");
            }

            if (mnum < 0) {
                throw new IllegalArgumentException("Negative method index");
            }

            nativeCallSite(Thread.currentThread(), cnum, mnum);
        }
    }

    /* Before any of the return bytecodes, a call to
     *   Tracker.ReturnSite() is injected.
     */

    private static native void nativeReturnSite(Object thr, int cnum, int mnum);

    public static void ReturnSite(int cnum, int mnum)
    {
        if ( engaged != 0 ) {
            if (cnum < 0) {
                throw new IllegalArgumentException("Negative class index");
            }

            if (mnum < 0) {
                throw new IllegalArgumentException("Negative method index");
            }

            nativeReturnSite(Thread.currentThread(), cnum, mnum);
        }
    }

}

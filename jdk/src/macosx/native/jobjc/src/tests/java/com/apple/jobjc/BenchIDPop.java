/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.apple.jobjc;

import com.apple.jobjc.foundation.FoundationFramework;
import com.apple.jobjc.foundation.NSString;

public class BenchIDPop extends BaseBench{
    static native long jniNSStringAlloc();
    static native long jniNSStringAllocAndRetain();
    static native long jniNSStringCached();
    static native void jniCFRetain(long x);
    static native void jniCFRelease(long x);

    final static int ITERS = 1000;
    final static FoundationFramework FND = JOBJC.Foundation();

    private static class LongWrap{
        long l;
        public LongWrap(long l){ this.l = l; }
    }

    public void testIt(){
        bench("Alloc, retain, pop a new NSString", 2, 3, 2000,
                new Task("jniNSStringAllocAndRetain()"){
            @Override public void run() {
                for(int i = 0; i < ITERS; i++)
                    jniNSStringAllocAndRetain();
            }},

            new Task("new LongWrap(jniNSStringAllocAndRetain())"){
                @Override public void run() {
                    for(int i = 0; i < ITERS; i++)
                        new LongWrap(jniNSStringAllocAndRetain());
                }},

                new Task("FND.NSString().alloc()"){
                    @Override public void run() {
                        for(int i = 0; i < ITERS; i++)
                            FND.NSString().alloc();
                    }},

                    new Task("new NSString(jniNSStringAlloc(), RUNTIME)"){
                        @Override public void run() {
                            for(int i = 0; i < ITERS; i++)
                                new NSString(jniNSStringAlloc(), RUNTIME);
                        }}
        );

        final long nsstringPtr = jniNSStringAlloc();

        bench("Get and hold an existing object", 2, 3, 2000,
                new Task("jniCFRetain(nsstringPtr)"){
            @Override public void run() {
                for(int i = 0; i < ITERS; i++)
                    jniCFRetain(nsstringPtr);
            }},

            new Task("jniCFRetain(new LongWrap(nsstringPtr).l"){
                @Override public void run() {
                    for(int i = 0; i < ITERS; i++)
                        jniCFRetain(new LongWrap(nsstringPtr).l);
                }},

                new Task("ID.getInstance(nsstringPtr, RUNTIME)"){
                    @Override public void run() {
                        for(int i = 0; i < ITERS; i++)
                            ID.getInstance(nsstringPtr, RUNTIME);
                    }}
        );
    }

}

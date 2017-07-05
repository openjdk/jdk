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

import java.util.concurrent.Callable;

import com.apple.jobjc.foundation.NSString;

public class UtilsTest extends PooledTestCase{
    public void testStrings(){
        String s = "fooBarBazDazzle";
        NSString ns = Utils.get().strings().nsString(s);
        String t = Utils.get().strings().javaString(ns);
        assertEquals(s, t);
    }

    public void testThreadsPerformRunnableOnMainThread(){
        final long testThreadId = Thread.currentThread().getId();
        class Wrap{ public long x = testThreadId; }
        final Wrap wrap = new Wrap();
        assertTrue(testThreadId == wrap.x);

        Utils.get().threads().performOnMainThread(new Runnable(){
            public void run() {
                wrap.x = Thread.currentThread().getId();
            }
        }, true);

        assertTrue(testThreadId != wrap.x);
    }

    public void testThreadsPerformCallableOnMainThread() throws Exception{
        final long testThreadId = Thread.currentThread().getId();
        final long mainThreadId = Utils.get().threads().performOnMainThread(new Callable<Long>(){
            public Long call() { return Thread.currentThread().getId(); }
        });
        assertTrue(testThreadId != mainThreadId);
    }

    public void testThreadsPerformCallableOnMainThreadException() throws Exception{
        class FooException extends RuntimeException{}
        try {
            Utils.get().threads().performOnMainThread(new Callable<Object>(){
                public Object call() { throw new FooException(); }
            });
        } catch (FooException e) {
            return;
        }
        fail("Failed to catch exception.");
    }

    public static void main(String[] args){
        junit.textui.TestRunner.run(UtilsTest.class);
    }
}


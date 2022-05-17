/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/**
 * @test
 * @summary Test that preview APIs throws exception when preview features not enabled
 * @run testng/othervm PreviewFeaturesNotEnabled
 */

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Executors;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class PreviewFeaturesNotEnabled {

    /**
     * Thread.ofVirtual should fail with UOE.
     */
    @Test
    public void testOfVirtual() throws Exception {
        Method ofVirtual = Thread.class.getDeclaredMethod("ofVirtual");
        var exc = expectThrows(InvocationTargetException.class, () -> ofVirtual.invoke(null));
        assertTrue(exc.getCause() instanceof UnsupportedOperationException);
    }

    /**
     * Thread.startVirtualThread should fail with UOE.
     */
    @Test
    public void testStartVirutalThread() throws Exception {
        Method startVirtualThread = Thread.class.getMethod("startVirtualThread", Runnable.class);
        Runnable task = () -> { };
        var exc = expectThrows(InvocationTargetException.class,
                () -> startVirtualThread.invoke(null, task));
        assertTrue(exc.getCause() instanceof UnsupportedOperationException);
    }

    /**
     * Executors.newVirtualThreadPerTaskExecutor should fail with UOE.
     */
    @Test
    public void testNewVirtualThreadPerTaskExecutor() throws Exception {
        Method newVirtualThreadPerTaskExecutor = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
        var exc = expectThrows(InvocationTargetException.class,
                () -> newVirtualThreadPerTaskExecutor.invoke(null));
        assertTrue(exc.getCause() instanceof UnsupportedOperationException);
    }

    /**
     * Directly accessing internal Continuation class should fail with UOE.
     */
    @Test
    public void testContinuationInitializer() throws Exception {
        var exc = expectThrows(ExceptionInInitializerError.class,
                () -> Class.forName("jdk.internal.vm.Continuation"));
        assertTrue(exc.getCause() instanceof UnsupportedOperationException);
    }

    /**
     * Thread.isVirtual should not fail.
     */
    @Test
    public void testIsVirtual() throws Exception {
        boolean isVirtual = isVirtual(Thread.currentThread());
        assertFalse(isVirtual);
    }

    /**
     * Thread.ofPlatform should not fail.
     */
    @Test
    public void testOfPlatform() throws Exception {
        Method ofPlatform = Thread.class.getDeclaredMethod("ofPlatform");
        Object builder = ofPlatform.invoke(null);
        Method startMethod = Class.forName("java.lang.Thread$Builder")
                .getMethod("start", Runnable.class);
        Runnable task = () -> { };
        Thread thread = (Thread) startMethod.invoke(builder, task);
    }

    /**
     * Invokes Thread::isVirtual reflectively to test if the given thread is a
     * virtual thread.
     */
    private static boolean isVirtual(Thread thread) throws Exception {
        Method isVirtualMethod = Thread.class.getDeclaredMethod("isVirtual");
        return (boolean) isVirtualMethod.invoke(thread);
    }
}

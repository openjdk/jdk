/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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

package nsk.share;
import java.lang.ref.Cleaner;

/**
 * Finalizable interface allows <tt>Finalizer</tt> to perform finalization of an object.
 * Each object that requires finalization at VM shutdown time should implement this
 * interface and call the <tt>registerCleanup</tt> to activate a <tt>Finalizer</tt> hook.
 *
 * @see Finalizer
 */
public interface Finalizable {

    /**
     * This method will be implemented by FinalizableObject and is called in <tt>finalizeAtExit</tt>.
     *
     * @see Finalizer
     */
    public void cleanup();

    /**
     * This method will be invoked by <tt>Finalizer</tt> when virtual machine
     * shuts down.
     *
     * @throws Throwable if any throwable exception thrown during finalization
     */
    default public void finalizeAtExit() throws Throwable {
        cleanup();
    }

    /**
     * This method will register a cleanup method and create an instance of Finalizer
     * to register the object for finalization at VM exit.
     *
     * @see Finalizer
     */
    default public void registerCleanup() {
       Finalizer finalizer = new Finalizer(this);
       finalizer.activate();

       Cleaner.create().register(this, () -> cleanup());
    }
}

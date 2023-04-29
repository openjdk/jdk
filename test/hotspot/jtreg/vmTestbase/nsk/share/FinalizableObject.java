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
 * This class is an simple exalmple of finalizable object, that implements interface
 * <code>Finalizable</code>.
 *
 * @see Finalizable
 * @see Finalizer
 */
public class FinalizableObject implements Finalizable {


    /**
     * This method will be invoked by <tt>Finalizer</tt> when virtual mashine
     * shuts down.
     *
     * @throws Throwable if any throwable exception thrown during finalization
     *
     * @see Finalizer
     */

    public void cleanup() {}
    /**
     * This method will be invoked by <tt>Finalizer</tt> when virtual mashine
     * shuts down.
     *
     * @throws Throwable if any throwable exception thrown during finalization
     *
     * @see Finalizer
     */
    public void finalizeAtExit() throws Throwable {
        cleanup();
    }

    public void registerCleanup() {
       // install finalizer to print errors summary at exit
       Finalizer finalizer = new Finalizer(this);
       finalizer.activate();

       // register the cleanup method to be called when this Log instance becomes unreachable.
       Cleaner.create().register(this, () -> cleanup());
    }
}

/*
 * Copyright (C) 2022, Tencent. All rights reserved.
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

/*
 * @test
 * @bug 8284490
 * @summary Remove finalizer method in java.security.jgss
 * @key intermittent
 * @library /test/lib/
 * @build jdk.test.lib.util.ForceGC
 * @run main/othervm GssContextCleanup
 */

import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSManager;

import java.lang.ref.WeakReference;

import jdk.test.lib.util.ForceGC;

public final class GssContextCleanup {
    public static void main(String[] args) throws Exception {
        // Enable debug log so that the failure analysis could be easier.
        System.setProperty("sun.security.nativegss.debug", "true");

        // Use native provider
        System.setProperty("sun.security.jgss.native", "true");

        // Create an object
        GSSManager manager = GSSManager.getInstance();
        GSSContext context = manager.createContext((GSSCredential)null);
        WeakReference<GSSContext> weakRef = new WeakReference<>(context);
        context = null;

        // Check if the object has been collected.
        if (!ForceGC.wait(() -> weakRef.refersTo(null))) {
            throw new RuntimeException("GSSContext object is not released");
        }
    }
}

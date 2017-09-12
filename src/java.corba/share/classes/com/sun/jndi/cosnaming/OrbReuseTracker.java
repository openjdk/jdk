/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.jndi.cosnaming;

import org.omg.CORBA.ORB;

/**
 * This class keeps track of references to the shared ORB object
 * and destroys it when no more references are made to the ORB
 * object. This object is created for each ORB object that CNCtx
 * creates.
 */
class OrbReuseTracker {

    int referenceCnt;
    ORB orb;

    private static final boolean debug = false;

    OrbReuseTracker(ORB orb) {
        this.orb = orb;
        referenceCnt++;
        if (debug) {
             System.out.println("New OrbReuseTracker created");
        }
    }

    synchronized void incRefCount() {
        referenceCnt++;
        if (debug) {
             System.out.println("Increment orb ref count to:" + referenceCnt);
        }
    }

    synchronized void decRefCount() {
        referenceCnt--;
        if (debug) {
             System.out.println("Decrement orb ref count to:" + referenceCnt);
        }
        if ((referenceCnt == 0)) {
            if (debug) {
                System.out.println("Destroying the ORB");
            }
            orb.destroy();
        }
    }
}

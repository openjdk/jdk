/*
 * Copyright 2002-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package sun.nio.ch;


// Special-purpose data structure for sets of native threads


class NativeThreadSet {

    private long[] elts;
    private int used = 0;

    NativeThreadSet(int n) {
        elts = new long[n];
    }

    // Adds the current native thread to this set, returning its index so that
    // it can efficiently be removed later.
    //
    int add() {
        long th = NativeThread.current();
        if (th == -1)
            return -1;
        synchronized (this) {
            int start = 0;
            if (used >= elts.length) {
                int on = elts.length;
                int nn = on * 2;
                long[] nelts = new long[nn];
                System.arraycopy(elts, 0, nelts, 0, on);
                elts = nelts;
                start = on;
            }
            for (int i = start; i < elts.length; i++) {
                if (elts[i] == 0) {
                    elts[i] = th;
                    used++;
                    return i;
                }
            }
            assert false;
            return -1;
        }
    }

    // Removes the thread at the given index.
    //
    void remove(int i) {
        if (i < 0)
            return;
        synchronized (this) {
            elts[i] = 0;
            used--;
        }
    }

    // Signals all threads in this set.
    //
    void signal() {
        synchronized (this) {
            int u = used;
            int n = elts.length;
            for (int i = 0; i < n; i++) {
                long th = elts[i];
                if (th == 0)
                    continue;
                NativeThread.signal(th);
                if (--u == 0)
                    break;
            }
        }
    }

}

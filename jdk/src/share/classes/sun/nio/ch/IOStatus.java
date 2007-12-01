/*
 * Copyright 2002-2003 Sun Microsystems, Inc.  All Rights Reserved.
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


// Constants for reporting I/O status

final class IOStatus {

    private IOStatus() { }

    static final int EOF = -1;              // End of file
    static final int UNAVAILABLE = -2;      // Nothing available (non-blocking)
    static final int INTERRUPTED = -3;      // System call interrupted
    static final int UNSUPPORTED = -4;      // Operation not supported
    static final int THROWN = -5;           // Exception thrown in JNI code
    static final int UNSUPPORTED_CASE = -6; // This case not supported

    // The following two methods are for use in try/finally blocks where a
    // status value needs to be normalized before being returned to the invoker
    // but also checked for illegal negative values before the return
    // completes, like so:
    //
    //     int n = 0;
    //     try {
    //         begin();
    //         n = op(fd, buf, ...);
    //         return IOStatus.normalize(n);    // Converts UNAVAILABLE to zero
    //     } finally {
    //         end(n > 0);
    //         assert IOStatus.check(n);        // Checks other negative values
    //     }
    //

    static int normalize(int n) {
        if (n == UNAVAILABLE)
            return 0;
        return n;
    }

    static boolean check(int n) {
        return (n >= UNAVAILABLE);
    }

    static long normalize(long n) {
        if (n == UNAVAILABLE)
            return 0;
        return n;
    }

    static boolean check(long n) {
        return (n >= UNAVAILABLE);
    }

    // Return true iff n is not one of the IOStatus values
    static boolean checkAll(long n) {
        return ((n > EOF) || (n < UNSUPPORTED_CASE));
    }

}

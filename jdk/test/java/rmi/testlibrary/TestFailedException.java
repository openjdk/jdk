/*
 * Copyright 1999 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/**/


/**
 * Simple class to wrap test failure exceptions in a RuntimeException.
 * Provides a detail exception and a message.
 */
public class TestFailedException extends java.lang.RuntimeException {
    public Throwable detail;

    public TestFailedException() {}

    public TestFailedException(String s) {
        super(s);
    }

    public TestFailedException(String s, Throwable ex) {
        super(s);
        detail = ex;
    }

    public String getMessage() {
        if (detail == null)
            return super.getMessage();
        else
            return super.getMessage() +
                "; nested exception is: \n\t" +
                detail.toString();
    }

    public void printStackTrace(java.io.PrintStream ps)
    {
        if (detail == null) {
            super.printStackTrace(ps);
        } else {
            synchronized(ps) {
                ps.println(this);
                detail.printStackTrace(ps);
            }
        }
    }

    public void printStackTrace()
    {
        printStackTrace(System.err);
    }

    public void printStackTrace(java.io.PrintWriter pw)
    {
        if (detail == null) {
            super.printStackTrace(pw);
        } else {
            synchronized(pw) {
                pw.println(this);
                detail.printStackTrace(pw);
            }
        }
    }
}

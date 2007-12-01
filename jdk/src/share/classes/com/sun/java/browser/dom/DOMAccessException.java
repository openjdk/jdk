/*
 * Copyright 2000 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.java.browser.dom;

public class DOMAccessException extends Exception
{
    /**
     * Constructs a new DOMAccessException with no detail message.
     */
    public DOMAccessException()
    {
        this(null, null);
    }


    /**
     * Constructs a new DOMAccessException with the given detail message.
     *
     * @param msg Detail message.
     */
    public DOMAccessException(String msg)
    {
        this(null, msg);
    }

    /**
     * Constructs a new DOMAccessException with the given exception as a root clause.
     *
     * @param e Exception.
     */
    public DOMAccessException(Exception e)
    {
        this(e, null);
    }

    /**
     * Constructs a new DOMAccessException with the given exception as a root clause and the given detail message.
     *
     * @param e Exception.
     * @param msg Detail message.
     */
    public DOMAccessException(Exception e, String msg)
    {
        this.ex = e;
        this.msg = msg;
    }

    /**
     * Returns the detail message of the error or null if there is no detail message.
     */
    public String getMessage()
    {
        return msg;
    }

    /**
     * Returns the root cause of the error or null if there is none.
     */
    public Throwable getCause()
    {
        return ex;
    }

    private Throwable ex;
    private String msg;
}

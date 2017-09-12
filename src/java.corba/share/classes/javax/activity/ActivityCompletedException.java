/*
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
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

package javax.activity;

/**
 * This exception may be thrown on any method for which Activity context is
 * accessed and indicates that ongoing work within the Activity is not
 * possible. This may be because the Activity has been instructed to complete
 * with CompletionStatusFailOnly or has ended as a result of a timeout. This
 * exception will be propagated across ORB boundaries via an
 * org.omg.CORBA.ACTIVITY_COMPLETED system exception. An application should
 * handle this error by attempting to complete the Activity.
 */
public class ActivityCompletedException extends java.rmi.RemoteException
{
    /**
     * Constructs a new instance with null as its detail message.
     */
    public ActivityCompletedException() { super(); }

    /**
     * Constructs a new instance with the specified detail message.
     *
     * @param message the detail message.
     */
    public ActivityCompletedException(String message) {
        super(message);
    }

    /**
     * Constructs a new throwable with the specified cause.
     *
     * @param cause a chained exception of type
     * <code>Throwable</code>.
     */
    public ActivityCompletedException(Throwable cause) {
        this("", cause);
    }

    /**
     * Constructs a new throwable with the specified detail message and cause.
     *
     * @param message the detail message.
     *
     * @param cause a chained exception of type
     * <code>Throwable</code>.
     */
    public ActivityCompletedException(String message, Throwable cause) {
        super(message, cause);
    }
}

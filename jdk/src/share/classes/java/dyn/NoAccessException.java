/*
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
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

package java.dyn;

/**
 * Thrown to indicate that a caller has attempted to create a method handle
 * which accesses a field, method, or class to which the caller does not have access.
 * This unchecked exception is analogous to {@link IllegalAccessException},
 * which is a checked exception thrown when reflective invocation fails
 * because of an access check.  With method handles, this same access
 * checking is performed by the {@link MethodHandles.Lookup lookup object}
 * on behalf of the method handle creator,
 * at the time of creation.
 * @author John Rose, JSR 292 EG
 * @since 1.7
 */
public class NoAccessException extends ReflectiveOperationException {
    private static final long serialVersionUID = 292L;

    /**
     * Constructs a {@code NoAccessException} with no detail message.
     */
    public NoAccessException() {
        super();
    }

    /**
     * Constructs a {@code NoAccessException} with the specified
     * detail message.
     *
     * @param s the detail message
     */
    public NoAccessException(String s) {
        super(s);
    }

    /**
     * Constructs a {@code NoAccessException} with the specified cause.
     *
     * @param cause the underlying cause of the exception
     */
    public NoAccessException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a {@code NoAccessException} with the specified
     * detail message and cause.
     *
     * @param s the detail message
     * @param cause the underlying cause of the exception
     */
    public NoAccessException(String s, Throwable cause) {
        super(s, cause);
    }
}

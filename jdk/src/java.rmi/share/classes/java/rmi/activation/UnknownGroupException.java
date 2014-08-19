/*
 * Copyright (c) 1997, 1999, Oracle and/or its affiliates. All rights reserved.
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

package java.rmi.activation;

/**
 * An <code>UnknownGroupException</code> is thrown by methods of classes and
 * interfaces in the <code>java.rmi.activation</code> package when the
 * <code>ActivationGroupID</code> parameter to the method is determined to be
 * invalid, i.e., not known by the <code>ActivationSystem</code>.  An
 * <code>UnknownGroupException</code> is also thrown if the
 * <code>ActivationGroupID</code> in an <code>ActivationDesc</code> refers to
 * a group that is not registered with the <code>ActivationSystem</code>
 *
 * @author  Ann Wollrath
 * @since   1.2
 * @see     java.rmi.activation.Activatable
 * @see     java.rmi.activation.ActivationGroup
 * @see     java.rmi.activation.ActivationGroupID
 * @see     java.rmi.activation.ActivationMonitor
 * @see     java.rmi.activation.ActivationSystem
 */
public class UnknownGroupException extends ActivationException {

    /** indicate compatibility with the Java 2 SDK v1.2 version of class */
    private static final long serialVersionUID = 7056094974750002460L;

    /**
     * Constructs an <code>UnknownGroupException</code> with the specified
     * detail message.
     *
     * @param s the detail message
     * @since 1.2
     */
    public UnknownGroupException(String s) {
        super(s);
    }
}

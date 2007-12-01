/*
 * Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.attach;

import com.sun.tools.attach.spi.AttachProvider;         // for javadoc

/**
 * Thrown by {@link com.sun.tools.attach.VirtualMachine#attach
 * VirtalMachine.attach} when attempting to attach to a Java virtual machine
 * for which a compatible {@link com.sun.tools.attach.spi.AttachProvider
 * AttachProvider} does not exist. It is also thrown by {@link
 * com.sun.tools.attach.spi.AttachProvider#attachVirtualMachine
 * AttachProvider.attachVirtualMachine} if the provider attempts to
 * attach to a Java virtual machine with which it not comptatible.
 */
public class AttachNotSupportedException extends Exception {

    /** use serialVersionUID for interoperability */
    static final long serialVersionUID = 3391824968260177264L;

    /**
     * Constructs an <code>AttachNotSupportedException</code> with
     * no detail message.
     */
    public AttachNotSupportedException() {
        super();

    }

    /**
     * Constructs an <code>AttachNotSupportedException</code> with
     * the specified detail message.
     *
     * @param   s   the detail message.
     */
    public AttachNotSupportedException(String s) {
        super(s);
    }

}

/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.ws.client.sei;

import javax.xml.ws.WebServiceException;

/**
 * Handles an invocation of a method.
 *
 * <p>
 * Each instance of {@link MethodHandler} has an implicit knowledge of
 * a particular method that it handles.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class MethodHandler {

    protected final SEIStub owner;

    protected MethodHandler(SEIStub owner) {
        this.owner = owner;
    }

    /**
     * Performs the method invocation.
     *
     * @param proxy
     *      The proxy object exposed to the user. Must not be null.
     * @param args
     *      The method invocation arguments. To handle asynchroonus method invocations
     *      without array reallocation, this aray is allowed to be longer than the
     *      actual number of arguments to the method. Additional array space should be
     *      simply ignored.
     * @return
     *      a return value from the method invocation. may be null.
     *
     * @throws WebServiceException
     *      If used on the client side, a {@link WebServiceException} signals an error
     *      during the service invocation.
     * @throws Throwable
     *      some faults are reported in terms of checked exceptions.
     */
    abstract Object invoke(Object proxy, Object[] args) throws WebServiceException, Throwable;
}

/*
 * Copyright (c) 1999, 2011, Oracle and/or its affiliates. All rights reserved.
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

/*
 * This source code is provided to illustrate the usage of a given feature
 * or technique and has been deliberately simplified. Additional steps
 * required for a production-quality application, such as security checks,
 * input validation and proper error handling, might not be present in
 * this sample code.
 */


package com.sun.tools.example.debug.bdi;

import com.sun.jdi.ReferenceType;

public class ExceptionSpec extends EventRequestSpec {

    boolean notifyCaught;
    boolean notifyUncaught;

    ExceptionSpec(EventRequestSpecList specs, ReferenceTypeSpec refSpec,
                  boolean notifyCaught, boolean notifyUncaught)
    {
        super(specs, refSpec);
        this.notifyCaught = notifyCaught;
        this.notifyUncaught = notifyUncaught;
    }

    @Override
    void notifySet(SpecListener listener, SpecEvent evt) {
        listener.exceptionInterceptSet(evt);
    }

    @Override
    void notifyDeferred(SpecListener listener, SpecEvent evt) {
        listener.exceptionInterceptDeferred(evt);
    }

    @Override
    void notifyResolved(SpecListener listener, SpecEvent evt) {
        listener.exceptionInterceptResolved(evt);
    }

    @Override
    void notifyDeleted(SpecListener listener, SpecEvent evt) {
        listener.exceptionInterceptDeleted(evt);
    }

    @Override
    void notifyError(SpecListener listener, SpecErrorEvent evt) {
        listener.exceptionInterceptError(evt);
    }

    /**
     * The 'refType' is known to match.
     */
    @Override
    void resolve(ReferenceType refType) {
        setRequest(refType.virtualMachine().eventRequestManager()
                   .createExceptionRequest(refType,
                                           notifyCaught, notifyUncaught));
    }

    @Override
    public int hashCode() {
        return refSpec.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ExceptionSpec) {
            ExceptionSpec es = (ExceptionSpec)obj;

            return refSpec.equals(es.refSpec);
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer("exception catch ");
        buffer.append(refSpec.toString());
        return buffer.toString();
    }
}

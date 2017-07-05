/*
 * Copyright (c) 1999, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.example.debug.bdi;

import com.sun.jdi.ReferenceType;
import com.sun.jdi.request.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

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

    void notifySet(SpecListener listener, SpecEvent evt) {
        listener.exceptionInterceptSet(evt);
    }

    void notifyDeferred(SpecListener listener, SpecEvent evt) {
        listener.exceptionInterceptDeferred(evt);
    }

    void notifyResolved(SpecListener listener, SpecEvent evt) {
        listener.exceptionInterceptResolved(evt);
    }

    void notifyDeleted(SpecListener listener, SpecEvent evt) {
        listener.exceptionInterceptDeleted(evt);
    }

    void notifyError(SpecListener listener, SpecErrorEvent evt) {
        listener.exceptionInterceptError(evt);
    }

    /**
     * The 'refType' is known to match.
     */
    void resolve(ReferenceType refType) {
        setRequest(refType.virtualMachine().eventRequestManager()
                   .createExceptionRequest(refType,
                                           notifyCaught, notifyUncaught));
    }

    public int hashCode() {
        return refSpec.hashCode();
    }

    public boolean equals(Object obj) {
        if (obj instanceof ExceptionSpec) {
            ExceptionSpec es = (ExceptionSpec)obj;

            return refSpec.equals(es.refSpec);
        } else {
            return false;
        }
    }

    public String toString() {
        StringBuffer buffer = new StringBuffer("exception catch ");
        buffer.append(refSpec.toString());
        return buffer.toString();
    }
}

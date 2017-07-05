/*
 * Copyright 1999 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.example.debug.bdi;

import com.sun.jdi.*;
import com.sun.jdi.request.*;

public abstract class WatchpointSpec extends EventRequestSpec {
    final String fieldId;

    WatchpointSpec(EventRequestSpecList specs,
                   ReferenceTypeSpec refSpec, String fieldId) {
        super(specs, refSpec);
        this.fieldId = fieldId;
//        if (!isJavaIdentifier(fieldId)) {
//            throw new MalformedMemberNameException(fieldId);
//        }
    }

    void notifySet(SpecListener listener, SpecEvent evt) {
        listener.watchpointSet(evt);
    }

    void notifyDeferred(SpecListener listener, SpecEvent evt) {
        listener.watchpointDeferred(evt);
    }

    void notifyResolved(SpecListener listener, SpecEvent evt) {
        listener.watchpointResolved(evt);
    }

    void notifyDeleted(SpecListener listener, SpecEvent evt) {
        listener.watchpointDeleted(evt);
    }

    void notifyError(SpecListener listener, SpecErrorEvent evt) {
        listener.watchpointError(evt);
    }

    public int hashCode() {
        return refSpec.hashCode() + fieldId.hashCode() +
            getClass().hashCode();
    }

    public boolean equals(Object obj) {
        if (obj instanceof WatchpointSpec) {
            WatchpointSpec watchpoint = (WatchpointSpec)obj;

            return fieldId.equals(watchpoint.fieldId) &&
                   refSpec.equals(watchpoint.refSpec) &&
                   getClass().equals(watchpoint.getClass());
        } else {
            return false;
        }
    }

    public String errorMessageFor(Exception e) {
        if (e instanceof NoSuchFieldException) {
            return ("No field " + fieldId + " in " + refSpec);
        } else {
            return super.errorMessageFor(e);
        }
    }
}

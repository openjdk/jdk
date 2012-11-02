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

import com.sun.jdi.*;
import java.util.List;

public class LineBreakpointSpec extends BreakpointSpec {
    int lineNumber;

    LineBreakpointSpec(EventRequestSpecList specs,
                       ReferenceTypeSpec refSpec, int lineNumber) {
        super(specs, refSpec);
        this.lineNumber = lineNumber;
    }

    /**
     * The 'refType' is known to match.
     */
    @Override
    void resolve(ReferenceType refType) throws InvalidTypeException,
                                             LineNotFoundException {
        if (!(refType instanceof ClassType)) {
            throw new InvalidTypeException();
        }
        Location location = location((ClassType)refType);
        setRequest(refType.virtualMachine().eventRequestManager()
                   .createBreakpointRequest(location));
    }

    private Location location(ClassType clazz) throws
                                            LineNotFoundException {
        Location location = null;
        try {
            List<Location> locs = clazz.locationsOfLine(lineNumber());
            if (locs.size() == 0) {
                throw new LineNotFoundException();
            }
            // TODO handle multiple locations
            location = locs.get(0);
            if (location.method() == null) {
                throw new LineNotFoundException();
            }
        } catch (AbsentInformationException e) {
            /*
             * TO DO: throw something more specific, or allow
             * AbsentInfo exception to pass through.
             */
            throw new LineNotFoundException();
        }
        return location;
    }

    public int lineNumber() {
        return lineNumber;
    }

    @Override
    public int hashCode() {
        return refSpec.hashCode() + lineNumber;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof LineBreakpointSpec) {
            LineBreakpointSpec breakpoint = (LineBreakpointSpec)obj;

            return refSpec.equals(breakpoint.refSpec) &&
                   (lineNumber == breakpoint.lineNumber);
        } else {
            return false;
        }
    }

    @Override
    public String errorMessageFor(Exception e) {
        if (e instanceof LineNotFoundException) {
            return ("No code at line " + lineNumber() + " in " + refSpec);
        } else if (e instanceof InvalidTypeException) {
            return ("Breakpoints can be located only in classes. " +
                        refSpec + " is an interface or array");
        } else {
            return super.errorMessageFor( e);
        }
    }

    @Override
    public String toString() {
        StringBuffer buffer = new StringBuffer("breakpoint ");
        buffer.append(refSpec.toString());
        buffer.append(':');
        buffer.append(lineNumber);
        buffer.append(" (");
        buffer.append(getStatusString());
        buffer.append(')');
        return buffer.toString();
    }
}

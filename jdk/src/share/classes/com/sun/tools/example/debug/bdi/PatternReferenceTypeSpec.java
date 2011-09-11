/*
 * Copyright (c) 1999, 2003, Oracle and/or its affiliates. All rights reserved.
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
import java.util.StringTokenizer;

class PatternReferenceTypeSpec implements ReferenceTypeSpec {
    final boolean isWild;
    final String classId;

    PatternReferenceTypeSpec(String classId)
//                             throws ClassNotFoundException
    {
//        checkClassName(classId);
        isWild = classId.startsWith("*.");
        if (isWild) {
            this.classId = classId.substring(1);
        } else {
            this.classId = classId;
        }
    }

    /**
     * Does the specified ReferenceType match this spec.
     */
    @Override
    public boolean matches(ReferenceType refType) {
        if (isWild) {
            return refType.name().endsWith(classId);
        } else {
            return refType.name().equals(classId);
        }
    }

    @Override
    public int hashCode() {
        return classId.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PatternReferenceTypeSpec) {
            PatternReferenceTypeSpec spec = (PatternReferenceTypeSpec)obj;

            return classId.equals(spec.classId) && (isWild == spec.isWild);
        } else {
            return false;
        }
    }

    private void checkClassName(String className) throws ClassNotFoundException {
        // Do stricter checking of class name validity on deferred
        //  because if the name is invalid, it will
        // never match a future loaded class, and we'll be silent
        // about it.
        StringTokenizer tokenizer = new StringTokenizer(className, ".");
        boolean first = true;
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            // Each dot-separated piece must be a valid identifier
            // and the first token can also be "*". (Note that
            // numeric class ids are not permitted. They must
            // match a loaded class.)
            if (!Utils.isJavaIdentifier(token) && !(first && token.equals("*"))) {
                throw new ClassNotFoundException();
            }
            first = false;
        }
    }

    @Override
    public String toString() {
        return isWild? "*" + classId : classId;
    }
}

/*
 * Copyright 1999-2003 Sun Microsystems, Inc.  All Rights Reserved.
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
    public boolean matches(ReferenceType refType) {
        if (isWild) {
            return refType.name().endsWith(classId);
        } else {
            return refType.name().equals(classId);
        }
    }

    public int hashCode() {
        return classId.hashCode();
    }

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

    public String toString() {
        return isWild? "*" + classId : classId;
    }
}

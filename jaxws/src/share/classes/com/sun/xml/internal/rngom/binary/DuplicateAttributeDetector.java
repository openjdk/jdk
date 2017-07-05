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
package com.sun.xml.internal.rngom.binary;

import java.util.ArrayList;
import java.util.List;

import com.sun.xml.internal.rngom.nc.NameClass;

class DuplicateAttributeDetector {
    private List nameClasses = new ArrayList();
    private Alternative alternatives = null;

    private static class Alternative {
        private int startIndex;
        private int endIndex;
        private Alternative parent;

        private Alternative(int startIndex, Alternative parent) {
            this.startIndex = startIndex;
            this.endIndex = startIndex;
            this.parent = parent;
        }
    }

    boolean addAttribute(NameClass nc) {
        int lim = nameClasses.size();
        for (Alternative a = alternatives; a != null; a = a.parent) {
            for (int i = a.endIndex; i < lim; i++)
                if (nc.hasOverlapWith((NameClass) nameClasses.get(i)))
                    return false;
            lim = a.startIndex;
        }
        for (int i = 0; i < lim; i++)
            if (nc.hasOverlapWith((NameClass) nameClasses.get(i)))
                return false;
        nameClasses.add(nc);
        return true;
    }

    void startChoice() {
        alternatives = new Alternative(nameClasses.size(), alternatives);
    }

    void alternative() {
        alternatives.endIndex = nameClasses.size();
    }

    void endChoice() {
        alternatives = alternatives.parent;
    }

}

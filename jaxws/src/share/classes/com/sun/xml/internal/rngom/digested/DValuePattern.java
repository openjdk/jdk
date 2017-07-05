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
package com.sun.xml.internal.rngom.digested;

import com.sun.xml.internal.rngom.parse.Context;

/**
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public class DValuePattern extends DPattern {
    private String datatypeLibrary;
    private String type;
    private String value;
    private Context context;
    private String ns;

    public DValuePattern(String datatypeLibrary, String type, String value, Context context, String ns) {
        this.datatypeLibrary = datatypeLibrary;
        this.type = type;
        this.value = value;
        this.context = context;
        this.ns = ns;
    }

    public String getDatatypeLibrary() {
        return datatypeLibrary;
    }

    public String getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public Context getContext() {
        return context;
    }

    public String getNs() {
        return ns;
    }

    public boolean isNullable() {
        return false;
    }

    public Object accept( DPatternVisitor visitor ) {
        return visitor.onValue(this);
    }
}

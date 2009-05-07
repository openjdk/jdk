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

import com.sun.xml.internal.rngom.ast.om.Location;
import com.sun.xml.internal.rngom.parse.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Kohsuke Kawaguchi (kk@kohsuke.org)
 */
public class DDataPattern extends DPattern {
    DPattern except;

    String datatypeLibrary;
    String type;

    final List<Param> params = new ArrayList<Param>();

    /**
     * Parameter to a data pattern.
     */
    public final class Param {
        String name;
        String value;
        Context context;
        String ns;
        Location loc;
        Annotation anno;

        public Param(String name, String value, Context context, String ns, Location loc, Annotation anno) {
            this.name = name;
            this.value = value;
            this.context = context;
            this.ns = ns;
            this.loc = loc;
            this.anno = anno;
        }

        public String getName() {
            return name;
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

        public Location getLoc() {
            return loc;
        }

        public Annotation getAnno() {
            return anno;
        }
    }

    /**
     * Gets the datatype library URI.
     *
     * @return
     *      Can be empty (which represents the built-in datatypes), but never null.
     */
    public String getDatatypeLibrary() {
        return datatypeLibrary;
    }

    /**
     * Gets the datatype name, such as "int" or "token".
     *
     * @return
     *      never null.
     */
    public String getType() {
        return type;
    }

    /**
     * Gets the parameters of this &lt;data pattern.
     *
     * @return
     *      can be empty but never null.
     */
    public List<Param> getParams() {
        return params;
    }

    /**
     * Gets the pattern that reprsents the &lt;except> child of this data pattern.
     *
     * @return null if not exist.
     */
    public DPattern getExcept() {
        return except;
    }

    public boolean isNullable() {
        return false;
    }

    public Object accept( DPatternVisitor visitor ) {
        return visitor.onData(this);
    }
}

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
package com.sun.tools.internal.xjc.reader.xmlschema;

import org.xml.sax.Locator;

/**
 * Details of a name collision.
 *
 * @author
 *     Kohsuke Kawaguchi (kohsuke.kawaguchi@sun.com)
 */
final class CollisionInfo {
    private final String name;
    private final Locator source1;
    private final Locator source2;

    public CollisionInfo(String name, Locator source1, Locator source2) {
        this.name = name;
        this.source1 = source1;
        this.source2 = source2;
    }

    /**
     * Returns a localized message that describes the collision.
     */
    public String toString() {
        return Messages.format( Messages.MSG_COLLISION_INFO,
                name, printLocator(source1), printLocator(source2) );
    }

    private String printLocator(Locator loc) {
        if( loc==null )     return "";

        int line = loc.getLineNumber();
        String sysId = loc.getSystemId();
        if(sysId==null)     sysId = Messages.format(Messages.MSG_UNKNOWN_FILE);

        if( line!=-1 )
            return Messages.format( Messages.MSG_LINE_X_OF_Y,
                    Integer.toString(line), sysId );
        else
            return sysId;
    }
}

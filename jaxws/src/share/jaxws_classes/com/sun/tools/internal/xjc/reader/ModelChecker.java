/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.internal.xjc.reader;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import javax.xml.namespace.QName;

import com.sun.tools.internal.xjc.ErrorReceiver;
import com.sun.tools.internal.xjc.model.CClassInfo;
import com.sun.tools.internal.xjc.model.CPropertyInfo;
import com.sun.tools.internal.xjc.model.Model;

/**
 * Checks errors on model classes.
 *
 * <p>
 * This should be used as a {@link Ring} component.
 *
 * @author Kohsuke Kawaguchi
 */
public final class ModelChecker {
    private final Model model = Ring.get(Model.class);
    private final ErrorReceiver errorReceiver = Ring.get(ErrorReceiver.class);

    public ModelChecker() {
    }

    public void check() {
        for( CClassInfo ci : model.beans().values() )
            check(ci);
    }

    private void check( CClassInfo ci ) {
        List<CPropertyInfo> props = ci.getProperties();
        Map<QName,CPropertyInfo> collisionTable = new HashMap<QName,CPropertyInfo>();

        OUTER:
        for( int i=0; i<props.size(); i++ ) {
            CPropertyInfo p1 = props.get(i);

            if(p1.getName(true).equals("Class")) {
                errorReceiver.error(p1.locator,Messages.PROPERTY_CLASS_IS_RESERVED.format());
                continue;
            }

            QName n = p1.collectElementNames(collisionTable);
            if(n!=null) {
                CPropertyInfo p2 = collisionTable.get(n);

                if (p2.getName(true).equals(n.toString()) || p2.getName(false).equals(n.toString())) {
                    errorReceiver.error(p1.locator, Messages.DUPLICATE_ELEMENT.format(n));
                    errorReceiver.error(p2.locator, Messages.ERR_RELEVANT_LOCATION.format());
                }
            }

            for( int j=i+1; j<props.size(); j++ ) {
                if(checkPropertyCollision(p1,props.get(j)))
                    continue OUTER;
            }
            for( CClassInfo c=ci.getBaseClass(); c!=null; c=c.getBaseClass() ) {
                for( CPropertyInfo p2 : c.getProperties() )
                    if(checkPropertyCollision(p1,p2))
                        continue OUTER;
            }
        }
    }

    private boolean checkPropertyCollision(CPropertyInfo p1, CPropertyInfo p2) {
        if(!p1.getName(true).equals(p2.getName(true)))
            return false;
        errorReceiver.error(p1.locator,Messages.DUPLICATE_PROPERTY.format(p1.getName(true)));
        errorReceiver.error(p2.locator,Messages.ERR_RELEVANT_LOCATION.format());
        return true;
    }
}

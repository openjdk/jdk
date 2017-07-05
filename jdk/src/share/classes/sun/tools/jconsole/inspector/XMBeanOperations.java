/*
 * Copyright 2004-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.tools.jconsole.inspector;

import javax.management.*;
import java.util.ArrayList;

import sun.tools.jconsole.MBeansTab;

@SuppressWarnings("serial")
public class XMBeanOperations extends XOperations {

    public XMBeanOperations(MBeansTab mbeansTab) {
        super(mbeansTab);
    }

    protected MBeanOperationInfo[] updateOperations(MBeanOperationInfo[] operations) {
        //remove get,set and is
        ArrayList<MBeanOperationInfo> mbeanOperations =
        new ArrayList<MBeanOperationInfo>(operations.length);
        for(MBeanOperationInfo operation : operations) {
            if (!( (operation.getSignature().length == 0 &&
                    operation.getName().startsWith("get") &&
                    !operation.getReturnType().equals("void"))  ||
                   (operation.getSignature().length == 1 &&
                    operation.getName().startsWith("set") &&
                    operation.getReturnType().equals("void")) ||
                   (operation.getName().startsWith("is") &&
                    operation.getReturnType().equals("boolean"))
                   ) ) {
                mbeanOperations.add(operation);
            }
        }
        return mbeanOperations.toArray(new MBeanOperationInfo[0]);
    }

}

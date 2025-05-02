/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
   @test
   @bug 4248070
   @summary cellEditor bound in JTable.
*/

import javax.swing.JTable;
import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;

public class bug4248070 {

  public static void main(String[] argv) {

    BeanInfo bi = null;

    try {
        bi = Introspector.getBeanInfo(JTable.class);
    } catch (IntrospectionException e) {
    }

    PropertyDescriptor[] pd = bi.getPropertyDescriptors();
    int i;
    for (i=0; i<pd.length; i++) {
        if (pd[i].getName().equals("cellEditor")) {
            break;
        }
    }
    if (!pd[i].isBound()) {
       throw new RuntimeException("cellEditor property of JTable isn't flagged as bound in bean info...");
    }
  }

}

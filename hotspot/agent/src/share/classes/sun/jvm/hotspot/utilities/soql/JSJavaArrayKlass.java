/*
 * Copyright (c) 2003, 2007, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package sun.jvm.hotspot.utilities.soql;

import java.util.*;
import sun.jvm.hotspot.oops.*;

/**
   This is JavaScript wrapper for Java ArrayKlass.
*/

public abstract class JSJavaArrayKlass extends JSJavaKlass {
   public JSJavaArrayKlass(ArrayKlass kls, JSJavaFactory fac) {
      super(kls, fac);
   }

   public final ArrayKlass getArrayKlass() {
      return (ArrayKlass) getKlass();
   }

   public Object getMetaClassFieldValue(String name) {
      if (name.equals("dimension")) {
         return new Long(getArrayKlass().getDimension());
      } else {
         return super.getMetaClassFieldValue(name);
      }
   }

   public boolean hasMetaClassField(String name) {
      if (name.equals("dimension")) {
         return true;
      } else {
         return super.hasMetaClassField(name);
      }
   }

   public boolean isArray() {
      return true;
   }

   public String[] getMetaClassFieldNames() {
       String[] superFields = super.getMetaClassFieldNames();
       String[] res = new String[superFields.length + 1];
       System.arraycopy(superFields, 0, res, 0, superFields.length);
       res[superFields.length] = "dimension";
       return res;
   }

   public abstract Object getFieldValue(int index, Array array);
}

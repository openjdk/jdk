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
import sun.jvm.hotspot.utilities.*;

/**
   This is JavaScript wrapper for TypeArrayKlass.
*/

public class JSJavaTypeArrayKlass extends JSJavaArrayKlass {
   public JSJavaTypeArrayKlass(TypeArrayKlass kls, JSJavaFactory fac) {
      super(kls, fac);
   }

   public final TypeArrayKlass getTypeArrayKlass() {
       return (TypeArrayKlass) getArrayKlass();
   }

   public String getName() {
      int type = (int) getTypeArrayKlass().getElementType();
      switch (type) {
         case TypeArrayKlass.T_BOOLEAN:
            return "boolean[]";
         case TypeArrayKlass.T_CHAR:
            return "char[]";
         case TypeArrayKlass.T_FLOAT:
            return "float[]";
         case TypeArrayKlass.T_DOUBLE:
            return "double[]";
         case TypeArrayKlass.T_BYTE:
            return "byte[]";
         case TypeArrayKlass.T_SHORT:
            return "short[]";
         case TypeArrayKlass.T_INT:
            return "int[]";
         case TypeArrayKlass.T_LONG:
            return "long[]";
         default:
            if (Assert.ASSERTS_ENABLED) {
               Assert.that(false, "Unknown primitive array type");
            }
            return null;
      }
   }

   public Object getFieldValue(int index, Array array) {
      TypeArray typeArr = (TypeArray) array;
      int type = (int) getTypeArrayKlass().getElementType();
      switch (type) {
         case TypeArrayKlass.T_BOOLEAN:
            return Boolean.valueOf(typeArr.getBooleanAt(index));
         case TypeArrayKlass.T_CHAR:
            return new String(new char[] { typeArr.getCharAt(index) });
         case TypeArrayKlass.T_FLOAT:
            return new Float(typeArr.getFloatAt(index));
         case TypeArrayKlass.T_DOUBLE:
            return new Double(typeArr.getDoubleAt(index));
         case TypeArrayKlass.T_BYTE:
            return new Byte(typeArr.getByteAt(index));
         case TypeArrayKlass.T_SHORT:
            return new Short(typeArr.getShortAt(index));
         case TypeArrayKlass.T_INT:
            return new Integer(typeArr.getIntAt(index));
         case TypeArrayKlass.T_LONG:
            return new Long(typeArr.getLongAt(index));
         default:
            if (Assert.ASSERTS_ENABLED) {
               Assert.that(false, "Unknown primitive array type");
            }
            return null;
      }
   }
}

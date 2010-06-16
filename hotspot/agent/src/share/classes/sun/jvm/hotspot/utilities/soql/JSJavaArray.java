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

import sun.jvm.hotspot.oops.*;

/**
   This is JavaScript wrapper for Java Array.
*/

public abstract class JSJavaArray extends JSJavaObject {
   public JSJavaArray(Array array, JSJavaFactory fac) {
      super(array, fac);
      type = (JSJavaArrayKlass) fac.newJSJavaKlass(array.getKlass());
   }

   public final Array getArray() {
       return (Array) getOop();
   }

   public final JSJavaClass getJSJavaClass() {
       return type.getJSJavaClass();
   }

   public Object get(String name) {
      if (name.equals("length")) {
         return new Integer((int)getArray().getLength());
      } else {
         return super.get(name);
      }
   }

   public Object get(int index) {
      return (isInRange(index)) ? type.getFieldValue(index, getArray())
          : super.get(index);
   }

   public Object[] getIds() {
      Object[] superFields = super.getIds();
      final int len = (int) getArray().getLength();
      Object[] res = new Object[superFields.length + len];
      for (int i = 0; i < len; i++) {
          res[i] = new Integer(i);
      }
      System.arraycopy(superFields, 0, res, len, superFields.length);
      return  res;
   }

   public boolean has(String name) {
      if (name.equals("length")) {
         return true;
      } else {
         return super.has(name);
      }
   }

   public boolean has(int index) {
      if (isInRange(index)) {
         return true;
      } else {
         return super.has(index);
      }
   }

   public void put(String name, Object value) {
      if (! name.equals("length")) {
         super.put(name, value);
      }
   }

   public void put(int index, Object value) {
      if (! isInRange(index)) {
         super.put(index, value);
      }
   }

   //-- Internals only below this point
   private boolean isInRange(int index) {
      return index >= 0 && index < getArray().getLength();
   }

   private JSJavaArrayKlass type;
}

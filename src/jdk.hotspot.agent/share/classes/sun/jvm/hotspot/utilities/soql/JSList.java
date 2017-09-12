/*
 * Copyright (c) 2004, 2007, Oracle and/or its affiliates. All rights reserved.
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
   This is JavaScript wrapper for Java List.
*/

public class JSList extends DefaultScriptObject {
   public JSList(List list, JSJavaFactory fac) {
      this.list = list;
      this.factory = fac;
   }

   public Object get(String name) {
      if (name.equals("length")) {
         return new Integer(list.size());
      } else {
         return super.get(name);
      }
   }

   public Object get(int index) {
      if (isInRange(index)) {
          Object item = list.get(index);
          return wrapObject(item);
      } else {
          return super.get(index);
      }
   }

   public Object[] getIds() {
      Object[] superIds = super.getIds();
      final int size = list.size();
      Object[] res = new Object[superIds.length + size];
      for (int i = 0; i < size; i++) {
          res[i] = new Integer(i);
      }
      System.arraycopy(superIds, 0, res, size, superIds.length);
      return res;
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

   public String toString() {
       StringBuffer buf = new StringBuffer();
       buf.append('[');
       for (Iterator itr = list.iterator(); itr.hasNext();) {
           buf.append(wrapObject(itr.next()));
           if (itr.hasNext()) {
               buf.append(", ");
           }
       }
       buf.append(']');
       return buf.toString();
   }

   //-- Internals only below this point
   private boolean isInRange(int index) {
      return index >= 0 && index < list.size();
   }

   private Object wrapObject(Object obj) {
      return factory.newJSJavaWrapper(obj);
   }

   private final List list;
   private final JSJavaFactory factory;
}

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

import sun.jvm.hotspot.oops.Oop;

/** This is JavaScript wrapper for a Java Object in debuggee.*/

public abstract class JSJavaObject extends DefaultScriptObject {
   public JSJavaObject(Oop oop, JSJavaFactory factory) {
       this.oop = oop;
       this.factory = factory;
   }

   public final Oop getOop() {
       return oop;
   }

   public boolean equals(Object o) {
      if (o == null || !(o instanceof JSJavaObject)) {
         return false;
      }

      JSJavaObject other = (JSJavaObject) o;
      return oop.equals(other.oop);
   }

   public int hashCode() {
      return oop.hashCode();
   }

   public String toString() {
      return "Object " + oop.getHandle().toString();
   }

   private final Oop oop;
   protected final JSJavaFactory factory;
}

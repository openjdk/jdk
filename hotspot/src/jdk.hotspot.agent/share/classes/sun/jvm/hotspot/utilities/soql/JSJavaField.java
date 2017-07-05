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
import sun.jvm.hotspot.runtime.*;

public class JSJavaField extends DefaultScriptObject {
   private static final int FIELD_NAME               = 0;
   private static final int FIELD_SIGNATURE          = 1;
   private static final int FIELD_HOLDER             = 2;
   private static final int FIELD_IS_PRIVATE         = 3;
   private static final int FIELD_IS_PUBLIC          = 4;
   private static final int FIELD_IS_PROTECTED       = 5;
   private static final int FIELD_IS_PACKAGE_PRIVATE = 6;
   private static final int FIELD_IS_STATIC          = 7;
   private static final int FIELD_IS_FINAL           = 8;
   private static final int FIELD_IS_VOLATILE        = 9;
   private static final int FIELD_IS_TRANSIENT       = 10;
   private static final int FIELD_IS_SYNTHETIC       = 11;
   private static final int FIELD_UNDEFINED          = -1;

   public JSJavaField(Field f, JSJavaFactory fac) {
      this.field = f;
      this.factory = fac;
   }

   public Object get(String name) {
      int fieldID = getFieldID(name);
      switch (fieldID) {
      case FIELD_NAME:
         return field.getID().getName();
      case FIELD_SIGNATURE:
         return field.getSignature().asString();
      case FIELD_HOLDER:
         return getFieldHolder();
      case FIELD_IS_PRIVATE:
         return Boolean.valueOf(field.isPrivate());
      case FIELD_IS_PUBLIC:
         return Boolean.valueOf(field.isPublic());
      case FIELD_IS_PROTECTED:
         return Boolean.valueOf(field.isProtected());
      case FIELD_IS_PACKAGE_PRIVATE:
         return Boolean.valueOf(field.isPackagePrivate());
      case FIELD_IS_STATIC:
         return Boolean.valueOf(field.isStatic());
      case FIELD_IS_FINAL:
         return Boolean.valueOf(field.isFinal());
      case FIELD_IS_VOLATILE:
         return Boolean.valueOf(field.isVolatile());
      case FIELD_IS_TRANSIENT:
         return Boolean.valueOf(field.isTransient());
      case FIELD_IS_SYNTHETIC:
         return Boolean.valueOf(field.isSynthetic());
      case FIELD_UNDEFINED:
      default:
          return super.get(name);
      }
   }

   public Object[] getIds() {
      Object[] fieldNames = fields.keySet().toArray();
      Object[] superFields = super.getIds();
      Object[] res = new Object[fieldNames.length + superFields.length];
      System.arraycopy(fieldNames, 0, res, 0, fieldNames.length);
      System.arraycopy(superFields, 0, res, fieldNames.length, superFields.length);
      return res;
   }

   public boolean has(String name) {
      if (getFieldID(name) != FIELD_UNDEFINED) {
          return true;
      } else {
          return super.has(name);
      }
   }

   public void put(String name, Object value) {
       if (getFieldID(name) == FIELD_UNDEFINED) {
           super.put(name, value);
       }
   }

   public boolean equals(Object o) {
      if (o == null || !(o instanceof JSJavaField)) {
         return false;
      }

      JSJavaField other = (JSJavaField) o;
      return field.equals(other.field);
   }

   public int hashCode() {
      return field.hashCode();
   }

   public String toString() {
      StringBuffer buf = new StringBuffer();
      buf.append("Field ");
      buf.append(field.getFieldHolder().getName().asString().replace('/', '.'));
      buf.append('.');
      buf.append(field.getID().getName());
      return buf.toString();
   }

   //-- Internals only below this point
   private JSJavaObject getFieldHolder() {
      return factory.newJSJavaKlass(field.getFieldHolder()).getJSJavaClass();
   }

   private static Map fields = new HashMap();
   private static void addField(String name, int fieldId) {
      fields.put(name, new Integer(fieldId));
   }

   private static int getFieldID(String name) {
      Integer res = (Integer) fields.get(name);
      return (res != null)? res.intValue() : FIELD_UNDEFINED;
   }

   static {
      addField("name", FIELD_NAME);
      addField("signature", FIELD_SIGNATURE);
      addField("holder", FIELD_HOLDER);
      addField("isPrivate", FIELD_IS_PRIVATE);
      addField("isPublic", FIELD_IS_PUBLIC);
      addField("isProtected", FIELD_IS_PROTECTED);
      addField("isPackagePrivate", FIELD_IS_PACKAGE_PRIVATE);
      addField("isStatic", FIELD_IS_STATIC);
      addField("isFinal", FIELD_IS_FINAL);
      addField("isVolatile", FIELD_IS_VOLATILE);
      addField("isTransient", FIELD_IS_TRANSIENT);
      addField("isSynthetic", FIELD_IS_SYNTHETIC);
   }

   private final Field field;
   private final JSJavaFactory factory;
}

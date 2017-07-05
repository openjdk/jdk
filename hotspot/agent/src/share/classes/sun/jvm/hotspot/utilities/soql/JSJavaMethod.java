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

/**
 * Wraps a methodOop from the debuggee VM.
 */
public class JSJavaMethod extends JSJavaObject {
   private static final int FIELD_NAME               = 0;
   private static final int FIELD_SIGNATURE          = 1;
   private static final int FIELD_HOLDER             = 2;
   private static final int FIELD_IS_PRIVATE         = 3;
   private static final int FIELD_IS_PUBLIC          = 4;
   private static final int FIELD_IS_PROTECTED       = 5;
   private static final int FIELD_IS_PACKAGE_PRIVATE = 6;
   private static final int FIELD_IS_STATIC          = 7;
   private static final int FIELD_IS_FINAL           = 8;
   private static final int FIELD_IS_SYNCHRONIZED    = 9;
   private static final int FIELD_IS_NATIVE          = 10;
   private static final int FIELD_IS_ABSTRACT        = 11;
   private static final int FIELD_IS_STRICT          = 12;
   private static final int FIELD_IS_SYNTHETIC       = 13;
   private static final int FIELD_IS_OBSOLETE        = 14;
   private static final int FIELD_UNDEFINED          = -1;

   public JSJavaMethod(Method m, JSJavaFactory fac) {
      super(m, fac);
   }

   public final Method getMethod() {
      return (Method) getOop();
   }

   public Object get(String name) {
      int fieldID = getFieldID(name);
      Method method = getMethod();
      switch (fieldID) {
      case FIELD_NAME:
         return method.getName().asString();
      case FIELD_SIGNATURE:
         return method.getSignature().asString();
      case FIELD_HOLDER:
         return getMethodHolder();
      case FIELD_IS_PRIVATE:
         return Boolean.valueOf(method.isPrivate());
      case FIELD_IS_PUBLIC:
         return Boolean.valueOf(method.isPublic());
      case FIELD_IS_PROTECTED:
         return Boolean.valueOf(method.isProtected());
      case FIELD_IS_PACKAGE_PRIVATE:
         return Boolean.valueOf(method.isPackagePrivate());
      case FIELD_IS_STATIC:
         return Boolean.valueOf(method.isStatic());
      case FIELD_IS_FINAL:
         return Boolean.valueOf(method.isFinal());
      case FIELD_IS_SYNCHRONIZED:
         return Boolean.valueOf(method.isSynchronized());
      case FIELD_IS_NATIVE:
         return Boolean.valueOf(method.isNative());
      case FIELD_IS_ABSTRACT:
         return Boolean.valueOf(method.isAbstract());
      case FIELD_IS_STRICT:
         return Boolean.valueOf(method.isStrict());
      case FIELD_IS_SYNTHETIC:
         return Boolean.valueOf(method.isSynthetic());
      case FIELD_IS_OBSOLETE:
         return Boolean.valueOf(method.isObsolete());
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
       if (getFieldID(name) != FIELD_UNDEFINED) {
           return;
       } else {
           super.put(name, value);
       }
   }

   public String toString() {
       StringBuffer buf = new StringBuffer();
       buf.append("Method ");
       buf.append(getMethod().externalNameAndSignature());
       return buf.toString();
   }

   //-- Internals only below this point
   private JSJavaObject getMethodHolder() {
       Klass k = getMethod().getMethodHolder();
       return factory.newJSJavaKlass(k).getJSJavaClass();
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
      addField("isSynchronized", FIELD_IS_SYNCHRONIZED);
      addField("isNative", FIELD_IS_NATIVE);
      addField("isAbstract", FIELD_IS_ABSTRACT);
      addField("isStrict", FIELD_IS_STRICT);
      addField("isSynthetic", FIELD_IS_SYNTHETIC);
      addField("isObsolete", FIELD_IS_OBSOLETE);
   }
}

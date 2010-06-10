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
   This is JavaScript wrapper for InstanceKlass.
*/
public class JSJavaInstanceKlass extends JSJavaKlass {
   private static final int FIELD_SOURCE_FILE        = 1;
   private static final int FIELD_INTERFACES         = 2;
   private static final int FIELD_FIELDS             = 3;
   private static final int FIELD_METHODS            = 4;
   private static final int FIELD_IS_PRIVATE         = 5;
   private static final int FIELD_IS_PUBLIC          = 6;
   private static final int FIELD_IS_PROTECTED       = 7;
   private static final int FIELD_IS_PACKAGE_PRIVATE = 8;
   private static final int FIELD_IS_STATIC          = 9;
   private static final int FIELD_IS_FINAL           = 10;
   private static final int FIELD_IS_ABSTRACT        = 11;
   private static final int FIELD_IS_STRICT          = 12;
   private static final int FIELD_IS_SYNTHETIC       = 13;
   private static final int FIELD_IS_INTERFACE       = 14;
   private static final int FIELD_CLASS_LOADER       = 15;
   private static final int FIELD_PROTECTION_DOMAIN  = 16;
   private static final int FIELD_SIGNERS            = 17;
   private static final int FIELD_STATICS            = 18;
   private static final int FIELD_UNDEFINED          = -1;

   public JSJavaInstanceKlass(InstanceKlass kls, JSJavaFactory fac) {
      super(kls, fac);
      this.instanceFields = new HashMap();
      this.staticFields = new HashMap();
   }

   public final InstanceKlass getInstanceKlass() {
      return (InstanceKlass) getKlass();
   }

   public Object getMetaClassFieldValue(String name) {
      int fieldID = getFieldID(name);
      InstanceKlass ik = getInstanceKlass();
      switch (fieldID) {
      case FIELD_SOURCE_FILE: {
         Symbol sourceFile = ik.getSourceFileName();
         return (sourceFile != null)? sourceFile.asString() : "<unknown>";
      }
      case FIELD_INTERFACES:
         return getInterfaces();
      case FIELD_FIELDS:
         return factory.newJSList(ik.getImmediateFields());
      case FIELD_METHODS:
         return factory.newJSList(ik.getImmediateMethods());
      case FIELD_IS_PRIVATE:
         return Boolean.valueOf(getAccessFlags().isPrivate());
      case FIELD_IS_PUBLIC:
         return Boolean.valueOf(getAccessFlags().isPublic());
      case FIELD_IS_PROTECTED:
         return Boolean.valueOf(getAccessFlags().isProtected());
      case FIELD_IS_PACKAGE_PRIVATE: {
         AccessFlags acc = getAccessFlags();
         return Boolean.valueOf(!acc.isPrivate() && !acc.isPublic() && !acc.isProtected());
      }
      case FIELD_IS_STATIC:
         return Boolean.valueOf(getAccessFlags().isStatic());
      case FIELD_IS_FINAL:
         return Boolean.valueOf(getAccessFlags().isFinal());
      case FIELD_IS_ABSTRACT:
         return Boolean.valueOf(getAccessFlags().isAbstract());
      case FIELD_IS_STRICT:
         return Boolean.valueOf(getAccessFlags().isStrict());
      case FIELD_IS_SYNTHETIC:
         return Boolean.valueOf(getAccessFlags().isSynthetic());
      case FIELD_IS_INTERFACE:
         return Boolean.valueOf(ik.isInterface());
      case FIELD_CLASS_LOADER:
         return factory.newJSJavaObject(ik.getClassLoader());
      case FIELD_PROTECTION_DOMAIN:
         return factory.newJSJavaObject(ik.getProtectionDomain());
      case FIELD_SIGNERS:
         return factory.newJSJavaObject(ik.getSigners());
      case FIELD_STATICS:
         return getStatics();
      case FIELD_UNDEFINED:
      default:
         return super.getMetaClassFieldValue(name);
      }
   }

   public boolean hasMetaClassField(String name) {
      if (getFieldID(name) != FIELD_UNDEFINED) {
          return true;
      } else {
          return super.hasMetaClassField(name);
      }
   }

   public String getName() {
      return getInstanceKlass().getName().asString().replace('/', '.');
   }

   public boolean isArray() {
      return false;
   }

   public String[] getMetaClassFieldNames() {
      String[] superFields = super.getMetaClassFieldNames();
      Set k = fields.keySet();
      String[] res = new String[k.size() + superFields.length];
      System.arraycopy(superFields, 0, res, 0, superFields.length);
      int i = superFields.length;
      for (Iterator itr = k.iterator(); itr.hasNext();) {
          res[i] = (String) itr.next();
          i++;
      }
      return res;
   }

   public Object getInstanceFieldValue(String name, Instance instance) throws NoSuchFieldException {
      Field fld = findInstanceField(name);
      if (fld != null) {
         return getFieldValue(fld, name, instance);
      } else {
         throw new NoSuchFieldException(name + " is not field of "
                + getInstanceKlass().getName().asString().replace('/', '.'));
      }
   }

   public Object getStaticFieldValue(String name) throws NoSuchFieldException {
      Field fld = findStaticField(name);
      if (fld != null) {
         return getFieldValue(fld, name, getInstanceKlass());
      } else {
         throw new NoSuchFieldException(name + " is not field of "
                + getInstanceKlass().getName().asString().replace('/', '.'));
      }
   }

   public String[] getInstanceFieldNames() {
      if (instanceFieldNames == null) {
         InstanceKlass current = getInstanceKlass();
         while (current != null) {
            List tmp = current.getImmediateFields();
            for (Iterator itr = tmp.iterator(); itr.hasNext();) {
               Field fld = (Field) itr.next();
               if (!fld.isStatic()) {
                  String name = fld.getID().getName();
                  if (instanceFields.get(name) == null) {
                     instanceFields.put(name, fld);
                  }
               }
            }
            current = (InstanceKlass) current.getSuper();
         }

         Set s = instanceFields.keySet();
         instanceFieldNames = new String[s.size()];
         int i = 0;
         for (Iterator itr = s.iterator(); itr.hasNext(); i++) {
            instanceFieldNames[i] = (String) itr.next();
         }
      }
      return instanceFieldNames;
   }

   public boolean hasInstanceField(String name) {
      Field fld = findInstanceField(name);
      return (fld != null)? true: false;
   }

   public String[] getStaticFieldNames() {
      if (staticFieldNames == null) {
         InstanceKlass current = getInstanceKlass();
         List tmp = current.getImmediateFields();
         for (Iterator itr = tmp.iterator(); itr.hasNext();) {
            Field fld = (Field) itr.next();
            if (fld.isStatic()) {
               staticFields.put(fld.getID().getName(), fld);
            }
         }

         Set s = staticFields.keySet();
         staticFieldNames = new String[s.size()];
         int i = 0;
         for (Iterator itr = s.iterator(); itr.hasNext(); i++) {
            staticFieldNames[i] = (String) itr.next();
         }
      }
      return staticFieldNames;
   }

   public boolean hasStaticField(String name) {
      Field fld = findStaticField(name);
      return (fld != null)? true: false;
   }

   //-- Intenals only below this point
   private static Map fields = new HashMap();
   private static void addField(String name, int fieldId) {
      fields.put(name, new Integer(fieldId));
   }

   private static int getFieldID(String name) {
      Integer res = (Integer) fields.get(name);
      return (res != null)? res.intValue() : FIELD_UNDEFINED;
   }

   static {
      addField("sourceFile", FIELD_SOURCE_FILE);
      addField("interfaces", FIELD_INTERFACES);
      addField("fields", FIELD_FIELDS);
      addField("methods", FIELD_METHODS);
      addField("isPrivate", FIELD_IS_PRIVATE);
      addField("isPublic", FIELD_IS_PUBLIC);
      addField("isProtected", FIELD_IS_PROTECTED);
      addField("isPackagePrivate", FIELD_IS_PACKAGE_PRIVATE);
      addField("isStatic", FIELD_IS_STATIC);
      addField("isFinal", FIELD_IS_FINAL);
      addField("isAbstract", FIELD_IS_ABSTRACT);
      addField("isStrict", FIELD_IS_STRICT);
      addField("isSynthetic", FIELD_IS_SYNTHETIC);
      addField("isInterface", FIELD_IS_INTERFACE);
      addField("classLoader", FIELD_CLASS_LOADER);
      addField("protectionDomain", FIELD_PROTECTION_DOMAIN);
      addField("signers", FIELD_SIGNERS);
      addField("statics", FIELD_STATICS);
   }

   private AccessFlags getAccessFlags() {
      if (accFlags == null) {
         accFlags = new AccessFlags(getInstanceKlass().computeModifierFlags());
      }
      return accFlags;
   }

   private Object getFieldValue(Field fld, String name, Oop oop) {
       FieldType fd = fld.getFieldType();
       if (fd.isObject() || fd.isArray()) {
         return factory.newJSJavaObject(((OopField)fld).getValue(oop));
       } else if (fd.isByte()) {
          return new Byte(((ByteField)fld).getValue(oop));
       } else if (fd.isChar()) {
          return new String(new char[] { ((CharField)fld).getValue(oop) });
       } else if (fd.isDouble()) {
          return new Double(((DoubleField)fld).getValue(oop));
       } else if (fd.isFloat()) {
          return new Float(((FloatField)fld).getValue(oop));
       } else if (fd.isInt()) {
          return new Integer(((IntField)fld).getValue(oop));
       } else if (fd.isLong()) {
          return new Long(((LongField)fld).getValue(oop));
       } else if (fd.isShort()) {
          return new Short(((ShortField)fld).getValue(oop));
       } else if (fd.isBoolean()) {
          return Boolean.valueOf(((BooleanField)fld).getValue(oop));
       } else {
          if (Assert.ASSERTS_ENABLED) {
             Assert.that(false, "invalid field type for " + name);
          }
          return null;
       }
   }

   private Field findInstanceField(String name) {
      Field fld = (Field) instanceFields.get(name);
      if (fld != null) {
         return fld;
      } else {
         InstanceKlass current = getInstanceKlass();
         while (current != null) {
            List tmp = current.getImmediateFields();
            for (Iterator itr = tmp.iterator(); itr.hasNext();) {
               fld = (Field) itr.next();
               if (fld.getID().getName().equals(name) && !fld.isStatic()) {
                   instanceFields.put(name, fld);
                   return fld;
               }
            }
            // lookup in super class.
            current = (InstanceKlass) current.getSuper();
         }
      }
      // no match
      return null;
   }

   private Field findStaticField(String name) {
      Field fld = (Field) staticFields.get(name);
      if (fld != null) {
         return fld;
      } else {
         // static fields are searched only in current.
         // Direct/indirect super classes and interfaces
         // are not included in search.
         InstanceKlass current = getInstanceKlass();
         List tmp = current.getImmediateFields();
         for (Iterator itr = tmp.iterator(); itr.hasNext();) {
            fld = (Field) itr.next();
            if (fld.getID().getName().equals(name) && fld.isStatic()) {
               staticFields.put(name, fld);
               return fld;
            }
         }
         // no match
         return null;
      }
   }

   private JSList getInterfaces() {
      InstanceKlass ik = getInstanceKlass();
      List intfs = ik.getDirectImplementedInterfaces();
      List res = new ArrayList(0);
      for (Iterator itr = intfs.iterator(); itr.hasNext();) {
          Klass k = (Klass) itr.next();
          res.add(k.getJavaMirror());
      }
      return factory.newJSList(res);
   }

   private JSMap getStatics() {
      String[] names = getStaticFieldNames();
      Map map = new HashMap();
      for (int i=0; i < names.length; i++) {
         try {
            map.put(names[i], getStaticFieldValue(names[i]));
         } catch (NoSuchFieldException exp) {}
      }
      return factory.newJSMap(map);
  }

   private Map           instanceFields;
   private Map           staticFields;
   private String[]      instanceFieldNames;
   private String[]      staticFieldNames;
   private AccessFlags   accFlags;
}

/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package java.lang.invoke;
import java.lang.invoke.MethodHandleNatives.Constants;

/**
 * Cracking (reflecting) method handles back into their constituent symbolic parts.
 *
 */
final class MethodHandleInfo {
   public static final int
       REF_getField                = Constants.REF_getField,
       REF_getStatic               = Constants.REF_getStatic,
       REF_putField                = Constants.REF_putField,
       REF_putStatic               = Constants.REF_putStatic,
       REF_invokeVirtual           = Constants.REF_invokeVirtual,
       REF_invokeStatic            = Constants.REF_invokeStatic,
       REF_invokeSpecial           = Constants.REF_invokeSpecial,
       REF_newInvokeSpecial        = Constants.REF_newInvokeSpecial,
       REF_invokeInterface         = Constants.REF_invokeInterface;

   private final Class<?> declaringClass;
   private final String name;
   private final MethodType methodType;
   private final int referenceKind;

   public MethodHandleInfo(MethodHandle mh) {
       MemberName mn = mh.internalMemberName();
       if (mn == null)  throw new IllegalArgumentException("not a direct method handle");
       this.declaringClass = mn.getDeclaringClass();
       this.name = mn.getName();
       this.methodType = mn.getMethodOrFieldType();
       byte refKind = mn.getReferenceKind();
       if (refKind == REF_invokeSpecial && !mh.isInvokeSpecial())
           // Devirtualized method invocation is usually formally virtual.
           refKind = REF_invokeVirtual;
       this.referenceKind = refKind;
   }

   public Class<?> getDeclaringClass() {
       return declaringClass;
   }

   public String getName() {
       return name;
   }

   public MethodType getMethodType() {
       return methodType;
   }

   public int getModifiers() {
       return -1; //TODO
   }

   public int getReferenceKind() {
       return referenceKind;
   }

   static String getReferenceKindString(int referenceKind) {
        switch (referenceKind) {
            case REF_getField: return "getfield";
            case REF_getStatic: return "getstatic";
            case REF_putField: return "putfield";
            case REF_putStatic: return "putstatic";
            case REF_invokeVirtual: return "invokevirtual";
            case REF_invokeStatic: return "invokestatic";
            case REF_invokeSpecial: return "invokespecial";
            case REF_newInvokeSpecial: return "newinvokespecial";
            case REF_invokeInterface: return "invokeinterface";
            default: return "UNKNOWN_REFENCE_KIND" + "[" + referenceKind + "]";
        }
    }

    @Override
    public String toString() {
        return String.format("%s %s.%s:%s", getReferenceKindString(referenceKind),
                             declaringClass.getName(), name, methodType);
    }
}

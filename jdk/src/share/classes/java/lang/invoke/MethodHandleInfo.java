/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

//Not yet public: public
class MethodHandleInfo {
   public static final int
       REF_NONE                    = Constants.REF_NONE,
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

   public MethodHandleInfo(MethodHandle mh) throws ReflectiveOperationException {
       MemberName mn = mh.internalMemberName();
       this.declaringClass = mn.getDeclaringClass();
       this.name = mn.getName();
       this.methodType = mn.getMethodType();
       this.referenceKind = mn.getReferenceKind();
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

   public int getReferenceKind() {
       return referenceKind;
   }
}

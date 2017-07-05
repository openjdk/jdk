/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import sun.jvm.hotspot.oops.Metadata;

/** This is JavaScript wrapper for a metadata object in debuggee.*/

public abstract class JSMetadata extends DefaultScriptObject {
   public JSMetadata(Metadata metadata, JSJavaFactory factory) {
       this.metadata = metadata;
       this.factory = factory;
   }

   public final Metadata getMetadata() {
       return metadata;
   }

   public boolean equals(Object o) {
      if (o == null || !(o instanceof JSMetadata)) {
         return false;
      }

      JSMetadata other = (JSMetadata) o;
      return metadata.equals(other.metadata);
   }

   public int hashCode() {
      return metadata.hashCode();
   }

   public String toString() {
     return "Metadata " + metadata.getAddress().toString();
   }

   private final Metadata metadata;
   protected final JSJavaFactory factory;
}

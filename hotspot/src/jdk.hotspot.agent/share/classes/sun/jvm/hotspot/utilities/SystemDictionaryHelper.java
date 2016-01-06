/*
 * Copyright (c) 2002, 2005, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.utilities;

import java.util.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.memory.*;
import sun.jvm.hotspot.runtime.*;

public class SystemDictionaryHelper {
   static {
      VM.registerVMInitializedObserver(new Observer() {
         public void update(Observable o, Object data) {
            initialize();
         }
      });
   }

   private static synchronized void initialize() {
      klasses = null;
   }

   // Instance klass array sorted by name.
   private static InstanceKlass[] klasses;

   // side-effect!. caches the instance klass array.
   public static synchronized InstanceKlass[] getAllInstanceKlasses() {
      if (klasses != null) {
         return klasses;
      }

      final Vector tmp = new Vector();
      SystemDictionary dict = VM.getVM().getSystemDictionary();
      dict.classesDo(new SystemDictionary.ClassVisitor() {
                        public void visit(Klass k) {
                           if (k instanceof InstanceKlass) {
                              InstanceKlass ik = (InstanceKlass) k;
                              tmp.add(ik);
                           }
                        }
                     });

      Object[] tmpArray = tmp.toArray();
      klasses = new InstanceKlass[tmpArray.length];
      System.arraycopy(tmpArray, 0, klasses, 0, tmpArray.length);
      Arrays.sort(klasses, new Comparator() {
                          public int compare(Object o1, Object o2) {
                             InstanceKlass k1 = (InstanceKlass) o1;
                             InstanceKlass k2 = (InstanceKlass) o2;
                             Symbol s1 = k1.getName();
                             Symbol s2 = k2.getName();
                             return s1.asString().compareTo(s2.asString());
                          }
                      });
      return klasses;
   }

   // returns array of instance klasses whose name contains given namePart
   public static InstanceKlass[] findInstanceKlasses(String namePart) {
      namePart = namePart.replace('.', '/');
      InstanceKlass[] tmpKlasses = getAllInstanceKlasses();

      Vector tmp = new Vector();
      for (int i = 0; i < tmpKlasses.length; i++) {
         String name = tmpKlasses[i].getName().asString();
         if (name.indexOf(namePart) != -1) {
            tmp.add(tmpKlasses[i]);
         }
      }

      Object[] tmpArray = tmp.toArray();
      InstanceKlass[] searchResult = new InstanceKlass[tmpArray.length];
      System.arraycopy(tmpArray, 0, searchResult, 0, tmpArray.length);
      return searchResult;
   }

   // find first class whose name matches exactly the given argument.
   public static InstanceKlass findInstanceKlass(String className) {
      // convert to internal name
      className = className.replace('.', '/');
      SystemDictionary sysDict = VM.getVM().getSystemDictionary();

      // check whether we have a bootstrap class of given name
      Klass klass = sysDict.find(className, null, null);
      if (klass != null) {
         return (InstanceKlass) klass;
      }

      // check whether we have a system class of given name
      klass = sysDict.find(className, sysDict.javaSystemLoader(), null);
      if (klass != null) {
         return (InstanceKlass) klass;
      }

      // didn't find bootstrap or system class of given name.
      // search through the entire dictionary..
      InstanceKlass[] tmpKlasses = getAllInstanceKlasses();
      // instance klass array is sorted by name. do binary search
      int low = 0;
      int high = tmpKlasses.length-1;

      int mid = -1;
      while (low <= high) {
         mid = (low + high) >> 1;
         InstanceKlass midVal = tmpKlasses[mid];
         int cmp = midVal.getName().asString().compareTo(className);

         if (cmp < 0) {
             low = mid + 1;
         } else if (cmp > 0) {
             high = mid - 1;
         } else { // match found
             return tmpKlasses[mid];
         }
      }
      // no match ..
      return null;
   }
}

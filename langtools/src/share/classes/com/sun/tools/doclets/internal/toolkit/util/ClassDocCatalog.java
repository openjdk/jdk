/*
 * Copyright (c) 2001, 2008, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.doclets.internal.toolkit.util;

import com.sun.javadoc.*;
import java.util.*;

/**
 * This class acts as an artificial PackageDoc for classes specified
 * on the command line when running Javadoc.  For example, if you
 * specify several classes from package java.lang, this class will catalog
 * those classes so that we can retrieve all of the classes from a particular
 * package later.
 *
 * This code is not part of an API.
 * It is implementation that is subject to change.
 * Do not use it as an API
 *
 * @author Jamie Ho
 * @since 1.4
 */

 public class ClassDocCatalog {

     /**
      * Stores the set of packages that the classes specified on the command line
      * belong to.  Note that the default package is "".
      */
     private Set<String> packageSet;


     /**
      * Stores all classes for each package
      */
     private Map<String,Set<ClassDoc>> allClasses;

     /**
      * Stores ordinary classes (excluding Exceptions and Errors) for each
      * package
      */
     private Map<String,Set<ClassDoc>> ordinaryClasses;

     /**
      * Stores exceptions for each package
      */
     private Map<String,Set<ClassDoc>> exceptions;

    /**
     * Stores enums for each package.
     */
    private Map<String,Set<ClassDoc>> enums;

    /**
     * Stores annotation types for each package.
     */
    private Map<String,Set<ClassDoc>> annotationTypes;

     /**
      * Stores errors for each package
      */
     private Map<String,Set<ClassDoc>> errors;

     /**
      * Stores interfaces for each package
      */
     private Map<String,Set<ClassDoc>> interfaces;

     /**
      * Construct a new ClassDocCatalog.
      *
      * @param classdocs the array of ClassDocs to catalog
      */
     public ClassDocCatalog (ClassDoc[] classdocs) {
         init();
         for (int i = 0; i < classdocs.length; i++) {
             addClassDoc(classdocs[i]);
         }
     }

     /**
      * Construct a new ClassDocCatalog.
      *
      */
     public ClassDocCatalog () {
         init();
     }

     private void init() {
         allClasses = new HashMap<String,Set<ClassDoc>>();
         ordinaryClasses = new HashMap<String,Set<ClassDoc>>();
         exceptions = new HashMap<String,Set<ClassDoc>>();
         enums = new HashMap<String,Set<ClassDoc>>();
         annotationTypes = new HashMap<String,Set<ClassDoc>>();
         errors = new HashMap<String,Set<ClassDoc>>();
         interfaces = new HashMap<String,Set<ClassDoc>>();
         packageSet = new HashSet<String>();
     }

     /**
      * Add the given class to the catalog.
      * @param classdoc the ClassDoc to add to the catelog.
      */
      public void addClassDoc(ClassDoc classdoc) {
        if (classdoc == null) {
            return;
        }
        addClass(classdoc, allClasses);
        if (classdoc.isOrdinaryClass()) {
            addClass(classdoc, ordinaryClasses);
        } else if (classdoc.isException()) {
            addClass(classdoc, exceptions);
        } else if (classdoc.isEnum()) {
            addClass(classdoc, enums);
        } else if (classdoc.isAnnotationType()) {
            addClass(classdoc, annotationTypes);
        } else if (classdoc.isError()) {
            addClass(classdoc, errors);
        } else if (classdoc.isInterface()) {
            addClass(classdoc, interfaces);
        }
      }

      /**
       * Add the given class to the given map.
       * @param classdoc the ClassDoc to add to the catelog.
       * @param map the Map to add the ClassDoc to.
       */
      private void addClass(ClassDoc classdoc, Map<String,Set<ClassDoc>> map) {

          PackageDoc pkg = classdoc.containingPackage();
          if (pkg.isIncluded()) {
              //No need to catalog this class since it's package is
              //included on the command line
              return;
          }
          String key = Util.getPackageName(pkg);
          Set<ClassDoc> s = map.get(key);
          if (s == null) {
              packageSet.add(key);
              s = new HashSet<ClassDoc>();
          }
          s.add(classdoc);
          map.put(key, s);

      }

      private ClassDoc[] getArray(Map<String,Set<ClassDoc>> m, String key) {
          Set<ClassDoc> s = m.get(key);
          if (s == null) {
              return new ClassDoc[] {};
          } else {
              return s.toArray(new ClassDoc[] {});
          }
      }

      /**
       * Return all of the classes specified on the command-line that
       * belong to the given package.
       * @param packageDoc the package to return the classes for.
       */
      public ClassDoc[] allClasses(PackageDoc pkgDoc) {
          return pkgDoc.isIncluded() ?
                pkgDoc.allClasses() :
                getArray(allClasses, Util.getPackageName(pkgDoc));
      }

      /**
       * Return all of the classes specified on the command-line that
       * belong to the given package.
       * @param packageName the name of the package specified on the
       * command-line.
       */
      public ClassDoc[] allClasses(String packageName) {
          return getArray(allClasses, packageName);
      }

     /**
      * Return the array of package names that this catalog stores
      * ClassDocs for.
      */
     public String[] packageNames() {
         return packageSet.toArray(new String[] {});
     }

     /**
      * Return true if the given package is known to this catalog.
      * @param packageName the name to check.
      * @return true if this catalog has any information about
      * classes in the given package.
      */
     public boolean isKnownPackage(String packageName) {
         return packageSet.contains(packageName);
     }


      /**
       * Return all of the errors specified on the command-line
       * that belong to the given package.
       * @param packageName the name of the package specified on the
       * command-line.
       */
      public ClassDoc[] errors(String packageName) {
          return getArray(errors, packageName);
      }

      /**
       * Return all of the exceptions specified on the command-line
       * that belong to the given package.
       * @param packageName the name of the package specified on the
       * command-line.
       */
      public ClassDoc[] exceptions(String packageName) {
          return getArray(exceptions, packageName);
      }

      /**
       * Return all of the enums specified on the command-line
       * that belong to the given package.
       * @param packageName the name of the package specified on the
       * command-line.
       */
      public ClassDoc[] enums(String packageName) {
          return getArray(enums, packageName);
      }

      /**
       * Return all of the annotation types specified on the command-line
       * that belong to the given package.
       * @param packageName the name of the package specified on the
       * command-line.
       */
      public ClassDoc[] annotationTypes(String packageName) {
          return getArray(annotationTypes, packageName);
      }

      /**
       * Return all of the interfaces specified on the command-line
       * that belong to the given package.
       * @param packageName the name of the package specified on the
       * command-line.
       */
      public ClassDoc[] interfaces(String packageName) {
          return getArray(interfaces, packageName);
      }

      /**
       * Return all of the ordinary classes specified on the command-line
       * that belong to the given package.
       * @param packageName the name of the package specified on the
       * command-line.
       */
      public ClassDoc[] ordinaryClasses(String packageName) {
          return getArray(ordinaryClasses, packageName);
      }
}

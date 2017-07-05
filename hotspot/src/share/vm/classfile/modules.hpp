/*
* Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_CLASSFILE_MODULES_HPP
#define SHARE_VM_CLASSFILE_MODULES_HPP

#include "memory/allocation.hpp"
#include "runtime/handles.hpp"

class Symbol;

class Modules : AllStatic {

public:
  // define_module defines a module containing the specified packages. It binds the
  // module to its class loader by creating the ModuleEntry record in the
  // ClassLoader's ModuleEntry table, and creates PackageEntry records in the class
  // loader's PackageEntry table.  As in JVM_DefineClass the jstring format for all
  // package names must use "/" and not "."
  //
  //  IllegalArgumentExceptions are thrown for the following :
  // * Module's Class loader is not a subclass of java.lang.ClassLoader
  // * Module's Class loader already has a module with that name
  // * Module's Class loader has already defined types for any of the module's packages
  // * Module_name is syntactically bad
  // * Packages contains an illegal package name
  // * Packages contains a duplicate package name
  // * A package already exists in another module for this class loader
  // * Module is an unnamed module
  //  NullPointerExceptions are thrown if module is null.
  static void define_module(jobject module, jstring version,
                            jstring location, jobjectArray packages, TRAPS);

  // Provides the java.lang.reflect.Module for the unnamed module defined
  // to the boot loader.
  //
  //  IllegalArgumentExceptions are thrown for the following :
  //  * Module has a name
  //  * Module is not a subclass of java.lang.reflect.Module
  //  * Module's class loader is not the boot loader
  //  NullPointerExceptions are thrown if module is null.
  static void set_bootloader_unnamed_module(jobject module, TRAPS);

  // This either does a qualified export of package in module from_module to module
  // to_module or, if to_module is null, does an unqualified export of package.
  // The format for the package name must use "/' not ".".
  //
  // Error conditions causing IlegalArgumentException to be throw :
  // * Module from_module does not exist
  // * Module to_module is not null and does not exist
  // * Package is not syntactically correct
  // * Package is not defined for from_module's class loader
  // * Package is not in module from_module.
  static void add_module_exports(jobject from_module, jstring package, jobject to_module, TRAPS);

  // This does a qualified export of package in module from_module to module
  // to_module.  The format for the package name must use "/' not ".".
  //
  // Error conditions causing IlegalArgumentException to be throw :
  // * Module from_module does not exist
  // * Module to_module does not exist
  // * Package is not syntactically correct
  // * Package is not defined for from_module's class loader
  // * Package is not in module from_module.
  static void add_module_exports_qualified(jobject from_module, jstring package, jobject to_module, TRAPS);

  // add_reads_module adds module to_module to the list of modules that from_module
  // can read.  If from_module is the same as to_module then this is a no-op.
  // If to_module is null then from_module is marked as a loose module (meaning that
  // from_module can read all current and future unnamed  modules).
  // An IllegalArgumentException is thrown if from_module is null or either (non-null)
  // module does not exist.
  static void add_reads_module(jobject from_module, jobject to_module, TRAPS);

  // can_read_module returns TRUE if module asking_module can read module target_module,
  // or if they are the same module, or if the asking_module is loose and target_module
  // is null.
  //
  // Throws IllegalArgumentException if:
  // * either asking_module or target_module is not a java.lang.reflect.Module
  static jboolean can_read_module(jobject asking_module, jobject target_module, TRAPS);

  // If package is valid then this returns TRUE if module from_module exports
  // package to module to_module, if from_module and to_module are the same
  // module, or if package is exported without qualification.
  //
  // IllegalArgumentException is throw if:
  // * Either to_module or from_module does not exist
  // * package is syntactically incorrect
  // * package is not in from_module
  static jboolean is_exported_to_module(jobject from_module, jstring package, jobject to_module, TRAPS);

  // Return the java.lang.reflect.Module object for this class object.
  static jobject get_module(jclass clazz, TRAPS);

  // Return the java.lang.reflect.Module object for this class loader and package.
  // Returns NULL if the class loader has not loaded any classes in the package.
  // The package should contain /'s, not .'s, as in java/lang, not java.lang.
  // NullPointerException is thrown if package is null.
  // IllegalArgumentException is thrown if loader is neither null nor a subtype of
  // java/lang/ClassLoader.
  static jobject get_module_by_package_name(jobject loader, jstring package, TRAPS);
  static jobject get_named_module(Handle h_loader, const char* package, TRAPS);

  // If package is defined by loader, return the
  // java.lang.reflect.Module object for the module in which the package is defined.
  // Returns NULL if package is invalid or not defined by loader.
  static jobject get_module(Symbol* package_name, Handle h_loader, TRAPS);

  // This adds package to module.
  // It throws IllegalArgumentException if:
  // * Module is bad
  // * Module is unnamed
  // * Package is not syntactically correct
  // * Package is already defined for module's class loader.
  static void add_module_package(jobject module, jstring package, TRAPS);

  // Marks the specified package as exported to all unnamed modules.
  // If either module or package is null then NullPointerException is thrown.
  // If module or package is bad, or module is unnamed, or package is not in
  // module then IllegalArgumentException is thrown.
  static void add_module_exports_to_all_unnamed(jobject module, jstring package, TRAPS);

  // Return TRUE if package_name is syntactically valid, false otherwise.
  static bool verify_package_name(char *package_name);

  // Return TRUE iff package is defined by loader
  static bool is_package_defined(Symbol* package_name, Handle h_loader, TRAPS);
};

#endif // SHARE_VM_CLASSFILE_MODULES_HPP

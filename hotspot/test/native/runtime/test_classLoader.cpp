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
 */

#include "precompiled.hpp"
#include "classfile/classLoader.hpp"
#include "memory/resourceArea.hpp"
#include "unittest.hpp"

// Tests ClassLoader::package_from_name()
TEST_VM(classLoader, null_class_name) {
  ResourceMark rm;
  bool bad_class_name = false;
  const char* retval= ClassLoader::package_from_name(NULL, &bad_class_name);
  ASSERT_TRUE(bad_class_name) << "Function did not set bad_class_name with NULL class name";
  ASSERT_STREQ(retval, NULL) << "Wrong package for NULL class name pointer";
}

TEST_VM(classLoader, empty_class_name) {
  ResourceMark rm;
  const char* retval = ClassLoader::package_from_name("");
  ASSERT_STREQ(retval, NULL) << "Wrong package for empty string";
}

TEST_VM(classLoader, no_slash) {
  ResourceMark rm;
  const char* retval = ClassLoader::package_from_name("L");
  ASSERT_STREQ(retval, NULL) << "Wrong package for class with no slashes";
}

TEST_VM(classLoader, just_slash) {
  ResourceMark rm;
  bool bad_class_name = false;
  const char* retval = ClassLoader::package_from_name("/", &bad_class_name);
  ASSERT_TRUE(bad_class_name) << "Function did not set bad_class_name with package of length 0";
  ASSERT_STREQ(retval, NULL) << "Wrong package for class with just slash";
}

TEST_VM(classLoader, multiple_slashes) {
  ResourceMark rm;
  const char* retval = ClassLoader::package_from_name("///");
  ASSERT_STREQ(retval, "//") << "Wrong package for class with just slashes";
}

TEST_VM(classLoader, standard_case_1) {
  ResourceMark rm;
  bool bad_class_name = true;
  const char* retval = ClassLoader::package_from_name("package/class", &bad_class_name);
  ASSERT_FALSE(bad_class_name) << "Function did not reset bad_class_name";
  ASSERT_STREQ(retval, "package") << "Wrong package for class with one slash";
}

TEST_VM(classLoader, standard_case_2) {
  ResourceMark rm;
  const char* retval = ClassLoader::package_from_name("package/folder/class");
  ASSERT_STREQ(retval, "package/folder") << "Wrong package for class with multiple slashes";
}

TEST_VM(classLoader, class_array) {
  ResourceMark rm;
  bool bad_class_name = false;
  const char* retval = ClassLoader::package_from_name("[package/class", &bad_class_name);
  ASSERT_FALSE(bad_class_name) << "Function set bad_class_name with class array";
  ASSERT_STREQ(retval, "package") << "Wrong package for class with leading bracket";
}

TEST_VM(classLoader, class_object_array) {
  ResourceMark rm;
  bool bad_class_name = false;
  const char* retval = ClassLoader::package_from_name("[Lpackage/class", &bad_class_name);
  ASSERT_TRUE(bad_class_name) << "Function did not set bad_class_name with array of class objects";
  ASSERT_STREQ(retval, NULL) << "Wrong package for class with leading '[L'";
}

/*
 * Copyright (c) 1999, 2002, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Regression test for:
 * Javadoc does not process base class. If user specifies few classes on the
 * command line and few packages, with a situation where one of the specified
 * classes(on the command line) extends a class from one of the packages, then
 * due to some anomaly in ordering in which all the class and package objects
 * get constructed, few classes were getting marked as "not included", even
 * thought they were included in this run and hence documentation for those
 * packages was wrong. The test case for which javadoc was failing is given
 * in bug# 4197513.
 *
 * @bug 4197513
 * @summary Javadoc does not process base class.
 * @build BaseClass.java
 * @run shell BaseClassWrapper.sh
 * @author Atul M Dambalkar
 */

import com.sun.javadoc.*;

public class BaseClass {

  public static boolean start(RootDoc root) throws Exception {
     if (!root.classNamed("baz.Foo").isIncluded()) {
         throw new Exception("Base class is not included: baz.Foo");
     }
     return true;
  }

}

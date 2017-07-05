/*
 * Copyright (c) 1999, Oracle and/or its affiliates. All rights reserved.
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
/*
 * COMPONENT_NAME: idl.parser
 *
 * ORIGINS: 27
 *
 * Licensed Materials - Property of IBM
 * 5639-D57 (C) COPYRIGHT International Business Machines Corp. 1997, 1999
 * RMI-IIOP v1.0
 *
 */

package com.sun.tools.corba.se.idl;

// NOTES:
// -capitalize and parseTypeModifier should probably be in the
//  generators package.
// -D58319<daz> Add version() method.

import java.util.ResourceBundle;
import java.text.MessageFormat;
import java.util.Hashtable;

public class ResourceBundleUtil
{
  // <d58319>
  /**
   * Fetch the version number of this build of the IDL Parser Framework.
   * This method may be called before or after the framework has been
   * initialized. If the framework is inititialized, the version information
   * is extracted from the message properties object; otherwise, it is extracted
   * from the indicated resouce bundle.
   * @return the version number.
   **/
  public static String getVersion ()
  {
    String version = getMessage ("Version.product", getMessage ("Version.number"));
    return version;
  } // getVersion


  //////////////
  // Message-related methods

  public static String getMessage (String key)
  {
    return fBundle.getString(key);
  } // getMessage

  public static String getMessage (String key, String fill)
  {
    Object[] args = { fill };
    return MessageFormat.format(fBundle.getString(key), args);
  } // getMessage

  public static String getMessage (String key, String[] fill)
  {
    return MessageFormat.format(fBundle.getString(key), fill);
  } // getMessage


  /** Register a ResourceBundle.  This file will be searched for
      in the CLASSPATH. */
  public static void registerResourceBundle (ResourceBundle bundle)
  {
    if (bundle != null)
      fBundle = bundle;
  } // registerResourceBundle


  /** Gets the current ResourceBundle.  */
  public static ResourceBundle getResourceBundle ()
  {
    return fBundle;
  } // getResourceBundle

  private static ResourceBundle  fBundle;
  static
  {
    // get the resource bundle for the locale on this machine
    fBundle = ResourceBundle.getBundle("com.sun.tools.corba.se.idl.idl");
  }

} // class ResourceBundleUtil

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
 * COMPONENT_NAME: idl.toJava
 *
 * ORIGINS: 27
 *
 * Licensed Materials - Property of IBM
 * 5639-D57 (C) COPYRIGHT International Business Machines Corp. 1997, 1999
 * RMI-IIOP v1.0
 *
 */

package com.sun.tools.corba.se.idl.toJavaPortable;

// NOTES:

import java.io.PrintWriter;

import com.sun.tools.corba.se.idl.SymtabEntry;

/**
 *
 **/
public interface JavaGenerator
{
  // The helper methods print the specific helper method.
  // The helper read/write methods call the read/write methods.

  int helperType (int index, String indent, TCOffsets tcoffsets, String name, SymtabEntry entry, PrintWriter stream);

  void helperRead (String entryName, SymtabEntry entry, PrintWriter stream);

  void helperWrite (SymtabEntry entry, PrintWriter stream);

  // The read/write methods print the streaming of the type.
  // This printed code is found in the helper method but it is only
  // that code that is concerned with streaming itself.

  int read (int index, String indent, String name, SymtabEntry entry, PrintWriter stream);

  int write (int index, String indent, String name, SymtabEntry entry, PrintWriter stream);

  int type (int index, String indent, TCOffsets tcoffsets, String name, SymtabEntry entry, PrintWriter stream);

} // interface JavaGenerator

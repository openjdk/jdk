/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
package com.sun.xml.internal.rngom.xml.sax;

import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.SAXException;

public class AbstractLexicalHandler implements LexicalHandler {
  public void startDTD(String s, String s1, String s2) throws SAXException {
  }

  public void endDTD() throws SAXException {
  }

  public void startEntity(String s) throws SAXException {
  }

  public void endEntity(String s) throws SAXException {
  }

  public void startCDATA() throws SAXException {
  }

  public void endCDATA() throws SAXException {
  }

  public void comment(char[] chars, int start, int length) throws SAXException {
  }
}

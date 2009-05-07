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

import com.sun.xml.internal.rngom.util.Uri;
import org.xml.sax.Locator;

public class XmlBaseHandler {
  private int depth = 0;
  private Locator loc;
  private Entry stack = null;

  private static class Entry {
    private Entry parent;
    private String attValue;
    private String systemId;
    private int depth;
  }

  public void setLocator(Locator loc) {
    this.loc = loc;
  }

  public void startElement() {
    ++depth;
  }

  public void endElement() {
    if (stack != null && stack.depth == depth)
      stack = stack.parent;
    --depth;
  }

  public void xmlBaseAttribute(String value) {
    Entry entry = new Entry();
    entry.parent = stack;
    stack = entry;
    entry.attValue = Uri.escapeDisallowedChars(value);
    entry.systemId = getSystemId();
    entry.depth = depth;
  }

  private String getSystemId() {
    return loc == null ? null : loc.getSystemId();
  }

  public String getBaseUri() {
    return getBaseUri1(getSystemId(), stack);
  }

  private static String getBaseUri1(String baseUri, Entry stack) {
    if (stack == null
        || (baseUri != null && !baseUri.equals(stack.systemId)))
      return baseUri;
    baseUri = stack.attValue;
    if (Uri.isAbsolute(baseUri))
      return baseUri;
    return Uri.resolve(getBaseUri1(stack.systemId, stack.parent), baseUri);
  }
}

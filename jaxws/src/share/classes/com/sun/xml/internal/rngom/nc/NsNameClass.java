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
package com.sun.xml.internal.rngom.nc;

import javax.xml.namespace.QName;

public final class NsNameClass extends NameClass {

  private final String namespaceUri;

  public NsNameClass(String namespaceUri) {
    this.namespaceUri = namespaceUri;
  }

  public boolean contains(QName name) {
    return this.namespaceUri.equals(name.getNamespaceURI());
  }

  public int containsSpecificity(QName name) {
    return contains(name) ? SPECIFICITY_NS_NAME : SPECIFICITY_NONE;
  }

  public int hashCode() {
    return namespaceUri.hashCode();
  }

  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof NsNameClass))
      return false;
    return namespaceUri.equals(((NsNameClass)obj).namespaceUri);
  }

  public <V> V accept(NameClassVisitor<V> visitor) {
    return visitor.visitNsName(namespaceUri);
  }

  public boolean isOpen() {
    return true;
  }
}

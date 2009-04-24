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

public class NsNameExceptNameClass extends NameClass {

  private final NameClass nameClass;
  private final String namespaceURI;

  public NsNameExceptNameClass(String namespaceURI, NameClass nameClass) {
    this.namespaceURI = namespaceURI;
    this.nameClass = nameClass;
  }

  public boolean contains(QName name) {
    return (this.namespaceURI.equals(name.getNamespaceURI())
            && !nameClass.contains(name));
  }

  public int containsSpecificity(QName name) {
    return contains(name) ? SPECIFICITY_NS_NAME : SPECIFICITY_NONE;
  }

  public boolean equals(Object obj) {
    if (obj == null || !(obj instanceof NsNameExceptNameClass))
      return false;
    NsNameExceptNameClass other = (NsNameExceptNameClass)obj;
    return (namespaceURI.equals(other.namespaceURI)
            && nameClass.equals(other.nameClass));
  }

  public int hashCode() {
    return namespaceURI.hashCode() ^ nameClass.hashCode();
  }

  public <V> V accept(NameClassVisitor<V> visitor) {
    return visitor.visitNsNameExcept(namespaceURI, nameClass);
  }

  public boolean isOpen() {
    return true;
  }
}

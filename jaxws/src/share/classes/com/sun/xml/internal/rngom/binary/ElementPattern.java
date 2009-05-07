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
package com.sun.xml.internal.rngom.binary;

import com.sun.xml.internal.rngom.binary.visitor.PatternFunction;
import com.sun.xml.internal.rngom.binary.visitor.PatternVisitor;
import com.sun.xml.internal.rngom.nc.NameClass;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

public final class ElementPattern extends Pattern {
  private Pattern p;
  private NameClass origNameClass;
  private NameClass nameClass;
  private boolean expanded = false;
  private boolean checkedRestrictions = false;
  private Locator loc;

  ElementPattern(NameClass nameClass, Pattern p, Locator loc) {
    super(false,
          ELEMENT_CONTENT_TYPE,
          combineHashCode(ELEMENT_HASH_CODE,
                          nameClass.hashCode(),
                          p.hashCode()));
    this.nameClass = nameClass;
    this.origNameClass = nameClass;
    this.p = p;
    this.loc = loc;
  }

  void checkRestrictions(int context, DuplicateAttributeDetector dad, Alphabet alpha)
    throws RestrictionViolationException {
    if (alpha != null)
      alpha.addElement(origNameClass);
    if (checkedRestrictions)
      return;
    switch (context) {
    case DATA_EXCEPT_CONTEXT:
      throw new RestrictionViolationException("data_except_contains_element");
    case LIST_CONTEXT:
      throw new RestrictionViolationException("list_contains_element");
    case ATTRIBUTE_CONTEXT:
      throw new RestrictionViolationException("attribute_contains_element");
    }
    checkedRestrictions = true;
    try {
      p.checkRestrictions(ELEMENT_CONTEXT, new DuplicateAttributeDetector(), null);
    }
    catch (RestrictionViolationException e) {
      checkedRestrictions = false;
      e.maybeSetLocator(loc);
      throw e;
    }
  }

  Pattern expand(SchemaPatternBuilder b) {
    if (!expanded) {
      expanded = true;
      p = p.expand(b);
      if (p.isNotAllowed())
        nameClass = NameClass.NULL;
    }
    return this;
  }

  boolean samePattern(Pattern other) {
    if (!(other instanceof ElementPattern))
      return false;
    ElementPattern ep = (ElementPattern)other;
    return nameClass.equals(ep.nameClass) && p == ep.p;
  }

  void checkRecursion(int depth) throws SAXException {
    p.checkRecursion(depth + 1);
  }

  public void accept(PatternVisitor visitor) {
    visitor.visitElement(nameClass, p);
  }

  public Object apply(PatternFunction f) {
    return f.caseElement(this);
  }

  void setContent(Pattern p) {
    this.p = p;
  }

  public Pattern getContent() {
    return p;
  }

  public NameClass getNameClass() {
    return nameClass;
  }

  public Locator getLocator() {
    return loc;
  }
}

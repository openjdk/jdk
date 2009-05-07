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
import com.sun.xml.internal.rngom.nc.SimpleNameClass;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

public final class AttributePattern extends Pattern {
  private NameClass nameClass;
  private Pattern p;
  private Locator loc;

  AttributePattern(NameClass nameClass, Pattern value, Locator loc) {
    super(false,
          EMPTY_CONTENT_TYPE,
          combineHashCode(ATTRIBUTE_HASH_CODE,
                          nameClass.hashCode(),
                          value.hashCode()));
    this.nameClass = nameClass;
    this.p = value;
    this.loc = loc;
  }

  Pattern expand(SchemaPatternBuilder b) {
    Pattern ep = p.expand(b);
    if (ep != p)
      return b.makeAttribute(nameClass, ep, loc);
    else
      return this;
  }

  void checkRestrictions(int context, DuplicateAttributeDetector dad, Alphabet alpha)
    throws RestrictionViolationException {
    switch (context) {
    case START_CONTEXT:
      throw new RestrictionViolationException("start_contains_attribute");
    case ELEMENT_CONTEXT:
      if (nameClass.isOpen())
        throw new RestrictionViolationException("open_name_class_not_repeated");
      break;
    case ELEMENT_REPEAT_GROUP_CONTEXT:
      throw new RestrictionViolationException("one_or_more_contains_group_contains_attribute");
    case ELEMENT_REPEAT_INTERLEAVE_CONTEXT:
      throw new RestrictionViolationException("one_or_more_contains_interleave_contains_attribute");
    case LIST_CONTEXT:
      throw new RestrictionViolationException("list_contains_attribute");
    case ATTRIBUTE_CONTEXT:
      throw new RestrictionViolationException("attribute_contains_attribute");
    case DATA_EXCEPT_CONTEXT:
      throw new RestrictionViolationException("data_except_contains_attribute");
    }
    if (!dad.addAttribute(nameClass)) {
      if (nameClass instanceof SimpleNameClass)
        throw new RestrictionViolationException("duplicate_attribute_detail", ((SimpleNameClass)nameClass).name);
      else
        throw new RestrictionViolationException("duplicate_attribute");
    }
    try {
      p.checkRestrictions(ATTRIBUTE_CONTEXT, null, null);
    }
    catch (RestrictionViolationException e) {
      e.maybeSetLocator(loc);
      throw e;
    }
  }

  boolean samePattern(Pattern other) {
    if (!(other instanceof AttributePattern))
      return false;
    AttributePattern ap = (AttributePattern)other;
    return nameClass.equals(ap.nameClass)&& p == ap.p;
  }

  void checkRecursion(int depth) throws SAXException {
    p.checkRecursion(depth);
  }

  public void accept(PatternVisitor visitor) {
    visitor.visitAttribute(nameClass, p);
  }

  public Object apply(PatternFunction f) {
    return f.caseAttribute(this);
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

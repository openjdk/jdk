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
import org.xml.sax.SAXException;

public class OneOrMorePattern extends Pattern {
  Pattern p;

  OneOrMorePattern(Pattern p) {
    super(p.isNullable(),
          p.getContentType(),
          combineHashCode(ONE_OR_MORE_HASH_CODE, p.hashCode()));
    this.p = p;
  }

  Pattern expand(SchemaPatternBuilder b) {
    Pattern ep = p.expand(b);
    if (ep != p)
      return b.makeOneOrMore(ep);
    else
      return this;
  }

  void checkRecursion(int depth) throws SAXException {
    p.checkRecursion(depth);
  }

  void checkRestrictions(int context, DuplicateAttributeDetector dad, Alphabet alpha)
    throws RestrictionViolationException {
    switch (context) {
    case START_CONTEXT:
      throw new RestrictionViolationException("start_contains_one_or_more");
    case DATA_EXCEPT_CONTEXT:
      throw new RestrictionViolationException("data_except_contains_one_or_more");
    }

    p.checkRestrictions(context == ELEMENT_CONTEXT
                        ? ELEMENT_REPEAT_CONTEXT
                        : context,
                        dad,
                        alpha);
    if (context != LIST_CONTEXT
        && !contentTypeGroupable(p.getContentType(), p.getContentType()))
      throw new RestrictionViolationException("one_or_more_string");
  }

  boolean samePattern(Pattern other) {
    return (other instanceof OneOrMorePattern
            && p == ((OneOrMorePattern)other).p);
  }

  public void accept(PatternVisitor visitor) {
    visitor.visitOneOrMore(p);
  }

  public Object apply(PatternFunction f) {
    return f.caseOneOrMore(this);
  }

  Pattern getOperand() {
    return p;
  }
}

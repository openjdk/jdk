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

import com.sun.xml.internal.rngom.binary.visitor.*;

public class GroupPattern extends BinaryPattern {
  GroupPattern(Pattern p1, Pattern p2) {
    super(p1.isNullable() && p2.isNullable(),
          combineHashCode(GROUP_HASH_CODE, p1.hashCode(), p2.hashCode()),
          p1,
          p2);
  }

  Pattern expand(SchemaPatternBuilder b) {
    Pattern ep1 = p1.expand(b);
    Pattern ep2 = p2.expand(b);
    if (ep1 != p1 || ep2 != p2)
      return b.makeGroup(ep1, ep2);
    else
      return this;
  }

  void checkRestrictions(int context, DuplicateAttributeDetector dad, Alphabet alpha) throws RestrictionViolationException {
    switch (context) {
    case START_CONTEXT:
      throw new RestrictionViolationException("start_contains_group");
    case DATA_EXCEPT_CONTEXT:
      throw new RestrictionViolationException("data_except_contains_group");
    }
    super.checkRestrictions(context == ELEMENT_REPEAT_CONTEXT
                            ? ELEMENT_REPEAT_GROUP_CONTEXT
                            : context,
                            dad,
                            alpha);
    if (context != LIST_CONTEXT
        && !contentTypeGroupable(p1.getContentType(), p2.getContentType()))
      throw new RestrictionViolationException("group_string");
  }

  public void accept(PatternVisitor visitor) {
    visitor.visitGroup(p1, p2);
  }
  public Object apply(PatternFunction f) {
    return f.caseGroup(this);
  }
}

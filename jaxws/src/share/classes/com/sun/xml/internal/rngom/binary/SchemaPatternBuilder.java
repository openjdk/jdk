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

import com.sun.xml.internal.rngom.nc.NameClass;
import org.relaxng.datatype.Datatype;
import org.xml.sax.Locator;

public class SchemaPatternBuilder extends PatternBuilder {
  private boolean idTypes;
  private final Pattern unexpandedNotAllowed =
        new NotAllowedPattern() {
        boolean isNotAllowed() {
            return false;
        }
        Pattern expand(SchemaPatternBuilder b) {
            return b.makeNotAllowed();
        }
    };

  private final TextPattern text = new TextPattern();
  private final PatternInterner schemaInterner = new PatternInterner();

  public SchemaPatternBuilder() { }

  public boolean hasIdTypes() {
    return idTypes;
  }

  Pattern makeElement(NameClass nameClass, Pattern content, Locator loc) {
    Pattern p = new ElementPattern(nameClass, content, loc);
    return schemaInterner.intern(p);
  }

  Pattern makeAttribute(NameClass nameClass, Pattern value, Locator loc) {
    if (value == notAllowed)
      return value;
    Pattern p = new AttributePattern(nameClass, value, loc);
    return schemaInterner.intern(p);
  }

  Pattern makeData(Datatype dt) {
    noteDatatype(dt);
    Pattern p = new DataPattern(dt);
    return schemaInterner.intern(p);
  }

  Pattern makeDataExcept(Datatype dt, Pattern except, Locator loc) {
    noteDatatype(dt);
    Pattern p = new DataExceptPattern(dt, except, loc);
    return schemaInterner.intern(p);
  }

  Pattern makeValue(Datatype dt, Object obj) {
    noteDatatype(dt);
    Pattern p = new ValuePattern(dt, obj);
    return schemaInterner.intern(p);
  }

  Pattern makeText() {
    return text;
  }

  Pattern makeOneOrMore(Pattern p) {
    if (p == text)
      return p;
    return super.makeOneOrMore(p);
  }

  Pattern makeUnexpandedNotAllowed() {
    return unexpandedNotAllowed;
  }

  Pattern makeError() {
    Pattern p = new ErrorPattern();
    return schemaInterner.intern(p);
  }

  Pattern makeChoice(Pattern p1, Pattern p2) {
    if (p1 == notAllowed || p1 == p2)
      return p2;
    if (p2 == notAllowed)
      return p1;
    return super.makeChoice(p1, p2);
  }

  Pattern makeList(Pattern p, Locator loc) {
    if (p == notAllowed)
      return p;
    Pattern p1 = new ListPattern(p, loc);
    return schemaInterner.intern(p1);
  }

  Pattern makeMixed(Pattern p) {
    return makeInterleave(text, p);
  }

  private void noteDatatype(Datatype dt) {
    if (dt.getIdType() != Datatype.ID_TYPE_NULL)
      idTypes = true;
  }
}

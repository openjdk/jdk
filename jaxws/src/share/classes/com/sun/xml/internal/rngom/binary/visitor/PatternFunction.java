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
package com.sun.xml.internal.rngom.binary.visitor;

import com.sun.xml.internal.rngom.binary.AfterPattern;
import com.sun.xml.internal.rngom.binary.AttributePattern;
import com.sun.xml.internal.rngom.binary.ChoicePattern;
import com.sun.xml.internal.rngom.binary.DataExceptPattern;
import com.sun.xml.internal.rngom.binary.DataPattern;
import com.sun.xml.internal.rngom.binary.ElementPattern;
import com.sun.xml.internal.rngom.binary.EmptyPattern;
import com.sun.xml.internal.rngom.binary.ErrorPattern;
import com.sun.xml.internal.rngom.binary.GroupPattern;
import com.sun.xml.internal.rngom.binary.InterleavePattern;
import com.sun.xml.internal.rngom.binary.ListPattern;
import com.sun.xml.internal.rngom.binary.NotAllowedPattern;
import com.sun.xml.internal.rngom.binary.OneOrMorePattern;
import com.sun.xml.internal.rngom.binary.RefPattern;
import com.sun.xml.internal.rngom.binary.TextPattern;
import com.sun.xml.internal.rngom.binary.ValuePattern;

public interface PatternFunction {
    Object caseEmpty(EmptyPattern p);
    Object caseNotAllowed(NotAllowedPattern p);
    Object caseError(ErrorPattern p);
    Object caseGroup(GroupPattern p);
    Object caseInterleave(InterleavePattern p);
    Object caseChoice(ChoicePattern p);
    Object caseOneOrMore(OneOrMorePattern p);
    Object caseElement(ElementPattern p);
    Object caseAttribute(AttributePattern p);
    Object caseData(DataPattern p);
    Object caseDataExcept(DataExceptPattern p);
    Object caseValue(ValuePattern p);
    Object caseText(TextPattern p);
    Object caseList(ListPattern p);
    Object caseRef(RefPattern p);
    Object caseAfter(AfterPattern p);
}

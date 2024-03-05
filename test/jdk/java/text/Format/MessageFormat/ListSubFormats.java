/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8318761
 * @summary Test MessageFormatPattern ability to recognize and produce the
 *          appropriate FormatType and FormatStyle for ListFormat. ListFormat's
 *          STANDARD, OR, and UNIT types are supported as built-in patterns for
 *          MessageFormat. All types use the FULL style.
 * @run junit ListSubFormats
 */

import java.text.ListFormat;
import java.text.MessageFormat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ListSubFormats {

    // Recognize the 'list' FormatType as well as '', 'or', and
    // 'unit' associated FormatStyles
    @Test
    public void applyPatternTest() {
        var mFmt = new MessageFormat("{0,list}{1,list,or}{2,list,unit}");
        var listStandard = ListFormat.getInstance(mFmt.getLocale(),
                ListFormat.Type.STANDARD, ListFormat.Style.FULL);
        var listOr = ListFormat.getInstance(mFmt.getLocale(),
                ListFormat.Type.OR, ListFormat.Style.FULL);
        var listUnit = ListFormat.getInstance(mFmt.getLocale(),
                ListFormat.Type.UNIT, ListFormat.Style.FULL);
        assertEquals(mFmt.getFormatsByArgumentIndex()[0], listStandard);
        assertEquals(mFmt.getFormatsByArgumentIndex()[1], listOr);
        assertEquals(mFmt.getFormatsByArgumentIndex()[2], listUnit);
    }

    // Ensure incorrect FormatElement pattern throws IAE
    // java.text.ListFormat does not support String subformatPatterns
    @Test
    public void badApplyPatternTest() {
        // Wrong FormatStyle
        IllegalArgumentException exc = assertThrows(IllegalArgumentException.class, () ->
                new MessageFormat("{0,list,standard}"));
        assertEquals("Unexpected modifier for List: standard", exc.getMessage());

        // Wrong FormatType
        exc = assertThrows(IllegalArgumentException.class, () ->
                new MessageFormat("{0,listt,or}"));
        assertEquals("unknown format type: listt", exc.getMessage());

    }

    // STANDARD, OR, UNIT ListFormats (with FULL style) should
    // produce correct patterns.
    @Test
    public void toPatternTest() {
        var mFmt = new MessageFormat("{0}{1}{2}");
        mFmt.setFormatByArgumentIndex(0,
                ListFormat.getInstance(mFmt.getLocale(), ListFormat.Type.STANDARD, ListFormat.Style.FULL));
        mFmt.setFormatByArgumentIndex(1,
                ListFormat.getInstance(mFmt.getLocale(), ListFormat.Type.OR, ListFormat.Style.FULL));
        mFmt.setFormatByArgumentIndex(2,
                ListFormat.getInstance(mFmt.getLocale(), ListFormat.Type.UNIT, ListFormat.Style.FULL));
        assertEquals("{0,list}{1,list,or}{2,list,unit}", mFmt.toPattern());
    }

    // A custom ListFormat cannot be recognized, thus does not produce any built-in pattern
    @Test
    public void badToPatternTest() {
        var mFmt = new MessageFormat("{0}");
        mFmt.setFormatByArgumentIndex(0,
                ListFormat.getInstance(mFmt.getLocale(), ListFormat.Type.UNIT, ListFormat.Style.NARROW));
        assertEquals("{0}", mFmt.toPattern());
    }
}

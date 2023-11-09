/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
    @Test
    public void badApplyPatternTest() {
        // Wrong FormatStyle
        assertThrows(IllegalArgumentException.class, () ->
                new MessageFormat("{0,list,standard"));
        // Wrong FormatType
        assertThrows(IllegalArgumentException.class, () ->
                new MessageFormat("{0,listt,or"));
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

    // Test that the listFmt Subformats format properly within the MessageFormat
    @ParameterizedTest
    @MethodSource
    public void formatTest(MessageFormat mFmt, ListFormat lFmt) {
        List<String> listData = List.of("foo", "bar", "baz");
        Object[] data = {listData};
        // Check ListFormat sub-format is formatting properly
        assertEquals(mFmt.format(data), "quux"+lFmt.format(listData)+"quux");
    }

    // MessageFormat with patterns that contain the associated ListFormat
    private static Stream<Arguments> formatTest() {
        Locale loc = Locale.getDefault(Locale.Category.FORMAT);
        return Stream.of(
                Arguments.of(new MessageFormat("quux{0,list}quux"),
                        ListFormat.getInstance(loc, ListFormat.Type.STANDARD, ListFormat.Style.FULL)),
                Arguments.of(new MessageFormat("quux{0,list,or}quux"),
                        ListFormat.getInstance(loc, ListFormat.Type.OR, ListFormat.Style.FULL)),
                Arguments.of(new MessageFormat("quux{0,list,unit}quux"),
                        ListFormat.getInstance(loc, ListFormat.Type.UNIT, ListFormat.Style.FULL))
        );
    }
}

/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * (C) Copyright Taligent, Inc. 1996, 1997 - All Rights Reserved
 * (C) Copyright IBM Corp. 1996 - 1998 - All Rights Reserved
 *
 * The original version of this source code and documentation
 * is copyrighted and owned by Taligent, Inc., a wholly-owned
 * subsidiary of IBM. These materials are provided under terms
 * of a License Agreement between Taligent and Sun. This technology
 * is protected by multiple US and International patents.
 *
 * This notice and attribution to Taligent may not be removed.
 * Taligent is a registered trademark of Taligent, Inc.
 *
 */

/*
 * COPYRIGHT AND PERMISSION NOTICE
 *
 * Copyright (C) 1991-2012 Unicode, Inc. All rights reserved. Distributed under
 * the Terms of Use in http://www.unicode.org/copyright.html.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of the Unicode data files and any associated documentation (the "Data
 * Files") or Unicode software and any associated documentation (the
 * "Software") to deal in the Data Files or Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, and/or sell copies of the Data Files or Software, and
 * to permit persons to whom the Data Files or Software are furnished to do so,
 * provided that (a) the above copyright notice(s) and this permission notice
 * appear with all copies of the Data Files or Software, (b) both the above
 * copyright notice(s) and this permission notice appear in associated
 * documentation, and (c) there is clear notice in each modified Data File or
 * in the Software as well as in the documentation associated with the Data
 * File(s) or Software that the data or software has been modified.
 *
 * THE DATA FILES AND SOFTWARE ARE PROVIDED "AS IS", WITHOUT WARRANTY OF ANY
 * KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT OF
 * THIRD PARTY RIGHTS. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR HOLDERS
 * INCLUDED IN THIS NOTICE BE LIABLE FOR ANY CLAIM, OR ANY SPECIAL INDIRECT OR
 * CONSEQUENTIAL DAMAGES, OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE,
 * DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER
 * TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE
 * OF THE DATA FILES OR SOFTWARE.
 *
 * Except as contained in this notice, the name of a copyright holder shall not
 * be used in advertising or otherwise to promote the sale, use or other
 * dealings in these Data Files or Software without prior written authorization
 * of the copyright holder.
 */

package sun.text.resources.pl;

import java.util.ListResourceBundle;

public class FormatData_pl extends ListResourceBundle {
    /**
     * Overrides ListResourceBundle
     */
    protected final Object[][] getContents() {
        return new Object[][] {
            { "MonthNames",
                new String[] {
                    "stycznia",
                    "lutego",
                    "marca",
                    "kwietnia",
                    "maja",
                    "czerwca",
                    "lipca",
                    "sierpnia",
                    "wrze\u015bnia",
                    "pa\u017adziernika",
                    "listopada",
                    "grudnia",
                    "",
                }
            },
            { "standalone.MonthNames",
                new String[] {
                    "stycze\u0144", // january
                    "luty", // february
                    "marzec", // march
                    "kwiecie\u0144", // april
                    "maj", // may
                    "czerwiec", // june
                    "lipiec", // july
                    "sierpie\u0144", // august
                    "wrzesie\u0144", // september
                    "pa\u017adziernik", // october
                    "listopad", // november
                    "grudzie\u0144", // december
                    "" // month 13 if applicable
                }
            },
            { "MonthAbbreviations",
                new String[] {
                    "sty", // abb january
                    "lut", // abb february
                    "mar", // abb march
                    "kwi", // abb april
                    "maj", // abb may
                    "cze", // abb june
                    "lip", // abb july
                    "sie", // abb august
                    "wrz", // abb september
                    "pa\u017a", // abb october
                    "lis", // abb november
                    "gru", // abb december
                    "" // abb month 13 if applicable
                }
            },
            { "DayNames",
                new String[] {
                    "niedziela", // Sunday
                    "poniedzia\u0142ek", // Monday
                    "wtorek", // Tuesday
                    "\u015broda", // Wednesday
                    "czwartek", // Thursday
                    "pi\u0105tek", // Friday
                    "sobota" // Saturday
                }
            },
            { "DayAbbreviations",
                new String[] {
                    "N", // abb Sunday
                    "Pn", // abb Monday
                    "Wt", // abb Tuesday
                    "\u015ar", // abb Wednesday
                    "Cz", // abb Thursday
                    "Pt", // abb Friday
                    "So" // abb Saturday
                }
            },
            { "DayNarrows",
                new String[] {
                    "N",
                    "P",
                    "W",
                    "\u015a",
                    "C",
                    "P",
                    "S",
                }
            },
            { "Eras",
                new String[] { // era strings
                    "p.n.e.",
                    "n.e."
                }
            },
            { "NumberElements",
                new String[] {
                    ",", // decimal separator
                    "\u00a0", // group (thousands) separator
                    ";", // list separator
                    "%", // percent sign
                    "0", // native 0 digit
                    "#", // pattern digit
                    "-", // minus sign
                    "E", // exponential
                    "\u2030", // per mille
                    "\u221e", // infinity
                    "\ufffd" // NaN
                }
            },
            { "TimePatterns",
                new String[] {
                    "HH:mm:ss z", // full time pattern
                    "HH:mm:ss z", // long time pattern
                    "HH:mm:ss", // medium time pattern
                    "HH:mm", // short time pattern
                }
            },
            { "DatePatterns",
                new String[] {
                    "EEEE, d MMMM yyyy", // full date pattern
                    "d MMMM yyyy", // long date pattern
                    "yyyy-MM-dd", // medium date pattern
                    "yy-MM-dd", // short date pattern
                }
            },
            { "DateTimePatterns",
                new String[] {
                    "{1} {0}" // date-time pattern
                }
            },
            { "DateTimePatternChars", "GyMdkHmsSEDFwWahKzZ" },
            { "cldr.buddhist.DatePatterns",
                new String[] {
                    "EEEE, G y MMMM dd",
                    "G y MMMM d",
                    "d MMM y G",
                    "dd.MM.yyyy G",
                }
            },
            { "cldr.japanese.DatePatterns",
                new String[] {
                    "EEEE, d MMMM, y G",
                    "d MMMM, y G",
                    "d MMM y G",
                    "dd.MM.yyyy G",
                }
            },
            { "cldr.roc.DatePatterns",
                new String[] {
                    "EEEE, d MMMM, y G",
                    "d MMMM, y G",
                    "d MMM y G",
                    "dd.MM.yyyy G",
                }
            },
            { "roc.DatePatterns",
                new String[] {
                    "EEEE, d MMMM, y GGGG",
                    "d MMMM, y GGGG",
                    "d MMM y GGGG",
                    "dd.MM.yyyy GGGG",
                }
            },
            { "cldr.islamic.DatePatterns",
                new String[] {
                    "EEEE, d MMMM, y G",
                    "d MMMM, y G",
                    "d MMM y G",
                    "dd.MM.yyyy G",
                }
            },
            { "islamic.DatePatterns",
                new String[] {
                    "EEEE, d MMMM, y GGGG",
                    "d MMMM, y GGGG",
                    "d MMM y GGGG",
                    "dd.MM.yyyy GGGG",
                }
            },
            { "calendarname.islamic-civil", "kalendarz islamski (metoda obliczeniowa)" },
            { "calendarname.islamicc", "kalendarz islamski (metoda obliczeniowa)" },
            { "calendarname.islamic", "kalendarz islamski (metoda wzrokowa)" },
            { "calendarname.japanese", "kalendarz japo\u0144ski" },
            { "calendarname.gregorian", "kalendarz gregoria\u0144ski" },
            { "calendarname.gregory", "kalendarz gregoria\u0144ski" },
            { "calendarname.roc", "kalendarz Republiki Chi\u0144skiej" },
            { "calendarname.buddhist", "kalendarz buddyjski" },
            { "field.era", "Era" },
            { "field.year", "Rok" },
            { "field.month", "Miesi\u0105c" },
            { "field.week", "Tydzie\u0144" },
            { "field.weekday", "Dzie\u0144 tygodnia" },
            { "field.hour", "Godzina" },
            { "field.minute", "Minuta" },
            { "field.second", "Sekunda" },
            { "field.zone", "Strefa" },
        };
    }
}

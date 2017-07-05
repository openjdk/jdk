/*
 * Copyright (c) 2006, 2013, Oracle and/or its affiliates. All rights reserved.
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

package sun.text.resources.sr;

import java.util.ListResourceBundle;

public class FormatData_sr extends ListResourceBundle {
    @Override
    protected final Object[][] getContents() {
        final String[] rocEras = {
            "\u041f\u0440\u0435 \u0420\u041a",
            "\u0420\u041a",
        };
        return new Object[][] {
            { "MonthNames",
                new String[] {
                    "\u0458\u0430\u043d\u0443\u0430\u0440",
                    "\u0444\u0435\u0431\u0440\u0443\u0430\u0440",
                    "\u043c\u0430\u0440\u0442",
                    "\u0430\u043f\u0440\u0438\u043b",
                    "\u043c\u0430\u0458",
                    "\u0458\u0443\u043d",
                    "\u0458\u0443\u043b",
                    "\u0430\u0432\u0433\u0443\u0441\u0442",
                    "\u0441\u0435\u043f\u0442\u0435\u043c\u0431\u0430\u0440",
                    "\u043e\u043a\u0442\u043e\u0431\u0430\u0440",
                    "\u043d\u043e\u0432\u0435\u043c\u0431\u0430\u0440",
                    "\u0434\u0435\u0446\u0435\u043c\u0431\u0430\u0440",
                    "",
                }
            },
            { "MonthAbbreviations",
                new String[] {
                    "\u0458\u0430\u043d",
                    "\u0444\u0435\u0431",
                    "\u043c\u0430\u0440",
                    "\u0430\u043f\u0440",
                    "\u043c\u0430\u0458",
                    "\u0458\u0443\u043d",
                    "\u0458\u0443\u043b",
                    "\u0430\u0432\u0433",
                    "\u0441\u0435\u043f",
                    "\u043e\u043a\u0442",
                    "\u043d\u043e\u0432",
                    "\u0434\u0435\u0446",
                    "",
                }
            },
            { "DayNames",
                new String[] {
                    "\u043d\u0435\u0434\u0435\u0459\u0430",
                    "\u043f\u043e\u043d\u0435\u0434\u0435\u0459\u0430\u043a",
                    "\u0443\u0442\u043e\u0440\u0430\u043a",
                    "\u0441\u0440\u0435\u0434\u0430",
                    "\u0447\u0435\u0442\u0432\u0440\u0442\u0430\u043a",
                    "\u043f\u0435\u0442\u0430\u043a",
                    "\u0441\u0443\u0431\u043e\u0442\u0430",
                }
            },
            { "DayAbbreviations",
                new String[] {
                    "\u043d\u0435\u0434",
                    "\u043f\u043e\u043d",
                    "\u0443\u0442\u043e",
                    "\u0441\u0440\u0435",
                    "\u0447\u0435\u0442",
                    "\u043f\u0435\u0442",
                    "\u0441\u0443\u0431",
                }
            },
            { "DayNarrows",
                new String[] {
                    "\u043d",
                    "\u043f",
                    "\u0443",
                    "\u0441",
                    "\u0447",
                    "\u043f",
                    "\u0441",
                }
            },
            { "Eras",
                new String[] {
                    "\u043f. \u043d. \u0435.",
                    "\u043d. \u0435",
                }
            },
            { "short.Eras",
                new String[] {
                    "\u043f. \u043d. \u0435.",
                    "\u043d. \u0435.",
                }
            },
            { "narrow.Eras",
                new String[] {
                    "\u043f.\u043d.\u0435.",
                    "\u043d.\u0435.",
                }
            },
            { "NumberPatterns",
                new String[] {
                    "#,##0.###",
                    "\u00a4 #,##0.00",
                    "#,##0%",
                }
            },
            { "NumberElements",
                new String[] {
                    ",",
                    ".",
                    ";",
                    "%",
                    "0",
                    "#",
                    "-",
                    "E",
                    "\u2030",
                    "\u221e",
                    "NaN",
                }
            },
            { "TimePatterns",
                new String[] {
                    "HH.mm.ss z",
                    "HH.mm.ss z",
                    "HH.mm.ss",
                    "HH.mm",
                }
            },
            { "DatePatterns",
                new String[] {
                    "EEEE, dd.MMMM.yyyy.",
                    "dd.MM.yyyy.",
                    "dd.MM.yyyy.",
                    "d.M.yy.",
                }
            },
            { "DateTimePatterns",
                new String[] {
                    "{1} {0}",
                }
            },
            { "DateTimePatternChars", "GanjkHmsSEDFwWxhKzZ" },
            { "cldr.japanese.DatePatterns",
                new String[] {
                    "EEEE, MMMM d, y G",
                    "MMMM d, y G",
                    "MMM d, y G",
                    "M/d/yy G",
                }
            },
            { "roc.Eras", rocEras },
            { "roc.short.Eras", rocEras },
            { "islamic.MonthNames",
                new String[] {
                    "\u041c\u0443\u0440\u0430\u0445\u0430\u043c",
                    "\u0421\u0430\u0444\u0430\u0440",
                    "\u0420\u0430\u0431\u0438\u02bb I",
                    "\u0420\u0430\u0431\u0438\u02bb II",
                    "\u0408\u0443\u043c\u0430\u0434\u0430 I",
                    "\u0408\u0443\u043c\u0430\u0434\u0430 II",
                    "\u0420\u0430\u0452\u0430\u0431",
                    "\u0428\u0430\u02bb\u0431\u0430\u043d",
                    "\u0420\u0430\u043c\u0430\u0434\u0430\u043d",
                    "\u0428\u0430\u0432\u0430\u043b",
                    "\u0414\u0443\u02bb\u043b-\u041a\u0438\u02bb\u0434\u0430",
                    "\u0414\u0443\u02bb\u043b-\u0445\u0438\u0452\u0430",
                    "",
                }
            },
            { "islamic.Eras",
                new String[] {
                    "",
                    "\u0410\u0425",
                }
            },
            { "calendarname.islamic-civil", "\u0418\u0441\u043b\u0430\u043c\u0441\u043a\u0438 \u0446\u0438\u0432\u0438\u043b\u043d\u0438 \u043a\u0430\u043b\u0435\u043d\u0434\u0430\u0440" },
            { "calendarname.islamicc", "\u0418\u0441\u043b\u0430\u043c\u0441\u043a\u0438 \u0446\u0438\u0432\u0438\u043b\u043d\u0438 \u043a\u0430\u043b\u0435\u043d\u0434\u0430\u0440" },
            { "calendarname.islamic", "\u0418\u0441\u043b\u0430\u043c\u0441\u043a\u0438 \u043a\u0430\u043b\u0435\u043d\u0434\u0430\u0440" },
            { "calendarname.japanese", "\u0408\u0430\u043f\u0430\u043d\u0441\u043a\u0438 \u043a\u0430\u043b\u0435\u043d\u0434\u0430\u0440" },
            { "calendarname.gregorian", "\u0413\u0440\u0435\u0433\u043e\u0440\u0438\u0458\u0430\u043d\u0441\u043a\u0438 \u043a\u0430\u043b\u0435\u043d\u0434\u0430\u0440" },
            { "calendarname.gregory", "\u0413\u0440\u0435\u0433\u043e\u0440\u0438\u0458\u0430\u043d\u0441\u043a\u0438 \u043a\u0430\u043b\u0435\u043d\u0434\u0430\u0440" },
            { "calendarname.roc", "\u041a\u0430\u043b\u0435\u043d\u0434\u0430\u0440 \u0420\u0435\u043f\u0443\u0431\u043b\u0438\u043a\u0435 \u041a\u0438\u043d\u0435" },
            { "calendarname.buddhist", "\u0411\u0443\u0434\u0438\u0441\u0442\u0438\u0447\u043a\u0438 \u043a\u0430\u043b\u0435\u043d\u0434\u0430\u0440" },
            { "field.era", "\u0435\u0440\u0430" },
            { "field.year", "\u0433\u043e\u0434\u0438\u043d\u0430" },
            { "field.month", "\u043c\u0435\u0441\u0435\u0446" },
            { "field.week", "\u043d\u0435\u0434\u0435\u0459\u0430" },
            { "field.weekday", "\u0434\u0430\u043d \u0443 \u043d\u0435\u0434\u0435\u0459\u0438" },
            { "field.dayperiod", "\u043f\u0440\u0435 \u043f\u043e\u0434\u043d\u0435/\u043f\u043e\u043f\u043e\u0434\u043d\u0435" },
            { "field.hour", "\u0447\u0430\u0441" },
            { "field.minute", "\u043c\u0438\u043d\u0443\u0442" },
            { "field.second", "\u0441\u0435\u043a\u0443\u043d\u0434" },
            { "field.zone", "\u0437\u043e\u043d\u0430" },
        };
    }
}

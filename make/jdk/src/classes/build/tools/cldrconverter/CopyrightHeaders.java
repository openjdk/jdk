/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.cldrconverter;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

class CopyrightHeaders {
    private static final String ORACLE2012 =
        "/*\n" +
        " * Copyright (c) %d, Oracle and/or its affiliates. All rights reserved.\n" +
        " */\n";

    private static final String ORACLE_AFTER2012 =
        "/*\n" +
        " * Copyright (c) 2012, %d, Oracle and/or its affiliates. All rights reserved.\n" +
        " */\n";

    // Last updated:  - 11/03/2025
    private static final String UNICODE =
        """
        /*
         * UNICODE LICENSE V3
         *
         * COPYRIGHT AND PERMISSION NOTICE
         *
         * Copyright © 1991-2025 Unicode, Inc.
         *
         * NOTICE TO USER: Carefully read the following legal agreement. BY
         * DOWNLOADING, INSTALLING, COPYING OR OTHERWISE USING DATA FILES, AND/OR
         * SOFTWARE, YOU UNEQUIVOCALLY ACCEPT, AND AGREE TO BE BOUND BY, ALL OF THE
         * TERMS AND CONDITIONS OF THIS AGREEMENT. IF YOU DO NOT AGREE, DO NOT
         * DOWNLOAD, INSTALL, COPY, DISTRIBUTE OR USE THE DATA FILES OR SOFTWARE.
         *
         * Permission is hereby granted, free of charge, to any person obtaining a
         * copy of data files and any associated documentation (the "Data Files") or
         * software and any associated documentation (the "Software") to deal in the
         * Data Files or Software without restriction, including without limitation
         * the rights to use, copy, modify, merge, publish, distribute, and/or sell
         * copies of the Data Files or Software, and to permit persons to whom the
         * Data Files or Software are furnished to do so, provided that either (a)
         * this copyright and permission notice appear with all copies of the Data
         * Files or Software, or (b) this copyright and permission notice appear in
         * associated Documentation.
         *
         * THE DATA FILES AND SOFTWARE ARE PROVIDED "AS IS", WITHOUT WARRANTY OF ANY
         * KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
         * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT OF
         * THIRD PARTY RIGHTS.
         *
         * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR HOLDERS INCLUDED IN THIS NOTICE
         * BE LIABLE FOR ANY CLAIM, OR ANY SPECIAL INDIRECT OR CONSEQUENTIAL DAMAGES,
         * OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS,
         * WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION,
         * ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THE DATA
         * FILES OR SOFTWARE.
         *
         * Except as contained in this notice, the name of a copyright holder shall
         * not be used in advertising or otherwise to promote the sale, use or other
         * dealings in these Data Files or Software without prior written
         * authorization of the copyright holder.
         *
         * SPDX-License-Identifier: Unicode-3.0
         */
        """;

    private static String OPENJDK2012 =
        "/*\n" +
        " * Copyright (c) %d, Oracle and/or its affiliates. All rights reserved.\n" +
        " * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.\n" +
        " *\n" +
        " * This code is free software; you can redistribute it and/or modify it\n" +
        " * under the terms of the GNU General Public License version 2 only, as\n" +
        " * published by the Free Software Foundation.  Oracle designates this\n" +
        " * particular file as subject to the \"Classpath\" exception as provided\n" +
        " * by Oracle in the LICENSE file that accompanied this code.\n" +
        " *\n" +
        " * This code is distributed in the hope that it will be useful, but WITHOUT\n" +
        " * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or\n" +
        " * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License\n" +
        " * version 2 for more details (a copy is included in the LICENSE file that\n" +
        " * accompanied this code).\n" +
        " *\n" +
        " * You should have received a copy of the GNU General Public License version\n" +
        " * 2 along with this work; if not, write to the Free Software Foundation,\n" +
        " * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.\n" +
        " *\n" +
        " * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA\n" +
        " * or visit www.oracle.com if you need additional information or have any\n" +
        " * questions.\n" +
        " */\n";

    private static String OPENJDK_AFTER2012 =
        "/*\n" +
        " * Copyright (c) 2012, %d, Oracle and/or its affiliates. All rights reserved.\n" +
        " * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.\n" +
        " *\n" +
        " * This code is free software; you can redistribute it and/or modify it\n" +
        " * under the terms of the GNU General Public License version 2 only, as\n" +
        " * published by the Free Software Foundation.  Oracle designates this\n" +
        " * particular file as subject to the \"Classpath\" exception as provided\n" +
        " * by Oracle in the LICENSE file that accompanied this code.\n" +
        " *\n" +
        " * This code is distributed in the hope that it will be useful, but WITHOUT\n" +
        " * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or\n" +
        " * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License\n" +
        " * version 2 for more details (a copy is included in the LICENSE file that\n" +
        " * accompanied this code).\n" +
        " *\n" +
        " * You should have received a copy of the GNU General Public License version\n" +
        " * 2 along with this work; if not, write to the Free Software Foundation,\n" +
        " * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.\n" +
        " *\n" +
        " * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA\n" +
        " * or visit www.oracle.com if you need additional information or have any\n" +
        " * questions.\n" +
        " */\n";

    static String getOracleCopyright(int year) {
        return String.format(year > 2012 ? ORACLE_AFTER2012 : ORACLE2012, year);
    }

    static String getUnicodeCopyright() {
        return UNICODE;
    }

    static String getOpenJDKCopyright(int year) {
        return String.format(year > 2012 ? OPENJDK_AFTER2012 : OPENJDK2012, year);
    }

    // no instantiation
    private CopyrightHeaders() {
    }
}


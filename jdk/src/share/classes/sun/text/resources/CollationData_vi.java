/*
 * Portions Copyright 2005 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 */

/*
 *******************************************************************************
 * (C) Copyright IBM Corp. 1996-2003 - All Rights Reserved                     *
 *                                                                             *
 * The original version of this source code and documentation is copyrighted   *
 * and owned by IBM, These materials are provided under terms of a License     *
 * Agreement between IBM and Sun. This technology is protected by multiple     *
 * US and International patents. This notice and attribution to IBM may not    *
 * to removed.                                                                 *
 *******************************************************************************
 *
 * This locale data is based on the ICU's Vietnamese locale data (rev. 1.38)
 * found at:
 *
 * http://oss.software.ibm.com/cvs/icu/icu/source/data/locales/vi.txt?rev=1.38
 */

package sun.text.resources;

import java.util.ListResourceBundle;

public class CollationData_vi extends ListResourceBundle {

    protected final Object[][] getContents() {
        return new Object[][] {
            { "Rule",
                "&\u0300;\u0309;\u0303;\u0301;\u0323"
                + "&D<\u0111,\u0110"
                + "&Z<\u0306<\u0302<\u031b"
            },
        };
    }
}

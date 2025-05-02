/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4185816
 * @library /java/text/testlib
 * @build Bug4185816Test HexDumpReader
 * @run junit Bug4185816Test
 * @summary test that MessageFormat invariants are preserved across serialization.
 */

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file and, per its terms, should not be removed:
 *
 * (C) Copyright IBM Corp. 1996 - 1999 - All Rights Reserved
 *
 * Portions copyright (c) 2007 Sun Microsystems, Inc.
 * All Rights Reserved.
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
 * Permission to use, copy, modify, and distribute this software
 * and its documentation for NON-COMMERCIAL purposes and without
 * fee is hereby granted provided that this copyright notice
 * appears in all copies. Please refer to the file "copyright.html"
 * for further important copyright and licensing information.
 *
 * SUN MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SUN SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

import java.util.*;
import java.io.*;
import java.text.ChoiceFormat;
import java.text.MessageFormat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 *  A Locale can never contain language codes of he, yi or id.
 */
public class Bug4185816Test {
    private static final String FILE_NAME = "Bug4185816.ser";
    private static final String CORRUPT_FILE_NAME = "Bug4185816Corrupt.ser";

    @Test
    public void testIt() throws Exception {
        Exception e = checkStreaming(FILE_NAME);
        if (e != null) {
            fail("MessageFormat did not stream in valid stream: "+e);
            e.printStackTrace();
        }
        e = checkStreaming(CORRUPT_FILE_NAME);
        if (!(e instanceof InvalidObjectException)) {
            fail("MessageFormat did NOT detect corrupt stream: "+e);
            e.printStackTrace();
        }
    }

    public Exception checkStreaming(final String fileName) {
        try {
            final InputStream is = HexDumpReader.getStreamFromHexDump(fileName + ".txt");
            final ObjectInputStream in = new ObjectInputStream(is);
            final MessageFormat form = (MessageFormat)in.readObject();
            final Object[] testArgs = {12373L, "MyDisk"};
            final String result = form.format(testArgs);
            in.close();
        } catch (Exception e) {
            return e;
        }
        return null;
    }
}

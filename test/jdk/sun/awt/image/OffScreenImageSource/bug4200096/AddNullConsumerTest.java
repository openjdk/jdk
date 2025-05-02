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

import java.awt.image.BufferedImage;

/**
 * @test
 * @bug 4200096
 * @summary OffScreenImageSource.addConsumer(null) shouldn't throw (or print) a NullPointerException
 * @author Jeremy Wood
 */

/**
 * This makes sure if OffScreenImageSource#addConsumer(null) is called: we
 * treat that as a no-op and return immediately.
 * <p>
 * This test exists primarily to make sure the resolution to 4200096 does not
 * significantly change legacy behavior. Whether or not a NPE is printed to
 * System.err is not a hotly contested question, but at one point one of the
 * proposed (and rejected) resolutions to 4200096 had the potential to
 * throw a NPE when addConsumer(null) was called. That would be a
 * significant change that we want to avoid.
 * </p>
 */
 public class AddNullConsumerTest {
    public static void main(String[] args) throws Exception {
        try (AutoCloseable setup = bug4200096.setupTest(false)) {
            BufferedImage bufferedImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            bufferedImage.getSource().addConsumer(null);
        }
    }
}
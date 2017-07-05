/*
 * Copyright (c) 1998, 2006, Oracle and/or its affiliates. All rights reserved.
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

package sun.java2d.cmm;

import java.io.InputStream;
import java.io.IOException;


/**
 * A class to pass information about a profile to be loaded from
 * a file to the static getInstance(InputStream) method of
 * ICC_Profile.  Loading of the profile data and initialization
 * of the CMM is to be deferred as long as possible.
 */
public class ProfileDeferralInfo extends InputStream {

    public int colorSpaceType, numComponents, profileClass;
    public String filename;

    public ProfileDeferralInfo(String fn, int type, int ncomp, int pclass) {

        super();
        filename = fn;
        colorSpaceType = type;
        numComponents = ncomp;
        profileClass = pclass;
    }


    /**
     * Implements the abstract read() method of InputStream.
     */
    public int read() throws IOException {

        return 0;
    }

}

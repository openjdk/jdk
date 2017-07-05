/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/**
 * @test
 * @bug 6476665 6523403 6733501
 * @summary Verifies reading and writing profiles and tags of the standard color
 * spaces
 * @run main ReadWriteProfileTest
 */
import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.util.*;
import java.nio.*;
import java.util.Hashtable;

public class ReadWriteProfileTest implements Runnable {
    /* Location of the tag sig counter in 4-byte words */
    final static int TAG_COUNT_OFFSET = 32;

    /* Location of the tag sig table in 4-byte words */
    final static int TAG_ELEM_OFFSET = 33;

    static byte[][] profiles;
    static int [][] tagSigs;
    static Hashtable<Integer,byte[]> [] tags;

    static int [] cspaces = {ColorSpace.CS_sRGB, ColorSpace.CS_PYCC,
                             ColorSpace.CS_LINEAR_RGB, ColorSpace.CS_CIEXYZ,
                             ColorSpace.CS_GRAY};

    static String [] csNames = {"sRGB", "PYCC", "LINEAR_RGB", "CIEXYZ", "GRAY"};

    static void getProfileTags(byte [] data, Hashtable tags) {
        ByteBuffer byteBuf = ByteBuffer.wrap(data);
        IntBuffer intBuf = byteBuf.asIntBuffer();
        int tagCount = intBuf.get(TAG_COUNT_OFFSET);
        intBuf.position(TAG_ELEM_OFFSET);
        for (int i = 0; i < tagCount; i++) {
            int tagSig = intBuf.get();
            int tagDataOff = intBuf.get();
            int tagSize = intBuf.get();

            byte [] tagData = new byte[tagSize];
            byteBuf.position(tagDataOff);
            byteBuf.get(tagData);
            tags.put(tagSig, tagData);
        }
    }

    static {
        profiles = new byte[cspaces.length][];
        tags = new Hashtable[cspaces.length];

        for (int i = 0; i < cspaces.length; i++) {
            ICC_Profile pf = ICC_Profile.getInstance(cspaces[i]);
            profiles[i] = pf.getData();
            tags[i] = new Hashtable();
            getProfileTags(profiles[i], tags[i]);
        }
    }

    public void run() {
        for (int i = 0; i < cspaces.length; i++) {
            ICC_Profile pf = ICC_Profile.getInstance(cspaces[i]);
            byte [] data = pf.getData();
            pf = ICC_Profile.getInstance(data);
            if (!Arrays.equals(data, profiles[i])) {
                System.err.println("Incorrect result of getData() " + "with " +
                                   csNames[i] + " profile");
                throw new RuntimeException("Incorrect result of getData()");
            }

            for (int tagSig : tags[i].keySet()) {
                byte [] tagData = pf.getData(tagSig);
                byte [] empty = new byte[tagData.length];
                pf.setData(tagSig, empty);
                pf.setData(tagSig, tagData);

                byte [] tagData1 = pf.getData(tagSig);

                if (!Arrays.equals(tagData1, tags[i].get(tagSig)))
                {
                    System.err.println("Incorrect result of getData(int) with" +
                                       " tag " +
                                       Integer.toHexString(tagSig) +
                                       " of " + csNames[i] + " profile");

                    throw new RuntimeException("Incorrect result of " +
                                               "getData(int)");
                }
            }
        }
    }

    public static void main(String [] args) {
        ReadWriteProfileTest test = new ReadWriteProfileTest();
        test.run();
    }
}

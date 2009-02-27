/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
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

/* @test
   @summary Test ModelByteBuffer writeTo method */

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.sound.sampled.*;

import com.sun.media.sound.*;

public class WriteTo {

    static float[] testarray;
    static byte[] test_byte_array;
    static File test_file;
    static AudioFormat format = new AudioFormat(44100, 16, 1, true, false);

    static void setUp() throws Exception {
        testarray = new float[1024];
        for (int i = 0; i < 1024; i++) {
            double ii = i / 1024.0;
            ii = ii * ii;
            testarray[i] = (float)Math.sin(10*ii*2*Math.PI);
            testarray[i] += (float)Math.sin(1.731 + 2*ii*2*Math.PI);
            testarray[i] += (float)Math.sin(0.231 + 6.3*ii*2*Math.PI);
            testarray[i] *= 0.3;
        }
        test_byte_array = new byte[testarray.length*2];
        AudioFloatConverter.getConverter(format).toByteArray(testarray, test_byte_array);
        test_file = File.createTempFile("test", ".raw");
        FileOutputStream fos = new FileOutputStream(test_file);
        fos.write(test_byte_array);
    }

    static void tearDown() throws Exception {
        if(!test_file.delete())
            test_file.deleteOnExit();
    }

    public static void main(String[] args) throws Exception {
        try
        {
            setUp();

            for (int i = 0; i < 2; i++) {
                ModelByteBuffer buff;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                if(i == 0)
                    buff = new ModelByteBuffer(test_file);
                else
                    buff = new ModelByteBuffer(test_byte_array);
                buff.writeTo(baos);
                byte[] b = baos.toByteArray();
                for (int j = 0; j < b.length; j++)
                    if(b[i] != test_byte_array[i])
                        throw new RuntimeException("baos.toByteArray() incorrect!");
            }
        }
        finally
        {
            tearDown();
        }
    }

}

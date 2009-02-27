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
   @summary Test RiffReader getFilePointer method */

import java.io.File;
import java.io.FileInputStream;

import javax.sound.sampled.*;

import com.sun.media.sound.*;

public class GetFilePointer {

    private static void assertEquals(Object a, Object b) throws Exception
    {
        if(!a.equals(b))
            throw new RuntimeException("assertEquals fails!");
    }

    public static void main(String[] args) throws Exception {
        RIFFWriter writer = null;
        RIFFReader reader = null;
        File tempfile = File.createTempFile("test",".riff");
        try
        {
            writer = new RIFFWriter(tempfile, "TEST");
            RIFFWriter chunk = writer.writeChunk("TSCH");
            chunk.writeByte(10);
            writer.close();
            writer = null;
            FileInputStream fis = new FileInputStream(tempfile);
            reader = new RIFFReader(fis);
            RIFFReader readchunk = reader.nextChunk();
            long p = readchunk.getFilePointer();
            readchunk.readByte();
            assertEquals(p+1,readchunk.getFilePointer());
            fis.close();
            reader = null;


        }
        finally
        {
            if(writer != null)
                writer.close();
            if(reader != null)
                reader.close();

            if(tempfile.exists())
                if(!tempfile.delete())
                    tempfile.deleteOnExit();
        }
    }
}

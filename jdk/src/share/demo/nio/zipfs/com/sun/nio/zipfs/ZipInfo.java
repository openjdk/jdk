/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.sun.nio.zipfs;

import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import com.sun.nio.zipfs.ZipFileSystem.Entry;
import static com.sun.nio.zipfs.ZipConstants.*;
import static com.sun.nio.zipfs.ZipUtils.*;

/**
 * Print the loc and cen tables of the ZIP file
 *
 * @author  Xueming Shen
 */

public class ZipInfo {

    public static void main(String[] args) throws Throwable {
        if (args.length < 2) {
            print("Usage: java ZipInfo [cen|loc] zfname");
        } else {
            Map<String, ?> env = Collections.emptyMap();
            ZipFileSystem zfs = (ZipFileSystem)(new ZipFileSystemProvider()
                                    .newFileSystem(Paths.get(args[1]), env));

            long pos = 0;

            if ("loc".equals(args[0])) {
                print("[Local File Header]%n");
                byte[] buf = new byte[1024];
                for (int i = 0; i < zfs.getEntryNames().length; i++) {
                    Entry loc = Entry.readLOC(zfs, pos, buf);
                    print("--------loc[%x]--------%n", pos);
                    printLOC(loc);
                    pos = loc.endPos;
                }
            } if ("cen".equals(args[0])) {
                int i = 0;
                Iterator<ZipFileSystem.IndexNode> itr = zfs.inodes.values().iterator();
                print("[Central Directory Header]%n");
                while (itr.hasNext()) {
                    Entry cen = Entry.readCEN(zfs.cen, itr.next().pos);
                    print("--------cen[%d]--------%n", i);
                    printCEN(cen);
                    i++;
                }
            }
            zfs.close();
        }
    }

    static void print(String fmt, Object... objs) {
        System.out.printf(fmt, objs);
    }

    static void printLOC(Entry loc) {
        print("  [%x, %x]%n", loc.startPos, loc.endPos);
        print("  Signature   :     %8x%n", LOCSIG);
        print("  Version     :         %4x    [%d.%d]%n",
                  loc.version, loc. version/10, loc. version%10);
        print("  Flag        :         %4x%n", loc.flag);
        print("  Method      :         %4x%n", loc. method);
        print("  LastMTime   :     %8x    [%tc]%n",
                  loc.mtime, dosToJavaTime(loc.mtime));
        print("  CRC         :     %8x%n", loc.crc);
        print("  CSize       :     %8x%n", loc.csize);
        print("  Size        :     %8x%n", loc.size);
        print("  NameLength  :         %4x    [%s]%n",
                  loc.nlen, new String(loc.name));
        print("  ExtraLength :         %4x%n", loc.elen);
        if (loc.hasZip64)
            print(" *ZIP64*%n");
    }

    static void printCEN(Entry cen) {
        print("  Signature   :     %08x%n", CENSIG);
        print("  VerMadeby   :         %4x    [%d.%d]%n",
                  cen.versionMade, cen.versionMade/10, cen.versionMade%10);
        print("  VerExtract  :         %4x    [%d.%d]%n",
                  cen.version, cen.version/10, cen.version%10);
        print("  Flag        :         %4x%n", cen.flag);
        print("  Method      :         %4x%n", cen.method);
        print("  LastMTime   :     %8x    [%tc]%n",
                  cen.mtime, dosToJavaTime(cen.mtime));
        print("  CRC         :     %8x%n", cen.crc);
        print("  CSize       :     %8x%n", cen.csize);
        print("  Size        :     %8x%n", cen.size);
        print("  NameLen     :         %4x    [%s]%n",
                  cen.nlen, new String(cen.name));
        print("  ExtraLen    :         %4x%n", cen.elen);
        print("  CommentLen  :         %4x%n", cen.clen);
        print("  DiskStart   :         %4x%n", cen.disk);
        print("  Attrs       :         %4x%n", cen.attrs);
        print("  AttrsEx     :     %8x%n", cen.attrsEx);
        print("  LocOff      :     %8x%n", cen.locoff);
        if (cen.hasZip64)
            print(" *ZIP64*%n");
    }
}

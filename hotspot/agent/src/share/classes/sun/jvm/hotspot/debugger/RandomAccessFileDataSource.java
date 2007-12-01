/*
 * Copyright 2000-2001 Sun Microsystems, Inc.  All Rights Reserved.
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
 *
 */

package sun.jvm.hotspot.debugger;

import java.io.*;

/* This class is used by the Windows COFF and Posix ELF implementations. */
public class RandomAccessFileDataSource implements DataSource {
  public RandomAccessFileDataSource(RandomAccessFile file) {
    this.file = file;
  }

  public byte  readByte()       throws IOException { return file.readByte();       }
  public short readShort()      throws IOException { return file.readShort();      }
  public int   readInt()        throws IOException { return file.readInt();        }
  public long  readLong()       throws IOException { return file.readLong();       }
  public int   read(byte[] b)   throws IOException { return file.read(b);          }
  public void  seek(long pos)   throws IOException { file.seek(pos);               }
  public long  getFilePointer() throws IOException { return file.getFilePointer(); }
  public void  close()          throws IOException { file.close();                 }

  private RandomAccessFile file;
}

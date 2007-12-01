/*
 * Copyright 2002 Sun Microsystems, Inc.  All Rights Reserved.
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


package sun.tools.javap;

import java.util.*;
import java.io.*;

/**
 * Stores exception table data in code attribute.
 *
 * @author  Sucheta Dambalkar (Adopted code from jdis)
 */
class TrapData {
    short start_pc, end_pc, handler_pc, catch_cpx;
  int num;


    /**
     * Read and store exception table data in code attribute.
     */
    public TrapData(DataInputStream in, int num) throws IOException {
        this.num=num;
        start_pc = in.readShort();
        end_pc=in.readShort();
        handler_pc=in.readShort();
        catch_cpx=in.readShort();
    }

    /**
     * returns recommended identifier
     */
    public String ident() {
        return "t"+num;
    }

}

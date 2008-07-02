/*
 * Copyright 1999-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 *
 */

package bench.serial;

import bench.Benchmark;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;

/**
 * Benchmark for testing speed of ObjectOutputStream/ObjectInputStream
 * construction.
 */
public class Cons implements Benchmark {

    /**
     * Dummy object to write to newly constructed serialization stream.
     */
    static class Dummy implements Serializable {
    }

    /**
     * Repeatedly construct ObjectOutputStream and ObjectInputStream objects.
     * Arguments: <# repetitions>
     */
    public long run(String[] args) throws Exception {
        int reps = Integer.parseInt(args[0]);
        Dummy dummy = new Dummy();
        StreamBuffer sbuf = new StreamBuffer();

        doReps(sbuf, dummy, 1);         // warmup

        long start = System.currentTimeMillis();
        doReps(sbuf, dummy, reps);
        return System.currentTimeMillis() - start;
    }

    /**
     * Run benchmark for given number of cycles.
     */
    void doReps(StreamBuffer sbuf, Dummy dummy, int reps) throws Exception {
        OutputStream out = sbuf.getOutputStream();
        InputStream in = sbuf.getInputStream();
        for (int i = 0; i < reps; i++) {
            sbuf.reset();
            ObjectOutputStream oout = new ObjectOutputStream(out);
            oout.writeObject(dummy);
            oout.flush();
            ObjectInputStream oin = new ObjectInputStream(in);
            oin.readObject();
        }
    }
}

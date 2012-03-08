/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.org.jvnet.mimepull;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

/**
 * Removing files based on this
 * <a href="http://java.sun.com/developer/technicalArticles/javase/finalization/">article</a>
 *
 * @author Jitendra Kotamraju
 */
final class WeakDataFile extends WeakReference<DataFile> {

    private static final Logger LOGGER = Logger.getLogger(WeakDataFile.class.getName());
    //private static final int MAX_ITERATIONS = 2;
    private static ReferenceQueue<DataFile> refQueue = new ReferenceQueue<DataFile>();
    private static List<WeakDataFile> refList = new ArrayList<WeakDataFile>();
    private final File file;
    private final RandomAccessFile raf;
    private static boolean hasCleanUpExecutor = false;
    static {
        CleanUpExecutorFactory executorFactory = CleanUpExecutorFactory.newInstance();
        if (executorFactory!=null) {
            LOGGER.fine("Initializing clean up executor for MIMEPULL: "
                    + executorFactory.getClass().getName());
            Executor executor = executorFactory.getExecutor();
            executor.execute(new Runnable() {
                public void run() {
                    WeakDataFile weak = null;
                    while (true) {
                        try {
                            weak = (WeakDataFile) refQueue.remove();
                            LOGGER.fine("Cleaning file = "+weak.file+" from reference queue.");
                            weak.close();
                        } catch (InterruptedException e) {
                        }
                    }
                }
            });
            hasCleanUpExecutor = true;
        }
    }

    WeakDataFile(DataFile df, File file) {
        super(df, refQueue);
        refList.add(this);
        this.file = file;
        try {
            raf = new RandomAccessFile(file, "rw");
        } catch(IOException ioe) {
            throw new MIMEParsingException(ioe);
        }
        if (!hasCleanUpExecutor) {
            drainRefQueueBounded();
        }
    }

    synchronized void read(long pointer, byte[] buf, int offset, int length ) {
        try {
            raf.seek(pointer);
            raf.readFully(buf, offset, length);
        } catch(IOException ioe) {
            throw new MIMEParsingException(ioe);
        }
    }

    synchronized long writeTo(long pointer, byte[] data, int offset, int length) {
        try {
            raf.seek(pointer);
            raf.write(data, offset, length);
            return raf.getFilePointer();    // Update pointer for next write
        } catch(IOException ioe) {
            throw new MIMEParsingException(ioe);
        }
    }

    void close() {
        LOGGER.fine("Deleting file = "+file.getName());
        refList.remove(this);
        try {
            raf.close();
            file.delete();
        } catch(IOException ioe) {
            throw new MIMEParsingException(ioe);
        }
    }

    void renameTo(File f) {
        LOGGER.fine("Moving file="+file+" to="+f);
        refList.remove(this);
        try {
            raf.close();
            file.renameTo(f);
        } catch(IOException ioe) {
            throw new MIMEParsingException(ioe);
        }

    }

    static void drainRefQueueBounded() {
        WeakDataFile weak = null;
        while (( weak = (WeakDataFile) refQueue.poll()) != null ) {
            LOGGER.fine("Cleaning file = "+weak.file+" from reference queue.");
            weak.close();
        }
    }
}

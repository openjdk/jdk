/*
 * Copyright (c) 2002-2016, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package jdk.internal.jline.console.history;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.Flushable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;

import jdk.internal.jline.internal.Log;
import static jdk.internal.jline.internal.Preconditions.checkNotNull;

/**
 * {@link History} using a file for persistent backing.
 * <p/>
 * Implementers should install shutdown hook to call {@link FileHistory#flush}
 * to save history to disk.
 *
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 * @since 2.0
 */
public class FileHistory
    extends MemoryHistory
    implements PersistentHistory, Flushable
{
    private final File file;

    /**
     * Load a history file into memory, truncating to default max size.
     */
    public FileHistory(final File file) throws IOException {
        this(file, true);
    }

    /**
     * Create a FileHistory, but only initialize if doInit is true. This allows
     * setting maxSize or other settings; call load() before using if doInit is
     * false.
     */
    public FileHistory(final File file, final boolean doInit) throws IOException {
        this.file = checkNotNull(file).getAbsoluteFile();
        if (doInit) {
            load();
        }
    }

    /**
     * Load history from file, e.g. if using delayed init.
     */
    public void load() throws IOException {
        load(file);
    }

    public File getFile() {
        return file;
    }

    public void load(final File file) throws IOException {
        checkNotNull(file);
        if (file.exists()) {
            Log.trace("Loading history from: ", file);
            FileReader reader = null;
            try{
                reader = new FileReader(file);
                load(reader);
            } finally{
                if(reader != null){
                    reader.close();
                }
            }
        }
    }

    public void load(final InputStream input) throws IOException {
        checkNotNull(input);
        load(new InputStreamReader(input));
    }

    public void load(final Reader reader) throws IOException {
        checkNotNull(reader);
        BufferedReader input = new BufferedReader(reader);

        String item;
        while ((item = input.readLine()) != null) {
            internalAdd(item);
        }
    }

    public void flush() throws IOException {
        Log.trace("Flushing history");

        if (!file.exists()) {
            File dir = file.getParentFile();
            if (!dir.exists() && !dir.mkdirs()) {
                Log.warn("Failed to create directory: ", dir);
            }
            if (!file.createNewFile()) {
                Log.warn("Failed to create file: ", file);
            }
        }

        PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(file)));
        try {
            for (Entry entry : this) {
                out.println(entry.value());
            }
        }
        finally {
            out.close();
        }
    }

    public void purge() throws IOException {
        Log.trace("Purging history");

        clear();

        if (!file.delete()) {
            Log.warn("Failed to delete history file: ", file);
        }
    }
}

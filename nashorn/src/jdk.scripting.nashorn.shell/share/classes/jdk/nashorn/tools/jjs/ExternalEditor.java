/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.tools.jjs;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.function.Consumer;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

final class ExternalEditor {
    private final Consumer<String> errorHandler;
    private final Consumer<String> saveHandler;
    private final Console input;

    private WatchService watcher;
    private Thread watchedThread;
    private Path dir;
    private Path tmpfile;

    ExternalEditor(Consumer<String> errorHandler, Consumer<String> saveHandler, Console input) {
        this.errorHandler = errorHandler;
        this.saveHandler = saveHandler;
        this.input = input;
    }

    private void edit(String cmd, String initialText) {
        try {
            setupWatch(initialText);
            launch(cmd);
        } catch (IOException ex) {
            errorHandler.accept(ex.getMessage());
        }
    }

    /**
     * Creates a WatchService and registers the given directory
     */
    private void setupWatch(String initialText) throws IOException {
        this.watcher = FileSystems.getDefault().newWatchService();
        this.dir = Files.createTempDirectory("REPL");
        this.tmpfile = Files.createTempFile(dir, null, ".js");
        Files.write(tmpfile, initialText.getBytes(Charset.forName("UTF-8")));
        dir.register(watcher,
                ENTRY_CREATE,
                ENTRY_DELETE,
                ENTRY_MODIFY);
        watchedThread = new Thread(() -> {
            for (;;) {
                WatchKey key;
                try {
                    key = watcher.take();
                } catch (ClosedWatchServiceException ex) {
                    break;
                } catch (InterruptedException ex) {
                    continue; // tolerate an intrupt
                }

                if (!key.pollEvents().isEmpty()) {
                    if (!input.terminalEditorRunning()) {
                        saveFile();
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    errorHandler.accept("Invalid key");
                    break;
                }
            }
        });
        watchedThread.start();
    }

    private void launch(String cmd) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd, tmpfile.toString());
        pb = pb.inheritIO();

        try {
            input.suspend();
            Process process = pb.start();
            process.waitFor();
        } catch (IOException ex) {
            errorHandler.accept("process IO failure: " + ex.getMessage());
        } catch (InterruptedException ex) {
            errorHandler.accept("process interrupt: " + ex.getMessage());
        } finally {
            try {
                watcher.close();
                watchedThread.join(); //so that saveFile() is finished.
                saveFile();
            } catch (InterruptedException ex) {
                errorHandler.accept("process interrupt: " + ex.getMessage());
            } finally {
                input.resume();
            }
        }
    }

    private void saveFile() {
        List<String> lines;
        try {
            lines = Files.readAllLines(tmpfile);
        } catch (IOException ex) {
            errorHandler.accept("Failure read edit file: " + ex.getMessage());
            return ;
        }
        StringBuilder sb = new StringBuilder();
        for (String ln : lines) {
            sb.append(ln);
            sb.append('\n');
        }
        saveHandler.accept(sb.toString());
    }

    static void edit(String cmd, Consumer<String> errorHandler, String initialText,
            Consumer<String> saveHandler, Console input) {
        ExternalEditor ed = new ExternalEditor(errorHandler,  saveHandler, input);
        ed.edit(cmd, initialText);
    }
}

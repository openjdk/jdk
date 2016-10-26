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

package jdk.internal.jshell.tool;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * Wrapper for controlling an external editor.
 */
public class ExternalEditor {
    private final Consumer<String> errorHandler;
    private final Consumer<String> saveHandler;
    private final Consumer<String> printHandler;
    private final IOContext input;
    private final boolean wait;

    private WatchService watcher;
    private Thread watchedThread;
    private Path dir;
    private Path tmpfile;

    ExternalEditor(Consumer<String> errorHandler, Consumer<String> saveHandler,
            IOContext input, boolean wait, Consumer<String> printHandler) {
        this.errorHandler = errorHandler;
        this.saveHandler = saveHandler;
        this.printHandler = printHandler;
        this.input = input;
        this.wait = wait;
    }

    private void edit(String[] cmd, String initialText) {
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
        this.dir = Files.createTempDirectory("jshelltemp");
        this.tmpfile = Files.createTempFile(dir, null, ".java");
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
                    // The watch service has been closed, we are done
                    break;
                } catch (InterruptedException ex) {
                    // tolerate an interrupt
                    continue;
                }

                if (!key.pollEvents().isEmpty()) {
                    // Changes have occurred in temp edit directory,
                    // transfer the new sources to JShell (unless the editor is
                    // running directly in JShell's window -- don't make a mess)
                    if (!input.terminalEditorRunning()) {
                        saveFile();
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    // The watch service has been closed, we are done
                    break;
                }
            }
        });
        watchedThread.start();
    }

    private void launch(String[] cmd) throws IOException {
        String[] params = Arrays.copyOf(cmd, cmd.length + 1);
        params[cmd.length] = tmpfile.toString();
        ProcessBuilder pb = new ProcessBuilder(params);
        pb = pb.inheritIO();

        try {
            input.suspend();
            Process process = pb.start();
            // wait to exit edit mode in one of these ways...
            if (wait) {
                // -wait option -- ignore process exit, wait for carriage-return
                Scanner scanner = new Scanner(System.in);
                printHandler.accept("jshell.msg.press.return.to.leave.edit.mode");
                scanner.nextLine();
            } else {
                // wait for process to exit
                process.waitFor();
            }
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
        try {
            saveHandler.accept(Files.lines(tmpfile).collect(Collectors.joining("\n", "", "\n")));
        } catch (IOException ex) {
            errorHandler.accept("Failure in read edit file: " + ex.getMessage());
        }
    }

    static void edit(String[] cmd, Consumer<String> errorHandler, String initialText,
            Consumer<String> saveHandler, IOContext input, boolean wait, Consumer<String> printHandler) {
        ExternalEditor ed = new ExternalEditor(errorHandler, saveHandler, input, wait, printHandler);
        ed.edit(cmd, initialText);
    }
}

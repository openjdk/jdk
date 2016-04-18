/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @summary Testing external editor.
 * @bug 8080843 8143955
 * @ignore 8080843
 * @modules jdk.jshell/jdk.internal.jshell.tool
 * @build ReplToolTesting CustomEditor EditorTestBase
 * @run testng ExternalEditorTest
 */

import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.fail;

public class ExternalEditorTest extends EditorTestBase {

    private static Path executionScript;
    private static ServerSocket listener;

    private DataInputStream inputStream;
    private DataOutputStream outputStream;

    @Override
    public void writeSource(String s) {
        try {
            outputStream.writeInt(CustomEditor.SOURCE_CODE);
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            outputStream.writeInt(bytes.length);
            outputStream.write(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String getSource() {
        try {
            outputStream.writeInt(CustomEditor.GET_SOURCE_CODE);
            int length = inputStream.readInt();
            byte[] bytes = new byte[length];
            inputStream.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void sendCode(int code) {
        try {
            outputStream.writeInt(code);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void accept() {
        sendCode(CustomEditor.ACCEPT_CODE);
    }

    @Override
    public void exit() {
        sendCode(CustomEditor.EXIT_CODE);
        inputStream = null;
        outputStream = null;
    }

    @Override
    public void cancel() {
        sendCode(CustomEditor.CANCEL_CODE);
    }

    @Override
    public void testEditor(boolean defaultStartup, String[] args, ReplTest... tests) {
        ReplTest[] t = new ReplTest[tests.length + 1];
        t[0] = a -> assertCommandCheckOutput(a, "/set editor " + executionScript,
                assertStartsWith("|  Editor set to: " + executionScript));
        System.arraycopy(tests, 0, t, 1, tests.length);
        super.testEditor(defaultStartup, args, t);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").startsWith("Windows");
    }

    @BeforeClass
    public static void setUpExternalEditorTest() throws IOException {
        listener = new ServerSocket(0);
        listener.setSoTimeout(30000);
        int localPort = listener.getLocalPort();

        executionScript = Paths.get(isWindows() ? "editor.bat" : "editor.sh").toAbsolutePath();
        Path java = Paths.get(System.getProperty("java.home")).resolve("bin").resolve("java");
        try (BufferedWriter writer = Files.newBufferedWriter(executionScript)) {
            if(!isWindows()) {
                writer.append(java.toString()).append(" ")
                        .append(" -cp ").append(System.getProperty("java.class.path"))
                        .append(" CustomEditor ").append(Integer.toString(localPort)).append(" $@");
                executionScript.toFile().setExecutable(true);
            } else {
                writer.append(java.toString()).append(" ")
                        .append(" -cp ").append(System.getProperty("java.class.path"))
                        .append(" CustomEditor ").append(Integer.toString(localPort)).append(" %*");
            }
        }
    }

    private Future<?> task;
    @Override
    public void assertEdit(boolean after, String cmd,
                           Consumer<String> checkInput, Consumer<String> checkOutput, Action action) {
        if (!after) {
            setCommandInput(cmd + "\n");
            task = getExecutor().submit(() -> {
                try (Socket socket = listener.accept()) {
                    inputStream = new DataInputStream(socket.getInputStream());
                    outputStream = new DataOutputStream(socket.getOutputStream());
                    checkInput.accept(getSource());
                    action.accept();
                } catch (SocketTimeoutException e) {
                    fail("Socket timeout exception.\n Output: " + getCommandOutput() +
                            "\n, error: " + getCommandErrorOutput());
                } catch (Throwable e) {
                    shutdownEditor();
                    if (e instanceof AssertionError) {
                        throw (AssertionError) e;
                    }
                    throw new RuntimeException(e);
                }
            });
        } else {
            try {
                task.get();
                checkOutput.accept(getCommandOutput());
            } catch (ExecutionException e) {
                if (e.getCause() instanceof AssertionError) {
                    throw (AssertionError) e.getCause();
                }
                throw new RuntimeException(e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void shutdownEditor() {
        if (outputStream != null) {
            exit();
        }
    }

    @Test
    public void setUnknownEditor() {
        test(
                a -> assertCommand(a, "/set editor", "|  The '/set editor' command requires a path argument"),
                a -> assertCommand(a, "/set editor UNKNOWN", "|  Editor set to: UNKNOWN"),
                a -> assertCommand(a, "int a;", null),
                a -> assertCommand(a, "/ed 1",
                        "|  Edit Error: process IO failure: Cannot run program \"UNKNOWN\": error=2, No such file or directory")
        );
    }

    @Test(enabled = false)
    public void testRemoveTempFile() {
        test(new String[]{"-nostartup"},
                a -> assertCommandCheckOutput(a, "/set editor " + executionScript,
                        assertStartsWith("|  Editor set to: " + executionScript)),
                a -> assertVariable(a, "int", "a", "0", "0"),
                a -> assertEditOutput(a, "/e 1", assertStartsWith("|  Edit Error: Failure read edit file:"), () -> {
                    sendCode(CustomEditor.REMOVE_CODE);
                    exit();
                }),
                a -> assertCommandCheckOutput(a, "/v", assertVariables())
        );
    }

    @AfterClass
    public static void shutdown() throws IOException {
        executorShutdown();
        if (listener != null) {
            listener.close();
        }
    }
}

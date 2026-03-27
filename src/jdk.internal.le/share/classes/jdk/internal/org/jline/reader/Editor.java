/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.reader;

import java.io.IOException;
import java.util.List;

/**
 * Interface for launching external editors from within a JLine application.
 * <p>
 * The Editor interface provides functionality for opening and editing files in
 * an external text editor. This allows JLine applications to offer users the
 * ability to edit content in their preferred editor rather than being limited
 * to the line editing capabilities of the terminal.
 * <p>
 * Typical use cases include:
 * <ul>
 *   <li>Editing configuration files</li>
 *   <li>Writing or modifying scripts</li>
 *   <li>Composing long text content</li>
 * </ul>
 * <p>
 * Implementations of this interface handle the details of launching the external
 * editor process, waiting for it to complete, and potentially reading back the
 * edited content.
 *
 * @see LineReader#editAndAddInBuffer(java.nio.file.Path)
 */
public interface Editor {
    /**
     * Opens the specified files in the external editor.
     * <p>
     * This method launches the external editor with the given files as arguments.
     * The behavior depends on the specific editor implementation and configuration.
     *
     * @param files the list of files to open in the editor
     * @throws IOException if an I/O error occurs while launching the editor
     */
    public void open(List<String> files) throws IOException;

    /**
     * Runs the editor process.
     * <p>
     * This method starts the editor process and typically waits for it to complete.
     * The specific behavior depends on the editor implementation.
     *
     * @throws IOException if an I/O error occurs while running the editor
     */
    public void run() throws IOException;

    /**
     * Sets whether the editor should run in restricted mode.
     * <p>
     * In restricted mode, the editor may have limited functionality or access
     * to certain features or files. This is typically used for security reasons
     * when the application needs to limit what the user can do in the editor.
     *
     * @param restricted true to enable restricted mode, false otherwise
     */
    public void setRestricted(boolean restricted);
}

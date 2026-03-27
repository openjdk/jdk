/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.reader;

/**
 * Callback used to mask parts of the line for sensitive input like passwords.
 * <p>
 * The MaskingCallback interface provides methods to transform the input line
 * both for display purposes and for history storage. This allows applications
 * to implement custom masking strategies for sensitive information, such as
 * passwords, API keys, or other confidential data.
 * <p>
 * When a MaskingCallback is provided to the LineReader, it will be used to:
 * <ul>
 *   <li>Transform the line before displaying it to the user (e.g., replacing password characters with asterisks)</li>
 *   <li>Transform the line before storing it in the history (e.g., removing sensitive information)</li>
 * </ul>
 * <p>
 * A simple implementation is provided in {@link org.jline.reader.impl.SimpleMaskingCallback},
 * which replaces all characters with a single mask character.
 *
 * @see org.jline.reader.impl.SimpleMaskingCallback
 * @see LineReader#readLine(String, String, MaskingCallback, String)
 */
public interface MaskingCallback {

    /**
     * Transforms the line before it is displayed so that sensitive parts can be hidden.
     * <p>
     * This method is called by the LineReader whenever the display needs to be updated.
     * It allows the implementation to replace sensitive information with mask characters
     * or other visual indicators while preserving the actual input for processing.
     * <p>
     * For example, a password masking implementation might replace each character with
     * an asterisk (*) or hide the input entirely.
     *
     * @param line the current line being edited (contains the actual input)
     * @return the modified line to display (with sensitive parts masked)
     */
    String display(String line);

    /**
     * Transforms the line before storing it in the history.
     * <p>
     * This method is called by the LineReader when a line is about to be added to
     * the command history. It allows the implementation to remove or redact sensitive
     * information before it is persisted.
     * <p>
     * If the return value is empty or null, the line will not be saved in the history at all,
     * which is often appropriate for commands containing passwords or other sensitive data.
     * <p>
     * For example, a command like "login --password=secret" might be transformed to
     * "login --password=****" or simply "login" before being stored in history.
     *
     * @param line the line to be added to history (contains the actual input)
     * @return the modified line for history storage, or null/empty to prevent history storage
     */
    String history(String line);
}

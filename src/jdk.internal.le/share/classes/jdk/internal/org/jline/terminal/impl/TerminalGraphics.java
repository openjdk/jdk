/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal.impl;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import jdk.internal.org.jline.terminal.Terminal;

/**
 * Common interface for terminal graphics protocols.
 *
 * <p>This interface provides a unified API for displaying images in terminals
 * using various graphics protocols such as Sixel, Kitty Graphics Protocol,
 * and iTerm2 Inline Images Protocol.</p>
 *
 * <p>Different terminals support different graphics protocols:</p>
 * <ul>
 *   <li><strong>Sixel</strong> - Supported by xterm, iTerm2, foot, WezTerm, etc.</li>
 *   <li><strong>Kitty Graphics Protocol</strong> - Supported by Kitty, Ghostty, WezTerm</li>
 *   <li><strong>iTerm2 Inline Images</strong> - Supported by iTerm2</li>
 * </ul>
 *
 * <p>The implementation automatically detects which protocols are supported
 * and uses the best available option.</p>
 *
 * @since 3.30.0
 */
public interface TerminalGraphics {

    /**
     * Graphics protocol types supported by terminals.
     */
    enum Protocol {
        /** Sixel graphics protocol - widely supported */
        SIXEL("sixel"),

        /** Kitty graphics protocol - modern, feature-rich */
        KITTY("kitty"),

        /** iTerm2 inline images protocol - iTerm2 specific */
        ITERM2("iterm2");

        private final String name;

        Protocol(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Image display options for controlling how images are rendered.
     */
    class ImageOptions {
        private Integer width;
        private Integer height;
        private Boolean preserveAspectRatio = true;
        private Boolean inline = true;
        private String name;

        public ImageOptions() {}

        public ImageOptions width(int width) {
            this.width = width;
            return this;
        }

        public ImageOptions height(int height) {
            this.height = height;
            return this;
        }

        public ImageOptions size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public ImageOptions preserveAspectRatio(boolean preserve) {
            this.preserveAspectRatio = preserve;
            return this;
        }

        public ImageOptions inline(boolean inline) {
            this.inline = inline;
            return this;
        }

        public ImageOptions name(String name) {
            this.name = name;
            return this;
        }

        // Getters
        public Integer getWidth() {
            return width;
        }

        public Integer getHeight() {
            return height;
        }

        public Boolean getPreserveAspectRatio() {
            return preserveAspectRatio;
        }

        public Boolean getInline() {
            return inline;
        }

        public String getName() {
            return name;
        }
    }

    /**
     * Gets the graphics protocol type supported by this implementation.
     *
     * @return the protocol type
     */
    Protocol getProtocol();

    /**
     * Checks if this graphics protocol is supported by the given terminal.
     *
     * @param terminal the terminal to check
     * @return true if the protocol is supported, false otherwise
     */
    boolean isSupported(Terminal terminal);

    /**
     * Gets the priority of this protocol for automatic selection.
     * Higher values indicate higher priority.
     *
     * @return the priority value (0-100)
     */
    int getPriority();

    /**
     * Displays a BufferedImage on the terminal using this graphics protocol.
     *
     * @param terminal the terminal to display the image on
     * @param image the image to display
     * @throws IOException if an I/O error occurs
     * @throws UnsupportedOperationException if the protocol is not supported
     */
    void displayImage(Terminal terminal, BufferedImage image) throws IOException;

    /**
     * Displays a BufferedImage on the terminal with custom options.
     *
     * @param terminal the terminal to display the image on
     * @param image the image to display
     * @param options display options for the image
     * @throws IOException if an I/O error occurs
     * @throws UnsupportedOperationException if the protocol is not supported
     */
    void displayImage(Terminal terminal, BufferedImage image, ImageOptions options) throws IOException;

    /**
     * Displays an image file on the terminal.
     *
     * @param terminal the terminal to display the image on
     * @param file the image file to display
     * @throws IOException if an I/O error occurs
     * @throws UnsupportedOperationException if the protocol is not supported
     */
    void displayImage(Terminal terminal, File file) throws IOException;

    /**
     * Displays an image file on the terminal with custom options.
     *
     * @param terminal the terminal to display the image on
     * @param file the image file to display
     * @param options display options for the image
     * @throws IOException if an I/O error occurs
     * @throws UnsupportedOperationException if the protocol is not supported
     */
    void displayImage(Terminal terminal, File file, ImageOptions options) throws IOException;

    /**
     * Displays an image from an input stream on the terminal.
     *
     * @param terminal the terminal to display the image on
     * @param inputStream the input stream containing the image data
     * @throws IOException if an I/O error occurs
     * @throws UnsupportedOperationException if the protocol is not supported
     */
    void displayImage(Terminal terminal, InputStream inputStream) throws IOException;

    /**
     * Displays an image from an input stream on the terminal with custom options.
     *
     * @param terminal the terminal to display the image on
     * @param inputStream the input stream containing the image data
     * @param options display options for the image
     * @throws IOException if an I/O error occurs
     * @throws UnsupportedOperationException if the protocol is not supported
     */
    void displayImage(Terminal terminal, InputStream inputStream, ImageOptions options) throws IOException;

    /**
     * Converts an image to the protocol-specific format.
     * This method is useful for debugging or when you need the raw protocol data.
     *
     * @param image the image to convert
     * @param options display options for the image
     * @return the protocol-specific representation of the image
     * @throws IOException if an I/O error occurs
     */
    String convertImage(BufferedImage image, ImageOptions options) throws IOException;
}

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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.imageio.ImageIO;

import jdk.internal.org.jline.terminal.Terminal;

/**
 * Implementation of iTerm2's Inline Images Protocol.
 *
 * <p>The iTerm2 Inline Images Protocol allows displaying images directly in the terminal
 * using base64-encoded data embedded in escape sequences. The protocol uses OSC (Operating
 * System Command) escape sequences of the form: {@code <ESC>]1337;File=<parameters>:<base64_data><BEL>}</p>
 *
 * <p>This protocol is supported by:</p>
 * <ul>
 *   <li>iTerm2</li>
 *   <li>Some other terminals with iTerm2 compatibility</li>
 * </ul>
 *
 * <p>The protocol supports various parameters for controlling image display including
 * dimensions, positioning, and preservation of aspect ratio.</p>
 *
 * @see <a href="https://iterm2.com/documentation-images.html">iTerm2 Inline Images</a>
 * @since 3.30.0
 */
public class ITerm2Graphics implements TerminalGraphics {

    /**
     * Creates a new ITerm2Graphics instance.
     */
    public ITerm2Graphics() {
        // Default constructor
    }

    private static final String ITERM2_IMAGE_START = "\033]1337;File=";
    private static final String ITERM2_IMAGE_END = "\007"; // BEL character

    @Override
    public Protocol getProtocol() {
        return Protocol.ITERM2;
    }

    @Override
    public boolean isSupported(Terminal terminal) {
        // Check for iTerm2
        String termProgram = System.getenv("TERM_PROGRAM");
        if ("iTerm.app".equals(termProgram)) {
            return true;
        }

        // Check for iTerm2-specific environment variables
        if (System.getenv("ITERM_SESSION_ID") != null) {
            return true;
        }

        // Check TERM_PROGRAM_VERSION for iTerm2
        String termProgramVersion = System.getenv("TERM_PROGRAM_VERSION");
        if (termProgramVersion != null && "iTerm.app".equals(termProgram)) {
            return true;
        }

        // Check for LC_TERMINAL which iTerm2 sets
        String lcTerminal = System.getenv("LC_TERMINAL");
        if ("iTerm2".equals(lcTerminal)) {
            return true;
        }

        // TODO: Add runtime detection by sending a query and checking response
        // This would require terminal capability querying support

        return false;
    }

    @Override
    public int getPriority() {
        // iTerm2 protocol has medium priority - it's good but specific to iTerm2
        return 70;
    }

    @Override
    public void displayImage(Terminal terminal, BufferedImage image) throws IOException {
        displayImage(terminal, image, new ImageOptions());
    }

    @Override
    public void displayImage(Terminal terminal, BufferedImage image, ImageOptions options) throws IOException {
        String iterm2Data = convertImage(image, options);
        terminal.writer().print(iterm2Data);
        terminal.writer().flush();
    }

    @Override
    public void displayImage(Terminal terminal, File file) throws IOException {
        displayImage(terminal, file, new ImageOptions());
    }

    @Override
    public void displayImage(Terminal terminal, File file, ImageOptions options) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            displayImage(terminal, fis, options);
        }
    }

    @Override
    public void displayImage(Terminal terminal, InputStream inputStream) throws IOException {
        displayImage(terminal, inputStream, new ImageOptions());
    }

    @Override
    public void displayImage(Terminal terminal, InputStream inputStream, ImageOptions options) throws IOException {
        BufferedImage image = ImageIO.read(inputStream);
        if (image == null) {
            throw new IOException("Unable to read image from input stream");
        }
        displayImage(terminal, image, options);
    }

    @Override
    public String convertImage(BufferedImage image, ImageOptions options) throws IOException {
        // Convert BufferedImage to PNG bytes
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        byte[] imageBytes = baos.toByteArray();

        // Encode to base64
        String base64Data = Base64.getEncoder().encodeToString(imageBytes);

        // Build parameters
        StringBuilder parameters = new StringBuilder();

        // Add name if specified
        if (options.getName() != null) {
            parameters.append("name=").append(base64Encode(options.getName())).append(";");
        }

        // Add dimensions if specified
        if (options.getWidth() != null) {
            parameters.append("width=").append(options.getWidth()).append(";");
        }
        if (options.getHeight() != null) {
            parameters.append("height=").append(options.getHeight()).append(";");
        }

        // Set preserve aspect ratio
        if (options.getPreserveAspectRatio() != null && !options.getPreserveAspectRatio()) {
            parameters.append("preserveAspectRatio=0;");
        }

        // Set inline display
        if (options.getInline() != null && !options.getInline()) {
            parameters.append("inline=0;");
        } else {
            parameters.append("inline=1;");
        }

        // Build the complete escape sequence
        StringBuilder result = new StringBuilder();
        result.append(ITERM2_IMAGE_START);
        result.append(parameters.toString());
        result.append(":");
        result.append(base64Data);
        result.append(ITERM2_IMAGE_END);

        return result.toString();
    }

    /**
     * Converts an image with additional iTerm2-specific options.
     *
     * @param image the image to convert
     * @param options display options
     * @param size size specification (e.g., "50%", "100px")
     * @param position position specification for non-inline images
     * @return the complete iTerm2 graphics protocol sequence
     * @throws IOException if an I/O error occurs
     */
    public String convertImageWithExtendedOptions(
            BufferedImage image, ImageOptions options, String size, String position) throws IOException {
        // Convert BufferedImage to PNG bytes
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        byte[] imageBytes = baos.toByteArray();

        // Encode to base64
        String base64Data = Base64.getEncoder().encodeToString(imageBytes);

        // Build parameters
        StringBuilder parameters = new StringBuilder();

        // Add name if specified
        if (options.getName() != null) {
            parameters.append("name=").append(base64Encode(options.getName())).append(";");
        }

        // Add size if specified
        if (size != null) {
            parameters.append("size=").append(size).append(";");
        } else {
            // Add dimensions if specified
            if (options.getWidth() != null) {
                parameters.append("width=").append(options.getWidth()).append(";");
            }
            if (options.getHeight() != null) {
                parameters.append("height=").append(options.getHeight()).append(";");
            }
        }

        // Set preserve aspect ratio
        if (options.getPreserveAspectRatio() != null && !options.getPreserveAspectRatio()) {
            parameters.append("preserveAspectRatio=0;");
        }

        // Set inline display
        if (options.getInline() != null && !options.getInline()) {
            parameters.append("inline=0;");
            // Add position for non-inline images
            if (position != null) {
                parameters.append("position=").append(position).append(";");
            }
        } else {
            parameters.append("inline=1;");
        }

        // Build the complete escape sequence
        StringBuilder result = new StringBuilder();
        result.append(ITERM2_IMAGE_START);
        result.append(parameters.toString());
        result.append(":");
        result.append(base64Data);
        result.append(ITERM2_IMAGE_END);

        return result.toString();
    }

    /**
     * Displays an image with percentage-based sizing.
     *
     * @param terminal the terminal
     * @param image the image to display
     * @param widthPercent width as percentage of terminal width
     * @param heightPercent height as percentage of terminal height (optional)
     * @throws IOException if an I/O error occurs
     */
    public void displayImageWithPercentageSize(
            Terminal terminal, BufferedImage image, int widthPercent, Integer heightPercent) throws IOException {
        String size = widthPercent + "%";
        if (heightPercent != null) {
            size += "x" + heightPercent + "%";
        }

        String iterm2Data = convertImageWithExtendedOptions(image, new ImageOptions(), size, null);
        terminal.writer().print(iterm2Data);
        terminal.writer().flush();
    }

    /**
     * Displays an image as a background (non-inline).
     *
     * @param terminal the terminal
     * @param image the image to display
     * @param position position specification (e.g., "center", "top-left")
     * @throws IOException if an I/O error occurs
     */
    public void displayBackgroundImage(Terminal terminal, BufferedImage image, String position) throws IOException {
        ImageOptions options = new ImageOptions().inline(false);
        String iterm2Data = convertImageWithExtendedOptions(image, options, null, position);
        terminal.writer().print(iterm2Data);
        terminal.writer().flush();
    }

    /**
     * Base64 encodes a string for use in iTerm2 parameters.
     *
     * @param input the string to encode
     * @return the base64-encoded string
     */
    private String base64Encode(String input) {
        return Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }
}

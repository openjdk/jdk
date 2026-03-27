/*
 * Copyright (c) the original author(s).
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */
package jdk.internal.org.jline.terminal.impl;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;

import jdk.internal.org.jline.terminal.Terminal;
import jdk.internal.org.jline.utils.NonBlockingReader;

import static jdk.internal.org.jline.terminal.TerminalBuilder.GRAPHICS_SIXEL_SUBSEQUENT_TIMEOUT;
import static jdk.internal.org.jline.terminal.TerminalBuilder.GRAPHICS_SIXEL_TIMEOUT;

/**
 * Implementation of the Sixel Graphics Protocol.
 *
 * <p>
 * Sixel is a bitmap graphics format supported by some terminals that allows
 * displaying raster graphics directly in the terminal. This class provides
 * methods for converting images to Sixel format and displaying them on
 * terminals that support Sixel graphics.
 * </p>
 *
 * <p>
 * The name "Sixel" comes from "six pixels" because each character cell
 * represents 6 pixels arranged vertically.
 * </p>
 *
 * <p>Sixel graphics are supported by many terminals including:</p>
 * <ul>
 *   <li>xterm</li>
 *   <li>iTerm2</li>
 *   <li>foot</li>
 *   <li>WezTerm</li>
 *   <li>Konsole</li>
 *   <li>VS Code (with enableImages setting)</li>
 * </ul>
 *
 * @since 3.30.0
 */
public class SixelGraphics implements TerminalGraphics {

    /**
     * Creates a new SixelGraphics instance.
     */
    public SixelGraphics() {
        // Default constructor
    }

    private static final String DCS = "\u001bP"; // Device Control String
    private static final String ST = "\u001b\\"; // String Terminator
    private static final String SIXEL_INTRO = "9;1;q"; // Sixel introduction with parameters
    private static final int MAX_COLORS = 256; // Maximum number of colors in palette
    private static final int DEFAULT_WIDTH = 800; // Default max width for images
    private static final int DEFAULT_HEIGHT = 480; // Default max height for images

    // User override for sixel support (volatile for thread-safety)
    private static volatile Boolean sixelSupportOverride = null;

    /**
     * Sets an override for sixel support detection.
     * This can be used to explicitly enable or disable sixel support,
     * regardless of terminal detection.
     *
     * @param supported true to force enable sixel support, false to force disable,
     *                 null to use automatic detection
     */
    public static void setSixelSupportOverride(Boolean supported) {
        sixelSupportOverride = supported;
    }

    /**
     * Checks if the terminal supports Sixel graphics.
     *
     * This method uses a combination of terminal type checking and environment
     * variables to determine if the terminal supports Sixel graphics.
     * The detection can be overridden using setSixelSupportOverride().
     *
     * @param terminal the terminal to check
     * @return true if the terminal supports Sixel graphics, false otherwise
     */
    public static boolean isSixelSupported(Terminal terminal) {
        // Check for user override first
        if (sixelSupportOverride != null) {
            return sixelSupportOverride;
        }

        // Try runtime detection first (if terminal supports it)
        Boolean runtimeDetection = detectSixelSupportRuntime(terminal);
        if (runtimeDetection != null) {
            return runtimeDetection;
        }

        // Fall back to static detection
        return detectSixelSupportStatic(terminal);
    }

    /**
     * Attempts to detect Sixel support at runtime by querying the terminal.
     * This is more reliable than static detection but may not work with all terminals.
     *
     * @param terminal the terminal to query
     * @return true if sixel is supported, false if not supported, null if detection failed
     */
    private static Boolean detectSixelSupportRuntime(Terminal terminal) {
        // Skip runtime detection for terminals we know don't support Sixel
        // This prevents hanging and response leakage
        // Note: We return null (not false) to allow fallback to static detection,
        // since the environment variable might not match the actual terminal type
        String termProgram = System.getenv("TERM_PROGRAM");
        if ("com.mitchellh.ghostty".equals(termProgram)
                || "ghostty".equals(termProgram)
                || "kitty".equals(termProgram)
                || "Apple_Terminal".equals(termProgram)) {
            return null; // Skip runtime detection, fall back to static detection
        }

        jdk.internal.org.jline.terminal.Attributes originalAttributes = null;
        try {
            // Enter raw mode to prevent echo and ensure clean query transmission
            originalAttributes = terminal.enterRawMode();

            // Send Device Attributes query (same method as lsix command)
            terminal.writer().print("\033[c");
            terminal.writer().flush();

            // Read response with configurable timeout (default: 200ms for faster response)
            long timeoutMs = Long.parseLong(System.getProperty(GRAPHICS_SIXEL_TIMEOUT, "200"));
            long subsequentTimeoutMs = Long.parseLong(System.getProperty(GRAPHICS_SIXEL_SUBSEQUENT_TIMEOUT, "25"));
            String response = readTerminalResponse(terminal, timeoutMs, subsequentTimeoutMs);
            if (response != null) {
                // Look for code "4" which indicates Sixel graphics support
                // Response format: ESC[?1;2;4;6;9;15;18;21;22c
                // Code "4" = Sixel graphics support
                return response.contains(";4;") || response.contains(";4c");
            }

            return null; // Detection failed/timed out

        } catch (Exception e) {
            // If runtime detection fails, return null to fall back to static detection
            return null;
        } finally {
            // Always restore original terminal attributes
            if (originalAttributes != null) {
                try {
                    terminal.setAttributes(originalAttributes);
                } catch (Exception e) {
                    // Ignore errors during attribute restoration
                }
            }
        }
    }

    /**
     * Detects Sixel support using static information (terminal type, environment variables).
     *
     * @param terminal the terminal to check
     * @return true if the terminal likely supports Sixel graphics, false otherwise
     */
    private static boolean detectSixelSupportStatic(Terminal terminal) {
        // Check terminal type against known terminals that support sixel
        String terminalType = terminal.getType().toLowerCase(Locale.ROOT);

        // List of terminals known to support sixel
        // Note: xterm variants removed due to false positives - many terminals
        // set TERM to xterm variants but don't actually support Sixel
        Set<String> sixelTerminals = new HashSet<>(Arrays.asList(
                "mintty", // mintty since 2.6.0
                "foot", // foot since 1.2.0
                "iterm2", // iTerm2 since 3.3.0
                "konsole", // konsole since 22.04
                "mlterm", // mlterm since 3.1.9
                "wezterm", // wezterm since 20200620
                "contour", // contour (all versions)
                "domterm", // domterm since 2.0
                "xfce4-terminal" // xfce-terminal since commit 493a7a5
                ));

        // Check if terminal type is in our known list first
        // This takes precedence over environment variables
        if (sixelTerminals.contains(terminalType)) {
            return true;
        }

        // Check for environment variables that might indicate sixel support
        String termEnv = System.getenv("TERM");
        String termProgram = System.getenv("TERM_PROGRAM");
        String termProgramVersion = System.getenv("TERM_PROGRAM_VERSION");

        // Check for terminals that should use other protocols instead of Sixel

        // Ghostty does NOT support Sixel (confirmed by maintainer)
        // Uses Kitty graphics protocol instead
        if ("com.mitchellh.ghostty".equals(termProgram) || "ghostty".equals(termProgram)) {
            return false;
        }

        // Kitty does not support Sixel (by design), uses its own graphics protocol
        if ("kitty".equals(termProgram)) {
            return false;
        }

        // macOS Terminal.app does NOT support Sixel (confirmed by arewesixelyet.com)
        if ("Apple_Terminal".equals(termProgram)) {
            return false;
        }

        // Check for specific terminal programs that support sixel
        if ("iTerm.app".equals(termProgram) && termProgramVersion != null) {
            // iTerm2 supports sixel since version 3.3.0
            try {
                String[] versionParts = termProgramVersion.split("\\.");
                if (versionParts.length >= 2) {
                    int major = Integer.parseInt(versionParts[0]);
                    int minor = Integer.parseInt(versionParts[1]);
                    if (major > 3 || (major == 3 && minor >= 3)) {
                        return true;
                    }
                }
            } catch (NumberFormatException e) {
                // Ignore parsing errors
            }
        }

        // Note: We used to check for XTerm with sixel support here, but this caused
        // too many false positives since many terminals set TERM to xterm variants
        // but don't actually support Sixel. Runtime detection should handle real xterm.

        return false;
    }

    /**
     * Displays an image in Sixel format on the terminal.
     *
     * @param terminal the terminal to display the image on
     * @param image the image to display
     * @throws IOException if an I/O error occurs
     */
    public static void displayImageStatic(Terminal terminal, BufferedImage image) throws IOException {
        if (!isSixelSupported(terminal)) {
            throw new UnsupportedOperationException("Terminal does not support Sixel graphics");
        }

        String sixelData = convertToSixel(image);
        terminal.writer().print(sixelData);
        terminal.writer().println();
        terminal.writer().flush();
    }

    /**
     * Displays an image file in Sixel format on the terminal.
     *
     * @param terminal the terminal to display the image on
     * @param file the image file to display
     * @throws IOException if an I/O error occurs
     */
    public static void displayImageStatic(Terminal terminal, File file) throws IOException {
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            throw new IOException("Unable to read image file: unsupported format or invalid file");
        }
        displayImageStatic(terminal, image);
    }

    /**
     * Displays an image from an input stream in Sixel format on the terminal.
     *
     * @param terminal the terminal to display the image on
     * @param inputStream the input stream containing the image data
     * @throws IOException if an I/O error occurs
     */
    public static void displayImageStatic(Terminal terminal, InputStream inputStream) throws IOException {
        BufferedImage image = ImageIO.read(inputStream);
        if (image == null) {
            throw new IOException("Unable to read image from stream: unsupported format or invalid data");
        }
        displayImageStatic(terminal, image);
    }

    /**
     * Converts a BufferedImage to Sixel format.
     *
     * @param image the image to convert (must not be null)
     * @return a string containing the Sixel data
     * @throws NullPointerException if image is null
     */
    public static String convertToSixel(BufferedImage image) throws IOException {
        if (image == null) {
            throw new NullPointerException("Image must not be null");
        }

        // Resize the image if it's too large
        BufferedImage resizedImage = resizeImageIfNeeded(image);

        // Convert to indexed color if needed
        BufferedImage indexedImage = convertToIndexedColor(resizedImage);

        // Generate Sixel data
        return generateSixelData(indexedImage);
    }

    /**
     * Resizes an image if it's too large for terminal display.
     * Uses high-quality scaling to maintain image quality.
     *
     * @param image the image to resize
     * @return the resized image, or the original image if no resizing is needed
     */
    private static BufferedImage resizeImageIfNeeded(BufferedImage image) {
        // Use the default max dimensions
        return resizeImageIfNeeded(image, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * Resizes an image if it's too large for the specified dimensions.
     * Uses high-quality scaling to maintain image quality.
     *
     * @param image the image to resize
     * @param maxWidth the maximum width
     * @param maxHeight the maximum height
     * @return the resized image, or the original image if no resizing is needed
     */
    private static BufferedImage resizeImageIfNeeded(BufferedImage image, int maxWidth, int maxHeight) {
        if (image.getWidth() <= maxWidth && image.getHeight() <= maxHeight) {
            return image;
        }

        // Calculate new dimensions while maintaining aspect ratio
        double scale = Math.min((double) maxWidth / image.getWidth(), (double) maxHeight / image.getHeight());

        int newWidth = (int) (image.getWidth() * scale);
        int newHeight = (int) (image.getHeight() * scale);

        // Create a new image with transparency support if the original has it
        int imageType = image.getTransparency() == BufferedImage.OPAQUE
                ? BufferedImage.TYPE_INT_RGB
                : BufferedImage.TYPE_INT_ARGB;

        BufferedImage resized = new BufferedImage(newWidth, newHeight, imageType);
        Graphics2D g = resized.createGraphics();

        // Use high quality rendering
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw the image with a white background if needed
        if (imageType == BufferedImage.TYPE_INT_RGB) {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, newWidth, newHeight);
        }

        g.drawImage(image, 0, 0, newWidth, newHeight, null);
        g.dispose();

        return resized;
    }

    /**
     * Converts an image to indexed color with a maximum of MAX_COLORS colors.
     * Uses a better approach to color quantization to preserve image quality.
     *
     * @param image the image to convert
     * @return the converted image
     */
    private static BufferedImage convertToIndexedColor(BufferedImage image) {
        // First convert to RGB if it's not already
        BufferedImage rgbImage;
        if (image.getType() != BufferedImage.TYPE_INT_RGB) {
            rgbImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgbImage.createGraphics();
            g.setColor(Color.WHITE); // Set white background
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.drawImage(image, 0, 0, null);
            g.dispose();
        } else {
            rgbImage = image;
        }

        // Create a color model with a maximum of MAX_COLORS colors
        // We'll use Java's built-in color quantization but with some pre-processing
        BufferedImage indexedImage =
                new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_INDEXED);

        // Draw the image with dithering to improve color representation
        Graphics2D g = indexedImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(rgbImage, 0, 0, null);
        g.dispose();

        return indexedImage;
    }

    /**
     * Generates Sixel data from an indexed color image.
     * Uses optimized encoding to reduce size and improve display quality.
     *
     * @param image the indexed color image
     * @return a string containing the Sixel data
     */
    private static String generateSixelData(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Start with DCS sequence and Sixel introduction
        // Parameters: 9;1;q means:
        // - 9: Pixel aspect ratio for square pixels (1:1)
        // - 1: Background color handling (0=device default, 1=no change, 2=set to 0)
        // - q: Sixel mode indicator
        baos.write((DCS + SIXEL_INTRO).getBytes(StandardCharsets.US_ASCII));

        // Extract color palette
        java.awt.image.IndexColorModel cm = (java.awt.image.IndexColorModel) image.getColorModel();
        int colorCount = Math.min(cm.getMapSize(), MAX_COLORS);

        // Define color palette - only include colors that are actually used
        // This helps reduce the size of the Sixel data
        boolean[] colorUsed = new boolean[colorCount];
        int width = image.getWidth();
        int height = image.getHeight();

        // Create a color index map for the image
        int[][] colorMap = new int[height][width];

        // First scan to find used colors and build color map
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = image.getRGB(x, y) & 0xFFFFFF;
                int colorIndex = findClosestColorIndex(cm, pixel);
                colorMap[y][x] = colorIndex;
                if (colorIndex < colorCount) {
                    colorUsed[colorIndex] = true;
                }
            }
        }

        // Define only the colors that are used
        for (int i = 0; i < colorCount; i++) {
            if (colorUsed[i]) {
                int r = cm.getRed(i);
                int g = cm.getGreen(i);
                int b = cm.getBlue(i);

                // Sixel color definition: "#" + color_index + ";" + color_spec + ";" + r + ";" + g + ";" + b
                // RGB values need to be scaled to 0-100 range
                String colorDef = String.format("#%d;2;%d;%d;%d", i, (r * 100) / 255, (g * 100) / 255, (b * 100) / 255);
                baos.write(colorDef.getBytes(StandardCharsets.US_ASCII));
            }
        }

        // Process image data in 6-pixel-high strips
        // We'll use a simpler approach: for each strip, output all colors
        for (int y = 0; y < height; y += 6) {
            // For each used color in this strip
            for (int colorIndex = 0; colorIndex < colorCount; colorIndex++) {
                if (!colorUsed[colorIndex]) {
                    continue;
                }

                // Check if this color appears in this strip
                boolean colorInStrip = false;
                for (int checkY = y; checkY < Math.min(y + 6, height) && !colorInStrip; checkY++) {
                    for (int checkX = 0; checkX < width && !colorInStrip; checkX++) {
                        if (colorMap[checkY][checkX] == colorIndex) {
                            colorInStrip = true;
                        }
                    }
                }

                if (!colorInStrip) {
                    continue;
                }

                // Select this color
                baos.write(String.format("#%d", colorIndex).getBytes(StandardCharsets.US_ASCII));

                // For each column in this strip
                for (int x = 0; x < width; x++) {
                    int sixelData = 0;

                    // Collect 6 vertical pixels for this color
                    for (int subY = 0; subY < 6; subY++) {
                        if (y + subY < height && colorMap[y + subY][x] == colorIndex) {
                            // Set the bit for this pixel (bit 0 is top pixel)
                            sixelData |= (1 << subY);
                        }
                    }

                    // Output sixel character
                    baos.write((char) (sixelData + 63));
                }

                // Graphics carriage return to return to start of line
                baos.write('$');
            }

            // Graphics new line to move to next strip
            baos.write('-');
        }

        // End Sixel data with ST sequence
        baos.write(ST.getBytes(StandardCharsets.US_ASCII));

        return baos.toString(StandardCharsets.US_ASCII.name());
    }

    /**
     * Finds the closest color in the color model to the given RGB value.
     *
     * @param cm the color model
     * @param rgb the RGB value
     * @return the index of the closest color
     */
    private static int findClosestColorIndex(java.awt.image.IndexColorModel cm, int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        int closestIndex = 0;
        int closestDistance = Integer.MAX_VALUE;

        for (int i = 0; i < cm.getMapSize(); i++) {
            int cr = cm.getRed(i);
            int cg = cm.getGreen(i);
            int cb = cm.getBlue(i);

            // Simple Euclidean distance in RGB space
            int distance = (r - cr) * (r - cr) + (g - cg) * (g - cg) + (b - cb) * (b - cb);

            if (distance < closestDistance) {
                closestDistance = distance;
                closestIndex = i;
            }
        }

        return closestIndex;
    }

    // TerminalGraphics interface implementation

    @Override
    public Protocol getProtocol() {
        return Protocol.SIXEL;
    }

    @Override
    public int getPriority() {
        return 10; // Lower priority than Kitty and iTerm2
    }

    @Override
    public boolean isSupported(Terminal terminal) {
        return isSixelSupported(terminal);
    }

    @Override
    public void displayImage(Terminal terminal, BufferedImage image) throws IOException {
        displayImageStatic(terminal, image);
    }

    @Override
    public void displayImage(Terminal terminal, File file) throws IOException {
        displayImageStatic(terminal, file);
    }

    @Override
    public void displayImage(Terminal terminal, InputStream inputStream) throws IOException {
        displayImageStatic(terminal, inputStream);
    }

    /**
     * Displays an image with size constraints.
     * This is a convenience method not part of the TerminalGraphics interface.
     *
     * @param terminal the terminal to display the image on
     * @param image the image to display
     * @param maxWidth maximum width for the image
     * @param maxHeight maximum height for the image
     * @throws IOException if an I/O error occurs
     */
    public void displayImage(Terminal terminal, BufferedImage image, int maxWidth, int maxHeight) throws IOException {
        // Resize image if needed
        BufferedImage resizedImage = resizeImageIfNeeded(image, maxWidth, maxHeight);
        displayImageStatic(terminal, resizedImage);
    }

    @Override
    public void displayImage(Terminal terminal, BufferedImage image, TerminalGraphics.ImageOptions options)
            throws IOException {
        BufferedImage processedImage = image;

        // Apply size options if specified
        if (options.getWidth() != null || options.getHeight() != null) {
            int targetWidth = options.getWidth() != null ? options.getWidth() : image.getWidth();
            int targetHeight = options.getHeight() != null ? options.getHeight() : image.getHeight();

            if (options.getPreserveAspectRatio() != null && options.getPreserveAspectRatio()) {
                // Calculate dimensions preserving aspect ratio
                double aspectRatio = (double) image.getWidth() / image.getHeight();
                if (options.getWidth() != null && options.getHeight() == null) {
                    targetHeight = (int) (targetWidth / aspectRatio);
                } else if (options.getHeight() != null && options.getWidth() == null) {
                    targetWidth = (int) (targetHeight * aspectRatio);
                }
            }

            processedImage = resizeImageIfNeeded(image, targetWidth, targetHeight);
        }

        displayImageStatic(terminal, processedImage);
    }

    @Override
    public void displayImage(Terminal terminal, File file, TerminalGraphics.ImageOptions options) throws IOException {
        BufferedImage image = ImageIO.read(file);
        if (image == null) {
            throw new IOException("Unable to read image file: unsupported format or invalid file");
        }
        displayImage(terminal, image, options);
    }

    @Override
    public void displayImage(Terminal terminal, InputStream inputStream, TerminalGraphics.ImageOptions options)
            throws IOException {
        BufferedImage image = ImageIO.read(inputStream);
        if (image == null) {
            throw new IOException("Unable to read image from stream: unsupported format or invalid data");
        }
        displayImage(terminal, image, options);
    }

    @Override
    public String convertImage(BufferedImage image, TerminalGraphics.ImageOptions options) throws IOException {
        BufferedImage processedImage = image;

        // Apply size options if specified
        if (options.getWidth() != null || options.getHeight() != null) {
            int targetWidth = options.getWidth() != null ? options.getWidth() : image.getWidth();
            int targetHeight = options.getHeight() != null ? options.getHeight() : image.getHeight();

            if (options.getPreserveAspectRatio() != null && options.getPreserveAspectRatio()) {
                // Calculate dimensions preserving aspect ratio
                double aspectRatio = (double) image.getWidth() / image.getHeight();
                if (options.getWidth() != null && options.getHeight() == null) {
                    targetHeight = (int) (targetWidth / aspectRatio);
                } else if (options.getHeight() != null && options.getWidth() == null) {
                    targetWidth = (int) (targetHeight * aspectRatio);
                }
            }

            processedImage = resizeImageIfNeeded(image, targetWidth, targetHeight);
        }

        return convertToSixel(processedImage);
    }

    /**
     * Gets a human-readable name for this graphics protocol.
     *
     * @return the protocol name
     */
    public String getName() {
        return getProtocol().getName();
    }

    /**
     * Gets a description of this graphics protocol.
     *
     * @return a description of the protocol
     */
    public String getDescription() {
        return "Sixel graphics protocol - widely supported bitmap format";
    }

    /**
     * Reads a response from the terminal with a timeout.
     * Used for runtime detection of terminal capabilities.
     *
     * @param terminal the terminal to read from
     * @param timeoutMs timeout in milliseconds
     * @return the response string, or null if timeout/error
     */
    private static String readTerminalResponse(Terminal terminal, long timeoutMs, long subsequentTimeoutMs)
            throws IOException {
        try {
            NonBlockingReader reader = terminal.reader();
            StringBuilder response = new StringBuilder();

            long startTime = System.currentTimeMillis();
            int c;

            // Read characters until we get a complete response or timeout
            while ((c = reader.read(timeoutMs)) >= 0) {
                response.append((char) c);

                // Check if we have a complete Device Attributes response
                // Format: ESC[?...c or ESC[...c
                String responseStr = response.toString();
                if (responseStr.contains("\033[") && responseStr.endsWith("c")) {
                    return responseStr;
                }

                // Safety check: don't read forever
                if (response.length() > 200 || (System.currentTimeMillis() - startTime) > timeoutMs) {
                    break;
                }

                // Use shorter timeout for subsequent characters (configurable)
                timeoutMs = subsequentTimeoutMs;
            }

            // Return what we got, even if incomplete
            return response.length() > 0 ? response.toString() : null;

        } catch (Exception e) {
            return null;
        }
    }
}

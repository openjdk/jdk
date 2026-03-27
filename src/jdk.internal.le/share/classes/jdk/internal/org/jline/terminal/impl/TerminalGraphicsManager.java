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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.CopyOnWriteArrayList;

import jdk.internal.org.jline.terminal.Terminal;

/**
 * Manager for terminal graphics protocols.
 *
 * <p>This class provides a unified interface for displaying images in terminals
 * using the best available graphics protocol. It automatically detects which
 * protocols are supported by the terminal and selects the most appropriate one.</p>
 *
 * <p>The manager supports multiple graphics protocols:</p>
 * <ul>
 *   <li><strong>Kitty Graphics Protocol</strong> - Modern, feature-rich protocol</li>
 *   <li><strong>iTerm2 Inline Images</strong> - iTerm2's proprietary protocol</li>
 *   <li><strong>Sixel</strong> - Widely supported legacy protocol</li>
 * </ul>
 *
 * <p>Protocols are selected based on priority and terminal support. Higher priority
 * protocols are preferred when multiple protocols are available.</p>
 *
 * @since 3.30.0
 */
public class TerminalGraphicsManager {

    /**
     * Creates a new TerminalGraphicsManager instance.
     */
    public TerminalGraphicsManager() {
        // Default constructor
    }

    // Use CopyOnWriteArrayList for thread-safe reads without synchronization
    // Writes (registerProtocol) are rare, reads (getBestProtocol) are frequent
    private static final List<TerminalGraphics> AVAILABLE_PROTOCOLS = new CopyOnWriteArrayList<>();
    private static volatile TerminalGraphics.Protocol forcedProtocol = null;
    private static final boolean JAVA_DESKTOP_AVAILABLE;

    static {
        // Check if java.desktop module is available
        boolean desktopAvailable = false;
        try {
            // Try to load a class from java.desktop to verify it's available
            Class.forName("java.awt.image.BufferedImage");
            desktopAvailable = true;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            // java.desktop is not available - graphics features will be disabled
            System.err.println("Warning: java.desktop module not available. Terminal graphics features are disabled.");
        }
        JAVA_DESKTOP_AVAILABLE = desktopAvailable;

        if (JAVA_DESKTOP_AVAILABLE) {
            // Register built-in protocols only if java.desktop is available
            try {
                registerProtocol(new KittyGraphics());
                registerProtocol(new ITerm2Graphics());
                registerProtocol(new SixelGraphics());

                // Load additional protocols via ServiceLoader
                ServiceLoader<TerminalGraphics> loader = ServiceLoader.load(TerminalGraphics.class);
                for (TerminalGraphics protocol : loader) {
                    registerProtocol(protocol);
                }

                // Sort by priority (highest first)
                AVAILABLE_PROTOCOLS.sort(
                        Comparator.comparingInt(TerminalGraphics::getPriority).reversed());
            } catch (NoClassDefFoundError e) {
                // This shouldn't happen if the check above passed, but handle it gracefully
                System.err.println("Error initializing graphics protocols: " + e.getMessage());
            }
        }
    }

    /**
     * Registers a graphics protocol implementation.
     * The protocol list is automatically re-sorted by priority after registration.
     * If a protocol of the same type is already registered, it will not be added again.
     *
     * @param protocol the protocol implementation to register
     */
    public static synchronized void registerProtocol(TerminalGraphics protocol) {
        // Check if a protocol of the same type is already registered
        boolean alreadyRegistered =
                AVAILABLE_PROTOCOLS.stream().anyMatch(p -> p.getProtocol() == protocol.getProtocol());

        if (!alreadyRegistered) {
            AVAILABLE_PROTOCOLS.add(protocol);
            // Re-sort to maintain priority ordering (highest first)
            AVAILABLE_PROTOCOLS.sort(
                    Comparator.comparingInt(TerminalGraphics::getPriority).reversed());
        }
    }

    /**
     * Forces the use of a specific graphics protocol, overriding automatic detection.
     * This is useful for testing or when automatic detection fails.
     *
     * @param protocol the protocol to force, or null to enable automatic detection
     */
    public static void forceProtocol(TerminalGraphics.Protocol protocol) {
        forcedProtocol = protocol;
    }

    /**
     * Gets the forced protocol, if any.
     *
     * @return the forced protocol, or null if automatic detection is enabled
     */
    public static TerminalGraphics.Protocol getForcedProtocol() {
        return forcedProtocol;
    }

    /**
     * Checks if the java.desktop module is available.
     * Graphics features require java.desktop for AWT imaging types.
     *
     * @return true if java.desktop is available, false otherwise
     */
    public static boolean isJavaDesktopAvailable() {
        return JAVA_DESKTOP_AVAILABLE;
    }

    /**
     * Finds the best graphics protocol for the given terminal.
     *
     * @param terminal the terminal to check
     * @return the best available graphics protocol, or empty if none are supported
     */
    public static Optional<TerminalGraphics> getBestProtocol(Terminal terminal) {
        // If a protocol is forced, try to find and return it (but still check if supported)
        if (forcedProtocol != null) {
            return AVAILABLE_PROTOCOLS.stream()
                    .filter(p -> p.getProtocol() == forcedProtocol)
                    .filter(protocol -> protocol.isSupported(terminal))
                    .findFirst();
        }

        // Find the highest priority protocol that is supported
        return AVAILABLE_PROTOCOLS.stream()
                .filter(protocol -> protocol.isSupported(terminal))
                .findFirst();
    }

    /**
     * Gets all available graphics protocols.
     *
     * @return a list of all registered graphics protocols
     */
    public static List<TerminalGraphics> getAvailableProtocols() {
        return new ArrayList<>(AVAILABLE_PROTOCOLS);
    }

    /**
     * Gets all graphics protocols supported by the given terminal.
     *
     * @param terminal the terminal to check
     * @return a list of supported graphics protocols, sorted by priority
     */
    public static List<TerminalGraphics> getSupportedProtocols(Terminal terminal) {
        return AVAILABLE_PROTOCOLS.stream()
                .filter(protocol -> protocol.isSupported(terminal))
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Checks if any graphics protocol is supported by the given terminal.
     *
     * @param terminal the terminal to check
     * @return true if at least one graphics protocol is supported
     */
    public static boolean isGraphicsSupported(Terminal terminal) {
        return getBestProtocol(terminal).isPresent();
    }

    /**
     * Displays a BufferedImage on the terminal using the best available protocol.
     *
     * @param terminal the terminal to display the image on
     * @param image the image to display
     * @throws IOException if an I/O error occurs
     * @throws UnsupportedOperationException if no graphics protocol is supported or java.desktop is not available
     */
    public static void displayImage(Terminal terminal, BufferedImage image) throws IOException {
        if (!JAVA_DESKTOP_AVAILABLE) {
            throw new UnsupportedOperationException("Terminal graphics require java.desktop module. "
                    + "Please ensure java.desktop is available in your runtime environment.");
        }
        TerminalGraphics protocol = getBestProtocol(terminal)
                .orElseThrow(
                        () -> new UnsupportedOperationException("No graphics protocol supported by this terminal"));
        protocol.displayImage(terminal, image);
    }

    /**
     * Displays a BufferedImage on the terminal with custom options.
     *
     * @param terminal the terminal to display the image on
     * @param image the image to display
     * @param options display options for the image
     * @throws IOException if an I/O error occurs
     * @throws UnsupportedOperationException if no graphics protocol is supported or java.desktop is not available
     */
    public static void displayImage(Terminal terminal, BufferedImage image, TerminalGraphics.ImageOptions options)
            throws IOException {
        if (!JAVA_DESKTOP_AVAILABLE) {
            throw new UnsupportedOperationException("Terminal graphics require java.desktop module. "
                    + "Please ensure java.desktop is available in your runtime environment.");
        }
        TerminalGraphics protocol = getBestProtocol(terminal)
                .orElseThrow(
                        () -> new UnsupportedOperationException("No graphics protocol supported by this terminal"));
        protocol.displayImage(terminal, image, options);
    }

    /**
     * Displays an image file on the terminal using the best available protocol.
     *
     * @param terminal the terminal to display the image on
     * @param file the image file to display
     * @throws IOException if an I/O error occurs
     * @throws UnsupportedOperationException if no graphics protocol is supported or java.desktop is not available
     */
    public static void displayImage(Terminal terminal, File file) throws IOException {
        if (!JAVA_DESKTOP_AVAILABLE) {
            throw new UnsupportedOperationException("Terminal graphics require java.desktop module. "
                    + "Please ensure java.desktop is available in your runtime environment.");
        }
        TerminalGraphics protocol = getBestProtocol(terminal)
                .orElseThrow(
                        () -> new UnsupportedOperationException("No graphics protocol supported by this terminal"));
        protocol.displayImage(terminal, file);
    }

    /**
     * Displays an image file on the terminal with custom options.
     *
     * @param terminal the terminal to display the image on
     * @param file the image file to display
     * @param options display options for the image
     * @throws IOException if an I/O error occurs
     * @throws UnsupportedOperationException if no graphics protocol is supported or java.desktop is not available
     */
    public static void displayImage(Terminal terminal, File file, TerminalGraphics.ImageOptions options)
            throws IOException {
        if (!JAVA_DESKTOP_AVAILABLE) {
            throw new UnsupportedOperationException("Terminal graphics require java.desktop module. "
                    + "Please ensure java.desktop is available in your runtime environment.");
        }
        TerminalGraphics protocol = getBestProtocol(terminal)
                .orElseThrow(
                        () -> new UnsupportedOperationException("No graphics protocol supported by this terminal"));
        protocol.displayImage(terminal, file, options);
    }

    /**
     * Displays an image from an input stream on the terminal using the best available protocol.
     *
     * @param terminal the terminal to display the image on
     * @param inputStream the input stream containing the image data
     * @throws IOException if an I/O error occurs
     * @throws UnsupportedOperationException if no graphics protocol is supported or java.desktop is not available
     */
    public static void displayImage(Terminal terminal, InputStream inputStream) throws IOException {
        if (!JAVA_DESKTOP_AVAILABLE) {
            throw new UnsupportedOperationException("Terminal graphics require java.desktop module. "
                    + "Please ensure java.desktop is available in your runtime environment.");
        }
        TerminalGraphics protocol = getBestProtocol(terminal)
                .orElseThrow(
                        () -> new UnsupportedOperationException("No graphics protocol supported by this terminal"));
        protocol.displayImage(terminal, inputStream);
    }

    /**
     * Displays an image from an input stream on the terminal with custom options.
     *
     * @param terminal the terminal to display the image on
     * @param inputStream the input stream containing the image data
     * @param options display options for the image
     * @throws IOException if an I/O error occurs
     * @throws UnsupportedOperationException if no graphics protocol is supported or java.desktop is not available
     */
    public static void displayImage(Terminal terminal, InputStream inputStream, TerminalGraphics.ImageOptions options)
            throws IOException {
        if (!JAVA_DESKTOP_AVAILABLE) {
            throw new UnsupportedOperationException("Terminal graphics require java.desktop module. "
                    + "Please ensure java.desktop is available in your runtime environment.");
        }
        TerminalGraphics protocol = getBestProtocol(terminal)
                .orElseThrow(
                        () -> new UnsupportedOperationException("No graphics protocol supported by this terminal"));
        protocol.displayImage(terminal, inputStream, options);
    }
}

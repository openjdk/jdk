/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.awt.screencast;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.PosixFilePermission;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import static sun.awt.screencast.ScreencastHelper.SCREENCAST_DEBUG;

/**
 * Helper class for persistent storage of ScreenCast restore tokens
 * and associated screen boundaries.
 *
 * The restore token allows the ScreenCast session to be restored
 * with previously granted screen access permissions.
 */
@SuppressWarnings("removal")
final class TokenStorage {

    private TokenStorage() {}

    private static final String REL_NAME =
            ".awt/robot/screencast-tokens.properties";

    private static final Properties PROPS = new Properties();
    private static final Path PROPS_PATH;
    private static final Path PROP_FILENAME;

    private static void doPrivilegedRunnable(Runnable runnable) {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            @Override
            public Void run() {
                runnable.run();
                return null;
            }
        });
    }

    static {
        PROPS_PATH = AccessController.doPrivileged(new PrivilegedAction<Path>() {
            @Override
            public Path run() {
                return setupPath();
            }
        });

        if (PROPS_PATH != null) {
            PROP_FILENAME = PROPS_PATH.getFileName();
            if (SCREENCAST_DEBUG) {
                System.out.println("Token storage: using " + PROPS_PATH);
            }
            setupWatch();
        } else {
            // We can still work with tokens,
            // but they are not saved between sessions.
            PROP_FILENAME = null;
        }
    }

    private static Path setupPath() {
        String userHome = System.getProperty("user.home", null);
        if (userHome == null) {
            return null;
        }

        Path path = Path.of(userHome, REL_NAME);
        Path workdir = path.getParent();

        if (!Files.exists(workdir)) {
            try {
                Files.createDirectories(workdir);
            } catch (Exception e) {
                if (SCREENCAST_DEBUG) {
                    System.err.printf("Token storage: cannot create" +
                                    " directory %s %s\n", workdir, e);
                }
                return null;
            }
        }

        if (!Files.isWritable(workdir)) {
            if (SCREENCAST_DEBUG) {
                System.err.printf("Token storage: %s is not writable\n", workdir);
            }
            return null;
        }

        try {
            Files.setPosixFilePermissions(
                    workdir,
                    Set.of(PosixFilePermission.OWNER_READ,
                           PosixFilePermission.OWNER_WRITE,
                           PosixFilePermission.OWNER_EXECUTE)
            );
        } catch (IOException e) {
            if (SCREENCAST_DEBUG) {
                System.err.printf("Token storage: cannot set permissions " +
                        "for directory %s %s\n", workdir, e);
            }
        }

        if (Files.exists(path)) {
            if (!setFilePermission(path)) {
                return null;
            }

            readTokens(path);
        }

        return path;
    }

    private static boolean setFilePermission(Path path) {
        try {
            Files.setPosixFilePermissions(
                    path,
                    Set.of(PosixFilePermission.OWNER_READ,
                           PosixFilePermission.OWNER_WRITE)
            );
            return true;
        } catch (IOException e) {
            if (SCREENCAST_DEBUG) {
                System.err.printf("Token storage: failed to set " +
                        "property file permission %s %s\n", path, e);
            }
        }
        return false;
    }

    private static class WatcherThread extends Thread {
        private final WatchService watcher;

        public WatcherThread(WatchService watchService) {
            this.watcher = watchService;
            setName("ScreencastWatcher");
            setDaemon(true);
        }

        @Override
        public void run() {
            if (SCREENCAST_DEBUG) {
                System.out.println("ScreencastWatcher: started");
            }
            for (;;) {
                WatchKey key;
                try {
                    key = watcher.take();
                } catch (InterruptedException e) {
                    if (SCREENCAST_DEBUG) {
                        System.err.println("ScreencastWatcher: interrupted");
                    }
                    return;
                }

                for (WatchEvent<?> event: key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == OVERFLOW
                            || !event.context().equals(PROP_FILENAME)) {
                        continue;
                    }

                    if (SCREENCAST_DEBUG) {
                        System.out.printf("ScreencastWatcher: %s %s\n",
                                kind, event.context());
                    }

                    if (kind == ENTRY_CREATE) {
                        doPrivilegedRunnable(() -> setFilePermission(PROPS_PATH));
                    } else if (kind == ENTRY_MODIFY) {
                        doPrivilegedRunnable(() -> readTokens(PROPS_PATH));
                    } else if (kind == ENTRY_DELETE) {
                        synchronized (PROPS) {
                            PROPS.clear();
                        }
                    }
                }

                key.reset();
            }
        }
    }

    private static WatchService watchService;

    private static void setupWatch() {
        doPrivilegedRunnable(() -> {
            try {
                watchService =
                        FileSystems.getDefault().newWatchService();

                PROPS_PATH
                        .getParent()
                        .register(watchService,
                                ENTRY_CREATE,
                                ENTRY_DELETE,
                                ENTRY_MODIFY);

            } catch (Exception e) {
                if (SCREENCAST_DEBUG) {
                    System.err.printf("Token storage: failed to setup " +
                            "file watch %s\n", e);
                }
            }
        });

        if (watchService != null) {
            new WatcherThread(watchService).start();
        }
    }

    // called from native
    private static void storeTokenFromNative(String oldToken,
                                             String newToken,
                                             int[] allowedScreenBounds) {
        if (SCREENCAST_DEBUG) {
            System.out.printf("// storeToken old: |%s| new |%s| " +
                            "allowed bounds %s\n",
                    oldToken, newToken,
                    Arrays.toString(allowedScreenBounds));
        }

        if (allowedScreenBounds == null) return;

        TokenItem tokenItem = new TokenItem(newToken, allowedScreenBounds);

        if (SCREENCAST_DEBUG) {
            System.out.printf("// Storing TokenItem:\n%s\n", tokenItem);
        }

        synchronized (PROPS) {
            String oldBoundsRecord = PROPS.getProperty(tokenItem.token, null);
            String newBoundsRecord = tokenItem.dump();

            boolean changed = false;

            if (oldBoundsRecord == null
                    || !oldBoundsRecord.equals(newBoundsRecord)) {
                PROPS.setProperty(tokenItem.token, newBoundsRecord);
                if (SCREENCAST_DEBUG) {
                    System.out.printf(
                            "// Writing new TokenItem:\n%s\n", tokenItem);
                }
                changed = true;
            }

            if (oldToken != null && !oldToken.equals(newToken)) {
                // old token is no longer valid
                if (SCREENCAST_DEBUG) {
                    System.out.printf(
                            "// storeTokenFromNative old token |%s| is "
                                    + "no longer valid, removing\n", oldToken);
                }

                PROPS.remove(oldToken);
                changed = true;
            }

            if (changed) {
                doPrivilegedRunnable(() -> store("save tokens"));
            }
        }
    }

    private static boolean readTokens(Path path) {
        if (path == null) return false;

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            synchronized (PROPS) {
                PROPS.clear();
                PROPS.load(reader);
            }
        } catch (IOException e) {
            if (SCREENCAST_DEBUG) {
                System.err.printf("""
                        Token storage: failed to load property file %s
                        %s
                        """, path, e);
            }
            return false;
        }

        return true;
    }

    static Set<TokenItem> getTokens(List<Rectangle> affectedScreenBounds) {
        // We need an ordered set to store tokens
        // with exact matches at the beginning.
        LinkedHashSet<TokenItem> result = new LinkedHashSet<>();

        Set<String> malformed = new HashSet<>();
        List<TokenItem> allTokenItems;

        synchronized (PROPS) {
            allTokenItems =
                    PROPS.entrySet()
                    .stream()
                    .map(o -> {
                        String token = String.valueOf(o.getKey());
                        TokenItem tokenItem =
                                TokenItem.parse(token, o.getValue());
                        if (tokenItem == null) {
                            malformed.add(token);
                        }
                        return tokenItem;
                    })
                    .filter(Objects::nonNull)
                    .sorted((t1, t2) -> //Token with more screens preferred
                            t2.allowedScreensBounds.size()
                            - t1.allowedScreensBounds.size()
                    )
                    .toList();
        }

        doPrivilegedRunnable(() -> removeMalformedRecords(malformed));

        // 1. Try to find exact matches
        for (TokenItem tokenItem : allTokenItems) {
            if (tokenItem != null
                && tokenItem.hasAllScreensWithExactMatch(affectedScreenBounds)) {

                result.add(tokenItem);
            }
        }

        if (SCREENCAST_DEBUG) {
            System.out.println("// getTokens exact matches 1. " + result);
        }

        // 2. Try screens of the same size but in different locations,
        // screens may have been moved while the token is still valid
        List<Dimension> dimensions =
                affectedScreenBounds
                .stream()
                .map(rectangle -> new Dimension(
                        rectangle.width,
                        rectangle.height
                ))
                .toList();

        for (TokenItem tokenItem : allTokenItems) {
            if (tokenItem != null
                && tokenItem.hasAllScreensOfSameSize(dimensions)) {

                result.add(tokenItem);
            }
        }

        if (SCREENCAST_DEBUG) {
            System.out.println("// getTokens same sizes 2. " + result);
        }

        // 3. add tokens with the same or greater number of screens
        // This is useful if we once received a token with one screen resolution
        // and the same screen was later scaled in the system.
        // In that case, the token is still valid.

        allTokenItems
                .stream()
                .filter(t ->
                        t.allowedScreensBounds.size() >= affectedScreenBounds.size())
                .forEach(result::add);

        return result;
    }

    private static void removeMalformedRecords(Set<String> malformedRecords) {
        if (!isWritable()
            || malformedRecords == null
            || malformedRecords.isEmpty()) {
            return;
        }

        synchronized (PROPS) {
            for (String token : malformedRecords) {
                Object remove = PROPS.remove(token);
                if (SCREENCAST_DEBUG) {
                    System.err.println("removing malformed record\n" + remove);
                }
            }

            store("remove malformed records");
        }
    }

    private static void store(String failMsg) {
        if (!isWritable()) {
            return;
        }

        synchronized (PROPS) {
            try (BufferedWriter writer = Files.newBufferedWriter(PROPS_PATH)) {
                PROPS.store(writer, null);
            } catch (IOException e) {
                if (SCREENCAST_DEBUG) {
                    System.err.printf(
                            "Token storage: unable to %s\n%s\n", failMsg, e);
                }
            }
        }
    }

    private static boolean isWritable() {
        if (PROPS_PATH == null
            || (Files.exists(PROPS_PATH) && !Files.isWritable(PROPS_PATH))) {

            if (SCREENCAST_DEBUG) {
                System.err.printf(
                        "Token storage: %s is not writable\n", PROPS_PATH);
            }
            return false;
        } else {
            return true;
        }
    }
}

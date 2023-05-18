package sun.awt.screencast;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import static sun.awt.screencast.ScreencastHelper.SCREENCAST_DEBUG;

final class TokenStorage {
    private TokenStorage() {
    }

    private static final Preferences TOKEN_STORE;

    static {
        Preferences tokenStore;
        try {
            tokenStore = Preferences
                    .userNodeForPackage(ScreencastHelper.class)
                    .node("tokenStorage");
        } catch (Exception e) {
            System.err.println("Failed to initialize token storage");
            tokenStore = null;
        }

        TOKEN_STORE = tokenStore;
    }


    static boolean initSuccessful() {
        return TOKEN_STORE != null;
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

        storeToken(new TokenItem(newToken, allowedScreenBounds));

        if (oldToken != null && !oldToken.equals(newToken)) {
            // old token is no longer valid
            if (SCREENCAST_DEBUG) {
                System.out.printf(
                        "// storeTokenFromNative old token |%s| is "
                        + "no longer valid, removing\n", oldToken);
            }
            TOKEN_STORE.remove(oldToken);
        }
    }

    private static void storeToken(TokenItem tokenItem) {
        if (SCREENCAST_DEBUG) {
            System.out.printf("Storing TokenItem:\n%s\n", tokenItem);
        }

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(bos);
            out.writeObject(tokenItem);
            TOKEN_STORE.putByteArray(tokenItem.token, bos.toByteArray());
            TOKEN_STORE.flush();
        } catch (IOException | BackingStoreException e) {
            if (SCREENCAST_DEBUG) {
                System.err.println(e);
            }
        }
    }

    private static TokenItem readToken(String token) {
        byte[] bytes = TOKEN_STORE.getByteArray(token, null);
        if (bytes == null) {
            return null;
        }
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            ObjectInputStream in = new ObjectInputStream(bis);
            Object o = in.readObject();
            if (o instanceof TokenItem t) {
                if (SCREENCAST_DEBUG) {
                    System.out.printf("TokenItem read:\n%s\n", t);
                }
                return t;
            }
        } catch (ClassNotFoundException | IOException e) {
            if (SCREENCAST_DEBUG) {
                System.err.println(e);
            }
        }
        return null;
    }

    static Set<TokenItem> getTokens(List<Rectangle> affectedScreenBounds) {
        // We need an ordered set to store tokens with exact matches at the beginning.
        LinkedHashSet<TokenItem> result = new LinkedHashSet<>();
        try {
            TOKEN_STORE.sync();

            List<TokenItem> allTokenItems =
                    Arrays.stream(TOKEN_STORE.keys())
                            .map(TokenStorage::readToken)
                            .toList();


            // 1. Try to find exact matches
            for (TokenItem tokenItem : allTokenItems) {
                if (tokenItem != null
                        && tokenItem
                        .hasAllScreensWithExactMatch(affectedScreenBounds)) {
                    result.add(tokenItem);
                }
            }

            if (SCREENCAST_DEBUG) {
                System.out.println("// getTokens exact matches 1. " + result);
            }


            // 2. Try screens of the same size but in different locations,
            // screens may have been moved while the token is still valid
            List<Dimension> dimensions = affectedScreenBounds
                    .stream()
                    .map(rectangle -> new Dimension(
                            rectangle.width,
                            rectangle.height
                    ))
                    .collect(Collectors.toCollection(ArrayList::new));

            for (TokenItem tokenItem : allTokenItems) {
                if (tokenItem != null
                        && tokenItem.hasAllScreensOfSameSize(dimensions)) {
                    result.add(tokenItem);
                }
            }

            if (SCREENCAST_DEBUG) {
                System.out.println("// getTokens same sizes 2. " + result);
            }

            return result;
        } catch (BackingStoreException e) {
            if (SCREENCAST_DEBUG) {
                System.err.println(e);
            }
        }
        return Set.of();
    }
}

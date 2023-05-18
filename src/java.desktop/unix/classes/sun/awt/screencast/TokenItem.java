package sun.awt.screencast;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

final class TokenItem implements Serializable {
    static final long serialVersionUID = -289029960632065531L;

    String token;
    ArrayList<Rectangle> allowedScreensBounds;

    public TokenItem(String token, int[] allowedScreenBounds) {
        if (allowedScreenBounds.length % 4 != 0) {
            throw new RuntimeException("array with incorrect length provided");
        }

        this.token = token;

        this.allowedScreensBounds =
                (ArrayList<Rectangle>) IntStream
                        .iterate(0,
                                i -> i < allowedScreenBounds.length,
                                i -> i + 4)
                        .mapToObj(i -> new Rectangle(
                                allowedScreenBounds[i], allowedScreenBounds[i+1],
                                allowedScreenBounds[i+2], allowedScreenBounds[i+3]
                        ))
                        .collect(Collectors.toList());
    }

    public boolean hasAllScreensWithExactMatch(List<Rectangle> bounds) {
        return allowedScreensBounds.containsAll(bounds);
    }

    public boolean hasAllScreensOfSameSize(List<Dimension> screenSizes) {
        // We also need to consider duplicates, since there may be
        // multiple screens of the same size.
        // The token item must also have at least the same number
        // of screens with that size.

        List<Dimension> tokenSizes = allowedScreensBounds
                .stream()
                .map(bounds -> new Dimension(bounds.width, bounds.height))
                .collect(Collectors.toCollection(ArrayList::new));

        return screenSizes.size() == screenSizes
                .stream()
                .filter(tokenSizes::remove)
                .count();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Token: " + token + "\n");
        for (Rectangle bounds : allowedScreensBounds) {
            sb.append("\t").append(bounds).append("\n");
        }
        return sb.toString();
    }
}
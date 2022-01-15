package sun.java2d.xr;

import java.util.List;

/**
 * Replaces GrowableRectArray.
 * For storing rectangles, though different from {@link java.awt.Rectangle} because.
 *
 * @author SWinxy
 */
public record Rect(int x, int y, int width, int height) {

    public static int[] collect(List<Rect> rects) {
        int[] out = new int[rects.size() * 4];

        for (int i = 0; i < rects.size(); i++) {
            Rect rect = rects.get(i);
            out[i] = rect.x;
            out[i + 1] = rect.y;
            out[i + 2] = rect.width;
            out[i + 3] = rect.height;
        }

        return out;
    }

    public static void move(List<Rect> rects, int x, int y) {
        for (int i = 0; i < rects.size(); i++) {
            Rect rect = rects.get(i);
            rects.add(new Rect(rect.x() + x, rect.y() + y, rect.width(), rect.height()));
        }
    }
}

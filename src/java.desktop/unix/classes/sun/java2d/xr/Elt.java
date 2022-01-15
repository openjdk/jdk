package sun.java2d.xr;

import java.util.List;

/**
 * Replaces GrowableEltArray.
 *
 * @author SWinxy
 */
public record Elt(int charCnt, int xOff, int yOff, int glyphSet) {

    public static int[] collect(List<Elt> elts) {
        int[] out = new int[elts.size() * 4];

        for (int i = 0; i < elts.size(); i++) {
            Elt elt = elts.get(i);
            out[i] = elt.charCnt;
            out[i + 1] = elt.xOff;
            out[i + 2] = elt.yOff;
            out[i + 3] = elt.glyphSet;
        }

        return out;
    }
}

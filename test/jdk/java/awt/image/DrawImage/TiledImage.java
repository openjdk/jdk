import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.util.Objects;
import java.util.Vector;


/**
 * Test drawing a tiled image made of non-writable {@link Raster} tiles.
 * Drawing works when tiles are instances of {@link WritableRaster}.
 * But if tiles are instances of {@link Raster} only, then the following
 * exception is thrown:
 *
 * <pre>
 * Exception in thread "main" java.awt.image.RasterFormatException: (parentX + width) is outside raster
 *     at java.desktop/java.awt.image.WritableRaster.createWritableChild(WritableRaster.java:228)
 *     at java.desktop/sun.java2d.SunGraphics2D.drawTranslatedRenderedImage(SunGraphics2D.java:2852)
 *     at java.desktop/sun.java2d.SunGraphics2D.drawRenderedImage(SunGraphics2D.java:2711)</pre>
 *
 * The bug is demonstrated by drawing the same image twice:
 * once with {@link WritableRaster} tiles (which succeed),
 * then the same operation but with {@link Raster} tiles.
 *
 * The bug is caused by the following code in {@code SunGraphics2D}:
 *
 * <pre>
 * // Create a WritableRaster containing the tile
 * WritableRaster wRaster = null;
 * if (raster instanceof WritableRaster) {
 *     wRaster = (WritableRaster)raster;
 * } else {
 *     // Create a WritableRaster in the same coordinate system
 *     // as the original raster.
 *     wRaster =
 *         Raster.createWritableRaster(raster.getSampleModel(),
 *                                     raster.getDataBuffer(),
 *                                     null);
 * }
 * // Translate wRaster to start at (0, 0) and to contain
 * // only the relevent portion of the tile
 * wRaster = wRaster.createWritableChild(tileRect.x, tileRect.y,
 *                                       tileRect.width,
 *                                       tileRect.height,
 *                                       0, 0,
 *                                       null);
 * </pre>
 *
 * If {@code raster} is not an instance of {@link WritableRaster},
 * then a new {@link WritableRaster} is created wrapping the same
 * buffer <strong>but with a location of (0,0)</strong>, because
 * the {@code location} argument of {@code createWritableRaster}
 * is null. Consequently the call to {@code createWritableChild}
 * shall not be done in that case, because the raster is already
 * translated. The current code applies translation twice.
 *
 * This bug is largely unnoticed because most {@code Raster.create}
 * methods actually create {@link WritableRaster} instances, even
 * when the user did not asked for writable raster. To make this
 * bug apparent, we need to invoke {@code Raster.createRaster(…)}
 * with a sample model for which no optimization is provided.
 */
final class TiledImage implements RenderedImage {
    /**
     * Run the test using writable tiles first, then read-only tiles.
     */
    public static void main(String[] args) {
        draw(true);         // Pass.
        draw(false);        // Fail with exception described in javadoc.
    }

    private static final int NUM_X_TILES = 2, NUM_Y_TILES = 3;

    private static final int TILE_WIDTH = 16, TILE_HEIGHT = 12;

    /**
     * Tests rendering a tiled image.
     *
     * @param  writable  whether the image shall use writable raster.
     */
    private static void draw(final boolean writable) {
        final BufferedImage target = new BufferedImage(
                TILE_WIDTH  * NUM_X_TILES,
                TILE_HEIGHT * NUM_Y_TILES,
                BufferedImage.TYPE_BYTE_GRAY);

        final RenderedImage source = new TiledImage(writable,
                target.getColorModel());

        Graphics2D g = target.createGraphics();
        g.drawRenderedImage(source, new AffineTransform());
        g.dispose();
    }

    private final ColorModel colorModel;

    private final Raster[] tiles;

    /**
     * Creates a tiled image. The image is empty,
     * but pixel values are not the purpose of this test.
     *
     * @param  writable  whether the image shall use writable raster.
     */
    private TiledImage(boolean writable, ColorModel cm) {
        /*
         * We need a sample model class for which Raster.createRaster
         * do not provide a special case. That method has optimizations
         * for most SampleModel sub-types, except BandedSampleModel.
         */
        SampleModel sm = new BandedSampleModel(DataBuffer.TYPE_BYTE, TILE_WIDTH, TILE_HEIGHT, 1);
        tiles = new Raster[NUM_X_TILES * NUM_Y_TILES];
        final Point location = new Point();
        for (int tileY = 0; tileY < NUM_Y_TILES; tileY++) {
            for (int tileX = 0; tileX < NUM_X_TILES; tileX++) {
                location.x = tileX * TILE_WIDTH;
                location.y = tileY * TILE_HEIGHT;
                DataBufferByte db = new DataBufferByte(TILE_WIDTH * TILE_HEIGHT);
                Raster r;
                if (writable) {
                    r = Raster.createWritableRaster(sm, db, location);
                } else {
                    // Case causing RasterFormatException later.
                    r = Raster.createRaster(sm, db, location);
                }
                tiles[tileX + tileY * NUM_X_TILES] = r;
            }
        }
        colorModel = cm;
    }

    @Override
    public ColorModel getColorModel() {
        return colorModel;
    }

    @Override
    public SampleModel getSampleModel() {
        return tiles[0].getSampleModel();
    }

    @Override
    public Vector<RenderedImage> getSources() {
        return new Vector();
    }

    @Override
    public Object getProperty(String key) {
        return Image.UndefinedProperty;
    }

    @Override
    public String[] getPropertyNames() {
        return null;
    }

    @Override public int getMinX()            {return 0;}
    @Override public int getMinY()            {return 0;}
    @Override public int getMinTileX()        {return 0;}
    @Override public int getMinTileY()        {return 0;}
    @Override public int getTileGridXOffset() {return 0;}
    @Override public int getTileGridYOffset() {return 0;}
    @Override public int getNumXTiles()       {return NUM_X_TILES;}
    @Override public int getNumYTiles()       {return NUM_Y_TILES;}
    @Override public int getTileWidth()       {return TILE_WIDTH;}
    @Override public int getTileHeight()      {return TILE_HEIGHT;}
    @Override public int getWidth()           {return TILE_WIDTH  * NUM_X_TILES;}
    @Override public int getHeight()          {return TILE_HEIGHT * NUM_Y_TILES;}

    @Override
    public Raster getTile(final int tileX, final int tileY) {
        Objects.checkIndex(tileX, NUM_X_TILES);
        Objects.checkIndex(tileY, NUM_Y_TILES);
        return tiles[tileX + tileY * NUM_X_TILES];
    }

    @Override
    public Raster getData() {
        throw new UnsupportedOperationException("Not needed for this test.");
    }

    @Override
    public Raster getData(Rectangle rect) {
        throw new UnsupportedOperationException("Not needed for this test.");
    }

    @Override
    public WritableRaster copyData(WritableRaster raster) {
        throw new UnsupportedOperationException("Not needed for this test.");
    }
}

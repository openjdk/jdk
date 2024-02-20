package build.tools.runtimelink;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jdk.internal.jimage.BasicImageReader;

public class ImageReader extends BasicImageReader implements JimageDiffGenerator.ImageResource {

    public ImageReader(Path path) throws IOException {
        super(path);
    }

    public static boolean isNotTreeInfoResource(String path) {
        return !(path.startsWith("/packages") || path.startsWith("/modules"));
    }

    @Override
    public List<String> getEntries() {
        return Arrays.asList(getEntryNames()).stream()
                .filter(ImageReader::isNotTreeInfoResource)
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public byte[] getResourceBytes(String name) {
        return getResource(name);
    }

}

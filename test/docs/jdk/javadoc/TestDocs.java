/*
 * @test
 * @library /test/lib
 * @build jtreg.SkippedException
 * @summary example of a test on the generated documentation
 * @run main TestDocs
 */

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import jtreg.SkippedException;

public class TestDocs {
    private static final Path ROOT_PATH = Path.of(System.getProperty("test.jdk"));

    public static Path resolveDocs() {
        Path firstCandidate = ROOT_PATH.getParent()
                .resolve("docs");
        Path secondCandidate = ROOT_PATH.getParent().getParent()
                .resolve("docs.doc_api_spec").resolve("docs");

        if (Files.exists(firstCandidate)) {
            return firstCandidate;
        } else if (Files.exists(secondCandidate)) {
            return secondCandidate;
        } else {
            throw new SkippedException("docs folder not found in either location");
        }
    }

    public static void main(String[] args) throws Exception {
        Path docRoot = resolveDocs();
        System.err.println(docRoot);
        final List<Path> files=new ArrayList<>();
        try {
            Files.walkFileTree(docRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!attrs.isDirectory()) {
                        files.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (var file : files) {
            if (!Files.isReadable(file)) {
                throw new Exception("File " + file + " is unreadable");
            }
        }
    }
}

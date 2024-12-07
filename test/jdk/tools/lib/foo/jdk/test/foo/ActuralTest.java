package jdk.test.foo;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collections;
import sun.nio.fs.DefaultFileSystemProvider;

public class ActuralTest {
    public static void main(String[] args) throws IOException, ClassNotFoundException, IllegalAccessException {

        //get the 'BOOT_MODULES_JIMAGE' field of local ImageReaderFactory
        Field local_boot_modules_jimage_field = jdk.internal.jimage.ImageReaderFactory.class.getDeclaredFields()[1];
        local_boot_modules_jimage_field.setAccessible(true);
        Path local_boot_modules_jimage = (Path) local_boot_modules_jimage_field.get(null);
        if ( DefaultFileSystemProvider.theFileSystem() != local_boot_modules_jimage.getFileSystem() ){
            throw new AssertionError("Creating local_boot_modules_jimage field should use sun.nio.fs.DefaultFileSystemProvider.theFileSystem() when ImageReaderFactory is loaded by boot classloader");
        }

        String targetJDK = System.getProperty("java.home");
        System.out.println("java.home: " + targetJDK);
        // set target jdk
        FileSystem jrtFs = FileSystems.newFileSystem(URI.create("jrt:/"), Collections.singletonMap("java.home", targetJDK));
        ClassLoader jrtFsLoader = jrtFs.getClass().getClassLoader();
        // get the 'BOOT_MODULES_JIMAGE' field of target ImageReaderFactory and verify the fileSystem which created the BOOT_MODULES_JIMAGE
        Field target_boot_modules_jimage_field = Class.forName("jdk.internal.jimage.ImageReaderFactory", true, jrtFsLoader).getDeclaredFields()[1];
        target_boot_modules_jimage_field.setAccessible(true);
        Path target_boot_modules_jimage = (Path) target_boot_modules_jimage_field.get(null);
        if ( FileSystems.getDefault() != target_boot_modules_jimage.getFileSystem() ){
            throw new AssertionError("Creating target_boot_modules_jimage field should use FileSystems.getDefault() when ImageReaderFactory is loaded by custom classloader");
        }
        jrtFs.close();

        //If the -Djava.nio.file.spi.DefaultFileSystemProvider value was set and DefaultFileSystemProvider was loaded successfully within jimage
        System.out.println("success");
    }
}

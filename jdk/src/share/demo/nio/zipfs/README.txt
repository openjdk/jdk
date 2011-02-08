ZipFileSystem is a file system provider that treats the contents of a zip or
JAR file as a java.nio.file.FileSystem.

The factory methods defined by the java.nio.file.FileSystems class can be
used to create a FileSystem, eg:

   // use file type detection
   Path jarfile = Paths.get("foo.jar");
   FileSystem fs = FileSystems.newFileSystem(jarfile, null);

-or

   // locate file system by the legacy JAR URL syntax
   Map<String,?> env = Collections.emptyMap();
   URI uri = URI.create("jar:file:/mydir/foo.jar");
   FileSystem fs = FileSystems.newFileSystem(uri, env);

Once a FileSystem is created then classes in the java.nio.file package
can be used to access files in the zip/JAR file, eg:

   Path mf = fs.getPath("/META-INF/MANIFEST.MF");
   InputStream in = mf.newInputStream();

See Demo.java for more interesting usages.



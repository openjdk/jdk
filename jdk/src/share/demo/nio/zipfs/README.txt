ZipFileSystem is a file system provider that treats the contents of a zip or
JAR file as a java.nio.file.FileSystem.

To deploy the provider you must copy zipfs.jar into your extensions
directory or else add <JDK_HOME>/demo/nio/ZipFileSystem/zipfs.jar
to your class path.

The factory methods defined by the java.nio.file.FileSystems class can be
used to create a FileSystem, eg:

   // use file type detection
   Map<String,?> env = Collections.emptyMap();
   Path jarfile = Path.get("foo.jar");
   FileSystem fs = FileSystems.newFileSystem(jarfile, env);

-or

   // locate file system by URI
   Map<String,?> env = Collections.emptyMap();
   URI uri = URI.create("zip:///mydir/foo.jar");
   FileSystem fs = FileSystems.newFileSystem(uri, env);

Once a FileSystem is created then classes in the java.nio.file package
can be used to access files in the zip/JAR file, eg:

   Path mf = fs.getPath("/META-INF/MANIFEST.MF");
   InputStream in = mf.newInputStream();



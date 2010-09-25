import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
public class Loader2 extends ClassLoader {
  int _recur;
  public void print( String msg ) {
    for( int i=0; i<_recur; i++ )
      System.out.print("  ");
    System.out.println(">>Loader2>> "+msg);
  }

  protected Class findClass2(String name) throws ClassNotFoundException {
    print("Fetching the implementation of "+name);
    int old = _recur;
    try {
      FileInputStream fi = new FileInputStream(name+".impl2");
      byte result[] = new byte[fi.available()];
      fi.read(result);

      print("DefineClass1 on "+name);
      _recur++;
      Class clazz = defineClass(name, result, 0, result.length);
      _recur = old;
      print("Returning newly loaded class.");
      return clazz;
    } catch (Exception e) {
      _recur = old;
      print("Not found on disk.");
      // If we caught an exception, either the class was not found or
      // it was unreadable by our process.
      return null;
      //throw new ClassNotFoundException(e.toString());
    }
  }

  protected synchronized Class loadClass(String name, boolean resolve) throws ClassNotFoundException  {
    // Attempt a disk load first
    Class c = findClass2(name);
    if( c == null ) {
      // check if the class has already been loaded
      print("Checking for prior loaded class "+name);
      c = findLoadedClass(name);
      print("Letting super-loader load "+name);
      int old = _recur;
      _recur++;
      c = super.loadClass(name, false);
      _recur=old;
    }
    if (resolve) { print("Resolving class "+name); resolveClass(c); }
    print("Returning clazz "+c.getClassLoader()+":"+name);
    return c;
  }
}

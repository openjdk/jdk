public class Foo {
static void printValue(String name, boolean property) {

    String value = (property) ? System.getProperty(name) : System.getenv(name);

    System.out.println(name + "=" + value);

}

public static void main(String... args) {

    System.out.println("Execute test:");

    printValue("os.name", true);

    printValue("os.arch", true);

    printValue("os.version", true);

    printValue("sun.arch.data.model", true);

    printValue("java.library.path", true);

    printValue("LIBPATH", false);

    printValue("LIBPATH_32", false);

    printValue("LIBPATH_64", false);

}

}

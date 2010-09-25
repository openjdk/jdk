/*
 * @test
 * @bug 6894807
 * @summary No ClassCastException for HashAttributeSet constructors if run with -Xcomp
 * @compile IsInstanceTest.java
 * @run shell Test6894807.sh
*/

public class IsInstanceTest {

    public static void main(String[] args) {
        BaseInterface baseInterfaceImpl = new BaseInterfaceImpl();
        for (int i = 0; i < 100000; i++) {
            if (isInstanceOf(baseInterfaceImpl, ExtendedInterface.class)) {
                System.out.println("Failed at index:" + i);
                System.out.println("Arch: "+System.getProperty("os.arch", "")+
                                   " OS: "+System.getProperty("os.name", "")+
                                   " OSV: "+System.getProperty("os.version", "")+
                                   " Cores: "+Runtime.getRuntime().availableProcessors()+
                                   " JVM: "+System.getProperty("java.version", "")+" "+System.getProperty("sun.arch.data.model", ""));
                break;
            }
        }
        System.out.println("Done!");
    }

    public static boolean isInstanceOf(BaseInterface baseInterfaceImpl, Class... baseInterfaceClasses) {
        for (Class baseInterfaceClass : baseInterfaceClasses) {
            if (baseInterfaceClass.isInstance(baseInterfaceImpl)) {
                return true;
            }
        }
        return false;
    }

    private interface BaseInterface {
    }

    private interface ExtendedInterface extends BaseInterface {
    }

    private static class BaseInterfaceImpl implements BaseInterface {
    }
}

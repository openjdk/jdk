import p1.AInterface;
import p3.ServiceInterface;

module m1 {
    exports p1;

    requires transitive java.desktop;
    requires m3;

    provides ServiceInterface
            with AInterface;
}

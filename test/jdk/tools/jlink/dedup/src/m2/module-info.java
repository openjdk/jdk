import m2.p2.BInterface;
import m3.p3.ServiceInterface;

module m2 {
    exports m2.p2 to m3;

    requires transitive java.desktop;
    requires m3;

    provides ServiceInterface
            with BInterface;
}

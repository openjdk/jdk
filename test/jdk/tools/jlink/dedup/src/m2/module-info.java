import p2.BInterface;
import p3.ServiceInterface;

module m2 {
    exports p2 to m3;

    requires transitive java.desktop;
    requires m3;

    provides p3.ServiceInterface
            with BInterface;
}

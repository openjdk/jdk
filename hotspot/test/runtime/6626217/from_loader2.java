// A simple class to extend an abstract class and get loaded with different
// loaders.  This class is loaded via LOADER2.
public class from_loader2 implements IFace {
  public many_loader[] gen() {
    many_loader[] x = new many_loader[1];
    x[0] = new many_loader();
    return x;
  }
}

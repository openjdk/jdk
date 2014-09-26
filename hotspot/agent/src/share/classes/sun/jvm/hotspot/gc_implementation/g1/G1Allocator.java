package sun.jvm.hotspot.gc_implementation.g1;

import java.util.Observable;
import java.util.Observer;

import sun.jvm.hotspot.debugger.Address;
import sun.jvm.hotspot.runtime.VM;
import sun.jvm.hotspot.runtime.VMObject;
import sun.jvm.hotspot.types.CIntegerField;
import sun.jvm.hotspot.types.Type;
import sun.jvm.hotspot.types.TypeDataBase;

public class G1Allocator extends VMObject {

  //size_t _summary_bytes_used;
  static private CIntegerField summaryBytesUsedField;

  static {
    VM.registerVMInitializedObserver(new Observer() {
      public void update(Observable o, Object data) {
        initialize(VM.getVM().getTypeDataBase());
      }
    });
  }

  static private synchronized void initialize(TypeDataBase db) {
    Type type = db.lookupType("G1Allocator");

    summaryBytesUsedField = type.getCIntegerField("_summary_bytes_used");
  }

  public long getSummaryBytes() {
    return summaryBytesUsedField.getValue(addr);
  }

  public G1Allocator(Address addr) {
    super(addr);

  }
}

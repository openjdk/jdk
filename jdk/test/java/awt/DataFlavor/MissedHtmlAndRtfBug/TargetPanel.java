import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class TargetPanel extends Panel implements DropTargetListener {


    //private final CustomDropTargetListener dropTargetListener = new CustomDropTargetListener();

    private Frame frame;
    DataFlavor dataFlavor;

    public TargetPanel(Frame frame, DataFlavor dataFlavor) {
        this.dataFlavor = dataFlavor;
        this.frame = frame;
        setBackground(Color.DARK_GRAY);
        setPreferredSize(new Dimension(200, 200));
        setDropTarget(new DropTarget(this, this));
    }

    public void dragEnter(DropTargetDragEvent dtde) {
        if (dtde.isDataFlavorSupported(dataFlavor)) {
            dtde.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
        }
    }

    public void dragOver(DropTargetDragEvent dtde) {
        if (dtde.isDataFlavorSupported(dataFlavor)) {
            dtde.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
        }
    }

    public void dropActionChanged(DropTargetDragEvent dtde) {
        if (dtde.isDataFlavorSupported(dataFlavor)) {
            dtde.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
        }
    }

    public void dragExit(DropTargetEvent dte) {

    }

    public void drop(DropTargetDropEvent dtde) {
        dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
        if (dtde.isDataFlavorSupported(dataFlavor)) {
            String result = null;
            try {
                Transferable t = dtde.getTransferable();
                byte[] data = (byte[]) dtde.getTransferable().getTransferData(dataFlavor);
                result = new String(data, "UTF-16");
                repaint();
            } catch (UnsupportedFlavorException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            dtde.dropComplete(true);


            if (result != null && result.contains(MyTransferable.TEST_DATA)) {
                System.err.println(InterprocessMessages.EXECUTION_IS_SUCCESSFULL);
                Timer t = new Timer();
                t.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        System.exit(0);
                    }
                }, 2000);
                return;

            }
        }
        dtde.rejectDrop();
        System.err.println(InterprocessMessages.DATA_IS_CORRUPTED);
        System.exit(InterprocessMessages.DATA_IS_CORRUPTED);
    }

}

package nmt;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HexFormat;

public class LogFile
{
    static final HexFormat HEX = HexFormat.of();	

    final String name;
    int size = 0;
    RandomAccessFile file = null;
    MappedByteBuffer out = null;

    public LogFile(String name)
    {
        super();
        this.name = name;

        try
        {
            RandomAccessFile f = new RandomAccessFile(this.name, "r"); // check if the file exists
            f.close();

            this.file = new RandomAccessFile(this.name, "rw"); // open for reading and writing
            if (this.file.length() < Integer.MAX_VALUE)
            {
                this.size = (int)this.file.length();
            }
            else
            {
                throw new RuntimeException("this.file.length() > Integer.MAX_VALUE ["+this.file.length()+"]");
            }
            //System.out.println(String.format("file: \"%s\", length: %.2fM", this.name, (size()/1024.0/1024.0)));
            this.out = this.file.getChannel().map(FileChannel.MapMode.PRIVATE, 0, this.size);
            //System.out.println(String.format("direct: %s, order: %s", this.out.isDirect(), this.out.order()));
            //System.out.println("");
            this.out.order(ByteOrder.nativeOrder());
        }		
        catch (Exception e)
        {
            this.file = null;
            this.out = null;
            System.out.println(e);
        }
    }

    public void close()
    {
        try
        {
            this.file.close();
            this.file = null;
        }		
        catch (IOException e)
        {
            System.out.println(e);
        }
    }
    
    public int size()
    {
        return this.size;
    }

    public void print(int i)
    {
        System.out.print("00000000:");
        System.out.print(" ");
        System.out.print(HEX.toHexDigits(this.out.get((i*16)+0)));
        System.out.print(HEX.toHexDigits(this.out.get((i*16)+1)));
        System.out.print(" ");
        System.out.print(HEX.toHexDigits(this.out.get((i*16)+2)));
        System.out.print(HEX.toHexDigits(this.out.get((i*16)+3)));
        System.out.print(" ");
        System.out.print(HEX.toHexDigits(this.out.get((i*16)+4)));
        System.out.print(HEX.toHexDigits(this.out.get((i*16)+5)));
        System.out.print(" ");
        System.out.print(HEX.toHexDigits(this.out.get((i*16)+6)));
        System.out.print(HEX.toHexDigits(this.out.get((i*16)+7)));
        System.out.print(" ");
        System.out.print(HEX.toHexDigits(this.out.get((i*16)+8)));
        System.out.print(HEX.toHexDigits(this.out.get((i*16)+9)));
        System.out.print(" ");
        System.out.print(HEX.toHexDigits(this.out.get((i*16)+10)));
        System.out.print(HEX.toHexDigits(this.out.get((i*16)+11)));
        System.out.print(" ");
        System.out.print(HEX.toHexDigits(this.out.get((i*16)+12)));
        System.out.print(HEX.toHexDigits(this.out.get((i*16)+13)));
        System.out.print(" ");
        System.out.print(HEX.toHexDigits(this.out.get((i*16)+14)));
        System.out.print(HEX.toHexDigits(this.out.get((i*16)+15)));
        System.out.print(" ");
        System.out.print(" ");
        for (int j = (i*16); j < ((i*16)+16); j++)
        {
            if ((this.out.get(j) >= 32) && (this.out.get(j) < 128))
            {
                System.out.print((char)this.out.get(j));
            }
            else
            {
                System.out.print('.');
            }
        }
        System.out.println();
    }
}

package cs576;

import sun.awt.windows.WingDings;

import java.io.DataInput;
import java.io.IOException;

import static cs576.VideoEncoder.MACROBLOCK_LENGTH;

/**
 * Created by Jeffreye on 4/2/2017.
 */
public class Macroblock {

    private byte layer;
    private int x;
    private int y;
    private int index;
    private Interframe frame;

    public Macroblock(Interframe frame, int x, int y, int blockIndex) {
        this.frame = frame;
        this.x = x;
        this.y = y;
        this.index = blockIndex;
    }

    public Macroblock(Interframe frame, int x, int y, int index, byte layer) {
        this.frame = frame;
        this.x = x;
        this.y = y;
        this.index = index;

        this.layer = layer;
    }

    public int getLayer() {
        return layer & 0xFF;
    }

    public void setLayer(int layer) {
        this.layer = (byte) layer;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getBlockIndex() {
        return index;
    }

}

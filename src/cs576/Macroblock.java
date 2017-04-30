package cs576;

import sun.awt.windows.WingDings;

import java.io.DataInput;
import java.io.IOException;
import java.io.PrintStream;

import static cs576.VideoEncoder.MACROBLOCK_LENGTH;

/**
 * Created by Jeffreye on 4/2/2017.
 */
public class Macroblock {

    public static final int BACKGROUND_LAYER = 0;

    private int layer;
    private int x;
    private int y;
    private int index;
    private Frame frame;

    private MotionVector motionVector;

    public Macroblock(Frame frame, int x, int y, int blockIndex) {
        this.frame = frame;
        this.x = x;
        this.y = y;
        this.index = blockIndex;
//        this.motionVector = new MotionVector(0,0);
        this.motionVector = null;
    }

    public int getLayer() {
        return layer;
    }

    public boolean isBackgroundLayer(){
        return layer == BACKGROUND_LAYER;
    }

    public void setLayer(int layer) {
        this.layer = layer;
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

    public MotionVector getMotionVector() {
        return motionVector;
    }

    public void setMotionVector(MotionVector motionVector) {
        this.motionVector = motionVector;
    }
}

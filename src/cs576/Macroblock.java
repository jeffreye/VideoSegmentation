package cs576;

import static java.lang.Math.pow;

/**
 * Created by Jeffreye on 4/2/2017.
 */
public class Macroblock {

    static final int MACROBLOCK_LENGTH = 16;

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
        this.motionVector = null;
    }

    public int dist2(int x, int y){
        return (int)(pow(this.x-x,2) + pow(this.y-y,2));
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

package cs576;

import java.io.OutputStream;

/**
 * Created by Jeffreye on 4/1/2017.
 */
public class Interframe implements Frame {

    private Macroblock[] macroblocks;
    private int macroblockWidth;
    private int macroblockHeight;

    public Interframe(int macroblockHeight, int macroblockWidth) {
        this.macroblockHeight = macroblockHeight;
        this.macroblockWidth = macroblockWidth;
        this.macroblocks = new Macroblock[macroblockWidth * macroblockHeight];
    }

    public Macroblock getBlock(int x, int y){
        int index = y * getMacroblockWidth() + x;
        Macroblock result = macroblocks[index];
        if (result == null){
            result = new Macroblock(x,y,index);
            macroblocks[index] = result;
        }
        return result;
    }

    public Macroblock[] getBlocks(){
        return this.macroblocks;
    }

    @Override
    public void serialize(OutputStream os) throws java.io.IOException{


        // compute the DCT values for each blocks
        // NO zigzag order, error frame either(perhaps)

        // save them to files

        /**
         * File format:
         * block_type R(coeff1 .. coeff64) G(coeff1 .. coeff64) B(coeff1 .. coeff64)
         * ...
         */

        byte[] buffer = new byte[VideoEncoder.blocksize * VideoEncoder.blocksize * 3];
        for (Macroblock mc : this.macroblocks){
            mc.calculateDCT(buffer);
            os.write(mc.getLayer());
            os.write(buffer);
        }
    }

    public int getMacroblockWidth() {
        return macroblockWidth;
    }

    public int getMacroblockHeight() {
        return macroblockHeight;
    }
}

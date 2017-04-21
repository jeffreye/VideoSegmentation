package cs576;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static cs576.Utils.*;
import static cs576.VideoEncoder.MACROBLOCK_LENGTH;

/**
 * Created by Jeffreye on 4/1/2017.
 */
public class Interframe extends Frame {
    private byte[][] acValues;
    private int[] dcValues;

    public Interframe(byte[] imageBuffer, int height, int width) {
        super(imageBuffer,height,width);

        this.acValues = new byte[getDctValueSize(height, width)][];
        this.dcValues = new int[acValues.length];
        calculateDCTValues(imageY, imageU, imageV, height, width, acValues, dcValues);

        // Reconstruct for next frame
        calculateImage(acValues,dcValues,height,width,imageY, imageU, imageV);
    }


    public Interframe(int height, int width, DataInput inputStream) throws IOException {
        super(height,width);

        int blockIndex = 0;
        for (int i = 0; i < height; i += MACROBLOCK_LENGTH) {
            for (int j = 0; j < width; j += MACROBLOCK_LENGTH) {
                macroblocks[blockIndex].setLayer(inputStream.readInt());
                blockIndex++;
            }
        }

        this.acValues = new byte[getDctValueSize(height, width)][];
        this.dcValues = new int[acValues.length];
        for (int i = 0; i < acValues.length; i++) {
            dcValues[i] = inputStream.readInt();
            inputStream.readFully(acValues[i] = new byte[64]);
        }
        // Done reading


        calculateImage(acValues, dcValues, height, width, this.imageY, this.imageU, this.imageV);
    }

    @Override
    public int getFrameType() {
        return INTERFRAME;
    }

    @Override
    public void serialize(DataOutput os) throws java.io.IOException {
        // save them to files

        // Serialize all marcobloks' layers
        for (Macroblock mc : this.macroblocks) {
            os.writeInt(mc.getLayer());
        }

        // Serialize "JPEG" Image
        for (int i = 0; i < acValues.length; i++) {
            os.writeInt(dcValues[i]);
            os.write(acValues[i]);
        }
    }

    @Override
    public byte[] getRawImage() {
        return convertToRGB(imageY, imageU, imageV);
    }
}

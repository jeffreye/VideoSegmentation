package cs576;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static cs576.Utils.*;
import static cs576.VideoEncoder.MACROBLOCK_LENGTH;

/**
 * Created by Jeffreye on 4/15/2017.
 */
public class PredictiveFrame extends Frame {
    HashMap<Macroblock, MotionVector> motionVectors;
    private Frame referenceFrame;

    private byte[][] errorAcValues;
    private int[] errorDcValues;

    public PredictiveFrame(Frame referenceFrame, byte[] imageBuffer, int searchRange) {
        super(imageBuffer, referenceFrame.height, referenceFrame.width);

        this.motionVectors = new HashMap<>();
        this.referenceFrame = referenceFrame;

        computeMotionVectors(referenceFrame, searchRange, imageY, height, width);
        computeErrorFrame();
        groupRegions();
    }

    public PredictiveFrame(Frame referenceFrame, DataInput inputStream) throws IOException {
        super(referenceFrame.height, referenceFrame.width);

        int blockIndex = 0;
        for (int i = 0; i < height; i += MACROBLOCK_LENGTH) {
            for (int j = 0; j < width; j += MACROBLOCK_LENGTH) {
                macroblocks[blockIndex].setLayer(inputStream.readInt());
                blockIndex++;
            }
        }

        this.referenceFrame = referenceFrame;
        int size = inputStream.readInt();
        this.motionVectors = new HashMap<>(size);

        for (int i = 0; i < size; i++) {
            int index = inputStream.readInt();
            int x = inputStream.readInt();
            int y = inputStream.readInt();

            this.motionVectors.put(
                    this.referenceFrame.getBlock(index),
                    new MotionVector(x, y)
            );
        }

        this.errorAcValues = new byte[getDctValueSize(referenceFrame.height, referenceFrame.width)][];
        this.errorDcValues = new int[errorAcValues.length];
        for (int i = 0; i < errorAcValues.length; i++) {
            errorDcValues[i] = inputStream.readInt();
            inputStream.readFully(errorAcValues[i] = new byte[64]);
        }
        // Done reading


        float[][] errorImageY = new float[height][width];
        float[][] errorImageU = new float[height][width];
        float[][] errorImageV = new float[height][width];
        calculateImage(
                this.errorAcValues, this.errorDcValues, referenceFrame.height, referenceFrame.width,
                errorImageY, errorImageU, errorImageV);


        reconstruct(imageY, imageU, imageV, motionVectors, height, width, referenceFrame);

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                imageY[i][j] += errorImageY[i][j];
                imageU[i][j] += errorImageU[i][j];
                imageV[i][j] += errorImageV[i][j];
            }
        }

    }

    /**
     * Compute motion vectors using block based SAD(sum of absolute difference brute search)
     * or fast motion estimation(FME), and then compute error frame
     * @param previous
     * @param k
     * @param currentY
     * @param height
     * @param width
     */
    private void computeMotionVectors(Frame previous, int k, float[][] currentY, int height, int width) {
        float[][] previousY = previous.imageY;
        for (Macroblock b1 : previous.getBlocks()) {
            float min = Float.MAX_VALUE;
            int deltaX = 0, deltaY = 0;
            for (int i = -k; i <= k; i++) {
                for (int j = -k; j <= k; j++) {

                    int left = b1.getX() + i;
                    int right = left + VideoEncoder.MACROBLOCK_LENGTH - 1;

                    int top = b1.getY() + j;
                    int bottom = top + VideoEncoder.MACROBLOCK_LENGTH - 1;

                    if (left < 0 || top < 0 || bottom >= height || right >= width)
                        continue;

                    // do macroblock searches only using data of one component, which should be the Y component
                    float sum = 0;
                    for (int y = top; y <= bottom; y++) {
                        for (int x = left; x <= right; x++) {
                            sum += Math.abs(previousY[y][x] - currentY[y][x]);
                        }
                    }

                    if (sum < min) {
                        deltaX = i;
                        deltaY = j;
                        min = sum;
                    }
                }
            }

            if (min == Float.MAX_VALUE) {
                // TODO: no matching block
                System.err.println("no matching block found");
            } else {
                motionVectors.put(b1, new MotionVector(deltaX, deltaY));
            }

        }
    }

    private void computeErrorFrame() {
        // A blank image
        float[][] reconstructedImageY = new float[height][width];
        float[][] reconstructedImageU = new float[height][width];
        float[][] reconstructedImageV = new float[height][width];

        reconstruct(reconstructedImageY, reconstructedImageU, reconstructedImageV, motionVectors, height, width, referenceFrame);


        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                reconstructedImageY[i][j] = imageY[i][j] - reconstructedImageY[i][j];
                reconstructedImageU[i][j] = imageU[i][j] - reconstructedImageU[i][j];
                reconstructedImageV[i][j] = imageV[i][j] - reconstructedImageV[i][j];
            }
        }

        this.errorAcValues = new byte[getDctValueSize(height, width)][];
        this.errorDcValues = new int[errorAcValues.length];

        calculateDCTValues(reconstructedImageY, reconstructedImageU, reconstructedImageV, height, width, errorAcValues, errorDcValues);
    }

    /**
     * Reconstruct last frame
     * @param reconstructedImageY
     * @param reconstructedImageU
     * @param reconstructedImageV
     * @param motionVectors
     * @param height
     * @param width
     * @param referenceFrame
     */
    private static void reconstruct(float[][] reconstructedImageY, float[][] reconstructedImageU, float[][] reconstructedImageV, HashMap<Macroblock, MotionVector> motionVectors, int height, int width, Frame referenceFrame) {
        // TODO: reconstruct by using decompressed last frame

        for (Map.Entry<Macroblock, MotionVector> kvp : motionVectors.entrySet()) {
            Macroblock mc = kvp.getKey();
            int originalX = mc.getX();
            int originalY = mc.getY();
            int x = originalX + kvp.getValue().x;
            int y = originalY + kvp.getValue().y;

            // Fill pixels from macroblocks
            for (int i = 0; i < VideoEncoder.MACROBLOCK_LENGTH; i++) {
                for (int j = 0; j < VideoEncoder.MACROBLOCK_LENGTH; j++) {
                    if (i + y >= height || j + x >= width || originalY + i >= height || originalX + j >= width) {
                        continue;
                    } else {
                        reconstructedImageY[y + i][x + j] = referenceFrame.imageY[originalY + i][originalX + j];
                        reconstructedImageU[y + i][x + j] = referenceFrame.imageU[originalY + i][originalX + j];
                        reconstructedImageV[y + i][x + j] = referenceFrame.imageV[originalY + i][originalX + j];
                    }
                }
            }
        }
    }

    /**
     * Assign all macroblocks a layer index
     */
    public void groupRegions() {
        /**
         * •Contiguous or adjacent
         •The motion vectors are all consistent – important!
         The consistency of the motion vector direction gives you an indication
         that all the macroblocks probably belong to the same object and are moving in a certain direction
         */
//
//        for (Frame f : frames.values()) {
//            if (f.getFrameType() != Frame.PREDICTIVEFRAME)
//                continue;
//            PredictiveFrame frame = (PredictiveFrame) f;
//            HashMap<Integer,MotionVector> layers = new HashMap<>();
//            layers.put(0,new MotionVector(0,0)); // background
//
//            for (Map.Entry<Macroblock, MotionVector> entry : frame.motionVectors.entrySet()) {
//
//                double angle = entry.getValue().toAngle();
//                boolean found = false;
//
//                // Find a layer with similar motion vector
//                for (Map.Entry<Integer, MotionVector> layer_direction : layers.entrySet()){
//                    if (layer_direction.getValue().toAngle() - angle <= 10){
//
//                        entry.getKey().setLayer(layer_direction.getKey());
//                        found = true;
//                        break;
//                    }
//
//                }
//
//                // Create a new layer
//                if (!found){
//                    layers.put(layers.size(), entry.getValue());
//                }
//            }
//        }
    }

    @Override
    public int getFrameType() {
        return PREDICTIVEFRAME;
    }

    @Override
    public void serialize(DataOutput os) throws java.io.IOException {
        // serialize motion vectors


        // Serialize all marcobloks' layers
        for (Macroblock mc : this.macroblocks) {
            os.writeInt(mc.getLayer());
        }

        // Serialize motion vectors
        os.writeInt(motionVectors.size());
        for (Map.Entry<Macroblock, MotionVector> kvp : motionVectors.entrySet()) {

            os.writeInt(kvp.getKey().getBlockIndex());

            MotionVector v = kvp.getValue();
            os.writeInt(v.x);
            os.writeInt(v.y);
        }

        // Serialize error "JPEG" Image
        for (int i = 0; i < errorAcValues.length; i++) {
            os.writeInt(errorDcValues[i]);
            os.write(errorAcValues[i]);
        }
    }

}

package cs576;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static cs576.Utils.*;

/**
 * Created by Jeffreye on 4/15/2017.
 */
public class PredictiveFrame implements Frame {
    private HashMap<Macroblock, MotionVector> motionVectors;
    private Interframe referenceFrame;
    private byte[][] errorAcValues;
    private int[] errorDcValues;

    // Error frame
    /***
     * Error Image (Y Channel)
     */
    float[][] errorImageY;

    /***
     * Error Image (U Channel)
     */
    float[][] errorImageU;

    /***
     * Error Image (V Channel)
     */
    float[][] errorImageV;

    /***
     * Image (Y Channel)
     */
    float[][] imageY;

    /***
     * Image (U Channel)
     */
    float[][] imageU;

    /***
     * Image (V Channel)
     */
    float[][] imageV;

    public PredictiveFrame(Interframe reference, byte[] imageBuffer, int range) {
        this.motionVectors = new HashMap<>();
        this.referenceFrame = reference;
        int height = referenceFrame.height;
        int width = referenceFrame.width;

        this.imageY = new float[height][width];
        this.imageU = new float[height][width];
        this.imageV = new float[height][width];
        for (int k = 0; k < height; k++) {
            for (int l = 0; l < width; l++) {
                int pixelIndex = (k) * width + l;
                int r = imageBuffer[pixelIndex] & 0xFF;
                int g = imageBuffer[pixelIndex + height * width] & 0xFF;
                int b = imageBuffer[pixelIndex + height * width * 2] & 0xFF;

                imageY[k][l] = 0.2990f * r + 0.5870f * g + 0.1140f * b;
                imageU[k][l] = 128f - 0.1687f * r + 0.3313f * g + 0.5000f * b;
                imageV[k][l] = 128f + 0.5000f * r + 0.418688f * g + 0.081312f * b;
            }
        }

        computeMotionVectors(reference, range, imageY, height, width);
        computeErrorFrame();

        imageY = null;
        imageU = null;
        imageV = null;
        errorImageY = null;
        errorImageU = null;
        errorImageV = null;
    }

    public PredictiveFrame(Interframe referenceFrame, DataInput inputStream) throws IOException {
        int height = referenceFrame.height;
        int width = referenceFrame.width;
        this.referenceFrame = referenceFrame;
        int size = inputStream.readInt();
        this.motionVectors = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            int blockIndex = inputStream.readInt();
            int x = inputStream.readInt();
            int y = inputStream.readInt();

            this.motionVectors.put(
                    this.referenceFrame.getBlock(blockIndex),
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


        this.errorImageY = new float[height][width];
        this.errorImageU = new float[height][width];
        this.errorImageV = new float[height][width];
        calculateImage(
                this.errorAcValues,this.errorDcValues, referenceFrame.height, referenceFrame.width,
                this.errorImageY, this.errorImageU, this.errorImageV);

        this.imageY = new float[height][width];
        this.imageU = new float[height][width];
        this.imageV = new float[height][width];
        // reconstruct last frame
        for (Map.Entry<Macroblock, MotionVector> kvp : motionVectors.entrySet()) {
            Macroblock mc = kvp.getKey();
            int x = mc.getX() + kvp.getValue().x;
            int y = mc.getY() + kvp.getValue().y;

            // Fill pixels from macroblocks
            for (int i = 0; i < VideoEncoder.MACROBLOCK_LENGTH; i++) {
                for (int j = 0; j < VideoEncoder.MACROBLOCK_LENGTH; j++) {
                    if (i + y >= referenceFrame.height || j + x >= referenceFrame.width) {
                        continue;
                    } else {
                        imageY[y + i][x + j] = errorImageY[y + i][x + j] + referenceFrame.imageY[y + i][x + j];
                        imageU[y + i][x + j] = errorImageU[y + i][x + j] + referenceFrame.imageU[y + i][x + j];
                        imageV[y + i][x + j] = errorImageV[y + i][x + j] + referenceFrame.imageV[y + i][x + j];
                    }
                }
            }
        }

        errorImageY = null;
        errorImageU = null;
        errorImageV = null;
        errorAcValues = null;
    }

    /**
     * Compute motion vectors using block based SAD(sum of absolute difference brute search)
     * or fast motion estimation(FME), and then compute error frame
     */
    private void computeMotionVectors(Interframe previous, int k, float[][] currentY, int height, int width) {
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
        int height = referenceFrame.height;
        int width = referenceFrame.width;

        // A blank image
        float[][] reconstructedImageY = new float[height][width];
        float[][] reconstructedImageU = new float[height][width];
        float[][] reconstructedImageV = new float[height][width];

        // reconstruct last frame
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

        // A blank image
        this.errorImageY = new float[height][width];
        this.errorImageU = new float[height][width];
        this.errorImageV = new float[height][width];

        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                errorImageY[i][j] = imageY[i][j] - reconstructedImageY[i][j];
                errorImageU[i][j] = imageU[i][j] - reconstructedImageU[i][j];
                errorImageV[i][j] = imageV[i][j] - reconstructedImageV[i][j];
            }
        }

        this.errorAcValues = new byte[getDctValueSize(height, width)][];
        this.errorDcValues = new int[errorAcValues.length];

        calculateDCTValues(errorImageY, errorImageU, errorImageV, height, width, errorAcValues,errorDcValues);
    }

    @Override
    public int getFrameType() {
        return PREDICTIVEFRAME;
    }

    @Override
    public void serialize(DataOutput os) throws java.io.IOException {
        // serialize motion vectors

        os.writeInt(motionVectors.size());
        for (Map.Entry<Macroblock, MotionVector> kvp : motionVectors.entrySet()) {

            os.writeInt(kvp.getKey().getBlockIndex());

            MotionVector v = kvp.getValue();
            os.writeInt(v.x);
            os.writeInt(v.y);
        }
        for (int i = 0; i < errorAcValues.length; i++) {
            os.writeInt(errorDcValues[i]);
            os.write(errorAcValues[i]);
        }
    }

    @Override
    public byte[] getRawImage() {
        return convertToRawImage(imageY, imageU, imageV);
    }
}

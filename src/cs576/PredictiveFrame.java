package cs576;

import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Jeffreye on 4/15/2017.
 */
public class PredictiveFrame implements Frame {
    private HashMap<Macroblock, MotionVector> motionVectors;
    private Interframe refrenceFrame;
    // Error frame

    public PredictiveFrame(Interframe reference, byte[] buffer, int range, int height, int width) {
        motionVectors = new HashMap<>();
        refrenceFrame = reference;

        byte[] Ys = new byte[buffer.length / 3];
        // Convert all RGB to YUV first
        int ind = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

                byte r = buffer[ind];
                byte g = buffer[ind + height * width];
                byte b = buffer[ind + height * width * 2];

                Ys[ind] = (byte) Utils.convertToY(r, g, b);
                ind++;
            }
        }

        computeMotionVectors(reference, range, Ys, height, width);
    }

    /**
     * Compute motion vectors using block based SAD(sum of absolute difference brute search)
     * or fast motion estimation(FME), and then compute error frame
     *
     * @param previous
     * @param current
     */
    private void computeMotionVectors(Interframe previous, int k, byte[] current, int height, int width) {
        for (Macroblock b1 : previous.getBlocks()) {
            byte[] Ys = b1.getComponentY();

            int min = Integer.MAX_VALUE;
            int deltaX = 0, deltaY = 0;
            for (int i = -k; i <= k; i++) {
                for (int j = -k; j <= k; j++) {

                    int left = b1.getX() + i;
                    int right = left + VideoEncoder.blocksize - 1;

                    int top = b1.getY() + j;
                    int bottom = top + +VideoEncoder.blocksize - 1;

                    if (left < 0 || top < 0 || bottom >= height || right >= width)
                        continue;

                    // do macroblock searches only using data of one component, which should be the Y component
                    int index = 0;
                    int sum = 0;
                    for (int y = top; y <= bottom; y++) {
                        for (int x = left; x <= right; x++) {
                            sum += Math.abs((Ys[index++] & 0xff) - (current[y * width + x] & 0xff));
                        }
                    }

                    if (sum < min) {
                        deltaX = i;
                        deltaY = j;
                        min = sum;
                    }
                }
            }

            if (min == Integer.MAX_VALUE) {
                // TODO: no matching block
                System.err.println("no matching block found");
            } else {
                motionVectors.put(b1, new MotionVector(deltaX, deltaY));
            }

        }
    }


    @Override
    public void serialize(OutputStream os) throws java.io.IOException {
        // serialize motion vectors
        // then compute error frame (reconstruct last frame from compressed data)

        for (Map.Entry<Macroblock, MotionVector> kvp : motionVectors.entrySet()) {
            os.write(kvp.getKey().getBlockIndex());
            MotionVector v = kvp.getValue();
            os.write(v.x);
            os.write(v.y);
        }
    }
}

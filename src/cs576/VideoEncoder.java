package cs576;

import java.io.*;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Created by Jeffreye on 4/1/2017.
 */
public class VideoEncoder {

    static final int MACROBLOCK_LENGTH = 16;

    private String inputFile;
    private String outputFile;
    private int width;
    private int height;
    private static final int k = 10;
    private static final int PREDICT_FRAMES = 4;

    private ConcurrentSkipListMap<Integer, Frame> frames;

    public VideoEncoder(String inputFile, String outputFile, int width, int height) throws FileNotFoundException {
        this.outputFile = outputFile;
        this.inputFile = inputFile;
        this.width = width;
        this.height = height;
        frames = new ConcurrentSkipListMap<>();
    }


    byte[] readImage(InputStream inputStream) throws IOException {

        byte[] bytes = new byte[width * height * 3];
        int offset = 0;
        int numRead = 0;
        while (offset < bytes.length && (numRead = inputStream.read(bytes, offset, bytes.length - offset)) >= 0) {
            offset += numRead;
        }
        return bytes;
    }

    public void encode() throws IOException {


        InputStream inputStream = new FileInputStream(inputFile);
        // Mark first frame as an I frame
        int frameCount = 0;
        int frameNumbers = inputStream.available() / (3 * height * width);

        DataOutputStream outputStream = new DataOutputStream(new FileOutputStream(outputFile));
        outputStream.writeInt(width);
        outputStream.writeInt(height);

        outputStream.writeInt(frameNumbers);

        Frame lastFrame = null;
        while (inputStream.available() != 0) {

            byte[] frameBuffer = readImage(inputStream);

            if (frameCount % PREDICT_FRAMES == 0) {
                lastFrame = new Interframe(frameBuffer, height, width);
            } else {
                lastFrame = new PredictiveFrame(lastFrame, frameBuffer, k);
            }

            outputStream.write(lastFrame.getFrameType());
            lastFrame.serialize(outputStream);
            outputStream.flush();

            frameCount++;
            System.out.print("\r");
            System.out.print("Frame " + Integer.toString(frameCount) + "/" + Integer.toString(frameNumbers) + " Processed.");


        }
        inputStream.close();
        outputStream.close();

        System.out.println("\rDONE");
    }


    public static void main(String[] argv) {
        if (argv.length == 1 && argv[0].endsWith(".rgb")) {
            try {
                String input = argv[0];
                String output = input.substring(0, input.length() - 4).concat(".cmp");
                VideoEncoder encoder = new VideoEncoder(input, output, 960, 540);
                encoder.encode();
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }

        } else {
            System.err.println("Invalid arguments");
            System.err.println("Only accepts file name as argument");
        }
    }
}

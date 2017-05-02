package cs576;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Created by Jeffreye on 4/1/2017.
 */
public class VideoEncoder {

    private String inputFile;
    private String outputFile;
    private int width;
    private int height;
    private static final int k = 16;

    private ByteBuffer bytes;

    public VideoEncoder(String inputFile, String outputFile, int width, int height) throws FileNotFoundException {
        this.outputFile = outputFile;
        this.inputFile = inputFile;
        this.width = width;
        this.height = height;

        bytes = ByteBuffer.allocate(height * width * 3);
    }


    byte[] readImage(FileChannel inputStream) throws IOException {

        bytes.clear();
        while (bytes.hasRemaining() && inputStream.read(bytes) >= 0) {

        }
        return bytes.array();
    }

    public void encode() throws IOException {
        final long startTime = System.nanoTime();

        FileChannel inputStream = new FileInputStream(inputFile).getChannel();
        // Mark first frame as an I frame
        int frameNumbers = (int) (inputStream.size() / (3 * height * width));

        FileChannel outputStream = new FileOutputStream(outputFile).getChannel();


        ByteBuffer buffer = ByteBuffer.allocateDirect(3 * 4);
        buffer.putInt(width);
        buffer.putInt(height);
        buffer.putInt(frameNumbers);

        buffer.flip();
        outputStream.write(buffer);

        int frameCount = 0;

        // Place a holder first
        ByteBuffer framePositions = ByteBuffer.allocateDirect(frameNumbers * 8);
        outputStream.write(framePositions);
        framePositions.clear();

        SegmentedFrame prev = new SegmentedFrame(readImage(inputStream), height, width);
        frameCount++;

        while (frameCount < frameNumbers) {
            SegmentedFrame curr = new SegmentedFrame(readImage(inputStream), height, width)
                    .computeDiff(prev, k);
            frameCount++;

            framePositions.putLong(outputStream.position());
            prev.serialize(outputStream);

            prev = curr;

            // release reference so that GC could collect them
            final int RESERVED_FRAMES = 5;
            SegmentedFrame f = curr;
            for (int i = 0; i < RESERVED_FRAMES && f != null; i++) {
                f = f.referenceFrame;
            }
            if (f != null)
                f.referenceFrame = null;

            System.out.print("\r");
            System.out.print("Frame " + Integer.toString(frameCount) + "/" + Integer.toString(frameNumbers) + " Processed.");

        }

        assert inputStream.position() == inputStream.size();

        framePositions.putLong(outputStream.position());
        prev.serialize(outputStream);

        assert !framePositions.hasRemaining();
        framePositions.flip();
        outputStream.position(12);
        outputStream.write(framePositions);

        inputStream.close();
        outputStream.close();
        System.out.println("\rDONE");

        // End timer
        final long endTime = System.nanoTime();

        System.out.printf("\rTime elapsed: %f s\n",
                (float) (endTime - startTime) / 1e9);
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

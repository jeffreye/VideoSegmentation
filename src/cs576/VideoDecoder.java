package cs576;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by Jeffreye on 4/1/2017.
 */
public class VideoDecoder implements ActionListener,MouseMotionListener {

    private static final int BATCH_WORKS = 20;
    private final String inputFile;
    private final String outputFile;
    private final int foregroundQuantizationValue;
    private final int backgroundQuantizationValue;
    private int width;
    private int height;
    private int pointerX, pointerY;
    private int videostatus;
    private int currentFrame;
    private boolean restartFlag;


    private BufferedImage image;
    private JFrame frame;
    private JLabel lbIm1;
    private JLabel lbText1;
    private JButton playpause;
    private JButton restart;
    private JSlider seekBar;
    private Queue<int[]> imgs;
    private Queue<int[]> imageBuffers;
    private Timer timer;


//    int[] rawImage;

    private boolean doneDecoding;

    public VideoDecoder(String inputFile, String outputFile, int foregroundQuantizationValue, int backgroundQuantizationValue, int fps) throws FileNotFoundException {
        this.inputFile = inputFile;
        this.outputFile = outputFile;
        this.foregroundQuantizationValue = foregroundQuantizationValue;
        this.backgroundQuantizationValue = backgroundQuantizationValue;
        this.videostatus = 1;
        this.currentFrame = 0;
        this.pointerX = 0;
        this.pointerY = 0;


        imgs = new ArrayDeque<>(10);
//        allImgs = new ArrayList<>(100);
        imageBuffers = new ArrayDeque<>(10);
//        img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        showWindow(fps);
    }


    public void showWindow(int fps) {
        frame = new JFrame();
        GridBagLayout gLayout = new GridBagLayout();
        frame.getContentPane().setLayout(gLayout);
        String result = String.format("Video height: %d, width: %d", height, width);
        lbText1 = new JLabel(result);
        lbText1.setHorizontalAlignment(SwingConstants.CENTER);
        frame.addMouseMotionListener(this);
        lbIm1 = new JLabel();

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        c.weightx = 0.5;
        c.gridx = 0;
        c.gridy = 0;
        frame.getContentPane().add(lbText1, c);


        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        c.gridx = 0;
        c.gridy = 1;
        frame.getContentPane().add(lbIm1, c);

        playpause = new JButton("Play/Pause");
        playpause.addActionListener(this);

        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        c.gridx = 0;
        c.gridy = 2;
        frame.getContentPane().add(playpause, c);

        restart = new JButton("Restart");
        restart.addActionListener(this);

        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        c.gridx = 0;
        c.gridy = 3;
        frame.getContentPane().add(restart, c);

        seekBar = new JSlider();

        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        c.gridx = 0;
        c.gridy = 4;
        frame.getContentPane().add(seekBar, c);

        frame.setTitle("Video Player");
        frame.pack();
        frame.setVisible(true);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);


        timer = new Timer(1000 / fps, e -> refreshImage());
        timer.setInitialDelay(0);
        timer.start();
    }


    private void refreshImage() {

        if (imgs.size() == 0)
            return;

        int[] buffer = imgs.remove();
        imageBuffers.add(buffer);

        image.getRaster().setDataElements(0, 0, width, height, buffer);
        lbIm1.repaint();
        currentFrame++;
        updateSlider();
    }

    public void decode() throws IOException {
        FileInputStream inputStream = new FileInputStream(this.inputFile);

        ByteBuffer buffer = ByteBuffer.allocateDirect(3 * 4);
        inputStream.getChannel().read(buffer);
        buffer.flip();

        width = buffer.getInt();
        height = buffer.getInt();
        int frameNumbers = buffer.getInt();
        seekBar.setMinimum(0);
        seekBar.setMaximum(frameNumbers-1);
        String result = String.format("Video height: %d, width: %d", height, width);
        lbText1.setText(result);

        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        lbIm1.setIcon(new ImageIcon(image));
        frame.pack();

        for (int i = 0; i < 10; i++) {
            imageBuffers.add(new int[height * width]);
        }

        decodeSync(inputStream.getChannel(), frameNumbers);

        doneDecoding = true;
        inputStream.close();
    }

    public void decodeSync(FileChannel inputStream, int frameNumbers) throws IOException {
        SegmentedFrame lastFrame = new SegmentedFrame(height, width);
        while (true) {
            // Reset to first frame


            if (inputStream.size() == inputStream.position() || restartFlag) {
                inputStream.position(4 * 3);
                currentFrame = 0;
                restartFlag = false;
            }


            // Stop buffering
            if (imageBuffers.size() == 0)
                try {
                    Thread.sleep(200);
                    continue;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

//            final long startTime = System.nanoTime();

            int[] rawImage = imageBuffers.remove();

            lastFrame.loadFrom(inputStream);
            lastFrame = lastFrame.reconstruct(foregroundQuantizationValue, backgroundQuantizationValue, pointerX, pointerY);
            lastFrame.getRawImage(rawImage);

            imgs.add(rawImage);

            // End timer
//            final long endTime = System.nanoTime();
//
//            System.out.printf("\rTime elapsed: %f ms\n",
//                    (float) (endTime - startTime) / 1e6);
        }
    }

    public void decodeAsync(DataInputStream inputStream, int frameNumbers) throws IOException {
        int frameCount = 0;
        CompletableFuture<SegmentedFrame> lastFrame = CompletableFuture.completedFuture(null);
        CompletableFuture<Void> outputFuture = CompletableFuture.completedFuture(null);
        AtomicInteger processedInteger = new AtomicInteger(0);

        FileOutputStream outputStream = new FileOutputStream(outputFile);

        while (inputStream.available() != 0) {


            if (frameCount % BATCH_WORKS == 0 && lastFrame != null) {
                while (!lastFrame.isDone()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    System.out.print("\r");
                    System.out.print("Frame " + Integer.toString(processedInteger.get()) + "/" + Integer.toString(frameNumbers) + " Processed.");

                }
            }

            CompletableFuture<SegmentedFrame> current = CompletableFuture.completedFuture(new SegmentedFrame(height, width, inputStream));
            lastFrame = lastFrame.thenCombineAsync(current, (last, curr) -> curr.reconstruct(foregroundQuantizationValue, backgroundQuantizationValue, pointerX, pointerY));

            outputFuture.thenAcceptBoth(lastFrame, (o, last) -> {
                try {
                    outputStream.write(last.getRawImage());
                    outputStream.flush();
                    processedInteger.incrementAndGet();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            frameCount++;
        }

        outputFuture.join();
        outputStream.close();
    }

    /*=============================================================================
    * ============================= Action Listeners ==============================
    * =============================================================================
    */

    public void playOrPauseVideo() {
        if(videostatus==1) {
            timer.stop();
            videostatus = 0;
        }
        else {
            timer.start();
            videostatus = 1;
        }
    }


    public void actionPerformed (ActionEvent e) {
        Object src = e.getSource();
        if (src == playpause) {
            playOrPauseVideo();
        }
        else if (src == restart) {
            restartFlag = true;
        }
    }

    public void mouseMoved(MouseEvent e) {
        this.pointerX = e.getX();
        this.pointerY = e.getY();
    }

    public void mouseDragged(MouseEvent e) {

    }

    public void updateSlider() {
        seekBar.setValue(currentFrame);
    }




    public static void main(String[] argv) {
        if (argv.length >= 2 && argv[0].endsWith(".cmp")) {
            try {
                String input = argv[0];
                String output = input.substring(0, input.length() - 4).concat(".raw");
                int quantizationForeground = Integer.parseInt(argv[1]);
                int quantizationBackground = Integer.parseInt(argv[2]);
                VideoDecoder decoder = new VideoDecoder(input, output, quantizationForeground, quantizationBackground, 30);
                decoder.decode();
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }

        } else {
            System.err.println("Invalid arguments");
            System.err.println("Only accepts file name as argument");
        }
    }
}

package overskam.projectH.video;

import javafx.scene.image.Image;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.JavaFXFrameConverter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class VideoFrameReader implements AutoCloseable {

    private static final int SEEK_WARMUP_FRAMES = 30;

    private FFmpegFrameGrabber grabber;
    private final JavaFXFrameConverter converter = new JavaFXFrameConverter();
    private final Java2DFrameConverter imageFileConverter = new Java2DFrameConverter();
    private int currentFrameIndex = -1;

    public Image openVideoAndReadFirstFrame(File videoFile) throws FrameGrabber.Exception {
        close();

        grabber = new FFmpegFrameGrabber(videoFile);
        grabber.start();

        Frame frame = grabber.grabImage();

        if (frame == null) {
            throw new FrameGrabber.Exception("Could not read first video frame");
        }

        currentFrameIndex = 0;

        return converter.convert(frame.clone());
    }

    public Image readNextFrame() throws FrameGrabber.Exception {
        if (grabber == null) {
            return null;
        }

        Frame frame = grabNextImageFrame();

        if (frame == null) {
            return null;
        }

        currentFrameIndex++;
        return converter.convert(frame.clone());
    }

    public Image readFrameAt(int frameIndex) throws FrameGrabber.Exception {
        Frame frame = grabFrameAt(frameIndex);

        if (frame == null) {
            return null;
        }

        currentFrameIndex = frameIndex;
        return converter.convert(frame.clone());
    }

    public Image readPreviousFrame() throws FrameGrabber.Exception {
        if (currentFrameIndex <= 0) {
            return null;
        }

        return readFrameAt(currentFrameIndex - 1);
    }

    public int getCurrentFrameIndex() {
        return currentFrameIndex;
    }

    public void saveFrameAsJpeg(int frameIndex, File outputFile)
            throws FrameGrabber.Exception, IOException {
        Frame frame = grabFrameAt(frameIndex);

        if (frame == null) {
            return;
        }

        BufferedImage bufferedImage = imageFileConverter.convert(frame.clone());

        if (bufferedImage == null) {
            return;
        }

        ImageIO.write(bufferedImage, "jpg", outputFile);
    }

    public Image readCurrentFrameAgain() throws FrameGrabber.Exception {
        if (grabber == null || currentFrameIndex < 0) {
            return null;
        }

        return readFrameAt(currentFrameIndex);
    }

    private Frame grabFrameAt(int frameIndex) throws FrameGrabber.Exception {
        if (grabber == null || frameIndex < 0) {
            return null;
        }

        int seekStartFrame = Math.max(0, frameIndex - SEEK_WARMUP_FRAMES);
        grabber.setVideoFrameNumber(seekStartFrame);

        Frame frame = null;

        for (int index = seekStartFrame; index <= frameIndex; index++) {
            frame = grabNextImageFrame();

            if (frame == null) {
                return null;
            }
        }

        return frame;
    }

    private Frame grabNextImageFrame() throws FrameGrabber.Exception {
        Frame frame;

        do {
            frame = grabber.grab();
        } while (frame != null && frame.image == null);

        return frame;
    }

    @Override
    public void close() throws FrameGrabber.Exception {
        if (grabber != null) {
            grabber.close();
            grabber = null;
        }

        currentFrameIndex = -1;
    }
}
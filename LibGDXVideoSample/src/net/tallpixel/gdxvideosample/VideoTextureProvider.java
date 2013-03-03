package net.tallpixel.gdxvideosample;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.xuggle.xuggler.Global;
import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IPixelFormat;
import com.xuggle.xuggler.IStream;
import com.xuggle.xuggler.IStreamCoder;
import com.xuggle.xuggler.IVideoPicture;
import com.xuggle.xuggler.IVideoResampler;
import com.xuggle.xuggler.Utils;

/**
 * A VideoTextureProvider wraps a video on the internal file system.
 * It provides methods to play, pause and stop the associated video
 * and provides a Texture object that contains the current video
 * frame
 * @author ajs
 */
public class VideoTextureProvider {
	
	public enum PlayState {
		PLAYING,
		PAUSED,
		STOPPED
	}

	private String videoPath;
	private FileHandle fileHandle;
	private InputStream inputStream;
	
	private Texture texture;
	
	private PlayState playState = PlayState.STOPPED;
	private long playTimeMilliseconds = 0;
	private IContainer container;
	
	private long firstTimestampMilliseconds = Global.NO_PTS;
	
	// The tollerance used when waiting for the playhead to catch up
	private long sleepTolleranceMilliseconds = 50;
	
	
	int videoStreamId = -1;
	IStreamCoder videoCoder = null;
	IVideoResampler resampler = null;
	IPacket packet = IPacket.make();
	
	long sleepTimeoutMilliseconds = 0;
	
	/**
	 * ctor
	 * @param _videoPath An internal LibGDX path to a video file
	 */
	public VideoTextureProvider(String _videoPath) {
		videoPath = _videoPath;
		
		// Let's make sure that we can actually convert video pixel formats.
		if (!IVideoResampler.isSupported(IVideoResampler.Feature.FEATURE_COLORSPACECONVERSION)) {
			throw new RuntimeException("VideoTextureProvider requires the GPL version of Xuggler (with IVideoResampler support)");
		}
		
		// Get a handle to the file and open it for reading
		fileHandle = Gdx.files.internal(videoPath);
		
		if(!fileHandle.exists()) {
			throw new IllegalArgumentException("Video file does not exist: " + videoPath);
		}
		
		inputStream = fileHandle.read();
		
		// Initialize the texture to a black color until the video is ready
		//texture = new Texture(Gdx.files.internal("data/black.png"));
	}

	/**
	 * Plays the video stream, or resumes it if it was paused
	 */
	public void play() {
		
		if(container == null) {
			// Create a Xuggler container object
			container = IContainer.make();
		}
		
		if(!container.isOpened()) {
			// Open up the container
			if (container.open(inputStream, null) < 0) {
				throw new RuntimeException("Could not open video file: " + videoPath);
			}
	
			// Query how many streams the call to open found
			int numStreams = container.getNumStreams();
	
			// Iterate through the streams to find the first video stream
			for (int i = 0; i < numStreams; i++) {
				// Find the stream object
				IStream stream = container.getStream(i);
				
				// Get the pre-configured decoder that can decode this stream;
				IStreamCoder coder = stream.getStreamCoder();
				
				if (coder.getCodecType() == ICodec.Type.CODEC_TYPE_VIDEO) {
					videoStreamId = i;
					videoCoder = coder;
					break;
				}
			}
			
			if (videoStreamId == -1) {
				throw new RuntimeException("Could not find video stream in container: " + videoPath);
			}
			
			/* Now we have found the video stream in this file. Let's open up our
			 * decoder so it can do work
			 */
			if (videoCoder.open() < 0) {
				throw new RuntimeException("Could not open video decoder for container: " + videoPath);
			}
			
			if (videoCoder.getPixelType() != IPixelFormat.Type.BGR24) {
				// If this stream is not in BGR24, we're going to need to convert it
				resampler = IVideoResampler.make(
					videoCoder.getWidth(),
					videoCoder.getHeight(),
					IPixelFormat.Type.BGR24,
					videoCoder.getWidth(),
					videoCoder.getHeight(),
					videoCoder.getPixelType()
				);
				
				if (resampler == null) {
					throw new RuntimeException("Could not create color space resampler");
				}
			}
			
			/* Query the first timestamp in the stream
			 * Timestamps are in microseconds - convert to milli
			 */
			firstTimestampMilliseconds = container.getStartTime() / 1000;
		}
		
		playState = PlayState.PLAYING;
	}
	
	/**
	 * Pauses the video stream, allowing it to be resumed later
	 * with play()
	 */
	public void pause() {
		playState = PlayState.PAUSED;
	}
	
	/**
	 * Stops the video stream, resetting the play head to the
	 * beginning of the stream
	 */
	public void stop() {
		container.close();
		container = null;
		
		try {
			inputStream.close();
		} catch (IOException e) {}
		inputStream = fileHandle.read();
		
		playTimeMilliseconds = 0;
		
		// Initialize the texture to a black color until it is next played
		texture = new Texture(Gdx.files.internal("data/black.png"));
		
		playState = PlayState.STOPPED;
	}
	
	public PlayState getState() {
		return playState;
	}
	
	/**
	 * Updates the video play head
	 * @param dtSeconds The elapsed time since the last call to update(),
	 * 	in seconds
	 * @return True if the video texture has changed, false otherwise
	 */
	public boolean update(float dtSeconds) {
		if(playState != PlayState.PLAYING) return false;
		
		long dtMilliseconds = (long)(dtSeconds * 1000);
		playTimeMilliseconds += dtMilliseconds;
		
		sleepTimeoutMilliseconds = (long) Math.max(0, sleepTimeoutMilliseconds - dtMilliseconds);
		if(sleepTimeoutMilliseconds > 0) {
			// The playhead is still ahead of the current frame - do nothing
			return false;
		}
		
		while(true) {
			int packet_read_result = container.readNextPacket(packet);
			
			if(packet_read_result < 0) {
				// Got bad packet - we've reached end of the video stream
				stop();
				return true;
			}
			
			if(packet.getStreamIndex() == videoStreamId) {
				// We have a valid packet from our stream
				
				// Allocate a new picture to get the data out of Xuggler
				IVideoPicture picture = IVideoPicture.make(
					videoCoder.getPixelType(),
					videoCoder.getWidth(),
					videoCoder.getHeight()
				);
				
				// Attempt to read the entire packet
				int offset = 0;
				while(offset < packet.getSize()) {
					// Decode the video, checking for any errors
					int bytesDecoded = videoCoder.decodeVideo(picture, packet, offset);
					if (bytesDecoded < 0) {
						throw new RuntimeException("Got error decoding video");
					}
					offset += bytesDecoded;

					/* Some decoders will consume data in a packet, but will not
					 * be able to construct a full video picture yet. Therefore
					 * you should always check if you got a complete picture
					 * from the decoder
					 */
					if (picture.isComplete()) {
						// We've read the entire packet
						IVideoPicture newPic = picture;
						
						// Timestamps are stored in microseconds - convert to milli
						long absoluteFrameTimestampMilliseconds = picture.getTimeStamp() / 1000;
						long relativeFrameTimestampMilliseconds = (absoluteFrameTimestampMilliseconds - firstTimestampMilliseconds);
						long frameTimeDelta = relativeFrameTimestampMilliseconds - playTimeMilliseconds;
						
						if(frameTimeDelta > 0) {
							// The video is ahead of the playhead, don't read any more frames until it catches up
							sleepTimeoutMilliseconds = frameTimeDelta + sleepTolleranceMilliseconds;
							return false;
						}
						
						/* If the resampler is not null, that means we didn't get the video in
						 * BGR24 format and need to convert it into BGR24 format
						 */
						if (resampler != null) {
							// Resample the frame
							newPic = IVideoPicture.make(
								resampler.getOutputPixelFormat(),
								picture.getWidth(), picture.getHeight()
							);
							
							if (resampler.resample(newPic, picture) < 0) {
								throw new RuntimeException("Could not resample video");
							}
						}
						
						if (newPic.getPixelType() != IPixelFormat.Type.BGR24) {
							throw new RuntimeException("Could not decode video" + " as BGR 24 bit data");
						}

						// And finally, convert the BGR24 to an Java buffered image
						BufferedImage javaImage = Utils.videoPictureToImage(newPic);
						
						// Update the current texture
						updateTexture(javaImage);
						
						// Let the caller know the texture has changed
						return true;
					}
				}
			}
		}
	}
	
	/**
	 * Updates the internal texture with new video data
	 * @param img The new video frame data
	 */
	private void updateTexture(BufferedImage img) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		
		try {
			ImageIO.write(img, "bmp", baos);
			baos.flush();
			byte[] bytes = baos.toByteArray();
			baos.close();
			
			Pixmap pix = new Pixmap(bytes, 0, bytes.length);
			texture = new Texture(pix);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	/**
	 * Gets the video texture
	 * @return The video texture, containing the current video frame
	 */
	public Texture getTexture() {
		return texture;
	}
	
	/**
	 * Permanently disposes of all objects
	 */
	public void dispose() {
		if(inputStream != null) {
			try {
				inputStream.close();
			} catch(Exception e) {}
			inputStream = null;
		}

		if(texture != null) {
			texture.dispose();
			texture = null;
		}
		
		if (videoCoder != null) {
			videoCoder.close();
			videoCoder = null;
		}
		
		if (container != null) {
			container.close();
			container = null;
		}
	}

}

package net.tallpixel.gdxvideosample;

import net.tallpixel.gdxvideosample.VideoTextureProvider.PlayState;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.GL10;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * This sample demonstrates loading a video via Xuggler and rendering it
 * to a LibGDX texture.
 * 
 * Controls: Touch to play / pause, Enter to stop.
 * 
 * @author ajs
 */
public class VideoSample implements ApplicationListener, InputProcessor {
	
	private OrthographicCamera camera;
	private SpriteBatch batch;
	private VideoTextureProvider textureProvider;
	private Sprite sprite;
	
	private String video_path = "data/big_buck_bunny_trailer_400p.ogg";
	
	@Override
	public void create() {		
		float w = Gdx.graphics.getWidth();
		float h = Gdx.graphics.getHeight();
		
		camera = new OrthographicCamera(1, h/w);
		batch = new SpriteBatch();
		
		Gdx.input.setInputProcessor(this);
		
		textureProvider = new VideoTextureProvider(video_path);
		textureProvider.play();
	}

	@Override
	public void dispose() {
		batch.dispose();
		textureProvider.dispose();
	}

	@Override
	public void render() {
		float dt = Gdx.graphics.getDeltaTime();
		
		Gdx.gl.glClearColor(1, 1, 1, 1);
		Gdx.gl.glClear(GL10.GL_COLOR_BUFFER_BIT);
		
		// If the video texture has changed, update the sprite
		if(textureProvider.update(dt)) {
			Texture tex = textureProvider.getTexture();
			
			if(sprite == null) {
				// Initialize the sprite
				sprite = new Sprite(tex);
				
				sprite.setSize(0.9f, 0.9f * sprite.getHeight() / sprite.getWidth());
				sprite.setOrigin(sprite.getWidth()/2, sprite.getHeight()/2);
				sprite.setPosition(-sprite.getWidth()/2, -sprite.getHeight()/2);
			} else {
				sprite.setTexture(tex);
			}
			
		}
		
		batch.setProjectionMatrix(camera.combined);
		batch.begin();
		sprite.draw(batch);
		batch.end();
	}

	@Override
	public void resize(int width, int height) {
	}

	@Override
	public void pause() {
	}

	@Override
	public void resume() {
	}

	@Override
	public boolean keyDown(int keycode) {
		return false;
	}

	@Override
	public boolean keyUp(int keycode) {
		
		// Stop on enter
		if(keycode == Keys.ENTER) {
			textureProvider.stop();
			sprite.setTexture(textureProvider.getTexture());
			return true;
		}
		
		return false;
	}

	@Override
	public boolean keyTyped(char character) {
		return false;
	}

	@Override
	public boolean touchDown(int screenX, int screenY, int pointer, int button) {
		return false;
	}

	@Override
	public boolean touchUp(int screenX, int screenY, int pointer, int button) {
		
		// Toggle play/pause on touch
		if(textureProvider.getState() != PlayState.PLAYING) {
			textureProvider.play();
		} else {
			textureProvider.pause();
		}
		
		return true;
	}

	@Override
	public boolean touchDragged(int screenX, int screenY, int pointer) {
		return false;
	}

	@Override
	public boolean mouseMoved(int screenX, int screenY) {
		return false;
	}

	@Override
	public boolean scrolled(int amount) {
		return false;
	}
}

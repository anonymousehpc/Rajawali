package rajawali.materials;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import rajawali.Camera;
import rajawali.Capabilities;
import rajawali.lights.ALight;
import rajawali.materials.methods.IDiffuseMethod;
import rajawali.materials.shaders.FragmentShader;
import rajawali.materials.shaders.VertexShader;
import rajawali.materials.shaders.fragments.SingleColorFragmentShaderFragment;
import rajawali.materials.shaders.fragments.SingleColorVertexShaderFragment;
import rajawali.materials.textures.ATexture;
import rajawali.materials.textures.ATexture.TextureException;
import rajawali.materials.textures.TextureManager;
import rajawali.renderer.AFrameTask;
import rajawali.renderer.RajawaliRenderer;
import rajawali.util.RajLog;
import android.opengl.GLES20;

public class Material extends AFrameTask {

	private VertexShader mVertexShader;
	private FragmentShader mFragmentShader;
	
	private IDiffuseMethod mDiffuseMethod;

	private boolean mUseSingleColor;
	private boolean mUseVertexColors;
	private boolean mIsDirty = true;

	private int mProgramHandle = -1;
	private int mVShaderHandle;
	private int mFShaderHandle;

	private float[] mModelMatrix;
	private float[] mViewMatrix;

	protected Stack<ALight> mLights;
	
	private int mColor = 0xffffff;

	/**
	 * This texture's unique owner identity String. This is usually the fully qualified name of the
	 * {@link RajawaliRenderer} instance.
	 */
	protected String mOwnerIdentity;
	/**
	 * The maximum number of available textures for this device.
	 */
	private int mMaxTextures;
	protected ArrayList<ATexture> mTextureList;

	public Material()
	{
		mTextureList = new ArrayList<ATexture>();
		mLights = new Stack<ALight>();
	}

	public void useSingleColor(boolean value)
	{
		if (value != mUseSingleColor)
		{
			mIsDirty = true;
			mUseSingleColor = value;
		}
	}

	public boolean usingSingleColor()
	{
		return mUseSingleColor;
	}

	public void useVertexColors(boolean value)
	{
		if (value != mUseVertexColors)
		{
			mIsDirty = true;
			mUseVertexColors = value;
		}
	}
	
	public void setColor(int color) {
		mColor = color;
		if(mVertexShader != null)
		{
			SingleColorVertexShaderFragment f = (SingleColorVertexShaderFragment)mVertexShader.getShaderFragment(SingleColorVertexShaderFragment.SHADER_ID);
			if(f == null) return;
			f.setColor(color);
		}
	}
	
	public int getColor() {
		return mColor;
	}

	public boolean usingVertexColors()
	{
		return mUseVertexColors;
	}

	void add()
	{
		createShaders();
	}

	void remove()
	{
		mModelMatrix = null;
		mViewMatrix = null;

		if (mLights != null)
			mLights.clear();
		if (mTextureList != null)
			mTextureList.clear();

		if (RajawaliRenderer.hasGLContext()) {
			GLES20.glDeleteShader(mVShaderHandle);
			GLES20.glDeleteShader(mFShaderHandle);
			GLES20.glDeleteProgram(mProgramHandle);
		}
	}

	void reload()
	{
		mIsDirty = true;
		createShaders();
	}

	public void createShaders()
	{
		if (!mIsDirty)
			return;
		
		mMaxTextures = Capabilities.getInstance().getMaxTextureImageUnits();

		mVertexShader = new VertexShader();
		mFragmentShader = new FragmentShader();

		if (mUseSingleColor)
		{
			SingleColorVertexShaderFragment svs = new SingleColorVertexShaderFragment();
			svs.setColor(mColor);
			mVertexShader.addShaderFragment(svs);
			mFragmentShader.addShaderFragment(new SingleColorFragmentShaderFragment());
		}

		mVertexShader.buildShader();
		mFragmentShader.buildShader();

		mProgramHandle = createProgram(mVertexShader.getShaderString(), mFragmentShader.getShaderString());
		if (mProgramHandle == 0)
			return;

		for(int i=0; i<mTextureList.size(); i++)
			setTextureParameters(mTextureList.get(i));
		
		mVertexShader.setLocations(mProgramHandle);
		mFragmentShader.setLocations(mProgramHandle);
		
		//RajLog.i(mVertexShader.getShaderString());
		//RajLog.d(mFragmentShader.getShaderString());

		mIsDirty = false;
	}

	protected int loadShader(int shaderType, String source) {
		int shader = GLES20.glCreateShader(shaderType);
		if (shader != 0) {
			GLES20.glShaderSource(shader, source);
			GLES20.glCompileShader(shader);
			int[] compiled = new int[1];
			GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
			if (compiled[0] == 0) {
				RajLog.e("[" + getClass().getName() + "] Could not compile "
						+ (shaderType == GLES20.GL_FRAGMENT_SHADER ? "fragment" : "vertex") + " shader:");
				RajLog.e("Shader log: " + GLES20.glGetShaderInfoLog(shader));
				GLES20.glDeleteShader(shader);
				shader = 0;
			}
		}
		return shader;
	}

	protected int createProgram(String vertexSource, String fragmentSource) {
		mVShaderHandle = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
		if (mVShaderHandle == 0) {
			return 0;
		}

		mFShaderHandle = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
		if (mFShaderHandle == 0) {
			return 0;
		}

		int program = GLES20.glCreateProgram();
		if (program != 0) {
			GLES20.glAttachShader(program, mVShaderHandle);
			GLES20.glAttachShader(program, mFShaderHandle);
			GLES20.glLinkProgram(program);

			int[] linkStatus = new int[1];
			GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
			if (linkStatus[0] != GLES20.GL_TRUE) {
				RajLog.e("Could not link program in " + getClass().getCanonicalName() + ": ");
				RajLog.e(GLES20.glGetProgramInfoLog(program));
				GLES20.glDeleteProgram(program);
				program = 0;
			}
		}
		return program;
	}

	public void useProgram()
	{
		if (mIsDirty)
		{
			createShaders();
		}

		mVertexShader.applyParams();
		mFragmentShader.applyParams();
		
		GLES20.glUseProgram(mProgramHandle);
	}
	
	private void setTextureParameters(ATexture texture) {
		if(texture.getUniformHandle() > -1) return;
		
		int textureHandle = GLES20.glGetUniformLocation(mProgramHandle, texture.getTextureName());
		if (textureHandle == -1) {
			RajLog.d("Could not get attrib location for "
					+ texture.getTextureName() + ", " + texture.getTextureType());
		}
		texture.setUniformHandle(textureHandle);
	}
	
	public void bindTextures() {
		int num = mTextureList.size();

		for (int i = 0; i < num; i++) {
			ATexture texture = mTextureList.get(i);
			GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + i);
			GLES20.glBindTexture(texture.getGLTextureType(), texture.getTextureId());
			GLES20.glUniform1i(GLES20.glGetUniformLocation(mProgramHandle, texture.getTextureName()), i);
		}
	}

	public void unbindTextures() {
		int num = mTextureList.size();

		for (int i = 0; i < num; i++) {
			ATexture texture = mTextureList.get(i);
			GLES20.glBindTexture(texture.getGLTextureType(), 0);
		}
		
		GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
	}
	
	public void addTexture(ATexture texture) throws TextureException {
		if(mTextureList.indexOf(texture) > -1) return;
		if(mTextureList.size() + 1 > mMaxTextures) {
			throw new TextureException("Maximum number of textures for this material has been reached. Maximum number of textures is " + mMaxTextures + ".");
		}
		mTextureList.add(texture);

		TextureManager.getInstance().addTexture(texture);
		texture.registerMaterial(this);
		
		if(mProgramHandle > -1)
			setTextureParameters(texture);
	}
	
	public void removeTexture(ATexture texture) {
		mTextureList.remove(texture);
		texture.unregisterMaterial(this);
	}
	
	public ArrayList<ATexture> getTextureList() {
		return mTextureList;
	}
	
	public void copyTexturesTo(AMaterial material) throws TextureException {
		int num = mTextureList.size();

		for (int i = 0; i < num; ++i)
			material.addTexture(mTextureList.get(i));
	}

	public void setVertices(final int vertexBufferHandle) {
		mVertexShader.setVertices(vertexBufferHandle);
	}

	public void setTextureCoords(int textureCoordBufferHandle) {
		setTextureCoords(textureCoordBufferHandle, false);
	}

	public void setTextureCoords(final int textureCoordBufferHandle, boolean hasCubemapTexture) {
		mVertexShader.setTextureCoords(textureCoordBufferHandle, hasCubemapTexture);
	}
	
	public void setNormals(final int normalBufferHandle) {
		mVertexShader.setNormals(normalBufferHandle);
	}

	public float[] getModelViewMatrix() {
		return mModelMatrix;
	}

	public void setMVPMatrix(float[] mvpMatrix) {
		mVertexShader.setMVPMatrix(mvpMatrix);
	}

	public void setModelMatrix(float[] modelMatrix) {
		mModelMatrix = modelMatrix;
		mVertexShader.setModelMatrix(modelMatrix);
	}

	public void setViewMatrix(float[] viewMatrix) {
		mViewMatrix = viewMatrix;
		mVertexShader.setViewMatrix(viewMatrix);
	}

	public void setLights(List<ALight> lights) {
		// TODO
	}

	public void setCamera(Camera camera) {
		// TODO
	}
	
	public void setDiffuseMethod(IDiffuseMethod diffuseMethod)
	{
		if(mDiffuseMethod == diffuseMethod) return;
		mDiffuseMethod = diffuseMethod;
		mIsDirty = true;
	}
	
	public IDiffuseMethod getDiffuseMethod()
	{
		return mDiffuseMethod;
	}

	public void setOwnerIdentity(String identity)
	{
		mOwnerIdentity = identity;
	}

	public String getOwnerIdentity()
	{
		return mOwnerIdentity;
	}

	@Override
	public TYPE getFrameTaskType() {
		return TYPE.MATERIAL;
	}
}
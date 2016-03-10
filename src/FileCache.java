package com.sunteorum.kiku.cache;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.text.TextUtils;

/**
 * 文件缓存类
 * @author KYO
 *
 */
public class FileCache implements Cacheable<File> {
	private final String CACHE_DIR_NAME = "file_cache"; //缓存目录名
	private final String CACHE_FILE_PREFIX = ""; //缓存文件名前缀
	private final String CACHE_FILE_SUFFIX = ""; //缓存文件名后缀
	private final String CHARSET = "UTF-8"; //默认字符集
	
	private Context context;
	private File cacheDir;
	private long freeSize = 1024 * 1024 * 10;
	private long maxSize = 0;
	private int hits = 0;
	
	public FileCache(Context context) {
		super();
		this.context = context;
		cacheDir = new File(context.getCacheDir(), CACHE_DIR_NAME);
		if (!cacheDir.exists()) cacheDir.mkdirs();
		maxSize = getFolderFreeSize(cacheDir) - freeSize;
	}

	/**
	 * 取得文件路径对应的键名。(用做该文件的缓存文件名)
	 * @param path 网络或本地的文件路径
	 * @return
	 */
	public String getCacheKey(String path) {
		if (TextUtils.isEmpty(path)) return null;
		String key = CACHE_FILE_PREFIX + path.hashCode() + CACHE_FILE_SUFFIX;
		return key;
	}
	
	/**
	 * 取得文件路径指向的缓存文件。 ({@link #get(String)} 会判断该文件是否存在)。
	 * @param path 网络或本地的文件路径
	 * @return
	 */
	public File getCacheFile(String path) {
		return new File(cacheDir, getCacheKey(path));
	}
	
	/**
	 * 返回当前的缓存目录
	 * @return
	 */
	public File getCacheDir() {
		return cacheDir;
	}
	
	/**
	 * 设置缓存的目录
	 * @param cacheDir 缓存目录
	 */
	public void setCacheDir(File cacheDir) {
		if (cacheDir == null) throw new NullPointerException("file must not null");
		this.cacheDir = cacheDir;
		if (!cacheDir.exists()) cacheDir.mkdirs();
		maxSize = getFolderFreeSize(cacheDir) - freeSize;
	}
	
	@Override
	public boolean contains(String key) {
		File f = getCacheFile(key);
		return f.exists();
	}

	@Override
	public void put(String key, Object value) {
		File f = getCacheFile(key);
		if (value instanceof File)
			fileChannelCopy((File) value, f);
		else if (value instanceof Bitmap)
			putBitmap(key, (Bitmap) value);
		else if (value instanceof InputStream)
			putStream(key, (InputStream) value);
		else
			putString(key, value.toString());
		
		checkSize();
	}

	@Override
	public File get(String key) {
		File f = getCacheFile(key);
		if (f.isFile()) {
			hits++;
			//修改文件时间用于使最近使用的缓存文件保持优先
			f.setLastModified(System.currentTimeMillis());
			return f;
		}
		return null;
	}

	@Override
	public File remove(String key) {
		File f = getCacheFile(key);
		if (f.isFile() && f.delete()) return f;
		return null;
	}

	@Override
	public int size() {
		int length = 0;
		File[] files = cacheDir.listFiles();
		if (files == null) return 0;
		for (File f : files) {
			if (f.isFile()) length++;
		}
		return length;
	}

	@Override
	public void trimToSize(int size) {
		List<File> fileList = getSortedCacheFileList();
		if (fileList == null) return;
		
		for (File f : fileList) {
			if (size() > size) f.delete();
		}
		
	}

	@Override
	public void clear() {
		File[] files = cacheDir.listFiles();
		if (files == null) return;
		for (File f : files) {
			if (f.isFile()) f.delete();
		}
		
	}
	
	/**
	 * 返回 {@link #get(String)} 次数
	 * @return
	 */
	public int getHits() {
		return hits;
	}

	/**
	 * 将图片缓存入文件。(缓存文件存在时将不保存)
	 * @param url 图片的请求地址
	 * @param bitmap 图片
	 */
	private void putBitmap(String url, Bitmap bitmap) {
		if (TextUtils.isEmpty(url) || bitmap == null) return;
		
		Bitmap.CompressFormat cf = null;
		if (url.toLowerCase(Locale.getDefault()).endsWith(".png"))
			cf = Bitmap.CompressFormat.PNG;
		else
			cf = Bitmap.CompressFormat.JPEG;
		
		File f = getCacheFile(url);
		if (f.exists() || isLocalFile(url)) return;
		
		writeBitmapToFile(f, bitmap, cf);
	}
	
	/**
	 * 将输入流内容缓存入文件。(缓存文件存在时将不写入)
	 * @param url 输入流的请求地址
	 * @param is 输入流
	 */
	private void putStream(String url, InputStream is) {
		File f = getCacheFile(url);
		if (f.exists() || isLocalFile(url)) return;
		
		writeStreamToFile(f, is);
	}

	/**
	 * 将文本内容缓存至文件。(缓存文件存在时将不写入)
	 * @param url 输入流的请求地址
	 * @param text 文本
	 */
	private void putString(String url, String text) {
		File f = getCacheFile(url);
		if (f.exists() || isLocalFile(url)) return;
		
		writeTextToFile(context, f, text, CHARSET);
	}

	/**
	 * 取得缓存的文件
	 * @param url 请求地址
	 * @param cl 是否判断本地文件 {@link #isLocalFile(String)} ，是则返回该本地文件。
	 * @return
	 */
	public File get(String url, boolean cl) {
		if (!cl) return get(url);
		File f = new File(Uri.parse(url).getPath());
		if (f.isFile()) return f;
		return null;
	}

	/**
	 * 取得文件的大小 {@link #java.io.File.length()}
	 * @param value
	 * @return
	 */
	public long sizeOf(File value) {
		if (value == null) return -1;
		return value.length();
	}

	/**
	 * 返回所有缓存文件的总大小。
	 * @return
	 */
	protected long getSize() {
		long size = 0;
		File[] files = cacheDir.listFiles();
		if (files == null) return 0;
		for (File f : files) {
			if (f.isFile()) {
				size += sizeOf(f);
			}
		}
		return size;
	}
	
	private void checkSize() {
		if (getSize() <= maxSize) return;
		List<File> fileList = getSortedCacheFileList();
		if (fileList == null) return;
		for (File f : fileList) {
			if (getSize() > maxSize) f.delete();
			else break;
		}
	}
	
	/**
	 * 返回以文件时间排序好的缓存文件列表
	 * @return
	 */
	private List<File> getSortedCacheFileList() {
		File[] files = cacheDir.listFiles();
		if (files == null) return null;
		List<File> fileList = new ArrayList<File>(); //Arrays.asList(files);
		for (File f : files) {
			if (f.isFile()) fileList.add(f);
		}
		
		Collections.sort(fileList, new Comparator<File>() {

			@Override
			public int compare(File lhs, File rhs) {
				long d = lhs.lastModified() - rhs.lastModified();
				if (d > Integer.MAX_VALUE) d = Integer.MAX_VALUE;
				if (d < Integer.MIN_VALUE) d = Integer.MIN_VALUE;
				return (int) d;
			}
			
		});
		
		return fileList;
	}
	
	/**
	 * 判断 URL 是否指向的是本地文件
	 * @param url
	 * @return
	 */
	public boolean isLocalFile(String url) {
		File f = new File(Uri.parse(url).getPath());
		return f.exists();
	}
	
	/**
	 * 文件通道拷贝方法
	 * @param s 源文件
	 * @param t 目标文件
	 * @return
	 */
	public static boolean fileChannelCopy(File s, File t) {
		if (s == null || t == null) return false;
		if (s.equals(t)) return true;
		FileInputStream fi = null;
		FileOutputStream fo = null;
		FileChannel in = null;
		FileChannel out = null;
		long st = s.lastModified();
		try {
			fi = new FileInputStream(s);
			fo = new FileOutputStream(t);
			in = fi.getChannel();
			out = fo.getChannel();
			in.transferTo(0, in.size(), out);
			t.setLastModified(st);
			return true;
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				fi.close();
				in.close();
				fo.close();
				out.close();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return false;
		
	}
	
	/**
	 * 写入图像数据至文件
	 * @param file 文件
	 * @param bitmap 图像
	 * @param compressFormat 图像压缩格式
	 * @return 是否写入成功
	 */
	public static boolean writeBitmapToFile(File file, Bitmap bitmap, Bitmap.CompressFormat compressFormat) {
		if (file == null || bitmap == null) return false;
		
		if (compressFormat == null)
			compressFormat = Bitmap.CompressFormat.JPEG;
		FileOutputStream fos = null;
		
		boolean result = false;
		File dir = file.getParentFile();
		if (dir != null && !dir.exists()) dir.mkdirs();
		
		try {
			file.createNewFile();
			if (!file.exists()) return false;
			fos = new FileOutputStream(file);
			bitmap.compress(compressFormat, 100, fos);
			fos.flush();
			result = true;
		} catch (Exception e) {
			e.printStackTrace();
			if (file.exists()) file.delete();
		} finally {
			closeStream(fos);
		}
		
		return result;
	}

	/**
	 * 写入输入流数据至文件
	 * @param file 文件
	 * @param inputStream 输入流
	 * @return 是否写入成功
	 */
	public static boolean writeStreamToFile(File file, InputStream inputStream) {
		if (file == null || inputStream == null) return false;
		
		boolean result = false;
		File dir = file.getParentFile();
		if (dir != null && !dir.exists()) dir.mkdirs();
		
		FileOutputStream fos = null;
		byte[] buffer = new byte[4096];
		int len = 0;
		try {
			file.createNewFile();
			if (!file.exists()) return false;
			fos = new FileOutputStream(file);
			while ((len = inputStream.read(buffer)) != -1) {
				fos.write(buffer, 0, len);
			}
			fos.flush();
			result = true;
		} catch (IOException e) {
			e.printStackTrace();
			if (file.exists()) file.delete();
		} finally {
			closeStream(fos);
			closeStream(inputStream);
		}
		
		return result;
	}

	/**
	 * 写入文本数据至文件
	 * @param context 上下文环境
	 * @param file 文件
	 * @param text 文本内容
	 * @param charset 字符集
	 * @return 是否写入成功
	 */
	public static boolean writeTextToFile(Context context, File file, String text, String charset) {
		if (context == null || file == null) return false;
		if (TextUtils.isEmpty(text)) return false;
		if (TextUtils.isEmpty(charset)) charset = "gb2312";
		
		boolean result = false;
		File dir = file.getParentFile();
		if (dir != null && !dir.exists()) dir.mkdirs();
		
		FileOutputStream outputStream = null;
		try {
			file.createNewFile();
			if (!file.exists()) return false;
			outputStream = context.openFileOutput(file.getAbsolutePath(), Context.MODE_PRIVATE);
			outputStream.write(text.getBytes(charset));
			outputStream.flush();
			result = true;
		} catch (Exception e) {
			e.printStackTrace();
			if (file.exists()) file.delete();
		} finally {
			closeStream(outputStream);
		}
		
		return result;
	}
	
	/**
	 * 关闭流
	 * @param stream
	 */
	public static void closeStream(Closeable stream) {
		if (stream == null) return;
		try {
			stream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 获取目录的可用空间大小
	 * @param dir
	 * @return
	 */
	@SuppressWarnings("deprecation")
	public static long getFolderFreeSize(File dir) {
		if (dir == null || !dir.exists()) return -1;
		try {
			android.os.StatFs statFs = new android.os.StatFs(
					(dir.isDirectory()) ? dir.getAbsolutePath() : dir.getPath());
			long blockSize = statFs.getBlockSize();
			//long totalSize = statFs.getBlockCount()*blockSize;
			long availableSize = statFs.getAvailableBlocks()*blockSize;
			//long freeSize = statFs.getFreeBlocks()*blockSize;
			return (availableSize);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return 0;
	}
	
}

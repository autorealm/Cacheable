package com.sunteorum.kiku.cache;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

/**
 * 图像缓存类
 * @author KYO
 *
 */
public class BitmapCache implements Cacheable<Bitmap> {
	private final static int MAX_IMAGE_SIZE = 2048;
	private static int HARD_CACHE_CAPACITY = 6; //缓存数量
	private long size; //当前内存占用
	private long maxSize = (Runtime.getRuntime().maxMemory() / 4); //最大内存占用
	private int hits; //从缓存中取得数据的次数
	
	private final Map<String, Bitmap> sHardBitmapCache = Collections.synchronizedMap(
		new LinkedHashMap<String, Bitmap>(HARD_CACHE_CAPACITY / 2, 0.75f, true) {
		
			private static final long serialVersionUID = 1L;

			@Override
			protected boolean removeEldestEntry(LinkedHashMap.Entry<String, Bitmap> eldest) {
				if (size() > HARD_CACHE_CAPACITY) {
					sSoftBitmapCache.put(eldest.getKey(), new SoftReference<Bitmap>(eldest.getValue()));
					return true;
				} else {
					return false;
				}
			}
		
	});

	private final static ConcurrentHashMap<String, SoftReference<Bitmap>>
			sSoftBitmapCache = new ConcurrentHashMap<String, SoftReference<Bitmap>>(HARD_CACHE_CAPACITY / 2);

	public BitmapCache() {
		super();
	}
	
	/**
	 * 构造方法
	 * @param maxCapacity 最大缓存数量
	 */
	public BitmapCache(int maxCapacity) {
		super();
		if (maxCapacity > 0)
			HARD_CACHE_CAPACITY = maxCapacity;
	}

	@Override
	public boolean contains(String key) {
		return (sHardBitmapCache.containsKey(key) || sSoftBitmapCache.containsKey(key));
	}

	@Override
	public void put(String key, Object value) {
		if (value == null) return;
		Bitmap previous = null;
		if (!(value instanceof Bitmap)) {
			File f = null;
			String s = null;
			if (value instanceof File) {
				f = (File) value;
				s = f.getAbsolutePath();
			} else {
				s = value.toString();
				f = new File(s);
				if (!f.exists()) f = new File(Uri.parse(s).getPath());
			}
			try {
				if (!f.exists())
					value = decodeBitmapFromStream(new java.net.URL(s).openStream());
				else
					value = decodeBitmapFromFile(f);
			} catch (Exception e) {
				e.printStackTrace();
			} catch (OutOfMemoryError e) {
				e.printStackTrace();
			}
		}
		if (value == null) return;
		if (value instanceof Bitmap) {
			synchronized (sHardBitmapCache) {
				previous = sHardBitmapCache.put(key, (Bitmap) value);
				size += sizeOf((Bitmap) value);
				checkSize();
			}
		}
		if (previous != null) {
			sSoftBitmapCache.put(key, new SoftReference<Bitmap>(previous));
			size -= sizeOf(previous);
		}

	}

	@Override
	public Bitmap get(String key) {
		if (key == null) return null;
		
		synchronized (sHardBitmapCache) {
			if (sHardBitmapCache.containsKey(key)) {
				final Bitmap bitmap = sHardBitmapCache.get(key);
				if (bitmap != null) {
					sHardBitmapCache.remove(key);
					sHardBitmapCache.put(key, bitmap);
					hits++;
					return bitmap;
				}
			}
		}
		
		if (sSoftBitmapCache.containsKey(key)) {
			SoftReference<Bitmap> bitmapReference = sSoftBitmapCache.get(key);
			if (bitmapReference != null) {
				final Bitmap bitmap = bitmapReference.get();
				if (bitmap != null) {
					return bitmap;
				} else {
					sSoftBitmapCache.remove(key);
				}
			}
		}
		
		return null;
	}

	@Override
	public Bitmap remove(String key) {
		if (key == null) return null;

		Bitmap previous;
		synchronized (sHardBitmapCache) {
			previous = sHardBitmapCache.remove(key);
		}

		if (previous != null) {
			sSoftBitmapCache.put(key, new SoftReference<Bitmap>(previous));
			size -= sizeOf(previous);
		}

		return previous;
	}

	@Override
	public int size() {
		return sHardBitmapCache.size();
	}

	@Override
	public void trimToSize(int size) {
		if (size < 0 || (sHardBitmapCache.isEmpty() && size != 0)) {
			 return;
		}
		while (size() > size) {
			Map.Entry<String, Bitmap> toEvict = sHardBitmapCache.entrySet().iterator().next();
			remove(toEvict.getKey());
			
		}

	}

	@Override
	public void clear() {
		synchronized (sHardBitmapCache) {
			sHardBitmapCache.clear();
		}
		size = 0;
		sSoftBitmapCache.clear();
	}

	/**
	 * 强制清空并回收所有缓存图像
	 */
	protected void clearAndRecycleCacheBitmap() {
		Iterator<Entry<String, Bitmap>> iter = sHardBitmapCache.entrySet().iterator();
		while (iter.hasNext()) {
			Entry<String, Bitmap> entry = iter.next();
			Bitmap bmp = entry.getValue();
			if (bmp != null && !bmp.isRecycled()) bmp.recycle();
			iter.remove();
		}
		size = 0;
		clearAndRecycleCacheBitmap(sSoftBitmapCache);
	}

	/**
	 * 清空并回收缓存中的图像
	 * @param imgCache
	 */
	public static void clearAndRecycleCacheBitmap(Map<String, SoftReference<Bitmap>> imgCache) {
		if (imgCache == null || imgCache.isEmpty()) return;
		Object[] keys = imgCache.keySet().toArray();
		for (int i = 0; i < keys.length; i++) {
			if (!imgCache.containsKey(keys[i])) continue;
			Bitmap bmp = imgCache.get(keys[i]).get();
			if (bmp != null) {
				if (!bmp.isRecycled()) bmp.recycle();
			}
		}
		
		imgCache.clear();
		
	}

	/**
	 * 取得图片所占用的内存大小
	 * @param value
	 * @return
	 */
	public int sizeOf(Bitmap value) {
		if (value == null) return 0;
		return  value.getRowBytes() * value.getHeight();
	}

	private synchronized void checkSize() {
		System.out.println("[BitmapCache] cache size=" + size + " length=" + sHardBitmapCache.size());
		if (size > maxSize) {
			//least recently accessed item will be the first one iterated  
			Iterator<Entry<String, Bitmap>> iter = sHardBitmapCache.entrySet().iterator();
			while (iter.hasNext()) {
				Entry<String, Bitmap> entry = iter.next();
				size -= sizeOf(entry.getValue());
				iter.remove();
				if (size <= maxSize)
					break;
			}
			System.out.println("[BitmapCache] Clean cache. New size " + sHardBitmapCache.size());
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
	 * 设置最大内存占用大小
	 * @param maxSize
	 */
	public void setMaxSize(long maxSize) {
		if (maxSize <= 0) throw new IllegalArgumentException("size must be > 0");
		this.maxSize = maxSize;
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
	 * 读取输入流到字节数组
	 * @param inStream 输入流
	 * @return
	 * @throws Exception
	 */
	public static byte[] readInputStream(InputStream is) throws Exception {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int len = 0;
		while((len = is.read(buffer)) != -1) {
			os.write(buffer, 0, len);
		}
		os.flush();
		byte[] bytes = os.toByteArray();
		closeStream(os);
		closeStream(is);
		
		return bytes;
    }
	
	public static Bitmap decodeBitmapFromFile(File file) {
		return decodeBitmapFromFile(file, MAX_IMAGE_SIZE, MAX_IMAGE_SIZE);
	}
	
	/**
	 * 从文件获取图像
	 * @param file
	 * @param reqWidth
	 * @param reqHeight
	 * @return
	 */
	public static Bitmap decodeBitmapFromFile(File file, int reqWidth, int reqHeight) {
		InputStream inputStream = null;
		try {
			inputStream = new FileInputStream(file);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		return decodeBitmapFromStream(inputStream, reqWidth, reqHeight);
	}
	
	public static Bitmap decodeBitmapFromStream(InputStream inputStream) {
		return decodeBitmapFromStream(inputStream, MAX_IMAGE_SIZE, MAX_IMAGE_SIZE);
	}
	
	/**
	 * 从输入流获取图像
	 * @param inputStream
	 * @param reqWidth
	 * @param reqHeight
	 * @return
	 */
	public static Bitmap decodeBitmapFromStream(InputStream inputStream, int reqWidth, int reqHeight) {
		if (inputStream == null) return null;
		if (reqWidth <= 0 || reqHeight <= 0) return null;
		
		try {
			byte[] byteArr = readInputStream(inputStream);
			if (byteArr == null) return null;
			
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;
			BitmapFactory.decodeByteArray(byteArr, 0, byteArr.length, options);
			
			int height = options.outHeight;
			int width = options.outWidth;
			int inSampleSize = 1;
			if (height > reqHeight || width > reqWidth) {
				int halfHeight = height / 2;
				int halfWidth = width / 2;
				while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth) {
					inSampleSize *= 2;
				}
			}
			
			options.inSampleSize = inSampleSize;
			options.inJustDecodeBounds = false;
			options.inPreferredConfig = Bitmap.Config.ARGB_8888;
			
			return BitmapFactory.decodeByteArray(byteArr, 0, byteArr.length, options);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
}

package com.sunteorum.kiku.cache;

/**
 * 缓存类接口
 * @author KYO
 *
 * @param <T> 缓存对象，获取方法 {@link #get(String)} 将从缓存中得到该对象。<br>（键统一为字符串类型，推荐使用地址链接。）
 * <br>缓存方法 {@link #put(String, Object)} 可以放入任何对象，但需要判断该对象类型。
 */
public interface Cacheable<T extends Object> {
	
	/**
	 * 判断指定键是否存在缓存对象
	 * @param key
	 * @return
	 */
	public boolean contains(String key);
	
	/**
	 * 添加缓存对象
	 * @param key
	 * @param value
	 */
	//public void put(String key, T value);
	public void put(String key, Object value);
	
	/**
	 * 取得缓存对象
	 * @param key
	 * @return
	 */
	public T get(String key);
	
	/**
	 * 移除缓存对象
	 * @param key
	 * @return
	 */
	public T remove(String key);
	
	/**
	 * 取得缓存中的对象数量
	 * @return
	 */
	public int size();
	
	/**
	 * 使缓存对象数量减少（若大于）至指定数量
	 * @param size
	 */
	public void trimToSize(int size);
	
	/**
	 * 清空并重置缓存内容
	 */
	public void clear();
	
}

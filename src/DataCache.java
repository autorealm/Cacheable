package com.sunteorum.kiku.cache;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase.CursorFactory;

/**
 * 数据缓存类
 * @author KYO
 *
 */
public class DataCache implements Cacheable<String> {
	public final static String DATA_NAME = "data_cache.db";
	public final static String TABLE_NAME = "master";
	public final static String FIELD_ID = "_id";
	public final static String FIELD_TAG = "tag";
	public final static String FIELD_TYPE = "type";
	public final static String FIELD_URI = "uri";
	public final static String FIELD_DATA = "data";
	public final static String FIELD_EXTRA_INFO = "extra_info";
	public final static String FIELD_ENTER_TIME = "enter_time";
	public final static String FIELD_LAST_GET_TIME = "last_get_time";
	public final static String FIELD_GET_COUNT = "get_count";
	public final static String FIELD_EXPIRE = "expire";
	
	private final Context context;
    private static DataCacheHelper instance = null;
    private static SQLiteDatabase database = null;
	
	public DataCache(Context context, String tableName) {
		this.context = context;
		DataCacheHelper.getInstance(this.context);
		
	}

	@Override
	public boolean contains(String key) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void put(String key, Object value) {
		// TODO Auto-generated method stub

	}

	@Override
	public String get(String key) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String remove(String key) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int size() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void trimToSize(int size) {
		// TODO Auto-generated method stub

	}

	@Override
	public void clear() {
		// TODO Auto-generated method stub

	}

	public static class DataCacheHelper extends SQLiteOpenHelper {

		protected DataCacheHelper(Context context) {
			super(context, DATA_NAME, null, 1);
			database = this.getWritableDatabase();
		}

		protected DataCacheHelper(Context context, String name, CursorFactory factory, int version) {
			super(context, name, factory, version);
			instance = this;
			database = this.getWritableDatabase();
		}

	    public static DataCacheHelper getInstance(Context context) {
	        if (instance == null) {
	            instance = new DataCacheHelper(context.getApplicationContext());
	        }
	        return instance;
	    }
	    
	    public static String getCreateSQL(String tableName) {
	    	String sql = "create table if not exists " + tableName + " (" +
					FIELD_ID + " integer primary key autoincrement, " +
					FIELD_ID + " integer not null, " +
					FIELD_ID + " varchar(64) not null, " +
					FIELD_ID + " text not null, " +
					FIELD_ID + " integer default 2, " +
					FIELD_ID + " integer default 0, " +
					FIELD_ID + " integer default 0, " +
					FIELD_ID + " varchar(255) default null, " +
					FIELD_ID + " varchar(255) default null, " +
					FIELD_ID + " text ); ";
	    	
	    	return sql;
	    }
	    
		@Override
		public void onCreate(SQLiteDatabase db) {
			
			db.beginTransaction();
			try {
				db.execSQL(getCreateSQL(TABLE_NAME));
				db.setTransactionSuccessful();
			} finally {
				db.endTransaction();
			}

		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
			
			onCreate(db);

		}

		@Override
		public synchronized void close() {
			super.close();
			if (database != null && database.isOpen()) {
				database.close();
				database = null;
			}
			
		}
	}
	
}

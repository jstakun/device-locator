package net.gmsworld.devicelocator.utilities;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import org.apache.commons.lang3.StringUtils;

import java.util.Set;

public class PreferencesUtils {

    private static final String TAG = PreferencesUtils.class.getSimpleName();

    private final SharedPreferences sharedPreferences;
    private final Context context;

    public PreferencesUtils(Context context) {
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.context = context;
    }

    public String getEncryptedString(String key) {
        String value = sharedPreferences.getString(key, "");
        if (StringUtils.isNotEmpty(value)) {
            try {
                value = new String(SCUtils.decrypt(Base64.decode(value, Base64.NO_PADDING), context));
            } catch (Exception e) {
                //Log.d(TAG, e.getMessage(), e);
                Log.w(TAG, key + " is unencrypted!");
            }
        }
        return value;
    }

    public void setEncryptedString(String key, String value) {
        if (StringUtils.isNotEmpty(value)) {
            try {
                byte[] encValue = SCUtils.encrypt(value.getBytes(), context);
                sharedPreferences.edit().putString(key, Base64.encodeToString(encValue, Base64.NO_PADDING)).apply();
            } catch (Exception e) {
                Log.e(TAG, "Unable to encrypt " + key, e);
            }
        }
    }

    public void setString(String key, String value) {
        if (StringUtils.isNotEmpty(value)) {
            sharedPreferences.edit().putString(key, value).apply();
        }
    }

    public void setLong(String key, Long value) {
        if (value != null) {
            sharedPreferences.edit().putLong(key, value).apply();
        }
    }

    public void setInt(String key, Integer value) {
        if (value != null) {
            sharedPreferences.edit().putInt(key, value).apply();
        }
    }

    public void setBoolean(String key, Boolean value) {
        if (value != null) {
            sharedPreferences.edit().putBoolean(key, value).apply();
        }
    }

    public String getString(String key) {
        return sharedPreferences.getString(key, "");
    }

    public Long getLong(String key) {
        return sharedPreferences.getLong(key, 0L);
    }

    public Long getLong(String key, Long defaultValue) {
        return sharedPreferences.getLong(key, defaultValue);
    }

    public Integer getInt(String key) {
        return sharedPreferences.getInt(key, 0);
    }

    public Integer getInt(String key, Integer defaultValue) {
        return sharedPreferences.getInt(key, defaultValue);
    }

    public Boolean getBoolean(String key, Boolean defaultValue) {
        return sharedPreferences.getBoolean(key, defaultValue);
    }

    public boolean contains(String key) {
        return sharedPreferences.contains(key);
    }

    public Set<String> getStringSet(String key, Set<String> defaultValue) {
        return sharedPreferences.getStringSet(key, defaultValue);
    }

    public void remove(String key) {
        sharedPreferences.edit().remove(key).apply();
    }
}

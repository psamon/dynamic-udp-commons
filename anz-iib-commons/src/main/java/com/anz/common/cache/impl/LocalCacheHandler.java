/**
 * 
 */
package com.anz.common.cache.impl;

import java.io.InputStream;
import java.net.URL;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.cache.Cache;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.Duration;
import javax.cache.expiry.ModifiedExpiryPolicy;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * ehCache Cache Handler in JCashe JSR107 standard API
 * Cache Handler Factory -> Cache Handler -> Caching Provider -> Cache Manager -> Cache
 * @author sanketsw
 * 
 */
public class LocalCacheHandler extends AbstractCacheHandler {

	private static final Logger logger = LogManager.getLogger();

	private static LocalCacheHandler _inst = null;

	

	private LocalCacheHandler() throws Exception {
		super();
	}

	public static LocalCacheHandler getInstance() throws Exception {
		if (_inst == null) {
			
				_inst = new LocalCacheHandler();
			
		}
		return _inst;
	}


	@Override
	public String getDefaultCacheName() {
		return "DefaultMap";
	}

	@Override
	public String getCachingProviderName() {
		return "org.ehcache.jcache.JCacheCachingProvider";
	}

	/* (non-Javadoc)
	 * @see com.anz.common.cache.impl.AbstractCacheHandler#getCache(java.lang.String)
	 */
	@Override
	public Cache<String, String> getCache(String cacheName) {
		Cache<String, String> cache = null;
		try {
			cache = cacheManager.getCache(cacheName);
		} catch(Exception e) {
			cache = cacheManager.getCache(cacheName, String.class, String.class);
		}
		if (cache == null) {
			MutableConfiguration<String, String> jcacheConfig = new MutableConfiguration<String, String>();
			jcacheConfig.setTypes(String.class, String.class);
			// create cache
			cache = cacheManager.createCache(cacheName, jcacheConfig);
		}
		return cache;
	}
	
	@Override
	public String getCacheManagerURI() {
		URL resource = LocalCacheHandler.class.getResource("ehcache-localcache.xml");
        if(resource != null) {
            return resource.toString();
        } else {
        	logger.warn("Could not load the resource {}", "ehcache-localcache.xml");
        }
        return null;
	}
	
	

}

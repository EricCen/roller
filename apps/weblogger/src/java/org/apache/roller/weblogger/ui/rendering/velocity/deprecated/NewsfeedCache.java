/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  The ASF licenses this file to You
 * under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.  For additional information regarding
 * copyright in this work, please see the NOTICE file in the top level
 * directory of this distribution.
 */

package org.apache.roller.weblogger.ui.rendering.velocity.deprecated;

import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.SyndFeedInput;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.roller.weblogger.config.RollerConfig;
import org.apache.roller.weblogger.util.LRUCache2;


/**
 * Returns parsed RSS feed by pulling one from a cache or by retrieving and
 * parging the specified feed using the Flock RSS parser.
 *
 * TODO: use PlanetRoller to implement NewsfeedCache instead.
 */
public class NewsfeedCache {
    
    private static Log mLogger = LogFactory.getLog(NewsfeedCache.class);
    
    /** Static singleton * */
    private static NewsfeedCache mInstance = null;
    
    /** Instance vars * */
    private boolean aggregator_enabled = true;
    private boolean aggregator_cache_enabled = true;
    private int aggregator_cache_timeout = 14400;
    
    /** LRU cache */
    LRUCache2 mCache = null;
    
    
    /** Constructor */
    private NewsfeedCache() {
        // lookup the props we need
        String enabled = RollerConfig.getProperty("aggregator.enabled");
        String usecache = RollerConfig.getProperty("aggregator.cache.enabled");
        String cachetime = RollerConfig.getProperty("aggregator.cache.timeout");
        
        if("true".equalsIgnoreCase(enabled))
            this.aggregator_enabled = true;
        
        if("true".equalsIgnoreCase(usecache))
            this.aggregator_cache_enabled = true;
        
        try {
            this.aggregator_cache_timeout = Integer.parseInt(cachetime);
        } catch(Exception e) { mLogger.warn(e); }
        
        // finally ... create the cache
        this.mCache = new LRUCache2(100, 1000 * this.aggregator_cache_timeout);
    }
    
    
    /** static singleton retriever */
    public static NewsfeedCache getInstance() {
        synchronized (NewsfeedCache.class) {
            if (mInstance == null) {
                if (mLogger.isDebugEnabled()) {
                    mLogger.debug("Instantiating new NewsfeedCache");
                }
                mInstance = new NewsfeedCache();
            }
        }
        return mInstance;
    }
    
    
    /**
     * Returns a Channel object for the supplied RSS newsfeed URL.
     *
     * @param feedUrl RSS newsfeed URL.
     * @return FlockFeedI for specified RSS newsfeed URL.
     */
    public SyndFeed getChannel(String feedUrl) {
        
        SyndFeed feed = null;
        try {
            // If aggregator has been disable return null
            if (!aggregator_enabled) {
                return null;
            }
            
            if (aggregator_cache_enabled) {
                if (mLogger.isDebugEnabled()) {
                    mLogger.debug("Newsfeed: use Cache for " + feedUrl);
                }
                
                // Get pre-parsed feed from the cache
                feed = (SyndFeed) mCache.get(feedUrl);
                if (mLogger.isDebugEnabled()) {
                    mLogger.debug("Newsfeed: got from Cache");
                }
                
                if (feed == null) {
                    try {
                        // Parse the feed
                        SyndFeedInput feedInput = new SyndFeedInput();
                        feed = feedInput.build(new InputStreamReader(
                                new URL(feedUrl).openStream()));
                    } catch (Exception e1) {
                        mLogger.info("Error parsing RSS: " + feedUrl);
                    }
                }
                // Store parsed feed in the cache
                mCache.put(feedUrl, feed);
                mLogger.debug("Newsfeed: not in Cache");
                
            } else {
                if (mLogger.isDebugEnabled()) {
                    mLogger.debug("Newsfeed: not using Cache for " + feedUrl);
                }
                try {
                    // charset fix from Jason Rumney (see ROL-766)
                    URLConnection connection = new URL(feedUrl).openConnection();
                    connection.connect();
                    String contentType = connection.getContentType();
                    // Default charset to UTF-8, since we are expecting XML
                    String charset = "UTF-8";
                    if (contentType != null) {
                        int charsetStart = contentType.indexOf("charset=");
                        if (charsetStart >= 0) {
                            int charsetEnd = contentType.indexOf(";", charsetStart);
                            if (charsetEnd == -1) charsetEnd = contentType.length();
                            charsetStart += "charset=".length();
                            charset = contentType.substring(charsetStart, charsetEnd);
                            // Check that charset is recognized by Java
                            try {
                                byte[] test = "test".getBytes(charset);
                            } catch (UnsupportedEncodingException codingEx) {
                                // default to UTF-8
                                charset = "UTF-8";
                            }
                        }
                    }
                    // Parse the feed
                    SyndFeedInput feedInput = new SyndFeedInput();
                    feed = feedInput.build(new InputStreamReader(
                            connection.getInputStream(), charset));
                } catch (Exception e1) {
                    mLogger.info("Error parsing RSS: " + feedUrl);
                }
            }
            
        } catch (Exception ioe) {
            if (mLogger.isDebugEnabled()) {
                mLogger.debug("Newsfeed: Unexpected exception", ioe);
            }
        }
        
        return feed;
    }
    
}

package org.apache.hadoop.hive.metastore;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisSentinelPool;

public class RedisUtil {
	
	private static final Logger LOG = LoggerFactory.getLogger(RedisUtil.class.getName());
	private final JedisPoolConfig jedisPoolConfig;
	private static JedisSentinelPool jedisSentinelPool = null;
	private static Set<String> sentinels = new HashSet<>();
	
	public RedisUtil() {
		LOG.info("-----tianlong-----RedisUtil Constructor");
		jedisPoolConfig = new JedisPoolConfig();
		jedisPoolConfig.setMaxTotal(2000);
		jedisPoolConfig.setBlockWhenExhausted(false);
		
		sentinels.add("192.168.11.94:26380");
		sentinels.add("192.168.11.95:26380");
		sentinels.add("192.168.11.97:26380");
	}
	
	public synchronized Jedis getJedis() {
		jedisSentinelPool = new JedisSentinelPool("mymaster", sentinels, jedisPoolConfig, 120 * 1000);
		if (jedisSentinelPool != null) {
			return jedisSentinelPool.getResource();
		}
		return null;
	}
	
	public void destroy() {
		if (jedisSentinelPool == null) return;
		jedisSentinelPool.destroy();
	}
	
}

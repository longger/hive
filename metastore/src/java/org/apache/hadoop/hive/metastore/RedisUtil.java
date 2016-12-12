package org.apache.hadoop.hive.metastore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisUtil {
	
	private static final Logger LOG = LoggerFactory.getLogger(RedisUtil.class.getName());

	private static String host = "192.168.11.94";
	private static int port = 6380;
	private final JedisPoolConfig jedisPoolConfig;
	private static JedisPool jedisPool = null;
	
	public RedisUtil() {
		LOG.info("-----tianlong-----RedisUtil Constructor");
		jedisPoolConfig = new JedisPoolConfig();
		jedisPoolConfig.setMaxTotal(2000);
		jedisPoolConfig.setBlockWhenExhausted(false);
	}
	
	public synchronized Jedis getJedis() {
		jedisPool = new JedisPool(jedisPoolConfig, host, port);
		if (jedisPool != null) {
			return jedisPool.getResource();
		}
		return null;
	}
	
	public void destroy() {
		if (jedisPool == null) return;
		jedisPool.destroy();
	}
	
	
	
}

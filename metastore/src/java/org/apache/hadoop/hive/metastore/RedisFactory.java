package org.apache.hadoop.hive.metastore;

import org.apache.hadoop.hive.conf.HiveConf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisSentinelPool;

public class RedisFactory {
	/*
	 * Redis工厂模式
	 */
	private static HiveConf conf = new HiveConf();
	private static JedisSentinelPool jsp = null;
	private static JedisPool jp = null;
	private static HostAndPort ri = null;
	private final JedisPoolConfig config;
	private static final Logger LOG = LoggerFactory.getLogger(RedisFactory.class.getName());
	public RedisFactory() {
		config = new JedisPoolConfig();
		config.setBlockWhenExhausted(false);
		config.setMaxTotal(2000);
	}
	
	// 两种模式的区别，destory
	public void destroy() {
		switch (conf.getRedisMode()) {
		case STANDALONE:
			if (jp != null) {
				jp.destroy();
			}
			break;
		case SENTINEL:
			if (jsp != null) {
				jsp.destroy();
			}
			break;
		}
	}
	
	public synchronized Jedis getDefaultInstance() {
		switch (conf.getRedisMode()) {
		case STANDALONE:
			if (ri == null) {
				ri = conf.getRedisHP();
				if (ri != null) {
					jp = new JedisPool(config, ri.getHost(), ri.getPort());
				}
			}
			if (jp != null) {
				return jp.getResource();
			}
			break;
		
		case SENTINEL:
			Jedis r;
			if (jsp != null) {
				r = jsp.getResource();
			} else {
				jsp = new JedisSentinelPool("mymaster", conf.getSentinel(), config, 120 * 1000);
				r = jsp.getResource();
			}
			return r;
		default:
			break;
		}
		return null;
	}

	public synchronized static Jedis putInstance(Jedis j) {
		if (j == null) {
			return null;
		}
		switch (conf.getRedisMode()) {
		case STANDALONE:
			jp.returnResource(j);
			break;
		case SENTINEL:
			jsp.returnResource(j);
		default:
			break;
		}
		return null;
	}
	
	public synchronized static Jedis pubBrokenInstance(Jedis j) {
		if (j == null) {
			return null;
		}
		switch (conf.getRedisMode()) {
		case STANDALONE:
			jp.returnBrokenResource(j);
			break;
		case SENTINEL:
			jp.returnBrokenResource(j);
		default:
			break;
		}
		return null;
	}

}

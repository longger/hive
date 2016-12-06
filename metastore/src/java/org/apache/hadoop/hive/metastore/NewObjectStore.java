package org.apache.hadoop.hive.metastore;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.jdo.PersistenceManagerFactory;

import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.conf.HiveConf;
import org.apache.hadoop.hive.metastore.api.AggrStats;
import org.apache.hadoop.hive.metastore.api.ColumnStatistics;
import org.apache.hadoop.hive.metastore.api.CurrentNotificationEventId;
import org.apache.hadoop.hive.metastore.api.Database;
import org.apache.hadoop.hive.metastore.api.FileMetadataExprType;
import org.apache.hadoop.hive.metastore.api.Function;
import org.apache.hadoop.hive.metastore.api.HiveObjectPrivilege;
import org.apache.hadoop.hive.metastore.api.Index;
import org.apache.hadoop.hive.metastore.api.InvalidInputException;
import org.apache.hadoop.hive.metastore.api.InvalidObjectException;
import org.apache.hadoop.hive.metastore.api.InvalidPartitionException;
import org.apache.hadoop.hive.metastore.api.MetaException;
import org.apache.hadoop.hive.metastore.api.NoSuchObjectException;
import org.apache.hadoop.hive.metastore.api.NotificationEvent;
import org.apache.hadoop.hive.metastore.api.NotificationEventRequest;
import org.apache.hadoop.hive.metastore.api.NotificationEventResponse;
import org.apache.hadoop.hive.metastore.api.Partition;
import org.apache.hadoop.hive.metastore.api.PartitionEventType;
import org.apache.hadoop.hive.metastore.api.PrincipalPrivilegeSet;
import org.apache.hadoop.hive.metastore.api.PrincipalType;
import org.apache.hadoop.hive.metastore.api.PrivilegeBag;
import org.apache.hadoop.hive.metastore.api.Role;
import org.apache.hadoop.hive.metastore.api.RolePrincipalGrant;
import org.apache.hadoop.hive.metastore.api.Table;
import org.apache.hadoop.hive.metastore.api.TableMeta;
import org.apache.hadoop.hive.metastore.api.Type;
import org.apache.hadoop.hive.metastore.api.UnknownDBException;
import org.apache.hadoop.hive.metastore.api.UnknownPartitionException;
import org.apache.hadoop.hive.metastore.api.UnknownTableException;
import org.apache.hadoop.hive.metastore.partition.spec.PartitionSpecProxy;
import org.apache.hadoop.hive.shims.ShimLoader;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;

import redis.clients.jedis.Jedis;

public class NewObjectStore implements RawStore, Configurable{
	
	// NewObjectStore只用初始化一次，第一次初始化要在Redis里面新建哈希表，首先对应的Table的
	// 还要检查Redis环境
	// 只用初始化一次，所以是static类变量
	private static final Logger LOG = LoggerFactory.getLogger(NewObjectStore.class.getName());
	public static boolean initialized = false;
	private static Properties prop = null;
	private static PersistenceManagerFactory pmf = null;
	private Configuration hiveConf;
	public Jedis jedis;
	
	private static Lock pmfPropLock = new ReentrantLock();
	
	// 检查配置参数
	public static void initial() {
		if (initialized == false) {
			LOG.info("-----tianlong-----initial newobjectstore the first time！");
			// 初始化成功后，initialized设为true
			initialized = true;
			LOG.info("-----tianlong-----initial success!");
		}
	}

	@Override
	public void setConf(Configuration conf) {
		// TODO Auto-generated method stub
		pmfPropLock.lock();
		initialized = false;
		Properties propsFromConf = getDataSourceProps(conf);
		boolean propsChanged = !propsFromConf.equals(prop);
		
		if (propsChanged) {
			pmf = null;
			prop = null;
		}
		
		pmfPropLock.unlock();
	}
	
	private static Properties getDataSourceProps(Configuration conf) {
	    Properties prop = new Properties();

	    Iterator<Map.Entry<String, String>> iter = conf.iterator();
	    while (iter.hasNext()) {
	      Map.Entry<String, String> e = iter.next();
	      if (e.getKey().contains("datanucleus") || e.getKey().contains("jdo")) {
	        Object prevVal = prop.setProperty(e.getKey(), conf.get(e.getKey()));
	        if (LOG.isDebugEnabled()
	            && !e.getKey().equals(HiveConf.ConfVars.METASTOREPWD.varname)) {
	          LOG.debug("Overriding " + e.getKey() + " value " + prevVal
	              + " from  jpox.properties with " + e.getValue());
	        }
	      }
	    }
	    // Password may no longer be in the conf, use getPassword()
	    try {
	      String passwd =
	          ShimLoader.getHadoopShims().getPassword(conf, HiveConf.ConfVars.METASTOREPWD.varname);
	      if (passwd != null && !passwd.isEmpty()) {
	        prop.setProperty(HiveConf.ConfVars.METASTOREPWD.varname, passwd);
	      }
	    } catch (IOException err) {
	      throw new RuntimeException("Error getting metastore password: " + err.getMessage(), err);
	    }

	    if (LOG.isDebugEnabled()) {
	      for (Entry<Object, Object> e : prop.entrySet()) {
	        if (!e.getKey().equals(HiveConf.ConfVars.METASTOREPWD.varname)) {
	          LOG.debug(e.getKey() + " = " + e.getValue());
	        }
	      }
	    }
	    return prop;
	  }

	@Override
	public Configuration getConf() {
		// TODO Auto-generated method stub
		return hiveConf;
	}

	@Override
	public void shutdown() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean openTransaction() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean commitTransaction() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void rollbackTransaction() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void createDatabase(Database db) throws InvalidObjectException, MetaException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Database getDatabase(String name) throws NoSuchObjectException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean dropDatabase(String dbname) throws NoSuchObjectException, MetaException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean alterDatabase(String dbname, Database db) throws NoSuchObjectException, MetaException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public List<String> getDatabases(String pattern) throws MetaException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> getAllDatabases() throws MetaException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean createType(Type type) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Type getType(String typeName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean dropType(String typeName) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void createTable(Table tbl) throws InvalidObjectException, MetaException {
		// TODO Auto-generated method stub
		jedis = new RedisFactory().getDefaultInstance();
		String tbls = JSON.toJSONString(tbl);
		jedis.hset("table", "" + tbl.getDbName() + "." + tbl.getTableName(), tbls);
		LOG.info("-----tianlong-----create table " + tbl.getTableName() + " success!");
	}

	@Override
	public boolean dropTable(String dbName, String tableName)
			throws MetaException, NoSuchObjectException, InvalidObjectException, InvalidInputException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Table getTable(String dbName, String tableName) throws MetaException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean addPartition(Partition part) throws InvalidObjectException, MetaException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean addPartitions(String dbName, String tblName, List<Partition> parts)
			throws InvalidObjectException, MetaException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean addPartitions(String dbName, String tblName, PartitionSpecProxy partitionSpec, boolean ifNotExists)
			throws InvalidObjectException, MetaException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Partition getPartition(String dbName, String tableName, List<String> part_vals)
			throws MetaException, NoSuchObjectException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean doesPartitionExist(String dbName, String tableName, List<String> part_vals)
			throws MetaException, NoSuchObjectException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean dropPartition(String dbName, String tableName, List<String> part_vals)
			throws MetaException, NoSuchObjectException, InvalidObjectException, InvalidInputException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public List<Partition> getPartitions(String dbName, String tableName, int max)
			throws MetaException, NoSuchObjectException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void alterTable(String dbname, String name, Table newTable) throws InvalidObjectException, MetaException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public List<String> getTables(String dbName, String pattern) throws MetaException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<TableMeta> getTableMeta(String dbNames, String tableNames, List<String> tableTypes)
			throws MetaException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Table> getTableObjectsByName(String dbname, List<String> tableNames)
			throws MetaException, UnknownDBException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> getAllTables(String dbName) throws MetaException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> listTableNamesByFilter(String dbName, String filter, short max_tables)
			throws MetaException, UnknownDBException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> listPartitionNames(String db_name, String tbl_name, short max_parts) throws MetaException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> listPartitionNamesByFilter(String db_name, String tbl_name, String filter, short max_parts)
			throws MetaException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void alterPartition(String db_name, String tbl_name, List<String> part_vals, Partition new_part)
			throws InvalidObjectException, MetaException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void alterPartitions(String db_name, String tbl_name, List<List<String>> part_vals_list,
			List<Partition> new_parts) throws InvalidObjectException, MetaException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean addIndex(Index index) throws InvalidObjectException, MetaException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Index getIndex(String dbName, String origTableName, String indexName) throws MetaException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean dropIndex(String dbName, String origTableName, String indexName) throws MetaException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public List<Index> getIndexes(String dbName, String origTableName, int max) throws MetaException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> listIndexNames(String dbName, String origTableName, short max) throws MetaException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void alterIndex(String dbname, String baseTblName, String name, Index newIndex)
			throws InvalidObjectException, MetaException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public List<Partition> getPartitionsByFilter(String dbName, String tblName, String filter, short maxParts)
			throws MetaException, NoSuchObjectException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean getPartitionsByExpr(String dbName, String tblName, byte[] expr, String defaultPartitionName,
			short maxParts, List<Partition> result) throws TException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public List<Partition> getPartitionsByNames(String dbName, String tblName, List<String> partNames)
			throws MetaException, NoSuchObjectException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Table markPartitionForEvent(String dbName, String tblName, Map<String, String> partVals,
			PartitionEventType evtType)
			throws MetaException, UnknownTableException, InvalidPartitionException, UnknownPartitionException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isPartitionMarkedForEvent(String dbName, String tblName, Map<String, String> partName,
			PartitionEventType evtType)
			throws MetaException, UnknownTableException, InvalidPartitionException, UnknownPartitionException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean addRole(String rowName, String ownerName)
			throws InvalidObjectException, MetaException, NoSuchObjectException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean removeRole(String roleName) throws MetaException, NoSuchObjectException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean grantRole(Role role, String userName, PrincipalType principalType, String grantor,
			PrincipalType grantorType, boolean grantOption)
			throws MetaException, NoSuchObjectException, InvalidObjectException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean revokeRole(Role role, String userName, PrincipalType principalType, boolean grantOption)
			throws MetaException, NoSuchObjectException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public PrincipalPrivilegeSet getUserPrivilegeSet(String userName, List<String> groupNames)
			throws InvalidObjectException, MetaException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public PrincipalPrivilegeSet getDBPrivilegeSet(String dbName, String userName, List<String> groupNames)
			throws InvalidObjectException, MetaException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public PrincipalPrivilegeSet getTablePrivilegeSet(String dbName, String tableName, String userName,
			List<String> groupNames) throws InvalidObjectException, MetaException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public PrincipalPrivilegeSet getPartitionPrivilegeSet(String dbName, String tableName, String partition,
			String userName, List<String> groupNames) throws InvalidObjectException, MetaException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public PrincipalPrivilegeSet getColumnPrivilegeSet(String dbName, String tableName, String partitionName,
			String columnName, String userName, List<String> groupNames) throws InvalidObjectException, MetaException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<HiveObjectPrivilege> listPrincipalGlobalGrants(String principalName, PrincipalType principalType) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<HiveObjectPrivilege> listPrincipalDBGrants(String principalName, PrincipalType principalType,
			String dbName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<HiveObjectPrivilege> listAllTableGrants(String principalName, PrincipalType principalType,
			String dbName, String tableName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<HiveObjectPrivilege> listPrincipalPartitionGrants(String principalName, PrincipalType principalType,
			String dbName, String tableName, List<String> partValues, String partName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<HiveObjectPrivilege> listPrincipalTableColumnGrants(String principalName, PrincipalType principalType,
			String dbName, String tableName, String columnName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<HiveObjectPrivilege> listPrincipalPartitionColumnGrants(String principalName,
			PrincipalType principalType, String dbName, String tableName, List<String> partValues, String partName,
			String columnName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean grantPrivileges(PrivilegeBag privileges)
			throws InvalidObjectException, MetaException, NoSuchObjectException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean revokePrivileges(PrivilegeBag privileges, boolean grantOption)
			throws InvalidObjectException, MetaException, NoSuchObjectException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public Role getRole(String roleName) throws NoSuchObjectException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> listRoleNames() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Role> listRoles(String principalName, PrincipalType principalType) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<RolePrincipalGrant> listRolesWithGrants(String principalName, PrincipalType principalType) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<RolePrincipalGrant> listRoleMembers(String roleName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Partition getPartitionWithAuth(String dbName, String tblName, List<String> partVals, String user_name,
			List<String> group_names) throws MetaException, NoSuchObjectException, InvalidObjectException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Partition> getPartitionsWithAuth(String dbName, String tblName, short maxParts, String userName,
			List<String> groupNames) throws MetaException, NoSuchObjectException, InvalidObjectException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> listPartitionNamesPs(String db_name, String tbl_name, List<String> part_vals, short max_parts)
			throws MetaException, NoSuchObjectException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Partition> listPartitionsPsWithAuth(String db_name, String tbl_name, List<String> part_vals,
			short max_parts, String userName, List<String> groupNames)
			throws MetaException, InvalidObjectException, NoSuchObjectException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean updateTableColumnStatistics(ColumnStatistics colStats)
			throws NoSuchObjectException, MetaException, InvalidObjectException, InvalidInputException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean updatePartitionColumnStatistics(ColumnStatistics statsObj, List<String> partVals)
			throws NoSuchObjectException, MetaException, InvalidObjectException, InvalidInputException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public ColumnStatistics getTableColumnStatistics(String dbName, String tableName, List<String> colName)
			throws MetaException, NoSuchObjectException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<ColumnStatistics> getPartitionColumnStatistics(String dbName, String tblName, List<String> partNames,
			List<String> colNames) throws MetaException, NoSuchObjectException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean deletePartitionColumnStatistics(String dbName, String tableName, String partName,
			List<String> partVals, String colName)
			throws NoSuchObjectException, MetaException, InvalidObjectException, InvalidInputException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean deleteTableColumnStatistics(String dbName, String tableName, String colName)
			throws NoSuchObjectException, MetaException, InvalidObjectException, InvalidInputException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public long cleanupEvents() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public boolean addToken(String tokenIdentifier, String delegationToken) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean removeToken(String tokenIdentifier) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getToken(String tokenIdentifier) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> getAllTokenIdentifiers() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int addMasterKey(String key) throws MetaException {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void updateMasterKey(Integer seqNo, String key) throws NoSuchObjectException, MetaException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean removeMasterKey(Integer keySeq) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String[] getMasterKeys() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void verifySchema() throws MetaException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String getMetaStoreSchemaVersion() throws MetaException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setMetaStoreSchemaVersion(String version, String comment) throws MetaException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void dropPartitions(String dbName, String tblName, List<String> partNames)
			throws MetaException, NoSuchObjectException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public List<HiveObjectPrivilege> listPrincipalDBGrantsAll(String principalName, PrincipalType principalType) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<HiveObjectPrivilege> listPrincipalTableGrantsAll(String principalName, PrincipalType principalType) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<HiveObjectPrivilege> listPrincipalPartitionGrantsAll(String principalName,
			PrincipalType principalType) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<HiveObjectPrivilege> listPrincipalTableColumnGrantsAll(String principalName,
			PrincipalType principalType) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<HiveObjectPrivilege> listPrincipalPartitionColumnGrantsAll(String principalName,
			PrincipalType principalType) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<HiveObjectPrivilege> listGlobalGrantsAll() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<HiveObjectPrivilege> listDBGrantsAll(String dbName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<HiveObjectPrivilege> listPartitionColumnGrantsAll(String dbName, String tableName, String partitionName,
			String columnName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<HiveObjectPrivilege> listTableGrantsAll(String dbName, String tableName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<HiveObjectPrivilege> listPartitionGrantsAll(String dbName, String tableName, String partitionName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<HiveObjectPrivilege> listTableColumnGrantsAll(String dbName, String tableName, String columnName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void createFunction(Function func) throws InvalidObjectException, MetaException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void alterFunction(String dbName, String funcName, Function newFunction)
			throws InvalidObjectException, MetaException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void dropFunction(String dbName, String funcName)
			throws MetaException, NoSuchObjectException, InvalidObjectException, InvalidInputException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Function getFunction(String dbName, String funcName) throws MetaException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Function> getAllFunctions() throws MetaException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> getFunctions(String dbName, String pattern) throws MetaException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public AggrStats get_aggr_stats_for(String dbName, String tblName, List<String> partNames, List<String> colNames)
			throws MetaException, NoSuchObjectException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public NotificationEventResponse getNextNotification(NotificationEventRequest rqst) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void addNotificationEvent(NotificationEvent event) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void cleanNotificationEvents(int olderThan) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public CurrentNotificationEventId getCurrentNotificationEventId() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void flushCache() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public ByteBuffer[] getFileMetadata(List<Long> fileIds) throws MetaException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void putFileMetadata(List<Long> fileIds, List<ByteBuffer> metadata) throws MetaException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public boolean isFileMetadataSupported() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void getFileMetadataByExpr(List<Long> fileIds, FileMetadataExprType type, byte[] expr,
			ByteBuffer[] metadatas, ByteBuffer[] exprResults, boolean[] eliminated) throws MetaException {
		// TODO Auto-generated method stub
		
	}

}

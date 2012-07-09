/*
 * Copyright 2011 tetsuo.ohta[at]gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tetz42.clione;

import static tetz42.clione.loader.LoaderUtil.*;
import static tetz42.clione.util.ClioneUtil.*;
import static tetz42.util.Util.*;

import java.io.Closeable;
import java.io.InputStream;
import java.sql.Connection;
import java.util.HashSet;
import java.util.List;

import tetz42.clione.exception.ConnectionNotFoundException;
import tetz42.clione.loader.LoaderUtil;
import tetz42.clione.util.Config;
import tetz42.clione.util.ParamMap;
import tetz42.util.Using;

/**
 * 
 * @author tetz
 */
public class SQLManager implements Closeable {

	/**
	 * Enumeration of RDBMS Product.
	 * 
	 * @author tetz
	 */
	public static enum Product {
		ORACLE, SQLSERVER, DB2, MYSQL, FIREBIRD, POSTGRES, SQLITE
	}

	/**
	 * The set of SQL string and its parameters.<br>
	 * The instance of this class is generated by
	 * {@link SQLExecutor#genSqlAndParams()}.
	 * 
	 * @see SQLExecutor#genSqlAndParams(Object)
	 * @author tetz
	 */
	public static class SqlAndParam {
		public final String sql;
		public final List<Object> params;

		public SqlAndParam(String sql, List<Object> params) {
			this.sql = sql;
			this.params = params;
		}
	}

	private static ThreadLocal<Connection> tcon = new ThreadLocal<Connection>();

	/**
	 * Generates SQLManager instance.<br>
	 * The instance generated would use the connection passed through
	 * SQLManager.setThreadConnection(Connection) if it is available.<br>
	 * It will be determined by Config#DBMS_PRODUCT_NAME or the value obtained
	 * by DatabaseMetaData#getDatabaseProductName() what RDBMS products are.
	 * 
	 * @return SQLManager instance
	 * @see SQLManager#setThreadConnection(Connection)
	 * @see Config#DBMS_PRODUCT_NAME
	 */
	public static SQLManager sqlManager() {
		return new SQLManager(null, (String) null);
	}

	/**
	 * Generates SQLManager instance.<br>
	 * It will be determined by Config#DBMS_PRODUCT_NAME or the value obtained
	 * by DatabaseMetaData#getDatabaseProductName() what RDBMS products are.
	 * 
	 * @param con
	 *            connection
	 * @return SQLManager instance
	 * @see Config#DBMS_PRODUCT_NAME
	 */
	public static SQLManager sqlManager(Connection con) {
		return new SQLManager(con, (String) null);
	}

	/**
	 * Generates SQLManager instance.<br>
	 * The instance generated would use the connection passed through
	 * SQLManager.setThreadConnection(Connection) if it is available.<br>
	 * 
	 * @param product
	 *            RDBMS product
	 * @return SQLManager instance
	 * @see SQLManager#setThreadConnection(Connection)
	 */
	public static SQLManager sqlManager(Product product) {
		return new SQLManager(null, product);
	}

	/**
	 * Generates SQLManager instance.<br>
	 * The instance generated would use the connection passed through
	 * SQLManager.setThreadConnection(Connection) if it is available.<br>
	 * 
	 * @param productName
	 *            RDBMS product name. ex) oracle, mysql, db2, and so on.
	 * @return SQLManager instance
	 * @see SQLManager#setThreadConnection(Connection)
	 */
	public static SQLManager sqlManager(String productName) {
		return new SQLManager(null, productName);
	}

	/**
	 * Generates SQLManager instance.<br>
	 * 
	 * @param con
	 *            connection
	 * @param product
	 *            RDBMS product
	 * @return SQLManager instance
	 */
	public static SQLManager sqlManager(Connection con, Product product) {
		return new SQLManager(con, product);
	}

	/**
	 * Generates SQLManager instance.<br>
	 * 
	 * @param con
	 *            connection
	 * @param productName
	 *            RDBMS product name. ex) oracle, mysql, db2, and so on.
	 * @return SQLManager instance
	 */
	public static SQLManager sqlManager(Connection con, String productName) {
		return new SQLManager(con, productName);
	}

	/**
	 * Registers the connection with thread local variable.<br>
	 * Note: If you register a connection using this method,
	 * SQLManager.setThreadConnection(null) should be called before the thread
	 * ends.
	 * 
	 * @param con
	 *            connection
	 */
	public static void setThreadConnection(Connection con) {
		tcon.set(con);
	}

	/**
	 * Obtains the connection registered by
	 * SQLManager#setThreadConnection(Connection).
	 * 
	 * @return connection
	 * @see SQLManager#setThreadConnection(Connection)
	 */
	public static Connection getThreadConnection() {
		return tcon.get();
	}

	/**
	 * Generates ParamMap instance.
	 * 
	 * @return ParamMap instance
	 */
	public static ParamMap params() {
		return new ParamMap();
	}

	/**
	 * Generates ParamMap instance and registers the key and value.
	 * 
	 * @return ParamMap instance
	 * @see ParamMap#$(String, Object)
	 */
	public static ParamMap params(String key, Object value) {
		return params().$(key, value);
	}

	/**
	 * Generates ParamMap instance, inspects the parameter object, and registers
	 * its result.
	 * 
	 * @param obj
	 *            object
	 * @return ParamMap instance
	 * @see ParamMap#object(Object)
	 */
	public static ParamMap params(Object obj) {
		return params().object(obj);
	}

	/**
	 * Generates ParamMap instance and registers the keys with the value
	 * 'Boolean.TRUE'.
	 * 
	 * @param keys
	 * @return ParamMap instance
	 * @see ParamMap#$on(String...)
	 */
	public static ParamMap paramsOn(String... keys) {
		return params().$on(keys);
	}

	/**
	 * The given parameters will be converted to the SQL file path like below:<br>
	 * -- [the package name of clazz]/sql/[the class name of clazz]/[sqlFile]<br>
	 * For example, 'clazz' is a class object of 'tetz42.dao.PersonDao', and
	 * 'sqlFile' is 'Select.sql', the sql file path is:<br>
	 * -- tetz42/dao/sql/PersonDao/Select.sql<br>
	 * 
	 * @param clazz
	 *            class object
	 * @param sqlFileName
	 *            SQL file name
	 * @return SQL file path generated
	 */
	public static String getSQLPath(Class<?> clazz, String sqlFileName) {
		return LoaderUtil.getSQLPath(clazz, sqlFileName);
	}

	private final Connection con;
	private final String productName;
	private final HashSet<SQLExecutor> processingExecutorSet = new HashSet<SQLExecutor>();
	private String resourceInfo;
	private String executedSql;
	private List<Object> executedParams;
	private Object[] negativeValues = null;

	private SQLManager(Connection con, Product product) {
		this.con = getCon(con);
		this.productName = product.name().toLowerCase();
	}

	private SQLManager(Connection con, String productName) {
		this.con = getCon(con);

		if (productName == null)
			productName = Config.get().DBMS_PRODUCT_NAME;

		if (this.con != null && productName == null) {
			try {
				productName = toProduct(this.con.getMetaData()
						.getDatabaseProductName());
			} catch (Exception ignore) {
			}
		}
		this.productName = productName;
	}

	/**
	 * Generates SQLExecutor instance.<br>
	 * The given SQL is bound to the instance.
	 * 
	 * @param sql
	 *            SQL string performed
	 * @return SQLExecuter instance
	 * @see SQLExecutor
	 */
	public SQLExecutor useSQL(String sql) {
		SQLExecutor sqlExecutor = new SQLExecutor(this, getNodeBySQL(sql));
		// TODO better solution.
		this.resourceInfo = sqlExecutor.resourceInfo;
		return sqlExecutor;
	}

	/**
	 * Generates SQLExecutor instance.<br>
	 * The given parameters will be converted to the SQL file path like below:<br>
	 * -- [the package name of clazz]/sql/[the class name of clazz]/[sqlFile]<br>
	 * For example, 'clazz' is a class object of 'tetz42.dao.PersonDao', and
	 * 'sqlFile' is 'Select.sql', the sql file path is:<br>
	 * -- tetz42/dao/sql/PersonDao/Select.sql<br>
	 * The SQL file indicated is loaded and the contents is bound to the
	 * instance to be returned.
	 * 
	 * @param clazz
	 *            class object
	 * @param sqlFile
	 *            SQL file name
	 * @return SQLExecuter instance
	 * @see SQLExecutor
	 */
	public SQLExecutor useFile(Class<?> clazz, String sqlFile) {
		SQLExecutor sqlExecutor = new SQLExecutor(this, getNodeByClass(clazz,
				sqlFile, productName));
		// TODO better solution.
		this.resourceInfo = sqlExecutor.resourceInfo;
		return sqlExecutor;
	}

	/**
	 * Generates SQLExecutor instance.<br>
	 * The SQL file indicated by parameter is loaded and the contents is bound
	 * to the instance to be returned.
	 * 
	 * @param sqlPath
	 *            SQL file path
	 * @return SQLExecuter instance
	 * @see SQLExecutor
	 */
	public SQLExecutor useFile(String sqlPath) {
		SQLExecutor sqlExecutor = new SQLExecutor(this, getNodeByPath(sqlPath,
				productName));
		// TODO better solution.
		this.resourceInfo = sqlExecutor.resourceInfo;
		return sqlExecutor;
	}

	/**
	 * Generates SQLExecutor instance.<br>
	 * The given input stream is loaded and the contents is bound to the
	 * instance to be returned.
	 * 
	 * @param in
	 *            input stream
	 * @return SQLExecuter instance
	 * @see SQLExecutor
	 */
	public SQLExecutor useStream(InputStream in) {
		SQLExecutor sqlExecutor = new SQLExecutor(this, getNodeByStream(in));
		// TODO better solution.
		this.resourceInfo = sqlExecutor.resourceInfo;
		return sqlExecutor;
	}

	/**
	 * Considers empty string as negative.<br>
	 * 
	 * @return this
	 * @see SQLManager#asNegative(Object...)
	 */
	public SQLManager emptyAsNegative() {
		return asNegative("");
	}

	/**
	 * Considers given parameters as negative.<br>
	 * By default, negative values are below:<br>
	 * - null<br>
	 * - Boolean.FALSE<br>
	 * - empty list<br>
	 * - empty array<br>
	 * - a list contains negative value only<br>
	 * - a array contains negative value only<br>
	 * 
	 * @param negativeValues
	 *            values to be considered as negative
	 * @return this
	 */
	public SQLManager asNegative(Object... negativeValues) {
		this.negativeValues = combine(this.negativeValues, negativeValues);
		return this;
	}

	/**
	 * Get the information of the SQL performed previous.<br>
	 * The format of information is below:<br>
	 * 
	 * --- sql ---<br>
	 * select<br>
	 * &nbsp;&nbsp;*<br>
	 * from<br>
	 * &nbsp;&nbsp;people<br>
	 * where<br>
	 * &nbsp;&nbsp;title = ?<br>
	 * &nbsp;&nbsp;and sex is ?<br>
	 * --- params ---<br>
	 * [chief, female]<br>
	 * --- resource ---<br>
	 * SQL file path:tetz42/dao/sql/PersonDao/Select.sql<br>
	 * 
	 * @return the SQL information
	 */
	public String getSQLInfo() {
		return genSQLInfo(getSql(), getParams(), getResourceInfo());
	}

	/**
	 * Get the SQL resource information performed previous.
	 * 
	 * @return the SQL resource information
	 * @see SQLManager#getSQLInfo()
	 */
	public String getResourceInfo() {
		return this.resourceInfo;
	}

	/**
	 * Get the SQL performed previous.
	 * 
	 * @return SQL
	 * @see SQLManager#getSQLInfo()
	 */
	public String getSql() {
		return this.executedSql;
	}

	/**
	 * Get the SQL parameters performed previous.
	 * 
	 * @return SQL parameters
	 * @see SQLManager#getSQLInfo()
	 */
	public List<Object> getParams() {
		return this.executedParams;
	}

	/**
	 * Get the database connection bound this SQLManager instance.
	 * 
	 * @return database connection
	 */
	public Connection con() {
		if (this.con == null)
			throw new ConnectionNotFoundException("No connection!");
		return this.con;
	}

	/**
	 * Close the statement and the result set bound to SQLExecuter instances
	 * generated on this SQLManager instance.
	 */
	public void closeStatement() {
		closeResources(processingExecutorSet);
	}

	/**
	 * Close the statement, the result set and the database connection.
	 * 
	 * @see SQLManager#closeStatement();
	 */
	public void closeConnection() {
		closeResources(processingExecutorSet, con());
	}

	/**
	 * Close the statement and the result set bound to SQLExecuter instances
	 * generated on this SQLManager instance.
	 * 
	 * @see SQLManager#closeStatement();
	 */
	@Override
	public void close() {
		closeStatement();
	}

	Object[] getNegativeValues() {
		return negativeValues;
	}

	void putExecutor(SQLExecutor executor) {
		this.processingExecutorSet.add(executor);
	}

	void removeExecutor(SQLExecutor executor) {
		this.processingExecutorSet.remove(executor);
	}

	void setInfo(String resourceInfo, String sql, List<Object> params) {
		this.resourceInfo = resourceInfo;
		this.executedSql = sql;
		this.executedParams = params;
	}

	String getProductName() {
		return productName;
	}

	private static String toProduct(String productName) {
		if (productName == null)
			return null;
		for (Product product : Product.values()) {
			String token = product == Product.SQLSERVER ? "sql server"
					: product.name().toLowerCase();
			if (productName.toLowerCase().contains(token))
				return product.name();
		}
		return null;
	}

	private void closeResources(Object... resources) {
		new Using<Object>(resources) {
			@Override
			protected Object execute() throws Exception {
				return null; // do nothing.
			}
		}.invoke();
	}

	private Connection getCon(Connection con) {
		return con != null ? con : getThreadConnection();
	}
}

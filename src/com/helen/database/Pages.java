package com.helen.database;

import java.net.URL;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.apache.xmlrpc.client.XmlRpcSun15HttpTransportFactory;

public class Pages {

	private static final Logger logger = Logger.getLogger(Pages.class);
	private static Boolean synching = false;
	private static XmlRpcClientConfigImpl config;
	private static XmlRpcClient client;
	private static HashSet<String> storedPages = new HashSet<String>();

	static {
		config = new XmlRpcClientConfigImpl();
		try {
			config.setServerURL(new URL(Configs.getSingleProperty(
					"wikidotServer").getValue()));
			config.setBasicUserName(Configs.getSingleProperty("appName")
					.getValue());
			config.setBasicPassword(Configs.getSingleProperty("wikidotapikey")
					.getValue());
			config.setEnabledForExceptions(true);
			config.setConnectionTimeout(10 * 1000);
			config.setReplyTimeout(30 * 1000);

			client = new XmlRpcClient();
			client.setTransportFactory(new XmlRpcSun15HttpTransportFactory(
					client));
			client.setTypeFactory(new XmlRpcTypeNil(client));
			client.setConfig(config);

		} catch (Exception e) {
			logger.error("There was an exception", e);
		}

		loadPages();
	}

	private static Object pushToAPI(String method, Object... params)
			throws XmlRpcException {
		return (Object) client.execute(method, params);
	}

	public static void uploadSeries() {
		String regex = "(?m)<li><a href=\"\\/(.+)\">(.+)<\\/a> - (.+)<\\/li>";
		Pattern r = Pattern.compile(regex);

		String[] pages = new String[] { "scp-series-1", "scp-series-2",
				"scp-series-3" };

		for (String page : pages) {
			Map<String, Object> params = new HashMap<String, Object>();
			params.put("site", "scp-wiki");
			params.put("page", page);

			try {
				@SuppressWarnings("unchecked")
				HashMap<String, Object> result = (HashMap<String, Object>) pushToAPI(
						"pages.get_one", params);

				String[] lines = ((String) result.get("html")).split("\n");
				ArrayList<String[]> pagelist = new ArrayList<String[]>();
				for (String s : lines) {
					Matcher m = r.matcher(s);
					if (m.find()) {
						pagelist.add(new String[] { m.group(1), m.group(2),
								m.group(3) });
					}
				}

				for (String[] pageParts : pagelist) {
					if (!storedPages.contains(pageParts[0])) {
						CloseableStatement stmt = Connector.getStatement(
								Queries.getQuery("insertPage"), pageParts[0],
								pageParts[2]);
						stmt.executeUpdate();
					}
				}

				// TODO construct batches of 10 to do meta-data updates on

			} catch (Exception e) {
				logger.error(
						"There was an exception attempting to grab the series page metadata",
						e);
			}
		}
	}

	public static void getMethodList() {
		try {
			Object[] result = (Object[]) pushToAPI("system.listMethods",
					(Object[]) null);

			String[] methodList = new String[result.length];
			for (int i = 0; i < result.length; i++) {
				methodList[i] = (String) result[i];
			}

			for (String str : methodList) {
				logger.info(str);
			}
			// return methodList;
		} catch (Exception e) {
			logger.error("There was an exception", e);
		}
	}

	public static void listPage() {
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("site", Configs.getSingleProperty("site").getValue());
		try {

			Object[] result = (Object[]) pushToAPI("pages.select", params);

			// Convert result to a String[]
			String[] pageList = new String[result.length];
			for (int i = 0; i < result.length; i++) {
				pageList[i] = (String) result[i];
			}
			int i = 0;
			logger.info(pageList.length);
			for (String str : pageList) {
				if (!storedPages.contains(str)) {
					try {
						CloseableStatement stmt = Connector.getStatement(
								Queries.getQuery("insertPage"), str);
						stmt.executeUpdate();
					} catch (Exception e) {
						logger.error("Couldn't insert page name", e);
					}
				}
			}

			loadPages();
		} catch (Exception e) {
			logger.error("There was an exception", e);
		}
	}

	public static void getTags() {
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("site", Configs.getSingleProperty("site").getValue());

		try {

			Object[] result = (Object[]) pushToAPI("tags.select", params);

			// Convert result to a String[]
			String[] pageList = new String[result.length];
			for (int i = 0; i < result.length; i++) {
				pageList[i] = (String) result[i];
			}
			int i = 0;
			logger.info(pageList.length);
			for (String str : pageList) {
				logger.info(str);
			}
		} catch (Exception e) {
			logger.error("There was an exception", e);
		}
	}

	public static void pagesTest() {
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("site", Configs.getSingleProperty("site").getValue());

		try {

			Object[] result = (Object[]) pushToAPI("site.pages", params);

			// Convert result to a String[]
			String[] pageList = new String[result.length];
			for (int i = 0; i < result.length; i++) {
				pageList[i] = (String) result[i];
			}
			int i = 0;
			logger.info(pageList.length);
			for (String str : pageList) {
				logger.info(str);
			}
		} catch (Exception e) {
			logger.error("There was an exception", e);
		}
	}

	private static void loadMetaData() {
		if (synching) {
			java.sql.Timestamp now = new java.sql.Timestamp(
					System.currentTimeMillis());

			for (int i = 0; i < storedPages.size(); i++) {

			}
		}
	}

	private static void loadPages() {
		storedPages = new HashSet<String>();

		try {
			CloseableStatement stmt = Connector.getStatement(Queries
					.getQuery("getStoredPages"));
			ResultSet rs = stmt.getResultSet();

			while (rs != null && rs.next()) {
				storedPages.add(rs.getString("pagename"));
			}
		} catch (Exception e) {
			logger.error("There was an exception retreiving stored pages", e);
		}
	}

	public static void checkIfUpdate() {
		if (!synching) {
			try {
				CloseableStatement stmt = Connector.getStatement(Queries
						.getQuery("lastPageUpdate"));
				ResultSet rs = stmt.getResultSet();
				if (rs != null && rs.next()) {
					java.sql.Timestamp ts = rs.getTimestamp("lastUpdate");
					if ((System.currentTimeMillis() - ts.getTime()) > (60 * 60 * 1000)) {
						synching = true;
						listPage();
					}
				}
			} catch (Exception e) {
				logger.error("Error checking if update required.", e);
			}
		}
	}

}
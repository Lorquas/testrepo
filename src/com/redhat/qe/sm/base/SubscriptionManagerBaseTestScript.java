package com.redhat.qe.sm.base;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.redhat.qe.auto.testng.TestScript;
import com.redhat.qe.sm.cli.tasks.CandlepinTasks;
import com.redhat.qe.tools.SSHCommandRunner;

public class SubscriptionManagerBaseTestScript extends TestScript {

	protected static CandlepinTasks servertasks	= null;
	
	public static SSHCommandRunner server	= null;
	public static SSHCommandRunner client	= null;
	public static SSHCommandRunner client1	= null;	// client1
	public static SSHCommandRunner client2	= null;	// client2
	
	// /etc/rhsm/rhsm.conf parameters..................
	
	// rhsm.conf [server] configurations
	protected static String serverHostname				= null;
	protected static String serverPrefix 				= null;
	protected static String serverPort 					= null;
	protected static String serverInsecure				= null;
	protected static String serverCaCertDir				= null;
	
	// rhsm.conf [rhsm] configurations
	protected static String rhsmBaseUrl					= null;
	protected static String rhsmRepoCaCert				= null;
	protected static String rhsmShowIncompatiblePools	= null;
	protected static String rhsmProductCertDir			= null;
	protected static String rhsmEntitlementCertDir		= null;
	protected static String rhsmConsumerCertDir			= null;
	
	// rhsm.conf [rhsmcertd] configurations
	protected static String rhsmcertdCertFrequency		= null;

	
	
	
	protected String serverInstallDir			= getProperty("sm.server.installDir","");
	protected String serverImportDir			= getProperty("sm.server.importDir","");
	protected String serverBranch				= getProperty("sm.server.branch","");
	protected Boolean isServerOnPremises		= Boolean.valueOf(getProperty("sm.server.onPremises","false"));
	protected Boolean deployServerOnPremises	= Boolean.valueOf(getProperty("sm.server.deploy","true"));

	protected String client1hostname			= getProperty("sm.client1.hostname","");
	protected String client1username			= getProperty("sm.client1.username","");
	protected String client1password			= getProperty("sm.client1.password","");

	protected String client2hostname			= getProperty("sm.client2.hostname","");
	protected String client2username			= getProperty("sm.client2.username","");
	protected String client2password			= getProperty("sm.client2.password","");

	protected String clienthostname			= client1hostname;
	protected String clientusername			= client1username;
	protected String clientpassword			= client1password;
	
	protected String clientOwnerUsername	= getProperty("sm.client.owner.username","");
	protected String clientOwnerPassword	= getProperty("sm.client.owner.password","");
	protected String clientUsernames		= getProperty("sm.client.usernames","");
	protected String clientPasswords		= getProperty("sm.client.passwords","");

	protected String tcUnacceptedUsername	= getProperty("sm.client.username.tcunaccepted","");
	protected String tcUnacceptedPassword	= getProperty("sm.client.password.tcunaccepted","");
	protected String regtoken				= getProperty("sm.client.regtoken","");
	protected String enableRepoForDeps		= getProperty("sm.client.enableRepoForDeps","");

	protected String prodCertLocation		= getProperty("sm.prodcert.url","");
	protected String prodCertProduct		= getProperty("sm.prodcert.product","");
	
	protected String sshUser				= getProperty("sm.ssh.user","root");
	protected String sshKeyPrivate			= getProperty("sm.sshkey.private",".ssh/id_auto_dsa");
	protected String sshkeyPassphrase		= getProperty("sm.sshkey.passphrase","");
	
	protected String dbHostname				= getProperty("sm.server.db.hostname","");
	protected String dbSqlDriver			= getProperty("sm.server.db.sqlDriver","");
	protected String dbPort					= getProperty("sm.server.db.port","");
	protected String dbName					= getProperty("sm.server.db.name","");
	protected String dbUsername				= getProperty("sm.server.db.username","");
	protected String dbPassword				= getProperty("sm.server.db.password","");

	protected List<String> rpmUrls			= null;


	
	
	

	
	public SubscriptionManagerBaseTestScript() {
		super();
		
		if (getProperty("sm.rpm.urls", "").equals("")) rpmUrls = new ArrayList<String>(); else rpmUrls = Arrays.asList(getProperty("sm.rpm.urls", "").trim().split(","));
		
		// rhsm.conf [server] configurations
		serverHostname				= getProperty("sm.server.hostname","");
		serverPrefix 				= getProperty("sm.server.prefix","");
		serverPort 					= getProperty("sm.server.port","");
		serverInsecure				= getProperty("sm.server.insecure","");
		serverCaCertDir				= getProperty("sm.server.caCertDir","");
		
		// rhsm.conf [rhsm] configurations
		rhsmBaseUrl					= getProperty("sm.rhsm.baseUrl","");
		rhsmRepoCaCert				= getProperty("sm.rhsm.repoCaCert","");
		rhsmShowIncompatiblePools	= getProperty("sm.rhsm.showIncompatiblePools","");
		rhsmProductCertDir			= getProperty("sm.rhsm.productCertDir","");
		rhsmEntitlementCertDir		= getProperty("sm.rhsm.entitlementCertDir","");
		rhsmConsumerCertDir			= getProperty("sm.rhsm.consumerCertDir","");
		
		// rhsm.conf [rhsmcertd] configurations
		rhsmcertdCertFrequency		= getProperty("sm.rhsmcertd.certFrequency","");
	}
	
	/**
	 * Wraps a call to System.getProperty (String key, String def) returning "" when the System value startsWith("$")
	 * @param key
	 * @param def
	 * @return
	 */
	protected String getProperty (String key, String def) {
		// Hudson parameters that are left blank will be passed in by the variable
		// name of the parameter beginning with a $ sign, catch these and blank it
		String property = System.getProperty(key,def);
		return property.startsWith("$")? "":property;
	}

}

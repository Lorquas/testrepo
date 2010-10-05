package com.redhat.qe.sm.cli.tasks;

import java.io.File;
import java.lang.reflect.Field;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.xmlrpc.XmlRpcException;

import com.redhat.qe.auto.testng.BzChecker;
import com.redhat.qe.auto.testng.Assert;
import com.redhat.qe.auto.testng.LogMessageUtil;
import com.redhat.qe.tools.RemoteFileTasks;
import com.redhat.qe.tools.SSHCommandResult;
import com.redhat.qe.tools.SSHCommandRunner;
import com.redhat.qe.sm.base.ConsumerType;
import com.redhat.qe.sm.base.SubscriptionManagerCLITestScript;
import com.redhat.qe.sm.data.ConsumerCert;
import com.redhat.qe.sm.data.EntitlementCert;
import com.redhat.qe.sm.data.InstalledProduct;
import com.redhat.qe.sm.data.ProductSubscription;
import com.redhat.qe.sm.data.SubscriptionPool;

/**
 * @author jsefler
 *
 */
public class SubscriptionManagerTasks {

	protected static Logger log = Logger.getLogger(SubscriptionManagerTasks.class.getName());
	protected /*NOT static*/ SSHCommandRunner sshCommandRunner = null;
	public static String redhatRepoFile			= "/etc/yum.repos.d/redhat.repo";
	public static String defaultConfigFile		= "/etc/rhsm/rhsm.conf";
	public static String rhsmcertdLogFile		= "/var/log/rhsm/rhsmcertd.log";
	public static String rhsmLogFile			= "/var/log/rhsm/rhsm.log";
	public static String rhsmYumRepoFile		= "/etc/yum/pluginconf.d/rhsmplugin.conf";
	public static String factsDir				= "/etc/rhsm/facts/";
	
	// will be initialized by initializeFieldsFromConfigFile()
	public String productCertDir				= null; // "/etc/pki/product";
	public String entitlementCertDir			= null; // "/etc/pki/entitlement";
	public String consumerCertDir				= null; // "/etc/pki/consumer";
	public String consumerKeyFile				= null; // consumerCertDir+"/key.pem";
	public String consumerCertFile				= null; // consumerCertDir+"/cert.pem";
	
	
	public SubscriptionManagerTasks(SSHCommandRunner runner) {
		super();
		setSSHCommandRunner(runner);
		initializeFieldsFromConfigFile();
	}
	
	public void setSSHCommandRunner(SSHCommandRunner runner) {
		sshCommandRunner = runner;
	}
	
	/**
	 * should be called from constructor and after install
	 */
	protected void initializeFieldsFromConfigFile() {
		if (RemoteFileTasks.testFileExists(sshCommandRunner, defaultConfigFile)==1) {
			this.consumerCertDir	= getConfigFileParameter("consumerCertDir");
			this.entitlementCertDir	= getConfigFileParameter("entitlementCertDir");
			this.productCertDir		= getConfigFileParameter("productCertDir");
			this.consumerCertFile	= consumerCertDir+"/cert.pem";
			this.consumerKeyFile	= consumerCertDir+"/key.pem";
			log.info(this.getClass().getSimpleName()+".initializeFieldsFromConfigFile() succeeded on '"+sshCommandRunner.getConnection().getHostname()+"'.");
		} else {
			log.warning("Cannot "+this.getClass().getSimpleName()+".initializeFieldsFromConfigFile() on '"+sshCommandRunner.getConnection().getHostname()+"' until file exists: "+defaultConfigFile);
		}
	}
	
	
	public void installSubscriptionManagerRPMs(String[] rpmUrls, String enablerepofordeps) {

		// verify the subscription-manager client is a rhel 6 machine
		log.info("Verifying prerequisite...  client hostname '"+sshCommandRunner.getConnection().getHostname()+"' is a Red Hat Enterprise Linux .* release 6 machine.");
		Assert.assertEquals(sshCommandRunner.runCommandAndWait("cat /etc/redhat-release | grep -E \"^Red Hat Enterprise Linux .* release 6.*\"").getExitCode(),Integer.valueOf(0),"subscription-manager-cli hostname must be RHEL 6.*");

		log.info("Uninstalling existing subscription-manager RPMs...");
		sshCommandRunner.runCommandAndWait("rpm -e subscription-manager-gnome");
		RemoteFileTasks.runCommandAndAssert(sshCommandRunner,"rpm -q subscription-manager-gnome",Integer.valueOf(1),"package subscription-manager-gnome is not installed",null);
		sshCommandRunner.runCommandAndWait("rpm -e subscription-manager");
		RemoteFileTasks.runCommandAndAssert(sshCommandRunner,"rpm -q subscription-manager",Integer.valueOf(1),"package subscription-manager is not installed",null);
		sshCommandRunner.runCommandAndWait("rpm -e python-rhsm");
		RemoteFileTasks.runCommandAndAssert(sshCommandRunner,"rpm -q python-rhsm",Integer.valueOf(1),"package python-rhsm is not installed",null);
		
		for (String rpmUrl : rpmUrls) {
			rpmUrl = rpmUrl.trim();
			log.info("Installing RPM from "+rpmUrl+"...");
			String sm_rpm = "/tmp/"+Arrays.asList(rpmUrl.split("/")).get(rpmUrl.split("/").length-1);
			RemoteFileTasks.runCommandAndAssert(sshCommandRunner,"wget -O "+sm_rpm+" --no-check-certificate \""+rpmUrl.trim()+"\"",Integer.valueOf(0),null,"“"+sm_rpm+"” saved");
			// using yum localinstall should enable testing on RHTS boxes right off the bat.
			sshCommandRunner.runCommandAndWait("yum -y localinstall "+sm_rpm+" --nogpgcheck --disablerepo=* --enablerepo="+enablerepofordeps);
		}
		
		log.info("Installed version of subscription-manager RPM...");
		RemoteFileTasks.runCommandAndAssert(sshCommandRunner,"rpm -q subscription-manager",Integer.valueOf(0),"^subscription-manager-\\d.*",null);	// subscription-manager-0.63-1.el6.i686

		initializeFieldsFromConfigFile();
	}
	
	
	public void cleanOutAllCerts() {
		sshCommandRunner.runCommandAndWait("killall -9 yum");
		String value;
		
		value = getConfigFileParameter("consumerCertDir");
		log.info("Cleaning out certs from consumerCertDir: "+value);
		if (!value.startsWith("/etc/pki/")) log.warning("UNRECOGNIZED DIRECTORY.  NOT CLEANING CERTS FROM: "+value);
		else sshCommandRunner.runCommandAndWait("rm -rf "+value+"/*");
		
		value = getConfigFileParameter("entitlementCertDir");
		log.info("Cleaning out certs from entitlementCertDir: "+value);
		if (!value.startsWith("/etc/pki/")) log.warning("UNRECOGNIZED DIRECTORY.  NOT CLEANING CERTS FROM: "+value);
		else sshCommandRunner.runCommandAndWait("rm -rf "+value+"/*");
		
		value = getConfigFileParameter("productCertDir");
		log.info("Cleaning out certs from productCertDir: "+value);
		if (!value.startsWith("/etc/pki/")) log.warning("UNRECOGNIZED DIRECTORY.  NOT CLEANING CERTS FROM: "+value);
		else sshCommandRunner.runCommandAndWait("rm -rf "+value+"/*");
	}
	
	public void updateConfigFileParameter(String parameter, String value){
		Assert.assertEquals(
				RemoteFileTasks.searchReplaceFile(sshCommandRunner, defaultConfigFile, "^"+parameter+"\\s*=.*$", parameter+"="+value.replaceAll("\\/", "\\\\/")),
				0,"Updated rhsm config parameter '"+parameter+"' to value: " + value);
	}
	
	public String getConfigFileParameter(String parameter){
		SSHCommandResult result = RemoteFileTasks.runCommandAndAssert(sshCommandRunner, "grep -E ^"+parameter+" "+defaultConfigFile, 0, "^"+parameter, null);
		String value = result.getStdout().split("=|:")[1];
		return value.trim();
	}
	
//	public void updateSMConfigFile(String hostname, String port){
//		Assert.assertEquals(
//				RemoteFileTasks.searchReplaceFile(sshCommandRunner, defaultConfigFile, "^hostname\\s*=.*$", "hostname="+hostname),
//				0,"Updated rhsm config hostname to point to:" + hostname);
//		Assert.assertEquals(
//				RemoteFileTasks.searchReplaceFile(sshCommandRunner, defaultConfigFile, "^port\\s*=.*$", "port="+port),
//				0,"Updated rhsm config port to point to:" + port);
//		
//		// jsefler - 7/21/2010
//		// FIXME DELETEME AFTER FIX FROM <alikins> so, just talked to jsefler and nadathur, we are going to temporarily turn ca verification off, till we get a DEV ca or whatever setup, so we don't break QA at the moment
//		// TEMPORARY WORK AROUND TO AVOID ISSUES:
//		// https://bugzilla.redhat.com/show_bug.cgi?id=617703 
//		// https://bugzilla.redhat.com/show_bug.cgi?id=617303
//		/*
//		if (isServerOnPremises) {
//
//			log.warning("TEMPORARY WORKAROUND...");
//			sshCommandRunner.runCommandAndWait("echo \"candlepin_ca_file = /tmp/candlepin-ca.crt\"  >> "+defaultConfigFile);
//		}
//		*/
//		/* Hi,
//		Insecure mode option moved to /etc/rhsm/rhsm.conf file after commandline option(-k, --insecure) failed to gather the popularity votes.
//
//		To enable insecure mode, add the following as a new line to rhsm.conf file
//		insecure_mode=t
//    
//
//		To disable insecure mode, either remove 'insecure_mode' or set it to any value
//		other than 't', 'True', 'true', 1.
//
//		thanks,
//		Ajay
//		*/
//		log.warning("WORKAROUND FOR INSECURITY...");
//		//sshCommandRunner.runCommandAndWait("echo \"insecure_mode = true\"  >> "+defaultConfigFile);	// prior workaround
//		Assert.assertEquals(
//				RemoteFileTasks.searchReplaceFile(sshCommandRunner, defaultConfigFile, "^insecure\\s*=.*$", "insecure=1"),
//				0,"Updated rhsm config insecure to: 1");
//
//	}
	
	
	public void adjustRHSMYumRepo(boolean enabled){
		Assert.assertEquals(
				RemoteFileTasks.searchReplaceFile(sshCommandRunner, 
						rhsmYumRepoFile, 
						"^enabled=.*$", 
						"enabled="+(enabled?'1':'0')),
						0,
						"Adjusted RHSM Yum Repo config file, enabled="+(enabled?'1':'0')
				);
	}
	
	

	/**
	 * Update the minutes value for the certFrequency setting in the
	 * default /etc/rhsm/rhsm.conf file and restart the rhsmcertd service.
	 * @param certFrequency - Frequency of certificate refresh (in minutes)
	 * @param waitForMinutes - after restarting, should we wait for the next refresh?
	 */
	public void restart_rhsmcertd (int certFrequency, boolean waitForMinutes){
		updateConfigFileParameter("certFrequency", String.valueOf(certFrequency));
		RemoteFileTasks.runCommandAndAssert(sshCommandRunner,"service rhsmcertd restart",Integer.valueOf(0),"^Starting rhsmcertd "+certFrequency+"\\[  OK  \\]$",null);
		RemoteFileTasks.runCommandAndAssert(sshCommandRunner,"tail -2 "+rhsmcertdLogFile,Integer.valueOf(0),"started: interval = "+certFrequency+" minutes",null);

		if (waitForMinutes) {
			SubscriptionManagerCLITestScript.sleep(certFrequency*60*1000);
			SubscriptionManagerCLITestScript.sleep(10000);	// give the rhsmcertd a chance check in with the candlepin server and update the certs
		}
	}
	
	
	public List<SubscriptionPool> getCurrentlyAvailableSubscriptionPools() {
		return SubscriptionPool.parse(listAvailableSubscriptionPools().getStdout());
	}
	
	public List<SubscriptionPool> getCurrentlyAllAvailableSubscriptionPools() {
		return SubscriptionPool.parse(listAllAvailableSubscriptionPools().getStdout());
	}
	
	public List<ProductSubscription> getCurrentlyConsumedProductSubscriptions() {
		return ProductSubscription.parse(listConsumedProductSubscriptions().getStdout());
	}
	
	public List<InstalledProduct> getCurrentlyInstalledProducts() {
		return InstalledProduct.parse(listInstalledProducts().getStdout());
	}
	
	public List<EntitlementCert> getCurrentEntitlementCerts() {
		sshCommandRunner.runCommandAndWait("find "+entitlementCertDir+"/product/ -name '*.pem' | xargs -I '{}' openssl x509 -in '{}' -noout -text");
		String certificates = sshCommandRunner.getStdout();
		return EntitlementCert.parse(certificates);
	}
	
	/**
	 * @return a ConsumerCert object corresponding to the current: openssl x509 -noout -text -in /etc/pki/consumer/cert.pem
	 */
	public ConsumerCert getCurrentConsumerCert() {
		sshCommandRunner.runCommandAndWait("openssl x509 -noout -text -in "+consumerCertFile);
		String certificate = sshCommandRunner.getStdout();
		return ConsumerCert.parse(certificate);
	}
	
	/**
	 * @return from the contents of the current /etc/pki/consumer/cert.pem
	 */
	public String getCurrentConsumerId() {
		return getCurrentConsumerCert().consumerid;
	}
	
	/**
	 * @param registerResult
	 * @return from the stdout of the register command
	 */
	public String getCurrentConsumerId(SSHCommandResult registerResult) {
		return registerResult.getStdout().split(" ")[0];
	}
	
	public String getFactValue(String factName) {
		SSHCommandResult result = facts_(true, false);
		
		String regex=factName.replaceAll("\\(","\\\\(").replaceAll("\\)","\\\\)")+":(.*)";
		Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
		Matcher matcher = pattern.matcher(result.getStdout());
		Assert.assertTrue(matcher.find(),"Found fact "+factName); 

//		log.fine("Matches: ");
//		do {
//			log.fine(matcher.group());
//		} while (matcher.find());
		return matcher.group(1).trim();	// return the contents of the first capturing group
	}
	
	/**
	 * @return a map of serialNumber to SubscriptionPool pairs.  The SubscriptionPool is the source from where the serialNumber for the currentlyConsumedProductSubscriptions came from.
	 */
	public Map<String, SubscriptionPool> getCurrentSerialMapOfSubscriptionPools() {
		sshCommandRunner.runCommandAndWait("find "+entitlementCertDir+"/product/ -name '*.pem' | xargs -I '{}' openssl x509 -in '{}' -noout -text");
		String certificates = sshCommandRunner.getStdout();
		return SubscriptionPool.parseCerts(certificates);
	}
	
	/**
	 * @return List of /etc/pki/entitlement/product/*.pem files sorted newest first
	 */
	public List<File> getCurrentEntitlementCertFiles() {
		//sshCommandRunner.runCommandAndWait("find /etc/pki/entitlement/product/ -name '*.pem'");
		sshCommandRunner.runCommandAndWait("ls -1t "+entitlementCertDir+"/product/*.pem");
		String lsFiles = sshCommandRunner.getStdout().trim();
		List<File> files = new ArrayList<File>();
		if (!lsFiles.isEmpty()) {
			for (String lsFile : Arrays.asList(lsFiles.split("\n"))) {
				files.add(new File(lsFile));
			}
		}
		return files;
	}
	
	public HashMap<String,String[]> getPackagesCorrespondingToSubscribedRepos(){
		int min = 3;
		sshCommandRunner.runCommandAndWait("killall -9 yum");
		log.info("timeout of "+min+" minutes for next command");
		sshCommandRunner.runCommandAndWait("yum list available",Long.valueOf(min*60000));
		HashMap<String,String[]> pkgMap = new HashMap<String,String[]>();
		
		String[] packageLines = sshCommandRunner.getStdout().split("\\n");
		
		int pkglistBegin = 0;
		
		for(int i=0;i<packageLines.length;i++){
			pkglistBegin++;
			if(packageLines[i].contains("Available Packages"))
				break;
		}
		
		for(ProductSubscription sub : getCurrentlyConsumedProductSubscriptions()){
			ArrayList<String> pkgList = new ArrayList<String>();
			for(int i=pkglistBegin;i<packageLines.length;i++){
				String[] splitLine = packageLines[i].split(" ");
				String pkgName = splitLine[0];
				String repoName = splitLine[splitLine.length - 1];
				if(repoName.toLowerCase().contains(sub.productName.toLowerCase()))
					pkgList.add(pkgName);
			}
			pkgMap.put(sub.productName, (String[])pkgList.toArray());
		}
		
		return pkgMap;
	}

	/**
	 * @param productSubscription
	 * @return the SubscriptionPool from which this consumed ProductSubscription came from
	 */
	public SubscriptionPool getSubscriptionPoolFromProductSubscription(ProductSubscription productSubscription) {
		
		// if already known, return the SubscriptionPool from which ProductSubscription came
		if (productSubscription.fromPool != null) return productSubscription.fromPool;
		
		productSubscription.fromPool = getCurrentSerialMapOfSubscriptionPools().get(productSubscription.serialNumber);

		return productSubscription.fromPool;
	}
	
//	/**
//	 * @param subscriptionName
//	 * @param subscriptionPools - usually getCurrentlyAvailableSubscriptionPools()
//	 * @return the SubscriptionPool from subscriptionPools whose name is subscriptionName (if not found, null is returned)
//	 */
//	public SubscriptionPool findSubscriptionPoolWithNameFrom(String subscriptionName, List<SubscriptionPool> subscriptionPools) {
//		
//		SubscriptionPool subscriptionPoolWithSubscriptionName = null;
//		for (SubscriptionPool subscriptionPool : subscriptionPools) {
//			if (subscriptionPool.subscriptionName.equals(subscriptionName)) subscriptionPoolWithSubscriptionName = subscriptionPool;
//		}
//		return subscriptionPoolWithSubscriptionName;
//	}
	
	/**
	 * @param fieldName
	 * @param fieldValue
	 * @param subscriptionPools - usually getCurrentlyAvailableSubscriptionPools()
	 * @return - the SubscriptionPool from subscriptionPools that has a matching field (if not found, null is returned)
	 */
	public SubscriptionPool findSubscriptionPoolWithMatchingFieldFromList(String fieldName, Object fieldValue, List<SubscriptionPool> subscriptionPools) {
		
		SubscriptionPool subscriptionPoolWithMatchingField = null;
		for (SubscriptionPool subscriptionPool : subscriptionPools) {
			try {
				if (SubscriptionPool.class.getField(fieldName).get(subscriptionPool).equals(fieldValue)) {
					subscriptionPoolWithMatchingField = subscriptionPool;
				}
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (SecurityException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NoSuchFieldException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return subscriptionPoolWithMatchingField;
	}
	
//	/**
//	 * @param productName
//	 * @param productSubscriptions - usually getCurrentlyConsumedProductSubscriptions()
//	 * @return the ProductSubscription from productSubscriptions whose name is productName (if not found, null is returned)
//	 */
//	public ProductSubscription findProductSubscriptionWithNameFrom(String productName, List<ProductSubscription> productSubscriptions) {
//		ProductSubscription productSubscriptionWithProductName = null;
//		for (ProductSubscription productSubscription : productSubscriptions) {
//			if (productSubscription.productName.equals(productName)) productSubscriptionWithProductName = productSubscription;
//		}
//		return productSubscriptionWithProductName;
//	}
	
	/**
	 * @param fieldName
	 * @param fieldValue
	 * @param productSubscriptions - usually getCurrentlyConsumedProductSubscriptions()
	 * @return - the ProductSubscription from productSubscriptions that has a matching field (if not found, null is returned)
	 */
	public ProductSubscription findProductSubscriptionWithMatchingFieldFromList(String fieldName, Object fieldValue, List<ProductSubscription> productSubscriptions) {
		ProductSubscription productSubscriptionWithMatchingField = null;
		for (ProductSubscription productSubscription : productSubscriptions) {
			try {
				if (ProductSubscription.class.getField(fieldName).get(productSubscription).equals(fieldValue)) {
					productSubscriptionWithMatchingField = productSubscription;
				}
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (SecurityException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NoSuchFieldException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return productSubscriptionWithMatchingField;
	}
	
	/**
	 * For the given consumed ProductSubscription, get the EntitlementCerts
	 * @param productSubscription
	 * @return
	 */
	public List<EntitlementCert> getEntitlementCertsFromProductSubscription(ProductSubscription productSubscription) {
		String certFile = entitlementCertDir+"/product/"+productSubscription.serialNumber+".pem";
		sshCommandRunner.runCommandAndWait("openssl x509 -text -noout -in '"+certFile+"'");
		String certificates = sshCommandRunner.getStdout();
		List<EntitlementCert> entitlementCerts = EntitlementCert.parse(certificates);
		return entitlementCerts;
	}
	
	public List<EntitlementCert> getEntitlementCertsFromEntitlementCertFile(File serialPemFile) {
		sshCommandRunner.runCommandAndWait("openssl x509 -noout -text -in "+serialPemFile.getPath());
		String certificates = sshCommandRunner.getStdout();
		return EntitlementCert.parse(certificates);

	}
	
	public String getSerialNumberFromEntitlementCertFile(File serialPemFile) {
		// example serialPemFile: /etc/pki/entitlement/product/196.pem
		// extract the serial number from the certFile name
		// Note: probably a more robust way to do this is to get it from inside the file
		//Integer serialNumber = Integer.valueOf(serialPemFile.getName().split("\\.")[0]);
		String serialNumber = serialPemFile.getName().split("\\.")[0];
		return serialNumber;
	}
	

	
	// register module tasks ************************************************************
	
	/**
	 * register without asserting results
	 */
	public SSHCommandResult register_(String username, String password, ConsumerType type, String consumerId, Boolean autosubscribe, Boolean force) {

		// assemble the register command
		String										command  = "subscription-manager-cli register";	
		if (username!=null)							command += " --username="+username;
		if (password!=null)							command += " --password="+password;
		if (type!=null)								command += " --type="+type;
		if (consumerId!=null)						command += " --consumerid="+consumerId;
		if (autosubscribe!=null && autosubscribe)	command += " --autosubscribe";
		if (force!=null && force)					command += " --force";
		
		// register without asserting results
		return sshCommandRunner.runCommandAndWait(command);
	}
	
	/**
	 * @param username
	 * @param password
	 * @param type <br>
	 * <i>system</i>		Used for example registering a plain RHEL machine (Default)<br>
	 * <i>person</i>		Used for registering as a RH Personal<br>
	 * <i>domain</i>		Used for IPA tests<br>
	 * <i>candlepin</i>		Used for a connected Candlepin, export tests<br>
	 * @param consumerId
	 * @param autosubscribe
	 * @param force
	 */
	public SSHCommandResult register(String username, String password, ConsumerType type, String consumerId, Boolean autosubscribe, Boolean force) {
		
		SSHCommandResult sshCommandResult = register_(username, password, type, consumerId, autosubscribe, force);

		// assert results for a successful registration
		if (sshCommandResult.getStdout().startsWith("This system is already registered.")) return sshCommandResult;
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "The register command was a success.");
		Assert.assertContainsMatch(sshCommandResult.getStdout().trim(), "[a-f,0-9,\\-]{36} "+username);
		
		// assert that register with consumerId returns the expected uuid
		if (consumerId!=null) {
			Assert.assertEquals(sshCommandResult.getStdout().trim(), consumerId+" "+username, "register to an exiting consumer was a success");
		}
		
		// assert certificate files are dropped into /etc/pki/consumer
		Assert.assertTrue(RemoteFileTasks.testFileExists(sshCommandRunner,consumerKeyFile)==1, consumerKeyFile+" is present after register.");
		Assert.assertTrue(RemoteFileTasks.testFileExists(sshCommandRunner,consumerCertFile)==1, consumerCertFile+" is present after register.");
		
		// TEMPORARY WORKAROUND FOR BUG: https://bugzilla.redhat.com/show_bug.cgi?id=639417 - jsefler 10/1/2010
		boolean invokeWorkaroundWhileBugIsOpen = true;
		String bugId="639417"; 
		try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla bug "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			restart_rhsmcertd(Integer.valueOf(getConfigFileParameter("certFrequency")), false);
		}
		// END OF WORKAROUND
		
		
		return sshCommandResult; // from the register command
	}
	

	
	
	// reregister module tasks ************************************************************

//	/**
//	 * reregister without asserting results
//	 */
//	public SSHCommandResult reregister_(String username, String password, String consumerid) {
//
//		// assemble the unregister command
//		String					command  = "subscription-manager-cli reregister";	
//		if (username!=null)		command += " --username="+username;
//		if (password!=null)		command += " --password="+password;
//		if (consumerid!=null)	command += " --consumerid="+consumerid;
//		
//		// register without asserting results
//		return sshCommandRunner.runCommandAndWait(command);
//	}
//	
//	/**
//	 * "subscription-manager-cli reregister"
//	 */
//	public SSHCommandResult reregister(String username, String password, String consumerid) {
//		
//		// get the current ConsumerCert
//		ConsumerCert consumerCertBefore = null;
//		if (consumerid==null) {	//if (RemoteFileTasks.testFileExists(sshCommandRunner, consumerCertFile)==1) {
//			consumerCertBefore = getCurrentConsumerCert();
//			log.fine("Consumer cert before reregistering: "+consumerCertBefore);
//		}
//		
//		SSHCommandResult sshCommandResult = reregister_(username,password,consumerid);
//		
//		// assert results for a successful reregistration
//		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "The reregister command was a success.");
//		String regex = "[a-f,0-9,\\-]{36}";			// consumerid regex
//		if (consumerid!=null) regex=consumerid;		// consumerid
//		if (username!=null) regex+=" "+username;	// username
//		Assert.assertContainsMatch(sshCommandResult.getStdout().trim(), regex);
//
//		// get the new ConsumerCert
//		ConsumerCert consumerCertAfter = getCurrentConsumerCert();
//		log.fine("Consumer cert after reregistering: "+consumerCertAfter);
//		
//		// assert the new ConsumerCert from a successful reregistration
//		if (consumerCertBefore!=null) {
//			Assert.assertEquals(consumerCertAfter.consumerid, consumerCertBefore.consumerid,
//				"The consumer cert userid remains unchanged after reregistering.");
//			Assert.assertEquals(consumerCertAfter.username, consumerCertBefore.username,
//				"The consumer cert username remains unchanged after reregistering.");
//			Assert.assertTrue(consumerCertAfter.validityNotBefore.after(consumerCertBefore.validityNotBefore),
//				"The consumer cert validityNotBefore date has been changed to a newer date after reregistering.");
//		}
//		
//		// assert the new consumer certificate contains the reregistered credentials...
//		if (consumerid!=null) {
//			Assert.assertEquals(consumerCertAfter.consumerid, consumerid,
//				"The reregistered consumer cert belongs to the requested consumerid.");
//		}
//		if (username!=null) {
//			Assert.assertEquals(consumerCertAfter.username, username,
//				"The reregistered consumer cert belongs to the authenticated username.");
//		}
//		
//		return sshCommandResult; // from the reregister command
//	}
	
	public SSHCommandResult reregisterToExistingConsumer(String username, String password, String consumerId) {
		log.warning("The subscription-manager-cli reregister module has been eliminated and replaced by register --consumerid (10/4/2010 git hash b3c728183c7259841100eeacb7754c727dc523cd)...");
		RemoteFileTasks.runCommandAndWait(sshCommandRunner, "rm -f "+consumerCertFile, LogMessageUtil.action());
		return register(username,password,null,consumerId,null,null);
	}
	
	
	// identity module tasks ************************************************************

	/**
	 * identity without asserting results
	 */
	public SSHCommandResult identity_(String username, String password, Boolean regenerate) {

		// assemble the unregister command
		String								command  = "subscription-manager-cli identity";	
		if (username!=null)					command += " --username="+username;
		if (password!=null)					command += " --password="+password;
		if (regenerate!=null && regenerate)	command += " --regenerate";
		
		// register without asserting results
		return sshCommandRunner.runCommandAndWait(command);
	}
	
	/**
	 * "subscription-manager-cli identity"
	 */
	public SSHCommandResult identity(String username, String password, Boolean regenerate) {
		
		SSHCommandResult sshCommandResult = identity_(username,password,regenerate);
		
//		String corruptIdentityMsg = "Consumer identity either does not exist or is corrupted.";
//		
//		// TEMPORARY WORKAROUND FOR BUG: https://bugzilla.redhat.com/show_bug.cgi?id=640128 - jsefler 9/28/2010
//		boolean invokeWorkaroundWhileBugIsOpen = true;
//		String bugId="640128"; 
//		try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla bug "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
//		if (invokeWorkaroundWhileBugIsOpen) {
//			corruptIdentityMsg = "Consumer identity either does not exists or is corrupted.";
//		}
//		// END OF WORKAROUND
//		
//		if (sshCommandResult.getStdout().startsWith(corruptIdentityMsg)) {
//			return sshCommandResult;
//		}
		
//		// get the current identity cert
//		ConsumerCert consumerCert = getCurrentConsumerCert();
		
		// assert results for a successful identify
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "The identify command was a success.");
		String regex = "[a-f,0-9,\\-]{36}";			// consumerid regex
		Assert.assertContainsMatch(sshCommandResult.getStdout().trim(), regex);
		
		return sshCommandResult; // from the reregister command
	}
	
	// unregister module tasks ************************************************************

	/**
	 * unregister without asserting results
	 */
	public SSHCommandResult unregister_() {

		// assemble the unregister command
		String command  = "subscription-manager-cli unregister";	
		
		// register without asserting results
		return sshCommandRunner.runCommandAndWait(command);
	}
	
	/**
	 * "subscription-manager-cli unregister"
	 */
	public SSHCommandResult unregister() {
		SSHCommandResult sshCommandResult = unregister_();
		
		// assert results for a successful registration
		if (sshCommandResult.getExitCode()==0) {
			Assert.assertTrue(sshCommandResult.getStdout().trim().equals("System has been un-registered."), "The unregister command was a success.");
			Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "The unregister command was a success with exit code 0.");
		} else {
			Assert.assertTrue(sshCommandResult.getStdout().startsWith("This system is currently not registered."),"The unregister command was not necessary.  It was already unregistered");
			Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(1), "The unregister command returned exit code 1 meaning that it was already unregistered.");
		} 
		
		// assert that the consumer cert and key have been removed
		Assert.assertFalse(RemoteFileTasks.testFileExists(sshCommandRunner,consumerKeyFile)==1, consumerKeyFile+" is present after unregister.");
		Assert.assertFalse(RemoteFileTasks.testFileExists(sshCommandRunner,consumerCertFile)==1, consumerCertFile+" is present after unregister.");

		// assert that all of the entitlement product certs have been removed
		Assert.assertTrue(getCurrentEntitlementCertFiles().size()==0, "All of the entitlement product certificates have been removed after unregister.");
		RemoteFileTasks.runCommandExpectingNonzeroExit(sshCommandRunner,"ls "+entitlementCertDir+"/product | grep pem");

		return sshCommandResult; // from the unregister command
	}
	
	
	
	// list module tasks ************************************************************
	
	/**
	 * list without asserting results
	 */
	public SSHCommandResult list_(Boolean all, Boolean available, Boolean consumed) {

		// assemble the register command
		String								command  = "subscription-manager-cli list";	
		if (all!=null && all)				command += " --all";
		if (available!=null && available)	command += " --available";
		if (consumed!=null && consumed)		command += " --consumed";

		
		// list without asserting results
		return sshCommandRunner.runCommandAndWait(command);
	}
	
	/**
	 * @return SSHCommandResult from "subscription-manager-cli list"
	 */
	public SSHCommandResult listInstalledProducts() {
		//return RemoteFileTasks.runCommandExpectingNoTracebacks(sshCommandRunner,"subscription-manager-cli list");
		return list_(null,null,null);
	}
	
	/**
	 * @return SSHCommandResult from "subscription-manager-cli list --available"
	 */
	public SSHCommandResult listAvailableSubscriptionPools() {
		//return RemoteFileTasks.runCommandExpectingNoTracebacks(sshCommandRunner,"subscription-manager-cli list --available");
		return list_(null,Boolean.TRUE,null);
	}
	
	/**
	 * @return SSHCommandResult from "subscription-manager-cli list --all --available"
	 */
	public SSHCommandResult listAllAvailableSubscriptionPools() {
		//return RemoteFileTasks.runCommandExpectingNoTracebacks(sshCommandRunner,"subscription-manager-cli list --all --available");

		// TEMPORARY WORKAROUND FOR BUG: https://bugzilla.redhat.com/show_bug.cgi?id=638266 - jsefler 9/28/2010
		boolean invokeWorkaroundWhileBugIsOpen = false;
		String bugId="638266"; 
		try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla bug "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			return list_(Boolean.FALSE,Boolean.TRUE,null);
		}
		// END OF WORKAROUND
		
		return list_(Boolean.TRUE,Boolean.TRUE,null);
		
	}
	
	/**
	 * @return SSHCommandResult from "subscription-manager-cli list --consumed"
	 */
	public SSHCommandResult listConsumedProductSubscriptions() {
		//return RemoteFileTasks.runCommandExpectingNoTracebacks(sshCommandRunner,"subscription-manager-cli list --consumed");
		return list_(null,null,Boolean.TRUE);
	}
	
	
	
	// subscribe module tasks ************************************************************

	/**
	 * subscribe without asserting results
	 */
	public SSHCommandResult subscribe_(String poolId, String productId, String regtoken, String email, String locale) {
		
		// assemble the subscribe command
		String					command  = "subscription-manager-cli subscribe";	
		if (poolId!=null)		command += " --pool="+poolId;
		if (productId!=null)	command += " --product="+productId;
		if (regtoken!=null)		command += " --regtoken="+regtoken;
		if (email!=null)		command += " --email="+email;
		if (locale!=null)		command += " --locale="+locale;
		
		// subscribe without asserting results
		return sshCommandRunner.runCommandAndWait(command);
	}

	/**
	 * subscribe without asserting results
	 */
	public SSHCommandResult subscribe_(List<String> poolIds, List<String> productIds, List<String> regtokens, String email, String locale) {

		// assemble the subscribe command
		String														command  = "subscription-manager-cli subscribe";	
		if (poolIds!=null)		for (String poolId : poolIds)		command += " --pool="+poolId;
		if (productIds!=null)	for (String productId : productIds)	command += " --product="+productId;
		if (regtokens!=null)	for (String regtoken : regtokens)	command += " --regtoken="+regtoken;
		if (email!=null)											command += " --email="+email;
		if (locale!=null)											command += " --locale="+locale;

		// subscribe without asserting results
		return sshCommandRunner.runCommandAndWait(command);
	}
	
	public SSHCommandResult subscribe(String poolId, String productId, String regtoken, String email, String locale) {

		SSHCommandResult sshCommandResult = subscribe_(poolId, productId, regtoken, email, locale);
		
		// assert results
		if (sshCommandResult.getStderr().startsWith("This consumer is already subscribed")) return sshCommandResult;
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "The subscribe command was a success.");
		return sshCommandResult;
	}
	
	public SSHCommandResult subscribe(List<String> poolIds, List<String> productIds, List<String> regtokens, String email, String locale) {

		SSHCommandResult sshCommandResult = subscribe_(poolIds, productIds, regtokens, email, locale);
		
		// assert results
		if (sshCommandResult.getStderr().startsWith("This consumer is already subscribed")) return sshCommandResult;
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "The subscribe command was a success.");
		return sshCommandResult;
	}
	
	public void subscribeToProduct(String product) {
		RemoteFileTasks.runCommandExpectingNonzeroExit(sshCommandRunner,"subscription-manager-cli subscribe --product="+product);
	}
	
	/**
	 * subscribe to the given SubscriptionPool and return the newly dropped EntitlementCert file to the newly consumed ProductSubscriptions 
	 * @param pool
	 * @return
	 */
	public File subscribeToSubscriptionPoolUsingPoolId(SubscriptionPool pool) {
		List<ProductSubscription> beforeProductSubscriptions = getCurrentlyConsumedProductSubscriptions();
		List<File> beforeEntitlementCertFiles = getCurrentEntitlementCertFiles();
		log.info("Subscribing to subscription pool: "+pool);
		subscribe(pool.poolId, null, null, null, null);

		// assert that the remaining SubscriptionPools does NOT contain the pool just subscribed to
		List<SubscriptionPool> afterSubscriptionPools = getCurrentlyAvailableSubscriptionPools();
		Assert.assertTrue(!afterSubscriptionPools.contains(pool),
				"The available subscription pools no longer contains the just subscribed to pool: "+pool);
		
		// assert that the remaining SubscriptionPools do NOT contain the same productId just subscribed to
		for (SubscriptionPool afterSubscriptionPool : afterSubscriptionPools) {
			Assert.assertTrue(!afterSubscriptionPool.productId.equals(pool.productId),
					"This remaining available pool "+afterSubscriptionPool+" does NOT contain the same productId ("+pool.productId+") after subscribing to pool: "+pool);
		}

		// assert that a new entitlement cert file has been dropped in /etc/pki/entitlement/product
		List<File> afterEntitlementCertFiles = getCurrentEntitlementCertFiles();
		File newCertFile = afterEntitlementCertFiles.get(0);
		Assert.assertTrue(afterEntitlementCertFiles.size()>0 && !beforeEntitlementCertFiles.contains(newCertFile),
				"A new entitlement certificate ("+newCertFile+") has been dropped after after subscribing to pool: "+pool);
		
		// assert that only ONE new entitlement cert file has been dropped in /etc/pki/entitlement/product
		// https://bugzilla.redhat.com/show_bug.cgi?id=640338
		Assert.assertTrue(afterEntitlementCertFiles.size()==beforeEntitlementCertFiles.size()+1,
				"Only ONE new entitlement certificate (got '"+String.valueOf(afterEntitlementCertFiles.size()-beforeEntitlementCertFiles.size())+"' new certs) has been dropped after after subscribing to pool: "+pool);
		
		// assert that consumed ProductSubscriptions has NOT decreased
		List<ProductSubscription> afterProductSubscriptions = getCurrentlyConsumedProductSubscriptions();
		Assert.assertTrue(afterProductSubscriptions.size() >= beforeProductSubscriptions.size() && afterProductSubscriptions.size() > 0,
				"The list of currently consumed product subscriptions has increased (from "+beforeProductSubscriptions.size()+" to "+afterProductSubscriptions.size()+"), or has remained the same after subscribing (using poolID="+pool.poolId+") to pool: "+pool+"  Note: The list of consumed product subscriptions can remain the same when all the products from this subscription pool are a subset of those from a previously subscribed pool.");

		//return getEntitlementCertsFromEntitlementCertFile(newCertFile);
		return newCertFile;
	}
	
	public void subscribeToSubscriptionPoolUsingProductId(SubscriptionPool pool) {
		log.warning("Subscribing to a Subscription Pool using --product Id has been removed in subscription-manager-0.71-1.el6.i686.  Forwarding this subscribe request to use --pool Id...");
		subscribeToSubscriptionPoolUsingPoolId(pool); return;
		
		/* jsefler 7/22/2010
		List<ProductSubscription> before = getCurrentlyConsumedProductSubscriptions();
		log.info("Subscribing to subscription pool: "+pool);
		subscribe(null, pool.productId, null, null, null);
		String stderr = sshCommandRunner.getStderr().trim();
		
		List<ProductSubscription> after = getCurrentlyConsumedProductSubscriptions();
		if (stderr.equals("This consumer is already subscribed to the product '"+pool.productId+"'")) {
			Assert.assertTrue(after.size() == before.size() && after.size() > 0,
					"The list of currently consumed product subscriptions has remained the same (from "+before.size()+" to "+after.size()+") after subscribing (using productID="+pool.productId+") to pool: "+pool+"   Note: The list of consumed product subscriptions can remain the same when this product is already a subset from a previously subscribed pool.");
		} else {
			Assert.assertTrue(after.size() >= before.size() && after.size() > 0,
					"The list of currently consumed product subscriptions has increased (from "+before.size()+" to "+after.size()+"), or has remained the same after subscribing (using productID="+pool.productId+") to pool: "+pool+"  Note: The list of consumed product subscriptions can remain the same when this product is already a subset from a previously subscribed pool.");
			Assert.assertTrue(!getCurrentlyAvailableSubscriptionPools().contains(pool),
					"The available subscription pools no longer contains pool: "+pool);
		}
		*/
	}
	
	public void subscribeToSubscriptionPoolUsingPoolId(SubscriptionPool pool, boolean withPoolID){
		log.info("Subscribing to subscription pool: "+ pool);
		if(withPoolID){
			log.info("Subscribing to pool with pool ID:"+ pool.subscriptionName);
			sshCommandRunner.runCommandAndWait("subscription-manager-cli subscribe --pool="+pool.poolId);
		}
		else{
			log.info("Subscribing to pool with pool name:"+ pool.subscriptionName);
			sshCommandRunner.runCommandAndWait("subscription-manager-cli subscribe --product=\""+pool.productId+"\"");
		}
		Assert.assertTrue(getCurrentlyConsumedProductSubscriptions().size() > 0, "Successfully subscribed to pool with pool ID: "+ pool.poolId +" and pool name: "+ pool.subscriptionName);
		//TODO: add in more thorough product subscription verification
		// first improvement is to assert that the count of consumedProductIDs is at least one greater than the count of consumedProductIDs before the new pool was subscribed to.
	}
	
	public void subscribeToRegToken(String regtoken) {
		log.info("Subscribing to registration token: "+ regtoken);
		RemoteFileTasks.runCommandExpectingNoTracebacks(sshCommandRunner, "subscription-manager-cli subscribe --regtoken="+regtoken);
		Assert.assertTrue((getCurrentlyConsumedProductSubscriptions().size() > 0),
				"At least one entitlement consumed by regtoken subscription");
	}
	
	/**
	 * Individually subscribe to each of the currently available subscription pools one at a time
	 */
	public void subscribeToEachOfTheCurrentlyAvailableSubscriptionPools() {

		// individually subscribe to each available subscription pool
		for (SubscriptionPool pool : getCurrentlyAvailableSubscriptionPools()) {
			subscribeToSubscriptionPoolUsingPoolId(pool);
		}
		
		// assert
		assertNoAvailableSubscriptionPoolsToList("Asserting that no available subscription pools remain after individually subscribing to them all.");
	}
	
	
	/**
	 * Collectively subscribe to all of the currently available subscription pools in one command call
	 * @param assumingRegisterType - "system" or "candlepin"
	 */
	public void subscribeToAllOfTheCurrentlyAvailableSubscriptionPools(ConsumerType assumingRegisterType) {

		// assemble a list of all the available SubscriptionPool ids
		List <String> poolIds = new ArrayList<String>();
		List <SubscriptionPool> poolsBeforeSubscribe = getCurrentlyAvailableSubscriptionPools();
		for (SubscriptionPool pool : poolsBeforeSubscribe) {
			poolIds.add(pool.poolId);
		}
		if (!poolIds.isEmpty()) subscribe(poolIds, null, null, null, null);
		
		// assert results when assumingRegisterType="system"
		if (assumingRegisterType==null || assumingRegisterType.equals(ConsumerType.system)) {
			assertNoAvailableSubscriptionPoolsToList("Asserting that no available subscription pools remain after simultaneously subscribing to them all.");
			return;
		}
		
		// assert results when assumingRegisterType="candlepin"
		else if (assumingRegisterType.equals(ConsumerType.candlepin)) {
			List <SubscriptionPool> poolsAfterSubscribe = getCurrentlyAvailableSubscriptionPools();
			for (SubscriptionPool beforePool : poolsBeforeSubscribe) {
				boolean foundPool = false;
				for (SubscriptionPool afterPool : poolsAfterSubscribe) {
					if (afterPool.equals(beforePool)) {
						foundPool = true;
						// assert the quantity has decremented;
						Assert.assertEquals(Integer.valueOf(afterPool.quantity).intValue(), Integer.valueOf(beforePool.quantity).intValue()-1,
								"The quantity of entitlements from subscription pool id '"+afterPool.poolId+"' has decremented by one.");
						break;
					}
				}
				if (!foundPool) {
					Assert.fail("Could not find subscription pool "+beforePool+" listed after subscribing to it as a registered "+assumingRegisterType+" consumer.");
				}
			}
			return;
		}
		
		Assert.fail("Do not know how to assert subscribeToAllOfTheCurrentlyAvailableSubscriptionPools assumingRegisterType="+assumingRegisterType);
	}
//	public void subscribeToAllOfTheCurrentlyAvailableSubscriptionPools() {
//
//		// assemble a list of all the available SubscriptionPool ids
//		List <Integer> poolIds = new ArrayList<Integer>();
//		for (SubscriptionPool pool : getCurrentlyAvailableSubscriptionPools()) {
//			poolIds.add(pool.poolId);
//		}
//		if (!poolIds.isEmpty()) subscribe(poolIds, null, null, null, null);
//		
//		// assert
//		assertNoAvailableSubscriptionPoolsToList("Asserting that no available subscription pools remain after simultaneously subscribing to them all.");
//	}
	
	protected void assertNoAvailableSubscriptionPoolsToList(String assertMsg) {
		boolean invokeWorkaroundWhileBugIsOpen = true;
		
		// TEMPORARY WORKAROUND FOR BUG: https://bugzilla.redhat.com/show_bug.cgi?id=613635 - jsefler 7/14/2010
		invokeWorkaroundWhileBugIsOpen = true;
		try {String bugId="613635"; if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla bug "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			Assert.assertContainsMatch(listAvailableSubscriptionPools().getStdout(),"^No Available subscription pools to list$",assertMsg);
			return;
		} // END OF WORKAROUND
		
		// TEMPORARY WORKAROUND FOR BUG: https://bugzilla.redhat.com/show_bug.cgi?id=622839 - jsefler 8/10/2010
		invokeWorkaroundWhileBugIsOpen = true;
		try {String bugId="622839"; if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla bug "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			Assert.assertContainsMatch(listAvailableSubscriptionPools().getStdout(),"^No Available subscription pools to list$",assertMsg);
			return;
		} // END OF WORKAROUND
		
		// TEMPORARY WORKAROUND FOR BUG: https://bugzilla.redhat.com/show_bug.cgi?id=623657 - jsefler 8/12/2010
		invokeWorkaroundWhileBugIsOpen = true;
		try {String bugId="623657"; if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla bug "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			Assert.assertContainsMatch(listAvailableSubscriptionPools().getStdout(),"^No Available subscription pools to list$",assertMsg);
			return;
		} // END OF WORKAROUND
		
		// assert
		Assert.assertEquals(listAvailableSubscriptionPools().getStdout().trim(),
				"No Available subscription pools to list",assertMsg);
	}
	
	
	
	// unsubscribe module tasks ************************************************************

	/**
	 * unsubscribe without asserting results
	 */
	public SSHCommandResult unsubscribe_(Boolean all, String serial) {

		// assemble the unsubscribe command
		String					command  = "subscription-manager-cli unsubscribe";
		if (all!=null && all)	command += " --all";
		if (serial!=null)		command += " --serial="+serial;

		
		// unsubscribe without asserting results
		return sshCommandRunner.runCommandAndWait(command);
	}
	
	public SSHCommandResult unsubscribe(Boolean all, String serial) {

		SSHCommandResult sshCommandResult = unsubscribe_(all, serial);
		
		// assert results
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "The unsubscribe command was a success.");
		return sshCommandResult;
	}
	
	/**
	 * unsubscribe from entitlement product certificate serial and assert results
	 * @param serialNumber
	 * @return - false when no unsubscribe took place
	 */
	public boolean unsubscribeFromSerialNumber(String serialNumber) {
		String certFile = entitlementCertDir+"/product/"+serialNumber+".pem";
		boolean certFileExists = RemoteFileTasks.testFileExists(sshCommandRunner,certFile)==1? true:false;
		
		log.info("Unsubscribing from certificate serial: "+ serialNumber);
		SSHCommandResult result = unsubscribe(Boolean.FALSE, serialNumber);
		
		// assert the results
		if (!certFileExists) {
			Assert.assertContainsMatch(result.getStderr(), "Entitlement Certificate with serial number "+serialNumber+" could not be found.",
				"Entitlement Certificate with serial "+serialNumber+" could not be removed since it was not found.");			
			return false;
		}
		
		// assert the certFileExists is removed
		Assert.assertTrue(RemoteFileTasks.testFileExists(sshCommandRunner,certFile)==0,
				"Entitlement Certificate with serial "+serialNumber+" ("+certFile+") has been removed.");

		return true;
	}
	
	/**
	 * Issues a call to "subscription-manager-cli unsubscribe" which will unsubscribe from
	 * all currently consumed product subscriptions and then asserts the list --consumed is empty.
	 */
	public void unsubscribeFromAllOfTheCurrentlyConsumedProductSubscriptions() {
		//RemoteFileTasks.runCommandExpectingNoTracebacks(sshCommandRunner,"subscription-manager-cli unsubscribe");
		unsubscribe(Boolean.TRUE, null);

		// assert that there are no product subscriptions consumed
		Assert.assertEquals(listConsumedProductSubscriptions().getStdout().trim(),
				"No Consumed subscription pools to list","Successfully unsubscribed from all consumed products.");
		
		// assert that there are no entitlement product cert files
		Assert.assertTrue(sshCommandRunner.runCommandAndWait("find "+entitlementCertDir+"/product/ -name '*.pem'").getStdout().equals(""),
				"No entitlement product cert files exist after unsubscribing from all subscription pools.");
		
		// assert that the yum redhat repo file is gone
		Assert.assertTrue(RemoteFileTasks.testFileExists(sshCommandRunner, redhatRepoFile)==1,
				"The redhat repo file '"+redhatRepoFile+"' has been removed after unsubscribing from all subscription pools.");
	}
	
	/**
	 * Individually unsubscribe from each of the currently consumed product subscriptions.
	 */
	public void unsubscribeFromEachOfTheCurrentlyConsumedProductSubscriptions() {
		log.info("Unsubscribing from each of the currently consumed product subscriptions...");
		for(ProductSubscription sub : getCurrentlyConsumedProductSubscriptions())
			unsubscribeFromProductSubscription(sub);
		Assert.assertTrue(getCurrentlyConsumedProductSubscriptions().size()==0,
				"Currently no product subscriptions are consumed.");
		Assert.assertTrue(getCurrentEntitlementCertFiles().size()==0,
				"This machine has no entitlement certificate files.");			
	}
	
	/**
	 * Unsubscribe from the given product subscription using its serial number.
	 * @param productSubscription
	 * @return - false when the productSubscription has already been unsubscribed at a previous time
	 */
	public boolean unsubscribeFromProductSubscription(ProductSubscription productSubscription) {
//		String certFile = entitlementCertDir+"/product/"+productSubscription.serialNumber+".pem";
//		boolean certFileExists = RemoteFileTasks.testFileExists(sshCommandRunner,certFile)==1? true:false;
		
		log.info("Unsubscribing from product subscription: "+ productSubscription);
		boolean unsubscribed = unsubscribeFromSerialNumber(productSubscription.serialNumber);
		
//		if (certFileExists) {
//			// assert that the cert file was removed
//			Assert.assertTrue(RemoteFileTasks.testFileExists(sshCommandRunner,certFile)==0,
//					"After unsubscribing from serial number "+productSubscription.serialNumber+", the entitlement cert file '"+certFile+"' has been removed.");
//			Assert.assertTrue(!getCurrentEntitlementCertFiles().contains(certFile),
//					"After unsubscribing from serial number "+productSubscription.serialNumber+", the entitlement cert file '"+certFile+"' has been removed.");
//		} else {
//			// assert an error message when the product subscription was not found
//			// Example Stderr: Entitlement Certificate with serial number 301 could not be found.
//			Assert.assertEquals(sshCommandRunner.getStderr().trim(), "Entitlement Certificate with serial number "+productSubscription.serialNumber+" could not be found.",
//					"When the entitlement cert file corresponding to a product subscription does not exist, then you cannot unsubscribe from it.");
//		}
		
		Assert.assertTrue(!getCurrentlyConsumedProductSubscriptions().contains(productSubscription),
				"The currently consumed product subscriptions does not contain product: "+productSubscription);

		return unsubscribed;
	}
	
	
	// facts module tasks ************************************************************
	
	/**
	 * facts without asserting results
	 */
	public SSHCommandResult facts_(Boolean list, Boolean update) {

		// assemble the register command
		String							command  = "subscription-manager-cli facts";	
		if (list!=null && list)			command += " --list";
		if (update!=null && update)		command += " --update";
		
		// register without asserting results
		return sshCommandRunner.runCommandAndWait(command);
	}
	
	/**
	 * @param list
	 * @param update
	 * @return
	 */
	public SSHCommandResult facts(Boolean list, Boolean update) {
		ConsumerCert consumerCert = getCurrentConsumerCert();
		
		SSHCommandResult sshCommandResult = facts_(list, update);

		// assert results for a successful facts
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "The facts command was a success.");
		String regex = "";
		if (list!=null && list)		regex=".*:.*";					// list
		if (update!=null && update)	regex=consumerCert.consumerid;	// consumerid

		Assert.assertContainsMatch(sshCommandResult.getStdout().trim(), regex);
		
		return sshCommandResult; // from the facts command
	}
	
	
	
	
	
	
	
	
	/**
	 * Assert that the given entitlement certs are displayed in the stdout from "yum repolist all".
	 * @param entitlementCerts
	 */
	public void assertEntitlementCertsInYumRepolist(List<EntitlementCert> entitlementCerts, boolean areReported) {
		/* # yum repolist all
Loaded plugins: refresh-packagekit, rhnplugin, rhsmplugin
Updating Red Hat repositories.
This system is not registered with RHN.
RHN support will be disabled.
http://redhat.com/foo/path/never/repodata/repomd.xml: [Errno 14] HTTP Error 404 : http://www.redhat.com/foo/path/never/repodata/repomd.xml 
Trying other mirror.
repo id                      repo name                                                      status
always-enabled-content       always-enabled-content                                         disabled
content-label                content                                                        disabled
never-enabled-content        never-enabled-content                                          enabled: 0
rhel-beta                    Red Hat Enterprise Linux 5.90Workstation Beta - x86_64         disabled
rhel-beta-debuginfo          Red Hat Enterprise Linux 5.90Workstation Beta - x86_64 - Debug disabled
rhel-beta-optional           Red Hat Enterprise Linux 5.90Workstation Beta (Optional) - x86 disabled
rhel-beta-optional-debuginfo Red Hat Enterprise Linux 5.90Workstation Beta (Optional) - x86 disabled
rhel-beta-optional-source    Red Hat Enterprise Linux 5.90Workstation Beta (Optional) - x86 disabled
rhel-beta-source             Red Hat Enterprise Linux 5.90Workstation Beta - x86_64 - Sourc disabled
rhel-latest                  Latest RHEL 6                                                  enabled: 0
repolist: 0
		*/
		
		// assert all of the entitlement certs are reported in the stdout from "yum repolist all"
		SSHCommandResult result = sshCommandRunner.runCommandAndWait("yum repolist all");
 		for (EntitlementCert entitlementCert : entitlementCerts) {
			String regex = String.format("^%s\\s+(?:%s|.*)\\s+%s", entitlementCert.label.trim(), entitlementCert.name.substring(0,Math.min(entitlementCert.name.length(), 25)), entitlementCert.enabled.equals("1")? "enabled":"disabled");	// 25 was arbitraily picked to be short enough to be displayed by yum repolist all
			if (areReported)
				Assert.assertContainsMatch(result.getStdout(), regex);
			else
				Assert.assertContainsNoMatch(result.getStdout(), regex);
 		}

		// assert that the sshCommandRunner.getStderr() does not contains an error on the entitlementCert.download_url e.g.: http://redhat.com/foo/path/never/repodata/repomd.xml: [Errno 14] HTTP Error 404 : http://www.redhat.com/foo/path/never/repodata/repomd.xml 
		// FIXME EVENTUALLY WE NEED TO UNCOMMENT THIS ASSERT
		//Assert.assertContainsNoMatch(result.getStderr(), "HTTP Error \\d+", "HTTP Errors were encountered when runnning yum repolist all.");
	}
	
	public String getRedhatRelease() {
//		// verify the grinder hostname is a rhel 5 machine
//		log.info("Verifying prerequisite...  hostname '"+grinderHostname+"' is a Red Hat Enterprise Linux .* release 5 machine.");
//		Assert.assertEquals(sshCommandRunner.runCommandAndWait("cat /etc/redhat-release | grep -E \"^Red Hat Enterprise Linux .* release 5.*\"").getExitCode(),Integer.valueOf(0),"Grinder hostname must be RHEL 5.*");
		return sshCommandRunner.runCommandAndWait("cat /etc/redhat-release").getStdout();
	}
	
	// protected methods ************************************************************

	protected boolean poolsNoLongerAvailable(ArrayList<SubscriptionPool> beforeSubscription, ArrayList<SubscriptionPool> afterSubscription) {
		for(SubscriptionPool beforePool:beforeSubscription)
			if (afterSubscription.contains(beforePool))
				return false;
		return true;
	}
	
	protected void runRHSMCallAsLang(String lang,String rhsmCall){
		sshCommandRunner.runCommandAndWait("export LANG="+lang+"; " + rhsmCall);
	}
	
	protected void setLanguage(String lang){
		sshCommandRunner.runCommandAndWait("export LANG="+lang);
	}
}

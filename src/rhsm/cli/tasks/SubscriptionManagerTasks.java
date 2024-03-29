package rhsm.cli.tasks;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.xmlrpc.XmlRpcException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.testng.SkipException;

import com.redhat.qe.Assert;
import com.redhat.qe.auto.bugzilla.BzChecker;
import com.redhat.qe.jul.TestRecords;
import rhsm.base.ConsumerType;
import rhsm.base.SubscriptionManagerBaseTestScript;
import rhsm.base.SubscriptionManagerCLITestScript;
import rhsm.data.CertStatistics;
import rhsm.data.ConsumerCert;
import rhsm.data.ContentNamespace;
import rhsm.data.EntitlementCert;
import rhsm.data.InstalledProduct;
import rhsm.data.Org;
import rhsm.data.ProductCert;
import rhsm.data.ProductNamespace;
import rhsm.data.ProductSubscription;
import rhsm.data.Repo;
import rhsm.data.SubscriptionPool;
import rhsm.data.YumRepo;
import com.redhat.qe.tools.RemoteFileTasks;
import com.redhat.qe.tools.SSHCommandResult;
import com.redhat.qe.tools.SSHCommandRunner;

/**
 * @author jsefler
 *
 */
public class SubscriptionManagerTasks {

	protected static Logger log = Logger.getLogger(SubscriptionManagerTasks.class.getName());
	protected /*NOT static*/ SSHCommandRunner sshCommandRunner = null;
	public final String command				= "subscription-manager";
	public final String redhatRepoFile		= "/etc/yum.repos.d/redhat.repo";
	public final String rhsmConfFile		= "/etc/rhsm/rhsm.conf";
	public final String rhsmcertdLogFile	= "/var/log/rhsm/rhsmcertd.log";
	public final String rhsmUpdateFile		= "/var/run/rhsm/update";
	public final String rhsmLogFile			= "/var/log/rhsm/rhsm.log";
	public final String rhsmPluginConfFile	= "/etc/yum/pluginconf.d/subscription-manager.conf"; // "/etc/yum/pluginconf.d/rhsmplugin.conf"; renamed by dev on 11/24/2010
	public final String rhnPluginConfFile	= "/etc/yum/pluginconf.d/rhnplugin.conf";
	public final String rhsmFactsJsonFile	= "/var/lib/rhsm/facts/facts.json";
	public final String productIdJsonFile	= "/var/lib/rhsm/productid.js";	// maps a product id to the repository from which it came; managed by subscription-manager's ProductDatabase python class
	public final String rhnSystemIdFile		= "/etc/sysconfig/rhn/systemid";
	public final String rhnUp2dateFile		= "/etc/sysconfig/rhn/up2date";
	public final String factsDir			= "/etc/rhsm/facts";
	public final String certVersionFactsFilename	= "automation_forced_certificate_version.facts";
	public final String overrideFactsFilename		= "automation_override.facts";
	public final String brandingDir			= "/usr/share/rhsm/subscription_manager/branding";
	public final String varLogMessagesFile	= "/var/log/messages";
	public final String varLogAuditFile		= "/var/log/audit/audit.log";
	public final String rhsmCertD			= "rhsmcertd";
	public final String rhsmCertDWorker		= "/usr/libexec/rhsmcertd-worker";
	public final String rhsmComplianceD		= "/usr/libexec/rhsmd";	// /usr/libexec/rhsm-complianced; RHEL61
	public final String rhnDefinitionsDir	= "/tmp/"+"rhnDefinitionsDir";

	
	//public final String msg_ConsumerNotRegistered		= "Consumer not registered. Please register using --username and --password";	// changed by bug https://bugzilla.redhat.com/show_bug.cgi?id=749332
	//public final String msg_ConsumerNotRegistered		= "Error: You need to register this system by running `register` command.  Try register --help.";	// changed by bug https://bugzilla.redhat.com/show_bug.cgi?id=767790
	public final String msg_ConsumerNotRegistered		= "This system is not yet registered. Try 'subscription-manager register --help' for more information.";
	public final String msg_NeedListOrUpdateOption		= "Error: Need either --list or --update, Try facts --help";
	
	// will be initialized by initializeFieldsFromConfigFile()
	public String productCertDir				= null; // "/etc/pki/product";
	public String entitlementCertDir			= null; // "/etc/pki/entitlement";
	public String consumerCertDir				= null; // "/etc/pki/consumer";
	public String caCertDir						= null; // "/etc/rhsm/ca";
	public String baseurl						= null;
	public String consumerKeyFile()	{			return this.consumerCertDir+"/key.pem";}
	public String consumerCertFile() {			return this.consumerCertDir+"/cert.pem";}

	
	public String hostname						= null;	// of the client
	public String ipaddr						= null;	// of the client
	public String arch							= null;	// of the client
	public String sockets						= null;	// of the client
	public String variant						= null;	// of the client
	public String releasever					= null;	// of the client	 // e.g. 5Server	// e.g. 5Client
	
	protected String currentlyRegisteredUsername	= null;	// most recent username used during register
	protected String currentlyRegisteredPassword	= null;	// most recent password used during register
	protected String currentlyRegisteredOrg			= null;	// most recent owner used during register
	protected ConsumerType currentlyRegisteredType	= null;	// most recent consumer type used during register
	
	public String redhatRelease			= null;	// Red Hat Enterprise Linux Server release 5.8 Beta (Tikanga)
	public String redhatReleaseX		= null;	// 5
	public String redhatReleaseXY		= null;	// 5.8
	
	public Map<String,String> installedPackageVersion = new HashMap<String,String>();	// contains key=python-rhsm, value=python-rhsm-0.98.9-1.el5

	
	public SubscriptionManagerTasks(SSHCommandRunner runner) {
		super();
		sshCommandRunner = runner;
		hostname		= sshCommandRunner.runCommandAndWait("hostname").getStdout().trim();
		ipaddr			= sshCommandRunner.runCommandAndWait("ifconfig eth0 | grep 'inet addr:' | cut -d: -f2 | sed s/'  Bcast'//g").getStdout().trim();
		arch			= sshCommandRunner.runCommandAndWait("uname --machine").getStdout().trim();  // uname -i --hardware-platform :print the hardware platform or "unknown"	// uname -m --machine :print the machine hardware name
		releasever		= sshCommandRunner.runCommandAndWait("rpm -q --qf \"%{VERSION}\\n\" --whatprovides /etc/redhat-release").getStdout().trim();  // e.g. 5Server		// cut -f 5 -d : /etc/system-release-cpe	// rpm -q --qf "%{VERSION}\n" --whatprovides system-release		// rpm -q --qf "%{VERSION}\n" --whatprovides /etc/redhat-release
//		rhsmComplianceD	= sshCommandRunner.runCommandAndWait("rpm -ql subscription-manager | grep libexec/rhsm").getStdout().trim();
		redhatRelease	= sshCommandRunner.runCommandAndWait("cat /etc/redhat-release").getStdout().trim();
		redhatReleaseXY = sshCommandRunner.runCommandAndWait("cat /etc/redhat-release").getStdout().trim();
		if (redhatRelease.contains("Server")) variant = "Server";	//69.pem
		if (redhatRelease.contains("Client")) variant = "Client";	//68.pem   (aka Desktop)
		if (redhatRelease.contains("Workstation")) variant = "Workstation";	//71.pem
		if (redhatRelease.contains("ComputeNode")) variant = "ComputeNode";	//76.pem
		//if (redhatRelease.contains("IBM POWER")) variant = "IBM Power";	//74.pem	Red Hat Enterprise Linux for IBM POWER	// TODO  Not sure if these are correct or if they are just Server on a different arch
		//if (redhatRelease.contains("IBM System z")) variant = "System Z";	//72.pem	Red Hat Enterprise Linux for IBM System z	// TODO

		Pattern pattern = Pattern.compile("\\d+\\.\\d+");
		Matcher matcher = pattern.matcher(redhatRelease);
		Assert.assertTrue(matcher.find(),"Extracted redhatReleaseXY '"+matcher.group()+"' from '"+redhatRelease+"'");
		redhatReleaseXY = matcher.group();
		redhatReleaseX = redhatReleaseXY.replaceFirst("\\..*", "");
		
		// predict sockets on the system
		if (Float.valueOf(redhatReleaseXY) < 6.0f) {
			sockets = sshCommandRunner.runCommandAndWait("for cpu in `ls -1 /sys/devices/system/cpu/ | egrep cpu[[:digit:]]`; do echo \"cpu `cat /sys/devices/system/cpu/$cpu/topology/physical_package_id`\"; done | grep cpu | sort | uniq | wc -l").getStdout().trim();  // Reference: Bug 707292 - cpu socket detection fails on some 5.7 i386 boxes
		} else if (Float.valueOf(redhatReleaseXY) < 6.4f) {
			sockets = sshCommandRunner.runCommandAndWait("lscpu | grep 'CPU socket(s)'").getStdout().split(":")[1].trim();	// CPU socket(s):         2	
		} else {
			sockets = sshCommandRunner.runCommandAndWait("lscpu | grep 'Socket(s)'").getStdout().split(":")[1].trim();	// Socket(s):             2
		}
	}
	

	
	/**
	 * Must be called after installSubscriptionManagerRPMs(...)
	 */
	public void initializeFieldsFromConfigFile() {
		if (RemoteFileTasks.testFileExists(sshCommandRunner, rhsmConfFile)==1) {
			this.consumerCertDir	= getConfFileParameter(rhsmConfFile, "consumerCertDir").replaceFirst("/$", "");
			this.entitlementCertDir	= getConfFileParameter(rhsmConfFile, "entitlementCertDir").replaceFirst("/$", "");
			this.productCertDir		= getConfFileParameter(rhsmConfFile, "productCertDir").replaceFirst("/$", "");
			this.caCertDir			= getConfFileParameter(rhsmConfFile, "ca_cert_dir").replaceFirst("/$", "");
			this.baseurl			= getConfFileParameter(rhsmConfFile, "baseurl").replaceFirst("/$", "");
			log.info(this.getClass().getSimpleName()+".initializeFieldsFromConfigFile() succeeded on '"+sshCommandRunner.getConnection().getHostname()+"'.");
		} else {
			log.warning("Cannot "+this.getClass().getSimpleName()+".initializeFieldsFromConfigFile() on '"+sshCommandRunner.getConnection().getHostname()+"' until file exists: "+rhsmConfFile);
		}
	}
	
	
	/**
	 * Must be called after initializeFieldsFromConfigFile(...)
	 * @param repoCaCertUrls
	 */
	public void installRepoCaCerts(List<String> repoCaCertUrls) {
		// transfer copies of CA certs that cane be used when generating yum repo configs 
		for (String repoCaCertUrl : repoCaCertUrls) {
			String repoCaCert = Arrays.asList(repoCaCertUrl.split("/")).get(repoCaCertUrl.split("/").length-1);
			log.info("Copying repo CA cert '"+repoCaCert+"' from "+repoCaCertUrl+"...");
			//File repoCaCertFile = new File(serverCaCertDir.replaceFirst("/$","/")+Arrays.asList(repoCaCertUrl.split("/|=")).get(repoCaCertUrl.split("/|=").length-1));
			RemoteFileTasks.runCommandAndAssert(sshCommandRunner,"cd "+caCertDir+"; wget --no-clobber --no-check-certificate \""+repoCaCertUrl+"\"",Integer.valueOf(0),null,"."+repoCaCert+". saved|File ."+repoCaCert+". already there");
		}
	}
	
	
	/**
	 * Must be called after initializeFieldsFromConfigFile(...)
	 * @param repoCaCertFile
	 * @param toNewName
	 * @throws IOException
	 */
	public void installRepoCaCert(File repoCaCertFile, String toNewName) throws IOException {
		if (repoCaCertFile==null) return;
		if (toNewName==null) toNewName = repoCaCertFile.getName();
		
		// transfer the CA Cert File from the candlepin server to the clients so we can test in secure mode
		RemoteFileTasks.putFile(sshCommandRunner.getConnection(), repoCaCertFile.getPath(), caCertDir+"/"+toNewName, "0644");
		updateConfFileParameter(rhsmConfFile, "insecure", "0");
	}
	
	
	/**
	 * Must be called after installProductCerts(...)
	 * @param productCerts
	 * @throws IOException
	 */
	public void installProductCerts(List <File> productCerts) throws IOException {
		if (productCerts.size() > 0) {
			// directory must exist otherwise the copy will fail
			sshCommandRunner.runCommandAndWait("mkdir -p "+productCertDir);
		}

		for (File file : productCerts) {
			RemoteFileTasks.putFile(sshCommandRunner.getConnection(), file.getPath(), productCertDir+"/", "0644");
		}
	}

	public void installSubscriptionManagerRPMs(List<String> rpmInstallUrls, List<String> rpmUpdateUrls, String installOptions) {

		// make sure the client's time is accurate
		RemoteFileTasks.runCommandAndAssert(sshCommandRunner, "service ntpd stop; ntpdate clock.redhat.com; service ntpd start; chkconfig ntpd on", /*Integer.valueOf(0) DON"T CHECK EXIT CODE SINCE IT RETURNS 1 WHEN STOP FAILS EVEN THOUGH START SUCCEEDS*/null, "Starting ntpd:\\s+\\[  OK  \\]", null);

		// yum clean all
		SSHCommandResult sshCommandResult = sshCommandRunner.runCommandAndWait("yum clean all");
		if (sshCommandResult.getExitCode().equals(1)) {
			sshCommandRunner.runCommandAndWait("rm -f "+redhatRepoFile);
		}
//FIXME Failing on client2 with: [Errno 2] No such file or directory: '/var/cache/yum/x86_64/6Server'
//		Assert.assertEquals(sshCommandRunner.runCommandAndWait("yum clean all").getExitCode(),Integer.valueOf(0),"yum clean all was a success");
		sshCommandRunner.runCommandAndWait("yum clean all");
		
		// uninstall current rpms
		// http://hudson.rhq.lab.eng.bos.redhat.com:8080/hudson/view/Entitlement/job/subscription-manager_RHEL5.8/lastSuccessfulBuild/artifact/rpms/x86_64/python-rhsm.noarch.rpm,      http://hudson.rhq.lab.eng.bos.redhat.com:8080/hudson/view/Entitlement/job/subscription-manager_RHEL5.8/lastSuccessfulBuild/artifact/rpms/x86_64/subscription-manager.x86_64.rpm,      http://hudson.rhq.lab.eng.bos.redhat.com:8080/hudson/view/Entitlement/job/subscription-manager_RHEL5.8/lastSuccessfulBuild/artifact/rpms/x86_64/subscription-manager-gnome.x86_64.rpm,      http://hudson.rhq.lab.eng.bos.redhat.com:8080/hudson/view/Entitlement/job/subscription-manager_RHEL5.8/lastSuccessfulBuild/artifact/rpms/x86_64/subscription-manager-firstboot.x86_64.rpm,      http://hudson.rhq.lab.eng.bos.redhat.com:8080/hudson/view/Entitlement/job/subscription-manager_RHEL5.8/lastSuccessfulBuild/artifact/rpms/x86_64/subscription-manager-migration.x86_64.rpm,     http://gibson.usersys.redhat.com/latestrpm/?arch=noarch&version=1&rpmname=subscription-manager-migration-data
		List<String> rpmUrlsReversed = new ArrayList<String>();
		for (String rpmUrl : rpmInstallUrls) rpmUrlsReversed.add(0,rpmUrl);
		for (String rpmUrl : rpmUrlsReversed) {
			rpmUrl = rpmUrl.trim();
			String rpm = Arrays.asList(rpmUrl.split("/|=")).get(rpmUrl.split("/|=").length-1);
			String pkg = rpm.replaceFirst("\\.rpm$", "");
			String rpmPath = "/tmp/"+rpm; if (!rpmPath.endsWith(".rpm")) rpmPath+=".rpm";
			
			// remove the existing package first
			log.info("Removing existing package "+pkg+"...");
			sshCommandRunner.runCommandAndWait("yum remove -y "+pkg);
			RemoteFileTasks.runCommandAndAssert(sshCommandRunner,"rpm -q "+pkg,Integer.valueOf(1),"package "+pkg+" is not installed",null);
		}
		
		// install new rpms
		// http://hudson.rhq.lab.eng.bos.redhat.com:8080/hudson/view/Entitlement/job/subscription-manager_RHEL5.8/lastSuccessfulBuild/artifact/rpms/x86_64/python-rhsm.noarch.rpm,      http://hudson.rhq.lab.eng.bos.redhat.com:8080/hudson/view/Entitlement/job/subscription-manager_RHEL5.8/lastSuccessfulBuild/artifact/rpms/x86_64/subscription-manager.x86_64.rpm,      http://hudson.rhq.lab.eng.bos.redhat.com:8080/hudson/view/Entitlement/job/subscription-manager_RHEL5.8/lastSuccessfulBuild/artifact/rpms/x86_64/subscription-manager-gnome.x86_64.rpm,      http://hudson.rhq.lab.eng.bos.redhat.com:8080/hudson/view/Entitlement/job/subscription-manager_RHEL5.8/lastSuccessfulBuild/artifact/rpms/x86_64/subscription-manager-firstboot.x86_64.rpm,      http://hudson.rhq.lab.eng.bos.redhat.com:8080/hudson/view/Entitlement/job/subscription-manager_RHEL5.8/lastSuccessfulBuild/artifact/rpms/x86_64/subscription-manager-migration.x86_64.rpm,     http://gibson.usersys.redhat.com/latestrpm/?arch=noarch&version=1&rpmname=subscription-manager-migration-data
		for (String rpmUrl : rpmInstallUrls) {
			rpmUrl = rpmUrl.trim();
			String rpm = Arrays.asList(rpmUrl.split("/|=")).get(rpmUrl.split("/|=").length-1);
			String pkg = rpm.replaceFirst("\\.rpm$", "");
			String rpmPath = "/tmp/"+rpm; if (!rpmPath.endsWith(".rpm")) rpmPath+=".rpm";
			
			// install rpmUrl
			log.info("Installing RPM from "+rpmUrl+"...");
			RemoteFileTasks.runCommandAndAssert(sshCommandRunner,"wget -O "+rpmPath+" --no-check-certificate \""+rpmUrl.trim()+"\"",Integer.valueOf(0),null,"."+rpmPath+". saved");
			Assert.assertEquals(sshCommandRunner.runCommandAndWait("yum -y localinstall "+rpmPath+" "+installOptions).getExitCode(),Integer.valueOf(0),
					"Yum installed local rpm: "+rpmPath);
		}
		
		// attempt to install all required packages that are not already installed
		//List<String> pkgs = new ArrayList<String>(Arrays.asList(new String[]{"python-rhsm", "subscription-manager", "subscription-manager-gnome", "subscription-manager-firstboot", "subscription-manager-migration", "subscription-manager-migration-data", "expect"}));	// prior to bug 818397
		List<String> pkgs = new ArrayList<String>(Arrays.asList(new String[]{"python-rhsm", "subscription-manager", "subscription-manager-gui", "subscription-manager-firstboot", "subscription-manager-migration", "subscription-manager-migration-data", "expect"}));
		// TEMPORARY WORKAROUND FOR BUG
		String bugId = "790116"; boolean invokeWorkaroundWhileBugIsOpen = true;
		try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			String pkg = "subscription-manager-migration-data";
			log.warning("Skipping the install of "+pkg+".");
			pkgs.remove(pkg);
		}
		// END OF WORKAROUND
		for (String pkg : pkgs) {
			if (!isPackageInstalled(pkg)) {
				Assert.assertEquals(sshCommandRunner.runCommandAndWait("yum -y install "+pkg+" "+installOptions).getExitCode(),Integer.valueOf(0), "Yum installed package: "+pkg);
			}
		}
		
		// update new rpms
		// http://gibson.usersys.redhat.com/latestrpm/?arch=x86_64&basegrp=subscription-manager&version=0.98.15&rpmname=subscription-manager,     http://gibson.usersys.redhat.com/latestrpm/?arch=x86_64&basegrp=subscription-manager&version=0.98.15&rpmname=subscription-manager-gnome,     http://gibson.usersys.redhat.com/latestrpm/?arch=x86_64&basegrp=subscription-manager&version=0.98.15&rpmname=subscription-manager-firstboot,     http://gibson.usersys.redhat.com/latestrpm/?arch=x86_64&basegrp=subscription-manager&version=0.98.15&rpmname=subscription-manager-migration,    http://gibson.usersys.redhat.com/latestrpm/?arch=noarch&version=1.11&release=el5&rpmname=subscription-manager-migration-data
		String rpmPaths = "";
		for (String rpmUrl : rpmUpdateUrls) {
			rpmUrl = rpmUrl.trim();
			String rpm = Arrays.asList(rpmUrl.split("/|=")).get(rpmUrl.split("/|=").length-1);
			String pkg = rpm.replaceFirst("\\.rpm$", "");
			String rpmPath = "/tmp/"+rpm; if (!rpmPath.endsWith(".rpm")) rpmPath+=".rpm";
			
			// upgrade rpmUrl
			log.info("Upgrading RPM from "+rpmUrl+"...");
			RemoteFileTasks.runCommandAndAssert(sshCommandRunner,"wget -O "+rpmPath+" --no-check-certificate \""+rpmUrl.trim()+"\"",Integer.valueOf(0),null,"."+rpmPath+". saved");
			rpmPaths += rpmPath; rpmPaths += " ";
		}
		if (!rpmUpdateUrls.isEmpty()) Assert.assertEquals(sshCommandRunner.runCommandAndWait("yum -y localupdate "+rpmPaths+" "+installOptions).getExitCode(),Integer.valueOf(0), "Yum updated local rpms: "+rpmPaths);

		
		// remember the versions of the packages installed
		for (String pkg : pkgs) {
			String version = sshCommandRunner.runCommandAndWait("rpm -q "+pkg).getStdout().trim();
			installedPackageVersion.put(pkg,version);
		}
	}
	
	public void setupRhnDefinitions(String gitRepository) {
		if (gitRepository.equals("")) return;
		
		// git clone git://git.app.eng.bos.redhat.com/rcm/rhn-definitions.git
		log.info("Cloning Rhn Definitions...");
		/* git may is not always installed (e.g. RHEL5/epel s390,ia64), therefore stop asserting which causes entire setupBeforeSuite to fail.
		RemoteFileTasks.runCommandAndAssert(sshCommandRunner, "rm -rf "+rhnDefinitionsDir+" && mkdir "+rhnDefinitionsDir, new Integer(0));
		RemoteFileTasks.runCommandAndAssert(sshCommandRunner, "git clone "+gitRepository+" "+rhnDefinitionsDir, new Integer(0));
		*/
		sshCommandRunner.runCommandAndWait("rm -rf "+rhnDefinitionsDir+" && mkdir "+rhnDefinitionsDir);
		sshCommandRunner.runCommandAndWait("git clone "+gitRepository+" "+rhnDefinitionsDir);
		if (sshCommandRunner.getExitCode()!=0) log.warning("Encountered problems while cloning "+gitRepository+"; dependent tests will likely fail or skip.");
		
	}
	
	public void setupTranslateToolkit(String gitRepository) {
		if (gitRepository.equals("")) return;
		
		// git clone git://github.com/translate/translate.git
		log.info("Cloning Translate Toolkit...");
		final String translateToolkitDir	= "/tmp/"+"translateToolkitDir";
		RemoteFileTasks.runCommandAndAssert(sshCommandRunner, "rm -rf "+translateToolkitDir+" && mkdir "+translateToolkitDir, new Integer(0));
		/* git may is not always installed (e.g. RHEL5/epel s390,ia64), therefore stop asserting which causes entire setupBeforeSuite to fail.
		RemoteFileTasks.runCommandAndAssert(sshCommandRunner, "git clone "+gitRepository+" "+translateToolkitDir, new Integer(0));
		sshCommandRunner.runCommandAndWaitWithoutLogging("cd "+translateToolkitDir+" && ./setup.py install --force");
		RemoteFileTasks.runCommandAndAssert(sshCommandRunner, "which pofilter", new Integer(0));
		*/
		sshCommandRunner.runCommandAndWait("git clone "+gitRepository+" "+translateToolkitDir);
		sshCommandRunner.runCommandAndWait("cd "+translateToolkitDir+" && ./setup.py install --force");
		sshCommandRunner.runCommandAndWait("which pofilter");
		if (sshCommandRunner.getExitCode()!=0) log.warning("Encountered problems while installing pofilter; related tests will likely fail or skip.");
	}
	
	
	public void removeAllFacts() {
		log.info("Cleaning out facts from consumerCertDir: "+this.factsDir);
		if (!this.factsDir.startsWith("/etc/rhsm/")) log.warning("UNRECOGNIZED DIRECTORY.  NOT CLEANING FACTS FROM: "+this.factsDir);
		else sshCommandRunner.runCommandAndWait("rm -rf "+this.factsDir+"/*.facts");
	}
	
	public void removeAllCerts(boolean consumers, boolean entitlements, boolean products) {
		sshCommandRunner.runCommandAndWait("killall -9 yum");
		String certDir;
		
		if (consumers) {
			certDir = this.consumerCertDir;	// getConfigFileParameter("consumerCertDir");
			log.info("Cleaning out certs from consumerCertDir: "+certDir);
			if (!certDir.startsWith("/etc/pki/") && !certDir.startsWith("/tmp/")) log.warning("UNRECOGNIZED DIRECTORY.  NOT CLEANING CERTS FROM: "+certDir);
			else {
				sshCommandRunner.runCommandAndWait("rm -rf "+certDir+"/*");
				this.currentlyRegisteredUsername = null;
				this.currentlyRegisteredPassword = null;
				this.currentlyRegisteredOrg = null;
				this.currentlyRegisteredType = null;
			}
		}
		
		if (entitlements) {
			certDir = this.entitlementCertDir;	// getConfigFileParameter("entitlementCertDir");
			log.info("Cleaning out certs from entitlementCertDir: "+certDir);
			if (!certDir.startsWith("/etc/pki/") && !certDir.startsWith("/tmp/")) log.warning("UNRECOGNIZED DIRECTORY.  NOT CLEANING CERTS FROM: "+certDir);
			else sshCommandRunner.runCommandAndWait("rm -rf "+certDir+"/*");
		}
		
		if (products) {
			certDir = this.productCertDir;	// getConfigFileParameter("productCertDir");
			log.info("Cleaning out certs from productCertDir: "+certDir);
			if (!certDir.startsWith("/etc/pki/") && !certDir.startsWith("/tmp/")) log.warning("UNRECOGNIZED DIRECTORY.  NOT CLEANING CERTS FROM: "+certDir);
			else sshCommandRunner.runCommandAndWait("rm -rf "+certDir+"/*");
		}
	}
	
	public void removeRhnSystemIdFile() {
		//RemoteFileTasks.runCommandAndWait(sshCommandRunner, "rm -rf "+rhnSystemIdFile, TestRecords.action());
		sshCommandRunner.runCommandAndWait("rm -rf "+rhnSystemIdFile);
		
		// also do a yum clean all to avoid rhnplugin message: This system may not be registered to RHN Classic or RHN Satellite. SystemId could not be acquired.
		//RemoteFileTasks.runCommandAndWait(sshCommandRunner, "yum clean all", TestRecords.action());
		sshCommandRunner.runCommandAndWait("yum clean all");
	}
	
	public void updateYumRepo(String yumRepoFile, YumRepo yumRepo){
		log.info("Updating yumrepo file '"+yumRepoFile+"' repoid '"+yumRepo.id+"' to: "+yumRepo);
		// first, empty the contents of the current yumRepo
		// sed -i "/\[REPOID\]/,/\[/ s/^[^\[].*//" /etc/yum.repos.d/redhat.repo
		String command = String.format("sed -i \"/\\[%s\\]/,/\\[/ s/^[^\\[].*//\" %s", yumRepo.id, yumRepoFile);
		RemoteFileTasks.runCommandAndAssert(sshCommandRunner,command,Integer.valueOf(0));
		// then, add all of the non-null yumRepo parameters
		Map<String,String> parameterValueMap = new HashMap<String,String>();
		if (yumRepo.name!=null)						parameterValueMap.put("name", yumRepo.name);
		if (yumRepo.baseurl!=null)					parameterValueMap.put("baseurl", yumRepo.baseurl);
		if (yumRepo.enabled!=null)					parameterValueMap.put("enabled", yumRepo.enabled.toString());
		if (yumRepo.gpgcheck!=null)					parameterValueMap.put("gpgcheck", yumRepo.gpgcheck.toString());
		if (yumRepo.gpgkey!=null)					parameterValueMap.put("gpgkey", yumRepo.gpgkey);
		if (yumRepo.sslcacert!=null)				parameterValueMap.put("sslcacert", yumRepo.sslcacert);
		if (yumRepo.sslverify!=null)				parameterValueMap.put("sslverify", yumRepo.sslverify.toString());
		if (yumRepo.sslclientcert!=null)			parameterValueMap.put("sslclientcert", yumRepo.sslclientcert);
		if (yumRepo.sslclientkey!=null)				parameterValueMap.put("sslclientkey", yumRepo.sslclientkey);
		if (yumRepo.metadata_expire!=null)			parameterValueMap.put("metadata_expire", yumRepo.metadata_expire);
		if (yumRepo.metalink!=null)					parameterValueMap.put("metalink", yumRepo.metalink);
		if (yumRepo.mirrorlist!=null)				parameterValueMap.put("mirrorlist", yumRepo.mirrorlist);
		if (yumRepo.repo_gpgcheck!=null)			parameterValueMap.put("repo_gpgcheck", yumRepo.repo_gpgcheck.toString());
		if (yumRepo.gpgcakey!=null)					parameterValueMap.put("gpgcakey", yumRepo.gpgcakey);
		if (yumRepo.exclude!=null)					parameterValueMap.put("exclude", yumRepo.exclude);
		if (yumRepo.includepkgs!=null)				parameterValueMap.put("includepkgs", yumRepo.includepkgs);
		if (yumRepo.enablegroups!=null)				parameterValueMap.put("enablegroups", yumRepo.enablegroups.toString());
		if (yumRepo.failovermethod!=null)			parameterValueMap.put("failovermethod", yumRepo.failovermethod);
		if (yumRepo.keepalive!=null)				parameterValueMap.put("keepalive", yumRepo.keepalive.toString());
		if (yumRepo.timeout!=null)					parameterValueMap.put("timeout", yumRepo.timeout);
		if (yumRepo.http_caching!=null)				parameterValueMap.put("http_caching", yumRepo.http_caching);
		if (yumRepo.retries!=null)					parameterValueMap.put("retries", yumRepo.retries);
		if (yumRepo.throttle!=null)					parameterValueMap.put("throttle", yumRepo.throttle);
		if (yumRepo.bandwidth!=null)				parameterValueMap.put("bandwidth", yumRepo.bandwidth);
		if (yumRepo.mirrorlist_expire!=null)		parameterValueMap.put("mirrorlist_expire", yumRepo.mirrorlist_expire);
		if (yumRepo.proxy!=null)					parameterValueMap.put("proxy", yumRepo.proxy);
		if (yumRepo.proxy_username!=null)			parameterValueMap.put("proxy_username", yumRepo.proxy_username);
		if (yumRepo.proxy_password!=null)			parameterValueMap.put("proxy_password", yumRepo.proxy_password);
		if (yumRepo.username!=null)					parameterValueMap.put("username", yumRepo.username);
		if (yumRepo.password!=null)					parameterValueMap.put("password", yumRepo.password);
		if (yumRepo.cost!=null)						parameterValueMap.put("cost", yumRepo.cost);
		if (yumRepo.skip_if_unavailable!=null)		parameterValueMap.put("skip_if_unavailable", yumRepo.skip_if_unavailable.toString());
		if (yumRepo.priority!=null)					parameterValueMap.put("priority", yumRepo.priority.toString());
		addYumRepoParameters(yumRepoFile,yumRepo.id,parameterValueMap);
	}
	public void updateYumRepoParameter(String yumRepoFile, String repoid, String parameter, String value){
		log.info("Updating yumrepo file '"+yumRepoFile+"' repoid '"+repoid+"' parameter '"+parameter+"' value to: "+value);
//		String command = "sed -i \"/\\["+repoid+"\\]/,/\\[/ s/^"+parameter+"\\s*=.*/"+parameter+"="+value+"/\" "+yumRepoFile;
		String command = String.format("sed -i \"/\\[%s\\]/,/\\[/ s/^%s\\s*=.*/%s=%s/\" %s", repoid, parameter, parameter, value, yumRepoFile);
		RemoteFileTasks.runCommandAndAssert(sshCommandRunner,command,Integer.valueOf(0));
	}
	public void addYumRepoParameter(String yumRepoFile, String repoid, String parameter, String value){
		Map<String,String> parameterValueMap = new HashMap<String,String>();
		parameterValueMap.put(parameter, value);
		addYumRepoParameters(yumRepoFile,repoid,parameterValueMap);
	}
	public void addYumRepoParameters(String yumRepoFile, String repoid, Map<String,String> parameterValueMap){
		
		String a = "";
		for (String parameter:parameterValueMap.keySet()) {
			String value = parameterValueMap.get(parameter);
			log.info("Adding yumrepo file '"+yumRepoFile+"' repoid '"+repoid+"' option: "+parameter+"="+value);
			a += parameter+"="+value+"\\n";
		}
		if (parameterValueMap.size()>1) a = a.replaceFirst("\\\\n$", "");	// strip the trailing \n
		
		// sed  -i "/\[REPOID\]/ a F=bar\nG=tar\n" /etc/yum.repos.d/redhat.repo
		String command = String.format("sed -i \"/\\[%s\\]/ a %s\" %s", repoid, a, yumRepoFile);
		RemoteFileTasks.runCommandAndAssert(sshCommandRunner,command,Integer.valueOf(0));
	}
	
	public void updateConfFileParameter(String confFile, String parameter, String value){
		log.info("Updating config file '"+confFile+"' parameter '"+parameter+"' value to: "+value);
		Assert.assertEquals(
				RemoteFileTasks.searchReplaceFile(sshCommandRunner, confFile, "^"+parameter+"\\s*=.*$", parameter+"="+value.replaceAll("\\/", "\\\\/")),
				0,"Updated '"+confFile+"' parameter '"+parameter+"' to value '"+value+"'.");
		
		// also update this "cached" value for these config file parameters
		if (confFile.equals(this.rhsmConfFile)) {
			if (parameter.equals("consumerCertDir"))	this.consumerCertDir = value;
			if (parameter.equals("entitlementCertDir"))	this.entitlementCertDir = value;
			if (parameter.equals("productCertDir"))		this.productCertDir = value;
			if (parameter.equals("baseurl"))			this.baseurl = value;
			if (parameter.equals("ca_cert_dir"))		this.caCertDir = value;
		}
	}
	
	public void commentConfFileParameter(String confFile, String parameter){
		log.info("Commenting out config file '"+confFile+"' parameter: "+parameter);
		Assert.assertEquals(
				RemoteFileTasks.searchReplaceFile(sshCommandRunner, confFile, "^"+parameter+"\\s*=", "#"+parameter+"="),
				0,"Commented '"+confFile+"' parameter: "+parameter);
	}
	
	public void uncommentConfFileParameter(String confFile, String parameter){
		log.info("Uncommenting config file '"+confFile+"' parameter: "+parameter);
		Assert.assertEquals(
				RemoteFileTasks.searchReplaceFile(sshCommandRunner, confFile, "^#\\s*"+parameter+"\\s*=", parameter+"="),
				0,"Uncommented '"+confFile+"' parameter: "+parameter);
	}
	

	/**
	 * This method should be deleted and replaced with calls to getConfFileParameter(String confFile, String section, String parameter)
	 * @param confFile
	 * @param parameter
	 * @return
	 */
	public String getConfFileParameter(String confFile, String parameter){
		// Note: parameter can be case insensitive
//		SSHCommandResult result = RemoteFileTasks.runCommandAndAssert(sshCommandRunner, "grep -iE ^"+parameter+" "+confFile, 0/*, "^"+parameter, null*/);
		SSHCommandResult result = RemoteFileTasks.runCommandAndAssert(sshCommandRunner, String.format("grep -iE \"^%s *(=|:)\" %s",parameter,confFile), 0);	// tolerates = or : assignment character
		String value = result.getStdout().split("=|:",2)[1];
		return value.trim();
	}
	/**
	 * @param confFile
	 * @param section
	 * @param parameter
	 * @return value of the section.parameter config (null when not found)
	 */
	public String getConfFileParameter(String confFile, String section, String parameter){

		SSHCommandResult result = RemoteFileTasks.runCommandAndAssert(sshCommandRunner, "egrep -v  \"^\\s*(#|$)\" "+confFile, 0);
		
		//	[root@jsefler-onprem-62server ~]# egrep -v  "^\s*(#|$)" /etc/rhsm/rhsm.conf
		//	[server]
		//	hostname=jsefler-onprem-62candlepin.usersys.redhat.com
		//	prefix=/candlepin
		//	port=8443
		//	insecure=0
		//	ssl_verify_depth = 3
		//	ca_cert_dir=/etc/rhsm/ca/
		//	proxy_hostname =
		//	proxy_port = 
		//	proxy_user =
		//	proxy_password =
		//	[rhsm]
		//	baseurl=https://cdn.redhat.com
		//	repo_ca_cert=%(ca_cert_dir)sredhat-uep.pem
		//	productCertDir=/etc/pki/product
		//	entitlementCertDir=/etc/pki/entitlement
		//	consumercertdir=/etc/pki/consumer
		//	certfrequency=2400
		//	proxy_port = BAR
		//	[rhsmcertd]
		//	certFrequency=240
		
		// ^\[rhsm\](?:\n[^\[]*?)+^(?:consumerCertDir|consumercertdir)\s*[=:](.*)
		String parameterRegex = "(?:"+parameter+"|"+parameter.toLowerCase()+")";	// note: python may write and tolerate all lowercase parameter names
		String regex = "^\\["+section+"\\](?:\\n[^\\[]*?)+^"+parameterRegex+"\\s*[=:](.*)";
		Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
		Matcher matcher = pattern.matcher(result.getStdout());
		if (!matcher.find()) {
			log.warning("Did not find section '"+section+"' parameter '"+parameter+"' in conf file '"+confFile+"'.");
			return null;
		}

//		log.fine("Matches: ");
//		do {
//			log.fine(matcher.group());
//		} while (matcher.find());
		return matcher.group(1).trim();	// return the contents of the first capturing group
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
	
	

	/**
	 * Update the rhsmcertd frequency configurations in /etc/rhsm/rhsm.conf file and restart the rhsmcertd service.
	 * @param certFrequency - Frequency of certificate refresh (in minutes) (passing null will not change the current value)
	 * @param healFrequency - Frequency of subscription auto healing (in minutes) (passing null will not change the current value)
	 * @param waitForMinutes - after restarting, should we wait for the next certFrequency refresh? - TODO THIS PARAM IS IGNORED AS OF 07/16/2012
	 * @param assertCertificatesUpdate if NULL, do not wait for certificate updates; if TRUE, wait and assert rhsmcertd logs Certificates updated; if FALSE, wait and assert rhsmcertd logs Update failed
	 */
	public void restart_rhsmcertd (Integer certFrequency, Integer healFrequency, boolean waitForMinutes, Boolean assertCertificatesUpdate){
		
		// update the configuration for certFrequency and healFrequency
		//updateConfFileParameter(rhsmConfFile, "certFrequency", String.valueOf(certFrequency));
		//updateConfFileParameter(rhsmConfFile, "healFrequency", String.valueOf(healFrequency));
		// do it in one ssh call
		List<String[]> listOfSectionNameValues = new ArrayList<String[]>();
		if (certFrequency!=null) listOfSectionNameValues.add(new String[]{"rhsmcertd", "certFrequency".toLowerCase(), String.valueOf(certFrequency)});
		else certFrequency = Integer.valueOf(getConfFileParameter(rhsmConfFile, "rhsmcertd", "certFrequency"));
		if (healFrequency!=null) listOfSectionNameValues.add(new String[]{"rhsmcertd", "healFrequency".toLowerCase(), String.valueOf(healFrequency)});
		else healFrequency = Integer.valueOf(getConfFileParameter(rhsmConfFile, "rhsmcertd", "healFrequency"));
		if (listOfSectionNameValues.size()>0) config(null,null,true,listOfSectionNameValues);
		
		// mark the rhsmcertd log file before restarting the deamon
		String rhsmcertdLogMarker = System.currentTimeMillis()+" Testing service rhsmcertd restart...";
		RemoteFileTasks.markFile(sshCommandRunner, rhsmcertdLogFile, rhsmcertdLogMarker);
		
		// TEMPORARY WORKAROUND FOR BUG
		String bugId="804227"; //  Status: 	CLOSED ERRATA
		boolean invokeWorkaroundWhileBugIsOpen = false;
		try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			throw new SkipException("Restart rhsmcertd has no workaround for Bugzilla "+bugId+".");
		}
		// END OF WORKAROUND
		
		//	[root@jsefler-63server ~]# service rhsmcertd restart
		//	Stopping rhsmcertd                                         [  OK  ]
		//	Starting rhsmcertd 2 1440                                  [  OK  ]

		// TEMPORARY WORKAROUND FOR BUG
		if (this.arch.equals("s390x") || this.arch.equals("ppc64")) {
			bugId="691137";	// Status: 	CLOSED ERRATA
			invokeWorkaroundWhileBugIsOpen = false;
			try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
			if (invokeWorkaroundWhileBugIsOpen) {
				RemoteFileTasks.runCommandAndWait(sshCommandRunner,"service rhsmcertd restart", TestRecords.action());
			} else {
				/* VALID PRIOR TO BUG 818978:
				 * RemoteFileTasks.runCommandAndAssert(sshCommandRunner,"service rhsmcertd restart",Integer.valueOf(0),"^Starting rhsmcertd "+certFrequency+" "+healFrequency+"\\[  OK  \\]$",null);
				 */
			}
		} else {
		// END OF WORKAROUND
			
		/* VALID PRIOR TO BUG 818978:
		 * RemoteFileTasks.runCommandAndAssert(sshCommandRunner,"service rhsmcertd restart",Integer.valueOf(0),"^Starting rhsmcertd "+certFrequency+" "+healFrequency+"\\[  OK  \\]$",null);
		 */
		}
		
		// NEW SERVICE RESTART FEEDBACK AFTER IMPLEMENTATION OF Bug 818978 - Missing systemD unit file
		//	[root@jsefler-59server ~]# service rhsmcertd restart
		//	Stopping rhsmcertd...                                      [  OK  ]
		//	Starting rhsmcertd...                                      [  OK  ]
		RemoteFileTasks.runCommandAndAssert(sshCommandRunner,"service rhsmcertd restart",Integer.valueOf(0),"^Starting rhsmcertd\\.\\.\\.\\[  OK  \\]$",null);	

		
		// # service rhsmcertd restart
		// rhsmcertd (pid 10172 10173) is running...
		
		//RemoteFileTasks.runCommandAndAssert(sshCommandRunner,"service rhsmcertd status",Integer.valueOf(0),"^rhsmcertd \\(pid \\d+ \\d+\\) is running...$",null);	// RHEL62 branch
		RemoteFileTasks.runCommandAndAssert(sshCommandRunner,"service rhsmcertd status",Integer.valueOf(0),"^rhsmcertd \\(pid \\d+\\) is running...$",null);		// master/RHEL58 branch
		//RemoteFileTasks.runCommandAndAssert(sshCommandRunner,"service rhsmcertd status",Integer.valueOf(0),"^rhsmcertd \\(pid( \\d+){1,2}\\) is running...$",null);	// tolerate 1 or 2 pids for RHEL62 or RHEL58; don't really care which it is since the next assert is really sufficient

		// # tail -f /var/log/rhsm/rhsmcertd.log
		// Wed Nov  9 15:21:54 2011: started: interval = 1440 minutes
		// Wed Nov  9 15:21:54 2011: started: interval = 240 minutes
		// Wed Nov  9 15:21:55 2011: certificates updated
		// Wed Nov  9 15:21:55 2011: certificates updated
		
		// TEMPORARY WORKAROUND FOR BUG
		/*boolean*/ invokeWorkaroundWhileBugIsOpen = false; // Current bug status is: CLOSED ERRATA; setting invokeWorkaroundWhileBugIsOpen to false to save execution time
		String bugId1="752572";	// Status: 	CLOSED ERRATA
		String bugId2="759199";	// Status: 	CLOSED ERRATA
		invokeWorkaroundWhileBugIsOpen = false;
		try {if (invokeWorkaroundWhileBugIsOpen&&(BzChecker.getInstance().isBugOpen(bugId1)||BzChecker.getInstance().isBugOpen(bugId2))) {log.fine("Invoking workaround for Bugzillas:  https://bugzilla.redhat.com/show_bug.cgi?id="+bugId1+" https://bugzilla.redhat.com/show_bug.cgi?id="+bugId2);} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			log.warning("Skipping assert of the rhsmcertd logging of the started: interval certFrequency and healFrequency while bug "+bugId1+" or "+bugId2+" is open.");
		} else {
		// END OF WORKAROUND
		//RemoteFileTasks.runCommandAndAssert(sshCommandRunner,"tail -4 "+rhsmcertdLogFile,Integer.valueOf(0),"(.*started: interval = "+healFrequency+" minutes\n.*started: interval = "+certFrequency+" minutes)|(.*started: interval = "+certFrequency+" minutes\n.*started: interval = "+healFrequency+" minutes)",null);
		//RemoteFileTasks.runCommandAndAssert(sshCommandRunner,"tail -4 "+rhsmcertdLogFile,Integer.valueOf(0),".* healing check started: interval = "+healFrequency+"\n.* cert check started: interval = "+certFrequency,null);
		/* VALID PRIOR TO BUG 818978:
		 * RemoteFileTasks.runCommandAndAssert(sshCommandRunner,"tail -4 "+rhsmcertdLogFile,Integer.valueOf(0),".* healing check started: interval = "+healFrequency+" minute\\(s\\)\n.* cert check started: interval = "+certFrequency+" minute\\(s\\)",null);
		 */
		}
		
		// give the rhsmcertd time to make its initial check-in with the candlepin server and update the certs
		// I've seen this take 10 to 15 seconds as demonstrated here...
		
		// when registered...
		//	1334786048260 Testing service rhsmcertd restart...
		//	Wed Apr 18 17:54:11 2012: healing check started: interval = 1440
		//	Wed Apr 18 17:54:11 2012: cert check started: interval = 240
		//	Wed Apr 18 17:54:21 2012: certificates updated
		//	Wed Apr 18 17:54:26 2012: certificates updated
		
		// when not registered...
		//	1341610172422 Testing service rhsmcertd restart...
		//	Fri Jul  6 17:30:48 2012: Loading configuration from command line
		//	Fri Jul  6 17:30:48 2012: Cert Frequency: 14400 seconds
		//	Fri Jul  6 17:30:48 2012: Heal Frequency: 86400 seconds
		//	Fri Jul  6 17:30:48 2012: healing check started: interval = 1440 minute(s)
		//	Fri Jul  6 17:30:48 2012: cert check started: interval = 240 minute(s)
		//	Fri Jul  6 17:30:48 2012: update failed (255), retry will occur on next run
		//	Fri Jul  6 17:30:49 2012: update failed (255), retry will occur on next run


		// assert the rhsmcertd log file reflected newly updated certificates...
		/* VALID PRIOR TO BUG 818978:
		int i=0, delay=10;
		String rhsmcertdLogResult,updateRegex;
		if (this.currentlyRegisteredUsername==null)	updateRegex = ".*update failed \\(255\\), retry will occur on next run\\n.*update failed \\(255\\), retry will occur on next run";	// when NOT registered
		else										updateRegex = ".*certificates updated\\n.*certificates updated";
		do {	// retry every 10 seconds (up to a minute) for the expected update certificates regex in the rhsmcertd log
			SubscriptionManagerCLITestScript.sleep(delay*1000);i++;	// wait a few seconds before trying again
			rhsmcertdLogResult = RemoteFileTasks.getTailFromMarkedFile(sshCommandRunner, rhsmcertdLogFile, rhsmcertdLogMarker, "update").trim();
			if (rhsmcertdLogResult.matches(updateRegex)) break;
		} while (delay*i < 60);
		Assert.assertTrue(rhsmcertdLogResult.matches(updateRegex), "Expected certificate update regex '"+updateRegex+"' is being logged to rhsmcertd log during a restart.");
		*/
		
		//	[root@jsefler-59server ~]# tail -f /var/log/rhsm/rhsmcertd.log
		//  1342466941476 Testing service rhsmcertd restart...
		//	Mon Jul 16 13:23:56 2012 [INFO] rhsmcertd is shutting down...
		//	Mon Jul 16 13:23:56 2012 [INFO] Starting rhsmcertd...
		//	Mon Jul 16 13:23:56 2012 [INFO] Healing interval: 1440.0 minute(s) [86400 second(s)]
		//	Mon Jul 16 13:23:56 2012 [INFO] Cert check interval: 2.0 minute(s) [120 second(s)]
		//	Mon Jul 16 13:23:56 2012 [INFO] Waiting 120 second(s) [2.0 minute(s)] before running updates.
		//	Mon Jul 16 13:25:56 2012 [WARN] (Healing) Update failed (255), retry will occur on next run.
		//	Mon Jul 16 13:25:57 2012 [WARN] (Cert Check) Update failed (255), retry will occur on next run.
		//	Mon Jul 16 13:27:56 2012 [WARN] (Cert Check) Update failed (255), retry will occur on next run.
		
		//  1342466941944 Testing service rhsmcertd restart...
		//	Mon Jul 16 14:30:20 2012 [INFO] rhsmcertd is shutting down...
		//	Mon Jul 16 14:30:20 2012 [INFO] Starting rhsmcertd...
		//	Mon Jul 16 14:30:20 2012 [INFO] Healing interval: 1440.0 minute(s) [86400 second(s)]
		//	Mon Jul 16 14:30:20 2012 [INFO] Cert check interval: 2.0 minute(s) [120 second(s)]
		//	Mon Jul 16 14:30:20 2012 [INFO] Waiting 120 second(s) [2.0 minute(s)] before running updates.
		//	Mon Jul 16 14:32:25 2012 [INFO] (Healing) Certificates updated.
		//	Mon Jul 16 14:32:29 2012 [INFO] (Cert Check) Certificates updated.
		//	Mon Jul 16 14:34:22 2012 [INFO] (Cert Check) Certificates updated.

		String rhsmcertdLogResult = RemoteFileTasks.getTailFromMarkedFile(sshCommandRunner, rhsmcertdLogFile, rhsmcertdLogMarker, null).trim();
		Integer hardWaitForFirstUpdateCheck = 120; // this is a dev hard coded wait (seconds) before the first check for updates is attempted  REFERENCE BUG 818978#c2
		String rhsmcertdLogResultExpected;
		rhsmcertdLogResultExpected = String.format(" Starting rhsmcertd...");																										Assert.assertTrue(rhsmcertdLogResult.contains(rhsmcertdLogResultExpected),"Tail of rhsmcertd log contains the expected restart message '"+rhsmcertdLogResultExpected+"'.");
		rhsmcertdLogResultExpected = String.format(" Healing interval: %.1f minute(s) [%d second(s)]",healFrequency*1.0,healFrequency*60);											Assert.assertTrue(rhsmcertdLogResult.contains(rhsmcertdLogResultExpected),"Tail of rhsmcertd log contains the expected restart message '"+rhsmcertdLogResultExpected+"'.");
		rhsmcertdLogResultExpected = String.format(" Cert check interval: %.1f minute(s) [%d second(s)]",certFrequency*1.0,certFrequency*60);										Assert.assertTrue(rhsmcertdLogResult.contains(rhsmcertdLogResultExpected),"Tail of rhsmcertd log contains the expected restart message '"+rhsmcertdLogResultExpected+"'.");
		rhsmcertdLogResultExpected = String.format(" Waiting %d second(s) [%.1f minute(s)] before running updates.",hardWaitForFirstUpdateCheck,hardWaitForFirstUpdateCheck/60.0 );	Assert.assertTrue(rhsmcertdLogResult.contains(rhsmcertdLogResultExpected),"Tail of rhsmcertd log contains the expected restart message '"+rhsmcertdLogResultExpected+"'.");

		/* IGNORED
		if (waitForMinutes && certFrequency!=null) {
			SubscriptionManagerCLITestScript.sleep(certFrequency*60*1000);
		}
		*/
		
		// assert the rhsmcertd log for messages stating the cert and heal frequencies have be logged
		if (assertCertificatesUpdate!=null) {
			// Waiting 120 second(s) [2.0 minute(s)] before running updates.
			SubscriptionManagerCLITestScript.sleep(hardWaitForFirstUpdateCheck*1000);
			
			// assert these cert and heal update/fail messages are logged (but give the system up to a minute to do it)
			String healMsg = assertCertificatesUpdate? "(Healing) Certificates updated.":"(Healing) Update failed (255), retry will occur on next run.";
			String certMsg = assertCertificatesUpdate? "(Cert Check) Certificates updated.":"(Cert Check) Update failed (255), retry will occur on next run.";
			int i=0, delay=10;
			do {	// retry every 10 seconds (up to a minute) for the expected update messages in the rhsmcertd log
				SubscriptionManagerCLITestScript.sleep(delay*1000);i++;	// wait a few seconds before trying again
				rhsmcertdLogResult = RemoteFileTasks.getTailFromMarkedFile(sshCommandRunner, rhsmcertdLogFile, rhsmcertdLogMarker, null).trim();
				if (rhsmcertdLogResult.contains(healMsg) && rhsmcertdLogResult.contains(certMsg)) break;
			} while (delay*i < 60);
			
			// TEMPORARY WORKAROUND FOR BUG
			bugId="861443"; // Bug 861443 - rhsmcertd logging of Healing shows "Certificates updated." when it should fail.
			invokeWorkaroundWhileBugIsOpen = true;
			try {if (!assertCertificatesUpdate&&invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
			if (!assertCertificatesUpdate&&invokeWorkaroundWhileBugIsOpen) {
				log.warning("Skipping assertion: "+"Tail of rhsmcertd log contains the expected restart message '"+healMsg+"'.");
			} else
			// END OF WORKAROUND
			Assert.assertTrue(rhsmcertdLogResult.contains(healMsg),"Tail of rhsmcertd log contains the expected restart message '"+healMsg+"'.");
			Assert.assertTrue(rhsmcertdLogResult.contains(certMsg),"Tail of rhsmcertd log contains the expected restart message '"+certMsg+"'.");
		}
	}
	
	public void stop_rhsmcertd (){
		RemoteFileTasks.runCommandAndAssert(sshCommandRunner,"service rhsmcertd stop",Integer.valueOf(0));
//		RemoteFileTasks.runCommandAndAssert(sshCommandRunner,"service rhsmcertd status",Integer.valueOf(0),"^rhsmcertd is stopped$",null);
		RemoteFileTasks.runCommandAndAssert(sshCommandRunner,"service rhsmcertd status",Integer.valueOf(3),"^rhsmcertd is stopped$",null);  // exit code 3 = program not running		// reference Bug 232163; Bug 679812
	}
	
	public void waitForRegexInRhsmcertdLog(String logRegex, int timeoutMinutes) {
		RemoteFileTasks.runCommandAndAssert(sshCommandRunner,"tail -1 "+rhsmcertdLogFile,Integer.valueOf(0));
		int retryMilliseconds = Integer.valueOf(getConfFileParameter(rhsmConfFile, "certFrequency"))*60*1000;  // certFrequency is in minutes
		int t = 0;
		
		while(!sshCommandRunner.runCommandAndWait("tail -1 "+rhsmcertdLogFile).getStdout().trim().matches(logRegex) && (t*retryMilliseconds < timeoutMinutes*60*1000)) {
			// pause for the sleep interval
			SubscriptionManagerCLITestScript.sleep(retryMilliseconds); t++;	
		}
		if (t*retryMilliseconds > timeoutMinutes*60*1000) sshCommandRunner.runCommandAndWait("tail -24 "+rhsmLogFile);
		
		// assert that the state was achieved within the timeout
		Assert.assertFalse((t*retryMilliseconds > timeoutMinutes*60*1000), "The rhsmcertd log matches '"+logRegex+"' within '"+t*retryMilliseconds+"' milliseconds (timeout="+timeoutMinutes+" min)");
	}
		
	
	

	/**
	 * @return the current service level returned by subscription-manager service-level --show (must already be registered); will return an empty string when the service level preference is not set.
	 */
	public String getCurrentServiceLevel() {
		
		SSHCommandResult result = service_level(true, false, null, null, null, null, null, null, null, null, null);
		
		// [root@jsefler-r63-server ~]# subscription-manager service-level --show
		// Current service level: Standard
		//
		// [root@jsefler-r63-server ~]# subscription-manager service-level --show
		// Current service level: 
		
		// [root@jsefler-59server ~]# subscription-manager service-level --show --list
		// Service level preference not set
		// +-------------------------------------------+
		//                Available Service Levels
		// +-------------------------------------------+
		// PREMIUM
		// STANDARD
		// NONE
		// [root@jsefler-59server ~]# 


		String serviceLevel = result.getStdout().split("\\+-+\\+")[0].replaceFirst(".*:", "").trim();
		serviceLevel = serviceLevel.replace("Service level preference not set", "");	// decided not to use serviceLevel=null when the service level is not set because the json value of the consumer's service level will be "" instead of null which effectively means the service level is not set.

		return serviceLevel;
	}
	
	/**
	 * @return the current release returned by subscription-manager release (must already be registered)
	 */
	public String getCurrentRelease() {
		
		SSHCommandResult result = release(null, null, null, null, null, null, null);
		
		//	[root@jsefler-r63-server ~]# subscription-manager release
		//	Release: foo
		//	[root@jsefler-r63-server ~]# subscription-manager release
		//	Release not set

		String release = result.getStdout().replaceFirst(".*:", "").replaceFirst("Release not set", "").trim();
		
		return release;
	}
	
	/**
	 * @return list of the service labels returned by subscription-manager service-level --list (must already be registered)
	 */
	public List<String> getCurrentlyAvailableServiceLevels() {
		
		SSHCommandResult result = service_level_(false, true, null, null, null, null, null, null, null, null, null);
		
		List<String> serviceLevels = new ArrayList<String>();
		if (!result.getExitCode().equals(Integer.valueOf(0))) return serviceLevels;

		//	ssh root@margo.idm.lab.bos.redhat.com subscription-manager service-level --list
		//	Stdout: This org does not have any subscriptions with service levels.
		//	Stderr:
		//	ExitCode: 0
		if (result.getStdout().trim().equals("This org does not have any subscriptions with service levels.")) return serviceLevels;

		
		//	[root@jsefler-r63-server ~]# subscription-manager service-level --list
		//	+-------------------------------------------+
		//	          Available Service Levels
		//	+-------------------------------------------+
		//	Standard
		//	None
		//	Premium
		for (String serviceLevel : result.getStdout().split("\\+-+\\+")[result.getStdout().split("\\+-+\\+").length-1].trim().split("\\n")) {
			serviceLevels.add(serviceLevel);
		}
		
		return serviceLevels;
	}
	
	/**
	 * @param proxy TODO
	 * @param proxyusername TODO
	 * @param proxypassword TODO
	 * @return list of the releases returned by subscription-manager release --list (must already be registered)
	 */
	public List<String> getCurrentlyAvailableReleases(String proxy, String proxyusername, String proxypassword) {
		
		SSHCommandResult result = release_(null,true,null,null,proxy, proxyusername, proxypassword);
		String stdout = result.getStdout().trim();
		
		//	[root@jsefler-r63-workstation ~]# subscription-manager release --list
		//	5.7
		//	5.8
		//	5Client
		//	6.0
		//	6.1
		//	6.2
		//	6Workstation
		
		// Bug 824979 - No message for "subscription-manager release --list" with no subscriptions
		// result when no releases are available
		// FINE: ssh root@jsefler-59server.usersys.redhat.com subscription-manager release --list
		// FINE: Stdout: 
		// FINE: Stderr: No release versions available, please check subscriptions.
		// FINE: ExitCode: 255
		
		// Bug 808217 - [RFE] a textural output banner would be nice for subscription-manager release --list
		//	[root@jsefler-6 ~]# subscription-manager release --list
		//	+-------------------------------------------+
		//	          Available Releases
		//	+-------------------------------------------+
		//	6.1
		//	6.2
		//	6.3
		//	6Server
		
		// strip off the banner (added by bug 808217 in RHEL64)
		String bannerRegex = "\\+-+\\+\\n\\s*Available Releases\\s*\\n\\+-+\\+";
		stdout = stdout.replaceFirst(bannerRegex, "");

		List<String> releases =  new ArrayList<String>();
		for (String release : stdout.split("\\s*\\n\\s*")) {
			if (!release.isEmpty())	releases.add(release);
		}
		
		return releases;
	}
	
	
	/**
	 * @return list of the expected releases currently available based on the currently enabled repo content and this major RHEL release
	 */
	public List<String> getCurrentlyExpectedReleases() {
		HashSet<String> expectedReleaseSet = new HashSet<String>();
		String baseurl = getConfFileParameter(rhsmConfFile, "rhsm", "baseurl");
		List<ProductCert> productCerts = getCurrentProductCerts();
		
		// loop through all of the currently entitled repo urls
		for (EntitlementCert entitlementCert : getCurrentEntitlementCerts()) {
			for (ContentNamespace contentNamespace : entitlementCert.contentNamespaces) {
				if (contentNamespace.type.equalsIgnoreCase("yum")) {
					if (contentNamespace.enabled) {	// Bug 820639 - subscription-manager release --list should exclude listings from disabled repos
						if (areAllRequiredTagsInContentNamespaceProvidedByProductCerts(contentNamespace, productCerts)) {	// Bug 861151 - subscription-manager release doesn't take variant into account 
							if (contentNamespace.downloadUrl.contains("$releasever")) {
								if (contentNamespace.downloadUrl.contains("/"+redhatReleaseX+"/")) {	// Bug 818298 - subscription-manager release --list should not display releasever applicable to rhel-5 when only rhel-6 product is installed
									// example contentNamespace.downloadUrl:  /content/dist/rhel/server/5/$releasever/$basearch/iso
									String listingUrl =  contentNamespace.downloadUrl.startsWith("http")? "":baseurl;
									listingUrl += contentNamespace.downloadUrl.split("/\\$releasever/")[0];
									listingUrl += "/listing";
									String command = String.format("curl --stderr /dev/null --insecure --tlsv1 --cert %s --key %s %s" , entitlementCert.file.getPath(), getEntitlementCertKeyFileCorrespondingToEntitlementCertFile(entitlementCert.file).getPath(), listingUrl);
									SSHCommandResult result = sshCommandRunner.runCommandAndWaitWithoutLogging(command);
									//	[root@qe-blade-13 ~]# curl --stderr /dev/null --insecure --tlsv1 --cert /etc/pki/entitlement/2013167262444796312.pem --key /etc/pki/entitlement/2013167262444796312-key.pem https://cdn.rcm-qa.redhat.com/content/dist/rhel/server/6/listing
									//	6.1
									//	6.2
									//	6Server
									
									//	[root@jsefler-6 ~]# curl --stderr /dev/null --insecure --tlsv1 --cert /etc/pki/entitlement/3360706382464344965.pem --key /etc/pki/entitlement/3360706382464344965-key.pem https://cdn.redhat.com/content/dist/rhel/server/6/listing
									//	<HTML><HEAD>
									//	<TITLE>Access Denied</TITLE>
									//	</HEAD><BODY>
									//	<H1>Access Denied</H1>
									if (result.getStdout().toUpperCase().contains("<HTML>")) {
										log.warning("curl result: "+result);	// or should this be a failure?
										Assert.fail("Expected to retrieve a list of available release versions. (Example: 6.1, 6.2, 6Server)");
									} else {
										expectedReleaseSet.addAll(Arrays.asList(result.getStdout().trim().split("\\s*\\n\\s*")));
									}
								}
							}
						}
					}
				}
			}
		}
		return new ArrayList<String>(expectedReleaseSet);
		
		// ^^ TODO On second thought, it would technically be more correct to loop over the current YumRepo object rather than the Entitlement Certs since a repo enablement could have been manually overridden
// TODO work in progress
//		for (YumRepo yumRepo : getCurrentlySubscribedYumRepos()()) {
//					if (yumRepo.enabled) {	// Bug 820639 - subscription-manager release --list should exclude listings from disabled repos
//						if (yumRepo.baseurl.contains("$releasever")) {
//							if (yumRepo.baseurl.contains("/"+redhatReleaseX+"/")) {	// Bug 818298 - subscription-manager release --list should not display releasever applicable to rhel-5 when only rhel-6 product is installed
//								// example contentNamespace.downloadUrl:  /content/dist/rhel/server/5/$releasever/$basearch/iso
//								String listingUrl =  yumRepo.baseurl.startsWith("http")? "":baseurl;
//								listingUrl += yumRepo.baseurl.split("/\\$releasever/")[0];
//								listingUrl += "/listing";
//								String command = String.format("curl --stderr /dev/null --insecure --cert %s --key %s %s" , entitlementCert.file.getPath(), getEntitlementCertKeyFileCorrespondingToEntitlementCertFile(entitlementCert.file).getPath(), listingUrl);
//								SSHCommandResult result = sshCommandRunner.runCommandAndWaitWithoutLogging(command);
//								//	[root@qe-blade-13 ~]# curl --stderr /dev/null --insecure --cert /etc/pki/entitlement/2013167262444796312.pem --key /etc/pki/entitlement/2013167262444796312-key.pem https://cdn.rcm-qa.redhat.com/content/dist/rhel/server/6/listing
//								//	6.1
//								//	6.2
//								//	6Server
//								expectedReleaseSet.addAll(Arrays.asList(result.getStdout().trim().split("\\s*\\n\\s*")));
//							}
//						}
//					}
//		}
	}
	
	
	
	
	public List<SubscriptionPool> getCurrentlyAvailableSubscriptionPools(String providingProductId, String serverUrl) throws JSONException, Exception {
		List<SubscriptionPool> subscriptionPoolsProvidingProductId = new ArrayList<SubscriptionPool>();
		
		for (SubscriptionPool subscriptionPool : getCurrentlyAvailableSubscriptionPools()) {
			if (CandlepinTasks.getPoolProvidedProductIds(currentlyRegisteredUsername, currentlyRegisteredPassword, serverUrl, subscriptionPool.poolId).contains(providingProductId)) {
				subscriptionPoolsProvidingProductId.add(subscriptionPool);
			}
		}
		return subscriptionPoolsProvidingProductId;
	}
	
	/**
	 * @return list of objects representing the subscription-manager list --available
	 */
	public List<SubscriptionPool> getCurrentlyAvailableSubscriptionPools() {
		return SubscriptionPool.parse(listAvailableSubscriptionPools().getStdout());
	}
	
	/**
	 * @return list of objects representing the subscription-manager list --all --available
	 */
	public List<SubscriptionPool> getCurrentlyAllAvailableSubscriptionPools() {
		return SubscriptionPool.parse(listAllAvailableSubscriptionPools().getStdout());
	}
	
	/**
	 * @return list of objects representing the subscription-manager list --consumed
	 */
	public List<ProductSubscription> getCurrentlyConsumedProductSubscriptions() {
		return ProductSubscription.parse(listConsumedProductSubscriptions().getStdout());
	}
	/**
	 * @return list of objects representing the subscription-manager list --avail --ondate
	 */
	public List<SubscriptionPool> getAvailableFutureSubscriptionsOndate(String onDateToTest) {
		return SubscriptionPool.parse(list_(null, true, null, null, null, onDateToTest, null, null, null).getStdout());
	}
	
	/**
	 * @return list of objects representing the subscription-manager repos --list
	 */
	public List<Repo> getCurrentlySubscribedRepos() {
		return Repo.parse(listSubscribedRepos().getStdout());
	}
	
	/**
	 * @return list of objects representing the Red Hat Repositories from /etc/yum.repos.d/redhat.repo
	 */
	public List<YumRepo> getCurrentlySubscribedYumRepos() {
		// trigger a yum transaction so that subscription-manager plugin will refresh redhat.repo
		//sshCommandRunner.runCommandAndWait("killall -9 yum"); // is this needed?
		//sshCommandRunner.runCommandAndWait("yum repolist all --disableplugin=rhnplugin"); // --disableplugin=rhnplugin helps avoid: up2date_client.up2dateErrors.AbuseError
		sshCommandRunner.runCommandAndWait("yum -q repolist --disableplugin=rhnplugin"); // --disableplugin=rhnplugin helps avoid: up2date_client.up2dateErrors.AbuseError
		
		return YumRepo.parse(sshCommandRunner.runCommandAndWait("cat "+redhatRepoFile).getStdout());
	}
	
	/**
	 * @return list of objects representing the subscription-manager list --installed
	 */
	public List<InstalledProduct> getCurrentlyInstalledProducts() {
		return InstalledProduct.parse(listInstalledProducts().getStdout());
	}
	
	@Deprecated
	public List<EntitlementCert> getCurrentEntitlementCertsUsingOpensslX509() {

		// THIS ORIGINAL IMPLEMENTATION HAS BEEN THROWING A	java.lang.StackOverflowError
		// REIMPLEMENTING THIS METHOD TO HELP BREAK THE PROBLEM DOWN INTO SMALLER PIECES - jsefler 11/23/2010
//		sshCommandRunner.runCommandAndWait("find "+entitlementCertDir+" -name '*.pem' | grep -v key.pem | xargs -I '{}' openssl x509 -in '{}' -noout -text");
//		String certificates = sshCommandRunner.getStdout();
//		return EntitlementCert.parse(certificates);

		// STACK OVERFLOW PROBLEM FIXED
//		List<EntitlementCert> entitlementCerts = new ArrayList<EntitlementCert>();
//		for (File entitlementCertFile : getCurrentEntitlementCertFiles()) {
//			entitlementCerts.add(getEntitlementCertFromEntitlementCertFile(entitlementCertFile));
//		}
//		return entitlementCerts;
		
		//sshCommandRunner.runCommandAndWait("find "+entitlementCertDir+" -name '*.pem' | grep -v key.pem | xargs -I '{}' openssl x509 -in '{}' -noout -text");
		sshCommandRunner.runCommandAndWaitWithoutLogging("find "+entitlementCertDir+" -regex \".*/[0-9]+.pem\" -exec openssl x509 -in '{}' -noout -text \\; -exec echo \"    File: {}\" \\;");
		String certificates = sshCommandRunner.getStdout();
		return EntitlementCert.parseStdoutFromOpensslX509(certificates);
	}
	public List<EntitlementCert> getCurrentEntitlementCerts() {
		sshCommandRunner.runCommandAndWaitWithoutLogging("find "+entitlementCertDir+" -regex \"/.+/[0-9]+.pem\" -exec rct cat-cert {} \\;");
		String certificates = sshCommandRunner.getStdout();
		return EntitlementCert.parse(certificates);
	}
	
	public List<ProductCert> getCurrentProductCerts() {
		return getProductCerts(productCertDir);
	}
	public List<ProductCert> getProductCertsUsingOpensslX509(String fromProductCertDir) {
		/* THIS ORIGINAL IMPLEMENTATION DID NOT INCLUDE THE FILE IN THE OBJECT
		sshCommandRunner.runCommandAndWaitWithoutLogging("find "+productCertDir+" -name '*.pem' | xargs -I '{}' openssl x509 -in '{}' -noout -text");
		String certificates = sshCommandRunner.getStdout();
		return ProductCert.parse(certificates);
		*/
		/*
		List<ProductCert> productCerts = new ArrayList<ProductCert>();
		for (File productCertFile : getCurrentProductCertFiles()) {
			productCerts.add(ProductCert.parse(sshCommandRunner, productCertFile));
		}
		return productCerts;
		*/
		sshCommandRunner.runCommandAndWaitWithoutLogging("find "+fromProductCertDir+" -name '*.pem' -exec openssl x509 -in '{}' -noout -text \\; -exec echo \"    File: {}\" \\;");
		String certificates = sshCommandRunner.getStdout();
		return ProductCert.parse(certificates);
		
	}
	public List<ProductCert> getProductCerts(String fromProductCertDir) {
		sshCommandRunner.runCommandAndWaitWithoutLogging("find "+fromProductCertDir+" -name '*.pem' -exec rct cat-cert {} \\;");
		String certificates = sshCommandRunner.getStdout();
		return ProductCert.parse(certificates);
		
	}
	public List<ProductCert> getCurrentProductCerts(String providingTag) {
		List<ProductCert> prodctCertsProvidingTag = new ArrayList<ProductCert>();
		for (ProductCert productCert : getCurrentProductCerts()) {
			if (productCert.productNamespace.providedTags==null) continue;
			List<String> providedTags = Arrays.asList(productCert.productNamespace.providedTags.split("\\s*,\\s*"));
			if (providedTags.contains(providingTag)) prodctCertsProvidingTag.add(productCert);
		}
		return prodctCertsProvidingTag;
	}

	/**
	 * @return the currently installed ProductCert that provides tag "rhel-5" or "rhel-6" depending on this redhat-release;
	 * also asserts that at most only one product cert is installed that provides this tag;
	 * returns null if not found
	 */
	public ProductCert getCurrentRhelProductCert() {
		// get the current base RHEL product cert
		String providingTag = "rhel-"+redhatReleaseX;
		List<ProductCert> rhelProductCerts = getCurrentProductCerts(providingTag);
		Assert.assertTrue(rhelProductCerts.size()<=1, "No more than one product cert is installed that provides RHEL tag '"+providingTag+"' (actual='"+rhelProductCerts.size()+"').");
		if (rhelProductCerts.isEmpty()) return null; 
		return rhelProductCerts.get(0);
	}
	
	public boolean isRhelProductCertSubscribed() {
		ProductCert rhelProductCert=getCurrentRhelProductCert();
		if (rhelProductCert==null) return false;	// rhel product cert cannot be subscribed if a rhel product cert is not installed
		InstalledProduct installedRhelProduct = InstalledProduct.findFirstInstanceWithMatchingFieldFromList("productId", rhelProductCert.productId, getCurrentlyInstalledProducts());
		if (installedRhelProduct==null) Assert.fail("Could not find the installed product corresponding to the current RHEL product cert: "+rhelProductCert);
		return installedRhelProduct.status.equals("Subscribed");
	}
	
	/**
	 * @return a ConsumerCert object corresponding to the current identity certificate parsed from the output of: openssl x509 -noout -text -in /etc/pki/consumer/cert.pem
	 */
	public ConsumerCert getCurrentConsumerCertUsingOpensslX509() {
		if (!RemoteFileTasks.testExists(sshCommandRunner, this.consumerCertFile())) {
			log.info("Currently, there is no consumer registered.");
			return null;
		}
		sshCommandRunner.runCommandAndWaitWithoutLogging("openssl x509 -noout -text -in "+this.consumerCertFile());
		String certificate = sshCommandRunner.getStdout();
		return ConsumerCert.parseStdoutFromOpensslX509(certificate);
	}
	/**
	 * @return a ConsumerCert object corresponding to the current identity certificate parsed from the output of: rct cat-cert /etc/pki/consumer/cert.pem
	 */
	public ConsumerCert getCurrentConsumerCert() {
		if (!RemoteFileTasks.testExists(sshCommandRunner, this.consumerCertFile())) {
			log.info("Currently, there is no consumer registered.");
			return null;
		}
		sshCommandRunner.runCommandAndWaitWithoutLogging("rct cat-cert "+this.consumerCertFile());
		String certificate = sshCommandRunner.getStdout();
		return ConsumerCert.parse(certificate);
	}
	
	/**
	 * @return consumerid from the Subject CN of the current /etc/pki/consumer/cert.pem identity x509 certificate
	 */
	public String getCurrentConsumerId() {
		ConsumerCert currentConsumerCert = getCurrentConsumerCert();
		if (currentConsumerCert==null) return null;
		return currentConsumerCert.consumerid;
	}
	

	public String getCurrentlyRegisteredOwnerKey() throws JSONException, Exception {
		if (this.currentlyRegisteredOrg!=null) return this.currentlyRegisteredOrg;
		if (this.currentlyRegisteredUsername==null) return null;
//		String hostname = getConfFileParameter(rhsmConfFile, "hostname");
//		String port = getConfFileParameter(rhsmConfFile, "port");
//		String prefix = getConfFileParameter(rhsmConfFile, "prefix");
		
		return (CandlepinTasks.getOwnerKeyOfConsumerId(this.currentlyRegisteredUsername, this.currentlyRegisteredPassword, SubscriptionManagerBaseTestScript.sm_serverUrl, getCurrentConsumerId()));
	}
	
	/**
	 * @return from the contents of the current /etc/pki/consumer/cert.pem
	 */
	public List<Org> getOrgs(String username, String password) {
//		List<String> orgs = new ArrayList<String>();
//		SSHCommandResult result = orgs(username, password, null, null, null);
//		for (String line : result.getStdout().split("\n")) {
//			orgs.add(line);
//		}
//		if (orgs.size()>0) orgs.remove(0); // exclude the first title line of output...  orgs:
//		return orgs;
		
		return Org.parse(orgs(username, password, null, null, null, null).getStdout());
	}
	
	/**
	 * @param registerResult
	 * @return from the stdout of the register command
	 */
	public String getCurrentConsumerId(SSHCommandResult registerResult) {
		
		// Example stdout:
		// ca3f9b32-61e7-44c0-94c1-ce328f7a15b0 jsefler.usersys.redhat.com
		
		// Example stdout:
		// The system with UUID 4e3675b1-450a-4066-92da-392c204ca5c7 has been unregistered
		// ca3f9b32-61e7-44c0-94c1-ce328f7a15b0 testuser1
		
		/*
		Pattern pattern = Pattern.compile("^[a-f,0-9,\\-]{36} [^ ]*$", Pattern.MULTILINE);
		Matcher matcher = pattern.matcher(registerResult.getStdout());
		Assert.assertTrue(matcher.find(),"Found the registered UUID in the register result."); 
		return matcher.group().split(" ")[0];
		*/
		
		// The example output and code above is from RHEL61 and RHEL57, it has changed in RHEL62 to:
		// The system with UUID 080ee4f9-736e-4195-88e1-8aff83250e7d has been unregistered
		// The system has been registered with id: 3bc07645-781f-48ef-b3d4-8821dae438f8 

		Pattern pattern = Pattern.compile("^The system has been registered with id: [a-f,0-9,\\-]{36} *$", Pattern.MULTILINE/* | Pattern.DOTALL*/);
		Matcher matcher = pattern.matcher(registerResult.getStdout());
		Assert.assertTrue(matcher.find(),"Found the registered UUID in the register result."); 
		return matcher.group().split(":")[1].trim();
	}
	
	/**
	 * @param factName
	 * @return The fact value that subscription-manager lists for factName is returned.  If factName is not listed, null is returned.
	 */
	public String getFactValue(String factName) {
		Map<String,String> factsMap = getFacts(factName);
		if (!factsMap.containsKey(factName)) {
			log.warning("Did not find fact '"+factName+"' in the facts list on system '"+hostname+"'.");
			return null;
		}
		return (factsMap.get(factName));
	}
	
	/**
	 * @return Map of the system's facts
	 */
	public Map<String,String> getFacts() {
		return getFacts(null);
	}
	/**
	 * @param grepFilter
	 * @return Map of the system's facts filtered by grepping for specific values
	 */
	public Map<String,String> getFacts(String grepFilter) {
		Map<String,String> factsMap = new HashMap<String,String>();
		List<String> factNames = new ArrayList<String>();

		//SSHCommandResult factsList = facts_(true, false, null, null, null);
		SSHCommandResult factsList = sshCommandRunner.runCommandAndWait(this.command+" facts --list" + (grepFilter==null? "":" | grep \""+grepFilter+"\""));
		String factsListAsString = factsList.getStdout().trim();
		// # subscription-manager facts --list
		// cpu.architecture: x86_64
		// cpu.bogomips: 4600.03
		// cpu.core(s)_per_socket: 1
		// cpu.cpu(s): 2
		// dmi.system.uuid: a2e71856-6778-7975-772a-21750aa3eeb0
		// dmi.system.version: Not Specified
		// uname.sysname: Linux
		// uname.version: #1 SMP Mon Mar 21 10:20:35 EDT 2011
		// virt.host_type: ibm_systemz
		// ibm_systemz-zvm
		// uname.sysname: Linux
		// network.ipaddr: 10.16.66.203
		// system.entitlements_valid: invalid
		// system.name: jsefler-r63-server.usersys.redhat.com
		// system.uuid: 1c404f7f-a77b-4afa-8748-0532f05435b5
		// uname.machine: x86_64
		
		String factNameRegex="^[\\w\\.\\(\\)-]+: ";
		Pattern pattern = Pattern.compile(factNameRegex, Pattern.MULTILINE);
		Matcher matcher = pattern.matcher(factsListAsString);
		while (matcher.find()) {
			matcher.group();
			factNames.add(matcher.group());
		}
		
		int fromIndex=0;
		for (int f = 0; f < factNames.size(); f++) {
			String thisFactName = factNames.get(f);
			String thisFactValue;
			String nextFactName;
			if (f==factNames.size()-1) {
				thisFactValue = factsListAsString.substring(factsListAsString.indexOf(thisFactName,fromIndex)+thisFactName.length());	
			} else {
				nextFactName = factNames.get(f+1);
				thisFactValue = factsListAsString.substring(factsListAsString.indexOf(thisFactName,fromIndex)+thisFactName.length(), factsListAsString.indexOf(nextFactName,fromIndex));
			}
			fromIndex = factsListAsString.indexOf(thisFactName,fromIndex)+thisFactName.length();
			thisFactName = thisFactName.replaceFirst(": $", "");
			thisFactValue = thisFactValue.replaceFirst("\n$","");
			factsMap.put(thisFactName, thisFactValue);
		}
		
		return factsMap;
	}
	
	/**
	 * @param factsMap - map of key/values pairs that will get written as JSON to a facts file that will override the true facts on the system.  Note: subscription-manager facts --update may need to be called after this method to realize the override.
	 */
	public void createFactsFileWithOverridingValues (Map<String,String> factsMap) {
		createFactsFileWithOverridingValues (overrideFactsFilename, factsMap);
	}
	/**
	 * @param factsFilename - name for the facts file (e.g. sockets.facts); must end in ".facts" to be recognized by rhsm; do NOT prepend with /etc/rhsm/facts
	 * @param factsMap
	 */
	public void createFactsFileWithOverridingValues (String factsFilename, Map<String,String> factsMap) {
		
		// assemble an echo command and run it to create a facts file
		String keyvaluesString = "";
		for (String key : factsMap.keySet()) {
			if (factsMap.get(key)==null)
				keyvaluesString += String.format("\"%s\":%s, ", key, factsMap.get(key));
			else
				keyvaluesString += String.format("\"%s\":\"%s\", ", key, factsMap.get(key));
		}
		keyvaluesString = keyvaluesString.replaceFirst(", *$", "");
		String echoCommand = String.format("echo '{%s}' > %s", keyvaluesString, (factsDir+"/"+factsFilename).replaceAll("/{2,}", "/"));	// join the dir and filename and make sure there are not too many /'s
        sshCommandRunner.runCommandAndWait(echoCommand);	// create an override facts file
	}
	public void deleteFactsFileWithOverridingValues () {
		deleteFactsFileWithOverridingValues(overrideFactsFilename);	// delete the override facts file
	}
	public void deleteFactsFileWithOverridingValues (String factsFilename) {
		String deleteCommand = String.format("rm -f %s", (factsDir+"/"+factsFilename).replaceAll("/{2,}", "/"));
		sshCommandRunner.runCommandAndWait(deleteCommand);
	}
	
	
	/**
	 * @return a Map equivalent to the contents of "/var/lib/rhsm/productid.js"
	 * @throws JSONException
	 */
	public Map<String,String> getProductIdRepoMap() throws JSONException {
		Map<String,String> productIdToRepoMap = new HashMap<String,String>();
		sshCommandRunner.runCommandAndWait/*WithoutLogging*/("cat "+productIdJsonFile);
		JSONObject productIdToRepoJSON = new JSONObject(sshCommandRunner.getStdout());

		Iterator<String> productIdKeysIter = productIdToRepoJSON.keys();
		while (productIdKeysIter.hasNext()) {
			String productId = productIdKeysIter.next();
			String repo = productIdToRepoJSON.getString(productId);
			productIdToRepoMap.put(productId, repo);
		}
		return productIdToRepoMap;
	}
	
	
	/**
	 * @return a map of serialNumber to SubscriptionPool pairs.  The SubscriptionPool is the source from where the serialNumber for the currentlyConsumedProductSubscriptions came from.
	 * @throws Exception 
	 */
//	public Map<Long, SubscriptionPool> getCurrentSerialMapToSubscriptionPools() {
//		sshCommandRunner.runCommandAndWait("find "+entitlementCertDir+" -name '*.pem' | xargs -I '{}' openssl x509 -in '{}' -noout -text");
//		String certificates = sshCommandRunner.getStdout();
//		return SubscriptionPool.parseCerts(certificates);
//	}
	public Map<BigInteger, SubscriptionPool> getCurrentSerialMapToSubscriptionPools(String username, String password) throws Exception  {
		
		Map<BigInteger, SubscriptionPool> serialMapToSubscriptionPools = new HashMap<BigInteger, SubscriptionPool>();
//		String hostname = getConfFileParameter(rhsmConfFile, "hostname");
//		String port = getConfFileParameter(rhsmConfFile, "port");
//		String prefix = getConfFileParameter(rhsmConfFile, "prefix");
		for (EntitlementCert entitlementCert : getCurrentEntitlementCerts()) {
			JSONObject jsonEntitlement = CandlepinTasks.getEntitlementUsingRESTfulAPI(username,password,SubscriptionManagerBaseTestScript.sm_serverUrl,entitlementCert.id);
			String poolHref = jsonEntitlement.getJSONObject("pool").getString("href");
			JSONObject jsonPool = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(username,password,SubscriptionManagerBaseTestScript.sm_serverUrl,poolHref));
			String subscriptionName = jsonPool.getString("productName");
			String productId = jsonPool.getString("productId");
			String poolId = jsonPool.getString("id");
			String quantity = Integer.toString(jsonPool.getInt("quantity"));	// = jsonPool.getString("quantity");
			String endDate = jsonPool.getString("endDate");
			Boolean multiEntitlement = CandlepinTasks.isPoolProductMultiEntitlement(username,password, SubscriptionManagerBaseTestScript.sm_serverUrl, poolId);
			SubscriptionPool fromPool = new SubscriptionPool(subscriptionName,productId,poolId,quantity,multiEntitlement,endDate);
			serialMapToSubscriptionPools.put(entitlementCert.serialNumber, fromPool);
		}
		return serialMapToSubscriptionPools;
	}
	
	/**
	 * @param lsOptions - options used when calling ls to populate the order of the returned List (man ls for more info)
	 * <br>Possibilities:
	 * <br>"" no sort order preferred
	 * <br>"-t" sort by modification time
	 * <br>"-v" natural sort of (version) numbers within text
	 * @return List of /etc/pki/entitlement/*.pem files sorted using lsOptions (excluding a key.pem file)
	 */
	public List<File> getCurrentEntitlementCertFiles(String lsOptions) {
		if (lsOptions==null) lsOptions = "";
		//sshCommandRunner.runCommandAndWait("find /etc/pki/entitlement/ -name '*.pem'");
		//sshCommandRunner.runCommandAndWait("ls -1 "+lsOptions+" "+entitlementCertDir+"/*.pem");
		sshCommandRunner.runCommandAndWait("ls -1 "+lsOptions+" "+entitlementCertDir+"/*.pem | grep -v key.pem");
		String lsFiles = sshCommandRunner.getStdout().trim();
		List<File> files = new ArrayList<File>();
		if (!lsFiles.isEmpty()) {
			for (String lsFile : Arrays.asList(lsFiles.split("\n"))) {
				
				// exclude the the key.pem file
				if (lsFile.endsWith("key.pem")) continue;
				
				// TEMPORARY WORKAROUND FOR BUG: https://bugzilla.redhat.com/show_bug.cgi?id=640338 - jsefler 10/7/2010
				if (lsFile.matches(".*\\(\\d+\\)\\.pem")) {
					boolean invokeWorkaroundWhileBugIsOpen = true;
					String bugId="640338"; 
					try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
					if (invokeWorkaroundWhileBugIsOpen) {
						continue;
					}
				}
				// END OF WORKAROUND
				
				files.add(new File(lsFile));
			}
		}
		return files;
	}
	/**
	 * @return List of /etc/pki/entitlement/*.pem files (excluding a key.pem file)
	 */
	public List<File> getCurrentEntitlementCertFiles() {
		return getCurrentEntitlementCertFiles("-v");
	}

	

	/**
	 * @param lsOptions - options used when calling ls to populate the order of the returned List (man ls for more info)
	 * <br>Possibilities:
	 * <br>"" no sort order preferred
	 * <br>"-t" sort by modification time
	 * <br>"-v" natural sort of (version) numbers within text
	 * @return List of /etc/pki/product/*.pem files sorted using lsOptions
	 */
	public List<File> getCurrentProductCertFiles(String lsOptions) {
		if (lsOptions==null) lsOptions = "";
		//sshCommandRunner.runCommandAndWait("find /etc/pki/product/ -name '*.pem'");
		sshCommandRunner.runCommandAndWait("ls -1 "+lsOptions+" "+productCertDir+"/*.pem");
		String lsFiles = sshCommandRunner.getStdout().trim();
		List<File> files = new ArrayList<File>();
		if (!lsFiles.isEmpty()) {
			for (String lsFile : Arrays.asList(lsFiles.split("\n"))) {
				files.add(new File(lsFile));
			}
		}
		return files;
	}
	
	/**
	 * @return List of /etc/pki/product/*.pem files
	 */
	public List<File> getCurrentProductCertFiles() {
		return getCurrentProductCertFiles("-v");
	}
	
	
// replaced by getYumListOfAvailablePackagesFromRepo(...)
//	/**
//	 * @return
//	 * @author ssalevan
//	 */
//	public HashMap<String,String[]> getPackagesCorrespondingToSubscribedRepos(){
//		int min = 3;
//		sshCommandRunner.runCommandAndWait("killall -9 yum");
//		log.info("timeout of "+min+" minutes for next command");
//		sshCommandRunner.runCommandAndWait("yum list available",Long.valueOf(min*60000));
//		HashMap<String,String[]> pkgMap = new HashMap<String,String[]>();
//		
//		String[] packageLines = sshCommandRunner.getStdout().split("\\n");
//		
//		int pkglistBegin = 0;
//		
//		for(int i=0;i<packageLines.length;i++){
//			pkglistBegin++;
//			if(packageLines[i].contains("Available Packages"))
//				break;
//		}
//		
//		for(ProductSubscription sub : getCurrentlyConsumedProductSubscriptions()){
//			ArrayList<String> pkgList = new ArrayList<String>();
//			for(int i=pkglistBegin;i<packageLines.length;i++){
//				String[] splitLine = packageLines[i].split(" ");
//				String pkgName = splitLine[0];
//				String repoName = splitLine[splitLine.length - 1];
//				if(repoName.toLowerCase().contains(sub.productName.toLowerCase()))
//					pkgList.add(pkgName);
//			}
//			pkgMap.put(sub.productName, (String[])pkgList.toArray());
//		}
//		
//		return pkgMap;
//	}

	/**
	 * @param productSubscription
	 * @param username	- owner of the subscription pool (will be used in a REST api call to the candlepin server)
	 * @param password
	 * @return the SubscriptionPool from which this consumed ProductSubscription came from
	 * @throws Exception
	 */
	public SubscriptionPool getSubscriptionPoolFromProductSubscription(ProductSubscription productSubscription, String username, String password) throws Exception {
		
		// if already known, return the SubscriptionPool from which ProductSubscription came
		if (productSubscription.fromSubscriptionPool != null) return productSubscription.fromSubscriptionPool;
		
		productSubscription.fromSubscriptionPool = getCurrentSerialMapToSubscriptionPools(username, password).get(productSubscription.serialNumber);

		return productSubscription.fromSubscriptionPool;
	}
	
//DELETEME
//	/**
//	 * @param fieldName
//	 * @param fieldValue
//	 * @param subscriptionPools - usually getCurrentlyAvailableSubscriptionPools()
//	 * @return - the SubscriptionPool from subscriptionPools that has a matching field (if not found, null is returned)
//	 */
//	public SubscriptionPool findSubscriptionPoolWithMatchingFieldFromList(String fieldName, Object fieldValue, List<SubscriptionPool> subscriptionPools) {
//		
//		SubscriptionPool subscriptionPoolWithMatchingField = null;
//		for (SubscriptionPool subscriptionPool : subscriptionPools) {
//			try {
//				if (SubscriptionPool.class.getField(fieldName).get(subscriptionPool).equals(fieldValue)) {
//					subscriptionPoolWithMatchingField = subscriptionPool;
//				}
//			} catch (IllegalArgumentException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			} catch (SecurityException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			} catch (IllegalAccessException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			} catch (NoSuchFieldException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
//		return subscriptionPoolWithMatchingField;
//	}
//	
//	
//	/**
//	 * @param fieldName
//	 * @param fieldValue
//	 * @param productSubscriptions - usually getCurrentlyConsumedProductSubscriptions()
//	 * @return - the ProductSubscription from productSubscriptions that has a matching field (if not found, null is returned)
//	 */
//	public ProductSubscription findProductSubscriptionWithMatchingFieldFromList(String fieldName, Object fieldValue, List<ProductSubscription> productSubscriptions) {
//		ProductSubscription productSubscriptionWithMatchingField = null;
//		for (ProductSubscription productSubscription : productSubscriptions) {
//			try {
//				if (ProductSubscription.class.getField(fieldName).get(productSubscription).equals(fieldValue)) {
//					productSubscriptionWithMatchingField = productSubscription;
//				}
//			} catch (IllegalArgumentException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			} catch (SecurityException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			} catch (IllegalAccessException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			} catch (NoSuchFieldException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
//		return productSubscriptionWithMatchingField;
//	}
//	
//	
//	/**
//	 * @param fieldName
//	 * @param fieldValue
//	 * @param installedProducts - usually getCurrentProductCerts()
//	 * @return - the InstalledProduct from installedProducts that has a matching field (if not found, null is returned)
//	 */
//	public InstalledProduct findInstalledProductWithMatchingFieldFromList(String fieldName, Object fieldValue, List<InstalledProduct> installedProducts) {
//		InstalledProduct installedProductWithMatchingField = null;
//		for (InstalledProduct installedProduct : installedProducts) {
//			try {
//				if (InstalledProduct.class.getField(fieldName).get(installedProduct).equals(fieldValue)) {
//					installedProductWithMatchingField = installedProduct;
//				}
//			} catch (IllegalArgumentException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			} catch (SecurityException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			} catch (IllegalAccessException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			} catch (NoSuchFieldException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
//		return installedProductWithMatchingField;
//	}
//	
//	
//	/**
//	 * @param fieldName
//	 * @param fieldValue
//	 * @param productCerts - usually getCurrentlyProductCerts()
//	 * @return - the ProductCert from productCerts that has a matching field (if not found, null is returned)
//	 */
//	public ProductCert findProductCertWithMatchingFieldFromList(String fieldName, Object fieldValue, List<ProductCert> productCerts) {
//		ProductCert productCertWithMatchingField = null;
//		for (ProductCert productCert : productCerts) {
//			try {
//				if (ProductCert.class.getField(fieldName).get(productCert).equals(fieldValue)) {
//					productCertWithMatchingField = productCert;
//				}
//			} catch (IllegalArgumentException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			} catch (SecurityException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			} catch (IllegalAccessException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			} catch (NoSuchFieldException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
//		return productCertWithMatchingField;
//	}
//	
//	
//	/**
//	 * @param fieldName
//	 * @param fieldValue
//	 * @param entitlementCerts - usually getCurrentEntitlementCerts()
//	 * @return - the EntitlementCert from entitlementCerts that has a matching field (if not found, null is returned)
//	 */
//	public EntitlementCert findEntitlementCertWithMatchingFieldFromList(String fieldName, Object fieldValue, List<EntitlementCert> entitlementCerts) {
//		EntitlementCert entitlementCertWithMatchingField = null;
//		for (EntitlementCert entitlementCert : entitlementCerts) {
//			try {
//				if (EntitlementCert.class.getField(fieldName).get(entitlementCert).equals(fieldValue)) {
//					entitlementCertWithMatchingField = entitlementCert;
//				}
//			} catch (IllegalArgumentException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			} catch (SecurityException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			} catch (IllegalAccessException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			} catch (NoSuchFieldException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
//		return entitlementCertWithMatchingField;
//	}
	


	
//KEEPME FOR FUTURE USAGE SOMEWHERE ELSE	
//	/**
//	 * Given a List of instances of some class (e.g. getCurrentEntitlementCerts()), this
//	 * method is useful for finding the first instance (e.g. an EntitlementCert) whose public
//	 * field by the name "fieldName" has a value of fieldValue.  If no match is found, null is returned.
//	 * @param <T>
//	 * @param fieldName
//	 * @param fieldValue
//	 * @param dataInstances
//	 * @return
//	 */
//	@SuppressWarnings("unchecked")
//	public <T> T findFirstInstanceWithMatchingFieldFromList(String fieldName, Object fieldValue, List<T> dataInstances) {
//		Collection<T> dataInstancesWithMatchingFieldFromList = Collections2.filter(dataInstances, new ByValuePredicate(fieldName,fieldValue));
//		if (dataInstancesWithMatchingFieldFromList.isEmpty()) return null;
//		return (T) dataInstancesWithMatchingFieldFromList.toArray()[0];
//	}
//	
//	/**
//	 * Given a List of instances of some class (e.g. getAllAvailableSubscriptionPools()), this
//	 * method is useful for finding a subset of instances whose public field by the name "fieldName"
//	 * has a value of fieldValue.  If no match is found, an empty list is returned.
//	 * @param <T>
//	 * @param fieldName
//	 * @param fieldValue
//	 * @param dataInstances
//	 * @return
//	 */
//	@SuppressWarnings("unchecked")
//	public <T> List<T> findAllInstancesWithMatchingFieldFromList(String fieldName, Object fieldValue, List<T> dataInstances) {
//		Collection<T> dataInstancesWithMatchingFieldFromList = Collections2.filter(dataInstances, new ByValuePredicate(fieldName,fieldValue));
//		return (List<T>) Arrays.asList(dataInstancesWithMatchingFieldFromList.toArray());
//	}
//	
//	class ByValuePredicate implements Predicate<Object> {
//		Object value;
//		String fieldName;
//		public ByValuePredicate(String fieldName, Object value) {
//			this.value=value;
//			this.fieldName=fieldName;
//		}
//		public boolean apply(Object toTest) {
//			try {
//				return toTest.getClass().getField(fieldName).get(toTest).equals(value);
//			} catch (IllegalArgumentException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			} catch (SecurityException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			} catch (IllegalAccessException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			} catch (NoSuchFieldException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//			return false;
//		}
//	}

	
	/**
	 * @return a list of the currently granted EntitlementCerts that are within the warningPeriod (days) of its endDate
	 */
	public List<EntitlementCert> getCurrentEntitlementCertsWithinWarningPeriod() {
		List<EntitlementCert> entitlementCertsWithinWarningPeriod = new ArrayList<EntitlementCert>();
		Calendar now = new GregorianCalendar();	now.setTimeInMillis(System.currentTimeMillis());
		
		// assemble all of the current entitlementCerts that are within the warning period
		for (EntitlementCert entitlementCert : getCurrentEntitlementCerts()) {
			
			// find the warning period
			int warningPeriod = 0;	// assume zero
			try {warningPeriod = Integer.valueOf(entitlementCert.orderNamespace.warningPeriod);}
			catch (NumberFormatException e) {
				log.warning("The OrderNamespace's warningPeriod is non-numeric or non-existing in EntitlementCert: "+entitlementCert);
			}
			
			// subtract the warningPeriod number of days from the endDate
			entitlementCert.orderNamespace.endDate.add(Calendar.DATE, -1*warningPeriod);
			
			// check if we are now inside the warningPeriod
			if (entitlementCert.orderNamespace.endDate.before(now)) {
				entitlementCertsWithinWarningPeriod.add(entitlementCert);
			}
		}
		return entitlementCertsWithinWarningPeriod;
	}
	
	/**
	 * For the given consumed ProductSubscription, get the corresponding EntitlementCert
	 * @param productSubscription
	 * @return
	 */
	@Deprecated
	public EntitlementCert getEntitlementCertCorrespondingToProductSubscriptionUsingOpensslX509(ProductSubscription productSubscription) {
		String serialPemFile = entitlementCertDir+"/"+productSubscription.serialNumber+".pem";
		sshCommandRunner.runCommandAndWaitWithoutLogging("openssl x509 -text -noout -in "+serialPemFile+"; echo \"    File: "+serialPemFile+"\"");	// openssl x509 -text -noout -in /etc/pki/entitlement/5066044962491605926.pem; echo "    File: /etc/pki/entitlement/5066044962491605926.pem"
		String certificate = sshCommandRunner.getStdout();
		List<EntitlementCert> entitlementCerts = EntitlementCert.parseStdoutFromOpensslX509(certificate);
		Assert.assertEquals(entitlementCerts.size(), 1,"Only one EntitlementCert corresponds to ProductSubscription: "+productSubscription);
		return entitlementCerts.get(0);
	}
	/**
	 * For the given consumed ProductSubscription, get the corresponding EntitlementCert
	 * @param productSubscription
	 * @return
	 */
	public EntitlementCert getEntitlementCertCorrespondingToProductSubscription(ProductSubscription productSubscription) {
		String serialPemFile = entitlementCertDir+"/"+productSubscription.serialNumber+".pem";
		sshCommandRunner.runCommandAndWaitWithoutLogging("rct cat-cert "+serialPemFile);
		String certificate = sshCommandRunner.getStdout();
		List<EntitlementCert> entitlementCerts = EntitlementCert.parse(certificate);
		Assert.assertEquals(entitlementCerts.size(), 1,"Only one EntitlementCert corresponds to ProductSubscription: "+productSubscription);
		return entitlementCerts.get(0);
	}
	
	/**
	 * For the given ProductCert installed in /etc/pki/product, get the corresponding InstalledProduct from subscription-manager list --installed
	 * @param productCert
	 * @return instance of InstalledProduct (null if not found)
	 */
	public InstalledProduct getInstalledProductCorrespondingToProductCert(ProductCert productCert) {
		return getInstalledProductCorrespondingToProductCert(productCert,getCurrentlyInstalledProducts());
	}
	public InstalledProduct getInstalledProductCorrespondingToProductCert(ProductCert productCert, List<InstalledProduct> fromInstalledProducts) {
		for (InstalledProduct installedProduct : fromInstalledProducts) {
			
			/* IMPLEMENTATION BEFORE THE PRODUCT ID WAS INCLUDED AS A FIELD FOR InstalledProduct
			// when a the product cert is missing OIDS, "None" is rendered in the list --installed
			String name = productCert.productNamespace.name==null?"None":productCert.productNamespace.name;
			String version = productCert.productNamespace.version==null?"None":productCert.productNamespace.version;
			String arch = productCert.productNamespace.arch==null?"None":productCert.productNamespace.arch;
			
			if (installedProduct.productName.equals(name) &&
				installedProduct.version.equals(version) &&
				installedProduct.arch.equals(arch)) {
				return installedProduct;
			}
			*/
			if (productCert.productId.equals(installedProduct.productId)) return installedProduct;
		}
		return null; // not found
	}
	
	
	public EntitlementCert getEntitlementCertCorrespondingToSubscribedPool(SubscriptionPool subscribedPool) {
//		String hostname = getConfFileParameter(rhsmConfFile, "hostname");
//		String port = getConfFileParameter(rhsmConfFile, "port");
//		String prefix = getConfFileParameter(rhsmConfFile, "prefix");
		
		for (File entitlementCertFile : getCurrentEntitlementCertFiles("-t")) {
			EntitlementCert entitlementCert = getEntitlementCertFromEntitlementCertFile(entitlementCertFile);
			try {
				JSONObject jsonEntitlement = CandlepinTasks.getEntitlementUsingRESTfulAPI(this.currentlyRegisteredUsername,this.currentlyRegisteredPassword,SubscriptionManagerBaseTestScript.sm_serverUrl,entitlementCert.id);
				JSONObject jsonPool = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(this.currentlyRegisteredUsername,this.currentlyRegisteredPassword,SubscriptionManagerBaseTestScript.sm_serverUrl,jsonEntitlement.getJSONObject("pool").getString("href")));
				if (jsonPool.getString("id").equals(subscribedPool.poolId)) {
					return entitlementCert;
				}
			} catch (Exception e) {
				e.printStackTrace();
				Assert.fail(e.getMessage());
			}
		}
		return null;	// not found
	}
	
	public List<ProductCert> getCurrentProductCertsCorrespondingToSubscriptionPool(SubscriptionPool pool) throws JSONException, Exception {
		List<ProductCert> currentProductCertsCorrespondingToSubscriptionPool = new ArrayList<ProductCert>();
//		String hostname = getConfFileParameter(rhsmConfFile, "hostname");
//		String port = getConfFileParameter(rhsmConfFile, "port");
//		String prefix = getConfFileParameter(rhsmConfFile, "prefix");
		List<ProductCert> currentProductCerts = getCurrentProductCerts();

		JSONObject jsonPool = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(this.currentlyRegisteredUsername,this.currentlyRegisteredPassword,SubscriptionManagerBaseTestScript.sm_serverUrl,"/pools/"+pool.poolId));
		JSONArray jsonProvidedProducts = (JSONArray) jsonPool.getJSONArray("providedProducts");
		for (int k = 0; k < jsonProvidedProducts.length(); k++) {
			JSONObject jsonProvidedProduct = (JSONObject) jsonProvidedProducts.get(k);
			String providedProductId = jsonProvidedProduct.getString("productId");
			
			// is this productId among the installed ProductCerts? if so, add them all to the currentProductCertsCorrespondingToSubscriptionPool
			currentProductCertsCorrespondingToSubscriptionPool.addAll(ProductCert.findAllInstancesWithMatchingFieldFromList("productId", providedProductId, currentProductCerts));
		}
		
		return currentProductCertsCorrespondingToSubscriptionPool;
	}
	
	public List <EntitlementCert> getEntitlementCertsCorrespondingToProductCert(ProductCert productCert) {
		List<EntitlementCert> correspondingEntitlementCerts = new ArrayList<EntitlementCert>();
		ProductNamespace productNamespaceMatchingProductCert = null;
		for (EntitlementCert entitlementCert : getCurrentEntitlementCerts()) {
			productNamespaceMatchingProductCert = ProductNamespace.findFirstInstanceWithMatchingFieldFromList("id", productCert.productId, entitlementCert.productNamespaces);	
			if (productNamespaceMatchingProductCert!=null) {
				correspondingEntitlementCerts.add(entitlementCert);
			}
		}
		return correspondingEntitlementCerts;
	}
	
	public EntitlementCert getEntitlementCertFromEntitlementCertFileUsingOpensslX509(File serialPemFile) {
		sshCommandRunner.runCommandAndWaitWithoutLogging("openssl x509 -text -noout -in "+serialPemFile+"; echo \"    File: "+serialPemFile+"\"");	// openssl x509 -text -noout -in /etc/pki/entitlement/5066044962491605926.pem; echo "    File: /etc/pki/entitlement/5066044962491605926.pem"
		String certificates = sshCommandRunner.getStdout();
		List<EntitlementCert> entitlementCerts = EntitlementCert.parseStdoutFromOpensslX509(certificates);
		
		// assert that only one EntitlementCert was parsed and return it
		Assert.assertEquals(entitlementCerts.size(), 1, "Entitlement cert file '"+serialPemFile+"' parsed only one EntitlementCert.");
		return entitlementCerts.get(0);
	}
	public EntitlementCert getEntitlementCertFromEntitlementCertFile(File serialPemFile) {
		sshCommandRunner.runCommandAndWaitWithoutLogging("rct cat-cert "+serialPemFile);
		String certificates = sshCommandRunner.getStdout();
		List<EntitlementCert> entitlementCerts = EntitlementCert.parse(certificates);
		
		// assert that only one EntitlementCert was parsed and return it
		Assert.assertEquals(entitlementCerts.size(), 1, "Entitlement cert file '"+serialPemFile+"' parsed only one EntitlementCert.");
		return entitlementCerts.get(0);
	}
	
	public BigInteger getSerialNumberFromEntitlementCertFile(File serialPemFile) {
		// example serialPemFile: /etc/pki/entitlement/196.pem
		// extract the serial number from the certFile name
		// Note: probably a more robust way to do this is to get it from inside the file
		//Integer serialNumber = Integer.valueOf(serialPemFile.getName().split("\\.")[0]);
		String serialNumber = serialPemFile.getName().split("\\.")[0];
		//return Long.parseLong(serialNumber, 10);
		//return new Long(serialNumber);
		return new BigInteger(serialNumber);
	}
	
	public File getEntitlementCertFileFromEntitlementCert(EntitlementCert entitlementCert) {
		File serialPemFile = new File(entitlementCertDir+File.separator+entitlementCert.serialNumber+".pem");
		return serialPemFile;
	}
	
	public File getEntitlementCertKeyFileFromEntitlementCert(EntitlementCert entitlementCert) {
		File serialKeyPemFile = new File(entitlementCertDir+File.separator+entitlementCert.serialNumber+"-key.pem");
		return serialKeyPemFile;
	}
	
	public File getEntitlementCertKeyFileCorrespondingToEntitlementCertFile(File entitlementCertFile) {
		// 239223656620993791.pem  => 239223656620993791-key.pem
		String serialKeyPem = entitlementCertFile.getPath().replaceAll("(\\.\\w*)$", "-key$1");
		// 239223656620993791      => 239223656620993791-key
		if (!serialKeyPem.contains("-key.")) serialKeyPem += "-key";

		return new File(serialKeyPem);
	}
	
	@Deprecated
	public ProductCert getProductCertFromProductCertFileUsingOpensslX509(File productPemFile) {
		sshCommandRunner.runCommandAndWaitWithoutLogging("openssl x509 -noout -text -in "+productPemFile.getPath());
		String certificates = sshCommandRunner.getStdout();
		List<ProductCert> productCerts = ProductCert.parse(certificates);
		
		// assert that only one ProductCert was parsed and return it
		Assert.assertEquals(productCerts.size(), 1, "Product cert file '"+productPemFile+"' parsed only one ProductCert.");
		return productCerts.get(0);
	}
	public ProductCert getProductCertFromProductCertFile(File productPemFile) {
		sshCommandRunner.runCommandAndWaitWithoutLogging("rct cat-cert "+productPemFile.getPath());
		String certificates = sshCommandRunner.getStdout();
		List<ProductCert> productCerts = ProductCert.parse(certificates);
		
		// assert that only one ProductCert was parsed and return it
		Assert.assertEquals(productCerts.size(), 1, "Product cert file '"+productPemFile+"' parsed only one ProductCert.");
		return productCerts.get(0);
	}
	
	public CertStatistics getCertStatisticsFromCertFile(File certPemFile) {
		sshCommandRunner.runCommandAndWait/*WithoutLogging*/("rct stat-cert "+certPemFile.getPath());
		String rawStatistics = sshCommandRunner.getStdout();
		CertStatistics certStatistics = CertStatistics.parse(rawStatistics);

		return certStatistics;
	}
	
	
	
	// register module tasks ************************************************************
	
	/**
	 * register WITHOUT asserting results.
	 */
	public SSHCommandResult register_(String username, String password, String org, String environment, ConsumerType type, String name, String consumerid, Boolean autosubscribe, String servicelevel, String release, List<String> activationkeys, String serverurl, String baseurl, Boolean force, Boolean autoheal, String proxy, String proxyuser, String proxypassword) {
		
		// assemble the command
		String command = this.command;											command += " register";
		if (username!=null)														command += " --username="+String.format(username.contains(" ")? "\"%s\"":"%s", username);	// quote username containing spaces
		if (password!=null)														command += " --password="+String.format(password.contains("(")||password.contains(")")? "\"%s\"":"%s", password);	// quote password containing ()
		if (org!=null)															command += " --org="+org;
		if (environment!=null)													command += " --environment="+environment;
		if (type!=null)															command += " --type="+type;
		if (name!=null)															command += " --name="+String.format(name.contains("\"")? "'%s'":"\"%s\"", name./*escape backslashes*/replace("\\", "\\\\")./*escape backticks*/replace("`", "\\`"));
		if (consumerid!=null)													command += " --consumerid="+consumerid;
		if (autosubscribe!=null && autosubscribe)								command += " --autosubscribe";
		if (servicelevel!=null)													command += " --servicelevel="+String.format(servicelevel.contains(" ")||servicelevel.isEmpty()? "\"%s\"":"%s", servicelevel);	// quote a value containing spaces or is empty
		if (release!=null)														command += " --release="+release;
		if (activationkeys!=null)	for (String activationkey : activationkeys)	command += " --activationkey="+String.format(activationkey.contains(" ")? "\"%s\"":"%s", activationkey);	// quote activationkey containing spaces
		if (serverurl!=null)													command += " --serverurl="+serverurl;
		if (baseurl!=null)														command += " --baseurl="+baseurl;
		if (force!=null && force)												command += " --force";
		if (proxy!=null)														command += " --proxy="+proxy;
		if (proxyuser!=null)													command += " --proxyuser="+proxyuser;
		if (proxypassword!=null)												command += " --proxypassword="+proxypassword;

		// run command without asserting results
		SSHCommandResult sshCommandResult = sshCommandRunner.runCommandAndWait(command);
		
		// reset this.currentlyRegistered values
		if (sshCommandResult.getExitCode().equals(Integer.valueOf(0))) {
			// The system has been registered with id: 660faf39-a8f2-4311-acf2-5c1bb3c141ef
			this.currentlyRegisteredUsername = username;
			this.currentlyRegisteredPassword = password;
			this.currentlyRegisteredOrg = org;
			this.currentlyRegisteredType = type;
		} else
		if (sshCommandResult.getExitCode().equals(Integer.valueOf(1)) && autosubscribe!=null && autosubscribe) {
			// https://bugzilla.redhat.com/show_bug.cgi?id=689608
			this.currentlyRegisteredUsername = username;
			this.currentlyRegisteredPassword = password;
			this.currentlyRegisteredOrg = org;
			this.currentlyRegisteredType = type;	
		} else
		if (sshCommandResult.getExitCode().equals(Integer.valueOf(1)) && (force==null || !force)) {
			// This system is already registered. Use --force to override
		} else
		if (sshCommandResult.getExitCode().equals(Integer.valueOf(1)) && (environment!=null)) {
			// Server does not support environments.
		} else
		if (sshCommandResult.getExitCode().equals(Integer.valueOf(255))) {
			// Traceback/Error
			this.currentlyRegisteredUsername = null;
			this.currentlyRegisteredPassword = null;
			this.currentlyRegisteredOrg = null;
			this.currentlyRegisteredType = null;	
		} else {
			Assert.fail("Encountered an unknown exitCode '"+sshCommandResult.getExitCode()+"' during a attempt to register.");
		}
		
		// set autoheal attribute of the consumer
		if (autoheal!=null && sshCommandResult.getExitCode().equals(Integer.valueOf(0))) {
			try {
				// Note: NullPointerException will likely occur when activationKeys are used because null will likely be passed for username/password
				CandlepinTasks.setAutohealForConsumer(currentlyRegisteredUsername, currentlyRegisteredPassword, SubscriptionManagerBaseTestScript.sm_serverUrl, getCurrentConsumerId(sshCommandResult), autoheal);
			} catch (Exception e) {
				e.printStackTrace();
				Assert.fail(e.getMessage());
			} 
		}
		
		return sshCommandResult;
	}
	
	/**
	 * register WITHOUT asserting results.
	 */
	public SSHCommandResult register_(String username, String password, String org, String environment, ConsumerType type, String name, String consumerid, Boolean autosubscribe, String servicelevel, String release, String activationkey, String serverurl, String baseurl, Boolean force, Boolean autoheal, String proxy, String proxyuser, String proxypassword) {
		
		List<String> activationkeys = activationkey==null?null:Arrays.asList(new String[]{activationkey});

		return register_(username, password, org, environment, type, name, consumerid, autosubscribe, servicelevel, release, activationkeys, serverurl, baseurl, force, autoheal, proxy, proxyuser, proxypassword);
	}
	
	

	
	public SSHCommandResult register(String username, String password, String org, String environment, ConsumerType type, String name, String consumerid, Boolean autosubscribe, String servicelevel, String release, List<String> activationkeys, String serverurl, String baseurl, Boolean force, Boolean autoheal, String proxy, String proxyuser, String proxypassword) {
		
		boolean alreadyRegistered = this.currentlyRegisteredUsername==null? false:true;
		String msg;
		SSHCommandResult sshCommandResult = register_(username, password, org, environment, type, name, consumerid, autosubscribe, servicelevel, release, activationkeys, serverurl, baseurl, force, autoheal, proxy, proxyuser, proxypassword);
	
		// assert results when already registered
		if ((force==null || !force) && alreadyRegistered) {
			Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(1), "The exit code from the register command indicates we are already registered.");
			Assert.assertEquals(sshCommandResult.getStdout().trim(), "This system is already registered. Use --force to override");	
			Assert.assertEquals(sshCommandResult.getStderr().trim(), "");	
			return sshCommandResult;
		}

		// assert results for a successful registration exit code
		if (autosubscribe==null || !autosubscribe)	// https://bugzilla.redhat.com/show_bug.cgi?id=689608
			Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "The exit code from the register command indicates a success.");
		
		// assert the heading for the current status of the installed products
		msg = "Installed Product Current Status:";
		if (autosubscribe==null || !autosubscribe)
			Assert.assertFalse(sshCommandResult.getStdout().contains(msg),
					"register without autosubscribe should not show a list of the \""+msg+"\".");
		else
			Assert.assertTrue(sshCommandResult.getStdout().contains(msg),
					"register with autosubscribe should show a list of the \""+msg+"\".");	
		
		// assert stdout results for a successful registration id
		if (type==ConsumerType.person) name = username;		// https://bugzilla.redhat.com/show_bug.cgi?id=661130
		if (name==null) name = this.hostname;				// https://bugzilla.redhat.com/show_bug.cgi?id=669395
		//Assert.assertContainsMatch(sshCommandResult.getStdout().trim(), "[a-f,0-9,\\-]{36} "+name);	// applicable to RHEL61 and RHEL57. changed in RHEL62 due to feedback from mmccune https://engineering.redhat.com/trac/kalpana/wiki/SubscriptionManagerReview - jsefler 6/28/2011
		//Assert.assertContainsMatch(sshCommandResult.getStdout().trim(), "The system has been registered with id: [a-f,0-9,\\-]{36}");
		msg = "The system has been registered with id: [a-f,0-9,\\-]{36}";
		Assert.assertTrue(Pattern.compile(".*"+msg+".*",Pattern.DOTALL).matcher(sshCommandResult.getStdout()).find(),"Stdout from register contains a match to expected msg: "+msg);
		
		// assert that register with consumerId returns the expected uuid
		if (consumerid!=null) {
			//Assert.assertEquals(sshCommandResult.getStdout().trim(), consumerId+" "+username, "register to an exiting consumer was a success");
			//Assert.assertContainsMatch(sshCommandResult.getStdout().trim(), "^"+consumerId, "register to an exiting consumer was a success");	// removed name from assert to account for https://bugzilla.redhat.com/show_bug.cgi?id=669395	// applicable to RHEL61 and RHEL57.
			//Assert.assertContainsMatch(sshCommandResult.getStdout().trim(), "The system has been registered with id: "+consumerid, "register to an exiting consumer was a success");	// removed name from assert to account for https://bugzilla.redhat.com/show_bug.cgi?id=669395
			msg = "The system has been registered with id: "+consumerid;
			Assert.assertTrue(sshCommandResult.getStdout().contains(msg), "Stdout from register contains a match to expected msg: "+msg);
		}
		
		// assert certificate files are installed into /etc/pki/consumer
		Assert.assertTrue(RemoteFileTasks.testExists(sshCommandRunner,this.consumerKeyFile()), "Consumer key file '"+this.consumerKeyFile()+"' must exist after register.");
		Assert.assertTrue(RemoteFileTasks.testExists(sshCommandRunner,this.consumerCertFile()), "Consumer cert file '"+this.consumerCertFile()+"' must exist after register.");
		
		// TEMPORARY WORKAROUND FOR BUG: https://bugzilla.redhat.com/show_bug.cgi?id=639417 - jsefler 10/1/2010
		boolean invokeWorkaroundWhileBugIsOpen = false;	// Status: 	CLOSED CURRENTRELEASE
		String bugId="639417"; 
		try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			restart_rhsmcertd(Integer.valueOf(getConfFileParameter(rhsmConfFile, "certFrequency")), null, false, null);
		}
		// END OF WORKAROUND
		
		// TEMPORARY WORKAROUND FOR Bug 797243 - manual changes to redhat.repo are too sticky
		invokeWorkaroundWhileBugIsOpen = true;
		bugId="797243"; 
		try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			log.warning("Triggering a yum transaction to insure the redhat.repo file is wiped clean");
			sshCommandRunner.runCommandAndWait("yum repolist --disableplugin=rhnplugin"); // --disableplugin=rhnplugin helps avoid: up2date_client.up2dateErrors.AbuseError
		}
		// END OF WORKAROUND
		
		return sshCommandResult; // from the register command
	}
	
	public SSHCommandResult register(String username, String password, String org, String environment, ConsumerType type, String name, String consumerid, Boolean autosubscribe, String servicelevel, String release, String activationkey, String serverurl, String baseurl, Boolean force, Boolean autoheal, String proxy, String proxyuser, String proxypassword) {
		List<String> activationkeys = activationkey==null?null:Arrays.asList(new String[]{activationkey});

		return register(username, password, org, environment, type, name, consumerid, autosubscribe, servicelevel, release, activationkeys, serverurl, baseurl, force, autoheal, proxy, proxyuser, proxypassword);
	}
	
	public SSHCommandResult register(String username, String password, String org) {
		return register(username, password, org, null, null, null, null, null, null, null, (String)null, null, null, null, null, null, null, null);
	}
	
	// reregister module tasks ************************************************************

//	/**
//	 * reregister without asserting results
//	 */
//	public SSHCommandResult reregister_(String username, String password, String consumerid) {
//
//		// assemble the command
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
//		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "The exit code from the reregister command indicates a success.");
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
		//RemoteFileTasks.runCommandAndWait(sshCommandRunner, "rm -f "+consumerCertFile, TestRecords.action());
		//removeAllCerts(true, true);
		clean(null, null, null);
		return register(username,password,null,null,null,null,consumerId, null, null, null, new ArrayList<String>(), null, null, null, null, null, null, null);
	}
	
	
	
	// clean module tasks ************************************************************

	/**
	 * clean without asserting results
	 * @param proxy TODO
	 * @param proxyuser TODO
	 * @param proxypassword TODO
	 */
	public SSHCommandResult clean_(String proxy, String proxyuser, String proxypassword) {

		// assemble the command
		String command = this.command;	command += " clean";
		if (proxy!=null)				command += " --proxy="+proxy;
		if (proxyuser!=null)			command += " --proxyuser="+proxyuser;
		if (proxypassword!=null)		command += " --proxypassword="+proxypassword;
		
		// run command without asserting results
		return sshCommandRunner.runCommandAndWait(command);
	}
	
	/**
	 * "subscription-manager-cli clean"
	 * @param proxy TODO
	 * @param proxyuser TODO
	 * @param proxypassword TODO
	 */
	public SSHCommandResult clean(String proxy, String proxyuser, String proxypassword) {
		
		SSHCommandResult sshCommandResult = clean_(proxy, proxyuser, proxypassword);
		
		// assert results for a successful clean
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "The exit code from the clean command indicates a success.");
		Assert.assertEquals(sshCommandResult.getStdout().trim(), "All local data removed");
		
		// assert that the consumer cert directory is gone
		Assert.assertFalse(RemoteFileTasks.testExists(sshCommandRunner,consumerCertDir), consumerCertDir+" does NOT exist after clean.");
		this.currentlyRegisteredUsername = null;
		this.currentlyRegisteredPassword = null;
		this.currentlyRegisteredOrg = null;
		this.currentlyRegisteredType = null;
		
		// assert that the entitlement cert directory is gone
		//Assert.assertFalse(RemoteFileTasks.testFileExists(sshCommandRunner,entitlementCertDir)==1, entitlementCertDir+" does NOT exist after clean.");
		// assert that the entitlement cert directory is gone (or is empty)
		if (RemoteFileTasks.testExists(sshCommandRunner,entitlementCertDir)) {
			Assert.assertEquals(sshCommandRunner.runCommandAndWait("ls "+entitlementCertDir).getStdout(), "", "The entitlement cert directory is empty after running clean.");
		}

		return sshCommandResult; // from the clean command
	}
	
	
	
	// import module tasks ************************************************************

	/**
	 * import WITHOUT asserting results
	 * @param certificates - list of paths to certificate files to be imported
	 * @return
	 */
	public SSHCommandResult importCertificate_(List<String> certificates/*, String proxy, String proxyuser, String proxypassword*/) {

		// assemble the command
		String command = this.command;									command += " import";
		if (certificates!=null)	for (String certificate : certificates)	command += " --certificate="+certificate;

//		if (proxy!=null)				command += " --proxy="+proxy;
//		if (proxyuser!=null)			command += " --proxyuser="+proxyuser;
//		if (proxypassword!=null)		command += " --proxypassword="+proxypassword;
		
		// run command without asserting results
		return sshCommandRunner.runCommandAndWait(command);
	}
	
	/**
	 * import WITHOUT asserting results.
	 */
	public SSHCommandResult importCertificate_(String certificate/*, String proxy, String proxyuser, String proxypassword*/) {
		
		List<String> certificates = certificate==null?null:Arrays.asList(new String[]{certificate});

		return importCertificate_(certificates/*, proxy, proxyuser, proxypassword*/);
	}
	
	/**
	 * import with assertions that the results are a success"
	 * @param certificates - list of paths to certificates file to be imported
	 * @return
	 */
	public SSHCommandResult importCertificate(List<String> certificates/*, String proxy, String proxyuser, String proxypassword*/) {
		
		SSHCommandResult sshCommandResult = importCertificate_(certificates/*, proxy, proxyuser, proxypassword*/);
		
		// assert results for a successful import
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "The exit code from the import command indicates a success.");
		
		// Successfully imported certificate {0}
		for (String certificate: certificates) {
			String successMsg = "Successfully imported certificate "+(new File(certificate)).getName();
			Assert.assertTrue(sshCommandResult.getStdout().contains(successMsg),"The stdout from the import command contains expected message: "+successMsg);		
		}
	
		// {0} is not a valid certificate file. Please use a valid certificate.
		
		// assert that the entitlement certificate has been extracted to /etc/pki/entitlement
		//Assert.assertTrue(RemoteFileTasks.testFileExists(sshCommandRunner,consumerCertDir)==1, consumerCertDir+" does NOT exist after clean.");

		// assert that the key has been extracted to /etc/pki/entitlement
		//Assert.assertTrue(RemoteFileTasks.testFileExists(sshCommandRunner,consumerCertDir)==1, consumerCertDir+" does NOT exist after clean.");

		return sshCommandResult; // from the import command
	}
	
	public SSHCommandResult importCertificate(String certificate/*, String proxy, String proxyuser, String proxypassword*/) {
		
		List<String> certificates = certificate==null?null:Arrays.asList(new String[]{certificate});

		return importCertificate(certificates/*, proxy, proxyuser, proxypassword*/);
	}
	
	// refresh module tasks ************************************************************

	/**
	 * refresh without asserting results
	 * @param proxy TODO
	 * @param proxyuser TODO
	 * @param proxypassword TODO
	 */
	public SSHCommandResult refresh_(String proxy, String proxyuser, String proxypassword) {

		// assemble the command
		String command = this.command;	command += " refresh";
		if (proxy!=null)				command += " --proxy="+proxy;
		if (proxyuser!=null)			command += " --proxyuser="+proxyuser;
		if (proxypassword!=null)		command += " --proxypassword="+proxypassword;
		
		// run command without asserting results
		return sshCommandRunner.runCommandAndWait(command);
	}
	
	/**
	 * "subscription-manager-cli refresh"
	 * @param proxy TODO
	 * @param proxyuser TODO
	 * @param proxypassword TODO
	 */
	public SSHCommandResult refresh(String proxy, String proxyuser, String proxypassword) {
		
		SSHCommandResult sshCommandResult = refresh_(proxy, proxyuser, proxypassword);
		
		// assert results for a successful clean
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "The exit code from the refresh command indicates a success.");
		Assert.assertEquals(sshCommandResult.getStdout().trim(), "All local data refreshed");
		
		return sshCommandResult; // from the refresh command
	}
	
	
	
	// identity module tasks ************************************************************

	/**
	 * identity without asserting results
	 * @param username
	 * @param password
	 * @param regenerate
	 * @param force
	 * @param proxy
	 * @param proxyuser
	 * @param proxypassword
	 * @return
	 */
	public SSHCommandResult identity_(String username, String password, Boolean regenerate, Boolean force, String proxy, String proxyuser, String proxypassword) {

		// assemble the command
		String command = this.command;		command += " identity";
		if (username!=null)					command += " --username="+username;
		if (password!=null)					command += " --password="+password;
		if (regenerate!=null && regenerate)	command += " --regenerate";
		if (force!=null && force)			command += " --force";
		if (proxy!=null)					command += " --proxy="+proxy;
		if (proxyuser!=null)				command += " --proxyuser="+proxyuser;
		if (proxypassword!=null)			command += " --proxypassword="+proxypassword;
		
		// run command without asserting results
		return sshCommandRunner.runCommandAndWait(command);
	}
	
	/**
	 * "subscription-manager-cli identity"
	 * @param username
	 * @param password
	 * @param regenerate
	 * @param force
	 * @param proxy
	 * @param proxyuser
	 * @param proxypassword
	 * @return
	 */
	public SSHCommandResult identity(String username, String password, Boolean regenerate, Boolean force, String proxy, String proxyuser, String proxypassword) {
		
		SSHCommandResult sshCommandResult = identity_(username, password, regenerate, force, proxy, proxyuser, proxypassword);
		regenerate = regenerate==null? false:regenerate;	// the non-null default value for regenerate is false

		// assert results for a successful identify
		/* Example sshCommandResult.getStdout():
		 * Current identity is: 8f4dd91a-2c41-4045-a937-e3c8554a5701 name: testuser1
		 */
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "The exit code from the identity command indicates a success.");
		
		
		// TEMPORARY WORKAROUND FOR BUG: https://bugzilla.redhat.com/show_bug.cgi?id=719109 - jsefler 7/05/2011
		boolean invokeWorkaroundWhileBugIsOpen = true;
		String bugId="719109"; 
		try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			// skip the assertion of user feedback in stdout
			return sshCommandResult;
		}
		// END OF WORKAROUND
		
		
		if (regenerate) {
			Assert.assertEquals(sshCommandResult.getStdout().trim(), "Identity certificate has been regenerated.");
		}
// DELETEME
// DON'T ASSERT THIS HERE.  WILL BE ASSERTED IN OTHER TESTS. IT'S POSSIBLE THAT THIS IS NOT EXPECTED EVEN THOUGH EXIT CODE IS 0 (e.g when registered classically)
//		else {
//			Assert.assertContainsMatch(sshCommandResult.getStdout().trim(), "Current identity is: [a-f,0-9,\\-]{36}");
//		}
		
		return sshCommandResult; // from the identity command
	}
	
	
	// orgs module tasks ************************************************************

	/**
	 * orgs without asserting results
	 * @param username
	 * @param password
	 * @param serverurl TODO
	 * @return
	 */
	public SSHCommandResult orgs_(String username, String password, String serverurl, String proxy, String proxyuser, String proxypassword) {

		// assemble the command
		String command = this.command;	command += " orgs";
		if (username!=null)				command += " --username="+username;
		if (password!=null)				command += " --password="+password;
		if (serverurl!=null)			command += " --serverurl="+serverurl;
		if (proxy!=null)				command += " --proxy="+proxy;
		if (proxyuser!=null)			command += " --proxyuser="+proxyuser;
		if (proxypassword!=null)		command += " --proxypassword="+proxypassword;
		
		// run command without asserting results
		return sshCommandRunner.runCommandAndWait(command);
	}
	
	/**
	 * "subscription-manager orgs"
	 * @param username
	 * @param password
	 * @param serverurl TODO
	 * @return
	 */
	public SSHCommandResult orgs(String username, String password, String serverurl, String proxy, String proxyuser, String proxypassword) {
		
		SSHCommandResult sshCommandResult = orgs_(username, password, serverurl, proxy, proxyuser, proxypassword);
		
		// assert results...
		/*
		[root@jsefler-r63-server ~]# subscription-manager orgs --username testuser1 --password password
		+-------------------------------------------+
		          testuser1 Organizations
		+-------------------------------------------+

		OrgName: 	Admin Owner              
		OrgKey: 	admin                    

		OrgName: 	Snow White               
		OrgKey: 	snowwhite                
		*/

		// assert the banner
		String bannerRegex = "\\+-+\\+\\n\\s*"+username+" Organizations\\s*\\n\\+-+\\+";
		Assert.assertTrue(Pattern.compile(".*"+bannerRegex+".*",Pattern.DOTALL).matcher(sshCommandResult.getStdout()).find(),"Stdout from orgs contains the expected banner regex: "+bannerRegex);

		// assert the exit code was a success
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "The exit code from the orgs command indicates a success.");

		return sshCommandResult; // from the orgs command
	}
	
	
	// service-level module tasks ************************************************************

	/**
	 * service_level without asserting results
	 * @param show
	 * @param list
	 * @param set
	 * @param unset TODO
	 * @param username
	 * @param password
	 * @param org
	 * @param serverurl TODO
	 * @param proxy
	 * @param proxyuser
	 * @param proxypassword
	 * @return
	 */
	public SSHCommandResult service_level_(Boolean show, Boolean list, String set, Boolean unset, String username, String password, String org, String serverurl, String proxy, String proxyuser, String proxypassword) {

		// assemble the command
		String command = this.command;	command += " service-level";
		if (show!=null && show)			command += " --show";
		if (list!=null && list)			command += " --list";
		if (set!=null)					command += " --set="+String.format("\"%s\"", set);
		if (unset!=null && unset)		command += " --unset";
		if (username!=null)				command += " --username="+username;
		if (password!=null)				command += " --password="+password;
		if (org!=null)					command += " --org="+org;
		if (serverurl!=null)			command += " --serverurl="+serverurl;
		if (proxy!=null)				command += " --proxy="+proxy;
		if (proxyuser!=null)			command += " --proxyuser="+proxyuser;
		if (proxypassword!=null)		command += " --proxypassword="+proxypassword;

		// run command without asserting results
		return sshCommandRunner.runCommandAndWait(command);
	}
	
	/**
	 * "subscription-manager service-level"
	 * @param show
	 * @param list
	 * @param set
	 * @param unset TODO
	 * @param username
	 * @param password
	 * @param org
	 * @param serverurl TODO
	 * @param proxy
	 * @param proxyuser
	 * @param proxypassword
	 * @return
	 */
	public SSHCommandResult service_level(Boolean show, Boolean list, String set, Boolean unset, String username, String password, String org, String serverurl, String proxy, String proxyuser, String proxypassword) {
		
		SSHCommandResult sshCommandResult = service_level_(show, list, set, unset, username, password, org, serverurl, proxy, proxyuser, proxypassword);
		
		// assert results...
		/*
		[root@jsefler-r63-server ~]# subscription-manager service-level --show --list
		Current service level: 
		+-------------------------------------------+
          			Available Service Levels
		+-------------------------------------------+
		Standard
		None
		Premium
		*/
		
				
		if (Boolean.valueOf(System.getProperty("sm.server.old","false"))) {
			Assert.assertEquals(sshCommandResult.getStderr().trim(), "ERROR: The service-level command is not supported by the server.");
			throw new SkipException(sshCommandResult.getStderr().trim());
 		}
 		 			
		// assert the banner
		String bannerRegex = "\\+-+\\+\\n\\s*Available Service Levels\\s*\\n\\+-+\\+";
		if (list!=null && list) {	// when explicitly asked to list
			Assert.assertTrue(Pattern.compile(".*"+bannerRegex+".*",Pattern.DOTALL).matcher(sshCommandResult.getStdout()).find(),"Stdout from service-level (with option --list) contains the expected banner regex: "+bannerRegex);
		} else {
			Assert.assertTrue(!Pattern.compile(".*"+bannerRegex+".*",Pattern.DOTALL).matcher(sshCommandResult.getStdout()).find(),"Stdout from service-level (without option --list) does not contain the banner regex: "+bannerRegex);	
		}
		
		// assert the "Current service level: "
		String serviceLevelMsg = "Current service level: ";
		String serviceLevelNotSetMsg = "Service level preference not set";	// Bug 825286 - subscription-manager service-level --show
		if ((show!=null && show) // when explicitly asked to show
			|| ((show==null || !show) && (list==null || !list) && (set==null) && (unset==null || !unset)) ){	// or when no options are explicity asked, then the default behavior is --show
			if (!sshCommandResult.getStdout().contains(serviceLevelNotSetMsg)) {
				Assert.assertTrue(sshCommandResult.getStdout().contains(serviceLevelMsg),"Stdout from service-level (with option --show) contains the expected feedback: "+serviceLevelMsg);
			} else {
				Assert.assertTrue(!sshCommandResult.getStdout().contains(serviceLevelMsg),"Stdout from service-level (without option --show) does not contain feedback: "+serviceLevelNotSetMsg);
			}
		}
		
		// assert the "Service level set to: "
		String serviceLevelSetMsg = "Service level set to: ";
		if (set!=null && !set.isEmpty()) {
			Assert.assertTrue(sshCommandResult.getStdout().contains(serviceLevelSetMsg+set),"Stdout from service-level (with option --set) contains the expected feedback: "+serviceLevelSetMsg+set);
		} else {
			// TEMPORARY WORKAROUND FOR BUG
			boolean invokeWorkaroundWhileBugIsOpen = true;
			try {String bugId="835050"; if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
			if (invokeWorkaroundWhileBugIsOpen) {
				log.warning("Skipping service-level unset feedback message while this bug is open.");
			} else
			// END OF WORKAROUND
			Assert.assertTrue(!sshCommandResult.getStdout().contains(serviceLevelSetMsg),"Stdout from service-level (without option --set) does not contain feedback: "+serviceLevelSetMsg);
		}
		
		// assert the "Service level preference has been unset"
		String serviceLevelUnsetMsg = "Service level preference has been unset";
		if ((unset!=null && unset) || (set!=null && set.isEmpty())) {
			Assert.assertTrue(sshCommandResult.getStdout().contains(serviceLevelUnsetMsg),"Stdout from service-level (with option --unset) contains the expected feedback: "+serviceLevelUnsetMsg);
		} else {
			Assert.assertTrue(!sshCommandResult.getStdout().contains(serviceLevelUnsetMsg),"Stdout from service-level (without option --unset) does not contain the feedback: "+serviceLevelUnsetMsg);
		}
		
		// assert the exit code was a success
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "The exit code from the service-level command indicates a success.");
		
		return sshCommandResult; // from the service-level command
	}
	
	
	// release module tasks ************************************************************

	/**
	 * SSHCommand subscription-manager release [parameters] without asserting any results
	 * @param show
	 * @param list
	 * @param set
	 * @param unset
	 * @param proxy
	 * @param proxyuser
	 * @param proxypassword
	 * @return SSHCommandResult stdout, stderr, exitCode
	 */
	public SSHCommandResult release_(Boolean show, Boolean list, String set, Boolean unset, String proxy, String proxyuser, String proxypassword) {

		// assemble the command
		String command = this.command;	command += " release";
		if (show!=null && show)			command += " --show";
		if (list!=null && list)			command += " --list";
		if (set!=null)					command += " --set="+(set.equals("")?"\"\"":set);	// quote an empty string
		if (unset!=null && unset)		command += " --unset";
		if (proxy!=null)				command += " --proxy="+proxy;
		if (proxyuser!=null)			command += " --proxyuser="+proxyuser;
		if (proxypassword!=null)		command += " --proxypassword="+proxypassword;
		
		// run command without asserting results
		return sshCommandRunner.runCommandAndWait(command);
	}
	
	/**
	 * SSHCommand subscription-manager release [parameters]
	 * @param show
	 * @param list
	 * @param set
	 * @param unset
	 * @param proxy
	 * @param proxyuser
	 * @param proxypassword
	 * @return SSHCommandResult stdout, stderr, exitCode
	 */
	public SSHCommandResult release(Boolean show, Boolean list, String set, Boolean unset, String proxy, String proxyuser, String proxypassword) {
		
		SSHCommandResult sshCommandResult = release_(show, list, set, unset, proxy, proxyuser, proxypassword);
		
		// assert results...
		if (list==null)
		if (Boolean.valueOf(System.getProperty("sm.server.old","false"))) {
			Assert.assertEquals(sshCommandResult.getStderr().trim(), "ERROR: The 'release' command is not supported by the server.");
			throw new SkipException(sshCommandResult.getStderr().trim());
 		}
		
		
//TODO
//		/*
//		[root@jsefler-r63-server ~]# subscription-manager service-level --show --list
//		Current service level: 
//		+-------------------------------------------+
//          			Available Service Levels
//		+-------------------------------------------+
//		Standard
//		None
//		Premium
//		*/
//		
//		// assert the banner
//		String bannerRegex = "\\+-+\\+\\n\\s*Available Service Levels\\s*\\n\\+-+\\+";
//		if (list!=null && list) {	// when explicitly asked to list
//			Assert.assertTrue(Pattern.compile(".*"+bannerRegex+".*",Pattern.DOTALL).matcher(sshCommandResult.getStdout()).find(),"Stdout from service-level (with option --list) contains the expected banner regex: "+bannerRegex);
//		} else {
//			Assert.assertTrue(!Pattern.compile(".*"+bannerRegex+".*",Pattern.DOTALL).matcher(sshCommandResult.getStdout()).find(),"Stdout from service-level (without option --list) should not contains the banner regex: "+bannerRegex);	
//		}
//		
//		// assert the "Current service level: "
//		String regex = "Current service level: ";
//		if (show!=null && show) {	// when explicitly asked to show
//			Assert.assertTrue(Pattern.compile(".*"+regex+".*",Pattern.DOTALL).matcher(sshCommandResult.getStdout()).find(),"Stdout from service-level (with option --show) contains the expected regex: "+regex);
//		} else if (list!=null && list) {	// when explicitly asked to list but not show
//			Assert.assertTrue(!Pattern.compile(".*"+regex+".*",Pattern.DOTALL).matcher(sshCommandResult.getStdout()).find(),"Stdout from service-level (with option --list, but not --show) should not contains the regex: "+regex);	
//		} else if ((show==null || !show) && (list==null || !list)) {	// when no options are explicity asked, then the default behavior is --show
//			Assert.assertTrue(Pattern.compile(".*"+regex+".*",Pattern.DOTALL).matcher(sshCommandResult.getStdout()).find(),"Stdout from service-level (without options --show --list) contains the expected regex: "+regex);		
//		}
		
		if (set!=null) {
			Assert.assertEquals(sshCommandResult.getStdout().trim(), String.format("Release set to: %s", set).trim(),"Stdout from release --set with a value.");
		}
		if (unset!=null) {
			Assert.assertEquals(sshCommandResult.getStdout().trim(), "Release preference has been unset","Stdout from release --unset.");
		}
		
		// assert the exit code was a success
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "The exit code from the release command indicates a success.");
		
		return sshCommandResult; // from the service-level command
	}
	
	
	// config module tasks ************************************************************

	/**
	 * config without asserting results
	 */
	public SSHCommandResult config_(Boolean list, Boolean remove, Boolean set, List<String[]> listOfSectionNameValues) {
		
		// assemble the command
		String command = this.command;				command += " config";
		if (list!=null && list)						command += " --list";
		for (String[] section_name_value : listOfSectionNameValues) {
			// double quote the value when necessary
			if (listOfSectionNameValues.size()>2 && section_name_value[2].equals("")) section_name_value[2] = "\"\"";	// double quote blank values
			if (listOfSectionNameValues.size()>2 && section_name_value[2].contains(" ")) section_name_value[2] = "\""+section_name_value[2]+"\"";	// double quote value containing spaces (probably never used)

			if (remove!=null && remove)				command += String.format(" --remove=%s.%s", section_name_value[0],section_name_value[1]);  // expected format section.name
			if (set!=null && set)					command += String.format(" --%s.%s=%s", section_name_value[0],section_name_value[1],section_name_value[2]);  // expected format section.name=value
		}
		
		// run command without asserting results
		return sshCommandRunner.runCommandAndWait(command);
	}
	
	/**
	 * config without asserting results
	 */
	public SSHCommandResult config_(Boolean list, Boolean remove, Boolean set, String[] section_name_value) {
		List<String[]> listOfSectionNameValues = new ArrayList<String[]>();
		listOfSectionNameValues.add(section_name_value);
		return config_(list, remove, set, listOfSectionNameValues);
	}
	
	/**
	 * "subscription-manager config"
	 */
	public SSHCommandResult config(Boolean list, Boolean remove, Boolean set, List<String[]> listOfSectionNameValues) {
		
		SSHCommandResult sshCommandResult = config_(list, remove, set, listOfSectionNameValues);
		
		// assert results...
		
		// assert the exit code was a success
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "The exit code from the config command indicates a success.");

		/*
		[root@jsefler-onprem-62server ~]# subscription-manager config --list
		[server]
		   ca_cert_dir = [/etc/rhsm/ca/]
		   hostname = jsefler-onprem-62candlepin.usersys.redhat.com
		   insecure = [0]
		   port = [8443]
		   prefix = [/candlepin]
		   proxy_hostname = []
		   proxy_password = []
		   proxy_port = []
		   proxy_user = []
		   repo_ca_cert = [/etc/rhsm/ca/redhat-uep.pem]
		   ssl_verify_depth = [3]

		[rhsm]
		   baseurl = https://cdn.redhat.com
		   ca_cert_dir = [/etc/rhsm/ca/]
		   certfrequency = 2400
		   consumercertdir = /etc/pki/consumer
		   entitlementcertdir = /etc/pki/entitlement
		   hostname = [localhost]
		   insecure = [0]
		   port = [8443]
		   prefix = [/candlepin]
		   productcertdir = /etc/pki/product
		   proxy_hostname = []
		   proxy_password = []
		   proxy_port = BAR
		   proxy_user = []
		   repo_ca_cert = [/etc/rhsm/ca/redhat-uep.pem]
		   ssl_verify_depth = [3]

		[rhsmcertd]
		   ca_cert_dir = [/etc/rhsm/ca/]
		   certfrequency = 240
		   hostname = [localhost]
		   insecure = [0]
		   port = [8443]
		   prefix = [/candlepin]
		   proxy_hostname = []
		   proxy_password = []
		   proxy_port = []
		   proxy_user = []
		   repo_ca_cert = [/etc/rhsm/ca/redhat-uep.pem]
		   ssl_verify_depth = [3]

		[] - Default value in use

		[root@jsefler-onprem-62server ~]# echo $?
		0
		[root@jsefler-onprem-62server ~]# subscription-manager config --remove=rhsmcertd.certfrequency
		You have removed the value for section rhsmcertd and name certfrequency.
		[root@jsefler-onprem-62server ~]# echo $?
		0
		[root@jsefler-onprem-62server ~]# subscription-manager config --rhsmcertd.certfrequency=240
		[root@jsefler-onprem-62server ~]# echo $?
		0
		[root@jsefler-onprem-62server ~]# 
		 */
		
		// assert remove stdout indicates a success
		if (remove!=null && remove) {
			for (String[] section_name_value : listOfSectionNameValues) {
				String section	= section_name_value[0];
				String name		= section_name_value[1];
				String value	= section_name_value[2];
				//# subscription-manager config --remove rhsmcertd.port
				//You have removed the value for section rhsmcertd and name port.
				//The default value for port will now be used.
				//Assert.assertTrue(sshCommandResult.getStdout().contains("You have removed the value for section "+section+" and name "+name+".\nThe default value for "+name+" will now be used."), "The stdout indicates the removal of config parameter name '"+name+"' from section '"+section+"'.");
				Assert.assertTrue(sshCommandResult.getStdout().contains("You have removed the value for section "+section+" and name "+name.toLowerCase()+"."), "The stdout indicates the removal of config parameter name '"+name+"' from section '"+section+"'.");
				Assert.assertEquals(sshCommandResult.getStdout().contains("The default value for "+name.toLowerCase()+" will now be used."), defaultConfFileParameterNames(true).contains(name), "The stdout indicates the default value for '"+name+"' will now be used after having removed it from section '"+section+"'.");
			}
		}

		
		return sshCommandResult; // from the orgs command
	}
	
	public SSHCommandResult config(Boolean list, Boolean remove, Boolean set, String[] section_name_value) {
		List<String[]> listOfSectionNameValues = new ArrayList<String[]>();
		listOfSectionNameValues.add(section_name_value);
		return config(list, remove, set, listOfSectionNameValues);
	}
	
	public List<String> defaultConfFileParameterNames(Boolean toLowerCase) {
		if (toLowerCase==null) toLowerCase=false;	// return the defaultConfFileParameterNames in lowerCase
		
		// hard-coded list of parameter called DEFAULTS in /usr/lib/python2.6/site-packages/rhsm/config.py
		// this list of hard-coded parameter names have a hard-coded value (not listed here) that will be used
		// after a user calls subscription-manager --remove section.name otherwise the remove will set the value to ""
		List<String> defaultNames = new ArrayList<String>();

		// BEFORE FIX FOR BUG 807721
		// initialize defaultNames (will appear in all config sections and have a default value)
		//	DEFAULTS = {
		//	        'hostname': 'localhost',
		//	        'prefix': '/candlepin',
		//	        'port': '8443',
		//	        'ca_cert_dir': '/etc/rhsm/ca/',
		//	        'repo_ca_cert': '/etc/rhsm/ca/redhat-uep.pem',
		//	        'ssl_verify_depth': '3',
		//	        'proxy_hostname': '',
		//	        'proxy_port': '',
		//	        'proxy_user': '',
		//	        'proxy_password': '',
		//	        'insecure': '0'
		//	        }
		/*
		defaultNames.add("hostname");
		defaultNames.add("prefix");
		defaultNames.add("port");
		defaultNames.add("ca_cert_dir");
		defaultNames.add("repo_ca_cert");
		defaultNames.add("ssl_verify_depth");
		defaultNames.add("proxy_hostname");
		defaultNames.add("proxy_port");
		defaultNames.add("proxy_user");
		defaultNames.add("proxy_password");
		defaultNames.add("insecure");
		*/
		
		// AFTER FIX FOR BUG 807721
		//# Defaults are applied to each section in the config file.
		//DEFAULTS = {
		//                'hostname': 'localhost',
		//                'prefix': '/candlepin',
		//                'port': '8443',
		//                'ca_cert_dir': '/etc/rhsm/ca/',
		//                'repo_ca_cert': '/etc/rhsm/ca/redhat-uep.pem',
		//                'ssl_verify_depth': '3',
		//                'proxy_hostname': '',
		//                'proxy_port': '',
		//                'proxy_user': '',
		//                'proxy_password': '',
		//                'insecure': '0',
		//                'baseurl': 'https://cdn.redhat.com',
		//                'manage_repos': '1',
		//                'productCertDir': '/etc/pki/product',
		//                'entitlementCertDir': '/etc/pki/entitlement',
		//                'consumerCertDir': '/etc/pki/consumer',
		//                'certFrequency': '240',
		//                'healFrequency': '1440',
		//            }
		defaultNames.add("hostname");
		defaultNames.add("prefix");
		defaultNames.add("port");
		defaultNames.add("ca_cert_dir");
		defaultNames.add("repo_ca_cert");
		defaultNames.add("ssl_verify_depth");
		defaultNames.add("proxy_hostname");
		defaultNames.add("proxy_port");
		defaultNames.add("proxy_user");
		defaultNames.add("proxy_password");
		defaultNames.add("insecure");
		defaultNames.add("baseurl");
		defaultNames.add("manage_repos");
		defaultNames.add("productCertDir");
		defaultNames.add("entitlementCertDir");
		defaultNames.add("consumerCertDir");
		defaultNames.add("certFrequency");
		defaultNames.add("healFrequency");
		
		// lowercase all of the defaultNames when requested
		if (toLowerCase) for (String defaultName : defaultNames) {
			defaultNames.set(defaultNames.indexOf(defaultName), defaultName.toLowerCase());
		}
		
		return defaultNames;
	}
	
	// environments module tasks ************************************************************

	/**
	 * environments without asserting results
	 * @param username
	 * @param password
	 * @param org
	 * @param serverurl TODO
	 * @return
	 */
	public SSHCommandResult environments_(String username, String password, String org, String serverurl, String proxy, String proxyuser, String proxypassword) {

		// assemble the command
		String command = this.command;	command += " environments";
		if (username!=null)				command += " --username="+username;
		if (password!=null)				command += " --password="+password;
		if (org!=null)					command += " --org="+org;
		if (serverurl!=null)			command += " --serverurl="+serverurl;
		if (proxy!=null)				command += " --proxy="+proxy;
		if (proxyuser!=null)			command += " --proxyuser="+proxyuser;
		if (proxypassword!=null)		command += " --proxypassword="+proxypassword;
		
		// run command without asserting results
		return sshCommandRunner.runCommandAndWait(command);
	}
	
	/**
	 * "subscription-manager environments"
	 * @param username
	 * @param password
	 * @param org
	 * @param serverurl TODO
	 * @return
	 */
	public SSHCommandResult environments(String username, String password, String org, String serverurl, String proxy, String proxyuser, String proxypassword) {
		
		SSHCommandResult sshCommandResult = environments_(username, password, org, serverurl, proxy, proxyuser, proxypassword);
		
		// TODO assert results...
		
		// assert the exit code was a success
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "The exit code from the environments command indicates a success.");
		
		return sshCommandResult; // from the environments command
	}
	
	
	// unregister module tasks ************************************************************

	/**
	 * unregister without asserting results
	 * @param proxy TODO
	 * @param proxyuser TODO
	 * @param proxypassword TODO
	 */
	public SSHCommandResult unregister_(String proxy, String proxyuser, String proxypassword) {

		// assemble the command
		String command = this.command;	command += " unregister";
		if (proxy!=null)				command += " --proxy="+proxy;
		if (proxyuser!=null)			command += " --proxyuser="+proxyuser;
		if (proxypassword!=null)		command += " --proxypassword="+proxypassword;
		
		workaroundForBug844455();
		
		// run command without asserting results
		SSHCommandResult sshCommandResult = sshCommandRunner.runCommandAndWait(command);
		
		// reset this.currentlyRegistered values
		if (sshCommandResult.getExitCode().equals(Integer.valueOf(0))) {			// success
			this.currentlyRegisteredUsername = null;
			this.currentlyRegisteredPassword = null;
			this.currentlyRegisteredOrg = null;
			this.currentlyRegisteredType = null;
		} else if (sshCommandResult.getExitCode().equals(Integer.valueOf(1))) {		// already unregistered	
		} else if (sshCommandResult.getExitCode().equals(Integer.valueOf(255))) {	// failure
		}
		
		// return the results
		return sshCommandResult;
	}
	
	/**
	 * "subscription-manager-cli unregister"
	 * @param proxy TODO
	 * @param proxyuser TODO
	 * @param proxypassword TODO
	 */
	public SSHCommandResult unregister(String proxy, String proxyuser, String proxypassword) {
		SSHCommandResult sshCommandResult = unregister_(proxy, proxyuser, proxypassword);
		
		// assert results for a successful registration
		if (sshCommandResult.getExitCode()==0) {
			// TEMPORARY WORKAROUND FOR BUG
			boolean invokeWorkaroundWhileBugIsOpen = true;
			try {String bugId="800121"; if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
			if (invokeWorkaroundWhileBugIsOpen) {
				log.warning("If 'NoneType' object message was thrown to stdout during unregister, we will ignore it while this bug is open.");
				Assert.assertTrue(sshCommandResult.getStdout().trim().contains("System has been un-registered."), "The unregister command was a success.");
			} else
			// END OF WORKAROUND
			Assert.assertTrue(sshCommandResult.getStdout().trim().equals("System has been un-registered."), "The unregister command was a success.");
			Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "The exit code from the unregister command indicates a success.");
		} else if (sshCommandResult.getExitCode()==1) {
			Assert.assertTrue(sshCommandResult.getStdout().startsWith("This system is currently not registered."),"The unregister command was not necessary.  It was already unregistered");
		} else {
			Assert.fail("An unexpected exit code ("+sshCommandResult.getExitCode()+") was returned when attempting to unregister.");		
		}
		
		// assert that the consumer cert and key have been removed
		Assert.assertTrue(!RemoteFileTasks.testExists(sshCommandRunner,this.consumerKeyFile()), "Consumer key file '"+this.consumerKeyFile()+"' does NOT exist after unregister.");
		Assert.assertTrue(!RemoteFileTasks.testExists(sshCommandRunner,this.consumerCertFile()), "Consumer cert file '"+this.consumerCertFile()+" does NOT exist after unregister.");
		
		// assert that all of the entitlement certs have been removed (Actually, the entitlementCertDir should get removed)
		Assert.assertTrue(getCurrentEntitlementCertFiles().size()==0, "All of the entitlement certificates have been removed after unregister.");
// FIXME UNCOMMENT SOMETIME IN THE FUTURE.  DOES NOT SEEM TO BE ACCURATE AT THIS TIME 10/25/2010
//		// Bug 852685 - Folder "/etc/pki/entitlement/" cannot be removed after unregistering with subscription-manager via CLI
//		Assert.assertEquals(RemoteFileTasks.testFileExists(sshCommandRunner, entitlementCertDir),0,"Entitlement Cert directory '"+entitlementCertDir+"' should not exist after unregister.");

		return sshCommandResult; // from the unregister command
	}
	
	
	
	// list module tasks ************************************************************
	
	/**
	 * list without asserting results
	 * @param all TODO
	 * @param available TODO
	 * @param consumed TODO
	 * @param installed TODO
	 * @param servicelevel TODO
	 * @param ondate TODO
	 * @param proxy TODO
	 * @param proxyuser TODO
	 * @param proxypassword TODO
	 */
	public SSHCommandResult list_(Boolean all, Boolean available, Boolean consumed, Boolean installed, String servicelevel, String ondate, String proxy, String proxyuser, String proxypassword) {

		// assemble the command
		String command = this.command;		command += " list";	
		if (all!=null && all)				command += " --all";
		if (available!=null && available)	command += " --available";
		if (consumed!=null && consumed)		command += " --consumed";
		if (installed!=null && installed)	command += " --installed";
		if (ondate!=null)					command += " --ondate="+ondate;
		if (servicelevel!=null)				command += " --servicelevel="+String.format(servicelevel.contains(" ")||servicelevel.isEmpty()?"\"%s\"":"%s", servicelevel);	// quote a value containing spaces or is empty
		if (proxy!=null)					command += " --proxy="+proxy;
		if (proxyuser!=null)				command += " --proxyuser="+proxyuser;
		if (proxypassword!=null)			command += " --proxypassword="+proxypassword;
		
		// run command without asserting results
		return sshCommandRunner.runCommandAndWait(command);
	}
	
	public SSHCommandResult list(Boolean all, Boolean available, Boolean consumed, Boolean installed, String servicelevel, String ondate, String proxy, String proxyuser, String proxypassword) {
		
		SSHCommandResult sshCommandResult = list_(all, available, consumed, installed, servicelevel, ondate, proxy, proxyuser, proxypassword);
		
		// assert results...
		
		// assert the exit code was a success
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "The exit code from the list module indicates a success.");
		
		return sshCommandResult; // from the list command
	}
	
	/**
	 * @return SSHCommandResult from "subscription-manager-cli list --installed"
	 */
	public SSHCommandResult listInstalledProducts() {
		
		SSHCommandResult sshCommandResult = list(null,null,null,Boolean.TRUE, null, null, null, null, null);
		
		if (getCurrentProductCertFiles().isEmpty() /*&& getCurrentEntitlementCertFiles().isEmpty() NOT NEEDED AFTER DESIGN CHANGE FROM BUG 736424*/) {
			Assert.assertTrue(sshCommandResult.getStdout().trim().equals("No installed products to list"), "No installed products to list");
		} else {
			//Assert.assertContainsMatch(sshCommandResult.getStdout(), "Installed Product Status"); // produces too much logging
			String title = "Installed Product Status";
			Assert.assertTrue(sshCommandResult.getStdout().contains(title),"The list of installed products is entitled '"+title+"'.");
		}

		return sshCommandResult;
	}
	
	/**
	 * @return SSHCommandResult from "subscription-manager-cli list --available"
	 */
	public SSHCommandResult listAvailableSubscriptionPools() {

		SSHCommandResult sshCommandResult = list(null,Boolean.TRUE,null, null, null, null, null, null, null);

		//Assert.assertContainsMatch(sshCommandResult.getStdout(), "Available Subscriptions"); // produces too much logging

		return sshCommandResult;
	}
	
	/**
	 * @return SSHCommandResult from "subscription-manager-cli list --all --available"
	 */
	public SSHCommandResult listAllAvailableSubscriptionPools() {

		// TEMPORARY WORKAROUND FOR BUG: https://bugzilla.redhat.com/show_bug.cgi?id=638266 - jsefler 9/28/2010
		boolean invokeWorkaroundWhileBugIsOpen = false;
		String bugId="638266"; 
		try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			return list_(Boolean.FALSE,Boolean.TRUE,null, null, null, null, null, null, null);
		}
		// END OF WORKAROUND
		
		SSHCommandResult sshCommandResult = list(Boolean.TRUE,Boolean.TRUE,null, null, null, null, null, null, null);
		
		//Assert.assertContainsMatch(sshCommandResult.getStdout(), "Available Subscriptions"); // produces too much logging

		return sshCommandResult;
		
	}
	
	/**
	 * @return SSHCommandResult from "subscription-manager-cli list --consumed"
	 */
	public SSHCommandResult listConsumedProductSubscriptions() {

		SSHCommandResult sshCommandResult = list(null,null,Boolean.TRUE, null, null, null, null, null, null);
		
		List<File> entitlementCertFiles = getCurrentEntitlementCertFiles();

		if (entitlementCertFiles.isEmpty()) {
			Assert.assertTrue(sshCommandResult.getStdout().trim().equals("No consumed subscription pools to list"), "No consumed subscription pools to list");
		} else {
			String title = "Consumed Product Subscriptions";
			title = "Consumed Subscriptions";	// changed in https://bugzilla.redhat.com/show_bug.cgi?id=806986#c10
			//Assert.assertContainsMatch(sshCommandResult.getStdout(), title); // produces too much logging
			Assert.assertTrue(sshCommandResult.getStdout().contains(title),"The list of consumed products is entitled '"+title+"'.");
		}

		return sshCommandResult;
	}
	
	

	

	
	// redeem module tasks ************************************************************

	/**
	 * redeem without asserting results
	 * @param email TODO
	 * @param locale TODO
	 * @param serverurl TODO
	 * @param proxy TODO
	 * @param proxyuser TODO
	 * @param proxypassword TODO
	 */
	public SSHCommandResult redeem_(String email, String locale, String serverurl, String proxy, String proxyuser, String proxypassword) {
		
		// assemble the command
		String command = this.command;	command += " redeem";
		if (email!=null)				command += " --email="+email;
		if (locale!=null)				command += " --locale="+locale;
		if (serverurl!=null)			command += " --serverurl="+serverurl;
		if (proxy!=null)				command += " --proxy="+proxy;
		if (proxyuser!=null)			command += " --proxyuser="+proxyuser;
		if (proxypassword!=null)		command += " --proxypassword="+proxypassword;
		
		// run command without asserting results
		return sshCommandRunner.runCommandAndWait(command);
	}

	public SSHCommandResult redeem(String email, String locale, String serverurl, String proxy, String proxyuser, String proxypassword) {

		SSHCommandResult sshCommandResult = redeem_(email, locale, serverurl, proxy, proxyuser, proxypassword);
		
		// TODO assert results...
		
		return sshCommandResult;
	}
	
	
	
	// repos module tasks ************************************************************

	/**
	 * @return SSHCommandResult from subscription-manager repos [parameters] without asserting any results
	 */
	public SSHCommandResult repos_(Boolean list, List<String> enableRepos, List<String> disableRepos, String proxy,String proxyuser,String proxypassword) {
		
		// assemble the command
		String command = this.command;									command += " repos";
		if (list!=null && list)											command += " --list";
		if (enableRepos!=null)	for (String enableRepo : enableRepos)	command += " --enable="+enableRepo;
		if (disableRepos!=null)	for (String disableRepo : disableRepos)	command += " --disable="+disableRepo;
		if (proxy!=null)												command += " --proxy="+proxy;
		if (proxyuser!=null)											command += " --proxyuser="+proxyuser;
		if (proxypassword!=null)										command += " --proxypassword="+proxypassword;
		
		// run command without asserting results
		return sshCommandRunner.runCommandAndWait(command);
	}
	public SSHCommandResult repos_(Boolean list, String enableRepo, String disableRepo, String proxy,String proxyuser,String proxypassword) {
		List<String> enableRepos = enableRepo==null?null:Arrays.asList(new String[]{enableRepo});
		List<String> disableRepos = disableRepo==null?null:Arrays.asList(new String[]{disableRepo});
		return repos_(list, enableRepos, disableRepos, proxy, proxyuser, proxypassword);
	}

	/**
	 * @return SSHCommandResult from subscription-manager repos [parameters]
	 */
	public SSHCommandResult repos(Boolean list, List<String> enableRepos, List<String> disableRepos, String proxy,String proxyuser,String proxypassword) {

		SSHCommandResult sshCommandResult = repos_(list, enableRepos, disableRepos, proxy,proxyuser,proxypassword);
		
		// assert results...
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "The exit code from the repos command indicates a success.");
		
		// when rhsm.manage_repos is off, this feedback overrides all operations
		String manage_repos = getConfFileParameter(rhsmConfFile, "rhsm", "manage_repos"); if (manage_repos==null) manage_repos="1";
		if (manage_repos.equals("0")) {
			//Assert.assertEquals(sshCommandResult.getStdout().trim(), "Repositories disabled by configuration.","Stdout when rhsm.manage_repos is configured to 0.");
			//Assert.assertEquals(sshCommandResult.getStdout().trim(), "Repositories disabled by configuration.\nThe system is not entitled to use any repositories.","Stdout when rhsm.manage_repos is configured to 0.");
			Assert.assertEquals(sshCommandResult.getStdout().trim(), "Repositories disabled by configuration.\nThis system has no repositories available through subscriptions.","Stdout when rhsm.manage_repos is configured to 0.");
			return sshCommandResult;
		}
		
		// assert list feedback
		String bannerRegex;
		bannerRegex = "\\+-+\\+\\n\\s*Entitled Repositories in "+redhatRepoFile+"\\s*\\n\\+-+\\+";	// changed by bug 846834
		bannerRegex = "\\+-+\\+\\n\\s*Available Repositories in "+redhatRepoFile+"\\s*\\n\\+-+\\+";
		//bannerRegex += "|The system is not entitled to use any repositories.";	// changed by bug 846834
		bannerRegex += "|This system has no repositories available through subscriptions.";
		if (list!=null && list) {	// when explicitly asked to list
			Assert.assertTrue(Pattern.compile(".*"+bannerRegex+".*",Pattern.DOTALL).matcher(sshCommandResult.getStdout()).find(),"Stdout from repos (with option --list) contains the expected banner regex: "+bannerRegex);
		}
//		else {
//			Assert.assertTrue(!Pattern.compile(".*"+bannerRegex+".*",Pattern.DOTALL).matcher(sshCommandResult.getStdout()).find(),"Stdout from repos (without option --list) does not contain the banner regex: "+bannerRegex);	
//		}
		
		// assert the enable feedback
		if (enableRepos!=null) for (String enableRepo : enableRepos) {
			String expectedStdout = "Repo "+enableRepo+" is enabled for this system.";
			Assert.assertTrue(sshCommandResult.getStdout().contains(expectedStdout), "Stdout from repos --enable includes expected feedback '"+expectedStdout+"'.");
		}
		
		// assert the disable feedback
		if (disableRepos!=null) for (String disableRepo : disableRepos) {
			String expectedStdout = "Repo "+disableRepo+" is disabled for this system.";
			Assert.assertTrue(sshCommandResult.getStdout().contains(expectedStdout), "Stdout from repos --disable includes expected feedback '"+expectedStdout+"'.");
		}
		
		return sshCommandResult;
	}
	public SSHCommandResult repos(Boolean list, String enableRepo, String disableRepo, String proxy,String proxyuser,String proxypassword) {
		List<String> enableRepos = enableRepo==null?null:Arrays.asList(new String[]{enableRepo});
		List<String> disableRepos = disableRepo==null?null:Arrays.asList(new String[]{disableRepo});
		return repos(list, enableRepos, disableRepos, proxy, proxyuser, proxypassword);
	}
	
	
	/**
	 * @return SSHCommandResult from "subscription-manager repos --list"
	 */
	public SSHCommandResult listSubscribedRepos() {

		Calendar now = new GregorianCalendar();
		now.setTimeInMillis(System.currentTimeMillis());
		
		SSHCommandResult sshCommandResult = repos(true, (String)null, (String)null, null,null,null);
		//Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "The exit code from the repos --list command indicates a success.");
		
		//List<File> entitlementCertFiles = getCurrentEntitlementCertFiles();
		List<ProductCert> productCerts = getCurrentProductCerts();
		List<EntitlementCert> entitlementCerts = getCurrentEntitlementCerts();
		int numContentNamespaces = 0;
		for (EntitlementCert entitlementCert : entitlementCerts) {
			
			// we should NOT count contentNamespaces from entitlement certs that are not valid now
			if (entitlementCert.validityNotBefore.after(now) || entitlementCert.validityNotAfter.before(now)) continue;

			for (ContentNamespace contentNamespace : entitlementCert.contentNamespaces) {
				// we should NOT count contentNamespaces from for which all required tags are not provided by the installed product certs
				if (!areAllRequiredTagsInContentNamespaceProvidedByProductCerts(contentNamespace, productCerts)) {
					log.warning("None of the currently installed product certs provide the required tags '"+contentNamespace.requiredTags+"' for entitled content namespace: "+contentNamespace.name);
					continue;
				}

				numContentNamespaces++;
			}
			
		}

		if (numContentNamespaces==0) {
			//Assert.assertTrue(sshCommandResult.getStdout().trim().equals("The system is not entitled to use any repositories."), "The system is not entitled to use any repositories.");	// changed by bug 846834
			Assert.assertTrue(sshCommandResult.getStdout().trim().equals("This system has no repositories available through subscriptions."), "This system has no repositories available through subscriptions.");
		} else {
			String title;
			title = "Entitled Repositories in "+redhatRepoFile;	// changed by bug 846834
			title = "Available Repositories in "+redhatRepoFile;
			Assert.assertTrue(sshCommandResult.getStdout().contains(title),"The list of repositories is entitled '"+title+"'.");
		}

		return sshCommandResult;
	}
	
	
	
	// subscribe module tasks ************************************************************

	/**
	 * subscribe WITHOUT asserting results
	 * @param servicelevel TODO
	 */
	public SSHCommandResult subscribe_(Boolean auto, String servicelevel, List<String> poolIds, List<String> productIds, List<String> regtokens, String quantity, String email, String locale, String proxy, String proxyuser, String proxypassword) {
		
		// assemble the command
		String command = this.command;									command += " subscribe";
		if (auto!=null && auto)											command += " --auto";
		if (servicelevel!=null)											command += " --servicelevel="+String.format(servicelevel.contains(" ")||servicelevel.isEmpty()?"\"%s\"":"%s", servicelevel);	// quote a value containing spaces or is empty
		if (poolIds!=null)		for (String poolId : poolIds)			command += " --pool="+poolId;
		if (productIds!=null)	for (String productId : productIds)		command += " --product="+productId;
		if (regtokens!=null)	for (String regtoken : regtokens)		command += " --regtoken="+regtoken;
		if (quantity!=null)												command += " --quantity="+quantity;
		if (email!=null)												command += " --email="+email;
		if (locale!=null)												command += " --locale="+locale;
		if (proxy!=null)												command += " --proxy="+proxy;
		if (proxyuser!=null)											command += " --proxyuser="+proxyuser;
		if (proxypassword!=null)										command += " --proxypassword="+proxypassword;
		
		// run command without asserting results
		return sshCommandRunner.runCommandAndWait(command);
	}

	/**
	 * subscribe WITHOUT asserting results.
	 * @param servicelevel TODO
	 */
	public SSHCommandResult subscribe_(Boolean auto, String servicelevel, String poolId, String productId, String regtoken, String quantity, String email, String locale, String proxy, String proxyuser, String proxypassword) {
		
		List<String> poolIds	= poolId==null?null:Arrays.asList(new String[]{poolId});
		List<String> productIds	= productId==null?null:Arrays.asList(new String[]{productId});
		List<String> regtokens	= regtoken==null?null:Arrays.asList(new String[]{regtoken});

		return subscribe_(auto, servicelevel, poolIds, productIds, regtokens, quantity, email, locale, proxy, proxyuser, proxypassword);
	}


	
	/**
	 * subscribe and assert all results are successful
	 * @param servicelevel TODO
	 */
	public SSHCommandResult subscribe(Boolean auto, String servicelevel, List<String> poolIds, List<String> productIds, List<String> regtokens, String quantity, String email, String locale, String proxy, String proxyuser, String proxypassword) {

		SSHCommandResult sshCommandResult = subscribe_(auto, servicelevel, poolIds, productIds, regtokens, quantity, email, locale, proxy, proxyuser, proxypassword);
		auto = auto==null? false:auto;	// the non-null default value for auto is false

		// assert results...
		String stdoutMessage;
		
		// just return the result for the following cases:
		if (sshCommandResult.getStdout().startsWith("This consumer is already subscribed") ||	// This consumer is already subscribed to the product matching pool with id 'ff8080812c71f5ce012c71f6996f0132'.
			sshCommandResult.getStdout().startsWith("No entitlements are available") ||			// No entitlements are available from the pool with id '8a90f8143611c33f013611c4797b0456'.   (Bug 719743)
			sshCommandResult.getStdout().startsWith("Pool is restricted") ||					// Pool is restricted to virtual guests: '8a90f85734205a010134205ae8d80403'.
			sshCommandResult.getStdout().startsWith("All installed products are covered") ||	// All installed products are covered by valid entitlements. No need to update subscriptions at this time.
			sshCommandResult.getStdout().startsWith("Unable to entitle consumer")) {			// Unable to entitle consumer to the pool with id '8a90f8b42e3e7f2e012e3e7fc653013e'.: rulefailed.virt.only
																								// Unable to entitle consumer to the pool with id '8a90f85734160df3013417ac68bb7108'.: Entitlements for awesomeos-virt-4 expired on: 12/7/11 3:43 AM
			log.warning(sshCommandResult.getStdout().trim());
			return sshCommandResult;	
		}
		
		// assert the subscribe does NOT report "The system is unable to complete the requested transaction"
		//Assert.assertContainsNoMatch(sshCommandResult.getStdout(), "The system is unable to complete the requested transaction","The system should always be able to complete the requested transaction.");
		stdoutMessage = "The system is unable to complete the requested transaction";
		Assert.assertFalse(sshCommandResult.getStdout().contains(stdoutMessage), "The subscribe stdout should NOT report: "+stdoutMessage);
	
		// assert the subscribe does NOT report "Entitlement Certificate\\(s\\) update failed due to the following reasons:"
		//Assert.assertContainsNoMatch(sshCommandResult.getStdout(), "Entitlement Certificate\\(s\\) update failed due to the following reasons:","Entitlement Certificate updates should be successful when subscribing.");
		stdoutMessage = "Entitlement Certificate(s) update failed due to the following reasons:";
		Assert.assertFalse(sshCommandResult.getStdout().contains(stdoutMessage), "The subscribe stdout should NOT report: "+stdoutMessage);

		// assert that the entitlement pool was found for subscribing
		//Assert.assertContainsNoMatch(sshCommandResult.getStdout(),"No such entitlement pool:", "The subscription pool was found.");
		//Assert.assertContainsNoMatch(sshCommandResult.getStdout(), "Subscription pool .* does not exist.","The subscription pool was found.");
		//stdoutMessage = "Subscription pool "+(poolId==null?"null":poolId)+" does not exist.";	// Subscription pool {0} does not exist.
		//Assert.assertFalse(sshCommandResult.getStdout().contains(stdoutMessage), "The subscribe stdout should NOT report: "+stdoutMessage);
		if (poolIds!=null) {
			for (String poolId : poolIds) {
				stdoutMessage = "Subscription pool "+poolId+" does not exist.";	// Subscription pool {0} does not exist.
				Assert.assertFalse(sshCommandResult.getStdout().contains(stdoutMessage), "The subscribe stdout should NOT report: "+stdoutMessage);
			}
		}
		
		// assert the stdout msg was a success
		if (servicelevel!=null && !servicelevel.equals(""))
			Assert.assertTrue(sshCommandResult.getStdout().contains("Service level set to: "+servicelevel), "The autosubscribe stdout reports: Service level set to: "+servicelevel);
		if (auto)
			Assert.assertTrue(sshCommandResult.getStdout().contains("Installed Product Current Status:"), "The autosubscribe stdout reports: Installed Product Current Status");
		else
			Assert.assertTrue(sshCommandResult.getStdout().startsWith("Success"), "The subscribe stdout reports: Success");

		// assert the exit code was not a failure
		if (auto)
			Assert.assertNotSame(sshCommandResult.getExitCode(), Integer.valueOf(255), "The exit code from the subscribe --auto command does not indicate a failure (exit code 0 indicates an entitlement was granted, 1 indicates an entitlement was not granted, 255 indicates a failure).");
		else
			Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "The exit code from the subscribe command indicates a success.");
			
		return sshCommandResult;
	}
	
	/**
	 * subscribe and assert all results are successful
	 * @param servicelevel TODO
	 */
	public SSHCommandResult subscribe(Boolean auto, String servicelevel, String poolId, String productId, String regtoken, String quantity, String email, String locale, String proxy, String proxyuser, String proxypassword) {

		List<String> poolIds	= poolId==null?null:Arrays.asList(new String[]{poolId});
		List<String> productIds	= productId==null?null:Arrays.asList(new String[]{productId});
		List<String> regtokens	= regtoken==null?null:Arrays.asList(new String[]{regtoken});

		return subscribe(auto, servicelevel, poolIds, productIds, regtokens, quantity, email, locale, proxy, proxyuser, proxypassword);
	}
	
	
//	public SSHCommandResult subscribe(List<String> poolIds, List<String> productIds, List<String> regtokens, String quantity, String email, String locale, String proxy, String proxyuser, String proxypassword) {
//
//		SSHCommandResult sshCommandResult = subscribe_(null, poolIds, productIds, regtokens, quantity, email, locale, proxy, proxyuser, proxypassword);
//		
//		// assert results
//		Assert.assertContainsNoMatch(sshCommandResult.getStdout(), "Entitlement Certificate\\(s\\) update failed due to the following reasons:","Entitlement Certificate updates should be successful when subscribing.");
//		if (sshCommandResult.getStderr().startsWith("This consumer is already subscribed")) return sshCommandResult;
//		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "The exit code from the subscribe command indicates a success.");
//		return sshCommandResult;
//	}
	
	
	public File subscribeToProductId(String productId) {
		//RemoteFileTasks.runCommandExpectingNonzeroExit(sshCommandRunner,"subscription-manager-cli subscribe --product="+product);
		
		SubscriptionPool pool = SubscriptionPool.findFirstInstanceWithMatchingFieldFromList("productId", productId, getCurrentlyAvailableSubscriptionPools());
		Assert.assertNotNull(pool,"Found an available pool to subscribe to productId '"+productId+"': "+pool);
		return subscribeToSubscriptionPool(pool);
	}
	
	/**
	 * subscribe to the given SubscriptionPool 
	 * @param pool
	 * @return the newly installed EntitlementCert file to the newly consumed ProductSubscriptions 
	 */
	public File subscribeToSubscriptionPool(SubscriptionPool pool)  {
		
//		String hostname = getConfFileParameter(rhsmConfFile, "hostname");
//		String port = getConfFileParameter(rhsmConfFile, "port");
//		String prefix = getConfFileParameter(rhsmConfFile, "prefix");
		
		List<ProductSubscription> beforeProductSubscriptions = getCurrentlyConsumedProductSubscriptions();
		List<File> beforeEntitlementCertFiles = getCurrentEntitlementCertFiles();
		log.info("Subscribing to subscription pool: "+pool);
		SSHCommandResult sshCommandResult = subscribe(null, null, pool.poolId, null, null, null, null, null, null, null, null);

		// is this pool multi-entitleable?
		/* This information is now in the SubscriptionPool itself
		boolean isPoolMultiEntitlement = false;
		try {
			isPoolMultiEntitlement = CandlepinTasks.isPoolProductMultiEntitlement(this.currentlyRegisteredUsername,this.currentlyRegisteredPassword,SubscriptionManagerBaseTestScript.sm_serverUrl,pool.poolId);
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
		*/

		// assert that the remaining SubscriptionPools does NOT contain the pool just subscribed too (unless it is multi-entitleable)
		List<SubscriptionPool> afterSubscriptionPools = getCurrentlyAvailableSubscriptionPools();
		if (!pool.quantity.equalsIgnoreCase("unlimited") && Integer.valueOf(pool.quantity)<=1) {
			Assert.assertTrue(!afterSubscriptionPools.contains(pool),
					"When the final quantity from the pool was consumed, the remaining available subscription pools no longer contains the just subscribed to pool: "+pool);
		} else if (!pool.multiEntitlement) {
			Assert.assertTrue(!afterSubscriptionPools.contains(pool),
					"When the pool is not multi-entitleable, the remaining available subscription pools no longer contains the just subscribed to pool: "+pool);
		} else {
			Assert.assertTrue(afterSubscriptionPools.contains(pool),
					"When the pool is multi-entitleable, the remaining available subscription pools still contains the just subscribed to pool: "+pool);
		}
		
		// assert that the remaining SubscriptionPools do NOT contain the same productId just subscribed to
		//log.warning("We will no longer assert that the remaining available pools do not contain the same productId ("+pool.productId+") as the pool that was just subscribed.  Reference: https://bugzilla.redhat.com/show_bug.cgi?id=663455");
		/*
		for (SubscriptionPool afterSubscriptionPool : afterSubscriptionPools) {
			Assert.assertTrue(!afterSubscriptionPool.productId.equals(pool.productId),
					"This remaining available pool "+afterSubscriptionPool+" does NOT contain the same productId ("+pool.productId+") after subscribing to pool: "+pool);
		}
		*/

		// is this a personal subpool?
		String poolProductId = pool.productId;
		boolean isSubpool = false; 
		try {
			JSONArray personSubscriptionPoolProductData;
//			personSubscriptionPoolProductData = new JSONArray(System.getProperty("sm.person.subscriptionPoolProductData", "<>").replaceAll("<", "[").replaceAll(">", "]")); // hudson parameters use <> instead of []
			personSubscriptionPoolProductData = new JSONArray(SubscriptionManagerBaseTestScript.getProperty("sm.person.subscriptionPoolProductData", "[]").replaceFirst("^\"", "").replaceFirst("\"$", "").replaceAll("<", "[").replaceAll(">", "]")); // hudson JSONArray parameters get surrounded with double quotes that need to be stripped
			for (int j=0; j<personSubscriptionPoolProductData.length(); j++) {
				JSONObject poolProductDataAsJSONObject = (JSONObject) personSubscriptionPoolProductData.get(j);
				String personProductId = poolProductDataAsJSONObject.getString("personProductId");
				JSONObject subpoolProductDataAsJSONObject = poolProductDataAsJSONObject.getJSONObject("subPoolProductData");
				String systemProductId = subpoolProductDataAsJSONObject.getString("systemProductId");
				if (poolProductId.equals(systemProductId)) { // special case when pool's productId is really a personal subpool
					poolProductId = personProductId;
					isSubpool = true;
					break;
				}
			}
		} catch (JSONException e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		} 
		
		// figure out which entitlement cert file has been newly installed into /etc/pki/entitlement after attempting to subscribe to pool
		/* OLD - THIS ALGORITHM BREAKS DOWN WHEN MODIFIER ENTITLEMENTS ARE IN PLAY
		File newCertFile = null;
		List<File> afterEntitlementCertFiles = getCurrentEntitlementCertFiles();
		for (File file : afterEntitlementCertFiles) {
			if (!beforeEntitlementCertFiles.contains(file)) {
				newCertFile = file; break;
			}
		}
		*/
		/* VALID BUT INEFFICIENT
		List<File> afterEntitlementCertFiles = getCurrentEntitlementCertFiles();
		File newCertFile = null;
		Map<BigInteger, SubscriptionPool> map = new HashMap<BigInteger, SubscriptionPool>();
		try {
			map = getCurrentSerialMapToSubscriptionPools(this.currentAuthenticator,this.currentAuthenticatorPassword);
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
		for (BigInteger serial: map.keySet()) {
			if (map.get(serial).poolId.equals(pool.poolId)) {
				newCertFile = new File(this.entitlementCertDir+"/"+serial+".pem");
				break;
			}
		}
		*/
		// NOTE: this block of code is somewhat duplicated in getEntitlementCertCorrespondingToSubscribedPool(...)
		File newCertFile = null;
		List<File> afterEntitlementCertFiles = getCurrentEntitlementCertFiles("-t");
		for (File entitlementCertFile : afterEntitlementCertFiles) {
			if (!beforeEntitlementCertFiles.contains(entitlementCertFile)) {
				EntitlementCert entitlementCert = getEntitlementCertFromEntitlementCertFile(entitlementCertFile);
				try {
					JSONObject jsonEntitlement = CandlepinTasks.getEntitlementUsingRESTfulAPI(this.currentlyRegisteredUsername,this.currentlyRegisteredPassword,SubscriptionManagerBaseTestScript.sm_serverUrl,entitlementCert.id);
					JSONObject jsonPool = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(this.currentlyRegisteredUsername,this.currentlyRegisteredPassword,SubscriptionManagerBaseTestScript.sm_serverUrl,jsonEntitlement.getJSONObject("pool").getString("href")));
					if (jsonPool.getString("id").equals(pool.poolId)) {
						newCertFile = entitlementCertFile; break;
					}
				} catch (Exception e) {
					e.printStackTrace();
					Assert.fail(e.getMessage());
				}
			}
		}
		
		// when the pool is already subscribed to...
		if (sshCommandResult.getStdout().startsWith("This consumer is already subscribed")) {
			
			// assert that NO new entitlement cert file has been installed in /etc/pki/entitlement
			/*Assert.assertNull(newCertFile,
					"A new entitlement certificate has NOT been installed after attempting to subscribe to an already subscribed to pool: "+pool);
			*/
			Assert.assertEquals(beforeEntitlementCertFiles.size(), afterEntitlementCertFiles.size(),
					"The existing entitlement certificate count remains unchanged after attempting to subscribe to an already subscribed to pool: "+pool);

			// find the existing entitlement cert file corresponding to the already subscribed pool
			/* ALREADY FOUND USING ALGORITHM ABOVE 
			EntitlementCert entitlementCert = null;
			for (File thisEntitlementCertFile : getCurrentEntitlementCertFiles()) {
				EntitlementCert thisEntitlementCert = getEntitlementCertFromEntitlementCertFile(thisEntitlementCertFile);
				if (thisEntitlementCert.orderNamespace.productId.equals(poolProductId)) {
					entitlementCert = thisEntitlementCert;
					break;
				}
			}
			Assert.assertNotNull(entitlementCert, isSubpool?
					"Found an already existing Entitlement Cert whose personal productId matches the system productId from the subscription pool: "+pool:
					"Found an already existing Entitlement Cert whose productId matches the productId from the subscription pool: "+pool);
			newCertFile = getEntitlementCertFileFromEntitlementCert(entitlementCert); // not really new, just already existing
			*/
			
			// assert that consumed ProductSubscriptions has NOT changed
			List<ProductSubscription> afterProductSubscriptions = getCurrentlyConsumedProductSubscriptions();
			Assert.assertTrue(afterProductSubscriptions.size() == beforeProductSubscriptions.size() && afterProductSubscriptions.size() > 0,
					"The list of currently consumed product subscriptions has not changed (from "+beforeProductSubscriptions.size()+" to "+afterProductSubscriptions.size()+") since the productId of the pool we are trying to subscribe to is already consumed.");

		// when no free entitlements exist...		// No entitlements are available from the pool with id '8a90f8143611c33f013611c4797b0456'.
		} else if (sshCommandResult.getStdout().startsWith("No entitlements are available")) {
			
			// assert that the depleted pool Quantity is zero
			SubscriptionPool depletedPool = SubscriptionPool.findFirstInstanceWithMatchingFieldFromList("poolId", pool.poolId, getCurrentlyAllAvailableSubscriptionPools());
			/* behavior changed on list --all --available  (3/4/2011)
			Assert.assertNotNull(depletedPool,
					"Found the depleted pool amongst --all --available after having consumed all of its available entitlements: ");
			*/
			Assert.assertNull(depletedPool,
					"Should no longer find the depleted pool amongst --all --available after having consumed all of its available entitlements: ");
//			Assert.assertEquals(depletedPool.quantity, "0",
//					"Asserting the pool's quantity after having consumed all of its available entitlements is zero.");
			JSONObject jsonPool = null;
			int consumed = 0;
			int quantity = Integer.valueOf(pool.quantity);
			try {
				jsonPool = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(this.currentlyRegisteredUsername,this.currentlyRegisteredPassword,SubscriptionManagerBaseTestScript.sm_serverUrl,"/pools/"+pool.poolId));
				consumed = jsonPool.getInt("consumed");
				quantity = jsonPool.getInt("quantity");
			} catch (Exception e) {
				e.printStackTrace();
				Assert.fail(e.getMessage());
			} 
			Assert.assertEquals(consumed, quantity,
					"Asserting the pool's consumed attribute equals it's total quantity after having consumed all of its available entitlements.");

			//  assert that NO new entitlement cert file has been installed in /etc/pki/entitlement
			Assert.assertNull(newCertFile,
					"A new entitlement certificate has NOT been installed after attempting to subscribe to depleted pool: "+depletedPool);
			Assert.assertEquals(beforeEntitlementCertFiles.size(), afterEntitlementCertFiles.size(),
					"The existing entitlement certificate count remains unchanged after attempting to subscribe to depleted pool: "+depletedPool);

			
		// otherwise, the pool is NOT already subscribe to...
		} else {
	
			// assert that only ONE new entitlement cert file has been installed in /etc/pki/entitlement
			// https://bugzilla.redhat.com/show_bug.cgi?id=640338
			Assert.assertTrue(afterEntitlementCertFiles.size()==beforeEntitlementCertFiles.size()+1,
					"Only ONE new entitlement certificate has been installed (count was '"+beforeEntitlementCertFiles.size()+"'; is now '"+afterEntitlementCertFiles.size()+"') after subscribing to pool: "+pool);

			// assert that the other cert files remain unchanged
			/* CANNOT MAKE THIS ASSERT/ASSUMPTION ANYMORE BECAUSE ADDITION OF AN ENTITLEMENT CAN AFFECT A MODIFIER PRODUCT THAT PROVIDES EXTRA CONTENT FOR THIS PRODUCT (A MODIFIER PRODUCT IS ALSO CALLED EUS) 2/21/2011 jsefler
			if (!afterEntitlementCertFiles.remove(newCertFile)) Assert.fail("Failed to remove certFile '"+newCertFile+"' from list.  This could be an automation logic error.");
			Assert.assertEquals(afterEntitlementCertFiles,beforeEntitlementCertFiles,"After subscribing to pool id '"+pool+"', the other entitlement cert serials remain unchanged");
			*/
			
			// assert the new entitlement cert file has been installed in /etc/pki/entitlement
			Assert.assertNotNull(newCertFile, "A new entitlement certificate has been installed after subscribing to pool: "+pool);
			log.info("The new entitlement certificate file is: "+newCertFile);
			
			// assert that the productId from the pool matches the entitlement productId
			// TEMPORARY WORKAROUND FOR BUG: https://bugzilla.redhat.com/show_bug.cgi?id=650278 - jsefler 11/05/2010
			// TEMPORARY WORKAROUND FOR BUG: https://bugzilla.redhat.com/show_bug.cgi?id=806986 - jsefler 06/28/2012
			boolean invokeWorkaroundWhileBugIsOpen = true;
			String bugId1="650278"; 
			String bugId2="806986"; 
			try {if (invokeWorkaroundWhileBugIsOpen&&(BzChecker.getInstance().isBugOpen(bugId1)||BzChecker.getInstance().isBugOpen(bugId2))) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId1).toString()+" Bugzilla "+bugId1+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId1+")"); log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId2).toString()+" Bugzilla "+bugId2+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId2+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
			if (invokeWorkaroundWhileBugIsOpen) {
				log.warning("Skipping assert that the productId from the pool matches the entitlement productId");
			} else {
			// END OF WORKAROUND
			EntitlementCert entitlementCert = getEntitlementCertFromEntitlementCertFile(newCertFile);
			File newCertKeyFile = getEntitlementCertKeyFileFromEntitlementCert(entitlementCert);
			Assert.assertEquals(entitlementCert.orderNamespace.productId, poolProductId, isSubpool?
					"New EntitlementCert productId '"+entitlementCert.orderNamespace.productId+"' matches originating Personal SubscriptionPool productId '"+poolProductId+"' after subscribing to the subpool.":
					"New EntitlementCert productId '"+entitlementCert.orderNamespace.productId+"' matches originating SubscriptionPool productId '"+poolProductId+"' after subscribing to the pool.");
			Assert.assertTrue(RemoteFileTasks.testExists(sshCommandRunner, newCertFile.getPath()),"New EntitlementCert file exists after subscribing to SubscriptionPool '"+pool.poolId+"'.");
			Assert.assertTrue(RemoteFileTasks.testExists(sshCommandRunner, newCertKeyFile.getPath()),"New EntitlementCert key file exists after subscribing to SubscriptionPool '"+pool.poolId+"'.");
			}

		
			// assert that consumed ProductSubscriptions has NOT decreased
			List<ProductSubscription> afterProductSubscriptions = getCurrentlyConsumedProductSubscriptions();
			//this assertion was valid prior to bug Bug 801187 - collapse list of provided products for subscription-manager list --consumed
			//Assert.assertTrue(afterProductSubscriptions.size() >= beforeProductSubscriptions.size() && afterProductSubscriptions.size() > 0,
			//		"The list of currently consumed product subscriptions has increased (from "+beforeProductSubscriptions.size()+" to "+afterProductSubscriptions.size()+"), or has remained the same after subscribing (using poolID="+pool.poolId+") to pool: "+pool+"  Note: The list of consumed product subscriptions can remain the same when all the products from this subscription pool are a subset of those from a previously subscribed pool.");
			Assert.assertTrue(afterProductSubscriptions.size() == beforeProductSubscriptions.size()+1,
					"The list of currently consumed product subscriptions has increased by 1 (from "+beforeProductSubscriptions.size()+" to "+afterProductSubscriptions.size()+"), after subscribing to pool: "+pool);
		}
		
		return newCertFile;
	}
	
	
	
	/**
	 * subscribe to the given SubscriptionPool without asserting results
	 * @param pool
	 * @return the newly installed EntitlementCert file to the newly consumed ProductSubscriptions (null if there was a problem)
	 * @throws Exception 
	 * @throws JSONException 
	 */
	public File subscribeToSubscriptionPool_(SubscriptionPool pool) throws JSONException, Exception  {
		
//		String hostname = getConfFileParameter(rhsmConfFile, "hostname");
//		String port = getConfFileParameter(rhsmConfFile, "port");
//		String prefix = getConfFileParameter(rhsmConfFile, "prefix");
		
		log.info("Subscribing to subscription pool: "+pool);
		SSHCommandResult sshCommandResult = subscribe(null, null, pool.poolId, null, null, null, null, null, null, null, null);

		// get the serial of the entitlement that was granted from this pool
		//BigInteger serialNumber = CandlepinTasks.getOwnersNewestEntitlementSerialCorrespondingToSubscribedPoolId(this.currentlyRegisteredUsername,this.currentlyRegisteredPassword,SubscriptionManagerBaseTestScript.sm_serverUrl,getCurrentlyRegisteredOwnerKey(),pool.poolId);
		BigInteger serialNumber = CandlepinTasks.getConsumersNewestEntitlementSerialCorrespondingToSubscribedPoolId(this.currentlyRegisteredUsername,this.currentlyRegisteredPassword,SubscriptionManagerBaseTestScript.sm_serverUrl,getCurrentConsumerId(),pool.poolId);
		//Assert.assertNotNull(serialNumber, "Found the serial number of the entitlement that was granted after subscribing to pool id '"+pool.poolId+"'.");
		if (serialNumber==null) return null;
		File serialPemFile = new File(entitlementCertDir+File.separator+serialNumber+".pem");
		//Assert.assertEquals(RemoteFileTasks.testFileExists(sshCommandRunner, serialPemFile.getPath()),1, "Found the EntitlementCert file ("+serialPemFile+") that was granted after subscribing to pool id '"+pool.poolId+"'.");

		return serialPemFile;
	}
	
	//@Deprecated
	public File subscribeToSubscriptionPoolUsingProductId(SubscriptionPool pool) {
		log.warning("Subscribing to a Subscription Pool using --product Id has been removed in subscription-manager-0.71-1.el6.i686.  Forwarding this subscribe request to use --pool Id...");
		return subscribeToSubscriptionPoolUsingPoolId(pool);
		
		/* jsefler 7/22/2010
		List<ProductSubscription> before = getCurrentlyConsumedProductSubscriptions();
		log.info("Subscribing to subscription pool: "+pool);
		subscribe(null, pool.productId, null, null, null);
		String stderr = sshCommandRunner.getStderr().trim();
		
		List<ProductSubscription> after = getCurrentlyConsumedProductSubscriptions();
		if (stderr.equals("This consumer is already subscribed to the product '"+pool.productId+"'.")) {
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
	
	public File subscribeToSubscriptionPoolUsingPoolId(SubscriptionPool pool/*, boolean withPoolID*/) {
		return subscribeToSubscriptionPool(pool);
		
		/* jsefler 11/22/2010
		if(withPoolID){
			log.info("Subscribing to pool with poolId: "+ pool.poolId);
			sshCommandRunner.runCommandAndWait("subscription-manager-cli subscribe --pool="+pool.poolId);
		}
		else{
			log.info("Subscribing to pool with productId: "+ pool.productId);
			sshCommandRunner.runCommandAndWait("subscription-manager-cli subscribe --product=\""+pool.productId+"\"");
		}
		Assert.assertTrue(getCurrentlyConsumedProductSubscriptions().size() > 0,
				"Successfully subscribed to pool with pool ID: "+ pool.poolId +" and pool name: "+ pool.subscriptionName);
		//TODO: add in more thorough product subscription verification
		// first improvement is to assert that the count of consumedProductIDs is at least one greater than the count of consumedProductIDs before the new pool was subscribed to.
		*/
	}
	
	public void subscribeToRegToken(String regtoken) {
		log.info("Subscribing to registration token: "+ regtoken);
		RemoteFileTasks.runCommandExpectingNoTracebacks(sshCommandRunner, "subscription-manager-cli subscribe --regtoken="+regtoken);
		Assert.assertTrue((getCurrentlyConsumedProductSubscriptions().size() > 0),
				"At least one entitlement consumed by regtoken subscription");
	}
	
	/**
	 * Individually subscribe to each of the currently available subscription pools one at a time 
	 * @return SubscriptionPools that were available for subscribing 
	 */
	public List <SubscriptionPool> subscribeToTheCurrentlyAvailableSubscriptionPoolsIndividually() {

		// individually subscribe to each available subscription pool
		List <SubscriptionPool> pools = getCurrentlyAvailableSubscriptionPools();
		for (SubscriptionPool pool : pools) {
			subscribeToSubscriptionPool(pool);
		}
		
		// assert
		assertNoAvailableSubscriptionPoolsToList(true, "Asserting that no available subscription pools remain after individually subscribing to them all.");
		return pools;
	}
	
	
	/**
	 * Collectively subscribe to the currently available subscription pools in one command call
	 * 
	 * @return SubscriptionPools that were available for subscribing 
	 * @throws Exception 
	 * @throws JSONException 
	 */
	public List<SubscriptionPool> subscribeToTheCurrentlyAvailableSubscriptionPoolsCollectively() throws JSONException, Exception {
		
		// assemble a list of all the available SubscriptionPool ids
		List <String> poolIds = new ArrayList<String>();
		List <SubscriptionPool> poolsBeforeSubscribe = getCurrentlyAvailableSubscriptionPools();
		for (SubscriptionPool pool : poolsBeforeSubscribe) {
			poolIds.add(pool.poolId);
		}
		if (!poolIds.isEmpty()) subscribe(null,null, poolIds, null, null, null, null, null, null, null, null);
		
		// assert results when assumingRegisterType="system"
		if (currentlyRegisteredType==null || currentlyRegisteredType.equals(ConsumerType.system)) {
			assertNoAvailableSubscriptionPoolsToList(true, "Asserting that no available subscription pools remain after collectively subscribing to them all.");
			return poolsBeforeSubscribe;
		}
		
		// assert results when assumingRegisterType="candlepin"
		else if (currentlyRegisteredType.equals(ConsumerType.candlepin)) {
			List <SubscriptionPool> poolsAfterSubscribe = getCurrentlyAvailableSubscriptionPools();
			for (SubscriptionPool beforePool : poolsBeforeSubscribe) {
				boolean foundPool = false;
				for (SubscriptionPool afterPool : poolsAfterSubscribe) {
					if (afterPool.equals(beforePool)) {
						foundPool = true;
						
						// determine how much the quantity should have decremented
						int expectedDecrement = 1;
						String virt_only = CandlepinTasks.getPoolAttributeValue(currentlyRegisteredUsername, currentlyRegisteredPassword, SubscriptionManagerBaseTestScript.sm_serverUrl, afterPool.poolId, "virt_only");
						String virt_limit = CandlepinTasks.getPoolProductAttributeValue(currentlyRegisteredUsername, currentlyRegisteredPassword, SubscriptionManagerBaseTestScript.sm_serverUrl, afterPool.poolId, "virt_limit");
						if (virt_only!=null && Boolean.valueOf(virt_only) && virt_limit!=null) expectedDecrement += Integer.valueOf(virt_limit);	// the quantity consumed on a virt pool should be 1 (from the subscribe on the virtual pool itself) plus virt_limit (from the subscribe by the candlepin consumer on the physical pool)

						// assert the quantity has decremented;
						Assert.assertEquals(Integer.valueOf(afterPool.quantity).intValue(), Integer.valueOf(beforePool.quantity).intValue()-expectedDecrement,
								"The quantity of entitlements from subscription pool id '"+afterPool.poolId+"' has decremented by "+expectedDecrement+".");
						break;
					}
				}
				if (!foundPool) {
					Assert.fail("Could not find subscription pool "+beforePool+" listed after subscribing to it as a registered "+currentlyRegisteredType+" consumer.");
				}
			}
			return poolsBeforeSubscribe;
		}
		
		Assert.fail("Do not know how to assert subscribeToTheCurrentlyAvailableSubscriptionPoolsCollectively when registered as type="+currentlyRegisteredType);
		return poolsBeforeSubscribe;
	}
	/**
	 * @return list of all the currently available subscription pools that about to be subscribed
	 */
	public List<SubscriptionPool> subscribeToTheCurrentlyAllAvailableSubscriptionPoolsCollectively() {

		// assemble a list of all the available SubscriptionPool ids
		List<String> poolIds = new ArrayList<String>();
		List<SubscriptionPool> subscriptionPools = getCurrentlyAllAvailableSubscriptionPools();
		for (SubscriptionPool pool : subscriptionPools) poolIds.add(pool.poolId);

		if (!poolIds.isEmpty()) subscribe(null,null,poolIds, null, null, null, null, null,null,null,null);
		
		// assert
		assertNoAvailableSubscriptionPoolsToList(true,"Asserting that no available subscription pools remain after simultaneously subscribing to them all available.");
		
		return subscriptionPools;
	}
	
	public void assertNoAvailableSubscriptionPoolsToList(boolean ignoreMuliEntitlementSubscriptionPools, String assertMsg) {
		boolean invokeWorkaroundWhileBugIsOpen = true;
		
		// TEMPORARY WORKAROUND FOR BUG
		invokeWorkaroundWhileBugIsOpen = false; // true;	// Status: CLOSED ERRATA	// Bug 613635 - “connection.UEPConnection instance “ displays while availability check
		try {String bugId="613635"; if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			Assert.assertContainsMatch(listAvailableSubscriptionPools().getStdout(),"^No available subscription pools to list$",assertMsg);
			return;
		}
		// END OF WORKAROUND
		
		// TEMPORARY WORKAROUND FOR BUG
		invokeWorkaroundWhileBugIsOpen = false; // true;	// Status: CLOSED ERRATA	// Bug 622839 - extraneous user hash code appears in stdout after executing list --available
		try {String bugId="622839"; if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			Assert.assertContainsMatch(listAvailableSubscriptionPools().getStdout(),"^No available subscription pools to list$",assertMsg);
			return;
		}
		// END OF WORKAROUND
		
		// TEMPORARY WORKAROUND FOR BUG
		invokeWorkaroundWhileBugIsOpen = false; // true;	// Status: CLOSED DUPLICATE of bug 623481	// Bug 623657 - extraneous self.conn output appears in stdout after executing list --available
		try {String bugId="623657"; if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			Assert.assertContainsMatch(listAvailableSubscriptionPools().getStdout(),"^No available subscription pools to list$",assertMsg);
			return;
		}
		// END OF WORKAROUND
		
		
		// determine which available pools are multi-entitlement pools
		List<SubscriptionPool> poolsAvailableExcludingMuliEntitlement = new ArrayList<SubscriptionPool>();
		List<SubscriptionPool> poolsAvailable = getCurrentlyAvailableSubscriptionPools();
		for (SubscriptionPool pool : poolsAvailable) {
			try {
//				if (!CandlepinTasks.isPoolProductMultiEntitlement(getConfFileParameter(rhsmConfFile, "hostname"),getConfFileParameter(rhsmConfFile, "port"),getConfFileParameter(rhsmConfFile, "prefix"),this.currentlyRegisteredUsername,this.currentlyRegisteredPassword,pool.poolId)) {
				if (!CandlepinTasks.isPoolProductMultiEntitlement(this.currentlyRegisteredUsername,this.currentlyRegisteredPassword,SubscriptionManagerBaseTestScript.sm_serverUrl,pool.poolId)) {
					poolsAvailableExcludingMuliEntitlement.add(pool);
				}
			} catch (Exception e) {
				e.printStackTrace();
				Assert.fail(e.getMessage());
			}
		}
		
		// assert
		if (ignoreMuliEntitlementSubscriptionPools) {
			Assert.assertEquals(poolsAvailableExcludingMuliEntitlement.size(),0,
					assertMsg+" (muti-entitlement pools were excluded.)");
		} else {
			Assert.assertEquals(poolsAvailable.size(),0,
					assertMsg+" (muti-entitlement pools were excluded.)");
			Assert.assertEquals(listAvailableSubscriptionPools().getStdout().trim(),
				"No available subscription pools to list",assertMsg);
		}
	}
	
	
	
	// unsubscribe module tasks ************************************************************

	/**
	 * unsubscribe without asserting results
	 */
	public SSHCommandResult unsubscribe_(Boolean all, List<BigInteger> serials, String proxy, String proxyuser, String proxypassword) {

		// assemble the command
		String command = this.command;							command += " unsubscribe";
		if (all!=null && all)									command += " --all";
		if (serials!=null)	for (BigInteger serial : serials)	command += " --serial="+serial;
		if (proxy!=null)										command += " --proxy="+proxy;
		if (proxyuser!=null)									command += " --proxyuser="+proxyuser;
		if (proxypassword!=null)								command += " --proxypassword="+proxypassword;
		
		if (all!=null && all && serials==null) workaroundForBug844455();
		
		// run command without asserting results
		return sshCommandRunner.runCommandAndWait(command);
	}
	/**
	 * unsubscribe without asserting results
	 */
	public SSHCommandResult unsubscribe_(Boolean all, BigInteger serial, String proxy, String proxyuser, String proxypassword) {
		
		List<BigInteger> serials = serial==null?null:Arrays.asList(new BigInteger[]{serial});

		return unsubscribe_(all, serials, proxy, proxyuser, proxypassword);
	}
	
	/**
	 * unsubscribe and assert all results are successful
	 */
	public SSHCommandResult unsubscribe(Boolean all, List<BigInteger> serials, String proxy, String proxyuser, String proxypassword) {

		SSHCommandResult sshCommandResult = unsubscribe_(all, serials, proxy, proxyuser, proxypassword);
		
		// assert results
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "The exit code from the unsubscribe command indicates a success.");
		Assert.assertEquals(sshCommandResult.getStderr(), "", "Stderr from the unsubscribe.");
		return sshCommandResult;
	}
	
	/**
	 * unsubscribe and assert all results are successful
	 */
	public SSHCommandResult unsubscribe(Boolean all, BigInteger serial, String proxy, String proxyuser, String proxypassword) {
		
		List<BigInteger> serials = serial==null?null:Arrays.asList(new BigInteger[]{serial});
		
		return unsubscribe(all, serials, proxy, proxyuser, proxypassword);
	}
	
	/**
	 * unsubscribe from entitlement certificate serial and assert results
	 * @param serialNumber
	 * @return - false when no unsubscribe took place
	 */
	public boolean unsubscribeFromSerialNumber(BigInteger serialNumber) {
		String certFilePath = entitlementCertDir+"/"+serialNumber+".pem";
		String certKeyFilePath = entitlementCertDir+"/"+serialNumber+"-key.pem";
		File certFile = new File(certFilePath);
		boolean certFileExists = RemoteFileTasks.testExists(sshCommandRunner,certFilePath);
		if (certFileExists) Assert.assertTrue(RemoteFileTasks.testExists(sshCommandRunner,certKeyFilePath),
				"Entitlement Certificate file with serial '"+serialNumber+"' ("+certFilePath+") and corresponding key file ("+certKeyFilePath+") exist before unsubscribing.");
		List<File> beforeEntitlementCertFiles = getCurrentEntitlementCertFiles();

		log.info("Attempting to unsubscribe from certificate serial: "+ serialNumber);
		SSHCommandResult result = unsubscribe_(false, serialNumber, null, null, null);
		
		// assert the results
		String expectedStdoutMsg;
		if (!certFileExists) {
			String regexForSerialNumber = serialNumber.toString();
			
			// TEMPORARY WORKAROUND FOR BUG: https://bugzilla.redhat.com/show_bug.cgi?id=639320 - jsefler 10/1/2010
			boolean invokeWorkaroundWhileBugIsOpen = true;
			String bugId="639320"; 
			try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
			if (invokeWorkaroundWhileBugIsOpen) {
				regexForSerialNumber = "[\\d,]*";
			}
			// END OF WORKAROUND
						
			//Assert.assertContainsMatch(result.getStderr(), "Entitlement Certificate with serial number "+regexForSerialNumber+" could not be found.",
			//		"Stderr from an attempt to unsubscribe from Entitlement Certificate serial "+serialNumber+" that was not found in "+entitlementCertDir);
			//Assert.assertContainsMatch(result.getStdout(), "Entitlement Certificate with serial number "+regexForSerialNumber+" could not be found.",
			//		"Stdout from an attempt to unsubscribe from Entitlement Certificate serial "+serialNumber+" that was not found in "+entitlementCertDir);
			//Assert.assertEquals(result.getExitCode(), Integer.valueOf(255), "The unsubscribe should fail when its corresponding entitlement cert file ("+certFilePath+") does not exist.");
			expectedStdoutMsg = "Unsuccessfully unsubscribed serial numbers:";	// added by bug 867766
			expectedStdoutMsg = "Unsuccessfully removed serial numbers:";	// changed by bug 874749
			Assert.assertTrue(result.getStdout().contains(expectedStdoutMsg), "Stdout from unsubscribe contains expected message: "+expectedStdoutMsg);
			expectedStdoutMsg = "   Entitlement Certificate with serial number "+serialNumber+" could not be found.";
			Assert.assertTrue(result.getStdout().contains(expectedStdoutMsg), "Stdout from unsubscribe contains expected message: "+expectedStdoutMsg);
			Assert.assertEquals(result.getStderr(),"", "Stderr from unsubscribe.");
			Assert.assertEquals(result.getExitCode(), Integer.valueOf(1), "ExitCode from unsubscribe when the serial's entitlement cert file ("+certFilePath+") does not exist.");	// changed by bug 873791
			return false;
		}
		
		// assert the entitlement certFilePath is removed
		Assert.assertTrue(!RemoteFileTasks.testExists(sshCommandRunner,certFilePath),
				"Entitlement Certificate with serial '"+serialNumber+"' ("+certFilePath+") has been removed.");

		// assert the entitlement certKeyFilePath is removed
		// TEMPORARY WORKAROUND FOR BUG: https://bugzilla.redhat.com/show_bug.cgi?id=708362 - jsefler 08/25/2011
		boolean invokeWorkaroundWhileBugIsOpen = true;
		String bugId="708362"; 
		try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		boolean assertCertKeyFilePathIsRemoved = true;
		if (invokeWorkaroundWhileBugIsOpen) log.warning("Skipping the assertion that the Entitlement Certificate key with serial '"+serialNumber+"' ("+certKeyFilePath+") has been removed while bug is open."); else
		// END OF WORKAROUND
		Assert.assertTrue(!RemoteFileTasks.testExists(sshCommandRunner,certKeyFilePath),
				"Entitlement Certificate key with serial '"+serialNumber+"' ("+certKeyFilePath+") has been removed.");

		// assert that only ONE entitlement cert file was removed
		List<File> afterEntitlementCertFiles = getCurrentEntitlementCertFiles();
		Assert.assertTrue(afterEntitlementCertFiles.size()==beforeEntitlementCertFiles.size()-1,
				"Only ONE entitlement certificate has been removed (count was '"+beforeEntitlementCertFiles.size()+"'; is now '"+afterEntitlementCertFiles.size()+"') after unsubscribing from serial: "+serialNumber);
		
		// assert that the other cert files remain unchanged
		/* CANNOT MAKE THIS ASSERT/ASSUMPTION ANYMORE BECAUSE REMOVAL OF AN ENTITLEMENT CAN AFFECT A MODIFIER PRODUCT THAT PROVIDES EXTRA CONTENT FOR THIS SERIAL (A MODIFIER PRODUCT IS ALSO CALLED EUS) 2/21/2011 jsefler
		if (!beforeEntitlementCertFiles.remove(certFile)) Assert.fail("Failed to remove certFile '"+certFile+"' from list.  This could be an automation logic error.");
		Assert.assertEquals(afterEntitlementCertFiles,beforeEntitlementCertFiles,"After unsubscribing from serial '"+serialNumber+"', the other entitlement cert serials remain unchanged");
		*/
		
		expectedStdoutMsg = "Successfully unsubscribed serial numbers:";	// added by bug 867766
		expectedStdoutMsg = "Successfully removed serial numbers:";	// changed by bug 874749
		Assert.assertTrue(result.getStdout().contains(expectedStdoutMsg), "Stdout from unsubscribe contains expected message: "+expectedStdoutMsg);
		expectedStdoutMsg = "   "+serialNumber;	// added by bug 867766
		Assert.assertTrue(result.getStdout().contains(expectedStdoutMsg), "Stdout from unsubscribe contains expected message: "+expectedStdoutMsg);
		Assert.assertEquals(result.getStderr(),"", "Stderr from unsubscribe.");
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(0), "ExitCode from unsubscribe when the serial's entitlement cert file ("+certFilePath+") does exist.");	// added by bug 873791

		return true;
	}
	
	/**
	 * Unsubscribe from the given product subscription using its serial number.
	 * @param productSubscription
	 * @return - false when the productSubscription has already been unsubscribed at a previous time
	 */
	public boolean unsubscribeFromProductSubscription(ProductSubscription productSubscription) {
		
		log.info("Unsubscribing from product subscription: "+ productSubscription);
		boolean unsubscribed = unsubscribeFromSerialNumber(productSubscription.serialNumber);
		
		Assert.assertTrue(!getCurrentlyConsumedProductSubscriptions().contains(productSubscription),
				"The currently consumed product subscriptions does not contain product: "+productSubscription);

		return unsubscribed;
	}
	
	/**
	 * Issues a call to "subscription-manager unsubscribe --all" which will unsubscribe from
	 * all currently consumed product subscriptions and then asserts the list --consumed is empty.
	 */
	public void unsubscribeFromAllOfTheCurrentlyConsumedProductSubscriptions() {

		unsubscribe(true, (BigInteger)null, null, null, null);

		// assert that there are no product subscriptions consumed
		Assert.assertEquals(listConsumedProductSubscriptions().getStdout().trim(),
				"No consumed subscription pools to list","Successfully unsubscribed from all consumed products.");
		
		// assert that there are no entitlement cert files
		Assert.assertTrue(sshCommandRunner.runCommandAndWait("find "+entitlementCertDir+" -name '*.pem' | grep -v key.pem").getStdout().equals(""),
				"No entitlement cert files exist after unsubscribing from all subscription pools.");

		// assert that the yum redhat repo file is gone
		/* bad assert...  the repo file is present but empty
		Assert.assertTrue(RemoteFileTasks.testFileExists(sshCommandRunner, redhatRepoFile)==0,
				"The redhat repo file '"+redhatRepoFile+"' has been removed after unsubscribing from all subscription pools.");
		*/
	}
	
	/**
	 * Individually unsubscribe from each of the currently consumed product subscriptions.
	 * This will ultimately issue multiple calls to unsubscribe --serial SERIAL for each of the product subscriptions being consumed. 
	 */
	public void unsubscribeFromTheCurrentlyConsumedProductSubscriptionsIndividually() {
		log.info("Unsubscribing from each of the currently consumed product subscription serials one at a time...");
		for(ProductSubscription sub : getCurrentlyConsumedProductSubscriptions())
			unsubscribeFromProductSubscription(sub);
		Assert.assertTrue(getCurrentlyConsumedProductSubscriptions().size()==0,
				"Currently no product subscriptions are consumed.");
		Assert.assertTrue(getCurrentEntitlementCertFiles().size()==0,
				"This machine has no entitlement certificate files.");			
	}
	
	/**
	 * Collectively unsubscribe from all of the currently consumed product subscriptions.
	 * This will ultimately issue a single call to unsubscribe --serial SERIAL1 --serial SERIAL2 --serial SERIAL3 for each of the product subscriptions being consumed. 
	 * @throws Exception 
	 */
	public SSHCommandResult unsubscribeFromTheCurrentlyConsumedProductSubscriptionsCollectively() throws Exception {
		log.info("Unsubscribing from all of the currently consumed product subscription serials in one collective call...");
		List<BigInteger> serials = new ArrayList<BigInteger>();
	
		// THIS CREATES PROBLEMS WHEN MODIFIER ENTITLEMENTS ARE BEING CONSUMED; ENTITLEMENTS FROM MODIFIER POOLS COULD REMAIN AFTER THE COLLECTIVE UNSUBSCRIBE
		//for(ProductSubscription productSubscription : getCurrentlyConsumedProductSubscriptions()) serials.add(sub.serialNumber);
		
		// THIS AVOIDS PROBLEMS WHEN MODIFIER ENTITLEMENTS ARE BEING CONSUMED
		for(ProductSubscription productSubscription : getCurrentlyConsumedProductSubscriptions()) {
			EntitlementCert entitlementCert = getEntitlementCertCorrespondingToProductSubscription(productSubscription);
			JSONObject jsonEntitlement = CandlepinTasks.getEntitlementUsingRESTfulAPI(this.currentlyRegisteredUsername,this.currentlyRegisteredPassword,SubscriptionManagerBaseTestScript.sm_serverUrl,entitlementCert.id);
			String poolHref = jsonEntitlement.getJSONObject("pool").getString("href");
			JSONObject jsonPool = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(this.currentlyRegisteredUsername,this.currentlyRegisteredPassword,SubscriptionManagerBaseTestScript.sm_serverUrl,poolHref));
			String poolId = jsonPool.getString("id");
				
			if (CandlepinTasks.isPoolAModifier(this.currentlyRegisteredUsername, this.currentlyRegisteredPassword, poolId,  SubscriptionManagerBaseTestScript.sm_serverUrl)) {
				serials.add(0,productSubscription.serialNumber);	// serials to entitlements that modify others should be at the front of the list to be removed, otherwise they will get re-issued under a new serial number when the modified entitlement is removed first.
			} else {
				serials.add(productSubscription.serialNumber);
			}
		}
		
		// unsubscribe from all serials collectively
		SSHCommandResult result = unsubscribe(false,serials,null,null,null);
		Assert.assertTrue(getCurrentlyConsumedProductSubscriptions().size()==0,
				"Currently no product subscriptions are consumed.");
		Assert.assertTrue(getCurrentEntitlementCertFiles().size()==0,
				"This machine has no entitlement certificate files.");
		return result;
	}
	
	
	
	// facts module tasks ************************************************************
	
	/**
	 * facts without asserting results
	 * @param proxy TODO
	 * @param proxyuser TODO
	 * @param proxypassword TODO
	 */
	public SSHCommandResult facts_(Boolean list, Boolean update, String proxy, String proxyuser, String proxypassword) {

		// assemble the command
		String command = this.command;	command += " facts";	
		if (list!=null && list)			command += " --list";
		if (update!=null && update)		command += " --update";
		if (proxy!=null)				command += " --proxy="+proxy;
		if (proxyuser!=null)			command += " --proxyuser="+proxyuser;
		if (proxypassword!=null)		command += " --proxypassword="+proxypassword;
		
		// run command without asserting results
		return sshCommandRunner.runCommandAndWait(command);
	}
	
	/**
	 * @param list
	 * @param update
	 * @param proxy TODO
	 * @param proxyuser TODO
	 * @param proxypassword TODO
	 * @return
	 */
	public SSHCommandResult facts(Boolean list, Boolean update, String proxy, String proxyuser, String proxypassword) {
		
		SSHCommandResult sshCommandResult = facts_(list, update, proxy, proxyuser, proxypassword);

		// assert results for a successful facts
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "The exit code from the facts command indicates a success.");
		String regex = "";
		if (list!=null && list)		regex=".*:.*";						// list of the current facts
		if (update!=null && update)	regex="Successfully updated the system facts\\.";	// regex=getCurrentConsumerCert().consumerid;	// consumerid	// RHEL57 RHEL61

		Assert.assertContainsMatch(sshCommandResult.getStdout().trim(), regex);
		
		return sshCommandResult; // from the facts command
	}
	
	
	
	// version module tasks ************************************************************
	
	/**
	 * version without asserting results
	 * @return result of the command line call to subscription-manager version
	 */
	public SSHCommandResult version_() {

		// assemble the command
		String command = this.command;	command += " version";	
		
		// run command without asserting results
		return sshCommandRunner.runCommandAndWait(command);
	}
	
	/**
	 * version with asserting results
	 * @return result of the command line call to subscription-manager version
	 */
	public SSHCommandResult version() {
		
		SSHCommandResult sshCommandResult = version_();

		// assert results for a successful version
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(0), "The exit code from the version command indicates a success.");
		
		return sshCommandResult; // from the version command
	}
	
	
	
	
	
	
	
	
	
	
	
//	public boolean areAllRequiredTagsInContentNamespaceProvidedByProductCerts(ContentNamespace contentNamespace, List<ProductCert> productCerts) {
//
//		// get all of the provided tags from the productCerts
//		List<String> providedTags = new ArrayList<String>();
//		for (ProductCert productCert : productCerts) {
//			for (ProductNamespace productNamespace : productCert.productNamespaces) {
//				if (productNamespace.providedTags!=null) {
//					for (String providedTag : productNamespace.providedTags.split("\\s*,\\s*")) {
//						providedTags.add(providedTag);
//					}
//				}
//			}
//		}
//		
//		// get all of the required tags from the contentNamespace
//		List<String> requiredTags = new ArrayList<String>();
//		if (contentNamespace.requiredTags!=null) {
//			for (String requiredTag : contentNamespace.requiredTags.split("\\s*,\\s*")) {
//				requiredTags.add(requiredTag);
//			}
//		}
//		
//		// are ALL of the requiredTags provided?  Note: true is returned (and should be) when requiredTags.isEmpty()
//		return providedTags.containsAll(requiredTags);
//	}
	public boolean areAllRequiredTagsInContentNamespaceProvidedByProductCerts(ContentNamespace contentNamespace, List<ProductCert> productCerts) {
		return areAllRequiredTagsProvidedByProductCerts(contentNamespace.requiredTags, productCerts);
	}
	
	public boolean areAllRequiredTagsProvidedByProductCerts(String requiredTagsAsString, List<ProductCert> productCerts) {

		// get all of the provided tags from the productCerts
		List<String> providedTags = new ArrayList<String>();
		for (ProductCert productCert : productCerts) {
			if (productCert.productNamespace.providedTags!=null) {
				for (String providedTag : productCert.productNamespace.providedTags.split("\\s*,\\s*")) {
					providedTags.add(providedTag);
				}
			}
		}
		
		// get all of the required tags from the contentNamespace
		List<String> requiredTags = new ArrayList<String>();
		if (requiredTagsAsString!=null) {
			for (String requiredTag : requiredTagsAsString.split("\\s*,\\s*")) {
				if (!requiredTag.isEmpty()) requiredTags.add(requiredTag);
			}
		}
		
		// are ALL of the requiredTags provided?  Note: true is returned (and should be) when requiredTags.isEmpty()
		return providedTags.containsAll(requiredTags);
	}
	
	public boolean isPackageInstalled(String pkg) {
		// [root@dell-pe2800-01 ~]# rpm -q subscription-manager-migration-data
		// package subscription-manager-migration-data is not installed
		// [root@dell-pe2800-01 ~]# echo $?
		// 1
		// [root@dell-pe2800-01 ~]# rpm -q subscription-manager
		// subscription-manager-0.98.14-1.el5
		// [root@dell-pe2800-01 ~]# echo $?
		// 0
		// [root@dell-pe2800-01 ~]# 

		return sshCommandRunner.runCommandAndWait("rpm -q "+pkg).getExitCode()==0? true:false;
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
		
		/* [root@jsefler-itclient01 product]# yum repolist all
Loaded plugins: pidplugin, refresh-packagekit, rhnplugin, rhsmplugin
Updating Red Hat repositories.
INFO:repolib:repos updated: 0
This system is not registered with RHN.
RHN support will be disabled.
red-hat-enterprise-linux-6-entitlement-alpha-rpms                                                                         | 4.0 kB     00:00     
red-hat-enterprise-linux-6-entitlement-alpha-rpms-updates                                                                 |  951 B     00:00     
repo id                                                                        repo name                                           status
red-hat-enterprise-linux-6-entitlement-alpha-debug-rpms                        Red Hat Enterprise Linux 6 Entitlement Alpha (Debug disabled
red-hat-enterprise-linux-6-entitlement-alpha-debug-rpms-updates                Red Hat Enterprise Linux 6 Entitlement Alpha (Debug disabled
red-hat-enterprise-linux-6-entitlement-alpha-optional-debug-rpms               Red Hat Enterprise Linux 6 Entitlement Alpha - Opti disabled
red-hat-enterprise-linux-6-entitlement-alpha-optional-debug-rpms-updates       Red Hat Enterprise Linux 6 Entitlement Alpha - Opti disabled
red-hat-enterprise-linux-6-entitlement-alpha-optional-rpms                     Red Hat Enterprise Linux 6 Entitlement Alpha - Opti disabled
red-hat-enterprise-linux-6-entitlement-alpha-optional-rpms-updates             Red Hat Enterprise Linux 6 Entitlement Alpha - Opti disabled
red-hat-enterprise-linux-6-entitlement-alpha-optional-source-rpms              Red Hat Enterprise Linux 6 Entitlement Alpha - Opti disabled
red-hat-enterprise-linux-6-entitlement-alpha-optional-source-rpms-updates      Red Hat Enterprise Linux 6 Entitlement Alpha - Opti disabled
red-hat-enterprise-linux-6-entitlement-alpha-rpms                              Red Hat Enterprise Linux 6 Entitlement Alpha (RPMs) enabled: 3,394
red-hat-enterprise-linux-6-entitlement-alpha-rpms-updates                      Red Hat Enterprise Linux 6 Entitlement Alpha (RPMs) enabled:     0
red-hat-enterprise-linux-6-entitlement-alpha-source-rpms                       Red Hat Enterprise Linux 6 Entitlement Alpha (Sourc disabled
red-hat-enterprise-linux-6-entitlement-alpha-source-rpms-updates               Red Hat Enterprise Linux 6 Entitlement Alpha (Sourc disabled
red-hat-enterprise-linux-6-entitlement-alpha-supplementary-debug-rpms          Red Hat Enterprise Linux 6 Entitlement Alpha - Supp disabled
red-hat-enterprise-linux-6-entitlement-alpha-supplementary-debug-rpms-updates  Red Hat Enterprise Linux 6 Entitlement Alpha - Supp disabled
red-hat-enterprise-linux-6-entitlement-alpha-supplementary-rpms                Red Hat Enterprise Linux 6 Entitlement Alpha - Supp disabled
red-hat-enterprise-linux-6-entitlement-alpha-supplementary-rpms-updates        Red Hat Enterprise Linux 6 Entitlement Alpha - Supp disabled
red-hat-enterprise-linux-6-entitlement-alpha-supplementary-source-rpms         Red Hat Enterprise Linux 6 Entitlement Alpha - Supp disabled
red-hat-enterprise-linux-6-entitlement-alpha-supplementary-source-rpms-updates Red Hat Enterprise Linux 6 Entitlement Alpha - Supp disabled
repolist: 3,394
		*/
		
		List<ProductCert> currentProductCerts = this.getCurrentProductCerts();
		
		// TEMPORARY WORKAROUND FOR BUG: https://bugzilla.redhat.com/show_bug.cgi?id=697087 - jsefler 04/27/2011
		if (this.redhatRelease.contains("release 5")) {
			boolean invokeWorkaroundWhileBugIsOpen = true;
			String bugId="697087"; 
			// NOTE: LET'S MAKE THIS A PERMANENT WORKAROUND FOR THIS METHOD
			// try {if (invokeWorkaroundWhileBugIsOpen/*&&BzChecker.getInstance().isBugOpen(bugId)*/) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
			if (invokeWorkaroundWhileBugIsOpen) {
				
				List<String> yumRepoListAll			= this.getYumRepolist("all");
				List<String> yumRepoListEnabled		= this.getYumRepolist("enabled");
				List<String> yumRepoListDisabled	= this.getYumRepolist("disabled");
				
		 		for (EntitlementCert entitlementCert : entitlementCerts) {
		 			for (ContentNamespace contentNamespace : entitlementCert.contentNamespaces) {
		 				if (!contentNamespace.type.equalsIgnoreCase("yum")) continue;
		 				if (areReported && areAllRequiredTagsInContentNamespaceProvidedByProductCerts(contentNamespace,currentProductCerts)) {
							if (contentNamespace.enabled) {
								Assert.assertTrue(yumRepoListEnabled.contains(contentNamespace.label),
										"Yum repolist enabled includes repo id/label '"+contentNamespace.label+"' that comes from entitlement cert "+entitlementCert.id+"'s content namespace: "+contentNamespace);
							} else {
								Assert.assertTrue(yumRepoListDisabled.contains(contentNamespace.label),
										"Yum repolist disabled includes repo id/label '"+contentNamespace.label+"' that comes from entitlement cert "+entitlementCert.id+"'s content namespace: "+contentNamespace);
							}
		 				}
						else
							Assert.assertFalse(yumRepoListAll.contains(contentNamespace.label),
									"Yum repolist all excludes repo id/label '"+contentNamespace.label+"'.");
			 		}
		 		}
		 		return;
			}
		}
		// END OF WORKAROUND
		
		
				
				
		// assert all of the entitlement certs are reported in the stdout from "yum repolist all"
		sshCommandRunner.runCommandAndWait("killall -9 yum");
		SSHCommandResult result = sshCommandRunner.runCommandAndWait("yum repolist all --disableplugin=rhnplugin");	// FIXME, THIS SHOULD MAKE USE OF getYumRepolist
 		for (EntitlementCert entitlementCert : entitlementCerts) {
 			for (ContentNamespace contentNamespace : entitlementCert.contentNamespaces) {
 				
 				// Note: When the repo id and repo name are really long, the repo name in the yum repolist all gets crushed (hence the reason for .* in the regex)
				String regex = String.format("^%s\\s+(?:%s|.*)\\s+%s", contentNamespace.label.trim(), contentNamespace.name.substring(0,Math.min(contentNamespace.name.length(), 25)), contentNamespace.enabled? "enabled:":"disabled$");	// 25 was arbitraily picked to be short enough to be displayed by yum repolist all
				boolean isReported = Pattern.compile(regex,Pattern.MULTILINE).matcher(result.getStdout()).find();

				boolean areAllRequiredTagsInstalled = areAllRequiredTagsInContentNamespaceProvidedByProductCerts(contentNamespace,currentProductCerts);
				if (!contentNamespace.type.equalsIgnoreCase("yum")) {
//					Assert.assertContainsNoMatch(result.getStdout(), regex, null, "ContentNamespace label '"+contentNamespace.label.trim()+"' from EntitlementCert '"+entitlementCert.serialNumber+"' is NOT reported in yum repolist all since its type '"+contentNamespace.type+"' is non-yum.");
					Assert.assertTrue(!isReported, "ContentNamespace label '"+contentNamespace.label.trim()+"' from EntitlementCert '"+entitlementCert.serialNumber+"' is NOT reported in yum repolist all since its type '"+contentNamespace.type+"' is non-yum.");
				} else if (areReported && areAllRequiredTagsInstalled) {
//					Assert.assertContainsMatch(result.getStdout(), regex, null, "ContentNamespace label '"+contentNamespace.label.trim()+"' from EntitlementCert '"+entitlementCert.serialNumber+"' is reported in yum repolist all.");
					Assert.assertTrue(isReported, "ContentNamespace label '"+contentNamespace.label.trim()+"' from EntitlementCert '"+entitlementCert.serialNumber+"' is reported in yum repolist all.");
				} else {
//					Assert.assertContainsNoMatch(result.getStdout(), regex, null, "ContentNamespace label '"+contentNamespace.label.trim()+"' from EntitlementCert '"+entitlementCert.serialNumber+"' is NOT reported in yum repolist all"+((areReported&&!areAllRequiredTagsInstalled)?" since all its required tags '"+contentNamespace.requiredTags+"' are NOT found among the currently installed product certs.":"."));
					Assert.assertTrue(!isReported, "ContentNamespace label '"+contentNamespace.label.trim()+"' from EntitlementCert '"+entitlementCert.serialNumber+"' is NOT reported in yum repolist all"+((areReported&&!areAllRequiredTagsInstalled)?" since all its required tags '"+contentNamespace.requiredTags+"' are NOT found among the currently installed product certs.":"."));
				}
 			}
 		}

		// assert that the sshCommandRunner.getStderr() does not contains an error on the entitlementCert.download_url e.g.: http://redhat.com/foo/path/never/repodata/repomd.xml: [Errno 14] HTTP Error 404 : http://www.redhat.com/foo/path/never/repodata/repomd.xml 
		// FIXME EVENTUALLY WE NEED TO UNCOMMENT THIS ASSERT
		//Assert.assertContainsNoMatch(result.getStderr(), "HTTP Error \\d+", "HTTP Errors were encountered when runnning yum repolist all.");
	}
	
	/**
	 * @param options [all|enabled|disabled] [--option=...]
	 * @return array of repo labels returned from a call to yum repolist [options]
	 */
	public ArrayList<String> getYumRepolist(String options){
		if (options==null) options="";
		ArrayList<String> repos = new ArrayList<String>();
		sshCommandRunner.runCommandAndWait("killall -9 yum");
		sshCommandRunner.runCommandAndWait("yum repolist "+options+" --disableplugin=rhnplugin"); // --disableplugin=rhnplugin helps avoid: up2date_client.up2dateErrors.AbuseError
				
		// TEMPORARY WORKAROUND FOR BUG: https://bugzilla.redhat.com/show_bug.cgi?id=697087 - jsefler 04/27/2011
		if (this.redhatRelease.contains("release 5")) {
			boolean invokeWorkaroundWhileBugIsOpen = true;
			String bugId="697087"; 
			try {if (invokeWorkaroundWhileBugIsOpen && BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
			if (invokeWorkaroundWhileBugIsOpen) {
				
				// avoid "yum repolist" and assemble the list of repos directly from the redhat repo file
				List<YumRepo> yumRepoList =   getCurrentlySubscribedYumRepos();
				for (YumRepo yumRepo : yumRepoList) {
					if		(options.startsWith("all"))													repos.add(yumRepo.id);
					else if (options.startsWith("enabled")	&& yumRepo.enabled.equals(Boolean.TRUE))	repos.add(yumRepo.id);
					else if (options.startsWith("disabled")	&& yumRepo.enabled.equals(Boolean.FALSE))	repos.add(yumRepo.id);
					else if (options.equals("")				&& yumRepo.enabled.equals(Boolean.TRUE))	repos.add(yumRepo.id);
				}
				sshCommandRunner.runCommandAndWait("yum repolist "+options+" --disableplugin=rhnplugin"); // --disableplugin=rhnplugin helps avoid: up2date_client.up2dateErrors.AbuseError
				return repos;
			}
		}
		// END OF WORKAROUND
		
		// WARNING: DO NOT MAKE ANYMORE CALLS TO sshCommandRunner.runCommand* DURING EXECUTION OF THE REMAINDER OF THIS METHOD.
		// getYumRepolistPackageCount() ASSUMES sshCommandRunner.getStdout() CAME FROM THE CALL TO yum repolist

		// Example sshCommandRunner.getStdout()
		//	[root@jsefler-itclient01 product]# yum repolist all
		//	Loaded plugins: pidplugin, refresh-packagekit, rhnplugin, rhsmplugin
		//	Updating Red Hat repositories.
		//	INFO:repolib:repos updated: 0
		//	This system is not registered with RHN.
		//	RHN support will be disabled.
		//	red-hat-enterprise-linux-6-entitlement-alpha-rpms                                                                         | 4.0 kB     00:00     
		//	red-hat-enterprise-linux-6-entitlement-alpha-rpms-updates                                                                 |  951 B     00:00     
		//	repo id                                                                        repo name                                           status
		//	red-hat-enterprise-linux-6-entitlement-alpha-debug-rpms                        Red Hat Enterprise Linux 6 Entitlement Alpha (Debug disabled
		//	red-hat-enterprise-linux-6-entitlement-alpha-debug-rpms-updates                Red Hat Enterprise Linux 6 Entitlement Alpha (Debug disabled
		//	red-hat-enterprise-linux-6-entitlement-alpha-optional-debug-rpms               Red Hat Enterprise Linux 6 Entitlement Alpha - Opti disabled
		//	red-hat-enterprise-linux-6-entitlement-alpha-optional-debug-rpms-updates       Red Hat Enterprise Linux 6 Entitlement Alpha - Opti disabled
		//	repolist: 3,394
		
		//	[root@athlon6 ~]# yum repolist enabled --disableplugin=rhnplugin
		//	Loaded plugins: product-id, refresh-packagekit, security, subscription-manager
		//	No plugin match for: rhnplugin
		//	Updating certificate-based repositories.
		//	repolist: 0
		
		String[] availRepos = sshCommandRunner.getStdout().split("\\n");
		
		int repolistStartLn = 0;
		int repolistEndLn = 0;
		
		for(int i=0;i<availRepos.length;i++)
			if (availRepos[i].startsWith("repo id"))
				repolistStartLn = i + 1;
			else if (availRepos[i].startsWith("repolist:"))
				repolistEndLn = i;
		if (repolistStartLn>0)
			for(int i=repolistStartLn;i<repolistEndLn;i++)
				repos.add(availRepos[i].split(" ")[0]);
		
		return repos;
	}
	

	/**
	 * @param options [all|enabled|disabled] [--option=...]
	 * @return the value reported at the bottom of a call to yum repolist [options] (repolist: value)
	 */
	public Integer getYumRepolistPackageCount(String options){
		getYumRepolist(options);

		// Example sshCommandRunner.getStdout()
		//	[root@jsefler-itclient01 product]# yum repolist all
		//	Loaded plugins: pidplugin, refresh-packagekit, rhnplugin, rhsmplugin
		//	Updating Red Hat repositories.
		//	INFO:repolib:repos updated: 0
		//	This system is not registered with RHN.
		//	RHN support will be disabled.
		//	red-hat-enterprise-linux-6-entitlement-alpha-rpms                                                                         | 4.0 kB     00:00     
		//	red-hat-enterprise-linux-6-entitlement-alpha-rpms-updates                                                                 |  951 B     00:00     
		//	repo id                                                                        repo name                                           status
		//	red-hat-enterprise-linux-6-entitlement-alpha-debug-rpms                        Red Hat Enterprise Linux 6 Entitlement Alpha (Debug disabled
		//	red-hat-enterprise-linux-6-entitlement-alpha-debug-rpms-updates                Red Hat Enterprise Linux 6 Entitlement Alpha (Debug disabled
		//	red-hat-enterprise-linux-6-entitlement-alpha-optional-debug-rpms               Red Hat Enterprise Linux 6 Entitlement Alpha - Opti disabled
		//	red-hat-enterprise-linux-6-entitlement-alpha-optional-debug-rpms-updates       Red Hat Enterprise Linux 6 Entitlement Alpha - Opti disabled
		//	repolist: 3,394
		
		// Example sshCommandRunner.getStderr()
		//	INFO:rhsm-app.repolib:repos updated: 63
		//	https://cdn.redhat.com/FOO/content/beta/rhel/server/6/6Server/x86_64/os/repodata/repomd.xml: [Errno 14] PYCURL ERROR 22 - "The requested URL returned error: 403"
		//	https://cdn.redhat.com/content/beta/rhel/client/6/x86_64/supplementary/source/SRPMS/repodata/repomd.xml: [Errno 14] PYCURL ERROR 22 - "The requested URL returned error: 404"

		Assert.assertTrue(!sshCommandRunner.getStderr().contains("The requested URL returned error:"),"The requested URL did NOT return an error.");
		
		// parse out the value from repolist: value
		String regex="repolist:(.*)";
		
		Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
		Matcher matcher = pattern.matcher(sshCommandRunner.getStdout());
		//Assert.assertTrue(matcher.find(),"Found fact "+factName);
		if (!matcher.find()) {
			log.warning("Did not find repolist package count.");
			return null;
		}
		
		Integer packageCount = Integer.valueOf(matcher.group(1).replaceAll(",","").trim());

		return packageCount;
	}
	
	
	@Deprecated	// replaced by public ArrayList<String> getYumListAvailable (String options)
	public ArrayList<String> getYumListOfAvailablePackagesFromRepo (String repoLabel) {
		ArrayList<String> packages = new ArrayList<String>();
		sshCommandRunner.runCommandAndWait("killall -9 yum");

		int min = 5;
		log.fine("Using a timeout of "+min+" minutes for next command...");
		//SSHCommandResult result = sshCommandRunner.runCommandAndWait("yum list available",Long.valueOf(min*60000));
		SSHCommandResult result = sshCommandRunner.runCommandAndWait("yum list available --disablerepo=* --enablerepo="+repoLabel+" --disableplugin=rhnplugin",Long.valueOf(min*60000));  // --disableplugin=rhnplugin helps avoid: up2date_client.up2dateErrors.AbuseError

		// Example result.getStdout()
		//xmltex.noarch                             20020625-16.el6                      red-hat-enterprise-linux-6-entitlement-alpha-rpms
		//xmlto.x86_64                              0.0.23-3.el6                         red-hat-enterprise-linux-6-entitlement-alpha-rpms
		//xmlto-tex.noarch                          0.0.23-3.el6                         red-hat-enterprise-linux-6-entitlement-alpha-rpms
		//xorg-x11-apps.x86_64                      7.4-10.el6                           red-hat-enterprise-linux-6-entitlement-alpha-rpms

		String regex="(\\S+) +(\\S+) +"+repoLabel+"$";
		Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
		Matcher matcher = pattern.matcher(result.getStdout());
		if (!matcher.find()) {
			log.fine("Did NOT find any available packages from repoLabel: "+repoLabel);
			return packages;
		}

		// assemble the list of packages and return them
		do {
			packages.add(matcher.group(1)); // group(1) is the pkg,  group(2) is the version
		} while (matcher.find());
		return packages;		
	}
	
//	public ArrayList<String> yumListAvailable (String disableplugin, String disablerepo, String enablerepo, String globExpression) {
	/**
	 * @param options
	 * @return array of packages returned from a call to yum list available [options]
	 */
	public ArrayList<String> getYumListAvailable (String options) {
		ArrayList<String> packages = new ArrayList<String>();
		sshCommandRunner.runCommandAndWait("killall -9 yum");
		if (options==null) options="";

//		String							command  = "yum list available";
//		if (disableplugin!=null)		command += " --disableplugin="+disableplugin;
//		if (disablerepo!=null)			command += " --disablerepo="+disablerepo;
//		if (enablerepo!=null)			command += " --enablerepo="+enablerepo;
//		if (globExpression!=null)		command += " "+globExpression;
		String							command  = "yum list available "+options+" --disableplugin=rhnplugin"; // --disableplugin=rhnplugin helps avoid: up2date_client.up2dateErrors.AbuseError
		
		// execute the yum command to list available packages
		int min = 5;
		log.fine("Using a timeout of "+min+" minutes for next command...");
		SSHCommandResult result = sshCommandRunner.runCommandAndWait(command,Long.valueOf(min*60000));
		
		// Example result.getStderr() 
		//	INFO:repolib:repos updated: 0
		//	This system is not registered with RHN.
		//	RHN support will be disabled.
		//	Error: No matching Packages to list
		if (result.getStderr().contains("Error: No matching Packages to list")) {
			log.info("No matching Packages to list from: "+command);
			return packages;
		}
		
		// Example result.getStdout()
		//  Loaded plugins: product-id, refresh-packagekit, subscription-manager
		//  No plugin match for: rhnplugin
		//  Updating certificate-based repositories.
		//  Available Packages
		//	xmltex.noarch                 20020625-16.el6     red-hat-enterprise-linux-6-entitlement-alpha-rpms
		//	xmlto.x86_64                  0.0.23-3.el6        red-hat-enterprise-linux-6-entitlement-alpha-rpms
		//	xmlto-tex.noarch              0.0.23-3.el6        red-hat-enterprise-linux-6-entitlement-alpha-rpms
		//	xorg-x11-apps.x86_64          7.4-10.el6          red-hat-enterprise-linux-6-entitlement-alpha-rpms
		//	pacemaker-libs-devel.i686     1.1.7-6.el6         rhel-ha-for-rhel-6-server-rpms
		//	pacemaker-libs-devel.x86_64   1.1.7-6.el6         rhel-ha-for-rhel-6-server-rpms
		//	perl-Net-Telnet.noarch        3.03-11.el6         rhel-ha-for-rhel-6-server-rpms
		//	pexpect.noarch                2.3-6.el6           rhel-ha-for-rhel-6-server-rpms
		//	python-repoze-what-plugins-sql.noarch
		//	                              1.0-0.6.rc1.el6     rhel-ha-for-rhel-6-server-rpms
		//	python-repoze-what-quickstart.noarch
		//	                              1.0.1-1.el6         rhel-ha-for-rhel-6-server-rpms
		//	python-repoze-who-friendlyform.noarch
		//	                              1.0-0.3.b3.el6      rhel-ha-for-rhel-6-server-rpms
		//	python-repoze-who-plugins-sa.noarch
		//	                              1.0-0.4.rc1.el6     rhel-ha-for-rhel-6-server-rpms
		//	python-suds.noarch            0.4.1-3.el6         rhel-ha-for-rhel-6-server-rpms
		//	python-tw-forms.noarch        0.9.9-1.el6         rhel-ha-for-rhel-6-server-rpms
		//	resource-agents.x86_64        3.9.2-12.el6_3.2    rhel-ha-for-rhel-6-server-rpms

		String availablePackadesTable = result.getStdout();	String prefix = "Available Packages";
		if (availablePackadesTable.contains(prefix)) {
			availablePackadesTable = availablePackadesTable.substring(availablePackadesTable.indexOf(prefix)+prefix.length(), availablePackadesTable.length()).trim();	// strip leading info before the list of "Availabile Packages"
		}
		
		//if (enablerepo==null||enablerepo.equals("*")) enablerepo="(\\S+)";
		//String regex="^(\\S+) +(\\S+) +"+enablerepo+"$";
		//String regex="^(\\S+) +(\\S+) +(\\S+)$";	// assume all the packages are on a line with three words
		String regex="^(\\S+)(?:\\n)? +(\\S+) +(\\S+)$";
		Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
		Matcher matcher = pattern.matcher(availablePackadesTable);
		if (!matcher.find()) {
			log.info("Did NOT find any available packages from: "+command);
			return packages;
		}

		// assemble the list of packages and return them
		do {
			packages.add(matcher.group(1)); // group(1) is the pkg,  group(2) is the version,  group(3) is the repo
		} while (matcher.find());
		
		// flip the packages since the ones at the end of the list are usually easier to install 
		ArrayList<String> packagesCloned = (ArrayList<String>) packages.clone(); packages.clear();
		for (int p=packagesCloned.size()-1; p>=0; p--) packages.add(packagesCloned.get(p));

		return packages;
	}
	
	/**
	 * Disable all of the repos in /etc/yum.repos.d
	 * NOTE: On RHEL5, yum-utils must be installed first.
	 */
	public void yumDisableAllRepos() {
		yumDisableAllRepos(null);
	}
	
	/**
	 * Disable all of the repos in /etc/yum.repos.d
	 * NOTE: On RHEL5, yum-utils must be installed first.
	 * @param options - any additional options that you want appended when calling "yum repolist enabled" and "yum-config-manager --disable REPO"
	 */
	public void yumDisableAllRepos(String options) {
		if (options==null) options="";
		for (String repo : getYumRepolist(("enabled"+" "+options).trim())) {
			String command = ("yum-config-manager --disable "+repo+" "+options).trim();
			SSHCommandResult result = sshCommandRunner.runCommandAndWait(command);
			Assert.assertEquals(result.getExitCode(), Integer.valueOf(0), "ExitCode from command '"+command+"'.");
		}
	}
	
	public ArrayList<String> yumGroupList (String Installed_or_Available, String options) {
		ArrayList<String> groups = new ArrayList<String>();
		sshCommandRunner.runCommandAndWait("killall -9 yum");

		String command = "yum grouplist "+options+" --disableplugin=rhnplugin"; // --disableplugin=rhnplugin helps avoid: up2date_client.up2dateErrors.AbuseError
		
		// execute the yum command to list available packages
		int min = 5;
		log.fine("Using a timeout of "+min+" minutes for next command...");
		SSHCommandResult result = sshCommandRunner.runCommandAndWait(command,Long.valueOf(min*60000));
		
		// Example result.getStdout()
		//	[root@jsefler-betaqa-1 product]# yum grouplist --disablerepo=* --enablerepo=rhel-entitlement-beta
		//	Loaded plugins: product-id, refresh-packagekit, rhnplugin, subscription-manager
		//	Updating Red Hat repositories.
		//	INFO:rhsm-app.repolib:repos updated: 0
		//	This system is not registered with RHN.
		//	RHN support will be disabled.
		//	Setting up Group Process
		//	rhel-entitlement-beta                                                                                                                                 | 4.0 kB     00:00     
		//	rhel-entitlement-beta/group_gz                                                                                                                        | 190 kB     00:00     
		//	Installed Groups:
		//	   Additional Development
		//	   Assamese Support
		//	   Base
		//	Available Groups:
		//	   Afrikaans Support
		//	   Albanian Support
		//	   Amazigh Support
		//	Done

		String regex = Installed_or_Available+" Groups:((\\n\\s{3}.*)+)";
		Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
		Matcher matcher = pattern.matcher(result.getStdout());
		if (!matcher.find()) {
			log.info("Did NOT find any "+Installed_or_Available+" Groups from: "+command);
			return groups;
		}

		// assemble the list of groups and return them
		for (String group : matcher.group(1).trim().split("\\n\\s{3}")) groups.add(group);

		return groups;		
	}
	
	
	/**
	 * Find an available package for install that is unique to the specified repo label.
	 * @param repo
	 * @return
	 * Note: You should consider calling yumDisableAllRepos() before using this method especially when this client was provisioned by Beaker.
	 */
	public String findUniqueAvailablePackageFromRepo (String repo) {
		for (String pkg : getYumListAvailable("--disablerepo=* --enablerepo="+repo)) {
			if (!getYumListAvailable("--disablerepo="+repo+" "+pkg).contains(pkg)) {
				if (yumCanInstallPackageFromRepo(pkg,repo,null)) {
					return pkg;
				}
			}
		}
		return null;
	}
	
	public String findRandomAvailablePackageFromRepo (String repo) {
		ArrayList<String> pkgs = getYumListAvailable("--disablerepo=* --enablerepo="+repo);
		if (pkgs.isEmpty()) return null;
		return pkgs.get(SubscriptionManagerCLITestScript.randomGenerator.nextInt(pkgs.size()));
	}
	
	public String findAnAvailableGroupFromRepo(String repo) {
		List <String> groups = yumGroupList("Available", "--disablerepo=* --enablerepo="+repo);
		for (int i=0; i<groups.size(); i++) {
			String group = groups.get(i);

			// choose a group that has "Mandatory Packages:"
			String mandatoryPackages = "Mandatory Packages:";
			if (sshCommandRunner.runCommandAndWait("yum groupinfo \""+groups.get(i)+"\" | grep \""+mandatoryPackages+"\"").getStdout().trim().equals(mandatoryPackages)) return group;
		}
		return null;
	}

	public String findAnInstalledGroupFromRepo(String repo) {
		List <String> groups = yumGroupList("Installed", "--disablerepo=* --enablerepo="+repo);
		for (int i=0; i<groups.size(); i++) {
			String group = groups.get(i);
			// don't consider these very important groups
			if (group.equals("Base")) continue;
			if (group.equals("X Window System")) continue;
			if (group.startsWith("Network")) continue;	// Network Infrastructure Server, Network file system client, Networking Tools
			
			return group;
		}
		return null;
	}
	
	/**
	 * @param pkg
	 * @param repoLabel
	 * @param installOptions
	 * @return true - when pkg can be cleanly installed from repolLabel with installOptions. <br>
	 *         false - when the user is not prompted with "Is this ok [y/N]:" to Complete! the install
	 */
	public boolean yumCanInstallPackageFromRepo (String pkg, String repoLabel, String installOptions) {
		
		// attempt to install the pkg from repo with the installOptions, but say N at the prompt: Is this ok [y/N]: N
		if (installOptions==null) installOptions=""; installOptions = installOptions.replaceFirst("-y", "");
		String command = "echo N | yum install "+pkg+" --enablerepo="+repoLabel+" --disableplugin=rhnplugin "+installOptions; // --disableplugin=rhnplugin helps avoid: up2date_client.up2dateErrors.AbuseError
		SSHCommandResult result = RemoteFileTasks.runCommandAndAssert(sshCommandRunner,command, 1);

		// disregard the package if it was obsoleted...
		
		//	Loaded plugins: product-id, refresh-packagekit, security, subscription-manager
		//	No plugin match for: rhnplugin
		//	Updating certificate-based repositories.
		//	Setting up Install Process
		//	Package gfs-pcmk is obsoleted by cman, trying to install cman-3.0.12.1-21.el6.x86_64 instead
		//	Resolving Dependencies
		//	--> Running transaction check
		//	---> Package cman.x86_64 0:3.0.12.1-21.el6 will be installed
		String regex="Package "+pkg.split("\\.")[0]+".* is obsoleted by (.+), trying to install .+ instead";
		Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
		Matcher matcher = pattern.matcher(sshCommandRunner.getStdout());
		String obsoletedByPkg = null;
		if (matcher.find()) {
			obsoletedByPkg = matcher.group(1);
			// can the obsoletedByPkg be installed from repoLabel instead? 
			//return yumCanInstallPackageFromRepo (obsoletedByPkg, repoLabel, installOptions);
			log.fine("Disregarding package '"+pkg+"' as installable from repo '"+repoLabel+"' because it has been obsoleted.");
			return false;
		}
		
		//	Total download size: 2.1 M
		//	Installed size: 4.8 M
		//	Is this ok [y/N]: N
		//	Exiting on user Command
		return result.getStdout().contains("Is this ok [y/N]:");
	}
	
	// 
	/**
	 * @param pkg
	 * @param repoLabel
	 * @param destdir
	 * @param downloadOptions
	 * @return the actual downloaded package File (null if there was an error)
	 * TODO: on RHEL5, the yum-utils package must be installed first to get yumdownloader
	 */
	public File yumDownloadPackageFromRepo (String pkg, String repoLabel, String destdir, String downloadOptions) {
		
		// use yumdownloader the package with repoLabel enabled
		if (downloadOptions==null) downloadOptions=""; //downloadOptions += " -y";
		String command = "yumdownloader "+pkg+" --destdir="+destdir+" --disablerepo=* --enablerepo="+repoLabel+" --noplugins "+downloadOptions; // --disableplugin=rhnplugin helps avoid: up2date_client.up2dateErrors.AbuseError
		//SSHCommandResult result = RemoteFileTasks.runCommandAndAssert(sshCommandRunner,command, 0, "^Complete!$",null);
		SSHCommandResult result = sshCommandRunner.runCommandAndWait(command+"; "+command); // the second command is needed to populate stdout
		Assert.assertTrue(!result.getStderr().toLowerCase().contains("error"), "Stderr from command '"+command+"' did not report an error.");
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(0), "ExitCode from command '"+command+"'.");

		//[root@jsefler-stage-6server ~]# yumdownloader --disablerepo=* ricci.x86_64 --enablerepo=rhel-ha-for-rhel-6-server-rpms --destdir /tmp
		//Loaded plugins: product-id, refresh-packagekit
		//ricci-0.16.2-35.el6_1.1.x86_64.rpm                                                                                                                                     | 614 kB     00:00     
		//[root@jsefler-stage-6server ~]# yumdownloader --disablerepo=* ricci.x86_64 --enablerepo=rhel-ha-for-rhel-6-server-rpms --destdir /tmp
		//Loaded plugins: product-id, refresh-packagekit
		///tmp/ricci-0.16.2-35.el6_1.1.x86_64.rpm already exists and appears to be complete
		//[root@jsefler-stage-6server ~]# 
		
		// extract the name of the downloaded pkg
		// ([/\w\.\-+]*\.rpm)
		String regex = "([/\\w\\.\\-+]*\\.rpm)";	
		Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
		Matcher matcher = pattern.matcher(result.getStdout());
		if (!matcher.find()) {
			log.warning("Did not find the name of the downloaded pkg using regex '"+regex+"'.");
			return null;
		}
		String rpm = matcher.group(1).trim();	// return the contents of the first capturing group
		
		//File pkgFile = new File(destdir+File.separatorChar+rpm);
		File pkgFile = new File(rpm);
		
		// assert the downloaded file exists
		Assert.assertEquals(RemoteFileTasks.testFileExists(sshCommandRunner,pkgFile.getPath()),1,"Package '"+pkg+"' exists in destdir '"+destdir+"' after yumdownloading.");
		
		return pkgFile;
	}
	
	public SSHCommandResult yumInstallPackageFromRepo (String pkg, String repoLabel, String installOptions) {
		
		// install the package with repoLabel enabled
		String command = "yum -y install "+pkg;
		command += " --disableplugin=rhnplugin";	// --disableplugin=rhnplugin helps avoid: up2date_client.up2dateErrors.AbuseError
		if (repoLabel!=null) command += " --enablerepo="+repoLabel;
		if (installOptions!=null) command += " "+installOptions; 
		//SSHCommandResult result = RemoteFileTasks.runCommandAndAssert(sshCommandRunner,command, 0, "^Complete!$",null);
		SSHCommandResult result = sshCommandRunner.runCommandAndWait(command);
		Assert.assertTrue(!result.getStderr().toLowerCase().contains("error"), "Stderr from command '"+command+"' did not report an error.");
		Assert.assertTrue(result.getStdout().contains("\nComplete!"), "Stdout from command '"+command+"' reported a successful \"Complete!\".");
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(0), "ExitCode from command '"+command+"'.");
		
		//	201104051837:12.757 - FINE: ssh root@jsefler-betastage-server.usersys.redhat.com yum -y install cairo-spice-debuginfo.x86_64 --enablerepo=rhel-6-server-beta-debug-rpms --disableplugin=rhnplugin (com.redhat.qe.tools.SSHCommandRunner.run)
		//	201104051837:18.156 - FINE: Stdout: 
		//	Loaded plugins: product-id, refresh-packagekit, subscription-manager
		//	No plugin match for: rhnplugin
		//	Updating Red Hat repositories.
		//	Setting up Install Process
		//	Package cairo-spice-debuginfo is obsoleted by spice-server, trying to install spice-server-0.7.3-2.el6.x86_64 instead
		//	Resolving Dependencies
		//	--> Running transaction check
		//	---> Package spice-server.x86_64 0:0.7.3-2.el6 will be installed
		//	--> Finished Dependency Resolution
		//
		//	Dependencies Resolved
		//
		//	================================================================================
		//	 Package          Arch       Version          Repository                   Size
		//	================================================================================
		//	Installing:
		//	 spice-server     x86_64     0.7.3-2.el6      rhel-6-server-beta-rpms     245 k
		//
		//	Transaction Summary
		//	================================================================================
		//	Install       1 Package(s)
		//
		//	Total download size: 245 k
		//	Installed size: 913 k
		//	Downloading Packages:
		//	Running rpm_check_debug
		//	Running Transaction Test
		//	Transaction Test Succeeded
		//	Running Transaction
		//
		//	  Installing : spice-server-0.7.3-2.el6.x86_64                              1/1 
		//	duration: 205(ms)
		//
		//	Installed:
		//	  spice-server.x86_64 0:0.7.3-2.el6                                             
		//
		//	Complete!
		//	 (com.redhat.qe.tools.SSHCommandRunner.runCommandAndWait)
		//	201104051837:18.180 - FINE: Stderr: 
		//	INFO:rhsm-app.repolib:repos updated: 63
		//	Installed products updated.
		//	 (com.redhat.qe.tools.SSHCommandRunner.runCommandAndWait)
		//	201104051837:18.182 - FINE: ExitCode: 0 (com.redhat.qe.tools.SSHCommandRunner.runCommandAndWait)

		// EXAMPLE FROM RHEL62
		//	ssh root@tyan-gt24-03.rhts.eng.bos.redhat.com yum install gfs-pcmk.x86_64 --enablerepo=rhel-rs-for-rhel-6-server-rpms --disableplugin=rhnplugin -y
		//	Stdout:
		//	Loaded plugins: product-id, refresh-packagekit, security, subscription-manager
		//	No plugin match for: rhnplugin
		//	Updating certificate-based repositories.
		//	Setting up Install Process
		//	Package gfs-pcmk is obsoleted by cman, trying to install cman-3.0.12.1-19.el6.x86_64 instead
		//	Resolving Dependencies
		//	--> Running transaction check
		//	---> Package cman.x86_64 0:3.0.12.1-19.el6 will be installed
		//	--> Processing Dependency: clusterlib = 3.0.12.1-19.el6 for package: cman-3.0.12.1-19.el6.x86_64
		//	--> Processing Dependency: modcluster >= 0.15.0-3 for package: cman-3.0.12.1-19.el6.x86_64
		//	--> Processing Dependency: fence-virt >= 0.2.3-1 for package: cman-3.0.12.1-19.el6.x86_64
		//	--> Processing Dependency: fence-agents >= 3.1.5-1 for package: cman-3.0.12.1-19.el6.x86_64
		//	--> Processing Dependency: openais >= 1.1.1-1 for package: cman-3.0.12.1-19.el6.x86_64
		//	--> Processing Dependency: ricci >= 0.15.0-4 for package: cman-3.0.12.1-19.el6.x86_64
		//	--> Processing Dependency: corosync >= 1.4.1-3 for package: cman-3.0.12.1-19.el6.x86_64
		//	--> Processing Dependency: libcpg.so.4(COROSYNC_CPG_1.0)(64bit) for package: cman-3.0.12.1-19.el6.x86_64
		//	--> Processing Dependency: libconfdb.so.4(COROSYNC_CONFDB_1.0)(64bit) for package: cman-3.0.12.1-19.el6.x86_64
		//	--> Processing Dependency: libSaCkpt.so.3(OPENAIS_CKPT_B.01.01)(64bit) for package: cman-3.0.12.1-19.el6.x86_64
		//	--> Processing Dependency: libcman.so.3()(64bit) for package: cman-3.0.12.1-19.el6.x86_64
		//	--> Processing Dependency: libfenced.so.3()(64bit) for package: cman-3.0.12.1-19.el6.x86_64
		//	--> Processing Dependency: liblogthread.so.3()(64bit) for package: cman-3.0.12.1-19.el6.x86_64
		//	--> Processing Dependency: libdlm.so.3()(64bit) for package: cman-3.0.12.1-19.el6.x86_64
		//	--> Processing Dependency: libfence.so.4()(64bit) for package: cman-3.0.12.1-19.el6.x86_64
		//	--> Processing Dependency: libccs.so.3()(64bit) for package: cman-3.0.12.1-19.el6.x86_64
		//	--> Processing Dependency: libcpg.so.4()(64bit) for package: cman-3.0.12.1-19.el6.x86_64
		//	--> Processing Dependency: libconfdb.so.4()(64bit) for package: cman-3.0.12.1-19.el6.x86_64
		//	--> Processing Dependency: libdlmcontrol.so.3()(64bit) for package: cman-3.0.12.1-19.el6.x86_64
		//	--> Processing Dependency: libSaCkpt.so.3()(64bit) for package: cman-3.0.12.1-19.el6.x86_64
		//	--> Running transaction check
		//	---> Package clusterlib.x86_64 0:3.0.12.1-19.el6 will be installed
		//	---> Package corosync.x86_64 0:1.4.1-3.el6 will be installed
		//	--> Processing Dependency: libnetsnmp.so.20()(64bit) for package: corosync-1.4.1-3.el6.x86_64
		//	---> Package corosynclib.x86_64 0:1.4.1-3.el6 will be installed
		//	--> Processing Dependency: librdmacm.so.1(RDMACM_1.0)(64bit) for package: corosynclib-1.4.1-3.el6.x86_64
		//	--> Processing Dependency: libibverbs.so.1(IBVERBS_1.0)(64bit) for package: corosynclib-1.4.1-3.el6.x86_64
		//	--> Processing Dependency: libibverbs.so.1(IBVERBS_1.1)(64bit) for package: corosynclib-1.4.1-3.el6.x86_64
		//	--> Processing Dependency: libibverbs.so.1()(64bit) for package: corosynclib-1.4.1-3.el6.x86_64
		//	--> Processing Dependency: librdmacm.so.1()(64bit) for package: corosynclib-1.4.1-3.el6.x86_64
		//	---> Package fence-agents.x86_64 0:3.1.5-9.el6 will be installed
		//	--> Processing Dependency: perl(Net::Telnet) for package: fence-agents-3.1.5-9.el6.x86_64
		//	--> Processing Dependency: /usr/bin/ipmitool for package: fence-agents-3.1.5-9.el6.x86_64
		//	--> Processing Dependency: perl-Net-Telnet for package: fence-agents-3.1.5-9.el6.x86_64
		//	--> Processing Dependency: pexpect for package: fence-agents-3.1.5-9.el6.x86_64
		//	--> Processing Dependency: python-suds for package: fence-agents-3.1.5-9.el6.x86_64
		//	--> Processing Dependency: telnet for package: fence-agents-3.1.5-9.el6.x86_64
		//	--> Processing Dependency: net-snmp-utils for package: fence-agents-3.1.5-9.el6.x86_64
		//	--> Processing Dependency: sg3_utils for package: fence-agents-3.1.5-9.el6.x86_64
		//	---> Package fence-virt.x86_64 0:0.2.3-4.el6 will be installed
		//	---> Package modcluster.x86_64 0:0.16.2-13.el6 will be installed
		//	--> Processing Dependency: oddjob for package: modcluster-0.16.2-13.el6.x86_64
		//	---> Package openais.x86_64 0:1.1.1-7.el6 will be installed
		//	---> Package openaislib.x86_64 0:1.1.1-7.el6 will be installed
		//	---> Package ricci.x86_64 0:0.16.2-42.el6 will be installed
		//	--> Processing Dependency: nss-tools for package: ricci-0.16.2-42.el6.x86_64
		//	--> Running transaction check
		//	---> Package ipmitool.x86_64 0:1.8.11-12.el6 will be installed
		//	---> Package libibverbs.x86_64 0:1.1.5-3.el6 will be installed
		//	---> Package librdmacm.x86_64 0:1.0.14.1-3.el6 will be installed
		//	---> Package net-snmp-libs.x86_64 1:5.5-37.el6 will be installed
		//	--> Processing Dependency: libsensors.so.4()(64bit) for package: 1:net-snmp-libs-5.5-37.el6.x86_64
		//	---> Package net-snmp-utils.x86_64 1:5.5-37.el6 will be installed
		//	---> Package nss-tools.x86_64 0:3.12.10-4.el6 will be installed
		//	---> Package oddjob.x86_64 0:0.30-5.el6 will be installed
		//	---> Package perl-Net-Telnet.noarch 0:3.03-11.el6 will be installed
		//	---> Package pexpect.noarch 0:2.3-6.el6 will be installed
		//	---> Package python-suds.noarch 0:0.4.1-3.el6 will be installed
		//	---> Package sg3_utils.x86_64 0:1.28-4.el6 will be installed
		//	---> Package telnet.x86_64 1:0.17-47.el6 will be installed
		//	--> Running transaction check
		//	---> Package lm_sensors-libs.x86_64 0:3.1.1-10.el6 will be installed
		//	--> Finished Dependency Resolution
		//	
		//	Dependencies Resolved
		//	
		//	================================================================================
		//	Package Arch Version Repository Size
		//	================================================================================
		//	Installing:
		//	cman x86_64 3.0.12.1-19.el6 beaker-HighAvailability 427 k
		//	Installing for dependencies:
		//	clusterlib x86_64 3.0.12.1-19.el6 beaker-HighAvailability 92 k
		//	corosync x86_64 1.4.1-3.el6 beaker-HighAvailability 185 k
		//	corosynclib x86_64 1.4.1-3.el6 beaker-HighAvailability 169 k
		//	fence-agents x86_64 3.1.5-9.el6 beaker-HighAvailability 147 k
		//	fence-virt x86_64 0.2.3-4.el6 beaker-HighAvailability 34 k
		//	ipmitool x86_64 1.8.11-12.el6 beaker-Server 323 k
		//	libibverbs x86_64 1.1.5-3.el6 beaker-Server 43 k
		//	librdmacm x86_64 1.0.14.1-3.el6 beaker-Server 26 k
		//	lm_sensors-libs x86_64 3.1.1-10.el6 beaker-Server 36 k
		//	modcluster x86_64 0.16.2-13.el6 beaker-HighAvailability 184 k
		//	net-snmp-libs x86_64 1:5.5-37.el6 beaker-Server 1.5 M
		//	net-snmp-utils x86_64 1:5.5-37.el6 beaker-Server 168 k
		//	nss-tools x86_64 3.12.10-4.el6 beaker-Server 747 k
		//	oddjob x86_64 0.30-5.el6 beaker-Server 59 k
		//	openais x86_64 1.1.1-7.el6 beaker-HighAvailability 191 k
		//	openaislib x86_64 1.1.1-7.el6 beaker-HighAvailability 81 k
		//	perl-Net-Telnet noarch 3.03-11.el6 beaker-HighAvailability 54 k
		//	pexpect noarch 2.3-6.el6 beaker-Server 146 k
		//	python-suds noarch 0.4.1-3.el6 beaker-HighAvailability 217 k
		//	ricci x86_64 0.16.2-42.el6 beaker-HighAvailability 614 k
		//	sg3_utils x86_64 1.28-4.el6 beaker-Server 470 k
		//	telnet x86_64 1:0.17-47.el6 beaker-Server 57 k
		//	
		//	Transaction Summary
		//	================================================================================
		//	Install 23 Package(s)
		//	
		//	Total download size: 5.9 M
		//	Installed size: 19 M
		//	Downloading Packages:
		//	--------------------------------------------------------------------------------
		//	Total 8.2 MB/s | 5.9 MB 00:00
		//	Running rpm_check_debug
		//	Running Transaction Test
		//	Transaction Test Succeeded
		//	Running Transaction
		//	
		//	Installing : libibverbs-1.1.5-3.el6.x86_64 1/23
		//	
		//	Installing : oddjob-0.30-5.el6.x86_64 2/23
		//	
		//	Installing : librdmacm-1.0.14.1-3.el6.x86_64 3/23
		//	
		//	Installing : fence-virt-0.2.3-4.el6.x86_64 4/23
		//	
		//	Installing : lm_sensors-libs-3.1.1-10.el6.x86_64 5/23
		//	
		//	Installing : 1:net-snmp-libs-5.5-37.el6.x86_64 6/23
		//	
		//	Installing : corosync-1.4.1-3.el6.x86_64 7/23
		//	
		//	Installing : corosynclib-1.4.1-3.el6.x86_64 8/23
		//	
		//	Installing : openais-1.1.1-7.el6.x86_64 9/23
		//	
		//	Installing : openaislib-1.1.1-7.el6.x86_64 10/23
		//	
		//	Installing : clusterlib-3.0.12.1-19.el6.x86_64 11/23
		//	
		//	Installing : modcluster-0.16.2-13.el6.x86_64 12/23
		//	
		//	Installing : 1:net-snmp-utils-5.5-37.el6.x86_64 13/23
		//	
		//	Installing : pexpect-2.3-6.el6.noarch 14/23
		//	
		//	Installing : perl-Net-Telnet-3.03-11.el6.noarch 15/23
		//	
		//	Installing : 1:telnet-0.17-47.el6.x86_64 16/23
		//	
		//	Installing : python-suds-0.4.1-3.el6.noarch 17/23
		//	
		//	Installing : nss-tools-3.12.10-4.el6.x86_64 18/23
		//	
		//	Installing : ricci-0.16.2-42.el6.x86_64 19/23
		//	
		//	Installing : sg3_utils-1.28-4.el6.x86_64 20/23
		//	
		//	Installing : ipmitool-1.8.11-12.el6.x86_64 21/23
		//	
		//	Installing : fence-agents-3.1.5-9.el6.x86_64 22/23
		//	Stopping kdump:[ OK ]
		//	Starting kdump:[ OK ]
		//	
		//	Installing : cman-3.0.12.1-19.el6.x86_64 23/23
		//	
		//	Installed:
		//	cman.x86_64 0:3.0.12.1-19.el6
		//	
		//	Dependency Installed:
		//	clusterlib.x86_64 0:3.0.12.1-19.el6 corosync.x86_64 0:1.4.1-3.el6
		//	corosynclib.x86_64 0:1.4.1-3.el6 fence-agents.x86_64 0:3.1.5-9.el6
		//	fence-virt.x86_64 0:0.2.3-4.el6 ipmitool.x86_64 0:1.8.11-12.el6
		//	libibverbs.x86_64 0:1.1.5-3.el6 librdmacm.x86_64 0:1.0.14.1-3.el6
		//	lm_sensors-libs.x86_64 0:3.1.1-10.el6 modcluster.x86_64 0:0.16.2-13.el6
		//	net-snmp-libs.x86_64 1:5.5-37.el6 net-snmp-utils.x86_64 1:5.5-37.el6
		//	nss-tools.x86_64 0:3.12.10-4.el6 oddjob.x86_64 0:0.30-5.el6
		//	openais.x86_64 0:1.1.1-7.el6 openaislib.x86_64 0:1.1.1-7.el6
		//	perl-Net-Telnet.noarch 0:3.03-11.el6 pexpect.noarch 0:2.3-6.el6
		//	python-suds.noarch 0:0.4.1-3.el6 ricci.x86_64 0:0.16.2-42.el6
		//	sg3_utils.x86_64 0:1.28-4.el6 telnet.x86_64 1:0.17-47.el6
		//	
		//	Complete!
		//	Stderr: Installed products updated.
		//	ExitCode: 0

		//	201111171056:22.839 - FINE: ssh root@jsefler-stage-6server.usersys.redhat.com echo N | yum install ricci-debuginfo.x86_64 --enablerepo=rhel-ha-for-rhel-6-server-htb-debug-rpms --disableplugin=rhnplugin  (com.redhat.qe.tools.SSHCommandRunner.run)
		//	201111171056:24.774 - FINE: Stdout: 
		//	Loaded plugins: product-id, refresh-packagekit, subscription-manager
		//	No plugin match for: rhnplugin
		//	Updating certificate-based repositories.
		//	Setting up Install Process
		//	Resolving Dependencies
		//	--> Running transaction check
		//	---> Package ricci-debuginfo.x86_64 0:0.16.2-35.el6 will be installed
		//	--> Finished Dependency Resolution
		//	
		//	Dependencies Resolved
		//	
		//	================================================================================
		//	 Package    Arch   Version       Repository                                Size
		//	================================================================================
		//	Installing:
		//	 ricci-debuginfo
		//	            x86_64 0.16.2-35.el6 rhel-ha-for-rhel-6-server-htb-debug-rpms 4.4 M
		//	
		//	Transaction Summary
		//	================================================================================
		//	Install       1 Package(s)
		//	
		//	Total download size: 4.4 M
		//	Installed size: 27 M
		//	Is this ok [y/N]: Exiting on user Command
		//	Complete!
		//	 (com.redhat.qe.tools.SSHCommandRunner.runCommandAndWait)
		//	201111171056:24.775 - FINE: Stderr:  (com.redhat.qe.tools.SSHCommandRunner.runCommandAndWait)
		//	201111171056:24.775 - FINE: ExitCode: 1 (com.redhat.qe.tools.SSHCommandRunner.runCommandAndWait)
		//	201111171056:24.775 - INFO: Asserted: 1 is present in the list [1] (com.redhat.qe.auto.testng.Assert.pass)
		//	201111171056:28.183 - FINE: ssh root@jsefler-stage-6server.usersys.redhat.com yum install ricci-debuginfo.x86_64 --enablerepo=rhel-ha-for-rhel-6-server-htb-debug-rpms --disableplugin=rhnplugin  -y (com.redhat.qe.tools.SSHCommandRunner.run)
		//	201111171056:30.752 - FINE: Stdout: 
		//	Loaded plugins: product-id, refresh-packagekit, subscription-manager
		//	No plugin match for: rhnplugin
		//	Updating certificate-based repositories.
		//	Setting up Install Process
		//	Resolving Dependencies
		//	--> Running transaction check
		//	---> Package ricci-debuginfo.x86_64 0:0.16.2-35.el6 will be installed
		//	--> Finished Dependency Resolution
		//	
		//	Dependencies Resolved
		//	
		//	================================================================================
		//	 Package    Arch   Version       Repository                                Size
		//	================================================================================
		//	Installing:
		//	 ricci-debuginfo
		//	            x86_64 0.16.2-35.el6 rhel-ha-for-rhel-6-server-htb-debug-rpms 4.4 M
		//	
		//	Transaction Summary
		//	================================================================================
		//	Install       1 Package(s)
		//	
		//	Total download size: 4.4 M
		//	Installed size: 27 M
		//	Downloading Packages:
		//	 (com.redhat.qe.tools.SSHCommandRunner.runCommandAndWait)
		//	201111171056:30.767 - FINE: Stderr: 
		//	https://cdn.redhat.com/content/htb/rhel/server/6/6Server/x86_64/highavailability/debug/Packages/ricci-debuginfo-0.16.2-35.el6.x86_64.rpm: [Errno 14] PYCURL ERROR 22 - "The requested URL returned error: 404"
		//	Trying other mirror.
		//	
		//	
		//	Error Downloading Packages:
		//	  ricci-debuginfo-0.16.2-35.el6.x86_64: failure: Packages/ricci-debuginfo-0.16.2-35.el6.x86_64.rpm from rhel-ha-for-rhel-6-server-htb-debug-rpms: [Errno 256] No more mirrors to try.
		//	
		//	 (com.redhat.qe.tools.SSHCommandRunner.runCommandAndWait)
		//	201111171056:30.775 - FINE: ExitCode: 1 (com.redhat.qe.tools.SSHCommandRunner.runCommandAndWait)

		// check if the package was obsoleted:
		// Package cairo-spice-debuginfo is obsoleted by spice-server, trying to install spice-server-0.7.3-2.el6.x86_64 instead
		String regex="Package "+pkg.split("\\.")[0]+".* is obsoleted by (.+), trying to install .+ instead";
		Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
		Matcher matcher = pattern.matcher(sshCommandRunner.getStdout());
		String obsoletedByPkg = null;
		if (matcher.find()) {
			obsoletedByPkg = matcher.group(1);
			log.warning("Package '"+pkg+"' was obsoleted by '"+obsoletedByPkg+"'. The replacement package may NOT get installed from repository '"+repoLabel+"'.");
			pkg = obsoletedByPkg;
		}
		
		// FIXME, If the package is obsoleted, then the obsoletedByPkg may not come from the same repo and the following assert will fail
		
		// assert the installed package came from repoLabel
		//	spice-server     x86_64     0.7.3-2.el6      rhel-6-server-beta-rpms     245 k
		//  cman x86_64 3.0.12.1-19.el6 beaker-HighAvailability 427 k
		if (repoLabel==null) {
			regex=pkg.split("\\.")[0]+"\\n? +(\\w+) +([\\w\\.-]+) +([\\w-]+)";		
		} else {
			regex=pkg.split("\\.")[0]+"\\n? +(\\w+) +([\\w\\.-]+) +("+repoLabel+")";
		}
		pattern = Pattern.compile(regex, Pattern.MULTILINE);
		matcher = pattern.matcher(sshCommandRunner.getStdout());
		Assert.assertTrue(matcher.find(), "Package '"+pkg+"' appears to have been installed"+(repoLabel==null?"":" from repository '"+repoLabel+"'")+".");
		String arch = matcher.group(1);
		String version = matcher.group(2);
		String repo = matcher.group(3);
		

		// finally assert that the package is actually installed
		//
		// RHEL 5...
		//	201106061840:40.270 - FINE: ssh root@jsefler-stage-5server.usersys.redhat.com yum list installed GConf2-debuginfo.x86_64 --disableplugin=rhnplugin (com.redhat.qe.tools.SSHCommandRunner.run)
		//	201106061840:41.529 - FINE: Stdout: 
		//	Loaded plugins: product-id, security, subscription-manager
		//	No plugin match for: rhnplugin
		//	Updating Red Hat repositories.
		//	Installed Packages
		//	GConf2-debuginfo.x86_64                  2.14.0-9.el5                  installed
		//	 (com.redhat.qe.tools.SSHCommandRunner.runCommandAndWait)
		//	201106061840:41.530 - FINE: Stderr:  (com.redhat.qe.tools.SSHCommandRunner.runCommandAndWait)
		//	201106061840:41.530 - FINE: ExitCode: 0 (com.redhat.qe.tools.SSHCommandRunner.runCommandAndWait)
		//
		// RHEL 6...
		//	201104051839:15.836 - FINE: ssh root@jsefler-betastage-server.usersys.redhat.com yum list installed spice-server --disableplugin=rhnplugin (com.redhat.qe.tools.SSHCommandRunner.run)
		//	201104051839:16.447 - FINE: Stdout: 
		//	Loaded plugins: product-id, refresh-packagekit, subscription-manager
		//	No plugin match for: rhnplugin
		//	Updating Red Hat repositories.
		//	Installed Packages
		//	spice-server.x86_64             0.7.3-2.el6             @rhel-6-server-beta-rpms
		//	 (com.redhat.qe.tools.SSHCommandRunner.runCommandAndWait)
		//	201104051839:16.453 - FINE: Stderr: INFO:rhsm-app.repolib:repos updated: 63	 (com.redhat.qe.tools.SSHCommandRunner.runCommandAndWait)
		//	201104051839:16.455 - FINE: ExitCode: 0 (com.redhat.qe.tools.SSHCommandRunner.runCommandAndWait)
		RemoteFileTasks.runCommandAndAssert(sshCommandRunner,"yum list installed "+pkg+" --disableplugin=rhnplugin", 0, "^"+pkg.split("\\.")[0]+"."+arch+" +"+version+" +(installed|@"+repo+")$",null);
		
		return result;
	}
	
	/**
	 * yum -y install pkg<br>
	 * Assert the install is Complete! and pkg is installed.
	 * @param pkg
	 * @return
	 */
	public SSHCommandResult yumInstallPackage (String pkg) {
		return yumInstallPackageFromRepo(pkg,null,null);
	}
	
	/**
	 * yum -y remove pkg<br>
	 * Assert the removal is Complete! and no longer installed.
	 * @param pkg
	 * @return
	 */
	public SSHCommandResult yumRemovePackage (String pkg) {
		String command = "yum -y remove "+pkg+" --disableplugin=rhnplugin"; // --disableplugin=rhnplugin helps avoid: up2date_client.up2dateErrors.AbuseError
		SSHCommandResult result = RemoteFileTasks.runCommandAndAssert(sshCommandRunner,command, 0, "^Complete!$",null);
		RemoteFileTasks.runCommandAndAssert(sshCommandRunner,"yum list installed "+pkg+" --disableplugin=rhnplugin", 1, null,"Error: No matching Packages to list");
		return result;
	}
	
	public SSHCommandResult yumInstallGroup (String group) {
		String command = "yum -y groupinstall \""+group+"\" --disableplugin=rhnplugin"; // --disableplugin=rhnplugin helps avoid: up2date_client.up2dateErrors.AbuseError
		SSHCommandResult result = RemoteFileTasks.runCommandAndAssert(sshCommandRunner,command, 0, "^Complete!$",null);
		Assert.assertFalse(this.yumGroupList("Available", ""/*"--disablerepo=* --enablerepo="+repo*/).contains(group),"Yum group is Available after calling '"+command+"'.");
		return result;
	}
	
	public SSHCommandResult yumRemoveGroup (String group) {
		String command = "yum -y groupremove \""+group+"\" --disableplugin=rhnplugin"; // --disableplugin=rhnplugin helps avoid: up2date_client.up2dateErrors.AbuseError
		SSHCommandResult result = RemoteFileTasks.runCommandAndAssert(sshCommandRunner,command, 0, "^Complete!$",null);
		Assert.assertFalse(this.yumGroupList("Installed", ""/*"--disablerepo=* --enablerepo="+repo*/).contains(group),"Yum group is Installed after calling '"+command+"'.");
		return result;
	}
	
	public SSHCommandResult yumClean (String option) {
		String command = "yum clean \""+option+"\" --disableplugin=rhnplugin"; // --disableplugin=rhnplugin helps avoid: up2date_client.up2dateErrors.AbuseError
		SSHCommandResult result = RemoteFileTasks.runCommandAndAssert(sshCommandRunner,command, 0, "^Cleaning",null);
		return result;
	}
	
	public String getRedhatRelease() {
//		// verify the grinder hostname is a rhel 5 machine
//		log.info("Verifying prerequisite...  hostname '"+grinderHostname+"' is a Red Hat Enterprise Linux .* release 5 machine.");
//		Assert.assertEquals(sshCommandRunner.runCommandAndWait("cat /etc/redhat-release | grep -E \"^Red Hat Enterprise Linux .* release 5.*\"").getExitCode(),Integer.valueOf(0),"Grinder hostname must be RHEL 5.*");
		return sshCommandRunner.runCommandAndWait("cat /etc/redhat-release").getStdout();
	}
	
	/**
	 * @param key - e.g. "REGISTERED_TO_OTHER_WARNING"
	 * @return the branding string value for the key
	 */
	public String getBrandingString(String key) {

		// view /usr/share/rhsm/subscription_manager/branding/__init__.py and search for "self." to find branding message strings e.g. "REGISTERED_TO_OTHER_WARNING"
		return sshCommandRunner.runCommandAndWait("cd "+brandingDir+"; python -c \"import __init__ as sm;brand=sm.Branding(sm.get_branding());print brand."+key+"\"").getStdout();
	}
	
	
	
	
	/**
	 * Call rhnreg_ks and assert the existence of a systemid file afterwards.
	 * @param rhnUsername
	 * @param rhnPassword
	 * @param rhnHostname
	 * @return the rhn system_id value from the contents of the systemid file
	 */
	public String registerToRhnClassic(String rhnUsername, String rhnPassword, String rhnHostname) {
		
		// register to RHN Classic
		// [root@jsefler-onprem-5server ~]# rhnreg_ks --serverUrl=https://xmlrpc.rhn.code.stage.redhat.com/XMLRPC --username=qa@redhat.com --password=CHANGE-ME --force --norhnsd --nohardware --nopackages --novirtinfo
		//	ERROR: refreshing remote package list for System Profile
		String command = String.format("rhnreg_ks --serverUrl=https://xmlrpc.%s/XMLRPC --username=%s --password=%s --profilename=%s --force --norhnsd --nohardware --nopackages --novirtinfo", rhnHostname, rhnUsername, rhnPassword, "rhsm-automation."+hostname);
		SSHCommandResult result = sshCommandRunner.runCommandAndWait(command);
		
		// assert result
		Integer exitCode = result.getExitCode();
		String stdout = result.getStdout();
		String stderr = result.getStderr();
		if (stdout.contains("ERROR: refreshing remote package list for System Profile")) {
			// ERROR: refreshing remote package list for System Profile
			log.warning("Ignoring stdout result: "+"ERROR: refreshing remote package list for System Profile");
			stdout = stdout.replaceAll("ERROR: refreshing remote package list for System Profile", "");
		}
		if (stderr.contains("forced skip_if_unavailable=True due to")) {
			// Repo content-label-72 forced skip_if_unavailable=True due to: /etc/pki/entitlement/2114809071147763952.pem
			String regex = "Repo .+ forced skip_if_unavailable=True due to: .+.pem";
			log.warning("Ignoring stderr results matching: "+regex);
			stderr = stderr.replaceAll(regex, "");
		}
		
		Assert.assertEquals(exitCode, new Integer(0),"Exitcode from attempt to register to RHN Classic.");
		Assert.assertEquals(stderr.trim(), "","Stderr from attempt to register to RHN Classic.");
		Assert.assertEquals(stdout.trim(), "","Stdout from attempt to register to RHN Classic.");
		
		// assert existance of system id file
		Assert.assertTrue(RemoteFileTasks.testExists(sshCommandRunner, rhnSystemIdFile),"The system id file '"+rhnSystemIdFile+"' exists.  This indicates this system is registered using RHN Classic.");
		
		// get the value of the systemid
		// [root@jsefler-onprem-5server rhn]# grep ID- /etc/sysconfig/rhn/systemid
		// <value><string>ID-1021538137</string></value>
		command = String.format("grep ID- %s", rhnSystemIdFile);
		return sshCommandRunner.runCommandAndWait(command).getStdout().trim().replaceAll("\\<.*?\\>", "").replaceFirst("ID-", "");		// return 1021538137
	}




	public boolean isRhnSystemIdRegistered(String rhnUsername, String rhnPassword,String rhnHostname, String systemId) {
		String command = String.format("rhn-is-registered.py --username=%s --password=%s --server=%s  %s", rhnUsername, rhnPassword, rhnHostname, systemId);
		SSHCommandResult result = sshCommandRunner.runCommandAndWait(command);
		return Boolean.valueOf(result.getStdout().trim());
	}
	
	// protected methods ************************************************************

	protected boolean poolsNoLongerAvailable(ArrayList<SubscriptionPool> beforeSubscription, ArrayList<SubscriptionPool> afterSubscription) {
		for(SubscriptionPool beforePool:beforeSubscription)
			if (afterSubscription.contains(beforePool))
				return false;
		return true;
	}
	
	/**
	 * This command is very useful to run an rhsm cli command in a specific language.<p>
	 * It is also very useful to run an rhsm cli command in the native local (with lang=nul)
	 * when the normal sshCommandRunner encounters:<br>
	 * Stderr: 'ascii' codec can't decode byte 0xe2 in position 55: ordinal not in range(128)
	 * @param lang
	 * @param rhsmCommand
	 * @return
	 */
	public SSHCommandResult runCommandWithLang(String lang, String rhsmCommand){
		if (lang==null) {
			lang="";
		} else {
			if (!lang.toUpperCase().contains(".UTF")) lang=lang+".UTF-8";	// append ".UTF-8" when not already there
			lang="LANG="+lang;
		}
		String command = lang+" "+rhsmCommand;
		command = "PYTHONIOENCODING=ascii "+command;	// THIS WORKAROUND IS NEEDED AFTER master commit 056e69dc833919709bbf23d8a7b73a5345f77fdf RHEL6.4 commit 1bc25596afaf294cd217200c605737a43112a378 for bug 800323
		return sshCommandRunner.runCommandAndWait(command);
	}
	public SSHCommandResult runCommandWithLangAndAssert(String lang, String rhsmCommand, Integer exitCode, String stdoutRegex, String stderrRegex){
		List<String> stdoutRegexs = null;
		if (stdoutRegex!=null) {
			stdoutRegexs = new ArrayList<String>();	stdoutRegexs.add(stdoutRegex);
		}
		List<String> stderrRegexs = null;
		if (stderrRegex!=null) {
			stderrRegexs = new ArrayList<String>();	stderrRegexs.add(stderrRegex);
		}
		return runCommandWithLangAndAssert(lang, rhsmCommand, exitCode, stdoutRegexs, stderrRegexs);
	}
	public SSHCommandResult runCommandWithLangAndAssert(String lang, String rhsmCommand, Integer exitCode, List<String> stdoutRegexs, List<String> stderrRegexs){
		if (lang==null) {
			lang="";
		} else {
			if (!lang.toUpperCase().contains(".UTF")) lang=lang+".UTF-8";	// append ".UTF-8" when not already there
			lang="LANG="+lang;
		}
		String command = lang+" "+rhsmCommand;
		command = "PYTHONIOENCODING=ascii "+command;	// THIS WORKAROUND IS NEEDED AFTER master commit 056e69dc833919709bbf23d8a7b73a5345f77fdf RHEL6.4 commit 1bc25596afaf294cd217200c605737a43112a378 for bug 800323
		return RemoteFileTasks.runCommandAndAssert(sshCommandRunner, command, exitCode, stdoutRegexs, stderrRegexs);
	}
	
	public void setLanguage(String lang){
		sshCommandRunner.runCommandAndWait("export LANG="+lang);
	}
	
	protected void workaroundForBug844455() {
		// TEMPORARY WORKAROUND FOR BUG
		List<File> entitlementFiles = getCurrentEntitlementCertFiles();
		int tooManyEntitlements = 30;
		if (entitlementFiles.size()>tooManyEntitlements) { 
			boolean invokeWorkaroundWhileBugIsOpen = true;
			String bugId="844455";	// Bug 844455 - when consuming many entitlements, subscription-manager unsubscribe --all throws SSLTimeoutError: timed out
			try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
			if (invokeWorkaroundWhileBugIsOpen) {
				log.warning("The workaround to avoid an SSLTimeoutError during an unregister or unsubscribe --all is to incrementally unsubscribe reducing the current entitlements to approximately "+tooManyEntitlements+".  Then resume the unregister or unsubscribe --all.");
				for (int i=entitlementFiles.size()-1; i>=tooManyEntitlements; i--) {
					unsubscribe_(null, getSerialNumberFromEntitlementCertFile(entitlementFiles.get(i)), null,null,null);
				}
			}
		}
		// END OF WORKAROUND
	}
	
	public JSONArray workaroundForBug844455(JSONArray jsonPools) {
		// TEMPORARY WORKAROUND FOR BUG
		int tooManyPools = 30;
		if (jsonPools.length()>tooManyPools) { 
			boolean invokeWorkaroundWhileBugIsOpen = true;
			String bugId="844455";	// Bug 844455 - when consuming many entitlements, subscription-manager unsubscribe --all throws SSLTimeoutError: timed out
			try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
			if (invokeWorkaroundWhileBugIsOpen) {
				log.warning("The workaround is to reducing the number of multiple pools approximately "+tooManyPools+".  Then resume the unregister or unsubscribe --all.");
				for (int i=jsonPools.length()-1; i>=30; i--) {
					jsonPools.remove(i);
				}
			}
		}
		// END OF WORKAROUND
		return jsonPools;
	}
}

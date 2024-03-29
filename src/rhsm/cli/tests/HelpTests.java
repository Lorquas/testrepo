package rhsm.cli.tests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.xmlrpc.XmlRpcException;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.redhat.qe.Assert;
import com.redhat.qe.auto.bugzilla.BlockedByBzBug;
import com.redhat.qe.auto.bugzilla.BzChecker;
import com.redhat.qe.auto.tcms.ImplementsNitrateTest;
import com.redhat.qe.auto.testng.TestNGUtils;
import rhsm.base.SubscriptionManagerCLITestScript;
import com.redhat.qe.tools.RemoteFileTasks;
import com.redhat.qe.tools.SSHCommandResult;

/**
 * @author jsefler
 *
 */
@Test(groups={"HelpTests"})
public class HelpTests extends SubscriptionManagerCLITestScript{
	
	
	// Test Methods ***********************************************************************
	
	@Test(	description="subscription-manager-cli: man page",
			groups={},
			enabled=true)
	@ImplementsNitrateTest(caseId=41697)
	public void ManPageExistanceForCLI_Test() {
		if (clienttasks==null) throw new SkipException("A client connection is needed for this test.");
		String cliCommand = clienttasks.command;
		RemoteFileTasks.runCommandAndAssert(client,"man -P cat "+cliCommand,0);
		RemoteFileTasks.runCommandAndAssert(client,"whatis "+cliCommand,0,"^"+cliCommand+" ",null);
		log.warning("We only tested the existence of the man page; NOT the content.");
	}
	
	@Test(	description="subscription-manager-gui: man page",
			groups={},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void ManPageExistanceForGUI_Test() {
		if (clienttasks==null) throw new SkipException("A client connection is needed for this test.");
		String guiCommand = clienttasks.command+"-gui";
		// is the guiCommand installed?
		if (client.runCommandAndWait("rpm -q "+guiCommand).getStdout().contains("is not installed")) {
			RemoteFileTasks.runCommandAndAssert(client,"man -P cat "+guiCommand,1,null,"^No manual entry for "+guiCommand);
			RemoteFileTasks.runCommandAndAssert(client,"whatis "+guiCommand,0,"^"+guiCommand+": nothing appropriate",null);
			log.warning("In this test we only assert the existence of the man page; NOT the contents!");
			throw new SkipException(guiCommand+" is not installed and therefore its man page is also not installed.");
		} else {
			RemoteFileTasks.runCommandAndAssert(client,"man -P cat "+guiCommand,0);
			RemoteFileTasks.runCommandAndAssert(client,"whatis "+guiCommand,0,"^"+guiCommand+" ",null);
			log.warning("In this test we only assert the existence of the man page; NOT the contents!");
		}
	}
	
	@Test(	description="rhsm-icon: man page",
			groups={"blockedByBug-771726"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void ManPageExistanceForRhsmIcon_Test() {
		if (clienttasks==null) throw new SkipException("A client connection is needed for this test.");
		String command = "rhsm-icon"; //iconCommand = "rhsm-compliance-icon"; // prior to bug 771726
		// is the command installed?
		if (client.runCommandAndWait("rpm -q "+clienttasks.command+"-gui").getStdout().contains("is not installed")) {
			RemoteFileTasks.runCommandAndAssert(client,"man -P cat "+command,1,null,"^No manual entry for "+command);
			RemoteFileTasks.runCommandAndAssert(client,"whatis "+command,0,"^"+command+": nothing appropriate",null);
			log.warning("In this test we only assert the existence of the man page; NOT the contents!");
			throw new SkipException(command+" is not installed and therefore its man page is also not installed.");
		} else {
			RemoteFileTasks.runCommandAndAssert(client,"man -P cat "+command,0);
			RemoteFileTasks.runCommandAndAssert(client,"whatis "+command,0,"^"+command+" ",null);
			log.warning("In this test we only assert the existence of the man page; NOT the contents!");
		}
	}
	
	@Test(	description="install-num-migrate-to-rhsm: man page",
			groups={},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void ManPageExistanceForInstallNumMigrateToRhsm_Test() {
		if (clienttasks==null) throw new SkipException("A client connection is needed for this test.");
		String command = MigrationTests.installNumTool;
		// is the command installed?
		if (client.runCommandAndWait("rpm -q "+clienttasks.command+"-migration").getStdout().contains("is not installed")) {
			RemoteFileTasks.runCommandAndAssert(client,"man -P cat "+command,1,null,"^No manual entry for "+command);
			RemoteFileTasks.runCommandAndAssert(client,"whatis "+command,0,"^"+command+": nothing appropriate",null);
			log.warning("In this test we only assert the existence of the man page; NOT the contents!");
			throw new SkipException(command+" is not installed and therefore its man page cannot be installed.");
		} else if (!clienttasks.redhatReleaseX.equals("5")) {
			log.info("The man page for '"+command+"' should only be installed on RHEL5.");
			RemoteFileTasks.runCommandAndAssert(client,"man -P cat "+command,1,null,"^No manual entry for "+command);
			RemoteFileTasks.runCommandAndAssert(client,"whatis "+command,0,"^"+command+": nothing appropriate",null);
			throw new SkipException("The migration tool '"+command+"' and its man page is only applicable on RHEL5.");
		} else {
			RemoteFileTasks.runCommandAndAssert(client,"man -P cat "+command,0);
			RemoteFileTasks.runCommandAndAssert(client,"whatis "+command,0,"^"+command+" ",null);
			log.warning("In this test we only assert the existence of the man page; NOT the contents!");
		}
	}
	
	@Test(	description="rhn-migrate-classic-to-rhsm: man page",
			groups={},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void ManPagevForRhnMigrateClassicToRhsm_Test() {
		if (clienttasks==null) throw new SkipException("A client connection is needed for this test.");
		String command = MigrationTests.rhnMigrateTool;
		// is the command installed?
		if (client.runCommandAndWait("rpm -q "+clienttasks.command+"-migration").getStdout().contains("is not installed")) {
			RemoteFileTasks.runCommandAndAssert(client,"man -P cat "+command,1,null,"^No manual entry for "+command);
			RemoteFileTasks.runCommandAndAssert(client,"whatis "+command,0,"^"+command+": nothing appropriate",null);
			log.warning("In this test we only assert the existence of the man page; NOT the contents!");
			throw new SkipException(command+" is not installed and therefore its man page is also not installed.");
		} else {
			RemoteFileTasks.runCommandAndAssert(client,"man -P cat "+command,0);
			RemoteFileTasks.runCommandAndAssert(client,"whatis "+command,0,"^"+command+" ",null);
			log.warning("In this test we only assert the existence of the man page; NOT the contents!");
		}
	}
	
	@Test(	description="rct: man page",
			groups={"blockedByBug-862909"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void ManPageExistanceForRCT_Test() {
		if (clienttasks==null) throw new SkipException("A client connection is needed for this test.");
		String rctCommand = "rct";
		RemoteFileTasks.runCommandAndAssert(client,"man -P cat "+rctCommand,0);
		RemoteFileTasks.runCommandAndAssert(client,"whatis "+rctCommand,0,"^"+rctCommand+" ",null);
		log.warning("We only tested the existence of the man page; NOT the content.");
	}
	
	
	
	
	@Test(	description="subscription-manager-cli: assert only expected command line options are available",
			groups={},
			dataProvider="ExpectedCommandLineOptionsData")
	@ImplementsNitrateTest(caseId=46713)
	//@ImplementsNitrateTest(caseId=46707)
	public void ExpectedCommandLineOptions_Test(Object meta, String command, String stdoutRegex, List<String> expectedOptions) {
		log.info("Testing subscription-manager-cli command line options '"+command+"' and verifying that only the expected options are available.");
		SSHCommandResult result = RemoteFileTasks.runCommandAndAssert(client,command,0);
		
		Pattern pattern = Pattern.compile(stdoutRegex, Pattern.MULTILINE);
		Matcher matcher = pattern.matcher(result.getStdout());
		Assert.assertTrue(matcher.find(),"Available command line options matching regex '"+stdoutRegex+"' are shown with command: "+command);
		
		// find all the matches to stderrRegex
		List <String> actualOptions = new ArrayList<String>();
		do {
			
			// TEMPORARY WORKAROUND FOR BUG
			if (!command.contains(" register") && !command.contains(" config") && (matcher.group().contains("--serverurl")||matcher.group().contains("--baseurl"))) {
				boolean invokeWorkaroundWhileBugIsOpen = true;
				String bugId="842768"; 
				try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
				if (invokeWorkaroundWhileBugIsOpen) {
					log.warning("Ignoring the presence of option '"+matcher.group()+"' for command '"+command+"' while bug '"+bugId+"' is open.");
					continue;
				}
			}
			// END OF WORKAROUND
			
			actualOptions.add(matcher.group().trim());
		} while (matcher.find());
		
		// assert all of the expectedOptions were found and that no unexpectedOptions were found
		for (String expectedOption : expectedOptions) {
			if (!actualOptions.contains(expectedOption)) {
				log.warning("Could not find the expected command '"+command+"' option '"+expectedOption+"'.");
			} else {
				Assert.assertTrue(actualOptions.contains(expectedOption),"The expected command '"+command+"' option '"+expectedOption+"' is available.");
			}
		}
		for (String actualOption : actualOptions) {
			if (!expectedOptions.contains(actualOption))
				log.warning("Found an unexpected command '"+command+"' option '"+actualOption+"'.");
		}
		Assert.assertTrue(actualOptions.containsAll(expectedOptions), "All of the expected command '"+command+"' line options are available.");
		Assert.assertTrue(expectedOptions.containsAll(actualOptions), "All of the available command '"+command+"' line options are expected.");
	}
	
	

	
	
	// Candidates for an automated Test:
	// TODO Bug 694662 - the whitespace in the title line of man subscription-manager-gui is completely consumed
	// TODO Bug 765905 - add man pages for subscription-manager-migration
	// TODO Bug 812104 - Unable to tab complete new subscription-manager modules (release and service-level)
	// TODO Bug 817117 - bash-completion of subscription-manager environments --<TAB><TAB> is not working
	// TODO Bug 817390 - bash-completion of subscription-manager subscribe --<TAB><TAB> is not finding --servicelevel option
	
	
	// Configuration Methods ***********************************************************************
	
	@BeforeClass(groups={"setup"})
	public void makewhatisBeforeClass() {
		if (clienttasks==null) return;
		// running makewhatis (when needed) to ensure that the whatis database is built on Beaker provisioned systems
		if (client.runCommandAndWait("grep "+clienttasks.command+" /var/cache/man/whatis").getStdout().trim().isEmpty()) {
			RemoteFileTasks.runCommandAndAssert(client,"makewhatis",0);
		}
	}
	
	
	// Protected Methods ***********************************************************************

	
	// Data Providers ***********************************************************************
	
	@DataProvider(name="ExpectedCommandLineOptionsData")
	public Object[][] getExpectedCommandLineOptionsDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getExpectedCommandLineOptionsDataAsListOfLists());
	}
	protected List<List<Object>> getExpectedCommandLineOptionsDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		if (clienttasks==null) return ll;
		
		// String command, String stdoutRegex, List<String> expectedOptions
		String command;
		List<String> modules = new ArrayList<String>();
		List<String> options = new ArrayList<String>();
		String module;
		String modulesRegex = "^	[\\w-]+";
		       modulesRegex = "^  [\\w-]+";	// valid after bug 848095
		String optionsRegex = "^  --[\\w\\.]+(=[\\w\\.]+)*|^  -\\w(=\\w+)*, --\\w+(=\\w+)*";
		       optionsRegex = "^  --[\\w\\.-]+(=[\\w\\.-]+)*|^  -[\\?\\w]( \\w+)*, --[\\w\\.-]+(=\\w+)*";
		
		// EXAMPLES FOR optionsRegex
		//  -h, --help            show this help message and exit
		//  --list                list the configuration for this system
		//  --remove=REMOVE       remove configuration entry by section.name
		//  --server.hostname=SERVER.HOSTNAME
		//  -?, --help                  Show help options
		//  --help-all                  Show all help options
		//  -f, --force-icon=TYPE       Force display of the icon (expired, partial or warning)
		//  -c, --check-period          How often to check for validity (in seconds)
		//  -i INSTNUMBER, --instnumber=INSTNUMBER
		
		// ========================================================================================
		// subscription-manager MODULES
		modules.clear();
		modules.add("config");
		modules.add("import");
		modules.add("redeem");
		modules.add("orgs");
		modules.add("repos");
		modules.add("clean");
		modules.add("environments");
		modules.add("facts");
		modules.add("identity");
		modules.add("list");
		modules.add("refresh");
		modules.add("register");
		modules.add("subscribe");
		modules.add("unregister");
		modules.add("unsubscribe");
		modules.add("service-level");
		modules.add("release");
		modules.add("version");
		modules.add("attach");	// added by bug 874804
		modules.add("remove");	// added by bug 874749
		for (String smHelpCommand : new String[]{clienttasks.command+" -h",clienttasks.command+" --help"}) {
			List <String> usages = new ArrayList<String>();
			String usage = String.format("Usage: %s [options] MODULENAME --help",clienttasks.command);	// prior to Bug 796730 - subscription-manager usage statement
			usage = String.format("Usage: %s MODULE-NAME [MODULE-OPTIONS] [--help]",clienttasks.command);
			usages.add(usage);
			ll.add(Arrays.asList(new Object[] {new BlockedByBzBug("796730"), smHelpCommand, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\?", "\\\\?")+" *$", usages}));
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, modulesRegex, new ArrayList<String>(modules)}));
		}
		
		
		// subscription-manager config OPTIONS
		module = "config";
		options.clear();
		options.add("-h, --help");
		options.add("--list");
		options.add("--remove=REMOVE");
		options.add("--server.ca_cert_dir=SERVER.CA_CERT_DIR");
		options.add("--server.hostname=SERVER.HOSTNAME");
		options.add("--server.insecure=SERVER.INSECURE");
		options.add("--server.port=SERVER.PORT");
		options.add("--server.prefix=SERVER.PREFIX");
		options.add("--server.proxy_hostname=SERVER.PROXY_HOSTNAME");
		options.add("--server.proxy_password=SERVER.PROXY_PASSWORD");
		options.add("--server.proxy_port=SERVER.PROXY_PORT");
		options.add("--server.proxy_user=SERVER.PROXY_USER");
		options.add("--server.repo_ca_cert=SERVER.REPO_CA_CERT");
		options.add("--server.ssl_verify_depth=SERVER.SSL_VERIFY_DEPTH");
		options.add("--rhsm.baseurl=RHSM.BASEURL");
		options.add("--rhsm.ca_cert_dir=RHSM.CA_CERT_DIR");
		options.add("--rhsm.consumercertdir=RHSM.CONSUMERCERTDIR");
		options.add("--rhsm.entitlementcertdir=RHSM.ENTITLEMENTCERTDIR");
		options.add("--rhsm.hostname=RHSM.HOSTNAME");
		options.add("--rhsm.insecure=RHSM.INSECURE");
		options.add("--rhsm.port=RHSM.PORT");
		options.add("--rhsm.prefix=RHSM.PREFIX");
		options.add("--rhsm.productcertdir=RHSM.PRODUCTCERTDIR");
		options.add("--rhsm.proxy_hostname=RHSM.PROXY_HOSTNAME");
		options.add("--rhsm.proxy_password=RHSM.PROXY_PASSWORD");
		options.add("--rhsm.proxy_port=RHSM.PROXY_PORT");
		options.add("--rhsm.proxy_user=RHSM.PROXY_USER");
		options.add("--rhsm.repo_ca_cert=RHSM.REPO_CA_CERT");
		options.add("--rhsm.manage_repos=RHSM.MANAGE_REPOS");	// Bug 797996 - new configuration for rhsm.manage_repos should be exposed
		options.add("--rhsm.ssl_verify_depth=RHSM.SSL_VERIFY_DEPTH");
		options.add("--rhsmcertd.ca_cert_dir=RHSMCERTD.CA_CERT_DIR");
		options.add("--rhsmcertd.certfrequency=RHSMCERTD.CERTFREQUENCY");
		options.add("--rhsmcertd.healfrequency=RHSMCERTD.HEALFREQUENCY");
		options.add("--rhsmcertd.hostname=RHSMCERTD.HOSTNAME");
		options.add("--rhsmcertd.insecure=RHSMCERTD.INSECURE");
		options.add("--rhsmcertd.port=RHSMCERTD.PORT");
		options.add("--rhsmcertd.prefix=RHSMCERTD.PREFIX");
		options.add("--rhsmcertd.proxy_hostname=RHSMCERTD.PROXY_HOSTNAME");
		options.add("--rhsmcertd.proxy_password=RHSMCERTD.PROXY_PASSWORD");
		options.add("--rhsmcertd.proxy_port=RHSMCERTD.PROXY_PORT");
		options.add("--rhsmcertd.proxy_user=RHSMCERTD.PROXY_USER");
		options.add("--rhsmcertd.repo_ca_cert=RHSMCERTD.REPO_CA_CERT");
		options.add("--rhsmcertd.ssl_verify_depth=RHSMCERTD.SSL_VERIFY_DEPTH");
		// after bug 807721, more config options are available
		options.add("--server.certfrequency=SERVER.CERTFREQUENCY");
		options.add("--server.manage_repos=SERVER.MANAGE_REPOS");
		options.add("--server.entitlementcertdir=SERVER.ENTITLEMENTCERTDIR");
		options.add("--server.baseurl=SERVER.BASEURL");
		options.add("--server.productcertdir=SERVER.PRODUCTCERTDIR");
		options.add("--server.consumercertdir=SERVER.CONSUMERCERTDIR");
		options.add("--server.healfrequency=SERVER.HEALFREQUENCY");
		options.add("--rhsm.certfrequency=RHSM.CERTFREQUENCY");
		options.add("--rhsm.healfrequency=RHSM.HEALFREQUENCY");
		options.add("--rhsmcertd.manage_repos=RHSMCERTD.MANAGE_REPOS");
		options.add("--rhsmcertd.entitlementcertdir=RHSMCERTD.ENTITLEMENTCERTDIR");
		options.add("--rhsmcertd.baseurl=RHSMCERTD.BASEURL");
		options.add("--rhsmcertd.productcertdir=RHSMCERTD.PRODUCTCERTDIR");
		options.add("--rhsmcertd.consumercertdir=RHSMCERTD.CONSUMERCERTDIR");
		for (String smHelpCommand : new String[]{clienttasks.command+" -h "+module,clienttasks.command+" --help "+module}) {
			List <String> usages = new ArrayList<String>();
			String usage = String.format("Usage: %s %s [OPTIONS]",clienttasks.command,module);
			//if (clienttasks.redhatRelease.contains("release 5")) usage = usage.replaceFirst("^Usage", "usage"); // TOLERATE WORKAROUND FOR Bug 693527 ON RHEL5
			usages.add(usage);
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\?", "\\\\?")+" *$", usages}));
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, optionsRegex, new ArrayList<String>(options)}));
		}
		
		// subscription-manager import OPTIONS
		module = "import";
		options.clear();
		options.add("-h, --help");
		//options("--certificate=CERTIFICATE_FILES");	// prior to fix for Bug 735212
		options.add("--certificate=CERTIFICATE_FILE");
		// TEMPORARY WORKAROUND FOR BUG: https://bugzilla.redhat.com/show_bug.cgi?id=733873
		boolean invokeWorkaroundWhileBugIsOpen = true;
		String bugId="733873"; 
		try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			options.add("--proxy=PROXY_URL");
			options.add("--proxyuser=PROXY_USER");
			options.add("--proxypassword=PROXY_PASSWORD");
		}
		// END OF WORKAROUND
		for (String smHelpCommand : new String[]{clienttasks.command+" -h "+module,clienttasks.command+" --help "+module}) {
			List <String> usages = new ArrayList<String>();
			String usage = String.format("Usage: %s %s [OPTIONS]",clienttasks.command,module);
			//if (clienttasks.redhatRelease.contains("release 5")) usage = usage.replaceFirst("^Usage", "usage"); // TOLERATE WORKAROUND FOR Bug 693527 ON RHEL5
			usages.add(usage);
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\?", "\\\\?")+" *$", usages}));
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, optionsRegex, new ArrayList<String>(options)}));
		}

		// subscription-manager redeem OPTIONS
		module = "redeem";
		options.clear();
		options.add("-h, --help");
		options.add("--email=EMAIL");
		options.add("--locale=LOCALE");
		options.add("--proxy=PROXY_URL");
		options.add("--proxyuser=PROXY_USER");
		options.add("--proxypassword=PROXY_PASSWORD");
		for (String smHelpCommand : new String[]{clienttasks.command+" -h "+module,clienttasks.command+" --help "+module}) {
			List <String> usages = new ArrayList<String>();
			String usage = String.format("Usage: %s %s [OPTIONS]",clienttasks.command,module);
			//if (clienttasks.redhatRelease.contains("release 5")) usage = usage.replaceFirst("^Usage", "usage"); // TOLERATE WORKAROUND FOR Bug 693527 ON RHEL5
			usages.add(usage);
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\?", "\\\\?")+" *$", usages}));
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, optionsRegex, new ArrayList<String>(options)}));
		}
		
		// subscription-manager orgs OPTIONS
		module = "orgs";
		options.clear();
		options.add("-h, --help");
		options.add("--serverurl=SERVER_URL");
		options.add("--username=USERNAME");
		options.add("--password=PASSWORD");
		options.add("--proxy=PROXY_URL");
		options.add("--proxyuser=PROXY_USER");
		options.add("--proxypassword=PROXY_PASSWORD");
		for (String smHelpCommand : new String[]{clienttasks.command+" -h "+module,clienttasks.command+" --help "+module}) {
			List <String> usages = new ArrayList<String>();
			String usage = String.format("Usage: %s %s [OPTIONS]",clienttasks.command,module);
			//if (clienttasks.redhatRelease.contains("release 5")) usage = usage.replaceFirst("^Usage", "usage"); // TOLERATE WORKAROUND FOR Bug 693527 ON RHEL5
			usages.add(usage);
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\?", "\\\\?")+" *$", usages}));
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, optionsRegex, new ArrayList<String>(options)}));
		}
		
		// subscription-manager repos OPTIONS
		module = "repos";
		options.clear();
		options.add("-h, --help");
		options.add("--list");
		options.add("--enable=REPOID");
		options.add("--disable=REPOID");
		for (String smHelpCommand : new String[]{clienttasks.command+" -h "+module,clienttasks.command+" --help "+module}) {
			List <String> usages = new ArrayList<String>();
			String usage = String.format("Usage: %s %s [OPTIONS]",clienttasks.command,module);
			//if (clienttasks.redhatRelease.contains("release 5")) usage = usage.replaceFirst("^Usage", "usage"); // TOLERATE WORKAROUND FOR Bug 693527 ON RHEL5
			usages.add(usage);
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\?", "\\\\?")+" *$", usages}));
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, optionsRegex, new ArrayList<String>(options)}));
		}
		
		// subscription-manager clean OPTIONS
		module = "clean";
		options.clear();
		options.add("-h, --help");
		// removed in https://bugzilla.redhat.com/show_bug.cgi?id=664581
		//options("--proxy=PROXY_URL");
		//options("--proxyuser=PROXY_USER");
		//options("--proxypassword=PROXY_PASSWORD");
		for (String smHelpCommand : new String[]{clienttasks.command+" -h "+module,clienttasks.command+" --help "+module}) {
			List <String> usages = new ArrayList<String>();
			String usage = String.format("Usage: %s %s [OPTIONS]",clienttasks.command,module);
			//if (clienttasks.redhatRelease.contains("release 5")) usage = usage.replaceFirst("^Usage", "usage"); // TOLERATE WORKAROUND FOR Bug 693527 ON RHEL5
			usages.add(usage);
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\?", "\\\\?")+" *$", usages}));
			ll.add(Arrays.asList(new Object[] {new BlockedByBzBug("664581"), smHelpCommand, optionsRegex, new ArrayList<String>(options)}));
		}
		
		// subscription-manager environments OPTIONS
		module = "environments";
		options.clear();
		options.add("-h, --help");
		options.add("--serverurl=SERVER_URL");
		options.add("--username=USERNAME");
		options.add("--password=PASSWORD");
		options.add("--org=ORG");
		options.add("--proxy=PROXY_URL");
		options.add("--proxyuser=PROXY_USER");
		options.add("--proxypassword=PROXY_PASSWORD");
		for (String smHelpCommand : new String[]{clienttasks.command+" -h "+module,clienttasks.command+" --help "+module}) {
			List <String> usages = new ArrayList<String>();
			String usage = String.format("Usage: %s %s [OPTIONS]",clienttasks.command,module);
			//if (clienttasks.redhatRelease.contains("release 5")) usage = usage.replaceFirst("^Usage", "usage"); // TOLERATE WORKAROUND FOR Bug 693527 ON RHEL5
			usages.add(usage);
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\?", "\\\\?")+" *$", usages}));
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, optionsRegex, new ArrayList<String>(options)}));
		}
		
		// subscription-manager facts OPTIONS
		module = "facts";
		options.clear();
		options.add("-h, --help");
		options.add("--list");
		options.add("--update");
		options.add("--proxy=PROXY_URL");
		options.add("--proxyuser=PROXY_USER");
		options.add("--proxypassword=PROXY_PASSWORD");
		for (String smHelpCommand : new String[]{clienttasks.command+" -h "+module,clienttasks.command+" --help "+module}) {
			List <String> usages = new ArrayList<String>();
			String usage = String.format("Usage: %s %s [OPTIONS]",clienttasks.command,module);
			//if (clienttasks.redhatRelease.contains("release 5")) usage = usage.replaceFirst("^Usage", "usage"); // TOLERATE WORKAROUND FOR Bug 693527 ON RHEL5
			usages.add(usage);
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\?", "\\\\?")+" *$", usages}));
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, optionsRegex, new ArrayList<String>(options)}));
		}
		
		// subscription-manager identity OPTIONS
		module = "identity";
		options.clear();
		options.add("-h, --help");
		options.add("--username=USERNAME");
		options.add("--password=PASSWORD");
		options.add("--regenerate");
		options.add("--force");
		options.add("--proxy=PROXY_URL");
		options.add("--proxyuser=PROXY_USER");
		options.add("--proxypassword=PROXY_PASSWORD");
		for (String smHelpCommand : new String[]{clienttasks.command+" -h "+module,clienttasks.command+" --help "+module}) {
			List <String> usages = new ArrayList<String>();
			String usage = String.format("Usage: %s %s [OPTIONS]",clienttasks.command,module);
			//if (clienttasks.redhatRelease.contains("release 5")) usage = usage.replaceFirst("^Usage", "usage"); // TOLERATE WORKAROUND FOR Bug 693527 ON RHEL5
			usages.add(usage);
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\?", "\\\\?")+" *$", usages}));
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, optionsRegex, new ArrayList<String>(options)}));
		}
		
		// subscription-manager list OPTIONS
		module = "list";
		options.clear();
		options.add("-h, --help");
		options.add("--installed");	// result of https://bugzilla.redhat.com/show_bug.cgi?id=634254
		options.add("--consumed");
		options.add("--available");
		options.add("--all");
		options.add("--servicelevel=SERVICE_LEVEL");	// result of https://bugzilla.redhat.com/show_bug.cgi?id=800999
		options.add("--ondate=ON_DATE");	// result of https://bugzilla.redhat.com/show_bug.cgi?id=672562
		options.add("--proxy=PROXY_URL");
		options.add("--proxyuser=PROXY_USER");
		options.add("--proxypassword=PROXY_PASSWORD");
		for (String smHelpCommand : new String[]{clienttasks.command+" -h "+module,clienttasks.command+" --help "+module}) {
			List <String> usages = new ArrayList<String>();
			String usage = String.format("Usage: %s %s [OPTIONS]",clienttasks.command,module);
			//if (clienttasks.redhatRelease.contains("release 5")) usage = usage.replaceFirst("^Usage", "usage"); // TOLERATE WORKAROUND FOR Bug 693527 ON RHEL5
			usages.add(usage);
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\?", "\\\\?")+" *$", usages}));
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, optionsRegex, new ArrayList<String>(options)}));
		}
		
		// subscription-manager refresh OPTIONS
		module = "refresh";
		options.clear();
		options.add("-h, --help");
		options.add("--proxy=PROXY_URL");
		options.add("--proxyuser=PROXY_USER");
		options.add("--proxypassword=PROXY_PASSWORD");
		for (String smHelpCommand : new String[]{clienttasks.command+" -h "+module,clienttasks.command+" --help "+module}) {
			List <String> usages = new ArrayList<String>();
			String usage = String.format("Usage: %s %s [OPTIONS]",clienttasks.command,module);
			//if (clienttasks.redhatRelease.contains("release 5")) usage = usage.replaceFirst("^Usage", "usage"); // TOLERATE WORKAROUND FOR Bug 693527 ON RHEL5
			usages.add(usage);
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\?", "\\\\?")+" *$", usages}));
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, optionsRegex, new ArrayList<String>(options)}));
		}
		
		// subscription-manager register OPTIONS
		module = "register";
		options.clear();
		options.add("-h, --help");
		options.add("--baseurl=BASE_URL");
		options.add("--serverurl=SERVER_URL");
		options.add("--username=USERNAME");
		options.add("--type=UNITTYPE");					// changed by bug 874816	options.add("--type=CONSUMERTYPE");
		options.add("--name=SYSTEMNAME");				// changed by bug 874816	options.add("--name=CONSUMERNAME");
		options.add("--password=PASSWORD");
		options.add("--consumerid=SYSTEMID");			// changed by bug 874816	options.add("--consumerid=CONSUMERID");
		options.add("--org=ORG");
		options.add("--environment=ENVIRONMENT");
		options.add("--autosubscribe");
		options.add("--auto-attach");					// added by bug 876340
		options.add("--force");
		options.add("--activationkey=ACTIVATION_KEYS");	// Bug 874755 - help message terminology for cli options that can be specified in multiplicity 
		options.add("--servicelevel=SERVICE_LEVEL");
		options.add("--release=RELEASE");
		options.add("--proxy=PROXY_URL");
		options.add("--proxyuser=PROXY_USER");
		options.add("--proxypassword=PROXY_PASSWORD");
		for (String smHelpCommand : new String[]{clienttasks.command+" -h "+module,clienttasks.command+" --help "+module}) {
			List <String> usages = new ArrayList<String>();
			String usage = String.format("Usage: %s %s [OPTIONS]",clienttasks.command,module);
			//if (clienttasks.redhatRelease.contains("release 5")) usage = usage.replaceFirst("^Usage", "usage"); // TOLERATE WORKAROUND FOR Bug 693527 ON RHEL5
			usages.add(usage);
			ll.add(Arrays.asList(new Object[]{null, smHelpCommand, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\?", "\\\\?")+" *$", usages}));
			ll.add(Arrays.asList(new Object[]{new BlockedByBzBug("628589"), smHelpCommand, optionsRegex, new ArrayList<String>(options)}));
		}
		
		// subscription-manager unregister OPTIONS
		module = "unregister";
		options.clear();
		options.add("-h, --help");
		options.add("--proxy=PROXY_URL");
		options.add("--proxyuser=PROXY_USER");
		options.add("--proxypassword=PROXY_PASSWORD");
		for (String smHelpCommand : new String[]{clienttasks.command+" -h "+module,clienttasks.command+" --help "+module}) {
			List <String> usages = new ArrayList<String>();
			String usage = String.format("Usage: %s %s [OPTIONS]",clienttasks.command,module);
			//if (clienttasks.redhatRelease.contains("release 5")) usage = usage.replaceFirst("^Usage", "usage"); // TOLERATE WORKAROUND FOR Bug 693527 ON RHEL5
			usages.add(usage);
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\?", "\\\\?")+" *$", usages}));
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, optionsRegex, new ArrayList<String>(options)}));
		}
		
		// subscription-manager subscribe OPTIONS
		module = "subscribe";
		options.clear();
		options.add("-h, --help");
		options.add("--pool=POOL");
		options.add("--quantity=QUANTITY");
		options.add("--auto");	// result of https://bugzilla.redhat.com/show_bug.cgi?id=680399
		options.add("--servicelevel=SERVICE_LEVEL");
		//options("--regtoken=REGTOKEN");	// https://bugzilla.redhat.com/show_bug.cgi?id=670823
		//options("--email=EMAIL");			// https://bugzilla.redhat.com/show_bug.cgi?id=670823
		//options("--locale=LOCALE");		// https://bugzilla.redhat.com/show_bug.cgi?id=670823
		options.add("--proxy=PROXY_URL");
		options.add("--proxyuser=PROXY_USER");
		options.add("--proxypassword=PROXY_PASSWORD");
		for (String smHelpCommand : new String[]{clienttasks.command+" -h "+module,clienttasks.command+" --help "+module}) {
			List <String> usages = new ArrayList<String>();
			String usage = String.format("Usage: %s %s [OPTIONS]",clienttasks.command,module);
			//if (clienttasks.redhatRelease.contains("release 5")) usage = usage.replaceFirst("^Usage", "usage"); // TOLERATE WORKAROUND FOR Bug 693527 ON RHEL5
			usages.add(usage);
			String deprecation = "Deprecated, see attach";	// added by bug 874808
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\?", "\\\\?")+" *$", usages}));
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, "^"+deprecation+"$", Arrays.asList(new String[]{deprecation})}));
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, optionsRegex, new ArrayList<String>(options)}));
		}
		
		// subscription-manager attach OPTIONS	// added by bug 874804
		module = "attach";
		options.clear();
		options.add("-h, --help");
		options.add("--pool=POOL");
		options.add("--quantity=QUANTITY");
		options.add("--auto");
		options.add("--servicelevel=SERVICE_LEVEL");
		options.add("--proxy=PROXY_URL");
		options.add("--proxyuser=PROXY_USER");
		options.add("--proxypassword=PROXY_PASSWORD");
		for (String smHelpCommand : new String[]{clienttasks.command+" -h "+module,clienttasks.command+" --help "+module}) {
			List <String> usages = new ArrayList<String>();
			String usage = String.format("Usage: %s %s [OPTIONS]",clienttasks.command,module);
			usages.add(usage);
			String deprecation = "Attach a specified subscription to the registered system";
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\?", "\\\\?")+" *$", usages}));
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, "^"+deprecation+"$", Arrays.asList(new String[]{deprecation})}));
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, optionsRegex, new ArrayList<String>(options)}));
		}
		
		// subscription-manager unsubscribe OPTIONS
		module = "unsubscribe";
		options.clear();
		options.add("-h, --help");
		options.add("--serial=SERIAL");	// Bug 874755 - help message terminology for cli options that can be specified in multiplicity 
		options.add("--all");
		options.add("--proxy=PROXY_URL");
		options.add("--proxyuser=PROXY_USER");
		options.add("--proxypassword=PROXY_PASSWORD");
		for (String smHelpCommand : new String[]{clienttasks.command+" -h "+module,clienttasks.command+" --help "+module}) {
			List <String> usages = new ArrayList<String>();
			String usage = String.format("Usage: %s %s [OPTIONS]",clienttasks.command,module);
			//if (clienttasks.redhatRelease.contains("release 5")) usage = usage.replaceFirst("^Usage", "usage"); // TOLERATE WORKAROUND FOR Bug 693527 ON RHEL5
			usages.add(usage);
			String deprecation = "Deprecated, see remove";	// added by bug 874749
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\?", "\\\\?")+" *$", usages}));
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, "^"+deprecation+"$", Arrays.asList(new String[]{deprecation})}));
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, optionsRegex, new ArrayList<String>(options)}));
		}
		
		// subscription-manager remove OPTIONS	// added by bug 874749
		module = "remove";
		options.clear();
		options.add("-h, --help");
		options.add("--serial=SERIAL");
		options.add("--all");
		options.add("--proxy=PROXY_URL");
		options.add("--proxyuser=PROXY_USER");
		options.add("--proxypassword=PROXY_PASSWORD");
		for (String smHelpCommand : new String[]{clienttasks.command+" -h "+module,clienttasks.command+" --help "+module}) {
			List <String> usages = new ArrayList<String>();
			String usage = String.format("Usage: %s %s [OPTIONS]",clienttasks.command,module);
			usages.add(usage);
			String deprecation = "Remove all or specific subscriptions from this system";
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\?", "\\\\?")+" *$", usages}));
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, "^"+deprecation+"$", Arrays.asList(new String[]{deprecation})}));
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, optionsRegex, new ArrayList<String>(options)}));
		}
		
		// subscription-manager service-level OPTIONS
		module = "service-level";
		options.clear();
		options.add("-h, --help");
		options.add("--serverurl=SERVER_URL");
		options.add("--username=USERNAME");
		options.add("--password=PASSWORD");
		options.add("--org=ORG");
		options.add("--show");
		options.add("--list");
		options.add("--set=SERVICE_LEVEL");
		options.add("--unset");
		options.add("--proxy=PROXY_URL");
		options.add("--proxyuser=PROXY_USER");
		options.add("--proxypassword=PROXY_PASSWORD");
		for (String smHelpCommand : new String[]{clienttasks.command+" -h "+module,clienttasks.command+" --help "+module}) {
			List <String> usages = new ArrayList<String>();
			String usage = String.format("Usage: %s %s [OPTIONS]",clienttasks.command,module);
			//if (clienttasks.redhatRelease.contains("release 5")) usage = usage.replaceFirst("^Usage", "usage"); // TOLERATE WORKAROUND FOR Bug 693527 ON RHEL5
			usages.add(usage);
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\?", "\\\\?")+" *$", usages}));
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, optionsRegex, new ArrayList<String>(options)}));
		}
				
		// subscription-manager release OPTIONS
		module = "release";
		options.clear();
		options.add("-h, --help");
		options.add("--proxy=PROXY_URL");
		options.add("--proxyuser=PROXY_USER");
		options.add("--proxypassword=PROXY_PASSWORD");
		options.add("--set=RELEASE");
		options.add("--list");
		options.add("--unset");
		options.add("--show");	// Bug 812153 - release command should have a --show option which is the default
		for (String smHelpCommand : new String[]{clienttasks.command+" -h "+module,clienttasks.command+" --help "+module}) {
			List <String> usages = new ArrayList<String>();
			String usage = String.format("Usage: %s %s [OPTIONS]",clienttasks.command,module);
			//if (clienttasks.redhatRelease.contains("release 5")) usage = usage.replaceFirst("^Usage", "usage"); // TOLERATE WORKAROUND FOR Bug 693527 ON RHEL5
			usages.add(usage);
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\?", "\\\\?")+" *$", usages}));
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, optionsRegex, new ArrayList<String>(options)}));
		}
		
		// subscription-manager version OPTIONS
		module = "version";
		options.clear();
		options.add("-h, --help");
		for (String smHelpCommand : new String[]{clienttasks.command+" -h "+module,clienttasks.command+" --help "+module}) {
			List <String> usages = new ArrayList<String>();
			String usage = String.format("Usage: %s %s [OPTIONS]",clienttasks.command,module);
			//if (clienttasks.redhatRelease.contains("release 5")) usage = usage.replaceFirst("^Usage", "usage"); // TOLERATE WORKAROUND FOR Bug 693527 ON RHEL5
			usages.add(usage);
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\?", "\\\\?")+" *$", usages}));
			ll.add(Arrays.asList(new Object[] {null, smHelpCommand, optionsRegex, new ArrayList<String>(options)}));
		}
		
		// ========================================================================================
		// subscription-manager-gui OPTIONS
		if (!client.runCommandAndWait("rpm -q "+clienttasks.command+"-gui").getStdout().contains("is not installed")) {	// test only when the rpm is installed
			//	[root@jsefler-6 ~]# subscription-manager-gui --help
			//	Usage: subscription-manager-gui [OPTIONS]
			//
			//	Options:
			//	  -h, --help  show this help message and exit
			//	  --register  launches the registration dialog on startup.
			
			command = clienttasks.command+"-gui"; 
			options.clear();
			options.add("-h, --help");
			options.add("--register");
			for (String commandHelp : new String[]{command+" -h", command+" --help"}) {
				
				// attempt to avoid bug 881095 RuntimeError: could not open display
				if ((Integer.valueOf(clienttasks.redhatReleaseX)==6 && Float.valueOf(clienttasks.redhatReleaseXY)<6.4) || 
					(Integer.valueOf(clienttasks.redhatReleaseX)==5 && Float.valueOf(clienttasks.redhatReleaseXY)<5.10)){
					commandHelp = "export DISPLAY=localhost:10.0 && "+commandHelp;
				}
				
				List <String> usages = new ArrayList<String>();
				String usage = String.format("Usage: %s [OPTIONS]",command);
				usages.add(usage);
				ll.add(Arrays.asList(new Object[] {new BlockedByBzBug("881095"), commandHelp, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\|", "\\\\|").replaceAll("\\?", "\\\\?")+" *$", usages}));
				ll.add(Arrays.asList(new Object[] {new BlockedByBzBug("881095"), commandHelp, optionsRegex, new ArrayList<String>(options)}));
			}
		}
		
		// ========================================================================================
		// rhsm-icon OPTIONS
		if (!client.runCommandAndWait("rpm -q "+clienttasks.command+"-gui").getStdout().contains("is not installed")) {	// test only when the rpm is installed
			//	[root@jsefler-onprem-5server ~]# rhsm-icon -?
			//	Usage:
			//	  rhsm-icon [OPTION...] rhsm icon
			//	
			//	Help Options:
			//	  -?, --help                  Show help options
			//	  --help-all                  Show all help options
			//	  --help-gtk                  Show GTK+ Options
			//	
			//	Application Options:
			//	  -c, --check-period          How often to check for validity (in seconds)
			//	  -d, --debug                 Show debug messages
			//	  -f, --force-icon=TYPE       Force display of the icon (expired, partial or warning)
			//	  -i, --check-immediately     Run the first status check right away
			//	  --display=DISPLAY           X display to use
			command = "rhsm-icon";
			List <String> rhsmIconOptions = new ArrayList<String>();
			if (clienttasks.redhatReleaseX.equals("5"))	rhsmIconOptions.add("-?, --help");	// rhel5
			else										rhsmIconOptions.add("-h, --help");	// rhel6
			rhsmIconOptions.add("--help-all");
			rhsmIconOptions.add("--help-gtk");
			rhsmIconOptions.add("-c, --check-period");
			rhsmIconOptions.add("-d, --debug");
			rhsmIconOptions.add("-f, --force-icon=TYPE");
			rhsmIconOptions.add("-i, --check-immediately");
			rhsmIconOptions.add("--display=DISPLAY");
			for (String rhsmIconHelpCommand : new String[]{command+" -?", command+" --help"}) {
				List <String> usages = new ArrayList<String>();
				String usage = command+" [OPTIONS]";
				usage = command+" [OPTION...]"; // usage = rhsmIconCommand+" [OPTION...] rhsm icon"; // Bug 771756 - rhsm-icon --help usage message is misleading 
				usages.add(usage);
				if (!Arrays.asList("6.1","5.7","6.2","5.8","6.3").contains(clienttasks.redhatReleaseXY)) // skip the following rhsmIconHelpCommand usage test since bug 771756 was not fixed until 5.9
				ll.add(Arrays.asList(new Object[] {new BlockedByBzBug("771756"), rhsmIconHelpCommand, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\?", "\\\\?")+" *$", usages}));
				ll.add(Arrays.asList(new Object[] {null, rhsmIconHelpCommand, optionsRegex, rhsmIconOptions}));
			}
			List <String> rhsmIconGtkOptions = new ArrayList<String>();
			rhsmIconGtkOptions.add("--screen=SCREEN");
			// GTK options are presented here: http://developer.gnome.org/gtk-tutorial/2.90/c39.html
			rhsmIconGtkOptions.add("--class=CLASS");
			rhsmIconGtkOptions.add("--name=NAME");
			rhsmIconGtkOptions.add("--display=DISPLAY");
			rhsmIconGtkOptions.add("--sync");
			rhsmIconGtkOptions.add("--gtk-module=MODULES");
			rhsmIconGtkOptions.add("--g-fatal-warnings");
			if (!clienttasks.redhatReleaseX.equals("5"))	rhsmIconGtkOptions.add("--gdk-debug=FLAGS");
			if (!clienttasks.redhatReleaseX.equals("5"))	rhsmIconGtkOptions.add("--gdk-no-debug=FLAGS");
			if (!clienttasks.redhatReleaseX.equals("5"))	rhsmIconGtkOptions.add("--gtk-debug=FLAGS");
			if (!clienttasks.redhatReleaseX.equals("5"))	rhsmIconGtkOptions.add("--gtk-no-debug=FLAGS");
			ll.add(Arrays.asList(new Object[] {null, command+" --help-gtk", optionsRegex, rhsmIconGtkOptions}));
			List <String> rhsmIconAllOptions = new ArrayList<String>();
			rhsmIconAllOptions.addAll(rhsmIconOptions);
			rhsmIconAllOptions.addAll(rhsmIconGtkOptions);
			ll.add(Arrays.asList(new Object[] {null, command+" --help-all", optionsRegex, rhsmIconAllOptions}));
		}
		
		// ========================================================================================
		// rhn-migrate-classic-to-rhsm OPTIONS
		if (!client.runCommandAndWait("rpm -q "+clienttasks.command+"-migration").getStdout().contains("is not installed")) {	// test only when the rpm is installed
			//	[root@jsefler-onprem-5server ~]# rhn-migrate-classic-to-rhsm -h
			//	usage: /usr/sbin/rhn-migrate-classic-to-rhsm [--force|--cli-only|--help|--no-auto]
			//	
			//	options:
			//	  -f, --force     Ignore Channels not available on RHSM
			//	  -c, --cli-only  Don't launch the GUI tool to subscribe the system, just use
			//	                  the CLI tool which will do it automatically
			//	  -n, --no-auto   Don't launch subscription manager at end of process.
			//	  -h, --help      show this help message and exit
			
			command = MigrationTests.rhnMigrateTool; 
			options.clear();
			options.add("-f, --force");
			options.add("-g, --gui");
			options.add("-n, --no-auto");
			options.add("-s SERVICELEVEL, --servicelevel=SERVICELEVEL");
			options.add("--serverurl=SERVERURL");
			options.add("-h, --help");
			for (String commandHelp : new String[]{command+" -h", command+" --help"}) {
				List <String> usages = new ArrayList<String>();
				String usage = String.format("usage: %s [OPTIONS]",command);
				usage = String.format("Usage: %s [OPTIONS]",command);
				usages.add(usage);
				ll.add(Arrays.asList(new Object[] {null, commandHelp, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\|", "\\\\|").replaceAll("\\?", "\\\\?")+" *$", usages}));
				ll.add(Arrays.asList(new Object[] {null, commandHelp, optionsRegex, new ArrayList<String>(options)}));
			}
		}
		
		// ========================================================================================
		// install-num-migrate-to-rhsm OPTIONS
		if (!client.runCommandAndWait("rpm -q "+clienttasks.command+"-migration").getStdout().contains("is not installed")) {	// test only when the rpm is installed
		if (clienttasks.redhatReleaseX.equals("5")) {	// test only on RHEL5
			//	[root@jsefler-onprem-5server ~]# install-num-migrate-to-rhsm --help
			//	usage: install-num-migrate-to-rhsm [options]
			//	
			//	options:
			//	  -h, --help            show this help message and exit
			//	  -i INSTNUMBER, --instnumber=INSTNUMBER
			//	                        Install number to run against
			//	  -d, --dryrun          Only print the files which would be copied over
			
			command = MigrationTests.installNumTool; 
			options.clear();
			options.add("-h, --help");
			options.add("-i INSTNUMBER, --instnumber=INSTNUMBER");
			options.add("-d, --dryrun");
			for (String commandHelp : new String[]{command+" -h", command+" --help"}) {
				List <String> usages = new ArrayList<String>();
				String usage = String.format("usage: %s [options]",command);
				usage = String.format("Usage: %s [OPTIONS]",command);	// changed by bug 876692
				usages.add(usage);
				ll.add(Arrays.asList(new Object[] {null, commandHelp, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\|", "\\\\|").replaceAll("\\?", "\\\\?")+" *$", usages}));
				ll.add(Arrays.asList(new Object[] {null, commandHelp, optionsRegex, new ArrayList<String>(options)}));
			}
		}
		}
		
		// ========================================================================================
		// rhsmcertd OPTIONS
		
		//	[root@nec-em15 ~]# rhsmcertd -?
		//	Usage:
		//	  rhsmcertd [OPTION...] 
		//	
		//	Help Options:
		//	  -?, --help                      Show help options
		//	  --help-all                      Show all help options
		//	  --help-rhsmcertd                rhsmcertd
		//	
		//	Application Options:
		//	  -c, --cert-interval=MINUTES     Interval to run cert check (in minutes)
		//	  -i, --heal-interval=MINUTES     Interval to run healing (in minutes)
		//	  -n, --now                       Run the initial checks immediatly, with no delay.
		//	  -d, --debug                     Show debug messages
		
		command = clienttasks.rhsmCertD; 
		options.clear();
		if (clienttasks.redhatReleaseX.equals("5"))	options.add("-?, --help");	// rhel5
		else										options.add("-h, --help");	// rhel6
		//options.add("--help-all");		// removed by Bug 842020 - what is rhsmcertd --help-rhsmcertd? 
		//options.add("--help-rhsmcertd");	// removed by Bug 842020 - what is rhsmcertd --help-rhsmcertd? 
		options.add("-c, --cert-interval=MINUTES");
		//options.add("-i, --heal-interval=MINUTES");		// removed by bug 876753
		options.add("--heal-interval=MINUTES");				// added by bug 876753 as a deprecation
		options.add("-i, --auto-attach-interval=MINUTES");	// added by bug 876753
		options.add("-n, --now");
		options.add("-d, --debug");
		for (String commandHelp : new String[]{command+" -?", command+" -h", command+" --help"}) {
			List <String> usages = new ArrayList<String>();
			String usage = command+" [OPTIONS]";
			usage = command+" [OPTION...]";
			usages.add(usage);
			ll.add(Arrays.asList(new Object[] {null, commandHelp, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\?", "\\\\?")+" *$", usages}));
			ll.add(Arrays.asList(new Object[] {null, commandHelp, optionsRegex, new ArrayList<String>(options)}));
		}

		
		// ========================================================================================
		// /usr/libexec/rhsmd OPTIONS
		
		//	[root@jsefler-6 ~]# /usr/libexec/rhsmd --help
		//	Usage: rhsmd [options]
		//
		//	Options:
		//	  -h, --help            show this help message and exit
		//	  -d, --debug           Display debug messages
		//	  -k, --keep-alive      Stay running (don't shut down after the first dbus
		//	                        call)
		//	  -s, --syslog          Run standalone and log result to syslog
		//	  -f FORCE_SIGNAL, --force-signal=FORCE_SIGNAL
		//	                        Force firing of a signal (valid, expired, warning,
		//	                        partial, classic or registration_required)
		//	  -i, --immediate       Fire forced signal immediately (requires --force-
		//	                        signal)
		
		command = clienttasks.rhsmComplianceD; 
		options.clear();
		options.add("-h, --help");
		options.add("-d, --debug");
		options.add("-k, --keep-alive");
		options.add("-s, --syslog");
		options.add("-f FORCE_SIGNAL, --force-signal=FORCE_SIGNAL");
		options.add("-i, --immediate");
		for (String commandHelp : new String[]{command+" -h", command+" --help"}) {
			List <String> usages = new ArrayList<String>();
			String usage = String.format("Usage: %s [options]",command.replaceFirst("/.+/", ""));
			usage = String.format("Usage: %s [OPTIONS]",command.replaceFirst("/.+/", ""));	// changed by bug 876692
			usages.add(usage);
			ll.add(Arrays.asList(new Object[] {null, commandHelp, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\?", "\\\\?")+" *$", usages}));
			ll.add(Arrays.asList(new Object[] {null, commandHelp, optionsRegex, new ArrayList<String>(options)}));
		}
		
		// ========================================================================================
		// /usr/libexec/rhsmcertd-worker OPTIONS
		
		//	[root@jsefler-6 ~]# /usr/libexec/rhsmcertd-worker --help
		//	Usage: rhsmcertd-worker [options]
		//
		//	Options:
		//	  -h, --help  show this help message and exit
		//	  --autoheal  perform an autoheal check
		
		command = clienttasks.rhsmCertDWorker; 
		options.clear();
		options.add("-h, --help");
		options.add("--autoheal");
		for (String commandHelp : new String[]{command+" -h", command+" --help"}) {
			List <String> usages = new ArrayList<String>();
			String usage = String.format("Usage: %s [options]",command.replaceFirst("/.+/", ""));
			usage = String.format("Usage: %s [OPTIONS]",command.replaceFirst("/.+/", ""));	// changed by bug 876692
			usages.add(usage);
			ll.add(Arrays.asList(new Object[] {null, commandHelp, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\?", "\\\\?")+" *$", usages}));
			ll.add(Arrays.asList(new Object[] {null, commandHelp, optionsRegex, new ArrayList<String>(options)}));
		}
		
		// ========================================================================================
		// rct MODULES
		
		//	[root@jsefler-rhel59 ~]# rct --help
		//	
		//	Usage: rct MODULE-NAME [MODULE-OPTIONS] [--help]
		//	
		//	
		//	Primary Modules:
		//	
		//		cat-cert       Print certificate info to standard output.
		//	
		//	Other Modules (Please consult documentation):
		
		command = "rct"; 
		modules.clear();
		modules.add("cat-cert");
		modules.add("stat-cert");
		for (String commandHelp : new String[]{command+" -h",command+" --help"}) {
			List <String> usages = new ArrayList<String>();
			String usage = String.format("Usage: %s MODULE-NAME [MODULE-OPTIONS] [--help]",command);
			usages.add(usage);
			ll.add(Arrays.asList(new Object[] {null, commandHelp, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\?", "\\\\?")+" *$", usages}));
			ll.add(Arrays.asList(new Object[] {null, commandHelp, modulesRegex, new ArrayList<String>(modules)}));
		}
		
		// rct cat-cert OPTIONS
		
		//	[root@jsefler-rhel59 ~]# rct cat-cert --help
		//	Usage: rct cat-cert [OPTIONS] CERT_FILE
		//	
		//	Print certificate info to standard output.
		//	
		//	options:
		//	  -h, --help     show this help message and exit
		//	  --no-products  do not show the cert's product information
		//	  --no-content   do not show the cert's content info.
		module = "cat-cert";
		options.clear();
		options.add("-h, --help");
		options.add("--no-products");
		options.add("--no-content");
		for (String commandHelp : new String[]{command+" "+module+" -h",command+" "+module+" --help"}) {
			List <String> usages = new ArrayList<String>();
			String usage = String.format("Usage: %s %s [OPTIONS] CERT_FILE",command,module);
			usages.add(usage);
			ll.add(Arrays.asList(new Object[] {null, commandHelp, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\?", "\\\\?")+" *$", usages}));
			ll.add(Arrays.asList(new Object[] {null, commandHelp, optionsRegex, new ArrayList<String>(options)}));
		}
		
		// rct stat-cert OPTIONS
		
		//	[root@jsefler-6 ~]# rct stat-cert --help
		//	Usage: rct stat-cert [OPTIONS] CERT_FILE
		//
		//	Print certificate statistics and sizes
		//
		//	Options:
		//	  -h, --help  show this help message and exit
		//	[root@jsefler-6 ~]# rct stat-cert --help
		//	Usage: rct stat-cert [OPTIONS] CERT_FILE
		//
		//	Print certificate statistics and sizes
		//
		//	Options:
		//	  -h, --help  show this help message and exit
		module = "stat-cert";
		options.clear();
		options.add("-h, --help");
		for (String commandHelp : new String[]{command+" "+module+" -h",command+" "+module+" --help"}) {
			List <String> usages = new ArrayList<String>();
			String usage = String.format("Usage: %s %s [OPTIONS] CERT_FILE",command,module);
			usages.add(usage);
			ll.add(Arrays.asList(new Object[] {null, commandHelp, usage.replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]").replaceAll("\\?", "\\\\?")+" *$", usages}));
			ll.add(Arrays.asList(new Object[] {null, commandHelp, optionsRegex, new ArrayList<String>(options)}));
		}
		
		
		
		return ll;
	}
}

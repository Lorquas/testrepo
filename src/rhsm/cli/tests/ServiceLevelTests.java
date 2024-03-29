package rhsm.cli.tests;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.xmlrpc.XmlRpcException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.testng.SkipException;
import org.testng.annotations.AfterGroups;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.redhat.qe.Assert;
import com.redhat.qe.auto.bugzilla.BzChecker;
import com.redhat.qe.auto.tcms.ImplementsNitrateTest;
import com.redhat.qe.auto.testng.TestNGUtils;
import rhsm.base.CandlepinType;
import rhsm.base.SubscriptionManagerCLITestScript;
import rhsm.cli.tasks.CandlepinTasks;
import rhsm.data.EntitlementCert;
import rhsm.data.InstalledProduct;
import rhsm.data.Org;
import rhsm.data.ProductCert;
import rhsm.data.ProductNamespace;
import rhsm.data.ProductSubscription;
import rhsm.data.SubscriptionPool;
import com.redhat.qe.tools.SSHCommandResult;

/**
 * @author jsefler
 *
 * Reference: https://engineering.redhat.com/trac/Entitlement/wiki/SlaSubscribe
 */

@Test(groups={"ServiceLevelTests"})
public class ServiceLevelTests extends SubscriptionManagerCLITestScript {

		
	// Test methods ***********************************************************************
	
	
	
	@Test(	description="subscription-manager: service-level (when not registered)",
			groups={"blockedByBug-826856"/*,"blockedByBug-837036"*/},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void ServiceLevelWhenNotRegistered_Test() {
		
		// make sure we are not registered
		clienttasks.unregister(null, null, null);
		
		SSHCommandResult result;
		
		// with credentials
		result = clienttasks.service_level_(null, null, null, null, sm_clientUsername, sm_clientPassword, /*sm_clientOrg*/null, null, null, null, null);
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(255), "ExitCode from service-level (implies --show) without being registered");
		//Assert.assertEquals(result.getStdout().trim(),"Error: This system is currently not registered.", "Stdout from service-level --show without being registered");
		Assert.assertEquals(result.getStdout().trim(),"This system is not yet registered. Try 'subscription-manager register --help' for more information.", "Stdout from service-level --show without being registered");
		if (sm_clientOrg!=null) {
			result = clienttasks.service_level_(null, null, null, null, sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null);
			Assert.assertEquals(result.getExitCode(), Integer.valueOf(255), "ExitCode from service-level (implies --show) without being registered");
			Assert.assertEquals(result.getStdout().trim(),"Error: --org is only supported with the --list option", "Stdout from service-level --show without being registered");
		}
		
		// without credentials
		result = clienttasks.service_level_(null, null, null, null, null, null, null, null, null, null, null);
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(255), "ExitCode from service-level (implies --show) without being registered");
		//Assert.assertEquals(result.getStdout().trim(),"Error: This system is currently not registered.", "Stdout from service-level --show without being registered");
		Assert.assertEquals(result.getStdout().trim(),"This system is not yet registered. Try 'subscription-manager register --help' for more information.", "Stdout from service-level --show without being registered");
	}
	
	
	@Test(	description="subscription-manager: service-level --show (when not registered)",
			groups={"blockedByBug-826856",/*"blockedByBug-837036"*/},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void ServiceLevelShowWhenNotRegistered_Test() {
		
		// make sure we are not registered
		clienttasks.unregister(null, null, null);
		
		SSHCommandResult result;
		
		// with credentials
		result = clienttasks.service_level_(true, null, null, null, sm_clientUsername, sm_clientPassword, /*sm_clientOrg*/null, null, null, null, null);
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(255), "ExitCode from service-level --show without being registered");
		//Assert.assertEquals(result.getStdout().trim(),"Error: This system is currently not registered.", "Stdout from service-level --show without being registered");
		Assert.assertEquals(result.getStdout().trim(),"This system is not yet registered. Try 'subscription-manager register --help' for more information.", "Stdout from service-level --show without being registered");
		if (sm_clientOrg!=null) {
			result = clienttasks.service_level_(null, null, null, null, sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null);
			Assert.assertEquals(result.getExitCode(), Integer.valueOf(255), "ExitCode from service-level --show without being registered");
			Assert.assertEquals(result.getStdout().trim(),"Error: --org is only supported with the --list option", "Stdout from service-level --show without being registered");
		}
		
		// without credentials
		result = clienttasks.service_level_(true, null, null, null, null, null, null, null, null, null, null);
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(255), "ExitCode from service-level --show without being registered");
		//Assert.assertEquals(result.getStdout().trim(),"Error: This system is currently not registered.", "Stdout from service-level --show without being registered");
		Assert.assertEquals(result.getStdout().trim(),"This system is not yet registered. Try 'subscription-manager register --help' for more information.", "Stdout from service-level --show without being registered");
	}
	
	
	@Test(	description="subscription-manager: service-level --list (when not registered)",
			groups={},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void ServiceLevelListWhenNotRegistered_Test() {
		
		// make sure we are not registered
		clienttasks.unregister(null, null, null);
		
		SSHCommandResult result = clienttasks.service_level_(null, true, null, null, null, null, null, null, null, null, null);
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(255), "ExitCode from service-level --list without being registered");
		Assert.assertEquals(result.getStdout().trim(),"Error: you must register or specify --username and --password to list service levels", "Stdout from service-level --list without being registered");
	}
	
	
	@Test(	description="subscription-manager: service-level --set (when not registered)",
			groups={},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void ServiceLevelSetWhenNotRegistered_Test() {
		
		// make sure we are not registered
		clienttasks.unregister(null, null, null);
		
		SSHCommandResult result;
		
		// with credentials
		result = clienttasks.service_level_(null, null, "FOO", null, sm_clientUsername, sm_clientPassword, /*sm_clientOrg*/null, null, null, null, null);
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(255), "ExitCode from service-level --set without being registered");
		//Assert.assertEquals(result.getStdout().trim(),"Error: This system is currently not registered.", "Stdout from service-level --set without being registered");
		Assert.assertEquals(result.getStdout().trim(),"This system is not yet registered. Try 'subscription-manager register --help' for more information.", "Stdout from service-level --set without being registered");
		if (sm_clientOrg!=null) {
			result = clienttasks.service_level_(null, null, "FOO", null, sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null);
			Assert.assertEquals(result.getExitCode(), Integer.valueOf(255), "ExitCode from service-level --set without being registered");
			Assert.assertEquals(result.getStdout().trim(),"Error: --org is only supported with the --list option", "Stdout from service-level --set without being registered");
		}
		
		// without credentials
		result = clienttasks.service_level_(null, null, "FOO", null, null, null, null, null, null, null, null);
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(255), "ExitCode from service-level --set without being registered");
		//Assert.assertEquals(result.getStdout().trim(),"Error: This system is currently not registered.", "Stdout from service-level --set without being registered");
		Assert.assertEquals(result.getStdout().trim(),"This system is not yet registered. Try 'subscription-manager register --help' for more information.", "Stdout from service-level --set without being registered");
	}
	
	
	@Test(	description="subscription-manager: service-level --unset (when not registered)",
			groups={},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void ServiceLevelUnsetWhenNotRegistered_Test() {
		
		// make sure we are not registered
		clienttasks.unregister(null, null, null);
		
		SSHCommandResult result;
		
		// with credentials
		result = clienttasks.service_level_(null, null, null, true, sm_clientUsername, sm_clientPassword, /*sm_clientOrg*/null, null, null, null, null);
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(255), "ExitCode from service-level --unset without being registered");
		//Assert.assertEquals(result.getStdout().trim(),"Error: This system is currently not registered.", "Stdout from service-level --unset without being registered");
		Assert.assertEquals(result.getStdout().trim(),"This system is not yet registered. Try 'subscription-manager register --help' for more information.", "Stdout from service-level --unset without being registered");
		if (sm_clientOrg!=null) {
			result = clienttasks.service_level_(null, null, null, true, sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null);
			Assert.assertEquals(result.getExitCode(), Integer.valueOf(255), "ExitCode from service-level --unset without being registered");
			Assert.assertEquals(result.getStdout().trim(),"Error: --org is only supported with the --list option", "Stdout from service-level --unset without being registered");
		}
		
		// without credentials
		result = clienttasks.service_level_(null, null, null, true, null, null, null, null, null, null, null);
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(255), "ExitCode from service-level --unset without being registered");
		//Assert.assertEquals(result.getStdout().trim(),"Error: This system is currently not registered.", "Stdout from service-level --unset without being registered");
		Assert.assertEquals(result.getStdout().trim(),"This system is not yet registered. Try 'subscription-manager register --help' for more information.", "Stdout from service-level --unset without being registered");
	}
	
	
	@Test(	description="subscription-manager: service-level --list (with invalid credentials)",
			groups={},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void ServiceLevelListWithInvalidCredentials_Test() {
		String x = String.valueOf(getRandInt());
		SSHCommandResult result;
				
		// test while unregistered
		clienttasks.unregister(null, null, null);
		result = clienttasks.service_level_(null, true, null, null, sm_clientUsername, sm_clientPassword+x, sm_clientOrg, null, null, null, null);
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(255), "ExitCode from service-level --list with invalid credentials");
		//if (sm_serverOld) {Assert.assertEquals(result.getStdout().trim(), "Error: you must register or specify --org."); throw new SkipException("service-level --list with invalid credentials against an old candlepin server is not supported.");}
		if (sm_serverOld) {Assert.assertEquals(result.getStderr().trim(), "ERROR: The service-level command is not supported by the server."); throw new SkipException("Skipping this test since service-level is gracefully not supported when configured against an old candlepin server.");}
		Assert.assertEquals(result.getStderr().trim(), servertasks.invalidCredentialsMsg(), "Stderr from service-level --list with invalid credentials");
		Assert.assertEquals(result.getStdout().trim(), "", "Stdout from service-level --list with invalid credentials");

		// test while registered
		clienttasks.register(sm_clientUsername,sm_clientPassword,sm_clientOrg,null,null,null,null,null,null,null,(List<String>)null,null,null,null,null, null, null, null);
		result = clienttasks.service_level_(null, true, null, null, sm_clientUsername, sm_clientPassword+x, sm_clientOrg, null, null, null, null);
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(255), "ExitCode from service-level --list with invalid credentials");
		Assert.assertEquals(result.getStderr().trim(), servertasks.invalidCredentialsMsg(), "Stderr from service-level --list with invalid credentials");
		Assert.assertEquals(result.getStdout().trim(), "", "Stdout from service-level --list with invalid credentials");
	}
	
	
	@Test(	description="subscription-manager: service-level --list (with invalid org)",
			groups={"blockedByBug-796468","blockedByBug-815479"},
			enabled=true)
	@ImplementsNitrateTest(caseId=165509)
	public void ServiceLevelListWithInvalidOrg_Test() {
		String x = String.valueOf(getRandInt());
		SSHCommandResult result;
				
		// test while unregistered
		clienttasks.unregister(null, null, null);
		result = clienttasks.service_level_(null, true, null, null, sm_clientUsername, sm_clientPassword, sm_clientOrg+x, null, null, null, null);
		if (sm_serverOld) {Assert.assertEquals(result.getStderr().trim(), "ERROR: The service-level command is not supported by the server."); throw new SkipException("Skipping this test since service-level is gracefully not supported when configured against an old candlepin server.");}
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(255), "ExitCode from service-level --list with invalid org");
		Assert.assertEquals(result.getStderr().trim(), String.format("Organization with id %s could not be found.",sm_clientOrg+x), "Stderr from service-level --list with invalid org");
		Assert.assertEquals(result.getStdout().trim(), "", "Stdout from service-level --list with invalid credentials");

		// test while registered
		clienttasks.register(sm_clientUsername,sm_clientPassword,sm_clientOrg,null,null,null,null,null,null,null,(List<String>)null,null,null,null,null, null, null, null);
		result = clienttasks.service_level_(null, true, null, null, sm_clientUsername, sm_clientPassword, sm_clientOrg+x, null, null, null, null);
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(255), "ExitCode from service-level --list with invalid org");
		Assert.assertEquals(result.getStderr().trim(), String.format("Organization with id %s could not be found.",sm_clientOrg+x), "Stderr from service-level --list with invalid org");
		Assert.assertEquals(result.getStdout().trim(), "", "Stdout from service-level --list with invalid credentials");
	}
	
	
	@Test(	description="subscription-manager: service-level --show (after registering without a service level)",
			groups={},
			enabled=false) // assertions in this test are already a subset of ServiceLevelShowAvailable_Test(...)
	@Deprecated
	@ImplementsNitrateTest(caseId=157213)
	public void ServiceLevelShowAfterRegisteringWithoutServiceLevel_Test_DEPRECATED() throws JSONException, Exception  {
		SSHCommandResult result;
				
		// register with no service-level
		String consumerId = clienttasks.getCurrentConsumerId(clienttasks.register(sm_clientUsername,sm_clientPassword,sm_clientOrg,null,null,null,null,null,null,null,(List<String>)null,null,null,true,null, null, null, null));
		
		// get the current consumer object and assert that the serviceLevel persisted
		JSONObject jsonConsumer = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername, sm_clientPassword, sm_serverUrl, "/consumers/"+consumerId));
		Assert.assertEquals(jsonConsumer.get("serviceLevel"), JSONObject.NULL, "The call to register without a servicelevel leaves the current consumer object serviceLevel attribute value null.");
		
		result = clienttasks.service_level(true, false, null, null, null, null, null, null, null, null, null);
		Assert.assertEquals(result.getStdout().trim(), "Current service level:", "When the system has been registered without a service level, the current service level should be null.");
	}
	
	
	@Test(	description="subscription-manager: service-level --show (after registering without a service level)",
			groups={"AcceptanceTests"},
			dataProvider="getRegisterCredentialsExcludingNullOrgData",
			enabled=true)
	@ImplementsNitrateTest(caseId=155949)
	public void ServiceLevelShowAvailable_Test(String username, String password, String org) throws JSONException, Exception  {
				
		// register with no service-level
		String consumerId = clienttasks.getCurrentConsumerId(clienttasks.register(username,password,org,null,null,null,null,null,null,null,(List<String>)null,null,null,true,null, null, null, null));
		
		// get the current consumer object and assert that the serviceLevel is empty (value is "")
		JSONObject jsonConsumer = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(username,password,sm_serverUrl,"/consumers/"+consumerId));
		// Assert.assertEquals(jsonConsumer.get("serviceLevel"), JSONObject.NULL, "The call to register without a servicelevel leaves the current consumer object serviceLevel attribute value null.");	// original value was null
		if (sm_serverOld) {Assert.assertFalse(jsonConsumer.has("serviceLevel"), "Consumer attribute serviceLevel should not exist against an old candlepin server."); throw new SkipException("The service-level command is not supported by the server.");}
		Assert.assertEquals(jsonConsumer.get("serviceLevel"), "", "The call to register without a servicelevel leaves the current consumer object serviceLevel attribute value empty.");
	
		// assert that "Current service level:" is empty
		Assert.assertEquals(clienttasks.getCurrentServiceLevel(), "", "When the system has been registered without a service level, the current service level value should be empty.");
		//Assert.assertEquals(clienttasks.service_level(null,null,null,null,null,null,null,null, null).getStdout().trim(), "Current service level:", "When the system has been registered without a service level, the current service level value should be empty.");
		Assert.assertEquals(clienttasks.service_level(null,null,null,null,null,null,null,null, null, null, null).getStdout().trim(), "Service level preference not set", "When the system has been registered without a service level, the current service level value should be empty.");

		// get all the valid service levels available to this org	
		List<String> serviceLevelsExpected = CandlepinTasks.getServiceLevelsForOrgKey(/*username or*/sm_serverAdminUsername, /*password or*/sm_serverAdminPassword, sm_serverUrl, org);
		
		// assert that all the valid service levels are returned by service-level --list
		List<String> serviceLevelsActual = clienttasks.getCurrentlyAvailableServiceLevels();		
		Assert.assertTrue(serviceLevelsExpected.containsAll(serviceLevelsActual)&&serviceLevelsActual.containsAll(serviceLevelsExpected), "The actual service levels available to the current consumer "+serviceLevelsActual+" match the expected list of service levels available to the org '"+org+"' "+serviceLevelsExpected+".");

		// assert that exempt service levels do NOT appear as valid service levels
		for (String sm_exemptServiceLevel : sm_exemptServiceLevelsInUpperCase) {
			for (String serviceLevel : serviceLevelsExpected) {
				
				// TEMPORARY WORKAROUND FOR BUG: 840022 - product attributes with support_level=Layered also need support_level_exempt=true
				if (serviceLevel.equalsIgnoreCase(sm_exemptServiceLevel) && sm_serverType.equals(CandlepinType.hosted)) {
					boolean invokeWorkaroundWhileBugIsOpen = true;
					String bugId="840022"; 
					try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
					if (invokeWorkaroundWhileBugIsOpen) {
						throw new SkipException("Skipping this test with serviceLevel='"+serviceLevel+"' against a hosted candlepin while bug "+bugId+" is open.");
					}
				}
				// END OF WORKAROUND

				Assert.assertTrue(!serviceLevel.equalsIgnoreCase(sm_exemptServiceLevel), "Regardless of case, available service level '"+serviceLevel+"' should NOT match exempt service level '"+sm_exemptServiceLevel+"'.");
			}
		}
		
		// subscribe with each service level and assert that it persists as the system preference
		String currentServiceLevel = clienttasks.getCurrentServiceLevel();
		for (String serviceLevel : serviceLevelsExpected) {
			
			// the following if block was added after implementation of Bug 864207 - 'subscription-manager subscribe --auto' should be smart enough to not run when all products are subscribed already
			// when the system is already valid (all of the currently installe products are subscribed), then auto-subscribe will do nothing and the service level will remain
			if (clienttasks.getFactValue("system.entitlements_valid").equalsIgnoreCase("valid")) {
				// verify that auto-subscribe will throw a blocker message and the current service level will remain
				SSHCommandResult result = clienttasks.subscribe_(true, serviceLevel, (String)null, (String)null, (String)null, null, null, null, null, null, null);
				String expectedStdout = "All installed products are covered by valid entitlements. No need to update subscriptions at this time.";
				Assert.assertTrue(result.getStdout().trim().startsWith(expectedStdout), "When the system is already compliant, an attempt to auto-subscribe should inform us with exactly this message: "+expectedStdout);
				Assert.assertEquals(clienttasks.getCurrentServiceLevel(), currentServiceLevel, "When the system is already compliant, an attempt to auto-subscribe with a servicelevel should NOT alter the current service level: "+currentServiceLevel);
				clienttasks.unsubscribe(true, (BigInteger)null, null, null, null);
			}
			
			// auto-subscribe and assert that the requested service level is persisted
			clienttasks.subscribe_(true, serviceLevel, (String)null, (String)null, (String)null, null, null, null, null, null, null);
			currentServiceLevel = clienttasks.getCurrentServiceLevel();
			Assert.assertEquals(currentServiceLevel, serviceLevel, "When the system is auto subscribed with the service level option, the service level should be persisted as the new preference.");
		}
	}
		
	
	
	
	
	
	@Test(	description="subscription-manager: register to a Candlepin server using autosubscribe with an unavailable servicelevel",
			groups={"blockedByBug-795798","blockedByBug-864508","blockedByBug-864508"},
			enabled=true)
	public void RegisterWithUnavailableServiceLevel_Test() {

		// attempt the registration
		String unavailableServiceLevel = "FOO";
		SSHCommandResult sshCommandResult = clienttasks.register_(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, true, unavailableServiceLevel, null, (String)null, null, null, true, null, null, null, null);
		String msg = "Cannot set a service level for a consumer that is not available to its organization."; // before Bug 795798 - Cannot set a service level for a consumer that is not available to its organization.
		//msg = String.format("Service level %s is not available to consumers of organization %s.",unavailableServiceLevel,sm_clientOrg);
		msg = String.format("Service level '%s' is not available to consumers of organization %s.",unavailableServiceLevel,sm_clientOrg);
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(255));
		Assert.assertTrue(sshCommandResult.getStdout().trim().contains(msg), "Stdout message contains: "+msg);
		Assert.assertEquals(sshCommandResult.getStderr().trim(), "", "Stderr message from an attempt to register with autosubscribe and an unavailable servicelevel.");

		// despite a failed attempt to set a service level, we should still be registered
		Assert.assertNotNull(clienttasks.getCurrentConsumerId(), "Despite a failed attempt to set a service level during register with autosubscribe, we should still be registered");
		
		// since the autosubscribe was aborted, we should not be consuming and entitlements
		Assert.assertTrue(clienttasks.getCurrentEntitlementCerts().isEmpty(), "Due to a failed attempt to set a service level during register with autosubscribe, we should not be consuming any entitlements.");	
	}
	
	
	@Test(	description="subscription-manager: register with autosubscribe while specifying an valid service level; assert the entitlements granted match the requested service level",
			groups={"AcceptanceTests","blockedByBug-859652"},
			dataProvider="getAllAvailableServiceLevelData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void RegisterWithAvailableServiceLevel_Test(Object bugzilla, String serviceLevel) throws JSONException, Exception {
		// Reference: https://engineering.redhat.com/trac/Entitlement/wiki/SlaSubscribe

		// register with autosubscribe specifying a valid service level
		String consumerId = clienttasks.getCurrentConsumerId(clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, true, serviceLevel, null, (String)null, null, null, true, null, null, null, null));
		
		// get the current consumer object and assert that the serviceLevel persisted
		JSONObject jsonConsumer = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername, sm_clientPassword, sm_serverUrl, "/consumers/"+consumerId));
		Assert.assertEquals(jsonConsumer.get("serviceLevel"), serviceLevel, "The call to register with autosubscribe and a servicelevel persisted the servicelevel setting on the current consumer object.");
		
		// assert that each of the autosubscribed entitlements come from a pool that supports the specified service level
		clienttasks.listConsumedProductSubscriptions();
		for (EntitlementCert entitlementCert : clienttasks.getCurrentEntitlementCerts()) {
			if (sm_exemptServiceLevelsInUpperCase.contains(entitlementCert.orderNamespace.supportLevel.toUpperCase())) {
				log.warning("After autosubscribed registration with service level '"+serviceLevel+"', this autosubscribed entitlement provides an exempt service level '"+entitlementCert.orderNamespace.supportLevel+"' from entitled orderNamespace: "+entitlementCert.orderNamespace);
			} else {
				//CASE SENSITIVE ASSERTION Assert.assertEquals(entitlementCert.orderNamespace.supportLevel, serviceLevel,"This autosubscribed entitlement provides the requested service level '"+serviceLevel+"' from entitled orderNamespace: "+entitlementCert.orderNamespace);
				Assert.assertTrue(entitlementCert.orderNamespace.supportLevel.equalsIgnoreCase(serviceLevel),"Ignoring case, this autosubscribed entitlement provides the requested service level '"+serviceLevel+"' from entitled orderNamespace: "+entitlementCert.orderNamespace);
			}
		}
	}
	
	
	@Test(	description="subscription-manager: register with autosubscribe while specifying an valid random case SeRviCEleVel; assert the installed product status is independent of the specified service level case.",
			groups={"AcceptanceTests","blockedByBug-859652","blockedByBug-859652"},
			dataProvider="getAllAvailableServiceLevelData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void VerifyRegisterWithServiceLevelIsCaseInsensitive(Object bugzilla, String serviceLevel) {
		
		// TEMPORARY WORKAROUND FOR BUG
		if (sm_serverType.equals(CandlepinType.hosted)) {
		String bugId = "818319"; boolean invokeWorkaroundWhileBugIsOpen = true;
		try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			throw new SkipException("Hosted candlepin server '"+sm_serverHostname+"' does not yet support this test execution.");
		}
		}
		// END OF WORKAROUND
		
		// register with autosubscribe specifying a valid service level and get the installed product status
		List<InstalledProduct> installedProductsAfterAutosubscribedRegisterWithServiceLevel= InstalledProduct.parse(clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, true, serviceLevel, null, (String)null, null, null, true, null, null, null, null).getStdout());
		
		// register with autosubscribe specifying a mixed case service level and get the installed product status
		String mixedCaseServiceLevel = randomizeCaseOfCharactersInString(serviceLevel);
		List<InstalledProduct> installedProductsAfterAutosubscribedRegisterWithMixedCaseServiceLevel= InstalledProduct.parse(clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, true, mixedCaseServiceLevel, null, (String)null, null, null, true, null, null, null, null).getStdout());

		// assert that the two lists are identical (independent of the serviceLevel case specified during registration)
		Assert.assertEquals(installedProductsAfterAutosubscribedRegisterWithMixedCaseServiceLevel.size(), clienttasks.getCurrentProductCertFiles().size(), "The registration output displayed the same number of installed product status's as the number of installed product certs.");
		Assert.assertTrue(installedProductsAfterAutosubscribedRegisterWithServiceLevel.containsAll(installedProductsAfterAutosubscribedRegisterWithMixedCaseServiceLevel) && installedProductsAfterAutosubscribedRegisterWithMixedCaseServiceLevel.containsAll(installedProductsAfterAutosubscribedRegisterWithServiceLevel), "Autosubscribed registration with serviceLevel '"+mixedCaseServiceLevel+"' yielded the same installed product status as autosubscribed registration with serviceLevel '"+serviceLevel+"'.");
		
		// assert that each of the consumed ProductSubscriptions match the specified service level
		List<ProductSubscription> consumedProductSubscriptions = clienttasks.getCurrentlyConsumedProductSubscriptions();
		if (consumedProductSubscriptions.isEmpty()) log.warning("No entitlements were granted after registering with autosubscribe and service level '"+mixedCaseServiceLevel+"'."); 
		for (ProductSubscription productSubscription : consumedProductSubscriptions) {
			// tolerate exemptServiceLevels
			if (sm_exemptServiceLevelsInUpperCase.contains(productSubscription.serviceLevel.toUpperCase())) {
				log.warning("After autosubscribed registration with service level '"+mixedCaseServiceLevel+"', this consumed ProductSubscription provides an exempt service level '"+productSubscription.serviceLevel+"'.");
				continue;
			}
			
			Assert.assertTrue(productSubscription.serviceLevel.equalsIgnoreCase(mixedCaseServiceLevel),
					"After autosubscribed registration with service level '"+mixedCaseServiceLevel+"', this consumed ProductSubscription provides a service level '"+productSubscription.serviceLevel+"' that is a case insensitive match to '"+mixedCaseServiceLevel+"'.");
		}
	}
	
	
	
	
	
	
	
	@Test(	description="subscription-manager: subscribe with auto while specifying an unavailable service level",
			groups={"blockedByBug-795798","blockedByBug-864508"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void AutoSubscribeWithUnavailableServiceLevel_Test() {
		
		// register with force
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, true, false, null, null, null);
		
		// subscribe with auto specifying an unavailable service level
		SSHCommandResult result = clienttasks.subscribe_(true,"FOO",(String)null,null,null,null,null,null,null, null, null);
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(255),"Exit code from an attempt to subscribe with auto and an unavailable service level.");
		//Assert.assertEquals(result.getStdout().trim(), "Cannot set a service level for a consumer that is not available to its organization.", "Stdout from an attempt to subscribe with auto and an unavailable service level.");
		//Assert.assertEquals(result.getStdout().trim(), String.format("Service level %s is not available to consumers of organization %s.","FOO",sm_clientOrg), "Stdout from an attempt to subscribe with auto and an unavailable service level.");
		Assert.assertEquals(result.getStdout().trim(), String.format("Service level '%s' is not available to consumers of organization %s.","FOO",sm_clientOrg), "Stdout from an attempt to subscribe with auto and an unavailable service level.");
		Assert.assertEquals(result.getStderr().trim(), "", "Stderr from an attempt to subscribe with auto and an unavailable service level.");
	}
	
	
	@Test(	description="subscription-manager: subscribe with auto while specifying an valid service level; assert the entitlements granted match the requested service level",
			groups={"AcceptanceTests","blockedByBug-859652"},
			dataProvider="getAllAvailableServiceLevelData",
			enabled=true)
	@ImplementsNitrateTest(caseId=157229)	// 147971
	public void AutoSubscribeWithServiceLevel_Test(Object bugzilla, String serviceLevel) throws JSONException, Exception {
		// Reference: https://engineering.redhat.com/trac/Entitlement/wiki/SlaSubscribe
		
		// ensure system is registered
		if (clienttasks.getCurrentConsumerId()==null) clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, "AutoSubscribeWithServiceLevelConsumer", null, null, null, null, (String)null, null, null, null, false, null, null, null);
		
		// remember this initial service level preference for this consumer
		String initialConsumerServiceLevel = clienttasks.getCurrentServiceLevel();
		
		// start fresh by returning all entitlements
		clienttasks.unsubscribeFromAllOfTheCurrentlyConsumedProductSubscriptions();
		
		// autosubscribe with a valid service level
		SSHCommandResult subscribeResult = clienttasks.subscribe(true,serviceLevel,(String)null,(String)null,(String)null,null,null,null,null, null, null);
		
		// get the current consumer object and assert that the serviceLevel persisted
		JSONObject jsonConsumer = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername, sm_clientPassword, sm_serverUrl, "/consumers/"+clienttasks.getCurrentConsumerId()));
		if (serviceLevel==null || serviceLevel.equals("")) {
			Assert.assertEquals(jsonConsumer.get("serviceLevel"), initialConsumerServiceLevel, "The consumer's serviceLevel preference should remain unchanged when calling subscribe with auto and a servicelevel of null or \"\".");
		} else {
			Assert.assertEquals(jsonConsumer.get("serviceLevel"), serviceLevel, "The call to subscribe with auto and a servicelevel persisted the servicelevel setting on the current consumer object.");			
		}
		
		// assert that each of the autosubscribed entitlements come from a pool that supports the specified service level
		List<EntitlementCert> entitlementCerts = clienttasks.getCurrentEntitlementCerts();
		if (subscribeResult.getExitCode().intValue()==1) Assert.assertEquals(entitlementCerts.size(), 0, "When subscribe --auto returns an exitCode of 1, then no entitlements should have been granted.");
		for (EntitlementCert entitlementCert : entitlementCerts) {

			// tolerate exemptServiceLevels
			if (entitlementCert.orderNamespace.supportLevel!=null && sm_exemptServiceLevelsInUpperCase.contains(entitlementCert.orderNamespace.supportLevel.toUpperCase())) {
				log.warning("After autosubscribing, this EntitlementCert provides an exempt service level '"+entitlementCert.orderNamespace.supportLevel+"'.");
				continue;
			}
			
			if ((serviceLevel==null || serviceLevel.equals("")) && initialConsumerServiceLevel.equals("")) {
				log.info("When specifying a servicelevel of null or \"\" during an autosubscribe and the current consumer's has no servicelevel preference, then the servicelevel of the granted entitlement certs can be anything.  This one is '"+entitlementCert.orderNamespace.supportLevel+"'.");
			} else if ((serviceLevel==null || serviceLevel.equals("")) && !initialConsumerServiceLevel.equals("")){
				//CASE SENSITIVE ASSERTION Assert.assertEquals(entitlementCert.orderNamespace.supportLevel,initialConsumerServiceLevel, "When specifying a servicelevel of null or \"\" during an autosubscribe and the current consumer has a sericelevel preference set, then the servicelevel of the granted entitlement certs must match the current consumer's service level preference.");
				//Assert.assertTrue(entitlementCert.orderNamespace.supportLevel.equalsIgnoreCase(initialConsumerServiceLevel), "When specifying a servicelevel of null or \"\" during an autosubscribe and the current consumer has a servicelevel preference set, then the servicelevel from the orderNamespace of this granted entitlement cert ("+entitlementCert.orderNamespace.supportLevel+") must match the current consumer's service level preference ("+initialConsumerServiceLevel+").");
				Assert.assertTrue(initialConsumerServiceLevel.equalsIgnoreCase(entitlementCert.orderNamespace.supportLevel), "When specifying a servicelevel of null or \"\" during an autosubscribe and the current consumer has a servicelevel preference set, then the servicelevel from the orderNamespace of this granted entitlement cert ("+entitlementCert.orderNamespace.supportLevel+") must match the current consumer's service level preference ("+initialConsumerServiceLevel+").");
			} else {
				//CASE SENSITIVE ASSERTION Assert.assertEquals(entitlementCert.orderNamespace.supportLevel,serviceLevel, "This autosubscribed entitlement was filled from a subscription order that provides the requested service level '"+serviceLevel+"': "+entitlementCert.orderNamespace);
				//Assert.assertTrue(entitlementCert.orderNamespace.supportLevel.equalsIgnoreCase(serviceLevel), "Ignoring case, this autosubscribed entitlement was filled from a subscription order that provides the requested service level '"+serviceLevel+"': "+entitlementCert.orderNamespace);
				Assert.assertTrue(serviceLevel.equalsIgnoreCase(entitlementCert.orderNamespace.supportLevel), "Ignoring case, this autosubscribed entitlement was filled from a subscription order that provides the requested service level '"+serviceLevel+"': "+entitlementCert.orderNamespace);
			}
		}
	}
	
	@Test(	description="subscription-manager: after autosubscribing with a service level, assert that another autosubscribe (without specifying service level) uses the service level persisted from the first sutosubscribe",
			groups={"blockedByBug-859652"},
			dependsOnMethods={},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void AutoSubscribeAgainAssertingServiceLevelIsPersisted_Test() throws JSONException, Exception {
		// Reference: https://engineering.redhat.com/trac/Entitlement/wiki/SlaSubscribe

		// get all the valid service levels available to this org	
		List<String> serviceLevelsExpected = CandlepinTasks.getServiceLevelsForOrgKey(/*username or*/sm_serverAdminUsername, /*password or*/sm_serverAdminPassword, sm_serverUrl, sm_clientOrg);
		String serviceLevel = serviceLevelsExpected.get(randomGenerator.nextInt(serviceLevelsExpected.size()));
		
		//clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, serviceLevel, null, (String)null, null, false, null, null, null);
		AutoSubscribeWithServiceLevel_Test(null,serviceLevel);

		// return all entitlements
		clienttasks.unsubscribe(true, (BigInteger)null, null, null, null);
		
		// autosubscribe again without specifying a service level
		clienttasks.subscribe(true,null,(String)null,(String)null,(String)null,null,null,null,null, null, null);
		
		// get the current consumer object and assert that the serviceLevel persisted even though the subscribe did NOT specify a service level
		Assert.assertEquals(clienttasks.getCurrentServiceLevel(), serviceLevel, "The call to subscribe with auto (without specifying a servicelevel) did not alter current servicelevel.");

		// assert that each of the autosubscribed entitlements come from a pool that supports the original service level
		for (EntitlementCert entitlementCert : clienttasks.getCurrentEntitlementCerts()) {
			
			// tolerate exemptServiceLevels
			if (sm_exemptServiceLevelsInUpperCase.contains(entitlementCert.orderNamespace.supportLevel.toUpperCase())) {
				log.warning("After autosubscribing, this EntitlementCert provides an exempt service level '"+entitlementCert.orderNamespace.supportLevel+"'.");
				continue;
			}

			//CASE SENSITIVE ASSERTION Assert.assertEquals(entitlementCert.orderNamespace.supportLevel, serviceLevel,"This autosubscribed EntitlementCert was filled from a subscription order that provides the original service level '"+serviceLevel+"': "+entitlementCert.orderNamespace);
			Assert.assertTrue(entitlementCert.orderNamespace.supportLevel.equalsIgnoreCase(serviceLevel),"Ignoring case, this autosubscribed EntitlementCert was filled from a subscription order that provides the original service level '"+serviceLevel+"': "+entitlementCert.orderNamespace);
		}
	}
	@Test(	description="subscription-manager: subscribe with auto without specifying any service level; assert the service level used matches whatever the consumer's current preference level is set",
			groups={"AcceptanceTests","blockedByBug-859652"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void AutoSubscribeWithNullServiceLevel_Test() throws JSONException, Exception {
		AutoSubscribeWithServiceLevel_Test(null,null);
	}
	@Test(	description="subscription-manager: subscribe with auto specifying a service level of \"\"; assert the service level is unset and the autosubscribe proceeds without any service level preference",
			groups={"AcceptanceTests","blockedByBug-859652"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void AutoSubscribeWithBlankServiceLevel_Test() throws JSONException, Exception {
		AutoSubscribeWithServiceLevel_Test(null,"");
	}
	
 
	@Test(	description="subscription-manager: autosubscribe while specifying an valid service level; assert the installed product status is independent of the specified SerViceLeVEL case.",
			groups={"blockedByBug-818319","blockedByBug-859652","AcceptanceTests"},
			dataProvider="getAllAvailableServiceLevelData",
			enabled=true)
	@ImplementsNitrateTest(caseId=157227) // 157226 //157225
	public void VerifyAutoSubscribeWithServiceLevelIsCaseInsensitive_Test(Object bugzilla, String serviceLevel) {
		
		// TEMPORARY WORKAROUND FOR BUG
		if (sm_serverType.equals(CandlepinType.hosted)) {
		String bugId = "818319"; boolean invokeWorkaroundWhileBugIsOpen = true;
		try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			throw new SkipException("This test is blocked by Bugzilla https://bugzilla.redhat.com/show_bug.cgi?id="+bugId);
		}
		}
		// END OF WORKAROUND
		
		// system was already registered by dataProvider="getSubscribeWithAutoAndServiceLevelData"
		if (clienttasks.getCurrentConsumerId()==null) {
			clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, null, false, null, null, null);
		}
		
		// start fresh by returning all entitlements
		clienttasks.unsubscribeFromAllOfTheCurrentlyConsumedProductSubscriptions();
	
		// autosubscribe specifying a valid service level and get the installed product status
		List<InstalledProduct> installedProductsAfterAutosubscribingWithServiceLevel= InstalledProduct.parse(clienttasks.subscribe(true, serviceLevel, (String)null, (String)null, (String)null, null, null, null, null, null, null).getStdout());
		
		// unsubscribe from all entitlements
		clienttasks.unsubscribeFromAllOfTheCurrentlyConsumedProductSubscriptions();
		
		// autosubscribe specifying a mixed case service level and get the installed product status
		String mixedCaseServiceLevel = randomizeCaseOfCharactersInString(serviceLevel);
		List<InstalledProduct> installedProductsAfterAutosubscribingWithMixedCaseServiceLevel= InstalledProduct.parse(clienttasks.subscribe(true, mixedCaseServiceLevel, (String)null, (String)null, (String)null, null, null, null, null, null, null).getStdout());

		// assert that the two lists are identical (independent of the serviceLevel case specified during autosubscribe)
		Assert.assertEquals(installedProductsAfterAutosubscribingWithMixedCaseServiceLevel.size(), clienttasks.getCurrentProductCertFiles().size(), "The subscribe output displayed the same number of installed product status's as the number of installed product certs.");
		Assert.assertTrue(installedProductsAfterAutosubscribingWithServiceLevel.containsAll(installedProductsAfterAutosubscribingWithMixedCaseServiceLevel) && installedProductsAfterAutosubscribingWithMixedCaseServiceLevel.containsAll(installedProductsAfterAutosubscribingWithServiceLevel), "Autosubscribe with serviceLevel '"+mixedCaseServiceLevel+"' yielded the same installed product status as autosubscribe with serviceLevel '"+serviceLevel+"'.");
		
		// assert that each of the consumed ProductSubscriptions match the specified service level
		List<ProductSubscription> consumedProductSubscriptions = clienttasks.getCurrentlyConsumedProductSubscriptions();
		if (consumedProductSubscriptions.isEmpty()) log.warning("No entitlements were granted after autosubscribing with service level '"+mixedCaseServiceLevel+"'."); 
		for (ProductSubscription productSubscription : consumedProductSubscriptions) {
			
			// tolerate exemptServiceLevels
			if (sm_exemptServiceLevelsInUpperCase.contains(productSubscription.serviceLevel.toUpperCase())) {
				log.warning("After autosubscribe with service level '"+mixedCaseServiceLevel+"', this consumed ProductSubscription provides an exempt service level '"+productSubscription.serviceLevel+"'.");
				continue;
			}
			
			Assert.assertTrue(productSubscription.serviceLevel.equalsIgnoreCase(mixedCaseServiceLevel),
						"After autosubscribe with service level '"+mixedCaseServiceLevel+"', this consumed ProductSubscription provides a service level '"+productSubscription.serviceLevel+"' that is a case insensitive match to '"+mixedCaseServiceLevel+"'.");
		}
	}
	
	
	
	@Test(	description="installed products provided by available pools with an exempt service level should be auto-subscribed regardless of what service level is specified (or is not specified)",
			groups={"blockedByBug-818319","AcceptanceTests","blockedByBug-859652"},
			dataProvider="getExemptInstalledProductAndServiceLevelData",
			enabled=true)
	@ImplementsNitrateTest(caseId=157229)
	public void VerifyInstalledProductsProvidedByAvailablePoolsWithExemptServiceLevelAreAutoSubscribedRegardlessOfServiceLevel_Test(Object bugzilla, String installedProductId, String serviceLevel) {
		
		// randomize the case of the service level
		String seRvICElevEl = randomizeCaseOfCharactersInString(serviceLevel);
		log.info("This test will be conducted with a randomized serviceLevel value: "+seRvICElevEl);

		// TEMPORARY WORKAROUND FOR BUG
		if (sm_serverType.equals(CandlepinType.hosted)) {
		String bugId = "818319"; boolean invokeWorkaroundWhileBugIsOpen = true;
		try {if (invokeWorkaroundWhileBugIsOpen&&BzChecker.getInstance().isBugOpen(bugId)) {log.fine("Invoking workaround for "+BzChecker.getInstance().getBugState(bugId).toString()+" Bugzilla "+bugId+".  (https://bugzilla.redhat.com/show_bug.cgi?id="+bugId+")");} else {invokeWorkaroundWhileBugIsOpen=false;}} catch (XmlRpcException xre) {/* ignore exception */} catch (RuntimeException re) {/* ignore exception */}
		if (invokeWorkaroundWhileBugIsOpen) {
			seRvICElevEl = serviceLevel;
			log.warning("This test will NOT be conducted with a randomized serviceLevel value.  Testing with serviceLevel: "+seRvICElevEl);
		}
		}
		// END OF WORKAROUND
		
		// register with autosubscribe and a randomize case serviceLevel
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, true, seRvICElevEl, null, (List<String>)null, null, null, true, false, null, null, null);
		
		// assert that the installed ProductId is "Subscribed"
		InstalledProduct installedProduct = InstalledProduct.findFirstInstanceWithMatchingFieldFromList("productId", installedProductId, clienttasks.getCurrentlyInstalledProducts());
		Assert.assertEquals(installedProduct.status, "Subscribed", "After registering with autosubscribe and serviceLevel '"+seRvICElevEl+"' the following installed exempt product should be subscribed: "+installedProduct);
		
		// EXTRA CREDIT: assert that the consumed ProductSubscription that provides the installed exempt product is among the known exempt service levels.
		// WARNING: THIS MAY NOT BE AN APPROPRIATE ASSERTION WHEN THE EXEMPT PRODUCT HAPPENS TO ALSO BE PROVIDED BY A SUBSCRIPTION THAT COINCIDENTY PROVIDES ANOTHER INSTALLED PRODUCT
		List<ProductSubscription> productSubscriptions = clienttasks.getCurrentlyConsumedProductSubscriptions();
		for (EntitlementCert entitlementCert : clienttasks.getCurrentEntitlementCerts()) {
			for (ProductNamespace productNamespace : entitlementCert.productNamespaces) {
				if (productNamespace.id.equals(installedProductId)) {
					BigInteger serialNumber = clienttasks.getSerialNumberFromEntitlementCertFile(entitlementCert.file);
					ProductSubscription productSubscription = ProductSubscription.findFirstInstanceWithMatchingFieldFromList("serialNumber", serialNumber, productSubscriptions);
					Assert.assertTrue(sm_exemptServiceLevelsInUpperCase.contains(productSubscription.serviceLevel.toUpperCase()),"Installed exempt product '"+installedProduct+"' is entitled by consumed productSubscription '"+productSubscription+"' that provides one of the exempt service levels '"+sm_exemptServiceLevelsInUpperCase+"'.");
				}
			}
		}
	}
		
	
	@Test(	description="Using curl, set the default service level for an org and then register using org credentials to verify consumer's service level",
			groups={},
			dataProvider="getAllAvailableServiceLevelData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void SetDefaultServiceLevelForOrgAndRegister_Test(Object bugzilla, String defaultServiceLevel) throws JSONException, Exception {
		
		// update the defaultServiceLevel on the Org
		String org = clienttasks.getCurrentlyRegisteredOwnerKey();
		JSONObject jsonOrg = CandlepinTasks.setAttributeForOrg(sm_clientUsername, sm_clientPassword, sm_serverUrl, org, "defaultServiceLevel", defaultServiceLevel);
		Assert.assertEquals(jsonOrg.get("defaultServiceLevel"), defaultServiceLevel, "The defaultServiceLevel update to org '"+org+"' appears successful on the candlepin server.");
		
		// register and assert the consumer's service level is set to the new org default
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, true, null, null, null, null);
		Assert.assertEquals(clienttasks.getCurrentServiceLevel(), defaultServiceLevel, "Immediately upon registering, the consumer's service level preference was set to the org's default.");
	}
	
	
	@Test(	description="Using curl, unset the default service level for an org and then register using org credentials to verify consumer's service level is not set",
			groups={},
			dependsOnMethods={"SetDefaultServiceLevelForOrgAndRegister_Test"}, alwaysRun=true,
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void UnsetDefaultServiceLevelForOrgAndRegister_Test() throws JSONException, Exception {
		
		// update the defaultServiceLevel on the Org (setting to "" will nullify the attribute on the org; setting to JSONObject.NULL does not work)
		JSONObject jsonOrg = CandlepinTasks.setAttributeForOrg(sm_clientUsername, sm_clientPassword, sm_serverUrl, sm_clientOrg, "defaultServiceLevel", "");
		Assert.assertEquals(jsonOrg.get("defaultServiceLevel"), JSONObject.NULL, "The defaultServiceLevel update to the org appears successful on the candlepin server.");
		
		// register and assert the consumer's service level is not set
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, true, null, null, null, null);
		Assert.assertEquals(clienttasks.getCurrentServiceLevel(), "", "Immediately upon registering, the consumer's service level preference was set to the org's default (which was unset).");
	}
	
	
	
	
	@Test(	description="subscription-manager: service-level --set (with unavailable serviceLevel)",
			groups={"blockedByBug-864508"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void ServiceLevelSetWithUnavailableServiceLevel_Test() throws JSONException, Exception {
			
		// test while registered
		if (clienttasks.getCurrentConsumerId()==null) clienttasks.register(sm_clientUsername,sm_clientPassword,sm_clientOrg);
		String org = clienttasks.getCurrentlyRegisteredOwnerKey();	// current org (in case sm_clientOrg is null)
		
		String unavailableSericeLevel = "FOO";
		SSHCommandResult result = clienttasks.service_level_(null, null, unavailableSericeLevel, null, null, null, null, null, null, null, null);
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(255), "ExitCode from service-level --set with unavailable serviceLevel");
		//Assert.assertEquals(result.getStderr().trim(), String.format("Service level %s is not available to consumers of organization %s.",unavailableSericeLevel,org), "Stderr from service-level --set with unavailable serviceLevel");
		Assert.assertEquals(result.getStderr().trim(), String.format("Service level '%s' is not available to consumers of organization %s.",unavailableSericeLevel,org), "Stderr from service-level --set with unavailable serviceLevel");
		Assert.assertEquals(result.getStdout().trim(), "", "Stdout from service-level --set with unavailable serviceLevel");
	}
	
	
	@Test(	description="subscription-manager: service-level --set (with exempt serviceLevel should be treated as unavailable)",
			groups={"blockedByBug-864508"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void ServiceLevelSetWithExemptServiceLevel_Test() {
			
		// test while registered
		if (clienttasks.getCurrentConsumerId()==null) clienttasks.register(sm_clientUsername,sm_clientPassword,sm_clientOrg,null,null,null,null,null,null,null,(List<String>)null,null,null,null,null, null, null, null);
		
		String exemptSericeLevel = sm_exemptServiceLevelsInUpperCase.get(SubscriptionManagerCLITestScript.randomGenerator.nextInt(sm_exemptServiceLevelsInUpperCase.size()));
		SSHCommandResult result = clienttasks.service_level_(null, null, exemptSericeLevel, null, null, null, null, null, null, null, null);
		log.info("An exempt service level should be treated as an unavailable service level when attempting to set.");
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(255), "ExitCode from service-level --set with exempt serviceLevel");
		//Assert.assertEquals(result.getStderr().trim(), String.format("Service level %s is not available to consumers of organization admin.",exemptSericeLevel), "Stderr from service-level --set with exempt serviceLevel");
		Assert.assertEquals(result.getStderr().trim(), String.format("Service level '%s' is not available to consumers of organization admin.",exemptSericeLevel), "Stderr from service-level --set with exempt serviceLevel");
		Assert.assertEquals(result.getStdout().trim(), "", "Stdout from service-level --set with exempt serviceLevel");
	}
	
	
	@Test(	description="subscription-manager: service-level --set (with available serviceLevel)",
			groups={},
			dataProvider="getAllAvailableServiceLevelData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void ServiceLevelSetWithAvailableServiceLevel_Test(Object bugzilla, String serviceLevel) {
		
		// no need to register ("getAllAvailableServiceLevelData" will re-register with force)
		
		clienttasks.service_level(null, null, serviceLevel, null, null, null, null, null, null, null, null);
		Assert.assertEquals(clienttasks.getCurrentServiceLevel(), serviceLevel, "The --set serviceLevel matches the current --show serviceLevel.");
	}
	
	
	@Test(	description="subscription-manager: service-level --set should accept \"\" to effectively unset",
			groups={"blockedByBug-835050"},
			dataProvider="getAllAvailableServiceLevelData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void UnsetServiceLevel_Test(Object bugzilla, String serviceLevel) {
		
		ServiceLevelSetWithAvailableServiceLevel_Test(bugzilla,serviceLevel);
		clienttasks.service_level(null, null, "", null, null, null, null, null, null, null, null);
		Assert.assertEquals(clienttasks.getCurrentServiceLevel(), "", "The serviceLevel can effectively be unset by setting a value of \"\".");
	}
	
	
	@Test(	description="subscription-manager: service-level --set (with case insensitivity) is preserved throughtout an identity regeneration",
			groups={},
			dataProvider="getAllAvailableServiceLevelData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void ServiceLevelSetWithAvailableServiceLevelIsPreservedThroughIdentityRegeneration_Test(Object bugzilla, String serviceLevel) throws JSONException, Exception {
		
		// no need to register ("getAllAvailableServiceLevelData" will re-register with force)
		
		serviceLevel = randomizeCaseOfCharactersInString(serviceLevel);
		clienttasks.service_level(null, null, serviceLevel, null, null, null, null, null, null, null, null);
		clienttasks.identity(null,null,true,null,null,null,null);
		Assert.assertEquals(clienttasks.getCurrentServiceLevel(), serviceLevel, "The --set serviceLevel matches the current --show serviceLevel even after an identity regeneration.");
	}
	
	
	@Test(	description="subscription-manager: service-level --unset after setting an available service level",
			groups={"blockedByBug-829803","blockedByBug-829812"},
			dataProvider="getAllAvailableServiceLevelData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void ServiceLevelUnsetAfterSet_Test(Object bugzilla, String serviceLevel) {
		
		ServiceLevelSetWithAvailableServiceLevel_Test(bugzilla,serviceLevel);
		clienttasks.service_level(null, null, null, true, null, null, null, null, null, null, null);
		Assert.assertEquals(clienttasks.getCurrentServiceLevel(), "", "The serviceLevel is unset after calling with the --unset option.");
	}
	
	
	@Test(	description="subscription-manager: service-level --list should be unique (regardless of case)",
			groups={/*"blockedByBug-845043",*/"AcceptanceTests"},
			dataProvider="getRegisterCredentialsExcludingNullOrgData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void ServiceLevelListIsUnique_Test(String username, String password, String org) {
		
		clienttasks.register(username, password, org,null,null,null,null,null,null,null,(String)null,null,null,true,null,null,null,null);
		
		List<String> availServiceLevels = clienttasks.getCurrentlyAvailableServiceLevels();
		Set<String> uniqueServiceLevels = new HashSet<String>();
		for (String availServiceLevel : availServiceLevels) uniqueServiceLevels.add(availServiceLevel.toUpperCase());
		
		Assert.assertTrue(uniqueServiceLevels.size()==availServiceLevels.size(), "The available service levels are unique. There are no duplicates (regardless of case).");
	}
	
	
	
	protected String server_hostname = null;
	protected String server_port = null;
	protected String server_prefix = null;
	protected String clientOrg = null;
	@BeforeGroups(value={"ServiceLevelListWithServerurl_Test"}, groups={"setup"})
	public void beforeServiceLevelListWithServerurl_Test() {
		if (clienttasks==null) return;
		server_hostname	= clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "server", "hostname");
		server_port		= clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "server", "port");
		server_prefix	= clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "server", "prefix");
		clientOrg 		= clienttasks.getOrgs(sm_clientUsername,sm_clientPassword).get(0).orgKey;	// use the first org
	}
	@Test(	description="subscription-manager: service-level --list with --serverurl",
			dataProvider="getServerurl_TestData",
			groups={"ServiceLevelListWithServerurl_Test"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void ServiceLevelListWithServerurl_Test(Object bugzilla, String serverurl, String expectedHostname, String expectedPort, String expectedPrefix, Integer expectedExitCode, String expectedStdoutRegex, String expectedStderrMatch) {
		// get original server at the beginning of this test
		String hostnameBeforeTest	= clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "hostname");
		String portBeforeTest		= clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "port");
		String prefixBeforeTest		= clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "prefix");
		
		// service-level --list with a serverurl
		SSHCommandResult sshCommandResult = clienttasks.service_level_(null,true,null,null,sm_clientUsername,sm_clientPassword,clientOrg,serverurl, null, null, null);
		
		// assert the sshCommandResult here
		if (expectedExitCode!=null)	Assert.assertEquals(sshCommandResult.getExitCode(), expectedExitCode,"ExitCode after register with --serverurl="+serverurl+" and other options:");
		if (expectedStdoutRegex!=null)	Assert.assertContainsMatch(sshCommandResult.getStdout().trim(), expectedStdoutRegex,"Stdout after register with --serverurl="+serverurl+" and other options:");
		if (expectedStderrMatch!=null)	Assert.assertContainsMatch(sshCommandResult.getStderr().trim(), expectedStderrMatch,"Stderr after register with --serverurl="+serverurl+" and other options:");
		Assert.assertContainsNoMatch(sshCommandResult.getStderr().trim(), "Traceback.*","Stderr after register with --serverurl="+serverurl+" and other options should not contain a Traceback.");
		
		// negative testcase assertions........
		if (expectedExitCode.equals(new Integer(255))) {
			// assert that the current config remains unchanged when the expectedExitCode is 255
			Assert.assertEquals(clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "hostname"), hostnameBeforeTest, "The "+clienttasks.rhsmConfFile+" configuration for [server] hostname should remain unchanged when attempting to register with an invalid serverurl.");
			Assert.assertEquals(clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "port"),	portBeforeTest, "The "+clienttasks.rhsmConfFile+" configuration for [server] port should remain unchanged when attempting to register with an invalid serverurl.");
			Assert.assertEquals(clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "prefix"), prefixBeforeTest, "The "+clienttasks.rhsmConfFile+" configuration for [server] prefix should remain unchanged when attempting to register with an invalid serverurl.");
						
			return;	// nothing more to do after these negative testcase assertions
		}
		
		// positive testcase assertions........
		Assert.assertEquals(clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "hostname"), expectedHostname, "The "+clienttasks.rhsmConfFile+" configuration for [server] hostname has been updated from the specified --serverurl "+serverurl);
		Assert.assertEquals(clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "port"), expectedPort, "The "+clienttasks.rhsmConfFile+" configuration for [server] port has been updated from the specified --serverurl "+serverurl);
		Assert.assertEquals(clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "prefix"), expectedPrefix, "The "+clienttasks.rhsmConfFile+" configuration for [server] prefix has been updated from the specified --serverurl "+serverurl);
	}
	@AfterGroups(value={"ServiceLevelListWithServerurl_Test"},groups={"setup"})
	public void afterRegisterWithServerurl_Test() {
		if (server_hostname!=null)	clienttasks.config(null,null,true,new String[]{"server","hostname",server_hostname});
		if (server_port!=null)		clienttasks.config(null,null,true,new String[]{"server","port",server_port});
		if (server_prefix!=null)	clienttasks.config(null,null,true,new String[]{"server","prefix",server_prefix});
	}
	

	
	
	
	
	
	
	

	// Candidates for an automated Test:
	
	
		
	// Configuration methods ***********************************************************************
	@BeforeClass(groups="setup")
	public void createSubscriptionsProvidingProductWithExemptServiceLevels() throws JSONException, Exception {
		String name,productId, serviceLevel;
		List<String> providedProductIds = new ArrayList<String>();
		Map<String,String> attributes = new HashMap<String,String>();
		JSONObject jsonEngProduct, jsonMktProduct, jsonSubscription;
		if (server==null) {
			log.warning("Skipping createSubscriptionsWithVariationsOnProductAttributeSockets() when server is null.");
			return;	
		}
	
		// Subscription with an exempt_support_level
		serviceLevel = "Exempt SLA";
		name = "An \""+serviceLevel+"\" service level subscription (matches all service levels)";
		productId = "exempt-sla-product-sku";
		providedProductIds.clear();
		providedProductIds.add("99000");
		attributes.clear();
		attributes.put("support_level", serviceLevel);
		attributes.put("support_level_exempt", "true");
		attributes.put("version", "0.1");
		//attributes.put("variant", "server");
		attributes.put("arch", "ALL");
		attributes.put("warning_period", "45");
		// delete already existing subscription and products
		CandlepinTasks.deleteSubscriptionsAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, productId);
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/products/"+productId);
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/products/"+providedProductIds.get(0));
		// create a new engineering product, marketing product that provides the engineering product, and a subscription for the marketing product
		attributes.put("type", "SVC");
		CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "Exempt Product Bits", providedProductIds.get(0), 1, attributes, null);
		attributes.put("type", "MKT");
		CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, name, productId, 1, attributes, null);
		CandlepinTasks.createSubscriptionAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, 20, -1*24*60/*1 day ago*/, 15*24*60/*15 days from now*/, getRandInt(), getRandInt(), productId, providedProductIds);
		// now install the product certificate
		JSONObject jsonProductCert = new JSONObject (CandlepinTasks.getResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/products/"+providedProductIds.get(0)+"/certificate"));
		String cert = jsonProductCert.getString("cert");
		String key = jsonProductCert.getString("key");
		client.runCommandAndWait("echo \""+cert+"\" > "+clienttasks.productCertDir+"/TestExemptProduct"+providedProductIds.get(0)+"_.pem");
		
		// add this exempt service level to the script parameter for exempt service levels
		if (!sm_exemptServiceLevelsInUpperCase.contains(serviceLevel.toUpperCase())) {
			sm_exemptServiceLevelsInUpperCase.add(serviceLevel.toUpperCase());
		}
	}

	
	@BeforeClass(groups="setup")
	public void createMultipleSubscriptionsWithSameServiceLevels() throws JSONException, Exception {
		String name,productId, serviceLevel;
		List<String> providedProductIds = new ArrayList<String>();
		Map<String,String> attributes = new HashMap<String,String>();
		JSONObject jsonEngProduct, jsonMktProduct, jsonSubscription;
		if (server==null) {
			log.warning("Skipping createSubscriptionsWithDuplicateServiceLevels() when server is null.");
			return;	
		}
	
		// Subscription with "Ultimate SLA" support_level
		serviceLevel = "Ultimate SLA";
		name = "The \""+serviceLevel+"\" service level subscription";
		productId = "ultimate-sla-product-sku-1";
		providedProductIds.clear();
		attributes.clear();
		attributes.put("support_level", serviceLevel);
		attributes.put("support_level_exempt", "false");
		attributes.put("version", "0.1");
		//attributes.put("variant", "server");
		attributes.put("arch", "ALL");
		attributes.put("warning_period", "5");
		// delete already existing subscription
		CandlepinTasks.deleteSubscriptionsAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, productId);
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/products/"+productId);
		// create a new marketing product, and a subscription for the marketing product
		attributes.put("type", "MKT");
		CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, name, productId, 1, attributes, null);
		CandlepinTasks.createSubscriptionAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, 20, -1*24*60/*1 day ago*/, 15*24*60/*15 days from now*/, getRandInt(), getRandInt(), productId, providedProductIds);

		// Subscription with "ultimate sla" support_level
		serviceLevel = "ultimate sla";
		name = "The \""+serviceLevel+"\" service level subscription";
		productId = "ultimate-sla-product-sku-2";
		providedProductIds.clear();
		attributes.clear();
		attributes.put("support_level", serviceLevel);
		attributes.put("support_level_exempt", "false");
		attributes.put("version", "0.2");
		//attributes.put("variant", "server");
		attributes.put("arch", "ALL");
		attributes.put("warning_period", "5");
		// delete already existing subscription
		CandlepinTasks.deleteSubscriptionsAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, productId);
		CandlepinTasks.deleteResourceUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, "/products/"+productId);
		// create a new marketing product, and a subscription for the marketing product
		attributes.put("type", "MKT");
		CandlepinTasks.createProductUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, name, productId, 1, attributes, null);
		CandlepinTasks.createSubscriptionAndRefreshPoolsUsingRESTfulAPI(sm_serverAdminUsername, sm_serverAdminPassword, sm_serverUrl, sm_clientOrg, 20, -1*24*60/*1 day ago*/, 15*24*60/*15 days from now*/, getRandInt(), getRandInt(), productId, providedProductIds);
	}
	
	
	// Protected methods ***********************************************************************



	
	// Data Providers ***********************************************************************
	

	@DataProvider(name="getExemptInstalledProductAndServiceLevelData")
	public Object[][] getExemptInstalledProductAndServiceLevelDataAs2dArray() throws JSONException, Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getExemptInstalledProductAndServiceLevelDataAsListOfLists());
	}
	/**
	 * @return List of [Object bugzilla, String installedProductId, String serviceLevel]
	 */
	protected List<List<Object>>getExemptInstalledProductAndServiceLevelDataAsListOfLists() throws JSONException, Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
	
		// get the available service levels
		List<String> serviceLevels = new ArrayList<String>();
		for (List<Object> availableServiceLevelData : getAllAvailableServiceLevelDataAsListOfLists()) {
			// availableServiceLevelData :   Object bugzilla, String serviceLevel
			serviceLevels.add((String)availableServiceLevelData.get(1));
		}
		
		// get all of the installed products
		List<ProductCert> installedProductCerts = clienttasks.getCurrentProductCerts();
		
		// process the available SubscriptionPools looking for provided productIds from a pool that has an exempt service level
		for (List<Object> subscriptionPoolsData : getAvailableSubscriptionPoolsDataAsListOfLists()) {
			SubscriptionPool subscriptionPool = (SubscriptionPool) subscriptionPoolsData.get(0);

			JSONObject jsonPool = new JSONObject(CandlepinTasks.getResourceUsingRESTfulAPI(sm_clientUsername,sm_clientPassword,sm_serverUrl,"/pools/"+subscriptionPool.poolId));	
			JSONArray jsonProvidedProducts = jsonPool.getJSONArray("providedProducts");
			String value = CandlepinTasks.getPoolProductAttributeValue(jsonPool, "support_level_exempt");
			
			// skip this subscriptionPool when "support_level_exempt" productAttribute is NOT true
			if (value==null) continue;
			if (!Boolean.valueOf(value)) continue;
			
			// process each of the provided products (exempt) to see if it is installed
			for (int i = 0; i < jsonProvidedProducts.length(); i++) {
				JSONObject jsonProvidedProduct = (JSONObject) jsonProvidedProducts.get(i);
				String productId = jsonProvidedProduct.getString("productId");
				
				// is productId installed?
				if (ProductCert.findFirstInstanceWithMatchingFieldFromList("productId", productId, installedProductCerts)!=null) {
					// found an installed exempt product, add a row for each of the service levels
					for (String serviceLevel : serviceLevels) {
						// Object bugzilla, String installedProductId, String serviceLevel
						ll.add(Arrays.asList(new Object[]{null, productId, serviceLevel}));
					}
				}
			}
		}
		
		return ll;
	}
}

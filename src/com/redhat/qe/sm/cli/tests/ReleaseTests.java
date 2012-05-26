package com.redhat.qe.sm.cli.tests;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.redhat.qe.auto.testng.Assert;
import com.redhat.qe.sm.base.CandlepinType;
import com.redhat.qe.sm.base.SubscriptionManagerCLITestScript;
import com.redhat.qe.sm.cli.tasks.CandlepinTasks;
import com.redhat.qe.sm.data.EntitlementCert;
import com.redhat.qe.sm.data.InstalledProduct;
import com.redhat.qe.sm.data.ProductCert;
import com.redhat.qe.sm.data.Repo;
import com.redhat.qe.sm.data.SubscriptionPool;
import com.redhat.qe.sm.data.YumRepo;
import com.redhat.qe.tools.SSHCommandResult;

/**
 * @author jsefler
 *
 */

@Test(groups={"ReleaseTests","AcceptanceTest"})
public class ReleaseTests extends SubscriptionManagerCLITestScript {

	
	// Test methods ***********************************************************************
	
	@Test(	description="attempt to get the subscription-manager release when not registered",
			groups={},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void AttemptToGetReleaseWhenNotRegistered_Test() {
		
		// make sure we are not registered
		clienttasks.unregister(null, null, null);
		
		SSHCommandResult result;
		
		result = clienttasks.release_(null, null, null, null, null);
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(255), "ExitCode from calling release without being registered");
		Assert.assertEquals(result.getStdout().trim(), clienttasks.msg_ConsumerNotRegistered, "Stdout from release without being registered");

		result = clienttasks.release_(true, null, null, null, null);
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(255), "ExitCode from calling release --list without being registered");
		Assert.assertEquals(result.getStdout().trim(), clienttasks.msg_ConsumerNotRegistered, "Stdout from release without being registered");

		result = clienttasks.release_(null, "FOO", null, null, null);
		Assert.assertEquals(result.getExitCode(), Integer.valueOf(255), "ExitCode from calling release --set without being registered");
		Assert.assertEquals(result.getStdout().trim(), clienttasks.msg_ConsumerNotRegistered, "Stdout from release without being registered");
	}
	
	
	@Test(	description="attempt to get the subscription-manager release when a release has not been set; should be told that the release is not set",
			groups={},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void AttemptToGetReleaseWhenReleaseHasNotBeenSet_Test() {
		
		// make sure we are newly registered
		clienttasks.register(sm_clientUsername,sm_clientPassword,sm_clientOrg,null,null,null,null,null,null,null,(List<String>)null,true,null,null,null, null);		
		SSHCommandResult result = clienttasks.release(null, null, null, null, null);
		Assert.assertEquals(result.getStdout().trim(), "Release not set", "Stdout from release without having set it");
	}
	
	
	@Test(	description="attempt to get the subscription-manager release list without having subscribed to any entitlements",
			groups={},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void AttemptToGetReleaseListWithoutHavingAnyEntitlements_Test() {
		
		// make sure we are newly registered
		clienttasks.register(sm_clientUsername,sm_clientPassword,sm_clientOrg,null,null,null,null,null,null,null,(List<String>)null,true,null,null,null, null);		

		Assert.assertTrue(clienttasks.getCurrentlyAvailableReleases(null, null, null).isEmpty(), "No releases should be available without having been entitled to anything.");
	}
	
	
	@Test(	description="verify that the consumer's current subscription-manager release value matches the release value just set",
			groups={"blockedByBug-814385"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void GetTheReleaseAfterSettingTheRelease_Test() {
		
		// make sure we are newly registered
		clienttasks.register(sm_clientUsername,sm_clientPassword,sm_clientOrg,null,null,null,null,null,null,null,(List<String>)null,true,null,null,null, null);		

		clienttasks.release(null, "Foo", null, null, null);
		Assert.assertEquals(clienttasks.getCurrentRelease(), "Foo", "The release value retrieved after setting the release.");
	}
	
	
	@Test(	description="assert that the subscription-manager release can be unset",
			groups={"blockedByBug-807822","blockedByBug-814385"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void UnsetTheReleaseAfterSettingTheRelease_Test() {
		
		// make sure we are newly registered
		clienttasks.register(sm_clientUsername,sm_clientPassword,sm_clientOrg,null,null,null,null,null,null,null,(List<String>)null,true,null,null,null, null);		

		clienttasks.release(null, "Foo", null, null, null);
		SSHCommandResult result = clienttasks.release(null, "", null, null, null);
		//Assert.assertEquals(result.getStdout().trim(), "Release not set", "Stdout from release after attempting to unset it.");	// DEV choose to implement this differently https://bugzilla.redhat.com/show_bug.cgi?id=807822#c2
		Assert.assertEquals(clienttasks.getCurrentRelease(), "", "The release value retrieved after attempting to unset it.");
	}
	
	
	@Test(	description="after subscribing to all available subscriptions, assert that content with url paths that reference $releasever are substituted with the consumers current release preference",
			groups={"blockedByBug-807407"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void VerifyReleaseverSubstitutionInRepoLists_Test() throws JSONException, Exception {
		
		// make sure we are newly registered
		clienttasks.register(sm_clientUsername,sm_clientPassword,sm_clientOrg,null,null,null,null,null,null,null,(List<String>)null,true,null,null,null, null);

		// subscribe to all available subscriptions
		clienttasks.subscribeToTheCurrentlyAvailableSubscriptionPoolsCollectively();
		
		// get current list of Repos and YumRepos before setting a release version preference
		List<Repo> reposBeforeSettingReleaseVer = clienttasks.getCurrentlySubscribedRepos();
		List<YumRepo> yumReposBeforeSettingReleaseVer = clienttasks.getCurrentlySubscribedYumRepos();
		
		boolean skipTest = true;
		for (Repo repo : reposBeforeSettingReleaseVer) {
			if (repo.repoUrl.contains("$releasever")) {
				skipTest = false; break;
			}
		}
		if (skipTest) throw new SkipException("After subscribing to all available subscriptions, could not find any enabled content with a repoUrl that employs $releasever");

		Assert.assertEquals(reposBeforeSettingReleaseVer.size(), yumReposBeforeSettingReleaseVer.size(), "The subscription-manager repos list count should match the yum reposlist count.");
		
		// now let's set a release version preference
		String releaseVer = "TestRelease-1.0";
		clienttasks.release(null, releaseVer, null, null, null);

		// assert that each of the Repos after setting a release version preference substitutes the $releasever
		for (Repo repoAfter : clienttasks.getCurrentlySubscribedRepos()) {
			Repo repoBefore = Repo.findFirstInstanceWithMatchingFieldFromList("repoName", repoAfter.repoName, reposBeforeSettingReleaseVer);
			Assert.assertNotNull(repoBefore,"Found the the same repoName from the subscription-manager repos --list after setting a release version preference.");
			
			if (!repoBefore.repoUrl.contains("$releasever")) {
				Assert.assertEquals(repoAfter.repoUrl, repoBefore.repoUrl,
						"After setting a release version preference, the subscription-manager repos --list reported repoUrl for '"+repoAfter.repoName+"' should remain unchanged since it did not contain the yum $releasever variable.");
			} else {
				Assert.assertEquals(repoAfter.repoUrl, repoBefore.repoUrl.replaceAll("\\$releasever", releaseVer),
						"After setting a release version preference, the subscription-manager repos --list reported repoUrl for '"+repoAfter.repoName+"' should have a variable substitution for $releasever.");
			}
		}
		
		
		// assert that each of the YumRepos after setting a release version preference actually substitutes the $releasever
		for (YumRepo yumRepoAfter : clienttasks.getCurrentlySubscribedYumRepos()) {
			YumRepo yumRepoBefore = YumRepo.findFirstInstanceWithMatchingFieldFromList("id", yumRepoAfter.id, yumReposBeforeSettingReleaseVer);
			Assert.assertNotNull(yumRepoBefore,"Found the the same repo id from the yum repolist after setting a release version preference.");
			
			if (!yumRepoBefore.baseurl.contains("$releasever")) {
				Assert.assertEquals(yumRepoAfter.baseurl, yumRepoBefore.baseurl,
						"After setting a release version preference, the yum repolist reported baseurl for '"+yumRepoAfter.id+"' should remain unchanged since it did not contain the yum $releasever variable.");
			} else {
				Assert.assertEquals(yumRepoAfter.baseurl, yumRepoBefore.baseurl.replaceAll("\\$releasever", releaseVer),
						"After setting a release version preference, the yum repolist reported baseurl for '"+yumRepoAfter.id+"' should have a variable substitution for $releasever.");
			}
		}
		
		// now let's unset the release version preference
		clienttasks.release(null, "", null, null, null);
		
		// assert that each of the Repos and YumRepos after unsetting the release version preference where restore to their original values (containing $releasever)
		List<Repo> reposAfterSettingReleaseVer = clienttasks.getCurrentlySubscribedRepos();
		List<YumRepo> yumReposAfterSettingReleaseVer = clienttasks.getCurrentlySubscribedYumRepos();
		
		Assert.assertTrue(reposAfterSettingReleaseVer.containsAll(reposBeforeSettingReleaseVer) && reposBeforeSettingReleaseVer.containsAll(reposAfterSettingReleaseVer),
				"After unsetting the release version preference, all of the subscription-manager repos --list were restored to their original values.");
		Assert.assertTrue(yumReposAfterSettingReleaseVer.containsAll(yumReposBeforeSettingReleaseVer) && yumReposBeforeSettingReleaseVer.containsAll(yumReposAfterSettingReleaseVer),
				"After unsetting the release version preference, all of the yum repolist were restored to their original values.");
	}
	
	
	@Test(	description="register to a RHEL subscription and verify that release --list matches the expected CDN listing for this x-stream release of RHEL",
			groups={"blockedByBug-818298","blockedByBug-820639"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void VerifyReleaseListMatchesCDN_Test() throws JSONException, Exception {

		// make sure we are newly registered
		clienttasks.register(sm_clientUsername,sm_clientPassword,sm_clientOrg,null,null,null,null,null,null,null,(List<String>)null,true,null,null,null, null);

		// get the current base RHEL product cert
		String providingTag = "rhel-"+clienttasks.redhatReleaseX;
		List<ProductCert> rhelProductCerts = clienttasks.getCurrentProductCerts(providingTag);
		Assert.assertEquals(rhelProductCerts.size(), 1, "Only one product cert is installed that provides RHEL tag '"+providingTag+"'");
		ProductCert rhelProductCert = rhelProductCerts.get(0);
		
		// find an available RHEL subscription pool that provides for this base RHEL product cert
		List<SubscriptionPool> rhelSubscriptionPools = clienttasks.getCurrentlyAvailableSubscriptionPools(rhelProductCert.productId, sm_serverUrl);
		if (rhelSubscriptionPools.isEmpty()) throw new SkipException("Cannot find an available SubscriptionPool that provides for this installed RHEL Product: "+rhelProductCert);
		SubscriptionPool rhelSubscriptionPool = rhelSubscriptionPools.get(0);	// choose one
		
		// subscribe to the RHEL subscription
		File rhelEntitlementCertFile = clienttasks.subscribeToSubscriptionPool(rhelSubscriptionPool);
		File rhelEntitlementCertKeyFile = clienttasks.getEntitlementCertKeyFileCorrespondingToEntitlementCertFile(rhelEntitlementCertFile);
		
		// get the currently expected release listing based on the currently enabled repos
		List<String> expectedReleases = clienttasks.getCurrentlyExpectedReleases();
		
		// get the actual release listing
		List<String> actualReleases = clienttasks.getCurrentlyAvailableReleases(null,null,null);
		
		// assert that they are equivalent
		Assert.assertTrue(expectedReleases.containsAll(actualReleases) && actualReleases.containsAll(expectedReleases), "The actual subscription-manager releases list "+actualReleases+" matches the expected consolidated CDN listing "+expectedReleases+" after being granted an entitlement from subscription product: "+rhelSubscriptionPool.productId);
	}
	
	
	@Test(	description="register to a RHEL subscription and verify that release --list excludes 6.0",
			groups={"blockedByBug-802245"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void VerifyReleaseListExcludes60OnRHEL6System_Test() throws JSONException, Exception {
		if (!clienttasks.redhatReleaseX.equals("6")) throw new SkipException("This test is only applicable on RHEL6.");
		
		// make sure we are newly registered with autosubscribe
		clienttasks.register(sm_clientUsername,sm_clientPassword,sm_clientOrg,null,null,null,null,true,null,null,(List<String>)null,true,null,null,null, null);

		// get the current base RHEL product cert
		String providingTag = "rhel-"+clienttasks.redhatReleaseX;
		List<ProductCert> rhelProductCerts = clienttasks.getCurrentProductCerts(providingTag);
		Assert.assertEquals(rhelProductCerts.size(), 1, "Only one product cert is installed that provides RHEL tag '"+providingTag+"'");
		ProductCert rhelProductCert = rhelProductCerts.get(0);
		
		// assert that it was autosubscribed
		InstalledProduct rhelInstalledProduct = InstalledProduct.findFirstInstanceWithMatchingFieldFromList("productId", rhelProductCert.productId, clienttasks.getCurrentlyInstalledProducts());
		Assert.assertNotNull(rhelInstalledProduct, "Our base installed RHEL product was autosubscribed during registration.");

		// get the actual release listing
		List<String> actualReleases = clienttasks.getCurrentlyAvailableReleases(null, null, null);
		
		// assert that the list excludes 6.0, but includes the current X.Y release
		String release60 = "6.0";
		Assert.assertTrue(!actualReleases.contains(release60), "The subscription-manager releases list should exclude '"+release60+"' since '"+clienttasks.command+"' did not exist in RHEL Release '"+release60+"'.");
		//NOT PRACTICAL SINCE CONTENT FROM THIS Y-STREAM MAY NOT BE AVAILABLE UNTIL GA Assert.assertTrue(actualReleases.contains(clienttasks.redhatReleaseXY), "The subscription-manager releases list should include '"+clienttasks.redhatReleaseXY+"' since it is the current RHEL Release under test.");
	}
	
	
	@Test(	description="register to a RHEL subscription and verify that release --list excludes 5.6, 5.5, 5.4, 5.3, 5.2, 5.1, 5.0",
			groups={"blockedByBug-785989"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void VerifyReleaseListExcludes56OnRHEL5System_Test() throws JSONException, Exception {
		if (!clienttasks.redhatReleaseX.equals("5")) throw new SkipException("This test is only applicable on RHEL5.");
		
		// make sure we are newly registered with autosubscribe
		clienttasks.register(sm_clientUsername,sm_clientPassword,sm_clientOrg,null,null,null,null,true,null,null,(List<String>)null,true,null,null,null, null);

		// get the current base RHEL product cert
		String providingTag = "rhel-"+clienttasks.redhatReleaseX;
		List<ProductCert> rhelProductCerts = clienttasks.getCurrentProductCerts(providingTag);
		Assert.assertEquals(rhelProductCerts.size(), 1, "Only one product cert is installed that provides RHEL tag '"+providingTag+"'");
		ProductCert rhelProductCert = rhelProductCerts.get(0);
		
		// assert that it was autosubscribed
		InstalledProduct rhelInstalledProduct = InstalledProduct.findFirstInstanceWithMatchingFieldFromList("productId", rhelProductCert.productId, clienttasks.getCurrentlyInstalledProducts());
		Assert.assertNotNull(rhelInstalledProduct, "Our base installed RHEL product was autosubscribed during registration.");

		// get the actual release listing
		List<String> actualReleases = clienttasks.getCurrentlyAvailableReleases(null, null, null);
		
		// assert that the list excludes 5.6, 5.5, 5.4, 5.3, 5.2, 5.1, 5.0, but includes the current X.Y release
		for (String release: new String[]{"5.6","5.5","5.4","5.3","5.2","5.1","5.0"}) {
			Assert.assertTrue(!actualReleases.contains(release), "The subscription-manager releases list should exclude '"+release+"' since '"+clienttasks.command+"' did not exist in RHEL Release '"+release+"'.");
		}
		//NOT PRACTICAL SINCE CONTENT FROM THIS Y-STREAM MAY NOT BE AVAILABLE UNTIL GA Assert.assertTrue(actualReleases.contains(clienttasks.redhatReleaseXY), "The subscription-manager releases list should include '"+clienttasks.redhatReleaseXY+"' since it is the current RHEL Release under test.");
	}
	
	
	@Test(	description="using a no auth proxy server, register to a RHEL subscription and verify that release --list matches the expected CDN listing for this x-stream release of RHEL",
			groups={},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void VerifyReleaseListMatchesCDNUsingNoAuthProxyCommandLineArgs_Test() throws JSONException, Exception {
		verifyReleaseListMatchesCDN(sm_noauthproxyHostname,sm_noauthproxyPort,null,null);
	}
	
	
	@Test(	description="using a basic auth proxy server, register to a RHEL subscription and verify that release --list matches the expected CDN listing for this x-stream release of RHEL",
			groups={},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void VerifyReleaseListMatchesCDNUsingBasicAuthProxyCommandLineArgs_Test() {
		verifyReleaseListMatchesCDN(sm_basicauthproxyHostname,sm_basicauthproxyPort,sm_basicauthproxyUsername,sm_basicauthproxyPassword);
	}
	
	
	@Test(	description="using a no auth proxy server set within rhsm.conf, register to a RHEL subscription and verify that release --list matches the expected CDN listing for this x-stream release of RHEL",
			groups={"blockedByBug-822965"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void VerifyReleaseListMatchesCDNUsingNoAuthProxyViaRhsmConfFile_Test() throws JSONException, Exception {
		clienttasks.config(false, false, true, Arrays.asList(
				new String[]{"server","proxy_hostname",sm_noauthproxyHostname},
				new String[]{"server","proxy_port",sm_noauthproxyPort}));
		verifyReleaseListMatchesCDN(null,null,null,null);
	}
	
	
	@Test(	description="using a basic auth proxy server set within rhsm.conf, register to a RHEL subscription and verify that release --list matches the expected CDN listing for this x-stream release of RHEL",
			groups={"blockedByBug-822965"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void VerifyReleaseListMatchesCDNUsingBasicAuthProxyViaRhsmConfFile_Test() {
		clienttasks.config(false, false, true, Arrays.asList(
				new String[]{"server","proxy_hostname",sm_basicauthproxyHostname},
				new String[]{"server","proxy_port",sm_basicauthproxyPort},
				new String[]{"server","proxy_user",sm_basicauthproxyUsername},
				new String[]{"server","proxy_password",sm_basicauthproxyPassword}));
		verifyReleaseListMatchesCDN(null,null,null,null);
	}
	

	
	
	
	

	// Candidates for an automated Test:
	
	
	
	// Configuration methods ***********************************************************************
	@BeforeClass (groups="setup")
	public void rememberServerProxyConfigs() {
		if (clienttasks==null) return;
		
		serverProxyHostname	= clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "server", "proxy_hostname");
		serverProxyPort		= clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "server", "proxy_port");
		serverProxyUser		= clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "server", "proxy_user");
		serverProxyPassword	= clienttasks.getConfFileParameter(clienttasks.rhsmConfFile, "server", "proxy_password");
	}
	
	@BeforeMethod (groups="setup")
	@AfterClass (groups="setup")
	public void restoreServerProxyConfigs() {
		if (clienttasks==null) return;
		
		clienttasks.config(false, false, true, Arrays.asList(
				new String[]{"server","proxy_hostname",serverProxyHostname},
				new String[]{"server","proxy_port",serverProxyPort},
				new String[]{"server","proxy_user",serverProxyUser},
				new String[]{"server","proxy_password",serverProxyPassword}));
	}

	
	// Protected methods ***********************************************************************
	String serverProxyHostname	= null;
	String serverProxyPort		= null;
	String serverProxyUser		= null;
	String serverProxyPassword	= null;

	
	protected void verifyReleaseListMatchesCDN(String proxy_hostname, String proxy_port, String proxy_username, String proxy_password) {
		
		// assemble the proxy from the proxy_hostname and proxy_port
		String proxy = proxy_hostname;
		if (proxy_hostname!=null && proxy_port!=null) proxy+=":"+proxy_port;
		
		// get the current base RHEL product cert
		String providingTag = "rhel-"+clienttasks.redhatReleaseX;
		List<ProductCert> rhelProductCerts = clienttasks.getCurrentProductCerts(providingTag);
		Assert.assertEquals(rhelProductCerts.size(), 1, "Only one product cert is installed that provides RHEL tag '"+providingTag+"'");
		ProductCert rhelProductCert = rhelProductCerts.get(0);

		// make sure we are newly registered
		SSHCommandResult registerResult = clienttasks.register(sm_clientUsername,sm_clientPassword,sm_clientOrg,null,null,null,null,true,null,null,(List<String>)null,true,null,proxy,proxy_username, proxy_password);

		// assert that we are subscribed to RHEL
		//if (!InstalledProduct.findFirstInstanceWithMatchingFieldFromList("productId", rhelProductCert.productId, clienttasks.getCurrentlyInstalledProducts()).status.equals("Subscribed")) {
		if (!InstalledProduct.findFirstInstanceWithMatchingFieldFromList("productName", rhelProductCert.productName, InstalledProduct.parse(registerResult.getStdout())).status.equals("Subscribed")) {
			clienttasks.listAvailableSubscriptionPools();
			throw new SkipException("Autosubscribe could not find an available subscription that provides RHEL content for installed RHEL product:"+rhelProductCert.productNamespace);
		}
		// ^^ that is faster, but the following is more reliable...
		/*
		// find an available RHEL subscription pool that provides for this base RHEL product cert
		List<SubscriptionPool> rhelSubscriptionPools = clienttasks.getCurrentlyAvailableSubscriptionPools(rhelProductCert.productId, sm_serverUrl);
		if (rhelSubscriptionPools.isEmpty()) throw new SkipException("Cannot find an available SubscriptionPool that provides for this installed RHEL Product: "+rhelProductCert);
		SubscriptionPool rhelSubscriptionPool = rhelSubscriptionPools.get(0);	// choose one
		
		// subscribe to the RHEL subscription
		File rhelEntitlementCertFile = clienttasks.subscribeToSubscriptionPool(rhelSubscriptionPool);
		File rhelEntitlementCertKeyFile = clienttasks.getEntitlementCertKeyFileCorrespondingToEntitlementCertFile(rhelEntitlementCertFile);
		*/
		
		// get the currently expected release listing based on the currently enabled repos
		List<String> expectedReleases = clienttasks.getCurrentlyExpectedReleases();
		
		// get the actual release listing
		List<String> actualReleases = clienttasks.getCurrentlyAvailableReleases(proxy, proxy_username, proxy_password);
		
		// assert that they are equivalent
		Assert.assertTrue(expectedReleases.containsAll(actualReleases) && actualReleases.containsAll(expectedReleases), "The actual subscription-manager releases list "+actualReleases+" matches the expected consolidated CDN listing "+expectedReleases+" after successfully autosubscribing to installed RHEL product: "+rhelProductCert.productName);
	}
	
	
	
	
	// Data Providers ***********************************************************************
	

}

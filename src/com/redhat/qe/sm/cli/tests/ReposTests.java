package com.redhat.qe.sm.cli.tests;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.json.JSONException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.redhat.qe.Assert;
import com.redhat.qe.auto.testng.TestNGUtils;
import com.redhat.qe.sm.base.SubscriptionManagerCLITestScript;
import com.redhat.qe.sm.data.ContentNamespace;
import com.redhat.qe.sm.data.EntitlementCert;
import com.redhat.qe.sm.data.ProductCert;
import com.redhat.qe.sm.data.Repo;
import com.redhat.qe.sm.data.SubscriptionPool;
import com.redhat.qe.sm.data.YumRepo;
import com.redhat.qe.tools.RemoteFileTasks;
import com.redhat.qe.tools.SSHCommandResult;

/**
 * @author jsefler
 *
 *
 */
@Test(groups={"ReposTests"})
public class ReposTests extends SubscriptionManagerCLITestScript {

	
	// Test methods ***********************************************************************
	
	@Test(	description="subscription-manager: subscribe to a pool and verify that the newly entitled content namespaces are represented in the repos list",
			enabled=true,
			groups={"AcceptanceTests","blockedByBug-807407"},
			dataProvider="getAvailableSubscriptionPoolsData")
	//@ImplementsNitrateTest(caseId=)
	public void ReposListReportsGrantedContentNamespacesAfterSubscribingToPool_Test(SubscriptionPool pool) throws JSONException, Exception{

		log.info("Following is a list of previously subscribed repos...");
		List<Repo> priorRepos = clienttasks.getCurrentlySubscribedRepos();
		
		//File entitlementCertFile = clienttasks.subscribeToSubscriptionPool(pool);	// for this test, we can skip the exhaustive asserts done by this call to clienttasks.subscribeToSubscriptionPool(pool)
		File entitlementCertFile = clienttasks.subscribeToSubscriptionPool_(pool);
		Assert.assertTrue(RemoteFileTasks.testExists(client, entitlementCertFile.getPath()), "Found the EntitlementCert file ("+entitlementCertFile+") that was granted after subscribing to pool id '"+pool.poolId+"'.");

		EntitlementCert entitlementCert = clienttasks.getEntitlementCertFromEntitlementCertFile(entitlementCertFile);
		
		
		// the following block of code was added to account for prior subscribed modifier pools that could provide more repos than expected once this pool is subscribed
		// check the modifierSubscriptionData for SubscriptionPools that may already have been subscribed too and will modify this pool thereby enabling more repos than expected 
		for (List<Object> row : modifierSubscriptionData) {
			// ll.add(Arrays.asList(new Object[]{modifierPool, label, modifiedProductIds, requiredTags, providingPools}));
			SubscriptionPool modifierPool = (SubscriptionPool)row.get(0);
			String label = (String)row.get(1);
			List<String> modifiedProductIds = (List<String>)row.get(2);
			String requiredTags = (String)row.get(3);
			List<SubscriptionPool> poolsModified = (List<SubscriptionPool>)row.get(4);
			if (poolsModified.contains(pool)) {
				if (priorSubscribedPoolIds.contains(modifierPool.poolId)) {
					// the modifier's content should now be available in the repos too
					EntitlementCert modifierEntitlementCert = clienttasks.getEntitlementCertCorrespondingToSubscribedPool(modifierPool);						

					// simply add the contentNamespaces (if not already there) from the modifier to the entitlement cert's contentNamespaces so they will be accounted for in the repos list test below
					for (ContentNamespace contentNamespace : modifierEntitlementCert.contentNamespaces) {
						if (!contentNamespace.type.equalsIgnoreCase("yum")) continue;
						if (!entitlementCert.contentNamespaces.contains(contentNamespace)) {
							log.warning("Due to a previously subscribed modifier subscription pool ("+modifierPool.subscriptionName+"), the new repos listed should also include ContentNamespace: "+contentNamespace);
							entitlementCert.contentNamespaces.add(contentNamespace);
						}
					}
				}
			}
		}
		priorSubscribedPoolIds.add(pool.poolId);
		
		log.info("Following is the new list of subscribed repos after subscribing to pool: "+pool);			
		List<Repo> actualRepos = clienttasks.getCurrentlySubscribedRepos();
		
		// assert that the new contentNamespaces from the entitlementCert are listed in repos
		int numNewRepos=0;
		for (ContentNamespace contentNamespace : entitlementCert.contentNamespaces) {
			if (!contentNamespace.type.equalsIgnoreCase("yum")) continue;

			// instantiate the expected Repo that represents this contentNamespace
			String expectedRepoUrl;	// the expected RepoUrl is set by joining the rhsm.conf baseurl with the downloadUrl in the contentNamespace which is usually a relative path.  When it is already a full path, leave it!
			if (contentNamespace.downloadUrl.contains("://")) {
				expectedRepoUrl = contentNamespace.downloadUrl;
			} else {
				expectedRepoUrl = clienttasks.baseurl.replaceFirst("//+$","//")+contentNamespace.downloadUrl.replaceFirst("^//+","");	// join baseurl to downloadUrl with "/"
			}
			Repo expectedRepo = new Repo(contentNamespace.name,contentNamespace.label,expectedRepoUrl,contentNamespace.enabled.trim().equals("1")?true:false);
			
			// assert the subscription-manager repos --list reports the expectedRepo (unless it requires tags that are not found in the installed product certs)
			if (clienttasks.areAllRequiredTagsInContentNamespaceProvidedByProductCerts(contentNamespace,currentProductCerts)) {
				Assert.assertTrue(actualRepos.contains(expectedRepo),"The newly entitled contentNamespace '"+contentNamespace+"' is represented in the subscription-manager repos --list by: "+expectedRepo);
				
				if (!priorRepos.contains(expectedRepo)) numNewRepos++;	// also count the number of NEW contentNamespaces
				
			} else {
				Assert.assertFalse(actualRepos.contains(expectedRepo),"The newly entitled contentNamespace '"+contentNamespace+"' is NOT represented in the subscription-manager repos --list because it requires tags ("+contentNamespace.requiredTags+") that are not provided by the currently installed product certs.");
			}
		}

		
		// assert that the number of repos reported has increased by the number of contentNamespaces in the new entitlementCert (unless the 
		Assert.assertEquals(actualRepos.size(), priorRepos.size()+numNewRepos, "The number of entitled repos has increased by the number of NEW contentNamespaces ("+numNewRepos+") from the newly granted entitlementCert (including applicable contentNamespaces from a previously subscribed modifier pool).");
		
		// randomly decide to unsubscribe from the pool only for the purpose of saving on accumulated logging and avoid a java heap memory error
		//if (randomGenerator.nextInt(2)==1) clienttasks.unsubscribe(null, entitlementCert.serialNumber, null, null, null); AND ALSO REMOVE pool FROM priorSubscribedPools
	}
	protected Set<String> priorSubscribedPoolIds=new HashSet<String>();
	
	
	@Test(	description="subscription-manager: subscribe to a future pool and verify that NO content namespaces are represented in the repos list",
			groups={"blockedByBug-768983","unsubscribeAllBeforeThisTest"},
			dataProvider="getAllFutureSystemSubscriptionPoolsData",
			enabled=true)
			//@ImplementsNitrateTest(caseId=)
	public void ReposListReportsNoContentNamespacesAfterSubscribingToFuturePool_Test(SubscriptionPool pool) throws Exception {
		
		// subscribe to the future SubscriptionPool
		SSHCommandResult subscribeResult = clienttasks.subscribe(null,null,pool.poolId,null,null,null,null,null,null,null, null);

		// assert that the granted EntitlementCert and its corresponding key exist
		EntitlementCert entitlementCert = clienttasks.getEntitlementCertCorrespondingToSubscribedPool(pool);
		File entitlementCertFile = clienttasks.getEntitlementCertFileFromEntitlementCert(entitlementCert);
		File entitlementCertKeyFile = clienttasks.getEntitlementCertKeyFileFromEntitlementCert(entitlementCert);
		Assert.assertTrue(RemoteFileTasks.testExists(client, entitlementCertFile.getPath()), "EntitlementCert file exists after subscribing to future SubscriptionPool.");
		Assert.assertTrue(RemoteFileTasks.testExists(client, entitlementCertKeyFile.getPath()), "EntitlementCert key file exists after subscribing to future SubscriptionPool.");
		
		// assuming that we are not subscribed to a non-future subscription pool, assert that there are NO subscribed repos 
		Assert.assertEquals(clienttasks.getCurrentlySubscribedRepos().size(),0,"Assuming that we are not currently subscribed to a non-future subscription pool, then there should NOT be any repos reported after subscribing to future subscription pool '"+pool.poolId+"'.");
		
		// TODO we may want to randomly unsubscribe from serial number without asserting to save some computation of the accumulating entitlement certs
	}
	
	
	@Test(	description="subscription-manager: after subscribing to all pools, verify that manual edits to enable repos in redhat.repo are preserved.",
			enabled=true,
			groups={},
			dataProvider="getYumReposData")
	//@ImplementsNitrateTest(caseId=)
	public void ReposListPreservesManualEditsToEnablementOfRedhatRepos_Test(YumRepo yumRepo){

		Repo repo = new Repo(yumRepo.name,yumRepo.id,yumRepo.baseurl,yumRepo.enabled);

		// assert that the yumRepo is reported in the subscription-manager repos
		List<Repo> currentlySubscribedRepos = clienttasks.getCurrentlySubscribedRepos();
		Assert.assertTrue(currentlySubscribedRepos.contains(repo),"The yumRepo '"+yumRepo+"' is represented in the subscription-manager repos --list by: "+repo);
		
		// also verify that yumRepo is reported in the yum repolist
		Assert.assertTrue(clienttasks.getYumRepolist(yumRepo.enabled?"enabled":"disabled").contains(yumRepo.id), "yum repolist properly reports the enablement of yumRepo id '"+yumRepo.id+"' before manually changing its enabled value.");
	
		// manually edit the redhat.repo and change the enabled parameter for this yumRepo
		Boolean newEnabledValue = yumRepo.enabled? false:true;	// toggle the value
		clienttasks.updateYumRepoParameter(clienttasks.redhatRepoFile,yumRepo.id,"enabled",newEnabledValue.toString());
		Repo newRepo = new Repo(yumRepo.name,yumRepo.id,yumRepo.baseurl,newEnabledValue);

		// verify that the change is preserved by subscription-manager repos --list
		currentlySubscribedRepos = clienttasks.getCurrentlySubscribedRepos();
		Assert.assertTrue(currentlySubscribedRepos.contains(newRepo),"yumRepo id '"+yumRepo.id+"' was manually changed to enabled="+newEnabledValue+" and the subscription-manager repos --list reflects the change as: "+newRepo);
		Assert.assertFalse(currentlySubscribedRepos.contains(repo),"The original repo ("+repo+") is no longer found in subscription-manager repos --list.");
		
		// also verify the change is reflected in yum repolist
		Assert.assertTrue(clienttasks.getYumRepolist(newEnabledValue?"enabled":"disabled").contains(yumRepo.id), "yum repolist properly reports the enablement of yumRepo id '"+yumRepo.id+"' which was manually changed to '"+newEnabledValue+"'.");
		Assert.assertFalse(clienttasks.getYumRepolist(!newEnabledValue?"enabled":"disabled").contains(yumRepo.id), "yum repolist properly reports the enablement of yumRepo id '"+yumRepo.id+"' which was manually changed to '"+newEnabledValue+"'.");
	}

	
	@Test(	description="subscription-manager: repos --list reports no entitlements when not registered",
			enabled=true,
			groups={"blockedByBug-724809","blockedByBug-807360","blockedByBug-837447"})
	//@ImplementsNitrateTest(caseId=)
	public void ReposListIsEmptyWhenNotRegistered_Test(){
		
		clienttasks.unregister(null,null,null);		
		
		Assert.assertEquals( clienttasks.getCurrentlySubscribedRepos().size(),0, "No repos are reported by subscription-manager repos --list when not registered.");
	}
	
	
	@Test(	description="subscription-manager: set manage_repos to 0 and assert redhat.repo is removed.",
			enabled=true,
			groups={"blockedByBug-767620","blockedByBug-797996","ManageReposTests","AcceptanceTests"})
	//@ImplementsNitrateTest(caseId=)
	public void ReposListIsDisabledByConfigurationAfterRhsmManageReposIsConfiguredOff_Test() throws JSONException, Exception{
		
		// manually set the manage_repos to 1
		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "manage_repos", "1");
		
		// register
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, true, false, null, null, null);
		
		// assert that the repos list is enabled.
		Assert.assertTrue(clienttasks.repos(true,null,null,null,null,null).getStdout().trim().equals("The system is not entitled to use any repositories."), "The system is not entitled to use any repositories, but is enabled by configuration!");

		// assert that the redhat.repo exists before and after a yum transaction
		//Assert.assertEquals(RemoteFileTasks.testFileExists(client, clienttasks.redhatRepoFile),1,"When rhsm.manage_repos is configured on, the redhat.repo should exist after registration.");
		clienttasks.getYumRepolist(null);
		Assert.assertTrue(RemoteFileTasks.testExists(client, clienttasks.redhatRepoFile),"When rhsm.manage_repos is configured on, the redhat.repo should exist after yum transaction.");

		// assert that the repos list is not entitled to use any repositories, but is enabled by configuration!
		Assert.assertTrue(clienttasks.repos(true,null,null,null,null,null).getStdout().trim().equals("The system is not entitled to use any repositories."), "The system is not entitled to use any repositories, but is enabled by configuration!");

		// NOW DISABLE THE rhsm.manage_repos CONFIGURATION FILE PARAMETER
		clienttasks.config(null, null, true, new String[]{"rhsm","manage_repos","0"});
		
		// assert that the repos list is disabled by configuration!
		Assert.assertTrue(clienttasks.repos(true,null,null,null,null,null).getStdout().trim().equals("Repositories disabled by configuration."), "Repositories disabled by configuration.");

		// trigger a yum transaction and assert that the redhat.repo no longer exists
		clienttasks.getYumRepolist(null);
		Assert.assertTrue(!RemoteFileTasks.testExists(client, clienttasks.redhatRepoFile),"When rhsm.manage_repos is configured off, the redhat.repo file should not exist anymore.");
		
		// subscribe to all subscriptions and re-assert that the repos list remains disabled.
		clienttasks.subscribeToTheCurrentlyAvailableSubscriptionPoolsCollectively();
		Assert.assertTrue(clienttasks.repos(true,null,null,null,null,null).getStdout().trim().equals("Repositories disabled by configuration."), "Repositories disabled by configuration remains even after subscribing to all pools while rhsm.manage_repos is off.");
		Assert.assertTrue(!RemoteFileTasks.testExists(client, clienttasks.redhatRepoFile),"Even after subscribing to all subscription pools while rhsm.manage_repos is off, the redhat.repo is not generated.");
	}
	
	
	@Test(	description="subscription-manager: set manage_repos to 1 and assert redhat.repo is restored.",
			enabled=true,
			groups={"blockedByBug-767620","blockedByBug-797996","ManageReposTests"})
	//@ImplementsNitrateTest(caseId=)
	public void ReposListIsEnabledByConfigurationAfterRhsmManageReposIsConfiguredOn_Test() throws JSONException, Exception{
		
		// manually set the manage_repos to 0
		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "manage_repos", "0");
		
		// register
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, true, false, null, null, null);

		// assert that the repos list is disabled.
		Assert.assertTrue(clienttasks.repos(true,null,null,null,null,null).getStdout().trim().equals("Repositories disabled by configuration."), "Repositories disabled by configuration remains even after subscribing to all pools while rhsm.manage_repos is off.");

		// assert that the redhat.repo does NOT exist before and after a yum transaction
		//Assert.assertEquals(RemoteFileTasks.testFileExists(client, clienttasks.redhatRepoFile),0,"When rhsm.manage_repos is configured off, the redhat.repo should NOT exist after registration.");
		clienttasks.getYumRepolist(null);
		Assert.assertTrue(!RemoteFileTasks.testExists(client, clienttasks.redhatRepoFile),"When rhsm.manage_repos is configured off, the redhat.repo should NOT exist after yum transaction.");

		// subscribe to all subscriptions and re-assert that the repos list remains disabled.
		clienttasks.subscribeToTheCurrentlyAvailableSubscriptionPoolsCollectively();
		Assert.assertTrue(clienttasks.repos(true,null,null,null,null,null).getStdout().trim().equals("Repositories disabled by configuration."), "Repositories disabled by configuration remains even after subscribing to all pools while rhsm.manage_repos is off.");
		Assert.assertTrue(!RemoteFileTasks.testExists(client, clienttasks.redhatRepoFile),"Even after subscribing to all subscription pools while rhsm.manage_repos is off, the redhat.repo is not generated.");

		// NOW ENABLE THE rhsm.manage_repos CONFIGURATION FILE PARAMETER
		clienttasks.config(null, null, true, new String[]{"rhsm","manage_repos","1"});
			
		// assert that the repos list is not entitled to use any repositories, but is enabled by configuration!
		Assert.assertTrue(!clienttasks.getCurrentlySubscribedRepos().isEmpty(), "Now that the system's rhsm.manage_repos is enabled, the entitled content (assuming>0) is displayed in the repos --list.");

		// trigger a yum transaction and assert that the redhat.repo now exists
		clienttasks.getYumRepolist(null);
		Assert.assertTrue(RemoteFileTasks.testExists(client, clienttasks.redhatRepoFile),"When rhsm.manage_repos is configured on, the redhat.repo file should now exist.");
	}
	
	@Test(	description="subscription-manager: enable the repo.",
			enabled=true,
			groups={"EnableReposTests"})
	//@ImplementsNitrateTest(caseId=)
	public void VerifyEnableRepos_Test() throws JSONException, Exception{
		
		String repoName;	
		// register
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, true, false, null, null, null);
		clienttasks.subscribeToTheCurrentlyAvailableSubscriptionPoolsCollectively();
		
				// trigger a yum transaction and assert that the redhat.repo now exists
		
		ArrayList<String> reposlist=clienttasks.getYumRepolist("all");
		repoName= ((String)reposlist.get(2));
		SSHCommandResult result=clienttasks.repos(false, null, null, null,repoName,null);
		Assert.assertEquals(result.getStdout().trim(),"Repo " +repoName+"is enabled for this system", "Stdout from service-level --set without being registered");
		
		}
	
	
	
	
	// Candidates for an automated Test:
	// TODO Bug 797243 - manual changes to redhat.repo are too sticky

	
	
	
	
	
	
	
	
	
	// Configuration methods ***********************************************************************

	@BeforeClass(groups={"setup"})
	public void setupBeforeClass() throws JSONException, Exception {
		currentProductCerts = clienttasks.getCurrentProductCerts();
		modifierSubscriptionData = getModifierSubscriptionDataAsListOfLists(null);
	}
	
	@BeforeGroups(groups={"setup"}, value={"unsubscribeAllBeforeThisTest"})
	public void unsubscribeAllBeforeGroups() {
		clienttasks.unsubscribeFromAllOfTheCurrentlyConsumedProductSubscriptions();
	}
	
	@AfterMethod(groups={"setup"})
	//@AfterGroups(groups={"setup"}, value={"ManageReposTests"})	// did not work the way I wanted it to.  Other @Tests were getting launched during ManageReposTests
	public void setManageReposConfiguration() {
		clienttasks.updateConfFileParameter(clienttasks.rhsmConfFile, "manage_repos", "1");
	}

	
	
	// Protected methods ***********************************************************************

	List<ProductCert> currentProductCerts=new ArrayList<ProductCert>();
	List<List<Object>> modifierSubscriptionData = null;

	
	// Data Providers ***********************************************************************
	
	@DataProvider(name="getYumReposData")
	public Object[][] getYumReposDataAs2dArray() throws Exception {
		return TestNGUtils.convertListOfListsTo2dArray(getYumReposDataAsListOfLists());
	}
	protected List<List<Object>> getYumReposDataAsListOfLists() throws Exception {
		List<List<Object>> ll = new ArrayList<List<Object>>(); if (!isSetupBeforeSuiteComplete) return ll;
		
		// register
		clienttasks.register(sm_clientUsername, sm_clientPassword, sm_clientOrg, null, null, null, null, null, null, null, (String)null, null, null, true, false, null, null, null);
		
		// subscribe to all available subscription so as to populate the redhat.repo file
		clienttasks.subscribeToTheCurrentlyAvailableSubscriptionPoolsCollectively();
		
		clienttasks.getYumRepolist("all");	// trigger a yum transaction so that subscription-manager plugin will refresh redhat.repo
		for (YumRepo yumRepo : clienttasks.getCurrentlySubscribedYumRepos()) {
			ll.add(Arrays.asList(new Object[]{yumRepo}));
			
			// minimize the number of dataProvided rows (useful during automated testcase development)
			if (Boolean.valueOf(getProperty("sm.debug.dataProviders.minimize","false"))) break;
		}

		return ll;
	}

}

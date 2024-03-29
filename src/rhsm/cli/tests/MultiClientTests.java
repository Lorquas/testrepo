package rhsm.cli.tests;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.testng.SkipException;
import org.testng.annotations.AfterGroups;
import org.testng.annotations.Test;

import com.redhat.qe.Assert;
import com.redhat.qe.auto.tcms.ImplementsNitrateTest;
import rhsm.base.ConsumerType;
import rhsm.base.SubscriptionManagerCLITestScript;
import rhsm.cli.tasks.CandlepinTasks;
import rhsm.data.SubscriptionPool;
import com.redhat.qe.tools.SSHCommandResult;

/**
 * @author jsefler
 *
 */
@Test(groups={"MultiClientTests"})
public class MultiClientTests extends SubscriptionManagerCLITestScript{
	

	// Test Methods ***********************************************************************

	// FIXME Redesign this test to use only one client box and use clean and register --consumerid to switch users  (see SubscribeTests.MultiConsumerSubscribeWithQuantity_Test as an example)
	@Test(	description="bind/unbind with two users/consumers",
			groups={},
			dataProvider="getAvailableSubscriptionPoolsData")
	@ImplementsNitrateTest(caseId=53217)
	public void MultiClientSubscribeToSameSubscriptionPool_Test(SubscriptionPool pool) throws JSONException, Exception {
//if (!pool.quantity.equalsIgnoreCase("unlimited")) throw new SkipException("debugging...");
		// test prerequisites
		if (client2tasks==null) throw new SkipException("This multi-client test requires a second client.");
		String client1OwnerKey = CandlepinTasks.getOwnerKeyOfConsumerId(sm_client1Username, sm_client1Password, sm_serverUrl, client1tasks.getCurrentConsumerId());
		String client2OwnerKey = CandlepinTasks.getOwnerKeyOfConsumerId(sm_client2Username, sm_client2Password, sm_serverUrl, client2tasks.getCurrentConsumerId());
		if (!client1OwnerKey.equals(client2OwnerKey)) throw new SkipException("This multi-client test requires that both client registerers belong to the same owner. (client1: username="+sm_client1Username+" ownerkey="+client1OwnerKey+") (client2: username="+sm_client2Username+" ownerkey="+client2OwnerKey+")");
		
		String client1RedhatRelease = client1tasks.getRedhatRelease();
		String client2RedhatRelease = client2tasks.getRedhatRelease();
				
		// assert that the subscriptionPool is available to both consumers with the same quantity...
		List<SubscriptionPool> cl1SubscriptionPools = client1tasks.getCurrentlyAvailableSubscriptionPools();
		List<SubscriptionPool> cl2SubscriptionPools;
		if (client2RedhatRelease.equals(client1RedhatRelease))
			cl2SubscriptionPools = client2tasks.getCurrentlyAvailableSubscriptionPools();
		else
			cl2SubscriptionPools = client2tasks.getCurrentlyAllAvailableSubscriptionPools();
		SubscriptionPool cl1SubscriptionPool;
		SubscriptionPool cl2SubscriptionPool;
		
		/* THE FOLLOWING BLOCK OF LOGIC WAS NEEDED PRIOR TO DESIGN CHANGE AS DETAILED IN https://bugzilla.redhat.com/show_bug.cgi?id=663455
		// Before proceeding with this test, determine if the productId provided by this subscription pool has already been entitled.
		// This will happen when more than one pool has been created under a different contract/serial so as to increase the
		// total quantity of entitlements available to the consumers.
		if (alreadySubscribedProductIdsInMultiClientSubscribeToSameSubscriptionPool_Test.contains(pool.productId)) {
			log.info("Because the productId '"+pool.productId+"' from this pool has already been subscribed to via a previously available pool, this pool should no longer be available to consumer 1 ("+client1username+") but should still be available to consumer 2 ("+client2username+") with the original quantity...");
			Assert.assertFalse(cl1SubscriptionPools.contains(pool),"Subscription pool "+pool+" is NOT available to consumer1 ("+client1username+").");
			Assert.assertTrue(cl2SubscriptionPools.contains(pool),"Subscription pool "+pool+" is still available to consumer2 ("+client2username+").");
			cl2SubscriptionPool = cl2SubscriptionPools.get(cl2SubscriptionPools.indexOf(pool));
			Assert.assertEquals(cl2SubscriptionPool.quantity, pool.quantity, "The quantity of entitlements from subscription pool "+pool+" available to consumer2 ("+client2username+") remains unchanged.");
			return; // test complete
		}
		*/
		
		// assert that the subscriptionPool is available to consumer 1
		Assert.assertTrue(cl1SubscriptionPools.contains(pool),"Subscription pool "+pool+" is available to consumer1 ("+sm_client1Username+").");
		cl1SubscriptionPool = cl1SubscriptionPools.get(cl1SubscriptionPools.indexOf(pool));

		// assert that the subscriptionPool is available to consumer 2
		Assert.assertTrue(cl2SubscriptionPools.contains(pool),"Subscription pool "+pool+" is available to consumer2 ("+sm_client2Username+").");
		cl2SubscriptionPool = cl2SubscriptionPools.get(cl2SubscriptionPools.indexOf(pool));

		// assert that the quantity available to both clients is the same
		Assert.assertEquals(cl1SubscriptionPool.quantity, cl2SubscriptionPool.quantity, "The quantity of entitlements from subscription pool id '"+pool.poolId+"' available to both consumers is the same.");

		// subscribe consumer1 to the pool and assert that the available quantity to consumer2 has decremented by one...
		client1tasks.subscribeToSubscriptionPool(pool);
		alreadySubscribedProductIdsInMultiClientSubscribeToSameSubscriptionPool_Test.add(pool.productId);

		// assert that the subscriptionPool is still available to consumer 2
		if (client2RedhatRelease.equals(client1RedhatRelease))
			cl2SubscriptionPools = client2tasks.getCurrentlyAvailableSubscriptionPools();
		else
			cl2SubscriptionPools = client2tasks.getCurrentlyAllAvailableSubscriptionPools();
		Assert.assertTrue(cl2SubscriptionPools.contains(pool),"Subscription pool id "+pool.poolId+" is still available to consumer2 ("+sm_client2Username+").");
		cl2SubscriptionPool = cl2SubscriptionPools.get(cl2SubscriptionPools.indexOf(pool));

		// assert that the quantity has decremented by one
		if (cl1SubscriptionPool.quantity.equalsIgnoreCase("unlimited")) {
			//Assert.assertEquals(cl2SubscriptionPool.quantity, "unlimited", "When the quantity of entitlements from subscription pool id '"+pool.poolId+"' is 'unlimited', then the available to consumer2 ("+sm_client2Username+") must remain 'unlimited' after consumer1 subscribed to the pool.");
			Assert.assertEquals(cl2SubscriptionPool.quantity, "Unlimited", "When the quantity of entitlements from subscription pool id '"+pool.poolId+"' is 'Unlimited', then the available to consumer2 ("+sm_client2Username+") must remain 'Unlimited' after consumer1 subscribed to the pool.");		// altered after Bug 862885 - String Update: Capitalize unlimited in the All Available Subscriptions tab 
		} else {
			Assert.assertEquals(Integer.valueOf(cl2SubscriptionPool.quantity).intValue(), Integer.valueOf(cl1SubscriptionPool.quantity).intValue()-1, "The quantity of entitlements from subscription pool id '"+pool.poolId+"' available to consumer2 ("+sm_client2Username+") has decremented by one.");
		}
	}
	protected List<String> alreadySubscribedProductIdsInMultiClientSubscribeToSameSubscriptionPool_Test = new ArrayList<String>();
	
	
	
	
	@Test(	description="verify that only one person can be registered under username at a time",
			groups={"MultiClientRegisterAsPerson_Test"},
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void MultiClientRegisterAsPerson_Test() throws JSONException, Exception {
		// test prerequisites
		if (client2tasks==null) throw new SkipException("This multi-client test requires a second client.");
		unregisterMultiClientRegisterAsPersonAfterGroups();
		
		// decide what username and password to test with
		String username = sm_clientUsername;
		String password = sm_clientPassword;
		String owner = sm_clientOrg;
		if (!sm_rhpersonalUsername.equals("")) {
			username = sm_rhpersonalUsername;
			password = sm_rhpersonalPassword;
			owner = sm_rhpersonalOrg;
		}
		
		//personIdForMultiClientRegisterAsPerson_Test = client1tasks.getCurrentConsumerId(client1tasks.register(clientusername, clientpassword, ConsumerType.person, null, null, null, null, null, null, null));
		client1tasks.register(username, password, owner, null, ConsumerType.person, null, null, null, null, null, (String)null, null, null, null, false, null, null, null);
		
		// attempt to register a second person consumer using the same username
		SSHCommandResult sshCommandResult = client2tasks.register_(username, password, owner, null, ConsumerType.person, null, null, null, null, null, (String)null, null, null, null, null, null, null, null);

		// assert the sshCommandResult here
		// User testuser1 has already registered a personal consumer
		Assert.assertContainsMatch(sshCommandResult.getStderr().trim(), String.format("User %s has already registered a personal consumer", username),"stderr after attempt to register same person from a second different client:");
		Assert.assertEquals(sshCommandResult.getExitCode(), Integer.valueOf(255),"exitCode after attempt to register same person from a second different client");

	}
	
	
	// Configuration Methods ***********************************************************************

	//protected String personIdForMultiClientRegisterAsPerson_Test = null;
	@AfterGroups(groups={"setup"},value="MultiClientRegisterAsPerson_Test", alwaysRun=true)
	public void unregisterMultiClientRegisterAsPersonAfterGroups() {
		//if (personIdForMultiClientRegisterAsPerson_Test!=null) {
			client2tasks.unregister(null,null,null);
			client1tasks.unregister(null,null,null);
		//}
	}
	
	
	// Protected Methods ***********************************************************************

	

	
	
	// Data Providers ***********************************************************************

	

}

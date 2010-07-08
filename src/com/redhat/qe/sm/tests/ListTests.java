package com.redhat.qe.sm.tests;

import org.testng.annotations.Test;

import com.redhat.qe.auto.tcms.ImplementsTCMS;
import com.redhat.qe.sm.base.SubscriptionManagerTestScript;
import com.redhat.qe.tools.RemoteFileTasks;

public class ListTests extends SubscriptionManagerTestScript{
	
	@Test(description="subscription-manager-cli: list available entitlements",
			dependsOnGroups={"sm_stage2"},
			groups={"sm_stage3"})
	@ImplementsTCMS(id="41678")
	public void EnsureAvailableEntitlementsListed_Test(){
		sm.listAvailable();
	}
	
	
	@Test(description="subscription-manager-cli: list consumed entitlements",
			dependsOnGroups={"sm_stage3"},
			groups={"sm_stage4", "not_implemented"})
	@ImplementsTCMS(id="41679")
	public void EnsureConsumedEntitlementsListed_Test(){
		sm.subscribeToAllPools(false);
		sm.listConsumed();
	}
}
package com.redhat.qe.sm.tests;

import java.util.ArrayList;

import org.testng.annotations.Test;

import com.redhat.qe.auto.tcms.ImplementsTCMS;
import com.redhat.qe.auto.testopia.Assert;
import com.redhat.qe.sm.tasks.Pool;
import com.redhat.qe.tools.RemoteFileTasks;

public class Subscribe extends Register{
	
	@Test(description="Verify all subscriptions can be subscribed to",
			dependsOnMethods="ValidRegistration_Test",
			groups={"sm"})
	@ImplementsTCMS(id="41680")
	public void SubscribeToValidSubscriptionsByProductID_Test(){
		this.refreshSubscriptions();
		for (Pool sub:this.availSubscriptions)
			this.subscribeToPool(sub, false);
		Assert.assertEquals(this.getNonSubscribedSubscriptions().size(),
				0,
				"Asserting that all available subscriptions are now subscribed");
	}
	
	@Test(description="Enable yum repo and verify that content is available for testing",
			dependsOnMethods="SubscribeToValidSubscriptionsByProductID_Test",
			groups={"sm"})
	@ImplementsTCMS(id="41696")
	public void EnableYumRepoAndVerifyContentAvailable_Test(){
		this.adjustRHSMYumRepo(true);
		for(Pool sub:this.consumedSubscriptions){
			ArrayList<String> repos = this.getYumRepolist();
			Assert.assertTrue(repos.contains(sub.productId),
					"Yum reports product subscribed to repo: " + sub.productId);
		}	
	}
	
	@Test(description="Enable yum repo and verify that content is available for testing",
			dependsOnMethods="EnableYumRepoAndVerifyContentAvailable_Test",
			groups={"sm"})
	@ImplementsTCMS(id="41696")
	public void DisableYumRepoAndVerifyContentNotAvailable_Test(){
		this.adjustRHSMYumRepo(false);
		for(Pool sub:this.availSubscriptions)
			for(String repo:this.getYumRepolist())
				if(repo.contains(sub.productId))
					Assert.fail("After unsubscribe, Yum still has access to repo: "+repo);
	}
	
	@Test(description="Tests that certificate frequency is updated at appropriate intervals",
			dependsOnMethods="SubscribeToValidSubscriptionsByProductID_Test",
			groups={"sm"})
	@ImplementsTCMS(id="41692")
	public void certFrequency_Test(){
		this.changeCertFrequency("1");
		this.sleep(70*1000);
		Assert.assertEquals(RemoteFileTasks.grepFile(sshCommandRunner,
				rhsmcertdLogFile,
				"certificates updated"),
				0,
				"rhsmcertd reports that certificates have been updated at new interval");
	}
	
	@Test(description="Tests that certificates are refreshed appropriately",
			dependsOnMethods="certFrequency_Test",
			groups={"sm"})
	@ImplementsTCMS(id="41694")
	public void refreshCerts_Test(){
		sshCommandRunner.runCommandAndWait("rm -f /etc/pki/entitlement/*");
		sshCommandRunner.runCommandAndWait("rm -f /etc/pki/entitlement/product/*");
		sshCommandRunner.runCommandAndWait("rm -f /etc/pki/product/*");
		sshCommandRunner.runCommandAndWait("rm -f "+rhsmcertdLogFile);
		
		this.sleep(70*1000);
		
		Assert.assertEquals(RemoteFileTasks.grepFile(sshCommandRunner,
				rhsmcertdLogFile,
				"certificates updated"),
				0,
				"rhsmcertd reports that certificates have been updated");
		
		//verify that PEM files are present in all certificate directories
		RemoteFileTasks.runCommandAndAssert(sshCommandRunner, 
				"ls /etc/pki/entitlement", 
				"pem", 
				null, 
				0);
		RemoteFileTasks.runCommandAndAssert(sshCommandRunner, 
				"ls /etc/pki/entitlement/product", 
				"pem", 
				null, 
				0);
		RemoteFileTasks.runCommandAndAssert(sshCommandRunner, 
				"ls /etc/pki/product", 
				"pem", 
				null, 
				0);
	}
}

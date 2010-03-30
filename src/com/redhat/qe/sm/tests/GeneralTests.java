package com.redhat.qe.sm.tests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.redhat.qe.auto.tcms.ImplementsTCMS;
import com.redhat.qe.auto.testng.TestNGUtils;
import com.redhat.qe.auto.testopia.Assert;
import com.redhat.qe.tools.RemoteFileTasks;

@Test(groups={"sm"})
public class GeneralTests extends Setup{
	
	@Test(description="Verify subscription-manager-cli command line options for help.",dataProvider="HelpTextData")
	@ImplementsTCMS(id="41697")
	public void HelpTextPresent_Test(String command, String stdoutGrepExpression, String stderrGrepExpression, int expectedExitCode) {
		log.info("Testing subscription-manager-cli command line options '"+command+"' and verifying the output.");
		RemoteFileTasks.runCommandAndAssert(sshCommandRunner, command,stdoutGrepExpression,stderrGrepExpression,expectedExitCode);
	}
	
	@DataProvider(name="HelpTextData")
	public Object[][] getHelpTextDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getHelpTextDataAsListOfLists());
	}
	protected List<List<Object>> getHelpTextDataAsListOfLists() {
		List<List<Object>> ll = new ArrayList<List<Object>>();
		for (String grinderHelpCommand : new String[]{"subscription-manager-cli -h","subscription-manager-cli --help"}) {
			ll.add(Arrays.asList(new Object[]{ grinderHelpCommand, "Usage: subscription-manager-cli [options] MODULENAME --help", null, 0 }));
			ll.add(Arrays.asList(new Object[]{ grinderHelpCommand, "^[[:tab:]]+list^[[:space:]]+list available or consumer subscriptions for registered user", null, 0 }));
			ll.add(Arrays.asList(new Object[]{ grinderHelpCommand, "^[[:tab:]]+register^[[:space:]]+register the client to a Unified Entitlement Platform.", null, 0 }));
			ll.add(Arrays.asList(new Object[]{ grinderHelpCommand, "^[[:tab:]]+subscribe^[[:space:]]+subscribe the registered user to a specified product or regtoken.", null, 0 }));
			ll.add(Arrays.asList(new Object[]{ grinderHelpCommand, "^[[:tab:]]+unsubscribe^[[:space:]]+unsubscribe the registered user from all or specific subscriptions.", null, 0 }));
		}
		
		return ll;
	}
	
	@Test(description="Verify functionality not present if client not registered",dataProvider="NegativeFunctionalityData")
	@ImplementsTCMS(id="41697")
	public void NegativeFunctionality_Test(String command) {
		log.info("Testing subscription-manager-cli command without registering, expecting it to fail: "+ command);
		RemoteFileTasks.runCommandExpectingNonzeroExit(sshCommandRunner, command);
	}
	
	@DataProvider(name="NegativeFunctionalityData")
	public String[] getNegativeFunctionalityDataAs2dArray() {
		ArrayList<String> negCmds = new ArrayList<String>();
		
		negCmds.add(RHSM_LOC + "list --available");
		negCmds.add(RHSM_LOC + "list --consumed");
		negCmds.add(RHSM_LOC + "subscribe --product=FOO");
		negCmds.add(RHSM_LOC + "subscribe --regtoken=FOO");
		negCmds.add(RHSM_LOC + "subscribe --pool=FOO");
		negCmds.add(RHSM_LOC + "unsubscribe --product=FOO");
		negCmds.add(RHSM_LOC + "unsubscribe --regtoken=FOO");
		negCmds.add(RHSM_LOC + "unsubscribe --pool=FOO");
		
		return (String[])negCmds.toArray();
	}
}

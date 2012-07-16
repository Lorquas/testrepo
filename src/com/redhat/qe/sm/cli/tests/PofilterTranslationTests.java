package com.redhat.qe.sm.cli.tests;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.redhat.qe.Assert;
import com.redhat.qe.auto.bugzilla.BlockedByBzBug;
import com.redhat.qe.auto.testng.TestNGUtils;
import com.redhat.qe.sm.data.Translation;
import com.redhat.qe.tools.SSHCommandResult;
//import com.sun.org.apache.xalan.internal.xsltc.compiler.Pattern;
import com.sun.org.apache.xalan.internal.xsltc.compiler.util.ErrorMsg;

/**
 * @author jsefler
 * @References
 *   Engineering Localization Services: https://home.corp.redhat.com/node/53593
 *   http://git.fedorahosted.org/git/?p=subscription-manager.git;a=blob;f=po/pt.po;h=0854212f4fab348a25f0542625df343653a4a097;hb=RHEL6.3
 *   Here is the raw rhsm.po file for LANG=pt
 *   http://git.fedorahosted.org/git/?p=subscription-manager.git;a=blob;f=po/pt.po;hb=RHEL6.3
 *   
 *   https://engineering.redhat.com/trac/LocalizationServices
 *   https://engineering.redhat.com/trac/LocalizationServices/wiki/L10nRHEL6LanguageSupportCriteria
 *   
 *   https://translate.zanata.org/zanata/project/view/subscription-manager/iter/0.99.X/stats
 *   
 *   https://fedora.transifex.net/projects/p/fedora/
 *   
 *   http://translate.sourceforge.net/wiki/
 *   http://translate.sourceforge.net/wiki/toolkit/index
 *   http://translate.sourceforge.net/wiki/toolkit/pofilter
 *   http://translate.sourceforge.net/wiki/toolkit/pofilter_tests
 *   http://translate.sourceforge.net/wiki/toolkit/installation
 *   
 *   https://github.com/translate/translate
 **/
@Test(groups={"PofilterTranslationTests"})
public class PofilterTranslationTests extends TranslationTests{
	
	
	// Test Methods ***********************************************************************

	@Test(	description="run pofilter translate tests on the translation file",
			groups={},
			dataProvider="getTranslationFilePofilterTestData",
			enabled=true)
	//@ImplementsNitrateTest(caseId=)
	public void pofilter_Test(Object bugzilla, String pofilterTest, File translationFile) {
		log.info("For an explanation of pofilter test '"+pofilterTest+"', see: http://translate.sourceforge.net/wiki/toolkit/pofilter_tests");
		File translationPoFile = new File(translationFile.getPath().replaceFirst(".mo$", ".po"));
		
		// if pofilter test -> notranslatewords, create a file with words that don't have to be translated to native language
		//String fileName = (getProperty("automation.dir", "/tmp")+"/tmp/notranslatefile").replace("tmp/tmp", "tmp");
		String fileName = "/tmp/notranslatefile";
		if(pofilterTest.equals("notranslatewords")) {
			String rmCommand = "rm "+fileName;
			SSHCommandResult removeFile = client.runCommandAndWait(rmCommand);
			if(!removeFile.getStderr().contains("No such file or directory"))
				Assert.assertEquals(removeFile.getExitCode(), new Integer(0), fileName+" deleted.");
					
			// The words that need not be translated can be added to the below list
			List<String> notranslateWords = Arrays.asList("Red Hat");
			for(String str : notranslateWords) {
				String echoCommand = "echo "+str+" >> "+fileName;
				SSHCommandResult writeData = client.runCommandAndWait(echoCommand);
				Assert.assertEquals(writeData.getExitCode(), new Integer(0), str+" successfully written into the file.");
			}
			log.info("Successfull created \"notranslatefile\" file");
		}
		
		// execute the pofilter test
		String pofilterCommand = "pofilter --gnome -t "+pofilterTest+" --notranslatefile "+fileName;
		//String pofilterCommand = "pofilter --gnome -t "+pofilterTest; 
		SSHCommandResult pofilterResult = client.runCommandAndWait(pofilterCommand+" "+translationPoFile);
		Assert.assertEquals(pofilterResult.getExitCode(), new Integer(0), "Successfully executed the pofilter tests.");
		
		// convert the pofilter test results into a list of failed Translation objects for simplified handling of special cases 
		List<Translation> pofilterFailedTranslations = Translation.parse(pofilterResult.getStdout());
		
		
		// remove the first translation which contains only meta data
		if (!pofilterFailedTranslations.isEmpty() && pofilterFailedTranslations.get(0).msgid.equals("")) pofilterFailedTranslations.remove(0);
		
		
		// ignore the following special cases of acceptable results..........
		List<String> ignorableMsgIds = Arrays.asList();
		if (pofilterTest.equals("accelerators")) {
			if (translationFile.getPath().contains("/hi/")) ignorableMsgIds = Arrays.asList("proxy url in the form of proxy_hostname:proxy_port");
			if (translationFile.getPath().contains("/ru/")) ignorableMsgIds = Arrays.asList("proxy url in the form of proxy_hostname:proxy_port");
		}
		if (pofilterTest.equals("newlines")) {
			ignorableMsgIds = Arrays.asList(
					"Optional language to use for email notification when subscription redemption is complete. Examples: en-us, de-de",
					"\n"+"Unable to register.\n"+"For further assistance, please contact Red Hat Global Support Services.",
					"Tip: Forgot your login or password? Look it up at http://red.ht/lost_password",
					"Unable to perform refresh due to the following exception: %s",
					""+"This migration script requires the system to be registered to RHN Classic.\n"+"However this system appears to be registered to '%s'.\n"+"Exiting.",
					"The tool you are using is attempting to re-register using RHN Certificate-Based technology. Red Hat recommends (except in a few cases) that customers only register with RHN once.",
					// bug 825397	""+"Redeeming the subscription may take a few minutes.\n"+"Please provide an email address to receive notification\n"+"when the redemption is complete.",	// the Subscription Redemption dialog actually expands to accommodate the message, therefore we could ignore it	// bug 825397 should fix this
					// bug 825388	""+"We have detected that you have multiple service level\n"+"agreements on various products. Please select how you\n"+"want them assigned.", // bug 825388 or 825397 should fix this
					"\n"+"This machine appears to be already registered to Certificate-based RHN.  Exiting.");	
		}
		if (pofilterTest.equals("xmltags")) { 
			Boolean match = false; 
			List <String> ignoreMsgIDs = new ArrayList<String>();
			for(Translation pofilterFailedTranslation : pofilterFailedTranslations) {
				// Parsing mgID and msgStr for XMLTags
				Pattern xmlTags = Pattern.compile("<.+?>");  
				Matcher tagsMsgID = xmlTags.matcher(pofilterFailedTranslation.msgid);
				Matcher tagsMsgStr = xmlTags.matcher(pofilterFailedTranslation.msgstr);
				// Populating a msgID tags into a list
				ArrayList<String> msgIDTags = new ArrayList<String>();
				while(tagsMsgID.find()) {
					msgIDTags.add(tagsMsgID.group());
				}
				// Sorting an list of msgID tags
				ArrayList<String> msgIDTagsSort = new ArrayList<String>(msgIDTags);
				Collections.sort(msgIDTagsSort);
				// Populating a msgStr tags into a list
				ArrayList<String> msgStrTags = new ArrayList<String>();
				while(tagsMsgStr.find()) {
					msgStrTags.add(tagsMsgStr.group());
				}
				// Sorting an list of msgStr tags
				ArrayList<String> msgStrTagsSort = new ArrayList<String>(msgStrTags);
				Collections.sort(msgStrTagsSort);
				// Verifying whether XMLtags are opened and closed appropriately 
				// If the above condition holds, then check for XML Tag ordering
				if(msgIDTagsSort.equals(msgStrTagsSort) && msgIDTagsSort.size() == msgStrTagsSort.size()) {
					int size = msgIDTags.size(),count=0;
					// Stack to hold XML tags
					Stack<String> stackMsgIDTags = new Stack<String>();
					Stack<String> stackMsgStrTags = new Stack<String>();
					// Temporary stack to hold popped elements
					Stack<String> tempStackMsgIDTags = new Stack<String>();
					Stack<String> tempStackMsgStrTags = new Stack<String>();
					while(count< size) {
						// If it's not a close tag push into stack
						if(!msgIDTags.get(count).contains("/")) stackMsgIDTags.push(msgIDTags.get(count));
						else {
							if(checkTags(stackMsgIDTags,tempStackMsgIDTags,msgIDTags.get(count))) match = true;
							else {
								// If an open XMLtag doesn't have an appropriate close tag exit loop
								match = false;
								break;
							}
						}
						// If it's not a close tag push into stack
						if(!msgStrTags.get(count).contains("/")) stackMsgStrTags.push(msgStrTags.get(count));
						else {
							if(checkTags(stackMsgStrTags,tempStackMsgStrTags,msgStrTags.get(count))) match = true;
							else {
								// If an open XMLtag doesn't have an appropriate close tag exit loop
								match = false;
								break;
							}
						}
						// Incrementing count to point to the next element
						count++;
					}
				}
				if(match) ignoreMsgIDs.add(pofilterFailedTranslation.msgid);
			}
			ignorableMsgIds = ignoreMsgIDs;
		}
		if (pofilterTest.equals("filepaths")) {
			List <String> ignoreMsgIDs = new ArrayList<String>();
			for(Translation pofilterFailedTranslation : pofilterFailedTranslations) {
				// Parsing mgID and msgStr for FilePaths ending ' ' (space) 
				Pattern filePath = Pattern.compile("/.*?( |$)", Pattern.MULTILINE);  
				Matcher filePathMsgID = filePath.matcher(pofilterFailedTranslation.msgid);
				Matcher filePathMsgStr = filePath.matcher(pofilterFailedTranslation.msgstr);
				ArrayList<String> filePathsInID = new ArrayList<String>();
				ArrayList<String> filePathsInStr = new ArrayList<String>();
				// Reading the filePaths into a list
				while(filePathMsgID.find()) {
					filePathsInID.add(filePathMsgID.group());
				}
				while(filePathMsgStr.find()) {
					filePathsInStr.add(filePathMsgStr.group());
				}
				// If the lists are equal in size, then compare the contents of msdID->filePath and msgStr->filePath
				//if(filePathsInID.size() == filePathsInStr.size()) {
					for(int i=0;i<filePathsInID.size();i++) {
						// If the msgID->filePath ends with '.', remove '.' and compare with msgStr->filePath
						if(filePathsInID.get(i).trim().startsWith("//")) {
							ignoreMsgIDs.add(pofilterFailedTranslation.msgid);
							continue;
						}
						//contains("//")) ignoreMsgIDs.add(pofilterFailedTranslation.msgid);
						if(filePathsInID.get(i).trim().charAt(filePathsInID.get(i).trim().length()-1) == '.') {
							String filePathID = filePathsInID.get(i).trim().substring(0, filePathsInID.get(i).trim().length()-1);
							if(filePathID.equals(filePathsInStr.get(i).trim())) ignoreMsgIDs.add(pofilterFailedTranslation.msgid);
						}
						/*else {
							if(filePathsInID.get(i).trim().equals(filePathsInStr.get(i).trim())) ignoreMsgIDs.add(pofilterFailedTranslation.msgid);
						}*/
					}
				//}
			}
			ignorableMsgIds = ignoreMsgIDs;
		}
		// TODO remove or comment this ignore case once the msgID is corrected 
		// error: 		msgid "Error: you must register or specify --username and password to list service levels"
		// rectified:	msgid "Error: you must register or specify --username and --password to list service levels"
		if (pofilterTest.equals("options")) {
			ignorableMsgIds = Arrays.asList("Error: you must register or specify --username and password to list service levels");
		}
		if (pofilterTest.equals("short")) {
			ignorableMsgIds = Arrays.asList("No","Yes","Key","Value","N/A","None");
		}
		if (pofilterTest.equals("doublewords")) {
			ignorableMsgIds = Arrays.asList("Subscription Subscriptions Box","Subscription Subscriptions Label");
		}
		if (pofilterTest.equals("unchanged")) {
			List<String> conditionalIgnorableMsgIdsSpanish 	= Arrays.asList("no");
			List<String> conditionalIgnorableMsgIdsFrench 	= Arrays.asList("Options","Type","Version","page 2","%prog [options]");
			List<String> conditionalIgnorableMsgIdsGerman 	= Arrays.asList("<b>Account:</b>","<b>python-rhsm Version:</b> %s","<b>Subscription Management Service Version:</b> %s","Login:","Name","Name:                 \t%s","Status","Status:               \t%s","Version","Version:              \t%s","_System","integer","long integer","name: %s");
			List<String> conditionalIgnorableMsgIdsPortugese= Arrays.asList("Status","Status:               \t%s","<b>HTTP Proxy</b>","<b>python-rhsm version:</b> %s","<b>subscription management service version:</b> %s","Login:","Virtual","_Help","hostname[:port][/prefix]","org id: %s");
			List<String> conditionalIgnorableMsgIdsTamil    = Arrays.asList("org id: %s");
			List<String> conditionalIgnorableMsgIdsBengali  = Arrays.asList("Red Hat Subscription Manager","Red Hat Subscription Validity Applet","Subscription Validity Applet");
			
			ignorableMsgIds = Arrays.asList("close_button","facts_view","register_button","register_dialog_main_vbox","registration_dialog_action_area\n","prod 1, prod2, prod 3, prod 4, prod 5, prod 6, prod 7, prod 8","%s of %s","floating-point","integer","long integer","Copyright (c) 2012 Red Hat, Inc.","RHN Classic","env_select_vbox_label","environment_treeview","no_subs_label","org_selection_label","org_selection_scrolledwindow","owner_treeview","progress_label","python-rhsm: %s","register_details_label","register_progressbar","subscription-manager: %s","system_instructions_label","system_name_label","connectionStatusLabel",""+"\n"+"This software is licensed to you under the GNU General Public License, version 2 (GPLv2). There is NO WARRANTY for this software, express or implied, including the implied warranties of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2 along with this software; if not, see:\n"+"\n"+"http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt\n"+"\n"+"Red Hat trademarks are not licensed under GPLv2. No permission is granted to use or replicate Red Hat trademarks that are incorporated in this software or its documentation.\n");
			
			if     (translationFile.getPath().contains("/bn_IN/")) ignorableMsgIds.addAll(conditionalIgnorableMsgIdsBengali);
			else if(translationFile.getPath().contains("/ta_IN/")) ignorableMsgIds.addAll(conditionalIgnorableMsgIdsTamil);
			else if(translationFile.getPath().contains("/pt_BR/")) ignorableMsgIds.addAll(conditionalIgnorableMsgIdsPortugese);
			else if(translationFile.getPath().contains("/de_DE/")) ignorableMsgIds.addAll(conditionalIgnorableMsgIdsGerman);
			else if(translationFile.getPath().contains("/es_ES/")) ignorableMsgIds.addAll(conditionalIgnorableMsgIdsSpanish);
			else if(translationFile.getPath().contains("/fr/"))    ignorableMsgIds.addAll(conditionalIgnorableMsgIdsFrench);
			
		}
		// pluck out the ignorable pofilter test results
		for (String msgid : ignorableMsgIds) {
			Translation ignoreTranslation = Translation.findFirstInstanceWithMatchingFieldFromList("msgid", msgid, pofilterFailedTranslations);
			if (ignoreTranslation!=null) {
				log.info("Ignoring result of pofiliter test '"+pofilterTest+"' for msgid: "+ignoreTranslation.msgid);
				pofilterFailedTranslations.remove(ignoreTranslation);
			}
		}
		
		// assert that there are no failed pofilter translation test results
		Assert.assertEquals(pofilterFailedTranslations.size(),0, "Discounting the ignored test results, the number of failed pofilter '"+pofilterTest+"' tests for translation file '"+translationFile+"'.");
	}
	
	
	
	
	
	// Candidates for an automated Test:
	
	// Configuration Methods ***********************************************************************
	
	
	// Protected Methods ***********************************************************************

	
	// Data Providers ***********************************************************************
	@DataProvider(name="getTranslationFilePofilterTestData")
	public Object[][] getTranslationFilePofilterTestDataAs2dArray() {
		return TestNGUtils.convertListOfListsTo2dArray(getTranslationFilePofilterTestDataAsListOfLists());
	}
	
	// Returns the tag character (Eg: <b> or </b> return_val = 'b')
	protected char parseElement(String str){
		return str.charAt(str.length()-2);
	}
	
	// Function checks whether every open tag has an appropriate close tag in order
	protected Boolean checkTags(Stack<String> tags, Stack<String> temp, String tagElement) {
		// If there are no open tags in the stack -> return
		if(tags.empty()) return false;
		String popElement = tags.pop();
		// If openTag in stack = closeTag -> return  
		if(!popElement.contains("/") && parseElement(popElement) == parseElement(tagElement)) return true;
		else {
			// Continue popping elements from stack and push to temp until appropriate open tag is found
			while(popElement.contains("/") || parseElement(popElement) != parseElement(tagElement)) {
				temp.push(popElement);
				// If stack = empty and no match, push back the popped elements -> return
				if(tags.empty()) {
					while(!temp.empty()) {
						tags.push(temp.pop());
					}
					return false;
				}
				popElement = tags.pop();
			}
			// If a match is found, push back the popped elements -> return
			while(!temp.empty()) tags.push(temp.pop());
		}
		return true;
	}
	
	protected List<List<Object>> getTranslationFilePofilterTestDataAsListOfLists() {
		
		List<List<Object>> ll = new ArrayList<List<Object>>();
		// see http://translate.sourceforge.net/wiki/toolkit/pofilter_tests
		//	Critical -- can break a program
		//    	accelerators, escapes, newlines, nplurals, printf, tabs, variables, xmltags, dialogsizes
		//	Functional -- may confuse the user
		//    	acronyms, blank, emails, filepaths, functions, gconf, kdecomments, long, musttranslatewords, notranslatewords, numbers, options, purepunc, sentencecount, short, spellcheck, urls, unchanged
		//	Cosmetic -- make it look better
		//    	brackets, doublequoting, doublespacing, doublewords, endpunc, endwhitespace, puncspacing, simplecaps, simpleplurals, startcaps, singlequoting, startpunc, startwhitespace, validchars
		//	Extraction -- useful mainly for extracting certain types of string
		//    	compendiumconflicts, credits, hassuggestion, isfuzzy, isreview, untranslated

		List<String> pofilterTests = Arrays.asList(
				//	Critical -- can break a program
				"accelerators","escapes","newlines","printf","tabs","variables","xmltags",
				//	Functional -- may confuse the user
				"blank","emails","filepaths","gconf","long","notranslatewords","options","short","urls","unchanged",
				//	Cosmetic -- make it look better
				"doublewords",
				//	Extraction -- useful mainly for extracting certain types of string
				"untranslated");
		
		// debugTesting
		
		// Tests completed 	-> 	"escapes","accelerators","newlines","printf","tabs","variables","xmltags","blank","emails","filepaths","gconf","long","short","notranslatewords","unchanged","variables","options"		
		// Ambiguous		-> 	"nplurals","printf","variables"			
		// TODO				->  		
				
		//pofilterTests = Arrays.asList("escapes","accelerators","newlines","printf","tabs","variables","xmltags","blank","emails","filepaths","gconf","long","short","notranslatewords","unchanged","variables","options");

		for (File translationFile : translationFileMap.keySet()) {
			for (String pofilterTest : pofilterTests) {
				BlockedByBzBug bugzilla = null;
				// Bug 825362	[es_ES] failed pofilter accelerator tests for subscription-manager translations 
				if (pofilterTest.equals("accelerators") && translationFile.getPath().contains("/es_ES/")) bugzilla = new BlockedByBzBug("825362");
				// Bug 825367	[zh_CN] failed pofilter accelerator tests for subscription-manager translations 
				if (pofilterTest.equals("accelerators") && translationFile.getPath().contains("/zh_CN/")) bugzilla = new BlockedByBzBug("825367");
				
				
				// Bug 825397	Many translated languages fail the pofilter newlines test
				if (pofilterTest.equals("newlines") && !(translationFile.getPath().contains("/zh_CN/")||translationFile.getPath().contains("/ru/")||translationFile.getPath().contains("/ja/"))) bugzilla = new BlockedByBzBug("825397");			
				// Bug 825393	[ml_IN][es_ES] translations should not use character ¶ for a new line. 
				if (pofilterTest.equals("newlines") && translationFile.getPath().contains("/ml/")) bugzilla = new BlockedByBzBug("825393");
				if (pofilterTest.equals("newlines") && translationFile.getPath().contains("/es_ES/")) bugzilla = new BlockedByBzBug("825393");
				
				
				// Bug 827059	[kn] translation fails for printf test
				if (pofilterTest.equals("printf") && translationFile.getPath().contains("/kn/")) bugzilla = new BlockedByBzBug("827059");
				// Bug 827079 	[es-ES] translation fails for printf test
				if (pofilterTest.equals("printf") && translationFile.getPath().contains("/es_ES/")) bugzilla = new BlockedByBzBug("827079");
				// Bug 827085	[hi] translation fails for printf test
				if (pofilterTest.equals("printf") && translationFile.getPath().contains("/hi/")) bugzilla = new BlockedByBzBug("827085");
				// Bug 827089 	[hi] translation fails for printf test
				if (pofilterTest.equals("printf") && translationFile.getPath().contains("/te/")) bugzilla = new BlockedByBzBug("827089");
				
				
				// Bug 827113 	Many Translated languages fail the pofilter tabs test
				if (pofilterTest.equals("tabs") && !(translationFile.getPath().contains("/pa/")||translationFile.getPath().contains("/mr/")||translationFile.getPath().contains("/de_DE/")||translationFile.getPath().contains("/bn_IN/"))) bugzilla = new BlockedByBzBug("825397");
				
				
				// Bug 827161 	[bn_IN] failed pofilter xmltags tests for subscription-manager translations
				if (pofilterTest.equals("xmltags") && translationFile.getPath().contains("/bn_IN/")) bugzilla = new BlockedByBzBug("827161");
				// Bug 827208	[or] failed pofilter xmltags tests for subscription-manager translations
				if (pofilterTest.equals("xmltags") && translationFile.getPath().contains("/or/")) bugzilla = new BlockedByBzBug("827208");
				// Bug 827214	[or] failed pofilter xmltags tests for subscription-manager translations
				if (pofilterTest.equals("xmltags") && translationFile.getPath().contains("/ta_IN/")) bugzilla = new BlockedByBzBug("827214");
				// Bug 828368 - [kn] failed pofilter xmltags tests for subscription-manager translations
				if (pofilterTest.equals("xmltags") && translationFile.getPath().contains("/kn/")) bugzilla = new BlockedByBzBug("828368");
				// Bug 828365 - [kn] failed pofilter xmltags tests for subscription-manager translations
				if (pofilterTest.equals("xmltags") && translationFile.getPath().contains("/kn/")) bugzilla = new BlockedByBzBug("828365");
				// Bug 828372 - [es_ES] failed pofilter xmltags tests for subscription-manager translations
				if (pofilterTest.equals("xmltags") && translationFile.getPath().contains("/es_ES/")) bugzilla = new BlockedByBzBug("828372");
				// Bug 828416 - [ru] failed pofilter xmltags tests for subscription-manager translations
				if (pofilterTest.equals("xmltags") && translationFile.getPath().contains("/ru/")) bugzilla = new BlockedByBzBug("828416");
				
				
				// Bug 828566	[bn-IN] cosmetic bug for subscription-manager translations
				if (pofilterTest.equals("filepaths") && translationFile.getPath().contains("/bn_IN/")) bugzilla = new BlockedByBzBug("828566");
				// Bug 828567 	[as] cosmetic bug for subscription-manager translations
				if (pofilterTest.equals("filepaths") && translationFile.getPath().contains("/as/")) bugzilla = new BlockedByBzBug("828567");
				// Bug 828576 - [ta_IN] failed pofilter filepaths tests for subscription-manager translations
				if (pofilterTest.equals("filepaths") && translationFile.getPath().contains("/ta_IN/")) bugzilla = new BlockedByBzBug("828576");
				// Bug 828579 - [ml] failed pofilter filepaths tests for subscription-manager translations
				if (pofilterTest.equals("filepaths") && translationFile.getPath().contains("/ml/")) bugzilla = new BlockedByBzBug("828579");
				// Bug 828580 - [mr] failed pofilter filepaths tests for subscription-manager translations
				if (pofilterTest.equals("filepaths") && translationFile.getPath().contains("/mr/")) bugzilla = new BlockedByBzBug("828580");
				// Bug 828583 - [ko] failed pofilter filepaths tests for subscription-manager translations
				if (pofilterTest.equals("filepaths") && translationFile.getPath().contains("/ko/")) bugzilla = new BlockedByBzBug("828583");
				
				
				// Bug 828810 - [kn] failed pofilter varialbes tests for subscription-manager translations
				if (pofilterTest.equals("variables") && translationFile.getPath().contains("/kn/")) bugzilla = new BlockedByBzBug("828810");
				// Bug 828816 - [es_ES] failed pofilter varialbes tests for subscription-manager translations
				if (pofilterTest.equals("variables") && translationFile.getPath().contains("/es_ES/")) bugzilla = new BlockedByBzBug("828816");
				// Bug 828821 - [hi] failed pofilter varialbes tests for subscription-manager translations
				if (pofilterTest.equals("variables") && translationFile.getPath().contains("/hi/")) bugzilla = new BlockedByBzBug("828821");
				// Bug 828867 - [te] failed pofilter varialbes tests for subscription-manager translations
				if (pofilterTest.equals("variables") && translationFile.getPath().contains("/te/")) bugzilla = new BlockedByBzBug("828867");
				
				
				// Bug 828903 - [bn_IN] failed pofilter options tests for subscription-manager translations 
				if (pofilterTest.equals("options") && translationFile.getPath().contains("/bn_IN/")) bugzilla = new BlockedByBzBug("828903");
				// Bug 828930 - [as] failed pofilter options tests for subscription-manager translations
				if (pofilterTest.equals("options") && translationFile.getPath().contains("/as/")) bugzilla = new BlockedByBzBug("828930");
				// Bug 828948 - [or] failed pofilter options tests for subscription-manager translations
				if (pofilterTest.equals("options") && translationFile.getPath().contains("/or/")) bugzilla = new BlockedByBzBug("828948");
				// Bug 828954 - [ta_IN] failed pofilter options test for subscription-manager translations
				if (pofilterTest.equals("options") && translationFile.getPath().contains("/ta_IN/")) bugzilla = new BlockedByBzBug("828954");
				// Bug 828958 - [pt_BR] failed pofilter options test for subscription-manager translations
				if (pofilterTest.equals("options") && translationFile.getPath().contains("/pt_BR/")) bugzilla = new BlockedByBzBug("828958");
				// Bug 828961 - [gu] failed pofilter options test for subscription-manager translations
				if (pofilterTest.equals("options") && translationFile.getPath().contains("/gu/")) bugzilla = new BlockedByBzBug("828961");
				// Bug 828965 - [hi] failed pofilter options test for subscription-manager trasnlations
				if (pofilterTest.equals("options") && translationFile.getPath().contains("/hi/")) bugzilla = new BlockedByBzBug("828965");
				// Bug 828966 - [zh_CN] failed pofilter options test for subscription-manager translations
				if (pofilterTest.equals("options") && translationFile.getPath().contains("/zh_CN/")) bugzilla = new BlockedByBzBug("828966");
				// Bug 828969 - [te] failed pofilter options test for subscription-manager translations
				if (pofilterTest.equals("options") && translationFile.getPath().contains("/te/")) bugzilla = new BlockedByBzBug("828969");
				
				
				// Bug 828985 - [ml] failed pofilter urls test for subscription manager translations
				if (pofilterTest.equals("urls") && translationFile.getPath().contains("/ml/")) bugzilla = new BlockedByBzBug("828985");
				// Bug 828989 - [pt_BR] failed pofilter urls test for subscription-manager translations
				if (pofilterTest.equals("urls") && translationFile.getPath().contains("/pt_BR/")) bugzilla = new BlockedByBzBug("828989");
				
				
				// Bug 829459 - [bn_IN] failed pofilter unchanged option test for subscription-manager translations
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/bn_IN/")) bugzilla = new BlockedByBzBug("829459");
				// Bug 829470 - [or] failed pofilter unchanged options for subscription-manager translations
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/or/")) bugzilla = new BlockedByBzBug("829470");
				// Bug 829471 - [ta_IN] failed pofilter unchanged optioon test for subscription-manager translations
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/ta_IN/")) bugzilla = new BlockedByBzBug("829471");
				// Bug 829476 - [ml] failed pofilter unchanged option test for subscription-manager translations
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/ml/")) bugzilla = new BlockedByBzBug("829476");
				// Bug 829479 - [pt_BR] failed pofilter unchanged option test for subscription manager translations
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/pt_BR/")) bugzilla = new BlockedByBzBug("829479");
				// Bug 829482 - [zh_TW] failed pofilter unchanged option test for subscription manager translations
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/zh_TW/")) bugzilla = new BlockedByBzBug("829482");
				// Bug 829483 - [de_DE] failed pofilter unchanged options test for suubscription manager translations
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/de_DE/")) bugzilla = new BlockedByBzBug("829483");
				// Bug 829486 - [fr] failed pofilter unchanged options test for subscription manager translations
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/fr/")) bugzilla = new BlockedByBzBug("829486");
				// Bug 829488 - [es_ES] failed pofilter unchanged option tests for subscription manager translations				
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/es_ES/")) bugzilla = new BlockedByBzBug("829488");
				// Bug 829491 - [it] failed pofilter unchanged option test for subscription manager translations
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/it/")) bugzilla = new BlockedByBzBug("829491");
				// Bug 829492 - [hi] failed pofilter unchanged option test for subscription manager translations
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/hi/")) bugzilla = new BlockedByBzBug("829492");
				// Bug 829494 - [zh_CN] failed pofilter unchanged option for subscription manager translations
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/zh_CN/")) bugzilla = new BlockedByBzBug("829494");
				// Bug 829495 - [pa] failed pofilter unchanged option test for subscription manager translations
				if (pofilterTest.equals("unchanged") && translationFile.getPath().contains("/pa/")) bugzilla = new BlockedByBzBug("829495");
				
				
				ll.add(Arrays.asList(new Object[] {bugzilla,	pofilterTest,	translationFile}));
				 
			}
		}
		return ll;
	}
	
}

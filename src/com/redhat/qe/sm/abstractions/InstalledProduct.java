package com.redhat.qe.sm.abstractions;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class InstalledProduct extends CandlepinAbstraction {
	
	public String productName;
	public String status;
	public Date expires;
	public Integer subscription;
	
	public InstalledProduct(Map<String, String> productData) {
		super(productData);
	}

	@Override
	public String toString() {
		
//		public String productName;
//		public String status;
//		public Date expires;
//		public Integer subscription;
		
		String string = "";
		if (productName != null)		string += String.format(" %s='%s'", "productName",productName);
		if (subscription != null)		string += String.format(" %s='%s'", "subscription",subscription);
		if (status != null)				string += String.format(" %s='%s'", "status",status);
		if (expires != null)			string += String.format(" %s='%s'", "expires",expires);

		return string.trim();
	}
	
	
	/**
	 * @param stdoutListingOfProductCerts - stdout from "subscription-manager-cli list"
	 * @return
	 */
	static public List<InstalledProduct> parse(String stdoutListingOfProductCerts) {
		/*
		# subscription-manager-cli list
		+-------------------------------------------+
		    Installed Product Status
		+-------------------------------------------+

		ProductName:        	Shared Storage (GFS)     
		Status:             	Not Installed            
		Expires:            	2011-07-01               
		Subscription:       	17                       
		ContractNumber:        	0   
		*/
		
		Map<String,String> regexes = new HashMap<String,String>();
		
//		regexes.put("productName",	"ProductName:\\s*([a-zA-Z0-9 ,:()]*)");
//		regexes.put("status",		"Status:\\s*([a-zA-Z0-9 ,:()]*)");
//		regexes.put("expires",		"Expires:\\s*([a-zA-Z0-9 ,:()]*)");
//		regexes.put("subscription",	"Subscription:\\s*([a-zA-Z0-9 ,:()]*)");
		
		// ProductCert abstractionField			pattern		(Note: the abstractionField must be defined in the ProductCert class)
		regexes.put("productName",				"ProductName:\\s*(.*)");
		regexes.put("status",					"Status:\\s*(.*)");
		regexes.put("expires",					"Expires:\\s*(.*)");
		regexes.put("subscription",				"Subscription:\\s*(.*)");
		
		List<Map<String,String>> productCertList = new ArrayList<Map<String,String>>();
		for(String field : regexes.keySet()){
			Pattern pat = Pattern.compile(regexes.get(field), Pattern.MULTILINE);
			addRegexMatchesToList(pat, stdoutListingOfProductCerts, productCertList, field);
		}
		
		List<InstalledProduct> productCerts = new ArrayList<InstalledProduct>();
		for(Map<String,String> prodCertMap : productCertList)
			productCerts.add(new InstalledProduct(prodCertMap));
		return productCerts;
	}
}
package com.redhat.qe.sm.abstractions;

import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public abstract class CandlepinAbstraction {
	protected static String simpleDateFormat = "yyyy-MM-dd";	// 2010-07-01	// default SimpleDateFormat 
	protected static Logger log = Logger.getLogger(CandlepinAbstraction.class.getName());
	

	
	
	public CandlepinAbstraction(Map<String, String> productData){
		if (productData == null)
			return;
		
		for (String keyField : productData.keySet()){
			Field abstractionField = null;
			try {
				abstractionField = this.getClass().getField(keyField);
//				if (abstractionField.getType().equals(Date.class))
//					abstractionField.set(this, this.parseDateString(productData.get(keyField)));
				if (abstractionField.getType().equals(Calendar.class))
					abstractionField.set(this, this.parseDateString(productData.get(keyField)));
//				else if (abstractionField.getType().equals(Integer.class))
//					abstractionField.set(this, Integer.parseInt(productData.get(keyField)));
				else if (abstractionField.getType().equals(Integer.class))
					abstractionField.set(this, this.parseInt(productData.get(keyField)));
				else if (abstractionField.getType().equals(Boolean.class))
					abstractionField.set(this, productData.get(keyField).toLowerCase().contains("true"));
				else
					abstractionField.set(this, productData.get(keyField));
			} catch (Exception e){
				log.warning("Exception caught while creating Candlepin abstraction: " + e.getMessage());
				if (abstractionField != null)
					try {
						abstractionField.set(this, null);
					} catch (Exception x){
						log.warning("and we can't even set it to null.  Whaaaaaa?");
					}
				for (StackTraceElement ste:e.getStackTrace()){
					log.warning(ste.toString());
				}
			}
		}
	}
	
	
	@Override
	public boolean equals(Object obj){
		CandlepinAbstraction certObj = (CandlepinAbstraction)obj;
		boolean matched = true;
		for(Field certField:certObj.getClass().getDeclaredFields()){
			try {
				Field correspondingField = this.getClass().getField(certField.getName());
				matched = correspondingField.get(this).equals(certField.get(certObj));
			} catch (Exception e) {
				return false;
			}
		}
		return matched;
	}
	
	
	// protected methods ************************************************************

	protected Calendar parseDateString(String dateString){
		return parseDateString(dateString, simpleDateFormat);
	}
	
	protected final Calendar parseDateString(String dateString, String simpleDateFormat){
		try{
			DateFormat dateFormat = new SimpleDateFormat(simpleDateFormat);
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(dateFormat.parse(dateString));
			return calendar;
		}
		catch (ParseException e){
			log.warning("Failed to parse date string '"+dateString+"' with format '"+simpleDateFormat+"':\n"+e.getMessage());
			return null;
		}
	}

	public static String formatDateString(Calendar date){
		DateFormat dateFormat = new SimpleDateFormat(simpleDateFormat);
		return dateFormat.format(date.getTime());
	}
	
	protected Integer parseInt(String intString){
		return Integer.parseInt(intString);
	}
	
	static protected boolean addRegexMatchesToList(Pattern regex, String to_parse, List<Map<String,String>> matchList, String sub_key) {
		Matcher matcher = regex.matcher(to_parse);
		int currListElem=0;
		while (matcher.find()){
			if (matchList.size() < currListElem + 1) matchList.add(new HashMap<String,String>());
			Map<String,String> matchMap = matchList.get(currListElem);
			matchMap.put(sub_key, matcher.group(1).trim());
			matchList.set(currListElem, matchMap);
			currListElem++;
		}
		return true;
	}
			
	
	static protected boolean addRegexMatchesToMap(Pattern regex, String to_parse, Map<String, Map<String,String>> matchMap, String sub_key) {
        Matcher matcher = regex.matcher(to_parse);
        while (matcher.find()) {
            Map<String,String> singleCertMap = matchMap.get(matcher.group(1));
            if(singleCertMap == null){
            	Map<String,String> newBranch = new HashMap<String,String>();
            	singleCertMap = newBranch;
            }
            singleCertMap.put(sub_key, matcher.group(2));
            matchMap.put(matcher.group(1), singleCertMap);
        }
        return true;
	}
}

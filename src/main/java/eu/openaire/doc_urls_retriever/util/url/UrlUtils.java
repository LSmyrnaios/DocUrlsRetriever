package eu.openaire.doc_urls_retriever.util.url;

import java.util.Collection;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.openaire.doc_urls_retriever.crawler.MachineLearning;
import org.apache.commons.lang3.StringUtils;

import eu.openaire.doc_urls_retriever.crawler.CrawlerController;
import eu.openaire.doc_urls_retriever.util.file.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author Lampros A. Smyrnaios
 */
public class UrlUtils
{
	private static final Logger logger = LoggerFactory.getLogger(UrlUtils.class);
	
	public final static Pattern URL_TRIPLE = Pattern.compile("(.+:\\/\\/(?:www(?:(?:\\w+)?\\.)?)?([\\w\\.\\-]+)(?:[\\:\\d]+)?\\/(?:.+\\/)?(?:[\\w.-]*[^\\.pdf]\\?[\\w.-]+[^site]=)?)(.+)?");
	// URL_TRIPLE regex to group domain, path and ID --> group <1> is the regular PATH, group<2> is the DOMAIN and group <3> is the regular "ID".
	
	public static final Pattern URL_DIRECTORY_FILTER = Pattern.compile(".+\\/(?:login|join|subscr|register|submit|post|import|announcement|feed|about|citation|faq|wiki|support|error|misuse|abuse|notfound|contribute|subscription|advertisers"
																	+ "|author|editor|license|disclaimer|policies|policy|privacy|terms|sitemap|account|search|statistics|cookie|application|help|law|permission|ethic|contact|survey|wallet"
																	+ "|template|logo|image|photo|profile).*");
	// We check them as a directory to avoid discarding publications's urls about these subjects.
	
	public static final Pattern PAGE_FILE_EXTENSION_FILTER = Pattern.compile(".+\\.(?:ico|css|js|gif|jpg|jpeg|png|wav|mp3|mp4|webm|mkv|pt|mso|dtl)(?:\\?.+=.+)?$");
	
    public static final Pattern INNER_LINKS_FILE_EXTENSION_FILTER = Pattern.compile(".+:\\/\\/.+\\.(?:ico|css|js|gif|jpg|jpeg|png|wav|mp3|mp4|webm|mkv|pt|xml|mso|dtl)(?:\\?.+=.+)?$");
    // Here don't include .php and relative extensions, since even this can be a docUrl. For example: https://www.dovepress.com/getfile.php?fileID=5337
	// So, we make a new REGEX for these extensions, this time, without a potential argument in the end (?id=XXX..)
	public static final Pattern PLAIN_PAGE_EXTENSION_FILTER = Pattern.compile(".+\\.(?:php|php2|php3|php4|php5|phtml|htm|html|shtml|xht|xhtm|xhtml|xml|aspx|asp|jsp)$");
	
	public static final Pattern INNER_LINKS_FILE_FORMAT_FILTER = Pattern.compile(".+:\\/\\/.+format=(?:xml|htm|html|shtml|xht|xhtm|xhtml).*");
    
    public static final Pattern SPECIFIC_DOMAIN_FILTER = Pattern.compile(".+:\\/\\/.*(?:google|goo.gl|gstatic|facebook|twitter|youtube|linkedin|wordpress|s.w.org|ebay|bing|amazon|wikipedia|myspace|yahoo|mail|pinterest|reddit|blog|tumblr"
																					+ "|evernote|skype|microsoft|adobe|buffer|digg|stumbleupon|addthis|delicious|dailymotion|gostats|blogger|copyright|friendfeed|newsvine|telegram|getpocket"
																					+ "|flipboard|instapaper|line.me|telegram|vk|ok.rudouban|baidu|qzone|xing|renren|weibo).*\\/.*");
    
    public static final Pattern PLAIN_DOMAIN_FILTER = Pattern.compile(".+:\\/\\/[\\w.:-]+(?:\\/)?$");	// Exclude plain domains' urls.
	
    public static final Pattern JSESSIONID_FILTER = Pattern.compile(".+:\\/\\/.+(;(?:JSESSIONID|jsessionid)=.*[?\\w\\W]+$)");
	
    public static final Pattern DOC_URL_FILTER = Pattern.compile(".+:\\/\\/.+(pdf|download|/doc|document|(?:/|[?]|&)file|/fulltext|attachment|/paper|viewfile|viewdoc|/get|cgi/viewcontent.cgi?).*");
    // "DOC_URL_FILTER" works for lowerCase Strings (we make sure they are in lowerCase before we check).
    // Note that we still need to check if it's an alive link and if it's actually a docUrl (though it's mimeType).
	
	public static final Pattern MIME_TYPE_FILTER = Pattern.compile("([\\w]+\\/[\\w]+)(?:;[\\s]*[\\w]+\\=.+)?");
	
	public static int sumOfDocsFound = 0;	// Change it back to simple int if finally in singleThread mode
	public static long inputDuplicatesNum = 0;
	
	public static HashSet<String> duplicateUrls = new HashSet<String>();
	public static HashSet<String> docUrls = new HashSet<String>();
	public static HashSet<String> knownDocTypes = new HashSet<String>();
	
	// Counters for certain unwanted domains. We show statistics in the end.
	public static int elsevierLinks = 0;
	public static int doajResultPageLinks = 0;
	public static int dlibHtmlDocUrls = 0;
	public static int ifnmuDeepCrawlingPages = 0;
	
	static {
		logger.debug("Setting knownDocTypes. Currently testing only \".pdf\" type.");
		knownDocTypes.add("application/pdf");	// For the moment we care only for the pdf type.
	}

	
	/**
	 * This method loads the urls from the input file in memory and check their type.
	 * Then, the loaded urls will either reach the connection point, were they will be checked for a docMimeType or they will be send directly for crawling.
	 * @throws RuntimeException
	 */
	public static void loadAndCheckUrls() throws RuntimeException
	{
		Collection<String> loadedUrlGroup;
		
		boolean firstRun = true;
		
		// Start loading and checking urls.
        while ( true )
        {
        	loadedUrlGroup = FileUtils.getNextUrlGroupFromJson(); // Take urls from jsonFile.
			
			//loadedUrlGroup = FileUtils.getNextUrlGroupTest();	// Take urls from single-columned (testing) csvFile.
			
	        if ( loadedUrlGroup.isEmpty() ) {
	        	if ( firstRun ) {
	        		logger.error("Could not retrieve any urls from the inputFile!");
	        		throw new RuntimeException();
	        	}
	        	else {
	        		logger.debug("Done loading " + FileUtils.getCurrentlyLoadedUrls() + " urls from the inputFile.");	// DEBUG!
	        		break;	// No more urls to load and check, initialize M.L.A. (if wanted) and start Crawling.
	        	}
	        }
			
			firstRun = false;
	        
			for ( String retrievedUrl : loadedUrlGroup )
			{
				String lowerCaseUrl = retrievedUrl.toLowerCase();	// Only for string checking purposes, not supposed to reach any connection.
				
				if ( matchesCertainUrlTypesAtLoading(retrievedUrl, lowerCaseUrl) )
					continue;
				
				// Remove "jsessionid" for urls. Most of them, if not all, will already be expired.
				if ( lowerCaseUrl.contains("jsessionid") )
					retrievedUrl = UrlUtils.removeJsessionid(retrievedUrl);
				
				// Check if it's a duplicate. (if already found before inside or outside the Crawler4j).
	        	if ( UrlUtils.duplicateUrls.contains(retrievedUrl) ) {
	        		logger.debug("Skipping url: \"" + retrievedUrl + "\", at loading, as it has already been seen!");	// DEBUG!
	        		UrlUtils.inputDuplicatesNum ++;
	        		UrlUtils.logUrl(retrievedUrl, "duplicate", "Discarded at loading time, as it's a duplicate.");
	        		continue;
	        	}
	        	
				CrawlerController.controller.addSeed(retrievedUrl);	// If this is not a valid url, Crawler4j will throw it away by itself.
				
			}// end for-loop
        }// end while-loop
		
		
		// If we decide to use the MLA, then initialize it.
		if ( MachineLearning.useMLA )
			new MachineLearning();
	}
	
	
	/**
	 * This method takes the "retrievedUrl" from the inputFile and the "lowerCaseUrl" that comes out the retrieved one.
	 * It then checks if the "lowerCaseUrl" matched certain criteria representing the unwanted urls' types. It uses the "retrievedUrl" for proper logging.
	 * If these criteria match, then it logs the url and returns "true", otherwise, it returns "false".
	 * @param lowerCaseUrl
	 * @return true/false
	 */
	public static boolean matchesCertainUrlTypesAtLoading(String retrievedUrl, String lowerCaseUrl)
	{
		if ( lowerCaseUrl.contains("doaj.org/toc/") ) {	// Avoid resultPages.
			UrlUtils.doajResultPageLinks ++;
			UrlUtils.logUrl(retrievedUrl, "unreachable", "Discarded at loading time, after matching to the Results-directory: \"doaj.org/toc/\".");
			return true;
		}
		else if ( lowerCaseUrl.contains("dlib.org") ) {    // Avoid HTML docUrls.
			UrlUtils.dlibHtmlDocUrls ++;
			UrlUtils.logUrl(retrievedUrl, "unreachable", "Discarded at loading time, after matching to the HTML-docUrls site: \"dlib.org\".");
			return true;
		}
		else if ( lowerCaseUrl.contains("ojs.ifnmu.edu.ua") ) {	// Avoid crawling in larger depth.
			UrlUtils.ifnmuDeepCrawlingPages ++;
			UrlUtils.logUrl(retrievedUrl,"unreachable", "Discarded at loading time, after matching to the increasedCrawlingDepth-site: \"ojs.ifnmu.edu.ua\".");
			return true;
		}
		else if ( UrlUtils.PLAIN_DOMAIN_FILTER.matcher(lowerCaseUrl).matches() ||UrlUtils.SPECIFIC_DOMAIN_FILTER.matcher(lowerCaseUrl).matches()
				|| UrlUtils.URL_DIRECTORY_FILTER.matcher(lowerCaseUrl).matches() || UrlUtils.PAGE_FILE_EXTENSION_FILTER.matcher(lowerCaseUrl).matches())
		{
			UrlUtils.logUrl(retrievedUrl, "unreachable", "Discarded at loading time, after matching to unwantedType-regex-rules.");
			return true;
		}
		else
			return false;
	}


    /**
     * This method logs the outputEntry to be written, as well as the docUrlPath (if non-empty String) and adds entries in the blackList.
     * @param sourceUrl
     * @param initialDocUrl
     * @param errorCause
     */
    public static void logUrl(String sourceUrl, String initialDocUrl, String errorCause)
    {
        String finalDocUrl = initialDocUrl;
		
        if ( !finalDocUrl.equals("unreachable") && !finalDocUrl.equals("duplicate") )	// If we have reached a docUrl..
        {
            // Remove "jsessionid" for urls for "cleaner" output.
            if ( finalDocUrl.contains("jsessionid") || finalDocUrl.contains("JSESSIONID") )
                if ( (finalDocUrl = UrlUtils.removeJsessionid(initialDocUrl)) == null )	// If there is problem removing the "jsessionid" and it return "null", reassign the initial value.
                    finalDocUrl = initialDocUrl;
			
            logger.debug("docUrl found: <" + finalDocUrl + ">");
            sumOfDocsFound ++;
			
            // Gather data for the MLA, if we, or the program itself, decide so.
            if ( MachineLearning.useMLA )
				MachineLearning.gatherMLData(sourceUrl, finalDocUrl);
			
            docUrls.add(finalDocUrl);	// Add it here, in order to be able to recognize it and quick-log it later, but also to distinguish it from other duplicates.
        }
        else if ( !finalDocUrl.equals("duplicate") )	{// Else if this url is not a docUrl and has not been processed before..
            duplicateUrls.add(sourceUrl);	 // Add it in duplicates BlackList, in order not to be accessed for 2nd time in the future..
        }	// We don't add docUrls here, as we want them to be separate for checking purposes/
		
        //logger.debug("docUrl received in \"UrlUtils.logUrl()\": "+  docUrl);	// DEBUG!
		
        FileUtils.tripleToBeLoggedOutputList.add(new TripleToBeLogged(sourceUrl, finalDocUrl, errorCause));	// Log it to be written later.
		
        if ( FileUtils.tripleToBeLoggedOutputList.size() == FileUtils.groupCount )	// Write to file every time we have a group of <groupCount> urls' sets.
            FileUtils.writeToFile();
    }


    /**
     * This method takes a url and its mimeType and checks if it's a document mimeType or not.
     * @param linkStr
     * @param mimeType
     * @return boolean
     */
    public static boolean hasDocMimeType(String linkStr, String mimeType)
    {
		String plainMimeType = mimeType;	// Make sure we don't cause any NPE later on..
    	if ( mimeType.contains("charset") )
		{
			plainMimeType = removeCharsetFromMimeType(mimeType);
			
			if ( plainMimeType == null ) {    // If there was any error removing the charset, still try to save any docMimeType (currently pdf-only).
				if (mimeType.contains("pdf"))
					return true;
				else
					return false;
			}
		}
		
		if ( knownDocTypes.contains(plainMimeType) )
            return true;
        else if ( plainMimeType.equals("application/octet-stream") && linkStr.toLowerCase().contains("pdf") )
            // This is a special case. (see: "https://kb.iu.edu/d/agtj")
            // TODO - When we will accept more docTypes, match it against "DOC_URL_FILTER" instead of just "pdf".
            return true;
        else
            return false;
    }
    
	
	/**
	 * This method receives the mimeType and returns it without the "charset" part.
	 * If there is any error, it returns null.
	 * @param mimeType
	 * @return charset-free mimeType
	 */
	public static String removeCharsetFromMimeType(String mimeType)
	{
		String plainMimeType = null;
		
		Matcher mimeMatcher = UrlUtils.MIME_TYPE_FILTER.matcher(mimeType);
		if ( mimeMatcher.matches() )
		{
			plainMimeType = mimeMatcher.group(1);
			if ( plainMimeType == null || plainMimeType.isEmpty() ) {
				logger.warn("Unexpected null or empty value returned by \"mimeMatcher.group(1)\" for mimeType: \"" + mimeType + "\".");
				return null;
			}
		}
		else {
			logger.warn("Unexpected MIME_TYPE_FILTER's (" + mimeMatcher.toString() + ") mismatch for mimeType: \"" + mimeType + "\"");
			return null;
		}
		
		return plainMimeType;
	}
	
	
	/**
	 * This method returns the domain of the given url, in lowerCase (for better comparison).
	 * @param urlStr
	 * @return domainStr
	 */
	public static String getDomainStr(String urlStr)
	{
		 if ( (urlStr == null) || urlStr.isEmpty() ) {
			logger.error("A null or an empty String was given when called \"getDomainStr()\" method!");
			return null;
		}
		
		String domainStr = null;
		Matcher matcher = URL_TRIPLE.matcher(urlStr);
		
		if ( matcher.matches() )
		{
		    domainStr = matcher.group(2);	// Group <2> is the DOMAIN.
		    if ( (domainStr == null) || domainStr.isEmpty() ) {
		    	logger.warn("Unexpected null or empty value returned by \"matcher.group(2)\" for url: \"" + urlStr + "\".");
		    	return null;
		    }
		}
		else {
			logger.warn("Unexpected URL_TRIPLE's (" + matcher.toString() + ") mismatch for url: \"" + urlStr + "\"");
			return null;
		}
		
		return domainStr.toLowerCase();	// We return it in lowerCase as we don't want to store double domains. (it doesn't play any part in connectivity, only the rest of the url is case-sensitive.)
	}


	/**
	 * This method returns the path of the given url.
	 * @param urlStr
	 * @return pathStr
	 */
	public static String getPathStr(String urlStr)
	{
		if ( (urlStr == null) || urlStr.isEmpty() ) {
			logger.error("A null or an empty String was given when called \"getPathStr()\" method!");
			return null;
		}
		
		String pathStr = null;
		Matcher matcher = URL_TRIPLE.matcher(urlStr);
		
		if ( matcher.matches() )
		{
			pathStr = matcher.group(1);	// Group <1> is the PATH.
			if ( (pathStr == null) || pathStr.isEmpty() ) {
				logger.warn("Unexpected null or empty value returned by \"matcher.group(1)\" for url: \"" + urlStr + "\".");
				return null;
			}
		}
		else {
			logger.warn("Unexpected URL_TRIPLE's (" + matcher.toString() + ") mismatch for url: \"" + urlStr + "\"");
			return null;
		}
		
		return pathStr;
	}


	/**
	 * This method is responsible for removing the "jsessionid" part of a url.
	 * If no jsessionId is found, then it returns the string it recieved.
	 * @param urlStr
	 * @return urlWithoutJsessionId
	 */
	public static String removeJsessionid(String urlStr)
	{
		String finalUrl = urlStr;
		
		String jsessionid = null;
		
		Matcher jsessionIdMatcher = JSESSIONID_FILTER.matcher(urlStr);
		if (jsessionIdMatcher.matches())
		{
			jsessionid = jsessionIdMatcher.group(1);	// Take only the 1st part of the urlStr, without the jsessionid.
		    if ( (jsessionid == null) || jsessionid.isEmpty() ) {
		    	logger.warn("Unexpected null or empty value returned by \"jsessionIdMatcher.group(1)\" for url: \"" + urlStr + "\"");
		    	return finalUrl;
		    }
		    finalUrl = StringUtils.replace(finalUrl, jsessionid, "");
		}
		else
			logger.warn("Unexpected \"JSESSIONID_FILTER\" mismatch for url: \"" + urlStr + "\" !");
		
		return finalUrl;
	}

}

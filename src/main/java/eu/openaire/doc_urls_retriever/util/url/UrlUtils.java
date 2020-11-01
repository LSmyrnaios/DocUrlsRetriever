package eu.openaire.doc_urls_retriever.util.url;

import eu.openaire.doc_urls_retriever.crawler.MachineLearning;
import eu.openaire.doc_urls_retriever.util.file.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @author Lampros Smyrnaios
 */
public class UrlUtils
{
	private static final Logger logger = LoggerFactory.getLogger(UrlUtils.class);

	public static final Pattern URL_TRIPLE = Pattern.compile("(.+://(?:ww(?:w|\\d)(?:(?:\\w+)?\\.)?)?([\\w.\\-]+)(?:[:\\d]+)?(?:.*/)?)(?:([^/^;^?]*)(?:(?:;|\\?)[^/^=]+(?:=.*)?)?)?");
	// URL_TRIPLE regex to group domain, path and ID --> group <1> is the regular PATH, group<2> is the DOMAIN and group <3> is the regular "ID".
	// TODO - Add explanation also for the non-captured groups for better maintenance. For example the "ww(?:w|\d)" can capture "www", "ww2", "ww3" ect.

	public static final Pattern JSESSIONID_FILTER = Pattern.compile("(.+://.+)(?:;(?:JSESSIONID|jsessionid)=[^?]+)(\\?.+)?");	// Remove the jsessionid but keep the url-params in the end.

	public static final Pattern ANCHOR_FILTER = Pattern.compile("(.+)(#.+)");	// Remove the anchor at the end of the url to avoid duplicate versions. (anchors might exist even in docUrls themselves)

	public static int sumOfDocUrlsFound = 0;	// Change it back to simple int if finally in singleThread mode

	public static final HashSet<String> duplicateUrls = new HashSet<String>();

	public static final HashMap<String, String> docUrlsWithIDs = new HashMap<String, String>();	// Null keys are allowed (in case they are not available in the input).

	public static final String alreadyDownloadedByIDMessage = "This file is probably already downloaded from ID=";


	/**
     * This method logs the outputEntry to be written, as well as the docUrlPath (if non-empty String) and adds entries in the blackList.
	 * @param urlId (it may be null if no id was provided in the input)
	 * @param sourceUrl
	 * @param pageUrl
	 * @param docUrl
	 * @param comment
	 * @param pageDomain (it may be null)
	 */
    public static void logQuadruple(String urlId, String sourceUrl, String pageUrl, String docUrl, String comment, String pageDomain)
    {
        String finalDocUrl = docUrl;

        if ( !finalDocUrl.equals("duplicate") )
        {
			if ( !finalDocUrl.equals("unreachable") ) {
				sumOfDocUrlsFound ++;

				// Remove "jsessionid" from urls for "cleaner" output and "already found docUrl"-matching.
				String lowerCaseUrl = finalDocUrl.toLowerCase();
				if ( lowerCaseUrl.contains("jsessionid") )
					finalDocUrl = UrlUtils.removeJsessionid(docUrl);

				// Gather data for the MLA, if we decide to have it enabled.
				if ( MachineLearning.useMLA )
					MachineLearning.gatherMLData(pageUrl, finalDocUrl, pageDomain);

				if ( !comment.contains(UrlUtils.alreadyDownloadedByIDMessage) )	// Add this id, only if this is a first-crossed docUrl.
					docUrlsWithIDs.put(finalDocUrl, urlId);	// Add it here, in order to be able to recognize it and quick-log it later, but also to distinguish it from other duplicates.
			}
			else	// Else if this url is not a docUrl and has not been processed before..
				duplicateUrls.add(sourceUrl);	// Add it in duplicates BlackList, in order not to be accessed for 2nd time in the future. We don't add docUrls here, as we want them to be separate for checking purposes.
		}

		FileUtils.quadrupleToBeLoggedList.add(new QuadrupleToBeLogged(urlId, sourceUrl, finalDocUrl, comment));	// Log it to be written later in the outputFile.

        if ( FileUtils.quadrupleToBeLoggedList.size() == FileUtils.jsonBatchSize )	// Write to file every time we have a group of <jsonBatchSize> quadruples.
            FileUtils.writeToFile();
    }


	/**
	 * This method returns the domain of the given url, in lowerCase (for better comparison).
	 * @param urlStr
	 * @param matcher
	 * @return domainStr
	 */
	public static String getDomainStr(String urlStr, Matcher matcher)
	{
		if ( matcher == null )
			if ( (matcher = getUrlMatcher(urlStr)) == null )
				return null;

		String domainStr = null;
		try {
			domainStr = matcher.group(2);	// Group <2> is the DOMAIN.
		} catch (Exception e) { logger.error("", e); }
		if ( (domainStr == null) || domainStr.isEmpty() ) {
			logger.warn("No domain was extracted from url: \"" + urlStr + "\".");
			return null;
		}

		return domainStr.toLowerCase();	// We return it in lowerCase as we don't want to store double domains. (it doesn't play any part in connectivity, only the rest of the url is case-sensitive.)
	}


	/**
	 * This method returns the path of the given url.
	 * @param urlStr
	 * @param matcher
	 * @return pathStr
	 */
	public static String getPathStr(String urlStr, Matcher matcher)
	{
		if ( matcher == null )
			if ( (matcher = getUrlMatcher(urlStr)) == null )
				return null;

		String pathStr = null;
		try {
			pathStr = matcher.group(1);	// Group <1> is the PATH.
		} catch (Exception e) { logger.error("", e); }
		if ( (pathStr == null) || pathStr.isEmpty() ) {
			logger.warn("No pathStr was extracted from url: \"" + urlStr + "\".");
			return null;
		}

		return pathStr;
	}


	/**
	 * This method returns the path of the given url.
	 * @param urlStr
	 * @param matcher
	 * @return pathStr
	 */
	public static String getDocIdStr(String urlStr, Matcher matcher)
	{
		if ( matcher == null )
			if ( (matcher = getUrlMatcher(urlStr)) == null )
				return null;

		String docIdStr = null;
		try {
			docIdStr = matcher.group(3);	// Group <3> is the docId.
		} catch (Exception e) { logger.error("", e); }
		if ( (docIdStr == null) || docIdStr.isEmpty() ) {
			logger.warn("No docID was extracted from url: \"" + urlStr + "\".");
			return null;
		}

		return docIdStr;
	}


	public static Matcher getUrlMatcher(String urlStr)
	{
		if ( urlStr == null ) {	// Avoid NPE in "Matcher"
			logger.error("The received \"urlStr\" was null in \"getUrlMatcher()\"!");
			return null;
		}

		// If the url ends with "/" then remove it as its a "mistake" and the last part of it is the "docID" we want.
		if ( urlStr.endsWith("/") )
			urlStr = urlStr.substring(0, (urlStr.length() -1));
		Matcher urlMatcher = UrlUtils.URL_TRIPLE.matcher(urlStr);
		if ( urlMatcher.matches() )
			return urlMatcher;
		else {
			logger.warn("Unexpected URL_TRIPLE's (" + urlMatcher.toString() + ") mismatch for url: \"" + urlStr + "\"");
			return null;
		}
	}


	/**
	 * This method is responsible for removing the "jsessionid" part of a url.
	 * If no jsessionId is found, then it returns the string it received.
	 * @param urlStr
	 * @return urlWithoutJsessionId
	 */
	public static String removeJsessionid(String urlStr)
	{
		if ( urlStr == null ) {	// Avoid NPE in "Matcher"
			logger.error("The received \"urlStr\" was null in \"removeJsessionid()\"!");
			return null;
		}

		String finalUrl = urlStr;

		String preJsessionidStr = null;
		String afterJsessionidStr = null;

		Matcher jsessionidMatcher = JSESSIONID_FILTER.matcher(urlStr);
		if ( jsessionidMatcher.matches() )
		{
			try {
				preJsessionidStr = jsessionidMatcher.group(1);	// Take only the 1st part of the urlStr, without the jsessionid.
			} catch (Exception e) { logger.error("", e); }
		    if ( (preJsessionidStr == null) || preJsessionidStr.isEmpty() ) {
		    	logger.warn("Unexpected null or empty value returned by \"jsessionidMatcher.group(1)\" for url: \"" + urlStr + "\"");
		    	return finalUrl;
		    }
		    finalUrl = preJsessionidStr;

		    try {
		    	afterJsessionidStr = jsessionidMatcher.group(2);
			} catch (Exception e) { logger.error("", e); }
			if ( (afterJsessionidStr == null) || afterJsessionidStr.isEmpty() )
				return finalUrl;
			else
				return finalUrl + afterJsessionidStr;
		}
		else
			return finalUrl;
	}


	/**
	 * This method removes the anchor part in the end of the URL.
	 * Unlike the jsessionid-case, the anchor consists from everything is from "#" to the right of the url-string till the end of it. No "valuable part" exist after the anchor.
	 * @param urlStr
	 * @return
	 */
	public static String removeAnchor(String urlStr)
	{
		if ( urlStr == null ) {	// Avoid NPE in "Matcher"
			logger.error("The received \"urlStr\" was null in \"removeAnchor()\"!");
			return null;
		}

		String noAnchorUrl = null;

		Matcher anchorMatcher = ANCHOR_FILTER.matcher(urlStr);
		if ( anchorMatcher.matches() )
		{
			try {
				noAnchorUrl = anchorMatcher.group(1);	// Take only the 1st part of the urlStr, without the jsessionid.
			} catch (Exception e) { logger.error("", e); }
			if ( (noAnchorUrl == null) || noAnchorUrl.isEmpty() ) {
				logger.warn("Unexpected null or empty value returned by \"anchorMatcher.group(1)\" for url: \"" + urlStr + "\"");
				return urlStr;
			}
			else
				return noAnchorUrl;
		}
		else
			return urlStr;
	}

}

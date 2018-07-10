package eu.openaire.doc_urls_retriever.util.url;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.openaire.doc_urls_retriever.crawler.MachineLearning;

import eu.openaire.doc_urls_retriever.util.file.FileUtils;
import eu.openaire.doc_urls_retriever.util.http.ConnSupportUtils;
import eu.openaire.doc_urls_retriever.util.http.HttpConnUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This class contains various methods and regexes to handle the URLs.
 * @author Lampros A. Smyrnaios
 */
public class UrlUtils
{
	private static final Logger logger = LoggerFactory.getLogger(UrlUtils.class);
	
	public static final Pattern URL_TRIPLE = Pattern.compile("(.+:\\/\\/(?:www(?:(?:\\w+)?\\.)?)?([\\w\\.\\-]+)(?:[\\:\\d]+)?(?:.*\\/)?(?:[\\w\\.\\,\\-\\_\\%\\:\\;\\~]*\\?[\\w\\.\\,\\-\\_\\%\\:\\;\\~]+\\=)?)(.+)?");
	// URL_TRIPLE regex to group domain, path and ID --> group <1> is the regular PATH, group<2> is the DOMAIN and group <3> is the regular "ID".
	
	public static final Pattern URL_DIRECTORY_FILTER =
			Pattern.compile(".*\\/(?:profile|login|auth\\.|authentication\\.|ac(?:c)?ess|join|subscr|register|submit|post\\/|send\\/|shop\\/|watch|import|bookmark|announcement|rss|feed|about|faq|wiki|news|events|cart|support|sitemap|htmlmap|license|disclaimer|polic(?:y|ies)|privacy|terms|help|law"
							+ "|(?:my|your)?account|user|fund|aut(?:h)?or|editor|citation|review|external|statistics|application|permission|ethic|conta(?:c)?t|survey|wallet|contribute|deposit|donate|template|logo|image|photo|advertiser|people|(?:the)?press"
							+ "|error|(?:mis|ab)use|gateway|sorryserver|cookieabsent|notfound|404\\.(?:\\w)?htm).*");
	// We check them as a directory to avoid discarding publications's urls about these subjects. There's "acesso" (single "c") in Portuguese.. Also there's "autore" & "contatto" in Italian.
	
	public static final Pattern CURRENTLY_UNSUPPORTED_DOC_EXTENSION_FILTER = Pattern.compile(".+\\.(?:doc|docx|ppt|pptx)(?:\\?.+)?$");	// Doc-extensions which are currently unsupported.
	
	public static final Pattern PAGE_FILE_EXTENSION_FILTER = Pattern.compile(".+\\.(?:ico|css|js|gif|jpg|jpeg|png|wav|mp3|mp4|webm|mkv|mov|pt|xml|rdf|bib|nt|refer|enw|ris|n3|csv|tsv|mso|dtl|svg|asc|txt|c|cc|cxx|cpp|java|py)(?:\\?.+)?$");
	
	public static final Pattern INNER_LINKS_KEYWORDS_FILTER = Pattern.compile(".*(?:doi.org|mailto:|\\?lang=|isallowed=n).*");	// Plain key-words inside innerLinks-String. We avoid "doi.org" in inner links, as, after many redirects, they will reach the same pageUrl.
	
	public static final Pattern INNER_LINKS_FILE_EXTENSION_FILTER = Pattern.compile(".+\\.(?:ico|css|js|gif|jpg|jpeg|png|wav|mp3|mp4|webm|mkv|mov|pt|xml|rdf|bib|nt|refer|enw|ris|n3|csv|tsv|mso|dtl|svg|do|asc|txt|c|cc|cxx|cpp|java|py)(?:\\?.+)?$");
    // In the above, don't include .php and relative extensions, since even this can be a docUrl. For example: https://www.dovepress.com/getfile.php?fileID=5337
	
	// So, we make a new REGEX for these extensions, this time, without a potential argument in the end (e.g. ?id=XXX..), except for the potential "lang".
	public static final Pattern PLAIN_PAGE_EXTENSION_FILTER = Pattern.compile(".+\\.(?:php|php2|php3|php4|php5|phtml|htm|html|shtml|xht|xhtm|xhtml|xml|rdf|bib|nt|refer|enw|ris|n3|csv|tsv|aspx|asp|jsp|do|asc)$");
	
	public static final Pattern INNER_LINKS_FILE_FORMAT_FILTER = Pattern.compile(".+format=(?:xml|htm|html|shtml|xht|xhtm|xhtml).*");
    
    public static final Pattern SPECIFIC_DOMAIN_FILTER = Pattern.compile(".+:\\/\\/.*(?:google|goo.gl|gstatic|facebook|twitter|youtube|linkedin|wordpress|s.w.org|ebay|bing|amazon\\.|wikipedia|myspace|yahoo|mail|pinterest|reddit|blog|tumblr"
																					+ "|evernote|skype|microsoft|adobe|buffer|digg|stumbleupon|addthis|delicious|dailymotion|gostats|blogger|copyright|friendfeed|newsvine|telegram|getpocket"
																					+ "|flipboard|instapaper|line.me|telegram|vk|ok.rudouban|baidu|qzone|xing|renren|weibo|doubleclick|github).*\\/.*");
    
    public static final Pattern PLAIN_DOMAIN_FILTER = Pattern.compile(".+:\\/\\/[\\w.:-]+(?:\\/)?$");	// Exclude plain domains' urls.
	
    public static final Pattern JSESSIONID_FILTER = Pattern.compile("(.+:\\/\\/.+)(?:\\;(?:JSESSIONID|jsessionid)=.+)(\\?.+)");
	
    public static final Pattern DOC_URL_FILTER = Pattern.compile(".+(pdf|download|/doc|document|(?:/|[?]|&)file|/fulltext|attachment|/paper|viewfile|viewdoc|/get|cgi/viewcontent.cgi?).*");
    // "DOC_URL_FILTER" works for lowerCase Strings (we make sure they are in lowerCase before we check).
    // Note that we still need to check if it's an alive link and if it's actually a docUrl (though it's mimeType).
	
	/*
	public static final Pattern SCIENCEDIRECT_DOMAINS = Pattern.compile(".+:\\/\\/.*(?:sciencedirect|linkinghub.elsevier)(?:.com\\/.+)");
	public static final Pattern DOI_ORG_J_FILTER = Pattern.compile(".+[doi.org]\\/[\\d]{2}\\.[\\d]{4}\\/[j]\\..+");	// doi.org urls which has this form and redirect to "sciencedirect.com".
	public static final Pattern DOI_ORG_PARENTHESIS_FILTER = Pattern.compile(".+[doi.org]\\/[\\d]{2}\\.[\\d]{4}\\/[\\w]*[\\d]{4}\\-[\\d]{3}(?:[\\d]|[\\w])[\\(][\\d]{2}[\\)][\\d]{5}\\-(?:[\\d]|[\\w])");	// Same reason as above.
	public static final Pattern DOI_ORG_JTO_FILTER = Pattern.compile(".+[doi.org]\\/[\\d]{2}\\.[\\d]{4}\\/.*[jto]\\..+");	// doi.org urls which has this form and redirect to "sciencedirect.com".
	*/
	
	public static int sumOfDocUrlsFound = 0;	// Change it back to simple int if finally in singleThread mode
	public static int inputDuplicatesNum = 0;
	
	public static final HashSet<String> duplicateUrls = new HashSet<String>();
	public static final HashSet<String> docUrls = new HashSet<String>();
	
	// Counters for certain unwanted domains. We show statistics in the end.
	public static int javascriptPageUrls = 0;
	public static int sciencedirectUrls = 0;
	public static int elsevierUnwantedUrls = 0;
	public static int crawlerSensitiveDomains = 0;
	public static int doajResultPageUrls = 0;
	public static int pagesWithHtmlDocUrls = 0;
	public static int pagesRequireLoginToAccessDocFiles = 0;
	public static int pagesWithLargerCrawlingDepth = 0;	// Pages with their docUrl behind an inner "view" page.
	public static int longToRespondUrls = 0;	// Urls belonging to domains which take too long to respon
	public static int urlsWithUnwantedForm = 0;	// (plain domains, unwanted page-extensions ect.)
	public static int pangaeaUrls = 0;	// These urls are in false form by default, but even if they weren't or we transform them, PANGAEA. only gives datasets, not fulltext.
	public static int connProblematicUrls = 0;	// Urls known to have connectivity problems, such as long conn times etc.
	public static int pagesNotProvidingDocUrls = 0;
	
	
	/**
	 * This method checks if the given url is either of unwantedType or if it's a duplicate in the input, while removing the potential jsessionid from the url.
	 * It returns the givenUrl without the jsessionidPart if this url is accepted for connection/crawling, otherwise, it returns "null".
	 * @param urlId
	 * @param retrievedUrl
	 * @param lowerCaseUrl
	 * @return the non-jsessionid-url-string / null for unwanted-duplicate-url
	 */
	public static String handleUrlChecks(String urlId, String retrievedUrl, String lowerCaseUrl)
	{
		String currentUrlDomain = UrlUtils.getDomainStr(retrievedUrl);
		if ( currentUrlDomain == null ) {    // If the domain is not found, it means that a serious problem exists with this docPage and we shouldn't crawl it.
			logger.warn("Problematic URL in \"UrlUtils.handleUrlChecks()\": \"" + retrievedUrl + "\"");
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, retrievedUrl, "Discarded in 'UrlUtils.handleUrlChecks()' method, after the occurrence of a domain-retrieval error.", null);
			return null;
		}
		
		if ( HttpConnUtils.blacklistedDomains.contains(currentUrlDomain) ) {	// Check if it has been blackListed after running inner links' checks.
			logger.debug("We will avoid to connect to blackListed domain: \"" + currentUrlDomain + "\"");
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, "unreachable", "Discarded in 'UrlUtils.handleUrlChecks()' method, as its domain was found blackListed.", null);
			return null;
		}
		
		if ( ConnSupportUtils.checkIfPathIs403BlackListed(retrievedUrl, currentUrlDomain) ) {
			logger.debug("Preventing reaching 403ErrorCode with url: \"" + retrievedUrl + "\"!");
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, retrievedUrl, "Discarded in 'UrlUtils.handleUrlChecks()' as it had a blackListed urlPath.", null);
			return null;
		}
		
		if ( matchesUnwantedUrlType(urlId, retrievedUrl, lowerCaseUrl) )
			return null;	// The url-logging is happening inside this method (per urlType).
		
		// Remove "jsessionid" for urls. Most of them, if not all, will already be expired.
		if ( lowerCaseUrl.contains("jsessionid") )
			retrievedUrl = UrlUtils.removeJsessionid(retrievedUrl);
		
		// Check if it's a duplicate.
		if ( UrlUtils.duplicateUrls.contains(retrievedUrl) ) {
			logger.debug("Skipping url: \"" + retrievedUrl + "\", at loading, as it has already been seen!");
			UrlUtils.inputDuplicatesNum ++;
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, "duplicate", "Discarded in 'UrlUtils.handleUrlChecks()', as it's a duplicate.", null);
			return null;
		}
		
		// Handle the weird-case of: "ir.lib.u-ryukyu.ac.jp"
		// See: http://ir.lib.u-ryukyu.ac.jp/handle/123456789/8743
		// Note that this is NOT the case for all of the urls containing "/handle/123456789/".. but just for this domain.
		if ( retrievedUrl.contains("ir.lib.u-ryukyu.ac.jp") && retrievedUrl.contains("/handle/123456789/") ) {
			logger.debug("We will handle the weird case of \"" + retrievedUrl + "\".");
			return StringUtils.replace(retrievedUrl, "/123456789/", "/20.500.12000/");
		}
		
		return retrievedUrl;	// The calling method needs the non-jsessionid-string.
	}
	
	
	/**
	 * This method takes the "retrievedUrl" from the inputFile and the "lowerCaseUrl" that comes out the retrieved one.
	 * It then checks if the "lowerCaseUrl" matched certain criteria representing the unwanted urls' types. It uses the "retrievedUrl" for proper logging.
	 * If these criteria match, then it logs the url and returns "true", otherwise, it returns "false".
	 * @param urlId
	 * @param lowerCaseUrl
	 * @return true/false
	 */
	public static boolean matchesUnwantedUrlType(String urlId, String retrievedUrl, String lowerCaseUrl)
	{
		if ( lowerCaseUrl.contains("frontiersin.org") || lowerCaseUrl.contains("tandfonline.com") ) {	// Avoid JavaScript-powered domains, other than the "sciencedirect.com", which is counted separately.
			UrlUtils.javascriptPageUrls++;
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, "unreachable", "Discarded after matching to a JavaScript-using domain, other than.", null);
			return true;
		}
		else if ( lowerCaseUrl.contains("www.elsevier.com") || lowerCaseUrl.contains("journals.elsevier.com") ) {	// The plain "www.elsevier.com" and the "journals.elsevier.com" don't give docUrls.
			// The "linkinghub.elsevier.com" is redirecting to "sciencedirect.com".
			// Note that we still accept the "elsevier.es" pageUrls, which give docUrls.
			UrlUtils.elsevierUnwantedUrls ++;
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, "unreachable", "Discarded after matching to the unwanted 'elsevier.com' domain.", null);
			return true;
		}
		else if ( lowerCaseUrl.contains("europepmc.org") || lowerCaseUrl.contains("ncbi.nlm.nih.gov") ) {	// Avoid known-crawler-sensitive domains.
			UrlUtils.crawlerSensitiveDomains ++;
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, "unreachable", "Discarded after matching to a crawler-sensitive domain.", null);
			return true;
		}
		else if ( lowerCaseUrl.contains("doaj.org/toc/") ) {	// Avoid resultPages.
			UrlUtils.doajResultPageUrls ++;
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, "unreachable", "Discarded after matching to the Results-directory: 'doaj.org/toc/'.", null);
			return true;
		}
		else if ( lowerCaseUrl.contains("dlib.org") || lowerCaseUrl.contains("saberes.fcecon.unr.edu.ar") ) {    // Avoid HTML docUrls.
			UrlUtils.pagesWithHtmlDocUrls++;
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, "unreachable", "Discarded after matching to an HTML-docUrls site.", null);
			return true;
		}
		else if ( lowerCaseUrl.contains("rivisteweb.it") || lowerCaseUrl.contains("wur.nl") || lowerCaseUrl.contains("remeri.org.mx")
				|| lowerCaseUrl.contains("cam.ac.uk") || lowerCaseUrl.contains("scindeks.ceon.rs") || lowerCaseUrl.contains("egms.de") ) {	// Avoid pages known to not provide docUrls (just metadata).
			UrlUtils.pagesNotProvidingDocUrls ++;												// Keep "remeri" subDomain of "org.mx", as the TLD is having a lot of different sites.
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, "unreachable", "Discarded after matching to the non docUrls-providing site 'rivisteweb.it'.", null);
			return true;
		}
		else if ( lowerCaseUrl.contains("bibliotecadigital.uel.br") || lowerCaseUrl.contains("cepr.org") ) {	// Avoid domains requiring login to access docUrls.
			UrlUtils.pagesRequireLoginToAccessDocFiles++;
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, "unreachable", "Discarded after matching to a domain which needs login to access docFiles.", null);
			return true;
		}
		else if ( lowerCaseUrl.contains("/view/") || lowerCaseUrl.contains("scielosp.org") || lowerCaseUrl.contains("dk.um.si") || lowerCaseUrl.contains("apospublications.com")
				|| lowerCaseUrl.contains("jorr.org") || lowerCaseUrl.contains("redalyc.org") ) {	// Avoid crawling pages with larger depth (innerPagesToDocUrls or Previews of docUrls).
			UrlUtils.pagesWithLargerCrawlingDepth ++;
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, "unreachable", "Discarded after matching to an increasedCrawlingDepth-site.", null);
			return true;
		}
		else if ( lowerCaseUrl.contains("doi.org/https://doi.org/") && lowerCaseUrl.contains("pangaea.") ) {	// PANGAEA. urls with problematic form and non docUrl inner links.
			UrlUtils.pangaeaUrls ++;
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, "unreachable", "Discarded after matching to 'PANGAEA.' urls with invalid form and non-docUrls in their inner links.", null);
			return true;
		}
		else if ( lowerCaseUrl.contains("200.17.137.108") ) {	// Known domains with connectivity problems.
			UrlUtils.connProblematicUrls ++;
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, "unreachable", "Discarded after matching to known urls with connectivity problems.", null);
			return true;
		}
		/*else if ( lowerCaseUrl.contains("handle.net") || lowerCaseUrl.contains("doors.doshisha.ac.jp") || lowerCaseUrl.contains("opac-ir.lib.osaka-kyoiku.ac.jp") ) {	// Slow urls (taking more than 3secs to connect).
			UrlUtils.longToRespondUrls ++;
			UrlUtils.logTriple(urlId, retrievedUrl,"unreachable", "Discarded after matching to domain, known to take long to respond.", null);
			return true;
		}*/
		else if ( lowerCaseUrl.contains("sharedsitesession") ) {	// either "getSharedSiteSession" or "consumeSharedSiteSession".
			ConnSupportUtils.blockSharedSiteSessionDomain(retrievedUrl, null);
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, "unreachable", "It was discarded after participating in a 'sharedSiteSession-redirectionPack'.", null);
			return true;
		}
		/*else if ( UrlUtils.SCIENCEDIRECT_DOMAINS.matcher(lowerCaseUrl).matches()
				|| UrlUtils.DOI_ORG_J_FILTER.matcher(lowerCaseUrl).matches() || UrlUtils.DOI_ORG_PARENTHESIS_FILTER.matcher(lowerCaseUrl).matches()
				|| UrlUtils.DOI_ORG_JTO_FILTER.matcher(lowerCaseUrl).matches() ) {
			UrlUtils.sciencedirectUrls ++;
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, "unreachable", "Discarded after matching to 'sciencedirect.com'-family urls.", null);
			return true;
		}*/
		else if ( shouldNotAcceptPageUrl(retrievedUrl, lowerCaseUrl) ) {
			UrlUtils.urlsWithUnwantedForm ++;
			UrlUtils.logQuadruple(urlId, retrievedUrl, null, "unreachable", "Discarded after matching to unwantedType-regex-rules.", null);
			return true;
		}
		else
			return false;
	}
	
	
	/**
	 * This method matches the given pageUrl against general regexes.
	 * It returns "true" if the givenUrl should not be accepted, otherwise, it returns "false".
	 * @param pageUrl
	 * @param lowerCasePageUrl
	 * @return true / false
	 */
	public static boolean shouldNotAcceptPageUrl(String pageUrl, String lowerCasePageUrl)
	{
		String lowerCaseUrl = null;
		
		if ( lowerCasePageUrl == null )
			lowerCaseUrl = pageUrl.toLowerCase();
		else
			lowerCaseUrl = lowerCasePageUrl;	// We might have already done the transformation in the calling method.
		
		return	UrlUtils.PLAIN_DOMAIN_FILTER.matcher(lowerCaseUrl).matches() || UrlUtils.SPECIFIC_DOMAIN_FILTER.matcher(lowerCaseUrl).matches()
				|| UrlUtils.URL_DIRECTORY_FILTER.matcher(lowerCaseUrl).matches() || UrlUtils.PAGE_FILE_EXTENSION_FILTER.matcher(lowerCaseUrl).matches()
				|| UrlUtils.CURRENTLY_UNSUPPORTED_DOC_EXTENSION_FILTER.matcher(lowerCaseUrl).matches();	// TODO - To be removed when these docExtensions get supported.
	}
	

    /**
     * This method logs the outputEntry to be written, as well as the docUrlPath (if non-empty String) and adds entries in the blackList.
	 * @param urlId
	 * @param sourceUrl
	 * @param pageUrl
	 * @param initialDocUrl
	 * @param comment
	 * @param domain (it may be null)
	 */
    public static void logQuadruple(String urlId, String sourceUrl, String pageUrl, String initialDocUrl, String comment, String domain)
    {
        String finalDocUrl = initialDocUrl;
		
        if ( !finalDocUrl.equals("unreachable") && !finalDocUrl.equals("duplicate") )	// If we have reached a docUrl..
        {
            // Remove "jsessionid" for urls for "cleaner" output.
			String lowerCaseUrl = finalDocUrl.toLowerCase();
            if ( lowerCaseUrl.contains("jsessionid") )
                finalDocUrl = UrlUtils.removeJsessionid(initialDocUrl);
			
			sumOfDocUrlsFound++;
			
            // Gather data for the MLA, if we decide to have it enabled.
            if ( MachineLearning.useMLA )
				MachineLearning.gatherMLData(domain, pageUrl, finalDocUrl);
			
            docUrls.add(finalDocUrl);	// Add it here, in order to be able to recognize it and quick-log it later, but also to distinguish it from other duplicates.
        }
        else if ( !finalDocUrl.equals("duplicate") )	{// Else if this url is not a docUrl and has not been processed before..
            duplicateUrls.add(sourceUrl);	 // Add it in duplicates BlackList, in order not to be accessed for 2nd time in the future..
        }	// We don't add docUrls here, as we want them to be separate for checking purposes.
		
		FileUtils.quadrupleToBeLoggedOutputList.add(new QuadrupleToBeLogged(urlId, sourceUrl, finalDocUrl, comment));	// Log it to be written later in the outputFile.
		
        if ( FileUtils.quadrupleToBeLoggedOutputList.size() == FileUtils.groupCount )	// Write to file every time we have a group of <groupCount> triples.
            FileUtils.writeToFile();
    }
    
	
	/**
	 * This method returns the domain of the given url, in lowerCase (for better comparison).
	 * @param urlStr
	 * @return domainStr
	 */
	public static String getDomainStr(String urlStr)
	{
		String domainStr = null;
		Matcher matcher = null;
		
		try {
			matcher = URL_TRIPLE.matcher(urlStr);
		} catch (NullPointerException npe) {	// There should never be an NPE...
			logger.debug("NPE was thrown after calling \"Matcher\" in \"getDomainStr()\" with \"null\" value!");
			return null;
		}
		
		if ( matcher.matches() ) {
			domainStr = matcher.group(2);	// Group <2> is the DOMAIN.
			if ( (domainStr == null) || domainStr.isEmpty() ) {
				logger.warn("Unexpected null or empty value returned by \"matcher.group(2)\" for url: \"" + urlStr + "\".");
				return null;
			}
		} else {
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
		String pathStr = null;
		Matcher matcher = null;
		
		try {
			matcher = URL_TRIPLE.matcher(urlStr);
		} catch (NullPointerException npe) {	// There should never be an NPE...
			logger.debug("NPE was thrown after calling \"Matcher\" in \"getPathStr()\" with \"null\" value!");
			return null;
		}
		
		if ( matcher.matches() ) {
			pathStr = matcher.group(1);	// Group <1> is the PATH.
			if ( (pathStr == null) || pathStr.isEmpty() ) {
				logger.warn("Unexpected null or empty value returned by \"matcher.group(1)\" for url: \"" + urlStr + "\".");
				return null;
			}
		} else {
			logger.warn("Unexpected URL_TRIPLE's (" + matcher.toString() + ") mismatch for url: \"" + urlStr + "\"");
			return null;
		}
		
		return pathStr;
	}
	
	
	/**
	 * This method returns the path of the given url.
	 * @param urlStr
	 * @return pathStr
	 */
	public static String getDocIdStr(String urlStr)
	{
		String docIdStr = null;
		Matcher matcher = null;
		
		try {
			matcher = URL_TRIPLE.matcher(urlStr);
		} catch (NullPointerException npe) {	// There should never be an NPE...
			logger.debug("NPE was thrown after calling \"Matcher\" in \"getDocIdStr\" with \"null\" value!");
			return null;
		}
		
		if ( matcher.matches() ) {
			docIdStr = matcher.group(3);	// Group <3> is the docId.
			if ( (docIdStr == null) || docIdStr.isEmpty() ) {
				logger.warn("Unexpected null or empty value returned by \"matcher.group(3)\" for url: \"" + urlStr + "\".");
				return null;
			}
		}
		else {
			logger.warn("Unexpected URL_TRIPLE's (" + matcher.toString() + ") mismatch for url: \"" + urlStr + "\"");
			return null;
		}
		
		return docIdStr;
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
		
		String preJsessionidStr = null;
		String afterJsessionidStr = null;
		
		Matcher jsessionidMatcher = JSESSIONID_FILTER.matcher(urlStr);
		if (jsessionidMatcher.matches())
		{
			preJsessionidStr = jsessionidMatcher.group(1);	// Take only the 1st part of the urlStr, without the jsessionid.
		    if ( (preJsessionidStr == null) || preJsessionidStr.isEmpty() ) {
		    	logger.warn("Unexpected null or empty value returned by \"jsessionidMatcher.group(1)\" for url: \"" + urlStr + "\"");
		    	return finalUrl;
		    }
		    finalUrl = preJsessionidStr;
		    
		    afterJsessionidStr = jsessionidMatcher.group(2);
			if ( (afterJsessionidStr == null) || afterJsessionidStr.isEmpty() )
				return finalUrl;
			else
				return finalUrl + afterJsessionidStr;
		}
		else
			return finalUrl;
	}
	
}

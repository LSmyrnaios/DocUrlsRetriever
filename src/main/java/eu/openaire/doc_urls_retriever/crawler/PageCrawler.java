package eu.openaire.doc_urls_retriever.crawler;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import edu.uci.ics.crawler4j.crawler.Page;
import edu.uci.ics.crawler4j.crawler.WebCrawler;
import edu.uci.ics.crawler4j.url.URLCanonicalizer;
import edu.uci.ics.crawler4j.url.WebURL;
import eu.openaire.doc_urls_retriever.util.http.HttpUtils;
import eu.openaire.doc_urls_retriever.util.url.UrlUtils;



public class PageCrawler extends WebCrawler
{
	private static final Logger logger = LogManager.getLogger(PageCrawler.class);


	@Override
	public boolean shouldVisit(Page referringPage, WebURL url)
	{
		String lowerCaseUrlStr = url.toString().toLowerCase();

		if ( lowerCaseUrlStr.contains("elsevier.com") ) {   // Avoid this JavaScript site with non accesible dynamic links.
            UrlUtils.elsevierLinks ++;
            return false;
		}
		else if ( lowerCaseUrlStr.contains("doaj.org/toc/") ) {
			UrlUtils.doajResultPageLinks ++;
			return false;
		}
		else
            return	!UrlUtils.SPECIFIC_DOMAIN_FILTER.matcher(lowerCaseUrlStr).matches()
                    && !UrlUtils.PAGE_FILE_EXTENSION_FILTER.matcher(lowerCaseUrlStr).matches()
					&& !UrlUtils.URL_DIRECTORY_FILTER.matcher(lowerCaseUrlStr).matches();
	}
	
	
	public boolean shouldNotCheckInnerLink(String referringPageDomain, String linkStr)
	{
		String lowerCaseLink = linkStr.toLowerCase();

		return	!lowerCaseLink.contains(referringPageDomain)	// Don't check this link if it belongs in a different domain than the referringPage's one.
				|| lowerCaseLink.contains("citation") || lowerCaseLink.contains("mailto:")
				|| UrlUtils.SPECIFIC_DOMAIN_FILTER.matcher(lowerCaseLink).matches()
				|| UrlUtils.PLAIN_DOMAIN_FILTER.matcher(lowerCaseLink).matches()
				|| UrlUtils.URL_DIRECTORY_FILTER.matcher(lowerCaseLink).matches()
				|| UrlUtils.INNER_LINKS_FILE_EXTENSION_FILTER.matcher(lowerCaseLink).matches()
				|| UrlUtils.INNER_LINKS_FILE_FORMAT_FILTER.matcher(lowerCaseLink).matches()
				|| UrlUtils.PLAIN_PAGE_EXTENSION_FILTER.matcher(lowerCaseLink).matches();
	}
	
	
	@Override
	public boolean shouldFollowLinksIn(WebURL url)
	{
		return false;	// We don't want any inner lnks to be followed for crawling.
	}
	
	
	@Override
	public void visit(Page page)
	{
		String pageUrl = page.getWebURL().getURL();

		logger.debug("Checking pageUrl: \"" + pageUrl + "\".");

		if ( pageUrl.contains("doaj.org/toc/") ) {	// Re-check here for these resultPages, as it seems that Crawler4j has a bug in handling "shouldVisit()" method.
			logger.debug("Not visiting: " + pageUrl + " as per your \"shouldVisit\" policy (used a workaround for Crawler4j bug)");
			UrlUtils.doajResultPageLinks ++;
			return;
		}

		String pageContentType = page.getContentType();

		if ( UrlUtils.checkIfDocMimeType(pageUrl, pageContentType) ) {
			logger.debug("docUrl found: <" + pageUrl + ">");
			UrlUtils.logUrl(pageUrl, pageUrl);
			return;
		}
		
		String currentPageDomain = UrlUtils.getDomainStr(pageUrl);
		
		HttpUtils.lastConnectedHost = currentPageDomain;	// The crawler opened a connection to download this page.

		if ( HttpUtils.blacklistedDomains.contains(currentPageDomain) ) {	// Check if it has been blackListed after running inner links' checks.
			logger.debug("Avoid crawling blackListed domain: \"" + currentPageDomain + "\"");
			return;
		}

	    // Check if we can find the docUrl based on previous runs. (Still in experimental stage)
/*    	if ( UrlUtils.guessInnerDocUrl(pageUrl) )	// If we were able to find the right path.. and hit a docUrl successfully.. return.
    		return;*/
        
	    Set<WebURL> currentPageLinks = page.getParseData().getOutgoingUrls();

		if ( currentPageLinks.isEmpty() )	// If no links were retrieved (e.g. the pageUrl was some kind of non-page binary content)
		{
			logger.warn("No links were able to be retrieved from pageUrl: " + pageUrl + ". Its contentType is: " + pageContentType);
			UrlUtils.logUrl(pageUrl, "unreachable");
			return;
		}

		HashSet<String> curLinksStr = new HashSet<String>();	// HashSet to store the String version of each link.

		String urlToCheck = null;


		// Check if urls inside this page, match to a docUrl regex, if they do, try connecting with them and see if they truly are docUrls. If they are, return.
		for ( WebURL link : currentPageLinks )
		{
			try {
				// Produce fully functional inner links, NOT inner paths. (Make sure that Crawler4j doesn't handle that already..)
				URL base = new URL(pageUrl);
				URL targetUrl = new URL(base, link.toString());	// Combine base (domain) and resource (inner link), to produce the final link.
				String currentLink  = targetUrl.toString();

				if ( UrlUtils.duplicateUrls.contains(currentLink) )
					continue;

				if ( shouldNotCheckInnerLink(currentPageDomain, currentLink) ) {	// If this link matches certain blackListed criteria, move on..
					//logger.debug("Avoided link: " + currentLink );
					UrlUtils.duplicateUrls.add(currentLink);
					continue;
				}

				if ( (urlToCheck = URLCanonicalizer.getCanonicalURL(currentLink) ) == null ) {	// Fix potential encoding problems.
					logger.debug("Could not cannonicalize inner url: " + currentLink);
					UrlUtils.duplicateUrls.add(currentLink);
					continue;
				}

				if ( UrlUtils.docUrls.contains(urlToCheck) ) {	// If we got into an already-found docUrl, log it and return.
					UrlUtils.logUrl(pageUrl, urlToCheck);
					logger.debug("Re-crossing the already found url: \"" +  urlToCheck + "\"");
					return;
				}

				curLinksStr.add(urlToCheck);	// Keep the string version of this link, in order not to make the transformation later..

				Matcher docUrlMatcher = UrlUtils.DOC_URL_FILTER.matcher(urlToCheck.toLowerCase());
				if ( docUrlMatcher.matches() )
				{
					if ( HttpUtils.connectAndCheckMimeType(pageUrl, urlToCheck, null) ) {
						//logger.debug("\"DOC_URL_FILTER\" revealed a docUrl in pageUrl: \"" + pageUrl + "\", after matching to: \"" + docUrlMatcher.group(1) + "\"");
						return;
					}
				}// end if
			} catch (RuntimeException re) {
				UrlUtils.duplicateUrls.add(urlToCheck);	// Don't check it ever again..
			} catch (MalformedURLException me) {
				logger.warn(me);
			}
		}// end for-loop

		// If we reached here, it means that we couldn't find a docUrl the quick way.. so we have to check some (we exclude lots of them) of the inner links one by one.

		//logger.debug("Num of links in \"" + pageUrl + "\" is: " + currentPageLinks.size());

		for ( String currentLink : curLinksStr )
		{
			if ( UrlUtils.duplicateUrls.contains(currentLink) )
				continue;	// Don't check already seen links.

			if ( (urlToCheck = URLCanonicalizer.getCanonicalURL(currentLink) ) == null ) {	// Fix potential encoding problems.
				logger.debug("Could not cannonicalize inner url: " + currentLink);
				UrlUtils.duplicateUrls.add(currentLink);
				continue;	// Could not canonicalize this url! Move on..
			}

			if ( UrlUtils.docUrls.contains(urlToCheck) ) {	// If we got into an already-found docUrl, log it and return.
				logger.debug("Re-crossing a previously found docUrl: \"" +  urlToCheck + "\"");
				UrlUtils.logUrl(pageUrl, currentLink);
				return;
			}

			try {
				if ( HttpUtils.connectAndCheckMimeType(pageUrl, urlToCheck, null) )
					return;
			} catch (RuntimeException e) {
				// No special handling here.. nor logging..
			}
		}	// end for-loop

		// If we get here it means that this pageUrl is not a docUrl nor it contains a docUrl..
		UrlUtils.logUrl(pageUrl, "unreachable");
	}

}

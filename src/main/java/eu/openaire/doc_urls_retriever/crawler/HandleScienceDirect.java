package eu.openaire.doc_urls_retriever.crawler;


import eu.openaire.doc_urls_retriever.util.http.ConnSupportUtils;
import eu.openaire.doc_urls_retriever.util.http.HttpConnUtils;
import eu.openaire.doc_urls_retriever.util.url.UrlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @author Lampros A. Smyrnaios
 */
public class HandleScienceDirect
{
	private static final Logger logger = LoggerFactory.getLogger(HandleScienceDirect.class);
	
	public static final Pattern SCIENCEDIRECT_FINAL_DOC_URL = Pattern.compile("(?:window.location[\\s]+\\=[\\s]+\\')(.*)(?:\\'\\;)");
	
	
	/**
	 * This method handles the JavaScriptSites of "sciencedirect.com"-family. It retrieves the docLinks hiding inside.
	 * It returns true if the docUrl was found, otherwise, it returns false.
	 * Note that these docUrl d not last long, since they are produced based on timestamp and jsessionid. After a while they just redirect to the pageUrl.
	 * @param urlId
	 * @param sourceUrl
	 * @param pageUrl
	 * @param pageDomain
	 * @param conn
	 * @return true/false
	 */
	public static boolean handleScienceDirectFamilyUrls(String urlId, String sourceUrl, String pageUrl, String pageDomain, HttpURLConnection conn)
	{
		try {
			// Handle "linkinghub.elsevier.com" urls which contain javaScriptRedirect..
			if ( pageDomain.equals("linkinghub.elsevier.com") ) {
				//UrlUtils.elsevierLinks ++;
				if ( (pageUrl = silentRedirectElsevierToScienseRedirect(pageUrl)) != null )
					conn = HttpConnUtils.handleConnection(urlId, sourceUrl, pageUrl, pageUrl, pageDomain, true, false);
				else
					return false;
			}
			
			// We now have the "sciencedirect.com" url (either from the beginning or after silentRedirect).
			
			logger.debug("ScienceDirect-url: " + pageUrl);
			String html = ConnSupportUtils.getHtmlString(conn);
			Matcher metaDocUrlMatcher = PageCrawler.META_DOC_URL.matcher(html);
			if ( metaDocUrlMatcher.find() )
			{
				String metaDocUrl = metaDocUrlMatcher.group(1);
				if ( metaDocUrl.isEmpty() ) {
					logger.error("Could not retrieve the metaDocUrl from a \"sciencedirect.com\" url!");
					return false;
				}
				//logger.debug("MetaDocUrl: " + metaDocUrl);	// DEBUG!
				
				// Get the new html..
				// We don't disconnect the previous one, since they both are in the same domain (see JavaDocs).
				conn = HttpConnUtils.handleConnection(urlId, sourceUrl, pageUrl, metaDocUrl, pageDomain, true, false);
				
				//logger.debug("Url after connecting: " + conn.getURL().toString());
				//logger.debug("MimeType: " + conn.getContentType());
				
				html = ConnSupportUtils.getHtmlString(conn);    // Take the new html.
				Matcher finalDocUrlMatcher = SCIENCEDIRECT_FINAL_DOC_URL.matcher(html);
				if ( finalDocUrlMatcher.find() )
				{
					String finalDocUrl = finalDocUrlMatcher.group(1);
					if ( finalDocUrl.isEmpty() ) {
						logger.error("Could not retrieve the finalDocUrl from a \"sciencedirect.com\" url!");
						return false;
					}
					//logger.debug("FinalDocUrl: " + finalDocUrl);	// DEBUG!
					
					// Check and/or download the docUrl. These urls are one-time-links, meaning that after a while they will just redirect to their pageUrl.
					if ( HttpConnUtils.connectAndCheckMimeType(urlId, sourceUrl, pageUrl, finalDocUrl, pageDomain, false, true) )    // We log the docUrl inside this method.
						return true;
					else {
						logger.warn("LookedUp finalDocUrl: \"" + finalDocUrl + "\" was not an actual docUrl!");
						return false;
					}
				} else {
					logger.warn("The finalDocLink could not be found!");
					//logger.debug("HTML-code:\n" + html);	// DEBUG!
					return false;
				}
			} else {
				logger.warn("The metaDocLink could not be found!");	// It's possible if the document only available after paying (https://www.sciencedirect.com/science/article/pii/S1094202598900527)
				//logger.debug("HTML-code:\n" + html);	// DEBUG!
				return false;
			}
		} catch (Exception e) {
			logger.error("" + e);
			return false;
		}
		finally {
			// If the initial pageDomain was different from "sciencedirect.com", close the "sciencedirect.com"-connection here.
			// Otherwise, if it came as a "sciencedirect.com", it will be closed where it was first created, meaning in "HttpConnUtils.connectAndCheckMimeType()".
			if ( !pageDomain.equals("sciencedirect.com") )
				conn.disconnect();	// Disconnect from the final-"sciencedirect.com"-connection.
		}
	}
	
	
	/**
	 * This method recieves a url from "linkinghub.elsevier.com" and returns it's matched url in "sciencedirect.com".
	 * We do this because the "linkinghub.elsevier.com" urls have a javaScript redirect inside which we are not able to handle without doing html scraping.
	 * If there is any error this method returns the URL it first recieved.
	 * @param linkingElsevierUrl
	 * @return
	 */
	public static String silentRedirectElsevierToScienseRedirect(String linkingElsevierUrl)
	{
		if ( !linkingElsevierUrl.contains("linkinghub.elsevier.com") ) // If this method was called for the wrong url, then just return it.
			return linkingElsevierUrl;
		
		String idStr = null;
		Matcher matcher = UrlUtils.URL_TRIPLE.matcher(linkingElsevierUrl);
		if ( matcher.matches() ) {
			idStr = matcher.group(3);
			if ( idStr == null || idStr.isEmpty() ) {
				logger.warn("Unexpected id-missing case for: " + linkingElsevierUrl);
				return linkingElsevierUrl;
			}
		}
		else {
			logger.warn("Unexpected \"URL_TRIPLE\" mismatch for: " + linkingElsevierUrl);
			return linkingElsevierUrl;
		}
		
		return ("https://www.sciencedirect.com/science/article/pii/" + idStr);
	}
	
}

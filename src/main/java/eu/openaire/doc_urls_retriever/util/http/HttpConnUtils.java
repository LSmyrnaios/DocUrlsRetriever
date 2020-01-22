package eu.openaire.doc_urls_retriever.util.http;

import edu.uci.ics.crawler4j.url.URLCanonicalizer;
import eu.openaire.doc_urls_retriever.crawler.PageCrawler;
import eu.openaire.doc_urls_retriever.exceptions.*;
import eu.openaire.doc_urls_retriever.util.file.FileUtils;
import eu.openaire.doc_urls_retriever.util.url.LoaderAndChecker;
import eu.openaire.doc_urls_retriever.util.url.UrlTypeChecker;
import eu.openaire.doc_urls_retriever.util.url.UrlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;


/**
 * @author Lampros Smyrnaios
 */
public class HttpConnUtils
{
	private static final Logger logger = LoggerFactory.getLogger(HttpConnUtils.class);
	
	public static final HashSet<String> domainsWithUnsupportedHeadMethod = new HashSet<String>();
	public static final HashSet<String> blacklistedDomains = new HashSet<String>();	// Domains with which we don't want to connect again.
	
	public static final HashMap<String, Integer> timesDomainsHadInputNotBeingDocNorPage = new HashMap<String, Integer>();
	public static final HashMap<String, Integer> timesDomainsReturnedNoType = new HashMap<String, Integer>();	// Domain which returned no content-type not content disposition in their responce and amount of times they did.
	
	public static int domainsBlockedDueToSSLException = 0;
	
	public static String lastConnectedHost = "";
	public static final int politenessDelay = 0;	// Time to wait before connecting to the same host again.
	
	public static final int maxConnGETWaitingTime = 20000;	// Max time (in ms) to wait for a connection, using "HTTP GET".
	public static final int maxConnHEADWaitingTime = 15000;	// Max time (in ms) to wait for a connection, using "HTTP HEAD".
	
	private static final int maxRedirectsForPageUrls = 7;// The usual redirect times for doi.org urls is 3, though some of them can reach even 5 (if not more..)
	private static final int maxRedirectsForInternalLinks = 2;	// Internal-DOC-Links shouldn't take more than 2 redirects.
	
    private static final int timesToReturnNoTypeBeforeBlocked = 10;
	private static final int timesToHaveNoDocNorPageInputBeforeBlocked = 10;
    
	public static final int maxAllowedContentSize = 1073741824;	// 1Gb
	private static final boolean shouldNOTacceptGETmethodForUncategorizedInternalLinks = true;
	
	
	/**
	 * This method checks if a certain url can give us its mimeType, as well as if this mimeType is a docMimeType.
	 * It automatically calls the "logUrl()" method for the valid docUrls, while it doesn't call it for non-success cases, thus allowing calling method to handle the case.
	 * @param urlId
	 * @param sourceUrl	// The inputUrl
	 * @param pageUrl	// May be the inputUrl or a redirected version of it.
	 * @param resourceURL	// May be the inputUrl or an internalLink of that inputUrl.
	 * @param domainStr
	 * @param calledForPageUrl
	 * @param calledForPossibleDocUrl
	 * @return "true", if it's a docMimeType, otherwise, "false", if it has a different mimeType.
	 * @throws RuntimeException (when there was a network error).
	 * @throws ConnTimeoutException
	 * @throws DomainBlockedException
	 * @throws DomainWithUnsupportedHEADmethodException
	 */
	public static boolean connectAndCheckMimeType(String urlId, String sourceUrl, String pageUrl, String resourceURL, String domainStr, boolean calledForPageUrl, boolean calledForPossibleDocUrl)
													throws RuntimeException, ConnTimeoutException, DomainBlockedException, DomainWithUnsupportedHEADmethodException
	{
		HttpURLConnection conn = null;
		try {
			if ( domainStr == null )	// No info about domainStr from the calling method.. we have to find it here.
				if ( (domainStr = UrlUtils.getDomainStr(resourceURL)) == null )
					throw new RuntimeException();	// The cause it's already logged inside "getDomainStr()".
			
			conn = handleConnection(urlId, sourceUrl, pageUrl, resourceURL, domainStr, calledForPageUrl, calledForPossibleDocUrl);
			
			// Check if we are able to find the mime type, if not then try "Content-Disposition".
			String mimeType = conn.getContentType();
			String contentDisposition = null;
			
			if ( mimeType == null ) {
				contentDisposition = conn.getHeaderField("Content-Disposition");
				if ( contentDisposition == null ) {
					String warnMsg = "No ContentType nor ContentDisposition, were able to be retrieved from url: " + conn.getURL().toString();
					if ( ConnSupportUtils.countAndBlockDomainAfterTimes(HttpConnUtils.blacklistedDomains, timesDomainsReturnedNoType, domainStr, HttpConnUtils.timesToReturnNoTypeBeforeBlocked) ) {
						logger.warn(warnMsg);
						logger.warn("Domain: " + domainStr + " was blocked after returning no Type-info more than " + HttpConnUtils.timesToReturnNoTypeBeforeBlocked + " times.");
						throw new DomainBlockedException();
					}
					else
						throw new RuntimeException(warnMsg);	// We can't retrieve any clue. This is not desired. The "warnMsg" will be printed by the caller method.
				}
			}
			
			String finalUrlStr = conn.getURL().toString();
			
			//logger.debug("Url: " + finalUrlStr);	// DEBUG!
			//logger.debug("MimeType: " + mimeType);	// DEBUG!
			
			if ( ConnSupportUtils.hasDocMimeType(finalUrlStr, mimeType, contentDisposition, conn) ) {
				logger.info("docUrl found: <" + finalUrlStr + ">");
				String fullPathFileName = "";
				if ( FileUtils.shouldDownloadDocFiles ) {
					try {
						fullPathFileName = ConnSupportUtils.downloadAndStoreDocFile(conn, domainStr, finalUrlStr);
						logger.info("DocFile: \"" + fullPathFileName + "\" has been downloaded.");
					} catch (DocFileNotRetrievedException dfnde) {
						fullPathFileName = "DocFileNotRetrievedException was thrown before the docFile could be stored.";
						logger.warn(fullPathFileName, dfnde);
					}
				}
				UrlUtils.logQuadruple(urlId, sourceUrl, pageUrl, finalUrlStr, fullPathFileName, domainStr);	// we send the urls, before and after potential redirections.
				return true;
			}
			else if ( calledForPageUrl ) {	// Visit this url only if this method was called for an inputUrl.
				if ( finalUrlStr.contains("viewcontent.cgi") ) {	// If this "viewcontent.cgi" isn't a docUrl, then don't check its internalLinks. Check this: "https://docs.lib.purdue.edu/cgi/viewcontent.cgi?referer=&httpsredir=1&params=/context/physics_articles/article/1964/type/native/&path_info="
					logger.warn("Unwanted pageUrl: \"" + finalUrlStr + "\" will not be visited!");
					UrlUtils.logQuadruple(urlId, sourceUrl, null, "unreachable", "It was discarded in 'HttpConnUtils.connectAndCheckMimeType()', after matching to a non-docUrl with 'viewcontent.cgi'.", domainStr);
					return false;
				}
				else if ( (mimeType != null) && ((mimeType.contains("htm") || mimeType.contains("text"))) )	// The content-disposition is non-usable in the case of pages.. it's probably not provided anyway.
					PageCrawler.visit(urlId, sourceUrl, finalUrlStr, mimeType, conn);
				else {
					logger.warn("Non-pageUrl: \"" + finalUrlStr + "\" with mimeType: \"" + mimeType + "\" will not be visited!");
					UrlUtils.logQuadruple(urlId, sourceUrl, null, "unreachable", "It was discarded in 'HttpConnUtils.connectAndCheckMimeType()', after not matching to a docUrl nor to an htm/text-like page.", domainStr);
					if ( ConnSupportUtils.countAndBlockDomainAfterTimes(blacklistedDomains, timesDomainsHadInputNotBeingDocNorPage, domainStr, HttpConnUtils.timesToHaveNoDocNorPageInputBeforeBlocked) )
						logger.warn("Domain: " + domainStr + " was blocked after having no Doc nor Pages in the input more than " + HttpConnUtils.timesToReturnNoTypeBeforeBlocked + " times.");
				}	// We log the quadruple here, as there is connection-kind-of problem here.. it's just us considering it an unwanted case. We don't throw "DomainBlockedException()", as we don't handle it for inputUrls (it would also log the quadruple twice with diff comments).
			}
		} catch (AlreadyFoundDocUrlException afdue) {	// An already-found docUrl was discovered during redirections.
			return true;	// It's already logged for the outputFile.
		} catch (RuntimeException re) {
			if ( calledForPageUrl ) {	// Log this error only for docPages, not internalLinks.
				LoaderAndChecker.connProblematicUrls ++;
				ConnSupportUtils.printEmbeddedExceptionMessage(re, resourceURL);
			}
			throw re;
		} catch (ConnTimeoutException cte) {
			if ( calledForPageUrl )
				UrlTypeChecker.longToRespondUrls ++;
			throw cte;
		} catch (DomainBlockedException | DomainWithUnsupportedHEADmethodException e) {
			if ( calledForPageUrl )
				LoaderAndChecker.connProblematicUrls ++;
			throw e;
		} catch (Exception e) {
			if ( calledForPageUrl ) {	// Log this error only for docPages.
				logger.warn("Could not handle connection for \"" + resourceURL + "\"!");
				LoaderAndChecker.connProblematicUrls ++;
			}
			throw new RuntimeException();
		} finally {
			if ( conn != null )
				conn.disconnect();
		}
		return false;
	}
	
	
	public static HttpURLConnection handleConnection(String urlId, String sourceUrl, String pageUrl, String resourceURL, String domainStr, boolean calledForPageUrl, boolean calledForPossibleDocUrl)
										throws AlreadyFoundDocUrlException, RuntimeException, ConnTimeoutException, DomainBlockedException, DomainWithUnsupportedHEADmethodException, IOException
	{
		HttpURLConnection conn = openHttpConnection(resourceURL, domainStr, calledForPageUrl, calledForPossibleDocUrl);
		
		int responceCode = conn.getResponseCode();	// It's already checked for -1 case (Invalid HTTP responce), inside openHttpConnection().
		if ( (responceCode >= 300) && (responceCode <= 399) ) {   // If we have redirections..
			conn = handleRedirects(urlId, sourceUrl, pageUrl, resourceURL, conn, responceCode, domainStr, calledForPageUrl, calledForPossibleDocUrl);    // Take care of redirects.
		}
		else if ( (responceCode < 200) || (responceCode >= 400) ) {	// If we have error codes.
			String errorLogMessage = ConnSupportUtils.onErrorStatusCode(resourceURL, domainStr, responceCode);
			throw new RuntimeException(errorLogMessage);	// This is only thrown if a "DomainBlockedException" is caught.
		}
		// Else it's an HTTP 2XX SUCCESS CODE.
		
		return conn;
	}
	
	
	/**
     * This method sets up a connection with the given url, using the "HEAD" method. If the server doesn't support "HEAD", it logs it, then it resets the connection and tries again using "GET".
     * The "domainStr" may be either null, if the calling method doesn't know this String (then openHttpConnection() finds it on its own), or an actual "domainStr" String.
	 * @param resourceURL
	 * @param domainStr
	 * @param calledForPageUrl
	 * @param calledForPossibleDocUrl
	 * @return HttpURLConnection
	 * @throws RuntimeException
	 * @throws ConnTimeoutException
	 * @throws DomainBlockedException
	 * @throws DomainWithUnsupportedHEADmethodException
     */
	public static HttpURLConnection openHttpConnection(String resourceURL, String domainStr, boolean calledForPageUrl, boolean calledForPossibleDocUrl)
									throws RuntimeException, ConnTimeoutException, DomainBlockedException, DomainWithUnsupportedHEADmethodException
    {
    	URL url = null;
		HttpURLConnection conn = null;
		int responceCode = 0;
		
		try {
			if ( blacklistedDomains.contains(domainStr) )
		    	throw new RuntimeException("Preventing connecting to blacklistedHost: \"" + domainStr + "\"!");
			
			// Check whether we don't accept "GET" method for uncategorizedInternalLinks and if this url is such a case.
			if ( shouldNOTacceptGETmethodForUncategorizedInternalLinks
				&& !calledForPossibleDocUrl && domainsWithUnsupportedHeadMethod.contains(domainStr) )
				throw new DomainWithUnsupportedHEADmethodException();
			
			if ( ConnSupportUtils.checkIfPathIs403BlackListed(resourceURL, domainStr) )
				throw new RuntimeException("Preventing reaching 403ErrorCode with url: \"" + resourceURL + "\"!");
			
			url = new URL(resourceURL);
			
			conn = (HttpURLConnection) url.openConnection();
			
			conn.setInstanceFollowRedirects(false);	// We manage redirects on our own, in order to control redirectsNum, as well as to avoid redirecting to unwantedUrls.
			
			if ( (calledForPageUrl && !calledForPossibleDocUrl)	// Either for just-webPages or for docUrls, we want to use "GET" in order to download the content.
					|| (calledForPossibleDocUrl && FileUtils.shouldDownloadDocFiles) ) {
				conn.setRequestMethod("GET");	// Go directly with "GET".
				conn.setConnectTimeout(maxConnGETWaitingTime);
				conn.setReadTimeout(maxConnGETWaitingTime);
			} else {
				conn.setRequestMethod("HEAD");	// Else, try "HEAD" (it may be either a domain that supports "HEAD", or a new domain, for which we have no info yet).
				conn.setConnectTimeout(maxConnHEADWaitingTime);
				conn.setReadTimeout(maxConnHEADWaitingTime);
			}
			
			if ( (politenessDelay > 0) && domainStr.contains(lastConnectedHost) )	// If this is the last-visited domain, sleep a bit before re-connecting to it.
				Thread.sleep(politenessDelay);	// Avoid server-overloading for the same host.
			
			conn.connect();	// Else, first connect and if there is no error, log this domain as the last one.
			lastConnectedHost = domainStr;
			
			if ( (responceCode = conn.getResponseCode()) == -1 )
				throw new RuntimeException("Invalid HTTP response for \"" + resourceURL + "\"");
			
			if ( (responceCode == 405 || responceCode == 501) && conn.getRequestMethod().equals("HEAD") )	// If this SERVER doesn't support "HEAD" method or doesn't allow us to use it..
			{
				//logger.debug("HTTP \"HEAD\" method is not supported for: \"" + resourceURL +"\". Server's responceCode was: " + responceCode);
				
				// This domain doesn't support "HEAD" method, log it and then check if we can retry with "GET" or not.
				domainsWithUnsupportedHeadMethod.add(domainStr);
				
				if ( shouldNOTacceptGETmethodForUncategorizedInternalLinks && !calledForPossibleDocUrl )	// If we set not to retry with "GET" when we try uncategorizedInternalLinks, throw the related exception and stop the crawling of this page.
					throw new DomainWithUnsupportedHEADmethodException();
				
				// If we accept connection's retrying, using "GET", move on reconnecting.
				// No call of "conn.disconnect()" here, as we will connect to the same server.
				conn = (HttpURLConnection) url.openConnection();
				
				conn.setRequestMethod("GET");	// To reach here, it means that the HEAD method is unsupported.
				conn.setConnectTimeout(maxConnGETWaitingTime);
				conn.setReadTimeout(maxConnGETWaitingTime);
				conn.setInstanceFollowRedirects(false);
				
				if ( politenessDelay > 0 )
					Thread.sleep(politenessDelay);	// Avoid server-overloading for the same host.
				
				conn.connect();
				//logger.debug("ResponceCode for \"" + resourceURL + "\", after setting conn-method to: \"" + conn.getRequestMethod() + "\" is: " + conn.getResponseCode());
				
				if ( conn.getResponseCode() == -1 )	// Make sure we throw a RunEx on invalidHTTP.
					throw new RuntimeException("Invalid HTTP response for \"" + resourceURL + "\"");
			}
		} catch (RuntimeException re) {	// The cause it's already logged.
			if ( conn != null )
				conn.disconnect();
			throw re;
		} catch (DomainWithUnsupportedHEADmethodException dwuhe) {
			throw dwuhe;
		} catch (UnknownHostException uhe) {
			logger.debug("A new \"Unknown Network\" Host was found and blacklisted: \"" + domainStr + "\"");
			if ( conn != null )
				conn.disconnect();
			blacklistedDomains.add(domainStr);	//Log it to never try connecting with it again.
			throw new DomainBlockedException();
		} catch (SocketTimeoutException ste) {
			logger.debug("Url: \"" + resourceURL + "\" failed to respond on time!");
			if ( conn != null )
				conn.disconnect();
			ConnSupportUtils.onTimeoutException(domainStr);	// May throw a "DomainBlockedException", which will be thrown before the "ConnTimeoutException".
			throw new ConnTimeoutException();
		} catch (ConnectException ce) {
			if ( conn != null )
				conn.disconnect();
			String eMsg = ce.getMessage();
			if ( (eMsg != null) && eMsg.toLowerCase().contains("timeout") ) {	// If it's a "connection timeout" type of exception, treat it like it.
				ConnSupportUtils.onTimeoutException(domainStr);	// Can throw a "DomainBlockedException", which will be thrown before the "ConnTimeoutException".
				throw new ConnTimeoutException();
			}
			throw new RuntimeException(eMsg);
		} catch (SSLException ssle) {
			logger.warn("No Secure connection was able to be negotiated with the domain: \"" + domainStr + "\".", ssle.getMessage());
			if ( conn != null )
				conn.disconnect();
			// TODO - For "SSLProtocolException", see more about it's possible handling here: https://stackoverflow.com/questions/7615645/ssl-handshake-alert-unrecognized-name-error-since-upgrade-to-java-1-7-0/14884941#14884941
			// TODO - Maybe we should make another list where only urls in https, from these domains, would be blocked.
			blacklistedDomains.add(domainStr);
			domainsBlockedDueToSSLException ++;
			throw new DomainBlockedException();
		} catch (SocketException se) {
			if ( conn != null )
				conn.disconnect();
			
			String errorMsg = se.getMessage();
			if ( errorMsg != null )
				errorMsg = "\"" + errorMsg + "\". This SocketException was recieved after trying to connect with the domain: \"" + domainStr + "\"";
			
			throw new RuntimeException(errorMsg);
			//blacklistedDomains.add(domainStr);
			//throw new DomainBlockedException();
    	} catch (Exception e) {
			logger.error("", e);
			if ( conn != null )
				conn.disconnect();
			throw new RuntimeException();
		}
		
		return conn;
    }
    
	
    /**
     * This method takes an open connection for which there is a need for redirections (this need is verified before this method is called).
     * It opens a new connection every time, up to the point we reach a certain number of redirections defined by "maxRedirects".
	 * @param urlId
	 * @param sourceUrl
	 * @param pageUrl
	 * @param internalLink
	 * @param conn
	 * @param responceCode
	 * @param domainStr
	 * @param calledForPageUrl
	 * @param calledForPossibleDocUrl
	 * @return Last open connection. If there was any problem, it returns "null".
	 * @throws AlreadyFoundDocUrlException
	 * @throws RuntimeException
	 * @throws ConnTimeoutException
	 * @throws DomainBlockedException
	 * @throws DomainWithUnsupportedHEADmethodException
	 */
	public static HttpURLConnection handleRedirects(String urlId, String sourceUrl, String pageUrl, String internalLink, HttpURLConnection conn, int responceCode, String domainStr, boolean calledForPageUrl, boolean calledForPossibleDocUrl)
																			throws AlreadyFoundDocUrlException, RuntimeException, ConnTimeoutException, DomainBlockedException, DomainWithUnsupportedHEADmethodException
	{
		int curRedirectsNum = 0;
		int maxRedirects = 0;
		String initialUrl = null;
		String urlType = null;	// Used for logging.
		
		if ( calledForPageUrl ) {
			maxRedirects = maxRedirectsForPageUrls;
			initialUrl = sourceUrl;	// Keep initialUrl for logging and debugging.
			urlType = "pageUrl";
		} else {
			maxRedirects = maxRedirectsForInternalLinks;
			initialUrl = internalLink;
			urlType = "internalLink";
		}
		
		try {
			do {	// We assume we already have an HTTP-3XX response code.
				curRedirectsNum ++;
				if ( curRedirectsNum > maxRedirects )
					throw new RuntimeException("Redirects exceeded their limit (" + maxRedirects + ") for " + urlType + ": \"" + initialUrl + "\"");
				
				String location = conn.getHeaderField("Location");
				if ( location == null )
				{
					if ( responceCode == 300 ) {	// The "Location"-header MAY be provided, giving the proposed link by the server.
						// Go and parse the page and select one of the links to redirect to. Assign it to the "location".
						if ( (location = ConnSupportUtils.getInternalLinkFromHTTP300Page(conn)) == null )
							throw new RuntimeException("No \"link\" was retrieved from the HTTP-300-page: \"" + conn.getURL().toString() + "\".");
					}
					else	// It's unacceptable for codes > 300 to not provide the "location" field.
						throw new RuntimeException("No \"Location\" field was found in the HTTP Header of \"" + conn.getURL().toString() + "\", after recieving an \"HTTP " + responceCode + "\" Redirect Code.");
				}
				
				String lowerCaseLocation = location.toLowerCase();
				if ( (calledForPageUrl && UrlTypeChecker.shouldNotAcceptPageUrl(location, lowerCaseLocation))	// Redirecting a pageUrl.
						|| (!calledForPageUrl && UrlTypeChecker.shouldNotAcceptInternalLink(location, lowerCaseLocation)) )	// Redirecting an internalPageLink.
					throw new RuntimeException("Url: \"" + initialUrl + "\" was prevented to redirect to the unwanted location: \"" + location + "\", after recieving an \"HTTP " + responceCode + "\" Redirect Code.");
				else if ( lowerCaseLocation.contains("sharedsitesession") ) {	// either "getSharedSiteSession" or "consumeSharedSiteSession".
					ConnSupportUtils.blockSharedSiteSessionDomain(initialUrl, domainStr);
					throw new DomainBlockedException();
				}
				
				String targetUrl = ConnSupportUtils.getFullyFormedUrl(null, location, conn.getURL());
				if ( targetUrl == null )
					throw new RuntimeException();
				
				//ConnSupportUtils.printRedirectDebugInfo(conn, location, targetUrl, curRedirectsNum);	// throws IOException
				
				if ( UrlUtils.docUrlsWithKeys.containsKey(targetUrl) ) {	// If we got into an already-found docUrl, log it and return.
					logger.info("re-crossed docUrl found: <" + targetUrl + ">");
					if ( FileUtils.shouldDownloadDocFiles )
						UrlUtils.logQuadruple(urlId, sourceUrl, pageUrl, targetUrl, UrlUtils.alreadyDownloadedByIDMessage + UrlUtils.docUrlsWithKeys.get(targetUrl), domainStr);
					else
						UrlUtils.logQuadruple(urlId, sourceUrl, pageUrl, targetUrl, "", domainStr);
					throw new AlreadyFoundDocUrlException();
				}
				/*else if ( calledForPageUrl && targetUrl.contains("elsevier.com") ) {	// Avoid pageUrls redirecting to "elsevier.com" (mostly "doi.org"-urls).
					logger.debug("Url: \"" + initialUrl + "\" was prevented to redirect to the unwanted url: \"" + targetUrl + "\", after recieving an \"HTTP " + responceCode + "\" Redirect Code.");
					throw new RuntimeException();
				}*/
				
				if ( !targetUrl.contains(HttpConnUtils.lastConnectedHost) )    // If the next page is not in the same domain as the "lastConnectedHost", we have to find the domain again inside "openHttpConnection()" method.
					if ( (domainStr = UrlUtils.getDomainStr(targetUrl)) == null )
						throw new RuntimeException();	// The cause it's already logged inside "getDomainStr()".

				if ( (targetUrl = URLCanonicalizer.getCanonicalURL(targetUrl, null, StandardCharsets.UTF_8)) == null ) {
					logger.warn("Could not cannonicalize url: " + targetUrl);
					throw new RuntimeException();	// Don't let it continue.
				}

				conn.disconnect();
				conn = HttpConnUtils.openHttpConnection(targetUrl, domainStr, calledForPageUrl, calledForPossibleDocUrl);
				
				responceCode = conn.getResponseCode();	// It's already checked for -1 case (Invalid HTTP), inside openHttpConnection().
				
				if ( (responceCode >= 200) && (responceCode <= 299) ) {
					//ConnSupportUtils.printFinalRedirectDataForWantedUrlType(initialUrl, conn.getURL().toString(), null, curRedirectsNum);	// DEBUG!
					return conn;	// It's an "HTTP SUCCESS", return immediately.
				}
			} while ( (responceCode >= 300) && (responceCode <= 399) );
			
			// It should have returned if there was an HTTP 2XX code. Now we have to handle the error-code.
			ConnSupportUtils.onErrorStatusCode(conn.getURL().toString(), domainStr, responceCode);
			throw new RuntimeException();	// This is not thrown if a "DomainBlockedException" was thrown first.
			
		} catch (AlreadyFoundDocUrlException | RuntimeException | ConnTimeoutException | DomainBlockedException | DomainWithUnsupportedHEADmethodException e) {	// We already logged the right messages.
			conn.disconnect();
			throw e;
		} catch (Exception e) {
			logger.warn("", e);
			conn.disconnect();
			throw new RuntimeException();
		}
	}
	
}

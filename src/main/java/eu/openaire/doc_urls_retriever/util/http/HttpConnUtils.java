package eu.openaire.doc_urls_retriever.util.http;

import edu.uci.ics.crawler4j.url.URLCanonicalizer;
import eu.openaire.doc_urls_retriever.crawler.PageCrawler;
import eu.openaire.doc_urls_retriever.crawler.SpecialUrlsHandler;
import eu.openaire.doc_urls_retriever.exceptions.*;
import eu.openaire.doc_urls_retriever.util.file.FileUtils;
import eu.openaire.doc_urls_retriever.util.url.LoaderAndChecker;
import eu.openaire.doc_urls_retriever.util.url.UrlTypeChecker;
import eu.openaire.doc_urls_retriever.util.url.UrlUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * @author Lampros Smyrnaios
 */
public class HttpConnUtils
{
	private static final Logger logger = LoggerFactory.getLogger(HttpConnUtils.class);

	public static final Set<String> domainsWithUnsupportedHeadMethod = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
	static {	// Add domains which were manually observed to act strangely and cannot be detected automatically at run-time.
		domainsWithUnsupportedHeadMethod.add("os.zhdk.cloud.switch.ch");	// This domain returns "HTTP-403-ERROR" when it does not support the "HEAD" method, at least when checking an actual file.
	}

	public static final Set<String> domainsWithUnsupportedAcceptLanguageParameter = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

	public static final Set<String> blacklistedDomains = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());	// Domains with which we don't want to connect again.

	public static final Hashtable<String, Integer> timesDomainsHadInputNotBeingDocNorPage = new Hashtable<String, Integer>();
	public static final Hashtable<String, Integer> timesDomainsReturnedNoType = new Hashtable<String, Integer>();	// Domain which returned no content-type not content disposition in their response and amount of times they did.

	public static AtomicInteger numOfDomainsBlockedDueToSSLException = new AtomicInteger(0);

	public static String lastConnectedHost = "";
	public static String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:87.0) Gecko/20100101 Firefox/87.0";
	public static String acceptLanguage = "en,en;q=0.5";

	public static final int maxConnGETWaitingTime = 15000;	// Max time (in ms) to wait for a connection, using "HTTP GET".
	public static final int maxConnHEADWaitingTime = 10000;	// Max time (in ms) to wait for a connection, using "HTTP HEAD".

	private static final int maxRedirectsForPageUrls = 7;// The usual redirect times for doi.org urls is 3, though some of them can reach even 5 (if not more..)
	private static final int maxRedirectsForInternalLinks = 2;	// Internal-DOC-Links shouldn't take more than 2 redirects.

	private static final int timesToHaveNoDocNorPageInputBeforeBlocked = 10;

	public static final int maxAllowedContentSize = 1073741824;	// 1Gb ; yes some publications can be huge..
	private static final boolean shouldNOTacceptGETmethodForUncategorizedInternalLinks = true;


	public static final Set<String> domainsSupportingHTTPS = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

	public static AtomicInteger timesDidOfflineHTTPSredirect = new AtomicInteger(0);


	/**
	 * This method checks if a certain url can give us its mimeType, as well as if this mimeType is a docMimeType.
	 * It automatically calls the "logUrl()" method for the valid docUrls, while it doesn't call it for non-success cases, thus allowing calling method to handle the case.
	 * @param urlId
	 * @param sourceUrl	// The inputUrl
	 * @param pageUrl	// May be the inputUrl or a redirected version of it.
	 * @param resourceURL	// May be the inputUrl or an internalLink of that inputUrl.
	 * @param domainStr
	 * @param calledForPageUrl
	 * @param calledForPossibleDocOrDatasetUrl
	 * @return "true", if it's a docMimeType, otherwise, "false", if it has a different mimeType.
	 * @throws RuntimeException (when there was a network error).
	 * @throws ConnTimeoutException
	 * @throws DomainBlockedException
	 * @throws DomainWithUnsupportedHEADmethodException
	 */
	public static boolean connectAndCheckMimeType(String urlId, String sourceUrl, String pageUrl, String resourceURL, String domainStr, boolean calledForPageUrl, boolean calledForPossibleDocOrDatasetUrl)
													throws RuntimeException, ConnTimeoutException, DomainBlockedException, DomainWithUnsupportedHEADmethodException
	{
		HttpURLConnection conn = null;
		try {
			if ( domainStr == null )	// No info about domainStr from the calling method.. we have to find it here.
				if ( (domainStr = UrlUtils.getDomainStr(resourceURL, null)) == null )
					throw new RuntimeException();	// The cause it's already logged inside "getDomainStr()".

			conn = handleConnection(urlId, sourceUrl, pageUrl, resourceURL, domainStr, calledForPageUrl, calledForPossibleDocOrDatasetUrl);

			// Check if we are able to find the mime type, if not then try "Content-Disposition".
			String mimeType = conn.getContentType();
			String contentDisposition = null;

			String finalUrlStr = conn.getURL().toString();

			if ( !finalUrlStr.contains(domainStr) )	// Get the new domain after possible change from redirects.
				if ( (domainStr = UrlUtils.getDomainStr(finalUrlStr, null)) == null )
					throw new RuntimeException();	// The cause it's already logged inside "getDomainStr()".

			boolean foundDetectedContentType = false;
			String firstHtmlLine = null;
			BufferedReader bufferedReader = null;

			///////////////////////////
			//mimeType = null;	// DEBUG!
			///////////////////////////

			if ( mimeType == null ) {
				contentDisposition = conn.getHeaderField("Content-Disposition");
				if ( contentDisposition == null ) {
					ArrayList<Object> detectionList = ConnSupportUtils.detectContentTypeFromResponseBody(finalUrlStr, domainStr, conn, calledForPageUrl);
					mimeType = (String)detectionList.get(0);
					foundDetectedContentType = (boolean)detectionList.get(1);
					firstHtmlLine = (String)detectionList.get(2);
					bufferedReader = (BufferedReader) detectionList.get(3);
					calledForPossibleDocOrDatasetUrl = (boolean) detectionList.get(4);
					//logger.debug(mimeType); logger.debug(String.valueOf(foundDetectedContentType)); logger.debug(firstHtmlLine); logger.debug(String.valueOf(bufferedReader)); logger.debug(String.valueOf(calledForPossibleDocUrl));	// DEBUG!
				}
			}

			String lowerCaseMimeType = mimeType;
			if ( mimeType != null && !foundDetectedContentType )	// It may have gained a value after auto-detection, so we check again. If it was auto-detected, then it's already in lowercase.
				lowerCaseMimeType = mimeType.toLowerCase();	// I found the rare case of "Application/pdf", so we need lowercase as any mimeType could have either uppercase or lowercase version..

			//logger.debug("Url: " + finalUrlStr);	// DEBUG!
			//logger.debug("MimeType: " + mimeType);	// DEBUG!
			String returnedType = ConnSupportUtils.hasDocOrDatasetMimeType(finalUrlStr, lowerCaseMimeType, contentDisposition, conn, calledForPageUrl, calledForPossibleDocOrDatasetUrl);
			if ( (returnedType != null) && !finalUrlStr.contains("academic.microsoft.com/api") )	// The "academic.microsoft.com/api" is a json-page which provides the docUrls, so avoid identify it as a "dataset" here..
			{
				if ( LoaderAndChecker.retrieveDocuments && returnedType.equals("document") ) {
					logger.info("docUrl found: < " + finalUrlStr + " >");
					String fullPathFileName = "";
					if ( FileUtils.shouldDownloadDocFiles ) {
						try {
							if ( foundDetectedContentType ) {	// If we went and detected the pdf from the request-code, then reconnect and proceed with downloading (reasons explained elsewhere).
								conn = handleConnection(urlId, sourceUrl, pageUrl, finalUrlStr, domainStr, calledForPageUrl, calledForPossibleDocOrDatasetUrl);	// No need to "conn.disconnect()" before, as we are re-connecting to the same domain.
							}
							fullPathFileName = ConnSupportUtils.downloadAndStoreDocFile(conn, domainStr, finalUrlStr, calledForPageUrl);
							logger.info("DocFile: \"" + fullPathFileName + "\" has been downloaded.");
						} catch (DocFileNotRetrievedException dfnde) {
							fullPathFileName = "DocFileNotRetrievedException was thrown before the docFile could be stored. Stacktrace:";
							StackTraceElement[] stels = dfnde.getStackTrace();
							StringBuilder sb = new StringBuilder(22).append(fullPathFileName).append(FileUtils.endOfLine);	// This StringBuilder is thread-safe as a local-variable.
							for ( int i = 0; (i < stels.length) && (i < 10); ++i ) {
								sb.append(stels[i]);
								if (i < 9) sb.append(FileUtils.endOfLine);
							}
							logger.error(sb.toString());	// TODO - Instead of the stacktrace, provide the right message when first thrown, just like in "RuntimeException".
						}
					}
					UrlUtils.logOutputData(urlId, sourceUrl, pageUrl, finalUrlStr, fullPathFileName, null, true, "true", "true", "true");	// we send the urls, before and after potential redirections.
					return true;
				}
				else if ( LoaderAndChecker.retrieveDatasets && returnedType.equals("dataset") ) {
					logger.info("datasetUrl found: < " + finalUrlStr + " >");
					// TODO - handle possible download and improve logging...
					String fullPathFileName = FileUtils.shouldDownloadDocFiles ? "It's a dataset-url. The download is not supported." : "It's a dataset-url.";
					UrlUtils.logOutputData(urlId, sourceUrl, pageUrl, finalUrlStr, fullPathFileName, null, true, "true", "true", "true");	// we send the urls, before and after potential redirections.
					return true;
				}
				else {	// Either "document" or "dataset", but the user specified that he doesn't want it.
					//logger.debug("Type \"" + returnedType + "\", which was specified that it's unwanted in this run, was found for url: < " + finalUrlStr + " >");	// DEBUG!
					if ( calledForPageUrl )
						UrlUtils.logOutputData(urlId, sourceUrl, null, "unreachable", "It was discarded in 'HttpConnUtils.connectAndCheckMimeType()', after matching to an unwanted mimeType: " + returnedType, null, true, "true", "true", "false");
					return false;
				}
			}
			else if ( calledForPageUrl ) {	// Visit this url only if this method was called for an inputUrl.
				if ( finalUrlStr.contains("viewcontent.cgi") ) {	// If this "viewcontent.cgi" isn't a docUrl, then don't check its internalLinks. Check this: "https://docs.lib.purdue.edu/cgi/viewcontent.cgi?referer=&httpsredir=1&params=/context/physics_articles/article/1964/type/native/&path_info="
					logger.warn("Unwanted pageUrl: \"" + finalUrlStr + "\" will not be visited!");
					UrlUtils.logOutputData(urlId, sourceUrl, null, "unreachable", "It was discarded in 'HttpConnUtils.connectAndCheckMimeType()', after matching to a non-docUrl with 'viewcontent.cgi'.", null, true, "true", "true", "false");
					return false;
				}
				else if ( (lowerCaseMimeType != null) && ((lowerCaseMimeType.contains("htm") || (lowerCaseMimeType.contains("text") && !lowerCaseMimeType.contains("xml") && !lowerCaseMimeType.contains("csv") && !lowerCaseMimeType.contains("tsv")))) )	// The content-disposition is non-usable in the case of pages.. it's probably not provided anyway.
					// TODO - Better make a regex for the above checks..
					PageCrawler.visit(urlId, sourceUrl, finalUrlStr, mimeType, conn, firstHtmlLine, bufferedReader);
				else if ( finalUrlStr.contains("academic.microsoft.com/api/") )	// JSON content.
					SpecialUrlsHandler.extractDocUrlFromAcademicMicrosoftJson(urlId, sourceUrl, finalUrlStr, conn);
				else {
					logger.warn("Non-pageUrl: \"" + finalUrlStr + "\" with mimeType: \"" + mimeType + "\" will not be visited!");
					UrlUtils.logOutputData(urlId, sourceUrl, null, "unreachable", "It was discarded in 'HttpConnUtils.connectAndCheckMimeType()', after not matching to a docUrl nor to an htm/text-like page.", null, true, "true", "true", "false");
					if ( ConnSupportUtils.countAndBlockDomainAfterTimes(blacklistedDomains, timesDomainsHadInputNotBeingDocNorPage, domainStr, HttpConnUtils.timesToHaveNoDocNorPageInputBeforeBlocked, true) )
						logger.warn("Domain: \"" + domainStr + "\" was blocked after having no Doc nor Pages in the input more than " + HttpConnUtils.timesToHaveNoDocNorPageInputBeforeBlocked + " times.");
				}	// We log the quadruple here, as there is connection-kind-of problem here.. it's just us considering it an unwanted case. We don't throw "DomainBlockedException()", as we don't handle it for inputUrls (it would also log the quadruple twice with diff comments).
			}
		} catch (AlreadyFoundDocUrlException afdue) {	// An already-found docUrl was discovered during redirections.
			return true;	// It's already logged for the outputFile.
		} catch (RuntimeException re) {
			if ( re instanceof NullPointerException )
				re.printStackTrace();

			if ( calledForPageUrl ) {
				LoaderAndChecker.connProblematicUrls.incrementAndGet();
				ConnSupportUtils.printEmbeddedExceptionMessage(re, resourceURL);
			}	// Log this error only for docPages or possibleDocOrDatasetUrls, not other internalLinks.
			else if ( calledForPossibleDocOrDatasetUrl )
				ConnSupportUtils.printEmbeddedExceptionMessage(re, resourceURL);
			throw re;
		} catch (ConnTimeoutException cte) {
			if ( calledForPageUrl )
				UrlTypeChecker.longToRespondUrls.incrementAndGet();
			throw cte;
		} catch (DomainBlockedException | DomainWithUnsupportedHEADmethodException e) {
			if ( calledForPageUrl )
				LoaderAndChecker.connProblematicUrls.incrementAndGet();
			throw e;
		} catch (Exception e) {
			if ( calledForPageUrl ) {	// Log this error only for docPages.
				logger.warn("Could not handle connection for \"" + resourceURL + "\"!");
				LoaderAndChecker.connProblematicUrls.incrementAndGet();
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
		if ( (domainStr == null) && (domainStr = UrlUtils.getDomainStr(resourceURL, null)) == null )
			throw new RuntimeException();

		HttpURLConnection conn = openHttpConnection(resourceURL, domainStr, calledForPageUrl, calledForPossibleDocUrl);
		// The "resourceUrl" might have changed (due to special-handling of some pages), but it doesn't cause any problem. It's only used with "internalLinks" which are not affected by the special handling.

		//ConnSupportUtils.printConnectionDebugInfo(conn, true);	// DEBUG!

		int responseCode = conn.getResponseCode();	// It's already checked for -1 case (Invalid HTTP response), inside openHttpConnection().
		if ( (responseCode >= 300) && (responseCode <= 399) ) {   // If we have redirections..
			conn = handleRedirects(urlId, sourceUrl, pageUrl, resourceURL, conn, responseCode, domainStr, calledForPageUrl, calledForPossibleDocUrl);    // Take care of redirects.
		}
		else if ( (responseCode < 200) || (responseCode >= 400) ) {	// If we have error codes.
			String errorMessage = ConnSupportUtils.onErrorStatusCode(conn.getURL().toString(), domainStr, responseCode, calledForPageUrl);
			throw new RuntimeException(errorMessage);	// This is only thrown if a "DomainBlockedException" is caught.
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
		HttpURLConnection conn = null;
		int responseCode = 0;

		try {
			if ( blacklistedDomains.contains(domainStr) )
		    	throw new RuntimeException("Avoid connecting to blackListed domain: \"" + domainStr + "\" with url: " + resourceURL);

			// Check whether we don't accept "GET" method for uncategorizedInternalLinks and if this url is such a case.
			if ( !calledForPageUrl && shouldNOTacceptGETmethodForUncategorizedInternalLinks
					&& !calledForPossibleDocUrl && domainsWithUnsupportedHeadMethod.contains(domainStr) )	// Exclude the possibleDocUrls and the ones which cannot connect with "HEAD".
				throw new DomainWithUnsupportedHEADmethodException();

			if ( ConnSupportUtils.checkIfPathIs403BlackListed(resourceURL, domainStr) )
				throw new RuntimeException("Avoid reaching 403ErrorCode with url: \"" + resourceURL + "\"!");

			if ( !resourceURL.startsWith("https", 0) && domainsSupportingHTTPS.contains(domainStr) ) {
				resourceURL = ConnSupportUtils.offlineRedirectToHTTPS(resourceURL);
				timesDidOfflineHTTPSredirect.incrementAndGet();
			}

			// For the urls which has reached this point, make sure no weird "ampersand"-anomaly blocks us...
			boolean weirdMetaDocUrlWhichNeedsGET = false;
			if ( calledForPossibleDocUrl && resourceURL.contains("amp%3B") ) {
				//logger.debug("Just arrived weirdMetaDocUrl: " + resourceURL);
				resourceURL = StringUtils.replace(resourceURL, "amp%3B", "", -1);
				//logger.debug("After replacement in the weirdMetaDocUrl: " + resourceURL);
				weirdMetaDocUrlWhichNeedsGET = true;
			}

			boolean havingScienceDirectPDF = false;
			if ( "pdf.sciencedirectassets.com".equals(domainStr) )	// Avoiding NPE.
				havingScienceDirectPDF = true;
			else if ( calledForPageUrl && !calledForPossibleDocUrl )
				resourceURL = SpecialUrlsHandler.checkAndHandleSpecialUrls(resourceURL);	// May throw a "RuntimeException".

			URL url = new URL(resourceURL);
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestProperty("User-Agent", userAgent);
			if ( !domainsWithUnsupportedAcceptLanguageParameter.contains(domainStr) )
				conn.setRequestProperty("Accept-Language", acceptLanguage);
			conn.setInstanceFollowRedirects(false);	// We manage redirects on our own, in order to control redirectsNum, avoid redirecting to unwantedUrls and handling errors.

			boolean useHttpGetMethod = false;

			if ( (calledForPageUrl && !calledForPossibleDocUrl)	// For just-webPages, we want to use "GET" in order to download the content.
				|| (calledForPossibleDocUrl && FileUtils.shouldDownloadDocFiles)	// For docUrls, if we should download them.
				|| weirdMetaDocUrlWhichNeedsGET	// If we have a weirdMetaDocUrl-case then we need "GET".
				|| havingScienceDirectPDF	// If we got the new scienceDirect-DocUrl, then we should go only with "GET".
				|| domainsWithUnsupportedHeadMethod.contains(domainStr) )	// If the domain doesn't support "HEAD", then we only do "GET".
			{
				conn.setRequestMethod("GET");	// Go directly with "GET".
				conn.setConnectTimeout(maxConnGETWaitingTime);
				conn.setReadTimeout(maxConnGETWaitingTime);
				useHttpGetMethod = true;
			} else {
				conn.setRequestMethod("HEAD");	// Else, try "HEAD" (it may be either a domain that supports "HEAD", or a new domain, for which we have no info yet).
				conn.setConnectTimeout(maxConnHEADWaitingTime);
				conn.setReadTimeout(maxConnHEADWaitingTime);
			}

			ConnSupportUtils.applyPolitenessDelay(resourceURL, domainStr);	// This is synchronized.

			conn.connect();	// Else, first connect and if there is no error, log this domain as the last one.

			if ( (responseCode = conn.getResponseCode()) == -1 )
				throw new RuntimeException("Invalid HTTP response for \"" + resourceURL + "\"");

			if ( responseCode == 406 )	// It's possible that the server does not support the "Accept-Language" parameter.
			{
				resourceURL = conn.getURL().toString();

				if ( (domainStr = UrlUtils.getDomainStr(resourceURL, null)) != null ) {
					logger.warn("The server \"" + domainStr + "\" probably does not support the \"Accept-Language\" parameter. Going to reconnect without it");
					domainsWithUnsupportedAcceptLanguageParameter.add(domainStr);	// Take note that this domain does not support it..
				}

				url = new URL(resourceURL);
				conn = (HttpURLConnection) url.openConnection();
				conn.setRequestProperty("User-Agent", userAgent);
				conn.setInstanceFollowRedirects(false);

				if ( useHttpGetMethod ) {
					conn.setRequestMethod("GET");	// Go directly with "GET".
					conn.setConnectTimeout(maxConnGETWaitingTime);
					conn.setReadTimeout(maxConnGETWaitingTime);
				} else {
					conn.setRequestMethod("HEAD");	// Else, try "HEAD" (it may be either a domain that supports "HEAD", or a new domain, for which we have no info yet).
					conn.setConnectTimeout(maxConnHEADWaitingTime);
					conn.setReadTimeout(maxConnHEADWaitingTime);
				}

				ConnSupportUtils.applyPolitenessDelay(resourceURL, domainStr);	// This is synchronized.

				conn.connect();	// Else, first connect and if there is no error, log this domain as the last one.

				if ( conn.getResponseCode() == -1 )
					throw new RuntimeException("Invalid HTTP response for \"" + resourceURL + "\"");
			}
			else if ( ((responseCode == 405) || (responseCode == 501)) && conn.getRequestMethod().equals("HEAD") )	// If this SERVER doesn't support "HEAD" method or doesn't allow us to use it..
			{
				resourceURL = conn.getURL().toString();	// Update as it might have changed after redirections.

				//logger.debug("HTTP \"HEAD\" method is not supported for: \"" + resourceURL +"\". Server's responseCode was: " + responseCode);

				// Take the new domain as we are not sure if the initial domain caused this issue or an another domains to which the initialUrl was redirected to..
				if ( (domainStr = UrlUtils.getDomainStr(resourceURL, null)) != null )
					domainsWithUnsupportedHeadMethod.add(domainStr);	// This domain doesn't support "HEAD" method, log it and then check if we can retry with "GET" or not.

				if ( !calledForPageUrl && shouldNOTacceptGETmethodForUncategorizedInternalLinks && !calledForPossibleDocUrl )	// If we set not to retry with "GET" when we try uncategorizedInternalLinks, throw the related exception and stop the crawling of this page.
					throw new DomainWithUnsupportedHEADmethodException();

				// If we accept connection's retrying, using "GET", move on reconnecting.
				// No call of "conn.disconnect()" here, as we will connect to the same server.
				url = new URL(resourceURL);
				conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("GET");	// To reach here, it means that the HEAD method is unsupported.
				conn.setRequestProperty("User-Agent", userAgent);
				if ( !domainsWithUnsupportedAcceptLanguageParameter.contains(domainStr) )
					conn.setRequestProperty("Accept-Language", acceptLanguage);
				conn.setConnectTimeout(maxConnGETWaitingTime);
				conn.setReadTimeout(maxConnGETWaitingTime);
				conn.setInstanceFollowRedirects(false);

				ConnSupportUtils.applyPolitenessDelay(resourceURL, domainStr);	// This is synchronized.

				conn.connect();
				//logger.debug("responseCode for \"" + resourceURL + "\", after setting conn-method to: \"" + conn.getRequestMethod() + "\" is: " + conn.getResponseCode());

				responseCode = conn.getResponseCode();
				if ( responseCode == -1 )	// Make sure we throw a RunEx on invalidHTTP.
					throw new RuntimeException("Invalid HTTP response for \"" + resourceURL + "\"");

				if ( responseCode == 406 )	// It's possible that the server does not support the "Accept-Language" parameter.
				{
					//logger.debug("The \"Accept-Language\" parameter is probably not supported for: \"" + resourceURL +"\". Server's responseCode was: " + responseCode);

					if ( (domainStr = UrlUtils.getDomainStr(conn.getURL().toString(), null)) != null ) {
						logger.warn("The server \"" + domainStr + "\" probably does not support the \"Accept-Language\" parameter. Going to reconnect without it");
						domainsWithUnsupportedAcceptLanguageParameter.add(domainStr);	// Take note that this domain does not support it..
					}

					url = new URL(resourceURL);
					conn = (HttpURLConnection) url.openConnection();
					conn.setRequestMethod("GET");	// To reach here, it means that the HEAD method is unsupported.
					conn.setRequestProperty("User-Agent", userAgent);
					conn.setConnectTimeout(maxConnGETWaitingTime);
					conn.setReadTimeout(maxConnGETWaitingTime);
					conn.setInstanceFollowRedirects(false);

					ConnSupportUtils.applyPolitenessDelay(resourceURL, domainStr);	// This is synchronized.

					conn.connect();	// Else, first connect and if there is no error, log this domain as the last one.

					if ( (conn.getResponseCode()) == -1 )
						throw new RuntimeException("Invalid HTTP response for \"" + resourceURL + "\"");
				}
			}
		} catch (RuntimeException | DomainWithUnsupportedHEADmethodException redwuhme) {
			if ( conn != null )
				conn.disconnect();
			throw redwuhme;	// We want to throw the same exception to keep the messages and the stacktrace in place.
		} catch (Exception e) {
			if ( conn != null )
				conn.disconnect();
			if ( e instanceof UnknownHostException ) {
				logger.debug("A new \"Unknown Network\" Host was found and blacklisted: \"" + domainStr + "\"");
				blacklistedDomains.add(domainStr);	//Log it to never try connecting with it again.
				throw new DomainBlockedException(domainStr);
			}
			else if ( e instanceof SocketTimeoutException ) {
				logger.debug("Url: \"" + resourceURL + "\" failed to respond on time!");
				ConnSupportUtils.onTimeoutException(domainStr);	// May throw a "DomainBlockedException", which will be thrown before the "ConnTimeoutException".
				throw new ConnTimeoutException();
			}
			else if ( e instanceof ConnectException ) {
				String eMsg = e.getMessage();
				if ( (eMsg != null) && eMsg.toLowerCase().contains("timeout") ) {	// If it's a "connection timeout" type of exception, treat it like it.
					ConnSupportUtils.onTimeoutException(domainStr);	// Can throw a "DomainBlockedException", which will be thrown before the "ConnTimeoutException".
					throw new ConnTimeoutException();
				}
				throw new RuntimeException(eMsg);
			}
			else if ( e instanceof SSLException ) {
				// TODO - For "SSLProtocolException", see more about it's possible handling here: https://stackoverflow.com/questions/7615645/ssl-handshake-alert-unrecognized-name-error-since-upgrade-to-java-1-7-0/14884941#14884941
				// TODO - Maybe we should make another list where only urls in https, from these domains, would be blocked.
				blacklistedDomains.add(domainStr);
				numOfDomainsBlockedDueToSSLException.incrementAndGet();
				logger.warn("No Secure connection was able to be negotiated with the domain: \"" + domainStr + "\", so it was blocked. Exception message: " + e.getMessage());
				throw new DomainBlockedException(domainStr);
			}
			else if ( e instanceof SocketException ) {
				String errorMsg = e.getMessage();
				if ( errorMsg != null )
					errorMsg = "\"" + errorMsg + "\". This SocketException was received after trying to connect with the domain: \"" + domainStr + "\"";

				// We don't block the domain, since this is temporary.
				throw new RuntimeException(errorMsg);
			}

			logger.error("", e);
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
	 * @param responseCode
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
	public static HttpURLConnection handleRedirects(String urlId, String sourceUrl, String pageUrl, String internalLink, HttpURLConnection conn, int responseCode, String domainStr, boolean calledForPageUrl, boolean calledForPossibleDocUrl)
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
		URL currentUrlObject;
		String currentUrl;

		try {
			do {	// We assume we already have an HTTP-3XX response code.
				curRedirectsNum ++;
				if ( curRedirectsNum > maxRedirects )
					throw new RuntimeException("Redirects exceeded their limit (" + maxRedirects + ") for " + urlType + ": \"" + initialUrl + "\"");

				currentUrlObject = conn.getURL();
				currentUrl = currentUrlObject.toString();

				String location = conn.getHeaderField("Location");
				if ( location == null )
				{
					if ( responseCode == 300 ) {	// The "Location"-header MAY be provided, giving the proposed link by the server.
						// Go and parse the page and select one of the links to redirect to. Assign it to the "location".
						if ( (location = ConnSupportUtils.getInternalLinkFromHTTP300Page(conn)) == null )
							throw new RuntimeException("No \"link\" was retrieved from the HTTP-300-page: \"" + currentUrl + "\".");
					}
					else	// It's unacceptable for codes > 300 to not provide the "location" field.
						throw new RuntimeException("No \"Location\" field was found in the HTTP Header of \"" + currentUrl + "\", after receiving an \"HTTP " + responseCode + "\" Redirect Code.");
				}

				String targetUrl = ConnSupportUtils.getFullyFormedUrl(null, location, currentUrlObject);
				if ( targetUrl == null )
					throw new RuntimeException("Could not create target url for resourceUrl: " + currentUrl + " having location: " + location);

				String lowerCaseTargetUrl = targetUrl.toLowerCase();
				if ( (calledForPageUrl && UrlTypeChecker.shouldNotAcceptPageUrl(targetUrl, lowerCaseTargetUrl))	// Redirecting a pageUrl.
						|| (!calledForPageUrl && UrlTypeChecker.shouldNotAcceptInternalLink(targetUrl, lowerCaseTargetUrl)) )	// Redirecting an internalPageLink.
					throw new RuntimeException("Url: \"" + initialUrl + "\" was prevented to redirect to the unwanted location: \"" + targetUrl + "\", after receiving an \"HTTP " + responseCode + "\" Redirect Code, in redirection-number: " + curRedirectsNum);
				else if ( lowerCaseTargetUrl.contains("sharedsitesession") ) {	// either "getSharedSiteSession" or "consumeSharedSiteSession".
					logger.warn("Initial-url: \"" + initialUrl + "\" tried to cause a \"sharedSiteSession-redirectionPack\" by redirecting to \"" + targetUrl + "\"!");
					List<String> blockedDomains = ConnSupportUtils.blockSharedSiteSessionDomains(targetUrl, currentUrl);
					throw new DomainBlockedException(blockedDomains);
				}

				String tempTargetUrl = targetUrl;
				if ( !targetUrl.contains("#/") && (targetUrl = URLCanonicalizer.getCanonicalURL(targetUrl, null, StandardCharsets.UTF_8)) == null )
					throw new RuntimeException("Could not canonicalize target url: " + tempTargetUrl);	// Don't let it continue.

				//ConnSupportUtils.printRedirectDebugInfo(currentUrl, location, targetUrl, responseCode, curRedirectsNum);

				if ( UrlUtils.docOrDatasetUrlsWithIDs.containsKey(targetUrl) ) {	// If we got into an already-found docUrl, log it and return.
					logger.info("re-crossed docUrl found: < " + targetUrl + " >");
					LoaderAndChecker.reCrossedDocUrls.incrementAndGet();
					if ( FileUtils.shouldDownloadDocFiles )
						UrlUtils.logOutputData(urlId, sourceUrl, pageUrl, targetUrl, UrlUtils.alreadyDownloadedByIDMessage + UrlUtils.docOrDatasetUrlsWithIDs.get(targetUrl), null, false, "true", "true", "true");
					else
						UrlUtils.logOutputData(urlId, sourceUrl, pageUrl, targetUrl, "", null, false, "true", "true", "true");
					throw new AlreadyFoundDocUrlException();
				}

				if ( !targetUrl.contains(domainStr) ) {    // If the next page is not in the same domain as the current one, we have to find the domain again.
					conn.disconnect();	// Close the socket with that server.
					if ( (domainStr = UrlUtils.getDomainStr(targetUrl, null)) == null )
						throw new RuntimeException();	// The cause it's already logged inside "getDomainStr()".
				}

				// Check if this redirection is just http-to-https and store the domain to do offline-redirection in the future.
				// If the domain supports it, do an offline-redirect to "HTTPS", thus avoiding the expensive real-redirect.
				if ( ConnSupportUtils.isJustAnHTTPSredirect(currentUrl, targetUrl) ) {
					domainsSupportingHTTPS.add(domainStr);    // It does not store duplicates.
					//logger.debug("Found an HTTP-TO-HTTPS redirect: " + currentUrl + " --> " + targetUrl);	// DEBUG!
				}

				conn = HttpConnUtils.openHttpConnection(targetUrl, domainStr, calledForPageUrl, calledForPossibleDocUrl);

				responseCode = conn.getResponseCode();	// It's already checked for -1 case (Invalid HTTP), inside openHttpConnection().

				if ( (responseCode >= 200) && (responseCode <= 299) ) {
					//ConnSupportUtils.printFinalRedirectDataForWantedUrlType(initialUrl, currentUrl, null, curRedirectsNum);	// DEBUG!
					return conn;	// It's an "HTTP SUCCESS", return immediately.
				}
			} while ( (responseCode >= 300) && (responseCode <= 399) );
			
			// It should have returned if there was an HTTP 2XX code. Now we have to handle the error-code.
			String errorMessage = ConnSupportUtils.onErrorStatusCode(currentUrl, domainStr, responseCode, calledForPageUrl);
			throw new RuntimeException(errorMessage);	// This is not thrown if a "DomainBlockedException" was thrown first.
			
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

package eu.openaire.doc_urls_retriever.util.http;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import eu.openaire.doc_urls_retriever.crawler.PageCrawler;
import eu.openaire.doc_urls_retriever.exceptions.DocFileNotRetrievedException;
import eu.openaire.doc_urls_retriever.exceptions.DomainBlockedException;
import eu.openaire.doc_urls_retriever.exceptions.JavaScriptDocLinkFoundException;
import eu.openaire.doc_urls_retriever.util.file.FileUtils;
import eu.openaire.doc_urls_retriever.util.url.UrlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @author Lampros Smyrnaios
 */
public class ConnSupportUtils
{
	private static final Logger logger = LoggerFactory.getLogger(ConnSupportUtils.class);

	private static final StringBuilder htmlStrB = new StringBuilder(300000);	// We give an initial size (maxExpectedHtmlLength) to optimize performance (avoid re-allocations at run-time).
	
	public static final Pattern MIME_TYPE_FILTER = Pattern.compile("(?:\\((?:')?)?([\\w]+/[\\w+\\-.]+).*");

	public static final Pattern POSSIBLE_DOC_MIME_TYPE = Pattern.compile("(?:(?:application|binary)/(?:(?:x-)?octet-stream|save|force-download))|unknown");	// We don't take it for granted.. if a match is found, then we check for the "pdf" keyword in the "contentDisposition" (if it exists) or in the url.

	public static final HashMap<String, Integer> timesDomainsReturned5XX = new HashMap<String, Integer>();	// Domains that have returned HTTP 5XX Error Code, and the amount of times they did.
	public static final HashMap<String, Integer> timesDomainsHadTimeoutEx = new HashMap<String, Integer>();
	public static final HashMap<String, Integer> timesPathsReturned403 = new HashMap<String, Integer>();
	
	public static final SetMultimap<String, String> domainsMultimapWithPaths403BlackListed = HashMultimap.create();	// Holds multiple values for any key, if a domain(key) has many different paths (values) for which there was a 403 errorCode.
	
	private static final int timesToHave403errorCodeBeforePathBlocked = 5;	// If a path leads to 403 with different urls, more than 5 times, then this path gets blocked.
	private static final int numberOf403BlockedPathsBeforeDomainBlocked = 5;	// If a domain has more than 5 different 403-blocked paths, then the whole domain gets blocked.

	private static final int timesToHave5XXerrorCodeBeforeDomainBlocked = 10;
	private static final int timesToHaveTimeoutExBeforeDomainBlocked = 25;
	
	public static final HashSet<String> knownDocMimeTypes = new HashSet<String>();
	static {
		logger.debug("Setting up the official document mime types. Currently there is support only for pdf documents.");
		knownDocMimeTypes.add("application/pdf");
		knownDocMimeTypes.add("image/pdf");
	}
	
	
	/**
	 * This method takes a url and its mimeType and checks if it's a document mimeType or not.
	 * @param urlStr
	 * @param mimeType
	 * @param contentDisposition
	 * @return boolean
	 */
	public static boolean hasDocMimeType(String urlStr, String mimeType, String contentDisposition, HttpURLConnection conn)
	{
		if ( mimeType != null )
		{
			if ( mimeType.contains("System.IO.FileInfo") ) {	// Check this out: "http://www.esocialsciences.org/Download/repecDownload.aspx?fname=Document110112009530.6423303.pdf&fcategory=Articles&AId=2279&fref=repec", ιt has: "System.IO.FileInfo".
				// In this case, we want first to try the "Content-Disposition", as it's more trustworthy. If that's not available, use the urlStr as the last resort.
				if ( conn != null )	// Just to be sure we avoid an NPE.
					contentDisposition = conn.getHeaderField("Content-Disposition");
				// The "contentDisposition" will be definitely "null", since "mimeType != null" and so, the "contentDisposition" will not have been retrieved.
				
				if ( (contentDisposition != null) && !contentDisposition.equals("attachment") )
					return	contentDisposition.contains("pdf");	// TODO - add more types as needed. Check: "http://www.esocialsciences.org/Download/repecDownload.aspx?qs=Uqn/rN48N8UOPcbSXUd2VFI+dpOD3MDPRfIL8B3DH+6L18eo/yEvpYEkgi9upp2t8kGzrjsWQHUl44vSn/l7Uc1SILR5pVtxv8VYECXSc8pKLF6QJn6MioA5dafPj/8GshHBvLyCex2df4aviMvImCZpwMHvKoPiO+4B7yHRb97u1IHg45E+Z6ai0Z/0vacWHoCsNT9O4FNZKMsSzen2Cw=="
				else
					return	urlStr.toLowerCase().contains("pdf");
			}
			
			String plainMimeType = mimeType;	// Make sure we don't cause any NPE later on..
			if ( mimeType.contains("charset") || mimeType.contains("name")
					|| mimeType.startsWith("(", 0) )	// See: "https://www.mamsie.bbk.ac.uk/articles/10.16995/sim.138/galley/134/download/" -> "Content-Type: ('application/pdf', none)"
			{
				plainMimeType = getPlainMimeType(mimeType);
				if ( plainMimeType == null ) {    // If there was any error removing the charset, still try to save any docMimeType (currently pdf-only).
					logger.warn("Url with problematic mimeType (" + mimeType + ") was: " + urlStr);
					return	urlStr.toLowerCase().contains("pdf");
				}
			}
			
			if ( knownDocMimeTypes.contains(plainMimeType) )
				return true;
			else if ( POSSIBLE_DOC_MIME_TYPE.matcher(plainMimeType).matches() )
			{
				contentDisposition = conn.getHeaderField("Content-Disposition");
				if ( (contentDisposition != null) && !contentDisposition.equals("attachment") )
					return	contentDisposition.contains("pdf");
				else
					return	urlStr.toLowerCase().contains("pdf");
			}
			else
				return false;
			// This is a special case. (see: "https://kb.iu.edu/d/agtj" for "octet" info.
			// and an example for "unknown" : "http://imagebank.osa.org/getExport.xqy?img=OG0kcC5vZS0yMy0xNy0yMjE0OS1nMDAy&xtype=pdf&article=oe-23-17-22149-g002")
			// TODO - When we will accept more docTypes, match it also against other docTypes, not just "pdf".
		}
		else if ( (contentDisposition != null) && !contentDisposition.equals("attachment") ) {	// If the mimeType was not retrieved, then try the "Content Disposition".
				// TODO - When we will accept more docTypes, match it also against other docTypes instead of just "pdf".
			return	contentDisposition.contains("pdf");
		}
		else {	// This is not expected to be reached. Keep it for method-reusability.
			logger.warn("No mimeType, nor Content-Disposition, were able to be retrieved for url: " + urlStr);
			return false;
		}
	}
	
	
	/**
	 * This method receives the mimeType and returns it without the "parentheses" ot the "charset" part.
	 * If there is any error, it returns null.
	 * @param mimeType
	 * @return charset-free mimeType
	 */
	public static String getPlainMimeType(String mimeType)
	{
		if ( mimeType == null ) {	// Null-check to avoid NPE in "matcher()".
			logger.warn("A null mimeType was given to \"getPlainMimeType()\".");
			return null;
		}
		
		String plainMimeType = null;
		Matcher mimeMatcher = MIME_TYPE_FILTER.matcher(mimeType);
		if ( mimeMatcher.matches() ) {
			try {
				plainMimeType = mimeMatcher.group(1);
			} catch (Exception e) { logger.error("", e); }
			if ( (plainMimeType == null) || plainMimeType.isEmpty() ) {
				logger.warn("Unexpected null or empty value returned by \"mimeMatcher.group(1)\" for mimeType: \"" + mimeType + "\".");
				return null;
			}
		} else {
			logger.warn("Unexpected MIME_TYPE_FILTER's (" + mimeMatcher.toString() + ") mismatch for mimeType: \"" + mimeType + "\"");
			return null;
		}
		
		return plainMimeType;
	}
	
	
	/**
	 * This method first checks which "HTTP METHOD" was used to connect to the docUrl.
	 * If this docUrl was connected using "GET" (i.e. when this docURL was fast-found as a possibleDocUrl), just write the data to the disk.
	 * If it was connected using "HEAD", then, before we can store the data to the disk, we connect again, this time with "GET" in order to download the data.
	 * It returns the docFileName which was produced for this docUrl.
	 * @param conn
	 * @param domainStr
	 * @param docUrl
	 * @return
	 * @throws DocFileNotRetrievedException
	 */
	public static String downloadAndStoreDocFile(HttpURLConnection conn, String domainStr, String docUrl)
			throws DocFileNotRetrievedException
	{
		boolean reconnected = false;
		try {
			if ( conn.getRequestMethod().equals("HEAD") ) {    // If the connection happened with "HEAD" we have to re-connect with "GET" to download the docFile
				// No call of "conn.disconnect()" here, as we will connect to the same server.
				conn = HttpConnUtils.openHttpConnection(docUrl, domainStr, false, true);
				reconnected = true;
				int responseCode = conn.getResponseCode();    // It's already checked for -1 case (Invalid HTTP response), inside openHttpConnection().
				// Only a final-url will reach here, so no redirect should occur (thus, we don't check for it).
				if ( (responseCode < 200) || (responseCode >= 400) ) {    // If we have unwanted/error codes.
					String errorMessage = onErrorStatusCode(conn.getURL().toString(), domainStr, responseCode);
					throw new DocFileNotRetrievedException(errorMessage);
				}
			}

			// Check if we should abort the download based on its content-size.
			int contentSize = getContentSize(conn, true);
			if ( contentSize == -1 )
				throw new DocFileNotRetrievedException();

			// Write the downloaded bytes to the docFile and return the docFileName.
			return FileUtils.storeDocFile(conn.getInputStream(), docUrl, conn.getHeaderField("Content-Disposition"));
			
		} catch (DocFileNotRetrievedException dfnre ) {	// Catch it here, otherwise it will be caught as a general exception.
			throw dfnre;	// Avoid creating a new "DocFileNotRetrievedException" if it's already created. By doing this we have a better stack-trace if we decide to log it in the caller-method.
		} catch (Exception e) {
			logger.warn("", e);
			throw new DocFileNotRetrievedException();
		} finally {
			if ( reconnected )	// Otherwise the given-previous connection will be closed by the calling method.
				conn.disconnect();
		}
	}
	
	
	/**
	 * This method receives a pageUrl which gave an HTTP-300-code and extracts an internalLink out of the multiple choices provided.
	 * @param conn
	 * @return
	 */
	public static String getInternalLinkFromHTTP300Page(HttpURLConnection conn)
	{
		try {
			String html = null;
			if ( (html = ConnSupportUtils.getHtmlString(conn, null)) == null ) {
				logger.warn("Could not retrieve the HTML-code for HTTP300PageUrl: " + conn.getURL().toString());
				return null;
			}

			HashSet<String> extractedLinksHashSet = PageCrawler.extractInternalLinksFromHtml(html, conn.getURL().toString());
			if ( extractedLinksHashSet == null || extractedLinksHashSet.size() == 0 )
				return null;	// Logging is handled inside..

			return new ArrayList<>(extractedLinksHashSet).get(0);	// There will be only a couple of urls so it's not a big deal to gather them all.
		} catch (JavaScriptDocLinkFoundException jsdlfe) {
			return jsdlfe.getMessage();	// Return the Javascript link.
		} catch (Exception e) {
			logger.error("", e);
			return null;
		}
	}
	
	
	/**
	 * This method is called on errorStatusCode only. Meaning any status code not belonging in 2XX or 3XX.
	 * @param urlStr
	 * @param domainStr
	 * @param errorStatusCode
	 * @throws DomainBlockedException
	 * @return
	 */
	public static String onErrorStatusCode(String urlStr, String domainStr, int errorStatusCode) throws DomainBlockedException
	{
		if ( (errorStatusCode == 500) && domainStr.contains("handle.net") ) {    // Don't take the 500 of "handle.net", into consideration, it returns many times 500, where it should return 404.. so treat it like a 404.
			//logger.warn("\"handle.com\" returned 500 where it should return 404.. so we will treat it like a 404.");    // See an example: "https://hdl.handle.net/10655/10123".
			errorStatusCode = 404;	// Set it to 404 to be handled as such, if any rule for 404s is to be added later.
		}

		String errorLogMessage;

		if ( (errorStatusCode >= 400) && (errorStatusCode <= 499) ) {	// Client Error.

			errorLogMessage = "Url: \"" + urlStr + "\" seems to be unreachable. Received: HTTP " + errorStatusCode + " Client Error.";
			if ( errorStatusCode == 403 ) {
				if ( domainStr == null ) {
					if ( (domainStr = UrlUtils.getDomainStr(urlStr, null)) != null )
						on403ErrorCode(urlStr, domainStr);	// The "DomainBlockedException" will go up-method by its own, if thrown inside this one.
				} else
					on403ErrorCode(urlStr, domainStr);
			}
		}
		else {	// Other errorCodes. Retrieve the domain and make the required actions.
			domainStr = UrlUtils.getDomainStr(urlStr, null);
			
			if ( (errorStatusCode >= 500) && (errorStatusCode <= 599) ) {	// Server Error.
				errorLogMessage = "Url: \"" + urlStr + "\" seems to be unreachable. Received: HTTP " + errorStatusCode + " Server Error.";
				if ( domainStr != null )
					on5XXerrorCode(domainStr);
			} else {	// Unknown Error (including non-handled: 1XX and the weird one: 999 (used for example on Twitter), responseCodes).
				logger.warn("Url: \"" + urlStr + "\" seems to be unreachable. Received unexpected responseCode: " + errorStatusCode);
				if ( domainStr != null )
					HttpConnUtils.blacklistedDomains.add(domainStr);
				
				throw new DomainBlockedException();	// Throw this even if there was an error preventing the domain from getting blocked.
			}
		}

		return errorLogMessage;
	}
	
	
	/**
	 * This method handles the HTTP 403 Error Code.
	 * When a connection returns 403, we take the path of the url and we block it, as the directory which we are trying to connect to, is forbidden to be accessed.
	 * If a domain ends up having more paths blocked than a certain number, we block the whole domain itself.
	 * @param urlStr
	 * @param domainStr
	 * @throws DomainBlockedException
	 */
	public static void on403ErrorCode(String urlStr, String domainStr) throws DomainBlockedException
	{
		String pathStr = UrlUtils.getPathStr(urlStr, null);
		if ( pathStr == null )
			return;
		
		if ( countAndBlockPathAfterTimes(domainsMultimapWithPaths403BlackListed, timesPathsReturned403, pathStr, domainStr, timesToHave403errorCodeBeforePathBlocked) )
		{
			logger.debug("Path: \"" + pathStr + "\" of domain: \"" + domainStr + "\" was blocked after returning 403 Error Code.");
			
			// Block the whole domain if it has more than a certain number of blocked paths.
			if ( domainsMultimapWithPaths403BlackListed.get(domainStr).size() > numberOf403BlockedPathsBeforeDomainBlocked )
			{
				HttpConnUtils.blacklistedDomains.add(domainStr);	// Block the whole domain itself.
				logger.debug("Domain: \"" + domainStr + "\" was blocked, after having more than " + numberOf403BlockedPathsBeforeDomainBlocked + " of its paths 403blackListed.");
				domainsMultimapWithPaths403BlackListed.removeAll(domainStr);	// No need to keep its paths anymore.
				throw new DomainBlockedException();
			}
		}
	}
	
	
	public static boolean countAndBlockPathAfterTimes(SetMultimap<String, String> domainsWithPaths, HashMap<String, Integer> pathsWithTimes, String pathStr, String domainStr, int timesBeforeBlocked)
	{
		if ( countInsertAndGetTimes(pathsWithTimes, pathStr) > timesBeforeBlocked ) {
			domainsWithPaths.put(domainStr, pathStr);	// Add this path in the list of blocked paths of this domain.
			pathsWithTimes.remove(pathStr);	// No need to keep the count for a blocked path.
			return true;
		}
		else
			return false;
	}
	
	
	/**
	 * This method check if there was ever a url from the given/current domain, which returned an HTTP 403 Error Code.
	 * If there was, it retrieves the directory path of the given/current url and checks if it caused an 403 Error Code before.
	 * It returns "true" if the given/current path is already blocked,
	 * otherwise, if it's not blocked, or if there was a problem retrieving this path from this url, it returns "false".
	 * @param urlStr
	 * @param domainStr
	 * @return boolean
	 */
	public static boolean checkIfPathIs403BlackListed(String urlStr, String domainStr)
	{
		if ( domainsMultimapWithPaths403BlackListed.containsKey(domainStr) )	// If this domain has returned 403 before, then go and check if the current path is blacklisted.
		{
			String pathStr = UrlUtils.getPathStr(urlStr, null);
			if ( pathStr == null )	// If there is a problem retrieving this athStr, return false;
				return false;
			
			return domainsMultimapWithPaths403BlackListed.get(domainStr).contains(pathStr);
		}
		return false;
	}
	
	
	public static void on5XXerrorCode(String domainStr) throws DomainBlockedException
	{
		if ( countAndBlockDomainAfterTimes(HttpConnUtils.blacklistedDomains, timesDomainsReturned5XX, domainStr, timesToHave5XXerrorCodeBeforeDomainBlocked) ) {
			logger.debug("Domain: \"" + domainStr + "\" was blocked after returning 5XX Error Code " + timesToHave5XXerrorCodeBeforeDomainBlocked + " times.");
			throw new DomainBlockedException();
		}
	}
	
	
	public static void onTimeoutException(String domainStr) throws DomainBlockedException
	{
		if ( countAndBlockDomainAfterTimes(HttpConnUtils.blacklistedDomains, timesDomainsHadTimeoutEx, domainStr, timesToHaveTimeoutExBeforeDomainBlocked) ) {
			logger.debug("Domain: \"" + domainStr + "\" was blocked after causing TimeoutException " + timesToHaveTimeoutExBeforeDomainBlocked + " times.");
			throw new DomainBlockedException();
		}
	}
	
	
	/**
	 * This method handles domains which are reaching cases were they can be blocked.
	 * It calculates the times they did something and if they reached a red line, it adds them in the blackList provided by the caller.
	 * After adding it in the blackList, it removes its counters to free-up memory.
	 * It returns "true", if this domain was blocked, otherwise, "false".
	 * @param blackList
	 * @param domainsWithTimes
	 * @param domainStr
	 * @param timesBeforeBlock
	 * @return boolean
	 */
	public static boolean countAndBlockDomainAfterTimes(HashSet<String> blackList, HashMap<String, Integer> domainsWithTimes, String domainStr, int timesBeforeBlock)
	{
		if ( countInsertAndGetTimes(domainsWithTimes, domainStr) > timesBeforeBlock ) {
			blackList.add(domainStr);    // Block this domain.
			domainsWithTimes.remove(domainStr);	// Remove counting-data.
			return true;	// This domain was blocked.
		} else
			return false;	// It wasn't blocked.
	}
	
	
	public static int countInsertAndGetTimes(HashMap<String, Integer> itemWithTimes, String itemToCount)
	{
		int curTimes = 1;
		if ( itemWithTimes.containsKey(itemToCount) )
			curTimes += itemWithTimes.get(itemToCount);
		
		itemWithTimes.put(itemToCount, curTimes);
		
		return curTimes;
	}
	
	
	public static void blockSharedSiteSessionDomain(String forbiddenUrl)
	{
		String urlDomain = null;
		if ( (urlDomain = UrlUtils.getDomainStr(forbiddenUrl, null)) == null )
			return;	// The problem is logged, but nothing more needs to bo done.

		HttpConnUtils.blacklistedDomains.add(urlDomain);
		logger.debug("Domain: \"" + urlDomain + "\" was blocked after trying to cause a \"sharedSiteSession-redirectionPack\" with url: \"" + forbiddenUrl + "\"!");
	}


	public static String getHtmlString(HttpURLConnection conn, BufferedReader bufferedReader)
	{
		int contentSize = getContentSize(conn, false);
		if ( contentSize == -1 ) {
			logger.warn("Aborting HTML-extraction..");
			return null;
		}

		try (BufferedReader br = (bufferedReader != null ? bufferedReader : new BufferedReader(new InputStreamReader(conn.getInputStream()))) )	// Try-with-resources
		{
			String inputLine;
			while ( (inputLine = br.readLine()) != null ) {
				htmlStrB.append(inputLine);
//				if ( !inputLine.isEmpty() )
//					logger.debug(inputLine);	// DEBUG!
			}
			//logger.debug("Chars in html: " + String.valueOf(htmlStrB.length()));	// DEBUG!

			return (htmlStrB.length() != 0) ? htmlStrB.toString() : null;	// Make sure we return a "null" on empty string, to better handle the case in the caller-function.

		} catch ( IOException ioe ) {
			String exceptionMessage = ioe.getMessage();
			if ( exceptionMessage != null )
				logger.error("IOException when retrieving the HTML-code: " + exceptionMessage + "!");
			else
				logger.error("", ioe);
			return null;
		} catch ( Exception e ) {
			logger.error("", e);
			return null;
		}
		finally {
			htmlStrB.setLength(0);	// Reset "StringBuilder" WITHOUT re-allocating.
		}
	}


	/**
	 * This method examines the first line of the Response-body and returns the content-type.
	 * TODO - The only "problem" is that after the "inputStream" closes, it cannot be opened again. So, we cannot parse the HTML afterwards nor download the pdf.
	 * TODO - I guess it's fine to just re-connect but we should search for a way to reset the stream without the overhead of re-connecting.. (keeping the first line and using it later is the solution I use, but only for the html-type, since the pdf-download reads bytes and not lines)
	 * @param conn
	 * @return "html", "pdf", "undefined", null
	 */
	public static DetectedContentType extractContentTypeFromResponseBody(HttpURLConnection conn)
	{
		int contentSize = getContentSize(conn, false);
		if ( contentSize == -1 ) {
			logger.warn("Aborting HTML-extraction..");
			return null;
		}

		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));

			String inputLine;

			// Skip empty lines in the beginning of the HTML-code
			while ( (inputLine = br.readLine()) != null && inputLine.isEmpty())
			{ /* No action inside */ }

			//logger.debug("First line of RequestBody: " + inputLine);	// DEBUG!
			if ( inputLine == null )
				return null;

			String lowercaseInputLine = inputLine.toLowerCase();
			if ( lowercaseInputLine.startsWith("<!doctype html", 0) )
				return new DetectedContentType("html", inputLine, br);
			else {
				br.close();	// We close the stream here, since if we got a pdf we should reconnect in order to get the very first bytes (we don't read "lines" when downloading PDFs).
				if ( lowercaseInputLine.startsWith("%pdf-", 0) )
					return new DetectedContentType("pdf", null, null);	// For PDFs we just going to re-connect in order to download the, since we read plain bytes for them and not String-lines, so we re-connect just to be sure we don't corrupt them.
				else
					return new DetectedContentType("undefined", inputLine, null);
			}

		} catch ( IOException ioe ) {
			String exceptionMessage = ioe.getMessage();
			if ( exceptionMessage != null )
				logger.error("IOException when retrieving the HTML-code: " + exceptionMessage + "!");
			else
				logger.error("", ioe);
			return null;
		} catch ( Exception e ) {
			logger.error("", e);
			return null;
		}
	}

	
	/**
	 * This method returns the ContentSize of the content of an HttpURLConnection.
	 * @param conn
	 * @param calledForFullTextDownload
	 * @return contentSize
	 * @throws NumberFormatException
	 */
	public static int getContentSize(HttpURLConnection conn, boolean calledForFullTextDownload)
	{
		int contentSize = 0;
		try {
			contentSize = Integer.parseInt(conn.getHeaderField("Content-Length"));
			if ( (contentSize <= 0) || (contentSize > HttpConnUtils.maxAllowedContentSize) ) {
				logger.warn((calledForFullTextDownload ? "DocUrl: \"" : "Url: \"") + conn.getURL().toString() + "\" had a non-acceptable contentSize: " + contentSize + ". The maxAllowed one is: " + HttpConnUtils.maxAllowedContentSize);
				return -1;
			}
			//logger.debug("Content-length of \"" + conn.getURL().toString() + "\" is: " + contentSize);	// DEBUG!
			return contentSize;

		} catch (NumberFormatException nfe) {
			if ( calledForFullTextDownload )	// It's not useful otherwise.
				logger.warn("No \"Content-Length\" was retrieved from docUrl: \"" + conn.getURL().toString() + "\"! We will store the docFile anyway..");	// No action is needed.
			return -2;
		} catch ( Exception e ) {
			logger.error("", e);
			return -2;
		}
	}


	public static void closeBufferedReader(BufferedReader bufferedReader)
	{
		try {
			if ( bufferedReader != null )
				bufferedReader.close();
		} catch ( IOException ioe ) {
			logger.warn("Problem when closing \"BufferedReader\": " + ioe.getMessage());
		}
	}

	
	/**
	 * This method constructs fully-formed urls which are connection-ready, as the retrieved links may be relative-links.
	 * @param pageUrl
	 * @param currentLink
	 * @param UrlBase
	 * @return
	 */
	public static String getFullyFormedUrl(String pageUrl, String currentLink, URL UrlBase)
	{
		try {
			if ( UrlBase == null ) {
				if ( pageUrl != null )
					UrlBase = new URL(pageUrl);
				else {
					logger.error("No UrlBase to produce a fully-formedUrl for internal-link: " + currentLink);
					return null;
				}
			}

			return	new URL(UrlBase, currentLink).toString();	// Return the TargetUrl.

		} catch (Exception e) {
			logger.error("Error when producing fully-formedUrl for internal-link: " + currentLink, e.getMessage());
			return null;
		}
	}
	
	
	public static void printEmbeddedExceptionMessage(RuntimeException re, String resourceURL)
	{
		String exMsg = re.getMessage();
		if (exMsg != null) {
			StackTraceElement firstLineOfStackTrace = re.getStackTrace()[0];
			logger.warn("[" + firstLineOfStackTrace.getFileName() + "->" + firstLineOfStackTrace.getMethodName() + "(@" + firstLineOfStackTrace.getLineNumber() + ")] - " + exMsg);
		} else
			logger.warn("Could not handle connection for \"" + resourceURL + "\"!");
	}


	public static void printConnectionDebugInfo(HttpURLConnection conn, boolean shouldShowFullHeaders)
	{
		if ( conn == null ) {
			logger.warn("The given connection instance was null..");
			return;
		}
		logger.debug("Connection debug info:\nURL: < {} >,\nContentType: \"{}\". ContentDisposition: \"{}\", HTTP-method: \"{}\"",
				conn.getURL().toString(), conn.getContentType(), conn.getHeaderField("Content-Disposition"), conn.getRequestMethod());
		if ( shouldShowFullHeaders ) {
			StringBuilder sb = new StringBuilder(300).append("Headers:\n");
			Map<String, List<String>> headers = conn.getHeaderFields();
			for ( String headerKey : headers.keySet() )
				for ( String headerValue : headers.get(headerKey) )
					sb.append(headerKey).append(" : ").append(headerValue).append("\n");
			logger.debug(sb.toString());
		}
	}
	
	
	public static void printRedirectDebugInfo(HttpURLConnection conn, String location, String targetUrl, int curRedirectsNum) throws IOException
	{
		// FOR DEBUG -> Check to see what's happening with the redirect urls (location field types, as well as potential error redirects).
		// Some domains use only the target-ending-path in their location field, while others use full target url.
		
		if ( conn.getURL().toString().contains("doi.org") ) {	// Debug a certain domain or url-path.
			logger.debug("\n");
			logger.debug("Redirect(s) num: " + curRedirectsNum);
			logger.debug("Redirect code: " + conn.getResponseCode());
			logger.debug("Base: " + conn.getURL());
			logger.debug("Location: " + location);
			logger.debug("Target: " + targetUrl + "\n");
		}
	}
	
	/**
	 * This method print redirectStatistics if the initial url matched to the given wantedUrlType.
	 * It's intended to be used for debugging-only.
	 * @param initialUrl
	 * @param finalUrl
	 * @param wantedUrlType
	 * @param redirectsNum
	 */
	public static void printFinalRedirectDataForWantedUrlType(String initialUrl, String finalUrl, String wantedUrlType, int redirectsNum)
	{
		if ( (wantedUrlType != null) && initialUrl.contains(wantedUrlType) ) {
			logger.debug("\"" + initialUrl + "\" DID: " + redirectsNum + " redirect(s)!");
			logger.debug("Final link is: \"" + finalUrl + "\"");
		}
	}
	
}

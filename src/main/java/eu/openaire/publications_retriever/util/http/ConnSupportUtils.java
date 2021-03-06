package eu.openaire.publications_retriever.util.http;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import eu.openaire.publications_retriever.PublicationsRetriever;
import eu.openaire.publications_retriever.crawler.MachineLearning;
import eu.openaire.publications_retriever.crawler.PageCrawler;
import eu.openaire.publications_retriever.exceptions.DocFileNotRetrievedException;
import eu.openaire.publications_retriever.exceptions.DocLinkFoundException;
import eu.openaire.publications_retriever.exceptions.DomainBlockedException;
import eu.openaire.publications_retriever.util.file.FileUtils;
import eu.openaire.publications_retriever.util.url.LoaderAndChecker;
import eu.openaire.publications_retriever.util.url.UrlUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @author Lampros Smyrnaios
 */
public class ConnSupportUtils
{
	private static final Logger logger = LoggerFactory.getLogger(ConnSupportUtils.class);

	public static final Pattern MIME_TYPE_FILTER = Pattern.compile("(?:\\([']?)?([\\w]+/[\\w+\\-.]+).*");

	public static final Pattern POSSIBLE_DOC_OR_DATASET_MIME_TYPE = Pattern.compile("(?:(?:application|binary)/(?:(?:x-)?octet-stream|save|force-download))|unknown");	// We don't take it for granted.. if a match is found, then we check for the "pdf" keyword in the "contentDisposition" (if it exists) or in the url.
	// There are special cases. (see: "https://kb.iu.edu/d/agtj" for "octet" info.
	// and an example for "unknown" : "http://imagebank.osa.org/getExport.xqy?img=OG0kcC5vZS0yMy0xNy0yMjE0OS1nMDAy&xtype=pdf&article=oe-23-17-22149-g002")

	public static final Pattern HTML_STRING_MATCH = Pattern.compile("^(?:[\\s]*<(?:!doctype\\s)?html).*");
	public static final Pattern RESPONSE_BODY_UNWANTED_MATCH = Pattern.compile("^(?:[\\s]+|[\\s]*<(?:\\?xml|!--).*)");	// TODO - Avoid matching to "  <?xml>sddfs<html[...]" (as some times the whole page-code is a single line)

	public static final Pattern SPACE_ONLY_LINE = Pattern.compile("^[\\s]+$");	// For full-HTML-extraction.

	private static final Pattern NON_PROTOCOL_URL = Pattern.compile("^(?:[^:/]+://)(.*)");

	// Note: We cannot remove all the spaces from the HTML, as the JSOUP fails to extract the internal links. If a custom-approach will be followed, then we can take the space-removal into account.
	//public static final Pattern REMOVE_SPACES = Pattern.compile("([\\s]+)");

	public static final int minPolitenessDelay = 3000;	// 3 sec
	public static final int maxPolitenessDelay = 7000;	// 7 sec

	public static final Hashtable<String, Integer> timesDomainsReturned5XX = new Hashtable<String, Integer>();	// Domains that have returned HTTP 5XX Error Code, and the amount of times they did.
	public static final Hashtable<String, Integer> timesDomainsHadTimeoutEx = new Hashtable<String, Integer>();
	public static final Hashtable<String, Integer> timesPathsReturned403 = new Hashtable<String, Integer>();
	
	public static final SetMultimap<String, String> domainsMultimapWithPaths403BlackListed = Multimaps.synchronizedSetMultimap(HashMultimap.create());	// Holds multiple values for any key, if a domain(key) has many different paths (values) for which there was a 403 errorCode.
	
	private static final int timesToHave403errorCodeBeforePathBlocked = 10;	// If a path leads to 403 with different urls, more than 5 times, then this path gets blocked.
	private static final int numberOf403BlockedPathsBeforeDomainBlocked = 50;	// If a domain has more than 5 different 403-blocked paths, then the whole domain gets blocked.

	private static final int timesToHave5XXerrorCodeBeforeDomainBlocked = 10;
	private static final int timesToHaveTimeoutExBeforeDomainBlocked = 25;

	private static final int timesToReturnNoTypeBeforeDomainBlocked = 10;
	public static AtomicInteger reCrossedDocUrls = new AtomicInteger(0);

	public static final Set<String> knownDocMimeTypes = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
	public static final Set<String> knownDatasetMimeTypes = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());


	public static void setKnownMimeTypes()
	{
		if ( LoaderAndChecker.retrieveDocuments ) {
			setKnownDocMimeTypes();
			if ( LoaderAndChecker.retrieveDatasets )
				setKnownDatasetMimeTypes();
		} else
			setKnownDatasetMimeTypes();
	}


	public static void setKnownDocMimeTypes()
	{
		logger.debug("Setting up the official document mime types. Currently there is support only for pdf documents.");
		knownDocMimeTypes.add("application/pdf");
		knownDocMimeTypes.add("application/x-pdf");
		knownDocMimeTypes.add("image/pdf");
	}


	public static void setKnownDatasetMimeTypes()
	{
		logger.debug("Setting up the official dataset mime types. Currently there is support for xls, xlsx, csv, tsv, tab, json, geojson, xml, ods, rdf, zip, gzip, rar, tar, 7z, tgz, gz[\\d]*, bz[\\d]*, xz, smi, por, ascii, dta, sav, dat, txt, ti[f]+, twf, svg, sas7bdat, spss, sas, stata, sql, mysql, postgresql, sqlite, bigquery, shp, shx, prj, sbx, sbn, dbf, mdb, accdb, dwg, mat, pcd, bt, n[sc]?[\\d]*, h4, h5, hdf, hdf4, hdf5, trs, opj, fcs, fas, fasta, values datasets.");
		knownDatasetMimeTypes.add("application/vnd.ms-excel");
		knownDatasetMimeTypes.add("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		knownDatasetMimeTypes.add("text/csv");
		knownDatasetMimeTypes.add("text/tab-separated-values");
		knownDatasetMimeTypes.add("application/json");
		knownDatasetMimeTypes.add("application/xml");	// There is also the "text/xml", but that is not a binary-dataset-file.
		knownDatasetMimeTypes.add("application/rdf+xml");
		knownDatasetMimeTypes.add("application/smil+xml");	// .smi
		knownDatasetMimeTypes.add("application/smil");	// .smi
		knownDatasetMimeTypes.add("text/rdf+n3");
		knownDatasetMimeTypes.add("text/plain");	// Some csv and txt datasets
		knownDatasetMimeTypes.add("application/zip");
		knownDatasetMimeTypes.add("application/gzip");
		knownDatasetMimeTypes.add("application/rar");
		knownDatasetMimeTypes.add("application/vnd.rar");
		knownDatasetMimeTypes.add("application/x-tar");
		knownDatasetMimeTypes.add("application/x-7z-compressed");
		knownDatasetMimeTypes.add("application/x-sas-data");	// ".sas7bdat" file
		knownDatasetMimeTypes.add("application/x-netcdf");	// nc3, nc4, ns
		knownDatasetMimeTypes.add("application/x-sql");
		knownDatasetMimeTypes.add("image/tiff");
	}

	
	/**
	 * This method takes a url and its mimeType and checks if it's a document mimeType or not.
	 * @param urlStr
	 * @param mimeType in lowercase
	 * @param contentDisposition
	 * @param calledForPageUrl
	 * @param calledForPossibleDocOrDatasetUrl)
	 * @return boolean
	 */
	public static String hasDocOrDatasetMimeType(String urlStr, String mimeType, String contentDisposition, HttpURLConnection conn, boolean calledForPageUrl, boolean calledForPossibleDocOrDatasetUrl)
	{
		String typeToReturn = null;
		String lowerCaseUrl = null;

		if ( mimeType != null )
		{	// The "mimeType" here is in lowercase
			if ( mimeType.contains("system.io.fileinfo") ) {	// Check this out: "http://www.esocialsciences.org/Download/repecDownload.aspx?fname=Document110112009530.6423303.pdf&fcategory=Articles&AId=2279&fref=repec", ιt has: "System.IO.FileInfo".
				// In this case, we want first to try the "Content-Disposition", as it's more trustworthy. If that's not available, use the urlStr as the last resort.
				if ( conn != null )	// Just to be sure we avoid an NPE.
					contentDisposition = conn.getHeaderField("Content-Disposition");
				// The "contentDisposition" will be definitely "null", since "mimeType != null" and so, the "contentDisposition" will not have been retrieved by the caller method.
				
				if ( (contentDisposition != null) && !contentDisposition.equals("attachment") )
					typeToReturn = contentDisposition.toLowerCase().contains("pdf") ? "document" : null;	// TODO - add more types as needed. Check: "http://www.esocialsciences.org/Download/repecDownload.aspx?qs=Uqn/rN48N8UOPcbSXUd2VFI+dpOD3MDPRfIL8B3DH+6L18eo/yEvpYEkgi9upp2t8kGzrjsWQHUl44vSn/l7Uc1SILR5pVtxv8VYECXSc8pKLF6QJn6MioA5dafPj/8GshHBvLyCex2df4aviMvImCZpwMHvKoPiO+4B7yHRb97u1IHg45E+Z6ai0Z/0vacWHoCsNT9O4FNZKMsSzen2Cw=="
				else
					typeToReturn = urlStr.toLowerCase().contains("pdf") ? "document" : null;

				return typeToReturn;
			}

			String plainMimeType = mimeType;	// Make sure we don't cause any NPE later on..
			if ( mimeType.contains("charset") || mimeType.contains("name")
					|| mimeType.startsWith("(", 0) )	// See: "https://www.mamsie.bbk.ac.uk/articles/10.16995/sim.138/galley/134/download/" -> "Content-Type: ('application/pdf', none)"
			{
				plainMimeType = getPlainMimeType(mimeType);
				if ( plainMimeType == null ) {    // If there was any error removing the charset, still try to determine the data-type.
					logger.warn("Url with problematic mimeType (" + mimeType + ") was: " + urlStr);
					lowerCaseUrl = urlStr.toLowerCase();
					if ( lowerCaseUrl.contains("pdf") )
						typeToReturn = "document";
					else if ( LoaderAndChecker.DATASET_URL_FILTER.matcher(lowerCaseUrl).matches() )
						typeToReturn = "dataset";

					return typeToReturn;	// Default is "null".
				}
			}

			// Cleanup the mimeType further, e.g.: < application/pdf' > (with the < ' > in the end): http://www.ccsenet.org/journal/index.php/ijb/article/download/48805/26704
			plainMimeType = StringUtils.replace(plainMimeType, "'", "", -1);
			plainMimeType = StringUtils.replace(plainMimeType, "\"", "", -1);

			if ( knownDocMimeTypes.contains(plainMimeType) )
				typeToReturn = "document";
			else if ( knownDatasetMimeTypes.contains(plainMimeType) )
				typeToReturn = "dataset";
			else if ( POSSIBLE_DOC_OR_DATASET_MIME_TYPE.matcher(plainMimeType).matches() )
			{
				contentDisposition = conn.getHeaderField("Content-Disposition");
				if ( (contentDisposition != null) && !contentDisposition.equals("attachment") )	// It may be "attachment" but also be a pdf.. but we have to check if the "pdf" exists inside the url-string.
				{
					String lowerCaseContentDisposition = contentDisposition.toLowerCase();
					if ( lowerCaseContentDisposition.contains("pdf") )
						typeToReturn = "document";
					else {
						String clearContentDisposition = StringUtils.replace(lowerCaseContentDisposition, "\"", "", -1);
						if ( LoaderAndChecker.DATASET_URL_FILTER.matcher(clearContentDisposition).matches() )
							typeToReturn = "dataset";
					}
				}
				else {
					lowerCaseUrl = urlStr.toLowerCase();
					if ( lowerCaseUrl.contains("pdf") )
						typeToReturn = "document";
					else if ( LoaderAndChecker.DATASET_URL_FILTER.matcher(lowerCaseUrl).matches() )
						typeToReturn = "dataset";
				}
			}	// TODO - When we will accept more docTypes, match it also against other docTypes, not just "pdf".
			return typeToReturn;	// Default is "null".
		}
		else if ( (contentDisposition != null) && !contentDisposition.equals("attachment") ) {	// If the mimeType was not retrieved, then try the "Content Disposition".
			// TODO - When we will accept more docTypes, match it also against other docTypes instead of just "pdf".
			String lowerCaseContentDisposition = contentDisposition.toLowerCase();
			if ( lowerCaseContentDisposition.contains("pdf") )
				typeToReturn = "document";
			else {
				String clearContentDisposition = StringUtils.replace(lowerCaseContentDisposition, "\"", "", -1);
				if ( LoaderAndChecker.DATASET_URL_FILTER.matcher(clearContentDisposition).matches() )
					typeToReturn = "dataset";
			}
			return typeToReturn;	// Default is "null".
		}
		else {	// This is not expected to be reached. Keep it for method-reusability.
			if ( calledForPageUrl || calledForPossibleDocOrDatasetUrl )
				logger.warn("No mimeType, nor Content-Disposition, were able to be retrieved for url: " + urlStr);
			return null;
		}
	}


	public static void handleReCrossedDocUrl(String urlId, String sourceUrl, String pageUrl, String docUrl, Logger logger, boolean calledForPageUrl) {
		logger.info("re-crossed docUrl found: < " + docUrl + " >");
		reCrossedDocUrls.incrementAndGet();
		String wasDirectLink = ConnSupportUtils.getWasDirectLink(sourceUrl, pageUrl, calledForPageUrl, docUrl);
		if ( FileUtils.shouldDownloadDocFiles )
			UrlUtils.logOutputData(urlId, sourceUrl, pageUrl, docUrl, UrlUtils.alreadyDownloadedByIDMessage + UrlUtils.docOrDatasetUrlsWithIDs.get(docUrl), null, false, "true", "true", "true", wasDirectLink);
		else
			UrlUtils.logOutputData(urlId, sourceUrl, pageUrl, docUrl, "", null, false, "true", "true", "true", wasDirectLink);
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
			} catch (Exception e) { logger.error("", e); return null; }
			if ( (plainMimeType == null) || plainMimeType.isEmpty() ) {
				logger.warn("Unexpected null or empty value returned by \"mimeMatcher.group(1)\" for mimeType: \"" + mimeType + "\".");
				return null;
			}
		} else {
			logger.warn("Unexpected MIME_TYPE_FILTER's (" + mimeMatcher + ") mismatch for mimeType: \"" + mimeType + "\"");
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
	 * @param calledForPageUrl
	 * @return
	 * @throws DocFileNotRetrievedException
	 */
	public static String downloadAndStoreDocFile(HttpURLConnection conn, String domainStr, String docUrl, boolean calledForPageUrl)
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
					String errorMessage = onErrorStatusCode(conn.getURL().toString(), domainStr, responseCode, calledForPageUrl);
					throw new DocFileNotRetrievedException(errorMessage);
				}
			}

			// Check if we should abort the download based on its content-size.
			int contentSize = getContentSize(conn, true);
			if ( contentSize == -1 )	// "Unacceptable size"-code..
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


	public static final Hashtable<String, DomainConnectionData> domainsWithLocks = new Hashtable<>();

	/**
	 * This method receives the domain and manages the sleep-time, if needed.
	 * It first extracts the last 3 parts of the domain. Then it checks if the domain is faced for the first time.
	 * If it is the first time, then the domain is added in the Hashtable along with a new DomainConnectionData.
	 * Else the thread will lock that domain and check if it was connected before at most "minPolitenessDelay" secs, if so, then the thread will sleep for a random number of milliseconds.
	 * Different threads lock on different domains, so each thread is not dependent on another thread which works on a different domain.
	 * @param domainStr
	 */
	public static void applyPolitenessDelay(String domainStr)
	{
		// Consider only the last three parts of a domain, not all, otherwise, a sub-sub-domain might connect simultaneously with a another sub-sub-domain.
		domainStr = UrlUtils.getTopThreeLevelDomain(domainStr);

		DomainConnectionData domainConnectionData = domainsWithLocks.get(domainStr);
		if ( domainConnectionData == null ) {	// If it is the 1st time connecting.
			domainsWithLocks.put(domainStr, new DomainConnectionData());
			return;
		}

		domainConnectionData.lock.lock();// Threads trying to connect with the same domain, should sleep one AFTER the other, to avoid coming back after sleep at the same time, in the end..
		long elapsedTimeMillis;
		Instant currentTime = Instant.now();
		try {
			elapsedTimeMillis = Duration.between(domainConnectionData.lastTimeConnected, currentTime).toMillis();
		} catch (Exception e) {
			logger.warn("An exception was thrown when tried to obtain the time elapsed from the last time the domain connected: " + e.getMessage());
			domainConnectionData.updateAndUnlock(currentTime);
			return;
		}

		if ( elapsedTimeMillis < minPolitenessDelay ) {
			long randomPolitenessDelay = getRandomNumber(minPolitenessDelay, maxPolitenessDelay);
			long finalPolitenessDelay = randomPolitenessDelay - elapsedTimeMillis;
			//logger.debug("WILL SLEEP for " + finalPolitenessDelay + " | randomNumber was " + randomPolitenessDelay + ", elapsedTime was: " + elapsedTimeMillis + " | domain: " + domainStr);	// DEBUG!
			try {
				Thread.sleep(finalPolitenessDelay);    // Avoid server-overloading for the same domain.
			} catch (InterruptedException ie) {
				Instant newCurrentTime = Instant.now();
				try {
					elapsedTimeMillis = Duration.between(currentTime, newCurrentTime).toMillis();
				} catch (Exception e) {
					logger.warn("An exception was thrown when tried to obtain the time elapsed from the last time the \"currentTime\" was updated: " + e.getMessage());
					domainConnectionData.updateAndUnlock(newCurrentTime);
					return;
				}
				if ( elapsedTimeMillis < minPolitenessDelay ) {
					finalPolitenessDelay -= elapsedTimeMillis;
					try {
						Thread.sleep(finalPolitenessDelay);
					} catch (InterruptedException ignored) {
					}
				}
			}	// At this point, if both sleeps failed, some time has already passed, so it's ok to connect to the same domain.
			currentTime = Instant.now();	// Update, after the sleep.
		} //else
			//logger.debug("NO SLEEP NEEDED, elapsedTime: " + elapsedTimeMillis + " > " + minPolitenessDelay + " | domain: " + domainStr);	// DEBUG!

		domainConnectionData.updateAndUnlock(currentTime);
	}


	/**
	 * This method does an offline-redirect to HTTPS. It is called when the url uses HTTP, but handles exceptions as well.
	 * @param url
	 * @return
	 */
	public static String offlineRedirectToHTTPS(String url)
	{
		return StringUtils.replace(url, "http:", "https:", 1);
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
		} catch ( DocLinkFoundException dlfe) {
			return dlfe.getMessage();	// Return the DocLink to connect with.
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
	 * @param calledForPageUrl
	 * @throws DomainBlockedException
	 * @return
	 */
	public static String onErrorStatusCode(String urlStr, String domainStr, int errorStatusCode, boolean calledForPageUrl) throws DomainBlockedException
	{
		if ( (errorStatusCode == 500) && domainStr.contains("handle.net") ) {    // Don't take the 500 of "handle.net", into consideration, it returns many times 500, where it should return 404.. so treat it like a 404.
			//logger.warn("\"handle.com\" returned 500 where it should return 404.. so we will treat it like a 404.");    // See an example: "https://hdl.handle.net/10655/10123".
			errorStatusCode = 404;	// Set it to 404 to be handled as such, if any rule for 404s is to be added later.
		}

		String errorLogMessage;

		if ( (errorStatusCode >= 400) && (errorStatusCode <= 499) )	// Client Error.
		{
			errorLogMessage = "Url: \"" + urlStr + "\" seems to be unreachable. Received: HTTP " + errorStatusCode + " Client Error.";
			if ( errorStatusCode == 403 ) {
				if ( (domainStr == null) || !urlStr.contains(domainStr) )	// The domain might have changed after redirections.
					domainStr = UrlUtils.getDomainStr(urlStr, null);

				if ( domainStr != null )	// It may be null if "UrlUtils.getDomainStr()" failed.
					on403ErrorCode(urlStr, domainStr, calledForPageUrl);	// The "DomainBlockedException" will go up-method by its own, if thrown inside this one.
			}
		}
		else {	// Other errorCodes. Retrieve the domain and make the required actions.
			if ( (domainStr == null) || !urlStr.contains(domainStr) )	// The domain might have changed after redirections.
				domainStr = UrlUtils.getDomainStr(urlStr, null);

			if ( (errorStatusCode >= 500) && (errorStatusCode <= 599) ) {	// Server Error.
				errorLogMessage = "Url: \"" + urlStr + "\" seems to be unreachable. Received: HTTP " + errorStatusCode + " Server Error.";
				if ( domainStr != null )
					on5XXerrorCode(domainStr);
			} else {	// Unknown Error (including non-handled: 1XX and the weird one: 999 (used for example on Twitter), responseCodes).
				logger.warn("Url: \"" + urlStr + "\" seems to be unreachable. Received unexpected responseCode: " + errorStatusCode);
				if ( domainStr != null ) {
					HttpConnUtils.blacklistedDomains.add(domainStr);
					logger.debug("Domain: \"" + domainStr + "\" was blocked, after giving a " + errorStatusCode + " HTTP-status-code.");
				}
				
				throw new DomainBlockedException(domainStr);	// Throw this even if there was an error preventing the domain from getting blocked.
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
	 * @param calledForPageUrl
	 * @throws DomainBlockedException
	 */
	public static void on403ErrorCode(String urlStr, String domainStr, boolean calledForPageUrl) throws DomainBlockedException
	{
		String pathStr = UrlUtils.getPathStr(urlStr, null);
		if ( pathStr == null )
			return;
		
		if ( countAndBlockPathAfterTimes(domainsMultimapWithPaths403BlackListed, timesPathsReturned403, pathStr, domainStr, timesToHave403errorCodeBeforePathBlocked, calledForPageUrl) )
		{
			logger.debug("Path: \"" + pathStr + "\" of domain: \"" + domainStr + "\" was blocked after returning 403 Error Code more than " + timesToHave403errorCodeBeforePathBlocked + " times.");
			// Block the whole domain if it has more than a certain number of blocked paths.
			if ( domainsMultimapWithPaths403BlackListed.get(domainStr).size() > numberOf403BlockedPathsBeforeDomainBlocked )
			{
				HttpConnUtils.blacklistedDomains.add(domainStr);	// Block the whole domain itself.
				logger.debug("Domain: \"" + domainStr + "\" was blocked, after having more than " + numberOf403BlockedPathsBeforeDomainBlocked + " of its paths 403blackListed.");
				domainsMultimapWithPaths403BlackListed.removeAll(domainStr);	// No need to keep its paths anymore.
				throw new DomainBlockedException(domainStr);
			}
		}
	}
	
	
	public static boolean countAndBlockPathAfterTimes(SetMultimap<String, String> domainsWithPaths, Hashtable<String, Integer> pathsWithTimes, String pathStr, String domainStr, int timesBeforeBlocked, boolean calledForPageUrl)
	{
		if ( countInsertAndGetTimes(pathsWithTimes, pathStr) > timesBeforeBlocked ) {

			// If we use MLA, we are storing the docPage-successful-paths, so check if this is one of them, if it is then don't block it.
			// If it's an internal-link, then.. we can't iterate over every docUrl-successful-path of every docPage-successful-path.. it's too expensive O(5*n), not O(1)..
			if ( calledForPageUrl && MachineLearning.useMLA && MachineLearning.successPathsHashMultiMap.containsKey(pathStr) )
				return false;

			domainsWithPaths.put(domainStr, pathStr);	// Add this path in the list of blocked paths of this domain.
			pathsWithTimes.remove(pathStr);	// No need to keep the count for a blocked path.
			return true;
		}
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
		if ( countAndBlockDomainAfterTimes(HttpConnUtils.blacklistedDomains, timesDomainsReturned5XX, domainStr, timesToHave5XXerrorCodeBeforeDomainBlocked, true) ) {
			logger.debug("Domain: \"" + domainStr + "\" was blocked after returning 5XX Error Code " + timesToHave5XXerrorCodeBeforeDomainBlocked + " times.");
			throw new DomainBlockedException(domainStr);
		}
	}
	
	
	public static void onTimeoutException(String domainStr) throws DomainBlockedException
	{
		if ( countAndBlockDomainAfterTimes(HttpConnUtils.blacklistedDomains, timesDomainsHadTimeoutEx, domainStr, timesToHaveTimeoutExBeforeDomainBlocked, true) ) {
			logger.debug("Domain: \"" + domainStr + "\" was blocked after causing TimeoutException " + timesToHaveTimeoutExBeforeDomainBlocked + " times.");
			throw new DomainBlockedException(domainStr);
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
	 * @param checkAgainstDocUrlsHits
	 * @return boolean
	 */
	public static boolean countAndBlockDomainAfterTimes(Set<String> blackList, Hashtable<String, Integer> domainsWithTimes, String domainStr, int timesBeforeBlock, boolean checkAgainstDocUrlsHits)
	{
		int badTimes = countInsertAndGetTimes(domainsWithTimes, domainStr);

		if ( badTimes > timesBeforeBlock )
		{
			if ( checkAgainstDocUrlsHits ) {	// This will not be the case for MLA-blocked-domains.
				Integer goodTimes = UrlUtils.domainsAndHits.get(domainStr);
				if ( (goodTimes != null) && (goodTimes >= badTimes) )
					return false;
			}

			blackList.add(domainStr);    // Block this domain.
			domainsWithTimes.remove(domainStr);	// Remove counting-data.
			return true;	// This domain was blocked.
		}
		return false;	// It wasn't blocked.
	}
	
	
	public static int countInsertAndGetTimes(Hashtable<String, Integer> itemWithTimes, String itemToCount)
	{
		int curTimes = 1;
		if ( itemWithTimes.containsKey(itemToCount) )
			curTimes += itemWithTimes.get(itemToCount);
		
		itemWithTimes.put(itemToCount, curTimes);
		
		return curTimes;
	}


	/**
	 * This method blocks the domain of the targetLink which tried to cause the "sharedSiteSession-redirectionPack".
	 * It also blocks the domain of the url which led to this redirection (if applicable and only for the right-previous url)
	 * @param targetUrl
	 * @param previousFromTargetUrl
	 * @return
	 */
	public static List<String> blockSharedSiteSessionDomains(String targetUrl, String previousFromTargetUrl)
	{
		List<String> blockedDomainsToReturn = new ArrayList<>(2);
		String targetUrlDomain, beforeTargetUrlDomain;

		if ( (targetUrlDomain = UrlUtils.getDomainStr(targetUrl, null)) == null )
			return null;	// The problem is logged, but nothing more needs to bo done.

		blockedDomainsToReturn.add(targetUrlDomain);
		if ( HttpConnUtils.blacklistedDomains.add(targetUrlDomain) )	// If it was added for the first time.
			logger.debug("Domain: \"" + targetUrlDomain + "\" was blocked after trying to cause a \"sharedSiteSession-redirectionPack\" with url: \"" + targetUrl + "\"!");

		if ( (previousFromTargetUrl != null) && !previousFromTargetUrl.equals(targetUrl) ) {
			if ( (beforeTargetUrlDomain = UrlUtils.getDomainStr(previousFromTargetUrl, null)) != null ) {
				blockedDomainsToReturn.add(beforeTargetUrlDomain);
				if ( HttpConnUtils.blacklistedDomains.add(beforeTargetUrlDomain) )    // If it was added for the first time.
					logger.debug("Domain: \"" + beforeTargetUrlDomain + "\" was blocked after its url : \"" + previousFromTargetUrl + "\" tried to redirect to targetUrl: \"" + targetUrl + "\" and cause a \"sharedSiteSession-redirectionPack\"!");
			}
		}

		return blockedDomainsToReturn;
	}


	public static String getHtmlString(HttpURLConnection conn, BufferedReader bufferedReader)
	{
		int contentSize = getContentSize(conn, false);
		if ( contentSize == -1 ) {	// "Unacceptable size"-code..
			logger.warn("Aborting HTML-extraction for pageUrl: " + conn.getURL().toString());
			return null;
		}

		StringBuilder htmlStrB = new StringBuilder(300000);	// Initialize it here each time for thread-safety (thread-locality).

		try (BufferedReader br = (bufferedReader != null ? bufferedReader : new BufferedReader(new InputStreamReader(conn.getInputStream()))) )	// Try-with-resources
		{
			String inputLine;
			while ( (inputLine = br.readLine()) != null )
			{
				if ( !inputLine.isEmpty() && (inputLine.length() != 1) && !SPACE_ONLY_LINE.matcher(inputLine).matches() ) {	// We check for (inputLine.length() != 1), as some lines contain an unrecognized byte.
					htmlStrB.append(inputLine);
					//logger.debug(inputLine);	// DEBUG!
				}
			}
			//logger.debug("Chars in html: " + String.valueOf(htmlStrB.length()));	// DEBUG!

			return (htmlStrB.length() != 0) ? htmlStrB.toString() : null;	// Make sure we return a "null" on empty string, to better handle the case in the caller-function.

		} catch ( IOException ioe ) {
			logger.error("IOException when retrieving the HTML-code: " + ioe.getMessage());
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
	 *
	 * @param finalUrlStr
	 * @param domainStr
	 * @param conn
	 * @param calledForPageUrl
	 * @return
	 * @throws DomainBlockedException
	 * @throws RuntimeException
	 */
	public static ArrayList<Object> detectContentTypeFromResponseBody(String finalUrlStr, String domainStr, HttpURLConnection conn, boolean calledForPageUrl)
			throws DomainBlockedException, RuntimeException
	{
		String warnMsg = "No ContentType nor ContentDisposition, were able to be retrieved from url: " + finalUrlStr;
		String mimeType = null;
		boolean foundDetectedContentType = false;
		String firstHtmlLine = null;
		BufferedReader bufferedReader = null;
		boolean calledForPossibleDocUrl = false;
		boolean wasConnectedWithHTTPGET = conn.getRequestMethod().equals("GET");

		// Try to detect the content type.
		if ( wasConnectedWithHTTPGET ) {
			DetectedContentType detectedContentType = ConnSupportUtils.extractContentTypeFromResponseBody(conn);
			if ( detectedContentType != null ) {
				if ( calledForPageUrl && detectedContentType.detectedContentType.equals("html") ) {
					logger.debug("The url with the undeclared content type < " + finalUrlStr + " >, was examined and found to have HTML contentType! Going to visit the page.");
					mimeType = "text/html";
					foundDetectedContentType = true;
					firstHtmlLine = detectedContentType.firstHtmlLine;
					bufferedReader = detectedContentType.bufferedReader;
				} else if ( detectedContentType.detectedContentType.equals("pdf") ) {
					logger.debug("The url with the undeclared content type < " + finalUrlStr + " >, was examined and found to have PDF contentType!");
					mimeType = "application/pdf";
					calledForPossibleDocUrl = true;	// Important for the re-connection.
					foundDetectedContentType = true;
				} else if ( detectedContentType.detectedContentType.equals("undefined") )
					logger.debug("The url with the undeclared content type < " + finalUrlStr + " >, was examined and found to have UNDEFINED contentType.");
				else
					warnMsg += "\nUnspecified \"detectedContentType\":" + detectedContentType.detectedContentType;
			}
			else	//  ( detectedContentType == null )
				warnMsg += "\nCould not retrieve the response-body for url: " + finalUrlStr;
		}
		else	// ( connection-method == "HEAD" )
			warnMsg += "\nThe initial connection was made with the \"HTTP-HEAD\" method, so there is no response-body to use to detect the content-type.";

		if ( !foundDetectedContentType && wasConnectedWithHTTPGET ) {	// If it could be detected but was not, only then go and check if it should be blocked.
			if ( ConnSupportUtils.countAndBlockDomainAfterTimes(HttpConnUtils.blacklistedDomains, HttpConnUtils.timesDomainsReturnedNoType, domainStr, timesToReturnNoTypeBeforeDomainBlocked, true) ) {
				logger.warn(warnMsg);
				logger.warn("Domain: \"" + domainStr + "\" was blocked after returning no Type-info more than " + timesToReturnNoTypeBeforeDomainBlocked + " times.");
				throw new DomainBlockedException(domainStr);
			} else
				throw new RuntimeException(warnMsg);	// We can't retrieve any clue. This is not desired. The "warnMsg" will be printed by the caller method.
		}

		ArrayList<Object> detectionList = new ArrayList<>(5);
		detectionList.add(0, mimeType);
		detectionList.add(1, foundDetectedContentType);
		detectionList.add(2, firstHtmlLine);
		detectionList.add(3, bufferedReader);
		detectionList.add(4, calledForPossibleDocUrl);
		return detectionList;
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
		if ( contentSize == -1 ) {	// "Unacceptable size"-code..
			logger.warn("Aborting HTML-extraction for pageUrl: " + conn.getURL().toString());
			return null;
		}

		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String inputLine;

			// Skip empty lines in the beginning of the HTML-code
			while ( ((inputLine = br.readLine()) != null) && (inputLine.isEmpty() || (inputLine.length() == 1) || RESPONSE_BODY_UNWANTED_MATCH.matcher(inputLine).matches()) )	// https://repositorio.uam.es/handle/10486/687988
			{	/* No action inside */	}	// https://bv.fapesp.br/pt/publicacao/96198/homogeneous-gaussian-profile-p-type-emitters-updated-param/		http://naosite.lb.nagasaki-u.ac.jp/dspace/handle/10069/29792

			// For DEBUGing..
			/*while ( ((inputLine = br.readLine()) != null) && !(inputLine.isEmpty() || inputLine.length() == 1 || RESPONSE_BODY_UNWANTED_MATCH.matcher(inputLine).matches()) )
			{ logger.debug(inputLine + "\nLength of line: " + inputLine.length());
				logger.debug(Arrays.toString(inputLine.chars().toArray()));
			}*/

			//logger.debug("First line of RequestBody: " + inputLine);	// DEBUG!
			if ( inputLine == null )
				return null;

			String lowerCaseInputLine = inputLine.toLowerCase();
			//logger.debug(lowerCaseInputLine + "\nLength of line: "  + lowerCaseInputLine.length());	// DEBUG!
			if ( HTML_STRING_MATCH.matcher(lowerCaseInputLine).matches() )
				return new DetectedContentType("html", inputLine, br);
			else {
				br.close();	// We close the stream here, since if we got a pdf we should reconnect in order to get the very first bytes (we don't read "lines" when downloading PDFs).
				if ( lowerCaseInputLine.startsWith("%pdf-", 0) )
					return new DetectedContentType("pdf", null, null);	// For PDFs we just going to re-connect in order to download the, since we read plain bytes for them and not String-lines, so we re-connect just to be sure we don't corrupt them.
				else
					return new DetectedContentType("undefined", inputLine, null);
			}
		} catch ( IOException ioe ) {
			logger.error("IOException when retrieving the HTML-code: " + ioe.getMessage());
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
			if ( calledForFullTextDownload )	// It's not useful to show a logging-message otherwise.
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


	public static boolean isJustAnHTTPSredirect(String currentUrl, String targetUrl)
	{
		// First check if we go from an http to an https in general.
		if ( !currentUrl.startsWith("http://", 0) || !targetUrl.startsWith("https", 0) )
			return false;

		// Take the url after the protocol and check if it's the same, if it is then we have our HTTPS redirect, if not then it's another type of redirect.
		return haveOnlyProtocolDifference(currentUrl, targetUrl);
	}


	/**
	 * This method returns "true" if the two urls have only a protocol-difference.
	 * @param url1
	 * @param url2
	 * @return
	 */
	public static boolean haveOnlyProtocolDifference(String url1, String url2)
	{
		Matcher url1NonProtocolMatcher = NON_PROTOCOL_URL.matcher(url1);
		if ( !url1NonProtocolMatcher.matches() ) {
			logger.warn("URL < " + url1 + " > failed to match with \"NON_PROTOCOL_URL\"-regex: " + NON_PROTOCOL_URL);
			return false;
		}

		String non_protocol_url1;
		try {
			non_protocol_url1 = url1NonProtocolMatcher.group(1);
		} catch (Exception e) { logger.error("No match for url1: " + url1, e); return false; }
		if ( (non_protocol_url1 == null) || non_protocol_url1.isEmpty() ) {
			logger.warn("Unexpected null or empty value returned by \"url1NonProtocolMatcher.group(1)\" for url: \"" + url1 + "\"");
			return false;
		}

		Matcher url2UrlNonProtocolMatcher = NON_PROTOCOL_URL.matcher(url2);
		if ( !url2UrlNonProtocolMatcher.matches() ) {
			logger.warn("URL < " + url2 + " > failed to match with \"NON_PROTOCOL_URL\"-regex: " + NON_PROTOCOL_URL);
			return false;
		}

		String non_protocol_url2Url;
		try {
			non_protocol_url2Url = url2UrlNonProtocolMatcher.group(1);
		} catch (Exception e) { logger.error("No match for url2: " + url2, e); return false; }
		if ( (non_protocol_url2Url == null) || non_protocol_url2Url.isEmpty() ) {
			logger.warn("Unexpected null or empty value returned by \"url2UrlNonProtocolMatcher.group(1)\" for url: \"" + url2 + "\"");
			return false;
		}

		return ( non_protocol_url1.equals(non_protocol_url2Url) );
	}


	public static InputStream getInputStreamFromInputDataUrl()
	{
		InputStream inputStream = null;
		if ( (PublicationsRetriever.inputDataUrl == null) || PublicationsRetriever.inputDataUrl.isEmpty() ) {
			String errorMessage = "The \"inputDataUrl\" was not given, even though";
			logger.error(errorMessage);
			System.err.println(errorMessage);
			PublicationsRetriever.executor.shutdownNow();
			System.exit(55);
		}

		try {
			HttpURLConnection conn = HttpConnUtils.handleConnection(null, PublicationsRetriever.inputDataUrl, PublicationsRetriever.inputDataUrl, PublicationsRetriever.inputDataUrl, null, true, true);
			String mimeType = conn.getHeaderField("Content-Type");
			if ( (mimeType == null) || !mimeType.toLowerCase().contains("json") ) {
				String errorMessage = "The mimeType of the url was either null or a non-json: " + mimeType;
				logger.error(errorMessage);
				System.err.println(errorMessage);
				PublicationsRetriever.executor.shutdownNow();
				System.exit(56);
			}

			inputStream = conn.getInputStream();

		} catch (Exception e) {
			String errorMessage = "Unexpected error when retrieving the input-stream from the inputDataUrl:\n" + e.getMessage();
			logger.error(errorMessage);
			System.err.println(errorMessage);
			PublicationsRetriever.executor.shutdownNow();
			System.exit(57);
		}

		// If the user gave both the inputUrl and the inputFile, then make sure we close the SYS-IN stream.
		try {
			System.in.close();
		} catch (Exception ignored) { }

		return inputStream;
	}


	private static final ThreadLocalRandom threadLocalRandom = ThreadLocalRandom.current();

	public static long getRandomNumber(int min, int max) {
		return threadLocalRandom.nextLong(min, max+1);	// It's (max+1) because the max upper bound is exclusive.
	}


	public static String getWasDirectLink(String sourceUrl, String pageUrl, boolean calledForPageUrl, String finalUrlStr) {
		String wasDirectLink;
		if ( calledForPageUrl ) {
			boolean isSpecialUrl = HttpConnUtils.isSpecialUrl.get();	// It's more efficient to save it once in a temp-variable.
			if ( (!isSpecialUrl && pageUrl.equals(finalUrlStr)) || sourceUrl.equals(finalUrlStr) )	// Or if it was not a "specialUrl" and the pageUrl is the same as the dcoUrl.
				wasDirectLink = "true";
			else if ( isSpecialUrl )
				wasDirectLink = "false";
			else
				wasDirectLink = "N/A";
		} else {
			// This datasetUrl came from crawling the pageUrl, so we know that it surely did not come directly from the sourceUrl.
			wasDirectLink = "false";
		}
		return wasDirectLink;
	}


	public static void printEmbeddedExceptionMessage(Exception e, String resourceURL)
	{
		String exMsg = e.getMessage();
		if (exMsg != null) {
			StackTraceElement firstLineOfStackTrace = e.getStackTrace()[0];
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
			StringBuilder sb = new StringBuilder(1000).append("Headers:\n");	// This StringBuilder is thread-safe as a local-variable.
			Map<String, List<String>> headers = conn.getHeaderFields();
			for ( String headerKey : headers.keySet() )
				for ( String headerValue : headers.get(headerKey) )
					sb.append(headerKey).append(" : ").append(headerValue).append("\n");
			logger.debug(sb.toString());
		}
	}
	
	
	public static void printRedirectDebugInfo(String currentUrl, String location, String targetUrl, int responseCode, int curRedirectsNum)
	{
		// FOR DEBUG -> Check to see what's happening with the redirect urls (location field types, as well as potential error redirects).
		// Some domains use only the target-ending-path in their location field, while others use full target url.
		
		if ( currentUrl.contains("doi.org") ) {	// Debug a certain domain or url-path.
			logger.debug("\n");
			logger.debug("Redirect(s) num: " + curRedirectsNum);
			logger.debug("Redirect code: " + responseCode);
			logger.debug("Base: " + currentUrl);
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
